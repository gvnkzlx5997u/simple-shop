import { expect, test, type APIRequestContext } from '@playwright/test';

import {
  addFavorite,
  addToCart,
  createL1,
  createL2,
  listBuyerOrders,
  listCart,
  listFavorites,
  newApi,
  newBuyer,
  offShelf,
  publishGoods,
  removeFavorite,
  removeFromCart,
  sellerLogin,
} from '../helpers/api';
import type { GoodsView } from '../helpers/api';
import { buyerFixture, goodsFixture, uniqueName } from '../helpers/data';
import { BUYER_URL } from '../helpers/config';
import {
  buyerAddToCart,
  buyerCartRow,
  buyerFavoriteCard,
  buyerLoginUi,
  buyerOpenCart,
  buyerOpenFavorites,
} from '../helpers/ui';

/**
 * 02b 买家购物车与收藏（Backlog 10–15、1 的买家视角）
 *
 * 覆盖点：
 *  - 未登录进购物车/收藏/订单要跳登录；
 *  - 行按「买家 + 商品」唯一，重复加购不新增行；
 *  - 数量、勾选只存 localStorage（换设备/换 context 不保留），金额随之变化；
 *  - 数量超库存时夹到库存上限；
 *  - 已下架商品留在购物车但置灰、不可勾选、不能结算；
 *  - 收藏/取消收藏、收藏列表里的已下架商品不可进详情；
 *  - 购物车勾选后「转收藏」：购物车少行、收藏多件。
 *
 * 购物车不落库数量与勾选是阶段三的明确取舍（Backlog D3），所以「换 context 不保留」是断言而不是缺陷。
 */
test.describe.serial('02b 买家购物车与收藏', () => {
  let ctx: APIRequestContext;
  let sellerToken: string;
  let buyerToken: string;
  let l2Id: string;
  let main: GoodsView;
  let second: GoodsView;
  let cartInvalid: GoodsView;
  let favInvalid: GoodsView;
  const buyer = buyerFixture('cart');

  async function clearCart(): Promise<void> {
    const cart = await listCart(ctx, buyerToken);
    for (const row of cart.items) {
      await removeFromCart(ctx, buyerToken, row.goods_id);
    }
  }

  async function clearFavorites(): Promise<void> {
    const favorites = await listFavorites(ctx, buyerToken);
    for (const row of favorites.items) {
      await removeFavorite(ctx, buyerToken, row.goods_id);
    }
  }

  test.beforeAll(async () => {
    ctx = await newApi();
    sellerToken = await sellerLogin(ctx);
    const tag = uniqueName('cart');
    const l1 = await createL1(ctx, sellerToken, `购物车一级-${tag}`);
    const l2 = await createL2(ctx, sellerToken, l1.id, `购物车二级-${tag}`);
    l2Id = l2.id;

    main = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('购物车主商品'), l2Id, { stock: 5, price: '12.50' }),
    );
    second = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('购物车次商品'), l2Id, { stock: 3, price: '20.00' }),
    );
    cartInvalid = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('购物车待下架'), l2Id, { stock: 2, price: '5.00' }),
    );
    favInvalid = await publishGoods(
      ctx,
      sellerToken,
      goodsFixture(uniqueName('收藏待下架'), l2Id, { stock: 2, price: '6.00' }),
    );

    buyerToken = await newBuyer(ctx, buyer);
  });

  test.afterAll(async () => {
    await ctx.dispose();
  });

  test('T02b-01 未登录进购物车 / 收藏 / 订单 → 跳登录并带回来路', async ({ page }) => {
    for (const path of ['/cart', '/favorites', '/orders']) {
      await page.goto(`${BUYER_URL}${path}`);
      await expect(page, `${path} 未登录应跳登录页`).toHaveURL(/\/login/);
      await expect(page).toHaveURL(/redirect/);
    }
  });

  test('T02b-02 重复加购：同一商品仍只有一行', async ({ page }) => {
    await clearCart();
    await buyerLoginUi(page, buyer.username, buyer.password);

    await page.goto(`${BUYER_URL}/goods/${main.id}`);
    await buyerAddToCart(page);
    await expect(page.locator('.notice')).toContainText('已加入购物车');

    // 同一按钮再点一次：提示已在车里，不新增行
    await page.getByRole('button', { name: '加入购物车' }).click();
    await expect(page.locator('.notice')).toContainText('已经在购物车里了');

    await buyerOpenCart(page);
    await expect(page.locator('.row-item')).toHaveCount(1);

    const cart = await listCart(ctx, buyerToken);
    expect(cart.items, '库里也只应有一行').toHaveLength(1);
  });

  test('T02b-03 改数量：小计与合计跟着变，超库存夹到上限', async ({ page }) => {
    await clearCart();
    await addToCart(ctx, buyerToken, main.id);
    await addToCart(ctx, buyerToken, second.id);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCart(page);

    // 默认各 1 件、都勾选：12.50 + 20.00
    await expect(page.locator('.total strong')).toHaveText('¥32.50');

    const mainRow = buyerCartRow(page, main.name);
    await mainRow.locator('.qty-input').fill('2');
    await mainRow.locator('.qty-input').blur();
    await expect(mainRow.locator('.line-total')).toHaveText('¥25.00');
    await expect(page.locator('.total strong')).toHaveText('¥45.00');

    // 填 99（超过库存 5）→ 失焦后夹到 5
    await mainRow.locator('.qty-input').fill('99');
    await mainRow.locator('.qty-input').blur();
    await expect(mainRow.locator('.qty-input')).toHaveValue('5');
    await expect(page.locator('.total strong')).toHaveText('¥82.50');
  });

  test('T02b-04 移除一行：该行消失、总价同步', async ({ page }) => {
    // 每个用例是独立的浏览器上下文，登录态不跨用例，必须各自登录
    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCart(page);

    // 数量也只存在当前上下文的 localStorage 里，先显式设成 2 再算账
    const mainRow = buyerCartRow(page, main.name);
    await mainRow.locator('.qty-input').fill('2');
    await mainRow.locator('.qty-input').blur();
    await expect(page.locator('.total strong')).toHaveText('¥45.00');

    const secondRow = buyerCartRow(page, second.name);
    await secondRow.locator('button.remove').click();
    await expect(secondRow).toHaveCount(0);
    await expect(page.locator('.total strong')).toHaveText('¥25.00');

    const cart = await listCart(ctx, buyerToken);
    expect(cart.items.map((row) => row.goods_id)).toEqual([main.id]);
  });

  test('T02b-05 数量与勾选不落库：换一个浏览器上下文登录同账号 → 回默认', async ({
    page,
    browser,
  }) => {
    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCart(page);

    // 本上下文改成 3 件 + 取消勾选（只写 localStorage）
    const mainRow = buyerCartRow(page, main.name);
    await mainRow.locator('.qty-input').fill('3');
    await mainRow.locator('.qty-input').blur();
    await mainRow.locator('.row-check').uncheck();
    await expect(page.getByRole('button', { name: '去结算' })).toBeDisabled();

    // 换一个全新上下文（等价于换设备）：同一个账号，本地状态是空的
    const fresh = await browser.newContext();
    const freshPage = await fresh.newPage();
    await buyerLoginUi(freshPage, buyer.username, buyer.password);
    await buyerOpenCart(freshPage);
    const freshRow = buyerCartRow(freshPage, main.name);
    await expect(freshRow.locator('.qty-input'), '数量应回到默认 1').toHaveValue('1');
    await expect(freshRow.locator('.row-check'), '默认是勾选').toBeChecked();

    // 服务端仍然只有一行（数量/勾选从不落库）
    const cart = await listCart(ctx, buyerToken);
    expect(cart.items).toHaveLength(1);
    await fresh.close();
  });

  test('T02b-06 已下架商品：留在购物车但置灰、不可勾选、不能结算', async ({ page }) => {
    await clearCart();
    await addToCart(ctx, buyerToken, cartInvalid.id);

    // 卖家下架后再打开发购物车
    await offShelf(ctx, sellerToken, cartInvalid.id);
    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCart(page);

    const row = buyerCartRow(page, cartInvalid.name);
    await expect(row, '行仍在，但带 disabled 类').toHaveClass(/disabled/);
    await expect(row.locator('.row-check')).toBeDisabled();
    await expect(row).toContainText('已下架');
    await expect(page.locator('.warn-bar')).toContainText('已下架或缺货');
    await expect(page.getByRole('button', { name: '去结算' })).toBeDisabled();
  });

  test('T02b-07 勾选两件去结算：生成 1 笔订单，明细 2 行', async ({ page }) => {
    await clearCart();
    await addToCart(ctx, buyerToken, main.id);
    await addToCart(ctx, buyerToken, second.id);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCart(page);
    const mainRow = buyerCartRow(page, main.name);
    await mainRow.locator('.qty-input').fill('2');
    await mainRow.locator('.qty-input').blur();
    await expect(page.locator('.total strong')).toHaveText('¥45.00');

    await page.getByRole('button', { name: '去结算' }).click();

    // 成功后直接进订单详情（不是留在购物车弹提示）
    await expect(page).toHaveURL(/\/orders\/[^/]+$/);
    const orders = await listBuyerOrders(ctx, buyerToken);
    const latest = orders.items[0];
    expect(latest.item_count, '两件商品应是一笔订单里的 2 行').toBe(2);
    expect(Number(latest.total_amount)).toBe(45);

    await expect(page.locator('.items tbody tr')).toHaveCount(2);
    await expect(page.locator('.amount')).toContainText('¥45.00');

    // 已下单的行从购物车消失（前端下单成功后清掉本地与服务端行）
    const cart = await listCart(ctx, buyerToken);
    expect(cart.items).toHaveLength(0);
  });

  test('T02b-08 收藏 / 取消收藏：按钮态回显、列表同步', async ({ page }) => {
    await clearFavorites();
    await buyerLoginUi(page, buyer.username, buyer.password);

    await page.goto(`${BUYER_URL}/goods/${main.id}`);
    await page.getByRole('button', { name: '收藏', exact: true }).click();
    await expect(page.getByRole('button', { name: '已收藏' })).toBeVisible();

    await buyerOpenFavorites(page);
    await expect(buyerFavoriteCard(page, main.name)).toHaveCount(1);

    await buyerFavoriteCard(page, main.name).getByRole('button', { name: '取消收藏' }).click();
    await expect(buyerFavoriteCard(page, main.name)).toHaveCount(0);
  });

  test('T02b-09 收藏里的已下架商品：标注「已下架」且点不进详情', async ({ page }) => {
    await addFavorite(ctx, buyerToken, favInvalid.id);
    await offShelf(ctx, sellerToken, favInvalid.id);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenFavorites(page);

    const card = buyerFavoriteCard(page, favInvalid.name);
    await expect(card).toHaveClass(/disabled/);
    await expect(card.locator('.badge')).toHaveText('已下架');

    // 点名称不会跳详情，仍停在收藏页
    await card.locator('.name').click();
    await expect(page).toHaveURL(/\/favorites/);
  });

  test('T02b-10 购物车勾选两件转收藏：购物车少两行、收藏多两件', async ({ page }) => {
    await clearCart();
    await clearFavorites();
    await addToCart(ctx, buyerToken, main.id);
    await addToCart(ctx, buyerToken, second.id);

    await buyerLoginUi(page, buyer.username, buyer.password);
    await buyerOpenCart(page);
    await expect(page.locator('.row-item')).toHaveCount(2);

    await page.getByRole('button', { name: '转收藏' }).click();
    await expect(page.locator('.notice')).toContainText('移到收藏');
    await expect(page.locator('.row-item')).toHaveCount(0);

    const favorites = await listFavorites(ctx, buyerToken);
    expect(favorites.items.map((row) => row.goods_id).sort()).toEqual([main.id, second.id].sort());

    await buyerOpenFavorites(page);
    await expect(buyerFavoriteCard(page, main.name)).toHaveCount(1);
    await expect(buyerFavoriteCard(page, second.name)).toHaveCount(1);
  });
});
