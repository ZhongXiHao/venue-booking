package com.fdu.cloudnative.api.job;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 只在 k8s 模式下创建 client，本地 noop 模式不会连集群。
// 在 Pod 里运行时自动使用 ServiceAccount 的凭据；在本机运行时读取 ~/.kube/config。
@Configuration
@ConditionalOnProperty(name = "report.launcher", havingValue = "k8s")
@EnableConfigurationProperties(ReportJobProperties.class)
public class KubernetesClientConfig {
    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        // 默认会重试 10 次（指数退避，约 20 秒）。创建 Job 发生在用户的请求里，
        // 集群不可用时应当尽快返回 503，所以只重试 2 次。
        Config config = new ConfigBuilder().withRequestRetryBackoffLimit(2).build();
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}
