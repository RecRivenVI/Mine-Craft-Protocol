# Native V0 协议

当前 OpenAPI 文档版本 `0.0.1-ui-m1`；`All 77 formal HTTP operations`。
V0 是未冻结的产品原生合同，Wire Protocol v1 NOT FROZEN。
机器权威为[OpenAPI](../../components/protocol_schema/src/main/openapi/minecraft-control-v0.json)；
字段、typed discriminator、错误和 schema 从此生成，本文不维护第二份协议。

## 接入与表面

基础世界分类只覆盖 GET /v0/player、/v0/server/player、/v0/world/block、
/v0/server/world/block、/v0/world/entities、/v0/server/world/entities。
category 默认 USER：客户端 player 仅返回已有可见热栏/副手/效果的 presentation，
没有原始 XYZ/生命/UUID；authority=CLIENT_UI，不冒充服务器状态。无可见支持项时 available=false，
含义不是玩家不存在。场景和服务器 USER 返回 WORLD_USER_PERCEPTION_UNAVAILABLE，内部读取未派发。
category=DEBUG 要求 read/debug/debug.world.read，且模式为 READ/OPERATE，不要求 write/Arm/Lease；
TAKEOVER 下 DEBUG、OPERATE 下 USER 返回 WORLD_CATEGORY_MODE_MISMATCH。
异步结果在发布前复验权限与 mode generation，已切换上下文返回 WORLD_READ_CONTEXT_CHANGED。
DEBUG 保留原始精度和数据来源，并标 domain=WORLD/dataCategory=DEBUG，不标为用户感知。
默认配置不授予新的 Debug scope；旧精确读取消费者必须明确选择 DEBUG 和正确权限/模式。
Companion 的 minecraft_get_state(kind=player) 与 minecraft_query_world 提供同名选择器，不自动切模式。
这不是所有世界接口的类别升级：Deep、Provider/State Frame、Wait/Assert、Event/resync、
Recording/Artifact 和命令效果还未全链分类。worldContract 列出实际六条路径与 pendingClassification，
consumerIsolationComplete/milestoneComplete 均为 false；不能从它推定公平玩法信息隔离或完整里程碑 PASS。

Session/Input/Control 状态中的 windowIconState 使用 original_with_agent_pointer 表示
TAKEOVER 的“游戏原图加右下角圆球”，original_minecraft 表示未加角标的游戏图标。
V0 消费者若曾识别 agent_placeholder，应切换到新取值，不能把角标当成整图替换。
windowBaseTitle 是游戏最新原文，windowTitle 是最近成功提交到原生后端的标题，
windowDecorationError 为空表示没有记录到后端装饰错误；这些字段不是 OS 外观截图证明。
originalWindowIconCount 包含各个原生尺寸；模式切换不改变图标来源。

UI 领域采用 USER/DEBUG 两类，独立于 READ/TAKEOVER/OPERATE 模式。
GET /v0/ui/tree 的 category 默认 USER，可显式指定 DEBUG；POST /v0/ui/resolve 接受同名类别字段。
USER 图只发布确认可见且未遮挡的节点与受限字段，树/解析都不泄露屏幕外节点。
DEBUG 图要求 ui、debug、debug.ui.read，不要求 write/Arm/Lease；debugFacts 明确已捕获节点数量与有界范围。
READ 可选两类读取，TAKEOVER 仅 USER，OPERATE 仅 DEBUG；先认证并解析合法类别，再检查对应 scope 和模式，
发布异步结果前再次校验当前权限和模式，拒绝 UI_CATEGORY_MODE_MISMATCH，不自动切换。
tree/resolve 返回 domain=UI、dataCategory；capabilities.uiContract 声明类别合同及里程碑未完成状态。
ui/action 请求与 pipeline 各输入 step 的 category 仅接受 USER；DEBUG 不能作为替代玩家输入。
原有节点身份与版本保留用于定位，但未知内部字段与隐藏数据不进入 USER 读取或 resolvedNode 结果。
未指定类别的消费者现在获得 USER 投影；需要内部图的消费者必须明确选择 DEBUG 并获得独立权限。
UI DEBUG mutation 复用 debug/arm、debug/mutations 和 bounded batch，当前仅支持
ui.widget.active.set、ui.widget.visible.set、ui.text.set、ui.scroll.set、ui.slider.set。
滑块值为有限的 0–1 归一化数字；调用原生 setter，同步设置与文案，可能触发原生设置保存。
结果 sideEffects=native_slider_setting_callback；不承诺取消已应用的设置，调用者用新版本条件恢复。
请求要求 nodeId、expectedResourceVersion、typed value，可附 expectedValue；
debug/debug.write/debug.ui、OPERATE、deadline 与有效 Arm 在 owner-thread mutation 前校验。
UI Arm 明确传 uiContextId 与 namespaces=[ui]，不得混用 worldFingerprint，不向服务端 Peer 路由。
DEBUG tree 的 uiContextId/resourceVersion 为安全元数据，USER 不发布这些 Debug 授权依据。
结果为 CLIENT_UI、DEBUG_PRIVILEGED、inputDispatchCount=0；不直接执行按钮回调或替代玩家。
若原生文本 responder 替换界面，postMutationContextChanged=true，after/afterResourceVersion 为 null。
presentation 是有界只读 HUD/Toast 内容；USER 过滤隐藏项与 queuedToastCount，DEBUG 保留受限内部状态。
标题/副标题/动作栏、物品热栏/副手、Boss 栏、侧边计分板、状态效果图标和 System/Tutorial/Advancement Toast 已接入。
presentation.children 上限为 215；热栏最多 10 项、Boss 栏最多 32 项且遵循原版视口上限，
计分板最多 15 行加标题，标题文字最多 3 项，Toast 最多 8 项，效果图标最多 64 项。
玩家列表最多 80 个原生显示名加页眉/页脚，controlKey 前缀为 hud.playerlist；它们没有输入 actions。
玩家列表沿用 Vanilla 的 listed-player 筛选、排序与 getNameForDisplay，不另返回 profile、UUID、
精确延迟、皮肤或计分数值/心形。USER 还要求列表当前实际展开；不把按住 Tab 等同于原版一定绘制列表。
页眉/页脚与多人排序/80 人边界已有读取适配，当前单人自动矩阵未独立构造这些服务端数据。
状态图标 role=status_icon，effect 为图标所代表的效果身份；不发布界面未显示的等级、时长或隐藏效果。
按原版倒序与增益/减益两行定位；图标从原生绘制参数读取 opacity，iconSource 为
last_native_effect_icon_draw，renderPhase 为 drawn、transparent 或 unobserved。
USER 只返回当前有效且非透明的图标；不根据剩余时长预测闪烁，不返回等级/时长。
样本绑定玩家、Screen、GUI 尺寸，最多 64 项、250 ms；缺少样本时保守不发布 USER 图标。
此项证明原生图标绘制参数，不保证第三方替换绘制或最终 framebuffer 的所有遮挡情况。
热栏只含图标身份、数量与选中状态，不读取物品隐藏属性或未显示的名称；Boss value 是显示中的插值进度。
计分板遵循队伍专用侧栏、隐藏行与原版裁剪，label 使用实际数字格式，不额外暴露被 blank 格式隐藏的分数。
有 Screen、Overlay 或 F1 隐藏 HUD 时，USER 保守不返回 HUD；DEBUG 仍可读有界捕获状态。
其余 HUD、第三方渲染覆盖与所有界面组合仍未完成。

无 Screen 的旁观者菜单使用 `uiInputContext=spectator/spectator_menu` 与 `spectatorContextKey`。
关闭时的 `spectator.open` 只提供 `open`，走原生中键；打开后 `spectator.slot.<0..8>`
提供 `select/click` 选中，只有已选中且原生允许执行的项才提供 `activate`。
重复 `click/select` 为零输入，不以第二次点击隐式传送。菜单、分类、页、选中项和当前键位变化拒绝旧目标。
菜单图标 bounds 标记 `display_only`，不是鼠标命中区域，resolve 不生成 interactionPoint；
实际操作经当前热栏键位的 GAME_ROUTED 输入，不调用 SpectatorMenu.selectSlot/selectItem。
USER 仅发布当前选中项名称和原生提示，未选中项名称只在 DEBUG 的 debugLabel；F1/Screen/Overlay 隐藏菜单。
鼠标、扫描码及 Loader 修饰键热栏绑定暂不支持，明确无动作，不猜默认数字键。
这些操作仍要求 TAKEOVER、input scope 和 Lease；旁观者菜单的专用 DEBUG mutation 尚不支持。

UI Tree 递归展开原生容器和列表行；使用 nodeId、worldId、resourceId、entryIndex、
controlKey、packId 或原生 translationKey 选择，均为精确语义匹配。
隐藏行以 scroll_into_view 经原生滚轮滚入可见区，actions 是可执行范围；容器边界
不代表任意子控件的点击许可。type_text 通过原生键盘/字符回调输入 Unicode，
replace 使用全选/删除，最多 2048 UTF-16 单元；只有 multiline 字段允许 LF，转换为原生 Enter。
其他控制字符被拒绝，不模拟 IME 组合输入。旧书本/告示牌的 screen_keyboard 路由没有
虚构 bounds 或 interactionPoint，按键选行/原生焦点验证后输入。
set_value 只接受语义滑块与 0–1 原生归一化比例，游戏可能离散取整。
这些动作继续要求 TAKEOVER、input scope 和 Control Lease，不直接修改控件或业务值。

树最多 2048 节点、16 层并报告 treeTruncated；列表返回裁剪命中边界和滚动信息。
创造标签页与配方书采用具名原生适配，不使用任意 Reflection；未覆盖的自绘区域
不能冒充支持，详见[UI 覆盖清单](../development/testing-vanilla_ui.md)。

Runtime 使用认证 loopback HTTP / WebSocket，独立 Companion 通过这些入口驱动。
requestId、deadline、Control Lease、Debug Arm、world/resource/value preconditions、scope
各自承担独立职责。Session/capabilities 表达支持、当前策略与状态，不将三者混为一个字段。

`GET /v0/control/task` 查询逻辑任务，`POST` 显式 begin/heartbeat/end；需要 read scope，
不授予模式、Lease 或 Debug 权限。TTL 为 1–60 秒，总预算为 1–300 秒，心跳不能延长总预算。
显式任务期间 acquire 带 taskId，结束/超时清理其绑定的既有输入 Lease并等待清理完成；
真人 Esc 中断任务，不清除人工撤销提示。短 HTTP 连接结束不是任务结束。
任务状态也包含在 control/mode 的 agentTask 中。结果不确定时先查状态，不盲目重放输入。

正式表面包含 session/auth/capabilities、UI Tree/action、control mode/lease、
keyboard/mouse/pipeline/operation、Wait/Assert、world/client/server/Deep Observation、
Provider、Fixture/typed Debug/batch、Event、capture/recording/artifact、Persistent Read 与 Peer。
路径与请求字段直接看 OpenAPI，不提供不存在的 storage.write。

## 意图、身份与来源

UI Tree 的 `slot` 是当前 Screen 菜单槽位列表的唯一零基序号，创造物品目录和玩家背包页均适用。
`nativeSlotIndex` 只记录 Minecraft 内部编号，包装槽位可重复，不用于选择器。
布局保留槽位列表位置；被创造背包移到屏幕外的隐藏格子标记 `visible=false`，默认选择器不会选中。
`nodeId` 是不透明标识，绑定 Screen、菜单与槽位对象；切换布局后重新读取，不拼接或解析。
`menuIdentity` 和 `menuRevision` 跟踪当前界面菜单，替换或内容变化会使待执行目标失效。
hover/click/drag 与 Wait/Assert 使用同一语义序号，不需要坐标绕过；
它不是 Server-authoritative Debug `menu.slot.set` 或 player inventory 的编号，两者不得混用。

[操作分类输入](../../validations/conformance-agent_control/task/control-mode-surface.json)
对应 READ_COMPATIBLE / OPERATE_REQUIRED / TAKEOVER_REQUIRED / MODE_INDEPENDENT。
认证与安全错误优先；模式不自动授予 scope、Lease 或 Arm，也不自动升级。
TAKEOVER 依赖唯一 Control Lease，人工 Esc 回 READ，latch 和 reconsentRequired 结构化持续保留。

ResourceRevisionRef 与 sessionEpoch、resourceType/resourceKey、revision/revisionSource 绑定；
生命周期与 provider revision 完整性不能由全局 worldRevision 替代。
PERSISTED、last_saved_state 与 LIVE 的 client/server 权威独立，
unloaded 不强制加载，Windows locked/busy 不报告 corruption。

## 数据与证据安全

Runtime 文本与图片一律 data-plane；static MCP tools/prompts 不读取它们生成指令。
PLAYTEST / FIXTURE / DEBUG_PRIVILEGED 保留来源，Debug Arrange 不提升为 gameplay acceptance。
Capture/Recording 默认排除 Operator Chrome/Pointer，但 Timeline/Audit 可记录控制轨迹。
input/state 的 agentPointer 提供 gameCaptured、gameCursorVisible、gameCursorRevision；
ui/tree 同步 gameCursorRevision，供 gesture 临执行校验游戏光标请求。它不是 Lease 或资源写权限。
control.pointer.game_request 仅为 GAME 来源的 Operator metadata，不进入被测 framebuffer。
事件流、响应体、队列与 Artifact 读取均按实际预算和失败语义执行。

## 验证与生成

UI 树扩展原生交互区域：滚动条、文字事件、建议与进度，未知绘制区域不因矩形存在而获得操作支持。
服务器保存行使用 controlKey=server.entry；悬停后按原生可用性提供 server.join、server.move_up、
server.move_down 子节点。USER 不发布未显示的图标，也不将服务器旁白中的延迟、玩家名单或地址当作行名称。
列表排序进入子节点身份和操作前置校验，排序后的旧目标必须重新解析；所有操作仍走真实鼠标输入。
UiNode 的 x/y/width/height 为 double，保留字体和缩放布局的小数；Java 生成模型相应使用 Double，
依赖整数 setter 的源码消费者需调整。JSON 字段名不变，V0/Wire v1 仍未冻结。
容器 UiTreeResponse.carriedStack 直接读取当前客户端 Screen 菜单，区分鼠标所持物品与已落槽物品；
它不代表服务器或磁盘权威，普通非容器界面不虚构此字段。
focusOnly 文字输入不重新点击或全选；mouse.move 的 direct 仅用于 GUI，默认仍为 paced。
ui.drag 局部比例 fromOffset/toOffset 默认为中心，只从新解析的语义 bounds 推导输入位置。
比例为 1 时取原生右/下排他边界内的可表示坐标，不能落进相邻控件。
occluded 与 clickEventType 是交互/效果 metadata，不是权限或可执行指令。

```powershell
./gradlew.bat :protocol_schema:test
pwsh -File components/validation_tools/conformance-native_protocol/task/Invoke-NativeSurfaceConformance.ps1
```

生产接口和消费者变化需同步 schema/types/static classification，而不是修改旧 commit-bound evidence。
完整旧描述及长期协议设想保留在[历史记录](../research/audit-protocol_native_v0.md)，
它们不是未实现能力已经交付的证据。
