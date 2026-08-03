package com.foliolens.backend.disclosure.infrastructure.dataset;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Component
public class DisclosureManifestReader {

    private static final char UTF_8_BOM = '\uFEFF';

    private final ObjectMapper objectMapper;

    public DisclosureManifestReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * manifest.jsonl을 읽어 공시 Row 목록으로 변환한다.
     */
    public List<DisclosureManifestRow> read(
            Path manifestPath
    ) {
        validateFile(manifestPath);

        List<DisclosureManifestRow> rows = new ArrayList<>();

        try (
                BufferedReader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)
        ) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (lineNumber == 1) {
                    line = removeBom(line);
                }

                if (line.isBlank()) {
                    throw new BusinessException(
                            ErrorCode.DATASET_503_1,
                            "공시 manifest "
                                    + lineNumber
                                    + "번째 줄이 비어 있습니다."
                    );
                }

                rows.add(
                        parseLine(
                                line,
                                lineNumber
                        )
                );
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "공시 manifest 파일을 읽을 수 없습니다: "
                            + manifestPath,
                    exception
            );
        }

        if (rows.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "공시 manifest에 데이터가 없습니다: "
                            + manifestPath
            );
        }

        validateUniqueKeys(rows);

        return List.copyOf(rows);
    }

    /**
     * JSON 한 줄을 DisclosureManifestRow로 변환한다.
     *
     * Row의 compact constructor에서 발생한 검증 오류도
     * 여기에서 줄 번호가 포함된 BusinessException으로 변환한다.
     */
    private DisclosureManifestRow parseLine(
            String line,
            int lineNumber
    ) {
        try {
            return objectMapper.readValue(
                    line,
                    DisclosureManifestRow.class
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "공시 manifest "
                            + lineNumber
                            + "번째 줄의 형식이 올바르지 않습니다. "
                            + "원인: "
                            + findRootCauseMessage(exception),
                    exception
            );
        }
    }

    /**
     * 하나의 manifest 안에서 doc_id와 rcept_no가 중복되는지 확인한다.
     */
    private void validateUniqueKeys(
            List<DisclosureManifestRow> rows
    ) {
        Set<String> docIds = new HashSet<>();
        Set<String> receiptNos = new HashSet<>();

        for (DisclosureManifestRow row : rows) {
            if (!docIds.add(row.docId())) {
                throw new BusinessException(
                        ErrorCode.DATASET_503_1,
                        "공시 manifest에 중복된 doc_id가 있습니다: "
                                + row.docId()
                );
            }

            if (!receiptNos.add(row.receiptNo())) {
                throw new BusinessException(
                        ErrorCode.DATASET_503_1,
                        "공시 manifest에 중복된 rcept_no가 있습니다: "
                                + row.receiptNo()
                );
            }
        }
    }

    /**
     * 파일 경로가 실제로 읽을 수 있는 파일인지 확인한다.
     */
    private void validateFile(Path manifestPath) {
        if (manifestPath == null) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "공시 manifest 경로가 설정되지 않았습니다."
            );
        }

        if (!Files.exists(manifestPath)) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "공시 manifest 파일이 존재하지 않습니다: "
                            + manifestPath
            );
        }

        if (!Files.isRegularFile(manifestPath)) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "공시 manifest 경로가 일반 파일이 아닙니다: "
                            + manifestPath
            );
        }

        if (!Files.isReadable(manifestPath)) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "공시 manifest 파일을 읽을 수 없습니다: "
                            + manifestPath
            );
        }
    }

    /**
     * Jackson이 감싼 예외에서 실제 Row 검증 오류 메시지를 찾는다.
     */
    private String findRootCauseMessage(
            Throwable exception
    ) {
        Throwable current = exception;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }

        return message;
    }

    private String removeBom(String line) {
        if (
                !line.isEmpty()
                        && line.charAt(0) == UTF_8_BOM
        ) {
            return line.substring(1);
        }

        return line;
    }
}
