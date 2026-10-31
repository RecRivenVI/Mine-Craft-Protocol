# UI 与世界首个里程碑状态

UI、世界与完整Showcase均 **IN PROGRESS**，尚未READY，统一人工验收未执行。
Developer Preview基线4346328d已完成自动冻结与Alpha分发；其五端UI/世界、45秒连续正确性、单JAR及服务端/三安装组合证明仍保留，不等于人工接受。
当前扩展平台工作区已有新增代码，尚未完成平台冻结；下方双里程碑矩阵记录DP基线，不证明新增平台已通过。实际已发布身份以外置latest及快照清单为准，不用新build文件替换旧快照。
恢复先读本页及[执行计划](plan-execution.md)，普通开发直接与用户协作；不自动恢复旧Goal或扩展其他批次。
本轮冻结与复用依据见[自动收尾记录](phase-1-core_delivery.md#2026-10-31开发者预览自动冻结)，既有安装和发布历史见[交付记录](phase-1-core_delivery.md#2026-10-22-e世界单jar与alpha交付)。

## 当前工作与下三步

当前主线为1.0扩展平台正式实现，基线develop/4346328d及Alpha 20261031-1440-d6f3a89a，开工工作树干净。研究原型不直接合入，既有DP自动证明不作为新平台证明。
1.0与Developer Preview是内部实现质量标准，不表示决定对外发布；不新增许可证、公开支持矩阵或对外长期兼容承诺。人工验收保留待办，不阻塞本轮开发。
由主线程连续推进；不操作Goal、不测性能、不做整合包/E1～E3/Persistent Write。允许阶段门禁通过后创建SSH签名Update检查点并推送，中途不发布Alpha；全部实现和五端完整回归通过才发布固定快照，保留旧快照，不通知使用方。

| 阶段 | 当前状态 | 完成门禁 | 当前依据 |
| --- | --- | --- | --- |
| EP-1 SDK与普通包纵切 | 完成 | 内容审批/依赖/运行端、事务注册、owner资源与逐项清理、Provider/probe、静态目录和离线MCP查阅；参考端实际运行及单元/合同负控 | [参考端实际结果](../../validations/integration-extension_platform/result/26.3-neoforge-client.json)：普通包/采样/MCP/Arm到期/Lease独立/正常退出；整仓check、五端verifyRelease、严格合规0失败0警告通过 |
| EP-2 五端早期加载桥 | 进行中 | 五端原生/最终单JAR与独立服务端实测；包内Mixin真实目标计数、错误/重复/错端诊断、两版本包在同一本体替换；不覆盖其他Loader资源查找 | 研究仅1.21.1成品259实测，不能复用为本轮通过 |
| EP-3 深层UI与逻辑输入 | 待办 | 可见投影/稳定目标/框架异步快照；真实hover、click、drag、多按钮、scroll、文本/IME入口及撤权清理；Modern UI/EMI超出单按钮/搜索子集 | 新门禁待实装 |
| EP-4 世界适配与七包 | 待办 | 普通目标复用、PartRef与pick/版本守卫；CC、Create、AE2、EMI、Exposure、Modern UI、LittleTiles逐项正常Act+独立Assert；Create/LittleTiles/AE2因果诊断，做不通明确缺口 | 研究闭环与未确定项分别保留，不作当前产品PASS |
| EP-5 统一测试与作者交付 | 待办 | 登记ID的profile/instance/fixture主机工具、活动世界拒绝、正常停止/锁/快照；正式作者规则、MCP文档/包目录/场景查阅、独立构建与安装；跨两个包故障/恢复 | 主机授权独立，CLI先行，不开放任意路径/shell |
| EP-6 冻结与Alpha | 待办 | 五端build/Java/Companion/OpenAPI/文档/严格合规/verifyRelease、适配/生命周期/安装及受影响既有矩阵；同批固定分发与集中人工清单 | 全部通过前不刷新latest |

下三步：打通EP-2的参考端、Fabric与旧Forge风险路径并补齐五端；深入UI逻辑输入；随后推进世界部件和七包并按实际依赖逐包验证，不无限追加第三方业务。
适配包作者工程暂放本仓tool组件components/extension_packages，按模组/Target独立产包，不合并进本体JAR、不新建远程仓库；这是可撤回工程位置选择。
六项研究默认值已获用户批准。SDK合同按研究第11节先区分冻结候选与预览面，最终划分仍由用户在平台实现后决定。

以下部署与双里程碑矩阵保留DP基线4346328d的既有结论；不表示新增平台或当前build产物已通过这些运行组。

合作客户端NeoForge 1.21.1已启动至标题页确认：实际2560×1440、RecRivenVI、主音量50%、音乐关闭，加载Custom Skin Loader/Zume后正常退出。两类隔离服务器的当前JAR启动证明保留；不把环境准备记作真人验收。

服务端READ日志/OPERATE原生命令需独立server_console授权；不持玩家Lease，TAKEOVER不适用。
目录和限制见[部署设计](../design/concept-deployment_scenarios.md#独立入口与原生输入)，
五端当前单JAR控制台及世界目录证据见[服务端验证](../../validations/integration-server_peer/validation.md)的result/<target>-server-debug.json。
服务器目录证据为同项result/<target>-server-debug.json，绑定原DP候选JAR；后续构建改变哈希时必须重新核对，不能沿用不匹配证据。
读取不要求Arm/Lease，OPERATE修改仍需完整独立授权；本轮具名运行组不替代五端全部组合的穷尽验收。
修改回执需要DEBUG读取scope，在执行前检查，不借server_console；旧版本/原值/非法原生属性及未加载拒绝、批次恢复和disarm均已实测。
服务器适用目录复用原生引擎，显式维度/真实玩家；当前实测包含环境、实体创建/移除、容器物品恢复、State Frame和Persistent Read。
Peer已实测未委托/非OP拒绝、所有者issue/attach、原生读写/恢复、错误连接/旧版本/撤销/deop拒绝，不取得输入Lease。
共享生产引擎测试覆盖排队撤销、发布时权限重查与无迟到修改；不冒充真人Esc、全部资源分支或第三方组合验收。
DP基线的build/check/verifyRelease、协议/文档及严格合规已通过；原固定快照与冻结证明一致，不被新构建改写。
本轮四项审查回归另由真实HTTP与MCP验证：expected保留递归JSON，数组完整比较；控制台支持USER，内部Provider要求显式DEBUG。
本地UI目录不借服务器委托；worldDiscovery明确保留远端失败原因，取消/shutdown/未知异常不伪装成部分成功。

可选安装互通使用官方原版/Loader和最终单JAR，无开发classpath或probe。下列结果已逐哈希核对原DP候选发行JAR，
包含真实入服、USER读取/背包输入、无Peer拒绝、双端委托/版本/撤权与断线清理；有限具名组不代表穷尽全部资源与并发组合。
默认未授予服务端控制台权限，TAKEOVER在独立服务器明确不适用；安装不授予OP、scope或Arm。
产品客户端及服务器正常退出；真正原版客户端无Runtime退出入口，在服务器正常保存关闭后终止自有已断线进程，未记作正常客户端退出。

| Target | 仅客户端 | 仅服务端 | 双端委托组 | 证据 |
| --- | --- | --- | --- | --- |
| Forge 1.20.1 | PASS | PASS | PASS | [当前JAR](../../validations/integration-server_peer/result/1.20.1-forge-optional-install.json) |
| NeoForge 1.21.1 | PASS | PASS | PASS | [当前JAR](../../validations/integration-server_peer/result/1.21.1-neoforge-optional-install.json) |
| NeoForge 26.1.2 | PASS | PASS | PASS | [当前JAR](../../validations/integration-server_peer/result/26.1.2-neoforge-optional-install.json) |
| NeoForge 26.3 | PASS | PASS | PASS | [当前JAR](../../validations/integration-server_peer/result/26.3-neoforge-optional-install.json) |
| Fabric 26.3 | PASS | PASS | PASS | [当前JAR](../../validations/integration-server_peer/result/26.3-fabric-optional-install.json) |

此前集成服补充场景为conformance-world_read/result/<target>-integrated-peer.json，原候选五端均正常退出、端口释放、源码未在运行中变化。
本轮按冻结矩阵逐端核对或补跑该组，是否属于当前候选以原始结果的源码绑定为准。
这是既有同进程序列化诊断：复用房主入口，不证明独立principal；独立主体由上述真实双端单JAR组合证明。
该组另实测USER目标方块变化事件和“实际背包按键→USER Provider读回”分段结果；Agent Esc未触发人工撤销。
正常保存退出有输入上下文切换导致的未确认回执，工具独立核验新原生上下文、标题页和held清零，未重放点击；未记作已确认点击回执。

## 双里程碑五端矩阵

P=仍有必需实现/覆盖，V=本包有限自动验收已具备、共同交付/真人可能仍待，均非领域READY。
剩余数按交付验收组计数，共5组，均需集中真人合作/体验；不是缺陷数、测试数或百分比。
主体实现、消费者整合与当前候选自动冻结已闭合；完整领域仍需用户接受，不把自动通过写成真人通过。

| 工作包 | Forge 1.20.1 | NeoForge 1.21.1 | NeoForge 26.1.2 | NeoForge 26.3 | Fabric 26.3 | 剩余组 | 粗估工作量 | 完成条件与最近证据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M-01 分类/权限 | P | P | P | P | P | 2 | 集中真人验收 | 01c/d/e/h/i/j自动组已具备；01f/g的真人撤销、非接管并行体验仍待，不借脚本OP或Agent Esc代替 |
| M-02 UI USER | V | V | V | V | V | 0 | 自动闭合 | 02a～g五端有限all；外部服务、缺类、管理发送前拒绝边界明确 |
| M-03 UI DEBUG | V | V | V | V | V | 0 | 自动闭合 | 03c含建议/旁观/本地typed、MCP与网页隔离五端整组通过 |
| M-04 HUD/Toast | V | V | V | V | V | 0 | 自动闭合 | H-02～10适用native draw/生命周期及Demo；不外推最终像素/网络/第三方 |
| M-05 世界 USER | V | V | V | V | V | 0 | 自动专项闭合 | 05a～h五端专项齐备；05g当前命令/生命周期通过，最终受影响冻结计M-10 |
| M-06 世界 DEBUG | V | V | V | V | V | 0 | 自动闭合 | 06a～h既有集成服批准目录五端专项闭合，含CF-022；06i版本链保留；新部署收口计M-09 |
| M-07 命令 | V | V | V | V | V | 1 | 1～3小时 | 07d五端自动权限变化各6项；剩真人协作、普通服务器权限变化与Esc |
| M-08 固定副本 | V | V | V | V | V | 0 | 自动组闭合 | 客户端与服务器不可变副本/事实哈希、锁、主体夹具；不复制Runtime凭据，不覆盖未知目录 |
| M-09 三部署/Peer | V | V | V | V | V | 0 | 适用自动组闭合 | 当前五端单JAR服务端/三组合重跑通过，含嵌套MCP条件、无Peer/委托本地UI发现及控制台类别；不外推全部边界或真人体验 |
| M-10 回归/制品 | V | V | V | V | V | 0 | 自动冻结闭合 | 完整检查、五端既有UI/世界组、45秒持续移动/观察/强停止、Core展示及最终单JAR安装通过；性能未测，历史NOT_MET保留 |
| M-11 展示 | P | P | P | P | P | 2 | 用户集中体验 | 保留客户端17项基础，增加服务器/委托展示入口；完整合作展示与现场体验尚未执行 |
| M-12 交付 | V | V | V | V | V | 0 | 分发候选可用 | 同批JAR/清单/手册门禁具备，实际提交和固定批次以Git/latest记录为准；所有旧快照保留，不代表人工接受 |

推进顺序：M-02/03/04自动组和既有M-05/M-06专项保留；当前M-09初始控制台与首批主世界DEBUG已闭合。
后续按用户安排集中M-01/M-07真人协作与M-11体验；自动冻结和分发不消除这5组待验，不自动恢复旧Goal。
四项静态审查发现已收口；完整里程碑仍待上述接受步骤，不依据既有测试数量推算READY日期。

## 世界组完成口径

分支见[世界目录](../design/concept-world_control.md#完整操作覆盖目录)，USER Act不由DEBUG代替。

| 组 | 必需范围 | 当前 |
| --- | --- | --- |
| 05a | WU-01～04：移动/攀爬/流体/相机及持续输入 | 五端专项闭合；含改键/原版平滑与强停止、退出重入清理 |
| 05b | WU-05/15/16：特殊移动/载具/滑翔 | 五端专项闭合；含真实双玩家驾驶/乘客 |
| 05c | WU-06/07/08/10：物品/破坏/放置/工具，含无效果诚实回执 | 五端item-utility、placement-branches、world-placement-receipts和full当前冻结通过 |
| 05d | WU-09/19：世界方块与UI桥接 | 桥接/书架/交互站、锚/真实重生五端冻结回归通过；26.1.2单次灯熄灭未复现项保留 |
| 05e | WU-11/12/13：战斗/蓄力/消耗/投掷 | combat-use/range-branches/world-seats/full五端当前冻结通过；不测性能或外推动态Boss |
| 05f | WU-14/17：实体/拾取/自然反馈，客户端掉落物ID/数量 | entity-branches/entity-social/entity-parts/full五端当前冻结通过；保留Loader原生消耗差异，不外推动态Boss |
| 05g | WU-18/20：生命周期/维度/重生/原生命令 | 五端lifecycle、player-command、command-configuration、command-collaboration及input-context当前回归通过；真人另计 |
| 05h | 缓存区域/section分页，范围/年龄/预算/续读，不自动寻路 | 五端功能闭合：实际页/点查、预算/半径、单用/取消/闲置到期；未重测性能 |
| 06a | WD-01/04：玩家/实体字段与合法typed状态 | 五端闭合：批准状态/属性/效果/装备/AI标志及权限负控；定位/关系另计 |
| 06b | WD-02/06：库存/装备/方块实体，含CF-022分页 | 五端闭合：原生物品/名字/损坏值、分页序列化、pending loot拒绝；其余业务字段只读 |
| 06c | WD-03：实体生命周期 | 五端闭合：原生工厂/discard、成员版本、旧身份拒绝、非加载与MCP清理 |
| 06d | WD-07：环境 | 五端闭合：原生时间/天气/规则、控制版本与自然推进、拒绝/恢复及MCP；播放只读 |
| 06e | WD-08：关系 | 五端闭合：原生骑乘/牵引/归属、参与者版本、双授权、MCP及副作用恢复 |
| 06f | WD-09/10：有界batch/只读模拟/已加载区域 | 五端闭合：调度预算、原生传播防加载及部分效果/MCP/录制回执；非回滚 |
| 06g | WD-11：合法LIVE转移 | 五端闭合：原生定位/换维度、精确身份读取、非加载/版本/Arm拒绝、MCP与恢复 |
| 06h | WD-12：Provider/Fixture批准目录 | 五端闭合：真实schema/policy声明、独立权限、版本/批次、MCP及Fixture污染分离 |

## 当前可复制快照

固定分发根目录为D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/，实际当前目录只从latest.txt选择一次。
七文件身份及证明以快照alpha-manifest.json为权威，手册为AGENT_GUIDE.md；按固定分发工作流复制和保留。
其他线程登记使用后复制固定目录，不直接引用build/，不以HEAD单独判断候选或混用手册/JAR。

本轮Developer Preview自动冻结沿用profile=e_world_trial_v1，并以developerPreviewFreeze补充具名冻结证明；humanAcceptance=NOT_PERFORMED，fullMilestonesComplete=false。
五端仅Loader和一个发行JAR的全新安装进入世界、握手/USER读取及保存退出/关闭通过；不是制品全部功能或真人验收。
已修CF-017/018/072、071、021、019、023，使用方组合仍待复测；详细来源见[阶段记录](phase-1-core_delivery.md#2026-10-20-e-ui参考端增量验证)。

开放项：SDL3启动、26.1.2 HUD写文件/TLS下载/灯熄灭，以及1.21.1 UI优化器取消的历史单次异常原记录。
本轮另修复并验证优化器自然完成后的Consumer竞争判定，不据此认定历史未知异常同源；当前矩阵未复现的记录不删。
复测通过不等于修复，根因未定项及原失败保留在阶段索引；Runner保留原始错误。
历史三攻击比值仍NOT_MET，本批不测性能、不删hold/冷却/GUI节奏凑指标。
原生消费不等于伤害，CLIENT_KNOWN预测不等于服务器确认；取消、held=0、无迟到及权限/截图隔离不放宽。

## 合同与限制

- READ只读；OPERATE不接管键鼠，获准原生命令/DEBUG可与真人并行；TAKEOVER持既有Lease。
  USER/DEBUG独立读取授权，模式不授予scope/Arm；Esc仅撤销接管并持续要求重新同意，合法独立DEBUG由模型结合用户决定。
- UI USER只读实际呈现；世界USER为有界客户端缓存，不自动加载Chunk、读存档或开放服务器内部数据。
  PLAYTEST、FIXTURE、DEBUG_PRIVILEGED不混淆，Operator UI不进入默认截图/录制。
- mod_client_side=optional、mod_server_side=optional，两端均可单独安装；M-09独立Runtime/初始控制台已五端验证，有限Peer仍依赖在线ServerPlayer。
  命令统一USER，原生可见性不代表授权或执行成功，不提升主体。
- 第三方Sodium/自绘UI存在语义缺口，授权后可截图/坐标回退；不宣称自动适配。模板/Target/依赖固定，不开展整合包测试。
- 自动测试1920×1080静音、4GiB、一次一个自有客户端；禁止Computer-Use/宿主输入注入/操作用户Prism或重要存档。
  不实施Persistent Write、后续Phase、E1/E2/E3、Fake Player、第二界面或寻路。

## 使用方反馈分诊

19条：接收11（缺陷5/缺口6）、待信息1、范围外7；未直接归为调用方/环境。
来源见[阶段分诊](phase-1-core_delivery.md#2026-10-20接收反馈分诊与后台相机最小复现)，使用方组合待复测。

| 归位 | 编号 | 当前结论 |
| --- | --- | --- |
| 已交付，等复测 | CF-017/018/072、071、021、019、023 | 修前/修后与受影响五端证明见上一批阶段索引 |
| 已交付，等复测 | CF-022 | 有界分页、五端Native/真实MCP专项及单JAR安装通过，已随本批E世界Alpha交付 |
| 第三方后续 | CF-015、064、056 | RRO/Modern UI语义及渲染阶段组合，范围外 |
| 待补信息 | CF-024 | 缺同批Target/玩家权限/discover与suggest原始回执 |
| 不在本批 | CF-016、025、063、065～068 | 不支持Target/受限tick/扩展平台与宿主工具 |

CF-056初判Radiance移植渲染阶段适配为主、双方协作：末帧Operator UI与私有GUI scope不匹配。
不转发线程，不关闭Chrome或截图隔离绕过，本项目原版修复不证明该组合修复。

## 验证入口与集中人工清单

[世界验证](../../validations/conformance-world_read/validation.md)、[UI验证](../../validations/conformance-vanilla_ui/validation.md)与
[发行验证](../../validations/regression-v1_release/validation.md)按实际依赖核验。
[固定快照工作流](../development/workflow-alpha_agent.md#固定分发与使用登记)负责分发；不自动运行全套Showcase或人工验收。

既有单次合作验收记录保留；当前新增候选没有做统一真人验收。
下一次集中安排含非接管协作、真实撤权/恢复、物理Esc、命令协作和整体展示；动作、预期及时长统一见[集中合作验收清单](../development/testing-cooperative_acceptance.md)，不自动代验。
