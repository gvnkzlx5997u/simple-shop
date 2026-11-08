package com.simpleshop.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.common.auth.PasswordHasher;
import com.simpleshop.testing.ApiTestBase;

/**
 * 限流在真实请求链路上的行为：测试 profile 默认关闭限流，这里显式打开并把阈值压到 1。
 *
 * <p>用例之间用不同的 X-Forwarded-For 隔离计数，避免共享同一个限流窗口。
 */
@TestPropertySource(properties = {
        "simple-shop.rate-limit.enabled=true",
        "simple-shop.rate-limit.register-per-minute=1",
        "simple-shop.rate-limit.login-per-minute=1"})
class RateLimitApiTest extends ApiTestBase {

    private static final String PASSWORD = "Buyer@1234";

    @Test
    @DisplayName("同一账号连续登录超过阈值返回 50001，换账号不受影响")
    void limitsLoginPerAccountAndClient() throws Exception {
        BuyerAccount first = createBuyer();
        BuyerAccount second = createBuyer();
        String clientIp = uniqueIp();

        assertThat(login(first.getUsername(), clientIp).path("code").asInt()).isZero();
        assertThat(login(second.getUsername(), clientIp).path("code").asInt()).isZero();
        assertThat(login(first.getUsername(), clientIp).path("code").asInt()).isEqualTo(50001);
    }

    @Test
    @DisplayName("同一地址的注册超过阈值返回 50001，换地址不受影响")
    void limitsRegistrationPerClient() throws Exception {
        String clientIp = uniqueIp();

        assertThat(register(unique("buyer-"), clientIp).path("code").asInt()).isZero();
        assertThat(register(unique("buyer-"), clientIp).path("code").asInt()).isEqualTo(50001);
        assertThat(register(unique("buyer-"), uniqueIp()).path("code").asInt()).isZero();
    }

    private com.fasterxml.jackson.databind.JsonNode login(String username, String clientIp)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/buyer/auth/session")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private com.fasterxml.jackson.databind.JsonNode register(String username, String clientIp)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", PASSWORD);
        body.put("phone", "13800000000");
        body.put("default_location", null);
        MvcResult result = mockMvc.perform(post("/api/buyer/auth/register")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String uniqueIp() {
        int hash = UUID.randomUUID().hashCode();
        return "10." + ((hash >> 16) & 0xFF) + "." + ((hash >> 8) & 0xFF) + "." + (hash & 0xFF);
    }
}
