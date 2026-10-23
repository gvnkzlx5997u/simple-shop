package com.simpleshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected MvcResult registerBuyer(
            String username, String password, String phone, String location) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("phone", phone);
        body.put("default_location", location);
        return mockMvc.perform(post("/api/buyer/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
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
}