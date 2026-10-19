# 里程碑验证故障调查

2026-10-16 归档调查：以下为原执行计划中已记录的故障与分析摘录，不是本次重新执行的测试。
保留各段失败、后续修正和验证范围，不以之后的成功覆盖早期失败。

## 问题

将执行计划中积累的故障分析移出当前计划，保留复现原因、处理方式和未确认的边界。

## 方法

按原段落边界转存，不改写源码、结果文件、提交绑定信息或验收结论。
每段标注其原上下文；时间、Target 与数量以该段为准，不能跨候选拼接。

## 发现

### 记录 1

上下文：2026-10-16 Jigsaw 配置增量。

首轮延迟读取旧 Operation 因既有十六项终态保留预算被清除而 FAIL；改为每项完成后立即检查，
未扩大 Runtime 预算或降低消费者断言。生成器新增独立配置范围及两项证明测试，包含十八种证据篡改拒绝。
一次 Gradle 构建与游戏启动并发导致共享 class 输出缺失，记 FAIL；停止并发后串行重编译及完整门禁通过。
重编译前后 core/safety/五端共九百项 class 的内容摘要一致，未用源码改动或删除用户数据绕过问题。
602 项产品 Java、51 项 Companion/网页、五端 build/check/verifyRelease、严格合规与关联 Native/GUI/Phase 8 静态检查通过。
五端 alpha JAR、清单和 Agent 手册已刷新；范围为 command-configuration、每端 12 项 Native/4 项 MCP、
fullNativeSmoke=false，不将专项或单端完整回归拼成五端领域验收。
串行重建后参考端另通过完整 38 项 Native 与 13 项真实 MCP，源码稳定、正常退出；其他四端不据此推定全套通过。
本次为开发 Runtime 专项，不代替完整领域、三部署、最终 JAR 独立安装或人工验收。
下一项为 Structure 的 UPDATE/SCAN 与 SAVE/LOAD 边界，随后 Test/不透明载荷及函数/调度；
不以安全拒绝冒充完整生成或管理型 UI 能力。三部署规划已纳入 M-09a 至 M-09f，仍为 DESIGN/PENDING。
两领域和完整 Showcase 仍 IN PROGRESS；不改变模式、权限、Lease、Arm、真人撤销或截图隔离。
正式 Persistent Write=0，不暂存、不提交、不推送。

### 记录 2

上下文：2026-10-16 命令配置增量。

初始测试未区分命令和先前输出文本框，且假定集成服有 stop、26.3 有旧 daytime 查询语法，均记 FAIL；
改为真实语义命令框、原生 reload 拒绝及跨端 gametime 查询，不放宽生产策略或服务器零包断言。
实际 Artifact 暴露 USER 投影遗漏配置类型，先记 FAIL；现补入显式字段投影，正负权限、取消与消费者回归通过。
参考端完整 38 项 Native 与 13 项真实 MCP、五端各五项 Core 展示已通过；
所有当前源码哈希复核匹配，五端 alpha JAR、清单和 Agent 手册由既有生成任务刷新。
alpha 仍采用 core-showcase 范围、fullNativeSmoke=false、MCP 专项数 0，
不把五端九项专项与单端完整套件拼成五端全套。旧 Core 证据曾被实际拒绝，拒绝前后既有清单和手册未变。
本次九项是开发 Runtime 专项，不代替完整领域、Peer、最终 JAR 独立安装或人工验收。
两领域及完整 Showcase 继续 IN PROGRESS；下一项是嵌入 NBT 的可执行载荷及其他管理型 GUI 边界，
再处理有界函数/调度，不能把未知或拒绝当作整个 M-07 完成。正式 Persistent Write=0，不暂存、不提交、不推送。

### 记录 3

上下文：2026-10-16 全量区块快照增量完成。

首轮探针 Mixin 包与入口重叠导致启动失败，记 FAIL；分离包后参考端实际通过。
Forge Mod 构造阶段登记配置未生效，记 FAIL 且正常退出；现采用标准验证启动 --mixin 早期登记。
探针 AP 的映射与发行重混淆输入已分离，未降低发行探针排除门禁；共享 MANIFEST 路径方案撤回并移入回收站。
1.21.1 探针缺少该版本必需的 Mod Loader 元数据而未被发现，首轮记 FAIL；仅修该端后八项通过，其余源码匹配的结果保留。
参考端完整 38 项 Native 与 13 项真实 MCP、五端各五项 Core 展示均通过；五端专项基准/恢复哈希相等，
所有源码匹配、sourceChangedDuringRun=false、实际最大堆 4 GiB、held input=0、正常退出和端口释放。
这些仍是开发 Runtime 范围，不把五端专项拼成五端完整套件，也不冒充 Peer、最终 JAR 安装或人工验收。
两领域及完整 Showcase 继续 IN PROGRESS；下一项回到 M-07 管理型 UI 最终提交及嵌入命令效果边界，
不再将已验证的整块快照缺口反复列为待修。正式 Persistent Write=0，不提交、不推送。

### 记录 4

上下文：2026-10-16 世界目标位置级生命周期增量完成。

早期专项把安全 release_all 的一次空清理计数误作玩家输入而失败，现按真实计数合同断言：
拒绝只允许一次空清理、held input=0、姿态不变，任何实际输入增加仍失败；未改变产品计数或放宽旧目标拒绝。
实际原版字节码另确认全量区块包可能原地重写同一 Chunk 且不经 setBlockState，
该批量快照分支仍缺独立守卫与真实证据，不由本次位置级修正或退出世界用例推定关闭。
两领域及完整 Showcase 继续 IN PROGRESS。下一项只补 M-05 全量区块包的原地替换屏障与配对回归，
保留本次非目标更新容忍和 A-B-A 拒绝，再继续 M-07 管理型 GUI 提交边界；不扩成另一个命令或存储系统。
不升级范围、不调用正式 Persistent Write、不暂存或发布。

### 记录 5

上下文：2026-10-16 原生命令消费者与夹具增量进行中。

真实 Artifact 暴露旧校验器只接受 PLAYTEST 污染事件的假设；该轮记 FAIL，
现严格要求修改命令保留 DEBUG_PRIVILEGED、GAME_ROUTED_RAW、未确认字段和恰好一个尝试/提交事件。
UI 全套另因测试把一次 Esc 当成已关闭聊天框而 FAIL；现仅对自己创建且前缀匹配的草稿执行有界原生 Esc，
逐次确认实际 Screen，验证仍为 TAKEOVER、无人工撤销且 held input=0，不让 helper 自动丢弃草稿。
修正后的五端各通过 UI 47 项、世界联合 38 项及 13 项真实 MCP 子检查，固定副本哈希、源码匹配并正常关闭。
十个自有实例均实际确认已停止；格式化后重新核对所有源码哈希，结果仍匹配。
当前已通过五端构建、测试、verifyRelease 与严格合规；五端 alpha、manifest 和 Agent 手册已更新为本候选 full Native 套件范围。
展示入口新增五个 Core 案例，参考端执行通过；随后 Forge 在线启动因依赖仓库 TLS 握手失败而未进入游戏，记 FAIL。
已加显式 Offline 启动选项，不改依赖与产品；最终脚本待从参考端重新进行有界 smoke，不把先前结果当新脚本证据。
离线参数拆分的启动前失败已修正，并用同一生产参数函数及真实 Gradle 任务确认完整 argv。
普通地形的 1.21.1 工作台 Act 出现 WORLD_TARGET_STALE，未忽略或重放；夹具恢复、客户端关闭和端口释放通过。
仅响应不能判定是状态、Chunk 实例还是全 Chunk revision 哪项失配；后者在代码中确为保守条件，作为 M-05 待调查限制。
展示新建夹具现改为 Vanilla GUI 的默认 Superflat，减少地形变量；旧专用数据原样保留，不以此关闭产品限制。
超平坦的 1.21.1 已通过工作台、文本和证据步骤，随后正式 Persistent Read 被实验 adapter 标记错误阻断，返回 404，整轮记 FAIL。
共享路由现只去掉正式存档读取的错误实验条件；其他实验路由、认证、storage.read 和只读安全边界保持。
真实 HTTP 回归覆盖普通/实验两种 service、缺独立 scope 与错误 token，未授权时 adapter 调用为 0；参考端构建通过。
这次共享入口修正使前述五端全套源码指纹不再匹配当前候选；保留其历史事实，不把旧 PASS 当本候选证明。
接下来先完成新的五端 Core 展示 smoke；alpha 生成器新增严格的 core-showcase 范围，不将此小集合写成全套 Native、MCP 或固定基准验收。
本增量只修改验证工具，不改变产品 API、权限、原生行为或视觉；两领域与完整 Showcase 仍 IN PROGRESS。
管理型 GUI 最终包边界、完整目录、Peer 与独立最终 JAR 安装证据仍待完成；正式 Persistent Write=0。

### 记录 6

上下文：2026-10-16 排队前置条件增量。

首轮参考端因此记 FAIL，脚本修正后重新执行，不拼接旧结果。
492 项产品 Java、48 项 Companion/网页、五端 build/check/verifyRelease、严格合规及 Native/GUI/Phase 8 静态检查通过。
专项另存固定结果，fullSmoke=false；alpha 的范围显式选择，不能把旧 37 项结果与专项拼成当前全套 PASS。
五端 alpha JAR、清单和 Agent 手册已更新并核对哈希，明确 nativeSmokeScope=input-preconditions、fullNativeSmoke=false；
默认全套生成已实际拒绝不匹配的旧结果，拒绝前后既有输出哈希不变。
本次未进入世界，未重跑全套世界/MCP Runtime、Peer、独立发行安装或人工验收。
源码抽查确认 pipeline 包装入口只统一检查 input；下一项以真实 HTTP 核对并对齐 UI/Wait/Assert typed step 的独立 scope，
再继续同种类/数量物品组件版本与 M-07 命令证据链，不把当前增量当作全部权限路径闭环。
这不是完整 compare-and-act 或前置条件全面闭环；两个里程碑及完整 Showcase 仍 IN PROGRESS，正式 Persistent Write 为 0。

### 记录 7

上下文：2026-10-15 世界固定输入副本增量。

首轮 Forge smoke 通过，Fabric 因中文实例而暴露 Runner 英文文案依赖，连退出也无法定位；
已按实际 Runtime 身份和翻译键正常关闭该唯一失败客户端，不模拟桌面输入。
原生按钮改用 translationKey，保留专用 Fixture 文字；新建/冷启动自有实例应用静音，不改已有语言和其他选项。
导航修复后 Fabric 又在 Arrange 前因原世界无四个空槽而安全拒绝，不清空未知物品；
新增显式 -FreshFixture 仅归档整个已停自有 client 到本 Target 的 retired，再建立空专用实例，原数据完整保留。
71 项工具检查通过，最终工具的 Forge/Fabric 有界 smoke 各自通过，sourceChangedDuringRun=false、
实际最大堆 4 GiB、held input=0、CLEAN_SHUTDOWN_PORT_RELEASED，不拼接旧工具结果。
Fabric 原副本完整留在本 Target 的 retired，最终 smoke 在新空副本中运行；不是中文完整流程或固定副本全矩阵证明。
五端 build/check/verifyRelease、474 项产品 Java、47 项 Companion/网页、严格合规及文档门禁通过，
生成结果保留 C-09 原文，Git 仅对该范围识别合法 CRLF，普通 diff --check 不豁免真实尾随空白。
未自动运行 All、真人输入或最终视觉验收；两个里程碑及完整 Showcase 均未 READY，Persistent Write 保持 0。

### 记录 8

上下文：2026-10-15 命令证据收口。

首轮 Forge 的新 Artifact 用例因首个状态样本尚未落盘而失败，修正为 Lease 内启动录制、等待 writtenStates>0、
先停止录制再释放 Lease；保持 Artifact 状态样本与污染 timeline 断言，按最终工具重新逐端执行，不拼接候选。
五端 build/check/verifyRelease、447 项产品 Java 与 47 项 Companion/网页测试通过，
Control/Showcase、Native/GUI 本地门禁与 Phase 8 hardening static 通过；严格合规失败 0、警告 0。
五端发行 JAR 各含一份共享命令回执类，无重复条目或新增测试类；alpha JAR、清单与 Agent 手册由工具更新。
本轮是开发 Runtime 自动回归，不是新的最终 JAR 安装、完整固定副本/Peer 或人工验收。
功能展示集合仍须按 M-11 完整准备；不启动整合包、其他 Phase 或 Extension。
所有测试客户端已关闭；保留工作树，不暂存、不提交、不推送，正式 Persistent Write 为 0。

### 记录 9

上下文：2026-10-14 窗口标题/图标改为原生最终输出装饰，标题文案不变，图标使用游戏原图加右下角圆球。。

本轮初次批测的 1.21.1 创造背包曾出现节点找不到；旧结果未捕获足够现场信息，不能独断单次失败因果。
复核确认 UI 身份表存在达到 2048 项即整表清空的缺陷，已五端替换为有界逐项回收，
当前访问节点保持稳定、旧编号不复用；新增跨边界实际页面导航与满表单元测试，不靠坐标或重试掩盖失效。
另修复 deadline 先发布超时、后向底层取消的时序窗口；五端测试阻住取消步骤，确认结果不会提前发布。
这保证取消调用的先后，不宣称任意第三方异步工作都已物理终止。
修复后重新恢复五端基线，批次 5d04d93096d84ae38e63a0a3eedc2660 全部通过；
每端实际跨越 2253–2344 个已分配身份，包含 1.21.1 创造卷轴失败位置的完整复测。
多行进度/配方提示均五端实测通过，Fabric 的两条只读消费者链也通过；源码未在运行中变化。
该结果不覆盖 System/Tutorial 独立场景、好友/音乐提示、旁观者菜单或完整网页人工验收。
编辑器增量把书本多行/追加/签名后重读、普通和悬挂告示牌四行乱序编辑/保存重开、
生存背包 2×2 合成纳入固定副本矩阵；这些是既有产品路径的更完整验收，不新增输入旁路。
悬挂告示牌夹具先修正重开站位：放置时的朝上命中方向不能代替牌面正面，
测试需避开已有工作台支撑并等待客户端姿态稳定，不放宽 Runtime 的目标或输入守卫。
参考端和 Fabric 的定向七项流程通过；Fabric 另通过书本、两种告示牌各一条真实 MCP 和网页输入链，
包含中文、多行、原生焦点追加、内容恢复和旧书页拒绝。此项不等于完整浏览器或人工验收。
本增量第一轮完整批次 c27d8edc9bf5478e895265278b23fe3e 受用户最小化窗口干扰：
三个旧版窗口报告 0×0 并进入暂停页，目标尺寸/页面守卫拒绝输入，整批记 FAIL。
用户确认并恢复窗口后，经正式接口关闭自有客户端；不得把其余两端通过拼成五端 PASS，需从固定副本整批重跑。
随后完整重跑批次 41e20e259a0141fdb9659540f9bc3241 五端均为 45/45，
1.x 两端实际到达 57 类 Screen、26.x 三端为 58 类；多一类来自新版独立书本签名页面。
各端源码运行中未变化，收尾 held input 为零、Lease 释放，五个自有客户端和 Runtime 端口全部退出。
Fabric 完整批次的六条编辑器 MCP/网页结果也通过；原用户网站未被重启或占用。
编辑器增量仅新增验证与说明，不修改产品输入、UI 外观、模式/权限或截图隔离合同；
正式 Persistent Write 调用仍为 0，首个里程碑仍为 IN PROGRESS，不提交、不推送。
2026-10-14 状态效果读取增量接入五端实际原生图标绘制参数，补齐临近结束时的闪烁读取。
1.x 观察真正绘制的 float alpha；26.x 观察绘制点的原生 alpha 并使用相同 ARGB 转换，不按剩余时长预测。
共享缓存最多 64 项，只有完成帧可读，绑定玩家、Screen 与 GUI 尺寸，250 ms 后失效；
USER 仅返回非透明有效图标，保持等级/时长和隐藏效果隔离，第三方替换绘制及所有视觉遮挡仍不保证。
初选的改参 Hook 被既有门禁拒绝后，改为纯观察 Inject；未放宽门禁、修改绘制参数或改变原版效果。
最终批次 e029beb26f49478f902a9697bcf4f406 五端全部 45/45，源码运行中未变化且与最终文件哈希一致。
每端采到 31 个短时效果样本，均含可见和全透明帧并验证过期；Fabric 另有两条真实 MCP/网页只读结果，
不申请输入 Lease、不改用户网站。五端构建、315 项产品 Java、62 项共享安全、38 项 Companion/网页测试、
协议生成、Control/结构/文档检查和 verifyRelease 通过；所有自有客户端与端口干净关闭。
该效果图标增量不证明其他 Toast、完整多人 HUD 或所有网页流程；统一人工验收仍未执行。
保留工作树，不提交、不推送；正式 Persistent Write 调用仍为 0，首个里程碑继续 IN PROGRESS。
旁观菜单增量随后完成五端原生适配及完整批次 6d932972f03246ca9a2d8359d39f1aa1，各 46/46。
五端在固定副本通过打开、选中/执行分离、重复点击零输入、旧节点拒绝、F1/DEBUG 隔离、READ 拒绝、
原生 Close 和五秒过期；恢复创造模式后继续完成原有全部流程，源码运行中未变化、输入收尾归零。
Fabric 另经真实 MCP stdio 和随机端口网页 HTTP 服务验证打开/选中/执行 Close，旧网页上下文被拒绝；
网页前端通过显示边界映射到语义快捷键，不开放第一人称坐标输入，旧截图不能作为新菜单点击依据。
本次 325 项 Target Java、63 项共享安全、40 项 Companion/网页测试及五端 build/check/verifyRelease 通过。
这不是多人传送/所有分类分页、所有键位类型、旁观专用 DEBUG mutation 或网页人类操作验收 PASS。
仍需补其他 HUD/Toast、剩余原版业务流程与独立夹具，最终网页统一验收尚未进行；不启动第二个里程碑。
此次并行由用户明确授权：五个独立 Runtime 端口、各自认证和副本；启动后 jcmd 实测最大堆均为 4 GiB，
五端同时就绪后才放行。批次正常退出、端口全部释放；不把 Java 堆上限当成堆外/GPU 总内存上限。
计分板分别遵循 1.20.1 的可见行裁剪/空标题行为与新版数字格式、队伍侧栏规则。
首轮并行的 Fabric 书本流程曾因未等待客户端所持物品就绪而超时；
测试补充接口条件等待后，重新恢复五端副本并全部通过，不改产品输入路径或固定 sleep 掩盖失败。
此前 UI DEBUG 增量的 Fabric 另通过 6 项真实 MCP 检查（含 UI Arm、隐藏/恢复、旧 revision 拒绝），
以及 6 项独立网页服务链检查（真实点击、文字光标、Shift/九格拖拽、滚动条、PNG 传输）；
临时网页端口独立，不占用原站点，截图内容不参与自动判断；本轮 HUD/窗口增量未重跑这两项消费者 Live，
它们不能作为本轮新增字段的消费者实测证据。Toast 增量另在 Fabric 用真实 MCP stdio 与随机端口网页服务
读取当前 RecipeToast，检查只读数据、阶段、无 DEBUG 字段且未取得 Lease，记录在 toastSamples；
此项不覆盖完整网页操作或浏览器视觉。Companion 本轮已通过新滑块 discriminator 的
成功与非法类型/范围自动检查及玩家列表只读传递测试，Native 五端分别实测，不混写成新的 MCP/网页真人验收。
逐端结果见[验证记录](../../validations/conformance-vanilla_ui/validation.md)。
网页仍是 USER 输入消费者，不因新的 UI Debug 能力自动获取调试权限；
尚未把全部原版内部操作、所有 HUD 或人工体验宣称完成。

### 记录 10

上下文：2026-10-16 13:53 恢复开发与推进方法调整。

2026-10-16 13:53 恢复开发与推进方法调整：以文件及进程核对中断现场，HEAD仍为f22f332c012779d592e03e9905a0b45f797d0b4e，
未暂存、未提交；未发现13:26之后versions/components非生成源码变动，无残留游戏/Gradle进程。
26.3 NeoForge与1.20.1 Forge的16案/6条MCP结果逐文件哈希匹配且正常关闭，可复用；
1.21.1中途结束不计通过，26.1.2与Fabric仍为旧12案；旧alpha清单/手册与13:26 JAR不一致，不作为当前交付。
已核对并将退出PID27920的endpoint移入回收站，保留其他实例数据；只补缺失三端，完成后重建alpha清单/手册。

### 记录 11

上下文：2026-10-16 14:24 广度推进第1个工具/验证增量。

参考端最初两次在边缘用例失败，记录到潜行期间仍停在y=80、z约3.293，测试同时松前进/潜行后原版惯性使玩家跌落；
第二次增加轨迹/终点诊断，核对原生maybeBackOffFromEdge依赖Shift状态。修正测试为先松前进、保持潜行500ms停稳再松潜行，
仍要求所有采样y=80且终点在平台边缘；不降低不能跌落的断言，没有为测试改动原版物理或Runtime。
新增工具语法、现有世界静态/固定副本/连接检查均通过；当前状态和后续工作只在status-milestones.md维护。
本增量不触发每三个增量或共享产品改动的完整矩阵刷新：alpha保留14:00配置专项快照，533个产品输入、五JAR及手册哈希复核一致；
旧配置结果的工具哈希不作为新移动工具版本的当前证据。新专项报告单独绑定当前工具，不拼接成全套PASS。
正式Persistent Write调用仍为0；无Computer Use、无暂存、无提交、无推送。

### 记录 12

上下文：2026-10-16 14:48 广度推进第2个增量。

首轮第二次打开马背包超时：已对齐的use可能早于按键被玩家移动tick采样；改为读取实际clientTick至少推进两次，
再执行相同原生use，参考端马/驴流程通过；没有增加固定sleep、直接开菜单、物品设值Act或降低结果断言。

### 记录 13

上下文：2026-10-16 14:58 本增量完整矩阵首轮在参考端已有movement TutorialToast未自然结束的前置等待失败，其他端未启动。。

2026-10-16 14:58 本增量完整矩阵首轮在参考端已有movement TutorialToast未自然结束的前置等待失败，其他端未启动。
当次四类新菜单及Crafter两态的真实MCP/临时网页读取均已实际通过，但整套仍为FAIL，不复用为完整通过。
固定基准options保留tutorialStep:movement；原生MovementTutorialStepInstance要求移动输入和转向各超过40个tick后才结束，
不能靠等待或把持续教学提示忽略掉。现新增真实跳跃输入与交替相对mouse.delta完成两部分，然后读取其自然隐藏和输入清零，
不改基准/options、不直接修改Tutorial、不跳过现有Toast断言。
参考端HUD专项于14:57实际12项通过，教学样本读取原生可见文字，记录106个client ticks、held keys/buttons=0。
14:58已在相同产品候选上用最终工具版本重启五端完整UI/世界/移动矩阵，目前参考端仍在运行；不能提前生成alpha或把队列说成执行完成。
613项产品Java与51项Companion/网页及五端构建、verifyRelease、严格合规已过；随后教学工具以参考端实跑验证，批次结束仍执行最终门禁。
状态页明确旧alpha/配置与移动专项均为前候选，不影响其历史通过事实；本增量成果不宣称两个领域READY。

## 2026-10-19：原生点击消费与松开后的队列

连续游玩候选的一次参考端重跑，三组相同40ms点击的输入回调均派发，但诊断只确认到2次原生实体攻击。
原始结果另存于仓库外Investigations/20261019-continuous-input/attack-after-mouseup/reference-under-counted.json，
SHA-256为3fd2a87873cc645db3faea1305393cfe3cd50095e9bdced4e3f6c1f5e6ea25fe；不改写或追认这次结果。
此结果的测量流程完成不等于三次实际消费通过，后续批跑中止，不以用户允许性能比值未达标为由放行计数不足。

核对真实Native字节码：mouseup只取消isDown，clickCount继续由后续handleKeybinds消费。
原计数要求Runtime仍按住mouse0，因此存在真实消费后漏记的条件；仅凭此源码分析不能追认故障样本实际发生了第三次攻击。
计数改为在既有routed消费上下文中观察真实attack RETURN，继续复验owner、原生epoch、接管代次及Lease。
它不是伤害、动画、服务器确认，也不声称排除了第三方在routed回调内直接调用原生方法的情况。

同一链还暴露了强清理的独立缺口：若只按held集合清理，会遗漏已up但仍pending的原生点击。
新增身份归属追踪只记录Runtime回调实际改变过的KeyMapping；强停止、取消、失权和epoch切换释放这些映射，
正常成功完成仅回收已消费归属，不抹掉已提交但尚待游戏消费的点击。10项纯Java正负例及真实Native release字节码验证覆盖该条件，
不将潜在窗口写成已经观察到的现场迟到攻击。

参考端修复后的三次独立实机复测，单次计数均为1/1/1、三次批次均为3，正常退出且源码未变。
原40ms、原版冷却和计时边界不变；终态后额外消费观察单列，不计入原pipeline或HTTP终态计时，缺失仍不确认。
三次比值2.60484、2.46793、2.42329均未达到1.5，按用户明确决定只允许带限制试用；这不是性能或完整里程碑PASS。
其余四端移植及共同候选验证的当前状态仍以状态页为准。

## 2026-10-19：区域读取的无进展预算拒绝

冻结产品候选7792367d的Forge区域专项，准备世界后的分页过程中返回明确的429 WORLD_REGION_BUDGET_NO_PROGRESS，
retryAfterMillis=50；未提交续读游标。原始FAIL正常退出，另存Investigations/20261019-continuous-input/region-cold-owner-budget/forge-first-read.json，
SHA-256为78d387d786f5ab617aeb674e6f3d6fa271c400c1fd9b21c85af78864b5c399f1，未覆盖成PASS。
该错误来自既有协作式8ms预算的无进展分支，不能只凭一次运行断定具体冷初始化或负载原因，也不能据此改大预算。

消费者此前无条件传播此错误，故停止矩阵。修订只针对read中明确未消费游标的该结构化拒绝，遵循合法retryAfterMillis并限制总尝试次数；
当前工具/发布器需记录和核对全部尝试，计入真实调用减少比值。其他错误、传输结果不确定、失效游标、取消、输入或修改不得据此重试。
共同矩阵重新绑定新工具后才可刷新alpha，当前验收状态见状态页。

## 2026-10-19：旧验证消费者的退出回执

参考端server-completion共同回归在七项通过后，Save & Quit返回409 REQUEST_CANCELLED，
executionOutcome=not_confirmed、retrySafe=false；客户端实际正常Stopping并释放发现记录。
原始FAIL另存Investigations/20261019-continuous-input/server-completion-epoch-exit/reference-first-run.json，
SHA-256为ec064b8d80e1dae8f671693254600aa3a393ed85c64030b529880f0f6e158e2d，不将正常关闭追认为成功点击回执。
旧消费者尚未复用独立退出观察器；该修订只允许明确的取消结构，并独立要求新epoch、标题无overlay、held=0及清理完成，
不重发点击、不吞其他409或传输错误。修订工具后的结果须重新绑定源码，原始失败保留。

## 2026-10-19：诊断维度准备与输入epoch

参考端完整回归在consumer维度往返的CHAT准备命令处返回409 REQUEST_CANCELLED，
executionOutcome=not_confirmed、retrySafe=false；服务器已执行维度切换，但旧输入管线在新epoch结束。
原始FAIL另存Investigations/20261019-continuous-input/consumer-dimension-epoch/reference-first-full.json，
SHA-256为1c2e77244d191815aaa4e2f7ce1749e4b0680d249d1a8d3c23a6414d296ac258。
首次清理Esc又因加载Screen更换而返回UI_TARGET_INVALIDATED；外层以新鲜状态重新导航，客户端随后正常Stopping。
没有把前两次失败请求写成成功，也没有重发维度命令。

维度变更本为诊断Arrange，不是玩家验收Act。修订该准备使用既有NATIVE原生命令入口，仍标未确认提交，
再分别确认USER维度、新epoch、页面就绪和输入清理；其他真实CHAT流程不改。
这不放松生产输入epoch守卫，也不宣称CHAT跨上下文请求已成功，后续共同回归须重新绑定工具。

## 2026-10-19：物理Esc与队列取消终态

候选7792367d的真人检查记录nativeRevocations从0变1、TAKEOVER退回READ、MANUALLY_REVOKED、
held keys/buttons为0且清理完成；但active Operation取消后，至少一项queued Operation先变为failed CONTROL_LEASE_REQUIRED。
原始FAIL保留于Investigations/20261019-continuous-input/manual-esc/first-queued-terminal-failure/manual-esc-report.json，
SHA-256为071b762b1a808f29b7cebb7ff76c351acf68c3dbb532b972adc50ab458aad7ae。
这不是完整撤销PASS，也没有在该失败样本上完成8秒无迟到判定；用户明确重新同意后，客户端已通过原生界面正常退出并移除发现记录。

根因是批量取消逐项调用Future.cancel，首项的同步drain回调会让输入队列启动后项；
后项Lease守卫正确拒绝输入，但Operation在遍历取消到它前已提交失败终态。
新增真实InputSequenceQueue的确定性测试在修复前失败；修复将批量操作先全部标记取消，再调用各Future，
关闭同样在清理回调前标记，完成/已失败操作不追改，独立非接管工作只在整个Runtime关闭时取消。
回调和terminal发布仍不持Operation锁；修复后专项通过，新的物理检查和五端共同候选仍须另跑。

## 2026-10-19：动态菜单夹具未形成实际重叠

候选f2dc15c9的26.1.2 NeoForge完整世界回归在动态菜单样本处失败；原始观测中，
otherCount于clientTick 7701从6增到7时指针仍在(312,77)，7711从7增到8时已经到达(168,202)。
指针7703～7709的移动区间内没有采到物品转移，因此不能证明“无关槽位变化与移动重叠”，断言正确拒绝计为通过。
这不证明生产目标校验失败，也不能用移动前后的更新代替所需重叠；没有放宽条件、修改节奏或补造样本。

原始FAIL正常关闭、源码/工具未变化、Persistent Write为0，另存
Investigations/20261019-continuous-input/dynamic-menu-overlap/26.1.2-first-full.json，
SHA-256为f9dbda2d94a1cc5be87c33ae9f62ae160458b754b3369f8fc75c15f5c645479b；失败矩阵检查点同步保留。
没有产品/工具改动；核对既有39份结果正文哈希与当前发布断言后，仅对未完成场景进行有界原样复测。
复测结果独立计证，不能将此原始缺覆盖样本追写为PASS。

## 2026-10-20：门放置预测与后续客户端回读不同

候选f2dc15c9的26.3 Fabric放置专项首次通过door_blocked，随后door_unblocked的逐属性比较失败：
原生useItemOn返回时的placementState.after为oak_door、hinge=right，稍后独立USER点读为同类型门、hinge=left，
其余记录属性相同。客户端预测和后续状态处于不同时间边界；回执本来就标effectConfirmed=false、serverValidationObserved=false，
不能把后续状态不同当作服务器确认，也不能把原回执追写为后续值。此样本不证明所有预测必与回读一致。

原始FAIL正常关闭、源码/工具未变化、Persistent Write为0，另存
Investigations/20261019-continuous-input/door-prediction-reconciliation/fabric-first-placement.json，
SHA-256为709943beff308c10b040d61ff3a5fb30f2103f523ffc7d15373eec4a6ef2f16a；失败检查点保留。
未删除hinge或放宽原断言；复核55份已完成结果后，只对该未完成场景进行有界同参数复测，另行计证。
具体服务器回包及量化误差成因尚未由此样本证明，不能仅凭差异确认产品故障或将一次复测推广为总是相等。

## 2026-10-20：放置消费者时间边界修正

同参数第二次Fabric复测仍在相同门铰链差异处失败，停止无改动重试。
第二份原始FAIL与检查点另存door-prediction-reconciliation/fabric-second-placement.json，
SHA-256为df97c10fc5c7aa076b0197e6ff5bbcd383594fbe49b1a47a0fafff25f5fd3cbc；正常退出、Persistent Write为0。
只读核对原生26.3的BlockHitResult传输将相对命中double写为float，门铰链以大于0.5判定，
与观察到的左右差异机制吻合，但失败样本没有独立保存完整actualHit，不宣称实际量化因果或服务器确认已证明。

确定性测试修正前复现同一错误断言，修正后通过：即时changed只比较native before/after，
两份完整状态分别按有限Vanilla夹具校验，仅门hinge允许合法左右；后续门/床成对状态必须一致，
受阻、消费未放置、缺属性、错朝向及虚构服务器确认仍拒绝。生产坐标、精度和回执时刻不变。
发布器复用同一完整验证，没有减少源码/工具绑定。旧47份世界PASS因工具改变失效并保留，
全部55份世界结果必须实际重跑；既有UI/独立安装只有实际哈希及完整发布断言仍匹配才能复用。
本条记录验证方法修正，不将尚未执行的新矩阵或旧FAIL追写为PASS。

## 结论

这些记录说明各次失败的处理与证据边界，不独立证明当前候选全部通过。
阶段结果见[阶段记录](../project/phase-1-core_delivery.md)，当前剩余任务见[状态页](../project/status-milestones.md)。
