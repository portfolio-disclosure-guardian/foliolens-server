package com.foliolens.backend.disclosure.infrastructure.search;

import com.foliolens.backend.company.domain.SourceProvider;
import com.foliolens.backend.disclosure.domain.DisclosureCategory;
import com.foliolens.backend.disclosure.domain.DisclosureSourceGroup;
import com.foliolens.backend.disclosure.repository.DisclosureMetadataSearchRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * PostgreSQL의 disclosures·companies·disclosure_documents를 조회하는
 * 메타데이터 검색 Repository.
 */
@Repository
public class JdbcDisclosureMetadataSearchRepository
        implements DisclosureMetadataSearchRepository {

    private static final String CONTEST_PROVIDER = "CONTEST";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDisclosureMetadataSearchRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate은 필수입니다."
        );
    }

    @Override
    public SearchResult search(
            DisclosureMetadataSearchCondition condition
    ) {
        Objects.requireNonNull(condition, "condition은 필수입니다.");

        SearchQuery query = buildSearchQuery(condition);
        Integer candidateCount = jdbcTemplate.queryForObject(
                countSql(query.whereClause()),
                query.parameters(),
                Integer.class
        );

        int totalCount = candidateCount == null ? 0 : candidateCount;
        if (totalCount == 0) {
            return new SearchResult(List.of(), 0);
        }

        List<DisclosureMetadataSearchHit> items = jdbcTemplate.query(
                selectSql(
                        query.whereClause(),
                        query.scoreExpression()
                ),
                query.parameters(),
                (resultSet, rowNumber) -> mapHit(resultSet, condition)
        );

        return new SearchResult(items, totalCount);
    }

    private SearchQuery buildSearchQuery(
            DisclosureMetadataSearchCondition condition
    ) {
        StringJoiner predicates = new StringJoiner(
                "\n  AND ",
                "WHERE ",
                ""
        );
        MapSqlParameterSource parameters = new MapSqlParameterSource();

        predicates.add("d.source_provider = :sourceProvider");
        parameters.addValue("sourceProvider", CONTEST_PROVIDER);

        if (!condition.companyIds().isEmpty()) {
            predicates.add("d.company_id IN (:companyIds)");
            parameters.addValue("companyIds", condition.companyIds());
        }

        if (condition.receiptDateFrom() != null) {
            predicates.add("d.receipt_date >= :receiptDateFrom");
            parameters.addValue(
                    "receiptDateFrom",
                    condition.receiptDateFrom()
            );
        }

        LocalDate effectiveDateTo = condition.effectiveReceiptDateTo();
        if (effectiveDateTo != null) {
            predicates.add("d.receipt_date <= :receiptDateTo");
            parameters.addValue("receiptDateTo", effectiveDateTo);
        }

        if (!condition.sourceGroups().isEmpty()) {
            predicates.add("d.source_group IN (:sourceGroups)");
            parameters.addValue(
                    "sourceGroups",
                    condition.sourceGroups().stream()
                            .map(DisclosureSourceGroup::getValue)
                            .toList()
            );
        }

        if (!condition.categories().isEmpty()) {
            predicates.add("d.category IN (:categories)");
            parameters.addValue(
                    "categories",
                    condition.categories().stream()
                            .map(Enum::name)
                            .toList()
            );
        }

        if (!condition.rawSubtypes().isEmpty()) {
            predicates.add("d.raw_subtype IN (:rawSubtypes)");
            parameters.addValue("rawSubtypes", condition.rawSubtypes());
        }

        String scoreExpression = addTitleConditions(
                condition.titleTerms(),
                predicates,
                parameters
        );

        switch (condition.correctionFilter()) {
            case ORIGINAL_ONLY -> predicates.add("d.correction = FALSE");
            case CORRECTION_ONLY -> predicates.add("d.correction = TRUE");
            case ALL -> {
                // 원공시와 정정공시를 모두 검색한다.
            }
        }

        parameters.addValue("limit", condition.limit());

        return new SearchQuery(
                predicates.toString(),
                scoreExpression,
                parameters
        );
    }

    private String addTitleConditions(
            List<String> titleTerms,
            StringJoiner predicates,
            MapSqlParameterSource parameters
    ) {
        if (titleTerms.isEmpty()) {
            return "0.0";
        }

        List<String> matches = new ArrayList<>();
        List<String> scoreParts = new ArrayList<>();

        for (int index = 0; index < titleTerms.size(); index++) {
            String parameterName = "titleTerm" + index;
            String normalizedTerm = titleTerms.get(index)
                    .toLowerCase(Locale.ROOT);

            parameters.addValue(parameterName, normalizedTerm);
            matches.add(
                    "POSITION(:" + parameterName
                            + " IN LOWER(d.report_name)) > 0"
            );
            scoreParts.add(
                    "CASE "
                            + "WHEN LOWER(d.report_name) = :"
                            + parameterName + " THEN 3.0 "
                            + "WHEN POSITION(:" + parameterName
                            + " IN LOWER(d.report_name)) = 1 THEN 2.0 "
                            + "WHEN POSITION(:" + parameterName
                            + " IN LOWER(d.report_name)) > 0 THEN 1.0 "
                            + "ELSE 0.0 END"
            );
        }

        predicates.add("(" + String.join(" OR ", matches) + ")");
        return "(" + String.join(" + ", scoreParts) + ")";
    }

    private String countSql(String whereClause) {
        return """
                SELECT COUNT(*)
                FROM disclosures d
                %s
                """.formatted(whereClause);
    }

    private String selectSql(
            String whereClause,
            String scoreExpression
    ) {
        return """
                SELECT
                    d.id AS disclosure_id,
                    c.id AS company_id,
                    c.listed_name AS company_name,
                    c.stock_code,
                    d.receipt_no,
                    d.receipt_date,
                    d.report_name,
                    d.source_group,
                    d.category,
                    d.raw_subtype,
                    d.correction,
                    d.source_provider,
                    COUNT(dd.id) AS document_count,
                    %s AS search_score
                FROM disclosures d
                JOIN companies c
                  ON c.id = d.company_id
                LEFT JOIN disclosure_documents dd
                  ON dd.disclosure_id = d.id
                %s
                GROUP BY
                    d.id,
                    c.id,
                    c.listed_name,
                    c.stock_code
                ORDER BY
                    search_score DESC,
                    d.receipt_date DESC,
                    d.receipt_no DESC
                LIMIT :limit
                """.formatted(scoreExpression, whereClause);
    }

    private DisclosureMetadataSearchHit mapHit(
            ResultSet resultSet,
            DisclosureMetadataSearchCondition condition
    ) throws SQLException {
        String reportName = resultSet.getString("report_name");

        return new DisclosureMetadataSearchHit(
                resultSet.getObject("disclosure_id", java.util.UUID.class),
                resultSet.getObject("company_id", java.util.UUID.class),
                resultSet.getString("company_name"),
                resultSet.getString("stock_code"),
                resultSet.getString("receipt_no"),
                resultSet.getObject("receipt_date", LocalDate.class),
                reportName,
                DisclosureSourceGroup.fromValue(
                        resultSet.getString("source_group")
                ),
                DisclosureCategory.valueOf(
                        resultSet.getString("category")
                ),
                resultSet.getString("raw_subtype"),
                resultSet.getBoolean("correction"),
                SourceProvider.valueOf(
                        resultSet.getString("source_provider")
                ),
                resultSet.getInt("document_count"),
                resultSet.getDouble("search_score"),
                matchedTerms(reportName, condition.titleTerms())
        );
    }

    private List<String> matchedTerms(
            String reportName,
            List<String> titleTerms
    ) {
        String normalizedReportName = reportName.toLowerCase(Locale.ROOT);

        return titleTerms.stream()
                .filter(term -> normalizedReportName.contains(
                        term.toLowerCase(Locale.ROOT)
                ))
                .toList();
    }

    private record SearchQuery(
            String whereClause,
            String scoreExpression,
            MapSqlParameterSource parameters
    ) {
    }
}
