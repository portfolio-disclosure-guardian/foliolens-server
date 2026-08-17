package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlockType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DisclosureContentBlockRepository
        extends JpaRepository<DisclosureContentBlock, UUID> {

    /**
     * 특정 원문 파일의 모든 블록을 원문 등장 순서로 조회한다.
     */
    List<DisclosureContentBlock>
    findAllByDisclosureDocumentIdOrderBySequenceNoAsc(
            UUID disclosureDocumentId
    );

    /**
     * 특정 섹션에 직접 포함된 블록을 원문 등장 순서로 조회한다.
     */
    List<DisclosureContentBlock>
    findAllBySectionIdOrderBySequenceNoAsc(UUID sectionId);

    /**
     * 섹션 시작 전에 나타난 preamble 블록을 원문 순서로 조회한다.
     */
    List<DisclosureContentBlock>
    findAllByDisclosureDocumentIdAndSectionIsNullOrderBySequenceNoAsc(
            UUID disclosureDocumentId
    );

    /**
     * 특정 원문 파일에서 지정한 유형의 블록만 조회한다.
     */
    List<DisclosureContentBlock>
    findAllByDisclosureDocumentIdAndBlockTypeOrderBySequenceNoAsc(
            UUID disclosureDocumentId,
            DisclosureContentBlockType blockType
    );

    /**
     * 특정 원문 파일에 저장된 전체 블록 수를 반환한다.
     */
    long countByDisclosureDocumentId(UUID disclosureDocumentId);

    /**
     * 특정 원문 파일에 저장된 블록이 존재하는지 확인한다.
     */
    boolean existsByDisclosureDocumentId(UUID disclosureDocumentId);

    /**
     * 재파싱 전에 특정 원문 파일의 기존 블록을 한 번에 삭제한다.
     *
     * 섹션 삭제보다 먼저 호출해야 한다. JPQL 벌크 삭제는
     * 영속성 컨텍스트를 우회하므로 자동 flush 및 clear를 사용한다.
     */
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            DELETE FROM DisclosureContentBlock block
            WHERE block.disclosureDocument.id = :documentId
            """)
    int deleteAllByDisclosureDocumentId(
            @Param("documentId") UUID disclosureDocumentId
    );
}

