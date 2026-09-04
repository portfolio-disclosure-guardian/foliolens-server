# FolioLens 역할 A 기능 명세서

| 항목 | 내용 |
|---|---|
| 문서 버전 | v0.2 |
| 문서 상태 | 현재 코드 기준 역할 A P0 잔여 작업 명세 |
| 검토 기준 | `develop` · `b17804c` + 2026-08-25 작업 트리 |
| 검토일 | 2026-08-25 |
| 대상 | AI 오케스트레이션, 평가 API, 답변 검증, 실행 추적, 제출 안정화 |

> 이 문서는 완료 이력을 보관하는 문서가 아니다. 이미 코드에 반영된 작업은 3장의 짧은 기준선만 남기고 구현 목록에서 제외한다. 상세 API·도구·데이터 스키마는 각 기준 문서에 두며, 이 문서에는 역할 A가 소유한 경계와 아직 끝나지 않은 작업만 둔다.

## 1. 문서 지위와 판정 규칙

### 1.1 우선순위

이 문서는 상위 요구사항을 재정의하지 않는 역할 A 전용 투영 문서다. 충돌 시 다음 순서를 따른다.

1. 사용자의 현재 명시적 요청
2. `요구사항_정의서.md`의 승인된 Must 요구사항
3. `기능명세서.md`
4. `IA.md`
5. `PROJECT_CONTEXT.md`
6. 현재 코드 관례

`TOOL_CONTRACTS.md`와 `DATA_CATALOG.md`는 각각 도구와 데이터의 상세 기준으로 참조하되, 위 우선순위를 뒤집지 않는다.

### 1.2 상태 표기

| 상태 | 의미 |
|---|---|
| `CODE_CONFIRMED` | 현재 소스에 구조가 존재함. 테스트 통과나 기능 완성을 뜻하지 않음 |
| `VERIFIED` | 이번 검토에서 명령 또는 자동 테스트로 확인함 |
| `REMAINING` | 역할 A가 구현하거나 검증해야 함 |
| `DEPENDENCY` | 역할 B 또는 C의 입력이 있어야 실제 데이터로 완료 가능 |
| `DECISION_REQUIRED` | 문서나 역할 간 합의가 먼저 필요함 |
| `EXTERNAL_PENDING` | 운영진 규격이 없어 현재 확정할 수 없음 |

구현 완료는 코드 존재가 아니라 해당 종료 조건을 검증하는 자동 테스트 통과로 판정한다.

## 2. 역할과 불변 경계

### 2.1 역할 A의 책임

- 평가 요청을 내부 명령으로 바꾸고 공통 답변 코어를 호출한다.
- HCX 계획 후보를 검증된 실행 계획으로 변환한다.
- 검색, 계산, 답변 생성, 검증의 순서·한도·실패 처리를 통제한다.
- HyperCLOVA X의 계획·답변 구조화 입출력과 오류를 격리한다.
- 최종 주장·수치·근거·금지 표현을 검증한다.
- 평가 응답, 실행 ID, 상태, 처리시간, 버전과 오류를 기록한다.
- 평가 profile, health/readiness와 제출 smoke test를 소유한다.

### 2.2 역할 B·C와의 경계

| 기능 | 역할 A | 역할 B | 역할 C |
|---|---|---|---|
| 기업·공시 검색 | 계획·한도·실행 순서 | 조회·검색·점수 | 필수 검색 범위 검토 |
| 사실·근거 | 검증 결과 소비·답변 연결 | 파싱·추출·원문 위치 | factKey·필수 근거 승인 |
| 계산 | 호출·답변 연결 | 결정적 계산 구현 | 공식·비교 가능성·반올림 승인 |
| 답변 | HCX 입력·조립·기계 검증 | 사용 근거 제공 | 표현·완전성·안전 기준 승인 |
| 골든 케이스 | 자동 실행 경로 | 검색·계산 fixture | 질문·기대값·판정 소유 |

역할 A는 역할 B의 JPA Entity·Repository를 직접 사용하지 않는다. 계획에는 `Company` Entity 대신 경계용 기업 참조 DTO를 사용한다.

### 2.3 불변 규칙

1. Spring이 실행 순서, 허용 도구, 한도, 계산과 최종 판정을 통제한다.
2. HCX 계획 후보는 Spring 검증 전 실행하지 않는다.
3. HCX에는 선택된 `CONTEST` 근거, 검증 fact와 계산 결과만 전달한다.
4. 금액·날짜·비율·증감률·기간을 HCX가 새로 계산하지 않는다.
5. FACT 주장은 evidence 또는 검증 fact, CALCULATION 주장은 계산 기록과 모든 입력 근거를 가져야 한다.
6. 검색 결과가 부족해도 OpenDART·뉴스·웹·다른 LLM으로 보완하지 않는다.
7. 미검증 답변 후보를 외부에 반환하지 않는다.
8. `think_trace`에는 원시 chain-of-thought, prompt, SQL, 토큰, 비밀값과 stack trace를 넣지 않는다.
9. 평가 어댑터와 이후 웹 어댑터는 같은 공통 서비스와 `AnswerResult`를 사용한다.
10. 첫 수직 슬라이스에 registry, factory, rule engine, 별도 Vector DB를 추가하지 않는다.

## 3. 현재 확인된 기반 — 다시 구현하지 않음

| 기반 | 현재 확인 | 판정 |
|---|---|---|
| 평가 진입점 | `EvaluationAnswerController`의 `GET /answer`와 `question_id`, `question` 파라미터 | `CODE_CONFIRMED` |
| 코어·어댑터 분리 | Controller가 `AnswerQuestionCommand`를 만들고, 서비스의 `AnswerResult`를 `EvaluationAnswerResponse.from`으로 매핑 | `CODE_CONFIRMED` |
| 외부 최상위 키 | `question_id`, `question`, `retrieved_context`, `think_trace`, `answer`의 Jackson 이름 | `CODE_CONFIRMED` |
| 평가 전용 예외 경계 | 파라미터·비즈니스·예상 밖 예외용 `EvaluationExceptionHandler` | `CODE_CONFIRMED` |
| 계획 도구 이름 | `ToolType`에 `SEARCH_DISCLOSURES`, `LOOKUP_FACTS`, `SEARCH_EVIDENCE`, `RESOLVE_DISCLOSURE_HISTORY`, `CALCULATE` | `CODE_CONFIRMED` |
| A-B 검색 seam | `DisclosureRetriever.retrieve(QuestionPlan)` | `CODE_CONFIRMED` |
| 질문 실행 저장 골격 | V5 `question_runs`, Entity, Repository, 생성 Service | `CODE_CONFIRMED` |
| 실행·DB 자산 | PostgreSQL, Flyway, Docker Compose, Actuator, read-only `/data` volume | `CODE_CONFIRMED` |
| Java 컴파일 | `backend\\gradlew.bat compileJava --no-daemon --console=plain` 성공 | `VERIFIED` |

위 항목은 기반만 존재한다. 실제 검색·계산·HCX·답변 검증이 연결됐다는 뜻은 아니다.

### 3.1 현재 테스트 기준선

> 2026-09-04 갱신(A9 작업): 이 절만 최신화했다. 문서 상단 메타데이터(검토일 2026-08-25)는 문서 전체 재검토 시점이라 그대로 둔다.

- 전체 테스트 콘솔 요약: 486개 중 실패 0, 에러 0. Docker 가용성 부족으로 인한 skip은 0건이다.
- skip 8건은 모두 이번 작업 이전부터 있던 의도적 opt-in 게이트다: `foliolens.test.dataset-root` 시스템 프로퍼티가 없으면 건너뛰는 HTML/PDF 코퍼스·청킹 테스트, `FOLIOLENS_ACTUAL_DB_AUDIT=true`가 아니면 건너뛰는 실 DB 감사 테스트.
- `OrchestrationAnswerServiceTest` 12/12 통과. GOLD-FACILITY-001~003이 모두 APPROVED로 바뀐 뒤 "승인 강제 시 검토중 케이스는 placeholder" 테스트가 운영 fixture를 더는 pending으로 가정하지 않도록 테스트 전용 C_REVIEW_PENDING 정책으로 분리했다.
- `OrchestrationAnswerRealDataIntegrationTest` 5/5 통과. 승인된 골든 케이스 3건(SK하이닉스·셀트리온·LG이노텍)을 모두 seed해 실제 PostgreSQL(Testcontainers)에서 COMPLETED·MATCH·기대 답변 일치까지 확인하는 테스트를 추가했다. 이 과정에서 `FakeHcxAnswerGenerator`가 질문과 무관하게 항상 `goldenCases().getFirst()`의 답변만 반환하던 결함을 고쳐 회사별로 올바른 답변이 나오게 했다.
- `disclosureData` HealthIndicator를 추가해 readiness 그룹(`readinessState,db,disclosureData`)이 Flyway V12 적용 여부와 승인된 골든 케이스의 필수 fact·evidence 존재까지 확인한다. 데이터가 없으면 `/actuator/health/readiness`가 더는 UP을 반환하지 않는다.
- 별도 Compose project(`foliolens-a9verify`)로 빈 PostgreSQL → `backup/foliolens-db.dump` 복원(disclosures 4204·facts 345·evidences 345, 원본과 일치) → 최신 이미지 기동을 재현해, Flyway가 12개 마이그레이션을 재실행 없이 validate하고 readiness가 UP이 되는 것을 확인했다(검증 후 project/volume 정리). README의 제출 프로필 절에 복원 명령을 추가했다.
- 남은 것: 실제 제출용 HCX 키(`HCX_APP_TYPE=serviceapp`)로 대표 질문 3건 전체 smoke. 인프라 전용 smoke(`submission-smoke.ps1 -InfrastructureOnly`)는 통과했지만 실 키 응답 품질 smoke는 키 보유자가 실행해야 한다.

## 4. 현재 코드의 잔여 차이

| 영역 | 현재 코드 | 남은 종료 조건 |
|---|---|---|
| 평가 근거 매핑 | `EvaluationAnswerResponse.from`이 `retrieved_context`를 항상 `[]`로 생성 | 실제 사용 evidence만 snake_case DTO로 매핑하고 계약 테스트 통과 |
| 실행 요약 | `think_trace`가 `List<String>` | 내부 `step`·`summary` 구조를 mapper에서 외부 규격으로 변환 |
| 공통 결과 | `AnswerResult`가 문서·문자열 요약·답변만 보유 | outcome, claims, used evidences, calculations, limitations, versions 추가 |
| 오케스트레이션 | 질문 run 생성 후 placeholder 문구 반환 | 계획→검색→계산→생성→검증 연결 |
| 계획 후보 | 회사는 문자열 mention, 기간은 중첩 날짜 범위, step input은 `JsonNode` | 버전·모호성·구조화 입력 검증 완료 |
| 검증 계획 | 접수·보고기간 `DateRange`와 기준일 `LocalDate` 적용, 회사·step 계약은 작업 중 | 회사 경계 DTO와 도구별 입력 타입 검증 완료 |
| 계획 검증 | 구현 없음 | schema, 기업, 기간, 도구, 의존성, 상한 검증 테스트 |
| 관심사 profile | `interestCodes` 필드만 있고 profile·검증 없음 | 첫 슬라이스 profile 하나와 명시 조건 비제거 규칙 검증 |
| 검색 결과 | `RetrievalResult`가 빈 record | 문서·fact·evidence·history·실행 결과·누락·경고·버전 반환 |
| 검색 문서 DTO | `RetrievedDocument`가 접수번호·출처·검색 방법 등 추적 필드 누락 | `TOOL_CONTRACTS.md`의 문서 경계 필드와 정렬 |
| 계산 경계 | 없음 | 검증 fact ID만 받는 `DisclosureCalculator` 한 개 추가 |
| 실행 상태 | run을 `PENDING`으로 저장한 뒤 상태를 바꾸지 않음 | 성공·실패 전이, 답변/오류/종료시각 기록 |
| channel | Entity 생성자가 `EVALUATION`을 고정 | `AnswerQuestionCommand.channel`을 저장까지 전달 |
| HCX | client, 설정, schema 없음 | 계획·답변 2개 호출, fake, 구조화 출력 검증 |
| 답변 검증 | 없음 | 수치·근거·계산·출처·금지 표현 검증 |
| 신뢰성 | deadline, 재검색·재생성 한도, 단계 로그 없음 | 설정 한도와 결정적 시나리오 테스트 |
| 오류 코드 | `DATASET_503_2`가 문자열 코드 `DATASET_503_1`을 중복 사용 | 코드 고유성 테스트와 값 수정 |
| 평가 profile | CONTEST 전용 Bean 경계와 데이터 readiness 없음 | 금지 외부 호출 0건과 준비 상태 검증 |
| 제출 검증 | 새 DB 기동·readiness·README 명령 재현은 검증 완료(3.1절) | 실제 제출용 HCX 키로 대표 질문 3건 전체 smoke만 남음 |

## 5. 역할 A가 소유하는 잔여 계약

상세 데이터의 필드 계약 부분은 `TOOL_CONTRACTS.md`를 참조하고, 평가의 5개 wire 필드 예시는 `API_명세서.md`와 `API_상세_명세서.md`를 참조한다. 이 문서들의 과거 구현 상태 스냅샷은 현재 사실로 사용하지 않는다. 여기서는 역할 A의 seam만 정리한다.

### 5.1 평가 어댑터

```http
GET /answer?question_id={externalQuestionId}&question={urlEncodedQuestion}
```

- 두 값은 trim 기준 비어 있지 않아야 한다.
- `question_id`는 평가 correlation 값이며 내부 `runId`와 다르다.
- 중복 정책이 확정되기 전에는 unique key나 cache key로 사용하지 않는다.
- 정상·부분·답변 불가는 동일한 5개 최상위 키와 HTTP 200을 사용한다.
- 요청 검증 실패는 400, 데이터 미준비는 503, deadline 초과는 504다.
- HCX 또는 최종 검증 실패의 외부 오류 body는 `EXTERNAL_PENDING`이다.

```json
{
  "question_id": "Q-001",
  "question": "질문 원문",
  "retrieved_context": [],
  "think_trace": [],
  "answer": "검증된 답변 또는 정보 한계"
}
```

`retrieved_context`에는 최종 claim이 실제 참조한 evidence만 포함한다. `think_trace`에는 완료된 공개 가능 단계만 포함한다. 두 필드의 최종 자료형·크기는 운영진 규격 확정 후 평가 mapper에서만 바꾼다.

### 5.2 계획 후보와 검증 계획 — 잠정 방향

현재 작업 방향은 별도 `IntentType` 필드를 두지 않고 필요한 작업을 `steps[].tool`과 구조화된 입력으로 표현하는 것이다. 다만 상위 문서 일부에는 아직 `intents`가 남아 있으므로 9.2의 문서 결정이 끝나기 전 아래 schema를 프로젝트 전체 확정 계약으로 부르거나 validator 구현을 고정하지 않는다.

`QuestionPlanCandidate` 최소 구조:

- `schemaVersion`
- `companies[].mention`
- `time.receiptPeriod`, `time.reportPeriod`, `time.asOf`
- 필요한 경우 `interestCodes`
- `steps: List<PlanStepCandidate>`
- `ambiguities`

검증된 `QuestionPlan` 최소 구조:

- 지원되는 `schemaVersion`
- `ResolvedCompanyRef` 목록
- 검증된 접수기간·보고기간·기준시점
- 적용한 profile ID·version
- `List<PlanStep>`
- 정규화·범위 축소 warning

`PlanStep`은 문자열 `stepId`, `ToolType tool`, `dependsOn: List<String>`와 도구별 구조화 input을 가진다. 후보 step과 검증 step을 같은 record로 재사용하지 않는다. JPA Entity와 자유 형식 `String input`을 계획 계약에 넣지 않는다.

Spring 검증기는 다음만 수행한다.

1. schema version과 도구 enum 확인
2. 기업 표현을 유일한 내부 ID로 해소
3. 접수기간·보고기간·`asOf` 분리와 데이터 범위 확인
4. step ID 중복·누락 참조·순환 의존성 거부
5. 도구별 입력·이전 출력 참조·계산 binding 확인
6. 단계 수·문서 수·`limit`·`topK` 상한 적용
7. 승인된 형식 정규화와 안전한 상한 축소 기록

질문에 없던 기업·기간·공시 유형·계산을 추가하지 않는다.

### 5.3 A-B-C seam

첫 슬라이스는 다음 두 포트만 사용한다.

```text
DisclosureRetriever.retrieve(QuestionPlan) -> RetrievalResult
DisclosureCalculator.calculate(CalculationCommand, List<RetrievedFact>) -> CalculationResult
```

`RetrievalResult`는 최소한 documents, facts, evidences, history, executedSteps, missingFactKeys, coverage, warnings, retrievalVersion을 반환한다. 모든 fact는 실제 evidence ID를 가져야 하고, 계산 입력은 `VERIFIED` fact ID로 제한한다.

단계별 interface, registry와 factory는 두 번째 실행 구현이 실제로 필요해질 때까지 만들지 않는다.

역할 C는 첫 슬라이스 전에 versioned `AnswerPolicy` 또는 동등한 불변 fixture로 다음을 제공한다.

- 필수 fact와 fact별 최소 evidence
- 허용 계산, 공식, 단위, 반올림과 허용 오차
- 완료·부분·답변 불가 판정 기준
- claim별 근거 연결 규칙
- 금지 표현 코드
- 골든 케이스

### 5.4 실행 상태와 답변 결과를 분리

한 enum에 실행 실패와 근거 충족도를 섞지 않는다. 아래 `AnswerOutcome`은 역할을 설명하기 위한 목표 이름이며 현재 코드나 상위 문서에서 승인된 타입은 아니다.

| 축 | 값 | 의미 |
|---|---|---|
| `QuestionRunStatus` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` | 실행 생명주기 |
| `AnswerOutcome` | `COMPLETED`, `PARTIAL`, `UNANSWERABLE` | 정상 실행 뒤 답변 충족도 |

- `UNANSWERABLE`은 `QuestionRunStatus.COMPLETED`와 함께 올 수 있는 정상 결과다.
- 시스템·DB·HCX·deadline 실패는 `QuestionRunStatus.FAILED`와 오류 응답으로 처리하고 정상 `AnswerResult`의 outcome으로 만들지 않는다.
- 세부 단계는 먼저 구조화 로그와 안전한 execution summary로 기록한다. P1 진행 UI가 실제로 필요해지기 전 실행 상태 enum을 세분화하지 않는다.

목표 `AnswerResult`:

- `runId`, `externalQuestionId`, `originalQuestion`
- `AnswerOutcome outcome`
- `renderedAnswer`
- `claims`, `usedEvidences`, `calculations`, `limitations`
- `safeExecutionSummary`
- 데이터·검색·계산·prompt·model `versions`

### 5.5 HCX와 최종 검증

역할 A가 직접 소유하는 모델 호출은 두 개뿐이다.

| 호출 | 입력 | 출력 |
|---|---|---|
| `QUESTION_PLAN` | 질문, 후보 schema, 허용 도구 | `QuestionPlanCandidate` |
| `ANSWER_COMPOSITION` | 검증 계획, fact, evidence, 계산, 한계 | `AnswerCandidate` |

비정형 fact 추출은 역할 B 경계다. 다른 LLM fallback과 모델 주도 URL·SQL·도구 실행은 금지한다.

최종 검증 순서:

1. 답변 후보 JSON Schema
2. 기업·날짜·금액·비율과 검증 fact 일치
3. 계산 공식·결과·입력 fact 일치
4. claim 유형별 evidence·calculation 참조 무결성
5. 접수번호·문서·원문 위치 유효성
6. 정정·후속 관계와 `asOf`
7. 모든 근거의 `sourceProvider=CONTEST`
8. 매수·매도, 목표주가, 가격 확률과 수익 보장 표현 차단
9. `usedEvidences`가 실제 claim 참조의 합집합인지 확인

### 5.6 오케스트레이션과 한도

```text
요청 검증
→ QuestionRun 생성·PROCESSING
→ HCX 계획 후보
→ Spring 계획 검증
→ DisclosureRetriever
→ 필요한 경우 DisclosureCalculator
→ 근거 충분성 판정
→ HCX 답변 후보
→ Spring 최종 검증
→ AnswerResult 또는 실패 응답
→ QuestionRun 종료 기록
```

- 계획 생성: 1회
- 조건을 바꾼 근거 재검색: 추가 최대 1회
- 검증 실패 후 답변 재생성: 추가 최대 1회
- 동일 입력의 동일 검색 반복 금지
- 전체 deadline이 개별 retry보다 우선

## 6. 첫 수직 슬라이스

### 6.1 기준 질문

> SK하이닉스가 2024년 4월 발표한 신규시설투자의 투자금액과 목적은 무엇이고, 자기자본 대비 비율은 맞는가?

기준 공시는 접수번호 `20240424800596`이다.

### 6.2 현재 dependency 차이

- 역할 B의 DART XML 파서·section/block 저장은 진척됐지만 이 기준 공시는 `exchange` HTML이다.
- 현재 저장소에는 해당 HTML의 facility fact 추출기, fact/evidence 저장, 실제 retriever와 계산기가 없다.
- 역할 C의 시설투자 반올림·허용 오차, 상태 판정과 골든 케이스도 코드에서 확인되지 않는다.

역할 A는 이 dependency를 기다리며 멈추지 않는다. 같은 경계 DTO를 사용하는 고정 fake/fixture로 오케스트레이션과 평가 계약을 먼저 완성하고, 실제 Role B 구현이 준비되면 adapter만 교체한다.

### 6.3 완료 조건

- `GET /answer`가 정확한 5개 최상위 키를 반환한다.
- `question_id`와 질문 원문이 보존된다.
- 실제 통합 단계에서는 `20240424800596`과 사용 문맥이 `retrieved_context`에 있다.
- 투자금액·자기자본·비율이 검증 fact·calculation과 일치한다.
- 모든 FACT·CALCULATION claim이 원문 evidence로 역추적된다.
- fake 기반 계약 테스트와 실제 PostgreSQL/Docker smoke를 각각 통과한다.

## 7. 남은 구현 순서

완료된 경로 생성과 코어·DTO 분리는 목록에서 제거했다.

| 순서 | 잔여 작업 | 종료 조건 |
|---:|---|---|
| A1 | 테스트 기준선 복구 | PostgreSQL 테스트 환경에서 `contextLoads` 통과, 평가 MVC 테스트는 DB 없이 실행 |
| A2 | 평가 mapper 완성(DONE) | 비어 있지 않은 `retrieved_context`, 구조화 `think_trace`, 접수일 형식, snake_case와 5개 키 계약 테스트 통과 |
| A3 | 계획 계약·검증기(DONE) | 후보/검증 DTO 분리, 구조화 input, 기업·기간·의존성·상한과 시설투자 profile 테스트 통과 |
| A4 | A-B-C fixture 경계(DONE) | `RetrievalResult`, document/evidence/fact/calculation DTO, calculator port, `AnswerPolicy` fixture 확정 |
| A5 | 한 질문 fake 수직 연결(DONE) | fake HCX·retriever·calculator로 완료·부분·불가·실패 시나리오 통과 |
| A6 | HCX 최소 연동(DONE) | 계획·답변 구조화 출력 schema와 timeout 연동 테스트 통과 |
| A7 | 검증·신뢰성·추적(DONE) | 참조 무결성, 금지 표현, deadline, retry, 세 ID·처리시간·버전·오류 코드, run 전이와 redaction 테스트 통과 |
| A8 | 실제 데이터 연결(DONE) | exchange HTML fact/evidence/calculation adapter로 기준 질문 통과 |
| A9 | 제출 환경 | 새 DB Docker 기동, readiness, 대표 질문 smoke와 README 명령 재현 |

첫 범위를 통과하기 전 비교·이력의 범위 확장, P1/P2, cache, 비동기 API와 범용 workflow framework를 추가하지 않는다.

## 8. 필수 자동 검증

### 8.1 평가 계약

- 정상 요청 200
- `question_id`, `question` 누락·빈 값·공백 400
- 한글·특수문자 URL decoding
- 정확한 5개 최상위 키와 웹 `ApiResponse` 미사용
- ID·질문 원문 보존
- 비어 있지 않은 근거의 nested snake_case 직렬화
- 답변 불가의 HTTP 200과 동일 schema

### 8.2 계획

- 미지원 schema·도구·연산 거부
- 기업 미해소·복수 후보 처리
- 접수기간·보고기간·`asOf` 분리
- 중복·순환 step과 없는 출력 참조 거부
- tool input과 계산 binding 검증
- 단계·문서·`topK` 상한
- 알 수 없는 관심사 profile 거부와 profile이 질문의 명시 조건을 제거하지 않음

### 8.3 오케스트레이션·상태

- `COMPLETED`, `PARTIAL`, `UNANSWERABLE`의 근거 기준
- 데이터·DB·HCX·deadline 실패 시 run `FAILED`
- run이 응답 뒤 `PENDING`에 남지 않음
- command channel, plan, answer 또는 error와 종료시각 저장
- request ID·external question ID·run ID 상관관계와 단계별 처리시간·버전·고유 오류 코드 기록
- 재검색·재생성 한도와 동일 검색 반복 금지
- 미검증 후보 외부 노출 금지

### 8.4 안전·통합

- 알 수 없는 evidence·fact·calculation ID 거부
- 모델이 바꾼 금액·날짜·비율 거부
- `think_trace`와 로그의 prompt·chain-of-thought·비밀·stack trace 차단
- 평가 profile의 OpenDART·뉴스·검색엔진·다른 LLM 호출 0건
- 투자 권유·목표주가·가격 확률·수익 보장 표현 차단
- PostgreSQL·Flyway·Docker smoke

## 9. 결정과 문서 동기화

### 9.1 결정 대기

| 항목 | 상태 | 확정 전 처리 |
|---|---|---|
| 평가 인증·오류 body | `EXTERNAL_PENDING` | mapper 경계 유지, 추측 금지 |
| `retrieved_context`, `think_trace` 최종 타입·크기 | `EXTERNAL_PENDING` | 내부 구조형을 유지하고 평가 mapper에서만 변환 |
| 제한시간·동시 요청 수 | `EXTERNAL_PENDING` | 설정 경계만 두고 수치 고정 금지 |
| HCX 모델 ID·endpoint·인증·호출 한도 | `EXTERNAL_PENDING` | 환경변수 경계, 다른 모델 우회 금지 |
| `facility.*` 대 `investment.*` | `DECISION_REQUIRED` | 묵시적 alias·중복 저장 금지 |
| 시설투자 반올림·허용 오차 | `DECISION_REQUIRED` | 승인 전 비율 일치 `COMPLETED` 판정 금지 |
| 별도 intent 필드 제거 | `DECISION_REQUIRED` | 현재 변경을 보존하되 상위 문서 동기화 후 계획 schema 고정 |
| 답변 충족도 타입·이름 | `DECISION_REQUIRED` | 실행 상태와 분리하되 `AnswerOutcome` 이름은 승인 전 목표명으로만 사용 |
| 중복 `question_id` 정책 | `DECISION_REQUIRED` | correlation 값, 매 요청 새 run |
| 최대 문서·step·topK | `DECISION_REQUIRED` | 설정값으로 두고 골든셋 측정 후 확정 |

### 9.2 함께 고쳐야 할 레거시 문서

이 요청에서는 `ROLE_A_SPEC.md`만 수정한다. 다음 불일치는 별도 문서 동기화 작업으로 남긴다.

| 문서 | 남은 불일치 |
|---|---|
| `data-ingestion/03_role_a_progress_and_remaining_work.md` | 역할 A 과거 진행 보고서임을 명시하거나 보관 문서로 이동 필요 |

## 10. 역할 A P0 완료 정의

다음을 모두 만족해야 역할 A P0가 완료다.

1. 전체 필수 테스트가 PostgreSQL 환경에서 통과하고 Docker-dependent 검증이 skip되지 않는다.
2. `GET /answer` 입력·5개 응답 키·오류 계약 테스트가 통과한다.
3. 계획 후보와 검증 계획이 분리되고 미허용 입력을 실행하지 않는다.
4. 대표 질문이 실제 공시 `20240424800596`의 fact·evidence·calculation으로 답변된다.
5. 모든 핵심 주장과 수치가 실제 원문 위치로 역추적된다.
6. 정상 실행의 `COMPLETED`, `PARTIAL`, `UNANSWERABLE`과 실행 `FAILED`가 구분된다.
7. 금지 외부 데이터와 다른 LLM 호출이 0건이다.
8. request ID, external question ID, run ID, 처리시간, 버전과 오류를 진단할 수 있다.
9. Docker 새 DB smoke와 README 재현 절차가 통과한다.
10. 역할 B가 경계 DTO를, 역할 C가 fact·계산·근거·표현 기준과 골든 케이스를 승인한다.
