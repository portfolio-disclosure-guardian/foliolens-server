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
 * PostgreSQL의 disclosures·companies·disclosure_documents를 조회하는 메타데이터 검색 Repository.
 *
 * 예:
 * 사용자가 다음 조건을 전달
 * 회사: 삼성전자
 * 기간: 2025년
 * 공시 종류: 주요사항보고서
 * 세부 유형: 신규시설투자등
 * 제목 검색어: 시설투자
 * 정정공시: 모두 포함
 * 최대 결과: 20개
 *
 * Repository는 DB에서 다음 작업을 함
 * 1. 삼성전자 공시만 남긴다.
 * 2. 2025년 공시만 남긴다.
 * 3. 주요사항보고서만 남긴다.
 * 4. 시설투자 유형만 남긴다.
 * 5. 보고서명에 시설투자가 들어간 공시를 찾는다.
 * 6. 제목이 더 잘 맞는 순서로 정렬한다.
 * 7. 상위 20개를 반환한다.
 *
 * 공시 목록에서 조사 대상을 고르는 클래스
 */
@Repository
public class JdbcDisclosureMetadataSearchRepository
        implements DisclosureMetadataSearchRepository {

    private static final String CONTEST_PROVIDER = "CONTEST";

    // NamedParameterJdbcTemplate은 Java에서 PostgreSQL에 SQL을 실행하게 해주는 도구
    /*
    * 사용하지 않는 위험한 방식 -> "WHERE company_id = '" + companyId + "'"
    * 대신 이름이 있는 파라미터를 사용  -> WHERE d.company_id IN (:companyIds)
    * */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDisclosureMetadataSearchRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate은 필수입니다."
        );
    }

    /**
     * 실행 흐름:
     * 검색 조건 확인
     *     ↓
     * SQL WHERE 조건 생성
     *     ↓
     * 전체 후보 개수 조회
     *     ↓
     * 후보가 0이면 빈 결과 반환
     *     ↓
     * 후보 목록·점수·문서 수 조회
     *     ↓
     * DisclosureMetadataSearchHit으로 변환
     */
    @Override
    public SearchResult search(
            DisclosureMetadataSearchCondition condition
    ) {
        Objects.requireNonNull(condition, "condition은 필수입니다.");

        // 검색 SQL 준비 -> 검색 조건을 보고 SQL의 WHERE 부분을 만듦
        SearchQuery query = buildSearchQuery(condition);

        // 전체 후보 수 조회 -> 먼저 조건에 맞는 공시가 총 몇 개인지 조회
        Integer candidateCount = jdbcTemplate.queryForObject(
                countSql(query.whereClause()),
                query.parameters(),
                Integer.class
        );

        int totalCount = candidateCount == null ? 0 : candidateCount;
        if (totalCount == 0) {
            return new SearchResult(List.of(), 0);
        }

        // 실제 검색 결과 조회 -> 두 번째 SQL에서 실제 공시 정보를 가져옴
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

    /**
     * 검색 조건을 SQL로 바꾸는 핵심 메서드
     * 기업 조건, 시작일, 종료일, 공시 그룹, 카테고리, 세부유형, 정정여부,
     */
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

        // 기업조건
        if (!condition.companyIds().isEmpty()) {
            predicates.add("d.company_id IN (:companyIds)");
            parameters.addValue("companyIds", condition.companyIds());
        }

        // 시작일
        if (condition.receiptDateFrom() != null) {
            predicates.add("d.receipt_date >= :receiptDateFrom");
            parameters.addValue(
                    "receiptDateFrom",
                    condition.receiptDateFrom()
            );
        }

        // 종료일과 asOf
        LocalDate effectiveDateTo = condition.effectiveReceiptDateTo();
        if (effectiveDateTo != null) {
            predicates.add("d.receipt_date <= :receiptDateTo");
            parameters.addValue("receiptDateTo", effectiveDateTo);
        }

        // 공시그룹
        if (!condition.sourceGroups().isEmpty()) {
            predicates.add("d.source_group IN (:sourceGroups)");
            parameters.addValue(
                    "sourceGroups",
                    condition.sourceGroups().stream()
                            .map(DisclosureSourceGroup::getValue)
                            .toList()
            );
        }

        // 카테고리
        if (!condition.categories().isEmpty()) {
            predicates.add("d.category IN (:categories)");
            parameters.addValue(
                    "categories",
                    condition.categories().stream()
                            .map(Enum::name)
                            .toList()
            );
        }

        // 세부유형
        if (!condition.rawSubtypes().isEmpty()) {
            predicates.add("d.raw_subtype IN (:rawSubtypes)");
            parameters.addValue("rawSubtypes", condition.rawSubtypes());
        }

        boolean hasStructuredTypeFilter =
                !condition.sourceGroups().isEmpty()
                        || !condition.categories().isEmpty()
                        || !condition.rawSubtypes().isEmpty();

        String scoreExpression = addTitleConditions(
                condition.titleTerms(),
                !hasStructuredTypeFilter,
                predicates,
                parameters
        );

        // 정정여부
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

    /**
     * 1. 보고서명에 검색어가 포함돼 있는지 검사
     * 2. 검색어가 얼마나 잘 일치했는지 점수 계산
     */
    private String addTitleConditions(
            List<String> titleTerms,
            boolean filterByTitle,
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
            String normalizedTerm = titleTerms.get(index).toLowerCase(Locale.ROOT);

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

        if (filterByTitle) {
            predicates.add("(" + String.join(" OR ", matches) + ")");
        }

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
