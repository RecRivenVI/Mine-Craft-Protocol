# Packaged Runtime integration

共享 runtime-safety 的开发接线和最终 JAR 嵌入结构。Packaging Gate 不是最终制品 Loader Live 的替代；历史 packaged attestation 仅证明原绑定 SHA。

## 入口与真实前提

- [主要入口](../../components/validation_tools/integration-packaged_runtime/task/Invoke-Phase9D21PackagingGate.ps1)；同目录其他具名 conformance / live / static 脚本为该套件的分项，保留原参数。
- 从仓库根目录查看参数：

```powershell
Get-Help ./components/validation_tools/integration-packaged_runtime/task/Invoke-Phase9D21PackagingGate.ps1 -Full
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

验证五端最终发行JAR在全新游戏目录，仅安装官方Minecraft/Loader及这一份产品JAR即可启动；不引用Gradle开发类路径、sourceSets或手工补库。
旧端缺失Netty HTTP须以匹配版本Jar-in-Jar交付；26.1.2/26.3从原版运行库提供，不重复嵌入；Fabric既有必需API通过include交付。
静态包装与真实安装分别记录；历史提交结果、开发Runtime矩阵和当前JAR结构均不能替代新安装结果。

## 运行

执行前阅读以下既有前提；静态工具检查从仓库根目录运行。

```powershell
./gradlew.bat :validation_tools:check
pwsh -NoProfile -File components/validation_tools/integration-packaged_runtime/task/Invoke-Phase9D21PackagingGate.ps1 -SkipBuild
foreach ($target in @('26.3-neoforge','1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-fabric')) {
    pwsh -NoProfile -File components/validation_tools/integration-packaged_runtime/task/Invoke-InstalledRuntimeSmoke.ps1 -Target $target
    if ($LASTEXITCODE -ne 0) { throw "全新安装失败：$target" }
}
```

构建完成后再启动，默认串行、最大堆4 GiB、静音。工具从官方版本清单/Loader安装器准备运行库，原版缓存复用必须匹配官方SHA-1，
游戏类路径仅含验证自有安装目录。每次将自己的旧安装移入回收站，未知目录或运行中实例拒绝覆盖；不访问外部Prism/日常实例。
旧安装先核对owner、Target、父目录、Runtime锁及重解析点，再在同卷仓库旁的短临时路径原子重命名后回收，避免Shell长路径限制。
若回收仍失败，保留并打印旧副本路径，不永久删除；新实例使用原来的固定位置，旧副本不进入类路径。
清理阶段异常写为FAIL及具体stage，不遗留可误读的IN_PROGRESS。组件check包含实际长路径回收、锁/身份拒绝和故障保留测试。
当前结果固定写到result/Target.json，包含源码、实际JAR、工具、Java/Loader、类加载来源和清理结果，不按运行次数新建结果目录。

## 通过标准

五端各自status=PASS，发行JAR与当前源码匹配，freshGameDirectory=true、externalModCount=1、developmentClasspath=false；
到达无加载覆盖层的标题屏幕，endpoint身份/握手及USER读取成功，实际HTTP类和产品Transport已加载。
公开Provider V2接口、注册表和schema注册类须实际从安装布局加载，读取到四个既有owner descriptor；记录类来源，不依赖项目classpath。
这证明发行SPI装载与目录，不替代世界内Provider mutation实测，后者由对应的当前候选消费者验证记录。
通过正式语义Quit请求退出，自有进程退出码0、Vanilla Stopping日志、无崩溃报告、端点删除；
退出HTTP可能以503 RUNTIME_SHUTTING_DOWN及executionOutcome=not_confirmed、retrySafe=false报告请求被关闭取消，或连接先断；
保留原始结果，不盲重放，不将其称为成功RPC，不能接受内部HTTP500代替明确关闭状态；实际自然退出与响应分别判断。
打包结构、独立安装结果及源码/JAR绑定均通过后，才可刷新alpha清单与固定分发快照；发布工具再次校验结果哈希。
这不代表完整UI/世界目录、MCP全部功能、Peer或人工验收完成。
