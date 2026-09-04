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

- HTML 청킹 배치의 대상 조회 확장 및 검색·Fact 추출 end-to-end 검증.
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
