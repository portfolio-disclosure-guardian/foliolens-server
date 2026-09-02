package com.foliolens.backend.disclosure.domain.fact;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisclosureEvidenceTest {

    @Test
    void 표_셀의_원문값과_행_셀_위치를_보존한다() {
        UUID contentBlockId = UUID.randomUUID();

        DisclosureEvidence evidence = new DisclosureEvidence(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "20240424800596",
                "20240424800596.xml",
                DisclosureDocumentRole.MAIN,
                EventDocumentRole.ORIGINAL,
                UUID.randomUUID(),
                "신규시설투자 > 투자내역",
                contentBlockId,
                EvidenceBlockType.TABLE_CELL,
                "투자내역",
                new DisclosureEvidenceLocation(
                        100,
                        120,
                        "table.rows[2]",
                        2,
                        1
                ),
                new DisclosureEvidenceValue(
                        "투자금액 | 5,296,200",
                        "투자금액",
                        "금액",
                        "5,296,200",
                        "백만원",
                        "VAT 제외 여부는 기타사항 확인 필요"
                ),
                EvidenceStatus.VERIFIED
        );

        assertThat(evidence.verified()).isTrue();
        assertThat(evidence.contentBlockId()).isEqualTo(contentBlockId);
        assertThat(evidence.location().tableRowIndex()).isEqualTo(2);
        assertThat(evidence.location().tableCellIndex()).isEqualTo(1);
        assertThat(evidence.value().rawValue()).isEqualTo("5,296,200");
        assertThat(evidence.value().rawUnit()).isEqualTo("백만원");
    }

    @Test
    void TABLE_ROW에는_행_위치가_필요하다() {
        assertThatThrownBy(() -> evidence(
                EvidenceBlockType.TABLE_ROW,
                DisclosureEvidenceLocation.unknown(),
                new DisclosureEvidenceValue(
                        "투자금액 | 5,296,200",
                        "투자금액",
                        null,
                        "5,296,200",
                        "백만원",
                        null
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableRowIndex");
    }

    @Test
    void 문단_Evidence에는_표_위치를_지정할_수_없다() {
        assertThatThrownBy(() -> evidence(
                EvidenceBlockType.PARAGRAPH,
                new DisclosureEvidenceLocation(
                        30,
                        31,
                        null,
                        0,
                        null
                ),
                new DisclosureEvidenceValue(
                        "차세대 DRAM 생산능력 확장",
                        null,
                        null,
                        "차세대 DRAM 생산능력 확장",
                        null,
                        null
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TABLE 계열");
    }

    @Test
    void 원문_행은_모두_미확인이거나_유효한_범위여야_한다() {
        assertThatThrownBy(() -> new DisclosureEvidenceLocation(
                -1,
                20,
                null,
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("원문 행");
    }

    private DisclosureEvidence evidence(
            EvidenceBlockType blockType,
            DisclosureEvidenceLocation location,
            DisclosureEvidenceValue value
    ) {
        return new DisclosureEvidence(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "20240424800596",
                "20240424800596.xml",
                DisclosureDocumentRole.MAIN,
                EventDocumentRole.ORIGINAL,
                UUID.randomUUID(),
                "신규시설투자 > 투자내역",
                UUID.randomUUID(),
                blockType,
                null,
                location,
                value,
                EvidenceStatus.CANDIDATE
        );
    }
}
