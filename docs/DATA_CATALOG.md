# FolioLens 데이터 카탈로그

| 항목 | 내용 |
|---|---|
| 문서 버전 | v1.0 |
| 문서 상태 | 수령 데이터 실물 검사 기준 초안 |
| 작성일 | 2026-08-01 |
| 대상 데이터 | DART 공시 코퍼스 |
| 현재 로컬 위치 | `../foliolens-data` |
| 권장 내부 데이터셋 ID | `contest-2026q1-v1` |

## 1. 문서 목적

이 문서는 FolioLens가 제공받은 원본 데이터의 구성, 필드, 파일 형식, 적재 기준과 알려진 예외를 정의한다.

이 문서는 다음 질문에 대한 기준 답변이다.

- 어떤 파일을 기업과 공시의 기준 데이터로 사용하는가?
- 각 필드의 의미와 자료형은 무엇인가?
- 파일별로 어떤 파서를 사용해야 하는가?
- 원본 파일과 DB 데이터를 어떤 키로 연결하는가?
- 결측, 정정공시, 첨부문서와 파싱 실패를 어떻게 처리하는가?
- 적재가 정상적으로 완료됐는지 무엇으로 검증하는가?

관련 문서와의 역할 차이는 다음과 같다.

| 문서 | 역할 |
|---|---|
| `DATA_CATALOG.md` | 제공받은 원본 데이터의 실제 구조와 사용 규칙 |
| `erd/foliolens_schema.sql` | 원본과 가공 결과를 저장할 PostgreSQL 구조 |
| `기능명세서.md` | 적재·파싱·검색·계산의 처리 방식 |
| `API_명세서.md` | 저장·가공된 결과를 외부에 제공하는 계약 |

## 2. 데이터셋 개요

### 2.1 범위

| 항목 | 값 |
|---|---:|
| 대상 기업 | 70개 |
| 시장 | KOSPI 61개, KOSDAQ 9개 |
| 업종 대분류 | 8개 |
| 세부 섹터 | 20개 |
| 비정기공시 접수 기간 | 2023-01-01 ~ 2026-03-31 |
| 정기공시 보고 기간 | FY2023 ~ 2026년 1분기 |
| 수집 문서 | 4,204건 |
| 원문 XML | 4,616개 |
| 전체 파일 | 4,907개 |
| 전체 용량 | 약 5.19 GiB |

정기공시의 `rcept_dt`는 보고 기준기간보다 늦을 수 있다. 예를 들어 2026년 1분기 보고서는 2026년 5월에 접수되며, 정정본은 2026년 6월에 접수될 수 있다. 기간 필터에서는 `rcept_dt`와 `base_year`·`base_month`를 구분해야 한다.

### 2.2 파일 유형별 현황

| 확장자 | 파일 수 | 용량 | 용도 |
|---|---:|---:|---|
| `.xml` | 4,616 | 약 5,290.86 MiB | 공시 원문. 일부는 실제 HTML 구조 |
| `.json` | 280 | 약 6.47 MiB | 기업·공시 그룹별 DART 목록 원본 |
| `.jsonl` | 1 | 약 2.31 MiB | 수집 대상 문서 manifest |
| `.csv` | 1 | 약 0.01 MiB | 기업 마스터 프로그래밍용 |
| `.xlsx` | 1 | 약 0.01 MiB | 기업 마스터 열람용 |
| `.pdf` | 3 | 약 6.59 MiB | XML 미제공 문서 대체 원문 |
| `.html` | 3 | 약 3.79 MiB | XML 미제공 문서 대체 뷰어 |
| `.md` | 2 | 약 0.01 MiB | 제공 데이터 설명과 필터 기준 |

## 3. 디렉터리 구조

```text
foliolens-data/
├─ README.md
├─ data_filter.md
├─ universe.csv
├─ universe.xlsx
├─ manifest.jsonl
└─ raw/
   ├─ periodic/
   │  └─ <법인명>/
   │     ├─ list_A.json
   │     └─ <접수번호>_<annual|half|quarter>_<연도>_<월>/
   │        └─ *.xml 또는 PDF+HTML
   ├─ major/
   │  └─ <법인명>/
   │     ├─ list_B001.json
   │     └─ <접수번호>/*.xml
   ├─ exchange/
   │  └─ <법인명>/
   │     ├─ list_I.json
   │     └─ <접수번호>/*.xml
   └─ holding/
      └─ <법인명>/
         ├─ list_D.json
         └─ <접수번호>/*.xml
```

### 3.1 파일별 사용 우선순위

| 파일 | 역할 | 적재 기준 여부 |
|---|---|---|
| `universe.csv` | 기업 마스터 | 기준 데이터 |
| `universe.xlsx` | 사람이 확인하기 위한 열람용 기업 마스터 | 참고용 |
| `manifest.jsonl` | 실제 수집 대상 공시 4,204건의 메타데이터 | 기준 데이터 |
| `raw/**/list_*.json` | DART 목록 API 조회 결과 원본 | 참고용, 직접 적재 기준으로 사용 금지 |
| `raw/**/*` 원문 | 공시 본문·표·첨부문서 | 파싱 대상 |

`list_*.json`에는 수집 대상이 아닌 공시도 포함되어 있다. 예를 들어 `exchange` 목록 JSON에는 총 10,921건, `holding` 목록 JSON에는 총 10,254건이 있지만 manifest에 선정된 문서는 각각 1,469건과 1,083건이다. 따라서 코퍼스 적재 대상은 반드시 `manifest.jsonl`을 기준으로 한다.

## 4. 기업 마스터: `universe.csv`

### 4.1 기본 구조

- 행 수: 70개
- 컬럼 수: 17개
- 인코딩: UTF-8 BOM
- 식별자 중복: 없음
- `corp_code` 결측: 없음
- `stock_code` 결측: 없음

### 4.2 필드 정의

| 필드 | 권장 타입 | 필수 | 의미 | 적재·검증 규칙 |
|---|---|---:|---|---|
| `corp_code` | String | Y | DART 기업 고유번호 | 8자리 숫자 문자열, 선행 0 보존 |
| `stock_code` | String | Y | 상장 종목코드 | 6자리 숫자 문자열, 선행 0 보존 |
| `corp_name` | String | Y | DART 공식 법인명 | 기업 조인의 기준 이름이지만 기본 식별자는 `corp_code` 사용 |
| `listed_name` | String | Y | 거래소 통용 종목명 | 사용자 검색 별칭으로 활용 |
| `corp_eng_name` | String | Y | 영문 법인명 | 검색·표시용 |
| `market` | Enum/String | Y | `KOSPI` 또는 `KOSDAQ` | 허용값 검증 |
| `industry` | String | Y | 업종 대분류 | 현재 8개 값 |
| `sector_no` | Integer/String | Y | 세부 섹터 번호 | 현재 1~20 |
| `sector` | String | Y | 세부 섹터명 | 현재 20개 값 |
| `listing_date` | LocalDate | Y | 상장일 | `yyyy-MM-dd` 파싱 |
| `fiscal_month` | Integer | Y | 결산월 | 원문 `12월`을 숫자 12로 정규화 |
| `market_cap` | Long/BigDecimal | Y | 시가총액 | 단위 억원, 2026-07-24 기준 스냅샷 |
| `n_periodic` | Integer | Y | 수집 정기공시 수 | 적재 완료 검증용 |
| `n_major` | Integer | Y | 수집 주요사항보고서 수 | 적재 완료 검증용 |
| `n_exchange` | Integer | Y | 수집 거래소공시 수 | 적재 완료 검증용 |
| `n_holding` | Integer | Y | 수집 지분공시 수 | 적재 완료 검증용 |
| `note` | String/null | N | 사명 변경·예외사항 | 빈 문자열은 null로 정규화 가능 |

### 4.3 사용 주의사항

1. `corp_code`와 `stock_code`를 숫자로 읽으면 선행 0이 사라지므로 항상 문자열로 처리한다.
2. 기업 조인과 DB 고유 제약은 `corp_code`를 우선한다.
3. `listed_name`과 `corp_name`이 다를 수 있으므로 둘 다 검색 별칭에 포함한다.
4. `market_cap`은 공시 당시 값이 아니라 2026-07-24 스냅샷이다. 과거 시점 계산의 입력으로 자동 사용하지 않는다.
5. `n_*` 필드는 기업 속성이라기보다 데이터셋 품질 검증용 집계값으로 취급한다.

## 5. 공시 manifest: `manifest.jsonl`

### 5.1 기본 구조

- 형식: JSON Lines
- 한 줄: 공시 문서 한 건
- 행 수: 4,204건
- 인코딩: UTF-8
- `doc_id` 중복: 없음
- `rcept_no` 중복: 없음
- 기업 수: 70개
- 정정공시: 1,004건

### 5.2 예시

```json
{
  "doc_id": "exchange_20240424800596",
  "corp_code": "00164779",
  "corp_name": "SK하이닉스",
  "listed_name": "SK하이닉스",
  "stock_code": "000660",
  "industry": "IT",
  "sector": "반도체·전자부품",
  "doc_group": "exchange",
  "doc_subtype": "신규시설투자등",
  "report_nm": "신규시설투자등",
  "is_correction": false,
  "rcept_no": "20240424800596",
  "rcept_dt": "20240424",
  "flr_nm": "SK하이닉스",
  "base_year": null,
  "base_month": null,
  "file_path": "raw/exchange/SK하이닉스/20240424800596",
  "file_format": "xml",
  "n_files": 1
}
```

### 5.3 필드 정의

| 필드 | 권장 타입 | 필수 | 의미 | 적재·검증 규칙 |
|---|---|---:|---|---|
| `doc_id` | String | Y | 데이터셋 내부 문서 ID | `{doc_group}_{rcept_no}`, 멱등성 키 후보 |
| `corp_code` | String | Y | DART 기업 코드 | `universe.csv`의 기업과 조인 |
| `corp_name` | String | Y | DART 공식 법인명 | 표시·원본 경로 확인용 |
| `listed_name` | String | Y | 거래소 통용 종목명 | 검색 별칭용 |
| `stock_code` | String | Y | 종목코드 | 6자리 문자열 |
| `industry` | String | Y | 업종 대분류 | 기업 마스터 값과 일치 검증 |
| `sector` | String | Y | 세부 섹터 | 기업 마스터 값과 일치 검증 |
| `doc_group` | Enum | Y | 공시 대분류 | `periodic`, `major`, `exchange`, `holding` |
| `doc_subtype` | String/null | 조건부 | 공시 세부 유형 | `major`는 현재 전부 null |
| `report_nm` | String | Y | 원문 보고서명 | 정정 태그와 세부 유형 유도에 사용 |
| `is_correction` | Boolean | Y | 기재정정 공시 여부 | 원공시 연결 자체는 제공하지 않음 |
| `rcept_no` | String | Y | DART 접수번호 | 14자리 숫자 문자열 |
| `rcept_dt` | LocalDate | Y | 접수일 | `yyyyMMdd` 파싱 |
| `flr_nm` | String | Y | 제출인 | 기업명과 다를 수 있음 |
| `base_year` | Integer/null | 조건부 | 정기공시 기준연도 | `periodic`에서만 값 존재 |
| `base_month` | Integer/null | 조건부 | 정기공시 기준월 | `periodic`에서만 3, 6, 9, 12 |
| `file_path` | String | Y | 데이터셋 루트 기준 상대경로 | Unicode 정규화 후 해석 |
| `file_format` | Enum | Y | 원문 제공 형식 | `xml` 또는 `pdf+html` |
| `n_files` | Integer | Y | 문서 폴더 내부 파일 수 | 실제 파일 수와 일치 검증 |

## 6. 공시 분류와 건수

| `doc_group` | 의미 | 문서 수 | 접수일 범위 | 주 파서 |
|---|---|---:|---|---|
| `periodic` | 사업·반기·분기보고서 | 1,054 | 2023-05-12 ~ 2026-06-19 | DART XML |
| `major` | 주요사항보고서 | 598 | 2023-01-02 ~ 2026-03-30 | DART XML |
| `exchange` | 거래소공시 | 1,469 | 2023-01-02 ~ 2026-03-31 | HTML/Jsoup |
| `holding` | 주식 등의 대량보유상황보고서 | 1,083 | 2023-01-02 ~ 2026-03-31 | DART XML |

### 6.1 세부 유형

| 그룹 | 세부 유형 | 문서 수 |
|---|---|---:|
| `periodic` | `annual` | 291 |
| `periodic` | `half` | 234 |
| `periodic` | `quarter` | 529 |
| `exchange` | 단일판매공급계약체결 | 1,106 |
| `exchange` | 단일판매공급계약해지 | 20 |
| `exchange` | 신규시설투자등 | 43 |
| `exchange` | 투자판단관련주요경영사항 | 300 |
| `holding` | 대량보유상황보고서 | 1,083 |
| `major` | null | 598 |

`major`는 manifest의 `doc_subtype`이 비어 있다. 정정 태그를 제거한 `report_nm` 기준으로 약 29개 보고서 유형이 있으므로 적재 시 `report_nm`에서 정규화 세부 유형을 유도하는 규칙이 필요하다. 원문 값과 정규화 값을 모두 보존한다.

## 7. 목록 JSON: `list_*.json`

### 7.1 형식

각 파일의 루트는 JSON 배열이며 배열 원소의 스키마는 동일하다.

```json
[
  {
    "corp_code": "00635134",
    "corp_name": "CJ제일제당",
    "stock_code": "097950",
    "corp_cls": "Y",
    "report_nm": "분기보고서 (2026.03)",
    "rcept_no": "20260515001768",
    "flr_nm": "CJ제일제당",
    "rcept_dt": "20260515",
    "rm": ""
  }
]
```

### 7.2 현황

| 그룹 | 목록 파일 | 목록 전체 항목 | 빈 목록 파일 |
|---|---:|---:|---:|
| `periodic` | 70 | 1,166 | 0 |
| `major` | 70 | 639 | 15 |
| `exchange` | 70 | 10,921 | 0 |
| `holding` | 70 | 10,254 | 0 |

목록 JSON은 DART 조회 결과의 보존·감사 용도로 사용한다. manifest에 없는 목록 항목을 평가용 DB에 자동 적재하지 않는다.

## 8. 원문 형식과 파서 라우팅

### 8.1 `periodic`, `major`, `holding`

대표 표본은 다음과 같은 DART 전용 문서 XML 구조다.

```xml
<?xml version="1.0" encoding="utf-8"?>
<DOCUMENT>
  <DOCUMENT-NAME>...</DOCUMENT-NAME>
  <COMPANY-NAME>...</COMPANY-NAME>
  <BODY>
    <SECTION-1>
      <TITLE>...</TITLE>
      <TABLE>...</TABLE>
    </SECTION-1>
  </BODY>
</DOCUMENT>
```

주요 요소 예시는 다음과 같다.

- 문서: `DOCUMENT`, `DOCUMENT-NAME`, `COMPANY-NAME`, `BODY`
- 구조: `SECTION-1`, `SECTION-2`, `TITLE`, `P`, `PGBRK`
- 표: `TABLE`, `THEAD`, `TBODY`, `TR`, `TH`, `TD`
- 부가 요소: `SPAN`, `IMAGE`, `IMG`, `TU`, `TE`

이는 표준 재무 XBRL fact 파일과 동일한 구조가 아니다. DART 문서용 XML에서 제목, 문단과 표 구조를 직접 복원해야 한다.

일부 문서는 엄격한 XML 파서에서 엔티티 또는 마크업 오류가 발생할 수 있다. 다음 정책을 적용한다.

1. UTF-8과 안전한 XML 옵션으로 1차 파싱한다.
2. 실패 시 허용 범위를 제한한 복구 파서를 사용한다.
3. 정상 추출한 구간은 보존하고 문서 상태를 `PARTIAL`로 기록한다.
4. 실패 원인, 파일명과 파서 버전을 저장한다.
5. 파싱 실패 시에도 공시 메타데이터는 삭제하지 않는다.

### 8.2 `exchange`

`exchange`의 원문은 확장자가 `.xml`이지만 실제 내용은 HTML이다.

```html
<html>
  <head>
    <title>SK하이닉스/신규시설투자등/...</title>
  </head>
  <body>
    <div class="xforms">
      <table>
        <tr>
          <td>투자금액(원)</td>
          <td>5,296,200,000,000</td>
        </tr>
      </table>
    </div>
  </body>
</html>
```

따라서 파일 확장자가 아닌 `doc_group`과 실제 루트 태그를 기준으로 Jsoup HTML 파서를 선택한다.

HTML의 `<meta>`가 `euc-kr`을 표시하더라도 현재 제공 데이터는 UTF-8로 정상 해석된다. 선언만 신뢰하지 말고 UTF-8로 먼저 읽은 뒤 대체문자 발생 여부를 검증한다.

### 8.3 `pdf+html` 예외

원본 XML이 제공되지 않은 다음 3개 정기공시는 PDF와 HTML로 대체 수집됐다.

| 기업 | 보고서 |
|---|---|
| KB금융 | `[기재정정]사업보고서 (2025.12)` |
| 한화오션 | `[기재정정]분기보고서 (2024.03)` |
| 한화에어로스페이스 | `분기보고서 (2026.03)` |

이 문서는 초기 수직 구현 대상에서 제외할 수 있지만 최종 적재 집계에서는 `FAILED`로 방치하지 않고 별도 파서 또는 `PARTIAL` 정책을 적용해야 한다.

## 9. 문서 폴더와 첨부파일

문서별 파일 수는 다음과 같다.

| 그룹 | `n_files` | 문서 수 |
|---|---:|---:|
| `exchange` | 1 | 1,469 |
| `major` | 1 | 598 |
| `holding` | 1 | 1,083 |
| `periodic` | 1 | 841 |
| `periodic` | 2 | 8 |
| `periodic` | 3 | 205 |

정기공시 폴더 예시는 다음과 같다.

```text
20240312000736_annual_2023_12/
├─ 20240312000736.xml
├─ 20240312000736_00760.xml
└─ 20240312000736_00761.xml
```

처리 규칙은 다음과 같다.

1. 접수번호와 정확히 같은 파일명을 기본 문서 후보로 본다.
2. `_숫자` 접미사가 붙은 파일은 첨부문서 후보로 본다.
3. 최종 문서 역할은 파일 내부 `DOCUMENT-NAME`으로 검증한다.
4. 첨부문서를 무조건 제외하지 않고 감사보고서 등 검색 가치가 있는 문서는 별도 `document_role`로 저장한다.
5. 하나의 공시 메타데이터와 여러 원문 파일의 관계를 일대다로 표현한다.

## 10. Unicode 경로 정규화

### 10.1 확인된 현상

- manifest의 한글 `file_path`: NFC 조합형
- 실제 `raw/` 기업 폴더 70개 중 66개: NFD 분해형
- manifest 경로를 그대로 조회할 때 발견되지 않는 문서: 4,054건
- 경로를 NFD로 변환하면 발견되는 문서: 4,054건
- 정규화 후에도 발견되지 않는 문서: 0건

이는 데이터 누락이 아니라 같은 한글을 표현하는 Unicode 방식의 차이다.

### 10.2 처리 규칙

1. 비즈니스 조인 키로 폴더명이나 기업명을 사용하지 않고 `corp_code`와 `doc_id`를 사용한다.
2. 실제 디렉터리 목록을 읽어 폴더명을 NFC로 정규화한 lookup map을 만든다.
3. manifest의 `file_path`도 비교할 때 NFC로 정규화한다.
4. 실제 파일 접근에는 lookup map이 보유한 원래 물리 경로를 사용한다.
5. DB에 manifest 원문 경로와 실제 해석된 경로를 구분해 보존할 수 있다.
6. 데이터 원본 폴더명을 적재 과정에서 임의로 변경하지 않는다.

Java에서는 `java.text.Normalizer`를 사용한다.

```java
String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
```

## 11. 정정공시와 문서 관계

manifest는 정정공시 여부만 제공한다.

```text
is_correction = true | false
```

다음 정보는 제공되지 않는다.

- 정정 대상 원공시 접수번호
- 정정 전후 필드 대응 관계
- 계약 체결과 해지 관계
- 시설투자 최초·변경·종료 관계
- 주요사항보고서의 후속 사건 관계

따라서 다음 단계에서 별도 관계 생성이 필요하다.

1. 기업, 공시 유형과 제목 정규화
2. 원문 정정표의 원공시 일자·항목 추출
3. 관련 공시 링크 또는 접수번호 추출
4. 시간순 후보 생성
5. 규칙 기반 검증
6. 불확실한 관계는 confidence와 함께 후보로 저장

정정 관계가 확정되기 전에는 단순히 가장 최근 공시를 원공시의 최종 상태라고 단정하지 않는다.

## 12. 저장 원칙

### 12.1 원본 보관

약 5.19 GiB의 원문을 애플리케이션 JAR 또는 Docker 이미지에 포함하지 않는다.

권장 방식은 다음과 같다.

```text
호스트 foliolens-data/ ── read-only volume ──> 컨테이너 /data
```

애플리케이션에는 데이터셋 루트를 설정으로 주입한다.

```yaml
foliolens:
  dataset:
    root: /data
```

PostgreSQL에는 원문 전체보다 다음 정보를 저장한다.

- 데이터셋 버전과 해시
- 기업과 공시 메타데이터
- 원본 상대경로, 파일명, 문서 역할과 content hash
- 파싱 상태와 오류
- 구조화한 장·절·문단·표·행
- 검색용 청크
- 원문 위치와 인용 정보
- 검증된 구조화 사실

### 12.2 출처 분리

- `source_provider=CONTEST`로 기록한다.
- 평가용 DB와 검색 인덱스에는 대회 제공 데이터만 적재한다.
- `list_*.json`에만 있고 manifest에는 없는 공시를 자동 보충하지 않는다.
- 평가 경로에서 OpenDART 또는 외부 데이터를 호출하지 않는다.

## 13. 권장 적재 순서

```text
1. 데이터셋 등록과 파일 목록·해시 계산
2. universe.csv 검증 및 Company 적재
3. manifest.jsonl 검증 및 Disclosure 적재
4. 실제 원문 경로 해석과 파일 수 검증
5. DisclosureDocument와 파일 해시 적재
6. 문서별 파서 라우팅
7. 섹션·문단·표·행 구조화
8. 검색 청크 생성
9. 공시 유형별 사실 추출
10. 정정·후속 관계 생성
11. 품질 집계와 검색 인덱스 활성화
```

적재는 전체를 하나의 트랜잭션으로 처리하지 않는다. 문서 또는 적절한 배치 단위로 커밋하며, 한 문서의 실패가 다른 문서 적재를 중단시키지 않게 한다.

### 13.1 문서 처리 상태

| 상태 | 의미 |
|---|---|
| `PENDING` | 메타데이터만 등록되고 아직 원문을 처리하지 않음 |
| `COMPLETED` | 원문과 필요한 구조가 정상적으로 처리됨 |
| `PARTIAL` | 일부 첨부·구간·표 처리에 실패했지만 사용 가능한 결과가 있음 |
| `FAILED` | 원문을 사용할 수 있는 형태로 처리하지 못함 |

## 14. 공시 유형별 초기 구조화 대상

모든 문서를 처음부터 완전 구조화하지 않는다. 질문 빈도와 결정적 계산 가능성이 높은 유형부터 진행한다.

### 14.1 신규시설투자

| fact key 예시 | 원문 레이블 |
|---|---|
| `investment.target` | 투자대상 |
| `investment.amount` | 투자금액(원) |
| `investment.equity_amount` | 자기자본(원) |
| `investment.equity_ratio` | 자기자본대비(%) |
| `investment.purpose` | 투자목적 |
| `investment.start_date` | 투자기간 시작일 |
| `investment.end_date` | 투자기간 종료일 |
| `investment.decision_date` | 이사회결의일(결정일) |

### 14.2 단일판매·공급계약

| fact key 예시 | 원문 레이블 |
|---|---|
| `contract.description` | 계약내용 |
| `contract.amount` | 계약금액 |
| `contract.sales_amount` | 최근 매출액 |
| `contract.sales_ratio` | 매출액 대비 비율 |
| `contract.counterparty` | 계약상대 |
| `contract.start_date` | 계약기간 시작일 |
| `contract.end_date` | 계약기간 종료일 |

구조화 사실에는 정규화 값만 저장하지 않고 다음을 함께 보존한다.

- 원문 값
- 원문 단위
- 정규화 값과 단위
- 변환 규칙
- 원문 파일
- 장·절·표·행 위치
- 추출 방식과 파서 버전
- 검증 상태

## 15. 적재 완료 검증 기준

### 15.1 기준 건수

| 검증 항목 | 기대값 |
|---|---:|
| 기업 | 70 |
| 전체 공시 | 4,204 |
| 정기공시 | 1,054 |
| 주요사항보고서 | 598 |
| 거래소공시 | 1,469 |
| 지분공시 | 1,083 |
| 정정공시 | 1,004 |
| `xml` 형식 문서 | 4,201 |
| `pdf+html` 형식 문서 | 3 |
| 원문 XML 파일 | 4,616 |

### 15.2 필수 검증

- `universe.csv`의 기업 수와 고유 코드 수가 70인지 확인한다.
- manifest 행 수, 고유 `doc_id` 수와 고유 `rcept_no` 수가 모두 4,204인지 확인한다.
- 모든 manifest의 `corp_code`가 기업 마스터에 존재하는지 확인한다.
- Unicode 경로 정규화 후 모든 `file_path`가 실제 폴더로 해석되는지 확인한다.
- 각 문서의 `n_files`와 실제 폴더 파일 수가 일치하는지 확인한다.
- 파일별 SHA-256을 계산하고 데이터셋 버전에 연결한다.
- 같은 데이터셋을 두 번 적재해도 기업, 공시, 문서가 중복되지 않는지 확인한다.
- `COMPLETED + PARTIAL + FAILED`가 manifest 4,204건과 일치하는지 확인한다.
- 임의의 검색 청크에서 원문 파일과 위치로 역추적할 수 있는지 확인한다.

## 16. 현재 확인된 데이터 품질 결과

| 검증 | 결과 |
|---|---|
| 기업 행 수 | 정상: 70 |
| 기업 코드 고유성 | 정상: 70/70 |
| 종목코드 고유성 | 정상: 70/70 |
| manifest 행 수 | 정상: 4,204 |
| `doc_id` 고유성 | 정상: 4,204/4,204 |
| `rcept_no` 고유성 | 정상: 4,204/4,204 |
| 기업별 `n_*` 합계와 manifest 그룹 건수 | 일치 |
| Unicode 정규화 후 원문 폴더 존재 | 정상: 4,204/4,204 |
| manifest `n_files`와 실제 파일 수 | 정상: 4,204/4,204 |
| 목록 JSON 파싱 | 정상: 280/280 |
| 원공시·정정공시 관계 | 미제공, 후속 구현 필요 |
| 전체 원문 파싱 성공률 | 아직 미측정 |
| 파일별 SHA-256 목록 | 아직 미생성 |

## 17. 첫 번째 수직 구현 범위

전체 데이터 적재 전에 다음 작은 범위로 적재 계약과 파서를 검증한다.

1. 기업 마스터 70개 적재
2. manifest 메타데이터 4,204건 멱등 적재
3. SK하이닉스 `신규시설투자등` 공시 `20240424800596` 원문 탐색
4. Jsoup으로 표의 레이블과 값 추출
5. 투자대상, 투자금액, 자기자본 대비 비율, 투자목적과 투자기간 구조화
6. 각 사실에서 원문 파일과 표 행으로 역추적
7. 투자금액과 자기자본을 사용한 비율 재계산 및 원문 비율과 비교
8. 동일 작업 재실행 시 중복이 발생하지 않는지 확인

이 수직 구현이 성공한 후 신규시설투자 43건 전체, 다른 거래소공시 유형, DART XML 순서로 확장한다.

## 18. 미확정 사항

다음 항목은 구현 전에 팀 결정 또는 추가 표본 검사가 필요하다.

1. 최종 내부 데이터셋 ID와 버전 증가 규칙
2. 데이터셋 전체 해시 산정 방식
3. 원문 저장소를 로컬 볼륨으로 유지할지 객체 저장소로 옮길지
4. DART XML 복구 파서와 허용 가능한 복구 범위
5. 정기공시 첨부문서의 `document_role` 분류 규칙
6. `major`의 29개 세부 유형 표준 코드
7. 정정·후속공시 관계의 확정 규칙과 confidence 기준
8. 청크 크기와 표 청킹 전략
9. 전체 원문 파싱 성공률과 유형별 실패 허용 기준
10. PDF+HTML 대체 문서의 P0 처리 수준

이 항목이 확정되면 `DECISIONS.md`, ERD, 기능명세서와 적재 테스트 기준을 함께 갱신한다.
