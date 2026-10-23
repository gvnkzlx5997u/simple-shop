package com.simpleshop;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
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
import com.simpleshop.goods.repository.GoodsContentBlockRepository;
import com.simpleshop.goods.repository.GoodsImageRepository;
import com.simpleshop.goods.repository.GoodsRepository;
import com.simpleshop.history.repository.GoodsHistoryRepository;
import com.simpleshop.history.repository.IntentionHistoryRepository;
import com.simpleshop.intention.repository.GoodsQueueSequenceRepository;
import com.simpleshop.intention.repository.IntentionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class DataLayerTestBase {

    @Autowired protected SellerAccountRepository sellerAccounts;
    @Autowired protected BuyerAccountRepository buyerAccounts;
    @Autowired protected CategoryL1Repository categoryL1s;
    @Autowired protected CategoryL2Repository categoryL2s;
    @Autowired protected GoodsRepository goodsRepository;
    @Autowired protected GoodsImageRepository goodsImages;
    @Autowired protected GoodsContentBlockRepository contentBlocks;
    @Autowired protected GoodsHistoryRepository goodsHistories;
    @Autowired protected GoodsQueueSequenceRepository queueSequences;
    @Autowired protected IntentionRepository intentions;
    @Autowired protected IntentionHistoryRepository intentionHistories;
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
        Goods goods = Goods.publish(unique("商品-"), new BigDecimal("99.99"), 3, category);
        return goodsRepository.saveAndFlush(goods);
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
