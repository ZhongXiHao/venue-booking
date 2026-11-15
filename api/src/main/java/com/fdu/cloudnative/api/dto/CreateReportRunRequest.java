package com.fdu.cloudnative.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateReportRunRequest(
        @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
        @NotNull(message = "报告起始日期不能为空")
        LocalDate rangeStart,

        @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
        @NotNull(message = "报告结束日期不能为空")
        LocalDate rangeEnd
) {
}
