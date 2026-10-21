package com.fdu.cloudnative.api.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    VENUE_CLOSED(HttpStatus.BAD_REQUEST, "场地已停用"),
    OUT_OF_OPENING_HOURS(HttpStatus.BAD_REQUEST, "预约时段超出开放时间"),
    VENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "指定的场地不存在"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "指定的预约不存在"),
    VENUE_NAME_DUPLICATE(HttpStatus.CONFLICT, "场地名称已存在"),
    RESERVATION_CONFLICT(HttpStatus.CONFLICT, "该时段已被预约"),
    ALREADY_CANCELLED(HttpStatus.CONFLICT, "预约已取消"),
    // 以下三个不在契约错误码表里，只用于保持路径不存在、方法不支持、Content-Type 不支持时的 HTTP 状态
    NOT_FOUND(HttpStatus.NOT_FOUND, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支持的请求方法"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求的 Content-Type 必须为 application/json"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus status;
    private final String defaultMessage;
}