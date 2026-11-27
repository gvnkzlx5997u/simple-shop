package com.simpleshop.aftersale.service;

import com.simpleshop.aftersale.dto.AftersaleCompleteRequest;
import com.simpleshop.aftersale.dto.AftersaleData;
import com.simpleshop.aftersale.dto.AftersaleDetailData;
import com.simpleshop.common.api.PageResponse;

/**
 * 卖家售后：查看全部、登记处理结果、取消。
 *
 * <p>卖家不能「先退款再改结果」：完成是终态，退款金额与时间在完成那一刻一次写定。
 */
public interface SellerAftersaleService {

    PageResponse<AftersaleData> list(
            String status, String orderNo, Integer page, Integer pageSize);

    AftersaleDetailData detail(String id);

    /** 登记结果并完成；退款类结果需要校验该订单项的退款额度。 */
    AftersaleData complete(String id, AftersaleCompleteRequest request);

    /** 取消（与拒绝同义）：已取消幂等返回，已完成返回 81002。 */
    AftersaleData cancel(String id);
}
