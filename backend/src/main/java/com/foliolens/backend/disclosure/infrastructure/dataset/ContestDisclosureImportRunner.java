package com.foliolens.backend.disclosure.infrastructure.dataset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
@ConditionalOnProperty(
        prefix = "foliolens.dataset",
        name = "import-disclosures-on-startup",
        havingValue = "true"
)
public class ContestDisclosureImportRunner implements ApplicationRunner {

    private final ContestDisclosureImporter disclosureImporter;

    @Override
    public void run(ApplicationArguments args) {
        log.info(
                "Contest disclosure manifest import started."
        );

        ContestDisclosureImporter.ImportResult result =
                disclosureImporter.importDisclosures();

        log.info(
                "Contest disclosure manifest import completed. "
                        + "input={}, created={}, updated={}, "
                        + "unchanged={}, total={}",
                result.inputCount(),
                result.createdCount(),
                result.updatedCount(),
                result.unchangedCount(),
                result.totalDisclosureCount()
        );
    }
}
