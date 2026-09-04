package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.fact.DecimalFactValue;
import com.foliolens.backend.disclosure.domain.fact.DisclosureFact;
import com.foliolens.backend.retrieval.DisclosureFactRetrievalMapper;
import com.foliolens.backend.retrieval.RetrievedFact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 최종 스냅샷의 접수번호 20240424800596을 실제 조회하는 선택형 감사 테스트.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
        named = "FOLIOLENS_ACTUAL_DB_AUDIT",
        matches = "true"
)
class DisclosureFactLookupActualDatabaseTest {

    private static final String RECEIPT_NO = "20240424800596";
    private static final List<String> FACT_KEYS = List.of(
            "facility.type",
            "facility.target",
            "facility.amount",
            "facility.equity_amount",
            "facility.equity_ratio",
            "facility.purpose",
            "facility.start_date",
            "facility.end_date",
            "facility.decision_date"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DisclosureFactLookupService lookupService;

    @Autowired
    private DisclosureFactRetrievalMapper retrievalMapper;

    @Test
    void 실제_골든_공시에서_VERIFIED_Fact와_Evidence를_함께_조회한다() {
        UUID disclosureId = jdbcTemplate.queryForObject(
                "SELECT id FROM disclosures WHERE receipt_no = ?",
                UUID.class,
                RECEIPT_NO
        );

        DisclosureFactLookupResult result = lookupService.lookup(
                Set.of(disclosureId),
                FACT_KEYS
        );

        assertThat(result.missingFactKeys()).isEmpty();
        assertThat(result.facts()).hasSize(9)
                .allMatch(DisclosureFact::verified);
        assertThat(result.evidences()).hasSize(9)
                .allMatch(evidence -> evidence.verified()
                        && RECEIPT_NO.equals(evidence.receiptNo()));

        DisclosureFact amount = result.facts().stream()
                .filter(fact -> "facility.amount".equals(fact.factKey()))
                .findFirst()
                .orElseThrow();
        assertThat(amount.normalizedValue()).isEqualTo(
                new DecimalFactValue(new BigDecimal("5296200000000"))
        );

        RetrievedFact retrievedAmount = retrievalMapper.toRetrievedFact(
                amount
        );
        assertThat(retrievedAmount.normalizedValue())
                .isEqualTo("5296200000000");
        assertThat(retrievedAmount.unit()).isEqualTo("KRW");
        assertThat(retrievedAmount.evidenceIds()).hasSize(1);
    }
}
