# simple-shop-buyer-frontend

在线购物系统 · 买家端前端（前后端分离）。本工程只包含买家端，卖家端是独立的 `simple-shop-seller-frontend` 工程，两端不互相依赖。

> **阶段进度：阶段四买家端已交付。** 功能域为商品浏览、账号、购物车、收藏、地址簿、结算与支付、订单、售后八块。订单是七态主链，交易完成由**买家确认收货**推进。整体进度见[根 README](../README.md)。

## 先看哪里

| 要找什么 | 去哪 |
| --- | --- |
| 接口清单、字段、错误码 | [openapi.json](../simple-shop-doc/阶段四/openapi.json) + 本工程 `src/api/types.ts`（与契约逐字对应） |
| 页面与登录要求 | `src/router/index.ts`（`meta.requiresAuth`） |
| 业务码 → 界面文案 | `src/utils/errorText.ts`（唯一来源） |
| 订单动作矩阵 | `src/utils/orderStatus.ts` |

## 技术栈

Vue 3 + Vite + TypeScript + Pinia + Vue Router + Axios。
样式：Tailwind CSS 4 + daisyUI 5，唯一入口 `assets/base.css`（`shop-light` / `shop-dark` 双主题，token 与卖家端同值）。
单元测试：Vitest（`test/` 目录，node 环境，无需后端、不连数据库、不起浏览器）。

## 功能域

| 功能域 | 能力 | 是否需要登录 |
| --- | --- | --- |
| 商品浏览 | 列表（12 条/页）、关键词搜索、按类别筛选、详情 | 否 |
| 账号 | 注册、登录、退出；登录后顶栏显示用户名 | 否（注册/登录本身） |
| 购物车 | 加购、改数量、勾选/全选、移除、失效行、合计、转收藏、去结算 | 是 |
| 收藏 | 详情页收藏/取消、收藏列表（12 条/页）、失效标注、单条取消 | 是 |
| 地址簿 | 列表（默认在前、上限 10）、新建、编辑、删除、设为默认 | 是 |
| 结算与支付 | 选已有地址下单；四渠道支付、按 `pay_deadline` 倒计时、失败可重试 | 是 |
| 订单 | 列表（10 条/页、七态筛选）、详情（头/明细/收货/支付/物流/退款/售后）、发货前取消、确认收货 | 是 |
| 售后 | 按订单项申请（标题/描述/≤9 张凭证图）、我的售后列表与详情 | 是 |

## 阶段四的关键口径

- **订单七态**：`PENDING_PAYMENT` → `PAID` → `CONFIRMED` → `PREPARED` → `SHIPPED` → `COMPLETED`，另有终态 `CANCELED`。
  买家动作矩阵（`utils/orderStatus.ts` 集中派生，页面只调函数）：

  | 状态 | 去支付 | 取消 | 确认收货 | 申请售后 |
  | --- | --- | --- | --- | --- |
  | 待支付 | ✅ | ✅ | — | — |
  | 已支付 / 已确认 / 备货完成 | — | ✅（全额退） | — | — |
  | 已发货 | — | — | ✅ | — |
  | 交易完成 | — | — | — | ✅ |
  | 已取消 | — | — | — | — |

- **倒计时只认 `pay_deadline`**：时限是后端配置，前端不按 24 小时自算。归零时只提示并给「刷新」，
  不本地改状态——后端超时扫描默认 60 秒一轮，此刻订单可能还没被置为已取消。
- **售后入口只用 `order_item_id`**：同一商品可能出现在多张订单里，用 `goods_id` 会指错行。
- **金额只用后端值**：`total_amount`、`amount`、`refunded_amount`、`refund_amount` 一律直接展示。
  `refunded_amount` 是「取消退款 + 售后退款」的和，由后端查询时算，前端不再相加。
- **售后列表不内嵌在订单详情**：按 `order_id` 单独查，与订单头、明细三块独立加载、独立报错、各自可重试。
- **申请售后不选类型**：退货退款 / 仅退款 / 换货是卖家处理时登记的结果；买家只能发起与查看，不能撤销或改状态。
- **凭证图先上传再申请**：只提交买家上传接口返回的 URL，外链与商品图目录地址会被拒。
- **下单必须选已有地址**：结算页不支持新建地址；订单存收货快照，之后改地址簿不影响历史订单。

## 本地存储约定

后端购物车表只有「买家 + 商品 + 加购时间」，**没有数量列也没有勾选列**。数量与勾选只存在浏览器里，按买家隔离。

| 键 | 内容 | 写入时机 | 清除时机 |
| --- | --- | --- | --- |
| `simple-shop.buyer.token` | 会话令牌 | 登录/注册成功 | 退出登录、收到 401 / 会话失效码 |
| `simple-shop.buyer.expire-at` | 令牌到期时间 | 同上 | 同上 |
| `simple-shop.buyer.username` | 用户名（隔离购物车本地状态、顶栏展示） | 同上 | 同上 |
| `simple-shop.buyer.theme` | 主题偏好 `shop-light` / `shop-dark` | 手动切换主题 | 不清 |
| `simple-shop.buyer.cart.<username>` | `{ "<goods_id>": { "quantity": 2, "checked": true } }` | 改数量、勾选、加购后 | 退出登录；或该用户名下购物车已空 |

- **按用户名分键**，换账号不串数据；退出登录只清当前用户名那一份。
- **读坏数据当空**：JSON 解析失败、结构不对、数量小于 1 一律回落默认值。
- **孤儿记录自动清**：本地有、服务端没有的 `goods_id` 在每次拉购物车时丢弃；服务端有、本地没有的补 `{1, true}`。
- **不可买强制取消勾选**：`selectable === false` 或库存为 0 的行一律置 false。
- **结算页自己 `load()`**：勾选在本地，所以直接刷新 `/checkout` 也能还原清单，不依赖购物车页的内存状态。

## 错误码处理

- 界面**不展示后端 `message` 原文**：它是给日志与排查用的英文，一律按业务码映射成中文，映射表在 `utils/errorText.ts`，新增码只改那一处。
- 未登记的码统一走「操作失败，请稍后再试」，不猜原因；404 兜底也不硬说成「商品」，具体是哪类资源由业务码决定。
- 已停用的码位（`10008`、`20020` 与整段 `3xxxx`/`4xxxx`）不再声明、不复用；前端单测断言它们落到通用兜底。
- 两处有意偏离：下单场景的参数错误有专属文案（那里没有表单可填）；会话失效统一在 `main.ts` 清令牌并跳登录。

## 目录结构

```text
src/
  api/           axios 封装与按域拆分的接口模块（http / token / types + 商品、购物车、订单、地址、售后…）
  stores/        auth（登录态）、cart（服务端行 + 本地数量勾选）、address
  views/         14 个页面（路由表见 router/index.ts）
  components/    common（AppIcon / StatusTag / Pagination / EmptyState / FormModal）与按域分包
  composables/   usePagedList（分页列表）、useCountdown（支付倒计时）、useTheme
  validation/    注册、地址、售后表单校验
  utils/         金额与时间格式化、订单与售后状态、错误码文案、购物车本地存储
  router/        路由与登录守卫
  assets/base.css  Tailwind + daisyUI 唯一 CSS 入口
test/            Vitest 单元测试（按模块镜像 utils / validation / composables / stores）
```

## 本地开发

### 1. 先启动后端

```bash
cd ../simple-shop-backend
mvn spring-boot:run
```

需要本机 MySQL，连接信息见后端 `application.yml`（`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 可用环境变量覆盖）。

> ⚠️ **阶段四要按 V4 重建库**：V4 给订单加的是 `NOT NULL` 无默认值的收货快照列，订单表必须为空才能执行。
> 旧库会因校验和或列约束启动失败。**不要关闭 Flyway 校验硬绕**，而是新建干净库并用 `DB_URL` 指过去；
> 删库属破坏性操作，执行前先确认。

后端起来后：接口文档 <http://localhost:8080/swagger-ui/index.html>。

联调常用开关：`PAYMENT_MOCK_FAIL_CHANNELS=ALIPAY` 复现支付失败；`ORDER_PAYMENT_TIMEOUT` 调短复现超时；
批量造数时 `RATE_LIMIT_ENABLED=false`。

### 2. 启动前端

```bash
npm install      # 首次
npm run dev      # http://localhost:5174
```

`vite.config.ts` 已把 `/api` 与 `/media` 代理到 `http://localhost:8080`。
换后端地址用 `VITE_API_BASE_URL=http://其它地址:8080/api npm run dev`。

### 3. 构建与测试

```bash
npm run build        # 含 vue-tsc 类型检查（test/ 也纳入），输出到 dist/
npm run test:unit    # Vitest 跑 test/，无需后端
npm run test:watch   # watch 模式
```

## 实现约定

- **响应统一 `{code, message, data}`**，`code = 0` 成功。业务拒绝多数走 HTTP 200；401/400/404/500 用 HTTP 状态。
- **401 统一跳登录**：`http.ts` 的 `toApiError()` 把 401 转成会话失效后走 `fail()`，
  由 `main.ts` 注册的回调清令牌并跳登录。⚠️ 新增请求分支（含 `put`/`upload`）不要绕开 `fail()`。
- **字段名保持 snake_case**，不做驼峰转换。
- **业务规则尽量不在前端复制**：可否购买看 `purchasable`，购物车行可用看 `selectable && stock > 0`。
  **唯一例外是订单按钮矩阵**（接口没有 `can_*` 标记），集中在 `utils/orderStatus.ts`。
- **时间以 UTC 存、按浏览器本地时区展示**，统一走 `utils/format.ts`。
- **改页面优先复用** `components/common`、`composables`、`stores`，不要为单个页面另起一套请求/状态实现。
- 移动端暂不做，只保留基础响应式；演示与验收在桌面浏览器。

## 样式约定

唯一 CSS 入口是 `assets/base.css`（`@import "tailwindcss"` + `@plugin "daisyui"` + 两套主题），与卖家端共用同一套主题 token。

- 优先级：daisyUI 组件类 > Tailwind 工具类 > `@layer components` 复合类。**不要写未分层的组件样式**，否则会压过工具类，之后只能靠 `!important` 打架。
- 写样式优先用 daisyUI 组件类，其次 Tailwind 工具类；确有全局复用的复合类再放进 `@layer components`。
- **不与 daisyUI 重名**：`.btn`、`.card`、`.input`、`.textarea`、`.select`、`.table`、`.badge`、`.menu`、`.modal`、`.toast`、`.alert` 归 daisyUI 所有，项目里不再定义同名类，也不给这些元素补裸写覆盖。
- **改 daisyUI 组件自身外观用 `@utility`**：它的组件类落在 utilities 层子层，`@layer components` 盖不住（会被静默忽略）。
- **颜色只用语义色**：`base-100/200/300`、`base-content`、`primary`、`success`、`warning`、`error`、`info` 及对应 `-content`；模板与 scoped 样式里不写死色值，弱化文字用 `text-base-content/60`。
- **叠加层用实色 `var(--color-surface-solid)`**：浅色的 `--color-base-100` 是半透明，盖在内容之上的界面必须不透明，否则背后文字会透上来——原生 `<select>` 的弹出列表、`option`、`modal-box` 都用它。新增浮层时不要直接用 `bg-base-100`。
- **两套主题**：`data-theme="shop-light" | "shop-dark"` 挂在 `<html>`；切换与持久化只走 `composables/useTheme`，页面内不要直接改 `document.documentElement.dataset.theme`；首屏由 `index.html` 内联脚本先定，避免闪白。
- **图标统一走 `AppIcon`**（内联 SVG + `currentColor`），不要在模板里 `<img src="*.svg">`——`assets/` 里的图标是固定黑色填充，深色主题下会糊成黑块。
- **SFC `<style scoped>` 里用 `@apply` 需 `@reference` 主 CSS**，否则报 unknown utility；scoped 里优先纯语义变量或全工具类。
- 尺寸取 Tailwind 默认档，不使用卖家的 `-sm` 后台密度——买家端是 storefront，信息密度诉求不同。

## 与卖家端的实现差异

买家端与卖家端目前有几处交互不一致，属**实现差异，不是项目约定**；不要把它们当成必须保持的规则，改动时也不必为了「保持两端一致」而回避调整。

| 位置 | 买家端现状 | 卖家端现状 |
| --- | --- | --- |
| 确认动作 | 内联二次确认（「确认 X / 再想想」） | `ConfirmDialog` 弹窗 |
| 操作结果提示 | 内联 notice / alert | 全局 toast |
| 页面骨架 | 960px 居中顶栏导航 | 左侧固定侧边栏 |
| 表单容器 | `FormModal` 弹窗，新建与编辑共用一个表单组件 | 独立页面 / 行内表单 |

买家端表单的既有约束（与两端差异无关，属可访问性要求）：用包裹式 `<label>` 或 `for`/`id` 关联控件，保证 `getByLabel` 有可访问名；`FormModal` 点遮罩或「取消」关闭，Esc 明确不关闭。

## 已知限制

- **订单按钮矩阵由前端按状态派生**：`OrderData` 没有 `can_cancel` 这类标记，只能在前端维护一份与《契约交接》对齐的矩阵。后端若调整边界，这里要同步改。
- **订单状态筛选只支持单状态**：后端按枚举解析 `status`，组合值会被拒绝，所以做成下拉单选。
- **购物车合计前端算**：接口只给单价与库存，合计按「分」累加再折回元；下单总额仍以后端为准。
- **`selectable` 只表示在售、不含库存**：所以「能否下单」前端还要补一个 `stock > 0`。
- **下单不会自动清购物车**：后端订单服务不引用购物车表，下单成功后由前端删行。
- **售后「已取消」不区分买家撤销与卖家拒绝**：后端没有独立拒绝态，两者是同一个动作。
- **商品/收藏列表 12 条/页，订单与售后列表 10 条/页**；后端 `page_size` 上限 100。

## 买家端不做的事

改资料、改密码、找回密码、购物车多规格、客服；
真实支付网关与支付宝沙箱、物流轨迹与状态枚举、地址省市区结构化；
售后按数量部分退款、买家撤销售后、申请时选售后类型；
超时自动确认收货、运费/优惠/发票/支付流水明细页；
卖家端的任何功能。
