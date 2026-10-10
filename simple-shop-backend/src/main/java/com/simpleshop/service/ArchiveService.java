package com.simpleshop.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.persistence.entity.EntityFactory;
import com.simpleshop.persistence.entity.Goods;
import com.simpleshop.persistence.entity.GoodsHistory;
import com.simpleshop.persistence.entity.Intention;
import com.simpleshop.persistence.enums.GoodsResult;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.repository.GoodsHistoryRepository;
import com.simpleshop.persistence.repository.GoodsRepository;
import com.simpleshop.persistence.repository.IntentionHistoryRepository;
import com.simpleshop.persistence.repository.IntentionRepository;
import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;

/**
 * 归档事务（{@code PS-03} 标记成功 / {@code PS-08} 手动下架，方案 §3.3.6）。
 *
 * <h2>本方法只做「搬迁」，不做「流水追加」（职责边界）</h2>
 * <p>调用的两个业务动作与它们各自的流水责任：
 * <table border="1">
 *   <caption>谁追加流水</caption>
 *   <tr><th>业务动作</th><th>谁追加</th><th>何时</th></tr>
 *   <tr><td>{@code markTradeSuccess}</td><td>{@code SellerIntentionService}</td>
 *       <td>在调 {@link #archive} <b>之前</b>，为本次 {@code trading} 意向追加 {@code sold} 一条</td></tr>
 *   <tr><td>{@code takeGoodsOffline}</td><td><b>无人</b></td>
 *       <td>手动下架时没有任何意向进入过交易，故<b>不产生流水</b>（{@code §9.9.5}）</td></tr>
 *   <tr><td>{@code ArchiveService}</td><td><b>一条都不追加</b></td><td>—</td></tr>
 * </table>
 * <p>把「追加流水」塞进本类会造成「到底谁负责」的语义混乱；更硬的原因是
 * {@code simpleshop_trade_history} 的 {@code trade_start} 与 {@code trade_end}
 * <b>都是 NOT NULL</b>，而归档本身并不总是拥有这两个时间戳。
 *
 * <h2>⚠️ 两个致命顺序（写反了会直接失败，且只有真库用例能发现）</h2>
 * <ol>
 *   <li><b>历史商品必须【先于】历史意向落库</b>，且用 {@code saveAndFlush} 显式落定。
 *       {@code simpleshop_intentions_history} 上有物理外键
 *       {@code fkey_intention_history_fk_goods_history_id → simpleshop_goods_history(id)}
 *       （{@code ON DELETE RESTRICT}）。顺序写反会被数据库直接拒绝——S7 已实测
 *       （把 {@code saveAndFlush} 挪到 {@code forEach} 之后 → 10 个归档用例里 6 个失败）。
 *       <p>⚠️ <b>关于 {@code saveAndFlush} 的确切作用（S7 校正）</b>：用它是为了<b>显式保证</b>顺序，
 *       而<b>不</b>应理解成「只 {@code save} 就一定会撞外键」——实测把 {@code saveAndFlush}
 *       换成 {@code save}（代码顺序不变），10 个归档用例<b>全部通过</b>：当前 Hibernate 版本
 *       对这类实体图的 INSERT 排序恰好是有利的。也就是说
 *       <b>「{@code save} 行不行」取决于 Hibernate 的内部排序，而 {@code saveAndFlush}
 *       把这个依赖去掉了</b>——这才是保留它的理由，而不是它能修掉某个已观测到的失败。</li>
 *   <li><b>删除用 {@code deleteAll} 而不是 {@code deleteAllInBatch}</b>。
 *       后者绕过持久化上下文直接发一条 {@code DELETE}，而上面已被管理的实体仍被 Hibernate
 *       视为持久态。S7 已实测改成 {@code deleteAllInBatch} 的<b>实际症状</b>：
 *       10 个归档用例里 6 个以 {@code TransientObjectException: persistent instance references
 *       an unsaved transient instance of 'Goods'} 失败——失败点是<b>随后删除商品的那次刷新</b>，
 *       而不是最初预判的「等到提交时才把旧行重新插回去」。
 *       症状不同、根因相同：绕过持久化上下文就会让上下文与库不一致。</li>
 * </ol>
 *
 * <h2>⚠️ 当前表的 {@code status}／{@code result}／{@code trade_end} 一律不写</h2>
 * <p>{@code 9-H} 与 {@code DEC-DB-11} 已定：{@code off_sale} 只出现在历史表、
 * {@code simpleshop_goods.result} 恒为 NULL。这些值全都只写在 {@code GoodsHistory} 上。
 * 归档路径下当前表那一行随即被删除，写它既无意义又会让「恒 NULL」的约定失效。
 *
 * <h2>⚠️ 历史实体不能有生命周期回调</h2>
 * <p>{@code GoodsHistory}／{@code IntentionHistory} <b>一条回调都没有</b>（刻意如此），
 * 因此 {@link EntityFactory} 逐列显式赋值、时间列<b>继承原值</b>。
 * <b>绝不可</b>「为了让时间自动填上」而给历史实体加 {@code @PreUpdate}——
 * 那会让二次保存覆盖掉归档时间。
 */
@Service
public class ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);

    /** 活动（非终态）状态集合，用于 {@code INV-05} 的前置校验。 */
    private static final List<IntentionStatus> ACTIVE_STATUSES =
            List.of(IntentionStatus.queued, IntentionStatus.trading);

    private final GoodsRepository goodsRepository;
    private final GoodsHistoryRepository goodsHistoryRepository;
    private final IntentionRepository intentionRepository;
    private final IntentionHistoryRepository intentionHistoryRepository;

    public ArchiveService(GoodsRepository goodsRepository,
                          GoodsHistoryRepository goodsHistoryRepository,
                          IntentionRepository intentionRepository,
                          IntentionHistoryRepository intentionHistoryRepository) {
        this.goodsRepository = goodsRepository;
        this.goodsHistoryRepository = goodsHistoryRepository;
        this.intentionRepository = intentionRepository;
        this.intentionHistoryRepository = intentionHistoryRepository;
    }

    /**
     * 把当前商品及其全部意向整体迁入历史表，并清空当前表。
     *
     * <h2>调用契约（调用方必须满足）</h2>
     * <ul>
     *   <li><b>必须在事务内</b>调用，且<b>已持有该商品的行锁</b>
     *       （{@code goodsRepository.findByIdForUpdate}）——否则并发进入交易可能在
     *       「校验通过」与「搬迁」之间插入一条新的活动意向；</li>
     *   <li>{@code tradeEnd} 与 {@code result} 由调用方给出：标记成功时
     *       {@code tradeEnd = 成交时间、result = sold}；手动下架时
     *       {@code tradeEnd = 下架时间、result = offline}。</li>
     * </ul>
     *
     * <h2>{@code INV-05} 的前置校验是<b>唯一</b>拦截点</h2>
     * <p>{@code countActiveByGoodsId > 0} → 抛 {@code 30005}，整体回滚，商品<b>不</b>进入已下架。
     * <p>⚠️ <b>数据库侧没有任何兜底</b>：{@code ck_intention_history_status_terminal} 已在
     * {@code DEC-DB-14} 中被删除。如果这里漏判，非终态意向会被静默写进历史表，
     * 而历史表按定义只应存放终态——这类脏数据<b>事后无法自动修复</b>（位次、口令码都已丢失）。
     *
     * @param goods    当前商品（须已加锁、且处于可归档状态）
     * @param result   商品级结果：{@code sold}（标记成功）／{@code offline}（手动下架）
     * @param tradeEnd 交易结束时间（= 归档时间点，必填，UTC）
     * @throws BusinessException {@code 30005}——仍有非终态意向，拒绝归档
     */
    @Transactional
    public void archive(Goods goods, GoodsResult result, LocalDateTime tradeEnd) {
        String goodsId = goods.getId();

        // ① INV-05 前置校验（第一步，且必须在搬迁前）
        long active = intentionRepository.countActiveByGoodsId(goodsId, ACTIVE_STATUSES);
        if (active > 0) {
            log.warn("archive rejected by INV-05: goodsId={} activeIntentions={}", goodsId, active);
            throw new BusinessException(ErrorCode.ARCHIVE_PRECONDITION_FAILED);
        }

        // ② 取全部意向（含终态），按 queue_order 升序
        List<Intention> intentions = intentionRepository.findByGoodsIdOrderByQueueOrderAsc(goodsId);

        // ③ ④ 先写【历史商品】并 flush —— 必须早于历史意向（见类注释的顺序①）
        GoodsHistory goodsHistory = EntityFactory.newGoodsHistory(goods, tradeEnd, result);
        goodsHistoryRepository.saveAndFlush(goodsHistory);

        // ⑤ 再写【历史意向】。传第 ④ 步已持久化的实体实例（而非 ID）——
        //    该关联是 @ManyToOne，且此时无需任何额外查询。
        intentions.forEach(intention -> intentionHistoryRepository.save(
                EntityFactory.newIntentionHistory(intention, goodsHistory)));

        // ⑥ 最后清【当前表】。先子后父（意向 → 商品），与物理外键方向一致。
        intentionRepository.deleteAll(intentions);
        goodsRepository.delete(goods);

        log.info("archived goods: goodsId={} result={} tradeEnd={} intentions={}",
                goodsId, result, tradeEnd, intentions.size());
    }
}
