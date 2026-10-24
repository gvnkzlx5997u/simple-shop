package com.simpleshop.testing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.account.repository.BuyerAccountRepository;
import com.simpleshop.account.service.BuyerAuthService;
import com.simpleshop.category.entity.CategoryL1;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.category.repository.CategoryL1Repository;
import com.simpleshop.category.repository.CategoryL2Repository;
import com.simpleshop.common.auth.PasswordHasher;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.repository.GoodsContentBlockRepository;
import com.simpleshop.goods.repository.GoodsImageRepository;
import com.simpleshop.goods.repository.GoodsRepository;
import com.simpleshop.goods.service.GoodsTradeSupport;
import com.simpleshop.history.repository.GoodsHistoryRepository;
import com.simpleshop.history.repository.IntentionHistoryRepository;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.repository.GoodsQueueSequenceRepository;
import com.simpleshop.intention.repository.IntentionRepository;
import com.simpleshop.intention.service.BuyerIntentionService;
import com.simpleshop.intention.service.SellerTradeService;

/**
 * 并发测试基类：无外层测试事务，被测服务在每个线程内开启并提交独立事务。
 * 造数即时提交；每个用例结束后按外键顺序清空业务表，不残留数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ConcurrencyTestBase {

    @Autowired protected CategoryL1Repository categoryL1s;
    @Autowired protected CategoryL2Repository categoryL2s;
    @Autowired protected GoodsRepository goodsRepository;
    @Autowired protected GoodsImageRepository goodsImages;
    @Autowired protected GoodsContentBlockRepository contentBlocks;
    @Autowired protected GoodsQueueSequenceRepository queueSequences;
    @Autowired protected IntentionRepository intentions;
    @Autowired protected GoodsHistoryRepository goodsHistories;
    @Autowired protected IntentionHistoryRepository intentionHistories;
    @Autowired protected BuyerAccountRepository buyerAccounts;
    @Autowired protected BuyerAuthService accounts;
    @Autowired protected GoodsTradeSupport goodsTradeSupport;
    @Autowired protected BuyerIntentionService buyerIntentionService;
    @Autowired protected SellerTradeService tradeService;

    @AfterEach
    void cleanBusinessTables() {
        intentionHistories.deleteAllInBatch();
        goodsHistories.deleteAllInBatch();
        intentions.deleteAllInBatch();
        queueSequences.deleteAllInBatch();
        contentBlocks.deleteAllInBatch();
        goodsImages.deleteAllInBatch();
        goodsRepository.deleteAllInBatch();
        buyerAccounts.deleteAllInBatch();
        categoryL2s.deleteAllInBatch();
        categoryL1s.deleteAllInBatch();
    }

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

    /** 建在售商品并初始化队列序号行，等价于发布服务的完整副作用。 */
    protected Goods publishedGoods(int stock) {
        Goods goods = goodsRepository.saveAndFlush(
                Goods.publish(unique("商品-"), new BigDecimal("99.99"), stock, createCategory()));
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));
        return goods;
    }

    /** 并发执行：所有线程在闩锁处同时起跑，返回按线程序的结果。 */
    protected static <T> List<T> runConcurrently(int threads, IntFunction<Callable<T>> taskFactory)
            throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>(threads);
            for (int index = 0; index < threads; index++) {
                Callable<T> task = taskFactory.apply(index);
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>(threads);
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
