package com.foliolens.backend.calculation;

import com.foliolens.backend.calculation.facility.DeterministicDisclosureCalculator;
import com.foliolens.backend.calculation.fake.FakeDisclosureCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DisclosureCalculator} Bean 선택이 프로필에 따라 정확히 하나로
 * 결정되는지 확인한다. {@link FakeDisclosureCalculator}는
 * fake-calculation 프로필에서만, {@link DeterministicDisclosureCalculator}는
 * 그 외 모든 프로필(기본·evaluation 포함)에서 활성화되어야 하며, 어떤
 * 프로필 조합에서도 두 Bean이 동시에 등록되면 안 된다.
 */
class DisclosureCalculatorWiringTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(
                    FakeDisclosureCalculator.class,
                    DeterministicDisclosureCalculator.class
            );

    @Test
    void 기본_프로필에서는_DeterministicDisclosureCalculator가_선택된다() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DisclosureCalculator.class);
            assertThat(context.getBean(DisclosureCalculator.class))
                    .isInstanceOf(DeterministicDisclosureCalculator.class);
        });
    }

    @Test
    void evaluation_프로필에서도_DeterministicDisclosureCalculator가_선택된다() {
        contextRunner
                .withInitializer(applicationContext ->
                        applicationContext.getEnvironment()
                                .setActiveProfiles("evaluation")
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DisclosureCalculator.class);
                    assertThat(context.getBean(DisclosureCalculator.class))
                            .isInstanceOf(DeterministicDisclosureCalculator.class);
                });
    }

    @Test
    void fake_calculation_프로필에서는_FakeDisclosureCalculator가_선택된다() {
        contextRunner
                .withInitializer(applicationContext ->
                        applicationContext.getEnvironment()
                                .setActiveProfiles("fake-calculation")
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DisclosureCalculator.class);
                    assertThat(context.getBean(DisclosureCalculator.class))
                            .isInstanceOf(FakeDisclosureCalculator.class);
                });
    }

    @Test
    void fake_calculation과_다른_프로필이_함께_있어도_FakeDisclosureCalculator만_선택된다() {
        contextRunner
                .withInitializer(applicationContext ->
                        applicationContext.getEnvironment()
                                .setActiveProfiles("evaluation", "fake-calculation")
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DisclosureCalculator.class);
                    assertThat(context.getBean(DisclosureCalculator.class))
                            .isInstanceOf(FakeDisclosureCalculator.class);
                });
    }
}
