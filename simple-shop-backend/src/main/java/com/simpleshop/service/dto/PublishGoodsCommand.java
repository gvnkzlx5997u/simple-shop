package com.simpleshop.service.dto;

/**
 * 发布商品的内部命令对象（Service 层的入参）。
 *
 * <p>依据：方案 §3.3.2 的 {@code publishGoods(PublishGoodsCommand cmd)}。
 *
 * <h2>⚠️ 为什么不让 Service 直接收 {@code web/dto/PublishGoodsRequest}</h2>
 * <p>这样 Service 就<b>不依赖 web 层</b>：下一轮买家端 JSP 直接调用 Service 时，
 * 不需要构造一个「请求体 DTO」；将来若卖家端换成 GraphQL 或消息触发，Service 一行不用改。
 * <p>同时它也解决了签名上的实际问题：四个字段<b>全是 {@code String}</b>，
 * 若写成 {@code publishGoods(String, String, String, String)}，调用处把
 * {@code name} 与 {@code description} 传反是<b>编译期查不出来</b>的。
 * 命令对象让「谁是谁」由字段名承担。
 *
 * <h2>字段保持原始字符串形态</h2>
 * <p>{@code price} 在此仍是 {@code String}（不是 {@code BigDecimal}）：
 * 「字符串 → BigDecimal」的解析与拒绝发生在 {@code GoodsValidator.parsePrice}，
 * 那里才知道该在越界时回 {@code 20010}、在无法解析时回 {@code 50002}。
 * 若在命令对象上就转成 {@code BigDecimal}，解析失败的语义会退化成一个笼统的反序列化错误。
 *
 * @param name        商品名称（必填，≤50 字符，拒绝 {@code <>}，不允许换行）
 * @param description 商品描述（可选，≤500 字符，拒绝 {@code <>}，允许换行）
 * @param picUrl      图片相对路径（可选；非空时必须是本服务落盘过的路径）
 * @param price       价格字符串（必填；{@code 0 < price ≤ 999999.99}，最多两位小数）
 */
public record PublishGoodsCommand(String name, String description, String picUrl, String price) {
}
