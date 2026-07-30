## 1. Runtime state machine

- [x] 1.1 Move the active runtime/module/interpreter lifecycle into a worker-owned session object
- [x] 1.2 Add frame-boundary RUNNING/MENU/RESTART/LEAVE states with lifecycle-stop priority
- [x] 1.3 Generalize the current settings pause so the worker and APU remain suspended across the menu, Settings hub, and category screens
- [x] 1.4 Add an APU suspend/resume/silence contract and clear input latches on menu entry and final resume

## 2. Menu UI

- [x] 2.1 Implement a native LCDUI system-menu `List` and keep the custom Canvas
      UI limited to a dimmed retained-frame `Paused` overlay and notifications
- [x] 2.2 Connect Continue, Settings, Restart Cart, and Exit with Exit always rendered as the final item
- [x] 2.3 Add capability-controlled Save State/Load State items without changing the stable base ordering
- [x] 2.4 Replace the direct in-game `Library` command with the system-menu entry point and preserve safe worker-owned teardown through Exit
- [x] 2.5 Add brief success/failure notifications without resuming a hidden frame
- [x] 2.6 Remove the redundant Canvas command chooser and open the native menu
      directly from the Nokia right soft key

## 3. Settings navigation

- [x] 3.1 Add an origin-aware native Settings category list with stable Audio, Controls, and Display & Touch ordering
- [x] 3.2 Route the implemented Audio form through the Settings hub and replace top-level `Sound settings` commands with `Settings`
- [x] 3.3 Add category registration hooks for Controls and Display & Touch without moving their capability-owned persistence into the menu package
- [x] 3.4 Make category Save/Cancel return to Settings and make Settings Back return to the paused menu or library according to its explicit origin

## 4. Verification

- [x] 4.1 Add host tests for state transitions, stable action/category ordering, Exit-last behavior, input isolation, and the absence of catch-up frames
- [x] 4.2 Add tests that traverse game menu → Settings → Audio → Settings → game menu without resuming the worker or leaking input
- [x] 4.3 Add KEmulator native-List structure and command flows for Continue,
      Restart, Exit, library-origin Settings, and game-origin Settings
- [x] 4.4 Verify worker/APU/storage cleanup exactly once for lifecycle stop, Restart, and Exit
- [x] 4.5 Run `just test`, `just build`, cartridge oracle gates, and a frame-loop performance A/B
- [x] 4.6 Review the final diff and commit only the verified feature as a separate commit when the implementation batch has been authorized
- [x] 4.7 Verify that one gameplay soft-key press opens the native paused menu
      without an intermediate one-item command screen
