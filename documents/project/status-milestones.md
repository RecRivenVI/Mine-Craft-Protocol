# UI 与世界首个里程碑状态

UI、世界与完整 Showcase 均 **IN PROGRESS**，尚未 READY；READY 也不等于最终人工 PASS。
43号Goal已交付E的UI试用快照20261021-0006-90123ba7：32份开发运行、五份独立制品入世界及构建/检查/发行/严格合规通过。
E世界、M-01、M-09及F另开Goal；CF-022、第三方兼容和长期扩展不实施。真人步骤集中到末尾，未执行不标PASS。

恢复先读本页及[执行计划](plan-execution.md)；本Goal已收口，等待用户安排，不自动启动后续开发或真人验收。
本页只描述当前状态；历史批次/测量见[阶段索引](phase-1-core_delivery.md#2026-10-20状态页证据归档)，
本轮失败过程及局部证明见[E UI记录](phase-1-core_delivery.md#2026-10-20-e-ui参考端增量验证)。

## 本轮修复与UI进度

| 条目 | 实际验证 | 剩余边界 |
| --- | --- | --- |
| CF-017/018/072 首次后台转向 | 两个NeoForge使用方版本线修前FAIL、修后Live及当前首次后台转向PASS；五端输入上下文/持续输入通过 | 原版样本同源逻辑capture缺失；三使用方组合仍待各自复测 |
| CF-071 槽位别名 | AutomationEngine修前3失败、修后6项单测PASS；当前五端完整UI语义槽位回归通过 | Shift归属不变，已交付、待使用方复测 |
| CF-021 方块实体选择器 | 参考修前FAIL；当前五端各4项Live、正常退出，源码/样本绑定通过 | 不扩成CF-022分页，已交付、待使用方复测 |
| CF-019/023 HUD就绪/失败解释 | 修前2项FAIL；相关16项Java/HTTP/Companion及当前五端Native/MCP消费者通过 | 已交付、待使用方复测 |

转向修复仅解除实际游戏逻辑capture对宿主焦点的依赖，仍禁止Host capture/warp；未抢焦点、自动暂停、重放或直接写朝向。
聊天建议typed选择/负控/旧owner拒绝/恢复，以及真实MCP DEBUG与网页USER隔离已参考Live通过。

| 有限验收组 | 当前已证明 | 仍需完成 |
| --- | --- | --- |
| M-03 本地typed DEBUG | 五端完整all及三组接受器通过：建议/旁观选择、标题生命周期、页眉页脚、适用Toast及MCP/网页隔离 | 自动有限目录闭合；不代表服务器事务回滚或真人验收 |
| M-07/07d | 五端各6项：实际服务端权限/命令树撤销恢复、发送前拒绝、批次中止及MCP无Lease提交 | 自动范围闭合；真人协作、普通服务器权限变化及Esc仍待 |
| M-02 USER有限目录 | 五端导航、确认/恢复、床/适用坐骑、配方/快捷栏、编辑器、链接、自绘画布及管理页拒绝通过 | 外部服务未验；Native缺类不计操作，优化早完成不算Cancel，管理拒绝不算执行成功 |
| M-04 HUD/Toast | 五端H-02～10适用draw/显隐/生命周期及独立Demo通过；旧Forge采用实际atlas/UV | Forge默认Gui没有保存提示，不计draw PASS；本地packet非多人网络，原生draw非最终像素/第三方组合 |

Native Screen目录按Target顺序为122/131/141/145/145类，均已归类；静态目录及其历史Live关联不证明本候选完成全部操作，外部服务与非交互过渡另行区分。
有限UI功能证据来自开发Runtime；最终JAR独立安装只证明正常Loader加载、握手、USER读取、进世界与退出，不冒充制品全UI或人工验收。
静态目录/类存在不代替逐端实际验收；仅按批准的有限目录及明确限制收口，不外推未测业务组合、第三方或最终像素。

## 已发布基线与本轮分发

当前可复制快照为 **20261021-0006-90123ba7**，位于 D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/。
五JAR、alpha-manifest.json、AGENT_GUIDE.md同批七文件及latest独立审计PASS；其他线程复制固定快照，不直接引用build/。
快照不可覆盖，保留最近三份及登记在用批次；0337-f2dc15c9保留为试用基线。
**本轮已发布，产品冻结**：747份产品/构建事实，指纹90123ba7d178dcdc0012a93c12c6da778ce101d04b0ce75355bf3210ace21dff。
最终开发32/32、独立单JAR正常Loader入世界5/5及来源/样本/制品审计通过；五端完整UI为126/130/138/139/139项。最终spotlessApply/build/check/verifyRelease/严格合规通过，2099文件失败0/警告0。七文件与build逐字节/哈希一致，latest指向完整快照，五份安装均exit0、endpoint移除；不以开发classpath替代安装，不重跑未受影响的历史65场景。
修复涉及旧端菜单延迟提交/强清理、Toast DEBUG字段投影、Companion安全端口和工具/探针发布；实际失败与局部证明均在阶段索引，不在本页累积流水。
参考端一次SDL3原生启动异常及26.1.2一次HUD probe写文件失败复试未再现，原失败保留，触发原因仍未确认；不宣称任意启动或文件系统稳定性已修复。
0422/f2dc仅作已发布或历史基线，不替代本候选37份证明。官方安装首次Guava下载TLS失败，未进入游戏；原样复试及后续五端通过，失败日志保留。
本批profile=e_ui_trial_v1、humanAcceptance=NOT_PERFORMED、fullMilestonesComplete=false；试用手册列明七个修复编号及历史性能NOT_MET，不宣称使用方组合或完整领域通过。

| 已交付项 | 证明与边界 |
| --- | --- |
| f2dc试用矩阵 | 指纹f2dc15c927ca7bf77dd7a0ffee52e605718366d4e16b1433319a24101fdcadf3，612份产品/构建事实；65场景位：63份原矩阵PASS，加Fabric一份九项功能证明覆盖performance/baseline两位，后两位性能DEFERRED，不是65份性能PASS |
| 参考真人Esc | active与两项queued取消、held=0、8070ms无迟到，后三次请求要求重新同意；仅历史参考证明，不是五端物理输入/视觉验收 |
| Linux/检查点 | Check 39313898839、Specification 39313896580均success；398f72ea9ed0efadbdfdf3a3cf61e7e55b5888e1已SSH签名、日期相等、Update无正文并推送develop同步，此后不再提交/推送 |
| 0422依赖更新 | 指纹52bc024f38e8a93e39fb0059b5e097725e079394554b2861d9de99a8b40a06df，612份事实；五端当前JAR独立安装进世界、CLIENT_KNOWN USER、同Lease、held=0、保存退出、exit0及endpoint移除PASS；旧65场景仅历史来源 |
| 构建/依赖 | 0422批次spotlessApply/check/verifyRelease/严格合规PASS，失败0/警告0；NeoForge 21.1.264、26.1.2.122、26.3.0.94，Fabric API 0.164.0+26.3，Loom1.19.1、Zod4.6.7；组件/Companion检查PASS、npm审计0 |

**本轮不做新性能测量，旧门槛不作为完成条件。**
历史三次攻击/单次p50比值2.74655、2.61862、2.69870仍为 **NOT_MET**，不删40ms hold、原版冷却或GUI节奏凑比值。
原生攻击消费不是伤害，客户端预测/USER回读不是服务器确认；held=0、取消/无迟到、权限和截图隔离要求不放宽。

## 双里程碑五端矩阵

UI交付Screen/Menu/Slot、自绘内容、HUD/Toast的USER语义读取/真实输入及批准typed DEBUG；世界交付有界客户端模型、有限WU/WD、命令、反馈及适用部署。
范围见[UI目录](../development/testing-vanilla_ui.md)、[世界目录](../design/concept-world_control.md)、[部署设计](../design/concept-deployment_scenarios.md)，不扩为所有参数组合。

P为仍有必需实现/覆盖，V为本包实现/有限自动验收已具备、共同交付或真人仍待，均非领域READY。
剩余数为可独立关闭的验收组，非缺陷数/测试数/本Goal场景位；共41组，含实现、夹具、交付及真人。0只表示本包没有剩余独立组，共同最终验收计M-10，不重复计数。
估算为工程工时非日历承诺；M-08/M-11/M-12中置信，其余低置信，M-09待产物决策重估。

| 工作包 | 1.20.1 Forge | 1.21.1 NeoForge | 26.1.2 NeoForge | 26.3 NeoForge | 26.3 Fabric | 剩余必需项数 | 粗估工作量 | 完成条件与最近证据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M-01 分类/权限 | P | P | P | P | P | 8 | 12～24小时 | 01c～01j：Peer、全链读取、非接管/撤销/协作、诊断/回读/订阅；01a/b及五端新准入8项保留 |
| M-02 UI USER | V | V | V | V | V | 0 | 自动组已闭合 | 02a～g有限目录五端当前all通过；外部服务/Native缺类及拒绝边界明确，共同交付仍待 |
| M-03 UI DEBUG | V | V | V | V | V | 0 | 自动组已闭合 | 03c含建议消费者、合法本地typed及MCP/网页隔离已五端整组通过，不是最终人工PASS |
| M-04 HUD/Toast | V | V | V | V | V | 0 | 自动组已闭合 | 04a～i/H-02～10适用原生draw及生命周期已五端通过；不外推最终像素或真实多人网络 |
| M-05 世界 USER | P | P | P | P | P | 8 | 18～32小时 | 05a～h：WU-01～20剩余移动、物品、方块、战斗、实体/生命周期及区域；既有67流程非完整目录 |
| M-06 世界 DEBUG | P | P | P | P | P | 8 | 12～22小时 | 06a～h：WD状态/槽位/生命周期/环境/关系/批次/转移/Provider；06i六类版本链五端14项/2MCP已闭合 |
| M-07 命令 | V | V | V | V | V | 1 | 1～3小时 | 07d自动权限变化五端各6项已通过；剩真人协作/普通服务器权限变化/Esc，不宣称真人并发Live |
| M-08 固定副本 | P | P | P | P | P | 2 | 2～4小时 | 08a恢复哈希/锁/凭据；08b三部署/零玩家配置，不无故重建基准 |
| M-09 三部署/Peer | P·阻塞 | P·阻塞 | P·阻塞 | P·阻塞 | P·阻塞 | 6 | 决策后重估 | 09a～f：产物/主体、零玩家Runtime、控制台、资源路由/消费者；有限Peer非正式支持 |
| M-10 回归/制品 | P | P | P | P | P | 4 | 8～16小时 | 10a领域矩阵、10b独立安装、10c取消/证据、10d连续正确性；基线8项保留，性能暂缓 |
| M-11 展示 | P | P | P | P | P | 2 | 2～5小时 | 11d协作/连续操作、11e三部署展示；11a/b/c既有17项基础保留，真人体验独立 |
| M-12 交付 | V | V | V | V | V | 2 | 1～3小时 | 12a目录/限制/手册审计，12b同批alpha七文件/固定分发；V非完整READY |

READY顺序：M-02/03/04有限自动组已闭合，M-07人工仍待；本Goal先完成受影响交付门禁和试用分发，不反复增加边界。
M-05/06广度、M-01协作、M-09部署另开Goal，之后完成M-08部署夹具及M-10/11/12完整交付。
M-09阻塞未解除，两个完整里程碑没有可信READY日期；A～D试用和依赖更新已交付，不冒充本轮证明。

## 合同与已知限制

- READ只读；OPERATE不接管真人键鼠，可执行获准原生命令/DEBUG并与真人并行；TAKEOVER持既有Lease接管。
  各模式独立授权USER/DEBUG读取，模式不授予权限；scope、Arm、前置值、Human Override和Evidence正交，不自动升降级回退。
- UI USER仅实际呈现；世界USER为有界客户端缓存，不自动加载区块、读存档或开放服务端内部数据。
  命令统一USER，原生可用性非授权/执行成功；Esc只撤销接管并要求重新同意，合法独立DEBUG是否继续由模型结合用户决定。
- M-09保持mod_side=both：模板不能表达所需可选单端/双端产物，不以可选网络通道冒充仅客户端安装支持。
  零玩家Runtime/控制台未实现，Peer依赖在线ServerPlayer且有限；三部署仍属双里程碑，不阻塞本Goal，也不擅自实施。
- 第三方Sodium等自绘UI有语义缺口，授权后截图/坐标可回退，不宣称自动适配；第三方/世界需求不扩入原版E UI。
  模板固定2.0.2，Minecraft/依赖保持检查点事实，本Goal不再升级或更新对照地图/夹具。
- 自动测试1920×1080、静音、4GiB、一次一个自有隔离客户端；禁止Computer-Use、宿主输入注入、外部Prism及重要存档操作。
  **统一人工验收未执行，Persistent Write调用0**；不启动后续Phase、E1/E2/E3、Fake Player或第二界面实施。

## 使用方反馈分诊

19条：接收11条（缺陷5、缺口6）、待补信息1条、范围外7条；未直接关闭为调用方/环境问题。
责任、来源及原始推断见[阶段分诊](phase-1-core_delivery.md#2026-10-20接收反馈分诊与后台相机最小复现)，使用方未复测不宣称其组合已通过。

| 归位 | 编号 | 当前处理 |
| --- | --- | --- |
| 本Goal优先缺陷 | CF-017/018/072、CF-071、CF-021 | 已交付、待使用方复测；修前/修后及当前受影响五端证明见首表 |
| 本Goal E UI | CF-019、CF-023 | 已交付、待使用方复测；Native/MCP及五端当前回归通过 |
| E世界另开Goal | CF-022 | 有界DEBUG方块实体分页未实现 |
| 第三方兼容后续 | CF-015、CF-064、CF-056 | RRO/Modern UI语义及渲染阶段组合缺口，不扩入原版E UI |
| 待补信息 | CF-024 | 需同批Target、玩家权限、discover/suggest原始回执 |
| 范围外 | CF-016、CF-025、CF-063、CF-065～068 | 未支持Target、受限tick、扩展平台/宿主工具，未接收为当前必需项 |

CF-056初判Radiance移植渲染阶段适配为主、双方协作：末帧Operator UI与其私有GUI scope不匹配。
不改其纯原版阶段、不转发线程，不关闭Chrome/截图隔离绕过；本项目原版修复不证明该组合已修。

## 证据入口与下三步

当前结果依[世界验证](../../validations/conformance-world_read/validation.md)、[UI验证](../../validations/conformance-vanilla_ui/validation.md)
及[发行验证](../../validations/regression-v1_release/validation.md)核验实际依赖；历史详情留在阶段记录。
分发按[固定快照工作流](../development/workflow-alpha_agent.md#固定分发与使用登记)，Showcase见[展示验证](../../validations/feature-control_ui_showcase/validation.md)，不自动运行全套。

1. 等待用户/外部评审安排使用方复测七个修复编号，不主动通知其他线程。
2. 用户决定执行时，用同批26.3-neoforge按执行计划完成集中人工清单；当前未开始，普通服务器前置不足则保留待验。
3. 后续E世界、M-01/M-09及F由用户另定Goal；本Goal停止，不再提交/推送，Persistent Write保持0。
