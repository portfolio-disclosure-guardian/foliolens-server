package com.foliolens.backend.disclosure.domain.fact.facility.normalization;

import com.foliolens.backend.disclosure.domain.fact.FactAvailabilityStatus;
import com.foliolens.backend.disclosure.domain.fact.FactNormalizationStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValue;
import com.foliolens.backend.disclosure.domain.fact.FactValueType;

import java.util.Objects;

/**
 * 원문값을 표준 {@link FactValue}로 바꾸려는 시도의 결과.
 *
 * 성공(MAPPED)하지 못한 경우에도 누락·모호·변환 실패 상태를 0이나 임의
 * 값으로 대체하지 않고 그대로 표현한다.
 */
public record FactValueNormalizationResult(
        FactValueType valueType,
        FactValue normalizedValue,
        String normalizedUnit,
        FactAvailabilityStatus availabilityStatus,
        FactNormalizationStatus normalizationStatus,
        String detail
) {

    public FactValueNormalizationResult {
        valueType = Objects.requireNonNull(
                valueType,
                "valueType은 필수입니다."
        );
        availabilityStatus = Objects.requireNonNull(
                availabilityStatus,
                "availabilityStatus는 필수입니다."
        );
        normalizationStatus = Objects.requireNonNull(
                normalizationStatus,
                "normalizationStatus는 필수입니다."
        );

        if (normalizationStatus == FactNormalizationStatus.MAPPED) {
            if (normalizedValue == null) {
                throw new IllegalArgumentException(
                        "MAPPED 결과에는 normalizedValue가 필요합니다."
                );
            }
            if (normalizedValue.valueType() != valueType) {
                throw new IllegalArgumentException(
                        "normalizedValue의 타입이 valueType과 다릅니다."
                );
            }
            if (availabilityStatus != FactAvailabilityStatus.AVAILABLE) {
                throw new IllegalArgumentException(
                        "MAPPED 결과의 availabilityStatus는 AVAILABLE이어야 합니다."
                );
            }
        } else if (normalizedValue != null) {
            throw new IllegalArgumentException(
                    "MAPPED가 아닌 결과에는 normalizedValue를 둘 수 없습니다."
            );
        }
    }

    public boolean mapped() {
        return normalizationStatus == FactNormalizationStatus.MAPPED;
    }

    public static FactValueNormalizationResult mapped(
            FactValue value,
            String normalizedUnit
    ) {
        Objects.requireNonNull(value, "value는 필수입니다.");
        return new FactValueNormalizationResult(
                value.valueType(),
                value,
                normalizedUnit,
                FactAvailabilityStatus.AVAILABLE,
                FactNormalizationStatus.MAPPED,
                null
        );
    }

    public static FactValueNormalizationResult missing(FactValueType valueType) {
        return new FactValueNormalizationResult(
                valueType,
                null,
                null,
                FactAvailabilityStatus.NOT_STATED,
                FactNormalizationStatus.MISSING,
                "원문 값이 없습니다."
        );
    }

    public static FactValueNormalizationResult ambiguous(
            FactValueType valueType,
            String detail
    ) {
        return new FactValueNormalizationResult(
                valueType,
                null,
                null,
                FactAvailabilityStatus.AMBIGUOUS,
                FactNormalizationStatus.AMBIGUOUS,
                requireDetail(detail)
        );
    }

    public static FactValueNormalizationResult unmapped(
            FactValueType valueType,
            String detail
    ) {
        return new FactValueNormalizationResult(
                valueType,
                null,
                null,
                FactAvailabilityStatus.PARSE_FAILED,
                FactNormalizationStatus.UNMAPPED,
                requireDetail(detail)
        );
    }

    private static String requireDetail(String detail) {
        return detail == null || detail.isBlank()
                ? "정규화할 수 없습니다."
                : detail.strip();
    }
}
