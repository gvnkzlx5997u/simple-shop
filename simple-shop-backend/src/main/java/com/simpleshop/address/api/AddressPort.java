package com.simpleshop.address.api;

import java.util.Optional;

/**
 * 地址只读端口：由 address 实现，供下单取收货快照。
 *
 * <p>只读是刻意的：订单只在下单那一刻读一次地址，之后不再回查，
 * 所以这里没有「更新地址」「删除地址」这类写能力，避免把订单和地址簿耦合成引用关系。
 */
public interface AddressPort {

    /**
     * 取属于该买家的地址；不存在或不属于该买家都返回空。
     *
     * <p>两种情况的区分交给调用方：下单场景一律按「地址无效」处理，
     * 不区分「不存在」与「别人的地址」，避免被用来探测地址 ID。
     */
    Optional<AddressSnapshot> findOwned(String buyerId, String addressId);
}
