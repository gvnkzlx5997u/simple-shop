import { request } from './http'
import type { CustomerDetailData, CustomerSummaryData, PageResponse } from './types'

const CUSTOMERS_PATH = '/seller/customers'

/** 客户列表；按注册时间倒序分页。 */
export function listCustomers(page = 1, pageSize = 10): Promise<PageResponse<CustomerSummaryData>> {
  return request<PageResponse<CustomerSummaryData>>({
    method: 'get',
    url: CUSTOMERS_PATH,
    params: { page, page_size: pageSize },
  })
}

/**
 * 客户详情；只有基本信息，不含购买历史（历史订单改查卖家订单列表的 buyer_id 筛选）。
 * 客户不存在返回 10009 + HTTP 404。
 */
export function getCustomerDetail(id: string): Promise<CustomerDetailData> {
  return request<CustomerDetailData>({
    method: 'get',
    url: CUSTOMERS_PATH + '/' + encodeURIComponent(id),
  })
}
