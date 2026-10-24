package com.simpleshop.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.account.entity.SellerAccount;
import com.simpleshop.common.auth.PasswordHasher;
import com.simpleshop.testing.DataLayerTestBase;

class AccountDataTest extends DataLayerTestBase {

    @Test
    @DisplayName("种子卖家账号存在，初始口令哈希可校验")
    void seedSellerIsAvailable() {
        SellerAccount seller = sellerAccounts.findByAccount("seller").orElseThrow();

        assertThat(seller.getId()).startsWith("SA");
        assertThat(seller.getCreateAt()).isNotNull();
        assertThat(PasswordHasher.matches("Abcd@1234", seller.getPassword())).isTrue();
    }

    @Test
    @DisplayName("买家用户名唯一")
    void buyerUsernameIsUnique() {
        BuyerAccount first = createBuyer();
        BuyerAccount duplicate = BuyerAccount.register(
                first.getUsername(), PasswordHasher.hash("Other@1234"), "13900000000", null);

        assertThatThrownBy(() -> buyerAccounts.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("买家手机号允许重复")
    void buyerPhoneMayRepeat() {
        BuyerAccount first = createBuyer();
        BuyerAccount second = BuyerAccount.register(
                unique("buyer-"), PasswordHasher.hash("Other@1234"), first.getPhone(), null);

        buyerAccounts.saveAndFlush(second);

        assertThat(buyerAccounts.countByPhone(first.getPhone())).isEqualTo(2);
    }
}
