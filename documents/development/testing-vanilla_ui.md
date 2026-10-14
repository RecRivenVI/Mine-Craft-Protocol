# 原版 UI 语义覆盖

当前实现覆盖原生 Widget 图、列表行、菜单槽位、原生滚动条、富文本区域、聊天建议与进度画布。
这不是“全部原版 UI 已验收”的声明；普通控件能被提取，不等于所有自绘交互区域已暴露。
操作只经项目 Native/MCP 接口，不依赖截图识别、手写坐标、Computer Use 或直接调用按钮业务函数。

## 支持范围

首个里程碑为 UI 三模式二类别完整交付，尚未完成。
当前树读取默认 USER；隐藏/遮挡节点及内部字段不进入用户视图，技术身份/版本/边界保留用于安全定位。
显式 category=DEBUG 要求 ui、debug、debug.ui.read，返回受预算限制的已捕获图，不自动取得输入 Lease 或 Arm。
resolve 使用相同类别，USER 无法解析并读出隐藏内容；真实输入仅接受 USER，不静默降级为 Debug。
Recording、Event resync、Wait/Assert 等既有 uiTree 消费路径默认使用 USER，不另开隐藏数据旁路。
Screen 静态目录已生成；UI DEBUG 已提供控件启用/显隐、EditBox 文本、列表滚动和滑块设值五类操作。
须独立 OPERATE、debug/debug.write/debug.ui、客户端 session Arm 和节点版本/可选值前置条件；不使用输入 Lease。
非 Screen 的 presentation 单独提供只读 HUD 标题/副标题/动作栏与 System/Tutorial/Advancement/Recipe Toast。
USER 不含隐藏/排队内容，HUD 有 Screen/Overlay 时保守不发布；Toast 文字来自原生渲染回调，
多行进度分别发布当前标题或名称，不提前透露下一阶段。样本超过一秒或 GUI 尺寸变化时不发布 USER 文字；
textPhase 标明阶段，完整待显示文字仅在 DEBUG 的 debugLabel 中，均受 2048 字符预算限制。
这不是 framebuffer 像素识别，也不证明第三方改绘后的最终可见性。新增热栏/副手图标、选中状态、Boss 栏显示进度和侧边计分板；
USER 不包含隐藏 HUD、未显示物品属性或 blank 数字格式隐藏的原始分数。
状态效果图标按原版两行与顺序读取，不包含等级/时长；原生绘制参数提供 opacity 和 renderPhase。
透明图标、隐藏 HUD 和无有效样本不进入 USER；样本绑定玩家、Screen 和 GUI 尺寸，250 ms 后失效。
五端分别观察实际绘制透明度，1.x 保留 float、26.x 使用绘制颜色中的量化 alpha；不按时长预测闪烁。
第三方替换图标绘制及最终 framebuffer 的所有遮挡情况仍不保证。玩家列表新增原版显示名和页眉/页脚读取，
USER 仅在原生列表展开时返回，不含账号内部字段、精确延迟或列表分数/心形。
多人排序、80 人边界和页眉/页脚仍缺独立服务端夹具验证。其余 HUD 和所有业务组合仍未完成。
旧固定结果不证明新增合同。

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
| HUD / Toast | presentation 中的有界文字、热栏/副手、Boss 栏、侧栏计分板、原生绘制阶段的状态效果图标、玩家列表显示名，非可操作节点 | 其他 Toast 尚未覆盖；玩家列表完整信息、多玩家夹具与完整视觉可见性仍待验收 |
| 旁观者菜单 | 原生快捷键打开、语义选中/明确执行、选中名称与提示，图标显示边界不冒充鼠标命中区域 | 多人分类分页/实际传送、鼠标/扫描码/Loader 修饰键热栏绑定与专用 DEBUG mutation 尚未验收或支持 |
| 新版 Dialog、Friends、Reporting 等 | 原生容器图按同一规则读取 | 各流程未独立验收，不从源码相同推断 PASS |
| 第三方 Mod UI | 原生 GuiEventListener/ContainerEventHandler 图及 Widget | 自绘无节点区域诚实保留限制 |

## 通用交互区域

Widget 图不是所有交互的完整目录。VanillaInteractionRegions 以明确的原生输入处理合同补充滚动条、
文字、建议与进度；配方展开层读取真正的原生 Widget 集合，不调用按钮业务函数。
节点标记 capabilitySource、interactionLayer、occluded/occludedBy 和受限操作。
文字事件保留 clickEventType 与不可信 metadata，Agent 不从内容获取指令或额外权限。
布局来自原生 font/glyph active area 或经过原生 style hit test 验证的区域，不做图片识别。
UI bounds 改为 double；既有整数布局不变，字体缩放和 glyph 小数位置不会在生成 Java 模型中丢失。

ui.drag 支持 fromOffset/toOffset 的 0–1 局部比例，默认中心。目标先按语义重新解析，再从真实边界计算落点，
适用于同一画布内平移和滚动条拖动，不需要 Agent 手写屏幕坐标。
拖动时允许自身引起的滑块纵向位置/命中裁剪高度及槽位内容变化；
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
pwsh -File components/validation_tools/compatibility-standard_gui/task/Invoke-VanillaUiTreeConformance.ps1 -Target 26.3-neoforge -BaseUri http://127.0.0.1:25582 -TokenFile validations/feature-runtime_probe/instance/26.3-neoforge/client/minecraft-protocol/token
pwsh -File components/validation_tools/compatibility-standard_gui/task/Invoke-VanillaRenderedUiConformance.ps1 -Target 26.3-neoforge -BaseUri http://127.0.0.1:25582 -TokenFile validations/feature-runtime_probe/instance/26.3-neoforge/client/minecraft-protocol/token -Cases all
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
```

task/screens.json 列出原版界面家族，result/<target>.json 记录实际到达的界面与未到达项。
All 仅用于已获用户授权的五端并行批测：独立端口 28591–28595、独立副本、每端最大 Java 堆 4 GiB。
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
$env:MCP_MINECRAFT_BASE_URL='http://127.0.0.1:25583'
$env:MCP_MINECRAFT_TOKEN_FILE=(Resolve-Path validations/feature-runtime_probe/instance/26.3-fabric/client/minecraft-protocol/token).Path
node components/companion/scripts/live-vanilla-ui.mjs
```

消费者工具要求先完成 Rendered 专项，且网页连接同一隔离实例；不接触其他存档。
固定 `ui-consumers-26.3-fabric.json` 只证明所列消费者链路，场景准备仍为诊断证据。

五端实现使用统一外部合同，各自适配 Minecraft/Loader 原生 API。
构建、单元测试与 Live 结果由当次执行报告负责；历史证据不证明新候选。
继续完善 UI 时先补清单中的自绘交互适配和对应实际场景，不扩展游戏世界动作。
正式 Persistent Write、统一人工 UX 和其他 Phase/Extension 不属于本轮。
