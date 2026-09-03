package com.foliolens.backend.disclosure.infrastructure.parsing.html.validation;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HtmlParsingValidationBatchRunnerTest {
    @Test void rejectsPartialPageWhenFull43ValidationWasRequested() {
        var service = mock(HtmlParsingValidationBatchService.class);
        var writer = mock(HtmlParsingValidationReportWriter.class);
        var result = new HtmlParsingValidationBatchResult(Instant.now(), Instant.now(), 43, List.of());
        when(service.validate(0, 10, "신규시설투자등")).thenReturn(result);
        when(writer.write(Path.of("result.csv"), result)).thenReturn(Path.of("result.csv"));
        var runner = new HtmlParsingValidationBatchRunner(service, writer, 0, 10, 43, "신규시설투자등", "result.csv");
        assertThatThrownBy(() -> runner.run(null)).hasMessageContaining("전체 검증");
        verify(writer).write(Path.of("result.csv"), result);
    }
}
