package com.foliolens.backend.retrieval;

import com.foliolens.backend.question.plan.confirmation.PlanStep;

import java.util.List;

public record RetrievalResult(List<RetrievedDocument> documents,
                              List<RetrievedFact> facts,
                              List<RetrievedEvidence> evidences,
                              List<RetrievedHistoryEvent> history,
                              List<PlanStep> executedSteps,
                              List<String> missingFactKeys,
                              RetrievalCoverage coverage,
                              List<String> warnings,
                              String retrievalVersion) {
}
