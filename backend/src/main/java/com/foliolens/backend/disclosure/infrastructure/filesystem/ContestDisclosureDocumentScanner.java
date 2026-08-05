package com.foliolens.backend.disclosure.infrastructure.filesystem;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class ContestDisclosureDocumentScanner implements DisclosureDocumentScanner {

    /**
     * 실제 문서 형식을 확인하기 위해 파일 앞부분만 보관한다.
     * SHA-256 계산은 파일 전체를 대상으로 한다.
     */
    private static final int PREFIX_LIMIT_BYTES = 128 * 1024;

    private static final int READ_BUFFER_SIZE = 64 * 1024;

    private static final Pattern ROOT_ELEMENT_PATTERN =
            Pattern.compile(
                    "(?is)^\\s*"
                            + "(?:<\\?xml[^>]*>\\s*)?"
                            + "(?:<!--.*?-->\\s*)*"
                            + "(?:<!doctype[^>]*>\\s*)*"
                            + "<\\s*([a-zA-Z][a-zA-Z0-9:_-]*)\\b"
            );

    private final DisclosurePathResolver pathResolver;

    public ContestDisclosureDocumentScanner(DisclosurePathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    @Override
    public List<ScannedDisclosureFile> scan(
            Disclosure disclosure,
            Path disclosureDirectory
    ) {
        Objects.requireNonNull(
                disclosure,
                "disclosure는 필수입니다."
        );

        Objects.requireNonNull(
                disclosureDirectory,
                "disclosureDirectory는 필수입니다."
        );

        validateDisclosureDirectory(
                disclosure,
                disclosureDirectory
        );

        // entries = disclosureDirectory 절대 경로 폴더에 있는 파일들의 경로를 담은 list
        List<Path> entries = listDirectoryEntries(disclosureDirectory);

        if (entries.isEmpty()) {
            throw datasetException(
                    "공시 원문 폴더가 비어 있습니다. "
                            + "docId=" + disclosure.getSourceDocId()
                            + ", directory=" + disclosureDirectory
            );
        }

        List<ScannedDisclosureFile> scannedFiles =
                entries.stream()
                        // 파일들의 경로를 하나한 scanFile 메서드로 넘김
                        // 경로를 타고 파일을 스캔하여 ScannedDisclosureFile 형식으로 만듦
                        .map(path -> scanFile(disclosure, path)) // scanFile 실행
                        .toList();

        validateMainDocument(disclosure, scannedFiles);

        return scannedFiles;
    }

    private ScannedDisclosureFile scanFile(
            Disclosure disclosure,
            Path sourceFile
    ) {
        validateSourceFile(
                disclosure,
                sourceFile
        );

        String fileName = sourceFile
                .getFileName()
                .toString();

        String fileExtension = extractFileExtension(fileName);

        FileInspection inspection = inspectFile(sourceFile);

        DisclosureDocumentContentFormat contentFormat =
                detectContentFormat(
                        sourceFile,
                        fileExtension,
                        inspection.prefix()
                );

        validateExtensionAndContentFormat(
                sourceFile,
                fileExtension,
                contentFormat
        );

        String relativePath = pathResolver.toDatasetRelativePath(sourceFile);

        String normalizedRelativePath = pathResolver.normalizeRelativePath(relativePath);

        DisclosureDocumentRole documentRole =
                determineInitialDocumentRole(
                        disclosure,
                        fileName,
                        fileExtension
                );

        return new ScannedDisclosureFile(
                relativePath,
                normalizedRelativePath,
                fileName,
                fileExtension,
                documentRole,

                /*
                 * DOCUMENT-NAME 또는 HTML title은
                 * 실제 파서가 원문 구조를 해석할 때 확정한다.
                 */
                null,

                contentFormat,
                inspection.fileSizeBytes(),
                inspection.sha256()
        );
    }

    /**
     * 파일을 한 번만 읽으면서 다음 두 작업을 함께 수행한다.
     *
     * 1. 전체 파일 SHA-256 계산
     * 2. 콘텐츠 판별용 앞부분 보관
     */
    private FileInspection inspectFile(Path sourceFile) {
        MessageDigest digest = createSha256Digest();

        ByteArrayOutputStream prefixBuffer =
                new ByteArrayOutputStream(
                        PREFIX_LIMIT_BYTES
                );

        long bytesRead = 0;

        try (
                InputStream inputStream =
                        new BufferedInputStream(
                                Files.newInputStream(sourceFile),
                                READ_BUFFER_SIZE
                        )
        ) {
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            int readLength;

            while (
                    (
                            readLength =
                                    inputStream.read(buffer)
                    ) != -1
            ) {
                digest.update(
                        buffer,
                        0,
                        readLength
                );

                bytesRead += readLength;

                int remainingPrefixBytes =
                        PREFIX_LIMIT_BYTES
                                - prefixBuffer.size();

                if (remainingPrefixBytes > 0) {
                    int copyLength = Math.min(
                            remainingPrefixBytes,
                            readLength
                    );

                    prefixBuffer.write(
                            buffer,
                            0,
                            copyLength
                    );
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "원문 파일을 읽지 못했습니다: "
                            + sourceFile,
                    exception
            );
        }

        long expectedFileSize;

        try {
            expectedFileSize = Files.size(sourceFile);
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "원문 파일 크기를 확인하지 못했습니다: "
                            + sourceFile,
                    exception
            );
        }

        /*
         * 파일을 읽는 동안 내용이 바뀌었다면
         * 크기와 해시를 신뢰할 수 없다.
         */
        if (bytesRead != expectedFileSize) {
            throw datasetException(
                    "원문 파일을 읽는 동안 크기가 변경되었습니다. "
                            + "path=" + sourceFile
                            + ", expectedSize="
                            + expectedFileSize
                            + ", readSize="
                            + bytesRead
            );
        }

        String sha256 =
                HexFormat.of()
                        .formatHex(digest.digest());

        return new FileInspection(
                bytesRead,
                prefixBuffer.toByteArray(),
                sha256
        );
    }

    private DisclosureDocumentContentFormat detectContentFormat(
            Path sourceFile,
            String fileExtension,
            byte[] prefix
    ) {
        if (startsWithPdfSignature(prefix)) {
            return DisclosureDocumentContentFormat.PDF;
        }

        int textOffset = utf8BomLength(prefix);

        String textPrefix = new String(
                prefix,
                textOffset,
                prefix.length - textOffset,
                java.nio.charset.StandardCharsets.ISO_8859_1
        );

        Matcher matcher =
                ROOT_ELEMENT_PATTERN.matcher(textPrefix);

        if (!matcher.find()) {
            throw datasetException(
                    "원문 파일의 루트 문서 형식을 확인할 수 없습니다. "
                            + "path=" + sourceFile
                            + ", extension=" + fileExtension
            );
        }

        String rootElement =
                matcher.group(1)
                        .toLowerCase(Locale.ROOT);

        if ("html".equals(rootElement)) {
            return DisclosureDocumentContentFormat.HTML;
        }

        if ("document".equals(rootElement)) {
            return DisclosureDocumentContentFormat.DART_XML;
        }

        throw datasetException(
                "지원하지 않는 원문 루트 요소입니다. "
                        + "path=" + sourceFile
                        + ", rootElement=" + rootElement
        );
    }

    private void validateDisclosureDirectory(
            Disclosure disclosure,
            Path disclosureDirectory
    ) {
        Path normalizedDirectory =
                disclosureDirectory
                        .toAbsolutePath()
                        .normalize();

        if (
                !Files.exists(
                        normalizedDirectory,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw datasetException(
                    "공시 원문 폴더가 존재하지 않습니다: "
                            + normalizedDirectory
            );
        }

        if (Files.isSymbolicLink(normalizedDirectory)) {
            throw datasetException(
                    "공시 원문 폴더는 심볼릭 링크일 수 없습니다: "
                            + normalizedDirectory
            );
        }

        if (
                !Files.isDirectory(
                        normalizedDirectory,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw datasetException(
                    "공시 원문 경로가 디렉터리가 아닙니다: "
                            + normalizedDirectory
            );
        }

        if (!Files.isReadable(normalizedDirectory)) {
            throw datasetException(
                    "공시 원문 폴더를 읽을 수 없습니다: "
                            + normalizedDirectory
            );
        }

        String actualRelativePath =
                pathResolver.toDatasetRelativePath(
                        normalizedDirectory
                );

        String normalizedActualPath =
                pathResolver.normalizeRelativePath(
                        actualRelativePath
                );

        String normalizedManifestPath =
                Normalizer.normalize(
                        disclosure.getManifestPath(),
                        Normalizer.Form.NFC
                );

        if (
                !normalizedManifestPath.equals(
                        normalizedActualPath
                )
        ) {
            throw datasetException(
                    "공시 원문 폴더가 manifest_path와 일치하지 않습니다. "
                            + "docId=" + disclosure.getSourceDocId()
                            + ", expected="
                            + normalizedManifestPath
                            + ", actual="
                            + normalizedActualPath
            );
        }
    }

    // disclosureDirectory 경로에 있는 file을 찾아서 파일 경로를 list로 만들어 반환
    private List<Path> listDirectoryEntries(Path disclosureDirectory) {
        try (
                Stream<Path> stream = Files.list(disclosureDirectory)
        ) {
            return stream
                    .sorted(
                            Comparator.comparing(path ->
                                    Normalizer.normalize(
                                            path.getFileName()
                                                    .toString(),
                                            Normalizer.Form.NFC
                                    )
                            )
                    )
                    .toList();
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "공시 원문 폴더의 파일 목록을 읽지 못했습니다: "
                            + disclosureDirectory,
                    exception
            );
        }
    }

    private void validateSourceFile(
            Disclosure disclosure,
            Path sourceFile
    ) {
        if (Files.isSymbolicLink(sourceFile)) {
            throw datasetException(
                    "원문 파일은 심볼릭 링크일 수 없습니다. "
                            + "docId=" + disclosure.getSourceDocId()
                            + ", path=" + sourceFile
            );
        }

        if (
                !Files.isRegularFile(
                        sourceFile,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw datasetException(
                    "공시 폴더에 일반 파일이 아닌 항목이 있습니다. "
                            + "docId=" + disclosure.getSourceDocId()
                            + ", path=" + sourceFile
            );
        }

        if (!Files.isReadable(sourceFile)) {
            throw datasetException(
                    "원문 파일을 읽을 수 없습니다. "
                            + "docId=" + disclosure.getSourceDocId()
                            + ", path=" + sourceFile
            );
        }

        if (
                !sourceFile.toAbsolutePath()
                        .normalize()
                        .getParent()
                        .equals(
                                sourceFile.getParent()
                                        .toAbsolutePath()
                                        .normalize()
                        )
        ) {
            throw datasetException(
                    "원문 파일 경로를 안전하게 해석할 수 없습니다: "
                            + sourceFile
            );
        }
    }

    private String extractFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');

        if (
                lastDot < 1
                        || lastDot == fileName.length() - 1
        ) {
            throw datasetException(
                    "원문 파일의 확장자를 확인할 수 없습니다: "
                            + fileName
            );
        }

        String extension =
                fileName.substring(lastDot + 1)
                        .toLowerCase(Locale.ROOT);

        if (
                !"xml".equals(extension)
                        && !"html".equals(extension)
                        && !"pdf".equals(extension)
        ) {
            throw datasetException(
                    "지원하지 않는 원문 파일 확장자입니다. "
                            + "fileName=" + fileName
                            + ", extension=" + extension
            );
        }

        return extension;
    }

    private void validateExtensionAndContentFormat(
            Path sourceFile,
            String fileExtension,
            DisclosureDocumentContentFormat contentFormat
    ) {
        boolean valid = switch (fileExtension) {
            case "xml" ->
                    contentFormat
                            == DisclosureDocumentContentFormat.DART_XML
                            || contentFormat
                            == DisclosureDocumentContentFormat.HTML;

            case "html" ->
                    contentFormat
                            == DisclosureDocumentContentFormat.HTML;

            case "pdf" ->
                    contentFormat
                            == DisclosureDocumentContentFormat.PDF;

            default -> false;
        };

        if (!valid) {
            throw datasetException(
                    "파일 확장자와 실제 콘텐츠 형식이 일치하지 않습니다. "
                            + "path=" + sourceFile
                            + ", extension=" + fileExtension
                            + ", contentFormat=" + contentFormat
            );
        }
    }

    private DisclosureDocumentRole determineInitialDocumentRole(
            Disclosure disclosure,
            String fileName,
            String fileExtension
    ) {
        String expectedMainFileName =
                disclosure.getReceiptNo()
                        + "."
                        + fileExtension;

        if (fileName.equalsIgnoreCase(expectedMainFileName)) {
            return DisclosureDocumentRole.MAIN;
        }

        String expectedViewerFileName =
                disclosure.getReceiptNo()
                        + "_viewer.html";

        if (
                "html".equals(fileExtension)
                        && fileName.equalsIgnoreCase(
                        expectedViewerFileName
                )
        ) {
            return DisclosureDocumentRole.VIEWER;
        }

        String attachmentPrefix =
                disclosure.getReceiptNo() + "_";

        if (
                "xml".equals(fileExtension)
                        && fileName.startsWith(
                        attachmentPrefix
                )
        ) {
            /*
             * 감사보고서인지 일반 첨부인지 여부는
             * DOCUMENT-NAME을 읽은 파서가 나중에 확정한다.
             */
            return DisclosureDocumentRole.ATTACHMENT;
        }

        return DisclosureDocumentRole.UNKNOWN;
    }

    private void validateMainDocument(
            Disclosure disclosure,
            List<ScannedDisclosureFile> scannedFiles
    ) {
        long mainDocumentCount =
                scannedFiles.stream()
                        .filter(file ->
                                file.documentRole()
                                        == DisclosureDocumentRole.MAIN
                        )
                        .count();

        if (mainDocumentCount != 1) {
            throw datasetException(
                    "공시 폴더에는 MAIN 원문 파일이 정확히 하나 있어야 합니다. "
                            + "docId=" + disclosure.getSourceDocId()
                            + ", mainDocumentCount="
                            + mainDocumentCount
            );
        }
    }

    private static boolean startsWithPdfSignature(
            byte[] bytes
    ) {
        byte[] signature = {
                '%',
                'P',
                'D',
                'F',
                '-'
        };

        if (bytes.length < signature.length) {
            return false;
        }

        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                return false;
            }
        }

        return true;
    }

    private static int utf8BomLength(byte[] bytes) {
        if (
                bytes.length >= 3
                        && (bytes[0] & 0xff) == 0xef
                        && (bytes[1] & 0xff) == 0xbb
                        && (bytes[2] & 0xff) == 0xbf
        ) {
            return 3;
        }

        return 0;
    }

    private static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            /*
             * SHA-256은 Java 표준 구현에 반드시 포함되므로
             * 발생한다면 정상적으로 실행할 수 없는 환경이다.
             */
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "현재 Java 환경에서 SHA-256을 사용할 수 없습니다.",
                    exception
            );
        }
    }

    private static BusinessException datasetException(
            String message
    ) {
        return new BusinessException(
                ErrorCode.DATASET_503_1,
                message
        );
    }

    private record FileInspection(
            long fileSizeBytes,
            byte[] prefix,
            String sha256
    ) {

    }
}
