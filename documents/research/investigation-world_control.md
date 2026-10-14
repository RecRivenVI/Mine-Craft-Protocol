# 世界领域三模式二类别应用调查

2026-10-14 调查：世界领域可以复用现有控制、安全与观察基础，但不能把 CLIENT_KNOWN 直接当作 USER。
本记录提出感知投影与语义输入的方案，尚未实现或冻结新接口；领域里程碑状态由[执行计划](../project/plan-execution.md)负责。

## 问题

用户将首个里程碑拆为 UI 与世界两个独立交付目标，暂停 UI 推进，先研究世界应用方式。
两个领域采用 READ、TAKEOVER、OPERATE 三模式与 USER、DEBUG 二类别；模式分层但不自动授予权限。
世界 USER 要表达玩家实际可感知的信息和可手动复现的操作，不能因 Agent 能读客户端内存而获得透视。
世界 DEBUG 要明确内部信息与 typed mutation，不通过玩家模拟或任意对象后门实现。

本轮只研究和同步治理；不启动实例、不实现世界分类、不修改 UI、存档、模式、Lease、Arm 或截图隔离。
正式 Persistent Write、自动寻路、长期自主游玩、完整 GPU 取证、其他 Phase 与 E1/E2/E3 不进入本研究的实施授权。

## 方法

基线为 develop 的 c6a86904a2a465b63b58b42b1e25cef774351f5b 和现有未提交工作树。
开始时 414 项 dirty，无暂存；源码结论来自当前工作树，不用该 HEAD 的旧产品制品证明新能力。
静态读取[控制模型](../design/concept-agent_control.md)、[威胁模型](../design/security-threat_model.md)、
[Core 范围](../project/scope-core.md)、[Native 合同](../reference/protocol-native_v0.md)及 OpenAPI。
参考端检查 NeoForgeProbeRuntime、ProbeTransport、Phase9ASpikeEngine、ObservationEngine、
ProviderExecutionEngine、ConditionEngine、RecordingEngine、EventHub 与 DebugMutationAuthorization 的生产路径。
其余四端核对同类入口与类别接线；没有新增游戏运行、协议 smoke 或五端世界 Live 验收。

## 发现

### 可复用基础与实际缺口

| 基础 | 源码事实 | 新模型影响 |
| --- | --- | --- |
| 玩家输入 | 五端已有 TAKEOVER/Lease、GAME_ROUTED 键鼠与 Vanilla 相对视角 | 可复用控制路径，不直接写位置、yaw/pitch 或绕过原版攻击/使用 |
| 客户端方块 | 已缓存 Chunk 检查后可读取请求坐标的 BlockState，不加载 Chunk | 未经过视野/遮挡/感知过滤，不能整体归为 USER |
| 附近实体 | 参考端查询玩家包围盒附近实体并返回 UUID、精确位置和速度 | 不是可见实体列表；墙后、背后或不可见实体不能因收到同步数据进入 USER |
| 玩家状态 | 当前返回精确坐标、血量、速度等内部字段 | 应按实际显示来源逐字段投影，而非给整个响应换类别名称 |
| Deep Observation | 已有 client_known/server_authoritative/both、预算、取消、资源 revision | 是内部观察基础，不是 USER 感知证明；客户端视角本身也不等于可见性 |
| typed Debug | 参考端存在 health、attribute、entity.no_gravity、world.block、BE 名称、menu.slot 等操作及 Provider mutation | 可复用 Arm/版本/值前置条件，仍不是所有世界内部操作完整支持 |
| 消费旁路 | Provider/State Frame、Wait/Assert、Recording、Event resync 可读取现有状态 | 分类必须覆盖这些消费者，不能只改单个 HTTP 响应 |
| 玩家命令 | 当前经 sendCommand 送出，结果立即标 accepted、NORMAL_NETWORK 和 serverValidation | 表示发送/校验路径，不等于已收到执行成功确认，也不是 GAME_ROUTED 键盘输入 |

当前树的 UI 分类不证明世界分类已存在：五端 transport 都有 uiCategory 接线，世界入口尚无同等类别门禁。

| Target | 现有世界读取/命令/Deep 入口 | 世界 USER/DEBUG 全链合同 |
| --- | --- | --- |
| 1.20.1 Forge | 静态确认存在 | 尚未接入 |
| 1.21.1 NeoForge | 静态确认存在 | 尚未接入 |
| 26.1.2 NeoForge | 静态确认存在 | 尚未接入 |
| 26.3 NeoForge | 静态确认存在，参考端详细阅读 | 尚未接入 |
| 26.3 Fabric | 静态确认存在 | 尚未接入 |

该表不是五端运行 PASS，不改写既有 Phase 8/9B/9C 的历史验收结论。
基础服务器读取有显式 Peer 路径，而参考端 formal Deep 路径使用 Integrated Server；
不能从基础 Peer 成功推定完整世界 Deep/Debug parity，仍需逐项 capability 和实际连接验证。

### 建议的映射

| 模式与类别 | 世界领域建议语义 | 明确不允许 |
| --- | --- | --- |
| READ + USER | 玩家当前感知的自身表现、已观察世界内容、瞄准目标和实际可见交互线索 | 自动转头/移动去探测，墙后扫描、隐藏库存、内部 AI、seed 或调度状态 |
| READ + DEBUG | 经独立读取授权获取已同步内部状态或实际可用服务器状态 | 自动获得写入 Arm/Lease，强制加载或把未收到的数据虚构为存在 |
| TAKEOVER + USER | 真实移动、瞄准、跳跃/潜行、攻击/使用、物品选择等原版玩家路径 | 直接 teleport/setBlock/改库存、服务器高权限绕过、静默升级类别 |
| OPERATE + DEBUG | 有 scope/Arm/资源与值前置条件的 typed 世界修改、Fixture 和诊断 | 模拟 WASD/GUI 来替代刚被撤销的接管，任意 NBT、对象、packet 或路径 RPC |

类别回答“信息/操作是否属于玩家层”，authority 回答“数据由谁持有”，两者独立。
CLIENT_KNOWN 可包含 DEBUG 数据；SERVER_AUTHORITATIVE 不会因更准确而自动成为 USER。
PERSISTED 保持 last_saved_state 与独立 storage.read，不冒充当前世界感知；其类别迁移方案还需明确，
本轮不改变已有权限，也不启动持久化写入。技术身份/token 是定位元数据，不等于玩家看到了内部值。

### USER 感知层

建议先做 Runtime 内的有界玩家感知投影，而不是先把完整状态发给 Companion 或网页再过滤。
每个字段说明感知来源、采样 tick、精度、显隐、时效和限制；范围、字段、列表数量与处理时间都有预算。
未知可见性返回未支持/不可感知，不退回完整客户端状态；过滤发生在查询、计数与断言之前。

- 自身血量/饥饿等只提供实际呈现的语义和精度；隐藏属性、精确内部值不能因“属于自己”自动进入 USER。
- 坐标/朝向遵循当前确实显示且未受 reduced-debug 等条件限制的原版信息来源；未打开 F3 时不偷偷替用户打开。
- 方块/实体从当前可感知目标与已观察结果出发，不用半径扫描代替视野。普通未知箱子不暴露内容，
  打开后交给既有 USER 菜单读取，关闭后的历史信息须标记旧观察，不能持续刷新隐藏库存。
- 视野、距离和简单射线是候选约束，不是最终像素可见性的充分证明；透明方块、部分遮挡、
  隐身、雾、暗处、第三人称及旁观视角都需具体规则。native hitResult 也不能独自证明实体可见。
- 若原版允许当前旁观玩家观察某内容，就按当前合法视角判断，而不是永久套用生存模式限制。
- 玩家通过正常操作主动揭示内容后可以读取新观察，READ 本身不代替玩家操作。
- USER 不意味着必须使用截图识别；结构化语义可以代表玩家所见，但不能多出玩家无法取得的情报。

“当前可见”与“经过正常操作可能获得”必须分开；后者先报告需要何种动作，不自动执行或预先返回内容。
复杂感知的首版范围、坐标精度和声音线索仍需冻结，不能承诺几何筛选即全部可见世界，也不自动开启 E3。

### 世界语义目标与真实输入

世界不是控件树，建议使用有界感知帧和不透明 targetRef；名称仅是设计占位，不是已发布 API。
引用绑定 session、世界/维度、玩家/观察视角、对象生命周期和采样上下文；同坐标替换、实体重建、
换维度、过期引用或目标变得不可感知时拒绝，不能把旧坐标当成仍有效对象。

建议链路为：感知目标 → 解析动作 → 经 Vanilla mouse delta 瞄准 → 最终复验 → 原版攻击/使用 → 等待实际反馈。
复验包括 mode/Lease、世界与对象身份、所持物品、可感知性、原版命中/距离及期限；
当前无法合法触达时明确失败，不 teleport、不强制加载、不隐式规划路线。
原始 GAME_ROUTED 低层输入仍可用；语义 helper 只是有界玩家输入编排，不代替原版冷却、碰撞、权限或服务器校验。
挖掘过程中自己造成的进度/最终方块变化需明确处理，不能既把正常完成误判失效，也不能忽略无关目标变化。
动作状态区分输入已发送、客户端观察、实际服务器确认及失败；现有 Operation 状态不被单个机制标记替代。

PLAYER 命令是额外边界：真人可在聊天中合法提交的命令不等于 Runtime typed Debug。
建议 USER 保留当前玩家实际权限，但若要求完全模拟真人，应明确采用聊天 UI/Enter 路径；
现有 NORMAL_NETWORK 路由不能改写成 GAME_ROUTED 证据。作弊/管理员命令的状态效果也不能冒充自然游玩。
命令是否纳入首版、允许哪些操作及证据判定仍待决定，不在本轮改变玩家命令行为。

### 共用安全、领域桥接与证据

不建立第二套 Mode 或 Lease：领域是每项操作的分类，不是另一种会话状态。
物理 Esc 对两个领域同时结束 TAKEOVER、清理输入并回 READ；READ 观察继续，
OPERATE 按独立授权可用且不清除重新同意 latch。取消、断线、期限、关闭沿用现有安全机制。
同一玩家的 UI 和世界输入共用有序 admission；打开背包、交互箱子等动作将后续步骤交给现有 UI 合同，
Screen/维度变化使不适用的世界 gesture 失效，不复制另一套 Inventory 系统。
世界对象与菜单视图的编号/版本不混用；UI 暂停只暂停其开发，不禁止使用已实现 UI 完成世界测试。
领域描述交互入口，写入授权还须按实际受影响资源检查：UI 上下文不能代替世界指纹授权玩家/箱子库存写入。
UI Arm 保持客户端 session/uiContextId，世界 mutation 保持实际世界/namespace 绑定；跨领域动作不混用两种凭证。

世界 DEBUG 读取建议有明确独立 scope，普通读取不自动得到它；读取不要求 mutation Arm。
具体 scope 名称、旧宽泛 read 接口迁移/兼容方式和错误 schema 尚未冻结，不能现在宣称已经隔离。
Provider 未经 Runtime 可见性投影不能自行声明输出为 USER。Wait/Assert 的真假、Event 数量/重同步、
State Frame、Recording stateReads、Artifact 也不能成为隐藏世界数据的旁路。
USER 目标不可感知时错误不泄露该隐藏对象是否存在；有界 DEBUG 查询独立报告来源、加载状态和限制。

数据类别不直接映射证据等级：USER 输入仍需证明原版路径与实际结果；Fixture/Debug 修改仍标诊断。
只读 DEBUG 服务器断言不修改世界，可以作为独立诊断层验证真实玩家动作，但不能声称是用户目视证据，
也不应仅因读取类别为 DEBUG 就伪造一次 mutation contamination。Arrange、Act、Assert 和来源分别记录。
OPERATE 与尚在执行的 TAKEOVER 不暗中并行修改同一对象；显式切模式先取消并清理玩家输入，
独立外部世界变化通过目标复验/资源前置条件处理，不假定整个 Minecraft 世界被 Runtime 锁住。

## 结论

方案可行；主要新工作是世界 USER 感知边界与真实输入语义编排，不是重写 Mode、Lease 或 Phase 9C。
UI 里程碑暂停，成果保留；世界里程碑进入研究，现有世界能力未被新模型全面验收。

建议实施顺序（均未启动）：

1. 冻结感知/精度/命令/领域桥接和首版世界覆盖清单，分类全部现有入口与消费者，不改变旧能力的历史结果。
2. 实施最小 USER 感知投影及明确授权的 DEBUG 读取，关闭 Provider、Wait/Assert、Event、Recording 的泄漏旁路。
3. 将目标瞄准、攻击/使用、移动与物品交互接入现有 TAKEOVER 和真实反馈，不加入自动寻路。
4. 按完整清单补 typed 世界 DEBUG、Fixture 和状态恢复，独立授予权限，不声称现有少数修改已覆盖全部。
5. 用固定世界副本和五端矩阵验收，再交付世界用户操作界面；界面形态尚未确定，不把 UI 网页直接等同完整世界控制。

固定夹具应覆盖墙后/背后/隐身实体、未知箱子、透明与部分遮挡、F3/reduced-debug、超距离、
同坐标替换、实体重建、维度/Screen 变化、原版拒绝、取消/Lease loss/真人 Esc 和未加载区块。
逐字段检验 USER 无隐藏数据、DEBUG 无缺失权限绕过及消费者同等隔离；正常 Act 与 Fixture Arrange 分别采证。
客户端收到数据、原版目标命中、服务器持有状态、最终画面可见和操作成功是不同证据，不能互相推定。
五端应分别验证单人、实际支持的 Peer 与无 Peer 远程环境；EULA/账号/服务前提不能替用户接受或伪造。

这不是世界功能 PASS 或实施批准；本轮无新 Runtime/协议行为，无 Minecraft/Persistent Write 调用，无提交或推送。
