package com.simpleshop.account.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.simpleshop.account.entity.BuyerAccount;

public interface BuyerAccountRepository extends JpaRepository<BuyerAccount, String> {

    Optional<BuyerAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByPhone(String phone);

    /** 卖家客户列表；注册时间秒级精度，用 id 做 tie-breaker。 */
    Page<BuyerAccount> findAllByOrderByCreateAtDescIdDesc(Pageable pageable);
}
