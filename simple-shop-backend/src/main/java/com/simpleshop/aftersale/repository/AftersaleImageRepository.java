package com.simpleshop.aftersale.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.simpleshop.aftersale.entity.AftersaleImage;

/** 售后图片仓储：图片按售后单读，顺序按 sort_no，不分页。 */
public interface AftersaleImageRepository extends JpaRepository<AftersaleImage, String> {

    List<AftersaleImage> findByAftersale_IdOrderBySortNoAsc(String aftersaleId);

    long countByAftersale_Id(String aftersaleId);
}
