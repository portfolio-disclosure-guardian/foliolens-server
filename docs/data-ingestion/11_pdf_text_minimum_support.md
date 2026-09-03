# PDF 최소 텍스트 파싱·청킹 지원

- 작성일: 2026-09-04
- 범위: 대회 제공 정기공시 PDF 3건의 페이지별 텍스트 검색
- 상태: 구현·전체 테스트 완료. 이 문서 작성 시 실제 DB 적재는 실행하지 않음
- 파서: `PdfTextDisclosureParser` / `1.0.0`
- 청킹: 기존 `DisclosureChunkGenerator` / `disclosure-chunk-v2`

## 1. 지원 수준과 제외 범위

PDF의 텍스트 레이어를 읽는다. OCR, 표의 행·셀·병합 복원, 장·절 계층 복원,
표의 숫자·항목·회계기간 연결 검증은 하지 않는다.
추출 문장은 검색 후보이며 검증된 수치 Fact가 아니다.

따라서 정상 적재 시에도 `parse_status=PARTIAL`로 저장하고,
텍스트 청킹이 끝나면 `chunk_status=COMPLETED`로 표시한다.
이 상태 조합은 실패가 아니라 **최소 텍스트 지원 완료**를 뜻한다.
일반 XML/HTML의 PARTIAL 문서를 청킹하도록 범위를 넓히지는 않았다.

함께 제공된 HTML 3개는 이번 PDF Runner가 처리하지 않는다.

| 접수번호 | 기업 | 함께 제공된 HTML의 실제 상태 | 이번 처리 |
|---|---|---|---|
| 20260619000667 | KB금융 | 본문 없는 뷰어 화면 | PDF 텍스트 사용 |
| 20240514001522 | 한화오션 | 본문 없는 뷰어 화면 | PDF 텍스트 사용 |
| 20260513000860 | 한화에어로스페이스 | 본문·표가 있는 HTML | 이번에는 PDF를 선택, HTML 추가 파싱은 보류 |

HTML 메타데이터나 원본 파일은 삭제하지 않는다. 상태도 가짜 COMPLETED로 바꾸지 않는다.
따라서 최종 집계에 HTML PENDING 3건이 남을 수 있다. 역할 A에게 이 제외 사유를 함께 전달한다.

## 2. 처리 흐름

```text
PdfParsingPersistenceBatchRunner (@Order 7)
  → PDF + parse_status=PENDING 문서를 한 건씩 조회
  → DisclosureSourceFileResolver: 경로·파일 크기·SHA-256 검증
  → DisclosureDocumentParsingService / DisclosureDocumentParserRouter
  → PdfTextDisclosureParser: 페이지별 텍스트·품질 경고 생성
  → ParsedDisclosureEntityMapper / DisclosureParsingPersistenceService
  → 섹션·블록·메타데이터 저장, parse_status=PARTIAL

DisclosureChunkingBatchRunner (@Order 8)
  → PDF_TEXT_ONLY 부분 추출 결과 중 chunk_status=PENDING 조회
  → DisclosureDocumentChunkingService / 기존 TextChunkGenerator
  → DisclosureChunkPersistenceService
  → 청크·출처 저장, chunk_status=COMPLETED
```

각 페이지를 최상위 섹션 하나(`PDF 페이지 N`)로 만든다.
텍스트가 있으면 PARAGRAPH 블록 하나를 생성하고 기존 TEXT 청킹 규칙으로 분할한다.
페이지가 서로 섞이지 않도록 섹션을 경계로 사용한다.
빈 페이지도 섹션과 `noTextPages` 기록을 유지해 뒤쪽 페이지 번호가 밀리지 않게 한다.
목차·쪽번호·반복 머리글 제거는 최소 버전에서 하지 않는다. 중복·노이즈가 남을 수 있다.

## 3. 원문 위치와 품질 기록

Flyway V10은 기존 테이블에 다음 필드만 추가한다. 기존 XML/HTML 재적재는 필요 없다.

| 테이블 | 필드 | 의미 |
|---|---|---|
| disclosure_documents | parse_metadata JSONB | PDF_TEXT_ONLY 모드, 페이지 수, 빈 페이지·의심 페이지, 처리 한계 |
| disclosure_content_blocks | source_page_number Integer | PDF 파일의 물리적 페이지 번호. 1부터 시작 |
| disclosure_content_blocks | text_extraction_suspect Boolean | 추출 텍스트의 품질 의심 여부 |

PDF의 `source_line_start`, `source_line_end`는 모두 `-1`이다.
PDF 페이지 번호를 XML 행 번호 칼럼에 대신 넣지 않는다.
인쇄된 쪽번호는 표지·목차 때문에 파일의 물리적 페이지와 다를 수 있다.

```text
disclosure_chunks
  → disclosure_chunk_sources.content_block_id
  → disclosure_content_blocks.source_page_number
```

검색 응답의 `sources[].sourcePageNumber`, `sources[].textExtractionSuspect`로도 전달한다.
PDF가 검색되면 결과의 `warnings`에 표·수치 관계 미검증 경고를 추가한다.
경고와 페이지 참조는 역할 A가 LLM 입력·답변 근거에 전달해야 한다.
청크 검색 적중을 VERIFIED Fact로 자동 승격하면 안 된다.

품질 휴리스틱:

- 대체문자 `U+FFFD`가 있으면 의심 페이지로 표시.
- 비어 있지 않은 줄이 20개 이상이고, 한 글자짜리 줄이 35% 이상이면 의심 표시.
- 의심 텍스트도 삭제하지 않는다.
- 의심 표시가 없다고 정확한 표나 검증된 숫자라는 뜻은 아니다.

암호화·추출 권한 제한, 텍스트가 전혀 없는 PDF는 실패 처리한다.
보호 한도는 파일 100 MiB, 2,000페이지, 페이지당 500,000자, 문서당 10,000,000자다.
크기 한도 초과를 문자열 잘라내기로 숨기지 않는다.

## 4. 실행 설정

기존 `.env`에서 회사·공시·문서 가져오기, XML/HTML 구조조사·검증·파싱 Runner는 모두 false로 둔다.
특정 문서 청킹 Runner(`foliolens.chunking.target.enabled`)도 꺼 둔다.
아래 설정만 활성화하면 한 번의 서버 시작으로 PDF 파싱 후 청킹까지 진행한다.

```dotenv
FOLIOLENS_PERSIST_PDF_PARSING_ON_STARTUP=true
FOLIOLENS_PERSIST_PDF_PARSING_MAX_DOCUMENTS=3

FOLIOLENS_CHUNKING_ON_STARTUP=true
FOLIOLENS_CHUNKING_CONTENT_FORMAT=PDF
FOLIOLENS_CHUNKING_RAW_SUBTYPE=
FOLIOLENS_CHUNKING_BATCH_SIZE=1
FOLIOLENS_CHUNKING_MAX_DOCUMENTS=3
FOLIOLENS_CHUNKING_MAX_FAILURES=1
```

저장소 루트에서:

```powershell
docker compose up -d --build
docker compose logs -f app
```

실제 Compose DB에 쓰는 명령이다. 기존 데이터를 삭제하거나 볼륨을 초기화하지 않는다.
이미 처리한 PDF는 PENDING 조회에서 제외되므로 재시작 시 자동 재파싱하지 않는다.
PDF 파싱은 첫 실패에서 멈춘다. 뒤의 청킹 Runner는 그때까지 저장된 PDF 텍스트를 처리할 수 있다.
실패 시 무조건 반복 실행하지 말고 원인과 대상 문서를 확인한다.

완료 확인 후 두 ON_STARTUP 값을 false로 되돌린다.
`.env`만 변경해도 이미 실행 중인 컨테이너 환경에는 즉시 반영되지 않으므로
다음 `docker compose up -d`로 설정을 적용한다.

## 5. 실제 DB 적재 후 확인

```sql
SELECT d.receipt_no, dd.file_name, dd.parse_status, dd.parser_name,
       dd.parser_version, dd.chunk_status, dd.chunk_generator_version,
       dd.parse_metadata
FROM disclosure_documents dd
JOIN disclosures d ON d.id = dd.disclosure_id
WHERE dd.content_format = 'PDF'
ORDER BY d.receipt_no;
```

예상: PDF 3건의 파싱 PARTIAL, 청킹 COMPLETED, mode=PDF_TEXT_ONLY.

```sql
SELECT dd.file_name, COUNT(dc.id) AS chunks
FROM disclosure_documents dd
LEFT JOIN disclosure_chunks dc ON dc.disclosure_document_id = dd.id
WHERE dd.content_format = 'PDF'
GROUP BY dd.id, dd.file_name
ORDER BY dd.file_name;

SELECT dd.file_name, COUNT(*) AS blocks,
       COUNT(*) FILTER (WHERE b.source_page_number IS NULL) AS missing_page,
       COUNT(*) FILTER (WHERE b.text_extraction_suspect) AS suspect_blocks
FROM disclosure_content_blocks b
JOIN disclosure_documents dd ON dd.id = b.disclosure_document_id
WHERE dd.content_format = 'PDF'
GROUP BY dd.file_name;

SELECT dd.file_name, c.chunk_sequence_no, c.body_text,
       b.source_page_number, b.text_extraction_suspect
FROM disclosure_chunks c
JOIN disclosure_documents dd ON dd.id = c.disclosure_document_id
JOIN disclosure_chunk_sources s ON s.disclosure_chunk_id = c.id
JOIN disclosure_content_blocks b ON b.id = s.content_block_id
WHERE dd.content_format = 'PDF'
ORDER BY dd.file_name, c.chunk_sequence_no
LIMIT 10;
```

## 6. 검증 결과

2026-09-04: 전체 321개 테스트 성공, 실패 0, 오류 0, 건너뜀 0.
테스트 DB는 별도 Testcontainers PostgreSQL이며 실제 Compose DB를 변경하지 않았다.

- 빈 페이지·숫자 문자열·페이지 경계·원문 행 번호 -1 보존.
- 의심 페이지 경고, 텍스트 없는 파일·손상 파일 실패 처리.
- 기존 XML·HTML 파싱/청킹/검색 회귀 테스트.
- PDF PARTIAL 저장 → 청킹 → 검색 결과의 페이지·한계 경고.
- 일반 PARTIAL 결과는 청킹 차단.
- 재청킹 시 중복 없이 교체, 재파싱 시 구 청크·출처 삭제 및 상태 초기화.

실제 PDF를 읽어 메모리에서 생성한 결과(운영 DB 저장 건수가 아님):

| 파일 | 페이지/블록 | TEXT 청크 | 의심 페이지 수 |
|---|---:|---:|---:|
| 20260619000667.pdf | 1,085 | 1,478 | 8 |
| 20260513000860.pdf | 447 | 566 | 0 |
| 20240514001522.pdf | 252 | 322 | 0 |
| 합계 | 1,784 | 2,366 | 8 |

KB금융 의심 페이지: 288, 318, 539, 542, 553, 555, 556, 616.
이는 자동 품질 경고이며 표의 정확성 검증 결과가 아니다.
모든 생성 블록에 청크 출처가 있고, 모든 청크는 TEXT 상한 2,000자 이내이며,
청크 순번과 페이지별 출처 연결을 검증했다. 실제 숫자 해석의 금융 정확성 검증은 포함하지 않는다.

재현 명령(backend 디렉터리, Java 21):

```powershell
./gradlew.bat test '-PcontestDatasetRoot=C:/study/portfolio-disclosure-guardian/foliolens-data'
```

## 7. 역할 A에게 전달할 때

1. 실제 PDF 적재를 실행하고 위 SQL로 검증한다.
2. 적재 Runner를 끄고 진행 중인 쓰기가 없는 상태에서 DB 스냅샷을 만든다.
3. V10과 PDF 의존성이 포함된 코드 커밋, DB 덤프, 이 처리 한계 문서를 함께 전달한다.
4. PDF 원문 파일은 덤프에 포함되지 않는다. 원문 페이지를 열거나 재적재하려면 동일 데이터셋도 필요하다.
5. 수신 측은 같은 마이그레이션·모델 코드로 복원하고 PDF 검색 시 경고·페이지 참조를 유지한다.

## 8. 의존성

[Apache PDFBox 공식 배포](https://pdfbox.apache.org/download.html)의 3.0.8을 사용한다.
파일 기반 Loader로 읽으며 외부 OpenDART·OCR·LLM·임베딩 호출은 없다.
