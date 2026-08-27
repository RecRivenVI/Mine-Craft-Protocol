# api-companion 迁移前记录

调查日期：2026-10-09。保留迁移前提交 1f754ab 的原文，仅作历史细节与决策追溯。

## 问题

迁移文档语言和配置体系时，避免丢失既有产品边界与设计理由。

## 方法

从迁移前 HEAD 导出 components/companion/README.md，保留原文如下。历史路径与运行方式不作为当前配置权威。

## 发现

````text
# Mine-Craft-Protocol MCP Companion

Independent TypeScript adapter from MCP to the authenticated Minecraft Runtime HTTP protocol. It is not embedded in Minecraft and does not change the native Runtime contract.

## Build and test

```powershell
cd components/companion
npm ci
npm test
```

Requirements: Node.js 22 or newer. The project uses the stable v2 packages `@modelcontextprotocol/server` and `@modelcontextprotocol/client` 2.0.0.

## Configuration

```text
MCP_MINECRAFT_BASE_URL=http://127.0.0.1:25583
MCP_MINECRAFT_TOKEN=<token>

# Preferred for local hosts:
MCP_MINECRAFT_TOKEN_FILE=D:\path\to\game\minecraft-protocol\token
```

Non-loopback Runtime URLs fail closed unless `MCP_COMPANION_ALLOW_NON_LOOPBACK=true` is explicit. This flag does not add TLS or pairing; external exposure remains a separate deployment responsibility.

## stdio

```powershell
node components\companion\dist\index.js
```

The Companion uses `serveStdio(factory)` and logs only to stderr. stdout is reserved for MCP JSON-RPC.

See [mcp.example.json](mcp.example.json) for host configuration. Replace `<repository>` with the actual absolute repository path; it is a template marker, not automatic host interpolation. Do not store token values in the repository.

## Documentation authority

Product scope and delivery status live in the [documentation index](../../documents/development/workflow-development.md).
[Native V0](../../documents/reference/protocol-native-v0.md) and the [control architecture](../../documents/design/model-agent-control.md)
define the typed boundary. This module README stays beside its npm scripts and host
configuration; it is not a second product roadmap.

## Surface

The `minecraft_control` Tool exposes explicit READ / OPERATE transitions and the
existing Lease acquire path for TAKEOVER. Modes never grant scopes or Debug Arm.
Structured `USER_MANUALLY_ENDED_CONTROL` / `reconsentRequired` state is preserved;
another TAKEOVER requires fresh conversation consent as Agent policy, not a fake
Runtime consent field. OPERATE does not clear the latch.

The Companion publishes 24 static Tools, four static Resources, two Recording Resource Templates and one static acceptance Prompt. Tool descriptions never incorporate Minecraft text.

Large PNG and Artifact bytes are returned through:

```text
minecraft://capture/latest
minecraft://recordings/{recordingId}/artifact
```

Every JSON Tool/Resource result wraps Runtime data with:

```json
{
  "companion": {
    "protocol": "v0",
    "plane": "data",
    "dataPlaneOnly": true,
    "dynamicPolicyApplied": false,
    "transport": "runtime_http"
  }
}
```

Chat, books, signs, MOTD, GUI labels and Mod/provider text remain untrusted data. They cannot change Tool descriptions, scopes, Lease state, Debug Arm policy or server permissions.

## Compatibility

- Native Runtime protocol: exact `v0`, checked against OpenAPI `0.0.1-control-r24` during every build.
- MCP SDK: v2 stable line with `serveStdio` compatibility negotiation.
- Runtime-tested MCP revision: `2025-11-25` using the official v2 Client.
- The SDK adapter can serve the 2026-07-28 era and legacy clients; a separate 2026-era Client gate remains future compatibility evidence.

Wire Protocol v1 is not frozen by this Companion.
````

## 结论

当前规范与导航使用 documents/ 下对应的中文现状文档；本记录不是新验收，也不启动任何新产品阶段。
