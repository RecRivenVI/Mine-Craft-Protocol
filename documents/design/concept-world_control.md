# 世界领域控制与首个里程碑

世界领域按 USER/DEBUG 区分类别，模式围绕是否接管真人玩家输入；共享读取/修改准入与首批非接管原生命令已迁移，完整目录与部署仍待闭合。
世界 USER 已采用有界客户端世界模型；读取消费链保持类别/权限隔离。命令边界按批准目录分类并拒绝未知效果，完整语义玩家操作目录与世界里程碑尚未完成。
已实现接口以 Native/OpenAPI 为准；本文的未实现部分不冒充产品能力或自动授权其他阶段。
交付状态只由[状态页](../project/status-milestones.md)负责，推进步骤见[执行计划](../project/plan-execution.md)；UI 与世界联合推进，但分别交付验收。
已有生产路径的调查保留在[研究记录](../research/investigation-world_control.md)，不改写历史结论。

## 交付边界

首个世界里程碑交付：Agent 无需截图识别或手写世界坐标，即可读取有界客户端世界信息，
通过语义目标执行原版玩家操作，并用独立授权的内部读取与强类型修改完成测试准备、诊断和恢复。
它不是自动寻路、生存策略或任意游戏对象编辑器。

| 模式 | 类别 | 世界领域职责 |
| --- | --- | --- |
| READ | USER | 只读有界客户端世界模型与标明时效的既有观察，不代替玩家探索 |
| READ | DEBUG | 独立授权读取有界内部状态，明确客户端、服务器和加载限制 |
| OPERATE | USER/DEBUG 读、USER 原生命令、DEBUG 修改 | 与真人并行的非接管原生操作及独立授权诊断/Fixture，不取得共享输入 Lease |
| TAKEOVER | USER/DEBUG 读；按路径分别记 USER/DEBUG 操作 | 当前玩家的真实输入必须持有既有 Lease，DEBUG 回读/修改仍需自身授权 |

模式、数据类别、数据权威、证据来源是四个不同维度；不新增领域 Mode 或第二套 Lease。
TAKEOVER 不获得 Runtime Debug 权限，OPERATE 不获得共享键鼠/焦点输入所有权；以真实玩家身份提交非接管原生命令不等于接管键鼠。
目标中真人 Esc 对两领域一起结束 TAKEOVER 和玩家输入，保留重新同意 latch，并将用户决定持续告知模型。
观察、独立 DEBUG 及非接管原生命令不因这次 Esc 被取消；模型决定继续观察、停止独立工作或显式继续 OPERATE。
合法 OPERATE 不清除 latch，也不能借 GUI/键鼠代替真人玩家控制。

世界范围以 LIVE 为准。Persistent Read 继续是独立的 PERSISTED/last_saved_state，
不补齐看不见的 LIVE 信息；Persistent Write 不进入里程碑。正常游戏保存并不等于调用 Persistent Write。
既有 UI 用于打开容器、聊天、死亡重生等桥接；UI 与世界各自验收，当前联合推进安排以执行计划为准，
不由本设计自动开启统一人工验收。

## USER 有界客户端世界模型

### 权威与字段边界

USER 世界读取不再要求数据出现在最终画面上，以当前玩家的客户端已加载/已接收世界为边界。
CLIENT_KNOWN 不等于服务器真值或人眼可见；墙后、地下、背后的缓存方块或被追踪实体可以返回，
这不是防透视、公平游戏或反作弊保证。UI USER 仍只读原版实际呈现，不随世界合同放宽。

- 玩家：当前客户端位置、朝向、生命、最大生命、吸收、食物、空气、选中热栏、维度及 gameMode/onGround/flying、usingItem/useTicks/usingHand/riding；不要求 HUD/F3 展开，不可得状态明确unknown。
- 方块：范围内已缓存 Chunk 的 ID、BlockState 和整数位置；使用非加载查询，不读取方块实体或容器内容。
- 实体：客户端追踪对象的类型、名称、位置、朝向、隐身、着地、生物 hurtAnimation/baby，以及适用对象的驯服、坐姿和牵引反馈；不开放 UUID、生命、AI、库存或 NBT。
- 场景：附近缓存方块立方体与实体球形查询；原生准星轮廓只是可选 aimPresentation 提示，不支配整个模型可用性。
- 服务器、Peer、完整 Deep/内部 Provider、Storage、计划刻及其他内部域不进入 USER；独立 DEBUG/PERSISTED 合同不变。

读取不自动移动玩家、转头、加载/生成区块、打开容器、执行命令或读取磁盘。
Screen、HUD/F3 显隐、相机方向、第三人称与窗口最小化不再决定缓存模型的存在；
离开世界后 available=false，不从服务器或旧样本补齐。

### 范围、精度和预算

实现常量权威在 WorldClientModel，精确定义同步 Native OpenAPI/capabilities。
单块查询和实体读取限定玩家附近 32 格；场景半径默认 4、最大 8，方块区域为玩家整数格中心的立方体。
场景 blockLimit 默认 256、最大 512，实体 limit/entityLimit 默认 64、最大 128；
方块由玩家所在格向外按立方壳层稳定遍历，预算截断不会只留下查询立方体底层。
实体默认半径 16，场景实体球半径与场景 radius 一致。只读当前维度，不接受任意锚点或自动分页。

一次场景最多尝试 4096 个方块，实体最多扫描 2048 个缓存候选，循环预算 25 ms，响应最多 512 KiB。
输入必须为有限数值，整数预算/方块坐标不接受小数、字符串或溢出；无效查询在调度读取前拒绝。
有界循环可能提前停止，分别报告扫描数、不可用采样数与截断；字符串最多 512 字符并标记截断。
达到上限按可能不完整报告，不把局部或空列表宣称为完整世界或不存在目标。

输出 authority=CLIENT_KNOWN、source=bounded_client_world_model、dataSource=LIVE、
visibility=not_evaluated、perceptionFiltered=false、storageAccessed=false、chunkLoadRequested=false。
坐标保留客户端原始数值精度，方块为整数格；不是服务器最新状态、显示精度或无误差运动预测。
sampledAtMillis 记录捕获时间；每次读取在客户端 owner thread 重新采样，不要求渲染帧可见性证明。
出范围为 outside_user_radius，范围内未缓存 Chunk 为 chunk_not_loaded，无客户端世界为 client_world_unavailable。
USER 模型通过下述短期 targetRef 连接语义输入；读取本身不执行动作，也不保证目标可达。

### 消费者与历史

Native、MCP、Provider、State Frame、Wait/Assert、Event resync 与 Recording stateReads 使用同一个入口。
默认 USER；DEBUG 仍独立要求 read/debug/debug.world.read，未知/服务器/内部来源在调用前拒绝。
USER 玩家条件可以断言已开放的坐标与生命，方块/实体条件受同一查询预算与字段白名单约束；
实体结果被截断时，否定存在性条件返回 WORLD_USER_QUERY_INCOMPLETE，不用不完整候选证明不存在。
UUID 等内部条件不能变成存在性旁路。普通事件仍采用独立白名单，放宽快照不自动开放所有事件。
Frame/Recording 不混合类别；当前异步发布复验权限与模式 generation，取消沿既有请求链传播。
目标保留内容/权限隔离，但不因模式转换取消仍合法的 DEBUG 回读；输入世代与读取/DEBUG 的授权及资源生命周期分别验证。

USER 语义版本只依据允许发布的模型；完整内部 Resource Version 仍用于 DEBUG 写前条件。
历史未分类/DEBUG 数据不能追认为 USER；既有 last_observed 必须标时效，不在后台刷新成当前状态。
声音和字幕仍只按实际呈现/可听来源，不把网络 sound event 自动解释成听到了声音；正向声音测试需单独授权。

## 语义操作与安全前置条件

正常路径为：当前客户端模型 → 语义目标/动作 → 原版相对输入瞄准 → 临执行复验 → 原版输入 → 等待真实反馈。
Target adapter 使用实际键位和原生输入路径，不假定 W、数字键、鼠标键或修饰键永远是默认绑定。
原始 GAME_ROUTED 输入仍可像真人盲走、盲挥或转头；缺少有效当前目标时拒绝语义自动命中，
不借 DEBUG 定位，不替 Agent 搜路，不提供成功保证。

targetRef 内部绑定 session、世界/维度、玩家/摄像机、对象生命周期和感知上下文；不能跨重连、换世界、
换维度或同坐标替换复用。输入前复验 mode/Lease、deadline、物品与玩家状态、原版距离/命中和原版实际命中/可交互性；缓存知道墙后目标不允许隔墙操作。
过期、未知或不再可交互目标统一拒绝，不借读取能力绕过原版输入命中规则；只设置短 TTL 不能替代复验。
持续挖掘要区分自己造成的进度/完成与无关替换；使用、蓄力、拖动和取消都受有界 gesture ownership 管理。

UI 和世界共用输入 admission 与取消：Screen replacement、缩放/尺寸变化、维度变化、Lease loss、
manual revoke、disconnect/shutdown 先停止并清理输入，不能等 Fade，也不能留下 late event。
模式切向 OPERATE 时先结束玩家 gesture；允许其后与真人并行的合法 DEBUG，不因真人活跃一概拒绝。
同一资源仍在 owner thread 串行提交，遵守显式 revision/值条件，权限较大也不等于自动重试或无条件覆盖。
既有未知输入来源 fail closed、一次性来源票据、宿主光标自由和 Operator UI 排除继续成立。

世界对象 token 与 Menu/Slot token 不混用；打开箱子、骑乘菜单等后续交互转交既有 UI 合同。
typed inventory mutation 必须授权实际世界资源，不能用 uiContextId/客户端 UI Arm 改世界库存。
跨端快照报告一致性级别，不承诺客户端、菜单和服务器恰好同 tick。

客户端基础 player/block/entities 与 scene 复用普通 read 获取有界 USER 模型；服务器与内部来源明确 DEBUG。
读取消费者隔离与首批 world.action 已接入，持续补充代表场景验收；不意味着命令效果或完整里程碑已完成。
capabilities 保持 readConsumerIsolationComplete=true、consumerIsolationComplete=false、milestoneComplete=false；
pendingClassification 仍包含 player_command_effects。权限、Lease、Arm、人工撤销与 Evidence 不变。

### 首批语义目标与动作

USER 单块、场景方块与实体摘要可附带 targetRef/targetTtlMs。非空气缓存方块和可被原版选中的客户端实体
使用独立 world: 引用，最长 15 秒、每 Runtime 最多 1024 项；引用不是 scope、Lease 或命中保证。
未加载、空气、自身、死亡及不可选实体不提供可执行目标。范围预算、截断与 USER 字段白名单不变。

现有 /v0/pipelines 的 world.action 支持 aim、attack、use、mine、use_hold、place，目标必须来自上述读取。
MCP 使用既有 minecraft_run_input_pipeline；不增加自动 acquire、自动选物品、路径规划或 DEBUG 回退。
动作与所有玩家输入共用串行队列、Operation、deadline、取消和清理，仍需 input scope、TAKEOVER 与 Lease。

每次校正复验世界、玩家、摄像机、目标生命周期、选中物品及当前视点/距离，再通过既有mouse.delta调用原版相机处理。
正常位移不被当作玩家身份变化或锁定到手势开始坐标；移动期间重新计算当前视点、目标几何与原版pick，命中不符时不按下，不自动走近或寻路。
按下前同一客户端线程内重新原版 pick，再按实际 attack/use 键位执行短按；不直接修改朝向或 world state。
无 Screen/Overlay、第一人称且存活才允许执行；旁观不被整体拒绝，交互效果仍由原版处理。电影相机与不支持的键位明确拒绝。
未指定放置面的方块动作从原生交互轮廓分解出的实际盒体取中心及面中心候选，最多 64 项，
通过客户端原生 OUTLINE 射线选取无遮挡的近点，不按方块或 Mod ID 建表；空轮廓或无可用候选明确拒绝。
这不是完整表面搜索，也不替代按下前的原生 pick、实体遮挡或距离校验；复杂形状仍可能没有候选。
指定面的 place 继续使用原合同的面内 u/v，不因通用瞄准改成任意表面或绕过 BlockItem 规则。
普通方块use可显式指定同样的面与u/v，投影到真实轮廓以区分书架等小格；只适用于方块，不接受放置目的格条件，也不读取/改写内部容器。

方块引用绑定缓存 Chunk 对象、BlockState 与位置级客户端变更历史；同 Chunk 其他位置更新不直接使它失效，
同坐标 A→B→A 仍拒绝旧引用。共享历史最多保留 4096 次已观察 Chunk 的原生更新，只弱引用 Chunk，
历史淘汰、序号耗尽或 Chunk 卸载/实例替换时 fail closed，重新读取取得新引用；不是延长 TTL 或放弃原版命中复验。
原版同步挖掘的完成证明仍另保留整 Chunk 变化计数及有界连锁记录，不把位置级引用当作忽略破坏副作用的许可。
全量区块包在原生 replaceWithPacketData 的 HEAD 单独记为整 Chunk 变更，先使旧引用失效再解码，
即使复用同一 Chunk 对象且状态相同也不能沿用旧引用；读取中断不回滚失效。整块与位置变更共用 4096 项历史预算。
验证探针只在专用运行中重发已加载 Chunk 的原生包，不修改世界或直接调用守卫，不进入产品 JAR。
各端实际证据与其他未覆盖生命周期仍以执行计划为准，这不是完整世界里程碑声明。
实体绑定具体客户端对象，移除或同 ID 替换失效；
移动实体重新计算当前包围盒中心。世界/玩家/相机引用为弱引用，失效不会继续持有旧世界。

mine 使用真实 attack 键持续挖掘，默认 5000 ms；use_hold 使用真实 use 键蓄力，默认 1000 ms。
holdMs 只用于这两类动作，范围 50..10000 ms；同一 gesture 在每次原版交互调用与轮询中复验上下文。
挖掘观察原版 destroyBlock 的客户端预测更新，区分自身完成与外部 Chunk 替换；超时明确失败并清理。
完成判定跟踪本次同步原版 destroyBlock 调用，目标必须从原状态变成破坏前流体对应的原版状态；
含水方块不再要求变为空气。调用内邻居/多格变化计入有界来源跟踪，最多 256 次；
目标重复变更、未记录的 Chunk 修改、对象替换或预算超限均拒绝，不把任意多次修改当作自身完成。
调用外的目标位置更新仍使引用失效；完成只代表客户端预测，不等于之后世界状态或服务器确认。
use_hold 允许有界 32 格客户端原版射线瞄准，不改原版交互距离，不实现抛物线预判或自动追踪。
use_hold.hand 为 main_hand（默认）或 off_hand，选中手必须具有原版使用时长；
副手要求主手为空，冲突时按下前返回 WORLD_USE_HAND_CONFLICT，不绕过原版主副手优先级。
动作仍只发送普通 use 键，由原版选择实际手；观察到另一只手使用会拒绝并释放。
不自动换物品/换手，也不保证原版交互方块不会先消费 use；完整物品矩阵尚未覆盖。
自然使用结束可以提前释放；按时松键后等待原版结束使用。取消只承诺释放输入，不回滚效果；弓松键可能射出箭。

place 仅支持主手 BlockItem，必须指定 up/down/north/south/east/west 表面。
可选 hit.u/v 范围 0.05..0.95，默认中心；上下表面 u=x/v=z，南北 u=x/v=y，东西 u=z/v=y。
重新原版 pick 必须命中指定面，落点距请求位置不超过 0.025 格；不通过改输入坐标补偿。
BlockPlaceContext 确定实际目的格和可替换性，expectedDestinationBlockId 可选值前置条件在按下和原版调用前复验。
一次 place 最多允许一个原版 useItemOn，不自研放置规则、不自动潜行或绕过交互方块；实际物品规则仍由原版执行。

玩家模型提供客户端 usingItem/useTicks/usingHand/riding，生物实体摘要可提供 hurtAnimation；
这些不是服务器内部关系、实体生命或伤害数值。喂食可由实际 HUD 物品减少独立观察，骑乘由客户端乘坐状态观察；
实体额外字段只投影客户端状态：baby 为生物幼体/体型标志，不是年龄数值；
tamed/sitting 仅用于 TamableAnimal 的同步驯服标志/坐姿，不代表所有可驯服物种或服务器 AI 指令。
leashed 为客户端牵引状态，leashHolderKnown 表示持有者在当前客户端缓存中可解析；
仅已知持有者才返回 leashHeldByPlayer，不暴露持有者身份或坐标。未知不能解释为属于其他人。
不读取 owner UUID、inLove、繁殖冷却、内部交易或服务器关系。
驯服、喂食繁殖和牵引仍使用普通 use，不强制成功；随机驯服必须有尝试次数/时间预算，
繁殖通过新出现的幼体独立确认，不能用消费食物直接证明已繁殖。
与村民 use 打开菜单后，选报价、支付和收货转入既有语义 UI/Slot 合同；不直接调用交易业务函数。
这些不等于所有动物、载具或交易分支全覆盖。当前不涵盖行走到目标、全部物品使用语义或完整世界操作目录。
结果区分原版命中、输入分发与效果确认；effectConfirmed=false 要求 Agent 另用观察、Wait/Assert 判断真实效果。
DEBUG Arrange 仍为 DEBUG_PRIVILEGED，GAME_ROUTED Act 的路径为 PLAYTEST，不会洗掉前置状态的污染记录。

## 玩家命令分类

### 路由、实际权限与效果分开

目标中所有命令操作统一归 USER，包括需要作弊或管理员权限的命令，不再逐条定义哪些属于 DEBUG。
目标提供两条客户端 USER 路径：TAKEOVER + Lease 的真实聊天输入，以及 OPERATE 下不占用共享输入的原生命令提交；
二者都绑定同一实际玩家/连接，不将其提升为 console。独立服务器控制台经 OPERATE + 操作员授权提交，不冒充玩家。
不因命令权限较高自动追加 typed DEBUG scope/Arm；原生命令授权、原版服务器权限与专用控制平面限制继续独立生效。
USER 不代表无副作用、纯 gameplay 或必然执行成功；typed API/内部读取仍按自己的 DEBUG 合同，不按游戏模式另造权限等级。
主体不得换成伪造 console/高等级 source；服务端自身控制台使用真实 server_console 独立授权。

原生命令发现读取当前主体已收到或被授权查询的命令树/补全，沿别名、redirect 和实际参数上下文判断。
树中存在只证明可见候选，不证明完整参数可用或服务器执行成功；不靠逐条命令白名单或有无 OP 猜测完整可用范围。
服务器可能改写语义或撤权，未知/过期/第三方规则明确报告，不后台执行查询命令当只读探针，不读取服务器内部对象补 USER 数据。
当前生产路径已接入本地/显式服务器命令发现与有界原生批次；当前五端验证及真人协作缺口见状态页，不能用旧 M-07 证明完整可用性。
下面的效果解析/保守拒绝是当前实现事实，不是新增按命令划分 DEBUG 的计划；安全限制须明确标为独立策略，不能用未知效果推导类别或虚构执行确认。

### 优先实施的 OPERATE 原生命令

沿当前 Target 合法的原生命令提交 API、签名/网络和服务器验证，以当前真实玩家提交，不打开/关闭聊天框，
不更改用户草稿/选择区/焦点，不模拟按键、清除真人 held input、申请 Lease 或临时切换 TAKEOVER。
command scope、实例/玩家/游戏连接、原生可用性、队列/期限及实际提交前的授权复验仍生效；不借 typed DEBUG 权限提升玩家权限。
没有合法玩家连接时明确不可用；单端客户端不因为玩家OP就直接调用远端内部dispatcher或服务器typed API。
原生提交上下文与现有输入手势许可分开校验，不删除现有方法/发包守卫，也不接受自报来源或任意原始packet。

每项操作标明实际路由、主体、USER类别、提交/效果状态和是否依赖接管；客户端/服务器不混用许可。
独立原生命令不因物理Esc或输入Lease结束被连带取消，聊天键入手势仍取消；操作自身取消、权限失效、连接变化与Runtime关闭仍须清理。
反馈遵循USER实际可发布边界，不偷读隐藏聊天或服务器日志补回执；可能已提交的命令不能自动重试。
五端/Native/MCP/网页均须验证真人聊天草稿和输入未被借用、正常权限拒绝、取消与无迟到提交；不能用删除TAKEOVER检查或开发类路径代替证明。
其他非接管世界/物品/容器动作先完成输入/上下文依赖审查和原生校验证明，未证明前仍走已有TAKEOVER路径，不自动新增完整OPERATE玩家动作目录。

### 当前原生提交与回执

既有命令接口按typed action区分execute/discover/batch，默认execute兼容单条调用。
discover默认只读当前主体的原生树和本地补全，不执行命令，不复用/取消真人聊天的单一服务器补全future；
本地结果以serverCompletionRequired表达服务器补全需求，completionScope=client_local_only，不把缺失服务器候选当作完整结果。
显式completionSource=SERVER通过独立事务返回当前玩家服务器候选；每连接单飞/250ms间隔/1500ms期限，真人待完成补全优先。
身份、世界、连接、命令树及授权在发送和发布前复验，超时或取消不发布结果；已提交查询不可撤回，原版预算仍共享。
不借用控制台、Debug、输入Lease或未选中的玩家主体，服务器候选也不证明授权、执行或全局完整。
batch在OPERATE/TAKEOVER按序执行1～32条NATIVE提交，每条复验当前玩家/世界/连接/命令树与授权，首次失败停止剩余项。
逐项commandIndex/commandRequestId只关联本次提交，不冒充服务器执行回执；非原子、无回滚、不自动重试。
取消或deadline后可能已有部分提交，错误标明executionOutcome=not_confirmed、retrySafe=false；应独立观察后再决策。

当前玩家命令支持 CHAT/NATIVE，AUTO默认在OPERATE选NATIVE，其余模式选CHAT；命令统一USER，不自动切模式或提升实际玩家权限。
CHAT走原生聊天/文本/Enter与正常网络验证，要求command、相关输入权限、TAKEOVER和Lease；世界/连接和草稿全程绑定单一取消手势。
NATIVE要求OPERATE或TAKEOVER及独立command scope，使用实际玩家原生命令API，不打开/关闭Screen、不更改聊天草稿/焦点/held input，不获取输入Lease。返回NORMAL_NETWORK来源和未确认提交，不采样聊天或冒充键鼠操作。
CHAT遇到现有Screen/草稿、原生输入长度超限和未知绑定时在输入前拒绝；没有直接发包回退。NATIVE保留现有Screen/草稿，仍限制原生单命令长度。
它返回 accepted/serverValidation，不等于服务器已执行成功；响应明确
submissionStatus=submitted_unconfirmed、effectConfirmed=false、serverValidationObserved=false、gameplayAcceptanceEligible=false。
原生适配器有界解析当前玩家实际收到的权限过滤命令树，沿 Brigadier 重定向和子上下文识别真实根与终端效果；
效果为 COMMUNICATION、INFORMATION 或 STATE_MUTATION，classificationSource=native_player_command_tree。
未知、未决函数/调度和宿主管理命令在发送前拒绝；这是原版语义分类，不证明第三方服务器没有改写同名命令。
原生聊天 GAME_ROUTED_RAW 仅在状态修改时标记污染；不把输入来源当作效果成功或干净验收结论。
状态修改的 mode=DEBUG_PRIVILEGED 是证据来源，不是切换 Presence Mode、授予 OP 或 Debug Arm。
方法入口和最终命令数据包边界均检查来源、当前玩家连接及独立scope。
CHAT另检查Lease、模式世代、Operation与输入许可；NATIVE检查独立工作许可/取消/期限，限定同一条原生命令与最多一次提交，不借输入许可。
聊天Enter和文本链接共用输入守卫，不在Vanilla崩溃包装回调内抛出协议异常，返回输入引擎后才报告拒绝。
每项输入Operation最多16个回执；取消、结束、嵌套回调和线程切换不能借用另一Operation的输入许可。

#### 命令反馈的最小关联方案

CHAT反馈采用有界时间关联，不伪造服务器请求ID或执行确认；NATIVE不进行聊天采样。它是现有聊天helper的能力，
不是新批量命令逐条成功证明。
原生Enter之前，在同一客户端线程记录当前聊天消息对象的基线；其后最多观察500ms，
只返回本窗口新进入客户端聊天缓冲的消息，最多16条、每条512个Unicode码点。
不读取服务器日志、不执行服务器命令、不返回基线历史；聊天隐藏时不读取隐藏内容。
返回commandFeedback，association=temporal_not_guaranteed、windowMillis/observedMillis、complete、truncated和messages。
消息是非可信游戏文本，不是Agent指令；它可能含其他玩家/系统的无关反馈，或遗漏延迟、关闭反馈及超预算消息。
空反馈、complete和错误色文字都不推导成功/失败，effectConfirmed/serverValidationObserved仍为false。

观察与同一输入gesture、世界/连接及Lease绑定，继续占有原来的串行输入队列。
取消、人工Esc、失去Lease或deadline到达即停止观察并清理；已提交命令不能回滚，
取消错误仍按未确认效果处理，不自动重试。命令提交后的世界/玩家/连接身份变化则立即结束采样，
complete=true、terminationReason=client_context_changed，observedMillis可以小于500；不读取替换上下文，
不把普通跨维度命令误报成未提交，也不覆盖独立发生的撤权/取消错误。后续输入不能借用该窗口，不新增长期聊天订阅。
普通键盘、富文本等非helper提交继续仅有来源回执，不假称已有同等关联。
实现验收包括旧聊天不混入、重复文本区分、窗口/长度上限、取消/连接变化，以及真实普通命令的成功文本和服务器错误文本。
本方案已接入五端并通过当前候选的Native/MCP相关回归；绑定范围见[当前状态](../project/status-milestones.md)。
这不构成任意命令执行确认，也不证明第三方服务器、所有消息或完整世界里程碑已验收。
发包前先保留活动录制来源，发送异常或调用方取消不能据此反推“没有效果/证据干净”。
键/鼠标 release 与后续步骤不得抹去之前的命令来源；输入前拒绝和排队取消不生成提交事件。
命令方块与命令方块矿车配置已接入原生最终发包守卫；配置载荷复用当前玩家树、scope、TAKEOVER、Lease 和输入许可。
配置查询与清空仍改变配置，回执独立记录 configurationKind/configuredCommandEffect，始终保留诊断污染与未执行确认。
不因配置的命令只读就把保存动作计为干净玩法，也不把 Helper 的命令许可借给配置操作。
Jigsaw 界面配置已进入同一原生提交守卫，BLOCK_STATE 与 COMMAND 载荷明确区分；
最终方块状态中的命令继续递归分类，普通方块状态仍是诊断配置，不是已执行模板生成。
Generate 原生回调先保存配置后请求生成；未决生成拒绝但不撤销已提交配置，失败来源与 Artifact 保留该事实。

管理型 UI 的原生提交目录如下，不以通用 Widget 可点击推定效果分类完成：

| 原生表面/提交 | 当前边界 | 剩余项 |
| --- | --- | --- |
| 命令方块/矿车配置 | 命令程序及清空已守卫，配置始终诊断 | 完整上下文/间接程序目录 |
| Jigsaw 配置/生成 | final_state 已守卫，模板生成未决时拒绝，保留前序配置来源 | 首个里程碑可按已知限制验证拒绝；生成包为零不等于前序配置零修改，完整模板解析不再必需 |
| Structure UPDATE_DATA/SCAN_AREA | 已接最终提交守卫，原生参数更新/扫描保留独立诊断来源；扫描不是 READ 查询 | 五端实际存值/区域变化、权限、恢复与消费者证据 |
| Structure SAVE_AREA/LOAD_AREA | 文件保存与未决模板加载在发送前拒绝，避免原生 handler 先更新参数 | 零包/零参数变化及已知限制可关闭首个里程碑此分支，不称支持保存/加载 |
| Test Block/Test Instance | 26.1.2/26.3 的配置及全部动作包接入发送前管理拒绝；UI Debug文本框在回调前拒绝；1.x原生无对应类 | 验证零相关服务器包/存值变化及Native/MCP拒绝；不要求本里程碑实现测试程序执行，也不宣称客户端草稿自动回滚 |
| Dialog/富文本回调 | 原生命令提交共用守卫；26.x非命令custom action按CUSTOM_UI_CALLBACK在最终发送前拒绝，不解释未知服务器效果 | 新拒绝路径仍须实际原生点击和零服务端回调验证，不由命令链接成功推定 |

未接守卫或未验证的分支仍是M-07缺口，不以NATIVE_ABSENT、隐藏按钮或坐标兜底掩盖。
按2026-10-16用户决定，不透明管理、函数/调度和来源不明数据复制可以“已验证拒绝+零相关服务器副作用+明确限制”收口；
这是首个里程碑的限制关闭，不是该高风险功能已实现，不将合法前序配置或已执行效果虚报为零修改。
已知语义只由当前原生数据与独立授权事实得出，不隐式读取服务器内部对象或磁盘补齐。
嵌入程序按原生解析的 Tag、BlockInput、ItemInput 和 NbtPath 处理，不从原始 SNBT 子串猜测；
Command 与 Jigsaw final_state 使用同一当前玩家树/注册表有界递归分类，普通名称与惰性 custom_data 不视为命令。
未知的服务器来源复制值不隐式读取或猜测，明确拒绝；预算和当前边界见[Native 合同](../reference/protocol-native_v0.md)。
这不是任意 NBT RPC，也不保证所有世界连锁或既有数据中的程序都已解析。
函数/调度、其余不透明载荷与管理型GUI须补齐拒绝边界和零副作用证据，不再要求首个里程碑实现完整效果解析；未验证前M-07仍未完成。

首个里程碑的保守拒绝目录为function/schedule、clone/place/loot、data modify的from/string来源、
item的from/modify，以及未知custom UI回调。客户端不隐式读取服务器模板、战利品表、修饰器或源物品来猜测效果；
这些返回PLAYER_COMMAND_UNCLASSIFIED，不是声称原生不可操作或已经支持其效果。已知字面物品/方块仍按原生参数有界解析。

命令统一 USER；下表只解释原生通道与实际效果，不是逐条类别/权限白名单。现有安全拒绝不因分类改变而自动开放：

| 命令类 | 原版例子 | 类别、路径与证据要求 |
| --- | --- | --- |
| 沟通/说明 | `/msg`、`/help` | 原版普通主体可达时可为 USER；跨玩家通信也需测试资产授权，具体 Target/主体仍须验证 |
| 需要额外权限的信息揭示 | `/seed`、`/locate`、`/time query`、`/data get` | USER命令，不能在READ隐式执行；只取实际反馈，不扩成内部对象，原版权限照常检查 |
| 修改/广播 | `/give`、`/tp`、`/setblock`、`/fill`、`/gamemode`、`/summon`、`/effect`、`/say` | USER命令，正常原生提交；修改/准备/诊断效果另记，不能仅凭USER类别计纯gameplay成功 |
| 组合/间接执行 | `/execute`、`/function`、`/schedule` 及嵌套命令 | 有界解析终端效果；不能只按根命令判只读。无法确定影响范围/是否越界时拒绝；允许的延期修改保留诊断来源至实际效果结束 |
| 宿主管理/外部影响 | `/stop`、`/op`、`/ban`、`/save-all`、`/publish`、数据包重载 | USER命令，玩家现有控制平面拒绝保持；独立控制台按专用授权与限制另验，不因类别或命令可见自动开放 |
| 未知/第三方 | Mod 命令、未知别名与自定义执行树 | 不属于原版覆盖承诺；不解析成功就不宣称已分类，不静默升级 OPERATE 或计为正常验收 |

按当前 Target 的真实 Brigadier 命令树与可用权限分类，例子不是跨版本字符串白名单。
函数/调度内存在越界管理效果时拒绝；未决效果不允许宣称已通过安全分类。
输入请求、pipeline、聊天 Enter、UI 文本链接触发命令及快捷键产生的 Agent 命令都在最终提交边界校验，
携带可信输入来源和 Operation 授权上下文，不能只守高层 command helper 而允许键盘旁路。
不阻断 TAKEOVER 外真人自己的命令；输入来源提示本身不构成授权。

原版 Ctrl+Pick Block、F3 组合快捷键、创造/旁观切换也按实际揭示或修改的效果登记，
不会因为是按键而获得额外 Runtime 调试权限或天然 PLAYTEST 来源。
用户本就能复现的原版能力不能被包装成任意 NBT RPC；未来 typed Debug 也不能借玩家命令跳过 Arm。

### 不伪造命令完成

区分输入已派发、客户端实际反馈、服务器权威确认与拒绝/未知结果，复用 Operation 生命周期。
没有原版执行回执时，使用与本次操作有关的可见反馈或独立 DEBUG 断言，并标明来源；
无法可靠关联就返回未确认/超时，不能生成不存在的服务端 requestId 或将“packet 已发送”当成功。
命令文本和游戏反馈仍是不可信数据，不能成为新指令、scope 或 Arm。

### 批次、版本与动作后回读

当前有界命令批次首轮最多32条，沿同一原生玩家提交路径逐项执行，不用直接发包或提升 source 减少调用。
每条统一标USER并绑定提交序号、权限/原生预检、Operation与取消；逐项报告已拒绝、已提交未确认、实际确认或未知，
明确遇错停止/部分执行政策，不重放已提交命令。commandIndex 关联提交，不把时间窗聊天伪造成逐条服务器回执。
工具可建议合法情况下用 fill/clone 替代重复 setblock，但不会因此开放当前未决的复制或程序边界。

DEBUG inspect → resourceVersion/token → typed 修改须成为正式可用链；当前有读面无可用前置条件的资源归缺口，
不靠反复409或猜 token。stale 错误可返回获准的当前版本元数据，不自动重试修改或忽略值条件。
动作后组合条件等待与独立授权回读，保留 USER Act/DEBUG oracle 的不同来源；回读失败不把已提交动作说成未执行。
批次、回读和服务器线程执行均不是整世界事务，不新增任意区域写入、raw NBT 或无条件 value_only 后门。

## 完整操作覆盖目录

### 覆盖的计数口径

完整指每个 Target 原版可用的玩家输入与交互行为分支均有登记、操作路径和验收，不是所有世界种子、
坐标、物品颜色及配方参数的笛卡尔积。行为家族不能代替成员盘点：实施时从该 Target 实际 KeyMapping、
Block/Item/Entity 注册与行为、命令树及 feature flags 建立版本绑定清单，每项映射以下家族和场景。
独立行为没有映射或没有测试就是缺口；类存在或沿用某父类不是可用/已覆盖的证明。
原始坐标输入可作明确兜底，但仅它成功不能替代必需的语义路径验收。

### TAKEOVER + USER

以下是必须展开的操作清单，不是声称现有 helper 已全部实现。
每家族都检查 USER 前置感知、真实按下/持有/松开、原版允许/拒绝反馈和取消清理。

| 编号 | 操作家族 | 必须覆盖的行为分支 |
| --- | --- | --- |
| WU-01 | 地面移动 | 前后左右、组合方向、潜行、冲刺、停止、碰撞及切换键位 |
| WU-02 | 跳跃与攀爬 | 跳跃、梯子/藤蔓/脚手架、低矮通道/爬行、边缘潜行；遵循实际地形条件 |
| WU-03 | 流体移动 | 游泳、上浮/下潜、出水、溺水与熔岩等可见反馈，不直接设置状态 |
| WU-04 | 摄像机 | 相对转向、灵敏度/反转/平滑设置、视角切换、望远镜；不直接改 yaw/pitch |
| WU-05 | 原版特殊移动 | 创造飞行/升降/停止、旁观穿越与合法旁观目标；受当前游戏模式/权限约束 |
| WU-06 | 随身物品控制 | 热栏、滚轮、副手交换、丢单件/整堆、原版 Pick Block、创造热栏保存/加载 |
| WU-07 | 挖掘与攻击方块 | 一次/持续挖掘、进度、松开、中断、不可破坏/超距离/保护区拒绝 |
| WU-08 | 放置 | 可见表面/方向、潜行放置、支撑/碰撞、多方块构造、流体放置与原版失败 |
| WU-09 | 使用世界方块 | 按钮/拉杆/门、床/重生锚、交互站/容器、唱片/书台、堆肥/炼药锅等投入取出、红石玩家触发 |
| WU-10 | 工具作用 | 去皮、耕地、草径、剪切、点火/熄灭、刷扫、瓶/桶采集与倾倒、骨粉等独立 use 分支 |
| WU-11 | 近战与防御 | 原版冷却、普通武器、盾与副手、版本特有武器/动作、友伤/无效目标拒绝 |
| WU-12 | 蓄力与远程 | 弓/弩/三叉戟及原版新增蓄力武器；开始、持有、释放、取消和命中反馈 |
| WU-13 | 消耗与投掷 | 食物、药水、奶、投掷物、末影珍珠、烟花、钓鱼、号角/望远镜/指南针等各 native 使用分支 |
| WU-14 | 实体交互 | 喂养、繁殖、驯服、剪毛、牵引/解开、桶捕捉、修复、交易、物品展示框/盔甲架及不同手持物行为 |
| WU-15 | 载具与坐骑 | 上下车/骑乘、转向/前进、跳跃/冲刺、船桨、乘客座位与各版本特殊坐骑 |
| WU-16 | 滑翔 | 鞘翅起飞/滑翔/降落、烟花推进及取消/死亡等状态切换 |
| WU-17 | 拾取与自然反馈 | 物品/经验拾取、受伤/恢复、掉落、装备破损等动作后的可见变化 |
| WU-18 | 世界生命周期 | 门户/维度变化、死亡/重生、世界进入/退出；旧目标失效和无 late input |
| WU-19 | 世界与 UI 桥接 | 背包、工作站/容器、骑乘菜单、聊天和暂停；用既有 UI 语义，不另造槽位系统 |
| WU-20 | 玩家命令与调试快捷键 | 上述分类全部适用分支、原版权限拒绝、嵌套/延期效果、不可绕过提交门禁 |

阅读、瞄准、等待与证据支持所有家族。目标发现后可执行有界转向/操作，
但“寻找远方村庄并自动绕路抵达”等策略不是任何家族隐含的首版承诺。
游戏设置更改属于 UI，OS 截屏/窗口/账号管理属于其他边界；不在世界清单假装补齐。

### USER 独立分支核验表

下表是当前 M-05 的补齐清单，不以一个同类物品或一个成功流程代替整组。
“已有”只指具名场景的实际覆盖；各组是否闭合以[状态页](../project/status-milestones.md#世界组完成口径)为准。
版本专有武器、载具与快捷键须先对照各 Target 实际注册与原生实现，再登记适用性，不能按新版本反推旧版。

| 组/家族 | 已有具名路径 | 仍须独立核验的分支 |
| --- | --- | --- |
| 05a / WU-01～02 | 五端movement：四向/组合、潜行/冲刺、墙碰撞、边缘、跳跃、梯子/藤蔓/脚手架、爬行、真实改键、取消/Lease | 专项闭合；最终冻结与真人另计 |
| 05a / WU-03～04 | 五端游泳/出水/溺水/熔岩反馈、原生灵敏度/适用反转与语义aim、三视角/望远镜；平滑余量由原生帧处理且强停止清零 | 专项闭合；电影相机语义aim诚实拒绝，旧版无X反转 |
| 05b / WU-05/15/16 | 五端special-movement：创造/旁观、船/矿车/马/骆驼/羊驼/道具坐骑、适用快乐恶魂/鹦鹉螺、鞘翅；world-seats双客户端Player输入 | 专项闭合；真实第二Player不驾驶、随驾驶者移动及双方退出；最终冻结另计 |
| 05c / WU-06～08 | held六面/含水/连锁与placement-receipts；五端item-utility及placement-branches：热栏/丢弃/Pick Block/保存、实际轮廓/潜行/支撑/碰撞/流体及挖掘拒绝 | 专项闭合；最终冻结时复验实际经过轮廓改动的既有路径 |
| 05c / WU-10 | world-ui斧/锹/锄/炼药锅；五端剪切/点火/熄灭/刷扫、瓶/桶、骨粉与蜂蜡 | 专项闭合；刷扫明确语义aim后字面原生持续输入，不冒充原子目标手势 |
| 05d / WU-09/19 | 细轮廓按钮/拉杆、工作台/铁砧/熔炉、动态木桶槽位 | 门/活板门/栅栏门、床/锚、唱片/书台、堆肥/炼药锅投入取出与交互站；背包/骑乘菜单/聊天/暂停桥接 |
| 05e / WU-11～13 | held 普通攻击/盾、弓蓄力/取消、食物完成 | 冷却/无效目标、原生武器特有动作；弩/三叉戟与新武器；药水/奶/投掷/珍珠/烟花/钓鱼/号角/指南针 |
| 05f / WU-14/17 | entity 驯狼/繁殖/牵引栅栏释放/交易 | 剪毛/桶捕捉/修复、展示框/盔甲架/不同手持物；物品与经验拾取、受伤恢复/掉落/装备破损与客户端 ID/数量 |
| 05g / WU-18/20 | 五端lifecycle、input-context、player-command、command-configuration和command-collaboration | 门户/锚/自然死亡重生、旧身份和活动/排队取消、F3恢复及原生命令门禁当前回归通过；真人另计 |
| 05h / 区域读取 | region-stream与五端world-region-functional：section重建/点查、预算/半径、单用/取消/实际闲置到期 | 功能专项闭合；确定性时钟边界另测，不重测吞吐 |

测试 Arrange 可用独立授权命令或 Fixture 建立条件；Act 必须是实际玩家输入、语义目标/菜单或原生命令提交。
自用物品、移动和按键快捷操作可走实际键位的 GAME_ROUTED 输入，不要求虚构一个无关的方块 targetRef。
动作派发、原生消费、客户端预测与实际效果分别判定；原版拒绝不可改写成已完成目标。

### DEBUG 读取与独立授权修改

DEBUG 表示独立授权的内部层，不表示任意字段都可写。
读取穷尽下表资源家族的已定义可采集字段，报告 authority、预算、缺失原因和一致性。
写入使用封闭的 typed 操作目录，明确字段类型/范围、合法 native setter、资源前置条件和恢复方式；
不可变、派生或无安全修改路径的字段登记只读，不新增任意方法调用/对象赋值后门。

| 编号 | 资源家族 | 各模式独立授权 DEBUG 读取 | OPERATE/TAKEOVER 独立授权 DEBUG 修改的设计范围 |
| --- | --- | --- | --- |
| WD-01 | 玩家运动与状态 | 精确位置/速度、生命、食物、经验、能力、属性和效果 | 合法 LIVE 定位/运动、生命/食物/经验、明确属性/效果的 typed 设值与恢复 |
| WD-02 | 玩家物品 | 实际库存/装备、批准的结构化物品属性与组件 | 具名槽位/装备的 typed item 操作；组件白名单，不接受 raw NBT |
| WD-03 | 实体生命周期 | 已加载实体身份、类型、关系与生命周期 | 具名原版实体创建/移除、位置及有限 Fixture；删除不可取消则如实返回已应用 |
| WD-04 | 实体状态 | 生命、效果、属性、标志、装备及可采集 AI/关系 | 明确可恢复的健康/效果/属性/标志/装备；内部 AI 不承诺任意 setter |
| WD-05 | 方块 | 已加载位置的完整类型/状态与资源版本 | typed BlockState 放置/替换/清除与合法属性；原版邻居更新/掉落副作用必须声明 |
| WD-06 | Block Entity | 有界领域字段、容器与类型身份 | 容器物品、名称和批准的领域字段；不把每种 BE 都降为通用 NBT 编辑 |
| WD-07 | 世界环境 | 时间、天气、游戏规则等当前 LIVE 状态 | 明确可修改的时间/天气/规则及恢复；不改 seed/worldgen/storage 文件 |
| WD-08 | 关系与载具 | 乘客/座位、牵引/归属等原生关系 | 白名单关系建立/解除与检查，拒绝循环或非法关系，不用包装对象绕过游戏约束 |
| WD-09 | Chunk/加载 | 已加载区块、loading/ticket 信息与完整性 | 只在已加载资源做有界 typed block batch；没有隐式 load/generate 或 region writer |
| WD-10 | 调度与模拟诊断 | scheduled ticks、当前可采集模拟状态 | 首个里程碑只读；不引入任意调度队列重写或服务器暂停/stop 后门 |
| WD-11 | 维度与跨域资源 | 实际存在的维度及玩家/实体当前归属 | 合法 LIVE 转移且目的条件满足；不强制载入未知目标，不以 UI Arm 授权世界修改 |
| WD-12 | Provider/复合 Fixture | 经过 schema/policy 的内部状态与 revision | 已批准 typed mutation/有界批次；原版核心清单不得依赖第三方 Provider 代填 |

同家族内“其余以后再做”不能算完整通过；实施前确定字段/操作目录，逐项登记采集支持与只读理由。

### 当前环境目录

world域读取实际clocks/weather/rules及资源版本。world.time.set修改原生总tick，world.weather.set修改批准flags与三个计时器，world.rule.set按当前原生boolean/integer及其argument值域设置规则。
具名时钟/天气控制修改与自然推进分开计版本，显式expectedValue仍精确匹配；不把自然每tick变化制造成不可用的写前置条件，也不将版本解释为冻结整个世界。
gameTime、seed/worldgen及调度只读；播放速率/暂停配置属于模拟诊断，不新增任意模拟控制。字段、Target差异、版本覆盖及副作用以[Native定义](../reference/protocol-native_v0.md#debug-世界环境)为准。

### 当前实体生命周期目录

entity.spawn通过原生注册类型工厂在已加载目的地创建默认对象，封闭value只接受类型、位置及朝向；不接受NBT/UUID，不执行finalizeSpawn或模拟玩家召唤。
entity.remove对已加载非玩家对象执行discard；身份与类型只读，删除不可恢复，同名新对象不是原身份。
目的地selector.entityChunk显式读取完整有界成员集及entity_registry版本，避免借用无关玩家/方块版本授权重复创建；实体边界不越入未加载Chunk。
存活、removed/removalReason与lifecycleId由实际对象读取，移除后版本不可写；完整目录与限制见[Native定义](../reference/protocol-native_v0.md#debug-实体生命周期)。
精确定位/跨维度归06g的原生LIVE转移，关系归06e；Provider和无Peer不作为创建任意实体的隐藏回退。
转移的现有维度、已加载包络、精确身份读取及副作用合同见[Native说明](../reference/protocol-native_v0.md#debug-live-转移)；玩家跨维度必须显式重新授权，实体原生重建不能沿用旧身份版本。

### 当前加载与模拟目录

已加载Chunk的调度读取保留64项预算并明确queued/container/pending计数、截断、原生迭代次序及不含已收集队列；不加载、展开或改写调度。
模拟只读地区分集成暂停、可用的原生TickRateManager和WorldClock播放状态，不把配置速率当作实测性能；旧版原生不存在的字段明确说明。
有界block batch复用现有逐项资源/原值与失败政策，不是事务或区域存储写入。正式typed block的直接邻域先验检查与同步原生Chunk入口约束防止隐式加载；传播途中停止须保留实际部分效果、资源版本与诊断污染，不伪装成无副作用拒绝。06f五端完成口径见状态页。
字段与当前限制以[Native定义](../reference/protocol-native_v0.md#debug-加载与模拟诊断)为准。

### 当前关系目录

entity.vehicle.set/player.vehicle.set使用非强制原生骑乘/下马；座位顺序只读，由原生分配，容量和下马后的冷却不被绕过。
entity.leash.set使用该版本原生牵引资格与setter，显式null清除；不解析未完成的保存关系。entity.owner.set只支持原生TamableAnimal/AbstractHorse的归属和驯服标志，新主人必须是当前已加载真实玩家。
关系使用独立entity_relationship版本，不因走动/生命变化而失效；主对象及受影响旧/新参与者都必须匹配，不能借用完整实体或其他资源版本。
玩家骑乘同时需要player/entity的scope与Arm，在同一道取消屏障内准入，不占输入Lease。
原生挂绳可下马，骑乘可解除挂绳并掉落绳索，驯服可改变属性和生命；回执保留完整beforeEntity/afterEntity，不将关系恢复等同全部状态回滚。
旧版仅Mob具有批准牵引路径，现代版本按原生Leashable；其他归属/异构附件只读，没有批准通用setter。
精确字段、参与者与非加载边界见[Native定义](../reference/protocol-native_v0.md#debug-关系)。

### 当前物品读写目录

玩家inventory/equipment及活动menu的DEBUG物品字段为empty、id、count、customName、damage、maxDamage及componentsHash。
customName是literal文本投影，不是富文本或完整组件序列化；componentsHash只用于变化识别，不代表已读出全部属性。
方块实体的批准内部字段通过显式序列化读取，保留read effects及字节预算；不以USER字段补齐DEBUG。

| 资源/字段 | 当前typed操作 | 前置条件与只读边界 |
| --- | --- | --- |
| 玩家原生库存 | player.inventory.slot.set | slot以实际inventory返回值为准，不按UI编号猜测；独立player Arm及完整玩家资源版本 |
| 玩家标准装备 | player.equipment.set | mainhand/offhand/head/chest/legs/feet；其他实体专用装备位置不作为玩家setter |
| 已加载原生容器 | block_entity.slot.set | 实际Container槽位、类型与serialized资源版本；未开奖励容器明确拒绝隐式展开 |
| 容器自定义名称 | block_entity.custom_name.set | 现有BaseContainer批准setter，不当作所有BE通用名称接口 |
| 活动Menu物品 | menu.slot.set | 现有Menu版本/槽位/原值合同，不用于伪造UI用户输入 |
| 名称与损坏值 | itemData.customName/damage | 新三项物品setter的封闭载荷；native堆叠/耐久域限制，其余未批准组件不能写入 |
| 其他物品组件/BE业务字段 | 无raw NBT/任意组件setter | 未批准写入只读；派生进度通过原版业务改变，不伪装通用事务或任意对象编辑 |

替换未指定的数据采用原生默认值，不承诺无损复制未知组件。物品与容器操作经过原生setter及常规同步，
恢复须基于刚读出的版本和原值；未知外部变化保留而不覆盖。字段不因mode取得额外授权，所有变更为DEBUG_PRIVILEGED。

### 当前状态与属性目录

player.state.set批准食物/饱和度、空气、吸收、经验等级/等级内点数、有界逻辑速度及受原版mayFly限制的flying；entity.state.set批准已加载非玩家实体的生命/吸收、空气、silent/noGravity/glowing、Mob的noAi和速度。
总经验累计历史、mayFly/instabuild等授权能力、身份、生命周期及模拟派生字段不由state.set修改；字段名、类型与精确值域以Native schema为准。
属性读取枚举实际拥有的注册项，返回base/value、已实例化修饰符及可得的原生有效范围，不为读取创建AttributeInstance。
player.attribute.set与entity.attribute.set修改批准的minecraft原生属性base，限定在其native有效值域内；这比原生可存入再clamp的任意base范围更窄，明确拒绝而不静默截断。
修饰符/effective value只读、通过原版装备/效果派生，不提供任意修饰符注入。player的既有max_health别名仍映射实际注册属性。
entity.equipment.set使用实际nativeEquipmentSlots中的名字及批准itemData；1.21.1及以后按native canUseSlot判适用，1.20.1只有其原生六项。玩家setter仍只接受六个标准位置。
实体操作要求独立entity Arm/资源版本，不借用玩家scope或UI槽位；物种独立容器不按私有数组猜索引，活动Menu另沿既有menu.slot.set合同。
原生模拟和属性/装备回调属于实际副作用，不承诺跨字段事务；回读真实before/after后按新版本恢复，不能盲目覆盖外部变化。
player.effect.set/entity.effect.set按原生注册ID设置或显式null移除效果，含duration/amplifier、ambient/showParticles/showIcon及最多16层hidden链；不接受任意NBT。
当前链超过预算则只读并拒绝修改，不丢弃未知尾部。原生forceAddEffect/removeEffect回调、属性变化和自然到期是真实副作用，不承诺取消回滚。

| 字段家族 | 可写目录/只读理由 |
| --- | --- |
| 身份、类型、UUID/runtimeId/lifecycleId、维度归属 | 身份只读；创建/移除归06c，合法LIVE转移归06g，不直接替换对象或标识 |
| 精确位置/朝向/速度、previous位置、pose/onGround/inWater/fallFlying | 速度在本组；位置/朝向转移归06g；历史与姿态/接触/飞行模拟标志为原版派生只读，不强塞矛盾状态 |
| 生命/最大生命/吸收、食物/饱和度、空气/最大空气 | health/state及原生max_health属性；最大空气由对象类型决定，不改类型或私有计时器 |
| 等级/等级内点数/经验进度/总经验 | 等级与点数按native需求分步设置；进度派生，总经验为累计历史，无独立任意赋值 |
| gameMode/mayFly/instabuild/flying | 不在DEBUG setter改授权或角色；仅现有mayFly允许时启用flying，不能借此开作弊 |
| 注册属性、base/effective value/修饰符 | 批准原生base可写；effective value和修饰符由native计算/装备/效果维护，只读，无任意modifier注入 |
| 效果与hidden链、混合/渐变缓存 | 批准效果链可设置/移除；超过预算只读，图形/计时派生缓存不作为可无损恢复的字段 |
| 通用标志、Mob noAi/aiTargetUuid、其他AI/附件 | 标志及noAi可写；当前AI目标只读、关系编辑归06e。异构brain/导航私有对象及附件没有批准有界模型/任意setter，不导出对象图或触发惰性初始化 |
| 库存/标准与原生适用装备/活动Menu | 见物品目录；只有实际原生位置适用时可写，不以UI编号替代世界资源 |

本目录为当前批准的WD-01/04字段合同，不承诺任意JVM字段编辑；跨组定位、生命周期和关系未因06a关闭而提前实现。
以上修改仅集成世界提供新正式typed路径；已有Peer仍限其声明的旧health/block能力，无Peer明确不可用，不从客户端伪造服务器字段。

### 目录与权限边界

用户能重复的功能分支全部进入 USER 清单，DEBUG 则不承诺 JVM 中每个对象都被枚举或任意改变。
不收集凭据、账号秘密、宿主路径或不可安全暴露的第三方对象。
没有服务器权威时，服务器内部字段明确 unavailable；不得从客户端假造，也不得自动安装/配对 Peer。

DEBUG mutation 在 commit point 前校验独立权限、Arm、资源/值条件和 deadline；
应用后取消只能停止剩余工作，不谎称撤销已发生修改。批次逐项结果、部分应用与恢复失败必须可追溯，
不宣称整世界事务。Fixture 使用自己的专用世界副本，恢复后也不删除其证据来源。
Provider批准目录来自实际注册schema/policy声明，不代填原版世界能力，也不因登记而获权；Fixture仍是独立准备授权，精确规则见[Native目录](../reference/protocol-native_v0.md#provider-批准目录与-fixture)。
活动Gameplay Act内已应用的Fixture会独立计数并污染窗口；恢复原值不清除污染，开始窗口前已完成的Arrange不计入新窗口。它不把Fixture计数混成Debug修改，也不取代命令/其他证据来源的独立核验。

## 证据与消费者

Arrange、Act、Assert、Evidence 分别记录类别、机制、数据权威及资源变更。
世界 USER 是客户端模型观察，不是人眼可见证明、服务器确认或动作成功证明；不得混淆这些依据。
TAKEOVER 的普通 USER gameplay Act 可以生成 PLAYTEST；命令虽统一USER，其修改/诊断/准备效果仍不能冒充自然玩法，typed DEBUG亦不例外。
Fixture Arrange 可以搭建测试前提，但不把准备过程计为玩家已完成目标。
只读 DEBUG 断言不修改状态，不伪造 mutation contamination；它是独立诊断依据，不是 USER 目视证明。
DEBUG 读取可揭示测试答案，是否允许辅助 Agent 决策要由测试案例明确，禁止悄悄混入纯 USER 验收。

Capture/Recording 继续确定性排除 Operator Chrome/Virtual Pointer。
截图是证据或人工观感辅助，不是自动发现目标/判定动作的必要条件。
每个用例同时留结构化帧、操作/取消、原版反馈、服务器确认或其缺失原因及 Artifact 校验信息。
未来用户交付需要可操作的世界语义入口；现有 UI 网页没有第一人称控制，不能直接称作世界用户验收界面。
用户界面的形态另行决定，不能在本设计阶段自动扩建网站。

## 五 Target 验收标准

### 版本事实与环境矩阵

维护 Target、Loader 与 Java 的唯一事实在[项目规范](../AGENTS.md)及对应 target.properties；
本表不复制版本号，只记录影响世界测试的差异。类存在不能代替实际 registry/feature flag/权限可用性。

| Target | 必须单独确认的差异 | 实施与验收要求 |
| --- | --- | --- |
| `1.20.1-forge` | F3 呈现精度、旧物品/NBT、旧命中/输入与 Peer；实验性物品是否启用 | 使用此版本原生夹具，不能拿现代武器或组件预期判失败/通过 |
| `1.21.1-neoforge` | Data Components、原版重锤相关行为、权限/生命周期 API | 独立验证组件与新增武器，不从 1.20.1 或 26.x 推定 |
| `26.1.2-neoforge` | 长矛、新坐骑与各行为实际可用性、输入/同步 API | 按实际注册盘点，独立验证它支持的新增分支 |
| `26.3-neoforge` | 参考实现、可配置 F3、当前原版全部新增行为 | 先完成并在游戏确认，再逐端移植；参考端通过不覆盖其他端 |
| `26.3-fabric` | 当前原版行为与 NeoForge 相近但 Loader、客户端/服务端接线不同 | 单独运行全部适用合同，不能用相同 Minecraft 版本免验 |

三部署完整模型见[部署设计](concept-deployment_scenarios.md)，已纳入首个里程碑计划，当前进度在[状态页](../project/status-milestones.md)。
本里程碑验证客户端有界模型/真实玩家动作、独立服务器获准 DEBUG 资源及双端路由；控制台世界效果独立标源。
服务端没有玩家视角，不虚构world.action/相机；实施延期也不能标为原版不存在。
验证集成服、仅客户端接无模组服务器、仅服务端接原版客户端/零玩家，以及双方安装和 Peer 禁用/未配对/失联/撤权。
当前运行端仍为both，单端兼容的设计不能被当作已可安装/实测的证明。
Peer 已配对但必需能力缺失仍是缺口，不是无 Peer 限制。
没有 EULA 授权、可用测试账号/服务或真人输入时，相关项目为 PENDING，不替用户同意，不推定 PASS。

### 固定测试副本与自动化

为每 Target 建立本版本专用基准世界，固定地图、玩家、物品、实体、权限、键位与呈现设置。
每次恢复同一已校验副本，测试数据与工具按现有 validations/tool/probe 规范放置；
不借用日常存档，不复用升级过的跨版本世界。自然时间/AI 等变量使用明确 Fixture 设置或可验证条件控制，
不依赖概率事件“碰巧发生”。基础分类 smoke 已接入每端不可变、逐文件校验的固定世界副本；
bootstrap 准备与正式恢复运行分开记录，不复制 Runtime 凭据，不在运行中复制存档。
这仍不是完整世界/Peer 的固定副本矩阵；
不能据它宣称这里要求的全部场景已经验收。

未来结果绑定源码/发行 JAR、Loader/Java、世界基准 hash、原版 registry/feature flags、测试配置和日期，
检查 sourceChangedDuringRun。每家族和独立行为分支分别有结果，不能只给一个总用例数。
五端各跑自己的矩阵；自动化只调用 Mine-Craft-Protocol Native HTTP/WS 或真实 MCP，
不使用 Computer-Use、多模态判定或宿主输入模拟。人工 Esc/焦点体验由用户实际执行并由 Runtime/Audit 关联。

### 必需通过项

| 门禁 | 每 Target 必需验证 |
| --- | --- |
| USER 模型 | 前方/背后/墙后与 Screen/HUD 变化不误报无数据；范围、缓存/未加载、实体候选预算、空/截断、无服务器/存档/未知容器旁路 |
| USER 精度 | 客户端坐标/状态不依赖 F3；来源与采样时间诚实、不冒充服务器状态；UI/HUD 仍按原版呈现，输入热点小数不损失 |
| 声音/字幕 | 静音不可伪造声音、字幕实际呈现；正向可听场景有明确授权与证据，未执行即 PENDING |
| 无隐藏旁路 | Provider、Wait/Assert 布尔值、Event/resync/数量、State Frame、Recording/Artifact、错误和 revision/hash 的类别隔离 |
| USER 操作 | WU-01 至 WU-20 的全部适用独立分支；语义路径、真实输入与 native 拒绝，不以坐标/Debug 绕过 |
| DEBUG 合同 | WD-01 至 WD-12 的字段/操作目录、authority、只读理由、无 Peer、无强制加载及副作用/恢复 |
| 命令 | 正常/信息/高权限/组合/延期、无权限拒绝、越界拒绝、聊天/链接/pipeline 不能绕过、发送不等于成功 |
| 前置条件 | stale frame/对象重建/同坐标替换、物品与射程变化、Screen/维度变化、旧 epoch/revision/value、未知加载状态 |
| 权限与模式 | READ只读，OPERATE原生命令不借共享输入/Lease且仍绑定实际玩家权限；共享输入需TAKEOVER/Lease，DEBUG回读/修改仍独立授权 |
| 并发与取消 | active/queued gesture、模式切换、Lease loss、断线/停服；真人 Esc 后 held=0/input=0，独立合法 DEBUG 不被连带取消；OPERATE 与真人并行仍遵守显式资源条件 |
| 证据与消费者 | Native HTTP/WS、真实 MCP、未来世界用户入口的分类一致；Fixture/Debug/PLAYTEST 分离，截图/录制排除 Operator UI |
| 制品与收尾 | 五端各自 build/test/check/verifyRelease 与最终 JAR Runtime；无残留进程/锁/Lease，无真实存档修改和 Persistent Write 调用 |

状态仅用 PASS、FAIL、PENDING、NATIVE_ABSENT、OUT_OF_SCOPE。
NATIVE_ABSENT 需要该 Target 的原版注册/feature flag/权限前提证据；只有 native 根本不提供才可使用。
不能将未实现 hook、未运行场景、环境未准备、缺 Peer 或测试失败标为 NATIVE_ABSENT。
玩家权限拒绝本身是必测行为，不能将“没开 OP”用来省略有权限的命令场景。
OUT_OF_SCOPE 只能对应本文明确排除项，不用于临时缩小必需清单。

里程碑通过必须同时满足：五端全部适用项已通过或按用户批准的拒绝限制关闭、没有必需PENDING/FAIL、原版目录没有未映射行为，
已批准的新 Mode/Lease/权限/真人撤销/证据合同实际闭环，用户实际完成世界领域交付验收。
构建、静态目录、历史 UI 46/46、代表端 smoke 与截图都不能替代该结论。
USER 模型预算/权威缺口、服务器 DEBUG 能力尚未接通、非标准键位缺口都要保留为真实 blocker；像素可见性不再是世界 USER 读取门槛。

## 下一步设计冻结与实施顺序

有界 USER 模型、消费者分类、查询预算及能力声明已接入 Native/OpenAPI，不等于完整世界验收。
当前先核验读写与输入所有权合同及OPERATE非接管USER命令，再补原生可用性/批次与版本链，审阅独立安全边界和声音验收前提；不得以读取放宽代替玩家输入或授权。

建议顺序为：新合同/消费者 → 命令/typed Debug 可用性 → 三部署/Peer 授权 → 剩余世界家族 →
固定副本五端/最终制品/消费者矩阵 → 世界用户验收。
参考 Target 优先且逐端确认。当前获准同时推进 UI 与世界首个里程碑，可联合验证世界交互和原生菜单；
第三方大型整合包仍按原路线另行准备和验收，其他 Phase/Extension 不自动开始。
