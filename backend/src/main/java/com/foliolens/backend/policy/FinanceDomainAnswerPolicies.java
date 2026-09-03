package com.foliolens.backend.policy;

import com.foliolens.backend.question.plan.toolinput.CalculationOperation;

import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;

/**
 * finance_domain 01~12 문서를 옮긴 미승인 draft 정책 묶음.
 *
 * <p>각 문서의 골든 후보는 아직 C_REVIEW_PENDING이므로 실행 ID로 노출하지 않는다.
 * Fact의 필수도와 계산 입력 binding도 질문별 C 승인 전이므로 여기서는 지원 가능한
 * Fact/계산 목록만 보존한다.</p>
 */
public final class FinanceDomainAnswerPolicies {

    public static final String DRAFT_POLICY_VERSION = "UNVERSIONED-C_REVIEW_PENDING";

    private static final List<String> COMMON_ALLOWED_EXPRESSIONS = List.of(
            "대회 제공 공시 원문에서 확인되는 범위에서",
            "기준시점까지 확인된 상태는 ...입니다.",
            "공시된 값으로 재계산하면 ...입니다.",
            "동일 사건 여부는 추가 확인이 필요합니다."
    );

    private static final List<String> COMMON_FORBIDDEN_EXPRESSIONS = List.of(
            "매수·매도 권유",
            "목표주가 또는 주가 방향 단정",
            "수익·손실·성공확률 단정",
            "예정값을 실제 완료값으로 표현",
            "근거 없는 문서 관계 확정",
            "사건 상태와 API 답변 상태 혼동"
    );

    private FinanceDomainAnswerPolicies() {
    }

    public static List<AnswerPolicy> all() {
        return List.of(
                type01(), type02(), type03(), type04(), type05(), type06(),
                type07(), type08(), type09(), type10(), type11(), type12());
    }

    public static AnswerPolicy type01() {
        return draft(
                "단일판매·공급계약",
                facts("""
                        contract.contract_type.raw
                        contract.contract_type.code
                        contract.description.raw
                        contract.counterparty.raw
                        contract.counterparty.normalized_name
                        contract.counterparty_relation.raw
                        contract.counterparty_relation.code
                        contract.region.raw
                        contract.signed_date.raw
                        contract.signed_date.value
                        contract.start_date.raw
                        contract.start_date.value
                        contract.end_date.raw
                        contract.end_date.value
                        contract.conditions.raw
                        contract.payment_terms.raw
                        contract.notes.raw
                        contract.withheld_reason.raw
                        contract.withheld_until.raw
                        contract.withheld_until.value
                        contract.amount.raw_text
                        contract.amount.numeric_value
                        contract.amount.unit_raw
                        contract.amount.unit_code
                        contract.amount.normalized_value_krw
                        contract.amount.currency_raw
                        contract.amount.currency_code
                        contract.amount.foreign_value
                        contract.amount.disclosed_fx_rate
                        contract.amount.fx_base_date.value
                        contract.amount.vat_text
                        contract.amount.vat_status
                        contract.amount.basis_text
                        contract.amount.basis_code
                        contract.termination_amount.raw_text
                        contract.termination_amount.numeric_value
                        contract.termination_amount.unit_raw
                        contract.termination_amount.unit_code
                        contract.termination_amount.normalized_value_krw
                        contract.termination_amount.currency_raw
                        contract.termination_amount.currency_code
                        contract.termination_amount.foreign_value
                        contract.termination_amount.disclosed_fx_rate
                        contract.termination_amount.fx_base_date.value
                        contract.termination_amount.vat_text
                        contract.termination_amount.vat_status
                        contract.termination_amount.basis_text
                        contract.termination_amount.basis_code
                        contract.reference_sales.raw_text
                        contract.reference_sales.normalized_value_krw
                        contract.reference_sales.period
                        contract.reference_sales.accounting_basis
                        contract.disclosed_sales_ratio.raw
                        contract.disclosed_sales_ratio.value
                        contract.status
                        termination.reason.raw
                        termination.context.raw
                        termination.date.raw
                        termination.date.value
                        termination.original_contract_receipt_no.raw
                        termination.original_contract_receipt_no.resolved
                        termination.performance_amount
                        correction.reason.raw
                        correction.changes[].fact_id
                        correction.changes[].before.raw
                        correction.changes[].after.raw
                        """),
                calculations(
                        calculation("CONTRACT_SALES_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("CONTRACT_DURATION_DAYS", CalculationOperation.DATE_DURATION),
                        calculation("CONTRACT_AMOUNT_CHANGE", CalculationOperation.DIFFERENCE),
                        calculation("CONTRACT_AMOUNT_CHANGE_RATE", CalculationOperation.CHANGE_RATE),
                        calculation("CONTRACT_FX_CHECK", CalculationOperation.PRODUCT)),
                List.of(
                        "계약금액을 확정매출로 표현",
                        "해지금액을 확정손실로 표현",
                        "현재 환율 또는 임의 VAT를 적용",
                        "후보 관계만으로 계약상태 확정"
                ));
    }

    public static AnswerPolicy type02() {
        return draft(
                "신규시설투자",
                facts("""
                        facility.type.raw
                        facility.type.code
                        facility.target.raw
                        facility.purpose.raw
                        facility.location.raw
                        facility.scale.raw
                        facility.amount.raw_text
                        facility.amount.normalized_value_krw
                        facility.equity_amount.raw_text
                        facility.equity_amount.normalized_value_krw
                        facility.equity_basis.raw
                        facility.disclosed_equity_ratio.raw
                        facility.disclosed_equity_ratio.value
                        facility.decision_date.raw
                        facility.decision_date.value
                        facility.start_date.raw
                        facility.start_date.value
                        facility.end_date.raw
                        facility.end_date.value
                        facility.amount.vat_text
                        facility.amount.vat_status
                        facility.amount.basis_text
                        facility.amount.basis_code
                        facility.amount.foreign_value
                        facility.amount.currency_code
                        facility.amount.disclosed_fx_rate
                        facility.amount.fx_base_date.value
                        facility.amount.included_items.raw
                        facility.amount.excluded_items.raw
                        facility.total_project_amount.raw_text
                        facility.total_project_amount.normalized_value_krw
                        facility.company_share_amount.raw_text
                        facility.company_share_amount.normalized_value_krw
                        facility.company_share_rate.raw
                        facility.company_share_rate.value
                        facility.funding_sources.raw
                        facility.start_date_basis.raw
                        facility.end_date_basis.raw
                        facility.amount_change_possible.raw
                        facility.schedule_change_possible.raw
                        facility.permit_status.raw
                        facility.related_disclosures[].receipt_no
                        facility.current_status
                        facility.notes
                        facility.withheld_reason
                        facility.withheld_until
                        correction.changes[].fact_id
                        correction.changes[].before.raw
                        correction.changes[].after.raw
                        """),
                calculations(
                        calculation("FACILITY_EQUITY_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("FACILITY_AMOUNT_CHANGE", CalculationOperation.DIFFERENCE),
                        calculation("FACILITY_AMOUNT_CHANGE_RATE", CalculationOperation.CHANGE_RATE),
                        calculation("FACILITY_DURATION_DAYS", CalculationOperation.DATE_DURATION),
                        calculation("FACILITY_SCHEDULE_DELAY", CalculationOperation.DATE_DURATION),
                        calculation("FACILITY_FX_CHECK", CalculationOperation.PRODUCT),
                        calculation("FACILITY_COMPANY_SHARE_CHECK", CalculationOperation.PRODUCT),
                        calculation("FACILITY_DISCLOSURE_THRESHOLD", CalculationOperation.DIFFERENCE),
                        calculation("FACILITY_CHANGE_THRESHOLD", CalculationOperation.RATIO),
                        calculation("FACILITY_CASH_RATIO", CalculationOperation.RATIO),
                        calculation("FACILITY_OPERATING_CASH_FLOW_RATIO", CalculationOperation.RATIO),
                        calculation("FACILITY_TOTAL_ASSET_RATIO", CalculationOperation.RATIO),
                        calculation("FACILITY_LATEST_EQUITY_RATIO", CalculationOperation.RATIO),
                        calculation("FACILITY_ANNUALIZED_PLAN_AMOUNT", CalculationOperation.RATIO)),
                List.of(
                        "계획 투자금액을 실제 집행액으로 표현",
                        "종료 예정일을 확정 준공일로 표현",
                        "재무규모 대비 비율로 유동성 위기·수익성 단정",
                        "최신 규정을 과거 공시에 소급 적용"
                ));
    }

    public static AnswerPolicy type03() {
        return draft(
                "투자판단 관련 주요경영사항",
                facts("""
                        management_event.title.raw
                        management_event.type.code
                        management_event.subject.raw
                        management_event.summary.raw
                        management_event.parties[].name.raw
                        management_event.parties[].role.code
                        management_event.agreement_stage.raw
                        management_event.agreement_stage.code
                        management_event.legal_binding_text
                        management_event.amount.raw_text
                        management_event.amount.unit_raw
                        management_event.amount.currency_code
                        management_event.amount.normalized_value
                        management_event.amount.basis_text
                        management_event.amount.basis_code
                        management_event.decision_date.raw
                        management_event.decision_date.value
                        management_event.start_date.raw
                        management_event.start_date.value
                        management_event.end_date.raw
                        management_event.end_date.value
                        management_event.conditions.raw
                        management_event.uncertainties.raw
                        management_event.status.code
                        management_event.related_disclosures[]
                        management_event.milestones[].name.raw
                        management_event.milestones[].condition.raw
                        management_event.milestones[].planned_date.value
                        management_event.milestones[].achievement_evidence.raw
                        management_event.milestones[].status.code
                        management_event.milestones[].maximum_amount.raw_text
                        management_event.milestones[].maximum_amount.normalized_value
                        management_event.milestones[].confirmed_amount.raw_text
                        management_event.milestones[].confirmed_amount.normalized_value
                        management_event.milestones[].payment_date.value
                        management_event.support.grant_amount.raw_text
                        management_event.support.grant_amount.normalized_value
                        management_event.support.loan_amount.raw_text
                        management_event.support.loan_amount.normalized_value
                        management_event.support.provider.raw
                        management_event.guarantee.amount.raw_text
                        management_event.guarantee.amount.normalized_value
                        management_event.guarantee.scope.raw
                        management_event.commercial.document_type
                        management_event.commercial.binding_status
                        management_event.commercial.binding_clauses[]
                        management_event.commercial.counterparty
                        management_event.commercial.product_service
                        management_event.commercial.proposed_amount
                        management_event.commercial.proposed_quantity
                        management_event.commercial.conditions[]
                        management_event.commercial.followup_contract_receipt_no
                        management_event.clinical.candidate
                        management_event.clinical.indication
                        management_event.clinical.phase
                        management_event.clinical.event_type
                        management_event.clinical.regulator
                        management_event.clinical.country
                        management_event.clinical.protocol_id
                        management_event.clinical.target_subjects
                        management_event.clinical.enrolled_subjects
                        management_event.clinical.primary_endpoints[]
                        management_event.clinical.result_summary
                        management_event.clinical.safety_summary
                        management_event.clinical.next_step
                        management_event.license.asset
                        management_event.license.licensor
                        management_event.license.licensee
                        management_event.license.territory
                        management_event.license.total_maximum
                        management_event.license.upfront_amount
                        management_event.license.royalty_terms
                        management_event.license.refund_obligation
                        management_event.license.milestones[].invoice_date
                        management_event.license.milestones[].expected_payment_date
                        management_event.license.milestones[].receipt_date
                        management_event.license.milestones[].recognized_revenue
                        management_event.governance.agreement_type
                        management_event.governance.option_type
                        management_event.governance.right_holder
                        management_event.governance.obligors[]
                        management_event.governance.subject_share_count
                        management_event.governance.price
                        management_event.governance.exercise_start_date
                        management_event.governance.exercise_end_date
                        management_event.governance.exercise_status
                        management_event.governance.potential_controller
                        """),
                calculations(
                        calculation("MILESTONE_CONFIRMED_SHARE", CalculationOperation.RATIO),
                        calculation("SUPPORT_GRANT_SHARE", CalculationOperation.RATIO),
                        calculation("COMPANY_NET_BURDEN_REFERENCE", CalculationOperation.DIFFERENCE),
                        calculation("MANAGEMENT_EVENT_DURATION_DAYS", CalculationOperation.DATE_DURATION),
                        calculation("MANAGEMENT_EVENT_AMOUNT_CHANGE", CalculationOperation.DIFFERENCE),
                        calculation("CLINICAL_ENROLLMENT_RATE", CalculationOperation.RATIO),
                        calculation("GOVERNANCE_OPTION_SHARE_RATIO", CalculationOperation.RATIO)),
                List.of(
                        "MOU·LOI를 확정 본계약으로 표현",
                        "최대 조건부대가를 현재 매출·확정수익으로 표현",
                        "대출·보증을 상환의무 없는 보조금으로 표현",
                        "임상 등록률을 임상 성공률로 표현",
                        "수령 예정·청구·수익 인식을 실제 입금으로 표현"
                ));
    }

    public static AnswerPolicy type04() {
        return draft(
                "주식 등의 대량보유상황보고서",
                facts("""
                        holding.report_type.raw
                        holding.report_type.code
                        holding.report_date.raw
                        holding.report_date.value
                        holding.obligation_date.raw
                        holding.obligation_date.value
                        holding.reporter.name.raw
                        holding.reporter.relationship_to_issuer.raw
                        holding.report_reason.raw
                        holding.purpose.raw
                        holding.purpose.code
                        holding.previous.total_security_count.raw
                        holding.previous.total_security_count.value
                        holding.current.total_security_count.raw
                        holding.current.total_security_count.value
                        holding.previous.total_security_ratio.raw
                        holding.previous.total_security_ratio.value
                        holding.current.total_security_ratio.raw
                        holding.current.total_security_ratio.value
                        holding.current.voting_share_count.raw
                        holding.current.voting_share_count.value
                        holding.current.voting_share_ratio.raw
                        holding.current.voting_share_ratio.value
                        holding.members[].name.raw
                        holding.members[].member_type.code
                        holding.members[].relationship_to_reporter.raw
                        holding.members[].relationship_to_issuer.raw
                        holding.members[].voting_share_count
                        holding.members[].potential_security_count
                        holding.members[].total_security_count
                        holding.members[].total_security_ratio
                        holding.members[].account_type.raw
                        holding.members[].proprietary_account_count
                        holding.members[].customer_account_count
                        holding.securities[].holder_name.raw
                        holding.securities[].security_type.raw
                        holding.securities[].security_type.code
                        holding.securities[].count
                        holding.securities[].ratio
                        holding.securities[].ownership_form.raw
                        holding.transactions[].holder_name.raw
                        holding.transactions[].change_date.raw
                        holding.transactions[].change_date.value
                        holding.transactions[].method.raw
                        holding.transactions[].method.code
                        holding.transactions[].security_type.code
                        holding.transactions[].before_count
                        holding.transactions[].change_count
                        holding.transactions[].after_count
                        holding.transactions[].unit_price.raw
                        holding.transactions[].unit_price.value
                        holding.transactions[].note.raw
                        holding.contracts[].holder_name.raw
                        holding.contracts[].contract_type.raw
                        holding.contracts[].contract_type.code
                        holding.contracts[].share_count
                        holding.contracts[].share_ratio
                        holding.contracts[].counterparty.raw
                        holding.contracts[].start_date
                        holding.contracts[].end_date
                        holding.contracts[].loan_amount
                        holding.contracts[].interest_rate
                        holding.contracts[].maintenance_ratio
                        holding.funding[].own_funds
                        holding.funding[].borrowed_funds
                        holding.funding[].other_funds
                        holding.funding[].source_text
                        holding.form_version
                        holding.formula_basis_text.raw
                        """),
                calculations(
                        calculation("TOTAL_SECURITY_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("VOTING_SHARE_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("HOLDING_COUNT_CHANGE", CalculationOperation.DIFFERENCE),
                        calculation("HOLDING_RATIO_CHANGE_PP", CalculationOperation.DIFFERENCE),
                        calculation("ESTIMATED_TRANSACTION_AMOUNT", CalculationOperation.PRODUCT),
                        calculation("PLEDGED_HOLDING_RATIO", CalculationOperation.RATIO),
                        calculation("HOLDING_RATIO_CHANGE_RATE", CalculationOperation.CHANGE_RATE),
                        calculation("MEMBER_GROUP_SHARE", CalculationOperation.RATIO),
                        calculation("CONTRACTED_HOLDING_RATIO", CalculationOperation.RATIO),
                        calculation("FUNDING_SOURCE_SHARE", CalculationOperation.RATIO),
                        calculation("POTENTIAL_RIGHTS_RATIO_GAP_PP", CalculationOperation.DIFFERENCE),
                        calculation("FIVE_PERCENT_THRESHOLD_CHECK", CalculationOperation.DIFFERENCE)),
                List.of(
                        "대표보고자의 단독보유량으로 보고집단 합계를 표현",
                        "특별관계자 편입·제외를 실제 매매로 단정",
                        "잠재권리를 현재 의결권 주식으로 표현",
                        "담보계약을 즉시 반대매매로 표현",
                        "5% 임계치 계산으로 법 위반·보고 적법성 단정"
                ));
    }

    public static AnswerPolicy type05() {
        return draft(
                "정기공시 재무·사업정보",
                facts("""
                        periodic.report_type.raw
                        periodic.report_type.code
                        periodic.base_year
                        periodic.base_month
                        periodic.period_start.raw
                        periodic.period_start.value
                        periodic.period_end.raw
                        periodic.period_end.value
                        periodic.period_kind.code
                        periodic.accounting_basis.raw
                        periodic.accounting_basis.code
                        periodic.currency.raw
                        periodic.currency.code
                        periodic.unit.raw
                        periodic.unit.code
                        periodic.audit_opinion.raw
                        financial.revenue
                        financial.operating_profit
                        financial.net_income
                        financial.net_income_parent
                        financial.cost_of_sales
                        financial.gross_profit
                        financial.total_assets
                        financial.total_liabilities
                        financial.total_equity
                        financial.current_assets
                        financial.current_liabilities
                        financial.cash_and_equivalents
                        financial.operating_cash_flow
                        financial.investing_cash_flow
                        financial.financing_cash_flow
                        financial.capex_cash_outflow
                        financial.borrowings_current
                        financial.borrowings_noncurrent
                        financial.interest_expense
                        financial.inventories
                        financial.trade_receivables
                        financial.eps_basic
                        business.rnd_expense
                        business.order_backlog
                        business.segment_revenue[]
                        business.capex_plan
                        audit.opinion
                        audit.key_audit_matters[]
                        share.outstanding_count
                        share.treasury_count
                        dividend.cash_dividend_total
                        dividend.dps[]
                        correction.scope.raw
                        correction.scope.code
                        correction.reason.raw
                        correction.original_submission_date
                        correction.changes[].fact_id
                        correction.changes[].before.raw
                        correction.changes[].after.raw
                        """),
                calculations(
                        calculation("REVENUE_CHANGE_RATE", CalculationOperation.CHANGE_RATE),
                        calculation("OPERATING_MARGIN", CalculationOperation.RATIO),
                        calculation("NET_MARGIN", CalculationOperation.RATIO),
                        calculation("DEBT_TO_EQUITY", CalculationOperation.RATIO),
                        calculation("CURRENT_RATIO", CalculationOperation.RATIO),
                        calculation("OCF_MARGIN", CalculationOperation.RATIO),
                        calculation("RND_TO_SALES", CalculationOperation.RATIO),
                        calculation("GROSS_MARGIN", CalculationOperation.RATIO),
                        calculation("NET_DEBT_REFERENCE", CalculationOperation.DIFFERENCE),
                        calculation("TREASURY_SHARE_RATIO", CalculationOperation.RATIO),
                        calculation("DIVIDEND_PAYOUT_RATIO", CalculationOperation.RATIO)),
                List.of(
                        "기간종류·연결기준이 다른 재무값을 직접 비교",
                        "공란·대시를 숫자 0으로 변환",
                        "감사의견·핵심감사사항으로 부도·분식 확정",
                        "과거 성장률·배당성향을 미래 성장·배당 보장으로 표현"
                ));
    }

    public static AnswerPolicy type06() {
        return draft(
                "자기주식 취득·처분·신탁",
                facts("""
                        treasury_share.event_title.raw
                        treasury_share.event_type.code
                        treasury_share.event_status.code
                        treasury_share.decision_date.raw
                        treasury_share.decision_date.value
                        treasury_share.purpose.raw
                        treasury_share.purpose.code
                        treasury_share.method.raw
                        treasury_share.method.code
                        treasury_share.period.start.raw
                        treasury_share.period.start.value
                        treasury_share.period.end.raw
                        treasury_share.period.end.value
                        treasury_share.brokers[].name.raw
                        treasury_share.trust.counterparty.raw
                        treasury_share.trust.contract_amount.raw_text
                        treasury_share.trust.contract_amount.normalized_value_krw
                        treasury_share.trust.start_date
                        treasury_share.trust.end_date
                        treasury_share.trust.termination_reason.raw
                        treasury_share.result_report_date
                        treasury_share.cancellation_reason
                        treasury_share.trust.before_owned_count
                        treasury_share.trust.after_owned_count
                        treasury_share.trust.returned_share_count
                        treasury_share.trust.cash_return_amount
                        treasury_share.classes[].stock_class.raw
                        treasury_share.classes[].stock_class.code
                        treasury_share.classes[].planned_share_count
                        treasury_share.classes[].planned_unit_price.raw_text
                        treasury_share.classes[].planned_unit_price.normalized_value_krw
                        treasury_share.classes[].planned_amount.raw_text
                        treasury_share.classes[].planned_amount.normalized_value_krw
                        treasury_share.classes[].actual_share_count
                        treasury_share.classes[].actual_amount.raw_text
                        treasury_share.classes[].actual_amount.normalized_value_krw
                        treasury_share.classes[].before_owned_count
                        treasury_share.classes[].after_owned_count
                        treasury_share.classes[].total_issued_share_count
                        treasury_share.allottees[].name.raw
                        treasury_share.allottees[].relationship.raw
                        treasury_share.allottees[].allocated_count
                        treasury_share.allottees[].selection_reason.raw
                        treasury_share.allottees[].price_per_share
                        treasury_share.allottees[].amount
                        treasury_share.allottees[].lockup_period
                        """),
                calculations(
                        calculation("TREASURY_PLANNED_AVERAGE_PRICE", CalculationOperation.RATIO),
                        calculation("TREASURY_ACTUAL_AVERAGE_PRICE", CalculationOperation.RATIO),
                        calculation("TREASURY_EXECUTION_RATE", CalculationOperation.RATIO),
                        calculation("TREASURY_HOLDING_RATIO", CalculationOperation.RATIO),
                        calculation("TREASURY_EXTERNAL_FLOAT_CHANGE", CalculationOperation.DIFFERENCE),
                        calculation("TREASURY_AMOUNT_VARIANCE", CalculationOperation.DIFFERENCE),
                        calculation("TREASURY_SHARE_VARIANCE", CalculationOperation.DIFFERENCE),
                        calculation("TREASURY_TRUST_UTILIZATION", CalculationOperation.RATIO),
                        calculation("TREASURY_RETIREMENT_RATIO", CalculationOperation.RATIO)),
                List.of(
                        "계획수량·예정금액을 실제 집행 결과로 표현",
                        "자기주식 취득을 즉시 소각으로 표현",
                        "기존 자기주식 처분을 신주발행 희석으로 표현",
                        "신탁계약금액을 실제 취득금액으로 표현",
                        "기계적 주식수 변화로 주가 상승·하락 단정"
                ));
    }

    public static AnswerPolicy type07() {
        return draft(
                "자금조달·자본변동",
                facts("""
                        financing.event_type.raw
                        financing.event_type.code
                        financing.decision_date
                        financing.issue_amount.raw
                        financing.issue_amount.normalized
                        financing.issue_method.raw
                        financing.issue_method.code
                        financing.use_of_proceeds[].purpose
                        financing.use_of_proceeds[].amount
                        financing.equity.stock_class.raw
                        financing.equity.stock_class.code
                        financing.equity.new_share_count
                        financing.equity.par_value
                        financing.equity.pre_outstanding_share_count
                        financing.equity.issue_price.raw
                        financing.equity.issue_price.normalized
                        financing.equity.reference_price.raw
                        financing.equity.reference_price.normalized
                        financing.equity.disclosed_discount_rate
                        financing.equity.issue_price_method
                        financing.equity.allocation_method.raw
                        financing.equity.allocation_method.code
                        financing.equity.payment_date
                        financing.equity.listing_date
                        financing.equity.lockup_period
                        financing.equity.actual_issued_share_count
                        financing.equity.actual_paid_amount
                        financing.allottees[].name
                        financing.allottees[].relationship_to_issuer
                        financing.allottees[].selection_reason
                        financing.allottees[].allocated_share_count
                        financing.allottees[].allocated_amount
                        financing.allottees[].lockup_period
                        financing.allottees[].recent_transaction_text
                        financing.bonus.new_share_count
                        financing.bonus.pre_share_count
                        financing.bonus.record_date
                        financing.bonus.shares_per_old_share
                        financing.bonus.reserve_source
                        financing.bonus.reserve_amount
                        financing.bonus.excluded_treasury_share_count
                        financing.bonus.fractional_share_policy
                        financing.reduction.reduced_share_count
                        financing.reduction.capital_before
                        financing.reduction.capital_after
                        financing.reduction.shares_before
                        financing.reduction.shares_after
                        financing.reduction.reported_reduction_rate
                        financing.reduction.method
                        financing.reduction.reason
                        financing.reduction.paid_or_free
                        financing.reduction.payment_per_share
                        financing.reduction.record_date
                        financing.reduction.effective_date
                        financing.reduction.treasury_only
                        financing.bond.series
                        financing.bond.type.raw
                        financing.bond.type.code
                        financing.bond.face_amount
                        financing.bond.actual_issued_amount
                        financing.bond.outstanding_amount
                        financing.bond.coupon_rate
                        financing.bond.yield_to_maturity
                        financing.bond.issue_date
                        financing.bond.maturity_date
                        financing.bond.subscription_date
                        financing.bond.payment_date
                        financing.bond.repayment_method
                        financing.bond.issue_region
                        financing.bond.issue_market
                        financing.bond.manager
                        financing.bond.guarantee_status
                        financing.conversion.price
                        financing.conversion.ratio
                        financing.conversion.stock_class
                        financing.conversion.start_date
                        financing.conversion.end_date
                        financing.conversion.potential_share_count
                        financing.conversion.reported_potential_share_ratio
                        financing.conversion.reset_terms
                        financing.conversion.floor_price
                        financing.conversion.upward_reset_terms
                        financing.warrant.exercise_price
                        financing.warrant.period_start
                        financing.warrant.period_end
                        financing.warrant.potential_share_count
                        financing.warrant.is_detachable
                        financing.exchange.price
                        financing.exchange.target_type.raw
                        financing.exchange.target_type.code
                        financing.exchange.target_company
                        financing.exchange.target_share_count
                        financing.exchange.uses_issuer_treasury_shares
                        financing.exchange.period_start
                        financing.exchange.period_end
                        financing.options[].option_type.raw
                        financing.options[].option_type.code
                        financing.options[].holder
                        financing.options[].counterparty
                        financing.options[].start_date
                        financing.options[].end_date
                        financing.options[].exercisable_amount
                        financing.options[].exercisable_share_count
                        financing.options[].exercise_price
                        financing.options[].trigger_condition
                        financing.options[].nominee_designation_possible
                        financing.coco.capital_tier
                        financing.coco.is_perpetual
                        financing.coco.first_call_date
                        financing.coco.supervisory_approval_required
                        financing.coco.coupon_discretionary
                        financing.coco.coupon_non_cumulative
                        financing.coco.trigger_condition
                        financing.coco.loss_absorption_method.raw
                        financing.coco.loss_absorption_method.code
                        financing.coco.write_down_scope
                        financing.coco.write_down_permanent
                        financing.coco.conversion_share_count
                        financing.coco.capital_purpose
                        correction.date
                        correction.original_disclosure_date
                        correction.reason
                        correction.scope[]
                        correction.changes[].fact_id
                        correction.changes[].before.raw
                        correction.changes[].after.raw
                        """),
                calculations(
                        calculation("EQUITY_DILUTION_MAX", CalculationOperation.SHARE_DILUTION),
                        calculation("ISSUANCE_SIZE_TO_PRE_SHARES", CalculationOperation.RATIO),
                        calculation("ISSUE_DISCOUNT_CHECK", CalculationOperation.CHANGE_RATE),
                        calculation("ISSUE_AMOUNT_CHECK", CalculationOperation.PRODUCT),
                        calculation("POTENTIAL_DILUTION_MAX", CalculationOperation.SHARE_DILUTION),
                        calculation("REFIXING_MAX_SHARES", CalculationOperation.RATIO),
                        calculation("BONUS_SHARE_MULTIPLIER", CalculationOperation.RATIO),
                        calculation("CAPITAL_REDUCTION_RATE_CHECK", CalculationOperation.RATIO),
                        calculation("USE_OF_PROCEEDS_SUM_CHECK", CalculationOperation.SUM),
                        calculation("BOND_SIMPLE_CASH_INTEREST", CalculationOperation.PRODUCT)),
                List.of(
                        "예정 발행액을 실제 납입액으로 표현",
                        "잠재희석을 실제 희석 확정값이나 주가 하락률로 표현",
                        "무상증자로 기업가치가 증가한다고 표현",
                        "EB 자기주식 교부에 일반 신주 희석식을 적용",
                        "CoCo 최초 콜일을 확정 만기로 표현"
                ));
    }

    public static AnswerPolicy type08() {
        return draft(
                "합병·분할·주식교환",
                facts("""
                        reorganization.event_type.raw
                        reorganization.event_type.code
                        reorganization.action.raw
                        reorganization.action.code
                        reorganization.method.raw
                        reorganization.method.code
                        reorganization.purpose
                        reorganization.parties[].name
                        reorganization.parties[].role.raw
                        reorganization.parties[].role.code
                        reorganization.parties[].corporate_number
                        reorganization.parties[].listed_status
                        reorganization.parties[].market
                        reorganization.parties[].major_business
                        reorganization.parties[].total_assets
                        reorganization.parties[].total_liabilities
                        reorganization.parties[].total_equity
                        reorganization.parties[].sales
                        reorganization.parties[].operating_income
                        reorganization.parties[].net_income
                        reorganization.parties[].audit_opinion
                        reorganization.ratios[].from_party
                        reorganization.ratios[].to_party
                        reorganization.ratios[].raw
                        reorganization.ratios[].normalized
                        reorganization.valuation[].party
                        reorganization.valuation[].method
                        reorganization.valuation[].value_per_share
                        reorganization.valuation[].reference_date
                        reorganization.valuation[].market_reference_value
                        reorganization.valuation[].asset_value
                        reorganization.valuation[].earnings_value
                        reorganization.valuation[].intrinsic_value
                        reorganization.valuation[].final_transaction_value
                        reorganization.valuation[].external_appraiser
                        reorganization.valuation[].appraisal_opinion
                        reorganization.consideration.type.raw
                        reorganization.consideration.type.code
                        reorganization.consideration.cash_amount
                        reorganization.consideration.cash_per_share
                        reorganization.consideration.total_cash_amount
                        reorganization.new_shares[].stock_class
                        reorganization.new_shares[].count
                        reorganization.new_shares[].listing_date
                        reorganization.treasury_shares_used
                        reorganization.fractional_share_policy
                        reorganization.division.transferred_business
                        reorganization.division.remaining_business
                        reorganization.division.asset_amount
                        reorganization.division.liability_amount
                        reorganization.division.equity_amount
                        reorganization.division.ratio.raw
                        reorganization.division.ratio.normalized
                        reorganization.division.new_company_name
                        reorganization.division.listing_plan
                        reorganization.division.pre_total_assets
                        reorganization.division.pre_total_liabilities
                        reorganization.division.pre_net_assets
                        reorganization.division.surviving_ratio
                        reorganization.division.new_company_ratio
                        reorganization.division.ratio_formula_text
                        reorganization.division_merger.division_ratio
                        reorganization.division_merger.merger_ratio
                        reorganization.division_merger.final_allocation_ratio
                        reorganization.division_merger.formula_text
                        reorganization.decision_date
                        reorganization.shareholder_meeting_date
                        reorganization.creditor_protection_start
                        reorganization.creditor_protection_end
                        reorganization.dissent_notice_start
                        reorganization.dissent_notice_end
                        reorganization.appraisal_right_start
                        reorganization.appraisal_right_end
                        reorganization.appraisal_price
                        reorganization.effective_date
                        reorganization.registration_date
                        reorganization.conditions_precedent
                        reorganization.status.raw
                        reorganization.status.code
                        reorganization.related_disclosures[].receipt_no
                        reorganization.appraisal_rights[].party_name
                        reorganization.appraisal_rights[].is_available
                        reorganization.appraisal_rights[].planned_price
                        reorganization.appraisal_rights[].actual_claimed_shares
                        reorganization.appraisal_rights[].actual_claimed_amount
                        reorganization.appraisal_rights[].termination_cap
                        reorganization.conditions[].condition_type
                        reorganization.conditions[].condition_text
                        reorganization.conditions[].satisfaction_status
                        reorganization.termination.reason
                        """),
                calculations(
                        calculation("MERGER_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("NEW_SHARE_COUNT_CHECK", CalculationOperation.PRODUCT),
                        calculation("EXCHANGE_OWNERSHIP_CHECK", CalculationOperation.RATIO),
                        calculation("DIVISION_NET_ASSET_CHECK", CalculationOperation.DIFFERENCE),
                        calculation("SCHEDULE_CHANGE_DAYS", CalculationOperation.DATE_DURATION),
                        calculation("APPRAISAL_MAX_CASH_OUT", CalculationOperation.PRODUCT),
                        calculation("EXCHANGE_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("POST_TRANSACTION_SHARES", CalculationOperation.SUM),
                        calculation("EXISTING_HOLDER_DILUTION", CalculationOperation.SHARE_DILUTION),
                        calculation("DIVISION_RATIO_SUM_CHECK", CalculationOperation.SUM),
                        calculation("DIVISION_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("DIVISION_MERGER_ALLOCATION", CalculationOperation.PRODUCT),
                        calculation("TRANSFERRED_ASSET_RATIO", CalculationOperation.RATIO),
                        calculation("TRANSFERRED_LIABILITY_RATIO", CalculationOperation.RATIO),
                        calculation("TRANSFERRED_NET_ASSET_RATIO", CalculationOperation.RATIO),
                        calculation("APPRAISAL_CAP_USAGE", CalculationOperation.RATIO),
                        calculation("VALUATION_PREMIUM_DISCOUNT", CalculationOperation.CHANGE_RATE),
                        calculation("RATIO_CHANGE_RATE", CalculationOperation.CHANGE_RATE)),
                List.of(
                        "합병비율의 유리·불리 또는 경제적 공정성 단정",
                        "현금대가 거래의 신주 희석률 계산",
                        "철회된 과거 계획을 현재 유효한 계획으로 표현"
                ));
    }

    public static AnswerPolicy type09() {
        return draft(
                "자산·영업·지분거래",
                facts("""
                        transaction.event_type.raw
                        transaction.event_type.code
                        transaction.action.raw
                        transaction.action.code
                        transaction.status.raw
                        transaction.status.code
                        transaction.target_name
                        transaction.target_description
                        transaction.counterparty.name
                        transaction.counterparty.relationship
                        transaction.purpose
                        transaction.decision_date
                        transaction.contract_date
                        transaction.closing_date
                        transaction.amount.raw
                        transaction.amount.normalized
                        transaction.amount.basis_text
                        transaction.amount.vat_status
                        transaction.amount.foreign_value
                        transaction.amount.currency
                        transaction.amount.disclosed_fx_rate
                        transaction.amount.fx_base_date
                        transaction.asset.asset_type.raw
                        transaction.asset.asset_type.code
                        transaction.asset.location
                        transaction.asset.book_value
                        transaction.asset.company_total_assets
                        transaction.asset.reported_asset_ratio
                        transaction.asset.asset_name
                        transaction.asset.land_area
                        transaction.asset.building_area
                        transaction.asset.registration_date
                        transaction.asset.use_after_acquisition
                        transaction.asset.use_of_proceeds
                        transaction.business.scope
                        transaction.business.assets
                        transaction.business.liabilities
                        transaction.business.sales
                        transaction.business.company_assets
                        transaction.business.company_liabilities
                        transaction.business.company_sales
                        transaction.business.employees_transferred
                        transaction.business.contracts_transferred
                        transaction.business.name
                        transaction.business.details
                        transaction.business.is_entire_business
                        transaction.business.included_assets
                        transaction.business.included_liabilities
                        transaction.business.included_contracts
                        transaction.business.included_permits
                        transaction.business.included_employees
                        transaction.business.included_ip
                        transaction.business.excluded_items
                        transaction.business.financial_base_date
                        transaction.business.accounting_scope
                        transaction.equity.target_company
                        transaction.equity.security_type.raw
                        transaction.equity.security_type.code
                        transaction.equity.share_count
                        transaction.equity.pre_share_count
                        transaction.equity.post_share_count
                        transaction.equity.pre_ratio
                        transaction.equity.post_ratio
                        transaction.equity.target_total_shares
                        transaction.equity.management_control
                        transaction.equity.lockup_or_restriction
                        transaction.equity.issuer_country
                        transaction.equity.issuer_business
                        transaction.equity.disclosed_price_per_share
                        transaction.equity.control_before
                        transaction.equity.control_after
                        transaction.equity.control_change_text
                        transaction.equity.financials[].fiscal_year
                        transaction.equity.financials[].accounting_scope
                        transaction.equity.financials[].assets
                        transaction.equity.financials[].liabilities
                        transaction.equity.financials[].equity
                        transaction.equity.financials[].revenue
                        transaction.equity.financials[].net_income
                        transaction.equity.financials[].audit_opinion
                        transaction.equity.financials[].auditor
                        transaction.payments[].stage
                        transaction.payments[].amount
                        transaction.payments[].date
                        transaction.payments[].condition
                        transaction.payments[].currency
                        transaction.payments[].scheduled_date
                        transaction.payments[].actual_date
                        transaction.payments[].status
                        transaction.payments[].method
                        transaction.payments[].funding_source
                        transaction.payments[].fx_rate
                        transaction.payments[].fx_base_date
                        transaction.earnouts[].metric
                        transaction.earnouts[].condition
                        transaction.earnouts[].max_amount
                        transaction.reference.total_assets
                        transaction.reference.equity
                        transaction.reference.sales
                        transaction.reference.liabilities
                        transaction.reported.total_asset_ratio
                        transaction.reported.equity_ratio
                        transaction.counterparty.type
                        transaction.counterparty.country
                        transaction.counterparty.business
                        transaction.counterparty.address
                        transaction.counterparty.is_related_party
                        transaction.valuation.performed
                        transaction.valuation.reason
                        transaction.valuation.appraiser
                        transaction.valuation.base_date
                        transaction.valuation.method
                        transaction.valuation.range_min
                        transaction.valuation.range_max
                        transaction.valuation.opinion
                        transaction.valuation.assumptions
                        transaction.conditions[].condition_type
                        transaction.conditions[].description
                        transaction.conditions[].deadline
                        transaction.conditions[].status
                        transaction.options[].option_type
                        transaction.options[].holder
                        transaction.options[].counterparty
                        transaction.options[].start_date
                        transaction.options[].end_date
                        transaction.options[].share_count
                        transaction.options[].exercise_price
                        transaction.options[].trigger_condition
                        transaction.linked_transactions[].receipt_no
                        transaction.linked_transactions[].dependency
                        """),
                calculations(
                        calculation("ASSET_TOTAL_ASSET_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("ASSET_EQUITY_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("BUSINESS_ASSET_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("BUSINESS_SALES_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("BUSINESS_LIABILITY_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("AVERAGE_PRICE_PER_SHARE", CalculationOperation.RATIO),
                        calculation("POST_OWNERSHIP_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("OWNERSHIP_CHANGE_PP", CalculationOperation.DIFFERENCE),
                        calculation("IMPLIED_100_PERCENT_EQUITY_VALUE", CalculationOperation.RATIO),
                        calculation("PAYMENT_SUM_CHECK", CalculationOperation.SUM),
                        calculation("FX_AMOUNT_CHECK", CalculationOperation.PRODUCT),
                        calculation("PAYMENT_STAGE_SHARE", CalculationOperation.RATIO),
                        calculation("EARNOUT_MAX_SHARE", CalculationOperation.RATIO),
                        calculation("TRANSACTION_AMOUNT_CHANGE_RATE", CalculationOperation.CHANGE_RATE),
                        calculation("CLOSING_DATE_DELAY_DAYS", CalculationOperation.DATE_DURATION),
                        calculation("SIMPLE_DISPOSAL_SPREAD", CalculationOperation.DIFFERENCE),
                        calculation("ESTIMATED_DISPOSAL_GAIN", CalculationOperation.DIFFERENCE),
                        calculation("NET_PROCEEDS", CalculationOperation.DIFFERENCE)),
                List.of(
                        "거래금액을 회계상 이익으로 표현",
                        "최대 조건부대가를 확정 지급액으로 표현",
                        "단순 환산가치를 적정 기업가치로 표현",
                        "지분율만으로 경영권 취득·상실 단정"
                ));
    }

    public static AnswerPolicy type10() {
        return draft(
                "계속기업·법률위험",
                facts("""
                        risk_event.event_type.raw
                        risk_event.event_type.code
                        risk_event.event_action.raw
                        risk_event.event_action.code
                        risk_event.proceeding_status.raw
                        risk_event.proceeding_status.code
                        risk_event.result.raw
                        risk_event.result.code
                        risk_event.occurrence_date
                        risk_event.receipt_or_notice_date
                        risk_event.authority
                        risk_event.case_number
                        risk_event.counterparty
                        risk_event.summary
                        risk_event.company_response
                        risk_event.effect_currently_stayed
                        risk_event.related_disclosures[].receipt_no
                        risk_event.litigation.role.raw
                        risk_event.litigation.role.code
                        risk_event.litigation.claim_type
                        risk_event.litigation.claim_text
                        risk_event.litigation.claim_amount.raw
                        risk_event.litigation.claim_amount.normalized
                        risk_event.litigation.reference_equity
                        risk_event.litigation.reported_equity_ratio
                        risk_event.litigation.court_level
                        risk_event.litigation.judgment_date
                        risk_event.litigation.appeal_deadline
                        risk_event.litigation.appeal_action
                        risk_event.litigation.procedure_type.raw
                        risk_event.litigation.procedure_type.code
                        risk_event.litigation.parties[].name
                        risk_event.litigation.parties[].role.raw
                        risk_event.litigation.parties[].role.code
                        risk_event.litigation.relief_types[]
                        risk_event.litigation.interest_rate
                        risk_event.litigation.stage.raw
                        risk_event.litigation.stage.code
                        risk_event.litigation.outcome.raw
                        risk_event.litigation.outcome.code
                        risk_event.litigation.finality_status
                        risk_event.suspension.business_scope
                        risk_event.suspension.reason
                        risk_event.suspension.start_date
                        risk_event.suspension.end_date
                        risk_event.suspension.duration_text
                        risk_event.suspension.sales_amount
                        risk_event.suspension.company_sales
                        risk_event.suspension.reported_sales_ratio
                        risk_event.suspension.mitigation_text
                        risk_event.suspension.resume_date
                        risk_event.suspension.scope.raw
                        risk_event.suspension.scope.code
                        risk_event.suspension.operational_effect_status
                        risk_event.suspension.stay_application_status
                        risk_event.suspension.stay_decision_status
                        risk_event.suspension.authority_reason_text
                        risk_event.suspension.company_position_text
                        risk_event.insolvency.default_amount
                        risk_event.insolvency.default_date
                        risk_event.insolvency.payment_instrument
                        risk_event.insolvency.bank_transaction_status
                        risk_event.rehabilitation.filing_date
                        risk_event.rehabilitation.application_date
                        risk_event.rehabilitation.court
                        risk_event.rehabilitation.commencement_date
                        risk_event.rehabilitation.current_status
                        risk_event.rehabilitation.plan_submission_date
                        risk_event.rehabilitation.plan_approval_date
                        risk_event.rehabilitation.termination_date
                        risk_event.audit.going_concern_text
                        risk_event.audit.going_concern_is_material_uncertainty
                        risk_event.audit.going_concern_basis_period
                        """),
                calculations(
                        calculation("CLAIM_EQUITY_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("SUSPENDED_SALES_RATIO_CHECK", CalculationOperation.RATIO),
                        calculation("SUSPENSION_DURATION_DAYS", CalculationOperation.DATE_DURATION),
                        calculation("EVENT_AMOUNT_CHANGE_RATE", CalculationOperation.CHANGE_RATE),
                        calculation("RISK_AMOUNT_CASH_RATIO", CalculationOperation.RATIO)),
                List.of(
                        "소송 제기만으로 패소·손실 확정",
                        "청구금액을 충당부채 또는 실제 지급액으로 표현",
                        "집행정지 신청을 인용 결정으로 표현",
                        "공시 비율을 미래 매출 감소율로 표현"
                ));
    }

    public static AnswerPolicy type11() {
        return draft(
                "해외증권시장 상장·상장폐지",
                facts("""
                        overseas_listing.event_action.raw
                        overseas_listing.event_action.code
                        overseas_listing.listing_mode.raw
                        overseas_listing.listing_mode.code
                        overseas_listing.status.raw
                        overseas_listing.status.code
                        overseas_listing.decision_date
                        overseas_listing.planned_date
                        overseas_listing.actual_date
                        overseas_listing.completion_confirmation_date
                        overseas_listing.purpose
                        overseas_listing.depositary_name
                        overseas_listing.alternative_market
                        overseas_listing.markets[].market_id
                        overseas_listing.markets[].market_name.raw
                        overseas_listing.markets[].market_code
                        overseas_listing.markets[].country.raw
                        overseas_listing.markets[].country.code
                        overseas_listing.markets[].segment
                        overseas_listing.markets[].currency.raw
                        overseas_listing.markets[].currency.code
                        overseas_listing.markets[].affected_securities[].security_name.raw
                        overseas_listing.markets[].affected_securities[].security_type.code
                        overseas_listing.markets[].affected_securities[].stock_class.raw
                        overseas_listing.markets[].affected_securities[].dr_count
                        overseas_listing.markets[].affected_securities[].underlying_share_count
                        overseas_listing.securities[].security_id
                        overseas_listing.securities[].security_name.raw
                        overseas_listing.securities[].security_type.code
                        overseas_listing.securities[].stock_class.raw
                        overseas_listing.securities[].stock_class.code
                        overseas_listing.securities[].listed_quantity.raw
                        overseas_listing.securities[].listed_quantity.normalized
                        overseas_listing.securities[].dr_ratio.raw
                        overseas_listing.securities[].dr_ratio.normalized
                        overseas_listing.securities[].underlying_share_count
                        overseas_listing.securities[].is_new_issue
                        overseas_listing.securities[].is_secondary_sale
                        overseas_listing.issuer_total_shares
                        overseas_listing.fundraising_method.raw
                        overseas_listing.fundraising_method.code
                        overseas_listing.fundraising_status
                        overseas_listing.offer_price.raw
                        overseas_listing.offer_price.normalized
                        overseas_listing.gross_proceeds
                        overseas_listing.estimated_costs
                        overseas_listing.delisting_reason
                        overseas_listing.trade_end_planned_date
                        overseas_listing.trade_end_actual_date
                        overseas_listing.dr_cancellation_method
                        overseas_listing.holder_protection_measures
                        overseas_listing.remaining_overseas_markets[]
                        overseas_listing.remaining_domestic_listing
                        overseas_listing.domestic_market.raw
                        overseas_listing.domestic_listing_status
                        overseas_listing.related_disclosures[].receipt_no
                        overseas_listing.related_disclosures[].relation_type
                        overseas_listing.related_disclosures[].relation_evidence
                        overseas_listing.related_disclosures[].resolution_status
                        """),
                calculations(
                        calculation("OVERSEAS_LISTED_SHARE_RATIO", CalculationOperation.RATIO),
                        calculation("DR_UNDERLYING_SHARE_CHECK", CalculationOperation.PRODUCT),
                        calculation("GROSS_PROCEEDS_CHECK", CalculationOperation.PRODUCT),
                        calculation("NET_PROCEEDS_REFERENCE", CalculationOperation.DIFFERENCE),
                        calculation("SCHEDULE_CHANGE_DAYS", CalculationOperation.DATE_DURATION),
                        calculation("DR_COUNT_CHECK", CalculationOperation.RATIO),
                        calculation("UNDERLYING_COUNT_CHECK", CalculationOperation.RATIO),
                        calculation("UNDERLYING_COUNT_CHANGE", CalculationOperation.DIFFERENCE),
                        calculation("DR_COUNT_CHANGE", CalculationOperation.DIFFERENCE),
                        calculation("DR_COUNT_CHANGE_RATE", CalculationOperation.CHANGE_RATE),
                        calculation("DECISION_TO_COMPLETION_DAYS", CalculationOperation.DATE_DURATION),
                        calculation("SCHEDULE_VARIANCE_DAYS", CalculationOperation.DATE_DURATION),
                        calculation("DR_BACKING_SHARE_RATIO", CalculationOperation.RATIO),
                        calculation("NEW_SHARE_DILUTION", CalculationOperation.SHARE_DILUTION)),
                List.of(
                        "특정 해외증권 폐지를 회사 전체 또는 국내 주식 상장폐지로 표현",
                        "원주와 DR 수량을 같은 단위로 합산",
                        "구주매출·이전상장에 신주 희석률 적용",
                        "DR 기초 원주 비중을 외국인 지분율로 표현"
                ));
    }

    public static AnswerPolicy type12() {
        return draft(
                "정정·후속공시 및 기준시점 상태",
                facts("""
                        event_manifest.event_id
                        event_manifest.domain_type
                        event_manifest.entity_id
                        event_manifest.entity_name
                        event_manifest.root_receipt_no
                        event_manifest.documents[].receipt_no
                        event_manifest.documents[].document_date
                        event_manifest.documents[].document_role.raw
                        event_manifest.documents[].document_role.code
                        event_manifest.documents[].link_status
                        event_manifest.documents[].link_evidence
                        event_manifest.current_status.raw
                        event_manifest.current_status.code
                        event_manifest.status_as_of
                        event_manifest.policy_version
                        correction.is_correction
                        correction.date
                        correction.original_disclosure_date
                        correction.original_receipt_no
                        correction.reason
                        correction.scope[].raw
                        correction.scope[].code
                        correction.changes[].fact_id
                        correction.changes[].before.raw
                        correction.changes[].after.raw
                        correction.changes[].before.normalized
                        correction.changes[].after.normalized
                        correction.changes[].effective_from
                        correction.changes[].evidence_locator
                        fact_version.fact_id
                        fact_version.value.raw
                        fact_version.value.normalized
                        fact_version.valid_from
                        fact_version.valid_to
                        fact_version.source_receipt_no
                        fact_version.supersedes_version_id
                        fact_version.is_current
                        """),
                calculations(
                        calculation("VALUE_CHANGE", CalculationOperation.DIFFERENCE),
                        calculation("VALUE_CHANGE_RATE", CalculationOperation.CHANGE_RATE),
                        calculation("DATE_CHANGE_DAYS", CalculationOperation.DATE_DURATION),
                        calculation("RATIO_CHANGE_PP", CalculationOperation.DIFFERENCE),
                        calculation("AS_OF_VALUE_SELECT", CalculationOperation.AS_OF_SELECTION)),
                List.of(
                        "CANDIDATE_LINK를 확정관계로 사용해 최신값 병합",
                        "기준시점 이후 공시를 과거 답변에 소급 반영",
                        "정정되지 않은 Fact까지 새 버전으로 생성",
                        "정정·완료·해지 문서역할을 같은 상태로 처리"
                ));
    }

    private static AnswerPolicy draft(
            String disclosureSubtype,
            List<FactPolicy> facts,
            List<CalculationPolicy> calculations,
            List<String> typeSpecificForbiddenExpressions) {
        return new AnswerPolicy(
                DRAFT_POLICY_VERSION,
                disclosureSubtype,
                facts,
                calculations,
                COMMON_ALLOWED_EXPRESSIONS,
                concat(COMMON_FORBIDDEN_EXPRESSIONS, typeSpecificForbiddenExpressions),
                List.of());
    }

    private static List<FactPolicy> facts(String factKeys) {
        return factKeys.lines()
                .map(String::strip)
                .filter(key -> !key.isEmpty())
                .map(key -> new FactPolicy(key, FactNecessity.SUPPORTING))
                .toList();
    }

    private static List<CalculationPolicy> calculations(CalculationPolicy... calculations) {
        return List.of(calculations);
    }

    private static CalculationPolicy calculation(String calculationId, CalculationOperation operation) {
        return new CalculationPolicy(
                calculationId,
                operation,
                List.of(),
                null,
                RoundingMode.HALF_UP,
                2);
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
