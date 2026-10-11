<!--
  本文件由《后端业务层与卖家接口层开发方案》v9 拆分而来（2026-10-10）。
  对应：§3.3（3.3.1~3.3.8）；拆分前位于单文件方案的 第 313–764 行。
  章节编号 §X 与原文【完全一致，未重新编号】——代码中有约 150 处「方案 §X」引用依赖它。
  索引与 §编号 → 文件 的映射表见 ../README.md
-->

# 3.3 各 Service 的方法清单与业务口径

#### 3.3.1 `SellerAuthService` — `FR-01`、`FR-02`

```java
LoginData login(String account, String password);
void logout(String token);
void changePassword(String token, String oldPassword, String newPassword);
```

| 方法 | 步骤 | 错误码 |
| --- | --- | --- |
| `login` | ① `findByAccount(account)`；② **账号不存在或 `PasswordHasher.matches` 失败 → 同一个 `10001`**（统一提示，不区分，`M10-23`）；③ 生成 token，写入 `SessionStore`，有效期 30 分钟 | `10001`、`50002` |
| `logout` | 从 `SessionStore` 移除 token（幂等） | `10002` |
| `changePassword` | ① `findByAccount(当前会话的账号)`；② `matches(old, hash)` 失败 → `10003`；③ 新密码长度 < 8 → `10004`；④ `hash(new)` 写入 `ShopUser.password`；⑤ **清空该账号的全部会话**（`DEC-17`、`NFR-04`：旧凭证 ≤5s 失效） | `10002`、`10003`、`10004`、`50002` |

> **不做**：登录失败次数限制（`DEC-25`）、图形验证码（`DEC-26`）、找回/重置密码（`DEC-06`）。
> `ShopUser.updateAt` 的语义是「密码最后修改时间」，由实体 `@PreUpdate` 刷新；当前实体只有 `password` 一个可变字段，故等价（交接说明 §4.5）。**本轮不改实体**。

#### 3.3.2 `SellerGoodsService` — `FR-03` ~ `FR-06`、`FR-23`

```java
GoodsData         getCurrentGoods();                       // FR-04 → I11-04
PublishResultData publishGoods(PublishGoodsCommand cmd);   // FR-03 → I11-06
GoodsData         freezeGoods();                           // FR-05 → I11-07
GoodsData         unfreezeGoods();                         // FR-06 → I11-08
void              takeGoodsOffline();                      // FR-23 → I11-09
```

> **命名以本节下方与 §4.6.2 为准**：响应类型是 `GoodsData`（§4.6.2 的契约名），
> 不是初稿里的 `CurrentGoodsData`；`publishGoods` 返回 `PublishResultData` 而非裸 `String`
> （路由层之外的调用方也应能直接拿到 DTO 字段名）。已按此实现。

**`getCurrentGoods()`**（只读）
- `goodsRepository.findFirstByOrderByCreateAtAsc()`；空 → 返回 `null`（Controller 下发 `data: null`，前端走空态）。
- 下发字段：`id`、`name`、`description`、`pic_url`、`price`（**字符串两位小数**）、`status`、`freeze_by`（`on_sale` 时为 `null`）、`create_at`、`trade_start`（**仅内部字段，页面不展示**）。
- **`freeze_by` 必须下发**：`P10-07` 靠它判断「解冻」按钮是否可用（`FR-04` 注、`PS-06`）。

**`publishGoods(cmd)`**
1. **前置校验**：`goodsRepository.existsByStatusIn({on_sale, frozen})` 为 `true` → 抛 `20001`（`M10-24`）。
2. **字段校验**（§3.6 校验器，失败分别抛 `20007`/`20008`/`20010`）。
3. **图片路径校验**：`pic_url` 若非空，须是本次上传返回的合法相对路径（防任意路径注入，见 §7.3）。
4. 创建 `Goods`：`name`、`description`（可空）、`picUrl`（可空）、`price`（`BigDecimal(scale=2)`）、`status = on_sale`、`freezeBy = null`。`createAt`/`updateAt` 由 `@PrePersist` 写入。
   - **⚠️ 绝不调用 `setResult(...)`**（该列恒为 NULL，`DEC-DB-11`）。
5. `goodsRepository.save(goods)`；返回新 `id`。
6. 记操作日志（`G6-01`：发布商品虽不在 8 类必列清单中，但属关键写操作，一并记）。

**`freezeGoods()`**
1. `getCurrentGoods()`，空 → `20002`（空态）。
2. `findByIdForUpdate(id)` 取商品行锁；`status != on_sale` → `20003`。
3. `status = frozen`、`freezeBy = manual`。
4. **不校验队列是否为空**（澄清 Q18，`PS-02` 明确「在售即可冻结」——这是**曾经写错的地方**）。
5. 返回更新后的商品 DTO；记日志。

**`unfreezeGoods()`**
1. `findByIdForUpdate(id)`，空 → `20002`；`status != frozen` → `20004`。
2. **`freezeBy == trade` → `20005`**（`M10-15`）：交易冻结**禁止**手动解冻（`PS-06`、`BR-07`）。**这是 `FIX-01` 的服务端兜底防线，不得依赖前端禁用**。
3. `status = on_sale`、`freezeBy = null`。**不碰任何意向**（与「标记交易失败」的区别见 §3.7）。
4. 记日志。

**`takeGoodsOffline()`**
1. `findByIdForUpdate(id)`，空 → `20002`（当前无商品）；`status != on_sale` → `20003`。
2. **队列为空校验（`BR-09`、`DEC-01`、`PS-08`）—— 判定式必须用 `queued`，不能用「非终态」**：
   ```java
   // ✅ 正确：队列 = queued 意向；"队列为空" = 无 queued
   boolean queueEmpty = intentionRepository
           .findByGoodsIdAndStatusOrderByQueueOrderAsc(goodsId, IntentionStatus.queued)
           .isEmpty();
   if (!queueEmpty) throw new BusinessException(ErrorCode.QUEUE_NOT_EMPTY);   // 20006 / M10-16
   ```
   - **⚠️ 不要用 `countNonTerminal(goodsId, {succeeded, failed, revoked}) == 0` 判定**：它把 `trading` 也算作「非空」。虽然 `INV-04` 保证 `on_sale` 时不可能有 `trading` 意向（所以两者在本方法里**结果等价**），但**语义不同**——`PS-08` 的前置条件是「**队列**为空」，而「队列」在 §9.5.1/§9.5.3 中**专指 `queued`**。用错判定式会让后来改代码的人误以为「`trading` 也算队列」。
   - **`trading` 的兜底交给 `INV-05`**：如果因某种原因 `on_sale` 却存在 `trading` 意向，第 3 步的 `ArchiveService` 归档前置校验（`countActiveByGoodsId > 0`）会**拒绝并回滚**（`30005`）。**不需要**在本方法里额外写一遍。
3. **调用 `ArchiveService.archive(goods, GoodsResult.offline, nowUtc())`**（同一事务内），见 §3.3.6。
   - ⚠️ **`tradeEnd` 由 `ArchiveService` 显式写给历史实体**（= 下架时间）。**不要**写当前表的 `trade_end`（当前表该列连同整行随即被删除，保持它恒为 NULL 的口径更干净，与 `result` 恒 NULL 一致）。
   - ⚠️ **`result = offline` 只写在 `GoodsHistory` 上**，**绝不调用 `goods.setResult(...)`**（`DEC-DB-11`、R8）。
   - 手动下架**不产生任何交易流水**（没有任何意向进入过交易；`§9.9.5` 已注明 `offline`「无任何意向进入交易，故不产生流水」）。
4. 记日志（`NFR-12` 8 类之一：手动下架）。

#### 3.3.3 `SellerIntentionService` — `FR-07` ~ `FR-10`、`FR-24`

```java
IntentionPageData listIntentions(int page, int pageSize);   // FR-08 → I11-10
void enterTrade(String intentionId);                        // FR-07 → I11-11
void markTradeSuccess(String intentionId);                  // FR-09 → I11-12
void markTradeFailure(String intentionId, String disposal, String failReason); // FR-10 → I11-13
String getPasscode(String intentionId);                     // FR-24 → I11-14
```

> **⚠️ S6 实现时对下述口径的补充与澄清（已按此实现；不影响签名）**
>
> | # | 项 | 定稿口径 |
> | --- | --- | --- |
> | 1 | **无当前商品时的 `20002` 也适用于 `I11-12`／`I11-13`** | §4.3 的「接口」列只把 `20002` 列给了 `I11-07`／`I11-11`，但本节三个方法的步骤 1 都写了「空 → `20002`」。**以本节为准**（§4.3 那一列是**未穷举**）——与 S4 对 `I11-08`／`I11-09` 的处理**同一口径**。已在真实端口实测 |
> | 2 | **`markTradeFailure` 里字段校验的落点** | `disposal`（`30006`）与 `fail_reason`（`30007`）的校验放在**「取当前商品」之后、第一次取锁之前**：既满足本节「必须在第一次触碰仓储之前完成」的**目的**（非法值不能走错分支、不能先取错锁），又保持错误码的出现顺序与本节步骤编号一致。（**实测成对证据**：无商品 + `disposal=bogus` → `20002`；有商品 + 同输入 → `30006`） |
> | 3 | **`I11-10` 的位次只对 `trading` 置 `null`** | 按本节与第 11 章 §11.5 的原文：`rank = countQueuedAhead + 1`，**仅 `trading` 为 `null`**。⚠️ 但终态意向（`revoked`／`failed`）**已退出队列**（§9.5.3 的位次定义只覆盖 `queued`），它显示的只是「该序号前面还有几个排队的人」。**已登记为口径观察项（§8.11 O-1）**；若上游确认终态行应为 `null`，改 `rankOf()` 一行即可 |
> | 4 | **`enterTrade` 的 `30003` 不可达** | 队首是按 `queued` 查出来的，`findByIdForUpdate(队首 id)` 必然拿回同一条 `queued` 意向；单商品模型下「不属于当前商品」也不可能出现。故该分支是**纯防御**，本实现**不伪造用例**去覆盖它（见 §8.11 O-4）。`30004` 则**可以**用「在售却有 `trading` 意向」的夹具真测，已有用例 |
> | 5 | **`getPasscode` 不加锁** | 它是只读路径。读到之后即使该意向并发变成终态，本次返回的仍是「刚刚还有效」的码——与「先返回后失效」的正常时序不可区分，无正确性问题 |

**`listIntentions(page, pageSize)`**（只读）
- 当前商品为空 → 返回「商品为空」的空结构（Controller 可返回 `data: null` 或空名单，按 §4.5 定）。
- `intentionRepository.findByGoodsId(goodsId, PageRequest.of(page-1, pageSize, Sort.by("queueOrder").asc()))`。
- **⚠️ 排序必须显式传 `Sort.by("queueOrder").asc()`**（仓储不内建排序，约束 **C7** 于 §1.2）。
- **`rank`（位次）计算**：对每条**非 `trading`** 的意向，`rank = queue_order 升序在全表 queued 中的秩`。
  - 精确算法：`rank = countQueuedAhead(goodsId, queueOrder) + 1`（`§9.5.3`）。
  - **`trading` 意向 `rank = null`**（不占位次，`§9.5.3`、`I11-10`）。
  - **性能提示**：分页 10 条 × 1 次计数 = 10 次 SQL，在 P95 ≤ 500ms 内可接受（`12-P1`）。若日后名单变大，可改为「一次性取 `queued` 的 `queue_order` 列表 + 内存排名」。
- 出参：`total`、`page`、`page_size`、**`queue_count`**（= `countNonTerminal(goodsId, 终态集合)`，即 `10-D` 要求的队列计数，上限 1000）、`items[]`。
- `items[]` 每行：`id`、`rank`、`queue_order`、`name`、`tel`、`create_at`、`status`、`fail_type`、`fail_reason`。
  - **⚠️ 不下发 `token`**（`11-D`：口令码须经 `I11-14` 单独查询）。
  - **⚠️ 不下发 `trade_start`**（澄清 Q22：只记录、不展示）。

**`enterTrade(intentionId)`** — `PS-01` + `IS-02`
```
@Transactional
1. goods = goodsRepository.findFirstByOrderByCreateAtAsc()   // 非锁读，仅为取 id
   空 → 20002
2. goods = goodsRepository.findByIdForUpdate(goods.id)        // ② 商品行锁（必须）
   status != on_sale → 20003
3. head = intentionRepository.findFirstByGoodsIdAndStatusOrderByQueueOrderAsc(goods.id, queued)
   空 → 30002（无队首）
4. 若 intentionId != head.id → 30002（非队首，BR-12「先到先得」的服务端防线）
5. it = intentionRepository.findByIdForUpdate(intentionId)     // ③ 意向行锁
   it 为空或 it.status != queued → 30003
6. it.goods.id != goods.id → 30003
7. 再校验 INV-02：countByGoodsIdAndStatus(goods.id, trading) > 0 → 30004
   （商品行锁已把并发串行化，此处为防御性校验）
8. now = timeProvider.nowUtc()
   goods.status = frozen; goods.freezeBy = trade; goods.tradeStart = now
   it.status = trading
9. 记日志（关键操作）
```
> **注意**：`goods.tradeStart` 在**进入交易**时写；历史展示只用 `trade_end`（澄清 Q22）。
> **其余 `queued` 意向保持不变**（`BR-01`）——**不要**在此处改动它们。

**`markTradeSuccess(intentionId)`** — `PS-03` + `IS-04` + `IS-05`
```
@Transactional(timeout = 5)   // ⚠️ 全系统影响面最大的写操作，必须整体一个事务
1. goods = findFirstByOrderByCreateAtAsc(); 空 → 20002
2. goods = findByIdForUpdate(goods.id)                        // ② 商品行锁
   goods.status != frozen → 20004
   goods.freezeBy != trade → 20004
   // ✅ 已定（Q-1）：手动冻结的商品「必须先解冻才能标记交易结果」，
   //    因此这里【必须】严格要求 freezeBy == trade。
   //    该分支实际不可达（trading 意向只可能由 enterTrade 产生、而 enterTrade 要求
   //    on_sale 并把 freezeBy 置为 trade），故它是【防御性断言】——
   //    若真被触发，说明存在另一条把商品置为 frozen(manual) 却产生 trading 意向的路径，
   //    属数据不一致，应当拒绝而非继续。
   //    ⚠️ 用 20004（商品非已冻结）而非新错误码：契约的错误码表里没有更贴切的编号，
   //      且 §11.6.1 明确规定「新增错误码须落在所属域内」，不得擅自加码。
3. it = intentionRepository.findByIdForUpdate(intentionId)     // ③ 意向行锁
   it 为空或 it.status != trading → 30003
   it.goods.id != goods.id → 30003        // 防「意向与当前商品不匹配」
4. now = timeProvider.nowUtc()
   ── ① 先：其余 queued 意向 → failed，failType = sold_out（IS-04）
      for (other : findByGoodsIdAndStatusOrderByQueueOrderAsc(goods.id, queued))
          other.status = failed; other.failType = sold_out
          // ⚠️ 这些意向【不追加流水】：IS-04 是「他人成交」的连带效果，
          //    不是「对该意向登记交易结果」。流水追加时机见 §9.7.2（仅标记成功/失败两条路径）。
          //    注：§9.9.6 的流水 result 只取 sold/failed，不区分 sold_out/voided，
          //    若为 sold_out 也追加流水，会与「最后一次交易结果」语义重复。
          //    §9.7.2 明确写入时机只有「标记交易成功」「标记交易失败」两处，
          //    故此处不追加 —— 本方案按 §9.7.2 执行。
   ── ② 再：本次意向 → succeeded（IS-05）
      it.status = succeeded; it.failType = null; it.failReason = null
   ── ③ 追加本次成交的流水（result = sold）
      tradeHistoryRepository.save(EntityFactory.newTradeHistory(
          it.id, goods.tradeStart, now, TradeResult.sold, null, null))
      // ⚠️ tradeStart 来自 goods（进入交易时写入并在归档前一直保留在原行）
   ── ④ 最后：商品归档入历史（PS-03）
      archiveService.archive(goods, GoodsResult.sold, now)
      // archive 内部会把 goods.status 置 off_sale 并复制到 GoodsHistory；
      // 当前表的 trade_end/result/status 一律不写（§3.3.6）
5. 记日志
```
> **顺序不可调换**（`BR-03`、`N-03`）：先他人失败 → 再本人成功 → **追加流水** → 最后下架。该顺序保证「下架时名下所有意向均已终态」（`INV-05`）。
> **流水必须在 `archive` 之前追加**：`archive` 会 `deleteAll` 掉当前意向行，若之后才追加，虽然流水表无物理外键（不会报错），但事务内的持久化上下文已混乱、且逻辑上「流水归属的意向已不在当前表」难以自证。
> `30005`（`INV-05` 校验失败）由 `ArchiveService` 抛出——若在归档前仍有非终态意向则整体回滚。

**`markTradeFailure(intentionId, disposal, failReason)`** — `PS-04` + `IS-06`/`IS-07`
```
@Transactional(timeout = 5)
1. goods = findFirstByOrderByCreateAtAsc(); 空 → 20002
2. ★ 若 disposal == requeued：先取序号表行锁（⚠️ 必须在商品行锁【之前】）
   //    统一加锁顺序：① 序号表 → ② 商品行 → ③ 意向行（交接说明 §5.2）
   //    disposal 进入方法即已知，故可安全地把这把锁提前，避免与
   //    submitIntention（先序号表）构成反向取锁 → 死锁（风险 R3）
   seq = queueSequenceRepository.findByIdForUpdate(1)
   seq 为空 → 50000（部署错误：序号表未初始化）
3. goods = goodsRepository.findByIdForUpdate(goods.id)         // ② 商品行锁
   goods.status != frozen → 20004
   goods.freezeBy != trade → 20004
   // ✅ 已定（Q-1）：同 markTradeSuccess 的严格校验，理由见该处说明
4. it = intentionRepository.findByIdForUpdate(intentionId)      // ③ 意向行锁
   it 为空或 it.status != trading → 30003
   it.goods.id != goods.id → 30003
5. now = timeProvider.nowUtc()
   ── 追加流水（本次失败）：result = failed，failType = disposal，failReason = failReason
      tradeHistoryRepository.save(EntityFactory.newTradeHistory(
          it.id, goods.tradeStart, now, TradeResult.failed, disposal, failReason))
      // ⚠️ 无论作废还是重排队，都记一条（9-D、§9.7.2）
   ── 商品 → on_sale（PS-04）；freezeBy 清空（与 unfreezeGoods 一致）
      goods.status = on_sale; goods.freezeBy = null
      // ⚠️ goods.tradeStart 保留不清 —— 它是「本次交易开始时间」，
      //    下一次进入交易会覆盖它；历史展示只用 trade_end（澄清 Q22）
   ── 裁决：
      if (disposal == voided) {
          it.status = failed; it.failType = voided; it.failReason = failReason;
      } else {  // requeued
          // ⚠️ 关键：从 trading 直接回 queued，不经过任何终态（BR-27）
          next = seq.getCurrentValue() + 1; seq.setCurrentValue(next)
          it.queueOrder = next;                  // 序号刷新至队尾
          it.status = queued;
          // ⚠️ fail_type 保留为 requeued（DEC-DB-10 ①：重排队后 fail_type【保留】，
          //    作为「最近一次失败处理方式」的快照）；fail_reason 保留本次备注
          it.failType = FailType.requeued;
          it.failReason = failReason;
          // create_at 保持不变（BR-20、DEC-15）
          // token 不动 → 口令码保持有效（BR-27、INV-08）
      }
6. 其余 queued 意向状态不变、位次前移（位次是计数派生，无需任何 SQL）
7. 记日志
```

> **⚠️ 「重排队后 `fail_type` 保留 `requeued`」是设计说明书 `DEC-DB-10` ① 的明确口径**（原文：「`requeued` 后意向回到 `queued`，但 `fail_type` **保留 `requeued`**（作为『最近一次失败处理方式』的快照）」）。这**不是**矛盾——`fail_type` 在**非终态**意向上的语义是「最近一次交易失败的处置方式」，而不仅是「终态时的失败原因」。实现时**不要**把它清成 `null`。
> **与「意向状态」的对应**（设计说明书 §9.7.2「与意向状态的一致性」）：同一 `fk_intention_id` 的**最后一条**流水的 `result`/`fail_type` 应与意向表的 `status`/`fail_type` 对应。重排队场景下：最后一条流水 `result = failed`、`fail_type = requeued`，意向表 `status = queued`、`fail_type = requeued` ✅ 一致。
> 相应地，`markTradeSuccess` 把本次意向置 `succeeded` 时，`fail_type`/`fail_reason` 应清空（终态 `succeeded` 无失败语义）；但若该意向此前有重排队历史，`trades[]` 中仍完整保留每一次失败（澄清 Q15）。
> **⚠️ 加锁顺序（本方案最容易写错的一处，风险 R3）**：`submitIntention` 的取锁顺序是「**先序号表**、不锁商品」；而 `markTradeFailure` 的「重新排队」分支也需要序号表行锁。若在本方法里**先取商品行锁、再取序号表行锁**，就与 `submitIntention` 构成**反向取锁**，并发下**死锁**。
> **本方案的处理**：因为 `disposal` 在进入方法时即已知，把序号表行锁**提前到商品行锁之前**（见伪代码的步骤 2，标注 ★）。这样两条路径的取锁顺序一致，都是「① 序号表 → ② 商品行 → ③ 意向行」（交接说明 §5.2）。
> **实现要点**：`if (disposal == requeued)` 判断必须在**第一次触碰仓储之前**完成；`disposal` 的取值校验（`30006`）也应在此之前做，否则非法值会走错分支。
> **回归测试**：必须写一个**并发**用例——同一条意向「重新排队」与另一买家「提交意向」并发执行，断言两者都不超时、不死锁。

**`getPasscode(intentionId)`** — `FR-24`
- `findById(intentionId)`（普通查询即可，无需锁，只读）。
- 不存在 → `30008`。
- `!it.status.isTokenValid()`（即终态）→ `40002`（口令码已失效，**不展示**）。
- 返回 `token` **原文**；**记操作日志**（`NFR-12`：8 类关键操作之一——**但日志不得记录码值**，只记「谁在何时查看了哪条意向的口令码」，`12-H`）。

#### 3.3.4 `SellerHistoryService` — `FR-11`

```java
PageData<HistoryGoodsItemData> listHistory(int page, int pageSize);   // I11-15
HistoryGoodsDetailData getHistoryDetail(String goodsId);              // I11-16
```

**`listHistory(page, pageSize)`**
- `goodsHistoryRepository.findAllByOrderByTradeEndDesc(PageRequest.of(page-1, pageSize))`。
- **排序已内建在方法名中**（`trade_end` 倒序，`DEC-29`），调用方**不要**再传 `Sort`（否则覆盖）。
- `page_size` 默认 **10**；**不支持任何筛选**（`FR-11`）。
- **⚠️ 历史为空时返回【空页】而不是 `null`**（S8 实现时明确）：`total = 0`、`items = []`，`code = 0`。
  与 `I11-04`／`I11-10` 的空态**刻意不同**——`data: null` 在本项目里一直是「**当前无商品**」的专用表达（已定 Q-2），
  而「还没卖过东西」是**正常初始状态**，不是错误、也不表示「没有商品」。混用会让前端多一个语义含糊的分支。
- 每行：`id`、`name`、`create_at`、`trade_end`、`result`（`sold`/`offline`）。
  - **⚠️ 不展示 `trade_start`**（澄清 Q22）。

**`getHistoryDetail(goodsId)`**
- `goodsHistoryRepository.findById(goodsId)`；空 → `20011`（**HTTP 404**，归商品域，**不得越域占用 `4xxxx`**）。
- 商品信息：`id`/`name`/`description`/`pic_url`/`price`/`status`（恒 `off_sale`）/`create_at`/`trade_end`/`result`。
- `intentions[]`：`intentionHistoryRepository.findByGoodsHistoryIdOrderByCreateAtAsc(goodsId)`。
  - **⚠️ 按 `create_at` 升序**（`11-H`、`P10-09`）。
  - **⚠️ 不含、也不得新增 `queue_order` 与 `token`**（`11-I`）——历史实体**根本没有**这两个属性，编译期即保证。
  - 每行：`id`、`name`、`tel`、`create_at`、`status`、`fail_type`、`fail_reason`、**`trade_count`**、**`trades[]`**。
- **`trade_count` 与 `trades[]` 的取数（避免 N+1）**：
  1. 收集 `intentionIds`；
  2. **一次** `countGroupedByIntentionIdIn(ids)` 得 `Map<String, Long>`（**结果中缺失 = 0**，约束 C8）；
  3. 取流水：仓储**只提供按单个 `intentionId`** 的 `findByIntentionIdOrderByTradeStartAsc(...)`。逐条调用即 **N 次 SQL**。
     - **本方案的取舍**：**先用逐条查询**，因为它**不需要改动数据层交付物**（附录 B 的原则）；`I11-16` 是「点进某个历史商品」的详情页，实战中名单规模远小于 `I11-10` 的 1000 条上限（历史商品都是「小规模经营」留下的，每件商品的实际成交/失败次数也很少），N 通常是个位数。
     - **必须留的两条后路**：① 代码注释写明这个 N+1 点；② **在 §8.4 的性能用例里实测**（1000 条意向 + 100 条历史的条件下），若 `I11-16` 的 P95 超 500ms，则**在数据层新增** `List<TradeHistory> findByIntentionIdInOrderByTradeStartAsc(Collection<String>)`（一次查询取回全部，再在内存按 `intentionId` 分组）——这是**新增查询方法**，不违反附录 B（附录 B 禁止的是**改动既有方法**，新增只读查询方法只需在评审时说明理由）。
     - **不要**用 `countGroupedByIntentionIdIn` 的结果去拼 `trades[]`：它只给条数，**给不出每次的时间与失败类型**。`trade_count` 与 `trades.length` 必须**分别取、然后断言相等**（§8.3 第 13 条）。
  4. `trades[]` 每元素：`trade_start`、`trade_end`、`result`、`fail_type`、`fail_reason`。
  5. **`trade_count` 直接取 `trades.length()`，而不要用第 2 步的 `Map`**——两者理论恒等，取前者可以**从根上避免**「Map 缺失被当成 0 但实际有流水」这类不一致；第 2 步的 `Map` 仅在需要**不查流水就算总数**的优化路径里才用得上。**本方案默认不调 `countGroupedByIntentionIdIn`**，直接在 `trades` 上取长度（少一次查询）；若日后改用批量查询优化，再启用它并加断言。
- **⚠️ `trades[]` 是本接口的关键字段**（澄清 Q15、`BR-22`）：只返回最终结果**不满足**业务方要求。
- **⚠️⚠️ `trades[]` 的时间顺序在同一秒内没有保证（S7 实测发现，S8 实现时必须知道）**：
  `simpleshop_trade_history.trade_start` 是 `datetime`（**秒**精度，V1 脚本未带 `(3)`），
  因此**同一秒内**对同一意向产生的多条流水，其 `trade_start` **完全相同**，
  而 `findByIntentionIdOrderByTradeStartAsc` 对这些行的排序**不确定**（InnoDB 上通常表现为插入顺序，
  但那只是实现细节）。`findFirstByIntentionIdOrderByTradeStartDesc`（「最后一条流水」）同理。
  - **本方案的口径**：`trades[]` 逐条返回即可，**不得**在文档或界面上声称「严格按时间先后排列」。
    `BR-22`／`§10.4.5 P10-09` 要的是「**每一次失败都能查到**」（内容），不是排序保证。
    ⚠️ 相应地，**断言 `trades[]` 顺序的用例会偶发失败**（S7 的归档用例第一版就是这样翻车的）——
    写用例时应断言「两条都在、内容正确」，而不是「第一条是 X」。
  - **若要严格时序**：须把 `trade_start`/`trade_end` 改成 `datetime(3)` 并**新增**一个迁移脚本
    （改 V1/V2 会导致 Flyway 校验和失配，附录 B 禁止）——属**数据层变更**，须单独走评审。
    本方案**不做**，理由：真实场景下「同一秒内完成两轮『进入交易 → 标记失败』」几乎不可能，
    而收益只是展示顺序更严谨。已登记为 §8.12 的 **O-1**。

#### 3.3.5 `BuyerIntentionService` — `FR-12` ~ `FR-22`（**无 Controller**）

```java
GoodsData                        browseCurrentGoods();                                 // FR-12/FR-19
BuyerResult<SubmitResultData>    submitIntention(name, tel, clientKey);               // FR-13/FR-14
PasscodeQueryResult              queryByToken(rawToken);                             // FR-15/FR-22
BuyerResult<Void>                updateIntentionContact(rawToken, newName, newTel);  // FR-16
BuyerResult<Void>                revokeIntention(rawToken);                          // FR-17
```

> **⚠️ 上述签名相对初稿有三处修正（S5 实现时定稿，已按此实现）**
>
> | # | 初稿 | 定稿 | 原因 |
> | --- | --- | --- | --- |
> | 1 | `submitIntention(name, tel)` | `submitIntention(name, tel, **clientKey**)` | `NFR-17` 要求「同一 IP ≤10 次/分钟」，而初稿**没有任何客户端标识**，限流无从算起。这是原稿的遗漏。参数取名 `clientKey` 而非 `ipAddress`：Service 不该知道 HTTP，由调用方传「用于限流的客户端身份」 |
> | 2 | 失败返回 `void`／靠「买家端结果码」 | 统一返回 **`BuyerResult<T>`**（`Ok` / `Rejected(BuyerPrompt)`） | 初稿没规定失败**怎么表达**。买家端没有卖家端那层 `GlobalExceptionHandler`（§11.6.2：服务端渲染、不走业务码），故用**密封返回值**，让下一轮 JSP 能以 Java 21 穷尽 `switch` 处理——漏分支即编译不过 |
> | 3 | 「`空 或 goods.status != on_sale` → `B11-01` / `M10-13`」 | **拆成两条**：冻结（手动/交易）→ `GOODS_FROZEN`/`M10-01`；无商品或非在售 → `GOODS_UNAVAILABLE`/`M10-13` | §11.6.2 的 `B11-01` 条件明确是「**商品处于交易冻结**」；「商品没了/已下架」是另一回事且文案不同。**不擅自给 `M10-13` 编 `B11-11`**——编号只由上游分配 |
>
> 详见实现过程记录 §8.10 的 B-3／B-6／D-4。

> **命名说明**：`LoginData`、`GoodsData`、`SubmitResultData` 与 §4.6.2 的响应 DTO 同名同构（`SubmitResultData` = `{token, intention_id}`，供 JSP 成功页渲染口令码）。`PasscodeQueryResult` 是**买家端专用**的密封类型（下一轮的 JSP 直接用），**不经过 JSON 序列化**，故不列入 §4.6.2。`queryByToken` 的「格式不符」分支（`M10-28`）由调用方先调 `PasscodeService.isWellFormed` 判定，**不进 Service 的查询方法**。

**`submitIntention(name, tel)`** — `IS-01`
```
@Transactional
1. 字段校验：name 非空/≤50/统一文本规则/不允许换行；tel 匹配 ^[0-9-]{7,20}$
   失败 → 各自的买家端结果码（B11-03，文案 M10-11/M10-36/M10-37）
2. goods = goodsRepository.findFirstByOrderByCreateAtAsc()
   空 或 goods.status != on_sale → B11-01 / M10-13（商品状态已变化）
   ⚠️ 冻结期（含手动冻结与交易冻结）一律拒绝（INV-03、BR-02、BR-14）
3. seq = queueSequenceRepository.findByIdForUpdate(1)        // ① 序号表行锁（必须）
   seq 为空 → 部署错误（50000）
4. 队列上限校验（INV：NFR-07、DEC-23）
   count = intentionRepository.countNonTerminal(goods.id, {succeeded, failed, revoked})
   count >= 1000 → B11-02 / M10-12（不新增意向行）
   ⚠️ 必须在持序号表行锁后计数，否则并发下可能突破 1000
5. next = seq.currentValue + 1;  seq.currentValue = next
6. token = passcodeService.generateUnique()   // 12 位 [A-Z0-9]，唯一性见 §3.3.7
7. it = EntityFactory.newIntention(goods, next, name, tel, token)
   it.status = queued; it.failType = null; it.failReason = null
   it.createAt = timeProvider.nowUtc()   （或由 @PrePersist 写；见 §3.4 注）
   intentionRepository.save(it)
8. 商品状态不变（BR-13 —— 提交意向不等于冻结商品）
   ⚠️ 绝不写 goods.status
9. return new SubmitResultData(token, it.id)
```
> **注意**：`countNonTerminal` 的终态集合必须恰好是 `{succeeded, failed, revoked}`，即 `EnumSet` 中 `isTerminal()==true` 的那些。
> **注意**：`DEC-DB-13` 的「卖家写操作事务超时 5s」在本方法上**不适用**（本方法是**买家**写操作）；但买家提交意向同样是热点写，**建议一并设 `timeout = 5`**，保持全项目一致。

**`queryByToken(rawToken)`** — 三态（`BR-28`、`DEC-11`）
```
* 归一化：rawToken.trim().toUpperCase()（10-G、R12-06）
* 格式前置校验：不匹配 ^[A-Z0-9]{12}$ → B11-09 / M10-28（由调用方先调
  PasscodeService.isWellFormed 判定，【不进入本方法】；见 §3.3.7）
1. it = intentionRepository.findByToken(normalized)
   空 → 错误口令（B11-04 / M10-05）
2. it.status.isTerminal() → 失效口令（B11-05 / M10-06）
   ⚠️ 只说已失效，不说明原因、不返回姓名/电话/商品信息（BR-29、NFR-13）
   ⚠️ 判定式只用 status.isTokenValid()，绝不掺入商品状态（BR-26、INV-07）
3. queued  → 位次 = countQueuedAhead(it.goods.id, it.queueOrder) + 1（B11-06 / M10-07）
   trading → 已进入交易，无位次（B11-07 / M10-08）
```
> **返回值必须是密封的三态**（`sealed interface PasscodeQueryResult`，三个 record 实现：
> `NotFound` / `Expired` / `Active(rank, trading)`），**从类型上让「失效时顺便带出买家信息」无法表达**。
> **⚠️ `Expired` 与 `NotFound` 必须只带一个「提示类型」字段、不带任何买家或商品字段**——这是 `BR-29`（失效不说明原因、不返回买家信息）的**类型级**落实，比「记得别塞字段」可靠。
> **`Expired` 内部也不得携带 `status`/`failType`**：否则调用方（下一轮的 JSP）有可能把它渲染出来，等于绕过 `BR-29`。

**`updateIntentionContact(rawToken, newName, newTel)`** — `FR-16`
```
@Transactional
1. 归一化 + findByTokenForUpdate(normalized)     // ③ 意向行锁
   空 → 错误口令；status.isTerminal() → 失效口令（拒绝修改，BR-31）
2. 字段校验（同 submit）
3. 只改 name / tel
   ⚠️ 不动 queueOrder（不影响位次，澄清 Q1/Q5）
   ⚠️ 不动 createAt（BR-20、DEC-15）
   ⚠️ 不动 status / token
```
> **两项均可独立修改**：`newName`/`newTel` 传 `null` 表示该项不改。

**`revokeIntention(rawToken)`** — `IS-03`
```
@Transactional
1. 归一化 + findByTokenForUpdate(normalized)     // ③ 意向行锁
   空 → 错误口令；status.isTerminal() → 失效口令
2. status != queued → 拒绝（B11-08 / M10-14：trading 不可撤销，BR-19）
3. status = revoked; failType = revoked（FailType.revoked「买家撤销」，isAuto=true）
4. 位次自动前移（计数派生，无需 SQL）
```
> **买家侧无「作废」「重新排队」**（`BR-18`、`O-08`）——Service **不提供**这两个入口给买家。

#### 3.3.6 `ArchiveService` — 归档事务（Service 层实现，数据层只给实体与仓储）

```java
void archive(Goods goods, GoodsResult result, LocalDateTime tradeEnd);
```

> **⚠️ 本方法只做「搬迁」，不做「流水追加」。** 流水由 `enterTrade`/`markTradeSuccess`/`markTradeFailure` 按业务时机追加（见下方「职责边界」）。

**契约（交接说明 §6）**

```
【前置】必须在同一事务内、在商品行锁保护下调用
【职责边界】本方法只负责：INV-05 校验 → 写历史表 → 删当前表。不追加流水。

1. INV-05 前置校验（归档前必做，第一步）
   active = intentionRepository.countActiveByGoodsId(goods.id, {queued, trading})
   active > 0 → 抛 30005（拒绝归档，整体回滚）
   ⚠️ 数据库侧无任何兜底（ck_intention_history_status_terminal 已删，DEC-DB-14），
      这是唯一的拦截点

2. 取该商品全部意向（含终态），按 queue_order 升序
   intentions = intentionRepository.findByGoodsIdOrderByQueueOrderAsc(goods.id)

3. 先把 goods.status 置为 off_sale（仅内存态，供下面第 5 步复制）
   ⚠️ 本表实际持久化不落该值（9-H：off_sale 只出现在历史表）

4. ★ 先写【历史商品】并 flush —— 必须早于历史意向
   goodsHistory = EntityFactory.newGoodsHistory(goods, tradeEnd, result)
   goodsHistoryRepository.saveAndFlush(goodsHistory)
   ⚠️⚠️ 顺序不可颠倒：simpleshop_intentions_history 上有物理外键
        fkey_intention_history_fk_goods_history_id → simpleshop_goods_history(id)
        ON DELETE RESTRICT（交接说明 §8 的 V1 脚本）。
        若先插历史意向，会直接撞外键约束 → 归档整体失败。
        （S7 已实测：把 saveAndFlush 挪到意向写入之后 → 10 个归档用例里 6 个失败。）
        ⚠️ 而「只 save 不 flush 会撞外键」这个说法**不成立**（S7 实测：换成 save、代码顺序不变，
        归档用例全绿）——用 saveAndFlush 是为了**不依赖 Hibernate 的 INSERT 排序**，
        它是显式保证，不是某个已观测缺陷的修复。
   复制的列：
     - id/name/description/picUrl/price/createAt  ← 1:1 同名同类型复制
     - updateAt  ← 归档时间（= tradeEnd）；不要复制原 update_at
     - status    ← 恒写 off_sale
     - freezeBy  ← 恒清空（null）
     - tradeStart ← 继承原值（未进入交易的商品为 null）
     - tradeEnd  ← 调用方传入（必填）
     - result    ← 调用方传入（必填：sold / offline）

5. 再写【历史意向】（逐条，save 即可，无需逐条 flush）
   for (it : intentions)
     EntityFactory.newIntentionHistory(it, goodsHistory)   // 传【实体】而非 ID
   ⚠️ 该实体是第 4 步已 flush 的 GoodsHistory 实例，直接引用，无额外查询
   复制的列：id、createAt、name、tel、status、failType、failReason 逐列复制
     - id      ← 沿用原值（前缀仍为 I，9-G）
     - createAt ← 继承原值（历史实体无回调，必须显式赋值）
     - fkGoodsHistoryId ← goodsHistory.getId()（= 原商品 ID，值不变）
     - ⚠️ 不复制 queueOrder、token（该表根本没有这两列，DEC-DB-05、9-S）

6. 最后删【当前表】（先子后父，顺序也无硬约束但建议如此）
   intentionRepository.deleteAll(intentions)
   goodsRepository.delete(goods)
   ⚠️ 不要用 deleteAllInBatch：它会绕过持久化上下文，导致上面已被管理的
      实体仍被 Hibernate 认为是持久的，提交时可能重新 INSERT。用 deleteAll。

7. 事后断言（仅集成测试，生产路径不写）
   intentionHistoryRepository.countActive({queued, trading}) 应恒为 0
```

**职责边界：流水在哪一步追加（**易错，必须写清**）**

| 业务动作 | 谁追加流水 | 时机 |
| --- | --- | --- |
| `enterTrade` | `SellerIntentionService` | **进入交易时**（流水需要 `trade_start`；`TradeHistory.tradeStart`/`tradeEnd` 均 NOT NULL，所以**进入交易时不能写流水**——见下方说明） |
| `markTradeSuccess` | `SellerIntentionService` | 在调 `archive` **之前**，对本次 `trading` 意向追加 `result = sold` 的一条 |
| `markTradeFailure` | `SellerIntentionService` | 在改状态**之前/同时**，追加 `result = failed`、`failType = disposal` 的一条 |
| **`takeGoodsOffline`** | **不追加任何流水** | 手动下架时没有任何意向进入过交易（`§9.9.5` 注明 `offline` 不产生流水） |
| **`ArchiveService`** | **一条都不追加** | 它只搬迁；把流水追加塞进归档会导致「谁追加」的语义混乱 |

> **⚠️ 为什么 `enterTrade` 不追加流水**：`simpleshop_trade_history` 的 `trade_start` 与 `trade_end` **都是 `NOT NULL`**（V1 脚本）。进入交易时只有 `trade_start`、没有 `trade_end`，**无法插入**。因此流水一律在「**标记交易结果**」时追加——这与 `§9.7.2` 的「写入时机：① 标记交易成功；② 标记交易失败（无论作废还是重排队）」**完全一致**。
> **流水数量核对**：某意向经历「进入交易 → 标记失败(重排) → 进入交易 → 标记失败(作废)」应有 **2** 条流水；`I11-16` 的 `trade_count` = 流水条数 = 2。

> **历史实体的时间列必须继承原值**：`GoodsHistory`/`IntentionHistory` **一条回调都没有**（交接说明 §4.5），刻意如此——归档是**数据搬迁**，若挂了 `@PreUpdate` 会让二次保存覆盖归档时间。**绝不可**「为了让时间自动填上」给历史实体加回调。

**归档时的 `goods.tradeEnd` / `goods.result`**：当前表也有 `trade_end` 与 `result` 两列。归档路径下**当前表那一行随即被删除**，且 `9-H`/`DEC-DB-11` 已定「该表的 `off_sale` 与 `result` 都不写」；`result` 恒 NULL 是**硬约束**（R8）。**本方案统一：当前表的 `trade_end`、`result`、`status` 一律不写**，三者的值只在 `GoodsHistory` 上出现。

#### 3.3.7 `PasscodeService`

```java
String generateUnique();                 // 12 位 [A-Z0-9]
static String normalize(String raw);     // trim + toUpperCase(Locale.ROOT)
static boolean isWellFormed(String raw); // 内部先归一化，再匹配 ^[A-Z0-9]{12}$
```

> **⚠️ 两处实现口径澄清（S5 定稿）**
> 1. **`isWellFormed` 内部先归一化**：§3.6 对该字段的原文是「`^[A-Z0-9]{12}$`（**归一化后校验**）」。
>    把归一化并进来后，调用方可以直接喂用户原样输入；否则**忘了归一化**会把 `" abc123def456 "`
>    判成「格式不符」（`B11-09`），而查库时它又能命中——同一输入在两处得到相反结论。
> 2. **`toUpperCase` 必须显式传 `Locale.ROOT`**：无参版本用**默认区域**，
>    土耳其语下 `"i"` → `"İ"`（不是 `"I"`），会让含小写 `i` 的口令码在该环境下**静默查不到**。
>    已用「临时改默认区域」的用例钉住。
> 3. **唯一性**：`generateUnique` 按上表做「生成 → `findByToken` 查重 → 重试（上限 5）」；
>    **但「捕获 `DataIntegrityViolationException` 做一次重试」有意未实现**——唯一约束冲突会让事务
>    进入 rollback-only，同事务内重试必然得到 `UnexpectedRollbackException`。
>    数据一致性仍由 `uk_intention_token` 保证（失败安全），撞码概率约 `2×10⁻¹⁹`。
>    详见实现过程记录 §8.10 的 D-2。

| 项 | 口径 |
| --- | --- |
| 字符集 | `A–Z` + `0–9`（36 个），**不排除易混字符 `0/O/1/I`**（`DEC-07`、`R12-06`） |
| 长度 | 12（`C-16`、`BR-25`） |
| 生成 | `SecureRandom`（推荐）或 `ThreadLocalRandom`；**不用** `UUID` 截断（可读性与分布都更差） |
| 唯一性 | **数据库 `uk_intention_token` 是唯一防线**（`S7-02`）。生成时先 `findByToken` 查重 → 若存在则重试（上限 5 次）；**并捕获 `DataIntegrityViolationException` 做一次重试**（把唯一索引当作真正的保证） |
| 归一化 | Service 在**接收与写入两侧**都 `trim().toUpperCase()`（`R12-06`、`10-G`、`DEC-DB-08`）。**不得依赖** `utf8mb4_unicode_ci` 的大小写不敏感 |
| 日志 | **`token` 绝不出现在任何日志中**（`Intention.toString()` 已刻意省略）。**Service 与 Controller 一律不得打印实体或 token** |

#### 3.3.8 `OperationLogService` — `NFR-12`

```java
void log(String operationType, String targetType, String targetId);
```

| 项 | 口径 |
| --- | --- |
| 8 类必列操作 | 冻结、解冻、手动下架、进入交易、标记交易结果、作废/重新排队、**查看意向口令码**、修改密码（`NFR-12`） |
| 每条内容 | 操作时间（UTC）、操作类型、目标对象（`NFR-12` 可测指标 ②） |
| 实现 | **应用日志**（SLF4J + logback 文件 appender），**不纳入数据库表结构**（`9-B`、`12-G`） |
| 保留期 | **3 个月**，由 logback 的 `TimeBasedRollingPolicy` 配置 `maxHistory` 落地（`12-P7`） |
| ⚠️ 禁止 | **不记录口令码明文**（`12-H`）；不记录密码（明文或哈希）。「查看口令码」只记「谁看了哪条意向」 |
| 建议 | 用独立 logger 名（如 `com.simpleshop.audit`）+ 独立 appender，便于运维检索与删除 |

