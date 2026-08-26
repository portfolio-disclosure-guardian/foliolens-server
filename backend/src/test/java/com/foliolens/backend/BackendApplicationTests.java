package com.foliolens.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BackendApplicationTests {

	@Container
	static final PostgreSQLContainer POSTGRES =
			new PostgreSQLContainer(
					DockerImageName.parse("postgres:16-alpine")
			)
					.withDatabaseName("foliolens_context_test")
					.withUsername("foliolens")
					.withPassword("foliolens");

	@DynamicPropertySource
	static void configurePostgreSql(
			DynamicPropertyRegistry registry
	) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Test
	void contextLoads() {
	}

}
