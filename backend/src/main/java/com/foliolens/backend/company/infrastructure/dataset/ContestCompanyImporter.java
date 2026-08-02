package com.foliolens.backend.company.infrastructure.dataset;

import com.foliolens.backend.company.domain.Company;
import com.foliolens.backend.company.domain.SourceProvider;
import com.foliolens.backend.company.repository.CompanyRepository;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
/**
 * universe.csv에서 읽은 기업 정보를 DB의 companies 테이블과 비교해 신규 저장하거나 변경된 기업만 갱신하는 클래스
 */
public class ContestCompanyImporter {

    // 데이터셋 기대값 상수
    // CSV가 잘못 수정돼 기업이 69개만 남았다면 DB에 저장하기 전에 중단하는데에 쓰임
    private static final int EXPECTED_COMPANY_COUNT = 70;
    private static final int EXPECTED_PERIODIC_COUNT = 1_054;
    private static final int EXPECTED_MAJOR_COUNT = 598;
    private static final int EXPECTED_EXCHANGE_COUNT = 1_469;
    private static final int EXPECTED_HOLDING_COUNT = 1_083;

    private final CompanyCsvReader csvReader; // CSV 파일을 읽고 검증 -> List<CompanyCsvRow>로 변환
    private final CompanyRepository companyRepository; // 실제 DB 조회와 저장
    private final Path universeCsvPath; // 실제로 읽을 universe.csv의 전체 경로
    private final String datasetVersion;
    private final LocalDate marketCapAsOf;

    public ContestCompanyImporter(
            CompanyCsvReader csvReader,
            CompanyRepository companyRepository,
            @Value("${foliolens.dataset.root}")
            String datasetRoot,
            @Value("${foliolens.dataset.version}")
            String datasetVersion,
            @Value("${foliolens.dataset.market-cap-as-of}")
            String marketCapAsOf
    ) {
        this.csvReader = csvReader;
        this.companyRepository = companyRepository;

        // CSV 경로 생성
        this.universeCsvPath = Path.of(datasetRoot)
                .toAbsolutePath()
                .normalize()
                .resolve("universe.csv");

        this.datasetVersion = validateDatasetVersion(
                datasetVersion
        );

        this.marketCapAsOf = parseMarketCapAsOf(
                marketCapAsOf
        );
    }

    /**
     * 실행 흐름
     * importCompanies()
     * ├─ CSV 읽기
     * ├─ 데이터셋 전체 건수 검증
     * ├─ DB 기업 전체 조회
     * ├─ 기업코드·종목코드 Map 생성
     * ├─ CSV 기업 70개 반복
     * │  ├─ 종목코드 충돌 검사
     * │  ├─ 신규 기업 생성
     * │  ├─ 동일하면 무시
     * │  └─ 변경됐으면 갱신
     * ├─ 변경된 기업만 DB 저장
     * └─ ImportResult 반환
     */
    @Transactional
    public ImportResult importCompanies() {

        // universe.csv를 읽어 기업 70개의 행 객체로 변환
        List<CompanyCsvRow> rows = csvReader.read(universeCsvPath);

        // 데이터셋 전체 검증
        validateDataset(rows);

        // 기존 DB 기업 조회
        // 기업이 70개 정도이므로 전체 조회해 메모리에서 비교하는 것이 단순하고 효율적
        List<Company> existingCompanies = companyRepository.findAll();

        // 기업코드 Map 생성
        // "00126380" → 삼성전자 Company
        // "00164779" → SK하이닉스 Company
        // 이후 기업코드로 기존 기업을 빠르게 찾을 수 있음
        Map<String, Company> companyByCorpCode =
                existingCompanies.stream()
                        .collect(Collectors.toMap(
                                Company::getCorpCode,
                                Function.identity()
                        ));

        // 종목코드 Map 생성
        // "005930" → 삼성전자 Company
        // "000660" → SK하이닉스 Company
        // 종목코드가 다른 기업에 이미 사용되고 있는지 검사할 때 사용
        Map<String, Company> companyByStockCode =
                existingCompanies.stream()
                        .collect(Collectors.toMap(
                                Company::getStockCode,
                                Function.identity()
                        ));

        // 변경 기업 목록 기록
        List<Company> changedCompanies = new ArrayList<>();

        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for (CompanyCsvRow row : rows) {
            // CSV의 종목코드가 DB에서 다른 기업에 사용되고 있지는 않은지 확인
            validateStockCodeOwner(row, companyByStockCode);

            Company existing = companyByCorpCode.get(row.corpCode());

            // 신규 기업 처리
            // 같은 corpCode가 DB에 없으면 신규 기업으로 판단
            if (existing == null) {
                Company created = row.toCompany(
                        marketCapAsOf,
                        datasetVersion
                );

                changedCompanies.add(created);

                companyByCorpCode.put(
                        row.corpCode(),
                        created
                );
                companyByStockCode.put(
                        row.stockCode(),
                        created
                );

                createdCount++;
                continue;
            }

            // 변경 없는 기업 처리
            // DB 값과 CSV 값이 완전히 같다면 아무 작업도 하지 않음
            if (hasSameData(existing, row)) {
                unchangedCount++;
                continue;
            }

            String previousStockCode = existing.getStockCode();

            row.updateCompany(
                    existing,
                    marketCapAsOf,
                    datasetVersion
            );

            // 종목코드 Map 갱신
            // 기업의 종목코드가 변경됐다면 Map도 바꿈
            if (
                    !Objects.equals(
                            previousStockCode,
                            row.stockCode()
                    )
            ) {
                companyByStockCode.remove(
                        previousStockCode
                );
                companyByStockCode.put(
                        row.stockCode(),
                        existing
                );
            }

            changedCompanies.add(existing);
            updatedCount++;
        }

        // 변경 기업 저장
        if (!changedCompanies.isEmpty()) {
            companyRepository.saveAllAndFlush(
                    changedCompanies
            );
        }

        return new ImportResult(
                rows.size(),
                createdCount,
                updatedCount,
                unchangedCount,
                companyRepository.count()
        );
    }

    private void validateDataset(List<CompanyCsvRow> rows) {
        if (rows.size() != EXPECTED_COMPANY_COUNT) {
            throw datasetException(
                    "기업 수가 예상값과 다릅니다. expected="
                            + EXPECTED_COMPANY_COUNT
                            + ", actual="
                            + rows.size()
            );
        }

        validateCount(
                "periodic",
                EXPECTED_PERIODIC_COUNT,
                rows.stream()
                        .mapToInt(
                                CompanyCsvRow::nPeriodic
                        )
                        .sum()
        );

        validateCount(
                "major",
                EXPECTED_MAJOR_COUNT,
                rows.stream()
                        .mapToInt(
                                CompanyCsvRow::nMajor
                        )
                        .sum()
        );

        validateCount(
                "exchange",
                EXPECTED_EXCHANGE_COUNT,
                rows.stream()
                        .mapToInt(
                                CompanyCsvRow::nExchange
                        )
                        .sum()
        );

        validateCount(
                "holding",
                EXPECTED_HOLDING_COUNT,
                rows.stream()
                        .mapToInt(
                                CompanyCsvRow::nHolding
                        )
                        .sum()
        );
    }

    private void validateCount(
            String group,
            int expected,
            int actual
    ) {
        if (expected != actual) {
            throw datasetException(
                    group
                            + " 공시 수가 예상값과 다릅니다. expected="
                            + expected
                            + ", actual="
                            + actual
            );
        }
    }

    private void validateStockCodeOwner(
            CompanyCsvRow row,
            Map<String, Company> companyByStockCode
    ) {
        Company owner =
                companyByStockCode.get(row.stockCode());

        if (owner == null) {
            return;
        }

        if (!owner.getCorpCode().equals(row.corpCode())) {
            throw datasetException(
                    "종목코드가 다른 기업에서 사용 중입니다. "
                            + "stockCode="
                            + row.stockCode()
                            + ", existingCorpCode="
                            + owner.getCorpCode()
                            + ", inputCorpCode="
                            + row.corpCode()
            );
        }
    }

    private boolean hasSameData(
            Company company,
            CompanyCsvRow row
    ) {
        return Objects.equals(
                company.getStockCode(),
                row.stockCode()
        )
                && Objects.equals(
                company.getCorpName(),
                row.corpName()
        )
                && Objects.equals(
                company.getListedName(),
                row.listedName()
        )
                && Objects.equals(
                company.getCorpEngName(),
                row.corpEngName()
        )
                && company.getMarket() == row.market()
                && Objects.equals(
                company.getIndustry(),
                row.industry()
        )
                && Objects.equals(
                company.getSectorNo(),
                row.sectorNo()
        )
                && Objects.equals(
                company.getSector(),
                row.sector()
        )
                && Objects.equals(
                company.getListingDate(),
                row.listingDate()
        )
                && Objects.equals(
                company.getFiscalMonth(),
                row.fiscalMonth()
        )
                && Objects.equals(
                company.getMarketCap(),
                row.marketCap()
        )
                && Objects.equals(
                company.getMarketCapAsOf(),
                marketCapAsOf
        )
                && Boolean.TRUE.equals(
                company.getListed()
        )
                && company.getSourceProvider()
                == SourceProvider.CONTEST
                && Objects.equals(
                company.getSourceDatasetVersion(),
                datasetVersion
        )
                && Objects.equals(
                company.getNote(),
                row.note()
        );
    }

    private String validateDatasetVersion(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw datasetException(
                    "데이터셋 버전이 설정되지 않았습니다."
            );
        }

        return value.trim();
    }

    private LocalDate parseMarketCapAsOf(
            String value
    ) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "시가총액 기준일 설정이 올바르지 않습니다: "
                            + value,
                    exception
            );
        }
    }

    private BusinessException datasetException(
            String message
    ) {
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
            long totalCompanyCount
    ) {
    }
}
