package com.simpleshop.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.persistence.enums.FailType;
import com.simpleshop.persistence.enums.FreezeBy;
import com.simpleshop.persistence.enums.GoodsResult;
import com.simpleshop.persistence.enums.GoodsStatus;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.enums.TradeResult;

/**
 * {@link EntityFactory} 的字段赋值验证（方案 §3.4）。
 *
 * <p>本用例是<b>纯对象构造</b>测试（不启动 Spring、不连库），
 * 因此它能精确定位「哪一列赋错了」——而集成测试只能告诉你「数据不对」。
 *
 * <h2>为什么值得单独测</h2>
 * <p>{@code GoodsHistory} / {@code IntentionHistory} <b>没有任何生命周期回调</b>，
 * 归档时靠本工厂<b>逐列显式赋值</b>。若漏赋某一列，<b>不会有任何报错</b>——
 * 只会静默写进 {@code null}（或者更糟：写进回调的默认值）。
 * 因此这里把每一列都断言一遍。
 */
class EntityFactoryTest {

    private static final LocalDateTime CREATE_AT = LocalDateTime.of(2026, 10, 1, 8, 0, 0);
    private static final LocalDateTime TRADE_START = LocalDateTime.of(2026, 10, 2, 9, 30, 0);
    private static final LocalDateTime TRADE_END = LocalDateTime.of(2026, 10, 3, 15, 45, 0);

    // -------------------------------------------------------------------------
    // 发布商品
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("newGoods：初始状态固定 on_sale，且【绝不】写 result 列")
    void newGoodsSetsInitialState() {
        Goods goods = EntityFactory.newGoods("iPhone 15", "全新未拆封", "/images/2026/10/a.jpg",
                new BigDecimal("5999.00"));

        assertThat(goods.getName()).isEqualTo("iPhone 15");
        assertThat(goods.getDescription()).isEqualTo("全新未拆封");
        assertThat(goods.getPicUrl()).isEqualTo("/images/2026/10/a.jpg");
        assertThat(goods.getPrice()).isEqualByComparingTo("5999.00");
        assertThat(goods.getStatus()).isEqualTo(GoodsStatus.on_sale);

        // 其余列保持未设置，交给 @PrePersist 或后续状态迁移
        assertThat(goods.getId()).as("id 由 @PrePersist 生成").isNull();
        assertThat(goods.getCreateAt()).as("createAt 由 @PrePersist 生成").isNull();
        assertThat(goods.getFreezeBy()).isNull();
        assertThat(goods.getTradeStart()).isNull();
        assertThat(goods.getTradeEnd()).isNull();
        // ⚠️ DEC-DB-11 / 9-H：当前商品表的 result 恒为 NULL，任何业务路径都不得写入
        assertThat(goods.getResult()).as("当前表的 result 列必须保持 NULL").isNull();
    }

    // -------------------------------------------------------------------------
    // 提交意向
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("newIntention：初始状态固定 queued，token 与 queueOrder 已就位")
    void newIntentionSetsInitialState() {
        Goods goods = EntityFactory.newGoods("商品", null, null, new BigDecimal("1.00"));
        Intention intention = EntityFactory.newIntention(goods, 7, "张三", "13800138000", "ABCD1234EFGH");

        assertThat(intention.getGoods()).isSameAs(goods);
        assertThat(intention.getQueueOrder()).isEqualTo(7);
        assertThat(intention.getName()).isEqualTo("张三");
        assertThat(intention.getTel()).isEqualTo("13800138000");
        assertThat(intention.getToken()).isEqualTo("ABCD1234EFGH");
        assertThat(intention.getStatus()).isEqualTo(IntentionStatus.queued);
        assertThat(intention.getFailType()).isNull();
        assertThat(intention.getFailReason()).isNull();
        assertThat(intention.getId()).as("id 由 @PrePersist 生成").isNull();
        assertThat(intention.getCreateAt()).as("createAt 由 @PrePersist 生成").isNull();
    }

    // -------------------------------------------------------------------------
    // 交易流水
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("newTradeHistory：成功流水 failType/failReason 为空")
    void newTradeHistoryForSuccess() {
        TradeHistory history = EntityFactory.newTradeHistory("I-1", TRADE_START, TRADE_END,
                TradeResult.sold, null, null);

        assertThat(history.getIntentionId()).isEqualTo("I-1");
        assertThat(history.getTradeStart()).isEqualTo(TRADE_START);
        assertThat(history.getTradeEnd()).isEqualTo(TRADE_END);
        assertThat(history.getResult()).isEqualTo(TradeResult.sold);
        assertThat(history.getFailType()).isNull();
        assertThat(history.getFailReason()).isNull();
        assertThat(history.getId()).as("id 由 @PrePersist 生成").isNull();
        assertThat(history.getCreateAt()).as("createAt 由 @PrePersist 生成").isNull();
    }

    @Test
    @DisplayName("newTradeHistory：失败流水必带 failType（result=failed 时的条件必填）")
    void newTradeHistoryForFailure() {
        TradeHistory history = EntityFactory.newTradeHistory("I-2", TRADE_START, TRADE_END,
                TradeResult.failed, FailType.requeued, "买家临时有事");

        assertThat(history.getResult()).isEqualTo(TradeResult.failed);
        assertThat(history.getFailType()).isEqualTo(FailType.requeued);
        assertThat(history.getFailReason()).isEqualTo("买家临时有事");
    }

    // -------------------------------------------------------------------------
    // 归档：历史商品
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("newGoodsHistory：沿用原 ID、时间列继承原值、status 恒 off_sale、freezeBy 恒清空")
    void newGoodsHistoryCopiesColumns() {
        Goods source = goodsOnSale();
        source.setId("G-abc-123");
        source.setCreateAt(CREATE_AT);
        source.setUpdateAt(TRADE_START);
        // 「进入交易」后的状态（归档前商品的真实形态）
        source.setStatus(GoodsStatus.frozen);
        source.setFreezeBy(FreezeBy.trade);
        source.setTradeStart(TRADE_START);

        GoodsHistory history = EntityFactory.newGoodsHistory(source, TRADE_END, GoodsResult.sold);

        // 主键沿用原值（9-G：归档不改 ID，前缀表示来源类型而非所在表）
        assertThat(history.getId()).isEqualTo("G-abc-123");
        // 业务字段 1:1 复制
        assertThat(history.getName()).isEqualTo(source.getName());
        assertThat(history.getDescription()).isEqualTo(source.getDescription());
        assertThat(history.getPicUrl()).isEqualTo(source.getPicUrl());
        assertThat(history.getPrice()).isEqualByComparingTo(source.getPrice());
        // 时间列：createAt/tradeStart 继承原值；updateAt 写归档时间
        assertThat(history.getCreateAt()).isEqualTo(CREATE_AT);
        assertThat(history.getUpdateAt()).as("updateAt = 归档时间").isEqualTo(TRADE_END);
        assertThat(history.getTradeStart()).isEqualTo(TRADE_START);
        assertThat(history.getTradeEnd()).isEqualTo(TRADE_END);
        // 归档例外：status 恒 off_sale、freezeBy 恒清空
        assertThat(history.getStatus()).isEqualTo(GoodsStatus.off_sale);
        assertThat(history.getFreezeBy()).as("归档必须清空冻结来源").isNull();
        // 必填结果
        assertThat(history.getResult()).isEqualTo(GoodsResult.sold);
    }

    @Test
    @DisplayName("newGoodsHistory：手动下架时 result=offline，且 tradeStart 可为空")
    void newGoodsHistoryForManualOffline() {
        Goods source = goodsOnSale();
        source.setId("G-offline");
        source.setCreateAt(CREATE_AT);

        GoodsHistory history = EntityFactory.newGoodsHistory(source, TRADE_END, GoodsResult.offline);

        assertThat(history.getResult()).isEqualTo(GoodsResult.offline);
        assertThat(history.getStatus()).isEqualTo(GoodsStatus.off_sale);
        assertThat(history.getTradeStart())
                .as("未进入交易的商品，trade_start 继承为 null（9.6.1）")
                .isNull();
        assertThat(history.getTradeEnd()).isEqualTo(TRADE_END);
    }

    // -------------------------------------------------------------------------
    // 归档：历史意向
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("newIntentionHistory：逐列复制、沿用原 ID、且不复制 queue_order/token")
    void newIntentionHistoryCopiesColumns() {
        Goods goods = goodsOnSale();
        goods.setId("G-1");
        GoodsHistory goodsHistory = EntityFactory.newGoodsHistory(goods, TRADE_END, GoodsResult.sold);

        Intention source = EntityFactory.newIntention(goods, 42, "李四", "010-12345678", "XYZ987654321");
        source.setId("I-xyz-789");
        source.setCreateAt(CREATE_AT);
        source.setStatus(IntentionStatus.failed);
        source.setFailType(FailType.sold_out);
        source.setFailReason("已经卖给别人了");

        IntentionHistory history = EntityFactory.newIntentionHistory(source, goodsHistory);

        assertThat(history.getId()).isEqualTo("I-xyz-789");
        assertThat(history.getGoodsHistory()).isSameAs(goodsHistory);
        assertThat(history.getCreateAt()).isEqualTo(CREATE_AT);
        assertThat(history.getName()).isEqualTo("李四");
        assertThat(history.getTel()).isEqualTo("010-12345678");
        assertThat(history.getStatus()).isEqualTo(IntentionStatus.failed);
        assertThat(history.getFailType()).isEqualTo(FailType.sold_out);
        assertThat(history.getFailReason()).isEqualTo("已经卖给别人了");

        // ⚠️ 历史意向表【没有】queueOrder 与 token 属性（DEC-DB-05、9-S），
        // 因此这里无法断言「未被复制」——编译期就已经杜绝了误用。
        // 该口径的验证手段是 IntentionHistoryRepository 的字段清单与 V1 脚本，
        // 已由数据层的 StructureAndUniqueConstraintTest 覆盖。
    }

    @Test
    @DisplayName("newIntentionHistory：重排队意向的 fail_type 保留 requeued（DEC-DB-10 ①）")
    void newIntentionHistoryKeepsRequeuedFailType() {
        Goods goods = goodsOnSale();
        goods.setId("G-2");
        GoodsHistory goodsHistory = EntityFactory.newGoodsHistory(goods, TRADE_END, GoodsResult.sold);

        Intention source = EntityFactory.newIntention(goods, 9, "王五", "13900139000", "AAAA11112222");
        source.setId("I-requeued");
        source.setCreateAt(CREATE_AT);
        // 重排队后的形态：status 回到 queued，但 fail_type 保留 requeued 作为
        // 「最近一次失败处理方式」的快照（DEC-DB-10 ①）
        source.setStatus(IntentionStatus.queued);
        source.setFailType(FailType.requeued);

        IntentionHistory history = EntityFactory.newIntentionHistory(source, goodsHistory);

        assertThat(history.getStatus()).isEqualTo(IntentionStatus.queued);
        assertThat(history.getFailType())
                .as("归档是逐列复制，不得擅自清空 fail_type")
                .isEqualTo(FailType.requeued);
    }

    // -------------------------------------------------------------------------
    // 工具
    // -------------------------------------------------------------------------

    private static Goods goodsOnSale() {
        return EntityFactory.newGoods("测试商品", "描述", "/images/2026/10/test.png",
                new BigDecimal("100.00"));
    }
}
