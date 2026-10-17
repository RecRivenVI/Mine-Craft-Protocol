# Core 实现与里程碑阶段记录

历史阶段记录，归档日期为 2026-10-16。本文保存原执行计划中的实现、验证与交接快照，按原记录顺序保留。
各段“当前”“下一步”、数量、哈希、路径与未完成项只对该段候选成立，不是现行推进安排或新验收。
现行状态只看[状态页](status-milestones.md)，当前任务只看[执行计划](plan-execution.md)。

## 目标

保留 Core 与两个领域首个里程碑已推进阶段的结果及其证据边界，不提升任何原验收结论。

## 范围

从执行计划转存的阶段记录；绑定提交的 JSON、ZIP、历史路径与哈希不作修改。
长期设计摘录归入[设计调查](../research/survey-core_planning.md)，故障分析归入[验证调查](../research/incident-milestone_validation.md)。

## 步骤

1. 完成：按原章节与段落边界转存实施、构建、运行和交接记录，保留不同候选与未执行项的区分。
2. 完成：将调查分析单独归档，并在原位置保留对应链接。
3. 进行中：两个领域的完整里程碑及人工验收仍按现行计划推进；本文不宣称整体 READY 或 PASS。

## 结果

以下是历史快照，不因归档成为当前制品或当前源码的证明。

### 当前交付与验收

2026-10-16 Jigsaw 配置增量：五端最终 Connection.send 守卫识别最终方块状态配置和生成请求，
配置使用当前玩家原生命令树/注册表解析及既有嵌入程序预算；空状态不当作清空命令。
保存始终是配置修改，configurationPayloadType=BLOCK_STATE，与命令文本配置分开；
配置来源、实际效果、诊断污染及未确认状态保留到 Operation、USER Timeline 和真实 MCP。
Generate 的模板效果未决，继续拒绝生成包；原生按钮先保存配置，故准确返回部分失败及已提交配置，
不谎称零 mutation 或已回滚。只读服务器探针分别计数配置/生成包并核对最终状态哈希，不修改世界或加载区块。
五端最终冻结候选各十二项专项、四项真实 MCP 通过；固定基准/恢复哈希相等、源码稳定、最大堆 4 GiB，
正常退出并释放发现记录及端口。Artifact 精确包含六次配置尝试/提交，MCP 在 READ 下无 Lease 读取实际结果。
[本段调查见记录 1](../research/incident-milestone_validation.md#记录-1)。

以下为嵌入载荷及更早候选的记录，不替代当前 Jigsaw 候选证据。

2026-10-16 原生命令载荷增量：共享预算/字段及效果聚合在 runtime_core，五端分别提取原生 Tag、
BlockInput、ItemInput、NbtPath；Command 和 Jigsaw final_state 使用同一玩家命令树/注册表递归分析。
1.20.1 仅取旧物品的 BlockEntityTag/EntityTag，现代组件用原生 RegistryOps 编码；不读取服务器对象、库存或磁盘。
普通名称与惰性 custom_data 不当作命令；from/string 来源不明时明确未决，不能用安全拒绝冒充完整间接命令能力。
发送前重查输入许可/控制版本，空解析不能假扮清空配置；新增确定性测试含分析中失去模式、递归及载荷预算。
595 项产品 Java、51 项 Companion/网页、五端 build/check/verifyRelease 与严格合规，以及关联 Native/GUI/Phase 8 静态检查通过。
最终冻结候选五端每端十一项实际专项通过，含八种载荷前置拒绝、原生聊天 Enter 拒绝、
安全配置与标量清空的独立服务器读回、真实 MCP、Lease loss 后 held input=0 和无晚到输入。
拒绝批次的服务器计数只增加其 FIFO 屏障本身；helper 拒绝还要求零玩家输入，不把原始文字编辑算成零输入。
每端基准/恢复哈希相等、源码稳定、最大堆 4 GiB，均正常退出并释放端点/端口。
参考端先实际通过再移植，最终工具/格式冻结后重新执行，不用初始参考结果拼接当前矩阵。
参考端另通过完整 38 项 Native 与 13 项真实 MCP 回归；五端 alpha JAR、清单和 Agent 手册已由既有任务刷新。
alpha 范围为 player-command、每端 11 项 Native/1 项 MCP、fullNativeSmoke=false；
不将单端完整套件拼成五端全套。旧 Core 证据已实际拒绝，拒绝前后清单和手册未变。
本次专项不是完整领域、Peer、最终 JAR 独立安装或人工验收，三部署设计仍为 DESIGN/PENDING。
命令字段仍保守视为潜在程序，不保证所有原版数据携带位置已穷尽或所有世界连锁已静态证明；
后续须补齐不透明资源/数据复制、函数/调度和其余管理型 UI，M-07 及两领域仍 IN PROGRESS。
下一项先补 Jigsaw/Structure 等管理型 UI 的真实提交目录与边界，再处理需要服务器事实的有界效果解析；
不把当前对潜在程序字段的保守检查当成完整上下文或全世界静态证明。
不改变模式/权限/Lease/Arm/真人撤销/视觉或截图隔离；正式 Persistent Write=0，不暂存、不提交、不推送。

2026-10-16 三部署设计已纳入两个首个里程碑：仅客户端、仅服务端、双端联动均为必需部署维度，
能力/权限及有限实施顺序统一在[部署设计](../design/concept-deployment_scenarios.md)，不另建重复里程碑。
客户端 OP 不等于服务器内部通道；本地 UI 诊断、玩家命令和服务端 DEBUG 分别授权。
服务端建议以原生控制台为操作员 USER 表面，零玩家也能独立发现/连接；不虚构玩家、图形 UI 或 OS 输入。
联动取双方授权、实际主体权限和资源策略交集，服务器必须验证委托/Arm/版本，不信客户端自报。
源码复核五端 HTTP 均由客户端 tick 启动，现有 Peer 依赖在线玩家，服务端 mutation 只有开关/operator 基础检查；
独立服务器 Runtime、完整服务端授权与可选安装发布事实仍未完成。当前 mod_side=both，模板表达缺口明确保留。
本轮只加入规划与各领域验收要求，不修改运行端、协议、权限或产品行为，不新运行游戏/人工验收。
命令配置的五端九项、参考端完整套件与五端 Core 展示仍只证明下述已绑定候选；alpha JAR 源码未改变。
嵌入 NBT 的实际载荷分类调查尚未形成实现/验收，不把该调查或设计算作 M-07 关闭。
本次文档增量的 spotlessApply、check、verifyRelease、verifyCompliance --strict 和 diff 检查通过，
文档检查覆盖 76 篇；这是文档/当前候选回归，不是三部署 Runtime 或新增五端 Live PASS。
下一增量继续 M-07 嵌入载荷，M-09 按合规发布/身份 → 独立入口 → 联动 → 两领域矩阵推进。
两领域与完整 Showcase 仍 IN PROGRESS；正式 Persistent Write=0，不暂存、不提交、不推送。

2026-10-16 命令配置增量：五端最终 Connection.send 守卫识别原生命令方块与命令方块矿车配置，
复用 command/control/input、TAKEOVER、现有 Lease、当前连接及有界输入许可，不新增路由或直接发包捷径。
配置命令复用玩家实际原生命令树；清空不执行命令，保存查询也始终是配置修改，
configurationKind/configuredCommandEffect 与独立来源标记进入 Operation、MCP 和 USER Timeline 投影。
诊断污染不因回调结束消失，原始命令和内部对象不进入命令来源投影，不把配置保存冒充执行成功或干净玩法。
573 项产品 Java、51 项 Companion/网页、五端 build/check/verifyRelease 与严格合规已通过。
五端每端九项实际专项通过，含两类管理/未决载荷零服务器配置包、查询/清空的原生存值、
Artifact 恰好四次配置尝试/提交、READ 下无 Lease 的真实 MCP get/wait；基准与恢复哈希相等，
源码稳定、最大堆 4 GiB、held input=0，正常退出及端点/端口释放。
[本段调查见记录 2](../research/incident-milestone_validation.md#记录-2)。

以下为批量快照候选及更早结果，不替代命令配置候选的当前矩阵。

2026-10-16 全量区块快照增量完成：五端在原生 replaceWithPacketData 的 HEAD 标记整 Chunk 变更，
与位置级历史共用 4096 项预算，不使用可能与合法坐标碰撞的 sentinel；解码中断不复活旧引用。
共享测试覆盖整块/其他 Chunk、相同数据、异常、淘汰与序号耗尽；各端测试调用真实生产回调并核对实际原生方法和注入点。
559 项产品 Java、50 项 Companion/网页、五端 build/check/verifyRelease 与严格合规通过。
五端各八项专项均实际通过，包含新增原生重发场景：同一已缓存 Chunk、相同目标状态、零逐方块更新，
旧引用在 TTL 内被拒绝、只有一次空清理记账且姿态不变，新引用正常；探针未直接调用守卫或改世界。
Arrange 明确为 FIXTURE，Agent 触发和行为断言仍走正式接口，不当作干净 gameplay acceptance。
[本段调查见记录 3](../research/incident-milestone_validation.md#记录-3)。

以下为位置级候选与更早增量的记录，不证明新批量快照候选的全套矩阵。

2026-10-16 世界目标位置级生命周期增量完成：固定副本确定性复现同 Chunk 非目标更新导致旧引用误失效，
原目标石头未变，仅相邻位置由泥土变为圆石，305 ms 内原引用被拒绝，不是 TTL 过期。
现改为最多 4096 条弱引用位置历史；同位置 A-B-A、世界/玩家/相机/Chunk 实例变化、状态失配、
历史淘汰及序号耗尽仍 fail closed。原生挖掘的整 Chunk 变更计数和连锁破坏跟踪独立保留。
参考端先实际通过再逐端移植；同一冻结候选的五端专项各七项全部通过，固定基准哈希、源码和 4 GiB 最大堆匹配，
sourceChangedDuringRun=false、正常退出及端口释放。参考端完整 38 项联合 Native 和 13 项真实 MCP 通过，
不把其他四端的专项写成最新全套矩阵。五端各五项 Core 展示复验全部通过，源码匹配、held input=0、
正常保存退出和端口释放；包含三域 PERSISTED / last_saved_state、真实 ZIP 校验及取消后无晚到输入。
541 项产品 Java、50 项 Companion/网页、五端 build/check/verifyRelease 与严格合规已通过。
Native/标准 GUI/Phase 8 关联静态门禁通过；五端 alpha JAR、清单和 Agent 手册已更新，
运行范围仍为 core-showcase、fullNativeSmoke=false，不将七项专项或单端全套拼成五端完整验收。
[本段调查见记录 4](../research/incident-milestone_validation.md#记录-4)。

以下为前一增量的结果及过程，不能替代本次位置级候选的最终证据。

2026-10-16 Core 展示与正式只读路由增量完成：现有入口扩为十八项，支持五端、单例/All、停留、保留客户端及显式 Offline。
五端均实际通过新增五项 Core smoke：USER/DEBUG 读取、typed Debug 布置与真实工作台交互、语义文本草稿、Wait/Assert、
录制/截图/Artifact 成员与哈希、保存后 world/player/chunk 持久化读取、实际输入取消及无晚到输入。
每端最大堆 4 GiB、held input=0、源码哈希匹配且 sourceChangedDuringRun=false，夹具恢复、客户端退出和端口释放通过。
发现并修复正式 Persistent Read 被实验 adapter 标记误限的共享路由，真实 HTTP 证明独立 storage.read 及错误认证/缺 scope 时零分派，其他实验路由仍封闭。
535 项产品 Java、50 项 Companion/网页、76+51 项 Showcase 工具、11 项夹具/alpha 范围测试、五端 build/check/verifyRelease 与严格合规通过。
alpha 采用 core-showcase 范围，只记录五项开发 Runtime、fullNativeSmoke=false、MCP 专项数 0；旧源码结果被实际拒绝且拒绝前后清单不变。
不把此前 47/38/13 全套当本候选证明，也不把超平坦演示当固定基准或独立最终制品验收。
普通地形触发的过期目标仍保留为 M-05 限制；下一项用同区块非目标更新与 A-B-A 配对用例定位，保留过期/卸载/替换拒绝，不去掉前置条件迁就展示。
两领域及完整 Showcase 仍 IN PROGRESS；管理型 GUI 命令边界、完整目录、Peer、最终 JAR 独立运行和人工验收仍待收口。
正式 Persistent Write=0；所有自有测试客户端关闭，不暂存、不提交、不推送。

以下为本增量的过程与前候选记录，当前结果以以上范围和对应源码指纹为准。

2026-10-16 原生命令消费者与夹具增量进行中：村民的完整交易目录改用逐条原版 summon/data 命令准备，
每条含斜杠不超过原生 256 UTF-16 上限，保留商品、价格和使用次数，不走内部写入或长命令回退。
五端格式/长度及不安全参数拒绝测试通过，参考端实际支付一颗绿宝石并取得石头通过。
[本段调查见记录 5](../research/incident-milestone_validation.md#记录-5)。

2026-10-16 原生 Chat helper 增量：取消直接 sendCommand 回退，按当前原生 Chat 绑定打开、
逐字符输入并以 Enter 提交；只把实际提交标为 GAME_ROUTED_RAW，不当作服务器确认或 gameplay acceptance。
command/control/input、TAKEOVER、现有 Lease 仍独立校验；修改命令保留诊断污染，不授予 OP 或 Arm。
原生 256 UTF-16 上限含斜杠；已有 Screen/草稿、持有输入及未处理的 Chat 键队列在输入前拒绝。
每阶段绑定世界/玩家/连接/输入框与预期草稿，提交许可绑定命令且只在 Enter 阶段有效；
取消清理自身原生 Chat 键队列与 held input，不擦除/提交草稿，也不把其他输入映射全部清空。
先在参考端实跑确认，再移植；最终候选五端各通过八项联合专项检查，其中一项使用真实 MCP。
分别恢复本端不可覆盖基准，输入途中释放 Lease 后 held keys/buttons=0、无晚到输入；
sourceChangedDuringRun=false、源码匹配，正常关闭、发现记录及端口释放，五个自有实例均确认已停止。
534 项产品 Java、50 项 Companion/网页、五端 build/check/verifyRelease、严格合规及关联 Native/GUI/Phase 8 静态门禁通过。
五端 alpha、manifest 与 Agent 手册已更新，nativeSmokeScope=player-command、fullNativeSmoke=false。
旧全套证据已由生成器实际拒绝，拒绝前后既有清单不变；不把专项拼成全套 PASS。
这不是完整 UI/世界矩阵、Peer、独立制品安装或人工验收；两个领域和完整 Showcase 仍 IN PROGRESS。
下一项为管理型 GUI 最终提交边界及长夹具消费者适配，不恢复隐藏发包以迁就测试。
正式 Persistent Write=0；不暂存、不提交、不推送。

以下为先前候选的增量记录，不证明本次原生聊天候选的全套矩阵。

2026-10-16 命令终态来源与 UI 夹具增量：失败、超时、取消及 Lease loss 不再丢失已知命令来源，
无完整结果时只返回 partial provenance，不公开任意失败步骤数据。来源完整标记不等于服务器确认、效果成功或 held input=0。
终态事件在 Operation 状态提交后发布，回调不持有 Operation monitor 等待控制锁；HTTP/WS 与真实 MCP 原样保留字段。
前候选五端各通过 38 项 Native 与 13 项 MCP，四种终态场景均 held input=0、清理后无尾部输入、正常关闭并释放端口。

UI 工具的告示牌/进度服务器同步现显式退出接管，以 READ/DEBUG 读取，只给该测试进程增加只读 scope，
记录类别、无 Lease 和实际 tick 推进，重接管前检查人工撤销 latch。参考端现有 47 项 UI 检查已通过，三个屏障均实际执行。
随后 Forge 全套在 title subtitle 的有效 JSON 上记 FAIL，未重试或放宽断言；客户端正常关闭。
真实 ComponentArgument 单元反例定位到原生 reader 越过 EOF 后 getRemaining 自身越界；
五端改为与 Brigadier execute 相同的 canRead 结束规则，不改写命令，不放宽分类、权限或未解析后缀。
有效 JSON、损坏 JSON、尾随文本及五端原生结束语义测试通过；参考端 HUD/Toast 实跑通过后才移植其余端。

526 项产品 Java、50 项 Companion/网页、五端 build/check/verifyRelease 与严格合规通过。
EOF 修复后的五端现有 UI 套件各 47 项通过，三个夹具屏障均实际记录 READ/DEBUG、无 Lease 和 tick 推进，
源码匹配、sourceChangedDuringRun=false；十个自有 UI/世界实例目录实际确认已停止后才启动世界复验。
世界联合矩阵在同一最终候选上五端各通过 38 项 Native 与 13 项真实 MCP，四种终态的来源、污染和未确认标记保持，
held keys/buttons=0、清理后无晚到输入，基准/恢复哈希一致，源码匹配、sourceChangedDuringRun=false、cleanShutdown=true。
十个自有 UI/世界实例目录均已确认停止，端点及端口释放，无本批 crash report；前候选不拼入新结果。
五端 alpha JAR、源码/制品清单与 Agent 手册已按当前结果重新生成；fullNativeSmoke 仍只指本验证脚本。
UI 套件也不等于完整原版目录，开发 Runtime 和 JAR 结构不冒充独立制品安装或人工验收。
完整原版 UI/HUD/Toast、其余世界行为/typed DEBUG 目录、Peer、独立最终 JAR 安装和完整 Showcase 仍 IN PROGRESS。
下一项回到 M-07 原生 helper 与管理型 UI 最终提交边界，不重复已匹配的矩阵，除非相关实现改变。
不改模式、权限、Lease、Arm、真人 Esc、视觉或截图隔离；正式 Persistent Write=0，不暂存、不提交、不推送。

以下为此前增量记录；下一工作以上方有限清单和最新记录为准，旧批次不证明新候选。

2026-10-16 UI 物品数据与原生拖动增量：确定性反例证明原先仅比较种类/数量与普通 hash 无法保护组件变化，
现各端用原生客户端物品编码生成密钥指纹，菜单版本纳入物品数据。USER 只收到上下文内容令牌及可用标记，
不包含原始 NBT/组件或私有跨槽位指纹；内容令牌不是单调 Resource Revision，严格跨请求 pin 仍用 menuRevision。
原生编码不可用或超预算时物品手势明确失败，不降成无守卫坐标点击；预算与契约见[Native V0](../reference/protocol-native_v0.md)。
拖动不再笼统忽略槽位/所持物品变化，只在原生鼠标回调结束、同一客户端线程尚未让出时推进自身守卫，
下一输入前仍拒绝外部更新，Screen、菜单身份与几何不被重置。耗时校验后再次检查输入许可，取消不能变成晚到输入。

参考端先实际验证，再适配四端。最终候选五端分别从不可覆盖基准恢复，通过 37 项 Native 联合场景和 9 项真实 MCP；
铁砧实际改名保持种类、数量、菜单与槽位身份不变，内容令牌改变且菜单版本增加，取出后重开确认名称。
新增 pointer 专项复用现有 UI 工具与固定副本，不新增运行框架：每端实际通过七项，包含窗口模式/标题/图标、
语言列表和创造物品栏原生滚动条、语义 ui.drag 九槽分配与暂停/退出。64 个石头真实分成九格各 7 个，余 1 个另存，
held keys/buttons=0。物品由诊断命令准备，Act 为普通玩家输入，不将 Arrange 或自动检查当人工视觉验收。
UI 结果加入 runtime_core 与 Target/build facts 的源码绑定，旧钩子数量预期按当前实际站点修正，
不把聚合 Hook 描述行数误当注入站点数。两组运行源码均未改变，十次运行全部一次一个串行完成；自有实例均已停止，端点及端口释放。

509 项产品 Java、49 项 Companion/网页、五端 build/check/verifyRelease、严格合规与 Native/GUI/Phase 8 关键静态检查通过。
五端 alpha JAR、源码/制品清单与 Agent 手册已更新；fullNativeSmoke 只指本验证脚本，不指完整领域、Peer 或最终 JAR 安装验收。
本批没有完整原版 UI/HUD/Toast 目录、Dedicated Peer、最终发行 JAR 独立安装或新人工验收证据。
源码另发现 UI 全套工具的少数 Fixture helper 仍以默认 USER 请求服务端数据，须显式分类并按模式处理，不能放宽 Runtime 来迁就测试。
下一项继续 M-07 失败/取消命令证据链，并收口上述消费者；两个领域与完整 Showcase 仍 IN PROGRESS。
未修改权限、Lease、Arm、真人 Esc 或视觉合同；正式 Persistent Write=0，不暂存、不提交、不推送。

2026-10-16 pipeline 权限与 UI 条件增量：真实 HTTP 反例证明缺 ui/read 时可借 pipeline 执行，
普通条件入口也可绕过 ui，且 DEBUG UI 条件被当成 USER。现全部步骤在入队/声明手势前检查独立权限，
每步执行前重查；UiDataView 类别门禁集中在 ProtocolState，条件使用对应原生 USER/DEBUG 投影并在发布前复验。
事件条件需要 event，世界条件沿用既有独立授权；缺权限/类别不符的计划不能先执行前面的玩家输入。
原始输入不因缺 ui 被额外禁止，Legacy wait/screen 仅等待 Session 元数据，仍用 read，不机械提升权限要求。
未知/内部步骤在任何输入前失败；操作描述返回与执行同源的 15 类步骤目录及附加 scope，OpenAPI 补齐可选字段。
18 项真实 HTTP 回归包含异步模式变化/取消、正确 DEBUG 投影、受限 scope 拒绝、正常原始输入和能力数据。
首次参考端 Live 又发现 Companion Zod 丢弃 UI 条件 category，导致本应拒绝的计划被降成 USER 并执行；
该次记 FAIL 且正常收尾。修正四类条件 schema 并补真实 stdio 透传测试后，最终候选五端全部重新执行，
各通过 7 项 Native 和 2 项真实 Companion 专项；不拼接失败候选的通过项。每端当前源码/基准匹配、held input=0，
终止后无晚到输入，正常关闭并释放端口、发现记录与自有 Companion。专项 consumerScope=ui_condition_contract，fullSmoke=false。
501 项产品 Java、49 项 Companion/网页、五端 build/check/verifyRelease、严格合规、Native/GUI/Phase 8 静态检查通过。
本次未进入世界，未重跑完整世界/Peer/独立发行安装或人工验收；全套旧结果不证明本候选。
下一项为 M-02 同种类/数量物品组件变化，再继续 M-07 失败/取消命令证据链；两领域和完整 Showcase 仍 IN PROGRESS。
不改变模式、权限授予、Lease、Arm、真人 Esc 或视觉设计；正式 Persistent Write 为 0，不暂存、不提交、不推送。

2026-10-16 排队前置条件增量：真实 HTTP 反例证明初检通过、等待期间状态变化后，旧 UI 请求仍可返回成功，
pipeline 也未消费版本头。现 UI/原始输入在队列轮到自己时按调用方原值由 native owner thread 复验；
pipeline 加入同样的可选条件与正式描述，自己的后续切页按步骤守卫执行，不把初始状态永久固定。
前置读取属于可取消子工作；失败前未声明手势，不错误释放上一条合法原始流。
新测试发现清理取消会把前置读取超时覆盖成 cancelled，已修回调终止竞争并复验。
九项真实 HTTP 测试覆盖菜单/Screen 变化、正常多步、权限顺序、排队取消、超时和 Lease loss，
以及初检/队列复验后到首个原生输入之间的变化；原始按键/相对鼠标没有 GUI guard 也必须拒绝。
原值随过期输入许可传入原生 owner thread，同步刷新并复验后才进入首次输入；只读进度不消耗条件。
后续自身换页不永久固定初始版本；已完成/取消的 effect 不能重新使用该许可。
收尾反例发现首个原生条件拒绝仍会触发通用清理，已区分这种尚未输入的失败，不能误释放先前合法原始流；
已经分发的输入与普通取消仍保留原有释放规则，不用跳过清理掩盖残留。
MCP UI/pipeline 显式参数转成 Native 头，不放入 body，不自动读取新版本替换条件；新增真实 stdio 消费者测试。
五端各五项标题页/Options 专项检查通过，源码/基准匹配、held input=0、无晚到输入、正常退出并释放端口和发现记录；
包含有效原始条件下正常两步切页，不只测试全部拒绝。参考端先运行后逐端移植，最终候选五端分别重跑。
Quit 的回复可能随 Runtime 正常关闭被取消，脚本保留该状态，仅由外层实际退出/端口清理完成后确认干净关闭；
[本段调查见记录 6](../research/incident-milestone_validation.md#记录-6)。

2026-10-16 动态菜单目标守卫增量：默认语义手势逐次绑定实际目标、菜单/Screen 身份、几何与鼠标所持物品，
不再因无关槽位更新整体失效；目标物品种类/数量、身份或可操作状态变化仍拒绝。
原始坐标在等待与移动阶段保持全局菜单守卫；键盘等待不绑定无关槽位，显式全局版本仍严格。
先增加反例测试复现问题，复核又发现坐标等待边界，已停止该次自有运行、正常关闭后修正并重新验证；
不将中止批次或先前参考端结果拼入最终通过。
21 项指针测试覆盖等待/移动中的更新、所持物品/菜单替换、目标变化、显式版本、坐标和拖动自身拾取。
最终候选五端分别从不可覆盖基准恢复，通过 37 项 Native 与 9 项 MCP 子检查；
原生漏斗数量更新与指针移动并发采样重叠，稳定槽位实际转移八个泥土，变化目标和显式全局版本被拒绝。
每端 sourceChangedDuringRun=false、当前源码匹配、基准/恢复哈希一致、held input=0，正常退出且端口/发现记录释放。
五端 build/check/verifyRelease、483 项产品 Java、47 项 Companion/网页及关联 Native/GUI/Phase 8 静态门禁通过；
不把开发 Runtime 结果或 JAR 结构检查当作独立发行安装与完整领域验收。
五端 alpha JAR、清单与 Agent 手册已由生成工具绑定本批当前源码和独立运行结果更新；不是正式发布。
同 ID/数量物品组件变化、显式前置条件经过排队的精确值复验、剩余原版目录和完整 Peer/Showcase 仍待继续。
两个里程碑与完整展示集合仍 IN PROGRESS，正式 Persistent Write 为 0；保留工作树，不暂存、不提交、不推送。

2026-10-15 世界固定输入副本增量：只捕获干净 PASS 的自有专用世界，按 Target/client/facts 绑定不可覆盖基准。
每轮启动前确认 Runtime/session 锁、校验完整文件集合与哈希，再将旧工作副本移入回收站并恢复；
不能回收则保留到 retired，绝不永久删除或处理日常存档。凭据、发现记录和日志不进入基准。
bootstrap 准备与正式恢复运行分开记录，结果包含 baselineSha256/restoredSha256 和恢复时间，alpha 工具传递该依据。
10 项工具检查覆盖连续恢复、篡改/额外文件/路径、错误 Target/facts、OS 锁和凭据排除；
五端新候选分别从其独立基准实际通过 36 项联合检查与 9 项 MCP 子检查，基准/恢复哈希一致，
sourceChangedDuringRun=false、cleanShutdown=true；不把上一轮复用世界的结果算作固定副本通过。
五端 alpha、清单与 Agent 手册已按新证据生成，仍是开发 Runtime，不是最终 JAR 独立安装或完整领域验收。
固定副本不冻结原版随机性；仍保留有界条件等待、实际效果与失败，不通过自动重试 USER Act 隐藏竞争。
本增量不改变产品、模式、权限或输入；下一项是 M-02 动态菜单目标守卫及持续更新的实际分支，不反复盲跑同一失败。
另经生产路径核对，当前最终命令守卫仅拦截聊天命令包；命令方块/结构编辑器等管理型 GUI 的配置/执行包
不能由它推定安全分类，已纳入 M-07 待验目录，不通过实际执行宿主管理命令来测试，也不将该工作包标为完成。
Showcase 的诊断菜单和 Arm 指纹现显式使用 DEBUG，TAKEOVER 中的物品转移/所持断言改用 USER 树；
仅验证配置独立授予所需 debug.world.read，不改 Runtime 默认权限。工具记录源码前后哈希并核验实际 4 GiB 最大堆；
[本段调查见记录 7](../research/incident-milestone_validation.md#记录-7)。

2026-10-15 原生命令边界增量：共享分类基于实际玩家原生树的 literal/argument 路径，
别名与 execute 重定向保留真实上下文根，未知/间接未决及宿主管理效果在发包前拒绝。
方法入口和最终命令包同时检查 scope、当前玩家连接、模式世代、现有 Lease 与短期 Operation 许可，
不把输入来源提示当授权。Native 回调先取消提交、返回后报告错误，避免协议拒绝成为 Vanilla GUI 崩溃。
状态修改标记 DEBUG_PRIVILEGED 证据，不改变 TAKEOVER/USER 路径、不授予 Debug Arm/OP；
legacy NORMAL_NETWORK 始终污染，原生聊天输入保留 GAME_ROUTED_RAW 和独立效果确认要求。
命令来源附在步骤与总结果中，松键/清理不能覆盖；录制只生成一份提交事件并保留失败后的污染。
Hook manifest 和静态门禁公开实际新增三处取消注入，观察 Hook 不因此取得流程修改权。
当前候选已通过 474 项产品 Java、47 项 Companion/网页、五端 build/check/verifyRelease、
Native/GUI 本地门禁与 Phase 8 hardening static；严格合规失败 0、警告 0。
原生命令边界候选五端 Native/MCP 分别实际通过 36 项/9 项，源码匹配、运行中未变化、干净退出；
对应 alpha 已生成。其后的固定副本工具变化必须另行逐端运行，不复用本批次声明新工具通过。
额外的真实 Brigadier 回归发现别名续段的 query/set 参数可能被拆分，已修正为完整原生路径并覆盖五端；
参考端真实 xp query 与 raw Chat Enter 已通过，不借用修复前的五端结果。
随后 Forge 工作台入口出现 held-item 前置条件拒绝；日志显示动作前清理无物品、失败后清理移除一件，
保留夹具掉落物/同步竞争线索，不放宽生产守卫。工具现在明确清除自有附近掉落物并等待 USER 空手/装备同步，
新增三个确定性工具测试，失败现场在清理前保存 USER HUD/实体；这一工具变化同样要求当前源码重新逐端运行。
M-07 仍未关闭：legacy helper 的真正聊天转换、函数/调度有界解析、完整类别/链接负例和审计验收仍需继续。
UI/世界两领域与完整 Showcase 仍未 READY；未进行新的最终制品安装或人工验收，正式 Persistent Write 为 0。

2026-10-15 命令证据收口：UI/世界两个首个里程碑仍 IN PROGRESS，按上方 M-01 至 M-12 有限工作包持续推进。
五端玩家命令接口保留当前玩家 NORMAL_NETWORK、权限、TAKEOVER 与 Lease，
新增明确的 UNCLASSIFIED/submitted_unconfirmed、effectConfirmed=false、serverValidationObserved=false、
gameplayAcceptanceEligible=false、evidenceContaminated=true，不把 legacy mode=PLAYTEST 当作验收资格。
共享输入引擎将回调传至各 Target 的实际网络提交前，先标记活动录制；发送后失败或调用方取消不能反推没有效果。
无接管、非法命令与排队取消不产生提交事件；不引入新权限或控制模式，不改变 UI/UX。
真实 HTTP 测试覆盖回执/污染及注入发送异常，输入引擎测试覆盖排队取消和提交前失败；
原生聊天路径、真实命令树效果分类和其他入口的最终提交守卫仍未完成，不声称本轮已关闭全部命令旁路。
最终五端分别实际通过 34 项联合检查和 9 项真实 MCP stdio 子检查，
源码 hash 匹配、sourceChangedDuringRun=false、cleanShutdown=true，held input=0、端口/发现记录释放。
[本段调查见记录 8](../research/incident-milestone_validation.md#记录-8)。

2026-10-15 UI/世界工具与消费者增量：五端继续联合推进，但两个首个里程碑均为 IN PROGRESS。
普通 world.action use 已逐端验证斧去皮、锹造草径、锄耕地、错误物品不生效及炼药锅装水/取水，
不增加工具/方块 ID 执行分支；熔炉由普通世界交互打开，语义槽位放入材料/燃料、等待真实烧炼并取出铁锭。
修复 Companion 两处实际分类遗漏：世界 Debug Arm 自动发现指纹改用 DEBUG，世界 Debug batch 自动等待改用 DEBUG；
纯 UI 批次仍不额外要求世界读取权限。Native 权限、模式、Lease、Arm 和版本/值前置条件均不放宽。
五端分别实际通过 33 项联合检查，每端包含 8 项真实 MCP stdio 消费者子检查；
世界批次等值设值保留 partial/DEBUG_NO_STATE_CHANGE，不冒充成功修改，USER 无法取回内部结果；
UI 批次实际禁用/恢复暂停页 Options，inputDispatchCount=0，批次不取得玩家 Lease，最终 READ、held input=0。
首次真实 MCP 验证揭示指纹查询遗漏，修复并重新逐端执行；测试断言也已按正式 Mode 响应字段对齐，
不改 Runtime 门禁或等值设值规则来让测试通过。最终 sourceChangedDuringRun=false、源码哈希匹配、cleanShutdown=true。
五端 build/check/verifyRelease、443 项产品 Java 和 47 项 Companion/网页测试通过；
Native/GUI 本地门禁、Control/Showcase 静态检查通过，严格合规失败 0、警告 0；JAR 无重复条目或测试类混入。
五端 alpha JAR、清单和 Agent 手册绑定当前候选更新。此次为开发 Runtime 与代表流程验证，
不替代完整 UI 固定副本、完整世界/Peer、最终 JAR 安装或统一人工 UX 验收。
持续刷新菜单、全部原版 UI/HUD/Toast、完整世界操作目录及玩家命令效果分类仍有缺口。
保留工作树、不提交、不推送，不启动整合包或其他 Phase；正式 Persistent Write 为 0。

2026-10-15 UI/世界联合增量：按最新授权恢复 UI 推进，两个领域仍分别验收，不合并成单个里程碑。
本轮将非完整方块的语义瞄准改为原生轮廓盒体/面中心候选和原生遮挡射线，保持最终 pick、
TAKEOVER/Lease、权限与证据合同；不增加方块/Mod ID 执行白名单，不承诺任意第三方形状全覆盖。
联合场景为世界打开工作台后 3×3 合成、铁砧文字编辑及取物后重开确认；准备是诊断夹具，Act 为普通输入。
用户追加的边角瑕疵通过共享 ControlChrome 的连续角部混合修正，保留直边、蓝色、呼吸与截图隔离；观感仍由用户评价。
最终候选五端分别实际通过：四端各 29/29，Fabric 30/30，另含 6 项真实 MCP stdio 子检查。
原生完整植株放置/连锁破坏、按钮/拉杆、工作台九铁锭合成一个铁块、铁砧实际命名后重开均通过；
所有运行 sourceChangedDuringRun=false、当前源码匹配、cleanShutdown=true，held input=0，端口和发现记录释放。
此前重跑暴露的菜单确认等待、退出后标题页等待、失败续约计时器及复用夹具底板/半株初始化问题，
已在工具中修正并完整重跑；不放松生产版本守卫，不把测试准备或客户端反馈当作服务器权威。
五端 build/check/verifyRelease、443 项产品 Java 测试与 46 项 Companion/网页测试通过；
Native/GUI 本地门禁、Control/Showcase 静态检查通过，严格合规失败 0、警告 0。
四角连续性、无重叠绘制、缩放/尺寸/淡入/呼吸宽度由共享单元测试覆盖，输入落点和截图隔离不变；
未进行本轮人工观感验收、全 UI 固定副本、完整世界/Peer 或最终 JAR 独立安装验收，两个里程碑仍未完成。
五端 alpha JAR、源码/制品清单与 Agent 手册按当前结果生成，不沿用历史候选证据。
大型整合包兼容继续沿用 ATM9、ATM10、ATM11、ATM10 Aeronautics 四目标及用户固定 Prism 实例的路线；
本轮不下载、不部署、不启动外部整合包。正式 Persistent Write 为 0；不暂存、提交或推送。

2026-10-15 实体交互增量：USER 实体投影增加 baby、TamableAnimal 的 tamed/sitting、
leashed/leashHolderKnown 与仅已知持有者的 leashHeldByPlayer；不公开主人 UUID、love、繁殖冷却或服务器内部关系。
牵引持有者先在客户端缓存解析，再采样原版牵引状态，避免惰性解析导致单次响应字段矛盾。
普通 world.action use 复用原版物品/实体规则；已接入狼错误物品不驯服与有界喂骨、双牛喂食出生幼体、
玩家/栅栏牵引和普通攻击绳结释放、村民世界目标打开菜单及语义槽位付款取货的验收。
测试命令/自定义报价仅为诊断 Arrange，不直接设置主人、love 或生成幼体，也不直接兑换物品。
幼体清理只在自有有界 smoke 世界补标签，不改繁殖结果；驯服保留原版随机性，最多 32 次，预算内失败即失败。
最终候选五端逐个实际通过：四端各 26/26，Fabric 27/27，另含 6 项真实 MCP stdio 子检查。
每端均确认错误物品不驯服、实际喂骨驯服、自然出生幼体、玩家/栅栏牵引与攻击绳结解除，
村民语义菜单真实支付绿宝石 4→3 并取得一个石头；不是仅输入已发送或报价预览。
sourceChangedDuringRun=false、当前源码哈希匹配、cleanShutdown=true；held input=0，无晚到输入，端口/发现记录清理。
五端 build/check/verifyRelease、425 项 Java 与 46 项 Companion/网页测试通过；
Control/Showcase、Native/GUI 本地门禁通过，严格合规失败 0、警告 0。
源码/文档差异检查通过，Windows 工具生成结果按 C-09 保留原文；五端 alpha JAR 与 Agent 手册由生成工具更新。
本轮为开发 Runtime 及代表物种/固定诊断报价验收，不是完整世界里程碑、人工 UX 或最终 JAR 安装验收。
UI 继续暂停；全部物种/交互分支、玩家命令效果分类、固定副本/Peer 与完整交付验收仍未完成。
权限、Lease、Arm、真人 Esc、截图隔离和证据合同不变，正式 Persistent Write 为 0；不暂存、提交或推送。

2026-10-15 含水/连锁、副手与实体增量：五端接入有界同步原版破坏来源跟踪，
目标按破坏前流体替代，不再强制变为空气或只接受一次 Chunk 更新。
调用外修改、目标重复改写、未记录的 revision 增长和超预算仍拒绝。
use_hold.hand 支持主手/副手，副手要求空主手且实际使用手匹配；不绕过原版优先级。
USER 玩家增加 usingHand/riding，仅为客户端状态，不公开服务器关系。
参考端新增含水半砖留水、双格植物两部分移除、副手盾牌使用/取消与冲突拒绝、
喂牛消耗小麦、矿车乘坐/潜行退出均已实际通过；连锁后续状态与客户端自身预测分别记录。
最终候选五端逐个实际通过：四端各 22/22，Fabric 23/23，另含 6 项真实 MCP stdio 子检查。
每端确认含水保留 water、植物两格移除、旧挖掘引用拒绝、副手盾牌使用/取消与手冲突拒绝、
喂食小麦 8→7、乘坐和潜行退出；post-cleanup 输入序号不再增长，held input=0。
sourceChangedDuringRun=false、cleanShutdown=true，端口与发现记录清理。
五端 build/check/verifyRelease、424 项 Java 与 46 项 Companion/网页测试通过；
Control/Showcase、Native/GUI 本地门禁通过，严格合规失败 0、警告 0。
五份发行 JAR 的共享破坏跟踪类与原生适配各一份、无重复 ZIP 条目；alpha 清单和 Agent 手册绑定当前源码与结果生成。
多次同步变更、目标重复改写、未记录 revision 增长与预算超限另由合成测试覆盖；
原版可能只预测点击格，后续连锁效果不冒充同步预测。本轮是开发 Runtime，不是最终 JAR 安装或完整里程碑验收。
UI 继续暂停，完整世界目录、命令效果分类、固定副本/Peer 与交付验收仍未完成。
权限、Lease、Arm、真人 Esc 和证据合同不变，正式 Persistent Write 为 0；不暂存、提交或推送。

2026-10-15 世界持续动作增量：mine、use_hold 和六面/面内 u/v 的 place 已接入五端，复用既有
TAKEOVER、真实输入键位、Operation、deadline 与清理，不直接调用世界修改或改相机朝向。
参考端实际覆盖生存挖掘、超时/取消、弓蓄力松键与金苹果自然完成、六面非中心半砖放置、
实体受伤反馈、箱子矿车容器及死亡/替换实体旧引用拒绝；不是完整实体或世界操作目录。
原版不改变状态的确认包不再误增 Chunk 修改序号，真实 A→B→A 仍失效；死亡动画对象不再产生可执行引用。
最终候选五端逐个真实运行通过：四端各 19/19，Fabric 20/20，另含 6 项真实 MCP stdio 子检查。
确认挖掘完成/超时、活跃取消和 Lease loss 后退 READ、held input=0 且清理后 inputDispatchSequence 不再增长；
六面 u/v 放置由实际半砖状态验证，弓由使用状态和新出现的箭实体验证，金苹果自然结束与蓄力取消均通过。
实体攻击由客户端受伤动画确认，矿车交互打开真实容器；旧死亡/替换引用拒绝，Agent Esc 不撤销控制。
源码运行中未变化，正常关闭后端口与发现记录释放。五端 build/check/verifyRelease、131 项 runtime_core、
81 项 runtime_safety、209 项 Target Java 及 46 项 Companion/网页测试通过；Native/GUI/Control/Showcase
静态与本地门禁通过，严格合规失败 0、警告 0。五端发行 JAR 含新类且无重复条目。
alpha JAR、清单与 Agent 手册按当前源码和新结果生成；这不是最终 JAR 安装或完整世界里程碑验收。
该次增量的单格空气完成条件、含水/连锁与副手缺口由本节最新增量继续处理；完整实体行为仍待补齐。
UI 继续暂停，命令效果分类与完整世界里程碑未完成，正式 Persistent Write 为 0。
保留已有工作树成果，不使用 Computer Use，未暂存、未提交、未推送。

2026-10-15 世界 USER 语义动作增量：代码接入 targetRef 发现、原版相对输入瞄准与短按 attack/use，
复用现有输入队列、Operation、TAKEOVER/Lease 和清理；不加载 Chunk，不直接修改朝向或世界状态。
参考 NeoForge 26.3 已完成真实音符盒的发现、瞄准、使用状态变化、攻击移除和取消清理；首次接入暴露的
客户端线程自等待已改为同一 admission 内复用原生同步分发，不在客户端等待自己排队的 Future。
最终候选五端逐个真实运行通过：四端各 15/15，Fabric 16/16，另含 6 项真实 MCP stdio 检查。
各端确认音符盒 note=0→1、攻击后 air、同状态替换旧引用拒绝、遮挡拒绝和取消无残留；
源码运行中未变化、held input=0、正常关闭、端口与发现记录释放，均绑定当前源码，不推定实体完整行为矩阵。
五端 build/check/verifyRelease、128 项 runtime_core、79 项 runtime_safety、209 项 Target Java
及 46 项 Companion/网页测试通过；协议生成与 Control/Showcase/世界静态门禁通过，严格合规失败 0、警告 0。
五端 alpha JAR 已收集，Agent 手册及清单由工具按当前候选生成；不是新最终 JAR 安装或真人验收。
原版 pick、物品/位置、目标生命周期与缓存 Chunk 修改序号均在动作前复验；同状态替换也使旧引用失效。
该批增量仅支持 aim/attack/use 短动作；后续持续动作以本节最新记录为准，移动到目标仍未完成。
UI 继续暂停，正式 Persistent Write 为 0，不使用 Computer Use，不暂存、提交或推送。

2026-10-15 世界 USER 读取改为有界客户端世界模型：客户端 player/block/entities/scene 共用字段白名单与预算，
不以 Screen/HUD/F3/像素可见性为门槛；未加载/出范围/无世界分别报告，不加载区块，不查服务器或存档。
Provider、Frame、Wait/Assert、resync 与 Recording 使用同一入口，UI USER、输入、权限及证据合同不变。
最终候选五端真实 Native 验证通过：四端各 14/14，Fabric 15/15（另含 5 项真实 MCP stdio 子检查），
实际 USER 响应逐字段核对 Native OpenAPI；读取在 Inventory 中仍可用。非法原始坐标/预算在条件归一化前拒绝，
截断实体候选不能证明不存在；服务器、内部 Provider 和 Deep 不通过 USER 进入。
五端 build/check/verifyRelease、122 项 runtime_core、74 项 runtime_safety 与原有 Target/Companion 回归通过。
源码运行中未变化，收尾 held input=0、正常退出、端口与连接记录释放，正式 Persistent Write 调用为 0。
五端 alpha 和 Agent 手册由现有工具按当前源码、制品与五端结果重新生成，不沿用历史候选证据。
严格合规失败 0、警告 0；源码 diff 检查通过，工具结果按 C-09 保持原样，不手工改写或格式化。
本轮仅为开发 Runtime 读取验收与制品结构检查，不是新最终 JAR 安装或完整世界里程碑验收。
世界语义玩家动作、命令效果与完整里程碑仍未完成；UI 保持暂停，人工统一验收等待用户安排。
保留已有改动，不使用 Computer Use，未暂存、未提交、未推送。

以下为此前增量的已验证记录，其有限呈现范围不代表当前 USER 模型。

2026-10-15 Runtime 自动端口与实例发现：五端统一由 runtime_core 在实例目录锁内建立启动 UUID，
默认由 OS 分配 loopback 端口，成功监听后发布本实例 endpoint.json；固定端口仍支持，冲突时明确失败。
Companion 按明确实例目录握手、固定启动身份，记录变化或重启后拒绝，不自动换绑、重放或重新接管。
世界/UI/Showcase 及既有五端启动编排改为按实例发现，重复 Runner 不覆盖同一实例；历史单项 Gate
仍接受显式 URL/token，不将它们的历史业务场景算作本轮重新验收。Minecraft 游戏服务端和网页端口仍独立配置。
新增并发分配、重复实例/外部 OS 锁、崩溃残留、旧身份、HTTP/WS 与 Companion 身份保护测试。
实际五端逐个启动并正常退出，四端 14/14、Fabric 15/15（含 5 项真实 MCP 子检查）；
结果绑定当前 dirty 源码，记录实际端口/UUID/PID，held input=0，退出后端点移除，正式 Persistent Write 调用为 0。
五端 build/check/verifyRelease、119 项 runtime_core、69 项 runtime_safety、209 项 Target Java
及 46 项 Companion/网页测试通过；严格合规失败 0、警告 0，Control/Showcase、关键本地 Gate 与 Phase 8 静态回归通过。
这不是五个真实游戏并行、最终 JAR 独立安装或 UI 统一人工验收；并发端口由五个合成 transport 实际验证。
五端 alpha JAR 与 Agent 手册由现有清单工具更新，详细连接方式见[alpha Agent 手册](../development/workflow-alpha_agent.md)。
UI 里程碑保持暂停；模式、权限、Lease、Arm、真人 Esc、截图隔离和证据合同不变。
保留此前工作树成果，不使用 Computer Use，未暂存、未提交、未推送。

2026-10-15 世界读取消费者增量：本轮将 LIVE Provider/State Frame、Deep Observation、Wait/Assert、
Event/resync、Recording/Artifact 及世界诊断 Operation 结果统一接入 USER/DEBUG 边界。
默认 USER；内部来源先拒绝再捕获，DEBUG 保留独立 read/debug/debug.world.read 授权，不借用写入 Arm 或 Lease。
请求绑定模式版本，取消传播到底层；USER/DEBUG 事件环、序号与录制归档分离，未知事件不进入 USER。
旧的未分类录制不自动升级为 USER，DEBUG Artifact 使用显式类别与独立 MCP 资源路径；
取消操作仍按既有控制面授权执行，不顺带返回诊断结果。
新增 USER 场景基础只读取最近完成帧中原生方块轮廓的提交事实，最多一个不透明引用，
不暴露方块 ID、世界坐标或实体内部状态；热点只是 GUI 中心参考，不是投影几何或可执行瞄准点。
过期、非第一人称、Screen/Overlay、隐藏 HUD 及视口/相机/命中目标变化时保守不可用。
当前范围不是完整 USER 场景感知、实体分布、玩家命令分类、世界动作或完整里程碑交付。
UI 继续暂停，权限、Evidence、真人 Esc 与截图隔离合同不变；不使用 Computer Use，正式 Persistent Write 为 0。
本轮验证由[世界读取验证](../../validations/conformance-world_read/validation.md)保存新结果，
五端 alpha 继续由 verifyRelease 与现有清单工具生成，不使用历史结果替代新候选。

本增量最终候选五端 build/check/verifyRelease 通过，110 项 runtime_core、69 项 runtime_safety、
209 项 Target Java 与 41 项 Companion/网页测试通过；协议生成/类别解码、Control/Showcase 静态、
Native/Automation/Observation/Recording Local Gate 与 Phase 8 hardening static 通过；严格合规为失败 0、警告 0。
五端逐个运行同一 Native 场景：四端各 13/13，Fabric 14/14（含 5 项真实 MCP stdio 子检查）。
各端实际出现原生轮廓节点，Inventory 遮挡时不可用；USER 录制 ZIP 的结构化状态通过隔离检查。
源码运行中未变化，结果绑定当前 dirty 指纹；输入清零、正常退出、端口释放，最大堆 4 GiB。
alpha JAR 与源码/证据清单保留在 ignored build 目录；不是最终 JAR 安装、真人/图像验收或完整世界里程碑 PASS。
未暂存、未提交、未推送，UI 继续暂停。

2026-10-15 T-07 通用 Runtime 迁移：新增 runtime_core 产品组件，将 25 类同文源码以及
其通用 HTTP/WebSocket 传输依赖合并为单一实现；Provider 公开 SPI 名称保留，内部执行器使用独立 core 包。
正式 ProbeService 与可选 Phase9AExperimentalService 分离，实验路由仍仅属于原有三端，
Provider namespace 由各端生成的模组身份传入，不共享 Minecraft 源码或扩大能力。
模式、scope、Lease、Arm、Resource Version、Evidence 和持久化合同不变；UI 仍暂停。
五端 build/check/verifyRelease、92 项核心、66 项安全、209 项 Target 与 41 项 Companion 测试通过。
严格合规失败 0、警告 0，26 条既有 T-07 已消除；Control、Phase 8、9B/9B.1/9B.2/9C/9D
相关静态与当前本地门禁通过，五个 JAR 各有且仅有一份共享核心，不嵌入另一套 Gson/Netty。
五端独立、静音开发 Runtime 各通过 10/10 项 Native 验证，包括模式/权限、语义 GUI、世界基础读取、
Provider、Capture、Recording/Event 与正常退出；实际 JVM 核对加载了 core.ProbeTransport。
参考 NeoForge 26.3 独立服务端在用户明确 EULA 同意下启动到 Done，并通过 stop 正常关闭。
本轮不是最终制品安装 Live、真人/视觉统一验收或完整世界里程碑；正式 Persistent Write 调用为 0。
新增[迁移验证](../../validations/migration-runtime_core/validation.md)保存当前独立结果，
不覆盖历史世界验收。发行 JAR 位于 build/release/<mod_version>/，alpha 清单绑定新源码与五端制品。
已有工作树成果保留；未暂存、未提交、未推送，不开始后续功能或里程碑。

2026-10-15 Loader 与模板升级阶段记录：五端 Target 不变，固定 Minecraft 线与 26.3 NeoForge 的编译 Loader
按当前官方发布更新，Fabric Loader 已最新；具体版本以 target.properties 和项目规范为准。
模板规范从 1.2.2 升级到 2.0.2，旧模板组件合并为 gradle/conventions，规范附件不再混入项目文档。
项目组件、协议、模式、安全与里程碑合同保持不变；已有工作树成果保留，不提交、不推送。
升级前普通合规检查为失败 0、T-07 警告 26；严格模式把既有警告视为失败，不通过修改模板或
隐藏警告解决，也不借升级展开既有同文 Java 源码合并。spotlessApply、check 与 verifyRelease 已通过，
360 项 Target Java 测试、66 项共享安全测试和 41 项 Companion/网页测试保留；模板哈希一致。
升级当时普通合规为失败 0、警告 26；verifyCompliance --strict 实际返回失败，原因仅为这 26 条既有 T-07。
测试工作目录调整到各 Target 的 build，避免游戏库日志污染 Target 根目录，不修改产品 Java。
五端 runClient 已核对标题页、实际产品加载、共享安全与 UI/世界分类读取，均正常关闭且无新崩溃报告；
关闭本身会取消仍在返回的 UI 请求，退出响应不是本轮功能完成凭据，进程退出码与端口释放另外确认。
升级当时独立服务端因 local.toml 未接受
EULA 而跳过；用户随后明确同意，本轮迁移已补参考端独立验证，不复用此前 UI/世界或最终制品结果。
五端新 JAR 已收集到 build/release/<mod_version>/；旧 alpha-manifest 与世界运行结果不证明本次 Loader
候选，需在重新完成对应世界验证后由现有工具刷新，不手工改写证据或沿用旧哈希。

当前交付目标拆为两个独立的领域首个里程碑，均采用三模式二类别，并分别交付验收。

| 首个里程碑 | 当前状态 | 推进安排 |
| --- | --- | --- |
| UI 领域 | IN PROGRESS，尚未达成 | 合成、命名、熔炉与 UI 调试批次已有联合证据；继续补原版界面/HUD/Toast 与 DEBUG 目录，不以代表流程替代完整固定副本验收 |
| 世界领域 | IN PROGRESS，尚未达成 | 有界 USER 读取、持续动作、精确放置、代表实体/工具与 UI 桥接已有证据；移动、完整实体矩阵、命令效果分类、固定副本/Peer 与完整验收仍待补齐 |

各领域后续里程碑由用户另行定义，本项目不提前规划或启动。
当前世界设计权威为[世界控制与首个里程碑](../design/concept-world_control.md)，
覆盖 USER 客户端模型与预算、命令路由/效果分类、WU/WD 操作目录及五端独立验收。
基础读取和 LIVE 消费链的 category、debug.world.read 与能力声明已接入 Native/MCP/OpenAPI；
完整感知、玩家命令效果分类和世界操作仍待实施，不能从读取隔离推定全合同成立。
后续仍需固定原版独立行为/字段目录、实测预算与声音/服务/真人验收前提。
源码事实保留在[模型应用调查](../research/investigation-world_control.md)，不改写研究正文。
只在独立验证实例通过接口检查本轮行为；UI 已按最新授权恢复，两个领域的历史结果仍保留原验收范围。
已有世界 Observation/Debug 不因拆分失效；读取链采用新分类，mutation 权限和证据不因读取类别而升级。
UI 与世界共享会话模式、Lease 与安全基础；可联合执行测试，但分别记录两个领域实际覆盖与剩余缺口。

2026-10-15 世界分类基础：/v0/player、/v0/server/player 与两侧 block/entities 共六个入口默认 USER。
客户端玩家 USER 仅复用已有可见热栏/副手/效果呈现，保留 GUI 小数边界，不返回原始 XYZ/生命/UUID；
没有有效视口/支持的 HUD 时报告不可用，不据此断言玩家不存在。USER 场景/服务器查询在内部读取前拒绝。
DEBUG 要求 read/debug/debug.world.read、READ 或 OPERATE，不要求 write、Arm 或输入 Lease；
异步发布复验 mode generation，取消向底层传播，关闭后不发布旧结果。默认令牌不自动新增 Debug scope。
Provider/Deep/State Frame、Wait/Assert、Event/resync、Recording/Artifact 与玩家命令效果尚未完成分类；
capabilities.worldContract 明确 consumerIsolationComplete=false，当前候选不适合证明 USER 全链信息隔离。
UI 实现/网页和控制 UX 不扩展；本轮复用既有语义输入准备专用创造世界，夹具不靠固定第一页物品假设。
实际构建/测试/Native Runtime 结果由[世界读取验证](../../validations/conformance-world_read/validation.md)负责，
不是 UI 统一验收、最终制品 Live 或完整世界里程碑 PASS。
每轮内部试用 JAR 继续由 verifyRelease 收集到 build/release/<mod_version>/；
工具额外生成 build/alpha-manifest.json，绑定 dirty 源码指纹与五个 JAR 哈希，不把 HEAD 当成整个候选。
保持原有 mod_version，不擅自宣布正式发布；不提交、不推送，正式 Persistent Write 调用仍为 0。
本轮最终五端各通过 9 项真实 Native 基础用例，源码运行中未变化；实际最大堆均为 4 GiB，
held input=0、正常退出并释放自有端口。验证用可重复独立世界，不冒充完整固定副本世界矩阵。
五端实际 HTTP 路由/权限与发布复验测试包含在 360 项 Target Java 测试中；66 项共享安全、
41 项 Companion/网页测试、协议生成/类别解码保留、check/verifyRelease 与现有 Native/Automation/
Observation Local Gate、Phase 8 hardening static gate 通过。不是新真人、Peer 或最终 JAR 安装验收。
世界分类基础阶段合规保留了 T-07 提示；现已由上文 runtime_core 迁移解决，不改变该阶段的功能边界。

| 模式 | USER 类别 | DEBUG 类别 |
| --- | --- | --- |
| READ | 读取用户可见、可通过正常 UI 获取的内容 | 独立授权读取已捕获的内部 UI 数据 |
| TAKEOVER | 经真实玩家输入复现 UI 操作 | 禁止自动借用调试路径 |
| OPERATE | 不模拟玩家输入 | 经专用授权执行明确支持的 typed UI 调试操作 |

UI 领域保留的推进顺序及退出条件：

1. 接入正式 USER/DEBUG 读取与操作分类，Runtime 先隔离数据，Native/MCP/网页同步；
   默认 USER，不让隐藏列表、遮挡节点和内部字段通过旁路解析或操作结果泄露。
2. 按五个 Target 的实际原版界面建立完整目录，区分界面、控件、交互区域与业务流程；
   每项记录 USER 读取/操作、DEBUG 读取/操作及实际运行证据，不从某端推定其他端。
3. 补齐 USER 语义发现与真实输入，DEBUG 使用独立强类型入口和前置条件；
   没有实现的 Debug 能力明确不支持，不调用任意内部方法来假装覆盖。
4. 固定副本自动测试通过后，由用户从网页验收所有目标 UI，人工撤销等仍由用户执行。
   五端完整功能、消费者与交付检查全部成立才允许宣布里程碑完成。

当前已接入读取类别、USER 输入边界，以及五种有界 UI DEBUG 操作：控件启用、显隐、
EditBox 文本、原生列表滚动和滑块设值。UI 调试要求 OPERATE、独立 scope、客户端 session Arm、
节点 Resource Version 和可选值前置条件，不取得玩家 Lease，不执行任意回调。
DEBUG 图与操作均不是“游戏全部数据/所有内部操作”的承诺，完整里程碑验收尚未完成。
已从五端实际 Minecraft 构件建立 Screen 静态目录，数量依次为 95、103、118、119、119；
该目录全部标记未验收，不能把类数量或继承关系当作可操作覆盖率。
非 Screen 的 presentation 现提供 HUD 标题/副标题/动作栏与四类 Toast 的有界文字；
USER 不发布排队/隐藏内容。多行进度 Toast 按实际原生渲染回调区分标题与名称阶段，
热栏/副手、Boss 栏、侧边计分板、状态效果图标与玩家列表已接入有界只读适配；其余 HUD/Toast 仍需补齐，
不把显示内容虚构为可操作节点。隐藏 HUD 不进入 USER，计分板按实际显示格式读取。
效果图标不泄露未显示的等级/时长；普通读取使用最近完成的原生绘制透明度，
透明、过期及玩家/页面/尺寸不匹配样本保守隐藏。第三方替换绘制与完整视觉遮挡仍需补齐。
五端旁观者菜单现有独立语义快捷键适配：无 Screen 时打开、选中、明确执行和过期，
图标位置不冒充鼠标命中区域；重复点击已选中项为零输入，不隐式传送。
普通读取只含选中名称/原生提示，未选中内部名称保留在 DEBUG。专用旁观 DEBUG mutation、
多人分类分页/实际传送和非标准热栏绑定仍未支持或独立验收，不作为已完成里程碑项。
滑块 Debug 使用原生 setter 和设置回调，可能保存客户端设置；不借用输入 Lease，不是无副作用预览。
当前受限 DEBUG 图是 bounded captured UI graph，不声称已收集游戏的全部数据。
既有 20 项流程/五端历史 PASS 不证明本次新合同；改变源码后必须生成新结果。
保留此前工作树成果，不提交、不推送，不开始游戏世界能力、Persistent Write 或其他 Phase/Extension。

2026-10-13 分类基础阶段已接入五端 tree/resolve、USER action 结果、Native V0、MCP 与网页。
五端逐个恢复固定副本，各通过 21 项 Native 流程、实际到达 37 类 Screen；结果绑定当前源码，
sourceChangedDuringRun=false，收尾输入归零、Lease 释放。Fabric 另通过 4 项真实 MCP 类别检查和
6 项网页链检查（点击、原生文字光标、Shift 获取、九格拖拽、滚动条、PNG 传输），不作图片判定。
USER/DEBUG 在当前图中隔离，不宣称 DEBUG 收集了全部游戏数据；网页拒绝缺少类别标记的旧 Runtime。
五端 build/test、240 项 Target Java 测试、共享投影测试、33 项 Companion/网页测试、协议生成、
verifyRelease、Control/Showcase 工具检查、Native/自动化/Observation/Recording 本地检查和文档/合规检查通过。
该阶段的全仓格式枚举阻塞现已解决：项目根构建脚本保持模板的原文件覆盖范围，
改用单次带目录剪枝的文件树，不再逐 glob 重复扫描 Windows 测试实例；模板文件未改。
该阶段通过全仓 spotlessApply、check、verifyRelease 和五端 build，当时合规保留 T-07 提示。
Fabric 开发启动仍有系统性能计数器和无登录 Realms 警告，未因此中断上述流程；账号/外部服务未验收。
正式 Persistent Write 调用为 0。本次是首个里程碑的分类基础交付，不是完整 UI 或人工验收 PASS。

当前增量：UI 专用 DEBUG 和 HUD/Toast 已覆盖五端产品实现，Native 与 MCP 同步。
UI 节点版本按语义节点隔离，原生列表与其绘制的滚动条不共用状态指纹；
UI 调试批次在真正进入客户端修改前再次检查 deadline，超时排队项不能晚到执行。
五端共 315 项 Target Java 测试、60 项共享安全组件测试、37 项 Companion/网页测试与完整发行检查通过。
固定副本矩阵新增控件调试/恢复、文本调试/Cancel、HUD 文字/进度 Toast，
容器扩为 17 种。热栏/副手、Boss 栏、侧栏计分板、统计页和死亡/重生已纳入；
本轮再补滑块 DEBUG 的范围/值/版本拒绝、恢复、零输入，以及状态效果显隐和 F1 检查。
玩家列表按真实展开状态与原版显示名读取，验证 Tab/F1/背包遮挡和 READ 释放输入；
创建世界通过语义页签进入超平坦预设、游戏规则和数据包页，最后取消草稿，不新增测试存档。
2026-10-14 窗口标题/图标改为原生最终输出装饰，标题文案不变，图标使用游戏原图加右下角圆球。
按用户设计将角标限定到完整图标的右下四分之一，24×24 像素圆球以最近邻缩放，不改变游戏内圆球。
五端保留各尺寸原图、恢复与 close guard；旧占位 PNG 移入回收站。窗口诊断字段与 Hook manifest 同步。
此前窗口/卷轴增量五端分别恢复固定副本后并行批测，各通过 41 项 Native 流程，实际到达 53 类 Screen
及无 Screen 的游戏状态；包含加载页，不代表这些页面的全部业务组合已通过。
sourceChangedDuringRun=false，结果哈希与当前源码一致；检查收尾输入归零和 Lease 释放。
新增窗口检查经正式接口验证三模式标题、角标/原图恢复、反复接管、进入世界后的标题以及 Hook 声明；
五端均通过，但不代替 OS 图标外观的人工评价。
本次再补多人服务器列表语义行与悬停连接/排序图标；首尾不可用排序不进入 USER，
排序后旧子目标身份失效，不受异步 ping 文案变化影响，也不把 narration 隐藏信息当作可见名称。
五端分别通过添加、悬停、上下排序、旧目标拒绝、编辑、取消删除、确认清理与直连页取消。
只使用空白固定副本中的 loopback 记录；连接图标已适配但没有实际执行服务器加入，
不推定联机/认证成功。服务器完整状态文案/tooltip、LAN 和服务相关流程仍需补齐。
Companion/网页新增自动测试确认可见子目标经既有 Lease 转发、隐藏及排序前目标被拒绝；
本次没有新增真实 MCP/网页服务器列表 Live 结果，不把 Native 成功推定为全消费者验收。
本次继续补齐创造物品栏、织布机、切石机、交易界面的自绘卷轴：轨道与滑块通过语义节点拖动，
动作走原生输入，滚动后重新读取内容，旧图案/配方/交易目标不能误点。绘制和按下范围不同的菜单
使用真实可拖动交集；自身拖动允许滑块裁剪高度变化，Screen、GUI 尺寸及语义上下文仍受保护。
五端实际通过创造卷轴上下拖动/内容刷新/搜索禁用/标签隐藏、织布机图案和 12 条固定交易流程；
切石机在 1.21.1/26.x 用凝灰岩验证启用拖动，1.20.1 石头夹具只验证禁用状态，不冒充启用拖动 PASS。
新增 20 项 Target 单元测试覆盖卷轴实际生产数据与拖动守卫；网页增加透传/禁用拒绝测试。
未重跑本增量的真实 MCP/网页卷轴 Live，不用消费者单元测试代替它；首个里程碑仍未完成。
Toast 增量按各 Target 的实际渲染回调提取文字：多行进度分离标题/名称阶段，
配方解锁提供原生两行本地化文案；USER 不透出待显示名称、排队内容或过期/尺寸不符样本。
1.x 的边界低透明度与 26.x 的完全透明边界分别处理，不把不同版本的绘制条件统一猜测。
未知提示类型不虚构文字或动作；System/Tutorial 已接入，但没有独立文字场景 PASS。
[本段调查见记录 9](../research/incident-milestone_validation.md#记录-9)。

2026-10-13 通用原版 UI 基础、固定副本自动化项目与简化网页游戏窗口已实现。基于现有工作树继续实施，
不提交、不推送，不修改控制模式、权限、Lease、真人 Esc 或 Operator 截图隔离。
新增原生交互区域和固定副本 conformance-vanilla_ui；结果按实际界面和 Target 记录，
不把标准输入路径视作所有原版业务流程均已验收，暂不承诺自动适配第三方自绘内容。
网页改为直接 GUI 输入与截图管理，第一人称游玩不进入网页输入 surface。
此前五端各自实际通过 20 项 Native 流程，绑定当次生产源码与固定副本哈希，不代表新增二类别合同；
网页输入链独立验证点击、原生文本光标、Shift 获取、九格拖拽、滚动条与 PNG 传输。
这些结果不等于全部原版界面、所有业务组合或统一人工 UX 验收。

| 能力 | 已有状态 | 当前边界 |
|---|---|---|
| V1 核心自动测试闭环 | 已有绑定源码的发布验收 | [V1 attestation](../../validations/regression-v1_release/result/final-attestation-2dda8448d00852d42fb3e07525ee05daaaddd66f.json)只证明对应提交 |
| Deep Observation / Provider V2 / Revision | 已实现、合同硬化 | 每次请求按 Target capability 判断可用性 |
| Typed Debug / bounded batch | 已实现 | scope、Arm、资源/值前置条件、诊断证据独立 |
| Persistent Read / Write Safety Foundation | 已实现相应限定基础 | 正式 Persistent Write 未实现；Entry Review READY，不等于允许写 |
| runtime-safety 五端包装 | 已有最终制品验收 | [包装记录](../../validations/integration-packaged_runtime/result/packaged-artifact-attestation-dc12612dd0c31f86c26fa8b18aee17f6f4733e04.json)不是本轮新 Live |
| READ / OPERATE / TAKEOVER | Round 1 已验收；后续实现 COMPLETE | [实现证据](../../validations/conformance-agent_control/result/agent-control-r24-implementation-20260905.json)；统一人工/视觉验收 READY、未执行 |
| UI Showcase | READY | [可重复案例](../../validations/feature-control_ui_showcase/validation.md)，不是最终 UX PASS |
| 四个 ATM Compatibility Target | PLANNED / NOT STARTED | 用户负责固定 Prism 实例和版本；不自动安装/运行 |

构建、自动测试、运行、人工验收是不同结论。以下长期设计不代表全部 API 已实现；
实际字段以 OpenAPI、Runtime capability 和当前源码为准。Wire Protocol v1 **NOT FROZEN**。

### 当前风险与下一步

- 通用 UI/网页基础 READY；完整原版 UI 流程覆盖仍为 PARTIAL。
  [固定副本验证](../../validations/conformance-vanilla_ui/validation.md)列出 16 个界面家族，
  每端 result 明确记录实际到达与未到达的界面，不能用某端结果推定其他端。
  该轮五端构建、225 项产品 Java 测试、32 项 Companion/网页测试、协议生成、控制/结构/文档检查及 verifyRelease 通过。
  新增滚动条、富文本链接、建议和进度区域，原生菜单 carried state 与 slot 分开；
  新实例首轮资源重载的提前 Operator 文字渲染崩溃已加保护，不改变正常外观、权限或截图隔离。
  网页只保留游戏窗口与轻量截图/接管管理，直接鼠标、键盘与最终 IME 文字走标准接口；
  不提供第一人称游玩，不使用 Computer Use，不把 PNG 传输检查当作视觉判定。
  下一步由用户通过网页体验反馈；账号/外部服务、存档导入修复和全部配方/设置组合仍需专用场景。
  第三方自绘内容不承诺自动适配；统一人工控制 UX 验收仍未执行，其他 Phase/Extension 未开始。
  正式 Persistent Write 调用为 0；保留既有工作树，不提交、不推送。
- 2026-10-12 补齐优先原版自绘 UI：五端实际通过资源包选择/排序、切石机、织布机、
  附魔、交易、信标、书本编辑/签名/翻页及普通/悬挂告示牌，共 12 项新增场景；
  五端原有 6 项 UI 回归同时通过。[新增结果](../../validations/compatibility-standard_gui/result/rendered-ui-26.3-fabric.json)
  与各端对应文件绑定当前 dirty 源码哈希。旧版书本页面身份现包含页码，旧页面请求被拒绝。
  Native/OpenAPI/MCP 与网页同步语义 controlKey/packId/translationKey 和 keyboard-only 编辑；
  Fabric 的真实 MCP 包排序、网页多行书本/旧页面拒绝及告示牌行输入通过
  [消费者结果](../../validations/compatibility-standard_gui/result/ui-consumers-26.3-fabric.json)。
  五端 build / check / verifyRelease、205 项产品 Java 测试、25 项 Companion/网页测试通过。
  权限、TAKEOVER、Lease、输入取消与截图隔离不变，Persistent Write 调用为 0。
  该次记录结束时，书本独立文字链接、聊天建议弹层、进度图节点尚未适配；当前状态以上方新记录为准。
  人工统一验收仍未执行；保留工作树，不提交、不推送，下一步由用户体验反馈决定。
- 2026-10-12 扩展原版 UI 语义覆盖：修复列表仅暴露父容器和选择器只搜索第一层的问题，
  五端接入递归容器、原生行边界、文字/滑块/滚动和创造标签/配方书适配。
  网页按节点 actions 提供操作，不再将列表中心点击冒充世界选择。
  [覆盖清单](../development/testing-vanilla_ui.md)明确自绘区域和未验收项；不宣称全部原版 UI PASS。
  五端构建 / check / verifyRelease、195 项产品 Java 测试、23 项 Companion/网页测试通过。
  五端均实际完成世界行选择、中文输入、标签、滑块、隐藏列表滚动与嵌套选项，
  [当次结果](../../validations/compatibility-standard_gui/result/vanilla-ui-26.3-fabric.json)绑定 dirty 候选源码哈希，收尾 held input 为 0。
  Fabric 26.3 的真实 MCP stdio 与网页到 Native 的世界选择、中文输入及网页 PNG Capture 通过；
  单个 Showcase READ 场景通过正常 GUI 进入/退出世界，非人工视觉结论。
  自绘交互区域仍未全部覆盖，完整原版 UI 目标为 PARTIAL；人工统一验收仍暂缓。
  保留既有工作树，不提交、不推送；不扩展世界操作或 Persistent Write。
- 2026-10-12 新增 Companion 本机网页界面工作台：只消费标准 Native V0，不修改 Runtime。
  READ 浏览与 TAKEOVER 点击独立，复用现有认证、任务、Lease 和人工撤销合同；
  实际界面与槽位按语义 nodeId 交互，按需 Capture 提供底图。
  构建、自动测试和 26.3 Fabric 接口验证单独报告，用户网页观感不冒充统一真人验收。
  本轮不提交、不推送，不开始其他 Phase 或 Extension。
- 2026-10-11 系统修复创造背包的语义槽位身份：以当前 Screen 菜单的有序位置作为唯一编号，
  不再把可重复的原生包装槽位编号当作选择器；节点绑定菜单/槽位对象，隐藏槽标记不可见。
  五端同步实现与测试，现有圆球落点、动效、安全和证据隔离不变；
  五端 build / check / verifyRelease、197 项产品 Java 测试（含 25 项槽位专项）、
  Companion、协议生成与控制/文档门禁通过。26.3 NeoForge 实际语义取放/拖拽通过；
  26.3 Fabric 实际返回 47 个唯一背包槽，42 个可见槽均唯一解析，语义取放/悬停通过，
  服务端确认物品恢复且 held input = 0；上述结果不替代五端完整人工验收。
  保留已有工作树改动，不提交、不推送；26.3 Fabric 留给用户复测。
- 2026-10-11 按用户全局规则实施光标请求同步：普通 GUI 换页保留坐标，游戏有效捕获/释放及
  定位/显隐请求作用于虚拟光标；宿主鼠标同步不能覆盖它。五端分别适配 GLFW/SDL 原生入口，
  当前工作树的构建、自动测试和代表端运行单独验证，不冒充五端完整真人验收。
  五端 build / check / verifyRelease、168 项产品 Java 测试、Companion 与控制/文档门禁通过；
  26.3 NeoForge/Fabric 的实际 GUI 换页、背包归中/显隐案例通过，Fabric 任务连续性、
  截图/录制并发和取消回归通过。`cursor-rule` 可重复观察，运行记录绑定当前 dirty 工作树，
  不代替最终制品或用户视觉验收。
  保留已有任务连续性与弹簧/蓝边参数；不提交、不推送，26.3 Fabric 预览由用户观察。
- 2026-10-11 实施任务生命周期与视觉连续性修复：显式 begin/heartbeat/end 与权限、mode、
  Lease正交，任务等待不闪断，结束/超时清理绑定输入；切页保留最近 GUI 光标位置。
  五端代码和 Native/MCP 合同同步，当前候选自动验证与运行结果按本轮实际检查报告。
  不提交、不推送；统一人工验收未执行，26.3 Fabric 重复示例留给用户评价。
- 2026-10-11 调整圆球与 Control Chrome：采用真实弹簧响应、默认蓝色 #339CFF、
  70% 点击缩放、500/440 ms 视觉过渡及 3 秒边缘呼吸。
  精确落点、权限/Lease、拖拽和证据渲染边界不变；重复预览新增主菜单 `chrome-spring` 案例。
  自动验证和客户端启动不替代用户观感评价；本轮不提交、不推送，不开展统一人工验收。
- Companion 客户端依赖安全修复已完成；当前版本由其
  [package.json](../../components/companion/package.json) 与锁文件负责。
  2026-10-10 复查全量及生产依赖审计，均为 0 漏洞；不得继续把该问题列为当前 blocker。
  本轮重新执行 Phase 8 Local Gate，构建、105 项 Java 测试、Companion 测试和真实依赖审计均通过。
  这不改写历史 Phase 8 失败，也不是新提交的完整 Release Attestation。
- NeoForge 26.2 加载期曾出现 Operator Chrome `Pipeline contains invalid shader program`；
  资源加载后的复测成功不代表风险解决。[声明检查记录](../../validations/conformance-agent_control/result/control-capability-correction-20260907.json)保留该未解决项。
- 按用户 UI/UX 要求实现发光圆球指针、自由移动轻微回弹与 GUI 观看节奏；
  拖拽保持单调，权限撤销和输入释放立即执行。26.3 NeoForge/Fabric 已通过本轮自动 Runtime
  hover/click、拖拽取消、Agent Esc、相对视角、录制及干净退出；不是最终制品或真人验收。
  五端 build、check / verifyRelease、产品 Java 121 项测试与 Companion 3 项测试通过；
  统一人工/视觉验收仍待用户进行，不以自动测试代替观感评价。
- 根据 Showcase 反馈将圆球调整为 24×24 偶数像素尺寸，扩大明亮本体，
  实际按钮按下/松开驱动缩小与恢复；坐标、输入时序及安全清理不变。
  自动测试覆盖连续点击、多按钮、拖拽、取消复位及偶数几何；新版观感仍待用户确认。
- 系统核对交互坐标：修正原生 Slot bounds 向右下偏移 1 GUI 像素，metadata、resolve、
  hover/click 与 drag 共用中心计算，保留半像素精度并拒绝无效/歧义目标。
  不通过挪动画面补偿真实落点；圆球样式、回弹和观看节奏保持不变。
  五端坐标单元测试通过；26.3 NeoForge 原生界面/背包落点检查与 26.3 Fabric 真实物品
  hover/click/drag Smoke 通过，收尾 held input=0。这是当前工作树的开发 Runtime 验证，
  不是五端完整现场、最终 JAR 或统一人工/视觉验收。
- UI 预览入口复用 Showcase，默认 26.3 Fabric、2560×1440、主菜单起点并保留客户端；
  可单独重复指针、背包、界面、模式与叠层案例。主菜单前置条件拒绝误操作当前世界，
  客户端识别不再把 Fabric 服务端当作客户端；这不代替统一人工验收。
- 恢复新功能开发前，先完成下面的 Target / 依赖更新与回归。
  当前已升级构建事实并适配 26.3 API；五端实际启动与 Native/MCP 回归通过，人工验收仍待用户进行。
  T-07 通用代码迁移已完成，当前严格合规无警告；不因此恢复 UI 里程碑或启动后续功能。
- 深层能力后续范围见[Deep State 计划](plan-deep_state.md)。Persistent Write、
  Recording V2 / Reconstruction / stress 和 Phase 10 均须独立授权。
- 当前构建采用远程模板 2.0.2 的统一 conventions 插件；`check` / `verifyRelease` 校验布局、
  文档链接、配置和发行制品。以下历史布局说明不覆盖当前模板规范。
  产品合同、源码测试和验证项目的现有检查仍保留。
- 本地实例与构建输出按需生成，不是源码交付内容；当前源码工作树不附带历史存档或运行缓存。

### 恢复开发前的版本更新

#### 维护策略与本次目标

2026-10-10 完成 Target/依赖升级，2026-10-15 继续更新 Loader 与模板；以下为当前构建事实，自动/运行验收状态单独记录。
实际版本以 `target.properties`、`gradle/libs.versions.toml` 和 Companion 包配置为准。

- `1.20.1-forge`、`1.21.1-neoforge`、`26.1.2-neoforge` 固定 Minecraft 版本，持续更新对应 Loader。
- 现有 `26.3-neoforge` / `26.3-fabric` 是滚动 Minecraft 线，不再把 26.2 当长期固定版本。
  只跟进最新 Minecraft 正式版，只有 Loader 允许测试版；每次仍固定具体版本。
- 当前 Minecraft 最新正式版为 26.3，本次两个滚动 Target 均升级到 26.3。
  Minecraft 快照、预发布版与候选版不纳入维护 Target，即使某个 Loader 已能运行。
  对应 Loader 尚未支持最新正式版时，明确报告该端升级受阻，不伪称已支持。

| Target | Minecraft 策略 | Loader | Java | 处理 |
| --- | --- | --- | --- | --- |
| `1.20.1-forge` | 保持 | 47.4.28 | 17 | 更新 Loader，不升级 Minecraft |
| `1.21.1-neoforge` | 保持 | 21.1.259 | 21 | 更新 Loader，不升级 Minecraft |
| `26.1.2-neoforge` | 保持 | 26.1.2.117 | 25 | 更新 Loader，不升级 Minecraft |
| `26.3-neoforge` | 最新正式版滚动线 | 26.3.0.77 | 25 | 当前参考 Target；更新到已发布的非 Beta Loader |
| `26.3-fabric` | 最新正式版滚动线 | 0.19.5 | 25 | 已适配 SDL / Renderpearl；Fabric API 为 0.162.0+26.3 |

来源：[Minecraft 发布清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)、
[Forge 1.20.1](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)、
[NeoForge 发布元数据](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)、
[Fabric Loader 元数据](https://meta.fabricmc.net/v2/versions/loader/26.3)、
[Fabric API 26.3](https://github.com/FabricMC/fabric-api/releases/tag/0.162.0%2B26.3)。

#### 相关依赖与构建工具

升级候选优先使用最新兼容正式版；Loader 测试版按上述策略接受。
不把构建工具的开发快照或其他库的 Alpha 自动当成必须升级目标。
已为最新的依赖保持，不为产生版本变化而降级。

| 范围 | 本次目标 | 约束 |
| --- | --- | --- |
| 仓库模板 | 2.0.2 | 仅按[模板升级规程](../../gradle/conventions/specification/development/procedure-upgrade_template.md)整体升级受保护文件 |
| Gradle Wrapper | 保持 9.8.1 | 当前最新正式版；Wrapper 整体与校验和保持一致 |
| ModDev / Loom | 保持 2.0.148 / 1.18.3 | 项目选定版本保留，本轮不扩展为 Minecraft 构建插件升级 |
| Foojay / Spotless / ktfmt / TOML 解析 | 保持 1.0.0 / 8.10.4 / 0.65 / 2.2.0 | 不独立改写模板组件源码 |
| Java 格式器 | 保持 1.36.1 | 1.37.0 将 Style enum 改为其他类型，实测与 Spotless 8.10.4 不兼容；不改模板来绕过 |
| Markdown 检查器 | 保持 0.2.79 | 与模板 2.0.2 配套，规则来自规范插件 |
| OpenAPI Generator | 7.25.0 → 7.26.0 | 重新生成 Java / TypeScript 模型，不手工编辑生成文件 |
| 产品单元测试 JUnit | 5.14.4 / Platform 1.14.4 → 6.1.3 / 6.1.3 | 迁移到最新正式代际须验证 Java 17 下限；模板测试已为 6.1.3 |
| Mixin annotation processor | 0.8.5 → 0.8.7 | 不覆盖 Loader 运行时 Mixin |
| Gson 测试依赖 | 保持 2.14.0 | 当前最新；Minecraft 内置版本不由本项目强制替换 |
| Netty | 旧端 HTTP 模块与游戏依赖对齐；26.3 测试使用游戏的 4.2.16.Final | 移除重复测试版本；NeoForge 严格约束不允许与最新 4.2.19.Final 混搭 |
| Java 工具链 | 保持 17 / 21 / 25 代际，更新维护补丁 | Temurin Windows x64 17.0.20.1+1 / 21.0.12.1+1 / 25.0.4.1+1 已实际运行；验证时从本机工具链解析，不向 Target 配置写绝对路径 |
| MCP 服务端 / 客户端 | 服务端 2.0.0 → 2.3.1；客户端保持 2.3.1 | Core 随 SDK 解析到匹配版本，验证 stdio 互通 |
| Zod / TypeScript | 4.4.3 → 4.6.5；TypeScript 保持 7.0.2 | 重跑类型、工具合同和 Companion 测试 |
| Node / Node 类型 | 建议 Node 24.21.0 LTS；类型 24.10.0 → 24.19.2 | Node 26.11.1 与类型 26.6.5 为 Current 线备选，不混用运行时与类型代际 |
| CI Actions | 保持现有发布主线 | 不修改受保护模板工作流；模板新增变化按升级规程同步 |

上述候选来自对应上游发布或注册表；模板以
[2.0.2 发布](https://github.com/RecRivenVI/Ravens-Mod-Template/releases/tag/2.0.2)为准。
其他来源：[Gradle](https://services.gradle.org/versions/current)、
[构建插件](https://plugins.gradle.org/)、[Maven Central](https://repo.maven.apache.org/maven2/)、
[OpenAPI 发布](https://github.com/OpenAPITools/openapi-generator/releases/tag/v7.26.0)、
[Mixin](https://repo.spongepowered.org/repository/maven-public/org/spongepowered/mixin/maven-metadata.xml)、
[npm 注册表](https://registry.npmjs.org/)、[Node 发布线](https://nodejs.org/en/about/previous-releases)、
[Adoptium](https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse)。

#### 升级风险与退出条件

26.3 不是纯版本号更新：[官方移植说明](https://docs.neoforged.net/primer/docs/26.3/)记录
GLFW → SDL 输入/窗口后端迁移、键码变化及 Blaze3D → Renderpearl 渲染 API 变化。
26.3 Hook 已适配 SDL，固定 Minecraft 三端仍使用 GLFW，保持 TAKEOVER、真人 Esc、
Host Cursor 隔离、窗口标题/图标恢复和 Capture/Recording 排除 Operator UI 的外部语义。
不因版本迁移调整 UI 设计或扩大产品能力。适配细节见[26.3 移植](../development/porting-26_3.md)。
新 Minecraft Target 不能沿用 26.2 的最低 Loader 要求、DataVersion、资源格式或旧 Hook 签名；
固定 Minecraft 的三端仅在兼容性仍成立时保留现有最低 Loader 要求，不以编译版本替代运行下限判断。

Netty 必须区分测试与 Minecraft 共享运行时。上游当前正式支线为 4.1.139.Final / 4.2.19.Final，
不能只升级 HTTP 模块后与游戏内其他 Netty 模块混搭，更不能自动切到 5.0 Alpha。
Minecraft 1.20.1 / 1.21.1 / 26.1.2 / 26.3 的内置 Netty 分别为
4.1.82.Final / 4.1.97.Final / 4.2.7.Final / 4.2.16.Final。
旧端补充的 HTTP 模块应先验证与所属游戏模块一致的版本；整族替换须有独立兼容依据。
Loader 的 ASM、Mixin、LWJGL 及游戏内置库由其依赖图管理，不额外覆盖成 Maven 最新版本。

实施顺序：模板/工具与 Companion → 固定 Minecraft 三端 Loader → 26.3 NeoForge → 26.3 Fabric。
实施时同步 Target 注册、项目规范/README/安装说明、验证 Target 和脚本配置；
旧实例/存档非破坏保留，不用新 Minecraft 自动打开重要旧世界。
完成五端 build/test/发行制品检查、Native/MCP 与控制/观察/存储只读回归，
并取得新参考端及滚动 Fabric 的真实运行证据后，才进入新功能开发。
真人输入与视觉验收仍单独报告；历史 evidence 保持原文，不借历史 PASS 证明新版本。
Persistent Write、Phase 9E/F/G/10、E1/E2/E3 和整合包测试不由此次升级自动开启。

#### 本次实际回归范围

五个 Target 均完成本轮开发 Runtime 启动、HTTP/WebSocket、真实 MCP stdio 连接、
画面读取、READ/OPERATE/TAKEOVER 能力与状态一致性检查和正常退出；
结果记录在 [Runtime Probe](../../validations/feature-runtime_probe/validation.md) 的当前升级结果中。
这些是未提交候选的开发运行证据，不是最终 JAR 独立安装验收，也不是新 exact-commit attestation。

26.3 NeoForge/Fabric 另执行真实 GUI 悬停/点击、拖拽取消、Vanilla 相对视角、Agent Esc、
Capture、Recording 和 Save & Quit；取消后没有晚到鼠标事件。
Fabric 26.3 在专用新测试世界正常退出后，world/player/chunk 的 PERSISTED 只读均成功，
明确使用 retained detached storage context。测试世界由 Vanilla 正常保存，Persistent Write 调用为 0。

本轮未执行真人 Esc、Alt+Tab、真人点击、完整五端世界/人工矩阵、Vulkan 与 Dedicated Server。
服务器因本机未接受 EULA 不启动。最终留下单个 Fabric 26.3 测试客户端等待用户反馈，
不自动开始统一人工验收或新功能开发。

[本段调查见记录 10](../research/incident-milestone_validation.md#记录-10)。

本文件只描述当前计划，不再追加流水记录。已有阶段与调查内容在本次五端完整矩阵结束后一次性分类归档，
不改写既有证据或验收结论；归档前的旧条目不作为当前推进权威。
恢复时先读[唯一当前状态页](status-milestones.md)，其中维护M-01～M-12×五Target、剩余必需项/规模、最近证据及下三步。
用户已确认用合规文件名status-milestones.md承载STATUS页，不修改模板D-02或另建重复状态文档。
推进改为广度优先；不透明管理型效果可按发送前拒绝、零相关服务器副作用及明确限制验收，
不要求为首个里程碑实现Structure/Test模板解析、函数/调度或来源不明数据复制；不能把未接守卫/未验的拒绝写成通过。
每增量验证参考端和受影响端；三个增量、共享代码变化或候选冻结时执行五端完整矩阵并更新alpha。

M-09a～f按用户决定拆分：首个里程碑只交付三部署设计定稿、权限主体模型、诚实的安装/合规表达，
以及现有Peer范围内的双端联动验收；M-09a为当前安装事实/制品，b为既有主体/接收端授权，
c只交设计及明确不可用，d为客户端/联动UI，e为集成服/已有Peer世界目录，f为现有消费者和交付。
下一里程碑负责单端完整互通、独立服务端Runtime、零玩家发现/控制台三模式二类别、
完整仅服务端目录与对应消费者/制品矩阵。完整分配见状态页及[部署设计](../design/concept-deployment_scenarios.md)，
不因拆分宣称当前both可选安装，不用客户端OP或可达Peer代替服务端授权。
模式、权限、Lease、Arm、Human Override及证据合同不变；不使用Computer Use，不执行正式Persistent Write，不提交、不推送。

2026-10-16 14:00 中断恢复收尾：仅补跑1.21.1 NeoForge、26.1.2 NeoForge和26.3 Fabric的当前配置专项，
每端16项Native/6条真实MCP通过；与已核对源码哈希的26.3 NeoForge/1.20.1 Forge结果形成同一候选矩阵。
逐端重新核对所有记录的源码哈希、固定基准/恢复哈希、最大堆4 GiB、正式Persistent Write=0、正常退出和发现记录删除；
实例锁均可取得，无仓库Java Runtime/Gradle进程残留。端口/启动器清理由实际Runner确认，不用缺失的报告字段推断。
alpha生成器已刷新五个发行JAR的清单与Agent手册，逐一SHA-256一致，源码指纹为
9bc95d7d51940b62bb4412f3e848aee6f9b5cfb67e0df2a1e6d52fb6e217648d；范围command-configuration、fullNativeSmoke=false。
旧十二案证据拒绝事实保留；旧清单不再充当当前候选证明。这是专项恢复，不是新的完整五端领域或独立制品运行验收。
状态页已列每项剩余必需项与增量规模；M-02/M-04/M-05/M-06/M-09仍有真实工作，不将方法调整写成整体READY。

2026-10-16 14:24 广度推进第1个工具/验证增量：重跑五端原版命名类继承目录，具体Screen数为95/103/118/119/119，
现有16家族按类名映射65/67/68/68/68。全部目录状态仍是NOT_VALIDATED，不把静态映射当作真实操作或缺席依据。
补充movement专项及统一Runner选择，使用既有不可变副本和正式Native pipeline，不改产品代码或权限/证据合同。
先在26.3 NeoForge实际验证，再按Forge1.20.1、NeoForge1.21.1、NeoForge26.1.2、Fabric26.3依次运行，
五端各11项通过：权限负例、语义进入、WASD/斜向、跳跃、潜行/冲刺、墙体/边缘、梯子、水中升降、取消与Lease释放。
每端源码/工具哈希匹配、固定基准与恢复哈希一致、4 GiB、held keys/buttons=0、有界采样无晚到输入、正常退出与端点/端口释放。
未新增MCP检查；没有Peer、独立最终JAR或人工验收，不把11项提升为WU-01～03全家族或整个世界领域完成。

[本段调查见记录 11](../research/incident-milestone_validation.md#记录-11)。

2026-10-16 14:48 广度推进第2个增量：在既有UI Runner新增special-menus，参考端先完成七项原生检查，
实际取出讲台书并在背包确认、翻页、马鞍装备/取回、驴胸箱货物往返，以及Crafter禁用/启用和旧菜单版本零输入拒绝。
马类Act使用USER实体目标及真实潜行/使用，槽位Act只用语义节点/原生Shift点击；Arrange为自有副本的诊断命令，非自然游玩证据。
[本段调查见记录 12](../research/incident-milestone_validation.md#记录-12)。

发现Crafter树缺少可见禁用状态：参考端新增可选slotDisabled，只给原生网格前9格，active/actions仍由原生可操作性决定。
状态进入menuRevision与逐事件目标守卫；共享USER投影保留可见字段但不泄露隐藏槽位。
单测分别验证移动时外部状态变化在点击前拒绝，以及原生自身拖动回调后的状态合法推进，不误判自己的受控变化。
参考端实际流程确认后移植NeoForge1.21.1、NeoForge26.1.2和Fabric26.3；保持Forge1.20.1无Crafter导入/字段。
Forge原生JAR的CrafterMenu/Screen条目均不存在，Lectern/Horse条目存在，JAR SHA-256为
b28123b1199d53eddfcf2f247f82222cc632e2bcc058cb883680a93b9e04e031；不将未验项目伪报原生缺席。
Native/OpenAPI、使用说明和更新日志同步，真实MCP/临时网页将读取两种槽位状态，消费者不取得输入Lease。

两次构建/单元与严格门禁已通过；最后一次含自身拖动反例，之后仅修正测试对隐藏槽位的分组断言，不改产品。
14:48启动本增量冻结候选的五端完整UI、世界和移动回归，参考端优先、每次仅一个4 GiB客户端，逐次正常关闭再启动下一实例。
源代码/工具在运行中冻结；只有全部实际结果和源码/制品匹配时才重新生成alpha清单/手册。
14:00旧alpha不再代表修改后的候选；参考端之前七项结果也不冒充后加MCP/网页消费者版本已经通过。
当前状态仍IN PROGRESS；本轮不进行人工验收、独立最终制品安装或正式Persistent Write，不提交、不推送。

[本段调查见记录 13](../research/incident-milestone_validation.md#记录-13)。

2026-10-16 15:29 菜单候选完整矩阵进行中：26.3 NeoForge与Forge1.20.1已依次实际完成，UI分别52/51项，
世界各38项且包含13条真实MCP，移动各11项；均固定副本恢复、实际4 GiB、held input清零、正常关闭与端点/端口释放。
每份报告的工具/产品源码哈希重新核对，未拼接其他候选。Forge少一项的原因仅为原生没有CrafterMenu/Screen，
讲台、马鞍与驴胸箱三类流程均实际通过，不从参考端推断。
包装Gate以SkipBuild读取本轮已构建的五个JAR，平铺runtime_core/runtime_safety、唯一共享类和许可检查通过；
Runtime=NOT_RUN，不能代替独立最终JAR安装验收，旧dc12612制品证据不用于当前候选。
当前已启动1.21.1 NeoForge UI，剩余两端仍在队列；沿同一个有界进程会话继续，不重复启动或同时保留多个客户端。
本轮冻结产品与工具，仅同步当前状态/说明；alpha等待全部五端真实结果后生成，未提前宣称全矩阵或里程碑READY。

2026-10-16 15:44 NeoForge1.21.1本候选已实际完成UI52、世界38/真实MCP13、移动11，
逐文件核对产品/工具/消费者源码哈希、固定副本、实际4 GiB、held input=0、正常退出及端点/端口释放。
Crafter两态在Native、真实MCP和临时网页均读回，马/驴/讲台也实际执行；不凭参考端代码或构建推定。
至此矩阵3/5闭环，26.1.2 NeoForge已开始UI，Fabric尚未运行本冻结候选；继续同一串行会话，不重开已通过端。
状态页保留每个M工作包的剩余条件，不把当前52/38/11套件等同完整原版UI或WU/WD目录。
没有产品/工具改动、没有提交/推送；alpha仍等待五端完成后统一刷新，正式Persistent Write调用为0。

### 本阶段完整矩阵检查点

2026-10-16 16:16，菜单候选的同一冻结源码矩阵完成：Forge1.20.1 UI51项，其余四端UI各52项；
五端世界各38项并含13条真实MCP检查，移动各11项。逐份结果的源码哈希一致、sourceChangedDuringRun=false，
固定副本恢复、4 GiB、held input清零、正常关闭与端点/端口释放由实际Runner核对。
UI报告不含cleanShutdown字段，不用空字段推断关闭；以Runner正常退出和端点/锁清理判断。
生成器刷新五个alpha JAR对应清单与Agent手册，fullNativeSmoke=true仅指世界联合套件，
不表示完整领域、最终JAR独立Loader安装或人工验收完成。两个领域与完整Showcase仍IN PROGRESS。
文档归档不提升这些结论；正式Persistent Write调用保持0，不暂存、不提交、不推送。

### WD-05属性与固定分发检查点

2026-10-16 18:44，属性候选的五端矩阵全部完成。world.block.set新增有界properties/expectedProperties字符串映射，
同类型保留未指定属性，替换类型从原生默认状态开始；属性域、版本、值、世界Arm和独立scope在修改前校验。
邻居更新、方块实体替换和次生效果仍可能发生，不提供跨方块事务或回滚，不改变模式、输入、权限和证据合同。

原生属性快照改用Property.getName的序列化值。参考端实际发现音符盒枚举HARP与合法harp不同，修正并逐端实测，
不将String.valueOf(enum)当作原生格式。单测以原生Property及测试StringRepresentable枚举验证往返；
真实NoteBlockInstrument直接单测需要已启动注册表，实际原生值由游戏内矩阵验证，不伪称单元环境已完成Minecraft启动。
聊天取消测试加入显式子任务注册屏障，分别断言父许可立即失效、子任务取消和无后续输入；生产取消路径未改。

628项产品Java、52项Companion/网页及五端构建/verifyRelease通过，严格合规无警告，80篇Markdown与链接检查通过。
五端世界各44项，含13条既有真实MCP检查和属性专项内的实际MCP参数/修改验证；不将后者重复计作第14条消费者检查。
Forge UI51项、其余四端UI各52项，移动各11项；15份结果的源码/工具哈希逐一匹配、sourceChangedDuringRun=false，
每端自有固定副本恢复、4 GiB、held input清理、正常退出及端点/锁释放已复核。
UI报告没有cleanShutdown字段，仍以实际Runner与清理结果判断，不从缺失字段推定成功。

产品指纹b6d7829f70624c1bdeb7dcba445ce69edc51a611c4aac5cd63c1475d68314058的五个JAR、清单和Agent手册，
发布到D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/20261016-1844-b6d7829f，latest.txt指向这一完整七文件批次。
生成器在刷新后验证并自动分发，不覆盖已发布目录；最近三份和显式登记在用的批次保留，登记在快照之外，旧批次仅移入回收站。
31项合成检查覆盖时间格式、重复发布、过期源文件、哈希、目录冲突、并发锁、使用登记、保留及回收失败。
首次真实发布发现PowerShell自动解析UTC时间后丢失偏移的目录名问题，已修复并补UTC-Z测试；失败时尚未创建快照或更新latest.txt。

上述运行仍为开发Runtime矩阵。当前JAR结构检查不代替独立正常Loader安装，M-10尚未关闭；
UI/世界/完整Showcase均IN PROGRESS，人工验收未执行。正式Persistent Write为0，不暂存、不提交、不推送。

### M-10独立发行安装与Netty交付检查点

2026-10-16 19:22，优先处理使用方原样安装失败。已读漫画线程反馈及原始HttpMessage崩溃报告；
旧端implementation/additionalRuntimeClasspath仅满足开发运行，未把HTTP依赖交付给普通Loader。
Forge1.20.1和NeoForge1.21.1改为标准Jar-in-Jar，分别只嵌入4.1.82.Final和4.1.97.Final的HTTP模块，
不传递捆绑其余Netty。单独核对官方清单：26.1.2已有4.2.7.Final、26.3已有4.2.16.Final的HTTP模块，不重复打包4.1.x。
Fabric API是既有必需依赖，改用Loom include随单个产品JAR交付；补充Netty上游许可/NOTICE，保留Fabric API内部许可。

新增独立安装Runner，使用官方原版版本JSON、原始客户端JAR、Loader正式安装器/配置和普通运行库；
缓存复用须核对官方哈希，游戏classpath全部在验证自有安装目录，不引用Gradle项目输出、组件源码或开发依赖目录。
每次游戏目录为空，只放一个最终产品JAR，不放单独runtime_core/runtime_safety、手工HTTP库或探针Mod；
只通过正式接口读取/退出，不使用截图、多模态、Computer Use、宿主输入或其他线程实例。
初次NeoForge1.21.1安装工具把父版本原版JAR文件名带入profile，和官方ignoreList不符而重复加载Minecraft；
修正为使用profile的version_name.jar后复测，不把这次工具故障当作产品崩溃或通过。

独立Forge1.20.1启动实际暴露EffectIconRenderMixin将开发lambda名称设为remap=false的发行故障。
原有映射表包含对应SRG方法，恢复正常Mixin重映射并检查最终refmap，不改采集行为、不删除Hook或降低require断言。
重建后重新冻结同一候选，先参考端、再Forge1.20.1、NeoForge1.21.1、NeoForge26.1.2、Fabric26.3逐端运行。
五端全部实际到标题、生成endpoint、固定进程/实例身份握手、USER读取、原生语义Quit和退出码0；无崩溃，端点删除。
原始类加载日志证明旧两端HTTP来自最终JAR的jarjar路径，现代三端来自对应原版4.2.x运行库；产品Transport来自各自mods中的最终JAR。
退出HTTP五端均可能先返回500，实际正常退出另以自有进程退出码、Vanilla Stopping日志和清理判断；该产品体验问题仍列已知问题，不假称已修复。

alpha生成和固定分发均新增五端独立安装硬门禁，拒绝缺失、开发类路径、过期源码/JAR、非正常退出或已改变的结果；
实际验证缺少证据时不会改写生成清单/手册。37项分发合成检查、2组安装证据断言及现有Java/Companion/静态门禁通过。
628项产品Java、52项Companion/网页、五端verifyRelease及严格合规通过；当前安装源码指纹为
14534c813115f1eee049136ff4cb404f44f4c84c40251b94ae2262b953ec1333。
首份缺失安装门禁的20261016-1844-b6d7829f已撤回，旧latest送入回收站，撤回登记在快照之外，原七文件未改；
通过门禁的新七文件快照为20261016-1922-14534c81，latest指向新批次，清单包含安装结果摘要、实际类来源和结果哈希。

非阻断反馈已进入状态页：旧手册的有界USER描述已同步，世界条目双击selected失效、加载覆盖层就绪表达、
Sodium自绘语义缺口和退出HTTP体验留待后续，不在本次扩大实现。使用方旧临时HTTP补丁由其自行移除，本线程没有改其他实例。
这次仅证明发行安装的标题/READ/清理，不是完整世界、UI、Peer或人工验收；两个领域与完整Showcase仍IN PROGRESS。
正式Persistent Write为0，不暂存、不提交、不推送。

### 2026-10-16 Test管理拒绝与实例预设检查点

参考端先补Test Block/Instance原生配置与动作包的最终发送前管理拒绝，以及UI Debug文本responder的修改前拒绝，
再移植到NeoForge26.1.2和Fabric26.3。实际数据包单测覆盖全部七种Test Instance动作，不解析或执行测试程序。
原生服务器探针仅在owner thread计数管理包，并读取已加载固定方块的摘要；它不加载区块或改变世界。
三现代端各五项Native/真实MCP专项在旧分辨率通过，Done/QUERY/RESET均零相关服务器包，
OPERATE设值拒绝时客户端文字/Resource Revision不变，无Lease和输入dispatch；客户端USER草稿不承诺回滚。

初次验证脚本误以为Reset保留编辑器、多按Esc进入暂停页；随后纠正Native错误字段与JSON对象引用相等的测试假设。
不降低包计数或存值断言。新增OpenAPI说明一度造成description重复并使生成失败，合并至原字段后重新通过，未跳过验证。
635项产品Java、52项Companion/网页、五端构建/verifyRelease、Schema生成与严格合规通过。

用户追加实例标准时，停止旧矩阵的排队父进程，不强制结束游戏；参考端旧配置世界full完成44项并正常退出，
不把它冒充新1920标准证据。自动客户端改为1920×1080静音，人工展示2560×1440、主音量50%、音乐0和RecRivenVI，
原生prepare任务经工具组件统一应用声音。Showcase的自动client-multiplayer与人工client角色分离。
按Modrinth下载并核对Custom Skin Loader和Zume；前者五端有匹配，后者只有旧两端匹配，现代三端跳过。
五个人工目录已实际准备正确音量和相应JAR；不据此宣称附加Mod实际运行兼容。自动/发行单JAR门禁不安装这些模组。
33项预设检查、78项Showcase工具检查与原生准备验证通过；新1920三现代端Test专项各五项通过并清理。
五端完整领域套件及新安装工具的最终发行检查正在重新运行，未在本检查点提前声明完成或发布新alpha。

### 1920标准最终候选回归检查点

2026-10-16 21:54，63851031264c4eef243ee420c87b394254e5b93bf65af39fa7ada12943b892e4候选完成串行矩阵。
五端世界full各44项Native/13条真实MCP；UI依次51/52/52/52/52项，源码/工具哈希复核一致，固定副本恢复、4 GiB与正常退出通过。
五端又分别从全新正常Loader安装单个最终JAR，实际1920×1080、USER READ、类来源、无崩溃、退出码0与端点清理通过。
自动实例全局音量0，独立发行安装不附带Custom Skin Loader/Zume；没有Computer Use或正式Persistent Write。

新自动Showcase角色的FreshFixture仍写死client路径，改为按实际角色校验和归档，原数据非破坏保留。
参考端完整Runner smoke实际失败：Showcase Toasts (Fixture)不存在，返回UI_NODE_NOT_FOUND；正常清理退出，失败不抹去。
复核三个Neo的AutomationProbeScreen确实没有该按钮，Forge/Fabric有；登记M-11缺口，不为了展示修改产品或跳过断言伪装成功。
随后单独执行五项CoreSmoke，FreshFixture真实归档client-multiplayer后重新创建安全副本，五项通过，输入清零、取消无晚到输入、正常退出。
这不代表17项完整自动展示通过。再以人工预设执行READ案例，日志确认RecRivenVI和Custom Skin Loader 15.1，
启动参数2560×1440，退出后的主音量0.5/音乐0，正常退出；不宣称用户视觉验收或其他四端附加Mod兼容。
预设工具33项、Showcase工具79项；新准备探针只有options.txt的目录送入回收站，无用户存档被删除。
临时只读汇总命令有两次括号语法错误，改用逐文件哈希复核完成；不将工具错误计入产品通过或重新生成假结果。
当前UI、世界与完整Showcase仍IN PROGRESS，不暂存、不提交、不推送。

22:03完成最终Spotless、check、verifyRelease和严格合规；在线首次因Forge Maven TLS握手失败，离线锁定缓存重跑成功，未改TLS、版本或绕过检查。
最终JAR结构检查五端通过，无重复共享类；生成器重核五端当前源码/制品与独立安装后发布20261016-2203-63851031。
同批七文件、latest指针与生成手册均核验，旧批次文件未改；清理的latest备份进入回收站。
新快照绑定五端开发世界full及独立安装标题/READ，不冒充完整打包游戏流程、Peer或人工验收。

### 2026-10-16剩余工作计数与HUD一致性检查点

状态页新增每包剩余必需项数和粗估工时，按有编号的52个验收组核对，区分46组实现/覆盖与6组最终复验/交付。
预计先收口M-07，再修M-11真实Runner缺口；其余广度与Peer、最终交付按依赖推进，不因新增统计把P改V或READY。
M-07核对发现新版Dialog非命令custom action的最终守卫证据缺失，保留为07a待办；未做攻击复现，不宣称可利用或已经安全。
已有生命HUD采样完成五端编译、Schema及Hook声明一致性；五个精确原生绘制委托计入实际replacement总数并单独披露。
新增逐端ASM测试读取实际编译处理器，约束先原参数调用原绘制恰好一次，再观察。初轮因只读取不可见注解而未找到Redirect失败；
改为同时读取两种注解表后五端均通过，未减少五个委托或放宽原调用断言。Hook静态门禁仍禁止其他观察Hook的替换/取消。
647项产品Java、52项Companion/网页、五端check/verifyRelease、Schema、Control/Hook工具、严格合规零失败零警告通过。
早期参考端生命专项11项不是当前收尾哈希证据；新五端Live、独立发行安装及alpha刷新仍待，不改变已发布2203快照。
本检查点未关闭任何完整工作包，两个领域和完整Showcase仍IN PROGRESS；不提交、不推送，正式Persistent Write为0。

### 2026-10-16 M-07有限拒绝目录收口

补充CUSTOM_UI_CALLBACK最终包拒绝，以及function/schedule、clone/place/loot、item from/modify的保守未决分类；
不读取服务器模板、战利品表、修饰器或源物品来猜测效果。原有data来源拒绝、Structure/Test拒绝和Jigsaw部分提交真值保留。
三现代端对实际原生自定义动作包做单位检查，共享门禁验证零发布；真实聊天富文本语义点击另核对服务端回调接收数未增加。
首轮测试错误地要求一段富文本只有一个命中区域，在原生字形高度/间距分段处失败；检查原生active area采集后，
改为选择具有独立nodeId的真实语义区域，不猜整段文字矩形，不减少拒绝、零包或清理断言。后续三端均实际通过。
五端实际完成player-command专项11/11/12/12/12项、command-configuration各16项；
三现代端另各5项Test拒绝，共13份专项153项，包含真实MCP，均当前相关源码/工具哈希匹配、固定副本恢复、held清零和正常退出。
此处未把程序/模板执行解析、全世界连锁效果沙箱或第三方回调适配说成支持；这些按明确安全限制交付，不以未知效果冒充成功。

批测在26.1.2启动时发现真实崩溃：HUD委托的At描述符仍是Renderpearl类型，但该端原生调用使用Blaze3d RenderPipeline。
原始crash-2026-10-16_23.09.11-client.txt保留在其隔离实例crash-reports内；批测立即停止，没有继续启动Fabric。
只修该端五处描述符，新增测试读取原生Gui字节码核对真实调用与注解目标；测试初版误把注解At数组当单值，
修正数组读取后通过，未放宽目标存在断言。随后26.1.2及Fabric三项专项全部实际通过；
未受影响三端证据逐文件重核当前哈希后复用，不拼接过期结果。

本检查点将M-07五端P→V，07a/07b清零，总剩余验收组52→50，状态页同步更新估算与下一顺序。
V仍待M-10共同最终候选复验；未刷新alpha、未新做独立制品安装、未进行人工验收。正式Persistent Write仍为0，不提交、不推送。

收口后再次执行Spotless、check、verifyRelease及严格合规通过；652项产品Java零失败，52项Companion/网页通过。
十三份M-07报告在最终格式化后逐文件重核当前哈希，均匹配；五端端点无残留，自有游戏进程已清理。

### 2026-10-16 使用方优先切片与退出响应

按外部评审25号指令只读三方记录和Alpha使用登记，建立能力×使用Target×使用方×原工作包的表及C-01～07有限交付顺序。
三方实际使用主要是1.21.1 NeoForge，公开Provider/USER输入试验另包含26.3 Fabric；使用登记只有空批次目录，未代使用方登记或运行其实例。
GPU捕获、Sable适配与外置寻路保留为使用方可信扩展，不纳入Core或E1/E2/E3实现；完整50组范围不变。

已启动矩阵完成参考端UI及世界full，随后Forge生命专项在伤害后等待七颗完整心失败，队列停止，其他端未启动。
日志显示原版拒绝伤害，目标无敌；等待可观测服务器tick跨过新入场保护后，不改原断言，生命/护甲/食物/氧气/坐骑及显隐实际通过。
首轮修正后的隔离网页bootstrap另遇fetch failed，未因此跳过消费者；增加5秒超时和端口/底层原因诊断后有界复测，
Forge的hud-vitals专项11项、真实MCP与隔离网页读取、固定基准恢复和正常关闭通过。原连接失败未复现，不宣称已定位或修复其根因。

共享HTTP取消不再落入普通500：非关闭取消为409 REQUEST_CANCELLED，Runtime关闭为503 RUNTIME_SHUTTING_DOWN；
均标记executionOutcome=not_confirmed、retrySafe=false，不把原生已分发点击虚报为未执行、回滚或成功。
真实HTTP在native press内关闭及切模式取消的单测通过；Companion结构化保留新字段，Native/OpenAPI、手册、玩家说明和更新日志同步。
发行安装工具拒绝内部500，明确503结构/连接先断与实际退出分别判断；三组正负证据检查通过。

Spotless/check/verifyRelease/严格合规通过，654项产品Java零失败、53项Companion/网页及Schema生成通过。
参考端26.3 NeoForge使用最终单JAR全新官方Loader安装，endpoint、握手、USER READ通过，Quit实际返回503结构并自然退出0，无crash且清理完成。
产品指纹bb2bad0957add87a52c853de6309ba9add1da5344602515dab139a369454ebe0，JAR SHA-256为0e4972340e03ac6b9137997872ae57e3d15898580095c6e86d97d91e062ffd6b。
此时使用Target的新候选运行、双击缺陷、Provider专项与五端完整/安装仍待；未刷新alpha，不将中间里程碑或完整Goal宣称READY。
未提交、未推送，正式Persistent Write调用保持0。

### 2026-10-17 使用方语义双击与原生消费闭环

先在26.3 NeoForge的固定副本真实复现未选中世界条目double_click返回UI_TARGET_INVALIDATED，错误字段为selected。
仅对未选中的list_item，在第一次原生按下回调内、owner尚未让出时复核所有其他前置条件并接纳自身选中变化，一次手势至多一次。
之后外部选择变化、位置/身份/Screen/尺寸变化仍拒绝；不把剩余点击投递给替换后的界面，也不承诺回滚首击效果。
确定性测试覆盖正常两击及移动期间、回调内其他字段、首次释放后的外部变化；PointerGesture共29项通过。
五端实际world-selection专项各3项通过，均语义nodeId、未选中起点、进入固定世界、held清零及正常保存退出，不靠截图或坐标绕过。

新增consumer专项及full中同一组，实际在参考26.3 NeoForge、1.21.1 NeoForge和26.3 Fabric各9项通过：
USER移动/相对视角、READ无Lease截图/录制Artifact、资源重载、下界/主世界往返、保存退出/重进标记读回，
既有公开Provider V2各owner读取、类别/schema/独立scope拒绝、Arm/OPERATE/资源版本、延迟500ms修改在80ms请求超时后至少650ms无晚到效果、真实MCP读取及清理。
每端记录5个1920×1080当前PNG的传输/哈希和前后UI/overlay状态，不以哈希或renderer READY宣称第三方图像收敛；诊断命令与typed mutation不冒充无污染玩法证据。
未运行使用方实例或其GPU扩展，未新增client/render mutation dispatcher、寻路或Render Forensics。

full alpha生成器新增具体使用方样本及同批完整UI清理门禁；UI包装工具只给匹配本轮PID/端口/日期的结果补实际退出字段，不改旧结果。
最终安装新增Provider公开类真实加载来源及四个owner descriptor目录检查，与世界内SPI正负控分别记账；未以目录代替制品世界mutation。
3组consumer/UI证据与4组安装证据正负检查通过，缺失、旧作用域、伪造清理、越权证据、晚到效果或错误500不得发布。
首次复核命令把--strict放在check之后导致CLI拒绝；改为紧随verifyCompliance后执行，未修改任务或降低检查。
Spotless/check/verifyRelease、656项产品Java零失败、53项Companion/网页、Hook、工具检查、80篇Markdown及严格合规零失败零警告通过。
产品冻结指纹c4e30de08536971a7404a50cbe515c82ed77e79fe730cee3127ef4ae85298c73；此时启动单队列执行五端新安装再完整UI/世界，尚不发布alpha。
C-03/04/05开发接口分支转V，完整50组范围不变；不提交、不推送，正式Persistent Write为0。

同一冻结指纹的五端全新官方Loader安装随后全部通过：只有对应发行JAR，无开发classpath或单独补依赖，
实际解析HTTP、Transport及三项Provider公开SPI，四个Provider目录身份/schema/owner符合预期。
五端Quit均返回503 RUNTIME_SHUTTING_DOWN、executionOutcome=not_confirmed、retrySafe=false；
随后实际退出码0、Stopping日志、无崩溃及endpoint/端口清理均通过，不将取消回复宣称成功RPC。
C-02转V；完整UI/世界矩阵仍在串行执行，尚未发布快照。

第二批外部评审要求保持本批冻结，先发布当前C-01/06/07，再处理限流回执、玩家命令反馈、组合就绪、
USER玩家状态与命中诊断；第三方UI缺口和未来扩展API只登记。没有将这些新项插入正在运行的候选。

随后参考端完整UI61项通过，但包装工具漏写清理字段；只读复现ConvertFrom-Json将testedAt生成为UTC DateTime，
再次Parse其无时区显示字符串后早了8小时，误判为旧回执。仅修验证工具时间比较，使用生产函数验证UTC、
offset和string三种当前表示及旧时间/错误PID/端口拒绝；不改产品或手工补JSON，参考端UI须完整重跑。

随后完成冻结c4e30de0五端世界full各53项及14项MCP；UI为60/61/61/61/61项，参考端完整重跑后清理回执亦通过。
五端全新官方Loader、单最终JAR、1920×1080静音及4GiB检查通过；公开SPI真实来源、四个Provider目录、503未确认退出、自然退出0和端口/endpoint清理通过。
spotlessApply/check/verifyRelease/严格合规通过；发布七文件快照20261017-0209-c4e30de0，latest、五JAR/清单/手册SHA-256重新核验一致，无分发警告。
旧1922/2203保留，已撤回1844和latest替换备份移回收站，可恢复；没有改写旧快照或证据。
第一批使用方优先READY已简报，但不是完整UI/世界/Showcase或人工PASS；未提交、未推送，正式Persistent Write为0。
第二批报告随后只读核对，登记8个独立收口组，P3/P4只登记；未操作或启动使用方实例，未把其旧反馈当当前新失败。

2026-10-17第二批先在生产HTTP复现429缺少等待/额度字段，再修共享桶快照与Native/MCP/网页传递；
四项确定性预算、实际HTTP/认证负例及Companion结构测试通过，五端各两项标题专项通过，输入增量0且正常退出。
命令先写500ms/16条时间关联方案，再接入五端客户端聊天基线采样；不读取旧历史、隐藏文本或服务器日志。
首轮参考夹具使用26.3不存在的time query daytime而被原生树拒绝，改用五端均有的gametime，不放宽命令门禁。
参考端Native与MCP实际取得普通查询和服务器NBT路径错误文本，未知命令输入前拒绝，旧聊天不混入。
共享单测的两个旧代理未实现feedback完成信号而超时，同步代理后原断言通过；新增取消注册屏障覆盖反馈子任务竞争。
一次Companion套件有两项本地HTTP连接失败，增加具体地址/原因诊断后有界复测57项通过，未将未证明根因写成已修复。

页面组合就绪先由实际HTTP复现旧screen条件在overlay下仍通过，再增加同一新鲜快照的Screen/overlay/窗口/目标检查；
原有screen语义不变，只有显式ready=true启用组合，不预留未来点击。区块等待复用已有block.available条件，不新增强制加载。
五端各六项命令/就绪专项已通过，源码/基准哈希、清理与Persistent Write=0均核验；B2-03/04转V，B2-02继续既有消费者回归。
这些新增候选尚未完整冻结或发布，不改写c4e30de0的七文件快照，也不把增量专项冒充整个领域READY。

随后既有消费者专项先暴露悬空夹具的重力时序：新增命令反馈等待后玩家已落到-60，80高度的标记超出USER半径。
只给诊断Arrange增加稳定石头平台，不修改原版物理、读取半径或断言。重跑继续实际复现跨维度采样误拒绝，
改为上下文变化后提前结束反馈、保留未确认来源且不采新世界；参考端九项消费者流程通过，维度反馈分别在39/34ms以明确原因结束。
新鲜页面条件保留原screen行为，新增ready显式启用；玩家三字段则由当前Runtime的200 USER响应确认缺失后才实施。

USER玩家三字段随后在参考端Native、Provider、State Frame、Wait及真实MCP中通过，原生游戏模式变化可观察，
未连接世界时明确unknown。遮挡目标先实际复现WORLD_TARGET_NOT_HIT缺少命中事实，
再补有界CLIENT_KNOWN命中位置/方块/面/距离；保持原有射线和输入许可，不为诊断多发一次输入。
原生失败路径的空release_all本来就增加一次总派发序号，测试分别记录这一次清理和零玩家输入，
没有把序号强改为零。参考端十二项反馈专项通过后，逐端适配其余四端并完成五端编译。
共同门禁随后672项产品Java、58项Companion/网页及严格合规零失败零警告通过。

第一次新full在参考端的限流工具失败：先积攒所有短管线ID再读取，早期结果已被有界Operation表淘汰；
后续退出请求也因刚耗尽的pipeline额度被明确429拒绝。Runtime备用收尾正常退出0且端口/端点释放，
该批保留FAIL，不拼接前序成功项。工具改为及时读取每个已接受结果，并按公开额度等待补满后进入下一案例；
不增加产品额度、不重放拒绝输入、不扩大Operation表。工具检查及严格合规再次通过，固定副本重跑另行判断。

修复后的参考端限流专项及完整重跑通过，随后串行执行其余四端完整世界套件。
五端均67项/18项实际MCP，包含第二批14项具体验证和第一批9项消费者；
逐文件核对224～229份源码/工具哈希、不可变基准恢复、正常退出和Persistent Write=0。
B2-02/05/06及手册B2-07转V，第二批剩余一组共同UI/制品/交付；完整50组不因此减少。
当时尚未刷新第二批alpha，不用开发Runtime通过代替UI或独立最终JAR安装。

随后参考端UI首次full在讲台准备的clear命令返回REQUEST_CANCELLED，日志显示原生命令已处理，
不能称为回滚或零效果。原工具失败时先尝试回标题、失败后未及时释放Lease，后续quit任务冲突。
失败后接口读取held keys/buttons均0、没有真人撤销；不据后来任务过期猜测最初取消原因。
经本实例正式接口正常保存退出，Stopping及端点/进程释放已确认，原full仍保留FAIL。
仅补测试错误的Native回执/控制/输入快照与失败时finally释放；没有更改产品取消合同。
同一固定副本的特殊菜单七项定向通过且正常退出，未复现取消；带诊断的full重跑另行判断。

带诊断的full在同一处再次复现取消；失败当时明确modeTransitionReason=task_expired、READ、held=0，
expiresAtMillis早于deadlineAtMillis，证明命中60秒心跳TTL而非300秒总预算。
连续HUD诊断命令新增反馈等待，暴露Runner只在ui()发任务心跳、command()/key()和案例边界漏发的问题。
只给这些显式测试操作补现有任务/Lease心跳，保留原300秒上限及180秒空闲案例边界换任务；
不自动复活过期任务、不清人工撤销、不提高Runtime预算。Quit前再次确认标题，失败仍释放自己的Lease/任务。
两次失败均经正式接口正常退出，未强杀其他进程；世界五端证据不依赖这些UI工具，源码绑定仍有效。

心跳及退出清理修正后，工具检查、80篇Markdown和规范2.0.2严格检查零失败零警告通过。
参考端full从固定副本完整61项通过，越过原故障点，源码未改变、退出码0、端点和端口清理通过；
未增加固定延时、总预算或忽略取消错误。其余端串行共同回归继续，不把参考端成功推定为五端通过。

随后其余四端UI完整回归通过，五端依序60/61/61/61/61项，连同世界各67项/18项MCP逐文件源码绑定、
固定副本及清理审计通过。工具增加ui/key/command心跳与保留预算/安全Quit的防回归检查，
再次执行spotlessApply/check/verifyRelease/严格合规通过；产品Java仍672项、Companion/网页58项。
开始同批五端全新发行JAR安装，未生成或发布尚未通过安装门禁的alpha。

五端全新Loader、单个最终产品JAR安装随后全部通过，实际HTTP/Transport/SPI来源、四个Provider目录、
1920×1080静音、USER READ、Quit取消解释、自然退出0和端点/端口清理齐备。
旧端使用嵌入匹配HTTP库，26.x使用游戏原版库；无开发classpath、额外产品依赖JAR或探针模组。
安装范围仍是标题/读取/SPI，不冒充完整制品内玩法或用户视觉验收；开始同批清单/手册与固定快照生成。

2026-10-17发布第二批七文件不可变快照20261017-0555-c7fed238，产品源码指纹
c7fed238cd6129fd0a80fcca5d7e5f9e2c86073a5117f8515cc2d35bca0efcb2。
独立核对五个发行JAR、manifest、Agent手册及latest完全一致，packagedRuntimeVerified=true，发布无警告。
按发布时的实际显式登记及保留最近三份策略，未登记在用的1922旧批次和临时latest备份移入回收站，可恢复；
2203的mcmod_playtest登记保留，c4第一批快照原样保留，未手工撤销消费方登记。
对话简报新字段、目标、快照ID、禁止盲重放及后置条件；第二批八组归零，完整50组不变，继续M-11/03，不结束Goal。

### M-11 原生Toast与完整展示

三Neo在probe中补入默认关闭的Showcase Toast夹具，按各版本实际原生API适配；未增加产品路由或发行依赖。
Runner增加自动全套预设，逐帧等待实际发布的Tutorial/System原生文本，不能以按钮存在或点击回执代替。
参考端26.3 NeoForge的17项全套执行通过，静音、4GiB、恢复及正常退出齐备，不等于视觉/真人验收。
随后1.21.1全套在语义拖拽报Pointer carried stack changed，已正常清理退出，队列停止；不能复用参考端结果推定通过。
核对五端发现Forge 1.20.1与NeoForge 1.21.1的advanceDragGuard错误放在键盘回调，原生鼠标拾取后没有推进自身状态。
先增加按生产方法体检查的门禁并观察到FAIL，再将该调用移至鼠标回调，与其余三端保持一致；不放宽外部变化校验。
Runner另检查拖动后USER槽位与所持物品总量为8，不能只凭Operation完成宣称分配和恢复正确。后续构建及五端重跑另行判断。

修正后两旧端build/test及五端生产方法体门禁通过，参考端、1.21.1和26.1.2分别完整17项通过。
三Neo每端两次触发Tutorial/System夹具并读取原生绘制文本；逐文件核对203/201/206份源码工具与实际结果一致，
held=0、Artifact内容/校验和、PERSISTED读取、取消无晚到输入及正常退出/端口释放通过。
11a与11b分别收口，状态页从50减到48组；Forge/Fabric全套仍待完成，不提前将M-11标V。
现有c7固定快照不包含旧两端拖拽修正，保持原样并注明已知限制，新候选须另行验证/发布。

随后Forge与Fabric亦分别完成全部17项，五端逐文件核对201～206份源码/工具；
每端两次原生Toast、8个石头守恒、持久化三类读取、真实Artifact内容与校验和、取消和清理均通过。
五端端点已移除，无本线程游戏残留；最大堆4GiB、自动静音预设，Persistent Write=0。
11c关闭，M-11整体P→V，剩余47组；仍需最终候选复验及用户物理/视觉验收。
新增UI DEBUG专项仅扩展既有五类setter的拒绝/恢复/零输入证明，未新增操作或权限；其运行结果另行记录。

03a五端专项各5项/56份原生应用或拒绝样本通过，均从不可变副本恢复、零玩家输入、正常退出并移除端点。
Java新增排队UI mutation取消/撤Arm的owner许可测试与共同check/verifyRelease/严格合规通过；不是完整领域READY。
继续审查发现UI公开value截断为2048而setter接受4096，原生表示中的同前缀/不同尾部复用了资源token；
参考端先增加回归并观察失败，再将完整本地文本的有界指纹只纳入DEBUG版本状态，公开前缀和玩家输入保持不变。
指纹使用原始UTF-16单元，避免替代编码让不同孤立代理单元等价；超预算/缺owner拒绝修改，expectedValue比较完整原生值。
尾部、UTF-16和预算单测通过，参考端Live及其余Target适配待执行；未因先前56份短值样本将03a标完成。

随后五端完成相同守卫及Native接口复验，各5项/56份样本，所有五类setter均有实际应用、错误类型、
值/旧版本拒绝和恢复，输入增量0；源码/工具逐文件核对、正常退出0及端点移除通过。
完整文本指纹只加入DEBUG资源版本内部状态，USER字段不增；专门投影单测和Native文本节点实际guard可用断言通过。
根spotlessApply/check/verifyRelease/严格合规通过，684项Java零失败、Schema/Companion及五端制品静态核对通过。
03a闭合，剩余46组；书本/告示牌及其余03b/03c/03d仍单独待办，开始同候选完整UI/世界及独立发行安装检查点。

检查点参考端UI进行中又核对到自动验证工具debugUi结束会直接acquire，未逐次检查人工撤销。
经自有实例的正式control/task end主动停止当前任务，实际Operation记录task_ended/cancelled；
该full保留FAIL，正常退出0、端点和端口释放，无物理Esc或伪造人工撤销事件。
随后统一自动acquire的发送前检查：manual latch存在即停止，缺新鲜状态也拒绝；请求绑定检查时的原生modeVersion，
防止status与acquire之间发生人工撤销后被清除。保留调用方已有旧pin，不暗中刷新；不改Runtime、MCP或对话同意合同。
工具纯函数检查与生产State的status/acquire竞争单测另行运行，完整检查点从固定副本重新开始，不拼接已中断的full。

继续核对完整套件的独立消费者，书本/旁观者MCP及其隔离网页也须逐次绑定相同检查。
第二次参考端检查点经正式task end停止，保留FAIL且正常退出0、端点删除；未发生真人撤销，也不虚报该次full通过。
验证专有RuntimeClient装饰器只拦截自动acquire，保留调用方旧pin及其他请求；网页/Companion产品的显式重新同意路径不变。
世界Native/MCP、自动onboarding和独立安装入口一并核对；工具测试检查撤销后零acquire发送、READ继续可用及参数透传，生产State单测负责验证最终原子pin拒绝。

该检查点冻结产品指纹3d6e3fca5705127c9711c190a2a42fe76c231830ef955badaa34a44ab00a9aa7。
五端串行完整重跑UI60/61/61/61/61项、每端56份DEBUG样本，世界各67项及18项实际MCP均通过；
逐项源码/工具哈希匹配，固定基准恢复、正常退出及端点/端口释放通过，无本线程Java残留。
685项产品Java、58项Companion、Schema、严格合规等共同门禁先前已通过，发行安装及固定分发另行核验，不用开发Runtime替代。

同候选五端随后全部完成全新正常Loader、单个产品JAR安装：实际HTTP/Transport/SPI来源、标题与USER读取、
五端503 RUNTIME_SHUTTING_DOWN未确认回执及自然退出0、端点/端口清理通过，无开发classpath或额外补库。
共同spotlessApply/check/verifyRelease/严格合规再次通过，静态包装无重复共享类。
生成并固定发布20261017-0914-3d6e3fca，核验五JAR、清单、手册七文件及latest；发布无警告，保留最新三份及2203在用登记。
生成器已自动调用发布器；命令随后再调用一次只完成幂等核验及latest更新，没有覆盖已发布七文件。
两份latest临时备份及本线程五个旧独立安装目录按任务移入回收站，可恢复；没有删除在用快照或用户实例。
M-03a的完整文本版本守卫和旧两端拖拽修正随本批进入固定分发；M-11的17项证据仍绑定其独立候选，不冒充当前全目录或人工验收。
状态页切换为当前批次和剩余46组，继续03b等广度工作，不结束Goal，不提交或推送，正式Persistent Write保持0。

03b参考端增加MultiLineEditBox和告示牌原生TextFieldHelper typed草稿路径；只增加已知编辑器适配，保留Arm、完整文本版本、owner线程和零玩家输入。
原生helper校验在许可前不改文字或选择区、告示牌行token独立并绑定完整草稿的两项新增单测通过。
首次接口专项书本与普通告示牌通过，悬挂告示牌因测试文案Debug draft超出其原生行宽而拒绝，结果保留FAIL；正常退出0及端点清理通过。
随后只将测试文案改为Debug，保留过宽W串的拒绝断言和生产原生validator；从固定副本重跑，不将该拒绝伪称产品故障或通过。

参考端6项/78份样本完整通过后，逐端适配旧两端BookEditScreen的pageEdit/titleEdit helper与旧滚动API、
26.x的MultiLineEditBox；不存在Test管理类的旧端不引用该类，现有现代拒绝合同保留。
五端各6项/78份样本、20次实际修改/恢复，255/254/254/259/254份源码工具哈希、零输入增量、焦点和草稿恢复及清理通过。
Root check/verifyRelease/严格合规通过，695项产品Java零失败、58项Companion和原有Control/工具回归通过。
03b闭合，剩余45组；完整消费者03d和特殊呈现03c仍未关闭，不把USER网页编辑或原生接口专项冒充完整DEBUG消费者。
固定分发仍3d6e3fca，不向其写入03b新源码/手册；本增量后的同批检查点按三增量/共享修改/冻结规则进行。

03d首次参考端真实MCP检查失败：纯UI Debug batch恢复已完成，但退回READ后以USER查询Operation仍返回DEBUG草稿。
测试保留FAIL，正常退出0及端点释放；没有将该次计为消费者通过。生产根因是Operation只记录worldDiagnostic，纯UI未进入读取分类。
后续改为创建时固定UI/世界领域集合，get/wait及operation Wait/Assert共用读取与mode generation发布守卫；
诊断取消回执不返回result/error/errorCode/errorDetails，Companion自动等待所有Debug批次明确使用DEBUG，纯UI不增加世界读取要求。
新增真实HTTP单测先复现USER与缺读权限仍返回诊断数据，再修复；混合域、迟到结果、取消和只写权限不授予读权限单独核对。

03d的c3d2ff45参考端10项/56份Native/26条消费者专项和完整UI61项/134份Native/26条消费者、世界67项/18条MCP通过，清理及源码绑定通过。
随后Forge完整UI在新增加的书名消费者空值恢复失败：Ctrl+A被原版标题键盘入口忽略，只退格一字而未清空；保留FAIL、正常退出0、端点删除，其他Target未继续启动。
两端原生字节码均确认titleKeyPressed只处理Backspace和Enter；节点补充追加型编辑能力与完整值元数据，通用Automation用原生退格，不调用Debug/helper替代玩家。
通用替换另加实际清空后置核对，失败不追加新字；确定性测试先复现四项失败，再覆盖Unicode码点、完整值/策略拒绝、取消/换页和普通全选五项通过。
本修复改变源码指纹，c3参考端结果不计为新候选；固定快照仍3d6e3fca，不改写旧结果或已发布内容。

5cbde8d5候选五端定向消费者专项随后全部通过：各10项/56份Native样本/26条消费者结果，9类MCP DEBUG和9类网页USER隔离、4类编辑器两条USER输入恢复，源码工具哈希、退出0/端点删除及无本线程Java残留核验通过。
旧两端实际记录backspace_from_end、现代端select_all，空书名恢复均通过；705项产品Java、58项Companion/网页及五端check/verifyRelease/严格合规通过。
五端测试模型补齐原生文本值随全选/退格/换行/字符的变化，不放宽取消或换页断言。03d闭合，剩余44组，03c仍缺特殊呈现目录。
共享修改后的完整矩阵和全新安装未完成前不刷新alpha，不用c3或3d旧结果证明5cb候选。

5cb完整检查点实际完成参考端、Forge 1.20.1、NeoForge 1.21.1的UI/世界，及26.1.2的完整UI；逐文件哈希与清理匹配。
随后运行会话中断，26.1.2世界日志止于交易夹具、没有Stopping，新结果未完成；Fabric未执行，不把旧文件计为本批通过。
恢复核实本线程游戏/Runner均已退出、端口及实例锁释放后，仅将26.1.2的过期endpoint移入回收站，其他线程Java未触碰。

M-01/01b的只读调查随后用当前已编译生产Transport、临时HTTP夹具复现：仅世界DEBUG读权限仍可从事件和录制取得UI修改详情。
新增正式HTTP回归先得到4失败/1通过：还确认无ui权限录制调用UI owner，以及READ→OPERATE→READ后发布旧UI捕获。
实现独立UI详情检查、录制排队发布复验、缺UI授权不捕获及UI mode generation守卫，树/resolve/UI条件共用；
污染标记不因详情拒绝被清除，世界事件读取仍可用，模式/Lease/Arm和玩家输入合同未改变。
补充resolve与Wait/Assert往返负例后7项生产HTTP回归通过，连同既有Recording/Debug Operation回归通过。
新指纹60ab9c5b；5cb完整检查点不证明该源码，固定分发保持3d6e3fca并明确旧快照不含此修复。

格式化后候选指纹e83283fc；spotlessApply/check/verifyRelease/严格合规通过，五端发行构件更新，712项产品Java零失败。
58项Companion/网页及Schema、Control/Hook和验证工具检查通过；共享变更后的五端游戏和独立发行安装尚待执行，不刷新清单。

e83283fc随后五端完整UI均通过：Forge60项、其余61项，各134份Native样本/26条编辑器消费者；世界各67项/18项实际MCP通过。
逐文件源码工具哈希、退出及端点/端口清理核验通过。首个独立安装在启动前回收旧目录失败，未启动新JAR；
Windows报未经授权的操作，目录最长300字符、无readonly/reparse且owner正确。原工具结果留在IN_PROGRESS，发布仍被门禁拒绝。
补同卷短路径退休/回收与故障保留，仅改安装验证工具；实际合成长路径回收、模拟失败原字节保留、owner/Target/父路径和占用锁拒绝通过。
清理异常现生成明确FAIL/stage，新 helper 纳入工具哈希；产品JAR/源码未改，五端游戏证据不能被该启动前错误说成发行运行失败或通过。

2026-10-17该检查点随后完成：短路径回收修复后，e83283fc五端全新官方Loader/单发行JAR安装、标题无覆盖层、USER读取、公开Provider SPI来源、正常退出0及端点清理均通过。
旧两端HTTP实际来自嵌入的匹配Netty，26.1.2/26.3使用原版运行库；没有项目开发classpath或单独补依赖。
离线测试账号的Realms认证错误保留在日志，不作为本地Loader崩溃或Realms功能已通过。未运行制品内完整玩法或人工UX验收。
补充工具修复后的spotlessApply/check/verifyRelease/严格合规通过：712项产品Java零失败/错误/跳过，58项Companion/网页，80份Markdown及合规零失败零警告。
五端现有完整UI和世界的逐文件源码/工具/基准及清理再次审计通过，未为纯工具回收修复重复运行相同产品候选。
生成器实际验证五端完整结果与独立安装后发布20261017-1504-e83283fc，同批七文件、手册与清单哈希及latest核验通过，无分发警告。
保留最近三份及登记在用的快照；超期未登记20261017-0209-c4e30de0与latest临时备份移入回收站，已发布内容不覆盖。
正式Persistent Write为0，未暂存、提交或推送；两领域仍未READY，剩余44组，继续03c等原定广度工作。

M-03c随后将四类自绘Screen卷轴接入既有ui.scroll.set，参考端先实现范围/身份/版本守卫和本地Native显示同步；不选择配方/交易或发送玩家输入。
首个专项因工具误读未传给Node的DebugUi参数，在测试前退出；改为检查Runtime实际scope。
第二次创造卷轴通过，工作站就绪失败；独立场景遗漏完整菜单套件的地面准备，补齐自有平台并增加失败时的方块/玩家上下文诊断，未放宽就绪断言。
随后6流程/40份Native样本通过，但审计发现新场景仍被既有editorHandoff过滤，消费者为0；未将该次结果计作MCP/网页通过。
修复过滤并要求启用的每种卷轴都必须产生唯一的MCP DEBUG和网页USER隔离样本；缺样本直接失败。
逐端移植保留各版本原生API，4a532eb9五端check/verifyRelease及严格合规通过，722项产品Java零失败/错误/跳过。
当前参考端再次实际通过6流程/40份Native/8条消费者，退出0和源码工具哈希一致；其余四端专项继续，不刷新alpha或提前关闭03c。

4a532eb9卷轴专项随后五端通过，各6流程；Forge39份Native/6条消费者，其余各40份/8条，均有四类实际状态样本、原生恢复、零输入增量与退出0/端点删除。
Forge固定石头配方无滚动范围，只验证设0和拒绝移动，不把该分支记作成功滚动或MCP修改；其他已启用卷轴均有实际MCP DEBUG与网页USER隔离样本。
1.21.1首次在隔离网页bootstrap出现fetch failed，未记录端口及cause；同候选有界复测通过，保留故障记录，不声称根因已修。
只读核对本机TCP动态端口1024～15000；另以正式startUiConsole生产入口和自有6000监听确定性得到fetch failed、cause=bad port，随后关闭自有监听。
这证明浏览器端口限制的独立缺口，不能追认为原失败已确认同源；后续归M-10诊断/兼容，未在本批改变网络或权限合同。
五端源码/工具逐文件哈希核验通过，正式Persistent Write为0、未操作外部实例。当前增量仍未覆盖全部03c字段或完整领域。
当前产品源码为4a532eb9，旧e83283fc安装证据已被发布校验器实际拒绝用于新候选；未刷新build清单或发布新快照，使用方继续复制1504固定快照。

2026-10-17下一增量复用ui.text.set增加本地HUD标题/副标题/动作栏，绑定实际HUD/世界/Component身份、完整原生样式和文字与GUI上下文，不将自然计时当作文字版本。
Native setter只接受有界纯文字，可能重置生命周期或颜色动画；文字恢复不冒充原格式/计时回滚，不发送玩家输入/命令或修改服务器/世界。
普通JUnit调用原生样式Codec先失败于未Bootstrap注册表；补Bootstrap又确认真实NeoForge需要FML Loader，未伪造Loader或降低断言。
将完整内容/样式/null/前缀尾段/预算10项断言移至真实probe，投影/私有元数据隔离保留JUnit。
首次probe与产品使用同包，实际启动被JPMS split package拒绝，未进入世界；只在验证VM参数和启动身份后停止84660失败进程，wrapper/daemon和其他线程未动。
断言改到独立probe包的测试Mixin，新增split-package helper目录已移入回收站；产品不依赖probe，生产JAR排除probe。
参考端随后4流程/28份Native/6条真实消费者及10项原生断言通过。逐端移植时Forge文字编码按实际FriendlyByteBuf API适配，其余使用原生context-free Component codec。
五端共同构建/发行、727项Java零失败/错误/跳过、58项Companion和严格合规通过；候选0525cef0，未固定分发。
Forge实际专项首次缺probe文件，未计通过；核对生成运行参数确认UI验证缺世界验证已有的--mixin显式配置。
只改UI validation.toml的游戏参数，不改产品/日常运行；修正后Forge4流程/28份Native/6条消费者和10项原生守卫通过，其余四端按新配置继续复验。

0525cef0本地HUD专项随后五端通过，每端4流程、28份Native样本、6条实际MCP/网页消费者及10项真实Loader内容断言。
逐文件源码工具哈希一致、正常退出0、端点删除、零Persistent Write；未用同包伪Loader或单端结果替代其他端。
M-01/01b继续审计批次异步出口，确认每项沿既有observer经过UI读权限过滤；新增三项实际HTTP批次事件/录制正负例，
连同单次/迟到读取及Operation读隔离共15项通过。没有因此改动产品权限，也不据此关闭完整01b。

共同冻结复验先遇上游Maven TLS握手失败，未更改依赖或检查；使用同版本缓存离线执行spotlessApply/check/verifyRelease/严格合规通过。
产品Java共730项零失败/错误/跳过，Companion/网页58项和严格合规零失败零警告；常规弃用及注入故障测试警告不冒充合规警告。
0525cef0参考端完整UI62流程、202份Native、40条实际消费者通过，262份源码工具哈希匹配、退出0/端点删除，随后继续串行共同矩阵。

0525cef0参考端世界full67流程/18项实际MCP亦通过，235份源码工具哈希匹配、正常退出与端点清理；
使用方优先9项及第二批14项证明由正式alpha校验器再次通过。包装静态门禁五端通过，新增HUD产品类各一份、probe不进入发行JAR。
Forge下一轮恢复副本时Shell回收不可用，工具按既有非破坏回退将自有旧副本保留于该Target的retired目录，再从不可变基准恢复；未永久删除、未覆盖用户数据。

冻结矩阵运行期间只读审计03c剩余呈现：使用五端当前实际classpath中的构件逐一javap核对，
SystemToast均有reset，TutorialToast均有updateProgress/hide；SystemToast forceHide仅1.20.1缺席，26.3标题改存格式化lines。
将真实入口与副作用写入既有UI目录，明确未接入、不能假标只读；没有修改已冻结的产品源码，也没有以静态API证明Live。

0525cef0 Forge完整UI61流程、201份Native/38条消费者通过，258份源码工具哈希与退出0/端点删除一致，随后进入Forge世界full。
仅增强alpha消费者校验器，不改运行器/产品或既有结果：四类卷轴、三项HUD的完整拒绝/恢复、同进程同时间窗10项Loader守卫及实际MCP/网页证据均成硬要求。
五项校验器单测覆盖丢样本、失真回滚/输入声明、旧PID/Target/时间及消费者缺失，当前参考端与Forge真实结果通过增强检查。

0525cef0 Forge世界full67流程/18项实际MCP通过，233份源码工具哈希、正常退出与端点清理一致；
9项使用方优先和14项第二批证明通过正式校验器。共同产品指纹仍为0525cef0、559份事实文件，随后进入1.21.1 NeoForge完整UI，未启动并行客户端或刷新alpha。

1.21.1矩阵运行期间补齐M-01/01b的实际HTTP录制ZIP正负例源码：缺UI读权限时单次/批次详情不能进入下载包，
但世界事件和污染标记保留；授权包保留UI详情，USER列表/下载仍不得看到DEBUG录制。只改runtime_core单元测试文件，
实际检查它不在当前UI/世界运行源码哈希中；因客户端仍在运行，未另开Gradle，新增两项尚未编译/执行，不计PASS。

0525cef0的1.21.1 NeoForge完整UI62流程、202份Native/40条消费者通过，257份源码工具哈希及正常退出/端点删除一致；
新alpha校验器另外要求16类真实MCP DEBUG和16类网页USER隔离、四类卷轴及三项HUD同进程内容守卫，实际结果通过。
状态页用单张当前冻结表区分各端未执行/进行中/通过，后续进入同端世界full；当前仍未以旧安装证明发布新候选。

0525cef0的1.21.1 NeoForge世界full67流程/18项实际MCP通过，232份源码工具哈希匹配、正常退出与端点清理通过；
9项使用方优先和14项第二批证明通过正式校验器。至此三端完成当前候选既有完整UI/世界套件，串行进入26.1.2 NeoForge，未声称五端完成、刷新alpha或执行新Artifact单测。

0525cef0的26.1.2 NeoForge完整UI62流程、202份Native/40条消费者通过，257份源码工具哈希、正常退出/端点删除一致，
并通过增强alpha证据校验的16类MCP/网页、四类卷轴、三项HUD及同进程10项Loader内容守卫；随后进入同端世界full。
因此当前为四端UI、三端世界通过，Fabric及独立安装未完成，不把专项或历史结果拼成五端通过。

2026-10-17 18:15:39，26.1.2世界full实体交互期间收到native-input的human_manual_revocation，
运行器随后得到REQUEST_CANCELLED和USER_MANUALLY_ENDED_CONTROL，未清除latch或自动重新取得Lease。
本轮结果为FAIL、26项检查且cleanShutdown=false，不能按原有67项或旧候选结果计通过；串行后续Fabric未启动。
18:22～18:23通过实例身份握手核对PID88936仍属于本轮隔离目录：mode=READ、Lease available、
pressedKeyCount=pressedButtonCount=0、activeGestureId为空、hostCursorCaptured=false；两次inputDispatchSequence均为5681。
未发现本轮新崩溃报告。自动退出也需要接管导航，因此保留客户端及故障证据，等待用户明确重新同意；
未重启客户端绕过撤销，未刷新alpha，不把该中断声明为产品崩溃或完整人工撤销专项PASS。

2026-10-17用户明确同意重新接管并继续测试后，通过实际实例身份握手和当前modeVersion调用正式acquire，
确认TAKEOVER及reconsentRequired=false，再释放该Lease并由现有Native关闭流程正常保存退出。
旧PID88936/88112/50520均退出、10996监听和endpoint.json消失，latest.log有世界保存及Stopping；
原矩阵句柄53018明确终止为失败且未启动Fabric，不重写原FAIL结果。
旧游戏完全退出后启动spotlessApply/check/verifyRelease/严格合规离线共同门禁，准备从固定基准重跑中断项。

恢复后的共同门禁通过：732项产品Java零失败/错误/跳过，58项Companion/网页和严格合规零失败零警告。
UiAsyncReadIsolationTest共12项通过，新增两项实际HTTP ZIP下载的单次/批次负例和授权正例未改产品、权限或证据合同。
产品指纹复核仍为0525cef0、559份事实文件；随后仅从固定基准重跑26.1.2世界full，并排入Fabric完整UI/世界和五端独立最终JAR安装。
中断日志另存于仓库外诊断目录，不修改绑定结果凑通过，不启动第二个并行客户端。

0525cef0的26.1.2 NeoForge世界full从固定副本重跑67流程/18项实际MCP通过，230份源码工具哈希、
固定副本、正常退出与端点清理一致；9项使用方优先及14项第二批证明通过正式校验器。
串行进入Fabric UI。另补实际DEBUG WebSocket UI读权限正负例源码，要求同步修改之后的有序世界事件标记，
不靠固定sleep判定未收到UI详情；仅测试文件变化，未改变当前运行候选，尚未编译执行，不计PASS。

Fabric完整UI随后34项时失败：HUD消费者交接结束旧Task后，内层异常尚未恢复调用方Task，
HUD finally执行command清理，其heartbeat以undefined taskId发出，覆盖了原消费者异常。
当轮退出0、cleanShutdown=true、endpointRemoved=true、nativeRevocations=0、held=0；不是本轮再次人工撤销，也不证明Runtime任务合同失效。
原失败JSON及日志留存仓库外诊断目录。只改验证器：交接无论成功或失败均按现有READ/人工撤销守卫恢复新Task/Lease，
保留原始消费者错误并同时报告恢复错误，外层不覆盖已保存的首个失败；人工撤销后仍不得begin/acquire。
直接提取执行生产函数的4项工具测试覆盖成功、原始失败、双失败与人工撤销分支，均通过，不以这些替代Live。
旧客户端全部退出后，新增实际WebSocket正负例及共同门禁通过：UiAsyncReadIsolationTest13项、产品Java共733项零失败/错误/跳过，
58项Companion/网页与严格合规零失败零警告。未改产品源码，指纹仍0525cef0。
由于UI运行器变更，既有四端UI结果仍保留但不再充当当前工具证明；先排入Fabric HUD专项，随后须五端UI共同复验。

交接修正后的Fabric HUD专项4流程/28份Native/6条消费者通过；随后完整UI62流程/202份Native/40条消费者通过，
257份源码工具哈希及退出0/端点删除匹配，正式alpha UI校验器通过四类卷轴、三项HUD及10项同进程Loader内容守卫。
原始消费者异常未在这两次有界复测中复现，未将它追认为已知端口问题或Runtime缺陷已修复。
串行进入26.3 NeoForge完整UI复验；其余世界及独立安装尚未完成，不刷新alpha或提前关闭完整M-01/03。

0525cef0参考端26.3 NeoForge随后完整UI62流程/202份Native/40条消费者通过，262份源码工具哈希、
退出0/端点删除及正式alpha UI证明一致；未再次出现交接异常，原失败不改写为通过。
再次逐文件核对四端既有世界full，源码工具仍匹配，各67流程/18条实际MCP、9项优先及14项第二批证明通过。
随后串行进入Forge完整UI；未重复启动客户端，Fabric世界及五端独立安装仍待完成。

0525cef0 Forge完整UI使用修正后的交接工具再次通过61流程/201份Native/38条消费者，258份源码工具哈希、
退出0/端点删除及正式alpha UI证明一致；无范围的切石机卷轴保留拒绝分支，没有按其他端伪造消费者修改样本。
串行进入1.21.1 NeoForge完整UI，当前三端UI、四端世界完成本批相应复验，未刷新清单或发布新快照。

0525cef0的1.21.1 NeoForge完整UI使用新交接工具通过62流程/202份Native/40条消费者，257份源码工具哈希、
退出0/端点删除及正式alpha UI证明一致；旧两端书名空值恢复仍走已验证的原生退格路径。
串行进入26.1.2 NeoForge最后一端UI复验，当前四端UI、四端世界通过；不以这四端代替第五端或独立安装。

0525cef0的26.1.2 NeoForge完整UI使用新工具通过62流程/202份Native/40条消费者，257份源码工具哈希、
退出0/端点删除与正式alpha UI证明一致。随后逐文件复核五端完整UI全部匹配当前工具和产品，
Forge为61/201/38，其余各62/202/40；各含四类卷轴、三项HUD及同进程10项原生内容守卫。
串行进入Fabric世界full，五端独立安装尚未执行；这是既有完整套件共同复验，不是全部原版UI或整个领域READY。

0525cef0 Fabric世界full67流程/18条实际MCP通过，230份源码工具哈希、不可变副本恢复、正常退出与端点清理一致。
再次逐文件核对五端UI及五端世界，正式校验器的9项优先和14项第二批证明全部通过，正式Persistent Write为0。
对照现有验证说明的完整消费者隔离目录，13项UI异步/HTTP/实际ZIP/WS、5项Operation以及世界HTTP投影/授权回归，
结合五端当前Native/MCP/网页正负例关闭M-01/01b；01a正式目录和01c Peer仍缺，M-01保持P。
原50组中共7组闭合，剩43组（实现/覆盖37、最终复验/交付6），不缩减整个目标或据此宣布READY。

参考端26.3 NeoForge全新官方Loader、单一最终JAR安装随后通过；实际endpoint/USER READ/Provider SPI来源和正常退出0，
产品源码指纹0525cef0，JAR SHA-256为11a6a7efffae1414298eb2e7b4af6c3a79d9e4e21f5df2c5b0580a0affa8e3e3。
Forge官方安装器在DOWNLOAD_MOJMAPS步骤因SSLHandshakeException/远端EOF失败，尚未启动游戏或加载产品。
保存原始installer.log及失败JSON到仓库外诊断目录；无遗留Java，按原官方安装流程从新目录做一次有界重试，
不更换Loader/Java、不关闭证书或哈希校验、不跳过安装门禁，其他三端尚未启动。

Forge从全新目录按原官方流程的一次有界重试通过，随后1.21.1、26.1.2 NeoForge及26.3 Fabric独立安装均通过。
实际installed-evidence校验器核对五端最终JAR/native输出哈希、源码指纹0525cef0、工具哈希、
官方安装classpath、单一外部Mod、共享Transport与三项公开Provider SPI实际来源、标题/endpoint/USER READ、1920×1080/4 GiB及退出0。
旧两端HTTP模块来自匹配Jar-in-Jar，26.x来自各自原版模块；无开发classpath或手工复制class，不用JAR结构检查代替Live。
未发现本轮Java残留，最后共同门禁和alpha七文件固定分发仍须执行；先精简状态页的旧候选流水为当前冻结摘要，历史保留在本阶段记录。

2026-10-17 20:41，0525cef0候选最后共同执行spotlessApply/check/verifyRelease/verifyCompliance --strict通过，
733项产品Java零失败/错误/跳过，58项Companion/网页、4项UI交接工具检查及五端构建、Schema、Control/Hook通过，严格合规零失败零警告。
正式生成器验证五端既有完整UI/世界、全新单JAR安装与源码/工具/JAR哈希后发布
D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/20261017-2041-0525cef0，五JAR、清单和手册七文件全部复核一致，latest.txt已指向它。
保留最近三份及实际.usage登记；本次mcmod_playtest仍登记1504-e83283fc，未改写其在用记录。
过期且未登记的20261017-0555-c7fed238及发布器的.latest备份移入回收站可恢复；未永久删除世界或其他线程资料。
所有本线程测试Java已退出、端点已清理；正式Persistent Write为0，不暂存、不提交、不推送。
本次关闭01b，仍剩43组；两领域与完整Showcase均未READY。下一增量为03c剩余Toast的typed目录，不重复深化已关闭读取消费者隔离。

2026-10-17 21:32，System/Tutorial Toast typed增量先在26.3 NeoForge参考端实测，再逐端适配：
ui.toast.reset显式双字段纯文字，TutorialToast的ui.slider.set显示进度，ui.toast.hide单向原生隐藏。
首次实际Loader内容守卫失败揭示默认Language的FormattedCharSequence返回值与ClientLanguage不同；
构件字节码证实默认Language在完整visit后返回false。修正错误的完成布尔假设，sink仍不提前停止，预算异常仍拒绝；
完整样式、2048字符后尾部及预算拒绝7项实际Loader检查随后通过，没有绕过内容保护。
Fixture Screen起初因验证令牌缺fixture scope被正确拒绝；仅在DebugUi验证子进程显式授予所需Fixture权限。
原生通知退出后下标重排使旧resourceKey被正确拒绝，验证器改为等待实际owner离开后再发现下一目标，未放宽资源保护。
Forge匿名SystemToast探针因旧构件参数名重复而编译失败，改为有显式构造器的具名probe子类后通过；不修改原生构件或产品行为。
五端各5流程/29份Native/4条实际MCP DEBUG与网页USER隔离，源码工具、固定副本、退出0/端点删除及正式Persistent Write为0。
Forge原生没有SystemToast.forceHide，只证明明确拒绝；probe结束自身计时夹具，不冒充产品隐藏功能。
Schema、Companion、四端新增Java测试/探针编译及工具、严格合规检查通过；初次工具失败是新增专项缺DebugUi的测试前置条件，修正测试显式授权后通过。
alpha发布门禁增加同进程Toast守卫、真实修改/恢复、旧条件拒绝及两个实际消费者种类，7项合成正负检查通过，不作为新的Live结果。
Runner随后增加原始processStartedAt随证明保存，尚需完整UI/世界与最终JAR共同冻结；未发布新快照或回写0525cef0。
M-03保持P、03c合法目录审计尚未收口，总剩43组，不宣布两领域或完整Showcase READY；本线程所有游戏已正常关闭。

2026-10-17 21:52，本轮正式分类目录收尾通过：5项SurfaceContractCatalogueTest核对78项OpenAPI操作、
生产Operation描述、12类条件实际授权、Provider实际准入以及注册Target/实验Adapter/生产路由的对应关系。
RuntimeCoreBoundaryTest增加真实HTTP的实验inventory可用与不可用分支，Companion实际listTools核对24工具的metadata与typed action。
修正目录遗漏的begin_task/heartbeat_task/end_task及保留实验诊断的Target声明，未改变Runtime权限或暴露范围。
01a闭合，01c Peer主体/撤权仍未完成；最初50组现剩42组（实现/覆盖36、最终复验/交付6），不将整个M-01或领域改为READY。

随后本轮最终spotlessApply/check/verifyRelease/verifyCompliance --strict通过，245任务、1分41秒；
753项产品Java零失败/错误/跳过，58项Companion/网页、4项UI交接工具检查，五端发行JAR元数据/200项必需条目/探针排除通过，
Schema、Control/Hook、工具与rumdl通过，严格合规零失败零警告。构建已重建build/release，但Toast后的同候选完整UI/世界与独立安装未执行，
不刷新alpha清单、不发布新快照、不以旧0525cef0证明新增操作；latest继续指向20261017-2041-0525cef0。
按用户明确要求，本轮收尾后暂停任务及Goal，等待项目规划调整，不开始后续功能、游戏或人工验收。
工作树全部保留且未暂存、未提交、未推送，正式Persistent Write仍为0；本线程游戏已退出，不关闭其他线程实例。
