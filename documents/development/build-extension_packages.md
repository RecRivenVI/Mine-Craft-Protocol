# 构建独立扩展包

extension_packages 是 tool 组件，保存可独立交付的包作者工程，不进入产品 JAR。包只在本地审批后安装；公共 SDK 由 runtime_core 的 extensionSdkJar 生成。

## SDK 与平台探针

```powershell
./gradlew.bat :runtime_core:extensionSdkJar :extension_packages:assemble
```

平台探针从公开 SDK 编译，没有 Loader 模组元数据，Target/Java 从仓库事实生成。当前样本暴露真实服务器 owner-thread 采样的 DEBUG Provider 与显式 start/stop，不修改世界、不代替玩家输入。构建产物位于该组件 build/packages/platform_probe，不自动安装或批准。

七个第三方适配包与平台探针分开；其实际进度见项目状态页。包内源码、依赖锁与测试共同交付，不能直接合入研究原型或宣称未测试版本受支持。
