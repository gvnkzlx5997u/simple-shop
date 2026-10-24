package com.simpleshop.category.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.category.entity.CategoryL2;

public interface CategoryL2Repository extends JpaRepository<CategoryL2, String> {

    List<CategoryL2> findByL1_IdOrderByNameAsc(String l1Id);

    Optional<CategoryL2> findByL1_IdAndName(String l1Id, String name);

    boolean existsByL1_Id(String l1Id);

    @Query("select c from CategoryL2 c join fetch c.l1 where c.id = :id")
    Optional<CategoryL2> findWithL1ById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CategoryL2 c where c.id = :id")
    Optional<CategoryL2> findWithLockById(@Param("id") String id);

    @Query("select c from CategoryL2 c join fetch c.l1 order by c.l1.name asc, c.name asc")
    List<CategoryL2> findAllWithL1Ordered();
}
