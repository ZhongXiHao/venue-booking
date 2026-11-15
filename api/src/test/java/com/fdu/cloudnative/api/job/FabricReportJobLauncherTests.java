package com.fdu.cloudnative.api.job;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创建 Job 的单元测试：KubernetesClient 用 mock，不连接集群。
 * 主要检查发给 Kubernetes 的 Job 内容是否符合约定，以及创建失败时抛出的异常。
 */
class FabricReportJobLauncherTests {

    private static final ReportJobProperties PROPS = new ReportJobProperties(
            "venue-booking", "venue-api:lab2", "api-config", "database-credentials", "MYSQL_PASSWORD");

    // client.batch().v1().jobs().inNamespace(ns).resource(job).create() 这条链上的每一环
    private final KubernetesClient client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    @SuppressWarnings("unchecked")
    private final MixedOperation<Job, JobList, ScalableResource<Job>> jobs = mock(MixedOperation.class);
    @SuppressWarnings("unchecked")
    private final NonNamespaceOperation<Job, JobList, ScalableResource<Job>> namespaced = mock(NonNamespaceOperation.class);
    @SuppressWarnings("unchecked")
    private final ScalableResource<Job> resource = mock(ScalableResource.class);

    private FabricReportJobLauncher launcher;

    @BeforeEach
    void setUp() {
        when(client.batch().v1().jobs()).thenReturn(jobs);
        // 只有配置里的命名空间才有返回值：用错命名空间会在测试里直接暴露
        when(jobs.inNamespace("venue-booking")).thenReturn(namespaced);
        when(namespaced.resource(any(Job.class))).thenReturn(resource);
        launcher = new FabricReportJobLauncher(client, PROPS);
    }

    private static Container onlyContainer(Job job) {
        assertEquals(1, job.getSpec().getTemplate().getSpec().getContainers().size());
        return job.getSpec().getTemplate().getSpec().getContainers().get(0);
    }

    private static Map<String, EnvVar> envByName(Container container) {
        return container.getEnv().stream().collect(Collectors.toMap(EnvVar::getName, Function.identity()));
    }

    // ---------- Job 的内容 ----------

    @Test
    void buildJob_metadataAndRetryPolicy() {
        Job job = launcher.buildJob(12L, "report-12");

        assertEquals("report-12", job.getMetadata().getName());
        assertEquals("venue-booking", job.getMetadata().getNamespace());
        assertEquals("report-job", job.getMetadata().getLabels().get("app"));
        assertEquals("12", job.getMetadata().getLabels().get("run-id"));

        JobSpec spec = job.getSpec();
        assertEquals(2, spec.getBackoffLimit());                 // 有限次重试
        assertEquals(300L, spec.getActiveDeadlineSeconds());     // 超时
        assertEquals(3600, spec.getTtlSecondsAfterFinished());   // 结束后自动清理
        assertEquals("Never", spec.getTemplate().getSpec().getRestartPolicy());
        assertEquals("report-job", spec.getTemplate().getMetadata().getLabels().get("app"));
    }

    @Test
    void buildJob_containerRunsReportProfileWithRunId() {
        Container container = onlyContainer(launcher.buildJob(12L, "report-12"));

        assertEquals("venue-api:lab2", container.getImage());
        // minikube image load 进去的本地镜像不能每次都去仓库拉
        assertEquals("IfNotPresent", container.getImagePullPolicy());

        Map<String, EnvVar> env = envByName(container);
        assertEquals("report", env.get("SPRING_PROFILES_ACTIVE").getValue());
        assertEquals("12", env.get("REPORT_RUN_ID").getValue());
        assertEquals("noop", env.get("REPORT_LAUNCHER").getValue());
        // 手动触发的 Job 名已经由 API 写进记录，不需要再传给容器
        assertFalse(env.containsKey("REPORT_JOB_NAME"));
    }

    @Test
    void buildJob_databaseConfigComesFromConfigMapAndSecret() {
        Container container = onlyContainer(launcher.buildJob(12L, "report-12"));

        // 非敏感配置：整体引用 ConfigMap
        assertEquals(1, container.getEnvFrom().size());
        assertEquals("api-config", container.getEnvFrom().get(0).getConfigMapRef().getName());
        assertNull(container.getEnvFrom().get(0).getSecretRef());   // 不整体注入 Secret

        // 密码：只引用 Secret 里的一个 key，值不出现在 Job 里
        EnvVar password = envByName(container).get("DB_PASSWORD");
        assertNull(password.getValue());
        assertEquals("database-credentials", password.getValueFrom().getSecretKeyRef().getName());
        assertEquals("MYSQL_PASSWORD", password.getValueFrom().getSecretKeyRef().getKey());
    }

    @Test
    void buildJob_resourceNamesFollowConfiguration() {
        ReportJobProperties custom = new ReportJobProperties("ns-x", "img:1", "cm-x", "secret-x", "KEY_X");
        Job job = new FabricReportJobLauncher(client, custom).buildJob(3L, "report-3");

        Container container = onlyContainer(job);
        assertEquals("ns-x", job.getMetadata().getNamespace());
        assertEquals("img:1", container.getImage());
        assertEquals("cm-x", container.getEnvFrom().get(0).getConfigMapRef().getName());
        assertEquals("secret-x", envByName(container).get("DB_PASSWORD").getValueFrom().getSecretKeyRef().getName());
        assertEquals("KEY_X", envByName(container).get("DB_PASSWORD").getValueFrom().getSecretKeyRef().getKey());
    }

    // ---------- 提交给 Kubernetes ----------

    @Test
    void launch_createsJobInConfiguredNamespace() {
        launcher.launch(12L, "report-12");

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(namespaced).resource(captor.capture());
        assertEquals("report-12", captor.getValue().getMetadata().getName());
        verify(resource).create();
    }

    @Test
    void launch_kubernetesError_isWrappedWithCause() {
        KubernetesClientException boom = new KubernetesClientException("jobs.batch is forbidden");
        when(resource.create()).thenThrow(boom);

        ReportJobCreateException thrown = assertThrows(ReportJobCreateException.class,
                () -> launcher.launch(12L, "report-12"));
        assertSame(boom, thrown.getCause());
        // 这段文字会被服务层写进记录的 error_message
        assertTrue(thrown.getMessage().contains("jobs.batch is forbidden"));
    }

    @Test
    void launch_connectionError_messageIncludesRootCause() {
        // 连不上集群时外层 message 很笼统，真正的原因在最里层
        KubernetesClientException boom = new KubernetesClientException("An error has occurred.",
                new java.io.IOException("wrapper", new java.net.ConnectException("Connection refused")));
        when(resource.create()).thenThrow(boom);

        ReportJobCreateException thrown = assertThrows(ReportJobCreateException.class,
                () -> launcher.launch(12L, "report-12"));
        assertEquals("创建 Job 失败：An error has occurred.（Connection refused）", thrown.getMessage());
    }

    // ---------- 配置检查 ----------

    @Test
    void missingImage_failsAtStartup() {
        ReportJobProperties noImage = new ReportJobProperties("ns", null, "cm", "secret", "KEY");
        assertThrows(IllegalArgumentException.class, () -> new FabricReportJobLauncher(client, noImage));
    }
}
