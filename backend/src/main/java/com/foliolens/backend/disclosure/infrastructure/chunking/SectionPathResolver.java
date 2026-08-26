package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.domain.DisclosureSection;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class SectionPathResolver {

    public static final String PATH_SEPARATOR = " > ";
    public static final String PREAMBLE_PATH = "문서 서두";

    /**
     * 문서 하나에 속한 전체 Section 경로를 생성한다.
     *
     * 입력 목록의 순서를 유지하기 위해 LinkedHashMap을 반환한다.
     */
    public Map<UUID, String> resolveAll(List<DisclosureSection> sections) {

        Objects.requireNonNull(
                sections,
                "sections는 필수입니다."
        );

        Map<UUID, DisclosureSection> sectionsById = createSectionMap(sections);
        Map<UUID, String> resolvedCache = new HashMap<>();
        Map<UUID, String> result = new LinkedHashMap<>();

        for (DisclosureSection section : sections) {
            String path = resolve(
                    section,
                    sectionsById,
                    resolvedCache,
                    new HashSet<>()
            );

            result.put(section.getId(), path);
        }

        return Collections.unmodifiableMap(
                new LinkedHashMap<>(result)
        );
    }

    public String preamblePath() {
        return PREAMBLE_PATH;
    }

    private Map<UUID, DisclosureSection> createSectionMap(
            List<DisclosureSection> sections
    ) {
        Map<UUID, DisclosureSection> result = new HashMap<>();

        for (DisclosureSection section : sections) {
            Objects.requireNonNull(
                    section,
                    "Section 목록에는 null이 들어갈 수 없습니다."
            );

            UUID sectionId = Objects.requireNonNull(
                    section.getId(),
                    "저장되지 않은 Section은 경로를 생성할 수 없습니다."
            );

            DisclosureSection previous =
                    result.put(sectionId, section);

            if (previous != null) {
                throw new IllegalArgumentException(
                        "중복 Section ID가 존재합니다. sectionId="
                                + sectionId
                );
            }
        }

        return result;
    }

    private String resolve(
            DisclosureSection section,
            Map<UUID, DisclosureSection> sectionsById,
            Map<UUID, String> resolvedCache,
            Set<UUID> visiting
    ) {
        UUID sectionId = section.getId();

        String cached = resolvedCache.get(sectionId);

        if (cached != null) {
            return cached;
        }

        if (!visiting.add(sectionId)) {
            throw new IllegalStateException(
                    "Section 부모 관계에 순환이 존재합니다. sectionId="
                            + sectionId
            );
        }

        try {
            String parentPath = resolveParentPath(
                    section,
                    sectionsById,
                    resolvedCache,
                    visiting
            );

            String currentTitle = normalizeTitle(section.getTitle());

            String resolvedPath = combine(
                    parentPath,
                    currentTitle
            );

            resolvedCache.put(
                    sectionId,
                    resolvedPath
            );

            return resolvedPath;
        } finally {
            visiting.remove(sectionId);
        }
    }

    private String resolveParentPath(
            DisclosureSection section,
            Map<UUID, DisclosureSection> sectionsById,
            Map<UUID, String> resolvedCache,
            Set<UUID> visiting
    ) {
        DisclosureSection parent = section.getParentSection();

        if (parent == null) {
            return "";
        }

        UUID parentId = Objects.requireNonNull(
                parent.getId(),
                "저장되지 않은 부모 Section을 참조하고 있습니다."
        );

        DisclosureSection parentInDocument = sectionsById.get(parentId);

        if (parentInDocument == null) {
            throw new IllegalStateException(
                    "부모 Section이 입력 목록에 없습니다."
                            + " sectionId=" + section.getId()
                            + ", parentSectionId=" + parentId
            );
        }

        return resolve(
                parentInDocument,
                sectionsById,
                resolvedCache,
                visiting
        );
    }

    private String combine(
            String parentPath,
            String currentTitle
    ) {
        boolean hasParent =
                parentPath != null && !parentPath.isBlank();

        boolean hasCurrent =
                currentTitle != null && !currentTitle.isBlank();

        if (hasParent && hasCurrent) {
            return parentPath
                    + PATH_SEPARATOR
                    + currentTitle;
        }

        if (hasParent) {
            return parentPath;
        }

        if (hasCurrent) {
            return currentTitle;
        }

        return "";
    }

    private String normalizeTitle(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value
                .replaceAll("\\s+", " ")
                .strip();
    }
}
