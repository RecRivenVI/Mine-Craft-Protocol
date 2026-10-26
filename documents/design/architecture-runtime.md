# Runtime 架构

Mine-Craft-Protocol 是 Agent-native Minecraft Autonomous Testing Platform 的执行核心。
原生 Runtime HTTP/WebSocket 与独立 Node MCP Companion 分离，五个 Target 是 siblings，
没有跨 Target 的活动 Minecraft 对象或构建继承。当前实现/验收见[状态页](../project/status-milestones.md)，推进步骤见[执行计划](../project/plan-execution.md)。

## 组件与执行流

| 边界 | 职责 |
| --- | --- |
| versions/<target> | Minecraft/Loader adapter、Mixin/Accessor/Invoker、owner-thread capture 与 typed mutation |
| runtime_safety（product） | 已证明可共享的纯 Java 控制、指针、取消、Capture queue 与存储安全基础 |
| runtime_core（product） | [共享协议执行与策略](architecture-runtime_core.md)、Provider SPI/注册、资源版本、录制与通用服务接口 |
| protocol_schema（tool） | 未冻结 V0 OpenAPI 与生成 Java/TypeScript models，生成输出不冒充已接入 Runtime 依赖 |
| companion（tool） | 独立 MCP stdio surface、协议客户端、有界流读取、static descriptions 与注入隔离；可选本机网页界面工作台复用认证 Native 客户端 |
| validation_tools（tool） | 现有验证驱动、Showcase、报告与迁移 JAR 对照，不合并进产品 |

两个产品组件按模板 product 机制直接合并进每个最终 JAR，开发运行由正规 Loader classpath 接入；
不再声明旧 Jar-in-Jar/include 为当前打包事实。旧 packaged attestation 仅证明原制品。
目录/构建架构见[构建机制](../../gradle/conventions/specification/design/architecture-build.md)。

## Runtime 与线程

认证 HTTP worker 只解析有界请求并调度不可变 DTO；Client state 在 Client Thread，
render resource 在 Render Thread，server state 在 Server Thread，存储 IO 在有界 worker。
Pipeline / Operation 支持取消、deadline、终态与输入清理；任务离开 owner thread 不意味着队列可以无界。

Interaction Tree 递归读取原生 Screen/container/list children，结合 Menu slots 与具名创造标签/配方书
适配形成结构化语义目标；资源包图标、特殊菜单和书本/告示牌用 Target-specific 原生适配。
没有鼠标文本框的编辑器用 keyboard-only 路由，不虚构几何；页码/选项身份进入 stale guard。
Widget 与 Slot 不能混为内部 Reflection。私有 Vanilla 嵌套按钮只能用精确具名只读 Accessor/marker，
Hook 门禁保留封闭的文件/目标白名单，不允许任意字符串或第三方目标。
节点有界、列表边界裁剪、选择器递归；输入前重验实际对象与几何，自绘未知区域报告限制。
通用交互区域补足滚动条、富文本、建议、进度画布与配方展开层，显式记录原生输入优先级和遮挡。
文字区域身份绑定页面/文字/样式/几何的摘要，不让同一个 Screen 中的旧链接编号别名到新内容。
GUI bounds 保留 double；原生字体 active area 与只读 hit test 是几何依据，不由截图猜测。
本机网页只桥接标准 GUI 输入：有界序号流、焦点文字、held state 清理，不引入宿主输入或新的权限系统。
GAME_ROUTED 经正常 keyboard/mouse/Screen/Menu/packet/server validation，正常操作不得 direct mutation 冒充。
[控制模型](concept-agent_control.md)定义意图、Lease、Human Override、logical pointer 与 Operator pass。

## 观察、资源版本与扩展 Provider

LIVE 分 CLIENT_KNOWN/SERVER_AUTHORITATIVE；加载失败/未加载/缺权威应明确 unavailable，
不能将未加载远端伪装 void_air，不为观察强制加载。
Deep Observation 的 ResourceRevisionRef 包含 sessionEpoch 与稳定 resource key，
Menu/Entity/BE/Chunk 生命周期令旧 token 失效，tracker 有界。

Generic JSON object key canonicalized，array 默认有序；真正无序集合在领域 capture 按稳定 ID 排序。
Provider native revision 代表资源而非 query projection，必须非递减且同 revision/state 一致；
fallback 使用 query-independent revision basis，不可信时明确降级而非伪造并发前提。
Provider V2 有 schema/policy/affinity/budget/deadline/lifecycle 与隔离，第三方内容保持数据属性。
Ticket、scheduled ticks、entity components/capabilities/attachments 与 storage 使用 common semantic core
加 Target-specific detail，不强行统一不同 Minecraft 语义。

## Fixture、Debug 与证据

Fixture / PLAYTEST / DEBUG_PRIVILEGED 分开。Typed Deep Debug 使用独立 scope/Arm、
world fingerprint、资源/值 precondition、before/after/provenance/synchronization。
bounded Debug batch 逐项有界、可取消，不提供万能 reflection 或 raw mutation 后门。

Wait/Assert 支持 UI、player、block、entity、menu/inventory、event、operation 的 typed 条件，
独立和 Pipeline 语义一致。EventHub 有 filter、bounded queues、backpressure/ring、cursor/resume/gap/resync。
Capture/Recording 有 frame/state/event/byte、contact sheet 和 derivative 预算，
manifest、bundle、index/checksums、Timeline 与 Artifact streaming 分工。
active recording shutdown 必须取消/drain pending capture，inFlight 归零并完成唯一 finalization。
Operator UI/Pointer 在 framebuffer evidence 之后渲染，不能污染截图或录制。

## Dedicated Peer 与持久化

三种部署目标（仅客户端、仅服务端、双端联动）的主体、权限、能力矩阵和里程碑要求，
统一见[部署场景设计](concept-deployment_scenarios.md)。新增部署合同为 DESIGN/PENDING，
当前 mod_client_side=true、mod_server_side=true、客户端启动的 HTTP 入口及有限 Peer 不等于已实现三场景。

现有 Server Peer 基于游戏玩家连接提供有限服务器读取，mutation 另按服务端开关/operator 检查，
客户端 HTTP 入口保留自己的授权；这不等于服务端已完整验证 scope/Arm/资源版本委托。
完整授权和独立服务器入口属于上述待实施设计；不能由客户端构建成功推断 server safety，
也不宣称完整 ResourceVersion parity。

Persistent Read 五端有安全 context、world/player/chunk adapter、有界读取、
deadline/cancel/worker/生命周期、文件变化检测与明确 error classification。
PERSISTED/last_saved_state 不自动 reconcile LIVE；busy/locked 不冒充 corrupt。

Write Safety Foundation 已建立 identity、offline barrier、前置条件、synthetic single-file commit/recovery；
它不是正式 storage.write。Windows 原子替换不代表 directory/power-loss durability，
合作 session.lock 不保证对任意锁忽略程序的 CAS。未来 writer 仍需新的 Entry Review，
仅可考虑 offline/current Target/typed level.dat；player/Region/POI/entity/SavedData/Peer 写入都不自动开启。

## 发布与规划

Core 第一次 Preview 不依赖 E1/E2/E3 或 Persistent Write。
Recording V2、完整 Reconstruction、stress、Phase 10 和扩展都仍须单独授权。
Phase9ASpikeEngine 中受控实验保留原边界，不能把 snapshot-diff Delta 冒充 native event 或完整重建。
细节原文与历史设计保留在[迁移前记录](../research/audit-architecture_runtime.md)。
