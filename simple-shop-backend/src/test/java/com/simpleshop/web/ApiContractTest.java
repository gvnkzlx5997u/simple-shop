package com.simpleshop.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.simpleshop.session.SessionStore;

/**
 * 接口层公共设施的 HTTP 行为验证（S2 的验收凭据）。
 *
 * <h2>验证目标（对应用户给定的 S2 完成标志）</h2>
 * <p>「无 token 请求 {@code /api/seller/**} → <b>HTTP 401 + 业务码 10002</b>」。
 *
 * <h2>⚠️ 这里刻意用「未实现的接口路径」做验证</h2>
 * <p>S2 阶段<b>还没有任何 Controller</b>（{@code I11-01} 等在 S3/S4 才实现）。
 * 而拦截器在 handler 映射解析<b>之前</b>执行，因此请求一个<b>尚不存在</b>的
 * {@code /api/seller/product} 也会先被鉴权拦住 → 仍返回 {@code 10002}，
 * <b>不会</b>落到 404。这恰好证明拦截器<b>覆盖整个前缀</b>，
 * 而不是只对已实现的路径生效——后者是更常见也更危险的漏配。
 *
 * <h2>为什么用 MockMvc 而不是启动真实端口</h2>
 * <p>MockMvc 走的是与真实请求<b>同一套</b> DispatcherServlet + 拦截器 + 异常处理器链路
 * （只是不经过网络与真实的 Tomcat 连接器），因此足以验证状态码、响应体与头。
 * 真实端口的启动验证由 S1 的「war 可 java -jar 启动」用例覆盖。
 */
@SpringBootTest
class ApiContractTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SessionStore sessionStore;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    // -------------------------------------------------------------------------
    // 鉴权：无 token → 401 + 10002
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("无 token 请求 /api/seller/** → HTTP 401 + code 10002")
    void missingTokenYields401AndCode10002() throws Exception {
        mockMvc().perform(get("/api/seller/product"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.message").value("session invalid or expired"))
                // 契约 11.3.1：无数据时 data 为 null，但【键必须存在】（不是被省略）
                .andExpect(jsonPath("$['data']").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("无效 token → HTTP 401 + code 10002")
    void invalidTokenYields401() throws Exception {
        mockMvc().perform(get("/api/seller/product").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("Authorization 头格式不符（缺 Bearer 前缀）→ 401 + 10002")
    void malformedAuthorizationHeaderYields401() throws Exception {
        mockMvc().perform(get("/api/seller/product").header("Authorization", "token-without-scheme"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("拦截器覆盖整个 /api/seller/**（含尚未实现的接口）")
    void interceptorCoversUnmappedSellerPaths() throws Exception {
        // 这些接口要到 S3/S4 才实现；当前必须【先被鉴权拦住】，而不是变成 404
        mockMvc().perform(get("/api/seller/history/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));

        mockMvc().perform(post("/api/seller/intention/success"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));

        mockMvc().perform(post("/api/seller/product/freeze"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("登录接口（POST /api/seller/session）【不】被拦截器拦截")
    void loginPathIsExcludedFromAuth() throws Exception {
        // S2 尚无登录 Controller，因此最终仍是 404；但关键断言是【不是 401】——
        // 即拦截器确实把登录路径排除了（否则永远无法登录）。
        int status = mockMvc().perform(post("/api/seller/session")
                        .contentType("application/json")
                        .content("{\"account\":\"seller\",\"password\":\"Abcd@1234\"}"))
                .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .as("登录接口不应返回 401（否则陷入死锁：会话失效→需登录→登录需会话）")
                .isNotEqualTo(401);
    }

    @Test
    @DisplayName("携带有效 token 的请求可以穿透拦截器（落到真正的 handler，而非 401）")
    void validTokenPassesInterceptor() throws Exception {
        String token = sessionStore.create("seller").token();

        // ⚠️ 本用例在 S2 断言的是 404（当时 I11-04 尚未实现，「不是 401」只能用 404 表达）。
        //    S4 实现该接口后，代理式断言必须换成真实行为——否则它会是「因为接口没写所以绿」的假绿。
        //    真正的关注点始终是「没有被鉴权拦下」，故同时断言 200 与 code 0。
        mockMvc().perform(get("/api/seller/product").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("Bearer 前缀大小写不敏感（RFC 7235）")
    void bearerPrefixIsCaseInsensitive() throws Exception {
        String token = sessionStore.create("seller").token();

        mockMvc().perform(get("/api/seller/product").header("Authorization", "bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("退出登录（DELETE /api/seller/session）不返回 401 —— 幂等，token 无效也不报错")
    void logoutIsIdempotentWithoutToken() throws Exception {
        // 口径见 WebMvcConfig#addInterceptors 的说明：I11-01 与 I11-02 共用路径，
        // 按路径排除 ⇒ 退出登录不被拦截。它是幂等操作，不泄漏任何信息。
        int status = mockMvc().perform(delete("/api/seller/session"))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
    }

    // -------------------------------------------------------------------------
    // 统一响应结构
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("未知路径 → 404，且响应体仍是统一的 {code,message,data} 结构")
    void unknownPathKeepsEnvelopeShape() throws Exception {
        // 注意：本路径不在 /api/seller/** 下，不会先被拦截器拦到，
        // 因此它验证的是「路由不存在」这条分支的响应形状。
        mockMvc().perform(get("/no/such/endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(50002))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$['data']").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("业务错误的 message 一律是 ASCII（前端不得展示，仅日志用）")
    void errorMessagesAreAscii() throws Exception {
        String body = mockMvc().perform(get("/api/seller/product"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .as("message 必须是 ASCII 英文（已定 Q-13）；响应体整体不含中文")
                .matches("[\\x00-\\x7F]*");
    }
}
