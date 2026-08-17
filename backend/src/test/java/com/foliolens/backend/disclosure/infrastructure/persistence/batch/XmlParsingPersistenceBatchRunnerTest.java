package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

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

class XmlParsingPersistenceBatchRunnerTest {

    @Test
    void chunksAreRepeatedUntilMaxDocumentsIsReached() {
        XmlParsingPersistenceBatchService batchService =
                mock(XmlParsingPersistenceBatchService.class);

        when(batchService.persistNextChunk(50))
                .thenReturn(successResult(50))
                .thenReturn(successResult(50));
        when(batchService.persistNextChunk(20))
                .thenReturn(successResult(20));

        XmlParsingPersistenceBatchRunner runner =
                new XmlParsingPersistenceBatchRunner(
                        batchService,
                        50,
                        120,
                        10
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService, org.mockito.Mockito.times(2))
                .persistNextChunk(50);
        verify(batchService).persistNextChunk(20);
    }

    @Test
    void unlimitedRunStopsAfterAPartialFinalChunk() {
        XmlParsingPersistenceBatchService batchService =
                mock(XmlParsingPersistenceBatchService.class);

        when(batchService.persistNextChunk(50))
                .thenReturn(successResult(2));

        XmlParsingPersistenceBatchRunner runner =
                new XmlParsingPersistenceBatchRunner(
                        batchService,
                        50,
                        0,
                        10
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService).persistNextChunk(50);
    }

    @Test
    void noPendingDocumentsEndsNormally() {
        XmlParsingPersistenceBatchService batchService =
                mock(XmlParsingPersistenceBatchService.class);

        when(batchService.persistNextChunk(50))
                .thenReturn(emptyResult());

        XmlParsingPersistenceBatchRunner runner =
                new XmlParsingPersistenceBatchRunner(
                        batchService,
                        50,
                        0,
                        10
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService).persistNextChunk(50);
    }

    @Test
    void runStopsWhenFailureThresholdIsReached() {
        XmlParsingPersistenceBatchService batchService =
                mock(XmlParsingPersistenceBatchService.class);

        when(batchService.persistNextChunk(50))
                .thenReturn(failedResult(50, 2));

        XmlParsingPersistenceBatchRunner runner =
                new XmlParsingPersistenceBatchRunner(
                        batchService,
                        50,
                        500,
                        2
                );

        runner.run(mock(ApplicationArguments.class));

        verify(batchService).persistNextChunk(50);
    }

    @Test
    void negativeMaximumValuesAreRejected() {
        XmlParsingPersistenceBatchService batchService =
                mock(XmlParsingPersistenceBatchService.class);

        assertThatThrownBy(() ->
                new XmlParsingPersistenceBatchRunner(
                        batchService,
                        50,
                        -1,
                        10
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDocuments");

        assertThatThrownBy(() ->
                new XmlParsingPersistenceBatchRunner(
                        batchService,
                        50,
                        500,
                        -1
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxFailures");
    }

    private XmlParsingPersistenceBatchResult successResult(int count) {
        List<XmlParsingPersistenceRow> rows = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            rows.add(successRow(index));
        }

        return result(rows);
    }

    private XmlParsingPersistenceBatchResult failedResult(
            int count,
            int failedCount
    ) {
        List<XmlParsingPersistenceRow> rows = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            if (index < failedCount) {
                rows.add(failedRow(index));
            } else {
                rows.add(successRow(index));
            }
        }

        return result(rows);
    }

    private XmlParsingPersistenceBatchResult emptyResult() {
        return result(List.of());
    }

    private XmlParsingPersistenceBatchResult result(
            List<XmlParsingPersistenceRow> rows
    ) {
        return new XmlParsingPersistenceBatchResult(
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1),
                rows
        );
    }

    private XmlParsingPersistenceRow successRow(int index) {
        return new XmlParsingPersistenceRow(
                UUID.randomUUID(),
                receiptNo(index),
                "success-" + index + ".xml",
                XmlParsingPersistenceStatus.SUCCESS,
                0,
                0,
                1,
                2,
                1,
                null
        );
    }

    private XmlParsingPersistenceRow failedRow(int index) {
        return new XmlParsingPersistenceRow(
                UUID.randomUUID(),
                receiptNo(index),
                "failed-" + index + ".xml",
                XmlParsingPersistenceStatus.FAILED,
                0,
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
