package com.foliolens.backend.disclosure.domain;

import com.foliolens.backend.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "disclosure_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "disclosure_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_disclosure_documents_disclosure"
            )
    )
    private Disclosure disclosure;

    @NotBlank
    @Column(name = "relative_path", nullable = false)
    private String relativePath;

    @NotBlank
    @Column(
            name = "normalized_relative_path",
            nullable = false,
            unique = true
    )
    private String normalizedRelativePath;

    @NotBlank
    @Size(max = 500)
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @NotBlank
    @Size(max = 10)
    @Pattern(regexp = "^(xml|html|pdf)$")
    @Column(name = "file_extension", nullable = false, length = 10)
    private String fileExtension;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "document_role", nullable = false, length = 30)
    private DisclosureDocumentRole documentRole;

    @Size(max = 500)
    @Column(name = "document_name", length = 500)
    private String documentName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "content_format", nullable = false, length = 20)
    private DisclosureDocumentContentFormat contentFormat;

    @NotNull
    @PositiveOrZero
    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @NotBlank
    @Pattern(regexp = "^[0-9a-f]{64}$")
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 20)
    private DisclosureDocumentParseStatus parseStatus;

    @Size(max = 100)
    @Column(name = "parser_name", length = 100)
    private String parserName;

    @Size(max = 50)
    @Column(name = "parser_version", length = 50)
    private String parserVersion;

    @Column(name = "parse_error_message")
    private String parseErrorMessage;

    @Column(name = "parsed_at")
    private Instant parsedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "chunk_status", nullable = false, length = 20)
    private DisclosureDocumentChunkStatus chunkStatus;

    @Size(max = 100)
    @Column(name = "chunk_generator_name", length = 100)
    private String chunkGeneratorName;

    @Size(max = 100)
    @Column(name = "chunk_generator_version", length = 100)
    private String chunkGeneratorVersion;

    @Column(name = "chunk_error_message")
    private String chunkErrorMessage;

    @Column(name = "chunked_at")
    private Instant chunkedAt;

    private DisclosureDocument(
            Disclosure disclosure,
            String relativePath,
            String normalizedRelativePath,
            String fileName,
            String fileExtension,
            DisclosureDocumentRole documentRole,
            String documentName,
            DisclosureDocumentContentFormat contentFormat,
            Long fileSizeBytes,
            String sha256
    ) {
        this.disclosure = Objects.requireNonNull(
                disclosure,
                "disclosure는 필수입니다."
        );

        setFileMetadata(
                relativePath,
                normalizedRelativePath,
                fileName,
                fileExtension,
                documentRole,
                documentName,
                contentFormat,
                fileSizeBytes,
                sha256
        );

        resetParsing();
    }

    public static DisclosureDocument create(
            Disclosure disclosure,
            String relativePath,
            String normalizedRelativePath,
            String fileName,
            String fileExtension,
            DisclosureDocumentRole documentRole,
            String documentName,
            DisclosureDocumentContentFormat contentFormat,
            Long fileSizeBytes,
            String sha256
    ) {
        return new DisclosureDocument(
                disclosure,
                relativePath,
                normalizedRelativePath,
                fileName,
                fileExtension,
                documentRole,
                documentName,
                contentFormat,
                fileSizeBytes,
                sha256
        );
    }

    /**
     * 같은 파일을 다시 스캔했을 때 파일 정보를 갱신합니다.
     *
     * 파일 내용의 SHA-256이 바뀌었다면 기존 파싱 결과를
     * 더 이상 신뢰할 수 없으므로 상태를 PENDING으로 되돌립니다.
     */
    public void updateFileMetadata(
            String relativePath,
            String normalizedRelativePath,
            String fileName,
            String fileExtension,
            DisclosureDocumentRole documentRole,
            String documentName,
            DisclosureDocumentContentFormat contentFormat,
            Long fileSizeBytes,
            String sha256
    ) {
        String validatedSha256 = validateSha256(sha256);
        boolean contentChanged = !this.sha256.equals(validatedSha256);

        setFileMetadata(
                relativePath,
                normalizedRelativePath,
                fileName,
                fileExtension,
                documentRole,
                documentName,
                contentFormat,
                fileSizeBytes,
                validatedSha256
        );

        if (contentChanged) {
            resetParsing();
        }
    }

    /**
     * 원문 내부의 DOCUMENT-NAME 등을 읽은 후
     * 문서명과 파일 역할을 확정할 때 사용합니다.
     */
    public void updateDocumentClassification(
            DisclosureDocumentRole documentRole,
            String documentName
    ) {
        this.documentRole = Objects.requireNonNull(
                documentRole,
                "documentRole은 필수입니다."
        );
        this.documentName = normalizeNullable(documentName);
    }

    public void markCompleted(
            String parserName,
            String parserVersion,
            Instant parsedAt
    ) {
        this.parseStatus = DisclosureDocumentParseStatus.COMPLETED;
        this.parserName = requireNotBlank(parserName, "parserName");
        this.parserVersion = requireNotBlank(
                parserVersion,
                "parserVersion"
        );
        this.parseErrorMessage = null;
        this.parsedAt = Objects.requireNonNull(
                parsedAt,
                "parsedAt은 필수입니다."
        );
        resetChunking();
    }

    public void markPartial(
            String parserName,
            String parserVersion,
            String errorMessage,
            Instant parsedAt
    ) {
        this.parseStatus = DisclosureDocumentParseStatus.PARTIAL;
        this.parserName = requireNotBlank(parserName, "parserName");
        this.parserVersion = requireNotBlank(
                parserVersion,
                "parserVersion"
        );
        this.parseErrorMessage = requireNotBlank(
                errorMessage,
                "errorMessage"
        );
        this.parsedAt = Objects.requireNonNull(
                parsedAt,
                "parsedAt은 필수입니다."
        );
        resetChunking();
    }

    public void markFailed(
            String parserName,
            String parserVersion,
            String errorMessage,
            Instant parsedAt
    ) {
        this.parseStatus = DisclosureDocumentParseStatus.FAILED;
        this.parserName = requireNotBlank(parserName, "parserName");
        this.parserVersion = requireNotBlank(
                parserVersion,
                "parserVersion"
        );
        this.parseErrorMessage = requireNotBlank(
                errorMessage,
                "errorMessage"
        );
        this.parsedAt = Objects.requireNonNull(
                parsedAt,
                "parsedAt은 필수입니다."
        );
        resetChunking();
    }

    public void resetParsing() {
        this.parseStatus = DisclosureDocumentParseStatus.PENDING;
        this.parserName = null;
        this.parserVersion = null;
        this.parseErrorMessage = null;
        this.parsedAt = null;
        resetChunking();
    }

    public void markChunkingCompleted(
            String generatorName,
            String generatorVersion,
            Instant chunkedAt
    ) {
        String validatedGeneratorName = requireNotBlank(
                generatorName,
                "generatorName"
        );
        String validatedGeneratorVersion = requireNotBlank(
                generatorVersion,
                "generatorVersion"
        );
        Instant validatedChunkedAt = Objects.requireNonNull(
                chunkedAt,
                "chunkedAt은 필수입니다."
        );

        this.chunkStatus = DisclosureDocumentChunkStatus.COMPLETED;
        this.chunkGeneratorName = validatedGeneratorName;
        this.chunkGeneratorVersion = validatedGeneratorVersion;
        this.chunkErrorMessage = null;
        this.chunkedAt = validatedChunkedAt;
    }

    public void markChunkingFailed(
            String generatorName,
            String generatorVersion,
            String errorMessage,
            Instant chunkedAt
    ) {
        String validatedGeneratorName = requireNotBlank(
                generatorName,
                "generatorName"
        );
        String validatedGeneratorVersion = requireNotBlank(
                generatorVersion,
                "generatorVersion"
        );
        String validatedErrorMessage = requireNotBlank(
                errorMessage,
                "errorMessage"
        );
        Instant validatedChunkedAt = Objects.requireNonNull(
                chunkedAt,
                "chunkedAt은 필수입니다."
        );

        this.chunkStatus = DisclosureDocumentChunkStatus.FAILED;
        this.chunkGeneratorName = validatedGeneratorName;
        this.chunkGeneratorVersion = validatedGeneratorVersion;
        this.chunkErrorMessage = validatedErrorMessage;
        this.chunkedAt = validatedChunkedAt;
    }

    public void resetChunking() {
        this.chunkStatus = DisclosureDocumentChunkStatus.PENDING;
        this.chunkGeneratorName = null;
        this.chunkGeneratorVersion = null;
        this.chunkErrorMessage = null;
        this.chunkedAt = null;
    }

    private void setFileMetadata(
            String relativePath,
            String normalizedRelativePath,
            String fileName,
            String fileExtension,
            DisclosureDocumentRole documentRole,
            String documentName,
            DisclosureDocumentContentFormat contentFormat,
            Long fileSizeBytes,
            String sha256
    ) {
        String validatedRelativePath = validateRelativePath(
                relativePath,
                "relativePath"
        );

        String validatedNormalizedPath = validateRelativePath(
                normalizedRelativePath,
                "normalizedRelativePath"
        );

        validateNormalizedPath(
                validatedRelativePath,
                validatedNormalizedPath
        );

        String validatedFileName = requireNotBlank(
                fileName,
                "fileName"
        );

        if (validatedFileName.length() > 500) {
            throw new IllegalArgumentException(
                    "fileName은 500자를 초과할 수 없습니다."
            );
        }

        String validatedExtension = validateFileExtension(
                fileExtension
        );

        validateFileNameAndExtension(
                validatedFileName,
                validatedExtension
        );

        validatePathAndFileName(
                validatedRelativePath,
                validatedFileName
        );

        this.relativePath = validatedRelativePath;
        this.normalizedRelativePath = validatedNormalizedPath;
        this.fileName = validatedFileName;
        this.fileExtension = validatedExtension;
        this.documentRole = Objects.requireNonNull(
                documentRole,
                "documentRole은 필수입니다."
        );
        this.documentName = normalizeNullable(documentName);
        this.contentFormat = Objects.requireNonNull(
                contentFormat,
                "contentFormat은 필수입니다."
        );
        this.fileSizeBytes = validateFileSize(fileSizeBytes);
        this.sha256 = validateSha256(sha256);
    }

    private static String validateRelativePath(
            String value,
            String fieldName
    ) {
        String path = requireNotBlank(value, fieldName);

        if (
                path.startsWith("/")
                        || path.startsWith("\\")
                        || path.matches("^[A-Za-z]:.*")
                        || path.contains("\\")
        ) {
            throw new IllegalArgumentException(
                    fieldName
                            + "는 / 구분자를 사용하는 상대경로여야 합니다: "
                            + path
            );
        }

        String[] segments = path.split("/");

        for (String segment : segments) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(
                        fieldName
                                + "에는 상위 경로(..)를 사용할 수 없습니다: "
                                + path
                );
            }
        }

        return path;
    }

    private static void validateNormalizedPath(
            String relativePath,
            String normalizedRelativePath
    ) {
        String expected = Normalizer.normalize(
                relativePath,
                Normalizer.Form.NFC
        );

        if (!expected.equals(normalizedRelativePath)) {
            throw new IllegalArgumentException(
                    "normalizedRelativePath는 relativePath를 "
                            + "NFC 정규화한 값이어야 합니다."
            );
        }
    }

    private static void validatePathAndFileName(
            String relativePath,
            String fileName
    ) {
        if (
                !relativePath.equals(fileName)
                        && !relativePath.endsWith("/" + fileName)
        ) {
            throw new IllegalArgumentException(
                    "fileName이 relativePath의 마지막 파일명과 일치하지 않습니다."
            );
        }
    }

    private static String validateFileExtension(String value) {
        String extension = requireNotBlank(
                value,
                "fileExtension"
        ).toLowerCase(Locale.ROOT);

        if (
                !"xml".equals(extension)
                        && !"html".equals(extension)
                        && !"pdf".equals(extension)
        ) {
            throw new IllegalArgumentException(
                    "지원하지 않는 파일 확장자입니다: " + extension
            );
        }

        return extension;
    }

    private static void validateFileNameAndExtension(
            String fileName,
            String fileExtension
    ) {
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);

        if (!lowerFileName.endsWith("." + fileExtension)) {
            throw new IllegalArgumentException(
                    "fileName과 fileExtension이 일치하지 않습니다: "
                            + fileName
            );
        }
    }

    private static Long validateFileSize(Long value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(
                    "fileSizeBytes는 0 이상이어야 합니다."
            );
        }

        return value;
    }

    private static String validateSha256(String value) {
        String hash = requireNotBlank(value, "sha256")
                .toLowerCase(Locale.ROOT);

        if (!hash.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException(
                    "sha256은 64자리 16진수 문자열이어야 합니다."
            );
        }

        return hash;
    }

    private static String requireNotBlank(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 필수입니다."
            );
        }

        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
