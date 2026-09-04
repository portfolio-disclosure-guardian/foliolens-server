package com.foliolens.backend.global.health;

import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.foliolens.backend.disclosure.repository.DisclosureEvidenceRepository;
import com.foliolens.backend.disclosure.repository.DisclosureFactRepository;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.FactNecessity;
import com.foliolens.backend.policy.GoldenCase;
import com.foliolens.backend.policy.GoldenCaseApprovalStatus;

// readinessState,db만으로는 DB가 비어 있거나 V12 마이그레이션이 빠져도 UP이 될 수 있다.
// 실제 답변에 쓰이는 승인된 골든 케이스의 fact/evidence까지 확인해 제출 환경 재현 여부를 검증한다.
@Component
public class DisclosureDataHealthIndicator implements HealthIndicator {

    private static final String REQUIRED_FLYWAY_VERSION = "12";

    private final Flyway flyway;
    private final List<AnswerPolicy> answerPolicies;
    private final DisclosureFactRepository factRepository;
    private final DisclosureEvidenceRepository evidenceRepository;

    public DisclosureDataHealthIndicator(
            Flyway flyway,
            List<AnswerPolicy> answerPolicies,
            DisclosureFactRepository factRepository,
            DisclosureEvidenceRepository evidenceRepository) {
        this.flyway = flyway;
        this.answerPolicies = answerPolicies;
        this.factRepository = factRepository;
        this.evidenceRepository = evidenceRepository;
    }

    @Override
    public Health health() {
        if (!flywayVersion12Applied()) {
            return Health.down().withDetail("reason", "flyway migration V12 not applied").build();
        }

        int approvedCount = 0;
        for (AnswerPolicy policy : answerPolicies) {
            for (GoldenCase goldenCase : policy.goldenCases()) {
                if (goldenCase.approvalStatus() != GoldenCaseApprovalStatus.APPROVED) {
                    continue;
                }
                approvedCount++;
                String missing = missingRequirement(policy, goldenCase);
                if (missing != null) {
                    return Health.down()
                            .withDetail("goldenCaseId", goldenCase.goldenCaseId())
                            .withDetail("receiptNo", goldenCase.receiptNo())
                            .withDetail("reason", missing)
                            .build();
                }
            }
        }

        if (approvedCount == 0) {
            return Health.down().withDetail("reason", "no approved golden case configured").build();
        }
        return Health.up().withDetail("approvedGoldenCases", approvedCount).build();
    }

    private boolean flywayVersion12Applied() {
        return Arrays.stream(flyway.info().applied())
                .anyMatch(info -> REQUIRED_FLYWAY_VERSION.equals(versionOf(info))
                        && info.getState() == MigrationState.SUCCESS);
    }

    private static String versionOf(MigrationInfo info) {
        return info.getVersion() == null ? null : info.getVersion().getVersion();
    }

    private String missingRequirement(AnswerPolicy policy, GoldenCase goldenCase) {
        for (var factPolicy : policy.facts()) {
            if (factPolicy.necessity() != FactNecessity.REQUIRED) {
                continue;
            }
            long count = factRepository.countBySourceReceiptNoAndFactKey(goldenCase.receiptNo(), factPolicy.factKey());
            if (count == 0) {
                return "missing required fact " + factPolicy.factKey();
            }
        }
        if (evidenceRepository.countByReceiptNo(goldenCase.receiptNo()) == 0) {
            return "missing evidence";
        }
        return null;
    }
}
