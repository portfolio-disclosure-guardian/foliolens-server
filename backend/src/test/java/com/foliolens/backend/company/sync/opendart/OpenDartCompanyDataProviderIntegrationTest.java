package com.foliolens.backend.company.sync.opendart;

import com.foliolens.backend.company.sync.CompanySyncItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenDartCompanyDataProviderIntegrationTest {


    @Test
    @EnabledIfEnvironmentVariable(
            named = "OPENDART_API_KEY",
            matches = ".+"
    )
    void 실제_OpenDART_ZIP을_다운로드하고_파싱한다() {
        OpenDartProperties properties =
                new OpenDartProperties(
                        true,
                        URI.create(
                                "https://opendart.fss.or.kr/api/corpCode.xml"
                        ),
                        System.getenv("OPENDART_API_KEY"),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60),
                        50 * 1024 * 1024
                );

        OpenDartCompanyDataProvider provider =
                new OpenDartCompanyDataProvider(
                        properties,
                        new OpenDartCompanyXmlParser()
                );

        List<CompanySyncItem> companies =
                provider.fetchCompanies();

        assertFalse(companies.isEmpty());

        assertTrue(
                companies.stream()
                        .allMatch(company ->
                                company.corpCode()
                                        .matches("^[0-9]{8}$")
                        )
        );

        assertTrue(
                companies.stream()
                        .filter(company ->
                                company.stockCode() != null
                        )
                        .allMatch(company ->
                                company.stockCode()
                                        .matches("^[0-9A-Z]{6}$")
                        )
        );
    }
}