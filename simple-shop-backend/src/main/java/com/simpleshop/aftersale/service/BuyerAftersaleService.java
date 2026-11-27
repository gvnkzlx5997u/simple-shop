package com.simpleshop.aftersale.service;

import com.simpleshop.aftersale.dto.AftersaleAppliedData;
import com.simpleshop.aftersale.dto.AftersaleApplyRequest;
import com.simpleshop.aftersale.dto.AftersaleData;
import com.simpleshop.aftersale.dto.AftersaleDetailData;
import com.simpleshop.common.api.PageResponse;

/**
 * 买家售后：提交申请、我的售后列表、详情。买家不能取消或处理售后单。
 *
 * <p>申请不拦截「该订单项是否已退款」：换货之后仍可再申请，是否受理由卖家线下判断；
 * 只有「完成并退款」时才校验同一订单项的退款额度。
 */
public interface BuyerAftersaleService {

    AftersaleAppliedData apply(String buyerId, AftersaleApplyRequest request);

    PageResponse<AftersaleData> list(
            String buyerId, String status, String orderId, Integer page, Integer pageSize);

    AftersaleDetailData detail(String buyerId, String id);
}
