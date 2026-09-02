package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlockType;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.infrastructure.chunking.DisclosureTablePayloadReader;
import com.foliolens.backend.disclosure.infrastructure.chunking.SectionPathResolver;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentEvidenceExtractionResult;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentEvidenceExtractor;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentExtractionContext;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.repository.DisclosureContentBlockRepository;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.repository.DisclosureSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FacilityInvestmentEvidenceExtractionServiceTest {

    private static final UUID DISCLOSURE_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID BLOCK_ID = UUID.randomUUID();

    private DisclosureDocumentRepository documentRepository;
    private DisclosureSectionRepository sectionRepository;
    private DisclosureContentBlockRepository blockRepository;
    private SectionPathResolver sectionPathResolver;
    private DisclosureTablePayloadReader tablePayloadReader;
    private FacilityInvestmentEvidenceExtractor extractor;
    private FacilityInvestmentEvidenceExtractionService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DisclosureDocumentRepository.class);
        sectionRepository = mock(DisclosureSectionRepository.class);
        blockRepository = mock(DisclosureContentBlockRepository.class);
        sectionPathResolver = mock(SectionPathResolver.class);
        tablePayloadReader = mock(DisclosureTablePayloadReader.class);
        extractor = mock(FacilityInvestmentEvidenceExtractor.class);
        service = new FacilityInvestmentEvidenceExtractionService(
                documentRepository,
                sectionRepository,
                blockRepository,
                sectionPathResolver,
                tablePayloadReader,
                extractor
        );
    }

    @Test
    void 파싱된_시설투자_문서의_TABLE을_추출기에_전달한다() {
        Disclosure disclosure = facilityDisclosure();
        DisclosureDocument document = completedDocument(disclosure);
        DisclosureContentBlock block = mock(DisclosureContentBlock.class);
        ParsedDisclosureTable table = mock(ParsedDisclosureTable.class);
        FacilityInvestmentEvidenceExtractionResult extracted =
                new FacilityInvestmentEvidenceExtractionResult(
                        Map.of(),
                        List.of()
                );

        when(documentRepository.findWithDisclosureById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));
        when(sectionRepository
                .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(DOCUMENT_ID))
                .thenReturn(List.of());
        when(sectionPathResolver.resolveAll(List.of()))
                .thenReturn(Map.of());
        when(sectionPathResolver.preamblePath()).thenReturn("문서 서두");
        when(blockRepository
                .findAllByDisclosureDocumentIdAndBlockTypeOrderBySequenceNoAsc(
                        DOCUMENT_ID,
                        DisclosureContentBlockType.TABLE
                )).thenReturn(List.of(block));
        when(block.getId()).thenReturn(BLOCK_ID);
        when(block.getSection()).thenReturn(null);
        when(tablePayloadReader.read(block)).thenReturn(table);
        when(extractor.extract(any(), any())).thenReturn(extracted);

        FacilityInvestmentEvidenceExtractionResult result =
                service.extract(DOCUMENT_ID);

        ArgumentCaptor<FacilityInvestmentExtractionContext> captor =
                ArgumentCaptor.forClass(
                        FacilityInvestmentExtractionContext.class
                );
        verify(extractor).extract(captor.capture(), any());
        FacilityInvestmentExtractionContext context = captor.getValue();
        assertThat(context.disclosureId()).isEqualTo(DISCLOSURE_ID);
        assertThat(context.disclosureDocumentId()).isEqualTo(DOCUMENT_ID);
        assertThat(context.receiptNo()).isEqualTo("20240424800596");
        assertThat(context.documentName()).isEqualTo("20240424800596.xml");
        assertThat(context.sectionPath()).isEqualTo("문서 서두");
        assertThat(context.contentBlockId()).isEqualTo(BLOCK_ID);
        assertThat(result.warnings()).hasSize(1)
                .first().asString().contains("누락된 핵심");
    }

    @Test
    void 파싱되지_않은_문서는_추출하지_않는다() {
        DisclosureDocument document = completedDocument(
                facilityDisclosure()
        );
        when(document.getParseStatus())
                .thenReturn(DisclosureDocumentParseStatus.PENDING);
        when(documentRepository.findWithDisclosureById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.extract(DOCUMENT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("파싱 완료 문서만");

        verify(blockRepository, never())
                .findAllByDisclosureDocumentIdAndBlockTypeOrderBySequenceNoAsc(
                        any(),
                        any()
                );
    }

    @Test
    void 시설투자가_아닌_문서는_추출하지_않는다() {
        Disclosure disclosure = facilityDisclosure();
        DisclosureDocument document = completedDocument(disclosure);
        when(disclosure.getRawSubtype()).thenReturn("단일판매공급계약");
        when(documentRepository.findWithDisclosureById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.extract(DOCUMENT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("신규시설투자등");
    }

    private Disclosure facilityDisclosure() {
        Disclosure disclosure = mock(Disclosure.class);
        when(disclosure.getId()).thenReturn(DISCLOSURE_ID);
        when(disclosure.getReceiptNo()).thenReturn("20240424800596");
        when(disclosure.getRawSubtype()).thenReturn("신규시설투자등");
        when(disclosure.isCorrection()).thenReturn(false);
        return disclosure;
    }

    private DisclosureDocument completedDocument(Disclosure disclosure) {
        DisclosureDocument document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(DOCUMENT_ID);
        when(document.getDisclosure()).thenReturn(disclosure);
        when(document.getParseStatus())
                .thenReturn(DisclosureDocumentParseStatus.COMPLETED);
        when(document.getDocumentName()).thenReturn(null);
        when(document.getFileName()).thenReturn("20240424800596.xml");
        when(document.getDocumentRole()).thenReturn(DisclosureDocumentRole.MAIN);
        return document;
    }
}
