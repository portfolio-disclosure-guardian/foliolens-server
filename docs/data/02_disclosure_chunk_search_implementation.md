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

### 4.2 검색어 해석

`DisclosureChunkSearchTermResolver`는 내부 의미 코드를 원문에서 검색할 수 있는 표현으로 변환한다.

```text
facility.amount
→ 투자금액, 투자 금액, 투자규모

facility.purpose
→ 투자목적, 투자 목적

FACILITY_INVESTMENT
→ 시설투자, 설비투자
```

관련 Section 힌트도 함께 추가한다.

```text
신규시설투자
투자내역
기타 투자판단에 참고할 사항
```

호출자가 직접 전달한 `keywords`, `sectionHints`는 삭제하지 않고 해석 결과와 중복 없이 합친다.

지원하지 않는 Concept이나 Fact Key는 비슷한 표현으로 임의 변환하지 않는다. `unresolvedConcepts`, `unresolvedFactKeys`와 `warnings`에 기록한다.

### 4.3 검색 범위 검증

`JdbcDisclosureChunkSearchRepository`는 검색 전에 다음 조건을 검증한다.

1. 요청한 공시가 실제로 존재하는가?
2. 요청한 공시가 `source_provider=CONTEST`인가?
3. `documentIds`를 지정했다면 해당 문서가 요청한 공시에 속하는가?
4. 대상 문서의 파싱과 청킹 상태는 어떠한가?

다른 공시에 속한 문서를 검색 조건에 섞으면 예외가 발생한다. 파싱 또는 청킹이 완료되지 않은 문서는 검색 대상에서 제외하고 그 개수를 경고로 반환한다.

### 4.4 청크 후보 검색

검색 대상은 다음 조건으로 제한한다.

- `source_provider=CONTEST`
- 요청한 `disclosureIds`
- 요청 시 지정한 `documentIds`
- `chunk_status=COMPLETED`
- 요청한 청크 유형 또는 기본 `TEXT`, `TABLE`

키워드는 `disclosure_chunks.search_text`에서 찾고, Section 힌트는 `section_path`에서 찾는다. 해석 가능한 검색어와 Section 힌트가 하나도 없으면 조건을 넓혀 전체 청크를 반환하지 않고 빈 결과를 반환한다.

### 4.5 관련도 점수 계산

후보 청크에는 다음 점수를 합산한다.

| 점수 항목 | 현재 기준 |
|---|---:|
| 공시명에서 키워드 일치 | 키워드당 `0.25` |
| Section 경로에서 힌트 일치 | 힌트당 `1.5` |
| `body_text`에서 키워드 직접 일치 | 키워드당 `1.0` |
| `search_text`에서만 키워드 일치 | 키워드당 `0.5` |
| 공백이 포함된 구문이 `body_text`에 일치 | 구문당 `0.5` |
| Fact 힌트 검색어가 `search_text`에 일치 | 검색어당 `0.75` |
| 정정공시 가중치 | 현재 `0.0` |

최종 점수가 같으면 최신 접수일, 문서 내 청크 순번, 청크 ID 순으로 결과를 안정적으로 정렬한다.

점수는 Fact의 진실 여부가 아니라 질문과 관련될 가능성을 나타낸다. 높은 검색 점수만으로 Fact를 `VERIFIED` 상태로 만들지 않는다.

### 4.6 topK 적용

점수 계산 전후의 수를 구분한다.

```text
candidateChunkCount = 조건에 맞는 전체 후보 수
items = topK 적용 후 실제 반환한 청크 목록
```

후보가 반환 결과보다 많으면 `truncated=true`가 되고, topK로 일부 후보가 제외되었다는 경고가 추가된다.

### 4.7 원문 출처 연결

선택된 상위 청크의 ID를 모아 `disclosure_chunk_sources`를 한 번에 조회한다. 청크마다 별도 쿼리를 실행하지 않아 N+1 조회를 피한다.

각 검색 결과에는 다음 원문 위치가 연결된다.

- 원본 `contentBlockId`
- 청크 내부 출처 순서
- 문서 내 원본 Block 순번
- 원문 시작·종료 행
- 중첩 TABLE 경로
- TABLE 원본 행 시작·종료 인덱스

이 출처는 최종 검증된 Evidence가 아니라 Fact 추출기가 원문을 다시 확인하기 위한 위치 참조다.

### 4.8 결과 반환

최종 `DisclosureChunkSearchResult`에는 다음 정보가 포함된다.

| 필드 | 의미 |
|---|---|
| `items` | 관련도순 청크 결과 |
| `searchedDisclosureIds` | 실제 검색 대상으로 요청한 공시 ID |
| `searchedDocumentCount` | 청킹 완료 상태로 실제 검색한 문서 수 |
| `candidateChunkCount` | topK 적용 전 후보 수 |
| `truncated` | topK 때문에 일부 후보가 제외됐는지 여부 |
| `warnings` | 미지원 검색 키, 미완료 문서, 결과 제한 등의 정보 |
| `retrievalVersion` | 적용한 검색 정책 버전 `chunk-search-v1` |

각 `DisclosureChunkSearchHit`은 다음 핵심 정보를 가진다.

```text
기업·공시·원문 문서 정보
청크 유형과 문서 내 순번
Section 전체 경로
bodyText
searchText
최종 점수와 항목별 점수
실제로 일치한 검색어
원문 출처 목록
청크 생성 버전과 검색 버전
```

`bodyText`는 LLM 또는 Fact 추출기에 전달할 근거 후보 본문이다. `searchText`는 상위 Section·표 문맥 등이 포함된 검색 보조 문자열이므로 원문 그대로라고 표시하지 않는다.

## 5. Service의 역할

`DisclosureChunkSearchService`는 검색 알고리즘을 직접 구현하는 대신 전체 실행 순서를 관리한다.

```text
DisclosureChunkSearchCondition
→ neighborRadius 지원 범위 확인
→ DisclosureChunkSearchTermResolver.resolve()
→ DisclosureChunkSearchRepository.search()
→ truncated 계산
→ 해석기·Repository 경고 통합
→ DisclosureChunkSearchResult 반환
```

검색 정책 버전은 `chunk-search-v1`으로 고정하여 결과를 어떤 규칙으로 만들었는지 추적할 수 있게 했다.

## 6. 메타데이터 검색 관련 변경

청크 검색 전에 수행하는 공시 메타데이터 검색에서 제목 조건의 역할을 조정했다.

- `rawSubtype`, `category`, `sourceGroup` 중 하나 이상이 있으면 `titleTerms`는 점수 계산에만 사용한다.
- 공시 유형 조건이 전혀 없을 때만 `titleTerms`를 필터로 사용한다.

이 변경은 구조화된 공시 유형이 정확히 지정됐는데 공시 제목 표현이 조금 다르다는 이유로 관련 공시가 누락되는 것을 줄이기 위한 것이다.

## 7. 예외와 경고 정책

### 7.1 예외로 중단하는 경우

- `disclosureIds`가 비어 있음
- 검색 신호가 하나도 없음
- `topK` 또는 `neighborRadius`가 모델 허용 범위를 벗어남
- `IMAGE_CAPTION`을 검색 대상으로 요청함
- 존재하지 않거나 `CONTEST`가 아닌 공시를 요청함
- 요청한 문서가 요청한 공시에 속하지 않음
- `chunk-search-v1`에서 `neighborRadius`가 0이 아님

### 7.2 결과와 함께 경고하는 경우

- 지원하지 않는 Concept 또는 Fact Key가 포함됨
- 파싱 미완료 문서가 있음
- 청킹 미완료 문서가 있음
- 해석 가능한 검색어 또는 Section 힌트가 없음
- topK 때문에 후보 일부가 제외됨

구조적으로 잘못된 요청은 예외로 막고, 검색 가능한 범위 안에서 일부 품질 또는 데이터 누락이 있는 경우에는 결과와 경고를 함께 반환한다.

## 8. 테스트 결과

### 8.1 단위 테스트

- 명시적 키워드와 도메인 매핑 검색어가 정상적으로 합쳐지는지 확인
- 지원하지 않는 Concept·Fact Key를 추측하지 않는지 확인
- Service가 해석기와 Repository를 올바른 순서로 호출하는지 확인
- 검색 버전과 `truncated`, 경고가 정상 조립되는지 확인
- 미지원 `neighborRadius`가 Repository 호출 전에 거부되는지 확인

### 8.2 PostgreSQL 통합 테스트

Testcontainers PostgreSQL 16 환경에서 다음을 확인했다.

- 투자금액·투자목적 청크가 높은 순위로 검색됨
- topK 적용 전 후보 수와 `truncated` 값이 일치함
- 원문 행과 TABLE 행 범위가 실제 출처 모델로 반환됨
- 문서명이 없을 때 파일명을 대신 사용함
- 파싱·청킹 미완료 문서가 제외되고 경고가 반환됨
- 다른 공시에 속한 문서를 검색 범위에 넣으면 거부됨
- 미지원 Fact Key가 전체 청크 검색으로 이어지지 않음

검색 관련 전체 테스트 실행 결과는 다음과 같다.

```text
.\gradlew.bat test --tests "*Disclosure*Search*"

BUILD SUCCESSFUL
```

## 9. 현재 완료 범위

- [x] 청크 검색 조건·결과·출처 모델
- [x] 시설투자 Concept·Fact Key 검색어 해석기
- [x] 실제 PostgreSQL 청크 검색 Repository
- [x] 검색 범위와 문서 상태 검증
- [x] 설명 가능한 관련도 점수 계산
- [x] topK와 잘림 처리
- [x] 원문 ContentBlock·행·TABLE 범위 역추적
- [x] 내부 검색 Service
- [x] 단위 테스트와 PostgreSQL 통합 테스트
- [ ] 자연어 질문을 QueryPlan으로 변환하는 LLM 연결
- [ ] 메타데이터 검색과 청크 검색을 자동으로 연결하는 오케스트레이션
- [ ] 외부 또는 평가 API Controller
- [ ] 검색 결과 기반 Fact 추출·검증
- [ ] 검색 결과 기반 최종 답변 생성

## 10. 현재 제한사항

### 10.1 시설투자 우선 지원

검색어 해석기의 도메인 매핑은 첫 수직 구현 대상인 신규시설투자를 우선 지원한다. 다른 공시 유형은 금융 도메인 규칙과 골든 질문을 확정한 뒤 확장해야 한다.

### 10.2 문자열 검색 기준선

현재 후보 검색은 PostgreSQL의 문자열 포함 비교를 사용하는 lexical baseline이다. 선택된 공시 범위 안에서 실행되므로 첫 수직 검증에는 사용할 수 있지만, 대규모 품질 평가 후 다음 도입 여부를 판단해야 한다.

- PostgreSQL 전문검색
- 한국어 형태소 기반 토큰화
- 검색 인덱스
- 동의어 사전의 코드 외부화
- 대회 정책이 허용하는 임베딩 또는 재정렬 모델

### 10.3 이웃 청크 미지원

검색 조건 모델은 `neighborRadius=0~2`를 표현할 수 있지만 현재 결과 모델에는 이웃 청크를 본 결과와 구분해 담는 구조가 없다. `chunk-search-v1`은 값을 무시하지 않고 0만 허용한다.

### 10.4 정정 가중치 미적용

현재 검색 조건에는 `FactQueryMode`가 연결되어 있지 않아 정정공시 가중치는 0이다. `AS_FILED`, `LATEST_AS_OF`, `FULL_HISTORY` 실행 정책을 오케스트레이션과 연결할 때 확장한다.

### 10.5 내부 Service 단계

DB를 실제 조회하는 검색 기능은 구현됐지만 HTTP API는 아직 없다. 현재는 다른 Spring Service 또는 향후 검색 오케스트레이터가 `DisclosureChunkSearchService.search()`를 호출해야 한다.

## 11. 다음 작업

권장 구현 순서는 다음과 같다.

1. 대표 시설투자 골든 질문으로 실제 적재 DB 검색 결과를 확인한다.
2. 정답 원문 청크가 top 10에 포함되는지 측정한다.
3. 검색 결과에서 TABLE 행까지 정확히 역추적되는지 확인한다.
4. 공시 메타데이터 검색과 청크 검색을 연결하는 검색 오케스트레이션 Service를 구현한다.
5. QueryPlan의 `SEARCH_DISCLOSURES`, `SEARCH_EVIDENCE` 입력을 검색 조건으로 변환한다.
6. `facility.amount`, `facility.purpose` Fact 추출과 Evidence 검증을 수직 구현한다.
7. 실제 검색 품질 결과를 바탕으로 점수, 동의어와 인덱스를 개선한다.

첫 번째 완료 목표는 다음과 같다.

```text
FAC-Q02 질문
→ 대상 시설투자 공시 선택
→ 투자금액·투자목적 청크가 top 10에 포함
→ 각 청크에서 원본 TABLE 행까지 역추적
→ Fact 추출기가 검증 가능한 근거 후보로 사용
```
