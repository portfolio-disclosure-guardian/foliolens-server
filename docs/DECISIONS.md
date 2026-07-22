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