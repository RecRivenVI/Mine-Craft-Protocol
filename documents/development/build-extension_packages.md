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
