package com.foliolens.backend.disclosure.infrastructure.parsing.validation;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosurePathResolver;
import com.foliolens.backend.disclosure.infrastructure.parsing.DartXmlDisclosureParser;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureDocument;
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

@Service
public class XmlParsingValidationBatchService {

    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosurePathResolver pathResolver;
    private final DartXmlDisclosureParser parser;
    private final XmlParsingValidationMetricsCollector metricsCollector;

    public XmlParsingValidationBatchService(
            DisclosureDocumentRepository documentRepository,
            DisclosurePathResolver pathResolver,
            DartXmlDisclosureParser parser,
            XmlParsingValidationMetricsCollector metricsCollector
    ) {
        this.documentRepository = documentRepository;
        this.pathResolver = pathResolver;
        this.parser = parser;
        this.metricsCollector = metricsCollector;
    }

    public XmlParsingValidationBatchResult validate(
            int page,
            int limit
    ) {
        validatePage(page);
        validateLimit(limit);

        Instant startedAt = Instant.now();

        List<DisclosureDocument> documents =
                documentRepository
                        .findAllByContentFormatOrderByIdAsc(
                                DisclosureDocumentContentFormat.DART_XML,
                                PageRequest.of(page, limit)
                        )
                        .getContent();

        if (documents.isEmpty()) {
            throw new IllegalArgumentException(
                    "검증할 DART XML 문서가 없습니다. page=" + page
            );
        }

        List<XmlParsingValidationRow> rows =
                new ArrayList<>(documents.size());

        for (DisclosureDocument document : documents) {
            rows.add(validateDocument(document));
        }

        return new XmlParsingValidationBatchResult(
                startedAt,
                Instant.now(),
                rows
        );
    }

    private XmlParsingValidationRow validateDocument(
            DisclosureDocument document
    ) {
        long startedNanos = System.nanoTime();

        try {
            Path sourceFile = resolveSourceFile(document);

            ParsedDisclosureDocument parsed =
                    parser.parse(sourceFile);

            XmlParsingValidationMetrics metrics =
                    metricsCollector.collect(parsed);

            return XmlParsingValidationRow.success(
                    document,
                    parsed,
                    metrics,
                    elapsedMillis(startedNanos)
            );
        } catch (RuntimeException exception) {
            ErrorDetails error = extractErrorDetails(exception);

            return XmlParsingValidationRow.failed(
                    document,
                    elapsedMillis(startedNanos),
                    error.errorType(),
                    error.errorLine(),
                    error.errorColumn(),
                    error.errorMessage()
            );
        }
    }

    private void validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page는 0 이상이어야 합니다."
            );
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "limit은 1~" + MAX_BATCH_SIZE + " 범위여야 합니다."
            );
        }
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos
        );
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