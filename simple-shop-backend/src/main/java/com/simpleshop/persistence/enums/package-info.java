/**
 * 枚举字典的 Java 表示（设计说明书 §5）。
 *
 * <p>本包包含 6 个枚举：{@code GoodsStatus}、{@code FreezeBy}、{@code IntentionStatus}、
 * {@code FailType}、{@code GoodsResult}、{@code TradeResult}。
 *
 * <p>关键口径（§5.1）：数据库存小写英文代码，JPA 用 {@code @Enumerated(EnumType.STRING)}，
 * 因此<b>枚举常量名直接写成库中代码的小写下划线形式</b>（如 {@code on_sale}），
 * 不得写成驼峰——命名策略只作用于表名与列名，不影响枚举值。
 */
package com.simpleshop.persistence.enums;
