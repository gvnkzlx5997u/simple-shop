package com.simpleshop.category.service.impl;

import com.simpleshop.category.service.CategoryService;
import com.simpleshop.category.api.CategoryGoodsReferencePort;
import com.simpleshop.category.service.CategoryRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.category.dto.CategoryData;
import com.simpleshop.category.dto.CategoryNodeData;
import com.simpleshop.category.dto.CategorySelection;
import com.simpleshop.category.entity.CategoryL1;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.category.repository.CategoryL1Repository;
import com.simpleshop.category.repository.CategoryL2Repository;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.audit.OperationLogService;

/**
 * 两级类别的新增、重命名与删除。一级只做分组，商品只能引用二级类别。
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryL1Repository l1s;
    private final CategoryL2Repository l2s;
    private final CategoryGoodsReferencePort goodsReferences;
    private final OperationLogService audit;

    public CategoryServiceImpl(
            CategoryL1Repository l1s,
            CategoryL2Repository l2s,
            CategoryGoodsReferencePort goodsReferences,
            OperationLogService audit) {
        this.l1s = l1s;
        this.l2s = l2s;
        this.goodsReferences = goodsReferences;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryNodeData> tree() {
        List<CategoryL1> firstLevel = l1s.findAllByOrderByNameAsc();
        Map<String, List<CategoryNodeData>> children = new LinkedHashMap<>();
        for (CategoryL1 category : firstLevel) {
            children.put(category.getId(), new ArrayList<>());
        }
        for (CategoryL2 category : l2s.findAllWithL1Ordered()) {
            children.computeIfAbsent(category.getL1().getId(), key -> new ArrayList<>())
                    .add(CategoryNodeData.leaf(category.getId(), category.getName()));
        }

        List<CategoryNodeData> result = new ArrayList<>(firstLevel.size());
        for (CategoryL1 category : firstLevel) {
            result.add(new CategoryNodeData(
                    category.getId(),
                    category.getName(),
                    children.getOrDefault(category.getId(), List.of())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public CategorySelection resolveSelection(String rawCategoryId) {
        String value = rawCategoryId == null ? null : rawCategoryId.trim();
        if (value == null || value.isEmpty()) {
            return CategorySelection.none();
        }
        if (l2s.existsById(value)) {
            return new CategorySelection(value, 2);
        }
        if (l1s.existsById(value)) {
            return new CategorySelection(value, 1);
        }
        throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
    }

    /**
     * 商品发布/改挂时解析目标二级类别；一级类别返回 CATEGORY_NOT_LEAF。
     */
    @Transactional(readOnly = true)
    public CategoryL2 requireLeaf(String rawCategoryId) {
        String categoryId = requireId(rawCategoryId);
        CategoryL2 category = l2s.findWithL1ById(categoryId).orElse(null);
        if (category != null) {
            return category;
        }
        if (l1s.existsById(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_LEAF);
        }
        throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Transactional
    public CategoryData createL1(String rawName) {
        String name = CategoryRules.name(rawName);
        if (l1s.findByName(name).isPresent()) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }
        CategoryL1 category = CategoryL1.create(name);
        try {
            l1s.saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }
        audit.log(OperationLogService.CREATE_CATEGORY,
                OperationLogService.TARGET_CATEGORY, category.getId());
        return CategoryData.of(category);
    }

    @Transactional
    public CategoryData createL2(String l1Id, String rawName) {
        String name = CategoryRules.name(rawName);
        CategoryL1 parent = l1s.findWithLockById(requireId(l1Id))
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (l2s.findByL1_IdAndName(parent.getId(), name).isPresent()) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }
        CategoryL2 category = CategoryL2.create(parent, name);
        try {
            l2s.saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }
        audit.log(OperationLogService.CREATE_CATEGORY,
                OperationLogService.TARGET_CATEGORY, category.getId());
        return CategoryData.of(category);
    }

    @Transactional
    public CategoryData renameL1(String id, String rawName) {
        String name = CategoryRules.name(rawName);
        CategoryL1 category = l1s.findWithLockById(requireId(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!category.getName().equals(name)) {
            l1s.findByName(name).filter(other -> !other.getId().equals(category.getId()))
                    .ifPresent(other -> {
                        throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
                    });
            category.rename(name);
            try {
                l1s.saveAndFlush(category);
            } catch (DataIntegrityViolationException exception) {
                throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
            }
            audit.log(OperationLogService.RENAME_CATEGORY,
                    OperationLogService.TARGET_CATEGORY, category.getId());
        }
        return CategoryData.of(category);
    }

    @Transactional
    public CategoryData renameL2(String id, String rawName) {
        String name = CategoryRules.name(rawName);
        CategoryL2 category = l2s.findWithLockById(requireId(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!category.getName().equals(name)) {
            l2s.findByL1_IdAndName(category.getL1().getId(), name)
                    .filter(other -> !other.getId().equals(category.getId()))
                    .ifPresent(other -> {
                        throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
                    });
            category.rename(name);
            try {
                l2s.saveAndFlush(category);
            } catch (DataIntegrityViolationException exception) {
                throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
            }
            audit.log(OperationLogService.RENAME_CATEGORY,
                    OperationLogService.TARGET_CATEGORY, category.getId());
        }
        return CategoryData.of(category);
    }

    @Transactional
    public void deleteL1(String id) {
        CategoryL1 category = l1s.findWithLockById(requireId(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (l2s.existsByL1_Id(category.getId())) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        try {
            l1s.delete(category);
            l1s.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        audit.log(OperationLogService.DELETE_CATEGORY,
                OperationLogService.TARGET_CATEGORY, category.getId());
    }

    @Transactional
    public void deleteL2(String id) {
        CategoryL2 category = l2s.findWithLockById(requireId(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (goodsReferences.existsByCategoryId(category.getId())) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
        }
        try {
            l2s.delete(category);
            l2s.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
        }
        audit.log(OperationLogService.DELETE_CATEGORY,
                OperationLogService.TARGET_CATEGORY, category.getId());
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return id.trim();
    }
}
