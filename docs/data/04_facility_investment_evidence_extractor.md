# 시설투자 Evidence 추출기 구현

| 항목 | 내용 |
|---|---|
| 문서 ID | `FACILITY-INVESTMENT-EVIDENCE-EXTRACTOR` |
| 문서 버전 | v1.0 |
| 작성일 | 2026-09-01 |
| 추출기 버전 | `facility-evidence-v1` |
| 문서 상태 | 추출 규칙·서비스·단위 테스트 구현 완료 |

## 1. 구현 목적

파싱된 시설투자 표에서 승인된 Fact 레이블과 실제 값 셀을 찾아
`CANDIDATE` Evidence를 생성한다.

```text
ParsedDisclosureTable
→ rowSpan·colSpan을 펼친 LogicalTableGrid
→ 승인된 시설투자 행·열 레이블 매칭
→ 실제 값 셀과 원문 위치 특정
→ DisclosureEvidence(CANDIDATE)
```

추출기는 숫자·날짜 정규화나 `VERIFIED` 판정을 하지 않는다. 검색 문자열이
일치했다는 이유만으로 값을 검증된 Fact로 승격하지 않기 위해 책임을
분리했다.

## 2. 구현 구성

| 구성요소 | 책임 |
|---|---|
| `FacilityInvestmentExtractionContext` | 공시·문서·Section·ContentBlock 문맥 전달 |
| `FacilityInvestmentEvidenceExtractor` | 표의 Fact 레이블과 값 셀을 찾아 Evidence 후보 생성 |
| `FacilityInvestmentEvidenceExtractionResult` | Fact별 0~N개 후보, 누락·모호성·경고 표현 |
| `FacilityInvestmentEvidenceExtractionService` | 파싱 완료 문서의 TABLE 블록 조회와 추출기 연결 |
| `FacilityInvestmentFactDefinition` | Fact Key, 타입, 표준단위, 승인 원문 레이블 관리 |

## 3. 현재 추출 대상

필수 핵심 Fact는 요구사항의 8개다.

| Fact Key | 대표 레이블 |
|---|---|
| `facility.target` | 투자대상 |
| `facility.amount` | 투자금액(원) |
| `facility.equity_amount` | 자기자본(원) |
| `facility.equity_ratio` | 자기자본대비(%) |
| `facility.purpose` | 투자목적 |
| `facility.start_date` | 투자기간 + 시작일 |
| `facility.end_date` | 투자기간 + 종료일 |
| `facility.decision_date` | 이사회결의일(결정일) |

`facility.type`은 핵심 완료 조건에는 포함하지 않지만 `투자구분`에서 함께
추출하는 보조 Fact다.

## 4. 추출 규칙

### 4.1 레이블 정규화

다음 표현 차이를 제거한 뒤 승인 레이블과 정확히 비교한다.

- 순번: `2. 투자금액` → `투자금액`
- 목록 기호: `- 투자대상` → `투자대상`
- 공백: `투자 금액` → `투자금액`
- 단위 접미사: `투자금액(원)` → 레이블 `투자금액`, 원문 단위 `원`

부분 문자열만으로 Fact를 확정하지 않는다.

### 4.2 표 병합 처리

`TableLogicalGridBuilder`를 재사용해 `rowSpan`과 `colSpan`을 펼친다. 병합으로
복제된 논리 셀은 Evidence 값으로 중복 수집하지 않고 실제 원본 셀의
`cellIndex`와 원문 행을 저장한다.

예를 들어 다음 행은 하나의 투자금액 Evidence가 된다.

```text
2. 투자내역 | 투자금액(원) | 5,296,200,000,000
```

- `rowLabel`: `2. 투자내역 > 투자금액(원)`
- `rawValue`: `5,296,200,000,000`
- `rawUnit`: `원`
- `blockType`: `TABLE_CELL`
- `tableRowIndex`: 실제 표 행
- `tableCellIndex`: 실제 값 TD의 셀 인덱스

### 4.3 중첩 표

중첩 표는 부모 표와 섞지 않고 재귀적으로 별도 조사한다. Evidence에는
청킹과 같은 형식의 경로를 저장한다.

```text
rows[0].cells[1].nestedTables[0]
```

### 4.4 중복 후보

같은 Fact 레이블이 여러 행에서 발견되면 추출기가 임의로 하나를 고르지
않는다. 모든 후보를 반환하고 해당 Fact를 `ambiguousDefinitions`로 표시해
후속 검증기가 판정하도록 한다.

### 4.5 멱등 ID

Evidence ID는 문서 ID, ContentBlock ID, 중첩 경로, 행·셀 위치, Fact Key와
추출기 버전으로 결정한다. 동일 입력을 같은 버전으로 다시 추출하면 같은
Evidence ID가 만들어진다.

## 5. 서비스 검증

`FacilityInvestmentEvidenceExtractionService`는 다음 조건을 확인한다.

1. `rawSubtype=신규시설투자등`인 문서만 허용한다.
2. 파싱 상태가 `COMPLETED` 또는 `PARTIAL`이어야 한다.
3. TABLE ContentBlock만 조회한다.
4. Section 경로가 없으면 `문서 서두`로 기록한다.
5. 정정공시는 `eventDocumentRole=CORRECTION`으로 표시한다.
6. 핵심 후보 누락과 복수 후보를 경고로 반환한다.

## 6. 테스트 범위

SK하이닉스 `20240424800596`의 실제 표 배열을 재현한 테스트로 다음을
확인했다.

- 핵심 8개와 보조 투자유형 추출
- 금액 `원`, 비율 `%` 원문 단위 보존
- 투자기간 시작일·종료일 구분
- rowSpan·colSpan 처리
- 중첩 표 경로 보존
- 중복 후보의 모호성 유지
- 값 셀이 없는 레이블 제외
- 동일 입력의 Evidence ID 멱등성
- 문서 조회 서비스의 유형·파싱 상태 검증

## 7. 현재 데이터 상태와 다음 단계

대회 데이터의 `신규시설투자등` 원문은 43건 모두 `HTML` 콘텐츠다. 파일
확장자는 `.xml`일 수 있지만 실제 루트는 `<html>`이다. 현재 DB의 해당
43개 문서는 아직 파싱되지 않아 `PENDING` 상태이며 TABLE ContentBlock도
없다.

따라서 다음 순서는 다음과 같다.

1. 거래소 HTML을 `ParsedDisclosureDocument/Table`로 변환하는 파서 구현
2. HTML 파싱 결과를 기존 Section·ContentBlock 저장 흐름에 연결
3. `20240424800596` 실제 DB 데이터로 Evidence 8개 추출 검증
4. 금액·비율·날짜 정규화기 구현
5. Evidence 후보를 검증해 `DisclosureFact` 생성
6. 43건 배치로 미매핑·복수 후보·단위 예외 수집
