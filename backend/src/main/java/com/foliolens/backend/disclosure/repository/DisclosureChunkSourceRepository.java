package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.domain.DisclosureChunkSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisclosureChunkSourceRepository
        extends JpaRepository<DisclosureChunkSource, UUID> {

    /**
     * 특정 청크의 원본 출처를 청크 내부 순서로 조회한다.
     */
    List<DisclosureChunkSource>
    findAllByDisclosureChunkIdOrderBySourceOrderAsc(
            UUID disclosureChunkId
    );

    /**
     * 특정 원문 ContentBlock을 사용한 모든 청크 출처를 조회한다.
     */
    List<DisclosureChunkSource>
    findAllByContentBlockIdOrderByDisclosureChunkChunkSequenceNoAscSourceOrderAsc(
            UUID contentBlockId
    );

    long countByDisclosureChunkId(UUID disclosureChunkId);

    long countByDisclosureDocumentId(UUID disclosureDocumentId);

    boolean existsByContentBlockId(UUID contentBlockId);
}
