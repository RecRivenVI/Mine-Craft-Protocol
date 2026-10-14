# 项目规范

本文件补充[仓库规范](../AGENTS.md)，对整个项目生效。

## 项目信息

| 项 | 值 |
| --- | --- |
| 产品显示名 | Minecraft Protocol Probe |
| 模组 ID | `minecraft_protocol_probe` |
| Java 包 | `io.github.recrivenvi.minecraftprotocol` |
| 入口类 | `NeoForgeProbeMod`、`ForgeProbeMod`、`FabricProbeMod`、`FabricClientProbeMod` |
| 业务 | Agent-native Minecraft Autonomous Testing Platform Runtime |
| 文档主语言 | 简体中文 |
| 首页语言 | 简体中文 |
| 参考 Target | `26.3-neoforge` |

## Target

| Target | 加载器版本 | Java |
| --- | --- | --- |
| `1.20.1-forge` | 47.4.28 | 17 |
| `1.21.1-neoforge` | 21.1.259 | 21 |
| `26.1.2-neoforge` | 26.1.2.117 | 25 |
| `26.3-neoforge` | 26.3.0.77 | 25 |
| `26.3-fabric` | 0.19.5 | 25 |

## 补充规则

- **X-01** [判断] 通过本产品的 Runtime HTTP/WebSocket 或 MCP Companion 操作 Minecraft；禁止 Computer Use、CUA、桌面自动化或宿主输入注入。真人 Esc、Alt+Tab、点击与焦点验收必须由用户执行，结合 Runtime/Event/Audit 和用户反馈判断。
- **X-02** [判断] Core 是 Autonomous Testing Platform；E1/E2/E3 为可选扩展，不隐式阻塞首个 Developer Preview。
- **X-03** [判断] UI/游戏世界两个领域采用 READ/TAKEOVER/OPERATE 三模式、USER/DEBUG 二类别；READ 可提供两类独立读取，TAKEOVER 仅 USER，OPERATE 仅 DEBUG。模式分层但不自动继承或授予权限；scope、Lease、Debug Arm、Human Override 和 Evidence 独立。PLAYTEST、FIXTURE、DEBUG_PRIVILEGED 证据不得混淆。两个领域各自设首个里程碑，推进安排以执行计划为准；研究建议不视为已经实现或获准实施的合同。
- **X-04** [判断] 构建、启动、功能与人工验收分别报告。历史绑定提交的结果不证明当前候选；正式 Persistent Write 尚未实现。
- **X-05** [判断] 未经用户明确授权不提交、推送、建分支、标签或改写历史；删除使用回收站。普通验收一次只运行一个客户端。
- **X-06** [判断] Runtime 调用的诊断、统计与 Provider 入口属于正式产品，保留在产品源码；开发运行不得以复制发行 JAR 替代 Loader 原生 source-set。
- **X-07** [判断] `1.20.1-forge`、`1.21.1-neoforge`、`26.1.2-neoforge` 固定 Minecraft 版本并持续更新对应 Loader；26.3 NeoForge/Fabric 线滚动跟进最新 Minecraft 正式版与对应 Loader。只有 Loader 允许测试版，Minecraft 快照、预发布版与候选版不纳入维护 Target。每次升级核对真实 Loader 支持并固定具体版本；升级状态记录在[执行计划](project/plan-execution.md)，当前 Target 表只描述实际构建配置。
- **X-08** [判断] 项目名称使用 Mine-Craft-Protocol，不将用户输入的 `M-C-P` 作为正式简称。`MCP` 仅用于 Model Context Protocol 及其既有接口标识。
