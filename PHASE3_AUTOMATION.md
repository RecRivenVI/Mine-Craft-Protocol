# Phase 3 UI and Input Automation

> Status: complete for all five Runtime Targets (1.21.1/26.1.2 promoted during Phase 6)  
> Date: 2026-08-28  
> Contract status: unstable V0; Wire Protocol v1 is not frozen

## Implemented Targets

```text
1.20.1-forge
26.2-neoforge
26.2-fabric
```

NeoForge 1.21.1 and NeoForge 26.1.2 remain buildable placeholders and do not claim this Runtime surface.

## Delivered Surface

- Semantic roles, coverage, node revisions, interaction points and actions in Interaction Tree.
- Selector matching by ID, role, label, class, substring, Slot and explicit `nth`.
- Ambiguity and not-found typed failures.
- Bounds-center coordinate generation.
- Tree-targeted and explicit/vision-coordinate `ui.action` through normal Minecraft input handlers.
- Runtime `wait.until` and `assert.that` for Screen and UI-existence conditions.
- Cancellable input Pipeline with bounded delay, mouse, keyboard, UI, wait and assert steps.
- Multi-key chord, scroll, segmented drag and selector-to-selector drag support.
- Per-step Control Lease validation and finally-style input cleanup.
- Standard Minecraft Widget-based Mod GUI Fixture with explicit evidence contamination.
- Latest-256 structured Render Facts on NeoForge/Fabric 26.2.
- Honest Render Facts unavailability on Forge 1.20.1.

## Real Runtime Evidence

`conformance/phase3/Invoke-Phase3AutomationConformance.ps1` passed on all three Targets with `-RequireWorldLoop`.

| Gate | Forge 1.20.1 | NeoForge 26.2 | Fabric 26.2 |
|---|---:|---:|---:|
| Interaction Tree semantics | PASS | PASS | PASS |
| Selector and coordinate generation | PASS | PASS | PASS |
| Standard Mod GUI Fixture | PASS | PASS | PASS |
| Vision coordinate action | PASS | PASS | PASS |
| Runtime wait/assert | PASS | PASS | PASS |
| Scroll | PASS | PASS | PASS |
| Segmented drag | PASS | PASS | PASS |
| Multi-key chord | PASS | PASS | PASS |
| Pipeline cancellation cleanup | PASS | PASS | PASS |
| Structured Render Facts | unavailable | PASS | PASS |
| Title → World → Inventory Pipeline | PASS | PASS | PASS |
| Slot Screen hook | PASS | PASS | PASS |
| Menu dispatch | PASS | PASS | PASS |
| Normal container packet | PASS | PASS | PASS |
| Server validation | PASS | PASS | PASS |

The complete world scenario is a single Pipeline:

```text
semantic Singleplayer click
  → wait SelectWorldScreen
  → coordinate fallback selects saved world row
  → semantic Play Selected World click
  → wait world Screen closes
  → real E key opens Inventory
  → wait InventoryScreen
  → assert Slot 0 exists
  → semantic Slot 0 click
  → real E key closes Inventory
  → assert no Screen
```

## Fidelity Evidence

UI actions report:

```text
entryLayer=GAME_ROUTED_RAW
targetingSource=interaction_tree | vision | explicit_coordinate
directBusinessCallUsed=false
directMutationUsed=false
```

The Slot scenario advances all four independent trace counters:

```text
Screen slot click
Menu dispatch
client container packet
Server thread validation
```

The test Screen is different: opening it is a `DIRECT` Fixture Arrange step with `perspective=fixture` and `evidenceContaminated=true`. Clicking its widgets still uses GAME_ROUTED input. This distinction prevents Fixture setup from masquerading as gameplay acceptance.

## Render Facts Boundary

26.2 facts contain category, implementation class, structured bounds, sequence, Client tick and Screen revision. They are rendering observations only and explicitly return `semanticInference=false`.

No promise is made that a custom framebuffer or texture contains recoverable buttons, graphs, icons or text. Screenshot/vision remains the final fallback.

## Exit Decision

Phase 3 exit conditions are satisfied for all five Runtime Targets:

- a Pipeline can enter a world and operate a real Container;
- Vanilla and standard Widget-based Mod GUI controls are discoverable and actionable;
- scrolling, dragging and simultaneous key states are executable;
- cancellation demonstrably clears held input;
- action provenance proves the actual input entry path;
- Render Facts coverage is reported without invented semantics.

The next execution-plan phase is Phase 4: V1 Live Observation, Capture and Integrated Server authority boundaries.
