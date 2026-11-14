package com.fdu.cloudnative.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 在每个响应上加 X-Instance-Id，标明是哪个实例处理的请求。
 * 多副本部署时，通过 Service 连续请求即可看到不同的 Pod 名称。
 */
@Component
public class InstanceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Instance-Id";

    private final String instanceId;

    /**
     * POD_NAME 由 Deployment 通过 Downward API 注入；本地没有时退回主机名。
     */
    @Autowired
    public InstanceIdFilter(@Value("${POD_NAME:}") String podName) {
        this.instanceId = StringUtils.hasText(podName) ? podName.trim() : hostName();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 必须在放行之前设置：响应一旦提交，头就加不上了
        response.setHeader(HEADER, instanceId);
        filterChain.doFilter(request, response);
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
