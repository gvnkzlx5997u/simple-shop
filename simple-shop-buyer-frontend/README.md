# simple-shop-buyer-frontend

在线购物系统 · 买家端前端（前后端分离）。

对应后端：`simple-shop-backend`（Spring Boot，REST + 媒体文件，不输出 HTML）。
接口契约以 `simple-shop-doc/阶段三/openapi.json` 与后端 Swagger（`/swagger-ui/index.html`）为准。

> **当前状态：阶段D 已完成。** 功能域为商品浏览、购物车、收藏、订单、账号五块；
> 阶段二的「购买意向」与「我的历史」已整体退场（D1），代码与路由里不再存在。

## 技术栈

Vue 3 + Vite + TypeScript + Pinia + Vue Router + Axios。
不引入 UI 组件库，样式沿用 `assets/base.css` 的原生 CSS 约定。

## 功能域

| 功能域 | 能力 | 是否需要登录 |
| --- | --- | --- |
| 商品浏览 | 列表（12 条/页）、关键词搜索、按类别筛选、详情 | 否 |
| 账号 | 注册、登录、退出；登录后顶栏显示用户名 | 否（注册/登录本身） |
| 购物车 | 加购、改数量、勾选/全选、移除、失效行、合计、结算下单、转收藏 | 是 |
| 收藏 | 详情页收藏/取消、收藏列表（12 条/页）、失效标注、单条取消 | 是 |
| 订单 | 列表（10 条/页、状态下拉筛选）、详情（订单头 + 明细）、取消 | 是 |

## 路由

| 路由 | 名称 | 页面 | 登录 |
| --- | --- | --- | --- |
| `/` | — | 重定向到 `/goods` | — |
| `/goods` | `goods-list` | 商品列表 / 搜索 | 否 |
| `/goods/:id` | `goods-detail` | 商品详情（加购、收藏入口） | 否 |
| `/cart` | `cart` | 购物车 | 是 |
| `/favorites` | `favorites` | 我的收藏 | 是 |
| `/orders` | `orders` | 我的订单 | 是 |
| `/orders/:id` | `order-detail` | 订单详情 | 是 |
| `/login` | `login` | 登录 | 否 |
| `/register` | `register` | 注册 | 否 |
| `/:pathMatch(.*)*` | — | 兜底重定向到 `/goods` | — |

需要登录的页面在 `router/index.ts` 里用 `meta.requiresAuth` 标记，守卫统一跳 `/login?redirect=<原路径>`。
阶段二的 `/history` 已删除，旧书签会落到兜底规则回到商品列表。

## 接口

| 用途 | 方法 | 路径 |
| --- | --- | --- |
| 商品列表 | GET | `/api/public/goods`（`keyword`、`category_id`、`page`、`page_size`） |
| 类别树 | GET | `/api/public/categories` |
| 商品详情 | GET | `/api/public/goods/{id}` |
| 注册 | POST | `/api/buyer/auth/register` |
| 登录 / 退出 | POST / DELETE | `/api/buyer/auth/session` |
| 购物车列表 / 加购 | GET / POST | `/api/buyer/cart` |
| 移除购物车行 | DELETE | `/api/buyer/cart/{goodsId}` |
| 转收藏 | POST | `/api/buyer/cart/move-to-favorites` |
| 收藏列表 / 收藏 | GET / POST | `/api/buyer/favorites` |
| 收藏状态 / 取消 | GET / DELETE | `/api/buyer/favorites/{goodsId}` |
| 订单列表 / 下单 | GET / POST | `/api/buyer/orders` |
| 订单详情 | GET | `/api/buyer/orders/{id}` |
| 订单明细 | GET | `/api/buyer/orders/{id}/items` |
| 取消订单 | POST | `/api/buyer/orders/{id}/cancel` |

## 本地存储约定

后端购物车表只有「买家 + 商品 + 加购时间」，**没有数量列也没有勾选列**（可用
`DESCRIBE simpleshop_cart_item` 核实）。所以数量与勾选只存在浏览器里，按买家隔离。

| 键 | 内容 | 写入时机 | 清除时机 |
| --- | --- | --- | --- |
| `simple-shop.buyer.token` | 会话令牌 | 登录/注册成功 | 退出登录、收到 401 / 业务码 10002 |
| `simple-shop.buyer.expire-at` | 令牌到期时间 | 同上 | 同上 |
| `simple-shop.buyer.username` | 用户名（用于隔离购物车本地状态、顶栏展示） | 同上 | 同上 |
| `simple-shop.buyer.cart.<username>` | `{ "<goods_id>": { "quantity": 2, "checked": true } }` | 改数量、勾选、加购后 | 退出登录；或该用户名下购物车已空 |

约定细节：

- **按用户名分键**，换账号不串数据；退出登录只清当前用户名那一份。
- **读坏数据当空**：JSON 解析失败、结构不对、数量小于 1 一律回落成默认值，不让脏数据把购物车页拖垮。
- **数量上限不在本地夹**：本地不知道库存，上限由页面按接口返回的 `stock` 再夹一次；
  本地只保证下限 1。
- **孤儿记录自动清**：本地有、服务端没有的 `goods_id`，在每次拉购物车时丢弃
  （`stores/cart.ts` 的 `syncLocal`）；服务端有、本地没有的补 `{ quantity: 1, checked: true }`。
- **不可买强制取消勾选**：服务端 `selectable === false` 或库存为 0 的行，勾选一律置 false，
  不允许把失效商品带进下单。

## 错误码映射

界面**不展示后端 `message` 原文**，一律按业务码映射成中文（`stores/auth.ts` 的 `describeError`）。
下表左侧为后端 `ErrorCode` 的全量清单，右侧「买家端文案」为空表示该码只在卖家端出现、买家端映射里不写。

| 码 | 后端枚举 | 买家端文案 | 买家端是否会遇到 |
| --- | --- | --- | --- |
| 10001 | `LOGIN_FAILED` | 用户名或密码不正确 | 是 |
| 10002 | `SESSION_INVALID` | 登录已失效，请重新登录（并跳登录） | 是 |
| 10003 | `OLD_PASSWORD_MISMATCH` | — | 否（买家端无改密码入口） |
| 10004 | `PASSWORD_INVALID` | 密码不符合要求：至少 8 位 | 是 |
| 10005 | `USERNAME_TAKEN` | 这个用户名已经被注册了，换一个试试 | 是 |
| 10006 | `USERNAME_INVALID` | 用户名不符合要求：不能为空，不能含空格或尖括号，最长 50 字 | 是 |
| 10007 | `PHONE_INVALID` | 电话格式不对：请填 7~20 位数字，可含短横 | 是 |
| 10008 | `DEFAULT_LOCATION_INVALID` | 默认交易地点不符合要求：最多 50 字，不能含换行 | 是 |
| 10009 | `ACCOUNT_NOT_FOUND` | 账号不存在，请重新登录 | **否**（`requireAccount` 当前无调用方，见下） |
| 10010 | `ACCOUNT_INVALID` | 账号信息不符合要求 | **否**（只在 `AccountRules.account()` 里抛，该入口买家端不走） |
| 20001–20006 | 类别相关 6 个 | — | 否（类别写操作只在卖家端） |
| 20011 | `GOODS_NOT_FOUND` | 商品不存在或已下架 | 是（详情 404 走空态，不报错） |
| 20012–20014 | 商品名称/描述/价格 | — | 否（卖家端建商品） |
| 20015 | `STOCK_INVALID` | 库存不足，请回购物车调整数量 | 是 |
| 20016–20018 | 图片相关 | — | 否（卖家端） |
| 20019 | `GOODS_NOT_ON_SALE` | 有商品已下架，请回购物车处理 | 是 |
| 20020 / 20023 | 商品被未完成订单占用 / 状态不允许 | — | 否（卖家端） |
| 20025–20027 | 媒体相关 | — | 否（卖家端） |
| 50000 | `INTERNAL_ERROR` | 服务器出错了，请稍后再试 | 是 |
| 50001 | `TOO_MANY_REQUESTS` | 操作太频繁了，请稍后再试 | 是 |
| 50002 | `PARAM_INVALID` | 填写的内容不符合要求 | 是（下单场景有专属文案，见下） |
| 50003–50005 | 资源不存在 / 方法不允许 / 媒体类型 | — | 仅异常请求（路径写错、方法用错）；正常操作不会出现 |
| 60001 | `ORDER_NOT_FOUND` | 订单不存在 | 是（含他人订单，详情走空态） |
| 60002 | `ORDER_STATUS_INVALID` | 订单状态已变化，请刷新后重试 | **否**（只在卖家改单时抛，买家端不可达） |
| 60003 | `ORDER_NOT_CANCELABLE` | 已发货，不能取消 | 是 |
| 60004 | `ORDER_STOCK_SHORTAGE` | 卖家库存不足，订单未完成 | **否**（只在卖家确认/发货时抛；买家下单缺货走 20015） |
| 70001 | `CART_GOODS_NOT_SELECTABLE` | 商品已下架，无法加入购物车 | 是 |
| 70002 | `FAVORITE_GOODS_NOT_SELECTABLE` | 商品已下架，无法收藏 | 是 |
| 70003 | `CART_TO_FAVORITE_REJECTED` | 有商品已下架，未转收藏 | 是 |

**数量对账**：后端共 44 个码；买家端业务链路实际可达 **18 个**（上表标「是」的）；
映射里另有 **4 个预防性文案**（`10009`、`10010`、`60002`、`60004`），当前不可达，理由见下。

两处需要留意的行为：

- **50002 在下单场景有专属文案**「下单信息有误，请回购物车重新勾选后再提交」。
  全局文案「填写的内容不符合要求」对下单场景没有意义（那里没有表单可填），
  所以在 `views/Cart.vue` 的 `submitFailText` 里单独改写。这是**有意的偏离**，
  同一码在购物车页与其他页文案不同。
- **`10006` 只管字符规则，长度超限走 `50002`**：注册/登录的入参先用 Bean Validation 校验
  （`@CodePointLength(max = 50)`），超过 50 字直接回 `50002`（HTTP 400），根本到不了 `AccountRules`；
  `10006` 负责的是空值、空白字符、控制字符、尖括号、换行这几类。
  实测：60 字用户名 → `50002`；带空格的用户名 → `10006`。
- **404 兜底不再硬说「商品」**。业务码优先匹配；走到兜底说明是未登记的码，
  统一回「内容不存在或已被移除」。原本这里写死「商品不存在或已下架」，
  一旦后端新增一个 404 类错误（如 `10009`）就会被错说成商品，属于埋着的坑。
- **`10009` / `10010` / `60002` / `60004` 的映射是预防性的，当前链路不可达**：
  `BuyerAuthServiceImpl.requireAccount()` 已定义但**没有调用方**（注册/登录走 `AccountRules.username()`，抛 `10006`）；
  `AccountRules.account()`（抛 `10010`）同样无人调用；
  `60002` 与 `60004` 只在 `SellerOrderServiceImpl` 里抛，买家端碰不到（买家下单缺货走 `20015`）。
  这四条先补上，是为了后端将来接入时不会落到兜底文案。
  这几条是**读代码得出的结论，不是运行验证**——后端若改了调用关系，需要回头实测。

## 目录结构

```
src/
├── api/
│   ├── types.ts        接口契约类型（字段保持后端 snake_case）与业务码常量
│   ├── token.ts        令牌与到期时间的本地持久化
│   ├── http.ts         axios 封装：响应解包、ApiError、401 统一跳登录
│   ├── buyer.ts        商品 / 类别 / 账号接口
│   ├── cart.ts         购物车接口（加购、列表、移除、转收藏）
│   ├── favorite.ts     收藏接口（收藏、列表、状态、取消）
│   └── order.ts        订单接口（下单、列表、详情、明细、取消）
├── stores/
│   ├── auth.ts         登录态 + 业务码 → 中文文案（describeError）
│   └── cart.ts         服务端购物车行 + 本地数量勾选 → 展示行；下单、转收藏
├── utils/
│   ├── cartStorage.ts  购物车本地状态的读写（按用户名隔离）
│   ├── format.ts       UTC 时间转本地展示、金额两位小数
│   └── orderStatus.ts  订单六态中文名、买家可取消判断、筛选项
├── router/index.ts     路由与登录守卫
├── views/              GoodsList / GoodsDetail / Cart / Favorites / Orders / OrderDetail / Login / Register
├── App.vue             顶栏（商品 / 购物车 / 收藏 / 订单 / 登录退出）
├── assets/base.css     全局基础样式
└── main.ts             入口；注册 401 统一跳登录
```

## 本地开发

### 1. 先启动后端

```bash
cd ../simple-shop-backend
mvn spring-boot:run
```

需要本机 MySQL。连接信息见后端 `application.yml`（`DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
均可用环境变量覆盖）。

> ⚠️ 如果本机 `simple_shop` 库是早期版本建的，Flyway 会因校验和不匹配启动失败
> （`Validate failed: checksum mismatch`）。**不要用关闭校验的方式硬绕**，而是新建一个干净库
> 并用 `DB_URL` 指过去，让 Flyway 从 V1 重建：
>
> ```bash
> mysql -uroot -p -e "CREATE DATABASE simple_shop_dev DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci"
> DB_URL="jdbc:mysql://127.0.0.1:3306/simple_shop_dev?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&connectionTimeZone=UTC&rewriteBatchedStatements=true" \
>   mvn spring-boot:run
> ```

后端起来后：

- 接口文档：<http://localhost:8080/swagger-ui/index.html>
- 健康检查：<http://localhost:8080/api/public/health>

### 2. 启动前端

```bash
npm install      # 首次
npm run dev      # http://localhost:5174
```

`vite.config.ts` 已把 `/api` 与 `/media` 代理到 `http://localhost:8080`，开发期同源、无 CORS。
换后端地址用 `VITE_API_BASE_URL=http://其它地址:8080/api npm run dev`。

### 3. 构建与类型检查

```bash
npm run build        # 含 vue-tsc 类型检查，输出到 dist/
npm run type-check   # 只做类型检查
```

## 实现约定

- **后端响应统一为 `{ code, message, data }`**，`code = 0` 为成功。业务拒绝多数仍走 HTTP 200；
  会话失效 401、参数错误 400、资源不存在 404、服务端错误 500 用 HTTP 状态码。
- **401 统一跳登录**：`http.ts` 的 `toApiError()` 把 401 转成 `SESSION_INVALID` 后走 `fail()`，
  由 `main.ts` 注册的回调清令牌并跳 `/login?redirect=<当前页>`。任何调用方都不用自己处理。
  ⚠️ 新增错误分支时不要绕开 `fail()`——绕开就不跳登录了。
- **不展示后端 `message` 原文**，一律走 `describeError`。
- **字段名保持 snake_case**（`goods_id`、`page_size`、`create_at`），不做驼峰转换。
- **业务规则尽量不在前端复制**：可否购买看后端的 `purchasable`；购物车行可用看
  `selectable` 且 `stock > 0`（库存判断是因为接口的 `selectable` 只表示「在售」）。
  ⚠️ 唯一的例外是**订单可否取消**：后端 `OrderData` 没有返回 `can_cancel` 这类标记，
  前端只能按 `status` 落在前三态来判断（`utils/orderStatus.ts` 的 `canCancelByBuyer`），
  规则与后端 `Order.canCancelByBuyer()` 保持一致。后端若调整可取消范围，这里要同步改。
  见「已知限制与已知差异」。
- **金额只用后端值**：明细小计与订单总额都直接展示后端字段，前端不重算，
  只有购物车合计是前端按单价 × 数量累加（那时还没有订单）。
- **时间以 UTC 存、按浏览器本地时区展示**，统一走 `utils/format.ts` 的 `formatTime`。

## 已知限制与已知差异

- **订单可否取消由前端按状态判断，接口未返回该标记**。文档 §9.2 要求「可取消状态以接口返回为准」，
  但 `OrderData` 只有 `status`，没有 `can_cancel`。前端只能在 `utils/orderStatus.ts` 里
  维护一份「前三态可取消」的规则，与后端 `Order.canCancelByBuyer()` 对齐。
  这违反了质量门禁的字面要求，**登记为已知差异**；建议后端后续在订单响应里补一个布尔字段
  （如 `can_cancel`），前端即可删掉这份判断。
- **订单状态筛选只支持单状态**。后端 `GET /buyer/orders?status=` 的取值走 `OrderStatus.valueOf()`，
  只认 `PENDING_CONFIRM / CONFIRMED / PREPARED / SHIPPED / COMPLETED / CANCELED` 六个单值，
  传「进行中」这类四态合并会直接回 50002。所以前端做成下拉单选，没有做「进行中」标签页。
  见《阶段D-买家端开发计划》D5-3 登记。
- **移动端适配按组长确认暂缓**，当前只做基础响应式，演示与验收在桌面浏览器进行。
- **商品列表与收藏列表每页 12 条，订单列表每页 10 条**。后端 `page_size` 上限 100，超限回 400。
- **下单不会自动清购物车**：后端 `BuyerOrderServiceImpl` 不引用购物车表，
  下单成功后由前端删除对应行（`stores/cart.ts` 的 `submitOrder`），删除失败不影响已成订单。
- **`/history` 已下线**，旧书签会重定向到商品列表。

## 买家端不做的事

以下在阶段D 范围里明确不存在，前端也没有留下入口：

改资料、改密码、找回密码、购物车多规格、支付、收货地址、客服、售后、订单确认收货。
