package com.foliolens.backend.disclosure.infrastructure.parsing.pdf;

import com.foliolens.backend.disclosure.infrastructure.parsing.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 오프라인 PDF 텍스트 검색용 파서. OCR, 표 복원, 숫자 정규화는 하지 않는다. */
@Component
public class PdfTextDisclosureParser implements DisclosureDocumentParser {
    public static final String NAME = "PdfTextDisclosureParser";
    public static final String VERSION = "1.0.0";
    private static final int MAX_PAGES = 2_000;
    private static final long MAX_BYTES = 100L * 1024 * 1024;
    private static final int MAX_PAGE_CHARACTERS = 500_000;
    private static final long MAX_DOCUMENT_CHARACTERS = 10_000_000;

    @Override public String parserName() { return NAME; }
    @Override public String parserVersion() { return VERSION; }

    @Override public ParsedDisclosureDocument parse(Path sourceFile) {
        try {
            if (!Files.isRegularFile(sourceFile) || Files.size(sourceFile) > MAX_BYTES) {
                throw new IllegalArgumentException("PDF 파일이 없거나 허용 크기(100 MiB)를 초과합니다.");
            }
            try (PDDocument pdf = Loader.loadPDF(sourceFile.toFile())) {
                if (pdf.isEncrypted() || !pdf.getCurrentAccessPermission().canExtractContent()) {
                    throw new IllegalArgumentException("암호화되었거나 텍스트 추출이 제한된 PDF입니다.");
                }
                int pages = pdf.getNumberOfPages();
                if (pages < 1 || pages > MAX_PAGES) {
                    throw new IllegalArgumentException("PDF 페이지 수는 1~" + MAX_PAGES + "이어야 합니다.");
                }
                PageCollector collector = new PageCollector(pages);
                // PDFBox의 페이지 트리를 한 번만 순회한다. 페이지마다 전체 PDF를 재순회하지 않는다.
                collector.writeText(pdf, new StringWriter());
                collector.finish(pages);
                if (collector.characterCount == 0) {
                    throw new IllegalArgumentException("추출 가능한 PDF 텍스트가 없습니다. OCR은 지원하지 않습니다.");
                }
                return new ParsedDisclosureDocument(sourceFile.getFileName().toString(), null, List.of(),
                        collector.sections, List.of(),
                        PdfTextExtractionReport.of(pages, collector.empty, collector.suspicious));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("PDF 텍스트 추출에 실패했습니다.", exception);
        }
    }

    private static final class PageCollector extends PDFTextStripper {
        private final List<ParsedDisclosureSection> sections = new ArrayList<>();
        private final List<Integer> empty = new ArrayList<>();
        private final List<Integer> suspicious = new ArrayList<>();
        private int order;
        private long characterCount;
        private int lastPage;

        PageCollector(int pages) {
            setSortByPosition(true);
            setStartPage(1);
            setEndPage(pages);
            setLineSeparator("\n");
            setPageStart("");
            setPageEnd("");
        }

        @Override protected void writePage() throws IOException {
            var documentOutput = output;
            StringWriter pageOutput = new StringWriter();
            output = pageOutput;
            try { super.writePage(); }
            finally { output = documentOutput; }
            if (Thread.currentThread().isInterrupted()) throw new IOException("PDF 처리가 중단됐습니다.");
            int page = getCurrentPageNo();
            finish(page - 1); // PDFBox가 콘텐츠 스트림 없는 빈 페이지는 writePage 호출 없이 건너뛴다.
            String text = pageOutput.toString().replace("\r\n", "\n").replace('\r', '\n')
                    .replace("\u0000", "").strip();
            appendPage(page, text);
        }

        void finish(int throughPage) throws IOException {
            while (lastPage < throughPage) appendPage(lastPage + 1, "");
        }

        private void appendPage(int page, String text) throws IOException {
            if (text.length() > MAX_PAGE_CHARACTERS
                    || characterCount + text.length() > MAX_DOCUMENT_CHARACTERS) {
                throw new IOException("PDF 텍스트 크기 제한을 초과했습니다. 부분 문자열로 잘라 저장하지 않습니다.");
            }
            int sectionOrder = ++order;
            List<ParsedDisclosureBlock> blocks = List.of();
            if (text.codePoints().noneMatch(Character::isLetterOrDigit)) {
                empty.add(page);
            } else {
                boolean suspect = isSuspicious(text);
                if (suspect) suspicious.add(page);
                blocks = List.of(new ParsedDisclosureBlock(ParsedDisclosureBlockType.PARAGRAPH,
                        ++order, text, null, null, -1, -1, new PdfPageLocation(page, suspect)));
                characterCount += text.length();
            }
            // 페이지를 섹션 경계로 사용해 서로 다른 페이지의 문단이 합쳐지지 않게 한다.
            sections.add(new ParsedDisclosureSection(1, sectionOrder, "PDF 페이지 " + page,
                    -1, -1, blocks, List.of()));
            lastPage = page;
        }
    }

    static boolean isSuspicious(String text) {
        if (text.indexOf('\uFFFD') >= 0) return true;
        List<String> lines = text.lines().map(String::strip).filter(s -> !s.isBlank()).toList();
        long singleCharacterLines = lines.stream().filter(s -> s.codePointCount(0, s.length()) == 1).count();
        return lines.size() >= 20 && singleCharacterLines * 100 >= lines.size() * 35L;
    }
}
