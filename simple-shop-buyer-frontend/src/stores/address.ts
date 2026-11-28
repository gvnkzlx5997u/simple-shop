import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import * as api from '../api/address'
import type { AddressData, AddressRequest } from '../api/types'
import { describeError } from '../utils/errorText'

/** 后端上限；到了就禁用新建入口，不等提交后吃 80002。 */
export const MAX_ADDRESSES = 10

/**
 * 地址簿。结算页与地址管理页共用这一份，避免两处各拉一次列表。
 *
 * 默认地址的归属完全由后端决定（首条自动默认、设默认时清旧的、删默认后最新一条补位），
 * 所以每个写动作之后都重拉列表，不在本地推算谁是默认。
 */
export const useAddressStore = defineStore('address', () => {
  const items = ref<AddressData[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const errorText = ref('')

  const defaultAddress = computed<AddressData | null>(
    () => items.value.find((a) => a.is_default) ?? null,
  )
  const full = computed(() => items.value.length >= MAX_ADDRESSES)

  async function load(): Promise<void> {
    loading.value = true
    errorText.value = ''
    try {
      const data = await api.fetchAddresses()
      // 顺序沿用后端：默认在前，其余按创建时间倒序，前端不重排。
      items.value = data?.items ?? []
      loaded.value = true
    } catch (err) {
      errorText.value = describeError(err)
    } finally {
      loading.value = false
    }
  }

  /** 写动作一律把错误抛给页面，由页面决定提示文案；列表加载失败才落到 errorText。 */
  async function create(payload: AddressRequest): Promise<AddressData> {
    const created = await api.createAddress(payload)
    await load()
    return created
  }

  async function update(id: string, payload: AddressRequest): Promise<AddressData> {
    const updated = await api.updateAddress(id, payload)
    await load()
    return updated
  }

  async function remove(id: string): Promise<void> {
    await api.removeAddress(id)
    await load()
  }

  async function setDefault(id: string): Promise<AddressData> {
    const updated = await api.setDefaultAddress(id)
    await load()
    return updated
  }

  function reset(): void {
    items.value = []
    loaded.value = false
    errorText.value = ''
  }

  return {
    items,
    loading,
    loaded,
    errorText,
    defaultAddress,
    full,
    load,
    create,
    update,
    remove,
    setDefault,
    reset,
  }
})