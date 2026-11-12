# Simple-Shop 阶段U · 卖家端 UI 技术栈改造计划

> **文档性质**：卖家端样式技术栈改造与双主题落地计划（v0.1），说明选型、配色、迁移顺序与验收口径；**不含**业务口径变更与页面功能设计。
> **适用范围**：`simple-shop-seller-frontend`。买家端**不在**本次范围，仍用原生 CSS。
> **实施位置**：E1-5 之后、E1-3 接回与 E2 之前。功能口径仍以《阶段E-卖家端开发计划》为准。
> **上游依据**：`AGENTS.md` §5（前端约定）、阶段E《工具与验收现状》。
> **版本**：v1.0（U0–U4 全部完成，阶段 U 收口）　**日期**：2026-11-11

---

## 摘要（一页结论）

| 问题 | 结论 |
| --- | --- |
| 为什么改 | E1-3 的页面口径改造（原因展示、原因筛选、库存只读）要重写大部分模板；样式底座没定就动手，同一批页面要改两遍 |
| 技术栈 | Tailwind CSS 4 + daisyUI 5，经 `@tailwindcss/vite` 插件接入；不写 `tailwind.config.js`（v4 已废弃） |
| 为什么选 daisyUI | 提供的是**类名**而不是 Vue 组件：现有 `<table>`、`<form>`、`<button>` 结构基本保留，只换 class；组件边界、props、请求逻辑都不动 |
| 主题 | 自定义 `shop-light` / `shop-dark` 两套主题，`data-theme` 挂在 `<html>`，值持久化在 localStorage |
| 顺序 | U0 基座 → U1 公共件 → U2 起步页面（登录 / 商品列表 / 商品详情）→ U3 其余页面 → U4 收口 → 回 E1-3 |
| 迁移策略 | 与 daisyUI 同名的旧类**一次性**删除并全站替换（约 150 处，机械改动），不做兼容层 |
| 最大风险 | `.btn/.card/.input/.textarea` 与 daisyUI 同名；旧定义是非分层 CSS，会压过组件样式，必须同步删除 |
| 不做 | 买家端、路由、接口、业务规则、E2/E3 功能口径 |

---

## 1. 目标与非目标

### 1.1 目标

1. 卖家端具备可维护的样式底座：语义色 + 工具类 + 组件类，不再靠散落的 scoped CSS 拼页面。
2. 深色 / 浅色两套主题可切换、可持久化，且两套都达到「能长时间看」的可读性。
3. 把「改样式」和「改口径」拆开：U 阶段只动表现层，E2/E3 只动业务口径。
4. 公共件（侧栏导航、Toast、确认弹窗、分页、状态标签）先收敛成一套，后续页面直接复用。

### 1.2 非目标

- 不做买家端改造，也不把 Tailwind/daisyUI 的写法带到买家端。
- 不改路由、接口、状态管理、校验逻辑；不新增功能。
- 不实现 E2-1 的筛选条件进 URL、E2-3 的下架阻断文案——这些回 E1-3/E2 做，避免与样式改动混在一个提交里。
- 不追求像素级还原：允许密度、圆角、间距有 2~4px 级差异，但信息层级与操作位置不能变。

---

## 2. 技术选型

### 2.1 接入方式

| 项 | 决定 |
| --- | --- |
| Tailwind CSS | 4.x，`npm i -D tailwindcss @tailwindcss/vite` |
| daisyUI | 5.x，`npm i -D daisyui` |
| Vite | `vite.config.ts` 增加 `tailwindcss()` 插件，不加 PostCSS 配置 |
| 入口 | 仍是 `src/assets/base.css`，由 `main.ts` 引入，内容改为 `@import 'tailwindcss'` + `@plugin 'daisyui'` |
| 配置 | 不新建 `tailwind.config.js`；主题与插件选项写在 CSS 里（v4 口径） |

daisyUI 与 Tailwind 都放 `devDependencies`：构建期产物是静态 CSS，运行时不依赖这两个包。

### 2.2 为什么不引入 Vue 组件库

Element Plus / Naive UI 这类库会把现有的 `<table>`、`<form>`、`<input>` 换成封装组件，模板要整体重写，`v-model`、事件、插槽全部对不上；而本项目的页面结构（表格 + 表单 + 卡片）本身没问题，问题在样式。daisyUI 只提供类名，`<table class="table">` 还是那张表，改造面小一个量级。

---

## 3. 主题与配色

### 3.1 语义色约定

| 角色 | 用在哪 |
| --- | --- |
| `base-200` | 页面底色 |
| `base-100` | 卡片、面板、侧栏（半透明，见 3.2） |
| `surface-solid` | 叠加层：弹窗、下拉弹层（不透明，见下方补记） |
| `base-300` | 分隔线、边框、输入框描边 |
| `base-content` | 正文；弱化文字用 `base-content/60` |
| `primary` | 全页唯一主操作（发布、保存、搜索） |
| `success` / `warning` / `error` / `info` | 状态标签、校验提示、危险确认 |
| `neutral` | 不承载品牌色的实心块（如深色侧栏强调块） |

硬规则：模板与 scoped 样式里**不出现** `#fff`、`rgba(...)`、`gray-500` 这类固定色值。深色主题下唯一会「发白」的就是这一类写法。

### 3.2 两套主题 token

色板取自项目根目录 `配色方案.txt`：**暖色系**——浅色是纸白底 + 焦橙主色，深色是暖褐黑底 + 亮橙主色。冷蓝系整体退场，不要混用。

**shop-light**（默认、`prefersdark` 关闭）

| token | 值 | 说明 |
| --- | --- | --- |
| base-100 | `rgb(255 255 255 / 72%)` | 半透明白：纯白在暖色底上偏冷偏硬，叠加后是暖白（≈`#fcfaf7`）；深色主题仍为不透明 |
| base-200 | `#f6f3ee` | 页面底，偏暖的纸色 |
| base-300 | `#e7e1d6` | 边框，暖灰 |
| base-content | `#2b2620` | 正文，暖黑 |
| primary / content | `#d14408` / `#ffffff` | 焦橙；提亮观感上比纯红更"商品"，白字对比度 4.7:1 |
| secondary / content | `#0f766e` / `#ffffff` | 深青，备用 |
| accent / content | `#c026d3` / `#ffffff` | 品红，备用 |
| neutral / content | `#3f3a33` / `#f6f3ee` | 不承载品牌色的实心块 |
| info / content | `#0369a1` / `#ffffff` | |
| success / content | `#15803d` / `#ffffff` | 在售、成功；白字 4.6:1 |
| warning / content | `#f59e0b` / `#451a03` | 库存耗尽等警示；深棕字而非白字，避免对比度不足 |
| error / content | `#dc2626` / `#ffffff` | 危险操作、失败 |

**shop-dark**

| token | 值 | 说明 |
| --- | --- | --- |
| base-100 | `#26221c` | 卡片（比页面底亮，靠明度差做层级） |
| base-200 | `#1c1915` | 页面底，暖褐黑 |
| base-300 | `#38322a` | 边框；深色下边界比阴影更有效 |
| base-content | `#ede7dc` | 正文，暖白 |
| primary / content | `#ff6b35` / `#331005` | 亮橙；深色下用 `-content` 当字色，橙底黑字比白字更稳 |
| secondary / content | `#2dd4bf` / `#052e29` | 备用 |
| accent / content | `#e879f9` / `#4a044e` | 备用 |
| neutral / content | `#3a342b` / `#ede7dc` | |
| info / content | `#38bdf8` / `#082f49` | |
| success / content | `#4ade80` / `#052e16` | |
| warning / content | `#fbbf24` / `#451a03` | |
| error / content | `#f87171` / `#450a0a` | |

**两套共用的形状 token**

| token | 值 | 理由 |
| --- | --- | --- |
| `--radius-box` | `0.5rem` | 卡片 8px，与改造前一致 |
| `--radius-field` | `0.5rem` | 按钮/输入框 8px，略圆但统一 |
| `--radius-selector` | `1rem` | badge/checkbox 走胶囊形 |
| `--size-field` | `0.25rem` | 保持紧凑；页面统一再用 `-sm` 一档 |
| `--border` | `1px` | |
| `--depth` | light `1` / dark `0` | 深色下阴影不可见，层级靠 base-100 与 base-300 的明度差 |
| `--noise` | `0` | 不要颗粒感 |

暖色板的一个副作用：`primary` 与 `warning` 都是橙黄系，同时出现会互相削弱。约定 **`warning` 只用于警示文案与警示标签，不做按钮填充色**；按钮的实心色只有 `primary` 与 `error`。`success` 的绿在暖底上偏冷，面积控制在 badge 与提示条以内。

### 3.3 base.css 结构（已落地，与实际文件一致）

```css
@import 'tailwindcss';

@plugin 'daisyui' {
  themes: false; /* 内置主题全关，只用下面两套自定义主题 */
  logs: false;
}

@plugin 'daisyui/theme' {
  name: 'shop-light';
  default: true;
  prefersdark: false;
  color-scheme: light;

  --color-base-100: rgb(255 255 255 / 72%);
  /* 叠加层实色：弹窗、下拉弹层用，等于 base-100 叠在 base-200 上的观感 */
  --color-surface-solid: #fcfaf7;
  --color-base-200: #f6f3ee;
  --color-base-300: #e7e1d6;
  --color-base-content: #2b2620;
  --color-primary: #d14408;
  --color-primary-content: #ffffff;
  /* secondary / accent / neutral / info / success / warning / error 同 §3.2 */

  --radius-selector: 1rem;
  --radius-field: 0.5rem;
  --radius-box: 0.5rem;
  --size-selector: 0.25rem;
  --size-field: 0.25rem;
  --border: 1px;
  --depth: 1;
  --noise: 0;
}

@plugin 'daisyui/theme' {
  name: 'shop-dark';
  default: false;
  prefersdark: true;
  color-scheme: dark;
  /* 同结构，替换为 §3.2 的深色值；--depth: 0 */
}

@layer base {
  html {
    font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  }

  body {
    /* 页面底色走 base-200，卡片走 base-100，两者分工固定 */
    @apply bg-base-200 text-base-content;
    font-size: 14px;
    line-height: 1.6;
  }
}

@layer components {
  /* 页面通用容器：等价于改造前 .card 的观感，避免十几处重复写四条工具类 */
  .panel {
    @apply rounded-box border border-base-300 bg-base-100 p-4 shadow-sm;
  }

  /* 二级文字，等价于 text-base-content/60 */
  .muted {
    @apply text-base-content/60;
  }

  /* 路由切换过渡（U2 加） */
  .route-enter-active,
  .route-leave-active,
  .fade-enter-active,
  .fade-leave-active {
    transition:
      opacity 120ms ease-out,
      transform 120ms ease-out;
  }

  .route-enter-from {
    opacity: 0;
    transform: translateY(6px);
  }

  .route-leave-to {
    opacity: 0;
    transform: translateY(-4px);
  }

  .fade-enter-from,
  .fade-leave-to {
    opacity: 0;
  }

  @media (prefers-reduced-motion: reduce) {
    .route-enter-active,
    .route-leave-active,
    .fade-enter-active,
    .fade-leave-active {
      transition: none;
    }
  }
}

/* daisyUI 的组件类在 utilities 层的子层里，改它们的全局外观只能用 @utility */
@utility btn {
  font-size: 0.8125rem;
}

@utility input {
  font-size: 0.8125rem;
}

@utility select {
  font-size: 0.8125rem;
  /* 原生下拉的弹出列表跟着 select 的底色走，必须实色 */
  background-color: var(--color-surface-solid);
}

@utility textarea {
  font-size: 0.8125rem;
}

@utility modal-box {
  background-color: var(--color-surface-solid);
}
```

说明：

- 复合类只留 `.panel`（容器）与 `.muted`（二级文字），其余一律用工具类，避免长出一套自研设计系统。
- 原来过渡期保留的 `.field` / `.field__label` / `.field__error` 已在 U3 删掉——所有表单改用 daisyUI 的 `fieldset` + `fieldset-legend`，这四个类不再有引用。
- daisyUI 的 `-sm` 档字号是 12px，后台表单与按钮读起来偏小，统一提到 13px；**高度不变**（仍是 32px），密度不受影响。
- 这条 `@utility` 必须留在文件末尾：daisyUI 组件在 utilities 层的子层里，`@layer components` 里的同名规则会被静默忽略。

### 3.4 切换、持久化与首屏防闪

- `data-theme` 挂在 `<html>`：`shop-light` / `shop-dark`。`shop-dark` 标了 `prefersdark`，所以**首次访问不写 JS 也能跟随系统**。
- 用户手动切换后写 `localStorage['simple-shop.seller.theme']`，值只允许 `shop-light` / `shop-dark`；非法值忽略并按系统走。
- `composables/useTheme.ts`：模块级单例（与 `useToast` 同风格），导出 `theme`（当前生效值）、`setTheme(next)`、`toggle()`。页面里禁止直接改 `document.documentElement.dataset.theme`。
- 防闪白：在 `index.html` 的 `<head>` 里放一段同步内联脚本，读 localStorage 并立刻设置 `data-theme`，早于样式表渲染。脚本只做这一件事，不引外部文件。
- `color-scheme` 由主题声明，深色下滚动条与原生控件同步变暗，不会出现「深色页面白滚动条」。

### 3.5 状态标签的色调映射

`StatusTag` 的 `TagTone` 契约不变，只换实现：

| tone | daisyUI |
| --- | --- |
| `default` | `badge badge-ghost badge-sm` |
| `success` | `badge badge-success badge-soft badge-sm` |
| `warning` | `badge badge-warning badge-soft badge-sm` |
| `danger` | `badge badge-error badge-soft badge-sm` |
| `info` | `badge badge-info badge-soft badge-sm` |

`default` 用 `badge-ghost` 而不是 `badge-soft`：`badge-soft` 的底色取自色号本身，`badge-neutral` 在深色主题里会变成"深底深字"；`badge-ghost` 走 `base-*`，两套主题自动翻转。

商品两态与三种下架原因的**具体配色**留到 E1-3 定（届时只需改 `constants/labels.ts` 的 tone），U 阶段只保证机制可用。订单六态同理，留给 E3。

---

## 4. 现状盘点与影响面

### 4.1 需要替换的量

| 项 | 数量 | 处理 |
| --- | --- | --- |
| `class="btn"` | 40 | 补 `btn-sm`；其中 25 处无色按钮再加 `btn-outline` |
| `btn--small` | 24 | → `btn-sm` |
| `btn--primary` | 10 | → `btn-primary` |
| `btn--danger` | 3 | → `btn-error` |
| `class="input"` | 17 | 输入框保 `input`；3 处 `<select>` 改 `select` |
| `class="card"` | 15 | → `panel`（含商品卡、登录卡） |
| `data-table` | 1 | → `table table-sm` |
| `var(--*)` | 45 | 换成 daisyUI 语义色（见 4.3） |
| 硬编码色值 | 39 行 | U0 一并改成主题感知写法（见 4.5） |

合计约 150 处，分布在 16 个 `.vue` + 1 个 CSS。绝大多数可用查找替换完成，**不涉及任何逻辑改动**。

为什么无色按钮补 `btn-outline`：daisyUI 默认 `btn` 是 `base-200` 实心底、无边框；而页面里的按钮几乎都放在 `base-100` 面板上，两套主题下都会糊成一片（浅色下偏白、深色下偏黑）。描边版本在两种底色上都有清晰边界。

### 4.2 同名冲突（本次最大坑）

`base.css` 目前自己定义了 `.btn`、`.btn--primary`、`.btn--danger`、`.btn--small`、`.input`、`.textarea`、`.card`、`.data-table`，其中前五个与 daisyUI 组件类**完全同名**。

这些定义是**非分层** CSS，而 Tailwind/daisyUI 的类在 `@layer` 里。层叠规则是「非分层 > 分层」，所以只要旧定义还在，就会稳定压过 daisyUI 的 `.btn`/`.input`/`.card`，表现为「类名换了但样式没变」「按钮点着没反馈」这类假象，而且往工具类上加样式也不生效。

因此 U0 必须同时做三件事：删除旧定义、替换全部引用、把残留复合类放进 `@layer components`。三者拆开做就会出现半坏状态。

另一个坑：`var(--border)` 与 daisyUI 的 `--border`（边框宽度）同名。项目里的 `--border` 是**颜色**，不能做别名兼容，必须直接换成 `var(--color-base-300)`。

第三条是实测才发现的：**daisyUI 的组件类不在 `components` 层，而在 `utilities` 层下的 `daisyui.l1.l2.l3` 子层里**。所以 `@layer components { .btn { ... } }` 想改 daisyUI 组件的全局外观会被静默忽略（字号那次就是这么失效的），必须改用 `@utility btn { ... }`。这条已写进 `AGENTS.md` §5。

### 4.3 旧变量替换表

| 旧 | 新 |
| --- | --- |
| `var(--bg)` | `var(--color-base-200)` |
| `var(--surface)` | `var(--color-base-100)` |
| `var(--border)` | `var(--color-base-300)` |
| `var(--text)` | `var(--color-base-content)` |
| `var(--muted)` | `var(--color-base-content)` + 60% 透明度，或直接用 `.muted` 类 |
| `var(--primary)` | `var(--color-primary)` |
| `var(--danger)` | `var(--color-error)` |
| `var(--success)` | `var(--color-success)` |
| `var(--warning)` | `var(--color-warning)` |
| `var(--radius)` | `var(--radius-box)` |
| `var(--shadow)` | 删掉，改用 `shadow-sm` / `shadow-md` |

不保留任何旧变量别名：别名会让「还差哪一页没改」变得不可见。U0 结束时 `grep -E 'var\(--(bg|surface|border|text|muted|primary|danger|success|warning|radius)\)' src` 必须为 0（`var(--color-*)` 与 `var(--radius-box)` 这类 daisyUI 语义变量是允许的，也是推荐写法）。

### 4.4 E2E 影响

`simple-shop-test-e2e/tests/03-seller-goods.spec.ts`、`04-trade.spec.ts` 用 CSS 类名定位：`.tag`（4 处）、`.toast`（1 处）、`.goods__stock-input`。

- `.tag` 会被 `badge` 取代 → 该定位失效。这两个文件本就是阶段二遗留、已判定重写（`AGENTS.md` §7），U 阶段**不顺手改 E2E**。
- 处置：`StatusTag` 补 `data-testid="status-tag"`，Toast 每条补 `data-testid="toast-item"`；`ROLE`/文案定位优先，卖家 E2E 重写时统一改用 testid。
- `.goods__stock-input` 是页面自有类名，建议保留原类名作为稳定钩子，不随样式改造重命名。

### 4.5 硬编码色值的替换口径

深色主题下唯一会「发白」的就是写死的色值。39 行按四类处理，U0 已全部改完：

| 场景 | 旧写法 | 新写法 |
| --- | --- | --- |
| 错误提示条（5 处） | `#fecaca` 边框 + `#fef2f2` 底 | `color-mix(in oklab, var(--color-error) 35%, transparent)` 边框 + 10% 底 |
| 警示提示条（1 处） | `#fde68a` + `#fffbeb` | 同上，换成 `--color-warning`（35% / 12%） |
| 浅色块底（图片位、hover） | `#f3f4f6`、`#f9fafb`、`#fff` | `var(--color-base-300)` / `var(--color-base-200)` / `var(--color-base-100)` |
| 强调块底 | `#eff6ff`、`#f5f3ff`、`#6d28d9` | `color-mix(... var(--color-primary) 10%, transparent)`、`--color-accent` 12% 底 + 实色字 |
| 遮罩 | `rgba(15, 23, 42, 0.45)` | `rgb(0 0 0 / 45%)`（中性黑，两套主题通用） |

保留不动的只有三处：视频占位 `#000`（本来就是黑边）、两处 `box-shadow` 的黑色透明（阴影用主题色反而怪）。

---

## 5. 类名映射表

| 场景 | 旧 | 新 |
| --- | --- | --- |
| 按钮 | `.btn` + `.btn--small` | `btn btn-sm`；无色按钮再补 `btn-outline` |
| 主按钮 | `.btn--primary` | `btn btn-sm btn-primary` |
| 危险按钮 | `.btn--danger` | `btn btn-sm btn-error` |
| 次要/取消 | `.btn` | `btn btn-sm btn-outline`（面板上更清晰） |
| 文本输入 | `input.input` | `input input-sm` |
| 下拉 | `select.input` | `select select-sm`（`select` 不能用 `input`） |
| 多行文本 | `textarea.textarea` | `textarea textarea-sm` |
| 面板/卡片 | `div.card` | `div.panel` |
| 商品卡 | `a.card.goods-card` | U0 先 `panel goods-card`；U2 再改标准 `card` + `figure` + `card-body` |
| 表格 | `table.data-table` | `table table-sm`（外层 `overflow-x-auto`） |
| 弱化文字 | `.muted` | `.muted`（`.muted` 保留，等价 `text-base-content/60`） |
| 错误提示条 | 手写红框 div | U0 用 `color-mix` 保底；U2 换 `alert alert-error alert-soft` |
| 状态标签 | `.tag` | `badge badge-sm` + tone 映射（U0 已换） |
| 弹窗 | 自绘 mask + div | `modal modal-open` + `modal-box` + `modal-action`（U1） |
| 消息提示 | 自绘 `.toast` 条目 | `toast toast-top toast-center` + `alert alert-*`（U0 已换） |
| 侧栏菜单 | 自绘 `.layout__link` | `menu menu-sm` + `li` + `menu-active`（U1） |
| 分页 | 一排 `.btn--small` | `join` + `btn join-item btn-sm` / `btn-active`（U1） |

---

## 6. 阶段划分

| 阶段 | 目标 | 完成标志 |
| --- | --- | --- |
| U0 基座 | 装依赖、定主题、删同名旧类、全站机械替换 | build/test 通过；浅色观感不退化；深色可读 |
| U1 公共件 | 侧栏、Toast、状态标签、确认弹窗、分页 | 组件 props/emit 不变，调用方零改动 |
| U2 起步页面 | 登录、商品列表 + 商品卡、商品详情 | 两套主题下均正常，交互不变 |
| U3 其余页面 | 发布、类别、客户、改密 | 同上，表格窄屏可横滚 |
| U4 收口 | 删残留、回归、文档复核 | `npm run build` + `npm test` + 两主题手测通过 |

### U0 技术基座（已完成 2026-11-11）

- **依赖与构建**：`tailwindcss` 4.3.5、`@tailwindcss/vite` 4.3.5、`daisyui` 5.7.50 进 devDependencies；`vite.config.ts` 加 `tailwindcss()`；`package-lock.json` 已更新（Docker 里是 `npm ci`）。
- **CSS 入口**：按 §3.3 重写 `base.css`；`.btn*`、`.input`、`.textarea`、`.card`、`.data-table` 的旧定义全部删除，换成 daisyUI 组件 + `.panel`/`.field*`/`.muted`。
- **全站替换**：16 个 `.vue` 的类名与变量替换完成；`index.html` 加内联主题脚本、`lang` 改 `zh-CN`。
- **主题机制**：新增 `composables/useTheme.ts`（localStorage + 跟随系统 + 监听系统切换）；侧栏底部先放了一个「切换到深色/浅色」的入口，U1 再换成正式开关。
- **实测**：`npm run build` 通过（`index-*.css` 63.06 kB / gzip 10.46 kB），`npm test` 2 条通过；Edge 下登录、商品列表、商品详情、发布、类别、客户、改密七个页面在两套主题下逐个截图核对，控制台 0 报错 0 警告；`body` 底色实测 `rgb(246,243,238)` / `rgb(28,25,21)`，面板与卡片按 base-100 正确分层。
- **顺带做掉的三处**（原计划在 U1，因影响深色可读性提前）：`StatusTag` 换 `badge`、`ToastHost` 换 `toast`+`alert`、占位图 `product.svg` 的 `#3B3F51` 改暖灰 `#a89c8b`。
- **已知遗留**：`ConfirmDialog` 与 `Pagination` 仍是自绘结构（只换了颜色与尺寸）；商品卡还是 `panel` 而非标准 `card` 结构；客户列表的表格没有面板包裹。三项都在 U1/U2/U3 处理。

> 不做兼容层，理由见 §7。

### U1 公共件

| 件 | 改法 | 不能动的东西 |
| --- | --- | --- |
| `layouts/AppLayout.vue` | 侧栏 `bg-base-100 border-r border-base-300`；导航改 `menu menu-sm`，`li > RouterLink`，当前项 `menu-active`；主题入口改日/月图标按钮；退出登录保持 `btn btn-sm btn-outline`；sticky 视口不拉伸的写法保留 | `isActive` 判断逻辑、退出登录流程、`useTheme` 的存储键 |
| `components/common/ToastHost.vue` | **U0 已完成**：`toast toast-top toast-center z-50` + `alert alert-* shadow-lg` | 已保留 `TransitionGroup`、`role="status"`、`data-testid="toast-item"` |
| `components/common/StatusTag.vue` | **U0 已完成**：`badge badge-sm` + tone 映射（default 用 `badge-ghost`，其余 `badge-soft badge-*`） | 已保留 `label` / `tone` props 与 `data-testid="status-tag"` |
| `components/common/ConfirmDialog.vue` | `modal modal-open` + `modal-box` + `modal-action`；`danger` 决定 `btn-error` / `btn-primary`；Esc 关闭 | `open/message/title/confirmText/cancelText/danger/loading` 与 `confirm/cancel` 事件 |
| `components/common/Pagination.vue` | `join` + `btn join-item btn-sm`，当前页 `btn-primary`；总数文案 `text-sm text-base-content/60` | 页码窗口算法、`update:page` 语义 |

**验收**：5 个公共件的对外接口一字不改；两套主题下弹窗、toast、分页、标签都正常；Esc 关闭弹窗、Tab 焦点不退化。

**U1 完成情况（2026-11-11）**

- **侧栏改成可收起的图标栏**：展开 200px（图标 + 文字），收起 64px（纯图标，`title` 提供名称）；宽度用 `transition-[width,padding]` 过渡，状态存 `localStorage['simple-shop.seller.sidebar']`，刷新保持。整体换成 `flex` + daisyUI `menu menu-sm`，当前项用 `menu-active`（daisyUI 的 neutral 实心底，两套主题下对比度都在 8:1 以上）。
- **导航高亮按域归属**：`NavItem.matches` 列出归属前缀，商品详情 `/goods/:id` 与 `/publish` 都高亮「商品列表」；E1 之后新增的订单页届时加一条即可。
- **主题切换改成图标按钮**：`sun` / `moon` 二选一，`aria-label` 与 `title` 说明目标模式（切到的是另一个模式，不是当前模式）。
- **图标组件 `components/common/AppIcon.vue`**：把 `assets/*.svg` 的 path 内联成 `currentColor` 的 `<svg>`。原来的 SVG 是 `fill="#181818"` 固定黑，用 `<img>` 直插在深色主题下就是黑块。18 个图标已登记，`size` 由外层 `text-[18px]` 之类控制。
- **图标映射**：商品列表→`product`、类别管理→`file`、客户管理→`user`、修改密码→`key`、退出登录→`sign-out`（U1 先用 `list`/`edit`/`close` 顶着，U2 拿到正式图标后替换）。
- **确认弹窗换 `modal`**：`modal modal-open` + `modal-box` + `modal-action`，补了 Esc 关闭（提交中不响应），点遮罩仍取消；props/emit 未改。
- **分页换 `join`**：`join` + `btn join-item btn-sm`，当前页 `btn-primary`，总数文案 `text-sm text-base-content/60`；页码算法未动。
- **实测**：`npm run build`、`npm test` 通过；六个路由的导航高亮全部正确；收起与主题切换刷新后保持；Esc 关闭弹窗有效；控制台 0 报错 0 警告。侧栏、弹窗、分页在浅/深两色下逐张截图核对。
- **遗留**：daisyUI 的 `tooltip` 在 `menu` 项里会被裁切（试过 `tooltip-right` 也无效），所以收起态改用原生 `title`。登录后的页面在窄屏（<900px）没有折叠侧栏以外的适配，本次不做。

### U2 起步页面（先做这三处）

1. **登录**：居中 `card` + `w-full max-w-sm`；表单用 `fieldset` + `label` + `input input-sm`；会话过期提示改 `alert alert-warning alert-soft alert-sm`；提交按钮 `btn btn-primary w-full`。
2. **商品列表 + 商品卡**：页头 `flex items-center justify-between`；筛选区 `panel flex flex-wrap items-center gap-2`（`select-sm` / `input-sm` / `btn-sm`）；宫格 `grid gap-4 grid-cols-[repeat(auto-fill,minmax(200px,1fr))]`；`GoodsCard` 改标准 `card` 结构（`figure` 定 `aspect-square`，状态 badge 绝对定位在图上，`card-body p-3`），价格用 `text-error font-semibold`。
3. **商品详情**：分区改 `panel`；状态与下架原因并排两个 badge；库存行 `input input-sm w-24` + `btn btn-sm`；危险动作 `btn btn-error btn-outline btn-sm`；描述块与图集用 `table` / `divider` 组织。

**约束**：只动模板与样式，不动请求、状态、校验；筛选条件进 URL 留给 E2-1。

**U2 完成情况（2026-11-11）**

- **路由过渡**：`AppLayout` 内层 `RouterView` 包 `Transition name="route"`（淡入 + 8px 上浮，120ms，`mode="out-in"`）；`App.vue` 外层包 `Transition name="fade"` 只做登录页↔后台的淡入淡出。过渡类定义在 `base.css` 的 `@layer components`，并加了 `prefers-reduced-motion: reduce` 时直接关闭。
- **登录页**：居中 `card`（`max-w-sm`）+ `fieldset`/`fieldset-legend` 两组表单 + `alert alert-warning alert-soft`（会话过期）+ `btn btn-primary w-full`。右上角新增主题切换按钮——登录页没有侧栏，否则未登录时无法切主题。
- **商品列表**：页头改「标题 + 共 N 件商品 + 发布商品」；筛选行 `panel flex flex-wrap`，状态 `select select-sm w-32`、类别级联 `min-w-[280px] flex-1`、关键词 `input input-sm w-52`、搜索/重置带图标；错误态换 `alert alert-error alert-soft`；宫格 `grid-cols-[repeat(auto-fill,minmax(210px,1fr))]`，加载中降透明度并禁点；空态给了 `product` 图标 + 发布入口。
- **商品卡**：换成标准 `card` + `figure` + `card-body p-3` 结构，主图 `aspect-square`、状态 badge 绝对定位在图上、名称 `line-clamp-2`、价格 `mt-auto text-error`（把不同高度卡片的价格行压到底部对齐）。
- **商品详情**：主面板 `panel flex lg:flex-row`（图 320px + 信息自适应），信息行用 `dl` + `dt w-20 text-xs`，库存与类别行内编辑逻辑原样保留，按钮补图标（`edit`/`file`/`forbidden`）。`daisyUI` 的 `fieldset` 没用在详情页，避免行内编辑再套一层。
- **图标**：新增 `file`（类别管理）、`key`（修改密码）、`sign-out`（退出登录），`AppIcon` 现在共 21 个。
- **实测**：`npm run build`、`npm test` 通过；登录/列表/详情在浅深两色下截图核对；侧栏三个新图标在展开与收起态都单独看过；路由切换时 `route-enter-active` 类确实出现，控制台 0 报错 0 警告。
- **刻意保留**：`goods__stock-input` 这个类名留在库存输入框上，没有任何样式，只是给 E2E 留的稳定钩子。
- **未做**：`ConfirmDialog` 的 `modal` 在窄屏没有做 `modal-bottom` 适配；列表页窄屏（<640px）筛选行会换行堆叠，未做专门的移动端布局。

### U3 其余页面（已完成 2026-11-11）

原计划：发布（表单分组 `fieldset`）、类别管理（表格 + 行内编辑）、客户列表 / 详情（`panel` + `table`）、修改密码（同登录）。

实际落地：

- **类别管理重构**：卡片抽成 `components/category/CategoryCard.vue`，页面用 `grid-cols-[repeat(auto-fill,minmax(230px,1fr))]` 铺开，1280 宽下 **一行 4 张**（每张 246px，比原来整行一张窄了一个量级）。卡片内是一级名称 + 二级列表 + 底部添加行。
  - 二级的「改名 / 删除」改成 `edit` / `delete` 图标按钮（`btn btn-square btn-xs btn-ghost`，24px），不写文字，靠 `title` + `aria-label` 保可访问性。
  - 添加二级：点底部虚线「+」就地展开输入框 + `confirm` 按钮 + `close` 取消，回车提交、Esc 取消。
  - 改名：点铅笔把该行换成输入框（自动聚焦并全选）+ `confirm` / `close`。
  - 一级删除**只在没有二级类别时出现**，有子级时连入口都不给（原来点了才提示「请先删除子类别」）。
  - 删除二次确认沿用 `ConfirmDialog`，未改。
  - 卡片只收集输入，校验与请求仍在页面里：`rename` / `create` 事件带 `done(ok)` 回调，**成功才收起编辑态**，失败保留输入内容。
- **发布**：六个分组统一 `fieldset` + `fieldset-legend`（商品名称 / 价格 / 库存 / 所属类别 / 商品描述），价格与库存在 `sm:grid-cols-2` 里并排，提交按钮 `btn btn-primary`。`ImageFilePicker` 的标签字号从 14px 收到 12px，与 legend 对齐。
- **客户列表**：表格包进 `panel p-0` + `overflow-x-auto`，行 hover 加底色，行尾「详情」换 `arrow-right` 图标按钮。
- **客户详情**：卡片头部加头像圆 + 用户名 + 注册时间，下面 2 列信息；原来「注册时间」出现两次（头部与列表），只保留头部一处。
- **改密**：同登录页形态（`fieldset` × 3 + 全宽主按钮），宽度限 420px。
- **顺带清理**：`base.css` 里的 `.field` / `.field__label` / `.field__error` 已无引用，删除；`grep -rn 'page__\|field__\|class="page"' src` 现在为 0。
- **实测**：类别页新增 → 改名 → 删除全流程走通（新增 1 条 / 改名 1 条 / 弹窗出现 / 删除后消失），控制台 0 报错；四个页面在浅深两色下截图核对；`npm run build`、`npm test` 通过。
- **踩到的坑**：`ref="inputEl"` 写在 `v-for` 里会被 Vue 收集成**数组**，`inputEl.value.focus()` 直接报 `not a function`。改成函数 ref（`setInputEl`，参数类型写 `Element | ComponentPublicInstance | null` 才过类型检查）后正常。

### U4 收口（已完成 2026-11-11）

- **门禁实测**：旧 CSS 变量（`--bg/--surface/--border/--text/--muted/--primary/--danger/--success/--warning/--radius`）**0** 处；旧结构类（`class="page"` / `page__*` / `field__*`）**0** 处；硬编码色值**剩 1 处**——`DescriptionBlocks.vue` 的视频黑底 `#000`，属预期保留。
- **`base.css` 现状**：两套主题 + `.panel` + `.muted` + 四组路由过渡类 + 四条 `@utility`（btn/input/select/textarea 字号）。无孤儿样式。
- **交互修正**：客户列表改成**整行可点**进详情（`cursor-pointer` + `@click` + `tabindex="0"` + Enter 键），行尾箭头按钮降级为装饰（`aria-hidden` + `tabindex="-1"`，点击 `@click.stop` 避免冒泡触发两次）。原来只有 28px 的小箭头能点，命中面积太小。
- **回归范围**：9 条路由 × 浅/深两套主题逐页截图；登录表单错误态（2 条字段错误）、toast（类别页空名称提交）、确认弹窗 + Esc、类别卡片的增改删全流程、整行点击跳转，全部实测通过，控制台 0 报错 0 警告。
- **`npm run build` / `npm test`** 均通过；`index-*.css` 90.74 kB（gzip 14.51 kB）。
- **文档**：`README.md` 的目录结构（新增 `components/category/`、去掉已删的 `intention/`）、第十一节样式说明同步；`AGENTS.md` §5 已在 U0/U2 补充完毕，本轮无新增口径。
- **下一步**：回 E1-3，接回商品两态化的页面口径（原因展示、原因筛选、手动下架库存只读），然后进 E2。

**后续微调（E1-3 / E2 期间，2026-11-11）**：浅色 `base-100` 从 `#ffffff` 改为 `rgb(255 255 255 / 72%)`——纯白在暖色底上偏冷偏硬，半透明叠加后是暖白（≈`#fcfaf7`），卡片与输入框的层次也更柔和；深色主题保持不透明。模态框补了 `backdrop-blur-md`，否则 28% 的透明会让遮罩下的文字透上来影响阅读。所有 `bg-base-100` 的地方（面板、卡片、侧栏、输入框、表格、弹窗）自动跟着变，不需要逐处改。

**叠加层实色（E3-3 期间，2026-11-11）**：半透明的副作用在原生 `<select>` 上很明显——Chrome/Edge 的弹出列表面板沿用 select 的背景色，`<option>` 又是 `transparent`，于是整个下拉弹层是半透明的，背后的卡片和文字直接透上来。修法是给「盖在内容之上」的界面单独一个实色变量：

- `--color-surface-solid`：浅色 `#fcfaf7`（等于 base-100 叠在 base-200 上的观感），深色 `#26221c`（与深色 base-100 同值）。两套主题里都定义，改色时与 base-100 一起改。
- 消费方：`@utility select`（弹出列表跟着 select 走）、`@layer base` 里的 `option`、`@utility modal-box`。以后新增 `dropdown-content` / `toast` 一类浮层照此办理。
- 模态框的 `backdrop-blur-md` 随之去掉：底色已经实色，模糊不再起作用。
- 面板、卡片、侧栏、输入框、表格**不动**，仍是半透明 base-100，观感不变（实测 select 与 input 并排时色差不可辨）。

之所以不用 `backdrop-filter` 解决：原生 `<select>` 的弹出列表由浏览器绘制，CSS 的模糊只作用于闭合的那个框，碰不到弹层；要磨砂就得换成自定义下拉组件（`dropdown` + `menu`），顺带要自己实现键盘导航与点外关闭，暂不值得。

---

## 7. 迁移策略取舍

| 方案 | 做法 | 结论 |
| --- | --- | --- |
| **一次性替换**（采用） | U0 内删旧同名类 + 全站机械替换 | 约 150 处但只改类名与变量，逻辑零改动；改完就是一个稳定基线，后续每页可以独立验收 |
| 兼容桥 | 旧类包进 `.legacy-ui` 作用域，逐页摘除 | 不采用。两套样式系统并存，每改一个页面都要先判断「它受哪套控制」；U4 还得整层删，桥本身会成为第二套需要维护的约定 |
| 全站重写 | 一次重做所有页面 | 不采用。无法分批验收，且会把 E2 的口径改动混进来，出问题分不清是样式还是业务 |

---

## 8. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| 同名类残留 | 换类名后样式不生效，误判为「daisyUI 不好用」 | U0 一次性删净；验收时用 DevTools 确认 `.btn` 只有一条规则来源 |
| 非分层自定义样式 | 工具类调不动组件，最后靠 `!important` | 复合类一律放 `@layer components` |
| 硬编码色 45 处 | 深色下白底/黑字 | U0 用语义色兜底，U1–U3 逐页清零，U4 用 grep 卡门禁 |
| 密度变化 | 一屏信息量变少，后台可用性下降 | 全站统一 `-sm`；U4 用改造前后截图对比列表页行数 |
| 首屏闪白 | 深色用户每次刷新闪一下 | `index.html` 内联脚本，先设主题再渲染 |
| 构建体积 | 两套主题变量全量进 CSS | U0 记录 baseline；超过 100KB（未压缩）再考虑裁剪未用 token。2026-11-11 实测 107.79 kB（gzip 16.61 kB），增量来自订单列表引入的 `tabs`/`table`：gzip 仍小，暂不裁剪 |
| E2E 定位失效 | 4 处 `.tag` 定位报错 | U1 加 `data-testid`；不动遗留用例，卖家 E2E 重写时统一改用 testid |
| Docker 构建 | `npm ci` 缺新依赖会失败 | `package-lock.json` 与 `package.json` 同批提交；镜像不需要额外系统包 |
| 买家端被带偏 | 两端样式约定分叉变混乱 | `AGENTS.md` §5 已写明「不同步」；买家端 `npm run build` 不受影响 |

---

## 9. 验收口径

### 9.1 每个页面都要过的四项

1. 浅色：与改造前信息层级一致，操作位置不变。
2. 深色：无固定色值残留，文字对比度足够，边框可见。
3. 交互：加载态、空态、错误态、禁用态都还在，文案未变。
4. 代码：该页 scoped 样式里没有颜色字面量，没有 `var(--*)` 旧变量。

### 9.2 整体门禁

- `npm run build`、`npm test` 必须实际执行并通过（不是「应该能过」）。
- `grep -r 'var(--' src`、`grep -rE '#[0-9a-fA-F]{3,8}' src` 结果符合 §U4。
- 主题切换后刷新页面，主题保持；清空 localStorage 后跟随系统。

---

## 10. 交付物

- 卖家端样式底座：`base.css`（两套主题 + 复合类）、`useTheme`、`index.html` 内联脚本。
- 5 个公共件与 8 个页面的 daisyUI 化改造。
- 更新后的 `AGENTS.md` §5、`README.md` 第十一节、本文登记区。

---

## 11. 任务完成登记区

> 登记规则同《阶段E》：只改状态/负责人/日期/备注；状态取值 未开始 / 进行中 / 已完成 / 阻塞 / 取消；日期 YYYY-MM-DD。

| 编号 | 任务 | 前置 | 状态 | 完成日期 | 备注 |
| --- | --- | --- | --- | --- | --- |
| U0 | 技术基座（依赖、双主题、同名类替换） | E1-5 | 已完成 | 2026-11-11 | 分支 feature.refactor-phase3；build/test 通过，7 个页面双主题截图核对 |
| U1 | 公共件（侧栏 / Toast / 状态标签 / 确认弹窗 / 分页） | U0 | 已完成 | 2026-11-11 | 侧栏可收起 + 图标、主题改图标按钮、弹窗换 modal（补 Esc）、分页换 join |
| U2 | 起步页面（登录 / 商品列表 + 商品卡 / 商品详情） | U1 | 已完成 | 2026-11-11 | 另加路由过渡；三页均改 daisyUI 结构，图标补齐 file/key/sign-out |
| U3 | 其余页面（发布 / 类别 / 客户 / 改密） | U1 | 已完成 | 2026-11-11 | 类别卡片抽组件并重做为宫格；`.field*` 一并删除 |
| U4 | 收口与回归 | U2、U3 | 已完成 | 2026-11-11 | 门禁 0 残留；客户列表改整行可点；阶段 U 全部结束 |

---

**文档结束**
