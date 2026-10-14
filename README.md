# Mine-Craft-Protocol

当前 Core 是 **Agent-native Minecraft Autonomous Testing Platform**：让 Agent
通过 Minecraft 自身的 Runtime 完成 Arrange → Act → Observe / Wait / Assert → Evidence。
产品显示名仍为 Minecraft Protocol Probe；E1/E2/E3 是非阻塞的可选扩展，Wire Protocol v1 未冻结。

## Target

| Target | 编译加载器 | Java |
| --- | --- | --- |
| `1.20.1-forge` | Forge 47.4.28 | 17 |
| `1.21.1-neoforge` | NeoForge 21.1.259 | 21 |
| `26.1.2-neoforge` | NeoForge 26.1.2.117 | 25 |
| `26.3-neoforge` | NeoForge 26.3.0.77 | 25 |
| `26.3-fabric` | Fabric Loader 0.19.5 | 25 |

## 快速开始

参考 Target 为 `26.3-neoforge`。

```powershell
./gradlew.bat spotlessApply check verifyRelease
./gradlew.bat :version:26.3-neoforge:runClient
```

## 文档

- [开发与验证入口](documents/development/workflow-development.md)
- [日常开发](gradle/conventions/specification/development/workflow-daily.md)、[配置格式](gradle/conventions/specification/reference/configuration-repository.md)
- [Core 范围](documents/project/scope-core.md)、[可选扩展](documents/project/scope-extensions.md)
- [执行计划](documents/project/plan-execution.md)、[控制模型](documents/design/concept-agent_control.md)
- [Showcase](validations/feature-control_ui_showcase/validation.md)、[整合包长期计划](validations/compatibility-large_modpacks/validation.md)
- [仓库规范](AGENTS.md)、[项目安全补充](documents/AGENTS.md)

## 许可

本项目采用 [All Rights Reserved](LICENSE)，保留所有权利，不授予使用或再分发许可。
第三方文件的许可边界见 [NOTICE](NOTICE)。
