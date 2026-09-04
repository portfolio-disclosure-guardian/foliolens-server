package com.foliolens.backend.disclosure.infrastructure.html;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * HTML 원문을 UTF-8 우선, MS949 차선으로 디코딩한다.
 *
 * 실제 대회 HTML은 meta에 euc-kr가 적혀 있어도 UTF-8 바이트일 수 있으므로
 * 선언값만 신뢰하지 않고 실제 디코딩 성공 여부를 기준으로 선택한다.
 */
@Component
public class HtmlSourceReader {

    private static final Charset MS949 = Charset.forName("MS949");
    private static final char UTF_8_BOM = '\uFEFF';

    private final HtmlSourceFileValidator sourceFileValidator;

    public HtmlSourceReader(HtmlSourceFileValidator sourceFileValidator) {
        this.sourceFileValidator = Objects.requireNonNull(
                sourceFileValidator,
                "sourceFileValidator는 필수입니다."
        );
    }

    public DecodedHtmlSource read(Path sourceFile) {
        Path normalizedFile = sourceFileValidator.validate(sourceFile);
        byte[] bytes = readAllBytes(normalizedFile);

        if (bytes.length == 0) {
            throw datasetException(
                    "HTML 원문 파일이 비어 있습니다. path=" + normalizedFile,
                    null
            );
        }

        try {
            return decoded(
                    normalizedFile,
                    decode(bytes, StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8
            );
        } catch (CharacterCodingException utf8Exception) {
            try {
                return decoded(
                        normalizedFile,
                        decode(bytes, MS949),
                        MS949
                );
            } catch (CharacterCodingException ms949Exception) {
                ms949Exception.addSuppressed(utf8Exception);
                throw datasetException(
                        "HTML 원문을 UTF-8 또는 MS949로 해석할 수 없습니다. path="
                                + normalizedFile,
                        ms949Exception
                );
            }
        }
    }

    private byte[] readAllBytes(Path sourceFile) {
        try {
            return Files.readAllBytes(sourceFile);
        } catch (IOException exception) {
            throw datasetException(
                    "HTML 원문 파일을 읽을 수 없습니다. path=" + sourceFile,
                    exception
            );
        }
    }

    private String decode(byte[] bytes, Charset charset)
            throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    }

    private DecodedHtmlSource decoded(
            Path sourceFile,
            String content,
            Charset charset
    ) {
        String normalizedContent = removeBom(content);
        if (normalizedContent.isBlank()) {
            throw datasetException(
                    "HTML 원문 내용이 비어 있습니다. path=" + sourceFile,
                    null
            );
        }
        return new DecodedHtmlSource(
                sourceFile,
                normalizedContent,
                charset
        );
    }

    private String removeBom(String content) {
        if (!content.isEmpty() && content.charAt(0) == UTF_8_BOM) {
            return content.substring(1);
        }
        return content;
    }

    private BusinessException datasetException(
            String message,
            Throwable cause
    ) {
        if (cause == null) {
            return new BusinessException(ErrorCode.DATASET_503_1, message);
        }
        return new BusinessException(
                ErrorCode.DATASET_503_1,
                message,
                cause
        );
    }
}
