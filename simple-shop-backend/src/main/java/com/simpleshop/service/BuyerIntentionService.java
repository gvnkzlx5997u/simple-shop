package com.simpleshop.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.config.AppProperties;
import com.simpleshop.persistence.entity.EntityFactory;
import com.simpleshop.persistence.entity.Goods;
import com.simpleshop.persistence.entity.Intention;
import com.simpleshop.persistence.entity.QueueSequence;
import com.simpleshop.persistence.enums.FailType;
import com.simpleshop.persistence.enums.GoodsStatus;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.repository.GoodsRepository;
import com.simpleshop.persistence.repository.IntentionRepository;
import com.simpleshop.persistence.repository.QueueSequenceRepository;
import com.simpleshop.persistence.time.DatabaseTimeProvider;
import com.simpleshop.service.dto.BuyerPrompt;
import com.simpleshop.service.dto.BuyerResult;
import com.simpleshop.service.dto.GoodsData;
import com.simpleshop.service.dto.PasscodeQueryResult;
import com.simpleshop.service.dto.SubmitResultData;
import com.simpleshop.service.support.IntentionValidator;

/**
 * 买家端意向服务（{@code FR-12} ~ {@code FR-22}）。<b>无 Controller</b>——买家端为服务端渲染的 JSP
 * （下一轮），直接调用本 Service，不经过 HTTP 接口层（方案 §0.3、§2.3）。
 *
 * <pre>
 * GoodsData                        browseCurrentGoods();                                  // FR-12/FR-19
 * BuyerResult&lt;SubmitResultData&gt;    submitIntention(name, tel, clientKey);                // FR-13/FR-14
 * PasscodeQueryResult              queryByToken(rawToken);                              // FR-15/FR-22
 * BuyerResult&lt;Void&gt;                updateIntentionContact(rawToken, newName, newTel);  // FR-16
 * BuyerResult&lt;Void&gt;                revokeIntention(rawToken);                           // FR-17
 * </pre>
 *
 * <h2>⚠️⚠️ 本类最重要的实现约束：所有拒绝分支必须早于任何写操作</h2>
 * <p>本类<b>不用异常</b>表达业务拒绝，而是返回 {@link BuyerResult.Rejected}（理由见
 * {@link BuyerResult} 的类注释）。后果是：<b>返回拒绝是正常返回，事务会正常提交，不会回滚</b>——
 * 与卖家端「异常即回滚」（§3.1）<b>语义相反</b>。
 * <p>因此每个写方法的形状都是固定的两段：
 * <pre>
 * 【判定段】校验 → 前置条件 → 加锁后复查…… 任何一步不满足就 return Rejected
 * ---- 以下不再出现任何 return Rejected ----
 * 【写阶段】分配序号 → 写库
 * </pre>
 * <p>三个写方法（{@code submitIntention}／{@code updateIntentionContact}／{@code revokeIntention}）
 * 都遵守这个形状，且各自在代码里标注了「写阶段起点」。<b>若将来有人在写阶段之后加拒绝分支，
 * 那次写会被提交</b>——这是本类最需要评审关注的一点。
 *
 * <h2>加锁顺序（§3.2／§8.3）</h2>
 * <p>统一顺序为 <b>① 序号表行 → ② 商品行 → ③ 意向行</b>，本类与之相符：
 * <ul>
 *   <li>{@code submitIntention}：只锁 <b>① 序号表行</b>，<b>不锁商品</b>（交接说明 §5.2）；
 *       商品状态用普通读判定——那正是 §3.2 加锁矩阵里写的口径；</li>
 *   <li>{@code updateIntentionContact}／{@code revokeIntention}：只锁 <b>③ 意向行</b>，
 *       <b>不锁商品</b>（这两件事不该被商品行锁串行化）。</li>
 * </ul>
 * <p>由于本类从不「先锁②再锁①」，与卖家端（先锁②商品）不会形成反向获取，故不死锁。
 *
 * <h2>⚠️ 本类<b>不</b>提供买家侧的「作废」「重新排队」</h2>
 * <p>{@code BR-18}、{@code O-08}：那两个动作只属于卖家（{@code I11-13} 的 {@code disposal}）。
 * 买家能做的只有<b>撤销</b>（{@code IS-03}，且仅 {@code queued}）。
 */
@Service
public class BuyerIntentionService {

    private static final Logger log = LoggerFactory.getLogger(BuyerIntentionService.class);

    /** 卖家写操作的事务超时（{@code DEC-DB-13}）。单位是<b>秒</b>。 */
    private static final int WRITE_TIMEOUT_SECONDS = 5;

    /**
     * 终态集合，用于队列容量的计数口径（{@code NFR-07} ①：计数 = <b>非终态</b>意向数）。
     *
     * <p>必须<b>恰好</b>是 {@code isTerminal() == true} 的三个。多一个或少一个都会让
     * 「1000 条上限」提前或延后生效，而 {@code AC-18} 会逐条核对。
     */
    private static final List<IntentionStatus> TERMINAL_STATUSES =
            List.of(IntentionStatus.succeeded, IntentionStatus.failed, IntentionStatus.revoked);

    private final GoodsRepository goodsRepository;
    private final IntentionRepository intentionRepository;
    private final QueueSequenceRepository queueSequenceRepository;
    private final PasscodeService passcodeService;
    private final SubmitRateLimiter submitRateLimiter;
    private final DatabaseTimeProvider timeProvider;
    private final int queueMaxSize;

    public BuyerIntentionService(GoodsRepository goodsRepository,
                                IntentionRepository intentionRepository,
                                QueueSequenceRepository queueSequenceRepository,
                                PasscodeService passcodeService,
                                SubmitRateLimiter submitRateLimiter,
                                DatabaseTimeProvider timeProvider,
                                AppProperties properties) {
        this.goodsRepository = goodsRepository;
        this.intentionRepository = intentionRepository;
        this.queueSequenceRepository = queueSequenceRepository;
        this.passcodeService = passcodeService;
        this.submitRateLimiter = submitRateLimiter;
        this.timeProvider = timeProvider;
        this.queueMaxSize = properties.getQueue().getMaxSize();
    }

    // -------------------------------------------------------------------------
    // FR-12 / FR-19 浏览当前商品
    // -------------------------------------------------------------------------

    /**
     * 取当前商品供买家首页展示（{@code P10-01}）。
     *
     * <h2>⚠️ 与卖家端 {@code I11-04} 的两个区别</h2>
     * <ol>
     *   <li><b>不区分状态</b>：{@code frozen} 的商品照样返回——页面需要展示卡片 + 「已冻结」标签
     *       + 提示 {@code M10-01}（第 10 章 {@code P10-01} 的状态表），
     *       并据此禁用提交入口。若这里把冻结商品过滤掉，买家会看到「暂无在售商品」，
     *       与「商品交易中，暂停接收新意向」的含义完全不同。</li>
     *   <li><b>无商品返回 {@code null}</b>：空态不是错误分支（与 {@code I11-04} 一致），
     *       由调用方渲染 {@link BuyerPrompt#NO_CURRENT_GOODS}（{@code M10-02}）。</li>
     * </ol>
     * <p>复用卖家的 {@link GoodsData} 而非另建 DTO：契约字段相同，多一套形状只会多一处要同步的地方。
     * 其中的 {@code trade_start} 是「只记录不展示」的内部字段，页面不渲染即可。
     *
     * @return 当前商品；表内无行时返回 {@code null}
     */
    @Transactional(readOnly = true)
    public GoodsData browseCurrentGoods() {
        return goodsRepository.findFirstByOrderByCreateAtAsc()
                .map(GoodsData::from)
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // FR-13 / FR-14 提交意向（IS-01）
    // -------------------------------------------------------------------------

    /**
     * 提交购买意向并生成口令码。
     *
     * <h2>步骤与拒绝码（§3.3.5）</h2>
     * <ol>
     *   <li><b>限流</b>（内存，最前）→ {@link BuyerPrompt#TOO_MANY_REQUESTS}（{@code NFR-17}）</li>
     *   <li>姓名／电话校验 → {@code NAME_TEL_REQUIRED}／{@code NAME_INVALID}／{@code TEL_INVALID}
     *       （{@code M10-11}／{@code M10-36}／{@code M10-37}）</li>
     *   <li>商品必须存在且<b>在售</b>：冻结（手动或交易）→ {@code GOODS_FROZEN}（{@code M10-01}、
     *       {@code AC-17}）；无商品或非在售 → {@code GOODS_UNAVAILABLE}（{@code M10-13}）</li>
     *   <li>锁序号表行（<b>① 第一把锁</b>），随后<b>在持锁状态下</b>数队列</li>
     *   <li>非终态计数 ≥ {@code queue.max-size}（1000）→ {@code QUEUE_FULL}（{@code M10-12}、{@code AC-18}）</li>
     *   <li>写阶段：序号 +1、生成口令码、插入意向</li>
     * </ol>
     *
     * <h2>⚠️ 三处容易写错的地方</h2>
     * <ul>
     *   <li><b>队列计数必须在序号表行锁之后</b>（§3.3.5 明确）。否则并发提交会各自读到 999
     *       而双双插入，突破 1000 上限。</li>
     *   <li><b>绝不修改 {@code goods.status}</b>（{@code BR-13}：提交意向 ≠ 冻结商品）。
     *       提交只新增一行意向，商品状态一动不动。</li>
     *   <li><b>{@code createAt} 显式赋值</b>（§3.4 末的全项目口径）：不依赖
     *       {@code @PrePersist}，让时间来源在代码里看得见；实体回调仍作为兜底保留。</li>
     * </ul>
     *
     * <h2>⚠️ 「冻结期一律拒绝」覆盖两种冻结来源</h2>
     * <p>{@code INV-03}、{@code BR-02}、{@code BR-14}，并被 {@code AC-17}（{@code P0}）点名为
     * 「必须绕过前端直接 POST 也仍被拒」的验收项。故本方法是<b>服务端防线</b>，
     * 不依赖页面把提交按钮禁用。
     *
     * @param rawName   买家姓名（原样输入）
     * @param rawTel    联系电话（原样输入）
     * @param clientKey 客户端标识，用于限流（本轮为请求来源 IP；{@code null} 时放行并告警）
     * @return 成功时携带 {@code {token, intention_id}}；被拒时携带提示码
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public BuyerResult<SubmitResultData> submitIntention(String rawName, String rawTel, String clientKey) {
        // ---------- 判定段（以下任何一步都不写库）----------

        // ① 限流：内存判定，必须先于一切 DB 操作（否则超限请求会去抢序号表行锁）
        if (!submitRateLimiter.tryAcquire(clientKey)) {
            return BuyerResult.rejected(BuyerPrompt.TOO_MANY_REQUESTS);
        }

        // ② 字段校验（先收集，再按顺序报第一个问题）
        BuyerResult<String> nameCheck = IntentionValidator.normalizeName(rawName);
        BuyerResult<String> telCheck = IntentionValidator.normalizeTel(rawTel);
        BuyerPrompt fieldIssue = firstRejection(nameCheck, telCheck);
        if (fieldIssue != null) {
            return BuyerResult.rejected(fieldIssue);
        }

        // ③ 商品必须存在且在售
        Goods goods = goodsRepository.findFirstByOrderByCreateAtAsc().orElse(null);
        if (goods == null) {
            return BuyerResult.rejected(BuyerPrompt.GOODS_UNAVAILABLE);
        }
        if (goods.getStatus() == GoodsStatus.frozen) {
            // AC-17：手动冻结与交易冻结【一律】拒绝，且用同一个码与文案
            return BuyerResult.rejected(BuyerPrompt.GOODS_FROZEN);
        }
        if (goods.getStatus() != GoodsStatus.on_sale) {
            // 理论上当前表不落 off_sale（9-H）；真出现时按「状态已变化」处理，而不是当成冻结
            return BuyerResult.rejected(BuyerPrompt.GOODS_UNAVAILABLE);
        }

        // ④ 序号表行锁（① 第一把锁）。本方法不锁商品行（§3.2 加锁矩阵）
        QueueSequence sequence = queueSequenceRepository.findByIdForUpdate(QueueSequence.FIXED_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "queue sequence row (id=" + QueueSequence.FIXED_ID + ") is missing; "
                                + "V2__seed_initial_data.sql must have inserted it"));

        // ⑤ 队列上限：必须在【持锁之后】计数，否则并发下会突破上限
        long nonTerminal = intentionRepository.countNonTerminal(goods.getId(), TERMINAL_STATUSES);
        if (nonTerminal >= queueMaxSize) {
            return BuyerResult.rejected(BuyerPrompt.QUEUE_FULL);
        }

        // ---------- 写阶段起点（以下不再有任何拒绝分支）----------

        // ⚠️ 两处类型不同：QueueSequence.currentValue 是 Long（列 BIGINT），
        //    而 Intention.queueOrder 是 Integer（列 int）。用 Math.toIntExact 显式收窄，
        //    溢出时直接抛而不是静默截断——静默截断会产生负数序号，撞 uk_intention_queue_order
        //    或让位次计算彻底错乱。实际不可能溢出（队列上限 1000）。
        int nextQueueOrder = Math.toIntExact(sequence.getCurrentValue() + 1);
        sequence.setCurrentValue((long) nextQueueOrder);

        String token = passcodeService.generateUnique();
        Intention intention = EntityFactory.newIntention(
                goods, nextQueueOrder, nameCheck.orElseNull(), telCheck.orElseNull(), token);
        // §3.4 末：业务时间一律由 Service 显式写，不依赖 @PrePersist
        intention.setCreateAt(timeProvider.nowUtc());
        intentionRepository.save(intention);

        // ⚠️ 绝不写 goods.status（BR-13：提交意向不等于冻结商品）
        // ⚠️ 口令码不进日志（12-H）：只记意向 ID 与序号
        log.info("intention submitted: intentionId={} goodsId={} queueOrder={}",
                intention.getId(), goods.getId(), nextQueueOrder);
        return BuyerResult.ok(SubmitResultData.of(token, intention.getId()));
    }

    // -------------------------------------------------------------------------
    // FR-15 / FR-22 凭口令码查询（三态）
    // -------------------------------------------------------------------------

    /**
     * 凭口令码查询意向状态（三态）。
     *
     * <h2>判定式（§3.3.5、{@code BR-26}、{@code INV-07}）</h2>
     * <pre>
     * 归一化 → findByToken
     *   查不到            → NotFound                 （B11-04 / M10-05）
     *   status 为终态     → Expired                  （B11-05 / M10-06）⚠️ 不说明原因、不带任何信息
     *   queued            → Active(位次, false)      （B11-06 / M10-07）
     *   trading           → Active(null, true)       （B11-07 / M10-08）
     * </pre>
     *
     * <h2>⚠️ 判定<b>只</b>看意向状态，绝不掺入商品状态</h2>
     * <p>{@code BR-26}、{@code INV-07}：商品在 {@code on_sale ⇄ frozen} 之间变化时，
     * <b>同一口令码的返回结果必须不变</b>。因此本方法<b>不</b>查询商品
     * （除了经由位次统计间接用到 {@code goodsId}，那与商品状态无关）。
     *
     * <h2>⚠️ 格式不符（{@code B11-09}／{@code M10-28}）由调用方判定</h2>
     * <p>§3.3.5 明确：「格式前置校验…由调用方先调 {@code PasscodeService.isWellFormed} 判定，
     * <b>不进入本方法</b>」——目的是让「明显不是 12 位」的输入根本不发起查询。
     * <p>因此本方法对格式不符的输入会<b>退化</b>为 {@link PasscodeQueryResult.NotFound}
     * （查不到），这是<b>安全</b>的降级（不泄漏任何信息），但<b>文案不同</b>
     * （{@code M10-05} 而非 {@code M10-28}）。下一轮 JSP 必须先判格式，
     * 已登记为实现过程记录里的「下一轮待办」。
     *
     * @param rawToken 用户原样输入的口令码
     * @return 三态结果，见 {@link PasscodeQueryResult}
     */
    @Transactional(readOnly = true)
    public PasscodeQueryResult queryByToken(String rawToken) {
        String token = PasscodeService.normalize(rawToken);
        if (token == null || token.isEmpty()) {
            return new PasscodeQueryResult.NotFound();
        }
        Intention intention = intentionRepository.findByToken(token).orElse(null);
        if (intention == null) {
            return new PasscodeQueryResult.NotFound();
        }
        // isTokenValid() == !isTerminal()，是【唯一】判定式（IntentionStatus 的类注释）
        if (!intention.getStatus().isTokenValid()) {
            return new PasscodeQueryResult.Expired();
        }
        if (intention.getStatus() == IntentionStatus.trading) {
            // trading 不占位次，故 rank 必须为 null（Active 的紧凑构造器会强制这一点）
            return new PasscodeQueryResult.Active(null, true);
        }
        long ahead = intentionRepository.countQueuedAhead(
                intention.getGoods().getId(), intention.getQueueOrder());
        return new PasscodeQueryResult.Active((int) (ahead + 1), false);
    }

    // -------------------------------------------------------------------------
    // FR-16 修改姓名／电话
    // -------------------------------------------------------------------------

    /**
     * 凭口令码修改姓名／电话（{@code B11-06} 页面的「修改信息」）。
     *
     * <h2>⚠️ 三项「不许动」</h2>
     * <ul>
     *   <li><b>不动 {@code queueOrder}</b>：修改信息<b>不影响位次</b>（{@code FR-16} 注、{@code M10-10}）；</li>
     *   <li><b>不动 {@code createAt}</b>：那是「原始提交时间」，重排队与改信息都不得变更
     *       （{@code BR-20}、{@code DEC-15}）；</li>
     *   <li><b>不动 {@code status}／{@code token}</b>：改信息与交易状态、口令码无关。</li>
     * </ul>
     *
     * <h2>⚠️ {@code null} 与空串的含义<b>不同</b>（调用方必须分清）</h2>
     * <ul>
     *   <li>传 {@code null} = <b>该项不改</b>（§3.3.5「两项均可独立修改」）；两项都传 {@code null}
     *       时按<b>无操作成功</b>返回，而不是报错——因为「不改」本身是合法请求。</li>
     *   <li>传 {@code ""}／全空白 = <b>用户把该项清空了</b> → 字段校验失败
     *       → {@link BuyerPrompt#NAME_TEL_REQUIRED}。表单里的空输入框提交的是空串而非 {@code null}，
     *       所以真实表单走的是这一支。</li>
     * </ul>
     *
     * <h2>⚠️ 步骤顺序按 §3.3.5：先验口令码，再验字段</h2>
     * <p>即先「锁意向行 + 判口令码有效」，再做字段校验。这与「先做便宜的校验」的直觉相反，
     * 但符合 §3.1「先锁后读，再校验，最后写」的总原则，也符合 §3.3.5 的既定步骤。
     *
     * @param rawToken 口令码（原样输入）
     * @param newName  新姓名；{@code null} 表示不改
     * @param newTel   新电话；{@code null} 表示不改
     * @return 成功（含无操作）或被拒
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public BuyerResult<Void> updateIntentionContact(String rawToken, String newName, String newTel) {
        // ---------- 判定段 ----------

        // ① 锁意向行（③ 第三把锁；本方法不锁商品）
        Intention intention = lockByToken(rawToken);
        if (intention == null) {
            return BuyerResult.rejected(BuyerPrompt.PASSCODE_NOT_FOUND);
        }
        // ② 终态即口令码失效，拒绝修改（BR-31）
        if (!intention.getStatus().isTokenValid()) {
            return BuyerResult.rejected(BuyerPrompt.PASSCODE_EXPIRED);
        }
        // ③ 字段校验（只验要改的那些）
        BuyerResult<String> nameCheck = newName == null ? null : IntentionValidator.normalizeName(newName);
        BuyerResult<String> telCheck = newTel == null ? null : IntentionValidator.normalizeTel(newTel);
        BuyerPrompt fieldIssue = firstRejection(nameCheck, telCheck);
        if (fieldIssue != null) {
            return BuyerResult.rejected(fieldIssue);
        }
        // ④ 两项都没传 = 无操作成功（§3.3.5：null 表示该项不改）
        if (nameCheck == null && telCheck == null) {
            return BuyerResult.ok();
        }

        // ---------- 写阶段起点 ----------

        if (nameCheck != null) {
            intention.setName(nameCheck.orElseNull());
        }
        if (telCheck != null) {
            intention.setTel(telCheck.orElseNull());
        }
        // ⚠️ 不动 queueOrder（位次不变）、createAt（BR-20）、status、token
        log.info("intention contact updated: intentionId={} nameChanged={} telChanged={}",
                intention.getId(), nameCheck != null, telCheck != null);
        return BuyerResult.ok();
    }

    // -------------------------------------------------------------------------
    // FR-17 撤销意向（IS-03）
    // -------------------------------------------------------------------------

    /**
     * 凭口令码撤销意向（{@code IS-03}）。
     *
     * <h2>规则</h2>
     * <ul>
     *   <li>口令码不存在 → {@link BuyerPrompt#PASSCODE_NOT_FOUND}</li>
     *   <li>终态 → {@link BuyerPrompt#PASSCODE_EXPIRED}（已失效，撤销无从谈起）</li>
     *   <li>{@code trading} → {@link BuyerPrompt#REVOKE_NOT_ALLOWED}（{@code M10-14}、{@code BR-19}、{@code O-09}）</li>
     *   <li>{@code queued} → 置 {@code revoked}、{@code failType = revoked}</li>
     * </ul>
     *
     * <h2>⚠️ 位次<b>不需要</b>「前移」</h2>
     * <p>位次是<b>计数派生</b>的（{@code countQueuedAhead}），撤销后下一位自动变成第 1 位——
     * 没有任何「前移 SQL」要写（{@code BR-19}、§7.3「无需额外方法」）。
     * <p>若有人在这里加一句「把后面的人 queue_order 减一」，反而会破坏
     * {@code uk_intention_queue_order}（唯一约束）并制造并发写冲突。
     *
     * <h2>⚠️ 买家侧只有「撤销」，没有「作废」「重新排队」</h2>
     * <p>{@code BR-18}、{@code O-08}：那两个是卖家的裁决动作（{@code I11-13}）。
     *
     * @param rawToken 口令码（原样输入）
     * @return 成功或被拒
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public BuyerResult<Void> revokeIntention(String rawToken) {
        // ---------- 判定段 ----------

        Intention intention = lockByToken(rawToken);
        if (intention == null) {
            return BuyerResult.rejected(BuyerPrompt.PASSCODE_NOT_FOUND);
        }
        if (!intention.getStatus().isTokenValid()) {
            return BuyerResult.rejected(BuyerPrompt.PASSCODE_EXPIRED);
        }
        // isRevocable() 仅 queued 为 true（IntentionStatus 的口径），故 trading 落到这里
        if (!intention.getStatus().isRevocable()) {
            return BuyerResult.rejected(BuyerPrompt.REVOKE_NOT_ALLOWED);
        }

        // ---------- 写阶段起点 ----------

        intention.setStatus(IntentionStatus.revoked);
        // 买家撤销是系统自动产生的失败类型（FailType.revoked，isAuto = true）
        intention.setFailType(FailType.revoked);
        intention.setFailReason(null);
        log.info("intention revoked by buyer: intentionId={}", intention.getId());
        return BuyerResult.ok();
    }

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    /**
     * 归一化口令码并按口令码<b>加锁</b>读取意向（③ 意向行锁）。
     *
     * @param rawToken 原样输入
     * @return 意向；口令码为空或查不到时返回 {@code null}
     */
    private Intention lockByToken(String rawToken) {
        String token = PasscodeService.normalize(rawToken);
        if (token == null || token.isEmpty()) {
            return null;
        }
        // ⚠️ 必须在事务内调用（findByTokenForUpdate 是 SELECT ... FOR UPDATE）
        return intentionRepository.findByTokenForUpdate(token).orElse(null);
    }

    /**
     * 按参数顺序返回<b>第一个</b>被拒的提示码；全部通过（或该项未参与校验）时返回 {@code null}。
     *
     * <p>用「收集后按序取第一个」而不是「边校验边返回」，是为了让「姓名与电话都不合法时报哪个」
     * 由参数顺序<b>显式决定</b>（姓名的提示在前），而不是由代码里 if 的书写顺序隐式决定。
     *
     * @param checks 校验结果；元素可以是 {@code null}（表示该项未参与校验，如改信息时只传了一项）
     * @return 第一个提示码，或 {@code null}
     */
    private static BuyerPrompt firstRejection(BuyerResult<?>... checks) {
        for (BuyerResult<?> check : checks) {
            if (check != null && check instanceof BuyerResult.Rejected<?> rejected) {
                return rejected.prompt();
            }
        }
        return null;
    }
}
