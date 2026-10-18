# Agent Control 与 Core 功能可重复展示

## 目标

每个案例使用有界逻辑任务：等待不闪断，结束明确 end。`chrome-spring` 在主菜单展示
READ/OPERATE 蓝边呼吸、弹簧移动及 Options/Done 切页位置保留；参数保持既有设计。
从主菜单重复观察 READ / OPERATE / TAKEOVER、发光圆球、点击反馈、背包交互和界面切换。
入口登记五个 Target，包含17个自动案例与一个独立真人Esc案例；当前候选的执行状态以[状态页](../../documents/project/status-milestones.md)为准。
这不是两个领域里程碑、统一人工验收或视觉 PASS。
所有自动操作通过本产品 Runtime；禁止 Computer-Use、CUA 和宿主输入注入。
指针、动画与节奏使用当前产品实现，Runner 不另加一套动画。

## 运行

开发自动回归使用同一Runner的自动预设：1920×1080、静音、client-multiplayer角色，不加载人工验收附加Mod。
`-Automatic`只选择实例预设，不替用户评价视觉；`-All`固定执行17项，不包含真人Esc。

```powershell
pwsh -NoProfile -File components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiShowcase.ps1 -Target 26.3-neoforge -All -Automatic -HoldSeconds 0.35 -Offline
pwsh -NoProfile -File components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiShowcase.ps1 -Target 26.3-neoforge -Smoke -Offline
```

Toast夹具通过明确标记的诊断页按钮触发；新增NeoForge夹具仅在probe验证模组且showcaseFixture开启时接入，不进入产品JAR。
工具必须等到接口实际读到只读的Tutorial/System两类原生绘制文本；仅按钮存在、点击成功或Capture成功都不能代替该断言。
这不证明自然游戏事件触发、Chrome的像素遮挡关系或人工观感，物理Esc仍只在单独人工案例中等待用户。

从仓库根目录使用 PowerShell 7。简化入口默认 26.3 Fabric、2560×1440，
复用一个专用客户端；每次执行前请先手动回到主菜单。
若仍在世界或其他菜单，脚本拒绝执行，不会替用户退出世界或操作未知界面。

```powershell
$ui = './components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiPreview.ps1'
& $ui -PrepareClient
& $ui -ListCases
& $ui -Case chrome-spring -HoldSeconds 5
& $ui -Case cursor-rule -HoldSeconds 4
& $ui -Case pause-pointer -HoldSeconds 4
& $ui -Case inventory-pointer -HoldSeconds 4
& $ui -Case pointer-move
& $ui -Case takeover-gui
& $ui -Case gui-scenes
& $ui -Case mode-transition -HoldSeconds 5
& $ui -All -HoldSeconds 4
```

`-PrepareClient` 只准备主菜单，不执行案例，不申请 TAKEOVER。
已有客户端会被复用，不重复启动；成功案例会清理测试物品、释放 Lease、清空 held input，
正常保存退出世界并保留客户端于主菜单。切换 Target 前请先正常关闭当前客户端。
Forge 使用同一入口的 `-Target 1.20.1-forge`。

| Case | 主要观察内容 |
| --- | --- |
| `read` | 标题页与世界 HUD，弱蓝边、Presence 淡入淡出 |
| `operate` | 安全物品布置与恢复，中等蓝边，不模拟玩家 |
| `takeover-gameplay` | 短按 W/S、Vanilla 相对视角；第一人称隐藏 GUI 圆球 |
| `pointer-move` | 长短距离移动、轻微回弹、Slot 悬停与物品提示 |
| `takeover-gui` | 8 块石头的点击收缩、拿起/放下、格子间移动、真实拖拽 |
| `mode-transition` | 同一背包界面内三轮常规切换，再执行三轮100ms间隔的连续转向并记录实际模式/时间 |
| `ui-overlay` | 明确标记的 Fixture Tutorial/System Toast 与背包/暂停界面叠层 |
| `gui-scenes` | 标题页、背包、暂停、设置、仅滚动不改语言的语言列表 |
| `chrome-spring` | 只在主菜单/设置：#339CFF 三种模式、3 秒蓝边呼吸、长短弹簧运动、点击缩放与 Fade；不进入世界 |
| `cursor-rule` | Options/Done 普通换页保留；第一人称隐藏、打开背包归中显示、Pause/Options 保留；正常保存退出 |
| `pause-pointer` | 暂停菜单的游戏释放中心保留小数，再只悬停 Options 按钮真实中心；不点击、不补偿输入 |
| `inventory-pointer` | 背包游戏释放中心，再只悬停有石头的 Slot 中心；恢复本次布置的物品 |
| `world-observe` | READ 下分别展示 USER 有界客户端模型与独立 DEBUG 服务端读取，不持有输入 Lease |
| `world-interact` | typed DEBUG 在已知空气布置工作台；USER targetRef 瞄准、普通使用、Assert 打开工作台，再恢复 |
| `wait-evidence` | Recording → 真实背包、语义聊天草稿输入/取消 → 条件 Wait/Assert → Capture → Artifact，核对四类证据与校验和 |
| `persistent-read` | 正常 Save & Quit 后读取 world/player/chunk，明确 PERSISTED / last_saved_state，不回退 LIVE |
| `cancel-cleanup` | 等待 W 实际按住后取消 Operation，确认 READ、held input=0、后续视角输入不晚到 |
| `manual-esc` | 可选真人物理 Esc，等待 Runtime 撤销事件，不自动模拟 |

`-All` 按表顺序执行十七项自动案例，不包含 `manual-esc`，也不属于自动最终验收。
`-HoldSeconds` 为 0.25–60 秒，默认 4 秒，仅控制观察停留，不改变指针速度。
每个案例使用独立 begin/heartbeat/end 任务，短连接或任务内停留不会打断提示与呼吸。
`read` 中另有 17 秒不轮询 API 的区间，用于观察同一活动任务的视觉连续性；
任务结束才淡出，不以网络请求边界切换视觉状态。
Toast 独立到期，因此较长停留不会延长 Toast 生命周期。

### Arrange / Act / Assert / Evidence 索引

| 案例 | Arrange 与 Act | Assert、证据与恢复 |
| --- | --- | --- |
| read、world-observe | GUI进入自有世界后释放输入Lease；READ分别读取USER与独立授权DEBUG | 明确类别/authority，不加载区块；结果记录有界样本。进入世界的导航不冒充READ输入 |
| operate、pointer-move、takeover-gui、inventory-pointer | OPERATE预留四个空槽并放8个石头；TAKEOVER语义hover/click/drag | USER槽位与所持物品总量、落点中心和held=0；仅清理本次预留的已知物品。Arrange/Cleanup为DEBUG_PRIVILEGED |
| takeover-gameplay、mode-transition、cursor-rule、pause-pointer | 原生GUI/相对输入与显式模式切换；不直接修改视角 | 接口核对逻辑捕获/显隐、坐标/游戏请求版本及held=0；正常回标题，不判断视觉质量 |
| ui-overlay、gui-scenes、chrome-spring | 原生界面与明确标记的Fixture Toast；其余为正常导航/停留 | 两类实际绘制文本、Screen和指针状态；不宣称Toast像素遮挡或自然教学效果已验收 |
| world-interact | DEBUG在已知空气布置工作台；USER语义targetRef瞄准并使用 | 独立USER Screen Assert，按原世界/资源版本恢复；混合来源不计为干净PLAYTEST Arrange |
| wait-evidence | Recording内执行真实背包与不提交的聊天草稿、Wait/Assert/Capture | 核对实际ZIP的manifest、timeline、frame、contact sheet和全部checksums；关闭自己的草稿，不检查图片内容 |
| persistent-read | Vanilla正常Save & Quit，再读取刚保存的专用世界 | world/player/chunk均PERSISTED/last_saved_state；无LIVE回退、Persistent Write=0 |
| cancel-cleanup | 等到W实际按住后取消，后续步骤含相对视角输入 | READ、held=0、无late input并正常退出；不是物理Esc证据 |
| manual-esc | 只请求用户亲手按Esc，Runtime等待撤销事实 | latch与零输入；不自动重新接管，可能保留夹具/客户端等待用户明确同意后恢复 |

普通案例结果为接口执行记录；只有wait-evidence生成完整Artifact Bundle，不为每个案例虚构截图或Gameplay Acceptance。
失败时只释放Runner自己的许可；遇到未知物品、世界变化或资源版本冲突保留夹具并报告，不猜测恢复。
KeepClient只在安全回标题后保留自有客户端；默认正常退出并核对进程/Runtime端口。

高级 Runner 保留原来的默认收尾关闭行为；显式 `-FromTitle -KeepClient` 等价于预览入口的保护：

若专用世界已被试用装满，先正常关闭该自有客户端，可在高级 Runner 显式加 -FreshFixture。
它只在所有权与 Runtime/session 锁通过后，将整个旧 client 原样移到本 Target 的 retired 目录，
再建空实例；不会清空原库存、删除旧存档或自动重放失败动作。默认不归档、不重建。

```powershell
./components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiShowcase.ps1 -Target 26.3-fabric -Case pointer-move -FromTitle -KeepClient
./gradlew.bat :validation_tools:check
./components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiShowcase.ps1 -Target 26.3-fabric -Smoke -FromTitle -KeepClient
./components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiShowcase.ps1 -Target 26.3-fabric -Smoke -FromTitle -FreshFixture
./components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiShowcase.ps1 -Target 26.3-neoforge -CoreSmoke -FromTitle -Offline
./components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiShowcase.ps1 -Target 26.3-fabric -Case world-interact -HoldSeconds 4 -KeepClient
./components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiShowcase.ps1 -Target 1.20.1-forge -Case persistent-read -HoldSeconds 4
```

`-CoreSmoke` 只以短停留串行执行新增的五个 Core 案例，不执行 `-All` 或真人案例。
两种入口都支持 `-Offline`，显式使用已有的 Gradle/Loader 缓存；缺少依赖仍会失败，不切换依赖或自动下载。
首次在线 Forge smoke 因依赖仓库 TLS 握手失败而未进入游戏，不记为功能 PASS。
高级 Runner 同样支持 `1.21.1-neoforge` 与 `26.1.2-neoforge`；每次只运行一个客户端。
首次生成的专用世界及各次演示后的正常存档是可变展示资产，不是不可变的里程碑验收基准。
固定副本的自动回归仍使用独立的 world-read / vanilla-ui 验证。

## 实例与安全边界

专用实例位于 `validations/feature-control_ui_showcase/instance/<target>/client/`，
不使用日常实例或重要存档。Runtime 端口由 OS 分配，按该实例的发现记录固定启动身份；世界、token、缓存与日志不进 Git。
使用 Loader 原生开发 source-set 启动，不把这次预览当作最终 JAR 运行验收。

首次通过 Vanilla GUI 创建独立 Survival / Peaceful / Superflat 世界，使用默认超平坦模板；之后该实例必须只有一个世界。
创建草稿明确选择 World Tab 与原生 World Type，未找到控件或十二次切换仍未达到 Flat 时拒绝，不悄悄生成随机地形。
旧专用普通地形世界不自动转换；需要新夹具时须显式使用 FreshFixture，原目录仍原样保留。
新实例默认 English；已有实例保留语言，原生导航/退出使用 translationKey。
首次创建世界的难度草稿目前仍要求 English，不能由中文已有世界的导航通过推定任意语言的创建流程已覆盖。
普通展示client为2560×1440、RecRivenVI、master=0.5、music=0，并按版本装入登记的验收附加Mod。
Smoke/CoreSmoke使用另一client-multiplayer目录，1920×1080、全局静音、无附加Mod；不复用人工实例或冒充最终验收。
两者均保留语言及无关选项，原生准备任务统一应用声音；附加Mod选择在task/acceptance-mods.json，输入身份/哈希在inputs.toml。
普通背包区域至少四个空槽、carried stack 须为空。
Runner 每次从槽 9–35 中预留四个已确认空槽，不覆盖或移动原有物品；空间不足时拒绝。
物品使用现有 OPERATE、Debug scope/Arm、资源与值前置条件准备，属于 diagnostic evidence；
实际 hover/click/drag 使用 TAKEOVER + GAME_ROUTED，不把 Debug 布置冒充 gameplay。
仅清理本次最初确认为空的四个保留槽，遇到未知世界或物品会拒绝，不覆盖或删除。
世界列表必须有唯一 worldId 语义行，不回退为坐标；按钮/Slot 使用 Interaction Tree。
诊断物品准备/清理在 OPERATE 中使用显式 DEBUG 的内部菜单与世界指纹，验证配置独立授予 debug.world.read；
TAKEOVER 中的物品移动和鼠标所持断言只读取 USER UI Tree，不混入内部菜单状态。单客户端最大堆为 4 GiB。
工作台夹具另需 debug.world、debug.write、Arm 和资源/值前置条件，只修改观察到的空气位置。
清理绑定原世界指纹和修改后的 Resource Version，未知结果或外部修改不猜测恢复。
`world-interact` 的 Arrange/Cleanup 是 DEBUG_PRIVILEGED，Act 是真实玩家输入；输入响应不代表效果已确认，
成功依据独立 USER Screen Assert，不能把诊断布置算成干净的 gameplay acceptance。
`persistent-read` 只额外使用 storage.read，无 storage.write scope、路径写入或新产品路由。
`wait-evidence` 验证 ZIP 的真实成员与 SHA-256，不把默认排除 Operator UI 的产品策略当作本脚本已做像素隔离验收。
该案例最多录制六个一秒采样，避免长停留让 2560×1440 contact sheet 超出解码预算；较长 Hold 不代表全程都在录制。

`ui-overlay` 使用已有开发 Fixture 按钮产生 `Showcase Fixture` Toast，不新增产品路由。
真人 Esc 可随时结束接管；脚本停止且保留 reconsent latch，不自动重新接管。
中断时测试物品可能留在专用世界，须重新明确同意后收尾，不会在后续运行中静默覆盖。
未使用 Persistent Write API 或直接 NBT 写入；正常世界创建与 Save & Quit 会由 Vanilla 保存。

## 通过标准

工具单元/静态检查通过；代表端真实 Smoke 能执行点击、物品移动、拖拽、Toast 与菜单，
收尾到 READ / 主菜单、held keys/buttons 为 0、无遗留 gesture。
执行记录写入 `result/<target>.json` 的固定位置，标记当前提交、dirty 状态与开发 Runtime。
当前工具有85项入口检查与51项Core生产函数/实际ZIP数据检查；当前候选的执行范围见[状态页](../../documents/project/status-milestones.md)。
`mode-transition`在常规展示后记录九次短间隔模式切换的实际modeVersion和经过时间；它只证明接口执行、版本递进与清理，
连续动画和文字动态裁切另由共享生产绘制/时钟确定性测试覆盖，不把执行记录当人工视觉或截图像素结论。
结果要求sourceChangedDuringRun=false、201～206份实际源码/工具哈希匹配、自动1920×1080静音、实际最大堆4GiB、
held keys/buttons=0、端点移除、进程正常退出及Runtime端口释放。
每端两次读取Tutorial/System原生Toast绘制文本；语义拖动后槽位和所持石头总量为8，再清理已知夹具。
world/player/chunk实际返回PERSISTED/last_saved_state；Artifact ZIP真实成员和内容校验和通过，取消后的输入不晚到。
这些是Native开发Runtime的展示执行证明，不是完整原版目录、最终发行JAR或人工视觉验收；Operator UI像素隔离仍标NOT_PERFORMED。
历史失败及修复经过分别保留在[阶段记录](../../documents/project/phase-1-core_delivery.md)与[验证调查](../../documents/research/incident-milestone_validation.md)，
不将旧结果或本展示少量场景扩大为其他世界生命周期、全部语言创建流程或完整领域通过。
PrepareClient 的记录应有 `PREPARE_CLIENT_ONLY` 和空案例列表。
这些 PASS 只代表脚本动作完成，视觉质量由用户判断；不代替五 Target 统一验收。

相关入口：[Control 架构](../../documents/design/concept-agent_control.md)、
[执行状态](../../documents/project/plan-execution.md)。
