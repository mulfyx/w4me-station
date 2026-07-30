## 1. Layout model

- [ ] 1.1 Move render/pointer/touch geometry into one immutable `ViewportLayout`
- [ ] 1.2 Add `SYSTEM_HIDE_GAMEPAD_OVERLAY` handling and effective-visibility precedence
- [ ] 1.3 Implement Overlay/Game Above Controls and a deterministic small-screen fallback

## 2. Settings and input

- [ ] 2.1 Add Auto/Visible/Hidden, layout, and `Show FPS` controls to the `Display & Touch` Settings category
- [ ] 2.2 Persist the visibility, layout, and FPS settings and apply them before the first game presentation
- [ ] 2.3 Clear gestures and latches when the effective layout changes and handle pointers outside the game correctly

## 3. FPS overlay

- [ ] 3.1 Count completed presentations over an elapsed window using the frame loop's existing timestamps and exclude skipped and paused frames
- [ ] 3.2 Reset the measurement safely on pause, resume, and non-positive elapsed time
- [ ] 3.3 Cache the formatted FPS label and render it relative to the game rectangle without modifying WASM memory, framebuffer, or palette
- [ ] 3.4 Hide the overlay on system screens and keep the disabled path free of additional clock reads and per-frame allocation

## 4. Verification

- [ ] 4.1 Add geometry tests for portrait, landscape, and small screens and for system-flag transitions
- [ ] 4.2 Add FPS accounting tests for full-rate, skipped-presentation, pause/resume, incomplete-window, and backward-clock cases
- [ ] 4.3 Add a differential route gate proving that FPS Off and On produce identical WASM memory, framebuffer, palette, instruction count, and completion state
- [ ] 4.4 Extend KEmulator screenshot/touch gates for overlay, separate layout, a hidden gamepad, and a legible FPS label at supported resolutions
- [ ] 4.5 Run balanced native phoneME A/B checks with FPS disabled and enabled, confirming that the disabled path does not regress and reporting the enabled-overlay cost separately
- [ ] 4.6 Run `just test`, `just build`, `tools/kemu/run.sh verify touch`, and framebuffer oracle gates
- [ ] 4.7 Review the final diff and commit only the verified feature as a separate commit when the implementation batch has been authorized
