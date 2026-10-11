package com.simpleshop.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code I11-06}（发布商品）的请求体：{@code {name, description, pic_url, price}}。
 *
 * <p>依据：第 11 章 §11.6.1、方案 §4.6.3、§3.6。
 *
 * <h2>⚠️ 这里<b>只有</b> {@code @NotBlank}，没有任何 {@code @Size}——这是刻意的</h2>
 * <p>按 S3 实现时确立、并已回写方案 §4.6.3 的硬规则：
 * <blockquote>
 * <b>DTO 注解只判「有没有」，取值规则（长度、字符、区间）一律归 Service。</b>
 * </blockquote>
 * <p>因为 {@code 20007}／{@code 20008}／{@code 20010} 都是契约分配的<b>专属业务码</b>且
 * HTTP 状态为 <b>200</b>（业务拒绝属正常分支）。若在 {@code name} 上写
 * {@code @Size(max = 50)}，请求会在<b>进入 Controller 之前</b>被 Bean Validation 拦下，
 * 经 {@code GlobalExceptionHandler} 变成 {@code 50002} + <b>HTTP 400</b>：
 * <ul>
 *   <li>{@code 20007} 在 HTTP 路径上<b>永不可达</b>（成为死码）；</li>
 *   <li>前端按 {@code code} 映射文案（{@code UX-07}、{@code NFR-09}）时拿到的是通用的
 *       「参数错误」，而<b>不是</b> {@code M10-29}「名称不超过 50 个字符，不能包含
 *       {@code <} {@code >}」——界面提示直接错。</li>
 * </ul>
 * <p>反过来也不存在「漏校验」：{@code GoodsValidator} 仍会独立校验全部规则
 * （{@code G6-02}，且买家端 JSP 下一轮直接调 Service 时不过这条校验链）。
 * <p>判据一句话：<b>先查 §4.3 错误码表里该字段有没有专属码</b>——有就只留 {@code @NotBlank}。
 * 这里 {@code name}/{@code description}/{@code price} 三者都<b>有</b>专属码
 * （{@code 20007}/{@code 20008}/{@code 20010}），{@code pic_url} 有 {@code 20009}，
 * 因此本类<b>一个长度注解都不该有</b>。
 *
 * <h2>⚠️ {@code price} 是 {@code String}，不是 {@code BigDecimal}</h2>
 * <p>契约（{@code 11-F}）规定金额以<b>字符串</b>收发。若这里声明成 {@code BigDecimal}，
 * 除了会被 JSON 数字的浮点语义污染（{@code 999999.99} 在 JS 侧无法精确表示）之外，
 * 还会让「{@code "abc"}" 这类输入在 Jackson 反序列化阶段就变成笼统的
 * {@code HttpMessageNotReadableException} → {@code 50002}，
 * 而 {@code GoodsValidator} 想要区分「越界（{@code 20010}）」与「不是数字（{@code 50002}）」
 * 就再也做不到了。
 *
 * <h2>⚠️ 本类刻意<b>不</b>覆写 {@code toString()}</h2>
 * <p>{@code LoginRequest}／{@code ChangePasswordRequest} 屏蔽字段是因为它们含<b>口令</b>。
 * 本类的四个字段都是要展示给用户的商品信息，没有秘密——为了「统一风格」而给它们加
 * {@code ***} 只会让排查发布问题时看不到实际提交了什么。
 * 换言之，那两处的覆写是<b>按敏感度</b>做的，不是模板。
 */
public class PublishGoodsRequest {

    /** 商品名称。<b>仅</b>判有无；≤50 字符／拒绝 {@code <>}／不允许换行 由 Service 判 → {@code 20007}。 */
    @NotBlank(message = "name is required")
    @JsonProperty("name")
    private String name;

    /** 商品描述。可选（{@code null} 合法），故<b>无</b> {@code @NotBlank}；≤500 字符／拒绝 {@code <>} → {@code 20008}。 */
    @JsonProperty("description")
    private String description;

    /** 图片相对路径，来自 {@code I11-05} 的返回值。可选；非法/文件不存在 → {@code 20009}。 */
    @JsonProperty("pic_url")
    private String picUrl;

    /** 价格字符串。<b>仅</b>判有无；{@code 0 < price ≤ 999999.99} 且最多两位小数 → {@code 20010}。 */
    @NotBlank(message = "price is required")
    @JsonProperty("price")
    private String price;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPicUrl() {
        return picUrl;
    }

    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }
}
