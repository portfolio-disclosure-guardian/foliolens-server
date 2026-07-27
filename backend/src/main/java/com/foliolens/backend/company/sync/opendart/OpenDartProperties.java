package com.foliolens.backend.company.sync.opendart;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

// @ConfigurationProperties는 application.yml에 있는 여러 설정값을 하나의 Java 객체로 묶어서 가져오는 애노테이션
@ConfigurationProperties(prefix = "external.opendart")
public record OpenDartProperties(
        boolean enabled,
        URI corpCodeUrl,
        String apiKey,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxZipBytes
) {

    public void validateForRequest() {
        if (!enabled) {
            throw new IllegalStateException("OpenDART 연동이 비활성화되어 있습니다.");
        }

        if (corpCodeUrl == null) {
            throw new IllegalStateException("OpenDART 고유번호 API 주소가 설정되지 않았습니다.");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenDART 인증키가 설정되지 않았습니다.");
        }

        if (apiKey.trim().length() != 40) {
            throw new IllegalStateException("OpenDART 인증키는 40자리여야 합니다.");
        }

        if (connectTimeout == null || connectTimeout.isNegative()) {
            throw new IllegalStateException("OpenDART 연결 제한시간이 올바르지 않습니다.");
        }

        if (requestTimeout == null || requestTimeout.isNegative()) {
            throw new IllegalStateException("OpenDART 요청 제한시간이 올바르지 않습니다.");
        }

        if (maxZipBytes <= 0) {
            throw new IllegalStateException("OpenDART 최대 ZIP 크기가 올바르지 않습니다.");
        }
    }
}
