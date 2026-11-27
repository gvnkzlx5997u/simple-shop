package com.simpleshop.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.simpleshop.common.validation.CodePointLength;

import jakarta.validation.constraints.NotBlank;

/**
 * 发货时的物流信息。承运商与单号必填：买家查物流靠这两项，缺一项订单详情就没东西可看。
 *
 * <p>承运商只做字符串，不做枚举：前端给固定清单加「其他」，历史订单保留提交时的名称，
 * 后端不关心它是不是清单里那几家。单号允许多行——一个订单可能拆成多个包裹。
 */
public record ShipLogisticsRequest(
        @JsonProperty("carrier")
        @NotBlank(message = "must not be blank")
        @CodePointLength(max = 50)
        String carrier,

        @JsonProperty("tracking_no")
        @NotBlank(message = "must not be blank")
        @CodePointLength(max = 500)
        String trackingNo,

        @JsonProperty("logistics_remark")
        @CodePointLength(max = 500)
        String logisticsRemark) {
}
