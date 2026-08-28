package com.foliolens.backend.calculation;

public record CalculationCommand(String externalQuestionId, String question, String answer) {
}
