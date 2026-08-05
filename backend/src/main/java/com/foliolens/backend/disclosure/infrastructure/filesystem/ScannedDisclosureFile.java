package com.foliolens.backend.disclosure.infrastructure.filesystem;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;

/**
 * 원문 파일 하나를 스캔한 결과
 */
public record ScannedDisclosureFile(
        String relativePath,
        String normalizedRelativePath,
        String fileName,
        String fileExtension,
        DisclosureDocumentRole documentRole,
        String documentName,
        DisclosureDocumentContentFormat contentFormat,
        long fileSizeBytes,
        String sha256
) {
}
