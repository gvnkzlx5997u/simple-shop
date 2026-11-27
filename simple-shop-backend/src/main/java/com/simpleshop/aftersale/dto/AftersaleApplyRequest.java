package com.simpleshop.aftersale.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * 售后申请。按订单项申请，不按商品：同一商品可能出现在不同订单里，各申请一次。
 *
 * <p>申请时没有「售后类型」——退、换、仅退款是卖家线下协商后的处理结果，登记在完成时。
 * 图片在申请前先走 `POST /api/buyer/media` 上传，这里只收返回的 URL。
 *
 * <p>标题、描述与图片数量的校验放在服务层（81006 / 81007），不在边界用 Bean Validation：
 * 这几条是业务规则，前端需要拿到可判断的业务码，而不是统一变成 50002 的参数错误。
 */
public record AftersaleApplyRequest(
        @JsonProperty("order_item_id")
        @NotBlank(message = "must not be blank")
        String orderItemId,

        @JsonProperty("title")
        String title,

        @JsonProperty("description")
        String description,

        @JsonProperty("image_urls")
        List<String> imageUrls) {
}
