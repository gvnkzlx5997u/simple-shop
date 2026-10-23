package com.simpleshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.account.entity.SellerAccount;
import com.simpleshop.category.entity.CategoryL1;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.common.auth.PasswordHasher;

class AccountAndCategoryDataTest extends DataLayerTestBase {

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

    @Test
    @DisplayName("同一父级下二级类别名唯一")
    void categoryL2NameIsUniqueUnderParent() {
        CategoryL2 first = createCategory();
        CategoryL2 duplicate = CategoryL2.create(first.getL1(), first.getName());

        assertThatThrownBy(() -> categoryL2s.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("不同一级类别下可以有同名二级类别")
    void sameL2NameAllowedUnderDifferentParents() {
        CategoryL2 first = createCategory();
        CategoryL1 otherL1 = categoryL1s.saveAndFlush(CategoryL1.create(unique("L1-")));
        CategoryL2 other = CategoryL2.create(otherL1, first.getName());

        categoryL2s.saveAndFlush(other);

        assertThat(categoryL2s.findByL1_IdAndName(otherL1.getId(), first.getName()))
                .get()
                .extracting(CategoryL2::getId)
                .isEqualTo(other.getId());
    }

    @Test
    @DisplayName("一级类别仍有二级子类时不能删除")
    void categoryL1WithChildrenCannotBeDeleted() {
        CategoryL2 child = createCategory();
        String l1Id = child.getL1().getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from simpleshop_category_l1 where id = ?", l1Id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
