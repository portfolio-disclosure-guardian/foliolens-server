package com.foliolens.backend.question.plan.confirmation;

import java.time.Instant;

public record PlanTime (
        Instant receiptPeriod, //접수기간
        Instant reportPeriods, //보고기간
        Instant asOf //기준시점
){
}
