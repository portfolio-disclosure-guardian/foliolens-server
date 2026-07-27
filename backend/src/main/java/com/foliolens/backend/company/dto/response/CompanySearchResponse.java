package com.foliolens.backend.company.dto.response;

import com.foliolens.backend.common.response.PageInfoResponse;
import com.foliolens.backend.company.entity.Company;
import org.springframework.data.domain.Page;

import java.util.List;

public record CompanySearchResponse(
        List<CompanySearchItemResponse> items,
        PageInfoResponse page
) {

    public static CompanySearchResponse from(
            Page<Company> companyPage,
            String query
    ) {
        List<CompanySearchItemResponse> items =
                companyPage.getContent()
                        .stream()
                        .map(company ->
                                CompanySearchItemResponse.from(
                                        company,
                                        query
                                )
                        )
                        .toList();

        return new CompanySearchResponse(
                items,
                PageInfoResponse.from(companyPage)
        );
    }
}
