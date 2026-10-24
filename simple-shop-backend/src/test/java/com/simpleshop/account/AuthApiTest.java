package com.simpleshop.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.common.auth.AuthRole;
import com.simpleshop.testing.ApiTestBase;

class AuthApiTest extends ApiTestBase {

    private static final String BUYER_PASSWORD = "Buyer@1234";

    @Test
    @DisplayName("买家可以注册、登录、退出")
    void buyerCanRegisterLoginAndLogout() throws Exception {
        String username = unique("buyer-");
        assertApiCode(registerBuyer(username, BUYER_PASSWORD, "13800000000", "默认地点"), 0);

        String token = loginBuyer(username, BUYER_PASSWORD);
        assertThat(token).isNotBlank();
        assertThat(sessions.validate(token, AuthRole.BUYER)).isPresent();

        MvcResult logout = mockMvc.perform(delete("/api/buyer/auth/session")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(logout, 0);
        assertThat(sessions.validate(token, AuthRole.BUYER)).isEmpty();
    }

    @Test
    @DisplayName("买家注册校验用户名、密码、电话、默认地点与用户名唯一")
    void buyerRegistrationValidatesFields() throws Exception {
        assertApiCode(registerBuyer(unique("buyer-"), "short", "13800000000", null), 10004);
        assertApiCode(registerBuyer(unique("buyer-"), BUYER_PASSWORD, "12345", null), 10007);
        assertApiCode(registerBuyer("", BUYER_PASSWORD, "13800000000", null), 10006);
        assertApiCode(registerBuyer(unique("buyer-"), BUYER_PASSWORD, "13800000000", "a".repeat(51)), 10008);

        String username = unique("buyer-");
        assertApiCode(registerBuyer(username, BUYER_PASSWORD, "13800000000", null), 0);
        assertApiCode(registerBuyer(username, BUYER_PASSWORD, "13900000000", null), 10005);

        assertApiCode(registerBuyer(unique("buyer-"), BUYER_PASSWORD, "13800000000", null), 0);
    }

    @Test
    @DisplayName("买家密码错误返回统一登录失败")
    void buyerLoginRejectsWrongPassword() throws Exception {
        String username = unique("buyer-");
        registerBuyer(username, BUYER_PASSWORD, "13800000000", null);

        MvcResult result = mockMvc.perform(post("/api/buyer/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "Wrong@1234"))))
                .andReturn();

        assertApiCode(result, 10001);
    }

    @Test
    @DisplayName("买家令牌不能访问卖家接口")
    void buyerTokenCannotCallSellerApi() throws Exception {
        String username = unique("buyer-");
        registerBuyer(username, BUYER_PASSWORD, "13800000000", null);
        String token = loginBuyer(username, BUYER_PASSWORD);

        MvcResult result = changePassword(token, "Abcd@1234", "NewPass@1234");

        assertApiStatusAndCode(result, 401, 10002);
    }

    @Test
    @DisplayName("卖家可以登录、改密，旧令牌立即失效且旧口令不可再登录")
    void sellerCanChangePasswordAndOldTokenIsRevoked() throws Exception {
        String token = loginSeller("Abcd@1234");

        MvcResult changed = changePassword(token, "Abcd@1234", "NewPass@1234");
        assertApiCode(changed, 0);

        MvcResult oldToken = changePassword(token, "NewPass@1234", "Another@1234");
        assertApiStatusAndCode(oldToken, 401, 10002);

        MvcResult oldLogin = mockMvc.perform(post("/api/seller/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", "seller", "password", "Abcd@1234"))))
                .andReturn();
        assertApiCode(oldLogin, 10001);

        MvcResult newLogin = mockMvc.perform(post("/api/seller/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", "seller", "password", "NewPass@1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        assertThat(tokenOf(newLogin)).isNotBlank();
    }

    @Test
    @DisplayName("卖家改密时原密码错误返回 10003")
    void sellerChangePasswordRejectsWrongOldPassword() throws Exception {
        String token = loginSeller("Abcd@1234");

        MvcResult result = changePassword(token, "WrongOld@1234", "NewPass@1234");

        assertApiCode(result, 10003);
    }

    @Test
    @DisplayName("卖家改密时新密码过短返回 10004")
    void sellerChangePasswordRejectsShortNewPassword() throws Exception {
        String token = loginSeller("Abcd@1234");

        MvcResult result = changePassword(token, "Abcd@1234", "short");

        assertApiCode(result, 10004);
    }

    @Test
    @DisplayName("卖家退出后令牌失效，重复退出仍成功")
    void sellerLogoutRevokesToken() throws Exception {
        String token = loginSeller("Abcd@1234");

        MvcResult logout = mockMvc.perform(delete("/api/seller/auth/session")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(logout, 0);

        assertApiStatusAndCode(changePassword(token, "Abcd@1234", "NewPass@1234"), 401, 10002);

        MvcResult again = mockMvc.perform(delete("/api/seller/auth/session")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(again, 0);
    }

    @Test
    @DisplayName("卖家密码错误返回统一登录失败")
    void sellerLoginRejectsWrongPassword() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/seller/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", "seller", "password", "Wrong@1234"))))
                .andReturn();

        assertApiCode(result, 10001);
    }

    private MvcResult changePassword(String token, String oldPassword, String newPassword)
            throws Exception {
        return mockMvc.perform(put("/api/seller/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "old_password", oldPassword,
                                "new_password", newPassword))))
                .andReturn();
    }
}
