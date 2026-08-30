package com.foliolens.backend.calculation;

import com.foliolens.backend.question.plan.toolinput.CalculationOperation;

public record CalculationCommand(CalculationOperation operation, ComparisonBasis comparisonBasis) {
}
