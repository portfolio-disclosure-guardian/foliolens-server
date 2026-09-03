package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosurePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

/** -PcontestDatasetRoot=... 를 지정한 경우에만 로컬 제공 원문 43건을 읽는다. DB/외부 네트워크 사용 없음. */
@EnabledIfSystemProperty(named = "foliolens.test.dataset-root", matches = ".+")
class HtmlFacilityCorpusTest {
    @Test void validatesAll43FacilityDocuments() throws Exception {
        String root = System.getProperty("foliolens.test.dataset-root");
        var resolver = new DisclosurePathResolver(root);
        var mapper = new ObjectMapper();
        var validator = HtmlParserTestSupport.validator();
        int documents = 0;
        int corrections = 0;
        int tables = 0;
        int rows = 0;
        int cells = 0;
        int links = 0;
        for (String line : Files.readAllLines(Path.of(root).resolve("manifest.jsonl"))) {
            var item = mapper.readTree(line);
            if (!"exchange".equals(item.path("doc_group").asText())
                    || !"신규시설투자등".equals(item.path("doc_subtype").asText())) continue;
            var directory = resolver.resolveDirectory(item.path("file_path").asText());
            Path file = directory.resolve(item.path("rcept_no").asText() + ".xml");
            var result = validator.validate(file);
            boolean correction = item.path("is_correction").asBoolean();
            assertThat(result.metrics().tableCount()).as(file.toString()).isEqualTo(correction ? 4 : 1);
            assertThat(result.document().sections()).as(file.toString()).hasSize(correction ? 2 : 1);
            if (correction) {
                corrections++;
                assertThat(result.document().sections().getFirst().title()).isEqualTo("정정신고(보고)");
            }
            documents++;
            tables += result.metrics().tableCount();
            rows += result.metrics().tableRowCount();
            cells += result.metrics().tableCellCount();
            links += result.document().relatedLinks().size();
        }
        assertThat(documents).isEqualTo(43);
        assertThat(corrections).isEqualTo(15);
        assertThat(tables).isEqualTo(88);
        assertThat(rows).isEqualTo(845);
        assertThat(cells).isEqualTo(1873);
        assertThat(links).isEqualTo(18);
        System.out.printf("HTML corpus: documents=%d corrections=%d tables=%d rows=%d cells=%d links=%d%n",
                documents, corrections, tables, rows, cells, links);
    }
}
