package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosurePathResolver;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 운영 DB/네트워크 없이 제공 원문의 실패 10건·정정 오분류 14건을 포함한 50건 회귀 검증. */
@EnabledIfSystemProperty(named = "foliolens.test.dataset-root", matches = ".+")
class HtmlContractCorpusTest {
    @Test void validatesAll50ContractSampleDocuments() throws Exception {
        String root = System.getProperty("foliolens.test.dataset-root");
        Set<String> receipts = Files.readAllLines(HtmlParserTestSupport.fixture("contract-regression-receipts.txt"))
                .stream().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .collect(Collectors.toSet());
        assertThat(receipts).hasSize(50);
        var resolver = new DisclosurePathResolver(root);
        var mapper = new ObjectMapper();
        var validator = HtmlParserTestSupport.validator();
        var assertions = new SoftAssertions();
        Set<String> found = new HashSet<>();
        int passed = 0;
        for (String line : Files.readAllLines(Path.of(root).resolve("manifest.jsonl"))) {
            var item = mapper.readTree(line);
            String receipt = item.path("rcept_no").asText();
            if (!receipts.contains(receipt)) continue;
            assertThat(found.add(receipt)).as("duplicate receipt %s", receipt).isTrue();
            assertThat(item.path("doc_group").asText()).isEqualTo("exchange");
            assertThat(item.path("doc_subtype").asText()).isEqualTo("단일판매공급계약체결");
            Path file = resolver.resolveDirectory(item.path("file_path").asText()).resolve(receipt + ".xml");
            try {
                var result = validator.validate(file);
                var document = result.document();
                boolean correction = item.path("is_correction").asBoolean();
                assertions.assertThat(document.sections()).as(receipt).hasSize(correction ? 2 : 1);
                if (correction) {
                    assertions.assertThat(document.sections().getFirst().title()).as(receipt).isEqualTo("정정신고(보고)");
                    assertions.assertThat(document.sections().getLast().sourceLineStart()).as(receipt)
                            .isGreaterThan(document.sections().getFirst().sourceLineEnd());
                }
                assertions.assertThat(document.documentName()).as(receipt)
                        .isEqualTo(document.sections().getLast().title());
                assertions.assertThat(document.documentName().replaceAll("[\\sㆍ·・]", ""))
                        .as(receipt).startsWith("단일판매공급계약체결");
                assertions.assertThat(document.sections().getLast().blocks()).as(receipt).isNotEmpty();
                passed++;
            } catch (RuntimeException exception) {
                assertions.fail("%s: %s", receipt, exception.getMessage());
            }
        }
        assertThat(found).containsExactlyInAnyOrderElementsOf(receipts);
        System.out.printf("HTML contract regression: documents=%d passed=%d failed=%d%n", found.size(), passed, found.size() - passed);
        assertions.assertAll();
    }
}
