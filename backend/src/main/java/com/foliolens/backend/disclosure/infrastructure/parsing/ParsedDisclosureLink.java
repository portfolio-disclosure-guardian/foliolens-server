package com.foliolens.backend.disclosure.infrastructure.parsing;

import java.net.URI;
import java.util.Objects;

/**
 * 원문에 기재된 공시 링크. 정정/후속 관계가 확정됐다는 의미가 아니다.
 * KRX 식별자를 DART 접수번호로 변환하거나 외부 URL을 조회하지 않는다.
 */
public record ParsedDisclosureLink(
        int order,
        String label,
        String href,
        SourceSystem sourceSystem,
        String dartReceiptNo,
        String krxAcptNo,
        String krxRcpNo,
        int sourceLineStart,
        int sourceLineEnd
) {
    public enum SourceSystem { DART, KRX }

    public ParsedDisclosureLink {
        if (order < 1) throw new IllegalArgumentException("링크 순서는 1 이상이어야 합니다.");
        href = Objects.requireNonNull(href, "href는 필수입니다.").strip();
        URI uri = URI.create(href);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("공시 링크는 HTTP(S) 주소여야 합니다.");
        }
        Objects.requireNonNull(sourceSystem, "sourceSystem은 필수입니다.");
        String expectedHost = sourceSystem == SourceSystem.DART ? "dart.fss.or.kr" : "kind.krx.co.kr";
        if (!expectedHost.equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("공시 링크의 시스템과 호스트가 다릅니다.");
        }
        if (sourceSystem == SourceSystem.DART && (krxAcptNo != null || krxRcpNo != null)
                || sourceSystem == SourceSystem.KRX && dartReceiptNo != null) {
            throw new IllegalArgumentException("DART와 KRX 식별자를 혼용할 수 없습니다.");
        }
        validateIdentifier(dartReceiptNo);
        validateIdentifier(krxAcptNo);
        validateIdentifier(krxRcpNo);
        if (sourceLineStart < -1 || sourceLineEnd < -1
                || sourceLineStart != -1 && sourceLineEnd != -1 && sourceLineEnd < sourceLineStart) {
            throw new IllegalArgumentException("링크 원문 행 범위가 올바르지 않습니다.");
        }
        label = label == null || label.isBlank() ? null : label.strip();
    }

    private static void validateIdentifier(String value) {
        if (value != null && !value.matches("[0-9]{14}")) {
            throw new IllegalArgumentException("공시 링크 식별자는 14자리 숫자여야 합니다.");
        }
    }
}
