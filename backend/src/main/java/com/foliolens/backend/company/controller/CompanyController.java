package com.foliolens.backend.company.controller;

import com.foliolens.backend.common.response.ApiResponse;
import com.foliolens.backend.company.dto.response.CompanySearchResponse;
import com.foliolens.backend.company.service.CompanySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanySearchService companySearchService;


    /**
     * 기업 검색 API
     * 요청 예시 : GET /api/v1/companies?query=삼성&listedOnly=true&page=0&size=20
     * @param query = 검색 기업
     * @param listedOnly = 상장 기업만 검색인지
     * @param page = 검색 페이지 번호
     * @param size = 한 페이지 사이즈
     */
    @GetMapping
    public ApiResponse<CompanySearchResponse> searchCompanies(
            @RequestParam String query,
            @RequestParam(defaultValue = "true")
            boolean listedOnly,
            @RequestParam(defaultValue = "0")
            int page,
            @RequestParam(defaultValue = "20")
            int size
    ) {
        CompanySearchResponse response = companySearchService.search(
                        query,
                        listedOnly,
                        page,
                        size
                );

        return ApiResponse.success("기업 검색에 성공했습니다.", response);
    }
}
