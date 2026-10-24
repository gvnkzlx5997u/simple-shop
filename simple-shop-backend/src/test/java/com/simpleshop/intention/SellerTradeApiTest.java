package com.simpleshop.intention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.goods.repository.GoodsRepository;
import com.simpleshop.history.entity.GoodsHistory;
import com.simpleshop.history.entity.IntentionHistory;
import com.simpleshop.history.enums.GoodsHistoryResult;
import com.simpleshop.history.enums.IntentionResult;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.intention.enums.FailType;
import com.simpleshop.intention.enums.IntentionStatus;
import com.simpleshop.testing.ApiTestBase;

class SellerTradeApiTest extends ApiTestBase {

    @Test
    @DisplayName("队首进入交易：意向交易中、商品冻结、队列其余保留")
    void queueHeadEntersTrade() throws Exception {
        Goods goods = publishedGoods();
        BuyerAccount first = createBuyer();
        BuyerAccount second = createBuyer();
        Intention head = submit(goods, first, 1);
        Intention other = submit(goods, second, 2);
        String token = sellerToken();

        MvcResult result = mockMvc.perform(post("/api/seller/intentions/{id}/trade", head.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("TRADING"))
                .andExpect(jsonPath("$.data.buyer_username").isNotEmpty())
                .andReturn();

        assertThat(intentions.findById(head.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.TRADING);
        assertThat(intentions.findById(other.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.QUEUED);
        assertThat(goodsRepository.findById(goods.getId()).orElseThrow().getStatus())
                .isEqualTo(GoodsStatus.FROZEN);
    }

    @Test
    @DisplayName("非队首意向不能进入交易")
    void rejectsNonQueueHead() throws Exception {
        Goods goods = publishedGoods();
        submit(goods, createBuyer(), 1);
        Intention second = submit(goods, createBuyer(), 2);

        MvcResult result = trade(second.getId());

        assertApiCode(result, 30002);
        assertThat(intentions.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.QUEUED);
        assertThat(goodsRepository.findById(goods.getId()).orElseThrow().getStatus())
                .isEqualTo(GoodsStatus.ON_SALE);
    }

    @Test
    @DisplayName("商品已冻结（已有交易中意向）时不能再次进入交易")
    void rejectsWhenAnotherIntentionIsTrading() throws Exception {
        Goods goods = publishedGoods();
        Intention head = submit(goods, createBuyer(), 1);
        Intention second = submit(goods, createBuyer(), 2);

        assertApiCode(trade(head.getId()), 0);

        MvcResult result = trade(second.getId());
        assertApiCode(result, 30004);
        assertThat(intentions.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.QUEUED);
    }

    @Test
    @DisplayName("商品不在售时不能进入交易")
    void rejectsWhenGoodsNotOnSale() throws Exception {
        Goods goods = publishedGoods();
        Intention head = submit(goods, createBuyer(), 1);
        goods.changeStatus(GoodsStatus.OFF_MANUAL);
        goodsRepository.saveAndFlush(goods);

        MvcResult result = trade(head.getId());

        assertApiCode(result, 20019);
        assertThat(intentions.findById(head.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.QUEUED);
    }

    @Test
    @DisplayName("非排队中意向与不存在的意向都被拒绝")
    void rejectsInvalidOrUnknownIntention() throws Exception {
        Goods goods = publishedGoods();
        Intention queued = submit(goods, createBuyer(), 1);
        Intention failed = submit(goods, createBuyer(), 2);
        failed.markFailed(FailType.SELLER_VOIDED);
        intentions.saveAndFlush(failed);

        MvcResult terminal = trade(failed.getId());
        assertApiCode(terminal, 30003);

        MvcResult unknown = trade(unique("I-missing"));
        assertApiStatusAndCode(unknown, 404, 30001);

        assertThat(intentions.findById(queued.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.QUEUED);
    }

    @Test
    @DisplayName("成交且库存大于 0：意向成功、商品回在售、其余排队保留")
    void successReturnsGoodsToOnSale() throws Exception {
        Goods goods = publishedGoods();
        Intention head = submit(goods, createBuyer(), 1);
        Intention other = submit(goods, createBuyer(), 2);
        enterTrade(goods, head);

        MvcResult result = mockMvc.perform(post("/api/seller/intentions/{id}/success", head.getId())
                        .header("Authorization", bearer(sellerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.intention.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.goods_status").value("ON_SALE"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(goodsRepository.findById(goods.getId()).orElseThrow())
                .extracting(Goods::getStatus, Goods::getStock)
                .containsExactly(GoodsStatus.ON_SALE, 2);
        assertThat(intentions.findById(head.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.SUCCEEDED);
        assertThat(intentions.findById(head.getId()).orElseThrow().getFinishAt()).isNotNull();
        assertThat(intentions.findById(other.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.QUEUED);
    }

    @Test
    @DisplayName("成交且库存归零：其余排队自动失败，商品与全部意向同事务归档")
    void successArchivesGoodsWhenSoldOut() throws Exception {
        CategoryL2 category = createCategory();
        String l1Name = category.getL1().getName();
        Goods goods = createGoods(category);
        goods.changeStock(1);
        goodsRepository.saveAndFlush(goods);
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));
        BuyerAccount firstBuyer = createBuyer();
        Intention head = enterTrade(goods, submit(goods, firstBuyer, 1));
        Intention queued = submit(goods, createBuyer(), 2);

        MvcResult result = mockMvc.perform(post("/api/seller/intentions/{id}/success", head.getId())
                        .header("Authorization", bearer(sellerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goods_status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.intention.status").value("SUCCEEDED"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        flushAndClear();

        assertThat(goodsRepository.findById(goods.getId())).isEmpty();
        assertThat(intentions.count()).isZero();
        assertThat(queueSequences.findById(goods.getId())).isEmpty();

        GoodsHistory history = goodsHistories.findById(goods.getId()).orElseThrow();
        assertThat(history.getResult()).isEqualTo(GoodsHistoryResult.SOLD_OUT);
        assertThat(history.getStock()).isZero();
        assertThat(history.getCategoryL1Name()).isEqualTo(l1Name);
        assertThat(history.getCategoryL2Name()).isEqualTo(category.getName());
        assertThat(history.getDescriptionSnapshot()).isEqualTo("[]");
        assertThat(history.getImagesSnapshot()).isEqualTo("[]");
        assertThat(history.getArchivedAt()).isNotNull();

        List<IntentionHistory> intentionHistories =
                intentionHistoryList(goods.getId());
        assertThat(intentionHistories).hasSize(2);
        IntentionHistory headHistory = intentionHistories.stream()
                .filter(item -> item.getId().equals(head.getId())).findFirst().orElseThrow();
        assertThat(headHistory.getResult()).isEqualTo(IntentionResult.SUCCEEDED);
        assertThat(headHistory.getFailType()).isNull();
        assertThat(headHistory.getBuyerUsername()).isEqualTo(firstBuyer.getUsername());
        assertThat(headHistory.getBuyerPhone()).isEqualTo(firstBuyer.getPhone());
        assertThat(headHistory.getGoodsName()).isEqualTo(goods.getName());
        IntentionHistory queuedHistory = intentionHistories.stream()
                .filter(item -> item.getId().equals(queued.getId())).findFirst().orElseThrow();
        assertThat(queuedHistory.getResult()).isEqualTo(IntentionResult.FAILED);
        assertThat(queuedHistory.getFailType()).isEqualTo(FailType.SOLD_OUT);
    }

    @Test
    @DisplayName("归档包含商品名下全部意向，含先前已终态的记录")
    void archivesAllTerminalIntentions() throws Exception {
        Goods goods = publishedGoods();
        goods.changeStock(1);
        goodsRepository.saveAndFlush(goods);
        Intention earlier = submit(goods, createBuyer(), 1);
        earlier.markSucceeded();
        intentions.saveAndFlush(earlier);
        Intention head = enterTrade(goods, submit(goods, createBuyer(), 2));
        submit(goods, createBuyer(), 3);

        mockMvc.perform(post("/api/seller/intentions/{id}/success", head.getId())
                        .header("Authorization", bearer(sellerToken())))
                .andExpect(status().isOk());
        flushAndClear();

        assertThat(goodsRepository.findById(goods.getId())).isEmpty();
        assertThat(intentionHistoryList(goods.getId())).hasSize(3);
    }

    @Test
    @DisplayName("商品不在交易中或意向不在交易中时拒绝标记成功")
    void rejectsSuccessOutsideTrade() throws Exception {
        Goods goods = publishedGoods();
        Intention queued = submit(goods, createBuyer(), 1);

        MvcResult goodsNotFrozen = mockMvc.perform(
                        post("/api/seller/intentions/{id}/success", queued.getId())
                                .header("Authorization", bearer(sellerToken())))
                .andReturn();
        assertApiCode(goodsNotFrozen, 20023);

        Intention head = enterTrade(goods, submit(goods, createBuyer(), 2));
        MvcResult intentionNotTrading = mockMvc.perform(
                        post("/api/seller/intentions/{id}/success", queued.getId())
                                .header("Authorization", bearer(sellerToken())))
                .andReturn();
        assertApiCode(intentionNotTrading, 30003);
        assertThat(intentions.findById(head.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.TRADING);

        MvcResult unknown = mockMvc.perform(
                        post("/api/seller/intentions/{id}/success", unique("I-missing"))
                                .header("Authorization", bearer(sellerToken())))
                .andReturn();
        assertApiStatusAndCode(unknown, 404, 30001);
    }

    @Test
    @DisplayName("进入交易需要卖家令牌")
    void requiresSellerSession() throws Exception {
        Goods goods = publishedGoods();
        Intention head = submit(goods, createBuyer(), 1);

        MvcResult anonymous = mockMvc.perform(
                        post("/api/seller/intentions/{id}/trade", head.getId()))
                .andReturn();
        assertApiStatusAndCode(anonymous, 401, 10002);

        String username = unique("buyer-");
        registerBuyer(username, "Buyer@1234", "13800000000", null);
        MvcResult buyer = mockMvc.perform(
                        post("/api/seller/intentions/{id}/trade", head.getId())
                                .header("Authorization", bearer(loginBuyer(username, "Buyer@1234"))))
                .andReturn();
        assertApiStatusAndCode(buyer, 401, 10002);
    }

    @Test
    @DisplayName("作废裁决：意向交易失败、商品回在售、库存不变")
    void failureVoidedTerminatesIntention() throws Exception {
        Goods goods = publishedGoods();
        Intention head = enterTrade(goods, submit(goods, createBuyer(), 1));

        MvcResult result = mockMvc.perform(post("/api/seller/intentions/{id}/failure", head.getId())
                        .header("Authorization", bearer(sellerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("disposal", "VOIDED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.intention.status").value("FAILED"))
                .andExpect(jsonPath("$.data.intention.fail_type").value("SELLER_VOIDED"))
                .andExpect(jsonPath("$.data.goods_status").value("ON_SALE"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(goodsRepository.findById(goods.getId()).orElseThrow())
                .extracting(Goods::getStatus, Goods::getStock)
                .containsExactly(GoodsStatus.ON_SALE, 3);
        assertThat(intentions.findById(head.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.FAILED);
        assertThat(intentions.findById(head.getId()).orElseThrow().getFinishAt()).isNotNull();
    }

    @Test
    @DisplayName("重排裁决：意向回排队、序号至队尾、下单时间不变、新队首是下一位")
    void failureRequeuedMovesToQueueTailKeepingCreateAt() throws Exception {
        Goods goods = publishedGoods();
        Intention head = submit(goods, createBuyer(), 1);
        submit(goods, createBuyer(), 2);
        submit(goods, createBuyer(), 3);
        // 序号行在真实流程中随提交推进；测试直建意向，需同步推进到与意向数一致。
        GoodsQueueSequence sequence = queueSequences.findById(goods.getId()).orElseThrow();
        sequence.nextValue();
        sequence.nextValue();
        sequence.nextValue();
        queueSequences.saveAndFlush(sequence);
        enterTrade(goods, head);
        flushAndClear();
        java.time.LocalDateTime createdAt =
                intentions.findById(head.getId()).orElseThrow().getCreateAt();

        mockMvc.perform(post("/api/seller/intentions/{id}/failure", head.getId())
                        .header("Authorization", bearer(sellerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("disposal", "REQUEUED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intention.status").value("QUEUED"));
        flushAndClear();

        Intention after = intentions.findById(head.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(IntentionStatus.QUEUED);
        assertThat(after.getQueueOrder()).isEqualTo(4);
        assertThat(after.getCreateAt()).isEqualTo(createdAt);
        assertThat(goodsRepository.findById(goods.getId()).orElseThrow().getStatus())
                .isEqualTo(GoodsStatus.ON_SALE);
        assertThat(intentions.findFirstByGoods_IdAndStatusOrderByQueueOrderAsc(
                        goods.getId(), IntentionStatus.QUEUED)
                .orElseThrow().getQueueOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("disposal 缺失或非法时拒绝")
    void rejectsInvalidDisposal() throws Exception {
        Goods goods = publishedGoods();
        Intention head = enterTrade(goods, submit(goods, createBuyer(), 1));
        String token = bearer(sellerToken());

        MvcResult missing = mockMvc.perform(post("/api/seller/intentions/{id}/failure", head.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertApiCode(missing, 30006);

        MvcResult invalid = mockMvc.perform(post("/api/seller/intentions/{id}/failure", head.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("disposal", "CANCELLED"))))
                .andReturn();
        assertApiCode(invalid, 30006);

        assertThat(intentions.findById(head.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentionStatus.TRADING);
        assertThat(goodsRepository.findById(goods.getId()).orElseThrow().getStatus())
                .isEqualTo(GoodsStatus.FROZEN);
    }

    @Test
    @DisplayName("商品不在交易中、意向不在交易中或意向不存在时拒绝标记失败")
    void rejectsFailureOutsideTrade() throws Exception {
        Goods goods = publishedGoods();
        Intention queued = submit(goods, createBuyer(), 1);
        String body = json(java.util.Map.of("disposal", "VOIDED"));

        MvcResult goodsNotFrozen = mockMvc.perform(
                        post("/api/seller/intentions/{id}/failure", queued.getId())
                                .header("Authorization", bearer(sellerToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
        assertApiCode(goodsNotFrozen, 20023);

        Intention head = enterTrade(goods, submit(goods, createBuyer(), 2));
        MvcResult intentionNotTrading = mockMvc.perform(
                        post("/api/seller/intentions/{id}/failure", queued.getId())
                                .header("Authorization", bearer(sellerToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
        assertApiCode(intentionNotTrading, 30003);

        MvcResult unknown = mockMvc.perform(
                        post("/api/seller/intentions/{id}/failure", unique("I-missing"))
                                .header("Authorization", bearer(sellerToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
        assertApiStatusAndCode(unknown, 404, 30001);
    }

    private List<IntentionHistory> intentionHistoryList(String goodsHistoryId) {
        return intentionHistories.findByGoodsHistory_IdOrderByCreateAtAsc(goodsHistoryId);
    }

    /** 直接把意向置为交易中并冻结商品，绕开进入交易接口，聚焦裁决行为。 */
    private Intention enterTrade(Goods goods, Intention intention) {
        intention.enterTrade();
        goods.changeStatus(GoodsStatus.FROZEN);
        intentions.saveAndFlush(intention);
        goodsRepository.saveAndFlush(goods);
        return intention;
    }

    /** 先取令牌再构建请求：登录的嵌套 perform 会清掉外层请求的头。 */
    private MvcResult trade(String intentionId) throws Exception {
        String token = sellerToken();
        return mockMvc.perform(post("/api/seller/intentions/{id}/trade", intentionId)
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private String sellerToken() throws Exception {
        return loginSeller("Abcd@1234");
    }

    private Intention submit(Goods goods, BuyerAccount buyer, long queueOrder) {
        return intentions.saveAndFlush(Intention.submit(goods, buyer, queueOrder));
    }

    private Goods publishedGoods() {
        Goods goods = createGoods(createCategory());
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));
        return goods;
    }
}
