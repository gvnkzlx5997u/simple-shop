import { ApiError } from '../api/http'
import { ApiCode } from '../api/types'

/**
 * 把 ApiError 翻译成界面能显示的中文;后端 message 一律不直接展示。
 * 这里的每条文案都对应后端 ErrorCode 的一个具体码，不要合并成笼统的"操作失败"。
 */
export function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    // 先按业务码判断;业务码覆盖不到的，再按 HTTP 状态兜底。
    switch (err.code) {
      case ApiCode.LOGIN_FAILED:
        return '用户名或密码不正确'
      case ApiCode.SESSION_INVALID:
        return '登录已失效，请重新登录'
      case ApiCode.PASSWORD_INVALID:
        return '密码不符合要求：至少 8 位'
      case ApiCode.USERNAME_TAKEN:
        return '这个用户名已经被注册了，换一个试试'
      case ApiCode.USERNAME_INVALID:
        // 依据后端 AccountRules.loginName：非空、≤50 字、不含尖括号与换行、
        // 且任意位置不能出现空白字符或控制字符（有 isWhitespace / isISOControl 检查）。
        return '用户名不符合要求：不能为空，不能含空格或尖括号，最长 50 字'
      case ApiCode.PHONE_INVALID:
        return '电话格式不对：请填 7~20 位数字，可含短横'
      case ApiCode.ACCOUNT_INVALID:
        return '账号信息不符合要求'
      case ApiCode.ACCOUNT_NOT_FOUND:
        // 后端回 HTTP 404，但**不能**让下面的 404 兜底把它说成商品。
        return '账号不存在，请重新登录'
      case ApiCode.GOODS_NOT_FOUND:
        return '商品不存在或已下架'
      case ApiCode.CART_GOODS_NOT_SELECTABLE:
        return '商品已下架，无法加入购物车'
      case ApiCode.GOODS_NOT_ON_SALE:
        return '有商品已下架，请回购物车处理'
      case ApiCode.STOCK_INVALID:
        return '库存不足，请回购物车调整数量'
      case ApiCode.ORDER_STOCK_SHORTAGE:
        return '卖家库存不足，暂时无法完成，请联系卖家补货后重试'
      case ApiCode.FAVORITE_GOODS_NOT_SELECTABLE:
        return '商品已下架，无法收藏'
      case ApiCode.CART_TO_FAVORITE_REJECTED:
        return '有商品已下架，未转收藏'
      case ApiCode.ORDER_NOT_FOUND:
        return '订单不存在'
      case ApiCode.ORDER_STATUS_INVALID:
        return '订单状态已变化，请刷新后重试'
      case ApiCode.ORDER_NOT_CANCELABLE:
        return '订单已发货或已完成，不能取消'
      case ApiCode.IMAGE_INVALID:
        return '图片需为 JPG 或 PNG，且不超过 5MB'
      case ApiCode.MEDIA_TYPE_NOT_ALLOWED:
        return '只能上传售后凭证图'
      case ApiCode.ADDRESS_NOT_FOUND:
        return '地址不存在，请刷新后重试'
      case ApiCode.ADDRESS_LIMIT_EXCEEDED:
        return '最多保存 10 个地址，请先删除不用的'
      case ApiCode.ADDRESS_FIELD_INVALID:
        return '地址信息不符合要求：收货人 ≤50 字、手机号 7~20 位数字、详细地址 ≤200 字，均不能换行'
      case ApiCode.ADDRESS_REQUIRED:
        return '请先选择一个收货地址'
      case ApiCode.AFTERSALE_NOT_FOUND:
        return '售后单不存在'
      case ApiCode.AFTERSALE_STATUS_INVALID:
        return '售后单状态已变化，请刷新后查看'
      case ApiCode.AFTERSALE_NOT_APPLICABLE:
        return '该商品当前不可申请售后（仅交易完成的订单可申请）'
      case ApiCode.AFTERSALE_REFUND_DUPLICATED:
        return '该商品已退过款，不能重复退款'
      case ApiCode.AFTERSALE_RESULT_REQUIRED:
        return '缺少处理结果'
      case ApiCode.AFTERSALE_CONTENT_INVALID:
        return '标题或描述不符合要求：标题 ≤100 字且不能换行，描述 ≤1000 字'
      case ApiCode.AFTERSALE_IMAGE_LIMIT_EXCEEDED:
        return '最多上传 9 张图片'
      case ApiCode.LOGISTICS_NOT_EDITABLE:
        return '物流信息当前不可修改'
      case ApiCode.PAYMENT_CHANNEL_INVALID:
        return '请选择一个支付渠道'
      case ApiCode.PAYMENT_NOT_ALLOWED:
        return '订单当前不可支付，可能已超时取消，请刷新查看'
      case ApiCode.PAYMENT_FAILED:
        return '支付未成功，订单仍未支付，可换渠道重试'
      case ApiCode.REFUND_FAILED:
        return '退款失败，请联系卖家处理'
      case ApiCode.TOO_MANY_REQUESTS:
        return '操作太频繁了，请稍后再试'
      case ApiCode.PARAM_INVALID:
        return '填写的内容不符合要求'
      case ApiCode.INTERNAL_ERROR:
        return '服务器出错了，请稍后再试'
    }
    // 兜底：后端也可能只给 HTTP 状态码、业务码不在上面清单里。
    // 注意 404 不能硬说成「商品」——账号、订单、通用资源都可能是 404，
    // 具体是哪一种由上面的业务码决定;走到这里说明是还没登记的码。
    if (err.httpStatus === 404) return '内容不存在或已被移除'
    return '操作失败，请稍后再试'
  }
  return '网络异常，请检查后重试'
}

/** 下单失败的提示。参数类错误单独说，因为后端那句话是给表单用的，放在下单场景看不懂。 */
export function describeSubmitError(err: unknown): string {
  if (err instanceof ApiError && err.code === ApiCode.PARAM_INVALID) {
    return '下单信息有误，请回购物车重新勾选后再提交'
  }
  return describeError(err)
}