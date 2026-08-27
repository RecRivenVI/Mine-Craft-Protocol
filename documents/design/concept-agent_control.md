# Agent 控制模型

CURRENT：Round 1 已验收，Rounds 2–4 IMPLEMENTATION COMPLETE；
Unified Acceptance READY，尚未执行。Wire Protocol v1 NOT FROZEN。
[执行计划](../project/plan-execution.md)负责交付状态；[Showcase](../../validations/feature-control_ui_showcase/validation.md)
仅供反复观察，不等同人工验收。

## 状态与验收

- CURRENT：五端实现正式意图、独占输入、逻辑指针和像素 Operator UI。
- NOT YET ACCEPTED：新独占输入/光标/视觉模型的统一真人与五端验收。
- FUTURE：真实大型整合包覆盖及任何另行批准的能力扩展。

## 意图不是权限

| 模式 | 可执行 | 不会授予 |
| --- | --- | --- |
| READ | Observation、Event、Capture、Recording、Artifact、Persistent Read | 玩家输入、游戏状态 mutation |
| OPERATE | 明确授权的 typed Fixture/Debug、非玩家式 control-plane 操作 | GUI/背包/WASD/视角/攻击使用/玩家命令、输入 Lease |
| TAKEOVER | 已持 Lease 的 GAME_ROUTED 玩家路径 | Debug authority、服务端高权限、自动 gameplay PASS |

认证、scope、Control Lease、Debug Arm、Evidence Authority、Human Override 保持正交。
玩家式接口在非 TAKEOVER 返回 TAKEOVER_REQUIRED；不能静默升模式。
Debug status/read 仍兼容 READ，Debug menu mutation 只能算诊断，不能代替真实点击。

## 会话、租约与人工撤销

模式属于认证 Runtime control session，HTTP pooling/reconnect 本身不是模式转换。
controlSessionId + 单调 mode generation 校验显式转换，旧版本拒绝。
这是令牌生命周期的 principal，不是多租户或每个 MCP Client 的独立控制系统。

既有输入 Lease 是唯一接管所有权：acquire/reacquire 进入 TAKEOVER；
release、过期、绑定 WS 断开、close 结束接管并清理输入。
真人物理 Esc 立即 TAKEOVER → READ，释放 Lease、取消 active/queued 输入、
held keys/buttons=0、post-revoke dispatch=0，设置 USER_MANUALLY_ENDED_CONTROL /
reconsentRequired=true。Agent Routed Esc 只走正常 Minecraft GUI，不触发人工撤销。

latch 不禁止 READ，不自动禁止合法 OPERATE，也不被普通输入或 OPERATE 清除。
成功显式 reacquire 才清除；MCP policy 要求先在当前对话取得用户明确同意。
Runtime 不验证聊天，不接受可伪造 userConsent 参数。

## 任务生命周期

逻辑任务与 mode、权限、输入 Lease 正交，负责稳定活动 Presence，不是第二套授权系统。
单个认证 Runtime 同时只保留一个活动任务和最近终态，begin/heartbeat/end 使用独立 taskId。
TTL 最多 60 秒、总预算最多 300 秒；短连接关闭或任务内等待不改变 Presence。
显式任务期间 acquire 必须带 taskId，结束/超时取消其 Lease绑定的输入并清理 held input；
不取消独立的观察、Recording 或合法 OPERATE 工作。任务不自动切换模式或重新接管。
真人 Esc 中断活动任务，同时保持既有 READ + reconsentRequired；心跳不能复活终态。
Companion 正常 close 尝试结束自己持有的任务，异常断线由 TTL 有界收尾。
旧消费者未使用显式任务时，认证活动有 15 秒宽限，不依赖 TCP 连接持续存在。
一旦采用显式任务，后续请求不会隐式复活已结束任务，下一项工作需要显式 begin。

## 输入与光标

TAKEOVER 独占 Minecraft 玩家输入：native keyboard/char/IME/mouse move/button/scroll
不进入游戏，物理 Esc 除外。Alt+Tab、窗口关闭和系统快捷键不被阻止。
AgentInputContext 使用逐事件、一次性来源票据，处理 callback re-entry、嵌套和异步来源隔离。

宿主光标始终自由：TAKEOVER 不 grab/lock/warp，focus 或真人点击不再授予临时 capture。
能力支持字段 input.host_cursor_capture=blocked_during_takeover；
当前策略 hostCursorPolicy=never_capture_or_warp_during_takeover；
当前许可状态 hostCursorCaptureGranted=false。支持、策略、状态不是同一字段。
非 TAKEOVER 恢复 Vanilla 行为。

GUI 使用 absolute Agent Virtual Pointer，语义目标经确定性移动、mouseMoved/hover、
click/drag/scroll；gesture 串行拥有 pointer。自由移动使用二阶欠阻尼弹簧响应，
允许自然超出后回弹但不越出视口；按住按钮的移动和拖拽仍是单调轨迹。
点击前再核验 Screen identity/revision、
GUI scale、bounds 和目标存在性。取消/失效/resize/换 Screen 后不得晚到。
Gameplay 使用 logical relative/captured mode，经 Vanilla sensitivity/inversion/camera，
不写 yaw/pitch，不依赖 Host cursor；打开 Screen 后恢复 absolute pointer。
普通 GUI 换页保留虚拟光标坐标；只有游戏的有效定位或显隐请求才同步到虚拟光标。
Vanilla 逻辑捕获/释放发生真实状态转换时遵循其归中、隐藏/显示语义；已经释放时再次释放是
无效果调用，不重置坐标。显式 InputConstants 定位/模式请求可更新逻辑光标，但不作用于宿主鼠标。
Gameplay 相对运动不覆盖 GUI 位置；26.3 的 resyncMousePosition 属于宿主坐标同步，
TAKEOVER 中改为恢复 Agent 坐标，不能作为游戏定位请求。resize / GUI scale 保留归一化位置。
GUI 坐标范围为 `[0, width)` / `[0, height)`，最后一个 GUI 像素内的小数仍是有效位置；
游戏请求与视口重同步只裁剪真正越界的坐标，不减去整像素作为上限。
Screen、视口或 gameCursorRevision 变化使待执行 gesture 失效，下一次操作重新解析并校验目标。
同步入口限于 Minecraft 自身的 MouseHandler/InputConstants；第三方绕过它们直接调用原生
光标 API 不属于已验证兼容路径。所有状态修改在 Client Thread，模式和权限不由光标请求授予。

UI Tree 的 bounds 描述真实交互范围，metadata、resolve、hover/click 与 drag 端点共用同一中心计算。
Vanilla Slot 的 18×18 命中范围从 16×16 物品内容原点减去 (1,1) 开始，中心在内容原点加 (8,8)。
普通 Widget 保留原生 bounds；奇数尺寸的半像素中心保留为 double，不在输入路径取整。
GUI 与窗口坐标按实际宽高比例转换，不以 GUI scale 直接相乘代替。
圆球的几何绝对中心就是实际输入落点；绘制保留小数，不把中心取整到 GUI 像素网格。
渲染投影与输入 GUI 尺寸不一致时，只换算绘制原点，不反向改写输入坐标。
偶数尺寸的中心位于中央像素之间，不指定其中一个像素为热点；最终 GPU float 转换不做整数舍入。
无效 bounds 或同时提供 selector/coordinates
在发出输入前拒绝；Screen、bounds、GUI scale 的临点击复验继续独立生效。

GUI 操作采用可观看节奏：自由移动使用解析弹簧，response 表示自然周期而非总耗时。
短距离（不超过 196 GUI 像素）response=0.24 s、damping=0.84；其余 response
按 `0.9 × GUI 距离 / 1000` 映射并限制在 0.12–2.2 s，damping=0.9。
距离映射是本项目适配，不宣称复刻外部软件完整路径算法；圆球不使用箭头旋转或拉伸。
根据误差与速度包络确定有界移动时间，末尾保留精确 double 落点；
误差比例不超过 0.005、距离误差不超过 3.157 GUI 像素、速度包络不超过 12 GUI 像素/s。
按约 16 ms 调度采样，单次不超过 240 步；
停稳后保留 180 ms hover，连续 GUI 操作间隔至少 280 ms，
新 Screen 或尺寸/缩放变化后至少停留 700 ms 才开始下一次输入。
拖拽默认 600 ms，显式 duration/hold 和双击间隔保持原有含义。
这些等待走可取消调度器，不阻塞 Minecraft 线程；仍计入 operation deadline。
原始按键/按钮释放、Gameplay 相对输入、人工撤销和权限清理不等待观看节奏。
等待期间 Screen 或目标变化则拒绝，不在旧坐标继续点击。

## Presence 与证据画面

Presence 表示活动，不等同永久 READ mode；已认证活动、OPERATE work、Recording、
TAKEOVER 等按既有生命周期维持视觉。静默且无活动时回到无 Presence。
READ 弱、OPERATE 中、TAKEOVER 强蓝边，统一默认蓝色 #339CFF；顶部中央像素 Chrome；
Presence、强度和文案以 500 ms 的 cubic-bezier(0,0,0.58,1) 过渡，
指针显隐为 440 ms，同一曲线。中途转换从当前值续接，不按帧率累加动画速度。
蓝边每 1.5 s 往返一段（完整周期 3 s），曲线 cubic-bezier(0.42,0,0.58,1)，
不透明度系数 0.76–1.0、宽度系数 0.88–1.0。
96 显示像素的边缘区域按实际 GUI scale 转换并限制在半屏以内；中心不染色。
GUI TAKEOVER 显示 24×24 GUI 像素的蓝色发光圆球，明亮本体向外透明衰减；
偶数尺寸以球心对称，球心即真实输入坐标。真实按钮按下后，圆球在 60 ms 内缩至 70%，
松开后在 140 ms 内恢复；拖拽保持按下状态，连续点击从当前尺寸平滑续接。
70% 缩放、弹簧 response/damping、440 ms 显隐和边缘呼吸使用已核对的参数。
点击压缩/恢复的 60/140 ms 仍是本项目节奏，不把其他活动状态的 settleDuration 冒充点击时长。
圆球继续采用径向发光，不引入原生高斯模糊、箭头变形或额外渲染平面。
反馈仅改变 Operator UI 几何，不改变坐标、输入时间或操作结果。
清理仍按下的按钮时立即复位；已正常松开的恢复动画不会被 Pipeline 完成清理截断。
权限与取消立即生效，只有视觉渐变。

Operator Chrome/Pointer 位于正常 HUD、Toast、Screen、Mod UI 之上，但不进入证据 framebuffer：
Minecraft 内容 → evidence readback → Operator pass → present。
Timeline/Audit 可记 mode transition 和 pointer trajectory，不把 Operator UI 当被测画面。

窗口标题/placeholder 图标切换与真正原始标题/图标恢复幂等。
close guard 防止 GLFW 关闭后继续访问 window。重连、多次 acquire/release 不累积后缀。

## 并发与边界

Control session、Lease、gesture ownership、Human Override latch、Debug Arm 与 Evidence 是正交维度。
离开 TAKEOVER、Lease loss、manual revoke、disconnect、shutdown 先停止并 drain/cancel 输入，
不让视觉 Fade 推迟清理，不让 Debug/Fixture 自动取得 Lease或伪装 PLAYTEST。
cooperative hooks 不证明任意第三方 Mod 都无法绕过，整合包仍需独立兼容测试。
迁移前完整理由见[历史记录](../research/audit-model_agent_control.md)。
