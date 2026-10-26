# 原版 UI 语义覆盖

当前实现覆盖原生 Widget 图、列表行、菜单槽位、原生滚动条、富文本区域、聊天建议与进度画布。
这不是“全部原版 UI 已验收”的声明；普通控件能被提取，不等于所有自绘交互区域已暴露。
操作只经项目 Native/MCP 接口，不依赖截图识别、手写坐标、Computer Use 或直接调用按钮业务函数。

## 支持范围

首个里程碑为 UI 三模式二类别完整交付，尚未完成。
已批准目标包含完整[三部署](../design/concept-deployment_scenarios.md)：客户端图形UI及仅服务端原生控制台适用表面。
服务器控制台提供有界输出/补全和授权原生命令操作，不伪造Screen/HUD/截图；零玩家服务器没有真人玩家输入端时不提供假TAKEOVER。
客户端 USER 真实语义输入仍需TAKEOVER/Lease，DEBUG读取不再因接管互斥，typed DEBUG可在OPERATE/TAKEOVER独立授权。
共享读取/修改准入已接入新合同；完整原版目录、协作与单端/零玩家矩阵仍未覆盖，不把专项运行当作完整里程碑。
下方历史客户端矩阵不证明新增部署目标，当前mod_client_side=true、mod_server_side=true，双端必装要求不变；当前进度见[状态页](../project/status-milestones.md)。
当前树读取默认 USER；隐藏/遮挡节点及内部字段不进入用户视图，技术身份/版本/边界保留用于安全定位。
显式 category=DEBUG 要求 ui、debug、debug.ui.read，返回受预算限制的已捕获图，不自动取得输入 Lease 或 Arm。
resolve 使用相同类别，USER 无法解析并读出隐藏内容；真实输入仅接受 USER，不静默降级为 Debug。
Recording、Event resync、Wait/Assert 等既有 uiTree 消费路径默认使用 USER，不另开隐藏数据旁路。
UI树/resolve及UI条件在异步发布时复验独立类别权限与会话存活；模式往返本身不撤销合法读取。
UI调试事件与录制的修改详情独立检查debug.ui.read；世界DEBUG读权限不能替代它。
录制UI上下文维持USER类别，缺ui时不调用UI owner并标记不可用；诊断详情省略不清除证据污染。
共享生产HTTP回归覆盖缺scope、世界事件仍可读、录制详情/污染、无UI读取、模式往返仍授权与取消/关闭阻止迟到发布；不代替五端游戏与最终JAR验证。
Screen 静态目录已生成；UI DEBUG 已提供五类通用 setter，以及下文列出的建议/旁观选择、HUD 生命周期与 Toast 本地 typed 操作。
须 OPERATE 或 TAKEOVER、独立 debug/debug.write/debug.ui、客户端 session Arm 和节点版本/可选值前置条件；不使用输入 Lease。
TAKEOVER不省略任何scope/Arm/条件；真人Esc不连带取消独立DEBUG，USER失败不得自动改走setter。
非 Screen 的 presentation 为USER提供只读HUD标题/副标题/动作栏与System/Tutorial/Advancement/Recipe Toast；
独立授权的DEBUG本地HUD文字修改见下方typed目录，不把USER呈现改成可操作节点。
USER 不含隐藏/排队内容；普通HUD遵守各原生F1/Screen/Overlay规则，Inventory效果、焦点聊天及保存提示等晚期层仅在对应原生draw真实发生时返回。Toast文字来自原生渲染回调，
多行进度分别发布当前标题或名称，不提前透露下一阶段。Toast 实际绘制样本绑定实例、世界、摄像机、Screen、Overlay 和 GUI 尺寸，250 ms 后失效。
renderPhase 标明进入/完全显示/退出；部分滑出时只返回裁切边界，不发布未完整呈现的文字，退出请求尚未移出完整视口时仍保留原生文字阶段。
textPhase 保留原生阶段，完整待显示文字仅在 DEBUG 的 debugLabel 中，均受 2048 字符预算限制；USER 投影继续剥离 debugLabel。
这不是 framebuffer 像素识别，也不证明第三方改绘后的最终可见性。新增热栏/副手图标、选中状态、Boss 栏显示进度和侧边计分板；
USER 不包含隐藏 HUD、未显示物品属性或 blank 数字格式隐藏的原始分数。
状态效果图标按原版两行与顺序读取，不包含等级/时长；原生绘制参数提供 opacity 和 renderPhase。
透明图标、隐藏 HUD 和无有效样本不进入 USER；样本绑定玩家、Screen 和 GUI 尺寸，250 ms 后失效。
五端分别观察实际绘制透明度，1.x 保留 float、26.x 使用绘制颜色中的量化 alpha；不按时长预测闪烁。
第三方替换图标绘制及最终framebuffer的所有遮挡情况仍不保证。玩家列表USER仅在原生列表实际展开时返回已绘制的显示名、页眉/页脚、延迟图标及格式化分数/心形；
不含账号内部字段、精确延迟或未绘制原始分数。局部裁切文字不补出隐藏全文。
本地原生packet夹具验证排序、80行边界和格式，不代替独立服务端或真实多人网络证明。其余HUD和完整业务组合的进展由状态页记录。
旧固定结果不证明新增合同。

新增物品装饰与上下文条按实际原生绘制采样。旧Forge使用真实atlas/UV，新版使用实际sprite资源，不互相伪造身份；
USER只返回显示的像素范围、文字和图标，不附隐藏耐久、冷却、经验或跳跃统计。
晚期GUI文字的独立采样覆盖Inventory效果文字、实际tooltip、聊天与F3等已接入入口；源码接入不等同于各分支已完成Live。
语义hover允许自身引起的原生高亮变化，仍固定身份/几何；点击不放宽选中前置条件。
禁用目标拒绝后的安全清理也占用输入序号，验证须区分release_all与实际鼠标事件，不仅比较总计数。

诊断村民夹具通过有界原版 summon/data 命令逐条构建完整交易列表，每条含斜杠均在原生聊天限制内；
不恢复直接发包，不把 accepted 当作数据已同步，菜单仍须实际出现并执行语义滚动与选择断言。
聊天建议与 suggest-command 链接的测试草稿使用有界原生 Esc 收尾；第一次可能只关闭建议弹层。
调用方确认实际 Screen 关闭、held input=0 且 TAKEOVER 未撤销后才开始下一条命令，不由 helper 擅自丢弃草稿。

UI 与世界现已恢复联合推进。世界验证中的工作台 3×3 合成、铁砧重命名和熔炉烧炼使用真实 world.action use 打开菜单，
再通过语义 Slot/Text Field 操作、取出成品和重开确认；熔炉轮询原版实际产物，不以固定睡眠或输出预览代替效果。
测试工具和证据入口见
[世界与 UI 联合验证](../../validations/conformance-world_read/validation.md)。
该增量不替代本页完整固定副本矩阵，也不证明全部工作站、所有 UI 或第三方界面已验收。
语义手势逐次校验实际目标、菜单/Screen 身份、几何、GUI 尺寸和鼠标所持物品；无关槽位更新不再自动作废稳定目标。
目标物品/数量/组件变化、菜单替换或所持物品被外部改变仍拒绝。
拖动只在原生输入回调完成后、客户端线程尚未让出时推进自身物品守卫；外部变化不能被当成自身分配物品放过。
槽位/所持物品的 contentRevision 是不公开内容的上下文令牌；原生编码不可用或超预算时物品手势明确失败，
不向 USER 返回 NBT、组件原文或跨槽位指纹。单项 2 MiB、每快照 16 MiB、最多 2048 个槽位。
显式 X-MCP-Expected-Menu-Revision 仍要求全局版本匹配，原始坐标手势也保留全局菜单守卫；
键盘等待不绑定无关槽位内容，但继续校验 Screen/菜单身份和几何，显式全局版本仍严格；不会因后台输送而无法按 Esc 关闭菜单。
调用者不能把全局版本 pin 与容忍后台变化混用。旧联合场景的连续稳定等待不作为持续刷新菜单的通过依据。
新增原生漏斗持续输送场景并发采样槽位数量与指针位置，证明后台更新确实和语义移动重叠，
再通过稳定槽位实际转移物品，并验证变化目标及显式全局版本拒绝；不用固定延时或重放失效动作隐藏竞争。
Shift 点击同时路由 Shift 按下/松开；鼠标事件的 modifiers 字段本身不代替真实按键持有。

特殊菜单专项覆盖讲台原生翻页/取书、马鞍装备/取回、驴胸箱货物往返；准备在自有副本完成，实际操作仍走USER语义输入。
适用版本的自动合成器前九格提供可见 `slotDisabled`，禁用不等于不可点击；点击仍由原版启用/禁用，推进菜单版本。
逐事件守卫拒绝外部改变的目标；拖动仅在同一原生回调后推进自身产生的槽位状态，不把外部变更算作自身操作。
Forge 1.20.1 原生没有自动合成器；其余端是否通过本轮运行以固定结果及[当前状态](../project/status-milestones.md)为准，不从参考端推定。
固定副本的原版移动教学通过真实跳跃/相对转向完成；等待接口确认TutorialToast自然隐藏，不直接关闭教学或修改基准选项。

| UI 家族 | 当前支持 | 仍需专用适配或独立验收 |
| --- | --- | --- |
| 标题、暂停、确认、错误、断线、死亡 | 原生按钮与文字 | 不可交互加载页不虚构按钮 |
| 世界选择 | 存档名称/身份、行、选中状态、搜索、滚动 | 世界转换/修复流程未执行 |
| 创建世界 | 文本框、Game/World/More 标签、按钮、种子、选项、超平坦预设选择和草稿取消 | 外部文件导入与全部生成配置组合未验收；游戏规则/数据包导航不等于全部选项已测 |
| 设置、语言、键位、辅助功能、声音、视频 | 嵌套容器、裁剪行、按钮、滑块、文本框 | 每个设置组合未逐项运行 |
| 多人服务器列表、直连、添加服务器 | 原生列表、文本与按钮，保存行的悬停连接/排序图标，排序目标失效保护 | 外部服务器连接与身份认证未验收；不由本机列表管理推定联机成功 |
| 资源包/数据包列表 | packId、选择/移除、上移/下移原生图标；不可用操作明确禁用 | 数据包组合与不兼容确认流程未逐项验收 |
| 普通背包、箱子、炉子、铁砧等菜单 | 唯一视图槽位、鼠标所持物品、真实 click/drag/scroll；原生字段/按钮 | 客户端菜单不冒充服务器或磁盘权威 |
| 创造物品栏 | 唯一槽位、当前 Loader 页内标签、搜索文本、语义卷轴/滑块 | 快捷栏存取组合未验收 |
| 配方书 | 标签、搜索、过滤、可见配方/翻页按钮、原生展开层按钮与遮挡 | 全部配方组合未运行；展开选项的领域描述仍有限 |
| 交易、附魔、织布机、切石机、信标 | 语义交易、附魔选项、图案、配方与信标效果/确认；原生槽位；交易/织布机/切石机卷轴 | 全部交易/配方组合未逐项验收 |
| 书本 | 多行编辑、签名标题、翻页、内容与原生富文本链接 | 外部 URL 和命令效果不自动执行或绕过 Vanilla 确认 |
| 普通/悬挂告示牌 | 四行独立语义编辑，遵循原生键盘选行 | 正反面与全部木材组合未逐项验收 |
| 聊天建议、进度树 | 建议项/滚动、聊天文字链接、进度标签/节点悬停/画布拖拽 | 节点不冒充按钮；服务端数据未到达时不虚构目标 |
| HUD / Toast | presentation 中的有界实际绘制文字、图标与阶段；独立授权的本地 typed 操作见下表，USER 节点仍只读 | 适用原生家族的冻结候选逐端复验见状态页；本地夹具不代替真实多人网络或人工视觉验收 |
| 旁观者菜单 | 原生快捷键打开、语义选中/明确执行，以及独立 DEBUG 纯高亮选择；图标边界不冒充鼠标命中区域 | DEBUG 高亮不激活条目或传送；多人分类分页/实际传送与全部绑定组合不由本地选择证明 |
| 新版 Dialog、Friends、Reporting 等 | 原生容器图按同一规则读取 | 各流程未独立验收，不从源码相同推断 PASS |
| 第三方 Mod UI | 原生 GuiEventListener/ContainerEventHandler 图及 Widget | 自绘无节点区域诚实保留限制 |

## UI DEBUG 适用目录

通用 setter 与专用本地 typed 操作的生产入口均在客户端 owner thread 执行，使用 UI session Arm 与节点 Resource Version；
它们不是玩家输入，也不取得输入 Lease。下表只描述已实现类型，当前候选五端 Live 状态仍看[状态页](../project/status-milestones.md)。

| 操作 | 实际 Native 类型/值 | 行为与边界 |
| --- | --- | --- |
| ui.widget.active.set | AbstractWidget；boolean | 修改原生启用标志，不点击或执行按钮业务 |
| ui.widget.visible.set | AbstractWidget；boolean | 修改原生显示标志；隐藏节点仍只允许 DEBUG 查找，USER 不因已知 nodeId 绕过隐藏 |
| ui.text.set | EditBox、MultiLineEditBox、可编辑书本页/标题、告示牌四行、HUD标题/副标题/动作栏及玩家列表页眉/页脚；最多4096字符的string；不含Test Instance编辑器 | 编辑器保留原生validator，草稿可能随关闭提交，须恢复后核对；HUD/玩家列表分支只调用本地原生纯文字setter，不改变世界事实或模拟玩家，可能重置生命周期/格式。Test Instance在文字/管理回调前拒绝，不提供任意文字/NBT写入 |
| ui.scroll.set | AbstractSelectionList的原生像素范围；创造、织布机、切石机及交易Screen卷轴的0～1归一化范围 | 前者修改列表像素量，后者修改本地显示位置并同步原生行/可见物品；不能混用单位。无滚动范围只允许0，不选择配方/交易、不取得物品或伪造玩家输入 |
| ui.slider.set | AbstractSliderButton；0～1有限double | 调用原生setter和设置回调，可能持久保存客户端选项；必须显式恢复，不能当成无副作用预览 |
| ui.slider.set（Toast） | progressable的TutorialToast；0～1有限数字 | 原生updateProgress只改变本地显示，不推进教学事实；不支持无进度条类型 |
| ui.toast.reset | 驻留SystemToast；显式title/message两段，各最多4096字符 | 原生reset重设纯文字、重排及生命期；恢复文字不承诺格式/计时回滚，不隐式丢弃另一字段 |
| ui.toast.hide | 驻留TutorialToast、原生支持forceHide的SystemToast，以及适用版本的NowPlayingToast/FriendToast；仅true | 原生单向隐藏，不创建/复活通知、不作为自然完成；NowPlaying结束本地显示更新但不停止音乐；Forge 1.20.1的SystemToast明确不支持 |
| ui.canvas.pan.set | 当前原生首次居中后的选中AdvancementTab；完整有限{x,y} | 绝对GUI偏移，按debugPanRange限制可动轴、保留固定轴原生值；只调用本地scroll，不发送输入或解锁进度 |
| ui.list.selection.set | 原生聊天建议列表及旁观菜单的有效非负索引；expectedValue可为原生初始-1 | 只改变本地选择/高亮，不使用建议、提交聊天、激活旁观条目或传送；旁观同索引为无副作用no-op，owner/页/内容改变拒绝旧token |
| ui.hud.title.times.set | 完整{fadeInTicks,stayTicks,fadeOutTicks}，各0～12000整数tick | 设置本地原生标题三段时长，不以每tick倒计时使token失效；完整原生旧值用于前置比较，不承诺恢复已过去的时间 |
| ui.hud.title.clear | 标题/副标题完整组；仅true | 调用本地clearTitles同时清除两段，组owner及完整内容版本严格复验；不以空字符串替代清除，不承诺逆转已结束生命周期 |

版本/值条件在实际修改前复验；取消、过期节点、Screen/布局变化、错误Arm或不适用类型均不得静默转成其他操作。
原生文本token按完整UTF-16内容采样，公开value仍有2048字符边界；不同尾段或孤立代理单元不得复用同一修改条件。
采样复用有界内容预算，超限或未找到原生文本owner时debugTextGuardAvailable=false，修改前明确拒绝；不会用前缀猜测完整值。
完整值比较和内容指纹只用于DEBUG前置条件，不改变USER呈现、输入落点或正常键入过程。
USER文本替换按Native能力执行：默认Ctrl+A/Backspace；旧两端书名不支持选择和移动光标，声明backspace_from_end及完整值后按码点退格。
实际清空未完成时拒绝追加新文字；不完整内容或未知策略拒绝，不用DEBUG setter替代玩家输入。
responder 替换 Screen 后，`postMutationContextChanged=true`、after token 为空表示已应用但原上下文不再存在，不是“未执行”。
HUD字段位于presentation.children，nodeId为hud.title/hud.subtitle/hud.actionbar，并非GUI可点击目标。
版本绑定实际HUD/世界/Component身份、完整原生序列化与解析文字、Screen/Overlay和GUI尺寸；自然计时不使每次请求都失效。
sideEffects=native_hud_plain_text_and_lifetime：设置纯文字可能重置原生命期/颜色动画，恢复文字不等于恢复旧富文本或计时。
隐藏或过期消息不进入USER；DEBUG保留当前本地字段，未知或预算不足拒绝修改，不借世界内部数据补值。
书本/告示牌已接入上述文本合同；HUD/Toast与自绘交互区域不能因不是AbstractWidget就标成“原生只读/不适用”。
03c目录已接入建议/旁观选择、玩家列表文字、标题生命周期及适用Toast等本地typed分支；各端实现、消费者和冻结候选验收状态统一见[状态页](../project/status-milestones.md)。
专项结果不能替代整组同候选证据，也不能代替真人协作或视觉验收；本页不另行维护M-03阶段或运行进度。
槽位真实物品属于世界/菜单资源，使用独立世界授权的 typed inventory/menu 合同，不能用 UI session Arm 修改物品或借客户端虚拟槽号写服务器槽。
未知节点继续明确拒绝，不通过反射、任意方法或任意NBT来填空。

### Toast 原生修改适配

下表来自五端当前构建实际使用的Minecraft构件；生产适配已接入，真实逐端验证状态见当前状态页。
不可因内容不是Widget就标记只读，亦不可将构件入口、编译或专项通过当作完整呈现目录验收。

| 原生类型 | 五端可用入口与差异 | 当前实现与安全边界 |
| --- | --- | --- |
| SystemToast | 五端均有reset(Component, Component)；旧两端和26.1.2保留title Component及格式化messageLines，26.3两端标题也只剩titleLines，并有recalculateWidth | ui.toast.reset显式替换两段；版本绑定实际manager/驻留实例/Toast、完整样式与文字、布局/屏幕和原生重置状态，不以列表下标冒充身份；不用自然动画时钟使每次请求失效 |
| TutorialToast | 五端均有updateProgress(float)和hide；旧两端保留原始title/message，26.x改为格式化lines并有平滑进度 | progressable前置条件、有限值和实际进度/隐藏状态绑定版本；hide不可逆，不修改教学世界事实；旧对象消失或替换必须重新发现 |
| NowPlayingToast / FriendToast | NowPlaying适用于26.1.2及26.3，Friend仅26.3；按各端真实驻留owner接线 | ui.toast.hide只改变本地通知显示；NowPlaying先结束显示更新再请求隐藏，Friend调用原生hide，不调用音乐或账号服务 |

SystemToast的forceHide在1.20.1构件不存在，其余四端存在；不借其他版本方法名推断跨端支持。
进度、文字重设与隐藏是不同原生副作用，不用任意setter兜底；完整编码超预算拒绝，未知/排队类型仍不提供修改入口。
ui-debug-toast专项验证实际Loader完整样式/截断尾部/预算守卫、Native修改与旧版本/值/类型拒绝、真实MCP和网页USER隔离、零玩家输入及退出清理。
Fixture仅在probe中显式启用，通过既有Fixture Screen准备本地通知；Forge用probe结束自身夹具生命期，不伪装成产品强制隐藏能力。

### 进度画布原生平移

ui.canvas.pan.set使用选中AdvancementTab的原生scroll入口，以完整有限{x,y}设置绝对GUI偏移，不复用单轴归一化卷轴合同。
debugPanValue/debugPanRange只在DEBUG图中描述实际值、轴是否可动及原生范围；固定轴保留首次原生居中的值。
初次绘制尚未完成时拒绝可执行版本，不手动更改centered；切页、Tab替换、图边界或GUI变化都使旧token失效。
ui-debug-advancement专项验证固定轴、可平移/恢复、旧值/版本/Screen拒绝及真实MCP和网页USER隔离，不取得玩家Lease或发送输入。
仅恢复本次回执确认的改值和token，外部未知变化或响应丢失时保留现场并报告，不猜测恢复。
该平移分支不代替建议弹层、旁观菜单及HUD/Toast各自的typed验证；各分支当次验收以[状态页](../project/status-milestones.md)为准，不从单项通过推定整组或人工验收通过。

## HUD / Toast 必需目录

此目录用于 M-04 的独立行为验收，不把世界 PLAYER 读数代替当前 UI 呈现，也不从类存在推断 Live PASS。
按各 Target 当前构建构件的类与方法清点：1.20.1/1.21.1/26.1.2 的主类为 `Gui`，26.3 两端为 `Hud`；
26.1.2 的上下文条类以 `Renderer` 结尾，26.3 为 `ExperienceBar`、`JumpableVehicleBar`、`LocatorBar`。
原生方法/类只证明该端有此家族；新的实现仍须从实际绘制阶段采样，USER 不得发布隐藏、排队、过期或未实际呈现的内部值。
下表描述实现机制及验收边界；各端实际运行、冻结矩阵与人工验收状态统一见[状态页](../project/status-milestones.md)。

| 家族 | 必需独立行为 | 验收边界 |
| --- | --- | --- |
| H-01 标题与消息 | 标题、副标题、动作栏的当前文字、显隐与生命周期 | 按原生消息内容和显隐合同读取；本地typed文字/时长/清除不等于恢复已过去的生命周期，也不覆盖聊天等独立文字层 |
| H-02 物品栏呈现 | 热栏/副手、选中格、物品数量、实际显示的耐久/冷却和选中名称/提示 | 装饰、冷却与tooltip来自实际原生draw及真实hover，不返回未显示的物品属性或内部耐久/冷却计数 |
| H-03 生命状态图标 | 普通/特殊心形、吸收/闪烁、护甲、饥饿、氧气、坐骑生命及显隐条件 | 普通与hardcore/frozen/blinking等适用变体按原生素材、绘制颜色和提交顺序采样，不暴露更精细内部数值；创建Hardcore世界不代替死亡流程或视觉验收 |
| H-04 上下文条 | 经验进度/等级、坐骑跳跃与其互斥切换、适用版本定位条 | 按各版本实际原生条/文字绘制及互斥条件采样，不从玩家内部经验或跳跃值补出隐藏条；定位条只在原生适用端提供 |
| H-05 准星与画面覆盖 | 准星/攻击指示、瞄准镜、传送/受冻/睡眠等原生覆盖的当前类型与状态 | 有界记录原生几何、资源及透明度，并遵循该版本实际F1/Screen/Overlay条件；旧端恶心层保留其原生F1例外，不扩成任意纹理解析或最终像素识别 |
| H-06 Boss / 计分板 | 实际可见条目、名称、格式、进度、排序/截断与移除 | 旧Forge使用实际atlas/UV，其余端使用原生sprite；背景/分段/前景保留提交顺序，空白数字格式不补回原始分数 |
| H-07 效果图标 | 原生行/顺序、透明度、闪烁、F1/菜单遮挡；不透出未显示等级/时长 | 图标按实际绘制帧采样，透明/失效帧不进入USER；Inventory效果文字和hover提示依各自原生绘制入口，不从缓存推定可见 |
| H-08 玩家列表 | 原生过滤/排序/上限、页眉页脚、显示名、延迟图标与计分/生命显示 | 独立80行绘制帧保留真实显示文字、ping素材和格式化分数/心形；不公开profile、精确latency或原始分数，本地packet夹具不证明真实多人网络 |
| H-09 其他文字层 | 当前聊天、F3 调试文字、字幕、唱片/音乐、保存及适用的 Demo 提示 | 从各版本实际晚期文字入口采样；Forge的F3/Demo与动作栏走ForgeGui实际绘制，保存按原生适用性处理；无对应draw时不以后台缓存补值 |
| H-10 Toast | 各原生类型实际绘制的文字/图标语义、进入/完全显示/退出、排队与过期边界 | 旧四类及适用NowPlaying/Friend使用实际驻留draw和有界上下文；部分呈现隐藏全文，排队未绘制不进USER，原生隐藏请求不冒充自然教学/业务完成 |

Toast 与定位条的原生适用性如下；“无此原生类”是构件事实，不是对其他未执行流程的豁免。

| 原生内容 | 1.20.1 Forge | 1.21.1 NeoForge | 26.1.2 NeoForge | 26.3 NeoForge | 26.3 Fabric |
| --- | --- | --- | --- | --- | --- |
| System / Tutorial / Advancement / Recipe Toast | 已接入 | 已接入 | 已接入 | 已接入 | 已接入 |
| NowPlayingToast | 无此原生类 | 无此原生类 | 已接入 | 已接入 | 已接入 |
| FriendToast | 无此原生类 | 无此原生类 | 无此原生类 | 已接入 | 已接入 |
| LocatorBar / LocatorBarRenderer | 无此原生类 | 无此原生类 | 已接入 | 已接入 | 已接入 |

这些事实来自当前 Target Facts 选定的实际 Minecraft/Loader 构件，不使用旧缓存版本代替。
真实外部服务和账号流程与合成的原生呈现夹具分别标明；仅验证原生 Toast 文字不证明 Friends 服务或联机成功。

## 通用交互区域

Widget 图不是所有交互的完整目录。VanillaInteractionRegions 以明确的原生输入处理合同补充滚动条、
文字、建议与进度；配方展开层读取真正的原生 Widget 集合，不调用按钮业务函数。
节点标记 capabilitySource、interactionLayer、occluded/occludedBy 和受限操作。
文字事件保留 clickEventType 与不可信 metadata，Agent 不从内容获取指令或额外权限。
布局来自原生 font/glyph active area 或经过原生 style hit test 验证的区域，不做图片识别。
UI bounds 改为 double；既有整数布局不变，字体缩放和 glyph 小数位置不会在生成 Java 模型中丢失。

ui.drag 支持 fromOffset/toOffset 的 0–1 局部比例，默认中心。目标先按语义重新解析，再从真实边界计算落点，
适用于同一画布内平移和滚动条拖动，不需要 Agent 手写屏幕坐标。
拖动时允许自身引起的滑块纵向位置/命中裁剪高度变化；槽位内容仅在同一原生回调后推进守卫，
Screen、GUI 尺寸、对象、轨道几何和语义上下文仍逐次校验。
文本 focusOnly=true 保留当前焦点/光标/选择，不点击重新定位；焦点消失则拒绝。
mouse.move 的 direct 模式只用于 GUI，默认 paced 模式及既有权限、Lease、取消合同保持不变。

## 接口规则

节点 actions 是操作范围；不能把 list/container 的中心点击当作对子项的支持。
选择器递归搜索 children，歧义必须显式 nth 或更具体的身份；nodeId 不可解析或自行拼接。
原生对象身份使用有界的最近访问表；容量回收逐项淘汰旧对象，不整表清空当前界面身份。
编号在 Runtime 内单调分配，被淘汰对象重新出现也不复用旧编号；Screen、菜单与语义版本仍独立校验。
该缓存不是永久令牌承诺，未使用的旧目标仍可能失效，调用者应重新读取而不是拼接身份。
worldId 是存档内逻辑 ID，不是显示名称，也不是持久化写入身份。
resourceId 用于创造标签等注册资源；entryIndex 仅在对应列表上下文内有意义。
controlKey 选择专用操作，例如 pack.move_up、stonecutter.recipe.0、book.page、sign.line.3。
自绘卷轴使用 creative.scrollbar、loom.scrollbar、stonecutter.scrollbar、merchant.scrollbar
加 .track / .thumb 后缀；scrollAmount 为 0–1 比例，不能与通用列表的像素滚动量混用。
动作仍调用原生鼠标回调，不调用菜单 scrollTo 或写入滚动字段；禁用卷轴不发布输入动作。
部分原版菜单的绘制和按下范围不同，滑块目标裁剪到实际可启动拖动的交集；不修改原版行为。
packId 限定资源包身份；translationKey 来自原生翻译组件，避免依赖当前显示语言。
这些字段仅精确匹配，重复目标仍需更具体的选择器；不能凭 controlKey 拼造 nodeId。

保存的服务器列表使用 server.entry 选择行，悬停后才发布原生可见的 server.join、
server.move_up、server.move_down；首尾不可用排序不进入 USER。序号变化会使旧子目标失效，
重新读取后才操作，不根据服务器名称拼接目标，也不执行内部 join/swap 方法。
列表标签仅使用显示名称，不把 narration 中的隐藏地址、精确延迟或玩家名单带入 USER。
服务器 MOTD、版本/状态图标与 tooltip 的完整可见数据仍需单独适配，不由名称读取推定已覆盖。

隐藏列表行/子控件支持 scroll_into_view，使用原生滚轮，最多 24 次尝试。
滚动期间绑定初始 Screen 与目标身份；滚入视口后重读边界，不能点击旧坐标。
列表报告裁剪命中区域；未渲染布局的隐藏控件不得冒充可点击。

type_text 只接受语义 text_field，最多 2048 UTF-16 单元、有效 Unicode，拒绝控制字符和
未配对 surrogate；只有 multiline=true 的字段接受 LF，并经原生 Enter 输入。
默认全选替换，字符走原生 KeyboardHandler，不直接 setValue，
不使用宿主剪贴板或模拟 IME。set_value 是 0–1 滑块比例，游戏可能离散取整。
每次字符/按键验证 Screen、目标对象、几何和文本焦点；搜索引发菜单内容变化是合法变化，
但不能放宽 Screen/字段身份检查。所有输入仍要求 TAKEOVER、scope、Lease，并支持取消清理。
旧版书本与告示牌没有可点击文本框，使用 inputRoute=screen_keyboard；节点没有虚构 bounds，
resolve 不返回假 interactionPoint。告示牌经原生方向键选行后再输入；书本页码进入 contextKey，
同一个 Screen 翻页后旧页面目标也会失效。特殊菜单绑定当前配方/图案/交易状态，移动后重新校验。

## 可重复验证

先只启动一个隔离客户端，在标题页运行；不操作日常或用户重要实例。
TokenFile 指向该 Target 的 feature-runtime_probe 隔离实例 token 文件。

```powershell
./gradlew.bat :version:26.3-neoforge:runValidationFeatureRuntimeProbeClient
. ./components/validation_tools/RuntimeConnection.ps1
$connection=Get-RuntimeConnection 'validations/feature-runtime_probe/instance/26.3-neoforge/client' '26.3-neoforge'
pwsh -File components/validation_tools/compatibility-standard_gui/task/Invoke-VanillaUiTreeConformance.ps1 -Target 26.3-neoforge -BaseUri $connection.BaseUri -TokenFile $connection.TokenFile
pwsh -File components/validation_tools/compatibility-standard_gui/task/Invoke-VanillaRenderedUiConformance.ps1 -Target 26.3-neoforge -BaseUri $connection.BaseUri -TokenFile $connection.TokenFile -Cases all
```

验证语义输入、标签页、滑块、列表滚动、嵌套选项，并恢复设置、退回标题页、释放输入。
有存档时验证真实世界行选择；没有存档时不伪造该检查。结果固定写入本项 result，
绑定当次 commit、dirty 状态与源码哈希；不替代最终 JAR、五端完整流程或人工视觉验收。
Rendered 专项在隔离实例创建并复用专用创造世界，准备少量空资源包，结束时恢复包顺序。
场景准备使用真实玩家命令且明确标为 diagnostic Arrange，不算 gameplay acceptance；
实际 UI 操作均为语义 GAME_ROUTED，无截图、手写坐标或直接按钮回调。
结果固定写入 rendered-ui-<target>.json；失败也覆盖当前结果，不能继续沿用旧 PASS。

## 固定副本与覆盖清单

新增 conformance-vanilla_ui 以独立实例执行完整流程，从不可覆盖的基线恢复 saves、options 与资源包。
恢复前验证基线哈希，恢复后逐文件比较；只在 Runtime 干净停止时复制。
旧测试副本进入回收站，不处理日常实例。Baseline 与运行数据被忽略，不进 Git。
每端基线独立，当前用户名、种子及场景固定；初始化是诊断 Arrange，不算 gameplay acceptance。
模拟操作和断言均走接口；截图只供人类网页观看，自动测试 screenshotAssertions=0。

```powershell
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-fabric -DebugUi -KeepClient
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -All -DebugUi
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario editors
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario pointer
```

task/screens.json 列出原版界面家族，result/<target>.json 记录实际到达的界面与未到达项。
All 仅用于已获用户授权的五端并行批测：默认由 OS 分配独立 Runtime 端口、独立副本、每端最大 Java 堆 4 GiB。
按实例目录发现并固定启动身份；显式 RuntimePort 只用于需要固定端口的场景，不作为设备级端口调度器。
工具用 jcmd 核对实际堆上限，五端都到标题页后再放行测试，结束后关闭本轮客户端并检查端口释放。
不把堆上限当作进程/GPU 总内存上限。默认单端运行；All 不接受 KeepClient，原有网站和日常实例不参与。
批测期间不可最小化窗口、显示桌面或锁屏；无需同时聚焦五个窗口。0×0 窗口导致的目标失效不能重试绕过，
受干扰批次保留失败结论，关闭自有客户端、重新恢复固定副本后完整重跑。
当前矩阵含五类 UI DEBUG、HUD/Toast、17 种原生菜单、创建世界草稿、统计页与死亡/重生，实际当次结果以各 Target 文件为准。
不能把历史 20/21 项 PASS 当作新矩阵通过；所有断言来自接口，收尾输入与 Lease 清理。
Scenario editors 从相同不可变基线恢复副本，只运行书本、两种告示牌、生存合成及进入/退出流程；
Scenario hud 只执行十二项 HUD/Toast 及进入/退出流程。结果分别写入 `<target>-editors.json`、
`<target>-hud.json`，不覆盖完整结果。Scenario spectator 只运行四项菜单及进入/退出流程，
结果为 `<target>-spectator.json`。All 不接受缩减场景。
Fabric 的编辑器流程另交接 Lease/任务给真实 MCP 与临时网页服务，验证输入、恢复及旧书页拒绝；
网页原生焦点追加通过 input 入口，不把语义点击成功等同于所有浏览器事件已验收。

2026-10-14 本次工作树结果如下；是开发 Runtime 自动验收，不是最终 JAR 或人工视觉验收。

| Target | Native 流程 | UI DEBUG 五类 | HUD/玩家列表与窗口装饰 | 自绘卷轴 | 旁观菜单 | 收尾输入 |
| --- | --- | --- | --- | --- | --- | --- |
| 1.20.1 Forge | 46/46 | PASS | PASS | PASS，切石机仅禁用分支 | PASS | 0 |
| 1.21.1 NeoForge | 46/46 | PASS | PASS | PASS | PASS | 0 |
| 26.1.2 NeoForge | 46/46 | PASS | PASS | PASS | PASS | 0 |
| 26.3 NeoForge | 46/46 | PASS | PASS | PASS | PASS | 0 |
| 26.3 Fabric | 46/46 | PASS | PASS | PASS | PASS | 0 |

1.x 两端实际到达 57 类 Screen，26.x 三端为 58 类，另有无 Screen 游戏状态；
到达数量包括加载页，不等于完整操作覆盖率。
当前矩阵包含创建世界草稿导航/取消、Tab 玩家列表显隐和装饰显示名、三模式窗口标题与角标恢复。
Toast 增量逐端验证多行进度的原生标题/名称阶段、F1 隐藏和结束，以及配方解锁的真实两行文字；
textPhase 与 last_native_toast_render 来源来自实际回调，不是未来文字的预测。
连续语言页导航实际跨过 2253–2344 个分配身份后，未变化页面上的按钮仍可稳定读取、resolve 和点击。
当前批次为 6d932972f03246ca9a2d8359d39f1aa1，所有 sourceChangedDuringRun 均为 false。
旁观菜单通过真实中键打开、热栏键选中、重复点击零输入、旧节点/F1/READ 拒绝、明确 Close 和原生过期；
Fabric 两条 spectatorSamples 经真实 MCP stdio/网页 HTTP，旧菜单上下文拒绝且不使用世界坐标点击。
这不覆盖多人分类分页/实际传送、所有键位类型、专用旁观 DEBUG mutation 或浏览器完整人类验收。
状态效果图标的五端原生绘制增量通过：每端采到 31 个短时效果样本，包含全透明与可见帧，
自然过期后移除；USER 不包含透明/隐藏图标或等级/时长。Fabric 另通过真实 MCP 与独立网页的只读字段传递。
此次五端构建、325 项 Target Java 测试、63 项共享安全测试、40 项 Companion/网页测试、
协议生成、Control/结构/文档检查及 verifyRelease 通过；游戏结果为开发 Runtime，并非最终制品运行验收。
新增四项流程五端均通过：书本多行/追加/翻页/签名后重读、普通与悬挂告示牌四行编辑/保存重开、
生存背包原生 2×2 合成及结果转移。保存与合成不是直接修改数据，也不调用正式 Persistent Write。
Fabric 另有六条 editorSamples：真实 MCP 与独立网页服务分别编辑书本和两种告示牌并恢复内容，
验证原生焦点追加、旧书页拒绝及输入/Lease 交接；不代替浏览器事件全覆盖或人工视觉验收。
新批次前的 c27d8edc9bf5478e895265278b23fe3e 因用户最小化窗口导致三个旧版窗口 0×0，
目标守卫拒绝输入，整批记为失败；恢复后关闭自有客户端并从固定副本整批重跑，不拼接结果。
服务器列表管理另经五端实际验证：添加两条本机记录、悬停排序、旧目标拒绝、编辑、删除确认/取消和直连页取消。
加入服务器、完整状态文案/tooltip、LAN 与身份认证不在本次通过范围内。
窗口图标只以实际原生提交状态和共享像素单元测试验收，不读取 OS 截图、不代替用户评价角标大小。
五端本轮同时运行，实际最大堆均为 4 GiB，全部正常退出并释放端口；详情见 batch.json。
并行首轮 Fabric 书本流程记录过超时，补齐“客户端所选槽位已收到书本”的前置等待后重新恢复全部副本通过。
侧栏断言也等待客户端行数及最高分条目齐全，不把服务器命令完成当成客户端渲染数据已同步。
System/Tutorial Toast 的读取适配已构建并在真实 Loader 中加载，本矩阵未为这两类加入独立文字断言；
不能把进度 Toast 的 PASS 当作它们的完整验收。复杂设置/配方、服务/账号和其余 HUD 仍未完成。
此前 UI DEBUG 增量的真实 MCP stdio 额外通过 6 项类别及 UI 调试/恢复检查，不申请玩家 Lease；
本轮 Fabric 额外启动真实 MCP stdio 与随机本机端口网页服务，在同一新副本读取 RecipeToast；
两条只读链核对实际文字、阶段、无 debugLabel/动作且不获取 Lease，结果写在 toastSamples 中。
这不代替其他 HUD、菜单动作或浏览器人工验收，不沿用旧六项结果证明新字段。
本次服务器子目标已通过网页转发/隐藏拒绝/排序旧目标拒绝的自动测试，尚无该流程的真实网页或 MCP Live 结果。
自绘菜单卷轴五端分别实测：创造搜索/标签/滑块拖动、织布机图案、12 条固定交易以及旧目标拒绝。
1.21.1/26.x 另以凝灰岩测试切石机多行配方；1.20.1 石头配方不足以启用卷轴，只验证禁用且无动作。
这不承诺全部输入材料、配方或交易组合。相应 20 项 Target 单元测试与网页透传/禁用拒绝测试通过，
新增卷轴的真实 MCP/网页消费者链仍待执行，不以 Native 或单元测试替代。

网页六项消费者链单独记录 web-26.3-fabric.json，不由 Java/Native 成功推定网页成功。
网页链每次也应先完整执行上方 Fabric 命令恢复副本；不要直接在修改过的当前世界重复冒充固定副本验证。
原版 quick-craft 在 mouseDragged 时加入格子，初次按下本身不加入；轨迹必须实际经过第一格及其他目标。
存在标准输入路径不等于各业务流程全部验收；外部服务、账号授权、OS 文件选择/浏览器不属于 Minecraft UI 自动适配。
第三方内容暂不承诺自动适配。只读语义发现不通过试点按钮探测，破坏性操作只在明确的 disposable fixture 中验证。

## 既有验收边界与后续

下表为先前绑定源码的五端检查；它不替代新框架和网页的当次结果。每端独立运行，没有按代码相同推定。

| Target | 世界行 | 中文输入/标签 | 滑块/隐藏行/嵌套选项 | 收尾输入 |
| --- | --- | --- | --- | --- |
| 1.20.1 Forge | PASS | PASS | PASS | 0 |
| 1.21.1 NeoForge | PASS | PASS | PASS | 0 |
| 26.1.2 NeoForge | PASS | PASS | PASS | 0 |
| 26.3 NeoForge | PASS | PASS | PASS | 0 |
| 26.3 Fabric | PASS | PASS | PASS | 0 |

固定结果为 `validations/compatibility-standard_gui/result/vanilla-ui-<target>.json`，
记录当次 commit、dirty 状态和相关生产源码哈希。五端 build / check / verifyRelease、
205 项产品 Java 测试及 25 项 Companion/网页测试通过。
首轮 Fabric 26.3 记录过 MCP 文字/世界选择、网页相同操作和 PNG Capture；该记录只证明当次源码。
原始世界只用于隔离验证，创建/保存由正常 Vanilla GUI 完成；没有正式 Persistent Write 调用。
上述检查不是全部原版 UI、五端最终 JAR 或统一人工视觉验收。

本轮五端另外独立完成以下 12 项 Rendered 场景，每端 held keys/buttons=0：
资源包选择、上/下排序、移除并恢复；切石机配方、织布机图案、附魔、交易、信标；
书本多行编辑/翻页/旧页面拒绝、签名/内容读取；普通四行及悬挂告示牌编辑。
固定 `rendered-ui-<target>.json` 记录实际运行的源码与工具哈希。

Fabric 26.3 的消费者验证额外通过真实 MCP stdio 资源包选择/排序/恢复，
网页书本多行输入和 stale page 拒绝，以及无鼠标边界的告示牌语义行输入。
在其隔离客户端和本机网页已启动、READ 且标题页时运行：

```powershell
Remove-Item Env:MCP_MINECRAFT_BASE_URL, Env:MCP_MINECRAFT_TOKEN, Env:MCP_MINECRAFT_TOKEN_FILE -ErrorAction SilentlyContinue
$env:MCP_MINECRAFT_INSTANCE_DIR=(Resolve-Path validations/feature-runtime_probe/instance/26.3-fabric/client).Path
$env:MCP_MINECRAFT_TARGET='26.3-fabric'
node components/companion/scripts/live-vanilla-ui.mjs
```

消费者工具要求先完成 Rendered 专项，且网页连接同一隔离实例；不接触其他存档。
固定 `ui-consumers-26.3-fabric.json` 只证明所列消费者链路，场景准备仍为诊断证据。

五端实现使用统一外部合同，各自适配 Minecraft/Loader 原生 API。
构建、单元测试与 Live 结果由当次执行报告负责；历史证据不证明新候选。
继续完善 UI 时补清单中的自绘交互适配和实际场景；世界桥接复用既有 world.action 与 UI 输入，不另造菜单操作系统。
正式 Persistent Write、统一人工 UX 和其他 Phase/Extension 不属于本轮。

## 当前合同的验收边界

本节列出当前合同的验收条件；已接入实现与已有专项证据不表示所有部署、冻结候选消费者或真人步骤均已通过。

- READ、OPERATE、TAKEOVER下的USER/DEBUG tree、resolve、条件、Event、Recording与网页/MCP均保留类别权限，不用切模式取回读。
- TAKEOVER内正常语义点击和独立DEBUG读/写分别记来源；OPERATE不得获取真人输入Lease或偷偷点击GUI。
- 真人Esc仅停止active/queued玩家输入，held=0/post-revoke input=0；独立已授权DEBUG可继续，模型持续收到用户决定与reconsent状态。
- OPERATE与真人并行修改按owner thread及显式资源/值条件处理，允许覆盖用户状态但不跳过stale条件；Agent Routed Esc不人工撤销。
- OPERATE原生命令保持当前Screen、焦点/聊天草稿及held input，不申请输入Lease或临时切TAKEOVER；命令统一USER，缺command scope/真实玩家权限仍拒绝。命令自身原生效果与输入通道干扰分开断言。
- 物理Esc仅取消依赖接管的聊天键入，独立原生命令/DEBUG按各自生命周期处理；reconsent latch持续且不被OPERATE命令清除，自身取消或连接失效后无迟到提交。
- 五端分别验证仅客户端接无模组服务器、仅服务端零玩家/原版客户端及双端Peer；服务器原生控制台USER输出与DEBUG诊断不混入玩家UI。
- 控制台以独立操作员授权的OPERATE提交命令，不接管宿主终端；批次/取消/不保证关联及实际效果分别记录。
- 新模式与部署的完整消费者/最终制品证据必须同候选；截图/录制仍排除Operator UI，人工观感不由自动检查推定。
