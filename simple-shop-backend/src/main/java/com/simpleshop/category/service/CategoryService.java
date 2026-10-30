package com.simpleshop.category.service;

import java.util.List;
import com.simpleshop.category.dto.CategoryData;
import com.simpleshop.category.dto.CategoryNodeData;
import com.simpleshop.category.dto.CategorySelection;
import com.simpleshop.category.entity.CategoryL2;

/**
 * 两级类别的新增、重命名与删除。
 */
public interface CategoryService {

    List<CategoryNodeData> tree();

    CategorySelection resolveSelection(String rawCategoryId);

    /** 过渡方法：返回实体以保留商品侧 JPA 关联，S3 改为类别快照端口。 */
    CategoryL2 requireLeaf(String rawCategoryId);

    CategoryData createL1(String rawName);

    CategoryData createL2(String l1Id, String rawName);

    CategoryData renameL1(String id, String rawName);

    CategoryData renameL2(String id, String rawName);

    void deleteL1(String id);

    void deleteL2(String id);
}
