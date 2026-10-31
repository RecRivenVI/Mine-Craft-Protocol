# Deep State 当前能力与后续计划

当前实现以 [Native 合同](../reference/protocol-native_v0.md)、Target capability 和源码为准。
本计划不宣布新的验收，也不把诊断样片当作正式 Recording V2。

## 已有基础

- 五个真实 Target 均有 formal Deep Observation、Provider V2、资源局部 Revision 和 session epoch 身份。
- CLIENT_KNOWN 与 SERVER_AUTHORITATIVE 明确分开；未加载目标不得为了观察被自动加载。
- Player、Entity、Block/Block Entity、Chunk loading、Scheduled Tick 以强类型可用性和 provenance 表达；
  Ticket、attachments/capabilities、序列化结构等仍保留 Target-specific detail，不能硬造跨版本等价。
- Provider 的 schema、policy、affinity、预算、超时及 native/fallback revision 由独立合同约束。
  ordered JSON array 不能被通用 canonicalizer 当集合；不提供可信资源 revision 时必须诚实降级。
- Debug 采用 typed namespaces、bounded batch、逐项取消、before/after 和 resource/value preconditions。
  scope、Debug Arm、READ/OPERATE/TAKEOVER 意图、Lease、Evidence authority 正交。
- Chunk/Client/Network 的部分边界仍为 PARTIAL；没有任意反射、raw packet 或任意字段后门。
- Dedicated Peer 已接入服务器签发的连接级委托和批准的 typed 目录；五端具名验证范围见[状态页](status-milestones.md)，不外推完整 Deep Debug ResourceVersion parity。
- Persistent Read 有五端 Target adapter、有界 NBT/Region 只读访问、队列/生命周期约束和明确 stale/consistency。
  `PERSISTED / last_saved_state` 不冒充 LIVE。无调用方任意文件路径。
- Write Safety Foundation 包含稳定 storage identity、offline ownership、前置条件、单文件 commit/recovery 基础。
  Runtime session、storage identity、file snapshot/revision 三层不能混用。
  **正式 storage.write 仍未实现。** 独立 Write Entry Review 是 READY，不是实施授权。
- shared runtime-safety 已进入五端构建/开发运行和最终制品；最终运行证明仍绑定其原始制品提交。

## 保留的诊断实验

`Phase9ASpikeEngine` 名字对应已有实现，不是待删除产品功能。
三个代表 Target 的 bounded Keyframe / snapshot-diff Delta / reconstruction 仍是诊断实验；
不保证长期完整重建，不把推导 delta 冒充原生事件，debug Arrange 不计 gameplay acceptance。
实验入口仅用于明确受控场景，正式 Agent 使用应优先 formal Observation / Storage Read / typed Debug。

## 后续子任务与退出条件

| 范围 | 当前安排 | 必需退出条件 |
|---|---|---|
| Observation / Provider / Revision | 维护已有实现 | 五端合同、no-load、query-independent revision、epoch/lifecycle 与预算不回归 |
| Typed Debug | 维护已有实现 | 权限、Arm、before/after、取消、旧 token 拒绝及 Evidence 分级闭环 |
| Persistent Write | 独立 Entry Review；未开工 | 仅评估 offline + 当前 Target + typed single-file level.dat；DataVersion、ownership、atomicity/recovery 和全部前置条件复核 |
| Recording V2 / Canonical Store（9E） | NOT STARTED | 有界长期状态、来源明确的 Keyframe/Delta、测量驱动 codec、gap/预算/存储失败语义 |
| State Diff / Reconstruction（9F） | NOT STARTED | 离线重建与权威 bounded snapshot 对比，明确 EXACT / EXPECTED_PARTIAL / FAILED 及缺失 instrumentation |
| Five-target alignment / stress（9G） | NOT STARTED | 明确跨端对比、真实负载和清洁制品门禁，不用五个独立 PASS 冒充 parity |
| 高级恢复/诊断（Phase 10） | NOT STARTED | 另行范围和安全审查；不由现有实现自动开启 |

Persistent Write 首版边界仍禁止 playerdata、chunk/Region、.mcc、POI、entity、SavedData、
Dedicated Peer 写入、任意 NBT/path、自动跨版本 DataFix。Windows 原子替换不等于已证明断电事务。
未来任何扩张须重新说明线程、加载副作用、存储所有权、审计、回滚/恢复和一致性。

## 当前验证

[Deep-state conformance](../../validations/conformance-deep_state/validation.md)保留现有产品断言与具名场景。
普通 JUnit/Node 测试留在源码体系。构建/静态通过不构成新的五端 Live，
本轮不执行 Minecraft、人工验收或 Persistent Write。

## 目标

维持本计划已确认的 Core 范围与下一授权门槛。

## 范围

本文件中的状态与产品边界为本项目计划；构建结构以当前配置文档为准。

## 步骤

- 进行中：按以下执行台账推进；不得自动开始未授权阶段。

## 结果

以下保留既有实现、验收与历史发现；结构迁移不提升功能验收状态。
