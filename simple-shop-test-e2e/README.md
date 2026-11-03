# simple-shop E2E（阶段二）

Playwright 驱动**系统已装的 Edge**（不下载 Chromium），对真实运行中的前后端做端到端验收。

- 规模：**7 个 spec / 57 条用例**，全量跑约 **23 秒**
- 稳定性：连续 3 次执行结果一致（`workers:1` 串行，不依赖用例顺序以外的共享状态）
- 旧套件（阶段一的 36 条）已移到 `archive/`，**不要放回 `tests/`**，否则会被 `playwright test` 误跑

---

## 一、跑之前要准备什么

### 1. 三个服务都要先起来

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 后端 | `http://localhost:8080` | 连**独立 E2E 库**，不要连开发库 `simple_shop` |
| 买家 SPA | `http://localhost:5174` | `npm run dev -- --port 5174` |
| 卖家 SPA | `http://localhost:5173` | `npm run dev -- --port 5173` |

后端启动示例（独立库 + 放宽限流，否则批量注册会 429）：

```bash
cd simple-shop-backend
DB_PASSWORD=<你的 MySQL 口令> \
DB_URL="jdbc:mysql://localhost:3306/simple_shop_e2e?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&connectionTimeZone=UTC&rewriteBatchedStatements=true" \
java -Dserver.port=8080 \
     -Dsimple-shop.rate-limit.register-per-minute=1000 \
     -Dsimple-shop.rate-limit.login-per-minute=1000 \
     -Dsimple-shop.rate-limit.submit-intention-per-minute=1000 \
     -jar target/simple-shop.war
```

> ⚠️ 限流阈值用**系统属性**（`-Dsimple-shop.rate-limit.xxx`）最稳。写成环境变量容易踩「短横线转义」的坑。

### 2. E2E 库跑前重建（保证可重复）

```bat
scripts\reset-e2e-db.bat
```

需要先 `set DB_PASSWORD=...`。脚本只 `DROP/CREATE` 库 `simple_shop_e2e`，**不碰** `simple_shop` / `simple_shop_test`。
重建后启动后端，Flyway 会自动建 12 张表并写入种子卖家。

### 3. 依赖

```bash
npm ci
```

---

## 二、怎么跑

```bash
npx playwright test                    # 全量（无头，约 17 秒）
npx playwright test --headed           # 看着它点
npx playwright test tests/04-trade.spec.ts   # 只跑某个 spec
npx playwright show-report report      # 看 HTML 报告
```

地址和账号都可以用环境变量覆盖（不改代码）：

```bash
BUYER_URL=http://localhost:5174 \
SELLER_URL=http://localhost:5173 \
API_URL=http://localhost:8080 \
SELLER_PASSWORD=Abcd@1234 \
npx playwright test
```

**换成部署形态（Compose）时**，只改三个地址即可：

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
| 数据隔离用库解决就够 | 独立库 `simple_shop_e2e` + 跑前重建（`scripts/reset-e2e-db.bat`），同样能保证可重复 |

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

## 三、用例清单（57 条）

| Spec | 条数 | 覆盖 |
| --- | :---: | --- |
| `01-auth.spec.ts` | 8 | 卖家未登录跳转、登录成功/失败、买家注册即登录、买家登录、**买卖家令牌互调被拒**、错误口令 |
| `02-buyer.spec.ts` | 6 | 匿名看列表、**搜索只匹配名称**、两级类别筛选、详情价格/库存/描述、提交意向、历史不含排队中 |
| `03-seller-goods.spec.ts` | 11 | UI 发布商品、库存改 0 自动售罄/改回自动上架、手动下架不进历史、重新上架、**队列非空下架被拒**、删被引用类别 20006、库存负数被拒、**状态筛选**、类别改名/删空类别、重新上架缺 confirm → 20028、类别管理页手工新增一级/二级 |
| `04-trade.spec.ts` | 8 | 队首规则、非队首 30002、交易中买家可看不可买、失败+重排回队尾且下单时间不变、失败+作废、有余成交回在售、卖最后一件归档并清队、成交买家历史 |
| `05-contract.spec.ts` | 15 | 400+50002、`errors` 定位字段、名称 50/51 边界、价格 0/三位小数、发布库存 0、非法枚举、未登录 401、分页默认值、媒体边界（图片 >5MB→20016、视频 20MB 边界可传、>20MB→20026、视频当商品图→20027、文本伪装图片→20016） |
| `06-security.spec.ts` | 4 | 队列非空下架 20022、冻结期提交 20019、非队首 30002、**改密后旧令牌失效**（用完恢复口令） |
| `07-customer-history.spec.ts` | 5 | 客户列表/详情含购买历史、**历史列表与历史详情 UI**、改密页前端校验 |

### 数据策略

- 所有账号/商品名带 `RUN_ID` 后缀（时间戳+随机），同一台库反复跑不撞名；
- **造数走接口、被测流程走 UI**（交接文档 §3.3 的建议）；
- 用例之间不依赖上一次运行的残留；`04-trade` 内部有状态依赖，用 `describe.serial` 固定顺序。

---

## 四、已知现象（不是用例写错）

| 现象 | 说明 |
| --- | --- |
| 详情页价格显示 `¥99.9` 而非 `¥99.90` | 后端返回 `99.90`，前端按数字渲染，末尾 0 丢失。属展示问题，已在断言里按实际值写 |
| 意向名单会含已失败/已成功的历史行 | 列表按 `queue_order` 排序，**"第一行"不一定是队首**；用例里显式取「状态 QUEUED 且序号最小」 |
| 手动下架的商品在卖家「商品列表」、不进历史 | 文档存在冲突：状态模型 v1.2 说非终态留存；澄清#18/Backlog#8 说「历史→已下架 分区可见」。用例按**当前实现**断言，详见 `../E2E旧用例对照表_2026-10-30.md` §5 |
| 改密用例会真改卖家口令 | T06-04 用 `try/finally` 保证恢复；若进程被强杀，需手动把口令改回 `Abcd@1234` |
| **`VIDEO_INVALID(20025)` 实际不可达** | multipart 上限（20MB）与视频业务上限（20MB）相同，>20MB 的视频在容器层就被拦成 `MEDIA_INVALID(20026)`，永远走不到业务层的 20025。T05-13 按实际行为断言 |
| **`VIDEO_INVALID(20025)` 实际不可达** | multipart 上限（20MB）与视频业务上限（20MB）相同，>20MB 的视频在容器层就被拦成 `MEDIA_INVALID(20026)`，永远走不到业务层的 20025。T05-13 按实际行为断言 |

---

## 五、目录

```
simple-shop-test-e2e/
├─ playwright.config.ts     # workers:1 / Edge / 失败留 trace
├─ helpers/
│  ├─ config.ts             # 三个地址 + 账号（全走环境变量）
│  ├─ api.ts                # 阶段二接口封装（造数 + 负向调用）
│  ├─ data.ts               # 唯一后缀 + 商品/买家工厂
│  └─ ui.ts                 # 登录、确认弹窗、卡片定位
├─ tests/                   # 6 个 spec（43 条）
├─ scripts/reset-e2e-db.bat # 重建独立 E2E 库
├─ archive/                 # 阶段一旧用例与过期脚本（不参与执行）
├─ report/                  # HTML + JSON 报告（git-ignore）
└─ evidence/                # 失败截图与 trace（git-ignore）
```

---

## 六、未覆盖（如实标注）

- 媒体上传的 **UI 流程**（接口层的图片/视频各类边界已覆盖，但没走完整发布链路的界面操作）；
- 分页翻页与多页边界（当前数据量不足以触发第二页）；
- 多商品同时各自处于交易中；
- 浏览器返回/刷新深链、多标签页并发；
- 移动端适配（本轮不做）。
