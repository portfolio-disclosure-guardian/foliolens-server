package com.foliolens.backend.disclosure.infrastructure.search;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.repository.DisclosureChunkSearchRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * PostgreSQL에 저장된 TEXT·TABLE 청크를 문자열 일치 기반으로 검색하는
 * 첫 lexical 검색 구현.
 */
@Component
public class JdbcDisclosureChunkSearchRepository
        implements DisclosureChunkSearchRepository {

    private static final String CONTEST_PROVIDER = "CONTEST";
    private static final String COMPLETED = "COMPLETED";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDisclosureChunkSearchRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate은 필수입니다."
        );
    }

    @Override
    public SearchResult search(
            DisclosureChunkSearchCondition condition,
            ResolvedChunkSearchTerms terms,
            String retrievalVersion
    ) {
        Objects.requireNonNull(condition, "condition은 필수입니다.");
        Objects.requireNonNull(terms, "terms는 필수입니다.");
        String normalizedVersion = requireText(
                retrievalVersion,
                "retrievalVersion"
        );

        validateRequestedDisclosures(condition);
        validateRequestedDocuments(condition);
        ScopeStats scopeStats = loadScopeStats(condition);
        List<String> warnings = scopeWarnings(scopeStats);

        if (scopeStats.searchableDocumentCount() == 0
                || !terms.hasExecutableTerms()) {
            return new SearchResult(
                    List.of(),
                    scopeStats.searchableDocumentCount(),
                    0,
                    warnings
            );
        }

        ChunkQuery query = buildChunkQuery(condition, terms);
        Integer candidateCount = jdbcTemplate.queryForObject(
                countSql(query.whereClause()),
                query.parameters(),
                Integer.class
        );
        int totalCandidates = candidateCount == null ? 0 : candidateCount;

        if (totalCandidates == 0) {
            return new SearchResult(
                    List.of(),
                    scopeStats.searchableDocumentCount(),
                    0,
                    warnings
            );
        }

        List<ChunkRow> rows = jdbcTemplate.query(
                selectSql(query),
                query.parameters(),
                (resultSet, rowNumber) -> mapChunkRow(resultSet)
        );

        Map<UUID, List<DisclosureChunkSourceReference>> sourcesByChunkId =
                loadSources(rows);

        List<DisclosureChunkSearchHit> hits = rows.stream()
                .map(row -> toHit(
                        row,
                        terms,
                        sourcesByChunkId.getOrDefault(
                                row.chunkId(),
                                List.of()
                        ),
                        normalizedVersion
                ))
                .toList();

        List<String> resultWarnings = new ArrayList<>(warnings);
        if (hits.stream().flatMap(hit -> hit.sources().stream()).anyMatch(source -> source.sourcePageNumber() != null)) {
            resultWarnings.add(com.foliolens.backend.disclosure.infrastructure.parsing.pdf.PdfTextExtractionReport.LIMITATION);
        }
        if (hits.stream().flatMap(hit -> hit.sources().stream()).anyMatch(DisclosureChunkSourceReference::textExtractionSuspect)) {
            resultWarnings.add("텍스트 추출 품질이 의심되는 PDF 페이지가 포함됐습니다. 원본 페이지를 확인해야 합니다.");
        }
        return new SearchResult(
                hits,
                scopeStats.searchableDocumentCount(),
                totalCandidates,
                resultWarnings
        );
    }

    private void validateRequestedDisclosures(
            DisclosureChunkSearchCondition condition
    ) {
        MapSqlParameterSource parameters = baseScopeParameters(condition);
        Integer matchedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM disclosures d
                WHERE d.source_provider = :sourceProvider
                  AND d.id IN (:disclosureIds)
                """,
                parameters,
                Integer.class
        );

        if (matchedCount == null
                || matchedCount != condition.disclosureIds().size()) {
            throw new IllegalArgumentException(
                    "disclosureIds에는 존재하지 않거나 대회 데이터가 아닌 "
                            + "공시가 포함돼 있습니다."
            );
        }
    }

    private void validateRequestedDocuments(
            DisclosureChunkSearchCondition condition
    ) {
        if (condition.documentIds().isEmpty()) {
            return;
        }

        MapSqlParameterSource parameters = baseScopeParameters(condition);
        Integer matchedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM disclosure_documents dd
                JOIN disclosures d
                  ON d.id = dd.disclosure_id
                WHERE d.source_provider = :sourceProvider
                  AND d.id IN (:disclosureIds)
                  AND dd.id IN (:documentIds)
                """,
                parameters,
                Integer.class
        );

        if (matchedCount == null
                || matchedCount != condition.documentIds().size()) {
            throw new IllegalArgumentException(
                    "documentIds에는 요청한 공시에 속하지 않거나 "
                            + "존재하지 않는 문서가 포함돼 있습니다."
            );
        }
    }

    private ScopeStats loadScopeStats(
            DisclosureChunkSearchCondition condition
    ) {
        MapSqlParameterSource parameters = baseScopeParameters(condition);
        String documentFilter = condition.documentIds().isEmpty()
                ? ""
                : " AND dd.id IN (:documentIds)";

        return jdbcTemplate.queryForObject(
                """
                SELECT
                    COUNT(*) AS total_document_count,
                    COUNT(*) FILTER (
                        WHERE dd.parse_status <> :completed
                    ) AS parse_incomplete_count,
                    COUNT(*) FILTER (
                        WHERE dd.chunk_status <> :completed
                    ) AS chunk_incomplete_count,
                    COUNT(*) FILTER (
                        WHERE dd.chunk_status = :completed
                    ) AS searchable_document_count
                FROM disclosure_documents dd
                JOIN disclosures d
                  ON d.id = dd.disclosure_id
                WHERE d.source_provider = :sourceProvider
                  AND d.id IN (:disclosureIds)
                %s
                """.formatted(documentFilter),
                parameters,
                (resultSet, rowNumber) -> new ScopeStats(
                        resultSet.getInt("total_document_count"),
                        resultSet.getInt("parse_incomplete_count"),
                        resultSet.getInt("chunk_incomplete_count"),
                        resultSet.getInt("searchable_document_count")
                )
        );
    }

    private List<String> scopeWarnings(ScopeStats stats) {
        List<String> warnings = new ArrayList<>();

        if (stats.totalDocumentCount() == 0) {
            warnings.add("선택된 공시에 등록된 원문 문서가 없습니다.");
            return List.copyOf(warnings);
        }

        if (stats.parseIncompleteCount() > 0) {
            warnings.add(
                    "파싱이 완료되지 않은 원문 문서가 "
                            + stats.parseIncompleteCount() + "개 있습니다."
            );
        }

        if (stats.chunkIncompleteCount() > 0) {
            warnings.add(
                    "청킹이 완료되지 않은 원문 문서가 "
                            + stats.chunkIncompleteCount() + "개 있습니다."
            );
        }

        return List.copyOf(warnings);
    }

    private ChunkQuery buildChunkQuery(
            DisclosureChunkSearchCondition condition,
            ResolvedChunkSearchTerms terms
    ) {
        StringJoiner predicates = new StringJoiner(
                "\n  AND ",
                "WHERE ",
                ""
        );
        MapSqlParameterSource parameters = baseScopeParameters(condition);

        predicates.add("d.source_provider = :sourceProvider");
        predicates.add("d.id IN (:disclosureIds)");
        predicates.add("dd.chunk_status = :completed");
        predicates.add("dc.chunk_type IN (:chunkTypes)");

        parameters.addValue(
                "chunkTypes",
                condition.effectiveChunkTypes().stream()
                        .map(Enum::name)
                        .toList()
        );
        parameters.addValue("topK", condition.topK());

        if (!condition.documentIds().isEmpty()) {
            predicates.add("dd.id IN (:documentIds)");
        }

        List<String> candidateMatches = new ArrayList<>();
        ScoreExpressions scores = addTermExpressions(
                terms,
                parameters,
                candidateMatches
        );

        predicates.add(
                candidateMatches.isEmpty()
                        ? "FALSE"
                        : "(" + String.join(" OR ", candidateMatches) + ")"
        );

        return new ChunkQuery(
                predicates.toString(),
                scores,
                parameters
        );
    }

    private ScoreExpressions addTermExpressions(
            ResolvedChunkSearchTerms terms,
            MapSqlParameterSource parameters,
            List<String> candidateMatches
    ) {
        List<String> reportScores = new ArrayList<>();
        List<String> sectionScores = new ArrayList<>();
        List<String> bodyScores = new ArrayList<>();
        List<String> phraseBonuses = new ArrayList<>();
        List<String> factBonuses = new ArrayList<>();

        for (int index = 0; index < terms.keywords().size(); index++) {
            String parameterName = "keyword" + index;
            String term = terms.keywords().get(index);
            parameters.addValue(
                    parameterName,
                    term.toLowerCase(Locale.ROOT)
            );

            String searchMatch = contains(
                    parameterName,
                    "dc.search_text"
            );
            String bodyMatch = contains(parameterName, "dc.body_text");
            candidateMatches.add(searchMatch);

            reportScores.add(caseScore(
                    contains(parameterName, "d.report_name"),
                    0.25
            ));
            bodyScores.add(
                    "CASE WHEN " + bodyMatch + " THEN 1.0 "
                            + "WHEN " + searchMatch + " THEN 0.5 "
                            + "ELSE 0.0 END"
            );

            if (term.contains(" ")) {
                phraseBonuses.add(caseScore(bodyMatch, 0.5));
            }

            if (terms.factHintTerms().contains(term)) {
                factBonuses.add(caseScore(searchMatch, 0.75));
            }
        }

        for (int index = 0; index < terms.sectionHints().size(); index++) {
            String parameterName = "sectionHint" + index;
            parameters.addValue(
                    parameterName,
                    terms.sectionHints().get(index)
                            .toLowerCase(Locale.ROOT)
            );

            String sectionMatch = contains(
                    parameterName,
                    "dc.section_path"
            );
            candidateMatches.add(sectionMatch);
            sectionScores.add(caseScore(sectionMatch, 1.5));
        }

        return new ScoreExpressions(
                sumOrZero(reportScores),
                sumOrZero(sectionScores),
                sumOrZero(bodyScores),
                sumOrZero(phraseBonuses),
                sumOrZero(factBonuses),
                "0.0"
        );
    }

    private String contains(String parameterName, String columnName) {
        return "POSITION(:" + parameterName
                + " IN LOWER(" + columnName + ")) > 0";
    }

    private String caseScore(String condition, double score) {
        return "CASE WHEN " + condition + " THEN " + score
                + " ELSE 0.0 END";
    }

    private String sumOrZero(List<String> expressions) {
        if (expressions.isEmpty()) {
            return "0.0";
        }

        return "(" + String.join(" + ", expressions) + ")";
    }

    private String countSql(String whereClause) {
        return """
                SELECT COUNT(*)
                FROM disclosure_chunks dc
                JOIN disclosure_documents dd
                  ON dd.id = dc.disclosure_document_id
                JOIN disclosures d
                  ON d.id = dd.disclosure_id
                %s
                """.formatted(whereClause);
    }

    private String selectSql(ChunkQuery query) {
        ScoreExpressions score = query.scores();
        String finalScore = score.finalScore();

        return """
                SELECT
                    dc.id AS chunk_id,
                    d.id AS disclosure_id,
                    dd.id AS disclosure_document_id,
                    c.id AS company_id,
                    c.listed_name AS company_name,
                    d.receipt_no,
                    d.receipt_date,
                    d.report_name,
                    d.correction,
                    COALESCE(NULLIF(BTRIM(dd.document_name), ''), dd.file_name)
                        AS document_name,
                    dd.document_role,
                    dc.chunk_type,
                    dc.chunk_sequence_no,
                    dc.section_path,
                    dc.body_text,
                    dc.search_text,
                    dc.generator_version,
                    %s AS report_name_score,
                    %s AS section_path_score,
                    %s AS body_score,
                    %s AS phrase_bonus,
                    %s AS fact_hint_bonus,
                    %s AS correction_weight,
                    %s AS final_score
                FROM disclosure_chunks dc
                JOIN disclosure_documents dd
                  ON dd.id = dc.disclosure_document_id
                JOIN disclosures d
                  ON d.id = dd.disclosure_id
                JOIN companies c
                  ON c.id = d.company_id
                %s
                ORDER BY
                    final_score DESC,
                    d.receipt_date DESC,
                    dc.chunk_sequence_no ASC,
                    dc.id ASC
                LIMIT :topK
                """.formatted(
                score.reportNameScore(),
                score.sectionPathScore(),
                score.bodyScore(),
                score.phraseBonus(),
                score.factHintBonus(),
                score.correctionPenaltyOrBonus(),
                finalScore,
                query.whereClause()
        );
    }

    private ChunkRow mapChunkRow(ResultSet resultSet) throws SQLException {
        return new ChunkRow(
                resultSet.getObject("chunk_id", UUID.class),
                resultSet.getObject("disclosure_id", UUID.class),
                resultSet.getObject("disclosure_document_id", UUID.class),
                resultSet.getObject("company_id", UUID.class),
                resultSet.getString("company_name"),
                resultSet.getString("receipt_no"),
                resultSet.getObject("receipt_date", LocalDate.class),
                resultSet.getString("report_name"),
                resultSet.getBoolean("correction"),
                resultSet.getString("document_name"),
                DisclosureDocumentRole.valueOf(
                        resultSet.getString("document_role")
                ),
                DisclosureChunkType.valueOf(
                        resultSet.getString("chunk_type")
                ),
                resultSet.getInt("chunk_sequence_no"),
                resultSet.getString("section_path"),
                resultSet.getString("body_text"),
                resultSet.getString("search_text"),
                resultSet.getString("generator_version"),
                resultSet.getDouble("report_name_score"),
                resultSet.getDouble("section_path_score"),
                resultSet.getDouble("body_score"),
                resultSet.getDouble("phrase_bonus"),
                resultSet.getDouble("fact_hint_bonus"),
                resultSet.getDouble("correction_weight"),
                resultSet.getDouble("final_score")
        );
    }

    private Map<UUID, List<DisclosureChunkSourceReference>> loadSources(
            List<ChunkRow> rows
    ) {
        if (rows.isEmpty()) {
            return Map.of();
        }

        List<UUID> chunkIds = rows.stream()
                .map(ChunkRow::chunkId)
                .toList();
        MapSqlParameterSource parameters = new MapSqlParameterSource(
                "chunkIds",
                chunkIds
        );

        List<ChunkSourceRow> sourceRows = jdbcTemplate.query(
                """
                SELECT
                    dcs.disclosure_chunk_id,
                    dcs.id AS chunk_source_id,
                    dcs.content_block_id,
                    dcs.source_order,
                    dcs.block_sequence_no,
                    dcs.source_line_start,
                    dcs.source_line_end,
                    dcs.table_nesting_path,
                    dcs.table_row_index_start,
                    dcs.table_row_index_end,
                    dcb.source_page_number,
                    dcb.text_extraction_suspect
                FROM disclosure_chunk_sources dcs
                JOIN disclosure_content_blocks dcb ON dcb.id = dcs.content_block_id
                WHERE dcs.disclosure_chunk_id IN (:chunkIds)
                ORDER BY
                    dcs.disclosure_chunk_id,
                    dcs.source_order
                """,
                parameters,
                (resultSet, rowNumber) -> new ChunkSourceRow(
                        resultSet.getObject(
                                "disclosure_chunk_id",
                                UUID.class
                        ),
                        new DisclosureChunkSourceReference(
                                resultSet.getObject(
                                        "chunk_source_id",
                                        UUID.class
                                ),
                                resultSet.getObject(
                                        "content_block_id",
                                        UUID.class
                                ),
                                resultSet.getInt("source_order"),
                                resultSet.getInt("block_sequence_no"),
                                resultSet.getInt("source_line_start"),
                                resultSet.getInt("source_line_end"),
                                resultSet.getString("table_nesting_path"),
                                nullableInteger(
                                        resultSet,
                                        "table_row_index_start"
                                ),
                                nullableInteger(
                                        resultSet,
                                        "table_row_index_end"
                                ),
                                nullableInteger(resultSet, "source_page_number"),
                                resultSet.getBoolean("text_extraction_suspect")
                        )
                )
        );

        Map<UUID, List<DisclosureChunkSourceReference>> grouped =
                new LinkedHashMap<>();
        for (ChunkSourceRow sourceRow : sourceRows) {
            grouped.computeIfAbsent(
                    sourceRow.chunkId(),
                    ignored -> new ArrayList<>()
            ).add(sourceRow.source());
        }

        Map<UUID, List<DisclosureChunkSourceReference>> immutable =
                new LinkedHashMap<>();
        grouped.forEach((chunkId, sources) ->
                immutable.put(chunkId, List.copyOf(sources))
        );
        return Map.copyOf(immutable);
    }

    private Integer nullableInteger(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private DisclosureChunkSearchHit toHit(
            ChunkRow row,
            ResolvedChunkSearchTerms terms,
            List<DisclosureChunkSourceReference> sources,
            String retrievalVersion
    ) {
        SearchScoreBreakdown breakdown = new SearchScoreBreakdown(
                row.reportNameScore(),
                row.sectionPathScore(),
                row.bodyScore(),
                row.phraseBonus(),
                row.factHintBonus(),
                row.correctionWeight(),
                row.finalScore()
        );

        return new DisclosureChunkSearchHit(
                row.chunkId(),
                row.disclosureId(),
                row.documentId(),
                row.companyId(),
                row.companyName(),
                row.receiptNo(),
                row.receiptDate(),
                row.reportName(),
                row.correction(),
                row.documentName(),
                row.documentRole(),
                null,
                row.chunkType(),
                row.chunkSequenceNo(),
                row.sectionPath(),
                row.bodyText(),
                row.searchText(),
                row.finalScore(),
                breakdown,
                matchedTerms(row, terms),
                sources,
                row.generatorVersion(),
                retrievalVersion
        );
    }

    private List<String> matchedTerms(
            ChunkRow row,
            ResolvedChunkSearchTerms terms
    ) {
        String searchableText = String.join(
                "\n",
                row.reportName(),
                row.sectionPath(),
                row.bodyText(),
                row.searchText()
        ).toLowerCase(Locale.ROOT);
        Set<String> allTerms = new LinkedHashSet<>(terms.keywords());
        allTerms.addAll(terms.sectionHints());

        return allTerms.stream()
                .filter(term -> searchableText.contains(
                        term.toLowerCase(Locale.ROOT)
                ))
                .toList();
    }

    private MapSqlParameterSource baseScopeParameters(
            DisclosureChunkSearchCondition condition
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("sourceProvider", CONTEST_PROVIDER);
        parameters.addValue("completed", COMPLETED);
        parameters.addValue("disclosureIds", condition.disclosureIds());

        if (!condition.documentIds().isEmpty()) {
            parameters.addValue("documentIds", condition.documentIds());
        }

        return parameters;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없습니다."
            );
        }

        return value.strip();
    }

    private record ScopeStats(
            int totalDocumentCount,
            int parseIncompleteCount,
            int chunkIncompleteCount,
            int searchableDocumentCount
    ) {
    }

    private record ScoreExpressions(
            String reportNameScore,
            String sectionPathScore,
            String bodyScore,
            String phraseBonus,
            String factHintBonus,
            String correctionPenaltyOrBonus
    ) {

        String finalScore() {
            return "(" + String.join(
                    " + ",
                    reportNameScore,
                    sectionPathScore,
                    bodyScore,
                    phraseBonus,
                    factHintBonus,
                    correctionPenaltyOrBonus
            ) + ")";
        }
    }

    private record ChunkQuery(
            String whereClause,
            ScoreExpressions scores,
            MapSqlParameterSource parameters
    ) {
    }

    private record ChunkRow(
            UUID chunkId,
            UUID disclosureId,
            UUID documentId,
            UUID companyId,
            String companyName,
            String receiptNo,
            LocalDate receiptDate,
            String reportName,
            boolean correction,
            String documentName,
            DisclosureDocumentRole documentRole,
            DisclosureChunkType chunkType,
            int chunkSequenceNo,
            String sectionPath,
            String bodyText,
            String searchText,
            String generatorVersion,
            double reportNameScore,
            double sectionPathScore,
            double bodyScore,
            double phraseBonus,
            double factHintBonus,
            double correctionWeight,
            double finalScore
    ) {
    }

    private record ChunkSourceRow(
            UUID chunkId,
            DisclosureChunkSourceReference source
    ) {
    }
}
