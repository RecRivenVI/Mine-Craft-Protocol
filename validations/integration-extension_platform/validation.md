# 扩展平台集成验证

## 目标

验证正式 SDK 独立包的实际加载、内容审批、静态目录与文档、Provider 读取/typed Debug 和受控采样清理。开发运行、最终单 JAR、早期 Mixin、独立服务器、七模组业务及人工验收分别报告，不复用研究证明。

## 运行

从仓库根目录执行。Target 参数选择已登记目标，当前参考端为26.3-neoforge；需要正确Java/Loader缓存、Node、Companion依赖及本机EULA授权。

```powershell
./gradlew.bat :extension_packages:assemble :companion:assemble
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 26.3-neoforge
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 1.21.1-neoforge -Adapter modern_ui
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 1.21.1-neoforge -Adapter emi
pwsh -NoProfile -File components/validation_tools/integration-packaged_runtime/task/Invoke-InstalledRuntimeSmoke.ps1 -Target 26.3-neoforge -ExtensionPlatform
pwsh -NoProfile -File components/validation_tools/integration-packaged_runtime/task/Invoke-InstalledRuntimeSmoke.ps1 -Target 1.20.1-forge -ExtensionPlatform -ExtensionAdapter modern_ui
node components/validation_tools/integration-server_peer/task/server-console-smoke.mjs --target=26.3-neoforge --extension-platform
```

Runner 只使用本验证的隔离实例，1920×1080静音、最大4GiB，以本地测试授权安装固定探针包。所有GUI/命令/Debug操作走正式接口，正常退出并确认端点和端口释放。禁止Computer-Use，不访问Prism、重要存档或日常实例。

## 通过标准

每个已运行Target的固定result/Target-client.json记录当批源码/包身份与实际Runtime，目录为active，公开SDK共享类身份成立；真实Provider返回owner线程采样，未Arm/READ修改被拒绝，Arm显式start/stop与到期均清理、无Lease副作用。MCP静态资源可查阅，采样不是玩家玩法证据。输入归零、正常关闭、无崩溃和Persistent Write调用0。

成品客户端使用官方 Loader、全新游戏目录、最终发行 JAR 与已批准派生库，不带开发 classpath；独立服务器为零玩家原生运行，不借客户端类或输入 Lease。结果分别写为 Target.json、Target-installed-client.json、Target-installed-server.json 和 Target-installed-server-lifecycle.json，记录真实 JAR/包身份与正常退出。

早期 hook 的通过要求实际 MinecraftServer tick 计数与 helper 类身份，不只检查配置注册。替包工具 package-replacement.mjs 先 capture 再 verify 同一 Target 的前后成品结果，要求本体 JAR 哈希不变、原生重启、包版本/身份和实际代码返回字段变化，前后结果保存在固定 replacement 路径。旧候选结果只按自身身份解释。

没有运行的Target或最终发行装载不得写为PASS；七模组实际业务与人工验收不由平台探针代替。

UI 适配场景使用 inputs.toml/local.toml 登记的原始上游 JAR，以及独立编译并按内容批准的适配包。按 Target-client-Adapter.json 分开记录，避免不同案例互相覆盖；新结果同时绑定本体源码、所用包源码和实际上游 JAR 哈希，结束时重新核对。CC 的正常键盘输入须在客户端可见终端产生计算结果；Modern UI 须完成导航、滑块修改、文本恢复、按实际轴滚动、真实拖拽和拖拽中退回READ，确认取消、held清零与无迟到派发；EMI 须完成搜索、收藏状态切换与恢复、配方页的可见内容及正常退出。所有 Act 进入既有真实输入路径，命令只用于明确的测试准备，未运行的上游/Target 组合不外推。

Forge 原始第三方发行包使用独立成品安装场景，不用 named 开发运行冒充 SRG 兼容。适配场景可复用已验证的官方 Loader 安装缓存，只复制官方 profile/libraries，仍使用全新游戏目录和当前本体/适配包；结果明确记录 installerExecuted=false，不复用旧产品运行结论。纯净单JAR安装场景保持独立。第三方替换 HUD 后不伪造缺失文本；纯净 Forge 另断言实际绘制的经验等级。
