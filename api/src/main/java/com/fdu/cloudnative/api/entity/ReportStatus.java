package com.fdu.cloudnative.api.entity;

public enum ReportStatus {
    /**
     * 记录已创建，Job 尚未开始
     */
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}
