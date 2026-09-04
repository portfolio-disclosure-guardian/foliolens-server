package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.parsing.validation.XmlParsingValidationMetrics;

public record ValidatedHtmlDocument(
        ParsedDisclosureDocument document,
        XmlParsingValidationMetrics metrics
) {}
