package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.filesystem.DisclosureSourceFileResolver;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** DB에서 대상만 읽으며 파싱 상태나 저장된 본문은 변경하지 않는다. */
@Service
public class HtmlParsingValidationBatchService {
    private final DisclosureDocumentRepository repository;
    private final DisclosureSourceFileResolver files;
    private final HtmlParsingValidator validator;

    public HtmlParsingValidationBatchService(DisclosureDocumentRepository repository,
                                            DisclosureSourceFileResolver files,
                                            HtmlParsingValidator validator) {
        this.repository = repository;
        this.files = files;
        this.validator = validator;
    }

    public HtmlParsingValidationBatchResult validate(int page, int limit, String rawSubtype) {
        if (page < 0 || limit < 1 || limit > 500 || rawSubtype == null || rawSubtype.isBlank()) {
            throw new IllegalArgumentException("page >= 0, limit 1~500, rawSubtype이 필요합니다.");
        }
        String subtype = rawSubtype.strip();
        long count = repository.countHtmlParsingTargets(subtype);
        if ((long) page * limit >= count) throw new IllegalArgumentException("해당 페이지에 HTML 문서가 없습니다.");
        Instant start = Instant.now();
        List<HtmlParsingValidationRow> rows = new ArrayList<>();
        for (DisclosureDocument document : repository.findHtmlParsingTargets(
                subtype, null, PageRequest.of(page, limit)).getContent()) {
            long nanos = System.nanoTime();
            try {
                ValidatedHtmlDocument result = validator.validate(files.resolve(document));
                if (document.getDisclosure().isCorrection()
                        && result.document().sections().stream().noneMatch(s -> "정정신고(보고)".equals(s.title()))) {
                    throw new IllegalArgumentException("정정공시의 정정 섹션을 찾지 못했습니다.");
                }
                rows.add(new HtmlParsingValidationRow(document.getId(), document.getDisclosure().getReceiptNo(),
                        document.getFileName(), document.getDisclosure().isCorrection(),
                        result.document().documentName(), result.metrics(), result.document().relatedLinks().size(),
                        elapsed(nanos), HtmlParsingValidationRow.Status.SUCCESS, null));
            } catch (RuntimeException exception) {
                rows.add(new HtmlParsingValidationRow(document.getId(), document.getDisclosure().getReceiptNo(),
                        document.getFileName(), document.getDisclosure().isCorrection(), null, null, 0,
                        elapsed(nanos), HtmlParsingValidationRow.Status.FAILED, describe(exception)));
            }
        }
        return new HtmlParsingValidationBatchResult(start, Instant.now(), count, rows);
    }

    public static String describe(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getClass().getSimpleName() + ": " + current.getMessage();
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private long elapsed(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanos);
    }
}
