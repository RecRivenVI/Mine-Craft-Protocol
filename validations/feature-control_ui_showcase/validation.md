# Agent Control UI 可重复预览

## 目标

每个案例使用有界逻辑任务：等待不闪断，结束明确 end。`chrome-spring` 在主菜单展示
READ/OPERATE 蓝边呼吸、弹簧移动及 Options/Done 切页位置保留；参数保持既有设计。
从主菜单重复观察 READ / OPERATE / TAKEOVER、发光圆球、点击反馈、背包交互和界面切换。
支持 Forge 1.20.1、Fabric 26.3；准备状态 READY，不代表统一人工验收或视觉 PASS。
所有自动操作通过本产品 Runtime；禁止 Computer-Use、CUA 和宿主输入注入。
指针、动画与节奏使用当前产品实现，Runner 不另加一套动画。

## 运行

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
| `mode-transition` | 同一背包界面内三轮 READ → OPERATE → TAKEOVER → READ |
| `ui-overlay` | 明确标记的 Fixture Tutorial/System Toast 与背包/暂停界面叠层 |
| `gui-scenes` | 标题页、背包、暂停、设置、仅滚动不改语言的语言列表 |
| `chrome-spring` | 只在主菜单/设置：#339CFF 三种模式、3 秒蓝边呼吸、长短弹簧运动、点击缩放与 Fade；不进入世界 |
| `cursor-rule` | Options/Done 普通换页保留；第一人称隐藏、打开背包归中显示、Pause/Options 保留；正常保存退出 |
| `pause-pointer` | 暂停菜单的游戏释放中心保留小数，再只悬停 Options 按钮真实中心；不点击、不补偿输入 |
| `inventory-pointer` | 背包游戏释放中心，再只悬停有石头的 Slot 中心；恢复本次布置的物品 |
| `manual-esc` | 可选真人物理 Esc，等待 Runtime 撤销事件，不自动模拟 |

`-All` 按表顺序执行十二项自动案例，不包含 `manual-esc`。
`-HoldSeconds` 为 0.25–60 秒，默认 4 秒，仅控制观察停留，不改变指针速度。
每个案例使用独立 begin/heartbeat/end 任务，短连接或任务内停留不会打断提示与呼吸。
`read` 中另有 17 秒不轮询 API 的区间，用于观察同一活动任务的视觉连续性；
任务结束才淡出，不以网络请求边界切换视觉状态。
Toast 独立到期，因此较长停留不会延长 Toast 生命周期。

高级 Runner 保留原来的默认收尾关闭行为；显式 `-FromTitle -KeepClient` 等价于预览入口的保护：

```powershell
./components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiShowcase.ps1 -Target 26.3-fabric -Case pointer-move -FromTitle -KeepClient
./gradlew.bat :validation_tools:check
./components/validation_tools/feature-control_ui_showcase/task/Invoke-ControlUiShowcase.ps1 -Target 26.3-fabric -Smoke -FromTitle -KeepClient
```

## 实例与安全边界

专用实例位于 `validations/feature-control_ui_showcase/instance/<target>/client/`，
不使用日常实例或重要存档。Forge/Fabric 端口为 25601/25602，世界、token、缓存与日志不进 Git。
使用 Loader 原生开发 source-set 启动，不把这次预览当作最终 JAR 运行验收。

首次通过 Vanilla GUI 创建独立 Survival / Peaceful 世界；之后该实例必须只有一个世界。
保留 English 界面，普通背包区域至少四个空槽、carried stack 须为空。
Runner 每次从槽 9–35 中预留四个已确认空槽，不覆盖或移动原有物品；空间不足时拒绝。
物品使用现有 OPERATE、Debug scope/Arm、资源与值前置条件准备，属于 diagnostic evidence；
实际 hover/click/drag 使用 TAKEOVER + GAME_ROUTED，不把 Debug 布置冒充 gameplay。
仅清理本次最初确认为空的四个保留槽，遇到未知世界或物品会拒绝，不覆盖或删除。
世界列表无语义行时，仅在已确认单一世界的实例使用第一行坐标，其余按钮/Slot 使用 Interaction Tree。

`ui-overlay` 使用已有开发 Fixture 按钮产生 `Showcase Fixture` Toast，不新增产品路由。
真人 Esc 可随时结束接管；脚本停止且保留 reconsent latch，不自动重新接管。
中断时测试物品可能留在专用世界，须重新明确同意后收尾，不会在后续运行中静默覆盖。
未使用 Persistent Write API 或直接 NBT 写入；正常世界创建与 Save & Quit 会由 Vanilla 保存。

## 通过标准

工具单元/静态检查通过；代表端真实 Smoke 能执行点击、物品移动、拖拽、Toast 与菜单，
收尾到 READ / 主菜单、held keys/buttons 为 0、无遗留 gesture。
执行记录写入 `result/<target>.json` 的固定位置，标记当前提交、dirty 状态与开发 Runtime。
PrepareClient 的记录应有 `PREPARE_CLIENT_ONLY` 和空案例列表。
这些 PASS 只代表脚本动作完成，视觉质量由用户判断；不代替五 Target 统一验收。

相关入口：[Control 架构](../../documents/design/concept-agent_control.md)、
[执行状态](../../documents/project/plan-execution.md)。
