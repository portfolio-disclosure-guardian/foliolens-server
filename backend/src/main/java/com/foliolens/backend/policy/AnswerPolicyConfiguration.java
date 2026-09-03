package com.foliolens.backend.policy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnswerPolicyConfiguration {

    @Bean
    AnswerPolicy goldFacility001Policy() {
        return GoldFacility001Fixture.policy();
    }
}
