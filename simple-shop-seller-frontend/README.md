# simple-shop 卖家前端

simple-shop 的**卖家后台**，前后端分离的 Vue 单页应用（阶段二重制版）。

本工程**只包含卖家端**。买家前端是独立的 `buyer-frontend` 工程（另建，与本工程互不依赖）；两端共用同一套后端 REST 接口。后端已不再输出任何 HTML。

---

## 一、技术栈

| 类别     | 选型                               |
| -------- | ---------------------------------- |
| 框架     | Vue 3（script setup + TypeScript） |
| 构建     | Vite                               |
| 路由     | Vue Router                         |
| 状态     | Pinia                              |
| 请求     | axios                              |
| 样式     | 原生 CSS（不引入 UI 库）           |
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
| npm test           | 运行 Vitest             |
| npm run test:watch | 监听模式                |
| npm run format     | Prettier 格式化 src/    |

---

## 四、目录结构

    src/
    ├── api/                  接口层,与阶段二 openapi 契约逐字对应
    │   ├── http.ts           axios 实例、Bearer 注入、统一拆壳、ApiError、401 回调
    │   ├── token.ts          会话令牌的本地持久化(key: simple-shop.seller.token)
    │   ├── types.ts          统一响应、错误码全集、全部请求/响应 DTO
    │   ├── session.ts        登录 / 退出(/seller/auth/session)
    │   ├── account.ts        修改密码(/seller/auth/password)
    │   ├── goods.ts          商品列表/发布/详情/改库存/改挂/下架/重上架
    │   ├── media.ts          媒体上传(/seller/media?purpose=)
    │   ├── category.ts       类别树 + 一二级 CRUD
    │   ├── intention.ts      意向名单(按商品) + 进入交易/成功/失败
    │   ├── history.ts        历史商品列表 / 详情
    │   ├── customer.ts       客户列表 / 详情(内嵌购买历史分页)
    │   └── __tests__/        连通性测试
    ├── validation/           前端输入校验:login / goods / category / intention + shared
    ├── stores/               Pinia:auth(登录状态)与全局实例
    ├── router/               路由表 + 登录守卫
    ├── layouts/              AppLayout(左侧导航 sticky 固定视口 + 退出登录)
    ├── views/                10 个页面
    ├── components/
    │   ├── common/           通用件:ConfirmDialog、Pagination、StatusTag、ToastHost
    │   ├── goods/            商品域:GoodsCard(宫格卡)、CategoryCascadeSelect(两级级联)、
    │   │                     ImageFilePicker(主图/图集选择)、DescriptionBlocks(描述渲染)、
    │   │                     DescriptionBlockEditor(描述编辑器)+ descriptionEditor.ts(行模型)
    │   └── intention/        意向域:IntentionTable(名单表)、TradeFailureDialog(失败裁决)
    ├── composables/          useToast(轻量消息提示,短窗口同文案去重)
    ├── constants/            labels(枚举→中文)、messages(提示文案 + 错误码→文案)
    ├── utils/                format(东八区时间/金额/字节数)、error(异常→文案)
    └── assets/               base.css(全局基础样式)、product.svg(无图占位图)

组件按功能分子文件夹（common / goods / intention）；页面与其他目录暂不拆分。

### 页面与路由

| 路由             | 页面         | 说明                                                               |
| ---------------- | ------------ | ------------------------------------------------------------------ |
| /login           | 登录         | 未登录访问任意受保护页会跳到这里                                   |
| /                | 商品列表     | 宫格布局(主图/占位图);状态、类别(两级级联)、关键字筛选 + 分页     |
| /publish         | 发布商品     | 名称/价格/库存/类别/主图/图集/描述块编辑器;媒体发布时统一上传     |
| /goods/:id       | 商品详情     | 图集切换大图、结构化描述、改库存/改挂/下架/重上架、意向名单与裁决 |
| /categories      | 类别管理     | 两级类别树:增、行内改名、删(带子级/被引用会被拦截)                |
| /customers       | 客户列表     | 注册时间倒序分页                                                   |
| /customers/:id   | 客户详情     | 基本信息 + 购买历史内嵌分页(含失败记录)                           |
| /history         | 历史记录     | 售罄归档商品,归档时间倒序分页                                      |
| /history/:id     | 历史商品详情 | 归档快照(图集/描述)+ 意向名单                                      |
| /password        | 修改密码     | 成功后强制重新登录                                                 |

---

## 五、重要决定

### 1. 业务拒绝 = HTTP 200 + 业务码

后端把「队列非空」「非队首」这类业务规则拒绝视为**正常分支**，返回 HTTP 200 + 业务码；例外只有 10002→401、50002→400、资源不存在（20011/10009/40001 等）→404、50000→500 等。

前端 request() 对这两种情形一律抛 ApiError（含 code / httpStatus），页面按 code 分支处理。

**message 不用于展示**：它是给日志与排查用的 ASCII 英文；界面文案统一由 constants/messages.ts 的 apiCodeMessage(code) 映射。

### 2. 队首判定:第一页数据里的第一条 QUEUED

意向名单按 queue_order 升序返回且**没有位次字段**,队首 = 排在最前的 QUEUED 行,由前端在**第一页**数据上判定(翻页后无法从当前页判断,按钮不显示,后端 30002 兜底)。已知边界:若某商品前面积累了超过一页的终态意向,队首会被挤到第二页——把状态筛选切到「排队中」即可回到第一页操作。

### 3. trading 行留在列表里

后端分页返回全部意向,trading 行也在列表里且不保证落在第 1 页。「标记成功/失败」按钮放在该行,列表与分页口径与后端完全一致,不做独立卡片。

### 4. 媒体一律延迟到发布时统一上传

主图、图集、描述媒体在编辑期都只保留本地 File(商品图带 objectURL 缩略预览,描述媒体不预览、显示为文件名行)。点「发布」→ 确认 → **串行上传**(主图→图集→描述媒体,按钮显示 i/n 进度)→ 全部回填 URL 后终检 → 一次性发布。

任何一环失败即中止,已成功者的 URL 缓存在条目上,**重试只传剩余**。放弃发布会留下服务端孤立文件,系统不做清理(已接受)。

### 5. 描述块编辑器:隐藏块结构的「行流」编辑器

后端描述是结构化块数组(TEXT/IMAGE/VIDEO),但用户视角是**一个连续编辑区**:文本行是自动增高的无边框 textarea(视觉无缝堆叠),媒体行是原子行(类型徽章+文件名+大小+删除),可聚焦后按 Backspace 删除,删除后焦点落回上一文本行末尾;「插入图片/视频」在**当前光标处**把文本行切分插入。块结构只存在于视图模型(descriptionEditor.ts 的行模型)与提交映射中。组件只做本地预检与累计字数(0/1000)展示,提交校验在发布页。

### 6. 状态与金额的契约口径(踩过坑)

- 状态枚举一律**大写**:`ON_SALE/FROZEN/OFF_MANUAL/OFF_ZERO`、`QUEUED/TRADING/SUCCEEDED/FAILED`。
- **price 是 JSON 数字**(后端 BigDecimal 直接序列化),不是阶段一的字符串;展示统一走 formatPrice。发布请求传 Number(校验器产出的两位小数字符串转换而来)。
- DTO 字段保持 snake_case,与后端 JSON 逐字一致,不做驼峰转换。
- 下架有两种:OFF_MANUAL(手动,可重上架)与 OFF_ZERO(售罄清零,改库存>0 保存即自动上架);**历史只收售罄归档**,手动下架不进历史。

### 7. 交易中(冻结)的商品禁改

FROZEN 状态下改库存/改挂/下架按钮置灰(后端 20021 兜底);下架要求队列为空(20022);重新上架仅对 OFF_MANUAL 且带显式确认(confirm:true,后端 20028 校验)。

### 8. 「标记成功」按库存区分确认文案

最后 1 件库存成交会触发售罄归档(其余排队买家判失败、商品转入历史、不可撤销),用重确认文案;库存有余则轻文案(扣 1 件回在售)。成功响应的 goods_status=ARCHIVED 时 toast 提示后直接跳回列表(商品已从当前表转入历史,详情页停留会 404)。

### 9. 登录态与 401(并发只提示一次)

- 令牌存 localStorage(后端会话 30 分钟滑动过期),key 为 `simple-shop.seller.token`。
- 任意接口返回 10002:清空本地令牌并统一跳回登录页(main.ts 注册)。页面挂载常有**多个并发请求同时 401**,两层防重:跳转动作加单飞标记;useToast 对同文案提示做 1.5 秒窗口去重(全局提示与组件 catch 的同文案合并为一条)。
- 修改密码成功后后端吊销全部会话(含本次),前端同步清空并强制重新登录。

### 10. 前后端双重校验

validation/ 按域拆分(login/goods/category/intention),规则与后端一致:名称/类别名 ≤50 码点拒尖括号、价格按 BigDecimal 口径(0<p≤999999.99 两位小数)、发布库存 ≥1 调整库存 ≥0、描述文本**各块合计** ≤1000 码点、图集 ≤8 张连主图 ≤9 张、媒体类型与大小本地预检(图 ≤5MB、视频 ≤20MB)。服务端始终是唯一权威,后端专属业务码仍会被正确映射处理。

### 11. 原生 CSS,不引入 UI 库

全局基础样式(按钮/输入/表格/卡片/弹窗/标签)在 assets/base.css,组件内 scoped 样式。Toast、确认弹窗、分页均为自行实现。侧栏导航 sticky 固定在视口内,长页面不拉伸。

### 12. 时间与金额的展示口径

- 后端一律下发 UTC 的 ISO 8601 时间;前端在 utils/format.ts 统一按**东八区**格式化展示。
- 金额为 JSON 数字,展示时只做千分位与两位小数格式化。

---

## 六、接口层约定

- 卖家端接口按域拆到 api/ 下九个模块(session、account、goods、media、category、intention、history、customer + 基础 http/token/types),index.ts 统一导出;契约以 simple-shop-doc/阶段二/openapi.json 为准(接口变更后需后端重新导出并同步 types.ts)。
- 统一响应形状 `{code, message, data}`;分页响应 `{items, page, page_size, total}`,page 从 1 起。
- 错误码常量集中在 api/types.ts 的 ApiCode(与后端 ErrorCode 全量对齐);码到界面文案的映射在 constants/messages.ts。
- 令牌由 http.ts 请求拦截器统一注入 Authorization: Bearer ...。

---

## 七、测试

目前只有**连通性测试**,文件位于 `src/api/__tests__/connectivity.spec.ts`,验证登录(新路径)与受保护接口的 10002 语义:

    npm test

- 默认访问 http://localhost:8080;后端不可达时**自动跳过**并打印提示,不会让测试变红。
- 可用环境变量覆盖:SMOKE_BASE_URL、SMOKE_ACCOUNT、SMOKE_PASSWORD。

---

## 八、已知偏离与限制

| 项             | 说明                                                                       |
| -------------- | -------------------------------------------------------------------------- |
| 队首翻页边界   | 队首从第一页判定;前面堆了超过一页终态意向时需把筛选切到「排队中」再操作   |
| 商品内容不可改 | 阶段二契约:发布后名称/价格/描述/图片不可修改,仅库存与类别可调             |
| 孤立媒体文件   | 上传成功但发布失败/放弃时产生,系统不做清理                                |
| 描述编辑器     | 已实现并接入发布页;媒体行的拖拽排序等增强不做                             |
| 部署配置       | 正式部署使用 `deploy/frontend` 组合镜像（同时托管买家 `/` 与卖家 `/seller/`）；本工程 Dockerfile/nginx.conf 仅用于卖家独立预览。 |
