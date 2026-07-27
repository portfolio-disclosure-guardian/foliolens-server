package com.foliolens.backend.company.sync;

import java.time.OffsetDateTime;


/**
 * 동기화 결과
 * @param source = 데이터 소스가 어디인지
 * @param fetchedCount = 처리된 총 기업 개수
 * @param createdCount ＝ 새 기업 개수
 * @param updatedCount ＝ 변경된 기업 개수
 * @param unchangedCount ＝ 변경 없는 기업 개수
 * @param failedCount ＝ 잘못된 데이터 개수
 * @param startedAt ＝ 처리 시작 시간
 * @param finishedAt ＝ 처리 끝 시간
 */
public record CompanySyncResult(
        CompanyDataSource source,
        int fetchedCount,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        int failedCount,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
