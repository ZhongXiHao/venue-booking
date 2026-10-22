package com.fdu.cloudnative.api.common;

import java.util.List;

public record PageResult<T>(List<T> items, long total, int page, int pageSize) {
}
