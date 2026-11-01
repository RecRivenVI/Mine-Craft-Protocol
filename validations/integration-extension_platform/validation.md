# 扩展平台集成验证

## 目标

验证正式 SDK 独立包的实际加载、内容审批、静态目录与文档、Provider 读取/typed Debug 和受控采样清理。开发运行、最终单 JAR、早期 Mixin、独立服务器、七模组业务及人工验收分别报告，不复用研究证明。

## 运行

从仓库根目录执行。Target 参数选择已登记目标，当前参考端为26.3-neoforge；需要正确Java/Loader缓存、Node、Companion依赖及本机EULA授权。

```powershell
./gradlew.bat :extensionpacks:assemble :companion:assemble
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 26.3-neoforge
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 26.3-neoforge -Settings
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 26.3-neoforge -Settings -SettingsPhase disabled-restart
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 26.3-neoforge -Settings -SettingsPhase enabled-restart
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 1.21.1-neoforge -Adapter modernui
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 1.21.1-neoforge -Adapter emi
pwsh -NoProfile -File components/validation_tools/integration-extension_platform/task/Invoke-ExtensionPlatformSmoke.ps1 -Target 26.3-neoforge -LifetimeFaults
pwsh -NoProfile -File components/validation_tools/integration-packaged_runtime/task/Invoke-InstalledRuntimeSmoke.ps1 -Target 26.3-neoforge -ExtensionPlatform
pwsh -NoProfile -File components/validation_tools/integration-packaged_runtime/task/Invoke-InstalledRuntimeSmoke.ps1 -Target 1.20.1-forge -ExtensionPlatform -ExtensionAdapter modernui
node components/validation_tools/integration-server_peer/task/server-console-smoke.mjs --target=26.3-neoforge --extension-platform
node components/validation_tools/integration-server_peer/task/server-console-smoke.mjs --target=1.21.1-neoforge --extension-platform --extension-adapter=ae2
./gradlew.bat :extensionpacks:packageReplacementProbe
node components/validation_tools/integration-extension_platform/task/package-replacement.mjs stage-before 26.3-neoforge
pwsh -NoProfile -File components/validation_tools/integration-packaged_runtime/task/Invoke-InstalledRuntimeSmoke.ps1 -Target 26.3-neoforge -ExtensionPlatform
node components/validation_tools/integration-extension_platform/task/package-replacement.mjs capture 26.3-neoforge
node components/validation_tools/integration-extension_platform/task/package-replacement.mjs stage-after 26.3-neoforge
pwsh -NoProfile -File components/validation_tools/integration-packaged_runtime/task/Invoke-InstalledRuntimeSmoke.ps1 -Target 26.3-neoforge -ExtensionPlatform
node components/validation_tools/integration-extension_platform/task/package-replacement.mjs verify 26.3-neoforge
```

Runner 只使用本验证的隔离实例，1920×1080静音、最大4GiB，以本地测试授权安装固定探针包。正式GUI/命令/Debug操作走公开接口，正常退出并确认端点和端口释放。独服固定机器基线沿用已批准主机Fixture：第一次准备时在自有原生控制台执行固定forceload/setblock、正常保存并封存完整副本；它不是Runtime命令目录或玩法能力的证明。之后正式适配Act只经Native/MCP读取，不能加载目标。禁止Computer-Use，不访问Prism、重要存档或日常实例。

## 通过标准

每个已运行Target的固定result/Target-client.json记录当批源码/包身份与实际Runtime，目录为active，公开SDK共享类身份成立；真实Provider返回owner线程采样，未Arm/READ修改被拒绝，Arm显式start/stop与到期均清理、无Lease副作用。MCP静态资源可查阅，采样不是玩家玩法证据。输入归零、正常关闭、无崩溃和Persistent Write调用0。

扩展包配置组先执行initial，再在同一隔离实例依次执行disabled-restart和enabled-restart；后两次不重装包或重写批准记录。要求Loader原生配置入口、Agent语义和原始点击均不能首次批准、无效依赖自动禁用、已批准开关跨实际进程重启生效、无热卸载、完成后held为0。权限工具提示按屏幕宽度换行，测试截图写入外置Previews；真人首次批准和最终体验留待集中验收。

成品客户端使用官方 Loader、全新游戏目录、最终发行 JAR 与已批准包集合，首次正常启动准备发现桥后再启动自动生成加载库，不带开发 classpath；独立服务器为零玩家原生运行，不借客户端类或输入 Lease。结果分别写为 Target.json、Target-installed-client.json、Target-installed-server.json 和 Target-installed-server-lifecycle.json，记录真实 JAR/包身份与正常退出。

早期 hook 的通过要求实际 MinecraftServer tick 计数与 helper 类身份，不只检查配置注册。替包工具先将独立编译的1.0.3前包暂存，运行并capture，再恢复正常1.0.4包、运行并verify；两次之间不重新构建本体。要求当前最终本体 JAR 哈希不变、原生重启、包版本/身份和实际代码返回字段变化，前后结果保存在固定 replacement 路径。旧候选结果只按自身身份解释。

没有运行的Target或最终发行装载不得写为PASS；七模组实际业务与人工验收不由平台探针代替。

UI 适配场景使用 inputs.toml/local.toml 登记的原始上游 JAR，以及独立编译并按内容批准的适配包。按 Target-client-Adapter.json 分开记录，避免不同案例互相覆盖；新结果同时绑定本体源码、所用包源码和实际上游 JAR 哈希，结束时重新核对。CC 的正常键盘输入须在客户端可见终端产生计算结果；Modern UI 须完成导航、滑块修改、文本恢复、按实际轴滚动、真实拖拽和拖拽中退回READ，确认取消、held清零与无迟到派发；EMI 须完成搜索、收藏状态切换与恢复、配方页的可见内容及正常退出。所有 Act 进入既有真实输入路径，命令只用于明确的测试准备，未运行的上游/Target 组合不外推。

Forge 原始第三方发行包使用独立成品安装场景，不用 named 开发运行冒充 SRG 兼容。适配场景可复用已验证的官方 Loader 安装缓存，只复制官方 profile/libraries，仍使用全新游戏目录和当前本体/适配包；结果明确记录 installerExecuted=false，不复用旧产品运行结论。纯净单JAR安装场景保持独立。第三方替换 HUD 后不伪造缺失文本；纯净 Forge 另断言实际绘制的经验等级。

LifetimeFaults 安装两个独立编译并分别审批的包：逐包制造输入清理失败和grant资源清理失败，实际通过独立OPERATE修复；另一个包继续采样，原任务不恢复发布。实际背包/Lease与离开世界分别验证SCREEN、INPUT_LEASE、WORLD、CONNECTION失效。活动输入序列清理失败后队列要求重启属于保留的安全合同，不把它当成可热恢复；此类单元负控与普通屏障恢复分开。

独服适配场景按 Target-installed-server-Adapter.json 和 Target-installed-server-lifecycle-Adapter.json 保存。零玩家，server_operator命令仅作明确Arrange；AE2/Create/LittleTiles从真实已加载方块实体读取并拒绝远端未加载目标。客户端专用入口在独服不执行、不注册客户端能力；不把没有UI的运行端记作UI业务通过。正常停止后确认端点移除。七包的功能边界与上游不可用项须逐项保留。
