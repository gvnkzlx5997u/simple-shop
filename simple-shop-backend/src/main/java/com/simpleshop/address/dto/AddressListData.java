package com.simpleshop.address.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 地址簿列表：默认地址排最前，其余按创建时间倒序。
 *
 * <p>不分页——上限 10 条，翻页只会让「设默认」这类操作更难找。
 */
public record AddressListData(
        @JsonProperty("items") List<AddressData> items) {

    public AddressListData {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
