/**
 * 页面操作辅助（走真实 UI，不用后门）
 */
import type { Page } from '@playwright/test';
import { BUYER_URL, SELLER_URL, SELLER_ACCOUNT, SELLER_PASSWORD } from './config';

/** 卖家端登录（真实点界面） */
export async function uiLogin(page: Page): Promise<void> {
  await page.goto(`${SELLER_URL}/login`);
  await page.fill('#account', SELLER_ACCOUNT);
  await page.fill('#password', SELLER_PASSWORD);
  await page.click('.login__submit');
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 20_000 });
}

/**
 * 通用确认弹窗（ConfirmDialog）：点「确认」
 * ⚠️ exact:true 必须加 —— 否则会误匹配到「确认标记失败」（子串匹配）
 */
export async function confirmDialog(page: Page): Promise<void> {
  const dlg = page.getByRole('dialog');
  await dlg.waitFor({ state: 'visible' });
  await dlg.getByRole('button', { name: '确认', exact: true }).click();
}

/** 买家端提交一条意向 → 返回口令码（卖家端剧本造数据用） */
export async function submitBuyerIntention(
  page: Page,
  name: string,
  tel: string,
): Promise<string> {
  await page.goto(`${BUYER_URL}/intent/new`);
  await page.fill('#fName', name);
  await page.fill('#fTel', tel);
  await page.click('#btnSubmit');
  await page.click('#confirmOk'); // 二次确认层
  const code = page.locator('#tokenCode');
  await code.waitFor({ state: 'visible' });
  return ((await code.textContent()) || '').trim();
}

/** 买家端凭口令码查询 → 返回结果区文本 */
export async function buyerQuery(page: Page, token: string): Promise<string> {
  await page.goto(`${BUYER_URL}/query`);
  await page.fill('#fToken', token);
  await page.getByRole('button', { name: '查询' }).click();
  await page.locator('.result').first().waitFor({ state: 'visible' });
  return await page.locator('.result').first().innerText();
}
