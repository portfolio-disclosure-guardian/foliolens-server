# Evidence 및 시설투자 Fact 도메인 모델 구현

| 항목 | 내용 |
|---|---|
| 문서 ID | `EVIDENCE-FACILITY-FACT-DOMAIN-MODEL` |
| 문서 버전 | v1.0 |
| 작성일 | 2026-09-01 |
| 문서 상태 | 도메인 모델 및 단위 테스트 구현 완료 |
| 관련 계약 | `docs/data/01_search_fact_evidence_logical_contract.md` |

## 1. 구현 목적

검색된 청크를 바로 사실로 사용하지 않고 다음 경계를 코드로 표현한다.

```text
검색 청크 후보
→ 원문 위치와 원문값을 특정한 Evidence
→ 자료형·단위·검증 상태를 가진 Fact
→ 검증된 Fact만 계산 및 답변에 사용
```

이번 단계는 JPA Entity나 DB 테이블 구현이 아니다. 대표 시설투자 공시를
수직으로 추출하고 검증하기 위한 저장소 독립 도메인 모델이다.

## 2. Evidence 모델

### `DisclosureEvidence`

Fact 판단에 실제로 사용한 문장, 표 행 또는 표 셀을 표현한다.

- 공시·문서·접수번호를 식별한다.
- Section, ContentBlock, 원문 행 범위를 보존한다.
- TABLE Evidence는 행·셀 위치와 중첩 표 경로를 보존한다.
- 원문 문자열, 행·열 레이블, 원문값, 원문 단위, 주석을 함께 보존한다.
- `CANDIDATE`와 `VERIFIED`를 구분한다.

관련 값 객체:

- `DisclosureEvidenceLocation`: XML 행 및 표 내부 위치
- `DisclosureEvidenceValue`: 원문 텍스트·레이블·값·단위·주석

주요 불변조건:

1. 원문 Evidence에는 `contentBlockId`가 필요하다.
2. `TABLE_ROW`에는 행 인덱스와 행 레이블이 필요하다.
3. `TABLE_CELL`에는 행·셀 인덱스와 행 레이블이 필요하다.
4. 문단·제목 Evidence에 표 위치를 지정할 수 없다.
5. XML 행은 둘 다 미확인 `-1`이거나 유효한 시작·종료 범위여야 한다.

## 3. Fact 모델

### `DisclosureFact`

하나의 금융적 의미를 다음 정보와 함께 표현하는 공통 envelope다.

- `factKey`: `facility.amount`와 같은 승인된 의미 ID
- `rawValue`, `rawUnit`: 공시 원문 표시값
- `normalizedValue`, `normalizedUnit`: 타입이 보존된 표준값
- 생성·가용성·정규화·검증 상태
- 정규화 또는 계산 정책 버전
- Fact를 뒷받침하는 Evidence ID 목록

### 타입이 보존된 값 모델

| 모델 | 타입 | 시설투자 예 |
|---|---|---|
| `TextFactValue` | `TEXT` | 투자대상, 투자목적 |
| `DecimalFactValue` | `DECIMAL` | 투자금액, 자기자본, 비율 |
| `DateFactValue` | `DATE` | 시작일, 종료일, 결정일 |
| `CodeFactValue` | `CODE` | 표준 투자유형 코드 |

`normalizedValue`를 임의 문자열이나 `Object`로 두지 않으므로, 백엔드가
숫자와 날짜를 결정적으로 계산하고 검증할 수 있다.

주요 불변조건:

1. `normalizedValue` 타입은 `valueType`과 같아야 한다.
2. 원문 기반 수치 Fact에는 `rawValue`와 `rawUnit`이 필요하다.
3. 정규화된 수치 Fact에는 `normalizedUnit`이 필요하다.
4. `KRW` 금액의 통화 코드는 `KRW`여야 한다.
5. 정책을 적용해 만든 Fact에는 `policyVersion`이 필요하다.
6. 원문 기반 `VERIFIED` Fact에는 Evidence가 필요하다.
7. 모호하거나 검토가 필요한 Fact는 `VERIFIED`가 될 수 없다.
8. 기간 시작일과 종료일은 함께 존재하며 올바른 순서여야 한다.

## 4. 시설투자 Fact 정의

`FacilityInvestmentFactDefinition`은 첫 수직 구현에서 사용하는 Fact Key,
자료형, 표준 단위와 원문 표 레이블을 한곳에서 관리한다.

| Fact Key | 타입 | 표준 단위 | 대표 원문 레이블 |
|---|---|---|---|
| `facility.type` | `CODE` | - | 투자구분 |
| `facility.target` | `TEXT` | - | 투자대상 |
| `facility.amount` | `DECIMAL` | `KRW` | 투자금액, 투자예정금액 |
| `facility.equity_amount` | `DECIMAL` | `KRW` | 자기자본 |
| `facility.equity_ratio` | `DECIMAL` | `PERCENT` | 자기자본대비 |
| `facility.purpose` | `TEXT` | - | 투자목적 |
| `facility.start_date` | `DATE` | `ISO_DATE` | 투자기간 + 시작일 |
| `facility.end_date` | `DATE` | `ISO_DATE` | 투자기간 + 종료일 |
| `facility.decision_date` | `DATE` | `ISO_DATE` | 이사회결의일, 결정일 |

레이블 비교는 공백 차이를 제거한 뒤 정확히 비교한다. 부분 문자열만으로
Fact를 확정하지 않으므로 비슷한 문구를 잘못 매핑할 가능성을 줄인다.

### `FacilityInvestmentFactSet`

동일한 공시와 원문 문서에서 추출된 시설투자 Fact를 묶는다. 일부 값이
없는 `PARTIAL` 결과도 표현할 수 있으며, 정의와 Fact의 키·타입·단위,
공시 ID, 문서 ID와 접수번호가 일치하는지 검증한다.

## 5. 현재 완료 범위와 다음 단계

완료:

- Evidence 위치·원문값·상태 모델
- 타입이 보존된 Fact 값 모델
- 공통 Fact envelope와 불변조건
- 시설투자 Fact 정의와 Fact 묶음
- 시설투자 TABLE 행·셀 Evidence 후보 추출기
- 모델 단위 테스트

아직 하지 않은 작업:

- 거래소 HTML 원문을 공통 파싱 모델과 ContentBlock으로 만드는 파서
- 금액·비율·날짜 정규화기
- 시설투자 Fact 생성·검증 서비스
- 대표 골든 공시 통합 테스트와 금융 담당자 검수
- Fact·Evidence JPA Entity, Flyway 및 조회 도구

다음 구현은 거래소 HTML 파서를 연결한 뒤 `facility.amount`와
`facility.purpose` 두 항목을 대상으로 정규화 → Fact 검증까지 연결한다.
