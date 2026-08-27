package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentChunkStatus;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisclosureDocumentRepository extends JpaRepository<DisclosureDocument, UUID> {

    /**
     * NFC 정규화 상대경로로 원문 파일 조회
     *
     * disclosure_documents의 멱등성 기준으로 사용한다.
     */
    Optional<DisclosureDocument> findByNormalizedRelativePath(String normalizedRelativePath);

    /**
     * 동일한 정규화 경로가 이미 적재되어 있는지 확인
     */
    boolean existsByNormalizedRelativePath(String normalizedRelativePath);

    /**
     * 특정 공시에 포함된 모든 원문 파일을 파일명순으로 조회
     */
    List<DisclosureDocument> findAllByDisclosureIdOrderByFileNameAsc(UUID disclosureId);

    /**
     * 특정 공시에 등록된 실제 원문 파일 수 조회
     *
     * Disclosure.expectedFileCount와 비교할 때 사용한다.
     */
    long countByDisclosureId(UUID disclosureId);

    /**
     * 동일한 SHA-256을 가진 파일 조회
     *
     * 서로 다른 경로의 파일이 같은 내용을 가질 수 있으므로
     * Optional이 아니라 List로 반환한다.
     */
    List<DisclosureDocument> findAllBySha256(String sha256);

    /**
     * 콘텐츠 형식별 문서를 ID 순서로 나누어 조회한다.
     *
     * XML 구조 배치 조사에서는 DART_XML 문서만 일정 개수씩 읽기 위해 사용한다.
     * 연관된 공시 메타데이터도 결과 행에 필요하므로 EntityGraph로 함께 조회한다.
     */
    @EntityGraph(attributePaths = "disclosure")
    Slice<DisclosureDocument> findAllByContentFormatOrderByIdAsc(
            DisclosureDocumentContentFormat contentFormat,
            Pageable pageable
    );

    /**
     * 파싱 상태별 파일을 제한된 배치 크기로 조회
     *
     * 파싱 과정에서 Disclosure의 sourceGroup 등이 필요하므로
     * EntityGraph로 연관된 Disclosure를 함께 조회한다.
     */
    @EntityGraph(attributePaths = "disclosure")
    Slice<DisclosureDocument> findAllByParseStatusOrderByIdAsc(
            DisclosureDocumentParseStatus parseStatus,
            Pageable pageable
    );

    /**
     * 아직 처리하지 않은 특정 콘텐츠 형식의 문서를 제한된 수만큼 조회한다.
     *
     * 파싱 결과를 저장하면 상태가 PENDING에서 COMPLETED 또는 FAILED로
     * 변경되므로, 저장 배치는 항상 첫 페이지를 조회해 다음 대상을 고른다.
     */
    @EntityGraph(attributePaths = "disclosure")
    Slice<DisclosureDocument>
    findAllByContentFormatAndParseStatusOrderByIdAsc(
            DisclosureDocumentContentFormat contentFormat,
            DisclosureDocumentParseStatus parseStatus,
            Pageable pageable
    );

    /**
     * 파싱은 완료됐지만 아직 청킹하지 않은 특정 콘텐츠 형식의 문서를
     * 제한된 수만큼 조회한다.
     *
     * 처리 결과가 PENDING에서 COMPLETED 또는 FAILED로 바뀌므로
     * 청킹 배치는 항상 첫 페이지를 조회해 다음 대상을 선택한다.
     */
    @EntityGraph(attributePaths = "disclosure")
    Slice<DisclosureDocument>
    findAllByContentFormatAndParseStatusAndChunkStatusOrderByIdAsc(
            DisclosureDocumentContentFormat contentFormat,
            DisclosureDocumentParseStatus parseStatus,
            DisclosureDocumentChunkStatus chunkStatus,
            Pageable pageable
    );

    /**
     * 파싱 상태별 파일 수 조회
     */
    long countByParseStatus(DisclosureDocumentParseStatus parseStatus);

    /**
     * 청킹 상태별 원문 문서 수를 조회한다.
     */
    long countByChunkStatus(DisclosureDocumentChunkStatus chunkStatus);

    /**
     * 파일 확장자별 개수 조회
     *
     * 기대값:
     * xml  = 4,616
     * html = 3
     * pdf  = 3
     */
    long countByFileExtension(String fileExtension);

    /**
     * 실제 콘텐츠 형식별 개수 조회
     *
     * 확장자가 xml이지만 실제 내용이 HTML인 거래소공시를
     * 구분해서 집계할 때 사용한다.
     */
    long countByContentFormat(DisclosureDocumentContentFormat contentFormat);
}
