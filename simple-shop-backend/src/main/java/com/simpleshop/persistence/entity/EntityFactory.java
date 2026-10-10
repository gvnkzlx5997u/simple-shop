package com.simpleshop.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.simpleshop.persistence.enums.FailType;
import com.simpleshop.persistence.enums.GoodsResult;
import com.simpleshop.persistence.enums.GoodsStatus;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.enums.TradeResult;

/**
 * 实体创建工厂（供 Service 层使用）。
 *
 * <h2>为什么需要这个类（关键，不要试图删掉它）</h2>
 * <p>7 个实体的无参构造都是 {@code protected}（JPA 规范要求，且刻意避免业务代码误用——
 * 见《数据层 · 交接说明》§3 关于测试用 {@code TestEntities} 的同款说明）。
 * {@code protected} 构造<b>只在同包可见</b>，而 Service 层在 {@code com.simpleshop.service} 包，
 * <b>无法</b> {@code new Goods()}。
 *
 * <p>而以下四件事<b>必须</b>新建实体，绕不开：
 * <ul>
 *   <li><b>发布商品</b> → {@link #newGoods}</li>
 *   <li><b>提交意向</b> → {@link #newIntention}</li>
 *   <li><b>归档</b>（写历史商品／历史意向）→ {@link #newGoodsHistory}、{@link #newIntentionHistory}</li>
 *   <li><b>追加交易流水</b> → {@link #newTradeHistory}</li>
 * </ul>
 *
 * <p>因此把工厂放在<b>实体包内</b>（同包才能访问 {@code protected} 构造），只暴露业务创建所需的
 * 最小入口。<b>不</b>把实体构造改成 {@code public}——那会破坏「业务代码不能随意 new 出
 * 半初始化实体」的可见性约束，而该约束是数据层刻意设计的。
 *
 * <h2>⚠️ 与 {@code @PrePersist} 的分工（本类的核心正确性要点）</h2>
 * <p>{@code Goods}/{@code Intention}/{@code TradeHistory} 的 {@code @PrePersist} 都写成
 * {@code if (id == null)} / {@code if (createAt == null)} —— <b>只在为 null 时生成</b>。
 * 这个 {@code if} 是<b>硬约束</b>：归档时历史行要<b>沿用原 ID</b>、由调用方显式赋原值；
 * 若无条件赋值，归档会产生新 ID，直接违反 {@code 9-G}。
 *
 * <p>因此本类：
 * <ul>
 *   <li>{@link #newGoods}/{@link #newIntention}/{@link #newTradeHistory}：<b>不</b>设置
 *       {@code id}，让 {@code @PrePersist} 生成；{@code createAt} 由调用方决定是否显式传入。</li>
 *   <li>{@link #newGoodsHistory}/{@link #newIntentionHistory}：<b>必须显式赋值全部字段</b>——
 *       {@code GoodsHistory}/{@code IntentionHistory} <b>一条生命周期回调都没有</b>（刻意如此），
 *       归档是<b>数据搬迁</b>，时间列必须继承原值。若给历史实体挂 {@code @PreUpdate}，
 *       二次保存会覆盖归档时间。</li>
 * </ul>
 *
 * <h2>⚠️ 本类不做的两件事</h2>
 * <ul>
 *   <li><b>不写当前表的 {@code result} 列</b>：{@code simpleshop_goods.result} 恒为 NULL
 *       （{@code DEC-DB-11}）；该值只在归档时写到 {@link GoodsHistory} 上。
 *       {@link #newGoods} <b>不</b>调用 {@code setResult}，这是刻意的。</li>
 *   <li><b>不设置 {@code status} 之外的状态推导</b>：例如 {@link #newGoods} 固定
 *       {@code on_sale}（{@code FR-03}：发布即在售），调用方不得改；状态迁移由 Service 用 setter 完成。</li>
 * </ul>
 */
public final class EntityFactory {

    /** 工具类，禁止实例化。 */
    private EntityFactory() {
    }

    // =========================================================================
    // 当前表：业务创建
    // =========================================================================

    /**
     * 发布商品（{@code FR-03}）。初始状态固定 {@code on_sale}。
     *
     * <p>{@code id} 与 {@code createAt}/{@code updateAt} 交给 {@code @PrePersist}；
     * {@code freezeBy}、{@code tradeStart}、{@code tradeEnd} 保持 {@code null}。
     *
     * <p>⚠️ <b>刻意不调用 {@code setResult}</b>：当前表该列恒为 NULL（{@code DEC-DB-11}）。
     *
     * @param name        名称（≤50 字符，调用方须已校验）
     * @param description 描述（可空，≤500 字符）
     * @param picUrl      图片相对路径（可空，含 {@code /images} 前缀）
     * @param price       价格（{@code 0 < price ≤ 999999.99}，两位小数，调用方须已校验）
     */
    public static Goods newGoods(String name, String description, String picUrl, BigDecimal price) {
        Goods goods = new Goods();
        goods.setName(name);
        goods.setDescription(description);
        goods.setPicUrl(picUrl);
        goods.setPrice(price);
        goods.setStatus(GoodsStatus.on_sale);
        return goods;
    }

    /**
     * 提交购买意向（{@code FR-13}、{@code IS-01}）。初始状态固定 {@code queued}。
     *
     * <p>{@code id} 与 {@code createAt} 交给 {@code @PrePersist}。
     * 注意 {@code Intention} <b>没有</b> {@code update_at} 列，也<b>没有</b> {@code @PreUpdate}——
     * {@code createAt} 是「原始提交时间」，重排队与改姓名电话<b>都不得变更</b>
     * （{@code BR-20}、{@code DEC-15}）。
     *
     * @param goods      所属商品（当前商品实体）
     * @param queueOrder 排序序号，由 {@code QueueSequenceRepository} 在<b>同一事务内</b>分配
     * @param name       买家姓名（≤50 字符，调用方须已校验）
     * @param tel        联系电话（7~20 位数字与 {@code -}，调用方须已校验）
     * @param token      口令码（12 位 {@code [A-Z0-9]}，<b>已归一化为大写</b>）
     */
    public static Intention newIntention(Goods goods, int queueOrder, String name, String tel, String token) {
        Intention intention = new Intention();
        intention.setGoods(goods);
        intention.setQueueOrder(queueOrder);
        intention.setName(name);
        intention.setTel(tel);
        intention.setToken(token);
        intention.setStatus(IntentionStatus.queued);
        return intention;
    }

    /**
     * 追加交易流水（{@code §9.7.2}）。<b>只追加，不更新、不删除</b>。
     *
     * <p>⚠️ {@code tradeStart} 与 {@code tradeEnd} <b>都是 NOT NULL</b>（V1 脚本），
     * 因此本方法<b>只能</b>在「标记交易结果」时调用——那时两个时间戳都有值。
     * <b>不要</b>在「进入交易」时调用：那时只有 {@code tradeStart}，插入必然失败。
     *
     * <p>⚠️ {@code result = failed} 时 {@code failType} <b>必填</b>（该「条件必填」由应用层保证，
     * 数据库侧可空）。{@code result = sold} 时两者都传 {@code null}。
     *
     * @param intentionId 意向 ID；<b>裸 String</b>，本表无物理外键（{@code 9-N}）
     * @param tradeStart  该次「进入交易」的时间戳（UTC）
     * @param tradeEnd    该次「标记交易结果」的时间戳（UTC）
     * @param result      流水级结果：{@code sold} / {@code failed}
     * @param failType    失败类型；{@code result = sold} 时传 {@code null}
     * @param failReason  失败备注（可空，≤300 字符）
     */
    public static TradeHistory newTradeHistory(String intentionId,
                                               LocalDateTime tradeStart,
                                               LocalDateTime tradeEnd,
                                               TradeResult result,
                                               FailType failType,
                                               String failReason) {
        TradeHistory history = new TradeHistory();
        history.setIntentionId(intentionId);
        history.setTradeStart(tradeStart);
        history.setTradeEnd(tradeEnd);
        history.setResult(result);
        history.setFailType(failType);
        history.setFailReason(failReason);
        return history;
    }

    // =========================================================================
    // 历史表：归档（数据搬迁）
    // =========================================================================

    /**
     * 归档：由当前商品生成历史商品行（{@code PS-03}／{@code PS-08}、{@code §9.6.3}）。
     *
     * <p><b>⚠️ 历史实体没有任何生命周期回调</b>，因此本方法<b>逐列显式赋值</b>；
     * 时间列<b>继承原值</b>，不得依赖回调、也不得「为了让时间自动填上」而给实体加回调。
     *
     * <p>三处归档例外（{@code 9-S}）中的前两处在此落实：
     * <ul>
     *   <li>{@code status} 恒写 {@code off_sale}</li>
     *   <li>{@code freezeBy} 恒清空</li>
     * </ul>
     *
     * <p>{@code tradeEnd} 与 {@code result} 是历史表的<b>必填</b>列，由调用方传入：
     * 标记交易成功时 {@code tradeEnd} = 成交时间、{@code result = sold}；
     * 手动下架时 {@code tradeEnd} = 下架时间、{@code result = offline}。
     *
     * <p>⚠️ {@code updateAt} 写为归档时间（{@code §9.6.1}：「归档时写入（= 归档时间）」），
     * <b>不</b>复制原 {@code update_at}。
     *
     * @param source   当前商品（其 {@code status} 无需预置为 {@code off_sale}，本方法强制写）
     * @param tradeEnd 交易结束时间（= 归档时间点，必填）
     * @param result   商品级结果 {@code sold} / {@code offline}（必填）
     */
    public static GoodsHistory newGoodsHistory(Goods source, LocalDateTime tradeEnd, GoodsResult result) {
        GoodsHistory history = new GoodsHistory();
        // 主键沿用原值（前缀仍为 G，前缀表示「实体来源类型」而非所在表，9-G）
        history.setId(source.getId());
        history.setName(source.getName());
        history.setDescription(source.getDescription());
        history.setPicUrl(source.getPicUrl());
        history.setPrice(source.getPrice());
        history.setStatus(GoodsStatus.off_sale);   // 归档例外①：恒写 off_sale
        history.setFreezeBy(null);                 // 归档例外②：恒清空
        history.setCreateAt(source.getCreateAt()); // 继承原值
        history.setUpdateAt(tradeEnd);             // 归档时间
        history.setTradeStart(source.getTradeStart()); // 继承原值（未进入交易则为 null）
        history.setTradeEnd(tradeEnd);
        history.setResult(result);
        return history;
    }

    /**
     * 归档：由当前意向生成历史意向行（{@code §9.6.3}）。
     *
     * <p>归档例外第三处在此落实：历史意向表<b>根本没有</b> {@code queue_order} 与 {@code token}
     * 属性（{@code DEC-DB-05}、{@code 9-S}），因此<b>无法</b>也<b>不应</b>复制这两列——
     * 编译期即杜绝误用。原因是：{@code queue_order} 在归档时位次已无意义；
     * {@code token} 在归档时必已失效（归档前置要求全部意向终态，而终态即口令码失效，
     * {@code BR-26}），且 {@code FR-24}/{@code NFR-16} 明确「对终态意向不返回可用口令码」，
     * 故历史中无任何读取方。
     *
     * <p>⚠️ 外键列由 {@code fk_good_id} <b>改名</b>为 {@code fk_goods_history_id}，
     * <b>值不变</b>（都是商品 ID）。这里传<b>实体引用</b>而非 ID：该关联是
     * {@code @ManyToOne}，需要实体对象。调用方应传入<b>已 flush 的历史商品实例</b>
     * （{@code ArchiveService} 中先 {@code saveAndFlush(goodsHistory)} 再调用本方法），
     * 因为 {@code simpleshop_intentions_history} 上有物理外键指向
     * {@code simpleshop_goods_history(id)}，顺序反了会直接撞约束。
     *
     * @param source       当前意向（归档前置校验已保证其 {@code status} 为终态）
     * @param goodsHistory 对应的历史商品实体（须已持久化）
     */
    public static IntentionHistory newIntentionHistory(Intention source, GoodsHistory goodsHistory) {
        IntentionHistory history = new IntentionHistory();
        history.setId(source.getId());                 // 沿用原值（前缀仍为 I，9-G）
        history.setGoodsHistory(goodsHistory);         // fk_good_id → fk_goods_history_id，值不变
        history.setCreateAt(source.getCreateAt());     // 原始提交时间，继承（也是历史名单的排序键）
        history.setName(source.getName());
        history.setTel(source.getTel());
        history.setStatus(source.getStatus());
        history.setFailType(source.getFailType());
        history.setFailReason(source.getFailReason());
        return history;
    }
}
