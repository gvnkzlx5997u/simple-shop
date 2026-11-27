package com.simpleshop.address.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 地址条目。不含买家 ID：地址簿只对本人开放，调用方拿到的必然是自己那一份。
 */
public record AddressData(
        @JsonProperty("id") String id,
        @JsonProperty("receiver_name") String receiverName,
        @JsonProperty("receiver_phone") String receiverPhone,
        @JsonProperty("detail_address") String detailAddress,
        @JsonProperty("is_default") boolean isDefault,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("update_at") String updateAt) {
}
