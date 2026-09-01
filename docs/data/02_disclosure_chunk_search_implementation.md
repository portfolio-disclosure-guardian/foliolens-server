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
