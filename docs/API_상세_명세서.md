# FolioLens API 상세 명세서

| 항목 | 내용 |
|---|---|
| 문서 버전 | v1.0 |
| 문서 상태 | 공식 과제 반영 개정안 |
| 작성일 | 2026-07-28 |
| 상위 문서 | `API_명세서.md` v1.0 |
| 기준 문서 | `요구사항_정의서.md`, `기능명세서.md`, `IA.md` |

## 1. 문서 범위

본 문서는 FolioLens P0/P1 API의 URL별 상세 요청·응답 계약을 정의한다.

표시 상태:

| 상태 | 의미 |
|---|---|
| 구현됨 | 현재 코드에 Controller·Service가 존재 |
| 부분 구현 | 기반 코드만 존재하거나 계약 수정 필요 |
| 목표 계약 | 문서로 확정했지만 아직 구현되지 않음 |
| 미확정 | 운영진 규격 또는 팀 결정 필요 |

P2 포트폴리오 API는 경로만 보존하고 이번 상세 구현 범위에서 제외한다.

## 2. 공통 규칙

### 2.1 Base URL

```text
로컬: http://localhost:8080
운영: https://{assigned-host}
```

### 2.2 웹 API 성공 응답

현재 백엔드 `ApiResponse<T>` 형식을 그대로 사용한다.

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {}
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| success | Boolean | 성공 여부, 성공이면 `true` |
| code | String/null | 성공 시 현재 구현은 `null` |
| message | String | 사용자에게 표시 가능한 결과 메시지 |
| data | Object/null | API별 응답 데이터 |

### 2.3 웹 API 오류 응답

```json
{
  "success": false,
  "code": "QUESTION_404_1",
  "message": "질문 실행을 찾을 수 없습니다.",
  "data": null
}
```

현재 유효성 검사 실패는 `ApiResponse.fail(String)` 때문에 `code`가 `null`일 수 있다. 목표 구현에서는 `COMMON_400_1` 또는 도메인 오류 코드를 반환한다.

### 2.4 페이지 응답

```json
{
  "items": [],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "hasNext": false
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| number | Integer | 0부터 시작하는 현재 페이지 |
| size | Integer | 페이지 크기 |
| totalElements | Long | 전체 항목 수 |
| totalPages | Integer | 전체 페이지 수 |
| hasNext | Boolean | 다음 페이지 존재 여부 |

### 2.5 요청 추적

클라이언트는 선택적으로 다음 헤더를 보낼 수 있다.

```http
X-Request-Id: client-request-id
```

서버는 요청 헤더가 없으면 새 ID를 생성하고 응답 헤더에 반환한다.

### 2.6 날짜·금액·비율

- 날짜: `YYYY-MM-DD`
- 시각: ISO 8601 offset 포함
- 접수번호: String
- UUID: String
- 원화 정규화 금액: 1원 단위 JSON number
- 비율: `%`를 제외한 JSON number
- 원문 표시값: `rawValue`
- 화면 표시값: `displayValue`

### 2.7 null

- 확인되지 않은 값은 `null`
- 비어 있는 목록은 `[]`
- 확인되지 않은 숫자를 `0`으로 대체하지 않음

## 3. 평가 API

### 3.1 평가 질문 답변

- **URL:** `GET /answer`
- **우선순위:** P0 Must
- **상태:** 목표 계약
- **인증:** 운영진 최종 규격 확인 필요
- **응답 방식:** 동기

#### Description

- 운영진이 전달한 자연어 공시 질문에 답합니다.
- 대회 제공 공시 데이터만 검색·분석합니다.
- 실제 사용한 공시 문맥과 안전한 실행 요약을 함께 반환합니다.
- 공시에서 답을 찾을 수 없는 경우에도 추측하지 않고 정보 한계를 답변합니다.
- 웹 API의 `ApiResponse<T>`로 감싸지 않습니다.

#### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| question_id | String | 예 | 평가 질문 식별자 |
| question | String | 예 | URL 인코딩된 자연어 질문 |

#### 요청 예시

```http
GET /answer?question_id=q-001&question=A%EC%82%AC%EC%9D%98%202025%EB%85%84%20%EB%A7%A4%EC%B6%9C%EC%95%A1%EC%9D%80%3F
```

#### Response 200

```json
{
  "question_id": "q-001",
  "question": "A사의 2025년 매출액은?",
  "retrieved_context": [
    {
      "receipt_no": "20250320000001",
      "report_name": "사업보고서",
      "submitted_at": "2025-03-20",
      "section": "III. 재무에 관한 사항",
      "content": "매출액은 ...입니다."
    }
  ],
  "think_trace": [
    {
      "step": "RETRIEVAL",
      "summary": "A사의 2025년 사업보고서에서 매출액 항목을 검색했습니다."
    },
    {
      "step": "VALIDATION",
      "summary": "답변 수치와 공시 근거가 일치하는지 확인했습니다."
    }
  ],
  "answer": "A사의 2025년 매출액은 ...입니다. 근거는 2025년 사업보고서의 'III. 재무에 관한 사항'입니다."
}
```

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| question_id | String | 요청의 질문 ID |
| question | String | 요청 질문 원문 |
| retrieved_context | List<Object> | 최종 답변에 실제 사용한 공시 문맥 |
| retrieved_context[].receipt_no | String | 공시 접수번호 |
| retrieved_context[].report_name | String | 공시명 |
| retrieved_context[].submitted_at | LocalDate | 접수일 |
| retrieved_context[].section | String | 근거의 원문 위치 |
| retrieved_context[].content | String | 사용한 근거 문맥 |
| think_trace | List<Object> | 안전한 실행 단계 요약 |
| think_trace[].step | String | `PLANNING`, `RETRIEVAL`, `EXTRACTION`, `CALCULATION`, `VALIDATION` 등 |
| think_trace[].summary | String | 검색·계산·검증 실행 사실 |
| answer | String | 근거와 정보 한계를 포함한 최종 답변 |

#### 답변 불가 Response 200

```json
{
  "question_id": "q-002",
  "question": "A사의 현재 주가는 얼마야?",
  "retrieved_context": [],
  "think_trace": [
    {
      "step": "RETRIEVAL",
      "summary": "A사의 제공 공시에서 현재 주가 정보를 확인했습니다."
    },
    {
      "step": "VALIDATION",
      "summary": "제공 공시만으로 답을 확인할 수 없다고 판정했습니다."
    }
  ],
  "answer": "제공된 공시에서는 A사의 현재 주가를 확인할 수 없습니다. 실시간 시장 가격 데이터는 이번 데이터 범위에 포함되지 않습니다."
}
```

#### 오류

| 상황 | HTTP | 비고 |
|---|---:|---|
| question_id 누락 | 400 | 최종 오류 스키마 미확정 |
| question 누락·빈 값 | 400 | 최종 오류 스키마 미확정 |
| 데이터셋 준비 안 됨 | 503 | 평가 서버 상태 점검 필요 |
| 처리 제한시간 초과 | 504 | 운영진 제한시간 적용 |
| 내부 오류 | 500 | request ID 로그 기록 |

`retrieved_context`와 `think_trace`의 최종 자료형은 운영진 공지가 우선한다.

## 4. 데모 세션 API

### 4.1 데모 세션 생성

- **URL:** `POST /api/v1/demo-sessions`
- **우선순위:** P1 Should
- **상태:** 부분 구현(DB 초안)
- **인증:** 없음

#### Description

- 회원가입 없이 웹 질문 기능을 사용할 수 있는 임시 세션을 생성합니다.
- 프론트엔드는 첫 질문 전에 자동으로 호출할 수 있습니다.
- 토큰 원문은 DB에 저장하지 않고 해시만 저장해야 합니다.

#### Request Body

```json
{}
```

#### Response 201

```json
{
  "success": true,
  "code": null,
  "message": "데모 세션이 생성되었습니다.",
  "data": {
    "sessionToken": "opaque-random-token",
    "expiresAt": "2026-07-29T14:00:00+09:00"
  }
}
```

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| sessionToken | String | 이후 `X-Demo-Session` 헤더에 전달할 토큰 |
| expiresAt | OffsetDateTime | 세션 만료 시각 |

#### Error

| 상황 | 에러 코드 |
|---|---|
| 세션 발급 실패 | COMMON_500_1 |

## 5. 기업 API

### 5.1 기업 검색

- **URL:** `GET /api/v1/companies`
- **우선순위:** P0/P1 재사용
- **상태:** 부분 구현
- **인증:** 목표 계약상 없음

#### Description

- 기업명 또는 종목코드로 기업을 검색합니다.
- 질문의 기업이 모호할 때 사용자 후보 선택에도 사용합니다.
- 현재 코드는 종목코드 정확 일치, 기업명 정확 일치, 기업명 부분 일치를 구분합니다.

#### Query Parameters

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| query | String | 예 | 없음 | 기업명 또는 종목코드, 1~100자 |
| listedOnly | Boolean | 아니요 | true | 상장 기업만 검색 |
| page | Integer | 아니요 | 0 | 0 이상 |
| size | Integer | 아니요 | 20 | 1~100 |

#### 요청 예시

```http
GET /api/v1/companies?query=삼성&listedOnly=true&page=0&size=20
```

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "기업 검색에 성공했습니다.",
  "data": {
    "items": [
      {
        "companyId": "company-uuid",
        "corpCode": "00126380",
        "stockCode": "005930",
        "corpName": "삼성전자",
        "market": "KOSPI",
        "matchType": "PARTIAL_NAME",
        "listed": true
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  }
}
```

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| items | List<Object> | 기업 검색 결과 |
| items[].companyId | UUID | 내부 기업 ID |
| items[].corpCode | String | 기업 고유번호 |
| items[].stockCode | String/null | 종목코드 |
| items[].corpName | String | 정식 기업명 |
| items[].market | String/null | KOSPI, KOSDAQ, KONEX, OTHER |
| items[].matchType | String | EXACT_STOCK_CODE, EXACT_NAME, PARTIAL_NAME |
| items[].listed | Boolean | 상장 여부 |
| page | Object | 페이지 정보 |

#### Error

| 상황 | 에러 코드 |
|---|---|
| query 누락·공백·100자 초과 | COMMON_400_1 |
| page가 0 미만 | COMMON_400_1 |
| size가 1 미만 또는 100 초과 | COMMON_400_1 |

현재 `SecurityConfig` 때문에 실제 실행 시 JWT가 필요할 수 있으며, 공개 검색으로 변경해야 합니다.
현재 `CompanySearchItemResponse`에는 `companyId`가 없으므로 목표 계약에 맞게 필드를 추가해야 합니다.

## 6. 질문 API

### 6.1 질문 실행 생성

- **URL:** `POST /api/v1/questions`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** `X-Demo-Session` 또는 P2 JWT
- **Idempotency:** `Idempotency-Key` 권장

#### Description

- 자연어 질문 실행을 생성합니다.
- 일반 웹 질문은 비동기로 처리하며 `202 Accepted`를 반환합니다.
- 후속 질문도 동일 API를 사용하며 `conversationId`, `parentRunId`로 문맥을 전달합니다.
- 선택한 공시가 있으면 `selectedDisclosureIds`로 검색 범위를 좁힐 수 있습니다.

#### Request Headers

```http
X-Demo-Session: {sessionToken}
Idempotency-Key: optional-unique-key
Content-Type: application/json
```

#### Request Body

```json
{
  "question": "A사의 2024년과 2025년 시설투자 금액을 비교해줘.",
  "conversationId": null,
  "parentRunId": null,
  "selectedDisclosureIds": [],
  "portfolioId": null
}
```

#### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| question | String | 예 | 자연어 질문, 최대 길이는 설정값 |
| conversationId | UUID/null | 아니요 | 기존 대화에서 후속 질문할 때 사용 |
| parentRunId | UUID/null | 아니요 | 직접적인 이전 답변 실행 ID |
| selectedDisclosureIds | List<UUID> | 아니요 | 질문의 기준으로 선택한 공시 |
| portfolioId | UUID/null | 아니요 | P2 개인화 질문에서만 사용 |

#### Response 202

```json
{
  "success": true,
  "code": null,
  "message": "질문 요청이 접수되었습니다.",
  "data": {
    "runId": "49a1fcd8-3e45-47b0-91d5-10cce36a67de",
    "conversationId": "2a1f644f-214f-42db-ac1b-c867d35ba29a",
    "status": "RECEIVED",
    "statusMessage": "질문을 확인하고 있습니다.",
    "pollUrl": "/api/v1/questions/49a1fcd8-3e45-47b0-91d5-10cce36a67de",
    "createdAt": "2026-07-28T14:00:00+09:00"
  }
}
```

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| runId | UUID | 질문 실행 ID |
| conversationId | UUID | 대화 ID, 첫 질문이면 새로 생성 |
| status | String | 초기 상태 RECEIVED |
| statusMessage | String | 사용자용 상태 문구 |
| pollUrl | String | 실행 조회 경로 |
| createdAt | OffsetDateTime | 생성 시각 |

#### Error

| 상황 | 에러 코드 |
|---|---|
| 세션·인증 없음 | COMMON_401_1 |
| 질문 누락·공백 | QUESTION_400_1 |
| 질문 길이 초과 | QUESTION_400_2 |
| 존재하지 않는 conversationId | COMMON_404_1 |
| 다른 세션의 conversationId | QUESTION_403_1 |
| 선택 공시가 4개 초과 | COMMON_400_1 |
| 선택 공시 없음 또는 접근 불가 | DISCLOSURE_404_1 |
| 데이터셋 준비 안 됨 | DATASET_503_1 |
| 같은 Idempotency-Key에 다른 본문 | QUESTION_409_1 |

### 6.2 질문 실행·답변 조회

- **URL:** `GET /api/v1/questions/{runId}`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** 질문을 생성한 데모 세션 또는 사용자

#### Description

- 질문 실행의 현재 상태를 조회합니다.
- 처리 중에는 진행 상태만, 종료 상태에서는 구조화된 답변을 함께 반환합니다.
- `PARTIAL`과 `UNANSWERABLE`도 HTTP 200입니다.

#### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| runId | UUID | 예 | 질문 실행 ID |

#### 요청 예시

```http
GET /api/v1/questions/49a1fcd8-3e45-47b0-91d5-10cce36a67de
X-Demo-Session: {sessionToken}
```

#### 처리 중 Response 200

```json
{
  "success": true,
  "code": null,
  "message": "질문 처리 상태를 조회했습니다.",
  "data": {
    "runId": "49a1fcd8-3e45-47b0-91d5-10cce36a67de",
    "conversationId": "2a1f644f-214f-42db-ac1b-c867d35ba29a",
    "status": "RETRIEVING",
    "statusMessage": "관련 공시를 찾고 있습니다.",
    "progress": {
      "currentStage": "RETRIEVING",
      "completedStages": ["PLANNING"]
    },
    "question": "A사의 2024년과 2025년 시설투자 금액을 비교해줘.",
    "clarification": null,
    "answer": null,
    "dataBasis": null,
    "error": null,
    "createdAt": "2026-07-28T14:00:00+09:00",
    "completedAt": null
  }
}
```

#### 완료 Response 200

```json
{
  "success": true,
  "code": null,
  "message": "질문 답변 조회에 성공했습니다.",
  "data": {
    "runId": "49a1fcd8-3e45-47b0-91d5-10cce36a67de",
    "conversationId": "2a1f644f-214f-42db-ac1b-c867d35ba29a",
    "status": "COMPLETED",
    "statusMessage": "답변이 완료되었습니다.",
    "progress": {
      "currentStage": "COMPLETED",
      "completedStages": [
        "PLANNING",
        "RETRIEVING",
        "EXTRACTING",
        "CALCULATING",
        "GENERATING",
        "VALIDATING"
      ]
    },
    "question": "A사의 2024년과 2025년 시설투자 금액을 비교해줘.",
    "clarification": null,
    "answer": {
      "directAnswer": "시설투자 금액은 2,000억원에서 2,400억원으로 400억원, 20.0% 증가했습니다.",
      "claims": [
        {
          "claimId": "claim-1",
          "type": "FACT",
          "text": "2024년 시설투자 금액은 2,000억원입니다.",
          "evidenceIds": ["evidence-1"],
          "calculationId": null
        },
        {
          "claimId": "claim-2",
          "type": "CALCULATION",
          "text": "전년 대비 400억원, 20.0% 증가했습니다.",
          "evidenceIds": ["evidence-1", "evidence-2"],
          "calculationId": "calculation-1"
        }
      ],
      "comparisons": [
        {
          "comparisonId": "comparison-1",
          "label": "시설투자 금액",
          "basis": "동일 기업의 연도별 시설투자 금액",
          "items": [
            {
              "label": "2024년",
              "rawValue": "2,000억원",
              "normalizedValue": 200000000000,
              "unit": "KRW",
              "evidenceId": "evidence-1"
            },
            {
              "label": "2025년",
              "rawValue": "2,400억원",
              "normalizedValue": 240000000000,
              "unit": "KRW",
              "evidenceId": "evidence-2"
            }
          ],
          "calculationIds": ["calculation-1"],
          "warnings": []
        }
      ],
      "calculations": [
        {
          "calculationId": "calculation-1",
          "operation": "CHANGE_RATE",
          "label": "전년 대비 증감률",
          "displayValue": "20.0%",
          "status": "COMPLETED"
        }
      ],
      "disclosureTimeline": [],
      "usedEvidences": [
        {
          "evidenceId": "evidence-1",
          "receiptNo": "20240101000001",
          "companyName": "A사",
          "reportName": "신규시설투자등",
          "submittedAt": "2024-01-01",
          "sectionPath": "2. 투자내역 > 투자금액",
          "preview": "투자금액 2,000억원",
          "evidenceType": "TABLE",
          "correction": false
        }
      ],
      "limitations": [],
      "suggestedQuestions": [
        "두 공시의 완료 예정일도 비교해줘."
      ]
    },
    "dataBasis": {
      "sourceProvider": "CONTEST",
      "period": "2023.01~2026.03",
      "datasetVersion": "contest-data-v1"
    },
    "error": null,
    "createdAt": "2026-07-28T14:00:00+09:00",
    "completedAt": "2026-07-28T14:00:09+09:00"
  }
}
```

#### 최상위 Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| runId | UUID | 실행 ID |
| conversationId | UUID | 대화 ID |
| status | String | 질문 실행 상태 |
| statusMessage | String | 사용자용 상태 문구 |
| progress | Object | 처리 단계 |
| progress.currentStage | String | 현재 단계 |
| progress.completedStages | List<String> | 완료된 단계 |
| question | String | 질문 원문 |
| clarification | Object/null | 사용자 확인 요청 |
| answer | Object/null | 종료 상태의 구조화 답변 |
| dataBasis | Object/null | 데이터 범위와 버전 |
| error | Object/null | FAILED 상태의 오류 |
| createdAt | OffsetDateTime | 생성 시각 |
| completedAt | OffsetDateTime/null | 종료 시각 |

#### Answer Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| directAnswer | String | 질문에 대한 직접 답변 |
| claims | List<Object> | 사실·계산·설명·한계 주장 |
| comparisons | List<Object> | 비교표 데이터 |
| calculations | List<Object> | 계산 요약 |
| disclosureTimeline | List<Object> | 원공시·정정·후속 이력 |
| usedEvidences | List<Object> | 실제 사용 근거 요약 |
| limitations | List<Object> | 정보·비교 한계 |
| suggestedQuestions | List<String> | 현재 근거 범위의 후속 질문 |

#### Error

| 상황 | 에러 코드 |
|---|---|
| 잘못된 runId 형식 | COMMON_400_1 |
| 실행 없음 | QUESTION_404_1 |
| 다른 세션·사용자 실행 | QUESTION_403_1 |

### 6.3 모호성 확인 제출

- **URL:** `POST /api/v1/questions/{runId}/clarifications`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** 질문을 생성한 데모 세션 또는 사용자

#### Description

- `CLARIFICATION_REQUIRED` 상태의 질문에 사용자 선택을 제출하고 처리를 재개합니다.
- 초기 범위에서는 기업 선택을 지원합니다.

#### Clarification Required 조회 예

```json
{
  "success": true,
  "code": null,
  "message": "질문을 계속하려면 기업 선택이 필요합니다.",
  "data": {
    "runId": "run-uuid",
    "status": "CLARIFICATION_REQUIRED",
    "clarification": {
      "type": "COMPANY",
      "message": "어느 기업을 의미하는지 선택해 주세요.",
      "candidates": [
        {
          "companyId": "company-uuid-1",
          "corpName": "삼성전자",
          "stockCode": "005930",
          "market": "KOSPI",
          "matchType": "PARTIAL_NAME"
        },
        {
          "companyId": "company-uuid-2",
          "corpName": "삼성SDI",
          "stockCode": "006400",
          "market": "KOSPI",
          "matchType": "PARTIAL_NAME"
        }
      ]
    }
  }
}
```

#### Request Body

```json
{
  "type": "COMPANY",
  "selectedCompanyId": "company-uuid-1"
}
```

#### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| type | String | 예 | 초기에는 COMPANY |
| selectedCompanyId | UUID | 예 | 후보 목록에서 선택한 기업 |

#### Response 202

```json
{
  "success": true,
  "code": null,
  "message": "기업 선택이 반영되어 질문 처리를 재개했습니다.",
  "data": {
    "runId": "run-uuid",
    "status": "RETRIEVING",
    "statusMessage": "관련 공시를 찾고 있습니다.",
    "pollUrl": "/api/v1/questions/run-uuid"
  }
}
```

#### Error

| 상황 | 에러 코드 |
|---|---|
| 실행 없음 | QUESTION_404_1 |
| 다른 세션의 실행 | QUESTION_403_1 |
| 현재 상태가 CLARIFICATION_REQUIRED가 아님 | QUESTION_409_1 |
| 후보에 없는 selectedCompanyId | QUESTION_400_3 |

### 6.4 답변 근거 상세

- **URL:** `GET /api/v1/questions/{runId}/evidences/{evidenceId}`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** 질문을 생성한 데모 세션 또는 사용자

#### Description

- 답변의 핵심 주장을 지지한 공시 근거의 상세 문맥을 조회합니다.
- 표 근거인 경우 머리글과 행 정보를 함께 반환합니다.
- 해당 질문 실행에서 실제 사용한 근거만 조회할 수 있습니다.

#### Path Variables

| 필드 | 타입 | 설명 |
|---|---|---|
| runId | UUID | 질문 실행 ID |
| evidenceId | UUID 또는 opaque String | 근거 ID |

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "답변 근거 조회에 성공했습니다.",
  "data": {
    "evidenceId": "evidence-2",
    "supportedClaimIds": ["claim-1", "claim-2"],
    "disclosure": {
      "disclosureId": "disclosure-uuid",
      "receiptNo": "20250101000001",
      "companyName": "A사",
      "stockCode": "000000",
      "reportName": "신규시설투자등",
      "submittedAt": "2025-01-01T10:00:00+09:00",
      "correction": false
    },
    "location": {
      "sectionId": "section-uuid",
      "sectionPath": "2. 투자내역 > 투자금액",
      "page": null,
      "tableNumber": "표 1",
      "rowLabel": "투자금액",
      "startOffset": 120,
      "endOffset": 152
    },
    "content": "투자금액 2,400억원",
    "contextBefore": "투자 내역은 다음과 같습니다.",
    "contextAfter": "투자 종료 예정일은 2027년 11월입니다.",
    "table": {
      "headers": ["구분", "내용"],
      "row": ["투자금액", "2,400억원"]
    },
    "sourceProvider": "CONTEST"
  }
}
```

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| evidenceId | String | 근거 ID |
| supportedClaimIds | List<String> | 이 근거가 지지하는 주장 |
| disclosure | Object | 공시 메타데이터 |
| location | Object | 원문 위치 |
| content | String | 핵심 근거 문맥 |
| contextBefore | String/null | 앞 문맥 |
| contextAfter | String/null | 뒤 문맥 |
| table | Object/null | 표 머리글과 해당 행 |
| sourceProvider | String | 평가·웹 답변에서는 CONTEST |

#### Error

| 상황 | 에러 코드 |
|---|---|
| 질문 실행 없음 | QUESTION_404_1 |
| 접근 권한 없음 | QUESTION_403_1 |
| 실행에 속하지 않는 근거 | EVIDENCE_404_1 |

### 6.5 계산 상세

- **URL:** `GET /api/v1/questions/{runId}/calculations/{calculationId}`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** 질문을 생성한 데모 세션 또는 사용자

#### Description

- 계산 입력값, 단위, 공식, 결과, 반올림 규칙과 입력별 근거를 조회합니다.
- 모델이 아니라 백엔드 계산 도구의 기록을 반환합니다.

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "계산 상세 조회에 성공했습니다.",
  "data": {
    "calculationId": "calculation-1",
    "operation": "CHANGE_RATE",
    "label": "전년 대비 시설투자 금액 증감률",
    "inputs": [
      {
        "role": "BASE",
        "label": "2024년",
        "rawValue": "2,000억원",
        "normalizedValue": 200000000000,
        "unit": "KRW",
        "factId": "fact-1",
        "evidenceIds": ["evidence-1"]
      },
      {
        "role": "COMPARISON",
        "label": "2025년",
        "rawValue": "2,400억원",
        "normalizedValue": 240000000000,
        "unit": "KRW",
        "factId": "fact-2",
        "evidenceIds": ["evidence-2"]
      }
    ],
    "formula": "(comparison - base) / base * 100",
    "rawResult": 20.0,
    "displayValue": "20.0%",
    "roundingRule": "HALF_UP_1",
    "status": "COMPLETED",
    "failureReason": null
  }
}
```

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| calculationId | String | 계산 ID |
| operation | String | CHANGE_RATE, DIFFERENCE, RATIO 등 |
| label | String | 사용자용 계산명 |
| inputs | List<Object> | 계산 입력 |
| inputs[].role | String | BASE, COMPARISON, PART, WHOLE 등 |
| inputs[].rawValue | String/null | 공시 원문 값 |
| inputs[].normalizedValue | Number/null | 표준 단위 값 |
| inputs[].unit | String/null | 표준 단위 |
| inputs[].factId | String | 검증된 사실 ID |
| inputs[].evidenceIds | List<String> | 입력값 근거 |
| formula | String | 적용 공식 |
| rawResult | Number/null | 반올림 전 결과 |
| displayValue | String/null | 표시 결과 |
| roundingRule | String/null | 반올림 규칙 |
| status | String | COMPLETED 또는 NOT_APPLICABLE |
| failureReason | String/null | 계산 불가 이유 |

#### Error

| 상황 | 에러 코드 |
|---|---|
| 질문 실행 없음 | QUESTION_404_1 |
| 접근 권한 없음 | QUESTION_403_1 |
| 실행에 속하지 않는 계산 | CALCULATION_404_1 |

## 7. 대화 API

### 7.1 대화 조회

- **URL:** `GET /api/v1/conversations/{conversationId}`
- **우선순위:** P1 후반 Should
- **상태:** 목표 계약
- **인증:** 대화를 생성한 데모 세션 또는 사용자

#### Query Parameters

| 필드 | 타입 | 필수 | 기본값 |
|---|---|---|---|
| page | Integer | 아니요 | 0 |
| size | Integer | 아니요 | 20 |

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "대화 조회에 성공했습니다.",
  "data": {
    "conversationId": "conversation-uuid",
    "items": [
      {
        "runId": "run-uuid",
        "question": "A사의 2025년 시설투자 금액은?",
        "status": "COMPLETED",
        "directAnswer": "2,400억원입니다.",
        "evidenceCount": 1,
        "createdAt": "2026-07-28T14:00:00+09:00",
        "completedAt": "2026-07-28T14:00:06+09:00"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  }
}
```

#### Error

| 상황 | 에러 코드 |
|---|---|
| 대화 없음 | COMMON_404_1 |
| 접근 권한 없음 | COMMON_403_1 |

## 8. 공시 API

### 8.1 공시 목록 조회

- **URL:** `GET /api/v1/disclosures`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** 없음

#### Description

- 대회 제공 공시 목록을 조회합니다.
- 포트폴리오 보유 기업으로 제한하지 않습니다.
- 기업, 기간, 공시 범주·유형과 정정 여부를 필터링할 수 있습니다.

#### Query Parameters

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| companyId | UUID | 아니요 | 없음 | 기업 필터 |
| query | String | 아니요 | 없음 | 공시명 검색 |
| category | String | 아니요 | 없음 | PERIODIC, MATERIAL, EXCHANGE, OWNERSHIP |
| disclosureType | String | 아니요 | 없음 | 세부 공시 유형 |
| from | LocalDate | 아니요 | 없음 | 접수 시작일 |
| to | LocalDate | 아니요 | 없음 | 접수 종료일 |
| correction | Boolean | 아니요 | 없음 | 정정공시 여부 |
| page | Integer | 아니요 | 0 | 페이지 |
| size | Integer | 아니요 | 20 | 1~100 |
| sort | String | 아니요 | submittedAt,desc | 정렬 |

#### 요청 예시

```http
GET /api/v1/disclosures?companyId=company-uuid&category=PERIODIC&from=2024-01-01&to=2025-12-31&page=0&size=20
```

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "공시 목록 조회에 성공했습니다.",
  "data": {
    "items": [
      {
        "disclosureId": "disclosure-uuid",
        "receiptNo": "20250320000001",
        "company": {
          "companyId": "company-uuid",
          "corpName": "A사",
          "stockCode": "000000"
        },
        "reportName": "사업보고서",
        "category": "PERIODIC",
        "disclosureType": "ANNUAL_REPORT",
        "submittedAt": "2025-03-20T09:00:00+09:00",
        "submitter": "A사",
        "correction": false,
        "parseStatus": "COMPLETED",
        "sourceProvider": "CONTEST"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  }
}
```

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| disclosureId | UUID | 내부 공시 ID |
| receiptNo | String | 접수번호 |
| company | Object | 기업 요약 |
| reportName | String | 공시명 |
| category | String | 공시 범주 |
| disclosureType | String | 세부 공시 유형 |
| submittedAt | OffsetDateTime | 접수 시각 |
| submitter | String/null | 제출인 |
| correction | Boolean | 정정 여부 |
| parseStatus | String | PENDING, COMPLETED, PARTIAL, FAILED |
| sourceProvider | String | CONTEST |

#### Error

| 상황 | 에러 코드 |
|---|---|
| from이 to 이후 | COMMON_400_1 |
| 잘못된 범주·유형 | COMMON_400_1 |
| page·size 오류 | COMMON_400_1 |

검색 결과가 없는 경우 오류가 아니라 빈 `items`를 반환합니다.

### 8.2 공시 비교 조회

- **URL:** `GET /api/v1/disclosures/comparison`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** 없음

#### Description

- 공시 2~4건의 구조화 사실과 변경 내용을 비교합니다.
- 같은 기업의 기간별 공시, 원공시·정정공시 또는 여러 기업의 같은 지표 비교에 사용합니다.
- 비교 기준이 일치하지 않으면 계산하지 않고 경고를 반환합니다.

#### Query Parameters

동일한 `ids` 파라미터를 2~4회 전달합니다.

```http
GET /api/v1/disclosures/comparison?ids=id-1&ids=id-2
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| ids | List<UUID> | 예 | 비교할 공시 2~4개 |
| fields | List<String> | 아니요 | 비교할 factKey 제한 |

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "공시 비교 조회에 성공했습니다.",
  "data": {
    "comparisonId": "comparison-uuid",
    "basis": {
      "companyMatch": true,
      "periodComparable": true,
      "accountingBasisComparable": true,
      "unitComparable": true
    },
    "documents": [
      {
        "disclosureId": "id-1",
        "receiptNo": "20240101000001",
        "companyName": "A사",
        "reportName": "신규시설투자등",
        "submittedAt": "2024-01-01"
      },
      {
        "disclosureId": "id-2",
        "receiptNo": "20250101000001",
        "companyName": "A사",
        "reportName": "신규시설투자등",
        "submittedAt": "2025-01-01"
      }
    ],
    "rows": [
      {
        "factKey": "facility_investment.amount",
        "label": "투자금액",
        "values": [
          {
            "disclosureId": "id-1",
            "rawValue": "2,000억원",
            "normalizedValue": 200000000000,
            "unit": "KRW",
            "evidenceId": "evidence-1"
          },
          {
            "disclosureId": "id-2",
            "rawValue": "2,400억원",
            "normalizedValue": 240000000000,
            "unit": "KRW",
            "evidenceId": "evidence-2"
          }
        ],
        "difference": 40000000000,
        "changeRate": 20.0,
        "warnings": []
      }
    ],
    "timeline": [],
    "warnings": []
  }
}
```

#### Error

| 상황 | 에러 코드 |
|---|---|
| ids가 2개 미만 또는 4개 초과 | COMMON_400_1 |
| 중복 ID | COMMON_400_1 |
| 존재하지 않는 공시 | DISCLOSURE_404_1 |

기준 불일치는 HTTP 오류가 아니라 `warnings`와 null 계산값으로 표현합니다.

### 8.3 공시 상세 조회

- **URL:** `GET /api/v1/disclosures/{disclosureId}`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** 없음

#### Description

- 공시 메타데이터, 추출된 구조화 사실, 원문 목차와 관련 공시를 조회합니다.
- 전체 원문은 섹션 API에서 조회합니다.

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "공시 상세 조회에 성공했습니다.",
  "data": {
    "disclosureId": "disclosure-uuid",
    "receiptNo": "20250101000001",
    "company": {
      "companyId": "company-uuid",
      "corpCode": "00126380",
      "corpName": "A사",
      "stockCode": "000000",
      "market": "KOSPI"
    },
    "reportName": "신규시설투자등",
    "category": "EXCHANGE",
    "disclosureType": "FACILITY_INVESTMENT",
    "submittedAt": "2025-01-01T10:00:00+09:00",
    "submitter": "A사",
    "correction": false,
    "parseStatus": "COMPLETED",
    "facts": [
      {
        "factId": "fact-uuid",
        "factKey": "facility_investment.amount",
        "label": "투자금액",
        "valueType": "MONEY",
        "rawValue": "2,400억원",
        "normalizedValue": 240000000000,
        "currency": "KRW",
        "unit": "KRW",
        "periodStart": null,
        "periodEnd": null,
        "accountingBasis": null,
        "evidenceIds": ["evidence-2"],
        "validationStatus": "VERIFIED"
      }
    ],
    "outline": [
      {
        "sectionId": "section-uuid",
        "title": "2. 투자내역",
        "sectionType": "TABLE",
        "depth": 1,
        "hasChildren": true
      }
    ],
    "relations": [
      {
        "relationType": "FOLLOWS_UP",
        "targetDisclosureId": "target-uuid",
        "targetReportName": "신규시설투자 완료",
        "targetSubmittedAt": "2026-01-10"
      }
    ],
    "sourceProvider": "CONTEST",
    "datasetVersion": "contest-data-v1"
  }
}
```

#### Error

| 상황 | 에러 코드 |
|---|---|
| 잘못된 ID 형식 | COMMON_400_1 |
| 공시 없음 | DISCLOSURE_404_1 |

### 8.4 공시 원문 섹션 목록

- **URL:** `GET /api/v1/disclosures/{disclosureId}/sections`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** 없음

#### Query Parameters

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| parentSectionId | UUID | 아니요 | null | 특정 섹션의 자식 조회 |
| page | Integer | 아니요 | 0 | 페이지 |
| size | Integer | 아니요 | 50 | 최대 100 |

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "공시 원문 섹션 목록 조회에 성공했습니다.",
  "data": {
    "items": [
      {
        "sectionId": "section-uuid",
        "parentSectionId": null,
        "title": "2. 투자내역",
        "sectionPath": "2. 투자내역",
        "sectionType": "TABLE",
        "depth": 1,
        "displayOrder": 2,
        "hasChildren": true,
        "preview": "투자금액 2,400억원..."
      }
    ],
    "page": {
      "number": 0,
      "size": 50,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  }
}
```

#### Error

| 상황 | 에러 코드 |
|---|---|
| 공시 없음 | DISCLOSURE_404_1 |
| parentSectionId가 해당 공시 소속 아님 | COMMON_404_1 |

### 8.5 공시 원문 섹션 상세

- **URL:** `GET /api/v1/disclosures/{disclosureId}/sections/{sectionId}`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** 없음

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "공시 원문 섹션 조회에 성공했습니다.",
  "data": {
    "sectionId": "section-uuid",
    "disclosureId": "disclosure-uuid",
    "title": "2. 투자내역",
    "sectionPath": "2. 투자내역 > 투자금액",
    "sectionType": "TABLE",
    "content": "투자 내역은 다음과 같습니다.",
    "table": {
      "headers": ["구분", "내용"],
      "rows": [
        ["투자금액", "2,400억원"],
        ["투자목적", "생산능력 확대"]
      ]
    },
    "page": null,
    "startOffset": 100,
    "endOffset": 400
  }
}
```

#### Error

| 상황 | 에러 코드 |
|---|---|
| 공시 없음 | DISCLOSURE_404_1 |
| 섹션 없음 또는 다른 공시 소속 | COMMON_404_1 |

## 9. 데이터·분석 기준 API

### 9.1 분석 정책 조회

- **URL:** `GET /api/v1/meta/analysis-policy`
- **우선순위:** P1 Should
- **상태:** 목표 계약
- **인증:** 없음

#### Description

- 서비스가 사용하는 데이터 범위, 모델, 계산 책임, 답변 한계와 금지사항을 조회합니다.
- IA의 데이터·분석 기준 화면에서 사용합니다.

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "데이터·분석 기준 조회에 성공했습니다.",
  "data": {
    "dataSource": {
      "provider": "CONTEST",
      "periodFrom": "2023-01-01",
      "periodTo": "2026-03-31",
      "categories": [
        "PERIODIC",
        "MATERIAL",
        "EXCHANGE",
        "OWNERSHIP"
      ],
      "datasetVersion": "contest-data-v1",
      "lastIngestedAt": "2026-08-01T03:00:00+09:00"
    },
    "model": {
      "family": "HyperCLOVA X",
      "usage": [
        "질문 구조화",
        "비정형 사실 후보 추출",
        "근거 기반 설명"
      ]
    },
    "backendResponsibilities": [
      "기업 식별 검증",
      "공시 검색",
      "금액·날짜·단위 정규화",
      "증감률·비중 계산",
      "정정·후속공시 관계 확정",
      "근거·수치·금지 표현 검증"
    ],
    "prohibitedSources": [
      "OpenDART 실시간 조회",
      "뉴스",
      "증권사 리포트",
      "위키",
      "검색엔진"
    ],
    "limitations": [
      "제공 공시에서 확인할 수 없는 정보는 답변하지 않습니다.",
      "매수·매도 추천과 목표주가를 제공하지 않습니다."
    ],
    "versions": {
      "retrieval": "hybrid-v1",
      "factExtractor": "fact-v1",
      "calculationRules": "calc-v1",
      "prompt": "grounded-answer-v1"
    }
  }
}
```

## 10. 내부 데이터 적재 API

내부 API는 인터넷에 공개하지 않거나 관리자 인증을 적용합니다.

### 10.1 적재 작업 생성

- **URL:** `POST /internal/v1/datasets/ingestion-jobs`
- **우선순위:** P0 Must
- **상태:** 목표 계약
- **인증:** 관리자

#### Description

- 대회 제공 데이터셋의 적재와 선택적 인덱스 생성을 시작합니다.
- OpenDART URL이나 외부 데이터 URL을 입력받지 않습니다.

#### Request Body

```json
{
  "datasetVersion": "contest-data-v1",
  "manifestPath": "/data/contest-data-v1/manifest.json",
  "buildSearchIndex": true,
  "reprocessFailedOnly": false
}
```

#### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| datasetVersion | String | 예 | 데이터셋 버전 |
| manifestPath | String | 예 | 서버가 허용한 데이터 루트 아래 manifest 경로 |
| buildSearchIndex | Boolean | 아니요 | 적재 후 검색 인덱스 생성, 기본 true |
| reprocessFailedOnly | Boolean | 아니요 | 이전 실패 문서만 재처리 |

#### Response 202

```json
{
  "success": true,
  "code": null,
  "message": "데이터 적재 작업이 생성되었습니다.",
  "data": {
    "jobId": "job-uuid",
    "datasetVersion": "contest-data-v1",
    "status": "PENDING",
    "statusUrl": "/internal/v1/datasets/ingestion-jobs/job-uuid",
    "createdAt": "2026-08-01T03:00:00+09:00"
  }
}
```

#### Error

| 상황 | 에러 코드 |
|---|---|
| 관리자 인증 없음 | COMMON_401_1 |
| 권한 없음 | COMMON_403_1 |
| 허용 루트 밖 경로 | COMMON_400_1 |
| manifest 없음·형식 오류 | COMMON_400_1 |
| 같은 데이터셋 작업 진행 중 | DATASET_409_1 |

### 10.2 적재 작업 상태 조회

- **URL:** `GET /internal/v1/datasets/ingestion-jobs/{jobId}`
- **우선순위:** P0 Must
- **상태:** 목표 계약
- **인증:** 관리자

#### Response 200

```json
{
  "success": true,
  "code": null,
  "message": "데이터 적재 작업 조회에 성공했습니다.",
  "data": {
    "jobId": "job-uuid",
    "datasetVersion": "contest-data-v1",
    "status": "COMPLETED",
    "stage": "INDEXING",
    "counts": {
      "totalFiles": 1000,
      "completedFiles": 992,
      "partialFiles": 5,
      "failedFiles": 3,
      "duplicateFiles": 0,
      "companies": 50,
      "disclosures": 1000,
      "sections": 35000,
      "chunks": 42000
    },
    "qualityIssues": [
      {
        "code": "TABLE_PARSE_PARTIAL",
        "count": 5
      }
    ],
    "startedAt": "2026-08-01T03:00:00+09:00",
    "completedAt": "2026-08-01T03:20:00+09:00"
  }
}
```

#### Status

- `PENDING`
- `VALIDATING`
- `INGESTING`
- `PARSING`
- `INDEXING`
- `COMPLETED`
- `PARTIAL`
- `FAILED`

#### Error

| 상황 | 에러 코드 |
|---|---|
| 인증·권한 없음 | COMMON_401_1 또는 COMMON_403_1 |
| 작업 없음 | COMMON_404_1 |

## 11. 운영 상태 API

### 11.1 종합 상태

- **URL:** `GET /actuator/health`
- **상태:** 부분 구현(Actuator 의존성 존재)
- **인증:** 운영 정책에 따라 상태만 공개

```json
{
  "status": "UP"
}
```

### 11.2 Readiness

- **URL:** `GET /actuator/health/readiness`
- **설명:** 평가 질문을 받을 준비가 되었는지 확인

다음 조건이 준비되어야 `UP`입니다.

- PostgreSQL 연결
- 대회 데이터셋 적재 완료
- 검색 인덱스 준비
- 필수 설정 존재

HyperCLOVA X의 일시 장애를 readiness에 포함할지는 운영 방식에 따라 결정합니다.

### 11.3 Liveness

- **URL:** `GET /actuator/health/liveness`
- **설명:** 애플리케이션 프로세스가 살아 있는지 확인

## 12. P2 포트폴리오 API

다음 경로는 기존 기획을 보존하지만 P0/P1 구현 이후 상세 계약을 다시 확정합니다.

### 12.1 포트폴리오

```http
POST  /api/v1/portfolios
GET   /api/v1/portfolios/{portfolioId}
PATCH /api/v1/portfolios/{portfolioId}
```

### 12.2 보유 종목

```http
POST   /api/v1/portfolios/{portfolioId}/holdings
GET    /api/v1/portfolios/{portfolioId}/holdings/{holdingId}
PATCH  /api/v1/portfolios/{portfolioId}/holdings/{holdingId}
DELETE /api/v1/portfolios/{portfolioId}/holdings/{holdingId}
```

### 12.3 투자 가정

```http
POST   /api/v1/holdings/{holdingId}/theses
PATCH  /api/v1/holdings/{holdingId}/theses/{thesisId}
DELETE /api/v1/holdings/{holdingId}/theses/{thesisId}
```

### 12.4 대시보드

```http
GET /api/v1/portfolios/{portfolioId}/dashboard
```

공통 규칙:

- JWT 필요
- owner와 portfolio 소유권 검증
- 일반 공시 답변과 개인화 해석을 분리
- 매수·매도 추천 금지

## 13. 오류 코드 상세

### 13.1 현재 구현된 공통 코드

| 상황 | HTTP | 코드 | 메시지 |
|---|---:|---|---|
| 입력 오류 | 400 | COMMON_400_1 | 입력값이 올바르지 않습니다. |
| 인증 필요 | 401 | COMMON_401_1 | 인증이 필요합니다. |
| 접근 권한 없음 | 403 | COMMON_403_1 | 접근 권한이 없습니다. |
| 리소스 없음 | 404 | COMMON_404_1 | 요청한 리소스를 찾을 수 없습니다. |
| 내부 오류 | 500 | COMMON_500_1 | 서버 내부 오류가 발생했습니다. |

### 13.2 목표 도메인 코드

| 상황 | HTTP | 코드 | 권장 메시지 |
|---|---:|---|---|
| 질문 형식 오류 | 400 | QUESTION_400_1 | 질문을 입력해 주세요. |
| 질문 길이 초과 | 400 | QUESTION_400_2 | 질문이 허용 길이를 초과했습니다. |
| 명확화 선택 오류 | 400 | QUESTION_400_3 | 선택 가능한 기업 후보가 아닙니다. |
| 질문 접근 권한 없음 | 403 | QUESTION_403_1 | 해당 질문 실행에 접근할 수 없습니다. |
| 질문 실행 없음 | 404 | QUESTION_404_1 | 질문 실행을 찾을 수 없습니다. |
| 잘못된 상태 전이 | 409 | QUESTION_409_1 | 현재 상태에서는 요청을 처리할 수 없습니다. |
| 기업 없음 | 404 | COMPANY_404_1 | 제공 기업 목록에서 기업을 찾을 수 없습니다. |
| 공시 없음 | 404 | DISCLOSURE_404_1 | 공시를 찾을 수 없습니다. |
| 근거 없음 | 404 | EVIDENCE_404_1 | 답변 근거를 찾을 수 없습니다. |
| 계산 없음 | 404 | CALCULATION_404_1 | 계산 기록을 찾을 수 없습니다. |
| 같은 데이터셋 작업 진행 | 409 | DATASET_409_1 | 같은 데이터셋의 적재 작업이 이미 진행 중입니다. |
| 데이터 미준비 | 503 | DATASET_503_1 | 공시 데이터가 아직 준비되지 않았습니다. |
| 모델 호출 제한 | 429 | AGENT_429_1 | 잠시 후 다시 시도해 주세요. |
| 모델 출력 오류 | 502 | AGENT_502_1 | 답변 생성 결과를 검증할 수 없습니다. |
| 검증 실패 | 500 | AGENT_500_1 | 답변 검증을 완료하지 못했습니다. |
| 제한시간 초과 | 504 | AGENT_504_1 | 질문 처리 시간이 초과되었습니다. |

### 13.3 HTTP 오류로 처리하지 않는 상태

| 상황 | 표현 |
|---|---|
| 공시에서 답을 찾을 수 없음 | status=UNANSWERABLE, HTTP 200 |
| 일부 항목만 확인됨 | status=PARTIAL, HTTP 200 |
| 비교 기준 불일치 | limitations 또는 warnings |
| 분모 0으로 계산 불가 | calculation.status=NOT_APPLICABLE |
| 검색 결과가 빈 공시 목록 | items=[], HTTP 200 |

## 14. 질문 실행 상태별 계약

| 상태 | answer | clarification | error | polling |
|---|---|---|---|---|
| RECEIVED | null | null | null | 계속 |
| PLANNING | null | null | null | 계속 |
| CLARIFICATION_REQUIRED | null | 존재 | null | 사용자 입력 대기 |
| RETRIEVING | null | null | null | 계속 |
| EXTRACTING | null | null | null | 계속 |
| CALCULATING | null | null | null | 계속 |
| GENERATING | null | null | null | 계속 |
| VALIDATING | null | null | null | 계속 |
| COMPLETED | 존재 | null | null | 종료 |
| PARTIAL | 존재 | null | null | 종료 |
| UNANSWERABLE | 존재 | null | null | 종료 |
| FAILED | null 또는 검증된 부분 | null | 존재 | 종료 |

## 15. 보안 요구사항

- 평가 API에는 운영진 인증 규격만 적용합니다.
- 웹 질문 실행은 세션 또는 사용자별로 격리합니다.
- opaque run ID만으로 다른 실행에 접근할 수 없어야 합니다.
- 포트폴리오 API는 JWT와 owner 소유권을 모두 검증합니다.
- 내부 적재 API는 인터넷에서 접근하지 못하게 하거나 관리자 인증을 적용합니다.
- 질문과 공시 원문의 명령문은 프롬프트 지시로 신뢰하지 않습니다.
- 모델이 만든 URL을 서버에서 호출하지 않습니다.
- API 키, JWT와 데모 세션 토큰을 로그에 기록하지 않습니다.

## 16. 캐시와 멱등성

### 질문 생성

- `Idempotency-Key`의 범위는 세션 또는 사용자입니다.
- 같은 키와 같은 요청은 기존 run을 반환할 수 있습니다.
- 같은 키와 다른 요청은 409입니다.

### 실행 조회

- 처리 중 응답은 `Cache-Control: no-store`를 권장합니다.
- 종료 답변은 접근 권한을 전제로 짧은 private cache를 사용할 수 있습니다.

### 공개 공시

- 데이터셋 버전이 고정된 경우 ETag를 사용할 수 있습니다.
- 새 데이터셋이 적재되면 ETag와 캐시 키가 변경되어야 합니다.

## 17. 구현 전 필수 변경

### 17.1 ErrorCode

`ErrorCode.java`에 질문·공시·근거·계산·데이터셋·Agent 오류 코드를 추가해야 합니다.

### 17.2 Validation 오류

`ApiResponse.fail(String)` 대신 오류 코드를 보존하는 응답 방식을 추가해야 합니다.

### 17.3 SecurityConfig

다음 경로의 접근 정책을 분리해야 합니다.

```text
GET  /answer                         운영진 규격
POST /api/v1/demo-sessions           permitAll
GET  /api/v1/companies/**            permitAll
GET  /api/v1/disclosures/**          permitAll
GET  /api/v1/meta/**                 permitAll
/api/v1/questions/**                 demo-session 또는 JWT
/api/v1/portfolios/**                JWT
/internal/v1/**                      admin/internal
/actuator/health/**                  운영 정책
```

### 17.4 데이터 모델

기존 `agent_requests`의 owner·portfolio 필수 관계를 제거하거나 일반 `question_runs`를 도입해야 합니다.

필수 추가 모델:

- question_runs
- question_plans
- retrieval_runs
- retrieved_evidences
- calculation_records
- answer_validations
- disclosure_sections
- disclosure_chunks
- dataset_versions
- ingestion_jobs

### 17.5 OpenDART 격리

- 평가 프로필에서 OpenDART Bean을 생성하지 않습니다.
- 평가 DB와 인덱스에 CONTEST 외 데이터를 섞지 않습니다.
- 기존 OpenDART 기업 동기화는 `opendart-dev`에서만 실행합니다.

## 18. 계약 테스트 체크리스트

### 평가 API

- [ ] 한글 질문이 URL 디코딩되는가?
- [ ] question_id가 그대로 반환되는가?
- [ ] retrieved_context에 사용 근거만 포함되는가?
- [ ] think_trace가 내부 사고과정을 포함하지 않는가?
- [ ] 답변 불가도 같은 스키마와 HTTP 200으로 반환되는가?
- [ ] 제한시간 안에 응답하는가?

### 질문 실행

- [ ] 질문 생성이 202와 runId를 반환하는가?
- [ ] 처리 상태가 정의된 순서로 전이되는가?
- [ ] 종료 후 answer가 구조화되어 있는가?
- [ ] 다른 세션이 run을 조회할 수 없는가?
- [ ] 모호한 기업 선택 후 같은 실행이 재개되는가?
- [ ] 중복 명확화 제출이 409인가?

### 근거와 계산

- [ ] FACT 주장이 evidenceId를 갖는가?
- [ ] CALCULATION 주장이 calculationId를 갖는가?
- [ ] 계산 입력의 모든 fact가 근거를 갖는가?
- [ ] 표 근거에 머리글과 단위가 포함되는가?
- [ ] 실행에 속하지 않은 evidence·calculation 조회가 404인가?

### 공시

- [ ] 기업·기간·범주·정정 필터가 적용되는가?
- [ ] 빈 목록이 200과 빈 배열인가?
- [ ] 공시 섹션이 원문 위치로 역추적되는가?
- [ ] 비교 기준이 다르면 계산 결과가 null인가?
- [ ] 정정·후속공시 관계가 시간순으로 반환되는가?

### 대회 규정

- [ ] 모든 근거의 sourceProvider가 CONTEST인가?
- [ ] 평가 프로필에서 OpenDART 호출이 0건인가?
- [ ] 뉴스·검색엔진 호출이 0건인가?
- [ ] HyperCLOVA X 이외 LLM 호출이 없는가?
- [ ] 매수·매도·목표주가·상승 확률이 차단되는가?

## 19. 미확정 계약

1. 평가 API 오류 본문의 최종 형식
2. `retrieved_context`와 `think_trace`가 배열인지 문자열인지
3. 평가 API 인증 헤더
4. 웹 데모 세션 토큰을 헤더와 쿠키 중 어디에 둘지
5. 웹 질문 polling 권장 주기
6. 질문·대화·답변 보존기간
7. 원문 섹션의 page 또는 cursor 방식
8. 공시 비교를 GET 조회와 질문 API 중 어느 쪽에 집중할지
9. 내부 적재 API를 실제로 노출할지 CLI로 대체할지

운영진 규격 또는 팀 결정이 확정되면 상위 API 명세와 함께 갱신합니다.
