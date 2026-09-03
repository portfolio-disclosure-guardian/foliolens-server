# HCX(CLOVA Studio) 실연동 작업 프롬프트

> 이 문서는 Claude Code 세션에 그대로 전달하는 작업 지시서입니다.
> 사전 지식 없이 이 문서만으로 작업이 가능하도록 필요한 사실을 모두 담았습니다.

## 목표

`FakeHcxAnswerGenerator`로 동작 중인 답변 생성부를 실제 네이버 CLOVA Studio(HCX) API 호출로 교체할 수 있게 만든다.
플래그 하나(`HCX_API_ENABLED`)로 Fake ↔ 실호출을 전환하며, 실 키 없이도 기존 테스트가 전부 통과해야 한다.

## 확정된 API 사실 (2026-09-01 실호출 1회로 검증 완료)

- **엔드포인트**: `POST https://clovastudio.stream.ntruss.com/{appType}/v3/chat-completions/{model}`.
  `{appType}`은 테스트 키면 `testapp`, 서비스 앱 신청 후 서비스 키로 바꾸면 `serviceapp`이다 (설명회 자료에는 이 경로 segment가 빠져 있었고,
  이것 없이 호출하면 `40001 Invalid parameter`로 실패한다 — 실호출로 확인). 모델은 `HCX-005` 사용.
- **인증**: `Authorization: Bearer nv-****` 헤더 하나. OAuth 토큰 교환·client-id/secret **없음**.
- **헤더**:
  - `Content-Type: application/json`
  - `Accept: application/json` (비스트리밍. `text/event-stream`이면 SSE — 이번 작업에서는 사용하지 않음)
  - `X-NCP-CLOVASTUDIO-REQUEST-ID: <uuid>` (선택, 요청 추적용)
- **요청 바디** — v3는 `content`가 문자열이 아니라 **타입 배열**:

```json
{
  "messages": [
    { "role": "system", "content": [{ "type": "text", "text": "…" }] },
    { "role": "user",   "content": [{ "type": "text", "text": "…" }] }
  ],
  "topP": 0.8, "topK": 0, "maxTokens": 1024, "temperature": 0.5
}
```

- **응답 바디** (실호출로 확인한 실제 구조 — 설명회 자료의 `stopReason` 표기는 틀렸고 실제는 `finishReason`이며, `created`/`seed` 필드가 추가로 온다):

```json
{
  "status": { "code": "20000", "message": "OK" },
  "result": {
    "message": { "role": "assistant", "content": "…" },
    "finishReason": "stop",
    "created": 1788288762,
    "seed": 2121657598,
    "usage": { "promptTokens": 0, "completionTokens": 0, "totalTokens": 0 }
  }
}
```

## 현재 리포 상태

- 연동 지점: `backend/src/main/java/com/foliolens/backend/answer/HcxAnswerGenerator.java` 인터페이스.
  구현체는 `FakeHcxAnswerGenerator`뿐이며, `orchestration/OrchestrationAnswerService.java`가 호출한다. 파이프라인은 건드리지 않는다.
- `backend/src/main/resources/application.yml` 맨 아래 `hcx:` 블록(96행~)은 **OAuth로 잘못 가정된 설정이고 YAML도 깨져 있다**. 통째로 교체 대상.
- `.env.example`(리포 루트)에 HCX 변수가 없다. `.gitignore`는 이미 `.env`를 제외하고 있다.
- Spring Boot 4.1 + `spring-boot-starter-webmvc` → **`RestClient` 내장. 새 의존성 추가 금지.**
- `global/web/RequestCorrelationFilter.currentRequestId()`가 요청 상관 ID를 제공한다. HCX 호출 시 `X-NCP-CLOVASTUDIO-REQUEST-ID`로 그대로 전달할 것.

## 작업 내용

### 1. `application.yml`의 `hcx:` 블록 교체

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

`@ConfigurationProperties(prefix = "hcx.api")` record로 바인딩한다 (예: `answer/hcx/HcxApiProperties.java`).

### 2. `.env.example`에 추가

```
# CLOVA Studio (HCX) — 키는 발급 시 1회만 노출되므로 즉시 .env에 보관. 절대 커밋 금지
HCX_API_ENABLED=false
HCX_API_KEY=
HCX_MODEL=HCX-005
HCX_APP_TYPE=testapp
```

### 3. 실구현 `ClovaStudioHcxAnswerGenerator` 작성

- 위치: `backend/src/main/java/com/foliolens/backend/answer/hcx/` (요청/응답 DTO 포함)
- `@ConditionalOnProperty(name = "hcx.api.enabled", havingValue = "true")`
- `RestClient` 사용, connect/read 타임아웃은 설정값 적용 (응답 시간이 대회 평가 요소).
- 프롬프트 구성: `system` 메시지에 정책·검색 컨텍스트(`RetrievalResult`)·계산 결과(`CalculationResult`)·`AnswerOutcome`을 직렬화해 담고, `user` 메시지에 질문 원문을 담는다. 프롬프트 템플릿은 단순 문자열 조립으로 시작 (템플릿 엔진 금지).
- 응답은 `result.message.content`를 반환. `status.code`가 성공이 아니거나 HTTP 4xx/5xx면 예외를 삼키지 말고 `BusinessException`(적절한 `ErrorCode`, 필요 시 신규 코드 추가)으로 변환해 던진다. 재시도 로직은 넣지 않는다.
- **로그에 API 키와 프롬프트 원문을 남기지 않는다** (개인정보 마스킹은 대회 필수 요건).

### 4. Fake 전환 조건 부여

`FakeHcxAnswerGenerator`에 `@ConditionalOnProperty(name = "hcx.api.enabled", havingValue = "false", matchIfMissing = true)` 추가.
플래그 미설정 시 지금과 완전히 동일하게 동작해야 한다.

### 5. 테스트

- 실 API를 호출하는 테스트는 만들지 않는다 (키 없음, CI에서 못 돈다).
- `MockRestServiceServer` 또는 `RestClient.Builder` 목킹으로 `ClovaStudioHcxAnswerGenerator` 단위 테스트 1개:
  성공 응답 파싱, 에러 status 코드 → 예외 변환, 요청 헤더(Bearer, Content-Type, REQUEST-ID) 검증.
- 기존 테스트 전체 통과 확인 (`./gradlew test`, backend 디렉토리에서).

## 제약

- 새 라이브러리 의존성 추가 금지 (RestClient·Jackson으로 충분).
- 주석·커밋 메시지는 한국어, 코드 식별자는 영어.
- 시크릿 하드코딩 금지. 테스트 픽스처에도 실제 키 형태(`nv-` 실키) 넣지 말 것.
- 스트리밍(SSE), 재시도, 서킷브레이커, 프롬프트 템플릿 엔진은 **범위 외**. 요청받기 전에 만들지 말 것.
- 대회 규칙: 사용자 응답 자연어 생성은 HCX만 허용. 다른 LLM API(Claude/GPT) 호출 코드 금지.

## 완료 기준

1. `HCX_API_ENABLED` 미설정/false → 기존 Fake 동작 그대로, 전체 테스트 통과.
2. `HCX_API_ENABLED=true` + 키 설정 시 컨텍스트가 정상 기동하고 `ClovaStudioHcxAnswerGenerator` 빈이 활성화됨.
3. 신규 단위 테스트가 요청 형식(URL·헤더·바디)과 응답 파싱·에러 변환을 검증.
4. `git grep nv-` 결과에 실키 없음.

## 작업 후 사람이 할 일 (코드 밖)

1. ~~네이버 클라우드 콘솔 → CLOVA Studio → API 키에서 **테스트 키** 발급, 즉시 `.env`의 `HCX_API_KEY`에 저장 (1회만 노출됨).~~ 완료.
2. ~~curl로 실호출 1회 → 응답 스키마가 위 가정과 다르면 DTO 수정.~~ 완료 (2026-09-01). `appType` 경로 누락과 `finishReason` 필드명을 이때 발견해 수정함.
3. 최종 배포 전 "서비스 앱 신청" 후 **서비스 키**로 교체하고 `HCX_APP_TYPE=serviceapp`으로 변경.
4. 평가용 API 서버 엔드포인트 등록·https 도메인 요건은 대회 추후 공지 대기.
