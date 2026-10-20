package com.fdu.cloudnative.api.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class Venue {
    private Long id;
    private String name;
    private String location;
    private Integer capacity;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime openTime;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime closeTime;
    private VenueStatus status;
    private LocalDateTime createdAt;
}
