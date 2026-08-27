package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.DisclosureSection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SectionPathResolverTest {

    private final SectionPathResolver resolver =
            new SectionPathResolver();

    @Test
    void resolvesNestedPathsAndKeepsInputOrder() {
        UUID rootId = id(1);
        UUID blankChildId = id(2);
        UUID grandChildId = id(3);

        DisclosureSection root = section(
                rootId,
                " I.  회사의 개요 ",
                null
        );
        DisclosureSection blankChild = section(
                blankChildId,
                "  ",
                root
        );
        DisclosureSection grandChild = section(
                grandChildId,
                "주요\n사업",
                blankChild
        );

        Map<UUID, String> result = resolver.resolveAll(
                List.of(grandChild, root, blankChild)
        );

        assertEquals(
                List.of(grandChildId, rootId, blankChildId),
                new ArrayList<>(result.keySet())
        );
        assertEquals(
                "I. 회사의 개요 > 주요 사업",
                result.get(grandChildId)
        );
        assertEquals("I. 회사의 개요", result.get(rootId));
        assertEquals("I. 회사의 개요", result.get(blankChildId));
        assertEquals("문서 서두", resolver.preamblePath());
    }

    @Test
    void rejectsParentMissingFromInput() {
        DisclosureSection missingParent = section(
                id(1),
                "부모",
                null
        );
        DisclosureSection child = section(
                id(2),
                "자식",
                missingParent
        );

        assertThrows(
                IllegalStateException.class,
                () -> resolver.resolveAll(List.of(child))
        );
    }

    @Test
    void rejectsCircularParentRelationship() {
        DisclosureSection first = mock(DisclosureSection.class);
        DisclosureSection second = mock(DisclosureSection.class);

        when(first.getId()).thenReturn(id(1));
        when(first.getTitle()).thenReturn("첫째");
        when(first.getParentSection()).thenReturn(second);
        when(second.getId()).thenReturn(id(2));
        when(second.getTitle()).thenReturn("둘째");
        when(second.getParentSection()).thenReturn(first);

        assertThrows(
                IllegalStateException.class,
                () -> resolver.resolveAll(List.of(first, second))
        );
    }

    @Test
    void rejectsDuplicateAndUnsavedSections() {
        DisclosureSection first = section(id(1), "첫째", null);
        DisclosureSection duplicate = section(id(1), "중복", null);
        DisclosureSection unsaved = section(null, "미저장", null);

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveAll(List.of(first, duplicate))
        );
        assertThrows(
                NullPointerException.class,
                () -> resolver.resolveAll(List.of(unsaved))
        );
    }

    private DisclosureSection section(
            UUID id,
            String title,
            DisclosureSection parent
    ) {
        DisclosureSection section = mock(DisclosureSection.class);
        when(section.getId()).thenReturn(id);
        when(section.getTitle()).thenReturn(title);
        when(section.getParentSection()).thenReturn(parent);
        return section;
    }

    private UUID id(long suffix) {
        return new UUID(0, suffix);
    }
}
