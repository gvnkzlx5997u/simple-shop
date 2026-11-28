import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as api from '../../src/api/address'
import { ApiError } from '../../src/api/http'
import { ApiCode } from '../../src/api/types'
import type { AddressData } from '../../src/api/types'
import { MAX_ADDRESSES, useAddressStore } from '../../src/stores/address'

vi.mock('../../src/api/address', () => ({
  fetchAddresses: vi.fn(),
  createAddress: vi.fn(),
  updateAddress: vi.fn(),
  removeAddress: vi.fn(),
  setDefaultAddress: vi.fn(),
}))

function addr(over: Partial<AddressData> = {}): AddressData {
  return {
    id: 'a1',
    receiver_name: '张三',
    receiver_phone: '13800138000',
    detail_address: '中关村大街 1 号',
    is_default: false,
    create_at: '2026-11-20T10:00:00Z',
    update_at: '2026-11-20T10:00:00Z',
    ...over,
  }
}

const payload = {
  receiver_name: '李四',
  receiver_phone: '13900139000',
  detail_address: '望京街 2 号',
}

let store: ReturnType<typeof useAddressStore>

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  store = useAddressStore()
  vi.mocked(api.fetchAddresses).mockResolvedValue({ items: [] })
  vi.mocked(api.createAddress).mockResolvedValue(addr())
  vi.mocked(api.updateAddress).mockResolvedValue(addr())
  vi.mocked(api.removeAddress).mockResolvedValue(null)
  vi.mocked(api.setDefaultAddress).mockResolvedValue(addr({ is_default: true }))
})

describe('load', () => {
  it('成功：沿用后端顺序，不重排', async () => {
    vi.mocked(api.fetchAddresses).mockResolvedValue({
      items: [addr({ id: 'a2', is_default: true }), addr({ id: 'a1' })],
    })
    await store.load()
    expect(store.items.map((a) => a.id)).toEqual(['a2', 'a1'])
    expect(store.loaded).toBe(true)
    expect(store.errorText).toBe('')
  })

  it('失败：落 errorText，不抛给页面', async () => {
    vi.mocked(api.fetchAddresses).mockRejectedValueOnce(new ApiError(ApiCode.SESSION_INVALID, 'x'))
    await store.load()
    expect(store.errorText).toBe('登录已失效，请重新登录')
    expect(store.items).toEqual([])
  })
})

describe('派生状态', () => {
  it('defaultAddress 取后端标记的那条，没有则 null', async () => {
    expect(store.defaultAddress).toBeNull()
    vi.mocked(api.fetchAddresses).mockResolvedValue({
      items: [addr({ id: 'a1' }), addr({ id: 'a2', is_default: true })],
    })
    await store.load()
    expect(store.defaultAddress?.id).toBe('a2')
  })

  it('满 10 条时 full 为真', async () => {
    vi.mocked(api.fetchAddresses).mockResolvedValue({
      items: Array.from({ length: MAX_ADDRESSES }, (_, i) => addr({ id: 'a' + i })),
    })
    await store.load()
    expect(store.full).toBe(true)
  })
})

describe('写动作', () => {
  it('新建后重拉列表（首条自动默认由后端决定）', async () => {
    vi.mocked(api.createAddress).mockResolvedValue(addr({ is_default: true }))
    vi.mocked(api.fetchAddresses).mockResolvedValue({ items: [addr({ is_default: true })] })
    const created = await store.create(payload)
    expect(api.createAddress).toHaveBeenCalledWith(payload)
    expect(created.is_default).toBe(true)
    expect(api.fetchAddresses).toHaveBeenCalledTimes(1)
    expect(store.defaultAddress?.id).toBe('a1')
  })

  it('编辑只发三个字段，不带默认标记', async () => {
    await store.update('a1', payload)
    expect(api.updateAddress).toHaveBeenCalledWith('a1', payload)
    expect(Object.keys(vi.mocked(api.updateAddress).mock.calls[0]![1])).toEqual([
      'receiver_name',
      'receiver_phone',
      'detail_address',
    ])
    expect(api.fetchAddresses).toHaveBeenCalledTimes(1)
  })

  it('设默认后重拉，原默认由后端取消', async () => {
    vi.mocked(api.fetchAddresses).mockResolvedValue({
      items: [addr({ id: 'a2', is_default: true }), addr({ id: 'a1' })],
    })
    await store.setDefault('a2')
    expect(api.setDefaultAddress).toHaveBeenCalledWith('a2')
    expect(store.items.filter((a) => a.is_default).map((a) => a.id)).toEqual(['a2'])
  })

  it('删除后重拉，补位结果以列表为准', async () => {
    vi.mocked(api.fetchAddresses).mockResolvedValue({ items: [addr({ id: 'a3', is_default: true })] })
    await store.remove('a1')
    expect(api.removeAddress).toHaveBeenCalledWith('a1')
    expect(store.defaultAddress?.id).toBe('a3')
  })

  it('写动作失败向上抛，由页面给文案', async () => {
    vi.mocked(api.createAddress).mockRejectedValueOnce(
      new ApiError(ApiCode.ADDRESS_LIMIT_EXCEEDED, 'x'),
    )
    await expect(store.create(payload)).rejects.toBeInstanceOf(ApiError)
    expect(api.fetchAddresses).not.toHaveBeenCalled()
  })

  it('reset 清空本地状态', async () => {
    vi.mocked(api.fetchAddresses).mockResolvedValue({ items: [addr()] })
    await store.load()
    store.reset()
    expect(store.items).toEqual([])
    expect(store.loaded).toBe(false)
  })
})