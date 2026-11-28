import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  addToCart,
  createAddress,
  createL1,
  createL2,
  deleteAddress,
  listAddresses,
  newApi,
  newBuyer,
  placeOrder,
  publishGoods,
  sellerLogin,
  sellerOrderDetail,
  updateAddress,
} from '../helpers/api';
import type { GoodsView } from '../helpers/api';
import { BUYER_URL } from '../helpers/config';
import { addressFixture, buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { buyerLoginUi, buyerOpenAddresses, buyerOpenCart } from '../helpers/ui';

/**
 * 09 买家地址簿（阶段四新增）
 *
 * 覆盖：未登录拦截、首条自动默认、设为默认、编辑不改默认、删除二次确认、
 * 删默认后补位、10 条上限、字段校验与后端兜底、订单收货快照、结算改选地址。
 * 造数走接口，断言走页面与接口返回，不连数据库。
 */
test.describe.serial('09 买家地址簿', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let buyerToken: string;
  const buyer = buyerFixture('addr');
  let goods: GoodsView;

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('addr');
    const l1 = await createL1(ctx, sellerToken, `地址一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `地址二级-${tag}`);
    goods = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('地址商品'), l2.id, { stock: 50, price: '12.00' }),
    );
    buyerToken = await newBuyer(ctx, buyer);
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  /** 表单三个输入按顺序是 收货人 / 手机号 / 详细地址（label 里还带说明文字，按位置取更稳）。 */
  function formInputs(page: import('@playwright/test').Page) {
    const form = page.getByTestId('address-form');
    return { name: form.locator('input').nth(0), phone: form.locator('input').nth(1), detail: form.locator('input').nth(2) };
  }

  async function createViaUi(
    page: import('@playwright/test').Page,
    address: { receiver_name: string; receiver_phone: string; detail_address: string },
  ) {
    await page.getByTestId('address-new').click();
    const inputs = formInputs(page);
    await inputs.name.fill(address.receiver_name);
    await inputs.phone.fill(address.receiver_phone);
    await inputs.detail.fill(address.detail_address);
    await page.getByTestId('address-save').click();
  }

  test('AD-01 未登录进地址页 → 跳登录并带回跳地址', async ({ page }) => {
    await page.goto(`${BUYER_URL}/addresses`);
    await expect(page).toHaveURL(/\/login\?redirect=(%2F|\/)addresses/);

    await page.getByLabel('用户名').fill(buyer.username);
    await page.getByLabel('密码').fill(buyer.password);
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await expect(page).toHaveURL(/\/addresses$/);
    await expect(page.getByRole('heading', { name: '收货地址' })).toBeVisible();
  });

  test('AD-02 首条地址自动默认', async ({ page }) => {
    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenAddresses(page);

    await expect(page.getByTestId('address-row')).toHaveCount(0);
    await expect(page.getByText('还没有收货地址')).toBeVisible();

    await createViaUi(page, addressFixture(1));

    await expect(page.getByTestId('notice')).toContainText('已新增地址');
    await expect(page.getByTestId('address-row')).toHaveCount(1);
    await expect(page.getByTestId('address-row').first()).toContainText('默认');
    await expect(page.getByText('还没有收货地址')).toBeHidden();

    const book = await listAddresses(ctx, buyerToken);
    expect(book.items.filter((item) => item.is_default)).toHaveLength(1);
  });

  test('AD-03 设为默认的切换', async ({ page }) => {
    // 现在有 1 条默认；再造 1 条
    await createAddress(ctx, buyerToken, addressFixture(2));

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenAddresses(page);
    await expect(page.getByTestId('address-row')).toHaveCount(2);

    const second = page.getByTestId('address-row').nth(1);
    await second.getByTestId('address-set-default').click();

    await expect(page.getByTestId('notice')).toContainText('已设为默认地址');
    // 默认在前：切换后第 2 条排到最前
    await expect(page.getByTestId('address-row').first()).toContainText('默认');
    await expect(page.getByTestId('address-row').first()).toContainText(
      addressFixture(2).receiver_name,
    );

    const book = await listAddresses(ctx, buyerToken);
    expect(book.items.filter((item) => item.is_default)).toHaveLength(1);
    expect(book.items[0]!.is_default).toBe(true);
  });

  test('AD-04 编辑不改默认标记', async ({ page }) => {
    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenAddresses(page);

    const first = page.getByTestId('address-row').first();
    await expect(first).toContainText('默认');
    await first.getByTestId('address-edit').click();

    const inputs = formInputs(page);
    await inputs.detail.fill('E2E 改过的详细地址 99 号');
    await page.getByTestId('address-save').click();

    await expect(page.getByTestId('notice')).toContainText('地址已保存');
    await expect(page.getByTestId('address-row').first()).toContainText('E2E 改过的详细地址 99 号');

    const book = await listAddresses(ctx, buyerToken);
    const edited = book.items.find((item) => item.detail_address === 'E2E 改过的详细地址 99 号');
    expect(edited?.is_default, '编辑不改默认标记').toBe(true);
  });

  test('AD-05 删除的内联二次确认', async ({ page }) => {
    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenAddresses(page);
    const before = await listAddresses(ctx, buyerToken);
    expect(before.items.length).toBeGreaterThanOrEqual(2);

    const row = page.getByTestId('address-row').last();
    await row.getByTestId('address-remove').click();
    await page.getByRole('button', { name: '再想想' }).click();
    await expect(page.getByTestId('address-row')).toHaveCount(before.items.length);

    await row.getByTestId('address-remove').click();
    await page.getByRole('button', { name: '确认删除' }).click();
    await expect(page.getByTestId('notice')).toContainText('已删除地址');
    await expect(page.getByTestId('address-row')).toHaveCount(before.items.length - 1);
  });

  test('AD-06 删默认后由最新一条补位；删光回空态', async ({ page }) => {
    // 从头造 3 条：删掉默认那条后，剩余里最新创建的补位
    const current = await listAddresses(ctx, buyerToken);
    for (const item of current.items) await deleteAddress(ctx, buyerToken, item.id);

    const first = await createAddress(ctx, buyerToken, addressFixture(3));
    // 表里 create_at 是秒级 DATETIME：同一秒内建的多条无法区分先后，补位会退化成按 id 排序。
    // 真实用户不会在同一秒建三条地址，这里按秒隔开，让「最新创建」有确定含义。
    await new Promise((resolve) => setTimeout(resolve, 1100));
    await createAddress(ctx, buyerToken, addressFixture(4));
    await new Promise((resolve) => setTimeout(resolve, 1100));
    const newest = await createAddress(ctx, buyerToken, addressFixture(5));
    expect(first.is_default).toBe(true);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenAddresses(page);
    await expect(page.getByTestId('address-row')).toHaveCount(3);

    // 列表按「默认在前」排序：默认那条就是第一行
    await page.getByTestId('address-row').first().getByTestId('address-remove').click();
    await page.getByRole('button', { name: '确认删除' }).click();

    await expect(page.getByTestId('address-row')).toHaveCount(2);
    const promoted = page.getByTestId('address-row').first();
    await expect(promoted).toContainText(newest.receiver_name);
    await expect(promoted.getByTestId('status-tag')).toHaveText('默认');

    // 删光
    for (let i = 0; i < 2; i += 1) {
      await page.getByTestId('address-row').first().getByTestId('address-remove').click();
      await page.getByRole('button', { name: '确认删除' }).click();
    }
    await expect(page.getByText('还没有收货地址')).toBeVisible();
  });

  test('AD-07 上限 10 条：第 10 条能建，第 11 条被拦', async ({ page }) => {
    const current = await listAddresses(ctx, buyerToken);
    for (const item of current.items) await deleteAddress(ctx, buyerToken, item.id);
    for (let i = 1; i <= 9; i += 1) await createAddress(ctx, buyerToken, addressFixture(i));

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenAddresses(page);
    await createViaUi(page, addressFixture(9));
    await expect(page.getByTestId('address-row')).toHaveCount(10);

    await expect(page.getByTestId('address-new')).toBeDisabled();
    await expect(page.getByText('地址已达上限，删掉不用的才能再新建')).toBeVisible();

    const over = await ctx.fetch('/api/buyer/addresses', {
      method: 'POST',
      headers: { Authorization: `Bearer ${buyerToken}`, 'Content-Type': 'application/json' },
      data: JSON.stringify(addressFixture(10)),
    });
    const body = (await over.json()) as { code: number };
    expect(body.code, '第 11 条应被后端拦下').toBe(80002);
  });

  test('AD-08 字段校验与后端兜底', async ({ page }) => {
    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenAddresses(page);

    // 先删一条腾出位置再开表单
    const current = await listAddresses(ctx, buyerToken);
    await deleteAddress(ctx, buyerToken, current.items[0]!.id);
    await page.reload();

    await page.getByTestId('address-new').click();
    const inputs = formInputs(page);
    await inputs.name.fill('E2E 校验');
    await inputs.phone.fill('123456');
    await inputs.detail.fill('E2E 校验地址');
    await page.getByTestId('address-save').click();

    await expect(page.getByTestId('address-form').locator('p.text-error')).toContainText('手机号');
    await expect(page.getByTestId('address-form')).toBeVisible();

    // 201 字超长走 DTO 的 @Size 校验 → HTTP 400 + 50002（不是 80003，实跑如此）
    const badDetail = await ctx.fetch('/api/buyer/addresses', {
      method: 'POST',
      headers: { Authorization: `Bearer ${buyerToken}`, 'Content-Type': 'application/json' },
      data: JSON.stringify({
        receiver_name: '校验',
        receiver_phone: '13800138000',
        detail_address: 'A'.repeat(201),
      }),
    });
    expect(badDetail.status()).toBe(400);
    const badDetailBody = (await badDetail.json()) as {
      code: number;
      errors?: Array<{ field: string }>;
    };
    expect(badDetailBody.code).toBe(50002);
    expect(badDetailBody.errors?.some((item) => item.field.includes('detail_address'))).toBe(true);

    // 收货人带换行能过 DTO（是字符串），由领域规则拒绝 → 80003
    const badName = await ctx.fetch('/api/buyer/addresses', {
      method: 'POST',
      headers: { Authorization: `Bearer ${buyerToken}`, 'Content-Type': 'application/json' },
      data: JSON.stringify({
        receiver_name: '换\n行',
        receiver_phone: '13800138000',
        detail_address: 'E2E 地址',
      }),
    });
    expect((await badName.json() as { code: number }).code).toBe(80003);
  });

  test('AD-09 订单收货信息是下单时的快照', async () => {
    const snapshotBuyer = buyerFixture('snap');
    const snapshotToken = await newBuyer(ctx, snapshotBuyer);
    const address = await createAddress(ctx, snapshotToken, addressFixture(6));
    const order = await placeOrder(ctx, snapshotToken, [{ goods_id: goods.id, quantity: 1 }]);

    // 改地址再删掉，订单快照不应受影响
    await updateAddress(ctx, snapshotToken, address.id, {
      receiver_name: '改过的收货人',
      receiver_phone: '13700000000',
      detail_address: '改过的地址',
    });
    await deleteAddress(ctx, snapshotToken, address.id);

    const detail = await sellerOrderDetail(ctx, sellerToken, order.order_id);
    expect(detail.receiver_name).toBe(address.receiver_name);
    expect(detail.receiver_phone).toBe(address.receiver_phone);
    expect(detail.receiver_address).toBe(address.detail_address);
  });

  test('AD-10 结算改选第二条地址', async ({ page }) => {
    const pickBuyer = buyerFixture('pick');
    const pickToken = await newBuyer(ctx, pickBuyer);
    const first = await createAddress(ctx, pickToken, addressFixture(7));
    const second = await createAddress(ctx, pickToken, addressFixture(8));
    await addToCart(ctx, pickToken, goods.id);

    await buyerLoginUi(page, pickBuyer.username, pickBuyer.password);
    await buyerOpenCart(page);
    await page.getByRole('button', { name: '去结算' }).click();
    await expect(page).toHaveURL(/\/checkout$/);

    // 进页面预选的是默认地址（第一条）
    const defaultRadio = page
      .getByTestId('checkout-address')
      .filter({ has: page.locator(':checked') });
    await expect(page.getByTestId('checkout-address').first()).toBeChecked();
    expect(defaultRadio).toBeTruthy();

    // 改选第二条并提交
    await page
      .locator('label')
      .filter({ hasText: second.receiver_name })
      .getByTestId('checkout-address')
      .check();
    await page.getByTestId('checkout-submit').click();
    await expect(page).toHaveURL(/\/orders\/[^/]+$/);

    const orderId = new URL(page.url()).pathname.split('/').pop()!;
    const detail = await sellerOrderDetail(ctx, sellerToken, orderId);
    expect(detail.receiver_name).toBe(second.receiver_name);
    expect(detail.receiver_address).toBe(second.detail_address);
    expect(detail.receiver_name).not.toBe(first.receiver_name);
  });
});
