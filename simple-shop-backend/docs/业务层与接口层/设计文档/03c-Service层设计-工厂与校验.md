<!--
  本文件由《后端业务层与卖家接口层开发方案》v9 拆分而来（2026-10-10）。
  对应：§3.4~§3.7；拆分前位于单文件方案的 第 765–948 行。
  章节编号 §X 与原文【完全一致，未重新编号】——代码中有约 150 处「方案 §X」引用依赖它。
  索引与 §编号 → 文件 的映射表见 ../README.md
-->

# 3.4 实体工厂（绕开 `protected` 构造）

**问题**：`Goods()`、`Intention()` 等无参构造是 `protected`，Service 在别的包无法 `new`。而**发布商品、提交意向、归档、追加流水**都需要新建实体。

**方案：在 `persistence.entity` 包内新增一个 `public` 工厂类**，只暴露业务创建所需的最小入口：

```java
package com.simpleshop.persistence.entity;

/** 仅供 Service 层创建实体的显式入口。
 *  存在理由：实体无参构造为 protected（JPA 规范要求，且避免业务代码误用），
 *  生产代码在其它包无法 new，故在此——实体包内——提供受控工厂。 */
public final class EntityFactory {

    private EntityFactory() {}

    /** 发布商品：只接受业务允许初始化的字段。 */
    public static Goods newGoods(String name, String description, String picUrl, BigDecimal price) {
        Goods g = new Goods();
        g.setName(name);
        g.setDescription(description);
        g.setPicUrl(picUrl);
        g.setPrice(price);
        g.setStatus(GoodsStatus.on_sale);   // 初始状态固定
        return g;                            // id / createAt / updateAt 由 @PrePersist 写
    }

    /** 提交意向：id / createAt 由 @PrePersist 写。 */
    public static Intention newIntention(Goods goods, int queueOrder, String name, String tel, String token) {
        Intention i = new Intention();
        i.setGoods(goods);
        i.setQueueOrder(queueOrder);
        i.setName(name);
        i.setTel(tel);
        i.setToken(token);
        i.setStatus(IntentionStatus.queued);
        return i;
    }

    /** 归档：历史商品 —— 类内没有回调，全部字段由本方法【显式】赋值。
     *  ⚠️ 不接受 Good s 之外的时间参数「自动生成」；tradeEnd / result 由调用方传入。 */
    public static GoodsHistory newGoodsHistory(Goods src, LocalDateTime tradeEnd, GoodsResult result) {
        GoodsHistory h = new GoodsHistory();
        h.setId(src.getId());                        // 沿用原值（前缀仍为 G，9-G）
        h.setName(src.getName());
        h.setDescription(src.getDescription());
        h.setPicUrl(src.getPicUrl());
        h.setPrice(src.getPrice());
        h.setStatus(GoodsStatus.off_sale);           // 恒写 off_sale（9-H）
        h.setFreezeBy(null);                         // 恒清空
        h.setCreateAt(src.getCreateAt());            // 继承原值
        h.setUpdateAt(tradeEnd);                     // 归档时间
        h.setTradeStart(src.getTradeStart());        // 继承原值（未进入交易则为 null）
        h.setTradeEnd(tradeEnd);                     // 必填
        h.setResult(result);                         // 必填：sold / offline
        return h;
    }

    /** 归档：历史意向 —— 逐列复制，且不复制 queueOrder / token（该实体没有这两个属性）。 */
    public static IntentionHistory newIntentionHistory(Intention src, String goodsHistoryId) {
        IntentionHistory h = new IntentionHistory();
        h.setId(src.getId());                        // 沿用原值（前缀仍为 I，9-G）
        h.setCreateAt(src.getCreateAt());            // 原始提交时间，继承
        h.setName(src.getName());
        h.setTel(src.getTel());
        h.setStatus(src.getStatus());                // 归档时必为终态（INV-05 已校验）
        h.setFailType(src.getFailType());
        h.setFailReason(src.getFailReason());
        // h.setGoodsHistory(...) —— 需要 GoodsHistory 实体引用；
        //   见下方「外键的赋值方式」说明
        return h;
    }

    /** 追加交易流水 —— tradeStart / tradeEnd 均由业务显式赋值（两者 NOT NULL）。
     *  ⚠️ 只在「标记交易结果」时调用；enterTrade 时不调用（那时没有 tradeEnd）。 */
    public static TradeHistory newTradeHistory(String intentionId, LocalDateTime start,
                                               LocalDateTime end, TradeResult result,
                                               FailType failType, String failReason) { ... }
}
```

> **外键的赋值方式（`IntentionHistory.goodsHistory`）**：`IntentionHistory` 的关联是 `@ManyToOne`（要的是**实体引用**，不是裸 ID 字符串），所以 `newIntentionHistory` 需要 `GoodsHistory` 实例。两种写法：
> ① **推荐**：签名改成 `newIntentionHistory(Intention src, GoodsHistory goodsHistory)`，直接 `h.setGoodsHistory(goodsHistory)`。因为 `ArchiveService` 里**已经**持有刚 `saveAndFlush` 的 `GoodsHistory` 实例，传入即可，**无额外查询**。
> ② 只拿到 ID 时，用 `entityManager.getReference(GoodsHistory.class, id)` 取代理（**不触发查询**，因为只写外键列）。
> **不要**写成 `goodsHistoryRepository.findById(id).orElseThrow()` —— 那会多一次无谓查询。
> **⚠️ 与 `TradeHistory.intentionId` 的区别**：后者是**裸 `String` 列**（`9-N`，无物理外键、无 `@ManyToOne`），所以流水工厂收的是 `String intentionId`；两者**不可混淆**。

> **为什么不直接把实体构造改成 `public`**：会破坏「业务代码不能随意 `new` 出半初始化实体」的可见性约束，而该约束是数据层刻意设计的（交接说明 §3 的 `TestEntities` 说明即为同一思路的测试侧版本）。
> **为什么不放 Service 包**：`protected` 构造只在同包可见，放 Service 包根本编译不过。
> **本工厂与测试用的 `TestEntities` 是同一个思路的生产侧版本**，两者互不干扰。

**`@PrePersist` 与显式赋值的关系**（易错）：
- `Goods`/`Intention`/`TradeHistory` 的 `@PrePersist` 都是 `if (id == null)` / `if (createAt == null)` —— **只在为 null 时生成**。
- 归档时调用方**显式赋原值**，回调查不到 null 就不会覆盖；这正是 `if` 存在的意义（交接说明 §4.2 的硬约束）。
- **✅ 已定（本方案统一口径）：业务时间一律由 Service 显式写**——`submitIntention` 里显式调 `timeProvider.nowUtc()` 并 `setCreateAt(...)`，**不依赖 `@PrePersist`**。理由：① 时间来源单一且**在代码里看得见**（评审时不必去推断回调何时触发）；② 归档路径本来就必须显式赋值（历史实体无回调），两处口径一致、少一条心智负担；③ `@PrePersist` 的 `if (createAt == null)` 仍作为**兜底**保留（不删实体代码，遵守附录 B）。

### 3.5 一致性与并发要点汇总

| 场景 | 保证手段 | 相关不变量 |
| --- | --- | --- |
| 并发提交意向，序号不重复、不丢单 | **序号表行锁** `findByIdForUpdate(1)` | `NFR-01`、`BR-12` |
| 同时两条意向进入交易 | **商品行锁**串行化（不是条件 UPDATE 抢占） | `INV-02` |
| 交易中意向存在时商品必须在冻结 | 商品行锁 + 状态校验 | `INV-04` |
| 冻结期不接受新意向 | `submitIntention` 内 `status == on_sale` 校验 | `INV-03` |
| 归档时名下意向必须全终态 | 归档前置 `countActiveByGoodsId > 0 → 拒绝` | `INV-05` |
| 同一商品至多 1 条 `succeeded` | 「先他人失败 → 再本人成功 → 最后下架」的固定顺序 | `INV-06` |
| 口令码有效性只看意向终态 | `IntentionStatus.isTokenValid()` = `!isTerminal()`，**唯一判定式** | `INV-07`、`INV-08` |
| 全局至多 1 件在售/冻结 | `existsByStatusIn` 前置校验（**竞态属已接受风险**） | `INV-01` |

### 3.6 字段校验规则（校验责任**全部**在应用层，数据库只管结构）

> 数据库**没有**枚举取值 CHECK、格式 CHECK、区间 CHECK（`9-T` 已明确「值校验不下沉数据库」）。所有「值对不对」由本轮 Service 负责。
> 设计说明书 `DEC-DB-14` 已删除 **18 条** CHECK 约束（含 `ck_goods_status`、`ck_goods_price`、`ck_intention_token_format`、`ck_intention_tel_format`、`ck_intention_fail_type_required`、`ck_trade_history_fail_type_required`、`ck_intention_history_status_terminal` 等）——**这份清单就是本轮 Service 的校验责任清单**。
> **⚠️ Bean Validation 注解的落点（口径澄清）**：设计说明书 `DEC-DB-10` ③ 已定「**实体上不加** Bean Validation 业务注解」（避免 `@Size(max = 50)` 与 `@Column(length = 50)` 双源不一致）。但 **DTO 上加 `jakarta.validation` 注解是完全正确的用法**，也正是 `spring-boot-starter-validation` 的用途。**分界**：
> | 位置 | 是否加校验注解 |
> | --- | --- |
> | `persistence/entity/**` | ❌ **不加**（`DEC-DB-10` ③） |
> | `web/dto/**`、`service/dto/**`（请求 DTO） | ✅ 加 `@NotBlank`/`@Size`/`@Pattern`/`@DecimalMin` 等 |
> | Service 内部 | ✅ **仍须独立校验**——DTO 注解只能拦住 Controller 入参，**不能替代**业务规则校验（`G6-02`：服务端必须独立校验前置条件）。且 Service 也被买家端 JSP（下一轮）直接调用，届时不过 DTO 校验链 |

**统一文本规则**（`§9.2.5`）—— 适用于 `goods.name`、`goods.description`、`intentions.name`、`fail_reason`：

| 规则 | 口径 |
| --- | --- |
| 允许字符 | 英文、中文、数字、空格及常见标点 |
| **拒绝字符** | **`<` 与 `>` 一律拒绝**（完全拒绝，不做转义放行） |
| 首尾空格 | **禁止**：入库前 `trim`；中间空格允许 |
| 换行 | **按字段分别规定**：`description`、`fail_reason` **允许**；`name`（商品/买家姓名）**不允许** |
| 输出侧 | **仍须转义**（`C-15`、`NFR-20`）——输入拒绝是第一层，输出转义是第二层，**不可互补** |

**逐字段校验表**

| 字段 | 必填 | 长度 | 格式 | 错误码 / 文案 |
| --- | :---: | --- | --- | --- |
| `goods.name` | 是 | ≤50 字符 | 统一文本规则 + 禁首尾空格 + **不允许换行** | `20007` / `M10-29` |
| `goods.description` | 否 | ≤500 字符 | 统一文本规则 + **允许换行** | `20008` / `M10-30` |
| `goods.pic_url` | 否 | ≤512 | 由 `I11-05` 返回的服务端相对路径；只接受白名单目录下的既有文件 | `20009` / `M10-31`（若非法则视为未上传或报错，见 §7） |
| `goods.price` | 是 | — | **> 0 且 ≤ 999,999.99**；**两位小数**；用 `BigDecimal`，**禁止 `double`** | `20010` / `M10-32` |
| 图片文件 | 否 | ≤5MB | **JPG / PNG**；最多 1 张 | `20009` / `M10-31` |
| `intentions.name` | 是 | ≤50 字符 | 统一文本规则 + 禁首尾空格 + **不允许换行** | `M10-36` |
| `intentions.tel` | 是 | 7~20 位 | **`^[0-9-]{7,20}$`**（数字与 `-`，兼容座机，已决 `9-L`） | `M10-37` |
| `fail_reason` | 否 | ≤300 字符 | 统一文本规则 + **允许换行**；**买家不可见** | `30007` |
| `disposal` | 是 | — | 枚举 `voided` / `requeued` | `30006` |
| `account` | 是 | ≤50 | 非空 | `50002` |
| `password`（登录） | 是 | — | 非空（**登录侧不设长度限制**） | `50002` |
| `new_password`（改密） | 是 | **≥8** | — | `10004` |
| 口令码输入 | 是 | **12** | `^[A-Z0-9]{12}$`（归一化后校验） | `M10-28`（格式不符，**不发起有效性校验**） |
| `page` / `page_size` | 否 | — | `page ≥ 1`；`1 ≤ page_size ≤ 100`（建议上限，防全量拉取） | `50002` |

**价格处理要点**：
- 入参是**字符串**（`"1234.00"`），Service 用 `new BigDecimal(str)` 解析并 `setScale(2, RoundingMode.UNNECESSARY)`——**多余小数位直接拒绝**，不做静默四舍五入。
- 出参也是**字符串**（`toPlainString()`），**绝不用 `double`/`float`**（`11-F`、`C-12`）。

**时间处理要点**：
- 全部 `LocalDateTime`（UTC）。Jackson 序列化为 ISO 8601 带 `Z`：`2026-10-05T12:34:56Z`（`11.3.1`）。
- **服务端不下发本地时间**（`C-10`、`C-11`）——**东八区转换是前端的事**。
- **禁止**任何 `LocalDateTime.now()`。唯一来源 `DatabaseTimeProvider`。

### 3.7 三条易混操作的区分（必须写进代码注释）

| 操作 | 改商品 | 改意向 | 什么时候用 |
| --- | :---: | :---: | --- |
| **手动解冻**（`PS-05`） | 是（→`on_sale`） | **不碰任何意向** | 商品被**手动冻结**、卖家想恢复在售 |
| **标记交易失败**（`PS-04`） | 是（→`on_sale`） | **裁决那一条 `trading` 意向**（作废/重排） | 与**某位买家**的交易正式结束 |
| **买家撤销**（`IS-03`） | 否 | 该意向 → `revoked` | 买家侧操作，**卖家无法触发** |

> **✅ 已定（Q-1）：手动冻结的商品必须先解冻，才能标记交易结果。**
>
> **推导**（把三处前置条件串起来）：
> - `PS-01`（进入交易）要求商品 `on_sale`，且置 `freezeBy = trade`；
> - `PS-02`（手动冻结）只要求 `on_sale`（**不要求队列清空**），置 `freezeBy = manual`；
> - 因此：**手动冻结的商品既不能进入交易**（`PS-01` 要 `on_sale`），**也不存在 `trading` 意向**（唯一的产生入口就是 `PS-01`）。
> - ⇒ 而 `markTradeSuccess` / `markTradeFailure` 的前置都要求「目标意向处于 `trading`」——**手动冻结的商品根本满足不了**。
> - ⇒ **结论**：手动冻结的商品，**唯一出口是「手动解冻」**（`PS-05`）。想走交易流程，必须先解冻 → 进入交易（此时 `freezeBy` 变 `trade`）→ 再标记结果。
>
> **对代码的三点影响**：
> 1. `enterTrade` 要求 `goods.status == on_sale` —— **不加任何放宽**（不变）；
> 2. `markTradeSuccess` / `markTradeFailure` **严格要求 `goods.freezeBy == trade`** —— 这是**本次 Q-1 定下的收紧**（原稿只校验 `status == frozen`）。该分支实际不可达，作为**防御性断言**保留：真被触发说明存在「`frozen(manual)` 却有 `trading` 意向」的数据不一致，应当拒绝而非放行；
> 3. `unfreezeGoods` 要求 `freezeBy == manual` —— 不变（`PS-06`）。
>
> **⚠️ 与「手动冻结不解冻也能结束交易」的区别**：本次明确**不存在**这条路。这带来一个**业务上的现实约束**（值得写进交付说明）：**手动冻结会挡住交易流程**——若卖家在有人排队时手动冻结，必须自己记得解冻，否则队列会一直停着。`PS-02` 允许「在售即可冻结、不要求队列为空」，意味着**这种阻塞态是被需求允许的**（不是缺陷）。

---

