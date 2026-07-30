## Context

`W4Canvas` currently selects a `min(width,height)` square, centers it across the full Canvas, and then draws touch controls over the bottom 40–64 pixels. Pointer mapping uses the same full square. The runtime already stores `SYSTEM_FLAGS`, but the overlay does not honor `SYSTEM_HIDE_GAMEPAD_OVERLAY` (bit `0x02`).

The frame loop already records frame timestamps, decides whether a frame will be presented, and adapts `presentationDivisor`. It does not expose this information to the user. Counting update calls or displaying a fixed 60 would be misleading because the runtime may continue updating while presenting only every second, third, or fourth frame.

## Goals / Non-Goals

**Goals:** honor the cartridge flag; provide a user-controlled visibility policy; support a layout that does not cover the game; use one geometry model for rendering, pointer mapping, and touch hit testing; provide a truthful, low-overhead counter of frames actually presented to the display.

**Non-Goals:** framebuffer filtering, non-uniform aspect-ratio stretching, touch-control themes, arbitrary placement of individual buttons, a benchmark-quality profiler, a WASM update-rate counter, or changes to frame pacing and adaptive presentation.

## Decisions

### 1. Use one immutable `ViewportLayout`

When the Canvas size, settings, or system flag changes, calculate `gameLeft`, `gameTop`, `gameSide`, `controlsTop/Height`, and effective visibility. Rendering, pointer mapping, and touch hit testing use only this object, eliminating divergent formulas.

### 2. Visibility precedence

`Hidden` always hides controls. Cartridge `SYSTEM_HIDE_GAMEPAD_OVERLAY` also hides them in every user mode. `Visible` forces controls to appear on a pointer-capable device when the cartridge flag is clear; `Auto` additionally allows device heuristics to hide them when touch is unavailable or space is insufficient.

### 3. Two layouts

`Overlay` preserves the largest square and draws controls over its bottom area. `Game Above Controls` reserves control height first and then fits the square into the remaining area above the controls. A pointer outside the game returns sentinel coordinates and does not become a game mouse click.

If the separate area would reduce the game side below the verified minimum, the effective layout temporarily becomes Overlay and shows one brief message.

### 4. Scaling remains nearest-neighbor

Existing x/y maps are built for `gameSide`. Black bars are cleared using the full geometry, and overlays and menus are always drawn after the game image.

### 5. FPS measures completed presentations

The FPS counter counts only frames for which game presentation is completed. Skipped presentations do not increment the counter even though the WASM `update()` still runs.

The frame loop reuses its existing clock samples. Once at least 1,000 milliseconds have elapsed, it computes:

```text
fps = presentedFrames * 1000 / elapsedMillis
```

and begins a new measurement window. Until the first complete window, the overlay displays `FPS --`. Entering a paused menu or Settings invalidates the current window. Measurement restarts on resume so time spent paused cannot produce a false low value. A non-positive elapsed value, including a backward clock adjustment, also resets the window.

### 6. FPS is a cached system overlay

The formatted label is rebuilt only when a measurement window closes, not on every frame. Presentation draws the cached label after the game image and touch controls, relative to the actual game rectangle, with a small contrasting background.

The overlay never writes into linear memory, the WASM-4 framebuffer, or the palette. It is hidden while the paused system menu or a Settings screen is visible. With the setting disabled, the frame loop performs no extra clock read and allocates no FPS objects.

### 7. FPS is a global Display & Touch preference

`Show FPS` is an `On/Off` control in the `Display & Touch` category introduced by the in-game Settings redesign. It defaults to Off, is stored with the versioned display/touch preferences, and takes effect immediately when the user returns to the game.

## Risks / Trade-offs

- [The flag changes every frame] → recalculate the layout only when effective visibility changes and do not allocate arrays every frame.
- [A pointer gesture starts before the layout changes] → pin the layout at gesture start and clear touch buttons when it changes.
- [Small portrait screen] → use a deterministic fallback that keeps the game usable.
- [The platform timer is coarse or irregular] → use an elapsed window of at least one second and reset safely on non-positive elapsed time.
- [The counter itself slows presentation] → reuse existing timestamps, cache the label, avoid steady-state allocation, and require native phoneME A/B measurements.
- [The label covers important game pixels] → keep it compact, anchor it to the game rectangle, provide a contrasting backing, and leave it disabled by default.

## Migration Plan

First move the current geometry into `ViewportLayout` without visual changes, then add the system flag and separate layout. Add the versioned settings model and `Display & Touch` category, then add presentation accounting and the cached FPS overlay. The default `Auto + Overlay + Show FPS Off` preserves the current appearance except for the mandatory hide flag.

## Open Questions

- The minimum `gameSide` will be selected after screenshot verification at target resolutions; the specification requires only a deterministic fallback.
