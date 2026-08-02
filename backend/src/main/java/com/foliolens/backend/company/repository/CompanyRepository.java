package com.foliolens.backend.company.repository;

import com.foliolens.backend.company.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    // CSV 적재 시 기존 기업인지 확인
    Optional<Company> findByCorpCode(String corpCode);

    // 종목코드로 기업 조회
    Optional<Company> findByStockCode(String stockCode);

    // 기업코드 중복 검사
    boolean existsByCorpCode(String corpCode);

    // 종목코드 중복 검사
    boolean existsByStockCode(String stockCode);

    // 현재 상장기업을 이름순으로 조회
    List<Company> findAllByListedTrueOrderByCorpNameAsc();
}
