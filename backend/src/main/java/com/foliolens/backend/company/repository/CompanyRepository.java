package com.foliolens.backend.company.repository;

import com.foliolens.backend.company.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    // 기업 고유번호 조회
    Optional<Company> findByCorpCode(String corpCode);

    // 종목코드 조회
    Optional<Company> findByStockCode(String stockCode);

    // 중복 확인
    boolean existsByCorpCode(String corpCode);
    boolean existsByStockCode(String stockCode);


    /**
     * 검색 쿼리
     * 회사명 또는 중목코드로 검색
        * 검색 조건
        * 종목 코드 : 정확 일치
        * 회사명 : 부분 일치
            * 삼성을 입력하면 -> 삼성SDI, 상성물산 ... 여러개 나올 수 있음
     *
     * 상장회사 필터
     * 검색 결과 우선순위
         * 종목코드 정확 일치
         * 회사명 정확 일치
         * 회사명 부분 일치
         * 같은 우선순위에서는 회사명 가나다순
     */
    @Query(
            value = """
                    SELECT c
                    FROM Company c
                    WHERE
                        (:listedOnly = false OR c.listed = true)
                        AND (
                            c.stockCode = :query
                            OR LOWER(c.corpName)
                                LIKE LOWER(CONCAT('%', :query, '%'))
                        )
                    ORDER BY
                        CASE
                            WHEN c.stockCode = :query THEN 0
                            WHEN LOWER(c.corpName)
                                = LOWER(:query) THEN 1
                            ELSE 2
                        END,
                        c.corpName ASC
                    """,
            countQuery = """
                    SELECT COUNT(c)
                    FROM Company c
                    WHERE
                        (:listedOnly = false OR c.listed = true)
                        AND (
                            c.stockCode = :query
                            OR LOWER(c.corpName)
                                LIKE LOWER(CONCAT('%', :query, '%'))
                        )
                    """
    )
    Page<Company> search(
            @Param("query") String query,
            @Param("listedOnly") boolean listedOnly,
            Pageable pageable
    );
}
