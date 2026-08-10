package com.foliolens.backend.disclosure.infrastructure.profiling;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosurePathResolver;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.repository.DisclosureRepository;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(4)
@ConditionalOnProperty(
        prefix = "foliolens.profiling.xml-structure",
        name = "enabled",
        havingValue = "true"
)
public class XmlStructureProfileRunner implements ApplicationRunner {

    private static final Pattern RECEIPT_NO_PATTERN =
            Pattern.compile("^[0-9]{14}$");

    private final DisclosureRepository disclosureRepository;
    private final DisclosureDocumentRepository documentRepository;
    private final DisclosurePathResolver pathResolver;
    private final XmlStructureProfiler xmlStructureProfiler;
    private final String receiptNo;
    private final String targetFileName;

    public XmlStructureProfileRunner(
            DisclosureRepository disclosureRepository,
            DisclosureDocumentRepository documentRepository,
            DisclosurePathResolver pathResolver,
            XmlStructureProfiler xmlStructureProfiler,
            @Value(
                    "${foliolens.profiling.xml-structure.receipt-no:}"
            )
            String configuredReceiptNo,
            @Value(
                    "${foliolens.profiling.xml-structure.file-name:}"
            )
            String configuredFileName
    ) {
        this.disclosureRepository =
                disclosureRepository;

        this.documentRepository =
                documentRepository;

        this.pathResolver =
                pathResolver;

        this.xmlStructureProfiler =
                xmlStructureProfiler;

        this.receiptNo =
                validateReceiptNo(configuredReceiptNo);

        this.targetFileName =
                normalizeTargetFileName(configuredFileName);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(
                "XML 원문 구조 조사를 시작합니다. "
                        + "receiptNo={}, targetFileName={}",
                receiptNo,
                targetFileName == null
                        ? "MAIN"
                        : targetFileName
        );

        Disclosure disclosure =
                findDisclosure();

        DisclosureDocument targetDocument =
                findTargetDocument(disclosure);

        validateContentFormat(targetDocument);

        Path sourceFile =
                resolveSourceFile(
                        disclosure,
                        targetDocument
                );

        XmlStructureProfile profile =
                xmlStructureProfiler.profile(sourceFile);

        validateFileSize(
                targetDocument,
                profile
        );

        printProfile(
                disclosure,
                targetDocument,
                sourceFile,
                profile
        );
    }

    private Disclosure findDisclosure() {
        return disclosureRepository
                .findByReceiptNo(receiptNo)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.DISCLOSURE_404_1,
                                "구조 조사 대상 공시를 찾을 수 없습니다. "
                                        + "receiptNo="
                                        + receiptNo
                        )
                );
    }

    private DisclosureDocument findTargetDocument(
            Disclosure disclosure
    ) {
        List<DisclosureDocument> documents =
                documentRepository
                        .findAllByDisclosureIdOrderByFileNameAsc(
                                disclosure.getId()
                        );

        if (documents.isEmpty()) {
            throw datasetException(
                    "공시에 연결된 원문 문서가 없습니다. "
                            + "receiptNo=" + receiptNo
            );
        }

        if (targetFileName == null) {
            return findMainDocument(documents);
        }

        List<DisclosureDocument> matchedDocuments =
                documents.stream()
                        .filter(document ->
                                targetFileName.equals(
                                        document.getFileName()
                                )
                        )
                        .toList();

        if (matchedDocuments.size() != 1) {
            throw datasetException(
                    "설정한 파일명과 일치하는 원문 문서가 "
                            + "정확히 하나가 아닙니다. "
                            + "receiptNo=" + receiptNo
                            + ", targetFileName="
                            + targetFileName
                            + ", matchedCount="
                            + matchedDocuments.size()
            );
        }

        return matchedDocuments.getFirst();
    }

    private DisclosureDocument findMainDocument(
            List<DisclosureDocument> documents
    ) {

        List<DisclosureDocument> mainDocuments =
                documents.stream()
                        .filter(document ->
                                document.getDocumentRole()
                                        == DisclosureDocumentRole.MAIN
                        )
                        .toList();

        if (mainDocuments.size() != 1) {
            throw datasetException(
                    "공시에 연결된 MAIN 문서가 정확히 하나가 아닙니다. "
                            + "receiptNo=" + receiptNo
                            + ", mainDocumentCount="
                            + mainDocuments.size()
            );
        }

        return mainDocuments.getFirst();
    }

    private void validateContentFormat(
            DisclosureDocument document
    ) {
        if (
                document.getContentFormat()
                        != DisclosureDocumentContentFormat.DART_XML
        ) {
            throw datasetException(
                    "XmlStructureProfiler는 DART_XML 문서만 조사할 수 있습니다. "
                            + "receiptNo=" + receiptNo
                            + ", fileName="
                            + document.getFileName()
                            + ", contentFormat="
                            + document.getContentFormat()
            );
        }
    }

    private Path resolveSourceFile(
            Disclosure disclosure,
            DisclosureDocument document
    ) {
        Path disclosureDirectory =
                pathResolver
                        .resolveDirectory(
                                disclosure.getManifestPath()
                        )
                        .toAbsolutePath()
                        .normalize();

        Path sourceFile =
                disclosureDirectory
                        .resolve(document.getFileName())
                        .toAbsolutePath()
                        .normalize();

        /*
         * DB의 파일명이 "../" 등을 포함해 공시 폴더 밖으로
         * 벗어나는 것을 방지한다.
         */
        if (
                sourceFile.getParent() == null
                        || !sourceFile.getParent()
                        .equals(disclosureDirectory)
        ) {
            throw datasetException(
                    "원문 파일 경로가 공시 폴더를 벗어납니다. "
                            + "fileName="
                            + document.getFileName()
            );
        }

        return sourceFile;
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
            throw datasetException(
                    "DB에 저장된 파일 크기와 현재 파일 크기가 다릅니다. "
                            + "fileName=" + document.getFileName()
                            + ", databaseSize="
                            + document.getFileSizeBytes()
                            + ", actualSize="
                            + profile.fileSizeBytes()
            );
        }
    }

    private void printProfile(
            Disclosure disclosure,
            DisclosureDocument document,
            Path sourceFile,
            XmlStructureProfile profile
    ) {
        long totalElementCount =
                profile.tagCounts()
                        .values()
                        .stream()
                        .mapToLong(Long::longValue)
                        .sum();

        log.info(
                """

                ================= XML STRUCTURE PROFILE =================
                sourceDocId       : {}
                receiptNo         : {}
                reportName        : {}
                fileName          : {}
                documentRole      : {}
                contentFormat     : {}
                sourceFile        : {}
                fileSizeBytes     : {}
                rootElementName   : {}
                documentName      : {}
                maxDepth          : {}
                distinctTagCount  : {}
                totalElementCount : {}
                =========================================================
                """,
                disclosure.getSourceDocId(),
                disclosure.getReceiptNo(),
                disclosure.getReportName(),
                document.getFileName(),
                document.getDocumentRole(),
                document.getContentFormat(),
                sourceFile,
                profile.fileSizeBytes(),
                profile.rootElementName(),
                profile.documentName(),
                profile.maxDepth(),
                profile.tagCounts().size(),
                totalElementCount
        );

        log.info("태그별 등장 횟수:");

        profile.tagCounts().forEach(
                (tagName, count) ->
                        log.info(
                                "tag={}, count={}",
                                tagName,
                                count
                        )
        );

        log.info(
                "XML 원문 구조 조사가 완료되었습니다. receiptNo={}",
                receiptNo
        );
    }

    private static String validateReceiptNo(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw datasetException(
                    "XML 구조 조사를 활성화한 경우 "
                            + "접수번호 설정은 필수입니다."
            );
        }

        String normalized = value.trim();

        if (
                !RECEIPT_NO_PATTERN
                        .matcher(normalized)
                        .matches()
        ) {
            throw datasetException(
                    "XML 구조 조사 접수번호는 "
                            + "14자리 숫자여야 합니다. "
                            + "receiptNo=" + normalized
            );
        }

        return normalized;
    }

    private static String normalizeTargetFileName(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (
                normalized.contains("/")
                        || normalized.contains("\\")
                        || normalized.indexOf('\0') >= 0
                        || ".".equals(normalized)
                        || "..".equals(normalized)
        ) {
            throw datasetException(
                    "XML 구조 조사 대상에는 경로가 아닌 "
                            + "파일명만 입력해야 합니다. "
                            + "fileName=" + normalized
            );
        }

        if (
                !normalized
                        .toLowerCase(Locale.ROOT)
                        .endsWith(".xml")
        ) {
            throw datasetException(
                    "XML 구조 조사 대상 파일은 "
                            + ".xml 확장자여야 합니다. "
                            + "fileName=" + normalized
            );
        }

        return normalized;
    }

    private static BusinessException datasetException(
            String message
    ) {
        return new BusinessException(
                ErrorCode.DATASET_503_1,
                message
        );
    }
}
