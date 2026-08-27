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

## 运行

执行前阅读以下既有前提；静态工具检查从仓库根目录运行。

```powershell
./gradlew.bat :validation_tools:check
```

## 通过标准

以本说明中的具体结果字段、日志与失败条件为准；未执行的 Live/人工场景必须保持未验收。
