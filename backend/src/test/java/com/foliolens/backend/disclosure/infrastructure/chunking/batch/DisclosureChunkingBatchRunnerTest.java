package com.foliolens.backend.disclosure.infrastructure.chunking.batch;

import com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisclosureChunkingBatchRunnerTest {

    @Test
    void batchesAreRepeatedUntilMaxDocumentsIsReached() {
        DisclosureChunkingBatchService batchService =
                mock(DisclosureChunkingBatchService.class);

        when(batchService.processNextBatch(20, DisclosureDocumentContentFormat.DART_XML, null))
                .thenReturn(successResult(20))
                .thenReturn(successResult(20));
        when(batchService.processNextBatch(5, DisclosureDocumentContentFormat.DART_XML, null))
                .thenReturn(successResult(5));

        DisclosureChunkingBatchRunner runner =
                new DisclosureChunkingBatchRunner(
                        batchService,
                        20,
                        45,
                        5,
                        DisclosureDocumentContentFormat.DART_XML,
                        ""
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService, org.mockito.Mockito.times(2))
                .processNextBatch(20, DisclosureDocumentContentFormat.DART_XML, null);
        verify(batchService).processNextBatch(5, DisclosureDocumentContentFormat.DART_XML, null);
    }

    @Test
    void unlimitedRunStopsAfterPartialFinalBatch() {
        DisclosureChunkingBatchService batchService =
                mock(DisclosureChunkingBatchService.class);

        when(batchService.processNextBatch(10, DisclosureDocumentContentFormat.DART_XML, null))
                .thenReturn(successResult(3));

        DisclosureChunkingBatchRunner runner =
                new DisclosureChunkingBatchRunner(
                        batchService,
                        10,
                        0,
                        5,
                        DisclosureDocumentContentFormat.DART_XML,
                        ""
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService).processNextBatch(10, DisclosureDocumentContentFormat.DART_XML, null);
    }

    @Test
    void noPendingDocumentsEndsNormally() {
        DisclosureChunkingBatchService batchService =
                mock(DisclosureChunkingBatchService.class);

        when(batchService.processNextBatch(10, DisclosureDocumentContentFormat.DART_XML, null))
                .thenReturn(emptyResult());

        DisclosureChunkingBatchRunner runner =
                new DisclosureChunkingBatchRunner(
                        batchService,
                        10,
                        0,
                        5,
                        DisclosureDocumentContentFormat.DART_XML,
                        ""
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService).processNextBatch(10, DisclosureDocumentContentFormat.DART_XML, null);
    }

    @Test
    void runStopsWhenCumulativeFailureThresholdIsReached() {
        DisclosureChunkingBatchService batchService =
                mock(DisclosureChunkingBatchService.class);

        when(batchService.processNextBatch(10, DisclosureDocumentContentFormat.DART_XML, null))
                .thenReturn(failedResult(10, 1))
                .thenReturn(failedResult(10, 1));

        DisclosureChunkingBatchRunner runner =
                new DisclosureChunkingBatchRunner(
                        batchService,
                        10,
                        100,
                        2,
                        DisclosureDocumentContentFormat.DART_XML,
                        ""
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService, org.mockito.Mockito.times(2))
                .processNextBatch(10, DisclosureDocumentContentFormat.DART_XML, null);
    }

    @Test
    void invalidLimitsAreRejected() {
        DisclosureChunkingBatchService batchService =
                mock(DisclosureChunkingBatchService.class);

        assertThatThrownBy(() ->
                new DisclosureChunkingBatchRunner(
                        batchService,
                        0,
                        50,
                        5,
                        DisclosureDocumentContentFormat.DART_XML,
                        ""
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");

        assertThatThrownBy(() ->
                new DisclosureChunkingBatchRunner(
                        batchService,
                        101,
                        50,
                        5,
                        DisclosureDocumentContentFormat.DART_XML,
                        ""
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize는 1~100");

        assertThatThrownBy(() ->
                new DisclosureChunkingBatchRunner(
                        batchService,
                        10,
                        -1,
                        5,
                        DisclosureDocumentContentFormat.DART_XML,
                        ""
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDocuments");

        assertThatThrownBy(() ->
                new DisclosureChunkingBatchRunner(
                        batchService,
                        10,
                        50,
                        -1,
                        DisclosureDocumentContentFormat.DART_XML,
                        ""
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxFailures");
    }

    @Test
    void htmlPilotUsesConfiguredFormatSubtypeAndMaximum() {
        var service = mock(DisclosureChunkingBatchService.class);
        when(service.processNextBatch(5, DisclosureDocumentContentFormat.HTML, "신규시설투자등"))
                .thenReturn(successResult(5));
        var runner = new DisclosureChunkingBatchRunner(service, 10, 5, 1,
                DisclosureDocumentContentFormat.HTML, " 신규시설투자등 ");
        runner.run(mock(ApplicationArguments.class));
        verify(service).processNextBatch(5, DisclosureDocumentContentFormat.HTML, "신규시설투자등");
        org.mockito.Mockito.verifyNoMoreInteractions(service);
    }

    @Test
    void unsupportedFormatIsRejectedAtStartup() {
        assertThatThrownBy(() -> new DisclosureChunkingBatchRunner(
                mock(DisclosureChunkingBatchService.class), 5, 5, 1,
                DisclosureDocumentContentFormat.UNKNOWN, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DART_XML, HTML 또는 PDF");
    }

    @Test
    void springDefaultsKeepXmlAndStartupOptIn() {
        var service = mock(DisclosureChunkingBatchService.class);
        when(service.processNextBatch(10, DisclosureDocumentContentFormat.DART_XML, null))
                .thenReturn(emptyResult());
        var contextRunner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(DisclosureChunkingBatchService.class, () -> service)
                .withUserConfiguration(DisclosureChunkingBatchRunner.class);
        contextRunner.run(context -> org.assertj.core.api.Assertions.assertThat(context)
                .doesNotHaveBean(DisclosureChunkingBatchRunner.class));
        contextRunner.withPropertyValues("foliolens.chunking.batch.enabled=true").run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
            context.getBean(DisclosureChunkingBatchRunner.class).run(mock(ApplicationArguments.class));
        });
        verify(service).processNextBatch(10, DisclosureDocumentContentFormat.DART_XML, null);
    }

    private DisclosureChunkingBatchResult successResult(int count) {
        List<DisclosureChunkingBatchRow> rows = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            rows.add(successRow(index));
        }

        return result(rows);
    }

    private DisclosureChunkingBatchResult failedResult(
            int count,
            int failedCount
    ) {
        List<DisclosureChunkingBatchRow> rows = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            if (index < failedCount) {
                rows.add(failedRow(index));
            } else {
                rows.add(successRow(index));
            }
        }

        return result(rows);
    }

    private DisclosureChunkingBatchResult emptyResult() {
        return result(List.of());
    }

    private DisclosureChunkingBatchResult result(
            List<DisclosureChunkingBatchRow> rows
    ) {
        return new DisclosureChunkingBatchResult(
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1),
                rows
        );
    }

    private DisclosureChunkingBatchRow successRow(int index) {
        return new DisclosureChunkingBatchRow(
                UUID.randomUUID(),
                receiptNo(index),
                "success-" + index + ".xml",
                DisclosureChunkingBatchStatus.SUCCESS,
                0,
                2,
                3,
                1,
                null
        );
    }

    private DisclosureChunkingBatchRow failedRow(int index) {
        return new DisclosureChunkingBatchRow(
                UUID.randomUUID(),
                receiptNo(index),
                "failed-" + index + ".xml",
                DisclosureChunkingBatchStatus.FAILED,
                0,
                0,
                0,
                1,
                "테스트 실패"
        );
    }

    private String receiptNo(int index) {
        return "%014d".formatted(index + 1L);
    }
}
