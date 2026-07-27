package com.foliolens.backend.company.sync;

import java.util.List;

public interface CompanyDataProvider {

    // 구현체가 어떤 데이터를 사용하는지 반환
    CompanyDataSource source();

    // 외부 API, XML, CSV 등에서 기업 데이터를 읽어 CompanySyncItem 목록으로 반환
    List<CompanySyncItem> fetchCompanies();
}
