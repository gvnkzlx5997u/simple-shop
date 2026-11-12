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
  goodsNotFound: '商品不存在或已删除，请刷新列表',
  /** 手动下架是终态：不可重新上架，库存只读，类别仍可改挂；有未完成订单时会被拒绝。 */
  confirmOffShelf:
    '下架后商品停止展示，且不能重新上架（库存变为只读，类别仍可改挂）。\n有未完成订单时会被拒绝，确定下架？',
  offShelfDone: '已下架',
  stockUpdated: '库存已更新',
  categoryUpdated: '类别已更新',
  /** 库存耗尽 / 已售下架的商品补货即自动上架，无需单独的上架动作。 */
  restockHint: '补货即自动上架：把库存改为大于 0 保存即可，不需要单独的上架动作',
  /** 手动下架是终态，库存不可再改；类别仍可改挂。 */
  manualOffShelfHint: '手动下架为终态：库存不可再修改，类别仍可改挂',
  /** 在售商品把库存改成 0 会触发「库存耗尽」下架，保存前先提示后果。 */
  stockZeroHint: '保存后库存为 0，商品会自动下架（原因：库存耗尽）',
  emptyDescription: '无描述',
  confirmPublish: '发布后名称、价格、描述与图片均不可修改，请确认无误',
  publishSuccess: '发布成功，商品已上架',
  categoryLoadFailed: '类别加载失败，请刷新重试',
  categoryRequiredFirst: '请先在「类别管理」中创建类别',

  // 订单
  orderNotFound: '订单不存在或已被删除，请刷新列表',
  orderStatusInvalid: '订单状态不允许该操作，请刷新后重试',
  orderNotCancelable: '当前状态不可取消该订单',
  orderStockShortage: '库存不足，整单未能完成：订单仍在「已发货」，补货后可重试',
  /** 五个动作的二次确认；都写明「做什么、之后会怎样」，不写「是否继续」这类空话。 */
  confirmOrder: '确认接单后订单进入「已确认」，接下来需要备货。确定确认这笔订单？',
  confirmPrepare: '标记后订单进入「备货完成」，下一步可以开始发货。确认货已备好？',
  confirmShip: '发货后订单进入「已发货」，买家将不能再取消。确认开始发货？',
  confirmComplete:
    '完成交易会按明细数量扣减库存，且不可撤销；任一商品库存不足会整单拒绝。确认完成？',
  confirmCancelOrder: '取消后订单终止、不能恢复；只有交易完成前的订单可以取消。确定取消？',
  orderConfirmed: '订单已确认',
  orderPrepared: '已标记为备货完成',
  orderShipped: '已发货',
  orderCompleted: '交易完成，库存已扣减',
  orderCanceled: '订单已取消',

  // 空态
  emptyGoods: '暂无商品，点击「发布商品」创建第一条',
  emptyGoodsFiltered: '当前筛选下没有商品，换个条件或清除筛选试试',
  emptyCustomers: '暂无客户',
  emptyOrders: '暂无订单',
  emptyOrdersFiltered: '当前筛选下没有订单，换个条件或查看「全部」试试',
  emptyOrderItems: '这笔订单没有商品明细',

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
  [ApiCode.GOODS_HAS_UNFINISHED_ORDER]:
    '商品有未完成订单，暂不能下架或把库存清零；先处理完这些订单再操作',
  [ApiCode.GOODS_STATUS_MISMATCH]: '商品状态不允许该操作，请刷新后重试',
  [ApiCode.VIDEO_INVALID]: M10.videoInvalid,
  [ApiCode.MEDIA_INVALID]: '媒体文件无效或超出大小限制',
  [ApiCode.MEDIA_TYPE_NOT_ALLOWED]: '媒体类型与用途不符（商品图仅图片；描述媒体支持图片／视频）',
  [ApiCode.ORDER_NOT_FOUND]: M10.orderNotFound,
  [ApiCode.ORDER_STATUS_INVALID]: M10.orderStatusInvalid,
  [ApiCode.ORDER_NOT_CANCELABLE]: M10.orderNotCancelable,
  [ApiCode.ORDER_STOCK_SHORTAGE]: M10.orderStockShortage,
  [ApiCode.TOO_MANY_REQUESTS]: '操作过于频繁，请稍后重试',
  [ApiCode.INTERNAL_ERROR]: '服务端内部错误，请稍后重试',
  [ApiCode.PARAM_INVALID]: M10.paramInvalid,
  [ApiCode.RESOURCE_NOT_FOUND]: '请求的资源不存在',
}

/** 业务码 → 界面文案；未登记的码退化为通用提示。 */
export function apiCodeMessage(code: number): string {
  return API_CODE_MESSAGES[code] ?? M10.paramInvalid
}
