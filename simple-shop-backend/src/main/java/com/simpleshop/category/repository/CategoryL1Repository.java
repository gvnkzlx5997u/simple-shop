package com.simpleshop.category.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.category.entity.CategoryL1;

public interface CategoryL1Repository extends JpaRepository<CategoryL1, String> {

    Optional<CategoryL1> findByName(String name);

    List<CategoryL1> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CategoryL1 c where c.id = :id")
    Optional<CategoryL1> findWithLockById(@Param("id") String id);
}
