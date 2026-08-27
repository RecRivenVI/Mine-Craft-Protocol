# Automation

验证当前源码、Native 合同、Target 所有权和制品，而不是某个开发阶段的版本标签。

## 本地入口（不启动 Minecraft）

```powershell
pwsh -File components/validation_tools/conformance-automation/task/Invoke-AutomationLocalGate.ps1 -Offline
```

该入口构建五个实际 Target、验证 OpenAPI/生成模型，保留本项路由、类型与实现存在性断言。
制品身份取自 gradle.properties。仅在本轮已经完成构建时可用 `-SkipBuild`；
该选项不代表重新构建过。离线需要已有依赖缓存。

## Runtime 场景（另行授权）

[具名黑盒场景](../../components/validation_tools/conformance-automation/task/Invoke-Phase3AutomationConformance.ps1)保留实际参数，先阅读再执行。
需要有效 BaseUri、TokenFile、正确 Target 和对应 scope / Lease / Debug Arm，以及安全测试世界。
Server Peer 场景另需独立授权的服务端；无 Peer 时应拒绝不可用能力。
一次只保留一个普通客户端，不操作重要存档，不使用 CUA/宿主输入注入。
物理 Esc / 焦点 / 真人点击必须请求用户执行并由 Runtime/Audit 核对。

这些脚本中的保留编号是入口标识，不要求恢复旧源码或旧构建。
构建/静态不代替 Live；不同 Target 独立 PASS 不自动构成跨端 parity。
每次适用性、失败和未执行项均要按当次实际结果记录，不能从计划推导 PASS。

## 资产与证据

脚本由 components/validation_tools/ 承载；task/ 仅保存验证数据，instance/ 保存 ignored 验证实例，result/ 保存固定路径证据。
没有结果或实例就不创建占位目录。原生 JUnit/Node 测试留在源码体系。
PLAYTEST / FIXTURE / DEBUG_PRIVILEGED 不混用；不实现 Persistent Write 或越过当前授权范围。
当前状态见[执行计划](../../documents/project/plan-execution.md)。

## 目标

保留以下现有合同与验收边界，不以迁移后的构建替代历史或真人验收。

## 运行

执行前阅读以下既有前提；静态工具检查从仓库根目录运行。

```powershell
./gradlew.bat :validation_tools:check
```

## 通过标准

以本说明中的具体结果字段、日志与失败条件为准；未执行的 Live/人工场景必须保持未验收。
