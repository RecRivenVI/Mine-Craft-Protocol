# 三种部署场景与能力边界

已批准目标：仅客户端、仅服务端、双端联动完整纳入 UI/世界首个里程碑的适用范围。
独立服务器 Runtime、有限非图形控制台及首批主世界DEBUG已接入；完整单端互通、其余服务器目录及 Peer 委托仍待实施。
本文负责部署设计；[当前状态](../project/status-milestones.md)负责进度，[执行计划](../project/plan-execution.md)负责当前目标与步骤，
[控制模型](concept-agent_control.md)和[世界模型](concept-world_control.md)继续负责模式、类别、输入与证据。
不因新增部署目标开放 Persistent Write、宿主控制或任意命令/对象后门，不把设计标成现有能力。

## 当前实现与差距

当前产品事实为 mod_client_side=true、mod_server_side=true，不能据可选 Peer channel 宣称单端安装已受支持。
五端除客户端 Runtime 外，独立服务器在 SERVER_STARTED 建立自己的认证 loopback Runtime，
SERVER_STOPPING 关闭。只在 DedicatedServer 启动，集成服不会再创建第二个 endpoint。
原 DedicatedPeerServer 仍依赖已连接 ServerPlayer 接收游戏负载，与独立入口分开。
已有 Peer 读取及 teleport/health/block 修改只是有限目录，不等于 UI/WORLD 全功能联动。

现有 Peer mutation 在服务端按开关和玩家 operator 检查，客户端 HTTP 另有 scope/Arm 等门禁；
服务端负载自身尚未完整验证委托 principal、Arm、资源版本及其生命周期。
hello/hello_ack 不等于细粒度授权、启动身份绑定或完成配对证明。
这些是真实待补边界，不能依赖可信 Companion、客户端检查或 peerAuthenticated 字段代替服务端验证。

当前固定Alpha保持既有both安装说明；本地开发候选已支持独立服务端控制台，不要求有玩家连接。
仅客户端互通、无模组客户端加入及完整可选协商不因可选Peer或本次零玩家测试就成为已验安装承诺。
用户已批准以可选协商推进部署实现；模板允许独立声明两端可选，是否启用仍须完成对应运行与互通证明，并明确安装承诺。
当前 mod_client_side=true、mod_server_side=true，双端必装要求不变；零玩家控制台可用不代表无模组客户端加入或全部单端互通已验收。
正式安装承诺仍按逐 Target 实机证明收口，不把有限 Peer 或代表端结果当作所有组合通过。
当前安装说明见[使用指南](../usage/guide-installation.md)，本设计不修改运行端或元数据。

## 首个里程碑的主体合同

以下身份不是同一主体，不能互相代替。独立服务端已有实例/Bearer认证，该表不宣称完整Peer委托已经存在。

| 主体/标识 | 当前事实与权威 | 不能据此获得 |
| --- | --- | --- |
| Runtime principal | 当前本机 Bearer token 对应一个 principal，scope 由实例所有者配置；不同模型或 HTTP 连接不是独立安全账号 | 服务器 OP、另一 Runtime 的 scope、资源 owner 或多租户隔离 |
| Runtime/连接 identity | runtimeInstanceId 标识本次实例启动；connectionId 用于本机连接预算和审计 | 玩家身份、跨重启许可或独立权限 |
| 当前玩家 | 服务端实际连接绑定的 ServerPlayer/UUID，原版游戏模式和权限决定正常操作是否接受；不信任请求中的玩家名或 OP 自报 | Runtime token、Debug Arm、服务器内部数据或任意资源权限 |
| 现有 Peer 接收端 | 服务端拥有实际世界与玩家，现有有限修改按服务端开关及玩家权限执行；委托/Arm/版本闭环仍是待实现项 | 因 hello、客户端已检查或 peerAuthenticated 就跳过接收端授权 |
| 服务器所有者/控制台主体 | 独立于玩家 OP；专用实例、启动身份和 Bearer 认证；额外 server_console scope 默认不授予 | 借某个在线玩家代理全部管理权，或借客户端 token 自动认证为服务器所有者 |
| 物理用户退出 | 本机 Esc 立即撤销玩家 TAKEOVER，保留 READ 与重新同意 latch | 证明外部对话同意、清除诊断污染，或替另一实例授权 |

首个里程碑的已有客户端入口继续验证认证、scope、模式，再按操作验证输入 Lease 或独立 Debug Arm、
owner/世界/资源版本和值条件；有授权不等于原版服务器已经接受，有发送回执不等于效果成功。
UI session Arm 不授予服务器世界修改权；不同 owner 的 Arm、epoch 和 revision 不能互换。
现有 Peer 接收端缺口必须实现并实际验收，不能以本表的设计定稿替代。
新增服务端 Runtime 和控制台按下方有限目录实施，不把当前单令牌模型描述为多账号隔离。

## 同一模型，不同控制主体

部署位置不新增第四模式或第三类别。READ、TAKEOVER、OPERATE 与 USER、DEBUG 保持正交，
另明确主体为玩家或服务器操作员，来源为客户端、服务器、控制台或存档。
USER 表示该主体在对应原生表面可获得的信息和可复现操作，不一概等于低权限。
操作员 USER 输出不能自动混入玩家 USER；服务器真值不能替代有界客户端模型。

| 能力 | 仅客户端，多人服务器无本模组 | 仅服务端，客户端可不安装 | 双端联动，包括集成服 |
| --- | --- | --- | --- |
| USER 读取，各模式按授权 | 当前 Screen/HUD/Toast 与有界客户端缓存 | 操作员实际收到的有界控制台输出，无玩家视角 | 玩家与控制台主体分开，不扩成服务器全知 USER |
| DEBUG 读取，各模式按授权 | 本地内部 UI/客户端诊断；远端内部字段无通道则不可用 | 授权服务器 LIVE 资源及已定义控制台诊断，不强制加载 | 两侧分别标 owner、来源、版本和权限 |
| 共享玩家输入，仅 TAKEOVER | 既有Lease下GUI/移动/瞄准/交互及聊天键入命令 | 无客户端输入端时不适用，不伪造玩家或宿主键鼠 | 客户端执行GAME_ROUTED，服务器正常校验 |
| DEBUG 修改，OPERATE/TAKEOVER 独立授权 | 允许的本地 typed 操作，不能伪造远端服务器修改通道 | OPERATE 下服务器 typed Fixture/Debug | 明确路由，接收端重验权限/前置条件，不自动满权 |
| 命令操作，统一 USER | 目标OPERATE下真实玩家原生提交无需Lease；聊天键入仍需TAKEOVER，实际权限照常检查 | OPERATE，经服务器所有者授权的原生控制台入口 | 明确玩家/操作员及实际路由，不借Lease、OP或身份互相代理 |
| Capture/Recording | 画面及获准结构化数据 | 控制台/世界结构化记录，不伪造截图 | 画面和服务器轨道分别标源、标时 |
| Persistent Read | 本地获准存档，远端存档不可读 | 本服务器获准 storage context | 显式选择获准一侧，仍为 last_saved_state |

控制台是 UI 领域的非图形表面，命令的世界效果归世界领域，不新增第三领域。
不把控制台反馈升级成 READ USER 世界查询，不通过后台执行命令补足只读接口。
Provider、条件、Event/resync、Recording、Artifact、网页/MCP 均保持相同主体和类别边界。
模式描述是否接管真人玩家输入：零玩家服务器正常提供 READ/OPERATE，不为凑齐三个模式伪造 TAKEOVER。
控制台操作不接管物理终端，也不阻止管理员同时输入；所有命令均为 USER，权限高低和修改效果另记，不转成 typed DEBUG。

## 仅客户端：玩家权限是上限，不是内部通道

普通输入和命令继续由真实连接、游戏模式、权限过滤命令树及服务器原生校验决定。
OP 不是所有能力的布尔开关；具体命令/资源的实际权限须执行时验证，撤权不能沿用旧许可。
模式或 Runtime token 不授予 OP，也不能制造服务器未发送的数据。

- 本地 UI/缓存诊断按本机 principal、scope、类别和资源边界授权，纯本地读取不机械要求服务器 OP。
- 目标本地 typed 修改可在 OPERATE/TAKEOVER 独立授权，仍需 scope/Arm/前置条件；网络 responder/服务器副作用另行分类，不能借本地 setter 绕过玩家校验。
- 无 Peer 或其他正式授权通道时，服务器内部 DEBUG 明确不可用，即使玩家为 OP；不偷读远端存档、不拼接 RCON，不伪造服务器权威。
- 目标OPERATE可经真实玩家原生命令API提交USER命令，不打开聊天框或占用共享输入；当前聊天helper仍需TAKEOVER/Lease，不能只删除门禁。实际作弊/OP权限照常检查，READ不自动执行查询命令。
- 无可信通道不提供服务器 typed setter；合法玩家高权限命令保持原生路径和相应效果/证据来源，不静默改成服务器 OPERATE 或 typed DEBUG。

集成服虽只安装一份 JAR，仍有两个逻辑权威端，不能证明远端无 Peer 兼容。
反作弊和第三方服务规则另行验证；有界缓存读取不保证符合所有服务器的公平游戏政策。

## 仅服务端：独立 Runtime 与控制台

### 独立入口与原生输入

当前初始目录：认证、实例发现、READ/OPERATE、控制台有界分页、原生命令及 Operation 查询/等待/取消。
GET /v0/server/console 需要 read/server_console，POST /v0/server/console/commands 另需 command/control 和显式 OPERATE。
从 SERVER_STARTED 接入后读取 INFO 及以上已发布日志事件，保留来源、线程、序号、截断和 gap；不回补启动前输出，不还原终端提示符、ANSI 或完整历史输出，
也不保证涵盖第三方绕过日志系统的直接 stdout/stderr。输出是操作员的独立 USER 表面，不是玩家世界读取。
初始命令根限 help/list/say/seed/time/weather；stop、op/deop、保存/重载、函数/间接执行及未知根发送前拒绝。
这是有意保留的初始目录，不是完整服务器管理支持，也不改变现有玩家命令目录。
命令通过 MinecraftServer 自身任务队列进入 owner thread，再调用原生命令分发器；不会写终端 stdin 或调用宿主 shell。
服务端回调差异由 Target 适配，语法错误可能只产生文字而没有结果回调，Operation completed 仅表示分发返回。
明确 worldEffectConfirmed=false，不把命令回执算作干净 PLAYTEST。
普通 HTTP 完成或连接池关闭不撤销已接受 Operation；未分发命令受五秒期限、显式取消和 Runtime close 约束。
首批服务器DEBUG复用正式Deep Observation、Arm与typed mutation：零玩家主世界world/blocks读取及world.block.set/有界批次，
需独立DEBUG授权，不借server_console或玩家Lease；未加载明确拒绝。请求与限制见[Native合同](../reference/protocol-native_v0.md#独立服务器世界-debug)。
完整连接授权撤销、其余服务器DEBUG、Persistent Read、控制台 Recording 和补全仍为待办，不以本目录开放它们。
TAKEOVER 在没有玩家输入表面的独立 Runtime 明确不可用，capabilities/control 不宣告可接管。

服务器 Runtime 须在零玩家、无客户端类/渲染器时启动认证 loopback HTTP/WebSocket，
复用实例目录锁、自动端口、启动 UUID、预算和关闭清理；Companion 明确选择服务器实例。
不绑定在线玩家才存活，不新增公网监听、LAN/TLS 或宿主终端自动化。

READ USER 订阅实际发布的控制台输出流，保留时间、来源、顺序、截断和 gap，按权限过滤敏感字段。
不抓宿主终端画面、不默读任意日志/凭据文件；历史日志若后续加入，须单独授权、标明时效，不能伪装 LIVE。
补全仅查询当前控制台主体的原生 dispatcher，受明确预算限制，不执行命令探测结果。

控制台操作使用 OPERATE 和独立 server_console 主体授权，不接管真人玩家，不获取或伪造玩家输入 Lease。
文本经原生服务器命令队列和 owner thread 执行，不运行 shell、PowerShell、JVM 表达式或任意 packet。
READ 不执行命令，OPERATE 不能借控制台模拟真人客户端的 GUI/键鼠；没有玩家输入端时 TAKEOVER 明确不适用。
控制台请求仍需有界操作所有权、deadline、排队取消及会话关闭清理，不用“无需玩家 Lease”省略这些约束。
console source 权限高于普通玩家，须独立的控制台 scope 和服务器所有者授权，
客户端 token、玩家 OP 或 TAKEOVER 模式都不能自动授予这个主体。

命令统一为 USER，以实际命令树、原生校验和主体授权判断可用性，不靠逐条划成 DEBUG；
输入入队、原生执行、收到输出和世界效果分别记录，不以邻近日志伪造请求关联或成功。
取消阻止尚未分派的输入，原生执行后的效果不承诺回滚，返回部分执行/未确认状态。
嵌入命令、函数/调度等 M-07 的限制不因切到控制台消失。

stop、权限/封禁、保存、重载等有真实控制台语义，但当前玩家合同明确拒绝它们。
控制台目录必须登记这些分支与破坏性/宿主影响，实施前另行冻结允许/拒绝、专用授权和收尾规则；
按明确允许/拒绝与已知限制收口，不自动复用玩家允许目录；未决项为 PENDING，不能冒充全部控制台覆盖。
正常游戏保存与正式 Persistent Write 分开计数，不能以前者绕过后者禁令。

### Debug、人工退出与证据

独立授权的 DEBUG 读取捕获已定义服务器内部状态；OPERATE DEBUG 使用受限 typed 目录并验证
principal/scope/Arm、世界/资源 identity、revision、值条件、deadline 和恢复，不要求输入 Lease。
无玩家也能操作适用世界资源；指向玩家的操作须显式选择存在的真实玩家，不创建代理玩家或借 console 冒充玩家。

没有图形窗口就没有物理 Esc 钩子、蓝边、Pointer 或像素 Capture，不生成假通过证据。
提供明确人工取消/撤销指定操作或授权的入口，保留原生管理控制；不能阻挡管理员终端、OS 关闭或紧急停机。
不把管理员普通终端输入当成撤销 Agent 的暗号；客户端 Esc 也不远程撤销独立服务器 DEBUG。
断线、操作期限或停服停止相应排队操作；已执行效果不被虚报撤销，独立授权工作按自身生命周期清理。
客户端人工退出只保留在相应接管/重新同意边界，不通过服务器日志猜测聊天同意。

控制台提交须记录 console authority、原生机制、权限类别和实际效果；
即使命令类别为 USER，已知改变世界的诊断/准备效果也不是干净 PLAYTEST；typed DEBUG 修改与明确 Fixture 继续各自标源。
可录制控制台、事件、结构化状态和操作结果，不以空 PNG 冒充玩家画面。

## 双端联动：权限交集，不是自动满权

完整支持指已定义目录在相应端有实现且可授权，不代表连接后自动获得所有权限。
客户端拥有 Screen、Pointer、相机、玩家输入和 framebuffer；服务器拥有服务器资源。
Peer 不能直接调用 GUI 业务或用服务器定位冒充玩家移动。

有效权限取 Agent scope、服务端批准的委托、实际玩家/操作员权限、资源策略及当前能力的交集。
客户端不能自报 operator/scopes/Arm 已验证，接收端执行前重查；撤权使相关许可立即失效。
UI Arm 不授权服务器库存/方块，本地 Arm 不能自动充当服务器 Arm；
沿用相同授权语义，但服务器须持有并验证自身资源的许可，不建立第二套输入 Lease。

握手/委托须绑定两端启动身份、实际游戏连接和玩家、世界/维度生命周期、能力版本、
scope/资源范围与期限；这些是规划信息，不是已冻结的 V0 schema。
游戏连接认证只证明连接玩家，不证明其具有 Runtime Debug 权限；可达 hello_ack 不能代替授权。
服务器所有者的独立管理 principal 不等于玩家 OP，高权限 token 不经聊天或公开 Payload 分发。
资源 revision 由资源 owner 产生，两端 session/snapshot 不互相代替。

Peer 超时/断开取消依赖它的工作、拒绝旧结果/许可，不回退直接修改、玩家命令或磁盘。
普通游戏连接仍在时，获准的客户端读取/输入可按仅客户端能力继续，不自动升权或重新接管。
游戏断线/切服结束玩家 TAKEOVER、清理输入退 READ，并失效该连接的非接管命令、旧 Peer、世界引用和待返回状态；
重连不重放未确认 mutation。跨端批次没有整世界事务，逐项报告已应用、未执行、未知及恢复失败。
packet 已发送、permission cache 或按钮 enabled 都不是本次服务器接受的证明。

并发读取可并行，每个真人玩家输入仍只使用其客户端既有 Lease；非玩家控制台操作使用独立服务器授权，不另造输入租约。
不把控制台身份借给玩家。多个已授权来源修改同一资源时，
在服务器 owner thread 串行提交并重查显式资源/值条件，stale 明确失败；不能凭旧快照覆盖新值。
OPERATE 与真人可并行并覆盖用户状态，不因真人活跃一概拒绝，也不以优先权跳过调用方声明的条件。
委托方的模式/世代绑定其认证控制会话，接收方验证所存委托与来源，不靠请求里自报 mode 升权；
独立管理会话不被远端模式请求自动切换。这不宣称当前 loopback principal 已具备多租户隔离。

## 能力发现与消费者

在现有 capabilities/session 中演进部署角色、控制主体、可用来源、协商目录和拒绝原因，
区分适配器支持、当前实例具备和当前 principal 获准，不能由 installed=true 推导可执行。
无通道、无玩家/Screen、缺授权、旧身份、原版不存在和实现未完成必须可区分。
具体 wire 字段及兼容迁移在实施时同步 Native/OpenAPI/MCP，本设计不新增假接口或未来响应字段。

Companion 按明确实例/角色连接，断线不猜端口/token；网页玩家窗口与服务器控制台视图分开，
不把日志绘成游戏截图，也不在本轮暗中扩建网站。
Wait/Assert、Event、Recording、Artifact 和错误投影保留主体、来源、类别、时效和一致性；
读操作不通过副作用补数据，跨端不承诺同 tick。文本始终是不可信数据，不产生授权或 Agent 指令。
控制台单行/请求、输出、缓冲队列、补全候选和总期限均须有界并可发现，溢出显式报告，不无限追日志。
Persistent Read 只取获准安全 context，不接收任意路径；LIVE 仍不得隐式读磁盘。

## 两个里程碑的共同收口

部署是覆盖矩阵维度，不另建三个并行里程碑；归入 M-09，关联 M-01、M-08、M-10 至 M-12。
完整三部署现为已批准的双里程碑目标；实际剩余分组只在[状态页](../project/status-milestones.md)维护。

| 责任 | 必交付内容 | 所属领域 |
| --- | --- | --- |
| 安装与产物 | 符合模板的单端/双端安装与可选协商，最终 JAR 不依赖开发类路径或手补依赖 | 两领域共同基础 |
| 权限与身份 | 玩家、操作员、Runtime principal、委托及 owner 的独立授权；撤权/重启/连接/版本失效 | 两领域共同基础 |
| 独立服务器 Runtime | 零玩家启动、认证、实例发现、命令与 typed 工作调度、停服清理 | 两领域共同基础 |
| UI 表面 | 客户端 Screen/HUD/Toast 与非图形控制台输出/补全/命令输入；不伪造无客户端的截图 | UI |
| 世界资源 | 客户端缓存/玩家动作、集成服和独立服的批准 DEBUG 目录、双端路由及无通道拒绝 | 世界 |
| 消费者与交付 | Native/MCP/网页、Wait/Event/Recording/Artifact、展示与最终制品矩阵保持主体和类别一致 | 两领域共同收口 |

UI 首个里程碑覆盖图形和适用非图形原生表面；世界首个里程碑覆盖客户端 USER/实际输入及获准服务器 DEBUG。
Headless 没有 Screen/相机是原生缺席；独立 Runtime/初始控制台及首批方块DEBUG已接入，其余服务器资源/补全/Recording仍是待办，二者不能互换。
两领域分别达到 READY 再交用户验收，不以集成服证明单端安装，不把设计写成当前能力。

五 Target 均须使用各自固定副本验证：仅客户端模组/无模组服务器、仅服务端模组/无模组客户端及零玩家、
双方安装且 Peer 成功、Peer 禁用/未授权/失联、集成服；覆盖普通/管理玩家、作弊可用/不可用、独立操作员授权、
撤权与重启/重连失效。只有适用主体本来不存在的表面可标不适用，不能因此跳过合法授权的正例。
单端互通、Headless 和双端运行均须最终 JAR 实测，不由开发 classpath、静态元数据或代表端结果替代。

负例包括缺 scope/Arm/Lease、错误主体/实例/世界、旧委托/版本、无 Peer 却取内部状态、
命令/GUI/Provider 旁路、排队后撤权、迟到/重复响应、断线/停服及取消，拒绝前无新输入或 mutation。
commit 后取消如实记录效果，不能伪造零写入或回滚。
只用自有隔离实例和固定副本，默认一个客户端、最大 4 GiB，可带本测试必需的自有服务器；
不使用 Computer Use、外部 Prism 或重要存档，全部进程/端口/锁有界清理。

实施顺序为安装/主体合同 → 独立服务器入口与现有 Peer 接收端授权 → 控制台/世界和消费者 → 五端最终制品矩阵 → 展示与人工交付。
先冻结管理命令的允许/拒绝及专用授权再启用，不以设计立即开放服务器管理权；不借此实现 Fake Player 或 Persistent Write。
