# 승인 상태 강제·치명적 오류 검증 연결 프롬프트 (A7)

> 이 문서는 Claude Code 세션에 그대로 전달하는 작업 지시서입니다.
> 사전 지식 없이 이 문서만으로 작업이 가능하도록 필요한 사실을 모두 담았습니다.

## 목표

`GoldenCase.approvalStatus`와 `GoldenCase.criticalErrors`(+`AnswerPolicy.forbiddenExpressions`)는 지금 필드만 있고 아무 코드도 읽지 않는다. 이번 작업은 둘을 실제로 런타임에 연결한다.

1. **승인 상태 강제**: `approvalStatus`가 `APPROVED`가 아닌 골든 케이스는 (설정으로 켰을 때) 정상 답변을 만들지 않는다.
2. **치명적 오류 검증**: HCX가 만든 `renderedAnswer`에 `forbiddenExpressions`/`criticalErrors`에 해당하는 문구가 섞여 있으면 답변을 거부한다.

두 기능은 서로 독립이다 — 승인 여부는 "이 골든 케이스를 쓸 자격이 있는가"이고, 안전 검증은 "만들어진 텍스트가 안전한가"이다.

## 확정된 사실 (코드 실측)

### 지금 코드가 하는 일

`OrchestrationAnswerService.generateAnswer()` (`backend/src/main/java/com/foliolens/backend/orchestration/OrchestrationAnswerService.java`):

```java
private AnswerResult generateAnswer(AnswerQuestionCommand command, QuestionRun run) {
    AnswerPolicy policy = answerPolicies.stream()
            .filter(candidate -> candidate.goldenCases().stream()
                    .anyMatch(goldenCase -> goldenCase.goldenCaseId().equals(command.externalQuestionId())))
            .findFirst()
            .orElse(null);
    if (policy == null) {
        return placeholder(run);
    }
    // ... 검색 → 계산 → outcome → claims → answerReferenceValidator.validate(...) → hcxAnswerGenerator.generateAnswer(...) ...
    return new AnswerResult(...);
}
```

- `approvalStatus`는 전혀 읽지 않는다. `goldenCaseId`만 일치하면 무조건 진행한다.
- `hcxAnswerGenerator.generateAnswer(...)`가 반환한 `renderedAnswer`는 그대로 `AnswerResult`에 들어간다. `forbiddenExpressions`는 `ClovaStudioHcxAnswerGenerator`가 system 프롬프트에 "이런 표현 쓰지 마라"는 지시로 넣긴 하지만(`backend/src/main/java/com/foliolens/backend/answer/hcx/ClovaStudioHcxAnswerGenerator.java`), **모델이 실제로 지켰는지 사후 검증하는 코드는 없다.** `criticalErrors`는 프롬프트에도 안 들어가고 아무 데도 안 쓰인다.
- `AnswerReferenceValidator`(`backend/src/main/java/com/foliolens/backend/answer/AnswerReferenceValidator.java`)는 evidence/fact/calculation의 **참조 무결성**만 확인한다. 텍스트 내용(표현)은 검사하지 않는다 — 이번에 만들 것과 역할이 다르다.

### 지금 유일한 실데이터: GOLD-FACILITY-001은 `C_REVIEW_PENDING`이다

`GoldFacility001Fixture.policy()`의 골든 케이스는 `approvalStatus = GoldenCaseApprovalStatus.C_REVIEW_PENDING`이다(finance_domain 02번 문서 §6과 동일). **이게 현재 전체 테스트 스위트가 COMPLETED 답변을 검증하는 유일한 경로다.** 승인 상태를 무조건 강제하면 이 테스트들이 전부 깨진다 — 그래서 "작업 내용 1"에 스위치를 두라고 명시했다.

### `criticalErrors`/`forbiddenExpressions`는 매칭용 문구가 아니라 카테고리 설명이다

예: `"매수·매도 권유"`, `"수익 보장 또는 성공확률 단정"`. 실제 모델 답변이 이 문자열을 그대로 포함할 가능성은 낮다 — 모델은 "지금 사세요" 같은 실제 표현을 쓰지, 카테고리 라벨을 그대로 쓰지 않는다. **즉 문자열 포함 검사는 재현율이 낮은 약한 방어선이다.** 이번 작업은 이 구조를 만드는 것이지, 탐지 정확도를 보장하는 것이 아니다. 정확도를 올리려면 Role C가 카테고리 라벨이 아니라 실제 트리거 문구/정규식을 공급해야 하며, 그건 이 프롬프트의 범위 밖이다.

## 작업 내용

### 1. 승인 상태 강제 — 설정으로 켜고 끌 수 있게

`application.yml`에 플래그를 추가한다 (기존 `foliolens.question.plan.search-disclosures.*`와 같은 위치·스타일):

```yaml
foliolens:
  question:
    answer:
      require-approved-golden-case: ${FOLIOLENS_REQUIRE_APPROVED_GOLDEN_CASE:false}
```

기본값 `false` — 지금 승인된 골든 케이스가 하나도 없으므로, 기본값을 켜두면 아무 질문도 답변할 수 없다. Role C가 실제로 골든 케이스를 `APPROVED`로 승격하기 시작하면 그때 `true`로 바꾼다.

`OrchestrationAnswerService`가 매칭할 때 정책뿐 아니라 **어떤 GoldenCase가 매칭됐는지**도 알아야 승인 상태를 확인할 수 있다. 지금 코드는 정책만 뽑고 골든 케이스 자체는 버린다 — 아래처럼 함께 반환하도록 바꾼다.

```java
private record PolicyMatch(AnswerPolicy policy, GoldenCase goldenCase) {}

private Optional<PolicyMatch> matchPolicy(String externalQuestionId) {
    for (AnswerPolicy policy : answerPolicies) {
        for (GoldenCase goldenCase : policy.goldenCases()) {
            if (goldenCase.goldenCaseId().equals(externalQuestionId)) {
                return Optional.of(new PolicyMatch(policy, goldenCase));
            }
        }
    }
    return Optional.empty();
}
```

`generateAnswer()`에서:

```java
PolicyMatch match = matchPolicy(command.externalQuestionId()).orElse(null);
boolean approved = match != null && match.goldenCase().approvalStatus() == GoldenCaseApprovalStatus.APPROVED;
if (match == null || (requireApprovedGoldenCase && !approved)) {
    return placeholder(run);
}
AnswerPolicy policy = match.policy();
GoldenCase goldenCase = match.goldenCase();
```

`requireApprovedGoldenCase`는 생성자 `@Value("${foliolens.question.answer.require-approved-golden-case:false}") boolean`로 주입한다 — 이 값 하나만을 위해 `@ConfigurationProperties` 클래스를 새로 만들지 않는다(`QuestionPlanConverter`가 이미 이 스타일을 쓰고 있다).

**미승인 케이스도 "매칭 실패"와 같은 `placeholder()`로 보낸다.** "찾았지만 미승인"과 "아예 못 찾음"을 구분하는 메시지는 이번 범위가 아니다(아래 "범위 밖" 참고).

### 2. 답변 안전 검증기 신설

`backend/src/main/java/com/foliolens/backend/answer/AnswerSafetyValidator.java`:

```java
package com.foliolens.backend.answer;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.policy.AnswerPolicy;
import com.foliolens.backend.policy.GoldenCase;

@Component
public class AnswerSafetyValidator {

    public void validate(String renderedAnswer, AnswerPolicy policy, GoldenCase goldenCase) {
        Stream.concat(policy.forbiddenExpressions().stream(), goldenCase.criticalErrors().stream())
                .filter(renderedAnswer::contains)
                .findFirst()
                .ifPresent(matched -> {
                    throw new BusinessException(
                            ErrorCode.AGENT_502_1, "답변에 금지된 표현이 포함되어 있습니다: " + matched);
                });
    }
}
```

`OrchestrationAnswerService`에 `AnswerSafetyValidator`를 주입하고, `hcxAnswerGenerator.generateAnswer(...)` 호출 **직후**, `AnswerResult`를 만들기 **전**에 호출한다:

```java
String renderedAnswer = hcxAnswerGenerator.generateAnswer(
        command.question(), policy, retrieval, calculation, outcome);
answerSafetyValidator.validate(renderedAnswer, policy, goldenCase);
```

검증 실패는 `BusinessException`을 던지므로 기존 `getAnswer()`의 try/catch가 이미 `questionRunService.failQuestionRun(run, errorCode)`로 받는다 — 오케스트레이션의 다른 부분은 손댈 필요 없다.

### 3. 프롬프트에 `criticalErrors`도 추가 (선택이지만 권장)

`ClovaStudioHcxAnswerGenerator`의 `PolicyPromptView`는 지금 `forbiddenExpressions`만 프롬프트에 넣는다. 매칭된 `GoldenCase.criticalErrors()`도 함께 넣으면, 모델이 애초에 그 표현을 피할 확률이 올라간다(사후 검증과 별개로 사전 예방). 이 변경은 `generateAnswer(...)` 호출부에 `goldenCase`를 추가로 넘겨야 하므로 `HcxAnswerGenerator` 인터페이스 시그니처가 바뀐다 — 영향 범위가 커지니, 시간이 부족하면 2번(사후 검증)만 하고 이건 건너뛰어도 된다.

## 범위 밖 (이번에 만들지 않는 것)

- **정교한 표현 탐지(정규식, 동의어, 의미 기반 판단).** 위에서 설명했듯 지금 문구들은 카테고리 라벨이라 문자열 포함 검사의 재현율이 낮다. 이걸 개선하려면 Role C가 실제 트리거 문구를 공급해야 한다 — 코드로 해결할 문제가 아니다.
- **"미승인이라 답변 못 함"을 구분하는 전용 메시지/상태.** 지금은 매칭 실패와 똑같이 `placeholder()`로 보낸다.
- **승인 상태를 API 응답이나 `think_trace`에 노출.** 내부 판단 기준일 뿐 외부 계약이 아니다.
- **`require-approved-golden-case=true`로 기본값을 바꾸는 것.** Role C가 실제로 골든 케이스를 승인하기 전까지는 `false`로 둔다.

## 완료 기준

1. `foliolens.question.answer.require-approved-golden-case=false`(기본값)일 때 기존 테스트가 전부 그대로 통과한다 — `GOLD-FACILITY-001`(PENDING)이 여전히 `COMPLETED`를 반환해야 한다.
2. 같은 플래그를 `true`로 켠 테스트에서는 `GOLD-FACILITY-001` 질문이 `placeholder()` 경로(UNANSWERABLE)로 빠진다.
3. `AnswerSafetyValidator` 단위 테스트: `forbiddenExpressions`/`criticalErrors`에 있는 문구를 그대로 포함한 답변은 `BusinessException`(`AGENT_502_1`)을 던지고, 깨끗한 답변은 통과한다.
4. `OrchestrationAnswerService`에 안전 검증 실패 시나리오 테스트 추가 — 실패하면 `questionRunService.failQuestionRun`이 호출되고 run이 `FAILED`로 남는다(기존 "답변_생성_실패는_run을_FAILED로_기록한다" 테스트와 같은 패턴).
5. `./gradlew test`(backend 디렉토리에서) 전체 통과.
