package com.fdu.cloudnative.api.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一次报告运行，对应表 report_runs。
 */
@Data
public class ReportRun {
    private Long id;
    private TriggerType triggerType;
    /**
     * 统计起始日期（含）
     */
    private LocalDate rangeStart;
    /**
     * 统计结束日期（含）
     */
    private LocalDate rangeEnd;
    private ReportStatus status;
    /**
     * 对应的 Kubernetes Job 名；Job 尚未创建时为 null
     */
    private String jobName;
    /**
     * 定时任务的周期标识，库中唯一，用于防重复；手动任务为 null
     */
    private String scheduleKey;
    /**
     * 已执行次数（含重试）
     */
    private Integer attempt;
    private String errorMessage;
    /**
     * 以下三个时间均为 UTC
     */
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
