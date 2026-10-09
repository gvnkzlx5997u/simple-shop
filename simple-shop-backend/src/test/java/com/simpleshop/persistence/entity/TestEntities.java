package com.simpleshop.persistence.entity;

/**
 * <b>仅测试使用</b>的实体构造工厂。
 *
 * <h2>为什么需要它</h2>
 * <p>按 §6.2.1，所有实体的无参构造都是 {@code protected}（JPA 规范要求，且避免业务代码误用），
 * 于是「能在测试里 new 出实体」的前提是测试类与被测实体<b>同包</b>。
 * 若把各个测试类都塞进 {@code com.simpleshop.persistence.entity} 包，
 * 测试的组织结构就被生产代码的包布局绑架了。
 *
 * <p>因此改为：<b>仅本类</b>放在实体包内，对外暴露 {@code public static} 工厂方法；
 * 任何包的测试都能借助它构造实体，同时<b>生产代码的可见性约束原样保留</b>
 * （生产包内不新增任何 public 构造）。
 *
 * <p><b>⚠️ 本类位于 {@code src/test/java}，不会进入生产构件。</b>
 * 它只用来「让实体可构造」，不放任何断言逻辑。
 */
public final class TestEntities {

    private TestEntities() {
    }

    public static Goods newGoods() {
        return new Goods();
    }

    public static Intention newIntention() {
        return new Intention();
    }

    public static GoodsHistory newGoodsHistory() {
        return new GoodsHistory();
    }

    public static IntentionHistory newIntentionHistory() {
        return new IntentionHistory();
    }

    public static TradeHistory newTradeHistory() {
        return new TradeHistory();
    }

    public static ShopUser newShopUser() {
        return new ShopUser();
    }
}
