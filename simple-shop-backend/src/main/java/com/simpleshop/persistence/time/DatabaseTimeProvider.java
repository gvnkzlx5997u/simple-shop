package com.simpleshop.persistence.time;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

/**
 * 全项目<b>唯一</b>的时间来源（设计说明书 §4.8「写入来源」、§10.5「时间口径」）。
 *
 * <h2>强约束（§4.8、§10.5）</h2>
 * <ul>
 *   <li>库内所有 {@code datetime} 列的值一律为 <b>UTC</b>（{@code C-10}、{@code DEC-27}）。</li>
 *   <li>该值<b>只能</b>由本类提供：{@link #nowUtc()}。</li>
 *   <li><b>全项目禁止</b>直接使用 {@code LocalDateTime.now()} 入库——
 *       它取的是 JVM 默认时区（本机为 UTC+8），写入库中即为本地时间，
 *       与「库内即为 UTC」的目标直接冲突。</li>
 *   <li><b>全项目禁止</b>使用 {@code new Date()}／{@code System.currentTimeMillis()} 入库——
 *       语义为时间戳且时区含义隐式，与列类型 {@code datetime} + {@link LocalDateTime} 的口径不一致
 *       （§9.2.7、{@code DEC-DB-09}）。</li>
 *   <li>UTC+8 的<b>展示</b>转换不在数据层完成（{@code C-11}／{@code NFR-18}），由应用层／前端负责。</li>
 *   <li>归档复制时历史表的时间列<b>必须继承原值</b>，不得被回调重写（§6.2.2）。</li>
 * </ul>
 *
 * <h2>两种用法</h2>
 * <p>① 业务代码（Service，本阶段范围之外）：注入本 Bean 后调用 {@link #nowUtc()}。
 * <p>② 实体生命周期回调（{@code @PrePersist}／{@code @PreUpdate}）：回调由 Hibernate 调用，
 * 无法依赖注入，使用静态 {@link #utcNow()}（{@link #nowUtc()} 内部即委托给它）。
 * 实体侧禁止自行调用 {@code LocalDateTime.now()}。
 *
 * <p>本类无状态、线程安全。时间读取为<b>单点</b>，便于单测注入固定时间。
 */
@Component
public class DatabaseTimeProvider {

    /** 库内时间的唯一时区口径：UTC（{@code C-10}）。 */
    public static final ZoneOffset STORAGE_ZONE = ZoneOffset.UTC;

    /**
     * 当前 UTC 时间，供注入式业务代码使用（§10.5 指定的统一入口）。
     *
     * @return 以 UTC 为基准的「墙上时间」，可直接写入 {@code datetime} 列
     */
    public LocalDateTime nowUtc() {
        return utcNow();
    }

    /**
     * 当前 UTC 时间，供 JPA 实体生命周期回调（{@code @PrePersist}／{@code @PreUpdate}）使用。
     *
     * <p>与 {@link #nowUtc()} 完全等价，仅解决回调中无法依赖注入的问题。
     *
     * @return 以 UTC 为基准的「墙上时间」
     */
    public static LocalDateTime utcNow() {
        return LocalDateTime.now(STORAGE_ZONE);
    }
}
