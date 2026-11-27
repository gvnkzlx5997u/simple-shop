package com.simpleshop.aftersale.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simpleshop.aftersale.dto.AftersaleAppliedData;
import com.simpleshop.aftersale.dto.AftersaleApplyRequest;
import com.simpleshop.aftersale.dto.AftersaleData;
import com.simpleshop.aftersale.dto.AftersaleDetailData;
import com.simpleshop.aftersale.entity.Aftersale;
import com.simpleshop.aftersale.entity.AftersaleImage;
import com.simpleshop.aftersale.enums.AftersaleStatus;
import com.simpleshop.aftersale.repository.AftersaleImageRepository;
import com.simpleshop.aftersale.repository.AftersaleRepository;
import com.simpleshop.aftersale.service.AftersaleAssembler;
import com.simpleshop.aftersale.service.BuyerAftersaleService;
import com.simpleshop.aftersale.support.AftersaleNoGenerator;
import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.api.PageQuery;
import com.simpleshop.common.api.PageResponse;
import com.simpleshop.common.audit.OperationLogService;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.media.MediaPurpose;
import com.simpleshop.common.media.MediaStorageService;
import com.simpleshop.common.text.TextRules;
import com.simpleshop.order.api.AftersaleTarget;
import com.simpleshop.order.api.OrderTradePort;
import com.simpleshop.order.enums.OrderStatus;

/**
 * 买家售后实现。
 *
 * <p>三步顺序固定：先用订单端口确认「这个订单项能不能售后」，再校验标题描述，最后回验图片 URL。
 * 把内容校验放在订单判断之后，是为了让「订单不可售后」优先于「描述写错了」——
 * 前者是用户无法自行修复的，后者可以先告诉他改。
 *
 * <p>图片 URL 必须由本服务为该用途落盘：申请只接受上传接口返回的地址，
 * 外链与跨用途地址都会被媒体服务拒绝。
 */
@Service
public class BuyerAftersaleServiceImpl implements BuyerAftersaleService {

    private static final int MAX_IMAGES = 9;
    private static final int TITLE_MAX = 100;
    private static final int DESCRIPTION_MAX = 1000;

    private final AftersaleRepository aftersales;
    private final AftersaleImageRepository images;
    private final OrderTradePort orders;
    private final AftersaleNoGenerator numbers;
    private final MediaStorageService media;
    private final OperationLogService audit;
    private final AftersaleAssembler assembler;
    private final AppProperties properties;

    public BuyerAftersaleServiceImpl(
            AftersaleRepository aftersales,
            AftersaleImageRepository images,
            OrderTradePort orders,
            AftersaleNoGenerator numbers,
            MediaStorageService media,
            OperationLogService audit,
            AftersaleAssembler assembler,
            AppProperties properties) {
        this.aftersales = aftersales;
        this.images = images;
        this.orders = orders;
        this.numbers = numbers;
        this.media = media;
        this.audit = audit;
        this.assembler = assembler;
        this.properties = properties;
    }

    @Override
    @Transactional
    public AftersaleAppliedData apply(String buyerId, AftersaleApplyRequest request) {
        AftersaleTarget target = orders
                .findAftersaleTarget(buyerId, request == null ? null : request.orderItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AFTERSALE_NOT_APPLICABLE));
        if (target.orderStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.AFTERSALE_NOT_APPLICABLE,
                    "only completed orders can apply for aftersale");
        }

        String title = requireTitle(request.title());
        String description = requireDescription(request.description());
        List<String> urls = requireImages(request.imageUrls());

        Aftersale aftersale = aftersales.saveAndFlush(Aftersale.apply(
                numbers.next(),
                target.orderId(),
                target.orderItemId(),
                buyerId,
                title,
                description));

        List<AftersaleImage> rows = new ArrayList<>(urls.size());
        for (int index = 0; index < urls.size(); index++) {
            rows.add(AftersaleImage.add(aftersale, urls.get(index), index + 1));
        }
        if (!rows.isEmpty()) {
            images.saveAll(rows);
        }

        audit.log(OperationLogService.AFTERSALE_APPLY,
                OperationLogService.TARGET_AFTERSALE, aftersale.getId());
        return assembler.applied(aftersale);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AftersaleData> list(
            String buyerId, String status, String orderId, Integer page, Integer pageSize) {
        PageQuery pageQuery = PageQuery.of(page, pageSize, properties);
        Page<Aftersale> result = aftersales.search(
                buyerId,
                parseStatus(status),
                TextRules.trimToNull(orderId),
                pageQuery.pageable());
        List<AftersaleData> items = result.getContent().stream()
                .map(assembler::summary)
                .toList();
        return PageResponse.of(
                items, pageQuery.page(), pageQuery.pageSize(), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AftersaleDetailData detail(String buyerId, String id) {
        Aftersale aftersale = aftersales.findByIdAndBuyerId(trim(id), buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AFTERSALE_NOT_FOUND));
        return assembler.detail(aftersale);
    }

    private static AftersaleStatus parseStatus(String raw) {
        String value = TextRules.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return AftersaleStatus.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }

    private static String requireTitle(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()
                || TextRules.charCount(value) > TITLE_MAX
                || TextRules.containsLineBreak(value)
                || TextRules.containsControlChar(value)
                || TextRules.containsAngleBracket(value)) {
            throw new BusinessException(ErrorCode.AFTERSALE_CONTENT_INVALID, "title is invalid");
        }
        return value;
    }

    /** 描述允许多行：买家常把情况分几条写；仍然拒绝尖括号与奇怪的控制字符。 */
    private static String requireDescription(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()
                || TextRules.charCount(value) > DESCRIPTION_MAX
                || TextRules.containsAngleBracket(value)
                || TextRules.containsDisallowedControlChar(value)) {
            throw new BusinessException(
                    ErrorCode.AFTERSALE_CONTENT_INVALID, "description is invalid");
        }
        return value;
    }

    /** 图片只接受本服务为该用途落盘的地址；数量上限 9 张。 */
    private List<String> requireImages(List<String> raw) {
        List<String> urls = raw == null ? List.of() : raw;
        if (urls.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.AFTERSALE_IMAGE_LIMIT_EXCEEDED);
        }
        for (String url : urls) {
            media.verifyStoredUrl(url, MediaPurpose.AFTERSALE_IMAGE);
        }
        return urls;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
