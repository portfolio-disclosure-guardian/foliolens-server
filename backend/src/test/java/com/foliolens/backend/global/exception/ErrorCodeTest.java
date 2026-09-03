package com.foliolens.backend.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void 외부_진단에_사용하는_오류_코드는_모두_고유하다() {
        var codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .toList();

        assertThat(codes).doesNotHaveDuplicates();
    }
}
