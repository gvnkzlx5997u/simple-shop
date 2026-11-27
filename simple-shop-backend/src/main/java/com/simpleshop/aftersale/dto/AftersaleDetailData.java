package com.simpleshop.aftersale.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.aftersale.enums.AftersaleResult;
import com.simpleshop.aftersale.enums.AftersaleStatus;

/**
 * 售后详情：摘要 + 完整描述 + 图片。图片按 sort_no 顺序给，前端不再排序。
 */
public record AftersaleDetailData(
        @JsonProperty("aftersale_id") String aftersaleId,
        @JsonProperty("aftersale_no") String aftersaleNo,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("order_no") String orderNo,
        @JsonProperty("order_item_id") String orderItemId,
        @JsonProperty("goods_name") String goodsName,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("image_urls") List<String> imageUrls,
        @JsonProperty("status") AftersaleStatus status,
        @JsonProperty("result") AftersaleResult result,
        @JsonProperty("refund_amount") BigDecimal refundAmount,
        @JsonProperty("refunded_at") String refundedAt,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("update_at") String updateAt) {

    public AftersaleDetailData {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }
}
