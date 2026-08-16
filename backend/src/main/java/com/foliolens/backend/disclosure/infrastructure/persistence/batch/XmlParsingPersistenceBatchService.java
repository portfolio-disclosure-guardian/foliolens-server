package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentParseStatus;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosurePathResolver;
import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceResult;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.service.DisclosureDocumentParsingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class XmlParsingPersistenceBatchService {

    private static final int MAX_CHUNK_SIZE = 100;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosurePathResolver pathResolver;
    private final DisclosureDocumentParsingService parsingService;

    public XmlParsingPersistenceBatchService(
            DisclosureDocumentRepository documentRepository,
            DisclosurePathResolver pathResolver,
            DisclosureDocumentParsingService parsingService
    ) {
        this.documentRepository = documentRepository;
        this.pathResolver = pathResolver;
        this.parsingService = parsingService;
    }

    /**
     * PENDING 상태의 DART XML을 ID 순서로 제한된 수만큼 저장한다.
     *
     * 처리된 문서는 더 이상 PENDING이 아니므로 페이지를 이동하지 않고
     * 항상 첫 페이지를 읽는다. 다시 실행하면 다음 PENDING 문서가 선택된다.
     */
    public XmlParsingPersistenceBatchResult persistNextChunk(
            int chunkSize
    ) {
        validateChunkSize(chunkSize);

        Instant startedAt = Instant.now();

        List<DisclosureDocument> documents = documentRepository
                .findAllByContentFormatAndParseStatusOrderByIdAsc(
                        DisclosureDocumentContentFormat.DART_XML,
                        DisclosureDocumentParseStatus.PENDING,
                        PageRequest.of(0, chunkSize)
                )
                .getContent();

        List<XmlParsingPersistenceRow> rows =
                new ArrayList<>(documents.size());

        for (DisclosureDocument document : documents) {
            rows.add(persistDocument(document));
        }

        return new XmlParsingPersistenceBatchResult(
                startedAt,
                Instant.now(),
                rows
        );
    }

    private XmlParsingPersistenceRow persistDocument(
            DisclosureDocument document
    ) {
        long startedNanos = System.nanoTime();
        Path sourceFile;

        try {
            sourceFile = resolveSourceFile(document);
        } catch (RuntimeException exception) {
            parsingService.markFailed(document.getId(), exception);

            return XmlParsingPersistenceRow.failed(
                    document,
                    elapsedMillis(startedNanos),
                    extractErrorMessage(exception)
            );
        }

        try {
            DisclosureParsingPersistenceResult result =
                    parsingService.parseAndStore(
                            document.getId(),
                            sourceFile
                    );

            return XmlParsingPersistenceRow.success(
                    document,
                    result,
                    elapsedMillis(startedNanos)
            );
        } catch (RuntimeException exception) {
            return XmlParsingPersistenceRow.failed(
                    document,
                    elapsedMillis(startedNanos),
                    extractErrorMessage(exception)
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
        String actualRelativePath =
                pathResolver.toDatasetRelativePath(sourceFile);

        String actualNormalizedPath =
                pathResolver.normalizeRelativePath(actualRelativePath);

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

    private void validateChunkSize(int chunkSize) {
        if (chunkSize < 1 || chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "chunkSize는 1~"
                            + MAX_CHUNK_SIZE
                            + " 범위여야 합니다."
            );
        }
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos
        );
    }

    private String extractErrorMessage(Throwable throwable) {
        Throwable rootCause = throwable;

        while (
                rootCause.getCause() != null
                        && rootCause.getCause() != rootCause
        ) {
            rootCause = rootCause.getCause();
        }

        String detail = rootCause.getMessage();

        if (detail == null || detail.isBlank()) {
            detail = "상세 오류 메시지가 없습니다.";
        }

        String result = rootCause.getClass().getSimpleName()
                + ": " + detail.trim();

        if (result.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return result;
        }

        return result.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
