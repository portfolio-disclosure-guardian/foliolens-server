# AnswerPolicy 다중 공시유형 지원 리팩터링 프롬프트

> 이 문서는 Claude Code 세션에 그대로 전달하는 작업 지시서입니다.
> 사전 지식 없이 이 문서만으로 작업이 가능하도록 필요한 사실을 모두 담았습니다.

## 목표

`AnswerPolicy`가 지금은 "신규시설투자등"(finance_domain 02번) 하나에만 고정돼 있다. 이걸 `docs/finance_domain/01~12` 공시분석유형 각각이 독립된 `AnswerPolicy` 인스턴스를 가질 수 있는 구조로 바꾼다.

**이번 작업은 12개 유형의 실제 정책 데이터를 채우는 게 아니다.** 지금 값이 있는 건 GOLD-FACILITY-001(02번) 하나뿐이고 나머지 11개는 아직 코드화된 정책이 없다. 이번에 할 일은 **여러 개를 담을 수 있는 그릇으로 구조만 바꾸는 것**이며, 값은 하나도 바꾸지 않는다.

## 확정된 사실 (코드 실측)

- `backend/src/main/java/com/foliolens/backend/policy/AnswerPolicy.java`:
  ```java
  public record AnswerPolicy(
          String policyVersion,
          String disclosureSubtype,
          List<FactPolicy> facts,
          CalculationPolicy calculation,
          List<String> allowedExpressions,
          List<String> forbiddenExpressions,
          GoldenCase goldenCase) {   // ← 단수. 이걸 List<GoldenCase>로 바꾸는 게 이번 작업의 핵심
  }
  ```
- `GoldenCase.java` 자체 주석: "GOLD-FACILITY-001 3~7절의 기대값. 실제 검색·계산 구현을 이 값과 대조하는 **회귀 테스트 기준선**." — 정책 규칙이 아니라 테스트 정답지 역할이라는 뜻.
- 현재 유일한 인스턴스는 `GoldFacility001Fixture.policy()`: `policyVersion="1.0-draft"`, `disclosureSubtype="신규시설투자등"`, `goldenCase`는 `GOLD-FACILITY-001` 1건.
- `OrchestrationAnswerService.getAnswer()`가 `GoldFacility001Fixture.policy()`를 하드코딩해서 쓰고, `command.externalQuestionId()`가 그 골든케이스 ID와 일치하는지만 확인해서 불일치면 placeholder를 반환한다.
- `policy.goldenCase()`를 호출하는 곳은 아래 10개 파일이다. 전부 시그니처가 바뀌므로 컴파일 대상이다.
  ```text
  backend/src/main/java/com/foliolens/backend/policy/AnswerPolicy.java
  backend/src/main/java/com/foliolens/backend/policy/GoldenCase.java
  backend/src/main/java/com/foliolens/backend/policy/GoldFacility001Fixture.java
  backend/src/main/java/com/foliolens/backend/orchestration/OrchestrationAnswerService.java
  backend/src/main/java/com/foliolens/backend/answer/FakeHcxAnswerGenerator.java
  backend/src/main/java/com/foliolens/backend/retrieval/fake/FakeDisclosureRetriever.java
  backend/src/test/java/com/foliolens/backend/policy/GoldFacility001FixtureTest.java
  backend/src/test/java/com/foliolens/backend/orchestration/OrchestrationAnswerServiceTest.java
  backend/src/test/java/com/foliolens/backend/answer/AnswerScenarioTest.java
  backend/src/test/java/com/foliolens/backend/answer/hcx/ClovaStudioHcxAnswerGeneratorTest.java
  ```

## finance_domain 스펙 사실

- `docs/finance_domain/00.공통규격.md` 상단: "적용 범위: 공시 분석유형 01~12 전체". 즉 `01.단일판매공급계약.md`부터 `12.정정후속공시기준시점상태.md`까지 최대 **12개**의 유형이 있고, 각 유형이 하나의 `AnswerPolicy`에 대응한다. `00`은 공통 envelope 규격 문서이지 그 자체가 유형이 아니다.
- `00.공통규격.md` §9 "골든 후보 필수 구성": 골든 후보는 유형 하나당 여러 개 있을 수 있는 구조다. 실제로 실측해보면 `01`, `03`, `04`, `06`번 문서에는 이미 골든 후보가 유형당 2~4개씩 있다(전부 승인 상태 `C_REVIEW_PENDING`, 아직 `APPROVED` 아님). `02`(신규시설투자)만 지금 코드에 반영된 `GOLD-FACILITY-001` 1건이 있다.
- 이 사실 때문에 "유형을 여러 개 지원"과 "유형당 골든케이스를 여러 개 지원"은 분리할 수 없는 하나의 구조 변경이다 — `GoldenCase` 단수 필드로는 애초에 `01`, `03`, `04`, `06`번 문서를 코드화할 방법이 없다.

## 작업 내용

### 1. `GoldenCase`를 리스트로 변경

`AnswerPolicy.goldenCase` (단수) → `AnswerPolicy.goldenCases` (`List<GoldenCase>`)로 바꾼다. `GoldenCase` 레코드 자체의 필드는 건드리지 않는다 (필드 보강은 이번 범위 밖 — 아래 "범위 밖" 참고).

### 2. `GoldFacility001Fixture` 수정

`policy()`가 반환하는 값에서 `goldenCase` 자리를 `List.of(기존 GoldenCase 값)`으로 감싼다. **골든케이스의 실제 값(투자금액, 자기자본, 기대 답변 등)은 문자 하나도 바꾸지 않는다.**

### 3. 10개 호출 지점 수정

지금은 유형당 골든케이스가 정확히 1개뿐이므로, `policy.goldenCase()` 호출을 `policy.goldenCases().getFirst()`로 바꾸면 동작이 그대로 유지된다. 위에 나열된 10개 파일을 전부 이 방식으로 고친다.

### 4. `OrchestrationAnswerService`가 여러 정책을 받을 수 있게 함

지금:
```java
AnswerPolicy policy = GoldFacility001Fixture.policy();
if (!policy.goldenCase().goldenCaseId().equals(command.externalQuestionId())) {
    return placeholder(run);
}
```

이걸 "정확히 하나의 하드코딩된 정책"이 아니라 "여러 정책 후보 중 골든케이스 ID가 일치하는 것을 찾는" 형태로 바꾼다. 생성자로 `List<AnswerPolicy>`를 주입받고, 그 안의 모든 정책의 모든 골든케이스를 순회해서 `externalQuestionId`와 일치하는 걸 찾으면 그 정책으로 진행하고, 없으면 지금처럼 placeholder를 반환한다.

지금은 정책이 `GoldFacility001Fixture.policy()` 하나뿐이므로 `List.of(GoldFacility001Fixture.policy())`를 주입하면 된다.

## 범위 밖 (이번에 만들지 않는 것)

- **registry, factory, `DisclosureAnalysisType` enum 등 자동 등록/선택 메커니즘.** ROLE_A_SPEC.md 2.3절 불변규칙 10번("첫 수직 슬라이스에 registry, factory, rule engine을 추가하지 않는다")에 해당한다. 정책이 2개 이상 실제로 생기기 전까지는 `List<AnswerPolicy>`를 그냥 순회하는 것으로 충분하다.
- **`01`, `03`~`12`번 유형의 실제 `AnswerPolicy` 인스턴스 작성.** 이건 Role C가 각 유형 문서의 Fact·계산·표현 정책을 승인한 뒤 진행할 별도 작업이다.
- **`GoldenCase`에 "치명적 오류", "승인 상태" 필드 추가.** finance_domain 00번 §9에는 이 필드들이 있지만, 지금 모든 골든 후보가 `C_REVIEW_PENDING`이라 승인 로직이 당장 필요하지 않다. 별도 프롬프트로 처리한다.
- **HCX가 만든 계획에서 disclosureSubtype을 추론해 정책을 자동 선택하는 로직.** 지금 매칭은 여전히 골든케이스 ID 기준이다.

## 완료 기준

1. `AnswerPolicy.goldenCases()`가 `List<GoldenCase>`를 반환한다.
2. 위 10개 파일이 새 시그니처로 컴파일된다.
3. `./gradlew test` (backend 디렉토리에서) 전체 테스트가 리팩터링 전과 동일하게 통과한다 — 값이나 동작 변경이 없었음을 이걸로 확인한다.
4. `OrchestrationAnswerService`가 `List<AnswerPolicy>`를 생성자로 받고, 그 안에서 골든케이스 ID로 매칭하는 방식으로 바뀌었다.
5. `git diff`에서 골든케이스의 실제 값(금액·날짜·기대 답변 문자열 등)이 바뀐 줄이 없다 — 구조만 바뀌었는지 확인한다.
