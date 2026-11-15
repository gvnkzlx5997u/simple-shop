# Simple-Shop 阶段F · 买家端工程规范化计划

> **文档性质**：买家端 UI 技术栈同步、项目结构拆分与测试体系重构的实施计划（v0.1），说明选型、拆分映射、遗留测试逐条处置、阶段顺序与验收口径;**不含**业务口径变更与接口契约变更。
> **适用范围**：`simple-shop-buyer-frontend`、`simple-shop-test-e2e`（买家相关部分）、两端共享的规范文档。
> **上游依据**：`AGENTS.md` §2/§5/§7、《阶段U-卖家端UI改造计划》（参照实现）、《阶段D-买家端开发计划》（功能口径）、《阶段E-E2E用例清单》（测试口径）。
> **实施位置**：阶段三收尾（docker 构建 + 推送镜像）**之前**。镜像一旦推送再改前端就要重新构建推送，本阶段完成后再走收尾。
> **版本**：v0.1　**日期**：2026-11-14

---

## 摘要（一页结论）

| 问题 | 结论 |
| --- | --- |
| 为什么改 | 买家端与卖家端工程形态已分叉：卖家端有 Tailwind/daisyUI、组件分层、10 个单测文件;买家端仍是原生 CSS、8 个胖视图、零单测，E2E 工程里还留着两个直连数据库的裸脚本 |
| UI 技术栈 | **同步卖家端**：Tailwind CSS 4 + daisyUI 5，同一套 `shop-light`/`shop-dark` 主题 token;买家端用**默认尺寸**（ storefront 非后台），不照搬 `-sm` 密度 |
| 结构 | 按卖家端形态拆出 `components/{common,goods,cart,favorite,order}`、`validation/`，外提纯逻辑（注册校验、redirect 消毒、错误文案）;视图只做编排 |
| 遗留测试 | `d3-cart-check.js`（12 项）与 `d5-full-regression.js`（44 项）**整体删除**：已被主套件覆盖的不再重复，缺口补进 E2E（新增 14 条），纯逻辑下沉单元测试（9 个 spec 文件），数据库 schema 断言归还后端测试 |
| 硬规则 | **前端测试（单元 + E2E）一律不直连数据库**：造数走接口、断言走接口返回与页面;`scripts/db.js` 只用于重建 E2E 库与手工联调夹具 |
| 顺序 | F0 基座 → F1 公共件 → F2 组件拆分 → F3 单元测试 → F4 E2E 收编 → F5 文档与门禁 |
| 最大风险 | 买家端 `.card`/`.badge`/`.select` 与 daisyUI 同名、`base.css` 裸元素选择器未分层，会静默压过组件样式;E2E 买家选择器大面积换钩子 |
| 不做 | 路由、接口、业务规则、后端、卖家端代码、deploy 编排;不引入 Vue 组件库、不做移动端适配 |

**一句话判断**：这不是重写，是「把卖家端已验证的工程形态复制到买家端」——样式底座、组件边界、测试分层都有现成参照，难点只在遗留测试的逐条去留判定与 E2E 钩子迁移。

---

## 1. 现状盘点

### 1.1 买家端工程（`simple-shop-buyer-frontend`）

| 项 | 现状 | 问题 |
| --- | --- | --- |
| 视图 | 8 个共 2257 行：Cart 475 / OrderDetail 365 / GoodsDetail 361 / Favorites 290 / Orders 284 / GoodsList 229 / Register 153 / Login 100 | 模板、样式、业务逻辑混在单文件;Cart 一个视图含 230 行 scoped CSS |
| 组件 | **无 `components/` 目录** | 商品卡、收藏卡、订单行、分页条、空态、状态徽章全部在视图里手写，分页条三处各写一遍、状态徽章 CSS 两处重复 |
| 样式 | `base.css` 104 行原生 CSS，8 个自定义变量（`--color-text/text-weak/border/primary/danger/bg/surface/fill`，全站引用 74 处）;视图与全局另有 43 处硬编码色值 | 无主题机制;`.card`/`.badge`/`.select` 与 daisyUI 同名;`button`/`input`/`a` 裸元素选择器未分层 |
| 测试 | **零单元测试**，无 vitest;`package.json` 只有 dev/build/preview | 数量夹取、分累加合计、孤儿清理、空页回退、注册校验、redirect 消毒等纯逻辑全靠 E2E 与手工回归 |
| 纯逻辑散落 | `describeError` 在 `stores/auth.ts`;注册校验在 `Register.vue` 内联;redirect 消毒在 `Login.vue` 内联;下单失败专属文案在 `Cart.vue` 内联 | 不可独立测试，与卖家端 `utils/error.ts`、`validation/` 形态不一致 |
| 已有良好基础 | `usePagedList` 已抽公共（三页复用）;`cartStorage` 已独立纯读写;`api/` 分层清晰;配色已与卖家 shop-light 同源 | 这些**保留不动**，是拆分的起点而非障碍 |

### 1.2 卖家端参照（`simple-shop-seller-frontend`）

- 样式：`base.css` 唯一入口（`@import "tailwindcss"` + `@plugin "daisyui"` + 两套主题 + `.panel`/`.muted` 复合类 + 末尾 `@utility` 覆盖）;`useTheme` 模块级单例;`index.html` 内联防闪脚本。
- 结构：`components/{common,category,goods,order}`、`composables/`、`constants/`、`layouts/`、`validation/`、`utils/`。
- 测试：`vitest`（node 环境、threads），`test/` 下 10 个 spec（validation ×5、composables ×4、components ×1）;`test:unit` 无需后端。
- 公共件契约：`StatusTag`（label+tone → badge）、`Pagination`（join）、`ConfirmDialog`（modal）、`ToastHost`、`AppIcon`（内联 svg + currentColor）。

### 1.3 E2E 工程与遗留脚本

- 主套件：9 spec / 76 条，`workers:1` + 系统 Edge;**造数走接口、断言走接口返回与页面**，`tests/` 不连库。
- `d3-cart-check.js`（12 项）：页面操作 + 直连夹具库 `simple_shop_d1` 对账（账号入库、购物车行数、订单总额、明细之和、`information_schema` 查列）。
- `d5-full-regression.js`（44 项）：五组全量回归，**用 SQL 改夹具**（UPDATE 商品状态/库存、INSERT 10 笔订单、DELETE 清账号），断言混用页面文本与 SQL 查询。
- 配套：`verify-cart.bat`、`npm run verify:cart / verify:regression`、`simple-shop-d1-fixtures.sql`、`.env` 的 `E2E_FIXTURE_DB_NAME`。
- 文档口径冲突：
  - `AGENTS.md` §2/§5 明确「买家端不同步样式栈」「不要把 Tailwind 带进买家端」——**本次决策反转**，须同步修订;
  - 《阶段E-E2E用例清单》§7 判定两脚本「不并入、保留原样」，§6 还留着「将来某条用例确实要查库可 probe+skip」的例外——**本次改为收编后删除、例外堵死**;
  - 《阶段D-买家端开发计划》§1.3「不新增测试框架」、§9.2「不引入 UI 组件库」——被本计划取代。

### 1.4 覆盖缺口（d3/d5 有、主套件没有）

经逐条比对（详见 §5），主套件未覆盖的买家行为：搜索空态、退出登录、未登录点加购/收藏跳登录带 redirect、空购物车引导、勾选/全选对合计的影响、刷新后数量勾选保持、下单失败后购物车自动刷新、库存不足下单被拒（20015 买家侧）、订单分页与翻页、切筛选回第 1 页、订单头/明细独立加载与重试、打开详情后被发货再点取消、收藏状态刷新回显。共约 14 条，全部补进 E2E。

---

## 2. 目标与非目标

### 2.1 目标

1. 买家端样式底座与卖家端同构：Tailwind 4 + daisyUI 5、同一套双主题 token、语义色、无硬编码色、无未分层 CSS。
2. 视图瘦身：可复用 UI 拆成组件，纯逻辑外提为可单测模块;视图只保留编排与页面级状态。
3. 测试分层归位：纯逻辑 → 单元测试（工程内、无后端）;集成与两端连调 → E2E（接口造数、接口/页面断言）;数据库 schema 与金额不变量 → 后端测试。
4. 删除 d3/d5 及其配套，E2E 主套件补齐缺口并迁移买家选择器。
5. 规范文档同步：AGENTS.md、两端 README、E2E 交接与清单、阶段D 标注、目录索引。

### 2.2 非目标

- 不改路由、接口、DTO、业务规则（数量夹取、失效行锁定、空页回退、下单清车、转收藏事务口径等**行为一字不动**）。
- 不改后端、卖家端代码、deploy 编排;`deploy/frontend/Dockerfile` 的 buyer-build 阶段（`npm ci` + `npm run build`）保持可用，无需修改。
- 不引入 Vue 组件库（daisyUI 只给类名，理由同阶段U §2.2）。
- 不做移动端专门适配（沿用现有基础响应式，组长已确认暂缓）。
- 不新增买家端 `test:api` 连通性测试（接口连通已由 E2E 覆盖，避免重复）。
- 不为单测而过度拆分：只出现一次的 3~8 行小逻辑（如 `stockText`、`allImages` 去重）留在原处。

---

## 3. UI 技术栈与主题

### 3.1 接入方式（与卖家端一致）

| 项 | 决定 |
| --- | --- |
| 依赖 | `tailwindcss` 4.x、`@tailwindcss/vite` 4.x、`daisyui` 5.x 进 devDependencies;版本与卖家端对齐（4.3.5 / 5.7.50 起） |
| Vite | `vite.config.ts` 加 `tailwindcss()` 插件，不加 PostCSS 配置 |
| 入口 | 仍是 `src/assets/base.css`，重写为 `@import "tailwindcss"` + `@plugin "daisyui"` + 两套主题 + `.panel`/`.muted` + 末尾 `@utility` |
| 配置 | 不建 `tailwind.config.js`（v4 已废弃） |
| 主题 | **直接复用卖家端 `shop-light`/`shop-dark` 的全部 token 值**（买家 base.css 注释已写明「与卖家端同一套取色，两端并排看是同一个品牌」）;`--color-surface-solid` 叠加层实色变量一并复制 |
| 切换 | 新增 `composables/useTheme.ts`（复制卖家实现，存储键改 `simple-shop.buyer.theme`）;`index.html` 内联防闪脚本、`lang="zh-CN"`、标题改「在线购物」 |
| 图标 | 新增 `components/common/AppIcon.vue`（内联 svg + currentColor），**按需登记**（sun/moon 起步，用到再加），不整份复制卖家 21 个;核查 `public/icons.svg` 引用，无引用则删除 |

### 3.2 买家端与卖家端的刻意差异

| 维度 | 卖家端 | 买家端 | 理由 |
| --- | --- | --- | --- |
| 控件尺寸 | 全站 `-sm`（后台密度） | **默认尺寸** | storefront 面向普通用户，触控目标与信息密度要求不同;订单明细表可用 `table-sm` 一档 |
| 全局字号 `@utility` | btn/input/select/textarea 提到 13px | **不做**，用 daisyUI 默认 | 卖家那条是补偿 `-sm` 的 12px，买家不用 `-sm` 就不需要 |
| 确认弹窗 | `ConfirmDialog`（modal） | **保留内联二次确认**（确认取消/再想想） | 买家只有取消订单一处确认，内联更轻;E2E 已按内联断言 |
| 消息提示 | `ToastHost` + `useToast` | **保留内联 notice/error**，换 `alert alert-soft` 外观 | 买家反馈都发生在操作原地，不需要全局 toast;少一套状态与组件 |
| 布局 | 侧栏 `AppLayout` | 顶栏 `App.vue`（navbar），内容容器 `max-w-[960px]` 居中 | 买家是浏览型站点，沿用现有顶栏信息架构 |
| 主题存储键 | `simple-shop.seller.theme` | `simple-shop.buyer.theme` | 两端独立持久化，互不干扰 |

以上差异写进 AGENTS.md §5，避免后续协作者把卖家约定机械套到买家端。

---

## 4. 目标结构与组件拆分

### 4.1 目标目录

```
src/
├── api/                    不动（types/token/http/buyer/cart/favorite/order）
├── assets/base.css         重写：tailwind + daisyui + 双主题 + .panel/.muted + @utility
├── components/
│   ├── common/
│   │   ├── AppIcon.vue         内联 svg + currentColor（按需登记）
│   │   ├── EmptyState.vue      标题/说明/动作插槽，替换 5 处手写空态
│   │   ├── Pagination.vue      join 按钮组，page/total/pageSize props + update:page
│   │   └── StatusTag.vue       label+tone → badge，契约与卖家一致
│   ├── goods/
│   │   ├── GoodsCard.vue       列表宫格卡（card + figure + card-body）
│   │   ├── GoodsGallery.vue    详情主图 + 缩略图切换
│   │   └── DescriptionBlocks.vue  TEXT/IMAGE/VIDEO 描述块渲染
│   ├── cart/
│   │   ├── CartRow.vue         勾选/缩略图/信息/数量步进/小计/移除
│   │   └── CartSummaryBar.vue  全选/合计/转收藏/去结算（sticky 底栏）
│   ├── favorite/
│   │   └── FavoriteCard.vue    收藏卡（含失效标注与点击提示）
│   └── order/
│       ├── OrderRow.vue        订单列表行
│       └── OrderItemsTable.vue 订单明细表
├── composables/
│   ├── usePagedList.ts     不动（已抽好，行为有差异选项，单测锁定）
│   └── useTheme.ts         新增（买家存储键）
├── stores/                 auth/cart 保留;describeError 外提（见 4.3）
├── utils/
│   ├── cartStorage.ts      不动
│   ├── format.ts           不动
│   ├── orderStatus.ts      保留，新增 ORDER_STATUS_TONES（六态 → badge tone）
│   ├── errorText.ts        新增：describeError 从 stores/auth.ts 迁入 + describeSubmitError
│   └── redirect.ts         新增：Login 的站内 redirect 消毒逻辑外提
├── validation/
│   └── register.ts         新增：Register.vue 的 validate() 外提（规则与文案一字不动）
├── views/                  8 个视图瘦身：编排 + 页面级状态 + 布局
├── App.vue                 顶栏 navbar + 主题切换 + RouterView
├── router/index.ts         不动
└── main.ts                 不动
test/                       新增（见 §5.4）
vitest.config.ts            新增（node 环境、threads，参考卖家;买家无 @ 别名则不配 alias）
```

### 4.2 拆分映射（旧 → 新）

| 现状位置 | 拆出 | 保留在视图 |
| --- | --- | --- |
| GoodsList 宫格卡（约 30 行模板 + 40 行 CSS） | `goods/GoodsCard.vue` | 筛选状态、类别联动、usePagedList 编排 |
| GoodsDetail 图集（主图 + 缩略图） | `goods/GoodsGallery.vue` | 加载/空态/加购/收藏动作与提示 |
| GoodsDetail 描述块渲染 | `goods/DescriptionBlocks.vue` | 同上 |
| Cart 行（勾选/图/信息/步进/小计/移除，约 60 行模板 + 120 行 CSS） | `cart/CartRow.vue` | load/submit/move/remove 编排与失败刷新 |
| Cart 底栏（全选/合计/转收藏/结算） | `cart/CartSummaryBar.vue` | 同上 |
| Favorites 卡片 | `favorite/FavoriteCard.vue` | 分页、取消收藏、失效提示 |
| Orders 列表行 | `order/OrderRow.vue` | 筛选、分页、空态 |
| OrderDetail 明细表 | `order/OrderItemsTable.vue` | 头/明细两次加载、独立重试、内联取消（**不拆**，约 20 行） |
| Orders + OrderDetail 各一套 `.badge .st-*`（6 态硬编码色 ×2） | `common/StatusTag.vue` + `orderStatus.ts` 的 TONES | — |
| GoodsList/Orders/Favorites 三套手写分页条 | `common/Pagination.vue` | 翻页副作用（滚顶、清提示） |
| Cart/GoodsDetail/Orders/OrderDetail/Favorites 五套 `.empty` 块 | `common/EmptyState.vue` | 空态文案与动作 |
| App.vue 顶栏 | 不拆（94 行，本身就是布局），换 navbar 类 + 主题按钮 | — |

**拆分纪律**（同卖家 U1）：组件只收模板与样式，**props/emit 契约稳定，请求与状态逻辑留在视图/store**;CartRow 的数量 `change` 夹取与回写、失效行禁用等行为原样搬移，由单测与 E2E 锁定。

### 4.3 纯逻辑外提

| 模块 | 来源 | 内容 | 动机 |
| --- | --- | --- | --- |
| `utils/errorText.ts` | `stores/auth.ts` 的 `describeError` + `Cart.vue` 的 `submitFailText` | 业务码 → 中文文案;下单 50002 专属文案改名 `describeSubmitError` 一并迁入 | 纯函数不该住在 store 文件里;与卖家 `utils/error.ts` 对齐;可独立单测 |
| `validation/register.ts` | `Register.vue` 内联 `validate()` | 用户名/密码/电话/地点规则（含「电话至少 7 位数字」这条比后端严的有意偏离） | 与卖家 `validation/` 对齐;边界值单测 |
| `utils/redirect.ts` | `Login.vue` 内联消毒 | 只接受站内路径，拒绝 `//evil.com` 协议相对地址 | 安全相关纯函数，值得单测锁定 |
| `utils/orderStatus.ts` 增补 | Orders/OrderDetail 的 `.st-*` 配色 | `ORDER_STATUS_TONES`：PENDING_CONFIRM→warning、CONFIRMED/PREPARED/SHIPPED→info、COMPLETED→success、CANCELED→default | 状态色收敛一处，StatusTag 消费 |

`describeError` 迁移后更新全部 import（Cart/GoodsDetail/Favorites/Orders/OrderDetail/Login/Register/usePagedList/cart store 约 9 处，机械改动）;**不留 re-export 兼容层**（AGENTS.md 协作规则）。

---

## 5. 测试重构

### 5.1 分层原则与数据库禁令

| 层 | 工程 | 环境 | 造数 | 断言对象 | 数据库 |
| --- | --- | --- | --- | --- | --- |
| 单元测试 | 买家前端 `test/` | Vitest node 环境，无后端无浏览器 | vi.mock api 模块 + localStorage 内存桩 | 纯函数返回值、store 状态、composable 行为 | **禁止** |
| E2E | `simple-shop-test-e2e/tests/` | Playwright + Edge + 三服务 + E2E 库 | 接口（helpers/api.ts） | 页面文本/状态 + 接口返回 | **禁止** |
| 后端测试 | `simple-shop-backend` | JUnit + Testcontainers | SQL/JPA | 业务状态、返回码、**库内数据** | 允许（仅此层） |

**硬规则（写进 AGENTS.md §7 与 E2E README）**：

- 前端单元测试与 E2E 用例**一律不得直连数据库**——不查表对账、不查 `information_schema`、不用 SQL 造数/改夹具/清理。
- 要验证「后端真的收到了」，断言**接口返回**（接口即契约，比表结构稳定）;要验证 schema 事实（如购物车表无数量/勾选列），由**后端迁移测试**守，前端只断言其行为等价物（换上下文数量勾选回默认）。
- `scripts/db.js` 保留，但用途收窄为：重建 E2E 库（`db:reset`/`db:probe`）与手工联调夹具;**不再被任何测试脚本引用**。
- 《阶段E-E2E用例清单》§6 那条「万一将来某条用例确实要查库，先 probe 连不上就 skip」的例外**删除**，不留口子。
- 理由：SQL 断言让用例跟着表结构漂移，改一次迁移就要改一遍用例；夹具库与 E2E 主库两套数据并存（`simple_shop_d1` vs `simple_shop_e2e`）已经造成混库风险;d5 用 SQL INSERT 订单绕过了下单校验，测的不是真实链路。

### 5.2 d3-cart-check.js 逐条判定（12 项 → 全部收编，脚本删除）

| # | 原断言 | 处置 | 落点 |
| --- | --- | --- | --- |
| 1 | 注册后数据库有该账号 | 删 DB 断言 | 入库由后端账号测试守;E2E T01-04 已断言注册即登录 |
| 2 | 数据库购物车 2 行 | 换接口断言 | T02b-02/07 已用 `listCart` 断言行数 |
| 3 | 页面渲染 2 行 | 保留 | T02b-04/07 UI 行数断言 |
| 4 | 页面合计 214.50 | 下沉单测 + E2E | 单测 `totalCents` 分累加;T02b-03 合计断言 |
| 5 | 改数量后 31.00 | 下沉单测 + E2E | 单测 `clampQuantity`/`totalCents`;T02b-03 |
| 6 | 购物车表无数量/勾选列（查 information_schema） | **归还后端** | schema 事实由后端 V3 迁移测试 + JPA validate 守;前端行为等价物是 T02b-05（换上下文回默认），已有 |
| 7 | 勾回后 230.00 | 下沉单测 + E2E | 单测 `toggleChecked`/`totalCents`;T02b-03/07 |
| 8 | 订单新增 1 笔 | 换接口断言 | T02b-07 `listBuyerOrders` |
| 9 | 订单总额 230.00 | 换接口断言 | T02b-07 `total_amount` 断言（接口值） |
| 10 | 明细之和 230.00 | 归还后端 + E2E | 后端订单测试守金额不变量;T04-04（卖家详情合计=明细之和）、T02b-07 明细行数与金额已覆盖 |
| 11 | 页面金额 == 后端金额 | 保留 | T02b-07 同用例双断言（`.amount` 文本 + `total_amount` 数值） |
| 12 | 下单后购物车清空（DB 0 行 + 页面空态） | 换接口断言 + 补 UI | T02b-07 已断言 `listCart` 空;**补**回购物车页见「购物车是空的」空态（并入 T02b-12） |

### 5.3 d5-full-regression.js 逐条判定（44 项）

> 处置代号：**已有**=主套件已覆盖（迁移选择器即可）;**新增**=补 E2E 新用例;**单测**=下沉单元测试;**删除**=属后端职责或 SQL 夹具手段本身被禁止。

**A 组 · 未登录（10）**

| 编号 | 场景 | 处置 | 落点 |
| --- | --- | --- | --- |
| A1 | 列表可浏览 | 已有 | T02-01 |
| A2 | 详情可浏览 | 已有 | T02-04 |
| A3 | 搜索有结果 | 已有 | T02-01/02 |
| A4 | 搜索无结果空态 | **新增** | T02-05 |
| A5 | 注册（DB 查账号） | 已有（删 DB 断言） | T01-04;入库归后端测试 |
| A6 | 退出后重新登录 | **新增** | T01-09（退出 → 顶栏与入口变化 → 重登） |
| A7–A9 | 三个需登录路由被拦 | 已有 | T02b-01 |
| A10 | 未登录点加购跳登录带 redirect | **新增** | T02b-11（收藏按钮同断言） |

**B 组 · 购物车（12）**

| 编号 | 场景 | 处置 | 落点 |
| --- | --- | --- | --- |
| B1 | 空购物车引导 | **新增** | T02b-12（含 d3#12 下单后空态） |
| B2 | 加购单件 | 已有 | T02b-02 |
| B3 | 重复加购提示（DB 仍 1 行） | 已有（DB 换接口） | T02b-02 |
| B4 | 加购第二件 | 已有 | T02b-04/07 |
| B5 | 改数量 | 已有 + 单测 | T02b-03;cart store 单测 |
| B6 | 超库存夹住 | 已有 + 单测 | T02b-03;`clampQuantity` 单测 |
| B7 | 取消勾选影响合计 | **新增** + 单测 | T02b-13;`totalCents` 单测 |
| B8 | 全选勾上可买的 | **新增** + 单测 | T02b-13;`toggleAll`/`allChecked` 单测 |
| B9 | 刷新后数量勾选保持 | **新增** | T02b-14（localStorage 持久化，reload 断言） |
| B10 | 失效行灰掉锁住提示（SQL 下架） | 已有（SQL 换接口） | T02b-06 用 `offShelf` 接口造失效 |
| B11 | 失效行不计入合计 | 已有 + 单测 | T02b-06 补合计断言;`totalCents` 排除失效行单测 |
| B12 | 删除单行（DB 行数） | 已有（DB 换接口） | T02b-04 |

**C 组 · 下单（5）**

| 编号 | 场景 | 处置 | 落点 |
| --- | --- | --- | --- |
| C1 | 单件下单跳详情 | **新增** | T02c-01（T02b-07 是两件变体） |
| C2 | 多件下单金额正确 | 已有 | T02b-07 |
| C3 | 含失效行下单被拦（SQL 下架） | 已有（SQL 换接口） | T02b-06（UI 不可勾）+ T06-02（接口 20019）;stale 场景并入 T02b-15 |
| C4 | 下单失败后购物车自动刷新 | **新增** | T02b-15（加载购物车后卖家用接口下架 → 结算被拦 → 失效行当场显形） |
| C5 | 库存不足被拒提示（SQL 改库存） | **新增**（SQL 换接口） | T02b-16（加载后卖家 `updateStock` 改小 → 结算 20015 文案 → 勾选数量保持） |

**D 组 · 订单（10）**

| 编号 | 场景 | 处置 | 落点 |
| --- | --- | --- | --- |
| D1 | 列表可见已下的单（DB 笔数） | 已有（DB 换接口） | T08-08 |
| D2 | 分页（SQL INSERT 10 笔） | **新增**（SQL 换接口） | T02c-02（`placeOrder` 接口循环造 11+ 笔） |
| D3 | 翻第 2 页 | **新增** | T02c-02 |
| D4 | 按下单时间倒序 | 已有 | T08-08 |
| D5 | 状态筛选 | 已有 | T08-08 |
| D6 | 切筛选回第 1 页 | **新增** | T02c-03（含空态「查看全部」清筛选） |
| D7 | 明细二次请求 | **新增** | T02c-04（请求计数 + route 拦截明细失败 → 头可读 → 重试成功） |
| D8 | 取消二次确认（DB CANCELED） | 已有（DB 换接口） | T08-04 |
| D9 | 已发货无取消入口 | 已有 | T08-05 |
| D10 | 打开后被发货 → 取消提示（SQL 改状态） | **新增**（SQL 换接口） | T08-09（买家开着详情，卖家 `shipOrder` → 点取消 → 60003 文案） |

**E 组 · 收藏（7）**

| 编号 | 场景 | 处置 | 落点 |
| --- | --- | --- | --- |
| E1 | 详情页收藏 | 已有 | T02b-08 |
| E2 | 刷新后收藏状态回显 | **新增** | T02b-17 |
| E3 | 收藏列表显示 | 已有 | T02b-08 |
| E4 | 失效保留标注（SQL 下架） | 已有（SQL 换接口） | T02b-09 |
| E5 | 取消收藏（DB 0 条） | 已有（DB 换接口） | T02b-08 |
| E6 | 购物车转收藏（DB 行数） | 已有（DB 换接口） | T02b-10 |
| E7 | 转收藏后收藏页可见 | 已有 | T02b-10 |

**SQL 夹具手段的整体替换**：`resetFixtures`/`cleanupUser`/INSERT 订单全部删除。E2E 主套件用 `RUN_ID` 唯一造数 + 跑前重建库，不需要清理;商品状态/库存 manipulation 一律走卖家接口（`offShelf`/`updateStock`），订单造数走 `placeOrder`。

### 5.4 单元测试清单（买家工程 `test/`，Vitest node 环境）

配置：`vitest.config.ts` 参考卖家（environment node、pool threads、include `test/**/*.spec.ts`）;`package.json` 加 `test:unit`/`test:watch`;`test/` 纳入 tsconfig 类型检查。**不引入 happy-dom**：需要 localStorage 的测试用内存桩（`vi.stubGlobal`），与卖家 node 环境一致;`useTheme` 依赖 DOM，不做单测（卖家同样未测），由手工双主题回归 + E2E 冒烟覆盖。

| 文件 | 覆盖点 |
| --- | --- |
| `test/utils/format.spec.ts` | formatTime：UTC→本地、null/undefined/非法串→空串;formatMoney：number/string/null/NaN→0.00、两位小数、舍入 |
| `test/utils/orderStatus.spec.ts` | 六态文案、未知值回退原值;canCancelByBuyer 前三态 true 后三态 false;isTerminal;FILTERS 只含单值且顺序稳定;TONES 六态齐全 |
| `test/utils/cartStorage.spec.ts` | 读写往返、按用户名隔离、坏 JSON/非对象→{}、quantity<1/NaN→1 且向下取整、checked 缺省 true、空状态删键、clear 只清本人键、空用户名 no-op |
| `test/utils/errorText.spec.ts` | 18 个可达码 + 4 个预防码逐条文案;404 兜底「内容不存在」不硬说商品;未登记码兜底;非 ApiError→网络异常;describeSubmitError 的 50002 专属文案 |
| `test/utils/redirect.spec.ts` | `/cart` 放行;`//evil.com`、`http://…`、空串、非字符串一律拒绝回落 |
| `test/validation/register.spec.ts` | 用户名空/50/51/空格/尖括号;密码<8;电话 7~20 位数字短横、全短横拒绝（比后端严的有意偏离）、数字<7 拒绝;地点可选/50/51/换行/尖括号;trim 行为;每条错误文案逐字锁定 |
| `test/composables/usePagedList.spec.ts` | load 成功/失败;emptyPageFallback 空页回退且最多重试一次;resetTotalOnError 两种口径;hasPager/canPrev/canNext;reloadFromFirstPage;initialPage 非法收敛;失败清 items。**锁定现有行为，不顺手加竞态守卫**（买家版无守卫是有意取舍） |
| `test/stores/cart.spec.ts` | syncLocal 三件事（孤儿丢弃/补默认{1,true}/失效强制取消勾选）;clampQuantity 边界（1..stock、stock0→1、999 上限、NaN→1、取整）;rows 派生 checkable/stockShort;totalCents 分累加（0.1+0.2 类浮点、排除失效与未勾选）;checkedRows/invalidRows/allChecked/setQuantity/toggleChecked 对失效行 no-op;toggleAll 失效行恒 false;add 的 added/existed 判定;remove;submitOrder 成功清行清本地、失败原样保留;moveCheckedToFavorites 成功/失败;reset;未登录 load 清空。api 模块 vi.mock，localStorage 内存桩，createPinia/setActivePinia |
| `test/stores/auth.spec.ts` | setSession 写令牌与用户名;clearSession 只清当前用户购物车本地键（换账号不串数据）;logout 吞接口错误仍清本地;registerAndLogin 注册后再登录 |

### 5.5 E2E 新增用例（14 条，套件 76 → 90，spec 9 → 10）

| 编号 | 用例 | 归属 spec |
| --- | --- | --- |
| T01-09 | 退出登录 → 顶栏变登录/注册、需登录入口消失 → 重新登录恢复 | 01-auth |
| T02-05 | 搜索无结果 → 空态文案 | 02-buyer |
| T02b-11 | 未登录点「加入购物车」/「收藏」→ 跳登录且 redirect 带原详情页 | 02b-buyer-cart |
| T02b-12 | 空购物车引导（去逛逛）;下单成功后回购物车见空态 | 02b-buyer-cart |
| T02b-13 | 取消勾选/勾回/全选 → 合计与已选件数联动，失效行不参与 | 02b-buyer-cart |
| T02b-14 | 改数量与勾选后 reload → 本地状态保持 | 02b-buyer-cart |
| T02b-15 | 加载购物车后卖家接口下架其一 → 结算被拦（20019 文案）→ 购物车自动刷新、失效行显形锁住 | 02b-buyer-cart |
| T02b-16 | 加载后卖家接口改小库存 → 结算 20015「库存不足」文案 → 勾选数量保持 | 02b-buyer-cart |
| T02b-17 | 详情页收藏后 reload → 「已收藏」回显 | 02b-buyer-cart |
| T02c-01 | 单件下单 → 跳详情、待确认、金额正确 | **02c-buyer-orders（新 spec）** |
| T02c-02 | 接口造 11+ 笔 → 第 1 页 10 笔/共 N 笔 → 翻第 2 页余量正确 | 02c-buyer-orders |
| T02c-03 | 第 2 页切筛选 → 回第 1 页;某状态空态 → 「查看全部」清筛选 | 02c-buyer-orders |
| T02c-04 | 详情头+明细两次独立请求;route 拦截明细失败 → 头可读可重试 → 放行后重试成功 | 02c-buyer-orders |
| T08-09 | 买家开着详情，卖家接口发货 → 点取消 → 「已发货，不能取消」 | 08-cross-end |

造数全部走 helpers/api.ts（`placeOrder` 循环、`offShelf`、`updateStock`、`shipOrder`）;T02c-04 的 route 拦截是浏览器层手段，不碰数据库。新用例沿用 `RUN_ID` 唯一命名与 `describe.serial` 依赖约定。

### 5.6 遗留脚本与配套处置

| 项 | 处置 |
| --- | --- |
| `d3-cart-check.js` / `d5-full-regression.js` | **删除**（覆盖已收编，见 §5.2/5.3） |
| `verify-cart.bat` | 删除 |
| `package.json` 的 `verify:cart` / `verify:regression` | 删除 |
| `scripts/db.js` | **保留**，用途收窄为重建库 + 手工夹具;文件头注释更新 |
| `simple-shop-d1-fixtures.sql` | **保留**，重新定位为「手工联调夹具」（交接说明 §7 的省事环境仍用它），不再是测试依赖 |
| `.env.example` 的 `E2E_FIXTURE_DB_NAME` | 保留但注释改为「仅手工联调夹具库，测试不再使用」 |
| `买家端交接说明.md` §2 | 重写：两脚本已收编进主套件，跑法改为 `npx playwright test` |

---

## 6. E2E 选择器迁移

### 6.1 钩子策略

与卖家 U 阶段一致：**data-testid + role/aria/可访问名优先，CSS 类名只做 styling 不做定位**。新组件按下表带 testid;`helpers/ui.ts` 买家段一次性重写;02/02b/08（及 01 的买家部分）spec 里的裸 CSS 定位按表替换。卖家段与 03/04/05/06/07 不受影响。

| 用途 | 旧定位 | 新定位 |
| --- | --- | --- |
| 商品卡 | `.goods-card` | `[data-testid="goods-card"]`（filter hasText 名称） |
| 详情标题/价格/描述 | `.summary .title` / `.summary .price` / `.description` | `getByRole('heading', { name })` / `[data-testid="goods-price"]` / `[data-testid="goods-description"]` |
| 操作提示 | `.notice` | `[data-testid="notice"]` |
| 失效提示条 | `.warn-bar` | `[data-testid="warn-bar"]` |
| 购物车行 | `.row-item` | `[data-testid="cart-row"]` |
| 行勾选/数量/小计/移除 | `.row-check` / `.qty-input` / `.line-total` / `button.remove` | 行内 `[data-testid="cart-check"]` / `cart-qty` / `cart-line-total` / `cart-remove` |
| 全选 | `.bar .check-all input` | `[data-testid="cart-check-all"]` |
| 合计 | `.total strong` | `[data-testid="cart-total"]` |
| 结算/转收藏 | `button.checkout` / `button.to-fav` | `getByRole('button', { name: '去结算' / '转收藏' })`（文案不变） |
| 收藏卡/徽章/取消 | `.fav-card` / `.fav-card .badge` / `button.remove` | `[data-testid="fav-card"]` / `[data-testid="status-tag"]` / `getByRole('button', { name: '取消收藏' })` |
| 订单行/筛选 | `.order-row` / `.filter select` | `[data-testid="order-row"]` / `getByLabel('按状态筛选')`（select 补 aria-label） |
| 明细表/总额 | `.items tbody tr` / `.amount` | `[data-testid="order-items"] tbody tr` / `[data-testid="order-total"]` |
| 空态 | `.card.empty` 文本 | `[data-testid="empty-state"]` + 文案断言（文案不变） |
| 分页 | `button:has-text("下一页")` 等 | 按钮名不变;页码文案统一为「第 x / y 页 · 共 N 笔/条」（见 6.3） |
| 登录/注册表单 | `getByLabel('用户名'…)`、`button.primary` | getByLabel 不变（**保名约束见 6.2**）;按钮改 `getByRole('button', { name })` |
| 搜索 | `input[placeholder="搜索商品名称或描述"]`、`button.search-btn` | placeholder 与按钮名「搜索」不变;类别两个 select 补 aria-label，T02-03 从 `nth(0/1)` 改 getByLabel |

### 6.2 保名约束（迁移时不可破坏）

- 登录/注册**保留包裹式 `<label>`（或 `for`/`id`）**，让 input 有可访问名。卖家端教训：daisyUI `fieldset+legend` 只命名分组不给 input 起名，`getByLabel` 会落空——买家端表单用 `label` 包 `input`（daisyUI 类只加在 input 上），不照搬 fieldset 结构。
- 按钮文案一字不改：登录/注册并登录/加入购物车/收藏/已收藏/去结算/转收藏/取消订单/确认取消/再想想/上一页/下一页/搜索/退出。
- 搜索 placeholder「搜索商品名称或描述」不改（后端只匹配名称，文案是已知差异，E2E 按此断言）。
- 空态/提示文案不改：购物车是空的/去逛逛/还没有在售的商品/已下架/缺货/库存不足/已发货，不能取消/已经在购物车里了/已加入购物车/移到收藏 等。

### 6.3 分页文案统一

三处旧文案不一致（GoodsList「第 x 页 / 共 y 页」、Orders「第 x / y 页 · 共 N 笔」、Favorites「第 x / y 页」）。Pagination 统一为「第 x / y 页 · 共 N {unit}」，unit 由 props 传（笔/条/件）。**这是有意的文案变更**：旧 d5 断言随脚本删除，新 E2E 按新文案断言;「页码常显、按钮仅多页出现」的行为保留。

---

## 7. 样式迁移细则

### 7.1 同名冲突（F0 一次删净）

| 买家旧定义 | 冲突 | 处置 |
| --- | --- | --- |
| `base.css` 的 `.card` | daisyUI `.card` | 删;面板改 `.panel`，商品/收藏卡改标准 `card` 结构 |
| Orders/OrderDetail/Favorites scoped 的 `.badge` | daisyUI `.badge` | 删;换 StatusTag |
| GoodsList scoped 的 `.select` | daisyUI `.select` | 删;换 `select` 组件类 |
| `base.css` 裸 `button`/`input`/`a`/`*` 元素选择器 | 未分层，压过一切工具类 | 删;按钮换 `btn`、输入换 `input`/`select`、链接色交给 `@layer base` |
| `:root` 的 8 个 `--color-*` | 未分层，会压过 daisyUI 主题变量（`--color-primary` 甚至同名） | 删;全部换语义变量 |

### 7.2 旧变量替换表

| 旧 | 新 |
| --- | --- |
| `var(--color-text)` | `var(--color-base-content)` |
| `var(--color-text-weak)` | `.muted` 或 `text-base-content/60` |
| `var(--color-border)` | `var(--color-base-300)` |
| `var(--color-primary)` | `var(--color-primary)`（daisyUI 主题提供，删自定义） |
| `var(--color-danger)` | `var(--color-error)` |
| `var(--color-bg)` | `var(--color-base-200)` |
| `var(--color-surface)` | `var(--color-base-100)` |
| `var(--color-fill)` | `var(--color-base-300)`（图片位/失效底） |

不保留任何旧变量别名（同卖家 U0：别名会让「还差哪页没改」不可见）。

### 7.3 硬编码色替换（43 处）

| 场景 | 旧 | 新 |
| --- | --- | --- |
| 成功提示 `.notice`（4 视图） | `#15803d` | `alert alert-success alert-soft` 或 `text-success` |
| 警示 `.warn`/`.warn-bar`（Cart/GoodsDetail） | `#92400e`/`#fffbeb`/`#fde68a` | `alert alert-warning alert-soft` |
| 订单状态徽章（2 视图 ×6 态） | `#b45309`/`#1d4ed8`/`#eff6ff`/`#bfdbfe`/`#f0fdf4`/`#bbf7d0` 等 | StatusTag tone（warning/info/success/default），色值全部进主题 |
| 主按钮/主链接白字 | `#fff` | `btn-primary`（`primary-content` 自动） |
| 下拉背景 | `#fff` | `select` + `--color-surface-solid`（弹出列表实色，同卖家） |

豁免：描述视频黑底 `#000`（如有）保留。F5 用 grep 卡门禁。

### 7.4 scoped CSS 处置

组件样式**一律工具类化**，删除 scoped CSS（Cart 行的 grid 与移动端 reflow 用 `grid-cols-[…]` + 响应式变体表达）。个别确需保留的复合布局进 `base.css` 的 `@layer components`，**不在 SFC 里留未分层样式**（未分层会压过工具类，之后只能 `!important` 打架——卖家已验证的坑）。改 daisyUI 组件自身外观只用末尾 `@utility`。

**F0 实测补充**：

- SFC 的 `<style scoped>` 是独立样式上下文，里面写 `@apply text-base-content/60` 会报「Cannot apply unknown utility class」，必须 `@reference` 主 CSS 才能解析。因此**scoped 里不要用 `@apply`**：要么纯语义变量（`color-mix(in oklab, var(--color-base-content) 60%, transparent)`），要么把复合类放进 `base.css` 的 `@layer components`（那里有完整 Tailwind 上下文）。App.vue 导航高亮已按前者落地。
- F0  interim 保留 Cart 行网格为**纯布局** scoped（grid 列与窄屏 reflow，不含任何颜色，颜色全走模板语义工具类）;F2 抽出 `CartRow` 时再评估工具类化。其余视图 F0 已无 scoped。

---

## 8. 阶段划分

| 阶段 | 目标 | 完成标志 |
| --- | --- | --- |
| F0 基座 | 装依赖、重写 base.css、useTheme、index.html、App.vue navbar;全站旧类/旧变量/硬编码色机械替换，删同名旧定义 | `npm run build` 通过;浅色观感不退化、深色可读;旧变量 grep 为 0 |
| F1 公共件 | AppIcon/StatusTag/Pagination/EmptyState | 四件 props/emit 稳定;双主题下正常 |
| F2 组件拆分 | goods/cart/favorite/order 组件 + validation/redirect/errorText 外提 + testid 钩子;视图瘦身 | build 通过;行为不变（手工走一遍主链路）;describeError 旧引用为 0 |
| F3 单元测试 | vitest 配置 + 9 个 spec + localStorage 桩/api mock | `npm run test:unit` 全绿;test/ 纳入类型检查 |
| F4 E2E 收编 | helpers/ui.ts 买家段重写 + 02/02b/08/01 选择器迁移 + 14 条新用例 + 删 d3/d5/bat/scripts | 三服务 + 空库重建后全量 **90/90**;`npm run typecheck` 通过 |
| F5 文档与门禁 | §9 全部文档修订;grep 门禁;双主题 9 路由手测;构建产物核对 | 门禁全过;文档口径一致;此后再走 docker 构建 + 推送 |

F0 采用卖家验证过的「一次性机械替换」策略，不做兼容层（理由同阶段U §7）。F2 与 F3 可部分并行（format/orderStatus/cartStorage/usePagedList 的单测不依赖拆分）。F4 依赖 F2 的 testid。

---

## 9. 文档修订清单

| 文件 | 修订点 |
| --- | --- |
| `AGENTS.md` §2 | 样式栈 row 改「两端统一 Tailwind 4 + daisyUI 5（卖家 -sm 密度、买家默认尺寸）」;测试 row 补买家 Vitest |
| `AGENTS.md` §3 | 买家前端命令补 `npm run test:unit` |
| `AGENTS.md` §5 | 删「买家端不受影响/不要带 Tailwind 进买家端」条;「卖家端样式栈」改「两端样式栈」;新增买家差异条（默认尺寸、内联确认不用 modal、内联 alert 不用 toast、960 容器、主题键 `simple-shop.buyer.theme`、表单保 label 可访问名）;语义色/叠加层/主题/图标条款适用两端 |
| `AGENTS.md` §7 | 买家改完跑 build + test:unit;E2E 规模更新为 10 spec/90 条;**新增「前端测试（单元/E2E）一律不直连数据库」硬条款**，明确库内数据断言仅限后端测试、db.js 用途收窄；修改日期 |
| 买家 `README.md` | 技术栈、目录结构、新增「测试」节、本地存储约定补 theme 键、样式约定节;「不引入 UI 组件库」表述删除 |
| E2E `README.md` | 规模 90 条/10 spec;d3/d5 删除说明;目录树;scripts;DB 边界（删对账脚本例外）;买家钩子表 |
| `买家端交接说明.md` | §1 选择器表按 §6 重写;§2 脚本章节改「已收编进主套件」;§3 夹具定位改手工联调;§7 环境步骤更新 |
| 《阶段E-E2E用例清单》 | §3 买家选择器行更新（不再「不变」）;§6 删「将来可查库 probe+skip」例外;§7 处置改「已收编/删除」;§5 登记新增 14 条与覆盖矩阵更新 |
| 《阶段D-买家端开发计划》 | §1.3「不新增测试框架」、§9.2「不引入 UI 组件库」标注「已由阶段F 取代」;顶部加指向阶段F 的说明 |
| `阶段三/目录.md` | 新增阶段F 行（本次先登记） |
| E2E `package.json` / `.env.example` | 删 verify 脚本;FIXTURE_DB_NAME 注释改手工联调 |
| `deploy/frontend/Dockerfile` | **无需修改**（buyer-build 仍 npm ci + npm run build）;F5 验证构建可用 |

---

## 10. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| 同名类/裸元素选择器残留 | 换类后样式不生效，误判 daisyUI 不好用 | F0 一次删净;DevTools 确认 `.btn`/`.card` 单一规则来源;grep 门禁 |
| SFC scoped 未分层 CSS | 工具类调不动，`!important` 打架 | 组件全工具类化;残留复合类进 base.css `@layer components` |
| E2E 买家选择器大面积失效 | 套件红 | F2 先落 testid 与保名约束，F4 一次性迁移 helpers + specs，全量跑验证 |
| 表单改 fieldset 后 getByLabel 落空 | 登录/注册用例全红 | 保留包裹 label/for-id（卖家教训，§6.2） |
| 组件拆分改变数量夹取/失效锁定行为 | 业务回归 | 行为原样搬移;cart store 单测 + T02b-03/06 锁定 |
| 分页文案统一影响断言 | 旧断言失效 | 旧 d5 删除;新用例按新文案;Pagination 单测锁格式 |
| 主题首屏闪白 | 深色用户体验 | index.html 内联脚本先定主题（同卖家） |
| npm ci 拉新依赖失败 | Docker 构建挂 | package-lock.json 与 package.json 同批提交;F5 验证 buyer-build |
| 浮点合计回归 | 金额对不上 | totalCents 分累加单测（0.1+0.2 类用例） |
| 退出/换账号串购物车本地数据 | 隐私与正确性 | auth 单测锁 clearSession 只清本人键;T02b-05 换上下文断言 |
| 与阶段三收尾撞车 | 镜像推完又改 | 本阶段完成后再 docker 构建 + 推送（文档头部已注明） |

---

## 11. 验收门禁

1. `npm run build`（vue-tsc + vite）与 `npm run test:unit` **实际执行并通过**;test/ 无类型错误。
2. E2E 三服务 + 空库重建后全量 **90/90**、`npm run typecheck` 通过;环境不具备时如实说明哪些没跑、原因（AGENTS.md §7）。
3. grep 门禁（买家 src），**以临时脚本执行**（放在 git 仓库之外、不进版本库，重构完成后删除，见 F5）：
   - 旧变量 `var(--color-(text|text-weak|border|primary|danger|bg|surface|fill))` = 0;
   - 硬编码色 `#[0-9a-fA-F]{3,8}` = 0（视频黑底豁免需登记）;
   - 自定义 `.card`/`.badge`/`.select`/`.input` 定义 = 0;裸 `button`/`input` 全局样式 = 0;
   - `describeError` 从 `stores/auth` 的旧 import = 0。
4. 双主题手测：9 条路由 × 浅/深逐页截图核对;主题切换刷新保持;清 localStorage 跟随系统;首屏不闪。
5. 主链路手工走查（加购→改数量→勾选→结算→详情→取消;收藏→转收藏;失效行;空态;错误态）行为与改造前一致。
6. 文档口径一致：AGENTS.md、两端 README、E2E 交接/清单、目录索引无残留旧口径。
7. `deploy/frontend/Dockerfile` buyer-build 可构建（收尾时与镜像推送一并验证）。

---

## 12. 任务完成登记区

> 登记规则同《阶段D》：只改状态/负责人/完成日期/备注;新任务追加 F6-1…;取消标「取消」写原因不删行;状态取值 未开始/进行中/已完成/阻塞/取消;「已完成」= 代码合并 + 门禁实际执行通过 + 文档同步;日期 YYYY-MM-DD。

| 编号 | 任务 | 前置 | 状态 | 完成日期 | 备注 |
| --- | --- | --- | --- | --- | --- |
| F0 | 基座：依赖、base.css 双主题、useTheme、index.html、App.vue、全站机械替换 | — | 已完成 | 2026-11-15 | 依赖、base.css 双主题、useTheme、index.html、AppIcon(sun/moon)、App.vue navbar、8 视图机械替换;build 与临时门禁 0 残留;用户联调确认各数据页渲染正常。页尾色带（body 未铺满视口）与窄屏导航逐字折行两处在 F1 顺带修正。 |
| F1 | 公共件：AppIcon/StatusTag/Pagination/EmptyState | F0 | 已完成 | 2026-11-15 | StatusTag(label+tone→badge、testid=status-tag)、Pagination(join+统一页码文案「第 x / y 页 · 共 N unit」)、EmptyState(title/description/slot、testid=empty-state) 落地并接入视图;ORDER_STATUS_TONES 改为 tone 契约;注册输入补 aria-label 保证可访问名干净。双主题带数据实测：空态/购物车行/订单 StatusTag/分页均正常。 |
| F2 | 组件拆分与纯逻辑外提 + testid 钩子 | F1 | 已完成 | 2026-11-15 | 8 个域组件（GoodsCard/GoodsGallery/DescriptionBlocks/CartRow/CartSummaryBar/FavoriteCard/OrderRow/OrderItemsTable）+ 3 个纯逻辑模块（utils/errorText、utils/redirect、validation/register）落地;describeError 迁出 stores/auth;testid 钩子齐备（旧 E2E 类暂保留待 F4 迁移）。按用户反馈顺带：搜索栏加「清除」按钮（清空关键字+类别回第 1 页）、顶栏改单行 flex（窄屏导航整行堆叠）、窄屏搜索输入独占一行+类别与按钮整组另起一行。build/门禁/用户全链路回归通过。 |
| F3 | 单元测试：vitest + 9 spec | F0（部分依赖 F2） | 已完成 | 2026-11-15 | 9 个 spec / 84 用例全绿（format/orderStatus/cartStorage/errorText/redirect/register/usePagedList/cart store/auth store）。vitest node + **forks** + env.TZ=UTC（threads 共享主进程 ICU、运行时改 TZ 无效）;test/setup.ts 内存 localStorage 桩先于源码导入安装；test/ 纳入 tsconfig 类型检查。auth store 不暴露 setSession，测试经 mock login / 直写本地键建立会话。 |
| F4 | E2E 收编：选择器迁移 + 14 新用例 + 删 d3/d5 配套 | F2 | 已完成 | 2026-11-15 | helpers/ui.ts 买家段改 testid/role/aria；02/02b/08/01 选择器迁移；新增 14 条（01+1、02+1、02b+7、02c 新 spec 4、08+1），套件 **10 spec / 90 条**；删 d3/d5/verify-cart.bat 与 verify 脚本，db.js/.env.example 口径改「测试不连库、夹具仅手工联调」。自跑环境（db:reset + 后端连 E2E 库关限流 + 两端 dev）全量 **90/90（约 1.5m）**。修两处：02b-15/16 改用新造商品（旧商品被前面用例的未完成订单引用，下架回 20020）；T01-09 用 exact 名 + 先等登出态再断言，消除全量下的偶发竞态。 |
| F5 | 文档修订与门禁、双主题手测、构建核对 | F3、F4 | 已完成 | 2026-11-15 | AGENTS.md(§2/3/5/7)、买家 README、E2E README、买家端交接说明、阶段E-E2E用例清单、阶段D 标注、目录索引全部同步；门禁 0 残留、buyer test 84/84 + build、seller build 通过；双主题由用户逐阶段目视确认。**修复 docker `npm ci` 不同步：seller package-lock 缺 tailwind/daisyui 依赖，用 `npm install --package-lock-only` 重建；根 .dockerignore 并未排除 lock（非其因），seller 自带 .dockerignore 在根构建上下文下不生效但无害**。临时门禁脚本 f0-gate.mjs 已按约定删除（重构完成）。 |

---

**文档结束**