package com.foliolens.backend.disclosure.infrastructure.chunking;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DisclosureChunkingConfiguration {

    @Bean
    public DisclosureChunkingPolicy disclosureChunkingPolicy() {
        return DisclosureChunkingPolicy.dartXmlV3();
    }
}
