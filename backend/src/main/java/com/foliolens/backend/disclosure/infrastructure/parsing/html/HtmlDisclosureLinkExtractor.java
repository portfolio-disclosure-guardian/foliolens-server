package com.foliolens.backend.disclosure.infrastructure.parsing.html;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureLink;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class HtmlDisclosureLinkExtractor {
    private final HtmlTextExtractor textExtractor;
    private final HtmlSourceLocationResolver locationResolver;

    public HtmlDisclosureLinkExtractor(HtmlTextExtractor textExtractor,
                                       HtmlSourceLocationResolver locationResolver) {
        this.textExtractor = textExtractor;
        this.locationResolver = locationResolver;
    }

    public List<ParsedDisclosureLink> extract(Element root) {
        List<ParsedDisclosureLink> links = new ArrayList<>();
        for (Element anchor : root.select("a[href]")) {
            if (isHidden(anchor)) continue;
            String href = anchor.attr("href").strip();
            try {
                URI uri = URI.create(href);
                if (uri.getHost() == null || uri.getUserInfo() != null
                        || !("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme()))) continue;
                ParsedDisclosureLink.SourceSystem system = switch (uri.getHost().toLowerCase(Locale.ROOT)) {
                    case "dart.fss.or.kr" -> ParsedDisclosureLink.SourceSystem.DART;
                    case "kind.krx.co.kr" -> ParsedDisclosureLink.SourceSystem.KRX;
                    default -> null;
                };
                if (system == null) continue;
                Map<String, String> query = queryParameters(uri.getRawQuery());
                HtmlSourceLineRange range = locationResolver.resolve(anchor);
                links.add(new ParsedDisclosureLink(
                        links.size() + 1, textExtractor.extract(anchor), href, system,
                        system == ParsedDisclosureLink.SourceSystem.DART ? identifier(query.get("rcpno")) : null,
                        system == ParsedDisclosureLink.SourceSystem.KRX ? identifier(query.get("acptno")) : null,
                        system == ParsedDisclosureLink.SourceSystem.KRX ? identifier(query.get("rcpno")) : null,
                        range.startLine(), range.endLine()
                ));
            } catch (IllegalArgumentException ignored) {
                // 잘못된 URL은 실행/보정하지 않는다. 링크의 표시 문구는 본문에 보존된다.
            }
        }
        return List.copyOf(links);
    }

    private boolean isHidden(Element element) {
        for (Element current = element; current != null; current = current.parent()) {
            if (textExtractor.shouldIgnore(current, current.normalName())) return true;
        }
        return false;
    }

    private Map<String, String> queryParameters(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=", 2);
            if (pair.length != 2) continue;
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            if (result.containsKey(name) && !result.get(name).equals(value)) {
                throw new IllegalArgumentException("중복된 링크 식별자");
            }
            result.put(name, value);
        }
        return result;
    }

    private String identifier(String value) {
        return value != null && value.matches("[0-9]{14}") ? value : null;
    }
}
