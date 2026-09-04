package com.foliolens.backend.global.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.FactValidationStatus;
import com.foliolens.backend.disclosure.repository.DisclosureEvidenceRepository;
import com.foliolens.backend.disclosure.repository.DisclosureFactRepository;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.FactNecessity;
import com.foliolens.backend.policy.FactPolicy;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.policy.GoldenCaseApprovalStatus;

class DisclosureDataHealthIndicatorTest {

    private static final String RECEIPT_NO = "20240424800596";

    private Flyway flyway;
    private DisclosureFactRepository factRepository;
    private DisclosureEvidenceRepository evidenceRepository;
    private DisclosureDataHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        flyway = mock(Flyway.class);
        factRepository = mock(DisclosureFactRepository.class);
        evidenceRepository = mock(DisclosureEvidenceRepository.class);
        indicator = new DisclosureDataHealthIndicator(
                flyway,
                List.of(policy()),
                factRepository,
                evidenceRepository);
    }

    @Test
    void Flyway_V12가_적용되지_않으면_DOWN이다() {
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(info);
        when(info.applied()).thenReturn(new MigrationInfo[0]);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("reason"))
                .isEqualTo("flyway migration V12 not applied");
        verifyNoInteractions(factRepository, evidenceRepository);
    }

    @Test
    void 필수_VERIFIED_Fact가_하나라도_없으면_DOWN이다() {
        v12Applied();
        when(factRepository.countBySourceReceiptNoAndFactKeyAndValidationStatus(
                RECEIPT_NO, "facility.amount", FactValidationStatus.VERIFIED))
                .thenReturn(1L);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("reason"))
                .isEqualTo("missing required fact facility.purpose");
    }

    @Test
    void VERIFIED_Evidence가_없으면_DOWN이다() {
        v12Applied();
        when(factRepository.countBySourceReceiptNoAndFactKeyAndValidationStatus(
                anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(FactValidationStatus.VERIFIED)))
                .thenReturn(1L);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("reason")).isEqualTo("missing evidence");
    }

    @Test
    void V12와_필수_VERIFIED_Fact_Evidence가_모두_있으면_UP이다() {
        v12Applied();
        when(factRepository.countBySourceReceiptNoAndFactKeyAndValidationStatus(
                anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(FactValidationStatus.VERIFIED)))
                .thenReturn(1L);
        when(evidenceRepository.countByReceiptNoAndStatus(
                RECEIPT_NO, EvidenceStatus.VERIFIED)).thenReturn(1L);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("approvedGoldenCases")).isEqualTo(1);
    }

    private void v12Applied() {
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo migration = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.applied()).thenReturn(new MigrationInfo[]{migration});
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion("12"));
        when(migration.getState()).thenReturn(MigrationState.SUCCESS);
    }

    private static AnswerPolicy policy() {
        return new AnswerPolicy(
                "test",
                "신규시설투자등",
                List.of(
                        new FactPolicy("facility.amount", FactNecessity.REQUIRED),
                        new FactPolicy("facility.purpose", FactNecessity.REQUIRED)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new GoldenCase(
                        "GOLD-FACILITY-001",
                        "질문",
                        "SK하이닉스",
                        RECEIPT_NO,
                        Map.of(),
                        "",
                        "",
                        null,
                        AnswerOutcome.COMPLETED,
                        "답변",
                        List.of(),
                        GoldenCaseApprovalStatus.APPROVED)));
    }
}
