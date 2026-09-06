# Mine-Craft-Protocol

**Agent-native Minecraft Autonomous Testing Platform** — 让 Coding Agent 通过
Minecraft 自身的 Runtime 完成 Arrange → Act → Observe / Wait / Assert → Evidence
测试闭环，而不是把桌面自动化当作游戏输入系统。

原生 HTTP/WebSocket Runtime 与独立 MCP Companion 分离；观察、玩家操作、
Fixture / typed Debug 和证据来源保持明确边界。当前能力、限制和验收进度见
[统一文档入口](docs/README.md)，不要把历史 Phase PASS 等同于当前全部功能已验收。

## 五个真实 Target

| Target | Minecraft / Loader |
|---|---|
| `1.20.1-forge` | Forge 1.20.1 |
| `1.21.1-neoforge` | NeoForge 1.21.1 |
| `26.1.2-neoforge` | NeoForge 26.1.2 |
| `26.2-neoforge` | NeoForge 26.2 |
| `26.2-fabric` | Fabric 26.2 |

每端是 `versions/` 下的独立 sibling Gradle 工程。例如：

```powershell
.\gradlew.bat :versions:1.20.1-forge:build
```

## 产品与文档边界

- [Core Vision](docs/product/vision.md) 是唯一 committed 产品目标；
  第一次 Developer Preview 只以 Core 为交付目标。
- Development, Debugging and Testing Platform 是 umbrella positioning，
  不把所有潜在扩展变成当前产品承诺。
- [E1 Development Intelligence、E2 Autonomous Gameplay、E3 Render Forensics](docs/product/extensions.md)
  是独立 Optional Extensions，可部分完成、推迟或取消，不阻塞首个 Preview。
- [执行计划](docs/product/execution-plan.md) · [架构与控制模型](docs/architecture/agent-control.md)
  · [Showcase](docs/testing/control-ui-showcase.md) · [长期整合包兼容性计划](docs/testing/modpack-compatibility.md)
- [AGENTS.md](AGENTS.md) 继续定义仓库工作与真人验收边界。Wire Protocol v1 **NOT FROZEN**。
