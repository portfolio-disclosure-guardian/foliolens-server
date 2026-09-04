package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DisclosureFactRepository
        extends JpaRepository<DisclosureFactEntity, UUID> {

    @EntityGraph(attributePaths = {
            "disclosure",
            "disclosureDocument",
            "evidenceLinks",
            "evidenceLinks.disclosureEvidence"
    })
    List<DisclosureFactEntity>
    findAllByDisclosureDocumentIdOrderByFactKeyAsc(UUID disclosureDocumentId);

    @EntityGraph(attributePaths = {
            "disclosure",
            "disclosureDocument",
            "evidenceLinks",
            "evidenceLinks.disclosureEvidence"
    })
    @Query("""
            SELECT DISTINCT fact
            FROM DisclosureFactEntity fact
            WHERE fact.disclosure.id IN :disclosureIds
              AND fact.factKey IN :factKeys
              AND fact.validationStatus = :validationStatus
            ORDER BY fact.disclosure.id, fact.factKey, fact.id
            """)
    List<DisclosureFactEntity> findAllForLookup(
            @Param("disclosureIds") Collection<UUID> disclosureIds,
            @Param("factKeys") Collection<String> factKeys,
            @Param("validationStatus") FactValidationStatus validationStatus
    );

    long countByDisclosureDocumentId(UUID disclosureDocumentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM DisclosureFactEntity fact
            WHERE fact.disclosureDocument.id = :documentId
            """)
    int deleteAllByDisclosureDocumentId(
            @Param("documentId") UUID disclosureDocumentId
    );
}
