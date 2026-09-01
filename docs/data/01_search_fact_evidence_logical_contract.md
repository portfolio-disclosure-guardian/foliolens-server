# 검색 결과·Fact·Evidence 논리 계약

| 항목 | 내용 |
|---|---|
| 문서 ID | `SEARCH-FACT-EVIDENCE-CONTRACT` |
| 문서 버전 | v0.1 |
| 작성일 | 2026-08-27 |
| 문서 상태 | 검색 수직 구현을 위한 초안 |
| 현재 구현 범위 | 공시 메타데이터 검색과 검색 청크 결과 계약 |
| 다음 구현 범위 | 시설투자 Fact·Evidence 생성과 저장 |
| 기준 문서 | `요구사항_정의서.md`, `기능명세서.md`, `TOOL_CONTRACTS.md`, `finance_domain/00.공통규격.md`, `finance_domain/02.신규시설투자.md`, `finance_domain/12.정정후속공시기준시점상태.md` |

## 1. 문서 목적

이 문서는 다음 세 단계가 서로 임의의 DTO를 만들지 않고 같은 의미로 데이터를 주고받도록 논리 계약을 정의한다.

```text
검색
→ 원문 근거 확인
→ Fact 추출·검증
```

현재 바로 구현할 대상은 검색 조건·검색 결과·원문 출처 참조다. Fact와 Evidence는 검색 결과가 이후 추출 단계로 연결될 수 있도록 논리 구조와 경계를 먼저 정의하며, JPA Entity와 Flyway 스키마는 대표 시설투자 문서의 수직 검증 후 확정한다.

이 문서가 해결하려는 문제는 다음과 같다.

- 검색 결과와 검증된 사실을 같은 것으로 취급하지 않는다.
- 검색 결과에서 원본 ContentBlock과 TABLE 행까지 돌아갈 수 있게 한다.
- 원문값과 정규화값을 모두 보존한다.
- LLM이 임의로 값·단위·날짜·상태를 만들지 못하게 한다.
- 검색, Fact 추출, 계산, 답변에서 같은 Fact와 Evidence 계약을 재사용한다.

## 2. 핵심 용어와 경계

| 용어 | 정의 | 신뢰 수준 | 저장 여부 |
|---|---|---|---|
| 공시 검색 결과 | 기업·기간·공시 유형 조건과 일치하는 공시 후보 | 후보 | 초기에는 미저장 |
| 청크 검색 결과 | 질문 키워드와 관련성이 높은 TEXT·TABLE 청크 후보 | 후보 | 초기에는 미저장 |
| 원문 출처 참조 | 청크가 만들어진 ContentBlock·행 범위의 식별정보 | 위치 참조 | `disclosure_chunk_sources`에 저장됨 |
| Evidence | Fact 판단에 실제 사용한 표·행·셀·문장과 위치 | 검증 대상 | Fact 수직 구현 후 저장 검토 |
| Fact | Evidence를 통해 확인한 구조화 사실 | 검증 상태에 따라 사용 | Fact 수직 구현 후 저장 |
| Calculation | VERIFIED Fact만 입력받아 백엔드가 계산한 결과 | 결정적 결과 | 후속 구현 |

핵심 구분:

```text
검색 점수가 높다
≠ 사실로 검증됐다
```

검색 결과의 `score`는 질문과 관련될 가능성이다. Fact의 `validationStatus=VERIFIED`는 자료형·단위·대상·기간·근거를 검증했다는 뜻이다.

## 3. 전체 연결 구조

```text
DisclosureMetadataSearchCondition
→ 공시 메타데이터 검색
→ DisclosureMetadataSearchHit

DisclosureChunkSearchCondition
→ 선택된 공시의 청크 검색
→ DisclosureChunkSearchHit
   └─ DisclosureChunkSourceReference
      └─ DisclosureContentBlock / TABLE JSONB
         └─ DisclosureEvidence
            └─ DisclosureFact
               └─ Calculation
                  └─ Answer Claim
```

검색은 두 단계로 분리한다.

1. `SEARCH_DISCLOSURES`: 기업·기간·공시 유형으로 문서 후보를 줄인다.
2. `SEARCH_EVIDENCE`: 선택된 공시 안에서 관련 청크와 원문 후보를 찾는다.

청크 139만여 건 전체를 아무 조건 없이 본문 검색하지 않는다.

## 4. 공시 메타데이터 검색 계약

### 4.1 `DisclosureMetadataSearchCondition`

공시 본문을 검색하기 전에 기업·기간·공시 유형으로 후보 공시를 제한한다.

| 필드 | 타입 | 필수 | 의미 | 검증 규칙 |
|---|---|---:|---|---|
| `companyIds` | `Set<UUID>` | 조건부 | 대상 기업 ID | 비어 있으면 기업 미지정 검색으로 간주하되 API 상한 적용 |
| `receiptDateFrom` | `LocalDate` | 조건부 | 접수일 시작 | 종료일보다 뒤일 수 없음 |
| `receiptDateTo` | `LocalDate` | 조건부 | 접수일 종료 | 시작일보다 앞일 수 없음 |
| `asOf` | `LocalDate` | 조건부 | 조회 시점 상한 | `receiptDate <= asOf`; 최신 Fact 확정을 의미하지 않음 |
| `sourceGroups` | `Set<DisclosureSourceGroup>` | 조건부 | `periodic/major/exchange/holding` | 승인 enum만 허용 |
| `categories` | `Set<DisclosureCategory>` | 조건부 | 내부 공시 카테고리 | 승인 enum만 허용 |
| `rawSubtypes` | `Set<String>` | 조건부 | Manifest 원문 세부유형 | 원문값을 임의 표준화하지 않음 |
| `titleTerms` | `List<String>` | 조건부 | 보고서명 검색어 | 본문 검색어로 사용하지 않음 |
| `correctionFilter` | `CorrectionFilter` | 필수 | 정정 포함 방식 | 기본값 `ALL` |
| `limit` | `int` | 필수 | 최대 후보 수 | 1~50 |

`CorrectionFilter`:

| 값 | 의미 |
|---|---|
| `ALL` | 원공시와 정정공시 모두 포함 |
| `ORIGINAL_ONLY` | `correction=false`만 조회 |
| `CORRECTION_ONLY` | `correction=true`만 조회 |

기본값을 `LATEST_ONLY`로 두지 않는다. 최신 유효 상태는 별도의 정정·후속 관계 해결 단계가 결정한다.

보고서명 검색어는 구조화 유형 필터의 존재 여부에 따라 다르게 사용한다.

- `sourceGroups`, `categories`, `rawSubtypes` 중 하나 이상이 지정되면
  `titleTerms`는 후보를 제외하지 않고 검색 점수 계산에만 사용한다.
- 세 구조화 유형 필터가 모두 비어 있으면 `titleTerms` 중 하나 이상이
  보고서명에 포함된 공시만 후보로 남긴다.

이 규칙은 승인된 공시 유형으로 후보가 이미 제한된 상황에서 `CAPEX`,
`증설` 등의 표현 차이 때문에 관련 공시가 누락되는 것을 방지한다.

### 4.2 `DisclosureMetadataSearchHit`

| 필드 | 타입 | 필수 | 출처 |
|---|---|---:|---|
| `disclosureId` | `UUID` | 필수 | `disclosures.id` |
| `companyId` | `UUID` | 필수 | `companies.id` |
| `companyName` | `String` | 필수 | 회사 마스터 |
| `stockCode` | `String` | 조건부 | 회사 마스터 |
| `receiptNo` | `String` | 필수 | `disclosures.receipt_no` |
| `receiptDate` | `LocalDate` | 필수 | `disclosures.receipt_date` |
| `reportName` | `String` | 필수 | `disclosures.report_name` |
| `sourceGroup` | `DisclosureSourceGroup` | 필수 | `disclosures.source_group` |
| `category` | `DisclosureCategory` | 필수 | `disclosures.category` |
| `rawSubtype` | `String` | 조건부 | `disclosures.raw_subtype` |
| `correction` | `boolean` | 필수 | `disclosures.correction` |
| `sourceProvider` | `SourceProvider` | 필수 | 평가 경로에서는 `CONTEST`만 허용 |
| `documentCount` | `int` | 필수 | 연결된 원문 파일 수 |
| `searchScore` | `double` | 필수 | 제목·메타데이터 관련도 |
| `matchedTerms` | `List<String>` | 필수 | 실제 일치한 제목 검색어 |

### 4.3 메타데이터 검색 결과 묶음

`DisclosureMetadataSearchResult`는 다음 정보를 포함한다.

| 필드 | 의미 |
|---|---|
| `items` | 순위가 적용된 공시 후보 목록 |
| `candidateCount` | 필터 조건에 맞은 전체 후보 수 |
| `truncated` | limit 때문에 일부 후보가 제외됐는지 여부 |
| `warnings` | 모호한 기업·기간 누락·지원하지 않는 필터 경고 |
| `retrievalVersion` | 검색 정책·쿼리 버전 |

검색 결과가 없으면 예외가 아니라 빈 `items`를 반환한다.

## 5. 청크 검색 계약

### 5.1 `DisclosureChunkSearchCondition`

선택된 공시 집합 안에서 질문과 관련된 TEXT·TABLE 청크를 찾는다.

| 필드 | 타입 | 필수 | 의미 | 검증 규칙 |
|---|---|---:|---|---|
| `disclosureIds` | `Set<UUID>` | 필수 | 1단계에서 선택한 공시 | 비어 있으면 실행하지 않음 |
| `documentIds` | `Set<UUID>` | 조건부 | 특정 원문 파일로 더 제한 | 해당 공시에 속해야 함 |
| `concepts` | `Set<String>` | 조건부 | `CAPACITY_EXPANSION` 등 질문 개념 | 승인된 개념 또는 검색 힌트 |
| `factKeys` | `Set<String>` | 조건부 | `facility.amount` 등 필요한 Fact | 금융 문서가 승인한 Fact ID |
| `sectionHints` | `List<String>` | 조건부 | 예상 Section·표 제목 | 순위 가중치로 사용 |
| `keywords` | `List<String>` | 조건부 | 본문·표 검색어 | 최소 한 개 검색 신호 필요 |
| `chunkTypes` | `Set<DisclosureChunkType>` | 조건부 | `TEXT`, `TABLE` | 비어 있으면 둘 다 허용 |
| `topK` | `int` | 필수 | 반환할 검색 적중 수 | 초기 1~20 |
| `neighborRadius` | `int` | 필수 | 같은 Section의 앞뒤 청크 확장 범위 | 모델은 0~2, `chunk-search-v1` 실행은 0만 지원 |

다음 중 하나 이상은 반드시 있어야 한다.

- `concepts`
- `factKeys`
- `sectionHints`
- `keywords`

`factKeys`는 아직 Fact가 저장되지 않은 초기 단계에서는 Section·행 레이블·동의어를 선택하는 검색 힌트로만 사용한다. Fact가 적재된 후에는 `LOOKUP_FACTS`가 정확 조회를 먼저 수행한다.

### 5.2 `DisclosureChunkSearchHit`

| 필드 | 타입 | 필수 | 의미 |
|---|---|---:|---|
| `chunkId` | `UUID` | 필수 | `disclosure_chunks.id` |
| `disclosureId` | `UUID` | 필수 | 공시 메타데이터 ID |
| `disclosureDocumentId` | `UUID` | 필수 | 실제 원문 문서 ID |
| `companyId` | `UUID` | 필수 | 대상 기업 ID |
| `companyName` | `String` | 필수 | 표시·검증용 기업명 |
| `receiptNo` | `String` | 필수 | 근거 공시 접수번호 |
| `receiptDate` | `LocalDate` | 필수 | 공시 접수일 |
| `reportName` | `String` | 필수 | 공시명 |
| `correction` | `boolean` | 필수 | 정정 여부 |
| `documentName` | `String` | 필수 | 실제 원문 파일의 문서명 |
| `documentFileRole` | `DisclosureDocumentRole` | 필수 | 현재 DB의 `MAIN/ATTACHMENT/AUDIT_REPORT/VIEWER/UNKNOWN` |
| `eventDocumentRole` | `EventDocumentRole` | 조건부 | 사건 안의 원공시·정정·결과·해지 역할; 이력 해결 후 사용 |
| `chunkType` | `DisclosureChunkType` | 필수 | `TEXT` 또는 `TABLE` |
| `chunkSequenceNo` | `int` | 필수 | 문서 안 검색 청크 순번 |
| `sectionPath` | `String` | 필수 | 전체 Section 경로 |
| `bodyText` | `String` | 필수 | 검색 가능한 청크 본문 |
| `searchText` | `String` | 필수 | Section·제목·상위 표 문맥이 포함된 검색 문자열 |
| `searchScore` | `double` | 필수 | 검색 관련도 점수 |
| `scoreBreakdown` | `SearchScoreBreakdown` | 조건부 | 제목·Section·본문 등 점수 구성 |
| `matchedTerms` | `List<String>` | 필수 | 실제 일치한 검색어 |
| `sources` | `List<DisclosureChunkSourceReference>` | 필수 | 원본 위치 참조; 1개 이상 |
| `generatorVersion` | `String` | 필수 | 현재 `dart-xml-chunk-v3` |
| `retrievalVersion` | `String` | 필수 | 검색 정책·쿼리 버전 |

`bodyText`는 LLM에 제공 가능한 검색 본문이다. `searchText`는 검색용 문맥을 포함하므로 사용자에게 원문 그대로라고 표시하지 않는다.

### 5.3 `SearchScoreBreakdown`

점수 계산을 재현하고 검색 품질을 분석하기 위한 선택 모델이다.

| 필드 | 의미 |
|---|---|
| `reportNameScore` | 공시명 일치 점수 |
| `sectionPathScore` | Section·표 경로 일치 점수 |
| `bodyScore` | 청크 본문 일치 점수 |
| `phraseBonus` | 연속 구문 일치 보너스 |
| `factHintBonus` | Fact 레이블·동의어 일치 보너스 |
| `correctionPenaltyOrBonus` | 질문 모드에 따른 정정 가중치 |
| `finalScore` | 최종 순위 점수 |

초기 구현에서 DB가 하나의 최종 점수만 제공한다면 `scoreBreakdown`은 null일 수 있다. 검색 품질 조정 단계에서 추가한다.

### 5.4 `DisclosureChunkSourceReference`

현재 `disclosure_chunk_sources`와 일치하는 검색→원문 연결 모델이다.

| 필드 | 타입 | 필수 | 출처 |
|---|---|---:|---|
| `chunkSourceId` | `UUID` | 필수 | `disclosure_chunk_sources.id` |
| `contentBlockId` | `UUID` | 필수 | 원본 ContentBlock |
| `sourceOrder` | `int` | 필수 | 청크 안 출처 순서; 1부터 시작 |
| `blockSequenceNo` | `int` | 필수 | 문서 내 원본 Block 순번 |
| `sourceLineStart` | `int` | 필수 | 원문 시작 행; 미확인이면 -1 |
| `sourceLineEnd` | `int` | 필수 | 원문 종료 행; 미확인이면 -1 |
| `tableNestingPath` | `String` | 조건부 | 중첩 TABLE JSON 경로 |
| `tableRowIndexStart` | `Integer` | 조건부 | TABLE 원본 행 시작 인덱스 |
| `tableRowIndexEnd` | `Integer` | 조건부 | TABLE 원본 행 종료 인덱스 |

이 모델은 최종 Evidence가 아니다. Fact 추출기가 원본 블록을 다시 열 수 있게 하는 위치 참조다.

### 5.5 청크 검색 결과 묶음

`DisclosureChunkSearchResult`는 다음 정보를 포함한다.

| 필드 | 의미 |
|---|---|
| `items` | 순위가 적용된 청크 검색 결과 |
| `searchedDisclosureIds` | 실제 검색한 공시 집합 |
| `searchedDocumentCount` | 실제 검색한 원문 문서 수 |
| `candidateChunkCount` | 순위 적용 전 후보 청크 수 |
| `truncated` | topK 때문에 후보가 제외됐는지 여부 |
| `warnings` | 파싱 미완료·청킹 미완료·검색어 미매핑 등 |
| `retrievalVersion` | 검색 정책·쿼리 버전 |

### 5.6 `chunk-search-v1` 구현 기준

- 검색어 해석기는 첫 수직 범위인 시설투자의 핵심 `facility.*` Fact를
  금융 도메인 문서의 원문 레이블과 Section 힌트로 변환한다.
- 미지원 concept·factKey는 추측해 변환하지 않고 검색 경고로 반환한다.
- 평가는 `sourceProvider=CONTEST`, `chunk_status=COMPLETED`, 요청한
  `disclosureIds/documentIds`, `TEXT/TABLE` 범위에서만 수행한다.
- 후보는 `search_text`와 `section_path`의 문자열 일치로 제한하고,
  공시명·Section·본문·연속 구문·Fact 레이블 점수를 합산해 정렬한다.
- 상위 청크의 `disclosure_chunk_sources`는 일괄 조회하여 원본 Block,
  XML 행과 TABLE 행 범위를 함께 반환한다.
- `neighborRadius`를 조용히 무시하지 않는다. 이웃 문맥 출력 모델이 없는
  `chunk-search-v1`은 0만 허용하며, 1~2는 후속 버전에서 구현한다.

## 6. 검색 결과 불변조건

검색 구현은 다음 조건을 만족해야 한다.

1. 청크 검색은 `disclosureIds`가 비어 있으면 실행하지 않는다.
2. 검색된 문서와 청크는 요청한 공시에 속해야 한다.
3. 평가 경로의 근거는 `sourceProvider=CONTEST`만 허용한다.
4. `chunk_status=COMPLETED`인 문서의 청크만 검색한다.
5. `bodyText`, `searchText`, `sectionPath`는 null일 수 없다.
6. 모든 청크 적중에는 원문 출처가 1개 이상 있어야 한다.
7. TABLE 출처의 행 시작은 종료보다 뒤일 수 없다.
8. `topK`와 `limit` 상한을 백엔드가 검증한다.
9. 검색 결과가 없으면 외부 OpenDART·뉴스·웹으로 우회하지 않는다.
10. `asOf` 이후 접수된 공시는 검색 후보에서 제외한다.
11. 검색 점수만으로 Fact를 `VERIFIED`로 만들지 않는다.
12. 원시 서버 경로와 내부 예외 메시지를 외부 응답에 노출하지 않는다.

## 7. Evidence 논리 계약

### 7.1 검색 출처와 Evidence의 차이

```text
DisclosureChunkSourceReference
→ 원본 후보 위치

DisclosureEvidence
→ Fact 판단에 실제 사용한 정확한 표·행·셀·문장
```

예를 들어 청크 출처는 TABLE 5행~8행을 가리킬 수 있다. `facility.amount` Evidence는 그중 `투자금액` 행의 값 셀, 표 단위와 관련 주석까지 특정해야 한다.

### 7.2 `DisclosureEvidence`

| 필드 | 타입 | 필수 | 의미 |
|---|---|---:|---|
| `evidenceId` | `UUID` | 저장 시 | Evidence 식별자 |
| `disclosureId` | `UUID` | 필수 | 근거 공시 |
| `disclosureDocumentId` | `UUID` | 필수 | 실제 근거 문서 |
| `receiptNo` | `String` | 필수 | 근거 접수번호 |
| `documentName` | `String` | 필수 | 실제 원문 문서명 |
| `documentFileRole` | `DisclosureDocumentRole` | 필수 | 공시 안 실제 파일의 본문·첨부·감사보고서·뷰어 역할 |
| `eventDocumentRole` | `EventDocumentRole` | 조건부 | 사건 안의 원공시·정정·결과·해지 역할 |
| `sectionId` | `UUID` | 조건부 | preamble이면 null 가능 |
| `sectionPath` | `String` | 필수 | 장·절·표 경로 |
| `contentBlockId` | `UUID` | 필수 | 근거 ContentBlock |
| `blockType` | `EvidenceBlockType` | 필수 | `TABLE_CELL/TABLE_ROW/PARAGRAPH/TITLE` 등 |
| `tableNestingPath` | `String` | 조건부 | 중첩 표 경로 |
| `tableIndexOrName` | `String` | 조건부 | 표 식별정보 |
| `tableRowIndex` | `Integer` | 조건부 | 원본 TABLE 행 |
| `tableCellIndex` | `Integer` | 조건부 | 원본 TABLE 셀 |
| `rowLabel` | `String` | 수치 TABLE Fact | 값의 행 레이블 |
| `columnLabel` | `String` | 조건부 | 값의 열 머리글 |
| `sourceText` | `String` | 필수 | 판단에 사용한 원문 문장·표 행 |
| `rawValue` | `String` | 직접값 Fact | 실제 원문값 |
| `rawUnit` | `String` | 수치 Fact | 실제 원문 단위 |
| `sourceLineStart` | `int` | 필수 | 원문 시작 행 |
| `sourceLineEnd` | `int` | 필수 | 원문 종료 행 |
| `noteText` | `String` | 조건부 | 단위·산정기준·각주·기타사항 |

숫자 셀 하나만 Evidence로 저장하지 않는다. 표 머리글, 행 레이블, 단위와 산정기준을 함께 재현할 수 있어야 한다.

`documentFileRole`과 `eventDocumentRole`은 합치지 않는다.

```text
documentFileRole
→ 하나의 공시에 포함된 실제 파일 역할
→ MAIN, ATTACHMENT, AUDIT_REPORT, VIEWER, UNKNOWN

eventDocumentRole
→ 여러 공시를 하나의 사건으로 연결했을 때의 의미 역할
→ ORIGINAL, CORRECTION, RESULT, TERMINATION 등
```

예를 들어 정정공시의 본문 XML은 `documentFileRole=MAIN`, `eventDocumentRole=CORRECTION`이 될 수 있다.

### 7.3 Evidence 상태

초기 Evidence 상태는 다음 두 단계로 구분한다.

| 상태 | 의미 |
|---|---|
| `CANDIDATE` | 검색 또는 추출기가 찾았지만 아직 Fact 검증에 사용하지 않은 근거 후보 |
| `VERIFIED` | Fact 검증 과정에서 실제 원문과 위치를 확인한 근거 |

검색 결과를 반환할 때 모든 출처를 `VERIFIED` Evidence로 자동 승격하지 않는다.

## 8. Fact 논리 계약

### 8.1 Fact Key 원칙

실제 `factKey`는 금융적으로 하나의 의미를 갖는 ID다.

```text
facility.amount
```

다음은 별도 Fact Key가 아니라 같은 Fact envelope의 속성이다.

```text
facility.amount.raw_text
facility.amount.normalized_value_krw
```

즉 투자금액은 한 Fact 안에 원문값과 정규화값을 함께 보존한다.

### 8.2 `DisclosureFact`

| 필드 | 타입 | 필수 | 의미 |
|---|---|---:|---|
| `factId` | `UUID` | 저장 시 | Fact 식별자 |
| `disclosureId` | `UUID` | 필수 | Fact가 나온 공시 |
| `disclosureDocumentId` | `UUID` | 필수 | Fact가 나온 실제 문서 |
| `factKey` | `String` | 필수 | 금융 문서가 승인한 의미 단위 ID |
| `valueType` | `FactValueType` | 필수 | 값 자료형 |
| `rawValue` | `String` | 직접·텍스트 추출 Fact | 원문 표시값 |
| `rawUnit` | `String` | 수치 Fact | 원문 단위 |
| `normalizedValue` | `FactValue` | 정규화 가능 시 | 타입이 보존된 표준값 |
| `normalizedUnit` | `String` | 정규화 가능 시 | `KRW/SHARE/PERCENT` 등 |
| `currency` | `String` | 금액 Fact | 원문 통화 코드 |
| `periodStart` | `LocalDate` | 기간형 Fact | 측정 기간 시작 |
| `periodEnd` | `LocalDate` | 기간형 Fact | 측정 기간 종료 |
| `asOfDate` | `LocalDate` | 시점형 Fact | 값이 유효한 기준일 |
| `accountingBasis` | `AccountingBasis` | 재무 Fact | 연결·별도·기타 기준 |
| `generationMethod` | `FactGenerationMethod` | 필수 | Fact 생성 방식 |
| `availabilityStatus` | `FactAvailabilityStatus` | 필수 | 값의 존재·결측 상태 |
| `normalizationStatus` | `FactNormalizationStatus` | 필수 | 정규화 결과 상태 |
| `validationStatus` | `FactValidationStatus` | 필수 | 검증 상태 |
| `sourceReceiptNo` | `String` | 필수 | 값을 제공한 접수번호 |
| `policyVersion` | `String` | 정규화·분류·계산 Fact | 적용 정책 버전 |
| `evidenceIds` | `List<UUID>` | 직접·정규화·분류 Fact | 연결 Evidence; 1개 이상 |

`normalizedValue`를 Java `Object`나 임의 문자열 하나로 구현하지 않는다. 값 타입별 모델 또는 명확한 직렬화 계약을 사용한다.

### 8.3 `FactValueType`

| 값 | Java 표현 예 | 사용 예 |
|---|---|---|
| `TEXT` | `String` | 투자목적 |
| `DECIMAL` | `BigDecimal` | 금액·비율·환율 |
| `INTEGER` | `Long` 또는 `BigInteger` | 주식 수·인원 수 |
| `DATE` | `LocalDate` | 결정일·시작일·종료일 |
| `BOOLEAN` | `Boolean` | VAT 포함 여부가 명시적으로 확정된 경우 |
| `CODE` | 승인 enum 또는 코드 문자열 | 투자유형·사건상태 |
| `LIST` | 타입이 정의된 항목 목록 | 특별관계자·지급단계 |

시설투자 첫 수직 구현은 `TEXT`, `DECIMAL`, `DATE`, `CODE`를 우선 지원한다.

### 8.4 Fact 공통 enum

`finance_domain/00.공통규격.md`를 기준으로 다음 enum을 사용한다.

`FactGenerationMethod`:

- `SOURCE_METADATA`
- `DIRECT_RAW`
- `TEXT_EXTRACTED`
- `DIRECT_NORMALIZED`
- `DERIVED_CLASSIFICATION`
- `DERIVED_CALCULATION`
- `LINKED_RESOLVED`
- `SYSTEM_ASSIGNED`

`FactAvailabilityStatus`:

- `AVAILABLE`
- `NOT_STATED`
- `WITHHELD`
- `NOT_APPLICABLE`
- `AMBIGUOUS`
- `PARSE_FAILED`

`FactNormalizationStatus`:

- `MAPPED`
- `UNMAPPED`
- `AMBIGUOUS`
- `REVIEW_REQUIRED`
- `NOT_APPLICABLE`
- `MISSING`

`FactValidationStatus`:

- `UNVALIDATED`
- `VERIFIED`
- `REJECTED`

그 밖의 공통 enum:

`EvidenceStatus`:

- `CANDIDATE`
- `VERIFIED`

`EvidenceBlockType`:

- `DOCUMENT_METADATA`
- `SECTION`
- `TITLE`
- `HEADING`
- `PARAGRAPH`
- `TABLE`
- `TABLE_ROW`
- `TABLE_CELL`
- `NOTE`

`AccountingBasis`:

- `CONSOLIDATED`
- `SEPARATE`
- `OTHER`
- `UNKNOWN`

`EventDocumentRole`:

- `ORIGINAL`
- `CORRECTION`
- `PROGRESS`
- `COMPLETION`
- `TERMINATION`
- `RESULT`
- `UNKNOWN`

`FactQueryMode`:

- `AS_FILED`
- `LATEST_AS_OF`
- `FULL_HISTORY`

### 8.5 Fact 불변조건

1. `factKey`, `valueType`, `generationMethod`, `availabilityStatus`, `validationStatus`, `sourceReceiptNo`는 필수다.
2. 직접·정규화·분류 Fact가 `AVAILABLE`이면 Evidence가 1개 이상이어야 한다.
3. `VERIFIED` Fact는 `normalizationStatus=AMBIGUOUS/REVIEW_REQUIRED`일 수 없다.
4. 금액 Fact는 통화와 단위를 확인하지 않으면 계산 입력으로 사용할 수 없다.
5. 기간 종료일은 시작일보다 앞설 수 없다.
6. `NOT_APPLICABLE`, `NOT_STATED`, `WITHHELD`를 숫자 0으로 저장하지 않는다.
7. `PARSE_FAILED`를 원문 미기재로 처리하지 않는다.
8. Evidence와 Fact는 같은 공시·문서 관계를 만족해야 한다.
9. 원문값은 정규화값으로 덮어쓰지 않는다.
10. 계산에는 `validationStatus=VERIFIED`인 입력 Fact만 사용한다.

## 9. Fact와 Evidence 관계

하나의 Fact에는 여러 Evidence가 필요할 수 있다.

```text
facility.amount
├─ Evidence 1: 투자금액 값 셀
├─ Evidence 2: 표 상단 단위 “백만원”
├─ Evidence 3: VAT 제외 주석
└─ Evidence 4: 회사 부담분이라는 기타사항
```

하나의 Evidence가 여러 Fact를 지원할 수도 있다. 따라서 논리적으로는 다대다 관계다.

향후 저장 후보:

```text
disclosure_facts
disclosure_evidences
disclosure_fact_evidences
```

테이블명과 컬럼은 대표 문서 수직 검증 후 Flyway에서 확정한다.

## 10. 시설투자 첫 수직 구현 계약

첫 구현에서는 다음 핵심 Fact를 지원한다.

| Fact Key | 타입 | 주요 Evidence |
|---|---|---|
| `facility.target` | `TEXT` | 투자대상 행·기타사항 |
| `facility.amount` | `DECIMAL` | 투자금액 값·단위·금액 주석 |
| `facility.equity_amount` | `DECIMAL` | 자기자본 값·단위·회계기준 주석 |
| `facility.disclosed_equity_ratio` | `DECIMAL` | 자기자본대비 비율 행 |
| `facility.purpose` | `TEXT` | 투자목적 행·문장 |
| `facility.start_date` | `DATE` | 투자기간 시작일과 의미 |
| `facility.end_date` | `DATE` | 투자기간 종료일과 의미 |
| `facility.decision_date` | `DATE` | 이사회결의일·결정일 |

대표 골든 후보:

| 항목 | 값 |
|---|---|
| 기업 | SK하이닉스 |
| 접수번호 | `20240424800596` |
| 골든 ID | `GOLD-FACILITY-001` |
| 현재 상태 | `C_REVIEW_PENDING` |

검색 단계의 첫 목표는 다음과 같다.

```text
FAC-Q02: “투자금액과 목적은 무엇이야?”
→ 접수번호 20240424800596 공시 선택
→ 투자금액과 투자목적이 포함된 청크가 top 10에 포함
→ 각 청크에서 ContentBlock과 TABLE 행 범위로 역추적 가능
```

Fact 단계의 첫 목표는 C가 승인한 Evidence를 사용해 `facility.amount`와 `facility.purpose`를 `VERIFIED` 상태로 생성하는 것이다.

## 11. 저장 정책

### 11.1 지금 저장하는 것

- 원본 공시와 문서
- Section과 ContentBlock
- TABLE JSONB
- 검색 청크
- 청크 원문 출처 참조

### 11.2 지금 저장하지 않는 것

- 일반 검색 요청·검색 결과
- 검색 점수와 matched terms
- Evidence 후보
- 미확정 Fact 후보

검색 품질 평가가 필요하면 테스트 fixture 또는 평가 결과 파일로 먼저 기록한다.

### 11.3 다음 단계에서 저장할 것

- C가 승인한 Fact 구조를 만족하는 추출 결과
- Fact별 정확한 Evidence
- Fact와 Evidence 연결
- 추출기·정규화 정책 버전
- 정정·기준시점 Fact 버전

## 12. 패키지 권장 구조

현재 검색 구현은 다음 구조로 시작한다.

```text
disclosure
├─ repository
│  └─ 검색 전용 Repository 또는 Custom Repository
├─ service
│  └─ DisclosureChunkSearchService
└─ infrastructure
   └─ search
      ├─ DisclosureMetadataSearchCondition
      ├─ DisclosureMetadataSearchHit
      ├─ DisclosureMetadataSearchResult
      ├─ DisclosureChunkSearchCondition
      ├─ DisclosureChunkSearchHit
      ├─ DisclosureChunkSearchResult
      ├─ DisclosureChunkSourceReference
      └─ SearchScoreBreakdown
```

Fact 수직 구현을 시작할 때 별도 패키지를 추가한다.

```text
disclosure
├─ domain
│  └─ fact
│     ├─ DisclosureFact
│     ├─ DisclosureEvidence
│     └─ 공통 enum·값 모델
└─ infrastructure
   └─ extraction
      └─ facility
```

검색 결과 모델은 JPA Entity로 만들지 않는다. Repository projection 또는 조회 DTO를 서비스 결과 모델로 매핑한다.

## 13. 구현 순서

1. 검색 공통 enum과 입력값 상한을 정의한다.
2. `DisclosureMetadataSearchCondition/Hit/Result`를 구현한다.
3. 기업·기간·공시 그룹·보고서명 기반 공시 검색을 구현한다.
4. `DisclosureChunkSearchCondition/Hit/Result`와 `SourceReference`를 구현한다.
5. 선택된 공시의 TEXT·TABLE 청크 검색을 구현한다.
6. `FAC-Q01~FAC-Q05` 검색 fixture를 만든다.
7. `GOLD-FACILITY-001`의 정답 청크가 top 10에 포함되는지 확인한다.
8. 정답 청크에서 ContentBlock과 TABLE 행까지 역추적한다.
9. 실제 결과를 바탕으로 Evidence 모델의 필수 필드를 확정한다.
10. 시설투자 핵심 Fact 중 `facility.amount`, `facility.purpose`를 수직 구현한다.
11. C 검수 후 Fact·Evidence Entity와 Flyway를 확정한다.

## 14. 역할 분담과 승인 경계

### B가 구현·확인할 것

- 검색 조건과 결과 Java 모델
- 메타데이터·청크 검색 쿼리
- 검색 점수와 순위
- 청크에서 ContentBlock·TABLE 행으로의 역추적
- 원문 레이블 후보와 미매핑 사례 수집
- Fact raw·normalized·Evidence 생성
- 검색·추출·정규화 테스트

### C가 승인할 것

- Fact의 금융적 의미
- 서로 같은 의미로 취급 가능한 원문 레이블
- 금액·기간·회계기준·VAT 등 비교 가능 조건
- 골든 후보의 기대값과 원문 근거
- 계산식과 답변 표현 한계
- `PARTIAL/UNANSWERABLE` 금융 판정 기준

C가 청크 UUID, Java 클래스명, DB 컬럼명과 검색 SQL을 정할 필요는 없다.

## 15. 미확정 사항

다음 항목은 검색 결과를 실제로 확인한 후 결정한다.

1. PostgreSQL 검색 방식과 인덱스: lexical baseline 후 확정
2. 한국어 동의어·레이블 매핑 저장 방식
3. 검색 점수 상세 구성과 가중치
4. 앞뒤 청크 확장 기본값
5. Evidence와 Fact의 최종 JPA·Flyway 구조
6. 반복 Fact와 LIST 값의 저장 방식
7. Fact 정정 버전과 사건 Manifest의 최종 테이블 구조
8. `GOLD-FACILITY-001`의 C 최종 승인

임베딩과 Vector DB는 lexical 검색의 top K 품질과 대회 허용 정책을 확인한 뒤 결정한다.

## 16. 완료 기준

### 검색 계약 완료

- [ ] 메타데이터 검색과 청크 검색의 입력·출력이 분리됐다.
- [ ] 검색 결과에서 공시·문서·청크·ContentBlock을 식별할 수 있다.
- [ ] TABLE 중첩 경로와 행 범위를 반환할 수 있다.
- [ ] 검색 점수와 Fact 검증 상태가 구분된다.
- [ ] 정정공시를 기본적으로 검색 대상에서 제거하지 않는다.
- [ ] 결과 없음이 빈 결과로 표현된다.
- [ ] 검색 정책 버전을 반환한다.

### 첫 검색 수직 구현 완료

- [ ] 기업·기간·공시 유형으로 후보 공시를 제한한다.
- [ ] 선택된 공시의 TEXT·TABLE 청크를 검색한다.
- [ ] `GOLD-FACILITY-001`의 정답 근거가 top 10에 포함된다.
- [ ] 검색 적중에서 원본 TABLE 행까지 역추적된다.
- [ ] 검색 결과가 없을 때 외부 데이터로 우회하지 않는다.

### Fact·Evidence 다음 단계 진입 조건

- [ ] C가 시설투자 핵심 Fact와 골든 근거를 승인했다.
- [ ] `facility.amount`의 값·단위·주석 위치가 재현된다.
- [ ] `facility.purpose`의 원문 문장이 재현된다.
- [ ] Evidence 후보와 VERIFIED Evidence를 구분할 수 있다.
- [ ] Fact의 raw·normalized 값을 같은 envelope에서 표현할 수 있다.
