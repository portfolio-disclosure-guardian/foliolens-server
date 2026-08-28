package com.foliolens.backend.disclosure.infrastructure.search;

import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 공시 본문을 검색하기 전에 기업·기간·공시 유형으로 후보 공시를
 * 제한하는 메타데이터 검색 조건.
 *
 * null 컬렉션은 해당 필터를 지정하지 않은 것으로 보고 빈 컬렉션으로 정규화
 *
 * 예:
 * 질문:
 * 삼성전자가 2026년 1월 1일부터 8월 28일까지
 * 제출한 공시 중에서 시설투자 관련 주요사항보고서를 최대 20개 찾아라. 원공시와 정정공시를 모두 포함해라.
 *
 * 답변:
 * DisclosureMetadataSearchCondition(
 *     companyIds = [삼성전자 ID],
 *     receiptDateFrom = 2026-01-01,
 *     receiptDateTo = 2026-08-28,
 *     sourceGroups = [MAJOR_EVENT],
 *     categories = [FACILITY_INVESTMENT],
 *     correctionFilter = ALL,
 *     limit = 20
 * )
 */
public record DisclosureMetadataSearchCondition(
        /*
        * 빈 목록이면 특정 기업을 제한하지 않습니다.
        * 다만 전체 기업을 검색하더라도 limit은 최대 50개로 제한
        *
        * Set.of(samsungId, skHynixId)
        * 이면 삼성전자와 SK하이닉스 공시만 검색
        * */
        Set<UUID> companyIds, // 검색 대상 기업 ID 목록
        LocalDate receiptDateFrom,
        LocalDate receiptDateTo,
        LocalDate asOf, // 이 날짜 이후에 접수된 공시는 검색하지 않는 기준 시점
        Set<DisclosureSourceGroup> sourceGroups, // 대회 데이터의 공시 원본 그룹
        Set<DisclosureCategory> categories, // 서비스 내부에서 사용하는 공시 대분류
        Set<String> rawSubtypes, // 데이터셋에 기록된 원문 세부 공시 유형 - Manifest에 기록된 세부 공시 유형 원문
        List<String> titleTerms, // 공시 보고서명에서 찾을 검색어
        CorrectionFilter correctionFilter, // 정정공시를 포함하는 방법
        int limit // 반환할 최대 공시 후보 수
) {

    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 50;

    public DisclosureMetadataSearchCondition {
        companyIds = immutableSet(companyIds, "companyIds");
        sourceGroups = immutableSet(sourceGroups, "sourceGroups");
        categories = immutableSet(categories, "categories");
        rawSubtypes = immutableTextSet(rawSubtypes, "rawSubtypes");
        titleTerms = immutableTextList(titleTerms, "titleTerms");

        correctionFilter = correctionFilter == null
                ? CorrectionFilter.ALL
                : correctionFilter;

        if (receiptDateFrom != null
                && receiptDateTo != null
                && receiptDateFrom.isAfter(receiptDateTo)) {
            throw new IllegalArgumentException(
                    "receiptDateFrom은 receiptDateTo보다 뒤일 수 없습니다."
            );
        }

        if (receiptDateFrom != null
                && asOf != null
                && receiptDateFrom.isAfter(asOf)) {
            throw new IllegalArgumentException(
                    "receiptDateFrom은 asOf보다 뒤일 수 없습니다."
            );
        }

        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit은 " + MIN_LIMIT + " 이상 "
                            + MAX_LIMIT + " 이하여야 합니다."
            );
        }
    }

    /**
     * receiptDateTo와 asOf가 모두 있으면 더 이른 날짜를 실제 조회 상한으로
     * 사용한다.
     */
    public LocalDate effectiveReceiptDateTo() {
        if (receiptDateTo == null) {
            return asOf;
        }

        if (asOf == null) {
            return receiptDateTo;
        }

        return receiptDateTo.isBefore(asOf)
                ? receiptDateTo
                : asOf;
    }

    private static <T> Set<T> immutableSet(
            Set<T> values,
            String fieldName
    ) {
        if (values == null) {
            return Set.of();
        }

        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    fieldName + "에는 null 원소가 포함될 수 없습니다."
            );
        }

        return Set.copyOf(values);
    }

    private static Set<String> immutableTextSet(
            Set<String> values,
            String fieldName
    ) {
        if (values == null) {
            return Set.of();
        }

        return values.stream()
                .map(value -> requireText(value, fieldName))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static List<String> immutableTextList(
            List<String> values,
            String fieldName
    ) {
        if (values == null) {
            return List.of();
        }

        return values.stream()
                .map(value -> requireText(value, fieldName))
                .distinct()
                .toList();
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "에는 빈 문자열이 포함될 수 없습니다."
            );
        }

        return value.strip();
    }
}
