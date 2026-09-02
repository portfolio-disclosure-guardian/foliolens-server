package com.foliolens.backend.disclosure.service;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlockType;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import com.foliolens.backend.disclosure.domain.fact.EventDocumentRole;
import com.foliolens.backend.disclosure.infrastructure.chunking.DisclosureTablePayloadReader;
import com.foliolens.backend.disclosure.infrastructure.chunking.SectionPathResolver;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentEvidenceExtractionResult;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentEvidenceExtractor;
import com.foliolens.backend.disclosure.infrastructure.extraction.facility.FacilityInvestmentExtractionContext;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import com.foliolens.backend.disclosure.repository.DisclosureContentBlockRepository;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.repository.DisclosureSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 파싱·저장된 신규시설투자 문서의 TABLE 블록을 읽어
 * 시설투자 Evidence 후보를 생성한다.
 */
@Service
public class FacilityInvestmentEvidenceExtractionService {

    private static final String FACILITY_SUBTYPE = "신규시설투자등";

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosureSectionRepository sectionRepository;
    private final DisclosureContentBlockRepository blockRepository;
    private final SectionPathResolver sectionPathResolver;
    private final DisclosureTablePayloadReader tablePayloadReader;
    private final FacilityInvestmentEvidenceExtractor extractor;

    public FacilityInvestmentEvidenceExtractionService(
            DisclosureDocumentRepository documentRepository,
            DisclosureSectionRepository sectionRepository,
            DisclosureContentBlockRepository blockRepository,
            SectionPathResolver sectionPathResolver,
            DisclosureTablePayloadReader tablePayloadReader,
            FacilityInvestmentEvidenceExtractor extractor
    ) {
        this.documentRepository = Objects.requireNonNull(
                documentRepository,
                "documentRepository는 필수입니다."
        );
        this.sectionRepository = Objects.requireNonNull(
                sectionRepository,
                "sectionRepository는 필수입니다."
        );
        this.blockRepository = Objects.requireNonNull(
                blockRepository,
                "blockRepository는 필수입니다."
        );
        this.sectionPathResolver = Objects.requireNonNull(
                sectionPathResolver,
                "sectionPathResolver는 필수입니다."
        );
        this.tablePayloadReader = Objects.requireNonNull(
                tablePayloadReader,
                "tablePayloadReader는 필수입니다."
        );
        this.extractor = Objects.requireNonNull(
                extractor,
                "extractor는 필수입니다."
        );
    }

    @Transactional(readOnly = true)
    public FacilityInvestmentEvidenceExtractionResult extract(
            UUID disclosureDocumentId
    ) {
        Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );

        DisclosureDocument document = documentRepository
                .findWithDisclosureById(disclosureDocumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 공시 문서입니다. documentId="
                                + disclosureDocumentId
                ));

        validateExtractionTarget(document);

        List<DisclosureSection> sections = sectionRepository
                .findAllByDisclosureDocumentIdOrderBySequenceNoAsc(
                        disclosureDocumentId
                );
        Map<UUID, String> sectionPaths =
                sectionPathResolver.resolveAll(sections);

        List<DisclosureContentBlock> tableBlocks = blockRepository
                .findAllByDisclosureDocumentIdAndBlockTypeOrderBySequenceNoAsc(
                        disclosureDocumentId,
                        DisclosureContentBlockType.TABLE
                );

        if (tableBlocks.isEmpty()) {
            return FacilityInvestmentEvidenceExtractionResult.empty(
                    "파싱된 TABLE ContentBlock이 없습니다."
            );
        }

        Disclosure disclosure = document.getDisclosure();
        List<FacilityInvestmentEvidenceExtractionResult> results =
                new ArrayList<>(tableBlocks.size());

        for (DisclosureContentBlock block : tableBlocks) {
            ParsedDisclosureTable table = tablePayloadReader.read(block);
            DisclosureSection section = block.getSection();
            UUID sectionId = section == null ? null : section.getId();
            String sectionPath = sectionId == null
                    ? sectionPathResolver.preamblePath()
                    : sectionPaths.getOrDefault(sectionId, "");

            FacilityInvestmentExtractionContext context =
                    new FacilityInvestmentExtractionContext(
                            disclosure.getId(),
                            document.getId(),
                            disclosure.getReceiptNo(),
                            resolveDocumentName(document),
                            document.getDocumentRole(),
                            disclosure.isCorrection()
                                    ? EventDocumentRole.CORRECTION
                                    : EventDocumentRole.ORIGINAL,
                            sectionId,
                            sectionPath,
                            Objects.requireNonNull(
                                    block.getId(),
                                    "저장되지 않은 ContentBlock입니다."
                            )
                    );

            results.add(extractor.extract(context, table));
        }

        FacilityInvestmentEvidenceExtractionResult merged =
                FacilityInvestmentEvidenceExtractionResult.combine(results);
        List<String> warnings = new ArrayList<>(merged.warnings());

        if (!merged.missingCoreDefinitions().isEmpty()) {
            warnings.add(
                    "누락된 핵심 시설투자 Fact 후보가 있습니다: "
                            + merged.missingCoreDefinitions()
            );
        }
        if (!merged.ambiguousDefinitions().isEmpty()) {
            warnings.add(
                    "후보가 여러 개인 시설투자 Fact가 있습니다: "
                            + merged.ambiguousDefinitions()
            );
        }

        return new FacilityInvestmentEvidenceExtractionResult(
                merged.candidates(),
                warnings
        );
    }

    private void validateExtractionTarget(DisclosureDocument document) {
        Disclosure disclosure = document.getDisclosure();
        if (!FACILITY_SUBTYPE.equals(disclosure.getRawSubtype())) {
            throw new IllegalArgumentException(
                    "신규시설투자등 공시만 추출할 수 있습니다. rawSubtype="
                            + disclosure.getRawSubtype()
            );
        }

        DisclosureDocumentParseStatus status = document.getParseStatus();
        if (status != DisclosureDocumentParseStatus.COMPLETED
                && status != DisclosureDocumentParseStatus.PARTIAL) {
            throw new IllegalStateException(
                    "파싱 완료 문서만 시설투자 Evidence를 추출할 수 있습니다. "
                            + "parseStatus=" + status
            );
        }
    }

    private String resolveDocumentName(DisclosureDocument document) {
        String documentName = document.getDocumentName();
        return documentName == null || documentName.isBlank()
                ? document.getFileName()
                : documentName;
    }
}
