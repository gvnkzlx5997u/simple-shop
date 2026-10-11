package com.simpleshop.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.simpleshop.persistence.entity.Intention;
import com.simpleshop.service.support.UtcIso8601;

/**
 * 意向名单的一行（{@code I11-10} 的 {@code items[]}）。依据：第 11 章 §11.5、方案 §4.6.2。
 *
 * <h2>⚠️ 绝不含 {@code token}</h2>
 * <p>{@code 11-D}／{@code BR-30}／{@code NFR-16}：口令码是敏感字段，须经 {@code I11-14}
 * <b>单独按意向查询</b>，<b>不在名单里批量下发</b>。本记录<b>没有</b> {@code token} 组件，
 * 因此「顺手把它带上」在编译期就不可表达——这是把安全口径做成结构，而不是靠注释提醒。
 *
 * <h2>⚠️ 绝不含 {@code trade_start}</h2>
 * <p>澄清 Q22：{@code trade_start} <b>只记录、不展示</b>（前端页面没有这一列）。
 * 它与 {@code GoodsData} 的情形不同：那里 {@code trade_start} 是契约登记的字段，这里不是。
 *
 * <h2>⚠️ {@code rank} 是 {@code Integer}，不是 {@code int}</h2>
 * <p>因为 {@code trading} 意向<b>不占位次</b>（§9.5.3），其 {@code rank} 必须是 JSON 的
 * {@code null} 且<b>键仍然存在</b>（§11.3.1「不省略键」）。用基本类型就只能编一个
 * {@code 0} 或 {@code -1} 出来，前端拿到的是一个看起来像位次的假值。
 *
 * @param id         意向 ID（前缀 {@code I}）
 * @param rank       位次；{@code trading} 时为 {@code null}
 * @param queueOrder 排序序号（重排队时刷新）
 * @param name       买家姓名
 * @param tel        联系电话
 * @param createAt   原始提交时间（ISO 8601 UTC；<b>不随重排队改变</b>）
 * @param status     状态代码：{@code queued}／{@code trading}／{@code succeeded}／{@code failed}／{@code revoked}
 * @param failType   失败类型代码，可 {@code null}
 * @param failReason 失败备注，可 {@code null}（<b>买家不可见</b>，但卖家可看）
 */
public record IntentionItemData(
        @JsonProperty("id") String id,
        @JsonProperty("rank") Integer rank,
        @JsonProperty("queue_order") int queueOrder,
        @JsonProperty("name") String name,
        @JsonProperty("tel") String tel,
        @JsonProperty("create_at") String createAt,
        @JsonProperty("status") String status,
        @JsonProperty("fail_type") String failType,
        @JsonProperty("fail_reason") String failReason) {

    /**
     * 由实体构造（映射点）。逐字段显式赋值，<b>不</b>用反射式拷贝——后者会把实体将来新增的列
     * 静默带进响应，违反 §11.1「接口层不得引入未登记字段」（方案 §4.6.4）。
     *
     * <p>枚举 → 字符串统一用 {@code name()}（{@code 9-Q}：<b>常量名即库中代码</b>），
     * 用 {@code toString()}／{@code getLabel()} 会把中文或其它表示漏给前端。
     *
     * @param intention 意向实体（须在事务内读取，其 {@code goods} 是 {@code LAZY}，本方法不触碰它）
     * @param rank      位次；{@code trading} 传 {@code null}（由 Service 判定，见该处说明）
     */
    public static IntentionItemData from(Intention intention, Integer rank) {
        return new IntentionItemData(
                intention.getId(),
                rank,
                intention.getQueueOrder(),
                intention.getName(),
                intention.getTel(),
                UtcIso8601.of(intention.getCreateAt()),
                intention.getStatus().name(),
                intention.getFailType() == null ? null : intention.getFailType().name(),
                intention.getFailReason());
    }
}
