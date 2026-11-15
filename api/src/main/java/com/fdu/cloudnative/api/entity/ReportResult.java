package com.fdu.cloudnative.api.entity;

import lombok.Data;

/**
 * 某次报告里一个场地的统计结果，对应表 report_results。
 */
@Data
public class ReportResult {
    private Long id;
    private Long runId;
    private Long venueId;
    /**
     * 生成报告时的场地名快照
     */
    private String venueName;
    private Integer reservationCount;
    /**
     * 累计预约时长（分钟）
     */
    private Integer totalMinutes;
}
