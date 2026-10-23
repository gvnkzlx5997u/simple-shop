package com.simpleshop.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.simpleshop.common.config.AppProperties;

/**
 * 已校验的分页参数。页码从 1 开始；默认值与上限来自配置。
 */
public record PageQuery(int page, int pageSize) {

    public static PageQuery of(Integer page, Integer pageSize, AppProperties properties) {
        int resolvedPage = page == null ? 1 : page;
        int resolvedSize = pageSize == null
                ? properties.getPagination().getDefaultSize()
                : pageSize;
        if (resolvedPage < 1
                || resolvedSize < 1
                || resolvedSize > properties.getPagination().getMaxSize()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return new PageQuery(resolvedPage, resolvedSize);
    }

    public Pageable pageable() {
        return PageRequest.of(page - 1, pageSize);
    }
}
