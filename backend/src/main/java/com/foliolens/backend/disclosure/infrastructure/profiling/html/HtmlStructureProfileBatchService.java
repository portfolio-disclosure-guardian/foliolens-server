package com.foliolens.backend.disclosure.infrastructure.profiling.html;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosurePathResolver;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * DB에 등록된 실제 HTML 원문을 제한된 개수만 구조 조사한다.
 * 한 파일의 실패는 같은 배치의 다음 파일에 영향을 주지 않는다.
 */
@Service
public class HtmlStructureProfileBatchService {

    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosurePathResolver pathResolver;
    private final HtmlStructureProfiler htmlStructureProfiler;

    public HtmlStructureProfileBatchService(
            DisclosureDocumentRepository documentRepository,
            DisclosurePathResolver pathResolver,
            HtmlStructureProfiler htmlStructureProfiler
    ) {
        this.documentRepository = Objects.requireNonNull(
                documentRepository,
                "documentRepository는 필수입니다."
        );
        this.pathResolver = Objects.requireNonNull(
                pathResolver,
                "pathResolver는 필수입니다."
        );
        this.htmlStructureProfiler = Objects.requireNonNull(
                htmlStructureProfiler,
                "htmlStructureProfiler는 필수입니다."
        );
    }

    /**
     * page는 0부터 시작한다. rawSubtype이 비어 있으면 모든 HTML 문서를,
     * 값이 있으면 해당 공시 원문 유형의 HTML 문서만 조사한다.
     */
    public HtmlStructureProfileBatchResult profile(
            int page,
            int limit,
            String rawSubtype
    ) {
        int pageNumber = validatePage(page);
        int batchSize = validateLimit(limit);
        String subtypeFilter = normalizeFilter(rawSubtype);
        long totalDocumentCount = countTargets(subtypeFilter);

        validatePageRange(
                pageNumber,
                batchSize,
                subtypeFilter,
                totalDocumentCount
        );

        Instant startedAt = Instant.now();
        List<DisclosureDocument> documents = findTargets(
                pageNumber,
                batchSize,
                subtypeFilter
        );
        List<HtmlStructureProfileRow> rows = new ArrayList<>(documents.size());

        for (DisclosureDocument document : documents) {
            rows.add(profileDocument(document));
        }

        return new HtmlStructureProfileBatchResult(
                startedAt,
                Instant.now(),
                rows
        );
    }

    private List<DisclosureDocument> findTargets(
            int page,
            int limit,
            String rawSubtype
    ) {
        PageRequest pageRequest = PageRequest.of(page, limit);
        Slice<DisclosureDocument> slice;
        if (rawSubtype == null) {
            slice = documentRepository.findAllByContentFormatOrderByIdAsc(
                    DisclosureDocumentContentFormat.HTML,
                    pageRequest
            );
        } else {
            slice = documentRepository
                    .findAllByContentFormatAndDisclosure_RawSubtypeOrderByIdAsc(
                            DisclosureDocumentContentFormat.HTML,
                            rawSubtype,
                            pageRequest
                    );
        }
        return slice.getContent();
    }

    private long countTargets(String rawSubtype) {
        if (rawSubtype == null) {
            return documentRepository.countByContentFormat(
                    DisclosureDocumentContentFormat.HTML
            );
        }
        return documentRepository
                .countByContentFormatAndDisclosure_RawSubtype(
                        DisclosureDocumentContentFormat.HTML,
                        rawSubtype
                );
    }

    private HtmlStructureProfileRow profileDocument(
            DisclosureDocument document
    ) {
        long startedNanos = System.nanoTime();
        try {
            Path sourceFile = resolveSourceFile(document);
            HtmlStructureProfile profile = htmlStructureProfiler.profile(
                    sourceFile
            );
            validateFileSize(document, profile);
            return HtmlStructureProfileRow.success(
                    document,
                    profile,
                    elapsedMillis(startedNanos)
            );
        } catch (RuntimeException exception) {
            Throwable deepestCause = deepestCause(exception);
            return HtmlStructureProfileRow.failed(
                    document,
                    elapsedMillis(startedNanos),
                    deepestCause.getClass().getSimpleName(),
                    normalizeErrorMessage(deepestCause)
            );
        }
    }

    private Path resolveSourceFile(DisclosureDocument document) {
        Disclosure disclosure = Objects.requireNonNull(
                document.getDisclosure(),
                "DisclosureDocument의 disclosure는 필수입니다."
        );
        Path disclosureDirectory = pathResolver
                .resolveDirectory(disclosure.getManifestPath())
                .toAbsolutePath()
                .normalize();
        Path sourceFile = disclosureDirectory
                .resolve(document.getFileName())
                .toAbsolutePath()
                .normalize();

        if (sourceFile.getParent() == null
                || !sourceFile.getParent().equals(disclosureDirectory)) {
            throw new IllegalStateException(
                    "원문 파일 경로가 공시 폴더를 벗어납니다. fileName="
                            + document.getFileName()
            );
        }
        if (!Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(
                    "원문 파일이 존재하지 않거나 일반 파일이 아닙니다. path="
                            + sourceFile
            );
        }
        if (!Files.isReadable(sourceFile)) {
            throw new IllegalStateException(
                    "원문 파일을 읽을 수 없습니다. path=" + sourceFile
            );
        }

        validateRelativePath(document, sourceFile);
        return sourceFile;
    }

    private void validateRelativePath(
            DisclosureDocument document,
            Path sourceFile
    ) {
        String actualRelativePath = pathResolver.toDatasetRelativePath(
                sourceFile
        );
        String actualNormalizedPath = pathResolver.normalizeRelativePath(
                actualRelativePath
        );
        if (!Objects.equals(
                document.getNormalizedRelativePath(),
                actualNormalizedPath
        )) {
            throw new IllegalStateException(
                    "DB 경로와 실제 원문 파일 경로가 다릅니다. databasePath="
                            + document.getNormalizedRelativePath()
                            + ", actualPath=" + actualNormalizedPath
            );
        }
    }

    private void validateFileSize(
            DisclosureDocument document,
            HtmlStructureProfile profile
    ) {
        if (!Objects.equals(
                document.getFileSizeBytes(),
                profile.fileSizeBytes()
        )) {
            throw new IllegalStateException(
                    "DB 파일 크기와 실제 파일 크기가 다릅니다. fileName="
                            + document.getFileName()
                            + ", databaseSize=" + document.getFileSizeBytes()
                            + ", actualSize=" + profile.fileSizeBytes()
            );
        }
    }

    private int validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "HTML 구조 조사 페이지는 0 이상이어야 합니다. page=" + page
            );
        }
        return page;
    }

    private int validateLimit(int limit) {
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "HTML 구조 조사 배치 크기는 1~"
                            + MAX_BATCH_SIZE
                            + " 범위여야 합니다. limit=" + limit
            );
        }
        return limit;
    }

    private void validatePageRange(
            int page,
            int limit,
            String rawSubtype,
            long totalDocumentCount
    ) {
        long startIndex = (long) page * limit;
        if (startIndex >= totalDocumentCount) {
            throw new IllegalArgumentException(
                    "요청한 HTML 구조 조사 페이지에 대상 문서가 없습니다. page="
                            + page
                            + ", limit=" + limit
                            + ", rawSubtype="
                            + (rawSubtype == null ? "ALL" : rawSubtype)
                            + ", startIndex=" + startIndex
                            + ", totalDocumentCount=" + totalDocumentCount
            );
        }
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(
                0,
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startedNanos
                )
        );
    }

    private Throwable deepestCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String normalizeErrorMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        String normalized = message.replace('\r', ' ')
                .replace('\n', ' ')
                .strip();
        if (normalized.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
