package com.foliolens.backend.company.sync.opendart;


import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenDartProperties.class)
public class OpenDartConfiguration {
}
