# UI 与世界首个里程碑状态

UI、世界与完整 Showcase 均 **IN PROGRESS**，尚未 READY；READY 也不等于最终人工 PASS。
当前 Goal 的A～D试用快照、唯一检查点、Linux CI及六项依赖更新已完成；更新候选五端独立安装进入世界smoke、最后仓库检查与更新快照发布全部完成，交用户试用后停止。
E/F、完整三部署和后续使用方反馈不在本 Goal 实施，但仍属于双里程碑后续计划。

恢复工作先读本页、[执行计划](plan-execution.md)，以及40号指令第一至五节和“六、Mine-Craft-Protocol”。
本页只描述当前状态；历史批次、逐项反馈与测量见[阶段证据索引](phase-1-core_delivery.md#2026-10-20状态页证据归档)。

## 当前候选与分发

冻结试用候选为 `f2dc15c927ca7bf77dd7a0ffee52e605718366d4e16b1433319a24101fdcadf3`，612份产品/构建事实文件。

冻结候选的 **65个场景位已完成**：63份原矩阵PASS，加一份Fabric九项功能证明覆盖原performance/baseline两个场景位；五端现有完整UI/世界与独立单JAR安装齐备。
后两项的功能通过，性能明确 **DEFERRED**；这不是65份性能PASS，也没有开展新性能测量。

参考端真人 Esc 已证明 active及两项queued均取消、held=0、8070ms无迟到，三次请求持续要求重新同意。
原始失败保留；这不是五端物理输入或视觉验收。原生消费、客户端预测与USER回读分开，不伪造服务器确认。

当前可复制快照为 `20261020-0422-52bc024f`，位于 `D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/`；七文件一致性及latest指向的独立审计PASS。
清单、Agent手册及[连续游玩试用说明](../development/workflow-alpha_agent.md#连续游玩试用)已发布。其他线程只复制固定七文件，不直接引用 `build/`。
发布快照不可覆盖，保留最近三份及明确登记在用的批次；`20261020-0337-f2dc15c9`保留为冻结试用基线。

更新候选为 `52bc024f38e8a93e39fb0059b5e097725e079394554b2861d9de99a8b40a06df`，仍为612份源码/构建事实。
五端绑定同一最终版工具，独立安装进入世界全部PASS：CLIENT_KNOWN USER读取、同Lease、held=0、正常Save & Quit、退出码0及endpoint移除均已确认。
这是依赖更新的有界smoke，不是重跑旧65场景；新清单将旧矩阵明确标为历史基线，不冒充依赖更新后的完整回归。
最后spotlessApply、check、verifyRelease及严格合规通过，合规失败0、警告0；更新七文件已发布，latest已切换且独立审计通过。

## 本 Goal 的交付结果

| 项目 | 当前状态 | 完成条件 |
| --- | --- | --- |
| 冻结试用矩阵 | 完成65场景位 | 63份原矩阵PASS及Fabric一份九项功能证明；后者覆盖两个场景位，性能DEFERRED，不冒称性能PASS |
| 连续游玩试用分发 | 完成 | 20261020-0337-f2dc15c9七文件及试用说明已发布；当前latest已更新为下方依赖更新快照 |
| Linux清理测试 | 本机及真实Linux CI完成 | Check 39313898839、Specification 39313896580均success；按平台分支及单测保留，不跳过或吞异常 |
| 唯一检查点 | 完成 | ccbd2c8b9293faaace7de0b3ecc0a09eaf21e08a已SSH签名、作者/提交日期相等，正常push develop并同步；提交175份源码/测试/工具/文档，78份raw results未纳入 |
| 兼容依赖更新 | 完成；五端构建及独立安装进入世界PASS | NeoForge 21.1.264、26.1.2.122、26.3.0.94，Fabric API 0.164.0+26.3，Loom 1.19.1，Zod 4.6.7；三产品组件及Companion检查PASS，npm审计0 |
| 更新后分发 | 完成 | 20261020-0422-52bc024f同批七文件已发布；publisher无警告，latest及六份载荷哈希独立审计PASS；依赖更新后的工具、文档和证据按约定保留未提交 |

**性能检测本轮暂缓，不做新测量，旧性能门槛不作为本轮完成条件。**
三次攻击耗时/单次p50的既有2.74655、2.61862、2.69870仍为 **NOT_MET**，不改写成PASS。
既有测量只保留为历史；原生消费、输入持有与取消、held=0、无迟到、权限和截图隔离的正确性要求不放宽。
不删除40ms hold、原版冷却或GUI观看节奏来凑比值。

## 双里程碑五端矩阵

UI交付原版Screen/Menu/Slot、自绘内容、HUD/Toast的USER语义读取/真实输入及批准typed DEBUG；世界交付有界客户端模型、有限WU/WD目录、命令、反馈与适用部署。
范围由[UI目录](../development/testing-vanilla_ui.md)、[世界目录](../design/concept-world_control.md)和[部署设计](../design/concept-deployment_scenarios.md)定义，不扩成所有参数组合。

P为仍有必需实现/覆盖；V为实现待最终交付验证，均不是READY。
剩余数是可独立关闭的验收组，不是缺陷数、测试数或本Goal场景位；共58组，其中实现/覆盖50组，夹具及交付8组。
估算是工程工时，不是日历承诺；除M-08/M-11/M-12中置信外均低置信，M-09待产物决策后重估。

| 工作包 | 1.20.1 Forge | 1.21.1 NeoForge | 26.1.2 NeoForge | 26.3 NeoForge | 26.3 Fabric | 剩余必需项数 | 粗估工作量 | 完成条件与最近证据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M-01 分类/权限 | P | P | P | P | P | 8 | 12～24小时 | 01c～01j：Peer、全链读取、非接管/撤销/协作、诊断/回读/订阅；01a/b及五端新准入8项保留 |
| M-02 UI USER | P | P | P | P | P | 7 | 8～16小时 | 02a～g：世界菜单、创建、配方/创造、文本、画布、特殊服务页与完整目录；既有UI65/66/66/67/67流程非全覆盖 |
| M-03 UI DEBUG | P | P | P | P | P | 1 | 3～8小时 | 03c：特殊菜单/自绘/HUD/Toast合法字段与操作；03a/b/d闭合，画布3分支和部分Toast已实测 |
| M-04 HUD/Toast | P | P | P | P | P | 9 | 10～18小时 | 04a～i对应H-02～10：物品/生命/上下文/准星/计分板/效果/多人/文字/Toast；不逐纹理无限深化 |
| M-05 世界 USER | P | P | P | P | P | 8 | 18～32小时 | 05a～h：WU-01～20剩余移动、物品、方块、战斗、实体/生命周期及区域；既有67流程非完整目录 |
| M-06 世界 DEBUG | P | P | P | P | P | 8 | 12～22小时 | 06a～h：WD状态/槽位/生命周期/环境/关系/批次/转移/Provider；06i六类版本链五端14项/2MCP已闭合 |
| M-07 命令 | P | P | P | P | P | 1 | 2～5小时 | 07d：真人协作/权限变化/Esc；07c补全各8项及07e最多32条批次闭合，不宣称真人并发Live |
| M-08 固定副本 | P | P | P | P | P | 2 | 2～4小时 | 08a最终恢复哈希/锁/凭据；08b三部署/零玩家配置，不无故重建基准 |
| M-09 三部署/Peer | P·阻塞 | P·阻塞 | P·阻塞 | P·阻塞 | P·阻塞 | 6 | 决策后重估 | 09a～f：产物/主体、零玩家Runtime、控制台、资源路由和消费者；有限Peer不代替正式支持 |
| M-10 回归/制品 | P | P | P | P | P | 4 | 8～16小时 | 10a领域矩阵、10b独立安装、10c取消/证据、10d连续正确性；基线8项保留，性能部分本轮暂缓 |
| M-11 展示 | P | P | P | P | P | 2 | 2～5小时 | 11d协作/连续操作、11e三部署展示；11a/b/c既有17项基础保留，真人体验独立 |
| M-12 交付 | V | V | V | V | V | 2 | 1～3小时 | 12a全目录/限制/手册审计，12b同批alpha七文件与固定分发；V不是完整READY |

本 Goal 不以改计数或反复增加边界关闭这些包。优先完成A～D试用与更新快照，再按用户试用反馈另开E/F：
先收最接近闭合的M-03/07分支，再完成M-02/04及M-05/06广度和M-01协作；M-09解决产物依赖后补M-08部署夹具，最后M-10/11/12统一交付。
M-09阻塞尚未解除，因此没有可信的两个完整里程碑READY日期。

## 合同与已知限制

- READ只读；OPERATE不接管真人键鼠，允许获准的原生命令/DEBUG与真人并行；TAKEOVER持既有Lease接管。
  各模式可独立授权USER/DEBUG读取；scope、Arm、前置值、Human Override及Evidence正交，不自动升级或降级回退。
- UI USER只读实际呈现；世界USER为有界客户端缓存，不自动加载区块、读存档或开放服务端内部数据。命令统一USER，原生可用性不是授权或执行成功。
  Esc只终止接管并保留重新同意提示；独立合法DEBUG是否继续由模型结合用户决定处理。
- M-09保留 `mod_side=both` 合同：模板当前不能表达本项目可选单端/双端产物，不能以可选网络通道冒充仅客户端安装支持。
  零玩家Runtime/控制台未实现；Peer依赖在线ServerPlayer且能力有限。三部署仍是双里程碑目标，不阻塞本次A～D切片，也不在本Goal擅自实施。
- 第三方Sodium等自绘UI仍有语义覆盖缺口，授权后的截图/坐标可回退，不宣称自动第三方适配。使用方新转交的19条已逐项分诊，见下节；本轮不实施修复或扩大已完成Goal。
- 模板固定2.0.2，Minecraft版本不变；本轮仅更新兼容的最新稳定Loader/Fabric API/第三方模组与库。必须改模板的更新项跳过并记需求，地图/夹具等对照输入不更新。
- 自动测试沿用1920×1080、静音、4GiB、一次一个自有隔离客户端；不重做已落实的实例约定。禁止Computer-Use、宿主输入注入、外部Prism及重要存档操作。
  Persistent Write调用保持0；不启动E1/E2/E3、后续Phase、Fake Player或第二界面实施。统一人工验收尚未执行。

## 使用方反馈分诊

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

## 证据入口与下三步

[阶段证据索引](phase-1-core_delivery.md#2026-10-20状态页证据归档)保存历史批次、反馈分诊和既有测量；
当前结果按[世界验证](../../validations/conformance-world_read/validation.md)、[UI验证](../../validations/conformance-vanilla_ui/validation.md)
及[发行验证](../../validations/regression-v1_release/validation.md)核验实际依赖。
发布遵循[固定快照工作流](../development/workflow-alpha_agent.md#固定分发与使用登记)，Showcase见[展示验证](../../validations/feature-control_ui_showcase/validation.md)，不自动运行全套展示。

1. 交用户及使用方复制20261020-0422-52bc024f固定快照，按同批Agent手册试用；完整领域和统一人工验收尚未进行。
2. 19条新增反馈已完成分诊；等待用户决定是否另开E/F或第三方兼容Goal，不自动实施上述建议或开启三部署实现。
3. 本轮工作完成后停止；不进行第二次提交/推送、新性能检测或人工验收，Persistent Write保持0。
