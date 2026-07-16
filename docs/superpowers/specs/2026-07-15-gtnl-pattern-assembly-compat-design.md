# GTNL Pattern Assembly Compatibility Design

## Problem

Wildcard patterns expand one configured rule into multiple AE processing pattern details. The normal AE interface and GregTech ME crafting input hatch have compatibility mixins that call `WildcardPatternGenerator.generateAllDetails` and advertise every generated detail.

GT Not Leisure's `SuperCraftingInputHatchME` does not use either compatibility path. Each physical slot constructs one `PatternSlot`, calls `ICraftingPatternItem.getPatternForItem` once, stores that single detail, and advertises it once from `provideCrafting`. For an unexpanded wildcard pattern, the singular API intentionally returns a display detail using the dragged representative input and output. Consequently, the GTNL assembly exposes only the representative recipe even though the wildcard GUI reports the full expansion count.

The observed behavior follows directly: a wildcard configured from an aluminium seed advertises aluminium plate but not bronze plate; the same rule configured from a bronze seed advertises bronze plate but not aluminium plate.

## Scope

Add optional compatibility for GTNL's `SuperCraftingInputHatchME` in this mod. The change must:

- advertise every generated detail for a wildcard pattern stored in a GTNL assembly slot;
- route every advertised detail back to the correct physical slot;
- make the selected generated detail the slot's active recipe while its inputs are stored;
- persist enough active-pattern identity to recover safely after a world reload;
- preserve existing behavior for ordinary encoded patterns, AE interfaces, GregTech crafting input hatches, and installations without GTNL;
- support both regular and composite wildcard patterns through the existing generator dispatch.

Changing ore-dictionary matching, the wildcard GUI, or GTNL source code is outside this fix.

## Considered Approaches

### 1. Optional wildcard-side GTNL mixin

Intercept GTNL registration and dispatch from this mod. Expand wildcard stacks with `WildcardPatternGenerator.generateAllDetails`, map each detail to its owning GTNL slot, and track the active generated detail per slot.

This is the selected approach. It can ship with the wildcard mod and closely follows the already proven `MTEHatchCraftingInputMEMixin` behavior. Its main cost is coupling to GTNL's internal class and method structure.

### 2. Shared plural-pattern API

Introduce an interface such as `IExpandedCraftingPatternItem` that exposes a list of details, then update GTNL to consume it and manage active slot state.

This is cleaner long term, but it requires coordinated changes and releases in both projects. It is not suitable as the immediate fix.

### 3. One synthetic multi-output detail

Return all wildcard outputs from the singular AE pattern API.

This is rejected because it changes recipe semantics: AE would treat one operation as producing all expanded outputs rather than selecting one material-specific recipe.

## Design

### Optional Integration Boundary

Keep GTNL absent from the compile and runtime dependency graph. Its `dev-290` build is distributed through GitHub Actions rather than a stable Maven coordinate, and making it a build dependency would make this mod less reproducible.

Use string-targeted `@Pseudo` mixins plus a Mixin config plugin that checks for the `sciencenotleisure` target class before applying them. All method signatures exposed to the mixins use types already supplied by AE2, GT5U, Minecraft, or this mod. Small local slot and cache bridge interfaces carry operations between the outer assembly mixin and the nested slot mixin. These interfaces live in `compat.gtnl`, outside the configured Mixin package, so Mixin never attempts to parse them as mixin classes. Reflective access is limited to GTNL-owned fields whose types cannot be named without a hard dependency.

The config plugin reads target bytecode with Mixin's relocated ASM API (`org.spongepowered.asm.lib.*`). This avoids a runtime dependency on an external ASM package that is not guaranteed to exist in the 1.7.10 launch environment. The structural gate requires both GTNL's AE-owned item/fluid buffer fields, the methods used by the injections, and the hatch's direct `ICraftingProvider` interface before either optional mixin is applied. The `provideCrafting` injection also checks that interface before cancelling GTNL behavior, so a future structural mismatch still fails closed at runtime.

The integration targets `com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME` and its nested `PatternSlot` implementation. GTNL injections use optional requirements and validate all reflected members before cancelling original behavior. Target selection must fail closed: if a future GTNL version changes the expected methods or fields, ordinary patterns must remain usable and the compatibility code must not partially advertise wildcard details without a valid dispatch map.

### Registration Flow

At `provideCrafting`:

1. Preserve GTNL's active-state check.
2. Iterate its physical pattern slots.
3. For an ordinary pattern, retain GTNL's existing single-detail registration.
4. For a wildcard pattern, call `WildcardPatternGenerator.generateAllDetails(patternStack, world)`.
5. Store every returned detail in `patternDetailsPatternSlotMap` with a preferred owning physical slot.
6. Advertise every detail with `ICraftingProviderHelper.addCraftingOption`.

An empty or invalid expansion advertises nothing for that wildcard slot and must not affect neighboring slots.

When multiple physical slots advertise equal generated details, GTNL's map can retain only one preferred owner for that key. Dispatch therefore probes the preferred attached slot first and then every other attached slot in physical order, rechecking generated-pattern ownership. An occupied equivalent slot cannot hide another free slot that can accept the same AE request.

Each physical slot caches its expanded details and an ID-to-detail index when it is constructed or registered. Dispatch ownership checks query that index directly; they never regenerate the full wildcard expansion while probing fallback slots.

### Dispatch And Active Detail

At `pushPattern`:

1. Resolve the requested detail through `patternDetailsPatternSlotMap`.
2. Reject the request cleanly if no current physical slot owns it.
3. For a wildcard slot, set the requested generated detail as that slot's active detail before inserting inputs.
4. Preserve GTNL's fluid-packet checks and `insertItemsAndFluids` behavior.
5. If insertion fails, do not leave a newly selected detail active unless the slot already contains inputs for it.

While a wildcard slot contains AE-buffered inputs, methods used by GT recipe matching, especially `getPatternDetails` and `getPatternInputs`, must resolve against the active generated detail. Once the AE buffers are empty, it may return to its representative detail until another generated request arrives.

The nested-slot mixin updates the detail field that GTNL's existing `getPatternDetails` and `getPatternInputs` paths already read. Material identity and reload recovery inspect only GTNL's AE-owned `itemInventory` and `fluidInventory`; manual supplement slots must never select a wildcard material. If a slot is occupied only by manual supplements, or its buffered identity is unresolved, the mixin returns empty processing inputs so GTNL cannot run the representative recipe accidentally. The bridge keeps the original representative detail available for initial display and for slots that have never received an expanded request.

Generated-pattern identity uses `WildcardGeneratedPatternId`, not object identity or the dragged display stack. This keeps dispatch stable across regenerated detail instances.

### Persistence And Recovery

Persist the active generated-pattern ID and, where needed for immediate reconstruction, the generated pattern stack in the slot's existing NBT wrapper. On load:

1. Regenerate the wildcard's current details.
2. Restore the detail whose generated ID matches the saved ID.
3. If the saved ID is unavailable but stored inputs exist, recover only when exactly one generated detail matches all stored inputs.
4. If recovery is ambiguous, leave the slot inactive rather than assigning a wrong recipe.

This mirrors the safety properties of the existing GregTech hatch compatibility and prevents a restart from reinterpreting bronze inputs as the representative aluminium recipe.

Constructor-time regeneration is allowed when the world reference is temporarily `null`; the existing generator accepts that state and can still reconstruct generated IDs from pattern NBT. Deferring solely for a non-null world would discard the saved active ID before the slot becomes fully attached.

### Cache Invalidation

Changing the active generated detail must invalidate any GTNL processing-logic recipe cache associated with that physical slot before the next recipe check. The state machine increments a revision whenever active identity or resolution changes; the slot compares revisions and asks the parent cache bridge to invalidate GTNL processing logics after activation, recovery, clearing, or rollback. A HEAD hook on `isEmpty` performs this synchronization before GTNL consults its recipe cache, covering the transition where AE buffers drain but manual supplements keep the overall slot nonempty. Removing or replacing a wildcard pattern must remove all generated-detail mappings owned by the old slot. Registration must also discard mappings to detached slots so stale AE requests fail cleanly.

## Failure Handling

- Missing GTNL: skip the compatibility mixin entirely.
- Invalid wildcard expansion: register no details for that slot and avoid null map entries.
- Stale AE request: return `false` from dispatch instead of dereferencing a missing slot.
- Unsupported fluid packet: preserve GTNL's current rejection behavior.
- Ambiguous reload recovery: do not guess an active detail.
- Future incompatible GTNL structure: do not cancel GTNL's original methods; log one concise compatibility error and leave ordinary GTNL pattern behavior intact.

## Testing

Automated coverage will isolate the compatibility state and identity decisions from Mixin plumbing where practical:

- two wildcard stacks with identical `ingot* -> plate*` rules but different dragged seeds produce the same advertised generated IDs;
- all generated details map to the single owning physical slot;
- pushing the bronze detail selects bronze inputs, and pushing the aluminium detail selects aluminium inputs;
- replacing a physical pattern removes stale generated mappings;
- active generated ID survives NBT save/load;
- ambiguous stored inputs do not select an arbitrary generated detail;
- manual-only supplement slots do not select a generated material or expose processing inputs;
- state identity and resolution transitions advance the cache revision;
- draining AE buffers invalidates the generated recipe before a manual-only cache lookup;
- dispatch ownership checks use cached generated IDs without regenerating expansions;
- duplicate physical slots advertising the same generated detail fall back to another attached slot;
- structurally similar hatches without `ICraftingProvider` are rejected before cancellation;
- ordinary encoded patterns retain the original single-detail path;
- absence of GTNL does not apply or load the optional mixins and does not affect the existing build.

Verification will compile against the same AE2 `rv3-beta-977-GTNH` and GT5U `5.09.52.594` versions used by GTNL's `dev-290` branch, validate reflected member names against GTNL commit `06cc841398ea4f7d6129fb8584002f0584ea05a8`, run the full Gradle test/build tasks, and perform an in-game smoke test with the GTNL assembly when a runnable pack instance is available.

## Success Criteria

With one GTNL Super Pattern Input Assembly connected to AE, a single `ingot* -> plate*` wildcard pattern must expose and process both bronze plate and aluminium plate recipes regardless of which ingot and plate were originally dragged into the wildcard GUI. The wildcard GUI count and the set of recipes advertised by GTNL must agree after pattern changes and world reloads.
