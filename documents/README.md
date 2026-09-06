# 文档导航与权威关系

本目录是项目级文档唯一导航。

当前结构已迁移为 `versions / components / instances / documents / validations / gradle`。
产品组件与 Target 分层注册；Companion 不是 Gradle module。规范与 Instance Cascade
详见 [AGENTS](../AGENTS.md)。验证按 core / compatibility / releases 的责任分类，
Phase 编号只在这些分类内部保留。普通实例只有三种 variant；Showcase/Vulkan 是
`validations/.local/` 内的隔离场景。本轮仅结构/构建/路径迁移，不改变产品合同。

仓库根目录保留 README 与 AGENTS；构建/源码/
协议/执行脚本/不可变证据各归原目录，不因文档整理改变产品实现。

## 先读什么

| 要回答的问题 | 当前权威入口 | 职责 |
|---|---|---|
| 产品承诺是什么？ | [Core Vision](product/vision.md) | committed Autonomous Testing Core / Preview 边界 |
| E1/E2/E3 要不要做？ | [Extensions](product/extensions.md) | 独立、非阻塞、可取消的 Portfolio |
| 已完成什么、下一门槛是什么？ | [Execution Plan](product/execution-plan.md) | 当前状态台账与分阶段路线 |
| Runtime / Companion 如何工作？ | [Architecture](architecture/overview.md) | 当前采用的实现与 Target 差异 |
| 什么是安全边界？ | [Threat Model](architecture/threat-model.md) | 已实现防线、残余风险和未来安全前提 |
| READ / OPERATE / TAKEOVER 如何区分？ | [Agent Control](architecture/agent-control.md) | CURRENT / NOT YET ACCEPTED / FUTURE 明确区分 |
| 原生协议和 MCP 怎样接入？ | [Native V0 Reference](reference/protocol-v0.md) / [Companion README](../components/companion/README.md) | OpenAPI 为正式字段源；模块说明继续共址 |
| 怎么反复看 UI？ | [Control UI Showcase](testing/control-ui-showcase.md) | 8 个自动案例 + 可选真人 Esc；READY 不等于验收 PASS |
| 大型整合包怎么测？ | [Modpack Compatibility](testing/modpack-compatibility.md) | 四个固定 ATM 资产、Prism 工作流与 Tier 0–5；尚未开始 |

具体 API 以 [OpenAPI](../components/protocol-schema/src/main/openapi/minecraft-control-v0.json)、
Runtime capability / operation descriptors 和当前源码为准；文档不凭空增加 endpoint。
所有命令默认从仓库根目录执行；文中反引号路径默认是仓库相对路径，Markdown 链接则相对所在文档。

## 权威和状态规则

1. [AGENTS.md](../AGENTS.md) 负责仓库工作规则；用户明确任务边界始终必须遵守。
2. Core 产品范围只由 Vision 定义；Extensions 不反向扩大 Core DoD。
3. 当前进度/下一 Gate 只查 Execution Plan，不能从历史文档中的“next”继续开发。
4. Architecture / Agent Control / Threat Model 说明采用的设计；实验、未来和未验收项需明确标记。
5. **历史 PASS/FAIL 只属于绑定的时间/版本/SHA**；实现 COMPLETE、Showcase READY、
   自动 smoke PASS、真人验收 PASS、clean-remote attestation 是不同层次。
6. `validations/results/` 原始 JSON、签名/commit-bound 数据保持不变。历史 Markdown 正文也保留，
   本次只添加归档说明；历史旧路径按当时上下文解释，不改写历史结果来匹配新目录。

当前 Agent Control Round 1 已 PASS；Rounds 2–4 Implementation COMPLETE；
统一人工/视觉验收未执行。Showcase READY 不改变这个状态。
Persistent Write 仍未实现，独立 Entry Review 仅 READY，不能当作 OPEN。

<a id="phase-records"></a>
## Phase 历史与当前分解

- [Phase 0 Probe](phases/phase0-probe.md)
- [Phase 2 Protocol Core](phases/phase2-protocol-core.md)
- [Phase 3 Automation](phases/phase3-automation.md)
- [Phase 4 Observation](phases/phase4-observation.md)
- [Phase 5 Recording / Debug](phases/phase5-recording-debug.md)
- [Phase 6 Dedicated Server Peer](phases/phase6-dedicated-server-peer.md)
- [Phase 7 V1 Alignment](phases/phase7-v1-alignment.md)
- [Phase 8 MCP Companion](phases/phase8-mcp-companion.md)
- [Phase 8 historical hardening evidence](phases/phase8-hardening-evidence.md)
- [Phase 9 Implementation Plan](phases/phase9-implementation-plan.md)：当前分解 + 9A–9D 历史证据时间线。

Phase 1 没有独立原始 Markdown；构建/Schema 决策见 ADR 与 Phase 0 promotion，
不新建一份空壳 Phase 1 来填编号。

旧 `Invoke-Phase9AStaticGate.ps1` 仍绑定 Phase-9A-only 的文档状态和旧存储标记，
在本次整理前的当前分支上即失败；本次只修路径，不伪造旧门禁 PASS。
当前控制门禁见 [Control Implementation Static Gate](../validations/tests/core/control/Invoke-ControlImplementationStaticGate.ps1)，
当前 9B 门禁见 [Phase9B Static Gate](../validations/tests/core/phase9/Invoke-Phase9BStaticGate.ps1)。
历史说明/脚本不自动成为当前 release instruction。

## Architecture Decisions

- [ADR 0001 — Explicit Target Governance](architecture/adr/0001-explicit-target-governance.md)
- [ADR 0002 — Phase 0 Input / Render Hooks](architecture/adr/0002-phase0-input-render-hooks.md)
- [ADR 0003 — OpenAPI V0 source](architecture/adr/0003-openapi-v0-contract.md)
- [ADR 0004 — V1 loopback release profile](architecture/adr/0004-v1-loopback-release-profile.md)

Loopback ADR 原来在根目录也叫 ADR-0001，与 Target Governance 编号冲突。
本次改为 0004 并保留旧身份说明；安全决定本身未变，历史证据仍保留旧编号。

## 文档维护与检查

- 产品/架构/路线不要回到根目录散放；历史阶段放 `phases/`，实际测试说明放 `testing/`。
- `components/companion/README.md` 保留 npm、stdio、host config 近源说明；不复制产品路线。
- 执行脚本仍在 `validations/tests/`；只为路径迁移更新引用，不修改 Runtime 或协议语义。
- 轻量检查：[Invoke-DocumentationGate.ps1](../validations/tests/core/repository/Invoke-DocumentationGate.ps1)。
  它检查本地链接/锚点、当前文档路径引用、导航覆盖及基本权威约束，不下载外部资源、不启动游戏。
- 外部资料用于来源说明；Prism CLI 已按官方文档核对，但本机四个实例/版本尚未由用户固定。

## 前次文档治理清点（历史定位）

清点了 **24 份 tracked Markdown**：根目录 19、原 adr/ 3、Companion 1、
conformance Showcase 1；未发现其他 tracked README/design/plan 或 txt/rst/adoc 文档。
当时 22 份用 `git mv` 迁移，AGENTS 与 Companion README 留在原位。
本次新建根 README、此导航和 Modpack 计划；不创建额外一页式文档碎片。

<!-- migration-inventory:begin -->
| 原路径（历史定位） | 当前去向 / 保留位置 | 分类与处置 |
|---|---|---|
| `ADR-0001-V1-LOOPBACK-RELEASE-PROFILE.md` | [documents/architecture/adr/0004-v1-loopback-release-profile.md](architecture/adr/0004-v1-loopback-release-profile.md) | Architecture / ADR |
| `AGENTS.md` | [AGENTS.md](../AGENTS.md) | 根目录治理 / 必须共址 |
| `AGENT_CONTROL_MODEL_RESEARCH.md` | [documents/architecture/agent-control.md](architecture/agent-control.md) | CURRENT 已采用架构；人工验收未完成 |
| `ARCHITECTURE.md` | [documents/architecture/overview.md](architecture/overview.md) | CURRENT Architecture / 安全 |
| `PHASE0_PROBE.md` | [documents/phases/phase0-probe.md](phases/phase0-probe.md) | Historical phase record |
| `PHASE2_PROTOCOL_CORE.md` | [documents/phases/phase2-protocol-core.md](phases/phase2-protocol-core.md) | Historical phase record |
| `PHASE3_AUTOMATION.md` | [documents/phases/phase3-automation.md](phases/phase3-automation.md) | Historical phase record |
| `PHASE4_OBSERVATION.md` | [documents/phases/phase4-observation.md](phases/phase4-observation.md) | Historical phase record |
| `PHASE5_RECORDING_DEBUG.md` | [documents/phases/phase5-recording-debug.md](phases/phase5-recording-debug.md) | Historical phase record |
| `PHASE6_DEDICATED_SERVER_PEER.md` | [documents/phases/phase6-dedicated-server-peer.md](phases/phase6-dedicated-server-peer.md) | Historical phase record |
| `PHASE7_V1_ALIGNMENT.md` | [documents/phases/phase7-v1-alignment.md](phases/phase7-v1-alignment.md) | Historical phase record |
| `PHASE8_HARDENING_EVIDENCE.md` | [documents/phases/phase8-hardening-evidence.md](phases/phase8-hardening-evidence.md) | Historical phase record |
| `PHASE8_MCP_COMPANION.md` | [documents/phases/phase8-mcp-companion.md](phases/phase8-mcp-companion.md) | Historical phase record |
| `PHASE9_IMPLEMENTATION_PLAN.md` | [documents/phases/phase9-implementation-plan.md](phases/phase9-implementation-plan.md) | 当前 Phase 9 分解 + 有标签的历史证据 |
| `PLATFORM_EXTENSION_GOALS.md` | [documents/product/extensions.md](product/extensions.md) | Optional Portfolio / 非 Core Gate |
| `PLATFORM_VISION.md` | [documents/product/vision.md](product/vision.md) | CURRENT Core 产品合同 |
| `PROJECT_EXECUTION_PLAN.md` | [documents/product/execution-plan.md](product/execution-plan.md) | CURRENT 状态与执行计划 |
| `PROTOCOL_V0_DRAFT.md` | [documents/reference/protocol-v0.md](reference/protocol-v0.md) | Developer reference；Schema 才是规范源 |
| `THREAT_MODEL.md` | [documents/architecture/threat-model.md](architecture/threat-model.md) | CURRENT Architecture / 安全 |
| `adr/0001-explicit-target-governance.md` | [documents/architecture/adr/0001-explicit-target-governance.md](architecture/adr/0001-explicit-target-governance.md) | Architecture / ADR |
| `adr/0002-phase0-input-render-hooks.md` | [documents/architecture/adr/0002-phase0-input-render-hooks.md](architecture/adr/0002-phase0-input-render-hooks.md) | Architecture / ADR |
| `adr/0003-openapi-v0-contract.md` | [documents/architecture/adr/0003-openapi-v0-contract.md](architecture/adr/0003-openapi-v0-contract.md) | Architecture / ADR |
| `companion/README.md` | [components/companion/README.md](../components/companion/README.md) | Developer reference / 模块共址 |
| `validations/tests/control/SHOWCASE.md` | [documents/testing/control-ui-showcase.md](testing/control-ui-showcase.md) | Testing / 可重复展示，非最终验收 |
<!-- migration-inventory:end -->

整理同时修正了当前文档中的旧 Tool 数、旧协议版本、Fixture/Debug 与输入 Lease 的旧耦合、
Research 已实现却仍写 Future、LAN 当前可用错觉及过期“下一阶段”措辞。
旧 Phase 记录的测试数、SHA、时间和失败原文没有被重写。无产品功能改动；
Prism 环境未访问/修改，四包测试未执行。
