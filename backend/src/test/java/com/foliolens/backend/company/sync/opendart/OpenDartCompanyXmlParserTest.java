package com.foliolens.backend.company.sync.opendart;

import com.foliolens.backend.company.sync.CompanyDataProviderException;
import com.foliolens.backend.company.sync.CompanySyncItem;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class OpenDartCompanyXmlParserTest {


    private final OpenDartCompanyXmlParser parser =
            new OpenDartCompanyXmlParser();

    @Test
    void ZIP_안의_상장기업과_비상장기업을_파싱한다()
            throws Exception {

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                    <list>
                        <corp_code>00126380</corp_code>
                        <corp_name>삼성전자</corp_name>
                        <corp_eng_name>SAMSUNG ELECTRONICS</corp_eng_name>
                        <stock_code>005930</stock_code>
                        <modify_date>20260721</modify_date>
                    </list>
                    <list>
                        <corp_code>90000004</corp_code>
                        <corp_name>테스트비상장</corp_name>
                        <corp_eng_name>TEST COMPANY</corp_eng_name>
                        <stock_code></stock_code>
                        <modify_date>20260720</modify_date>
                    </list>
                </result>
                """;

        List<CompanySyncItem> companies =
                parser.parse(createZip("CORPCODE.xml", xml));

        assertEquals(2, companies.size());

        CompanySyncItem listedCompany =
                companies.get(0);

        assertEquals(
                "00126380",
                listedCompany.corpCode()
        );
        assertEquals(
                "005930",
                listedCompany.stockCode()
        );
        assertEquals(
                "삼성전자",
                listedCompany.corpName()
        );
        assertNull(listedCompany.market());
        assertNull(listedCompany.validFrom());
        assertNull(listedCompany.validTo());

        CompanySyncItem unlistedCompany =
                companies.get(1);

        assertEquals(
                "90000004",
                unlistedCompany.corpCode()
        );
        assertNull(unlistedCompany.stockCode());
        assertEquals(
                "테스트비상장",
                unlistedCompany.corpName()
        );
    }

    @Test
    void 빈_ZIP_응답이면_예외가_발생한다() {
        assertThrows(
                CompanyDataProviderException.class,
                () -> parser.parse(new byte[0])
        );
    }

    @Test
    void ZIP에_XML이_없으면_예외가_발생한다()
            throws Exception {

        byte[] zipBytes =
                createZip(
                        "README.txt",
                        "XML 파일이 아닙니다."
                );

        assertThrows(
                CompanyDataProviderException.class,
                () -> parser.parse(zipBytes)
        );
    }

    @Test
    void 기업고유번호가_8자리가_아니면_예외가_발생한다()
            throws Exception {

        String xml = """
                <result>
                    <list>
                        <corp_code>123</corp_code>
                        <corp_name>잘못된기업</corp_name>
                        <stock_code>123456</stock_code>
                        <modify_date>20260721</modify_date>
                    </list>
                </result>
                """;

        assertThrows(
                CompanyDataProviderException.class,
                () -> parser.parse(
                        createZip("CORPCODE.xml", xml)
                )
        );
    }

    @Test
    void 종목코드가_6자리가_아니면_예외가_발생한다()
            throws Exception {

        String xml = """
                <result>
                    <list>
                        <corp_code>12345678</corp_code>
                        <corp_name>잘못된기업</corp_name>
                        <stock_code>123</stock_code>
                        <modify_date>20260721</modify_date>
                    </list>
                </result>
                """;

        assertThrows(
                CompanyDataProviderException.class,
                () -> parser.parse(
                        createZip("CORPCODE.xml", xml)
                )
        );
    }

    @Test
    void 변경일자_형식이_잘못되면_예외가_발생한다()
            throws Exception {

        String xml = """
                <result>
                    <list>
                        <corp_code>12345678</corp_code>
                        <corp_name>잘못된기업</corp_name>
                        <stock_code>123456</stock_code>
                        <modify_date>2026-07-21</modify_date>
                    </list>
                </result>
                """;

        assertThrows(
                CompanyDataProviderException.class,
                () -> parser.parse(
                        createZip("CORPCODE.xml", xml)
                )
        );
    }

    private byte[] createZip(
            String entryName,
            String contents
    ) throws Exception {
        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        try (
                ZipOutputStream zipOutputStream =
                        new ZipOutputStream(outputStream)
        ) {
            zipOutputStream.putNextEntry(
                    new ZipEntry(entryName)
            );

            zipOutputStream.write(
                    contents.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            zipOutputStream.closeEntry();
        }

        return outputStream.toByteArray();
    }
}