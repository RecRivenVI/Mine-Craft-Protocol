# Large-Modpack Compatibility Matrix

> Status: **PLANNED / NOT STARTED — 等待用户准备并固定四个 PrismLauncher 实例。**
> 本文只定义长期测试资产和未来 Gate；本轮不安装、下载、配置或启动任何整合包。
> Authority: [Core vision](../product/vision.md) · [Execution plan](../product/execution-plan.md)
> · [Control architecture](../architecture/agent-control.md) · [Threat model](../architecture/threat-model.md)

## 1. 长期固定矩阵

这是长期常驻的 Core Compatibility Matrix，不是一次性 Demo，也不是 E1/E2/E3。
下列 Minecraft/Loader 是**用户指定的测试对照目标**，不是“该整合包所有版本都相同”的声明。

| Test Pack | 固定 Minecraft / Loader | 本项目 Target | Role | 当前状态 |
|---|---|---|---|---|
| All the Mods 9（ATM9） | 1.20.1 Forge | `1.20.1-forge` | 旧 Forge / 大型历史生态兼容性 | NOT STARTED；pack version / instance ID 待用户固定 |
| All the Mods 10（ATM10） | 1.21.1 NeoForge | `1.21.1-neoforge` | 主流现代 NeoForge | NOT STARTED；pack version / instance ID 待用户固定 |
| All the Mods 11（ATM11） | 26.1.2 NeoForge | `26.1.2-neoforge` | 新一代 NeoForge | NOT STARTED；pack version / instance ID 待用户固定 |
| All the Mods 10 Aeronautics（ATM10 Aeronautics） | 1.21.1 NeoForge | `1.21.1-neoforge` | 独立极端兼容性目标：航空 / 物理 / 特殊渲染压力 | NOT STARTED；pack version / instance ID 待用户固定 |

**ATM10 Aeronautics 不是普通 ATM10 的替代品。** 两行必须分别测试、分别保留结果。
实例实际 manifest 的 MC/Loader 若与表中目标不符，先停止并报告，不自动换 Target、
改 Mod、升级 pack 或把另一个版本的 PASS 借来填表。版本升级产生新 baseline，旧结果保留。

## 2. 两层 Gate，各自回答不同问题

```text
Deterministic Core Gate             Large-Modpack Compatibility Gate
    ↓                                  ↓
五个干净 Target                      ATM9 / ATM10 / ATM11 / ATM10 Aeronautics
本项目自己的行为是否正确              复杂第三方生态下是否破坏其他 Mod、Core 是否仍可用
```

- 五个干净 Target Gate 始终保留；三个 Forge/NeoForge pack 版本不覆盖
  `26.2-neoforge` / `26.2-fabric` 的干净基线。
- Pack PASS 不替代 Core conformance、安全或统一真人验收。Vanilla PASS 也不证明 Pack 兼容。
- [Control UI Showcase](control-ui-showcase.md) 用于观察视觉与交互，不是上述任一最终 Gate。
- 这是 Core 兼容性验证，不是自主游玩、任意 JVM 探索或 Render Forensics 的实现。
- 不自动把尚未固定版本的四包全量 Tier 结果加为第一次 Developer Preview blocker；
  具体发布范围由执行计划/Core 合同决定。宣传某个包的兼容性必须有对应版本证据，
  已确认的输入/数据安全或核心正确性缺陷仍按 Core blocker 处理。

## 3. 固定 PrismLauncher 工作流与责任

测试环境根目录约定为 `E:\Minecraft\PrismLauncherDev`。本轮未检查或修改该目录，
也不假定可执行文件、application data root、instance root、game directory 必然相同。

**用户负责**：

- 手动安装四个独立 PrismLauncher 实例，选择/固定 pack version；
- 准备可牺牲的专用测试世界及合法游戏账号；
- 维护这些实例为长期测试资产，决定何时升级/重建；
- 需要物理 Esc、Alt+Tab、真人点击、焦点变化时亲手操作。

**Astra / Coding Agent 在用户明确告知实例准备完毕并授权测试后负责**：

1. 只读核对四个实例 ID、实际 game directory、pack/MC/Loader/Java 和配置版本；
   首次记录 baseline manifest/mod-list hash。路径或版本不明确就停止，不猜 display name。
2. 对正确 Target 执行标准 `gradlew :versions:<target>:build`，记录 exact commit、
   clean/dirty 状态及最终发布 JAR SHA-256。正式 Compatibility PASS 不使用 dirty candidate
   或 Gradle/sourceSets 开发 classpath 代替最终产品。
3. 把该 Target 的最终 JAR 部署到已确认 game directory 的 `mods/`。
   检查旧 Mine-Craft-Protocol、重复 Mod ID/class 和嵌入依赖；只处理本项目制品，
   旧制品可恢复地留在测试产物目录、不能继续被 Loader 扫描。不要另装 runtime-safety
   来掩盖嵌入失败，不移动/删除其他 pack Mod。
4. 仅配置本项目必要的 connection / loopback port / token handoff / scopes。
   token 不写进 Git、报告或普通日志。默认只读权限；Fixture/Debug 仅在对应 Tier
   明确启用并使用 Debug Arm。不得给 pack 普通 Mod 改配置来掩盖兼容问题。
5. 用 **PrismLauncher CLI** 启动已核对的实例；不导入、不下载、不自动安装、升级或重建 pack。
   一次只运行一个明确用途的游戏实例，前一个清理后再启动下一个。
6. 通过 Mine-Craft-Protocol 的 Native HTTP/WS 或 MCP Companion 连接真实 Runtime；
   正常 GUI/玩家操作使用 Interaction Tree / GAME_ROUTED，禁止 Codex Computer Use、
   CUA、桌面自动化和宿主输入注入。
7. 分层执行下述 Gate。Debug/Fixture 只作独立标记的 Arrange，不冒充玩家 Act；
   mode 不授予 scope/Lease/Arm。人工撤销后不自动重新接管。
8. 正常 Save & Quit / 关闭 Minecraft，检查输入、Lease、Operation、Runtime 端口和
   Java 客户端进程清理。Prism Launcher 本身仍开着不等于游戏没退出。
9. 从**实际 instance/game directory**收集当前 run 的 `logs/latest.log`、
   存在时的 debug log、`crash-reports/`、Loader/Mixin 错误、`hs_err_pid*` /
   native-crash 文件和本项目 trace/audit。记录时间范围/偏移、进程/会话和日志 hash，
   不把上次的 latest.log/crash report 当成本轮结果。
10. 比较测试前后 mod list / config manifest；任何未批准的 pack 变化必须报告。
    只提交精简、脱敏、版本绑定的结果；整合包、世界、缓存和大量日志不进入项目 Git。

必要普通 Mod 配置变更只能在测试明确要求并单独记录时进行，作为 controlled variant；
不得把这种结果写成原始 pack baseline。Astra 不负责自动安装/升级/重建整合包。

## 4. CLI 使用依据（计划示例，不在本轮执行）

Prism 官方 CLI 支持 `--dir` 指定 application root、`--launch` 按 instance ID 启动；
instance ID 是实例文件夹名，不一定等于 UI display name。
参见 [Prism CLI 官方说明](https://prismlauncher.org/wiki/getting-started/command-line-interface/)。

未来执行前，以用户安装版本的 CLI/help 和实际配置确认路径；不要从 pack 名拼猜目录。
示意命令：

```powershell
$prismExe = '<已核对的 PrismLauncher.exe 完整路径>'
$prismDataRoot = '<已核对的 Prism application root>'
$instanceId = '<用户固定实例的实际 folder ID>'
& $prismExe --dir $prismDataRoot --launch $instanceId
```

本轮不会为验证这些命令而启动 Prism。也不使用 import、自动安装或升级参数。
若配置使用独立 `InstanceDir` 或自定义 game directory，读取实际配置后再定位
`mods/` 和日志，不硬编码 `instances/<id>/.minecraft` 或 `minecraft` 为唯一布局。

## 5. 长期分层 Gate

所有 Tier 都需记录期望、实际、证据、清理结果。前一层不通过时，不继续制造更复杂负载；
可报告分层 PARTIAL，但不能给出未经执行的总 PASS。

| Tier | 执行范围 | 至少需要的结果 |
|---|---|---|
| **0 — Boot** | 正确最终 JAR、Loader 启动、标题页、Runtime readiness | 无 crash、Mixin apply failure、duplicate class、classloader/native crash；确认实际 Loader 解析了嵌入依赖 |
| **1 — READ / passive coexistence** | 安装本项目但不接管玩家；世界正常运行、观察/只读采证、代表性第三方 Mod 正常使用 | 不获取输入 Lease、不发玩家事件；日志无本项目引入的持续异常；原生游戏/Mod 行为不被 passive hooks 破坏 |
| **2 — OPERATE** | 专用世界内，授权 Fixture / Typed Debug + Observation / Recording | 非玩家 mutation 有 scope/Arm/resource/value guard；READ/Recording 仍是读兼容能力；无隐式 TAKEOVER/Lease；恢复 Arrange 状态、分离 diagnostic evidence |
| **3 — TAKEOVER** | 真实 keyboard/mouse/camera、Lease、独占真人游戏输入、Host cursor 独立、Virtual Pointer | GAME_ROUTED、无宿主 cursor grab/warp；Lease loss/取消清理；Agent Esc 不撤销，真人 Esc 退 READ+latch；物理行为只能用户做 |
| **4 — Real Mod UI** | 多个实际第三方 Screen：Widget、Slot、Container、tooltip、hover、scroll、drag、dynamic UI | 真实 Virtual Pointer/正常 Menu/packet 消费；动态失效不会点击旧坐标；覆盖不足诚实降级，不猜任意 shader/texture 内部语义 |
| **5 — Stability** | 事先约定的较长运行：chunk loading、维度切换、多种 GUI、Recording、重复模式转换 | 无持续错误/卡死/内存或队列失控；预算/gap 诚实；取消与最终退出清理；逐项复查当前日志 |

Tier 1 专门回答：**“只安装我们的 Mod，会不会把整合包搞坏？”**
测试前先保留用户确认可启动的 pack baseline/log；必要 A/B 对照只改变本项目 JAR，
不顺手禁用其他 Mod。只有 READ RPC 成功而没有 pack 行为/日志对照，不足以回答这个问题。

Tier 4 在版本固定后选择实际存在的 Mod/Screen，登记 Mod ID/version、Screen、
目标/slot 和前后状态。现在不虚构四个尚未安装实例中有哪些 GUI。
缺语义节点时可记录 Render Facts/Vision fallback，但不能把 fallback 写成 semantic PASS。

Tier 5 开始前固定时长、动作次数、观察/录制预算和性能阈值；无预设阈值不得事后挑
“看起来正常”的数字。能实际获取时记录 FPS/TPS、延迟、heap、队列深度、recording
growth/gaps/drops；不可测项标 N/A/limitation。丢样本记录 gap，不通过阻塞 Minecraft
核心线程制造“0 gap”。这不是自动启动 Phase 9G 或 Recording V2。

## 6. 每次结果必须版本绑定

未来每一份结果至少包含：

- pack full name、pack version、发布来源/manifest；
- Minecraft version、Loader 名称及精确版本；
- Prism version、instance folder ID、真实 instance/game directory（公开报告可脱敏）；
- pack 原始 mod list / manifest hash，部署后 mod list hash及配置 baseline/variant hash；
- Mine-Craft-Protocol **exact source commit**、worktree cleanliness、Target、
  最终 JAR SHA-256、嵌入依赖/Loader resolution 证据；
- 实际 Java vendor/version、JVM 参数、GPU/驱动、实际 renderer/backend（含 fallback）；
- 日期、Runtime session identity、测试世界/fixture identity、case/Gate revision；
- Tier 0–5 各项 expected/actual/result、日志/Artifact 引用、失败/limitation、cleanup；
- 人工动作证据由谁执行、Runtime/Event/Audit 如何对应；未做则 NOT RUN；
- Fixture/Debug/PLAYTEST 的来源和 contamination，不把 mutation 当 gameplay；
- Persistent Write API 调用计数应为 0（正常 Vanilla 保存测试世界另行标记）。

不得只写 `ATM10 PASS`。PASS 只覆盖所记录版本、配置、后端和执行范围。
Pack、Loader、Java、GPU driver、mod/config manifest 或本项目相关代码升级后，
相关 Tier 的历史 PASS 变成待重新验证，不覆盖旧文件或复用旧日志。

建议结果词汇：PASS / FAIL / PARTIAL / NOT RUN / NOT APPLICABLE（需理由）。
任何适用 Tier 未执行时只能报部分覆盖；N/A 不允许隐藏 Boot、READ、
TAKEOVER 安全或清理失败。ATM10 Aeronautics 的成功也不代替普通 ATM10 行。

## 7. 触发与首轮 Entry 条件

- 首轮：用户明确确认四实例已安装、pack version 已固定、测试世界可用，并授权 baseline。
- 后续：本项目输入/Hook/Pointer/capture/runtime-safety packaging/生命周期相关变化、
  pack 或 Loader 升级、或实际兼容性事故，触发对应 Tier 回归。
- 长期人工排期由用户决定；本文不会创建自动化监控、下载器或持续后台客户端。
- 若有错 Target、缺 manifest、未固定版本、未知实例路径、重要世界混入或账号/权限不明，
  Entry 保持 CLOSED，先补事实；不创建替代实例来凑齐矩阵。

当前四行全部 **NOT STARTED**。下一步仅是等待用户完成四个固定 Prism 实例并明确
授权第一次 Compatibility Baseline。Persistent Write、Phase 9E/F/G/10 与 E1/E2/E3
均不因本计划而启动。
