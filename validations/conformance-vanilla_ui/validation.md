# 原版 UI 通用交互验证

## 目标

验证原版 UI 的语义目标发现与真实 GAME_ROUTED 输入，自动断言只读取接口，不使用截图、视觉模型或宿主输入。
范围包括标准页面、滚动条、富文本链接、建议弹层、进度画布及菜单；第三方自绘界面不承诺自动适配。
每次从同一受控基线恢复测试副本，日常实例和用户存档不参与。

## 运行

```powershell
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-fabric -DebugUi -KeepClient
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -All -DebugUi
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario editors
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud-vitals
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario pointer
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario special-menus
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-debug
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-debug-toast
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-debug-advancement
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-completion
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-local
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-navigation
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-native-pages
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-world-pages
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud-camera
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud-toast
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud-player-list
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud-variants
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud-late
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud-camera-variants
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud-saving
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud-hardcore
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario hud-demo
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-breadth
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-breadth -CheckPrefix 'UI recipes and hotbars:'
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Read-VanillaUiCatalog.ps1 -Target 26.3-neoforge
```

首次由工具经标准接口创建夹具并在正常退出后保存基线；以后只恢复该基线，不覆写它。
ui-completion只覆盖本次增量：声明的right_click/shift_click真实槽位派发、HUD组合就绪及Native/MCP结构化失败解释、
聊天建议局部typed选择、实际完成HUD绘制中的物品装饰和上下文条。它不是all，不以目录映射或单测替代Live。
ui-local检查本地标题整组生命周期、旁观菜单纯高亮、玩家列表页眉/页脚typed字段及适用的可见性Toast。
ui-navigation检查有限原生设置/创建草稿/模式切换/本地Dialog页面，probe入口仅在该场景或all显式启用。
ui-native-pages只跑有限确认/恢复/自有辅助世界/重配置页面，供相关失败局部复验；不替代导航或完整all结果。
ui-world-pages仅复验自有辅助世界恢复与重配置页面，不重复已通过的确认及一般恢复页。
CheckPrefix只执行所选专项中名称匹配的检查及必要的启动、世界与退出边界，结果明确标记partialScenario；不能用于all或替代完整交付证据。
hud-camera只覆盖其列出的真实准星、攻击条、装备覆盖和望远镜输入，不由生产Hook存在推定Portal/睡眠等尚未实测分支通过。
hud-toast检查26.3原生Friend/NowPlaying实际驻留绘制的进入、完整显示和退出；部分显示不泄露文字。
音乐夹具仅使用自有静音副本中的公开MusicManager，不声称真实声音、Friends服务或随机曲目的事务恢复；该场景的存在不等于已通过。
hud-player-list安排明确标记的本地原生client packet夹具，验证实际80行排序/筛选、延迟图标、分数格式/心形及其原生窄宽回退；不创建服务器玩家，不称真实多人网络验收。旧Forge仅验证原生支持的Numeric/Hearts格式与实际atlas，不伪造Fixed/Blank或listOrder。
hud-variants、hud-late和ui-breadth分别覆盖有限生命变体/Boss与侧栏、晚期文字/原生字幕消息、特殊世界菜单及配方/热栏；结果只证明其实际执行的分支，不推定所有原版条件或参数组合。
hud-camera-variants通过真实进入传送门、放床睡眠/醒来、恶心贴图及暗角选项/显隐证明采样；版本没有独立暗角选项时明确标记OPTION_ABSENT，不伪造设置。
hud-saving通过项目接口的真实暂停触发一次普通保存，独立要求USER实际Native提示绘制、同PID原生保存完成/标志清除、淡出及held=0。
Forge 47.4.28默认ForgeGui没有注册保存提示；该端只核验实际HUD/默认Overlay注册表并明确不适用，不计USER绘制PASS，其余四端保留真实绘制要求。
probe仅在该次原生保存标志设置后安排一次最多3秒的诊断同步屏障，不写保存状态/透明度、不额外调用保存；不证明自然保存耗时或性能。单人原生没有save-all命令，不以命令回执替代绘制。
hud-hardcore通过原生创建流程创建唯一自有Hardcore世界，观察实际心形后正常保存退出；保留在隔离目录，不改基线或现有世界。
hud-demo独立启动真实--demo客户端，按实际Java命令行与同PID端点核验，进入Demo_World，经真实介绍页的Later按钮观察原生倒计时后正常保存退出；不点击购买或重置，不由probe布尔值模拟Demo，也不承诺跨运行保留该临时世界。
场景前置只改自有固定副本；实际Slot和HUD Act使用GAME_ROUTED，typed建议修改单独标DEBUG且无输入Lease。
special-menus 为独立专项：讲台翻页/取书、马鞍往返和驴胸箱货物往返均走正常语义输入。
有自动合成器的版本另检查禁用/启用空格、菜单版本变化及旧版本拒绝；1.20.1的原生构建中没有CrafterMenu/Screen，不伪造该用例。
前置状态只由自有固定副本中的诊断命令准备，Act 不调用菜单业务方法、不手写UI坐标；不把专项等同全部原版UI完成。
游戏操作、收尾、复制和结果均由工具执行；默认一次一个 Target。
用户明确允许并行批测时可用 All；五端使用独立副本、token 与 OS 自动分配的 Runtime 端口，按实例发现并固定启动身份。
仅显式指定非零 RuntimePort 时使用连续的五端口区间，启动前检查占用，不按历史示例端口连接其他实例。
每端最大堆为 4 GiB、初始堆为 1 GiB；启动后用该 Java 的 jcmd 核对实际最大堆，
五个客户端全部达到标题页后才同时放行。该限制不包含堆外/GPU 内存和 Gradle 进程。
All 不支持 KeepClient，不新建基线；先单端准备基线。批次结束检查所有自有客户端与端口释放。
端口覆盖仅用于该验证的 JavaExec 启动，不改 Runtime 或日常实例配置。
结果写入固定的 batch.json 和各 Target JSON；批测不是宿主输入/人工视觉验收。
批测期间保持窗口未最小化，不使用显示桌面或锁屏；无需让五个窗口同时获得焦点。
窗口缩为 0×0 或 Screen 被外部操作替换时，原有目标失效保护应拒绝输入，不能绕过守卫继续计为 PASS。
受干扰批次记为失败；恢复窗口后经接口正常退出，再从固定副本完整重跑，不拼接不同批次的检查结果。
DebugRead 仅为该测试子进程显式配置 debug、debug.ui.read，不授予 debug.write、Arm 或更多世界修改权限。
本矩阵需要 DebugUi：另外显式授予 debug.write、debug.ui 与仅用于夹具同步的 debug.world.read，测试自己申请/解除仅 UI session 的 Arm。
告示牌/进度夹具的服务器 tick 屏障先释放输入租约，在 READ 下显式读取 DEBUG，结束后检查人工撤销 latch 再申请接管。
不授予世界修改 namespace；READ 类别消费者仍可只用 DebugRead，不能借此执行 UI mutation。
DebugUi另显式授予fixture，仅用于既有诊断Screen准备probe本地Toast夹具，不扩大产品默认scope或将Fixture算为正常玩法。
ui-debug-toast固定输出`<target>-ui-debug-toast.json`，覆盖SystemToast双字段reset、TutorialToast显示进度与原生单向hide。
各端9项同PID/Target/启动时间窗的实际Loader完整样式/尾部/预算及reset代次守卫，以及Native类型/值/旧版本拒绝和零输入，
结合真实MCP DEBUG修改/批次恢复与网页USER私有字段隔离；通知移除后等待实际owner消失，再取新下标目标，不重放旧条件。
Forge没有SystemToast.forceHide，只验证明确拒绝；probe夹具通过自己的结束按钮恢复正常计时，不作为产品隐藏PASS。
固定副本和正常退出/端点删除同样必须通过；这一专项不替代完整UI、所有Toast呈现或完整领域验收。
测试验证三模式独立授权的两类读取；共享玩家输入仍只准 TAKEOVER + Lease，DEBUG 修改独立检查 scope/Arm/条件，并检查拒绝没有输入副作用。
DEBUG 节点的 `debugMutation` 由实际原生 owner/type 生成，说明合法操作、字段和副作用，不是授权或任意 setter；USER 投影不返回它。
26.3 两端额外通过 probe 本地 FriendToast 夹具验证原生单向隐藏与 owner 消失，其余版本不伪造适用结果。
进度画布已支持选中原生Tab的二维typed平移与恢复；聊天建议、旁观菜单和其余HUD/Toast typed修改仍有限制，不因已有呈现读取或能力描述而视为完成。
pointer 为独立专项，不代表 all：验证窗口状态、原生语言列表/创造物品栏滚动条、
语义 ui.drag 将一叠石头真实分到九个背包槽位、余量存入及正常退出；不使用截图或手写落点。
物品由诊断命令准备，Act 走原生玩家输入，结果保留两类来源；core 与各端原生适配均纳入源码哈希。
连续经原生页面进入/退出语言列表，实际跨越超过 2048 个分配身份的范围，
核对未改变页面的按钮 nodeId 在反复读取、resolve 与真实点击之间稳定；不重试旧目标或改用坐标。
共享单元测试另外覆盖表满时当前整棵树、对象引用而非 equals、逐项淘汰及编号不复用。
五种 UI typed mutation 检查旧 revision、值前置条件、错误类型和边界；隐藏控件恢复，编辑内容通过 Cancel 丢弃。
ui-debug专项只运行标题、五类既有setter、语言列表和世界名称草稿；不进入世界或代替完整UI矩阵。
ui-debug-editors为M-03b参考端增量：固定世界中书本页/标题、普通与悬挂告示牌四行分别执行typed文本修改、
错误类型/值/原生宽度/旧版本拒绝、焦点及完整草稿恢复，且每次确认OPERATE、DEBUG证据和零玩家输入。
五端各6项/78份应用或拒绝样本、20次实际应用/恢复及清理均通过；逐文件核对源码工具哈希。
不将新源码计入3d6e3fca已发布快照或旧五端结果。
ui-debug-consumers单独验证五类setter及新增编辑器的真实MCP DEBUG mutate/batch恢复、Arm/值/旧版本拒绝及USER读取隔离，
再通过MCP/隔离网页的真实USER键入恢复原值。网页仍只发布USER图，OPERATE时也可只读快照；不静默改成DEBUG、输入或取得Lease。
当前五端各10项/56份Native样本/26条消费者结果、逐文件源码绑定和正常退出清理通过；不是全部原版UI或最终制品验收。
旧两端书名的空值替换使用声明的原生退格路径，26.x使用全选；Native/MCP/网页均核对实际清空和恢复。
Operation的get/wait与Wait/Assert均验证USER拒绝，诊断读取仍可用；取消只返回生命周期。
缺少独立读权限、混合领域与异步取消/模式发布的拒绝另由真实HTTP单测验证；网页不提供DEBUG编辑功能。

```powershell
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-debug-consumers
```

```powershell
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-debug-editors
```

该增量固定写入`<target>-ui-debug-editors.json`；书本/告示牌修改的是原生编辑草稿，可能在原生关闭流程提交，
必须恢复后正常保存/重开核对；不作为任意NBT或存档写入，不产生GAME_ROUTED证据。
结果固定为`<target>-ui-debug.json`。uiDebugSamples逐项记录原生应用/拒绝、资源版本和零玩家输入；
拒绝后重新读取实际节点，确认版本和输入序号未变化。隐藏/启用、文字、列表和滑块均显式恢复；
调试Arm、模式、版本/值负例及错误原生类型不通过重放或坐标回退。取消/到期的owner许可另由Java并发测试验证。
滑块通过独立 UI Arm 修改原生灵敏度设置，核对文案、诊断证据、零输入与旧版本拒绝，最终恢复原值。
HUD 标题/副标题/动作栏与真实 Advancement Toast 的文本只通过接口判断，非截图验收。
固定副本保留原版movement教学状态；通过真实跳跃输入和相对转向完成教学，读取实际clientTick推进、
可见TutorialToast文本及自然隐藏，held input清零后才继续其他Toast检查。不直接隐藏通知、不改options或不可变基准。
这同时是TutorialToast的USER呈现专项，不把截图或固定等待时间当作完成依据。
Toast 专项额外触发原版多行进度和单条配方解锁，检查标题/名称阶段、不泄露待显示名称、F1 隐藏与自然结束。
Fabric 同一副本另启动只读 MCP stdio 和随机本机端口的临时网页服务，分别读取真实 RecipeToast，
验证 label、textPhase、textSupported、无动作及无 debugLabel；消费者不取得 Lease，关闭后不影响主测试任务或用户网站。
对应 toastSamples 记录在该 Target 结果中；源码绑定包含辅助工具和 Fabric 实际消费的 Companion 源码/构建输出。
没有用 System/Tutorial、好友或音乐提示的未运行流程推定这些提示独立 PASS。
矩阵另检查热栏/副手、选中项、F1 隐藏与 DEBUG 保留、背包遮挡、Boss 进度/隐藏、侧栏裁剪与队伍覆盖；
支持数字格式的版本额外检查 blank 格式不泄露原始分数。状态效果覆盖增益/减益两行、隐藏图标、F1、移除与闪烁限制。
闪烁增量等待短时效果实际到达，采集原生绘制的可见与全透明帧，检查 opacity/renderPhase 与 USER 过滤、
自然过期和输入清理；effectSamples 保存采样数量、最小/最大透明度，不从服务器时长反推画面。
共享单元测试覆盖未完成帧、过期、玩家/页面/尺寸/位置变化与 64 项预算，绘制参数不被修改。
Fabric 另用真实 MCP 和随机端口网页读取稳定效果图标，保留来源/透明度且不申请输入 Lease。
统计页原生标签导航与死亡/重生也在固定副本中执行。
玩家列表测试通过原生 Tab 展开/收起、队伍前缀显示名、F1、背包遮挡与 READ 释放输入，
确认单人无列表目标时不会因 Tab 假造可见名单，DEBUG 保留有界隐藏内容。
当前不注入虚拟联机玩家；页眉/页脚、多玩家排序和 80 人边界未独立验收，不由单人结果推定。
窗口状态检查覆盖 TAKEOVER 角标、READ/OPERATE 原图、多次释放/重接管及既有标题后缀，
从正式 Session 读取原图尺寸数与原生提交结果；右下四分之一范围、最近邻像素缩放、透明合成和
其余原图不变性另由共享单元测试检查。
该检查不读取 OS 图标截图，也不替代用户对任务栏角标大小的视觉判断。
创建世界流程经语义标签进入超平坦预设，选择另一原生预设并确认文本变化，再浏览游戏规则/数据包页，
最终取消整个创建草稿并比较存档身份列表。它不验收所有预设、游戏规则或数据包组合。
多人服务器列表先断言固定副本没有用户服务器，再通过原生表单添加两条 loopback 记录；
悬停后操作排序图标，确认旧排序目标被拒绝，编辑名称、取消删除、确认清理及取消直连。
地址使用本验证自己绑定的随机loopback端口，保持假服务器的所有权；原版会尝试本机状态探测，测试不点击连接，不探测不属于本次验证的服务。
这些正常 GUI 操作只修改可丢弃实例的服务器列表，不连接第三方服务、不证明联机或认证成功。
自绘卷轴新增创造背包上下拖动/搜索禁用/标签隐藏、织布机图案、切石机配方、12 条固定交易的
语义拖动与滚动后重新选择，拒绝已滚出视口的旧目标。1.20.1 石头配方不足 13 项，仅检查禁用
卷轴与无动作；其余四端用凝灰岩检查启用拖动，不将禁用分支冒充滚动 PASS。
较长矩阵在无 held input 的案例边界释放并新建有界任务，保留 Runtime 原有 300 秒任务上限；
显式UI、命令、按键及案例边界均维护自己任务的60秒心跳TTL，不靠频繁点击维持连续诊断命令。
人工撤销会拒绝继续，不用延长产品权限预算或清除撤销状态来完成测试。
每次自动acquire（含Debug恢复和退出清理）先读取新鲜控制状态，拒绝manual latch并绑定该modeVersion；
status与acquire之间发生撤销时由Runtime原子拒绝旧版本，不能自动清除重新同意要求。
编辑器增量验证多行/中文/追加文字、书本翻页后旧目标拒绝、签名后从服务器返回的只读内容，
普通与悬挂告示牌四行乱序编辑、保存后重开及旧编辑器拒绝，并通过生存背包的语义槽位完成 2×2 合成取物。
挂牌重开使用无遮挡的正面站位，等待客户端位置稳定；不把放置时朝上的命中方向当作重开牌面。
场景准备使用既有玩家命令且只作用于本次副本，编辑、保存与合成走原生 UI，不直接改书本或菜单内容。
Fabric 另在当前副本交接控制权给真实 MCP stdio 和随机端口网页服务，验证两种告示牌和书本输入、
网页原生焦点追加与旧书页拒绝；每次交接释放原任务/Lease，结束输入归零，原用户网站不参与。
editorSamples 记录六条消费者结果，相关工具和界面目录纳入源码哈希。
Scenario editors 只执行七项编辑器相关流程，结果单独写入 `<target>-editors.json`；
不能用于 All 批次，也不会覆盖完整 `<target>.json`，不能将定向通过冒充全矩阵通过。
Scenario hud 只执行定向呈现流程，结果为 `<target>-hud.json`，不能用于 All 或覆盖完整结果。
Scenario spectator 执行标题、固定世界准备、旁观菜单和正常保存退出四项流程，
结果为 `<target>-spectator.json`，不能用于 All 或覆盖完整结果。
旁观菜单不是 Screen：通过语义中键打开、热栏键选中、重复点击零输入、旧节点拒绝、F1 隐藏、
READ 拒绝、明确执行 Close 和自然过期，收尾恢复创造模式。图标只声明显示边界，不生成鼠标命中点。
Fabric 另交接固定副本给真实 MCP stdio 与随机端口网页服务，执行打开/选中/关闭和旧网页上下文拒绝；
结果写入 spectatorSamples，仍不替代浏览器人类视觉/交互验收。
没有多人玩家夹具，不推定分类分页与真实传送成功；鼠标/扫描码/Loader 修饰键绑定和专用旁观 DEBUG mutation 仍受限。
静态目录从当前构建的具名 Minecraft 类继承关系生成，不运行游戏、不按文件名猜测 Screen。
inventory-<target>.json 仅清点实际 Screen，全部标为 UNVALIDATED；不是操作 PASS，HUD/Toast 等另需目录。
Fabric 固定副本和本机网页连接就绪后运行网页输入链验证；只检查接口状态和 PNG 传输，不读图片内容：

```powershell
node components/validation_tools/conformance-vanilla_ui/task/run-web-input.mjs
```

网页默认端口 4317；使用独立测试站点时可用 MCP_WEB_UI_URL 指定另一个 127.0.0.1 HTTP 地址，
不重启或占用用户已打开的网站。该项验证网页服务到 Native 的链路，不代替浏览器视觉验收。

同一 Fabric 固定副本以 DebugUi 启动、保持 READ 后，独立检查真实 MCP stdio 的两类读取、拒绝和 UI 调试：

```powershell
node components/validation_tools/conformance-vanilla_ui/task/run-ui-category-consumers.mjs
```

此项先检查 READ 两类，再显式进入 OPERATE、申请 UI Arm、隐藏/恢复标题页按钮并检查旧 revision 拒绝。
不申请 Lease、不操作玩家，收尾解除 Arm 并回 READ；结果固定为 ui-category-consumers-26.3-fabric.json。

## 通过标准

ui-debug-hud专项执行标题、固定世界准备、原生HUD标题/副标题/动作栏文字修改与恢复、消费者隔离及保存退出。
验证配置显式加载自己的probe Mixin，与既有世界验证接线一致；Forge 1.20.1不能依靠NeoForge/Fabric元数据自动发现该配置，不改变日常或发行JAR的运行参数。
显式DebugUi授权不可省略；原生Component完整内容/样式/null/前缀尾段与预算的10项断言在真实Loader的probe中执行，结果必须绑定本次PID、Target与时间，不将普通JUnit或旧文件冒充原生通过。
三种字段复用ui.text.set和UI Arm/版本守卫；Native输入增量为0，真实MCP修改/批次恢复和网页USER隔离均须各自产生样本。
恢复只检查文本，原生消息生命周期及富文本格式不保证回滚；动作栏可自然过期，USER不能因DEBUG仍有字段而看到过期文字。
固定结果为`<target>-ui-debug-hud.json`及`<target>-hud-content-probe.json`，不覆盖完整矩阵，probe不进入最终产品JAR。

```powershell
pwsh -NoProfile -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-debug-hud
```

ui-debug-scroll专项只执行标题、固定世界准备、创造/织布机/切石机/交易自绘卷轴及保存退出。
显式DebugUi授权不可省略；每类都必须有真实Native修改/恢复样本，不能以场景被过滤或未授权而记通过。
验证ui.scroll.set在OPERATE、UI Arm和新鲜Resource Version下修改原生本地滚动位置，拒绝错误值/旧版本，输入增量为0、不改变配方/交易选择或所持物品。
无可滚动范围时只允许0并拒绝移动，不能把该分支当成成功滚动；结果固定写入`<target>-ui-debug-scroll.json`，不覆盖完整矩阵。

```powershell
pwsh -NoProfile -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -DebugUi -Scenario ui-debug-scroll
```

world-selection专项从全新世界选择页确认固定世界未选中，再以nodeId语义double_click进入，读USER玩家可用、held为0，正常保存退出。
结果为`<target>-world-selection.json`，不覆盖全套；首次点击自身选中不是允许无条件更新目标身份、内容、位置或Screen的理由。
从仓库根目录运行：

```powershell
pwsh -NoProfile -File components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1 -Target 26.3-neoforge -Scenario world-selection
```

hud-vitals专项检查原生生命图标、小数伤害后的图标组合、护甲/饥饿、毒/凋零/吸收、氧气消耗、坐骑互斥及隐藏边界。
真实MCP与隔离网页在READ且无Lease下读取相同有界图标，不产生输入或公开原始内部数值；结果写入各Target的hud-vitals固定结果，不覆盖完整UI矩阵。

执行结果固定写入本项 result，绑定当次源码哈希和夹具哈希。
实际执行的断言全部通过，输入归零、Lease 释放，未执行界面明确列出，不宣称覆盖全部原版 UI。
sourceChangedDuringRun 必须为 false；源码改变时结果 FAIL，禁止用变更中的候选做验收。
截图不是自动判定输入；允许明确记录的接口坐标输入，不调用控件业务函数。
世界准备为诊断夹具证据，不冒充正常 gameplay；不调用正式 Persistent Write。
