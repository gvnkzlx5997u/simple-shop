import { describe, expect, it } from 'vitest'

import { ApiError } from '../../src/api/http'
import { ApiCode } from '../../src/api/types'
import { describeError, describeSubmitError } from '../../src/utils/errorText'

/** 业务码 → 界面文案。覆盖买家链路可达码 + 预防性码（81005 / 82002 只在卖家端抛）。 */
const CASES: Array<[number, string]> = [
  [ApiCode.LOGIN_FAILED, '用户名或密码不正确'],
  [ApiCode.SESSION_INVALID, '登录已失效，请重新登录'],
  [ApiCode.PASSWORD_INVALID, '密码不符合要求：至少 8 位'],
  [ApiCode.USERNAME_TAKEN, '这个用户名已经被注册了，换一个试试'],
  [ApiCode.USERNAME_INVALID, '用户名不符合要求：不能为空，不能含空格或尖括号，最长 50 字'],
  [ApiCode.PHONE_INVALID, '电话格式不对：请填 7~20 位数字，可含短横'],
  [ApiCode.ACCOUNT_NOT_FOUND, '账号不存在，请重新登录'],
  [ApiCode.ACCOUNT_INVALID, '账号信息不符合要求'],
  [ApiCode.GOODS_NOT_FOUND, '商品不存在或已下架'],
  [ApiCode.STOCK_INVALID, '库存不足，请回购物车调整数量'],
  [ApiCode.GOODS_NOT_ON_SALE, '有商品已下架，请回购物车处理'],
  [ApiCode.IMAGE_INVALID, '图片需为 JPG 或 PNG，且不超过 5MB'],
  [ApiCode.MEDIA_TYPE_NOT_ALLOWED, '只能上传售后凭证图'],
  [ApiCode.ORDER_NOT_FOUND, '订单不存在'],
  [ApiCode.ORDER_STATUS_INVALID, '订单状态已变化，请刷新后重试'],
  [ApiCode.ORDER_NOT_CANCELABLE, '订单已发货或已完成，不能取消'],
  [ApiCode.ORDER_STOCK_SHORTAGE, '卖家库存不足，暂时无法完成，请联系卖家补货后重试'],
  [ApiCode.CART_GOODS_NOT_SELECTABLE, '商品已下架，无法加入购物车'],
  [ApiCode.FAVORITE_GOODS_NOT_SELECTABLE, '商品已下架，无法收藏'],
  [ApiCode.CART_TO_FAVORITE_REJECTED, '有商品已下架，未转收藏'],
  [ApiCode.ADDRESS_NOT_FOUND, '地址不存在，请刷新后重试'],
  [ApiCode.ADDRESS_LIMIT_EXCEEDED, '最多保存 10 个地址，请先删除不用的'],
  [
    ApiCode.ADDRESS_FIELD_INVALID,
    '地址信息不符合要求：收货人 ≤50 字、手机号 7~20 位数字、详细地址 ≤200 字，均不能换行',
  ],
  [ApiCode.ADDRESS_REQUIRED, '请先选择一个收货地址'],
  [ApiCode.AFTERSALE_NOT_FOUND, '售后单不存在'],
  [ApiCode.AFTERSALE_STATUS_INVALID, '售后单状态已变化，请刷新后查看'],
  [ApiCode.AFTERSALE_NOT_APPLICABLE, '该商品当前不可申请售后（仅交易完成的订单可申请）'],
  [ApiCode.AFTERSALE_REFUND_DUPLICATED, '该商品已退过款，不能重复退款'],
  [ApiCode.AFTERSALE_RESULT_REQUIRED, '缺少处理结果'],
  [ApiCode.AFTERSALE_CONTENT_INVALID, '标题或描述不符合要求：标题 ≤100 字且不能换行，描述 ≤1000 字'],
  [ApiCode.AFTERSALE_IMAGE_LIMIT_EXCEEDED, '最多上传 9 张图片'],
  [ApiCode.LOGISTICS_NOT_EDITABLE, '物流信息当前不可修改'],
  [ApiCode.PAYMENT_CHANNEL_INVALID, '请选择一个支付渠道'],
  [ApiCode.PAYMENT_NOT_ALLOWED, '订单当前不可支付，可能已超时取消，请刷新查看'],
  [ApiCode.PAYMENT_FAILED, '支付未成功，订单仍未支付，可换渠道重试'],
  [ApiCode.REFUND_FAILED, '退款失败，请联系卖家处理'],
  [ApiCode.TOO_MANY_REQUESTS, '操作太频繁了，请稍后再试'],
  [ApiCode.PARAM_INVALID, '填写的内容不符合要求'],
  [ApiCode.INTERNAL_ERROR, '服务器出错了，请稍后再试'],
]

describe('describeError', () => {
  it.each(CASES)('业务码 %i 映射为固定中文文案', (code, text) => {
    expect(describeError(new ApiError(code, 'backend raw message'))).toBe(text)
  })

  it('不泄露后端 message 原文', () => {
    expect(describeError(new ApiError(ApiCode.LOGIN_FAILED, 'secret backend detail'))).not.toContain(
      'secret',
    )
  })

  it('未登记码 + 404 兜底不硬说成商品', () => {
    expect(describeError(new ApiError(99999, 'x', 404))).toBe('内容不存在或已被移除')
  })

  it('未登记码无 HTTP 状态走通用兜底', () => {
    expect(describeError(new ApiError(99999, 'x'))).toBe('操作失败，请稍后再试')
  })

  it('已停用的 10008 不再有专属文案', () => {
    expect(describeError(new ApiError(10008, 'x'))).toBe('操作失败，请稍后再试')
  })

  it('非 ApiError 视为网络异常', () => {
    expect(describeError(new Error('boom'))).toBe('网络异常，请检查后重试')
    expect(describeError(null)).toBe('网络异常，请检查后重试')
  })
})

describe('describeSubmitError', () => {
  it('下单场景的 50002 用专属文案', () => {
    expect(describeSubmitError(new ApiError(ApiCode.PARAM_INVALID, 'x'))).toBe(
      '下单信息有误，请回购物车重新勾选后再提交',
    )
  })

  it('其它码回落通用 describeError', () => {
    expect(describeSubmitError(new ApiError(ApiCode.GOODS_NOT_ON_SALE, 'x'))).toBe(
      '有商品已下架，请回购物车处理',
    )
    expect(describeSubmitError(new Error('boom'))).toBe('网络异常，请检查后重试')
  })
})