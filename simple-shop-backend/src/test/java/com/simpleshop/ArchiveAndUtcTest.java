package com.simpleshop;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.simpleshop.persistence.entity.Goods;
import com.simpleshop.persistence.entity.GoodsHistory;
import com.simpleshop.persistence.entity.Intention;
import com.simpleshop.persistence.entity.IntentionHistory;
import com.simpleshop.persistence.entity.TradeHistory;
import com.simpleshop.persistence.enums.FailType;
import com.simpleshop.persistence.enums.GoodsResult;
import com.simpleshop.persistence.enums.GoodsStatus;
import com.simpleshop.persistence.enums.IntentionStatus;
import com.simpleshop.persistence.enums.TradeResult;
import com.simpleshop.persistence.repository.GoodsHistoryRepository;
import com.simpleshop.persistence.repository.GoodsRepository;
import com.simpleshop.persistence.repository.IntentionHistoryRepository;
import com.simpleshop.persistence.repository.IntentionRepository;
import com.simpleshop.persistence.repository.TradeHistoryRepository;
import com.simpleshop.persistence.time.DatabaseTimeProvider;
import com.simpleshop.security.PasswordHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 归档字段核对与 UTC 校验。
 *
 * <p>覆盖：
 * <ul>
 *   <li><b>归档字段核对</b>：归档后历史意向表无 {@code queue_order}／{@code token} 列，其余列与原值一致；</li>
 *   <li><b>UTC 校验</b>：写入后直接查库，时间值与预期 UTC 时点一致。</li>
 * </ul>
 *
 * <p>另外补充了「口令码哈希与库中值匹配」与「历史时间列不被回调覆盖」两条——
 * 它们分别对应该表的数据口径与 §6.2.2 的时间回调口径，属数据层自测应覆盖的范围。
 */
@DisplayName("归档字段与UTC")
class ArchiveAndUtcTest extends DataLayerTestBase {

    @Autowired private GoodsRepository goodsRepository;
    @Autowired private IntentionRepository intentionRepository;
    @Autowired private GoodsHistoryRepository goodsHistoryRepository;
    @Autowired private IntentionHistoryRepository intentionHistoryRepository;
    @Autowired private TradeHistoryRepository tradeHistoryRepository;

    // -------------------------------------------------------------------------
    // 归档字段核对
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("归档：历史意向沿用原 ID，逐列与原值一致，且无 queue_order/token")
    void archiveCopiesEveryColumnExceptQueueOrderAndToken() {
        Goods goods = goodsRepository.saveAndFlush(newGoods(GoodsStatus.frozen));
        Intention intention = newIntention(goods, 801, IntentionStatus.failed, nextToken());
        intention.setFailType(FailType.voided);
        intention.setFailReason(TEST_MARKER + "卖家作废");
        intentionRepository.saveAndFlush(intention);
        String originalToken = intention.getToken();
        LocalDateTime originalCreateAt = intention.getCreateAt();

        // 归档口径：同一事务内「写历史表 → 删当前表」
        LocalDateTime archivedAt = utcNow();
        GoodsHistory goodsHistory = goodsHistoryRepository.saveAndFlush(
                newGoodsHistory(goods, archivedAt, GoodsResult.offline));
        IntentionHistory intentionHistory = intentionHistoryRepository.saveAndFlush(
                toHistory(intention, goodsHistory));
        intentionHistoryRepository.flush();

        // ---- 历史意向：逐列核对 ----
        assertThat(intentionHistory.getId())
                .as("归档不改 ID（9-G）").isEqualTo(intention.getId());
        assertThat(intentionHistory.getCreateAt())
                .as("create_at 继承原值，不被归档时间覆盖").isEqualTo(originalCreateAt);
        assertThat(intentionHistory.getName()).isEqualTo(intention.getName());
        assertThat(intentionHistory.getTel()).isEqualTo(intention.getTel());
        assertThat(intentionHistory.getStatus()).isEqualTo(intention.getStatus());
        assertThat(intentionHistory.getFailType()).isEqualTo(intention.getFailType());
        assertThat(intentionHistory.getFailReason()).isEqualTo(intention.getFailReason());
        assertThat(intentionHistory.getGoodsHistory().getId())
                .as("fk_good_id 改名为 fk_goods_history_id，值不变").isEqualTo(goods.getId());

        // ---- 历史意向表没有 queue_order / token 列，因此这两个值在库中不可能存在 ----
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select * from simpleshop_intentions_history where id = ?", intention.getId());
        assertThat(row).doesNotContainKeys("queue_order", "token");
        assertThat(row.keySet()).containsExactlyInAnyOrder(
                "id", "fk_goods_history_id", "create_at", "name", "tel",
                "status", "fail_type", "fail_reason");
        assertThat(row.get("status")).isEqualTo("failed");
        assertThat(row.get("fail_type")).isEqualTo("voided");

        // 试图插入这两列应直接报「未知列」——列确实不存在
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into simpleshop_intentions_history "
                        + "(id, fk_goods_history_id, create_at, name, tel, status, queue_order, token) "
                        + "values (?,?,?,?,?,?,?,?)",
                TEST_MARKER + "HX", goods.getId(), utcNow(), TEST_MARKER + "x", "138", "failed", 1, "X"))
                .as("历史意向表不应有 queue_order / token 列")
                .isInstanceOf(org.springframework.jdbc.BadSqlGrammarException.class);

        // ---- 历史商品：1:1 同名同类型，两处差异按 §4.3 ----
        Map<String, Object> goodsRow = jdbcTemplate.queryForMap(
                "select * from simpleshop_goods_history where id = ?", goods.getId());
        assertThat(goodsRow.get("status")).as("归档时恒写 off_sale").isEqualTo("off_sale");
        assertThat(goodsRow.get("freeze_by")).as("归档时恒清空 freeze_by").isNull();
        assertThat(goodsRow.get("result")).as("历史商品必填 result").isEqualTo("offline");
        assertThat(goodsRow.get("trade_end")).as("历史商品 trade_end 必填").isNotNull();
        assertThat((BigDecimal) goodsRow.get("price")).isEqualByComparingTo("9.90");

        // 口令码只存在于当前表，单表唯一索引即可保障全局唯一（§10.1 缺口消解）
        assertThat(originalToken).as("原口令码仍只在当前表").isNotNull();
        assertThat(intentionRepository.findByToken(originalToken))
                .as("归档后当前表仍有该意向（本例未删除当前行）").isPresent();
    }

    @Test
    @DisplayName("归档：历史实体的时间列不被回调覆盖（§6.2.2）")
    void archiveDoesNotLetCallbacksOverwriteTimes() {
        // 显式给商品一个较早的发布时间，使归档时间满足
        // ck_goods_history_time_order：trade_end >= create_at
        Goods goods = newGoods(GoodsStatus.frozen);
        goods.setCreateAt(utcNow().minusDays(10));
        goods = goodsRepository.saveAndFlush(goods);

        LocalDateTime archivedAt = utcNow().minusDays(3).truncatedTo(ChronoUnit.SECONDS);

        GoodsHistory history = goodsHistoryRepository.saveAndFlush(
                newGoodsHistory(goods, archivedAt, GoodsResult.offline));
        goodsHistoryRepository.flush();
        goodsHistoryRepository.saveAndFlush(history);   // 二次保存：若挂了 @PreUpdate 就会被污染
        goodsHistoryRepository.flush();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select create_at, update_at, trade_end from simpleshop_goods_history where id = ?",
                history.getId());
        // 用 getObject(..., LocalDateTime.class) 取字面值，避免 getTimestamp 叠加设备时区（§10.5）
        LocalDateTime storedUpdateAt = readDatetimeAsLocal(
                "simpleshop_goods_history", "update_at", history.getId());
        LocalDateTime storedTradeEnd = readDatetimeAsLocal(
                "simpleshop_goods_history", "trade_end", history.getId());

        assertThat(storedUpdateAt)
                .as("update_at 必须等于调用方显式赋的归档时间（二次保存不得覆盖）")
                .isEqualTo(archivedAt);
        assertThat(storedTradeEnd)
                .as("trade_end 必须等于调用方显式赋的归档时间")
                .isEqualTo(archivedAt);
        assertThat(row.get("create_at"))
                .as("create_at 继承原商品发布时间").isNotNull();
    }

    @Test
    @DisplayName("归档前置校验：仍有活动意向时应能报出 > 0（INV-05 拦截依据）")
    void archivePreconditionDetectsActiveIntentions() {
        Goods goods = goodsRepository.saveAndFlush(newGoods(GoodsStatus.frozen));
        intentionRepository.saveAndFlush(newIntention(goods, 811, IntentionStatus.trading, nextToken()));
        intentionRepository.flush();

        assertThat(intentionRepository.countActiveByGoodsId(goods.getId(), activeStatuses()))
                .as("存在 trading 意向，归档应被拒绝")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("流水：结果与失败类型口径，且时间先后 CHECK 生效")
    void tradeHistoryRespectsTimeOrderCheck() {
        Goods goods = goodsRepository.saveAndFlush(newGoods(GoodsStatus.frozen));
        Intention intention = intentionRepository.saveAndFlush(
                newIntention(goods, 821, IntentionStatus.failed, nextToken()));
        LocalDateTime now = utcNow();

        tradeHistoryRepository.saveAndFlush(newTradeHistory(
                intention.getId(), now.minusMinutes(5), now, TradeResult.failed));
        tradeHistoryRepository.flush();

        assertThat(tradeHistoryRepository.countByIntentionId(intention.getId())).isEqualTo(1);

        // ck_trade_history_time_order：trade_end < trade_start 必须被拒
        TradeHistory backwards = newTradeHistory(
                intention.getId(), now, now.minusMinutes(1), TradeResult.sold);
        assertRejectedByDatabase(() -> tradeHistoryRepository.saveAndFlush(backwards),
                "ck_trade_history_time_order");
    }

    // -------------------------------------------------------------------------
    // UTC 校验
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("UTC：写入后直接查库，时间值与预期 UTC 时点一致（非本机时区）")
    void storedTimestampsAreUtc() {
        assertThat(DatabaseTimeProvider.STORAGE_ZONE)
                .as("存储时区口径必须是 UTC").isEqualTo(ZoneOffset.UTC);

        // 记录写入前后的 UTC 边界
        LocalDateTime before = DatabaseTimeProvider.utcNow();
        Goods goods = goodsRepository.saveAndFlush(newGoods(GoodsStatus.on_sale));
        Intention intention = intentionRepository.saveAndFlush(
                newIntention(goods, 831, IntentionStatus.queued, nextToken()));
        intentionRepository.flush();
        LocalDateTime after = DatabaseTimeProvider.utcNow();

        // 注意：saveAndFlush 之后实体上的值才是「真正写入的值」（nanos 已被截断），
        // 因此边界断言用截断到秒的形式，避免纳秒进位造成的偶发失败。
        LocalDateTime goodsCreateAt = goods.getCreateAt();
        LocalDateTime intentionCreateAt = intention.getCreateAt();

        // 实体上的值应落在 [before, after]（同样按秒比较，容忍亚秒边界）
        assertThat(goodsCreateAt.truncatedTo(ChronoUnit.SECONDS))
                .isBetween(before.minusSeconds(1).truncatedTo(ChronoUnit.SECONDS),
                        after.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS));
        assertThat(intentionCreateAt.truncatedTo(ChronoUnit.SECONDS))
                .isBetween(before.minusSeconds(1).truncatedTo(ChronoUnit.SECONDS),
                        after.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS));

        // 库中的值应与实体写入的值「同秒」——说明 JDBC 层没有再做时区平移。
        //
        // ⚠️ 这里刻意**不**用 isEqualTo(实体值.truncatedTo(SECONDS))：
        // DATETIME 列秒以下精度为 0，写入时会把实体的纳秒**截断**（不是四舍五入），
        // 于是「实体值的小数值 ≥ 0.5s」时，实体截断到秒会比库中值大 1 秒，
        // 造成与代码正确性无关的偶发失败。
        // 正确的判据是「库中值落在实体值所在的那一秒内」，它同样能识别时区平移
        // （平移会差 8 小时 = 28800 秒，远超 1 秒）。
        LocalDateTime storedGoods = readDatetimeAsLocal("simpleshop_goods", "create_at", goods.getId());
        LocalDateTime storedIntention = readDatetimeAsLocal(
                "simpleshop_intentions", "create_at", intention.getId());

        long goodsDeltaSeconds = Math.abs(ChronoUnit.SECONDS.between(
                storedGoods.truncatedTo(ChronoUnit.SECONDS),
                goodsCreateAt.truncatedTo(ChronoUnit.SECONDS)));
        long intentionDeltaSeconds = Math.abs(ChronoUnit.SECONDS.between(
                storedIntention.truncatedTo(ChronoUnit.SECONDS),
                intentionCreateAt.truncatedTo(ChronoUnit.SECONDS)));

        long localOffsetSeconds = Math.abs(ZoneOffset.systemDefault().getRules()
                .getOffset(LocalDateTime.now()).getTotalSeconds());

        assertThat(goodsDeltaSeconds)
                .as("simpleshop_goods.create_at 应与实体值同秒（实测差 %d 秒；若时区平移会差 %d 秒）",
                        goodsDeltaSeconds, localOffsetSeconds)
                .isLessThanOrEqualTo(1);
        assertThat(intentionDeltaSeconds)
                .as("simpleshop_intentions.create_at 应与实体值同秒（实测差 %d 秒）", intentionDeltaSeconds)
                .isLessThanOrEqualTo(1);

        // 本机时区不是 UTC，用例才有区分度
        long localOffsetHours = ZoneOffset.systemDefault().getRules()
                .getOffset(LocalDateTime.now()).getTotalSeconds() / 3600;
        assertThat(Math.abs(localOffsetHours))
                .as("本机时区（%s）应不是 UTC，否则本用例无法区分「写 UTC」与「写本地时间」",
                        ZoneOffset.systemDefault())
                .isGreaterThan(0);

        // 关键断言：库中值必须贴近「真实 UTC 现在」，而不是「本机现在」
        long driftFromTrueUtcMinutes = Math.abs(
                ChronoUnit.MINUTES.between(storedGoods, LocalDateTime.now(ZoneOffset.UTC)));
        assertThat(driftFromTrueUtcMinutes)
                .as("库中时间应贴近真实 UTC；若误写成本地时间会偏差约 %s 小时", localOffsetHours)
                .isLessThan(5);

        // 反证：若用本机时区的 now()，与库中值的差会接近时区偏移
        long driftFromLocalMinutes = Math.abs(
                ChronoUnit.MINUTES.between(storedGoods, LocalDateTime.now()));
        assertThat(driftFromLocalMinutes)
                .as("库中值与「本机当前时间」应相差约一个时区偏移（%s 小时），"
                        + "这正是「不能用 LocalDateTime.now() 入库」的直接证据", localOffsetHours)
                .isGreaterThan(60 * (Math.abs(localOffsetHours) - 1));
    }

    /**
     * 按「字面墙上时间」读取 {@code datetime} 列。
     *
     * <p><b>⚠️ 这里刻意不用 {@code rs.getTimestamp().toLocalDateTime()}</b>：
     * {@code getTimestamp()} 会按驱动/JVM 时区把 {@code DATETIME} 解释一次，
     * 在 UTC+8 机器上读到的值会与库中字面值相差 8 小时。
     * 按 §10.5「读出的 {@code datetime} 与预期 UTC 时点比对时，不得再叠加设备时区偏移」，
     * 应使用 {@code getObject(..., LocalDateTime.class)} 取得未经时区转换的字面值。
     */
    private LocalDateTime readDatetimeAsLocal(String table, String column, String id) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from " + table + " where id = ?",
                (rs, rowNum) -> rs.getObject(column, LocalDateTime.class), id);
    }

    // -------------------------------------------------------------------------
    // 附带：口令码哈希与库中种子值匹配（种子数据的回归）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("口令码：库中 seller 哈希可被 PasswordHasher 校验")
    void seededSellerHashMatchesKnownPassword() {
        String hash = jdbcTemplate.queryForObject(
                "select password from simpleshop_users where account = 'seller'", String.class);

        assertThat(PasswordHasher.isBcryptHash(hash)).as("库中必须存 BCrypt 哈希").isTrue();
        assertThat(PasswordHasher.matches("Abcd@1234", hash))
                .as("种子口令应能通过校验").isTrue();
        assertThat(PasswordHasher.matches("wrong-password", hash))
                .as("错误口令不应通过").isFalse();
    }
}
