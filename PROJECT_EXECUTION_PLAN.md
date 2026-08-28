# Mine-Craft-Protocol 项目执行计划书

> 文档状态：Phase 9B Deep Observation / Provider V2 Complete — Contract Hardened
> 文档版本：0.5
> 编制日期：2026-08-27  
> 修订日期：2026-08-28
> 项目性质：Minecraft Java Agent 自动化、调试、录制与测试基础设施  
> 当前阶段：Phase 8/V1 与 Phase 9B.1 已完成；Phase 9C 未开始并等待独立审查；Phase 10 未开始

---

## 1. 执行摘要

本项目拟建设一套跨 Minecraft Java 版本、跨 Forge/NeoForge/Fabric Loader 的 **Minecraft Agent Control Runtime**。它在 Minecraft 客户端与可选服务器端 Peer 中提供深度观察、真实 GUI/键鼠操作、世界交互、特权调试、连续画面录制、世界状态录制、事件等待、断言、追踪与诊断能力，并通过稳定的原生 HTTP/WebSocket 协议以及独立 MCP Companion 向 Coding Agent、测试程序和其他自动化客户端开放。

项目不是普通的 Minecraft Bot，也不是简单地把 MCP SDK 嵌入游戏。其目标更接近以下能力的组合：

- Minecraft 的 Accessibility Tree / UI Tree；
- Minecraft 的 WebDriver 与键鼠宏引擎；
- 面向游戏状态的调试器与测试夹具；
- 画面、输入、世界状态、网络与事件的同步录制器；
- 面向 Coding Agent 的自动等待、断言、回放和失败诊断平台；
- 一个独立于 Minecraft 内部类名和 Loader API 的稳定控制协议。

项目核心执行哲学为：

> **全能观察、全能调试读写、真实游玩优先操作、语义定位与视觉兜底、逐操作来源标记、分层证据评价。**

测试方法论为：

> **白盒观察、黑盒操作、视觉兜底、夹具隔离。**

---

## 2. 已确认的目标版本矩阵

首期必须支持以下五个 Target：

| Target ID | Minecraft | Loader | 主要定位 |
|---|---:|---|---|
| `1.20.1-forge` | 1.20.1 | Forge | 老版本兼容下界与 Java 17 时代实现 |
| `1.21.1-neoforge` | 1.21.1 | NeoForge | 长期常用稳定版本 |
| `26.1.2-neoforge` | 26.1.2 | NeoForge | 新版本号体系与 Java 25 时代实现 |
| `26.2-neoforge` | 26.2 | NeoForge | 新 GUI/渲染架构与 Vulkan 兼容目标 |
| `26.2-fabric` | 26.2 | Fabric | 新架构下的跨 Loader 对照实现 |

各 Target 实现同一套外部行为契约，但允许使用完全不同的内部 Mixin、Accessor、Invoker、Access Transformer、Access Widener 和目标版本桥接代码。

项目不以“内部代码共享率”作为首要质量指标。真正的统一点是：

- 协议 Schema；
- 数据传输对象；
- 能力与权限模型；
- 操作语义；
- 事件语义；
- 错误模型；
- 录制格式；
- Conformance Test；
- Agent 看到的最终行为。

不同 Target 应使用各自合适的 Gradle 插件、映射与 Java Toolchain。可复用的纯协议 Java 模块应以最低兼容字节码为目标，Minecraft 相关代码不得跨 Target 直接共享活动游戏对象类型。

---

## 3. 最高级架构原则

### 3.1 Capability-first / Fidelity-first，允许必要侵入

先定义需要实现的能力、行为保真度和可验证证据，再为每个 Target 选择最合适的实现机制。Vanilla/Public Minecraft API、Loader API、Accessor、Invoker、Access Transformer、Access Widener、Mixin Instrumentation 和更深层内部修改都只是候选手段，不是项目目标本身。

候选决策深度可以表达为：

```text
先定义能力与行为保真度
    ↓
Vanilla / Public Minecraft API
    ↓
Loader API
    ↓
Accessor / Invoker / Access Transformer / Access Widener
    ↓
Mixin Instrumentation
    ↓
确有需要时采用更深层内部修改
```

这不是绝对优先级算法。如果公开 API 行为不完整、Loader API 绕过正常输入路径，或者 Accessor/Instrumentation 在某个 Target 上更稳定、更忠实，则应选择后者。最终实现选择同时依据：

- 功能完整性；
- 行为保真度；
- 可验证性；
- 与其他 Mod 的兼容性；
- 线程与生命周期正确性；
- 目标版本的长期维护成本。

Mixin 没有架构禁忌，但也没有默认优先权。Loader API 不得成为能力上限；当更深入的内部调用链是实现完整能力所必需时，允许并要求采用经过验证的侵入性实现。

Loader API 仍主要承担 Mod 入口、物理/逻辑侧识别、配置装载、插桩配置接入、Payload 注册，以及确实完整且忠实的生命周期集成。

### 3.2 观察型插桩不得主动干扰其他 Mod

深度读取以全能为目标，但观察实现必须以不改变游戏和其他 Mod 行为为硬约束：

- 当采用 Mixin/Access Transformation 时，默认只面向 Minecraft、Loader 和本项目类；
- 不默认向任意第三方 Mod 类注入；
- 读取型插桩不 cancel、不覆盖返回值、不重排控制流；
- 在满足能力和保真度时，优先考虑更小侵入面的 Accessor、Invoker 或入口/出口观察 Hook；
- 不为读取而触发区块加载、懒初始化或隐式写入；
- 不把活动 Minecraft 对象暴露给其他线程；
- 目标线程内制作不可变快照，异步序列化；
- 注入失败时明确降级或拒绝 readiness，不伪装成完整能力。

### 3.3 GUI 语义定位，游戏内真实分发

准确 UI Tree 用于识别目标和计算坐标。最终点击、长按、拖动、滚动、按键和组合操作必须尽量进入 Minecraft 正常输入分发路径，不直接调用按钮业务回调或直接修改 Menu 状态。

### 3.4 世界操作同时保留三种保真度

- `PLAYTEST`：当前玩家真实可完成的行为；
- `FIXTURE`：通过命令、Vanilla API 或 Loader API 建立测试前置条件；
- `DEBUG_PRIVILEGED`：调试器级全能读写，允许直接内部修改和边界状态构造。

三者必须共存，但每个操作必须携带真实来源，不得用调试写入伪装为玩家行为。

### 3.5 协议永远不暴露版本内部细节

Agent 面向稳定语义：

```text
ui.get_tree
ui.resolve
input.pipeline.run
player.get_state
world.query
debug.world.mutate
record.session.start
wait.until
assert.evaluate
```

Agent 不应依赖 Mojmap 类名、Mixin 目标方法、Loader 事件类或字段名。

### 3.6 MCP 是适配层，不是核心协议

原生 HTTP/WebSocket 协议必须可以被普通测试程序直接使用。MCP Companion 负责把完整协议整理成适合 Coding Agent 的 Tools、Resources、Prompts 和录制 Artifact 接口。

---

## 4. 总体系统架构

```text
                       Coding Agent / Test Runner
                                  │
                 ┌────────────────┴────────────────┐
                 │                                 │
             MCP Client                    Direct HTTP / WS
                 │                                 │
                 ▼                                 │
       TypeScript MCP Companion                    │
                 │                                 │
                 └──────────────┬──────────────────┘
                                │
                    Minecraft Control Protocol
                         HTTP + WebSocket
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Minecraft Client Runtime                     │
│                                                                 │
│  Protocol Gateway                                               │
│      │                                                          │
│      ├── Capability / Auth / Lease                              │
│      ├── Main-thread / Render-thread Scheduler                  │
│      ├── Observation Engine                                     │
│      ├── UI Interaction Tree                                    │
│      ├── GUI Render Tree / Hit Map                              │
│      ├── In-game Input Macro Engine                             │
│      ├── Player / Inventory / Command Controller                │
│      ├── Frame Capture / Composer                               │
│      ├── Unified Recording Timeline                             │
│      ├── Wait / Assert / Diff                                   │
│      ├── Debug Privileged Plane                                 │
│      └── Diagnostics / Trace                                    │
│                                │                                │
│                        Target Instrumentation                   │
│                    Mixin / Accessor / Invoker                    │
└────────────────────────────────┼────────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    ▼                         ▼
          Integrated Server           Dedicated Server Peer
                    │                         │
                    └────────────┬────────────┘
                                 ▼
                   Server-authoritative Observation
                   Fixture / Debug / State Recording
```

### 4.1 网络暴露策略

- 默认绑定 `127.0.0.1`；
- 支持显式启用 LAN；
- LAN 可由 Mod 直接开放，也可由 Companion 代理；
- 推荐由 Companion 承担 TLS、配对和外部网络暴露；
- Mod 内直接 LAN 模式必须具有等效的认证、来源验证和审计能力；
- MCP Companion 默认通过 loopback 连接 Mod。

---

## 5. 计划中的模块边界

```text
protocol-schema
protocol-java
runtime-contracts
runtime-common

target-forge-1.20.1
target-neoforge-1.21.1
target-neoforge-26.1.2
target-neoforge-26.2
target-fabric-26.2

server-peer-common
gateway-http-ws
recording-core
artifact-core
conformance-kit
compatibility-testmods

mcp-companion
docs
examples
```

### 5.1 Target 内部建议结构

```text
target-*/
  bootstrap/
  bridge/
  mixins/
    client/
    input/
    ui/
    render/
    network/
    server/
    debug/
  accessors/
  recording/
  resources/
  tests/
```

不建议用过厚的统一 Loader 抽象隐藏版本差异。允许 Target 层重复少量实现，以换取调用路径清晰、注入点可验证和故障容易定位。

---

## 6. 协议与运行时公共模型

### 6.1 请求生命周期

可能跨线程、跨 tick 或耗时的操作采用异步生命周期：

```text
received
  → validated
  → accepted
  → scheduled
  → executing
  → completed / failed / cancelled / timed_out
```

纯读取和立即完成的短操作可以直接返回结果，不要求人为创建长操作。公共请求层保持最小化：

```text
RequestEnvelope
requestId
protocolVersion / negotiatedProtocol
deadline?
traceOptions?
metadata?
```

修改型或控制型操作按需增加：

```text
MutationContext
  leaseId?
  idempotencyKey?
  preconditions?
```

长操作由 Runtime 返回 `operationId` 或等效 Operation Handle，并按能力支持状态查询、等待和取消。请求不需要预先为所有操作提供 `operationId`。

Debug 操作按能力另外要求：

```text
DebugContext
  debugArmId
  scope
  worldFingerprint
```

每个 operation descriptor 必须声明：

```text
requiresControlLease
requiresDebugArm
supportsIdempotency
supportsCancellation
supportedPreconditions
threadAffinity
executionClass
```

只读请求不默认要求 Lease、Idempotency 或 Screen Revision；需要并发保护的具体操作只声明与它真正相关的上下文。

### 6.2 资源级 Revision、Snapshot 与 Preconditions

不使用单一全局 `worldRevision` 作为普通请求的乐观并发 token，也不要求普通请求携带 `expectedWorldRevision`。Minecraft 活跃世界中的无关变化非常频繁，全局 revision 会制造大量与目标资源无关的 stale failure。

Revision 应尽量绑定实际资源：

```text
sessionEpoch / sessionRevision
screenRevision
menuRevision
playerRevision
nodeRevision
entityRevision
chunkRevision
containerRevision / slotRevision
providerRevision
targetRevision
```

快照和录制使用：

```text
snapshotId
querySnapshotId
stateFrameId
```

典型 Preconditions：

```text
UI:
  screenRevision
  menuRevision
  nodeRevision 或 selector re-resolution

Entity:
  dimension
  entityUuid
  entityRevision

Block:
  dimension
  position
  chunkRevision?
  expectedBlockState?

Container:
  menuId
  containerRevision?
  slotRevision?
  expectedItem?
```

Value-based precondition 与资源级 revision 同等重要。例如操作方块可以要求 `expectedBlockState.id = minecraft:stone`，而不是依赖任何全局世界版本号。

若为了诊断和录制需要全局变化序列，可保留 `worldChangeSequence`。它只是时间线事件序号或诊断元数据，不是默认事务一致性 token。

### 6.3 统一时间线

所有输入、帧、状态、事件和日志尽量带有：

```text
sequence
monotonicTimestampNs
wallClockTimestamp
clientTick
serverTick
renderFrame
screenRevision
menuRevision?
playerRevision?
resourceRevisionRefs?
worldChangeSequence?
stateFrameId?
sessionEpoch
sessionRevision
recordingId
pipelineRunId
pipelineStepId
```

统一时间线用于关联事实和诊断因果，不替代资源级并发控制。远程服务器无法保证 Client Tick 与 Server Tick 完全对齐时，必须标记时间对齐质量和估算偏移。

### 6.4 能力协商

能力不能只返回布尔值，应返回实现深度与验证状态：

```json
{
  "capability": "ui.render_tree",
  "status": "available",
  "implementation": "instrumented",
  "coverage": "render_primitives",
  "verification": "runtime_verified",
  "limitations": []
}
```

能力状态至少支持：

```text
available
degraded
unavailable
permission_denied
peer_required
hook_failed
unsupported_backend
```

### 6.5 操作来源与证据模型

`sessionProfile` 只是权限上限；每次操作必须记录实际 `operationProvenance`。

读取结果至少标记：

```text
perspective:
  rendered_visible
  client_known
  server_authoritative
  internal_instrumented
  persistent_storage

acquisition:
  public_api
  loader_api
  mixin_accessor
  captured_event
  direct_field
  storage_decode

completeness:
  complete
  projected
  partial
  inferred

readEffects:
  none
  lazy_initialization
  forces_chunk_load
  storage_access
```

写入结果至少标记：

```text
authority:
  current_player
  server_command
  test_fixture
  runtime_internal

mechanism:
  game_routed_raw
  game_routed_screen
  game_routed_keymapping
  normal_packet
  command_dispatch
  vanilla_api
  loader_api
  mixin_invoker
  direct_mutation
  persistent_storage

invariants:
  gameplay_validated
  vanilla_invariants_preserved
  partial_invariants
  raw_state

synchronization:
  normal_network_sync
  forced_resync
  local_only
  requires_reload

evidence:
  gameplay
  fixture
  diagnostic
  invalid_for_acceptance
```

### 6.6 错误模型

必须提供稳定错误码，包括但不限于：

- `STALE_SCREEN_REVISION`；
- `STALE_MENU_REVISION`；
- `STALE_ENTITY_REVISION`；
- `PRECONDITION_FAILED`；
- `BLOCK_STATE_MISMATCH`；
- `STALE_NODE`；
- `NODE_NOT_VISIBLE`；
- `NODE_OCCLUDED`；
- `INPUT_LEASE_REQUIRED`；
- `INPUT_STATE_CONFLICT`；
- `SERVER_PEER_REQUIRED`；
- `CAPABILITY_UNAVAILABLE`；
- `HOOK_VERIFICATION_FAILED`；
- `QUERY_BUDGET_EXCEEDED`；
- `FRAME_DROPPED`；
- `RECORDING_BACKPRESSURE`；
- `STATE_FRAME_PARTIAL`；
- `DEBUG_NOT_ARMED`；
- `DEBUG_SCOPE_DENIED`；
- `WORLD_FINGERPRINT_MISMATCH`；
- `PIPELINE_CANCELLED`；
- `PIPELINE_CLEANUP_FAILED`。

---

## 7. 控制权、会话与并发

### 7.1 Control Lease

提供：

```text
control.acquire
control.renew
control.release
control.status
control.emergency_release
```

默认规则：

- 同一时刻只有一个键鼠写入者；
- 可有多个只读观察者和录制者；
- Debug 写入可有更严格的独占锁；
- Lease 具有 TTL；
- 连接断开或超时自动释放全部按键和鼠标按钮；
- 游戏内保留人工紧急接管热键；
- 人工输入是否中止 Agent 流水线由策略配置决定。

### 7.2 资源锁

建议分离：

- `input`：键鼠状态写锁；
- `debug_world_write`：特权世界写锁；
- `screen_transition`：短期 UI 操作屏障；
- `recording`：只读，不阻塞输入；
- `artifact_composition`：纯异步任务。

录制不得抢占键鼠控制权，也不得因为拼接或编码阻塞游戏线程。

---

## 8. 深度观察引擎

### 8.1 玩家状态

应覆盖：

- UUID、名称和会话身份；
- 当前与上一位置、速度、朝向、相机；
- pose、碰撞、地面、水中、飞行状态；
- 生命、吸收、饥饿、空气、经验；
- 游戏模式、Abilities、权限；
- 背包、热栏、护甲、副手、鼠标携带物；
- 属性、效果、冷却；
- 载具、乘客、拴绳等关系；
- 准星方块/实体目标与射线结果；
- 当前 Screen、Menu、Container ID；
- 维度、出生点和重生点；
- 统计信息、进度与配方状态；
- 客户端预测状态和服务器权威状态的差异。

### 8.2 世界查询

禁止无界 `dumpWorld()` 作为普通接口。提供：

```text
world.get_block
world.get_block_entity
world.get_biome
world.get_chunk
world.query_blocks
world.query_entities
world.raycast
world.find_nearest
world.get_scoreboard
world.get_time_weather
world.get_gamerules
world.get_loaded_regions
```

查询支持：

- AABB、半径、区块和维度范围；
- predicate；
- projection；
- limit；
- pagination/cursor；
- sort；
- side/perspective；
- 是否允许加载数据；
- 每 tick 时间预算；
- 结果完整性标记。

### 8.3 第三方 Mod 数据扩展

核心不得反射遍历未知 Mod 的整个对象图。提供显式 Provider SPI：

```java
interface AgentDataProvider {
    Identifier id();
    ProviderCapabilities capabilities();
    DataSnapshot capture(ReadContext context);
    DebugMutationResult mutate(DebugMutationContext context);
}
```

Provider 可以只实现读取，也可以在明确 Debug Scope 下提供写入。

### 8.4 Live World 与 Persistent Storage

Live state 与 persisted state 是不同数据源，不能在普通 `world.*` 查询中静默混用。明确区分：

```text
LIVE
  client_known_state
  loaded_server_state
  server_authoritative_state

PERSISTED
  persistent_unloaded_state
  playerdata / level data / region data
```

`world.*` 只查询 Live World；持久化数据使用明确独立的 `storage.world.*` 或等效 namespace。目标区块未加载时，普通 world query 必须返回未加载/不可用状态，不得在未告知调用者的情况下自动改读磁盘。

Persistent read 必须携带：

```text
source
consistency
worldFingerprint
lastSavedState / saveMarker
liveWorldExists
storageAccessOccurred
sideEffects
stalePossibility
```

运行中世界的内存状态与磁盘 region/playerdata/level data 不一致是正常现象。普通读取不得为了回答问题而改变区块加载状态；需要访问未加载持久化数据时，必须使用显式接口、单独 scope、世界一致性检查和副作用标记。

---

## 9. GUI Interaction Tree 与 Render Tree

产品承诺为：任何 GUI 尽可能提供语义树；无法完整语义化时提供 Render Facts；再无法语义解析时提供 Screenshot/Vision 操作回退。不得承诺所有第三方 GUI 都能恢复完整业务语义。

### 9.1 Interaction Tree

覆盖：

- Screen；
- GuiEventListener；
- ContainerEventHandler；
- Widget；
- Button；
- EditBox；
- List；
- Tab；
- NarratableEntry；
- Menu；
- Slot；
- RecipeBook；
- Tooltip；
- Chat；
- Toast；
- HUD/Overlay；
- Loader 与 Mod 标准组件。

节点至少包含：

```text
nodeId
stableSelector
role
classCategory
label
narration
bounds
visibleBounds
scissorBounds
zOrder
focused
hovered
enabled
visible
clickable
actions
screenRevision
menuRevision?
nodeRevision
frameRevision
provenance
children
```

### 9.2 Render Tree

Render Tree 只表达渲染事实。通过目标版本的 GUI 渲染提交点捕获：

- Text；
- Item；
- Texture/Blit；
- Rectangle/Gradient；
- PiP；
- Tooltip strata；
- Render layer/stratum；
- Scissor；
- Transform；
- Bounds；
- 调用来源与对象关联信息。

1.20.1 与 26.2 可以使用不同实现，但输出统一 Render Tree。

第三方 Mod 可能先在自定义 framebuffer 中用 shader 生成完整 GUI，再只向最终界面提交一张 texture。Runtime 在这种情况下可以确认 texture、bounds、layer 和最终画面，但不能知道纹理内部是否包含按钮、图表、图标、文字或滑块。除非存在可靠 Interaction Tree、对象关联或 Provider 语义，不得从常见位置、颜色或纹理形状直接推断成已知业务控件。

### 9.3 覆盖等级

每个 Screen 或节点必须声明：

```text
semantic_native
semantic_instrumented
semantic_inferred
render_primitives
vision_only
unsupported
```

Capability 和实际 Screen/Node coverage 必须真实报告。Render Tree 提供 Render Facts，不得把任意纹理自动伪装成已知业务控件；`semantic_inferred` 也必须带有推断来源和置信信息。

### 9.4 节点生命周期

- Node ID 只能在对应 `screenRevision`/`nodeRevision` 内使用；
- Screen 重建、窗口缩放、列表滚动或布局变化后旧节点可失效；
- 高级流水线优先保存 selector，不保存永久坐标；
- 执行操作前重新解析 selector 和 hit area；
- Menu/Container 操作按需检查 `menuRevision`、`containerRevision`、`slotRevision` 或 value-based item precondition；
- 支持明确的 stale 错误和可配置重新定位策略。

---

## 10. 游戏内键鼠宏引擎

### 10.1 输入定义

项目要求的“真实键鼠”定义为 **在 Minecraft 内部模拟完整键鼠状态，并进入正常游戏输入分发路径**，不要求操纵操作系统光标，也不依赖窗口焦点。

`GAME_ROUTED` 是输入保真度家族，不是模糊的单一实现。每个 Target 必须验证并记录具体进入层级、消费链和网络结果。

### 10.2 输入路径深度与可验证证据

候选机制至少区分：

```text
GAME_ROUTED_RAW
  从 Minecraft mouse/keyboard input handler 层进入

GAME_ROUTED_SCREEN
  经过 Screen / Widget / AbstractContainerScreen 分发

GAME_ROUTED_KEYMAPPING
  更新虚拟按键状态并经过 KeyMapping / LocalPlayer movement input

NORMAL_NETWORK
  最终形成正常游戏 packet 并接受服务器处理或验证

DIRECT
  直接业务调用或状态 mutation，不属于真实输入证据
```

典型路径：

```text
GUI:
  virtual pointer
  → Minecraft mouse/input handler
  → Screen
  → Widget / Container
  → normal Menu processing

Player movement:
  virtual key state
  → KeyMapping
  → LocalPlayer movement input
  → normal player logic
  → normal packets

Container:
  virtual mouse
  → AbstractContainerScreen
  → Menu
  → normal packet
  → server validation
```

操作 provenance 至少说明：

- 输入从哪一层进入；
- 是否经过正常 Screen；
- 是否经过正常 Menu；
- 是否形成正常 packet；
- 是否经过服务器验证；
- 是否调用直接业务方法；
- 是否发生 direct mutation。

协议可以把多个证据组合成一次操作结果，但不得只写 `GAME_ROUTED=true` 就宣称已经覆盖完整真实路径。

### 10.3 鼠标能力

- GUI 绝对移动；
- 世界视角相对移动；
- 左、中、右和附加按钮；
- down/up/click；
- 双击和连续点击；
- 按指定 tick/frame/ms 长按；
- 拖动；
- 横向/纵向滚动；
- 平滑滚动与离散滚轮刻度；
- 多鼠标键同时按下；
- 鼠标与键盘组合；
- 可选游戏内虚拟光标显示。

### 10.4 键盘能力

- keyDown/keyUp/tap；
- 长按；
- 按键重复；
- 多键同时按下；
- Ctrl/Shift/Alt/Super 等修饰键；
- Key Code、Scan Code、字符输入；
- 文本输入；
- 键鼠跨设备组合；
- 按 tick/frame/ms 控制持续时间。

### 10.5 UI Tree 坐标生成

节点坐标策略：

```text
center
safe_interior
top_left
top_right
bottom_left
bottom_right
custom_offset
slot_center
slider_position
```

计算流程：

```text
selector 解析
  → screen/node/menu 相关 revision 或 value precondition 校验
  → visible/scissor/occlusion 校验
  → anchor 计算
  → GUI/Window/Framebuffer 坐标转换
  → 虚拟鼠标移动
  → 游戏内按钮 down/up
  → 消费路径和结果验证
```

### 10.6 长流水线 DSL

必须支持：

- 顺序步骤；
- 并行块；
- 指定绝对或相对 tick/frame/time；
- 条件分支；
- 有界循环；
- retry；
- timeout；
- wait.until；
- assert；
- 局部变量；
- selector 动态解析；
- 事件触发；
- 子流水线；
- barrier；
- dry-run；
- pause/resume/cancel；
- finally cleanup。

流水线必须在 Runtime 内调度，不能依赖 Agent 逐步通过网络发送每一个按键事件。

### 10.7 输入清理保证

以下场景必须释放全部虚拟输入状态：

- 正常完成；
- 流水线失败；
- Agent 取消；
- Lease 到期；
- 网络断开；
- Screen 意外切换；
- 世界退出；
- 游戏返回标题页；
- Runtime 进入 degraded 状态。

### 10.8 视觉回退

当 UI Tree 不足时：

```text
当前帧/帧序列
  → MCP/多模态模型分析
  → framebuffer 或 GUI 坐标
  → 坐标转换与 revision 检查
  → 选择并记录具体 GAME_ROUTED 输入深度
  → 点击/拖动/滚动
```

视觉产生坐标后仍然进入已验证的游戏内输入分发路径，并在 provenance 中记录实际深度和验证结果。

---

## 11. 世界操作与特权调试平面

### 11.1 PLAYTEST

用于证明玩家真实可以完成操作：

- 移动、跳跃、冲刺、潜行；
- 转向和视角移动；
- 攻击、使用、拾取、丢弃；
- 挖掘、放置和使用方块；
- 物品使用；
- 打开和操作容器；
- 当前玩家权限下的命令；
- 真实 GUI 和 Menu 包；
- 服务端正常验证。

高级动作应尽量编译成真实输入序列，而不是直接改变位置或世界状态。

### 11.2 FIXTURE

用于 Arrange：

- 设置天气、时间、游戏规则；
- 生成实体；
- 放置测试结构；
- 准备背包；
- 传送到测试区域；
- 配置玩家效果和权限；
- 重置测试场景；
- 使用管理员或测试命令；
- 使用 Vanilla/Loader 提供的正规 API。

Fixture 操作不能计为真实玩家验收证据。

### 11.3 DEBUG_PRIVILEGED

必须保留并完整实现调试器级读写能力，包括：

- 玩家、实体和世界内部字段读写；
- ItemStack、数据组件、Attachments、Capabilities、NBT；
- 方块与 Block Entity 状态；
- 区块、Ticket、EntityManager 和 Scheduled Tick；
- 创建正常路径无法产生的边界/非法状态；
- 大批量真实数据生成与修改；
- Menu、Screen、客户端预测状态；
- 网络和同步状态检查；
- 已注册 Mod Provider 的自定义内部状态；
- 明确授权下的持久化世界数据处理。

Debug 写入可以使用以下实现深度，但仍按 Capability/Fidelity、可验证性、兼容性和目标状态选择，不把它解释为绝对优先级算法：

```text
玩家真实操作
  → 命令
  → Vanilla API
  → Loader API
  → Mixin Invoker
  → Direct Mutation
  → Persistent Storage Mutation
```

当目标是 NaN、越界值、不同步状态或其他正常游戏无法构造的边界状态时，允许直接下降到低层修改。

### 11.4 对外 Debug 协议保持领域强类型

内部实现可以侵入，对外协议必须保持 Minecraft 领域语义和强类型。禁止提供：

```text
debug.set_field(object, field, value)
reflect.invoke(class, method, args)
unbounded_object_graph.traverse
```

对外操作应按领域组织，例如：

```text
debug.player.attribute.*
debug.entity.component.*
debug.world.block.*
debug.world.block_entity.*
debug.chunk.ticket.*
debug.menu.*
debug.network.*
debug.storage.*
```

Accessor、Invoker、Loader internals、Mixin、Direct Mutation 或 Persistent Storage 只属于 Target 内部实现细节，不进入公共参数模型。第三方 Mod 通过 Provider SPI 注册明确 Schema、能力、权限和 mutation contract。

### 11.5 Debug Arm

提供：

```text
debug.arm
debug.renew
debug.disarm
debug.status
```

要求：

- 默认关闭；
- 独立 scope；
- 绑定实例和世界指纹；
- 有 TTL；
- 写操作全量审计；
- 支持 namespace allowlist；
- 可要求测试世界或专用服务器标签；
- 持久化修改前支持可选备份/checkpoint；
- 不扩展为任意 Shell、文件系统或通用 JVM RAT。

### 11.6 证据污染检测

测试报告必须指出：

- Arrange 使用了哪些 Fixture/Debug 写入；
- Act 阶段是否只使用 PLAYTEST 路径；
- Assert 使用了玩家可见还是内部证据；
- Debug 写入是否发生在被验收的行为期间；
- 哪些结论属于 gameplay、diagnostic 或 boundary-test。

---

## 12. 帧捕获、连续录制与自动拼接

### 12.1 捕获源

目标支持：

```text
COMPOSITE
WORLD
GUI
```

`COMPOSITE` 为必须能力；`WORLD` 和 `GUI` 分离能力按 Target 和渲染后端报告 capability。

26.2 必须分别验证 OpenGL 与 Vulkan 后端。不得假设 OpenGL framebuffer API 在 Vulkan 下可用。

### 12.2 捕获参数

至少支持：

- 按 render frame、client tick、server tick 或毫秒间隔；
- 固定帧数、固定时长或条件终止；
- 原始尺寸或缩放尺寸；
- 裁剪区域；
- PNG、JPEG、WebP、RAW_RGBA；
- 压缩质量；
- 是否包含虚拟鼠标；
- 是否包含 HUD；
- 是否包含 Debug Overlay；
- GUI scale；
- 色彩空间；
- 队列容量；
- 队列满时阻塞、降采样或丢帧策略；
- 最大单帧和总 Artifact 大小。

### 12.3 异步录制管线

```text
Render Thread
  仅提交 GPU readback / 最小复制
       ↓
Bounded Frame Queue
       ↓
Encoder Workers
       ↓
Frame Store
       ↓
Composer Worker
```

禁止在 Render Thread 上执行 PNG/JPEG 编码、磁盘写入或拼接。

### 12.4 自动拼接

支持：

- 水平胶片；
- 垂直胶片；
- 固定行列网格；
- 自动网格；
- Sprite Sheet；
- Contact Sheet；
- 单元格宽高；
- contain/cover/stretch；
- 间距、边框、背景色；
- 最大输出尺寸；
- 超限自动拆分；
- 帧号、tick、时间戳、流水线步骤标题；
- 自定义排列顺序；
- 是否保留原始帧。

拼接结果是衍生产物，不默认删除原始帧。

### 12.5 并行保证

帧录制和拼接必须能够与键鼠流水线同时进行：

- 录制只读，不获得 input lease；
- 游戏线程只做必要快照；
- 编码和拼接在独立 Worker；
- 队列背压不得改变虚拟按键状态；
- 发生丢帧时记录 gap，而不是阻塞输入；
- 每帧关联当时的 pipeline step、screen revision 和 input state。

---

## 13. 世界级状态录制

### 13.1 State Frame

世界状态的每次逻辑采样定义为 `state frame`，支持：

- render frame 驱动；
- client tick 驱动；
- server tick 驱动；
- 固定毫秒间隔；
- 事件触发；
- 输入步骤前后触发；
- 断言失败时触发。

### 13.2 可选数据轨道

- Player；
- Inventory/Menu；
- Entities；
- Blocks；
- Block Entities；
- Chunks；
- Chunk Tickets；
- Scheduled Ticks；
- Time/Weather；
- Gamerules；
- Scoreboard/Bossbar；
- Command 状态；
- Client/Server world；
- 网络同步状态；
- 自定义 Mod Provider。

### 13.3 选择器

支持：

- 维度；
- AABB；
- 玩家半径；
- 类型、Tag 和 predicate；
- projection；
- limit；
- 排序；
- 变化字段过滤；
- 是否记录 NBT/Components/Attachments；
- 是否允许加载未加载数据；
- 采样预算。

### 13.4 Keyframe + Delta

世界录制采用：

```text
周期性完整 Keyframe
       +
逐 tick / event Delta
```

记录内容包括：

- 实体生成、移除和字段变化；
- 方块与 Block Entity 变化；
- 玩家和容器变化；
- 区块加载/卸载；
- 时间、天气和规则；
- 数据包与命令关联；
- Provider 自定义变化；
- 数据缺口、预算超限和降采样。

### 13.5 一致性等级

```text
exact_tick
synchronized_barrier
best_effort
partial
```

跨客户端与服务器组合快照必须说明一致性等级。每个 State Frame 使用 `stateFrameId`、资源级 revision 引用和必要的 `worldChangeSequence` 诊断位置，不使用全局世界 revision 作为一致性 token。Debug 模式可提供暂停/屏障来获得精确状态，但必须标记为非实时运行。

---

## 14. 统一多轨 Recording Session

不把帧录制、世界录制和输入追踪设计为孤立系统。统一 Recording Session 包含：

```text
Recording Session
 ├── video frames
 ├── stitched derivatives
 ├── UI interaction tree revisions
 ├── GUI render tree revisions
 ├── world state keyframes/deltas
 ├── input pipeline events
 ├── game events
 ├── network events
 ├── commands
 ├── logs
 ├── assertions
 └── performance metrics
```

### 14.1 内部调度

```text
                         Shared Timeline
                               │
          ┌────────────────────┼─────────────────────┐
          ▼                    ▼                     ▼
   Input Scheduler       Frame Capture         State Sampler
    Client Thread        Render Thread       Client/Server Thread
          │                    │                     │
          └────────────────────┼─────────────────────┘
                               ▼
                    Async Recording Writer
                               │
                        Artifact Composer
```

### 14.2 Artifact Bundle

建议录制输出为版本化 Bundle：

```text
manifest.json
timeline/
frames/
frame-index.json
ui/
world/
events/
network/
logs/
assertions/
derivatives/
checksums.json
```

`manifest.json`、`frame-index.json` 和面向人的元数据保持可读、版本化。高频长期数据的 canonical representation 不绑定 JSON/NDJSON，应允许：

```text
chunked binary
versioned binary records
compressed blocks
independent indexes
```

适用对象包括 World State Keyframe、per-tick delta、实体变化、Block Entity、Components、network metadata 和长时间 Recording。当前阶段不冻结最终 binary codec，只冻结 Bundle 能够携带版本、索引、压缩块和可迁移 Schema 的要求。

NDJSON/JSON 作为小型 trace、Debug export 和人工检查格式保留；Companion 和未来工具必须能够从 canonical store 导出可读格式，但不得假定 NDJSON 是长期存储真源。

大型产物通过 Artifact Handle、HTTP 下载或 WS binary stream 返回，不把所有内容直接嵌入单次 JSON 响应。

### 14.3 Crash-safe Finalization

JVM 崩溃时 Mod 未必能发送 `game.crashed`。Companion 应负责：

- 监控进程/连接退出；
- 保存最近已经收到的录制分片；
- 关联 crash report 和日志；
- 将未正常关闭的 Bundle 标记为 `aborted`；
- 尝试生成最后可用的索引和校验信息。

---

## 15. 事件、等待和断言

### 15.1 事件

至少覆盖：

- screen opened/closed/changed；
- UI tree changed；
- focus/hover changed；
- input dispatched/consumed；
- player changed；
- inventory/menu changed；
- entity spawned/removed/changed；
- block/chunk changed；
- chat received；
- command output；
- packet sent/received；
- connection joined/disconnected；
- resource reload；
- recording started/stopped/gap；
- assertion passed/failed；
- capability changed；
- hook failure；
- game shutdown/crash inferred by Companion。

WebSocket 事件必须支持：

- sequence；
- subscription filter；
- backpressure；
- ring buffer；
- resume cursor；
- gap notification；
- full resync。

### 15.2 Wait

`wait.until` 在 Runtime 内执行类型化条件，避免 Agent 使用不稳定 sleep：

- Screen/节点条件；
- 玩家状态；
- 实体/方块条件；
- Menu/Inventory 条件；
- 录制条件；
- 事件条件；
- 自定义 Provider 条件。

不在 Mod 内引入无界任意脚本表达式。条件语言需要受限、可预算和可取消。

Wait 条件应绑定它观察的资源，并在结果中返回相应 `screenRevision`、`menuRevision`、`playerRevision`、`entityRevision`、`chunkRevision`、`providerRevision`、`querySnapshotId` 或 `stateFrameId`。不得使用全局世界版本号把无关变化当作条件失效。

### 15.3 Assert

支持：

- 状态断言；
- 世界断言；
- UI Tree 断言；
- 图像区域断言；
- 世界状态 Diff；
- 事件序列断言；
- 性能阈值断言。

断言结果必须说明证据来源和是否可作为 gameplay acceptance。

Assert 可以绑定明确 Snapshot/State Frame，也可以使用 value-based condition；需要一致性时只声明与目标资源相关的 revision/precondition。

---

## 16. 额外纳入的高级功能

### 16.1 人类操作录制与宏回放

记录开发者真实操作，同时保存：

- 原始游戏内键鼠事件；
- UI selector；
- 当时坐标；
- Screen/Frame revision；
- 世界状态；
- 等待点和结果。

回放模式：

- 原始坐标；
- 相对坐标；
- selector 重新定位；
- 自适应等待；
- 转换为可编辑流水线。

### 16.2 Rolling Recorder

持续在内存中保留最近 N 秒：

- 帧；
- UI Tree；
- State Delta；
- 输入；
- 数据包；
- 日志；
- 性能指标。

在断言失败、崩溃、断线或手动触发时落盘。

### 16.3 Diff 与 Golden Baseline

- UI Tree Diff；
- 世界状态 Diff；
- 背包、实体、方块 Diff；
- 像素 Diff；
- 感知图像 Diff；
- 指定区域 Diff；
- Golden Frame；
- Golden State；
- 容差与忽略区域。

### 16.4 因果延迟追踪

追踪：

```text
输入发出
  → Screen/Player 消费
  → Packet 发出
  → Server 处理
  → World 改变
  → Client 同步
  → 新帧可见
```

用于识别同步延迟、错误预测、GUI 提前显示和客户端刷新缺失。

### 16.5 Debug 时间控制

显式 Debug Scope 下支持：

- 暂停 Client Tick；
- 暂停 Integrated Server Tick；
- 单步 Tick；
- 单步输入流水线；
- 在指定点捕获同步状态；
- 恢复运行。

所有此类记录标记为非真实时间，不用于真实性能验收。

### 16.6 网络记录

记录：

- packet/payload identifier；
- 方向；
- 大小；
- client/server tick；
- handler 开始/完成；
- 与输入、命令和世界变化的关联。

Payload 内容默认按 allowlist 解码，避免泄露敏感数据或无界序列化。

---

## 17. 线程、性能与背压

### 17.1 线程规则

- HTTP/WS 线程不得直接操作 Minecraft 对象；
- 客户端状态在 Client Thread 捕获；
- 渲染资源在 Render Thread 捕获；
- 服务器状态在 Server Thread 捕获；
- DTO 制作完成后才交给异步线程；
- 编码、压缩、Diff、拼接和磁盘写入全部异步；
- 不跨线程持有活动 Level、Entity、Screen、Menu、Chunk 引用。

### 17.2 预算

为以下操作分别设置预算：

- 单 tick 观察时间；
- 世界查询数量；
- State Frame 复制量；
- 帧 readback 队列；
- WS 发送队列；
- 单连接带宽；
- 单 Artifact 大小；
- Pipeline 步骤数、循环次数和持续时间。

### 17.3 降级策略

当录制无法跟上时优先：

1. 记录 gap；
2. 降低录制采样率；
3. 丢弃非关键派生帧；
4. 停止录制并返回明确错误。

不得通过阻塞 Client/Render Thread 来保证“零丢帧”，除非显式 Debug Capture 模式要求暂停游戏。

---

## 18. Instrumentation / Hook 治理与兼容性

每个关键 Hook——无论最终使用 Public API、Loader API、Accessor、Invoker、Access Transformation 或 Mixin——都必须拥有：

- 对应 capability；
- 目标版本；
- 实现机制与选择理由；
- 注入目的；
- 注入点选择说明；
- 预期匹配数量；
- 是否只读；
- 是否改变控制流；
- 兼容风险等级；
- Runtime 验证方法；
- 失败时的降级策略；
- Conformance Test。

### 18.1 禁止事项

观察平面原则上禁止：

- `@Overwrite`；
- 无必要的 cancel；
- 修改参数或返回值；
- 向任意第三方 Mod 类自动注入；
- 依赖不稳定调用序号且没有验证；
- 注入失败后继续宣称完整能力。

操作与 Debug 平面如确实需要改变控制流，必须隔离、标记并覆盖兼容测试。

### 18.2 启动 Readiness

Runtime 启动后执行 Hook Self-Test：

- 所选 Public/Loader/Internal Hook 是否可达；
- Mixin（若使用）是否应用；
- Accessor/Invoker（若使用）是否可用；
- 输入路径是否可注入；
- Screen/Render 捕获是否收到样本；
- Frame readback 是否成功；
- Server Peer 是否可达；
- 录制队列是否可用。

关键 Hook 失败时返回明确的 degraded/unavailable capability。

---

## 19. 安全模型

本项目等价于对 Minecraft 实例的高权限远程控制，安全从第一阶段实现。

### 19.1 网络安全

下列清单描述 Ultimate 网络安全目标。V1 Release Profile 按 ADR-0001 仅开放 loopback；其中 LAN、配对、TLS、IP allowlist 与持久可撤销 Principal 不得被误报为 V1 已实现。

- 默认 loopback；
- LAN 显式启用；
- 高强度随机 Token；
- 配对流程；
- scope；
- IP allowlist；
- Host 校验；
- Origin 校验；
- 请求体和速率限制；
- 可选/推荐 TLS；
- Token 不进入普通日志；
- 连接和写操作审计。

### 19.2 Scope

建议：

```text
read.visible
read.client
read.server
read.internal
ui
input
command.player
record.frame
record.state
fixture
debug.read
debug.write
debug.storage
admin
```

### 19.3 Agent Prompt Injection

聊天、书本、告示牌、MOTD、资源包、GUI 文本和 Mod 内容均为不可信输入。

强制隔离规则：Minecraft 世界产生的文本永远只能进入数据平面，不能动态改变控制平面。

```text
允许：
  Minecraft text
  → Tool Result / Resource Content / Observation Data

禁止：
  Minecraft text
  → MCP Tool Description
  → System Prompt
  → Tool Permission Definition
  → Runtime Policy
```

所有文本数据携带：

```text
source
trust
serverProvided
playerVisible
sanitization
```

MCP Companion 必须满足：

- 不得把游戏内文字当作系统指令；
- 不得根据服务器文字动态生成或修改高权限 Tool description；
- 不得把聊天、书本、MOTD、告示牌或 GUI 文本直接拼入系统指令；
- 不得因为游戏内文字的要求自动提升 scope；
- Debug Arm 和破坏性动作继续由独立授权控制；
- provenance/trust 信息随所有 Agent 可见内容传播；
- 高风险命令和 Debug 写入始终独立于文本内容进行权限判定。

### 19.4 明确禁止扩展

核心不得顺带提供：

- 任意 Shell；
- 任意文件系统浏览；
- 任意 ClassLoader 操作；
- 无边界通用反射 RPC；
- 任意本机进程控制。

调试能力限定在 Minecraft 运行时、已授权世界状态和显式 Provider 范围内。

---

## 20. Server Peer

### 20.1 部署模式

- Integrated Server：Client Runtime 直接桥接；
- Dedicated Server with Peer：通过 Minecraft Payload 或受保护内部通道连接；
- Remote Server without Peer：只能使用客户端已知数据和当前玩家合法操作。

### 20.2 Server Peer 职责

- 服务器权威状态读取；
- Server Tick 时间线；
- 世界/实体/玩家深度观察；
- Menu 和命令处理追踪；
- World State Recording；
- wait/assert；
- Fixture；
- Debug Privileged；
- 数据包关联；
- 客户端重同步；
- 权限和审计。

### 20.3 Capability 示例

```json
{
  "world": {
    "clientKnown": true,
    "serverAuthoritative": true,
    "serverPeer": true,
    "persistentUnloadedRead": false,
    "fixtureWrite": true,
    "debugDirectWrite": true
  }
}
```

---

## 21. HTTP、WebSocket 与 MCP Companion

### 21.1 原生协议

HTTP 适合：

- 查询；
- 短操作；
- Artifact 元数据与下载；
- 能力与配置；
- 调试授权流程。

WebSocket 适合：

- 事件；
- 长操作状态；
- Pipeline 控制；
- 录制状态；
- 二进制帧；
- 大型状态流；
- Resume/Gaps。

### 21.2 MCP Companion

Companion 使用独立 TypeScript 工程，支持 stdio，并根据客户端兼容矩阵支持所需 MCP 协议代际。

MCP 层不机械复制全部底层 RPC。建议按稳定领域提供约 15–30 个类型明确的工具，并将大数据通过 Resources/Artifacts 返回。

候选工具：

```text
minecraft_get_capabilities
minecraft_get_session
minecraft_get_state
minecraft_query_world
minecraft_get_ui
minecraft_interact_ui
minecraft_run_input_pipeline
minecraft_control_player
minecraft_execute_command
minecraft_start_recording
minecraft_stop_recording
minecraft_get_artifact
minecraft_wait
minecraft_assert
minecraft_fixture
minecraft_debug
minecraft_diagnostics
```

MCP Tool 输出继续保留原生协议中的 perspective、mechanism、evidence 和 trust 标记。

---

## 22. 建议的协议命名空间

```text
system.*
session.*
capability.*
control.*

observe.*
ui.*
input.state.*
input.pipeline.*
input.record.*
input.replay.*

player.*
inventory.*
world.*
entity.*
command.*

storage.world.*

fixture.world.*
fixture.player.*
fixture.entity.*

debug.world.*
debug.player.*
debug.entity.*
debug.menu.*
debug.client.*
debug.network.*
debug.storage.*

record.session.*
record.frame.*
record.state.*
record.event.*
record.rolling.*

artifact.*
timeline.*
event.*
wait.*
assert.*
diff.*
replay.*
diagnostics.*
```

---

## 23. 范围分层、执行阶段与里程碑

### 23.1 Ultimate Scope

Ultimate Scope 是本计划书描述的完整长期目标，包括五 Target、Interaction Tree、Render Tree、Vision、完整键鼠宏与 Pipeline DSL、Live World 查询、世界状态录制、Server Peer、完整 DEBUG_PRIVILEGED、连续录制与 Artifact、Golden Diff、Rolling Recorder、网络追踪、人类操作回放、Tick Step、MCP Companion、持久化世界诊断和高级崩溃恢复。

Ultimate Scope 不因为 Phase 0 或 V1 暂时没有实现而从长期架构中删除。其完成标准见 27.3。

### 23.2 V1 Product Scope

V1 是首个可称为完整可用产品的范围，重点建立 Coding Agent 自主测试闭环：

- 五 Target 共享外部核心契约并通过 V1 Conformance；
- Runtime 启动、V1 loopback-only 安全基线、Capability Self-Test；LAN 按 ADR-0001 保留在 Ultimate Scope；
- get session/get capabilities；
- Vanilla 与标准 Mod GUI 的 Interaction Tree；
- Render Facts 基线和真实可执行的 Screenshot/Vision fallback；
- UI selector/node resolve 与资源级 revision/precondition；
- 完整核心键鼠状态机、组合输入和可用的 Pipeline DSL；
- 可验证的 GAME_ROUTED 输入深度；
- 玩家移动、视角、世界交互和正常 Container 路径；
- Player/World/Entity 基础 Live Observation；
- Runtime 内 wait/assert；
- Screenshot、基础连续帧录制、基础拼接和版本化 Artifact；
- 基础可选 State Frame 录制，不要求 Ultimate 的完整高频世界重建能力；
- provenance/perspective/mechanism/evidence；
- Integrated Server 权威观察；
- Dedicated Server Peer 的基础权威查询、权限和受控 Fixture/Debug 扩展；
- Debug Arm 和代表性强类型 Debug 操作，但不要求所有深层领域在 V1 全覆盖；
- HTTP/WS 与 MCP Companion；
- Control Lease、权限、安全审计、Prompt Injection 隔离和断线输入清理。

以下能力保留在 Ultimate Scope，但不强制进入 V1：

- Golden Baseline；
- Rolling Recorder；
- 人类操作录制/回放；
- 完整因果延迟分析；
- Debug Tick Stepping；
- 高级 Persistent Storage Mutation；
- 全领域 Deep Debug 覆盖；
- 长期高频完整 World Recording；
- 高级 Crash Recovery 与恢复工具。

### 23.3 Phase 0 Vertical Slice

Phase 0 只证明最小控制闭环和三个极端 Target 的关键调用链可实现，不建设完整 Runtime，也不冻结 Wire Protocol v1。

最小闭环能力：

```text
runtime startup
loopback auth
capability self-test
get session / get capabilities

Interaction Tree
UI selector / node resolve
GAME_ROUTED click
basic keyboard / mouse movement
basic player movement / view / world interaction

player state
single block query
simple entity query
Composite Capture
WS event
wait.until
minimal trace + provenance
```

端到端行为闭环：

```text
Agent
  ↓
获取标题页 UI
  ↓
通过语义节点进入测试世界
  ↓
控制玩家移动与视角
  ↓
打开背包
  ↓
获取 Slot Tree
  ↓
通过真实输入操作 Slot
  ↓
执行一次世界交互
  ↓
读取玩家/世界状态
  ↓
截图
  ↓
等待与断言
  ↓
得到带 provenance 的结果
```

### Phase 0A：最小 Runtime Contracts 草图

只定义支撑 Probe 的概念 DTO、调用边界、线程归属和最小 provenance，不形成深度 Schema，不宣称协议稳定。

退出条件：三个 Target Probe 可以围绕同一组问题采集可比较证据。

### Phase 0B：Forge 1.20.1 UI / Input Probe

验证 Interaction Tree 来源、Screen/Menu 生命周期、鼠标到 Screen/Container 的实际链路、KeyMapping/玩家移动、Composite Capture 和 Client/Render/Integrated Server 线程边界。

退出条件：形成真实调用链记录、可运行最小闭环或明确失败证据，并识别所需 Public/Loader/Accessor/Mixin 机制。

### Phase 0C：NeoForge 26.2 UI / Input / Render / Capture Probe

验证新 GUI/Render 架构、Render Facts 捕获、GAME_ROUTED 最佳注入层、OpenGL Composite Capture、Vulkan Composite Capture、Screen/Menu 生命周期和线程边界。

退出条件：OpenGL/Vulkan 能力分别给出已验证 capability，或给出明确技术阻塞与降级边界。

### Phase 0D：Fabric 26.2 UI / Input / Render / Capture Probe

在 Fabric 上验证与 26.2 NeoForge 同等路径，识别 Loader 差异与可共享/不可共享边界。

退出条件：完成同一证据模板，并能够与 NeoForge 26.2 逐项比较。

### Phase 0E：三 Target 调用链与差异汇总

统一整理 UI、Input、Render、Capture、Container、Player Control 和线程调度的实际事实。

退出条件：没有仍被抽象层掩盖的关键调用链；所有未知项被分类为 Target-specific 或公共契约问题。

### Phase 0F：形成 Protocol V0 Draft

基于 Spike 事实设计最小 Request Envelope、Capability、Revision/Precondition、Provenance、UI/Input、Capture、Event 和 Wait Schema。

退出条件：Schema 能表达三个 Target 的已验证差异，但不包含未经样片验证的深层协议承诺。Protocol V0 可以迭代，不视为 Wire v1。

### Phase 0G：形成正式 Multi-Target Repository Skeleton

建立 Target 工程、Java Toolchain 隔离、协议模块、测试夹具、CI 基线、Hook 清单模板和 ADR 机制。

退出条件：三个 Probe Target 进入正式骨架；其余两个 Target 有可构建占位和明确接入点。

### Phase 0H：形成 Conformance V0

把最小闭环写成跨 Target 场景，记录成功、降级、失败、线程和 provenance 证据。

退出条件：Forge 1.20.1、NeoForge 26.2、Fabric 26.2 使用同一外部场景完成 Phase 0 闭环，或以诚实 capability 表达差异。

正式 Protocol Schema 不应在最高风险实现路径尚未验证前被设计得过深或视为稳定。不在技术样片验证前冻结 Wire Protocol v1。

### Phase 1：V0 执行基线与构建系统

基于 Phase 0 结果完善多 Target 构建、Protocol V0 代码生成、CI、ADR、`ARCHITECTURE.md`、`THREAT_MODEL.md`、Hook/Capability Self-Test 基线和 Conformance Harness。

退出门槛：五个 Target 均能构建最小 Mod；三个 Probe Target 运行 V0 场景；Schema 变化有版本和迁移纪律。

### Phase 2：协议核心、线程调度与安全

交付分层 Request Envelope、资源级 revision/precondition、Capability、Provenance、HTTP/WS、Auth/Scope、Control Lease、Client/Render/Server 调度器、审计和 trace。

退出门槛：跨线程无活动对象泄漏；权限拒绝、超时、取消、断线和输入清理均有自动测试。

### Phase 3：V1 UI、Render Facts 与输入宏

交付 Interaction Tree、Container Slot、Render Facts 基线、selector、资源级 revision、坐标生成、核心键鼠状态机、Pipeline DSL、wait/assert 和视觉回退。

退出门槛：可以通过流水线进入世界、操作 Vanilla/标准 Mod GUI、滚动、拖动、组合按键，并证明实际输入深度。

### Phase 4：V1 Live Observation、Capture 与 Integrated Server

交付 Player/World/Entity Live Query、Integrated Server 权威数据、Composite Capture、26.2 OpenGL/Vulkan capability、基础 Screenshot/State Frame、Provider Read SPI。

退出门槛：基础查询不隐式访问 Persistent Storage；客户端/服务器结果来源明确；Capture 与输入并行。

### Phase 5：V1 Recording、Artifact 与基础 Fixture/Debug

交付基础连续录制、基础拼接、版本化 Artifact Bundle、canonical store 抽象、Fixture、Debug Arm、代表性强类型 Debug API 和证据污染检测。

退出门槛：录制背压不阻塞游戏线程；Fixture/Debug 不能误计为 gameplay；Artifact 可由 Companion 消费。

### Phase 6：V1 Dedicated Server Peer

执行状态（2026-08-28）：已在五 Target 实现并通过 Integrated payload harness 与独立 Dedicated Server/Client 远程 conformance；协议仍为不稳定 `peer-v0`，不构成 Wire Protocol v1 冻结。

交付 Peer 协议、Server-authoritative Query、权限、基础 State Recording、受控 Fixture/Debug、Tick 对齐和断线降级。

退出门槛：无 Peer 时正确拒绝不可用能力；有 Peer 时完成权威观察和受控操作。

### Phase 7：五 Target V1 对齐

执行状态（2026-08-28）：已完成。NeoForge 1.21.1/26.1.2 完整 Runtime、五 Target 扩展标准 Widget GUI、disabled fail-closed、selector 消歧、动态控件、Hook manifest、静态冲突纪律和 capability 差异矩阵均已通过统一门禁。

补齐 NeoForge 1.21.1、NeoForge 26.1.2，执行五 Target Conformance、标准 Mod GUI 兼容测试、Hook 冲突测试和 capability 差异文档。

退出门槛：五 Target 达到 V1 公开能力声明，差异均以 capability 表达。

### Phase 8：MCP Companion 与 V1 发布硬化

执行状态（2026-08-28）：已完成。产品源码 commit `2dda8448d00852d42fb3e07525ee05daaaddd66f` 已通过 clean detached Remote Parity、dependency audit、五 Artifact hash binding、7-run/5-Target/2-Vulkan Live Matrix、Hardening Representative，以及 active Recording shutdown/race/idempotency/stop-vs-close 验收。V1 Remote Release Candidate 为 PASS。原生 Wire Protocol v1 仍保持未冻结。

交付 MCP Tools/Resources、stdio、协议代际兼容、Artifact 获取、Agent-friendly 错误、完整自主测试工作流、安全审计和性能基线。

退出门槛：满足 27.2 V1 Definition of Done。

### Phase 9：Ultimate 深度观察、Debug、Storage 与 World Recording

执行状态（2026-08-29）：Phase 9A 已完成事实调查；Phase 9B/9B.1 已完成五 Target Formal Deep Observation 与契约硬化。资源 revision 由投影前 canonical semantic state 生成，并使用有界、会话单调的 tracker；Provider V2 已强制认证 scopes、perspective、thread affinity、read-effects/no-load/no-storage/no-mutation、timeout/cancellation/late-result isolation 与 executable schema registry。Phase 9C 尚未开始，必须经独立审查另行开放。

扩展全领域强类型 Deep Debug、批量边界状态、高级 Provider、显式 Persistent Storage、完整 Keyframe/Delta、长期高频 canonical recording 和高级 Diff。

退出门槛：对应 Ultimate capability 有明确 Target coverage、权限、迁移和压力测试。

### Phase 10：Ultimate 高级诊断与恢复

执行状态：未开始。必须在 Phase 9 Conformance、Exit Gate 和独立审查完成后单独启动，不得与 Phase 9 合并。

交付 Rolling Recorder、人类操作录制/回放、Golden Diff、因果延迟、Debug Tick Step、Crash-safe Finalization 和高级诊断工具。

退出门槛：满足 27.3 Ultimate Definition of Done。

---

## 24. 核心验收场景

### 24.1 Phase 0 最小闭环：真实 GUI、玩家与容器

1. Agent 获取标题页 UI Tree；
2. 通过节点坐标点击进入世界；
3. 通过真实键盘和视角输入移动玩家；
4. 完成一次方块或实体世界交互；
5. 通过真实输入打开背包或箱子；
6. UI Tree 定位 Slot；
7. 通过 Shift + 左键完成移动；
8. 服务端验证 Menu 操作；
9. 观察玩家、单方块、简单实体和背包状态；
10. 获取 Composite Screenshot；
11. 使用 `wait.until` 和基础断言确认结果；
12. 输出带输入深度、资源 revision/precondition 和 provenance 的 gameplay evidence。

### 24.2 V1：视觉回退

1. 打开完全手绘的测试 GUI；
2. Interaction Tree 无可用控件；
3. Runtime 提供 Render Tree 和截图；
4. 多模态模型给出坐标；
5. GAME_ROUTED 点击；
6. 通过 Screen 变化和世界状态验证结果。

### 24.3 V1：长键鼠流水线

1. 同时按住 W 和 Sprint；
2. 持续转向；
3. 中途使用物品；
4. 打开 GUI 后滚动、拖动和组合按键；
5. 录制帧和世界状态；
6. 人工取消；
7. 验证所有输入状态释放。

### 24.4 Ultimate 压力场景：录制并行

1. 启动 20 分钟输入流水线；
2. 同时按设定间隔捕获帧；
3. 同时录制玩家、实体和目标区域状态；
4. 异步生成 Contact Sheet；
5. 验证输入延迟、丢帧、状态 gap 和内存上限。

### 24.5 Ultimate：边界和批量数据

1. 通过 Debug 批量创建大量真实实体/组件组合；
2. 明确记录 Debug provenance；
3. 玩家通过真实 GUI 检索和操作其中一项；
4. 通过内部状态和可见画面分别断言；
5. 报告区分 Arrange、Act 和 Assert 证据。

### 24.6 V1：Dedicated Server

1. 无 Peer 连接服务器；
2. 验证只能读取客户端已知状态；
3. 安装 Peer 后重新连接；
4. 获取 server-authoritative capability；
5. 执行受控 Fixture/Debug；
6. 验证权限、审计和 Tick 对齐。

### 24.7 Ultimate：崩溃与失败诊断

1. 开启 Rolling Recorder；
2. 触发测试 Mod 崩溃；
3. Companion 检测连接/进程终止；
4. 保存最后帧、状态 Delta、输入、数据包和日志；
5. 生成 aborted Bundle；
6. 输出最后已知因果链。

---

## 25. 质量门槛

每个进入对应发布范围的公开能力必须满足：

- 有与当前阶段匹配的版本化 Schema；Phase 0 草图不伪装成稳定协议；
- 有权限规则；
- 有 provenance；
- 有 Target capability；
- 有成功测试；
- 有失败/超时/取消测试；
- 有线程归属测试；
- 有性能预算；
- 有 Hook/Capability Self-Test；
- 有至少一个端到端 Conformance Scenario；
- 文档不把降级能力描述成完整能力。

### 25.1 不允许替代正式门槛的证据

- 单纯能够编译；
- 仅调用直接状态修改成功；
- 只看端口开放；
- 使用固定 sleep 的偶然通过；
- 只在一个 Target 运行；
- 只在 OpenGL 或只在 Vulkan 运行；
- 录制成功但输入被明显阻塞；
- Debug 修改成功后宣称玩家路径正常。

---

## 26. 主要风险与缓解

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| 跨版本内部结构差异巨大 | Target 实现成本高 | 共享协议和测试，不强求内部实现共享；先做三个极端样片 |
| 过早统一内部机制 | 形成错误公共抽象 | Capability/Fidelity-first；Spike 后再形成 Protocol V0 和正式骨架 |
| Hook/Mixin 冲突 | 与其他 Mod 不兼容 | 观察型注入、禁止 Overwrite、Self-Test、兼容 Test Mod、能力降级 |
| 完全手绘/Shader GUI 无语义 | UI Tree 不完整 | Render Facts、Hit Map、截图与多模态回退；不伪造业务语义 |
| 输入模拟绕过真实路径 | 测试证据无效 | 区分 GAME_ROUTED 深度；记录 Screen/Menu/packet/server validation 证据 |
| Debug 写入破坏不变量 | 世界损坏或测试误判 | 独立 scope、arm、审计、checkpoint、invariants/evidence 标记 |
| 大型世界录制卡顿 | TPS/FPS 下降 | Keyframe+Delta、预算、异步处理、背压和 gap |
| Live/Persisted 状态混用 | 返回过期或自相矛盾数据 | 独立 namespace、source/consistency/world fingerprint 和显式 storage access |
| Recording 过早锁定 NDJSON | 长期性能与迁移受限 | 可读 manifest + 未冻结 codec 的版本化 canonical binary store |
| Vulkan 截图路径不同 | 26.2 捕获失败 | 独立后端实现和 capability；目标设备验证 |
| Agent Prompt Injection | 恶意游戏文本影响 Agent | trust/source 标记、Companion 隔离、权限和审批 |
| LAN 高权限暴露 | 实例被接管 | loopback 默认、TLS/配对/token/scope/allowlist/审计 |
| JVM 崩溃无法发事件 | 录制丢失 | 分片写入、Rolling Recorder、Companion 监控和恢复 |
| MCP 客户端代际不一致 | Agent 无法连接 | 原生协议独立；Companion 维护 MCP 兼容矩阵 |

---

## 27. 分层 Definition of Done

### 27.1 Phase 0 Exit Criteria

1. Forge 1.20.1、NeoForge 26.2、Fabric 26.2 完成统一 Probe 模板；
2. 三 Target 的 Interaction Tree、GAME_ROUTED、Container、Player Control、Composite Capture 和线程调用链均有运行证据；
3. NeoForge/Fabric 26.2 分别记录 OpenGL/Vulkan capability；
4. Agent 完成 24.1 的最小端到端闭环；
5. Runtime startup、loopback auth、get session/get capabilities 可用；
6. player state、单方块、简单实体、WS event、wait.until、Composite Screenshot 和 minimal trace 可用；
7. 每次操作返回基础 provenance，并能区分 direct 与经过正常路径；
8. 三 Target 差异已经汇总，未知项被归类，不用虚假的公共抽象掩盖；
9. Protocol V0 Draft 仅基于已验证事实形成，未冻结 Wire v1；
10. Conformance V0 能重复执行最小闭环。

### 27.2 V1 Definition of Done

1. 五个 Target 均能加载并通过 V1 Conformance Suite；
2. 五 Target 共享外部核心契约，Target 差异通过 capability 真实表达；
3. Agent 可以从标题页自主进入测试世界；
4. UI Tree 可以操作 Vanilla 和标准 Mod GUI；
5. Render Facts 与 Screenshot/Vision fallback 能实际完成无法语义化界面的操作；
6. 玩家可以通过已验证的 GAME_ROUTED 输入移动、转向和交互；
7. Container 可以通过 Screen → Menu → normal packet → server validation 路径操作；
8. 核心键鼠组合、长流水线、取消和输入清理可用；
9. Player/World/Entity 基础 Live Observation 可用，不隐式混用 Persisted Storage；
10. Runtime 内 wait/assert 可用，不依赖固定 sleep；
11. Screenshot、基础连续录制、基础拼接和 V1 Artifact 可用，并可与输入并行；
12. provenance/perspective/mechanism/evidence 可用且进入报告；
13. Integrated Server 可提供权威数据；
14. Dedicated Peer 可扩展服务器权威查询、权限和受控 Fixture/Debug；
15. PLAYTEST、FIXTURE、DEBUG_PRIVILEGED 和证据污染检测有效；
16. HTTP/WS 与 MCP Companion 能让 Coding Agent 完成自主测试闭环；
17. V1 loopback-only Profile 下 Auth、Principal、Scope、Host/Origin 校验、速率/并发预算、Control Lease、Debug Arm、Prompt Injection 隔离、审计关联和断线清理有效；LAN 不得被宣称为 V1 Ready；
18. Hook/Capability Self-Test 失败会诚实降级，不伪装完整能力；
19. 录制、查询和网络压力不产生不可控主线程阻塞；
20. 未进入 V1 的 Ultimate 能力在 capability 和文档中明确，不以占位实现冒充完成。

### 27.3 Ultimate Definition of Done

在 V1 基础上进一步满足：

1. 完整可配置连续帧录制、自动拼接和统一多轨 Recording；
2. 长期高频 World State Keyframe/Delta 可重建、索引和导出；
3. canonical recording store 使用可演进的版本化高效表示，不被 NDJSON 锁死；
4. 全领域强类型 DEBUG_PRIVILEGED 和批量边界测试覆盖达到公开 capability；
5. LIVE 与 PERSISTED 的读取/写入、世界指纹、一致性和副作用完整实现；
6. Rolling Recorder、Golden Diff 和 Human Operation Replay 可用；
7. 因果延迟分析和 Debug Tick Step 可用并正确标记非实时证据；
8. 网络追踪、Provider 扩展和高级 Diff 可用；
9. 高级 Crash-safe Finalization 与最后可用诊断资料恢复可用；
10. Ultimate 各能力通过对应 Target coverage、性能、安全和兼容测试。

---

## 28. 当前已冻结的产品决策

以下内容视为当前执行基线，后续如修改应记录 ADR：

1. 项目核心是 Minecraft Agent Control Runtime，不是内嵌 MCP Server；
2. 原生协议独立于 MCP；
3. 支持五个指定 Target；
4. Capability-first / Fidelity-first；Mixin 允许必要侵入但没有默认优先权，Loader API 不构成功能上限；
5. 观察以全能为目标，但读取型插桩不得主动影响其他 Mod；
6. GUI 以兼容且全能操作为目标，但不承诺所有第三方 GUI 都能恢复完整业务语义；
7. UI Tree 用于坐标解析，实际操作走游戏内输入分发；
8. 截图和多模态坐标点击是 GUI 必备回退路径；
9. 键鼠引擎以完整宏能力和长流水线为目标；
10. 世界操作保留 PLAYTEST、FIXTURE、DEBUG_PRIVILEGED 三个平面；
11. 调试级全能读写必须实现并保留；
12. 所有读取和写入均标记视角、来源、机制、副作用和证据价值；
13. 连续帧录制、自动拼接和参数化输出必须实现；
14. 帧录制与键鼠流水线必须并行且互不干扰；
15. 世界状态录制必须支持自定义轨道、选择器、Keyframe 和 Delta；
16. 输入、帧、世界状态、事件、网络和日志共享统一时间线；
17. Server Peer 用于服务器权威观察、录制、Fixture 和 Debug；
18. 默认 loopback；V1 Release Profile 仅允许 loopback。LAN 显式启用能力保留在 Ultimate Scope，并须先完成 ADR-0001 所列 TLS、配对、可撤销 Principal、IP Policy 和独立 Conformance；
19. 不提供任意 Shell、任意文件系统和通用 JVM RAT 能力；
20. 不使用全局 `expectedWorldRevision` 作为普通 optimistic concurrency，采用资源级 revision 与 value precondition；
21. Request Envelope 公共层最小化，Lease、Idempotency、Precondition、Operation Handle 和 Debug Context 按能力声明；
22. DEBUG_PRIVILEGED 对外保持 Minecraft 领域强类型，不提供通用 Reflection RPC；
23. Live World 与 Persistent Storage 使用不同来源和 namespace，不允许普通查询隐式混用；
24. 高频长期 Recording 的 canonical representation 不被 JSON/NDJSON 锁死；
25. Minecraft 文本只进入数据平面，不能动态改变 Tool、权限、系统指令或 Runtime Policy；
26. Ultimate Scope、V1 Product Scope 和 Phase 0 Vertical Slice 分层管理；
27. 不在架构验证前过早冻结 Wire Protocol v1。

---

## 29. 下一步立即执行项

当前唯一允许的下一步是对 **Phase 9C Deep Debug / Batch Boundary State** 进行独立审查；本轮不得自动开始 Phase 9C。

```text
Phase 8 Remote Parity: PASS
V1 Remote Release Candidate: PASS
Phase 9A: PASS WITH IDENTIFIED IMPLEMENTATION GAPS
Phase 9B.1: PASS
Phase 9B: PASS — CONTRACT HARDENED
Phase 9C Entry Gate: READY FOR INDEPENDENT REVIEW
Phase 10: NOT STARTED
Wire Protocol v1: NOT FROZEN
```

Phase 9 与 Phase 10 必须保持独立：Phase 9 完成 Conformance、Exit Gate 和独立审查后，才能另行启动 Phase 10。
