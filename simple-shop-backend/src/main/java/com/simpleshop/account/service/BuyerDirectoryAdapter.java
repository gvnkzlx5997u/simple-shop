package com.simpleshop.account.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.simpleshop.account.api.BuyerDirectoryPort;
import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.account.repository.BuyerAccountRepository;

/**
 * 买家目录端口实现：一次批量查询，避免列表页按买家逐条读账号。
 */
@Component
public class BuyerDirectoryAdapter implements BuyerDirectoryPort {

    private final BuyerAccountRepository buyers;

    public BuyerDirectoryAdapter(BuyerAccountRepository buyers) {
        this.buyers = buyers;
    }

    @Override
    public Map<String, String> usernames(Collection<String> buyerIds) {
        if (buyerIds == null || buyerIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String buyerId : buyerIds) {
            if (buyerId != null && !buyerId.isBlank()) {
                ids.add(buyerId.trim());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>(ids.size());
        for (BuyerAccount buyer : buyers.findAllById(ids)) {
            result.put(buyer.getId(), buyer.getUsername());
        }
        return Map.copyOf(result);
    }
}
