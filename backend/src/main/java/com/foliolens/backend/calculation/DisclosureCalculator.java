package com.foliolens.backend.calculation;

import com.foliolens.backend.retrieval.RetrievedFact;

import java.util.List;

public interface DisclosureCalculator {
    CalculationResult calculate(
            CalculationCommand command, List<RetrievedFact> facts
    );
}
