package com.foliolens.backend.company.sync.opendart;

import com.foliolens.backend.company.sync.CompanyDataProviderException;
import com.foliolens.backend.company.sync.CompanySyncItem;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component // Bean으로 등록. 다른 서비스에서 생성자를 통해 주입
public class OpenDartCompanyXmlParser {

    private static final int MAX_COMPANY_COUNT = 500_000;

    private static final DateTimeFormatter MODIFY_DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * zip 파일 처리
     * @param zipBytes = OpenDART 고유번호 API가 반환한 ZIP 파일의 바이트 배열
     * @return = CompanySyncItem
               String corpCode,
     *         String stockCode,
     *         String corpName,
     *         Market market,
     *         LocalDate validFrom,
     *         LocalDate validTo
     */
    public List<CompanySyncItem> parse(byte[] zipBytes) {
        // 빈 응답 검사
        if (zipBytes == null || zipBytes.length == 0) {
            throw new CompanyDataProviderException(
                    "OpenDART ZIP 응답이 비어 있습니다."
            );
        }

        try (
                // 바이트 배열을 zip 스트림으로 변환
                // byte[]
                //  ↓ ByteArrayInputStream
                // 일반 바이트 스트림
                //  ↓ ZipInputStream
                // ZIP 내부 파일을 순서대로 읽을 수 있는 스트림
                ByteArrayInputStream byteInputStream = new ByteArrayInputStream(zipBytes);
                ZipInputStream zipInputStream = new ZipInputStream(byteInputStream, StandardCharsets.UTF_8)
        ) {

            ZipEntry entry; // ZIP 안의 파일 또는 디렉터리 하나를 나타냄

            while ((entry = zipInputStream.getNextEntry()) != null) {
                // .xml 파일을 찾음
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".xml")) {

                    return parseXml(zipInputStream);
                }

                zipInputStream.closeEntry();
            }

            throw new CompanyDataProviderException("OpenDART ZIP 안에서 XML 파일을 찾을 수 없습니다.");

        } catch (IOException e) {
            throw new CompanyDataProviderException("OpenDART ZIP 파일을 읽지 못했습니다.", e);
        }
    }

    /**
     * XML 본문 처리
     * @param zipInputStream = zip 안에서 발견한 xml 파일
     * @return = ZIP 안에서 발견한 XML 파일을 실제 기업 목록으로 변환
     */
    private List<CompanySyncItem> parseXml(ZipInputStream zipInputStream) {
        // StAX 방식의 XML 파서를 만드는 팩토리
        XMLInputFactory factory = createSecureXmlInputFactory();

        try {
            // XML 리더 생성
            XMLStreamReader reader = factory.createXMLStreamReader(zipInputStream);

            List<CompanySyncItem> companies = new ArrayList<>(); // 파싱이 끝난 기업 목록

            boolean insideCompany = false; // 현재 <list> 기업 항목 내부인지 표시

            String corpCode = null; // 현재 기업의 고유번호
            String corpName = null; // 현재 기업명
            String stockCode = null; // 현재 기업 종목코드
            String modifyDate = null; // OpenDART 최종변경일

            // XML 이벤트 반복
            // 대표적인 이벤트는 다음과 같습니다.
            // START_ELEMENT: <list>, <corp_code> 같은 시작 태그
            // END_ELEMENT: </list>, </corp_code> 같은 종료 태그
            // CHARACTERS: 태그 안의 문자열
            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String elementName = reader.getLocalName();

                    // 새 기업을 읽기 시작한다는 의미
                    if ("list".equals(elementName)) {
                        insideCompany = true;

                        corpCode = null;
                        corpName = null;
                        stockCode = null;
                        modifyDate = null;

                        continue;
                    }

                    if (!insideCompany) {
                        continue;
                    }

                    /*
                        예를 들어 XML이 다음과 같다면:
                            <corp_code>00126380</corp_code>
                        다음 결과가 됩니다.
                            corpCode = "00126380";
                     */
                    switch (elementName) {
                        case "corp_code" ->
                                corpCode = readText(reader);

                        case "corp_name" ->
                                corpName = readText(reader);

                        case "stock_code" ->
                                stockCode = readText(reader);

                        case "modify_date" ->
                                modifyDate = readText(reader);

                        // corp_eng_name은 현재 Company에 저장하지 않음
                        case "corp_eng_name" ->
                                readText(reader);

                        default -> {
                            // 알 수 없는 태그는 무시
                        }
                    }
                }

                if (event == XMLStreamConstants.END_ELEMENT && "list".equals(reader.getLocalName())) {

                    companies.add(
                            toSyncItem(
                                    corpCode,
                                    stockCode,
                                    corpName,
                                    modifyDate
                            )
                    );

                    if (companies.size() > MAX_COMPANY_COUNT) {
                        throw new CompanyDataProviderException(
                                "OpenDART 기업 수가 허용 범위를 초과했습니다."
                        );
                    }

                    insideCompany = false;
                }
            }

            reader.close();

            if (companies.isEmpty()) {
                throw new CompanyDataProviderException(
                        "OpenDART XML에 기업 데이터가 없습니다."
                );
            }

            return companies;

        } catch (XMLStreamException e) {
            throw new CompanyDataProviderException(
                    "OpenDART 기업 XML 파싱에 실패했습니다.",
                    e
            );
        }
    }

    /**
     * 필수값 확인
     * 앞뒤 공백 제거
     * 빈 종목코드를 null로 변환
     * 기업 고유번호 형식 검증
     * 종목코드 형식 검증
     * 변경일 형식 검증
     */
    private CompanySyncItem toSyncItem(
            String corpCode,
            String stockCode,
            String corpName,
            String modifyDate
    ) {
        String normalizedCorpCode =
                requireText(corpCode, "corp_code");

        String normalizedCorpName =
                requireText(corpName, "corp_name");

        String normalizedStockCode =
                normalizeNullable(stockCode);

        if (!normalizedCorpCode.matches("^[0-9]{8}$")) {
            throw new CompanyDataProviderException(
                    "잘못된 OpenDART 기업 고유번호: "
                            + normalizedCorpCode
            );
        }

        if (normalizedStockCode != null
                && !normalizedStockCode.matches("^[0-9A-Z]{6}$")) {
            throw new CompanyDataProviderException(
                    "잘못된 OpenDART 종목코드: "
                            + normalizedStockCode
            );
        }

        validateModifyDate(modifyDate, normalizedCorpCode);

        return new CompanySyncItem(
                normalizedCorpCode,
                normalizedStockCode,
                normalizedCorpName,

                // OpenDART 고유번호 파일에는 시장 구분이 없음
                null,

                // modify_date는 유효기간 시작일이 아니므로
                // validFrom에 넣으면 안 됨
                null,
                null
        );
    }

    private XMLInputFactory createSecureXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();

        // XXE 공격 방어
        factory.setProperty(
                XMLInputFactory.SUPPORT_DTD,
                false
        );

        factory.setProperty(
                "javax.xml.stream.isSupportingExternalEntities",
                false
        );

        factory.setProperty(
                XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES,
                false
        );

        return factory;
    }

    private String readText(XMLStreamReader reader) throws XMLStreamException {
        String value = reader.getElementText();

        if (value == null) {
            return null;
        }

        return value.trim();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new CompanyDataProviderException(
                    "OpenDART 기업 데이터에 "
                            + fieldName
                            + " 값이 없습니다."
            );
        }

        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private void validateModifyDate(String modifyDate, String corpCode) {
        if (modifyDate == null || modifyDate.isBlank()) {
            return;
        }

        try {
            LocalDate.parse(modifyDate.trim(), MODIFY_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CompanyDataProviderException(
                    "OpenDART 최종변경일자가 올바르지 않습니다. corpCode=" + corpCode, e);
        }
    }
}
