package com.fdu.cloudnative.api.exception;

/**
 * 校验失败时 data.fieldErrors 里的一项
 */
public record FieldErrorItem(String field, String message) {
}
