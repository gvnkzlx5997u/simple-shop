package com.simpleshop.service;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.persistence.entity.EntityFactory;
import com.simpleshop.persistence.entity.Goods;
import com.simpleshop.persistence.enums.FreezeBy;
import com.simpleshop.persistence.enums.GoodsResult;
import com.simpleshop.persistence.enums.GoodsStatus;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.repository.GoodsRepository;
import com.simpleshop.persistence.repository.IntentionRepository;
import com.simpleshop.persistence.time.DatabaseTimeProvider;
import com.simpleshop.service.dto.GoodsData;
import com.simpleshop.service.dto.PublishGoodsCommand;
import com.simpleshop.service.dto.PublishResultData;
import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;
import com.simpleshop.service.support.GoodsValidator;

/**
 * 卖家商品服务（{@code FR-03} ~ {@code FR-06}、{@code FR-23}）。方案 §3.3.2。
 *
 * <pre>
 * GoodsData         getCurrentGoods();                 // FR-04 → I11-04
 * PublishResultData publishGoods(PublishGoodsCommand); // FR-03 → I11-06
 * GoodsData         freezeGoods();                     // FR-05 → I11-07
 * GoodsData         unfreezeGoods();                   // FR-06 → I11-08
 * void              takeGoodsOffline();                // FR-23 → I11-09
 * </pre>
 *
 * <h2>「当前商品」是本类的核心概念</h2>
 * <p>商品表<b>至多 1 行</b>（{@code INV-01}、{@code BR-11}），因此没有「商品列表」，
 * 也没有任何接口带商品 ID（{@code I11-04}~{@code I11-09} 全部靠「当前商品」定位）。
 * {@link #lockCurrentGoods()} 把「取当前商品并加行锁」收敛成一处，三个状态变更方法共用。
 *
 * <h2>加锁顺序（{@code §3.2}）</h2>
 * <p>本类的写方法第一把锁都是<b>② 商品行</b>（序号表行只被买家提交意向使用），
 * 全局顺序「① 序号表 → ② 商品 → ③ 意向」不被破坏，故与买家端并发不会死锁。
 *
 * <h2>⚠️ 三条互不相同的操作，别混（方案 §3.7）</h2>
 * <table border="1">
 *   <caption>手动解冻 vs 标记交易失败 vs 买家撤销</caption>
 *   <tr><th>操作</th><th>改商品</th><th>改意向</th><th>何时用</th></tr>
 *   <tr><td>{@link #unfreezeGoods()}（{@code PS-05}）</td><td>是（→{@code on_sale}）</td>
 *       <td><b>不碰任何意向</b></td><td>商品被<b>手动冻结</b>、卖家想恢复在售</td></tr>
 *   <tr><td>{@code markTradeFailure}（{@code PS-04}）</td><td>是（→{@code on_sale}）</td>
 *       <td>裁决那一条 {@code trading} 意向</td><td>与<b>某位买家</b>的交易正式结束</td></tr>
 *   <tr><td>{@code revokeIntention}（{@code IS-03}）</td><td>否</td><td>该意向 → {@code revoked}</td>
 *       <td>买家侧操作，<b>卖家无法触发</b></td></tr>
 * </table>
 * <p>最容易写错的是把「解冻」做成「顺带把 trading 意向也处理掉」——那会把
 * {@code INV-04}（存在 {@code trading} 意向时商品必为 {@code frozen}）直接打破。
 */
@Service
public class SellerGoodsService {

    private static final Logger log = LoggerFactory.getLogger(SellerGoodsService.class);

    /** 卖家写操作的事务超时（{@code DEC-DB-13}）。单位是<b>秒</b>。 */
    private static final int WRITE_TIMEOUT_SECONDS = 5;

    /** {@code INV-01} 判据：表内存在这些状态之一即「已有商品」。 */
    private static final List<GoodsStatus> OCCUPYING_STATUSES =
            List.of(GoodsStatus.on_sale, GoodsStatus.frozen);

    private final GoodsRepository goodsRepository;
    private final IntentionRepository intentionRepository;
    private final ImageStorageService imageStorageService;
    private final ArchiveService archiveService;
    private final OperationLogService operationLogService;
    private final DatabaseTimeProvider timeProvider;

    public SellerGoodsService(GoodsRepository goodsRepository,
                              IntentionRepository intentionRepository,
                              ImageStorageService imageStorageService,
                              ArchiveService archiveService,
                              OperationLogService operationLogService,
                              DatabaseTimeProvider timeProvider) {
        this.goodsRepository = goodsRepository;
        this.intentionRepository = intentionRepository;
        this.imageStorageService = imageStorageService;
        this.archiveService = archiveService;
        this.operationLogService = operationLogService;
        this.timeProvider = timeProvider;
    }

    // -------------------------------------------------------------------------
    // FR-04 查当前商品（I11-04）
    // -------------------------------------------------------------------------

    /**
     * 取当前商品。
     *
     * <p><b>无商品时返回 {@code null}</b>，由 Controller 下发 {@code data: null}、{@code code: 0}
     * ——前端走 {@code P10-07} 空态（发布表单），而<b>不是</b>错误分支。
     * 这与 {@code 20002}（「当前无商品」）并不冲突：{@code 20002} 用于
     * 「本来要求有商品、结果没有」的操作类接口（冻结／解冻／下架），
     * 而本接口的语义就是「查一查有没有」，没有是<b>正常答案</b>。
     *
     * @return 商品 DTO；表空时为 {@code null}
     */
    @Transactional(readOnly = true)
    public GoodsData getCurrentGoods() {
        return goodsRepository.findFirstByOrderByCreateAtAsc()
                .map(GoodsData::from)
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // FR-03 发布商品（I11-06）
    // -------------------------------------------------------------------------

    /**
     * 发布商品（{@code I11-06}）。
     *
     * <h2>步骤与错误码（顺序<b>不可</b>调换）</h2>
     * <ol>
     *   <li>{@code existsByStatusIn({on_sale, frozen})} 为真 → {@code 20001}（{@code M10-24}）；
     *       <b>先判它</b>，因为「已经有商品了」是用户最先要知道的事实——此时再逐字段报
     *       「名称超长」是答非所问（{@code P10-06} 本来就不该让用户提交）。</li>
     *   <li>字段校验 → {@code 20007}／{@code 20008}／{@code 20010}／{@code 50002}；</li>
     *   <li>{@code pic_url} 回填校验 → {@code 20009}；</li>
     *   <li>建实体并保存（{@code status = on_sale}、{@code freezeBy = null}）；</li>
     *   <li>记操作日志。</li>
     * </ol>
     *
     * <h2>⚠️ {@code INV-01} 的并发竞态是<b>已接受风险</b>（{@code DEC-DB-06}）</h2>
     * <p>「先查再插」在并发下不可靠，而表内至多 1 行的约束<b>无法用 DDL 表达</b>。
     * 之所以接受：系统只有<b>一个</b>管理员账号、且<b>不提供注册入口</b>（{@code FR-01}、{@code O-10}），
     * 不存在第二个操作者。若将来引入多管理员，须回头重评（{@code INV-01} 的复核触发条件）。
     *
     * <h2>⚠️ 绝不调用 {@code goods.setResult(...)}</h2>
     * <p>{@code simpleshop_goods.result} 恒为 NULL（{@code DEC-DB-11}、{@code R8}）。
     * {@link EntityFactory#newGoods} 刻意没碰它——改用工厂而不是自己 {@code setStatus}/{@code setResult}，
     * 就是为了让「发布即在售」与「result 不写」这两条成为结构性保证。
     *
     * @param command 发布命令
     * @return 新商品 ID
     * @throws BusinessException {@code 20001}／{@code 20007}／{@code 20008}／{@code 20009}／{@code 20010}／{@code 50002}
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public PublishResultData publishGoods(PublishGoodsCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        // ① INV-01／BR-11 前置校验
        if (goodsRepository.existsByStatusIn(OCCUPYING_STATUSES)) {
            throw new BusinessException(ErrorCode.GOODS_ALREADY_EXISTS);
        }

        // ② 字段校验与归一化（全部规则在 GoodsValidator，见该类注释）
        String name = GoodsValidator.normalizeName(command.name());
        String description = GoodsValidator.normalizeDescription(command.description());
        BigDecimal price = GoodsValidator.parsePrice(command.price());

        // ③ 图片路径校验。空串/全空白按「未上传」处理（与描述的归一化口径一致），
        //    否则前端多带一个空格就会得到一句莫名其妙的「图片不合规」。
        String picUrl = command.picUrl() == null ? null : command.picUrl().trim();
        if (picUrl != null && picUrl.isEmpty()) {
            picUrl = null;
        }
        imageStorageService.verifyStoredPicUrl(picUrl);

        // ④ 建实体。初始 on_sale / freezeBy=null / result=null 全部由工厂保证。
        Goods goods = EntityFactory.newGoods(name, description, picUrl, price);
        goodsRepository.save(goods);

        // ⑤ NFR-12 之外的额外留痕（见 OperationLogService.PUBLISH_GOODS 的说明）
        operationLogService.log(OperationLogService.PUBLISH_GOODS,
                OperationLogService.TARGET_GOODS, goods.getId());
        log.info("published goods: goodsId={} name={} price={}", goods.getId(), name, price);
        return PublishResultData.of(goods.getId());
    }

    // -------------------------------------------------------------------------
    // FR-05 手动冻结（I11-07）
    // -------------------------------------------------------------------------

    /**
     * 手动冻结商品（{@code I11-07}、{@code PS-02}）。
     *
     * <h2>⚠️ 不校验队列是否为空（澄清 Q18）</h2>
     * <p>只要商品在售就能冻结——{@code PS-02} 的原文是「在售即可冻结」。
     * 这一点<b>曾经写错过</b>（早期稿误以为「有排队就不能冻结」），故此处特别标注：
     * <b>本方法里不应出现任何对 {@code intentionRepository} 的调用</b>。
     * <p>业务上也讲得通：冻结只是「暂停接受新意向」，已经在队里的人不该被清掉；
     * 而冻结之后买家无法再提交（{@code INV-03}），效果正是卖家想要的。
     *
     * <h2>⚠️ 冻结会挡住「进入交易」</h2>
     * <p>{@code I11-11} 要求商品为 {@code on_sale}，故手动冻结后无法对任何意向发起交易，
     * 必须先解冻（这正是已定 Q-1 的业务含义，方案 §3.7）。
     *
     * @return 更新后的商品 DTO（前端据此刷新按钮可用性）
     * @throws BusinessException {@code 20002}（无当前商品）、{@code 20003}（非在售）
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public GoodsData freezeGoods() {
        Goods goods = lockCurrentGoods();
        if (goods.getStatus() != GoodsStatus.on_sale) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
        }
        goods.setStatus(GoodsStatus.frozen);
        goods.setFreezeBy(FreezeBy.manual);

        operationLogService.log(OperationLogService.FREEZE_GOODS,
                OperationLogService.TARGET_GOODS, goods.getId());
        log.info("froze goods manually: goodsId={}", goods.getId());
        return GoodsData.from(goods);
    }

    // -------------------------------------------------------------------------
    // FR-06 手动解冻（I11-08）
    // -------------------------------------------------------------------------

    /**
     * 手动解冻商品（{@code I11-08}、{@code PS-05}）。
     *
     * <h2>⚠️ {@code 20005} 是本方法的核心拦截项（{@code FIX-01} 的服务端兜底）</h2>
     * <p>{@code freeze_by = trade} 表示商品正因某笔交易而冻结（{@code BR-07}、{@code PS-06}），
     * 此时解冻会破坏 {@code INV-04}（存在 {@code trading} 意向时商品必为 {@code frozen}），
     * 让商品在「有人在交易中」的状态下重新接受新意向。
     * <p><b>服务端必须自己拒绝，不得依赖前端禁用按钮</b>——验收方式 {@code V-02} 明确要求
     * 「必须包含绕过前端的负向调用」。前端禁用只是体验，服务端判断才是正确性。
     *
     * <h2>⚠️ 只改商品，不碰任何意向</h2>
     * <p>与「标记交易失败」的区别见类注释的对照表。这里<b>不应出现</b>任何
     * {@code intentionRepository} 调用。若某天有人「顺手」在这里把 {@code trading} 意向也裁决掉，
     * 那笔交易就会凭空消失：意向还标着 {@code trading}，商品却已解冻——
     * 买家查口令码仍会被告知「已进入交易」，而卖家侧再也找不到结束它的入口。
     *
     * @return 更新后的商品 DTO
     * @throws BusinessException {@code 20002}（无当前商品）、{@code 20004}（非已冻结）、
     *                          {@code 20005}（交易冻结，禁止手动解冻）
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public GoodsData unfreezeGoods() {
        Goods goods = lockCurrentGoods();
        if (goods.getStatus() != GoodsStatus.frozen) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FROZEN);
        }
        if (goods.getFreezeBy() == FreezeBy.trade) {
            throw new BusinessException(ErrorCode.GOODS_FROZEN_BY_TRADE);
        }
        // 说明：status=frozen 但 freezeBy=null 是「不该出现」的组合（BR-07 要求 frozen 时必填）；
        // 真出现时按「可解冻」处理，让管理员能从脏状态里恢复，而不是被永久卡住。
        goods.setStatus(GoodsStatus.on_sale);
        goods.setFreezeBy(null);

        operationLogService.log(OperationLogService.UNFREEZE_GOODS,
                OperationLogService.TARGET_GOODS, goods.getId());
        log.info("unfroze goods manually: goodsId={}", goods.getId());
        return GoodsData.from(goods);
    }

    // -------------------------------------------------------------------------
    // FR-23 手动下架（I11-09）
    // -------------------------------------------------------------------------

    /**
     * 手动下架商品（{@code I11-09}、{@code PS-08}），同事务内归档。
     *
     * <h2>⚠️ 队列为空的判定式必须用 {@code queued}，不能用「非终态」</h2>
     * <pre>
     * ✅ findByGoodsIdAndStatusOrderByQueueOrderAsc(id, queued).isEmpty()
     * ❌ countNonTerminal(id, {succeeded, failed, revoked}) == 0
     * </pre>
     * <p>在 {@code on_sale} 这个前提下两者<b>结果等价</b>（{@code INV-04} 保证此时不可能有
     * {@code trading} 意向），但<b>语义不同</b>：{@code PS-08} 的前置条件是「<b>队列</b>为空」，
     * 而「队列」在 {@code §9.5.1}/{@code §9.5.3} 中专指 {@code queued}。
     * 用错判定式不会立刻出错，却会让后来读代码的人以为「{@code trading} 也算队列」，
     * 进而在别处（那里两者不等价）照抄出真正的 bug。
     *
     * <h2>⚠️ 不额外检查 {@code trading}——那是 {@code INV-05} 的职责</h2>
     * <p>万一真出现「{@code on_sale} 却有 {@code trading} 意向」（不变量应已禁止），
     * {@link ArchiveService#archive} 的第一步 {@code countActiveByGoodsId > 0} 会以
     * {@code 30005} 拒绝并整体回滚。在此重复一遍只会多一条口径不一致的判定。
     *
     * <h2>手动下架<b>不产生任何交易流水</b></h2>
     * <p>没有任何意向进入过交易（{@code §9.9.5}）。这一点常被写错成「下架时给所有意向补流水」——
     * {@code ArchiveService} 一条都不追加，本方法也不追加。
     *
     * @throws BusinessException {@code 20002}（无当前商品）、{@code 20003}（非在售）、
     *                          {@code 20006}（队列非空）、{@code 30005}（归档前置校验失败）
     */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public void takeGoodsOffline() {
        Goods goods = lockCurrentGoods();
        if (goods.getStatus() != GoodsStatus.on_sale) {
            throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
        }
        String goodsId = goods.getId();

        boolean queueEmpty = intentionRepository
                .findByGoodsIdAndStatusOrderByQueueOrderAsc(goodsId, IntentionStatus.queued)
                .isEmpty();
        if (!queueEmpty) {
            throw new BusinessException(ErrorCode.QUEUE_NOT_EMPTY);
        }

        // 同一事务内归档；tradeEnd = 下架时间（由唯一时间来源给出）
        archiveService.archive(goods, GoodsResult.offline, timeProvider.nowUtc());

        operationLogService.log(OperationLogService.OFFLINE_GOODS,
                OperationLogService.TARGET_GOODS, goodsId);
        log.info("took goods offline: goodsId={}", goodsId);
    }

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    /**
     * 取「当前商品」并<b>加行锁</b>，供三个状态变更方法共用。
     *
     * <p>为什么分两步（先查 ID、再按 ID 加锁）：仓储只有
     * {@code findFirstByOrderByCreateAtAsc()}（无锁）与 {@code findByIdForUpdate(id)}（加锁），
     * 没有「取首行并加锁」的组合方法，而附录 B 不允许改动仓储接口。
     * 两步之间的窗口不会造成问题：表内至多 1 行，且<b>第二次读到的状态才是判定依据</b>——
     * 第一步只用来拿 ID。
     * <p>第二次读仍可能为空（并发下架刚好把行删了），故两处都按 {@code 20002} 处理。
     *
     * @return 已加锁的当前商品
     * @throws BusinessException {@code 20002}——当前没有商品
     */
    private Goods lockCurrentGoods() {
        String goodsId = goodsRepository.findFirstByOrderByCreateAtAsc()
                .map(Goods::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_CURRENT_GOODS));
        return goodsRepository.findByIdForUpdate(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_CURRENT_GOODS));
    }
}
