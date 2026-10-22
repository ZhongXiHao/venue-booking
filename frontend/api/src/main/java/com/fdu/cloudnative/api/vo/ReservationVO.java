package com.fdu.cloudnative.api.vo;

import com.fdu.cloudnative.api.entity.ReservationStatus;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ReservationVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long venueId;
    private String venueName;
    private String bookerName;
    private LocalDate reservationDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String purpose;
    private ReservationStatus status;
}