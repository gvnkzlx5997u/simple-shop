package com.simpleshop.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * API 测试辅助：注册、登录、构造 Bearer 头。
 */
public abstract class ApiTestBase extends DataLayerTestBase {

    protected void assertApiCode(MvcResult result, int code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asInt()).isEqualTo(code);
    }

    protected void assertApiStatusAndCode(MvcResult result, int httpStatus, int code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(httpStatus);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asInt()).isEqualTo(code);
    }

    /** 参数校验失败：HTTP 400 + 50002，且 errors 列表包含指定字段。 */
    protected void assertApiFieldError(MvcResult result, String field) throws Exception {
        assertApiStatusAndCode(result, 400, 50002);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("errors").isArray()).isTrue();
        assertThat(body.path("errors").findValuesAsText("field")).contains(field);
    }

    /**
     * 测试类默认包在回滚事务里，afterCommit 不会自动触发；需要观察提交后副作用的用例
     * 显式调用本方法，模拟真实提交。测试结束仍会回滚业务数据。
     */
    protected void runAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected MvcResult registerBuyer(String username, String password, String phone)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("phone", phone);
        return mockMvc.perform(post("/api/buyer/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    /**
     * 阶段三的调用点还带着「默认交易地点」，注册已不再收这个字段。
     * 保留重载只为让旧用例继续编译，第四参数被丢弃；T7 重写用例时一并删掉。
     */
    @Deprecated
    protected MvcResult registerBuyer(
            String username, String password, String phone, String location) throws Exception {
        return registerBuyer(username, password, phone);
    }

    protected String loginBuyer(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/buyer/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return tokenOf(result);
    }

    protected String loginSeller(String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/seller/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", "seller", "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return tokenOf(result);
    }

    protected String tokenOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("token").asText();
    }

    /** 响应体 data 节点；分页断言用 data.path("items") / data.path("total")。 */
    protected JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}