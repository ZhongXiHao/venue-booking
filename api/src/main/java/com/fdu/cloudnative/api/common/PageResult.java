package com.fdu.cloudnative.api.common;

import com.fdu.cloudnative.api.exception.BusinessException;
import com.fdu.cloudnative.api.exception.FieldErrorItem;

import java.util.ArrayList;
import java.util.List;

public record PageResult<T>(List<T> items, long total, int page, int pageSize) {
    public static final int MAX_PAGE_SIZE = 100;

    /** 校验分页参数，非法时抛出 400 VALIDATION_ERROR 并带上每个出错字段。 */
    public static void checkParams(int page, int pageSize) {
        List<FieldErrorItem> fieldErrors = new ArrayList<>();
        if (page < 1) {
            fieldErrors.add(new FieldErrorItem("page", "页码必须大于等于 1"));
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            fieldErrors.add(new FieldErrorItem("pageSize", "每页条数必须在 1 到 " + MAX_PAGE_SIZE + " 之间"));
        }
        if (!fieldErrors.isEmpty()) {
            throw BusinessException.validation(fieldErrors);
        }
    }
}
