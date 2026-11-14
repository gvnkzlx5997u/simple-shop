# simple-shop E2E

> **当前状态：阶段三用例已完成。** 阶段二 57 条逐条判定去留（保留 / 改写 / 删除）后留 42 条，
> 再按 Backlog 新增 34 条（订单生命周期、跨端联调、买家购物车与收藏），`tests/` 共 **76 条**。

Playwright 驱动**系统已装的 Edge**（不下载 Chromium），对真实运行中的前后端做端到端验收。

- 规模：**9 个 spec / 76 条用例**，全量跑约 **1.4 分钟**（含建 60+ 个商品/账号的造数）
- 稳定性：`workers:1` 串行；空库重建后两批各跑通（76/76）
- 历史套件都在 `archive/`（`阶段一/` 36 条、`阶段二/` 57 条原件），**不要放回 `tests/`**，否则会被 `playwright test` 误跑；对应关系见 `archive/目录.md`

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
| `E2E_FIXTURE_DB_NAME` | 买家端对账脚本（d3/d5）的**夹具库**，与主库分开 | `simple_shop_d1` |
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
$env:RATE_LIMIT_ENABLED="false";$env.DB_PASSWORD=<你的 MySQL 口令>;$env.DB_URL="jdbc:mysql://localhost:3306/simple_shop_e2e?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&connectionTimeZone=UTC&rewriteBatchedStatements=true";java -jar .\target\simple-shop.war
```


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

| 命令行（旧） | node 直连（现在） |
| --- | --- |
| 依赖 `mysql.exe` 的安装路径 / PATH，本机只装了 MySQL Shell 就跑不起来 | 只依赖 npm 依赖 `mysql2`，`npm ci` 即可 |
| SQL 要过 `cmd.exe`：多行被拆成多条只执行第一段，引号要自己转义，中文要加 `--default-character-set=utf8mb4` | 参数化查询直接传，无 shell 解析、无字符集参数 |
| 失败只能从 stderr 文本猜，退出码混着告警 | 抛带 `code` 的异常，消息里直接给出「连的哪个库 + 哪句 SQL + 可能原因」 |
| 只支持 Windows | Windows / macOS / Linux / WSL 一致 |

**边界**：只有「重建库」和「对账脚本（d3/d5）」用数据库连接；Playwright 用例本身仍然**只走接口与 UI**——
造数走接口、断言看接口返回，避免用例跟着表结构漂移。

### 3. 依赖

```bash
npm ci
```

---

## 二、怎么跑

```bash
npm run clean                                # 清掉上一轮的 report/ evidence/ test-results/（只删生成物）
npm test                                     # 全量（6 个 spec 42 条；含新增共 9 个 spec 76 条，约 2 分钟）
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

## 二·五、关于容器化（本轮不做，理由如下）

**结论：E2E 不做容器化，用「本地三服务 + 独立库」跑。**

| 原因 | 说明 |
| --- | --- |
| 开发机没有 Docker | 容器化后这套用例在开发机上跑不起来，而 E2E 的价值恰恰是"改完立刻能跑" |
| 调试成本 | 每轮要先构建 backend/frontend 镜像、等 mysql healthy，一轮从 20 秒变成几分钟 |
| 数据隔离用库解决就够 | 独立库 `simple_shop_e2e` + 跑前重建（`npm run db:reset`），同样能保证可重复 |

**代价（必须知道）**：本地三端口形态**不等于部署形态**。部署时是 nginx 单容器同时托管买家 `/`、卖家 `/seller/`，并反代 `/api`、`/media`。这条链路本轮 E2E 没有覆盖到。

**补救**：用例里没有任何硬编码地址，切部署形态只改环境变量、不改一行代码：

```bash
BUYER_URL=http://localhost        \
SELLER_URL=http://localhost/seller \
API_URL=http://localhost          \
npx playwright test
```

建议在「部署收口」时，于 Compose 环境跑一次同一套件，作为部署形态的证据（这一步依赖有 Docker 的机器）。

---

## 三、用例清单（42 条）

| Spec | 条数 | 覆盖 |
| --- | :---: | --- |
| `01-auth.spec.ts` | 8 | 卖家未登录跳转、登录成功/失败、买家注册即登录、买家登录、**买卖家令牌互调被拒**、错误口令 |
| `02-buyer.spec.ts` | 4 | 匿名看列表、**搜索只匹配名称**、两级类别筛选、详情价格（两位小数）/库存/描述 |
| `03-seller-goods.spec.ts` | 9 | UI 发布商品、库存改 0 自动下架（库存耗尽）/改回自动上架、手动下架终态（库存只读、无上架入口、可按原因筛出）、**有未完成订单下架被拒 20020**、删被引用类别 20006、库存负数 400+50002、**状态+原因筛选**、类别改名/删空类别、类别管理页手工新增一级/二级 |
| `05-contract.spec.ts` | 15 | 400+50002、`errors` 定位字段、名称 50/51 边界、价格 0/三位小数、发布库存 0、**非法查询参数（订单状态、无 status 的原因）**、未登录 401、分页默认值、媒体边界（图片 >5MB→20016、视频 20MB 边界可传、>20MB→20026、视频当商品图→20027、文本伪装图片→20016） |
| `06-security.spec.ts` | 3 | **有未完成订单时绕过前端下架/清零 20020**、**已下架商品加购 70001 + 下单 20019**、**改密后旧令牌失效**（用完恢复口令） |
| `07-customer.spec.ts` | 3 | 客户列表、**客户详情内嵌该客户订单且不再返回购买历史**、改密页前端校验 |
| `02b-buyer-cart.spec.ts` | 10 | 未登录拦截、重复加购仍一行、改数量/夹库存/合计、移除、**数量与勾选不落库**、失效行置灰不可结算、结算生成一笔两明细、收藏与取消、收藏失效不可进详情、转收藏 |
| `04-order-lifecycle.spec.ts` | 14 | 七 tab（含已取消）、状态进 URL、行→详情、头+明细合计、只有一个推进按钮、四步推进、完成扣库存/扣到 0 自动下架、**完成缺货 60004**、卖家取消（含已发货）、终态无动作/60003、跳级 60002、重复取消幂等、空态、商品内嵌订单 |
| `08-cross-end.spec.ts` | 8 | **买卖联调**：加购结算→卖家可见、推进状态同步、完成扣库存、发货前买家取消、发货后不可取消、有未完成订单下架被拒/取消后可下架、下单不锁库存、买家订单列表筛选与倒序 |

> 阶段二的 `04-trade.spec.ts`（8 条）**整组删除**：意向队列、交易裁决、冻结、归档在阶段三都不存在。
> 下一轮补新增用例：订单生命周期（04）、跨端联调（08）、买家购物车/收藏、商品两态补充，清单见
> `simple-shop-doc/阶段三/阶段E-E2E用例清单.md`。

### 数据策略

- 所有账号/商品名带 `RUN_ID` 后缀（时间戳+随机），同一台库反复跑不撞名；
- **造数走接口、被测流程走 UI**；
- 用例之间不依赖上一次运行的残留；`06`（改密）与 `07`（客户订单）内部有状态依赖，用 `describe.serial` 固定顺序。

---

## 四、已知现象（不是用例写错）

| 现象 | 说明 |
| --- | --- |
| ~~详情页价格显示 `¥99.9`~~ | 买家端已统一 `toFixed(2)`，现在显示 `¥99.90`；阶段三用例按两位小数断言 |
| 改密用例会真改卖家口令 | T06-04 用 `try/finally` 保证恢复；若进程被强杀，需手动把口令改回 `Abcd@1234` |
| **`VIDEO_INVALID(20025)` 实际不可达** | 单文件上限（`max-file-size=20MB`）与视频业务上限（20MB）相同，>20MB 的视频在容器层就被拦成 `MEDIA_INVALID(20026)`，永远走不到业务层的 20025。T05-13 按实际行为断言 |
| 列表筛选项与商品卡没有稳定类名 | 卖家端换 daisyUI 后靠 `aria-label`（`按状态与下架原因筛选`、`搜索商品名称`）、`data-testid`（`status-tag`、`toast-item`）与 `role=link + 名称` 定位；改版时优先保住这几个钩子 |

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
├─ tests/                   # 9 个 spec（76 条，阶段三口径）
├─ scripts/
│  ├─ db.js                 # MySQL 访问层（node 直连，mysql2；替代 mysql 命令行）
│  ├─ reset-e2e-db.js       # 重建独立 E2E 库（跨平台，npm run db:reset）
│  └─ reset-e2e-db.bat      # Windows 薄壳，内部调用上面的 .js
├─ d3-cart-check.js         # 购物车「页面金额 = 库内金额」对账脚本（12 项）
├─ d5-full-regression.js    # 阶段D 买家端全量回归脚本（44 项）
├─ archive/                 # 历史用例（不参与执行）
│  ├─ 目录.md               # 两代归档的说明与「阶段二 → 阶段三」对应关系
│  ├─ 阶段一/               # 36 条 + 当年的一键脚本
│  └─ 阶段二/               # 57 条原件（改造前快照）
├─ report/                  # HTML + JSON 报告（git-ignore）
└─ evidence/                # 失败截图与 trace（git-ignore）
```

---

## 六、未覆盖（如实标注）

- **导航栏购物车角标**：买家端未实现（Backlog #10 有、前端没有），未写用例；
- **「在售但库存 0」的购物车行**：阶段三库存归零即自动下架，该状态不可达，只测了「已下架行不可选」；
- 媒体上传的 **UI 流程**（接口层的图片/视频各类边界已覆盖，但没走完整发布链路的界面操作）；
- 分页翻页与多页边界（当前数据量不足以触发第二页）；
- 浏览器返回/刷新深链、多标签页并发；
- 移动端适配（本轮不做）。
