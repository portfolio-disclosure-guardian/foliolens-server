# Project Decisions

## 2026-07-22: Backend 기술 스택

- 결정: Java 21, Spring Boot, Gradle Groovy 사용
- 이유: 백엔드 팀원 2명이 Java와 Spring에 익숙함
- 제외: 초기 Python 멀티 Agent 구조
- 상태: 승인

## 2026-07-22: Frontend 기술 스택

- 결정: React, TypeScript, Vite 사용
- 이유: 대시보드와 AI 질문 화면의 동적 상호작용 필요
- 원칙: 초기에는 Redux와 복잡한 UI 라이브러리를 사용하지 않음
- 상태: 승인

## 2026-07-22: Agent 제어 방식

- 결정: Spring 백엔드가 실행 흐름을 제어
- HyperCLOVA X는 질문 구조화와 설명을 담당
- 계산과 중요도는 백엔드가 담당
- 상태: 승인

## 2026-08-27: 날짜와 시각 타입 정책

- 결정: 접수일·상장일·검색기간·보고기간·`asOf`는 Java `LocalDate`, PostgreSQL `DATE`, JSON `yyyy-MM-dd`를 사용
- 결정: 생성·수정·파싱·완료 시각은 Java `Instant`, PostgreSQL `TIMESTAMPTZ`, JSON UTC ISO-8601(`Z`)을 사용
- 규칙: 외부 API 계약과 영속 모델에서 `LocalDateTime`을 사용하지 않음
- 확장: 실제 접수 시각이 제공되면 기존 접수일을 유지하고 `submittedAt: Instant`를 별도 추가
- 상태: 승인
