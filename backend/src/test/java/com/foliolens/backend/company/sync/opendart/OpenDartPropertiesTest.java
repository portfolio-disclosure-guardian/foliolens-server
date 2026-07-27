package com.foliolens.backend.company.sync.opendart;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class OpenDartPropertiesTest {

    private static final String VALID_API_KEY =
            "a".repeat(40);

    @Test
    void 올바른_설정은_검증을_통과한다() {
        OpenDartProperties properties =
                createProperties(
                        true,
                        VALID_API_KEY,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60),
                        50 * 1024 * 1024
                );

        assertDoesNotThrow(
                properties::validateForRequest
        );
    }

    @Test
    void 비활성화된_설정은_예외가_발생한다() {
        OpenDartProperties properties =
                createProperties(
                        false,
                        VALID_API_KEY,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60),
                        50 * 1024 * 1024
                );

        assertThrows(
                IllegalStateException.class,
                properties::validateForRequest
        );
    }

    @Test
    void 인증키가_비어있으면_예외가_발생한다() {
        OpenDartProperties properties =
                createProperties(
                        true,
                        "",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60),
                        50 * 1024 * 1024
                );

        assertThrows(
                IllegalStateException.class,
                properties::validateForRequest
        );
    }

    @Test
    void 인증키가_40자리가_아니면_예외가_발생한다() {
        OpenDartProperties properties =
                createProperties(
                        true,
                        "short-key",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60),
                        50 * 1024 * 1024
                );

        assertThrows(
                IllegalStateException.class,
                properties::validateForRequest
        );
    }

    @Test
    void 연결제한시간이_0이면_예외가_발생한다() {
        OpenDartProperties properties =
                createProperties(
                        true,
                        VALID_API_KEY,
                        Duration.ZERO,
                        Duration.ofSeconds(60),
                        50 * 1024 * 1024
                );

        assertThrows(
                IllegalStateException.class,
                properties::validateForRequest
        );
    }

    @Test
    void ZIP_허용크기가_0이면_예외가_발생한다() {
        OpenDartProperties properties =
                createProperties(
                        true,
                        VALID_API_KEY,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60),
                        0
                );

        assertThrows(
                IllegalStateException.class,
                properties::validateForRequest
        );
    }

    private OpenDartProperties createProperties(
            boolean enabled,
            String apiKey,
            Duration connectTimeout,
            Duration requestTimeout,
            int maxZipBytes
    ) {
        return new OpenDartProperties(
                enabled,
                URI.create(
                        "https://opendart.fss.or.kr/api/corpCode.xml"
                ),
                apiKey,
                connectTimeout,
                requestTimeout,
                maxZipBytes
        );
    }
}