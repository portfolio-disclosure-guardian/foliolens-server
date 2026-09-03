package com.foliolens.backend.disclosure.infrastructure.html;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * 실제 콘텐츠가 HTML인 대회 원문 파일의 기본 안전성을 검증한다.
 * 거래소 원문은 확장자가 .xml이어도 HTML일 수 있다.
 */
@Component
public class HtmlSourceFileValidator {

    public Path validate(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile은 필수입니다.");
        Path normalized = sourceFile.toAbsolutePath().normalize();

        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw datasetException(
                    "HTML 원문 파일이 존재하지 않습니다. path=" + normalized
            );
        }
        if (Files.isSymbolicLink(normalized)) {
            throw datasetException(
                    "심볼릭 링크는 HTML 원문으로 사용할 수 없습니다. path="
                            + normalized
            );
        }
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw datasetException(
                    "HTML 원문 경로가 일반 파일이 아닙니다. path=" + normalized
            );
        }
        if (!Files.isReadable(normalized)) {
            throw datasetException(
                    "HTML 원문 파일을 읽을 수 없습니다. path=" + normalized
            );
        }

        String fileName = normalized.getFileName().toString()
                .toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".html") && !fileName.endsWith(".xml")) {
            throw datasetException(
                    "HTML 또는 HTML 콘텐츠의 .xml 파일만 지원합니다. path="
                            + normalized
            );
        }
        return normalized;
    }

    private BusinessException datasetException(String message) {
        return new BusinessException(ErrorCode.DATASET_503_1, message);
    }
}
