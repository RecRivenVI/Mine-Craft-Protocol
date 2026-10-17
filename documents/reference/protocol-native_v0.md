# Native V0 协议

当前 OpenAPI 文档版本 `0.0.1-ui-m1`；`All 78 formal HTTP operations`。
V0 是未冻结的产品原生合同，Wire Protocol v1 NOT FROZEN。
机器权威为[OpenAPI](../../components/protocol_schema/src/main/openapi/minecraft-control-v0.json)；
字段、typed discriminator、错误和 schema 从此生成，本文不维护第二份协议。

UI树、resolve及UI Wait/Assert在捕获和发布之间绑定mode generation；即使模式切出再切回，也以UI_READ_CONTEXT_CHANGED拒绝旧结果。
UI调试事件还需独立UI DEBUG读授权，世界DEBUG事件权限不授予UI详情；缺少UI读权限时不向诊断ring发布该UI事件。
录制的UI上下文仍是USER快照，缺ui或模式不允许USER时不调用UI owner，记录不可用；不会自动回退成DEBUG UI。
UI修改的录制诊断详情另需debug.ui.read，在异步写入前复验；权限不足或上下文改变时省略详情，但保留证据污染标记。
同步mutation回执继续按既有写合同返回本次修改的before/after；这不授予历史查询或其他资源的读取权限，独立消费须重新授权。
模式、Lease、Arm和修改权限不因这些读取检查而改变；V0消费者需处理409，不自动重放先前操作。

异步HTTP操作取消返回409 REQUEST_CANCELLED；Runtime关闭引起的取消返回503 RUNTIME_SHUTTING_DOWN，不再映射为内部500。
二者带executionOutcome=not_confirmed、retrySafe=false：取消不代表原生回调未发生，也不代表回滚，尤其是Quit Game已开始退出的情况。
若连接先关闭，消费者仍应核对自有PID、退出码、日志、endpoint及效果，不自动重放输入；既有scope/模式/Lease检查与优先级不变。

RATE_LIMITED及EXPENSIVE_OPERATION_RATE_LIMITED的429带retryAfterMillis与rateLimit：category为发生拒绝的桶，
operationCategory为请求所属类别，scope只表示principal/connection预算维度，不是授权scope。
capacity、remaining为整枚token额度，refillPerSecond为补充速率，cost为当前请求成本；快照来自拒绝时刻。
retryAfterMillis是该桶最早补足的估计，不预约额度，也不保证稍后请求通过；较早已扣的桶不退还，限流设置不变。
Native/MCP及网页错误保留结构，包含二进制读取的JSON错误；不自动重试玩家输入或mutation，先重查期限、模式/Lease及实际效果。
CONNECTION_LIMIT_EXCEEDED是独立并发连接上限，不是token补充倒计时，应释放不再使用的连接。

GET /v0/readiness的overall=ready指输入/观察桥接Hook就绪，不表示世界图像或第三方渲染已收敛。
Session的screenClass是底层Screen；加载覆盖层仍可能存在。采证前读取UI树的overlayIdentity、非零窗口/视口、预期Screen与玩家/世界状态，
再按使用方自己的有界oracle检查输出。Composite Capture取内容边界的当前帧，不暗含等待任意异步场景稳定；渲染帧推进也不能替代该判据。

GET /v0/wait/screen与POST /v0/operations/wait/screen等待Session的screenClass并返回Session，不捕获控件、文本或隐藏UI内容。
它们沿用read授权和模式兼容的会话元数据合同，不表示页面可交互；需要语义就绪时使用带ui读取授权的screen ready条件。
GET /v0/ui/vision/context是需ui/capture的技术校准信息，不返回语义节点正文，也不取得玩家输入权；真实控件发现仍使用相应类别的UI树。

## 接入与表面

`world.block.set` 可带 `properties` 与 `expectedProperties` 字符串映射，各最多64项；实际名称/值须属于目标方块的原生状态定义。
同类型只覆盖给出的属性，其余保持；替换类型从新类型默认状态开始。省略属性保持原有调用语义，同类型无变化仍拒绝。
`expectedProperties` 为子集值条件，不替代必需的 Resource Version、世界身份、OPERATE、独立 scope 和 Arm。
非法/未知属性和值在原生写入前拒绝。只操作已加载 LIVE 方块；原版邻居更新、BE 替换及可能的次生效果/掉落照常发生，不承诺多方块回滚。
返回仍为 DEBUG_PRIVILEGED/diagnostic，不成为 PLAYTEST 或 Persistent Write。

适用版本的自动合成器网格槽位额外提供可选 `slotDisabled`，来自客户端原生菜单的可见禁用状态。
这不等于 `active=false`：空的禁用格仍可通过正常语义点击重新启用，不新增 Debug setter 或专用发包。
状态变化推进 `menuRevision`，移动中的目标状态变化在点击前拒绝；其他菜单与没有自动合成器的 Target 不返回此字段。

语义 UI 手势默认按目标节点及 menuIdentity、Screen/视口/GUI 尺寸、所持物品校验，不把无关槽位的
menuRevision 变化当作目标失效。目标物品/数量/数据组件、可操作性、语义上下文或几何变化仍为 UI_TARGET_INVALIDATED。
POST /v0/ui/action 的显式 X-MCP-Expected-Menu-Revision 保留全局版本前置条件，并在移动中复验；
原始坐标移动保留全局菜单守卫。拖动只在原生鼠标回调完成后、客户端线程尚未让出时更新自身槽位/所持物品守卫；
下一输入前仍严格比较，不放宽菜单/Screen 身份，不将外部变化视作自身拖动。

未选中list_item的double_click可在第一次原生按下回调结束时同步该点击自身产生的selected状态，再校验第二次点击。
更新仅发生在原生owner线程、回调完成且尚未让出时，一次手势至多一次；身份、文本、资源、几何、Screen/视口及其他前置条件不变。
移动期间或第一次释放之后的外部选中变化仍拒绝；第一次点击已经切页时不把剩余点击投递给新界面，不宣称整个手势可回滚。

UI 槽位与 carriedStack 提供可选 contentRevision/contentGuardAvailable；版本是 Runtime 随机密钥派生的上下文令牌，
不公开 NBT/组件内容或跨槽位指纹，不是授权，也不是新增 selector 字段。menuRevision 纳入原生物品数据而不只比较种类/数量。
内容令牌不是单调的 Resource Revision，内容恢复相同时可恢复相同令牌；跨请求严格 pin 继续使用 menuRevision。
单物品原生编码最多 2 MiB，每菜单快照累计 16 MiB、最多 2048 个槽位；失败/超限使相应槽位守卫不可用，
物品相关手势返回 UI_CONTENT_GUARD_UNAVAILABLE，不降成不受保护的点击。客户端同步数据不能冒充服务器或持久化权威。
键盘等待只免除无关槽位版本变化，显式全局版本仍严格；原始坐标在等待和移动阶段均保持全局检查。
UI/原始输入在初检后进入串行队列，轮到执行时由原生 owner thread 再检查调用方原始版本值；
不将排队后的最新值替换成调用方的 expected。pipeline 同样支持两个可选版本头，在队列执行开始时检查。
过期返回 STALE_SCREEN_REVISION / STALE_MENU_REVISION；已接受的 pipeline 通过 Operation.errorCode 报告失败。
type_text的replace使用节点声明的原生输入能力：默认select_all；旧版追加型编辑器可声明textReplaceStrategy=backspace_from_end和valueComplete=true。
后者按完整有界值的Unicode码点数发送真实Backspace；不完整值、未知策略在输入前拒绝。
两条路径都读取实际清空结果，未清空返回UI_TEXT_REPLACEMENT_FAILED且不追加新文字；已有玩家输入不承诺回滚，不能盲重放。
执行前拒绝不发送新输入、不释放另一条已持有的原始流；取消/Lease 丢失传播到待完成的前置读取。
该条件是执行开始的前置条件，不是整个 pipeline 的原子事务；操作自身切页/修改后的步骤按各自守卫执行。
原始 expected 值还随输入许可传到首个产生输入的原生回调；同一客户端线程刷新并复核版本后才进入分发。
只读进度查询不消耗该条件；后续自身换页不永久绑定初始版本，仍按各步骤的目标/几何/身份守卫执行。
这不是整个动作或 pipeline 的原子 compare-and-act，不提供回滚；客户端不能自动忽略或替换旧值。
未新增模式、权限、路由或可伪造的跳过守卫参数；MCP 语义操作复用同一生产路径。

pipeline 的 input 是基础 scope，不代替步骤权限。ui.action/ui.drag 另需 ui；wait.until/assert.that 另需 read，
其 UI 条件另需 ui 与对应 USER/DEBUG 读取授权，event 条件另需 event，世界条件沿用独立类别授权。
整条计划在入队/声明手势前校验这些权限，每个步骤执行前重查；缺权限返回 SCOPE_DENIED，不先执行前面的输入。
未知/内部步骤在执行前失败，已经返回 Operation 的失败仍看 errorCode；这不是后续原版效果的事务或回滚。
GET /v0/operations 保留基础 scope，并增加可选 additionalScopes / additionalScopesByStep；后者是执行同源的公开步骤目录。
这些声明不授予权限；受限客户端不能再借 pipeline 绕过 ui/read/event，也不能自动补权限或切换模式。

POST /v0/command/player 使用当前 Chat 绑定、原生字符回调与 Enter，要求独立 command、control、input scope、TAKEOVER 与现有 Lease。
不调用 sendCommand 作为 helper 捷径，不静默关闭已有 Screen/覆盖草稿；不支持的绑定明确拒绝。
原生命令输入框上限包含斜杠，当前五端为 256 UTF-16 单元；传输的 2048 预算不绕过该限制。
世界、连接、Screen、输入框身份、焦点、光标与草稿逐阶段复验；输入许可绑定预期命令，仅 Enter 阶段允许提交。
取消只立即释放输入，不自动提交/擦除草稿；调用方须显式处理残留界面，不能盲重放。
GAME_ROUTED_RAW 是输入来源，不是服务器执行确认。响应 accepted 只表示提交；serverValidation 表示仍受原版验证，
不是验证已完成。effectClassification 为 COMMUNICATION/INFORMATION/STATE_MUTATION，
classificationSource=native_player_command_tree；解析的是当前玩家实际收到的原生命令树，不提升玩家权限。
submissionStatus=submitted_unconfirmed、effectConfirmed=false、serverValidationObserved=false 与 gameplayAcceptanceEligible=false。
helper响应另有commandFeedback：只取Enter后500ms内新增的客户端聊天消息，最多16条、每条512个Unicode码点。
association=temporal_not_guaranteed明确可能混入无关消息或漏掉延迟反馈；complete只表示采样窗口结束，
不是服务器确认。基线历史及隐藏/过滤的文本不返回，readable=false时messages为空；消息是非可信游戏内容。
普通命令引起客户端上下文变化时，反馈提前以terminationReason=client_context_changed结束，observedMillis可小于500；
仍返回已提交但效果未确认的来源，不读取新上下文。独立的Lease/模式撤权或取消仍按原错误合同处理。
观察仍绑定同一gesture/连接/Lease并可取消，不新增命令重试或长期聊天订阅；原始输入来源回执不自动拥有这个helper采样。

Wait/Assert的screen条件可带ready=true及selector，组合匹配Screen、无overlay、正窗口尺寸及新鲜可见/启用的单一目标。
readyAction默认click，必须存在于该目标公开actions；多目标要显式nth。结果evidence.pageReady描述这一次owner-thread快照，
不是将来点击的保留许可或最终画面收敛。缺selector为INVALID_CONDITION，旧screen条件未带ready时只判断Screen，不隐式改变合同。
block条件现有available=true表示目标块已经可读；可再加blockId验证具体内容。不加载区块、不把不可用状态当成air。

USER自身玩家响应包含客户端gameMode、onGround与abilities.flying；flying不是mayfly权限。
gameMode为survival/creative/adventure/spectator；无客户端世界或字段不可得时相应值明确为字符串unknown，不伪造false或服务器真值。
三个字段经client/player Provider、State Frame、条件和MCP保留，仍遵循模式、read scope与有界客户端模型，不开放服务器内部信息。

WORLD_TARGET_NOT_HIT的异步Operation可带errorDetails.actualHit，同步错误可带actualHit。
它只含当前客户端实际pick或候选outline ray的有界事实：kind/source、命中位置、距离，以及适用的方块坐标/面或实体类型。
source明确区分native_interaction_pick、bounded_client_vanilla_ray和candidate_outline_ray；后者是失败候选的实际遮挡，不冒充当前准星。
不可得为unknown，不携带UUID、NBT或私有输入guard；诊断不会改变原生命中规则、自动瞄准穿墙或派发被拒绝的按键。
状态修改的 evidenceContaminated=true，原因为 player_command_state_mutation；纯沟通/信息不因 helper 本身污染。
mode=PLAYTEST/DEBUG_PRIVILEGED 是证据来源，不是 Presence Mode 或授予 Debug Arm。
原生聊天/链接在方法入口与最终发包前复验权限、当前连接、模式/Lease 和有限 Operation 输入许可；
其 GAME_ROUTED_RAW 回执附在相关输入步骤和 pipeline 的 commandSubmissions 中，release 不能抹去来源。
输入 Operation 在执行中、失败、超时或取消后仍保留已知命令来源；没有完整结果时返回
type=input.command.provenance、partial=true，不附带失败步骤的任意内部数据，也不冒充成功执行或回滚。
commandSubmissionsComplete 仅表示该输入序列的来源收集结束，不表示服务器已确认、效果成功或 held input=0；
取消后的原生清理未结束或清理失败时可为 false。重复 GET/wait 不复制新增提交；状态为终态也不能推断零效果。
终态事件在 Operation 状态提交后发布，观察回调不持有 Operation 锁，避免与控制/会话锁反向等待。
未知/未决效果为 PLAYER_COMMAND_UNCLASSIFIED、宿主管理为 PLAYER_COMMAND_OUT_OF_SCOPE、
当前树不存在或语法不完整为 PLAYER_COMMAND_NOT_AVAILABLE，均不发送命令包。
状态修改在发包前标记活动录制，USER timeline 只保留投影的来源字段，不含命令文本；失败/取消不清除该事实。
命令方块与命令方块矿车的原生配置包在同一最终发包边界检查，不新增 API 或发包捷径；
仍需 command/control/input、TAKEOVER、当前 Lease 与有效输入许可，界面操作另需 ui。
回执增加可选 configurationKind=COMMAND_BLOCK/COMMAND_MINECART/JIGSAW_BLOCK/STRUCTURE_UPDATE/STRUCTURE_SCAN、configuredCommandEffect，
classificationSource=native_player_command_configuration。清空为 NONE，其他载荷按当前玩家原生命令树分类；
宿主管理、未决或不可用命令仍拒绝。配置本身一律为 STATE_MUTATION、DEBUG_PRIVILEGED、
evidenceContaminated=true、contaminationReason=player_command_configuration，不表示配置的命令已执行。
USER Timeline 仅保留上述来源投影，不记录原始命令或内部对象；后续失败/取消不抹去已发生的配置提交。
新字段为可选且原有聊天回执不变；严格枚举消费者须接受新增来源/污染原因，V0 仍未冻结。
configurationPayloadType 可选，当前生产者为 COMMAND、BLOCK_STATE 或 STRUCTURE_PARAMETERS；旧命令配置回执可没有该字段。
严格枚举消费者须接受新增配置类型，且不得把方块状态或结构参数当作可直接执行的命令文本。
Jigsaw final_state 经当前原生命令树的方块状态/载荷解析，不当作命令文本；合法值的 configuredCommandEffect 为 STATE_MUTATION，
空值不是“清空命令”。玩家权限、scope、TAKEOVER、Lease 与输入许可仍独立检查，不执行生成。
capabilities 的 command.player.configuration=native_command_blocks_and_jigsaw_final_state 表示适配器支持，
command.player.jigsaw_generation=unavailable_unresolved_template_effects 明确当前未决策略，不授予权限。
Jigsaw Generate 的模板效果尚不能可信解析，发送前以 PLAYER_COMMAND_UNCLASSIFIED 拒绝；
原版会先提交配置再请求生成，因此失败 Operation 可能已有配置回执/诊断污染，partial 来源不能改写成零修改或回滚。
配置包与生成包分别计数，存值不证明模板执行；未知生成仍是 M-07 缺口。
26.1.2/26.3 的 Test Block 配置与 Test Instance 的 INIT/QUERY/SET/RESET/SAVE/EXPORT/RUN
属于未开放的管理效果，Agent 提交在原生最终发包前返回 PLAYER_COMMAND_OUT_OF_SCOPE，不解析其载荷或发布成功回执。
原版编辑器可能已经改变客户端草稿或关闭界面；拒绝只保证未提交对应管理包，不宣称回滚全部 UI 行为。
1.20.1/1.21.1 的原生构件不提供上述两种包，不注册虚假的对应能力。
Structure UPDATE_DATA 和 SCAN_AREA 作为原生参数修改保留 STATE_MUTATION 诊断来源，不猜测命令树；
扫描是玩家主动触发的 Vanilla 动作，不是 READ 查询，不据此声明服务器扫描不会获取区块。
SAVE_AREA 在发送前以 PLAYER_COMMAND_OUT_OF_SCOPE 拒绝文件保存，LOAD_AREA 以 PLAYER_COMMAND_UNCLASSIFIED 拒绝未决模板效果；
两者不能到达服务器后才拒绝，因为原生 handler 会先修改结构参数。拒绝不发送配置包，也不产生已提交回执。
command.player.structure_configuration=native_parameter_update_and_scan 表示适配器支持，
command.player.structure_template=unavailable_storage_or_unresolved_template_effects 表示当前策略；两者均不授予权限。
命令 mutation 的原生 Tag、BlockInput 和 ItemInput 载荷还会检查 Command/final_state，
并在同一当前玩家命令树/注册表上递归解析内嵌命令或方块状态，不以命令文本子串猜测。
原生已验证 NbtPath 的字段名决定标量 Command/final_state 的含义；路径过滤器、带点的引用名称不冒充字段分隔。
普通名称文本与现代 minecraft:custom_data 不当作可执行程序；旧物品只从 BlockEntityTag/EntityTag 取载荷。
每层原生 Tag/JSON 图各最多 512 节点、深度 32，JSON 节点累计最多 1024；
内嵌程序最多 16 项、递归最多四层，合成的分析文本累计最多 32768 字符；单条文本仍受 2048 预算限制。
编码/解析不可用或超预算明确失败；分析不执行命令，不读取服务器对象或磁盘，不增加权限。
data modify 的 from/string 来源值无法在该客户端合同下可信解析时为 PLAYER_COMMAND_UNCLASSIFIED，
不隐式查服务器补齐。函数/调度、其他不透明载荷和管理型 GUI 仍未完成，不将安全拒绝算作全命令覆盖。
正式命令回执字段保持不变；mutation 根不会因内嵌程序只读而变成干净 gameplay。

世界 USER block、scene.blocks 与 entities 的可执行候选新增 targetRef/targetTtlMs；
现有 /v0/pipelines 的 world.action 接受 aim/attack/use/mine/use_hold/place，不增加 HTTP 路由或权限。
input scope → TAKEOVER → Control Lease → 队列/截止时间 → 原生上下文/生命周期/命中复验全部仍有效。
短按使用真实映射与 GAME_ROUTED 输入；effectConfirmed=false，效果必须另行观察。具体字段以 OpenAPI 为准。

五端 Runtime 默认端口为 0，由操作系统 bind 时分配。实际地址在本实例 minecraft-protocol/endpoint.json，
只发布 loopback URL、Target、目录、进程/启动身份及相对 token 文件名，不含 token。
同一目录的 OS lock 在写入 token 前取得，重复 Runtime 拒绝；正常关闭移除记录，崩溃残留不构成有效连接。
Session/capabilities 的 runtimeConnection 是实际连接状态；transportContract 描述自动端口/发现/固定端口支持。
runtimeInstanceId 不等同世界 sessionEpoch、Storage Identity、Resource Version 或输入 Lease。
发现消费者必须携带 X-MCP-Runtime-Instance，认证后、HTTP/WS/二进制分发前复验，
失配返回 RUNTIME_INSTANCE_MISMATCH/409，响应头也返回本启动身份。
显式固定端口仍支持；占用时失败，不静默换绑。旧显式 URL/token 客户端可继续使用，
但省略实例身份时没有重启隔离保证；新消费者应使用发现与身份 pin。
连接文件是受信任本地配置，不抵御可改写实例文件或控制宿主进程的恶意程序。

基础世界分类覆盖 GET /v0/player、/v0/server/player、/v0/world/block、
/v0/server/world/block、/v0/world/entities、/v0/server/world/entities。
category 默认 USER：客户端 player/block/entities 和 scene 使用有界客户端世界模型，不以画面可见性过滤，
authority=CLIENT_KNOWN、visibility=not_evaluated、storageAccessed=false、chunkLoadRequested=false。
玩家允许位置/朝向/生命等客户端字段；单块和实体半径至多 32，场景半径默认 4/最大 8。
实体可提供客户端 baby、TamableAnimal 的 tamed/sitting 与牵引反馈 leashed/leashHolderKnown；
仅持有者已解析时提供 leashHeldByPlayer，不暴露身份、内部 love 状态或繁殖冷却。
这些可选字段不适用/未知时不能推定 false；不是服务器状态确认，也不保证所有可驯服物种覆盖。
scene 的方块为立方体、实体为球形；blockLimit 默认 256/最大 512，limit/entityLimit 默认 64/最大 128。
最多扫描 4096 方块/2048 实体、循环 25 ms、响应 512 KiB，字符串截断明确标记。
未加载/出范围/无世界分别返回 chunk_not_loaded、outside_user_radius、client_world_unavailable。
截断或空数组不证明目标不存在。无自动 Chunk 加载、服务器查询、磁盘或内部状态 fallback。
scene 的 aimPresentation 只是原生准星轮廓提示，不能当作新世界语义动作；Screen/HUD/F3 不影响缓存模型可用性。

服务器入口和内部 Deep/Provider 仍要求显式 DEBUG，read/debug/debug.world.read 与 READ/OPERATE；
TAKEOVER 下 DEBUG、OPERATE 下 USER 返回 WORLD_CATEGORY_MODE_MISMATCH，不要求只读 Arm/Lease。
异步发布复验权限与 mode generation，变化返回 WORLD_READ_CONTEXT_CHANGED，取消传播到原生子工作。
USER 玩家坐标/生命条件与客户端 block/entities Provider、Frame、Recording 使用相同有界入口；
服务器/未知内部 Provider 先拒绝再捕获，Frame/Recording 先整体预检且不同类别不能混用。
实体 UUID、生命、AI、NBT/库存不进入 USER；玩家本人的客户端生命字段不等于开放所有实体内部状态。
UI USER、玩家输入、控制/调试权限与证据合同不因世界读取放宽而改变。
condition.category 是读取类别，事件家族筛选用 eventCategory。V0 响应由 HUD-only 变为客户端模型；
消费者应检查 source/stateSemantics，不能依赖旧 presentation 或 scene.nodes 字段。Wire v1 仍未冻结。

WS /v0/events 和 GET /v0/events/resync 的 category 默认 USER；DEBUG 独立授权。
两类 ring、游标和计数独立，USER 不收到内部事件；事件过滤不能改变连接的类别，切换需重新连接。
resync 不附带其他权限域的 Operation 结果；UI 仅在 ui scope 与 USER 兼容模式下提供。
每个 WS 订阅最多一个待完成 resync，关闭连接会取消；排队发送时再次校验授权。
录制请求保存 category；列表、状态、停止、Artifact 的 query category 必须匹配，默认 USER。
DEBUG 录制不进入 USER 列表，错类 ID 与不存在相同返回 RECORDING_NOT_FOUND；下载逐块复验授权与 mode generation。
UI/世界Debug batch的operation get/wait、Wait/Assert operation条件均显式category=DEBUG，不能从USER入口重取诊断结果。
创建时固定记录批次所属UI/世界领域；纯UI只要求UI诊断读权限，混合批次同时要求两个领域的读取权限。
写scope或Arm不授予读权限；异步返回前复验类别、权限与模式generation，变化后拒绝DEBUG_READ_CONTEXT_CHANGED。
批次提交回执只返回Operation生命周期，即使原生任务已完成也不附结果；结果通过显式DEBUG读取取得。
此类内部诊断Operation的取消仍沿用既有控制面授权，不新增诊断读取门槛；取消响应只报告生命周期，不返回内部result/error/errorDetails。
普通输入 Operation 的命令来源是当前玩家提交的元数据，不包含诊断步骤内容，按上方 commandSubmissions 合同保留。
world/fingerprint 与现有 Phase9A LIVE 实验读取也明确选择 DEBUG；PERSISTED 独立 storage.read 合同不变。

worldContract.readConsumerIsolationComplete 描述 LIVE 读取消费链已分类；
consumerIsolationComplete/milestoneComplete 仍为 false，pendingClassification 保留玩家命令效果。
这不是完整 USER 感知、第三方渲染适配或世界里程碑 PASS。

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
screen/ui.exists/menu/inventory 条件沿用同一 UI 类别门禁及原生投影，不把 DEBUG 静默降成 USER；
Wait/Assert 在异步发布前复查权限/模式，结果标记 domain=UI 与 dataCategory，取消传播到原生读取。
旧 wait/screen 仅等待 session.screenClass 并返回会话状态，不返回 UI 树，继续按 read scope 使用；
不能把这条状态等待接口当作语义 UI 条件或 DEBUG 树读取。
UI DEBUG mutation 复用 debug/arm、debug/mutations 和 bounded batch，当前仅支持
ui.widget.active.set、ui.widget.visible.set、ui.text.set、ui.scroll.set、ui.slider.set、ui.toast.reset、ui.toast.hide。
ui.scroll.set对AbstractSelectionList使用当前像素范围；对creative/loom/stonecutter/merchant.scrollbar.track使用0～1归一化位置，
无滚动范围时只允许0。Screen本地滚动同步原生行/可见物品，不选择配方或交易、不取得物品、不发送玩家输入或服务器包；单位不可混用。
滑块值为有限的 0–1 归一化数字；调用原生 setter，同步设置与文案，可能触发原生设置保存。
结果 sideEffects=native_slider_setting_callback；不承诺取消已应用的设置，调用者用新版本条件恢复。
请求要求 nodeId、expectedResourceVersion、typed value，可附 expectedValue；
debug/debug.write/debug.ui、OPERATE、deadline 与有效 Arm 在 owner-thread mutation 前校验。
UI Arm 明确传 uiContextId 与 namespaces=[ui]，不得混用 worldFingerprint，不向服务端 Peer 路由。
DEBUG tree 的 uiContextId/resourceVersion 为安全元数据，USER 不发布这些 Debug 授权依据。
结果为 CLIENT_UI、DEBUG_PRIVILEGED、inputDispatchCount=0；不直接执行按钮回调或替代玩家。
若原生文本 responder 替换界面，postMutationContextChanged=true，after/afterResourceVersion 为 null。
TestInstanceBlockEditScreen 的文本框不在 ui.text.set 目录内：原生 responder 可自动发送管理 QUERY，
因此以 UI_DEBUG_UNSUPPORTED 在本地文字修改及回调前拒绝，不通过 OPERATE 绕过玩家提交守卫。
这不改变其他普通文本框的原生 setter、独立权限、Arm 或版本前置条件。
ui.text.set也支持MultiLineEditBox、可编辑书本页/标题及普通/悬挂告示牌四行，使用各版本原生编辑器及validator。
4096字符是请求上界，不覆盖原生行宽/页长/标题限制。完整草稿绑定Resource Version，告示牌各行身份独立。
sideEffects=native_editor_draft_may_submit_on_close表示修改当前原生草稿、可能随原生关闭提交，应显式恢复；
恢复只保证声明的文字草稿；原生setter可能重建文字组件，不承诺隐藏格式、其他回调状态或服务器事务回滚。
不提供任意NBT/书本组件/文件写入或PLAYER输入。新增编辑器的五端实测状态独立记录，不以结构定义声称已验收。
ui.text.set也接受presentation.children中的hud.title/hud.subtitle/hud.actionbar，使用UI session Arm而非世界Arm。
目标版本绑定原生HUD/世界/Component身份与完整原生内容/文字，预检继续要求新鲜expectedResourceVersion，可附完整expectedValue。
这些字段仅调用本地原生纯文字消息setter，inputDispatchCount=0、CLIENT_UI/DEBUG_PRIVILEGED，不改变服务器/世界状态。
sideEffects=native_hud_plain_text_and_lifetime明确纯文字会替代富文本，原生setter可重置生命周期/颜色动画，不承诺格式或计时回滚。
自然计时不作为文字内容版本；隐藏/过期字段仅在DEBUG捕获，USER仍按实际呈现过滤，未运行或内容编码预算不足时拒绝。
Toast操作只接受当前manager内驻留的SystemToast/TutorialToast，不允许排队对象、任意类型或创建通知。
ui.toast.reset要求value={title:string,message:string}，两段各最多4096字符，显式重置SystemToast的两段纯文字；
sideEffects=native_toast_plain_text_reflow_and_lifetime，不承诺恢复格式/计时。expectedValue可比较完整原生两段解析文字，不接受截断前缀冒充相等。
TutorialToast在debugProgressable=true时接受ui.slider.set的有限0～1显示进度，sideEffects=native_tutorial_display_progress_only，不推进教学事实。
ui.toast.hide只接受value=true，可附expectedValue比较debugHideRequested；sideEffects=native_toast_hide_requested_not_reversible，不能反向显示或复活通知。
Forge 1.20.1的SystemToast没有forceHide，返回UI_DEBUG_UNSUPPORTED；该端TutorialToast及其他四端这两类的原生隐藏可用。
DEBUG节点的debugToastValue、debugProgress/debugProgressable、debugHideRequested、debugToastGuardAvailable与resourceVersion不进入USER投影。
版本绑定manager/驻留实例/Toast身份、完整格式化内容、进度/隐藏及Screen/Overlay/GUI尺寸；不绑定自然动画时钟，旧下标不能覆盖替换后的对象。
完整编码超预算拒绝UI_CONTENT_GUARD_UNAVAILABLE；隐藏后的after可能因对象已离开而为空，属于已经请求原生隐藏，不是未执行。
26.x的ServerboundCustomClickActionPacket不是玩家命令：Dialog/富文本的未知服务器回调统一按
CUSTOM_UI_CALLBACK、未决效果在原生最终发送前拒绝（PLAYER_COMMAND_UNCLASSIFIED），不解释id或payload、
不因minecraft命名空间就信任其服务器效果。这个拒绝不替代普通run_command链接已有的命令分类。
presentation为USER提供有界只读HUD/Toast内容；过滤隐藏项与queuedToastCount，DEBUG另提供批准的本地typed字段与受限内部状态。
标题/副标题/动作栏、物品热栏/副手、Boss 栏、侧边计分板、状态效果图标和 System/Tutorial/Advancement Toast 已接入。
presentation.children 上限为 471；热栏最多 10 项、Boss 栏最多 32 项且遵循原版视口上限，
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
生命、护甲、食物、氧气与坐骑生命按实际原生绘制顺序采样，最多256个图标，保留叠加/闪烁层而不推断精确生命或食物数值。
iconSource=last_completed_native_hud_draw，resourceId为实际原生纹理身份，entryIndex为本帧顺序；
valueSemantics=ordered_native_glyph_composition_not_raw_statistics。样本绑定世界、摄像主体、Screen及GUI尺寸，250ms过期。
hudIconSampleStatus区分CURRENT、UNOBSERVED、STALE、CONTEXT_CHANGED、BUDGET_EXCEEDED与OCCLUDED；
不完整、超预算或被普通Screen/Overlay遮挡的样本不进入USER。此为原生绘制参数观察，不保证第三方最终像素遮挡。
Hook manifest单独公开passiveDrawDelegationCount=5，同时计入replacementInjectionCount；它不是玩家输入许可。
这些只读委托先原样调用原生绘制一次，再记录参数；逐Target字节码测试约束调用次数、参数顺序及观察前无分支。
命令效果分类将function/schedule、clone/place/loot及item的from/modify保守拒绝为PLAYER_COMMAND_UNCLASSIFIED；
不读取服务器模板、战利品表、修饰器或源物品来推测影响。data modify的from/string来源同样拒绝。
这是有限首版安全限制，不是原版缺席；普通字面物品/方块设置仍要求实际玩家权限、模式、scope、Lease及操作许可。
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

正式路径及HTTP method的唯一接口目录是OpenAPI；当前验证输入逐项对照其78个operationId和x-agent-mode，
并核对24个实际MCP listTools结果及typed discriminator，不按HTTP POST或Tool名字粗分读取/修改。

| 表面/typed分支 | 模式/类别 | 独立授权及证据边界 |
| --- | --- | --- |
| Session/能力、readiness/trace/Hook/audit、操作描述、Peer状态/协商 | READ compatible；控制面元数据 | 各自read/diagnostics/Peer门禁；查询不授予模式、玩家或服务器权限，POST协商不等于世界mutation |
| task begin/heartbeat/end、显式mode/Lease管理、Arm管理、取消 | 任务为READ compatible；其余按MODE_INDEPENDENT声明 | read/control/Debug等实际scope及主体约束仍在；结束/取消可清理输入但不读取诊断载荷或伪造用户重新同意 |
| UI tree/resolve与screen/ui.exists/menu/inventory条件 | UI；READ两类、TAKEOVER USER、OPERATE DEBUG | USER仅实际呈现；DEBUG另需debug/debug.ui.read；条件含read及ui，授权/异步发布沿用相同投影，不能凭节点ID取隐藏数据 |
| 客户端模型、服务器/内部世界、Deep Observation、Provider/Frame及相关条件 | WORLD；按实际USER/DEBUG能力 | 客户端有界USER不加载区块；内部/服务器、第三方Provider及保留实验读取只接受DEBUG和独立世界读取scope，不回退LIVE/PERSISTED |
| UI action、键鼠/文本/输入pipeline、世界语义gesture、玩家命令 | TAKEOVER USER输入路径 | 原Control Lease、input/command/额外步骤scope和原生验证；诊断命令仍可污染为DEBUG_PRIVILEGED，不以输入机制洗白effects |
| Fixture、UI/世界typed Debug和batch、诊断事件压力发布 | OPERATE；各自FIXTURE/DEBUG操作 | Fixture自己的scope，Debug另需Arm/namespace/版本/值/deadline；不获得玩家Lease；批次逐项结果而非多资源事务 |
| Capture、Recording/Artifact及事件流 | READ compatible，显式记录/读取类别与资源权威 | capture/event/类别及独立UI诊断读取门禁；像素截图不是内部状态或第三方渲染收敛oracle；不混读诊断录制或丢失污染标记 |
| Persistent Read | READ compatible，PERSISTED/last_saved_state | 独立storage.read与实际存储上下文，不借LIVE类别提升权限，不自动加载/写入；当前无正式Persistent Write |

全部12种条件按实际生产授权路径分类：UI为screen/ui.exists/menu/inventory；WORLD为player/block/entity/scene/provider/event/recording，
event另需event scope；operation按结果的诊断领域复验，无诊断结果时仅为生命周期/普通结果。嵌入pipeline仍需整个TAKEOVER输入计划的权限，不把独立Wait/Assert升级成输入。
V1/V2 Provider的声明不授予USER能力；只有实际客户端投影和capture info支持USER，server/内部与扩展的默认查询不因此可见。
保留实验诊断的Target列表按注册Adapter及共享Transport的实际接口分支检查，不把阶段曾经通过或旧Target名称当当前可用性。
Peer接收端的委托/撤权生命周期属于M-01/01c和M-09，操作分类完成不等于远程主体安全审查已完成。

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

world.action 使用 USER 发现的 targetRef，支持 aim/attack/use、持续 mine/use_hold 和指定面的 place。
方块 targetRef 在同一客户端 Chunk/位置上追踪原生变更，保留 A-B-A 拒绝；其他位置变化不直接使该目标失效。
最多 4096 条弱引用变更历史，历史淘汰仍以 WORLD_TARGET_STALE 拒绝；世界/玩家/相机、Chunk 实例和当前 BlockState 继续复验。
全量区块包即使复用同一 Chunk 且状态相同，也在原生解码前作为整块变更使旧 targetRef 失效；解码中断不恢复旧引用。
整块与位置变更共用预算，未改变 targetRef TTL、权限或原版命中条件。
未指定面时方块瞄准使用原生 OUTLINE 盒体及面中心候选（最多 64 项），空形状/遮挡无法选取时拒绝；
这是目标选择改进，不增加权限或物品 ID 白名单，最终原生 pick 仍须命中。显式 place.face/hit 合同不变。
持续动作 holdMs 为 50..10000 ms，place.face 为六面名，hit.u/v 为面内 0.05..0.95，
可选 expectedDestinationBlockId 在原版目的格校验。动作在既有串行队列与 TAKEOVER/Lease 中执行，
每次原版交互复验目标和上下文，不直接调用世界修改或改相机朝向。
clientBreakPredicted 只代表客户端预测；effectConfirmed=false 要求独立观察。
mine 的 nativeBreakChanges/breakProvenance 描述有界同步原版破坏来源，包括流体替代与邻居更新；
不是任意忽略 Chunk revision。use_hold.hand 可为 main_hand（默认）或 off_hand；
副手要求主手为空，否则 WORLD_USE_HAND_CONFLICT；无持续使用能力返回 WORLD_USE_ITEM_REQUIRED。
实际手不符会拒绝并释放，不绕过原版优先级。USER 玩家 usingHand 为 none/main_hand/off_hand，
riding 为客户端乘坐状态，不公开服务器内部关系。
取消的合同是释放输入，不回滚；弓松键等原版效果可能发生。精确预算与字段见 OpenAPI 的 PipelineStep。

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
