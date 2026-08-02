package com.foliolens.backend.company.infrastructure.dataset;

import com.foliolens.backend.company.domain.Market;
import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class CompanyCsvReader {

    // CSV 파일 맨 앞에 붙을 수 있는 UTF-8 BOM 문자
    private static final char UTF_8_BOM = '\uFEFF';

    // 기업 CSV에 반드시 있어야 하는 컬럼 이름
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "corp_code",
            "stock_code",
            "corp_name",
            "listed_name",
            "corp_eng_name",
            "market",
            "industry",
            "sector_no",
            "sector",
            "listing_date",
            "fiscal_month",
            "market_cap",
            "n_periodic",
            "n_major",
            "n_exchange",
            "n_holding",
            "note"
    );

    // Apache Commons CSV가 파일을 어떤 규칙으로 읽을지 설정
    private static final CSVFormat CSV_FORMAT =
            CSVFormat.DEFAULT.builder()
                    .setHeader()  // CSV의 첫 번째 행을 헤더로 사용
                    .setSkipHeaderRecord(true) // 첫 번째 헤더 행을 실제 데이터로 처리하지 않음
                    .setTrim(true) // CSV 값 앞뒤의 공백을 제거
                    .setIgnoreEmptyLines(true) // CSV 중간이나 마지막의 빈 줄을 무시
                    .setIgnoreSurroundingSpaces(true) // 따옴표로 감싼 값 주변의 공백을 무시
                    .get();

    /**
     * 외부에서 사용하는 대표 메서드
     */
    public List<CompanyCsvRow> read(Path csvPath) {

        // 파일 검사
        // 경로가 null이 아님
        // 파일이 존재함
        // 디렉터리가 아니라 일반 파일임
        // 읽기 권한이 있음
        validateFile(csvPath);

        try {
            // UTF-8로 파일 읽기
            String content = Files.readString(
                    csvPath,
                    StandardCharsets.UTF_8
            );

            // 파일 맨 앞에 UTF-8 BOM이 있다면 제거
            content = removeBom(content);

            // CSV 파싱
            return parse(content);

        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "기업 CSV 파일을 읽을 수 없습니다: "
                            + csvPath,
                    exception
            );
        }
    }

    /**
     * 읽은 CSV 문자열을 실제 행 객체 목록으로 변환
     */
    private List<CompanyCsvRow> parse(String content) {
        try (
                // CSVParser 생성
                StringReader reader = new StringReader(content);
                CSVParser parser = CSV_FORMAT.parse(reader)
        ) {
            // CSV 헤더에 필수 컬럼이 모두 있는지 확인
            validateHeaders(parser.getHeaderNames());

            // 행 목록 생성 -> 파싱한 기업을 차례대로 담는 변경 가능한 리스트
            List<CompanyCsvRow> rows = new ArrayList<>();

            // 각 행을 변환
            for (CSVRecord record : parser) {
                rows.add(parseRow(record));
            }

            // 중복 코드 검증
            validateUniqueCodes(rows);

            return List.copyOf(rows);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | UncheckedIOException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "기업 CSV 내용을 파싱할 수 없습니다.",
                    exception
            );
        }
    }


    // CSV 한 행을 CompanyCsvRow 하나로 변환
    private CompanyCsvRow parseRow(CSVRecord record) {
        try {
            return new CompanyCsvRow(
                    required(record, "corp_code"),
                    required(record, "stock_code"),
                    required(record, "corp_name"),
                    required(record, "listed_name"),
                    required(record, "corp_eng_name"),
                    parseMarket(required(record, "market")),
                    required(record, "industry"),
                    Short.parseShort(
                            required(record, "sector_no")
                    ),
                    required(record, "sector"),
                    LocalDate.parse(
                            required(record, "listing_date")
                    ),
                    parseFiscalMonth(
                            required(record, "fiscal_month")
                    ),
                    Long.parseLong(
                            required(record, "market_cap")
                    ),
                    Integer.parseInt(
                            required(record, "n_periodic")
                    ),
                    Integer.parseInt(
                            required(record, "n_major")
                    ),
                    Integer.parseInt(
                            required(record, "n_exchange")
                    ),
                    Integer.parseInt(
                            required(record, "n_holding")
                    ),
                    optional(record, "note")
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "기업 CSV "
                            + record.getRecordNumber()
                            + "번째 데이터 행의 형식이 올바르지 않습니다.",
                    exception
            );
        }
    }

    private void validateFile(Path csvPath) {
        if (csvPath == null) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "기업 CSV 경로가 설정되지 않았습니다."
            );
        }

        if (!Files.exists(csvPath)) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "기업 CSV 파일이 존재하지 않습니다: "
                            + csvPath
            );
        }

        if (!Files.isRegularFile(csvPath)) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "기업 CSV 경로가 일반 파일이 아닙니다: "
                            + csvPath
            );
        }

        if (!Files.isReadable(csvPath)) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "기업 CSV 파일을 읽을 수 없습니다: "
                            + csvPath
            );
        }
    }

    private void validateHeaders(List<String> actualHeaders) {
        Set<String> missingHeaders = new HashSet<>(REQUIRED_HEADERS);

        missingHeaders.removeAll(actualHeaders);

        if (!missingHeaders.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "기업 CSV 필수 헤더가 없습니다: "
                            + missingHeaders
            );
        }
    }

    private void validateUniqueCodes(
            List<CompanyCsvRow> rows
    ) {
        Set<String> corpCodes = new HashSet<>();
        Set<String> stockCodes = new HashSet<>();

        for (CompanyCsvRow row : rows) {
            if (!corpCodes.add(row.corpCode())) {
                throw new BusinessException(
                        ErrorCode.DATASET_503_1,
                        "기업 CSV에 중복된 corp_code가 있습니다: "
                                + row.corpCode()
                );
            }

            if (!stockCodes.add(row.stockCode())) {
                throw new BusinessException(
                        ErrorCode.DATASET_503_1,
                        "기업 CSV에 중복된 stock_code가 있습니다: "
                                + row.stockCode()
                );
            }
        }
    }

    private Market parseMarket(String value) {
        try {
            return Market.valueOf(
                    value.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "지원하지 않는 시장입니다: " + value,
                    exception
            );
        }
    }

    private short parseFiscalMonth(String value) {
        String normalized = value.trim();

        if (!normalized.endsWith("월")) {
            throw new IllegalArgumentException(
                    "결산월 형식이 올바르지 않습니다: "
                            + value
            );
        }

        String month = normalized.substring(
                0,
                normalized.length() - 1
        );

        return Short.parseShort(month);
    }

    private String required(
            CSVRecord record,
            String header
    ) {
        String value = record.get(header);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    header + " 값은 필수입니다."
            );
        }

        return value.trim();
    }

    private String optional(
            CSVRecord record,
            String header
    ) {
        if (!record.isMapped(header)) {
            return null;
        }

        String value = record.get(header);

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String removeBom(String content) {
        if (!content.isEmpty() && content.charAt(0) == UTF_8_BOM) {
            return content.substring(1);
        }

        return content;
    }
}
