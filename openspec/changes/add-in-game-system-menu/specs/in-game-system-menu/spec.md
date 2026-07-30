## ADDED Requirements

### Requirement: Open the menu at a frame boundary

The system SHALL accept a system-menu command during play and SHALL enter the menu only after the current WASM-4 frame completes.

#### Scenario: The menu is requested during update

- **WHEN** the user invokes the menu while the worker is executing `update`
- **THEN** the current frame completes, the next `update` does not begin, a
  retained-frame Paused presentation is produced, and the native menu opens

### Requirement: Direct gameplay soft-key entry

The system SHALL open the native system-menu `List` directly from the Nokia
right soft key and SHALL NOT insert a platform command chooser containing only a
`Menu` action.

#### Scenario: The user presses the gameplay menu soft key

- **WHEN** the gameplay Canvas receives the Nokia right soft-key code `-7`
- **THEN** it requests the frame-boundary pause directly and the next system
  screen is the native paused menu

### Requirement: True runtime pause

The system SHALL stop game updates, presentation timing, and APU advancement while a single-player game is in the system menu or a Settings screen opened from that menu.

#### Scenario: A paused screen remains open

- **WHEN** the system menu or one of its Settings screens remains open for several seconds
- **THEN** game and APU state do not advance and no missed frames execute after Continue

### Requirement: Native system-menu presentation

The system SHALL present system-menu actions in a native LCDUI `List` and SHALL
limit custom Canvas drawing to a dimmed Paused presentation over the retained
last game frame and brief status notifications.

#### Scenario: Menu rows exceed the available height

- **WHEN** capability actions make the menu taller than the device display
- **THEN** the native LCDUI `List` provides its platform scrolling and selection
  behavior without custom Canvas hit testing

### Requirement: Base menu structure

The system SHALL order actions as `Continue`, optional `Save State`/`Load State`, `Settings`, `Restart Cart`, and `Exit`, and SHALL keep `Exit` as the final item for every capability combination.

#### Scenario: Save-state capability is connected

- **WHEN** the user opens the menu in a build with `single-save-state`
- **THEN** `Save State` and `Load State` are visible after Continue without slot selection and Exit remains the final action

#### Scenario: Save-state capability is absent

- **WHEN** the user opens the menu without `single-save-state`
- **THEN** the optional actions are omitted and the remaining order is `Continue`, `Settings`, `Restart Cart`, `Exit`

### Requirement: Category-based Settings

The system SHALL open one Settings category list and SHALL present available `Audio`, `Controls`, and `Display & Touch` categories in that order rather than mixing unrelated controls in one form.

#### Scenario: All settings capabilities are connected

- **WHEN** the user opens Settings in a build with audio, control, and display/touch settings
- **THEN** the category list shows `Audio`, `Controls`, and `Display & Touch` in stable order

#### Scenario: A settings capability is unavailable

- **WHEN** a category implementation is not present in the build
- **THEN** its row is omitted rather than opening a placeholder or non-functional screen

### Requirement: Settings preserve their origin

The system SHALL retain an explicit library or game-menu origin while navigating Settings and SHALL keep an active game paused until the user returns to it.

#### Scenario: Back from game-origin Settings

- **WHEN** the user opens Settings from the paused game and presses Back at the category list
- **THEN** the native system-menu list returns and no game update has executed

#### Scenario: Back from library-origin Settings

- **WHEN** the user opens Settings from the library and presses Back at the category list
- **THEN** the library returns with its prior selection preserved

#### Scenario: A category screen closes

- **WHEN** the user saves or cancels Audio, Controls, or Display & Touch
- **THEN** the Settings category list returns rather than skipping directly to the game or library

### Requirement: System input isolation

The system SHALL let LCDUI own input while the native menu is current and SHALL
clear game button latches when opening and closing it.

#### Scenario: Continue is confirmed with Fire

- **WHEN** the user presses Fire to select `Continue`
- **THEN** the menu closes and that Fire press is absent from the first resumed game frame

### Requirement: Safe transitions

The system SHALL perform `Restart Cart` and `Exit` through worker-owned teardown and SHALL close APU and storage handles exactly once.

#### Scenario: Exit returns to the library

- **WHEN** the user confirms the final `Exit` action
- **THEN** the active runtime stops cleanly and the library opens without a background worker thread

### Requirement: Action feedback

The system SHALL show a brief message after a runtime action and SHALL show an error rather than false success when it fails.

#### Scenario: Restart cannot load the cartridge

- **WHEN** cartridge reinitialization fails
- **THEN** the user sees the reason through the existing error flow and can return to the library
