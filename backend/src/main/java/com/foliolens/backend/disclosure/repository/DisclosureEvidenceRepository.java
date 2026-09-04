package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureEvidenceEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DisclosureEvidenceRepository
        extends JpaRepository<DisclosureEvidenceEntity, UUID> {

    @EntityGraph(attributePaths = {
            "disclosure",
            "disclosureDocument",
            "section",
            "contentBlock"
    })
    List<DisclosureEvidenceEntity>
    findAllByDisclosureDocumentIdOrderByIdAsc(UUID disclosureDocumentId);

    long countByDisclosureDocumentId(UUID disclosureDocumentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM DisclosureEvidenceEntity evidence
            WHERE evidence.disclosureDocument.id = :documentId
            """)
    int deleteAllByDisclosureDocumentId(
            @Param("documentId") UUID disclosureDocumentId
    );
}
