package com.simpleshop.goods.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;

public interface GoodsRepository extends JpaRepository<Goods, String> {

    Page<Goods> findByStatus(GoodsStatus status, Pageable pageable);

    Page<Goods> findByStatusAndCategory_Id(GoodsStatus status, String categoryId, Pageable pageable);

    boolean existsByCategory_Id(String categoryId);

    @EntityGraph(attributePaths = {"category", "category.l1"})
    @Query("select g from Goods g where g.id = :id")
    Optional<Goods> findDetailById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Goods g where g.id = :id")
    Optional<Goods> findWithLockById(@Param("id") String id);

    /**
     * 直接删除当前商品行，绕过一级缓存（实体引用会阻断 em.remove 的 flush）。
     * 归档流程专用：意向外键 RESTRICT，必须先删该商品全部意向行；
     * 图片、描述块、队列序号行由数据库级联删除。
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Goods g where g.id = :id")
    void deleteRowById(@Param("id") String id);

    /**
     * 卖家/公共商品列表共用查询。categoryLevel 为 0 表示不按类别筛选，
     * 为 1 时 categoryId 是一级类别，为 2 时是二级类别。
     */
    @Query(value = """
            select g from Goods g
            join fetch g.category c
            join fetch c.l1 l1
            where (:status is null or g.status = :status)
              and (:categoryLevel = 0
                   or (:categoryLevel = 1 and l1.id = :categoryId)
                   or (:categoryLevel = 2 and c.id = :categoryId))
              and (:keyword is null or lower(g.name) like lower(concat('%', :keyword, '%')))
            order by g.createAt desc, g.id desc
            """,
            countQuery = """
            select count(g) from Goods g
            where (:status is null or g.status = :status)
              and (:categoryLevel = 0
                   or (:categoryLevel = 1 and g.category.l1.id = :categoryId)
                   or (:categoryLevel = 2 and g.category.id = :categoryId))
              and (:keyword is null or lower(g.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<Goods> search(
            @Param("status") GoodsStatus status,
            @Param("categoryId") String categoryId,
            @Param("categoryLevel") int categoryLevel,
            @Param("keyword") String keyword,
            Pageable pageable);
}
