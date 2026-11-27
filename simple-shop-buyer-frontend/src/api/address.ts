import { del, get, post, put } from './http'
import type { AddressData, AddressListData, AddressRequest } from './types'

/**
 * 买家地址簿。归属由令牌决定，路径只带地址 id；他人地址按「不存在」返回（80001）。
 *
 * 上限 10 条、第一条自动默认、删默认后由剩余最新一条补位，都由后端保证，前端不自行推算。
 */

/** 地址列表：不分页，默认在前。 */
export function fetchAddresses(): Promise<AddressListData> {
  return get<AddressListData>('/buyer/addresses')
}

export function createAddress(payload: AddressRequest): Promise<AddressData> {
  return post<AddressData>('/buyer/addresses', payload)
}

/** 编辑地址。**不改默认标记**，入参也不含该字段（多传会被后端拒收）。 */
export function updateAddress(id: string, payload: AddressRequest): Promise<AddressData> {
  return put<AddressData>('/buyer/addresses/' + encodeURIComponent(id), payload)
}

export function removeAddress(id: string): Promise<null> {
  return del<null>('/buyer/addresses/' + encodeURIComponent(id))
}

/** 设为默认；原默认由后端自动取消，前端不做两次请求。 */
export function setDefaultAddress(id: string): Promise<AddressData> {
  return post<AddressData>('/buyer/addresses/' + encodeURIComponent(id) + '/default')
}