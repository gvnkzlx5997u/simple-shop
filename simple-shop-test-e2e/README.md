# simple-shop E2E

> **阶段进度：E2E 待改造。** `tests/` 现有 10 spec / 90 条，仍按**阶段三口径**编写（六态、卖家「完成订单」、购物车直接下单），对阶段四后端会成片失败，暂不作为验收依据。
> 阶段四最终用例清单已产出：[阶段四 E2E 用例清单](../simple-shop-doc/阶段四/阶段四-E2E用例清单.md) —— 目标 15 spec / 142 条（保留 63、修改 26、新增 53）。套件改造落地后再回填本文件的条数、批次与已知现象。
> 整体进度见[根 README](../README.md)。

Playwright 驱动**系统已装的 Edge**（不下载 Chromium），对真实运行中的前后端做端到端验收。

- 现状规模：**10 个 spec / 90 条用例**（阶段三口径，全量约 **1.5 分钟**）
- 稳定性：`workers:1` 串行；空库重建后全量跑通（90/90）
- 历史套件都在 `archive/`，**不要放回 `tests/`**，否则会被 `playwright test` 误跑；对应关系见 `archive/目录.md`，注意旧套件的状态口径可能过时

---

## 一、跑之前要准备什么

### 0. 一次性配置：复制 `.env`

```bash
cp .env.example .env     # Windows: copy .env.example .env
# 然后编辑 .env，至少填 E2E_DB_PASSWORD
```

`.env` 已在 `.gitignore` 里，**不要提交**。优先级是「真实环境变量 > `.env` > 代码默认值」，
所以 CI 或临时切环境照旧可以 `E2E_DB_NAME=xxx npm test` 覆盖单项，其余仍从 `.env` 读。

| 变量 | 用途 | 默认 |
| --- | --- | --- |
| `E2E_DB_HOST` / `E2E_DB_PORT` / `E2E_DB_USER` | 数据库连接 | `127.0.0.1` / `3306` / `root` |
| `E2E_DB_PASSWORD` | 数据库口令 | 空 |
| `E2E_DB_NAME` | **E2E 主库**（`db:reset` / `db:probe`） | `simple_shop_e2e` |
| `E2E_FIXTURE_DB_NAME` | **手工联调夹具库**（阶段F 起测试不再连库），与主库分开 | `simple_shop_d1` |
| `API_URL` / `BUYER_URL` / `SELLER_URL` | 三个服务地址 | 8080 / 5174 / 5173 |
| `SELLER_ACCOUNT` / `SELLER_PASSWORD` | 种子卖家账号 | `seller` / `Abcd@1234` |

`scripts/db.js` 与 `helpers/config.ts` 各自在加载时读一次 `.env`，所以**不用在终端 export，也不用在 .bat 里 set**。

### 1. 三个服务都要先起来

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 后端 | `http://localhost:8080` | 连**独立 E2E 库**，不要连开发库 `simple_shop` |
| 买家 SPA | `http://localhost:5174` | `npm run dev -- --port 5174` |
| 卖家 SPA | `http://localhost:5173` | `npm run dev -- --port 5173` |

后端启动示例（独立库 + 关闭限流，否则批量注册会被 50001 拒绝）：

```bash
cd simple-shop-backend
DB_PASSWORD=<你的 MySQL 口令> \
DB_URL="jdbc:mysql://localhost:3306/simple_shop_e2e?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&connectionTimeZone=UTC&rewriteBatchedStatements=true" \
java -Dserver.port=8080 \
     -DRATE_LIMIT_ENABLED=false \
     -jar target/simple-shop.war
```

后端添加了环境变量配置，可以通过环境变量一行启动：
```powershell
$env:RATE_LIMIT_ENABLED="false";$env:DB_PASSWORD=<你的 MySQL 口令>;$env:DB_URL="jdbc:mysql://localhost:3306/simple_shop_e2e?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&connectionTimeZone=UTC&rewriteBatchedStatements=true";java -jar .\target\simple-shop.war
```

阶段四联调还要注意：`MEDIA_DIR=./target/test-media`（售后凭证图落盘，**每轮跑前清空**）、`PAYMENT_MOCK_ENABLED=true`（默认就开）；要复现「支付失败仍待支付」用 `PAYMENT_MOCK_FAIL_CHANNELS=ALIPAY` 重启后端，不要改请求参数。完整的环境清单与批次划分见[阶段四 E2E 用例清单](../simple-shop-doc/阶段四/阶段四-E2E用例清单.md) §1.1 与 §6。


### 2. E2E 库跑前重建（保证可重复）

```bash
npm run db:probe     # 只探活并打印目标库，不动数据
npm run db:reset     # DROP + CREATE simple_shop_e2e
```

- 重建走 **node 直连**（`scripts/db.js`，mysql2），**不需要装 mysql 命令行、也不依赖它的安装路径**；
  `scripts\reset-e2e-db.bat` 保留为薄壳（内部还是调同一个 node 脚本），老命令照样能用。
- 参数都在 `.env` 里；临时指别的库用 `--db=库名`，或覆盖环境变量（优先级高于 `.env`）。
- 脚本只 `DROP/CREATE` 目标库，**不碰** `simple_shop` / `simple_shop_test`——这两个库名在 `db.js` 里有硬保护，传进去会直接拒绝。
- 重建后启动后端，Flyway 会自动建 12 张表并写入种子卖家。

### 2.5 为什么数据库操作走 node 而不是命令行

防止出现环境问题，node能保证跨系统兼容性。

**边界**：只有「重建库」与「手工联调夹具」用数据库连接；**测试（单元 / E2E）一律不连库**——
造数走接口、断言看接口返回与页面，避免用例跟着表结构漂移。

### 3. 依赖

```bash
npm ci
```

---

## 二、怎么跑

```bash
npm run clean                                # 清掉上一轮的 report/ evidence/ test-results/（只删生成物）
npm test                                     # 全量（10 个 spec / 90 条，约 1.5 分钟）
npx playwright test --headed                 # 看着它点
npx playwright test tests/03-seller-goods.spec.ts   # 只跑某个 spec（低配机器推荐分批跑）
npm run report                               # 看 HTML 报告
npm run typecheck                            # 类型检查（Playwright 运行时只转译，不查类型）
```

### 低配机器注意事项（本机实测踩过）

| 项 | 做法 | 原因 |
| --- | --- | --- |
| 并发 | **固定 `workers: 1`**，不要加 `--workers` | 一个 Edge + 两个 Vite + 一个 JVM 已吃掉大部分内存，并发会把内存打满 |
| 失败即停 | `maxFailures: 5`（配置里），想跑全量时加 `--max-failures=0` | 环境没起时继续跑只会反复开浏览器、堆 trace |
| 缓存 | 每轮失败后 `npm run clean` | trace + 截图单轮可达几十 MB，越攒越慢 |
| 跑完 | 关掉三个服务再开新的一轮 | 三个服务常驻比跑用例本身更占内存 |
| 证据 | 只在需要定位时保留 `evidence/`，确认后清理 | 报告与 trace 对下一轮没有价值 |
| 后台 | 关闭IDE等应用，用终端启动 | 节省内存 |

地址和账号写在 `.env` 里（改一次，Playwright 与对账脚本共用）；想临时换一组值就覆盖环境变量，不必改文件：

```bash
BUYER_URL=http://localhost:5174 \
SELLER_URL=http://localhost:5173 \
API_URL=http://localhost:8080 \
SELLER_PASSWORD=Abcd@1234 \
npx playwright test
```

**换成部署形态（Compose）时**，只改 `.env` 里的这三个地址即可：

```bash
BUYER_URL=http://localhost            # nginx 托管买家 /
SELLER_URL=http://localhost/seller    # 卖家挂在 /seller/
API_URL=http://localhost
```

---

## 二·五、关于容器化（暂不做）

**结论：E2E 不做容器化，用「本地三服务 + 独立库」跑。**

| 原因 | 说明 |
| --- | --- |
| 要求Docker环境 | 不能保证开发机上有docker安装 |
| 调试成本 | 每轮要先构建 backend/frontend 镜像、等 mysql healthy，一轮从 20 秒变成几分钟 |
| 数据隔离用库解决就够 | 独立库 `simple_shop_e2e` + 跑前重建（`npm run db:reset`），同样能保证可重复 |

**代价（必须知道）**：本地三端口形态**不等于部署形态**。部署时是 nginx 单容器同时托管买家 `/`、卖家 `/seller/`，并反代 `/api`、`/media`。这条链路 E2E 不覆盖。

**补救**：用例里没有任何硬编码地址，切部署形态改环境变量后运行（`.env`或临时变量）：

```bash
BUYER_URL=http://localhost        \
SELLER_URL=http://localhost/seller \
API_URL=http://localhost          \
npx playwright test
```

---

## 三、用例清单（阶段三口径，90 条）

> 下表是**现有套件**的实际覆盖，状态口径仍是阶段三的六态与「卖家完成订单」。阶段四的验收以[用例清单](../simple-shop-doc/阶段四/阶段四-E2E用例清单.md)为准；两者冲突时按清单改，不要把旧断言当契约。

| Spec | 条数 | 覆盖 |
| --- | :---: | --- |
| `01-auth.spec.ts` | 9 | 卖家未登录跳转、登录成功/失败、买家注册即登录、买家登录、**买卖家令牌互调被拒**、错误口令、**买家退出登录入口消失可重登** |
| `02-buyer.spec.ts` | 5 | 匿名看列表、**搜索只匹配名称**、两级类别筛选、详情价格（两位小数）/库存/描述、**搜索无结果空态** |
| `02b-buyer-cart.spec.ts` | 17 | 未登录拦截、重复加购仍一行、改数量/夹库存/合计、移除、**数量与勾选不落库/刷新保持**、失效行置灰不可结算、结算生成一笔两明细+回购物车空态、收藏与取消、收藏失效不可进详情、转收藏、**未登录加购/收藏回跳、空车引导、勾选全选合计、下单瞬间下架/缺货被拦并自动刷新、收藏刷新回显** |
| `02c-buyer-orders.spec.ts` | 4 | **单件经购物车下单跳详情、订单分页与翻页、切筛选回第 1 页+空态清筛选、订单头与明细独立请求+明细失败单独重试** |
| `03-seller-goods.spec.ts` | 11 | UI 发布商品、库存改 0 自动下架（库存耗尽）/改回自动上架、手动下架终态（库存只读、无上架入口、可按原因筛出）、**有未完成订单下架被拒 20020**、删被引用类别 20006、库存负数 400+50002、**状态+原因筛选**、类别改名/删空类别、类别管理页手工新增一级/二级 |
| `04-order-lifecycle.spec.ts` | 14 | 七 tab（含已取消）、状态进 URL、行→详情、头+明细合计、只有一个推进按钮、四步推进、完成扣库存/扣到 0 自动下架、**完成缺货 60004**、卖家取消（含已发货）、终态无动作/60003、跳级 60002、重复取消幂等、空态、商品内嵌订单 |
| `05-contract.spec.ts` | 15 | 400+50002、`errors` 定位字段、名称 50/51 边界、价格 0/三位小数、发布库存 0、**非法查询参数（订单状态、无 status 的原因）**、未登录 401、分页默认值、媒体边界（图片 >5MB→20016、视频 20MB 边界可传、>20MB→20026、视频当商品图→20027、文本伪装图片→20016） |
| `06-security.spec.ts` | 3 | **有未完成订单时绕过前端下架/清零 20020**、**已下架商品加购 70001 + 下单 20019**、**改密后旧令牌失效**（用完恢复口令） |
| `07-customer.spec.ts` | 3 | 客户列表、**客户详情内嵌该客户订单且不再返回购买历史**、改密页前端校验 |
| `08-cross-end.spec.ts` | 9 | **买卖联调**：加购结算→卖家可见、推进状态同步、完成扣库存、发货前买家取消、发货后不可取消、有未完成订单下架被拒/取消后可下架、下单不锁库存、买家订单列表筛选与倒序、**买家停留详情时卖家发货→取消被 60003 拦** |


### 数据策略

- 所有账号/商品名带 `RUN_ID` 后缀（时间戳+随机），同一台库反复跑不撞名；
- **造数走接口、被测流程走 UI**；
- 用例之间不依赖上一次运行的残留；`06`（改密）与 `07`（客户订单）内部有状态依赖，用 `describe.serial` 固定顺序。

---

## 四、已知现象（不是用例写错）

| 现象 | 说明 |
| --- | --- |
| 改密用例会真改卖家口令 | T06-04 用 `try/finally` 保证恢复；若进程被强杀，需手动把口令改回 `Abcd@1234` |
| **`VIDEO_INVALID(20025)` 实际不可达** | 单文件上限（`max-file-size=20MB`）与视频业务上限（20MB）相同，>20MB 的视频在容器层就被拦成 `MEDIA_INVALID(20026)`，永远走不到业务层的 20025。T05-13 按实际行为断言 |
| 列表筛选项与商品卡没有稳定类名 | 卖家端换 daisyUI 后靠 `aria-label`（`按状态与下架原因筛选`、`搜索商品名称`）、`data-testid`（`status-tag`、`toast-item`）与 `role=link + 名称` 定位；改版时优先保住这几个钩子 |
| 买家端选择器已统一 testid | 阶段F 后买家靠 `data-testid`（`goods-card`/`cart-row`/`cart-qty`/`cart-check`/`cart-total`/`cart-checkout`/`fav-card`/`order-row`/`order-items`/`order-total`/`status-tag`/`notice`/`warn-bar`/`empty-state`）与 `role`/`aria-label`（`按状态筛选`、`一级类别`、`二级类别`）定位；不要再写 `.row-item`/`.total strong` 等旧类名 |

---

## 五、目录

```
simple-shop-test-e2e/
├─ .env.example             # 本地配置模板（复制成 .env；.env 本身 git-ignore）
├─ playwright.config.ts     # workers:1 / Edge / 失败留 trace
├─ helpers/
│  ├─ config.ts             # 三个地址 + 账号（启动时读 .env）
│  ├─ api.ts                # 接口封装（造数 + 负向调用）
│  ├─ data.ts               # 唯一后缀 + 商品/买家工厂
│  └─ ui.ts                 # 登录、确认弹窗、卡片/状态/提示定位
├─ tests/                   # 10 个 spec（90 条，阶段三口径，待按阶段四清单改造）
├─ scripts/
│  ├─ db.js                 # MySQL 访问层（仅重建库 + 手工联调夹具；测试不连库）
│  ├─ reset-e2e-db.js       # 重建独立 E2E 库（跨平台，npm run db:reset）
│  └─ reset-e2e-db.bat      # Windows 薄壳，内部调用上面的 .js
├─ simple-shop-d1-fixtures.sql  # 手工联调夹具造数（幂等；非测试依赖）
├─ archive/                 # 历史用例（不参与执行）
│  ├─ 目录.md               # 两代归档的说明与「阶段二 → 阶段三」对应关系
│  ├─ 阶段一/               # 36 条 + 当年的一键脚本
│  └─ 阶段二/               # 57 条原件（改造前快照）
├─ report/                  # HTML + JSON 报告（git-ignore）
└─ evidence/                # 失败截图与 trace（git-ignore）
```

---

## 六、未覆盖（如实标注）

- **阶段四的全部新增能力**（地址簿、结算选地址、四渠道支付与失败重试、确认收货与缺货拒绝、售后申请/列表/详情、凭证图上传、物流多行单号）：现有套件零覆盖，用例已设计在[阶段四 E2E 用例清单](../simple-shop-doc/阶段四/阶段四-E2E用例清单.md)，等改造落地。
- **导航栏购物车角标**：功能未实现。
- **「在售但库存 0」的购物车行**：阶段三库存归零即自动下架，该状态不可达，只测了「已下架行不可选」；
- 媒体上传的 **UI 流程**（接口层的图片/视频各类边界已覆盖，但没走完整发布链路的界面操作）；
- 浏览器返回/多标签页并发（刷新保持已由 02b-14 / 02b-17 覆盖，返回与多标签未覆盖）；
- 移动端适配（本轮不做，仅基础响应式）。

---

## 七、性能测量（`npm run perf`）

E2E 工程里带了一个首屏测量脚本，量的是**构建产物**（`npm run preview`），不是 dev server：

```bash
# 前置：后端 8080；卖家端 npm run build && npm run preview（预览已配 /api 代理）
npm run perf -- --url=http://localhost:4173/login --name=卖家登录
npm run perf -- --url=http://localhost:4173/ --auth=seller --name=卖家首页
npm run perf -- --url=... --runs=7 --settle=1500 --budget-lcp=2.5   # 当门禁用
```

默认条件：**CPU 4× 降速 + Slow 4G（150ms RTT / 1.6Mbps）· 冷缓存 · 5 次**，
输出每次的 TTFB/FCP/LCP/CLS/TBT/请求数/传输量，再给中位数、p75、极差与 **LCP 元素**。
`--auth=seller` 会先调接口拿令牌写进 localStorage，用来测登录后的页面。

不能使用DevTool手动测试，读数不准。

买家端暂未适配，如需测量买家端，先改代码适配（`scripts/perf.mjs`）。

如果某次运行 FCP/LCP 为 0，脚本会提示：多半是**首屏被整页淡入/白屏包住**（合成器动画不产生 paint，
Chromium 就不报 FCP），可以用 `--motion=reduce` 复测确认。卖家端就踩过这个坑，已在 `main.ts`
改成 `router.isReady()` 后再挂载，详见卖家端 README §7.1。

### 与 Lighthouse 的关系

- 本脚本是「真实加载 + 应用限速」，测的是**这台机器上这个构建**的实际表现，适合改完代码立刻对比；
- Lighthouse 的 `--throttling-method=simulate` 用 Lantern 模拟，跨机器可比性更好，适合对外汇报；
  想跑就 `npx lighthouse http://localhost:4173/login --preset=desktop --view`（不装依赖，临时下载）。
- 两者都只能代表实验室数据；有真实用户后应该上 `web-vitals` 上报，看字段数据（p75）。
