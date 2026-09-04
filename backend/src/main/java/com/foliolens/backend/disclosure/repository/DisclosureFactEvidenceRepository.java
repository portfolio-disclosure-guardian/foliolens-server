package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.infrastructure.persistence.fact.DisclosureFactEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisclosureFactEvidenceRepository
        extends JpaRepository<DisclosureFactEvidenceEntity, UUID> {

    List<DisclosureFactEvidenceEntity>
    findAllByDisclosureFactIdOrderByEvidenceOrderAsc(UUID disclosureFactId);

    long countByDisclosureDocumentId(UUID disclosureDocumentId);
}
