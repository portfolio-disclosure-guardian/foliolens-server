package com.foliolens.backend.disclosure.infrastructure.persistence.fact;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.repository.DisclosureContentBlockRepository;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.repository.DisclosureEvidenceRepository;
import com.foliolens.backend.disclosure.repository.DisclosureFactRepository;
import com.foliolens.backend.disclosure.repository.DisclosureSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DisclosureFactPersistenceService {

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosureSectionRepository sectionRepository;
    private final DisclosureContentBlockRepository contentBlockRepository;
    private final DisclosureEvidenceRepository evidenceRepository;
    private final DisclosureFactRepository factRepository;
    private final DisclosureFactEntityMapper mapper;

    public DisclosureFactPersistenceService(
            DisclosureDocumentRepository documentRepository,
            DisclosureSectionRepository sectionRepository,
            DisclosureContentBlockRepository contentBlockRepository,
            DisclosureEvidenceRepository evidenceRepository,
            DisclosureFactRepository factRepository,
            DisclosureFactEntityMapper mapper
    ) {
        this.documentRepository = documentRepository;
        this.sectionRepository = sectionRepository;
        this.contentBlockRepository = contentBlockRepository;
        this.evidenceRepository = evidenceRepository;
        this.factRepository = factRepository;
        this.mapper = mapper;
    }

    /**
     * 한 원문 파일의 검증된 Evidence와 Fact를 트랜잭션 안에서 교체한다.
     * 같은 결정적 ID 입력으로 반복 실행해도 최종 행과 연결 수는 같다.
     */
    @Transactional
    public DisclosureFactPersistenceResult replaceVerifiedFacts(
            UUID disclosureDocumentId,
            Collection<DisclosureEvidence> evidences,
            Collection<DisclosureFact> facts
    ) {
        Objects.requireNonNull(disclosureDocumentId, "disclosureDocumentId는 필수입니다.");
        List<DisclosureEvidence> evidenceList = immutableList(evidences, "evidences");
        List<DisclosureFact> factList = immutableList(facts, "facts");
        validateInput(disclosureDocumentId, evidenceList, factList);

        int deletedFactCount = factRepository.deleteAllByDisclosureDocumentId(
                disclosureDocumentId
        );
        int deletedEvidenceCount = evidenceRepository
                .deleteAllByDisclosureDocumentId(disclosureDocumentId);

        DisclosureDocument document = documentRepository
                .findWithDisclosureById(disclosureDocumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 DisclosureDocument입니다: "
                                + disclosureDocumentId
                ));

        Map<UUID, DisclosureSection> sectionsById = loadSections(evidenceList);
        Map<UUID, DisclosureContentBlock> blocksById = loadBlocks(evidenceList);

        List<DisclosureEvidenceEntity> evidenceEntities = evidenceList.stream()
                .map(evidence -> mapper.toEntity(
                        evidence,
                        document,
                        evidence.sectionId() == null
                                ? null
                                : sectionsById.get(evidence.sectionId()),
                        evidence.contentBlockId() == null
                                ? null
                                : blocksById.get(evidence.contentBlockId())
                ))
                .toList();

        Map<UUID, DisclosureEvidenceEntity> savedEvidencesById = evidenceRepository
                .saveAllAndFlush(evidenceEntities)
                .stream()
                .collect(Collectors.toMap(
                        DisclosureEvidenceEntity::getId,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException(
                                    "중복 Evidence ID입니다: " + left.getId()
                            );
                        },
                        LinkedHashMap::new
                ));

        List<DisclosureFactEntity> factEntities = factList.stream()
                .map(fact -> mapper.toEntity(
                        fact,
                        document,
                        savedEvidencesById
                ))
                .toList();
        factRepository.saveAllAndFlush(factEntities);

        int linkCount = factList.stream()
                .mapToInt(fact -> fact.evidenceIds().size())
                .sum();
        return new DisclosureFactPersistenceResult(
                deletedFactCount,
                deletedEvidenceCount,
                factEntities.size(),
                evidenceEntities.size(),
                linkCount
        );
    }

    @Transactional(readOnly = true)
    public List<DisclosureFact> findFactsByDocumentId(UUID disclosureDocumentId) {
        Objects.requireNonNull(disclosureDocumentId, "disclosureDocumentId는 필수입니다.");
        return factRepository
                .findAllByDisclosureDocumentIdOrderByFactKeyAsc(disclosureDocumentId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DisclosureEvidence> findEvidencesByDocumentId(
            UUID disclosureDocumentId
    ) {
        Objects.requireNonNull(disclosureDocumentId, "disclosureDocumentId는 필수입니다.");
        return evidenceRepository
                .findAllByDisclosureDocumentIdOrderByIdAsc(disclosureDocumentId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    private void validateInput(
            UUID documentId,
            List<DisclosureEvidence> evidences,
            List<DisclosureFact> facts
    ) {
        Map<UUID, DisclosureEvidence> evidencesById = uniqueById(
                evidences,
                DisclosureEvidence::evidenceId,
                "Evidence"
        );
        uniqueById(facts, DisclosureFact::factId, "Fact");

        for (DisclosureEvidence evidence : evidences) {
            if (!documentId.equals(evidence.disclosureDocumentId())) {
                throw new IllegalArgumentException(
                        "다른 문서의 Evidence가 포함되어 있습니다: "
                                + evidence.evidenceId()
                );
            }
            if (evidence.status() != EvidenceStatus.VERIFIED) {
                throw new IllegalArgumentException(
                        "VERIFIED Evidence만 저장할 수 있습니다: "
                                + evidence.evidenceId()
                );
            }
        }

        for (DisclosureFact fact : facts) {
            if (!documentId.equals(fact.disclosureDocumentId())) {
                throw new IllegalArgumentException(
                        "다른 문서의 Fact가 포함되어 있습니다: " + fact.factId()
                );
            }
            if (fact.validationStatus() != FactValidationStatus.VERIFIED) {
                throw new IllegalArgumentException(
                        "VERIFIED Fact만 저장할 수 있습니다: " + fact.factId()
                );
            }
            for (UUID evidenceId : fact.evidenceIds()) {
                if (!evidencesById.containsKey(evidenceId)) {
                    throw new IllegalArgumentException(
                            "Fact가 입력에 없는 Evidence를 참조합니다: " + evidenceId
                    );
                }
            }
        }
    }

    private Map<UUID, DisclosureSection> loadSections(
            List<DisclosureEvidence> evidences
    ) {
        Set<UUID> ids = evidences.stream()
                .map(DisclosureEvidence::sectionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return loadRequired(
                ids,
                sectionRepository.findAllById(ids),
                DisclosureSection::getId,
                "DisclosureSection"
        );
    }

    private Map<UUID, DisclosureContentBlock> loadBlocks(
            List<DisclosureEvidence> evidences
    ) {
        Set<UUID> ids = evidences.stream()
                .map(DisclosureEvidence::contentBlockId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return loadRequired(
                ids,
                contentBlockRepository.findAllById(ids),
                DisclosureContentBlock::getId,
                "DisclosureContentBlock"
        );
    }

    private static <T> Map<UUID, T> loadRequired(
            Set<UUID> requestedIds,
            List<T> entities,
            Function<T, UUID> idExtractor,
            String type
    ) {
        Map<UUID, T> result = entities.stream().collect(Collectors.toMap(
                idExtractor,
                Function.identity()
        ));
        if (!result.keySet().containsAll(requestedIds)) {
            Set<UUID> missing = requestedIds.stream()
                    .filter(id -> !result.containsKey(id))
                    .collect(Collectors.toSet());
            throw new IllegalArgumentException(
                    "존재하지 않는 " + type + " ID입니다: " + missing
            );
        }
        return result;
    }

    private static <T> List<T> immutableList(Collection<T> values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + "는 null일 수 없습니다.");
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + "에는 null이 포함될 수 없습니다.");
        }
        return List.copyOf(values);
    }

    private static <T> Map<UUID, T> uniqueById(
            List<T> values,
            Function<T, UUID> idExtractor,
            String type
    ) {
        Map<UUID, T> result = new LinkedHashMap<>();
        for (T value : values) {
            UUID id = Objects.requireNonNull(
                    idExtractor.apply(value),
                    type + " ID는 필수입니다."
            );
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("중복 " + type + " ID입니다: " + id);
            }
        }
        return result;
    }
}
