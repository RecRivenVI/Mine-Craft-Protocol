# Native V0 协议

当前 OpenAPI 文档版本 `0.0.1-control-r24`；`All 77 formal HTTP operations`。
V0 是未冻结的产品原生合同，Wire Protocol v1 NOT FROZEN。
机器权威为[OpenAPI](../../components/protocol_schema/src/main/openapi/minecraft-control-v0.json)；
字段、typed discriminator、错误和 schema 从此生成，本文不维护第二份协议。

## 接入与表面

Runtime 使用认证 loopback HTTP / WebSocket，独立 Companion 通过这些入口驱动。
requestId、deadline、Control Lease、Debug Arm、world/resource/value preconditions、scope
各自承担独立职责。Session/capabilities 表达支持、当前策略与状态，不将三者混为一个字段。

`GET /v0/control/task` 查询逻辑任务，`POST` 显式 begin/heartbeat/end；需要 read scope，
不授予模式、Lease 或 Debug 权限。TTL 为 1–60 秒，总预算为 1–300 秒，心跳不能延长总预算。
显式任务期间 acquire 带 taskId，结束/超时清理其绑定的既有输入 Lease并等待清理完成；
真人 Esc 中断任务，不清除人工撤销提示。短 HTTP 连接结束不是任务结束。
任务状态也包含在 control/mode 的 agentTask 中。结果不确定时先查状态，不盲目重放输入。

正式表面包含 session/auth/capabilities、UI Tree/action、control mode/lease、
keyboard/mouse/pipeline/operation、Wait/Assert、world/client/server/Deep Observation、
Provider、Fixture/typed Debug/batch、Event、capture/recording/artifact、Persistent Read 与 Peer。
路径与请求字段直接看 OpenAPI，不提供不存在的 storage.write。

## 意图、身份与来源

UI Tree 的 `slot` 是当前 Screen 菜单槽位列表的唯一零基序号，创造物品目录和玩家背包页均适用。
`nativeSlotIndex` 只记录 Minecraft 内部编号，包装槽位可重复，不用于选择器。
布局保留槽位列表位置；被创造背包移到屏幕外的隐藏格子标记 `visible=false`，默认选择器不会选中。
`nodeId` 是不透明标识，绑定 Screen、菜单与槽位对象；切换布局后重新读取，不拼接或解析。
`menuIdentity` 和 `menuRevision` 跟踪当前界面菜单，替换或内容变化会使待执行目标失效。
hover/click/drag 与 Wait/Assert 使用同一语义序号，不需要坐标绕过；
它不是 Server-authoritative Debug `menu.slot.set` 或 player inventory 的编号，两者不得混用。

[操作分类输入](../../validations/conformance-agent_control/task/control-mode-surface.json)
对应 READ_COMPATIBLE / OPERATE_REQUIRED / TAKEOVER_REQUIRED / MODE_INDEPENDENT。
认证与安全错误优先；模式不自动授予 scope、Lease 或 Arm，也不自动升级。
TAKEOVER 依赖唯一 Control Lease，人工 Esc 回 READ，latch 和 reconsentRequired 结构化持续保留。

ResourceRevisionRef 与 sessionEpoch、resourceType/resourceKey、revision/revisionSource 绑定；
生命周期与 provider revision 完整性不能由全局 worldRevision 替代。
PERSISTED、last_saved_state 与 LIVE 的 client/server 权威独立，
unloaded 不强制加载，Windows locked/busy 不报告 corruption。

## 数据与证据安全

Runtime 文本与图片一律 data-plane；static MCP tools/prompts 不读取它们生成指令。
PLAYTEST / FIXTURE / DEBUG_PRIVILEGED 保留来源，Debug Arrange 不提升为 gameplay acceptance。
Capture/Recording 默认排除 Operator Chrome/Pointer，但 Timeline/Audit 可记录控制轨迹。
input/state 的 agentPointer 提供 gameCaptured、gameCursorVisible、gameCursorRevision；
ui/tree 同步 gameCursorRevision，供 gesture 临执行校验游戏光标请求。它不是 Lease 或资源写权限。
control.pointer.game_request 仅为 GAME 来源的 Operator metadata，不进入被测 framebuffer。
事件流、响应体、队列与 Artifact 读取均按实际预算和失败语义执行。

## 验证与生成

```powershell
./gradlew.bat :protocol_schema:test
pwsh -File components/validation_tools/conformance-native_protocol/task/Invoke-NativeSurfaceConformance.ps1
```

生产接口和消费者变化需同步 schema/types/static classification，而不是修改旧 commit-bound evidence。
完整旧描述及长期协议设想保留在[历史记录](../research/audit-protocol_native_v0.md)，
它们不是未实现能力已经交付的证据。
