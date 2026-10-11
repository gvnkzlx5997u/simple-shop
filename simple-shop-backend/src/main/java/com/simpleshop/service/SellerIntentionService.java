package com.simpleshop.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.persistence.entity.EntityFactory;
import com.simpleshop.persistence.entity.Goods;
import com.simpleshop.persistence.entity.Intention;
import com.simpleshop.persistence.entity.QueueSequence;
import com.simpleshop.persistence.enums.FailType;
import com.simpleshop.persistence.enums.FreezeBy;
import com.simpleshop.persistence.enums.GoodsResult;
import com.simpleshop.persistence.enums.GoodsStatus;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.enums.TradeResult;
import com.simpleshop.persistence.repository.GoodsRepository;
import com.simpleshop.persistence.repository.IntentionRepository;
import com.simpleshop.persistence.repository.QueueSequenceRepository;
import com.simpleshop.persistence.repository.TradeHistoryRepository;
import com.simpleshop.persistence.time.DatabaseTimeProvider;
import com.simpleshop.service.dto.IntentionItemData;
import com.simpleshop.service.dto.IntentionPageData;
import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;
import com.simpleshop.service.support.TradeFailureValidator;

/**
 * 卖家端意向与交易服务（{@code FR-07} ~ {@code FR-10}、{@code FR-24}）。方案 §3.3.3。
 *
 * <pre>
 * IntentionPageData listIntentions(page, pageSize);                       // FR-08 → I11-10
 * void              enterTrade(intentionId);                              // FR-07 → I11-11
 * void              markTradeSuccess(intentionId);                        // FR-09 → I11-12
 * void              markTradeFailure(intentionId, disposal, failReason);   // FR-10 → I11-13
 * String            getPasscode(intentionId);                             // FR-24 → I11-14
 * </pre>
 *
 * <h2>加锁顺序（{@code §3.2}）——本类是全系统唯一需要「先序号表」的写路径之一</h2>
 * <p>统一顺序是 <b>① 序号表行 → ② 商品行 → ③ 意向行</b>。本类三个状态变更方法的第一把锁
 * 都是<b>②商品行</b>，只有 {@link #markTradeFailure} 在「重新排队」分支上需要序号表行——
 * 而它在方法内被<b>提到商品行锁之前</b>（见该方法注释）。这不是风格问题：
 * 买家提交意向是「先序号表、不锁商品」，若这里反过来「先商品、再序号表」，
 * 两条路径就构成<b>反向取锁</b>，并发时直接死锁（风险 R3）。
 *
 * <h2>「当前商品」是本类的定位方式</h2>
 * <p>与 {@code SellerGoodsService} 同：商品表至多 1 行（{@code INV-01}），
 * 故 {@code I11-10}~{@code I11-13} 都没有商品 ID 参数。
 * <p><b>注意 {@code I11-10} 的路径是 {@code /api/seller/product/intentions}</b>
 * （在 {@code product} 下），而 {@code I11-11}~{@code I11-14} 在 {@code /api/seller/intention/*}
 * 下（单数 {@code intention}）——这是契约里最容易写错的一处路径。
 *
 * <h2>⚠️ 三个方法都不碰 {@code goods.setResult(...)}／{@code goods.setTradeEnd(...)}</h2>
 * <p>{@code DEC-DB-11}、{@code 9-H}、风险 R8：当前表的 {@code result} <b>恒为 NULL</b>，
 * {@code off_sale}／{@code trade_end}／{@code result} <b>只写在历史表</b>上（由
 * {@link ArchiveService} 完成）。本类只写 {@code status}／{@code freezeBy}／{@code tradeStart}。
 *
 * <h2>⚠️ 流水只在「标记交易结果」时追加（§9.7.2）</h2>
 * <p>{@code enterTrade} <b>不</b>追加流水：{@code simpleshop_trade_history} 的
 * {@code trade_start} 与 {@code trade_end} <b>都是 NOT NULL</b>，进入交易时还没有
 * {@code trade_end}，插入必然失败。{@link ArchiveService} 也<b>一条都不追加</b>——
 * 它只搬迁。因此全系统的流水写入点只有本类的
 * {@link #markTradeSuccess} 与 {@link #markTradeFailure}。
 */
@Service
public class SellerIntentionService {

    private static final Logger log = LoggerFactory.getLogger(SellerIntentionService.class);

    /** 卖家写操作的事务超时（{@code DEC-DB-13}）。单位是<b>秒</b>。 */
    private static final int WRITE_TIMEOUT_SECONDS = 5;

    /** 分页默认值与上限（{@code 10-E}、§3.6）；请求侧也有注解，Service 仍<b>独立</b>校验（{@code G6-02}）。 */
    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_PAGE_SIZE = 10;
    static final int MAX_PAGE_SIZE = 100;

    /** 终态集合：用于 {@code queue_count}（与 {@code submitIntention} 的队列容量口径<b>同一个</b>）。 */
    private static final List<IntentionStatus> TERMINAL_STATUSES =
            List.of(IntentionStatus.succeeded, IntentionStatus.failed, IntentionStatus.revoked);

    private final GoodsRepository goodsRepository;
    private final IntentionRepository intentionRepository;
    private final QueueSequenceRepository queueSequenceRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final ArchiveService archiveService;
    private final OperationLogService operationLogService;
    private final DatabaseTimeProvider timeProvider;

    public SellerIntentionService(GoodsRepository goodsRepository,
                                  IntentionRepository intentionRepository,
                                  QueueSequenceRepository queueSequenceRepository,
                                  TradeHistoryRepository tradeHistoryRepository,
                                  ArchiveService archiveService,
                                  OperationLogService operationLogService,
                                  DatabaseTimeProvider timeProvider) {
        this.goodsRepository = goodsRepository;
        this.intentionRepository = intentionRepository;
        this.queueSequenceRepository = queueSequenceRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.archiveService = archiveService;
        this.operationLogService = operationLogService;
        this.timeProvider = timeProvider;
    }

    // -------------------------------------------------------------------------
    // FR-08 查看意向名单（I11-10）
    // -------------------------------------------------------------------------

    /**
     * 查当前商品的意向名单（分页）。
     *
     * <h2>⚠️ 「当前无商品」返回 {@code null}，而不是 {@code 20002}（已定 Q-2、§4.5-A）</h2>
     * <p>与 {@code I11-04} 的空态同一口径：无商品即无名单，这是一个<b>正常答案</b>
     * （前端走空态分支），不是错误。{@code 20002} 在契约里只列给
     * 「本来要求有商品、结果没有」的<b>操作类</b>接口（{@code I11-07}／{@code I11-11}）。
     *
     * <h2>⚠️ 排序必须显式传 {@code queueOrder ASC}</h2>
     * <p>仓储的 {@code findByGoodsId} <b>不内建</b>排序（约束 {@code C7}，§1.2），
     * 不传排序等于把名单顺序交给数据库的默认行为——换一个索引、换一个版本就可能变。
     *
     * <h2>⚠️ 位次的算法与「谁能有位次」</h2>
     * <p>位次 = {@code countQueuedAhead(goodsId, queueOrder) + 1}，即「排在我前面的 {@code queued} 条数 + 1」。
     * <ul>
     *   <li>{@code trading} → {@code null}（不占位次，§9.5.3、{@code I11-10} 原文）；</li>
     *   <li>其余状态<b>照算</b>。这是<b>按上游原文实现</b>的：第 11 章 §11.5 的字段表只把
     *       {@code trading} 列为 {@code null}，方案 §3.3.3 的措辞也是「对每条<b>非 trading</b>
     *       的意向」计算。⚠️ 但要注意它的语义局限：{@code revoked}／{@code failed} 等
     *       <b>已经退出队列</b>的意向，其「位次」只是「那个序号前面还有几个排队的人」，
     *       并不表示它自己还在排队（{@code §9.5.3} 的位次定义只覆盖 {@code queued}）。
     *       已作为口径观察项登记在实现过程记录 §8.11 的 O-1：<b>若上游确认终态行应为 {@code null}，
     *       改这里一行即可</b>，不需要动接口形状。</li>
     * </ul>
     *
     * <h2>性能（{@code 12-P1}）</h2>
     * <p>每页 10 条 → 最多 10 次 {@code countQueuedAhead} + 1 次分页查询 + 1 次计数。
     * 方案 §3.3.3 已论证其在 P95 ≤ 500ms 内可接受；若日后名单变大，可改为
     * 「一次性取 {@code queued} 的 {@code queue_order} 列表 + 内存排名」。
     *
     * @param page     页码（<b>从 1 起</b>）
     * @param pageSize 每页条数（1~100）
     * @return 分页名单；<b>当前无商品时为 {@code null}</b>
     * @throws BusinessException {@code 50002}——分页参数越界
     */
    @Transactional(readOnly = true)
    public IntentionPageData listIntentions(int page, int pageSize) {
        validatePaging(page, pageSize);

        String goodsId = currentGoodsIdOrNull();
        if (goodsId == null) {
            return null;
        }

        Page<Intention> found = intentionRepository.findByGoodsId(goodsId,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "queueOrder")));

        List<IntentionItemData> items = found.getContent().stream()
                .map(intention -> IntentionItemData.from(intention, rankOf(goodsId, intention)))
                .toList();

        return new IntentionPageData(
                found.getTotalElements(),
                page,
                pageSize,
                Math.toIntExact(intentionRepository.countNonTerminal(goodsId, TERMINAL_STATUSES)),
                items);
    }

    // -------------------------------------------------------------------------
    // FR-07 进入交易（I11-11）
    // -------------------------------------------------------------------------

    /**
     * 对<b>队首</b>意向发起交易（{@code PS-01} + {@code IS-02}）。
     *
     * <h2>步骤与错误码（顺序<b>不可</b>调换）</h2>
     * <ol>
     *   <li>取当前商品（非锁读，只为拿 ID）→ 空则 {@code 20002}；</li>
     *   <li>加<b>商品行锁</b>；{@code status != on_sale} → {@code 20003}；</li>
     *   <li>取队首（{@code queued} 中 {@code queue_order} 最小者）→ 无则 {@code 30002}；</li>
     *   <li>目标不是队首 → {@code 30002}（{@code BR-12}「先到先得」的<b>服务端防线</b>）；</li>
     *   <li>加<b>意向行锁</b>；不存在／状态非 {@code queued}／不属于当前商品 → {@code 30003}；</li>
     *   <li>防御性校验：已存在 {@code trading} 意向 → {@code 30004}；</li>
     *   <li>写：商品 → {@code frozen}+{@code trade}、写 {@code trade_start}；该意向 → {@code trading}。</li>
     * </ol>
     *
     * <h2>⚠️ {@code 30002} 为什么必须在服务端判</h2>
     * <p>前端只让第 1 行可点，但验收方式 {@code V-02} 要求「必须包含绕过前端的负向调用」。
     * 绕过前端直接 POST 一条非队首的 {@code intention_id}，服务端<b>必须</b>拒绝
     * （{@code AC-22}）——否则「先到先得」就只是一句 UI 上的口号。
     *
     * <h2>⚠️ {@code 30004} 是防御性校验，不是主判据</h2>
     * <p>商品行锁已经把并发串行化了（并发两次进入交易 → 恰一次成功，{@code INV-02}）。
     * 走到第 6 步还能查到 {@code trading} 意向，说明存在另一条绕过本方法的路径，属数据不一致；
     * 此时拒绝比继续更安全。故它<b>不能</b>被简化成「反正锁住了，不用查」。
     *
     * <h2>⚠️ 其余 {@code queued} 意向一律不动</h2>
     * <p>{@code BR-01}：进入交易只影响这一条意向与商品状态。若在这里把它们标记掉，
     * 「交易失败 → 重新排队」就无队可回。
     * <p>{@code goods.tradeStart} 在<b>此时</b>写入；历史展示只用 {@code trade_end}（澄清 Q22）。
     *
     * @param intentionId 目标意向 ID
     * @throws BusinessException {@code 20002}／{@code 20003}／{@code 30002}／{@code 30003}／{@code 30004}
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public void enterTrade(String intentionId) {
        Goods goods = lockCurrentGoods();
        String goodsId = goods.getId();

        if (goods.getStatus() != GoodsStatus.on_sale) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
        }

        Intention head = intentionRepository
                .findFirstByGoodsIdAndStatusOrderByQueueOrderAsc(goodsId, IntentionStatus.queued)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_QUEUE_HEAD));
        if (!head.getId().equals(intentionId)) {
            throw new BusinessException(ErrorCode.NOT_QUEUE_HEAD);
        }

        Intention target = intentionRepository.findByIdForUpdate(intentionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_STATUS_MISMATCH));
        // 说明：目标为 null/空白时走不到这里（第 4 步的「不是队首」已经拒绝），
        // 因此无需额外的入参判空——契约给它的码本来也是这两个之一。
        if (target.getStatus() != IntentionStatus.queued
                || !target.getGoods().getId().equals(goodsId)) {
            throw new BusinessException(ErrorCode.INTENTION_STATUS_MISMATCH);
        }
        if (intentionRepository.countByGoodsIdAndStatus(goodsId, IntentionStatus.trading) > 0) {
            throw new BusinessException(ErrorCode.TRADING_ALREADY_EXISTS);
        }

        LocalDateTime now = timeProvider.nowUtc();
        goods.setStatus(GoodsStatus.frozen);
        goods.setFreezeBy(FreezeBy.trade);
        goods.setTradeStart(now);
        target.setStatus(IntentionStatus.trading);

        operationLogService.log(OperationLogService.ENTER_TRADE,
                OperationLogService.TARGET_INTENTION, intentionId);
        log.info("entered trade: goodsId={} intentionId={} tradeStart={}", goodsId, intentionId, now);
    }

    // -------------------------------------------------------------------------
    // FR-09 标记交易成功（I11-12）
    // -------------------------------------------------------------------------

    /**
     * 标记交易成功（{@code PS-03} + {@code IS-04} + {@code IS-05}）。
     *
     * <h2>⚠️ 全系统影响面最大的写操作，必须整体一个事务</h2>
     * <p>一次调用同时改变「其余全部 {@code queued} 意向」「本次意向」「商品状态」三处，
     * 并追加流水、归档清表。任何一步失败都必须整体回滚——否则会留下
     * 「商品已归档、但队列里还有人排队」这种无法自愈的脏数据（{@code INV-05}）。
     *
     * <h2>⚠️ 四步的顺序<b>不可</b>调换（{@code BR-03}、{@code N-03}）</h2>
     * <ol>
     *   <li>其余 {@code queued} → {@code failed} + {@code failType = sold_out}（{@code IS-04}）；</li>
     *   <li>本次意向 → {@code succeeded}，并<b>清空</b> {@code failType}／{@code failReason}；</li>
     *   <li><b>追加本次成交流水</b>（{@code result = sold}）；</li>
     *   <li>最后才归档（商品 → 历史，当前表清空）。</li>
     * </ol>
     * <p>先他人失败、再本人成功，保证「归档时名下所有意向均已终态」（{@code INV-05} 成立、
     * 且 {@code INV-06}「至多 1 条 {@code succeeded}」同时成立）。
     * <p><b>流水必须在归档之前追加</b>：归档会 {@code deleteAll} 掉当前意向行，
     * 之后才追加的话，事务内持久化上下文已混乱，且逻辑上「流水归属的意向已不在当前表」难以自证。
     *
     * <h2>⚠️ 其余 {@code queued} 意向<b>不</b>追加流水</h2>
     * <p>{@code IS-04} 是「他人成交」的<b>连带效果</b>，不是「对该意向登记交易结果」。
     * §9.7.2 明确流水写入时机只有「标记交易成功」「标记交易失败」两处，
     * 故此处只改状态。（若给它们也补流水，会与「最后一次交易结果」的语义重复。）
     *
     * <h2>⚠️ {@code freezeBy} 必须严格等于 {@code trade}（已定 Q-1）</h2>
     * <p>手动冻结的商品「必须先解冻才能标记交易结果」，因此这里<b>不能</b>放宽成
     * 「只要 {@code frozen} 就行」。该分支实际不可达（{@code trading} 意向只可能由
     * {@code enterTrade} 产生，而它要求 {@code on_sale} 并把 {@code freezeBy} 置为 {@code trade}），
     * 属<b>防御性断言</b>：真被触发说明存在另一条把商品置为 {@code frozen(manual)}
     * 却产生 {@code trading} 意向的路径，是数据不一致，应拒绝而非继续。
     * <p>用 {@code 20004}（而非新错误码）：契约表里没有更贴切的编号，
     * 而 §11.6.1 明确「新增错误码须落在所属域内」，不得擅自加码。
     *
     * @param intentionId 处于 {@code trading} 的意向 ID
     * @throws BusinessException {@code 20002}／{@code 20004}／{@code 30003}／{@code 30005}
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public void markTradeSuccess(String intentionId) {
        Goods goods = lockCurrentGoods();
        requireFrozenByTrade(goods);
        String goodsId = goods.getId();

        Intention target = requireTradingIntention(intentionId, goodsId);
        LocalDateTime now = timeProvider.nowUtc();

        // ① 其余 queued → failed + sold_out（不追加流水，见方法注释）
        for (Intention other : intentionRepository
                .findByGoodsIdAndStatusOrderByQueueOrderAsc(goodsId, IntentionStatus.queued)) {
            other.setStatus(IntentionStatus.failed);
            other.setFailType(FailType.sold_out);
        }

        // ② 本次意向 → succeeded；终态 succeeded 没有失败语义，故清空两者
        target.setStatus(IntentionStatus.succeeded);
        target.setFailType(null);
        target.setFailReason(null);

        // ③ 追加本次成交流水。trade_start 来自商品（进入交易时写入、归档前一直保留在原行）
        tradeHistoryRepository.save(EntityFactory.newTradeHistory(
                target.getId(), goods.getTradeStart(), now, TradeResult.sold, null, null));

        // ④ 最后归档（内部会再校验一次 INV-05，失败则 30005 整体回滚）
        archiveService.archive(goods, GoodsResult.sold, now);

        operationLogService.log(OperationLogService.MARK_TRADE_SUCCESS,
                OperationLogService.TARGET_INTENTION, intentionId);
        log.info("marked trade success: goodsId={} intentionId={} tradeEnd={}", goodsId, intentionId, now);
    }

    // -------------------------------------------------------------------------
    // FR-10 标记交易失败（I11-13）
    // -------------------------------------------------------------------------

    /**
     * 标记交易失败，并裁决该意向为「作废」或「重新排队」（{@code PS-04} + {@code IS-06}／{@code IS-07}）。
     *
     * <h2>步骤（★ 处是<b>本方案最容易写错的地方</b>，风险 R3）</h2>
     * <ol>
     *   <li>取当前商品（非锁读，只为拿 ID）→ 空则 {@code 20002}；</li>
     *   <li>校验 {@code disposal} → {@code 30006}、{@code failReason} → {@code 30007}；</li>
     *   <li>★ 若 {@code disposal == requeued}：<b>先</b>取序号表行锁（在商品行锁<b>之前</b>）；</li>
     *   <li>加商品行锁；非 {@code frozen}／{@code freezeBy != trade} → {@code 20004}；</li>
     *   <li>加意向行锁；不存在／状态非 {@code trading}／不属于当前商品 → {@code 30003}；</li>
     *   <li>追加流水（{@code result = failed}，{@code failType = disposal}）；商品 → {@code on_sale}、清 {@code freezeBy}；</li>
     *   <li>裁决：作废 → {@code failed}+{@code voided}；重新排队 → 回 {@code queued}、序号刷新至队尾。</li>
     * </ol>
     *
     * <h2>★ 为什么序号表行锁必须提到商品行锁之前</h2>
     * <p>买家「提交意向」的取锁顺序是<b>先序号表、不锁商品</b>（{@code §3.2} 加锁矩阵）。
     * 若本方法在「重新排队」分支里先取商品行锁、再取序号表行锁，两条路径就构成
     * <b>反向取锁</b>，并发下必然死锁。因为 {@code disposal} 在<b>进入方法时即已知</b>，
     * 就能安全地把这把锁提前——不需要先做任何查询来判断「要不要排队」。
     * <p>⚠️ 这正解释了第 2 步为什么必须排在第 3 步之前：若先按「不是 requeued」跳过序号表锁，
     * 之后再发现非法值，锁的顺序就已经错了。
     *
     * <h2>⚠️ 「重新排队」不产生终态，口令码保持有效（{@code BR-27}、{@code INV-08}）</h2>
     * <p>意向从 {@code trading} <b>直接</b>回到 {@code queued}，不经过任何终态——
     * 这是「静默买家」问题（{@code GAP-01}）的消解方式：买家手里的口令码还有效，
     * 他查到的位次会变，而不是突然被告知「已失效」。
     * <p>同时：{@code queue_order} 刷新至队尾、{@code create_at} <b>不变</b>（{@code BR-20}、{@code DEC-15}）、
     * {@code token} <b>不动</b>。
     *
     * <h2>⚠️ {@code failType} 在重排队后<b>保留</b> {@code requeued}（{@code DEC-DB-10} ①）</h2>
     * <p>非终态意向上的 {@code failType} 语义是「<b>最近一次</b>交易失败的处置方式」的快照，
     * 不只是「终态时的失败原因」。<b>不要</b>把它清成 {@code null}——
     * 那会让流水表里「最后一次 {@code result = failed}、{@code fail_type = requeued}」
     * 与意向表的 {@code queued} + 空 {@code failType} 对不上（§9.7.2 的一致性要求）。
     * <p>{@code failReason} 同样保留本次备注。
     *
     * <h2>⚠️ 商品的 {@code tradeStart} <b>不清</b></h2>
     * <p>它是「本次交易开始时间」，下次进入交易会覆盖它；历史展示只用 {@code trade_end}（澄清 Q22）。
     *
     * @param intentionId 处于 {@code trading} 的意向 ID
     * @param disposal    裁决方式：{@code voided}（作废）／{@code requeued}（重新排队）
     * @param failReason  失败备注，可空（≤300 字符，买家不可见）
     * @throws BusinessException {@code 20002}／{@code 20004}／{@code 30003}／{@code 30006}／{@code 30007}／{@code 50000}
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public void markTradeFailure(String intentionId, String disposal, String failReason) {
        // 第 1 步：当前商品（这一步与协议无关，先确认「有没有商品可操作」）
        String goodsId = currentGoodsId();

        // 第 2 步：字段校验。必须在【第一次取锁之前】完成，否则非法 disposal 会走错分支（见方法注释 ★）。
        FailType disposalType = TradeFailureValidator.parseDisposal(disposal);
        String reason = TradeFailureValidator.normalizeFailReason(failReason);

        // 第 3 步 ★：重新排队需要分配新序号，序号表行锁必须先于商品行锁取得
        QueueSequence sequence = null;
        if (disposalType == FailType.requeued) {
            sequence = queueSequenceRepository.findByIdForUpdate(QueueSequence.FIXED_ID)
                    // 序号表未初始化 = 部署错误（V2 会幂等写入该行），不是业务拒绝
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        }

        Goods goods = goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_CURRENT_GOODS));
        requireFrozenByTrade(goods);

        Intention target = requireTradingIntention(intentionId, goodsId);
        LocalDateTime now = timeProvider.nowUtc();

        // 追加流水：无论作废还是重排队都记一条（9-D、§9.7.2）
        tradeHistoryRepository.save(EntityFactory.newTradeHistory(
                target.getId(), goods.getTradeStart(), now, TradeResult.failed, disposalType, reason));

        // 商品恢复在售（PS-04），freezeBy 清空（与 unfreezeGoods 一致）；
        // tradeStart 保留不清（见方法注释）
        goods.setStatus(GoodsStatus.on_sale);
        goods.setFreezeBy(null);

        if (disposalType == FailType.voided) {
            target.setStatus(IntentionStatus.failed);
            target.setFailType(FailType.voided);
            target.setFailReason(reason);
        } else {
            // 从 trading 直接回 queued，不经过任何终态（BR-27）
            long next = sequence.getCurrentValue() + 1;
            sequence.setCurrentValue(next);
            target.setQueueOrder(Math.toIntExact(next));
            target.setStatus(IntentionStatus.queued);
            // failType 保留 requeued；createAt 不动；token 不动（口令码仍有效）
            target.setFailType(FailType.requeued);
            target.setFailReason(reason);
        }

        // ⚠️ 一次「标记失败」记【两条】审计记录，这是刻意的、不是重复记录（见 OperationLogService 的说明）：
        //    ① MARK_TRADE_FAILURE  → NFR-12 第 5 类「标记交易结果」（成功侧由 MARK_TRADE_SUCCESS 承担）
        //    ② DISPOSAL_VOIDED/REQUEUED → NFR-12 第 6 类「作废／重新排队」，单独成码以便按裁决方式统计
        //    S9 之前只记了 ②，于是常量 MARK_TRADE_FAILURE 一直没有调用方（死代码）——
        //    而「8 类各有记录」正是 NFR-12 的验收口径，故补上 ①。
        operationLogService.log(OperationLogService.MARK_TRADE_FAILURE,
                OperationLogService.TARGET_INTENTION, intentionId,
                "disposal=" + disposalType.name());
        operationLogService.log(
                disposalType == FailType.voided
                        ? OperationLogService.DISPOSAL_VOIDED
                        : OperationLogService.DISPOSAL_REQUEUED,
                OperationLogService.TARGET_INTENTION, intentionId);
        log.info("marked trade failure: goodsId={} intentionId={} disposal={} tradeEnd={}",
                goodsId, intentionId, disposalType, now);
    }

    // -------------------------------------------------------------------------
    // FR-24 查看意向口令码（I11-14）
    // -------------------------------------------------------------------------

    /**
     * 取某意向的口令码<b>原文</b>（{@code FR-24}）。
     *
     * <h2>只对<b>非终态</b>意向开放（{@code NFR-16}、{@code BR-26}）</h2>
     * <p>终态（{@code succeeded}／{@code failed}／{@code revoked}）→ {@code 40002}，<b>不展示</b>。
     * 判定式用 {@link IntentionStatus#isTokenValid()}，即 {@code !isTerminal()}——
     * 这是口令码有效性的<b>唯一</b>判定式，<b>不得</b>掺入商品状态
     * （{@code BR-26}、{@code INV-07}：商品在售↔冻结变化前后，同一口令码的结果必须不变）。
     *
     * <h2>⚠️ 只读路径，<b>不加锁</b></h2>
     * <p>读到之后即使该意向并发变成终态，本次返回的仍是一个「刚刚还有效」的码——
     * 这与「先返回后失效」的正常时序不可区分，没有正确性问题，故不需要行锁。
     *
     * <h2>⚠️ 记操作日志，但<b>绝不</b>记口令码（{@code NFR-12} 第 7 类、{@code 12-H}）</h2>
     * <p>「查看口令码」是 {@code NFR-12} 明列的 8 类关键操作之一（已定 Q-4/Q-9：应当记录）。
     * 记录的内容是「谁在何时查看了<b>哪条意向</b>」——{@link OperationLogService#log} 的
     * 第三个参数收的是<b>意向 ID</b>，从签名上就没有放码值的地方。
     *
     * @param intentionId 目标意向 ID
     * @return 口令码原文（12 位 {@code [A-Z0-9]}）
     * @throws BusinessException {@code 30008}（意向不存在）、{@code 40002}（已终态，口令码失效）
     */
    @Transactional(readOnly = true)
    public String getPasscode(String intentionId) {
        Intention intention = intentionRepository.findById(intentionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_NOT_FOUND));
        if (!intention.getStatus().isTokenValid()) {
            throw new BusinessException(ErrorCode.PASSCODE_EXPIRED);
        }

        // ⚠️ 只传意向 ID，不传 token（见方法注释）
        operationLogService.log(OperationLogService.VIEW_PASSCODE,
                OperationLogService.TARGET_INTENTION, intentionId);
        return intention.getToken();
    }

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    /**
     * 取当前商品 ID（<b>非锁读</b>）。
     *
     * <p>为什么分两步（先查 ID、再按 ID 加锁）：仓储只有
     * {@code findFirstByOrderByCreateAtAsc()}（无锁）与 {@code findByIdForUpdate(id)}（加锁），
     * 没有「取首行并加锁」的组合方法，而附录 B 不允许改动仓储接口。
     * 两步之间的窗口不会造成问题：表内至多 1 行，且<b>第二次读到的状态才是判定依据</b>。
     *
     * @throws BusinessException {@code 20002}——当前没有商品
     */
    private String currentGoodsId() {
        String goodsId = currentGoodsIdOrNull();
        if (goodsId == null) {
            throw new BusinessException(ErrorCode.NO_CURRENT_GOODS);
        }
        return goodsId;
    }

    /** 取当前商品 ID，无商品时返回 {@code null}（供 {@code I11-10} 的空态分支使用）。 */
    private String currentGoodsIdOrNull() {
        return goodsRepository.findFirstByOrderByCreateAtAsc().map(Goods::getId).orElse(null);
    }

    /**
     * 取当前商品并<b>加行锁</b>（第一把锁为商品行）。
     *
     * @throws BusinessException {@code 20002}——当前没有商品（含并发下架刚好把行删掉的窗口）
     */
    private Goods lockCurrentGoods() {
        String goodsId = currentGoodsId();
        return goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_CURRENT_GOODS));
    }

    /**
     * 校验商品处于「交易冻结」——两个标记结果方法的<b>共同前置</b>（已定 Q-1）。
     *
     * <p>必须是 {@code frozen} <b>且</b> {@code freezeBy == trade}：
     * 手动冻结的商品要先解冻才能标记交易结果，放宽任何一半都会让
     * 「手动冻结期间也能把某笔交易标记成功」成为可能。
     *
     * @throws BusinessException {@code 20004}——非已冻结，或冻结来源不是交易
     */
    private void requireFrozenByTrade(Goods goods) {
        if (goods.getStatus() != GoodsStatus.frozen || goods.getFreezeBy() != FreezeBy.trade) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FROZEN);
        }
    }

    /**
     * 取目标意向并加行锁，同时校验它是 {@code trading}、且属于当前商品。
     *
     * <p>「属于当前商品」这条不能省：{@code intention_id} 是客户端给的，
     * 拿另一件商品（或历史遗留）的意向 ID 来标记结果，会把两个商品的状态搅在一起。
     *
     * @throws BusinessException {@code 30003}——不存在、状态非 {@code trading}、或不属于当前商品
     */
    private Intention requireTradingIntention(String intentionId, String goodsId) {
        Intention intention = intentionRepository.findByIdForUpdate(intentionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTENTION_STATUS_MISMATCH));
        if (intention.getStatus() != IntentionStatus.trading
                || !intention.getGoods().getId().equals(goodsId)) {
            throw new BusinessException(ErrorCode.INTENTION_STATUS_MISMATCH);
        }
        return intention;
    }

    /**
     * 计算某意向在名单里的位次（见 {@link #listIntentions} 的说明）。
     *
     * @return {@code trading} 时为 {@code null}（不占位次）；其余为「前面还有几个 {@code queued}」+ 1
     */
    private Integer rankOf(String goodsId, Intention intention) {
        if (intention.getStatus() == IntentionStatus.trading) {
            return null;
        }
        return Math.toIntExact(intentionRepository.countQueuedAhead(goodsId, intention.getQueueOrder()) + 1);
    }

    /**
     * 分页参数校验（{@code §3.6}：{@code page ≥ 1}、{@code 1 ≤ page_size ≤ 100}）。
     *
     * <p>Controller 上也有 {@code @Min}／{@code @Max} 注解，两处都写是<b>刻意</b>的、且不构成双源：
     * <ul>
     *   <li>注解只覆盖 Controller 入参（HTTP 路径）；本方法也被买家端/测试<b>直接调用</b>，
     *       不过 DTO 校验链（{@code G6-02}）；</li>
     *   <li>两者返回<b>同一个</b>码 {@code 50002}——这正是「DTO 注解只判有没有、取值规则归 Service」
     *       那条硬规则（§4.6.3）能允许注解保留的原因：{@code page}／{@code page_size}
     *       <b>没有</b>专属业务码，所以两处口径一致，不会出现「注解拦成 {@code 50002}、
     *       Service 想说 {@code 20007}」的分裂。</li>
     * </ul>
     *
     * @throws BusinessException {@code 50002}（HTTP 400）——分页参数越界
     */
    private void validatePaging(int page, int pageSize) {
        if (page < DEFAULT_PAGE || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }
}
