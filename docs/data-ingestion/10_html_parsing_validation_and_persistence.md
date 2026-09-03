# 거래소 HTML 파싱·검증·DB 적재

## 범위

- 첫 검증 대상: manifest의 exchange / 신규시설투자등 43건.
- 기존 DART XML 파싱 결과는 그대로 유지한다.
- HTML 파서는 거래소 XForms를 지원한다. periodic의 PDF/HTML 뷰어, PDF, UNKNOWN은 라우터에서 거절한다.
- 이 문서는 구현과 실행 절차를 설명한다. 실제 운영 DB 적재 완료 보고서는 아니다.

## 흐름

원문 경로·크기·SHA-256 검증
→ HtmlSourceReader(UTF-8 우선, 실패 시 MS949)
→ DartHtmlDisclosureParser(Jsoup 위치 추적)
→ ParsedDisclosureDocument
→ HtmlParsingValidator(기존 구조 조사기와 표·행·셀 개수 대조)
→ DisclosureParsingPersistenceService(문서별 트랜잭션)

- HtmlTextExtractor: 줄바꿈 보존, 숨김 텍스트 제외.
- HtmlTableParser: 물리 행·셀, span, 중첩 표와 이미지 메타데이터 보존.
- 정정신고(보고)와 신규 시설투자 등 본문을 서로 다른 섹션에 저장한다.
- 정정표의 정정전/정정후 값 모두 원문 그대로 보존하며 최신 Fact를 결정하지 않는다.
- 표는 기존 disclosure_content_blocks.structured_content의 TABLE schemaVersion 2를 재사용한다.
- 링크는 V9의 disclosure_documents.related_disclosure_links에 schemaVersion 1 JSONB로 저장한다.
- KRX acptno/rcpno와 DART rcpNo는 별도 필드다. KRX 번호를 DART 접수번호로 추정하지 않는다.
- 링크는 관계 후보 정보일 뿐이다. 링크를 다운로드하거나 외부 네트워크로 확인하지 않는다.

## 기존 XML과 연결

DisclosureDocumentParserRouter가 실제 content_format 및 source_group/document_role을 확인한다.
DisclosureDocumentParsingService가 선택된 파서 이름과 버전을 저장한다.

- DART_XML: DartXmlDisclosureParser / 1.1.0
- 거래소 HTML: DartHtmlDisclosureParser / 1.1.0

기존 XML Runner 설정은 유지한다. 새 HTML Runner는 별도 opt-in이다.

## 1단계: DB를 변경하지 않는 43건 검증

실제 .env는 자동 변경하지 않았다. 아래를 추가/변경한다.

```dotenv
FOLIOLENS_VALIDATE_HTML_PARSING_ON_STARTUP=true
FOLIOLENS_VALIDATE_HTML_PARSING_PAGE=0
FOLIOLENS_VALIDATE_HTML_PARSING_LIMIT=50
FOLIOLENS_VALIDATE_HTML_PARSING_EXPECTED_COUNT=43
FOLIOLENS_VALIDATE_HTML_PARSING_RAW_SUBTYPE=신규시설투자등
FOLIOLENS_VALIDATE_HTML_PARSING_REPORT_FILE_NAME=html-parsing-validation.csv
FOLIOLENS_PERSIST_HTML_PARSING_ON_STARTUP=false
```

기존 import, profiling, XML validation/persistence, chunking ON_STARTUP은 모두 false로 둔다.

```powershell
docker compose up -d --build app
docker compose logs -f app
```

CSV: 기본 reports/html-parsing-validation.csv.
UTF-8 BOM과 명시적인 빈 필드를 사용한다.
총 대상 43, 검사 43, 실패 0이어야 전체 검증으로 인정한다.
expected-count=0은 개별 페이지 조사 시 전체 개수 확인을 생략하는 옵션이다.

검증기 자체는 DB에서 읽기만 한다. 다만 애플리케이션 기동 시 Flyway V9는 적용된다.
V9는 링크 컬럼만 추가하며 기존 XML 본문을 다시 만들지 않는다.

## 2단계: PENDING HTML 적재

```dotenv
FOLIOLENS_VALIDATE_HTML_PARSING_ON_STARTUP=false
FOLIOLENS_PERSIST_HTML_PARSING_ON_STARTUP=true
FOLIOLENS_PERSIST_HTML_PARSING_BATCH_SIZE=10
FOLIOLENS_PERSIST_HTML_PARSING_MAX_DOCUMENTS=43
FOLIOLENS_PERSIST_HTML_PARSING_MAX_FAILURES=5
FOLIOLENS_PERSIST_HTML_PARSING_RAW_SUBTYPE=신규시설투자등
```

같은 compose 명령으로 재생성한다.
이 설정은 테스트 DB가 아니라 compose의 실제 DB에 저장한다.

- 항상 PENDING 첫 페이지를 조회한다. 페이지 번호를 증가시키지 않는다.
- 각 파일은 저장 전에도 파싱 구조 검증을 수행한다.
- max-documents는 이번 실행의 최대 처리 수이며, 실패도 처리 수에 포함된다.
- 실패 한도 초과를 막기 위해 실제 배치 요청 수는 남은 실패 예산 이하로 제한될 수 있다.
- 완료 후 ON_STARTUP을 false로 되돌린다.
- FAILED는 자동 재시도하지 않는다. 원인을 해결하고 해당 문서만 재시도 대상으로 지정한다.
- 기존 성공 문서의 재파싱 시 그 문서의 청크·출처 → 블록 → 섹션 순으로 교체한다.
- 교체 실패 시 삭제와 저장은 모두 롤백된다. 실패 상태 기록은 별도 트랜잭션이다.
- 파싱 완료 후 chunk_status는 PENDING이다. 이번 변경은 HTML 전체 청킹 Runner 확장을 포함하지 않는다.

## 결과 확인 SQL

```sql
SELECT dd.parse_status, dd.parser_name, dd.parser_version, COUNT(*)
FROM disclosure_documents dd
JOIN disclosures d ON d.id = dd.disclosure_id
WHERE dd.content_format = 'HTML'
  AND d.source_group = 'exchange'
  AND d.raw_subtype = '신규시설투자등'
GROUP BY dd.parse_status, dd.parser_name, dd.parser_version;

SELECT dd.file_name,
       jsonb_array_length(dd.related_disclosure_links->'links') AS link_count
FROM disclosure_documents dd
JOIN disclosures d ON d.id = dd.disclosure_id
WHERE dd.content_format = 'HTML'
  AND d.raw_subtype = '신규시설투자등';
```

링크가 없는 원문은 link_count=0이 정상이다.

## 테스트

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat test --tests "*HtmlFacilityCorpusTest" "-PcontestDatasetRoot=C:/study/portfolio-disclosure-guardian/foliolens-data"
```

- 축약 원문 fixture: 원공시와 정정 시설투자.
- fixture 테스트: 섹션 분리, 정정 전/후 값, span, 줄바꿈, KRX/DART 식별자 분리.
- 실제 43건 테스트: 원본 파일 읽기만 수행하고 표/행/셀 누락을 비교한다.
- Testcontainers 통합 테스트: 임시 PostgreSQL에서 V9, JSONB, 멱등 교체, 청크 제거, 롤백 및 실패 상태를 검증한다.
- Docker가 없으면 Testcontainers 테스트는 skip되므로 전체 성공 건수와 skip을 구분한다.

## 실제 원문 읽기 검증 결과 (2026-09-02)

- 43건 모두 구조 조사기와 파싱 결과의 표/행/셀 개수가 일치했다.
- 일반 28건, 정정 15건.
- 표 88개, 행 845개, 셀 1,873개, 관련공시 링크 18개.
- 이 검증은 파일을 읽기만 했으며 운영 DB 적재 완료를 뜻하지 않는다.

## 남은 범위

- HTML 청킹의 실 DB 소규모 검증 및 검색·Fact 추출 end-to-end 검증. 청킹 배치 대상 조회 확장은 아래 2026-09-03 절에 반영했다.
- 정정 링크와 기업·일자·서류명을 대조해 실제 사건 관계를 확정하는 이력 서비스.
- 시설투자 외 거래소 HTML 유형 검증, PDF/뷰어 별도 지원.
- HTML 중첩 표의 parentContext는 현재 별도로 생성하지 않는다. 43건에는 중첩 표가 없으며, 중첩 표가 있는 HTML로 확대하기 전 정책 보완이 필요하다.

## 2026-09-03: 계약체결 일반 span 제목 보완

- 기존 `.xforms_title`, `h1`~`h6`, 정정 제목 인식에 일반 `span` 제목을 추가했다.
- 일반 `span`은 `.xforms` 내부·표 바깥에 있어야 한다. 최대 120자의 단일 행 텍스트, 굵게(`bold` 또는 700~900)·가운데 정렬, 다음 표시 요소가 `TABLE`인 조건을 모두 요구한다. 숨김 형제 요소는 건너뛴다.
- 문서명은 같은 규칙으로 생성된 첫 본문 섹션 제목을 사용한다. 정정 제목은 문서명으로 선택하지 않는다.
- 정정 제목만 있는 문서는 실패 처리한다. 정정 섹션 뒤에 본문 섹션이 있어야 하고 본문 섹션에는 표가 있어야 한다.
- 기존 표·중첩 표·행·셀 개수 대조는 유지한다. 추가 검증 실패 메시지는 누락 항목과 sections/preambleBlocks/tables/rows/cells/textCharacters를 구분한다.
- HTML 파서 버전은 `1.1.0`이다. 이 변경만으로 기존 DB가 자동 재파싱되지는 않는다.
- 축약 계약 fixture 2개와 50건의 접수번호 목록을 회귀 테스트에 추가했다. 실제 원문 테스트는 제공 데이터 디렉터리를 읽기만 하며 DB·원문·기존 CSV를 변경하지 않는다.

```powershell
cd backend
.\gradlew.bat test --tests "*DartHtmlDisclosureParserTest" --tests "*HtmlParsingValidatorTest" --tests "*HtmlFacilityCorpusTest" --tests "*HtmlContractCorpusTest" "-PcontestDatasetRoot=C:/study/portfolio-disclosure-guardian/foliolens-data"
```

계약체결 page 0의 동일 50건을 Runner로 다시 검사할 때는 기존 검증용 `.env`를 유지하고 `docker compose up -d --build app`으로 변경된 코드를 빌드한다. HTML 적재 설정은 검증 완료 전까지 `false`로 유지한다.

## 2026-09-03: HTML 소규모 청킹 실행

### 변경 범위

- 기존 청킹 배치에 `content-format`, 선택적인 `raw-subtype` 필터를 추가했다. 기본값은 `DART_XML`, 전체 유형으로 기존 XML 대상 선택을 유지한다.
- HTML은 거래소(`exchange`) 공시만 선택한다. 파싱 `COMPLETED` + 청킹 `PENDING` 문서를 ID 순서로 처리하고, 다음 배치도 항상 첫 페이지를 조회한다. 완료·실패 문서는 자동 재처리하지 않는다.
- 저장된 `disclosure_sections`, `disclosure_content_blocks`를 기존 TEXT/TABLE 생성기로 읽는다. 원문 재파싱, 새 Flyway, 기존 청크 삭제·상태 초기화는 필요 없다.
- 공통 생성기는 `DisclosureChunkGenerator`이다. 최초 공통 버전은 `disclosure-chunk-v1`이고, 아래 기호 전용 청크 제외 규칙부터 `disclosure-chunk-v2`를 사용한다. XML v3의 분할 크기는 유지한다. 앞으로 생성하는 XML/HTML 모두 공통 정책을 사용하며, 이미 저장된 문서의 버전/청크는 자동 변경하지 않는다.
- 생성기, 저장 서비스, 실패 기록기가 같은 정책 Bean을 사용한다.

### 먼저 시설투자 5건

아래는 실제 DB에 저장하는 실행 설정이다. 이번 코드 수정에서 사용자 `.env`나 운영 DB는 변경하지 않았다. 다른 적재·구조 조사·검증·특정 문서 청킹 Runner는 끈다.

```dotenv
FOLIOLENS_VALIDATE_HTML_PARSING_ON_STARTUP=false
FOLIOLENS_PERSIST_HTML_PARSING_ON_STARTUP=false
FOLIOLENS_PERSIST_XML_PARSING_ON_STARTUP=false

FOLIOLENS_CHUNKING_ON_STARTUP=true
FOLIOLENS_CHUNKING_CONTENT_FORMAT=HTML
FOLIOLENS_CHUNKING_RAW_SUBTYPE=신규시설투자등
FOLIOLENS_CHUNKING_BATCH_SIZE=1
FOLIOLENS_CHUNKING_MAX_DOCUMENTS=5
FOLIOLENS_CHUNKING_MAX_FAILURES=1
```

한 건씩 최대 5건을 처리한다. 실패 임계치는 배치가 끝날 때 확인하므로 초기 시험에서는 `batch-size=1`로 두면 첫 실패 후 중단한다. 일반·정정 구분으로 추출하는 설정은 아니며, 실 DB 대상은 ID 순서다. 두 형태의 대표 fixture는 자동 테스트에서 따로 검증한다.

저장소 루트에서:

```powershell
docker compose up -d --build --force-recreate app
docker compose logs -f app
```

- 시작 로그에서 `contentFormat=HTML, rawSubtype=신규시설투자등`을 확인한다.
- 적격 문서가 5건 이상이고 실패가 없다면 종료 로그는 `totalCount=5, successCount=5`이다.
- 결과는 CSV가 아니라 `disclosure_chunks`, `disclosure_chunk_sources`에 저장된다.
- 실행 완료 후 `FOLIOLENS_CHUNKING_ON_STARTUP=false`로 되돌린다. `.env` 변경은 컨테이너 재생성 시 적용된다. 켜둔 채 재생성하면 다음 PENDING 문서를 처리한다.

### DB 확인

```sql
SELECT d.raw_subtype, dd.parse_status, dd.chunk_status, COUNT(*)
FROM disclosure_documents dd
JOIN disclosures d ON d.id = dd.disclosure_id
WHERE dd.content_format = 'HTML' AND d.source_group = 'exchange'
GROUP BY d.raw_subtype, dd.parse_status, dd.chunk_status
ORDER BY d.raw_subtype, dd.chunk_status;

SELECT dd.id, dd.chunk_generator_name, dd.chunk_generator_version,
       dd.chunk_error_message, dd.chunked_at, COUNT(c.id) AS chunk_count
FROM disclosure_documents dd
JOIN disclosures d ON d.id = dd.disclosure_id
LEFT JOIN disclosure_chunks c ON c.disclosure_document_id = dd.id
WHERE dd.content_format = 'HTML' AND d.source_group = 'exchange'
  AND dd.chunk_status <> 'PENDING'
GROUP BY dd.id
ORDER BY dd.chunked_at DESC;
```

확인할 사항: 비어 있지 않은 청크, 금액·단위·항목명 보존, 정정/본문의 섹션 경계, 긴 셀의 분할, 원본 블록 및 표 행 범위 출처. `COMPLETED`만으로 답변 검색 품질까지 검증된 것은 아니다.

### 다음 유형과 전체 확대

시설투자 5건 확인 후 같은 유형의 나머지를 처리한다. 계약체결·계약해지·주요경영사항은 `FOLIOLENS_CHUNKING_RAW_SUBTYPE`을 해당 원문 유형명으로 바꿔 각각 5건부터 확인한다. 마지막에 모든 유형을 처리하려면 이 값을 빈 문자열로 둔다. `max-documents=0`은 무제한이므로 초기 검증에서는 사용하지 않는다.

### 자동 테스트

- `HtmlDisclosureChunkingTest`: 대표 HTML fixture 6종, 정정/본문 분리, 금액·단위·문단, 긴 셀의 내용 보존, 표 행/블록 출처, JSONB→청킹→엔티티 매핑.
- `DisclosureParsingPersistenceIntegrationTest`: 별도 PostgreSQL에서 대표 HTML 6종의 파싱→청킹 배치→청크·출처 저장→완료 상태와 재조회 제외, 형식·유형·상태·뷰어 필터.
- 기존 청킹 Service/Runner 회귀 테스트: 기본 XML, HTML 선택, 최대 처리 수, 실패 임계치, 잘못된 형식, opt-in 설정.
- 데이터셋 옵션을 지정하면 유형별 실제 원문 5건씩(총 20건)을 메모리에서만 청킹 검증한다. 이는 운영 DB 적재가 아니다.

```powershell
cd backend
.\gradlew.bat test --tests "*HtmlDisclosureChunkingTest" --tests "*DisclosureChunkingBatch*Test" --tests "*DisclosureParsingPersistenceIntegrationTest" "-PcontestDatasetRoot=C:/study/portfolio-disclosure-guardian/foliolens-data"
```

확인 결과(2026-09-03): 데이터셋 옵션을 지정한 전체 `test` 실행에서 283개 테스트가 모두 통과했다(실패 0, 오류 0, 건너뜀 0). 대표 원문 20건의 메모리 청킹 결과는 시설투자 11개, 계약체결 11개, 계약해지 5개, 주요경영사항 12개 청크다. 대표 fixture 6종의 청크·출처 DB 저장은 Testcontainers의 독립 PostgreSQL에서 검증했으며 운영 DB 적재 결과가 아니다.

### 공통 v2: 기호 전용 청크 제외

- 적용 위치: `DisclosureChunkGenerator`에서 TEXT/TABLE draft 수집 후, 최종 청크 순번을 부여하기 전.
- 판단 대상: `bodyText` 전체. `searchText`의 섹션명이나 머리글은 판단에 사용하지 않는다.
- Unicode 문자(`\p{L}`) 또는 숫자(`\p{N}`)가 하나도 없으면 제외한다. `-`, `—`, `…`, `| / · %`처럼 기호·공백뿐인 청크가 대상이다.
- `0`, `-1,000`, `0.00%`, `N/A`, `해당 없음`, `①`, `Ⅳ`, `매출 | -`는 보존한다.
- 일반 표 안의 `-` 셀이나 본문 일부를 제거하지 않는다. 유지하는 청크의 본문·검색문·출처는 그대로 두고, 최종 청크 순번만 1부터 빈 번호 없이 부여한다.
- 파싱된 섹션·블록·TABLE JSONB는 삭제하거나 수정하지 않는다. 기호 전용 표 블록은 원문 근거로 남지만 해당 청크·출처는 생성하지 않는다. 따라서 출처 검증에서 이 의도적 제외를 표 행 누락 오류로 계산하지 않는다.
- 모든 청크가 제외되면 기존 저장 서비스의 빈 결과 처리에 따라 청크 0개로 `COMPLETED`가 된다. 원본 파싱 결과는 남는다.
- 새 생성기 버전은 `disclosure-chunk-v2`이며, 파싱 버전은 바꾸지 않는다.
- 기존 v1 청크는 자동 삭제되지 않는다. 이미 청킹 완료된 문서는 일반 PENDING 배치에서 제외되므로, 기존 결과에도 적용하려면 별도로 해당 문서를 재청킹해야 한다. 재파싱은 필요 없다.

v2 검증 결과: 전체 311개 테스트 통과(실패·오류·건너뜀 0). 직전 실 DB 소규모 적재에 사용한 시설투자 5건의 원문을 메모리에서 재검증했을 때 파싱 블록 14개는 유지됐고, 청크는 14개에서 11개로 줄었다. 기호 전용 3개만 제외됐으며 문자·숫자를 포함하는 셀 텍스트와 남은 표 행 출처는 보존됐다. 실제 운영 DB의 기존 14개 청크는 변경하지 않았다.
