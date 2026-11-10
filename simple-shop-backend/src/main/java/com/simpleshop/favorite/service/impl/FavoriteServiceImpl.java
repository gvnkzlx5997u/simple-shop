package com.simpleshop.favorite.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.id.Ids;
import com.simpleshop.common.text.UtcIso8601;
import com.simpleshop.common.time.DatabaseTimeProvider;
import com.simpleshop.favorite.api.FavoritePort;
import com.simpleshop.favorite.dto.FavoriteItemData;
import com.simpleshop.favorite.entity.Favorite;
import com.simpleshop.favorite.repository.FavoriteRepository;
import com.simpleshop.favorite.service.FavoriteService;
import com.simpleshop.goods.api.GoodsQueryPort;
import com.simpleshop.goods.api.GoodsSnapshot;
import com.simpleshop.goods.enums.GoodsStatus;

/**
 * 收藏服务。同时实现 {@link FavoritePort}，供购物车的转收藏调用；
 * 端口方法沿用调用方事务，不另开事务，否则「收藏写成功、购物车删失败」会留下半截状态。
 */
@Service
public class FavoriteServiceImpl implements FavoriteService, FavoritePort {

    private final FavoriteRepository favorites;
    private final GoodsQueryPort goodsQueryPort;
    private final OperationLogService operationLog;
    private final AppProperties properties;

    public FavoriteServiceImpl(
            FavoriteRepository favorites,
            GoodsQueryPort goodsQueryPort,
            OperationLogService operationLog,
            AppProperties properties) {
        this.favorites = favorites;
        this.goodsQueryPort = goodsQueryPort;
        this.operationLog = operationLog;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void add(String buyerId, String goodsId) {
        String id = requireGoodsId(goodsId);
        GoodsSnapshot goods = goodsQueryPort.require(id);
        if (goods.status() != GoodsStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.FAVORITE_GOODS_NOT_SELECTABLE);
        }
        boolean existed = favorites.existsByBuyerIdAndGoodsId(buyerId, id);
        favorites.insertIfAbsent(Ids.next("FV"), buyerId, id, DatabaseTimeProvider.utcNow());
        if (!existed) {
            operationLog.log(OperationLogService.ADD_FAVORITE, OperationLogService.TARGET_FAVORITE, id);
        }
    }

    @Override
    @Transactional
    public void remove(String buyerId, String goodsId) {
        String id = requireGoodsId(goodsId);
        // 取消收藏不校验商品状态：失效行也必须能删。
        int deleted = favorites.deleteByBuyerIdAndGoodsId(buyerId, id);
        if (deleted > 0) {
            operationLog.log(OperationLogService.REMOVE_FAVORITE, OperationLogService.TARGET_FAVORITE, id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FavoriteItemData> list(String buyerId, Integer page, Integer pageSize) {
        PageQuery query = PageQuery.of(page, pageSize, properties);
        Page<Favorite> rows = favorites.findByBuyerIdOrderByCreateAtDescIdDesc(buyerId, query.pageable());
        if (rows.isEmpty()) {
            return PageResponse.of(List.of(), query.page(), query.pageSize(), rows.getTotalElements());
        }
        Map<String, GoodsSnapshot> snapshots = goodsQueryPort.snapshotsOf(
                rows.stream().map(Favorite::getGoodsId).toList());
        List<FavoriteItemData> items = new ArrayList<>(rows.getNumberOfElements());
        for (Favorite row : rows) {
            GoodsSnapshot goods = snapshots.get(row.getGoodsId());
            if (goods == null) {
                continue;
            }
            items.add(new FavoriteItemData(
                    goods.id(),
                    goods.name(),
                    goods.price(),
                    goods.stock(),
                    goods.status(),
                    goods.offShelfReason(),
                    goods.mainImageUrl(),
                    goods.status() == GoodsStatus.ON_SALE,
                    UtcIso8601.of(row.getCreateAt())));
        }
        return PageResponse.of(items, query.page(), query.pageSize(), rows.getTotalElements());
    }

    @Override
    @Transactional
    public int addAll(String buyerId, Collection<String> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (String raw : goodsIds) {
            String id = raw == null ? "" : raw.trim();
            if (id.isEmpty() || favorites.existsByBuyerIdAndGoodsId(buyerId, id)) {
                continue;
            }
            favorites.insertIfAbsent(Ids.next("FV"), buyerId, id, DatabaseTimeProvider.utcNow());
            operationLog.log(OperationLogService.ADD_FAVORITE, OperationLogService.TARGET_FAVORITE, id);
            added++;
        }
        return added;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean favorited(String buyerId, String goodsId) {
        // 只问「收藏行在不在」，不校验商品是否存在：商品没了收藏行也仍然存在。
        return favorites.existsByBuyerIdAndGoodsId(buyerId, requireGoodsId(goodsId));
    }

    private static String requireGoodsId(String goodsId) {
        String id = goodsId == null ? "" : goodsId.trim();
        if (id.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return id;
    }
}
