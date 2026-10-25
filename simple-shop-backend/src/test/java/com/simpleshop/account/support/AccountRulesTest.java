package com.simpleshop.account.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;

class AccountRulesTest {

    @Test
    @DisplayName("用户名去首尾空白，允许中文，拒绝空白/控制字符/尖括号/超长")
    void validatesUsername() {
        assertThat(AccountRules.username("  alice  ")).isEqualTo("alice");
        assertThat(AccountRules.username("张三")).isEqualTo("张三");
        assertThat(AccountRules.username("a".repeat(50))).hasSize(50);

        assertInvalid(() -> AccountRules.username(null), ErrorCode.USERNAME_INVALID);
        assertInvalid(() -> AccountRules.username("   "), ErrorCode.USERNAME_INVALID);
        assertInvalid(() -> AccountRules.username("a".repeat(51)), ErrorCode.USERNAME_INVALID);
        assertInvalid(() -> AccountRules.username("a b"), ErrorCode.USERNAME_INVALID);
        assertInvalid(() -> AccountRules.username("a\nb"), ErrorCode.USERNAME_INVALID);
        assertInvalid(() -> AccountRules.username("<alice>"), ErrorCode.USERNAME_INVALID);
    }

    @Test
    @DisplayName("卖家账号使用独立的错误码")
    void validatesSellerAccount() {
        assertThat(AccountRules.account(" seller ")).isEqualTo("seller");
        assertInvalid(() -> AccountRules.account(""), ErrorCode.ACCOUNT_INVALID);
        assertInvalid(() -> AccountRules.account("a".repeat(51)), ErrorCode.ACCOUNT_INVALID);
    }

    @Test
    @DisplayName("新密码至少 8 字符且 UTF-8 不超过 72 字节")
    void validatesNewPassword() {
        assertThat(AccountRules.newPassword("12345678")).isEqualTo("12345678");
        assertThat(AccountRules.newPassword("😀".repeat(18))).hasSize(36);

        assertInvalid(() -> AccountRules.newPassword(null), ErrorCode.PASSWORD_INVALID);
        assertInvalid(() -> AccountRules.newPassword("1234567"), ErrorCode.PASSWORD_INVALID);
        assertInvalid(() -> AccountRules.newPassword("        "), ErrorCode.PASSWORD_INVALID);
        assertInvalid(() -> AccountRules.newPassword("a".repeat(73)), ErrorCode.PASSWORD_INVALID);
        assertInvalid(() -> AccountRules.newPassword("😀".repeat(19)), ErrorCode.PASSWORD_INVALID);
    }

    @Test
    @DisplayName("登录密码只校验非空")
    void validatesLoginPassword() {
        assertThat(AccountRules.loginPassword("short")).isEqualTo("short");

        assertInvalid(() -> AccountRules.loginPassword(null), ErrorCode.PASSWORD_INVALID);
        assertInvalid(() -> AccountRules.loginPassword("  "), ErrorCode.PASSWORD_INVALID);
    }

    @Test
    @DisplayName("电话只允许 7~20 位数字与 -，且数字至少 7 位")
    void validatesPhone() {
        assertThat(AccountRules.phone(" 010-12345678 ")).isEqualTo("010-12345678");
        assertThat(AccountRules.phone("1234567")).isEqualTo("1234567");

        assertInvalid(() -> AccountRules.phone(null), ErrorCode.PHONE_INVALID);
        assertInvalid(() -> AccountRules.phone("12345"), ErrorCode.PHONE_INVALID);
        assertInvalid(() -> AccountRules.phone("12345678a"), ErrorCode.PHONE_INVALID);
        assertInvalid(() -> AccountRules.phone("1".repeat(21)), ErrorCode.PHONE_INVALID);
        assertInvalid(() -> AccountRules.phone("-------"), ErrorCode.PHONE_INVALID);
        assertInvalid(() -> AccountRules.phone("123456-"), ErrorCode.PHONE_INVALID);
    }

    @Test
    @DisplayName("默认交易地点可空，最多 50 字符，拒绝换行与尖括号")
    void validatesDefaultLocation() {
        assertThat(AccountRules.defaultLocation(null)).isNull();
        assertThat(AccountRules.defaultLocation("   ")).isNull();
        assertThat(AccountRules.defaultLocation(" 北京 朝阳 ")).isEqualTo("北京 朝阳");
        assertThat(AccountRules.defaultLocation("a".repeat(50))).hasSize(50);

        assertInvalid(() -> AccountRules.defaultLocation("a".repeat(51)),
                ErrorCode.DEFAULT_LOCATION_INVALID);
        assertInvalid(() -> AccountRules.defaultLocation("北京\n朝阳"),
                ErrorCode.DEFAULT_LOCATION_INVALID);
        assertInvalid(() -> AccountRules.defaultLocation("<北京>"),
                ErrorCode.DEFAULT_LOCATION_INVALID);
    }

    private static void assertInvalid(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(expected);
    }
}
