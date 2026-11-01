# 构建独立扩展包

extensionpacks 是 tool 组件，保存可独立交付的包作者工程，不进入产品 JAR。包只在本地审批后安装；公共 SDK 由 runtime_core 的 extensionSdkJar 生成。

## SDK 与平台探针

```powershell
./gradlew.bat :runtime_core:extensionSdkJar :extensionpacks:assemble
```

平台探针从公开 SDK 编译，旧 Forge 的原生名称记录在本验证的 legacy-mapping.json 测试输入中；有映射构建产物时额外核对，实际成品 tick 门禁验证命中。没有 Loader 模组元数据，Target/Java 从仓库事实生成。当前样本包含 MinecraftServer tick 的只读 Mixin 计数、原生共享 helper、真实服务器 owner-thread 采样的 DEBUG Provider 与显式 start/stop，不修改世界、不代替玩家输入。构建产物位于该组件 build/fixtures/platform_probe，不自动安装或批准。

七个第三方扩展包与平台探针分开；其实际进度见项目状态页。包内源码、依赖锁与测试共同交付，不能直接合入研究原型或宣称未测试版本受支持。

## 暂存加载库

正式安装后，游戏会准备Loader发现桥；有early hook的包在重启发现阶段由当前产品JAR自动校验并生成派生库。Fabric在Mixin配置装载前处理。本节的停机暂存只服务开发source-set验证。当前受控命令只接受本验证的登记 Target、client/server 与 native/installed 布局，不对 Runtime 令牌开放路径或 shell。

```powershell
./gradlew.bat :extensionpacks:stageValidationLibrary -PextensionTarget=26.3-neoforge -PextensionRole=client
```

验证 Runner 在持有实例锁、确认 Runtime 已关闭后调用此任务，将 pending 库替换为正式库；旧库送回收站。它不是远程安装 API。清单/内容身份变化必须重新审批、暂存并重启。Java SDK 可以跨版本编译，不表示原生 hook 和上游模组 API 可以跨 Target 通用。

## 普通实例的离线安装与移除

包文件由本地主机安装；游戏的模组配置页提供原版资源包式扩展包选择，不是远程文件安装器。登记profile是暂存和验证工具的输入，不要求普通游戏目录包含验证探针、世界或开发classpath。

普通包将完整目录或ZIP放入已停止实例的extensionpacks/，由真人在游戏内初次启用，批准按[清单与审批格式](../reference/api-extension_platform.md#清单格式1)保存在mine_craft_protocol/extensions/approvals.json。批准的是完整内容身份与scope，不是文件名；安装不授予Agent的游戏权限。重启后查询实际目录，不能以文件复制成功代替装载成功。

包含早期hook的包也使用同一流程。首次正常启动会在mods生成小型Loader发现桥；之后每次启动由当前产品JAR核对已批准、已启用且依赖匹配的包，并在mine_craft_protocol/extensions/native生成派生加载库。Fabric在原生Mixin阶段准备；Forge/NeoForge使用派生发现桥。普通安装不执行开发暂存任务，也不手工复制派生库。启用、禁用或更新后必须正常重启，不能热卸载Mixin。

更新或回滚必须停机并替换完整包，内容身份变化需要真人重新批准；移除时停机将包送回收站。保留token、世界、配置及其他模组。关闭注册不代表热卸载Mixin，只有重启后的目录和实际hook状态能确认移除结果。派生发现桥代码随本体变更而不兼容时，Runtime会明确诊断并保留文件；停机后将该派生文件送回收站，再正常启动生成新桥，不能在游戏占用时覆盖它。

## 受控主机与世界基线

Invoke-ExtensionHost 只接受登记的 Target、适配/fixture ID 和 native-client、installed-client、installed-server 三个 profile。它是本机操作系统授权的Windows工具，不接受游戏 token、任意路径或 shell。verify 复用现有启动与正常退出工作流；不是第二套进程管理器。独服平台与扩展包使用 installed-server；各适配保存独立的世界基线并绑定上游输入哈希。非Windows可运行静态检查和SDK构建，主机启动/进程/回收流程明确拒绝，不伪装已经验证。

```powershell
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionHost.ps1 -Action list -Target 1.21.1-neoforge -Adapter ae2
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionHost.ps1 -Action prepare -Target 1.21.1-neoforge -Adapter ae2
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionHost.ps1 -Action verify -Target 1.21.1-neoforge -Adapter ae2
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionHost.ps1 -Action snapshot -Target 1.21.1-neoforge -Adapter ae2
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionHost.ps1 -Action restore -Target 1.21.1-neoforge -Adapter ae2
```

snapshot 只复制固定场景的已停止世界，不复制 token、日志或本机配置；基线按固定 ID 保存，存在时拒绝覆盖。restore 先核对 Target/模组版本、完整文件清单和 SHA-256，再将现有测试世界送回收站并恢复同一副本。路径中的链接、活动 Java/端点、实例锁或世界锁均拒绝，不自动杀进程或绕过锁。只作用于本验证的自有实例，不访问日常或 Prism 实例。
