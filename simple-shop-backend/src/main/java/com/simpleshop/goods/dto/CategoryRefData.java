package com.simpleshop.goods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 商品所属的一级/二级类别引用。
 */
public record CategoryRefData(
        @JsonProperty("l1_id") String l1Id,
        @JsonProperty("l1_name") String l1Name,
        @JsonProperty("l2_id") String l2Id,
        @JsonProperty("l2_name") String l2Name) {
}
