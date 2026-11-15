package com.fdu.cloudnative.api.vo;

import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.ReportStatus;
import com.fdu.cloudnative.api.entity.TriggerType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 报告运行记录（契约 9.3 的 Report，不含 results）。
 * 用于 POST /reports 的返回和 GET /reports 的列表项。
 */
public record ReportRunVO(
        String id,
        TriggerType triggerType,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        ReportStatus status,
        String jobName,
        int attempt,
        String errorMessage,
        // 三个时间用 Instant：序列化为带 Z 的 ISO-8601（如 2026-11-15T08:30:00Z），前端据此换算时区
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
    public static ReportRunVO from(ReportRun run) {
        return new ReportRunVO(
                String.valueOf(run.getId()),
                run.getTriggerType(),
                run.getRangeStart(),
                run.getRangeEnd(),
                run.getStatus(),
                run.getJobName(),
                run.getAttempt() == null ? 0 : run.getAttempt(),
                run.getErrorMessage(),
                toUtcInstant(run.getCreatedAt()),
                toUtcInstant(run.getStartedAt()),
                toUtcInstant(run.getFinishedAt())
        );
    }

    /** 库里的 DATETIME 存的是 UTC，但不带时区信息，这里补上。 */
    static Instant toUtcInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
