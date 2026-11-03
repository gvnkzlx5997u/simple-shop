/**
 * 测试数据工厂。
 *
 * 三条规矩：
 *   1. 所有名字带 RUN_ID 后缀 —— 同一台库上反复跑不会撞名；
 *   2. 不用「清库」做隔离 —— 靠唯一后缀 + 每次跑前重建库（见 scripts/reset-e2e-db.bat）；
 *   3. 时间/金额都用字符串，避免 JS 浮点把价格变成 0.30000000000000004。
 */
import { RUN_ID } from './config';

let seq = 0;

/** 生成本次运行内唯一的名字：prefix-<运行号>-<序号> */
export function uniqueName(prefix: string): string {
  seq += 1;
  return `${prefix}-${RUN_ID}-${seq}`;
}

/** 一个买家账号（电话 11 位数字，满足"至少 7 位数字"） */
export function buyerFixture(prefix = 'buyer') {
  const name = uniqueName(prefix);
  return {
    username: name,
    password: 'Buyer@12345',
    phone: '13800000000',
    default_location: '教学楼 A',
  };
}

/** 一个商品（价格用字符串，两位小数） */
export function goodsFixture(name: string, categoryId: string, overrides: Record<string, unknown> = {}) {
  return {
    name,
    price: '99.90',
    stock: 3,
    category_id: categoryId,
    description: [{ type: 'TEXT' as const, text: 'E2E 自动创建的商品描述' }],
    ...overrides,
  };
}
