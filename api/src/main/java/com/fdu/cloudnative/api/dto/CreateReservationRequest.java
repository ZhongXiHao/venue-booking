package com.fdu.cloudnative.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class CreateReservationRequest {
    // { "venueId": "1", "bookerName": "软件工程学习小组", "reservationDate": "2026-10-25",
    //  "startTime": "14:00:00", "endTime": "16:00:00", "purpose": "课程项目讨论" }

    @NotNull(message = "场地 ID 不能为空")
    private Long venueId;
    @NotBlank(message = "预约人或预约组织不能为空")
    @Size(max = 100, message = "预约人或预约组织长度不能超过 100 个字符")
    private String bookerName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "预约日期不能为空")
    private LocalDate reservationDate;
    @NotNull(message = "预约开始时间不能为空")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;
    @NotNull(message = "预约结束时间不能为空")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;
    @NotBlank(message = "预约用途不能为空")
    @Size(max = 500, message = "预约用途长度不能超过 500 个字符")
    private String purpose;
}
