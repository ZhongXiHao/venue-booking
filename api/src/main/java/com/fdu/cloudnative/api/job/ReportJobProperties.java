package com.fdu.cloudnative.api.job;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 创建报告 Job 时用到的配置，前缀 report.job。
 * 在集群里通过环境变量提供（REPORT_JOB_IMAGE、REPORT_JOB_NAMESPACE、REPORT_JOB_CONFIG_MAP……），
 * 资源名都不写死在代码里。
 *
 * @param namespace         Job 所在的命名空间
 * @param image             Job 使用的镜像，和 API 是同一个；k8s 模式下必填
 * @param configMap         通过 envFrom 整体注入的 ConfigMap（DB_HOST、DB_PORT、DB_NAME、DB_USER）
 * @param secret            保存数据库密码的 Secret
 * @param secretPasswordKey Secret 里业务账号密码对应的 key，注入为 DB_PASSWORD
 */
@ConfigurationProperties("report.job")
public record ReportJobProperties(
        @DefaultValue("default") String namespace,
        String image,
        @DefaultValue("api-config") String configMap,
        @DefaultValue("database-credentials") String secret,
        @DefaultValue("MYSQL_PASSWORD") String secretPasswordKey) {
}
