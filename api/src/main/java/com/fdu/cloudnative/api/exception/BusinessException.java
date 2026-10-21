package com.fdu.cloudnative.api.exception;

import lombok.Getter;

import java.time.LocalTime;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    // 消息需要带上场地实际的开放时间：预约时间必须在场地开放时间08:00至22:00内
    public static BusinessException outOfOpeningHours(LocalTime openTime, LocalTime closeTime) {
        return new BusinessException(ErrorCode.OUT_OF_OPENING_HOURS,
                String.format("预约时间必须在场地开放时间 %s 至 %s 内", openTime, closeTime));

    }
}
