package com.fdu.cloudnative.api.exception;

import lombok.Getter;

import java.time.LocalTime;
import java.util.List;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    // 仅 VALIDATION_ERROR 使用，其余为 null
    private final List<FieldErrorItem> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = null;
    }

    private BusinessException(ErrorCode errorCode, String message, List<FieldErrorItem> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
    }

    // Service 层的校验失败（跨字段规则等），按契约返回 data.fieldErrors
    public static BusinessException validation(String field, String message) {
        return validation(List.of(new FieldErrorItem(field, message)));
    }

    public static BusinessException validation(List<FieldErrorItem> fieldErrors) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(), fieldErrors);
    }

    // 消息需要带上场地实际的开放时间：预约时间必须在场地开放时间08:00至22:00内
    public static BusinessException outOfOpeningHours(LocalTime openTime, LocalTime closeTime) {
        return new BusinessException(ErrorCode.OUT_OF_OPENING_HOURS,
                String.format("预约时间必须在场地开放时间 %s 至 %s 内", openTime, closeTime));
    }
}
