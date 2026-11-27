package com.simpleshop.aftersale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.aftersale.enums.AftersaleResult;
import com.simpleshop.aftersale.enums.AftersaleStatus;
import com.simpleshop.aftersale.repository.AftersaleRepository;
import com.simpleshop.order.entity.Order;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 卖家售后：查看全部、登记结果、取消。
 *
 * <p>重点在两处口径：换货不占退款额度，同一订单项只能退款一次；完成是终态，不能再取消。
 */
class SellerAftersaleApiTest extends AftersaleTestSupport {

    @Autowired
    private AftersaleRepository aftersales;

    @Test
    @DisplayName("卖家能看到全部售后，可按状态与订单号筛选")
    void listsAftersalesWithFilters() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String aftersaleId = applyAndGetId(buyerToken, order, "商品破损", "外包装破损。",
                List.of(uploadImage(buyerToken)));
        String seller = sellerToken();

        JsonNode all = dataOf(sellerGet(seller, "/api/seller/aftersales", Map.of()));
        assertThat(all.path("total").asLong()).isEqualTo(1);
        JsonNode row = all.path("items").get(0);
        assertThat(row.path("aftersale_id").asText()).isEqualTo(aftersaleId);
        assertThat(row.path("order_no").asText()).isEqualTo(order.getOrderNo());
        assertThat(row.path("goods_name").asText()).isNotBlank();

        assertThat(dataOf(sellerGet(seller, "/api/seller/aftersales",
                Map.of("status", "PENDING"))).path("total").asLong()).isEqualTo(1);
        assertThat(dataOf(sellerGet(seller, "/api/seller/aftersales",
                Map.of("status", "COMPLETED"))).path("total").asLong()).isZero();
        assertThat(dataOf(sellerGet(seller, "/api/seller/aftersales",
                Map.of("order_no", order.getOrderNo()))).path("total").asLong()).isEqualTo(1);
        assertThat(dataOf(sellerGet(seller, "/api/seller/aftersales",
                Map.of("order_no", "SO-NOT-EXISTS"))).path("total").asLong()).isZero();
    }

    @Test
    @DisplayName("完成换货：不产生退款金额与时间")
    void completesExchangeWithoutRefund() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String aftersaleId = applyAndGetId(buyerToken, order, "换货申请", "尺寸不合适。", List.of());

        JsonNode data = dataOf(complete(sellerToken(), aftersaleId, "EXCHANGE"));

        assertThat(data.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(data.path("result").asText()).isEqualTo("EXCHANGE");
        assertThat(data.path("refund_amount").isNull()).isTrue();
        assertThat(data.path("refunded_at").isNull()).isTrue();
    }

    @Test
    @DisplayName("完成仅退款：金额按订单项单价 × 数量，时间落库")
    void completesRefundWithComputedAmount() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String aftersaleId = applyAndGetId(buyerToken, order, "仅退款", "商品有问题。", List.of());

        JsonNode data = dataOf(complete(sellerToken(), aftersaleId, "REFUND_ONLY"));

        assertThat(data.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(data.path("result").asText()).isEqualTo("REFUND_ONLY");
        assertThat(data.path("refund_amount").decimalValue())
                .isEqualByComparingTo(order.getTotalAmount());
        assertThat(data.path("refunded_at").asText()).endsWith("Z");
    }

    @Test
    @DisplayName("同一订单项第二次退款：81004；换货不占额度")
    void refundQuotaIsPerOrderItem() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);

        String first = applyAndGetId(buyerToken, order, "换货", "先换货。", List.of());
        assertApiCode(complete(sellerToken(), first, "EXCHANGE"), 0);

        // 换货不占额度：同一订单项还能再申请并退款
        String second = applyAndGetId(buyerToken, order, "退款", "换货后仍不满意。", List.of());
        assertApiCode(complete(sellerToken(), second, "RETURN_AND_REFUND"), 0);

        // 第二次退款被拒
        String third = applyAndGetId(buyerToken, order, "再退一次", "重复申请。", List.of());
        assertApiCode(complete(sellerToken(), third, "REFUND_ONLY"), 81004);
        assertThat(aftersales.findById(third).orElseThrow().getStatus())
                .isEqualTo(AftersaleStatus.PENDING);
    }

    @Test
    @DisplayName("结果缺失或非法：81005")
    void rejectsMissingOrUnknownResult() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String aftersaleId = applyAndGetId(buyerToken, order, "标题", "描述", List.of());
        String seller = sellerToken();

        assertApiCode(complete(seller, aftersaleId, null), 81005);
        assertApiCode(complete(seller, aftersaleId, "PARTIAL_REFUND"), 81005);
        assertThat(aftersales.findById(aftersaleId).orElseThrow().getStatus())
                .isEqualTo(AftersaleStatus.PENDING);
    }

    @Test
    @DisplayName("取消：待处理可取消且重复取消幂等；已完成的不能取消（81002）")
    void cancelsPendingAndRejectsCompleted() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String seller = sellerToken();

        String pendingId = applyAndGetId(buyerToken, order, "取消我", "描述", List.of());
        JsonNode canceled = dataOf(cancel(seller, pendingId));
        assertThat(canceled.path("status").asText()).isEqualTo("CANCELED");
        assertApiCode(cancel(seller, pendingId), 0);

        // 取消后可以重新申请，生成新单号
        String againId = applyAndGetId(buyerToken, order, "重新申请", "描述", List.of());
        assertThat(againId).isNotEqualTo(pendingId);
        assertApiCode(complete(seller, againId, "EXCHANGE"), 0);
        assertApiCode(cancel(seller, againId), 81002);
        assertThat(aftersales.findById(againId).orElseThrow().getResult())
                .isEqualTo(AftersaleResult.EXCHANGE);
    }

    @Test
    @DisplayName("卖家详情带描述与图片；买家令牌不能访问卖家端点")
    void sellerDetailAndRoleIsolation() throws Exception {
        BuyerAccount buyer = createBuyer();
        String buyerToken = bearer(loginBuyer(buyer.getUsername(), PASSWORD));
        Order order = completedOrder(buyer);
        String imageUrl = uploadImage(buyerToken);
        String aftersaleId = applyAndGetId(buyerToken, order, "标题", "详细描述\n第二行。",
                List.of(imageUrl));

        JsonNode detail = dataOf(mockMvc.perform(
                        get("/api/seller/aftersales/{id}", aftersaleId)
                                .header("Authorization", sellerToken()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.path("description").asText()).contains("第二行");
        assertThat(detail.path("image_urls")).hasSize(1);
        assertThat(detail.path("goods_name").asText()).isNotBlank();

        assertApiStatusAndCode(mockMvc.perform(get("/api/seller/aftersales")
                        .header("Authorization", buyerToken))
                .andReturn(), 401, 10002);
    }

    private String sellerToken() throws Exception {
        return bearer(loginSeller("Abcd@1234"));
    }

    private String applyAndGetId(
            String buyerToken, Order order, String title, String description, List<String> urls)
            throws Exception {
        MvcResult applied = apply(buyerToken, firstItemId(order), title, description, urls);
        assertApiCode(applied, 0);
        return dataOf(applied).path("aftersale_id").asText();
    }

    private MvcResult complete(String sellerToken, String id, String result) throws Exception {
        Map<String, Object> body = result == null ? Map.of() : Map.of("result", result);
        return mockMvc.perform(post("/api/seller/aftersales/{id}/complete", id)
                        .header("Authorization", sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private MvcResult cancel(String sellerToken, String id) throws Exception {
        return mockMvc.perform(post("/api/seller/aftersales/{id}/cancel", id)
                        .header("Authorization", sellerToken))
                .andReturn();
    }

    private MvcResult sellerGet(String sellerToken, String path, Map<String, String> query)
            throws Exception {
        var request = get(path).header("Authorization", sellerToken);
        for (Map.Entry<String, String> entry : query.entrySet()) {
            request = request.queryParam(entry.getKey(), entry.getValue());
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    }
}
