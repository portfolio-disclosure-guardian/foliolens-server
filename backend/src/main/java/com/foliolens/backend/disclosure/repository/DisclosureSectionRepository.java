package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.domain.DisclosureSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DisclosureSectionRepository
        extends JpaRepository<DisclosureSection, UUID> {

    /**
     * 특정 원문 파일의 모든 섹션을 원문 등장 순서로 조회한다.
     */
    List<DisclosureSection>
    findAllByDisclosureDocumentIdOrderBySequenceNoAsc(
            UUID disclosureDocumentId
    );

    /**
     * 특정 원문 파일의 최상위 섹션만 원문 등장 순서로 조회한다.
     */
    List<DisclosureSection>
    findAllByDisclosureDocumentIdAndParentSectionIsNullOrderBySequenceNoAsc(
            UUID disclosureDocumentId
    );

    /**
     * 특정 원문 파일에 저장된 섹션 수를 반환한다.
     */
    long countByDisclosureDocumentId(UUID disclosureDocumentId);

    /**
     * 특정 원문 파일에 저장된 섹션이 존재하는지 확인한다.
     */
    boolean existsByDisclosureDocumentId(UUID disclosureDocumentId);

    /**
     * 재파싱 전에 특정 원문 파일의 기존 섹션을 한 번에 삭제한다.
     *
     * disclosure_content_blocks를 먼저 삭제한 뒤 호출한다.
     * JPQL 벌크 삭제는 영속성 컨텍스트를 우회하므로
     * 자동 flush 및 clear를 사용한다.
     */
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            DELETE FROM DisclosureSection section
            WHERE section.disclosureDocument.id = :documentId
            """)
    int deleteAllByDisclosureDocumentId(
            @Param("documentId") UUID disclosureDocumentId
    );
}

