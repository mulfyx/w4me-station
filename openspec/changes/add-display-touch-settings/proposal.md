## Why

The current touch gamepad is always drawn over the lower part of the square frame and does not honor `SYSTEM_HIDE_GAMEPAD_OVERLAY`. The game needs a predictable image that can remain unobstructed and user control over on-screen buttons. There is also no in-game indication of the presentation rate, so users cannot tell whether adaptive frame presentation has reduced visible FPS.

## What Changes

- Honor the cartridge's `SYSTEM_HIDE_GAMEPAD_OVERLAY` flag.
- Add `Auto`, `Visible`, and `Hidden` touch-gamepad modes.
- Add `Overlay` and `Game Above Controls` layouts, with the latter reserving a separate area that does not cover the 160x160 image.
- Calculate pointer coordinates strictly relative to the actual game rectangle.
- Add a global `Show FPS: On/Off` setting under `Display & Touch`, defaulting to Off.
- Report the rate of frames actually presented to the display rather than the WASM update rate or the nominal 60 FPS target.
- Render the FPS value as a cached system overlay without changing WASM memory, framebuffer, or palette and without per-frame allocation.
- Persist display/touch settings globally and adapt safely to small screens and orientation changes.

## Capabilities

### New Capabilities

- `display-touch-settings`: touch-gamepad visibility, a non-overlapping layout, correct scaling and pointer mapping, and an optional actual-presentation FPS overlay.

### Modified Capabilities

- None.

## Impact

This affects `W4Canvas` rendering, presentation accounting, hit testing, and pointer mapping, `SYSTEM_FLAGS` reads, the settings screen, and settings RMS. The pixel framebuffer and its palette remain unchanged.
