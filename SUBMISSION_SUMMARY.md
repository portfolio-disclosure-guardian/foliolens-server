# 제출 요약 (2026-09-05 기준)

이 문서는 "평가 API가 어떤 요청/응답 규격으로 동작하는가"와 "지금 프로젝트가 어떤 상태로 마무리됐는가"를 한 곳에 정리한 마무리 문서다. 상세 계약은 [`docs/API_명세서.md`](docs/API_명세서.md) 16절을 기준으로 한다.

## 1. 평가 API 규격 (주최측 공지 2026-09-05 확정)

### 요청 (주최측 → 참가팀)

```
GET {end-point}/answer?question_id={질의 ID}&question={평가 질의}
```

- 경로 고정: `/answer`
- 인증 헤더 없음
- 순차 단건 호출, 문항당 타임아웃 300초, 5xx·타임아웃 시 최대 2회 재시도

### 응답 (참가팀 → 주최측, JSON)

```json
{
  "question_id": "Q-001",
  "question": "평가 질의 원문",
  "retrieved_context": "[1] receipt_no=... | report_name=... | submitted_at=... | section=...\n본문 발췌",
  "think_trace": "[PLANNING] ...\n[RETRIEVAL] ...\n[CALCULATION] ...\n[VALIDATION] ...",
  "answer": "최종 생성 답변"
}
```

- **5개 필드 모두 문자열(string) 타입** — 여러 근거 문서·실행 단계는 문자열 안에서 구분 태그로 이어붙인다. 태그 형식 자체는 평가 대상이 아니다.
- `Content-Type: application/json;charset=UTF-8` — 질의·응답이 전부 한국어라 charset을 명시하지 않으면 일부 클라이언트가 잘못 디코딩한다.

### 이 규격을 만드는 코드

| 역할 | 파일 |
|---|---|
| 엔드포인트 진입점, Content-Type 고정 | `backend/src/main/java/com/foliolens/backend/evaluation/controller/EvaluationAnswerController.java` |
| 5개 키 응답 DTO, 배열→문자열 포맷팅 | `backend/src/main/java/com/foliolens/backend/evaluation/response/EvaluationAnswerResponse.java` |
| 질문 이해→검색→계산→HCX 답변→검증 오케스트레이션 | `backend/src/main/java/com/foliolens/backend/orchestration/OrchestrationAnswerService.java` |

## 2. 배포 상태

- **평가 엔드포인트**: `http://175.45.201.103/answer?question_id=...&question=...` (README.md에도 명시됨)
- **서버**: 네이버클라우드 VPC, Ubuntu 24.04, Docker + Docker Compose
- **컨테이너**: `foliolens-db-1`(PostgreSQL 17, 공시 데이터 복원 완료), `foliolens-app-1`(Spring Boot, 포트 80으로 노출) — 둘 다 healthy
- **HCX 설정**: `HCX_MODEL=HCX-005`, `HCX_APP_TYPE=serviceapp` (제출용 실키)
- **코드 기준**: `develop` 브랜치 최신 커밋

## 3. 이번 세션에서 확인·수정한 것

1. VPC 서버 최초 배포: Docker 설치, DB 덤프 복원, 앱 컨테이너 빌드·기동, ACG에 이미 열려있는 80번 포트로 노출
2. 주최측 헬스체크 공지 대조 — 아웃바운드/인바운드 공개망 통신, 시간 동기화 전부 정상 확인
3. 주최측 평가 API 규격 공지와 실제 응답을 대조해 발견한 실제 버그 2건 수정 후 재배포·재검증:
   - `retrieved_context`/`think_trace`가 배열이었던 것 → 문자열로 변경
   - 응답 `Content-Type`에 `charset=UTF-8` 누락 → 명시적으로 고정 (일부 클라이언트가 한글을 깨서 읽던 원인)
4. `scripts/submission-smoke.ps1`을 새 문자열 규격에 맞게 수정하고 BOM을 추가해 Windows PowerShell 5.1에서도 정상 동작하도록 함
5. `docs/API_명세서.md`의 "확정 필요" 항목 중 인증 방식·자료형·타임아웃/동시성을 이번 공지 기준으로 해소 반영

## 4. 재검증 방법

```powershell
.\scripts\submission-smoke.ps1 -BaseUrl "http://175.45.201.103" -InfrastructureOnly
.\scripts\submission-smoke.ps1 -BaseUrl "http://175.45.201.103"
```

마지막 실행 결과: `A9 submission smoke passed.`

## 5. 남은 수동 작업 (코드·서버 밖의 일)

- [ ] 대회 제출 폼에 엔드포인트(`http://175.45.201.103`) 등록 여부 확인
- [ ] 추가 공지(https 필수 여부, 평가 기간 확정일 등) 계속 확인
- [ ] **9월 30일 전** 네이버클라우드 결제수단/서버 해지 (안 하면 자동결제)
