# FolioLens QueryPlan 도구 계약과 공시·관심사 라우팅

| 항목 | 내용 |
|---|---|
| 문서 목적 | QueryPlan이 사용할 최소 도구, 공시별 검색 요소, 관심사별 라우팅 규칙을 확정하기 위한 설계안 |
| 대상 | P0 공시 QA 코어와 이후 P2 투자 가정 개인화 |
| 기준일 | 2026-08-03 |
| 상태 | 제안안. 공시 코퍼스 사실·목표 계약·현재 구현 상태를 구분해 서술 |

> 2026-08-25 역할 A 동기화: `QuestionPlanCandidate`·`QuestionPlan` 경계와 현재 계획·오케스트레이션 골격만 갱신했다. `intentTypes` 포함 여부는 `DECISION_REQUIRED`로 유지하며, 데이터 파이프라인·검색·fact·계산 구현 상태와 역할 B·C 계약은 재판정하지 않았다.

## 1. 기술 요약

초안의 방향인 “지표마다 도구를 만들지 않고 범용 도구를 조합한다”는 원칙은 유지한다. 다만 초안의 8개 이름을 그대로 Java 인터페이스 8개로 만들 필요는 없다.

QueryPlan이 선택할 논리 도구는 다음 5개면 충분하다.

1. `SEARCH_DISCLOSURES`: 기업·접수기간·보고기간·공시 유형으로 문서 후보 검색
2. `LOOKUP_FACTS`: 미리 추출되고 검증된 구조화 사실 조회
3. `SEARCH_EVIDENCE`: 선택된 문서의 장·절·문단·표·행에서 근거 검색
4. `RESOLVE_DISCLOSURE_HISTORY`: 원공시·정정·변경·해지·후속 이력과 기준시점 상태 확정
5. `CALCULATE`: 검증된 `factId`만으로 비교·비율·증감률·기간 등을 결정적으로 계산

다음 기능은 필요하지만 QueryPlan이 임의 선택하는 도구로 노출하지 않는다.

- `CompanyResolver`: 계획 검증 과정에서 기업명을 내부 ID로 바꾸는 정규화 단계
- `FactExtractor`: 구조화 사실이 없을 때 근거 문맥에서 후보를 만드는 내부 단계
- `FactValidator`와 `AnswerValidator`: 항상 실행해야 하는 백엔드 검증 단계
- `ComparisonTool`: 별도 도구 대신 `CALCULATE`의 연산과 비교 가능성 검사로 통합

관심사는 새 도구를 만드는 기준이 아니다. 관심사 코드는 같은 5개 도구에 넘길 다음 값을 고르는 라우팅 프로필이다.

~~~text
관심사
→ 우선 공시 그룹·세부 유형
→ 필요한 factKey
→ 섹션·표 레이블·동의어
→ 정정·후속 이력 필요 여부
→ 허용 계산
~~~

중요한 범위 제한은 다음과 같다.

- 평가 경로는 대회 `CONTEST` 코퍼스만 검색한다. OpenDART는 이 문서의 필드 설계 참고자료일 뿐 런타임 데이터 소스가 아니다.
- 저장소에는 승인된 관심사 enum이 아직 없다. 이 문서의 관심사 9종은 **제안 분류**다.
- `major` 598건은 manifest의 `doc_subtype`이 비어 있고, `report_nm` 기준 약 29종이라는 것까지만 확인돼 있다. 어떤 29종인지와 유형별 실제 필드는 전체 표본 검사 전까지 확정할 수 없다.
- 현재 검색·사실·이력·계산 도구는 구현돼 있지 않다. `QuestionPlan`과 `DisclosureRetriever`도 작업 중인 계약 골격이다.

## 2. 초안에서 유지할 것과 바꿀 것

| 초안 항목 | 판단 | 구체화 |
|---|---|---|
| 질문마다 도구를 만들지 않음 | 유지 | `매출액검색도구`, `배당검색도구` 같은 도구는 만들지 않는다. |
| 고정 QueryPlan | 유지 | 스키마는 고정하고 의도, 관심사, factKey, 공시 선택자와 연산만 바꾼다. |
| `CompanyResolverTool` | 역할 유지, 도구 노출 제외 | 계획 검증기가 반드시 실행한다. |
| `DisclosureSearchTool` | 유지 | 메타데이터 기반 문서 후보만 반환한다. |
| `EvidenceSearchTool` | 유지 | 문단·표 근거를 반환하고 숫자를 계산하지 않는다. |
| `FactLookupTool` | 유지 | 검증된 구조화 사실의 빠른 경로다. |
| `FactExtractionTool` | 내부 단계로 변경 | fact가 없을 때 백엔드가 호출한다. LLM이 매번 선택하게 하지 않는다. |
| `DisclosureHistoryTool` | 유지 | “최신 문서 1건”이 아니라 사건 이력과 `asOf` 상태를 반환한다. |
| `ComparisonTool` | 삭제 | `CALCULATE`의 비교 가능성 검사와 `DIFFERENCE` 등으로 충분하다. |
| `CalculationTool` | 유지 | 원문 숫자가 아니라 검증된 `factId`만 입력받는다. |
| `ToolRegistry` | 보류 | HCX function calling 또는 실행 구현이 2개 이상 필요할 때 추가한다. 현재는 기존 `DisclosureRetriever` 경계와 명시적 분기로 충분하다. |

## 3. 사실·설계·구현 상태

이 문서의 표는 다음 상태를 사용한다.

| 상태 | 의미 |
|---|---|
| 코퍼스 확인 | `DATA_CATALOG.md`에 실제 대회 데이터의 유형·건수·형식이 기록됨 |
| 필드 확인 | 제공 원문 표본에서 레이블까지 확인돼 `DATA_CATALOG.md`에 기록됨 |
| 목표 계약 | 요구사항·기능명세 또는 이 문서에서 정의했지만 실행 구현은 없음 |
| 참고 스키마 | 금융감독원 OpenDART 공식 구조를 내부 fact 설계 참고로만 사용 |
| 미확정 | 실제 코퍼스 전체 표본 또는 팀 결정이 더 필요함 |

현재 코퍼스 기준은 다음과 같다.

| `doc_group` | 확인된 범위 | 파서 특성 | 구조화 상태 |
|---|---:|---|---|
| `periodic` | 1,054건: 사업 291, 반기 234, 분기 529 | DART 문서 XML. 표준 XBRL fact 파일과 같지 않음 | 매출·영업이익·자산·부채·R&D는 목표 필드, 파서 미구현 |
| `major` | 598건, `report_nm` 기준 약 29종 | DART 문서 XML | `doc_subtype` 정규화와 유형별 필드 미확정 |
| `exchange` | 1,469건 | 확장자는 XML이나 실제 HTML, Jsoup 계열 파싱 | 시설투자·공급계약 일부 필드 확인 |
| `holding` | 1,083건 | DART 문서 XML | 대량보유 유형 존재 확인, 내부 fact 스키마 미확정 |

근거: `docs/DATA_CATALOG.md:148-234, 269-342, 493-531`.

## 4. 모든 공시에 공통으로 필요한 요소

### 4.1 문서 메타데이터

`SEARCH_DISCLOSURES`는 최소한 다음 요소를 반환해야 한다.

| 구분 | 필드 |
|---|---|
| 식별 | `disclosureId`, `docId`, `receiptNo` |
| 기업 | `companyId`, `corpCode`, `corpName`, `listedName`, `stockCode` |
| 분류 | `docGroup`, `normalizedSubtype`, `rawReportName` |
| 시간 | `receiptDate`, `reportPeriod`, `baseYear`, `baseMonth` |
| 상태 | `isCorrection`, `documentStatus` |
| 제출 | `submitter` |
| 출처 | `sourceProvider=CONTEST`, `datasetVersion` |

접수일과 보고기간은 같은 값이 아니다. 예를 들어 2026년 1분기보고서는 2026년 5월에 접수될 수 있다. 따라서 `receiptPeriod`, `reportPeriod`, 질문 기준시점 `asOf`를 분리한다.

### 4.2 근거 위치

`SEARCH_EVIDENCE`와 `LOOKUP_FACTS`가 반환하는 사실은 다음 위치로 역추적돼야 한다.

| 구분 | 필드 |
|---|---|
| 문서 | `disclosureId`, `receiptNo`, `documentId`, `documentRole` |
| 구조 | `sectionId`, `sectionPath`, `blockType` |
| 표 | `tableId`, `headers`, `rowLabel`, `columnLabel`, `rawUnit` |
| 문맥 | `content` 또는 필요한 최소 앞뒤 문맥 |
| 위치 | `sourceLocation`, 가능하면 `startOffset`·`endOffset` |
| 검색 | `retrievalScore`, `retrievalMethod`, `queryTerms` |
| 사용 | `usedInAnswer` |

### 4.3 구조화 사실

공시 종류가 달라도 공통 fact envelope은 하나를 사용한다.

~~~json
{
  "factId": "uuid",
  "disclosureId": "uuid",
  "factKey": "contract.amount",
  "valueType": "MONEY",
  "rawValue": "1,200억원",
  "normalizedValue": 120000000000,
  "currency": "KRW",
  "unit": "KRW",
  "periodStart": null,
  "periodEnd": null,
  "accountingBasis": null,
  "evidenceIds": ["evidence-uuid"],
  "validationStatus": "VERIFIED",
  "extractorVersion": "contract-v1"
}
~~~

원문 값·원문 단위·정규화 값·변환 규칙·원문 위치를 함께 보존한다. `normalizedValue`만 저장하면 표시 오류와 단위 변환을 다시 검증할 수 없다.

## 5. QueryPlan이 사용할 5개 도구

### 5.1 `SEARCH_DISCLOSURES`

역할: 내용을 읽기 전에 기업·시간·분류·제목·정정 여부로 문서 후보를 좁힌다.

~~~json
{
  "companyIds": ["uuid"],
  "receiptPeriod": {"from": "2024-01-01", "to": "2026-03-31"},
  "reportPeriod": {"from": "2024-01-01", "to": "2025-12-31"},
  "asOf": "2026-03-31",
  "docGroups": ["periodic", "exchange"],
  "subtypes": ["annual", "신규시설투자등"],
  "titleTerms": [],
  "correctionPolicy": "ALL",
  "limit": 20
}
~~~

반환:

~~~json
{
  "documents": [
    {
      "disclosureId": "uuid",
      "receiptNo": "20240424800596",
      "companyId": "uuid",
      "docGroup": "exchange",
      "subtype": "신규시설투자등",
      "reportName": "신규시설투자등",
      "receiptDate": "2024-04-24",
      "reportPeriod": null,
      "isCorrection": false,
      "sourceProvider": "CONTEST"
    }
  ],
  "coverage": {"candidateCount": 1, "truncated": false},
  "warnings": []
}
~~~

규칙:

- `keywords`로 본문까지 검색하지 않는다. 제목 검색어는 `titleTerms`, 본문 검색어는 `SEARCH_EVIDENCE`로 분리한다.
- `limit`는 1~50처럼 상한을 둔다.
- “최종본만” 동작을 기본값으로 삼지 않는다. 정정·후속 관계를 잃을 수 있다.

### 5.2 `LOOKUP_FACTS`

역할: 이미 검증된 정형 수치·날짜·상태를 가장 먼저 조회한다.

~~~json
{
  "companyIds": ["uuid"],
  "disclosureIds": [],
  "factKeys": ["financial.revenue", "business.rnd_expense"],
  "reportPeriods": [
    {"year": 2024, "quarter": "FY"},
    {"year": 2025, "quarter": "FY"}
  ],
  "accountingBasis": "CONSOLIDATED",
  "asOf": "2026-03-31",
  "effectiveOnly": true
}
~~~

반환: 공통 fact envelope 배열, 각 fact의 근거, 누락 factKey, 기준 충돌.

규칙:

- 기본적으로 `validationStatus=VERIFIED`만 반환한다.
- 누락을 0으로 바꾸지 않는다.
- 연결·별도, 누적·당기, 통화·단위가 다른 값을 같은 사실처럼 합치지 않는다.

### 5.3 `SEARCH_EVIDENCE`

역할: 선택된 공시에서 관련 문단·표·행을 검색한다. 서술형 질문과 누락 fact의 근거 확보에 사용한다.

~~~json
{
  "disclosureIds": ["uuid"],
  "concepts": ["RND_EXPENSE", "CAPACITY_EXPANSION"],
  "factKeys": ["business.rnd_expense"],
  "sectionHints": ["연구개발활동", "연구개발비용"],
  "keywords": ["연구개발비", "R&D"],
  "blockTypes": ["PARAGRAPH", "TABLE", "TABLE_ROW"],
  "topK": 10
}
~~~

반환: 근거 위치 계약을 만족하는 evidence 배열과 검색 범위.

규칙:

- 먼저 `SEARCH_DISCLOSURES`로 문서 집합을 제한한다.
- 표 셀 하나만 반환하지 않고 머리글·행 레이블·단위를 함께 반환한다.
- 검색 결과가 없을 때 외부 웹이나 OpenDART로 우회하지 않는다.

### 5.4 `RESOLVE_DISCLOSURE_HISTORY`

역할: 정정 여부 플래그를 넘어서 같은 사건의 최초·정정·변경·해지·완료를 연결하고 `asOf` 시점의 상태를 계산한다.

~~~json
{
  "seedDisclosureIds": ["uuid"],
  "eventKey": {
    "companyId": "uuid",
    "eventType": "SUPPLY_CONTRACT",
    "counterparty": "A사",
    "subject": "반도체 공급"
  },
  "asOf": "2025-12-31",
  "relationTypes": ["CORRECTS", "SUPERSEDES", "FOLLOWS_UP", "RELATED"]
}
~~~

반환:

~~~json
{
  "events": [
    {
      "disclosureId": "uuid",
      "relationType": "FOLLOWS_UP",
      "effectiveAt": "2025-01-20",
      "state": "TERMINATED",
      "changedFactKeys": ["contract.status"]
    }
  ],
  "currentState": "TERMINATED",
  "stateAsOf": "2025-12-31",
  "relationConfidence": 1.0,
  "unresolvedCandidates": []
}
~~~

규칙:

- manifest가 제공하는 것은 `is_correction`뿐이다. 원공시 접수번호와 후속 관계는 별도 추출·검증이 필요하다.
- 가장 최근 공시라는 이유만으로 동일 사건의 최종 상태라고 단정하지 않는다.
- 관계가 불확실하면 후보와 근거를 반환하고 `UNKNOWN` 상태를 허용한다.

### 5.5 `CALCULATE`

역할: 검증된 fact를 같은 기준으로 정렬한 뒤 결정적 계산을 수행한다.

~~~json
{
  "operation": "CHANGE_RATE",
  "inputFactIds": ["fact-2024", "fact-2025"],
  "comparisonBasis": {
    "sameCompany": true,
    "sameFactKey": true,
    "sameAccountingBasis": true,
    "samePeriodKind": true
  },
  "roundingRule": "HALF_UP_1"
}
~~~

지원 연산:

| 코드 | 동작 |
|---|---|
| `DIFFERENCE` | 비교값 - 기준값 |
| `CHANGE_RATE` | (비교값 - 기준값) / 기준값 × 100 |
| `RATIO` | 부분값 / 전체값 × 100 |
| `SUM` | 동일 기준 값 합계 |
| `AVERAGE` | 동일 기준 값 산술 평균 |
| `DATE_DURATION` | 종료일 - 시작일 |
| `UNIT_CONVERSION` | 표시 단위 변환 |
| `SHARE_DILUTION` | 정의된 주식 수 기준 잠재 희석률 |

규칙:

- LLM이 전달한 원시 숫자를 입력받지 않는다. `factId`를 다시 읽어 계산한다.
- 기준값 0, null, 기간·회계 기준 불일치는 `CALCULATION_NOT_APPLICABLE`로 반환한다.
- 원계산값과 표시값, 공식과 규칙 버전을 보존한다.

## 6. Agent 도구가 아닌 필수 내부 단계

### 6.1 기업 식별

`QuestionPlanCandidate`의 회사 표현을 기업 마스터로 검증한다.

~~~text
기업명·별칭·종목코드
→ 후보 검색
→ 유일한 companyId 확정 또는 AMBIGUOUS
~~~

기업 식별 실패 상태에서 공시 검색을 실행하지 않는다.

### 6.2 사실 추출과 정규화

`LOOKUP_FACTS`에 필요한 값이 없고 evidence가 있을 때만 수행한다.

~~~text
Evidence + typeSchemaId
→ 규칙 기반 표 파서 우선
→ 복잡한 문장·표만 HCX 구조화 후보
→ 자료형·단위·범위·원문 존재 검증
→ VERIFIED fact 저장
~~~

이 단계는 `시설투자검색도구` 같은 새 도구를 만들지 않고 `typeSchemaId`와 `factKey` 사전으로 확장한다.

### 6.3 검증

다음 검증은 계획에 없어도 항상 실행한다.

- 입력·허용 enum·도구·단계 수 검증
- fact의 원문 존재·자료형·단위 검증
- 정정 전 값의 최신값 오용 검사
- 계산 입력 기준과 공식 검증
- 답변의 기업명·날짜·수치·근거 ID 일치 검사
- 투자 권유·목표주가·확률·수익 보장 표현 검사

## 7. 공시 그룹별 요소 카탈로그

### 7.1 정기공시: 사업·반기·분기보고서

정기공시는 하나의 거대한 fact DTO가 아니라 다음 element pack으로 나눈다.

| element pack | 대표 원문 영역 | 우선 factKey | 기본 경로 |
|---|---|---|---|
| 재무성과 | 재무제표, 연결재무제표, 재무에 관한 사항 | `financial.revenue`, `financial.operating_profit`, `financial.net_income` | `LOOKUP_FACTS` → 누락 시 `SEARCH_EVIDENCE` |
| 재무상태 | 재무상태표, 현금흐름표, 주석 | `financial.total_assets`, `financial.total_liabilities`, `financial.total_equity`, `financial.cash_and_equivalents`, `financial.operating_cash_flow` | `LOOKUP_FACTS` |
| 사업·매출 | 사업의 내용, 주요 제품 및 서비스, 매출 및 수주상황 | `business.sales_by_segment`, `business.order_backlog`, `business.major_products` | `SEARCH_EVIDENCE` 중심 |
| 원가·생산 | 원재료, 생산설비, 생산능력·실적·가동률 | `business.raw_material_cost`, `business.capacity`, `business.utilization_rate` | fact 우선, 설명은 evidence |
| 연구개발 | 연구개발활동, 연구개발비용 | `business.rnd_expense`, `business.rnd_ratio`, `business.rnd_projects` | fact + evidence |
| 주식·배당 | 주식의 총수, 자기주식, 배당 | `shares.issued`, `shares.treasury`, `dividend.dps`, `dividend.total`, `dividend.yield` | `LOOKUP_FACTS` |
| 자금·채무 | 채무증권, CP·단기사채·회사채 잔액, 공모·사모자금 사용 | `debt.issued_amount`, `debt.outstanding`, `funds.use_amount` | fact + 만기·용도 evidence |
| 지배구조 | 최대주주, 사외이사, 임원·직원, 보수 | `governance.largest_holder_ratio`, `governance.outside_director_count` | fact + history |
| 감사·회계 | 감사인, 감사의견, 강조사항, 핵심감사사항 | `audit.opinion`, `audit.auditor`, `audit.key_matter` | fact + evidence |
| 타법인 투자 | 타법인 출자현황 | `investment.other_company.amount`, `investment.other_company.ownership_ratio`, `investment.other_company.book_value` | fact |
| 위험 | 위험관리 및 파생거래, 소송·우발사항, 기타 주석 | `risk.category`, `risk.exposure`, `risk.contingency_amount` | `SEARCH_EVIDENCE` 중심 |

정기보고서 구조화 시 반드시 다음 비교 축을 함께 저장한다.

- `reportCode`: 1분기·반기·3분기·사업보고서
- `fsDiv`: 연결(CFS)·별도(OFS)
- `statementType`: BS·IS·CIS·CF·SCE
- 당기 3개월 값과 당기 누적값
- 기간 시작·종료일, 통화와 표시 단위

OpenDART 공식 참고 구조에는 정기보고서 주요정보 30종이 있으며, 주식 총수·자기주식·배당·증감자·채무증권·미상환 잔액·자금 사용·감사·사외이사·최대주주·임직원·보수·타법인 출자 등을 제공한다. 이 목록은 내부 factKey 설계 참고이며 대회 평가 경로에서 API를 호출하지 않는다.

### 7.2 거래소공시

대회 코퍼스에서 확인된 세부 유형만 우선한다.

| subtype | 확인 건수 | 끌어올 요소 | 권장 factKey | 이력 |
|---|---:|---|---|---|
| 단일판매공급계약체결 | 1,106 | 계약내용, 금액, 최근 매출액, 매출 대비 비율, 상대방, 시작·종료일, 조건·지역 | `contract.description`, `contract.amount`, `contract.sales_amount`, `contract.sales_ratio`, `contract.counterparty`, `contract.start_date`, `contract.end_date`, `contract.status` | 정정·변경·해지 필수 |
| 단일판매공급계약해지 | 20 | 원계약 식별자, 해지금액, 해지사유, 해지일, 손해·위약 조건 | `contract.status`, `contract.termination_amount`, `contract.termination_reason`, `contract.termination_date` | 원계약 연결 필수 |
| 신규시설투자등 | 43 | 투자대상, 투자금액, 자기자본, 자기자본 대비 비율, 목적, 시작·종료일, 결정일, 자금조달 방식 | `facility.target`, `facility.amount`, `facility.equity_amount`, `facility.equity_ratio`, `facility.purpose`, `facility.start_date`, `facility.end_date`, `facility.decision_date` | 변경·기간연장·종료 확인 |
| 투자판단관련주요경영사항 | 300 | 사건명, 대상, 금액·규모, 결정일, 목적, 조건, 불확실성, 관련공시 | `management_event.type`, `management_event.subject`, `management_event.amount`, `management_event.decision_date`, `management_event.status` | 사건별로 판단 |

`투자판단관련주요경영사항`은 내용 범위가 넓으므로 고정 필드만으로 완전 처리하지 않는다. 공통 사건 필드만 구조화하고 세부 설명은 evidence 검색으로 남긴다.

### 7.3 지분공시

대회 코퍼스에는 `주식 등의 대량보유상황보고서` 1,083건이 확인돼 있다.

| element pack | 요소 | 권장 factKey |
|---|---|---|
| 보고 | 보고구분, 접수일, 보고사유 | `holding.report_type`, `holding.report_reason` |
| 보고자 | 대표보고자, 공동보유자, 발행회사와의 관계 | `holding.reporter`, `holding.joint_holders`, `holding.relationship` |
| 보유 | 보유 주식등 수, 보유비율 | `holding.share_count`, `holding.ratio` |
| 변동 | 증감 수, 비율 증감, 변동일·원인 | `holding.share_count_change`, `holding.ratio_change`, `holding.change_reason` |
| 목적 | 단순투자·일반투자·경영권 영향 등 보유 목적 | `holding.purpose` |
| 주요계약 | 담보·대차·신탁 등 주요체결 주식등의 수와 비율 | `holding.major_contract_share_count`, `holding.major_contract_ratio` |

`holding` 내부 스키마는 아직 저장소에서 확정되지 않았다. 위 필드는 공식 대량보유 요약 구조와 일반 원문 요소를 바탕으로 한 목표 계약이며, 제공 XML 표본으로 레이블을 검증해야 한다.

### 7.4 주요사항보고서

OpenDART 공식 주요정보는 36종을 다음처럼 묶는다. 대회 `major` 598건에 이 36종이 모두 들어 있다고 가정하면 안 된다.

| 묶음 | 공식 세부 유형 | 공통으로 끌어올 요소 |
|---|---|---|
| 자본 변동 | 유상증자, 무상증자, 유무상증자, 감자, 상각형 조건부자본증권 | 신주 종류·수, 증자 전 주식 수, 액면·발행가, 배정 방식, 자금조달 목적별 금액, 납입·상장일, 감자 목적·비율, 결정일 |
| 주권연계 사채 | 전환사채, 신주인수권부사채, 교환사채 | 발행총액, 표면·만기이율, 만기일, 발행방법, 전환·행사·교환가액과 비율, 대상 주식 수, 잠재 희석률, 청구기간, 가격조정, 자금용도 |
| 합병·분할 | 회사합병, 회사분할, 회사분할합병, 주식교환·이전 | 상대회사, 방식·목적, 비율, 대가, 주요 일정, 주주총회, 주식매수청구권, 외부평가기관, 효력발생일 |
| 영업·자산·지분 거래 | 영업양수·양도, 유형자산 양수·양도, 타법인 주식·출자증권 양수·양도, 주권 관련 사채권 양수·양도, 자산양수도(기타)·풋백옵션 | 대상, 상대방, 거래금액, 자산·자기자본 대비 비율, 지급방법, 계약·완료일, 취득 전후 지분, 목적·영향, 결정일 |
| 자기주식 | 자기주식 취득·처분, 자기주식취득 신탁계약 체결·해지 | 주식 종류·수, 예정금액, 기간·방법, 목적, 중개기관·계약상대, 계약금액, 전후 보유량·비율, 결정일 |
| 계속기업·법률 | 부도발생, 영업정지, 회생절차 개시신청, 해산사유 발생, 소송 등의 제기, 채권은행 관리절차 개시·중단 | 발생일, 원인, 금액·규모, 관련 기관·법원·사건번호, 영업·재무 영향, 대응·재개 계획, 현재 상태 |
| 해외상장 | 해외 증권시장 주권등 상장 결정·상장·상장폐지 결정·상장폐지 | 시장, 증권 종류·수, 일정, 결정·완료 상태, 사유 |

유형별 내부 fact namespace 예:

~~~text
capital_increase.*
capital_reduction.*
convertible_bond.*
bond_with_warrant.*
exchangeable_bond.*
merger.*
demerger.*
business_transfer.*
asset_transaction.*
equity_investment.*
treasury_share.*
distress.*
litigation.*
overseas_listing.*
~~~

`report_nm`에서 정정 태그를 제거한 뒤 공식 유형 후보로 정규화하되, 원문 `report_nm`도 반드시 보존한다. 매핑되지 않는 유형은 `UNSUPPORTED`로 버리지 말고 `UNKNOWN_SUBTYPE`과 원문을 저장해 evidence 검색은 가능하게 한다.

### 7.5 정정·후속 요소

모든 공시 유형에 다음 이력 요소를 공통 적용한다.

| 요소 | 설명 |
|---|---|
| `relationType` | `CORRECTS`, `SUPERSEDES`, `FOLLOWS_UP`, `RELATED` |
| `source/targetDisclosureId` | 관계 방향 |
| `relationBasis` | 원접수번호, 정정표, 관련공시, 결정적 사건 키 등 |
| `confidence` | 규칙으로 확정되지 않은 후보의 신뢰도 |
| `changedFactKeys` | 정정 전후 바뀐 필드 |
| `effectiveAt` | 사건 효력 시점 |
| `state` | 계획·진행·완료·변경·해지·철회·불명 등 |

현재 제공 manifest에는 이 관계가 없다. 전체 관계를 만들기 전까지 `is_correction=true`만 보고 현재값을 확정하지 않는다.

## 8. 제안 관심사 9종

이 분류는 공식 DART 분류나 승인된 저장소 enum이 아니라 FolioLens 투자 가정 라우팅을 위한 제안이다.

| 코드 | 사용자 관심 예 | 핵심 질문 |
|---|---|---|
| `GROWTH_DEMAND` | 매출 성장, 수주 증가, 고객 확대 | 수요와 매출 기반이 실제로 커지는가 |
| `PROFITABILITY_EFFICIENCY` | 이익률, 원가, 가동률 | 성장한 매출이 이익과 현금으로 이어지는가 |
| `INVESTMENT_INNOVATION` | 시설 증설, R&D, 신사업 | 미래 역량에 얼마를 어디에 투자하는가 |
| `FINANCIAL_STABILITY` | 부채, 유동성, 현금, 감사 | 투자·운영을 감당할 재무 여력이 있는가 |
| `FINANCING_DILUTION` | 유상증자, CB·BW·EB | 자금조달 조건과 잠재 희석은 무엇인가 |
| `SHAREHOLDER_RETURN` | 배당, 자사주, 소각 | 현금과 자본을 주주에게 어떻게 배분하는가 |
| `OWNERSHIP_GOVERNANCE` | 최대주주, 5% 보고, 이사회 | 지배력·경영진·감사 구조가 바뀌는가 |
| `CONTRACT_EXECUTION` | 공급계약, 해지, 수주잔고 | 계약의 규모·기간·상대방과 현재 이행 상태는 무엇인가 |
| `CORPORATE_ACTION_RISK` | 합병, 자산거래, 소송, 영업정지 | 회사 구조나 계속기업 위험을 바꾸는 사건이 있는가 |

관심사 코드는 검색 제외 조건이 아니라 우선순위 힌트다. 예를 들어 성장 관심사라고 해서 자금조달 공시를 제외하면, 성장 투자를 위한 대규모 희석성 조달을 놓칠 수 있다.

## 9. 관심사별 각 도구가 끌어올 요소

| 관심사 | `SEARCH_DISCLOSURES` | `LOOKUP_FACTS` | `SEARCH_EVIDENCE` | `RESOLVE_HISTORY` | `CALCULATE` |
|---|---|---|---|---|---|
| 성장·수요 | 정기, 계약 체결·해지, 주요경영사항, 영업·지분 취득 | 매출, 부문매출, 수주잔고, 계약금액·매출비율 | 사업의 내용, 제품·서비스, 매출·수주, 고객·지역, 성장 목적 | 계약 변경·해지, 사업 취득 완료 | 기간 차이·증감률, 계약 합계·매출비율 |
| 수익성·효율 | 정기와 정정본 | 매출, 영업이익, 순이익, 원가, 현금흐름, 가동률 | 원가 변동 사유, 제품 믹스, 원재료·생산설비 | 재무 정정 | 영업이익률, 순이익률, 증감률 |
| 투자·혁신 | 시설투자, 정기, 유형자산·타법인 지분 양수, 주요경영사항 | 시설투자액·자기자본비율, R&D비·매출비율, 투자기간 | 투자목적, 자금조달 방식, R&D 과제, 생산능력 | 투자 변경·기간연장·종료 | 투자액 증감, 자기자본·매출 대비 비율, 기간 |
| 재무안정 | 정기, 채무·자본 조달, 부도·회생·관리절차, 소송 | 자산·부채·자본·현금, 영업현금흐름, 채무잔액·만기, 감사의견 | 유동성·시장위험 주석, 핵심감사사항, 채무 조건 | 정정, 관리절차 개시·중단 | 부채비율, 유동성 비율, 만기 합계, 증감률 |
| 조달·희석 | 유상·무상증자, 감자, CB·BW·EB, 정기 주식총수 | 신주·기존주식 수, 발행가·총액, 잠재주식 수, 자금용도 | 배정 대상·방식, 리픽싱, 보호예수, 조달 배경 | 발행조건 정정·철회·완료 | 잠재 희석률, 조달 목적별 합계 |
| 주주환원 | 정기 배당·자기주식, 자기주식 취득·처분·신탁 | DPS, 배당총액·수익률·성향, 자기주식 수·금액 | 배당정책, 취득·처분 목적, 소각 여부 | 계획 변경·신탁 해지·완료 | 배당성향, 전년 대비 DPS, 자기주식 비율 |
| 지분·지배구조 | 대량보유, 정기 최대주주·임원·사외이사·감사 | 보고자, 보유 수·비율·증감·목적, 최대주주 지분, 이사 수, 감사의견 | 변동사유, 공동보유·주요계약, 핵심감사사항 | 5% 보고와 최대주주 변동의 시간순 상태 | 지분율 증감, 의결권 비율 |
| 계약 이행 | 계약 체결·해지와 정정본 | 계약내용·금액·매출비율·상대방·기간·상태 | 조건, 지역, 진행·해지 사유, 위약 조건 | 체결→정정→해지/완료 필수 | 계약기간, 금액 증감, 매출비율 재계산 |
| 기업행위·위험 | 합병·분할, 영업·자산 거래, 부도·정지·회생·소송 | 거래금액·비율, 상대방, 주요일자, 소송금액, 상태 | 목적·영향, 평가·조건, 대응 계획과 불확실성 | 결정→승인→완료·철회, 위험 해소 여부 | 자산·자본 대비 비율, 기간, 금액 합계 |

## 10. 라우팅 프로필 계약

관심사별 분기를 서비스 코드 곳곳에 흩뿌리지 않고 하나의 버전된 프로필로 표현한다.

~~~json
{
  "code": "INVESTMENT_INNOVATION",
  "primarySelectors": [
    {"docGroup": "exchange", "subtypes": ["신규시설투자등"]},
    {"docGroup": "periodic", "subtypes": ["annual", "half", "quarter"]}
  ],
  "primaryFactKeys": [
    "facility.amount",
    "facility.equity_ratio",
    "business.rnd_expense"
  ],
  "supportingFactKeys": [
    "financial.operating_cash_flow",
    "financial.total_equity"
  ],
  "sectionHints": ["연구개발활동", "원재료 및 생산설비", "투자목적"],
  "historyMode": "WHEN_CORRECTED_OR_CURRENT_STATE",
  "allowedOperations": ["DIFFERENCE", "CHANGE_RATE", "RATIO", "DATE_DURATION"],
  "version": "interest-routing-v1"
}
~~~

라우팅 순서:

~~~text
1. 질문에서 기업·기간·의도·관심사 후보 생성
2. 백엔드가 enum과 기업·시간 조건 검증
3. 여러 관심사 프로필의 selector와 factKey를 합집합
4. SEARCH_DISCLOSURES
5. LOOKUP_FACTS
6. 누락 fact 또는 서술형 요구만 SEARCH_EVIDENCE → 내부 추출
7. 현재 상태·정정 가능성이 있으면 RESOLVE_DISCLOSURE_HISTORY
8. 필요한 경우 CALCULATE
9. 답변 생성 후 근거·수치·안전 검증
~~~

관심사 프로필은 검색 순위를 높일 수 있지만, 질문이 명시한 공시 유형이나 factKey를 제거할 수 없다.

## 11. 권장 QueryPlan — 결정 대기 포함

초안처럼 스키마는 고정하되 접수기간·보고기간·기준시점과 retrieval request를 분리한다.

아래 JSON은 목표 구조를 설명하는 과거 제안 예시이며 현재 Java DTO나 확정 schema가 아니다. 특히 `intentTypes` 포함 여부는 결정 대기 상태다. HCX가 만드는 `QuestionPlanCandidate`와 Spring 검증을 통과한 `QuestionPlan`은 별도 DTO여야 하며, 미해결 기업 표현·모호성은 후보에, 해소된 기업 참조·검증 step·정규화 warning은 검증 계획에 둔다.

~~~json
{
  "schemaVersion": 1,
  "intentTypes": ["COMPARISON", "CALCULATION"],
  "companies": [
    {
      "mention": "삼성전자",
      "companyId": null,
      "resolutionStatus": "UNRESOLVED"
    }
  ],
  "time": {
    "receiptPeriod": null,
    "reportPeriods": [
      {"year": 2024, "periodType": "FY"},
      {"year": 2025, "periodType": "FY"}
    ],
    "asOf": "2026-03-31"
  },
  "interests": ["INVESTMENT_INNOVATION"],
  "retrievalRequests": [
    {
      "docGroups": ["periodic"],
      "subtypes": ["annual"],
      "factKeys": ["business.rnd_expense"],
      "sectionHints": ["연구개발활동", "연구개발비용"],
      "historyMode": "LATEST_EFFECTIVE"
    }
  ],
  "operations": [
    {
      "operation": "CHANGE_RATE",
      "inputBindings": [
        {"factKey": "business.rnd_expense", "period": {"year": 2024, "periodType": "FY"}},
        {"factKey": "business.rnd_expense", "period": {"year": 2025, "periodType": "FY"}}
      ]
    }
  ],
  "ambiguities": []
}
~~~

검증기는 후보에서 `companyId`를 확정하고 허용 enum, 기간, 최대 문서 수, 단계 의존성을 검사한 뒤 별도 `QuestionPlan`을 생성한다. 계산 시에는 `inputBindings`를 실제 검증된 `factId`로 바꾼다.

현재 작업 트리의 `QuestionPlan`은 `schemaVersion`, JPA `Company` 목록, 단일 `Instant`, `PlanStep` 목록과 warning을 가진 작업 중 골격이다. `QuestionPlanCandidate`는 단일 회사 문자열, 단순 `Instant from/to`, 관심 코드, 같은 `PlanStep` 타입과 모호성을 사용한다. 후보→검증 validator는 없고 `PlanStep` 입력도 자유 형식 문자열이므로, 이 문서의 목표 계약은 현재 구현 사실이 아니다.

## 12. 질문 유형별 실행 경로

| 질문 | 우선 경로 |
|---|---|
| “2025년 연결 매출액은?” | 문서 검색 → fact 조회 → 근거 반환 |
| “2024년 대비 얼마나 증가했어?” | 두 기간 fact 조회 → 기준 검사 → 계산 |
| “핵심 사업이 어떻게 변했어?” | 두 정기보고서의 사업 섹션 검색 → HCX 비교 설명 → 주장별 evidence 검증 |
| “이 계약은 아직 유효해?” | 계약 문서 검색 → 계약 fact → 정정·해지 이력 → `asOf` 상태 |
| “최근 자금조달을 정리해줘” | 증자·CB·BW·EB 문서 검색 → 유형별 fact → 이력 → 목적별 합계 |
| “현재 주가는?” | 코퍼스 범위 검사 → `UNANSWERABLE`, 외부 검색 금지 |

정형 질문은 fact 우선, 서술형 질문은 evidence 우선, 혼합 질문은 둘 다 사용한다.

## 13. 최소 구현 순서

### 13.1 첫 수직 슬라이스

기존 데이터 카탈로그의 검증 표본을 그대로 사용한다.

1. 기업 식별
2. `SEARCH_DISCLOSURES`로 SK하이닉스 `신규시설투자등` `20240424800596` 검색
3. Jsoup으로 표 행을 evidence로 복원
4. 시설투자 8개 fact 추출·검증
5. 투자금액 / 자기자본 비율 재계산
6. fact와 표 행 근거를 포함한 답변

필요한 실행 경계는 기존 `DisclosureRetriever.retrieve(QuestionPlan)` 하나와 계산 컴포넌트면 충분하다. 이 단계에서 범용 `ToolRegistry`나 인터페이스 8개를 만들지 않는다.

### 13.2 다음 확장

1. 공급계약 체결·해지와 사건 이력
2. 정기보고서 재무 fact와 R&D
3. 대량보유 지분 fact
4. `major`의 실제 29개 `report_nm` 목록 추출과 유형별 스키마
5. 골든셋에서 필요한 관심사 프로필만 추가

### 13.3 지금 만들지 않을 것

- 지표별 검색 도구
- 임의의 외부 웹·OpenDART 런타임 fallback
- 검색 구현 하나뿐인 상태의 factory·registry
- 정책 허용과 품질 개선 효과가 검증되지 않은 Vector DB
- P0 골든셋 전에 관심사 기반 중요도·알림 엔진

## 14. 완료 조건

도구 계약은 다음 질문에 모두 “예”라고 답할 때 완료다.

- 문서 검색 결과가 접수일과 보고기간을 구분하는가?
- 모든 수치가 원문 표의 머리글·행·단위로 역추적되는가?
- 계산 입력은 검증된 `factId`인가?
- 연결·별도와 누적·당기 기준 불일치를 차단하는가?
- 정정·후속공시가 있는 현재 상태 질문에서 최초 공시만 사용하지 않는가?
- 관심사가 달라도 같은 5개 도구를 재사용하는가?
- 관심사 라우팅이 관련 공시를 우선할 뿐 명시된 조건을 제외하지 않는가?
- 근거가 없을 때 `UNKNOWN`·`PARTIAL`·`UNANSWERABLE`을 구분하는가?
- 평가 프로필에서 `CONTEST` 외 데이터 호출이 0건인가?
- 실행 결과가 도구·검색 범위·계산·검증 버전으로 재현되는가?

## 15. 현재 구현 상태

| 항목 | 현재 확인 |
|---|---|
| 기업 데이터 | 기업 엔티티·Repository·CSV importer 존재 |
| `QuestionPlan` | 작업 중 골격. 후보·검증 DTO가 완전히 분리되지 않았고 경계용 기업 참조·시간 구분·구조화 step input·validator가 없음 |
| `DisclosureRetriever` | A-B 경계 인터페이스만 존재, 구현 없음 |
| `RetrievalResult` | 빈 record |
| 공시·section·chunk·fact Flyway | 현재 런타임 migration에 없음 |
| 실제 검색·추출·이력·계산 | 미구현 |
| 오케스트레이션 | Retriever를 호출하지 않고 기본 응답만 반환 |

따라서 이 문서의 도구와 factKey를 “현재 서비스가 제공한다”고 표현하면 안 된다. 구현 전 계약과 우선순위다.

## 16. 근거와 참고자료

### 저장소 기준

- `docs/PROJECT_CONTEXT.md:27-128, 130-194, 268-362`
- `docs/요구사항_정의서.md:150-197, 278-368, 384-444, 484-525, 647-707, 820-853`
- `docs/기능명세서.md:254-397, 506-734, 890-1046, 1431-1480`
- `docs/IA.md:127-157, 537-577, 859-927, 1201-1255`
- `docs/DECISIONS.md:17-22`
- `docs/DATA_CATALOG.md:34-105, 148-234, 269-373, 401-531, 562-609`
- 사용자 초안 `Queryplan에 담겨있는 검색도구와 검색도구의 구현`

### 공식 구조 참고

- [OpenDART 공시검색](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001): 공시 유형·상세유형·접수일·정정 표시를 포함한 목록 메타데이터
- [OpenDART 공시서류 원본파일](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019003): 접수번호 기반 원문 파일
- [OpenDART 정기보고서 주요정보 목록](https://opendart.fss.or.kr/guide/main.do?apiGrpCd=DS002): 주식·배당·채무·감사·주주·임직원 등 30종
- [OpenDART 단일회사 전체 재무제표](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS003&apiId=2019020): 연결·별도, 재무제표 구분, 당기·누적·전기 값
- [OpenDART 단일회사 주요 재무지표](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS003&apiId=2022001): 수익성·안정성·성장성·활동성 지표 분류
- [OpenDART 대량보유 상황보고](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS003&apiId=2019021): 보유 수·비율·증감·보고사유
- [OpenDART 주요사항보고서 주요정보 목록](https://opendart.fss.or.kr/guide/main.do?apiGrpCd=DS005): 주요사항보고서 36종
- [OpenDART 유상증자 결정](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS005&apiId=2020023): 신주 수, 증자 전 주식 수, 자금조달 목적
- [OpenDART 전환사채권 발행결정](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS005&apiId=2020033): 발행액, 이율·만기, 전환가액·주식 수·잠재 비율
- [OpenDART 배당에 관한 사항](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS002&apiId=2019005)
- [OpenDART 최대주주 현황](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS002&apiId=2019007)
- [OpenDART 감사인과 감사의견](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS002&apiId=2020009)

OpenDART 공식 구조는 내부 필드 사전의 누락을 줄이기 위한 참고자료다. 대회 평가 답변의 사실 근거와 검색 대상은 `manifest.jsonl`에 포함된 `CONTEST` 데이터로 제한한다.
