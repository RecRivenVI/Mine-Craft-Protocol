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

执行前阅读以下既有前提；静态工具检查从仓库根目录运行。

```powershell
./gradlew.bat :validation_tools:check
pwsh -NoProfile -File components/validation_tools/regression-v1_release/task/Test-AlphaSnapshot.ps1
```

当前候选五端运行结果均已完成且源码匹配后，从仓库根刷新并发布；不以旧结果或未完成的运行补齐清单。

```powershell
node components/validation_tools/regression-v1_release/task/write-alpha-manifest.mjs
```

只完成专项时，使用生成器既有的 --scenario 参数，清单保持真实范围，不冒充 full 或独立制品运行。

## 通过标准

full清单还要求同候选五端现有完整UI套件（含未选中世界语义双击、生命图标MCP/网页消费者、实际进程/端点清理）及九项使用方流程。
UI证明逐项要求四类Screen卷轴、三项本地HUD字段的恢复/零输入、错误值/类型/旧版本拒绝，
以及同Target、实际进程和启动时间窗内的10项Loader内容守卫；启用卷轴及HUD字段均须有实际MCP DEBUG与网页USER隔离样本。
Forge固定石头配方无滚动范围时只验证0/拒绝移动，不虚构其MCP正向修改；不以旧最低样本数或恢复纯文字宣称富文本/计时已回滚。
使用方流程的五次当前帧、资源重载/维度/保存重进、Provider独立授权/超时无late effect与真实MCP必须有具体样本，旧44案不能补作新候选证据。
发行安装另要求公开Provider SPI类的真实加载来源及注册目录，不将目录可解析写成最终JAR已完成世界mutation。
清单分别记录开发接口流程、UI回归及独立安装范围，不宣称第三方渲染收敛或人工验收。

以本说明中的具体结果字段、日志与失败条件为准；未执行的 Live/人工场景必须保持未验收。
固定快照的五个 JAR、清单和手册须与同批生成文件逐字节一致，并复核清单中的 SHA-256。
合成分发测试覆盖源文件被覆盖、过期哈希、目录名冲突、重复发布、并发锁、使用登记、未知登记和回收失败；
任何不明状态都保守保留，淘汰仅进入回收站。合成测试不作为 Minecraft 制品运行证据。
