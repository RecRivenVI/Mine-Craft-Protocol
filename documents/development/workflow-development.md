# 开发、构建与验证

从仓库根目录执行命令。Target 为 `:version:<target>`；
组件是独立 included builds，任务路径为 `:runtime_safety:<task>`、
`:protocol_schema:<task>`、`:companion:<task>`、`:validation_tools:<task>`。

`runtime_safety` 是随模组发布的产品组件；`protocol_schema`、`companion` 与
`validation_tools` 是工具组件，分别负责协议模型生成、MCP Companion 和验证编排。

## 构建与原生测试

```powershell
npm --prefix components/companion ci --include=dev
./gradlew.bat projects verifyConfiguration verifyCompliance
./gradlew.bat spotlessApply check verifyRelease
./gradlew.bat :runtime_safety:test :protocol_schema:test :companion:check :validation_tools:check
```

首次构建允许依赖下载；Java 工具链由 Target Facts 与 Gradle 管理。
`check` 包含五个 Target、工具/产品组件、格式、Markdown 与仓库规范检查。
产品与工具的依赖版本见 `gradle/libs.versions.toml`，不在文档维护第二份权威。

Companion 的 Node 版本要求与 npm 依赖分别以其 `package.json`、`package-lock.json` 为准。
GitHub Actions 先按该版本要求准备 Node，再执行 `npm ci --include=dev`，
最后运行 `check verifyRelease`；npm 缓存不替代依赖安装，TypeScript 与类型定义必须一并安装。

## 日常运行

```powershell
./gradlew.bat :version:26.3-neoforge:runClient
./gradlew.bat :version:26.3-fabric:runServer
```

日常实例为 `instances/<target>/{client,client-multiplayer,server}`。
[配置格式](../reference/configuration-repository.md)定义 TOML、覆盖规则与 EULA；
模板内置客户端玩家名 DevPlayer/DevGuest。服务端仅在用户于 local.toml 接受 EULA 后启动。
旧 properties 个人配置不再作为构建输入；本地未知内容保留，不自动删除。
普通验收一次一个客户端，不操作仓库之外的游戏实例。

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
