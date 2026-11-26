package com.simpleshop.address.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.address.entity.Address;

/**
 * 地址仓储。列表按「默认在前、再按创建时间倒序」，
 * 删除默认后取剩余里最新的一条补位也走同一排序。
 */
public interface AddressRepository extends JpaRepository<Address, String> {

    List<Address> findByBuyerIdOrderByDefaultFlagDescCreateAtDescIdDesc(String buyerId);

    Optional<Address> findByIdAndBuyerId(String id, String buyerId);

    long countByBuyerId(String buyerId);

    Optional<Address> findFirstByBuyerIdOrderByCreateAtDescIdDesc(String buyerId);

    /** 设默认前锁住当前买家全部地址，避免两次并发「设为默认」各清一半、最后由唯一索引报错。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Address a where a.buyerId = :buyerId")
    List<Address> findAllWithLockByBuyerId(@Param("buyerId") String buyerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Address a where a.id = :id")
    Optional<Address> findWithLockById(@Param("id") String id);
}
