package com.simpleshop.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * 只带一个意向 ID 的请求体（{@code I11-11} 进入交易、{@code I11-12} 标记成功共用）。
 *
 * <p>依据：第 11 章 §11.5、方案 §4.6.3。
 *
 * <h2>⚠️ JSON 键是 {@code intention_id}（{@code snake_case}）</h2>
 * <p>Java 字段是驼峰，契约是下划线（{@code 9-E}）。这里用 {@code @JsonProperty} <b>显式</b>声明，
 * <b>不</b>依赖全局命名策略——全局策略会让代码与 JSON 隐式耦合，
 * 且会连带影响实体（万一误序列化）。手动写一个注解，一眼就能核对契约。
 *
 * <h2>为什么 {@code @NotBlank} 可以留、而长度类注解不能留</h2>
 * <p>{@code intention_id} 在 §4.3 错误码表里<b>没有</b>专属码，故「缺失／空白」走
 * {@code 50002}（HTTP 400）——这正是 §11.3.4 对 400 的定义（报文本身不合法）。
 * 反过来，凡是<b>有</b>专属业务码的字段（如 {@code 20007}／{@code 30006}／{@code 30007}），
 * 注解就只能判「有没有」，取值规则一律归 Service，否则专属码在 HTTP 路径上永不可达
 * （硬规则见方案 §4.6.3，S3 时发现）。
 *
 * <p>「意向存在与否」「是否处于可操作状态」都不是本类的事：它们是**业务**判定，
 * 由 Service 返回 {@code 30003}／{@code 30002}／{@code 30008}（均为 HTTP 200）。
 * DTO 只负责「报文里有没有这个字段」。
 */
public class IntentionIdRequest {

    /** 目标意向 ID（前缀 {@code I}）。<b>仅</b>判有无；存在性与状态由 Service 判。 */
    @NotBlank(message = "intention_id is required")
    @JsonProperty("intention_id")
    private String intentionId;

    public String getIntentionId() {
        return intentionId;
    }

    public void setIntentionId(String intentionId) {
        this.intentionId = intentionId;
    }
}
