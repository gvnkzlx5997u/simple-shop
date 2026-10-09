/**
 * JPA 实体（设计说明书 §6）。
 *
 * <p>本包包含 7 个实体：{@code Goods}、{@code Intention}、{@code GoodsHistory}、
 * {@code IntentionHistory}、{@code TradeHistory}、{@code ShopUser}、{@code QueueSequence}。
 *
 * <p>实体通用规范见 §6.2：字段访问、{@code protected} 无参构造、类不可 final、
 * 关联一律 {@code FetchType.LAZY} 且不级联、不使用 {@code @OneToMany} 反向集合、
 * 不使用乐观锁 {@code @Version} 与软删除。
 *
 * <h2>时间字段必须逐个标注 {@code @JdbcTypeCode(LOCAL_DATE_TIME)}：为什么</h2>
 * <p>库中所有时间列都是 {@code DATETIME}（不含时区），语义是「字面墙上时间」，
 * 值由应用层按 UTC 写入（{@code C-10}、§4.8、§10.5）。但 Hibernate 6 对
 * {@code LocalDateTime} 的默认绑定走的是 {@code java.sql.Timestamp}，
 * 而 <b>Connector/J 的 {@code setTimestamp} 会按「驱动的会话时区」解释该值并做转换</b>：
 * 实测在本机（JVM 时区 {@code Asia/Shanghai}、连接串 {@code connectionTimeZone=UTC}）下，
 * 应用写入 {@code 11:33:19}（UTC），库中落成 {@code 03:33:19}——<b>整整偏移 8 小时</b>，
 * 直接破坏「库内即为 UTC」这一强约束。
 *
 * <p>加 {@code @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)} 后，Hibernate 改用
 * {@code setObject(..., LocalDateTime)} / {@code getObject(..., LocalDateTime.class)}，
 * 即<b>不做时区转换</b>地按字面值读写；实测同样连接串下写入 {@code 11:33:19} 即落库 {@code 11:33:19}。
 *
 * <p><b>⚠️ 该注解只能标在字段（或 getter）上，不能标在包上</b>：
 * 它声明的是 {@code @Target({FIELD, METHOD})}，写在 {@code package-info.java} 上无法通过编译。
 * 因此本包内<b>每个实体的每个时间字段</b>都单独标注——新增时间字段时<b>必须</b>一并标注，
 * 否则该字段会静默偏 8 小时。
 *
 * <p>注：{@code QueueSequence} 无时间字段，不需要该注解。
 */
package com.simpleshop.persistence.entity;
