package com.foliolens.backend.question.plan.toolinput;

import com.foliolens.backend.disclosure.domain.DisclosureCategory;

import java.util.List;

public record SearchDisclosuresInput(List<DisclosureCategory> categories,
                                     List<String> subtypes,
                                     List<String> titleTerms,
                                     int limit) implements ToolInput{
}
