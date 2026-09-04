package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactEntity;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactEntityMapper;
import com.foliolens.backend.disclosure.repository.DisclosureFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 선택된 공시에서 요청한 VERIFIED Fact와 연결 Evidence를 조회한다.
 */
@Service
@Transactional(readOnly = true)
public class DisclosureFactLookupService {

    public static final String LOOKUP_VERSION = "fact-lookup-v1";

    private final DisclosureFactRepository factRepository;
    private final DisclosureFactEntityMapper entityMapper;

    public DisclosureFactLookupService(
            DisclosureFactRepository factRepository,
            DisclosureFactEntityMapper entityMapper
    ) {
        this.factRepository = Objects.requireNonNull(
                factRepository,
                "factRepository는 필수입니다."
        );
        this.entityMapper = Objects.requireNonNull(
                entityMapper,
                "entityMapper는 필수입니다."
        );
    }

    public DisclosureFactLookupResult lookup(
            Set<UUID> disclosureIds,
            List<String> factKeys
    ) {
        LinkedHashSet<UUID> normalizedDisclosureIds = normalizeIds(
                disclosureIds
        );
        LinkedHashSet<String> normalizedFactKeys = normalizeFactKeys(
                factKeys
        );

        if (normalizedDisclosureIds.isEmpty()
                || normalizedFactKeys.isEmpty()) {
            return new DisclosureFactLookupResult(
                    List.of(),
                    List.of(),
                    List.copyOf(normalizedFactKeys)
            );
        }

        List<DisclosureFactEntity> entities = factRepository.findAllForLookup(
                normalizedDisclosureIds,
                normalizedFactKeys,
                FactValidationStatus.VERIFIED
        );

        List<DisclosureFact> facts = entities.stream()
                .map(entityMapper::toDomain)
                .toList();

        LinkedHashMap<UUID, DisclosureEvidence> evidencesById =
                new LinkedHashMap<>();
        entities.stream()
                .flatMap(entity -> entity.getEvidenceLinks().stream())
                .map(link -> entityMapper.toDomain(
                        link.getDisclosureEvidence()
                ))
                .filter(evidence -> evidence.status() == EvidenceStatus.VERIFIED)
                .forEach(evidence -> evidencesById.putIfAbsent(
                        evidence.evidenceId(),
                        evidence
                ));

        Set<String> foundFactKeys = facts.stream()
                .map(DisclosureFact::factKey)
                .collect(java.util.stream.Collectors.toSet());
        List<String> missingFactKeys = normalizedFactKeys.stream()
                .filter(factKey -> !foundFactKeys.contains(factKey))
                .toList();

        return new DisclosureFactLookupResult(
                facts,
                List.copyOf(evidencesById.values()),
                missingFactKeys
        );
    }

    private LinkedHashSet<UUID> normalizeIds(Set<UUID> values) {
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (UUID value : values) {
            result.add(Objects.requireNonNull(
                    value,
                    "disclosureIds에는 null이 포함될 수 없습니다."
            ));
        }
        return result;
    }

    private LinkedHashSet<String> normalizeFactKeys(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "factKeys에는 빈 값이 포함될 수 없습니다."
                );
            }
            result.add(value.strip());
        }
        return result;
    }
}
