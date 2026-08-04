# 역할 A 작업 현황과 잔여 범위

| 항목 | 내용 |
|---|---|
| 문서 목적 | 역할 A가 수행한 작업과 앞으로 수행할 작업을 구분하고, 역할 A 전용 요구사항·기능명세서의 기준 자료로 사용 |
| 역할 | AI 오케스트레이션·평가 API·배포 |
| 기준 브랜치 | `feature/add-plans-and-responses` |
| 기준 커밋 | `4d5fab8` (`feat) add annotations`) |
| 기준일 | 2026-08-04 |
| 코드 검증 | `compileJava` 성공, `test` 실패(1건 중 1건 실패) |
| 종합 상태 | 계약과 코드 골격 구현 단계. 대표 질문의 전체 실행 경로는 아직 동작하지 않음 |

## 1. 결론

역할 A는 평가 API, 질문 실행 기록, 질문 계획, 검색 경계와 공통 답변 결과의 코드 골격을 만들었고, 공시 QA 도구 계약을 문서화했다.

그러나 현재 상태를 기능 완료로 판단할 수는 없다. 애플리케이션 컨텍스트 테스트가 실패하고, 실제 endpoint는 요구 계약과 다르며, 검색·HyperCLOVA X·계산·검증이 연결되지 않았다. 따라서 현재 위치는 다음과 같다.

```text
계약 초안과 클래스 골격
→ 현재 위치
→ 평가 API 계약 복구와 테스트
→ 단일 공시 질문 수직 슬라이스
→ 비교·계산·정정 이력
→ 오류·관측·Docker 제출 안정화
```

역할 A P0의 완료 기준은 “대표 질문 하나가 대회 제공 공시 근거와 함께 `GET /answer`로 반환되고, 계약·오류·Docker 테스트가 통과하는 상태”다. 현재는 이 완료 기준 전이다.

## 2. 판단 기준

이 문서는 다음 상태를 구분한다.

| 상태 | 의미 |
|---|---|
| 완료 | 코드·계약·자동 테스트·실행 환경에서 검증됨 |
| 부분 완료 | 클래스나 계약은 있으나 실제 경로 또는 테스트가 미완성 |
| 문서 완료 | 설계 문서는 존재하나 팀 승인 또는 런타임 구현이 별도로 필요 |
| 미착수 | 현재 저장소에서 구현 근거를 찾지 못함 |
| 검증 실패 | 코드는 컴파일되지만 테스트 또는 실행 검증에 실패 |
| 외부 확정 대기 | 운영진 공지나 다른 역할의 계약이 먼저 필요 |

컴파일 성공만으로 기능 완료로 처리하지 않는다. 요구사항 정의서의 완료 정의에 따라 정상·오류·정보 부족 테스트, 근거 연결, 로그, Docker 재현까지 확인돼야 한다.

## 3. 역할 A의 공식 책임

역할 분담 문서에서 역할 A의 최종 책임은 “질문부터 최종 답변까지 전체 실행 경로”다.

| 책임 영역 | 역할 A가 맡는 내용 | 주요 요구사항 |
|---|---|---|
| 평가 API | `GET /answer`, 요청 파라미터, 5개 응답 필드, 평가 전용 오류 매핑 | `FR-EVAL-001~010`, `AC-009` |
| 질문 오케스트레이션 | 질문 계획, 검색·추출·계산 호출 순서, 상태 전이 | `FR-QUERY-001~008`, 기능명세 `FS-P0-04` |
| HyperCLOVA X | 계획 후보와 답변 생성, 구조화 출력 검증, 다른 LLM 우회 금지 | `IR-010~015`, 기능명세 9장 |
| 답변 조립 | 실제 사용 근거, 안전한 실행 요약, 최종 답변을 공통 결과로 조립 | `FR-ANS-001~013`, `FS-P0-09~11` |
| 장애 대응 | 시간 제한, 제한 재시도, 부분 답변, 오류 코드 | `NFR-REL-001~005`, 기능명세 13장 |
| 요청 추적 | 내부 실행 ID, 상태, 단계별 시간, 오류와 버전 기록 | `FR-OPS-003`, `NFR-OBS-001~005` |
| 배포 | Docker 재현 환경, health/readiness/liveness, README | `FR-OPS-001~008`, `NFR-MNT-004` |

데이터 적재·파싱·검색 구현은 역할 B가 주 담당이고, 금융 규칙·골든셋·답변 검수는 역할 C가 주 담당이다. 역할 A는 두 결과를 받아 전체 실행 경로로 연결하고 평가 계약으로 변환한다.

## 4. 지금까지 수행한 작업

### 4.1 공통 실행·응답 경계 작성 - 부분 완료

다음 계약과 골격을 만들었다.

- `AnswerQuestionCommand`: 외부 질문 ID, 질문 원문, 요청 채널
- `AnswerResult`: 공통 엔진이 반환할 내부 최종 결과
- `DisclosureAnswerService`: 질문 처리 진입 서비스
- `DisclosureRetriever`: 역할 A와 역할 B 사이의 검색 경계
- `RetrievedDocument`, `RetrievalResult`: 검색 결과 계약의 초기 형태

근거 파일:

- `backend/src/main/java/com/foliolens/backend/question/AnswerQuestionCommand.java`
- `backend/src/main/java/com/foliolens/backend/answer/AnswerResult.java`
- `backend/src/main/java/com/foliolens/backend/orchestration/DisclosureAnswerService.java`
- `backend/src/main/java/com/foliolens/backend/retrieval/DisclosureRetriever.java`

현재 한계:

- `DisclosureAnswerService`가 공통 `AnswerResult`가 아니라 평가 전용 `EvaluationAnswerResponse`를 직접 반환한다.
- `DisclosureRetriever`는 구현체가 없고 서비스에서 호출되지 않는다.
- `RetrievalResult`는 빈 record다.
- 검색·계산·생성·검증을 수행하지 않고 빈 목록과 미연결 안내 문구를 반환한다.

### 4.2 평가 API Controller와 DTO 작성 - 부분 완료, 계약 불일치

작성된 코드:

- `EvaluationAnswerController`
- `EvaluationAnswerResponse`
- `RetrievedContextResponse`

현재 Controller는 `question_id`와 `question`을 받고 서비스 응답을 반환한다. `retrieved_context`와 `think_trace`를 위한 JSON 이름도 일부 지정했다.

하지만 현재 실제 계약은 명세와 다르다.

| 항목 | 요구 계약 | 현재 코드 | 판단 |
|---|---|---|---|
| endpoint | `GET /answer` | `GET /api/v1/answer` | 불일치 |
| 질문 ID | `question_id` | `question_id` | 일치 |
| 질문 원문 | `question` | `questionText` | 불일치 |
| 검색 근거 | `retrieved_context` | `retrieved_context`지만 원소가 빈 record | 부분 |
| 실행 요약 | `think_trace` | 빈 `List<String>` | 부분 |
| 최종 답변 | `answer` | `answerText` | 불일치 |
| 빈 문자열 검증 | 400 | 검증 annotation과 명시적 검사 없음 | 미구현 |
| 계약 테스트 | 정상·누락·공백·오류 | 관련 테스트 없음 | 미구현 |

`@RequestParam` 때문에 파라미터 자체가 누락된 요청은 Spring이 400으로 처리할 수 있다. 그러나 `question_id=` 또는 공백 질문의 의미 검증은 구현되지 않았다.

### 4.3 질문 실행 기록 모델 작성 - 부분 완료, 실행 불가

다음 파일을 작성했다.

- `QuestionRun`
- `QuestionRunStatus`
- `QuestionRunRepository`
- `RequestChannel`

의도한 범위는 외부 질문 ID, 질문 원문, 채널, 상태, 계획 JSON, 최종 답변, 오류 코드와 완료 시각을 한 실행에 기록하는 것이다.

현재 한계:

- `question_runs` Flyway 마이그레이션이 없다.
- `ddl-auto: validate`이므로 테이블이 없으면 애플리케이션 기동 검증을 통과할 수 없다.
- `answerText` 문자열 필드에 `@JoinColumn`이 사용돼 있어 JPA 매핑 수정이 필요하다.
- 상태 enum은 `PENDING`, `PROCESSING`, `FAILED`, `COMPLETED`만 있어 명세의 `RECEIVED`부터 `VALIDATING`, `PARTIAL`, `UNANSWERABLE`까지의 상태와 다르다.
- 상태 변경 메서드, 실행 생성 규칙, 중복 `question_id` 정책과 Repository 조회가 없다.

따라서 현재는 엔티티 초안이며 요청 추적 기능 완료가 아니다.

### 4.4 질문 계획과 도구 선택 골격 작성 - 부분 완료

작성된 코드:

- `QuestionPlan`
- `QueryPlan`
- `PlanStep`
- `ToolName`
- `DisclosureSearchCommand`

현재 `QuestionPlan`에는 질문 유형, 기업명, 기간, 공시 유형, 지표, 도구, 명확화 여부가 있다. 검색 모듈로 전달할 필터 계약도 일부 작성했다.

현재 한계:

- `QueryPlan`은 빈 record다.
- `QuestionType`은 `COMPARISON`, `CALCULATION`만 있어 사실 조회·이력·종합·답변 불가 유형이 없다.
- `PlanStep`과 `QueryPlan`은 실제 서비스에서 사용되지 않는다.
- 코드의 도구 이름과 `TOOL_CONTRACTS.md`에서 확정한 5개 논리 도구 이름이 일치하지 않는다.
- HyperCLOVA X의 계획 후보와 백엔드 검증 결과가 분리돼 있지 않다.
- 기업 ID 확정, 접수기간·보고기간·기준시점 구분, 허용 enum과 단계 수 검증이 없다.

### 4.5 공시 QA 도구 계약 문서 작성 - 문서 완료, 팀 승인·구현 필요

`docs/TOOL_CONTRACTS.md`에 다음 내용을 정리했다.

- QueryPlan이 선택할 5개 논리 도구
  - `SEARCH_DISCLOSURES`
  - `LOOKUP_FACTS`
  - `SEARCH_EVIDENCE`
  - `RESOLVE_DISCLOSURE_HISTORY`
  - `CALCULATE`
- 기업 식별, 사실 추출, 사실·답변 검증은 항상 수행하는 내부 단계로 분리
- 접수기간, 보고기간과 기준시점 분리
- 검증된 `factId`만 계산 입력으로 사용
- 대회 `CONTEST` 데이터만 평가 근거로 사용
- 관심사별 별도 도구 대신 라우팅 프로필 사용
- 첫 수직 슬라이스와 현재 구현 상태 구분

이 문서는 역할 A 전용 기능명세서의 질문 계획·도구 실행 장에 재사용할 수 있다. 다만 문서의 목표 계약을 현재 런타임 기능으로 표현하면 안 된다.

### 4.6 공통 JPA 시간 엔티티 작성 - 완료된 공통 기반, 역할 A 핵심 기능은 아님

`BaseCreatedEntity`, `BaseTimeEntity`를 작성했고 현재 Company, Disclosure, QuestionRun의 감사 필드 기반으로 재사용되고 있다.

다만 현재 `BackendApplication`과 `JpaConfig`에 `@EnableJpaAuditing`이 중복돼 테스트가 실패한다. 공통 기반 코드는 존재하지만 현재 애플리케이션 기동 기준으로는 검증 실패 상태다.

### 4.7 과거 ERD·Owner·JWT 작업 - 현재 P0 완료량에서 제외

2026-07-23~27 Git 이력에는 ERD, 시간 엔티티, Owner 도메인과 JWT 보안 기반 작업이 있다.

현재 판단:

- 현재 소스 트리에는 당시 Owner·JWT 구현이 남아 있지 않다.
- 현재 공식 우선순위에서 로그인·소유권은 P2다.
- 평가 API에는 일반 서비스 JWT를 자동 적용하면 안 된다.
- 과거 작업 이력으로는 기록하되 현재 역할 A P0 완료량에는 포함하지 않는다.

## 5. Git 이력 기준 작업 묶음

현재 HEAD에서 확인한 역할 A 작성 커밋 중 P0와 직접 관련된 묶음은 다음과 같다.

| 기간 | 커밋 | 수행 내용 |
|---|---|---|
| 2026-08-02 | `3138a3d` | 생성·수정 시간 공통 엔티티 |
| 2026-08-02 | `4be512b`, `bfc70f9`, `46a5233` | 공통 답변·질문 계획·검색 경계·평가 API 초기 골격 |
| 2026-08-03 | `a853f3c` | JPA 감사 annotation 수정 |
| 2026-08-03 | `2d5cbd0` | `TOOL_CONTRACTS.md` 작성 |
| 2026-08-03 | `35f77e8` | `QuestionRun`, 요청 채널, 계획 단계와 도구 enum |
| 2026-08-03 | `40031ba`, `9b71576` | 평가 응답 DTO, Controller와 서비스 반환 형태 수정 |
| 2026-08-04 | `c816329`, `4d5fab8` | 질문 실행·응답 모델 정리와 필드 설명 추가 |

커밋이 존재한다는 사실과 기능 완료는 다르다. 이 표는 수행 이력이고, 완료 판단은 4장과 6장의 실행 검증을 따른다.

## 6. 현재 검증 결과

### 6.1 Java 컴파일

실행 명령:

```powershell
cd backend
.\gradlew.bat compileJava --no-daemon
```

2026-08-04 확인 결과:

```text
BUILD SUCCESSFUL
```

판단: 현재 소스는 Java 컴파일을 통과한다.

### 6.2 자동 테스트

실행 명령:

```powershell
cd backend
.\gradlew.bat test --no-daemon
```

2026-08-04 확인 결과:

```text
1 test completed, 1 failed
BackendApplicationTests > contextLoads() FAILED
BeanDefinitionOverrideException: jpaAuditingHandler
```

원인:

- `BackendApplication`에 `@EnableJpaAuditing`
- `JpaConfig`에도 `@EnableJpaAuditing`
- 동일한 `jpaAuditingHandler` Bean이 두 번 등록됨

판단: 애플리케이션 컨텍스트가 뜨지 않으므로 `/answer`, JPA 매핑, DB 연결과 Docker 앱 기동은 현재 검증할 수 없다.

### 6.3 API 계약 테스트

평가 API 전용 테스트는 없다. 다음 항목은 검증되지 않았다.

- `/answer` 실제 경로
- 한글 질문 URL 인코딩
- 정상 200과 5개 필드 이름
- 파라미터 누락·공백·길이 초과 400
- `PARTIAL`, `UNANSWERABLE`, `FAILED`
- timeout과 오류 응답
- `retrieved_context`의 공시 식별 가능성
- `think_trace`의 비밀정보·내부 사고과정 미포함

### 6.4 Docker와 배포

확인 결과:

- `backend/Dockerfile`과 `compose.yaml`은 존재한다.
- `docker compose config --quiet`은 구성 해석을 통과했다.
- Docker daemon이 실행 중이지 않아 컨테이너 기동과 health 확인은 수행하지 못했다.
- 현재 Docker 파일의 주 작성자는 역할 B 담당자이며, 역할 A의 제출 환경 동결·README 산출물은 아직 확인되지 않는다.
- 루트 `README.md`는 현재 실행·평가 API·HCX·장애 대응 절차를 재현할 수준이 아니다.

## 7. 역할 A 진행 현황표

| 영역 | 현재 상태 | 현재 근거 | 완료까지 필요한 핵심 |
|---|---|---|---|
| 공통 명령·답변 계약 | 부분 완료 | `AnswerQuestionCommand`, `AnswerResult` | 서비스 반환형과 adapter mapper 연결 |
| 평가 API | 부분 완료 | Controller·DTO 존재 | 경로·JSON 키 복구, 입력 검증, 계약 테스트 |
| 질문 실행 기록 | 부분 완료 | Entity·Repository 존재 | Flyway, JPA 매핑, 상태 전이, 중복 정책 |
| 질문 계획 | 부분 완료 | `QuestionPlan`, 도구 enum | 목표 스키마 확정, 후보 검증, 실제 실행 |
| 역할 A-B 검색 경계 | 부분 완료 | `DisclosureRetriever` | B 구현 연결, 실제 근거 DTO 확정 |
| 도구 계약 문서 | 문서 완료 | `TOOL_CONTRACTS.md` | 팀 승인과 코드 이름 정합화 |
| HyperCLOVA X | 미착수 | 클라이언트·설정 없음 | 최소 호출, 구조화 출력, fake·연동 테스트 |
| 답변 생성·검증 | 미착수 | placeholder 답변만 존재 | 근거 제한 생성, 수치·근거·금지 표현 검증 |
| 오류·재시도·timeout | 미착수 | 관련 실행 코드 없음 | 제한 횟수, 전체 시간 예산, 표준 오류 |
| 관측 가능성 | 미착수 | QuestionRun 초안만 존재 | request/run ID, 단계·시간·버전·오류 로그 |
| 자동 테스트 | 검증 실패 | `contextLoads` 1건 실패 | 기동 복구, API·오케스트레이션 계약 테스트 |
| Docker·README·배포 | 부분 자산 존재 | Docker 파일 존재 | 평가 profile, HCX 설정, smoke, 운영 절차 |
| P1 웹 질문 화면 | 미착수 | Vite 기본 화면, 역할 A 커밋 없음 | P0 완료 후 동일 공통 결과를 웹 DTO로 변환 |

## 8. 즉시 수정해야 할 현재 불일치

다음 항목은 새 기능보다 먼저 정리해야 한다.

1. 중복 `@EnableJpaAuditing`을 한 곳으로 통합해 `contextLoads`를 통과시킨다.
2. 평가 endpoint를 명세의 `GET /answer`로 맞춘다.
3. JSON 필드를 `question`, `answer`로 직렬화하고 5개 필드 계약 테스트를 추가한다.
4. `DisclosureAnswerService`가 평가 DTO가 아닌 `AnswerResult`를 반환하게 하고, 평가 adapter가 별도로 매핑한다.
5. `question_runs` Flyway와 JPA 매핑을 맞추거나, 첫 수직 슬라이스에서 사용하지 않을 엔티티라면 실행 경로에서 분리한다.
6. `QuestionPlan`, `QueryPlan`, `ToolName`을 `TOOL_CONTRACTS.md`와 하나의 계약으로 정리한다.

이 단계가 끝나기 전에는 HCX나 실제 검색 연결을 추가해도 실패 원인을 분리하기 어렵다.

## 9. 다음 작업 우선순위

### P0-A0. 녹색 기준선 복구

완료 조건:

- `compileJava` 성공
- `contextLoads` 성공
- `GET /answer` 정상·누락·공백 계약 테스트 성공
- 응답 JSON에 정확히 `question_id`, `question`, `retrieved_context`, `think_trace`, `answer` 존재

### P0-A1. 공통 엔진과 평가 adapter 분리

최소 흐름:

```text
EvaluationAnswerController
→ AnswerQuestionCommand
→ DisclosureAnswerService
→ AnswerResult
→ EvaluationAnswerResponse mapper
```

완료 조건:

- 공통 서비스가 평가 전용 DTO를 참조하지 않음
- 평가 스키마 변경이 mapper와 DTO에만 영향을 줌
- 현재 검색이 미연결이면 빈 배열을 사용하고 근거·실행 기록을 지어내지 않음

### P0-A2. 질문 계획 계약 확정

역할 A와 C가 먼저 확정하고 역할 B가 검색 입력으로 검토해야 한다.

필수 내용:

- 질문 유형 복수 선택
- 기업 표현과 검증된 `companyId` 분리
- 접수기간, 보고기간, `asOf` 분리
- 공시 그룹·세부 유형·factKey·section hint
- 5개 허용 도구와 단계 의존성
- 명확화 사유와 답변 불가 후보
- schema version과 계획 검증 결과

완료 조건:

- 구조화된 계획 후보와 백엔드 검증 완료 계획을 분리
- 허용되지 않은 도구·enum·기간·문서 수를 거부
- 계획 단위 테스트와 JSON Schema 계약 테스트 통과

### P0-A3. 단일 공시 질문 수직 슬라이스

첫 대상은 `TOOL_CONTRACTS.md`의 SK하이닉스 `신규시설투자등` 표본을 재사용한다.

최소 흐름:

```text
질문 수신
→ 기업·기간 계획
→ 역할 B의 검색 결과 1건
→ 검증된 시설투자 fact와 계산 결과
→ 근거 제한 답변 생성
→ 평가 DTO 변환
```

완료 조건:

- 대표 질문 하나가 실제 공시 접수번호·문맥과 함께 반환됨
- LLM이 금액·비율을 새로 계산하지 않음
- 같은 fixture로 반복 가능한 통합 테스트 존재

### P0-A4. HyperCLOVA X 최소 연동

필수 구현:

- 별도 client 경계
- 모델·endpoint·인증·timeout·재시도 설정
- `QUESTION_PLAN`, `ANSWER_COMPOSITION` 구조화 출력
- JSON Schema와 enum 검증
- 테스트에서는 고정 fake, 별도 연동 테스트에서만 실제 HCX 호출
- 다른 LLM fallback 금지

운영진이 허용 모델 ID, endpoint, 인증 방식과 제한을 확정하기 전에는 값을 추측해 코드에 고정하지 않는다.

### P0-A5. 답변 검증과 안전한 실패

필수 순서:

1. 응답 스키마
2. 기업·날짜·수치 일치
3. 계산 기록 일치
4. 주장별 근거 존재
5. 정정·기준시점 적용
6. `CONTEST` 데이터 범위
7. 투자 권유·목표주가·확률·수익 보장 표현

완료 조건:

- 검증 실패 시 최대 횟수 내 재검색·재생성
- 계속 실패하면 검증된 범위만 `PARTIAL` 또는 `UNANSWERABLE`
- 검증되지 않은 완성형 답변을 반환하지 않음

### P0-A6. 요청 추적·오류·시간 제한

필수 내용:

- request ID, external question ID, 내부 run ID 분리
- `RECEIVED`부터 `VALIDATING`까지 상태 기록
- 검색·모델·검증 단계별 처리시간
- 오류 코드, 재시도 횟수, 사용 공시 ID와 버전
- 중복 `question_id` 정책
- 전체 시간 예산이 개별 재시도보다 우선

### P0-A7. 제출 환경 동결

완료 조건:

- `evaluation` profile에서 `CONTEST` 데이터와 HyperCLOVA X만 외부 연결 허용
- Docker에서 앱·PostgreSQL 기동
- health, readiness, liveness 확인
- 대표 질문 smoke test 성공
- README만으로 빌드·실행·테스트·환경변수·평가 호출 재현
- 운영진 최종 인증·IP·제한시간 계약 반영

## 10. 다른 역할과 확정해야 할 경계

### 역할 B로부터 필요한 계약

- `DisclosureRetriever` 실제 구현
- 공시 ID, 접수번호, 기업, 공시명, 접수일, 원문 위치와 문맥
- 검색 점수·방법과 답변 사용 여부
- 검증된 fact와 원문 evidence 연결
- 검색 실패, 데이터셋 미준비, 정정 관계 미확정 상태

현재 `RetrievedContextResponse`가 빈 record이므로 평가 응답 매핑을 완료할 수 없다.

### 역할 C로부터 필요한 계약

- 질문 유형과 필수 근거 규칙
- 기업·기간·회계 기준 모호성 처리
- 계산 가능성·반올림·단위 규칙
- `PARTIAL`, `UNANSWERABLE` 판정 기준
- 금지 표현과 안전 응답
- 대표 질문·골든셋·기대 답변과 근거

### 공동 결정

- `QuestionPlan` 최종 스키마와 버전
- `retrieved_context`, `think_trace` 최종 자료형
- 공통 `AnswerResult`와 평가·웹 adapter 매핑
- `question_runs`와 실행 이벤트 저장 범위
- 제한시간, 최대 문서 수, 재시도 횟수

## 11. 외부 확정 대기 항목

다음은 현재 알 수 없으므로 임의 구현하지 않는다.

1. 운영진의 최종 평가 API 인증과 오류 본문
2. `retrieved_context`, `think_trace`의 최종 자료형과 크기 제한
3. 질문당 제한시간과 동시 요청 수
4. 허용된 HyperCLOVA X 모델 ID, endpoint와 호출 제한
5. 임베딩·재정렬 모델의 허용 범위
6. 평가 서버의 외부 통신·허용 IP 규칙

설정으로 바꿀 수 있는 경계를 먼저 만들고 실제 값은 운영진 공지 후 확정한다.

## 12. 역할 A 전용 요구사항·기능명세서로 전환할 구조

추후 전용 문서는 다음 구조로 작성하면 현재 작업과 잔여 범위를 직접 추적할 수 있다.

| 전용 명세 장 | 포함할 내용 | 기존 근거 |
|---|---|---|
| RA-01 범위와 책임 | P0/P1/P2, A-B-C 경계, 제외 범위 | 역할 분담 5장, 요구사항 19장 |
| RA-02 평가 입력 adapter | `/answer`, 입력 검증, 계약·오류 매핑 | `FR-EVAL-*`, `FS-P0-11` |
| RA-03 질문 계획 | 후보 생성, 백엔드 검증, 5개 도구 | `FR-QUERY-*`, `TOOL_CONTRACTS.md` |
| RA-04 오케스트레이션 | 상태 전이, 단계 순서, 반복 제한 | 기능명세 8장 |
| RA-05 HCX 연동 | client, 구조화 출력, prompt·model version | `IR-010~015`, 기능명세 9장 |
| RA-06 공통 답변과 adapter | `AnswerResult`, 근거·trace·answer mapping | 기능명세 6.6, 12.4 |
| RA-07 검증과 안전 | 근거·수치·금지 표현, 부분·불가 답변 | `FR-ANS-*`, `FS-P0-10` |
| RA-08 신뢰성과 관측 | timeout, retry, ID, 로그, 메트릭 | `NFR-REL-*`, `NFR-OBS-*` |
| RA-09 배포 | profile, Docker, health, README, smoke | `FR-OPS-*`, 기능명세 14·17장 |
| RA-10 테스트와 완료 정의 | 계약·통합·HCX fake·Docker smoke | 요구사항 17·21장, 기능명세 18·22장 |

각 요구사항에는 다음 필드를 둔다.

```text
ID / 설명 / 우선순위 / 입력 / 출력 / 선행조건
정상 시나리오 / 정보 부족 시나리오 / 오류 시나리오
다른 역할 의존성 / 자동 테스트 / 완료 근거 / 현재 상태
```

## 13. 다음 체크포인트

가장 가까운 체크포인트는 다음 하나다.

> 애플리케이션 컨텍스트가 정상 기동되고, 평가 계약과 정확히 일치하는 `GET /answer`가 빈 근거의 명시적 placeholder 응답을 반환하며, 정상·누락·공백 계약 테스트가 통과한다.

이 체크포인트 전에는 HCX, 복합 계획, 캐시, 비동기 처리나 별도 registry를 추가하지 않는다. 현재 실패 기준선을 먼저 복구하는 것이 가장 짧은 완료 경로다.

## 14. 근거 파일

- 기획 기준: `PROJECT_CONTEXT.md`, `요구사항_정의서.md`, `기능명세서.md`, `IA.md`, `DECISIONS.md`, 역할 분담 문서
- 도구 계약: `docs/TOOL_CONTRACTS.md`
- 역할 A 코드: `evaluation/`, `orchestration/DisclosureAnswerService.java`, `answer/AnswerResult.java`, `question/`, `retrieval/`
- DB 기준: `backend/src/main/resources/db/migration/`
- 실행 근거: Git 이력과 2026-08-04 로컬 `compileJava`, `test`, Docker 상태 확인 결과
