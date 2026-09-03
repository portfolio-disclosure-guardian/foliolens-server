package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.*;
import com.foliolens.backend.disclosure.infrastructure.parsing.pdf.*;
import com.foliolens.backend.disclosure.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PdfDisclosureChunkingTest {
    @Test void longPdfPageSplitsWithoutCrossingPageBoundary(@TempDir Path directory) throws Exception {
        String text = String.join("\n", IntStream.range(0, 50)
                .mapToObj(i -> "Sentence%03d: annual revenue and investment report for testing complete text preservation.".formatted(i))
                .toList());
        Path file = PdfTextDisclosureParserTest.createPdf(directory.resolve("long.pdf"), List.of(text, "Second page only."));
        var result = generate(file);
        assertSources(result);
        assertThat(result.chunks.size()).isGreaterThan(2);
        String all = String.join(" ", result.chunks.stream().map(GeneratedDisclosureChunk::bodyText).toList());
        for (int i = 0; i < 50; i++) assertThat(all).contains("Sentence%03d".formatted(i));
        assertThat(result.chunks.getLast().sectionPath()).isEqualTo("PDF 페이지 2");
    }

    /** 세 실제 PDF를 읽어 메모리에서만 파싱·청킹한다. 실제 DB 적재 아님. */
    @Test
    @EnabledIfSystemProperty(named = "foliolens.test.dataset-root", matches = ".+")
    void validatesAllThreeContestPdfs() throws Exception {
        Path root = Path.of(System.getProperty("foliolens.test.dataset-root"));
        List<Path> files;
        try (var paths = Files.walk(root.resolve("raw/periodic"))) {
            files = paths.filter(p -> p.toString().endsWith(".pdf")).sorted().toList();
        }
        assertThat(files).hasSize(3);
        var expectedPages = Map.of("20260619000667.pdf", 1085, "20260513000860.pdf", 447, "20240514001522.pdf", 252);
        for (Path file : files) {
            long start = System.nanoTime();
            var result = generate(file);
            assertSources(result);
            assertThat(result.report.pageCount()).isEqualTo(expectedPages.get(file.getFileName().toString()));
            assertThat(result.mapping.sections()).hasSize(result.report.pageCount());
            assertThat(result.mapping.blocks()).hasSize(result.report.pageCount() - result.report.noTextPages().size());
            System.out.printf("PDF corpus: file=%s pages=%d blocks=%d chunks=%d noTextPages=%s suspiciousPages=%s elapsedMillis=%d%n",
                    file.getFileName(), result.report.pageCount(), result.mapping.blocks().size(), result.chunks.size(),
                    result.report.noTextPages(), result.report.suspiciousPages(), (System.nanoTime() - start) / 1_000_000);
        }
    }

    private Result generate(Path file) {
        var parsed = new PdfTextDisclosureParser().parse(file);
        var document = mock(DisclosureDocument.class);
        when(document.getId()).thenReturn(UUID.randomUUID());
        when(document.getFileName()).thenReturn(parsed.fileName());
        when(document.getContentFormat()).thenReturn(DisclosureDocumentContentFormat.PDF);
        var json = new ObjectMapper();
        var mapping = new ParsedDisclosureEntityMapper(json).map(document, parsed);
        mapping.sections().forEach(s -> ReflectionTestUtils.setField(s, "id", UUID.randomUUID()));
        mapping.blocks().forEach(b -> ReflectionTestUtils.setField(b, "id", UUID.randomUUID()));
        var policy = DisclosureChunkingPolicy.disclosureV2();
        var normalizer = new ChunkTextNormalizer();
        var splitter = new SentenceBoundarySplitter();
        var tables = new TableChunkGenerator(policy, new DisclosureTablePayloadReader(json),
                new NestedTableContextSelector(normalizer, splitter), new TableLogicalGridBuilder(),
                new TableTextSerializer(normalizer), normalizer, splitter);
        var generator = new DisclosureChunkGenerator(policy, new SectionPathResolver(),
                new TextChunkGenerator(policy, normalizer, splitter), tables);
        return new Result(mapping, generator.generateChunks(document.getId(), mapping.sections(), mapping.blocks()),
                parsed.pdfTextReport());
    }

    private void assertSources(Result result) {
        assertThat(result.chunks).isNotEmpty();
        Map<UUID, DisclosureContentBlock> blocks = new HashMap<>();
        result.mapping.blocks().forEach(b -> blocks.put(b.getId(), b));
        Set<UUID> referenced = new HashSet<>();
        assertThat(result.chunks).extracting(GeneratedDisclosureChunk::chunkSequenceNo)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, result.chunks.size()).boxed().toList());
        for (var chunk : result.chunks) {
            assertThat(chunk.chunkType()).isEqualTo(DisclosureChunkType.TEXT);
            assertThat(chunk.bodyCharacterCount()).isLessThanOrEqualTo(2000);
            assertThat(chunk.sources()).hasSize(1);
            var source = chunk.sources().getFirst();
            referenced.add(source.contentBlockId());
            var block = blocks.get(source.contentBlockId());
            assertThat(block.getSourcePageNumber()).isPositive();
            assertThat(source.sourceLineStart()).isEqualTo(-1);
            assertThat(source.sourceLineEnd()).isEqualTo(-1);
            assertThat(chunk.sectionPath()).isEqualTo("PDF 페이지 " + block.getSourcePageNumber());
        }
        assertThat(referenced).containsExactlyInAnyOrderElementsOf(blocks.keySet());
    }

    private record Result(DisclosureParseMappingResult mapping, List<GeneratedDisclosureChunk> chunks,
                          PdfTextExtractionReport report) {}
}
