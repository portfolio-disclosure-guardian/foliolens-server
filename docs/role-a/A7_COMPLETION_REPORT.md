# A7 검증·신뢰성·추적 완료 보고서

## 1. 상태

- 완료 단계: `A7 검증·신뢰성·추적`
- 기준 문서: [`ROLE_A_SPEC.md`](./ROLE_A_SPEC.md) 5.4~5.6절, 7절, 8.3~8.4절
- 기준 커밋: `5fc9d93` (`feaure)complete A6 and A7`)
- 최종 검증일: 2026-09-03

A7에서 답변 후보가 외부로 반환되기 전에 근거·수치·표현을 검증하고, 실행 한도와 전체 deadline을 적용하며, 실행 상태와 안전한 진단 정보를 남기는 경계를 완성했다.

## 2. 최종 처리 흐름

```text
요청 접수
→ QuestionRun 생성(PENDING)·시작(PROCESSING)
→ HCX 계획 후보 1회 생성
→ Spring 계획 검증
→ 검증된 QuestionPlan 저장
→ 공시·근거 검색
→ VERIFIED fact/evidence/document만 선별
→ 결정적 계산
→ claim·참조 무결성 검증
→ HCX 답변 생성
→ 금지 표현·금액·날짜·비율 검증
→ AnswerResult 반환 및 run COMPLETED
  또는 오류 코드 저장 및 run FAILED
```

핵심 오케스트레이션은 [`OrchestrationAnswerService`](../../backend/src/main/java/com/foliolens/backend/orchestration/OrchestrationAnswerService.java)에 있다.

## 3. 구현 내용

### 3.1 검증된 근거만 사용하는 경계

[`AnswerReferenceValidator`](../../backend/src/main/java/com/foliolens/backend/answer/AnswerReferenceValidator.java)는 검색 결과에서 `VERIFIED` fact와 evidence만 남기고, 그 evidence에 연결된 document만 HCX 입력으로 전달한다.

- 미검증 fact/evidence/document와 검색 warning 제거
- fact가 참조하는 evidence와 document 존재 여부 확인
- 계산 입력 fact가 `VERIFIED`인지 확인
- claim의 fact/evidence/calculation 참조 무결성 확인
- 실제 claim이 사용한 document만 최종 `usedEvidences`로 반환

### 3.2 답변 안전 검증

[`AnswerSafetyValidator`](../../backend/src/main/java/com/foliolens/backend/answer/AnswerSafetyValidator.java)는 HCX가 만든 문자열 답변을 다음 순서로 검증한다.

1. null·공백 답변 거부
2. `AnswerPolicy.forbiddenExpressions`와 `GoldenCase.criticalErrors` 문구 차단
3. 원화 금액이 검증된 KRW fact와 일치하는지 확인
4. 날짜·연월이 검증된 DATE fact 또는 공시 접수일과 일치하는지 확인
5. 비율이 검증 fact 또는 백엔드 계산 결과와 일치하는지 확인

검증 실패는 `AGENT_502_1`로 처리하며 미검증 답변은 외부에 반환하지 않는다.

### 3.3 검색·생성 횟수 제한

[`QuestionPlanConverter`](../../backend/src/main/java/com/foliolens/backend/question/plan/QuestionPlanConverter.java)가 검색 계획을 검증한다.

- 계획 생성은 요청당 1회
- 동일 tool·동일 input 검색 반복 금지
- 조건을 바꾼 추가 검색은 전체 계획에서 최대 1회
- 한도 위반 계획은 `QUESTION_400_4`로 거부

답변은 최초 생성 후 안전 검증에 실패한 경우에만 한 번 더 생성한다. 두 번째 검증도 실패하면 `AGENT_502_1`로 종료한다.

### 3.4 전체 deadline

답변 처리 파이프라인은 Java 21 가상 스레드의 `FutureTask`로 실행하며 전체 deadline이 개별 재시도보다 우선한다.

- 설정: `FOLIOLENS_ANSWER_DEADLINE_MS`
- 기본값: `30000ms`
- 적용 범위: 계획 생성·검증·저장, 검색, 계산, 답변 생성·검증
- 초과 처리: 실행 취소, 추가 시도 중단, `AGENT_504_1`, run `FAILED`
- HCX connect/read timeout도 [`ClovaChatClient`](../../backend/src/main/java/com/foliolens/backend/answer/hcx/ClovaChatClient.java)에서 `AGENT_504_1`로 변환

별도 실행 스레드에는 [`RequestCorrelationFilter`](../../backend/src/main/java/com/foliolens/backend/global/web/RequestCorrelationFilter.java)가 request ID를 전파한다.

### 3.5 QuestionRun 저장과 상태 전이

[`QuestionRun`](../../backend/src/main/java/com/foliolens/backend/question/entity/QuestionRun.java)과 [`QuestionRunService`](../../backend/src/main/java/com/foliolens/backend/question/service/QuestionRunService.java)가 다음 값을 저장한다.

- request ID, external question ID, run ID
- 원 질문과 요청 channel
- 검증 완료 `QuestionPlan` JSON
- 최종 검증 답변 또는 고유 오류 코드
- `startedAt`, `completedAt`, 전체 처리시간
- `PENDING → PROCESSING → COMPLETED|FAILED` 상태 전이

종료된 run의 재전이는 거부한다. `UNANSWERABLE`은 정상 결과이므로 run 상태는 `COMPLETED`다.

### 3.6 구조화 추적과 redaction

오케스트레이션 로그는 질문·답변 원문 없이 다음 값만 기록한다.

- request ID, external question ID, run ID
- `PLANNING`, `RETRIEVAL`, `CALCULATION`, `ANSWER_GENERATION`, `VALIDATION`
- 단계별 `durationMs`
- dataset, plan schema, retrieval, policy, HCX model 버전
- 검색 document/evidence 수, 계산 operation/verdict, 생성 attempt
- 최종 outcome 또는 오류 코드

external question ID의 개행·탭은 로그 기록 전에 제거한다. [`EvaluationExceptionHandler`](../../backend/src/main/java/com/foliolens/backend/evaluation/exception/EvaluationExceptionHandler.java)는 예외 객체·메시지·stack trace를 기록하거나 응답에 노출하지 않는다.

### 3.7 평가 profile 격리

[`application-evaluation.yml`](../../backend/src/main/resources/application-evaluation.yml)은 평가 실행에서 적재·profiling·parsing·chunking 배치를 모두 비활성화한다. `HCX_API_ENABLED=true`를 명시하기 전에는 `RestClient`, `ClovaChatClient`, 실제 HCX 계획·답변 generator가 생성되지 않고 fake 구현만 활성화된다.

## 4. 설정

| 환경변수 | 기본값 | 용도 |
|---|---:|---|
| `FOLIOLENS_REQUIRE_APPROVED_GOLDEN_CASE` | `false` | 승인된 골든 케이스만 허용할지 결정 |
| `FOLIOLENS_ANSWER_DEADLINE_MS` | `30000` | 질문 처리 전체 deadline |
| `HCX_API_ENABLED` | `false` | 실제 HCX client 활성화 |
| `HCX_CONNECT_TIMEOUT_MS` | `3000` | HCX 연결 timeout |
| `HCX_READ_TIMEOUT_MS` | `30000` | HCX 응답 timeout |
| `HCX_MODEL` | `HCX-005` | HCX 모델 및 추적 버전 값 |

`GOLD-FACILITY-001`이 아직 `C_REVIEW_PENDING`이므로 승인 강제 기본값은 `false`다.

## 5. 자동 검증 근거

| 계약 | 주요 테스트 |
|---|---|
| 참조 무결성·VERIFIED 필터 | `AnswerReferenceValidatorTest` |
| 금지 표현·금액·날짜·비율 | `AnswerSafetyValidatorTest` |
| 검색 횟수·동일 입력 차단 | `QuestionPlanConverterTest` |
| deadline·재생성·추적·미검증 후보 차단 | `OrchestrationAnswerServiceTest` |
| run 상태·계획·답변·오류·처리시간 | `QuestionRunTest` |
| HCX HTTP/read timeout | `ClovaChatClientTest` |
| 평가 profile 외부 client 0건 | `HcxAnswerGeneratorWiringTest` |
| 예외 메시지·stack trace redaction | `EvaluationExceptionHandlerTest` |
| 오류 코드 고유성 | `ErrorCodeTest` |

검증 명령:

```powershell
cd backend
.\gradlew.bat test --rerun-tasks --no-daemon
```

검증 결과: `BUILD SUCCESSFUL`.

## 6. 현재 경계

- 답변 계약은 아직 구조화 `AnswerCandidate`가 아니라 문자열 `renderedAnswer`다. 따라서 수치 검증은 단위가 붙은 원화·비율과 일반적인 날짜 표현을 대상으로 한다.
- 금지 표현 검증은 현재 정책 문구의 직접 포함 여부를 검사한다. 동의어·의미 기반 분류는 정책 데이터가 구체화될 때 확장한다.
- 재검색 한도는 한 번 생성된 계획 안의 검색 step을 기준으로 강제한다. 계획을 다시 생성하지 않으므로 “계획 생성 1회” 계약을 유지한다.
- parser·prompt·calculator의 독립 버전 타입은 아직 없다. 현재 추적 로그에는 코드에서 확정 가능한 dataset·plan schema·retrieval·policy·model 버전만 기록한다.

이 경계들은 A7 완료를 막는 미구현 기능이 아니라 현재 문자열 답변 및 단일 계획 계약에서 의도적으로 제한한 범위다.
