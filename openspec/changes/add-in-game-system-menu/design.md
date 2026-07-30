## Context

`W4Canvas` currently runs the runtime inside a worker thread and keeps `Wasm4Runtime`, `WasmModule`, and `WasmInterpreter` as local variables in `run()`. The `Library` soft command immediately calls `W4MeMidlet.showLibrary()`, stops the canvas, and destroys the active session. `Sound settings` is exposed as a second top-level command and pauses the worker through a special-purpose `settingsOpen` flag while a native Form is current.

The system menu must retain the runtime and pause between two `update` calls
rather than during interpretation. Its actions use a native LCDUI `List` for
platform scrolling, focus, keyboard, and touch behavior. The Canvas may draw a
simple `Paused` overlay over the retained last game frame while the display
transition is pending or when the Canvas is exposed, but it does not implement a
custom menu widget. Settings use a second native LCDUI category list and
category Forms while the same worker-owned pause remains active.

## Goals / Non-Goals

**Goals:**

- one entry point for runtime actions;
- frame-boundary pause without losing the active cartridge;
- a native paused-game action list with stable ordering;
- `Exit` as the unambiguous final action that returns to the library;
- one Settings hub with Audio, Controls, and Display & Touch categories;
- origin-aware Back navigation between a category, Settings, the game menu, and the library;
- no menu input leaking into the game;
- safe Continue, Restart Cart, and Exit actions;
- extensibility through actions supplied by independent change packages.

**Non-Goals:**

- implementation of save states, category-specific settings, disk options, or netplay themselves;
- rendering the FPS counter, which belongs to `display-touch-settings`;
- pausing external phone time or updating the game in the background;
- styling for a specific phone model.

## Decisions

### 1. The menu is a worker-loop state

`RUNNING`, `MENU_REQUESTED`, `MENU_OPEN`, `RESTART_REQUESTED`, and `LEAVE_REQUESTED` belong to `W4Canvas`. The UI thread only sets a request; the worker accepts it after the current frame completes. This prevents snapshots or teardown while WASM is executing.

Stopping the thread and opening a new MIDP `List` is visually simpler, but it loses runtime context and creates a race with the APU and interpreter.

### 2. Use LCDUI for the menu and keep Canvas drawing presentation-only

The worker does not call `update` and performs no catch-up after Continue. After
the frame-boundary pause is accepted, it redraws the last presentation with a
simple dimmed `Paused` overlay and schedules a UI-thread transition to a native
LCDUI `List`. The action rows, selection highlight, scrolling, keyboard
navigation, and touch selection are owned by LCDUI rather than custom Canvas
code.

The `List` has an explicit Select command and a Back command that performs
Continue. Because no custom row geometry or pointer hit testing is used, the
design follows each device's native font, focus, and touch behavior.

The gameplay Canvas does not register a single `Menu` LCDUI `Command`: some
MIDP implementations expose such a command through a separate platform command
chooser, adding a redundant screen. On the Nokia target, the right soft-key code
`-7` requests the menu directly from `keyPressed()`. This binding only opens the
native `List`; it does not implement menu navigation on the Canvas.

### 3. Use a stable action order and reserve the final row for Exit

The base order is:

1. `Continue`
2. capability-provided `Save State`
3. capability-provided `Load State`
4. `Settings`
5. `Restart Cart`
6. `Exit`

Unavailable optional actions are omitted without changing the relative order of the remaining rows. `Exit` is always the final item. It replaces the label `Library` but retains the safe behavior of stopping the active session and returning to the library. It does not terminate the MIDlet.

### 4. Use a native category list for Settings

`Settings` opens one LCDUI `List` rather than a large mixed Form. Categories register under stable identifiers and appear in this order when their capabilities are available:

1. `Audio`
2. `Controls`
3. `Display & Touch`

Selecting a category opens its capability-owned screen. Saving or canceling that
screen returns to the Settings list. Back from the Settings list returns to the
native system-menu `List` when it was opened during play and to the library when
it was opened from the library. The source is explicit state, not inferred from
the current `Displayable`.

The existing `Sound settings` library and game commands become one `Settings` entry. Category packages retain ownership of their persistence and validation; this change owns only discovery and navigation.

### 5. Keep the worker-owned session paused across child screens

The game stays paused from confirmed menu entry until Continue, Restart, or Exit, including while the Settings list or a category Form is current. General pause state replaces the audio-specific `settingsOpen` handshake. Returning from a category must not briefly resume a game frame behind the settings UI.

### 6. Separate game and system input channels

While the menu is open, physical and touch events update only menu state. While a native Settings screen is current, the Canvas does not consume those events. All latch and pressed bits are cleared on entry and final resume so navigation or confirmation keys do not enter the next game frame.

### 7. Pause includes audio

The APU receives `suspendOutput()` after confirmed menu entry and `resumeOutput()` on Continue. Internal frame counters do not advance while paused. The backend must stop active audible sound or become silent using the best available mechanism.

Opening and closing category Forms does not repeatedly suspend or resume audio. Persistent mute and gain remain independent from temporary system-menu suspension.

### 8. Register items as stable actions

The base model contains Continue, Settings, Restart Cart, and Exit. Save/Load and future actions appear only when their capability is available, while retaining stable labels and ordering. Unavailable actions are omitted; failures from available actions produce a status notification.

Restart performs teardown and reinitialization on the worker thread. Exit performs teardown and then asks the MIDlet to switch `Displayable` on the UI thread.

## Risks / Trade-offs

- [The backend cannot immediately stop an already submitted tone] → extend the audio contract with a silence operation and test every backend explicitly.
- [The menu does not fit on a small screen] → rely on native LCDUI `List`
  scrolling instead of maintaining custom geometry.
- [A platform puts a lone Canvas command behind an Options screen] → do not
  register that command; consume the target soft-key event directly and open the
  native menu after the frame-boundary pause.
- [A category capability has not landed yet] → omit that category instead of exposing a dead row; stable identifiers allow it to appear later without changing navigation code.
- [A native Settings screen accidentally resumes the worker] → represent pause ownership independently from the current `Displayable` and test every return path.
- [MIDP lifecycle races with a menu request] → lifecycle stop takes priority and closes the APU/runtime idempotently.
- [Restart fails] → show the existing cartridge failure flow and return the user to the library.

## Migration Plan

1. Generalize the current settings pause into a worker-owned system-menu/session pause state.
2. Add the native system-menu `List` and the presentation-only paused overlay,
   replacing the direct in-game Library transition.
3. Add Restart, capability actions, and notifications.
4. Introduce the Settings category list, route the existing Audio form through it, and replace top-level `Sound settings` commands with `Settings`.
5. Connect Controls and Display & Touch as their capability packages are implemented.

Rollback removes the menu state machine and Settings hub and restores the existing `Library` and `Sound settings` commands; category-owned settings data requires no migration.

## Open Questions

- Additional vendor key codes may be selected through a real-device matrix when
  control settings are implemented; the Nokia right soft key `-7` is the
  required baseline binding.
