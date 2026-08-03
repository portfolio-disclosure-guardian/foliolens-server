package com.foliolens.backend.disclosure.repository;

import com.foliolens.backend.disclosure.domain.Disclosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisclosureRepository extends JpaRepository<Disclosure, UUID> {

    /**
     * 대회 데이터셋의 doc_id로 공시 조회
     *
     * 예: exchange_20240424800596
     */
    Optional<Disclosure> findBySourceDocId(String sourceDocId);

    /**
     * DART 접수번호로 공시 조회
     *
     * 예: 20240424800596
     */
    Optional<Disclosure> findByReceiptNo(String receiptNo);

    /**
     * 공시 적재 시 기존 공시와 연결된 Company까지 한 번에 조회
     *
     * Disclosure.company는 LAZY이기 때문에 일반 findAll()을 사용하고
     * 각 공시의 Company를 조회하면 추가 쿼리가 반복될 수 있다.
     */
    @Query("""
            SELECT disclosure
            FROM Disclosure disclosure
            JOIN FETCH disclosure.company
            """)
    List<Disclosure> findAllWithCompany();
}
