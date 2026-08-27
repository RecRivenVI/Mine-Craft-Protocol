# Phase 6 All-Target Runtime and Dedicated Server Peer

> Status: implemented and runtime-verified on all five Targets  
> Date: 2026-08-28  
> Contract status: unstable V0; `peer-v0` and Wire Protocol v1 are not frozen

## Scope Delivered

Phase 6 completed two related objectives:

1. NeoForge 1.21.1 and NeoForge 26.1.2 were promoted from buildable placeholders to the same Phase 5 Runtime capability level as the other Targets.
2. All five Targets gained an optional Dedicated Server Peer carried by Minecraft's normal custom-payload connection.

The current Target matrix is:

| Target | Full Runtime | Integrated authority | Physical Dedicated Server | Remote Peer round trip |
|---|---:|---:|---:|---:|
| Forge 1.20.1 | verified | verified | verified | verified |
| NeoForge 1.21.1 | verified | verified | verified | verified |
| NeoForge 26.1.2 | verified | verified | verified | verified |
| NeoForge 26.2 | verified | verified | verified | verified |
| Fabric 26.2 | verified | verified | verified | verified |

No Target depends on another Target's Runtime implementation.

## Peer Transport

The Peer uses the identifier `minecraft_protocol:peer_v0` and an unstable protocol label `peer-v0`.

```text
Client Runtime
  → hello
Dedicated/Integrated logical Server
  → hello_ack + serverTick + server gates

Client Runtime
  → request(requestId, typed operation, params)
Server main thread
  → response(requestId, serverTick, data | typed error)
```

Loader-specific transports are explicit:

- Forge 1.20.1: optional `SimpleChannel`, `consumerMainThread`.
- NeoForge 1.21.1: `PayloadRegistrar` plus `DirectionalPayloadHandler`.
- NeoForge 26.1.2/26.2: optional bidirectional play payload with explicit server/client handlers.
- Fabric 26.2: Fabric Networking API `serverboundPlay` / `clientboundPlay` registries and play receivers.

Fabric uses the official `fabric-api` aggregate dependency at `0.158.0+26.2`. The dependency is declared explicitly in both Gradle and `fabric.mod.json`.

## Typed Peer Operations

The V0 Peer accepts only:

```text
peer.status
player.get
world.block.get
world.entities.query
world.fingerprint
fixture.player.teleport
debug.player.health
debug.world.block
```

There is no generic field setter, reflection, method invocation, object traversal, filesystem access, shell access or ClassLoader RPC.

World block reads and Debug writes use loaded LIVE state only. They do not request a chunk load and do not inspect persistent storage. Debug block write accepts `expectedBlockId` as a value precondition.

## Authority and Evidence

Peer results declare:

```text
perspective=server_authoritative_live
source=dedicated_server_peer
authority=server_authoritative
dataSource=LIVE
storageAccessed=false
peerAuthenticated=true
serverTick
```

The normal `/v0/server/*` endpoints choose:

```text
Integrated Server present
  → direct Integrated Server scheduling

real remote connection + Peer negotiated
  → peer-v0 request

real remote connection without Peer
  → typed SERVER_PEER_UNAVAILABLE
```

`MCP_PEER_FORCE=true` is a development/conformance switch only. It routes Integrated Server calls through the payload channel so codec and reply behavior can be exercised on every Target. It does not change the production topology rule.

## Permission Model

Peer read operations use the authenticated Minecraft player connection. Fixture and Debug require both:

- explicit server process flag: `MCP_PEER_ALLOW_FIXTURE=true` / `MCP_PEER_ALLOW_DEBUG=true` (or matching system properties);
- operator or Integrated Server owner authority.

The Client Runtime independently enforces HTTP Bearer authentication, scopes, Control Lease and Debug Arm before calling the typed Peer operation. The server does not trust the Client Runtime's scope decision as a substitute for its own flag/operator gate.

Fixture/Debug results retain mode, mechanism and `evidenceContaminated=true`. The Peer does not allow them to masquerade as PLAYTEST evidence.

## Connection Lifecycle

- Hello is retried every two seconds until acknowledged.
- Requests have generated IDs and a five-second timeout.
- JSON messages are bounded below 32 KiB; entity results are capped at 128 entries and a 128-block radius.
- A connection identity change resets negotiation.
- Disconnect completes pending requests with `SERVER_PEER_DISCONNECTED`, clears them, and reports `connected=false`.
- A remote server without the channel degrades to `SERVER_PEER_UNAVAILABLE`.

## Conformance

`Invoke-Phase6PeerConformance.ps1` runs against an Integrated Server with forced payload routing. On all five Targets it verified:

- unavailable status at title;
- `peer-v0` handshake;
- player/block/entity authoritative reads;
- multi-provider State Frame with actual Peer source propagation;
- short state-only Recording backed by Peer reads;
- LIVE/no-storage provenance;
- Fixture teleport and typed Debug health/block with server flags and owner authority;
- Debug Arm and block value precondition;
- disconnect cleanup and zero pending requests.

`Invoke-Phase6DedicatedPeerConformance.ps1` runs a Client against an independent server process without forced routing. On all five Targets it verified:

- real remote connection;
- negotiated Peer;
- production Peer routing of player/entity reads;
- server-authoritative provenance;
- non-operator Fixture/Debug denial;
- remote disconnect cleanup.

The dedicated development servers were run in offline mode only to allow local development identities to connect. Their run directories are ignored test state, not product defaults. EULA acceptance for all five development server directories was explicitly authorized by the repository owner on 2026-08-28.

## Known Limits

- `peer-v0` is an internal V0 experiment, not a frozen public wire protocol.
- Physical remote mutation conformance used non-operator players and correctly reported Fixture/Debug unavailable. The same serialized mutation paths were verified through the Integrated Server owner harness.
- NeoForge 26.1.2 Vulkan capture has a run configuration but has not yet passed its own Vulkan gate; it is not reported as verified.
- Peer state recording currently composes existing Provider/State Frame reads through authoritative endpoints; no new global transaction or world revision is introduced.

## Commands

```powershell
.\conformance\phase6\Invoke-Phase6LocalGate.ps1 -Offline

# Integrated serialization harness: launch Client with MCP_PEER_FORCE=true first.
.\conformance\phase6\Invoke-Phase6PeerConformance.ps1 `
  -BaseUri http://127.0.0.1:<target-port> `
  -TokenFile <game-directory>\minecraft-protocol\token `
  -ExpectedTarget <target>

# Physical Dedicated Server: launch server and quick-play Client without MCP_PEER_FORCE.
.\conformance\phase6\Invoke-Phase6DedicatedPeerConformance.ps1 `
  -BaseUri http://127.0.0.1:<target-port> `
  -TokenFile <game-directory>\minecraft-protocol\token `
  -ExpectedTarget <target>
```

Supporting references: [NeoForge payload handling](https://docs.neoforged.net/docs/1.21.4/networking/payload/), [Fabric networking](https://docs.fabricmc.net/develop/networking), [Fabric API 0.158.0+26.2](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.158.0%2B26.2/).
