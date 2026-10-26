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

2026-10-18，用户批准协作控制新目标后重定双里程碑规划：各模式独立授权USER/DEBUG读取，
OPERATE不接管真人、TAKEOVER可做独立DEBUG，人工Esc只撤销接管；完整三部署重新纳入双里程碑。
既有剩42组的范围改为新规划58组，增加模式生命周期/回读、命令可用性/批次、版本链与完整服务器/消费者覆盖；
原已关闭分支与证据保留，新增范围不写成旧实现失败。Fake Player自有体系与第二游玩界面只做独立可行性研究。
本次只更新当前计划、设计与相关说明，不改源码、历史证据、快照或验收结论，不恢复暂停Goal、不启动游戏、不提交或推送。
同日用户撤回将部分命令划为DEBUG的决定；当前目标中全部命令操作统一USER，原版主体权限、实际效果与证据来源仍独立，不以逐条命令分类扩大实现范围。
同日进一步批准OPERATE执行获准的非接管原生操作，首批为以真实玩家身份提交命令，不打开聊天框、不改草稿/焦点或借输入Lease；
现有聊天键入手势仍需TAKEOVER，不直接删门禁。该决策归入01e/07d及相关取消/展示组，不增加完整非接管玩家动作承诺。
本次仍只更新计划与设计，其他原生动作先审查输入/上下文依赖；未实现、未启动游戏、不提交或推送。

### 2026-10-18 协作控制并行增量与视觉修复

主线程负责共享授权/生命周期、Native/OpenAPI/Companion及串行验收，同模型、同思考等级子代理分别完成UI、世界和命令/部署切片，另设只读审查。
交付本地命令树发现、最多32条顺序批次、逐项未确认回执；正式资源inspect映射、授权后安全stale计数及方块版本链；
原生UI节点的typed修改描述和26.3本地FriendToast隐藏。服务器补全、其他资源链及完整真人协作未因此关闭。
真实HTTP批次deadline测试覆盖首条保留、第二条pending/work取消及无第三条提交；shutdown锁顺序避免在持状态锁时等待独立工作取消。

Forge完整回归发现Gson ABI缺陷：原版2.10没有JsonObject.isEmpty，异步操作完成路径因此未进入终态。
修改为size检查，产品共享组件编译/测试明确使用2.10 API基线并增加实际测试运行库检查；模板工具Gson版本不变，不向游戏内嵌另一份Gson。
修复后Forge完整67项/18条MCP通过。另一次持续挖掘报告主手上下文变化，未在有界原样复测中重现，根因未确认；
固定夹具在破坏木板后复用已有落物/库存清理与独立客户端tick屏障，再开始黑曜石超时断言，未放宽生产保护或替换预期错误。
参考端官方安装曾发生依赖下载TLS失败，保留原失败，单次全新隔离目录重试后通过；没有改证书、哈希或依赖。

用户随后补充模式快速切换动画及收缩文字越界。共享文字改为从当前三层混合重新定向，宽度与文字使用同一权重；
五端仅文字绘制使用当前面板内容区的原生scissor并finally恢复，原500ms曲线、Pointer、蓝边、权限和截图隔离顺序不变。
新增7项生产绘制/时钟确定性测试。参考端真实接口运行9次快速切换，间隔112～155ms，held input=0且正常退出；不作人工视觉结论。
此后五端移植并移除无消费者的旧双文字接口。静态门禁随生产委托位置更新，继续核验共享零alpha保护及五端native clip接线，不把字符串检查当运行证据。
视觉修复后的新候选另作最终复验；此前不同指纹的完整套件不拼接成新alpha，不修改已发布快照，不恢复暂停Goal、不提交或推送。

本次显示收尾候选五端各完成mode-transition：每端三轮常规展示后九次快速切换，共45次，实际间隔109～150ms，
各214～219份源码/工具哈希与本次工作树匹配，held input=0，正常退出且端点/端口释放。
spotlessApply/check/verifyRelease/verifyCompliance --strict通过，838项产品Java、60项Companion/网页，合规零失败零警告；
五端发行JAR与对应原生构件一致。该专项不替代新候选完整UI/世界、独立安装或人工观感；新alpha尚未发布。

### 2026-10-18 进度画布、资源版本链与服务器补全

并行推进03c选中原生进度画布的二维平移、06i其余五种既有typed资源链、07c独立服务器补全。
参考端先完成真实接口，再移植四端；主线程统一合同、构建与隔离实例，产品与工具冻结后逐端复验。
进度操作使用原生scroll及完整{x,y}、实际Tab/Screen/图边界/GUI版本，首次居中由原生完成，固定轴不强制归零。
补全使用独立负事务ID与future，保留真人待完成补全优先；单飞、250ms间隔、1500ms期限，原版预算仍共享。
已提交只读查询不可撤回，取消后不发布结果，不宣称真人并发已实测或零网络影响。

资源链实测发现MAX_HEALTH尚未实例化时缺少正式base读取；五端补原生supplier-backed base/effective值，
按实际registry ID去重并保留排序，不创建AttributeInstance、不改USER。每端新增2项生产与原生字节码检查。
后续一次夹具因缺独立debug.entity权限被正常拒绝，仅为资源专项显式配置实体/BE/Menu各自scope，不放宽产品门禁。
参考端14项、2条真实MCP及safe stale/恢复证明通过。验证器与消费者恢复只接受本次确认的值及token，外部变化或未知回执不猜测覆盖。

进度专项首次位于Save & Quit之后，因玩家上下文已结束而失败；移至退出前并增加脚本顺序检查后，
参考端固定轴、二维平移/stale/恢复、替换Screen三项typed流程及实际MCP/网页隔离通过，正常退出。
五端编译/测试、协议生成、Control/工具、发行及严格合规通过：910项产品Java、61项Companion/网页，
每端213个必需发行条目；自动检查不等同新候选五端Live或人工验收。
冻结指纹ee01a2218d417b857236226b805fe0dbd6b32401ddb3877c3629f5110eaebc95，589份产品/构建事实，
随后启动四专项、完整世界/UI与全新单JAR安装的串行矩阵，未拼接实施阶段不同候选证明。
自动Goal仍暂停，未提交、推送或执行正式Persistent Write。

同批复验期间收包复核发现补全超时依赖tick清理：若迟到回包先于下一tick处理，仍可能发布。
通过本产品task end正常取消正在执行的参考端UI套件，原始REQUEST_CANCELLED保留；客户端正常退出、端点/端口释放，不将该次写成UI通过。
随后收包直接校验单调时钟，五端接线和共享测试覆盖未tick时1499ms成功、1500/1501ms超时、零授权发布及迟到结果不能复活。
因此重新构建与冻结，不复用ee01候选作为修复后证据，也不发布混批alpha。

修复后spotlessApply/check/verifyRelease/verifyCompliance --strict全部通过：911项产品Java、61项Companion/网页，合规零失败零警告。
新冻结指纹b0a95fd267ae2ecc507c8adb653494c4f25108c6796b334982693e562e801ee8，589份产品/构建事实文件、每端213个必需发行条目。
随后重新启动五端四专项、完整世界/UI与全新单JAR安装，上一候选记录不作为本批证明。

b0a95fd2参考端四专项8/8/8/14通过后，完整世界套件在握手阶段失败：自动端口6679被Node标准Fetch按WHATWG端口规则拒绝，未开始世界Act。
Node关闭回退同样被禁止端口阻断；主线程改用PowerShell调用本产品已验证端点的acquire与Quit语义按钮，客户端正常Stopping、Gradle退出0，端点/端口清理。
此为实际连接缺陷而非世界能力通过；保留失败日志，修复自动端口的有限安全选择后重新冻结和复验，不绕过Fetch限制或发布混批alpha。

自动端口修复使用标准禁止表，最多8次实际bind候选，每次坏候选同步关闭，仅最终安全监听发布；固定禁止端口和绑定异常明确失败。
新增3项生产选择/耗尽/固定端口负控，既有真实五实例检查最终HTTP可用端口。重建时旧测试夹具曾随机选到5060/5061后作为固定端口而失败；
普通HTTP测试改用生产自动分配，固定占用测试仅筛选合法测试前提，不放宽产品拒绝规则。本记录保留失败原因，后续完整重跑通过不改写为首次通过。
spotlessApply/check/verifyRelease/verifyCompliance --strict通过，914项产品Java、61项Companion/网页、合规零失败零警告。
冻结指纹2a4c09d77956ad3d3a2fb8c73aae5811610fc2c7b74a84393a4fe3ac8f2cf35e，589份产品/构建事实文件，重新执行同候选五端矩阵。

2a4c09d7参考端四专项8/8/8/14及完整世界67项通过；UI完整套件在系统Toast的实际MCP恢复时失败，客户端随后正常退出。
原生reset同步改文字/重排并置changed=true，下一tick更新lastChanged且清除changed；canonical包含这两个字段，导致同内容6692→6693。
消费者完整token保护因此拒绝恢复，未以新token盲目覆盖。暂停后续Target和alpha分发，先评估显式reset代次以保留真实同值重设失效，不把正常tick处理当外部修改。

参考端实现被动reset代次、完整canonical共享采样及原生probe后，target编译/测试通过，首次Runtime却捕获IllegalClassLoadError并退出：
初版标记接口嵌在Mixin包内，游戏类加载器禁止直接引用。该实现未移植或分发；保留09:53:55崩溃记录，改将纯Java标记接口放到runtime_core产品组件后再验证。

共享UiToastResetSource接口修正后，参考端target编译/测试/probe通过，真实ui-debug-toast专项通过并正常退出。
9项probe使用本线程私有原生ToastManager/真实驻留实例、生产canonical及版本验证器，证明native update消费后token稳定，同值reset使旧token拒绝；
实际MCP SystemToast reset/完整token恢复、Tutorial进度与原生单向隐藏均通过。随后授权其余四端移植，恢复guard未放宽。
新Hook是被动reset计数，不取消/替换原生调用；原cancellable/replacement/draw计数不因新增一项passive Inject而误加。

四端移植后统一spotlessApply/check/verifyRelease/verifyCompliance --strict通过：929项产品Java、61项Companion/网页，合规零失败零警告。
五端发行各214个必需条目及探针排除符合，另逐JAR核实core.UiToastResetSource与SystemToastResetMixin存在、旧Mixin嵌套接口不存在。
旧双端probe实际调用完整原生render，仅probe私有画布屏蔽绘制，诚实记录no_pixel_canvas；26.x调用真实update。两者都不冒充像素或人工验收。
冻结指纹07848af56e85782a2ca800848e128bce00ce3770422fda45dba899384b724a78，595份产品/构建事实；先专项复验，再重新执行同候选五端完整矩阵。

随后同候选串行完整矩阵通过，顺序为26.3 NeoForge、1.20.1 Forge、1.21.1 NeoForge、26.1.2 NeoForge、26.3 Fabric；一次一个1920×1080静音4 GiB自有副本。
按项目Target顺序，完整UI为65/66/66/67/67项，Native诊断样本250/251/251/254/254份、编辑器消费者44/46/46/46/46条；
各端9项Toast原生守卫、实际MCP完整token恢复及二维画布3分支通过。Forge无SystemToast.forceHide，按原生缺席明确拒绝。
五端各完成mode-collaboration 8项、command-discovery-batch 8项、server-completion 8项、resource-version 14项；
资源专项每端含2条真实MCP，实际覆盖六类正式inspect/值与版本/stale/重新读取/安全恢复，DEBUG Act零玩家输入，不取得Lease。
07c与06i闭合，剩余规划组57→55；03c只完成画布子项，建议列表、旁观及剩余HUD/Toast owner仍未闭合。

世界完整套件五端各67项/18项实际MCP通过；最终发行JAR逐端全新官方Loader目录，仅一份产品JAR，无开发classpath，
标题、endpoint身份、握手、USER READ及SPI来源均通过。五端自然退出0、端点删除、端口释放，无当前验证客户端残留；不等同最终制品内完整世界/UI或人工验收。
SERVER补全的真人future优先有生产结构和确定性测试，未做人类并发Live；已提交查询不可撤回，服务器预算仍共享。
未操作日常实例、Prism或重要存档，不使用Computer Use，不恢复暂停Goal，不暂存/提交/推送，正式Persistent Write仍为0。

最终spotlessApply/check/verifyRelease/verifyCompliance --strict再次通过：929项产品Java零失败/错误/跳过、61项Companion/网页，
80篇Markdown检查通过，严格合规检查1485份文件、零失败零警告。五端各214个必需发行条目，源码指纹仍为07848af5。
发布器逐文件复验当前源码、工具、五端完整UI/世界、20项协作专项及全新安装后，生成五端JAR、alpha-manifest.json和AGENT_GUIDE.md，
发布到D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/20261018-1221-07848af5/；七文件、清单/手册哈希与latest核验通过，分发无警告。
保留最近三份及显式登记在用批次；旧20261017-0914-3d6e3fca和替换latest的临时备份移至回收站，可恢复，未永久删除。
当前两领域与完整Showcase仍IN PROGRESS，不以本次alpha或自动矩阵宣布全目录READY/人工PASS。

### 2026-10-20状态页证据归档

本节于2026-10-20从状态页搬入，保留当时已发布bfdda69c、冻结候选f2dc15c9及42/65检查点的逐项证据、反馈分诊与剩余目录记录。
以下暂停安排、旧性能判据和后续计划只代表搬迁前台账，不是当前要求；40号指令已恢复推进、暂缓性能检测并调整验证层级，现行结论以[状态页](status-milestones.md)和[执行计划](plan-execution.md)为准。
这里只搬迁文字与导航，不改原阶段正文、原始JSON、历史失败、测量数值或验收结论；NOT_MET仍为NOT_MET。

#### 2026-10-20当前候选

- 核对日期2026-10-20，HEAD与本地origin/develop均为577904fb460f773d09398c2f72f1e0e4cb35a043；保留已有改动并验证批次A～D，不暂存、提交或推送。
- 当前冻结试用候选源码指纹为f2dc15c927ca7bf77dd7a0ffee52e605718366d4e16b1433319a24101fdcadf3，612份产品/构建事实文件。
  共同构建、1244项产品Java、64项Companion/网页、协议生成、工具门禁及严格合规通过，零合规警告；每端235个必需条目核验通过。
  修正消费者后的矩阵已保存42/65组：前三端各11组世界场景、26.1.2完整世界场景，以及实际哈希/完整断言仍匹配的四端UI和四端全新单JAR安装；余23组未执行，按用户资源安排暂停。放置消费者分开即时预测与后续USER回读，完整字段/成对状态及服务器未确认负控保留；不复用工具失效结果。尚未刷新清单/手册或发布此候选；下列已发布快照仍是旧批次。
  物理Esc批量取消重入的原FAIL保留；两阶段取消的真实队列负控修前失败、修后通过。新候选参考端真人复验：active及两项queued均cancelled/human_manual_revocation，held=0，8070ms无迟到，三次控制请求持续返回重新同意要求；不代表五端统一人工UX验收。
- 已发布快照源码指纹为bfdda69ccda9d29de0857401b1c1af6b7f5a2fea321b4b665cce03c820929554，597份产品/构建事实文件；当前工作树继续实施B的native输入epoch，不再等同该已发布候选。自动端口按标准Fetch兼容性有限选择，固定禁止端口明确失败；build/release不是跨线程分发入口。
- 生产接线已覆盖本地/显式服务器命令补全、最多32条NATIVE顺序批次、六类正式世界inspect版本链，以及选中进度画布的原生二维平移。共享读取/独立工作、授权摘要和UI实际owner描述保留；具体完成组仍以本页矩阵为准。
- SERVER补全使用独立事务和future，真人待完成请求优先；每连接单飞、250ms间隔、1500ms期限。已提交只读查询不可撤回，取消后不发布结果，原版服务器预算仍共享；不宣称真人并发Live已验、零预算影响或服务器执行成功。
- DEBUG玩家属性快照包含原生supplier提供的最大生命base及有效值，不创建AttributeInstance；原生读取名与typed写入名保持明确映射。测试恢复仅接受本次已确认改值与完整token，未知外部变化/响应丢失保留并报告。
- 已发布候选构建/自动门禁：1032项产品Java、63项Companion/网页、协议生成、Control/工具、五端发行及严格合规通过，合规零失败零警告；每端217个必需条目及探针排除符合。宿主样式参数隔离另含12项三端生产分支/接线/缓存/声明测试，静态门禁42项正反例通过。新的epoch候选另验，不复用这些结果宣称已通过。Gson 2.10仍为共享产品API基线，不替换游戏库或模板工具版本。
- interaction-baseline五端各8项通过：每端60个真实gameplay样本、实际丢弃物品、语义槽位悬停及取消清理；continuous-input五端各10项通过，含实际Native/MCP强停止和严格释放等待。源码/工具匹配、held=0、正常退出；同候选完整UI/世界、20份协作专项及五份全新单JAR安装均通过，不以它们代替完整领域或真人验收。
- 当前可复制快照为20261019-1245-bfdda69c，位于D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/，latest.txt指向它；五份JAR、清单与手册七文件一致。使用方只复制不可变快照，保留最近三份及登记在用批次，不混取build/。
- UI模式动画从当前三层文字/宽度权重连续重定向，文字使用面板当前内容区的原生裁切；500ms曲线、Pointer、蓝边、权限和截图隔离边界不变。本轮参考端物理Esc只验输入取消，完整人工视觉、五端物理输入和真人合作验收未执行，不据自动测试关闭这些步骤。
- TAKEOVER的独立DEBUG读取不再被模式互斥阻断；USER仍为有界客户端摘要，未加载、投影和预算限制仍明确返回。不掌握使用方原始请求时，不推定其所有区块“不完整”原因已解决。
- 成功Pipeline提供单调时钟timing；回调入口不等于dispatch或服务器效果。当前候选已完成端的分位数见本页当前时序摘录，未完成端不以其他批次结果代替。HTTP/效果另测、采样间隔不计入耗时，不构成跨机器SLA，也不证明持续吞吐。
- 同Lease强停止、输入代次和input.released条件保留，纯gameplay不重复构建完整UI/Menu快照。新候选的世界/玩家/连接epoch、区域分页和位姿流已接五端；当前工具绑定的实际通过范围见增量矩阵，其余端等待恢复后验证。换维度的原始加载未就绪失败保留；GUI观看节奏没有调整。
- 已修复已完成PipelineFuture取消失败仍触发cleanup的问题；两项确定性负控在修复前失败、修复后通过。完成/取消竞争只由实际获胜路径交接drained，不引入回调monitor锁；修复后的共同候选已重新完成五端矩阵和独立安装。
- 26.x宿主光标样式隔离已进入当前快照：仅TAKEOVER将原生请求归一为默认箭头，不影响虚拟圆球、位置或捕获；1.x无该API不加无效Hook。Hook诊断准确公开参数替换而非取消输入，OpenAPI枚举同步增加MIXIN_MODIFY_VARIABLE。五端世界full、持续输入、单步基线、四项协作专项、完整UI及全新单JAR安装均通过并干净关闭；不以编译或自动Hook验证宣称新的宿主物理外观或真人Esc验收。
- alpha发布器按规范化相对路径匹配Windows证据键，拒绝路径冲突、越界及非法摘要；不改原始结果或降低源码校验，16项配置/消费测试通过。1.21.1官方安装器一次TLS下载失败发生在启动前，保留原始故障资料并按同一正式流程重试通过，未关闭TLS或改变产品依赖。
- Companion握手从package.json取版本；Native V0版本独立。手册包含现有WS及恢复、必需diagnostics.kind与准确读取配方。新增USER物品itemId/itemCount、可见slotOwner及双树计数；overlay后不泄露底层title。字段为增量，非法chunkRadius现返回400 INVALID_DEEP_SELECTOR，不再静默钳制，调用方须传JSON整数0..2。
- 01a/01b当前目录基础为80项正式HTTP、24项实际MCP及typed action、13类条件；新增USER区域分页复用既有minecraft_query_world，强停止仍用POST input/release-all和minecraft_control release_inputs及只读input.released，模式不授予权限。见[消费者隔离目录](../../validations/conformance-world_read/validation.md#读取消费者隔离复核)；01c Peer与完整01d～01i、07d协作仍待收口。
- M-09仍受模板全局mod_side表达阻塞，保留both合同，不以可选通道或开发运行冒充三部署正式支持；其余组继续推进。
- 自动客户端1920×1080、静音、4 GiB，一次一个自有隔离副本；禁止Computer Use、外部Prism及重要存档操作。正式Persistent Write始终为0，自动Goal只推进A～D。

##### 2026-10-20当前已发布版本矩阵

以下只证明bfdda69c的既有完整套件、持续输入增量和独立安装，不表示全目录或人工验收。
UI数字为流程/Native样本/实际消费者，世界为流程/实际MCP；安装均绑定同批JAR、来源、源码指纹及工具哈希。

| Target | UI完整套件 | 世界full | 持续输入 | 全新单JAR安装 |
| --- | --- | --- | --- | --- |
| 1.20.1 Forge | 通过65/250/44 | 通过67/18 | 通过10项 | 通过，最终JAR标题/READ/SPI/退出 |
| 1.21.1 NeoForge | 通过66/251/46 | 通过67/18 | 通过10项 | 通过，最终JAR标题/READ/SPI/退出 |
| 26.1.2 NeoForge | 通过66/251/46 | 通过67/18 | 通过10项 | 通过，最终JAR标题/READ/SPI/退出 |
| 26.3 NeoForge | 通过67/254/46 | 通过67/18 | 通过10项 | 通过，最终JAR标题/READ/SPI/退出 |
| 26.3 Fabric | 通过67/254/46 | 通过67/18 | 通过10项 | 通过，最终JAR标题/READ/SPI/退出 |

本批另有五端四项专项：mode-collaboration各8项、command-discovery-batch各8项、server-completion各8项、resource-version各14项。
20份结果逐一绑定当前源码/工具，实际MCP、清理及不宣称真人并发的边界由发布器复验；不拿开发Runtime结果替代最终制品内完整玩法。

#### 2026-10-20当前 Goal 的七项判据

本 Goal 只交付 A～D 的连续游玩切片，不代表两领域整体 READY；实际数值及同候选证据齐备后才关闭各项。

| 判据 | 当前状态 | 剩余必需证明 |
| --- | --- | --- |
| 1 持有与清理 | 进行中；native epoch已接五端，新候选参考端真人撤销3项操作全部取消、8070ms输入增量0，held=0 | 当前候选五端共同自动复验；不将控制WS正常关闭等同所有网络故障，不将代表端物理Esc写成五端人工PASS |
| 2 tick状态流 | 进行中；共同候选参考端实际首秒20个新tick、GET位置/朝向/速度误差已量化、重连首秒20及真实缓存重放通过 | 其余端实测；撤权/旧上下文负控另有生产测试，参考静止误差不等于逐tick延迟 |
| 3 离散吞吐 | 当前参考同40ms hold及实际消费1/3确认，比值2.74655，NOT_MET；用户已允许带此限制试用，其余端测量进行中 | 不宣称性能PASS，不删hold、拖慢single或将原生调用等同伤害/服务端确认 |
| 4 区域感知 | 进行中；参考4096格以64格页预算实测66次、4096格页预算4次；55节点165点逐坐标/完整属性一致 | 其余端实际矩阵；这是同候选分页预算/点查比较，不冒充历史API、实际行走或寻路 |
| 5 分段回执 | 进行中；当前参考受阻/成功门床双植株、同ID slab及消费未放置8例通过 | 其余端及共同Native/MCP回归；输入终态、客户端预测和独立回读分开，不伪造服务器ack |
| 6 延迟预算 | 进行中；共同候选参考实际MCP玩家读取20次p50/p95=21.88/27.24ms，GUI20次内部p95=895.07ms，三类gameplay各20样本通过 | 其余端分位数及有条件预算；GUI保留观看节奏，不用它作gameplay吞吐基线 |
| 7 连续游玩分发 | 待办；既有七文件快照保留 | 新候选共同矩阵/独立安装后固定分发，附影响清单与可执行连续游玩试用说明 |

用户于2026-10-19明确批准：允许试用，明确保留性能未达标。仅1.5倍比值不再作为本批试用阻断；
实际原生消费、同参数hold、原版冷却、权限、取消清理及其余门禁仍必需，不能把性能目标改写成PASS。

##### 2026-10-20当前试用候选增量矩阵

以下对应f2dc15c9修正消费者后的当前结果；前三端已完成全部世界场景，其余端未完成的旧工具结果不复用。
交付须实际运行、当前源码/工具哈希和正常退出，不替代完整领域或五端人工UX验收。
攻击列的“通过”只表示测量及原生消费确认，1.5倍性能比值另记NOT_MET。

| Target | epoch | 区域/流/MCP | 放置回执 | 持续/攻击测量 | 基线/GUI20 | 持有/取消 |
| --- | --- | --- | --- | --- | --- | --- |
| 26.3 NeoForge | 通过3项 | 通过5项 | 通过8项 | 测量5项通过，2.74655 NOT_MET | 通过8项/GUI20 | 通过10项 |
| 1.20.1 Forge | 通过3项 | 通过5项 | 通过8项 | 测量5项通过，2.61862 NOT_MET | 通过8项/GUI20 | 通过10项 |
| 1.21.1 NeoForge | 通过3项 | 通过5项 | 通过8项 | 测量5项通过，2.69870 NOT_MET | 通过8项/GUI20 | 通过10项 |
| 26.1.2 NeoForge | 待重测 | 待重测 | 待重测 | 待重测 | 待重测 | 待重测 |
| 26.3 Fabric | 待重测 | 待重测 | 待重测 | 待重测 | 待重测 | 待重测 |

固定结果入口为`conformance-world_read/result/<target>-<scenario>.json`。消费者仅对明确未消费游标的
429 WORLD_REGION_BUDGET_NO_PROGRESS按合法提示有界重试，全部尝试计入调用数，8ms生产预算不变。
原始失败及修订依据见[验证调查](../research/incident-milestone_validation.md)；新候选不复用旧工具或旧产品证据，其他端及完整M-xx组不据参考端推定完成。

##### 2026-10-20批次 A 的有条件预算

以下为本批同机、单个1920×1080静音/4 GiB固定副本、已加载稳定世界、默认原版灵敏度的回归观察预算，
在五端共同候选矩阵前冻结。单步/读取/GUI分位数各至少20样本，报告p50/p95和样本数；队列、HTTP、内部pipeline、GUI与效果分别统计。
三次攻击比较另报实际单次3样本及一次批次，不据此推定长期尾延迟。
它们不是跨机器SLA，后台负载、区块加载、暂停/最小化和第三方渲染会影响结果；超出应如实列明原因，不自动放宽权限或删安全清理。

| 类别 | 本批观察预算 | 计时边界 |
| --- | --- | --- |
| 纯gameplay mouse.delta | 内部p95≤100ms | 实际pipeline时钟，不含调用者采样间隔 |
| Shift40 / delay100 | 内部p95≤150 / 200ms | 包含指定hold/delay和必需cleanup，不扣除它们凑值 |
| gameplay Native调用往返 | p95≤250ms | 实际请求至对应结果，不声称服务器效果 |
| USER player / scene读取 | HTTP p95≤200ms | 连续30秒期间的实际只读调用 |
| 实际MCP player读取 | p95≤300ms | 同一stdio进程20次；启动/握手单列，不计入读取RTT |
| GUI语义hover | 内部p95≤1800ms | 20次真实目标，保留可观看指针和GUI节奏 |
| 位姿流 | 首1000ms≥15个新native tick | 排除重放，另报告GET误差及恢复；不推导端到端50ms延迟 |
| 同4096格区域 | 大页调用数≤64格页预算实测调用数的一半 | 两次实际完整读取、相同内容与属性；不是历史API性能比较 |
| 三次攻击 | 总耗时/单次p50≤1.5，当前NOT_MET | 相同40ms hold、原版冷却、实际原生计数1/3和一次cleanup |

释放/取消的安全终态必须独立证明held=0、cleanup完成且有界无迟到；单次安全样本不计算虚假的p95。
三次攻击目标按用户批准保留未达标试用，其余数值按当前环境报告，不把“试用允许”改写成完整性能或领域验收通过。

##### 2026-10-20已完成端的当前时序摘录

以下仅为f2dc15c9及当前工具绑定的前三端实测，数值为毫秒p50/p95，采用nearest-rank。
三类gameplay、GUI和实际MCP各20样本；连续移动期间USER player/scene HTTP各30样本。
内部Pipeline、HTTP/MCP往返及GUI观看节奏分别统计，不互相替代；完整阶段及Native调用计时仍以对应结果为准。
26.1.2 NeoForge与26.3 Fabric的这些专项尚未完成，不从源码相同或标题启动推定数值。

| Target | mouse.delta内部 | Shift40内部 | delay100内部 | USER player HTTP | USER scene HTTP | 实际MCP player | GUI hover内部 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 26.3 NeoForge | 23.39/30.41 | 66.28/76.24 | 110.08/116.98 | 16.69/21.60 | 16.76/21.12 | 21.88/27.24 | 827.46/895.07 |
| 1.20.1 Forge | 21.64/25.16 | 63.19/74.22 | 107.00/116.24 | 16.73/17.81 | 16.78/17.84 | 22.00/25.05 | 812.56/846.40 |
| 1.21.1 NeoForge | 20.99/25.50 | 64.93/67.46 | 105.81/115.75 | 16.74/18.85 | 16.89/18.68 | 20.07/23.73 | 768.76/809.56 |

三端的首1000ms均收到20个新native tick，恢复后的首1000ms也均为20；重放不计入新帧。
独立停止后的GET对照中，位置、朝向和速度各轴绝对误差均为0；这仅是静止对照，不证明动态逐tick延迟或服务器真值。
原始结果正文SHA-256已与42组暂停检查点核对，本摘录不生成新Runtime证据或更新alpha清单。

#### 2026-10-20距离 READY 的剩余清单

计数单位是下表中带编号、可独立核验关闭的验收组，不是 Java 类数、测试断言数、所有参数组合或完成百分比。
每组须完成适用端实现、真实接口验证、恢复及限制说明；组内分支仍按原目录核对，不因合组而删减要求。
当前剩余58组：实现/覆盖50组，夹具与最终验证/交付8组（M-08、M-10、M-12）。这是规划验收组，不是58个已发现代码缺陷或58次完整矩阵。
本次批准要求新增01j状态订阅、05h区域感知、10d连续控制性能验证三组；其余改进映射到现有组，范围增加不表示已有实现退化。
批次A首增量没有关闭完整验收组，单步基线不冒充10d连续场景；M-10仍为P，已发布安装/矩阵结果不撤销。估算须结合持续场景与预算复核，不承诺总READY日期。
既有11a/11b/11c、03a/03b/03d及01a/01b的完成证据保留；新要求另设组，不把新增目标写成旧实现失败。共享场景可同时关闭关联组。
非接管操作决策落入01e与07d等现有分组，不新增整套玩家动作承诺；其余原生动作先做依赖审查，不将候选能力直接计为已开放。
0表示本包无剩余独立必需组，不自动等于READY：共同最终候选复验统一计入M-10，不重复记到每个已关闭包。
粗估是各包的工程工作量而非并行日历承诺；M-09产物决策尚未解决，当前没有可信的总READY时间。先逐组关闭，待该决策及参考端子项明确后再估总量，不沿用旧范围估时。
发现真实缺陷或原目录遗漏时，在受影响组说明原因、重新估计；不为增加边界参数无止境扩项，也不靠标记只读或原生缺席清零。

| 工作包 | 剩余必需验收组（编号与矩阵计数一致） |
| --- | --- |
| M-01 | 01c：Peer授权落点/异步发布；01d：所有读取消费链的模式解耦、类别授权和预算/拒绝一致性；01e：共享输入/非接管操作与独立DEBUG准入，审查其他原生动作依赖；01f：Esc仅撤销接管、独立DEBUG/原生命令/Arm及reconsent；01g：真人/OPERATE并行且显式条件有效；01h：任务/模式转换和结构化开工诊断；01i：动作/条件等待/独立回读的逐段结果、消费与效果的区别及wait语义；01j：复用WS/Event的有界玩家状态/选定世界变化订阅、类别/权限复验、序号/采样/背压/gap/重同步与生命周期 |
| M-02 | 02a：世界选择/编辑/转换/修复及双击失效；02b：创建世界与剩余原生设置的独立导航/取消；02c：创造快捷栏/配方与菜单剩余分支；02d：书本/告示牌/富文本链接与回调；02e：进度/建议等自绘交互；02f：各版本Dialog/Friends/Reporting及服务/账号边界；02g：静态Screen目录逐项映射到真实场景/有依据限制，补消费者验证。外部浏览器/文件选择不转宿主控制 |
| M-03 | 03c：特殊菜单/自绘区域及HUD/Toast的字段、合法操作或安全只读理由。卷轴/HUD文字及SystemToast reset、TutorialToast progress/hide已五端专项通过；Forge系统通知没有原生forceHide，明确拒绝且不虚报该能力。其余合法目录仍须核对，不因非Widget标只读。03a/03b/03d已闭合；文字恢复不冒充格式/计时或服务器事务回滚 |
| M-04 | 04a～04i分别对应H-02～H-10：物品呈现；生命图标；上下文条；准星/覆盖；Boss/计分板剩余格式；效果剩余遮挡；多人列表；其他文字；剩余Toast与生命周期。H-01已有代表流程，仍参加最终整体验证；04b先完成已有代码的Hook/Schema一致性和五端实测，不继续逐纹理深化 |
| M-05 | 05a：WU-01～04剩余键位/攀爬/爬行/流体/相机设置，含既有Lease下连续输入更新/停止及原生相对视角；05b：WU-05/15/16特殊移动、载具及滑翔；05c：WU-06/07/08/10物品控制、破坏/放置及工具剩余独立分支，受阻放置不虚报效果；05d：WU-09/19世界方块及UI桥接；05e：WU-11/12/13战斗/蓄力/消耗投掷；05f：WU-14/17实体、拾取与自然反馈及客户端已知掉落物类型/数量；05g：WU-18/20生命周期、维度/重生及命令目录关联验收；05h：已加载缓存区域/section分页、紧凑状态与续读、明确范围/时效/预算和公平读取调度。不发展寻路 |
| M-06 | 06a：WD-01/04玩家/实体状态；06b：WD-02/06槽位/装备/BE；06c：WD-03生命周期；06d：WD-07环境；06e：WD-08关系；06f：WD-09/10有界批次/只读模拟及获准服务器区域读取的预算、版本/失效和消费者隔离；06g：WD-11合法LIVE转移；06h：WD-12 Provider/Fixture完整目录。06i已闭合：六类正式inspect/token、条件、safe stale及受控恢复五端各14项通过，含2条真实MCP；WD-05已实现，参加回归 |
| M-07 | 07d：现有OPERATE原生命令五端通过，仍需完整真人协作、权限变化及Esc/latch验收。07c已闭合：本地原生树/显式SERVER独立事务、上下文失效和实际MCP五端各8项通过；真人future优先有结构/确定性证明，非真人并发Live，原版预算共享。07e已闭合：最多32条、逐项序号/未确认回执、首错停止、取消与部分提交均有证据；不实现任意程序效果沙箱 |
| M-08 | 08a：最终候选恢复哈希/锁/凭据核验；08b：新增三部署、零玩家及独立主体固定配置；不无故重建已确认基准 |
| M-09 | 09a：符合模板的单端/双端产物及安装协商；09b：服务器/Peer主体、委托、Arm、版本和撤权；09c：零玩家独立Runtime/认证/发现/收尾；09d：控制台输出/补全/原生命令操作及授权/取消；09e：仅客户端/独立服/双端世界路由、无通道与断线拒绝；09f：三部署Native/MCP/网页、条件/Event/Recording及能力/错误表达 |
| M-10 | 10a：最终冻结候选五端完整领域/消费者矩阵；10b：同一候选五端独立发行JAR安装/握手/READ/退出；10c：并发取消、held=0、无late input、Capture/Recording隔离与进程/端口清理；10d：请求/队列/消费/终态/效果分段基线与分位预算、连续30～60秒玩家操作穿插观察、区域续读和状态反馈场景。历史候选不代替新候选 |
| M-11 | 11d：连续控制/区域感知、真人继续操作时OPERATE原生命令、接管内DEBUG、USER批次及Esc后的独立工作展示；11e：三部署、非图形控制台及联动展示。既有11a/11b/11c复用，不改旧结果，真人案例独立等待用户 |
| M-12 | 12a：全部必需项/已知限制/人工步骤审计，Native/MCP版本和准确调用/事件/预算配方及兼容影响；12b：源码/JAR/证据同批的五端alpha、清单和手册，发布不可变快照并核验latest及消费者复制入口 |

#### 2026-10-20连续游玩反馈的计划映射

28项反馈绑定20261018-1221-07848af5及使用方26.3 NeoForge 26.3.0.86，本项目事实固定26.3.0.77。
下表是当前分诊和已批准计划，不是新运行证据；现有能力经源码核对，耗时、限流模式和具体动作效果仍须按批次A复现。
实现只归入既有M-01～M-12，新必需组为01j、05h、10d；不把每条反馈都扩成独立产品能力或已确认缺陷。

| 反馈编号 | 分诊与当前事实 | 计划落点与通过条件 |
| --- | --- | --- |
| 1/3/4/5/22 | 区域感知能力缺口及契约不一致：deep显式块选择上限64；chunkRadius已统一为JSON整数0..2，越界显式拒绝，区域分页仍待完成；scene为GET及有界摘要，world domain不是所有块的别名 | A/C，05h、01d、06f、12a：方法/域/参数公开且一致，已加载区域可分片续读，数量/字节/时间和未加载/时效明确，不扩大USER权威 |
| 2 | 预算/调度体验：已有结构化retryAfterMillis；仅凭2并发比8并发差不能推出单一根因或直接取消限流 | A/C，01d、05h、10d：记录真实预算消耗/拒绝路径和排队，验证公平性、取消及安全只读重试，不盲重放输入或mutation |
| 6/19/21/26/27 | 诊断/发现/文档缺口与版本事实冲突：Native说明已有简要WS；手册缺可运行连接/事件配方；diagnostics.kind当前必填；Companion包与握手版本不一致 | A，01h、12a：统一版本事实及Native/OpenAPI/MCP说明，公开诊断必填/缺省和事件/恢复合同，Agent按示例调用而非读源码猜测 |
| 7/8/9 | 数据表达：掉落物缺类型/数量；树根已有title，HUD在presentation，不能直接断言整个容器无标题或HUD不存在；槽位归属需明确 | A/E，05f、02c、04h、12a：核对真实生产投影及消费者，补客户端已知字段和容器/背包语义，分开呈现/交互计数，不增加USER私有数据 |
| 10/11/12/18 | 连续输入体验：已有key按住/松开、cleanupOnComplete及16项有界队列；未确认统一300ms完成窗口；相对视角经原生灵敏度/反转，0.15不是通用常量 | A/B，05a、01e/01f、10c/10d：分段测量、连续更新与安全停止，不并行争抢输入；保留GUI观看节奏和原生相机，不以删门禁或直接改位姿提速 |
| 13/16/17/20/24 | 结果/状态缺口：Native启动与Companion等待合同不同，有界wait可超时返回未终态；world.action已标effectConfirmed=false；flying已有USER读取，但没有持续位姿/方块流 | A/D，01i/01j、05c/05f、10d：接受/消费/终态/效果分段、有界回读及状态订阅；受阻放置明确效果未确认或条件不满足，不伪造服务器确认 |
| 14/15/23/25 | 路由解释、原版行为及范围：显式NATIVE命令已支持OPERATE/TAKEOVER且不借键鼠Lease；聊天键入仍需接管；双门分扇属原版；语义aim已有，自动寻路/跟随未实现 | B/D/E，05a～05g、07d、12a：提供正确路由/原生效果示例，完成有限玩家动作；不新增E2、自动寻路、自动建造或原版双门补丁 |
| 28 | 分发/复验成本：不可变快照和当前候选独立安装继续必需；无需因纯文档变化假定所有消费方须全量重测 | F，10a/10b、12a/12b：随批次列明实际接口/语义兼容影响和受影响回归，生产方完整新候选验证不省略，消费方按影响复测，不回写旧证明 |

#### 2026-10-20使用方优先 READY

两批均已 **READY**，已随[当前已发布版本](#2026-10-20当前已发布版本矩阵)完成共同复验与固定分发，包含卷轴/HUD增量及读取消费者隔离。进行中的新候选不替代已发布证明；首次交付的绑定记录见[阶段记录](phase-1-core_delivery.md)。
这里的READY仅指已发布合同的使用方切片；新模式和命令批次按各自专项计证，不能据此宣称完整协作或三部署目标已实现。
这是完整目标内的优先交付切片，不是缩减两个领域的目录，也不新增第三方适配或图形诊断产品。
只读核对日期：2026-10-17。第一批漫画校准目录、Voxy回归报告、可行性report及radiance-lab/README已核对；
在用快照以Alpha/.usage的实际显式登记为准，发布器已核对并保留在用批次；不以报告中的旧批次推定当前使用，不代替消费方登记或操作实例。
旧使用方报告是需求及历史结果，不证明新源码；第一批依据本仓库当前候选的新实测交付。第二批资料与P1/P2随后独立分诊、计数及验证，不撤回已验证的第一批。

##### 2026-10-20使用方能力表

| 有证据的能力 | 使用方Target | 使用方与依据 | 当前状态及原工作包/验收组 |
| --- | --- | --- | --- |
| 单JAR安装、endpoint身份绑定、握手、实例发现 | 1.21.1 NeoForge、26.3 Fabric | Voxy回归、漫画校准二、可行性D2及R1/R3；旧端补依赖已撤除 | 3d6e3fca五端全新单JAR安装及SPI来源通过；同批第二批独立安装已通过，M-10/10b、M-12/12b |
| 原生世界选择/创建、进入、保存返回标题及退出 | 1.21.1 NeoForge、26.3 Fabric | 漫画校准二、Voxy四场、可行性D2/D3/R3 | 双击修复五端各3项通过；取消映射已修，3d6e3fca五端最终JAR退出503且自然退出0；不代表外部项目已复测，M-02/02a/02b、M-10/10c |
| 有界客户端玩家/方块读取，位置/视角与加载状态 | 1.21.1 NeoForge、26.3 Fabric | 漫画截图记录含USER玩家响应；Voxy方块效果；可行性D3地图读取 | 3d6e3fca五端完整full及消费者专项通过；新字段已同批绑定，M-01/01a/01b、M-05/05a/05c |
| TAKEOVER原生键鼠、相对视角、相机/时间诊断命令及续租 | 1.21.1 NeoForge、26.3 Fabric | 漫画日夜机位、Voxy移动/重载、可行性D3 | 既有输入/命令守卫通过；诊断命令不冒充无污染PLAYTEST，不证明新增OPERATE原生命令，M-05/05a/05g、M-07/07c～07e、M-10/10c |
| 资源重载、维度往返与保存后重进 | 1.21.1 NeoForge | Voxy native/seven的01/02四场实际完成 | 3d6e3fca五端原生重载/维度往返/保存重进通过；产品组合就绪条件归B2-03，M-02/02a、M-05/05g、M-10/10a |
| Composite截图、Artifact资源读取和可重复取帧 | 1.21.1 NeoForge、26.3 Fabric | 漫画日夜1080p、Voxy远景、可行性D2/Radiance场景 | Operator UI隔离已有；画面收敛边界见下节，不把帧推进当最终稳定，M-01/01b、M-10/10c、M-12/12a |
| Provider V2成品扩展注册、owner线程DEBUG读取和schema/USER/scope拒绝 | 1.21.1 NeoForge、26.3 Fabric | 可行性D2及R1-off/on、Sodium精确版本读取、Sable服务端/渲染位姿 | 3d6e3fca五端公开SPI来源、owner/schema/USER/scope正负控通过；外部扩展报告仍属历史，M-01/01a/01b、M-06/06h |
| 集成服server_thread typed Provider mutation及future完成、Arm/模式/schema/版本拒绝 | 1.21.1 NeoForge | 可行性round2b R3桥接、Sable E2与五项负控 | 3d6e3fca五端server_thread、Arm/版本及取消无晚到修改通过，M-06/06h、M-10/10c；不新增client/render mutation dispatcher |
| 同批五JAR/清单/手册、固定快照及来源/限制说明 | 两个实际使用Target，发行仍五Target | 使用方报告实际分别绑定1922/2203，漫画已明确移除Netty临时补丁 | 3d6e3fca已固定分发并核验七文件；第二批也已验证发布，不引用build，M-10/10b、M-12/12a/12b |
| 正常单JAR安装、标题USER READ和语义Quit | 1.21.1 NeoForge | Raven's Models的debugging-mcp_alpha、Physics扩展的backlog-mcp_alpha；均绑定2203，退出500但自然退出0 | 本仓库当前候选全新安装/503解释通过；不算这两个外部项目已复测，M-10/10b/10c |
| 世界进入、玩家移动/视角、背包、help提交、Wait/Capture及输入释放 | 26.3 Fabric | Vulkanite的backlog-minecraft_protocol_alpha，2203第17场；PT帧数来自其自有probe | 本仓库当前候选相关原生流程通过；不承诺PT收敛或Vulkanite新快照兼容，M-02/02a/02c、M-05/05a/05g、M-10 |
| 第三方首次设置UI和动态内容 | 1.21.1 NeoForge | Physics QuickSetup；漫画Sodium/航空学/Sable报告 | 缺少语义节点或目标，第二批P3只登记，不把原生坐标回退计成适配PASS，M-02/02g、M-05/05d/05f |
| 迁移后的安装与接口接入准备 | 1.21.1 NeoForge，迁移中 | RRO按外部评审列为使用方，当前没有问题清单或实际调用证据 | 只记计划，不臆造使用结果；Raven's Models参考1.21.4 Fabric无本产品制品且不新增Target，M-10/10b、M-12 |

以上Target为报告中的实际使用版本，不表示其他三个Target不支持，也不以外部报告代替参考端26.3 NeoForge及相应使用端的新候选实测。
Voxy使用NeoForge21.1.251而本项目固定21.1.259：外部组合属于历史兼容证据，新候选验收仍固定本项目Loader，不擅自切换依赖。
漫画离线Anvil放置、RenderDoc/Nsight/GFX捕获、Sable/Radiance扩展和外置A*均由使用方实现，不移入Core，也不作为Persistent Write或E2/E3交付。

##### 2026-10-20已关闭七组的当前回归

| 顺序 | 中间组 | 剩余必需项 | 当前状态 |
| --- | --- | --- | --- |
| 1 | C-01 当前候选基线 | 已完成五端现有完整UI/世界回归、当前源码/工具/基准及清理审计 | **V**：当前世界各67项/18项实际MCP；UI为65/66/66/67/67项，发布校验器、源码/工具/固定副本及正常退出通过。不代表原版全部Screen或世界目录 |
| 2 | C-02 使用方故障 | 已完成故障修复与当前共同复验 | **V**：五端当前UI实际以未选中语义世界条目双击进入；外部变化的拒绝另有HTTP回归。当前最终JAR退出均为503 RUNTIME_SHUTTING_DOWN、executionOutcome=not_confirmed、retrySafe=false，随后自然退出0及清理；不是成功RPC或回滚，不盲重放 |
| 3 | C-03 原生使用闭环 | 已完成适用开发接口分支 | **V**：当前五端各9项consumer通过，真实运动/视角、维度往返、保存重进及标记读回、MCP和清理；未运行使用方实例 |
| 4 | C-04 截帧边界 | 已完成当前帧/就绪/隔离合同检查 | **V**：当前五端各5次1920×1080 PNG传输及Screen/overlay校验、READ无Lease录制/Artifact通过，原有帧隔离不改。只证明当前帧，不承诺第三方收敛，不以截图哈希充当像素oracle |
| 5 | C-05 Provider闭环 | 已完成现有公开SPI调用分支 | **V**：当前五端owner/schema/USER/scope拒绝、独立Arm/OPERATE/旧revision、server_thread mutation恢复、延迟500ms请求超时后至少650ms无晚到修改及真实MCP通过。不运行GPU扩展或新增client/render mutation |
| 6 | C-06 同批发行 | 已完成必要回归、五端全新Loader单JAR安装及源码/JAR/证据一致性 | **V**：当前五端全新安装含SPI目录/来源、503解释及自然退出0通过；完整既有UI/世界和哈希审计、spotlessApply/check/verifyRelease/严格合规通过，不代表完整制品内玩法 |
| 7 | C-07 固定交付 | 已完成七文件不可变快照、latest及全部哈希核验 | **完成**：当前快照五端JAR/清单/手册一致、无分发警告；保留最近三份及在用登记。完整里程碑未结束，当前Goal只推进A～D |

只有七组均有当前候选证据、无隐藏阻断并实际发布快照，才称“使用方优先 READY”；它不等于M-01～M-12或任一完整领域READY。
第一批七组已闭合并固定发布，剩余0组；C-01～06的V分支已通过共同交付核验。
完整里程碑的现行分组与P/V定义见上表，不因中间切片READY而自动关闭新增或未完成工作包。
full alpha生成器已增加九项使用方具体样本、当前完整UI结果与实际清理的前置检查；最终安装增加公开Provider SPI类真实来源和目录检查。
这些门禁的合成负例不算游戏通过；本批实际五端矩阵、安装及七文件审计均通过才发布。
失败、夹具修复、UTC回执修复及参考端重跑记录见阶段记录，未手工修改结果或拼接旧候选。
第二批P1/P2已闭合并固定交付，当前按A～D计划推进连续游玩，M-11/03其余广度留待后续；第三方覆盖和未来扩展API只登记。

##### 2026-10-20缺陷归属与边界

- **退出UI返回500：属于本项目，已修复。** 漫画、Voxy和可行性均有CancellationException。当前实现已区分409 REQUEST_CANCELLED/503 RUNTIME_SHUTTING_DOWN，保留executionOutcome=not_confirmed、retrySafe=false，MCP不丢字段；HTTP测试证明已分发点击也不能冒充回滚或成功。当前五端实际发行运行均验证503结构及自然退出0，不用历史退出日志掩盖原错误；第二批已共同回归并固定发布。
- **截帧稳定性：部分属于Runtime就绪表达，部分为消费者oracle。** Voxy明确记录LoadingOverlay淡出及首次/重进ROI未达标；帧推进或renderer READY不证明世界图像收敛。C-04核实当前帧来源、overlay和隔离，提供有界可观测条件；不承诺检测任意第三方渲染队列或GPU完成，不能把所有瞬态直接归因第三方或宣布已经修好。
- **扩展边界：保留。** server_thread mutation只在有owner的集成服/既有Peer范围可用；client/render mutation dispatcher未实现，受信任扩展并非沙箱。外部GPU捕获可能包含Operator UI，不等于本项目Composite Capture隔离失效；1000ms预算、扩展自有watchdog和恢复规则不自动变成平台保证。

##### 2026-10-20使用方反馈第二批

当前 **READY**，随[归档候选](#2026-10-20当前候选)共同复验并固定分发；不是完整领域READY。只读调查日期2026-10-17，漫画体验记录MCP-001～011/CALLER、Voxy两份更新、Physics/Raven's Models/Vulkanite问题清单、可行性两份报告均已阅读；RRO暂无清单。
记录大多绑定1922或2203，本批均先用实际生产接口复现再修复或验证既有合同，不把旧现象直接当当前缺陷。退出500及双击沿用第一批修复并重新回归。
调用方几何/函数包错误、OOM和外部GPU捕获差异不直接归因本项目；可行性报告中的26.3准备期native crash尚未归因，纯净新候选通过不能证明其扩展环境根因已修复。

该已发布切片的8组（6组P1、1组P2、1组共同验证/交付）均已关闭，剩余0；不另加到上表现行分组，也不证明新模型。
每组先复现，再最小修复或用当前已有合同及真实测试关闭；P3/P4不计实现任务。

| 顺序/组 | 优先级与完成条件 | 当前状态 | 粗估主动工作量 |
| --- | --- | --- | --- |
| B2-01 | P1读取/输入429：retryAfterMillis、实际类别额度/剩余额度，经Native/MCP保留；不改限流值或盲重放 | V：生产HTTP复现后修复，HTTP/确定性预算/认证负例、Schema及Companion网页通过；五端各2项实际Native/MCP专项通过，输入增量0、held=0、正常退出。共同最终候选复验归B2-08 | 0；共同复验另计 |
| B2-02 | P1玩家命令：先写最小方案，保持原生玩家路径；能关联则关联，否则明确有界时间窗/不保证，仍独立断言效果 | V：先设计后实现；五端当前full的普通查询/服务器错误文本、原生预检零输入、实际MCP及维度往返均通过。500ms/16条、时间关联不保证，不宣称效果成功；上下文改变提前结束且不读新世界 | 0；共同交付计B2-08 |
| B2-03 | P1页面就绪：Screen匹配、无overlay、新鲜可操作节点的组合Wait与手册实例；不把hook READY当页面READY | V：生产HTTP复现后修复screen.ready组合；新鲜目标/失效/窗口负例、MCP转发和五端实际标题就绪、正常退出通过。手册有准确参数；共同最终复验归B2-08 | 0；共同复验另计 |
| B2-04 | P1目标区块可读Wait：保留unknown、not loaded、实际air差别，不加载/生成 | V：现有block+available合同已足够，不新增加载能力；五端实际未知/未加载拒绝、真实空气及传送后客户端就绪通过，源码/基准/清理匹配。共同最终复验归B2-08 | 0；共同复验另计 |
| B2-05 | P1 USER自身gameMode/onGround/flying：只用客户端已知值，不可得明确unknown，五端/消费者/条件一致 | V：生产响应复现后修复；五端当前full均通过Native/Provider/Frame/Wait/实际MCP一致性及原生模式变化，未连接世界明确unknown，读取无输入Lease/副作用 | 0；共同交付计B2-08 |
| B2-06 | P1 WORLD_TARGET_NOT_HIT：有界实际命中种类、目标/面/距离，不改原版命中或跳过前置条件 | V：实际遮挡失败复现后修复；五端当前full均通过Native/MCP的实际方块、面、距离和Operation结构校验，只有一次既有release_all清理计数，额外玩家输入0；不改原版射线 | 0；共同交付计B2-08 |
| B2-07 | P2手册：acquire顺序、accepted/后置条件、整数XZ居中、页面/区块等待、第三方覆盖及扩展边界 | V：准确JSON示例、第三方回退及未来扩展限制已写入，五端当前full验证对应接口；固定分发手册生成和哈希核对计B2-08，不回写旧快照 | 0；共同交付计B2-08 |
| B2-08 | 共同验证/交付：参考端/受影响端增量，冻结后五端相关回归与独立安装，下一不可变七文件快照 | 完成：当前共同门禁、五端既有世界/UI和五端独立安装通过；同批七文件、源码/JAR/证据/手册哈希及latest核验通过，无分发警告。不是完整领域目录或人工验收；预算由对应Runner配置定义 | 0 |

P3只登记Sodium、Physics QuickSetup、自绘航空学拉杆和Sable子世界目标缺口；正式回退为授权TAKEOVER下项目Capture与原生坐标/hold/drag输入，之后核对状态，不保证绕过原生回调的任意Mod都可操作。
P4只登记未来扩展的Arm/Lease/Human Override撤销通知、client/render mutation dispatcher和Provider等待预算说明；不在本批实现调度器、GPU工具或E1/E2/E3。
八组已全部关闭；后续回到完整M-11/03等广度工作包，完成每个独立必需组即更新本页，不再重复实施已交付的第二批。

#### 2026-10-20使用方反馈分诊

| 项目 | 状态与处理 |
| --- | --- |
| 原样发行JAR缺Netty HTTP | 已修复，五端全新安装通过；不采用使用方临时补类路径作证，旧补丁由使用方自行移除 |
| Forge官方安装器的本机Java TLS握手失败 | 验证工具已将安装器Java与游戏Java分离：安装器读取模板daemon JVM事实，游戏仍按Target事实选择17/21/25。五端新工具全新安装、标题/READ/SPI与自然退出通过；不改TLS、Loader或产品依赖，不要求使用方为Mod额外安装Java25 |
| 清单/JAR批次不一致 | 当前有效七文件快照已发布，安装门禁和哈希通过；旧批次撤回，不从build/分发 |
| USER世界读取手册过时 | 当前手册已描述有界客户端模型、XYZ/生命与缓存方块/实体；新生成手册仍须绑定最终批次，不回写旧快照 |
| 未选中世界条目双击失效 | 已真实复现selected误拒绝，修复只同步第一次原生回调自身的选中变化，五端各3项真实语义专项通过；外部选中/位置等变化仍拒绝，不靠坐标绕过或假称整个动作可回滚 |
| 加载覆盖层与TitleScreen同时出现 | 已修复并进入e83283fc：显式screen.ready在同一新鲜树检查screen、overlay=0、有效窗口和唯一可操作目标；五端同批相关回归/独立安装与固定分发已通过。底层screen本身不代表可交互，不声称渲染已收敛或未来点击被预留 |
| Sodium设置页只有搜索框 | 第三方自绘控件语义覆盖缺口，保留截图/坐标回退；不冒充已完成第三方适配，不在本次打包修复扩展实现 |
| 退出HTTP返回CancellationException | 已修复：HTTP单测区分409取消/503关闭并标记未确认、禁止盲重试；Companion保留结构，安装门禁拒绝内部500。3d6e3fca五端实际JAR均验证503及自然退出0，二者分开记账，不把取消伪装为成功 |
| 三Neo的Showcase Toast夹具缺口 | 已关闭：仅probe补夹具，三Neo各17项全套均实际读取Tutorial/System原生绘制文本，两次触发、源码/工具匹配及正常清理通过；不以按钮存在代替实测，不证明自然教学或像素遮挡 |
| 旧两端语义拖拽误拒绝自身拾取 | 已修复并进入3d6e3fca固定快照；五端完整UI中的真实语义拖拽、物品数量与清理通过。17项Showcase另按其绑定候选，不冒充新候选完整展示 |
| UI DEBUG文本截断尾部未绑定版本 | 已修复并进入3d6e3fca固定快照：完整UTF-16内容及预算/缺owner保护五端单测与各56份实际接口样本通过，USER投影不增加私有元数据；同批完整UI/世界与独立安装通过 |
| UI诊断事件/录制缺独立读权限与迟到读取 | 已修复，当前13项UI异步/HTTP/实际ZIP/WS、5项Operation及世界投影/授权回归和五端消费者共同通过；UI修改详情独立读授权、迟到发布拒绝且污染标记保留。M-01/01a/01b闭合，01c未完成 |
| 隔离网页bootstrap偶发fetch failed | 原1.21.1失败未记录端口/cause，不能追认根因。后续冻结候选在实际自动端口6679确定性触发标准Fetch bad port；现已修复自动分配最多8次实际候选、坏候选同步关闭再选、仅安全端点发布，固定禁止端口及bind异常明确失败。生产选择正负例和当前五端完整UI/世界/安装通过；不声称所有网络失败都由端口引起 |
| 新版Dialog非命令回调 | 已修复：CUSTOM_UI_CALLBACK最终包守卫、三现代端Native包对象与共享门禁单测通过；三端实际富文本custom点击均拒绝、服务端回调计数不增、无提交回执并正常清理。不解释未知服务器载荷，不宣称完整自定义回调已支持 |
| Forge生命专项首次伤害夹具失败 | 原版返回目标无敌且仍画满心；增加可观察server tick屏障后原断言通过，不提高伤害绕过原版保护。后续一次隔离网页bootstrap连接失败保留，补超时/原因诊断后的有界复测11项通过；不据此猜测原连接失败根因已修 |
| SystemToast正常tick使typed恢复误判stale | 已修复并进入07848af5：完整原生内容/owner与被动reset代次绑定，正常消费changed/计时不推进版本，同值外部reset仍拒绝旧token；五端各9项原生probe与实际MCP完整token恢复通过，不放宽恢复守卫 |
| SERVER补全迟到回包先于tick清理 | 已修复并进入07848af5：收包直接检查单调时钟期限，确定性覆盖未tick时1499ms成功与1500/1501ms超时，零迟到授权发布；五端实际SERVER/MCP专项通过，不宣称真实人类并发已验或独享服务器预算 |

禁止Computer Use、宿主输入模拟、外部Prism/重要存档操作；正式Persistent Write始终为0。当前Goal只推进A～D；当前增量结果不替代完整新合同、三部署与真人验收。

### 2026-10-20 f2dc试用交付与Linux清理修复

冻结候选f2dc15c9的65个场景位完成：63份原矩阵PASS，另有一份26.3 Fabric九项功能证明覆盖原performance/baseline两个场景位。
后两项只证明功能，性能明确DEFERRED；没有新性能测量，不称65份性能PASS，三端既有攻击比值NOT_MET保持原结论。
完整UI/世界与全新单JAR安装按实际源码、工具及断言依赖核验，未因工具哈希变化重跑无关结果；本次不代表完整领域READY或五端人工验收。

同批五份JAR、alpha-manifest.json与AGENT_GUIDE.md已发布到D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/20261020-0337-f2dc15c9/。
七文件及latest指向的独立审计PASS；[连续游玩试用说明](../development/workflow-alpha_agent.md#连续游玩试用)已发布，不覆盖旧快照，不从build目录跨线程分发。

Linux清理测试已按平台分支修复：Windows使用回收站，其他平台清理测试自有临时目录，没有跳过或吞掉异常；对应局部测试通过。
本机统一spotlessApply、check、verifyRelease、verifyCompliance --strict --offline通过，246 tasks，耗时2分47秒；真实Linux CI仍待唯一检查点推送后确认。
此时检查点、兼容稳定依赖更新、五端独立安装进入世界smoke及更新快照均未执行；后续按当前计划推进，不重复完整矩阵。
M-01～M-12的P/V及58个剩余验收组保持，UI/世界与完整Showcase仍IN PROGRESS，Persistent Write调用保持0。

### 2026-10-20兼容依赖更新与五端独立安装进入世界

唯一Update检查点ccbd2c8b9293faaace7de0b3ecc0a09eaf21e08a已SSH签名，作者日期与提交日期相等，正常推送develop并同步。
175份源码、测试、工具及文档纳入提交，78份raw results未纳入；Check 39313898839与Specification 39313896580真实success，Linux清理分支的云端验证通过。此后未执行第二次提交或推送。

六项兼容稳定依赖已更新：NeoForge 21.1.259→21.1.264、26.1.2.117→26.1.2.122、26.3.0.77→26.3.0.94，Fabric API 0.162.0+26.3→0.164.0+26.3，Loom 1.18.3→1.19.1，Zod 4.6.5→4.6.7。
新旧版本与官方来源见[Runtime依赖记录](../reference/dependency-runtime.md#minecraft-与-loader)及[Companion依赖记录](../reference/dependency-companion.md#验证与审计)。
五Target构建、三产品组件及Companion检查通过，151 tasks、3分8秒，npm审计0；Minecraft、最低Loader要求、模板2.0.2和maps/固定夹具未改，产品Java、资源与协议未改。

最终冻结候选源码指纹为52bc024f38e8a93e39fb0059b5e097725e079394554b2861d9de99a8b40a06df，共612份源码/构建事实。
按[独立发行JAR验证](../../validations/integration-packaged_runtime/validation.md)在五Target逐端使用最终JAR进入固定世界，全部PASS；五份结果绑定同一最终工具版本与各自实际JAR。
每端独立确认CLIENT_KNOWN USER读取、同一输入Lease、held=0、正常Save & Quit、进程exit0及endpoint移除；一次只运行一个低优先级4逻辑核隔离客户端。
本次仅为依赖更新的安装/进入世界smoke，不是旧65场景重新实测，也不证明完整领域或人工验收。

保留两项工具失败及修复：EnterWorld夹具缺少读取held状态所需的diagnostics授权；1.21.1世界列表异步加载时过早断言。修复只补工具所需scope及真实列表就绪等待，相关Node13项通过，最终五端工具一致，未放宽Runtime权限。
官方安装器一次TLS失败在一次原样重跑后成功，未修改TLS或游戏库；不以重试覆盖原始失败。
历史攻击耗时NOT_MET不变，本轮性能DEFERRED且无新测量；M-01～M-12仍有58组，E/F未启动，Persistent Write调用保持0。
此时更新后快照仍待最后仓库检查及publisher；不预写快照名称或发布完成，0337试用快照与latest保持。

最终spotlessApply、check、verifyRelease及verifyCompliance --strict通过，246 tasks、2分12秒，合规失败0、警告0。
随后发布20261020-0422-52bc024f同批五JAR、alpha-manifest.json和AGENT_GUIDE.md，publisher无警告；独立审计确认七文件、六份载荷哈希和latest一致。
更新清单绑定当前五端独立安装进入世界证明，旧65场景仅保留为不可变试用基线的历史证明；历史NOT_MET未改写，也没有新性能测量。
唯一检查点后的依赖、工具、文档与结果按约定保留未提交，未第二次推送或建tag；本Goal完成后停止，完整领域、E/F及统一人工验收未启动。

### 2026-10-20接收反馈分诊与后台相机最小复现

以下为05:35完成的19条原始只读分诊，保留当时尚未复现的判断，不代表后续修复结果。

2026-10-20已接收 `D:/Workspaces/Artifacts/MineCraftProtocol/ConsumerFeedback/feedback-current.md` 的“给 MCP 的待转交清单”，共19条；CF-073及先前已接收清单不重复计入。
依据使用方原文、绑定证据和当前生产源码只读分诊，未启动游戏或复现；0337/0422发布与本机矩阵均不代替使用方复测。
结论为接收11条（缺陷5、能力缺口6）、待补充信息1条、当前范围外7条；没有足够依据将任何一条直接关闭为调用方或环境问题。
下表批次仅为建议归位，不是实施授权，不增加58个剩余验收组、不重开A～D或Goal；第三方兼容与扩展需求不自动成为E/F必需项。

| 编号/优先级 | 分诊结论 | 建议批次或后续条件 |
| --- | --- | --- |
| CF-015 / P2 | 接收能力缺口：RRO自定义11行未投影，顶层导航不证明选项可操作；不是安装崩溃 | 后续第三方UI兼容批次；不计原版E/F必需项 |
| CF-016 / P3 | 当前范围外：1.21.4 Fabric未登记，不是漏发受支持Target或运行故障 | 仅登记发行覆盖请求；新增Target须另行决定，不装26.3包代替 |
| CF-017 / P1 | 接收缺陷：Voxy首次后台入世界，逻辑未捕获时相对输入完成但未转向；暂停返回仅是绕行 | 后续E优先补修B输入生命周期，保留独立Voxy样本 |
| CF-018 / P1 | 接收缺陷：Physics首次转向无效而移动有效，后续新动作成功不关闭首例 | 与CF-017同簇安排E，不能据症状合并根因或归咎Physics |
| CF-019 / P2 | 接收能力缺口：无Screen的HUD没有“无遮罩且窗口有效”的组合Wait | E的M-04/M-10；与Screen就绪、目标区块可读及图像收敛分别验收 |
| CF-021 / P2 | 接收缺陷：坐标选择器合同不清；当前blockEntities遍历区块，未消费selectedBlocks，不能静默给无关坐标结果 | E的M-06；支持精确筛选或明确拒绝/能力说明，不自动加载区块 |
| CF-022 / P2 | 接收能力缺口：DEBUG方块实体受128项预算限制且无分页；USER区域续读不等于容器深读分页 | E的M-06候选：有界分页及生命周期设计，非直接提高预算 |
| CF-023 / P2 | 接收能力缺口：已有树证据，但pageReady只合并布尔判定，缺各子条件失败解释 | E的M-02/M-10：区分overlay、screen、selector、visible、active及几何，不改变就绪语义 |
| CF-024 / P3 | 待补充信息：gamerule当前已有策略分支，NOT_AVAILABLE来自原生解析不完整，不能据旧命令字符串认定平台白名单误拒 | 请补同批Target、当前玩家权限及discover/suggest原始结果；核对版本语法和名称，不绕过服务器树 |
| CF-025 / P3 | 当前范围外：tick属于已拒绝的host control-plane，受限步进是新能力而非现有合同漏洞 | 后续独立测试控制设计；当前按正常tick及独立条件等待，不建议解除拒绝 |
| CF-056 / P1 | 接收兼容能力缺口：Radiance移植层的GUI阶段未覆盖本项目末帧Operator UI，直接异常由其保护抛出 | 后续渲染兼容批次，Radiance阶段适配为主、双方协作；暂不转其纯原版阶段线程 |
| CF-063 / P3 | 当前范围外：公共扩展SDK、包批准/依赖/启动与受管重启尚非主线承诺；研究副本不算已交付 | 后续扩展平台另定批次；不为本次E/F增加可信Java加载器 |
| CF-064 / P3 | 接收能力缺口：Modern UI独立线程/View图不在当前原生控件投影范围，冷查询和字节预算问题未解决 | 后续第三方UI兼容批次，与CF-015相关但不可共用成功结论；不提高预算冒充适配 |
| CF-065 / P3 | 当前范围外：动态子部件几何/修订/原生命中需新的目标合同，研究样本尚未证明完整正常交互 | 后续第三方世界适配批次；现有BlockState/Chunk目标不作细部件保证 |
| CF-066 / P3 | 当前范围外：通用扩展Action SPI及第三方异步效果屏障尚未承诺，既有Pipeline取消证据不证明它们 | 后续扩展动作批次；不作为当前Core取消失效或放宽Lease的依据 |
| CF-067 / P3 | 当前范围外：具名早期Hook、采样会话和自动撤销资源需新扩展生命周期合同 | 后续诊断扩展批次；读取不能隐式启动采样，普通Java不构成沙箱 |
| CF-068 / P3 | 当前范围外：profile/job、封闭handler、硬磁盘配额与账本属于宿主测试工具需求，不是游戏Runtime缺陷 | 后续宿主工具批次；不引入任意shell入口，不从调查自建工具推定产品承诺 |
| CF-071 / P2 | 接收缺陷：虽原运行绑定研究副本，当前五端仍宣告shift_click，共享uiAction无对应派发分支，合同不一致可由源码确认 | E的M-02：语义宣告与真实Shift+guarded click一致；旧研究成功绕行不算正式Alpha已修复 |
| CF-072 / P1 | 接收缺陷：GLASS 1245后台首次进世界未捕获时移动有效、转向无效，原版暂停/返回不关闭失败 | 与CF-017/018同簇安排E；保留26.3/Vulkan样本，不以1.21.1或后续新动作成功外推 |

### P1责任与同源判断

CF-017/018/072归为一个待验证的输入生命周期问题簇，**尚未证明同源**。
五端当前 `allowHostMouseGrab` 都在窗口active或Agent routed上下文才发出逻辑capture；`routedMouseGrabbed`又依赖 `gameCaptured`。
TAKEOVER若在标题页建立，首次后台入世界可能没有补上该状态；`mouseDeltaNow`只检查无Screen/Overlay并调用原生输入，没有因此明确拒绝。
这与三例“移动有效、gameCaptured=false、暂停返回后新动作有效”吻合，优先调查本项目逻辑capture生命周期，而不是要求抢宿主焦点、自动暂停返回、重放输入或直接写yaw/pitch。
使用方原文及当前[1.21.1输入路径](../../versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/NeoForgeProbeRuntime.java)、[26.3输入路径](../../versions/26.3-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/NeoForgeProbeRuntime.java)支持此假设，未作运行因果证明。

CF-056责任初判是 **Radiance移植渲染兼容层为主，双方接入合同待协作**：其GUI scope在GameRenderer返回时关闭，本项目[末帧hook](../../versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/MinecraftMixin.java)在其后、present前绘制，因而落入OUTSIDE。
原版允许的晚期GuiGraphics绘制不因Radiance私有scope未覆盖就自动成为本项目非法绘制；但本项目也未实现其显式阶段适配，所以接收组合兼容缺口，不把责任推给调用方、驱动或GPU工具。
0914两原样JAR、仅GET READ、未acquire仍崩溃的证据有效；不是已修复后的复发，不证明Radiance单独运行失败，亦不证明当前0422组合结果。
后续需明确末帧UI接入并保留截图排除/最高UI层合同，再联合复验；不能关闭控制提示、放宽拒绝策略或用单JAR安装通过替代。按安排本轮不转发Radiance线程。

43号Goal随后在本项目原版固定副本复现了26.3 NeoForge和1.21.1 NeoForge的后台首次入世界转向：宿主未获焦点、W有效、Operation完成，但gameCaptured=false且朝向不变。
参考端只移除allowHostMouseGrab内逻辑capture对宿主焦点的依赖，同例修后第一次相对输入即生效，hostCursorCaptured仍false，正常退出。
调查中的启动夹具前提失败与产品失败分别保留；未使用宿主输入、自动暂停返回、重放或直接改yaw/pitch，第三方组合仍待使用方复测。

### 2026-10-20 E UI参考端增量验证

开工检查点398f72ea9ed0efadbdfdf3a3cf61e7e55b5888e1已SSH签名并正常推送develop，标题仅Update、无正文、作者/提交日期相等；本Goal此后未再次提交或推送。

[参考命令协作结果](../../validations/conformance-world_read/result/26.3-neoforge-command-collaboration.json)6项Live通过。
权限夹具在真实服务端当前玩家权限源安排撤销/恢复，重发原生命令树；撤权后单条命令在发送前拒绝，批次在拒绝项停止，恢复后Native及真实MCP无输入Lease提交成功。
以help随机nonce作为服务端FIFO标记，未修改OP表或世界元数据；正常退出、端口释放、held=0、消费源码运行中不变。
夹具先前错误采用新版已移除的daytime语法，随后采用单人世界无需该权限的seed，均失败并保留；最终从真实TimeCommand构件确认time query gametime的完整语法与权限要求，未修改产品解析/授权策略来迎合测试。
这些是自动权限变化，不是用户真人权限调整、协作或物理Esc验收。

[参考UI增量结果](../../validations/conformance-vanilla_ui/result/26.3-neoforge-ui-completion.json)13项Live通过。
包括真实右键拆分及Shift槽位转移、HUD组合就绪和Native/真实MCP结构化失败、建议列表typed选择/恢复/旧owner拒绝及网页USER隔离、实际物品装饰和上下文条绘制。
物品耐久、冷却自然结束、经验、真实held跳跃条和定位标记分别观察，F1/Inventory遮挡不由内部玩家数据推断。
保留的失败分别来自工具落脚点、错误wait路由和MCP整数负控，不把修正后的工具当作旧候选成功。

[参考本地DEBUG结果](../../validations/conformance-vanilla_ui/result/26.3-neoforge-ui-local.json)7项Live通过。
标题自然倒计时跨6个实际clientTick不使token失效，timing修改/恢复及整组clear独立验证；旁观菜单选中高亮不调用激活，同索引严格no-op；页眉页脚typed文字通过真实MCP/网页隔离。
NowPlaying和Friend原生hide后分别跨6个实际tick没有复现SHOW；未声称音乐、好友服务或所有Toast呈现完成。
两个UI增量均正常Save & Quit和关闭，endpoint已移除，消费源码运行期间不变、Persistent Write调用0；不代替其余四端、完整M-02/03/04/07或人工验收。

[Forge命令协作结果](../../validations/conformance-world_read/result/1.20.1-forge-command-collaboration.json)亦6项Live通过，正常关闭、held=0且消费源码未变；没有把参考结果推定到其余Target。
[参考相机HUD结果](../../validations/conformance-vanilla_ui/result/26.3-neoforge-hud-camera.json)7项Live通过：准星/F1/第三人称/Inventory、换物品后的攻击指示条、南瓜和真实held望远镜及释放。
这些是实际原生绘制参数，不是最终像素/视觉验收；Portal、睡眠等未跑分支仍待验。

导航增量暴露并区分三类失败：离线账户的Telemetry入口原生禁用，保持拒绝，不强行开放；失败后的release_all即使held=0仍增加输入序号，不能误认为实际点击。
真实AutomationEngine测试确认禁用点击在任何移动/按键前拒绝；工具断言同时检查唯一清理序号、指针和界面未变。
GameModeSwitcher悬停的原生高亮会在移动中变化，守卫把自身效果判为selected失效；修前单测和Live失败保留，修后33项手势及6项槽位测试通过，原生hover/释放modifier后的模式改变与恢复已实际通过。
此修复仅允许hover的高亮结果变化，click选择前置值、身份、几何及取消保护仍严格保留。
随后Dialog入口的失败来自两个probe按钮同坐标，实际点击触发了排在前面的Toast按钮；已调整probe布局，不改变产品UI或用坐标绕过。
导航尚未整体通过，失败结果保留在外部调查目录，不将部分通过写成M-02闭合。

M-07自动权限变化现已五端各6项通过，均正常关闭、held=0、endpoint移除且消费源码未变；仍不代表真人协作或物理Esc。
本地DEBUG专项新增1.21.1 NeoForge 6项、26.1.2 NeoForge 7项及Forge 6项实际通过，适用Native类型按版本分别验证，未由参考端推定。
Forge启动曾因新增纯Java Toast采样与Target处在同一Java包导致ModLauncher模块冲突；已移入runtime_core独立core包，并以Forge实际进入世界及正常退出证明修复。
相关三端Toast单元测试、五端玩家列表/模式菜单/USER投影局部测试及真实Hook门禁通过；新Hook副本正负控49项通过，不等同于五端最终UI矩阵。

参考玩家列表绘制专项6项实际通过：本地原生client packet夹具覆盖80行、排序、显式未列出条目、页眉页脚、ping图标、分数格式及心形回退，清理后正常退出。
真实部分裁切的分数只公开几何与阶段，不返回不可见全文；共享USER hud_text投影移除DEBUG全文value，而可编辑输入框和DEBUG原值不变。
这不是多人网络或远端服务验收，旧的缓存全文预期及伪造atlas测试字段失败均保留，不修改生产边界迎合断言。

参考驻留Toast专项5项实际通过，Friend和NowPlaying的进入、完整显示、部分裁切退出及自然结束分别观察。
真实Music Toast选项经USER语义滚动开启并恢复；MASTER始终为0，不使用隐藏节点、DEBUG设置或手写坐标。
适配26.1.2仅存在NowPlaying的Native差异，原本不存在的Friend不以模板代码虚构；局部测试通过不代替对应Live。

参考晚期文字专项9项Live通过，包括Inventory实际效果文字与tooltip、背景/焦点聊天、F3和自然消失动作栏，以及静音字幕方向/自然结束和真实唱片USER交互产生的提示。
输出仅取完成的原生draw；设置经真实USER操作并恢复，最终正常保存退出、endpoint移除且消费源码未变。四端端口随后完成局部编译与专项测试，尚无据此推定其Live。
旧端tooltip使用真实ClientTooltipComponent/Font参数及同owner作用域，不把26.3私有类或Neo补丁方法带入其他Target；实际Native ASM断言在各端单独核验。

参考相机补充专项首项传送门实际通过，随后睡眠规则查找失败；原始结果保留在外部调查目录。
核对Native后，工具改为实际Game Rules搜索框筛选sleep，再按可见字段的精确ruleId读取，不增大滚动预算、不假设默认值、不改Runtime或规则输入。

相机补充复验中，传送门、真实放床/睡眠/原生按钮醒来及恶心贴图与Screen Effects恢复已通过；最后暗角步骤因工具未匹配原生Show Vignette文案失败。
从当前Minecraft构件的en_us原文与VideoSettingsScreen原生方法核实入口后，只修正工具/其mock文案，未改Runtime或扩大查找预算；原始失败单独保留。

参考HUD变体7项Live通过：Native受伤闪烁结束、真实粉雪冻结/解冻、Boss颜色/分段与视口边界/隐藏移除重排，以及Fixed侧栏显示而不暴露底层分数。
正常保存退出、endpoint移除、消费源码未变，仍不代表其余四端或所有纹理/参数组合。

旧两端恶心Late采样、Boss原生绘制及相机对应专项各24项Java测试通过；两现代端Dialog语义与Late专项、四个非参考端新增probe编译通过。
Hook实际源门禁及真实源码副本82项正负控通过；其中未启动游戏，不将其写成五端Live。
参考导航主体8项与自有loopback连接取消/断开2项实际通过，正常退出且消费源码未变。
确认页首5项已实际返回，GenericWaitingScreen的原生延迟按钮使过早选择失败；工具改为等实际启用，原始失败保留。
为避免重复已通过业务，后续确认/恢复复验使用ui-native-pages局部分组；该分组不代替完整all结果。

Fabric H-08专项随后6项Live通过，本地Native packet实际绘制、Fixed/Blank、心形/窄宽回退及清理均确认，正常保存退出、endpoint移除且消费源码未变。
该夹具没有创建服务器玩家，不据此宣称真实多人联机完成。

参考保存专项第一次被分组名称过滤，只有启动/收尾，原始PASS文件保留但不接受为提示证据；修正分组并增加实际样本必需断言。
随后真实4项通过：原生暂停保存、实际USER保存文字、同PID保存完成/flag清除、自然淡出与held=0，正常退出且消费源码未变。
诊断屏障最多3秒，仅安排原生保存状态已成立后的观察时间，不写isSaving/alpha、不额外调用保存，也不是性能测量。
Forge移植首次观察超时，原生保存本身已完成；失败保留，不放宽UI draw条件。参考确认页15个Native页面/安全操作亦已实际完成。
恢复Progress探针原先以含样式Component.equals识别禁用Cancel，原生灰色inactive样式使正确的禁用状态被漏记；仅修正适用三端probe的完整内容匹配，真实active=false断言不变，同源Restore识别一并修正，仍待Live。

Forge保存提示调查以47.4.28官方sources及patched构件交叉确认：Minecraft实例化ForgeGui，其render只遍历26个默认Overlay，不调用Gui.render；默认注册表没有saving/autosave。
Gui.tick(boolean)仍更新autosave，因此暂停不是缺失原因，返回gameplay也不能使未注册提示出现。1.21.1 NeoForge实际注册SAVING_INDICATOR，不能一并判为旧版本不适用。
Forge验证改为当前PID/fixture的真实ForgeGui和默认注册表适用性证明，输出NOT_RENDERED_BY_DEFAULT_FORGE_GUI、userDrawValidated=false；不arm、不等待屏障、不人工调用原版绘制。
此前一次真实保存成功但提示超时的失败保留，不改写为USER绘制通过；其余四端仍要求真实保存及独立USER/Native绘制证据。

参考确认及一般恢复复验已通过，包含Progress的真实cancel flag与后续tick返回。
辅助世界Healthy Back失败源于probe把getWorldDir存档容器误当世界根，改用getLevelPath(ROOT)后实际通过；五端cleanup真实异常报告补齐，不吞错。
真实优化器在Cancel派发前自然完成时，目标预解析可返回UI_NODE_NOT_FOUND，工具现在与UI_TARGET_INVALIDATED一起仅在独立证明optimize_finished/nativeFinishedBeforeCancel及worker/存储/锁清理时接受该终态，不重放、不计Cancel通过。
重配置页首次读取到正确的禁用Disconnect，但工具以可点击动作过滤使它消失；修正为先定位可见节点、派发前另验active/click，Node11项通过，原始失败及事后真实600tick/断开清理证明分别保留。
清理导航补齐实际Disconnected按钮及重配置等待，不用反复Esc代替原生不接受Esc的页面；遗留客户端随后经项目接口正常退出。
Demo首次参数顺序被NeoForge DevLaunch误解成主类，改为Loader配置完成后追加--demo；随后参考真实启动、介绍/Later、倒计时及正常保存退出Live通过。

参考world-pages随后7项实际通过：健康恢复Back、优化器自然完成与锁清理、真实重配置ACK、600tick前后Disconnect状态及正常断开退出；未把优化器自然完成标成Cancel操作通过。
Nautilus专项的失败前USER诊断记录显示，玩家由(.5,80,.5)被开放水池推至(.5,72.197,-4.559)，两次正常槽位点击已完成，原生距离条件随后关闭菜单。
这是场景水流与立足点问题，不以关闭产品Screen/slot守卫修复；失败原始数据已独立保留，后续围池夹具仍需实际验证。
围池与干燥池沿修正后，参考Nautilus定点Live四项通过：真实潜行使用、四次语义槽位往返，四阶段USER位置均为(.5,82,.5)，正常保存退出且endpoint移除、消费源码未变。
这是partialScenario定点证明，不替代ui-breadth或all；未在Act传送、冻结玩家、重试旧坐标或放宽菜单守卫。
参考相机补充组随后完整7项PASS：传送门、真实睡眠/醒来、恶心贴图与Screen Effects恢复，以及原生Show Vignette开关/USER绘制/恢复；正常保存退出、endpoint移除且消费源码未变，不代表其余Target或最终framebuffer。
参考Hardcore专项2项实际PASS：从标题页经真实创建GUI进入唯一测试世界、实际绘制Hardcore心形、原生保存退出并在世界列表确认新身份；不改世界Hardcore标志、不测试死亡、不碰既有世界。
Forge保存适用性专项随后4项Live PASS，实际ForgeGui默认注册表与同PID夹具对应；userDrawValidated=false、pauseRequests=0、selectedNativePauseSaveCalls=0，正常保存退出且endpoint移除。此处PASS仅说明适用性核对正确，不是不存在的USER保存提示绘制通过。
配方专项先暴露无Screen响应不含treeTruncated的工具假设，按真实wire形状修正HUD路径，有Screen截断与Overlay守卫保留。
随后真实Inventory已右移而USER缺配方书子树，核实五端RecipeBookComponent继承空navigation矩形，生产采样将父节点标为不可见并整枝过滤；参考新增真实capture/USER投影测试修前5项4失败，原生origin矩形修正后相关23项Java通过，仍待Live与移植。
组合专项的真实Bed分支通过后，下一fixture因单件库存残留拒绝覆盖。Native链确认命令替换半床会经邻居shape更新destroyBlock(...,true)产生伴生掉落，不走Creative玩家preventDrops分支；仅修已知自有掉落的清理，不放宽后续未知库存守卫。
配方书原生边界与Tab选择移植后，五端局部编译/测试通过；参考真实类别、过滤和搜索操作通过，选择依据为Native book owner的selectedTab身份，不以会轮播的配方图案差异作为恢复证明。
后续右键展开在移动途中被label过期校验拒绝。原生RecipeBookComponent仅在未按Control时推进轮播时钟，右键消费完整RecipeCollection；验证采用真实Control持有、重新读取目标、右键和释放流程，不改生产点击守卫、不声称普通轮播目标自动稳定。
Control路径实际右键已打开弹层，但USER返回空树：弹层被放在其所遮挡的RecipeBook父节点中，投影裁剪祖先时连同弹层删除。五端采样改为根级modal，通用遮挡与底层隐藏不变；参考真实producer测试修前9项1失败，修后相关27项Java与探针编译通过。
修后实机USER已读到弹层及两个选项；下一次失败来自工具误以为选择后原版会关闭弹层，原生宽屏实际需点击选项外才关闭。后续仅修工具按USER矩形计算背景点击，明确不冒充语义节点点击；不改变Runtime或以隐藏槽位读取替代USER。
Saved Hotbars工具的hover预检误要求Slot动作列表声明hover，而既有Runtime允许可见命中矩形悬停。按既有合同修正并保留点击校验、补缺失矩形负控，Node23项通过；参考独立Live随后实际完成保存、清空、载入及空行恢复，正常保存退出、endpoint移除且消费源码未变。
配方修正后的参考独立Live随后通过：真实Control冻结/释放、两个Native选项、语义选择后一次矩形派生背景点击关闭，再由USER确认slot1原木1个与slot0木板4个。held为0、清理无失败、正常保存退出且endpoint移除；该partialScenario不替代五端完整all。工具26项和alpha消费者51项单测通过后冻结，不新增dismiss接口或放宽遮挡。
spotlessApply随后通过，候选产品/构建事实742份，源码指纹cd7c0ee748fa3718dbf4779ffc4d2fdcbd534afddea58e4e2e73ab56739d37d0；首个严格合规子任务0失败/0警告。完整build/check/verifyRelease及37份受影响运行此时仍待完成，不以该子任务结果替代交付。
冻结构建的五端Java、probe编译与发行核对已通过，但整体验证因Hook负控的精确文本替换未命中失败：Spotless把renderSleepFade注解参数拆行，测试没有真正生成remap错误。仅将该副本变异定位到唯一remap=false参数，82项实际源副本正负控随后全部通过；未修改生产Hook或放宽真实门禁，完整check重新收尾，运行矩阵已参考端串行开始。
随后check暴露世界静态测试仍要求Runtime内联聊天包类型，但生产已委托NativePlayerCommands进行typed提取；测试改为同时验证实际委托调用、源参数传播及提取器的真实包类型，不删除配置/发送前权限断言。相关工具测试通过，完整check、verifyRelease与严格合规随后成功；五端build/Java沿用同一冻结产品候选的本次已通过结果。
参考完整all在Boss断言失败，实际USER证据已正确按颜色背景、分段背景、颜色进度、分段进度输出。五端Native字节码确认顺序一致，Forge红条UV为20/90/25/95、宽度182/182/91/91；错误在新工具及其mock采用交替顺序，而非生产采样。仅同步工具与alpha接受器，新增旧错误顺序负控，58项Node通过；旧pilot绑定不同helper，不复用为新工具PASS，原始all失败保留于外部调查目录。
参考冻结候选input-context随后3项实际PASS：普通Screen不改变原生context、真实维度替换取消active/queued而保留Lease、关闭Lease绑定WebSocket退回READ并清理输入。正常退出、endpoint移除且消费源码未变；退出响应未确认时只按独立观察的标题页/新context/held=0判定，不重放操作。
Boss工具按Native纠正后的参考最小专项实际通过并正常退出。重新开始完整all后，并行只读复核发现H-10旧四类Toast进出阶段及02g矿车命令/两种Test编辑页尚未有完整本候选证明，主线程中止该次测试驱动以避免无效长矩阵；驱动中止后遗留任务令自动quit遭遇AGENT_TASK_CONFLICT，随后经Runtime结束自有任务并正常Quit，确认Stopping、无本仓库Java及endpoint。该中止不计完整all通过，既有Boss失败和本次中止日志分别保留；先补既定目录缺口，不将其降为不存在或外部服务。
旧四类Toast复用现有原生回调记录实际绘制变换与250ms上下文帧，进出阶段只发布裁切矩形、隐藏不完整文字；完全显示保留static/header/title等原生阶段。每实例不透明contextKey仅用于通知身份，不是账号或授权；无新增输入/绘制注入。旧两端空Recipe的实际无绘制早返由只读recipes与原生字节码分支核对，不能把所有HIDE返回当成无绘制。五端相关72项Java及probe编译通过；其中旧单测曾直接初始化Native Visibility触发未Bootstrap，改为真实数据对象经过生产capture的测试，原版render早返另由ASM证明，不伪称JUnit启动游戏。
参考首次Toast定点失败源于独立场景未完成原移动教学，夹具的empty前置不成立；显式进入已确认归属的测试显示页后清理通知，不计作教学完成。随后四类三阶段均实际通过，排队退出却误把Native HIDE首个完整帧要求为部分文字遮蔽；仅修工具等待真正裁切退出，生产仍保留完整可见文字。新增五端Java完整HIDE边界与工具双帧负控，排队定点复验通过：五个实际阻塞通知、三个不同clientTick不泄露Recipe、释放后同owner进入/完全显示/裁切退出/消失、正常保存退出及endpoint移除，消费源码未变。两个原始FAIL均独立保留；这些partial结果不是完整all或五端Live。
参考本地管理页定点随后3项Live通过：真实矿车命令草稿及Done拒绝，Test Block草稿及Done拒绝，Test Instance初始化/查询/Reset的受限路径；服务器FIFO计数与实际存储哈希保持，正常保存退出、endpoint移除且消费源码未变。准备与观察为明确诊断夹具，不冒充管理功能执行或本地草稿事务回滚；旧两端只有矿车页，现代三端才有Test页。Probe初次编译的entity.getTags API差异已按实际构件修正为现代entityTags、旧两端getTags，未放宽自有实体过滤；工具过滤的组不再退出重进世界或无条件Arrange，16项Node正负控通过。
上述增量随后spotlessApply通过，重新冻结744份产品/构建事实，指纹a0cd6b5e1661dbf414e57f4f367b392cd3c47772530e2b453753e1865c9085c7。参考完整all重新串行开始；两次定点Toast与管理页结果保留其真实partial范围，不替代新候选37份交付矩阵，发行JAR仍待重新收集核对后独立安装。

参考all在网页消费者的第二次启动失败：port=0分配到6566，Fetch按真实浏览器的禁止端口规则拒绝bootstrap，并非Runtime响应或UI节点失败。原始FAIL保留于外部调查目录reference-all-web-bad-port-before.json，客户端正常退出且endpoint消失；不以raw HTTP绕过浏览器限制冒充可用。
同批完整构建的严格合规子任务为0失败/0警告，但整体check因旧两端RecipeToastUiAccessor未登记公共Native适配器精确目标失败；保留失败，不将已通过子任务替代完整门禁。修复只补真实RecipeToast目标和只读recipes形状的审查/负控，不改生产Hook或放宽任意适配器限制。

网页自动端口修复将0分配限制为49152～65535，只有实际EADDRINUSE有界重选；显式浏览器受限端口明确拒绝，不偷偷换固定端口。Companion正式构建及34项直接测试通过，包括真实Fetch bootstrap、Origin/会话隔离、关闭解绑及碰撞，不新增依赖或更改Runtime权限。Toast精确适配器及负控随后实际85项通过，alpha接受器55项通过；这些自动检查不替代仍需执行的最终Live矩阵。

该冻结候选的最终spotlessApply及完整build/check/verifyRelease随后通过，严格合规检查2055份文件、0失败/0警告；日志e-ui-final-build-check-after-hook保留。参考all已实际越过原网页失败点，完整37份实机矩阵仍在执行，未提前刷新alpha或改写旧失败。

随后参考all已通过HUD与前五类导航，进入Dialog时超时。实际failureTree为LifecycleScreen；新Toast Lifecycle入口与既有UI Navigation入口均为(8,107,128,20)，完整all同时启用时原生点击命中另一个夹具。原始FAIL保留于reference-all-probe-button-collision-before.json，正常退出、endpoint移除且源码未变；只修probe入口布局并补真实五端源非重叠测试，不改生产输入守卫或直接调用目标回调。

五端probe入口移至(8,227,128,20)后，真实源码的非重叠/视口边界及107碰撞负控8项通过，五端probe编译与格式化通过。参考Dialog语义链接及实际页面替换已定点PASS，但同次局部Runner随后因未选择的owned-connection组仍要求两次连接而FAIL；保留原始结果，不称整体通过。工具仅在没有建立listener时跳过组后断言，零选中及完整TCP正负控5项通过，完整组的两次实际连接/关闭要求不变。

新入口的参考Toast定点随后整体通过：四类各三阶段及消失、五个阻塞通知和排队释放、暂停返回/保存退出、exit0与endpoint移除均确认，消费源码未变。该结果明确partialScenario，不替代完整all；产品744份事实指纹保持a0cd6b5e，继续固定交付矩阵。

新完整all随后实际通过Dialog、连接、确认/恢复、600tick重配置、三个管理页及配方/热栏，晚置移动教学却未见通知而超时；原始reference-all-late-native-tutorial-before.json保留，正常退出且endpoint移除。Native调查确认移动/转向各累计超过40tick才完成，Creative会转NONE；长等待本身不结束教学，首次提示需100tick。大量先前玩法、世界卸载/通知清理会使晚置前置不再可靠，不能把无通知推定为自然完成。原动作块移至首次world fixture之后、任何后续玩法或通知清理之前，等待完整可读Native提示再操作；9项工具测试通过，不改options/基准/教学状态，也不将fixture清理冒充玩家完成。

提前教学的参考hud定点随后整体PASS，原版提示完整出现、GAME_ROUTED跳跃与相对转向后自然消失、held=0及正常保存退出/endpoint移除均确认。其partial范围不冒充完整all；余下Advancement/Recipe/DEBUG消费者等待已只读复核，没有因新增部分裁切合同而机械更改不相关断言。

下一次完整all越过教学后，在typed HUD消费者比较失败：网页USER实际字段为原生draw的label，工具却拿DEBUG value字段比较。原始reference-all-hud-public-projection-before.json保留，正常退出且endpoint移除；Runtime边界不改。三个HUD消息消费者显式映射USER label，普通编辑器仍比较value，私有字段/消失owner限制保持；6项正负控拒绝用DEBUG值补过错误或空绘制。参考ui-debug-hud定点随后真实MCP修改/恢复、网页USER校验与正常退出整体通过，未将旧完整all改为PASS。

余下Toast消费者定点暴露真实生产回归：新增observed分支提前返回，漏掉原有bounded debugLabel，按既有DEBUG字段定位原生owner因而失败；resourceVersion并非根因，不能通过放宽定位器掩盖。修前结果toast-debug-observed-label-before.json保留。五端实际observed出口补回2048字符有界字段，USER仍剥离，部分裁切仍无隐藏正文；生产出口→DEBUG/USER回归及实际toast分支ASM接线检查已接入，参考8项Java通过，Native消费者复验进行中。此时a0cd6b5e不再作为最终候选，待修后重新冻结，不改写原始FAIL。

修后参考ui-debug-toast实际6项通过：本地可见性owner、驻留对象版本、原生reset与进度恢复、旧版本拒绝及单向关闭均经Native执行；正常保存退出、endpoint移除、消费源码未变。USER仍无debugLabel，定点不冒充完整all。其后启动spotlessApply及完整build/check/verifyRelease/严格合规，计划在重新冻结候选上串行完成37份既定受影响运行，不增加历史65场景或新性能测量。

重新冻结候选3466a65ca17e8302821273201866bcbe401b592229470769968850eb900bba13，744份产品/构建事实。14:31完整spotlessApply/build/check/verifyRelease/verifyCompliance --strict成功，248任务，日志e-ui-final-build-after-debug-exit.log；五端Toast生命周期42项Java通过。随后从参考端重新开始37份既定交付矩阵；尚未发布，不将定点或原候选结果改写成最终PASS。

参考all随后实际越过HUD/Toast两个消费者失败点、旧四类Toast三阶段与排队、玩家列表和旁观流程，但效果组把所有status_icon误计为效果图标，实际4而非2。Native效果之外的准星和暗角节点本身正确；修前结果reference-all-effect-role-overmatch-before.json保留，正常退出、endpoint移除且消费源码未变。只将工具的计数与清理限定到既有hud.effect身份，并用混合原生身份、隐藏效果额外计数和清理后剩余非效果图标补6项Node回归；产品指纹保持3466a65c，正在执行最小效果Live，不重建发行JAR或扩展范围。

效果消费者修后的参考最小Live随后4项整体PASS：两种可见/隐藏效果、F1、透明闪烁和自然过期、最终清理均按实际身份判断，正常保存退出且endpoint移除，消费源码未变；不以该partial结果替代完整all。保留产品3466a65c与本批已通过构建，重新开始受影响矩阵，不原样重跑失败断言。

下一次参考all在保存指示器证据读取遭遇ENOENT，稍后同进程记录已存在；原始reference-all-saving-proof-read-before.json保留，正常退出、endpoint移除且消费源码未变。保存probe的同步report将pending以非原子REPLACE_EXISTING移动到结果，替换窗口可能暂时无文件；同仓库重配置probe已采用ATOMIC_MOVE。仅将五端保存probe改为同目录原子替换，不加读取重试、不吞IO错误、不复用旧PID/fixture、不重放保存。18项工具回归含真实五端发布形状及既有身份/故障/清理负控通过，产品3466a65c和发行JAR不变，开始probe编译及最小Live复验。

原子发布修后spotlessApply、五端probe编译及相关24项工具回归通过；参考hud-saving最小Live4项整体通过，实际USER保存文字、同Native保存完成/清旗、淡出、held=0、正常保存退出及endpoint移除均确认，消费源码未变。恢复既定最终矩阵，工具与probe修改不触发无关历史或发行重构，未发布新快照。

恢复后的all在同一探针发布路径发生Windows AccessDeniedException，导致server tick崩溃；这次cleanShutdown=false，不能沿用前次最小Live的干净退出结论。原始reference-all-saving-publication-crash.json及saving-publication-native-crash.txt已保留。五端probe仅在已复现的AccessDenied上进行最多8次、间隔10ms的原子替换，最终异常或中断仍明确失败并保留记录；不加非原子回退、读端重试或产品诊断。开始实际编译方法的Windows文件锁回归及probe编译，尚不宣称该竞争已闭合。

五端probe编译及spotlessApply随后通过。仓库外harness直接调用参考实际编译UiSavingIndicatorFixture.publish，类SHA-256为8270346743f08eeda07960a8765e10ca5016da638cb75c2fdd08d9419450f6b1；四项Windows文件系统回归通过：无锁原子替换、NOSHARE_DELETE锁内实际观察到publish重试等待再解锁成功、持续锁耗尽保持AccessDenied及旧/临时完整记录、中断保持IOException原因与中断标志及两份记录。结果在saving-publish-harness/run-20261020-1529-observed-retry；不是游戏性能测量，也不以该方法回归替代Native保存与最终all。

有界占用处理后的参考hud-saving随后4项Native整体PASS，真实保存及USER文字、淡出、输入清理、正常退出、endpoint移除和消费源码未变均确认；不修改此前崩溃的cleanShutdown=false。恢复既定37份最终矩阵，产品指纹3466a65c不变。

参考26.3-neoforge最终all随后整体PASS，正常退出、endpoint移除、消费源码未变且Persistent Write调用0。直接交付接受器发现Hardcore exit字段误按回执读取，而实际saveQuit返回标题树，退出回执已保存在同结果worldExitSamples；修正接受器按同一world输入context唯一绑定回执并加缺失/重复/外来负控，不改原始结果或已通过Runner。矩阵继续Demo及受影响输入场景，不为接受器修正重跑该all。

参考all共139项，随后Demo、输入context、持续输入、方块实体选择器、命令协作及首次后台转向均正常通过，七个既定场景位完成后客户端关闭并开始Forge。接受器另外纠正Disconnected恢复页的真实fixture parent；两处均仅按已有生产者结构对齐，57项Node通过。三个UI证明函数直接接受all原始字节（SHA-256 e8cb42e9aa87143776c54c4ce4d8a0c39ab9fb6e7a1f1b25135f0e2efd491669），Demo证明直接接受其原始字节（44bcfdd874fa0f59aa1b3c8956daddfcb952c10d3072156f92fdd9ba09f93eb2）。退出回执须唯一同context并满足Target/代次/tick顺序与原uiWorldBoundary，缺失/重复/外来/混合伪造、held或重放均拒绝；不手改JSON，不因接受器变化重跑已通过场景。

Forge完整all在传送门组失败，记录为REQUEST_CANCELLED且Native ReceivingLevelScreen，日志确认进入Nether；正常退出、endpoint移除、源码未变，原始forge-all-portal-unload-before.json保留。工具采到覆盖层后立即release-all会把玩家停在门内，再依赖后续HTTP命令复位；改为同一原生W动作完成、USER确认已走过门、再release和复位，地面延长到同一有界自有区域。13项工具测试含未完成/完成却未离门拒绝；Forge最小Live随后4项整体通过且正常退出。产品3466a65c不变；该共享工具确实影响参考all的portal用例，因此只补该all，保留参考其余六场景，不重跑A～D无关结果。四端剩余矩阵继续，参考139项原始通过结果另存reference-all-139-before-portal-tool.json，不改为失败或冒充新工具证明。

Forge下一次all越过传送门与睡眠后，工具按新文本键查找无障碍入口失败；原始forge-all-accessibility-key-before.json保留，正常退出、endpoint移除且消费源码未变。当前47.4.28原生OptionsScreen字节码明确使用options.accessibility.title，而21.1.264使用options.accessibility；仅对旧端选择实际原生键。14项工具正负控覆盖缺失/重复拒绝和清理，不改产品权限、输入或UI，开始最小恶心覆盖层Live复验。

无障碍键修后的Forge最小恶心覆盖层Live整体通过，设置恢复、正常保存退出及endpoint移除均确认。完整all随后实际越过相机、生命和聊天组，在F3文字超时；原始forge-all-f3-native-text-before.json保留，cleanShutdown=true、endpointRemoved=true，消费源码未变。当前47.4.28字节码确认ForgeGui覆盖Gui.render：F3/Demo由renderHUDText实际String绘制，动作栏/唱片由renderRecordOverlay实际FormattedCharSequence绘制，原Vanilla采样虽注入合法但不被调用。

此次只修Forge实际出口，移除无作用F3/动作栏/Demo回调，保留原绘制参数、单次调用和int返回及有界晚期帧；renderDebug/isDemo只区分真实绘制家族，非两者事件文字不伪装。原生方法remap=false、Minecraft调用remap=true，产品Hook被动委托35减至34、替换总数40减至39，输入替换5与可取消21不变。相关原生字节码/委托测试及最小构建进行中，不把原3466构建或其他端结果外推为这次修复通过；其余采样覆盖点经只读核对未发现确定绕过。

Forge最小修复随后spotlessApply、相关13项Java与build通过，Hook门禁89项正负控通过；一次负控仍按旧委托数11改写，已对齐真实10并保持少一项必须拒绝。Native hud-late实测F3、动作栏、字幕、唱片及清理通过，真实--demo也通过并正常保存退出/endpoint移除；Forge保存提示依实际默认注册表仍明确不适用。745份产品/构建事实重新冻结为5d0eac763a6cb5b541ba0f607ff776e618606d67a631e532a2b55625dd05b45a。逐实际绑定核对参考六个未受影响场景仍有效，加Forge Demo共7/37；完整矩阵继续，不为新增Forge专属采样重跑其他Target无关结果，不提前刷新快照。

下一次Forge标题检查仍按旧35委托而失败，正常退出；Runner唯一Forge表项改为真实34。为遵守实际消费范围，接受器只对非Forge或Demo未执行分支使用唯一该字面量的反向整文件hash证明，任何其他字节变化、重复项或Forge all旧结果均拒绝；58项工具回归及真实参考Demo绑定通过，原JSON/digest不改。共享portal流程实际改变的参考all仍必须复验，不加例外。

完整Forge继续越过F3、动作栏/唱片和首个80行玩家列表组，数字分数却全部实际绘制为0；原始forge-all-player-list-before.json保留，正常退出、endpoint移除、held=0，消费源码未变。当前原生ClientboundSetScorePacket(Method,String,String,int)的第二参数是objectiveName、第三参数是owner，probe误传相反顺序。只交换该端夹具参数，新增实际原生构造器ASM与probe源次序负控，生产读取仍要求真实8675309及原生心形/窄列回退，不以0降低断言；正在编译和定点复验。

修后数字分数Live通过，心形阶段则暴露验证器只接受完整9×9图块，实际窗口右缘为6×9且已明确标记withheld_partial_or_occluded；原始forge-player-list-hearts-before.json保留，正常退出。仅允许已知原生UV、空label、有界尺寸且实际贴视口边界的部分心形；16项正负控拒绝内部裁切、错误阶段/文字/尺寸/UV。随后Forge玩家列表专项整体Live PASS：80行/六档ping、数字分数、真实心形及窄列文字回退、原listed/页眉页脚恢复和正常保存退出，endpoint移除、消费源码未变。产品5d0eac76不变，继续完整all，不将该专项冒充完整UI交付。

下一次完整Forge越过玩家列表，在遥测页无条件要求checkbox失败，原始forge-all-telemetry-control-before.json保留，正常退出且消费源码未变。当前47.4.28的TelemetryInfoScreen.createTelemetryButton通过OptionInstance创建CycleButton，21.1.264才使用Checkbox；测试按实际旧端class/button与现代checkbox各唯一存在判断，不修改遥测授权或选项。7项导航工具正负控及Forge设置导航最小Live随后通过，正常退出/endpoint移除。Demo仅排除其init明确未启用的Forge玩家列表夹具源，all及对应Mixin继续严格绑定；真实Forge Demo314项来源核验与原生退出证明通过，原JSON不改。

5d0eac76候选随后spotlessApply、五端build/check/verifyRelease及严格合规整体通过，248项任务、严格失败0/警告0。完整Forge在原生游戏模式菜单失败：真实hover选中survival、释放F3并关闭菜单，但USER玩家仍为creative；forge-all-native-game-mode-before.json保留FAIL、正常保存退出及endpoint移除事实。当前1.20.1/1.21.1原生菜单在下一次render才sendUnsignedCommand，本次key-release的输入permit作用域已结束，命令被正确拒绝而Native忽略false继续关闭；26.x改为keyReleased同步提交，不外推旧两端。正在只为该真实菜单的一次公有render承接原输入生命周期，不改坐标、调用私有提交、放宽未知命令来源或取消合同；当前不能发布快照。

旧两端只在Screen.renderWithTooltip内原有的那次公有render承接原snapshot/permit，作用域结束后才完成原输入future；不跨帧保留ThreadLocal。五端强清理在释放本Agent的F3之前取消实际GameMode菜单，避免下一帧或同步keyReleased迟到提交。旧两端入口刷新真实世界/玩家/连接；Forge原生同维度respawn可替换玩家并保留该Screen，Neo标准respawn通常换Screen，但断连仍可早于Core tick更新。新增菜单专项要求正常切换及还原，再选择另一模式并显式release-all，至少四个客户端tick观察到模式未变和held=0，不以此冒充真人Esc。

编译中发现26.3两端缺少InputConstants限定，以及测试将非AutoCloseable会话放入try-with-resources，已分别最小修正，不改会话实现。旧两端各7项延迟调度与4项语义Java测试随后通过；五端assemble/verifyRelease和严格合规失败0/警告0通过。747份产品/构建事实冻结为90123ba7d178dcdc0012a93c12c6da778ce101d04b0ce75355bf3210ace21dff，开始真实菜单专项与最终相关矩阵；不将编译、合规或纯Java测试当作Native操作成功。

90123ba7的Forge菜单专项实际通过：原生hover选择survival并释放F3后USER模式变更，再正常还原creative；第三次选另一模式后显式release-all，原生菜单关闭，五个后续client tick保持creative、按键/鼠标均0，正常保存退出、exit0、endpoint移除、消费源码未变。此为局部ui-navigation证明，不是完整all、真人Esc或新快照；随后启动本候选32组开发运行的相关矩阵，五端一次一个自有客户端。

随后的完整Forge再次通过菜单正常提交及取消，在自有TCP第二场景命中测试夹具15秒总寿命；第一段原生Cancel已实际通过（整组14秒），两个连续慢速GUI场景却共用一段预算。forge-all-owned-connection-before.json保留FAIL、正常退出/endpoint移除和消费源码未变。仅将自选夹具寿命按两次允许连接各15秒计为30秒，仍只监听loopback、最多两连接/每连接64KiB、不实现或回复Minecraft协议、deadline强清理；不修改产品、GUI节奏、原生连接等待或加入重试。工具与接受器正负控通过，超出实际max+1及擅自扩到60秒仍拒绝，开始最小Native连接/取消/断开专项；产品90123ba7不变。

连接工具与接受器随后65项Node测试通过；Forge最小Native连接场景实测Cancel关闭原传输、第二次有意关闭自有socket到实际DisconnectedScreen并由语义按钮返回，通过且正常保存退出、endpoint移除、消费源码未变。监听/连接全部关闭，未向外部服务器连接，也未用最终清理冒充原生Cancel；继续本候选最终相关矩阵，不把该专项计为完整all。

其后完整Forge已越过两项TCP及恢复页面，在社交入口断言失败；forge-all-social-menu-before.json保留FAIL、正常退出、endpoint移除及源码未变。当前旧两端原生PauseScreen在未发布的单人世界提供menu.shareToLan，而非社交按钮。测试改为严格确认实际LAN节点与缺席社交入口，再通过原版P键读取真实可见的单人限制动作栏；不发布LAN、不伪造账号、不构造Social Screen，也不将这一环境限制算成社交页面操作覆盖。现代禁用入口保持原证明，两分支不混用。

社交工具22项Node正负控及alpha接受器61项随后通过。Forge真实ui-breadth整体通过：床、明确不适用的旧端Nautilus、社交环境限制、矿车命令配置发送前拒绝、配方类别/搜索/多选及Saved Hotbars还原，正常保存退出、exit0、endpoint移除且消费源码未变。产品90123ba7不变，继续完整all；该专项不计完整UI矩阵，不修改旧失败证据。

Forge随后完整all126项通过，三组alpha UI证明直接接受原始结果，Demo、input-context及continuous-input也正常通过并退出。CF-021最终回归却在世界选择页未取得异步存档行，forge-block-entity-world-row-before.json保留FAIL与正常退出。测试原来只等Screen类型，现等待实际固定存档行并要求唯一，再执行语义点击；五项Node覆盖首帧空列表、错误行、重复行拒绝和Lease清理。只修这一测试入口，不改Runtime或重跑已通过的四组；四组实际消费来源仍核对一致，恢复剩余矩阵。

Forge选择器及命令协作随后通过并直接接受原始证据。串行启动器的下一项已在父进程状态核对间自动开始，重复尝试被runner.lock拒绝，未开第二个客户端；现有自有命令协作正常完成，无需删除锁或终止游戏。后续串行编排直接调用同一受限wrapper，保留已通过结果。

NeoForge1.21.1完整all通过延迟菜单、两项连接、恢复及原生600tick重新配置断开后，在特殊床用例清理拒绝超出预期的床数量，neo121-all-bed-cleanup-before.json保留FAIL、正常退出与消费源码未变。测试只保证选中手为空，却把整个hotbar已有红床都当成本用例所有；前序睡眠夹具可产生拾取物，这一假设不成立。清理现记录初始九槽，新增床数量仅允许0或1，只移除新增的一件，后续两个原生帧与逐槽深等证明初始库存恢复；未知额外数量和非床物品仍拒绝清空。24项工具测试含初始一件、总数两件及未知新增负控通过；NeoForge最小真实床用例亦通过并正常退出。产品90123ba7不变，只复验实际消费这一工具的UI all，Forge其余五组保留，不为工具改动重跑无关A～D矩阵。

接下来的NeoForge1.21.1运行在重新配置ACK发布期间崩溃，neo121-all-reconfiguration-ack-before.json保留FAIL、cleanShutdown=false、endpoint已移除和源码未变。实际堆栈为probe的UiReconfigurationFixture.report原子替换JSON时发生Windows AccessDeniedException；并非产品协议或GLFW崩溃。四个适用Target改为复用既有UiSavingIndicatorFixture.publish的有界原子替换，不新增第二套算法、非原子回退或产品读取重试。四端probe编译、30项相关Node以及实际编译发布方法的四项文件锁回归通过，覆盖正常原子替换、临时锁解除、持续锁保留旧/临时完整记录以及中断标志保留；开始最小原生重新配置复验。产品JAR与90123ba7指纹未变，旧失败不改写。

重新配置最小Native复验随后通过600tick切换、ACK、真实断开、保存退出及endpoint移除；下一次完整all也通过此段，却再次在床清理拒绝未知新增数量。neo121-all-bed-baseline-still-fails.json保留原失败，不能将库存基线修正称为完整修复。一次两床最小组合通过；试图在未启用重新配置probe的短实例运行该场景被夹具前置拒绝，不计产品失败或重新配置通过。

床工具现先记录并移除自有坐标区域内的旧红床物品实体，通过有界USER/CLIENT_KNOWN读取确认空后才建立九槽基线；当前床清理仍最多移除新增一件，不清空未知物品。新增准备前后证据，26项工具测试与63项alpha接受器正负控通过；最小Native复验进行中。旧HUD睡眠夹具可能留下物品历史是调查方向，尚未由完整组合计数证据确认，不提前宣称根因或发布快照。

随后床最小Native及NeoForge1.21.1完整all130项通过，正常保存退出、endpoint移除、消费源码未变；三个alpha UI证明直接接受原始结果。此次准备前后掉落物均空、初始库存红床0、新增0、九槽完全恢复；该事实不能反证或确认历史失败的具体物品来源，旧失败继续保留。继续该端Demo/输入/选择器/命令及其余三端，Forge只重验受工具影响的all，不提前发布。

NeoForge1.21.1 Demo随后通过，input-context却在下界目标高度失败，neo121-input-context-destination-before.json保留原结果及正常退出。实际Native日志确认传送至y=200，USER随后读到同一目标维度、y=128、onGround=true；空中传送后重力已令精确高度窗口失效，不是缺少维度切换。只把固定下界夹具目标改为实际顶层y=128，保留二维/高度误差、独立USER回读、epoch推进、两Operation取消、held=0、无迟到及同Lease要求；15项测试通过。此改动只使五端input-context待复验，不使已通过的UI、持续输入等无关结果失效。现代三端UI串行推进，未改产品或发布快照。

NeoForge1.21.1输入上下文、持续输入、精确方块实体、命令协作及后台首次转向随后通过并正常退出。Forge输入补跑却在异步存档行出现前断言失败，forge-input-context-row-before.json保留；只等待实际目标行并拒绝重复，17项测试通过，受影响的两端输入组再复验，其余组不失效。

26.1.2完整all在睡眠规则入口失败，neo261-all-sleep-rule-entry-before.json保留。官方client.jar实际含WorldOptions/InWorldGameRules/DebugOptions，但入口在Pause的Options内；WorldOptions使用Done返回Options，而26.3从Pause直达并Cancel返回。只适配三个工具的真实导航，原命名空间规则ID合法，按实际树读取而非旧语言资源猜测。原生睡眠最小用例随后通过；世界选项/规则/F3+F6 Debug最小导航首次遭probe写结果IOException，neo261-navigation-hud-probe-io-before.json保留且正常退出；一次原样复跑通过，未重现，不据此归责产品或隐藏原失败。

同一官方26.1.2 Pause字节码还确认未发布单人使用唯一LAN入口，测试及接受器将其纳入实际Social缺席分支，仍要求原生P可见拒绝、零页面操作覆盖。相关工具与64项alpha测试通过。未执行26.1.2分支的其他Target仅可用精确逆整文件字节hash验证原结果；任意额外变化、重复分支、错误路径或26.1.2本身均拒绝，Neo1.21原all130来源/三个proof直接通过。现代三端完整矩阵恢复，产品90123ba7不变。

26.1.2完整all随后138项通过，正常退出、endpoint移除、消费源码未变，Regression/Known/Finite三接受器直接接受原结果；Options中转/Done、Social缺席、床与Nautilus、三个管理页均为本次实际结果。HUD probe写结果失败未在该完整运行再现，旧失败保留，不宣称完成文件系统稳定性验证。继续该端其余组、现代两端和Forge受影响补跑，尚未发布快照或进行真人验收。

26.1.2其余五组随后通过并逐次干净退出。22:00逐份核对32位的当前候选来源及实际样本，16位有效：Forge四位、Neo1.21六位、Neo26.1六位。其余为现代两端13位及Forge all/两端input-context三位，不能使用旧结果；五份独立安装尚未运行。Fabric完整UI已串行启动，未刷新清单、快照或latest。

Fabric完整all139项及其余五组随后均实际通过并正常退出；原始all三组接受器直接通过。其第二条自有TCP连接建立约49ms后主动关闭，尚未发送握手，原生Disconnected页和语义返回真实成立；fabric-e-ui-final-proof.log保留接受器误拒绝，原JSON不改。接受器仅对第二条intentional_owned_disconnect允许0字节，并要求accepted≤close-request≤closed≤真实夹具寿命、同cleanup连接及Native页面；第一条Cancel仍要求收到1～65536字节和对端关闭。65项测试含五端零字节正例及时间/来源/字节/页面负控通过，fabric-e-ui-final-proof-after.log接受实际139项结果，不冒充Minecraft登录成功。

参考端26.3 NeoForge随后在图形后端初始化时以0xC000041D退出，未进入Title或执行UI用例；neo263-all-native-launch-before.out.log及.err.log保留。没有新结果可计通过，按约定只进行一次原样启动复试；22/32开发位完成、五份独立安装待办，候选产品90123ba7不变。

Windows Application事件1000/1001与实际游戏PID125612对应，报告SDL3.dll 3.4.14.0同偏移0x15d921的0xC0000005/0xC000041D，neo263-all-native-launch-before-windows-events.json保留原事件。一次原样重试已进入Title和固定世界并继续UI用例；不据此宣称已找到SDL触发原因、修复原版运行库或验证任意启动稳定性。

参考端原样复试的完整all139项随后通过并正常退出，neo263-e-ui-final-proof.log三接受器直接接受原JSON；其余Demo、输入上下文、持续输入、坐标选择器、命令协作及首次后台转向也逐次通过、干净退出。首次转向实际记录未聚焦标题页接管、语义进世界及首次相对输入改变朝向；没有抢焦点或重放输入。23:10开发位29/32完成，剩Forge all及旧两端input-context；五份独立安装与最终构建仍待，尚未发布新快照，原SDL3失败不改写。

Forge完整all126项随后通过并正常退出，forge-e-ui-final-proof.log三个接受器直接接受原始JSON；旧两端input-context亦通过实际异步存档行、下界固定目标、active/queued取消、held=0、同Lease及真实WS断开。23:36全部32开发位完成，e-ui-final-32-audit.log逐份校验747份候选事实、实际消费工具及原始样本，指纹仍为90123ba7，humanAcceptance=NOT_PERFORMED、fullMilestonesComplete=false。依原独立组定义，M-02/03/04的7/1/9组关闭，总剩余58→41；M-07的1组真人仍待，共同交付留在M-10，不把0或V写成完整领域READY。开始最终构建和五份独立安装，未刷新快照。

最终全仓检查首先在Forge NativeInputContextBoundaryTest的owner回调查找失败，原e-ui-final-build-check-release-strict.log及forge-native-input-boundary-before.xml保留。菜单调度已将两参数包装器委托给携带Snapshot/Permit的四参数owner，测试却只查包装器的一级lambda；生产刷新仍在真实owner中、withTakeover之前。仅修旧两端测试沿真实编译委托链检查，额外核验唯一签名、Snapshot/Permit捕获顺序，原刷新顺序、无monitor及保护区断言均保留，两端各8项随后通过；未改产品，不重跑32份Native。

23:56最终spotlessApply/build/check/verifyRelease/verifyCompliance --strict整体通过，248项任务、合规2099文件失败0/警告0，原生不适用与人工待验没有改成PASS。e-ui-final-32-after-format-audit.log再次直接接受32原结果及747份当前事实，产品仍为90123ba7；五JAR已收集，开始五端单最终JAR独立安装入世界，未生成或发布新清单。

独立安装首端26.3 NeoForge在官方安装器下载Guava listenablefuture时发生TLS EOF，尚未进入游戏，neo263-official-installer-tls-before.log/.json保留。一次原样复试通过，未关闭证书验证、改TLS或补开发类路径。至2026-10-21 00:06五端均在全新自有目录、单产品最终JAR及官方Loader/正常运行库下完成Title、endpoint/握手、USER客户端世界读取、同Lease进世界、正常保存退出与exit0，端点移除且无新崩溃报告；实际制品及747份当前事实均绑定，不外推成制品全UI验收。

e-ui-alpha-publication.log发布七文件20261021-0006-90123ba7并更新latest，warnings为空；仅回收其自有旧latest备份，三份最新与在用旧批次保留。e-ui-published-seven-file-independent-audit.log独立校验七文件列表、五JAR/手册/清单与build逐字节及SHA-256一致、latest路径、32开发/5安装的37份原证据及当前源码。profile=e_ui_trial_v1，humanAcceptance=NOT_PERFORMED、performanceAcceptance=NOT_MET且无新性能测量、fullMilestonesComplete=false、Persistent Write调用0。七个反馈编号同步为已交付/待使用方复测，不通知其他线程；M-07真人组及完整双里程碑未通过，集中人工步骤交用户后停止。此后无新增提交、推送或标签。

### 2026-10-21 E世界：检查点与CF-022

59号按原文创建Goal并读回一致，先核对三处风险：DEBUG分页漏项/authority/加载边界，持续输入取消后的迟到，既有代表流程与原版独立分支的覆盖差异。
唯一检查点82f0b0599cee874773c87e46feea0a8b58ff0d24正常推送develop；604份项目文件，Update无正文、SSH签名已验证、作者/提交日期均2026-10-21T01:17:37+08:00，HEAD与实际远程一致。
原始result、世界、构建产物和本机配置未纳入；剩余历史结果工作区不清理、不覆盖。此Goal不再提交/推送。

参考端同一固定副本用原生命令准备160个原版木桶，三批DEBUG方块读取对照确认160格；旧接口只返回128项且没有续页元数据，实际FAIL、sourceChangedDuringRun=false。
失败记录保留在D:/Workspaces/Artifacts/MineCraftProtocol/Investigations/20261021-e-world/cf022-reference-before.json；160格均恢复为空气，正常Save & Quit、exit0及endpoint移除。
修复为每页最多128项、8192已加载候选上限及按坐标key字典序seek续页；单Runtime32个游标、60秒TTL，绑定弱引用的实际世界/玩家及anchor/query。
每页LIVE，不伪装跨页快照；上限超出明确拒绝而非截断宣称完整。无原生Map顺序依赖，不在已删除前项后按offset跳过目标；序列化权限/预算与authority不变。

五端block-entity-pages专项逐端运行，160个容器通过Native及真实MCP stdio续读完整返回、无重复；查询变更与client_known续页拒绝，零输入派发，恢复160格及干净退出。
实际固定结果为validations/conformance-world_read/result/<target>-block-entity-pages.json，包含原生命令准备、原版缓存对照、各页及恢复样本；不以USER区域分页代替容器DEBUG分页。
共享分页/selector及五端实际编译派发边界测试通过，Companion转发/校验测试通过；OpenAPI首次错误使用3.1的const被3.0.3生成器拒绝，改为合法enum后Java/TypeScript生成与build通过，不关闭校验。
本项只关闭CF-022，不提前关闭整个06b或世界里程碑；无性能测量、真人验收、快照发布或Persistent Write。

06b增量新增player.inventory.slot.set、player.equipment.set及block_entity.slot.set，沿现有namespace Arm、owner-thread permit、资源版本与原值条件提交，不申请输入Lease。
替换仅支持id/count和literal customName/damage，按原生堆叠/耐久域校验；玩家物品读取新增名称/耐久投影，旧六项inspection加三项至九项，既有六链证明不宣称新三项已验。
参考端先编译及Native实机通过，再移植其他四端：五端world-debug-items各六项通过，包含Arm/READ拒绝、三种原生setter的修改/版本stale/原值拒绝/恢复、非法堆叠/raw metadata负控及真实MCP结构化回执。
Native Pause用于稳定测试资源，Arrange输入单记，DEBUG Act派发数0；槽位与头部均从实际空位置开始，恢复后比对原生容器序列化，再移除自有测试木桶。各端sourceChangedDuringRun=false、正常退出、端口/endpoint清理及Persistent Write=0。
比较标准为原版ItemStack、库存/装备/Container setter和原版序列化，不以自己计数器或业务直接写入冒充USER输入；本组是DEBUG测试，非玩法或人工验收。
实际26.3原版RandomizableContainerBlockEntity字节码确认getItem/setItem会unpackLootTable，序列化键为LootTable；因此待展开容器在访问槽位前明确DEBUG_CONTAINER_LOOT_UNRESOLVED，避免隐式生成整箱战利品。
这一例外已有共享Java及编译派发边界约束，未在本增量宣称未开奖励容器的五端实机负控完成；06b与M-06仍保持进行中。原始结果为各Target固定world-debug-items.json，不发布快照或扩大到M-01/M-09。

随后五端world-debug-items各七项通过，新增真实pending loot原版容器负控，拒绝前后完整序列化/版本不变，再经专用Fixture恢复；探针只用于Arrange且不进入发行JAR。
Forge首次七项均通过但关闭Fixture后仍在原版父Pause页，清理失败，world-items-forge-loot-before.json保留。工具改为读取实际Screen并逐层关闭已知父页，不盲发额外Esc；修后Forge及其余端恢复、保存退出、endpoint清理通过。
强化CF-022用例将唯一stone7标记放在首128项之外，再通过真实MCP以32项序列化分页读全160容器。该用例先揭露outer metadata错误声称无副作用；cf022-serialized-effects-before.json保留。
修复原生捕获副作用传播后五端均读全并与原版Items序列化一致；空/未序列化原生读仍为none，Provider副作用另行记录。实际生产formalizer及capabilities五端测试核对，不用新常量自证。
06b按批准物品/容器目录及明确只读边界闭合，M-06其余七组仍待；最终冻结回归和制品交付尚未执行，不以当前专项冒充整个M-06或新快照。

06a第一增量新增player.state.set与entity.state.set，使用既有独立namespace Arm、完整资源版本和typed部分原值条件；值域/类型/原生适用性在任何setter前校验，不借用输入Lease。
玩家新增饱和度、最大空气和等级内经验点精确投影；实体新增空气、吸收及silent/glowing，现有native位置/速度、生命/装备保留。公开vitals/living/common_state投影同步包含对应字段，不把默认全量读取通过冒充显式投影已验。
五端world-debug-state各六项实际通过，读取/修改/版本stale/原值拒绝/恢复与真实MCP回执对照native setter和深读取；Native Pause只稳定诊断Arrange，不作为USER玩法。
食物/饱和度/空气、等级与点数分步、逻辑速度、已加载ArmorStand生命/标志分别恢复，所有DEBUG Act输入派发差0，保存退出、端点释放、sourceChangedDuringRun=false及Persistent Write=0。
初次共享编译因固定Gson无JsonObject.isEmpty失败，改用size()==0后参考端及五端编译/生产声明测试通过；没有升级依赖。当前11项inspection是目录，不将其计为11条完整新验收链。
06a尚缺完整属性/效果等目录，不关闭该组；本轮不测性能、不进行真人验收、不再提交推送，也未发布59号快照。

随后属性读取改为枚举主体实际拥有的注册项，通过只读AttributeMap accessor读取已实例化修饰符，不为观察调用getInstance/getAttribute创建默认实例。
player.attribute.set拓展原生minecraft属性有效范围，保留原有max_health别名；entity.attribute.set及entity.equipment.set使用独立entity Arm和完整实体版本，不借玩家namespace。
API的base批准域比原生可存储再clamp的范围更窄，越界明确拒绝；modifier与effective value只读，文档明确此差异。六个实体标准装备位置本增量已验，其他Native位置及效果仍未据此宣称通过。
参考端先完成八项world-debug-state，再移植与逐端实测其余四端，各八项通过；包含显式字段组与完整Native捕获对照、非max-health玩家属性、实体属性、Native有效域拒绝及真实MCP装备名称/耐久/版本stale/恢复。
world-attributes-five-compile.log及world-attributes-five-contract.log记录五端编译/实际capabilities与commit顺序测试、Companion通过；生产读路径禁止实例化属性的编译测试覆盖五端。
最终各端sourceChangedDuringRun=false、cleanShutdown=true、held=0、endpoint清理及Persistent Write=0。13项inspection只作为批准目录，不冒充13个完整新场景。06a及M-06仍未闭合，下一步是效果与其余家族，不发布快照。

06a随后增加player.effect.set/entity.effect.set，原生active effect完整批准字段及有界hidden链读写；1.20.1按原生八参数/FactorData factory构造，现代七参数，不用NBT反序列化或复用虚假图形缓存。
实体装备按实际Native enum名字与canUseSlot判适用，玩家六位置不变；Mob noAi可恢复修改、aiTargetUuid只读。flying仅在原有mayFly为true时允许启用，不更改授权能力。
参考端先实际通过扩展场景，再移植四端。最终五端world-debug-state各11项通过：保留此前状态/属性/装备/投影链，新增双主体效果/hidden/移除、真实MCP效果null往返、Mob标志及Survival飞行拒绝/恢复Creative。
Mob准备与discard清理使用现有legacy debug scenario，明确只算诊断Arrange/cleanup，不用它冒充正式WD-03生命周期API；所有Act仍无输入Lease，inputDispatchSequence差0。
world-state-full-five-contract.log记录五端编译/生产方法及Shared/Companion验证通过。每端sourceChangedDuringRun=false、cleanShutdown=true、Persistent Write=0；15项inspection为目录，不等同15个完整新场景。
06a按当前批准字段目录和只读理由闭合，位置/生命周期/关系分别归06g/06c/06e；M-06仍剩六组。当前Peer仍限原声明的旧health/block，新正式typed能力不外推到Peer/无Peer。
原生getter/setter/构造与回读一致；base批准域较native存储域更窄的差异已明确记录；真人、任意AI对象、完整世界与最终候选/单JAR均不据此宣称通过。未刷新alpha或额外提交推送。

### 2026-10-21 E世界实体生命周期

06c新增正式entity.spawn/entity.remove。创建使用原生类型工厂、精确位置/朝向及addFreshEntity，不接受NBT/UUID或调用finalizeSpawn；相较summon初始化的差异已在Native/手册登记。
显式selector.entityChunk读取完整已加载成员集和entity_registry版本，最多4096，溢出不给部分可写版本；对象边界所触及Chunk均必须已加载。原生有界getEntities与getChunkNow路径不请求加载。
创建前检查成员版本/可选数量条件，创建使旧版本失效；移除要求对应实体版本及可选类型，拒绝玩家，走discard而非击杀。移除回执保留身份及实际DISCARDED，tombstone不可写，不提供同UUID重建。
参考端先通过后逐端移植，五端world-debug-entities各四个有限验收组通过：模式/Arm/类型/指纹/原值负控、未加载、原生创建与stale/删除/旧身份拒绝、真实MCP创建及清理。
固定副本原生暂停用于诊断稳定；所有创建UUID均单独discard，成员集恢复原值但版本继续递增，DEBUG Act输入差0、held=0。sourceChangedDuringRun=false、cleanShutdown=true、端点释放、Persistent Write=0。
证据为conformance-world_read/result中的五份world-debug-entities.json；构建/组件与生产声明/提交顺序验证见外部world-entities-five-contract.log。初次26.3 ChunkPos字段访问与26.1.2标识类移植编译错误已按实际API修正，无运行失败。
与原生factory/discard及实际成员读回一致；无Peer、任意NBT、召唤初始化、删除回滚不被宣称支持。06c批准目录闭合，定位及关系分别归06g/06e，M-06剩五组；没有新制品发布或额外Git写入。

### 2026-10-21 E世界环境

06d新增world.time.set/world.weather.set/world.rule.set及实际world.environment目录，使用既有world scope/Arm、指纹与独立资源版本，不申请输入Lease。
时钟版本取具名原生修改事件，自然tick不令其失效；天气版本覆盖具名修改和当前flags，倒计时独立推进。明确revisionCoverage，不将控制版本冒充完整环境CAS；可选expectedValue仍精确比较。
1.x只发布真实可写Overworld主时间，DerivedLevelData的其他维度setter为空；自然tick与显式setDayTime在确切native调用点区分。26.x按实际WorldClock注册项读取与设置，26.1.2使用只读private实例/播放字段accessor，26.3使用公开实例。
游戏规则枚举实际可用boolean/integer，发布真实ID与argument域，通过原生setter/callback修改；1.x完整int域与现代有界参数的差异如实保留，不附加现代范围。播放控制/gameTime/seed/worldgen/调度不在批准写目录。
参考端先通过，随后四端逐个实测，各world-debug-environment六组通过，包含真实MCP time往返、Arm/模式/类型/原值/原生域拒绝、时钟ABA拒绝、天气独立计时器和规则恢复。所有DEBUG Act输入差0、held=0、sourceChangedDuringRun=false、cleanShutdown=true、端点释放及Persistent Write=0。
原始自然推进样本五端均记录gameTime与天气计时器实际变化，控制版本保持；后补的“必须观察天气倒计时变化”断言对这五份原始样本独立复核，不改写JSON或重签结果。冻结交付仍将运行当前工具，不把本增量说成最终候选证明。
首轮参考端负控误将无Arm预期为模式错误；Forge首轮用例假定必有非负整数域，均按真实安全顺序/原生类型修正测试，原失败保留world-environment-reference-before.json及world-environment-forge-domain-before.json。
world-environment-five-contract.log、world-environment-shared-check.log记录生产字节码、五端编译/测试、Shared/Schema/Companion与工具通过；26.1.2私有API移植编译差异已消除。新三项Native行为与native setter/argument/回读一致，播放/派生数据边界按上文差异说明。
06d批准目录闭合，M-06剩四组；未发布59号快照、未额外提交推送，人工及最终单JAR仍待冻结阶段。

### 2026-10-21 E世界关系

06e新增entity.vehicle.set/player.vehicle.set、entity.leash.set及entity.owner.set，读取实际vehicle/passengers、牵引缓存和原生驯服归属。专用entity_relationship版本不绑定走动或生命；受影响旧/新参与者也必须提供版本，不接受重复或忽略额外项。
玩家骑乘在同一取消屏障内校验player/entity双scope和Arm，未嵌套取得StampedLock；其他既有单命名空间路径委派同一实现。DebugCombinedAuthorizationTest验证缺授权拒绝、单屏障持有及取消后不再准入；没有新Lease或输入路径。
非强制startRiding/stopRiding保留原版容量、循环和60tick重上车冷却；按原生牵引资格设置/清除，拒绝未解析关系与循环。下马前检查原生邻格/尺寸逃离包络已加载，读取不调用惰性owner/leash解析或存档钩子。
原生副作用与回读一致：挂绳自动下马、骑乘断绳并产生一个实际掉落物、狼驯服补血及解除驯服不恢复生命。测试明确恢复批准生命字段，按新UUID清理掉落；不改原版行为假装自动回滚。
参考端先确认，再移植四端；1.20.1用只读Mob缓存accessor及原生旧签名，1.21.1用Leashable/ownerUUID，26.x用当前原生引用与牵引接口。未构造假玩家或扩大Peer能力。
五端world-debug-relations各六个有限组通过，包含真实MCP、Native调试批次、模式/Arm/参与者负控、stale/原值链、容量/循环拒绝、实际副作用及恢复。自有实体归零，DEBUG输入差0、held=0、sourceChangedDuringRun=false、cleanShutdown=true、端口释放、Persistent Write=0。
原始摘要为conformance-world_read/result五份world-debug-relations.json；world-relations-five-contract.log记录四端移植编译/生产接线测试，参考端与共享、Schema/Companion测试在world-relations-reference-ready.log和world-relations-authorization.log；格式与工具门禁见world-relations-format-tools.log。
测试初期分别误传不支持类型的预期owner对象、未规范化省略null、将夹具放到原生绳长外及假定解除驯服会还原生命，均按实际Native/传输合同修正，不重试掩盖差异；首份失败保留world-relations-reference-before.json。实体discard的当前说明改为“不走死亡战利品但可有原生关系清理副作用”，不改写旧结果。
06e批准目录闭合，M-06剩三组；本增量不是冻结候选或独立单JAR验收，未刷新Alpha、额外提交或推送。

### 2026-10-21 E世界调度读取与中心批次

06f修前参考端实际创建65个原生排队流体tick：world.fluidScheduledTickCount为65，Chunk数组只有64项，缺少省略说明；world-loading-reference-before.json保留真实失败。65个方块已恢复，队列自然排空，客户端正常退出。
新增scheduledBlockTicksResult/scheduledFluidTicksResult，保留数组形状，区分原生getAll排队项、container.count含待展开项、预算和省略；不调用unpack/poll/schedule，不包括已收集待执行阶段，原生迭代顺序不承诺时间排序。共享ScheduledTickRead只负责该元数据和64项上限。
world.simulation只读地区分集成暂停、可用的原生TickRateManager和WorldClock；configuredTickRate不是性能测量。1.20.1没有管理器且暂停字段私有，使用只读accessor；其余端公开getter。不新增模拟控制、输入或权限。
正式快照本来已过滤旧REQUIRES_NEW_HOOK占位；本增量清理产生这些无效字段的旧读取层及对应删除分支，不把它描述为刚修复的正式API错误。OpenAPI保留兼容的可选元数据，生成/Companion构建测试通过。
参考端确认后四端逐个实际运行world-debug-loading，各四个有限组通过，包含READ/Arm/65项预算负控、真实65条队列和多次读取不改变、部分失败保留已应用前缀、未加载目标拒绝及真实MCP。所有测试方块恢复，原生调度自然排空；DEBUG Act输入差0、held=0、sourceChangedDuringRun=false、cleanShutdown=true、端口释放及Persistent Write=0。
结果为conformance-world_read/result五份world-debug-loading.json。world-loading-reference-contract.log、world-loading-schema.log、world-loading-five-contract.log及world-loading-forge-offline-contract.log记录实际生产字节码、共享和各端测试；Forge在线HEAD遇到TLS断连，使用既有官方缓存离线构建成功，不改依赖或TLS安全设置，未将环境重试称为修复。
原版NeighborUpdater会读邻居状态，Level.getBlockState可达允许创建的getChunk；中心场景和直接未加载目标拒绝不能证明任意传播均不加载。当前尚无该边缘的现场证明/阻断，06f仍为P，不将上述四组代替整组关闭。Native说明与计划保留此项。
本增量未测性能、未刷新Alpha或额外提交推送，不证明最终候选、单JAR或完整世界领域验收。

### 2026-10-21 E世界原生传播加载边界

06f修前参考端加载岛实证：正式world.block.set的目标已加载、东邻Chunk未加载，普通石头修改却令邻Chunk变为可用；失败原文保留world-boundary-reference-before.json。根因为原生邻居更新经Level.getBlockState到可创建的ServerChunkCache.getChunk，不是读取接口主动加载。
正式typed block增加直接邻域先验检查及同步原生Chunk入口约束；LoadedChunkMutationScope仅在已授权的实际setter内生效，普通Vanilla调用不变。不阻止后续普通模拟，不提供Fixture或Peer新授权。
原生红石传播可在更远的依赖处停止，此前源方块已变为redstone_block。单项DEBUG_MUTATION_PARTIAL_NOT_LOADED保留实际before/after、资源版本、blockedChunk与retrySafe=false；批次失败项保留同一result并停止余项，即使CONTINUE_ON_FAILURE。Native、真实MCP、证据窗口和Recording manifest/Timeline均保留诊断污染，不误称回滚或干净Gameplay。
参考端确认后移植四端；五份world-debug-boundary.json分别证明直接拒绝源不变、传播中源已变而邻Chunk仍未加载、MCP结构化部分效果、录制污染、夹具恢复及退出。DEBUG Act输入差0、held=0、sourceChangedDuringRun=false、cleanShutdown=true，端口/endpoint释放及Persistent Write=0。其余四端实测含批次余项不启动；参考端在声明同步后又确认当前工具与该余项条件。
收尾发现Hook总数声明遗漏世界守卫，且旧两端06d的原生时间setter委托尚未分列。现将DEBUG_MUTATION、被动世界委托、HUD委托和玩家控制分别公开，保留普通原生setter参数/调用次数及finally；26.1.2的private clock只读accessor纳入精确审查。Reference实际HTTP Hook manifest与当前生产声明一致；其余四端新声明有编译/静态证明，当前功能实测不被冒称其新声明已Live验证，冻结时按现行用例补齐。
world-boundary-five-contract.log、world-boundary-error-contract.log、world-boundary-format.log及world-boundary-hook-contract.log记录各端生产字节码、实际HTTP错误/批次污染、Schema生成及Companion通过；world-boundary-hook-static.log、world-boundary-hook-negative.log和world-boundary-control-static.log记录原生接线与正反例门禁。测试首轮误用blockedChunk字段名、录制停止HTTP方法及门禁错误子串，按实际合同修正工具，不改产品迎合预期。
06f批准目录闭合，M-06剩06g/h两组；当前35组仍未构成领域READY。未发布新Alpha、未额外提交推送；冻结候选、五端独立单JAR及真人项仍待后续步骤。

### 2026-10-21 E世界原生LIVE转移

06g新增当前玩家及明确非玩家实体的原生teleportTo定位/跨维度路径，value保留小数坐标和可选朝向；维度必须已存在，源与目的包络已加载，先解除骑乘/牵引及跨维度摄像机跟随。独立scope/Arm/资源版本、原值及同步原生加载守卫不放宽，不产生玩家输入或存档API调用。
DEBUG world列出现存维度；selector.entityTargets按明确维度/UUID查已加载对象，不隐式追加半径扫描，超maxEntities拒绝。投影前计算完整资源版本；Native schema及Java/TypeScript生成模型保留维度和目标字段，Companion使用相同封闭载荷。
与原生一致：非玩家跨维度重建对象、UUID保留但lifecycle更新；当前玩家换维度改变世界指纹，旧Arm拒绝，必须显式重新授权。速度/摄像机/物品使用和维度触发器保持版本原生行为，返回坐标不代表全部副作用回滚。其同步依赖若触及未知Chunk，沿用部分应用错误/污染合同；后续普通Vanilla模拟和视距加载不受冻结。

参考端先确认再移植四端。首轮返程因原目的Chunk被自然卸载而拒绝，world-transfer-reference-before.json保留失败；改测试Arrange为自有、非持久内存票据，退出前按同类型/位置移除，不为产品增加加载许可。Forge首次返程读取时原生状态仍在推进，旧版本被正确拒绝，失败保留world-transfer-forge-before.json；工具改为等待原生页面完成并真实暂停后读取，不自动重试旧token。MCP重新Arm替换原凭据后，Native批次明确重新申请，不伪装两套Arm同时有效。
当前五份conformance-world_read/result的world-debug-transfer.json各五组通过：准入/未知目标、玩家小数定位、实体重建与精确身份、玩家换维度Arm链、真实MCP及Native batch。还包含精确选择不混入附近实体和预算拒绝。DEBUG Act输入差0、held=0、自有实体0、临时票据0、sourceChangedDuringRun=false、cleanShutdown=true，端口/endpoint释放，Persistent Write=0。
world-transfer-five-contract.log、world-transfer-selected-contract.log及world-transfer-companion.log记录生产接线/共享测试、Schema生成模型字段及Companion通过；world-transfer-probe-ports.log记录各端夹具编译。收尾发现Schema重复sideEffects键，已合并回既有字段后重新生成，不绕过规范校验；world-transfer-docs-strict.log为当前文档80文件通过、严格合规0失败0警告。后续阶段仍按实际影响补验，不把这些专项结果当最终发行或真人验收。
06g批准目录五端闭合，M-06剩06h，当前剩34组；未刷新Alpha，未额外提交或推送，完整领域仍未READY。

### 2026-10-21 E世界Provider与Fixture目录

06h核对批准typed目录与既有独立Fixture路径；原版世界能力不依赖第三方Provider补齐。Provider cap缺少实际修改schema、revision约束与只读原因，现由runtime_core的ProviderInspection统一生产cap和读取声明，保留debugSupported兼容字段，公开debugDeclaration及其独立scope/Arm/版本、只读或不可用理由，不授予新权限。核心inspection目录纳入既有provider.mutate，五端不再各自追加重复来源；注册的可执行validator仍由Provider提供，不承诺任意字段或JSON Schema自动导出。
实际Provider政策保持一致：加载/存档观察被skipped，隐藏修改被permission_denied，不调用采集或发可写token。safe Provider仅为自有诊断计数状态；实际MCP修改/恢复、schema拒绝和原生版本失败前缀已证，不被当作Minecraft温度或第三方兼容证明。Operation完成与batch.result.status=partial分别检查；工具初期混淆两者及政策status，已按实际生产合同校正，不将这些工具预期错误归为产品缺陷。

参考端发现真实Fixture证据遗漏：录制manifest已污染，但活动Gameplay Act仍contaminated=false；world-provider-reference-before.json保留同一次原始结果、实际移动/恢复及计数。ProtocolState现在独立记录活动窗口的Fixture次数/请求，不改Debug计数；两个正式Fixture入口先完成观察再成功响应，响应取消不直接取消已提交上游的观察阶段，继续协作取消未完成上游。原值恢复不抹除窗口污染，窗口开始前已完成的Arrange不污染新窗口；权限、Mode、Lease、Arm和类别不变。
参考端确认后逐端运行world-debug-providers，五份结果均通过声明/政策与拒绝、实际Provider版本/batch前缀、真实MCP、Fixture独立授权/恢复及录制/窗口污染。每端Fixture计数2、Debug计数0、恢复后新窗口干净，DEBUG输入差0、held=0、cleanupErrors为空、sourceChangedDuringRun=false、cleanShutdown=true，端口/endpoint释放及Persistent Write=0。
world-provider-reference-contract.log、world-provider-evidence-contract.log、world-provider-reference-schema.log、world-provider-evidence-schema.log及world-provider-five-compile.log记录共享/真实生产路径测试、生成模型与五端编译；world-provider-companion.log为Companion通过。world-provider-static.log和world-provider-control-static.log分别通过Typed Debug及80HTTP/24MCP门禁，人工标为NOT_SIMULATED/UnifiedAcceptance PENDING；文档门禁见world-provider-docs-strict.log。
M-06的06a～h批准目录五端专项闭合，全局剩33组；M-05八组仍待分支盘点与闭合。此次未刷新Alpha或额外提交推送，不等同冻结候选、独立最终JAR或完整里程碑/人工验收。

### 2026-10-21 E世界05h区域纯功能闭合

对照原生ClientLevel缓存/ChunkStatus.FULL(false)和独立USER点查，新增world-region-functional而不运行region-stream中的吞吐/WS速率比较。五端分别重建跨section的75格，检查无遗漏/重复、原生状态属性、固定查询与单用游标、预算/32格半径拒绝、取消幂等及实际等待30秒闲置到期后的续读拒绝。硬生命周期、未知cell/已知空气区分、取消中断由既有WorldRegionPages确定性时钟测试和实际Native适配边界测试核验，不冒充现场卸载。

Forge首个请求实际返回WORLD_REGION_BUDGET_NO_PROGRESS，未提交游标；修前结果仅保留一份world-region-forge-before.json。新消费者按正式retryAfterMillis、同查询、最多三次处理此明确无进展，不重放成功页、不重试未知失败、不放宽Runtime预算。另一次世界列表刚创建但尚未异步填充，改为等待实际具名世界行出现，不改产品或固定副本。参考端已通过路径不因消费者增加该拒绝处理而重复运行。

五份result/<target>-world-region-functional.json均PASS、sourceChangedDuringRun=false、cleanShutdown=true，正常退出、端口/endpoint释放、Persistent Write=0；未做性能测量。world-region-functional-contract.log记录共享region与参考端Native边界测试通过，资源/运行摘要见既有limited-<target>-world-region-functional。05h关闭，全局剩32组、M-05剩05a～g；尚未冻结最终候选、刷新Alpha或额外提交推送。

### 2026-10-21 世界USER自身反馈与原生分支盘点

WorldClientSnapshot与WorldClientModel补充自身原生姿态、潜行/冲刺/游泳/滑翔、流体/着火/睡眠/死亡、眼高/空气上限、经验及相机类型/是否自身；不增加服务器权限、原始库存、NBT、实体身份或修改授权。参考端实际Native路径确认后逐端移植，OpenAPI及Java/TypeScript模型、玩家说明同步。USER State Frame、Assert与真实MCP在五端保留字段，READ无输入副作用。

独立probe通过既有Fixture Screen的明确按钮采集实际注册表/feature flags/键位及Item/Block使用方法来源。现代物品记录默认组件类型和原生使用动画/时长，1.20.1记录其实际旧版食物/使用模型，不假造现代组件。26.3的矛实际为普通Item加kinetic/piercing等组件，证明不能按类名直接推断覆盖；EntityType包装类也不冒充实例行为。该目录不是产品USER读取面或成员Act已通过证明。

五份<target>-world-user-catalog.json均PASS、源未变化、正常退出且Persistent Write=0。实际items/blocks/entities依次为：Forge 1255/1003/124；1.21.1 1333/1060/130；26.1.2 1506/1168/157；26.3两端均1658/1286/161。world-user-catalog-five-contract.log记录五端实际适配字节码/共享投影、WorldRead及探针编译通过；world-user-feedback-companion.log与world-user-feedback-schema-strict.log分别记录Companion、生成模型/80篇Markdown/严格合规通过。完整WU分支仍待Act，不降低M-05剩余七组。

### 2026-10-21 E世界05a移动与原生相机闭合

movement补齐藤蔓/脚手架、活板门强制爬行与退出、游泳/出水/溺水/熔岩反馈、三视角、实际W到R改键及恢复、灵敏度/适用反转、电影相机和望远镜。设置操作通过原生可见KeyEntry行与更改/重置按钮身份；看不到的行须实际滚动，不读取隐藏行代替操作。1.x原生无X反转，明确NATIVE_ABSENT；非默认设置下语义aim仍通过原版相对输入命中。准备命令为诊断Arrange，Act与独立USER反馈不依赖截图、屏幕坐标或直接姿态修改。

参考端电影相机修前实际派发相对输入后朝向不变，原始失败保留world-cinematic-reference-before.json。对照原生MouseHandler：平滑余量须逐帧消费，不能仅在一次输入回调期间提供逻辑捕获。现委托Minecraft.runTick中的原生鼠标消费者，并复用已有routed来源而不签发回调许可；取消、Lease/接管/原生上下文及GUI/世界平面切换重置原生SmoothDouble。语义aim在电影相机下仍诚实拒绝，不重写平滑算法或yaw/pitch。参考端实际确认后移植四端。

Forge首次完整场景动作通过，但Save & Quit后input.released超时，失败保留world-movement-forge-before.json。原生GUI回调可重入主循环卸载世界；回调返回后再登记held会把已清理状态复活。键/按钮与逻辑按下现在在原生回调前登记，重入清理不再被后续add/put覆盖。参考端input-context验证维度、活动/排队清理、真实Lease绑定WebSocket关闭及退出后，再移植四端；Forge复测完整movement含原失败节点通过，未放宽清零判据或重放退出点击。

五份<target>-movement.json各20项分支通过、sourceChangedDuringRun=false、cleanShutdown=true、held=0、端口/endpoint释放及Persistent Write=0；参考端完整movement先于登记顺序修复，后续input-context为该修复的直接实测，不伪称五份均来自最终冻结候选。world-cinematic-reference-contract.log、world-movement-five-contract.log、world-movement-five-native-contract.log及world-reentrant-input-five-contract.log为实际生产字节码/字段、来源隔离与回调顺序测试。Hook静态与94项隔离正反例通过，实际声明区分原生HUD/世界委托及控制替换；静态不冒充Live。

05a专项闭合，M-05剩05b～g六组、全局31组；冻结候选仍按影响复验。未做新性能或真人验收、未刷新Alpha、未额外提交推送。旁观目标、载具和滑翔继续按原生独立分支验证，不从普通移动推定支持。

### 2026-10-21 E世界05b参考端特殊移动与载具反馈

参考端special-movement在原版GUI进入固定副本后，通过真实键位完成创造飞行、旁观穿墙/语义瞄准后的原生spectate、船/矿车/马驾驶、骆驼冲刺/满座、羊驼乘客、猪/炽足兽道具控制和耐久消耗、鞘翅起降/烟花及取消后自然滑翔。旁观瞄准修前被WorldTargeting.valid整体拒绝，保留world-special-reference-before.json；移除重复的旁观模式禁令后由原版pick/输入/服务器决定行为，不增权限或直接设置相机。五端编译后的Native边界检查通过。

WorldClientSnapshot读取自身getVehicle及其getControllingPassenger，USER投影/模型保留vehicleType/vehicleControllerIsPlayer，离开载具不伪造默认值，不发布UUID。参考端Native、Frame和真实Companion消费者一致，再移植四端；world-vehicle-feedback-five.log为其相关编译/测试，不冒充四端Live。

骆驼测试的NPC预占不等同第二真人：原版Entity.addPassenger将首位Player放到NPC前，实际测试据此改为首真人优先与满座拒绝；修正前world-camel-fixture-reference-before.json保留失败。modern-mounts参考端另完成快乐恶魂原生升降/前进/四座拒绝和鹦鹉螺水下操控及真实绘制的冲刺冷却。鹦鹉螺初次登乘语义瞄准已朝下，固定小量抬头仍未转为朝上；该场景前提修正而非产品改值，world-nautilus-fixture-reference-before.json保留失败。

上述参考端运行sourceChangedDuringRun=false、正常退出、held=0、端口释放及Persistent Write=0；Native准备命令/安全效果与USER Act分列，不以派发成功代替效果。原版拒绝、自然惯性及视角变化保持。05b未关闭：其余四端、双玩家座位和最终冻结仍待，不刷新Alpha或宣称M-05闭合。

### 2026-10-21 E世界05b五端特殊移动与真实双玩家座位闭合

special-movement五端均通过：船桨、矿车实际微小前进、马跳跃、骆驼冲刺/原生玩家优先与满座拒绝、羊驼不可驾驶、猪/炽足兽持道具控制及真实HUD耐久消耗，26.x快乐恶魂与鹦鹉螺按适用性实测。鞘翅取消只停止输入、不直接终止自然滑翔，正常下降与落地独立回读；创造/旁观仍经原版输入。Snapshot的自身载具字段在五端实际Native、Frame与Companion保留，不公开关系UUID。

world-seats改用两个真实客户端，不创建Fake Player或以NPC冒充第二Player。显式probe仅在自有固定副本使用原生IntegratedServer诊断准备，监听强制127.0.0.1、临时关闭Minecraft网络认证供两个开发玩家加入；不是Runtime认证或正式三部署能力。新旧版本分别委托MultiplayerScope.LAN与GameType原生发布签名，Runtime模式/scope/Lease不变。角色各4GiB、1920×1080静音、独立发现与Lease，第二端只取必要USER输入和诊断scope，无DEBUG授权。

五端分别用真实Direct Connection GUI连接，船和骆驼、26.x快乐恶魂的第二Player确认不是原生驾驶者；其转头/W输入不移动载具，第一驾驶者的输入带动双方，随后分别正常下车、第二端断开退出、主端保存退出。原版登陆出生点可能在y=-60而测试平台为y=80，先诊断准备到共同区域并等待有界客户端同步，不能用一个客户端缓存当服务器玩家名册。首次错误计数与缺少diagnostics导致的清理失败保留world-seats-reference-arrange-before.json；该失败的第二端随后通过原生返回列表/标题/退出正常关闭，未改写失败为通过。探针签名首次启动失败保留world-seats-reference-startup-before.txt；精确重载签名后修正。

十份<target>-special-movement.json与<target>-world-seats.json均PASS、sourceChangedDuringRun=false、cleanShutdown=true、Persistent Write=0；双方held清理、端口/endpoint释放，Actor读到的是CLIENT_KNOWN而非服务器确认。后四端world-seats的seatPartner记录额外的实际堆上限与退出检查；参考端相同检查在外层Runner实际执行，未为了加记录字段重跑已通过分支。world-vehicle-feedback-five.log、world-special-five-offline-contract.log与world-seats-five-probe.log记录相应生产适配/原生边界/探针编译通过。新场景与角色均仅属于验证，没有升级依赖、改变产品权限或新增部署承诺。

05b专项闭合，M-05剩05c～g五组、全局30组。最终冻结候选、受影响回归、五端单JAR入世界和试用发布仍待；未做性能或人工验收，未额外提交推送。

### 2026-10-21 E世界05c物品工具与非完整轮廓

item-utility五端专项通过：原生数字/滚轮热栏、双手交换、单件与整堆丢弃、普通Pick Block与创造热栏，剪切、点火/熄灭、去蜡/刮铜、骨粉及瓶桶流体处理。刷扫由语义aim接普通持续右键，实际dusted逐步变化并自然转成沙；这是非原子字面输入，不伪称持续targetRef守卫。原版保存热栏是游戏输出，不是Runtime Persistent Write。

Fabric首次被pipeline原有限流拒绝，保留item-utility-fabric-rate-before.json；测试发送间隔调整为遵循每秒2项预算后通过，未改生产限流、权限或重放失败输入。五份结果均sourceChangedDuringRun=false、cleanShutdown=true、Persistent Write=0；不是最终冻结制品证明。

placement-branches参考端修前在下半砖顶面放置报WORLD_TARGET_NOT_HIT，原始失败保留world-partial-face-reference-before.json。显式面坐标按单位立方体计算是根因；现沿请求法线与实际原生VoxelShape求交，u/v仍为局部轴投影，最终原生pick与上下文保护不变，空洞和空形状拒绝。参考端实际半砖、箱子潜行与原版碰撞/支撑拒绝通过后移植四端；world-partial-face-five-tests.log记录五端生产几何/Native边界测试通过。

首个流体测试因水流推动玩家而被WORLD_ACTION_CONTEXT_CHANGED正确中止，失败保留world-fluid-fixture-reference-before.json；仅测试水槽与站位改为稳定前提，未放宽位移保护。

五份<target>-placement-branches.json均PASS、sourceChangedDuringRun=false、cleanShutdown=true、Persistent Write=0，分别实测实际轮廓、潜行、碰撞/无支撑、流体/方向以及Adventure/不可破坏超时和Creative破坏。既有held含水/连锁/六面与placement-receipts多格受阻/同ID预测保留，冻结时按轮廓影响复验，不将增量写为最终制品证明。05c专项闭合，M-05剩05d～g四组、全局29组，未做性能或人工验收、未刷新Alpha或额外提交。

### 2026-10-21 E世界05d参考端桥接与语义表面

参考端block-bridge实际从世界打开22类原生存储/工作站，继而完成门/活板门/栅栏门、日照反转、唱片存取、堆肥、书台取书、床睡眠/起身及背包/聊天/暂停桥接。world-block-bridge-reference-before-surfaces.json保存该增量；未直接创建Screen或调用菜单业务，未据静音日志宣称音频验收。

指定雕纹书架小格的修前请求报INVALID_WORLD_ACTION，保留world-surface-use-reference-before.json。原实现只允许place携带面坐标，无法区分use的小格；现为普通方块use增加可选face/hit，沿真实轮廓求交和原版pick，不接受实体面或放置目的格条件，不增加权限、Lease或DEBUG回退。world-surface-use-reference-contract.log记录共享Parser、参考端Native、生成模型和Companion测试通过；实际参考端确认后再移植其余四端，相关测试记录world-surface-use-five-tests.log。

surface-use参考端实测六书位逐一存取、真实MCP发现/操作与worldContract声明、复用显式已有Lease且不自动acquire；READ/OPERATE仍拒绝，实体面请求明确失败。26.x置物架三个位置实际物品存取及单架供电交换原生热栏6～8、再次交换恢复均通过。原生类核对1.20.1/1.21.1没有ShelfBlock，26.1.2有；不把缺少原生内容写成已实测。所有运行源未变化、正常退出、held=0、端口释放、Persistent Write=0。

spotlessApply/lintMarkdown/verifyCompliance --strict在05c及桥接工具首版通过，80篇文档无问题、2353文件严格合规零失败零警告；此结果早于表面use扩展，后续须复验。05d尚未关闭：完整五端桥接、重生锚及其他明确剩余分支仍待，不发布快照或宣称全里程碑通过。

### 2026-10-21 E世界五端桥接与表面输入一致

五份<target>-block-bridge.json均PASS、sourceChangedDuringRun=false、cleanShutdown=true、Persistent Write=0。各端实际从世界进入适用容器/工作站，完成开关、唱片/堆肥/书台、睡眠与玩家菜单桥接，以及六书位存取、MCP和模式/目标拒绝；26.x另完成三格置物与供电热栏交换。Native Scope、Lease、Arm与证据未调整；实体不能携带方块面，READ/OPERATE无输入副作用，MCP使用显式现有Lease且不自动申请。

该组不替代重生锚与其他资源/战斗/生命周期分支，05d～g仍为待闭合。接续station-use与combat-use按最小参考端核验，不为相同输入反复运行已通过的桥接组；最终冻结仍按实际影响复验。

### 2026-10-21 E世界交互站与移动攻击

station-use参考端实际完成开关驱动红石灯、熔岩/细雪炼药锅、瓶与水药水转移、蛋糕进食、蜡烛蛋糕点燃/上表面熄灭，以及原生600tick营火烹饪与真实掉落。初始Hunger夹具未先耗尽饱和度，随后普通难度短效果仍不足；失败保留world-station-hunger-fixture-before.json及world-station-normal-hunger-before.json。工具改为明确自有副本难度、观察食物实际降低并等待残余饥饿稳定，不直接修改食物或烹饪进度。

combat-use参考端修前，跨请求持有冲刺/前进后语义攻击被WORLD_ACTION_CONTEXT_CHANGED拒绝，原始结果world-moving-attack-reference-before.json。固定初始位置被当作身份是根因；WorldTargeting现在保持世界/玩家/相机/目标身份、物品与视口保护，每次从当前眼位计算并用原生pick确认，不因正常移动拒绝。没有自动移动、直接转向或权限变化。参考端真实移动攻击、原生剑冷却/受伤免疫、弩与三叉戟、蛋/雪球、药水/奶、号角/望远镜通过，随后input-context确认活动/排队取消、同Lease维度变化和实际控制WebSocket断开清理；再移植其余四端。

world-moving-attack-reference-tests.log与world-moving-attack-five-tests.log记录五端生产路径与原生边界测试通过。当前其余四端station-use、combat-use及input-context矩阵进行中；不能据参考端或编译宣称五端Live。内部伤害断言明确SERVER_AUTHORITATIVE DEBUG，USER Act仍为真实输入；没有测量新的攻击性能，Persistent Write=0。

### 2026-10-21 E世界交互站与战斗增量五端复验

五端station-use、combat-use及input-context均PASS、sourceChangedDuringRun=false、cleanShutdown=true、Persistent Write=0；自有客户端正常退出、端口/endpoint释放，held输入清理。原生营火自然完成，剑伤害与免疫、跨请求冲刺中的语义攻击、蓄力/投掷/饮用效果分别独立观察。维度变化取消活动和排队输入并保留同Lease，真实Lease绑定WebSocket正常关闭退READ；没有用普通HTTP连接结束冒充控制断线。

这些具名增量不代表05d/e整组关闭：重生锚、特殊武器/其余物品、实体及生命周期仍待。后续新增entity-branches路由只用于实体分支，不改写本次已通过的原始结果，最终冻结门禁另计。

### 2026-10-21 E世界实体部位缺口与显示反馈

entity-branches首次头盔取回通过，是因为前次操作的视线仍击中头部，不能证明能选择部位。增加真实语义转向地面再取回后，原版操作完成但头盔未取回；world-entity-body-point-before.json保存失败。对照原生ArmorStand.interactAt/getClickedSlot：头部、胸部、腿脚按实际命中高度选槽；WorldTargeting默认仅瞄准实体中心且只比较实体身份，无法表达该意图。

参考端增加可选entityHit归一化原生pick box点，选定点投射到原生射线入口，最终EntityHitResult位置也须一致；不接受方块、face/hit或place/mine混用，不是屏幕坐标或直接换装备。Scope、Lease、来源及新鲜原生命中不变。共享Parser、参考端原生几何和边界测试通过；Native/OpenAPI与Companion载荷/说明同步，真实六部位取放仍待本次运行结果，其余四端尚未移植。

展示框读取新增客户端同步的displayedItemId/displayedItemCount/displayedRotation，以原生getItem/getRotation为来源；不复用掉落物itemId字段混淆实体类型，不暴露NBT或隐藏库存。羊染色/剪毛实测原生消耗和红羊毛掉落；桶捕捉首次成功、放回时水流/站位导致原生pick miss，失败保留world-entity-bucket-fixture-before.json。测试改为有围墙水槽及干燥墙顶站位，不为产品加fluid ID执行分支或放宽reach。

### 2026-10-21 E世界实体部位参考端实测与四端移植

参考端entity-branches实际完成六部位装备取放、羊染色/剪毛、鱼和美西螈桶捕捉/释放、铁傀儡修复、牛奶与蘑菇煲、蘑菇牛转化与雪傀儡剪切、犰狳刷取、展示框插入/旋转/取下及破坏。显示物与旋转由独立客户端同步观察，修复伤害用显式DEBUG断言，不混入USER生命字段。没有直接调用生物交互或装备业务方法。

展示框首个夹具在原生命令仍未同步到客户端时读取了空气引用，world-entity-frame-arrange-before.json保留失败；改为等待实际石头与targetRef。拾取首轮固定短步后库存计数0，失败保留world-entity-pickup-fixture-before.json；原记录不足以精确追溯当时位置/拾取延迟，不能据此判产品缺陷。改为等待四十个实际客户端tick让物品自由落地，并独立确认HUD取回、经验增长与库存内容后通过，不改拾取延迟或强制拾取。

entity-tail仅隔离复验拾取/真实MCP，不重复此前通过的分支；随后参考端完整entity-branches含自然剑损坏通过。MCP实际发现实体并携带entityHit，使用显式已有Lease，取回头盔后Lease未变，没有自动acquire。参考端结果源未变、正常退出、held=0、端口释放与Persistent Write=0。

参考端确认后移植其余四端Native几何/显示读取及原生几何测试；world-entity-point-five-tests.log记录四端编译/原生边界通过，参考端相应结果在world-entity-point-frame-tests.log。world-entity-point-contract.log记录Schema生成和Companion通过。当前四端完整Live矩阵进行中，05f未关闭；未刷新Alpha或额外提交推送。

world-entity-point-format-strict.log为本增量格式、80篇Markdown及严格合规通过，2374文件零失败零警告；该结果早于后续接口说明和本段追加，交付前仍须复核。

### 2026-10-21 E世界实体部位五端与补充种属分支

entity-branches五份结果均PASS、sourceChangedDuringRun=false、cleanShutdown=true、held清理、端口释放与Persistent Write=0。各端实际覆盖装备部位、桶/奶/煲、原生特殊剪切、展示框同步旋转/取物、掉落/经验拾取、自然剑损坏，以及真实MCP明确复用Lease。1.20.1没有犰狳，该分支仅在其余四端运行；不把Native缺类写为通过。

后续entity-social参考端已确认猫/鹦鹉按物种食物和原生RNG驯服。狼坐姿首次因NoAI夹具而不运行原生Sit Goal，world-entity-sitting-fixture-before.json保留失败；改为保留AI、原生movement_speed设0，仅限制夹具移动。坐姿随后通过，喂食断言因假定消耗一件而失败，world-entity-healing-fixture-before.json保留原始结果。

对照本批实际Loader原生字节码：1.20.1/1.21.1的Wolf.mobInteract一次消耗；26.1.2/26.3的TamableAnimal.feed调用两次usePlayerItem。参考端实际两件耗尽与该原生路径一致，不据此认定Runtime重复输入，也不修改游戏行为来消除差异。测试按这些已核对的原生分支比较，生命为独立DEBUG断言；种属/坐姿/喂食补充及其余战斗/生命周期仍待完成。

### 2026-10-21 E世界原生门户与死亡输入缺陷

参考端lifecycle修前真实点火进入原生门户已通过，但环境岩浆死亡后旧S仍held=1，管线尾部还未取消；world-death-held-input-before.json保存原始输入、死亡页与旧epoch。InputContextEpoch只跟踪world/player/connection对象，死亡时这些对象未变是根因。

共享epoch现在额外跟踪原生玩家存活边界，五端调用均传当前LocalPlayer.isAlive；不按Screen类白名单判断。死亡/恢复更换输入代次并沿既有路径取消活动/排队和清理按住，不使死亡GUI不可用，不释放Lease、改模式或设置人工撤销；服务器LIVE/存档生命周期不由该输入边界改变。共享/Core新增同对象死亡/复活及死亡GUI可继续输入、Lease/模式不变测试；world-death-context-reference-tests.log通过。

参考端修后lifecycle完成真实点火/行走入门、锚充能与设定重生、持有S进入岩浆自然死亡、活动/排队均取消、held=0/无尾部派发、同Lease语义重生与锚充能消耗。Lease从实际control/status取证，不用mode中不存在的字段互相比对。重生请求实际REQUEST_CANCELLED且retrySafe=false，world-respawn-context-exit-before.json保留初次响应；工具不重发，独立核验新epoch、存活状态、维度与锚后确认效果。

补充工具错误已按正式合同更正：Operation的executing可包含等待输入队列，排队通过无派发与待终态证明，不虚构queued枚举；USER玩家用deadOrDying/health而非未声明alive字段。参考端最终lifecycle.json为PASS、源未变、正常退出、端口释放及Persistent Write=0。其余四端运行及快捷键/命令冻结回归仍待，05d/g不提前关闭。

world-death-five-contract-strict.log记录五端Native几何/读取边界、共享epoch及Core代次/状态流测试、格式/文档和严格合规通过；此为构建及契约证明，不冒充四端死亡实测。参考端正在补range-branches，盾牌夹具先举盾再生成真实攻击者，避免准备阶段已受伤；原失败world-shield-arrange-before.json保留，不改伤害或屏蔽原版攻击。

### 2026-10-21 E世界远距瞄准与真实玩家对照

参考端range-branches首先在9格珍珠目标处复现WORLD_TARGET_NOT_HIT，world-ranged-aim-before.json保留原生4.5格MISS。WorldTargeting将纯aim纳入既有32格原生客户端射线；attack/use/place/mine仍用近身native pick。五端代码同步，Native测试核对实际分支只含aim/use_hold；参考端实测远距aim成功、同目标use仍拒绝且珍珠未消耗，随后真实投掷产生位置与生命变化。

参考端另外完成盾防御与解除后受伤、抛投药水/经验瓶、钓鱼收放、地面烟花、重锤落击。长矛初次刚换装备后刺击无伤害，world-spear-feedback-before.json保留。对照Player.cannotAttackWithItem及MINIMUM_ATTACK_CHARGE，等待原生tick使装备蓄力就绪后，range-tail实际刺击及持续使用通过；不降低原版冷却或由Runtime强制造伤。

world-seats增加两个真实客户端的友伤开关和盾/斧对照。首次普通剑伤害的正常击退使后续盾测试目标超距，world-duel-knockback-fixture-before.json保留；独立场景重新准备站位后，参考端友伤拒绝/放行、前盾挡剑与冲刺斧禁用盾均通过，同时载具真实驾驶/乘客比较及双客户端正常退出通过。没有Fake Player或强制关系/伤害Act。

剩余utility补充激流、磁石指南针及特殊消耗品。激流实际位移后切下一夹具清空水体导致原版摔死，world-riptide-fall-fixture-before.json保留；改为下一Arrange先安全创造过渡、落地后恢复生存，Act仍按原版生存输入。该次结果不计通过，正按新前置复验；尚未以此关闭05e。E世界专用发布profile已接入原发布器，拒绝失败/清理不完整及尾部场景代替整组；未执行发布。

### 2026-10-21 E世界参考分支收口与冻结门禁

参考端range-tail修后通过实际长矛、激流、磁石指南针、蜂蜜移除毒效果与紫颂果自然传送，正常保存退出、源未变与端口清理均通过。蜂蜜前置改为原生命令显示毒效果图标，避免隐藏粒子参数同时隐藏HUD图标，不扩大USER读取范围。lifecycle补充原生F3+B/G/H的实际HUD反馈和二次切换恢复；与门户、锚、自然死亡重生同组通过。

正式全仓门禁首次暴露过时检查：NativeInputContextBoundaryTest仍要求三参数epoch，NativePlayerAttributeInspectionTest仍在player内只找MAX_HEALTH默认读；测试分别改为四参数存活边界及实际nativeAttributes完整原生目录读取。保留默认读取不创建实例、不写属性的原版对照，并新增实际isAlive调用断言。world静态检查的骑乘反馈从isPassenger改为核对实际getVehicle及riding字段；不修改生产行为迎合测试。

E UI历史计数单元夹具固定自己的历史计数，不再把当前新增相机Hook计数当作该历史字节夹具；不据此接受任何旧Live结果。world-alpha-publication-tests.log的66项通过，world-tool-tests.log、76份实际mjs语法检查通过；world-final-five-java-tests.log记录五端全量Java测试通过。

world-candidate-build-check-release-strict.log最终spotlessApply、build、check、verifyRelease与verifyCompliance --strict全部通过，五端发行内容/元数据与probe排除通过，严格合规0失败0警告。此后冻结产品及执行工具；仅参考端lifecycle/world-seats因与当前生产和实际工具逐项一致可复用，其余相关组按冻结候选运行，不重跑65旧逻辑场景。冻结矩阵与本Goal固定快照尚未完成，不将构建门禁写为M-05闭合或领域READY。

### 2026-10-21 E世界多部件目标目录缺口

冻结参考端full、range-branches及entity-social均通过；随后目录核验发现原生ClientLevel的主体遍历不包含独立part列表，EnderDragon主体不可pick，而真正可点击的八个部位未进入USER语义发现。Native字节码确认各端原生部位列表及父子生命周期，非按实体ID新增业务白名单。

初始entity-parts用NoAI夹具得到0/8；修正发现后仍0，因为原版NoAI会跳过部位位置更新，初始化部位停留原点并被合法半径过滤。因此该初始0不能单独作为发现缺陷的唯一因果证据，world-multipart-discovery-before.json保留原始上下文。改为原生Hover阶段先初始化，参考端已真实发现8个独立部位与引用；该次动态头部追踪实际WORLD_AIM_TIMEOUT，world-multipart-moving-aim-before.json保存，未计攻击成功。

当前在原生初始化后静止的对照中，核验真实部位pick/攻击及父死亡失效；不放宽近身距离，不以父实体替换命中部位或调用伤害业务接口。新增partName仅来自客户端原生部位名，不含父实体内部数据；共享投影及参考端Native/schema测试通过。此实际缺口使候选重新打开，四端移植、最终构建及矩阵仍待，不沿用上一候选构建或安装证明新代码。

参考端静止对照先请求head实际得到不同原生部位遮挡，world-multipart-native-occlusion-before.json保留坐标和实际射线，未将其放行或替换为父实体。选择该姿态实际暴露的neck后，真实鼠标攻击使父生命下降，kill Arrange后旧引用WORLD_TARGET_STALE；安全清理使dispatch序号增加1，不是再次攻击。断言改为原生攻击计数不变、held=0及清理后300ms无迟到，参考端最终entity-parts通过。

四端按其原生getPartEntities或dragonParts接口移植，Fabric使用原版父引用，Forge/NeoForge使用原生PartEntity；world-multipart-four-ports-tests.log记录编译与实际Native入口检查通过。Forge 1.20.1随后实际部位、伤害、失效、正常退出通过，其余端按顺序运行；尚不宣称五端多部件Live全部通过。

后续NeoForge 1.21.1、26.1.2及Fabric 26.3依次完成同一entity-parts场景。五端当前结果均PASS、源未变、正常退出与端口释放、Persistent Write=0；这是已初始化静止部位的Native输入对照，不外推动态Boss追踪或人工游玩。代码与接口覆盖五端，候选重新进入构建及最终相关矩阵；没有发布新快照。

多部件修复纳入后，world-candidate-build-check-release-strict.log再次完成spotlessApply/build/check/verifyRelease/严格合规，0失败0警告、五端发行检查通过。既有五份entity-parts与当前实际源码/工具全部一致，不为重复确认重跑。其余既定相关组开始冻结矩阵，顺序先lifecycle、entity-social、range-branches及world-seats；仅world-seats使用两个真实自有客户端，其余单实例。当前参考端lifecycle完成，其他组仍在执行；没有发布或将领域READY。

冻结lifecycle随后五端全部通过：真实门户进入、锚充能/重生、自然死亡时取消活动/排队、held=0及无迟到、旧引用失效、同Lease新代次GUI，以及原生F3状态恢复；源未变、正常退出与端口释放。请求未确认时保留原回执、独立观察、不重放；没有物理Esc或新性能测量。05g仍需命令相关冻结组，05d还需桥接组，不由这五份结果替代。

### 2026-10-21 E世界原生喂食差异

冻结entity-social先在四个Forge/NeoForge端通过，Fabric断言失败，原始结果保留在world-fabric-native-feeding-difference-before.json。对照实际Fabric 26.3 TamableAnimal.feed字节码只有一次usePlayerItem，NeoForge 26.x原生实现有两次；不是产品重复派发。测试按真实Loader实现区分消耗量，不修改产品输入或喂食业务。Fabric修正预期后猫/鹦鹉原生随机驯服、狼坐/站/治疗与物品消耗通过，正常退出、端口释放及Persistent Write=0。

entity-social最终五端均通过；range-branches也完成五端适用武器、盾牌挡伤、药水/经验、珍珠、钓鱼、烟花、激流/磁石/消耗品及清理。原版不存在的武器按注册目录排除，不以新版本反推旧端。

world-seats参考端通过友伤、盾/冲刺斧与驾驶/乘客，Forge随后在第二客户端即时取载具语义目标处失败，world-seat-forge-mount-before.json保留。主客户端看到原生spawn不等于另一客户端已同步；改为等待各自客户端目标可用后交互。Forge修正后完整战斗/船/骆驼、双方输入清理与正常退出通过，生产代码未改；其余端继续按当前脚本运行。

### 2026-10-22 E世界冻结矩阵恢复

系统重启后核对固定结果、实际生产与执行工具绑定：world-seats的Fabric结果已在上次最后一条汇报后正常完成，五端真实友伤/盾斧与驾驶/乘客均通过、正常关闭且源未变，不重跑。entity-parts、lifecycle、entity-social、range-branches的五端也仍对应当前候选；旧快照latest不变，新快照尚未发布。

恢复后从其余单客户端组继续。movement本身完整调用worldMovementBranches，发布组取消重复要求单独movement-branches，不删实际分支或用尾部用例替代整组。发布器相关66项单元检查通过；不因这一未被Runtime消费的发布目录调整使已通过的游戏结果失效。

block-bridge随后五端通过。station-use参考端、Forge及NeoForge 1.21.1通过；26.1.2首次在灯熄灭轮询失败，world-station-26.1-lamp-before.json保留，正常退出不等于用例通过。对照实际RedstoneLampBlock的原生邻居信号、四tick调度和关断逻辑，只做一次原样复测；外置只读观察同时记录客户端与服务器方块、调度数及冻结状态。复测完整通过，world-station-26.1-native-observation.jsonl显示原生开关关断、调度自然排空、客户端与服务端最终一致，未修改生产或测试代码。首次原因未定位，不宣称修复；保留开放项并继续剩余端，不再重复碰运气。

E世界发布仍引用的历史验收基线通过现有Pin入口登记为mine_craft_protocol_trial_baseline，避免发布保留最近三份时回收实际依赖；未改任何已发布七文件或其他使用方登记。

恢复后的block-bridge、station-use与combat-use均完成五端当前候选回归、正常退出及端口清理。05d桥接/原生交互站和05e新增战斗/蓄力/消耗专项收口；既有full持续流程仍需冻结回归。26.1.2灯熄灭首次失败仍为未复现开放项，复试不是修复声明。USER Act均为真实语义/原生输入，伤害内部读回明确为SERVER_AUTHORITATIVE DEBUG，不混入USER数据；不测性能，不外推动态Boss或真人验收。其余冻结组与独立安装、发布尚待。

entity-branches随后也完成五端冻结回归。player-command参考端全部功能检查通过后，保存退出因当前world epoch撤销旧请求而返回REQUEST_CANCELLED，旧独立命令Runner未处理这一既有合同，world-player-command-context-exit-before.json保留失败。仅修改player-command/command-configuration两个Runner：保留真实HTTP错误体，不重放退出，复用contextExitEvidence严格校验409、未确认/不可重试、新epoch、标题页及held清零/清理完成。主世界Runner和生产代码不变；已通过世界组没有消费这两个脚本，不重跑。world工具测试及66项发布器测试通过，两命令组按当前工具重新运行。

### 2026-10-22 v3第一检查点与05g收口

按76号恢复入口改用共同规则v3、预先授权v3及Minecraft/Web附则。用户撤回全部子代理批准，主线程核查接手已有文件；不操作任何Goal或线程控制接口，只完成05g及受影响冻结回归后停下。恢复时本仓库无残留游戏进程；原Fabric玩家命令日志仅到中途，旧结果不匹配当前候选，因此不计通过。

独立player-command Runner另有两项真实失败：标题页退出返回RUNTIME_SHUTTING_DOWN，异步世界列表尚空时就检查唯一世界。分别保留world-player-command-runtime-close-before.json和world-player-command-list-loading-before.json。前者只接收实际503未确认/不可重试响应并要求外层独立进程退出证明；后者等待真实目标列表项，仍要求唯一固定世界，不使用内部world-open或坐标。生产代码不变，不重放退出或吞掉其他错误。

五端player-command、command-configuration、command-collaboration及input-context随后均在当前源码/实际工具上通过，源未变、固定副本恢复、4GiB、正常退出与端口/endpoint释放、Persistent Write=0。结合当前五端lifecycle，05g自动专项收口；原生配置拒绝保留零服务器副作用及部分/未确认回执，实际服务器权限变化不替代普通服务器或真人协作验收。world工具测试及66项发布器测试再次通过。其余冻结组仍在执行；单JAR安装、最终发行检查和七文件发布均留后续检查点，本记录不证明当前制品独立可安装或领域READY。

后续Forge关系组在进入世界前失败，world-relations-forge-entity-before.json保留失败上下文：SelectWorldScreen已出现，但其异步世界列表尚未含固定世界，唯一世界断言得到0而非1；并非实体关系读取失败。只将world-debug-relations-actions.mjs的等待条件改为页面及真实World-Read-Alpha条目同时就绪，仍断言唯一世界，不增加固定延迟、自动重试、内部world-open或坐标绕过。相关单元/工具检查通过，参考端修后整组通过；五端关系组按新工具核验。其余已通过场景没有记录或消费该函数，不重跑；生产代码未改。

Forge传播边界Runner随后也在异步世界列表处得到0，world-boundary-forge-list-before.json保留失败；不是加载防护断言失败。边界及尚未运行的转移、Provider、连续输入采用同一真实条目就绪条件。关系与边界均完成修后五端，世界目录及连续输入最终五端冻结通过，生产代码未改。

记录器对独立命令收集整个世界工具目录，并在共享场景中记录未调用的continuous-input模块；直接按全部字节差异作废会反复重跑无关组。E世界发布验证只对本次五个具名就绪改动做精确全文件反向哈希复核，且只允许实际未执行的场景函数；执行过的分支、任何额外变动、错误哈希仍拒绝。旧结果及其绑定字节不改，复用条目单列recordedUnexecutedReadinessUpdates，不声称原始工具字节全部匹配。真实工具文件正负例在67项发布测试中通过，已完成世界组无未解释绑定差异。

UI参考端首次all执行到死亡准备时，kill命令触发原生死亡和输入代次失效，旧请求返回REQUEST_CANCELLED，Runner在测试重生按钮之前失败；ui-reference-respawn-context-before.json保留原始失败。只改验证准备路径：一次提交、不重放，保留409未确认/不可重试错误，以DeathScreen、USER生命0、新代次、同Lease/模式和输入清零独立核对死亡，再执行原有真实重生按钮检查及500ms无迟到观察。死亡准备不作为gameplay验收，也不改命令类别或运行时合同。20项直接提取Runner函数的单元正负例通过；新增respawn定向结果与all分离，参考端实际死亡、重生、保存退出通过。当前完整UI矩阵仍待收口，不将定向结果代替整组。

参考端与Forge随后完整UI all通过。1.21.1在优化器取消处失败，ui-1.21-world-recovery-before.json保留：原Runner以断言覆盖原始异常，只能确认返回自有Fixture页、资源关闭且未获得Cancel覆盖，无法确定最初错误代码。唯一ui-world-pages定向复测通过，不宣称修复或用它替代all。错误路径现先记录原始错误、HTTP状态和Runtime回执；仍只接受原有UI_TARGET_INVALIDATED/UI_NODE_NOT_FOUND早结束，其他错误原样抛出，未放宽白名单。13项世界恢复单元通过；完整1.21.1 UI按当前工具核验。

参考端/Forge实际早结束为上述两个已允许错误，native worker自然完成，未计Cancel覆盖；只增加错误诊断字段和保留原始失败不会改变这些成功路径的请求或准入。发布验证通过精确全文件反向哈希和原始样本核对复用，单列recordedToolUpdates、newDiagnosticsCovered=false，不把旧回执说成包含新诊断。68项发布验证正负例通过；其余更改仍拒绝，旧结果不手改。

最终收集器复核发现五端command-configuration虽然功能与外层退出日志通过，独立Runner漏写runtimeConnection，外层Test-WorldReceiptOwner因此拒绝附入cleanShutdown和实际4GiB证明。严格门禁正确拒绝这五份缺失记录，不以日志补写JSON。仅在该Runner结果中记录实际已连接的实例ID/PID/端口，沿用外层身份一致检查、进程退出/端口/endpoint核验；其他结果不放宽。五端仅该组按修后工具重跑。player-command未导入此文件，其记录目录中的唯一元数据变动以精确全文件反向哈希复用；实际执行的配置组不得复用。69项发布验证正负例通过，生产代码未改。

### 2026-10-22第一检查点结果

第一检查点PASS，限59号批准的M-05/M-06及受影响开发Runtime回归，不代表两个完整里程碑READY。以下每格均有实际游戏结果；同一场景覆盖多组时只运行/接收一份，不按表格重复执行。具体场景映射由[发布目录](../../components/validation_tools/regression-v1_release/task/e-world-proof.mjs)中的E_WORLD_GROUPS定义。

| 组 | Forge 1.20.1 | NeoForge 1.21.1 | NeoForge 26.1.2 | NeoForge 26.3 | Fabric 26.3 |
| --- | --- | --- | --- | --- | --- |
| 05a 移动/上下文 | PASS | PASS | PASS | PASS | PASS |
| 05b 特殊移动/双玩家载具 | PASS | PASS | PASS | PASS | PASS |
| 05c 物品/破坏/放置/工具 | PASS | PASS | PASS | PASS | PASS |
| 05d 方块交互/UI桥接/生命周期 | PASS | PASS | PASS | PASS | PASS |
| 05e 战斗/蓄力/消耗 | PASS | PASS | PASS | PASS | PASS |
| 05f 实体/自然反馈 | PASS | PASS | PASS | PASS | PASS |
| 05g 生命周期/命令/配置/协作 | PASS | PASS | PASS | PASS | PASS |
| 05h 有界区域读取 | PASS | PASS | PASS | PASS | PASS |
| 06a 玩家/实体状态 | PASS | PASS | PASS | PASS | PASS |
| 06b 库存/方块实体分页 | PASS | PASS | PASS | PASS | PASS |
| 06c 实体生命周期 | PASS | PASS | PASS | PASS | PASS |
| 06d 环境 | PASS | PASS | PASS | PASS | PASS |
| 06e 关系 | PASS | PASS | PASS | PASS | PASS |
| 06f 加载/传播边界 | PASS | PASS | PASS | PASS | PASS |
| 06g LIVE转移 | PASS | PASS | PASS | PASS | PASS |
| 06h Provider/Fixture | PASS | PASS | PASS | PASS | PASS |
| 受影响连续输入 | PASS | PASS | PASS | PASS | PASS |
| 受影响UI all有限目录 | PASS | PASS | PASS | PASS | PASS |

正式collectEWorldProof接收32场景×5端的160份当前候选记录：全部正常退出、执行期间源未变、Persistent Write=0；记录来自world_read及vanilla_ui的固定result路径。这是冻结目录覆盖，不以测试数量宣称所有原版或第三方行为已验收。

复用以实际生产源码、Target事实和执行工具为依据，不重跑已通过且未受影响的组。132份记录涉及精确工具差异复核：未执行世界列表就绪改动150处、未执行配置记录字段5处、成功路径不变的优化器错误诊断2处；这些是记录引用次数，不是新增测试。反向全文件哈希、场景准入及原始样本严格核对，任何额外改动仍拒绝；旧JSON不改。

player-command记录器只哈希Java/JSON/TypeScript，未记录的9个Loader资源绑定由同端本来就必需且通过的full世界记录提供，收集结果明确resourceBindingsFrom，不假称玩家命令记录含这些字段。只允许具名元数据/访问配置；遗漏代码、已记录但改变的哈希、其他Target、失败或未正常关闭的full均拒绝。70项发布器单元正负例通过。五端配置命令按补齐身份的Runner重新通过，关闭/堆上限证明由外层真实核验产生。

本次spotlessApply/check/verifyCompliance严格模式通过，合规0失败0警告；检查日志为外置Investigations/20261021-e-world/checkpoint1-check-strict.log，验证工具最终复核另记checkpoint1-final-tool-check.log。此前当前生产候选完整build/check/verifyRelease/严格合规仍见world-candidate-build-check-release-strict.log，本次不把它写成新安装证据。生产Java、协议与权限合同没有在本检查点改动。

未复现开放项仍保留：26.1.2交互站灯熄灭一次失败，1.21.1优化器取消一次原始错误丢失。后续通过不等于已修复；原失败及必要读回保留于外置Investigations。历史三攻击性能目标NOT_MET，本批不测性能。物理Esc、真人协作与普通服务器权限三项均未执行。

本次停止于第一检查点。第二检查点待安排：五端全新独立游戏目录，仅对应Loader及一份最终产品JAR，实际进入世界、握手/读取、保存退出/关闭，再做最终发行检查；不含开发classpath、额外模组或手工依赖。第三检查点待安排：同批清单/手册、不可变七文件快照、latest及集中人工清单。当前latest仍为20261021-0006-90123ba7，本次没有刷新清单、手册或发布快照，不操作任何Goal、子代理或Git写操作。

### 2026-10-22 E世界单JAR与Alpha交付

按106号合并原安装/发布检查点：同候选五端全新单JAR进入世界通过后直接交付Alpha并停止。候选完整身份、五个构件SHA、开发/安装原始结果引用集中在D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/20261022-1722-0ea052cb/alpha-manifest.json；同目录AGENT_GUIDE.md为使用入口，latest已指向该不可变七文件目录。profile=e_world_trial_v1，不代表完整领域、性能或真人验收通过。

本次先复核当前生产源码与第一检查点冻结目录仍一致，不重跑世界/UI矩阵；spotlessApply/build/check/verifyRelease/严格合规通过，五端各261个必需发行条目核验通过，合规0失败0警告。包装静态门禁另通过。随后参考端先证明入口有效，再串行其余四端；总计五次客户端启动，均1920×1080静音、最大堆4GiB、低优先级/4逻辑核，不向宿主发输入、不激活窗口，不改系统或用户实例。

| Target | 单JAR标题/握手 | 进入世界/USER读取 | 保存退出/关闭 | 实际HTTP来源 |
| --- | --- | --- | --- | --- |
| 1.20.1-forge | PASS | PASS | PASS | 产品Jar-in-Jar，4.1.82.Final |
| 1.21.1-neoforge | PASS | PASS | PASS | 产品Jar-in-Jar，4.1.97.Final |
| 26.1.2-neoforge | PASS | PASS | PASS | 原版安装库，4.2.7.Final |
| 26.3-neoforge | PASS | PASS | PASS | 原版安装库，4.2.16.Final |
| 26.3-fabric | PASS | PASS | PASS | 原版安装库，4.2.16.Final |

每端使用官方Loader的全新目录和仅一份外部产品JAR，不带开发classpath或手工补库；共享Transport、公开Provider SPI和HTTP实际类来源已记录。固定世界仅从既有不可变基准校验后复制，不修改原基准；通过正式GUI语义进入，USER玩家读取为CLIENT_KNOWN、非存档/非强制加载。保存退出独立确认标题、新输入上下文、held=0和Lease释放回READ，再从标题正常退出，进程码0、Stopping日志、无崩溃报告且endpoint删除。

首次发布被真实CLI门禁拒绝：alpha-evidence入口await世界校验，世界收集器又动态import尚在执行的同一入口，产生未完成顶层await。publish-alpha.log保留原失败，当时latest未变、未发布。仅将验证函数改为静态函数绑定，不改生产或验收断言；70项发布器单元和实际CLI通过后，由既有发布器发布已准备好的同批七文件。安装和冻结结果未消费该验证模块变化，不重复启动游戏。

日志集中于外置Investigations/20261022-e-world-release：candidate-build-check-release-strict.log、packaging-gate.log、五端limited安装摘要、publication-cli-fix-tests.log、publication-cli-fixed.log与publish-alpha-fixed.log；安装原始结果在integration-packaged_runtime固定result，清单记录其哈希。上批五份安装报告移入该外置目录的previous-installed-results保留，未手改。五份旧自有安装按owner/锁/路径校验后进入回收站；发布器无警告，保留最近三份及所有使用登记，包括历史验收基线，仅回收自己的latest备份。

新增可用范围为批准的世界USER/DEBUG目录及CF-022分页，细节见生成手册与清单的eWorldTrial；本次独立安装只证明Loader/连接/读取/进退生命周期，不能替代最终JAR内所有功能或第三方组合验证。26.1.2灯熄灭、1.21.1优化器取消两项未复现异常仍开放；三攻击性能仍为历史NOT_MET，本轮未测。OPERATE真人协作、普通服务器deop/op及物理Esc三项集中待用户，未自动代验。M-01/M-09/F/第三方兼容范围未扩大，未操作Goal或子代理，未提交/推送/tag，Persistent Write=0。

## 2026-10-23 独立服务器控制台基础

用户批准开始服务端开发，本批落地有限初始目录，不代表两个完整里程碑或所有三部署组合已交付。
五端用原生服务器生命周期建立同一ProbeTransport/ProtocolState/RuntimeEndpoint；零玩家可用，集成服不额外创建第二入口。
默认不授予server_console，操作员身份不借玩家OP/Lease；READ分页读取已发布日志，OPERATE经服务器任务队列和原生分发器执行。
首批命令根help/list/say/seed/time/weather，停服/权限/保存/重载/间接或未知根发送前拒绝；全部仍是USER，不自动获得DEBUG授权。

参考端先单JAR实测，再移植四端。Forge1.20.1适配File目录与三参结果回调；Fabric不借用NeoForge的source访问扩宽，反馈委托DedicatedServer。
控制台输出从SERVER_STARTED接入后开始，INFO及以上、256条环、每条4096字符、每页最多128条，显式gap/截断；不读旧日志或还原终端像素。
语法错误实测只产生反馈而不保证结果回调；Operation completed只表示分发返回，worldEffectConfirmed=false，取消不承诺回滚。
直接native source反馈关联唯一Operation ID；普通日志不按时间伪装为命令反馈。

五端对应integration-server_peer/result/<target>-console.json全部PASS：只装各自官方Loader和最终产品JAR，无实验probe、开发classpath或在线玩家，
真实Companion握手及命令、错误Bearer/实例pin、READ拒绝写、TAKEOVER不适用、原控制台输入并存、时间设置后独立原生回读、语法错误回执及正常停止。
五份证明的JAR哈希与build/libs和build/release对应构件逐一相等，进程退出码均0。
生产引擎及真实HTTP测试证明默认scope拒绝、排队取消/关闭后不派发、执行时授权重查，既有客户端回归保持。
check/verifyRelease、OpenAPI/Java及TypeScript生成、Companion、Control/Hook/结构/文档门禁及严格合规通过，规范2.0.2失败0警告0。
原生编译弃用提示仍保留，不属于合规失败。

参考端integration-packaged_runtime的当前最终JAR已另行通过进入固定世界、USER读取、保存退出及关闭；结果为26.3-neoforge.json。
旧自有安装目录按既有owner/锁/路径检查进入回收站，新安装未触碰用户实例或原基准。
这不是五端新客户端运行矩阵、物理Esc或统一UI/UX验收。完整可选单端互通、Peer委托与服务器DEBUG/存档/录制/补全仍待实施。
本轮未使用Computer-Use、未操作Goal/子代理、未提交/推送/tag、未发布Alpha。固定快照20261022-1722-0ea052cb保持原样，Persistent Write调用0。
本轮构建日志在build/server-console-final-gates.log，参考端客户端日志在build/server-console-client-regression.log；不以本地build目录作为稳定分发路径。

## 2026-10-23 首批独立服务器世界 DEBUG

用户授权继续服务端DEBUG。主线程复用正式Deep Observation、Arm、ResourceVersion和typed mutation/batch，
完成零在线玩家主世界world/blocks读取及world.block.set；其余目录/维度和完整Peer委托不在本批实现中。
修改回执需读取权限，执行前拒绝缺scope；不借控制台scope或玩家Lease，不以命令包装冒充typed DEBUG。
资源版本绑定服务器epoch和区块对象生命周期，身份使用弱引用，不保留已卸载区块；仍是观察变化序列，不是全世界事务令牌。
Fabric区块守卫进入main及common Mixin配置，保留客户端集成服路径，原生图形Hook没有移位。

五端integration-server_peer/result/<target>-server-debug.json均PASS，绑定本次最终JAR：实际Companion读取、Arm、修改、
旧版本/原值/非法属性拒绝、未加载无加载、批次恢复、disarm及正常关闭；对应JAR哈希逐一匹配build/release。
原生控制台只为自有夹具准备loaded区域和正常关闭；这是Fixture/收尾，不是产品自动加载或玩家验收。
密集负控曾命中既有DEBUG预算，Runner按2/s补充节奏，不修改产品限流；属性案例按实际before选择不同值，避免把正确的NO_STATE_CHANGE当作失败。
生产HTTP及引擎测试覆盖无console scope的合法DEBUG、默认/缺读取权限拒绝、排队取消/disarm/关闭和生命周期失效。
仅切回READ/交回玩家输入不取消既有独立DEBUG，仍使用明确取消或disarm。

spotlessApply后check/verifyRelease及严格合规通过；当前日志为build/server-debug-frozen-gates.log。
参考端及Fabric最终单JAR客户端已进入固定世界、USER读取、正常保存退出，结果为integration-packaged_runtime/result下对应JSON；
自有旧安装目录按owner/锁/路径检查移入回收站，可恢复，不触碰用户实例/存档。
未执行新五端完整客户端矩阵、真人验收或全部部署组合，不宣称两领域READY。
固定Alpha保持20261022-1722-0ea052cb，不更新旧清单配新JAR；未发布Alpha、未提交/推送/tag，Persistent Write调用0。
