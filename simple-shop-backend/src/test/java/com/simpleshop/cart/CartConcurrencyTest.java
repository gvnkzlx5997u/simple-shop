package com.simpleshop.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.testing.ConcurrencyTestBase;

/**
 * 购物车与收藏的并发用例。
 *
 * <p>串行跑不出来的是「同一行被两个请求同时删」：派生删除会先查再删，后一个在提交时发现
 * 行已不在而抛乐观锁异常，接口就变成 500。这里把四种「本应幂等」的动作都放到并发下跑一遍。
 */
class CartConcurrencyTest extends ConcurrencyTestBase {

    private static final String OK = "OK";

    @Test
    @DisplayName("两线程同时加购同一商品：只留一行，两边都不报错")
    void concurrentAddKeepsSingleRow() throws Exception {
        Goods goods = publishedGoods(5);
        BuyerAccount buyer = createBuyer();

        List<String> outcomes = runConcurrently(2,
                index -> () -> addOutcome(buyer.getId(), goods.getId()));

        assertThat(outcomes).containsExactly(OK, OK);
        assertThat(cartItems.findByBuyerIdOrderByCreateAtDescIdDesc(buyer.getId())).hasSize(1);
    }

    @Test
    @DisplayName("两线程同时转同一批：收藏只留一件，购物车被清空")
    void concurrentMoveToFavoritesMovesOnce() throws Exception {
        Goods goods = publishedGoods(5);
        BuyerAccount buyer = createBuyer();
        cart.add(buyer.getId(), goods.getId());

        List<String> outcomes = runConcurrently(2,
                index -> () -> moveOutcome(buyer.getId(), goods.getId()));

        // 一个真的转了过去（moved=1），另一个拿到 0：购物车行已经被前一个删掉。
        assertThat(outcomes).containsExactlyInAnyOrder("1", "0");
        assertThat(cartItems.findByBuyerIdOrderByCreateAtDescIdDesc(buyer.getId())).isEmpty();
        assertThat(favorites.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("两线程同时移除同一件：都返回成功，库里没有残留")
    void concurrentRemoveIsIdempotent() throws Exception {
        Goods goods = publishedGoods(5);
        BuyerAccount buyer = createBuyer();
        cart.add(buyer.getId(), goods.getId());

        List<String> outcomes = runConcurrently(2,
                index -> () -> removeOutcome(buyer.getId(), goods.getId()));

        assertThat(outcomes).containsExactly(OK, OK);
        assertThat(cartItems.findByBuyerIdOrderByCreateAtDescIdDesc(buyer.getId())).isEmpty();
    }

    @Test
    @DisplayName("两线程同时取消同一收藏：都返回成功，库里没有残留")
    void concurrentUnfavoriteIsIdempotent() throws Exception {
        Goods goods = publishedGoods(5);
        BuyerAccount buyer = createBuyer();
        favoriteService.add(buyer.getId(), goods.getId());

        List<String> outcomes = runConcurrently(2,
                index -> () -> unfavoriteOutcome(buyer.getId(), goods.getId()));

        assertThat(outcomes).containsExactly(OK, OK);
        assertThat(favorites.count()).isZero();
    }

    /** 失败时返回异常类名而不是笼统的 FAILED，断言挂掉时能直接看出是哪种异常。 */
    private String addOutcome(String buyerId, String goodsId) {
        return outcome(() -> cart.add(buyerId, goodsId));
    }

    private String removeOutcome(String buyerId, String goodsId) {
        return outcome(() -> cart.remove(buyerId, goodsId));
    }

    private String unfavoriteOutcome(String buyerId, String goodsId) {
        return outcome(() -> favoriteService.remove(buyerId, goodsId));
    }

    private String moveOutcome(String buyerId, String goodsId) {
        try {
            return String.valueOf(cartToFavorites.moveToFavorites(buyerId, List.of(goodsId)));
        } catch (RuntimeException exception) {
            return exception.getClass().getSimpleName();
        }
    }

    private static String outcome(Runnable action) {
        try {
            action.run();
            return OK;
        } catch (RuntimeException exception) {
            return exception.getClass().getSimpleName();
        }
    }
}
