package com.fdu.cloudnative.api.job;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * 真正调用 Kubernetes API 创建 Job 的实现，report.launcher=k8s 时生效。
 * Job 用和 API 相同的镜像，以 report profile 启动，跑完一次就退出。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "report.launcher", havingValue = "k8s")
public class FabricReportJobLauncher implements ReportJobLauncher {

    static final int BACKOFF_LIMIT = 2;                 // 失败最多重试 2 次
    static final long ACTIVE_DEADLINE_SECONDS = 300L;   // 整个 Job 最多跑 5 分钟
    static final int TTL_SECONDS_AFTER_FINISHED = 3600; // 结束 1 小时后自动清理

    private final KubernetesClient client;
    private final ReportJobProperties props;

    public FabricReportJobLauncher(KubernetesClient client, ReportJobProperties props) {
        Assert.hasText(props.image(), "report.launcher=k8s 时必须配置 report.job.image（环境变量 REPORT_JOB_IMAGE）");
        this.client = client;
        this.props = props;
    }

    @Override
    public void launch(Long runId, String jobName) {
        Job job = buildJob(runId, jobName);
        try {
            client.batch().v1().jobs().inNamespace(props.namespace()).resource(job).create();
            log.info("已创建 Job {}（runId={}，namespace={}）", jobName, runId, props.namespace());
        } catch (KubernetesClientException e) {
            throw new ReportJobCreateException("创建 Job 失败：" + describe(e), e);
        }
    }

    /**
     * 生成写进报告记录、会显示给用户的失败原因：只说明是哪一类问题。
     * fabric8 的原始信息里有集群内部地址、ServiceAccount 名和整段 Status，不适合给用户看；
     * 完整的异常作为 cause 保留，由服务层连同堆栈写进 API 日志，排查时看日志。
     */
    private static String describe(KubernetesClientException e) {
        int code = e.getCode();
        if (code <= 0) {
            return "无法连接 Kubernetes API";   // 没有拿到 HTTP 响应：网络不通、超时等
        }
        return switch (code) {
            case 401, 403 -> "API 没有创建 Job 的权限（" + code + "）";
            case 404 -> "命名空间不存在（404）";
            case 409 -> "同名 Job 已存在（409）";
            default -> {
                String reason = e.getStatus() != null ? e.getStatus().getReason() : null;
                yield "Kubernetes 返回 " + code + (reason != null ? " " + reason : "");
            }
        };
    }

    Job buildJob(Long runId, String jobName) {
        return new JobBuilder()
                .withNewMetadata()
                    .withName(jobName)
                    .withNamespace(props.namespace())
                    .addToLabels("app", "report-job")
                    .addToLabels("run-id", String.valueOf(runId))
                .endMetadata()
                .withNewSpec()
                    .withBackoffLimit(BACKOFF_LIMIT)
                    .withActiveDeadlineSeconds(ACTIVE_DEADLINE_SECONDS)
                    .withTtlSecondsAfterFinished(TTL_SECONDS_AFTER_FINISHED)
                    .withNewTemplate()
                        .withNewMetadata().addToLabels("app", "report-job").endMetadata()
                        .withNewSpec()
                            .withRestartPolicy("Never")   // 失败由 Job 重建 Pod，不在原 Pod 里重启
                            // 报告任务只访问数据库，不调用 Kubernetes API，不需要挂载 ServiceAccount 令牌
                            .withAutomountServiceAccountToken(false)
                            .withContainers(buildContainer(runId))
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
    }

    private Container buildContainer(Long runId) {
        return new ContainerBuilder()
                .withName("report")
                .withImage(props.image())
                .withImagePullPolicy("IfNotPresent")
                // 非敏感的数据库配置：整体引用和 API 相同的 ConfigMap
                .addNewEnvFrom().withNewConfigMapRef().withName(props.configMap()).endConfigMapRef().endEnvFrom()
                .withEnv(
                        env("SPRING_PROFILES_ACTIVE", "report"),
                        env("REPORT_RUN_ID", String.valueOf(runId)),
                        // 报告任务自己不需要创建 Job；显式覆盖 ConfigMap 里可能带进来的 k8s
                        env("REPORT_LAUNCHER", "noop"),
                        // 只取业务账号的密码，不把整个 Secret（含 root 密码）注入容器
                        secretEnv("DB_PASSWORD", props.secret(), props.secretPasswordKey()))
                .build();
    }

    private static EnvVar env(String name, String value) {
        return new EnvVarBuilder().withName(name).withValue(value).build();
    }

    private static EnvVar secretEnv(String name, String secret, String key) {
        return new EnvVarBuilder().withName(name)
                .withNewValueFrom().withNewSecretKeyRef(key, secret, false).endValueFrom()
                .build();
    }
}
