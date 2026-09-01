package com.foliolens.backend.disclosure.infrastructure.search;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 논리 concept·factKey를 금융 도메인 문서가 정의한 원문 레이블과
 * Section 힌트로 변환한다.
 *
 * 첫 수직 구현 범위는 신규시설투자 핵심 Fact다.
 */
@Component
public class DisclosureChunkSearchTermResolver {

    private static final List<String> FACILITY_SECTIONS = List.of(
            "신규시설투자",
            "투자내역",
            "기타 투자판단에 참고할 사항"
    );

    private static final Map<String, TermMapping> FACT_MAPPINGS =
            factMappings();

    private static final Map<String, TermMapping> CONCEPT_MAPPINGS =
            conceptMappings();

    public ResolvedChunkSearchTerms resolve(
            DisclosureChunkSearchCondition condition
    ) {
        if (condition == null) {
            throw new NullPointerException("condition은 필수입니다.");
        }

        LinkedHashSet<String> keywords = new LinkedHashSet<>(
                condition.keywords()
        );
        LinkedHashSet<String> sectionHints = new LinkedHashSet<>(
                condition.sectionHints()
        );
        LinkedHashSet<String> factHintTerms = new LinkedHashSet<>();
        LinkedHashSet<String> resolvedConcepts = new LinkedHashSet<>();
        LinkedHashSet<String> resolvedFactKeys = new LinkedHashSet<>();
        LinkedHashSet<String> unresolvedConcepts = new LinkedHashSet<>();
        LinkedHashSet<String> unresolvedFactKeys = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        List<String> sortedConcepts = condition.concepts().stream()
                .sorted()
                .toList();
        for (String concept : sortedConcepts) {
            TermMapping mapping = CONCEPT_MAPPINGS.get(concept);

            if (mapping == null) {
                unresolvedConcepts.add(concept);
                warnings.add("지원하지 않는 concept입니다: " + concept);
                continue;
            }

            resolvedConcepts.add(concept);
            keywords.addAll(mapping.keywords());
            sectionHints.addAll(mapping.sectionHints());
        }

        List<String> sortedFactKeys = condition.factKeys().stream()
                .sorted()
                .toList();
        for (String factKey : sortedFactKeys) {
            TermMapping mapping = FACT_MAPPINGS.get(factKey);

            if (mapping == null) {
                unresolvedFactKeys.add(factKey);
                warnings.add("지원하지 않는 factKey입니다: " + factKey);
                continue;
            }

            resolvedFactKeys.add(factKey);
            keywords.addAll(mapping.keywords());
            factHintTerms.addAll(mapping.keywords());
            sectionHints.addAll(mapping.sectionHints());
        }

        return new ResolvedChunkSearchTerms(
                List.copyOf(keywords),
                List.copyOf(sectionHints),
                Set.copyOf(factHintTerms),
                Set.copyOf(resolvedConcepts),
                Set.copyOf(resolvedFactKeys),
                Set.copyOf(unresolvedConcepts),
                Set.copyOf(unresolvedFactKeys),
                List.copyOf(warnings)
        );
    }

    private static Map<String, TermMapping> factMappings() {
        Map<String, TermMapping> mappings = new LinkedHashMap<>();

        mappings.put(
                "facility.type",
                facilityMapping("투자구분", "투자 구분")
        );
        mappings.put(
                "facility.target",
                facilityMapping("투자대상", "투자 대상")
        );
        mappings.put(
                "facility.amount",
                facilityMapping("투자금액", "투자 금액", "투자규모")
        );
        mappings.put(
                "facility.equity_amount",
                facilityMapping("자기자본", "자기 자본")
        );
        mappings.put(
                "facility.equity_ratio",
                facilityMapping("자기자본대비", "자기자본 대비")
        );
        mappings.put(
                "facility.purpose",
                facilityMapping("투자목적", "투자 목적")
        );
        mappings.put(
                "facility.start_date",
                facilityMapping("투자기간", "투자 기간", "시작일")
        );
        mappings.put(
                "facility.end_date",
                facilityMapping("투자기간", "투자 기간", "종료일")
        );
        mappings.put(
                "facility.decision_date",
                facilityMapping("이사회결의일", "이사회 결의일", "결정일")
        );

        return Map.copyOf(mappings);
    }

    private static Map<String, TermMapping> conceptMappings() {
        Map<String, TermMapping> mappings = new LinkedHashMap<>();

        mappings.put(
                "FACILITY_INVESTMENT",
                facilityMapping("시설투자", "설비투자")
        );
        mappings.put(
                "CAPACITY_EXPANSION",
                facilityMapping(
                        "생산능력 확대",
                        "생산 능력 확대",
                        "생산시설 확충",
                        "설비 증설"
                )
        );
        mappings.put(
                "INVESTMENT_SCALE",
                facilityMapping(
                        "투자금액",
                        "투자 금액",
                        "투자규모",
                        "자기자본대비"
                )
        );
        mappings.put(
                "COMPLETION_SCHEDULE",
                facilityMapping(
                        "투자기간",
                        "투자 기간",
                        "종료일",
                        "완료예정일"
                )
        );

        return Map.copyOf(mappings);
    }

    private static TermMapping facilityMapping(String... keywords) {
        return new TermMapping(
                List.of(keywords),
                FACILITY_SECTIONS
        );
    }

    private record TermMapping(
            List<String> keywords,
            List<String> sectionHints
    ) {
    }
}
