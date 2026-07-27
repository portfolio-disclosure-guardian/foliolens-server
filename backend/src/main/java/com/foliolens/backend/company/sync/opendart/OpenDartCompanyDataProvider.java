package com.foliolens.backend.company.sync.opendart;

import com.foliolens.backend.company.sync.CompanyDataProvider;
import com.foliolens.backend.company.sync.CompanyDataProviderException;
import com.foliolens.backend.company.sync.CompanyDataSource;
import com.foliolens.backend.company.sync.CompanySyncItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "external.opendart",
        name = "enabled",
        havingValue = "true"
)
/**
 * - 요청 주소 생성
 * - OpenDART HTTP 호출
 * - 응답 크기 제한
 * - HTTP 상태 확인
 * - ZIP인지 확인
 * - OpenDART 오류 응답 처리
 */
public class OpenDartCompanyDataProvider implements CompanyDataProvider {

    private static final Pattern STATUS_PATTERN =
            Pattern.compile("<status>\\s*([^<]+)\\s*</status>");

    private static final Pattern MESSAGE_PATTERN =
            Pattern.compile("<message>\\s*([^<]+)\\s*</message>");

    private final OpenDartProperties properties; // application.yaml에 있는 설정을 가져옴
    private final OpenDartCompanyXmlParser xmlParser; // 다운로드한 ZIP을 기업 목록으로 바꿈
    private final HttpClient httpClient; // OpenDART 서버에 HTTP 요청을 보냄

    public OpenDartCompanyDataProvider(
            OpenDartProperties properties,
            OpenDartCompanyXmlParser xmlParser
    ) {
        this.properties = properties;
        this.xmlParser = xmlParser;

        // Java 표준 HttpClient를 생성
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }


    // 데이터 출처 반환
    @Override
    public CompanyDataSource source() {
        return CompanyDataSource.OPENDART;
    }

    /**
     * 설정 검증
     *   ↓
     * 요청 URI 생성
     *   ↓
     * HTTP 요청 객체 생성
     *   ↓
     * OpenDART 동기 호출
     *   ↓
     * 응답 크기를 제한하며 읽기
     *   ↓
     * HTTP 상태 코드 확인
     *   ↓
     * ZIP 파일 여부 확인
     *   ↓
     * ZIP·XML 파싱
     *   ↓
     * 기업 목록 반환
     */
    @Override
    public List<CompanySyncItem> fetchCompanies() {
        // 요청 전 설정 검증
        properties.validateForRequest();

        log.info("OpenDART 기업 고유번호 ZIP 다운로드를 시작합니다.");

        // 요청 URI 생성
        URI requestUri = createRequestUri();

        // HTTP 요청 객체 생성
        HttpRequest request = HttpRequest.newBuilder()
                .uri(requestUri) // 요청 URI 설정
                .timeout(properties.requestTimeout())
                .header("Accept", "application/zip, application/octet-stream")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            // 응답 본문 읽기
            byte[] responseBytes;

            try (InputStream body = response.body()) {
                responseBytes = readLimited(body);
            }

            // HTTP 상태 코드 확인
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // 실패하면 다음 예외로 변환
                throw new CompanyDataProviderException(
                        "OpenDART HTTP 요청에 실패했습니다. status=" + response.statusCode()
                );
            }

            // ZIP 파일인지 확인
            if (!isZip(responseBytes)) {
                throw createOpenDartError(responseBytes);
            }

            // XML 파서에 전달 - 정상 ZIP으로 확인되면 앞서 설명한 파서를 호출
            List<CompanySyncItem> companies = xmlParser.parse(responseBytes);

            log.info(
                    "OpenDART 기업 고유번호 파싱을 완료했습니다. count={}",
                    companies.size()
            );

            return companies;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new CompanyDataProviderException(
                    "OpenDART 요청이 중단되었습니다.", e
            );

        } catch (IOException e) {
            throw new CompanyDataProviderException(
                    "OpenDART 통신에 실패했습니다.", e
            );
        }
    }

    // 요청 URI 생성 메서드
    private URI createRequestUri() {
        return UriComponentsBuilder
                .fromUri(properties.corpCodeUrl()) // 설정에 있는 기본 주소로 시작
                .queryParam("crtfc_key", properties.apiKey().trim()) // 인증키를 crtfc_key라는 쿼리 파라미터로 추가
                .build()
                .encode()
                .toUri();
    }

    private byte[] readLimited(InputStream inputStream) throws IOException {
        int maxSize = properties.maxZipBytes();

        byte[] responseBytes = inputStream.readNBytes(maxSize + 1);

        if (responseBytes.length > maxSize) {
            throw new CompanyDataProviderException(
                    "OpenDART ZIP 응답이 최대 허용 크기를 초과했습니다."
            );
        }

        return responseBytes;
    }

    // ZIP 파일인지 검증
    // ZIP 파일은 일반적으로 다음 매직 바이트로 시작
    // 50 4B 03 04
    // P  K
    private boolean isZip(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 0x50
                && bytes[1] == 0x4B
                && bytes[2] == 0x03
                && bytes[3] == 0x04;
    }

    private CompanyDataProviderException createOpenDartError(byte[] responseBytes) {
        String xml = new String(
                responseBytes,
                StandardCharsets.UTF_8
        );

        String status = extractValue(
                STATUS_PATTERN,
                xml
        );

        String message = extractValue(
                MESSAGE_PATTERN,
                xml
        );

        return new CompanyDataProviderException(
                "OpenDART가 ZIP 대신 오류를 반환했습니다."
                        + " status=" + defaultValue(status)
                        + ", message=" + defaultValue(message)
        );
    }

    private String extractValue(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1).trim();
    }

    private String defaultValue(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }

        return value;
    }
}
