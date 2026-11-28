# simple-shop 卖家前端

simple-shop 的**卖家后台**，前后端分离的 Vue 单页应用。本工程只包含卖家端，买家端是独立的 `simple-shop-buyer-frontend` 工程，两端不互相依赖。

> **阶段进度：阶段四卖家端已交付。** 订单七态、物流编辑、售后管理、客户页去默认地点均已完成。整体进度见[根 README](../README.md)。

## 先看哪里

| 要找什么 | 去哪 |
| --- | --- |
| 接口清单、字段、错误码 | [openapi.json](../simple-shop-doc/阶段四/openapi.json) + 本工程 `src/api/types.ts`（与契约逐字对应） |
| 页面与登录要求 | `src/router/index.ts` |
| 业务码 → 界面文案 | `src/constants/messages.ts`（唯一来源） |
| 订单动作矩阵 | `src/components/order/orderActions.ts` |
| 售后动作 | `src/components/aftersale/aftersaleActions.ts` |

## 一、技术栈

| 类别     | 选型                               |
| -------- | ---------------------------------- |
| 框架     | Vue 3（script setup + TypeScript） |
| 构建     | Vite                               |
| 路由     | Vue Router                         |
| 状态     | Pinia                              |
| 请求     | axios                              |
| 样式     | Tailwind CSS 4 + daisyUI 5（深/浅双主题） |
| 测试     | Vitest                             |
| 格式化   | Prettier                           |
| 类型检查 | vue-tsc                            |

Node 版本要求见 package.json 的 engines 字段。

## 二、快速开始

### 1. 启动后端

必须在**后端工程的根目录**执行（默认 8080 端口，需要本机 MySQL）：

    cd simple-shop-backend
    mvn spring-boot:run

后端启动时 Flyway 会自动建库建表（开发库 `simple_shop`）。

> ⚠️ **阶段四要按 V4 重建库**：V4 给订单加的是 `NOT NULL` 无默认值的收货快照列，订单表必须为空才能执行，旧库会启动失败。**不要关闭 Flyway 校验硬绕**，新建干净库并用 `DB_URL` 指过去即可；删库前先备份并确认。

### 2. 启动前端

    cd simple-shop-seller-frontend
    npm install
    npm run dev

打开 http://localhost:5173 ，用种子账号登录：

    账号：seller
    口令：Abcd@1234

### 3. 开发代理与后端地址

开发服务器已把 **/api** 与 **/media** 代理到 http://localhost:8080（见 vite.config.ts），本地开发无需处理跨域；商品图、描述媒体、售后凭证图均经 /media/** 访问。

部署（或后端不在本机）时，用环境变量 **VITE_API_BASE_URL** 指定接口前缀；不设置时默认 **/api**。生产构建 base 为 `/seller/`，开发期仍在根路径——不要用生产 URL 规则套开发地址。

联调常用开关：`PAYMENT_MOCK_FAIL_CHANNELS=ALIPAY` 复现支付失败；`ORDER_PAYMENT_TIMEOUT` 调短复现超时；批量造数时 `RATE_LIMIT_ENABLED=false`。

## 三、常用脚本

| 脚本               | 作用                    |
| ------------------ | ----------------------- |
| npm run dev        | 开发服务器（默认 5173） |
| npm run build      | 类型检查 + 生产构建     |
| npm run preview    | 预览构建产物            |
| npm run type-check | vue-tsc 类型检查        |
| npm run test:unit  | 单元测试（test/ 目录，无需后端） |
| npm run test:api   | 接口连通性测试（需后端，不可达自动跳过） |
| npm run test:watch | 单元测试监听模式        |
| npm run format     | Prettier 格式化 src/    |

## 四、目录结构

```text
src/
  api/           按域拆分的接口模块（session、account、goods、media、category、customer、orders、aftersale）+ http / token / types
  views/         12 个页面（路由表见 router/index.ts；商品、订单、售后、客户、类别、账号六组）
  components/    common（AppIcon / ConfirmDialog / Pagination / StatusTag / ToastHost）与按域分包：
                 goods（卡片、类别级联、图片选择、描述块编辑）、order（列表、流程图、物流弹窗、动作表）、
                 aftersale（动作表）、category（卡片、行内改名）
  composables/   usePagedList / useRequestSequence（竞态守卫）、useToast、useTheme、
                 useCategoryTree、usePublishFlow（发布编排）
  constants/     labels（枚举中文）、messages（提示与错误码文案）、carriers（承运商清单）
  validation/    登录、商品、类别、物流表单校验
  layouts/       后台骨架（侧边导航）
  stores/        auth（登录态）
  router/        路由与登录守卫
  assets/        base.css（Tailwind + daisyUI 唯一入口）、图标 SVG
test/            Vitest 单元测试（按模块镜像 validation / composables / constants / components）
```

组件按功能分子文件夹；页面不拆分。

## 五、重要决定

### 1. 业务拒绝 = HTTP 200 + 业务码

后端把业务规则拒绝视为**正常分支**，返回 HTTP 200 + 业务码；例外只有会话失效→401、参数错误→400、资源不存在→404、服务端错误→500。前端请求层对这两种情形一律抛带 code 与 httpStatus 的错误对象，页面按 code 分支处理。

**message 不用于展示**：它是给日志与排查用的 ASCII 英文；界面文案统一由 `constants/messages.ts` 映射，界面从不直接显示后端 message。

### 2. 商品两态 + 下架原因

状态只有 `ON_SALE` / `OFF_SHELF`；下架原因 `MANUAL` / `STOCK_ZERO` / `SOLD_OUT` 与状态成对展示。**MANUAL 是终态**：库存只读、没有重新上架入口，类别仍可改挂；`STOCK_ZERO` / `SOLD_OUT` 把库存改回大于 0 保存即**自动上架**。

阶段四起不再有「有未完成订单不许下架 / 不许清零」的限制：库存不预占、成交才扣减，卖家随时可下架或调库存；成交扣减到 0 仍自动下架（`SOLD_OUT`）。商品是否可购买以后端 `purchasable` 为准，前端不自行推断。

### 3. 订单七态：动作表驱动，成交由买家推进

七态整单流转：待支付 → 已支付 → 已确认 → 备货完成 → 已发货 → 交易完成，另有终态已取消。详情页用「状态 → 下一步动作」表（`order/orderActions.ts`，有单元测试）驱动渲染，不存在跳级入口：

| 状态 | 卖家可做 |
| --- | --- |
| 待支付 | 取消（**不能确认**，页面只解释原因，不给灰按钮） |
| 已支付 | 确认订单、取消（全额退） |
| 已确认 | 标记备货完成、取消（全额退） |
| 备货完成 | 开始发货（必须填承运商与运单号）、取消（全额退） |
| 已发货 | 修改物流、取消（全额退） |
| 交易完成 | 修改物流（无进度条、无取消） |
| 已取消 | 无动作 |

- **卖家没有「完成订单」**：交易完成只由买家确认收货推进，卖家端没有这个按钮与端点。
- 成交时按明细扣库存，任一不足整单拒绝（订单停在已发货），买家补货后可重试。
- 取消是整单终态、不动库存；已支付订单取消会全额原路退款，确认文案随是否已付款而变。
- 订单头、明细、售后是三次独立请求，各自持有 loading / error / 重试，互不拖垮。

### 4. 售后处理：登记结果或取消，二者都是终态

- 售后三态 `PENDING` / `COMPLETED` / `CANCELED`；「取消」与「拒绝」是同一个动作，没有独立的已拒绝态。
- 完成必须选处理结果（退货并退款 / 仅退款 / 换货），**不预选任何一项**——完成是终态、退款不可撤销，默认选中等于让人点两下就退钱。
- 退款金额由后端按「订单项单价 × 数量」算，页面只展示不提供输入框；换货不产生退款，也不占退款额度。
- 同一订单项只能退款一次；被拒时 Toast 提示并重拉，售后仍停在待处理。
- 完成与取消后会提示「换货不产生退款，仍可再申请」或「买家可以重新申请，会生成新单号」。
- 订单详情底部只读展示「该订单的售后」，处理动作一律在售后管理里做，不重复实现。

### 5. 发货与改物流共用一个弹窗

承运商是「固定清单 + 其他」：清单见 `constants/carriers.ts`（显示名短、存公司全名），选「其他」时才出现名称输入框；后端只存字符串、不做枚举。运单号是多行文本（一个订单可拆多个包裹），列表只显示首行、悬停 `title` 给完整单号，详情按换行展示。

校验在本地先做（必填、长度、控制字符），提交由页面负责；改物流时弹窗按订单现值回填（清单外的承运商落到「其他」并填原名，避免一编辑就冲掉），保存后状态不变。

### 6. 媒体一律延迟到发布时统一上传

主图、图集、描述媒体在编辑期只保留本地 File（商品图带 objectURL 缩略预览，描述媒体不预览、显示为文件名行）。点「发布」→ 确认 → **串行上传**（主图→图集→描述媒体，进度条显示 i/n）→ 全部回填 URL 后终检 → 一次性发布。编排集中在 `composables/usePublishFlow`。

任何一环失败即中止，已成功者的 URL 缓存在条目上，**重试只传剩余**（失败后按钮变「重试发布」，确认弹窗说明不重复上传）。放弃发布会留下服务端孤立文件，系统不做清理（已接受）。提交进行中整个表单（含描述编辑器）锁定，避免上传途中增删媒体。

### 7. 描述块编辑器：隐藏块结构的「行流」编辑器

后端描述是结构化块数组（TEXT/IMAGE/VIDEO），用户视角是**一个连续编辑区**：文本行是自动增高的无边框 textarea，媒体行是原子行（类型徽章+文件名+大小+删除），可聚焦后按 Backspace 删除；行首 Backspace / 行尾 Delete 合并相邻文本行或删除相邻媒体；「插入图片/视频」在**当前光标处**切分插入。行的切分/合并/删除是 `descriptionEditor.ts` 的**纯函数**（有单元测试），组件只负责 DOM 焦点与自动增高。组件只做本地预检与累计字数（0/1000）展示，提交校验在发布页。

### 8. 状态与金额的契约口径

- 状态枚举一律**大写**：`ON_SALE/OFF_SHELF`、`MANUAL/STOCK_ZERO/SOLD_OUT`、订单七态、售后三态与三种结果。
- **price 是 JSON 数字**（后端 BigDecimal 直接序列化）；展示统一走 `formatPrice`，发布请求传 Number。
- DTO 字段保持 **snake_case**，与后端 JSON 逐字一致，不做驼峰转换。
- 订单主键字段是 `order_id`（不是 id）；列表与详情只回订单头，明细按订单 ID 单独查。
- 支付与物流字段只读：`payment_channel` 与 `paid_at` 同生同灭，未支付时都不显示；`carrier` / `tracking_no` 可能含换行。

### 9. 登录态与 401（并发只提示一次）

- 令牌存 localStorage（后端会话 30 分钟滑动过期），key 为 `simple-shop.seller.token`。
- 任意接口返回会话失效码：清空本地令牌并统一跳回登录页（main.ts 注册）。页面挂载常有**多个并发请求同时 401**，两层防重：跳转动作加单飞标记；Toast 对同文案提示做 1.5 秒窗口去重。
- 修改密码成功后后端吊销全部会话（含本次），前端同步清空并强制重新登录。

### 10. 前后端双重校验

`validation/` 按域拆分（login/goods/category/logistics），规则与后端一致：名称/类别名 ≤50 码点拒尖括号与换行、价格按 BigDecimal 口径（0<p≤999999.99 两位小数，支持科学计数法）、发布库存 ≥1 调整库存 ≥0、描述文本**各块合计** ≤1000 码点、图集 ≤8 张连主图 ≤9 张、媒体类型与大小本地预检（图 ≤5MB、视频 ≤20MB）、承运商 ≤50 字且单行、运单号 ≤500 字（允许换行）。发布表单基础字段由 `validatePublishForm` 一次校验返回逐字段文案与归一化值。服务端始终是唯一权威。

### 11. 列表加载与共享状态

- **usePagedList**：商品/订单/售后/客户四个列表共用分页状态与加载逻辑，内置 **useRequestSequence 请求序号守卫**——快速切换筛选/翻页/路由参数时，过期响应（含错误）整体丢弃，loading 只由最新请求复位。商品、订单、售后把筛选写回 URL（刷新/分享可还原），客户列表不写。
- **useCategoryTree**：类别树模块级共享缓存 + 单飞请求，级联选择器挂载不再重复拉取；类别管理页进入时与每次增删改后强制 refresh 更新缓存。
- 订单列表是**同一份组件**的三种筛选入口（订单管理 / 按客户 / 按商品），差别只在 query 与是否写回 URL；详情一律按 order_id 进。

### 12. 类别管理：全局单编辑 + 瀑布流

编辑态由页面持有，**全局同一时刻只有一个行内编辑**，开始新编辑自动收起上一个；行内表单统一为 `InlineNameEditor`（挂载即聚焦、Esc 取消、Enter 提交）。布局用 CSS 多列瀑布流，卡片按内容自然高度排列；类别无序，列优先的阅读顺序可接受。

### 13. 发布页离开保护

表单脏（任一字段非默认值或有媒体/描述内容）且未发布成功时，路由离开弹确认框、关闭标签页触发 beforeunload 提示；发布成功后正常跳转不受影响。名称输入带实时码点计数（x/50，超限变红）。

### 14. 样式：Tailwind CSS 4 + daisyUI 5

- 唯一 CSS 入口是 `assets/base.css`：`@import "tailwindcss"` + `@plugin "daisyui"` + `shop-light` / `shop-dark` 两套主题（token 与买家端同值）。优先级：daisyUI 组件类 > Tailwind 工具类 > `@layer components` 复合类（`.panel` 与 `.muted`）。
- **不要与 daisyUI 重名**：`.btn`、`.card`、`.input`、`.textarea`、`.select`、`.table`、`.badge`、`.menu`、`.modal`、`.toast`、`.alert` 归 daisyUI 所有，项目里不再定义同名类，也不给这些元素补裸写覆盖；项目自有容器统一用 `.panel`。
- **要改 daisyUI 组件自身外观用 `@utility`**：daisyUI 组件类落在 utilities 层子层，`@layer components` 盖不住（会被静默忽略）。全局调整写成 `@utility btn { font-size: ... }` 这种形式。
- 颜色一律用语义色（`base-*`、`primary`、`success`、`warning`、`error`、`info`），不写死色值，否则深色主题必然失效；叠加层用实色 `--color-surface-solid`（浅色 `base-100` 是半透明，浮层必须实色）。
- SFC `<style scoped>` 是独立样式上下文，里面用 `@apply` 需 `@reference` 主 CSS，否则报 unknown utility；scoped 里优先纯语义变量或全工具类。
- 主题切换与持久化只走 `composables/useTheme`，首屏由 index.html 内联脚本先定，避免闪白。
- 图标统一用 `AppIcon`（内联 SVG + currentColor），不要 `<img src="*.svg">` 直插——assets 里的图标是固定黑色填充，深色主题下会糊成黑块。
- 数据密集型界面默认 `-sm` 一档（`btn-sm`/`input-sm`/`select-sm`/`table-sm`）；页面切换有 120ms 过渡，`prefers-reduced-motion` 下自动关闭。

### 15. 时间与金额的展示口径

后端一律下发 UTC 的 ISO 8601 时间；前端在 `utils/format.ts` 统一格式化展示。金额为 JSON 数字，展示时只做千分位与两位小数格式化。

## 六、接口层约定

- 接口按域拆到 `api/` 下，`types.ts` 与契约逐字对应；改接口时同步契约快照与本文件，`index.ts` 统一导出。
- 统一响应形状 `{code, message, data}`（参数错误时另带 `errors: [{field, message}]`）；分页响应 `{items, page, page_size, total}`，page 从 1 起、page_size 上限 100。
- 错误码常量集中在 `api/types.ts`，文案映射在 `constants/messages.ts`；未登记的码走通用兜底「操作失败，请稍后重试」，不猜原因。已停用的码位（`10008`、`20020` 与整段 `3xxxx`/`4xxxx`）不复用。
- 令牌由 `http.ts` 请求拦截器统一注入 `Authorization: Bearer ...`。
- 未知路由（含阶段二遗留的旧深链）统一重定向回商品列表，不渲染空白页。

## 七、测试

测试分两类，命令互不干扰：

    npm run test:unit    # 单元测试:test/ 目录,纯函数与 composable,无需后端
    npm run test:api     # 连通性测试:src/api/__tests__,需要后端
    npm run test:watch   # 单元测试监听模式

- **单元测试**（Vitest，node 环境）覆盖：validation 全部校验器（含价格 BigDecimal 口径、发布表单整体校验、物流表单）、composables（请求序号守卫、分页列表、类别树缓存、发布编排）、描述行操作纯函数与媒体预检、订单与售后的状态→动作矩阵、承运商常量。发布编排与类别树的接口调用以 mock 隔离。
- **连通性测试**默认访问 http://localhost:8080，验证登录与受保护接口的会话失效语义；后端不可达时**自动跳过**并打印提示，不会让测试变红。可用环境变量覆盖：`SMOKE_BASE_URL`、`SMOKE_ACCOUNT`、`SMOKE_PASSWORD`。
- `vitest.config.ts` 同时收集 `src/**/__tests__` 与 `test/` 两处；两个 npm 命令用路径过滤各取所需。

### 7.1 首屏性能（LCP / FCP）

用 E2E 工程里的 perf 脚本量**构建产物**，不要用 dev server，也不要在 DevTools 里手点：

```bash
# 前置：后端 8080 起着；本工程 npm run build && npm run preview（预览已配 /api 代理）
cd ../simple-shop-test-e2e
npm run perf -- --url=http://localhost:4173/login --name=卖家登录
npm run perf -- --url=http://localhost:4173/ --name=卖家首页 --auth=seller --runs=5
```

条件：CPU 4× 降速 + Slow 4G（150ms RTT / 1.6Mbps），冷缓存，跑 5 次取中位数并报 LCP 元素。
加 `--budget-lcp=2.5` 可当门禁（超预算退出码 1）。

**基线（2026-11-14，本机）**

| 页面 | TTFB | FCP | LCP（中位） | LCP 元素 | CLS |
| --- | --- | --- | --- | --- | :---: |
| `/login` | 0.15s | 1.27s | **1.27s** | `p` SimpleShop 87×24 | 0 |
| `/`（登录态） | 0.15s | 1.65s | **1.84s** | 商品卡主图 `img` 209×209 | 0 |

首页的 LCP 是列表接口返回后加载的主图，所以「接口慢 / 图大」会直接反映在 LCP 上；
登录页几乎没有图片，LCP 就是标题文字，主要成本在「下载 JS + 4× 降速下解析执行」。

**已修：首帧整页淡入导致 FCP/LCP 无法测量**

之前 `<RouterView>` 外包着 `<Transition name="fade">`，而 `app.mount()` 不等路由就绪：
首次渲染拿不到目标组件，等路由解析完再「进场」，整页从 `opacity:0` 淡到 1。
合成器驱动的透明度动画不产生新的 paint，Chromium 因此**只报 first-paint、不报 FCP/LCP**
（实测：默认动效偏好下 `performance.getEntriesByType('paint')` 永远只有 first-paint，
把系统改成「减少动效」才出现 FCP）。修法是在 `main.ts` 里 `router.isReady()` 后再挂载，
首屏直接渲染目标页面，`<Transition>` 看到的是首次渲染而不是进场。

## 八、已知偏离与限制

| 项             | 说明                                                                       |
| -------------- | -------------------------------------------------------------------------- |
| 商品内容不可改 | 发布后名称/价格/描述/图片不可修改,仅库存与类别可调（需求既定）              |
| 孤立媒体文件   | 上传成功但发布失败/放弃时产生,系统不做清理                                 |
| 描述编辑器     | 媒体行拖拽排序、媒体预览、粘贴/拖入文件均不做（已接受的取舍）               |
| 类别瀑布流     | CSS 多列布局为列优先顺序;编辑增高时列会重新平衡,相邻卡片可能轻微移位        |
| 会话           | 后端会话 30 分钟滑动过期、存于进程内,backend 重启会丢会话                   |
| 售后无独立拒绝态 | 后端没有「已拒绝」，「取消」与「拒绝」是同一个动作，界面统一显示已取消     |
| 物流无轨迹     | 只存承运商、单号（可多行）与备注，不查快递轨迹，没有节点时间线              |
| 部署配置       | 正式部署用 `deploy/frontend` 组合镜像（同时托管买家 `/` 与卖家 `/seller/`）；本工程 Dockerfile/nginx.conf 仅用于卖家独立预览 |
| 参数校验明细   | 后端 400 会带 `errors[]`，卖家端不逐字段回填（能到这一步的都是前端校验漏掉的输入），只按业务码给一句中文提示 |
