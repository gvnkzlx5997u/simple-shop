package com.simpleshop.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.simpleshop.persistence.entity.GoodsHistory;

/**
 * 历史商品仓储（设计说明书 §7.4）。主键为 {@code String}。
 *
 * <h2>⚠️ 只提供一种排序：{@code trade_end} 倒序</h2>
 * <p>{@code I11-15} 明确「历史列表按 {@code trade_end} 倒序」（{@code DEC-29}、{@code DEC-35}）。
 * 为与该口径<b>唯一一致</b>，本接口<b>只</b>提供 {@link #findAllByOrderByTradeEndDesc(Pageable)}，
 * <b>不</b>提供按 {@code createAt} 或 {@code id} 排序的重载——避免调用处在多个重载中选错排序口径（§7.4）。
 *
 * <p>对应的索引为 {@code idx_goods_history_trade_end}（单列升序索引即可支撑倒序扫描，
 * <b>不使用</b> {@code DESC} 索引，见 §4.3.2）。
 *
 * <p>其余取数（{@link #findById(Object)}、{@link #existsById(Object)}）继承自 {@link JpaRepository}，
 * 服务 {@code I11-16} 历史详情与 {@code 20011}（404）判定。
 */
public interface GoodsHistoryRepository extends JpaRepository<GoodsHistory, String> {

    /**
     * 历史商品列表分页：按 {@code trade_end} <b>倒序</b>。
     *
     * <p>用于 {@code I11-15}。排序已内建在方法名中，调用方只需传分页参数。
     *
     * @param pageable 分页参数（排序由本方法固定，调用方无需也无法覆盖）
     * @return 按交易结束时间倒序的分页结果
     */
    Page<GoodsHistory> findAllByOrderByTradeEndDesc(Pageable pageable);
}
