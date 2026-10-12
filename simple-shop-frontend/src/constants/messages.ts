import { ApiCode } from '../api/types'

/** 卖家端提示文案（第 10 章 M10-xx），组件与页面统一引用，避免文案散落。 */
export const M10 = {
  frozenByTrade: '交易中的商品不可解冻，请通过标记交易结果结束交易',
  queueNotEmpty: '队列中仍有买家，需先处理完队列后方可下架',
  confirmOffline: '下架后商品将归档入历史且不可恢复，确定下架？',
  confirmEnterTrade: '将对第 1 位买家发起交易，商品将冻结并停止接收新意向',
  confirmTradeSuccess:
    '确认成交？其余排队买家将自动标记为交易失败（商品已售出），商品将下架归档，且不可撤销',
  confirmTradeFailure:
    '请选择该买家意向的处理方式：作废（终态）／重新排队（回到队尾，口令码保持有效）',
  changePasswordSuccess: '密码已修改，所有会话已失效，请重新登录',
  sessionExpired: '登录已超时，请重新登录',
  loginFailed: '账号或密码错误',
  goodsAlreadyExists: '当前已有在售商品，无法发布第二条',
  confirmPublish: '发布后名称、描述、图片、价格不可修改，请确认无误',
  emptyIntentions: '暂无购买意向',
  emptyHistory: '暂无历史商品',
  confirmFreeze: '冻结后买家将无法提交新的意向，确定冻结？',
  oldPasswordMismatch: '原密码不正确',
  confirmChangePassword: '修改后所有登录会话将失效，需要重新登录，确定修改？',
  nameInvalid: '名称不超过 50 个字符，不能包含 < >，首尾不要留空格',
  descriptionInvalid: '描述不超过 500 个字符，不能包含 < >',
  imageInvalid: '仅支持 JPG／PNG，单张不超过 5MB',
  priceInvalid: '价格须大于 0 且不超过 999,999.99，最多两位小数',
  newPasswordTooShort: '新密码至少 8 位',
  paramInvalid: '请检查输入内容',
  nameRequired: '请填写商品名称',
  priceRequired: '请填写价格',
  passwordMismatch: '两次输入的新密码不一致',
} as const

const API_CODE_MESSAGES: Partial<Record<number, string>> = {
  [ApiCode.LOGIN_FAILED]: M10.loginFailed,
  [ApiCode.SESSION_INVALID]: M10.sessionExpired,
  [ApiCode.OLD_PASSWORD_MISMATCH]: M10.oldPasswordMismatch,
  [ApiCode.NEW_PASSWORD_TOO_SHORT]: M10.newPasswordTooShort,
  [ApiCode.GOODS_ALREADY_EXISTS]: M10.goodsAlreadyExists,
  [ApiCode.GOODS_NOT_ON_SALE]: '商品当前不是在售状态，请刷新后重试',
  [ApiCode.GOODS_NOT_FROZEN]: '商品当前未处于已冻结状态，请刷新后重试',
  [ApiCode.GOODS_FROZEN_BY_TRADE]: M10.frozenByTrade,
  [ApiCode.QUEUE_NOT_EMPTY]: M10.queueNotEmpty,
  [ApiCode.GOODS_NAME_INVALID]: M10.nameInvalid,
  [ApiCode.GOODS_DESCRIPTION_INVALID]: M10.descriptionInvalid,
  [ApiCode.IMAGE_INVALID]: M10.imageInvalid,
  [ApiCode.PRICE_OUT_OF_RANGE]: M10.priceInvalid,
  [ApiCode.NOT_QUEUE_HEAD]: '目标意向不是队首，请刷新后重试',
  [ApiCode.INTENTION_STATUS_MISMATCH]: '意向状态已变化，请刷新后重试',
  [ApiCode.TRADING_ALREADY_EXISTS]: '已有交易中的意向，请刷新后重试',
  [ApiCode.ARCHIVE_PRECONDITION_FAILED]: '仍有未处理完的意向，无法归档',
  [ApiCode.INTENTION_NOT_FOUND]: '意向不存在，请刷新后重试',
  [ApiCode.TOO_MANY_REQUESTS]: '操作过于频繁，请稍后重试',
  [ApiCode.INTERNAL_ERROR]: '服务端内部错误，请稍后重试',
  [ApiCode.PARAM_INVALID]: M10.paramInvalid,
}

/** 业务码 → 界面文案；未登记的码退化为通用提示。 */
export function apiCodeMessage(code: number): string {
  return API_CODE_MESSAGES[code] ?? M10.paramInvalid
}
