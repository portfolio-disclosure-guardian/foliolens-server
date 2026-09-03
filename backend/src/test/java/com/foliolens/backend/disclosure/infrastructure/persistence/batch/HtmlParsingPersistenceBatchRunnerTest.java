package com.foliolens.backend.disclosure.infrastructure.persistence.batch;

import com.foliolens.backend.disclosure.infrastructure.persistence.DisclosureParsingPersistenceResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;

class HtmlParsingPersistenceBatchRunnerTest {
    @Test void stopsAtMaxDocumentsAndNextBatchAlwaysStartsWithPending() {
        var service = mock(HtmlParsingPersistenceBatchService.class);
        var one = new DisclosureParsingPersistenceResult(UUID.randomUUID(), 0, 0, 1, 1);
        when(service.persistNextBatch(2, "신규시설투자등"))
                .thenReturn(new HtmlParsingPersistenceBatchResult(List.of(one, one), Map.of()));
        when(service.persistNextBatch(1, "신규시설투자등"))
                .thenReturn(new HtmlParsingPersistenceBatchResult(List.of(one), Map.of()));
        new HtmlParsingPersistenceBatchRunner(service, 2, 3, 5, "신규시설투자등").run(null);
        verify(service).persistNextBatch(2, "신규시설투자등");
        verify(service).persistNextBatch(1, "신규시설투자등");
        verifyNoMoreInteractions(service);
    }

    @Test void stopsAtFailureBudget() {
        var service = mock(HtmlParsingPersistenceBatchService.class);
        when(service.persistNextBatch(1, "신규시설투자등"))
                .thenReturn(new HtmlParsingPersistenceBatchResult(List.of(), Map.of(UUID.randomUUID(), "실패")));
        new HtmlParsingPersistenceBatchRunner(service, 10, 43, 1, "신규시설투자등").run(null);
        verify(service).persistNextBatch(1, "신규시설투자등");
        verifyNoMoreInteractions(service);
    }
}
