package com.simpleshop.aftersale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 卖家登记处理结果。用字符串收再转枚举：非法值要落到业务码 81005，
 * 而不是被 Jackson 变成「参数格式错误」。
 */
public record AftersaleCompleteRequest(
        @JsonProperty("result") String result) {
}
