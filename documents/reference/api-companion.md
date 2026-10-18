# MCP Companion 接口

Companion 是独立 Node/TypeScript 工具组件，不内嵌 MCP server 到 Minecraft。
当前 V0 文档版本为 `0.0.1-ui-m1`，`publishes 24 static Tools`。
工具描述/Prompt 固定，不来自 Runtime 文本；大型 PNG/ZIP 用 minecraft:// Resources。
Wire Protocol v1 未冻结。

minecraft_execute_player_command保留一个工具，action=execute（默认）/discover/batch显式区分：
discover在三模式均按自身read+command授权可用，completionSource默认CLIENT_LOCAL，不发送服务器补全包。
显式SERVER读取真实当前玩家服务器补全，独立事务/future、真人待完成请求优先；不修改Chat草稿或焦点，不获取Lease。
每连接最多1个Agent请求，间隔250ms、TTL1500ms；原版预算仍共享，取消不能撤回已提交查询但不会发布其结果。
本地/服务器来源和完整性字段原样保留，候选不等于执行授权、全局完整列表或服务器执行成功。
batch最多32条，仅NATIVE且要求OPERATE/TAKEOVER和command授权，无输入Lease或聊天副作用；逐项提交不等于执行成功，不保证原子或服务器反馈关联。
过期资源错误仅在完整授权且资源身份一致后保留expectedRevision/actualRevision、reinspectRequired=true与retrySafe=false；
不提供替代token，不自动重试，错误Arm、身份或scope不泄露当前revision。
Session/Capabilities.control及mode状态提供自身authorization摘要：scope、Lease/Arm状态和TTL，无凭据；contextValidated=false，实际操作仍独立授权。

Native的REQUEST_CANCELLED(409)及RUNTIME_SHUTTING_DOWN(503)按结构化错误保留，包含executionOutcome=not_confirmed和retrySafe=false。
取消或退出中断不代表原生操作未发生；Agent须核对实例生命周期/效果，不能自动重新接管或重放。网络先断时保留连接错误，不猜测成功。

minecraft_interact_ui 和 minecraft_run_input_pipeline 可显式提供 expectedScreenRevision / expectedMenuRevision。
Companion 将安全整数原样转成 Native 版本头，不混入动作 body，也不自动读取新版本替换调用方条件。
条件在串行队列执行开始及首个原生输入回调复验；旧条件保留 Native 稳定错误或 Operation.errorCode，不能自动重试。
不提供条件时仍使用既有语义目标守卫，不因此申请 Lease、切模式或提升权限；不是整个 pipeline 的事务保证。
pipeline 不能借 input scope 代替 ui/read/event 等步骤权限；Runtime 会先验证整条计划再执行输入。
Companion 原样保留 SCOPE_DENIED 与类别/模式错误，不自动重新 acquire、授予权限或改写条件；
UI 条件明确 USER/DEBUG，异步结果保留 UI 类别标记，DEBUG 不再被当作 USER 查询。

minecraft_execute_player_command 原样保留 Native 的提交与证据字段；accepted=true 不是执行成功。
原生命令树分类为 COMMUNICATION/INFORMATION/STATE_MUTATION，classificationSource=native_player_command_tree。
route=CHAT 走真实 Chat/字符/Enter，要求 command/control/input、TAKEOVER 和已有 Lease，拒绝已有 Screen/草稿及原版输入超限。
route=NATIVE 要求 OPERATE 或 TAKEOVER 与独立 command scope，使用当前玩家原生命令 API，不需要输入 Lease 或 Debug Arm，不改变 Screen、聊天草稿/焦点和键鼠；保留原生可用树、长度与服务器权限。
默认 AUTO 在 OPERATE 选 NATIVE，其余模式选 CHAT；READ 不自动升级。Companion 原样传递 route，不凭字符串错误猜测路径。
NATIVE 回执为 USER/NORMAL_NETWORK、requiresControlLease=false、inputDispatched=false；feedbackAvailable=false，不提供时间关联聊天采样。全部提交均不等于实际执行确认，不能盲重放。
GAME_ROUTED_RAW 是来源；gameplayAcceptanceEligible=false、effectConfirmed/serverValidationObserved=false。
CHAT 的 evidenceContaminated 由状态修改决定；NATIVE 另标非 GAME_ROUTED 来源，不作为纯 gameplay 输入。mode=PLAYTEST/DEBUG_PRIVILEGED 仅为证据来源，
不据此生成干净 gameplay 结论，不自动改用 Debug 或授予权限。
未知/未决函数与宿主管理请求原样保留 Native 稳定错误；聊天输入的最终检查和 commandSubmissions 也不由 Companion 绕过。
原生命令方块/矿车/Jigsaw/Structure 更新及扫描同样出现在输入 Operation 的 commandSubmissions；get/wait/cancel 原样保留
configurationKind、configurationPayloadType、configuredCommandEffect 与 native_player_command_configuration 来源，不自动获取 Lease。
即使配置的是查询或清空，配置提交仍是 DEBUG_PRIVILEGED 状态修改；不是执行该命令或获得服务器确认。
Jigsaw Generate 未决时拒绝，但原生可能已先保存配置；失败的 partial 来源仍保留已提交项，不将其伪装为零修改。
Structure 参数载荷为 STRUCTURE_PARAMETERS；保存文件和加载未决模板在发送前拒绝，不把其按钮点击解释为成功。
USER Recording/Artifact 保留无原始命令文本的来源投影，不改变其他 USER/DEBUG 数据隔离。

minecraft_run_input_pipeline 接受 world.action，targetRef 从 USER block/scene/entities 读取，
action=aim/attack/use/mine/use_hold/place；mine/use_hold 使用有界 holdMs（50..10000），place 使用 face、hit.u/v 和可选 expectedDestinationBlockId。
不新增 Tool，不自动 acquire、切模式或授予权限。结果保留 USER/WORLD、
PLAYTEST 输入路径和 effectConfirmed=false，不通过字符串猜测目标/效果；后续观察确认真实结果。
USER 实体查询保留适用对象的 baby、tamed/sitting 与 leashed/leashHolderKnown/leashHeldByPlayer，
未知/不适用字段可以缺省；不公开主人身份、服务器繁殖计时或内部关系。
实体 use 打开村民菜单后复用既有语义 UI/Slot，Companion 不直接执行兑换或自动授予接管。

minecraft_get_state(kind=player/scene/state_frame) 与 minecraft_query_world 显式传递 USER/DEBUG，不推断或升级。
USER 为有界客户端世界模型：player 允许客户端坐标/生命，block/entities 提供附近缓存摘要；不保证画面可见。
scene 接受 radius(0..8)、blockLimit(1..512)、entityLimit(1..128)；get_state 可用 sceneQuery 提供同样预算。
entities 的 radius 为 0..32、limit 为 1..128；不自动加载 Chunk、读取服务器、容器内部或 NBT。
UI USER 不变；服务端与内部 Deep 仍须 DEBUG。V0 消费者改读 blocks/entities/anchor 与截断字段，不依赖旧 scene.nodes。
minecraft_deep_observe、世界 Wait/Assert 及 Recording 同步 category；内部源须独立 debug.world.read，
不要求写 Arm 或玩家 Lease。录制查询和 Artifact 必须匹配类别，DEBUG 不通过普通 Resource URI 重取。
普通录制仍使用 minecraft://recordings/{recordingId} 和其 /artifact；
诊断录制使用 minecraft://recordings/debug/{recordingId} 和其 /artifact，并由 Runtime 再次授权。
所有 UI/世界 Debug batch 的 operation get/wait 和条件查询均显式选择 DEBUG。
minecraft_debug(batch) 自动等待固定使用DEBUG，由Native按创建时的typed items检查所属领域：
纯UI要求ui/debug/debug.ui.read，不增加世界读取权限；世界要求read/debug/debug.world.read，混合批次须同时满足。
waitForCompletion=false不隐式读取结果；手动查询任一诊断批次仍须category=DEBUG，USER不能重取诊断载荷。
取消接口保留控制面授权但只返回诊断批次生命周期，不返回result/error/errorDetails。
缺少诊断读取权限仍失败，不改用 USER 重试，也不自动取消已应用的 mutation。
世界 Debug Arm 未显式提供 worldFingerprint 时，其自动查询也以 DEBUG 读取，仍需独立读取权限；
UI Arm 的 uiContextId 不触发世界指纹查询。两种 Arm 不混用。
读取不会自动切换模式、清除人工撤销或获得权限。

minecraft_get_ui 的 tree view 默认 USER，可显式请求 DEBUG；返回结构保留 domain、dataCategory。
以下描述当前生产接口；[控制模型](../design/concept-agent_control.md)的共享读取/独立修改、原生命令、本地/显式服务器补全与有界命令批次已接入并完成五端专项。完整真人协作及三部署等剩余目标仍未实现，不能据设计提前调用。
DEBUG 读取由 Runtime 独立检查 ui、debug、debug.ui.read；READ/OPERATE/TAKEOVER 均可选择独立授权的两类，不自动补权限或变更模式。
vision/render_facts 尚未提供 DEBUG 类别查询，
显式请求时拒绝 UI_CATEGORY_UNSUPPORTED，不回退。网页游戏窗口只展示明确标记的 USER 图；
旧 Runtime 未提供类别标记时拒绝 WEB_UI_CATEGORY_MISMATCH，不猜测为 USER，也不在网页隐藏 DEBUG 字段。
minecraft_interact_ui 只接受 USER 类别。minecraft_debug 的 mutate/batch 支持七种 UI 专用操作：
ui.widget.active.set、ui.widget.visible.set、ui.text.set、ui.scroll.set、ui.slider.set、ui.toast.reset、ui.toast.hide；不执行任意按钮回调。
Test Instance 编辑器的 ui.text.set 返回结构化 UI_DEBUG_UNSUPPORTED，避免原生文本 responder 提交管理 QUERY；
不自动切换 TAKEOVER、取得 Lease 或重试为玩家输入。普通文本框仍按其 typed 目录使用。
ui.text.set可用于原生多行文本框、可编辑书本页/标题和告示牌四行；仍须新鲜节点版本和UI Arm。
书本/告示牌返回native_editor_draft_may_submit_on_close，修改的是原生草稿，可能随原生关闭提交；应先恢复再退出。
原生validator继续限制行宽/页长，拒绝不会自动降级为键盘输入。五端新增编辑器的实测状态以当前状态页为准。
滑块接收 0–1 有限数字，执行原生 setter/设置回调并报告 sideEffects；可能保存客户端设置，不属于玩家输入。
调用者先显式进入 OPERATE，从 DEBUG tree 获取 uiContextId 与节点 resourceVersion，
再用 minecraft_debug_arm 申请仅 namespaces=[ui] 的客户端 session Arm。
仍须 debug/debug.write/debug.ui、版本及可选值前置条件，不获得玩家 Lease、不混用 worldFingerprint。
结果保留 CLIENT_UI、DEBUG_PRIVILEGED 与结构化版本，网页不会因此自动获得 Debug 写权限。
ui.toast.reset显式提供title/message两段有界纯文字，重置驻留SystemToast的文字/排版及生命期；
TutorialToast的显示进度使用ui.slider.set，ui.toast.hide只能请求单向隐藏，不能复活对象或冒充教学完成。
仍使用相同UI Arm/OPERATE/版本条件；Forge 1.20.1的SystemToast原生强制隐藏不支持，其他类型/版本见Native目录。
Toast内部值与内容守卫不进入USER网页；MCP按结构转发，不从文字猜测能力或绕过原生类型拒绝。
tree 的 presentation 包含当前支持的只读 HUD/Toast 数据；完整 UI/HUD 覆盖仍在推进。
生命/护甲/食物/氧气/坐骑图标保留原生绘制顺序和hudIconSampleStatus，不转成更精确的内部数值；
MCP与网页保留这些结构字段，读取不取得Lease或产生玩家输入，失效样本明确缺席。

minecraft_interact_ui 与 pipeline 的 ui.action 同步支持 scroll_into_view、type_text 和
set_value，选择器递归读取原生节点，支持 controlKey、packId、translationKey 精确匹配。
书本页与告示牌行遵循 Native 的 multiline / screen_keyboard 声明，不猜测点击区域。
网页 Shift 点击同时经标准接口按住/释放原生 Shift，使旧版 Vanilla 的按键轮询也能识别；
拒绝或取消时释放按键，释放失败则结束本页控制，不保留未知 held input。
文字输入和滑块仍是 TAKEOVER 下的真实输入，
不是 Debug 或直接值修改。范围与限制见[原版 UI 覆盖](../development/testing-vanilla_ui.md)。

## 构建与连接

```powershell
npm --prefix components/companion ci
npm --prefix components/companion test
./gradlew.bat :companion:check
```

Node/npm 依赖来源、MCP 客户端安全要求与审计命令见[Companion 依赖](dependency-companion.md)。

连接参数以[实际配置示例](../../components/companion/mcp.example.json)及
组件 src/config.ts 为准。推荐 MCP_MINECRAFT_INSTANCE_DIR 指定确切实例目录，MCP_MINECRAFT_TARGET 核对目标。
读取该实例 endpoint.json/token 后以只读 Session 握手，锁定 runtimeInstanceId；每次请求和二进制资源都校验身份。
实例重启或记录变化时拒绝，不自动换绑、重放操作或重新取得 Lease。
显式 BASE_URL 加 TOKEN/TOKEN_FILE 兼容模式仍保留，但不再默认猜测端口，也不能与 INSTANCE_DIR 混用。
实例发现不授予权限；凭据不进入报告或浏览器。具体操作见[alpha Agent 手册](../development/workflow-alpha_agent.md)。
Loopback-first；ALLOW_NON_LOOPBACK 不让 Runtime 变成已验收 LAN/TLS 服务。
生成协议路径为 components/protocol_schema；构建校验 schema 版本与实际 types。

## Surface 与安全

24 个静态工具覆盖 session/capabilities、UI、control、input pipeline/operation、
Wait/Assert、Deep Observation、capture/recording/artifact、Debug Arm/typed Debug、Persistent Read。
typed discriminator 决定操作模式，不能只按 Tool 名推断。
工具/Resource JSON 保留原生 provenance 并标记 data-plane-only。
Content-Length precheck、stream reader、running budget 和 oversize abort 防止无界收包。

READ/OPERATE/TAKEOVER 查询与转换是正式结构；
Runtime Error 到 MCP 保留 USER_MANUALLY_ENDED_CONTROL 和 reconsentRequired，
不靠错误字符串猜模式。manual latch 持续返回，明确 reacquire 才清除。
收到人工撤销后，Agent 必须先取得当前对话内明确同意，再重新 TAKEOVER；
Runtime 不读取聊天或接收假 userConsent。

`minecraft_control` 的 begin_task / heartbeat_task / end_task 管理有界逻辑任务。
任务不升模式或授予权限；持续等待先心跳，正常结束显式 end_task。
acquire 自动携带本 Companion 已明确开始的 taskId，仍须既有 Control Lease授权。
close 尝试结束自己持有的任务；进程崩溃由 TTL 收尾，不自动重放或重新接管。
control status 返回 agentTask 的结构化状态。任务结果不确定时先读取状态再决定下一步。

## 本机网页游戏窗口

网页只保留游戏窗口、接管/释放与截图管理。不会自动接管，不提供第一人称移动或相对视角控制。
控件结构可作为可选叠层；操作不依赖图像识别。

```powershell
npm --prefix components/companion run build
Remove-Item Env:MCP_MINECRAFT_BASE_URL, Env:MCP_MINECRAFT_TOKEN, Env:MCP_MINECRAFT_TOKEN_FILE -ErrorAction SilentlyContinue
$env:MCP_MINECRAFT_INSTANCE_DIR = (Resolve-Path 'validations/conformance-vanilla_ui/instance/26.3-fabric/client').Path
$env:MCP_MINECRAFT_TARGET = '26.3-fabric'
npm --prefix components/companion run web
```

默认地址为[本机游戏窗口](http://127.0.0.1:4317)。连接仍使用 Companion 环境配置，网页不能指定任意 Native 路径或远程 URL。
只允许一个页面拥有输入；Runtime token 与 Lease 不返回浏览器。

点击、双击、右键、拖拽、滚轮与键盘通过既有 Native 输入入口发送。
浏览器中键/右键编号转换为游戏的真实按钮编号；普通世界画面只接受 E/Esc 打开界面，不发送移动、攻击或视角输入。
旁观者菜单是独立的语义 UI：中键打开，单击图标只选中，双击已选中且可执行项或 Enter 明确执行，
滚轮选择原生可执行项，当前热栏键位按选中/执行语义路由。图标位置是显示边界，不发送世界坐标点击。
每次操作仍要求 TAKEOVER/Lease 和新鲜菜单上下文；过期或隐藏菜单不会退回第一人称输入。
浏览器坐标按真实 GUI 尺寸映射，保留小数，不由图片识别或 CSS 偏移补偿。
鼠标移动使用 GUI-only 的 direct motion；Agent 既有 paced motion 仍为默认值。
文字按游戏唯一焦点字段输入，focusOnly 保留光标和选择范围，不重复点击字段或全选替换。
浏览器 IME 只发送最终文字；粘贴发送用户提供的文本，不触发 Minecraft 再读取宿主剪贴板。

输入带有递增序号与当前 Screen、尺寸、缩放和编辑上下文。队列有界，空闲移动合并；
连续移动最多每秒 20 次，为正常点击、文字与读取留出原有 SecurityGate 预算，不扩大 Runtime 限额。
失效输入不重放。改变界面的按下之后，松开只清理原有 held state，不在新界面补点。
页面失焦或取消会释放输入；与在途操作竞争时 fail closed 释放自己的 Lease。
真人 Esc 的 Native manual latch 与 reconsentRequired 结构化保留，网页不会自动重新接管。
Agent 仍须遵守当前对话内重新同意政策；Runtime 不验证聊天、不接受伪造 consent 参数。

截图调用标准 Capture，默认有界轮询展示；可以暂停、更新、保存或清除。
旧截图不作为新界面的操作依据。它不是视频流，也不承诺像素与语义快照原子同步。
Operator Chrome/Virtual Pointer 继续按原有截图隔离合同排除。

后端只开放固定网页路由，验证同源 Host/Origin、随机页面凭据、16 KiB 请求预算与连接上限。
Runtime 文字按数据处理，不解释成 HTML 或指令。语义 action 接口保留给自动工具，网页不再展示复杂属性面板。
Native、Companion 与网页测试由现有 check 执行；人工体验不从这些检查推定。

## 证据与验收边界

minecraft_run_input_pipeline 的 world.action/use_hold 接受 hand=main_hand 或 off_hand（默认主手）。
副手要求主手为空并支持原版持续使用；模式、Lease 与 input scope 不变，不自动换物品或跳过原版选手。
mine 以有界同步原版破坏和流体替代判断客户端预测，不能把成功响应当作服务器效果保证。

旧 MCP conformance 的客户端协商版本只证明对应测试；
不同 SDK/protocol 版本支持不能用声明代替实际 client-gate。
操作经 Runtime，不调用 Java 内部方法或宿主输入。
Fixture/Debug/PLAYTEST authority 不由 MCP 包装重新分级。

完整旧接口说明与配置背景保留在[历史记录](../research/audit-api_companion.md)；
当前接口以生产 types、static server tools 与[Native V0](protocol-native_v0.md)为准。
