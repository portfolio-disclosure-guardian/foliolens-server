package com.foliolens.backend.disclosure.infrastructure.html;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

/**
 * HTML 원문 파일을 실제 문자 인코딩으로 해석한 결과.
 */
public record DecodedHtmlSource(
        Path sourceFile,
        String content,
        Charset charset
) {

    public DecodedHtmlSource {
        sourceFile = Objects.requireNonNull(
                sourceFile,
                "sourceFile은 필수입니다."
        ).toAbsolutePath().normalize();
        content = Objects.requireNonNull(content, "content는 필수입니다.");
        charset = Objects.requireNonNull(charset, "charset은 필수입니다.");

        if (content.isBlank()) {
            throw new IllegalArgumentException(
                    "HTML 원문 내용은 비어 있을 수 없습니다."
            );
        }
    }
}
