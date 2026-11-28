/**
 * 内置承运商清单
 */
export const CARRIER_PRESETS: readonly { value: string; label: string }[] = [
  { value: '顺丰速运', label: '顺丰' },
  { value: '京东物流', label: '京东' },
  { value: '中通快递', label: '中通' },
  { value: '圆通速递', label: '圆通' },
  { value: '申通快递', label: '申通' },
  { value: '韵达快递', label: '韵达' },
  { value: '邮政EMS', label: '邮政 EMS' },
]

export const CARRIER_OTHER = 'OTHER'

/**
 * 把订单上已存的承运商名映射回下拉框的选择值。
 *
 * 改物流时要回填：清单里的名字直接选中；不在清单里（例如历史订单写的是别家）
 * 落到「其他」并把原名放进填写框——否则一编辑就把原来的承运商冲掉了。
 */
export function resolveCarrierSelection(raw: string | null | undefined): {
  choice: string
  other: string
} {
  const value = (raw ?? '').trim()
  if (value.length === 0) {
    return { choice: '', other: '' }
  }
  if (CARRIER_PRESETS.some((preset) => preset.value === value)) {
    return { choice: value, other: '' }
  }
  return { choice: CARRIER_OTHER, other: value }
}
