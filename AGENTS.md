# AGENTS.md

## 1. 프로젝트 목적

이 프로젝트는 제10회 2026 미래에셋증권 AI Festival의
공시 Agent 부문 출품작이다.

서비스 가칭은 `FolioLens`이며, 사용자의 보유 종목과 투자 이유를
기준으로 신규 공시를 분석해 투자 가정의 변화와 포트폴리오 위험을
근거와 함께 추적하는 개인화 공시 분석 Agent를 개발한다.

작업을 계획하거나 구현하기 전에 다음 문서를 우선 확인한다.

1. `docs/PROJECT_CONTEXT.md`
2. `docs/요구사항_정의서.md`
3. `docs/기능명세서.md`
4. `docs/IA.md`
5. `docs/DECISIONS.md`

요구사항이 충돌할 경우 다음 순서로 우선한다.

1. 현재 사용자의 명시적인 요청
2. 요구사항 정의서의 승인된 Must 요구사항
3. 기능명세서
4. IA
5. PROJECT_CONTEXT
6. 기존 코드 관례

## 2. 핵심 제품 원칙

- 단순 공시 요약 서비스가 아니라 포트폴리오 기반 개인화 분석 서비스다.
- 사용자의 보유 종목, 보유 비중, 투자 이유를 분석에 반영한다.
- 공시 원문의 사실과 Agent의 해석을 명확히 분리한다.
- 모든 핵심 수치와 주장에는 공시 원문 근거를 연결한다.
- 수치 계산과 중요도 점수는 백엔드의 결정적 로직에서 수행한다.
- LLM이 금액, 날짜, 변화율, 지분 희석률을 임의로 계산하게 하지 않는다.
- 매수·매도 추천, 목표주가, 상승 확률, 수익 보장 표현을 제공하지 않는다.
- 근거가 부족하면 추측하지 않고 `판단 불가` 또는 `추가 확인 필요`로 처리한다.
- 주가 예측 대신 긍정·부정 시나리오와 다음 확인 지표를 제공한다.

## 3. 대회 제약

- 언어모델은 대회에서 허용한 HyperCLOVA X 계열만 사용한다.
- 다른 언어모델을 서비스 경로에 추가하지 않는다.
- 임베딩 및 기타 모델은 대회 허용 정책을 확인한 뒤 사용한다.
- 대회 제공 데이터를 최우선으로 사용한다.
- 외부 OpenDART 및 뉴스 데이터는 대회가 허용한 범위에서만 사용한다.
- 예선 제출을 위해 소스코드, 기술제안서 및 평가 API 서버를 준비해야 한다.
- 평가 API 규격은 내부 도메인 API와 분리된 어댑터로 구현한다.

## 4. 기술 스택

### Backend

- Java 21
- Spring Boot
- Gradle Groovy DSL
- Spring Web
- Spring Data JPA
- Bean Validation
- PostgreSQL
- Flyway
- Spring Boot Actuator

### Frontend

- React
- TypeScript
- Vite
- 초기에는 기본 CSS와 fetch API를 사용한다.
- 필요성이 검증되기 전에는 Redux, Next.js 등 복잡한 도구를 추가하지 않는다.

### External services

- HyperCLOVA X
- 대회 제공 공시 데이터
- 허용되는 경우 OpenDART

## 5. 저장소 구조

```text
portfolio-disclosure-guardian/
├─ .github/
├─ docs/
├─ backend/
├─ frontend/
├─ AGENTS.md
├─ README.md
└─ .gitignore