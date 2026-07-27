package com.foliolens.backend.company.sync;

import com.foliolens.backend.company.entity.Market;

import java.time.LocalDate;

public record CompanySyncItem(
        String corpCode,
        String stockCode,
        String corpName,
        Market market,
        LocalDate validFrom,
        LocalDate validTo
) {


}
