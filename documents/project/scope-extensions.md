# 可选扩展范围

E1/E2/E3 是独立、非阻塞的 Optional Extensions，不扩大[Core](scope-core.md)交付合同，
不进入现有 Phase 9/10 退出门槛，也不阻塞第一次 Developer Preview 或 Core 1.0。

## 三项机会

| 扩展 | 目标 | 当前安排 |
| --- | --- | --- |
| E1 Development Intelligence & Exploratory Debug | 源码/制品/符号智能、受控探索调试和人类 Inspector | PLANNED，未实现 |
| E2 Autonomous Gameplay | 自动寻路、长期自主游玩与游戏策略 | IDEA / RESEARCH，未实现 |
| E3 Deterministic Graphics Acceptance & Render Forensics | 确定性图形验收、渲染取证、几何/资源/管线诊断 | IDEA / RESEARCH，未实现 |

它们不是必需的 E1 → E2 → E3 顺序，可以部分开发、延期、取消或单独发布。
采用范围文档不代表授权开始实施。

## 实施边界

- E1 的源码索引、缓存、制品仓库与探索服务不能偷偷成为 Core Runtime 的依赖。
  Safe Probe 仅考虑显式暴露根上的有界只读投影；不建立隐形反射、文件、网络或进程后门。
  全权限 Groovy/JVM 研究必须单独 threat model、Debug Arm、审计与人类许可。
- E2 的自主路径/计划不能通过直接 mutation 冒充玩家 GAME_ROUTED 行为。
  长期自主性与高权限 Debug 必须分开，仍遵守 Human Override 与控制租约。
- E3 的几何、资源、材质、管线与时间关系均需实际证据，单有 screenshot/geometry 不足以证明渲染正确。
  Radiance 是可选研究上界，不是产品交付承诺，也不由本项目自动改造外部渲染器。
- 各扩展需独立职责、生命周期、权限、预算、失败语义、五端适用性与明确验收。
  Core Gate 与扩展 Gate 分离，扩展失败不降低已验证 Core 的状态。

## 治理

扩展状态可以是 IDEA / RESEARCH / PLANNED / EXPERIMENTAL / ACTIVE / PARTIAL /
STABLE / DEFERRED / CANCELLED，不替代 Runtime Phase 状态。
只有新的明确产品治理决定才能把扩展能力提升为交付义务。

迁移前详细目标与原文保留在[历史记录](../research/audit-scope_extensions.md)；
当前实现状态见[状态页](status-milestones.md)，下一授权任务见[执行计划](plan-execution.md)。
