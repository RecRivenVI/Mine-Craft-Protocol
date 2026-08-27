# Mine-Craft-Protocol MCP Companion

Independent TypeScript adapter from MCP to the authenticated Minecraft Runtime HTTP protocol. It is not embedded in Minecraft and does not change the native Runtime contract.

## Build and test

```powershell
cd companion
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
node companion\dist\index.js
```

The Companion uses `serveStdio(factory)` and logs only to stderr. stdout is reserved for MCP JSON-RPC.

See `mcp.example.json` for host configuration. Do not store token values in the repository.

## Surface

The Companion publishes 19 static Tools, four static Resources, two Recording Resource Templates and one static acceptance Prompt. Tool descriptions never incorporate Minecraft text.

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

- Native Runtime protocol: exact `v0`, checked against OpenAPI `0.0.1-phase8` during every build.
- MCP SDK: v2 stable line with `serveStdio` compatibility negotiation.
- Runtime-tested MCP revision: `2025-11-25` using the official v2 Client.
- The SDK adapter can serve the 2026-07-28 era and legacy clients; a separate 2026-era Client gate remains future compatibility evidence.

Wire Protocol v1 is not frozen by this Companion.
