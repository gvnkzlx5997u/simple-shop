package com.simpleshop.account.api;

import java.util.Collection;
import java.util.Map;

/**
 * 买家目录只读端口：由 account 实现，供订单等下游按买家 ID 取用户名。
 *
 * <p>订单只存买家 ID，展示时实时读账号，账号改名后订单列表跟着变；
 * 不存在的 ID 不出现在返回结果里，由调用方决定如何展示。
 */
public interface BuyerDirectoryPort {

    /** 批量取用户名：买家 ID → 用户名。 */
    Map<String, String> usernames(Collection<String> buyerIds);
}
