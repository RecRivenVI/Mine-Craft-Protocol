# 扩展平台集成验证

## 目标

验证正式 SDK 独立包的实际加载、内容审批、静态目录与文档、Provider 读取/typed Debug 和受控采样清理。开发运行与最终单 JAR、早期 Mixin、七模组业务及人工验收分别报告，不复用研究证明。

## 运行

从仓库根目录执行。Target 参数选择已登记目标，当前参考端为26.3-neoforge；需要正确Java/Loader缓存、Node、Companion依赖及本机EULA授权。

```powershell
./gradlew.bat :extension_packages:assemble :companion:assemble
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 26.3-neoforge
```

Runner 只使用本验证的隔离实例，1920×1080静音、最大4GiB，以本地测试授权安装固定探针包。所有GUI/命令/Debug操作走正式接口，正常退出并确认端点和端口释放。禁止Computer-Use，不访问Prism、重要存档或日常实例。

## 通过标准

每个已运行Target的固定result/Target-client.json记录当批源码/包身份与实际Runtime，目录为active，公开SDK共享类身份成立；真实Provider返回owner线程采样，未Arm/READ修改被拒绝，Arm显式start/stop与到期均清理、无Lease副作用。MCP静态资源可查阅，采样不是玩家玩法证据。输入归零、正常关闭、无崩溃和Persistent Write调用0。

没有运行的Target或最终发行装载不得写为PASS；早期hook、七模组及独立服务端门禁由当前平台计划进一步收口。
