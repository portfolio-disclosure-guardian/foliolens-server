package com.foliolens.backend.policy;

import com.foliolens.backend.question.plan.toolinput.CalculationOperation;

import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;

/**
 * finance_domain 08~12 문서를 옮긴 미승인 draft 정책 묶음.
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
        return List.of(type08(), type09(), type10(), type11(), type12());
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
