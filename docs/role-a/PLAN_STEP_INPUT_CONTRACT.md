# `PlanStep.input` 계약

- 문서 버전: `0.1`
- 작성일: `2026-08-27`
- 범위: 역할 A의 질문 계획(`QuestionPlanCandidate` → `QuestionPlan`) 안에서 사용하는 도구별 `input`
- 상태: 첫 수직 슬라이스 기준 제안. 역할 B·C의 미확정 계약은 별도로 표시한다.

상태 표기는 다음 의미로 사용한다.

| 표기 | 의미 |
|---|---|
| `CODE_CONFIRMED` | 2026-08-27 작업 트리에서 직접 확인한 현재 구현 |
| `TARGET` | 첫 수직 슬라이스에 적용할 권장 계약이며 아직 구현 완료를 뜻하지 않음 |
| `DEPENDENCY` | 역할 B·C의 계약이 있어야 최종 확정 가능 |
| `DECISION_REQUIRED` | 구현 전에 역할 간 선택이 필요한 항목 |

1~3장·5~9장·12~13장은 `TARGET`, 4장은 `DEPENDENCY`, 10장은 `CODE_CONFIRMED`, 11장은 `DECISION_REQUIRED`로 읽는다.

## 1. 결론

`input`에는 **도구가 무엇을 조회하거나 계산할지 설명하는 계획 시점의 인자**가 들어간다. 실제 조회 결과나 DB 객체가 들어가는 자리가 아니다.

권장 경계는 다음과 같다.

| 단계 | `input` 타입 | 의미 |
|---|---|---|
| HCX 출력 | `JsonNode` | 아직 신뢰할 수 없는 도구별 JSON 후보 |
| Spring 검증 후 | `ToolInput`의 구체 record | 도구 종류와 구조·값·참조가 검증된 실행 계획 |
| 도구 실행 직전 | 도구별 runtime command | 전역 기업·시간 조건과 이전 단계의 실제 ID를 결합한 실행 인자 |

따라서 전체 `input`을 하나의 `String`으로 유지하면 안 된다. 반대로 HCX가 만드는 후보부터 `ToolInput` 인터페이스로 바로 역직렬화하는 것도 권장하지 않는다. 검증 전 후보와 검증 후 계획의 타입 경계가 사라지기 때문이다.

```java
import tools.jackson.databind.JsonNode;

public record PlanStepCandidate(
        String stepId,
        ToolType toolType,
        JsonNode input,
        List<String> dependsOn
) {
}

public record PlanStep(
        String stepId,
        ToolType toolType,
        ToolInput input,
        List<String> dependsOn
) {
}
```

검증기는 `toolType`을 기준으로 후보 `JsonNode`를 정확한 record로 변환한다.

```java
ToolInput validatedInput = switch (candidate.toolType()) {
    case SEARCH_DISCLOSURES -> readAndValidate(candidate.input(), SearchDisclosuresInput.class);
    case LOOKUP_FACTS -> readAndValidate(candidate.input(), LookupFactsInput.class);
    case SEARCH_EVIDENCE -> readAndValidate(candidate.input(), SearchEvidenceInput.class);
    case RESOLVE_DISCLOSURE_HISTORY -> readAndValidate(candidate.input(), ResolveDisclosureHistoryInput.class);
    case CALCULATE -> readAndValidate(candidate.input(), CalculateInput.class);
};
```

이 문서에서 `readAndValidate`는 설명용 이름이다. 아직 현재 코드에 존재한다고 간주하지 않는다.

`JsonNode`를 쓴다는 것만으로 엄격한 검증이 되지는 않는다. 현재 프로젝트는 Jackson 3의 `tools.jackson.databind.JsonNode`를 사용한다. validator는 도구별 strict reader 또는 그와 동등한 검사를 적용해 다음을 거부해야 한다.

- object가 아닌 `null`, array, string, number, boolean input
- 알 수 없는 필드
- 문자열과 숫자 사이의 자동 coercion
- 실수에서 정수로의 자동 변환
- 필수 필드의 누락과 명시적 `null`

그 뒤 collection 크기, 문자열 trim 후 공백 여부, enum·fact key allowlist, step 참조 같은 의미 검증을 별도로 수행한다. `int limit`처럼 누락 시 `0`이 될 수 있는 필드는 record 변환 전에 presence를 확인하고 승인된 기본값을 적용하거나 후보 변환용 nullable 타입을 사용한다.

검증된 계획을 JSONB로 저장했다가 다시 읽는다면 `ToolInput` 인터페이스만으로는 concrete subtype이 자동 복원되지 않는다. 저장본의 `tool`을 기준으로 같은 dispatcher를 다시 사용하거나, 검증된 계획은 in-memory에서만 쓰고 원본 후보 JSON과 실행 결과를 별도로 저장하는 정책을 정해야 한다. `sealed`는 이 역직렬화 문제를 해결하지 않는다.

## 2. `input`에 포함할 것과 포함하지 않을 것

### 포함할 것

- 해당 도구만 사용하는 검색 조건
- 이전 단계 출력을 가리키는 `stepId` 참조
- 요청할 fact key 목록
- 계산 연산과 피연산 fact binding
- 도구별 안전한 결과 개수 제한

### 포함하지 않을 것

- `QuestionPlan.companies`: 계획의 전역 조건이므로 step마다 복제하지 않는다.
- `QuestionPlan.time`: 접수기간·보고기간·`asOf`도 전역 조건으로 둔다.
- 실제 `disclosureId`, `factId`, evidence 본문: 이전 단계가 실행된 뒤에만 생기는 결과다.
- 금액·비율 등 LLM이 만든 원시 숫자: 계산은 검증된 fact ID로만 수행한다.
- JPA Entity, SQL, 테이블명, URL, repository 객체
- 반올림·허용 오차·완료 판정·금지 표현: 역할 C의 versioned `AnswerPolicy` 영역이다.
- 도구 출력 형식: 역할 B가 제공하는 runtime result 계약의 영역이다.

`TOOL_CONTRACTS.md` 5장의 JSON은 독립적인 도구 호출에 필요한 전체 조건을 보여준다. 이를 `PlanStep.input`에 그대로 복사하면 기업·시간 조건이 중복되고, 아직 존재하지 않는 실제 ID까지 HCX가 만들게 된다. 계획 실행기는 다음 세 정보를 합쳐 runtime command를 만든다.

```text
QuestionPlan 전역 조건
        +
현재 PlanStep.input
        +
dependsOn 단계의 실제 출력
        =
도구별 runtime command
```

첫 슬라이스는 전역 기업 하나와 하나의 검색 범위를 모든 step이 공유한다고 제한한다. 이후 다중 기업 계획에서 일부 기업만 사용하는 step이 필요해지면, 실제 ID를 복제하지 말고 `QuestionPlan.companies` 안의 항목을 가리키는 검증된 selector를 추가한다. 현재 최소 input에는 그 기능이 없다.

## 3. 첫 수직 슬라이스에서 바로 필요한 타입

첫 대상은 SK하이닉스 `신규시설투자등` 공시 `20240424800596`이다. 이 슬라이스에서 반드시 구현할 step input은 세 개면 충분하다.

### 3.1 `SEARCH_DISCLOSURES`

```java
public record SearchDisclosuresInput(
        List<DisclosureCategory> categories,
        List<String> subtypes,
        List<String> titleTerms,
        int limit
) implements ToolInput {
}
```

| 필드 | 의미 | 첫 슬라이스 검증 규칙 |
|---|---|---|
| `categories` | 공시 대분류 | 비어 있지 않아야 하며 허용 enum만 사용. 예: `EXCHANGE` |
| `subtypes` | 세부 공시 유형 | 승인된 subtype만 허용. 예: `신규시설투자등` |
| `titleTerms` | 제목 검색어 | 선택값. 본문 검색어를 넣지 않는다. |
| `limit` | 최대 후보 문서 수 | `1..50`. 기본값을 허용한다면 validator가 명시적으로 적용하고 warning에 기록한다. |

기업 ID와 시간 조건은 이 record에 넣지 않는다. 실행 시 `QuestionPlan.companies`와 `QuestionPlan.time`에서 가져온다.

`TOOL_CONTRACTS.md`의 `correctionPolicy`는 목표 도구 계약에는 필요하지만, 첫 슬라이스에서는 서버 정책 `ALL`로 고정하는 편이 안전하다. 질문에 따른 정책 선택이 실제로 필요해지면 enum 필드로 승격한다. HCX가 임의 문자열로 정정 정책을 만들게 해서는 안 된다.

`docGroups`, `categories`, `disclosureTypes`라는 세 이름이 현재 문서와 코드에서 혼용된다. 이 문서에서는 현재 도메인 enum `DisclosureCategory`에 맞춰 계획 필드명을 `categories`로 사용한다. runtime adapter에서 `DisclosureSearchCommand.disclosureTypes`로 변환한다.

외부 계약 예시는 `exchange`, Java enum은 `EXCHANGE`를 사용한다. wire schema를 Java enum literal로 고정하거나, 승인된 alias를 canonical enum으로 정규화한 뒤 warning을 남겨야 한다. 기본 Jackson 대소문자 coercion에 기대지 않는다.

### 3.2 `LOOKUP_FACTS`

```java
public record LookupFactsInput(
        String disclosureIdsFrom,
        List<String> factKeys
) implements ToolInput {
}
```

| 필드 | 의미 | 첫 슬라이스 검증 규칙 |
|---|---|---|
| `disclosureIdsFrom` | 공시 ID를 제공할 이전 step ID | 존재하는 `SEARCH_DISCLOSURES` step이어야 하며 `dependsOn`에도 포함돼야 한다. |
| `factKeys` | 조회할 정형 사실 종류 | 역할 C가 승인한 key만 허용하며 중복·빈 문자열을 거부한다. |

`disclosureIdsFrom`에는 실제 UUID 목록이 아니라 `s1` 같은 step ID가 온다. 실행기가 `s1`의 결과에서 실제 공시 ID를 꺼낸다.

첫 슬라이스의 후보 fact key는 다음 여덟 개다.

```text
facility.target
facility.amount
facility.equity_amount
facility.equity_ratio
facility.purpose
facility.start_date
facility.end_date
facility.decision_date
```

다만 `facility.*`와 기존 `investment.*`의 namespace 통합은 아직 역할 B·C 승인 전이다. 승인되기 전에는 alias를 묵시적으로 만들거나 두 namespace에 중복 저장하지 않는다.

독립 도구 계약에 있는 `accountingBasis`는 시설투자 사건 fact에는 적용되지 않으므로 첫 슬라이스 input에서 제외한다. `effectiveOnly`도 HCX 선택값으로 받지 않고, 조회기는 `VERIFIED` fact만 반환하며 이력·`asOf` 정책을 서버에서 일관되게 적용한다. 이후 재무 fact를 지원할 때 연결/별도와 기간 기준을 구분하는 enum 또는 구조 selector를 추가해야 한다.

### 3.3 `CALCULATE`

```java
public record CalculateInput(
        String factsFrom,
        CalculationOperation operation,
        List<String> inputBindings
) implements ToolInput {
}
```

| 필드 | 의미 | 첫 슬라이스 검증 규칙 |
|---|---|---|
| `factsFrom` | fact를 제공할 이전 step ID | 존재하는 `LOOKUP_FACTS` step이어야 하며 `dependsOn`에도 포함돼야 한다. |
| `operation` | 결정적 계산 종류 | 첫 슬라이스에서는 `RATIO`만 허용한다. |
| `inputBindings` | 이전 결과에서 고를 fact key | `RATIO`에서는 정확히 `[분자 factKey, 분모 factKey]` 두 개여야 한다. |

첫 슬라이스의 비율 계산은 다음처럼 표현한다.

```json
{
  "factsFrom": "s2",
  "operation": "RATIO",
  "inputBindings": [
    "facility.amount",
    "facility.equity_amount"
  ]
}
```

`inputBindings`는 숫자 값을 담지 않는다. 실행기는 `s2`가 반환한 fact 중 binding에 해당하고 `VERIFIED`인 fact ID만 골라 calculator command를 만든다. 분모가 0이거나 값·단위·기준이 호환되지 않으면 계산 불가로 처리한다.

이 단순한 `List<String>` binding은 **단일 기업·단일 대상 공시인 첫 슬라이스에만** 사용한다. 각 binding은 실행 시 정확히 하나의 `VERIFIED` fact로 해소돼야 한다. 같은 fact key의 후보가 여러 회사·기간·공시에서 둘 이상 나오거나 하나도 없으면 임의 선택하지 않고 `AMBIGUOUS_FACT_BINDING` 또는 동등한 구조화 오류로 계산을 중단한다.

다중 기업·다중 기간 계산을 추가할 때는 `factKey`만으로 부족하다. 그때는 company, disclosure, period, accounting basis 등을 포함하는 구조화 selector로 교체한다. `TOOL_CONTRACTS.md`의 `{factKey, period}` 예시는 그 확장 방향이지 현재 확정 Java 타입은 아니다.

반올림 방식과 공시 기재 비율 대비 허용 오차는 `CalculateInput`에 넣지 않는다. 동일 질문이 planner의 임의 선택에 따라 다른 결과를 내지 않도록 역할 C의 `AnswerPolicy`에서 버전으로 고정한다.

## 4. 조건부로 추가할 타입

다음 두 도구는 5개 도구 계약에는 포함되지만, 첫 수직 슬라이스의 기본 3-step 경로에는 필수가 아니다. 실제 실행 경로와 역할 B의 출력 계약이 준비될 때 구현한다.

### 4.1 `SEARCH_EVIDENCE`

```java
public record SearchEvidenceInput(
        String disclosureIdsFrom,
        List<String> factKeys,
        List<String> sectionHints,
        List<String> keywords,
        List<String> blockTypes,
        int topK
) implements ToolInput {
}
```

| 필드 | 의미 |
|---|---|
| `disclosureIdsFrom` | 검색 범위를 제공하는 이전 공시 검색 step |
| `factKeys` | 누락된 정형 사실 또는 근거를 찾을 fact key |
| `sectionHints` | 우선 탐색할 장·절 이름 |
| `keywords` | 본문·표에서 찾을 표현 |
| `blockTypes` | 허용할 근거 블록 종류 |
| `topK` | 반환할 근거 후보 상한 |

현재 `DisclosureContentBlockType`에는 `TABLE_ROW`와 `SECTION`이 없고 대신 `HEADING`이 있다. 목표 문서는 표의 행 단위 근거와 `SECTION`을 요구한다. 따라서 `TABLE_ROW` 지원과 `SECTION`↔`HEADING` 의미·매핑을 역할 B와 합의하기 전에는 `blockTypes`의 최종 enum을 확정할 수 없다. 임시로 문자열을 사용하더라도 validator의 allowlist를 통과한 값만 검증 계획에 넣어야 한다.

범위 없는 전체 본문 검색을 막기 위해 `factKeys`, `sectionHints`, `keywords` 중 최소 하나는 비어 있지 않아야 한다. 모든 문자열은 trim 후 빈 값과 중복을 거부하고 `blockTypes`와 `topK`에는 allowlist와 서버 상한을 적용한다.

별도 승인된 concept taxonomy가 없으므로 `concepts` 필드는 지금 추가하지 않는다. taxonomy와 검색 효과가 검증되면 추가한다.

### 4.2 `RESOLVE_DISCLOSURE_HISTORY`

```java
public record ResolveDisclosureHistoryInput(
        String seedDisclosureIdsFrom,
        List<String> relationTypes
) implements ToolInput {
}
```

| 필드 | 의미 |
|---|---|
| `seedDisclosureIdsFrom` | 이력을 확장할 최초 공시 집합을 제공하는 이전 step |
| `relationTypes` | 탐색할 허용 관계 종류 |

`asOf`는 `QuestionPlan.time.asOf`를 사용한다. HCX가 회사 ID·상대방·사건 제목을 조합해 임의 `eventKey`를 확정하게 하지 않는다. 사건 식별자가 필요하면 역할 B의 검색 결과나 별도 검증된 사건 참조에서 만들어야 한다.

위 두 필드만으로 충분하려면 history resolver가 seed 공시에서 사건 키를 추출하고 원접수번호·정정표·관련공시를 이용해 검증한다는 역할 B 계약이 필요하다. runtime command가 사전 계산된 사건 키를 반드시 요구한다면 계획 input에도 자유 JSON `eventKey`가 아니라 검증된 `eventKeyFrom`/`EventRef` 경계를 추가해야 한다. 어느 방식인지는 아직 `DECISION_REQUIRED`이므로 이 record를 구현 완료 계약으로 보지 않는다.

현재 작업 트리의 `ResolveDisclosureHistoryInput`은 `categories`, `subtypes`, `titleTerms`, `limit`를 갖고 있는데, 이는 공시 검색 입력과 같은 모양이라 도구 책임과 맞지 않는다. 복사 과정에서 잘못 들어간 것으로 보이지만 원인은 확인되지 않았으므로 이 문서에서는 **계약 불일치**로만 기록한다.

## 5. 첫 슬라이스의 전체 step 예시

기업과 시간 조건이 이미 `QuestionPlan` 상위 필드에 있다고 가정한 예시다.

```json
[
  {
    "stepId": "s1",
    "tool": "SEARCH_DISCLOSURES",
    "dependsOn": [],
    "input": {
      "categories": ["EXCHANGE"],
      "subtypes": ["신규시설투자등"],
      "titleTerms": [],
      "limit": 10
    }
  },
  {
    "stepId": "s2",
    "tool": "LOOKUP_FACTS",
    "dependsOn": ["s1"],
    "input": {
      "disclosureIdsFrom": "s1",
      "factKeys": [
        "facility.target",
        "facility.amount",
        "facility.equity_amount",
        "facility.equity_ratio",
        "facility.purpose",
        "facility.start_date",
        "facility.end_date",
        "facility.decision_date"
      ]
    }
  },
  {
    "stepId": "s3",
    "tool": "CALCULATE",
    "dependsOn": ["s2"],
    "input": {
      "factsFrom": "s2",
      "operation": "RATIO",
      "inputBindings": [
        "facility.amount",
        "facility.equity_amount"
      ]
    }
  }
]
```

일반 fact 조회 질문에서는 계산이 필요하지 않으면 `s3`를 생략할 수 있다. 그러나 공시 `20240424800596`의 첫 수직 슬라이스 완료 기준에는 투자금액/자기자본 비율 재계산이 명시돼 있으므로 이 acceptance 경로에서는 `s3`가 필수다. planner가 그 밖의 모든 질문에 계산 step을 기계적으로 넣어서는 안 된다.

wire JSON의 도구 필드명은 상위 목표 문서에 맞춰 `tool`을 사용했다. 현재 Java record 필드명은 `toolType`이다. Java 필드를 `tool`로 바꾸거나 명시적 JSON property mapping을 둘지 결정해야 하며, 두 이름을 아무 설정 없이 섞어 쓰면 안 된다.

## 6. 후보를 검증 계획으로 바꾸는 규칙

계획 validator는 최소한 다음을 검사해야 한다.

1. 지원하는 `schemaVersion`과 `ToolType`인지 확인한다.
2. `toolType`에 맞는 input record로만 역직렬화한다.
3. 알 수 없는 필드와 누락된 필수 필드를 거부한다.
4. `stepId` 중복, 존재하지 않는 참조, 자기 참조, 순환 의존성을 거부한다.
5. `*From`의 step ID가 `dependsOn`에 있고 예상한 출력 종류를 제공하는지 확인한다.
6. `categories`, subtype, fact key, operation, relation type, block type을 allowlist로 확인한다.
7. `limit`, `topK`, 전체 step 수를 서버 상한 안으로 제한한다.
8. 계획 검증 시 `RATIO`가 정확히 두 binding을 가지며 역할 C가 허용한 fact key 조합인지 확인한다.
9. `*From` 값은 알려진 step ID와 정확히 일치하고, 그 source tool의 출력 종류가 소비 도구와 호환돼야 한다. UUID처럼 보이는지 추측하는 휴리스틱은 사용하지 않는다.
10. 안전한 기본값 적용이나 상한 축소가 있었다면 `QuestionPlan.warnings`에 남긴다.

retrieval 이후 calculator의 runtime guard는 별도로 다음을 검사한다.

- 실제 입력이 `VERIFIED` fact인지
- 각 binding이 정확히 하나의 fact로 해소되는지
- 기간·회계기준·통화·단위가 호환되는지
- null 또는 0인 분모인지

첫 슬라이스에서는 구현을 단순하게 유지하기 위해 `dependsOn`이 현재 step보다 앞선 index만 참조하도록 제한한다. 일반 DAG와 위상 정렬이 실제로 필요해질 때 그 제약을 완화한다.

검증을 마친 record의 `List`도 원본 mutable list를 그대로 보관하면 변경될 수 있다. compact constructor 또는 계획 생성 시점에 `List.copyOf`로 방어 복사해 검증 후 계획을 불변 snapshot으로 만든다.

검증 실패 시 다른 의미의 step으로 바꾸거나 질문에 없던 도구를 추가하지 않는다. 후보를 실행하지 않고 구조화된 오류 또는 답변 한계로 처리한다.

## 7. 실행 시 실제 값으로 바꾸는 위치

| 계획 step | 계획 input | 실행기가 추가로 결합할 값 | runtime 결과 |
|---|---|---|---|
| `SEARCH_DISCLOSURES` | 분류·subtype·제목·limit | 현재 search command가 소비하는 전역 기업과 접수기간 | 실제 disclosure ID 목록 |
| `LOOKUP_FACTS` | `disclosureIdsFrom`, fact keys | 참조 step의 실제 disclosure ID | evidence가 연결된 fact 목록 |
| `SEARCH_EVIDENCE` | 참조·검색 힌트·`topK` | 실제 disclosure ID와 지원 블록 타입 | evidence 목록 |
| `RESOLVE_DISCLOSURE_HISTORY` | seed 참조·관계 타입 | 실제 disclosure ID와 전역 `asOf` | 사건 이력과 시점 상태 |
| `CALCULATE` | fact 참조·연산·binding | 참조 step의 `VERIFIED` fact ID와 AnswerPolicy | 결정적 계산 결과 |

각 runtime command는 `QuestionPlan` 전역 조건 중 자신에게 필요한 부분집합만 소비한다. 계획 DTO가 runtime command와 같을 필요는 없다.

현재 `DisclosureSearchCommand`는 `companyIds`, `from`, `to`, `disclosureTypes`, `keywords`, `limit`만 받는다. 계획 input의 `subtypes`, 제목 전용 조건, report period, `asOf`, correction policy를 손실 없이 표현할 수 없고 `keywords`의 제목/본문 의미도 불명확하다. 따라서 현재 command에 단순 매핑이 완료됐다고 볼 수 없다. 첫 실행 전에 역할 B 포트를 확장할지, 지원하지 않는 조건을 첫 슬라이스에서 명시적으로 금지할지 결정해야 한다.

## 8. 역할별 책임

### 역할 A

- 후보의 `JsonNode input` 경계
- 도구별 input record와 step 참조 방식
- `ToolType`과 input 타입의 대응
- 의존성, allowlist, 상한, 기본값 검증
- 검증된 `QuestionPlan` 생성
- 계획 input을 runtime command로 바꾸는 orchestration 경계

### 역할 B

- 실제 검색·fact·evidence·history 결과 DTO
- 저장소의 ID 타입과 조회 가능 필드
- 지원 가능한 subtype, block type, history relation
- `QuestionPlan`의 전역 조건과 input을 실제 query command로 매핑하는 데 필요한 포트 계약

### 역할 C

- 허용 fact key와 각 key의 의미·자료형·단위
- 질문별 필수·선택 fact
- 허용 계산과 operand 의미
- 반올림·허용 오차·완료/부분/답변 불가 기준
- claim별 최소 evidence와 금지 표현

역할 C의 정책이 완성되지 않아도 역할 A는 input의 **구조**와 변환 경계를 구현할 수 있다. 다만 `factKeys`, `operation`, binding 조합의 최종 allowlist는 역할 C의 승인 없이는 확정할 수 없다.

## 9. `ToolInput`을 `sealed`로 만들지 않는 이유

현재는 다음처럼 단순 marker interface면 충분하다.

```java
public interface ToolInput {
}
```

도구 종류는 `ToolType`에 이미 고정되어 있고 validator의 `switch`에서 타입 대응을 명시할 수 있다. `sealed`는 다음 요구가 생겼을 때 도입해도 늦지 않다.

- 컴파일러가 모든 하위 타입을 빠짐없이 처리했는지 강제해야 할 때
- pattern matching `switch`를 `ToolInput` 기준으로 수행할 때
- 허용 구현체가 같은 모듈/패키지에만 있어야 한다는 제약이 실제 안전성에 필요할 때

지금 `sealed`를 사용하면 얻는 이점은 작고, permits 목록과 패키지·모듈 제약만 추가된다. 따라서 첫 슬라이스에서는 일반 interface를 유지한다.

## 10. 현재 코드와의 차이

2026-08-27 작업 트리에서 확인한 상태다.

| 항목 | 현재 상태 | 필요한 조치 |
|---|---|---|
| `PlanStepCandidate.input` | 전체가 `String` | `JsonNode`로 교체 |
| `PlanStep.input` | `ToolInput` | 방향은 일치 |
| `ToolInput` | 일반 interface | 그대로 사용 가능 |
| `SearchDisclosuresInput` | 빈 record | 3.1의 최소 필드 추가 |
| `LookupFactsInput` | 참조 + fact keys | 첫 슬라이스 방향과 일치 |
| `SearchEvidenceInput` | 빈 record | 실제 경로 도입 시 4.1 계약 확정 |
| `ResolveDisclosureHistoryInput` | 검색 도구와 같은 필드 | 4.2의 이력 참조 구조로 교체 필요 |
| `CalculateInput` | 참조 + `RATIO` binding | 첫 슬라이스 방향과 일치 |
| `CalculationOperation` | `RATIO`만 존재 | 첫 슬라이스에는 충분, 이후 C 정책에 따라 확장 |
| `PlanTime` | 접수·보고기간 `DateRange`, `asOf` `LocalDate` | 적용 완료 |
| 회사 ID | 계획은 `Long`, 검색 command는 `UUID` | 역할 A·B가 하나의 내부 ID 타입으로 합의 필요 |
| search command | subtype·report period·`asOf`·correction policy를 표현하지 못하고 `keywords` 의미가 모호함 | 손실 없는 포트 계약 또는 첫 슬라이스 제한 결정 필요 |
| wire 도구 필드 | 목표 문서는 `tool`, Java record는 `toolType` | rename 또는 명시적 JSON mapping 필요 |
| evidence block | 목표의 `SECTION`, `TABLE_ROW`와 현재 `HEADING`, `TABLE` 중심 enum이 불일치 | 역할 B 계약과 함께 결정 필요 |

## 11. 아직 결정해야 하는 것

다음 항목은 이 문서만으로 확정할 수 없다.

1. `ResolvedCompanyRef.companyId`와 `DisclosureSearchCommand.companyIds`의 최종 ID 타입
2. `facility.*`와 `investment.*` 중 표준 fact namespace
3. `TABLE_ROW`를 저장·검색 단위로 지원할지와 최종 block type enum
4. `RATIO` 이후 허용할 calculation operation과 operation별 binding 규칙
5. 질문에 따른 정정 정책 선택을 planner에 노출할 시점
6. history resolver가 사건 키를 seed에서 검증할지, 검증된 `EventRef` 입력을 별도로 받을지
7. HCX wire 필드 `tool`과 Java 필드 `toolType`의 매핑 방식
8. 현재 `DisclosureSearchCommand`를 확장할지, 표현할 수 없는 검색 조건을 첫 슬라이스에서 금지할지
9. 다중 기업 계획에서 step별 기업 subset selector를 언제 도입할지

이 결정을 기다리는 동안 임의 alias, 자동 타입 변환, 자유 문자열 fallback을 추가하지 않는다.

## 12. 권장 구현 순서

1. `PlanStepCandidate.input`을 `String`에서 `JsonNode`로 교체한다.
2. 첫 슬라이스의 `SearchDisclosuresInput`, `LookupFactsInput`, `CalculateInput`만 완성한다.
3. `ToolType`별 strict 역직렬화와 validation을 하나의 validator에 구현한다.
4. `s1 → s2 → s3` 후보가 구체 `ToolInput`을 가진 `QuestionPlan`으로 변환되는 테스트를 작성한다.
5. 잘못된 타입, 알 수 없는 필드, 누락 참조, 순환, 원시 숫자가 거부되는 테스트를 작성한다.
6. 역할 B의 fake retriever/calculator와 연결해 공시 `20240424800596` 수직 경로를 검증한다.
7. 실제 실행 경로가 필요해질 때만 `SEARCH_EVIDENCE`와 `RESOLVE_DISCLOSURE_HISTORY` input을 확정한다.

도구별 executor interface, registry, factory는 첫 실행 구현이 하나뿐인 동안 추가하지 않는다.

## 13. 완료 기준

- HCX 후보 JSON이 문자열 재파싱 없이 `JsonNode`로 보존된다.
- validator 통과 후 각 `PlanStep.input`의 실제 클래스가 `toolType`과 일치한다.
- 전역 기업·시간 조건이 각 step input에 중복되지 않는다.
- 이전 단계 결과는 실제 ID가 아니라 명시적인 step 참조로 표현된다.
- 계산 input에는 원시 숫자가 없고 검증된 fact ID만 runtime에 전달된다.
- 역할 C가 승인하지 않은 fact key·연산·binding 조합은 실행되지 않는다.
- 첫 수직 슬라이스에 필요하지 않은 추상화나 도구 구현이 추가되지 않는다.

## 14. 근거 문서와 코드

- [요구사항 정의서](./요구사항_정의서.md): `FR-PLAN-003`~`007`, 5개 도구의 책임
- [기능명세서](./기능명세서.md): 질문 계획 예시, 후보/검증 계획 분리, 도구별 입력·출력
- [역할 A 명세](./ROLE_A_SPEC.md): 도구별 구조화 input, validator 책임, 첫 슬라이스 seam
- [역할 A의 역할 C 요청](./ROLE_A_TO_C_REQUEST.md): 첫 슬라이스 fact와 C의 정책 결정 항목
- [도구 계약](./TOOL_CONTRACTS.md): 독립 도구 호출 계약, evidence 위치, 계산 안전 규칙
- [`PlanStepCandidate`](../backend/src/main/java/com/foliolens/backend/question/plan/candidate/PlanStepCandidate.java)
- [`PlanStep`](../backend/src/main/java/com/foliolens/backend/question/plan/confirmation/PlanStep.java)
- [`ToolInput` 패키지](../backend/src/main/java/com/foliolens/backend/question/plan/toolinput/ToolInput.java)
- [`DisclosureSearchCommand`](../backend/src/main/java/com/foliolens/backend/disclosure/DisclosureSearchCommand.java)
