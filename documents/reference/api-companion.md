# MCP Companion 接口

Companion 是独立 Node/TypeScript 工具组件，不内嵌 MCP server 到 Minecraft。
当前 V0 文档版本为 `0.0.1-control-r24`，`publishes 24 static Tools`。
工具描述/Prompt 固定，不来自 Runtime 文本；大型 PNG/ZIP 用 minecraft:// Resources。
Wire Protocol v1 未冻结。

## 构建与连接

```powershell
npm --prefix components/companion ci
npm --prefix components/companion test
./gradlew.bat :companion:check
```

Node/npm 依赖来源、MCP 客户端安全要求与审计命令见[Companion 依赖](dependency-companion.md)。

连接参数以[实际配置示例](../../components/companion/mcp.example.json)及
组件 src/config.ts 为准，token 文件和 URL 必须由用户本机提供，不提交凭据。
Loopback-first；ALLOW_NON_LOOPBACK 不让 Runtime 变成已验收 LAN/TLS 服务。
生成协议路径为 components/protocol_schema；构建校验 schema 版本与实际 types。

## Surface 与安全

24 个静态工具覆盖 session/capabilities、UI、control、input pipeline/operation、
Wait/Assert、Deep Observation、capture/recording/artifact、Debug Arm/typed Debug、Persistent Read。
typed discriminator 决定操作模式，不能只按 Tool 名推断。
工具/Resource JSON 保留原生 provenance 并标记 data-plane-only。
Content-Length precheck、stream reader、running budget 和 oversize abort 防止无界收包。

READ/OPERATE/TAKEOVER 查询与转换是正式结构；
Runtime Error 到 MCP 保留 USER_MANUALLY_ENDED_CONTROL 和 reconsentRequired，
不靠错误字符串猜模式。manual latch 持续返回，明确 reacquire 才清除。
收到人工撤销后，Agent 必须先取得当前对话内明确同意，再重新 TAKEOVER；
Runtime 不读取聊天或接收假 userConsent。

`minecraft_control` 的 begin_task / heartbeat_task / end_task 管理有界逻辑任务。
任务不升模式或授予权限；持续等待先心跳，正常结束显式 end_task。
acquire 自动携带本 Companion 已明确开始的 taskId，仍须既有 Control Lease授权。
close 尝试结束自己持有的任务；进程崩溃由 TTL 收尾，不自动重放或重新接管。
control status 返回 agentTask 的结构化状态。任务结果不确定时先读取状态再决定下一步。

## 证据与验收边界

旧 MCP conformance 的客户端协商版本只证明对应测试；
不同 SDK/protocol 版本支持不能用声明代替实际 client-gate。
操作经 Runtime，不调用 Java 内部方法或宿主输入。
Fixture/Debug/PLAYTEST authority 不由 MCP 包装重新分级。

完整旧接口说明与配置背景保留在[历史记录](../research/audit-api_companion.md)；
当前接口以生产 types、static server tools 与[Native V0](protocol-native_v0.md)为准。
