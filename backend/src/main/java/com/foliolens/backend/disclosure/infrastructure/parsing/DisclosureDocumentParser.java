package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.nio.file.Path;

/** 원문 형식별 파서가 공유하는 계약. 저장과 네트워크 조회는 수행하지 않는다. */
public interface DisclosureDocumentParser {
    ParsedDisclosureDocument parse(Path sourceFile);
    String parserName();
    String parserVersion();
}
