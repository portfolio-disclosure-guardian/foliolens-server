package com.foliolens.backend.company.dto.response;

/**
 * 검색 종류는 2가지
 * 종목코드, 회사명
 */
public enum CompanyMatchType {

    EXACT_STOCK_CODE,  // 종목코드 정확 일치
    EXACT_NAME,        // 회사명 정확 일치
    PARTIAL_NAME       // 회사명 부분 일치
}