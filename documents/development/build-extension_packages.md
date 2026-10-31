# 构建独立扩展包

extension_packages 是 tool 组件，保存可独立交付的包作者工程，不进入产品 JAR。包只在本地审批后安装；公共 SDK 由 runtime_core 的 extensionSdkJar 生成。

## SDK 与平台探针

```powershell
./gradlew.bat :runtime_core:extensionSdkJar :extension_packages:assemble
```

平台探针从公开 SDK 编译，旧 Forge 的原生名称记录在本验证的 legacy-mapping.json 测试输入中；有映射构建产物时额外核对，实际成品 tick 门禁验证命中。没有 Loader 模组元数据，Target/Java 从仓库事实生成。当前样本包含 MinecraftServer tick 的只读 Mixin 计数、原生共享 helper、真实服务器 owner-thread 采样的 DEBUG Provider 与显式 start/stop，不修改世界、不代替玩家输入。构建产物位于该组件 build/packages/platform_probe，不自动安装或批准。

七个第三方适配包与平台探针分开；其实际进度见项目状态页。包内源码、依赖锁与测试共同交付，不能直接合入研究原型或宣称未测试版本受支持。

## 暂存加载库

普通包在安装和审批后重启即可；有早期 hook 的包还需在停机时生成派生库。当前受控命令只接受本验证的登记 Target、client/server 与 native/installed 布局，不对 Runtime 令牌开放路径或 shell。

```powershell
./gradlew.bat :extension_packages:stageValidationLibrary -PextensionTarget=26.3-neoforge -PextensionRole=client
```

验证 Runner 在持有实例锁、确认 Runtime 已关闭后调用此任务，将 pending 库替换为正式库；旧库送回收站。它不是远程安装 API。清单/内容身份变化必须重新审批、暂存并重启。Java SDK 可以跨版本编译，不表示原生 hook 和上游模组 API 可以跨 Target 通用。

## 普通实例的离线安装与移除

当前采用本地主机离线步骤，不提供远程安装或独立图形安装器。登记profile是暂存和验证工具的输入，不要求普通游戏目录包含验证探针、世界或开发classpath。

普通包将完整目录或ZIP放入已停止实例的minecraft-protocol/extensions/packages，并按[清单与审批格式](../reference/api-extension_platform.md#清单格式1)写入该实例的approvals.json。批准的是完整内容身份与scope，不是文件名；安装不授予Agent的游戏权限。重启后查询实际目录，不能以文件复制成功代替装载成功。

有早期hook时，先在已停止的登记暂存profile中放入完全相同的包集合与审批，再生成派生库；Target、物理运行端和宿主SDK批次必须与目的实例一致。派生库不绑定世界、token或实例路径，可将完整包集合、审批及生成的minecraft-protocol-extension-libraries.jar一并复制到目的实例，库放入mods。不要复制暂存实例的世界、token、配置或其他模组。复制前确认目的实例已正常停止、没有活动端点且instance.lock可独占；遇到占用或未知文件立即停止，旧包和旧派生库送回收站。复制后逐项核对清单索引及库的SHA-256，重启后再查目录/earlyHooks实际状态。主机文件权限与Runtime游戏授权分开，普通游戏token不能执行这些步骤。

更新或回滚必须停机并替换完整匹配集合，不能只换代码JAR或沿用旧审批。移除时同样先停机，将包与其审批条目移除、重新为剩余包暂存派生库；没有early hook后将旧派生库送回收站。保留token、世界、配置及其他模组。关闭注册不代表热卸载Mixin，只有重启后的目录和实际hook状态能确认移除结果。

## 受控主机与世界基线

Invoke-ExtensionHost 只接受登记的 Target、适配/fixture ID 和 native-client、installed-client、installed-server 三个 profile。它是本机操作系统授权的Windows工具，不接受游戏 token、任意路径或 shell。verify 复用现有启动与正常退出工作流；不是第二套进程管理器。独服平台与适配包使用 installed-server；各适配保存独立的世界基线并绑定上游输入哈希。非Windows可运行静态检查和SDK构建，主机启动/进程/回收流程明确拒绝，不伪装已经验证。

```powershell
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionHost.ps1 -Action list -Target 1.21.1-neoforge -Adapter ae2
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionHost.ps1 -Action prepare -Target 1.21.1-neoforge -Adapter ae2
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionHost.ps1 -Action verify -Target 1.21.1-neoforge -Adapter ae2
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionHost.ps1 -Action snapshot -Target 1.21.1-neoforge -Adapter ae2
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionHost.ps1 -Action restore -Target 1.21.1-neoforge -Adapter ae2
```

snapshot 只复制固定场景的已停止世界，不复制 token、日志或本机配置；基线按固定 ID 保存，存在时拒绝覆盖。restore 先核对 Target/模组版本、完整文件清单和 SHA-256，再将现有测试世界送回收站并恢复同一副本。路径中的链接、活动 Java/端点、实例锁或世界锁均拒绝，不自动杀进程或绕过锁。只作用于本验证的自有实例，不访问日常或 Prism 实例。
