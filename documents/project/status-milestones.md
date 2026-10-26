# UI 与世界首个里程碑状态

UI、世界与完整Showcase均 **IN PROGRESS**，尚未READY，统一人工验收未执行。
既有批次E世界Alpha继续有效；M-09初始控制台与首批服务端主世界DEBUG已完成，完整三部署尚未闭合。
恢复先读本页及[执行计划](plan-execution.md)，普通开发直接与用户协作；不自动恢复旧Goal或扩展其他批次。
冻结结果及复用依据见[第一检查点](phase-1-core_delivery.md#2026-10-22第一检查点结果)，安装和发布见[交付记录](phase-1-core_delivery.md#2026-10-22-e世界单jar与alpha交付)。

## 当前工作与下三步

本轮由主线程直接推进，不操作Goal/其他线程、不提交或推送、不测性能，Persistent Write=0。

1. 完成：五端零玩家Runtime/控制台、Native与真实Companion、原控制台并存、独立时间回读和正常关闭；参考端客户端单JAR入世界/退出回归通过。
2. 完成：零玩家主世界/方块深读、typed world.block.set和批次、独立scope/Arm/版本与取消；五端最终单JAR经实际Companion通过，HTTP负控及参考端/Fabric客户端回归通过。
3. 待安排：其余M-01、部署/展示/交付及最终统一验收；本轮未做人工验收，未发布新Alpha，Persistent Write=0。

服务端READ日志/OPERATE原生命令需独立server_console授权；不持玩家Lease，TAKEOVER不适用。
目录和限制见[部署设计](../design/concept-deployment_scenarios.md#独立入口与原生输入)，
五端当前单JAR证据见[服务端验证](../../validations/integration-server_peer/validation.md)的result/<target>-console.json。
首批世界DEBUG证据为同项result/<target>-server-debug.json，均匹配当前发行JAR；读取不要求Arm/Lease，OPERATE修改仍需完整独立授权。
修改回执需要DEBUG读取scope，在执行前检查，不借server_console；旧版本/原值/非法原生属性及未加载拒绝、批次恢复和disarm均已实测。
其余维度、玩家/实体/容器/Provider目录和完整Peer委托、无模组客户端互通仍待实施；新代码尚未发布到固定Alpha。
check/verifyRelease和严格合规通过；本地发行JAR已更新，旧固定Alpha及清单不变。

## 双里程碑五端矩阵

P=仍有必需实现/覆盖，V=本包有限自动验收已具备、共同交付/真人可能仍待，均非领域READY。
剩余数按独立验收组计数，共25组；不是缺陷数、测试数或百分比。共同最终交付计M-10。
估时低置信，M-09决策后重估。

| 工作包 | Forge 1.20.1 | NeoForge 1.21.1 | NeoForge 26.1.2 | NeoForge 26.3 | Fabric 26.3 | 剩余组 | 粗估工作量 | 完成条件与最近证据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M-01 分类/权限 | P | P | P | P | P | 8 | 12～24小时 | 01c～j：Peer/读取/非接管协作/诊断订阅；01a/b新准入保留，本批不实施 |
| M-02 UI USER | V | V | V | V | V | 0 | 自动闭合 | 02a～g五端有限all；外部服务、缺类、管理发送前拒绝边界明确 |
| M-03 UI DEBUG | V | V | V | V | V | 0 | 自动闭合 | 03c含建议/旁观/本地typed、MCP与网页隔离五端整组通过 |
| M-04 HUD/Toast | V | V | V | V | V | 0 | 自动闭合 | H-02～10适用native draw/生命周期及Demo；不外推最终像素/网络/第三方 |
| M-05 世界 USER | V | V | V | V | V | 0 | 自动专项闭合 | 05a～h五端专项齐备；05g当前命令/生命周期通过，最终受影响冻结计M-10 |
| M-06 世界 DEBUG | V | V | V | V | V | 0 | 自动闭合 | 06a～h既有集成服批准目录五端专项闭合，含CF-022；06i版本链保留；新部署收口计M-09 |
| M-07 命令 | V | V | V | V | V | 1 | 1～3小时 | 07d五端自动权限变化各6项；剩真人协作、普通服务器权限变化与Esc |
| M-08 固定副本 | P | P | P | P | P | 2 | 2～4小时 | 08a恢复/锁/凭据；08b部署夹具，不无故重建基准 |
| M-09 三部署/Peer | P | P | P | P | P | 6 | 分组复核 | 09c/d初始控制台及首批主世界DEBUG五端单JAR通过；其余目录、单端互通、完整委托/录制仍待办，完整组未关闭 |
| M-10 回归/制品 | P | P | P | P | P | 4 | 8～16小时 | 10a领域、10b安装、10c取消/证据、10d连续正确性；性能暂缓 |
| M-11 展示 | P | P | P | P | P | 2 | 2～5小时 | 11d协作/连续操作，11e三部署；已有17项基础保留 |
| M-12 交付 | V | V | V | V | V | 2 | 1～3小时 | 12a目录/手册，12b同批七文件固定分发；V不代表完整READY |

推进顺序：M-02/03/04自动组和既有M-05/M-06专项保留；当前M-09初始控制台与首批主世界DEBUG已闭合。
之后按用户安排推进M-09其余资源/Peer/互通、M-01、M-08及M-10/11/12；M-07真人集中执行，不自动恢复旧Goal。
M-09的产品方向已获用户批准，不再等模板决策；完整部署矩阵尚未闭合，两个里程碑仍没有可信READY日期。

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

固定分发根目录为D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/，latest指向 **20261022-1722-0ea052cb**。
七文件身份及证明以快照alpha-manifest.json为权威，手册为AGENT_GUIDE.md；按固定分发工作流复制和保留。
本批世界快照已发布；其他线程登记使用后复制固定目录，不直接引用build/，不以HEAD单独判断dirty候选。

最近交付为E世界试用，profile=e_world_trial_v1，humanAcceptance=NOT_PERFORMED，fullMilestonesComplete=false。
五端仅Loader和一个发行JAR的全新安装进入世界、握手/USER读取及保存退出/关闭通过；不是制品全部功能或真人验收。
已修CF-017/018/072、071、021、019、023，使用方组合仍待复测；详细来源见[阶段记录](phase-1-core_delivery.md#2026-10-20-e-ui参考端增量验证)。

开放项：SDL3启动、26.1.2 HUD写文件/TLS下载/灯熄灭、1.21.1 UI优化器取消的单次异常。
复测通过不等于修复，根因未定项及原失败保留在阶段索引；Runner保留原始错误。
历史三攻击比值仍NOT_MET，本批不测性能、不删hold/冷却/GUI节奏凑指标。
原生消费不等于伤害，CLIENT_KNOWN预测不等于服务器确认；取消、held=0、无迟到及权限/截图隔离不放宽。

## 合同与限制

- READ只读；OPERATE不接管键鼠，获准原生命令/DEBUG可与真人并行；TAKEOVER持既有Lease。
  USER/DEBUG独立读取授权，模式不授予scope/Arm；Esc仅撤销接管并持续要求重新同意，合法独立DEBUG由模型结合用户决定。
- UI USER只读实际呈现；世界USER为有界客户端缓存，不自动加载Chunk、读存档或开放服务器内部数据。
  PLAYTEST、FIXTURE、DEBUG_PRIVILEGED不混淆，Operator UI不进入默认截图/录制。
- mod_side=both及Loader元数据未改；M-09独立Runtime/初始控制台已五端验证，有限Peer仍依赖在线ServerPlayer。
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
下一次集中安排仍含非接管协作、真实撤权/恢复与物理Esc，不自动重跑；见[执行计划](plan-execution.md#集中真人步骤)。
