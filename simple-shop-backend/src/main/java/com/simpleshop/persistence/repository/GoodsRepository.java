package com.simpleshop.persistence.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.persistence.entity.Goods;
import com.simpleshop.persistence.enums.GoodsStatus;

import jakarta.persistence.LockModeType;

/**
 * 商品仓储（设计说明书 §7.2）。主键为 {@code String}。
 *
 * <h2>本仓储的定位</h2>
 * <p>商品表<b>至多 1 行</b>（{@code INV-01}），因此没有分页、没有条件查询的必要；
 * 本接口只提供「取当前商品」「判定是否已存在商品」「按 ID 加锁」三类入口。
 *
 * <h2>⚠️ 刻意不提供的方法</h2>
 * <ul>
 *   <li><b>不提供任何更新商品名称／描述／图片／价格的方法</b>（{@code BR-15}、§6.2.4）。
 *       这是「发布后不可修改」的第二道防线——实体保留 setter（JPA 与归档复制需要），
 *       但仓储层不开任何 {@code update*}／{@code @Modifying} 入口。</li>
 *   <li>不提供返回 {@code List<Goods>} 的无参全表方法（§7.1「不提供无界全表查询」）。</li>
 * </ul>
 *
 * <h2>加锁契约（§8.2，调用方必须遵守）</h2>
 * <p>凡<b>改变商品状态</b>或<b>改变「是否有意向处于 {@code trading}」</b>的事务，
 * <b>必须首先</b>调用 {@link #findByIdForUpdate(String)} 取得商品行锁
 * （进入交易、标记结果、手动冻结/解冻、手动下架）。
 * 该方法对应 {@code SELECT ... FOR UPDATE}，<b>必须在事务内调用</b>，否则行锁立即释放。
 */
public interface GoodsRepository extends JpaRepository<Goods, String> {

    /**
     * 取当前商品（唯一行）。
     *
     * <p>对应 {@code I11-04}、买家端首页。表内至多 1 行，取 {@code create_at} 升序首行。
     * 按 {@code createAt} 排序而非依赖默认顺序，避免数据库返回顺序不确定（§7.1）。
     *
     * @return 当前商品；表空时 {@link Optional#empty()}
     */
    Optional<Goods> findFirstByOrderByCreateAtAsc();

    /**
     * 是否存在处于给定状态之一的商品。用于「发布商品」的前置校验
     * （{@code I11-06} → {@code 20001}）。
     *
     * <p><b>⚠️ 本方法只提供判定依据，不做拒绝</b>（§7.1：仓储不抛业务异常）。
     * 拒绝逻辑与提示文案归 Service。</p>
     *
     * <p><b>⚠️ {@code INV-01} 的并发竞态属已接受风险</b>（{@code DEC-DB-06}、§4.1.2、§8.5）：
     * 「先查再插」在并发下不可靠。单管理员场景下判定可接受；
     * 若将来引入多管理员或注册入口，须回头重评（§8.5 保留了可选加固方案）。
     *
     * @param statuses 目标状态集合，通常为 {@code {on_sale, frozen}}
     * @return 存在返回 {@code true}
     */
    boolean existsByStatusIn(Collection<GoodsStatus> statuses);

    /**
     * 是否存在处于给定状态的商品。用于手动解冻的前置校验
     * （{@code I11-08} → {@code 20004}，{@code PS-05}）。
     *
     * @param status 目标状态，通常为 {@code frozen}
     * @return 存在返回 {@code true}
     */
    boolean existsByStatus(GoodsStatus status);

    /**
     * 按 ID <b>加排他锁</b>读取商品行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>服务对象：进入交易、标记交易结果、解冻、手动下架（{@code INV-02}、{@code INV-04}、{@code NFR-01}）。
     *
     * <p>用于把「读取当前状态 → 校验迁移合法性 → 写入新状态」串行化在商品这一行上，
     * 从而防住「两条不同意向同时进入交易」（§8.2 已论证：单靠条件 {@code UPDATE} 抢占防不住）。
     *
     * <p><b>⚠️ 必须在事务内调用</b>（§8.3 末尾）：脱离事务时 {@code FOR UPDATE} 的行锁会立即释放，
     * 保护形同虚设。
     *
     * @param id 商品 ID
     * @return 商品；不存在时 {@link Optional#empty()}
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Goods g where g.id = :id")
    Optional<Goods> findByIdForUpdate(@Param("id") String id);
}
