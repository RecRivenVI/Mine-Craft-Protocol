# MCP Companion 接口

Companion 是独立 Node/TypeScript 工具组件，不内嵌 MCP server 到 Minecraft。
当前 V0 文档版本为 `0.0.1-ui-m1`，`publishes 24 static Tools`。
工具描述/Prompt 固定，不来自 Runtime 文本；大型 PNG/ZIP 用 minecraft:// Resources。
Wire Protocol v1 未冻结。

minecraft_get_ui 的 tree view 默认 USER，可显式请求 DEBUG；返回结构保留 domain、dataCategory。
DEBUG 读取由 Runtime 独立检查 ui、debug、debug.ui.read。READ 可选两类，TAKEOVER 仅 USER，
OPERATE 仅 DEBUG；不自动补权限或变更模式。vision/render_facts 尚未提供 DEBUG 类别查询，
显式请求时拒绝 UI_CATEGORY_UNSUPPORTED，不回退。网页游戏窗口只展示明确标记的 USER 图；
旧 Runtime 未提供类别标记时拒绝 WEB_UI_CATEGORY_MISMATCH，不猜测为 USER，也不在网页隐藏 DEBUG 字段。
minecraft_interact_ui 只接受 USER 类别。minecraft_debug 的 mutate/batch 支持五种 UI 专用操作：
ui.widget.active.set、ui.widget.visible.set、ui.text.set、ui.scroll.set、ui.slider.set；不执行任意按钮回调。
滑块接收 0–1 有限数字，执行原生 setter/设置回调并报告 sideEffects；可能保存客户端设置，不属于玩家输入。
调用者先显式进入 OPERATE，从 DEBUG tree 获取 uiContextId 与节点 resourceVersion，
再用 minecraft_debug_arm 申请仅 namespaces=[ui] 的客户端 session Arm。
仍须 debug/debug.write/debug.ui、版本及可选值前置条件，不获得玩家 Lease、不混用 worldFingerprint。
结果保留 CLIENT_UI、DEBUG_PRIVILEGED 与结构化版本，网页不会因此自动获得 Debug 写权限。
tree 的 presentation 包含当前支持的只读 HUD/Toast 数据；完整 UI/HUD 覆盖仍在推进。

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
组件 src/config.ts 为准，token 文件和 URL 必须由用户本机提供，不提交凭据。
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
$env:MCP_MINECRAFT_BASE_URL = 'http://127.0.0.1:28591'
$env:MCP_MINECRAFT_TOKEN_FILE = (Resolve-Path 'validations/conformance-vanilla_ui/instance/26.3-fabric/client/minecraft-protocol/token').Path
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

旧 MCP conformance 的客户端协商版本只证明对应测试；
不同 SDK/protocol 版本支持不能用声明代替实际 client-gate。
操作经 Runtime，不调用 Java 内部方法或宿主输入。
Fixture/Debug/PLAYTEST authority 不由 MCP 包装重新分级。

完整旧接口说明与配置背景保留在[历史记录](../research/audit-api_companion.md)；
当前接口以生产 types、static server tools 与[Native V0](protocol-native_v0.md)为准。
