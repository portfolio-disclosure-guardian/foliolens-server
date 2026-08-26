package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlockType;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Set;

/**
 * disclosure_content_blocks.structured_content에 저장된
 * TABLE JSONB를 ParsedDisclosureTable로 변환한다.
 */
@Component
public class DisclosureTablePayloadReader {

    private static final Set<Integer> SUPPORTED_SCHEMA_VERSIONS =
            Set.of(1, 2);

    private final ObjectMapper objectMapper;

    public DisclosureTablePayloadReader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper는 필수입니다."
        );
    }

    public ParsedDisclosureTable read(DisclosureContentBlock block) {
        Objects.requireNonNull(
                block,
                "block은 필수입니다."
        );

        if (block.getBlockType() != DisclosureContentBlockType.TABLE) {
            throw new IllegalArgumentException(
                    "TABLE Block만 읽을 수 있습니다."
                            + " blockType=" + block.getBlockType()
            );
        }

        return read(block.getStructuredContent());
    }

    public ParsedDisclosureTable read(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException(
                    "TABLE structuredContent는 JSON 객체여야 합니다."
            );
        }

        JsonNode schemaVersionNode = payload.get("schemaVersion");

        if (schemaVersionNode == null || !schemaVersionNode.isIntegralNumber()) {
            throw new IllegalArgumentException(
                    "TABLE payload의 schemaVersion이 올바르지 않습니다."
            );
        }

        int schemaVersion = schemaVersionNode.asInt();

        if (!SUPPORTED_SCHEMA_VERSIONS.contains(schemaVersion)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 TABLE payload 버전입니다."
                            + " schemaVersion=" + schemaVersion
            );
        }

        JsonNode tableNode = payload.get("table");

        if (tableNode == null || !tableNode.isObject()) {
            throw new IllegalArgumentException(
                    "TABLE payload의 table 객체가 없습니다."
            );
        }

        try {
            ParsedDisclosureTable table =
                    objectMapper.treeToValue(
                            tableNode,
                            ParsedDisclosureTable.class
                    );

            return Objects.requireNonNull(
                    table,
                    "TABLE payload를 변환하지 못했습니다."
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "TABLE payload 형식이 올바르지 않습니다."
                            + " 원인=" + findRootCauseMessage(exception),
                    exception
            );
        }
    }

    private String findRootCauseMessage(Throwable exception) {
        Throwable current = exception;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }

        return message;
    }
}
