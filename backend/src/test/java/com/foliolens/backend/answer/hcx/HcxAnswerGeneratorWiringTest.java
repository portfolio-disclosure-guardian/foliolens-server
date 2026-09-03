package com.foliolens.backend.answer.hcx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.foliolens.backend.answer.FakeHcxAnswerGenerator;
import com.foliolens.backend.answer.HcxAnswerGenerator;
import com.foliolens.backend.question.plan.FakeHcxPlanGenerator;
import com.foliolens.backend.question.plan.HcxPlanGenerator;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// HCX_INTEGRATION_PROMPT.md 완료 기준 2번: enabled 플래그로 Fake ↔ 실 client가 정확히 하나씩만 활성화되는지 확인한다.
class HcxAnswerGeneratorWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    TestConfig.class, HcxRestClientConfig.class, ClovaChatClient.class,
                    ClovaStudioHcxAnswerGenerator.class, ClovaStudioHcxPlanGenerator.class,
                    FakeHcxAnswerGenerator.class, FakeHcxPlanGenerator.class);

    @Test
    void enabled_true면_실제_client_빈만_활성화된다() {
        runner.withPropertyValues(
                        "hcx.api.enabled=true",
                        "hcx.api.base-url=https://clovastudio.stream.ntruss.com",
                        "hcx.api.api-key=test-key",
                        "hcx.api.model=HCX-005",
                        "hcx.api.app-type=testapp",
                        "hcx.api.connect-timeout-ms=3000",
                        "hcx.api.read-timeout-ms=30000",
                        "hcx.api.max-tokens=1024",
                        "hcx.api.temperature=0.5",
                        "hcx.api.top-p=0.8")
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(HcxAnswerGenerator.class).size());
                    assertEquals(1, context.getBeansOfType(ClovaStudioHcxAnswerGenerator.class).size());
                    assertEquals(0, context.getBeansOfType(FakeHcxAnswerGenerator.class).size());
                    assertEquals(1, context.getBeansOfType(HcxPlanGenerator.class).size());
                    assertEquals(1, context.getBeansOfType(ClovaStudioHcxPlanGenerator.class).size());
                    assertEquals(0, context.getBeansOfType(FakeHcxPlanGenerator.class).size());
                });
    }

    @Test
    void enabled_false면_Fake_빈만_활성화된다() {
        runner.withPropertyValues("hcx.api.enabled=false")
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(HcxAnswerGenerator.class).size());
                    assertEquals(1, context.getBeansOfType(FakeHcxAnswerGenerator.class).size());
                    assertEquals(0, context.getBeansOfType(ClovaStudioHcxAnswerGenerator.class).size());
                    assertEquals(1, context.getBeansOfType(HcxPlanGenerator.class).size());
                    assertEquals(1, context.getBeansOfType(FakeHcxPlanGenerator.class).size());
                    assertEquals(0, context.getBeansOfType(ClovaStudioHcxPlanGenerator.class).size());
                });
    }

    @Test
    void enabled_미설정이면_Fake_빈만_활성화된다() {
        runner.run(context -> {
            assertEquals(1, context.getBeansOfType(HcxAnswerGenerator.class).size());
            assertEquals(1, context.getBeansOfType(FakeHcxAnswerGenerator.class).size());
            assertEquals(0, context.getBeansOfType(ClovaStudioHcxAnswerGenerator.class).size());
            assertEquals(1, context.getBeansOfType(HcxPlanGenerator.class).size());
            assertEquals(1, context.getBeansOfType(FakeHcxPlanGenerator.class).size());
            assertEquals(0, context.getBeansOfType(ClovaStudioHcxPlanGenerator.class).size());
        });
    }

    @Test
    void evaluation_profile은_HCX를_명시적으로_켜기_전_외부_HTTP_client가_0개다() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(
                        TestConfig.class, HcxRestClientConfig.class, ClovaChatClient.class,
                        ClovaStudioHcxAnswerGenerator.class, ClovaStudioHcxPlanGenerator.class,
                        FakeHcxAnswerGenerator.class, FakeHcxPlanGenerator.class)
                .withPropertyValues("spring.profiles.active=evaluation")
                .run(context -> {
                    assertEquals(0, context.getBeansOfType(RestClient.class).size());
                    assertEquals(0, context.getBeansOfType(ClovaChatClient.class).size());
                    assertEquals(0, context.getBeansOfType(ClovaStudioHcxAnswerGenerator.class).size());
                    assertEquals(0, context.getBeansOfType(ClovaStudioHcxPlanGenerator.class).size());
                    assertEquals(1, context.getBeansOfType(FakeHcxAnswerGenerator.class).size());
                    assertEquals(1, context.getBeansOfType(FakeHcxPlanGenerator.class).size());
                });
    }

    @Configuration
    @EnableConfigurationProperties(HcxApiProperties.class)
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }
}
