package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.*;
import com.foliolens.backend.disclosure.infrastructure.parsing.html.HtmlParserTestSupport;
import com.foliolens.backend.disclosure.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosurePathResolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** HTML 원문 → 공통 파싱/JSONB 모델 → 실제 청킹 → 엔티티 매핑. DB 사용 없음. */
class HtmlDisclosureChunkingTest {
    private final DisclosureChunkingPolicy policy = DisclosureChunkingPolicy.disclosureV2();

    @ParameterizedTest
    @ValueSource(strings = {"facility-original.xml", "facility-correction.xml", "contract-original.xml",
            "contract-correction.xml", "contract-cancellation.xml", "major-management.xml"})
    void generatesChunksWithCompleteBlockAndTableRowSources(String fixture) throws Exception {
        var result = generate(HtmlParserTestSupport.fixture(fixture));
        assertSourcesAndBounds(result);
    }

    private void assertSourcesAndBounds(Result result) {
        assertThat(result.chunks()).isNotEmpty();
        assertThat(result.chunks()).extracting(GeneratedDisclosureChunk::chunkSequenceNo)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, result.chunks().size()).boxed().toList());
        var entities = new GeneratedDisclosureChunkEntityMapper().toEntities(result.document(),
                result.mapping().sections(), result.mapping().blocks(), result.chunks());
        assertThat(entities).hasSize(result.chunks().size());

        for (var block : result.mapping().blocks()) {
            var sources = result.chunks().stream().flatMap(c -> c.sources().stream())
                    .filter(s -> s.contentBlockId().equals(block.getId())).toList();
            if (isSymbolOnlyTable(block)) {
                // 원본 표는 남지만 기호 전용 표는 더 이상 검색 청크/출처를 만들지 않는다.
                assertThat(sources).isEmpty();
                continue;
            }
            assertThat(sources).as("block %s", block.getSequenceNo()).isNotEmpty();
            if (block.getBlockType() == DisclosureContentBlockType.TABLE) {
                int rowCount = block.getStructuredContent().path("table").path("rows").size();
                for (int row = 0; row < rowCount; row++) {
                    int rowIndex = row;
                    assertThat(sources).anyMatch(s -> s.isTableSource()
                            && s.tableRowIndexStart() <= rowIndex && s.tableRowIndexEnd() >= rowIndex);
                }
            }
        }
        for (var chunk : result.chunks()) {
            assertThat(chunk.generatorName()).isEqualTo("DisclosureChunkGenerator");
            assertThat(chunk.generatorVersion()).isEqualTo("disclosure-chunk-v2");
            assertThat(chunk.bodyText()).containsPattern("[\\p{L}\\p{N}]");
            assertThat(chunk.bodyCharacterCount()).isLessThanOrEqualTo(
                    chunk.chunkType() == DisclosureChunkType.TABLE
                            ? policy.table().absoluteMaxChars() : policy.text().absoluteMaxChars());
            assertThat(chunk.searchText()).contains(chunk.sectionPath());
            for (var source : chunk.sources()) {
                var block = result.mapping().blocks().stream()
                        .filter(b -> b.getId().equals(source.contentBlockId())).findFirst().orElseThrow();
                assertThat(source.sourceLineStart()).isBetween(block.getSourceLineStart(), block.getSourceLineEnd());
                assertThat(source.sourceLineEnd()).isBetween(source.sourceLineStart(), block.getSourceLineEnd());
                assertThat(chunk.sectionId()).isEqualTo(block.getSection().getId());
            }
        }
    }

    private boolean isSymbolOnlyTable(DisclosureContentBlock block) {
        if (block.getBlockType() != DisclosureContentBlockType.TABLE) return false;
        // 표의 메타데이터(rowIndex 등)가 아니라 실제 셀 텍스트만 조사한다.
        var table = block.getStructuredContent().path("table");
        for (var row : table.path("rows")) {
            for (var cell : row.path("cells")) {
                if (!cell.path("nestedTables").isEmpty() || !cell.path("images").isEmpty()) return false;
                if (cell.path("text").asText("").matches("(?s).*[\\p{L}\\p{N}].*")) return false;
            }
        }
        return true;
    }

    @Test
    void correctionAndCurrentValuesStayInDifferentSectionContexts() throws Exception {
        var chunks = generate(HtmlParserTestSupport.fixture("contract-correction.xml")).chunks();
        var correction = chunks.stream().filter(c -> c.sectionPath().contains("정정신고")).toList();
        var body = chunks.stream().filter(c -> !c.sectionPath().contains("정정신고")).toList();
        assertThat(correction).anyMatch(c -> c.bodyText().contains("100,000,000"));
        assertThat(correction).anyMatch(c -> c.bodyText().contains("120,000,000"));
        assertThat(body).anyMatch(c -> c.bodyText().contains("120,000,000"));
        assertThat(body).noneMatch(c -> c.bodyText().contains("100,000,000"));
    }

    @Test
    void preservesFacilityAmountUnitAndPurpose() throws Exception {
        var chunks = generate(HtmlParserTestSupport.fixture("facility-original.xml")).chunks();
        assertThat(chunks).anyMatch(c -> c.bodyText().contains("투자금액(원)")
                && c.bodyText().contains("5,296,200,000,000"));
        assertThat(chunks).anyMatch(c -> c.bodyText().contains("차세대 DRAM 생산능력 확장"));
    }

    @Test
    void managementParagraphsBecomeTextChunks() throws Exception {
        var chunks = generate(HtmlParserTestSupport.fixture("major-management.xml")).chunks();
        assertThat(chunks).anyMatch(c -> c.chunkType() == DisclosureChunkType.TEXT
                && c.bodyText().contains("이사회에서 승인한"));
        assertThat(chunks).anyMatch(c -> c.chunkType() == DisclosureChunkType.TABLE
                && c.bodyText().contains("2026-03-10"));
        assertThat(chunks).anyMatch(c -> c.chunkType() == DisclosureChunkType.TEXT
                && c.bodyText().contains("실제 진행 경과"));
    }

    @Test
    void longHtmlCellIsSplitWithoutLosingText(@TempDir Path directory) throws Exception {
        var sentences = IntStream.range(0, 150).mapToObj(i ->
                "항목%03d의 운영계획은 이사회 승인 이후 공시된 조건에 따라 진행합니다.".formatted(i)).toList();
        Path file = directory.resolve("long-cell.xml");
        Files.writeString(file, "<html><body><div class='xforms'><h1>투자판단 관련 주요경영사항</h1>"
                + "<table><tr><td>주요내용</td><td>" + String.join("<br>", sentences)
                + "</td></tr></table></div></body></html>");
        var chunks = generate(file).chunks();
        assertThat(chunks.size()).isGreaterThan(1);
        String all = String.join("\n", chunks.stream().map(GeneratedDisclosureChunk::bodyText).toList());
        // 절대 상한 분할에서 문장 중간의 공백이 청크 경계로 바뀔 수 있지만 내용은 모두 남아야 한다.
        for (String sentence : sentences) {
            assertThat(all.replaceAll("\\s+", "")).contains(sentence.replaceAll("\\s+", ""));
        }
        assertThat(chunks).allSatisfy(c -> assertThat(c.bodyCharacterCount()).isLessThanOrEqualTo(3_000));
    }

    @Test
    void sharedPolicyKeepsXmlV3SizeRules() {
        assertThat(policy.text()).isEqualTo(DisclosureChunkingPolicy.dartXmlV3().text());
        assertThat(policy.table()).isEqualTo(DisclosureChunkingPolicy.dartXmlV3().table());
        assertThat(policy.generatorVersion()).isEqualTo("disclosure-chunk-v2");
        assertThat(DisclosureChunkingPolicy.disclosureV1().generatorVersion()).isEqualTo("disclosure-chunk-v1");
    }

    @Test
    void skipsDashOnlyTableButKeepsDashInsideMeaningfulTable(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("dash-tables.xml");
        Files.writeString(file, """
                <html><body><div class="xforms"><h1>신규 시설투자 등</h1>
                <table><tr><td>-</td></tr></table>
                <table><tr><td>투자금액(원)</td><td>0</td></tr><tr><td>비고</td><td>-</td></tr></table>
                <table><tr><td>-</td></tr></table>
                </div></body></html>
                """);
        var result = generate(file);
        assertSourcesAndBounds(result);
        assertThat(result.mapping().blocks()).hasSize(3);
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().bodyText()).contains("투자금액(원) | 0", "비고 | -");
        assertThat(result.chunks().getFirst().sources()).extracting(GeneratedChunkSource::contentBlockId)
                .containsExactly(result.mapping().blocks().get(1).getId());
    }

    @Test
    @EnabledIfSystemProperty(named = "foliolens.test.dataset-root", matches = ".+")
    void previouslyPersistedFiveFacilityDocumentsLoseOnlyThreeDashChunks() throws Exception {
        var receipts = Set.of("20250730800037", "20241218800350", "20240926800370",
                "20240612800420", "20250430800633");
        String root = System.getProperty("foliolens.test.dataset-root");
        var resolver = new DisclosurePathResolver(root);
        var mapper = new ObjectMapper();
        int documents = 0;
        int blocks = 0;
        int chunks = 0;
        for (String line : Files.readAllLines(Path.of(root).resolve("manifest.jsonl"))) {
            var item = mapper.readTree(line);
            if (!receipts.contains(item.path("rcept_no").asText())) continue;
            Path file = resolver.resolveDirectory(item.path("file_path").asText())
                    .resolve(item.path("rcept_no").asText() + ".xml");
            var result = generate(file);
            assertSourcesAndBounds(result);
            documents++;
            blocks += result.mapping().blocks().size();
            chunks += result.chunks().size();
            String all = String.join("\n", result.chunks().stream().map(GeneratedDisclosureChunk::bodyText).toList())
                    .replaceAll("\\s+", "");
            for (var block : result.mapping().blocks()) {
                for (var row : block.getStructuredContent().path("table").path("rows")) {
                    for (var cell : row.path("cells")) {
                        for (String part : cell.path("text").asText("").split("\\R")) {
                            if (part.matches("(?s).*[\\p{L}\\p{N}].*")) {
                                assertThat(all).as(file.toString()).contains(part.replaceAll("\\s+", ""));
                            }
                        }
                    }
                }
            }
        }
        assertThat(documents).isEqualTo(5);
        assertThat(blocks).isEqualTo(14);
        assertThat(chunks).isEqualTo(11);
    }

    /** 원문을 읽어 메모리에서만 검증한다. 운영 DB/원문 파일은 변경하지 않는다. */
    @Test
    @EnabledIfSystemProperty(named = "foliolens.test.dataset-root", matches = ".+")
    void readsFiveRealDisclosuresPerHtmlSubtype() throws Exception {
        String root = System.getProperty("foliolens.test.dataset-root");
        var resolver = new DisclosurePathResolver(root);
        var json = new ObjectMapper();
        var groups = new LinkedHashMap<String, List<tools.jackson.databind.JsonNode>>();
        for (String subtype : List.of("신규시설투자등", "단일판매공급계약체결",
                "단일판매공급계약해지", "투자판단관련주요경영사항")) {
            groups.put(subtype, new ArrayList<>());
        }
        for (String line : Files.readAllLines(Path.of(root).resolve("manifest.jsonl"))) {
            var item = json.readTree(line);
            var group = groups.get(item.path("doc_subtype").asText());
            if (group != null && "exchange".equals(item.path("doc_group").asText())) group.add(item);
        }
        for (var entry : groups.entrySet()) {
            // 가능한 유형에서는 일반 3건 + 정정 2건, 정정이 없으면 일반 문서로 채운다.
            var selected = new LinkedHashSet<tools.jackson.databind.JsonNode>();
            entry.getValue().stream().filter(i -> !i.path("is_correction").asBoolean()).limit(3).forEach(selected::add);
            entry.getValue().stream().filter(i -> i.path("is_correction").asBoolean()).limit(2).forEach(selected::add);
            for (var item : entry.getValue()) {
                if (selected.size() == 5) break;
                selected.add(item);
            }
            assertThat(selected).as(entry.getKey()).hasSize(5);
            int chunks = 0;
            for (var item : selected) {
                Path file = resolver.resolveDirectory(item.path("file_path").asText())
                        .resolve(item.path("rcept_no").asText() + ".xml");
                var result = generate(file);
                assertSourcesAndBounds(result);
                chunks += result.chunks().size();
            }
            System.out.printf("HTML chunk corpus: subtype=%s documents=%d chunks=%d%n",
                    entry.getKey(), selected.size(), chunks);
        }
    }

    private Result generate(Path file) {
        var parsed = HtmlParserTestSupport.parser().parse(file);
        var document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(UUID.randomUUID());
        when(document.getFileName()).thenReturn(parsed.fileName());
        var objectMapper = new ObjectMapper();
        var mapping = new ParsedDisclosureEntityMapper(objectMapper).map(document, parsed);
        mapping.sections().forEach(s -> ReflectionTestUtils.setField(s, "id", UUID.randomUUID()));
        mapping.blocks().forEach(b -> ReflectionTestUtils.setField(b, "id", UUID.randomUUID()));
        var normalizer = new ChunkTextNormalizer();
        var splitter = new SentenceBoundarySplitter();
        var tableGenerator = new TableChunkGenerator(policy, new DisclosureTablePayloadReader(objectMapper),
                new NestedTableContextSelector(normalizer, splitter), new TableLogicalGridBuilder(),
                new TableTextSerializer(normalizer), normalizer, splitter);
        var generator = new DisclosureChunkGenerator(policy, new SectionPathResolver(),
                new TextChunkGenerator(policy, normalizer, splitter), tableGenerator);
        return new Result(document, mapping,
                generator.generateChunks(document.getId(), mapping.sections(), mapping.blocks()));
    }

    private record Result(DisclosureDocument document, DisclosureParseMappingResult mapping,
                          List<GeneratedDisclosureChunk> chunks) {}
}
