# 开发、构建与验证

从仓库根目录执行命令。Target 为 `:version:<target>`；
组件是独立 included builds，任务路径为 `:runtime_core:<task>`、`:runtime_safety:<task>`、
`:protocol_schema:<task>`、`:companion:<task>`、`:validation_tools:<task>`。

`runtime_core` 与 `runtime_safety` 是随模组发布的产品组件；`protocol_schema`、`companion` 与
`validation_tools` 是工具组件，分别负责协议模型生成、MCP Companion 和验证编排。

## 构建与原生测试

首次运行将下列缓存目录占位符替换为仓库之外的本机路径；工具只准备已登记的验收输入，不启动游戏或修改EULA。

```powershell
npm --prefix components/companion ci --include=dev
pwsh -File components/validation_tools/feature-control_ui_showcase/task/Prepare-AcceptanceInputs.ps1 -CacheDirectory '<本机验收Mod缓存目录>'
./gradlew.bat projects verifyConfiguration verifyCompliance
./gradlew.bat spotlessApply check verifyRelease
./gradlew.bat :runtime_core:test :runtime_safety:test :protocol_schema:test :companion:check :validation_tools:check
```

首次构建允许依赖下载；Java 工具链由 Target Facts 与 Gradle 管理。
`check` 包含五个 Target、工具/产品组件、格式、Markdown 与仓库规范检查。
根格式配置保留模板相同的文件范围，但使用一次剪枝文件树遍历，避免 Windows 对每个 glob 重复扫描大型验证实例。
实例、结果和 local.toml 在遍历阶段排除；不会以禁用格式检查或改模板文件绕过。文本继续为 LF，bat/cmd 由原任务保持 CRLF。
产品与工具的依赖版本见 `gradle/libs.versions.toml`，不在文档维护第二份权威。
内部试用 JAR 由 `verifyRelease` 收集到 `build/release/<mod_version>/`。
完成当前源码的五端 Runtime 核心迁移验证后，可刷新绑定新源码与制品的 alpha 清单：

```powershell
node components/validation_tools/regression-v1_release/task/write-alpha-manifest.mjs --validation=migration-runtime_core
```

生成器同时验证并发布固定alpha快照；其他线程的复制、使用登记与保留规则见[alpha工作流](workflow-alpha_agent.md#固定分发与使用登记)。
build/release仅是会被构建覆盖的收集位置，不是跨线程分发入口。

配置缓存设置及当前任务不兼容原因以 [gradle.properties](../../gradle.properties) 为准。

Companion 的 Node 版本要求与 npm 依赖分别以其 `package.json`、`package-lock.json` 为准。
GitHub Actions 先按该版本要求准备 Node，再执行 `npm ci --include=dev`，
最后运行 `check verifyRelease`；npm 缓存不替代依赖安装，TypeScript 与类型定义必须一并安装。

## 日常运行

```powershell
./gradlew.bat :version:26.3-neoforge:runClient
./gradlew.bat :version:26.3-fabric:runServer
```

日常实例为 `instances/<target>/{client,client-multiplayer,server}`。
[配置格式](../../gradle/conventions/specification/reference/configuration-repository.md)定义 TOML、覆盖规则与 EULA；
模板默认玩家名可由本机覆盖；本机验收使用RecRivenVI。服务端仅在用户于 local.toml 接受 EULA 后启动。
旧 properties 个人配置不再作为构建输入；本地未知内容保留，不自动删除。
普通验收一次一个客户端，不操作仓库之外的游戏实例。

## 实例预设

自动客户端固定1920×1080，全局游戏音量0；保留各验证的隔离玩家名、世界和纯净Mod集合。
用户观察/验收客户端固定2560×1440，全局音量0.5、音乐音量0，玩家名RecRivenVI。
日常预设来自instances.toml，本机玩家名与输入路径在local.toml；Showcase的两种角色在其validation.toml中独立声明。
标准Native prepare任务通过工具组件统一准备声音，不修改产品Runtime或模板；仅处理本仓库已停止的标准实例，保留其他选项与存档。
独立发行安装使用同一自动标准，但仍只安装一个最终产品JAR，不用附加Mod掩盖依赖错误。

Custom Skin Loader与Zume只用于用户验收，不打包进产品、不加入自动基线。
按Modrinth的精确Minecraft/Loader匹配固定已发布文件；当前可用范围及登记见[依赖说明](../reference/dependency-runtime.md#验收附加模组)。
准备工具按inputs.toml的来源下载并核对官方SHA-512及登记SHA-256，保留已有本机路径，不接受不匹配的缓存。
CI准备相同的本地输入后仍执行完整check和verifyRelease，不因可选验收模组把检查降级。

Showcase普通Case/All/PrepareClient为人工观察，使用client角色；Smoke/CoreSmoke为自动验证，使用隔离client-multiplayer角色。
两者不会复用实例、凭据、世界或附加Mod。自动结果不替代用户视觉/物理输入验收；不使用Computer Use。

## 产品验证

执行逻辑位于 `components/validation_tools/<validation>/task/`；
`validations/<validation>/task/` 只存输入数据。
先阅读对应 `validation.md`，不要在无认证、Arm、Lease 或授权世界时运行 Live 门禁。

| 目的 | 说明 |
| --- | --- |
| Native / 权限 | [Native Protocol](../../validations/conformance-native_protocol/validation.md) |
| UI / 输入 | [Automation](../../validations/conformance-automation/validation.md)、[Agent Control](../../validations/conformance-agent_control/validation.md) |
| 观察 / 录制 / Debug | [Observation](../../validations/conformance-observation/validation.md)、[Recording/Debug](../../validations/conformance-recording_debug/validation.md)、[Deep State](../../validations/conformance-deep_state/validation.md) |
| 集成 | [Server Peer](../../validations/integration-server_peer/validation.md)、[发行制品](../../validations/integration-packaged_runtime/validation.md) |
| 回归 | [V1](../../validations/regression-v1_release/validation.md)、[Core Demo](../../validations/regression-core_demo/validation.md) |
| 可观察场景 | [Runtime Probe](../../validations/feature-runtime_probe/validation.md)、[UI Showcase](../../validations/feature-control_ui_showcase/validation.md) |
| 生态兼容 | [标准 GUI](../../validations/compatibility-standard_gui/validation.md)、[大型整合包计划](../../validations/compatibility-large_modpacks/validation.md) |

Vulkan 使用 [Vulkan 验证](../../validations/regression-vulkan/validation.md)的隔离客户端任务，
不是第四种日常实例。保留的历史结果不因本次迁移变成当前运行验收。

## 产品与安全权威

- [Core](../project/scope-core.md)、[Extensions](../project/scope-extensions.md)、[当前计划](../project/plan-execution.md)
- [架构](../design/architecture-runtime.md)、[控制模型](../design/concept-agent_control.md)、[威胁模型](../design/security-threat_model.md)
- [Native V0](../reference/protocol-native_v0.md)、[Companion](../reference/api-companion.md)
- [项目补充](../AGENTS.md)保留真人输入边界与证据分级。

本次结构迁移不授予新产品能力；Persistent Write 仍未实现，统一真人 UI 验收仍待用户安排。
