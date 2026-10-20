package com.fdu.cloudnative.api.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class Reservation {
    //id               BIGINT       NOT NULL AUTO_INCREMENT,
    //venue_id         BIGINT       NOT NULL,
    //reserver         VARCHAR(100) NOT NULL COMMENT '预约人或预约组织',
    //reservation_date DATE         NOT NULL,
    //start_time       TIME         NOT NULL,
    //end_time         TIME         NOT NULL,
    //purpose          VARCHAR(255) NOT NULL,
    //-- 取消采用逻辑取消：状态改为 CANCELLED，保留历史记录，冲突检查只比对 ACTIVE
    //status           ENUM('ACTIVE','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    //created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'UTC',
    //cancelled_at     DATETIME     NULL COMMENT 'UTC',
    //PRIMARY KEY (id),

    private Long id;
    private Long venueId;
    private String reserver;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reservationDate;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;
    private String purpose;
    private ReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime cancelledAt;
}
