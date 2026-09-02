package com.foliolens.backend.disclosure.infrastructure.extraction.facility;

import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 시설투자 표에서 찾은 Fact별 Evidence 후보 묶음.
 *
 * 하나의 Fact에 후보가 여러 개면 추출기가 임의로 하나를 선택하지 않고
 * 모호한 상태 그대로 반환한다.
 */
public record FacilityInvestmentEvidenceExtractionResult(
        Map<FacilityInvestmentFactDefinition, List<DisclosureEvidence>>
                candidates,
        List<String> warnings
) {

    public FacilityInvestmentEvidenceExtractionResult {
        candidates = immutableCandidates(candidates);
        warnings = immutableWarnings(warnings);
    }

    public static FacilityInvestmentEvidenceExtractionResult empty(
            String warning
    ) {
        return new FacilityInvestmentEvidenceExtractionResult(
                Map.of(),
                warning == null || warning.isBlank()
                        ? List.of()
                        : List.of(warning)
        );
    }

    public static FacilityInvestmentEvidenceExtractionResult combine(
            List<FacilityInvestmentEvidenceExtractionResult> results
    ) {
        Objects.requireNonNull(results, "results는 필수입니다.");

        EnumMap<FacilityInvestmentFactDefinition,
                LinkedHashMap<UUID, DisclosureEvidence>> merged =
                new EnumMap<>(FacilityInvestmentFactDefinition.class);
        List<String> warnings = new ArrayList<>();

        for (FacilityInvestmentEvidenceExtractionResult result : results) {
            Objects.requireNonNull(result, "results에는 null이 올 수 없습니다.");
            result.candidates().forEach((definition, evidences) -> {
                LinkedHashMap<UUID, DisclosureEvidence> byId =
                        merged.computeIfAbsent(
                                definition,
                                ignored -> new LinkedHashMap<>()
                        );
                for (DisclosureEvidence evidence : evidences) {
                    byId.putIfAbsent(evidence.evidenceId(), evidence);
                }
            });
            warnings.addAll(result.warnings());
        }

        EnumMap<FacilityInvestmentFactDefinition, List<DisclosureEvidence>>
                copied = new EnumMap<>(FacilityInvestmentFactDefinition.class);
        merged.forEach((definition, byId) ->
                copied.put(definition, List.copyOf(byId.values()))
        );

        return new FacilityInvestmentEvidenceExtractionResult(
                copied,
                warnings
        );
    }

    public List<DisclosureEvidence> candidatesFor(
            FacilityInvestmentFactDefinition definition
    ) {
        Objects.requireNonNull(definition, "definition은 필수입니다.");
        return candidates.getOrDefault(definition, List.of());
    }

    public Optional<DisclosureEvidence> uniqueCandidate(
            FacilityInvestmentFactDefinition definition
    ) {
        List<DisclosureEvidence> values = candidatesFor(definition);
        return values.size() == 1
                ? Optional.of(values.getFirst())
                : Optional.empty();
    }

    public Set<FacilityInvestmentFactDefinition> missingCoreDefinitions() {
        EnumSet<FacilityInvestmentFactDefinition> missing =
                EnumSet.copyOf(
                        FacilityInvestmentFactDefinition.coreDefinitions()
                );
        missing.removeAll(candidates.keySet());
        return Set.copyOf(missing);
    }

    public Set<FacilityInvestmentFactDefinition> ambiguousDefinitions() {
        EnumSet<FacilityInvestmentFactDefinition> ambiguous =
                EnumSet.noneOf(FacilityInvestmentFactDefinition.class);
        candidates.forEach((definition, values) -> {
            if (values.size() > 1) {
                ambiguous.add(definition);
            }
        });
        return Set.copyOf(ambiguous);
    }

    public boolean hasAllCoreCandidates() {
        return missingCoreDefinitions().isEmpty();
    }

    public int candidateCount() {
        return candidates.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    private static Map<FacilityInvestmentFactDefinition,
            List<DisclosureEvidence>> immutableCandidates(
            Map<FacilityInvestmentFactDefinition,
                    List<DisclosureEvidence>> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        EnumMap<FacilityInvestmentFactDefinition, List<DisclosureEvidence>>
                copied = new EnumMap<>(FacilityInvestmentFactDefinition.class);
        values.forEach((definition, evidences) -> {
            Objects.requireNonNull(definition, "Fact 정의는 null일 수 없습니다.");
            if (evidences == null || evidences.isEmpty()) {
                throw new IllegalArgumentException(
                        "후보 목록은 비어 있을 수 없습니다. definition="
                                + definition
                );
            }
            if (evidences.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "Evidence 후보에는 null이 포함될 수 없습니다."
                );
            }
            copied.put(definition, List.copyOf(evidences));
        });
        return Collections.unmodifiableMap(copied);
    }

    private static List<String> immutableWarnings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
