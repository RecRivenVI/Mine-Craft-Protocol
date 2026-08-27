# Runtime Probe

## 配置接入后的原生客户端启动核验

```powershell
pwsh -File components/validation_tools/feature-runtime_probe/task/Invoke-ConfiguredClientStartup.ps1
```

仅在明确授权启动时执行。枚举 settings 注册的五端原生 runClient；跨项目使用
`Local\RavenTemplateClientSmoke` Mutex 一次一个客户端。用当次日志中的 Mod 初始化、
渲染资源完成与持续存活核验，不截图、不操作GUI、不进入世界或联网测试。
没有非UI优雅退出命令时只受控终止本次精确PID，确认进程/启动器退出；不能把该退出称为优雅关闭。
日志/进程矩阵留在 ignored build/configuration，必要长期结论独立记录。

[配置接入与五端启动记录](result/configuration-client-startup-20260910.json)包含当前工作树的自动测试、
每端 PID/JDK/日志及受控退出证据；不是完整 UI、世界、Server 或人工验收。

Phase 0 的最小 HTTP/UI/input/capture 技术样片；保留原边界，不补造 Phase 1 文档。

## 入口与真实前提

- [主要入口](../../components/validation_tools/feature-runtime_probe/task/Invoke-Phase0Conformance.ps1)；同目录其他具名 conformance / live / static 脚本为该套件的分项，保留原参数。
- 从仓库根目录查看参数：

```powershell
Get-Help ./components/validation_tools/feature-runtime_probe/task/Invoke-Phase0Conformance.ps1 -Full
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
