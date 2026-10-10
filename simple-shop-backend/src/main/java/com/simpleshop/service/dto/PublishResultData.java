package com.simpleshop.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 发布商品的响应数据（{@code I11-06} 的 {@code data}）：{@code {id}}。
 *
 * <p>依据：方案 §4.6.2。
 *
 * <p><b>为什么只返回 {@code id}</b>：契约如此（{@code I11-06} 出参为 {@code {id}}）。
 * 前端发布成功后跳回商品页并重新 {@code GET I11-04} 取完整对象——
 * 这样「当前商品」只有<b>一个</b>读取口径，不会出现「发布响应里的字段」与
 * 「查询接口的字段」两套形状。
 *
 * @param id 新商品的 ID（前缀 {@code G}）
 */
public record PublishResultData(@JsonProperty("id") String id) {

    /** 由新建商品的 ID 构造。 */
    public static PublishResultData of(String id) {
        return new PublishResultData(id);
    }
}
