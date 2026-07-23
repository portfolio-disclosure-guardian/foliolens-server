# FolioLens API 명세서

| 항목 | 내용 |
|---|---|
| 문서 버전 | v0.2.1 |
| 문서 상태 | 구현 전 검토용 초안 |
| 작성일 | 2026-07-23 |
| 대상 | FolioLens MVP 웹 클라이언트·Spring 백엔드 |
| 기준 문서 | `요구사항_정의서.md`, `기능명세서.md`, `IA.md`, `DECISIONS.md` |
| 구현 기준 | `ApiResponse`, `ErrorCode`, `CustomException`, `GlobalExceptionHandler` |

## 1. 목적과 범위

이 문서는 FolioLens MVP의 프론트엔드와 백엔드 사이에서 사용할 HTTP API 계약을 정의한다.

다음 범위를 포함한다.

- 데모 세션
- 기업 검색
- 포트폴리오와 보유 종목 관리
- 투자 가정 관리
- 포트폴리오 대시보드
- 공시 목록
- 공시 상세 분석
- 원공시·정정공시 비교
- 공시 기반 자연어 질문과 답변 근거
- 데이터·분석 기준 조회
- 내부 공시 수집 작업
- 운영 상태 조회

다음 범위는 공식 규격 또는 팀 결정 이후 별도 버전에서 확정한다.

- 대회 평가 API의 최종 경로와 요청·응답 스키마
- 로그인·회원가입 및 운영 환경 인증 방식
- 알림, 질문 기록, 공통 위험 전용 화면 등 P1 기능
- 외부 OpenDART 사용 여부와 대회 제공 데이터 어댑터의 세부 계약

### 1.1 v0.2 변경 사항

- 실제 구현된 `ApiResponse<T>`의 `success`, `code`, `message`, `data` 구조를 모든 응답 예시에 반영했다.
- 기존 예시의 응답 본문 `requestId`, `errorCode`, `retryable`, `details`를 현재 구현에 맞게 제거했다.
- `CustomException`, `MethodArgumentNotValidException`, 미처리 `Exception`의 처리 결과를 구분했다.
- 현재 구현된 `COMMON_*` 오류 코드와 앞으로 추가할 도메인 오류 코드를 분리했다.
- 페이지 정보는 공통 응답의 최상위가 아니라 `data` 안에서 반환하도록 정리했다.

### 1.2 v0.2.1 변경 사항

- API 요약을 화면·공개 범위 중심 분류에서 URL 리소스별 분류로 변경했다.
- 같은 URL prefix에서 지원하는 HTTP Method와 하위 리소스를 한곳에서 확인할 수 있도록 정리했다.

## 2. 설계 원칙

### 2.1 사실과 해석 분리

- `facts`에는 공시 원문 또는 허용된 구조화 데이터에서 확인한 사실만 포함한다.
- 백엔드 계산 결과는 `calculations` 또는 `changes[].calculation`으로 구분한다.
- Agent 해석은 `thesisImpacts`, `scenarios`, `limitations`에 포함한다.
- 모든 핵심 사실과 해석은 `citationIds`로 근거를 연결한다.

### 2.2 결정적 계산

다음 값은 Spring 백엔드에서 계산한다.

- 금액과 단위 정규화
- 날짜 정규화
- 절대 변화량과 변화율
- 지분 희석률
- 보유 비중 합계
- 중요도 총점과 등급

HyperCLOVA X는 위 값을 새로 계산하지 않고 백엔드가 제공한 사실과 계산 결과를 설명한다.

### 2.3 부분 완료

- 일부 데이터가 없어도 확인된 사실은 반환한다.
- 누락 요소는 `missingFactors`와 `limitations`로 설명한다.
- 분석 상태는 `PENDING`, `PROCESSING`, `COMPLETED`, `PARTIAL`, `FAILED` 중 하나다.

### 2.4 평가 API 분리

사용자 화면용 `/api/v1` 계약과 대회 평가 API 계약을 분리한다.

```text
평가 요청
  → Evaluation API Adapter
  → FolioLens Application Service
  → Evaluation Response Mapper
```

평가 API의 공식 스키마가 공개되더라도 `/api/v1`의 응답 형식을 직접 변경하지 않는다.

## 3. 임시 결정과 미결정 사항

### 3.1 데모 세션

로그인 도입 여부가 미결정이므로 MVP 초안에서는 불투명한 데모 세션 토큰을 사용한다.

- `POST /api/v1/demo-sessions`에서 세션 토큰을 발급한다.
- 사용자 데이터 API는 `X-FolioLens-Session` 헤더를 요구한다.
- 세션 토큰은 충분히 긴 무작위 값이어야 하며 순차 ID를 사용하지 않는다.
- 이 방식은 대회 데모용 임시 계약이며 운영 환경 인증 방식으로 간주하지 않는다.

로그인 방식을 채택하면 `Authorization: Bearer {accessToken}`으로 교체하고 도메인 서비스에는 동일한 `ownerId`만 전달한다.

### 3.2 공식 평가 API

평가 요청·응답 스키마와 제한 시간이 공개되지 않았으므로 이 문서에서는 최종 엔드포인트를 확정하지 않는다.

구현 시 다음 패키지 경계를 유지한다.

```text
adapter.in.evaluation
application.analysis
adapter.out.disclosure
adapter.out.hyperclova
```

### 3.3 외부 공시 데이터

대회 제공 데이터를 우선한다. OpenDART는 대회 규정에서 허용되는 경우에만 외부 어댑터로 사용한다.

외부 공급자별 응답을 컨트롤러 DTO나 도메인 모델로 직접 노출하지 않고 공통 `DisclosureFact` 구조로 변환한다.

## 4. 공통 규약

### 4.1 Base URL

```text
/api/v1
```

내부 운영 API는 다음 경로를 사용한다.

```text
/internal/v1
```

### 4.2 요청 헤더

| 헤더 | 필수 | 설명 |
|---|---|---|
| `Content-Type: application/json` | 본문이 있는 요청 | JSON 요청 |
| `Accept: application/json` | 권장 | JSON 응답 |
| `X-FolioLens-Session` | 사용자 데이터 API | 데모 세션 토큰 |
| `X-Request-Id` | 예정 | 요구사항상 필요한 추적 ID. 현재 공통 응답·예외 처리 코드에는 미구현 |
| `Idempotency-Key` | 일부 POST | 동일 작업 중복 실행 방지 |

`X-Request-Id` 발급과 응답 헤더 전달은 요구사항에는 포함되지만 현재 코드에는 구현되지 않았다. 구현 전까지 API 응답 본문에도 `requestId`를 포함하지 않는다.

### 4.3 식별자

| 식별자 | 형식 |
|---|---|
| `portfolioId` | UUID 문자열 |
| `holdingId` | UUID 문자열 |
| `thesisId` | UUID 문자열 |
| `analysisId` | UUID 문자열 |
| `answerRequestId` | 자연어 질문 답변을 식별하는 문자열 |
| `receiptNo` | 14자리 공시 접수번호 문자열 |
| `corpCode` | 8자리 기업 고유번호 문자열 |
| `stockCode` | 6자리 종목코드 문자열 |

숫자로 변환하면 선행 0이 사라질 수 있으므로 기업·종목·접수번호는 항상 문자열로 처리한다.

### 4.4 필드 이름과 시각

- JSON 필드명은 `camelCase`를 사용한다.
- 날짜는 `YYYY-MM-DD` 형식을 사용한다.
- 시각은 타임존을 포함한 ISO-8601 형식을 사용한다.
- 대한민국 공시 시각 예: `2026-07-23T10:30:00+09:00`.

### 4.5 금액과 비율

- 원문 표현은 `rawValue`에 보존한다.
- 정확한 금액은 JSON 부동소수점 손실을 피하기 위해 10진 문자열로 반환한다.
- 통화는 ISO-4217 코드로 반환한다. 예: `KRW`, `USD`.
- 비율은 퍼센트 단위 숫자로 반환한다. 예: `18.5`는 18.5%를 의미한다.
- 계산할 수 없는 값은 0으로 대체하지 않고 `null`과 사유를 반환한다.

```json
{
  "rawValue": "2,400억원",
  "normalizedValue": "240000000000",
  "currency": "KRW",
  "unit": "WON"
}
```

### 4.6 성공 응답

현재 모든 본문 있는 성공 응답은 `ApiResponse<T>`로 감싼다.

- `success`: 항상 `true`
- `code`: 현재 성공 팩토리에서 설정하지 않으므로 `null`
- `message`: 기본값은 `요청이 성공적으로 처리되었습니다.`
- `data`: API별 응답 DTO

기본 성공 응답:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {}
}
```

커스텀 성공 메시지도 사용할 수 있다.

```json
{
  "success": true,
  "code": null,
  "message": "포트폴리오가 생성되었습니다.",
  "data": {}
}
```

목록과 페이지 정보도 `ApiResponse.data` 안에 포함한다.

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "items": [],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 0,
      "totalPages": 0,
      "hasNext": false
    }
  }
}
```

`204 No Content` 응답은 본문이 없으므로 `ApiResponse`를 사용하지 않는다.

### 4.7 페이지네이션

| 파라미터 | 기본값 | 제한 | 설명 |
|---|---:|---:|---|
| `page` | 0 | 0 이상 | 0부터 시작하는 페이지 번호 |
| `size` | 20 | 1~100 | 페이지 크기 |
| `sort` | API별 기본값 | 허용값만 사용 | 정렬 필드와 방향 |

정렬 예:

```text
sort=submittedAt,desc
```

### 4.8 오류 응답

`CustomException` 응답:

```json
{
  "success": false,
  "code": "COMMON_404_1",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```

`@Valid` 요청 검증 실패 응답:

```json
{
  "success": false,
  "code": null,
  "message": "포트폴리오 이름은 필수입니다., 보유 비중은 0보다 커야 합니다.",
  "data": null
}
```

현재 `GlobalExceptionHandler`는 `MethodArgumentNotValidException`의 모든 필드 메시지를 `, `로 연결한다. 이 경로는 `ApiResponse.fail(String)`을 사용하므로 `code`가 `null`이다.

처리되지 않은 예외 응답:

```json
{
  "success": false,
  "code": "COMMON_500_1",
  "message": "서버 내부 오류가 발생했습니다.",
  "data": null
}
```

| HTTP 상태 | 사용 예 |
|---:|---|
| 400 | JSON 형식, 파라미터, 필드 형식 오류 |
| 401 | 세션 누락 또는 만료 |
| 403 | 다른 세션 소유 데이터 접근 |
| 404 | 포트폴리오, 종목, 공시, 분석 없음 |
| 409 | 중복 종목, 멱등성 키 충돌, 상태 충돌 |
| 422 | 비중 합계 초과, 분석 가능한 근거 부족 등 비즈니스 검증 실패 |
| 429 | 외부 또는 내부 요청 제한 |
| 500 | 처리되지 않은 내부 오류 |
| 502 | 공시 데이터 또는 모델 공급자 오류 |
| 503 | 일시적 서비스 불가 |
| 504 | 외부 API 또는 모델 시간 초과 |

현재 구현된 오류 코드:

| ErrorCode enum | 응답 코드 | HTTP | 기본 메시지 |
|---|---|---:|---|
| `INVALID_INPUT` | `COMMON_400_1` | 400 | 입력값이 올바르지 않습니다. |
| `UNAUTHORIZED` | `COMMON_401_1` | 401 | 인증이 필요합니다. |
| `FORBIDDEN` | `COMMON_403_1` | 403 | 접근 권한이 없습니다. |
| `NOT_FOUND` | `COMMON_404_1` | 404 | 요청한 리소스를 찾을 수 없습니다. |
| `INTERNAL_SERVER_ERROR` | `COMMON_500_1` | 500 | 서버 내부 오류가 발생했습니다. |

도메인 오류 코드는 다음 규칙으로 `ErrorCode`에 추가한다.

```text
{DOMAIN}_{HTTP_STATUS}_{SEQUENCE}
```

예정된 도메인 오류:

| Enum 이름 | 예정 코드 | HTTP |
|---|---|---:|
| `PORTFOLIO_NOT_FOUND` | `PORTFOLIO_404_1` | 404 |
| `HOLDING_NOT_FOUND` | `HOLDING_404_1` | 404 |
| `THESIS_NOT_FOUND` | `THESIS_404_1` | 404 |
| `COMPANY_NOT_FOUND` | `COMPANY_404_1` | 404 |
| `COMPANY_AMBIGUOUS` | `COMPANY_409_1` | 409 |
| `DUPLICATE_HOLDING` | `HOLDING_409_1` | 409 |
| `PORTFOLIO_WEIGHT_EXCEEDED` | `PORTFOLIO_422_1` | 422 |
| `DISCLOSURE_NOT_FOUND` | `DISCLOSURE_404_1` | 404 |
| `DISCLOSURE_CONTENT_MISSING` | `DISCLOSURE_422_1` | 422 |
| `CORRECTION_ORIGINAL_NOT_FOUND` | `DISCLOSURE_422_2` | 422 |
| `UNSUPPORTED_DISCLOSURE` | `DISCLOSURE_422_3` | 422 |
| `INSUFFICIENT_EVIDENCE` | `ANALYSIS_422_1` | 422 |
| `ANALYSIS_ALREADY_RUNNING` | `ANALYSIS_409_1` | 409 |
| `EXTERNAL_API_RATE_LIMITED` | `EXTERNAL_429_1` | 429 |
| `EXTERNAL_API_UNAVAILABLE` | `EXTERNAL_502_1` | 502 |
| `MODEL_TIMEOUT` | `MODEL_504_1` | 504 |

위 도메인 코드는 아직 `ErrorCode` enum에 구현되지 않았으므로 구현 전 이름과 순번을 팀에서 확정한다.

### 4.9 예외 처리 흐름

```text
CustomException
  → 예외가 가진 ErrorCode의 HTTP 상태 사용
  → ApiResponse.fail(ErrorCode)

MethodArgumentNotValidException
  → HTTP 400
  → 필드 오류 메시지를 쉼표로 연결
  → ApiResponse.fail(String)

그 밖의 Exception
  → HTTP 500
  → COMMON_500_1
  → 내부 예외 메시지는 사용자에게 노출하지 않음
```

`CustomException(ErrorCode, String)` 생성자의 커스텀 메시지는 현재 핸들러가 `ApiResponse.fail(e.getErrorCode())`를 호출하기 때문에 응답에 반영되지 않는다. API 계약상 커스텀 메시지가 필요하면 핸들러 또는 응답 팩토리를 수정한 뒤 문서 버전을 올린다.

`PARTIAL`은 가능한 경우 정상 응답으로 반환하고 누락 항목을 데이터에 표시한다. 전체 응답을 만들 수 없을 때만 오류 응답을 사용한다.

## 5. 공통 열거형

### 5.1 공시 유형

초기 내부 유형은 다음과 같이 세분화한다. 최종 지원 목록은 대회 데이터 공개 후 확정한다.

```text
CAPITAL_INCREASE_PAID
CAPITAL_INCREASE_BONUS
CONVERTIBLE_BOND
BOND_WITH_WARRANT
FACILITY_INVESTMENT
SUPPLY_CONTRACT
DIVIDEND
MAJOR_SHAREHOLDER_CHANGE
EMBEZZLEMENT_BREACH_OF_TRUST
TRADING_SUSPENSION
EARNINGS_PRELIMINARY
UNSUPPORTED
```

### 5.2 중요도 등급

```text
URGENT     70~100
CAUTION    40~69
REFERENCE   0~39
```

### 5.3 투자 가정 영향 상태

```text
STRENGTHENED_POSSIBLE
MAINTAINED
WEAKENED_POSSIBLE
NOT_DIRECTLY_RELATED
INSUFFICIENT_EVIDENCE
```

### 5.4 분석 상태

```text
PENDING
PROCESSING
COMPLETED
PARTIAL
FAILED
```

## 6. API 요약

### 6.1 `/api/v1/demo-sessions`

| Method | Path | 설명 | 화면 |
|---|---|---|---|
| POST | `/api/v1/demo-sessions` | 데모 세션 생성 | 공통 |

### 6.2 `/api/v1/companies`

| Method | Path | 설명 | 화면 |
|---|---|---|---|
| GET | `/api/v1/companies` | 종목명·종목코드 검색 | ONB-003, PORT-002 |

### 6.3 `/api/v1/portfolios`

포트폴리오 자체와 포트폴리오에 귀속되는 보유 종목, 대시보드, 공시 분석 API를 포함한다.

| Method | Path | 설명 | 화면 |
|---|---|---|---|
| POST | `/api/v1/portfolios` | 포트폴리오 생성 | ONB-002 |
| GET | `/api/v1/portfolios/{portfolioId}` | 포트폴리오와 보유 종목 조회 | PORT-001 |
| PATCH | `/api/v1/portfolios/{portfolioId}` | 포트폴리오 설정 수정 | SET-001 |
| POST | `/api/v1/portfolios/{portfolioId}/holdings` | 보유 종목 추가 | ONB-003 |
| GET | `/api/v1/portfolios/{portfolioId}/holdings/{holdingId}` | 보유 종목 상세 | PORT-003 |
| PATCH | `/api/v1/portfolios/{portfolioId}/holdings/{holdingId}` | 보유 정보 수정 | PORT-002 |
| DELETE | `/api/v1/portfolios/{portfolioId}/holdings/{holdingId}` | 보유 종목 삭제 | PORT-002 |
| GET | `/api/v1/portfolios/{portfolioId}/dashboard` | 대시보드 조회 | DASH-001 |
| GET | `/api/v1/portfolios/{portfolioId}/disclosures` | 보유 기업 공시 목록 | DISC-001 |
| GET | `/api/v1/portfolios/{portfolioId}/disclosures/{receiptNo}/analysis` | 분석 결과 조회 | DISC-002 |
| POST | `/api/v1/portfolios/{portfolioId}/disclosures/{receiptNo}/analysis` | 분석 생성·재생성 요청 | DISC-002 |

### 6.4 `/api/v1/holdings`

포트폴리오에서 식별된 보유 종목의 투자 가정을 관리한다.

| Method | Path | 설명 | 화면 |
|---|---|---|---|
| POST | `/api/v1/holdings/{holdingId}/theses` | 투자 가정 추가 | ONB-004, THESIS-001 |
| PATCH | `/api/v1/holdings/{holdingId}/theses/{thesisId}` | 투자 가정 수정·비활성화 | THESIS-001 |
| DELETE | `/api/v1/holdings/{holdingId}/theses/{thesisId}` | 투자 가정 삭제 | THESIS-001 |

### 6.5 `/api/v1/disclosures`

포트폴리오에 종속되지 않는 공시 자체의 비교 결과를 조회한다.

| Method | Path | 설명 | 화면 |
|---|---|---|---|
| GET | `/api/v1/disclosures/{receiptNo}/comparison` | 원공시·정정공시 비교 | DISC-003 |

### 6.6 `/api/v1/questions`

| Method | Path | 설명 | 화면 |
|---|---|---|---|
| POST | `/api/v1/questions` | 공시 기반 질문 | CHAT-001 |

### 6.7 `/api/v1/answers`

| Method | Path | 설명 | 화면 |
|---|---|---|---|
| GET | `/api/v1/answers/{answerRequestId}/evidence` | 답변 근거 상세 | CHAT-002 |

### 6.8 `/api/v1/meta`

| Method | Path | 설명 | 화면 |
|---|---|---|---|
| GET | `/api/v1/meta/analysis-policy` | 데이터·분석 기준 | SET-002 |

### 6.9 `/internal/v1/disclosure-sync-jobs`

| Method | Path | 설명 |
|---|---|---|
| POST | `/internal/v1/disclosure-sync-jobs` | 보유 기업 공시 동기화 시작 |
| GET | `/internal/v1/disclosure-sync-jobs/{jobId}` | 동기화 상태 조회 |

### 6.10 `/actuator/health`

| Method | Path | 설명 |
|---|---|---|
| GET | `/actuator/health` | 종합 상태 |
| GET | `/actuator/health/readiness` | 요청 수신 준비 상태 |
| GET | `/actuator/health/liveness` | 프로세스 생존 상태 |

## 7. 데모 세션 API

### 7.1 데모 세션 생성

```http
POST /api/v1/demo-sessions
```

요청 본문은 없다.

응답 `201 Created`:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "sessionToken": "fls_7xVn...opaque-token",
    "expiresAt": "2026-07-24T10:00:00+09:00"
  }
}
```

보안상 응답과 서버 로그에 전체 세션 토큰을 반복해서 기록하지 않는다.

## 8. 기업 검색 API

### 8.1 기업 검색

```http
GET /api/v1/companies?query=삼성&listedOnly=true&page=0&size=20
```

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `query` | 예 | 회사명 또는 종목코드, 1~100자 |
| `listedOnly` | 아니오 | 기본값 `true` |
| `page`, `size` | 아니오 | 페이지네이션 |

검색 우선순위는 종목코드 정확 일치, 정식 회사명 정확 일치, 별칭 일치, 유사명 순이다.

응답 `200 OK`:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "items": [
      {
        "corpCode": "00126380",
        "stockCode": "005930",
        "corpName": "삼성전자",
        "market": "KOSPI",
        "matchType": "EXACT_NAME",
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

후보가 여러 개인 것은 정상 목록 응답이다. 사용자가 후보를 선택하지 않은 채 저장을 요청했을 때만 `COMPANY_AMBIGUOUS`를 반환한다.

## 9. 포트폴리오 API

### 9.1 포트폴리오 생성

```http
POST /api/v1/portfolios
```

요청:

```json
{
  "name": "내 포트폴리오",
  "description": "장기 투자 종목"
}
```

검증:

- `name`: 필수, 공백 제거 후 1~50자
- `description`: 선택, 최대 500자
- MVP에서는 세션당 활성 포트폴리오 1개를 허용한다.

응답 `201 Created`:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
    "name": "내 포트폴리오",
    "description": "장기 투자 종목",
    "totalWeight": 0.0,
    "remainingWeight": 100.0,
    "createdAt": "2026-07-23T10:00:00+09:00",
    "updatedAt": "2026-07-23T10:00:00+09:00"
  }
}
```

### 9.2 포트폴리오 조회

```http
GET /api/v1/portfolios/{portfolioId}
```

응답 `200 OK`:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
    "name": "내 포트폴리오",
    "description": "장기 투자 종목",
    "totalWeight": 58.0,
    "remainingWeight": 42.0,
    "holdings": [
      {
        "holdingId": "8aad34af-c7c9-4fd8-b191-39ae6609426a",
        "company": {
          "corpCode": "00126380",
          "stockCode": "005930",
          "corpName": "삼성전자"
        },
        "weight": 18.0,
        "activeThesisCount": 2,
        "urgentDisclosureCount": 1,
        "cautionDisclosureCount": 2,
        "latestImportantDisclosure": {
          "receiptNo": "20260721000001",
          "reportName": "신규시설투자등",
          "submittedAt": "2026-07-21T10:00:00+09:00"
        },
        "lastAnalyzedAt": "2026-07-23T09:30:00+09:00"
      }
    ],
    "createdAt": "2026-07-23T10:00:00+09:00",
    "updatedAt": "2026-07-23T10:10:00+09:00"
  }
}
```

### 9.3 포트폴리오 수정

```http
PATCH /api/v1/portfolios/{portfolioId}
```

요청:

```json
{
  "name": "장기 성장 포트폴리오",
  "description": "반도체와 배터리 중심"
}
```

응답은 수정된 포트폴리오 요약을 반환한다.

## 10. 보유 종목 API

### 10.1 보유 종목 추가

```http
POST /api/v1/portfolios/{portfolioId}/holdings
```

요청:

```json
{
  "corpCode": "00126380",
  "stockCode": "005930",
  "weight": 18.0,
  "quantity": "10",
  "averagePrice": {
    "amount": "72000",
    "currency": "KRW"
  },
  "investmentStartedOn": "2025-03-12",
  "memo": "장기 보유"
}
```

검증:

- `corpCode`, `stockCode`, 정식 회사명이 기업 기준정보와 일치해야 한다.
- `weight`는 0 초과 100 이하다.
- 추가 후 포트폴리오 전체 비중이 100을 초과하면 저장하지 않는다.
- 같은 회사의 중복 보유를 허용하지 않는다.
- `quantity`, `averagePrice`, `investmentStartedOn`, `memo`는 선택값이다.

응답 `201 Created`:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "holdingId": "8aad34af-c7c9-4fd8-b191-39ae6609426a",
    "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
    "company": {
      "corpCode": "00126380",
      "stockCode": "005930",
      "corpName": "삼성전자"
    },
    "weight": 18.0,
    "quantity": "10",
    "averagePrice": {
      "amount": "72000",
      "currency": "KRW"
    },
    "investmentStartedOn": "2025-03-12",
    "memo": "장기 보유",
    "createdAt": "2026-07-23T10:10:00+09:00",
    "updatedAt": "2026-07-23T10:10:00+09:00"
  }
}
```

### 10.2 보유 종목 상세 조회

```http
GET /api/v1/portfolios/{portfolioId}/holdings/{holdingId}
```

응답에는 보유 정보, 활성 투자 가정, 최근 중요 공시를 포함한다.

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "holdingId": "8aad34af-c7c9-4fd8-b191-39ae6609426a",
    "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
    "company": {
      "corpCode": "00126380",
      "stockCode": "005930",
      "corpName": "삼성전자"
    },
    "weight": 18.0,
    "quantity": "10",
    "averagePrice": {
      "amount": "72000",
      "currency": "KRW"
    },
    "investmentStartedOn": "2025-03-12",
    "memo": "장기 보유",
    "theses": [],
    "recentImportantDisclosures": [],
    "createdAt": "2026-07-23T10:10:00+09:00",
    "updatedAt": "2026-07-23T10:10:00+09:00"
  }
}
```

### 10.3 보유 정보 수정

```http
PATCH /api/v1/portfolios/{portfolioId}/holdings/{holdingId}
```

요청에는 변경할 필드만 포함한다.

```json
{
  "weight": 22.0,
  "quantity": "12",
  "memo": "비중 조정"
}
```

비중 또는 투자 가정이 바뀌면 기존 개인화 분석 캐시를 무효화한다.

### 10.4 보유 종목 삭제

```http
DELETE /api/v1/portfolios/{portfolioId}/holdings/{holdingId}
```

응답 `204 No Content`.

MVP에서는 삭제된 보유 종목의 공시 원문과 공통 사실 데이터는 유지할 수 있지만, 세션에 귀속된 투자 가정과 개인화 분석은 보존 정책에 따라 삭제하거나 비활성화한다.

## 11. 투자 가정 API

### 11.1 투자 가정 추가

```http
POST /api/v1/holdings/{holdingId}/theses
```

요청:

```json
{
  "originalText": "HBM 관련 매출이 계속 성장할 것으로 기대한다."
}
```

검증:

- `originalText`: 필수, 공백 제거 후 1~1000자
- 사용자 원문은 구조화 후에도 수정 없이 보존한다.

응답 `201 Created`:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "thesisId": "b00fb35b-9fde-48e8-b82b-29c3ec37209f",
    "holdingId": "8aad34af-c7c9-4fd8-b191-39ae6609426a",
    "originalText": "HBM 관련 매출이 계속 성장할 것으로 기대한다.",
    "topic": "HBM",
    "expectedDirection": "GROWTH",
    "metrics": [
      "HBM 관련 사업 매출",
      "설비투자",
      "영업이익"
    ],
    "active": true,
    "structureStatus": "COMPLETED",
    "createdAt": "2026-07-23T10:20:00+09:00",
    "updatedAt": "2026-07-23T10:20:00+09:00"
  }
}
```

모델 구조화가 실패하면 투자 가정 원문 저장은 성공시킬 수 있다.

```json
{
  "structureStatus": "FAILED",
  "topic": null,
  "expectedDirection": null,
  "metrics": [],
  "structureFailureReason": "MODEL_TIMEOUT"
}
```

### 11.2 투자 가정 수정

```http
PATCH /api/v1/holdings/{holdingId}/theses/{thesisId}
```

요청:

```json
{
  "originalText": "HBM 매출 성장과 수익성 개선을 확인한다.",
  "topic": "HBM 수익성",
  "expectedDirection": "GROWTH",
  "metrics": ["HBM 매출", "영업이익률"],
  "active": true
}
```

사용자가 구조화 결과를 수정한 값은 이후 분석에서 모델 생성값보다 우선한다.

### 11.3 투자 가정 삭제

```http
DELETE /api/v1/holdings/{holdingId}/theses/{thesisId}
```

응답 `204 No Content`.

내부 구현은 이력 보존을 위해 비활성화 또는 소프트 삭제를 사용할 수 있다. 사용자 조회 결과에는 노출하지 않는다.

## 12. 대시보드 API

### 12.1 포트폴리오 대시보드 조회

```http
GET /api/v1/portfolios/{portfolioId}/dashboard?limit=10
```

응답 `200 OK`:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "portfolio": {
      "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
      "name": "내 포트폴리오",
      "holdingCount": 3,
      "totalWeight": 76.0
    },
    "summary": {
      "urgentDisclosureCount": 1,
      "cautionDisclosureCount": 2,
      "referenceDisclosureCount": 4,
      "lastDataRefreshedAt": "2026-07-23T09:55:00+09:00"
    },
    "importantDisclosures": [
      {
        "receiptNo": "20260721000001",
        "company": {
          "corpCode": "00126380",
          "stockCode": "005930",
          "corpName": "삼성전자"
        },
        "holdingWeight": 18.0,
        "reportName": "신규시설투자등(정정)",
        "disclosureType": "FACILITY_INVESTMENT",
        "submittedAt": "2026-07-21T10:00:00+09:00",
        "correction": true,
        "importance": {
          "score": 78,
          "level": "URGENT",
          "reasons": [
            "포트폴리오 비중 18%",
            "투자금액 20% 증가",
            "활성 투자 가정과 직접 관련"
          ]
        },
        "headline": "투자금액이 400억 원 증가하고 완료 예정일이 8개월 연기됐습니다.",
        "affectedTheses": [
          {
            "thesisId": "b00fb35b-9fde-48e8-b82b-29c3ec37209f",
            "originalText": "신규 배터리 사업 성장",
            "status": "WEAKENED_POSSIBLE"
          }
        ],
        "analysisStatus": "COMPLETED"
      }
    ],
    "thesisImpactCounts": {
      "strengthenedPossible": 0,
      "maintained": 1,
      "weakenedPossible": 1,
      "insufficientEvidence": 1
    },
    "attentionItems": [
      {
        "type": "THESIS_MISSING",
        "holdingId": "e5224545-e5f9-414a-a645-e37cd9085323",
        "message": "투자 이유가 등록되지 않은 종목이 있습니다."
      }
    ]
  }
}
```

정렬:

1. 중요도 점수 내림차순
2. 점수가 같으면 접수 시각 내림차순

## 13. 공시 목록 API

### 13.1 보유 기업 공시 목록

```http
GET /api/v1/portfolios/{portfolioId}/disclosures
```

필터:

| 파라미터 | 설명 |
|---|---|
| `importanceLevel` | `URGENT`, `CAUTION`, `REFERENCE`. 반복 전달 가능 |
| `stockCode` | 보유 종목코드. 반복 전달 가능 |
| `disclosureType` | 공시 유형. 반복 전달 가능 |
| `correction` | 정정공시 여부 |
| `submittedFrom` | 접수 시작일 |
| `submittedTo` | 접수 종료일 |
| `analysisStatus` | 분석 상태 |
| `page`, `size` | 페이지네이션 |
| `sort` | `importanceScore,desc` 기본값 |

응답 목록 항목:

```json
{
  "receiptNo": "20260721000001",
  "company": {
    "corpCode": "00126380",
    "stockCode": "005930",
    "corpName": "삼성전자"
  },
  "holdingWeight": 18.0,
  "reportName": "신규시설투자등(정정)",
  "disclosureType": "FACILITY_INVESTMENT",
  "submittedAt": "2026-07-21T10:00:00+09:00",
  "correction": true,
  "importance": {
    "score": 78,
    "level": "URGENT"
  },
  "headline": "투자금액 증가 및 완료 예정일 연기",
  "thesisRelated": true,
  "analysisStatus": "COMPLETED",
  "sourceUrl": "https://dart.fss.or.kr/...",
  "dataAsOf": "2026-07-23T09:55:00+09:00"
}
```

현재 포트폴리오에 없는 기업의 공시는 반환하지 않는다.

## 14. 공시 분석 API

### 14.1 분석 생성 요청

```http
POST /api/v1/portfolios/{portfolioId}/disclosures/{receiptNo}/analysis
Idempotency-Key: analysis-{portfolioId}-{receiptNo}-{inputVersion}
```

요청:

```json
{
  "force": false
}
```

- 유효한 캐시가 있으면 기존 분석을 반환할 수 있다.
- `force=true`는 운영 정책상 허용된 경우에만 재분석한다.
- 같은 입력 버전에 대한 중복 요청은 새로운 분석을 만들지 않는다.

응답 `202 Accepted`:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "analysisId": "65f446ed-0afd-48f1-9c61-7432bb606179",
    "status": "PENDING",
    "receiptNo": "20260721000001",
    "statusUrl": "/api/v1/portfolios/69b13ed7-5a34-4f5b-9858-0a71d7035e92/disclosures/20260721000001/analysis",
    "requestedAt": "2026-07-23T10:30:00+09:00"
  }
}
```

### 14.2 분석 결과 조회

```http
GET /api/v1/portfolios/{portfolioId}/disclosures/{receiptNo}/analysis
```

처리 중에도 `200 OK`로 현재 상태를 반환한다.

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "analysisId": "65f446ed-0afd-48f1-9c61-7432bb606179",
    "status": "PROCESSING",
    "progress": {
      "step": "THESIS_IMPACT_ANALYSIS",
      "completedSteps": 5,
      "totalSteps": 8
    }
  }
}
```

완료 응답의 `data`는 다음 표준 분석 결과를 따른다.

### 14.3 표준 분석 결과

```json
{
  "analysisId": "65f446ed-0afd-48f1-9c61-7432bb606179",
  "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
  "status": "COMPLETED",
  "company": {
    "corpCode": "00126380",
    "stockCode": "005930",
    "corpName": "삼성전자",
    "holdingWeight": 18.0
  },
  "disclosure": {
    "receiptNo": "20260721000001",
    "reportName": "신규시설투자등(정정)",
    "disclosureType": "FACILITY_INVESTMENT",
    "submittedAt": "2026-07-21T10:00:00+09:00",
    "correction": true,
    "originalReceiptNo": "20260312000001",
    "sourceUrl": "https://dart.fss.or.kr/..."
  },
  "importance": {
    "score": 78,
    "level": "URGENT",
    "ruleVersion": "importance-v1",
    "breakdown": {
      "holdingWeight": {"score": 20, "maxScore": 35},
      "disclosureType": {"score": 23, "maxScore": 30},
      "financialMagnitude": {"score": 15, "maxScore": 15},
      "thesisRelevance": {"score": 10, "maxScore": 10},
      "correctionRepeat": {"score": 5, "maxScore": 5},
      "financialVulnerability": {"score": 5, "maxScore": 5}
    },
    "reasons": [
      "포트폴리오 비중 18%",
      "투자금액 20% 증가",
      "핵심 투자 가정과 직접 관련"
    ],
    "missingFactors": []
  },
  "facts": [
    {
      "factId": "fact-investment-amount",
      "fieldName": "investmentAmount",
      "label": "투자금액",
      "valueType": "MONEY",
      "rawValue": "2,400억원",
      "normalizedValue": "240000000000",
      "currency": "KRW",
      "unit": "WON",
      "period": null,
      "accountingBasis": null,
      "sourceType": "DISCLOSURE_DOCUMENT",
      "citationIds": ["citation-2"]
    },
    {
      "factId": "fact-completion-date",
      "fieldName": "expectedCompletionDate",
      "label": "투자 종료 예정일",
      "valueType": "DATE",
      "rawValue": "2027년 11월 30일",
      "normalizedValue": "2027-11-30",
      "currency": null,
      "unit": null,
      "period": null,
      "accountingBasis": null,
      "sourceType": "DISCLOSURE_DOCUMENT",
      "citationIds": ["citation-3"]
    }
  ],
  "changes": [
    {
      "fieldName": "investmentAmount",
      "label": "투자금액",
      "valueType": "MONEY",
      "before": {
        "rawValue": "2,000억원",
        "normalizedValue": "200000000000",
        "citationIds": ["citation-1"]
      },
      "after": {
        "rawValue": "2,400억원",
        "normalizedValue": "240000000000",
        "citationIds": ["citation-2"]
      },
      "calculation": {
        "absoluteChange": "40000000000",
        "changeRatePercent": 20.0,
        "formula": "(after - before) / before * 100",
        "inputFactIds": ["original-investment-amount", "fact-investment-amount"]
      },
      "changeReason": null
    },
    {
      "fieldName": "expectedCompletionDate",
      "label": "투자 종료 예정일",
      "valueType": "DATE",
      "before": {
        "normalizedValue": "2027-03-31",
        "citationIds": ["citation-4"]
      },
      "after": {
        "normalizedValue": "2027-11-30",
        "citationIds": ["citation-3"]
      },
      "calculation": {
        "dateShiftDays": 244,
        "humanReadable": "약 8개월 연기",
        "formula": "afterDate - beforeDate",
        "inputFactIds": ["original-completion-date", "fact-completion-date"]
      },
      "changeReason": null
    }
  ],
  "thesisImpacts": [
    {
      "thesisId": "b00fb35b-9fde-48e8-b82b-29c3ec37209f",
      "originalText": "신규 배터리 사업 성장",
      "status": "WEAKENED_POSSIBLE",
      "summary": "투자 목적은 유지됐지만 완료 일정이 연기됐습니다.",
      "impactPath": "완료 일정 연기 → 생산능력 확대 지연 가능성 → 관련 매출 반영 시점 지연 가능성",
      "counterpoint": "투자 목적과 사업 방향은 변경되지 않았습니다.",
      "uncertainty": "실제 공사 진행률과 분기별 집행액은 현재 공시만으로 확인할 수 없습니다.",
      "confidence": 0.82,
      "relatedFactIds": ["fact-investment-amount", "fact-completion-date"],
      "citationIds": ["citation-2", "citation-3"]
    }
  ],
  "scenarios": {
    "positive": [
      {
        "description": "증액된 투자금이 계획대로 집행되면 생산능력 확대가 이어질 수 있습니다.",
        "conditions": ["분기별 투자 집행이 계획대로 진행", "관련 생산시설 가동 일정 유지"],
        "citationIds": ["citation-2"]
      }
    ],
    "negative": [
      {
        "description": "일정 지연이 반복되면 사업화 시점과 현금흐름 부담을 추가로 확인해야 합니다.",
        "conditions": ["완료 예정일 추가 연기", "차입금 증가"],
        "citationIds": ["citation-3"]
      }
    ]
  },
  "nextMetrics": [
    {
      "name": "분기별 투자 집행액",
      "reason": "증액된 투자금이 실제로 집행되는지 확인하기 위해 필요합니다.",
      "availability": "NOT_AVAILABLE",
      "expectedSource": "다음 분기보고서 또는 정정공시"
    }
  ],
  "limitations": [
    {
      "code": "EXECUTION_PROGRESS_UNKNOWN",
      "message": "현재 공시만으로 실제 공사 진행률을 확인할 수 없습니다."
    }
  ],
  "citations": [
    {
      "citationId": "citation-2",
      "receiptNo": "20260721000001",
      "sourceUrl": "https://dart.fss.or.kr/...",
      "documentTitle": "신규시설투자등(정정)",
      "section": "2. 투자내역",
      "fieldLabel": "투자금액",
      "evidenceText": "투자금액 240,000,000,000원"
    }
  ],
  "dataAsOf": "2026-07-23T09:55:00+09:00",
  "generatedAt": "2026-07-23T10:31:15+09:00",
  "versions": {
    "model": "configured-hcx-model",
    "prompt": "disclosure-analysis-v1",
    "importanceRule": "importance-v1",
    "extractor": "facility-investment-v1",
    "schema": "analysis-result-v1"
  }
}
```

실제 구현과 계약 테스트는 모든 breakdown 점수에 `0 <= score <= maxScore`를 적용하고 총점이 각 점수의 합과 일치하는지 검증한다.

### 14.4 중요도 계산 계약

```text
score
= holdingWeight.score
+ disclosureType.score
+ financialMagnitude.score
+ thesisRelevance.score
+ correctionRepeat.score
+ financialVulnerability.score
```

| 요소 | 최대점수 |
|---|---:|
| 보유 비중 | 35 |
| 공시 유형 | 30 |
| 기업 대비 재무 규모 | 15 |
| 투자 가정 관련성 | 10 |
| 정정·반복성 | 5 |
| 재무 취약성 | 5 |

데이터가 없어서 평가하지 못한 요소는 0점으로 계산하되 반드시 `missingFactors`에 포함한다.

## 15. 원공시·정정공시 비교 API

### 15.1 비교 조회

```http
GET /api/v1/disclosures/{receiptNo}/comparison?changedOnly=true
```

`receiptNo`가 정정공시이면 연결된 원공시와 비교한다. 원공시이면 연결된 최신 정정공시가 있을 때 해당 정정공시와 비교한다.

응답:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "originalDisclosure": {
      "receiptNo": "20260312000001",
      "reportName": "신규시설투자등",
      "submittedAt": "2026-03-12T09:30:00+09:00",
      "sourceUrl": "https://dart.fss.or.kr/..."
    },
    "correctedDisclosure": {
      "receiptNo": "20260721000001",
      "reportName": "신규시설투자등(정정)",
      "submittedAt": "2026-07-21T10:00:00+09:00",
      "sourceUrl": "https://dart.fss.or.kr/..."
    },
    "changes": [],
    "dataAsOf": "2026-07-23T09:55:00+09:00"
  }
}
```

비교 대상 원공시를 찾지 못하면 정정공시 자체 분석은 유지하고 이 API는 `CORRECTION_ORIGINAL_NOT_FOUND`와 확인 가능한 메타데이터를 반환한다.

## 16. 질문 API

### 16.1 공시 기반 질문

```http
POST /api/v1/questions
```

요청:

```json
{
  "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
  "query": "이 시설투자 정정공시가 내 투자 이유에 어떤 영향을 줘?",
  "scope": "DISCLOSURE",
  "selectedHoldingId": "8aad34af-c7c9-4fd8-b191-39ae6609426a",
  "selectedReceiptNo": "20260721000001",
  "conversationId": null
}
```

검증:

- `query`: 필수, 1~2000자
- `scope`: `PORTFOLIO`, `HOLDING`, `DISCLOSURE`
- `DISCLOSURE` 범위에서는 `selectedReceiptNo`가 필수다.
- 선택된 공시는 대화에서 추정한 공시보다 항상 우선한다.
- 선택된 종목과 공시는 해당 포트폴리오에 포함돼야 한다.

응답 `200 OK`:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "answerRequestId": "answer-01K0EXAMPLE",
    "status": "SUCCESS",
    "answer": "투자 목적은 유지됐지만 완료 예정일이 약 8개월 연기되어 사업화 일정은 추가 확인이 필요합니다.",
    "portfolioRelevance": "해당 종목은 포트폴리오의 18%이며 등록한 신규 사업 성장 가정과 직접 관련됩니다.",
    "uncertainties": [
      "실제 공사 진행률은 현재 공시만으로 확인할 수 없습니다."
    ],
    "evidence": [
      {
        "citationId": "citation-3",
        "claim": "완료 예정일이 약 8개월 연기됐습니다.",
        "receiptNo": "20260721000001",
        "sourceUrl": "https://dart.fss.or.kr/..."
      }
    ],
    "toolsUsed": [
      "portfolioLookup",
      "disclosureLookup",
      "correctionComparator",
      "thesisLookup"
    ],
    "suggestedQuestions": [
      "투자금액은 얼마나 증가했어?",
      "다음 분기에 어떤 지표를 확인해야 해?"
    ],
    "confidence": 0.84,
    "latencyMs": 1830
  }
}
```

### 16.2 명확화 응답

회사가 둘 이상이거나 기간·공시가 불명확하면 임의로 선택하지 않는다.

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "answerRequestId": "answer-01K0EXAMPLE",
    "status": "CLARIFICATION_REQUIRED",
    "answer": null,
    "clarificationQuestion": "삼성전자와 삼성SDI 중 어느 기업을 의미하나요?",
    "candidates": [
      {
        "holdingId": "8aad34af-c7c9-4fd8-b191-39ae6609426a",
        "stockCode": "005930",
        "corpName": "삼성전자"
      },
      {
        "holdingId": "e5224545-e5f9-414a-a645-e37cd9085323",
        "stockCode": "006400",
        "corpName": "삼성SDI"
      }
    ],
    "toolsUsed": ["portfolioLookup"]
  }
}
```

질문 응답 상태:

```text
SUCCESS
CLARIFICATION_REQUIRED
PARTIAL
UNSUPPORTED
INSUFFICIENT_EVIDENCE
```

투자 권유 질문에도 HTTP 오류를 반환하지 않는다. `SUCCESS` 또는 `INSUFFICIENT_EVIDENCE` 상태로 확인 가능한 사실, 조건별 시나리오와 한계를 제공하고 직접 매수·매도 지시는 제외한다.

### 16.3 답변 근거 상세

```http
GET /api/v1/answers/{answerRequestId}/evidence
```

IA 초안의 `{requestId}`는 질문 답변을 식별하는 값이다. HTTP 요청 추적용 `requestId`와 혼동하지 않도록 이 명세에서는 `answerRequestId`로 이름을 구분한다.

응답:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "answerRequestId": "answer-01K0EXAMPLE",
    "claims": [
      {
        "claimId": "claim-1",
        "text": "투자금액이 20% 증가했습니다.",
        "citationIds": ["citation-1", "citation-2"],
        "calculation": {
          "formula": "(after - before) / before * 100",
          "inputs": {
            "before": "200000000000",
            "after": "240000000000"
          },
          "result": 20.0
        }
      }
    ],
    "citations": [],
    "dataAsOf": "2026-07-23T09:55:00+09:00"
  }
}
```

## 17. 데이터·분석 기준 API

### 17.1 분석 정책 조회

```http
GET /api/v1/meta/analysis-policy
```

세션 없이 조회할 수 있다.

응답:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "serviceNotice": "FolioLens는 투자 권유가 아닌 공시 정보 분석 도구입니다.",
    "dataSources": [
      {
        "name": "COMPETITION_DATA",
        "priority": 1,
        "enabled": true
      },
      {
        "name": "OPENDART",
        "priority": 2,
        "enabled": false,
        "reason": "대회 허용 정책 확인 필요"
      }
    ],
    "importance": {
      "ruleVersion": "importance-v1",
      "levels": {
        "URGENT": "70~100",
        "CAUTION": "40~69",
        "REFERENCE": "0~39"
      },
      "factors": [
        "HOLDING_WEIGHT",
        "DISCLOSURE_TYPE",
        "FINANCIAL_MAGNITUDE",
        "THESIS_RELEVANCE",
        "CORRECTION_REPEAT",
        "FINANCIAL_VULNERABILITY"
      ]
    },
    "thesisStatuses": [
      "STRENGTHENED_POSSIBLE",
      "MAINTAINED",
      "WEAKENED_POSSIBLE",
      "NOT_DIRECTLY_RELATED",
      "INSUFFICIENT_EVIDENCE"
    ],
    "modelResponsibilities": [
      "질문 구조화",
      "투자 가정 구조화",
      "관련성 판단과 조건부 설명"
    ],
    "backendResponsibilities": [
      "공시 조회와 원문 근거 연결",
      "금액·날짜·단위 정규화",
      "변화율·희석률·중요도 계산",
      "생성 결과 검증"
    ],
    "knownLimitations": [
      "지원하지 않는 공시 유형은 기본 메타데이터와 원문만 제공할 수 있습니다."
    ],
    "updatedAt": "2026-07-23T00:00:00+09:00"
  }
}
```

## 18. 내부 공시 동기화 API

브라우저에서 호출하지 않는다. 서비스 계정 또는 내부 네트워크 인증을 적용한다.

### 18.1 동기화 작업 생성

```http
POST /internal/v1/disclosure-sync-jobs
Idempotency-Key: disclosure-sync-{portfolioId}-{from}-{to}
```

요청:

```json
{
  "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
  "from": "2026-07-01",
  "to": "2026-07-23",
  "provider": "COMPETITION_DATA"
}
```

응답 `202 Accepted`:

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "jobId": "fd756c8b-c94f-4a5b-92bb-e9a59c943535",
    "status": "PENDING",
    "createdAt": "2026-07-23T11:00:00+09:00"
  }
}
```

### 18.2 동기화 상태 조회

```http
GET /internal/v1/disclosure-sync-jobs/{jobId}
```

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "jobId": "fd756c8b-c94f-4a5b-92bb-e9a59c943535",
    "status": "COMPLETED",
    "provider": "COMPETITION_DATA",
    "companyCount": 3,
    "fetchedDisclosureCount": 12,
    "createdDisclosureCount": 4,
    "updatedDisclosureCount": 1,
    "failedDisclosureCount": 0,
    "startedAt": "2026-07-23T11:00:01+09:00",
    "completedAt": "2026-07-23T11:00:08+09:00",
    "failures": []
  }
}
```

동일 접수번호와 동일 원문 해시에 대한 반복 동기화는 데이터 상태를 바꾸지 않는다.

## 19. 운영 상태 API

Spring Boot Actuator의 표준 health group 경로를 사용한다.

```http
GET /actuator/health
GET /actuator/health/readiness
GET /actuator/health/liveness
```

- 외부에는 필요한 최소 정보만 노출한다.
- DB, 공시 공급자, 모델 공급자의 상세 오류나 인증정보를 노출하지 않는다.
- readiness는 요청을 정상 처리할 준비가 됐는지 나타낸다.
- liveness는 프로세스를 재시작해야 하는 상태인지 나타낸다.

기존 기능명세서에 적힌 `/actuator/readiness`, `/actuator/liveness` 경로가 반드시 필요하면 위 표준 경로를 호출하는 별도 alias를 둔다.

## 20. 캐시와 무효화

분석 캐시 키에는 최소한 다음 값이 포함돼야 한다.

```text
portfolioId
receiptNo
disclosureDocumentHash
portfolioWeightVersion
thesisVersion
importanceRuleVersion
promptVersion
modelVersion
```

다음 상황에서는 기존 분석을 재사용하지 않는다.

- 정정공시 신규 제출
- 포트폴리오 비중 변경
- 투자 가정 변경
- 공시 원문 해시 변경
- 중요도 규칙 버전 변경
- 재평가가 필요한 모델·프롬프트 버전 변경

## 21. 보안과 로그

- 세션 토큰, API 키, DB 비밀번호를 로그에 기록하지 않는다.
- 평균 매입가와 수량은 분석에 필요하지 않은 로그에서 제외한다.
- 공시 원문의 문장을 시스템 명령으로 취급하지 않는다.
- 질문, 투자 가정, 공시 본문은 모델 입력 데이터로 명확히 구분한다.
- 추적 ID 구현 후 모든 로그에 `requestId`, 처리 상태, 지연시간, 오류 코드를 기록한다.
- 외부 호출 로그에는 공급자, 상태 코드, 지연시간만 기록하고 인증정보는 제거한다.
- 다른 세션의 포트폴리오에 대한 존재 여부를 추측할 수 없도록 403/404 정책을 일관되게 적용한다.

## 22. 계약 테스트 필수 항목

### 22.1 포트폴리오

- 비중 0 이하 거절
- 개별 비중 100 초과 거절
- 합계 비중 100 초과 거절
- 같은 기업 중복 등록 거절
- 다른 세션의 포트폴리오 접근 거절

### 22.2 공시 사실과 계산

- 금액 원문값·정규화값·통화·단위 보존
- 날짜 ISO-8601 정규화
- 연결·별도 기준이 다른 수치 비교 중단
- 이전 값이 0일 때 변화율 `null` 처리 및 절대 변화량 반환
- 계산 결과와 입력 fact 연결
- 정정 전후 citation 연결

### 22.3 중요도

- breakdown 각 점수가 허용 범위 안에 있음
- 총점이 breakdown 합과 일치함
- 총점에 맞는 등급 반환
- 데이터 부족 요소가 `missingFactors`에 포함됨
- 같은 입력과 규칙 버전에서 같은 결과 반환

### 22.4 모델 출력

- 답변 숫자가 facts 또는 calculations에 존재함
- 핵심 주장에 citation이 있음
- 근거 없는 회사·날짜·금액 차단
- 매수·매도·목표주가·수익 보장 표현 차단
- 모델 실패 시 사실 중심 부분 응답 제공

### 22.5 외부 장애

- 타임아웃과 제한된 재시도
- 요청 제한 시 429 변환
- 원문 실패 시 `PARTIAL` 결과
- 같은 동기화 작업 반복 시 멱등성 보장

## 23. MVP 이후 확장 후보

다음 API는 현재 계약에 포함하지 않는다.

```text
GET  /api/v1/portfolios/{portfolioId}/risks
GET  /api/v1/alerts
PATCH /api/v1/alerts/{alertId}
GET  /api/v1/conversations
GET  /api/v1/portfolios/{portfolioId}/weekly-reports
PUT  /api/v1/portfolios/{portfolioId}/disclosures/{receiptNo}/read
```

P1 기능을 추가할 때 기존 MVP 응답의 필수 필드를 제거하거나 의미를 변경하지 않는다.

## 24. 확정이 필요한 결정

구현 시작 전에 다음 항목을 팀에서 승인한다.

1. 데모 세션을 사용할지 로그인 기능을 구현할지
2. 세션당 포트폴리오를 1개로 제한할지
3. 평균 매입가와 수량을 MVP에서 입력받을지
4. 공시 상세 분석 생성 API를 완전 비동기로 할지, 15초 이내 동기 응답도 허용할지
5. 공시 목록에 읽음·안읽음 상태를 MVP에서 포함할지
6. 구조화된 투자 가정의 사용자 수정 기능을 MVP에 포함할지
7. 대회 데이터의 공시 유형·접수번호·원문 필드 형식
8. OpenDART 사용 허용 여부
9. 공식 평가 API 스키마와 제한 시간

위 결정으로 계약이 변경되면 문서 버전을 올리고 변경 이력을 기록한다.
