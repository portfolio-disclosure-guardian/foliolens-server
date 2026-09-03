package com.foliolens.backend.disclosure.infrastructure.chunking;

import java.util.Objects;

public record DisclosureChunkingPolicy(
        String generatorName,
        String generatorVersion,
        ChunkSizePolicy text,
        ChunkSizePolicy table
) {

    /** XML v3의 분할 규칙을 유지하면서 HTML에도 사용하는 공통 정책. */
    public static DisclosureChunkingPolicy disclosureV1() {
        DisclosureChunkingPolicy legacy = dartXmlV3();
        return new DisclosureChunkingPolicy(
                "DisclosureChunkGenerator", "disclosure-chunk-v1", legacy.text(), legacy.table()
        );
    }

    /** 공통 v1의 분할 규칙 + 기호·공백뿐인 최종 청크 제외. */
    public static DisclosureChunkingPolicy disclosureV2() {
        DisclosureChunkingPolicy previous = disclosureV1();
        return new DisclosureChunkingPolicy(
                "DisclosureChunkGenerator", "disclosure-chunk-v2", previous.text(), previous.table()
        );
    }

    public static DisclosureChunkingPolicy dartXmlV1() {
        return dartXmlPolicy("dart-xml-chunk-v1");
    }

    /**
     * v2는 중첩 표의 상위 셀 검색 문맥을 최대 500자로 제한한다.
     */
    public static DisclosureChunkingPolicy dartXmlV2() {
        return dartXmlPolicy("dart-xml-chunk-v2");
    }

    /**
     * v3는 파서가 보존한 중첩 표 앞·뒤 문맥을 문장 단위로 선택한다.
     */
    public static DisclosureChunkingPolicy dartXmlV3() {
        return dartXmlPolicy("dart-xml-chunk-v3");
    }

    private static DisclosureChunkingPolicy dartXmlPolicy(
            String generatorVersion
    ) {
        return new DisclosureChunkingPolicy(
                "DartXmlDisclosureChunkGenerator",
                generatorVersion,
                // text
                new ChunkSizePolicy(
                        700,   // 목표 하한
                        1_000, // 목표 상한
                        1_400, // 일반 최대
                        2_000  // 절대 최대
                ),
                // table
                new ChunkSizePolicy(
                        1_000, // 목표 하한
                        1_500, // 목표 상한
                        2_000, // 일반 최대
                        3_000  // 절대 최대
                )
        );
    }

    public DisclosureChunkingPolicy {
        generatorName = requireText(
                generatorName,
                "generatorName"
        );

        generatorVersion = requireText(
                generatorVersion,
                "generatorVersion"
        );

        text = Objects.requireNonNull(
                text,
                "text 정책은 필수입니다."
        );

        table = Objects.requireNonNull(
                table,
                "table 정책은 필수입니다."
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

    public record ChunkSizePolicy(
            int targetMinChars, // 목표 하한
            int targetMaxChars, // 목표 상한
            int normalMaxChars, // 일반 최대
            int absoluteMaxChars // 절대 최대
    ) {

        public ChunkSizePolicy {
            if (targetMinChars < 1) {
                throw new IllegalArgumentException(
                        "targetMinChars는 1 이상이어야 합니다."
                );
            }

            if (targetMaxChars < targetMinChars) {
                throw new IllegalArgumentException(
                        "targetMaxChars는 targetMinChars 이상이어야 합니다."
                );
            }

            if (normalMaxChars < targetMaxChars) {
                throw new IllegalArgumentException(
                        "normalMaxChars는 targetMaxChars 이상이어야 합니다."
                );
            }

            if (absoluteMaxChars < normalMaxChars) {
                throw new IllegalArgumentException(
                        "absoluteMaxChars는 normalMaxChars 이상이어야 합니다."
                );
            }
        }

        /**
         * 다음 내용을 추가해도 목표 상한 이내인지 확인한다.
         */
        public boolean fitsTarget(int currentLength, int addedLength) {
            validateLength(currentLength);
            validateLength(addedLength);

            return currentLength + addedLength <= targetMaxChars;
        }

        /**
         * 문단이나 행 경계를 유지하면서 허용할 수 있는지 확인한다.
         */
        public boolean fitsNormalMax(
                int currentLength,
                int addedLength
        ) {
            validateLength(currentLength);
            validateLength(addedLength);

            return currentLength + addedLength <= normalMaxChars;
        }

        public boolean requiresSplit(int length) {
            validateLength(length);
            return length > absoluteMaxChars;
        }

        private static void validateLength(int length) {
            if (length < 0) {
                throw new IllegalArgumentException(
                        "문자 길이는 0 이상이어야 합니다."
                );
            }
        }
    }
}
