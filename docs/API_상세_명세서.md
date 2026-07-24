# FolioLens API 상세 명세서

| 항목 | 내용 |
|---|---|
| 문서 버전 | v0.1 |
| 문서 상태 | 구현 전 검토용 초안 |
| 작성일 | 2026-07-24 |
| 대상 | FolioLens MVP 프론트엔드·Spring 백엔드 |
| 상위 계약 | `docs/API_명세서.md` |
| JSON 필드 규칙 | `camelCase` |

이 문서는 `API_명세서.md`에 정의된 엔드포인트를 프론트엔드 개발자가 바로 사용할 수 있도록 API별로 풀어서 설명한다. 공통 응답 형식, 중요도 계산 계약, 표준 공시 분석 결과 등 전체 설계의 기준은 `API_명세서.md`를 따른다.

## 1. 공통 규칙

### 1.1 Base URL

```text
공개 API: /api/v1
내부 API: /internal/v1
상태 확인: /actuator
```

### 1.2 사용자 인증

MVP에서는 JWT 대신 데모 세션 토큰을 사용한다.

```http
X-FolioLens-Session: {sessionToken}
```

로그인 방식을 도입하면 다음 형식으로 교체한다.

```http
Authorization: Bearer {accessToken}
```

### 1.3 공통 성공 응답

```json
{
  "success": true,
  "code": null,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {}
}
```

### 1.4 공통 오류 응답

```json
{
  "success": false,
  "code": "COMMON_404_1",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```

현재 코드에 구현된 오류는 `COMMON_400_1`, `COMMON_401_1`, `COMMON_403_1`, `COMMON_404_1`, `COMMON_500_1`이다. 아래 API별 오류 표에서 도메인 오류 뒤에 **(예정)**이 붙은 코드는 아직 `ErrorCode`에 추가되지 않은 계약 초안이다.

`@Valid` 검증 실패는 현재 다음처럼 `code`가 `null`이다.

```json
{
  "success": false,
  "code": null,
  "message": "포트폴리오 이름은 필수입니다.",
  "data": null
}
```

### 1.5 공통 페이지 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `items` | `List<Object>` | 현재 페이지의 데이터 |
| `page.number` | `Integer` | 현재 페이지 번호, 0부터 시작 |
| `page.size` | `Integer` | 요청한 페이지 크기 |
| `page.totalElements` | `Long` | 전체 데이터 수 |
| `page.totalPages` | `Integer` | 전체 페이지 수 |
| `page.hasNext` | `Boolean` | 다음 페이지 존재 여부 |

---

## 2. 데모 세션

### 2.1 데모 세션 생성

- **URL: `POST /api/v1/demo-sessions`**
- **인증:** 필요 없음
- **Description:**
  - FolioLens의 사용자 데이터에 접근할 때 사용할 임시 데모 세션을 생성한다.
  - 응답으로 받은 `sessionToken`을 이후 사용자 API의 `X-FolioLens-Session` 헤더에 전달한다.
  - 토큰은 불투명한 무작위 문자열이며 사용자 ID처럼 해석하지 않는다.
- **Path Variables:** 없음
- **Query Parameters:** 없음
- **Request Body:** 없음
- **요청 주소 예시:** `POST /api/v1/demo-sessions`
- **Response status:** `201 Created`

**Request Body**

```text
없음
```

**Response 201**

```json
{
  "success": true,
  "code": null,
  "message": "데모 세션이 생성되었습니다.",
  "data": {
    "sessionToken": "fls_7xVn...opaque-token",
    "expiresAt": "2026-07-25T10:00:00+09:00"
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `sessionToken` | `String` | 이후 사용자 API 인증에 사용할 세션 토큰 |
| `expiresAt` | `OffsetDateTime` | 세션 만료 시각 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션 토큰 생성 실패 | 500 | `COMMON_500_1` |

---

## 3. 기업 검색

### 3.1 기업 검색

- **URL: `GET /api/v1/companies`**
- **인증:** 데모 세션 필요
- **Description:**
  - 포트폴리오에 추가할 상장 기업을 회사명 또는 종목코드로 검색한다.
  - 검색 결과가 여러 개인 것은 오류가 아니며 사용자가 한 기업을 선택한다.
  - 검색 우선순위는 종목코드 정확 일치, 회사명 정확 일치, 별칭 일치, 유사명 순이다.
- **Path Variables:** 없음
- **Request Body:** 없음
- **요청 주소 예시:** `GET /api/v1/companies?query=삼성&listedOnly=true&page=0&size=20`
- **Response status:** `200 OK`

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `query` | `String` | 예 | 없음 | 회사명 또는 종목코드, 1~100자 |
| `listedOnly` | `Boolean` | 아니오 | `true` | 상장 기업만 조회할지 여부 |
| `page` | `Integer` | 아니오 | `0` | 페이지 번호 |
| `size` | `Integer` | 아니오 | `20` | 페이지 크기, 1~100 |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "기업 검색에 성공했습니다.",
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

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `items[].corpCode` | `String` | DART 기업 고유번호, 8자리 |
| `items[].stockCode` | `String` | 종목코드, 6자리 |
| `items[].corpName` | `String` | 정식 회사명 |
| `items[].market` | `String` | 상장 시장 |
| `items[].matchType` | `String` | 검색어 일치 방식 |
| `items[].listed` | `Boolean` | 상장 여부 |
| `page` | `Object` | 공통 페이지 정보 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 검색어가 없거나 길이 제한을 초과함 | 400 | 검증 오류로 `code: null` |
| 페이지 값이 허용 범위를 벗어남 | 400 | `COMMON_400_1` |
| 기업 기준정보 공급자 조회 실패 | 502 | `EXTERNAL_API_UNAVAILABLE` **(예정)** |

---

## 4. 포트폴리오

### 4.1 포트폴리오 생성

- **URL: `POST /api/v1/portfolios`**
- **인증:** 데모 세션 필요
- **Description:**
  - 현재 세션 소유의 포트폴리오를 생성한다.
  - MVP에서는 세션당 활성 포트폴리오 한 개를 허용한다.
  - 생성 직후에는 보유 종목이 없으므로 `totalWeight`는 0, `remainingWeight`는 100이다.
- **Path Variables:** 없음
- **Query Parameters:** 없음
- **요청 주소 예시:** `POST /api/v1/portfolios`
- **Response status:** `201 Created`

**Request Body**

```json
{
  "name": "내 포트폴리오",
  "description": "장기 투자 종목"
}
```

**Request Field**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | `String` | 예 | 포트폴리오 이름, 공백 제거 후 1~50자 |
| `description` | `String` | 아니오 | 포트폴리오 설명, 최대 500자 |

**Response 201**

```json
{
  "success": true,
  "code": null,
  "message": "포트폴리오가 생성되었습니다.",
  "data": {
    "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
    "name": "내 포트폴리오",
    "description": "장기 투자 종목",
    "totalWeight": 0.0,
    "remainingWeight": 100.0,
    "createdAt": "2026-07-24T10:00:00+09:00",
    "updatedAt": "2026-07-24T10:00:00+09:00"
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `portfolioId` | `UUID` | 생성된 포트폴리오 ID |
| `name` | `String` | 포트폴리오 이름 |
| `description` | `String` | 포트폴리오 설명 |
| `totalWeight` | `Decimal` | 등록된 보유 종목 비중 합계, 퍼센트 단위 |
| `remainingWeight` | `Decimal` | 추가 등록 가능한 나머지 비중 |
| `createdAt` | `OffsetDateTime` | 생성 시각 |
| `updatedAt` | `OffsetDateTime` | 마지막 수정 시각 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 이름이 비어 있거나 길이 제한을 초과함 | 400 | 검증 오류로 `code: null` |
| 활성 포트폴리오가 이미 존재함 | 409 | 도메인 오류 코드 추가 필요 |

### 4.2 포트폴리오 및 보유 종목 조회

- **URL: `GET /api/v1/portfolios/{portfolioId}`**
- **인증:** 데모 세션 필요
- **Description:**
  - 포트폴리오 기본 정보와 보유 종목 목록을 함께 조회한다.
  - 보유 종목별 활성 투자 가정 수, 중요 공시 수, 최근 중요 공시를 포함한다.
  - 현재 세션이 소유한 포트폴리오만 조회할 수 있다.
- **Request Body:** 없음
- **요청 주소 예시:** `GET /api/v1/portfolios/69b13ed7-5a34-4f5b-9858-0a71d7035e92`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 조회할 포트폴리오 ID |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "포트폴리오 조회에 성공했습니다.",
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

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `portfolioId` | `UUID` | 포트폴리오 ID |
| `name` | `String` | 포트폴리오 이름 |
| `description` | `String` | 포트폴리오 설명 |
| `totalWeight` | `Decimal` | 보유 비중 합계 |
| `remainingWeight` | `Decimal` | 남은 비중 |
| `holdings` | `List<Object>` | 보유 종목 목록 |
| `holdings[].holdingId` | `UUID` | 보유 종목 ID |
| `holdings[].company` | `Object` | 기업 식별 정보 |
| `holdings[].weight` | `Decimal` | 포트폴리오 내 보유 비중 |
| `holdings[].activeThesisCount` | `Integer` | 활성 투자 가정 수 |
| `holdings[].urgentDisclosureCount` | `Integer` | 긴급 공시 수 |
| `holdings[].cautionDisclosureCount` | `Integer` | 주의 공시 수 |
| `holdings[].latestImportantDisclosure` | `Object, null` | 최근 중요 공시 요약 |
| `holdings[].lastAnalyzedAt` | `OffsetDateTime, null` | 최근 개인화 분석 시각 |
| `createdAt` | `OffsetDateTime` | 생성 시각 |
| `updatedAt` | `OffsetDateTime` | 수정 시각 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 포트폴리오를 조회함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |

### 4.3 포트폴리오 수정

- **URL: `PATCH /api/v1/portfolios/{portfolioId}`**
- **인증:** 데모 세션 필요
- **Description:**
  - 포트폴리오 이름 또는 설명을 수정한다.
  - 요청 본문에는 변경할 필드만 포함할 수 있다.
  - 현재 세션이 소유한 포트폴리오만 수정할 수 있다.
- **요청 주소 예시:** `PATCH /api/v1/portfolios/69b13ed7-5a34-4f5b-9858-0a71d7035e92`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 수정할 포트폴리오 ID |

**Request Body**

```json
{
  "name": "장기 성장 포트폴리오",
  "description": "반도체와 배터리 중심"
}
```

**Request Field**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | `String` | 아니오 | 변경할 이름, 1~50자 |
| `description` | `String, null` | 아니오 | 변경할 설명, 최대 500자. `null`이면 설명 제거 |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "포트폴리오가 수정되었습니다.",
  "data": {
    "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
    "name": "장기 성장 포트폴리오",
    "description": "반도체와 배터리 중심",
    "totalWeight": 58.0,
    "remainingWeight": 42.0,
    "updatedAt": "2026-07-24T10:30:00+09:00"
  }
}
```

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 포트폴리오를 수정함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| 필드 형식이 올바르지 않음 | 400 | 검증 오류로 `code: null` |

---

## 5. 보유 종목

### 5.1 보유 종목 추가

- **URL: `POST /api/v1/portfolios/{portfolioId}/holdings`**
- **인증:** 데모 세션 필요
- **Description:**
  - 검색을 통해 선택한 기업을 포트폴리오에 추가한다.
  - 기업 식별정보는 서버의 기업 기준정보와 일치해야 한다.
  - 추가 후 전체 보유 비중이 100%를 초과하면 저장하지 않는다.
  - 같은 기업은 한 포트폴리오에 중복으로 추가할 수 없다.
- **요청 주소 예시:** `POST /api/v1/portfolios/69b13ed7-5a34-4f5b-9858-0a71d7035e92/holdings`
- **Response status:** `201 Created`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 종목을 추가할 포트폴리오 ID |

**Request Body**

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

**Request Field**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `corpCode` | `String` | 예 | DART 기업 고유번호 |
| `stockCode` | `String` | 예 | 종목코드 |
| `weight` | `Decimal` | 예 | 보유 비중, 0 초과 100 이하 |
| `quantity` | `Decimal String` | 아니오 | 보유 수량. 정밀도 보존을 위해 문자열 사용 |
| `averagePrice.amount` | `Decimal String` | 아니오 | 평균 매입가 |
| `averagePrice.currency` | `String` | `averagePrice` 사용 시 예 | 통화 코드 |
| `investmentStartedOn` | `LocalDate` | 아니오 | 투자 시작일 |
| `memo` | `String` | 아니오 | 사용자 메모 |

**Response 201**

```json
{
  "success": true,
  "code": null,
  "message": "보유 종목이 추가되었습니다.",
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
    "createdAt": "2026-07-24T10:10:00+09:00",
    "updatedAt": "2026-07-24T10:10:00+09:00"
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `holdingId` | `UUID` | 생성된 보유 종목 ID |
| `portfolioId` | `UUID` | 소속 포트폴리오 ID |
| `company` | `Object` | 기업 식별 정보 |
| `weight` | `Decimal` | 보유 비중 |
| `quantity` | `Decimal String, null` | 보유 수량 |
| `averagePrice` | `Object, null` | 평균 매입가와 통화 |
| `investmentStartedOn` | `LocalDate, null` | 투자 시작일 |
| `memo` | `String, null` | 사용자 메모 |
| `createdAt` | `OffsetDateTime` | 생성 시각 |
| `updatedAt` | `OffsetDateTime` | 수정 시각 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 포트폴리오에 추가함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| 기업 기준정보를 찾을 수 없음 | 404 | `COMPANY_NOT_FOUND` **(예정)** |
| 같은 기업이 이미 등록됨 | 409 | `DUPLICATE_HOLDING` **(예정)** |
| 추가 후 전체 비중이 100%를 초과함 | 422 | `PORTFOLIO_WEIGHT_EXCEEDED` **(예정)** |
| 요청 필드 형식이 올바르지 않음 | 400 | 검증 오류로 `code: null` |

### 5.2 보유 종목 상세 조회

- **URL: `GET /api/v1/portfolios/{portfolioId}/holdings/{holdingId}`**
- **인증:** 데모 세션 필요
- **Description:**
  - 보유 정보와 활성 투자 가정, 최근 중요 공시를 함께 조회한다.
  - `holdingId`가 요청한 `portfolioId`에 속하는지 확인한다.
  - 현재 세션이 소유한 데이터만 조회할 수 있다.
- **Request Body:** 없음
- **요청 주소 예시:** `GET /api/v1/portfolios/{portfolioId}/holdings/{holdingId}`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 포트폴리오 ID |
| `holdingId` | `UUID` | 예 | 조회할 보유 종목 ID |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "보유 종목 상세 조회에 성공했습니다.",
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
    "createdAt": "2026-07-24T10:10:00+09:00",
    "updatedAt": "2026-07-24T10:10:00+09:00"
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `holdingId` | `UUID` | 보유 종목 ID |
| `portfolioId` | `UUID` | 소속 포트폴리오 ID |
| `company` | `Object` | 기업 식별 정보 |
| `weight` | `Decimal` | 포트폴리오 내 비중 |
| `quantity` | `Decimal String, null` | 보유 수량 |
| `averagePrice` | `Object, null` | 평균 매입가 |
| `investmentStartedOn` | `LocalDate, null` | 투자 시작일 |
| `memo` | `String, null` | 사용자 메모 |
| `theses` | `List<Object>` | 활성 투자 가정 목록 |
| `recentImportantDisclosures` | `List<Object>` | 최근 중요 공시 요약 |
| `createdAt` | `OffsetDateTime` | 생성 시각 |
| `updatedAt` | `OffsetDateTime` | 수정 시각 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 데이터를 조회함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| 보유 종목이 존재하지 않거나 해당 포트폴리오 소속이 아님 | 404 | `HOLDING_NOT_FOUND` **(예정)** |

### 5.3 보유 정보 수정

- **URL: `PATCH /api/v1/portfolios/{portfolioId}/holdings/{holdingId}`**
- **인증:** 데모 세션 필요
- **Description:**
  - 보유 비중, 수량, 평균 매입가, 투자 시작일 또는 메모를 수정한다.
  - 요청 본문에는 변경할 필드만 포함한다.
  - 비중이 변경되면 해당 종목의 기존 개인화 분석 캐시를 무효화한다.
- **요청 주소 예시:** `PATCH /api/v1/portfolios/{portfolioId}/holdings/{holdingId}`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 포트폴리오 ID |
| `holdingId` | `UUID` | 예 | 수정할 보유 종목 ID |

**Request Body**

```json
{
  "weight": 22.0,
  "quantity": "12",
  "memo": "비중 조정"
}
```

**Request Field**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `weight` | `Decimal` | 아니오 | 변경할 비중, 0 초과 100 이하 |
| `quantity` | `Decimal String, null` | 아니오 | 변경할 수량 |
| `averagePrice` | `Object, null` | 아니오 | 변경할 평균 매입가 |
| `investmentStartedOn` | `LocalDate, null` | 아니오 | 변경할 투자 시작일 |
| `memo` | `String, null` | 아니오 | 변경할 메모 |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "보유 정보가 수정되었습니다.",
  "data": {
    "holdingId": "8aad34af-c7c9-4fd8-b191-39ae6609426a",
    "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
    "weight": 22.0,
    "quantity": "12",
    "averagePrice": {
      "amount": "72000",
      "currency": "KRW"
    },
    "investmentStartedOn": "2025-03-12",
    "memo": "비중 조정",
    "updatedAt": "2026-07-24T11:00:00+09:00"
  }
}
```

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 데이터를 수정함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| 보유 종목이 존재하지 않음 | 404 | `HOLDING_NOT_FOUND` **(예정)** |
| 변경 후 전체 비중이 100%를 초과함 | 422 | `PORTFOLIO_WEIGHT_EXCEEDED` **(예정)** |
| 요청 필드 형식이 올바르지 않음 | 400 | 검증 오류로 `code: null` |

### 5.4 보유 종목 삭제

- **URL: `DELETE /api/v1/portfolios/{portfolioId}/holdings/{holdingId}`**
- **인증:** 데모 세션 필요
- **Description:**
  - 포트폴리오에서 보유 종목을 제거한다.
  - 세션에 귀속된 투자 가정과 개인화 분석은 보존 정책에 따라 삭제하거나 비활성화한다.
  - 공시 원문과 여러 사용자에게 공통으로 사용되는 공시 사실 데이터는 삭제하지 않는다.
- **Request Body:** 없음
- **요청 주소 예시:** `DELETE /api/v1/portfolios/{portfolioId}/holdings/{holdingId}`
- **Response status:** `204 No Content`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 포트폴리오 ID |
| `holdingId` | `UUID` | 예 | 삭제할 보유 종목 ID |

**Response 204**

```text
응답 본문 없음
```

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 데이터를 삭제함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| 보유 종목이 존재하지 않음 | 404 | `HOLDING_NOT_FOUND` **(예정)** |

---

## 6. 투자 가정

### 6.1 투자 가정 추가

- **URL: `POST /api/v1/holdings/{holdingId}/theses`**
- **인증:** 데모 세션 필요
- **Description:**
  - 사용자가 해당 종목을 보유하는 이유를 원문 그대로 저장한다.
  - HyperCLOVA X가 원문에서 주제, 기대 방향, 확인 지표를 구조화한다.
  - 모델 구조화가 실패해도 사용자의 원문 저장은 성공할 수 있다.
  - 현재 세션 소유의 보유 종목에만 추가할 수 있다.
- **요청 주소 예시:** `POST /api/v1/holdings/{holdingId}/theses`
- **Response status:** `201 Created`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `holdingId` | `UUID` | 예 | 투자 가정을 추가할 보유 종목 ID |

**Request Body**

```json
{
  "originalText": "HBM 관련 매출이 계속 성장할 것으로 기대한다."
}
```

**Request Field**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `originalText` | `String` | 예 | 사용자가 작성한 투자 이유, 공백 제거 후 1~1000자 |

**Response 201**

```json
{
  "success": true,
  "code": null,
  "message": "투자 가정이 추가되었습니다.",
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
    "structureFailureReason": null,
    "createdAt": "2026-07-24T10:20:00+09:00",
    "updatedAt": "2026-07-24T10:20:00+09:00"
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `thesisId` | `UUID` | 투자 가정 ID |
| `holdingId` | `UUID` | 보유 종목 ID |
| `originalText` | `String` | 사용자가 입력한 원문 |
| `topic` | `String, null` | 구조화된 핵심 주제 |
| `expectedDirection` | `String, null` | 사용자가 기대하는 변화 방향 |
| `metrics` | `List<String>` | 이후 확인할 주요 지표 |
| `active` | `Boolean` | 활성 여부 |
| `structureStatus` | `String` | `COMPLETED` 또는 `FAILED` |
| `structureFailureReason` | `String, null` | 구조화 실패 사유 |
| `createdAt` | `OffsetDateTime` | 생성 시각 |
| `updatedAt` | `OffsetDateTime` | 수정 시각 |

구조화 실패 시에도 `201 Created`로 원문 저장 결과를 반환할 수 있다.

```json
{
  "structureStatus": "FAILED",
  "topic": null,
  "expectedDirection": null,
  "metrics": [],
  "structureFailureReason": "MODEL_TIMEOUT"
}
```

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션 소유의 보유 종목에 추가함 | 403 | `COMMON_403_1` |
| 보유 종목이 존재하지 않음 | 404 | `HOLDING_NOT_FOUND` **(예정)** |
| 원문이 비어 있거나 1000자를 초과함 | 400 | 검증 오류로 `code: null` |

### 6.2 투자 가정 수정

- **URL: `PATCH /api/v1/holdings/{holdingId}/theses/{thesisId}`**
- **인증:** 데모 세션 필요
- **Description:**
  - 투자 가정 원문 또는 구조화 결과를 수정한다.
  - 사용자가 직접 수정한 구조화 값은 이후 분석에서 모델 생성값보다 우선한다.
  - 투자 가정이 변경되면 관련 개인화 분석 캐시를 무효화한다.
- **요청 주소 예시:** `PATCH /api/v1/holdings/{holdingId}/theses/{thesisId}`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `holdingId` | `UUID` | 예 | 보유 종목 ID |
| `thesisId` | `UUID` | 예 | 수정할 투자 가정 ID |

**Request Body**

```json
{
  "originalText": "HBM 매출 성장과 수익성 개선을 확인한다.",
  "topic": "HBM 수익성",
  "expectedDirection": "GROWTH",
  "metrics": [
    "HBM 매출",
    "영업이익률"
  ],
  "active": true
}
```

**Request Field**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `originalText` | `String` | 아니오 | 변경할 사용자 원문 |
| `topic` | `String, null` | 아니오 | 변경할 핵심 주제 |
| `expectedDirection` | `String, null` | 아니오 | 변경할 기대 방향 |
| `metrics` | `List<String>` | 아니오 | 변경할 확인 지표 |
| `active` | `Boolean` | 아니오 | 활성 여부 |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "투자 가정이 수정되었습니다.",
  "data": {
    "thesisId": "b00fb35b-9fde-48e8-b82b-29c3ec37209f",
    "holdingId": "8aad34af-c7c9-4fd8-b191-39ae6609426a",
    "originalText": "HBM 매출 성장과 수익성 개선을 확인한다.",
    "topic": "HBM 수익성",
    "expectedDirection": "GROWTH",
    "metrics": [
      "HBM 매출",
      "영업이익률"
    ],
    "active": true,
    "structureStatus": "COMPLETED",
    "updatedAt": "2026-07-24T11:20:00+09:00"
  }
}
```

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션 소유의 투자 가정을 수정함 | 403 | `COMMON_403_1` |
| 보유 종목이 존재하지 않음 | 404 | `HOLDING_NOT_FOUND` **(예정)** |
| 투자 가정이 존재하지 않거나 해당 종목 소속이 아님 | 404 | `THESIS_NOT_FOUND` **(예정)** |
| 요청 필드 형식이 올바르지 않음 | 400 | 검증 오류로 `code: null` |

### 6.3 투자 가정 삭제

- **URL: `DELETE /api/v1/holdings/{holdingId}/theses/{thesisId}`**
- **인증:** 데모 세션 필요
- **Description:**
  - 투자 가정을 사용자 조회 결과에서 제거한다.
  - 내부에서는 이력 보존을 위해 소프트 삭제 또는 비활성화할 수 있다.
  - 관련 개인화 분석 캐시를 무효화한다.
- **Request Body:** 없음
- **요청 주소 예시:** `DELETE /api/v1/holdings/{holdingId}/theses/{thesisId}`
- **Response status:** `204 No Content`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `holdingId` | `UUID` | 예 | 보유 종목 ID |
| `thesisId` | `UUID` | 예 | 삭제할 투자 가정 ID |

**Response 204**

```text
응답 본문 없음
```

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션 소유의 투자 가정을 삭제함 | 403 | `COMMON_403_1` |
| 보유 종목이 존재하지 않음 | 404 | `HOLDING_NOT_FOUND` **(예정)** |
| 투자 가정이 존재하지 않음 | 404 | `THESIS_NOT_FOUND` **(예정)** |

---

## 7. 포트폴리오 대시보드

### 7.1 포트폴리오 대시보드 조회

- **URL: `GET /api/v1/portfolios/{portfolioId}/dashboard`**
- **인증:** 데모 세션 필요
- **Description:**
  - 서비스 첫 화면에 필요한 포트폴리오 요약을 한 번에 조회한다.
  - 이미 저장된 공시 분석 결과를 중요도순으로 모아 반환하며 새로운 분석 작업을 시작하지 않는다.
  - 중요 공시는 중요도 점수 내림차순, 같은 점수에서는 접수 시각 내림차순으로 정렬한다.
  - 투자 가정 미등록이나 분석 실패처럼 사용자가 확인해야 할 항목도 함께 반환한다.
- **Request Body:** 없음
- **요청 주소 예시:** `GET /api/v1/portfolios/{portfolioId}/dashboard?limit=10`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 대시보드를 조회할 포트폴리오 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `limit` | `Integer` | 아니오 | `10` | 반환할 중요 공시 최대 개수 |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "대시보드 조회에 성공했습니다.",
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

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `portfolio` | `Object` | 포트폴리오 이름, 종목 수, 비중 요약 |
| `summary.urgentDisclosureCount` | `Integer` | 긴급 등급 공시 수 |
| `summary.cautionDisclosureCount` | `Integer` | 주의 등급 공시 수 |
| `summary.referenceDisclosureCount` | `Integer` | 참고 등급 공시 수 |
| `summary.lastDataRefreshedAt` | `OffsetDateTime, null` | 공시 데이터 최종 갱신 시각 |
| `importantDisclosures` | `List<Object>` | 중요도순 공시 카드 목록 |
| `importantDisclosures[].importance` | `Object` | 중요도 점수, 등급, 산정 이유 |
| `importantDisclosures[].headline` | `String, null` | 한 줄 핵심 변화 |
| `importantDisclosures[].affectedTheses` | `List<Object>` | 영향을 받은 투자 가정 |
| `importantDisclosures[].analysisStatus` | `String` | 분석 진행 상태 |
| `thesisImpactCounts` | `Object` | 투자 가정 영향 상태별 개수 |
| `attentionItems` | `List<Object>` | 사용자가 확인하거나 보완할 항목 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 포트폴리오를 조회함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| `limit`가 허용 범위를 벗어남 | 400 | `COMMON_400_1` |

---

## 8. 공시 목록

### 8.1 보유 기업 공시 목록 조회

- **URL: `GET /api/v1/portfolios/{portfolioId}/disclosures`**
- **인증:** 데모 세션 필요
- **Description:**
  - 현재 포트폴리오에 등록된 기업의 공시만 조회한다.
  - 중요도, 종목, 공시 유형, 정정 여부, 접수 기간, 분석 상태로 필터링할 수 있다.
  - 기본 정렬은 중요도 점수 내림차순이며 페이지 단위로 반환한다.
- **Request Body:** 없음
- **요청 주소 예시:** `GET /api/v1/portfolios/{portfolioId}/disclosures?importanceLevel=URGENT&page=0&size=20`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 공시를 조회할 포트폴리오 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `importanceLevel` | `List<String>` | 아니오 | `URGENT`, `CAUTION`, `REFERENCE`; 반복 전달 가능 |
| `stockCode` | `List<String>` | 아니오 | 보유 종목코드; 반복 전달 가능 |
| `disclosureType` | `List<String>` | 아니오 | 공시 유형; 반복 전달 가능 |
| `correction` | `Boolean` | 아니오 | 정정공시 여부 |
| `submittedFrom` | `LocalDate` | 아니오 | 접수 시작일 |
| `submittedTo` | `LocalDate` | 아니오 | 접수 종료일 |
| `analysisStatus` | `String` | 아니오 | 분석 상태 |
| `page` | `Integer` | 아니오 | 페이지 번호, 기본값 0 |
| `size` | `Integer` | 아니오 | 페이지 크기, 기본값 20 |
| `sort` | `String` | 아니오 | 기본값 `importanceScore,desc` |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "공시 목록 조회에 성공했습니다.",
  "data": {
    "items": [
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

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `items[].receiptNo` | `String` | 공시 접수번호, 14자리 |
| `items[].company` | `Object` | 공시 기업 정보 |
| `items[].holdingWeight` | `Decimal` | 해당 기업의 포트폴리오 비중 |
| `items[].reportName` | `String` | 공시 제목 |
| `items[].disclosureType` | `String` | 내부 표준 공시 유형 |
| `items[].submittedAt` | `OffsetDateTime` | 공시 접수 시각 |
| `items[].correction` | `Boolean` | 정정공시 여부 |
| `items[].importance` | `Object` | 중요도 점수와 등급 |
| `items[].headline` | `String, null` | 분석된 한 줄 핵심 변화 |
| `items[].thesisRelated` | `Boolean` | 활성 투자 가정 관련 여부 |
| `items[].analysisStatus` | `String` | 분석 상태 |
| `items[].sourceUrl` | `String, null` | 공시 원문 URL |
| `items[].dataAsOf` | `OffsetDateTime` | 데이터 기준 시각 |
| `page` | `Object` | 공통 페이지 정보 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 포트폴리오를 조회함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| 필터, 날짜 또는 정렬 값이 올바르지 않음 | 400 | `COMMON_400_1` |

---

## 9. 공시 분석

### 9.1 공시 분석 생성·재생성 요청

- **URL: `POST /api/v1/portfolios/{portfolioId}/disclosures/{receiptNo}/analysis`**
- **인증:** 데모 세션 필요
- **추가 헤더:** `Idempotency-Key` 필요
- **Description:**
  - 특정 공시에 대한 포트폴리오 개인화 분석을 생성한다.
  - 공시 사실 추출, 정정 전후 비교, 중요도 계산, 투자 가정 영향 설명을 비동기로 수행한다.
  - 유효한 캐시가 있으면 기존 분석을 재사용할 수 있다.
  - 같은 입력 버전과 멱등성 키로 반복 요청해도 분석 작업을 중복 생성하지 않는다.
- **요청 주소 예시:** `POST /api/v1/portfolios/{portfolioId}/disclosures/{receiptNo}/analysis`
- **Response status:** `202 Accepted`

**Headers**

| 헤더 | 필수 | 설명 |
|---|---|---|
| `X-FolioLens-Session` | 예 | 데모 세션 토큰 |
| `Idempotency-Key` | 예 | `analysis-{portfolioId}-{receiptNo}-{inputVersion}` 형식의 중복 방지 키 |

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 분석 기준 포트폴리오 ID |
| `receiptNo` | `String` | 예 | 분석할 공시 접수번호, 14자리 |

**Request Body**

```json
{
  "force": false
}
```

**Request Field**

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `force` | `Boolean` | 아니오 | `false` | 캐시를 무시하고 재분석할지 여부. 운영 정책상 허용된 경우에만 적용 |

**Response 202**

```json
{
  "success": true,
  "code": null,
  "message": "공시 분석을 요청했습니다.",
  "data": {
    "analysisId": "65f446ed-0afd-48f1-9c61-7432bb606179",
    "status": "PENDING",
    "receiptNo": "20260721000001",
    "statusUrl": "/api/v1/portfolios/69b13ed7-5a34-4f5b-9858-0a71d7035e92/disclosures/20260721000001/analysis",
    "requestedAt": "2026-07-24T10:30:00+09:00"
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `analysisId` | `UUID` | 생성 또는 재사용된 분석 ID |
| `status` | `String` | 최초 상태, 일반적으로 `PENDING` |
| `receiptNo` | `String` | 공시 접수번호 |
| `statusUrl` | `String` | 분석 상태와 결과를 조회할 URL |
| `requestedAt` | `OffsetDateTime` | 분석 요청 시각 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 포트폴리오로 요청함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| 공시가 존재하지 않거나 해당 포트폴리오의 보유 기업 공시가 아님 | 404 | `DISCLOSURE_NOT_FOUND` **(예정)** |
| 동일 입력의 분석이 이미 진행 중임 | 409 | `ANALYSIS_ALREADY_RUNNING` **(예정)** |
| 공시 원문이 없어 분석할 수 없음 | 422 | `DISCLOSURE_CONTENT_MISSING` **(예정)** |
| 지원하지 않는 공시 유형임 | 422 | `UNSUPPORTED_DISCLOSURE` **(예정)** |
| 분석에 필요한 최소 근거가 없음 | 422 | `INSUFFICIENT_EVIDENCE` **(예정)** |
| 외부 데이터 공급자 요청 제한 | 429 | `EXTERNAL_API_RATE_LIMITED` **(예정)** |
| 모델 응답 시간 초과 | 504 | `MODEL_TIMEOUT` **(예정)** |

### 9.2 공시 분석 결과 조회

- **URL: `GET /api/v1/portfolios/{portfolioId}/disclosures/{receiptNo}/analysis`**
- **인증:** 데모 세션 필요
- **Description:**
  - 분석의 현재 진행 상태 또는 완료된 표준 분석 결과를 조회한다.
  - 분석 중인 경우에도 오류가 아니라 `200 OK`와 `PROCESSING` 상태를 반환한다.
  - `COMPLETED` 또는 `PARTIAL`이면 확인된 사실, 백엔드 계산, 투자 가정 영향, 시나리오와 원문 근거를 반환한다.
- **Request Body:** 없음
- **요청 주소 예시:** `GET /api/v1/portfolios/{portfolioId}/disclosures/{receiptNo}/analysis`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 분석 기준 포트폴리오 ID |
| `receiptNo` | `String` | 예 | 공시 접수번호, 14자리 |

**Response 200 — 처리 중**

```json
{
  "success": true,
  "code": null,
  "message": "공시 분석 상태 조회에 성공했습니다.",
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

**Response 200 — 완료**

```json
{
  "success": true,
  "code": null,
  "message": "공시 분석 결과 조회에 성공했습니다.",
  "data": {
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
        "citationIds": [
          "citation-2"
        ]
      }
    ],
    "changes": [
      {
        "fieldName": "investmentAmount",
        "label": "투자금액",
        "before": {
          "normalizedValue": "200000000000",
          "citationIds": [
            "citation-1"
          ]
        },
        "after": {
          "normalizedValue": "240000000000",
          "citationIds": [
            "citation-2"
          ]
        },
        "calculation": {
          "absoluteChange": "40000000000",
          "changeRatePercent": 20.0,
          "formula": "(after - before) / before * 100"
        }
      }
    ],
    "thesisImpacts": [
      {
        "thesisId": "b00fb35b-9fde-48e8-b82b-29c3ec37209f",
        "originalText": "신규 배터리 사업 성장",
        "status": "WEAKENED_POSSIBLE",
        "summary": "투자 목적은 유지됐지만 완료 일정이 연기됐습니다.",
        "counterpoint": "투자 목적과 사업 방향은 변경되지 않았습니다.",
        "uncertainty": "실제 공사 진행률은 확인할 수 없습니다.",
        "citationIds": [
          "citation-2"
        ]
      }
    ],
    "scenarios": {
      "positive": [],
      "negative": []
    },
    "nextMetrics": [],
    "limitations": [],
    "citations": [],
    "dataAsOf": "2026-07-23T09:55:00+09:00",
    "generatedAt": "2026-07-24T10:31:15+09:00",
    "versions": {
      "model": "configured-hcx-model",
      "prompt": "disclosure-analysis-v1",
      "importanceRule": "importance-v1",
      "extractor": "facility-investment-v1",
      "schema": "analysis-result-v1"
    }
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `analysisId` | `UUID` | 분석 결과 ID |
| `portfolioId` | `UUID` | 분석 기준 포트폴리오 ID |
| `status` | `String` | `PENDING`, `PROCESSING`, `COMPLETED`, `PARTIAL`, `FAILED` |
| `progress` | `Object, null` | 처리 중인 단계와 완료 단계 수 |
| `company` | `Object` | 기업 정보와 보유 비중 |
| `disclosure` | `Object` | 공시 메타데이터와 원문 URL |
| `importance` | `Object` | 백엔드가 계산한 중요도 점수, 등급, 이유와 누락 요소 |
| `facts` | `List<Object>` | 공시 원문에서 확인된 구조화 사실 |
| `facts[].rawValue` | `String, null` | 공시 원문 표현 |
| `facts[].normalizedValue` | `String, null` | 백엔드가 정규화한 값 |
| `facts[].citationIds` | `List<String>` | 사실의 원문 근거 ID |
| `changes` | `List<Object>` | 원공시·정정공시 또는 이전 값의 변경 사항 |
| `changes[].calculation` | `Object, null` | 백엔드가 계산한 절대 변화량, 변화율, 날짜 차이 |
| `thesisImpacts` | `List<Object>` | 사용자 투자 가정별 영향 설명 |
| `scenarios.positive` | `List<Object>` | 조건부 긍정 시나리오 |
| `scenarios.negative` | `List<Object>` | 조건부 부정 시나리오 |
| `nextMetrics` | `List<Object>` | 이후 확인해야 할 지표 |
| `limitations` | `List<Object>` | 데이터 부족 또는 분석 한계 |
| `citations` | `List<Object>` | 공시 원문 근거 상세 |
| `dataAsOf` | `OffsetDateTime` | 사용 데이터 기준 시각 |
| `generatedAt` | `OffsetDateTime` | 분석 생성 시각 |
| `versions` | `Object` | 모델, 프롬프트, 규칙, 추출기, 스키마 버전 |

완전한 표준 분석 결과와 중요도 `breakdown` 구조는 `API_명세서.md`의 **14.3 표준 분석 결과**, **14.4 중요도 계산 계약**을 따른다.

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 포트폴리오 분석을 조회함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| 공시가 존재하지 않음 | 404 | `DISCLOSURE_NOT_FOUND` **(예정)** |
| 아직 분석 요청이 생성되지 않음 | 404 | `COMMON_404_1` 또는 분석 전용 코드 추가 필요 |

---

## 10. 원공시·정정공시 비교

### 10.1 원공시·정정공시 비교 조회

- **URL: `GET /api/v1/disclosures/{receiptNo}/comparison`**
- **인증:** 데모 세션 필요
- **Description:**
  - 정정공시와 연결된 원공시의 변경 사항을 조회한다.
  - `receiptNo`가 정정공시이면 원공시와 비교하고, 원공시이면 연결된 최신 정정공시와 비교한다.
  - 값의 차이와 변화율 등 계산은 백엔드에서 수행하며 원문 근거를 연결한다.
- **Request Body:** 없음
- **요청 주소 예시:** `GET /api/v1/disclosures/20260721000001/comparison?changedOnly=true`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `receiptNo` | `String` | 예 | 원공시 또는 정정공시 접수번호, 14자리 |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `changedOnly` | `Boolean` | 아니오 | `true` | 값이 변경된 필드만 반환할지 여부 |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "정정공시 비교 조회에 성공했습니다.",
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
    "changes": [
      {
        "fieldName": "investmentAmount",
        "label": "투자금액",
        "before": {
          "normalizedValue": "200000000000",
          "citationIds": [
            "citation-1"
          ]
        },
        "after": {
          "normalizedValue": "240000000000",
          "citationIds": [
            "citation-2"
          ]
        },
        "calculation": {
          "absoluteChange": "40000000000",
          "changeRatePercent": 20.0
        }
      }
    ],
    "dataAsOf": "2026-07-23T09:55:00+09:00"
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `originalDisclosure` | `Object` | 원공시 메타데이터 |
| `correctedDisclosure` | `Object` | 정정공시 메타데이터 |
| `changes` | `List<Object>` | 필드별 변경 전후 값과 백엔드 계산 결과 |
| `changes[].before` | `Object` | 변경 전 값과 근거 |
| `changes[].after` | `Object` | 변경 후 값과 근거 |
| `changes[].calculation` | `Object, null` | 절대 변화량, 변화율 또는 날짜 차이 |
| `dataAsOf` | `OffsetDateTime` | 비교 데이터 기준 시각 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 공시가 존재하지 않음 | 404 | `DISCLOSURE_NOT_FOUND` **(예정)** |
| 연결된 원공시 또는 정정공시를 찾을 수 없음 | 422 | `CORRECTION_ORIGINAL_NOT_FOUND` **(예정)** |
| 비교 가능한 구조화 필드가 없음 | 422 | `INSUFFICIENT_EVIDENCE` **(예정)** |

---

## 11. 공시 기반 질문

### 11.1 공시 기반 자연어 질문

- **URL: `POST /api/v1/questions`**
- **인증:** 데모 세션 필요
- **Description:**
  - 포트폴리오, 보유 종목 또는 특정 공시를 범위로 자연어 질문을 처리한다.
  - 사용자가 화면에서 선택한 종목과 공시는 모델이 질문에서 추정한 대상보다 우선한다.
  - 질문 대상이 불명확하면 임의로 선택하지 않고 `CLARIFICATION_REQUIRED`를 반환한다.
  - 매수·매도 추천 질문도 HTTP 오류로 처리하지 않고 확인 가능한 사실, 조건부 시나리오와 한계를 제공한다.
- **Path Variables:** 없음
- **Query Parameters:** 없음
- **요청 주소 예시:** `POST /api/v1/questions`
- **Response status:** `200 OK`

**Request Body**

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

**Request Field**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 질문에 사용할 포트폴리오 ID |
| `query` | `String` | 예 | 사용자 질문, 공백 제거 후 1~2000자 |
| `scope` | `String` | 예 | `PORTFOLIO`, `HOLDING`, `DISCLOSURE` |
| `selectedHoldingId` | `UUID` | 조건부 | `HOLDING` 범위에서 선택한 보유 종목 ID |
| `selectedReceiptNo` | `String` | 조건부 | `DISCLOSURE` 범위에서 필수인 공시 접수번호 |
| `conversationId` | `String, null` | 아니오 | 기존 대화를 이어갈 때 사용할 대화 ID |

**Response 200 — 답변 완료**

```json
{
  "success": true,
  "code": null,
  "message": "질문 답변 생성에 성공했습니다.",
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

**Response 200 — 명확화 필요**

```json
{
  "success": true,
  "code": null,
  "message": "질문 대상을 추가로 확인해야 합니다.",
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
      }
    ],
    "toolsUsed": [
      "portfolioLookup"
    ]
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `answerRequestId` | `String` | 답변 요청 식별자 |
| `status` | `String` | `SUCCESS`, `CLARIFICATION_REQUIRED`, `PARTIAL`, `UNSUPPORTED`, `INSUFFICIENT_EVIDENCE` |
| `answer` | `String, null` | 근거 기반 답변 |
| `portfolioRelevance` | `String, null` | 포트폴리오 비중과 투자 가정 관련성 |
| `uncertainties` | `List<String>` | 확인할 수 없는 내용과 불확실성 |
| `evidence` | `List<Object>` | 답변 핵심 주장과 원문 근거 |
| `toolsUsed` | `List<String>` | 답변 생성에 사용된 내부 조회 도구 |
| `suggestedQuestions` | `List<String>` | 후속 질문 제안 |
| `confidence` | `Decimal, null` | 근거 충족도 기반 신뢰도, 0~1 |
| `latencyMs` | `Long` | 답변 처리 시간, 밀리초 |
| `clarificationQuestion` | `String, null` | 대상을 명확히 하기 위한 질문 |
| `candidates` | `List<Object>` | 사용자가 선택할 수 있는 후보 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션의 포트폴리오를 질문 범위로 사용함 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| 선택한 보유 종목이 존재하지 않음 | 404 | `HOLDING_NOT_FOUND` **(예정)** |
| 선택한 공시가 존재하지 않음 | 404 | `DISCLOSURE_NOT_FOUND` **(예정)** |
| 질문이 비어 있거나 2000자를 초과함 | 400 | 검증 오류로 `code: null` |
| 모델 응답 시간 초과로 부분 답변도 만들 수 없음 | 504 | `MODEL_TIMEOUT` **(예정)** |

### 11.2 답변 근거 상세 조회

- **URL: `GET /api/v1/answers/{answerRequestId}/evidence`**
- **인증:** 데모 세션 필요
- **Description:**
  - 생성된 답변의 주장별 원문 근거와 백엔드 계산 과정을 조회한다.
  - `answerRequestId`는 HTTP 추적용 요청 ID가 아니라 질문 답변을 식별하는 값이다.
  - 현재 세션이 생성한 답변의 근거만 조회할 수 있다.
- **Request Body:** 없음
- **요청 주소 예시:** `GET /api/v1/answers/answer-01K0EXAMPLE/evidence`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `answerRequestId` | `String` | 예 | 질문 API가 반환한 답변 요청 ID |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "답변 근거 조회에 성공했습니다.",
  "data": {
    "answerRequestId": "answer-01K0EXAMPLE",
    "claims": [
      {
        "claimId": "claim-1",
        "text": "투자금액이 20% 증가했습니다.",
        "citationIds": [
          "citation-1",
          "citation-2"
        ],
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
    "citations": [
      {
        "citationId": "citation-2",
        "receiptNo": "20260721000001",
        "sourceUrl": "https://dart.fss.or.kr/...",
        "section": "2. 투자내역",
        "fieldLabel": "투자금액",
        "evidenceText": "투자금액 240,000,000,000원"
      }
    ],
    "dataAsOf": "2026-07-23T09:55:00+09:00"
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `answerRequestId` | `String` | 답변 요청 ID |
| `claims` | `List<Object>` | 답변에 포함된 검증 대상 주장 |
| `claims[].claimId` | `String` | 주장 ID |
| `claims[].text` | `String` | 사용자에게 제시된 주장 |
| `claims[].citationIds` | `List<String>` | 주장을 뒷받침하는 근거 ID |
| `claims[].calculation` | `Object, null` | 백엔드 계산식, 입력값과 결과 |
| `citations` | `List<Object>` | 공시 원문 근거 상세 |
| `dataAsOf` | `OffsetDateTime` | 답변 데이터 기준 시각 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 세션이 없거나 만료됨 | 401 | `COMMON_401_1` |
| 다른 세션이 생성한 답변 근거를 조회함 | 403 | `COMMON_403_1` |
| 답변 요청이 존재하지 않음 | 404 | `COMMON_404_1` 또는 답변 전용 코드 추가 필요 |

---

## 12. 데이터·분석 기준

### 12.1 분석 정책 조회

- **URL: `GET /api/v1/meta/analysis-policy`**
- **인증:** 필요 없음
- **Description:**
  - 서비스가 사용하는 데이터 출처, 중요도 기준, 투자 가정 상태와 역할 분담을 조회한다.
  - 사용자 데이터가 아니라 서비스 정책을 설명하는 공개 메타데이터다.
  - 설정 화면에서 “데이터·분석 기준”을 설명할 때 사용한다.
- **Path Variables:** 없음
- **Query Parameters:** 없음
- **Request Body:** 없음
- **요청 주소 예시:** `GET /api/v1/meta/analysis-policy`
- **Response status:** `200 OK`

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "분석 정책 조회에 성공했습니다.",
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
    "updatedAt": "2026-07-24T00:00:00+09:00"
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `serviceNotice` | `String` | 투자 권유 서비스가 아니라는 안내 |
| `dataSources` | `List<Object>` | 데이터 출처, 우선순위, 활성 여부 |
| `importance` | `Object` | 중요도 규칙 버전, 등급 구간, 계산 요소 |
| `thesisStatuses` | `List<String>` | 투자 가정 영향 상태 목록 |
| `modelResponsibilities` | `List<String>` | 언어모델 담당 범위 |
| `backendResponsibilities` | `List<String>` | 백엔드 담당 범위 |
| `knownLimitations` | `List<String>` | 알려진 분석 한계 |
| `updatedAt` | `OffsetDateTime` | 정책 갱신 시각 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 정책 설정을 읽을 수 없음 | 500 | `COMMON_500_1` |

---

## 13. 내부 공시 동기화

이 API는 브라우저에서 직접 호출하지 않는다. 배치 작업, 관리자 도구 또는 내부 서비스에서만 호출하며 서비스 계정이나 내부 네트워크 인증을 적용한다.

### 13.1 공시 동기화 작업 생성

- **URL: `POST /internal/v1/disclosure-sync-jobs`**
- **인증:** 내부 서비스 인증 필요
- **추가 헤더:** `Idempotency-Key` 필요
- **Description:**
  - 지정한 포트폴리오의 보유 기업 공시를 공급자로부터 수집하는 비동기 작업을 생성한다.
  - 동일 접수번호와 동일 원문 해시는 중복 저장하지 않는다.
  - 같은 멱등성 키의 반복 요청은 새 작업을 중복 생성하지 않는다.
- **요청 주소 예시:** `POST /internal/v1/disclosure-sync-jobs`
- **Response status:** `202 Accepted`

**Headers**

| 헤더 | 필수 | 설명 |
|---|---|---|
| `Authorization` 또는 내부 인증 헤더 | 예 | 서비스 계정 인증정보 |
| `Idempotency-Key` | 예 | `disclosure-sync-{portfolioId}-{from}-{to}` 형식 |

**Request Body**

```json
{
  "portfolioId": "69b13ed7-5a34-4f5b-9858-0a71d7035e92",
  "from": "2026-07-01",
  "to": "2026-07-23",
  "provider": "COMPETITION_DATA"
}
```

**Request Field**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `portfolioId` | `UUID` | 예 | 수집 대상 포트폴리오 ID |
| `from` | `LocalDate` | 예 | 수집 시작일 |
| `to` | `LocalDate` | 예 | 수집 종료일 |
| `provider` | `String` | 예 | `COMPETITION_DATA` 또는 허용된 외부 공급자 |

**Response 202**

```json
{
  "success": true,
  "code": null,
  "message": "공시 동기화 작업을 생성했습니다.",
  "data": {
    "jobId": "fd756c8b-c94f-4a5b-92bb-e9a59c943535",
    "status": "PENDING",
    "createdAt": "2026-07-24T11:00:00+09:00"
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `jobId` | `UUID` | 동기화 작업 ID |
| `status` | `String` | 작업 최초 상태 |
| `createdAt` | `OffsetDateTime` | 작업 생성 시각 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 내부 인증정보가 없거나 유효하지 않음 | 401 | `COMMON_401_1` |
| 동기화 실행 권한이 없음 | 403 | `COMMON_403_1` |
| 포트폴리오가 존재하지 않음 | 404 | `PORTFOLIO_NOT_FOUND` **(예정)** |
| 시작일이 종료일보다 늦거나 범위가 올바르지 않음 | 400 | 검증 오류로 `code: null` |
| 허용되지 않은 공급자임 | 400 | `COMMON_400_1` |
| 외부 공급자의 요청 제한 | 429 | `EXTERNAL_API_RATE_LIMITED` **(예정)** |
| 외부 공급자를 사용할 수 없음 | 502 | `EXTERNAL_API_UNAVAILABLE` **(예정)** |

### 13.2 공시 동기화 작업 상태 조회

- **URL: `GET /internal/v1/disclosure-sync-jobs/{jobId}`**
- **인증:** 내부 서비스 인증 필요
- **Description:**
  - 공시 동기화 작업의 진행 상태와 수집 결과를 조회한다.
  - 전체 실패뿐 아니라 공시별 부분 실패 개수와 원인을 함께 제공한다.
- **Request Body:** 없음
- **요청 주소 예시:** `GET /internal/v1/disclosure-sync-jobs/{jobId}`
- **Response status:** `200 OK`

**Path Variables**

| 변수 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `jobId` | `UUID` | 예 | 조회할 동기화 작업 ID |

**Response 200**

```json
{
  "success": true,
  "code": null,
  "message": "공시 동기화 상태 조회에 성공했습니다.",
  "data": {
    "jobId": "fd756c8b-c94f-4a5b-92bb-e9a59c943535",
    "status": "COMPLETED",
    "provider": "COMPETITION_DATA",
    "companyCount": 3,
    "fetchedDisclosureCount": 12,
    "createdDisclosureCount": 4,
    "updatedDisclosureCount": 1,
    "failedDisclosureCount": 0,
    "startedAt": "2026-07-24T11:00:01+09:00",
    "completedAt": "2026-07-24T11:00:08+09:00",
    "failures": []
  }
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `jobId` | `UUID` | 동기화 작업 ID |
| `status` | `String` | `PENDING`, `PROCESSING`, `COMPLETED`, `PARTIAL`, `FAILED` |
| `provider` | `String` | 사용한 공시 데이터 공급자 |
| `companyCount` | `Integer` | 조회 대상 기업 수 |
| `fetchedDisclosureCount` | `Integer` | 공급자로부터 읽은 공시 수 |
| `createdDisclosureCount` | `Integer` | 새로 저장한 공시 수 |
| `updatedDisclosureCount` | `Integer` | 변경되어 갱신한 공시 수 |
| `failedDisclosureCount` | `Integer` | 처리에 실패한 공시 수 |
| `startedAt` | `OffsetDateTime, null` | 작업 시작 시각 |
| `completedAt` | `OffsetDateTime, null` | 작업 완료 시각 |
| `failures` | `List<Object>` | 공시별 실패 정보 |

**Error**

| 상황 | HTTP | 에러 코드 |
|---|---:|---|
| 내부 인증정보가 없거나 유효하지 않음 | 401 | `COMMON_401_1` |
| 동기화 조회 권한이 없음 | 403 | `COMMON_403_1` |
| 동기화 작업이 존재하지 않음 | 404 | `COMMON_404_1` 또는 동기화 전용 코드 추가 필요 |

---

## 14. 운영 상태

### 14.1 종합 상태 조회

- **URL: `GET /actuator/health`**
- **인증:** 사용자 세션 불필요. 배포 환경의 네트워크·보안 정책 적용
- **Description:**
  - Spring Boot 애플리케이션의 종합 상태를 확인한다.
  - Docker Compose의 상태 확인과 운영 모니터링에서 사용할 수 있다.
  - 외부에는 DB 주소, 인증정보 또는 공급자의 상세 오류를 노출하지 않는다.
- **Request Body:** 없음
- **Response status:** `200 OK` 또는 `503 Service Unavailable`

**Response 200**

```json
{
  "status": "UP"
}
```

**Response Field**

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | `String` | 종합 상태. 정상일 때 `UP` |

### 14.2 요청 수신 준비 상태 조회

- **URL: `GET /actuator/health/readiness`**
- **인증:** 사용자 세션 불필요. 배포 환경의 네트워크·보안 정책 적용
- **Description:**
  - 애플리케이션이 실제 요청을 받을 준비가 됐는지 확인한다.
  - DB 연결 등 필수 의존성이 준비되지 않았으면 `OUT_OF_SERVICE` 또는 `DOWN`을 반환할 수 있다.
- **Request Body:** 없음
- **Response status:** `200 OK` 또는 `503 Service Unavailable`

**Response 200**

```json
{
  "status": "UP"
}
```

### 14.3 프로세스 생존 상태 조회

- **URL: `GET /actuator/health/liveness`**
- **인증:** 사용자 세션 불필요. 배포 환경의 네트워크·보안 정책 적용
- **Description:**
  - 애플리케이션 프로세스를 재시작해야 할 정도로 비정상인지 확인한다.
  - 컨테이너 오케스트레이터의 재시작 판단에 사용한다.
- **Request Body:** 없음
- **Response status:** `200 OK` 또는 `503 Service Unavailable`

**Response 200**

```json
{
  "status": "UP"
}
```

**운영 상태 API 공통 Error**

| 상황 | HTTP | 응답 |
|---|---:|---|
| 애플리케이션 또는 필수 의존성이 비정상 | 503 | Actuator 표준 health 응답 |
| 운영 환경에서 엔드포인트 접근을 제한함 | 401 또는 403 | Spring Security 또는 인프라 정책 응답 |

Actuator 응답은 FolioLens의 `ApiResponse`로 감싸지 않고 Spring Boot Actuator의 표준 형식을 사용한다.

---

## 15. 구현 전 확정 사항

아래 항목은 상세 명세를 구현 계약으로 확정하기 전에 결정해야 한다.

1. 데모 세션을 유지할지 JWT 인증으로 교체할지
2. `PATCH` 요청에서 `null`을 “값 제거”로 사용할지, 해당 필드를 무시할지
3. 세션당 활성 포트폴리오 중복 오류 코드를 새로 추가할지
4. 답변 요청과 동기화 작업 전용 `404` 오류 코드를 추가할지
5. 분석 요청의 `Idempotency-Key`를 필수로 강제할지
6. `force=true` 재분석을 일반 사용자에게 허용할지
7. Actuator health group이 실제 설정에서 활성화되어 있는지
8. 도메인 오류 코드를 `ErrorCode`에 추가하고 API별 오류 표와 일치시키기
