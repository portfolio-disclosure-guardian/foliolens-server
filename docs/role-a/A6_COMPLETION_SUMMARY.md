# A6 완료 요약: HCX 최소 연동

| 항목 | 내용 |
|---|---|
| ROLE_A_SPEC 7절 기준 | `계획·답변 구조화 출력 schema와 timeout 연동 테스트 통과` |
| 상태 | **DONE** ([ROLE_A_SPEC.md:316](ROLE_A_SPEC.md#L316)) |
| 관련 커밋 | `4e196a1` feat)add HcxPlans and compelete Answer on A6, `5fc9d93` feaure)complete A6 and A7 |
| 작성일 | 2026-09-03 |

이 문서는 A6 범위(HCX 실연동)만 다룬다. 같은 커밋(`5fc9d93`)에는 A7(참조 무결성·안전검증·deadline·retry) 작업도 함께 들어있지만 그 상세는 이 문서의 범위 밖이다.

## 1. 공통 HCX 클라이언트 계층

[ClovaChatClient.java](../../backend/src/main/java/com/foliolens/backend/answer/hcx/ClovaChatClient.java) — 계획·답변 두 호출이 공유하는 chat-completions 요청/응답 처리를 한 곳으로 모았다.

### 실호출로 확정한 계약 (설명회 자료와 다름)

- 엔드포인트는 `/{appType}/v3/chat-completions/{model}` — `appType` segment가 없으면 `40001 Invalid parameter`. 테스트 키는 `testapp`, 서비스 키는 `serviceapp`.
- 응답 필드는 `stopReason`이 아니라 `finishReason`이며 `created`/`seed`가 추가로 온다 → 응답 record에 `@JsonIgnoreProperties(ignoreUnknown = true)`로 방어.

### 타임아웃 테스트가 잡은 실제 버그

JDK 내장 `HttpServer`로 응답을 일부러 지연시키는 로컬 서버를 띄워 `read-timeout-ms` 설정이 실제로 `AGENT_504_1`로 이어지는지 테스트했다([ClovaChatClientTest.java](../../backend/src/test/java/com/foliolens/backend/answer/hcx/ClovaChatClientTest.java)). 처음 실행에서 실패했다 — 이 Spring 버전은 read timeout을 `ResourceAccessException`이 아니라, 상태 코드를 읽는 시점에 지연 발생하는 일반 `RestClientException`으로 감싸서 던진다. 기존 catch 블록(`RestClientResponseException`, `ResourceAccessException`)이 이걸 못 잡아 처리되지 않은 예외가 새고 있었다.

수정: `RestClientException`을 추가로 잡고, 원인 체인에 `SocketTimeoutException`이 있는지(`hasCause`)로 504/502를 구분한다.

```java
} catch (RestClientException e) {
    if (hasCause(e, SocketTimeoutException.class)) {
        throw new BusinessException(ErrorCode.AGENT_504_1, "HCX 호출이 시간 내에 끝나지 않았습니다.", e);
    }
    throw new BusinessException(ErrorCode.AGENT_502_1, "HCX 호출을 처리하지 못했습니다.", e);
}
```

## 2. 답변 생성 (ANSWER_COMPOSITION)

[ClovaStudioHcxAnswerGenerator.java](../../backend/src/main/java/com/foliolens/backend/answer/hcx/ClovaStudioHcxAnswerGenerator.java) — `AnswerPolicy`(정답 제외)·`RetrievalResult`·`CalculationResult`·`AnswerOutcome`을 system 프롬프트로 직렬화해 `ClovaChatClient.chat(...)`을 호출한다. `goldenCase.expectedAnswer()`는 프롬프트에 직렬화하지 않는다(정답 유출 방지).

- [FakeHcxAnswerGenerator.java](../../backend/src/main/java/com/foliolens/backend/answer/FakeHcxAnswerGenerator.java) — `hcx.api.enabled=false`(기본값)일 때만 활성화. 두 구현체는 `@ConditionalOnProperty`로 상호배타.

## 3. 계획 생성 (QUESTION_PLAN)

- [HcxPlanGenerator.java](../../backend/src/main/java/com/foliolens/backend/question/plan/HcxPlanGenerator.java) — `generatePlan(String question): QuestionPlanCandidate` 포트.
- [ClovaStudioHcxPlanGenerator.java](../../backend/src/main/java/com/foliolens/backend/answer/hcx/ClovaStudioHcxPlanGenerator.java) — `QuestionPlanCandidate` JSON 스키마를 system 프롬프트에 명시해 요청, 모델이 코드블록으로 감싸 응답해도 첫 `{`~마지막 `}`만 추출해 파싱한다. 실호출 검증: 골든 질문으로 스키마와 정확히 일치하는 JSON(`SEARCH_DISCLOSURES`→`LOOKUP_FACTS` 2단계 계획)을 실제로 받는 것을 확인했다.
- [FakeHcxPlanGenerator.java](../../backend/src/main/java/com/foliolens/backend/question/plan/FakeHcxPlanGenerator.java) — **구현 중 발견한 빈 등록 공백**을 메운 것. `HcxAnswerGenerator`와 달리 `HcxPlanGenerator`는 원래 Fake 짝이 없었다 — `hcx.api.enabled=false`(기본값)로 뜨면 빈을 못 찾아 컨텍스트가 안 뜨는 상태였다. `GoldFacility001Fixture.questionPlanCandidate()`(신설)를 그대로 반환하도록 구현했다.

### 오케스트레이션 배선

[OrchestrationAnswerService.java](../../backend/src/main/java/com/foliolens/backend/orchestration/OrchestrationAnswerService.java)가 더 이상 고정 계획을 쓰지 않는다.

```java
QuestionPlanCandidate planCandidate = hcxPlanGenerator.generatePlan(command.question());
QuestionPlan plan = questionPlanConverter.candidateToConfirmation(planCandidate);
RetrievalResult retrieval = disclosureRetriever.retrieve(plan);
```

이를 위해 [QuestionPlanConverter.candidateToConfirmation](../../backend/src/main/java/com/foliolens/backend/question/plan/QuestionPlanConverter.java)을 package-private → `public`으로 열었다(다른 패키지인 orchestration에서 호출).

**알려진 한계**: `hcx.api.enabled=true` + 실제 `DefaultDisclosureRetriever`(B 구현) 조합에서는 검색은 실행되지만 `LOOKUP_FACTS`가 아직 미구현이라 `retrieval.facts()`가 항상 비어 `COMPLETED`까지 가지 않는다 — A8(실제 데이터 연결) 영역이라 이번 범위 밖에 남겨뒀다. Fake 프로필에서는 기존과 동일하게 완전히 동작한다.

## 4. 설정

`application.yml`:

```yaml
hcx:
  api:
    enabled: ${HCX_API_ENABLED:false}
    base-url: ${HCX_API_BASE_URL:https://clovastudio.stream.ntruss.com}
    api-key: ${HCX_API_KEY:}
    model: ${HCX_MODEL:HCX-005}
    app-type: ${HCX_APP_TYPE:testapp}
    connect-timeout-ms: ${HCX_CONNECT_TIMEOUT_MS:3000}
    read-timeout-ms: ${HCX_READ_TIMEOUT_MS:30000}
    max-tokens: ${HCX_MAX_TOKENS:1024}
    temperature: ${HCX_TEMPERATURE:0.5}
    top-p: ${HCX_TOP_P:0.8}
```

`HCX_API_ENABLED=false`(기본값)면 두 Fake 구현체만 활성화되어 기존 테스트가 그대로 통과한다. 실 키 없이도 전체 스위트가 돈다.

## 5. 테스트

| 파일 | 검증 내용 |
|---|---|
| `ClovaChatClientTest` | 성공 응답 파싱, 실패 status 코드, HTTP 에러, **read-timeout 실동작**(4번째 테스트, 로컬 slow server) |
| `ClovaStudioHcxAnswerGeneratorTest` | `ClovaChatClient` 위임, system 프롬프트에 정답 미포함 |
| `ClovaStudioHcxPlanGeneratorTest` | JSON 파싱, 코드블록 감싼 JSON 파싱, 비-JSON/깨진 JSON → `BusinessException` |
| `HcxAnswerGeneratorWiringTest` | `enabled` 플래그별로 답변·계획 생성기 각각 정확히 하나(Fake 또는 실제)만 활성화되는지 실제 Spring 컨텍스트로 확인 |
| `OrchestrationAnswerServiceTest` | 계획 생성이 포함된 전체 흐름에서 골든 케이스가 여전히 `COMPLETED`를 반환하는지 회귀 확인 |

전체 스위트 260개 테스트, 0 실패(Docker 의존 16개는 기존과 동일하게 skip).

## 6. ROLE_A_SPEC 완료 기준 대조

| 기준 | 상태 |
|---|---|
| 계획 구조화 출력 schema | ✅ 실호출 검증 |
| 답변 구조화 출력 schema | ✅ 실호출 검증 |
| timeout 연동 테스트 | ✅ 로컬 slow server로 실동작 검증, 버그 1건 발견·수정 |

A6는 위 세 조건을 모두 충족해 완료 처리한다.
