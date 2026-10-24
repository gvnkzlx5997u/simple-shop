package com.simpleshop.intention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.goods.entity.Goods;
import com.simpleshop.intention.entity.GoodsQueueSequence;
import com.simpleshop.testing.ApiTestBase;

/**
 * 提交意向的队列上限与限流边界。用独立配置启动上下文，
 * 避免小阈值污染共享上下文中的其他测试。
 */
@SpringBootTest(properties = {
        "simple-shop.rate-limit.submit-intention-per-minute=3",
        "simple-shop.queue.max-size=2"})
class BuyerIntentionLimitTest extends ApiTestBase {

    @Test
    @DisplayName("达到队列上限后拒绝提交，超过限流阈值后拒绝请求")
    void rejectsQueueFullThenRateLimited() throws Exception {
        Goods goods = createGoods(createCategory());
        queueSequences.saveAndFlush(GoodsQueueSequence.forGoods(goods.getId()));
        String username = unique("buyer-");
        registerBuyer(username, "Buyer@1234", "13800000000", null);
        String token = loginBuyer(username, "Buyer@1234");
        String body = json(Map.of("goods_id", goods.getId()));

        assertApiCode(submit(token, body), 0);
        assertApiCode(submit(token, body), 0);
        assertApiCode(submit(token, body), 30005);
        assertApiCode(submit(token, body), 50001);

        assertThat(intentions.count()).isEqualTo(2);
    }

    private MvcResult submit(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/buyer/intentions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }
}
