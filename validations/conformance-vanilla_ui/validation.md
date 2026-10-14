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
pwsh -File components/validation_tools/conformance-vanilla_ui/task/Read-VanillaUiCatalog.ps1 -Target 26.3-neoforge
```

首次由工具经标准接口创建夹具并在正常退出后保存基线；以后只恢复该基线，不覆写它。
游戏操作、收尾、复制和结果均由工具执行；默认一次一个 Target。
用户明确允许并行批测时可用 All；五端分别使用 28591–28595、独立副本和 token。
每端最大堆为 4 GiB、初始堆为 1 GiB；启动后用该 Java 的 jcmd 核对实际最大堆，
五个客户端全部达到标题页后才同时放行。该限制不包含堆外/GPU 内存和 Gradle 进程。
All 不支持 KeepClient，不新建基线；先单端准备基线。批次结束检查所有自有客户端与端口释放。
端口覆盖仅用于该验证的 JavaExec 启动，不改 Runtime 或日常实例配置。
结果写入固定的 batch.json 和各 Target JSON；批测不是宿主输入/人工视觉验收。
批测期间保持窗口未最小化，不使用显示桌面或锁屏；无需让五个窗口同时获得焦点。
窗口缩为 0×0 或 Screen 被外部操作替换时，原有目标失效保护应拒绝输入，不能绕过守卫继续计为 PASS。
受干扰批次记为失败；恢复窗口后经接口正常退出，再从固定副本完整重跑，不拼接不同批次的检查结果。
DebugRead 仅为该测试子进程显式配置 debug、debug.ui.read，不授予 debug.write、Arm 或更多世界修改权限。
本矩阵需要 DebugUi：另外显式授予 debug.write、debug.ui，测试自己申请/解除仅 UI session 的 Arm。
不隐式增加其他 Debug namespace；READ 类别消费者仍可只用 DebugRead，不能借此执行 UI mutation。
测试验证 READ 两类、TAKEOVER 仅 USER、OPERATE 仅 DEBUG，并检查拒绝没有输入副作用。
连续经原生页面进入/退出语言列表，实际跨越超过 2048 个分配身份的范围，
核对未改变页面的按钮 nodeId 在反复读取、resolve 与真实点击之间稳定；不重试旧目标或改用坐标。
共享单元测试另外覆盖表满时当前整棵树、对象引用而非 equals、逐项淘汰及编号不复用。
五种 UI typed mutation 检查旧 revision、值前置条件、错误类型和边界；隐藏控件恢复，编辑内容通过 Cancel 丢弃。
滑块通过独立 UI Arm 修改原生灵敏度设置，核对文案、诊断证据、零输入与旧版本拒绝，最终恢复原值。
HUD 标题/副标题/动作栏与真实 Advancement Toast 的文本只通过接口判断，非截图验收。
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
地址使用 Runtime 端口加 100，启动前拒绝已占用端口；原版会尝试本机状态探测，测试不点击连接。
这些正常 GUI 操作只修改可丢弃实例的服务器列表，不连接第三方服务、不证明联机或认证成功。
自绘卷轴新增创造背包上下拖动/搜索禁用/标签隐藏、织布机图案、切石机配方、12 条固定交易的
语义拖动与滚动后重新选择，拒绝已滚出视口的旧目标。1.20.1 石头配方不足 13 项，仅检查禁用
卷轴与无动作；其余四端用凝灰岩检查启用拖动，不将禁用分支冒充滚动 PASS。
较长矩阵在无 held input 的案例边界释放并新建有界任务，保留 Runtime 原有 300 秒任务上限；
人工撤销会拒绝继续，不用延长产品权限预算或清除撤销状态来完成测试。
编辑器增量验证多行/中文/追加文字、书本翻页后旧目标拒绝、签名后从服务器返回的只读内容，
普通与悬挂告示牌四行乱序编辑、保存后重开及旧编辑器拒绝，并通过生存背包的语义槽位完成 2×2 合成取物。
挂牌重开使用无遮挡的正面站位，等待客户端位置稳定；不把放置时朝上的命中方向当作重开牌面。
场景准备使用既有玩家命令且只作用于本次副本，编辑、保存与合成走原生 UI，不直接改书本或菜单内容。
Fabric 另在当前副本交接控制权给真实 MCP stdio 和随机端口网页服务，验证两种告示牌和书本输入、
网页原生焦点追加与旧书页拒绝；每次交接释放原任务/Lease，结束输入归零，原用户网站不参与。
editorSamples 记录六条消费者结果，相关工具和界面目录纳入源码哈希。
Scenario editors 只执行七项编辑器相关流程，结果单独写入 `<target>-editors.json`；
不能用于 All 批次，也不会覆盖完整 `<target>.json`，不能将定向通过冒充全矩阵通过。
Scenario hud 同样只执行十二项定向流程，结果为 `<target>-hud.json`，不能用于 All 或覆盖完整结果。
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

执行结果固定写入本项 result，绑定当次源码哈希和夹具哈希。
实际执行的断言全部通过，输入归零、Lease 释放，未执行界面明确列出，不宣称覆盖全部原版 UI。
sourceChangedDuringRun 必须为 false；源码改变时结果 FAIL，禁止用变更中的候选做验收。
截图不是自动判定输入；允许明确记录的接口坐标输入，不调用控件业务函数。
世界准备为诊断夹具证据，不冒充正常 gameplay；不调用正式 Persistent Write。
