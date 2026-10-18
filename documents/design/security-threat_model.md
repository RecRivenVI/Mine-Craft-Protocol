# 核心威胁模型

当前仅覆盖 Autonomous Testing Core；E1/E2/E3 未启动。
Runtime 使用 loopback-only 暴露；正式 Persistent Write 路由不存在。
当前验收状态见[状态页](../project/status-milestones.md)。

## 协作控制合同

[控制模型](concept-agent_control.md)以是否接管真人输入区分模式：读取按USER/DEBUG及自身授权，不再要求模式互斥；
OPERATE/TAKEOVER可做独立授权DEBUG修改，真人Esc只撤销接管及其玩家输入，不连带取消独立DEBUG/Arm。
OPERATE可与真人并行并覆盖状态，仍须owner thread、实际主体权限及显式资源/值条件；所有命令操作归USER，不按作弊权限转DEBUG。
命令树可用性不是授权或执行成功，客户端OP不提供服务器内部通道。仅服务端控制台使用独立操作员OPERATE，不伪造玩家TAKEOVER。
OPERATE可用真实玩家原生命令API，不打开聊天框/修改草稿或注入共享键鼠，无需玩家输入Lease；独立验证主体/连接、command scope、期限与原生权限。
不能仅删除现有聊天手势门禁或升级console source。物理Esc只撤销接管输入，不连带取消独立命令；连接/授权失效和操作自身取消仍有界清理。
共享准入及当前玩家命令路径已迁移；完整协作与三部署/Peer接收授权仍待实施，既有证据不证明新模型已完成。发布前继续核对全部消费者、接收端与取消链。

## 资产与权威边界

多个本机线程以明确的实例目录发现端点，Runtime 的 OS 目录锁先于 token 写入，默认由 OS 分配端口。
发现记录不含 token，不授予任何 scope/Lease/Arm；Companion 先安全握手，再给每个请求固定启动 UUID。
记录消失、重启或身份不符时拒绝，不猜测其他端口或自动重放控制。UUID 不代替世界、存档或文件版本身份。
默认随机 token 保留；显式 URL/token 旧客户端若不发送身份 header，没有同等重启隔离保证。
这不是防御能改写实例配置、读取 token 或操纵本机进程的宿主恶意程序的安全边界。

保护玩家输入、LIVE client/server state、Fixture/Debug authority、令牌/Lease、
Screenshot/聊天/书本/日志/录制证据、游戏稳定性与存档完整性。
Agent/Companion → 认证 loopback HTTP/WS worker → immutable command →
Minecraft client/render/server owner thread。HTTP 线程不得直接触碰游戏对象。

CLIENT_KNOWN、SERVER_AUTHORITATIVE、PERSISTED 分开；Persisted 只表示 last_saved_state，
明确 stale/consistency，不强制加载，不回退 LIVE，不提供任意路径。
Runtime session、stable Storage Identity、file snapshot/revision 三层独立。

新增三部署目标的主体/授权设计见[部署场景](concept-deployment_scenarios.md)，尚未实现。
当前完整 HTTP scope/Arm 门禁不能据此推定已在 Peer 接收端验证；现有游戏连接与 operator/开关检查
不等于细粒度委托，客户端 OP 也不提供不存在的服务器内部通道。
仅服务端控制台须独立操作员授权，不能与玩家 USER 混用；当前不开放新的控制台或服务器管理接口。

世界 USER 是有界客户端缓存，不是严格视觉可见性边界；附近墙后/地下已接收数据可能返回，不能作为防透视或公平游戏保证。
查询范围、工作量、时间和输出有界；不会自动加载 Chunk、查询服务器、磁盘或未打开容器。
幼体、TamableAnimal 驯服/坐姿及牵引反馈只来自客户端同步状态，不包含主人身份、love、繁殖冷却或内部报价。
持有者未知时不返回 leashHeldByPlayer，不能把解析延迟变成所有者断言；随机驯服和繁殖不得由诊断 setter 冒充玩家效果。

USER 语义持续动作不获得新权限：沿既有 TAKEOVER/Lease、输入串行队列与取消 admission，
在原版挖掘/使用入口复验目标、主副手、玩家/视口与有界期限。无当前语义 gesture 时钩子不改变 Vanilla。
原版未修改状态的确认包不等于生命周期变更；真实 A→B→A 仍使旧引用失效。
放置按原版 BlockPlaceContext 与新鲜面内命中复验，不用 DEBUG、自动潜行或直接世界调用绕过规则。
释放按住输入不是事务回滚；弓等物品可能在取消松键时生效，不能承诺取消等于零世界效果。
客户端破坏预测、使用状态及受伤动画不是服务器最终接受，PLAYTEST 输入响应仍需独立效果证据。
UI USER 仍受原版呈现限制；读到目标不授予穿墙交互、输入 Lease、Debug 或任何 mutation 权限。

## 防护与失败策略

| 风险 | 当前合同 |
| --- | --- |
| 未授权访问 | Bearer principal、scopes、Host/Origin、loopback 校验；模式不补权限 |
| 滥用资源 | body/rate/active-operation、读取字节/NBT/queue、Capture/Recording 与 Provider 预算 |
| 持续玩家输入 | Control Lease、期限、取消、manual Esc、disconnect/close 清理，晚到 dispatch 拒绝 |
| 命令旁路/错误证据 | 实际原生树及别名/完整参数上下文分类、方法与最终命令包双边界、短期 Operation 来源许可；未知/间接未决和宿主管理拒绝，状态修改/非原生来源保持污染 |
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

世界 USER targetRef 只是 15 秒的有界客户端候选引用，不是权限或已验证命中。
世界/玩家/摄像机、Chunk 对象/修改序号或实体对象变化时拒绝旧引用；Chunk A→B→A 也不复用。
每次相对校正与实际按下都在现有 TAKEOVER/Lease admission 中复验，按下前刷新原版 pick；
读取到墙后对象不意味着可以隔墙交互。输入结果不替代真实效果观察，也不清除 DEBUG Arrange 污染。
持续挖掘仅接受同一客户端线程、本次同步原版破坏范围内的有界变更和破坏前流体替代；
不能把调用外、其他线程、目标重复改写或未记录的 revision 增长算作自身完成。
副手持续使用要求空主手并观察实际原版选手，不能为了成功跳过原版优先级或直接调用副手业务方法。

- 本地令牌身份不是多租户或可对公网安全托管的 IAM；LAN proxy/TLS/pairing 未交付。
- session.lock 是合作式写入所有权，不是对任意忽略锁程序的原子 compare-and-swap。
- Windows directory durability 未证明，atomic replace 不等于断电级事务。
- 第三方 Mod 的输入、渲染和 storage hooks 需真实版本绑定兼容验证。
- Captures/Artifacts 可含用户内容；Operator UI 默认排除不等于数据已经匿名化。
- 不提供任意反射、JVM REPL、raw filesystem/NBT/packet escape hatch。

迁移前详细分析保留在[历史记录](../research/audit-security_threat_model.md)，不作为新验收结果。
