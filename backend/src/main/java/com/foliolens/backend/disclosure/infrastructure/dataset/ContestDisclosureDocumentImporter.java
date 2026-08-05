package com.foliolens.backend.disclosure.infrastructure.dataset;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosureDocumentScanner;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosurePathResolver;
import com.foliolens.backend.disclosure.infrastructure.filesystem.ScannedDisclosureFile;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.repository.DisclosureRepository;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContestDisclosureDocumentImporter {

    private static final int EXPECTED_DISCLOSURE_COUNT = 4_204;
    private static final int EXPECTED_DOCUMENT_COUNT = 4_622;

    private static final int EXPECTED_XML_COUNT = 4_616;
    private static final int EXPECTED_HTML_COUNT = 3;
    private static final int EXPECTED_PDF_COUNT = 3;

    private final DisclosureRepository disclosureRepository;
    private final DisclosureDocumentRepository documentRepository;
    private final DisclosurePathResolver pathResolver;
    private final DisclosureDocumentScanner documentScanner;
    private final TransactionTemplate transactionTemplate;

    public ContestDisclosureDocumentImporter(
            DisclosureRepository disclosureRepository,
            DisclosureDocumentRepository documentRepository,
            DisclosurePathResolver pathResolver,
            DisclosureDocumentScanner documentScanner,
            PlatformTransactionManager transactionManager
    ) {
        this.disclosureRepository = disclosureRepository;
        this.documentRepository = documentRepository;
        this.pathResolver = pathResolver;
        this.documentScanner = documentScanner;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    /**
     * 모든 공시 폴더의 실제 원문 파일을 찾아
     * disclosure_documents에 멱등 적재한다.
     */
    public synchronized ImportResult importDocuments() {
        List<Disclosure> disclosures = disclosureRepository.findAll();

        validateCount("공시", EXPECTED_DISCLOSURE_COUNT, disclosures.size());

        Set<String> seenNormalizedPaths = new HashSet<>();
        Map<String, Integer> extensionCounts = new HashMap<>();
        List<ImportFailure> failures = new ArrayList<>();

        int processedDisclosureCount = 0;
        int discoveredFileCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for (Disclosure disclosure : disclosures) {
            try {
                // disclosureDirectory 가 NFD(ㄱㅏㄴㅏ) 절대경로
                //C:\study\portfolio-disclosure-guardian\foliolens-data\raw\exchange\SK하이닉스\20240424800596
                Path disclosureDirectory = pathResolver
                        .resolveDirectory(disclosure.getManifestPath());

                // Importer가 찾아낸 공시 폴더(disclosureDirectory)를 조사해서,
                // 폴더 안의 "실제 파일 정보"를 목록으로 만들어주는 메서드
                List<ScannedDisclosureFile> scannedFiles = documentScanner
                        .scan(disclosure, disclosureDirectory);

                validateScannedFiles(
                        disclosure,
                        scannedFiles,
                        seenNormalizedPaths
                );

                /**
                 * 여기서 db에 disclosure_document 저장
                 */
                WriteResult writeResult =
                        saveDisclosureDocuments(
                                disclosure.getId(),
                                scannedFiles
                        );

                processedDisclosureCount++;
                discoveredFileCount += scannedFiles.size();
                createdCount += writeResult.createdCount();
                updatedCount += writeResult.updatedCount();
                unchangedCount += writeResult.unchangedCount();

                for (ScannedDisclosureFile scannedFile : scannedFiles) {
                    extensionCounts.merge(
                            scannedFile.fileExtension(),
                            1,
                            Integer::sum
                    );
                }
            } catch (RuntimeException exception) {
                failures.add(
                        new ImportFailure(
                                disclosure.getSourceDocId(),
                                disclosure.getManifestPath(),
                                exception.getMessage()
                        )
                );
            }
        }

        /*
         * 실패한 공시가 없을 때만 전체 데이터셋 기대값을 검증한다.
         * 실패가 있으면 발견 파일 수가 당연히 4,622보다 작을 수 있다.
         */
        if (failures.isEmpty()) {
            validateDatasetResult(
                    discoveredFileCount,
                    extensionCounts
            );
        }

        long totalDocumentCount = documentRepository.count();

        if (failures.isEmpty()) {
            validateCount(
                    "DB 원문 파일",
                    EXPECTED_DOCUMENT_COUNT,
                    totalDocumentCount
            );
        }

        return new ImportResult(
                disclosures.size(),
                processedDisclosureCount,
                failures.size(),
                discoveredFileCount,
                createdCount,
                updatedCount,
                unchangedCount,
                totalDocumentCount,
                failures
        );
    }

    /**
     * 한 공시에 포함된 파일만 짧은 트랜잭션으로 저장한다.
     *
     * 파일 탐색과 SHA-256 계산은 트랜잭션 밖에서 수행한다.
     */
    private WriteResult saveDisclosureDocuments(
            UUID disclosureId,
            List<ScannedDisclosureFile> scannedFiles
    ) {
        WriteResult result = transactionTemplate.execute(
                status -> upsertDisclosureDocuments(
                        disclosureId,
                        scannedFiles
                )
        );

        if (result == null) {
            throw datasetException(
                    "원문 파일 저장 결과를 확인할 수 없습니다. "
                            + "disclosureId=" + disclosureId
            );
        }

        return result;
    }

    // db 저장 메서드
    private WriteResult upsertDisclosureDocuments(
            UUID disclosureId,
            List<ScannedDisclosureFile> scannedFiles
    ) {
        /*
         * 현재 트랜잭션에서 관리되는 Disclosure 참조를 사용한다.
         * importDocuments()에서 조회한 Disclosure는 트랜잭션 밖에서는
         * 준영속 상태가 될 수 있기 때문이다.
         */
        Disclosure managedDisclosure = disclosureRepository.getReferenceById(disclosureId);

        // 이미 db에 저장되어 있는 파일들을 변경사항이 있는지 검사하고 변경사항이 없다면 저장하지 않으려고 검사
        List<DisclosureDocument> existingDocuments =
                documentRepository.findAllByDisclosureIdOrderByFileNameAsc(disclosureId);

        Map<String, DisclosureDocument> existingByPath =
                existingDocuments.stream()
                        .collect(Collectors.toMap(
                                DisclosureDocument
                                        ::getNormalizedRelativePath,
                                Function.identity()
                        ));

        List<DisclosureDocument> changedDocuments = new ArrayList<>();

        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for (ScannedDisclosureFile scannedFile : scannedFiles) {
            DisclosureDocument existing =
                    existingByPath.remove(
                            scannedFile.normalizedRelativePath()
                    );

            // db에 존재하지 않은 새로운 파일이라면 엔티티 생성 후 db에 저장
            if (existing == null) {
                DisclosureDocument created =
                        DisclosureDocument.create(
                                managedDisclosure,
                                scannedFile.relativePath(),
                                scannedFile.normalizedRelativePath(),
                                scannedFile.fileName(),
                                scannedFile.fileExtension(),
                                scannedFile.documentRole(),
                                normalizeNullable(
                                        scannedFile.documentName()
                                ),
                                scannedFile.contentFormat(),
                                scannedFile.fileSizeBytes(),
                                scannedFile.sha256()
                        );

                changedDocuments.add(created);
                createdCount++;
                continue;
            }

            /*
             * 같은 파일을 다시 적재할 때 파서가 이미 확정한
             * 문서명과 역할을 Scanner의 UNKNOWN/null 값으로
             * 덮어쓰지 않는다.
             */
            EffectiveClassification classification =
                    resolveClassification(
                            existing,
                            scannedFile
                    );

            if (
                    hasSameData(
                            existing,
                            scannedFile,
                            classification
                    )
            ) {
                unchangedCount++;
                continue;
            }

            boolean contentFormatChanged =
                    existing.getContentFormat()
                            != scannedFile.contentFormat();

            existing.updateFileMetadata(
                    scannedFile.relativePath(),
                    scannedFile.normalizedRelativePath(),
                    scannedFile.fileName(),
                    scannedFile.fileExtension(),
                    classification.documentRole(),
                    classification.documentName(),
                    scannedFile.contentFormat(),
                    scannedFile.fileSizeBytes(),
                    scannedFile.sha256()
            );

            /*
             * 파일 내용은 같더라도 콘텐츠 형식 판별이 바뀌면
             * 다른 파서를 사용해야 하므로 재파싱 대상으로 돌린다.
             */
            if (contentFormatChanged) {
                existing.resetParsing();
            }

            changedDocuments.add(existing);
            updatedCount++;
        }

        /*
         * DB에는 있지만 현재 실제 폴더에서는 발견되지 않은 파일이다.
         * 원본 파일을 임의로 삭제하거나 DB 행을 자동 삭제하지 않고
         * 데이터셋 불일치로 처리한다.
         */
        if (!existingByPath.isEmpty()) {
            String missingPaths =
                    existingByPath.keySet()
                            .stream()
                            .limit(5)
                            .collect(Collectors.joining(", "));

            throw datasetException(
                    "DB에는 존재하지만 실제 공시 폴더에서 찾지 못한 "
                            + "원문 파일이 있습니다. "
                            + "disclosureId=" + disclosureId
                            + ", paths=" + missingPaths
            );
        }

        // 여기서 db 저장
        if (!changedDocuments.isEmpty()) {
            documentRepository.saveAllAndFlush(changedDocuments);
        }

        return new WriteResult(
                createdCount,
                updatedCount,
                unchangedCount
        );
    }

    private void validateScannedFiles(
            Disclosure disclosure,
            List<ScannedDisclosureFile> scannedFiles,
            Set<String> seenNormalizedPaths
    ) {
        if (scannedFiles == null) {
            throw datasetException(
                    "Scanner가 null을 반환했습니다. "
                            + "docId=" + disclosure.getSourceDocId()
            );
        }

        validateCount(
                "공시별 원문 파일",
                disclosure.getExpectedFileCount(),
                scannedFiles.size()
        );

        String normalizedManifestPath =
                Normalizer.normalize(
                        disclosure.getManifestPath(),
                        Normalizer.Form.NFC
                );

        Set<String> localPaths = new HashSet<>();
        Set<String> localFileNames = new HashSet<>();

        for (ScannedDisclosureFile scannedFile : scannedFiles) {
            validateScannedFile(
                    disclosure,
                    scannedFile,
                    normalizedManifestPath
            );

            if (
                    !localPaths.add(
                            scannedFile.normalizedRelativePath()
                    )
            ) {
                throw datasetException(
                        "한 공시 폴더에서 정규화 경로가 중복되었습니다. "
                                + "docId=" + disclosure.getSourceDocId()
                                + ", path="
                                + scannedFile.normalizedRelativePath()
                );
            }

            if (!localFileNames.add(scannedFile.fileName())) {
                throw datasetException(
                        "한 공시 폴더에서 파일명이 중복되었습니다. "
                                + "docId=" + disclosure.getSourceDocId()
                                + ", fileName="
                                + scannedFile.fileName()
                );
            }
        }

        for (String normalizedPath : localPaths) {
            if (seenNormalizedPaths.contains(normalizedPath)) {
                throw datasetException(
                        "서로 다른 공시에서 동일한 원문 경로가 발견되었습니다. "
                                + "path=" + normalizedPath
                );
            }
        }

        seenNormalizedPaths.addAll(localPaths);
    }

    private void validateScannedFile(
            Disclosure disclosure,
            ScannedDisclosureFile scannedFile,
            String normalizedManifestPath
    ) {
        if (scannedFile == null) {
            throw datasetException(
                    "Scanner 결과에 null 파일이 포함되어 있습니다. "
                            + "docId=" + disclosure.getSourceDocId()
            );
        }

        requireNotBlank(
                scannedFile.relativePath(),
                "relativePath"
        );

        requireNotBlank(
                scannedFile.normalizedRelativePath(),
                "normalizedRelativePath"
        );

        requireNotBlank(
                scannedFile.fileName(),
                "fileName"
        );

        requireNotBlank(
                scannedFile.fileExtension(),
                "fileExtension"
        );

        requireNotBlank(
                scannedFile.sha256(),
                "sha256"
        );

        Objects.requireNonNull(
                scannedFile.documentRole(),
                "documentRole은 필수입니다."
        );

        Objects.requireNonNull(
                scannedFile.contentFormat(),
                "contentFormat은 필수입니다."
        );

        if (scannedFile.fileSizeBytes() < 0) {
            throw datasetException(
                    "파일 크기는 0 이상이어야 합니다. "
                            + "fileName=" + scannedFile.fileName()
            );
        }

        String expectedNormalizedPath =
                Normalizer.normalize(
                        scannedFile.relativePath(),
                        Normalizer.Form.NFC
                );

        if (
                !expectedNormalizedPath.equals(
                        scannedFile.normalizedRelativePath()
                )
        ) {
            throw datasetException(
                    "normalizedRelativePath가 relativePath의 "
                            + "NFC 정규화 결과와 다릅니다. "
                            + "fileName=" + scannedFile.fileName()
            );
        }

        int lastSeparator =
                scannedFile.normalizedRelativePath()
                        .lastIndexOf('/');

        if (lastSeparator < 0) {
            throw datasetException(
                    "원문 파일 경로에 부모 폴더가 없습니다. "
                            + "path="
                            + scannedFile.normalizedRelativePath()
            );
        }

        String parentPath =
                scannedFile.normalizedRelativePath()
                        .substring(0, lastSeparator);

        if (!parentPath.equals(normalizedManifestPath)) {
            throw datasetException(
                    "원문 파일이 공시 manifest 폴더 바로 아래에 있지 않습니다. "
                            + "docId=" + disclosure.getSourceDocId()
                            + ", expectedParent="
                            + normalizedManifestPath
                            + ", actualParent="
                            + parentPath
            );
        }
    }

    private EffectiveClassification resolveClassification(
            DisclosureDocument existing,
            ScannedDisclosureFile scannedFile
    ) {
        boolean sameContent = Objects.equals(
                existing.getSha256(),
                scannedFile.sha256()
        );

        if (!sameContent) {
            return new EffectiveClassification(
                    scannedFile.documentRole(),
                    normalizeNullable(
                            scannedFile.documentName()
                    )
            );
        }

        DisclosureDocumentRole effectiveRole =
                scannedFile.documentRole();

        if (
                effectiveRole
                        == DisclosureDocumentRole.UNKNOWN
                        && existing.getDocumentRole()
                        != DisclosureDocumentRole.UNKNOWN
        ) {
            effectiveRole = existing.getDocumentRole();
        }

        String scannedDocumentName =
                normalizeNullable(
                        scannedFile.documentName()
                );

        String effectiveDocumentName =
                scannedDocumentName != null
                        ? scannedDocumentName
                        : existing.getDocumentName();

        return new EffectiveClassification(
                effectiveRole,
                effectiveDocumentName
        );
    }

    private boolean hasSameData(
            DisclosureDocument document,
            ScannedDisclosureFile scannedFile,
            EffectiveClassification classification
    ) {
        return Objects.equals(
                document.getRelativePath(),
                scannedFile.relativePath()
        )
                && Objects.equals(
                document.getNormalizedRelativePath(),
                scannedFile.normalizedRelativePath()
        )
                && Objects.equals(
                document.getFileName(),
                scannedFile.fileName()
        )
                && Objects.equals(
                document.getFileExtension(),
                scannedFile.fileExtension()
        )
                && document.getDocumentRole()
                == classification.documentRole()
                && Objects.equals(
                document.getDocumentName(),
                classification.documentName()
        )
                && document.getContentFormat()
                == scannedFile.contentFormat()
                && Objects.equals(
                document.getFileSizeBytes(),
                scannedFile.fileSizeBytes()
        )
                && Objects.equals(
                document.getSha256(),
                scannedFile.sha256()
        );
    }

    private void validateDatasetResult(
            int discoveredFileCount,
            Map<String, Integer> extensionCounts
    ) {
        validateCount(
                "전체 원문 파일",
                EXPECTED_DOCUMENT_COUNT,
                discoveredFileCount
        );

        validateCount(
                "XML 원문 파일",
                EXPECTED_XML_COUNT,
                extensionCounts.getOrDefault("xml", 0)
        );

        validateCount(
                "HTML 원문 파일",
                EXPECTED_HTML_COUNT,
                extensionCounts.getOrDefault("html", 0)
        );

        validateCount(
                "PDF 원문 파일",
                EXPECTED_PDF_COUNT,
                extensionCounts.getOrDefault("pdf", 0)
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
                            + "expected=" + expected
                            + ", actual=" + actual
            );
        }
    }

    private static String requireNotBlank(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw datasetException(
                    fieldName + "은 필수입니다."
            );
        }

        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static BusinessException datasetException(
            String message
    ) {
        return new BusinessException(
                ErrorCode.DATASET_503_1,
                message
        );
    }

    private record WriteResult(
            int createdCount,
            int updatedCount,
            int unchangedCount
    ) {
    }

    private record EffectiveClassification(
            DisclosureDocumentRole documentRole,
            String documentName
    ) {
    }

    public record ImportFailure(
            String sourceDocId,
            String manifestPath,
            String reason
    ) {
    }

    public record ImportResult(
            int disclosureCount,
            int processedDisclosureCount,
            int failedDisclosureCount,
            int discoveredFileCount,
            int createdCount,
            int updatedCount,
            int unchangedCount,
            long totalDocumentCount,
            List<ImportFailure> failures
    ) {
        public ImportResult {
            failures = List.copyOf(failures);
        }

        public boolean successful() {
            return failedDisclosureCount == 0
                    && totalDocumentCount
                    == EXPECTED_DOCUMENT_COUNT;
        }
    }
}
