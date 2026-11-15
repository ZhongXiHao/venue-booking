package com.fdu.cloudnative.api.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 空实现：不创建任何 Job，只打一行日志，记录会停在 PENDING。
 * 配置项 report.launcher 为 noop 或未设置时生效，本地开发不需要集群。
 */
@Component
@ConditionalOnProperty(name = "report.launcher", havingValue = "noop", matchIfMissing = true)
public class NoopReportJobLauncher implements ReportJobLauncher {
    private static final Logger logger = LoggerFactory.getLogger(NoopReportJobLauncher.class);

    @Override
    public void launch(Long runId, String jobName) {
        logger.info("report.launcher=noop，未创建 Job：runId={}, jobName={}", runId, jobName);
    }
}
