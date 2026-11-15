package com.fdu.cloudnative.api.entity;

public enum TriggerType {
    /**
     * 用户在页面触发
     */
    MANUAL,
    /**
     * CronJob 定时触发
     */
    SCHEDULED
}
