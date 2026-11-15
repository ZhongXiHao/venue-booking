package com.fdu.cloudnative.api;

import com.fdu.cloudnative.api.config.InstanceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 纯单元测试，不启动 Spring 容器，也不需要数据库。 */
class InstanceIdFilterTests {

    private static String headerFor(String podName) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new InstanceIdFilter(podName)
                .doFilter(new MockHttpServletRequest("GET", "/api/venues"), response, new MockFilterChain());
        return response.getHeader(InstanceIdFilter.HEADER);
    }

    @Test
    void usesPodNameWhenPresent() throws Exception {
        assertEquals("api-7c9d8-abcde", headerFor("api-7c9d8-abcde"));
    }

    @Test
    void fallsBackToHostNameWhenPodNameMissing() throws Exception {
        String hostName = InetAddress.getLocalHost().getHostName();
        assertEquals(hostName, headerFor(""));
        assertEquals(hostName, headerFor("   "));
    }
}
