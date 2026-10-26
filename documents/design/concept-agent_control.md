# Agent 控制模型

模式围绕是否接管真人玩家输入，不与 USER/DEBUG 类别互斥；读取、独立修改和当前玩家原生命令已接入共享准入。
既有控制基础保留，新协作合同的完整目录、三部署和统一真人验收仍未完成，不沿用旧 READY 作为新结论。
Wire Protocol v1 NOT FROZEN。
[当前状态页](../project/status-milestones.md)负责交付状态；[Showcase](../../validations/feature-control_ui_showcase/validation.md)
仅供反复观察，不等同人工验收。

## 状态与验收

- CURRENT，已有实现：五端意图模式、独占输入、逻辑指针、Operator UI 和 USER/DEBUG 读取消费者隔离。
- CURRENT，本轮增量：三模式独立授权读取两类；OPERATE/TAKEOVER 独立 DEBUG/Fixture；OPERATE 当前玩家原生命令、本地树发现与有界批次；输入撤销不结束独立工作或活动任务。正式世界inspect映射、安全stale元数据及UI原生修改能力描述已接入，验证范围见状态页。
- CURRENT，本轮推进：显式服务器补全使用独立事务与future，真人待完成请求优先且服务器预算仍共享；六类既有修改的正式版本链及进度画布二维平移，逐端实测状态见状态页。
- 已批准待实施：完整协作/权限诊断、动作回读、其余完整DEBUG目录及三部署；不宣称尚未接入的原生动作已可用。
- NOT YET ACCEPTED：新合同的自动/制品矩阵与统一真人验收，不能沿用旧实现 READY。
- FUTURE，独立研究：Agent 自有玩家体系及单客户端第二游玩界面，未获准作为本次必交付实现；整合包另按兼容计划执行。

## 两个领域、三模式、二类别

UI 与游戏世界是交互领域，不是互斥的会话状态；READ、TAKEOVER、OPERATE 是模式，
USER、DEBUG 是数据/操作类别。模式表达只读、不接管操控或接管玩家输入，不是权限等级，不采用自动权限继承。
UI 与世界 LIVE 读取消费链已接入类别合同，完整操作目录与 Peer 授权仍待闭合；两个领域各自设首个里程碑。
交付状态以[状态页](../project/status-milestones.md)为准，当前推进步骤见[执行计划](../project/plan-execution.md)；历史另行归档。
世界的 USER 感知/精度、玩家命令、完整操作目录与五端门禁见[世界控制设计](concept-world_control.md)。
仅客户端、仅服务端及双端的主体、权限交集和控制台规划见[部署设计](concept-deployment_scenarios.md)；
它是两个领域的部署维度，不新增默认权限或另一套输入 Lease；零玩家服务器已接入有限控制台及首批主世界/方块DEBUG，完整单端互通、其余服务器目录与Peer委托仍未完成。
世界 LIVE 读取消费者已接入类别；USER 为有界客户端模型而非严格视觉呈现，服务器与内部数据仍为 DEBUG。
世界语义动作已覆盖代表场景，命令边界按批准目录分类并在发送前拒绝未知效果；完整操作目录与完整里程碑未完成，协议未冻结。
源码调查保留在[研究记录](../research/investigation-world_control.md)，实际阶段见执行计划。

当前共享准入如下；完整消费者和三部署的交付状态仍以状态页为准：

| 模式 | USER/DEBUG 读取 | 共享键鼠/焦点输入 | DEBUG 修改与非接管原生操作 | 真人输入 |
| --- | --- | --- | --- | --- |
| READ | 均按各自授权 | 不允许 | 不启动新修改 | 不阻止 |
| OPERATE | 均按各自授权 | 不取得或注入共享输入 | 独立授权后允许，首批原生命令仍为 USER | 不阻止，可与用户并行 |
| TAKEOVER | 均按各自授权 | 既有 Lease 与输入权限满足后允许 | 独立授权后允许，不因模式混淆类别 | 既有独占规则，物理 Esc 除外 |

USER 表示该主体的原生交互/反馈，所有命令操作统一归 USER，不按是否需要作弊或管理员权限逐条划入 DEBUG。
DEBUG 仍表示独立内部诊断与 typed 修改。命令输入机制、原版权限、实际效果与证据来源另记，USER 不等于无副作用或干净 PLAYTEST。
不按生存/冒险/创造/旁观另造权限层，原版实际约束仍生效。类别、数据 authority 与 Evidence 分别记录。

### OPERATE 的非接管操作边界

不接管输入指不拦截、消费或注入真人共享键鼠，不抢宿主光标；不等于不修改世界、玩家或 UI 状态。
调试任务可以混用 USER 原生命令与 DEBUG typed API，逐项记录路径和效果，不能为了工具方便改写类别。

首批开放目标是 OPERATE 下以当前真实玩家身份走原生命令提交 API：不打开聊天框、不更改聊天草稿/焦点、不模拟 Enter，
不取得玩家输入 Lease，也不临时切换 TAKEOVER。沿用该 Target 的原生命令提交、签名/网络及服务器校验，
验证 command scope、真实连接/主体、可用命令和操作期限；不直接换成服务器控制台 source 或原始 packet 后门。
POST /v0/command/player 的 route=NATIVE 使用该路径；route=CHAT 保留 TAKEOVER 的真实聊天输入与 Lease。
默认 AUTO 在 OPERATE 选 NATIVE，其余模式选 CHAT；不自动切模式或申请 Lease。READ 两条路径都不能启动新命令。
NATIVE 返回 NORMAL_NETWORK、dataCategory=USER、inputDispatched=false 与 requiresControlLease=false；不采样聊天回执、不宣称执行确认或 gameplay 验收。

typed 世界/UI 修改、Fixture 与 batch 可按独立授权在 OPERATE 或 TAKEOVER 执行；Capture/Recording/Wait 等复用独立类别读取合同。
容器、物品或世界交互等其他原生动作先审查共享输入、菜单/carried stack/焦点与原生校验依赖，只有证明不抢输入且不绕过规则才可声明 OPERATE 可用。
它们不是本次一并开放所有玩家动作的承诺。直接设置库存仍为 DEBUG；原生用户动作按其真实路径分类。
打开/替换 Screen、传送或修改玩家状态可能影响真人操作结果，必须报告副作用；不能称为完全无影响或事务回滚。

WASD、相对视角、虚拟指针、GUI 点击/拖拽/滚动和向当前焦点键入仍需 TAKEOVER/Lease；
不因 Host cursor 保持自由就把游戏内输入改称非接管。准入由 Runtime 实际路由判定，不接受调用方自报“无需输入所有权”绕过。
非接管操作仍有有界队列、owner thread、取消/deadline及连接/权限失效检查，不建立第二套输入 Lease。

### 当前读取与修改边界

生产读取按实际类别/scope授权；模式往返不使仍合法的读取失效，取消和 Runtime close 仍阻止迟到发布。
内容、预算与实际资源上下文边界如下，不因 TAKEOVER 获得服务器全知、自动加载区块或额外字段：

| 类别 | 读取边界 |
| --- | --- |
| USER | UI 为主体当前呈现；世界为有界客户端模型 |
| DEBUG | 独立授权的内部图与状态，不承诺任意对象或服务器全知 |

UI USER 默认不暴露屏幕外列表、被遮挡内容、隐藏祖先的子节点或未知内部字段；
nodeId、版本、边界与语义键是定位/安全元数据，不是额外游戏情报。
隐藏信息不能先发给 Companion/网页再隐藏，隔离发生在 Runtime 服务边界。
当前分类投影是首步基础，不意味着所有 UI 自绘文字、特殊业务或调试写入都已完成。
DEBUG 读取要求 ui、debug、debug.ui.read，不要求 write scope、Arm 或输入 Lease。
DEBUG 图受原有节点/深度预算限制，报告 bounded captured UI graph，不宣称游戏全部数据。

UI DEBUG 提供原生控件 active/visible、原生文本、scrollAmount 和滑块设值五类强类型修改。
文本包括EditBox、MultiLineEditBox、可编辑书本页/标题及普通/悬挂告示牌四行；新编辑器五端验收进度单独见状态页。
书本与告示牌只修改当前原生编辑草稿，遵循原生校验；可能在原生关闭流程提交，返回native_editor_draft_may_submit_on_close。
告示牌使用各自原生行宽限制，修改非当前行后恢复选中行与选择区；不发送玩家输入、不提供任意NBT或文件写入。
恢复文字不等于恢复隐藏文字格式或回滚服务器事务；原生setter的草稿组件重建等副作用仍需单独核对。
现有实现要求 OPERATE 或 TAKEOVER、debug/debug.write/debug.ui、有效 Arm 与 expectedResourceVersion；可叠加 expectedValue。
使用 TAKEOVER 不省略任何 DEBUG 权限或条件，独立修改不取得输入 Lease。
Arm 使用 DEBUG tree 的 uiContextId 和 namespace=ui，绑定客户端 sessionEpoch，不要求世界已打开，
也不能混入 worldFingerprint 或世界 namespace。复用现有 Arm、审计、取消与 bounded batch；不新增第二套授权。
节点 token 绑定 Screen、对象、布局和语义状态；切页、布局或值变化使旧条件失效。
原生文本控件的DEBUG token另绑定完整本地字符串的有界内容指纹，不只绑定公开的2048字符前缀；指纹不进入USER数据。
DEBUG节点的debugTextGuardAvailable说明是否完成采样，失败则在修改前返回UI_CONTENT_GUARD_UNAVAILABLE。
文本expectedValue比较完整原生值，不能用被截断的前缀冒充相等；该守卫不修改GAME_ROUTED输入或指针坐标。
scrollAmount亦支持创造物品栏、织布机、切石机与交易的原生自绘卷轴；这些分支使用0～1归一化位置，不等于原生列表的像素偏移，
无滚动范围只允许0，不选择配方或交易、不获取物品。
ui.text.set亦支持本地HUD标题、副标题与动作栏，版本绑定实际HUD/世界/Component身份及完整原生内容；
仅调用本地原生消息setter，可能重置消息生命周期、颜色或格式，返回native_hud_plain_text_and_lifetime。
恢复纯文字不等于恢复计时或富文本；USER仍只含可见文字，隐藏DEBUG字段与完整内容守卫不进入USER，HUD字段不是可点击目标。
原生文本 responder 可能替换 Screen，此时返回 postMutationContextChanged，after token 为空，不冒充未执行。
这些调用不发玩家输入、不获取 Lease，证据始终为 DEBUG_PRIVILEGED；不支持任意回调、反射或原始对象写入。

驻留SystemToast另支持ui.toast.reset，显式提供title/message两段纯文字，通过原生reset重排并可能重置生命期；
TutorialToast在progressable时支持ui.slider.set的0～1显示进度，两类支持ui.toast.hide的单向隐藏请求。
Forge 1.20.1没有原生SystemToast.forceHide，针对该类型明确拒绝隐藏，教学提示仍有原生hide。
Toast版本绑定实际manager/实例/对象、完整格式化文字、显示进度及隐藏状态，而非列表下标或自然动画时钟；
退出/替换的旧目标拒绝。仅DEBUG发布内部值和守卫，USER仍是无动作的当前呈现，排队或未知类型不获得修改入口。
这些是本地诊断副作用，不表示教学自然完成，不支持复活已移除的Toast、富文本/计时回滚或任意通知源修改。

presentation 提供有限 HUD/Toast 只读内容，与可操作 children 分开。
USER 仅提供确认可见的标题、副标题、动作栏和完全显示的已支持 Toast；隐藏/排队内容不泄漏。
非交互文字的 actions 为空，不把屏幕上画出的矩形自动当作按钮。

### 模式不自动授予权限

| 模式 | 可执行 | 不会授予 |
| --- | --- | --- |
| READ | 两类独立授权读取、Event、Capture、Recording、Artifact、Persistent Read | 玩家输入、新 mutation |
| OPERATE | 两类读取、明确授权的 typed Fixture/Debug、非接管原生命令等操作 | 真人 GUI/背包/WASD/视角/攻击使用的输入所有权或 Lease |
| TAKEOVER | 两类读取、已持 Lease 的玩家输入、独立授权的 DEBUG 操作 | Debug scope/Arm、作弊或服务端权限、自动 gameplay PASS |

USER 原生命令可在 OPERATE 下提交：客户端绑定真实玩家，独立服务端绑定操作员；不互相冒充，也不接管共享输入。
认证、scope、Control Lease、Debug Arm、Evidence Authority、Human Override 保持正交。
依赖共享键鼠/焦点的输入接口在非 TAKEOVER 返回 TAKEOVER_REQUIRED；非接管原生命令按其独立准入，不借此静默升模式。
DEBUG menu mutation 只能算诊断，不能代替真实点击；USER 失败不得自动改走 DEBUG。
UI/世界的普通读取、Frame、Provider、条件、Event/resync、Recording 与回读须使用同一类别/授权合同。
模式变化本身不再使仍合法的只读能力失效；输入仍校验接管世代，读取/DEBUG 分别校验自身授权、资源与取消生命周期。
认证和实际主体授权优先，缺 scope/Arm 保持权限错误；缺输入所有权返回 TAKEOVER_REQUIRED，TAKEOVER 缺 Lease 保留 Lease 错误。

### 让授权链可用，而非隐藏

正式 inspect 应提供该资源可用的 revision/token 或明确不可写原因，使读取到写前条件闭合；
版本错误提供可安全披露的当前元数据，但不通过错误泄漏另一类别内容，不替 Agent 自动刷新并重试修改。
开工诊断和响应/错误应结构化提供适用模式、版本、权限/Arm 与实例/世界上下文，减少必须先 status 的探测往返。
当前Session/control的authorization提供scope、输入Lease和Arm的有界摘要，不返回ID或资源token；
Arm摘要的contextValidated=false不代表已校验当前世界、epoch、资源或全部前置条件，实际请求仍逐项授权。
模式转换保持显式、有条件且幂等；不凭读请求、batch、readback 或任务能力清单自动补权。
动作、条件等待和独立授权回读可组合，但逐段报告输入已提交、效果确认及回读失败，不称原子事务。
有界命令批次的限制、关联与实际命令可用性见[世界合同](concept-world_control.md#玩家命令分类)。

## 会话、租约与人工撤销

模式属于认证 Runtime control session，HTTP pooling/reconnect 本身不是模式转换。
controlSessionId + 单调 mode generation 校验显式转换，旧版本拒绝。
这是令牌生命周期的 principal，不是多租户或每个 MCP Client 的独立控制系统。

既有输入 Lease 是唯一接管所有权：acquire/reacquire 进入 TAKEOVER；
release、过期、绑定 WS 断开、close 结束接管并清理输入。
真人物理 Esc 立即撤销 TAKEOVER，释放 Lease、取消 active/queued 玩家输入，
held keys/buttons=0、post-revoke dispatch=0，设置 USER_MANUALLY_ENDED_CONTROL /
reconsentRequired=true。观察继续，独立 DEBUG、非接管原生命令、batch 与 Arm 不因这次 Esc 被一并撤销。
由 Runtime 实际执行路由区分需接管输入与独立工作，不能按“涉及玩家”或当前模式一概取消；聊天键入路径仍随接管撤销。
将用户决定持续结构化告知模型，由模型选择继续观察、取消独立工作或显式继续 OPERATE；
不能把退出接管自动解释为用户禁止一切调试，也不能自动重新接管。Agent Routed Esc 只走正常 Minecraft GUI。
输入 handback 与独立工作许可已拆分；安全清理立即生效，视觉不延迟权限变化。当前增量不宣称新的真人桌面验收。

latch 不禁止 READ，不自动禁止合法 OPERATE，也不被普通输入或 OPERATE 清除。
成功显式 reacquire 才清除；MCP policy 要求先在当前对话取得用户明确同意。
Runtime 不验证聊天，不接受可伪造 userConsent 参数。

## 任务生命周期

逻辑任务与 mode、权限、输入 Lease 正交，负责稳定活动 Presence，不是第二套授权系统。
单个认证 Runtime 同时只保留一个活动任务和最近终态，begin/heartbeat/end 使用独立 taskId。
TTL 最多 60 秒、总预算最多 300 秒；短连接关闭或任务内等待不改变 Presence。
显式任务期间 acquire 必须带 taskId，结束/超时取消其 Lease绑定的输入并清理 held input；
不取消独立的观察、Recording 或合法 OPERATE 工作。任务不自动切换模式或重新接管。
真人 Esc 只结束任务内的 TAKEOVER 工作，不中断活动任务或借共享任务终态取消独立 DEBUG/原生命令；心跳不能清除人工撤销 latch 或恢复输入 Lease。
显式结束任务、真正的 Runtime 关闭或该工作自身权限撤销另按所属生命周期处理，心跳不能复活终态。
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
支持原生手形/缩放箭头的26.x版本中，TAKEOVER将Window.selectCursor的样式参数映射为DEFAULT，
让宿主光标保持默认箭头；不改Vanilla光标选项或缓存更新流程，退出接管后原生请求正常生效。
这是宿主样式隔离，不改变Agent圆球、悬停/拖拽、游戏逻辑定位或Capture/Recording顺序。

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
文案用三模式当前可见权重连续转向，面板宽度使用同一组权重；快速切换不跳回上一个目标的完整宽度。
原生裁切仅包围当前面板的文字内容区，收缩时旧长文案不会越过边界；绘制结束或异常均恢复裁切，不影响圆球和蓝边。
蓝边每 1.5 s 往返一段（完整周期 3 s），曲线 cubic-bezier(0.42,0,0.58,1)，
不透明度系数 0.76–1.0、宽度系数 0.88–1.0。
96 显示像素的边缘区域按实际 GUI scale 转换并限制在半屏以内；中心不染色。
四角使用相邻两边衰减的平滑并集，保持直边原有强度并在连接处连续；每个角像素只提交一次，避免叠色或方形等距线的斜接缝。
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

窗口标题和图标统一在 Window 向原生窗口后端提交的最终调用处加工，不取消游戏的更新。
最新游戏标题原样保留，按 Presence 追加既有“智能体读取中 / 智能体操控中 / 由智能体接管”后缀；
没有 Presence 或关闭时恢复原文。重复模式切换只从原文重新计算，不在上次输出上追加。
图标仅在 TAKEOVER 时为游戏最新原图叠加右下角蓝色圆球，不替换整个图标。
角标复用当前 Pointer 的 24×24 静态像素图案，以最近邻缩放到图标短边的一半，贴齐右下角；
正方形窗口图标中只占右下四分之一块，不向其他区域扩散，也不重新生成平滑大圆球。
原图透明度正确合成，各分辨率独立处理；不改变游戏内 Pointer 尺寸。
退出 TAKEOVER 恢复最近一次游戏提供的实际图标；各尺寸分别保留和合成，不用系统默认图标代替恢复。
GLFW 图标数组和 SDL 的多尺寸 surface 均按原生生命周期释放；异常降级保留游戏输出并报告状态。
close guard 防止原生窗口关闭后继续发布。第三方绕过 Window 直接操作 OS 的行为不在该拦截边界内。

## 并发与边界

Control session、Lease、gesture ownership、Human Override latch、Debug Arm 与 Evidence 是正交维度。
离开 TAKEOVER、Lease loss、manual revoke、disconnect、shutdown 先停止并 drain/cancel 输入，
不让视觉 Fade 推迟清理，不让 Debug/Fixture 自动取得 Lease或伪装 PLAYTEST。
OPERATE 与真人行为允许并发，获准 DEBUG 可覆盖用户操作；同一 owner thread 串行提交并重查显式资源/值条件，
不能只因真人正在操作而全部拒绝，也不能以权限较大为由忽略 stale 条件、越过服务器授权或强行重试。
离开接管不等于取消独立 DEBUG；连接丢失、授权到期、资源卸载和 Runtime shutdown 按各自真实生命周期失效。
测试记录独立成功、部分应用与冲突，不将取消谎称回滚，也不把 USER Act + DEBUG 断言伪称纯 USER 验收。
cooperative hooks 不证明任意第三方 Mod 都无法绕过，整合包仍需独立兼容测试。
迁移前完整理由见[历史记录](../research/audit-model_agent_control.md)。

## 独立 Agent 玩家体系

当前客户端体系控制真人当前玩家；Fake Player/Agent 自有玩家若可行，应属于另一体系，拥有自己的 READ/OPERATE/TAKEOVER、
actor 身份、任务和所有权，不把它塞进真人 OPERATE，也不让一次全局 set_mode 同时切换两者。
其 USER 感知、库存/菜单、命令权限、断线与清理必须绑定自身 actor，不能借真人 OP、Screen、HUD 或截图冒充自有数据。
单客户端第二独立游玩画面、部分 HUD、远方/跨维度数据与渲染隔离另行研究；第二相机不等于第二独立玩家。
目前仅授权可行性调查，不是本次双里程碑的必交付实现，不承诺第三方 Fake Player 等价或自动启动自主游玩。
