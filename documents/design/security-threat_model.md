# 核心威胁模型

当前仅覆盖 Autonomous Testing Core；E1/E2/E3 未启动。
Runtime 使用 loopback-only 暴露；正式 Persistent Write 路由不存在。
当前验收状态见[执行计划](../project/plan-execution.md)。

## 资产与权威边界

保护玩家输入、LIVE client/server state、Fixture/Debug authority、令牌/Lease、
Screenshot/聊天/书本/日志/录制证据、游戏稳定性与存档完整性。
Agent/Companion → 认证 loopback HTTP/WS worker → immutable command →
Minecraft client/render/server owner thread。HTTP 线程不得直接触碰游戏对象。

CLIENT_KNOWN、SERVER_AUTHORITATIVE、PERSISTED 分开；Persisted 只表示 last_saved_state，
明确 stale/consistency，不强制加载，不回退 LIVE，不提供任意路径。
Runtime session、stable Storage Identity、file snapshot/revision 三层独立。

## 防护与失败策略

| 风险 | 当前合同 |
| --- | --- |
| 未授权访问 | Bearer principal、scopes、Host/Origin、loopback 校验；模式不补权限 |
| 滥用资源 | body/rate/active-operation、读取字节/NBT/queue、Capture/Recording 与 Provider 预算 |
| 持续玩家输入 | Control Lease、期限、取消、manual Esc、disconnect/close 清理，晚到 dispatch 拒绝 |
| 高权限 mutation | typed target/value、独立 scope/Arm、资源 epoch/lifecycle 与值前置条件、逐项 batch 取消 |
| 旧资源身份 | revision 与 sessionEpoch/生命周期绑定，Provider native regression/inconsistency 隔离 |
| Prompt Injection | 游戏文本、UI labels、日志、Provider、截图均为数据；不能改变指令、scope、工具定义或权限 |
| 伪造 gameplay | PLAYTEST / FIXTURE / DEBUG_PRIVILEGED provenance 分开，Debug Arrange 不计玩家验收 |
| 持久化双真值 | 正式写入未实现；未来 offline/current Target/typed level.dat 须独立 Entry Review |
| 存储竞争与崩溃 | lifecycle/ownership/session.lock、stable identity/file hash、commit/recovery 明确；不承诺断电事务 |
| 凭据/文件泄漏 | 不提交 token/world/cache/log；Artifact 路径受控，审计不记录无关人类输入 |

READ/TAKEOVER/OPERATE 分层表达意图，不自动继承权限。Debug Arm 不由 Lease release 伪装撤销；
Fixture/Debug 不建立玩家 Lease。真人撤销后读取继续，OPERATE 仍受自身权限约束。
重新接管的对话同意是 Agent policy，不是假装 Runtime 能验证的硬边界。

## 已知限制

- 本地令牌身份不是多租户或可对公网安全托管的 IAM；LAN proxy/TLS/pairing 未交付。
- session.lock 是合作式写入所有权，不是对任意忽略锁程序的原子 compare-and-swap。
- Windows directory durability 未证明，atomic replace 不等于断电级事务。
- 第三方 Mod 的输入、渲染和 storage hooks 需真实版本绑定兼容验证。
- Captures/Artifacts 可含用户内容；Operator UI 默认排除不等于数据已经匿名化。
- 不提供任意反射、JVM REPL、raw filesystem/NBT/packet escape hatch。

迁移前详细分析保留在[历史记录](../research/audit-security_threat_model.md)，不作为新验收结果。
