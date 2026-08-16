package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureContentBlock;
import com.foliolens.backend.disclosure.domain.DisclosureSection;

import java.util.List;
import java.util.Objects;

/**
 * 파싱 모델을 DB 저장용 엔티티로 변환한 결과.
 *
 * 아직 DB에 저장된 상태는 아니며,
 * DisclosureSection과 DisclosureContentBlock 엔티티 목록만 보유한다.
 */
public record DisclosureParseMappingResult(
        List<DisclosureSection> sections,
        List<DisclosureContentBlock> blocks
) {

    public DisclosureParseMappingResult {
        sections = List.copyOf(
                Objects.requireNonNull(
                        sections,
                        "sections는 필수입니다."
                )
        );

        blocks = List.copyOf(
                Objects.requireNonNull(
                        blocks,
                        "blocks는 필수입니다."
                )
        );
    }
}
