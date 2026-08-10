package com.foliolens.backend.disclosure.infrastructure.profiling;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosurePathResolver;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * DB에 적재된 DART XML 원문을 제한된 개수만 구조 조사한다.
 *
 * 한 파일의 실패가 나머지 파일 조사를 중단시키지 않도록 파일별로 예외를 격리한다.
 */
@Service
public class XmlStructureProfileBatchService {

    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosurePathResolver pathResolver;
    private final XmlStructureProfiler xmlStructureProfiler;

    public XmlStructureProfileBatchService(
            DisclosureDocumentRepository documentRepository,
            DisclosurePathResolver pathResolver,
            XmlStructureProfiler xmlStructureProfiler
    ) {
        this.documentRepository = documentRepository;
        this.pathResolver = pathResolver;
        this.xmlStructureProfiler = xmlStructureProfiler;
    }

    /**
     * DART XML 문서를 ID 오름차순으로 페이지 단위 조사한다.
     *
     * page는 0부터 시작한다.
     * page=0이면 첫 번째 묶음,
     * page=1이면 두 번째 묶음을 조사한다.
     */
    public XmlStructureProfileBatchResult profile(int page, int limit) {
        int pageNumber = validatePage(page);
        int batchSize = validateLimit(limit);

        validatePageRange(pageNumber, batchSize);

        Instant startedAt = Instant.now();

        List<DisclosureDocument> documents =
                documentRepository
                        .findAllByContentFormatOrderByIdAsc(
                                DisclosureDocumentContentFormat.DART_XML,
                                PageRequest.of(pageNumber, batchSize)
                        )
                        .getContent();

        List<XmlStructureProfileRow> rows = new ArrayList<>(documents.size());

        for (DisclosureDocument document : documents) {
            rows.add(profileDocument(document));
        }

        return new XmlStructureProfileBatchResult(
                startedAt,
                Instant.now(),
                rows
        );
    }

    private XmlStructureProfileRow profileDocument(DisclosureDocument document) {
        long startedNanos = System.nanoTime();

        try {
            Path sourceFile = resolveSourceFile(document);
            XmlStructureProfile profile =
                    xmlStructureProfiler.profile(sourceFile);

            validateFileSize(document, profile);

            return XmlStructureProfileRow.success(
                    document,
                    profile,
                    elapsedMillis(startedNanos)
            );
        } catch (RuntimeException exception) {
            ErrorDetails errorDetails = extractErrorDetails(exception);

            return XmlStructureProfileRow.failed(
                    document,
                    elapsedMillis(startedNanos),
                    errorDetails.errorType(),
                    errorDetails.errorLine(),
                    errorDetails.errorColumn(),
                    errorDetails.errorMessage()
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

        if (
                sourceFile.getParent() == null
                        || !sourceFile.getParent()
                        .equals(disclosureDirectory)
        ) {
            throw new IllegalStateException(
                    "원문 파일 경로가 공시 폴더를 벗어납니다. fileName="
                            + document.getFileName()
            );
        }

        if (
                !Files.isRegularFile(
                        sourceFile,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
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
        String actualRelativePath = pathResolver
                .toDatasetRelativePath(sourceFile);
        String actualNormalizedPath = pathResolver
                .normalizeRelativePath(actualRelativePath);

        if (
                !Objects.equals(
                        document.getNormalizedRelativePath(),
                        actualNormalizedPath
                )
        ) {
            throw new IllegalStateException(
                    "DB 경로와 실제 원문 파일 경로가 다릅니다. databasePath="
                            + document.getNormalizedRelativePath()
                            + ", actualPath=" + actualNormalizedPath
            );
        }
    }

    private void validateFileSize(
            DisclosureDocument document,
            XmlStructureProfile profile
    ) {
        if (
                !Objects.equals(
                        document.getFileSizeBytes(),
                        profile.fileSizeBytes()
                )
        ) {
            throw new IllegalStateException(
                    "DB 파일 크기와 실제 파일 크기가 다릅니다. fileName="
                            + document.getFileName()
                            + ", databaseSize="
                            + document.getFileSizeBytes()
                            + ", actualSize="
                            + profile.fileSizeBytes()
            );
        }
    }

    private int validateLimit(int limit) {
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "XML 구조 조사 배치 크기는 1~"
                            + MAX_BATCH_SIZE
                            + " 범위여야 합니다. limit=" + limit
            );
        }

        return limit;
    }

    // 페이지 번호 검증 메서드
    private int validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "XML 구조 조사 페이지 번호는 0 이상이어야 합니다. "
                            + "page=" + page
            );
        }

        return page;
    }

    // 존재하지 않는 페이지 검증
    private void validatePageRange(int page, int limit) {
        long totalDocumentCount =
                documentRepository.countByContentFormat(
                        DisclosureDocumentContentFormat.DART_XML
                );

        long startIndex =
                (long) page * limit;

        if (startIndex >= totalDocumentCount) {
            throw new IllegalArgumentException(
                    "요청한 XML 구조 조사 페이지에 대상 문서가 없습니다. "
                            + "page=" + page
                            + ", limit=" + limit
                            + ", startIndex=" + startIndex
                            + ", totalDocumentCount="
                            + totalDocumentCount
            );
        }
    }

    private long elapsedMillis(long startedNanos) {
        long elapsedNanos = System.nanoTime() - startedNanos;

        return Math.max(
                0,
                TimeUnit.NANOSECONDS.toMillis(elapsedNanos)
        );
    }

    private String summarizeException(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        } else {
            message = exception.getClass().getSimpleName()
                    + ": "
                    + message.replace('\r', ' ')
                    .replace('\n', ' ')
                    .trim();
        }

        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }

        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private ErrorDetails extractErrorDetails(Throwable exception) {
        Throwable current = exception;
        Throwable deepestCause = exception;
        XMLStreamException xmlStreamException = null;

        while (current != null) {
            deepestCause = current;

            if (current instanceof XMLStreamException found) {
                xmlStreamException = found;
            }

            Throwable next = current.getCause();

            if (next == null || next == current) {
                break;
            }

            current = next;
        }

        Integer errorLine = null;
        Integer errorColumn = null;

        if (xmlStreamException != null) {
            Location location = xmlStreamException.getLocation();

            if (location != null) {
                if (location.getLineNumber() > 0) {
                    errorLine = location.getLineNumber();
                }

                if (location.getColumnNumber() > 0) {
                    errorColumn = location.getColumnNumber();
                }
            }
        }

        return new ErrorDetails(
                deepestCause.getClass().getSimpleName(),
                errorLine,
                errorColumn,
                normalizeErrorMessage(deepestCause)
        );
    }

    private String normalizeErrorMessage(Throwable exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        message = message
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();

        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }

        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private record ErrorDetails(
            String errorType,
            Integer errorLine,
            Integer errorColumn,
            String errorMessage
    ) {
    }
}
