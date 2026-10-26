# V1 release regression

## Exact-commit evidence contract

Formal remote evidence must contain `sourceCommit`, `originCommit`, `workingTreeClean`,
`gateVersion`, `timestamp`, critical Git source hashes and five artifact hashes.
The gate fetches its actual remote source, verifies a clean detached worktree,
builds and checks that source, and binds the output to that commit. A working-tree
test or previously retained result cannot substitute for this attestation.
No documentary PASS is an input to current release acceptance.

Phase 8 安全、取消、EventHub、Recording、审计和发布回归综合套件。RemoteParity 子门禁比较 exact commit / artifact，历史 JSON 不回写。

## 入口与真实前提

- [主要入口](../../components/validation_tools/regression-v1_release/task/Invoke-Phase8LocalGate.ps1)；同目录其他具名 conformance / live / static 脚本为该套件的分项，保留原参数。
- 从仓库根目录查看参数：

```powershell
Get-Help ./components/validation_tools/regression-v1_release/task/Invoke-Phase8LocalGate.ps1 -Full
```

本地构建/静态脚本需要对应 Java/Gradle、已安装的 Companion 依赖和可用依赖缓存。
带 BaseUri/TokenFile 等参数的 Live 子项需要已经授权的独立测试实例及有效令牌；按每个脚本实际参数配置。
先阅读所选入口再执行：Local/Gate 可能调用构建、审计或其他子项，不能将“本地”理解为无副作用。
一次只运行一个普通客户端。不得操作重要存档，不使用 CUA/宿主输入注入。
物理 Esc / 焦点 / 真人点击必须请求用户执行并由 Runtime/Audit 核对。

## 判定与资产

按原脚本断言判断 PASS/FAIL/PARTIAL；构建、静态、真实运行、人工验收分开记录。
跨 Target 使用同一合同不自动证明 parity，只有实际比较的子项才可作一致性证据。
`task/` 保存验证数据，脚本位于 components/validation_tools/；`instance/` 是可选 ignored 隔离数据；`result/` 只收长期证据。
没有现成结果/实例就不创建占位目录。原生源码测试仍在各组件/Target。
结果只对其绑定提交有效；未执行的 Minecraft Live/人工验收必须标为未执行。

当前执行安排见[执行台账](../../documents/project/plan-execution.md)。后续能力必须另行授权，不能从测试入口自动开启。

## 目标

保留以下现有合同与验收边界，不以迁移后的构建替代历史或真人验收。
alpha 刷新须验证当前源码、实际五端运行结果、发行 JAR 与生成手册一致，再发布固定的七文件快照。
分发目录及使用登记见[alpha Agent 工作流](../../documents/development/workflow-alpha_agent.md)。
已发布目录不可覆盖；latest.txt 只指向完整快照，保留最近三份及显式登记在用的批次。

## 运行

E世界试用使用e_world_trial_v1，M-05/M-06的实际适用验收组和受影响UI/连续输入须在冻结候选重新执行；每组定义在e-world-proof.mjs。不接受尾部调试场景替代完整组。五端最终单JAR另需独立安装入世界，人工及历史性能不在自动通过范围。

```powershell
node components/validation_tools/regression-v1_release/task/write-alpha-manifest.mjs --profile=e_world_trial_v1 --scenario=e-world-affected --historical-baseline=D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/20261020-0337-f2dc15c9
```

该入口会生成并发布七文件快照，不是只检查命令；只有既定范围全部真实通过后执行。

执行前阅读以下既有前提；静态工具检查从仓库根目录运行。

```powershell
./gradlew.bat :validation_tools:check
pwsh -NoProfile -File components/validation_tools/regression-v1_release/task/Test-AlphaSnapshot.ps1
```

当前候选五端运行结果均已完成且源码匹配后，从仓库根刷新并发布；不以旧结果或未完成的运行补齐清单。

```powershell
node components/validation_tools/regression-v1_release/task/write-alpha-manifest.mjs --scenario=full --profile=continuous_gameplay_trial_v1
```

此命令在校验后直接调用固定快照发布器，不是只生成文件。上述profile要求完整连续游玩试用回归；
历史快照保持原样，不按新profile回写。普通专项结果不能据其刷新alpha。

本轮依赖更新使用单独、显式的有界profile。先完成当前五端单发行JAR安装入世界smoke，再执行：

```powershell
node components/validation_tools/regression-v1_release/task/write-alpha-manifest.mjs --profile=dependency_update_trial_v1 --scenario=packaged-runtime --dependency-baseline=D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/20261020-0337-f2dc15c9
```

该profile严格绑定已封存基线的七文件与612份产品/构建事实，只允许本轮三个NeoForge、Fabric API、Loom事实及Zod包/锁变化。
产品Java、资源、协议、游戏版本与最低Loader要求均须不变；任何行为源码变化均拒绝，不以此绕过相应回归。
五端安装必须重新绑定当前源码/JAR、USER入世界、同Lease与干净退出，标题页证明不足以发布。
历史65场景只作基线范围索引，清单明确fullNativeSmoke=false、fullMilestonesComplete=false；
NOT_MET仅为历史测量，本轮无新性能测量，不宣称新候选完整Native/MCP或人工验收。

E的UI试用使用`e_ui_trial_v1`：冻结候选上各端完整UI、独立Demo、input-context、continuous-input、
block-entity-selector和command-collaboration，以及1.21.1/26.3 NeoForge的first-camera，共32份开发运行结果；
另需五端当前单发行JAR独立安装入世界，共37份证据。旧65场景仅作历史来源，不要求重跑未受影响的世界组。
全部原始结果、实际消费源码和发行JAR一致后执行：

```powershell
node components/validation_tools/regression-v1_release/task/write-alpha-manifest.mjs --profile=e_ui_trial_v1 --scenario=e-ui-affected --historical-baseline=D:/Workspaces/Artifacts/MineCraftProtocol/Alpha/20261020-0337-f2dc15c9
```

该入口不接受局部CheckPrefix结果替代完整UI，不把Forge未注册的保存提示或旧版本不存在的Native页面计为绘制通过。
人工协作、真实权限变化、物理Esc和最终视觉体验保持NOT_PERFORMED，集中交用户执行；完整双里程碑仍不因此READY。

## 通过标准

full清单还要求同候选五端现有完整UI套件（含未选中世界语义双击、生命图标MCP/网页消费者、实际进程/端点清理）及九项使用方流程。
UI证明逐项要求四类Screen卷轴、三项本地HUD字段的恢复/零输入、错误值/类型/旧版本拒绝，
以及同Target、实际进程和启动时间窗内的10项Loader内容守卫；启用卷轴及HUD字段均须有实际MCP DEBUG与网页USER隔离样本。
Forge固定石头配方无滚动范围时只验证0/拒绝移动，不虚构其MCP正向修改；不以旧最低样本数或恢复纯文字宣称富文本/计时已回滚。
使用方流程的五次当前帧、资源重载/维度/保存重进、Provider独立授权/超时无late effect与真实MCP必须有具体样本，旧44案不能补作新候选证据。
发行安装另要求公开Provider SPI类的真实加载来源及注册目录，不将目录可解析写成最终JAR已完成世界mutation。
清单分别记录开发接口流程、UI回归及独立安装范围，不宣称第三方渲染收敛或人工验收。

连续游玩试用另绑定五端各input-context、region-stream、world-placement-receipts、continuous-performance和interaction-baseline，
以及既有continuous-input和四项协作专项，共65个场景位。2026-10-20用户暂停新性能测量：保留四端既有性能/基线原始结果，
Fabric仅以continuous-functional的单次/三连原生消费、单步输入、两槽hover和取消清理关闭对应两个功能分支；性能明确DEFERRED，
实际为63份原矩阵结果与1份覆盖两功能分支的补充原始结果，不宣称65份性能PASS。
既有区域样本保留真实MCP20次读取，四端既有GUI基线保留20次实际语义悬停；不能以两槽功能验证伪装GUI20。
三次攻击使用与单次相同的40ms hold并核对原生调用计数；用户允许实测比值NOT_MET交付试用，但必须保留performanceComplete=false，
不声称性能目标或完整里程碑通过。物理Esc由用户执行并另记，自动结果中的physicalEscTested=false不得改写成真人通过。

以本说明中的具体结果字段、日志与失败条件为准；未执行的 Live/人工场景必须保持未验收。
固定快照的五个 JAR、清单和手册须与同批生成文件逐字节一致，并复核清单中的 SHA-256。
合成分发测试覆盖源文件被覆盖、过期哈希、目录名冲突、重复发布、并发锁、使用登记、未知登记和回收失败；
任何不明状态都保守保留，淘汰仅进入回收站。合成测试不作为 Minecraft 制品运行证据。
源码校验按实际依赖：世界入口仅在world-placement-receipts分支执行放置断言模块，其他场景不因该未执行模块的断言修订失效。
产品、事实、公共入口与其他实际工具仍逐字节校验；原始结果及其SHA不改写，不以此忽略真实输入或产品变更。
