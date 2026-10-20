package com.fdu.cloudnative.api.entity;

public enum VenueStatus {
    // 数据库枚举值为 OPEN / CLOSED，接口契约对外为 OPEN / DISABLED
    OPEN("OPEN"),
    CLOSED("DISABLED");

    private final String apiValue;

    VenueStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }
}
