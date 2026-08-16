package com.foliolens.backend.disclosure.infrastructure.xml;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

@Component
public class DartXmlSourceFileValidator {

    public Path validate(Path sourceFile) {
        Objects.requireNonNull(
                sourceFile,
                "sourceFile은 필수입니다."
        );

        Path normalized =
                sourceFile.toAbsolutePath().normalize();

        if (!Files.exists(
                normalized,
                LinkOption.NOFOLLOW_LINKS
        )) {
            throw datasetException(
                    "XML 파일이 존재하지 않습니다. path="
                            + normalized
            );
        }

        if (Files.isSymbolicLink(normalized)) {
            throw datasetException(
                    "심볼릭 링크는 XML 원문으로 사용할 수 없습니다. path="
                            + normalized
            );
        }

        if (!Files.isRegularFile(
                normalized,
                LinkOption.NOFOLLOW_LINKS
        )) {
            throw datasetException(
                    "XML 경로가 일반 파일이 아닙니다. path="
                            + normalized
            );
        }

        if (!Files.isReadable(normalized)) {
            throw datasetException(
                    "XML 파일을 읽을 수 없습니다. path="
                            + normalized
            );
        }

        String fileName = normalized
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        if (!fileName.endsWith(".xml")) {
            throw datasetException(
                    "XML 파일만 지원합니다. path="
                            + normalized
            );
        }

        return normalized;
    }

    private BusinessException datasetException(
            String message
    ) {
        return new BusinessException(
                ErrorCode.DATASET_503_1,
                message
        );
    }
}
