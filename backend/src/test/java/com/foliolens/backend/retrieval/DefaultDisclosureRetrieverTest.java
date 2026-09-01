package com.foliolens.backend.retrieval;

import com.foliolens.backend.company.domain.SourceProvider;
import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;
import com.foliolens.backend.disclosure.domain.fact.EvidenceBlockType;
import com.foliolens.backend.disclosure.domain.fact.EvidenceStatus;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchHit;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSearchResult;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureChunkSourceReference;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchCondition;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchHit;
import com.foliolens.backend.disclosure.infrastructure.search.DisclosureMetadataSearchResult;
import com.foliolens.backend.disclosure.infrastructure.search.SearchScoreBreakdown;
import com.foliolens.backend.disclosure.service.DisclosureChunkSearchService;
import com.foliolens.backend.disclosure.service.DisclosureMetadataSearchService;
import com.foliolens.backend.question.plan.ToolType;
import com.foliolens.backend.question.plan.confirmation.DateRange;
import com.foliolens.backend.question.plan.confirmation.PlanStep;
import com.foliolens.backend.question.plan.confirmation.PlanTime;
import com.foliolens.backend.question.plan.confirmation.QuestionPlan;
import com.foliolens.backend.question.plan.confirmation.ResolvedCompanyRef;
import com.foliolens.backend.question.plan.toolinput.SearchDisclosuresInput;
import com.foliolens.backend.question.plan.toolinput.SearchEvidenceInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultDisclosureRetrieverTest {

    private DisclosureMetadataSearchService metadataSearchService;
    private DisclosureChunkSearchService chunkSearchService;
    private DefaultDisclosureRetriever retriever;

    @BeforeEach
    void setUp() {
        metadataSearchService = mock(DisclosureMetadataSearchService.class);
        chunkSearchService = mock(DisclosureChunkSearchService.class);
        retriever = new DefaultDisclosureRetriever(
                metadataSearchService,
                chunkSearchService
        );
    }

    @Test
    void SEARCH_DISCLOSURES_결과의_공시ID로_SEARCH_EVIDENCE를_실행한다() {
        UUID companyId = UUID.randomUUID();
        UUID disclosureId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        UUID chunkSourceId = UUID.randomUUID();
        UUID contentBlockId = UUID.randomUUID();
        LocalDate receiptDate = LocalDate.of(2024, 4, 24);

        DisclosureMetadataSearchResult metadataResult =
                new DisclosureMetadataSearchResult(
                        List.of(new DisclosureMetadataSearchHit(
                                disclosureId,
                                companyId,
                                "SK하이닉스",
                                "000660",
                                "20240424800596",
                                receiptDate,
                                "신규시설투자등",
                                DisclosureSourceGroup.EXCHANGE,
                                DisclosureCategory.EXCHANGE,
                                "신규시설투자등",
                                false,
                                SourceProvider.CONTEST,
                                1,
                                2.0,
                                List.of("신규시설투자")
                        )),
                        1,
                        false,
                        List.of(),
                        "metadata-search-v1"
                );

        DisclosureChunkSourceReference source =
                new DisclosureChunkSourceReference(
                        chunkSourceId,
                        contentBlockId,
                        1,
                        7,
                        100,
                        120,
                        "table.rows[2]",
                        2,
                        3
                );
        SearchScoreBreakdown breakdown = new SearchScoreBreakdown(
                0.25,
                1.5,
                1.0,
                0.5,
                0.75,
                0.0,
                4.0
        );
        DisclosureChunkSearchResult chunkResult =
                new DisclosureChunkSearchResult(
                        List.of(new DisclosureChunkSearchHit(
                                chunkId,
                                disclosureId,
                                documentId,
                                companyId,
                                "SK하이닉스",
                                "20240424800596",
                                receiptDate,
                                "신규시설투자등",
                                false,
                                "20240424800596.xml",
                                DisclosureDocumentRole.MAIN,
                                null,
                                DisclosureChunkType.TABLE,
                                3,
                                "신규시설투자 > 투자내역",
                                "투자금액 | 5조 2,962억 원",
                                "신규시설투자 투자내역 투자금액 5조 2,962억 원",
                                4.0,
                                breakdown,
                                List.of("투자금액"),
                                List.of(source),
                                "dart-xml-chunk-v3",
                                "chunk-search-v1"
                        )),
                        Set.of(disclosureId),
                        1,
                        2,
                        true,
                        List.of("topK 상한으로 일부 후보가 제외됐습니다."),
                        "chunk-search-v1"
                );

        when(metadataSearchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(metadataResult);
        when(chunkSearchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(chunkResult);

        RetrievalResult result = retriever.retrieve(plan(companyId));

        ArgumentCaptor<DisclosureMetadataSearchCondition> metadataCondition =
                ArgumentCaptor.forClass(DisclosureMetadataSearchCondition.class);
        verify(metadataSearchService).search(metadataCondition.capture());
        assertThat(metadataCondition.getValue().companyIds())
                .containsExactly(companyId);
        assertThat(metadataCondition.getValue().receiptDateFrom())
                .isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(metadataCondition.getValue().categories())
                .containsExactly(DisclosureCategory.EXCHANGE);

        ArgumentCaptor<DisclosureChunkSearchCondition> chunkCondition =
                ArgumentCaptor.forClass(DisclosureChunkSearchCondition.class);
        verify(chunkSearchService).search(chunkCondition.capture());
        assertThat(chunkCondition.getValue().disclosureIds())
                .containsExactly(disclosureId);
        assertThat(chunkCondition.getValue().factKeys())
                .containsExactly("facility.amount");
        assertThat(chunkCondition.getValue().effectiveChunkTypes())
                .containsExactlyInAnyOrder(
                        DisclosureChunkType.TEXT,
                        DisclosureChunkType.TABLE
                );
        assertThat(chunkCondition.getValue().neighborRadius()).isZero();

        assertThat(result.executedSteps())
                .extracting(PlanStep::toolType)
                .containsExactly(
                        ToolType.SEARCH_DISCLOSURES,
                        ToolType.SEARCH_EVIDENCE
                );
        assertThat(result.coverage())
                .isEqualTo(new RetrievalCoverage(2, true));
        assertThat(result.documents()).singleElement().satisfies(document -> {
            assertThat(document.documentId()).isEqualTo("20240424800596");
            assertThat(document.content()).contains("5조 2,962억 원");
        });
        assertThat(result.evidences()).singleElement().satisfies(evidence -> {
            assertThat(evidence.evidenceId()).isEqualTo(chunkId.toString());
            assertThat(evidence.documentId()).isEqualTo(documentId.toString());
            assertThat(evidence.sectionPath())
                    .isEqualTo("신규시설투자 > 투자내역");
            assertThat(evidence.blockType()).isEqualTo(EvidenceBlockType.TABLE);
            assertThat(evidence.status()).isEqualTo(EvidenceStatus.CANDIDATE);
            assertThat(evidence.sources()).singleElement().satisfies(
                    evidenceSource -> {
                        assertThat(evidenceSource.contentBlockId())
                                .isEqualTo(contentBlockId.toString());
                        assertThat(evidenceSource.sourceLineStart())
                                .isEqualTo(100);
                        assertThat(evidenceSource.tableRowIndexStart())
                                .isEqualTo(2);
                    }
            );
        });
        assertThat(result.retrievalVersion())
                .isEqualTo(DefaultDisclosureRetriever.RETRIEVAL_VERSION);
    }

    private QuestionPlan plan(UUID companyId) {
        PlanStep searchDisclosures = new PlanStep(
                "s1",
                ToolType.SEARCH_DISCLOSURES,
                new SearchDisclosuresInput(
                        List.of(DisclosureCategory.EXCHANGE),
                        List.of("신규시설투자등"),
                        List.of(),
                        10
                ),
                List.of()
        );
        PlanStep searchEvidence = new PlanStep(
                "s2",
                ToolType.SEARCH_EVIDENCE,
                new SearchEvidenceInput(
                        "s1",
                        List.of("FACILITY_INVESTMENT"),
                        List.of("facility.amount"),
                        List.of("투자내역"),
                        List.of("투자금액"),
                        List.of("PARAGRAPH", "TABLE_ROW"),
                        5
                ),
                List.of("s1")
        );

        return new QuestionPlan(
                1L,
                List.of(new ResolvedCompanyRef(companyId, "SK하이닉스")),
                new PlanTime(
                        new DateRange(
                                LocalDate.of(2024, 4, 1),
                                LocalDate.of(2024, 4, 30)
                        ),
                        new DateRange(
                                LocalDate.of(2024, 1, 1),
                                LocalDate.of(2024, 3, 31)
                        ),
                        LocalDate.of(2024, 4, 24)
                ),
                List.of(searchDisclosures, searchEvidence),
                List.of()
        );
    }
}
