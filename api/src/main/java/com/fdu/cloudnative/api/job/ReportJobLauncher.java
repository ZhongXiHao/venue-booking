package com.fdu.cloudnative.api.job;

/**
 * 启动一次报告任务（创建 Kubernetes Job）。
 * 服务层只依赖这个接口：本地开发用空实现，集群里换成真正调用 Kubernetes 的实现。
 */
public interface ReportJobLauncher {

    String JOB_NAME_PREFIX = "report-";

    /** 手动触发的 Job 名：report-<运行 ID>。 */
    static String jobNameFor(Long runId) {
        return JOB_NAME_PREFIX + runId;
    }

    /**
     * 为指定的运行记录创建 Job，Job 只需要拿到 runId（环境变量 REPORT_RUN_ID）。
     * 调用时该记录必须已经提交到数据库，否则 Job 启动后可能读不到它。
     *
     * @throws RuntimeException 创建失败时抛出；调用方负责把记录改为 FAILED 并返回 503
     */
    void launch(Long runId, String jobName);
}
