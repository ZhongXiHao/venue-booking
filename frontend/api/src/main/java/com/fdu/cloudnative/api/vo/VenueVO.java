package com.fdu.cloudnative.api.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalTime;

@Data
public class VenueVO{
    //{"id": "1", "name": "第一会议室", "location": "教学楼A栋201", "capacity": 30,
    //   "openTime": "08:00:00", "closeTime": "22:00:00", "status": "OPEN"}
    private String id;
    private String name;
    private String location;
    private Integer capacity;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime openTime;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime closeTime;
    // OPEN / DISABLED
    private String status;
}
