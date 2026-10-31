package com.simpleshop.account.service;

import com.simpleshop.account.dto.BuyerLoginRequest;
import com.simpleshop.account.dto.BuyerRegisterRequest;
import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.common.auth.SessionStore;

/**
 * 买家注册、登录与账号读取。
 */
public interface BuyerAuthService {

    void register(BuyerRegisterRequest request, String clientKey);

    SessionStore.Session login(BuyerLoginRequest request, String clientKey);

    /** 过渡方法：S3 改为 AccountQueryPort 后删除。 */
    BuyerAccount requireAccount(String id);
}
