# Agent Control Model — Research Decision

> Date: 2026-09-05
> Status: Round 1 retained. Combined Rounds 2–4 IMPLEMENTATION COMPLETE; Unified Acceptance READY but NOT RUN. Wire Protocol v1 NOT FROZEN.
> Scope: Core input/presence UX only. No Persistent Write, new Phase, or E1/E2/E3 work.
> Prior UX closeout: PARTIAL; see `Artifacts/core/core-ux-closeout-20260905.json`.

## Recommendation

Adopt **READ / OPERATE / TAKEOVER as explicit work intentions**, not a permission
ladder. Authentication, scopes, Control Lease, Debug Arm and evidence authority
remain independent checks. A request must never silently upgrade its mode.

| Intention | Allowed behavior | Must not imply |
|---|---|---|
| READ / 读取 | Observation, Event, Capture, Recording, Artifact and Persistent Read | player input or game-state mutation; recording writes evidence files, not persisted world state |
| OPERATE / 操控 | explicitly authorized typed Fixture/Debug and non-player control-plane operations | GUI/Inventory clicks, WASD, camera, attack/use or player commands |
| TAKEOVER / 接管 | the existing leased GAME_ROUTED player path, including GUI, Container and normal player command packets | Debug authority, elevated server permissions or gameplay acceptance merely because the mode is active |

Player-style APIs should return a stable `TAKEOVER_REQUIRED`-equivalent result
outside TAKEOVER. Mode selection alone cannot satisfy a missing scope or Arm.
Read-only Debug capability/status queries remain READ. A typed Debug menu mutation
can be OPERATE only as a diagnostic mutation, never as a surrogate player click.

## Session and authority model

- Keep the existing single input Control Lease. Successful acquire/reacquire is
  the only entry to TAKEOVER; explicit release, TTL, bound WS disconnect and close
  remain its exit mechanisms. No second Lease system.
- Round 1 work intention belongs to the authenticated Runtime control session,
  shared across its HTTP connections. A random controlSessionId plus monotonic
  mode generation guards explicit transitions; stale session/generation is rejected.
  HTTP pooling/reconnection is not an intent transition. Token-lifetime principal
  identity is not multi-user tenancy; this is not a per-MCP-client mode system.
- Future Round 4 presentation should display actual activity: TAKEOVER owner first, then active OPERATE,
  then active READ. Another reader cannot overwrite a takeover's visible state.
  Merely possessing a token should not leave a permanent “reading” banner.
- Keep Presence Mode, Human Override Latch, Lease, Evidence Authority, Debug Arm
  and logical Pointer Ownership separate. Coordinate their transitions through
  small entry/exit barriers, not one combinatorial global state machine.
- First implementation should serialize effectful input gestures under the
  existing Lease and reject overlapping Debug/Fixture writes during TAKEOVER.
  One Lease currently permits multiple Operations; that is not yet a guarantee
  that two pointer trajectories or two cleanup routines cannot interfere.
- Switching to OPERATE must drain/cancel leased player input first. Switching
  from OPERATE to TAKEOVER must finish/cancel the affected mutation/batch first.
  Retain per-item cancellation and owner-thread resource/value checks; do not
  wait for arbitrary in-process Mod code forever or roll back already committed
  gameplay/Debug effects as if the operation were a transaction.

Round 1 places Fixture and typed Debug mutation under explicit OPERATE without
an input Lease. Scopes, Debug Arm, owner-thread resource/value preconditions and
PLAYTEST/FIXTURE/DEBUG_PRIVILEGED classification remain authoritative. A bounded
OPERATE admission guards queued/active work; conflicting intent changes fail
MODE_OPERATION_IN_PROGRESS until that work is finished or cancellation drains it.
An intent change is not rollback of a completed diagnostic mutation.

Companion control release no longer clears the independent cached Debug Arm.
Manual Esc falls back to READ and leaves the TAKEOVER-only reconsent latch set;
explicit OPERATE is permitted with its own authorization and does not clear the
latch. Reacquire is the only latch-clearing path. Conversation consent remains
Agent policy; the Runtime cannot verify chat or accept a fake consent field.

Round 1 classification is indexed in `conformance/control/control-mode-surface.json`:
74 formal HTTP operations, 24 MCP tools with typed action discriminators, retained
diagnostics and WS commands. Native GET/POST `/v0/control/mode` and MCP
`minecraft_control` status/set_mode expose stable state rather than error-string
inference. Only existing Lease acquire enters TAKEOVER. Ordinary HTTP disconnect
is not a new logical session; Lease-bound WS disconnect, TTL and Runtime close end
TAKEOVER. Mode transitions emit bounded audit/event metadata. Existing response
`mode=FIXTURE/DEBUG_PRIVILEGED` fields remain evidence authority, not work intention.

Current acceptance: `conformance/control/Invoke-ControlRound1Gate.ps1` and
`Artifacts/core/agent-control-round1-20260905.json`. This is working-tree candidate
evidence, not another clean-remote Phase 8 release attestation. Historical Phase
9A–9C drivers predate explicit intent: diagnostic bodies need OPERATE and player
sequences need TAKEOVER. In particular, the historical automated world-exit-during-
Debug-batch scenario cannot acquire TAKEOVER while that batch is still admitted;
Round 1 tests controlled rejection, batch cancellation/drain, then intent change.
The historical phase-wide live matrices were not rerun or relabelled as new evidence.

The fence is Runtime-local admission, not a distributed transaction. Existing
peer-v0 requests already sent to a Dedicated Server retain their acknowledgement /
timeout semantics: timeout does not prove that a remote mutation did not execute.
Reobserve authoritative state before retrying. No new remote cancellation or
multi-account isolation guarantee is claimed by the intention model.

Round 1 closeout also fixed first-use Recording finalization after Loader teardown:
Minecraft.close HEAD now drains the Runtime before resources/class loaders close;
the JVM hook is idempotent fallback. Five Targets passed cold Contact Sheet close.
Finalization errors retain a bounded typed stage/message and explicit non-ready
Bundle status; retained source tracks are not silently certified intact. The
historical Forge save-click timeout was not reproduced in three Pause cycles and
one Save & Quit; it remains historical, not a reason to repeat tests indefinitely.

## Human override and host cursor — implemented, unified acceptance pending

Implemented exclusive TAKEOVER: native keyboard, character/IME input, mouse
buttons, scroll and mouse movement do not affect Minecraft, except physical Esc
which immediately ends player control. Do not block OS Alt+Tab, window close or
input to other applications. Consume the first Esc; later Esc is Vanilla again.

On Esc: cancel pending gestures/Pipelines, invalidate their generation, release
held Runtime input, hide the logical pointer, fall back to READ and retain
`reconsentRequired=true`. Agent-routed Esc remains an ordinary GUI key. The
Companion must obtain explicit conversation reconsent before another acquire;
the Runtime must not accept a fabricated `userConsent=true` as proof.

An independently armed Debug credential is not automatically revoked just by an
input-mode change, but READ cannot use it to mutate. A session must explicitly
enter OPERATE and meet the existing authorization checks. Agents must not use
OPERATE/Debug to circumvent a person's instruction to stop. Reads remain usable.

During TAKEOVER, the host cursor **stays free on the supported standard paths**: no grab on entry,
focus, native click or routed click, and no cursor warp. The previous human-click capture grant is removed.
Block automatic grab at its entry point instead of repeatedly grabbing/releasing.
Focus loss still performs defensive cleanup; it does not itself end a valid
takeover. Outside takeover, restore ordinary Vanilla behavior.

This is a Minecraft input boundary, not a hostile-Mod or host-OS security sandbox.
A Mod polling GLFW directly, replacing callbacks or executing native code can
bypass cooperative hooks. Do not advertise protection against arbitrary local
code. Audit suppression counts and mode transitions, not private human key text.

## Virtual pointer: genuine routed input, separate rendering — implemented, unified acceptance pending

Use an internal absolute pointer in GUI and a logical relative/camera mode in
gameplay. Both are owned by the same input Lease/gesture generation, neither by
the Windows pointer. World camera motion still goes through Vanilla input and
sensitivity/inversion handling, not direct yaw/pitch mutation. No decorative
cursor is needed over the normal first-person crosshair.

For GUI actions:

```text
resolve semantic target
→ bounded deterministic trajectory
→ ordinary mouseMoved / hover / drag callbacks
→ re-resolve target and validate Screen/Menu identity and bounds
→ ordinary click/release and packet/server validation
```

Use a short fixed easing curve with a bounded duration/queue, not random jitter.
Recheck before each side effect. Resize, GUI-scale change, moving targets, Screen
replacement and cancellation must invalidate or explicitly recompute a gesture;
never complete a stale click at an old coordinate. Drag retains a gesture owner
until release/cancel, with no leftover held button on any transition.

The implemented pixel cursor is visible during TAKEOVER + GUI, with blue glow and
short appearance/disappearance easing. It represents the coordinates actually
delivered to Minecraft. Rendering a fake cursor while clicking elsewhere is not
acceptable. Hover/tooltip is real tested game content and **stays in captures**;
only the Operator cursor and chrome are excluded.

The earlier ambient-only `AgentInputContext` and coordinate cache have been
replaced by one-use callback admission plus explicit scheduled native provenance,
logical pointer state and bounded owner/drain sequencing. Queued gestures retain
immutable origin/context and revalidate on the owning thread. Do not infer trusted human origin merely from an absent ThreadLocal,
or let public requests select `NATIVE_HUMAN` themselves.

The [GLFW input guide](https://www.glfw.org/docs/latest/input_guide.html#events)
documents separate key/character callbacks and callback reentrancy from window
system calls. Its [cursor mode contract](https://www.glfw.org/docs/latest/input_guide.html#cursor_mode)
concerns the OS cursor; it is not an Agent logical-pointer contract. These are
reasons to test ingress provenance and the two coordinate domains independently.
This is a design inference, not proof that the proposed hooks already work.

## Operator chrome — implemented, unified acceptance pending

Implemented top-center Minecraft pixel typography, square/pixel-stepped borders,
Minecraft-scale spacing and a blue status palette. Reuse the proven edge glow,
short Fade and final Operator pass; do not introduce an OS overlay or a new
Render Plane. Suggested text/intensity:

| Activity | Text | Presentation |
|---|---|---|
| READ | 智能体正在读取您的实例 | quiet activity indicator; little or no perimeter glow |
| OPERATE | 智能体正在操控您的实例 | stronger blue indicator, diagnostic provenance in evidence |
| TAKEOVER | 智能体已接管您的实例 · Esc 以退出 | clear blue perimeter and escape instruction; GUI pointer |

Permission changes are immediate; only appearance fades. No model ID. Ordinary
HUD/Toast/Screen rendering precedes evidence readback, then Operator chrome and
pointer, then presentation. Preserve this explicit ordering, including loading
frames, async GPU readback and shutdown. Resize/fullscreen behavior remains a
five-Target acceptance case, not a claim about arbitrary post-present Mod hooks.

Timeline/Audit should record bounded pointer trajectory samples, gesture ID,
origin, mode transition and cancellation reason. These are operator metadata,
not proof of gameplay success. READ/OPERATE/TAKEOVER must never replace source,
perspective, resource identity or the existing contamination window.

## Reuse, change and execution order

Reuse: Lease/expiry/disconnect, Operation cancellation, ConditionEngine, semantic
target resolution, normal Screen/Menu/packet routing, Debug Arm/resource/value
checks, evidence classification, bounded capture queue, final Operator render
boundary, Fade and cached original title/icon restoration.

Round 1 changes Fixture's Lease coupling and mode-generation admission only.
Change in later authorized rounds: per-gesture serialization,
input-origin ingress, native suppression, current human cursor-capture grant,
GUI/world logical pointer routing, and chrome layout/copy. Keep Target hooks
explicit; do not promote new shared Loader abstractions before real evidence.

Historical decomposition below is retained for traceability. The user authorized the remaining design as one combined implementation; these are not separate delivery or acceptance rounds:

1. **Intent/authorization contract:** endpoint inventory, Fixture decision,
   actor identity, mode-transition concurrency, manual latch and stable errors.
   No implicit upgrades; reads remain independent.
2. **Exclusive takeover and host separation:** all native Minecraft input paths
   including character/scroll, no host grab/warp, generation/held-input cleanup.
   Human-only Esc/focus acceptance on Forge and Fabric, five-Target regression.
3. **Logical pointer and genuine GUI interaction:** bounded easing/hover/drag,
   camera parity, target revalidation and gesture serialization. Test dynamic
   Widgets/Inventory, cancellations, resize and concurrent requests.
4. **Pixel chrome and product acceptance:** top-center design, pointer visibility,
   deterministic capture/recording exclusion, five-Target runtime coverage and
   a new Human-visible Demo. Do not hide the current isolated Forge UI timeout.

Structural risks to resolve in those rounds: intent mistaken for authorization;
native callbacks/queued work misclassified across transitions; concurrent input
gestures sharing mutable MouseHandler state; hover causing Mod GUI side effects;
and non-cooperative Mod input/render hooks outside the supported boundary.

No round starts automatically. This research does not open Persistent Write or
any subsequent Phase/Extension and does not freeze Wire Protocol v1.

## Combined implementation contract (control-r24)

- Native key/character/move/button/scroll callbacks are scheduled with explicit native provenance. Modern Targets also guard IME preedit/status/replay. One-use, argument-bound Agent callback tickets cannot be reused by nested/native re-entry; an ambient routed scope is not callback authorization.
- TAKEOVER masks standard Minecraft key polling and clears prior native held bindings at owner-thread entry. It never installs OS-wide keyboard/mouse hooks; OS Alt+Tab, window close and system shortcuts remain outside the Minecraft suppression boundary.
- MouseHandler grab/release and InputConstants grab/warp entry points are guarded. Entering TAKEOVER releases any pre-existing Vanilla capture once. There is no native-click grant and no per-tick grab/release contest. Direct non-cooperative Mod GLFW/native code remains outside the supported trust boundary.
- One bounded 16-entry input sequence queue owns execution and cleanup, including legacy raw endpoints. Queued cancellation never cleans the active owner; stale admission fails before ownership. High-level GUI sequences cannot steal a Lease-owned raw held-button stream (POINTER_HELD); finish that stream with raw button-up or control cleanup first. Cleanup failure fail-closes further Agent input instead of handing off uncertain held state.
- GUI movement uses 12 deterministic smoothstep samples over 180 ms. Bounded drag uses deterministic samples and one owner. Frame identity, Screen revision, dimensions/scale, target identity and bounds are rechecked on the Minecraft owner thread before each motion and atomic press/scroll. A replaced Screen or changed viewport cannot receive the old gesture's release callback.
- Interaction identities are process-local monotonic IDs in a bounded 2048-entry map, not JVM addresses or external reflection. Eviction invalidates old guards rather than aliasing them.
- POST /v0/input/mouse/delta and Pipeline mouse.delta accept bounded relative pixel deltas. The existing Vanilla movement processor applies sensitivity/inversion; no yaw/pitch setter is used. Legacy absolute mouse.move is retained as virtual-coordinate compatibility, not host cursor positioning.
- ui.action hover uses the same trajectory and Vanilla callback path as click/drag. Hover/tooltip is tested game content and stays in Capture; only the Operator pixels are excluded.
- Presence is orthogonal to intention/authority. An authenticated active connection has a 15-second activity window; active responses, Recording, admitted OPERATE work or a TAKEOVER Lease keep Presence visible. No connection/work/Lease means visually idle even though the safe intention remains READ.
- Pixel top-center Chrome uses Minecraft font, stepped rectangular framing and one 160 ms animator for Presence, intensity, text crossfade and pointer fade. READ/OPERATE/TAKEOVER edge strengths are 0.25/0.55/1.0. Only TAKEOVER+GUI shows the pixel pointer. All Operator pixels follow content readback and precede present.
- Titles distinguish reading/operating/takeover without repeated suffixes. Only TAKEOVER uses the existing placeholder icon; leaving it restores the actual cached Minecraft icon. Inactive Presence and shutdown restore the original title.
- Native/MCP V0 is 0.0.1-control-r24: 75 formal HTTP operations and the same 24 MCP tools. Modes, scopes, Lease, Debug Arm, Resource Version, manual latch and evidence authority stay independent.
- This implementation does not promise distributed rollback of previously sent Peer packets, arbitrary native-Mod containment, a new text/JVM/filesystem RPC, or any Phase 9/10/Extension work.

Current automated entry points: Invoke-ControlImplementationStaticGate.ps1 and
Invoke-ControlImplementationSmoke.ps1. Human Esc/IME/focus/host-cursor validation,
visual comfort, arbitrary Mod GUI compatibility and the unified five-Target
human/visual matrix remain explicitly pending the next authorized acceptance.
