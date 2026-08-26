package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.chunking.DisclosureChunkingPolicy;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisclosureChunkFailureRecorderTest {

    private static final UUID DOCUMENT_ID = new UUID(700, 1);

    private DisclosureDocumentRepository documentRepository;
    private DisclosureChunkingPolicy policy;
    private DisclosureChunkFailureRecorder recorder;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DisclosureDocumentRepository.class);
        policy = DisclosureChunkingPolicy.dartXmlV1();
        recorder = new DisclosureChunkFailureRecorder(
                documentRepository,
                policy
        );
    }

    @Test
    void recordsRootCauseWithCurrentGeneratorMetadata() {
        DisclosureDocument document = mock(DisclosureDocument.class);
        RuntimeException failure = new RuntimeException(
                "상위 오류",
                new IllegalArgumentException("실제 청킹 실패 원인")
        );

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        recorder.markFailed(DOCUMENT_ID, failure);

        ArgumentCaptor<Instant> failedAtCaptor =
                ArgumentCaptor.forClass(Instant.class);

        verify(document).markChunkingFailed(
                eq(policy.generatorName()),
                eq(policy.generatorVersion()),
                eq("IllegalArgumentException: 실제 청킹 실패 원인"),
                failedAtCaptor.capture()
        );
        assertThat(failedAtCaptor.getValue()).isNotNull();
        verify(documentRepository).flush();
    }

    @Test
    void truncatesErrorMessageToDatabaseLimit() {
        DisclosureDocument document = mock(DisclosureDocument.class);
        RuntimeException failure = new RuntimeException("가".repeat(5_000));

        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.of(document));

        recorder.markFailed(DOCUMENT_ID, failure);

        ArgumentCaptor<String> messageCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(document).markChunkingFailed(
                eq(policy.generatorName()),
                eq(policy.generatorVersion()),
                messageCaptor.capture(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
        assertThat(messageCaptor.getValue()).hasSize(4_000);
    }

    @Test
    void rejectsMissingDocument() {
        when(documentRepository.findById(DOCUMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                recorder.markFailed(
                        DOCUMENT_ID,
                        new RuntimeException("실패")
                )
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("실패 상태를 기록할 원문 문서");
    }
}
