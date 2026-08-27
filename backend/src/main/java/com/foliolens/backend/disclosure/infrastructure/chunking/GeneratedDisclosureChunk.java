package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Chunk DTO
 * TEXT, TABLE, IMAGE_CAPTION 별 ChunkGenerator가
 * 이 GeneratedDisclosureChunk dto를 만듦
 * 이 dto가 실제 chunk entity로 생성됨
 */
public record GeneratedDisclosureChunk(
        UUID documentId,
        UUID sectionId,  // preamble이면 null
        String sectionPath,  // preamble이면 "문서 서두"
        DisclosureChunkType chunkType,

        int chunkSequenceNo, // 문서 안에서 1부터 시작하는 청크 순서
        String bodyText, // 원문 내용을 검색 가능한 형태로 직렬화한 본문
        String searchText, // Section 경로와 HEADING 등의 문맥을 포함한 검색 문자열
        /*
         * searchText가 무엇인가
         *  sectionPath:
            II. 사업의 내용 > 신규시설투자

            headingContext:
            반도체 생산시설 증설

            bodyText:
            투자금액은 5,000억원이며 2028년까지 집행할 예정이다.

         * searchText:
            II. 사업의 내용 > 신규시설투자
            반도체 생산시설 증설
            투자금액은 5,000억원이며 2028년까지 집행할 예정이다.

         * 검색 품질을 높이기 위한 용도
         */

        List<GeneratedChunkSource> sources, // 출처

        String generatorName,
        String generatorVersion
) {

    public GeneratedDisclosureChunk {
        documentId = Objects.requireNonNull(
                documentId,
                "documentId는 필수입니다."
        );

        chunkType = Objects.requireNonNull(
                chunkType,
                "chunkType은 필수입니다."
        );

        if (chunkSequenceNo < 1) {
            throw new IllegalArgumentException(
                    "chunkSequenceNo는 1 이상이어야 합니다."
            );
        }

        /*
         * 제목 없는 Section은 경로가 빈 문자열일 수 있다.
         * null은 허용하지 않는다.
         */
        sectionPath = normalizePath(sectionPath);

        bodyText = requireText(
                bodyText,
                "bodyText"
        );

        searchText = requireText(
                searchText,
                "searchText"
        );

        sources = List.copyOf(
                Objects.requireNonNull(
                        sources,
                        "sources는 필수입니다."
                )
        );

        if (sources.isEmpty()) {
            throw new IllegalArgumentException(
                    "청크는 원본 출처를 하나 이상 가져야 합니다."
            );
        }

        validateSourceOrder(sources);

        generatorName = requireText(
                generatorName,
                "generatorName"
        );

        generatorVersion = requireText(
                generatorVersion,
                "generatorVersion"
        );
    }

    public int bodyCharacterCount() {
        return bodyText.length();
    }

    public int searchCharacterCount() {
        return searchText.length();
    }

    public int firstSourceSequenceNo() {
        return sources.get(0).blockSequenceNo();
    }

    public int lastSourceSequenceNo() {
        return sources.get(sources.size() - 1).blockSequenceNo();
    }

    /**
     * 알 수 없는 행(-1)은 계산에서 제외한다.
     */
    public int sourceLineStart() {
        return sources.stream()
                .mapToInt(GeneratedChunkSource::sourceLineStart)
                .filter(line -> line >= 0)
                .min()
                .orElse(-1);
    }

    public int sourceLineEnd() {
        return sources.stream()
                .mapToInt(GeneratedChunkSource::sourceLineEnd)
                .filter(line -> line >= 0)
                .max()
                .orElse(-1);
    }

    private static void validateSourceOrder(
            List<GeneratedChunkSource> sources
    ) {
        int previousSequence = -1;

        for (GeneratedChunkSource source : sources) {
            if (source.blockSequenceNo() < previousSequence) {
                throw new IllegalArgumentException(
                        "sources는 원본 Block 순서로 정렬되어야 합니다."
                );
            }

            previousSequence = source.blockSequenceNo();
        }
    }

    private static String normalizePath(String value) {
        if (value == null) {
            return "";
        }

        return value.strip();
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없습니다."
            );
        }

        return value.strip();
    }
}
