/**
 * 承运商固定清单（需求总结 §发货、团队自决 B6）。
 *
 * 文档原话的清单是「顺丰、京东、中通、圆通、申通、韵达、邮政 EMS、其他」，
 * 选「其他」时填名称。这里把**显示名**按文档写短、**存的值**用公司全名：
 * 后端只存字符串、不做枚举，存全名在订单详情里更好读，也不影响与文档对照。
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

/** 「其他」的选择值；它不是承运商名称，只是让下面多出一个填写框。 */
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
