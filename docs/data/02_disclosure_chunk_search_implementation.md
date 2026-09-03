# 공시 청크·원문 근거 검색 구현

| 항목 | 내용 |
|---|---|
| 문서 ID | `DISCLOSURE-CHUNK-SEARCH-IMPLEMENTATION` |
| 문서 버전 | v1.0 |
| 작성일 | 2026-09-01 |
| 구현 버전 | `chunk-search-v1` |
| 문서 상태 | 구현 및 테스트 완료 |
| 관련 계약 | `docs/data/01_search_fact_evidence_logical_contract.md` |

## 1. 문서 목적

이 문서는 선택된 공시 문서 안에서 질문과 관련된 TEXT·TABLE 검색 청크를 찾고, 해당 청크가 만들어진 원문 위치까지 반환하는 검색 기능의 구현 결과를 기록한다.

이번 구현 범위는 다음과 같다.

```text
구조화된 청크 검색 조건
→ Concept·Fact Key 해석
→ 검색 대상 공시·문서 검증
→ 관련 청크 후보 검색
→ 관련도 점수 계산과 topK 선정
→ 원문 출처 일괄 조회
→ 청크 검색 결과 반환
```

이 기능은 자연어 답변을 직접 생성하지 않는다. QueryPlan 또는 상위 오케스트레이션 계층이 선택한 공시 안에서 LLM과 Fact 추출기가 사용할 근거 후보를 찾는 내부 검색 기능이다.

## 2. 구현 전후의 차이

### 2.1 구현 전

- 청킹 결과가 `disclosure_chunks`에 저장되어 있었다.
- 각 청크와 원본 ContentBlock의 관계가 `disclosure_chunk_sources`에 저장되어 있었다.
- 청크 검색 조건·결과 모델은 정의되어 있었지만 실제 DB 검색 흐름은 없었다.
- Concept과 Fact Key를 실제 공시 표현으로 변환하는 규칙이 없었다.

### 2.2 구현 후

- 내부 의미 코드인 Concept·Fact Key를 실제 검색어와 Section 힌트로 변환할 수 있다.
- 선택된 공시 범위 안에서 실제 PostgreSQL 청크 검색이 수행된다.
- 검색된 청크를 설명 가능한 항목별 점수로 정렬한다.
- 검색 결과에서 원본 ContentBlock, XML 행과 TABLE 행 범위로 역추적할 수 있다.
- 지원하지 않는 검색 조건과 미완료 문서는 추측하거나 숨기지 않고 예외 또는 경고로 반환한다.

## 3. 구현 파일과 책임

### 3.1 검색어 해석

| 파일 | 책임 |
|---|---|
| `DisclosureChunkSearchTermResolver` | Concept·Fact Key를 실제 검색어와 Section 힌트로 변환 |
| `ResolvedChunkSearchTerms` | 변환된 검색어, 해결·미해결 키와 경고를 전달 |

### 3.2 검색 실행

| 파일 | 책임 |
|---|---|
| `DisclosureChunkSearchRepository` | 청크 검색 저장소 계약 정의 |
| `JdbcDisclosureChunkSearchRepository` | PostgreSQL 범위 검증, 후보 검색, 점수 계산, 원문 출처 조회 |
| `DisclosureChunkSearchService` | 입력 검증, 검색어 해석, Repository 호출, 최종 결과와 경고 조립 |

### 3.3 테스트

| 파일 | 검증 범위 |
|---|---|
| `DisclosureChunkSearchTermResolverTest` | 검색어 매핑, 명시적 검색어 병합, 미지원 키 처리 |
| `DisclosureChunkSearchServiceTest` | Service 흐름, 검색 버전, 잘림과 경고 처리 |
| `DisclosureChunkSearchRepositoryIntegrationTest` | 실제 PostgreSQL 검색, 순위, 범위 제한, 원문 출처 역추적 |

## 4. 전체 실행 흐름

예시 질문은 다음과 같다.

```text
“선택한 신규시설투자 공시의 투자금액과 투자목적은 무엇인가?”
```

상위 계층은 이미 메타데이터 검색으로 관련 공시를 선택했다고 가정한다.

### 4.1 검색 조건 생성

QueryPlan 변환 계층은 다음 의미의 `DisclosureChunkSearchCondition`을 만든다.

```text
disclosureIds = 선택된 공시 ID
concepts = FACILITY_INVESTMENT
factKeys = facility.amount, facility.purpose
chunkTypes = TEXT, TABLE
topK = 5
neighborRadius = 0
```

`disclosureIds`는 필수다. 현재 청크 검색은 전체 공시를 대상으로 무제한 본문 검색하지 않는다.

## 5. QuestionPlan 검색 Step 연결

2026-09-01에 역할 A의 `QuestionPlan`과 실제 검색 Service 사이의 실행 연결을 추가했다.

### 5.1 연결 흐름

```text
QuestionPlan
→ DefaultDisclosureRetriever
→ s1 SEARCH_DISCLOSURES
→ DisclosureMetadataSearchService
→ s1 결과의 disclosureIds 보관
→ s2 SEARCH_EVIDENCE
→ disclosureIdsFrom으로 s1 결과 참조
→ DisclosureChunkSearchService
→ RetrievalResult 반환
```

`DefaultDisclosureRetriever`는 계획 순서대로 검색 Step을 실행하고 `stepId`별 실제 결과를 실행 문맥에 보관한다. `SEARCH_EVIDENCE.disclosureIdsFrom`에는 UUID 목록이 아니라 앞선 `SEARCH_DISCLOSURES`의 `stepId`가 들어간다.

현재 메타데이터 테이블과 검색 조건에는 보고기간 필드가 없으므로 `QuestionPlan.time` 중 `receiptPeriod`와 `asOf`만 실제 SQL 조건에 적용한다. `reportPeriod`가 아직 적용되지 않았다는 사실은 검색 결과 경고로 반환한다.

### 5.2 역할 A 입력 계약 보완

`SearchEvidenceInput`은 다음 정보를 전달한다.

| 필드 | 의미 |
|---|---|
| `disclosureIdsFrom` | 공시 ID를 제공하는 앞선 검색 Step ID |
| `concepts` | 질문의 상위 금융 개념 |
| `factKeys` | 찾으려는 Fact Key |
| `sectionHints` | 우선 탐색할 장·절 이름 |
| `keywords` | 본문·표에서 찾을 실제 표현 |
| `blockTypes` | 검색할 논리 근거 블록 유형 |
| `topK` | 반환할 근거 후보 상한 |

`QuestionPlanConverter`는 `disclosureIdsFrom`이 `dependsOn`에 포함되고 실제 앞선 `SEARCH_DISCLOSURES` Step을 가리키는지 검증한다.

### 5.3 결과 경계 보완

청크 검색에서 확보한 원문 위치가 역할 A 결과로 넘어가며 사라지지 않도록 `RetrievedEvidenceSource`를 추가했다. `RetrievedEvidence`에는 Section 경로와 다음 출처 목록을 함께 보존한다.

- 원본 ContentBlock ID
- XML 시작·종료 행
- 중첩 TABLE 경로
- TABLE 행 시작·종료 인덱스

검색 결과는 아직 검증된 Fact Evidence가 아니므로 `EvidenceStatus.CANDIDATE`로 반환한다.

### 5.4 실제 Bean과 Fake 분리

- 기본 Profile: `DefaultDisclosureRetriever`가 실제 PostgreSQL 검색 Service를 호출한다.
- `fake-retrieval` Profile: `FakeDisclosureRetriever`가 고정 골든 데이터를 반환한다.

두 구현체가 동시에 주입되지 않도록 상호 배타적인 Spring Profile을 사용한다.

### 5.5 연결 테스트

다음 내용을 테스트했다.

- QueryPlan 후보 JSON이 구체적인 `SearchEvidenceInput`으로 변환됨
- `disclosureIdsFrom`과 `dependsOn` 관계 검증
- `SEARCH_DISCLOSURES` 결과의 실제 공시 ID가 `SEARCH_EVIDENCE` 조건으로 전달됨
- 논리 block type이 TEXT·TABLE 청크 유형으로 변환됨
- 청크 검색 결과가 `RetrievedDocument`, `RetrievedEvidence`로 변환됨
- XML 행과 TABLE 행 범위가 `RetrievedEvidenceSource`에 보존됨
- 기존 PostgreSQL 메타데이터·청크 검색 통합 테스트 회귀 없음

### 5.6 아직 연결되지 않은 범위

현재 실제 연결 범위는 `SEARCH_DISCLOSURES`와 `SEARCH_EVIDENCE`다. 다음 도구는 경고만 남기고 실행하지 않는다.

- `LOOKUP_FACTS`
- `RESOLVE_DISCLOSURE_HISTORY`
- `CALCULATE`

또한 `OrchestrationAnswerService`는 아직 실제 LLM 계획 생성 결과가 아니라 고정 골든 계획을 사용한다. 자연어 질문부터 검색까지의 end-to-end 연결은 실제 QuestionPlan 생성기를 붙일 때 완료한다.
