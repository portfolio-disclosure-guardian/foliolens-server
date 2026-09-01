package com.foliolens.backend.disclosure.infrastructure.search;

/**
 * 청크 검색 점수가 어떤 요소로 구성됐는지 설명하는 선택 모델.
 * 최종점수만 반환하면 왜 높은 점수를 받았는지 알 수 없습니다.
 * 점수 구성 요소를 분리하면 검색 품질을 분석하고 가중치를 조정할 수 있습니다.
 */
public record SearchScoreBreakdown(
        double reportNameScore, // 공시명과 검색 조건이 얼마나 관련 있는지
        double sectionPathScore, // 청크가 위치한 Section 경로가 검색 힌트와 얼마나 일치하는지
        double bodyScore, // 실제 청크 본문에 검색어가 얼마나 포함됐는지
        double phraseBonus, // 검색어가 떨어져 등장하는 것보다 하나의 구문으로 붙어 등장할 때 주는 추가 점수
        double factHintBonus, // Fact 키에 연결된 원문 레이블이나 동의어가 발견됐을 때 주는 점수
        double correctionPenaltyOrBonus, // 질문 모드에 따라 정정공시의 점수를 조정
        double finalScore // 최종점수
) {

    public SearchScoreBreakdown {
        requireFinite(reportNameScore, "reportNameScore");
        requireFinite(sectionPathScore, "sectionPathScore");
        requireFinite(bodyScore, "bodyScore");
        requireFinite(phraseBonus, "phraseBonus");
        requireFinite(factHintBonus, "factHintBonus");
        requireFinite(
                correctionPenaltyOrBonus,
                "correctionPenaltyOrBonus"
        );
        requireFinite(finalScore, "finalScore");
    }

    public double componentTotal() {
        return reportNameScore
                + sectionPathScore
                + bodyScore
                + phraseBonus
                + factHintBonus
                + correctionPenaltyOrBonus;
    }

    private static void requireFinite(
            double value,
            String fieldName
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    fieldName + "는 유한한 숫자여야 합니다."
            );
        }
    }
}
