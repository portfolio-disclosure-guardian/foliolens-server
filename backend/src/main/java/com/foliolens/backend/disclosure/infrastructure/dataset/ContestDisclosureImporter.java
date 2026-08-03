package com.foliolens.backend.disclosure.infrastructure.dataset;

import com.foliolens.backend.company.domain.Company;
import com.foliolens.backend.company.domain.SourceProvider;
import com.foliolens.backend.company.repository.CompanyRepository;
import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureFileFormat;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;
import com.foliolens.backend.disclosure.repository.DisclosureRepository;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContestDisclosureImporter {

    private static final int EXPECTED_DISCLOSURE_COUNT = 4_204;
    private static final int EXPECTED_COMPANY_COUNT = 70;

    private static final int EXPECTED_PERIODIC_COUNT = 1_054;
    private static final int EXPECTED_MAJOR_COUNT = 598;
    private static final int EXPECTED_EXCHANGE_COUNT = 1_469;
    private static final int EXPECTED_HOLDING_COUNT = 1_083;

    private static final int EXPECTED_CORRECTION_COUNT = 1_004;
    private static final int EXPECTED_XML_COUNT = 4_201;
    private static final int EXPECTED_PDF_HTML_COUNT = 3;

    private final DisclosureManifestReader manifestReader;
    private final CompanyRepository companyRepository;
    private final DisclosureRepository disclosureRepository;

    private final Path manifestPath;
    private final String datasetVersion;

    public ContestDisclosureImporter(
            DisclosureManifestReader manifestReader,
            CompanyRepository companyRepository,
            DisclosureRepository disclosureRepository,
            @Value("${foliolens.dataset.root}")
            String datasetRoot,
            @Value("${foliolens.dataset.version}")
            String datasetVersion
    ) {
        this.manifestReader = manifestReader;
        this.companyRepository = companyRepository;
        this.disclosureRepository = disclosureRepository;

        if (datasetRoot == null || datasetRoot.isBlank()) {
            throw datasetException(
                    "데이터셋 루트 경로가 설정되지 않았습니다."
            );
        }

        if (datasetVersion == null || datasetVersion.isBlank()) {
            throw datasetException(
                    "데이터셋 버전이 설정되지 않았습니다."
            );
        }

        this.manifestPath = Path.of(datasetRoot)
                .toAbsolutePath()
                .normalize()
                .resolve("manifest.jsonl")
                .normalize();

        this.datasetVersion = datasetVersion.trim();
    }

    /**
     * manifest.jsonl의 공시 메타데이터를 DB에 적재한다.
     */
    @Transactional
    public ImportResult importDisclosures() {

        // 1. manifest 파일 읽기
        List<DisclosureManifestRow> rows = manifestReader.read(manifestPath);

        // 2. 전체 데이터셋 건수 검증
        validateDataset(rows);

        // 3. 기존 기업 전체 조회
        Map<String, Company> companyByCorpCode =
                companyRepository.findAll()
                        .stream()
                        .collect(Collectors.toMap(
                                Company::getCorpCode,
                                Function.identity()
                        ));

        validateCompanyCount(companyByCorpCode);

        // 4. 기존 공시와 연결된 기업까지 한 번에 조회
        List<Disclosure> existingDisclosures = disclosureRepository.findAllWithCompany();

        // 5. docId 기준 Map
        Map<String, Disclosure> disclosureBySourceDocId =
                existingDisclosures.stream()
                        .collect(Collectors.toMap(
                                Disclosure::getSourceDocId,
                                Function.identity()
                        ));

        // 6. 접수번호 기준 Map
        Map<String, Disclosure> disclosureByReceiptNo =
                existingDisclosures.stream()
                        .collect(Collectors.toMap(
                                Disclosure::getReceiptNo,
                                Function.identity()
                        ));

        List<Disclosure> changedDisclosures = new ArrayList<>();

        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        // 7. manifest 공시를 한 건씩 처리
        for (DisclosureManifestRow row : rows) {
            try {
                Company company = findCompany(
                        row,
                        companyByCorpCode
                );

                // manifest 기업 정보와 DB 기업 정보 비교
                row.validateCompany(company);

                // docId와 receiptNo가 서로 다른 DB 공시를 가리키는지 검사
                Disclosure existing =
                        findExistingDisclosure(
                                row,
                                disclosureBySourceDocId,
                                disclosureByReceiptNo
                        );

                // 신규 공시
                if (existing == null) {
                    Disclosure created =
                            row.toDisclosure(
                                    company,
                                    datasetVersion
                            );

                    changedDisclosures.add(created);

                    disclosureBySourceDocId.put(
                            row.docId(),
                            created
                    );
                    disclosureByReceiptNo.put(
                            row.receiptNo(),
                            created
                    );

                    createdCount++;
                    continue;
                }

                // 기존 공시가 다른 기업에 연결돼 있으면 데이터 충돌
                validateDisclosureCompany(
                        row,
                        existing,
                        company
                );

                // 변경된 값이 없으면 저장하지 않음
                if (
                        hasSameData(
                                existing,
                                row,
                                company
                        )
                ) {
                    unchangedCount++;
                    continue;
                }

                // 변경 가능한 메타데이터 갱신
                row.updateDisclosure(
                        existing,
                        datasetVersion
                );

                changedDisclosures.add(existing);
                updatedCount++;

            } catch (BusinessException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new BusinessException(
                        ErrorCode.DATASET_503_1,
                        "공시 적재 중 오류가 발생했습니다. "
                                + "docId="
                                + row.docId()
                                + ", receiptNo="
                                + row.receiptNo(),
                        exception
                );
            }
        }

        // 8. 신규·변경 공시만 저장
        if (!changedDisclosures.isEmpty()) {
            disclosureRepository.saveAllAndFlush(
                    changedDisclosures
            );
        }

        return new ImportResult(
                rows.size(),
                createdCount,
                updatedCount,
                unchangedCount,
                disclosureRepository.count()
        );
    }

    /**
     * 전체 manifest가 예상한 데이터셋과 같은지 검증한다.
     */
    private void validateDataset(
            List<DisclosureManifestRow> rows
    ) {
        validateCount(
                "전체 공시",
                EXPECTED_DISCLOSURE_COUNT,
                rows.size()
        );

        validateCount(
                "정기공시",
                EXPECTED_PERIODIC_COUNT,
                countBySourceGroup(
                        rows,
                        DisclosureSourceGroup.PERIODIC
                )
        );

        validateCount(
                "주요사항보고서",
                EXPECTED_MAJOR_COUNT,
                countBySourceGroup(
                        rows,
                        DisclosureSourceGroup.MAJOR
                )
        );

        validateCount(
                "거래소공시",
                EXPECTED_EXCHANGE_COUNT,
                countBySourceGroup(
                        rows,
                        DisclosureSourceGroup.EXCHANGE
                )
        );

        validateCount(
                "지분공시",
                EXPECTED_HOLDING_COUNT,
                countBySourceGroup(
                        rows,
                        DisclosureSourceGroup.HOLDING
                )
        );

        validateCount(
                "정정공시",
                EXPECTED_CORRECTION_COUNT,
                rows.stream()
                        .filter(row ->
                                Boolean.TRUE.equals(
                                        row.correction()
                                )
                        )
                        .count()
        );

        validateCount(
                "XML 공시",
                EXPECTED_XML_COUNT,
                rows.stream()
                        .filter(row ->
                                row.disclosureFileFormat()
                                        == DisclosureFileFormat.XML
                        )
                        .count()
        );

        validateCount(
                "PDF+HTML 공시",
                EXPECTED_PDF_HTML_COUNT,
                rows.stream()
                        .filter(row ->
                                row.disclosureFileFormat()
                                        == DisclosureFileFormat.PDF_HTML
                        )
                        .count()
        );

        long distinctCompanyCount =
                rows.stream()
                        .map(DisclosureManifestRow::corpCode)
                        .distinct()
                        .count();

        validateCount(
                "공시 대상 기업",
                EXPECTED_COMPANY_COUNT,
                distinctCompanyCount
        );
    }

    private long countBySourceGroup(
            List<DisclosureManifestRow> rows,
            DisclosureSourceGroup sourceGroup
    ) {
        return rows.stream()
                .filter(row ->
                        row.sourceGroup() == sourceGroup
                )
                .count();
    }

    private void validateCompanyCount(
            Map<String, Company> companyByCorpCode
    ) {
        if (
                companyByCorpCode.size()
                        != EXPECTED_COMPANY_COUNT
        ) {
            throw datasetException(
                    "DB 기업 수가 예상값과 다릅니다. "
                            + "expected="
                            + EXPECTED_COMPANY_COUNT
                            + ", actual="
                            + companyByCorpCode.size()
            );
        }
    }

    /**
     * manifest의 corpCode로 Company를 찾는다.
     */
    private Company findCompany(
            DisclosureManifestRow row,
            Map<String, Company> companyByCorpCode
    ) {
        Company company =
                companyByCorpCode.get(row.corpCode());

        if (company == null) {
            throw datasetException(
                    "공시에 연결할 기업을 찾을 수 없습니다. "
                            + "docId="
                            + row.docId()
                            + ", corpCode="
                            + row.corpCode()
            );
        }

        return company;
    }

    /**
     * sourceDocId와 receiptNo가 같은 기존 공시를 가리키는지 확인한다.
     */
    private Disclosure findExistingDisclosure(
            DisclosureManifestRow row,
            Map<String, Disclosure> disclosureBySourceDocId,
            Map<String, Disclosure> disclosureByReceiptNo
    ) {
        Disclosure bySourceDocId =
                disclosureBySourceDocId.get(
                        row.docId()
                );

        Disclosure byReceiptNo =
                disclosureByReceiptNo.get(
                        row.receiptNo()
                );

        // 두 키 모두 DB에 없음 → 신규 공시
        if (
                bySourceDocId == null
                        && byReceiptNo == null
        ) {
            return null;
        }

        // 한쪽 키만 존재하면 서로 다른 식별자로 저장된 충돌 데이터
        if (
                bySourceDocId == null
                        || byReceiptNo == null
        ) {
            throw datasetException(
                    "공시 식별자 충돌이 발생했습니다. "
                            + "docId="
                            + row.docId()
                            + ", receiptNo="
                            + row.receiptNo()
            );
        }

        // 두 키가 서로 다른 DB 공시를 가리키는 경우
        if (
                !Objects.equals(
                        bySourceDocId.getId(),
                        byReceiptNo.getId()
                )
        ) {
            throw datasetException(
                    "docId와 receiptNo가 서로 다른 공시를 가리킵니다. "
                            + "docId="
                            + row.docId()
                            + ", receiptNo="
                            + row.receiptNo()
            );
        }

        return bySourceDocId;
    }

    /**
     * 기존 공시가 manifest에서 찾은 같은 Company에 연결되어 있는지 검증한다.
     */
    private void validateDisclosureCompany(
            DisclosureManifestRow row,
            Disclosure disclosure,
            Company company
    ) {
        if (
                !Objects.equals(
                        disclosure.getCompany().getId(),
                        company.getId()
                )
        ) {
            throw datasetException(
                    "기존 공시가 다른 기업에 연결되어 있습니다. "
                            + "docId="
                            + row.docId()
                            + ", expectedCorpCode="
                            + company.getCorpCode()
                            + ", actualCorpCode="
                            + disclosure.getCompany()
                            .getCorpCode()
            );
        }
    }

    /**
     * DB의 기존 공시와 manifest 값이 모두 같은지 확인한다.
     */
    private boolean hasSameData(
            Disclosure disclosure,
            DisclosureManifestRow row,
            Company company
    ) {
        DisclosureSourceGroup sourceGroup =
                row.sourceGroup();

        return Objects.equals(
                disclosure.getSourceDocId(),
                row.docId()
        )
                && Objects.equals(
                disclosure.getCompany().getId(),
                company.getId()
        )
                && Objects.equals(
                disclosure.getReceiptNo(),
                row.receiptNo()
        )
                && disclosure.getCategory()
                == sourceGroup.getCategory()
                && disclosure.getSourceGroup()
                == sourceGroup
                && Objects.equals(
                disclosure.getRawSubtype(),
                row.docSubtype()
        )
                && Objects.equals(
                disclosure.getReportName(),
                row.reportName()
        )
                && disclosure.isCorrection()
                == Boolean.TRUE.equals(
                row.correction()
        )
                && Objects.equals(
                disclosure.getReceiptDate(),
                row.receiptDate()
        )
                && Objects.equals(
                disclosure.getSubmitter(),
                row.submitter()
        )
                && Objects.equals(
                disclosure.getBaseYear(),
                row.baseYear()
        )
                && Objects.equals(
                disclosure.getBaseMonth(),
                row.baseMonth()
        )
                && Objects.equals(
                disclosure.getManifestPath(),
                row.filePath()
        )
                && disclosure.getFileFormat()
                == row.disclosureFileFormat()
                && Objects.equals(
                disclosure.getExpectedFileCount(),
                row.fileCount()
        )
                && disclosure.getSourceProvider()
                == SourceProvider.CONTEST
                && Objects.equals(
                disclosure.getSourceDatasetVersion(),
                datasetVersion
        );
    }

    private void validateCount(
            String target,
            long expected,
            long actual
    ) {
        if (expected != actual) {
            throw datasetException(
                    target
                            + " 수가 예상값과 다릅니다. "
                            + "expected="
                            + expected
                            + ", actual="
                            + actual
            );
        }
    }

    private static String validateDatasetVersion(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw datasetException(
                    "데이터셋 버전이 설정되지 않았습니다."
            );
        }

        return value.trim();
    }

    private static BusinessException datasetException(String message) {
        return new BusinessException(
                ErrorCode.DATASET_503_1,
                message
        );
    }

    public record ImportResult(
            int inputCount,
            int createdCount,
            int updatedCount,
            int unchangedCount,
            long totalDisclosureCount
    ) {
    }
}
