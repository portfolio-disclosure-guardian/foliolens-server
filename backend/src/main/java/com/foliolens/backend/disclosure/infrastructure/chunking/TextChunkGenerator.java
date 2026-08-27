package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;
import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 같은 문서·같은 Section에 있는 HEADING, PARAGRAPH, PAGE_BREAK를 읽어서
 * 검색용 TEXT 청크 후보인 GeneratedChunkDraft를 만드는 클래스
 *
 * HEADING·PARAGRAPH Block
 * → 공백·줄바꿈 정규화
 * → 짧은 문단 결합
 * → 긴 문단 분할
 * → Section·HEADING 문맥 추가
 * → 원본 Block 연결
 * → GeneratedChunkDraft 생성
 */
@Component
public class TextChunkGenerator {

    private static final String PARAGRAPH_SEPARATOR = "\n\n";

    private final DisclosureChunkingPolicy policy;
    private final ChunkTextNormalizer normalizer;
    private final SentenceBoundarySplitter sentenceSplitter;

    public TextChunkGenerator(
            DisclosureChunkingPolicy policy,
            ChunkTextNormalizer normalizer,
            SentenceBoundarySplitter sentenceSplitter
    ) {
        this.policy = Objects.requireNonNull(
                policy,
                "policy는 필수입니다."
        );

        this.normalizer = Objects.requireNonNull(
                normalizer,
                "normalizer는 필수입니다."
        );

        this.sentenceSplitter = Objects.requireNonNull(
                sentenceSplitter,
                "sentenceSplitter는 필수입니다."
        );
    }

    /**
     * 같은 문서·같은 Section에 속한 Block 목록에서
     * TEXT 청크 후보만 생성한다.
     *
     * blocks는 sequenceNo 오름차순이어야 한다.
     */
    public List<GeneratedChunkDraft> generate(
            UUID documentId,
            UUID sectionId,
            String sectionPath,
            List<DisclosureContentBlock> blocks // 같은 section에 포함되어 있는 DisclosureContentBlock들
    ) {
        Objects.requireNonNull(
                documentId,
                "documentId는 필수입니다."
        );

        Objects.requireNonNull(
                blocks,
                "blocks는 필수입니다."
        );

        validateBlocks(
                documentId,
                sectionId,
                blocks
        );

        TextAccumulator accumulator =
                new TextAccumulator(
                        documentId,
                        sectionId,
                        sectionPath
                );


        /*
        * HEADING	    onHeading()	         소제목 문맥 갱신
          PARAGRAPH	    onParagraph()	     문단 추가·분할·결합
          PAGE_BREAK	onPageBreak()	     조건부 청크 종료
          TABLE	        onNonTextBoundary()	 현재 TEXT 종료
          IMAGE	        onNonTextBoundary()	 현재 TEXT 종료
        */

        for (DisclosureContentBlock block : blocks) {
            switch (block.getBlockType()) {
                case HEADING ->
                        accumulator.onHeading(block);

                case PARAGRAPH ->
                        accumulator.onParagraph(block);

                case PAGE_BREAK ->
                        accumulator.onPageBreak();

                case TABLE, IMAGE ->
                        accumulator.onNonTextBoundary();
            }
        }

        return accumulator.finish();
    }

    private void validateBlocks(
            UUID documentId,
            UUID sectionId,
            List<DisclosureContentBlock> blocks
    ) {
        int previousSequence = 0;

        for (DisclosureContentBlock block : blocks) {
            Objects.requireNonNull(
                    block,
                    "Block 목록에는 null이 들어갈 수 없습니다."
            );

            UUID blockId = Objects.requireNonNull(
                    block.getId(),
                    "저장되지 않은 Block은 청크로 만들 수 없습니다."
            );

            UUID actualDocumentId =
                    block.getDisclosureDocument().getId();

            if (!documentId.equals(actualDocumentId)) {
                throw new IllegalArgumentException(
                        "다른 문서의 Block이 포함되어 있습니다."
                                + " blockId=" + blockId
                );
            }

            UUID actualSectionId =
                    block.getSection() == null
                            ? null
                            : block.getSection().getId();

            if (!Objects.equals(sectionId, actualSectionId)) {
                throw new IllegalArgumentException(
                        "다른 Section의 Block이 포함되어 있습니다."
                                + " blockId=" + blockId
                );
            }

            if (block.getSequenceNo() <= previousSequence) {
                throw new IllegalArgumentException(
                        "blocks는 sequenceNo 오름차순이어야 합니다."
                                + " blockId=" + blockId
                );
            }

            previousSequence = block.getSequenceNo();
        }
    }

    /**
     * TextChunkGenerator 내부에서 실제 청크 생성 상태를 관리하는 클래스
     * 지금까지 읽은 HEADING과 PARAGRAPH를 임시로 모으다가 적절한 경계를 만나면 하나의 Draft로 확정
     */
    private final class TextAccumulator {

        private final UUID documentId;
        private final UUID sectionId;
        private final String sectionPath;

        // 완성된 TEXT Draft 목록
        private final List<GeneratedChunkDraft> result = new ArrayList<>();

        // 현재 문단에 적용할 HEADING 목록 -> 연속된 HEADING이 있을 수 있기 때문에 List로 관리
        private final List<HeadingPart> activeHeadings = new ArrayList<>();

        // 현재 하나의 TEXT 청크로 만들기 위해 모으고 있는 문단 조각
        private final List<ParagraphPart> bufferedParagraphs = new ArrayList<>();

        private int bodyLength; // 현재 누적된 본문 길이

        /*
         * 현재 HEADING이 실제 문단에 사용됐는지 기록한다.
         * 이후 새 HEADING이 나오면 기존 문맥을 교체한다.
         */
        private boolean headingContextUsed;

        /*
         * HEADING 이후 TABLE·IMAGE가 나타났는지 기록한다.
         * 그 뒤 새로운 HEADING이 나오면 이전 HEADING을 이어 붙이지 않는다.
         */
        private boolean nonTextBoundaryAfterHeading;

        private TextAccumulator(
                UUID documentId,
                UUID sectionId,
                String sectionPath
        ) {
            this.documentId = documentId;
            this.sectionId = sectionId;
            this.sectionPath = sectionPath == null ? "" : sectionPath.strip();
        }

        /**
         * 기존 문단이 있으면 청크로 확정
         * → HEADING 정규화
         * → activeHeadings에 저장
         */
        private void onHeading(DisclosureContentBlock block) {
            flush();

            /*
             * 이전 HEADING이 본문에 사용됐거나 (연속해서 heading이 나온게 아니라면)
             * 중간에 TABLE·IMAGE가 있었다면
             * 새로운 문맥으로 교체한다.
             *
             * 그렇지 않다면 연속 HEADING으로 보고 누적한다.
             */
            if (headingContextUsed || nonTextBoundaryAfterHeading) {
                activeHeadings.clear();
            }

            String heading = normalizer.normalizeHeading(block.getTextContent());

            if (!heading.isBlank()) {
                activeHeadings.add(
                        new HeadingPart(
                                heading,
                                sourceOf(block)
                        )
                );
            }

            headingContextUsed = false;
            nonTextBoundaryAfterHeading = false;
        }

        /**
         * 문단 정규화
         * → 빈 문단이면 제외
         * → 1,400자를 넘는지 확인
         * → 길면 SentenceBoundarySplitter로 분리
         * → 각 문단 조각을 누적
         */
        private void onParagraph(DisclosureContentBlock block) {
            String paragraph =
                    normalizer.normalizeParagraph(block.getTextContent());

            if (paragraph.isBlank()) {
                return;
            }

            DisclosureChunkingPolicy.ChunkSizePolicy textPolicy = policy.text();

            List<String> parts;

            /*
             * 일반 최대를 넘는 문단은 문장 단위 분리를 시도한다.
             */
            if (paragraph.length() > textPolicy.normalMaxChars()) {
                parts = sentenceSplitter.split(
                        paragraph,
                        textPolicy.normalMaxChars(),
                        textPolicy.absoluteMaxChars()
                );
            } else {
                parts = List.of(paragraph);
            }

            GeneratedChunkSource source = sourceOf(block);

            for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
                appendParagraphPart(
                        new ParagraphPart(
                                parts.get(partIndex),
                                source,
                                partIndex
                        )
                );
            }

            headingContextUsed = true;
        }

        private void onPageBreak() {
            /*
             * PAGE_BREAK는 목표 하한에 도달한 경우에만
             * 현재 청크를 끝내는 소프트 경계다.
             */
            if (bodyLength >= policy.text().targetMinChars()) {
                flush();
            }
        }

        private void onNonTextBoundary() {
            /*
             * TABLE·IMAGE와 TEXT를 하나의 청크에 섞지 않는다.
             */
            flush();
            nonTextBoundaryAfterHeading = true;
        }

        /**
         * 새 문단 조각을 현재 청크에 합칠지, 새로운 청크를 시작할지 결정하는 핵심 메서드
         * 판단과정:
         *  현재 청크가 비어 있음
         *  → 바로 추가
         *
         *  합쳐도 목표 상한 1,000자 이하
         *  → 현재 청크에 추가
         *
         *  현재 청크가 700자 미만이고
         *  합쳐도 일반 최대 1,400자 이하
         *  → 짧은 청크 방지를 위해 추가
         *
         *  그 외
         *  → 현재 청크 flush
         *  → 새 청크 시작
         */
        private void appendParagraphPart(ParagraphPart part) {
            int addedLength = part.text().length();

            if (!bufferedParagraphs.isEmpty()) {
                addedLength += PARAGRAPH_SEPARATOR.length();
            }

            if (bufferedParagraphs.isEmpty()) {
                addPart(part);
                return;
            }

            DisclosureChunkingPolicy.ChunkSizePolicy textPolicy = policy.text();

            boolean fitsTarget =
                    textPolicy.fitsTarget(
                            bodyLength,
                            addedLength
                    );

            /*
             * 현재 청크가 목표 하한보다 짧다면
             * 목표 상한은 넘더라도 일반 최대까지 결합을 허용한다.
             */
            boolean shouldFillShortChunk =
                    bodyLength < textPolicy.targetMinChars() && textPolicy.fitsNormalMax(
                            bodyLength,
                            addedLength
                    );

            if (fitsTarget || shouldFillShortChunk) {
                addPart(part);
                return;
            }

            flush();
            addPart(part);
        }

        // 결합하기로 결정된 문단 조각을 실제 버퍼에 추가
        private void addPart(ParagraphPart part) {
            if (part.text().length() > policy.text().absoluteMaxChars()) {
                throw new IllegalStateException(
                        "분리된 문단이 TEXT 절대 최대를 초과했습니다."
                                + " length=" + part.text().length()
                );
            }

            if (!bufferedParagraphs.isEmpty()) {
                bodyLength += PARAGRAPH_SEPARATOR.length();
            }

            bufferedParagraphs.add(part);
            bodyLength += part.text().length();
        }

        /**
         * 현재까지 모은 문단을 하나의 GeneratedChunkDraft로 확정하는 메서드
         * 처리 순서:
         *  버퍼가 비어 있으면 종료
         *  → 문단들을 bodyText로 결합
         *  → Section·HEADING·본문으로 searchText 생성
         *  → 원본 출처 목록 수집
         *  → GeneratedChunkDraft 생성
         *  → 결과 목록에 추가
         *  → 문단 버퍼 초기화
         */
        private void flush() {
            if (bufferedParagraphs.isEmpty()) {
                return;
            }

            String bodyText =
                    normalizer.joinParagraphs(
                            bufferedParagraphs.stream()
                                    .map(ParagraphPart::text)
                                    .toList()
                    );

            List<String> headingTexts =
                    activeHeadings.stream()
                            .map(HeadingPart::text)
                            .toList();

            String searchText =
                    normalizer.buildSearchText(
                            sectionPath,
                            headingTexts,
                            bodyText
                    );

            List<GeneratedChunkSource> sources = collectSources();

            ParagraphPart firstParagraph = bufferedParagraphs.get(0);

            result.add(
                    new GeneratedChunkDraft(
                            documentId,
                            sectionId,
                            sectionPath,
                            DisclosureChunkType.TEXT,
                            firstParagraph
                                    .source()
                                    .blockSequenceNo(),
                            firstParagraph.partIndex(),
                            bodyText,
                            searchText,
                            sources
                    )
            );

            bufferedParagraphs.clear();
            bodyLength = 0;
        }

        // 현재 청크가 어떤 원본 Block들로 만들어졌는지 수집
        private List<GeneratedChunkSource> collectSources() {
            LinkedHashSet<GeneratedChunkSource> uniqueSources =
                    new LinkedHashSet<>();

            activeHeadings.stream()
                    .map(HeadingPart::source)
                    .forEach(uniqueSources::add);

            bufferedParagraphs.stream()
                    .map(ParagraphPart::source)
                    .forEach(uniqueSources::add);

            return uniqueSources.stream()
                    .sorted(
                            Comparator.comparingInt(
                                    GeneratedChunkSource
                                            ::blockSequenceNo
                            )
                    )
                    .toList();
        }

        private List<GeneratedChunkDraft> finish() {
            flush();
            return List.copyOf(result);
        }
    }

    private GeneratedChunkSource sourceOf(
            DisclosureContentBlock block
    ) {
        return GeneratedChunkSource.block(
                block.getId(),
                block.getSequenceNo(),
                block.getSourceLineStart(),
                block.getSourceLineEnd()
        );
    }

    private record HeadingPart(
            String text,
            GeneratedChunkSource source
    ) {
    }

    private record ParagraphPart(
            String text,
            GeneratedChunkSource source,
            int partIndex
    ) {
    }
}