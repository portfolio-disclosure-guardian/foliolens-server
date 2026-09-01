package com.foliolens.backend.disclosure.infrastructure.search;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DisclosureChunkSearchTermResolverTest {

    private final DisclosureChunkSearchTermResolver resolver =
            new DisclosureChunkSearchTermResolver();

    @Test
    void mergesExplicitTermsWithFacilityFactAndConceptMappings() {
        DisclosureChunkSearchCondition condition = condition(
                Set.of("CAPACITY_EXPANSION"),
                Set.of("facility.amount", "facility.purpose"),
                List.of("투자내역"),
                List.of("차세대 DRAM")
        );

        ResolvedChunkSearchTerms result = resolver.resolve(condition);

        assertThat(result.keywords()).contains(
                "차세대 DRAM",
                "생산능력 확대",
                "투자금액",
                "투자목적"
        );
        assertThat(result.sectionHints()).contains(
                "투자내역",
                "신규시설투자"
        );
        assertThat(result.factHintTerms()).contains(
                "투자금액",
                "투자목적"
        );
        assertThat(result.resolvedConcepts())
                .containsExactly("CAPACITY_EXPANSION");
        assertThat(result.resolvedFactKeys())
                .containsExactlyInAnyOrder(
                        "facility.amount",
                        "facility.purpose"
                );
        assertThat(result.unresolvedConcepts()).isEmpty();
        assertThat(result.unresolvedFactKeys()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.hasExecutableTerms()).isTrue();
    }

    @Test
    void keepsUnsupportedInputsVisibleWithoutGuessing() {
        DisclosureChunkSearchCondition condition = condition(
                Set.of("UNKNOWN_CONCEPT"),
                Set.of("facility.unknown"),
                List.of(),
                List.of()
        );

        ResolvedChunkSearchTerms result = resolver.resolve(condition);

        assertThat(result.hasExecutableTerms()).isFalse();
        assertThat(result.unresolvedConcepts())
                .containsExactly("UNKNOWN_CONCEPT");
        assertThat(result.unresolvedFactKeys())
                .containsExactly("facility.unknown");
        assertThat(result.warnings()).containsExactlyInAnyOrder(
                "지원하지 않는 concept입니다: UNKNOWN_CONCEPT",
                "지원하지 않는 factKey입니다: facility.unknown"
        );
    }

    private DisclosureChunkSearchCondition condition(
            Set<String> concepts,
            Set<String> factKeys,
            List<String> sectionHints,
            List<String> keywords
    ) {
        return new DisclosureChunkSearchCondition(
                Set.of(UUID.randomUUID()),
                Set.of(),
                concepts,
                factKeys,
                sectionHints,
                keywords,
                Set.of(
                        DisclosureChunkType.TEXT,
                        DisclosureChunkType.TABLE
                ),
                10,
                0
        );
    }
}
