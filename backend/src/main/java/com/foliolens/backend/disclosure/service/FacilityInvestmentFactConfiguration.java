package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.fact.facility.generation.FacilityInvestmentFactGenerator;
import com.foliolens.backend.disclosure.domain.fact.facility.normalization.FacilityInvestmentValueNormalizer;
import com.foliolens.backend.disclosure.domain.fact.facility.verification.FacilityInvestmentEvidenceVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring에 의존하지 않는 시설투자 Fact 도메인 서비스를 애플리케이션에서
 * 조합해 사용할 수 있도록 등록한다.
 */
@Configuration(proxyBeanMethods = false)
public class FacilityInvestmentFactConfiguration {

    @Bean
    FacilityInvestmentValueNormalizer facilityInvestmentValueNormalizer() {
        return new FacilityInvestmentValueNormalizer();
    }

    @Bean
    FacilityInvestmentEvidenceVerifier facilityInvestmentEvidenceVerifier(
            FacilityInvestmentValueNormalizer normalizer
    ) {
        return new FacilityInvestmentEvidenceVerifier(normalizer);
    }

    @Bean
    FacilityInvestmentFactGenerator facilityInvestmentFactGenerator() {
        return new FacilityInvestmentFactGenerator();
    }
}
