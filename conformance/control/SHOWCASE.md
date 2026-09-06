# Agent Control UI Showcase

Repeatable viewing cases, **not Unified Acceptance or a visual PASS verdict**.
Forge 1.20.1 and Fabric 26.2 are supported. No CUA/host input injection is used.
The existing Chrome, pointer art, 12-step easing and mode contract are unchanged.

From the repository root in PowerShell 7:

```powershell
./conformance/control/Invoke-ControlUiShowcase.ps1 -ListCases
./conformance/control/Invoke-ControlUiShowcase.ps1 -Target 1.20.1-forge -Case takeover-gui -HoldSeconds 4 -KeepClient
./conformance/control/Invoke-ControlUiShowcase.ps1 -Target 26.2-fabric -All -HoldSeconds 4 -KeepClient
```

One client at a time. Without `-KeepClient`, the Runner saves/quits and closes
Minecraft through its own Runtime. With it, the client remains at Title/READ,
Lease released, held keys/buttons zero. Repeat the same target command to reuse
that instance. Quit it normally before switching Target; the Runner refuses a
second Showcase client or an unexplained existing game client.

## Cases

| Case | What to watch |
| --- | --- |
| `read` | Title + world HUD, weak Presence, Fade In/Out |
| `operate` | Reversible typed inventory fixture, medium Presence |
| `takeover-gameplay` | Short W/S and Vanilla relative look; no GUI pointer |
| `pointer-move` | Near/far moves across Inventory slots and stone tooltip |
| `takeover-gui` | Eight stones: hover, pickup, slot-to-slot, real drag, cleanup |
| `mode-transition` | Three READ → OPERATE → TAKEOVER → READ cycles, same world/GUI |
| `ui-overlay` | Fixture Tutorial/System Toast + Inventory/Pause |
| `gui-scenes` | Title, Inventory, Pause, Options, scroll-only Language list |
| `manual-esc` | Optional physical Escape; **excluded from `-All`** |

`-All` runs the first eight in that fixed order. Setup/navigation and cleanup are
announced separately from the viewing intervals. Entering a world/opening a GUI
requires explicit TAKEOVER even when the following viewing case is READ.
OPERATE fixture mutation and gameplay GUI interaction are separate evidence.

`-HoldSeconds` accepts 0.25–60 seconds (default 4). It controls viewing dwell, not
pointer speed. READ's no-presence interval is **17 seconds without API polling**,
allowing the existing 15-second inactivity timeout and Fade Out to occur. This
does not change Runtime timing. Fixture toasts expire independently (up to 12s).

## Isolated local instance

The Runner launches the standard Loader/Gradle **development source-set** client
with a test-only init-script override. This is intentionally **not** a packaged
JAR runtime attestation; the packaged attestation already exists separately.
Normal build/toolchain dependencies must be available. First launch may resolve
Gradle/Minecraft dependencies. No extra standalone runtime-safety JAR is copied.

Instances are `runs/<target>/control-ui-showcase/`, ports 25601 / 25602. Worlds,
tokens, caches, logs and individual execution reports stay Git-ignored. The
Runner will not use arbitrary paths or existing important worlds. First use
creates a new Survival/Peaceful world through Vanilla GUI. Subsequent uses
require exactly one world in this dedicated directory. Leave its language English
and inventory slots 9–12 empty; unexpected worlds/items cause refusal, not deletion.

Eight stones are arranged using existing OPERATE + `debug.menu`, a short-lived
Debug Arm, current resource revision and item/count preconditions. Only the four
initially empty reserved slots are cleaned. Actual hover/click/drag uses TAKEOVER
and GAME_ROUTED input. No Persistent Write API or on-disk NBT editing is used;
Vanilla world creation and Save & Quit naturally save the test world.

The world-selection list currently lacks semantic row children. The Runner uses
an explicit first-row coordinate only inside the validated one-world test
instance. All actionable buttons/Inventory slots use the Interaction Tree.

## Repeatable Toast fixture

`ui-overlay` uses real Vanilla Tutorial/System Toast renderers, labelled
`Showcase Fixture`, not a natural tutorial/advancement. A button in the existing
Automation Probe diagnostic Screen is enabled only with the showcase launch
property. It is absent from normal launches; there is no new public Toast route.
No extra in-game test banner is added to the Operator Chrome. Actual layering,
spacing and visual quality remain for the human to judge.

## Optional physical Esc / interruption

```powershell
./conformance/control/Invoke-ControlUiShowcase.ps1 -Target 26.2-fabric -Case manual-esc -ManualTimeoutSeconds 180
```

The Runner opens Inventory in TAKEOVER, prints **现在请按一次 Esc**, then waits for
the Runtime's native-revocation count, READ, `reconsentRequired=true` and clean
input. It never simulates physical Esc. Afterward the client is deliberately left
open, even without `-KeepClient`: no automatic reacquire to close it.

If the human revokes during another case, execution stops and preserves the
latch. No pretend `userConsent` parameter exists. Reacquisition requires explicit
conversation consent and the existing control-acquire workflow. If revocation
interrupts an inventory case, its diagnostic items may remain in the isolated
world; they are reported, not silently overwritten on the next run.

## Runner verification (not a showcase for visual judgement)

```powershell
./conformance/control/Test-ControlUiShowcase.ps1
./conformance/control/Invoke-ControlUiShowcase.ps1 -Target 1.20.1-forge -Smoke
./conformance/control/Invoke-ControlUiShowcase.ps1 -Target 26.2-fabric -Smoke
```

Smoke covers Runner plumbing, real inventory movement/drag, fixture availability,
cleanup and shutdown with short holds. It does not run `-All` or physical Esc.
`PASS` in an execution report means actions completed, **not** that the UI looks
good. Visual feedback and the separate five-Target Unified Acceptance are pending.
