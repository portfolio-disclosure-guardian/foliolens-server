package com.foliolens.backend.disclosure.infrastructure.dataset;

import com.foliolens.backend.disclosure.infrastructure.dataset.ContestDisclosureDocumentImporter.ImportFailure;
import com.foliolens.backend.disclosure.infrastructure.dataset.ContestDisclosureDocumentImporter.ImportResult;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
@ConditionalOnProperty(
        prefix = "foliolens.dataset",
        name = "import-documents-on-startup",
        havingValue = "true"
)
public class ContestDisclosureDocumentImportRunner implements ApplicationRunner {

    private static final int MAX_FAILURE_LOG_COUNT = 10;

    private final ContestDisclosureDocumentImporter documentImporter;

    @Override
    public void run(ApplicationArguments args) {
        log.info("공시 원문 파일 적재를 시작합니다.");

        ImportResult result = documentImporter.importDocuments();

        if (!result.successful()) {
            logFailures(result);

            throw new BusinessException(
                    ErrorCode.DATASET_503_2,
                    """
                    공시 원문 파일 적재에 실패했습니다. \
                    전체 공시=%d, 처리 공시=%d, 실패 공시=%d, 발견 파일=%d, DB 문서=%d
                    """.formatted(
                            result.disclosureCount(),
                            result.processedDisclosureCount(),
                            result.failedDisclosureCount(),
                            result.discoveredFileCount(),
                            result.totalDocumentCount()
                    )
            );
        }

        log.info(
                """
                공시 원문 파일 적재가 완료되었습니다. \
                전체 공시={}, 처리 공시={}, 발견 파일={}, 생성={}, 수정={}, 변경 없음={}, DB 문서={}
                """,
                result.disclosureCount(),
                result.processedDisclosureCount(),
                result.discoveredFileCount(),
                result.createdCount(),
                result.updatedCount(),
                result.unchangedCount(),
                result.totalDocumentCount()
        );
    }

    private void logFailures(ImportResult result) {
        result.failures().stream()
                .limit(MAX_FAILURE_LOG_COUNT)
                .forEach(this::logFailure);

        int omittedFailureCount =
                result.failures().size() - MAX_FAILURE_LOG_COUNT;

        if (omittedFailureCount > 0) {
            log.error(
                    "출력하지 않은 공시 원문 적재 실패가 {}건 더 있습니다.",
                    omittedFailureCount
            );
        }
    }

    private void logFailure(ImportFailure failure) {
        log.error(
                "공시 원문 적재 실패: sourceDocId={}, manifestPath={}, reason={}",
                failure.sourceDocId(),
                failure.manifestPath(),
                failure.reason()
        );
    }
}
