# Agent Control Model — Research Decision

> Date: 2026-09-05
> Status: PROPOSAL — NOT IMPLEMENTED / NOT A FROZEN PROTOCOL
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
- Work intention belongs to an authenticated logical client session, not an HTTP
  connection ID. HTTP pooling/reconnection is not an intent transition. Current
  token-lifetime principal identity is not a multi-user account system.
- The window displays actual activity: TAKEOVER owner first, then active OPERATE,
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

The existing formal Phase 9C Debug path already does **not** require the input
Lease. Existing Fixture routes and `minecraft_fixture` still do. Moving Fixture
under OPERATE requires a deliberate authorization/concurrency contract change,
not removing a check and calling it a rename. Existing value preconditions and
PLAYTEST/FIXTURE/DEBUG_PRIVILEGED classification remain authoritative.

The Companion currently clears its cached Debug Arm ID on control release,
although the formal Runtime Arm is independent. Future mode handling must
distinguish forgetting a cached handle from actually disarming the Runtime;
that behavior is reviewed here, not changed by this research.

## Human override and host cursor

Recommend exclusive TAKEOVER: native keyboard, character/IME input, mouse
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

During TAKEOVER, the host cursor should **always stay free**: no grab on entry,
focus, native click or routed click, and no cursor warp. Remove the current
human-click capture grant when this new contract is explicitly implemented.
Block automatic grab at its entry point instead of repeatedly grabbing/releasing.
Focus loss still performs defensive cleanup; it does not itself end a valid
takeover. Outside takeover, restore ordinary Vanilla behavior.

This is a Minecraft input boundary, not a hostile-Mod or host-OS security sandbox.
A Mod polling GLFW directly, replacing callbacks or executing native code can
bypass cooperative hooks. Do not advertise protection against arbitrary local
code. Audit suppression counts and mode transitions, not private human key text.

## Virtual pointer: genuine routed input, separate rendering

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

The future pixel cursor is visible during TAKEOVER + GUI, with blue glow and
short appearance/disappearance easing. It represents the coordinates actually
delivered to Minecraft. Rendering a fake cursor while clicking elsewhere is not
acceptable. Hover/tooltip is real tested game content and **stays in captures**;
only the Operator cursor and chrome are excluded.

Current `AgentInputContext` is synchronous and scoped; the existing coordinate
cache is not this new pointer model. Future queued gestures need immutable
origin/owner/generation attached at ingress and revalidated at owner-thread
dispatch. Do not infer trusted human origin merely from an absent ThreadLocal,
or let public requests select `NATIVE_HUMAN` themselves.

The [GLFW input guide](https://www.glfw.org/docs/latest/input_guide.html#events)
documents separate key/character callbacks and callback reentrancy from window
system calls. Its [cursor mode contract](https://www.glfw.org/docs/latest/input_guide.html#cursor_mode)
concerns the OS cursor; it is not an Agent logical-pointer contract. These are
reasons to test ingress provenance and the two coordinate domains independently.
This is a design inference, not proof that the proposed hooks already work.

## Operator chrome

Recommend top-center Minecraft pixel typography, square/pixel-stepped borders,
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

Change only when authorized: Fixture's Lease coupling, per-gesture serialization,
input-origin ingress, native suppression, current human cursor-capture grant,
GUI/world logical pointer routing, and chrome layout/copy. Keep Target hooks
explicit; do not promote new shared Loader abstractions before real evidence.

Proposed small rounds, each with its own exit gate:

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
