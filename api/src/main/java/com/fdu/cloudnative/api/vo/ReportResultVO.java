package com.fdu.cloudnative.api.vo;

import com.fdu.cloudnative.api.entity.ReportResult;

/** 报告详情里一个场地的统计结果（契约 9.3 的 results 元素）。 */
public record ReportResultVO(
        String venueId,
        String venueName,
        int reservationCount,
        int totalMinutes
) {
    public static ReportResultVO from(ReportResult result) {
        return new ReportResultVO(
                String.valueOf(result.getVenueId()),
                result.getVenueName(),
                result.getReservationCount(),
                result.getTotalMinutes()
        );
    }
}
