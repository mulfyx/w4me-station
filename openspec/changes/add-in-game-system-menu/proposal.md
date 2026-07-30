## Why

During play, the current commands expose an immediate `Library` exit and a separate sound form instead of one coherent paused experience. A unified game menu and category-based Settings hub are needed so users can pause safely, find related controls, and leave a cartridge without ambiguous navigation.

## What Changes

- Add an in-game system menu with a true pause at a frame boundary.
- Open it directly from the gameplay soft key without an intermediate platform
  command chooser containing a single `Menu` action.
- Use a native LCDUI system-menu `List`; keep custom Canvas drawing limited to a
  retained-frame `Paused` overlay and brief notifications.
- Define the stable action order as `Continue`, optional `Save State`/`Load State`, `Settings`, `Restart Cart`, and `Exit`.
- Replace the in-game `Library` action with `Exit`, make it the final item in every capability combination, and keep its behavior as a clean return to the cartridge library.
- Replace separate top-level settings commands with one Settings hub containing ordered `Audio`, `Controls`, and `Display & Touch` categories as their capabilities become available.
- Preserve the caller: Back from Settings returns to the paused game menu when opened during play and to the library when opened there.
- Do not pass keys used for menu interaction to the cartridge.
- Show brief success and failure messages after the menu closes.
- Make the menu and Settings hub extensible entry points for separate save-state, audio, controls, display/touch, disk-options, and netplay change packages.

## Capabilities

### New Capabilities

- `in-game-system-menu`: runtime pause, redesigned system-menu navigation, category-based Settings routing, and safe Continue/Restart/Exit transitions.

### Modified Capabilities

- None.

## Impact

This affects the `W4Canvas` frame loop and lifecycle, `W4MeMidlet` transitions,
native LCDUI menu and settings screens, `LibraryList` commands, rendering over
the last frame, and `Wasm4Apu` control. The implementation must remain compatible
with Java 1.3, CLDC 1.1, and MIDP 2.0.
