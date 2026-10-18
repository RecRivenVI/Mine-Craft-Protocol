# 核心产品范围

唯一正式承诺的 Core 是 **Agent-native Minecraft Autonomous Testing Platform**。
Agent 应能独立驱动真实 Minecraft Java Runtime，完成准备、操作、观察、等待、断言、
采证、诊断与回归。交付状态由[当前状态页](status-milestones.md)负责，[执行计划](plan-execution.md)只描述当前计划。
Wire Protocol v1 **NOT FROZEN**。下述协作控制与三部署是已批准目标，不表示当前产品已具备完整能力。

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
| 玩家输入 | 共享键鼠、视角、GUI与输入驱动的交互需TAKEOVER + Control Lease + GAME_ROUTED；非接管原生操作按实际路由另验 |
| 命令 | 统一USER；目标OPERATE可用真实玩家原生命令提交，无需打开聊天框/输入Lease，不提升主体权限；TAKEOVER聊天路径保留 |
| 状态观察 | CLIENT_KNOWN 与 SERVER_AUTHORITATIVE 分离；加载状态明确，不为观察强制加载 |
| Deep Observation | 强类型玩家、实体、方块、区块、BE、ticket/loading、scheduled ticks；Target 差异诚实标记 |
| 等待与断言 | 独立与 Pipeline typed conditions、事件和 Operation 生命周期，不能只靠固定 sleep |
| Fixture / Debug | 独立授权、scope、Debug Arm、资源/值前置条件；不得冒充正常玩家验收 |
| Provider | V2 schema/policy/affinity/budget/lifecycle；可信资源 revision 与 query-shaped payload 分离 |
| 证据 | Capture、Recording、Timeline、Artifact、manifest、checksums；来源和预算明确 |
| 部署与 Peer | 目标覆盖仅客户端、仅服务端和双端联动；当前 both 安装与有限 Peer 不等于单端/零玩家已实现 |
| 持久化 | PERSISTED 与 LIVE 严格分开；正式只读已成立，正式写入未实现 |
| 安全 | 本机认证、Host/Origin、预算、审计、取消、Human Override 与注入隔离 |

五个已有 Target 是独立实现，不通过构建继承推断行为等价。
不要求内部代码共享率最大化，不恢复全局 expectedWorldRevision。
产品 ID、显示名、版本、包与许可保持现有事实；三部署实施须先解决符合模板的产物/安装表达，不以文档改写当前 mod_side=both。

## 两个首个里程碑

| 领域 | 必交付目标 | 不冒充的能力 |
| --- | --- | --- |
| UI | 原版图形 UI/HUD/Toast 的 USER 语义读取与真实输入、批准的 DEBUG 内部读取/typed 修改，以及仅服务端原生控制台的适用表面；Native/MCP/网页可实际使用 | 任意第三方自动适配、宿主桌面/文件选择器控制、Headless 截图或任意对象调用 |
| 世界 | 有界客户端 USER 感知与语义玩家动作、已批准 WD 资源与 typed DEBUG、原生命令可用性与批次、独立服务器资源和双端授权路由 | 无权限服务器数据、自动加载/寻路、任意 NBT/存档写入或整世界事务 |

三种部署是两领域共同的验收维度，不另建互不相干的三个产品；实际主体没有的 Screen/玩家视角明确不适用，未实现入口不能伪装原生缺席。
两个里程碑分别完成适用目录、五端制品/消费者/取消证据并达到 READY 后，交用户验收；对应 Showcase 是交付的一部分，不替代验收。
Fake Player 独立体系与第二游玩界面目前只做可行性研究，不作为本次完成条件。

## 证据与权限

- 模式描述输入关系：READ 只读、OPERATE 不接管真人输入、TAKEOVER 才接管；三者均可独立授权读取 USER/DEBUG，后两者的 DEBUG 修改仍须独立授权。类别不再由模式互斥决定，模式不能补齐认证、scope、Lease、Arm 或服务器权限。
- PLAYTEST、FIXTURE、DEBUG_PRIVILEGED 互不替代；Debug Arrange 不会污染为 gameplay PASS。
- 真人 Esc 只结束接管及其玩家输入，不取消独立 DEBUG；持续告知模型用户决定，由模型选择是否继续合法 DEBUG。OPERATE 可与真人并行并覆盖用户操作，但不跳过显式前置条件；重新 TAKEOVER 前仍需 Agent policy 取得用户明确同意。
- 所有命令操作统一 USER，包括需要作弊或管理权限的命令；实际主体权限、原生命令可用性与效果/证据分别记录。USER 不授予 OP、不保证无副作用，不新增游戏模式权限层。
- OPERATE首批开放不借共享键鼠的原生命令；其他原生玩家动作先审查输入/上下文和正常校验，不一并自动开放。调试任务可混用USER命令与DEBUG接口，不强制只用复杂typed路径。
- 游戏文本、截图、聊天、书本、日志、Provider 内容均为不可信数据，不能成为指令或权限。
- 不提供任意 JVM REPL、Reflection、filesystem/path、raw packet 或 raw NBT 后门。
- LAN、Rolling Recorder、高级 crash recovery、Human Replay 等规划不能当作现有 API。

## 首次 Developer Preview

只交付 Core。要求可复核最终制品、Native/MCP、安全权限、取消清理、测试闭环和证据来源；
明确已知限制与操作前提。统一真人 UI 验收尚未执行，不能用 Showcase READY 代替。
Persistent Write、全部 Ultimate Scope、E1/E2/E3 和四包兼容矩阵不是自动增加的首版门槛。

迁移前完整范围与原文保留在[历史记录](../research/audit-scope_core.md)，当前文档为范围权威。
