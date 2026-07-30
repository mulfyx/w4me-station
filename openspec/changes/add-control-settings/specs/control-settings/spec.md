## ADDED Requirements

### Requirement: View the layout

The system SHALL show the current bindings for Up, Down, Left, Right, Button 1, Button 2, and System Menu using a meaningful key name or numeric code.

#### Scenario: A key code has no name

- **WHEN** the MIDP implementation does not return a useful key name
- **THEN** the screen shows a stable numeric representation and the binding remains editable

### Requirement: Capture a new key

The system SHALL remap an action only after entering an explicit capture mode and SHALL allow capture to be canceled without changing the layout.

#### Scenario: Capture is canceled

- **WHEN** the user opens `Press a key` and presses Back
- **THEN** the previous binding is preserved and the settings screen becomes interactive again

### Requirement: Explicit conflict resolution

The system SHALL detect when the selected key is already bound to another action and SHALL require confirmation to move it or cancel.

#### Scenario: Button 1 receives the Button 2 key

- **WHEN** the user selects an already assigned key
- **THEN** the system does not create a hidden duplicate binding and identifies the affected action

### Requirement: System-menu accessibility

The system MUST always preserve at least one functional way to open the System Menu and SHALL provide `Reset to defaults` through a MIDP command.

#### Scenario: The user tries to remove the last menu binding

- **WHEN** an operation would leave System Menu without a key
- **THEN** the system rejects it and explains the restriction

### Requirement: Persistence and application

The system SHALL persist the confirmed layout in versioned RMS and SHALL apply it no later than the next game frame.

#### Scenario: The layout is changed while paused

- **WHEN** the user saves a new key and returns to the game
- **THEN** the next frame uses the new layout and old pressed latches are cleared

### Requirement: Safe fallback

The system SHALL use functional defaults when the settings record is missing, corrupted, or unsupported.

#### Scenario: The settings RMS checksum is invalid

- **WHEN** the MIDlet loads a corrupted layout
- **THEN** the library and game start with defaults and cartridge/disk stores remain unchanged
