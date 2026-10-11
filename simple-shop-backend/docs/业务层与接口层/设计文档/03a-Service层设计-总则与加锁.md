<!--
  本文件由《后端业务层与卖家接口层开发方案》v9 拆分而来（2026-10-10）。
  对应：§3.1~§3.2；拆分前位于单文件方案的 第 280–312 行。
  章节编号 §X 与原文【完全一致，未重新编号】——代码中有约 150 处「方案 §X」引用依赖它。
  索引与 §编号 → 文件 的映射表见 ../README.md
-->

# 3. Service 层设计

### 3.1 分层与事务边界（总原则）

| 原则 | 说明 |
| --- | --- |
| **事务边界在 Service 方法上** | 用 `@Transactional`（写）与 `@Transactional(readOnly = true)`（读）。**Controller 不加事务注解** |
| **卖家写操作设 5 秒事务超时** | `@Transactional(timeout = 5)`（`DEC-DB-13` 已定：「卖家写操作事务超时 **5s**」，防行锁长时间持有）。`@Transactional.timeout` 的单位是**秒**，写 `5` 即 5 秒 |
| **一个业务动作 = 一个事务** | 每个会改变状态的卖家操作对应**恰好一个** `public` 方法，全部状态变更在**同一事务内**完成 |
| **行锁只在事务内** | 三种锁：序号表行、商品行、意向行。**统一加锁顺序：① 序号表行 → ② 商品行 → ③ 意向行**（防死锁） |
| **先锁后读，再校验，最后写** | 「读状态 → 校验迁移合法性 → 写新状态」必须在**持锁**状态下完成；不得先用非锁查询做校验 |
| **不返回实体** | Service 对外返回 DTO；实体不出 Service 边界（避免 OSIV 关闭后的 `LazyInitializationException`） |
| **异常即回滚** | 业务拒绝抛 `BusinessException`（`RuntimeException` 子类），触发回滚；**读路径**抛异常不影响数据 |
| **时间只有一个来源** | `DatabaseTimeProvider.nowUtc()`（注入后调用）。**禁止** `LocalDateTime.now()`、`new Date()`、`System.currentTimeMillis()` |

### 3.2 加锁矩阵（每个写方法的**第一把锁**）

| Service 方法 | ① 序号表行 | ② 商品行 | ③ 意向行 | 依据 |
| --- | :---: | :---: | :---: | --- |
| `submitIntention`（买家提交） | ✅ `findByIdForUpdate(1)` | ❌ **不锁商品** | ❌ | 交接说明 §5.2 |
| `revokeIntention`（买家撤销） | ❌ | ❌ **不锁商品** | ✅ `findByTokenForUpdate` | 交接说明 §5.2 |
| `updateIntentionContact`（买家改信息） | ❌ | ❌ | ✅ `findByTokenForUpdate` | 保护与并发状态变更的一致性 |
| `enterTrade`（进入交易） | ❌ | ✅ **必须首先锁** | ✅ `findByIdForUpdate` | `INV-02`、`INV-04` |
| `markTradeSuccess`（标记成功） | ❌ | ✅ | ✅ | `INV-05`、`INV-06` |
| `markTradeFailure`（标记失败） | ❌ | ✅ | ✅ | `PS-04`、`IS-06`/`IS-07` |
| `freezeGoods`（手动冻结） | ❌ | ✅ | ❌ | `PS-02` |
| `unfreezeGoods`（手动解冻） | ❌ | ✅ | ❌ | `PS-05`、`PS-06` |
| `takeGoodsOffline`（手动下架） | ❌ | ✅ | ❌ | `PS-08`、`INV-05` |
| `publishGoods`（发布商品） | ❌ | ❌（此时无商品行可锁） | ❌ | `INV-01` 的竞态属**已接受风险**（`DEC-DB-06`），见 §3.5 |
| 全部只读方法 | ❌ | ❌ | ❌ | 不加锁 |

> **`publishGoods` 为什么不锁**：`INV-01` 在数据库侧**不设唯一约束**，也无行可锁（`.`）；`DEC-DB-06` 已把「并发发布竞态」登记为**已接受风险**，理由：系统**只有一个管理员账号、不提供注册入口**，不存在第二个操作者。**对策**：仍按 `existsByStatusIn({on_sale, frozen})` 前置校验，并**在操作日志中记录**；若将来引入多管理员，须回头重评（`INV-01` 的复核触发条件）。

