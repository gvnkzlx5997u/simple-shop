# simple-shop-buyer-frontend

在线购物系统 · 买家端前端（前后端分离）。

对应后端：`simple-shop-backend`（Spring Boot，REST + 媒体文件，不输出 HTML）。
接口契约以 `simple-shop-doc/阶段四/openapi.json` 与《阶段四契约交接》为准，后端 Swagger 在 `/swagger-ui/index.html`。

> **当前状态：阶段四买家端已交付。** 功能域为商品浏览、账号、购物车、收藏、地址簿、结算与支付、订单、售后八块。
> 订单是七态主链，交易完成由**买家确认收货**推进；卖家端与 E2E 的改造由他人负责。

## 技术栈

Vue 3 + Vite + TypeScript + Pinia + Vue Router + Axios。
样式：Tailwind CSS 4 + daisyUI 5，唯一入口 `assets/base.css`（`shop-light` / `shop-dark` 双主题，token 与卖家端同值；买家用默认尺寸，不取后台 `-sm` 密度）。
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

## 路由

| 路由 | 名称 | 页面 | 登录 |
| --- | --- | --- | --- |
| `/` | — | 重定向到 `/goods` | — |
| `/goods` | `goods-list` | 商品列表 / 搜索 | 否 |
| `/goods/:id` | `goods-detail` | 商品详情（加购、收藏入口） | 否 |
| `/cart` | `cart` | 购物车 | 是 |
| `/checkout` | `checkout` | 结算（选地址 + 复核清单 + 提交） | 是 |
| `/favorites` | `favorites` | 我的收藏 | 是 |
| `/orders` | `orders` | 我的订单 | 是 |
| `/orders/:id` | `order-detail` | 订单详情 | 是 |
| `/orders/:id/pay` | `order-pay` | 支付 | 是 |
| `/addresses` | `addresses` | 收货地址 | 是 |
| `/aftersales` | `aftersales` | 我的售后 | 是 |
| `/aftersales/apply` | `aftersale-apply` | 申请售后（`?order_id=&order_item_id=`） | 是 |
| `/aftersales/:id` | `aftersale-detail` | 售后详情 | 是 |
| `/login` | `login` | 登录 | 否 |
| `/register` | `register` | 注册 | 否 |
| `/:pathMatch(.*)*` | — | 兜底重定向到 `/goods` | — |

需要登录的页面在 `router/index.ts` 里用 `meta.requiresAuth` 标记，守卫统一跳 `/login?redirect=<原路径>`。

## 接口

| 用途 | 方法 | 路径 |
| --- | --- | --- |
| 商品列表 / 类别树 / 商品详情 | GET | `/api/public/goods`、`/api/public/categories`、`/api/public/goods/{id}` |
| 注册 | POST | `/api/buyer/auth/register` |
| 登录 / 退出 | POST / DELETE | `/api/buyer/auth/session` |
| 购物车列表 / 加购 | GET / POST | `/api/buyer/cart` |
| 移除购物车行 | DELETE | `/api/buyer/cart/{goodsId}` |
| 转收藏 | POST | `/api/buyer/cart/move-to-favorites` |
| 收藏列表 / 收藏 | GET / POST | `/api/buyer/favorites` |
| 收藏状态 / 取消 | GET / DELETE | `/api/buyer/favorites/{goodsId}` |
| 地址列表 / 新建 | GET / POST | `/api/buyer/addresses` |
| 编辑 / 删除地址 | PUT / DELETE | `/api/buyer/addresses/{id}` |
| 设为默认 | POST | `/api/buyer/addresses/{id}/default` |
| 订单列表 / 下单 | GET / POST | `/api/buyer/orders` |
| 订单详情 / 明细 | GET | `/api/buyer/orders/{id}`、`/api/buyer/orders/{id}/items` |
| 支付 | POST | `/api/buyer/orders/{id}/pay` |
| 确认收货 | POST | `/api/buyer/orders/{id}/confirm-receipt` |
| 取消订单 | POST | `/api/buyer/orders/{id}/cancel` |
| 我的售后 / 申请 | GET / POST | `/api/buyer/aftersales` |
| 售后详情 | GET | `/api/buyer/aftersales/{id}` |
| 上传售后凭证图 | POST | `/api/buyer/media?purpose=AFTERSALE_IMAGE`（multipart 字段 `file`） |

`/api/public/health` 是运维探活端点，买家端不调用。

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
- **凭证图先上传再申请**：只提交 `/api/buyer/media` 返回的 URL，外链与商品图目录地址会被拒（20016）。
- **下单必须选已有地址**：结算页不支持新建地址；订单存收货快照，之后改地址簿不影响历史订单。

## 本地存储约定

后端购物车表只有「买家 + 商品 + 加购时间」，**没有数量列也没有勾选列**。数量与勾选只存在浏览器里，按买家隔离。

| 键 | 内容 | 写入时机 | 清除时机 |
| --- | --- | --- | --- |
| `simple-shop.buyer.token` | 会话令牌 | 登录/注册成功 | 退出登录、收到 401 / 业务码 10002 |
| `simple-shop.buyer.expire-at` | 令牌到期时间 | 同上 | 同上 |
| `simple-shop.buyer.username` | 用户名（隔离购物车本地状态、顶栏展示） | 同上 | 同上 |
| `simple-shop.buyer.theme` | 主题偏好 `shop-light` / `shop-dark` | 手动切换主题 | 不清 |
| `simple-shop.buyer.cart.<username>` | `{ "<goods_id>": { "quantity": 2, "checked": true } }` | 改数量、勾选、加购后 | 退出登录；或该用户名下购物车已空 |

- **按用户名分键**，换账号不串数据；退出登录只清当前用户名那一份。
- **读坏数据当空**：JSON 解析失败、结构不对、数量小于 1 一律回落默认值。
- **孤儿记录自动清**：本地有、服务端没有的 `goods_id` 在每次拉购物车时丢弃；服务端有、本地没有的补 `{1, true}`。
- **不可买强制取消勾选**：`selectable === false` 或库存为 0 的行一律置 false。
- **结算页自己 `load()`**：勾选在本地，所以直接刷新 `/checkout` 也能还原清单，不依赖购物车页的内存状态。

## 错误码映射

界面**不展示后端 `message` 原文**，一律按业务码映射成中文（`utils/errorText.ts` 的 `describeError`）。
后端共 **58** 个码；买家端 `ApiCode` 声明 **40** 个，其中 **39** 个有专属文案（`OK` 不需要）。

| 码 | 后端枚举 | 买家端文案 |
| --- | --- | --- |
| 10001 | `LOGIN_FAILED` | 用户名或密码不正确 |
| 10002 | `SESSION_INVALID` | 登录已失效，请重新登录（并跳登录） |
| 10004 | `PASSWORD_INVALID` | 密码不符合要求：至少 8 位 |
| 10005 | `USERNAME_TAKEN` | 这个用户名已经被注册了，换一个试试 |
| 10006 | `USERNAME_INVALID` | 用户名不符合要求：不能为空，不能含空格或尖括号，最长 50 字 |
| 10007 | `PHONE_INVALID` | 电话格式不对：请填 7~20 位数字，可含短横 |
| 10009 | `ACCOUNT_NOT_FOUND` | 账号不存在，请重新登录 |
| 10010 | `ACCOUNT_INVALID` | 账号信息不符合要求 |
| 20011 | `GOODS_NOT_FOUND` | 商品不存在或已下架 |
| 20015 | `STOCK_INVALID` | 库存不足，请回购物车调整数量 |
| 20016 | `IMAGE_INVALID` | 图片需为 JPG 或 PNG，且不超过 5MB |
| 20019 | `GOODS_NOT_ON_SALE` | 有商品已下架，请回购物车处理 |
| 20027 | `MEDIA_TYPE_NOT_ALLOWED` | 只能上传售后凭证图 |
| 50000 | `INTERNAL_ERROR` | 服务器出错了，请稍后再试 |
| 50001 | `TOO_MANY_REQUESTS` | 操作太频繁了，请稍后再试 |
| 50002 | `PARAM_INVALID` | 填写的内容不符合要求（下单场景有专属文案，见下） |
| 60001 | `ORDER_NOT_FOUND` | 订单不存在 |
| 60002 | `ORDER_STATUS_INVALID` | 订单状态已变化，请刷新后重试 |
| 60003 | `ORDER_NOT_CANCELABLE` | 订单已发货或已完成，不能取消 |
| 60004 | `ORDER_STOCK_SHORTAGE` | 卖家库存不足，暂时无法完成，请联系卖家补货后重试 |
| 70001 | `CART_GOODS_NOT_SELECTABLE` | 商品已下架，无法加入购物车 |
| 70002 | `FAVORITE_GOODS_NOT_SELECTABLE` | 商品已下架，无法收藏 |
| 70003 | `CART_TO_FAVORITE_REJECTED` | 有商品已下架，未转收藏 |
| 80001 | `ADDRESS_NOT_FOUND` | 地址不存在，请刷新后重试 |
| 80002 | `ADDRESS_LIMIT_EXCEEDED` | 最多保存 10 个地址，请先删除不用的 |
| 80003 | `ADDRESS_FIELD_INVALID` | 地址信息不符合要求：收货人 ≤50 字、手机号 7~20 位数字、详细地址 ≤200 字，均不能换行 |
| 80004 | `ADDRESS_REQUIRED` | 请先选择一个收货地址 |
| 81001 | `AFTERSALE_NOT_FOUND` | 售后单不存在 |
| 81002 | `AFTERSALE_STATUS_INVALID` | 售后单状态已变化，请刷新后查看 |
| 81003 | `AFTERSALE_NOT_APPLICABLE` | 该商品当前不可申请售后（仅交易完成的订单可申请） |
| 81004 | `AFTERSALE_REFUND_DUPLICATED` | 该商品已退过款，不能重复退款 |
| 81005 | `AFTERSALE_RESULT_REQUIRED` | 缺少处理结果（**预防性**：只在卖家完成时抛） |
| 81006 | `AFTERSALE_CONTENT_INVALID` | 标题或描述不符合要求：标题 ≤100 字且不能换行，描述 ≤1000 字 |
| 81007 | `AFTERSALE_IMAGE_LIMIT_EXCEEDED` | 最多上传 9 张图片 |
| 82002 | `LOGISTICS_NOT_EDITABLE` | 物流信息当前不可修改（**预防性**：只在卖家改物流时抛） |
| 83001 | `PAYMENT_CHANNEL_INVALID` | 请选择一个支付渠道 |
| 83002 | `PAYMENT_NOT_ALLOWED` | 订单当前不可支付，可能已超时取消，请刷新查看 |
| 83003 | `PAYMENT_FAILED` | 支付未成功，订单仍未支付，可换渠道重试 |
| 83004 | `REFUND_FAILED` | 退款失败，请联系卖家处理 |

**未声明的 18 个码**都属卖家专属或通用 HTTP 兜底，买家链路不可达：
`10003`（改密）、`20001–20006`（类别写）、`20012–20014`（商品字段）、`20017`/`20018`（图集）、
`20023`（商品状态）、`20025`/`20026`（视频与描述媒体）、`50003–50005`（资源/方法/媒体类型）。

**已停用码不得复用**：`10008`（原默认交易地点）、`20020`（原有未完成订单禁止下架），以及整段 `3xxxx`/`4xxxx`。
前端已删除 `10008` 的声明与文案，单测断言它落到通用兜底。

两处有意偏离：

- **50002 在下单场景有专属文案**「下单信息有误，请回购物车重新勾选后再提交」（`describeSubmitError`）。
  全局文案对下单场景没有意义，那里没有表单可填。
- **404 兜底不硬说「商品」**：业务码优先匹配，未登记码统一回「内容不存在或已被移除」。

## 目录结构

```
src/
├── api/
│   ├── types.ts        接口契约类型（snake_case）与业务码常量
│   ├── token.ts        令牌与到期时间的本地持久化
│   ├── http.ts         axios 封装：get/post/put/del/upload、ApiError、401 统一跳登录
│   ├── buyer.ts        商品 / 类别 / 账号
│   ├── cart.ts         购物车
│   ├── favorite.ts     收藏
│   ├── order.ts        订单（下单、列表、详情、明细、支付、确认收货、取消）
│   ├── address.ts      地址簿（列表、新建、编辑、删除、设默认）
│   ├── aftersale.ts    售后（申请、列表、详情）
│   └── media.ts        售后凭证图上传
├── stores/
│   ├── auth.ts         登录态
│   ├── cart.ts         服务端行 + 本地数量勾选 → 展示行；下单、转收藏
│   └── address.ts      地址簿（写动作后重拉，默认归属不在本地推算）
├── components/
│   ├── common/         AppIcon / StatusTag / Pagination / EmptyState / FormModal
│   ├── goods/          GoodsCard / GoodsGallery / DescriptionBlocks
│   ├── cart/           CartRow / CartSummaryBar
│   ├── favorite/       FavoriteCard
│   ├── order/          OrderRow / OrderItemsTable
│   └── address/        AddressForm
├── composables/
│   ├── usePagedList.ts 分页列表公共状态
│   ├── useCountdown.ts 支付倒计时（只认 pay_deadline）
│   └── useTheme.ts     双主题切换与持久化
├── validation/
│   ├── register.ts     注册字段校验
│   ├── address.ts      地址字段校验
│   └── aftersale.ts    售后标题/描述/图片校验
├── utils/
│   ├── cartStorage.ts  购物车本地状态读写
│   ├── format.ts       时间、金额、倒计时格式化
│   ├── orderStatus.ts  订单七态文案/tone/筛选、买家动作矩阵、支付渠道
│   ├── aftersaleStatus.ts 售后三态与结果文案
│   ├── errorText.ts    业务码 → 中文文案
│   └── redirect.ts     登录回跳地址消毒
├── router/index.ts     路由与登录守卫
├── views/              GoodsList / GoodsDetail / Cart / Checkout / Favorites / Orders /
│                       OrderDetail / OrderPay / Addresses / Aftersales / AftersaleApply /
│                       AftersaleDetail / Login / Register
├── App.vue             顶栏（商品/购物车/收藏/订单/地址/售后/主题/登录退出）
├── assets/base.css     Tailwind + daisyUI 唯一 CSS 入口
└── main.ts             入口；注册 401 统一跳登录
test/                   Vitest 单元测试（utils / validation / composables / stores）
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
- **401 统一跳登录**：`http.ts` 的 `toApiError()` 把 401 转成 `SESSION_INVALID` 后走 `fail()`，
  由 `main.ts` 注册的回调清令牌并跳登录。⚠️ 新增请求分支（含 `put`/`upload`）不要绕开 `fail()`。
- **字段名保持 snake_case**，不做驼峰转换。
- **业务规则尽量不在前端复制**：可否购买看 `purchasable`，购物车行可用看 `selectable && stock > 0`。
  **唯一例外是订单按钮矩阵**（接口没有 `can_*` 标记），集中在 `utils/orderStatus.ts`。
- **二次确认用内联**（「确认 X / 再想想」），不用 modal；提示用内联 notice/alert，不用全局 toast。
- **表单用弹窗**：外壳统一 `components/common/FormModal.vue`，点遮罩或取消关闭；
  `modal-box` 底色取 `--color-surface-solid`（浅色 `base-100` 是半透明，浮层必须实色）。
- **表单用包裹式 `<label>`** 保证 `getByLabel` 可访问名。
- **颜色只用语义色**，图标走 `AppIcon` 内联 path，不用 `<img src="*.svg">`。
- **时间以 UTC 存、按浏览器本地时区展示**，统一走 `utils/format.ts`。

## 已知限制与已知差异

- **订单按钮矩阵由前端按状态派生**：`OrderData` 没有 `can_cancel` 这类标记，只能在 `utils/orderStatus.ts`
  维护一份与《契约交接》§4 对齐的矩阵。后端若调整边界，这里要同步改。建议后端后续补布尔字段。
- **`GoodsSummary` / `GoodsDetail` 未声明 `off_shelf_reason` / `off_shelf_at`**：买家看不到已下架商品
  （详情直接空态），这两个字段用不到，属**有意省略**（阶段三 D5-3 已登记，阶段四沿用）。
- **订单状态筛选只支持单状态**：后端 `status` 走 `OrderStatus.valueOf()`，组合值回 50002，所以做成下拉单选。
- **购物车合计前端算**：接口只给单价与库存，合计按「分」累加再折回元；下单总额仍以后端为准。
- **`selectable` 只表示在售、不含库存**，所以「能否下单」前端还要补一个 `stock > 0`。
- **下单不会自动清购物车**：后端订单服务不引用购物车表，下单成功后由前端删行（`stores/cart.ts`）。
- **售后「已取消」不区分买家撤销与卖家拒绝**：后端没有独立拒绝态，两者是同一个动作。
- **倒计时归零与后端扫描不同步**：归零只提示 + 刷新，不本地改状态。
- **`/api/public/health` 不调用**：运维探活端点，买家端没有使用场景。
- **移动端适配暂缓**：当前只做基础响应式，演示与验收在桌面浏览器进行。
- **商品/收藏列表 12 条/页，订单与售后列表 10 条/页**；后端 `page_size` 上限 100。

## 买家端不做的事

改资料、改密码、找回密码、购物车多规格、客服；
真实支付网关与支付宝沙箱、物流轨迹与状态枚举、地址省市区结构化；
售后按数量部分退款、买家撤销售后、申请时选售后类型；
超时自动确认收货、运费/优惠/发票/支付流水明细页；
卖家端的任何功能。