package com.foliolens.backend.disclosure.domain;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum DisclosureFileFormat {

    XML("xml"),
    PDF_HTML("pdf+html");

    private final String value;

    DisclosureFileFormat(String value) {
        this.value = value;
    }

    public static DisclosureFileFormat fromValue(String value) {
        return Arrays.stream(values())
                .filter(format -> format.value.equals(value))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("지원하지 않는 공시 파일 형식입니다: " + value)
                );
    }
}
