package com.foliolens.backend.disclosure.infrastructure.chunking.batch;

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

        when(batchService.processNextBatch(20))
                .thenReturn(successResult(20))
                .thenReturn(successResult(20));
        when(batchService.processNextBatch(5))
                .thenReturn(successResult(5));

        DisclosureChunkingBatchRunner runner =
                new DisclosureChunkingBatchRunner(
                        batchService,
                        20,
                        45,
                        5
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService, org.mockito.Mockito.times(2))
                .processNextBatch(20);
        verify(batchService).processNextBatch(5);
    }

    @Test
    void unlimitedRunStopsAfterPartialFinalBatch() {
        DisclosureChunkingBatchService batchService =
                mock(DisclosureChunkingBatchService.class);

        when(batchService.processNextBatch(10))
                .thenReturn(successResult(3));

        DisclosureChunkingBatchRunner runner =
                new DisclosureChunkingBatchRunner(
                        batchService,
                        10,
                        0,
                        5
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService).processNextBatch(10);
    }

    @Test
    void noPendingDocumentsEndsNormally() {
        DisclosureChunkingBatchService batchService =
                mock(DisclosureChunkingBatchService.class);

        when(batchService.processNextBatch(10))
                .thenReturn(emptyResult());

        DisclosureChunkingBatchRunner runner =
                new DisclosureChunkingBatchRunner(
                        batchService,
                        10,
                        0,
                        5
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService).processNextBatch(10);
    }

    @Test
    void runStopsWhenCumulativeFailureThresholdIsReached() {
        DisclosureChunkingBatchService batchService =
                mock(DisclosureChunkingBatchService.class);

        when(batchService.processNextBatch(10))
                .thenReturn(failedResult(10, 1))
                .thenReturn(failedResult(10, 1));

        DisclosureChunkingBatchRunner runner =
                new DisclosureChunkingBatchRunner(
                        batchService,
                        10,
                        100,
                        2
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService, org.mockito.Mockito.times(2))
                .processNextBatch(10);
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
                        5
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");

        assertThatThrownBy(() ->
                new DisclosureChunkingBatchRunner(
                        batchService,
                        101,
                        50,
                        5
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize는 1~100");

        assertThatThrownBy(() ->
                new DisclosureChunkingBatchRunner(
                        batchService,
                        10,
                        -1,
                        5
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDocuments");

        assertThatThrownBy(() ->
                new DisclosureChunkingBatchRunner(
                        batchService,
                        10,
                        50,
                        -1
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxFailures");
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
