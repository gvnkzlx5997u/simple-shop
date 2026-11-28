import { ApiCode } from '../api/types'

/** 卖家端提示文案；组件与页面统一引用，避免文案散落。 */
export const M10 = {
  // 会话与账号
  sessionExpired: '登录已超时，请重新登录',
  loginFailed: '账号或密码错误',
  /** 空值与「格式不对」在业务码上都是 50002，界面要分开说：空值提示"请输入"，其余才说检查格式。 */
  accountRequired: '请输入账号',
  passwordRequired: '请输入密码',
  oldPasswordRequired: '请填写原密码',
  newPasswordRequired: '请填写新密码',
  oldPasswordMismatch: '原密码不正确',
  /** 10004：后端同时拒「不足 8 位」与「超过 BCrypt 有效上限 72 字节」，文案要把两条都说清。 */
  newPasswordTooShort: '新密码至少 8 位，且不超过 72 字节',
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
  confirmOffShelf: '下架后商品停止展示，且不能重新上架（库存变为只读，类别仍可改挂）。确定下架？',
  offShelfDone: '已下架',
  stockUpdated: '库存已更新',
  categoryUpdated: '类别已更新',
  /** 库存耗尽 / 已售下架的商品补货即自动上架，无需单独的上架动作。 */
  restockHint: '补货即自动上架：把库存改为大于 0 保存即可，不需要单独的上架动作',
  /** 手动下架是终态，库存不可再改；类别仍可改挂。这条不因阶段四放宽而取消。 */
  manualOffShelfHint: '手动下架为终态：库存不可再修改，类别仍可改挂',
  /** 在售商品把库存改成 0 会触发「库存耗尽」下架，保存前先提示后果。 */
  stockZeroHint: '保存后库存为 0，商品会自动下架（原因：库存耗尽）',
  emptyDescription: '无描述',
  confirmPublish: '发布后名称、价格、描述与图片均不可修改，请确认无误',
  confirmPublishRetry: '已上传的媒体不会重复上传，只补齐剩余部分。确认重试发布？',
  confirmLeavePublish: '内容尚未发布，离开将丢失已填写的内容（包括已选图片与描述）。确定离开？',
  publishSuccess: '发布成功，商品已上架',
  categoryLoadFailed: '类别加载失败，请刷新重试',
  categoryRequiredFirst: '请先在「类别管理」中创建类别',

  // 订单
  orderNotFound: '订单不存在或已被删除，请刷新列表',
  orderStatusInvalid: '订单状态不允许该操作，请刷新后重试',
  orderNotCancelable: '当前状态不可取消该订单',
  orderStockShortage: '买家确认收货时库存不足，整单被拒绝：订单仍在「已发货」，补货后买家可重试',
  /** 推进动作的二次确认；都写明「做什么、之后会怎样」，不写「是否继续」这类空话。 */
  confirmOrder: '确认接单后订单进入「已确认」，接下来需要备货。确定确认这笔订单？',
  confirmPrepare: '标记后订单进入「备货完成」，下一步可以开始发货。确认货已备好？',
  confirmCancelOrder:
    '取消后订单终止、不能恢复。已付款（含已发货）的订单会按实付金额全额原路退款，确定取消？',
  /** 待支付订单取消不涉及退款，别说「按实付金额退款」吓人。 */
  confirmCancelUnpaidOrder:
    '取消后订单终止、不能恢复。这笔订单还没付款，取消不产生退款，也不影响库存，确定取消？',
  orderConfirmed: '订单已确认',
  orderPrepared: '已标记为备货完成',
  orderShipped: '已发货',
  orderCanceled: '订单已取消',
  /** 待支付订单卖家不能推进：按钮不给，这里只解释原因。 */
  pendingPaymentHint: '买家还没付款，这一步要等付款之后才能操作',

  // 物流
  shipTitle: '填写发货信息',
  logisticsTitle: '修改物流信息',
  shipHint: '发货后订单进入「已发货」，买家将不能再取消；运单号有多行时请一行一个。',
  logisticsHint: '改物流不改变订单状态，发货后到交易完成的订单都可以改。',
  /** 下拉框的首项，不是报错文案。 */
  carrierSelectPlaceholder: '请选择承运商',
  carrierRequired: '请选择承运商',
  carrierOtherRequired: '请填写承运商名称',
  carrierTooLong: '承运商不超过 50 个字符',
  carrierSingleLine: '承运商请填在一行内（多个包裹把单号写进运单号，一行一个）',
  trackingNoRequired: '请填写运单号',
  trackingNoTooLong: '运单号不超过 500 个字符（多包裹请换行分隔）',
  logisticsRemarkTooLong: '备注不超过 500 个字符',
  carrierControlChar: '承运商不能包含控制字符',
  trackingNoControlChar: '运单号不能包含控制字符（换行与制表符除外）',
  remarkControlChar: '备注不能包含控制字符（换行与制表符除外）',
  carrierOtherPlaceholder: '请输入承运商名称，如 德邦物流',
  trackingNoPlaceholder: '一行一个运单号；一个包裹时只填一行',
  logisticsRemarkPlaceholder: '可选，例如「分两个包裹寄出」',
  logisticsUpdated: '物流信息已更新',
  logisticsEmpty: '尚未发货，没有物流信息',
  emptyOrderItems: '这笔订单没有商品明细',

  // 售后
  aftersaleNotFound: '售后单不存在或已被删除，请刷新列表',
  aftersaleStatusInvalid: '售后状态不允许该操作，请刷新后重试',
  aftersaleRefundDuplicated: '该订单项已经退过款，同一订单项只能退款一次（换货不受限）',
  aftersaleResultRequired: '请选择处理结果',
  aftersaleResultInvalid: '处理结果不合法，请重新选择',
  confirmCompleteAftersale: '完成后不能修改结果，也不再接受买家撤销。确定按所选结果完成？',
  confirmCancelAftersale:
    '取消与「拒绝」同义，买家看到的是已取消；买家之后可以重新申请，生成新单号。确定取消？',
  aftersaleCompleted: '售后已完成',
  aftersaleCanceled: '售后已取消',
  aftersaleResultTitle: '选择处理结果',
  emptyAftersales: '暂无售后单',
  emptyOrderAftersales: '这笔订单还没有售后申请',
  emptyAftersalesFiltered: '当前筛选下没有售后单，换个条件或查看「全部」试试',
  aftersaleOrderNoPlaceholder: '按订单号搜索',
  aftersaleImagesEmpty: '买家没有上传凭证图',

  // 空态
  emptyGoods: '暂无商品，点击「发布商品」创建第一条',
  emptyGoodsFiltered: '当前筛选下没有商品，换个条件或清除筛选试试',
  emptyCustomers: '暂无客户',
  emptyOrders: '暂无订单',
  emptyOrdersFiltered: '当前筛选下没有订单，换个条件或查看「全部」试试',

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
  // 10005~10007 只在买家注册时产生，卖家端 UI 到不了；仍然登记，避免落到通用兜底时文不对题。
  [ApiCode.USERNAME_TAKEN]: '用户名已被占用',
  [ApiCode.USERNAME_INVALID]: '用户名不合法（1~50 个字符，不含空格与 < >）',
  [ApiCode.PHONE_INVALID]: '联系电话格式不正确（7~20 位数字或 -）',
  // 卖家端唯一来源是「客户详情」：传入的买家不存在（HTTP 404）。
  [ApiCode.ACCOUNT_NOT_FOUND]: '客户不存在或已被删除，请刷新客户列表',
  // 卖家登录时账号字段被后端判为不合法（含空白、尖括号、控制字符）。
  [ApiCode.ACCOUNT_INVALID]: '账号格式不正确（1~50 个字符，不含空格与 < >）',
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
  [ApiCode.GOODS_STATUS_MISMATCH]: '商品状态不允许该操作，请刷新后重试',
  [ApiCode.VIDEO_INVALID]: M10.videoInvalid,
  [ApiCode.MEDIA_INVALID]: '媒体文件无效或超出大小限制',
  [ApiCode.MEDIA_TYPE_NOT_ALLOWED]: '媒体类型与用途不符（商品图仅图片；描述媒体支持图片／视频）',
  [ApiCode.ORDER_NOT_FOUND]: M10.orderNotFound,
  [ApiCode.ORDER_STATUS_INVALID]: M10.orderStatusInvalid,
  [ApiCode.ORDER_NOT_CANCELABLE]: M10.orderNotCancelable,
  [ApiCode.ORDER_STOCK_SHORTAGE]: M10.orderStockShortage,
  [ApiCode.AFTERSALE_NOT_FOUND]: M10.aftersaleNotFound,
  [ApiCode.AFTERSALE_STATUS_INVALID]: M10.aftersaleStatusInvalid,
  [ApiCode.AFTERSALE_REFUND_DUPLICATED]: M10.aftersaleRefundDuplicated,
  [ApiCode.AFTERSALE_RESULT_REQUIRED]: M10.aftersaleResultRequired,
  // 卖家端只能查看买家已上传的图，不会自己传，这里只作兜底。
  [ApiCode.AFTERSALE_IMAGE_LIMIT_EXCEEDED]: '售后凭证图数量超出限制',
  [ApiCode.LOGISTICS_NOT_EDITABLE]: '只有已发货（含交易完成）的订单才能修改物流',
  [ApiCode.TOO_MANY_REQUESTS]: '操作过于频繁，请稍后重试',
  [ApiCode.INTERNAL_ERROR]: '服务端内部错误，请稍后重试',
  [ApiCode.PARAM_INVALID]: M10.paramInvalid,
  [ApiCode.RESOURCE_NOT_FOUND]: '请求的资源不存在',
  [ApiCode.METHOD_NOT_ALLOWED]: '接口不支持该请求方式，请刷新页面后重试',
  [ApiCode.UNSUPPORTED_MEDIA_TYPE]: '请求格式不受支持（上传须为 multipart/form-data）',
}

/**
 * 业务码 → 界面文案；未登记的码退化为通用失败提示。
 * 兜底文案不能是「请检查输入内容」——未登记的码多半不是用户输入问题，那样会把责任推错方向。
 */
export function apiCodeMessage(code: number): string {
  return API_CODE_MESSAGES[code] ?? '操作失败，请稍后重试'
}
