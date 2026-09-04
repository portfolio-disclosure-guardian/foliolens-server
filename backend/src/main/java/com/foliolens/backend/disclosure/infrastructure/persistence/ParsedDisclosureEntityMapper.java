package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlockType;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureSection;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureBlock;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureImage;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureSection;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class ParsedDisclosureEntityMapper {

    /*
     * TABLE v2부터 중첩 표의 parentContext가 저장된다.
     * IMAGE payload 구조는 변경되지 않았으므로 v1을 유지한다.
     */
    private static final int TABLE_SCHEMA_VERSION = 2;
    private static final int IMAGE_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public ParsedDisclosureEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 파서 결과 전체를 DB 저장용 엔티티 목록으로 변환
     */
    public DisclosureParseMappingResult map(
            DisclosureDocument disclosureDocument,
            ParsedDisclosureDocument parsedDocument
    ) {
        Objects.requireNonNull(
                disclosureDocument,
                "disclosureDocument는 필수입니다."
        );

        Objects.requireNonNull(
                parsedDocument,
                "parsedDocument는 필수입니다."
        );

        validateFileName(disclosureDocument, parsedDocument);

        List<DisclosureSection> sections = new ArrayList<>();
        List<DisclosureContentBlock> blocks = new ArrayList<>();

        /*
         * SECTION이 시작되기 전에 등장한 블록
         * 소속 섹션이 없으므로 section에 null을 전달
         */
        mapBlocks(
                disclosureDocument,
                null,
                parsedDocument.preambleBlocks(),
                blocks
        );

        /*
         * 최상위 섹션부터 재귀적으로 변환한다.
         */
        for (ParsedDisclosureSection parsedSection : parsedDocument.sections()) {

            // mapSection 안에 mapBlock 코드 있음
            mapSection(
                    disclosureDocument,
                    null,
                    parsedSection,
                    sections,
                    blocks
            );
        }

        // 재귀 탐색 순서와 관계없이 반환 결과는 원문의 sequenceNo 순서로 정렬
        sections.sort(Comparator.comparingInt(DisclosureSection::getSequenceNo));

        blocks.sort(Comparator.comparingInt(DisclosureContentBlock::getSequenceNo));

        return new DisclosureParseMappingResult(
                sections,
                blocks
        );
    }

    /**
     * 섹션 하나와 그 자식 섹션들을 재귀적으로 변환한다.
     */
    private void mapSection(
            DisclosureDocument disclosureDocument,
            DisclosureSection parentSection,
            ParsedDisclosureSection parsedSection,
            List<DisclosureSection> sections,
            List<DisclosureContentBlock> blocks
    ) {
        DisclosureSection section = DisclosureSection.create(
                disclosureDocument,
                parentSection,
                parsedSection.level(),
                parsedSection.order(),
                parsedSection.title(),
                parsedSection.sourceLineStart(),
                parsedSection.sourceLineEnd()
        );

        /*
         * 부모 섹션을 먼저 목록에 넣는다.
         * 이후 자식 섹션이 이 엔티티를 parentSection으로 참조한다.
         */
        sections.add(section);

        mapBlocks(
                disclosureDocument,
                section,
                parsedSection.blocks(),
                blocks
        );

        for (ParsedDisclosureSection child : parsedSection.children()) {

            // 재귀적으로 처리
            mapSection(
                    disclosureDocument,
                    section,
                    child,
                    sections,
                    blocks
            );
        }
    }

    /**
     * 같은 섹션에 속한 블록들을 변환한다.
     */
    private void mapBlocks(
            DisclosureDocument disclosureDocument,
            DisclosureSection section,
            List<ParsedDisclosureBlock> parsedBlocks,
            List<DisclosureContentBlock> blocks
    ) {
        for (ParsedDisclosureBlock parsedBlock : parsedBlocks) {
            blocks.add(
                    mapBlock(
                            disclosureDocument,
                            section,
                            parsedBlock
                    )
            );
        }
    }

    /**
     * 파싱 블록 타입에 맞는 JPA 엔티티를 생성한다.
     */
    private DisclosureContentBlock mapBlock(
            DisclosureDocument disclosureDocument,
            DisclosureSection section,
            ParsedDisclosureBlock parsedBlock
    ) {
        // 엔티티로 변환!
        DisclosureContentBlock block = switch (parsedBlock.type()) {
            case HEADING -> DisclosureContentBlock.text(
                    disclosureDocument,
                    section,
                    DisclosureContentBlockType.HEADING,
                    parsedBlock.order(),
                    parsedBlock.content(),
                    parsedBlock.sourceLineStart(),
                    parsedBlock.sourceLineEnd()
            );

            case PARAGRAPH -> DisclosureContentBlock.text(
                    disclosureDocument,
                    section,
                    DisclosureContentBlockType.PARAGRAPH,
                    parsedBlock.order(),
                    parsedBlock.content(),
                    parsedBlock.sourceLineStart(),
                    parsedBlock.sourceLineEnd()
            );

            // table 형태는 jsonb 형태를 jsonNode로 저장
            case TABLE -> DisclosureContentBlock.structured(
                    disclosureDocument,
                    section,
                    DisclosureContentBlockType.TABLE,
                    parsedBlock.order(),
                    toJson(
                            new TablePayload(
                                    TABLE_SCHEMA_VERSION,
                                    parsedBlock.table()
                            )
                    ),
                    parsedBlock.sourceLineStart(),
                    parsedBlock.sourceLineEnd()
            );

            case IMAGE -> DisclosureContentBlock.structured(
                    disclosureDocument,
                    section,
                    DisclosureContentBlockType.IMAGE,
                    parsedBlock.order(),
                    toJson(
                            new ImagePayload(
                                    IMAGE_SCHEMA_VERSION,
                                    parsedBlock.image()
                            )
                    ),
                    parsedBlock.sourceLineStart(),
                    parsedBlock.sourceLineEnd()
            );

            case PAGE_BREAK -> mapPageBreak(
                    disclosureDocument,
                    section,
                    parsedBlock
            );
        };
        if (parsedBlock.pdfPage() != null) {
            block.attachPdfPage(parsedBlock.pdfPage().pageNumber(), parsedBlock.pdfPage().textExtractionSuspect());
        }
        return block;
    }

    private DisclosureContentBlock mapPageBreak(
            DisclosureDocument disclosureDocument,
            DisclosureSection section,
            ParsedDisclosureBlock parsedBlock
    ) {
        /*
         * PAGE_BREAK 엔티티는 한 행만 저장하므로
         * 파서 결과도 시작 행과 종료 행이 같아야 한다.
         */
        if (parsedBlock.sourceLineStart()
                != parsedBlock.sourceLineEnd()) {

            throw new IllegalArgumentException(
                    "PAGE_BREAK의 시작 행과 종료 행이 다릅니다."
                            + " start="
                            + parsedBlock.sourceLineStart()
                            + ", end="
                            + parsedBlock.sourceLineEnd()
            );
        }

        return DisclosureContentBlock.pageBreak(
                disclosureDocument,
                section,
                parsedBlock.order(),
                parsedBlock.sourceLineStart()
        );
    }

    /**
     * TABLE 또는 IMAGE 모델을 JSONB 저장용 JsonNode로 변환한다.
     */
    private JsonNode toJson(Object payload) {
        JsonNode jsonNode = objectMapper.valueToTree(payload);

        if (jsonNode == null || !jsonNode.isObject()) {
            throw new IllegalStateException(
                    "구조화 블록을 JSON 객체로 변환하지 못했습니다."
            );
        }

        return jsonNode;
    }

    public JsonNode relatedLinksPayload(ParsedDisclosureDocument document) {
        return toJson(java.util.Map.of("schemaVersion", 1, "links", document.relatedLinks()));
    }

    public JsonNode parseMetadataPayload(ParsedDisclosureDocument document) {
        return document.pdfTextReport() == null ? toJson(java.util.Map.of()) : toJson(document.pdfTextReport());
    }

    /**
     * DB의 DisclosureDocument와 파서 결과가
     * 동일한 파일을 가리키는지 확인한다.
     */
    private void validateFileName(
            DisclosureDocument disclosureDocument,
            ParsedDisclosureDocument parsedDocument
    ) {
        if (!disclosureDocument.getFileName()
                .equals(parsedDocument.fileName())) {

            throw new IllegalArgumentException(
                    "DisclosureDocument와 파싱 결과의 파일명이 다릅니다."
                            + " documentFileName="
                            + disclosureDocument.getFileName()
                            + ", parsedFileName="
                            + parsedDocument.fileName()
            );
        }
    }

    /**
     * TABLE JSONB 최상위 구조.
     *
     * schemaVersion을 저장해 향후 JSON 구조가 변경되었을 때
     * 이전 데이터와 구분할 수 있게 한다.
     */
    private record TablePayload(
            int schemaVersion,
            ParsedDisclosureTable table
    ) {
    }

    /**
     * IMAGE JSONB 최상위 구조.
     */
    private record ImagePayload(
            int schemaVersion,
            ParsedDisclosureImage image
    ) {
    }
}
