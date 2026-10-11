package com.simpleshop.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code I11-13}（标记交易失败）的请求体：{@code {intention_id, disposal, fail_reason}}。
 *
 * <p>依据：第 11 章 §11.5、方案 §4.6.3、Q-14。
 *
 * <h2>⚠️ {@code disposal} 是 {@code String}，<b>不是</b> Java 枚举（已定 Q-14）</h2>
 * <p>若声明成枚举，非法值会在 <b>Jackson 反序列化阶段</b>就抛
 * {@code HttpMessageNotReadableException} → {@code 50002} + HTTP <b>400</b>，
 * 而契约给这个字段分配的是 <b>{@code 30006}</b>（HTTP 200）——又一条「专属业务码永不可达」的死码。
 * <p>同时它<b>也没有</b> {@code @NotBlank}：{@code disposal} 有专属码（{@code 30006}），
 * 按硬规则注解只判「有没有」，而「没填」在这里的表达就是「取值非法」，
 * 由 Service 的 {@code TradeFailureValidator.parseDisposal} 统一抛 {@code 30006}。
 * 若在这里加 {@code @NotBlank}，漏填会被拦成 {@code 50002}+400，形成同一输入两个结论。
 *
 * <h2>⚠️ {@code fail_reason} <b>不得</b>加 {@code @Size(max = 300)}（{@code 30007}）</h2>
 * <p>这是 §4.6.3 硬规则里点名的一处：{@code fail_reason} 有专属码 {@code 30007}（HTTP 200）。
 * 加了长度注解，超长请求会在进 Controller 前被拦成 {@code 50002}+HTTP 400，
 * 前端拿到的就不是「备注太长」的就地提示，而是一句通用的「参数错误」——
 * <b>界面文案直接错了</b>（{@code UX-07}、{@code NFR-09}）。
 * <p>超长与含 {@code <}/{@code >} 的判定都在
 * {@link com.simpleshop.service.support.TradeFailureValidator#normalizeFailReason}。
 *
 * <h2>⚠️ 本类<b>不</b>覆写 {@code toString()}</h2>
 * <p>它的三个字段都不是口令或密码。{@code fail_reason} 虽是用户输入且买家不可见，
 * 但**服务端日志里出现卖家自己写的失败备注**不构成泄漏面（泄漏的是拿给买家看）。
 * 为了统一风格而屏蔽它，只会让排查「这次失败卖家填了什么」时看不到内容。
 */
public class TradeFailureRequest {

    /** 目标意向 ID（处于 {@code trading}）。<b>仅</b>判有无；状态由 Service 判 → {@code 30003}。 */
    @NotBlank(message = "intention_id is required")
    @JsonProperty("intention_id")
    private String intentionId;

    /** 裁决方式：{@code voided}／{@code requeued}。按 {@code String} 收，Service 判 → {@code 30006}。 */
    @JsonProperty("disposal")
    private String disposal;

    /** 失败备注，可空（≤300 字符，允许换行，买家不可见）。Service 判 → {@code 30007}。 */
    @JsonProperty("fail_reason")
    private String failReason;

    public String getIntentionId() {
        return intentionId;
    }

    public void setIntentionId(String intentionId) {
        this.intentionId = intentionId;
    }

    public String getDisposal() {
        return disposal;
    }

    public void setDisposal(String disposal) {
        this.disposal = disposal;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }
}
