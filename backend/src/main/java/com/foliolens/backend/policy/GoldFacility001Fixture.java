package com.foliolens.backend.policy;

import com.foliolens.backend.answer.AnswerOutcome;
import com.foliolens.backend.calculation.CalculationVerdict;
import com.foliolens.backend.question.plan.toolinput.CalculationOperation;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

// GOLD-FACILITY-001_C_APPROVAL_STEPS_1_TO_7.md의 1~7단계 승인 내용을 옮긴 fixture.
// policyVersion이 "1.0-draft"인 동안은 8단계 최종 패키지(C_APPROVED) 확정 전이므로
// 이 값을 실제 답변 판정에 그대로 사용하지 않는다 — 오케스트레이션·계약 테스트용 fixture로만 쓴다.
public final class GoldFacility001Fixture {

    private GoldFacility001Fixture() {
    }

    public static AnswerPolicy policy() {
        return new AnswerPolicy(
                "1.0-draft",
                "신규시설투자등",
                List.of(
                        new FactPolicy("facility.target", FactNecessity.SUPPORTING),
                        new FactPolicy("facility.amount", FactNecessity.REQUIRED),
                        new FactPolicy("facility.equity_amount", FactNecessity.REQUIRED),
                        new FactPolicy("facility.equity_ratio", FactNecessity.REQUIRED),
                        new FactPolicy("facility.purpose", FactNecessity.REQUIRED),
                        new FactPolicy("facility.start_date", FactNecessity.SUPPORTING),
                        new FactPolicy("facility.end_date", FactNecessity.SUPPORTING),
                        new FactPolicy("facility.decision_date", FactNecessity.SUPPORTING)
                ),
                new CalculationPolicy(
                        "FACILITY_EQUITY_RATIO_CHECK",
                        CalculationOperation.RATIO,
                        "facility.amount",
                        "facility.equity_amount",
                        "facility.equity_ratio",
                        RoundingMode.HALF_UP,
                        2
                ),
                List.of(
                        "대회 제공 공시 원문에 따르면",
                        "공시된 투자금액은 ...입니다.",
                        "공시에 기재된 금액으로 재계산하면 ...입니다.",
                        "공시와 동일한 자릿수로 반올림하면 ...로 일치합니다.",
                        "허용되는 반올림 범위를 넘어 일치하지 않습니다.",
                        "대회 제공 공시 원문에서 해당 항목을 확인할 수 없습니다.",
                        "공시된 종료 예정일은 ...입니다."
                ),
                List.of(
                        "매수·매도 권유",
                        "목표주가 제시",
                        "주가 상승·하락 확정",
                        "수익 보장 또는 성공확률 단정",
                        "투자금액이 크므로 유동성 위기가 발생한다는 단정",
                        "회사가 투자를 충분히 감당할 수 있다는 단정",
                        "투자수익성과 사업 성공 여부 단정",
                        "계획 투자금액을 이미 지출한 금액으로 표현",
                        "종료 예정일을 확정 준공일로 표현",
                        "시설증설 결정만으로 실제 생산능력 확대 완료를 단정",
                        "회사가 비율을 허위로 공시했다는 단정",
                        "추가 확인 없이 회사의 고의나 오류를 확정"
                ),
                new GoldenCase(
                        "GOLD-FACILITY-001",
                        "SK하이닉스가 2024년 4월 발표한 신규시설투자의 투자금액과 목적은 무엇이고, 자기자본 대비 비율은 맞는가?",
                        "SK하이닉스",
                        "20240424800596",
                        Map.of(
                                "facility.target", "청주 M15X 건설",
                                "facility.amount", "5296200000000",
                                "facility.equity_amount", "53503752397611",
                                "facility.equity_ratio", "9.90",
                                "facility.purpose", "선제적인 반도체 수요 대응을 위한 차세대 DRAM 생산능력 확장",
                                "facility.start_date", "2024-04-24",
                                "facility.end_date", "2026-10-30",
                                "facility.decision_date", "2024-04-24"
                        ),
                        "9.898744971458265453112664230",
                        "9.90",
                        CalculationVerdict.MATCH,
                        AnswerOutcome.COMPLETED,
                        "대회 제공 공시 원문에 따르면, SK하이닉스가 2024년 4월 결정한 청주 M15X 건설의 투자금액은 "
                                + "5조 2,962억 원이며, 투자목적은 선제적인 반도체 수요 대응을 위한 차세대 DRAM 생산능력 "
                                + "확장입니다. 공시에 기재된 최근 사업연도 말 연결 자기자본 53조 5,037억 5,239만 7,611원을 "
                                + "기준으로 투자금액 ÷ 자기자본 × 100을 계산하면 약 9.8987%입니다. 이를 공시와 동일하게 "
                                + "소수 둘째 자리로 반올림하면 9.90%이므로 공시된 자기자본 대비 비율과 일치합니다. 다만 "
                                + "공시 원문은 투자금액과 투자기간이 진행 과정 및 경영환경에 따라 변경될 수 있다고 밝히고 "
                                + "있습니다."
                )
        );
    }
}
