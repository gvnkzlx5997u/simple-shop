package com.simpleshop.testing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;

import com.simpleshop.persistence.time.DatabaseTimeProvider;

/**
 * 商品域的测试夹具：造数与清理（供卖家端与买家端的集成用例共用）。
 *
 * <h2>为什么用 JDBC 而不是走 Service／仓储</h2>
 * <p>集成用例需要构造一批<b>正常业务流程到不了的状态</b>，例如：
 * <ul>
 *   <li>{@code on_sale} 的商品却有 {@code trading} 意向（违反 {@code INV-04}，
 *       用来验证归档的 {@code INV-05} 兜底）；</li>
 *   <li>{@code frozen} + {@code trade} 的商品但没有对应意向；</li>
 *   <li>{@code succeeded}／{@code revoked} 等终态意向（正常流程要把它们做出来要走完整状态机）。</li>
 * </ul>
 * <p>用 Service 造这些状态要么做不到、要么让用例依赖被测代码本身（循环论证）。
 * 直接写库则「造什么就是什么」，且与 {@code DataLayerTestBase} 的既有做法一致。
 *
 * <h2>⚠️ {@link #cleanGoodsState()} 是必须的，不是可选的美化</h2>
 * <p>商品表<b>至多 1 行</b>（{@code INV-01}），而几乎所有查询都是
 * {@code findFirstByOrderByCreateAtAsc()}——只要库里残留任何一行商品，
 * 「当前商品」类用例就会作用在<b>别人</b>的数据上，用例成败将取决于测试类执行顺序
 * （单独跑绿、全量跑红）。故每个用例前后各清一次。
 *
 * <p>它只删商品域的 5 张表（按外键逆序），<b>不动</b>
 * {@code simpleshop_users}（种子账号）与 {@code simpleshop_queue_sequence} 的行——
 * 后者只把 {@code current_value} 复位，让各用例的 {@code queue_order} 互不影响。
 */
public final class ShopFixtures {

    /** 序号分配器：保证同一用例内 {@code queue_order} 与 {@code token} 不冲突（两者都有唯一约束）。 */
    private final AtomicInteger sequence = new AtomicInteger(1);

    private final JdbcTemplate jdbcTemplate;

    public ShopFixtures(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 把序号计数器复位到 1（与 {@link #cleanGoodsState()} 配对使用）。 */
    public void resetSequence() {
        sequence.set(1);
    }

    /**
     * 清空商品域的全部表（外键逆序）并复位队列序号。
     *
     * <p>{@code simpleshop_trade_history} 没有物理外键（{@code 9-N}），先删它最省心。
     */
    public void cleanGoodsState() {
        jdbcTemplate.update("delete from simpleshop_trade_history");
        jdbcTemplate.update("delete from simpleshop_intentions_history");
        jdbcTemplate.update("delete from simpleshop_intentions");
        jdbcTemplate.update("delete from simpleshop_goods_history");
        jdbcTemplate.update("delete from simpleshop_goods");
        jdbcTemplate.update("update simpleshop_queue_sequence set current_value = 0 where id = 1");
    }

    /**
     * 直接插一行商品。
     *
     * @param status   库中状态代码（{@code on_sale}／{@code frozen}）
     * @param freezeBy 冻结来源代码（{@code manual}／{@code trade}）；{@code on_sale} 时传 {@code null}
     * @param name     商品名称（建议带标记，便于排查）
     * @param price    价格字符串
     * @return 新商品 ID
     */
    public String insertGoods(String status, String freezeBy, String name, String price) {
        String id = "GTEST" + String.format("%04d", sequence.getAndIncrement());
        LocalDateTime now = DatabaseTimeProvider.utcNow();
        jdbcTemplate.update("insert into simpleshop_goods "
                        + "(id,name,description,pic_url,price,status,freeze_by,create_at,update_at,"
                        + "trade_start,trade_end,result) "
                        + "values (?,?,?,?,?,?,?,?,?,NULL,NULL,NULL)",
                id, name, null, null, new BigDecimal(price), status, freezeBy, now, now);
        return id;
    }

    /**
     * 直接插一条意向，自动分配 {@code queue_order} 与口令码。
     *
     * @param goodsId 所属商品 ID
     * @param status  库中状态代码（{@code queued}／{@code trading}／{@code succeeded}／{@code failed}／{@code revoked}）
     * @return 新意向 ID
     */
    public String insertIntention(String goodsId, String status) {
        int seq = sequence.getAndIncrement();
        return insertIntention(goodsId, seq, status, tokenFor(seq));
    }

    /**
     * 直接插一条意向（显式指定序号与口令码）。
     *
     * @param queueOrder 排序序号（全库唯一）
     * @param token      口令码（全库唯一；列宽 {@code varchar(20)}，契约形态为 12 位大写字母+数字）
     */
    public String insertIntention(String goodsId, int queueOrder, String status, String token) {
        // ⚠️ id 用独立的自增计数而不是「queue_order + 状态首字母」拼出来：
        //    后者在「同一 queue_order 配不同状态」时会撞主键（例如先插 queued 再插 succeeded），
        //    而用例里恰恰经常这么造数。
        String id = "ITEST" + String.format("%05d", sequence.getAndIncrement());
        jdbcTemplate.update("insert into simpleshop_intentions "
                        + "(id,fk_good_id,queue_order,create_at,name,tel,status,fail_type,fail_reason,token) "
                        + "values (?,?,?,?,?,?,?,NULL,NULL,?)",
                id, goodsId, queueOrder, DatabaseTimeProvider.utcNow(),
                "GDS-买家", "13800000000", status, token);
        syncQueueSequence(queueOrder);
        return id;
    }

    /**
     * 批量插 {@code count} 条同一状态的意向（用于「队列达上限」这类需要造量的用例）。
     *
     * <p>用 {@code batchUpdate} 而不是循环 {@code update}：1000 条单条往返会明显拖慢构建。
     *
     * @param status 状态代码
     * @return 实际插入条数
     */
    public int insertIntentions(String goodsId, int count, String status) {
        LocalDateTime now = DatabaseTimeProvider.utcNow();
        java.util.List<Object[]> batch = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int seq = sequence.getAndIncrement();
            batch.add(new Object[]{
                    "ITESTB" + String.format("%05d", seq), goodsId, seq, now,
                    "GDS-买家", "13800000000", status, tokenFor(seq)});
        }
        int[] affected = jdbcTemplate.batchUpdate(
                "insert into simpleshop_intentions "
                        + "(id,fk_good_id,queue_order,create_at,name,tel,status,fail_type,fail_reason,token) "
                        + "values (?,?,?,?,?,?,?,NULL,NULL,?)", batch);
        syncQueueSequence(sequence.get() - 1);
        return affected.length;
    }

    /**
     * 让 {@code simpleshop_queue_sequence.current_value} 不小于已造出的最大 {@code queue_order}。
     *
     * <h2>⚠️ 这不是「顺手加个保险」，而是在修复夹具对不变量的破坏</h2>
     * <p>{@code QueueSequence} 的口径是「{@code currentValue} = <b>已分配的最大</b> {@code queue_order}，
     * 只增不减」。而本类直接写库造数时，序号表仍是 0——于是被测代码一旦走「重新排队」分支
     * （{@code markTradeFailure(requeued)} 会取 {@code current_value + 1} 当新序号），
     * 分到的就是 {@code 1}，与夹具已经造出的 {@code queue_order = 1} <b>撞唯一索引</b>，
     * 用例会以一个与被测逻辑无关的 {@code Duplicate entry} 失败。
     *
     * <p>同步一次之后，夹具造出的数据就与「真实流程跑出来」的状态一致：
     * 序号表始终是「所有已发放序号的最大值」。这也让 {@code submitIntention} 在本夹具之后
     * 继续分配时不会与既有值冲突。
     */
    private void syncQueueSequence(int maxQueueOrder) {
        jdbcTemplate.update(
                "update simpleshop_queue_sequence set current_value = ? "
                        + "where id = 1 and current_value < ?", maxQueueOrder, maxQueueOrder);
    }

    /** 生成第 {@code n} 个口令码：12 位、{@code [A-Z0-9]}，与契约形态一致（便于顺带验证格式）。 */
    public static String tokenFor(int n) {
        return "T" + String.format("%011d", n);
    }

    /** 取一行商品（列名原样，便于逐列核对）。 */
    public Map<String, Object> goodsRow(String id) {
        return jdbcTemplate.queryForMap("select * from simpleshop_goods where id = ?", id);
    }

    /** 取一行意向（列名原样）。 */
    public Map<String, Object> intentionRow(String id) {
        return jdbcTemplate.queryForMap("select * from simpleshop_intentions where id = ?", id);
    }

    /** 取一行<b>历史</b>意向（归档后的行；列名原样，注意没有 {@code queue_order}／{@code token}）。 */
    public Map<String, Object> intentionRowInHistory(String id) {
        return jdbcTemplate.queryForMap("select * from simpleshop_intentions_history where id = ?", id);
    }

    /** 取一行<b>历史</b>商品（列名原样）。 */
    public Map<String, Object> goodsHistoryRow(String id) {
        return jdbcTemplate.queryForMap("select * from simpleshop_goods_history where id = ?", id);
    }

    /**
     * 取某意向的<b>唯一</b>一条流水（列名原样）。
     *
     * <p>⚠️ 只在「确定恰好 1 条」的断言里用：多于 1 条时 {@code queryForMap} 会抛
     * {@code IncorrectResultSizeDataAccessException}，那是用例的前提写错了，应当响亮失败。
     */
    public Map<String, Object> tradeFlow(String intentionId) {
        return jdbcTemplate.queryForMap(
                "select * from simpleshop_trade_history where fk_intention_id = ?", intentionId);
    }

    /** 取一行意向的口令码（⚠️ 仅用于测试断言，生产代码不得这样把口令码取出来打印）。 */
    public String tokenOf(String intentionId) {
        return jdbcTemplate.queryForObject(
                "select token from simpleshop_intentions where id = ?", String.class, intentionId);
    }

    /** 单值计数。 */
    public int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /** 当前商品行数。 */
    public int countGoods() {
        return count("select count(*) from simpleshop_goods");
    }

    /** 当前意向行数。 */
    public int countIntentions() {
        return count("select count(*) from simpleshop_intentions");
    }
}
