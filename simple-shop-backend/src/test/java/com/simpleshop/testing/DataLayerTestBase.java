package com.simpleshop.testing;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.account.repository.BuyerAccountRepository;
import com.simpleshop.account.repository.SellerAccountRepository;
import com.simpleshop.common.auth.PasswordHasher;
import com.simpleshop.common.auth.SessionStore;
import com.simpleshop.category.entity.CategoryL1;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.category.repository.CategoryL1Repository;
import com.simpleshop.category.repository.CategoryL2Repository;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.OffShelfReason;
import com.simpleshop.goods.repository.GoodsContentBlockRepository;
import com.simpleshop.goods.repository.GoodsImageRepository;
import com.simpleshop.goods.repository.GoodsRepository;
import com.simpleshop.inventory.entity.GoodsStock;
import com.simpleshop.inventory.repository.GoodsStockRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class DataLayerTestBase {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired protected SellerAccountRepository sellerAccounts;
    @Autowired protected BuyerAccountRepository buyerAccounts;
    @Autowired protected CategoryL1Repository categoryL1s;
    @Autowired protected CategoryL2Repository categoryL2s;
    @Autowired protected GoodsRepository goodsRepository;
    @Autowired protected GoodsImageRepository goodsImages;
    @Autowired protected GoodsContentBlockRepository contentBlocks;
    @Autowired protected GoodsStockRepository stockItems;
    @Autowired protected EntityManager entityManager;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected SessionStore sessions;

    protected String unique(String prefix) {
        return prefix + UUID.randomUUID();
    }

    protected CategoryL2 createCategory() {
        CategoryL1 l1 = categoryL1s.saveAndFlush(CategoryL1.create(unique("L1-")));
        return categoryL2s.saveAndFlush(CategoryL2.create(l1, unique("L2-")));
    }

    protected BuyerAccount createBuyer() {
        BuyerAccount buyer = BuyerAccount.register(
                unique("buyer-"), PasswordHasher.hash("Buyer@1234"), "13800000000", "默认地点");
        return buyerAccounts.saveAndFlush(buyer);
    }

    protected Goods createGoods(CategoryL2 category) {
        Goods goods = goodsRepository.saveAndFlush(
                Goods.publish(unique("商品-"), new BigDecimal("99.99"), category));
        stockItems.saveAndFlush(GoodsStock.initialize(goods.getId(), 3));
        return goods;
    }

    protected void setStock(Goods goods, int available) {
        GoodsStock stock = stockItems.findById(goods.getId())
                .orElseThrow(() -> new IllegalStateException("stock row missing"));
        stock.setAvailable(available);
        stockItems.saveAndFlush(stock);
    }

    /** 直接按领域方法改状态，用于准备「已下架」的夹具。 */
    protected Goods offShelf(Goods goods, OffShelfReason reason) {
        goods.offShelf(reason);
        return goodsRepository.saveAndFlush(goods);
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
