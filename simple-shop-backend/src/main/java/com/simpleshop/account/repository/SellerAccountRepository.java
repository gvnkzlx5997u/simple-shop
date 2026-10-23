package com.simpleshop.account.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.simpleshop.account.entity.SellerAccount;

public interface SellerAccountRepository extends JpaRepository<SellerAccount, String> {

    Optional<SellerAccount> findByAccount(String account);
}
