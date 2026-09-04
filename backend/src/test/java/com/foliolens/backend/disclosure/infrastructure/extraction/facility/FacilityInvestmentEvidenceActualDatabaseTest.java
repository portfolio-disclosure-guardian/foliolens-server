package com.foliolens.backend.disclosure.infrastructure.extraction.facility;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.DisclosureEvidence;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import com.foliolens.backend.disclosure.domain.fact.facility.FacilityInvestmentFactDefinition;
import com.foliolens.backend.disclosure.infrastructure.chunking.DisclosureTablePayloadReader;
import com.foliolens.backend.disclosure.infrastructure.chunking.TableLogicalGridBuilder;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 PostgreSQL에 적재된 골든 공시를 대상으로 하는 선택 실행형 감사 테스트.
 * 일반 test 실행에서는 건너뛰고 FOLIOLENS_ACTUAL_DB_AUDIT=true일 때만 실행한다.
 */
@EnabledIfEnvironmentVariable(
        named = "FOLIOLENS_ACTUAL_DB_AUDIT",
        matches = "true"
)
class FacilityInvestmentEvidenceActualDatabaseTest {

    private static final String RECEIPT_NO = "20240424800596";

    private static final String SELECT_TABLE_BLOCKS = """
            SELECT
                d.id AS disclosure_id,
                dd.id AS document_id,
                dd.file_name,
                dd.document_role,
                d.correction,
                ds.id AS section_id,
                COALESCE(ds.title, '') AS section_path,
                dcb.id AS content_block_id,
                dcb.structured_content::text AS structured_content
            FROM disclosures d
            JOIN disclosure_documents dd
              ON dd.disclosure_id = d.id
            JOIN disclosure_content_blocks dcb
              ON dcb.disclosure_document_id = dd.id
             AND dcb.block_type = 'TABLE'
            LEFT JOIN disclosure_sections ds
              ON ds.id = dcb.section_id
            WHERE d.receipt_no = ?
              AND d.raw_subtype = '신규시설투자등'
              AND dd.content_format = 'HTML'
              AND dd.parse_status = 'COMPLETED'
            ORDER BY dcb.sequence_no
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DisclosureTablePayloadReader payloadReader =
            new DisclosureTablePayloadReader(objectMapper);
    private final FacilityInvestmentEvidenceExtractor extractor =
            new FacilityInvestmentEvidenceExtractor(
                    new TableLogicalGridBuilder()
            );

    @Test
    void 실제_DB의_골든_공시에서_핵심_8개와_보조_투자유형의_근거를_유일하게_추출한다()
            throws Exception {
        List<FacilityInvestmentEvidenceExtractionResult> results =
                new ArrayList<>();

        try (Connection connection = openReadOnlyConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT_TABLE_BLOCKS
             )) {
            statement.setString(1, RECEIPT_NO);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ParsedDisclosureTable table = payloadReader.read(
                            objectMapper.readTree(
                                    rows.getString("structured_content")
                            )
                    );
                    FacilityInvestmentExtractionContext context =
                            new FacilityInvestmentExtractionContext(
                                    rows.getObject(
                                            "disclosure_id",
                                            UUID.class
                                    ),
                                    rows.getObject("document_id", UUID.class),
                                    RECEIPT_NO,
                                    rows.getString("file_name"),
                                    DisclosureDocumentRole.valueOf(
                                            rows.getString("document_role")
                                    ),
                                    rows.getBoolean("correction")
                                            ? EventDocumentRole.CORRECTION
                                            : EventDocumentRole.ORIGINAL,
                                    rows.getObject("section_id", UUID.class),
                                    rows.getString("section_path"),
                                    rows.getObject(
                                            "content_block_id",
                                            UUID.class
                                    )
                            );
                    results.add(extractor.extract(context, table));
                }
            }
            connection.rollback();
        }

        assertThat(results).as("기준 공시의 TABLE 블록").hasSize(1);

        FacilityInvestmentEvidenceExtractionResult result =
                FacilityInvestmentEvidenceExtractionResult.combine(results);

        assertThat(result.candidateCount()).isEqualTo(9);
        assertThat(result.hasAllCoreCandidates()).isTrue();
        assertThat(result.missingCoreDefinitions()).isEmpty();
        assertThat(result.ambiguousDefinitions()).isEmpty();
        assertThat(result.warnings()).isEmpty();

        Map<FacilityInvestmentFactDefinition, String> expectedValues =
                new EnumMap<>(FacilityInvestmentFactDefinition.class);
        expectedValues.put(FacilityInvestmentFactDefinition.TYPE, "시설증설");
        expectedValues.put(
                FacilityInvestmentFactDefinition.TARGET,
                "청주 M15X 건설"
        );
        expectedValues.put(
                FacilityInvestmentFactDefinition.AMOUNT,
                "5,296,200,000,000"
        );
        expectedValues.put(
                FacilityInvestmentFactDefinition.EQUITY_AMOUNT,
                "53,503,752,397,611"
        );
        expectedValues.put(
                FacilityInvestmentFactDefinition.EQUITY_RATIO,
                "9.90"
        );
        expectedValues.put(
                FacilityInvestmentFactDefinition.PURPOSE,
                "선제적인 반도체 수요 대응을 위한 차세대 DRAM 생산능력 확장"
        );
        expectedValues.put(
                FacilityInvestmentFactDefinition.START_DATE,
                "2024-04-24"
        );
        expectedValues.put(
                FacilityInvestmentFactDefinition.END_DATE,
                "2026-10-30"
        );
        expectedValues.put(
                FacilityInvestmentFactDefinition.DECISION_DATE,
                "2024-04-24"
        );

        expectedValues.forEach((definition, expectedValue) -> {
            DisclosureEvidence evidence = result.uniqueCandidate(definition)
                    .orElseThrow();
            assertThat(evidence.status()).isEqualTo(EvidenceStatus.CANDIDATE);
            assertThat(evidence.value().rawValue()).isEqualTo(expectedValue);
            assertThat(evidence.contentBlockId()).isNotNull();
            assertThat(evidence.location().hasSourceLines()).isTrue();
            assertThat(evidence.location().hasTableLocation()).isTrue();
            assertThat(evidence.location().tableCellIndex()).isNotNull();
        });

        DisclosureEvidence amount = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.AMOUNT
        ).orElseThrow();
        assertThat(amount.value().rawUnit()).isEqualTo("원");
        assertThat(amount.value().rowLabel())
                .isEqualTo("2. 투자내역 > 투자금액(원)");
        assertThat(amount.location().tableRowIndex()).isEqualTo(2);
        assertThat(amount.location().tableCellIndex()).isEqualTo(2);
        assertThat(amount.location().sourceLineStart()).isEqualTo(97);
        assertThat(amount.location().sourceLineEnd()).isEqualTo(97);

        DisclosureEvidence ratio = result.uniqueCandidate(
                FacilityInvestmentFactDefinition.EQUITY_RATIO
        ).orElseThrow();
        assertThat(ratio.value().rawUnit()).isEqualTo("%");
    }

    private Connection openReadOnlyConnection() throws Exception {
        String port = environmentOrDefault("POSTGRES_PORT", "5432");
        String database = environmentOrDefault("POSTGRES_DB", "foliolens");
        String username = environmentOrDefault(
                "POSTGRES_USER",
                "foliolens"
        );
        String password = requireEnvironment("POSTGRES_PASSWORD");
        Connection connection = DriverManager.getConnection(
                "jdbc:postgresql://localhost:" + port + "/" + database,
                username,
                password
        );
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
        return connection;
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }

    private String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " 환경변수가 필요합니다."
            );
        }
        return value;
    }
}
