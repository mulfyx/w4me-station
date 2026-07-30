## ADDED Requirements

### Requirement: The cartridge controls overlay hiding

The system MUST hide the touch gamepad when the active cartridge sets `SYSTEM_HIDE_GAMEPAD_OVERLAY`.

#### Scenario: The flag is set during play

- **WHEN** a completed frame changes the system flag from visible to hidden
- **THEN** the next presentation does not draw controls and their area does not generate gamepad buttons

### Requirement: User visibility policy

The system SHALL provide `Auto`, `Visible`, and `Hidden` modes, with the cartridge hide flag taking precedence.

#### Scenario: Hidden mode

- **WHEN** the user selects Hidden and returns to the game
- **THEN** the touch gamepad is not displayed regardless of screen heuristics

### Requirement: Non-overlapping game layout

The system SHALL provide `Game Above Controls`, in which the game and control rectangles do not overlap.

#### Scenario: A portrait screen has enough space

- **WHEN** `Game Above Controls` is selected and the touch gamepad is visible
- **THEN** the square image is positioned entirely above the control area without covering framebuffer pixels

### Requirement: Unified pointer geometry

The system SHALL calculate WASM-4 mouse coordinates relative to the actual rendered game rectangle and SHALL NOT treat the touch-control area as a game click.

#### Scenario: Pressing Button 1 below the game

- **WHEN** the user touches Button 1 in the separate layout
- **THEN** the gamepad receives Button 1 and the mouse button remains unpressed

### Requirement: Deterministic small-screen fallback

The system SHALL keep the game usable and SHALL explicitly apply a safe fallback when the separate layout does not fit.

#### Scenario: Height is insufficient

- **WHEN** `Game Above Controls` would reduce the game rectangle below the supported minimum
- **THEN** the system uses Overlay or hides controls according to a documented rule and shows one brief message

### Requirement: Display/touch settings persistence

The system SHALL store the selected visibility, layout, and FPS-overlay settings in versioned RMS and SHALL apply defaults when the record is corrupted.

#### Scenario: The MIDlet restarts with saved settings

- **WHEN** the user selected the separate layout and starts the MIDlet again
- **THEN** the setting is applied before the first rendered game frame without an intermediate overlap

### Requirement: FPS counter setting

The system SHALL provide a global `Show FPS` On/Off setting in the `Display & Touch` category, SHALL default it to Off, and SHALL apply a saved value before the first game presentation.

#### Scenario: First launch uses the unobtrusive default

- **WHEN** no valid display/touch settings record exists
- **THEN** `Show FPS` is Off and no FPS overlay is drawn

#### Scenario: The user enables the counter

- **WHEN** the user selects `Show FPS: On`, leaves Settings, and later restarts the MIDlet
- **THEN** the FPS overlay is enabled both after leaving Settings and before the first game presentation after restart

### Requirement: FPS reflects actual presentation rate

The system MUST calculate FPS from completed game presentations over an elapsed window of at least one second and MUST exclude skipped presentations and paused time.

#### Scenario: Adaptive presentation skips every other frame

- **WHEN** WASM updates continue near 60 updates per second but only every second frame is presented
- **THEN** the displayed FPS reflects approximately 30 completed presentations per second rather than the update rate

#### Scenario: The game is paused

- **WHEN** the paused system menu or a Settings screen remains open and the user then resumes play
- **THEN** paused time is excluded and the counter waits for a new complete measurement window

### Requirement: FPS overlay is runtime-only

The system SHALL draw the FPS label as a system overlay, SHALL position it relative to the actual game rectangle, and SHALL NOT modify WASM linear memory, framebuffer, or palette state.

#### Scenario: Comparing the same game route with FPS off and on

- **WHEN** an identical input trace is run with the FPS overlay disabled and enabled
- **THEN** the WASM memory, framebuffer, palette, logical instruction count, and game completion state are identical

#### Scenario: A system screen is open

- **WHEN** the paused game menu or any Settings screen is visible
- **THEN** the FPS label is hidden

### Requirement: FPS accounting has bounded overhead

The system MUST reuse frame-loop clock samples that are already required for pacing, MUST NOT allocate an object for each presented frame, and SHALL update the formatted label no more than once per completed measurement window.

#### Scenario: FPS is enabled during steady-state play

- **WHEN** multiple frames are presented within one measurement window
- **THEN** the same cached label is reused until the window closes

#### Scenario: FPS is disabled

- **WHEN** `Show FPS` is Off during steady-state play
- **THEN** FPS accounting performs no additional clock read and creates no FPS label
