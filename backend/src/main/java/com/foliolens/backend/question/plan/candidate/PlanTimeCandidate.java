package com.foliolens.backend.question.plan.candidate;

import java.time.Instant;

// 시간 표현이지만, HCX가 시간을 어떻게 뱉을지 알수가 없어서 일단 String으로 받음!
// TODO: HCX가 시간을 어떻게 뱉는지 확인 후 Instant로 바꾸기
public record PlanTimeCandidate(
        String receiptPeriod, //접수기간
        String reportPeriods, //보고기간
        String asOf //기준시점
){
}
