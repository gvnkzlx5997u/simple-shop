package com.simpleshop.category.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 类别树节点。一级节点包含二级子节点，二级节点 children 为空数组。
 */
public record CategoryNodeData(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("children") List<CategoryNodeData> children) {

    public CategoryNodeData {
        children = children == null ? List.of() : List.copyOf(children);
    }

    public static CategoryNodeData leaf(String id, String name) {
        return new CategoryNodeData(id, name, List.of());
    }
}
