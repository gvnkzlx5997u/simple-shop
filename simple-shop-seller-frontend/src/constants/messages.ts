import { ApiCode } from '../api/types'

/** 卖家端提示文案；组件与页面统一引用，避免文案散落。 */
export const M10 = {
  // 会话与账号
  sessionExpired: '登录已超时，请重新登录',
  loginFailed: '账号或密码错误',
  oldPasswordMismatch: '原密码不正确',
  newPasswordTooShort: '新密码至少 8 位',
  passwordMismatch: '两次输入的新密码不一致',
  changePasswordSuccess: '密码已修改，所有会话已失效，请重新登录',
  confirmChangePassword: '修改后所有登录会话将失效，需要重新登录，确定修改？',
  paramInvalid: '请检查输入内容',

  // 商品表单
  nameRequired: '请填写商品名称',
  nameInvalid: '名称不超过 50 个字符，不能包含 < >，不允许换行',
  priceRequired: '价格格式不正确或为空',
  priceInvalid: '价格须大于 0 且不超过 999,999.99，最多两位小数',
  stockInvalid: '库存须是不小于 1 的整数',
  adjustStockInvalid: '库存须是不小于 0 的整数',
  categoryL1Required: '请填写一级类别',
  categoryL2Required: '请选择二级类别',
  descriptionInvalid: '描述文本各块合计不超过 1000 个字符，不能包含 < >；媒体块须先上传',
  imageInvalid: '仅支持 JPG／PNG，单张不超过 5MB',
  videoInvalid: '仅支持 MP4，单个不超过 20MB',
  mediaTypeInvalid: '仅支持 JPG／PNG 图片或 MP4 视频',
  imageCountExceeded: '图集最多 8 张，连主图最多 9 张',
  mainImageRequired: '请先上传主图，再添加图集图片',

  // 商品操作
  goodsFrozen: '商品交易中（冻结），不能修改库存、类别或下架',
  queueNotEmpty: '队列中仍有买家，需先处理完队列后方可下架',
  goodsNotFound: '商品不存在或已删除，请刷新列表',
  confirmOffShelf: '下架后商品停止展示并不再接受意向（之后可重新上架），确定下架？',
  confirmReList: '重新上架后商品恢复展示并继续接受意向，确定上架？',
  offShelfDone: '已下架',
  reListDone: '已重新上架',
  stockUpdated: '库存已更新',
  categoryUpdated: '类别已更新',
  offZeroHint: '该商品因库存清零下架：把库存改为大于 0 保存即自动重新上架，无需手动上架',
  emptyDescription: '无描述',
  confirmPublish: '发布后名称、价格、描述与图片均不可修改，请确认无误',
  publishSuccess: '发布成功，商品已上架',
  categoryLoadFailed: '类别加载失败，请刷新重试',
  categoryRequiredFirst: '请先在「类别管理」中创建类别',

  // 意向与交易
  notQueueHead: '只能对队首意向进入交易，请刷新后重试',
  tradingAlreadyExists: '已有交易中的意向，请先完成当前交易',
  queueFull: '排队人数已达上限，暂不能提交新意向',
  confirmEnterTrade: '将对队首买家发起交易：商品冻结并停止接收新意向，确定继续？',
  confirmTradeSuccessWithStock: '确认与该买家成交？将扣减 1 件库存，库存有余则商品回到在售',
  confirmTradeSuccessArchive:
    '确认与该买家成交？这是最后 1 件库存：其余排队买家将标记为失败（商品已售出），商品将售罄归档并转入历史，不可撤销',
  confirmTradeFailure:
    '请选择该买家意向的处理方式：作废（终态）／重新排队（回到队尾，下单时间不变）',
  tradeFailureDone: '已标记交易失败',
  enterTradeDone: '已进入交易，商品冻结并停止接收新意向',
  tradeSuccessArchived: '已成交并售罄归档，商品已转入历史记录',
  tradeSuccessOnSale: '已成交，商品仍在售',

  // 空态
  emptyGoods: '暂无商品，点击「发布商品」创建第一条',
  emptyIntentions: '暂无购买意向',
  emptyHistory: '暂无历史商品',
  emptyCustomers: '暂无客户',
  emptyPurchases: '该客户暂无购买记录',
  emptyHistoryIntentions: '无意向记录',

  // 类别
  categoryNameInvalid: '类别名不超过 50 个字符，不能包含 < >',
  emptyCategories: '暂无一级类别，先在上方创建一个',
  emptyCategoryChildren: '暂无二级类别',
} as const

const API_CODE_MESSAGES: Partial<Record<number, string>> = {
  [ApiCode.LOGIN_FAILED]: M10.loginFailed,
  [ApiCode.SESSION_INVALID]: M10.sessionExpired,
  [ApiCode.OLD_PASSWORD_MISMATCH]: M10.oldPasswordMismatch,
  [ApiCode.PASSWORD_INVALID]: M10.newPasswordTooShort,
  [ApiCode.CATEGORY_NOT_FOUND]: '类别不存在或已被删除，请刷新',
  [ApiCode.CATEGORY_NOT_LEAF]: '只能选择二级类别',
  [ApiCode.CATEGORY_NAME_INVALID]: M10.categoryNameInvalid,
  [ApiCode.CATEGORY_NAME_DUPLICATED]: '同级已有同名类别',
  [ApiCode.CATEGORY_HAS_CHILDREN]: '请先删除该类别下的二级类别',
  [ApiCode.CATEGORY_IN_USE]: '仍有商品挂在该类别下，不能删除',
  [ApiCode.GOODS_NOT_FOUND]: M10.goodsNotFound,
  [ApiCode.GOODS_NAME_INVALID]: M10.nameInvalid,
  [ApiCode.GOODS_DESCRIPTION_INVALID]: M10.descriptionInvalid,
  [ApiCode.PRICE_INVALID]: M10.priceInvalid,
  [ApiCode.STOCK_INVALID]: M10.stockInvalid,
  [ApiCode.IMAGE_INVALID]: M10.imageInvalid,
  [ApiCode.IMAGE_LIMIT_EXCEEDED]: M10.imageCountExceeded,
  [ApiCode.MAIN_IMAGE_REQUIRED]: M10.mainImageRequired,
  [ApiCode.GOODS_NOT_ON_SALE]: '商品当前不在售，请刷新后重试',
  [ApiCode.GOODS_NOT_OFF_SHELF]: '商品当前未处于下架状态，请刷新后重试',
  [ApiCode.GOODS_FROZEN]: M10.goodsFrozen,
  [ApiCode.QUEUE_NOT_EMPTY]: M10.queueNotEmpty,
  [ApiCode.GOODS_STATUS_MISMATCH]: '商品状态不允许该操作，请刷新后重试',
  [ApiCode.GOODS_STOCK_NOT_ZERO]: '该操作要求商品库存为 0',
  [ApiCode.VIDEO_INVALID]: M10.videoInvalid,
  [ApiCode.MEDIA_INVALID]: '媒体文件无效或超出大小限制',
  [ApiCode.MEDIA_TYPE_NOT_ALLOWED]: '媒体类型与用途不符（商品图仅图片；描述媒体支持图片／视频）',
  [ApiCode.CONFIRMATION_REQUIRED]: '该操作需要二次确认，请重试',
  [ApiCode.INTENTION_NOT_FOUND]: '意向不存在，请刷新后重试',
  [ApiCode.NOT_QUEUE_HEAD]: M10.notQueueHead,
  [ApiCode.INTENTION_STATUS_MISMATCH]: '意向状态已变化，请刷新后重试',
  [ApiCode.TRADING_ALREADY_EXISTS]: M10.tradingAlreadyExists,
  [ApiCode.QUEUE_FULL]: M10.queueFull,
  [ApiCode.INVALID_DISPOSAL]: '请选择处理方式（作废或重新排队）',
  [ApiCode.HISTORY_GOODS_NOT_FOUND]: '历史商品不存在，请刷新列表',
  [ApiCode.TOO_MANY_REQUESTS]: '操作过于频繁，请稍后重试',
  [ApiCode.INTERNAL_ERROR]: '服务端内部错误，请稍后重试',
  [ApiCode.PARAM_INVALID]: M10.paramInvalid,
  [ApiCode.RESOURCE_NOT_FOUND]: '请求的资源不存在',
}

/** 业务码 → 界面文案；未登记的码退化为通用提示。 */
export function apiCodeMessage(code: number): string {
  return API_CODE_MESSAGES[code] ?? M10.paramInvalid
}
