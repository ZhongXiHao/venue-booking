package com.fdu.cloudnative.api.vo;

import com.fdu.cloudnative.api.entity.ReportResult;
import com.fdu.cloudnative.api.entity.ReportRun;
import com.fdu.cloudnative.api.entity.ReportStatus;
import com.fdu.cloudnative.api.entity.TriggerType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 报告详情（GET /reports/{id}）：ReportRunVO 的全部字段加 results。
 * 契约要求 results 与其他字段平铺在同一层，record 又不能继承，所以字段重复写一遍。
 */
public record ReportDetailVO(
        String id,
        TriggerType triggerType,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        ReportStatus status,
        String jobName,
        int attempt,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        List<ReportResultVO> results
) {
    /**
     * 状态不是 SUCCESS 时 results 一律为空数组（契约 9.3），
     * 即使库里还留着上一次执行的结果行也不返回。
     */
    public static ReportDetailVO from(ReportRun run, List<ReportResult> results) {
        ReportRunVO base = ReportRunVO.from(run);
        List<ReportResultVO> items = run.getStatus() == ReportStatus.SUCCESS && results != null
                ? results.stream().map(ReportResultVO::from).toList()
                : List.of();
        return new ReportDetailVO(
                base.id(), base.triggerType(), base.rangeStart(), base.rangeEnd(), base.status(),
                base.jobName(), base.attempt(), base.errorMessage(),
                base.createdAt(), base.startedAt(), base.finishedAt(),
                items
        );
    }
}
