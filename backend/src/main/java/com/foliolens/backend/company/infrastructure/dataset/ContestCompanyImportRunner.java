package com.foliolens.backend.company.infrastructure.dataset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "foliolens.dataset",
        name = "import-companies-on-startup",
        havingValue = "true"
)
public class ContestCompanyImportRunner implements ApplicationRunner {

    private final ContestCompanyImporter companyImporter;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Contest company dataset import started.");

        ContestCompanyImporter.ImportResult result =
                companyImporter.importCompanies();

        log.info(
                "Contest company dataset import completed. "
                        + "input={}, created={}, updated={}, unchanged={}, total={}",
                result.inputCount(),
                result.createdCount(),
                result.updatedCount(),
                result.unchangedCount(),
                result.totalCompanyCount()
        );
    }
}
