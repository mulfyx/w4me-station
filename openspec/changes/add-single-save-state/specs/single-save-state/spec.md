## ADDED Requirements

### Requirement: One snapshot for the current session

The system SHALL store no more than one user save state for the active cartridge session and SHALL atomically replace it on the next `Save State`.

#### Scenario: Repeated save

- **WHEN** the user saves state a second time in the same session
- **THEN** a subsequent Load restores the second state and no slot selection appears

### Requirement: Save and Load are in the system menu

The system SHALL provide separate `Save State` and `Load State` actions in the main system menu without a slot submenu.

#### Scenario: The user opens the menu

- **WHEN** a single-player game is running and the system menu is open
- **THEN** both actions are visible in one list and require no name, number, or thumbnail

### Requirement: Create snapshots at a frame boundary

The system SHALL perform capture and restore only between cartridge lifecycle calls and SHALL NOT save an active interpreter call stack.

#### Scenario: Save is selected after a frame

- **WHEN** the user confirms `Save State`
- **THEN** the system captures the completed frame state before the next `update` begins

### Requirement: Complete restoration

The system SHALL restore linear memory, globals, and every other mutable VM/runtime state required for equivalent continuation, including logical disk and supported APU state.

#### Scenario: The game changes after Save

- **WHEN** the game changes memory, globals, disk, and active tone envelopes after Save and the user performs Load
- **THEN** the next frame observes the saved values and does not receive the Load confirmation press

### Requirement: Load before the first Save

The system SHALL leave the game unchanged and SHALL show `Need to save a state first` when the current session has no snapshot.

#### Scenario: New session

- **WHEN** the user selects `Load State` before the first successful Save
- **THEN** the menu closes or remains interactive, the game remains unchanged, and the missing-state message is displayed

### Requirement: Snapshot lifetime

The system SHALL remove the snapshot on Restart Cart, exit to Library, cartridge failure, MIDlet close, or MIDlet destruction.

#### Scenario: Cartridge restart

- **WHEN** the user saves state and then performs `Restart Cart`
- **THEN** the new session cannot load the previous session's snapshot

### Requirement: Failure without game corruption

The system SHALL keep the current game functional and SHALL report an error when Save or Load cannot complete fully.

#### Scenario: Insufficient heap for Save

- **WHEN** the snapshot cannot be allocated without `OutOfMemoryError`
- **THEN** the runtime continues, incomplete state does not become available to Load, and `State save failed` is shown
