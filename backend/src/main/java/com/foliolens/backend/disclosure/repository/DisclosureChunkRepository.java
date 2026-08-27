package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.domain.DisclosureChunk;
import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DisclosureChunkRepository
        extends JpaRepository<DisclosureChunk, UUID> {

    /**
     * 특정 원문 파일의 모든 청크를 문서 전역 순서로 조회한다.
     */
    List<DisclosureChunk>
    findAllByDisclosureDocumentIdOrderByChunkSequenceNoAsc(
            UUID disclosureDocumentId
    );

    /**
     * 특정 원문 파일에서 지정한 유형의 청크만 문서 순서로 조회한다.
     */
    List<DisclosureChunk>
    findAllByDisclosureDocumentIdAndChunkTypeOrderByChunkSequenceNoAsc(
            UUID disclosureDocumentId,
            DisclosureChunkType chunkType
    );

    /**
     * 특정 Section에 속한 청크를 문서 순서로 조회한다.
     */
    List<DisclosureChunk>
    findAllBySectionIdOrderByChunkSequenceNoAsc(UUID sectionId);

    /**
     * Section 이전의 문서 서두 청크만 문서 순서로 조회한다.
     */
    List<DisclosureChunk>
    findAllByDisclosureDocumentIdAndSectionIsNullOrderByChunkSequenceNoAsc(
            UUID disclosureDocumentId
    );

    long countByDisclosureDocumentId(UUID disclosureDocumentId);

    long countByDisclosureDocumentIdAndChunkType(
            UUID disclosureDocumentId,
            DisclosureChunkType chunkType
    );

    boolean existsByDisclosureDocumentId(UUID disclosureDocumentId);

    /**
     * 문서의 청크를 재생성하기 전에 기존 청크를 한 번에 삭제한다.
     *
     * disclosure_chunk_sources는 DB의 ON DELETE CASCADE로 함께 삭제된다.
     * JPQL 벌크 삭제는 영속성 컨텍스트를 우회하므로 자동 flush 및 clear를 사용한다.
     */
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            DELETE FROM DisclosureChunk chunk
            WHERE chunk.disclosureDocument.id = :documentId
            """)
    int deleteAllByDisclosureDocumentId(
            @Param("documentId") UUID disclosureDocumentId
    );
}
