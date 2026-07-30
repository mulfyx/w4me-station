## 0. LCDUI foundation

- [x] 0.1 Replace the hand-drawn library Canvas with an implicit LCDUI List while preserving bundled and installed launch mapping
- [x] 0.2 Verify native selection, focus preservation, commands, and representative cartridge launches in KEmulator
    - KEmulator's LCDUI model verifies the List, commands, selected index, Run, return, and Sound settings flow. Its raw key injector cannot move a high-level List, so cartridge oracle scenarios use test-only direct-launch MIDlets; native rendering and keypad/touch navigation still require a real-device smoke test.

## 1. Metadata and view model

- [ ] 1.1 Add stable composite identity for bundled and installed entries
- [ ] 1.2 Implement a bounded and checksummed Favorites/Recent metadata store
- [ ] 1.3 Build a snapshot view model with All/Favorites/Recent/search and empty-state handling

## 2. Library UX

- [ ] 2.1 Add a view switcher, favorite toggle, and MRU update after successful launch
- [ ] 2.2 Add TextBox search, clear, and case-insensitive local filtering
- [ ] 2.3 Restore selected identity, first visible row, view, and query after child screens

## 3. Verification

- [ ] 3.1 Add tests for identity reinstallation, MRU uniqueness and limits, metadata corruption, and empty filters
- [ ] 3.2 Add a KEmulator navigation flow for favorites, recent, search, and focus restoration
- [ ] 3.3 Run `just test`, `just build`, and `tools/kemu/run.sh verify library`
- [ ] 3.4 Review the final diff and commit only the verified feature as a separate commit when the implementation batch has been authorized
