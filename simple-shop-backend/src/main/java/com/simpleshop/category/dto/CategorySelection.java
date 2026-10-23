package com.simpleshop.category.dto;

/**
 * 类别筛选结果：level 为 0 表示不筛选，1 表示一级类别，2 表示二级类别。
 */
public record CategorySelection(String id, int level) {

    public static CategorySelection none() {
        return new CategorySelection(null, 0);
    }
}
