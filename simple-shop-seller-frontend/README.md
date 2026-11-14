# simple-shop 卖家前端

simple-shop 的**卖家后台**，前后端分离的 Vue 单页应用，按**阶段三**口径实现：商品两态 + 下架原因、订单取代购买意向、客户购买历史改查订单。

本工程**只包含卖家端**。买家前端是独立的 `buyer-frontend` 工程（与本工程互不依赖）；两端共用同一套后端 REST 接口，契约以 `simple-shop-doc/阶段三/openapi.json` 为准。后端已不再输出任何 HTML。

---

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

---

## 二、快速开始

### 1. 启动后端

必须在**后端工程的根目录**执行（默认 8080 端口，需要本机 MySQL）：

    cd simple-shop-backend
    mvn spring-boot:run

后端启动时 Flyway 会自动建库建表（开发库 `simple_shop`）。

### 2. 启动前端

    cd simple-shop-seller-frontend
    npm install
    npm run dev

打开 http://localhost:5173 ，用种子账号登录：

    账号：seller
    口令：Abcd@1234

### 3. 开发代理与后端地址

开发服务器已把 **/api** 与 **/media** 代理到 http://localhost:8080（见 vite.config.ts），本地开发无需处理跨域；商品图、描述媒体均经 /media/** 访问。

部署（或后端不在本机）时，用环境变量 **VITE_API_BASE_URL** 指定接口前缀；不设置时默认 **/api**。

---

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

---

## 四、目录结构

    src/
    ├── api/                  接口层,与阶段三 openapi 契约逐字对应
    │   ├── http.ts           axios 实例、Bearer 注入、统一拆壳、ApiError、401 回调
    │   ├── token.ts          会话令牌的本地持久化(key: simple-shop.seller.token)
    │   ├── types.ts          统一响应、错误码全集、全部请求/响应 DTO
    │   ├── session.ts        登录 / 退出(/seller/auth/session)
    │   ├── account.ts        修改密码(/seller/auth/password)
    │   ├── goods.ts          商品列表/发布/详情/改库存/改挂/下架
    │   ├── media.ts          媒体上传(/seller/media?purpose=)
    │   ├── category.ts       类别树 + 一二级 CRUD
    │   ├── customer.ts       客户列表 / 详情(购买历史改查订单)
    │   ├── orders.ts         订单列表 / 详情 / 明细 / 确认·备货·发货·完成·取消
    │   └── __tests__/        连通性测试(npm run test:api)
    ├── validation/           前端输入校验:login / goods(含发布表单整体校验) / category + shared
    ├── stores/               Pinia:auth(登录状态)与全局实例
    ├── router/               路由表 + 登录守卫
    ├── layouts/              AppLayout(左侧导航 sticky 固定视口 + 退出登录)
    ├── views/                10 个页面
    ├── components/
    │   ├── common/           通用件:AppIcon(图标,currentColor)、ConfirmDialog、Pagination、StatusTag、ToastHost
    │   ├── category/         类别域:CategoryCard(一级卡片)、InlineNameEditor(行内改名/新增表单)、categoryEdit.ts(编辑目标类型)
    │   ├── order/            订单域:OrderList(状态 tab + 列表 + 分页,订单页与客户/商品详情共用)、OrderProgress(状态流程图)
    │   └── goods/            商品域:GoodsCard(宫格卡)、CategoryCascadeSelect(两级级联)、
    │                         ImageFilePicker(主图/图集选择)、DescriptionBlocks(描述渲染)、
    │                         DescriptionBlockEditor(描述编辑器)+ descriptionEditor.ts(行模型与行操作纯函数)
    ├── composables/          useToast(消息提示,短窗口同文案去重)、useTheme(深浅主题)、
    │                         useRequestSequence(请求序号守卫)、usePagedList(分页列表状态+竞态守卫)、
    │                         useCategoryTree(类别树共享缓存+单飞)、usePublishFlow(发布编排+进度)
    ├── constants/            labels(枚举→中文)、messages(提示文案 + 错误码→文案)
    ├── utils/                format(东八区时间/金额/字节数)、error(异常→文案)
    └── assets/               base.css(Tailwind 入口 + daisyUI 主题 + 项目复合类)、图标 SVG

    test/                     单元测试(npm run test:unit),与 src 平级、按模块镜像
    ├── validation/           shared / login / category / goods / publishForm
    ├── composables/          useRequestSequence / usePagedList / useCategoryTree / usePublishFlow
    └── components/goods/     descriptionEditor(媒体预检 + 行操作纯函数)

组件按功能分子文件夹（common / category / goods / order）；页面与其他目录暂不拆分。

### 页面与路由

| 路由             | 页面         | 说明                                                               |
| ---------------- | ------------ | ------------------------------------------------------------------ |
| /login           | 登录         | 未登录访问任意受保护页会跳到这里                                   |
| /                | 商品列表     | 宫格布局;状态与下架原因合一下拉、类别(两级级联)、关键字筛选 + 分页,筛选同步 URL |
| /publish         | 发布商品     | 名称/价格/库存/类别/主图/图集/描述块编辑器;媒体发布时统一上传;脏表单离开保护 |
| /goods/:id       | 商品详情     | 图集切换大图、结构化描述、改库存/改挂/下架、该商品的订单(内嵌)      |
| /orders          | 订单管理     | 状态 tab 筛选 + 分页;支持 ?buyer_id= / ?goods_id= 深链筛选         |
| /orders/:id      | 订单详情     | 订单头与明细独立加载;按状态表驱动推进(确认→备货→发货→完成)与取消    |
| /customers       | 客户列表     | 注册时间倒序分页                                                   |
| /customers/:id   | 客户详情     | 基本信息 + 该客户的订单(内嵌,按 buyer_id 筛选)                     |
| /categories      | 类别管理     | 两级类别树:增、行内改名、删;全局单编辑;多列瀑布流布局              |
| /password        | 修改密码     | 成功后强制重新登录                                                 |

---

## 五、重要决定

### 1. 业务拒绝 = HTTP 200 + 业务码

后端把业务规则拒绝视为**正常分支**，返回 HTTP 200 + 业务码；例外只有 10002→401、50002→400、资源不存在→404、50000→500 等。前端 request() 对这两种情形一律抛 ApiError（含 code / httpStatus），页面按 code 分支处理。

**message 不用于展示**：它是给日志与排查用的 ASCII 英文；界面文案统一由 constants/messages.ts 的 apiCodeMessage(code) 映射。

### 2. 商品两态 + 下架原因

状态只有 `ON_SALE` / `OFF_SHELF`；下架原因 `MANUAL` / `STOCK_ZERO` / `SOLD_OUT` 与状态成对展示。**MANUAL 是终态**：库存只读、没有重新上架入口，类别仍可改挂；`STOCK_ZERO` / `SOLD_OUT` 把库存改回大于 0 保存即**自动上架**。有未完成订单时手动下架与手动清零会被后端拒绝（20020），界面文案已说明。商品是否可购买以后端 `purchasable` 为准，前端不自行推断。

### 3. 订单按序推进，完成才扣库存

六态整单流转（待确认→已确认→备货完成→已发货→交易完成，另有已取消）。详情页用「状态→下一步动作」表驱动渲染，不存在跳级入口；五个动作共用确认弹窗与提交防重。**交易完成时按明细扣库存，任一不足整单拒绝**（60004），订单停在已发货，提示补货后重试或取消。买家在发货前可取消、卖家在完成前可取消，取消是终态、不动库存。订单头与明细是两次独立请求，各自持有 loading/error/重试，互不拖垮。

### 4. 媒体一律延迟到发布时统一上传

主图、图集、描述媒体在编辑期只保留本地 File（商品图带 objectURL 缩略预览，描述媒体不预览、显示为文件名行）。点「发布」→ 确认 → **串行上传**（主图→图集→描述媒体，进度条显示 i/n）→ 全部回填 URL 后终检 → 一次性发布。编排集中在 composables/usePublishFlow。

任何一环失败即中止，已成功者的 URL 缓存在条目上，**重试只传剩余**（失败后按钮变「重试发布」，确认弹窗说明不重复上传）。放弃发布会留下服务端孤立文件，系统不做清理（已接受）。提交进行中整个表单（含描述编辑器）锁定，避免上传途中增删媒体。

### 5. 描述块编辑器：隐藏块结构的「行流」编辑器

后端描述是结构化块数组（TEXT/IMAGE/VIDEO），用户视角是**一个连续编辑区**：文本行是自动增高的无边框 textarea，媒体行是原子行（类型徽章+文件名+大小+删除），可聚焦后按 Backspace 删除；行首 Backspace / 行尾 Delete 合并相邻文本行或删除相邻媒体；「插入图片/视频」在**当前光标处**切分插入。行的切分/合并/删除是 descriptionEditor.ts 的**纯函数**（有单元测试），组件只负责 DOM 焦点与自动增高。组件只做本地预检与累计字数（0/1000）展示，提交校验在发布页。

### 6. 状态与金额的契约口径

- 状态枚举一律**大写**：`ON_SALE/OFF_SHELF`、`MANUAL/STOCK_ZERO/SOLD_OUT`、订单六态。
- **price 是 JSON 数字**（后端 BigDecimal 直接序列化）；展示统一走 formatPrice，发布请求传 Number（校验器产出的两位小数字符串转换而来）。
- DTO 字段保持 **snake_case**，与后端 JSON 逐字一致，不做驼峰转换。
- 订单主键字段是 `order_id`（不是 id）；列表与详情只回订单头，明细按订单 ID 单独查。

### 7. 登录态与 401（并发只提示一次）

- 令牌存 localStorage（后端会话 30 分钟滑动过期），key 为 `simple-shop.seller.token`。
- 任意接口返回 10002：清空本地令牌并统一跳回登录页（main.ts 注册）。页面挂载常有**多个并发请求同时 401**，两层防重：跳转动作加单飞标记；useToast 对同文案提示做 1.5 秒窗口去重。
- 修改密码成功后后端吊销全部会话（含本次），前端同步清空并强制重新登录。

### 8. 前后端双重校验

validation/ 按域拆分（login/goods/category），规则与后端一致：名称/类别名 ≤50 码点拒尖括号与换行、价格按 BigDecimal 口径（0<p≤999999.99 两位小数，支持科学计数法）、发布库存 ≥1 调整库存 ≥0、描述文本**各块合计** ≤1000 码点、图集 ≤8 张连主图 ≤9 张、媒体类型与大小本地预检（图 ≤5MB、视频 ≤20MB）。发布表单基础字段由 validatePublishForm 一次校验返回逐字段文案与归一化值。服务端始终是唯一权威。

### 9. 列表加载与共享状态

- **usePagedList**：商品/订单/客户三个列表共用分页状态与加载逻辑，内置 **useRequestSequence 请求序号守卫**——快速切换筛选/翻页/路由参数时，过期响应（含错误）整体丢弃，loading 只由最新请求复位。商品与订单列表把筛选写回 URL（刷新/分享可还原），客户列表不写。
- **useCategoryTree**：类别树模块级共享缓存 + 单飞请求，级联选择器挂载不再重复拉取；类别管理页进入时与每次增删改后强制 refresh 更新缓存。
- 订单列表是**同一份组件**的三种筛选入口（订单管理 / 按客户 / 按商品），差别只在 query 与是否写回 URL；详情一律按 order_id 进。

### 10. 类别管理：全局单编辑 + 瀑布流

编辑态由页面持有（activeEdit），**全局同一时刻只有一个行内编辑**，开始新编辑自动收起上一个；行内表单统一为 InlineNameEditor（挂载即聚焦、Esc 取消、Enter 提交）。布局用 CSS 多列瀑布流，卡片按内容自然高度排列；类别无序，列优先的阅读顺序可接受。

### 11. 发布页离开保护

表单脏（任一字段非默认值或有媒体/描述内容）且未发布成功时，路由离开弹确认框、关闭标签页触发 beforeunload 提示；发布成功后正常跳转不受影响。名称输入带实时码点计数（x/50，超限变红）。

### 12. 样式：Tailwind CSS 4 + daisyUI 5

- 唯一 CSS 入口是 assets/base.css：`@import "tailwindcss"` + `@plugin "daisyui"` + `shop-light` / `shop-dark` 两套主题。样式优先级：daisyUI 组件类 > Tailwind 工具类 > `@layer components` 复合类（`.panel` 与 `.muted`）。
- 颜色一律用语义色（`base-*`、`primary`、`success`、`warning`、`error`、`info`），不写死色值；叠加层用实色 `--color-surface-solid`。
- 主题切换与持久化只走 composables/useTheme，首屏由 index.html 内联脚本先定，避免闪白。
- 图标统一用 AppIcon（内联 SVG + currentColor），不要 `<img src="*.svg">` 直插。
- 数据密集型界面默认 `-sm` 一档；页面切换有 120ms 过渡，`prefers-reduced-motion` 下自动关闭。

### 13. 时间与金额的展示口径

后端一律下发 UTC 的 ISO 8601 时间；前端在 utils/format.ts 统一按**东八区**格式化展示。金额为 JSON 数字，展示时只做千分位与两位小数格式化。

---

## 六、接口层约定

- 卖家端接口按域拆到 api/ 下七个模块（session、account、goods、media、category、customer、orders + 基础 http/token/types），index.ts 统一导出；契约以 `simple-shop-doc/阶段三/openapi.json` 为准（接口变更后需后端重新导出并同步 types.ts）。
- 统一响应形状 `{code, message, data}`（参数错误时另带 `errors: [{field, message}]`）；分页响应 `{items, page, page_size, total}`，page 从 1 起、page_size 上限 100。
- 错误码常量集中在 api/types.ts 的 ApiCode（与后端 ErrorCode 全量对齐）；码到界面文案的映射在 constants/messages.ts。3xxxx/4xxxx 旧码段已停用，不得复用；7xxxx 属买家购物车与收藏域，卖家端接口不会返回。
- 令牌由 http.ts 请求拦截器统一注入 Authorization: Bearer ...。
- 未知路由（含阶段二遗留的 `/history` 等旧路径）统一重定向回商品列表，不渲染空白页。

### 6.1 错误码 → 界面文案

后端码表见 `simple-shop-doc/阶段三/契约交接.md` §5 与 `ErrorCode` 枚举；下表是**卖家端实际可达**的码在本工程里的文案（`constants/messages.ts` 为唯一来源，改文案改那一处）。每个业务拒绝都有中文提示，界面从不直接显示后端 `message`。

| 码 | 触发场景 | 界面文案 |
| --- | --- | --- |
| 10001 | 登录账号或口令错误 | 账号或密码错误（登录表单字段） |
| 10002 | 会话失效 / 未登录 / 角色不匹配 | 登录已超时，请重新登录（清令牌 → 跳登录页） |
| 10003 | 改密时原密码不对 | 原密码不正确（原密码字段） |
| 10004 | 新密码不足 8 位或超过 72 字节 | 新密码至少 8 位，且不超过 72 字节 |
| 10005~10008 | 买家注册专用（用户名占用 / 用户名非法 / 电话非法 / 地点非法） | 已登记文案，卖家端界面不可达 |
| 10009 | 客户详情查不到该买家（HTTP 404） | 客户不存在或已被删除，请刷新客户列表 |
| 10010 | 登录账号含空白、尖括号或控制字符 | 账号格式不正确（1~50 个字符，不含空格与 < >） |
| 20001 | 类别已被删除 | 类别不存在或已被删除，请刷新 |
| 20002 | 商品改挂选了二级以外 | 只能选择二级类别 |
| 20003 | 类别名非法（空、>50 字符、含 < >） | 类别名不超过 50 个字符，不能包含 < > |
| 20004 | 同级已有同名类别 | 同级已有同名类别 |
| 20005 | 删除仍有二级子类的一级类别 | 请先删除该类别下的二级类别 |
| 20006 | 删除仍被商品引用的类别 | 仍有商品挂在该类别下，不能删除 |
| 20011 | 商品不存在（HTTP 404） | 商品不存在或已删除，请刷新列表 |
| 20012 | 商品名非法 | 名称不超过 50 个字符，不能包含 < >，不允许换行 |
| 20013 | 描述块非法 | 描述文本各块合计不超过 1000 个字符，不能包含 < >；媒体块须先上传 |
| 20014 | 价格越界或小数位过多 | 价格须大于 0 且不超过 999,999.99，最多两位小数 |
| 20015 | 库存非法（发布 <1 / 调整 <0） | 库存须是不小于 1 的整数 / 库存须是不小于 0 的整数 |
| 20016 | 图片类型或大小不合法 | 仅支持 JPG／PNG，单张不超过 5MB |
| 20017 | 图片数量超限 | 图集最多 8 张，连主图最多 9 张 |
| 20018 | 有图集但缺主图 | 请先上传主图，再添加图集图片 |
| 20019 | 对非在售商品下架 | 商品当前不在售，请刷新后重试 |
| 20020 | 商品被未完成订单引用（禁止手动下架 / 手动清零） | 商品有未完成订单，暂不能下架或把库存清零；先处理完这些订单再操作 |
| 20023 | 商品状态不允许该操作（如手动下架后改库存） | 商品状态不允许该操作，请刷新后重试 |
| 20025 | 视频非法或过大（受 multipart 上限影响实际不可达） | 仅支持 MP4，单个不超过 20MB |
| 20026 | 媒体非法或超出上限 | 媒体文件无效或超出大小限制 |
| 20027 | 媒体类型与用途不符 | 媒体类型与用途不符（商品图仅图片；描述媒体支持图片／视频） |
| 60001 | 订单不存在（HTTP 404，详情页只给「返回列表」） | 订单不存在或已被删除，请刷新列表 |
| 60002 | 状态不允许该操作（跳级 / 重复推进 / 终态推进） | 订单状态不允许该操作，请刷新后重试 |
| 60003 | 当前状态不可取消（交易完成后） | 当前状态不可取消该订单 |
| 60004 | 完成时库存不足（整单拒绝，订单停在已发货） | 库存不足，整单未能完成：订单仍在「已发货」，补货后可重试 |
| 50000 | 服务端异常 | 服务端内部错误，请稍后重试 |
| 50001 | 触发限流（默认仅注册/登录/下单有阈值） | 操作过于频繁，请稍后重试 |
| 50002 | 缺参数 / 参数格式错（HTTP 400） | 请检查输入内容 |
| 50003 | 请求的资源不存在（HTTP 404） | 请求的资源不存在 |
| 50004 | 请求方法不支持（HTTP 405） | 接口不支持该请求方式，请刷新页面后重试 |
| 50005 | Content-Type 不支持（HTTP 415） | 请求格式不受支持（上传须为 multipart/form-data） |
| — | 未登记的业务码 | 操作失败，请稍后重试（兜底，不猜原因） |
| — | 网络不通 / 响应形状异常 | 无法连接服务器，请检查后端是否已启动 / 服务返回了无法识别的响应 |

---

## 七、测试

测试分两类，命令互不干扰：

    npm run test:unit    # 单元测试:test/ 目录,纯函数与 composable,无需后端
    npm run test:api     # 连通性测试:src/api/__tests__,需要后端
    npm run test:watch   # 单元测试监听模式

- **单元测试**（Vitest，node 环境）覆盖：validation 全部校验器（含价格 BigDecimal 口径、发布表单整体校验）、composables（请求序号守卫、分页列表、类别树缓存、发布编排）、描述行操作纯函数与媒体预检。发布编排与类别树的接口调用以 mock 隔离。
- **连通性测试**默认访问 http://localhost:8080，验证登录与受保护接口的 10002 语义；后端不可达时**自动跳过**并打印提示，不会让测试变红。可用环境变量覆盖：SMOKE_BASE_URL、SMOKE_ACCOUNT、SMOKE_PASSWORD。
- vitest.config.ts 同时收集 `src/**/__tests__` 与 `test/` 两处；两个 npm 命令用路径过滤各取所需。

---

## 八、已知偏离与限制

| 项             | 说明                                                                       |
| -------------- | -------------------------------------------------------------------------- |
| 商品内容不可改 | 阶段三契约:发布后名称/价格/描述/图片不可修改,仅库存与类别可调               |
| 孤立媒体文件   | 上传成功但发布失败/放弃时产生,系统不做清理                                 |
| 描述编辑器     | 媒体行拖拽排序、媒体预览、粘贴/拖入文件均不做（已接受的取舍）               |
| 类别瀑布流     | CSS 多列布局为列优先顺序;编辑增高时列会重新平衡,相邻卡片可能轻微移位        |
| 会话           | 后端会话 30 分钟滑动过期、存于进程内,backend 重启会丢会话                   |
| 部署配置       | 正式部署使用 `deploy/frontend` 组合镜像（同时托管买家 `/` 与卖家 `/seller/`）；本工程 Dockerfile/nginx.conf 仅用于卖家独立预览。生产构建 base 为 `/seller/`，开发期仍在根路径 |
| 快照未标空值   | `openapi.json` 未标注字段可空（`off_shelf_reason`/`off_shelf_at`/`main_image_url`/`description.text`/`default_location`/`parent_id`），`types.ts` 按《状态模型设计》标成 `string \| null`；这是快照生成器的表达限制，不是契约差异 |
| 参数校验明细   | 后端 400/50002 会带 `errors[]`，卖家端不逐字段回填（能到这一步的都是前端校验漏掉的输入），只按业务码给一句中文提示 |