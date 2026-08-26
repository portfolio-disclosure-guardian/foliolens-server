package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.DisclosureChunkType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;


/**
 * TextChunker, TableChunker 등이 만든 임시 청크를 담아두고,
 * 전체 순서와 검색 문자열 등을 확정한 후 GeneratedDisclosureChunk로 변환하기 위한 중간 객체
 *
 * 왜 바로 GeneratedDisclosureChunk를 만들지 않는가?
 *  TextChunker가 문단 청크 하나를 만들었을 때는 chunkSequenceNo를 알기 어려움
 *  각 컴포넌트는 자신이 만든 청크만 알고 있다. 모든 청크를 합쳤을 때 몇 번째 청크가 되는지는 모릅
 *  그래서 먼저 Draft로 반환
 *  나중에 총괄 DisclosureChunkGenerator가 원문 순서대로 정렬
 */
public record GeneratedChunkDraft(
        UUID documentId,
        UUID sectionId,
        String sectionPath,
        DisclosureChunkType chunkType,

        /*
         * 최종 chunkSequenceNo를 정하기 전에
         * 원문 순서를 비교하기 위한 값이다.
         */
        int anchorBlockSequenceNo,

        /*
         * 한 Block이 여러 청크로 분리될 때 사용하는 순서다.
         * 분리되지 않았으면 0이다.
         */
        int anchorPartIndex,

        String bodyText,
        String searchText,
        List<GeneratedChunkSource> sources
) {

    public GeneratedChunkDraft {
        documentId = Objects.requireNonNull(
                documentId,
                "documentId는 필수입니다."
        );

        chunkType = Objects.requireNonNull(
                chunkType,
                "chunkType은 필수입니다."
        );

        if (anchorBlockSequenceNo < 1) {
            throw new IllegalArgumentException(
                    "anchorBlockSequenceNo는 1 이상이어야 합니다."
            );
        }

        if (anchorPartIndex < 0) {
            throw new IllegalArgumentException(
                    "anchorPartIndex는 0 이상이어야 합니다."
            );
        }

        sectionPath = sectionPath == null ? "" : sectionPath.strip();

        bodyText = requireText(bodyText, "bodyText");
        searchText = requireText(searchText, "searchText");

        sources = List.copyOf(
                Objects.requireNonNull(
                        sources,
                        "sources는 필수입니다."
                )
        );

        if (sources.isEmpty()) {
            throw new IllegalArgumentException(
                    "청크 출처가 하나 이상 필요합니다."
            );
        }
    }

    public GeneratedDisclosureChunk complete(
            int chunkSequenceNo,
            DisclosureChunkingPolicy policy
    ) {
        Objects.requireNonNull(policy, "policy는 필수입니다.");

        return new GeneratedDisclosureChunk(
                documentId,
                sectionId,
                sectionPath,
                chunkType,
                chunkSequenceNo,
                bodyText,
                searchText,
                sources,
                policy.generatorName(),
                policy.generatorVersion()
        );
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
