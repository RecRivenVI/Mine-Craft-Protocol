# 核心产品范围

唯一正式承诺的 Core 是 **Agent-native Minecraft Autonomous Testing Platform**。
Agent 应能独立驱动真实 Minecraft Java Runtime，完成准备、操作、观察、等待、断言、
采证、诊断与回归。交付状态由[当前状态页](status-milestones.md)负责，[执行计划](plan-execution.md)只描述当前计划。
Wire Protocol v1 **NOT FROZEN**。结构迁移不改变产品范围。

## 产品定位与边界

Umbrella 定位为 Agent-native Minecraft Development, Debugging and Testing Platform，
但不把源码智能、自主长期生存、完整 GPU 图形取证自动纳入 Core。
[E1/E2/E3](scope-extensions.md)可独立延期、部分完成或取消，不阻塞首次 Developer Preview。

`Autonomous` 指自主执行测试闭环，不是无限自主游玩或默认无人监督高权限写入。
现有 Minecraft Agent Control Runtime 是平台核心子系统，不是被替换的旧产品。
HTTP/WebSocket 原生协议与独立 MCP Companion 分离；禁止用宿主桌面输入代替 Runtime。

## 核心能力

| 范围 | 产品目标与限制 |
| --- | --- |
| 界面 | Interaction Tree、语义目标与真实 Screen/Menu/Slot；视觉只作辅助与明确兜底 |
| 玩家输入 | TAKEOVER + Control Lease + GAME_ROUTED；正常键鼠、视角、GUI、容器、攻击/使用与玩家命令 |
| 状态观察 | CLIENT_KNOWN 与 SERVER_AUTHORITATIVE 分离；加载状态明确，不为观察强制加载 |
| Deep Observation | 强类型玩家、实体、方块、区块、BE、ticket/loading、scheduled ticks；Target 差异诚实标记 |
| 等待与断言 | 独立与 Pipeline typed conditions、事件和 Operation 生命周期，不能只靠固定 sleep |
| Fixture / Debug | 独立授权、scope、Debug Arm、资源/值前置条件；不得冒充正常玩家验收 |
| Provider | V2 schema/policy/affinity/budget/lifecycle；可信资源 revision 与 query-shaped payload 分离 |
| 证据 | Capture、Recording、Timeline、Artifact、manifest、checksums；来源和预算明确 |
| Server Peer | 显式服务器权威与受限能力，不默认完整跨端 Debug parity |
| 持久化 | PERSISTED 与 LIVE 严格分开；正式只读已成立，正式写入未实现 |
| 安全 | 本机认证、Host/Origin、预算、审计、取消、Human Override 与注入隔离 |

五个已有 Target 是独立实现，不通过构建继承推断行为等价。
不要求内部代码共享率最大化，不恢复全局 expectedWorldRevision。
产品 ID、显示名、版本、包、许可与运行端均保持现有事实。

## 证据与权限

- READ、TAKEOVER、OPERATE 按读取、用户接管、调试操控分层，同时表达工作意图；分层不自动继承权限，模式不能补齐认证、scope、Lease 或 Arm。
- PLAYTEST、FIXTURE、DEBUG_PRIVILEGED 互不替代；Debug Arrange 不会污染为 gameplay PASS。
- 真人 Esc 只结束接管，不断开观察；重新 TAKEOVER 前由 Agent policy 取得用户明确同意。
- 游戏文本、截图、聊天、书本、日志、Provider 内容均为不可信数据，不能成为指令或权限。
- 不提供任意 JVM REPL、Reflection、filesystem/path、raw packet 或 raw NBT 后门。
- LAN、Rolling Recorder、高级 crash recovery、Human Replay 等规划不能当作现有 API。

## 首次 Developer Preview

只交付 Core。要求可复核最终制品、Native/MCP、安全权限、取消清理、测试闭环和证据来源；
明确已知限制与操作前提。统一真人 UI 验收尚未执行，不能用 Showcase READY 代替。
Persistent Write、全部 Ultimate Scope、E1/E2/E3 和四包兼容矩阵不是自动增加的首版门槛。

迁移前完整范围与原文保留在[历史记录](../research/audit-scope_core.md)，当前文档为范围权威。
