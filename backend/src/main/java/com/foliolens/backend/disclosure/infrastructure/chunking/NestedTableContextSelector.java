package com.foliolens.backend.disclosure.infrastructure.chunking;

import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureTableContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 중첩 표의 앞·뒤 직접 텍스트에서 검색에 사용할 가까운 문맥을 고른다.
 *
 * <p>표 앞 문맥은 끝부분을, 표 뒤 문맥은 시작부분을 우선한다.
 * 일반적인 경우 문장 경계를 보존하고, 단일 문장 자체가 예산보다
 * 긴 경우에만 {@link SentenceBoundarySplitter}의 안전 분할을 사용한다.</p>
 */
@Component
public class NestedTableContextSelector {

    /*
     * 여러 중첩 단계의 문맥을 합칠 때 가장 가까운 부모 표 문맥을
     * 우선 보존할 수 있도록 직접 부모 문맥에는 350자를 배정한다.
     */
    static final int MAX_SELECTED_CONTEXT_CHARS = 350;

    private static final int CONTEXT_SEPARATOR_CHARS = 1;

    private final ChunkTextNormalizer textNormalizer;
    private final SentenceBoundarySplitter sentenceSplitter;

    public NestedTableContextSelector(
            ChunkTextNormalizer textNormalizer,
            SentenceBoundarySplitter sentenceSplitter
    ) {
        this.textNormalizer = Objects.requireNonNull(
                textNormalizer,
                "textNormalizer는 필수입니다."
        );
        this.sentenceSplitter = Objects.requireNonNull(
                sentenceSplitter,
                "sentenceSplitter는 필수입니다."
        );
    }

    /**
     * 부모 문맥이 없는 최상위 표나 v1 TABLE payload에는
     * 앞·뒤가 모두 비어 있는 문맥을 반환한다.
     */
    public ParsedDisclosureTableContext select(
            ParsedDisclosureTableContext context
    ) {
        if (context == null || context.isEmpty()) {
            return emptyContext();
        }

        String precedingText = normalize(context.precedingText());
        String followingText = normalize(context.followingText());

        if (precedingText.isBlank()) {
            return new ParsedDisclosureTableContext(
                    null,
                    selectFollowing(
                            followingText,
                            MAX_SELECTED_CONTEXT_CHARS
                    )
            );
        }

        if (followingText.isBlank()) {
            return new ParsedDisclosureTableContext(
                    selectPreceding(
                            precedingText,
                            MAX_SELECTED_CONTEXT_CHARS
                    ),
                    null
            );
        }

        return selectBothSides(
                precedingText,
                followingText
        );
    }

    private ParsedDisclosureTableContext selectBothSides(
            String precedingText,
            String followingText
    ) {
        int contentBudget = MAX_SELECTED_CONTEXT_CHARS
                - CONTEXT_SEPARATOR_CHARS;

        int precedingBudget = contentBudget / 2;
        int followingBudget = contentBudget - precedingBudget;

        /*
         * 한쪽 원문이 배정된 예산보다 짧으면 남는 길이를
         * 반대쪽에 넘겨 불필요하게 문맥을 버리지 않는다.
         */
        if (precedingText.length() < precedingBudget) {
            int unused = precedingBudget - precedingText.length();
            precedingBudget -= unused;
            followingBudget += unused;
        }

        if (followingText.length() < followingBudget) {
            int unused = followingBudget - followingText.length();
            followingBudget -= unused;
            precedingBudget += unused;
        }

        return new ParsedDisclosureTableContext(
                selectPreceding(
                        precedingText,
                        precedingBudget
                ),
                selectFollowing(
                        followingText,
                        followingBudget
                )
        );
    }

    /**
     * TABLE 직전에 가까운 마지막 문장 묶음을 선택한다.
     */
    private String selectPreceding(
            String value,
            int maxChars
    ) {
        List<String> parts = split(value, maxChars);

        return parts.isEmpty()
                ? null
                : parts.getLast();
    }

    /**
     * TABLE 직후에 가까운 첫 문장 묶음을 선택한다.
     */
    private String selectFollowing(
            String value,
            int maxChars
    ) {
        List<String> parts = split(value, maxChars);

        return parts.isEmpty()
                ? null
                : parts.getFirst();
    }

    private List<String> split(
            String value,
            int maxChars
    ) {
        if (value == null || value.isBlank() || maxChars < 1) {
            return List.of();
        }

        return sentenceSplitter.split(
                value,
                maxChars,
                maxChars
        );
    }

    private String normalize(String value) {
        return textNormalizer.normalizeHeading(value);
    }

    private ParsedDisclosureTableContext emptyContext() {
        return new ParsedDisclosureTableContext(
                null,
                null
        );
    }
}
