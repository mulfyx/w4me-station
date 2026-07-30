## ADDED Requirements

### Requirement: Global mute

The system SHALL provide a global `Sound: On/Off` setting and SHALL stop audible output immediately when sound is turned off.

#### Scenario: Muting during an active tone

- **WHEN** the user turns sound off while the backend is playing a tone
- **THEN** output becomes silent using the best supported mechanism and new tones remain inaudible until sound is turned on

### Requirement: Master volume without altering tone semantics

The system SHALL scale only sustain and peak volume and MUST preserve the original frequency, duration, channel, waveform, and note-mode flags.

#### Scenario: Gain is 100

- **WHEN** master volume is set to 100
- **THEN** the backend receives the original WASM-4 tone parameters unchanged

### Requirement: Capability-aware UI

The system SHALL show only controls supported by the active audio backend and SHALL clearly identify a silent backend.

#### Scenario: The backend supports mute only

- **WHEN** the user opens Audio Settings
- **THEN** the On/Off toggle is available and unsupported precise volume controls are not presented as functional

### Requirement: Audio mode belongs to Audio Settings

The system SHALL provide the `Automatic` and `Compatible` audio mode selector in Audio Settings rather than as a separate library command.

#### Scenario: Selecting Compatible mode

- **WHEN** the user selects `Compatible` and saves Audio Settings
- **THEN** the selection is persisted and is used the next time a cartridge audio backend is created

### Requirement: Volume control reflects the stored gain

The volume control SHALL open at the currently stored gain, including the default value of 100.

#### Scenario: Opening settings at the default gain

- **WHEN** the user opens Audio Settings before changing the volume
- **THEN** the interactive volume control and its text both show 100 percent

### Requirement: Setting persistence

The system SHALL persist confirmed mute and gain values in versioned RMS and SHALL restore them before the first cartridge tone on the next launch.

#### Scenario: The MIDlet is restarted while muted

- **WHEN** the user turns sound off, closes the MIDlet, and starts it again
- **THEN** the cartridge produces no audible tone until sound is explicitly turned on

### Requirement: Separate pause and mute behavior

The system SHALL temporarily suspend audio for the system menu without changing the persisted user gain.

#### Scenario: The menu is opened at gain 50

- **WHEN** the user opens and then closes the system menu
- **THEN** audio returns to gain 50 rather than the default of 100

### Requirement: Persistence failure does not break the runtime

The system SHALL continue the game with the selected session gain and SHALL report an error if the RMS update fails.

#### Scenario: RMS is unavailable

- **WHEN** the user changes volume while the settings store is unavailable
- **THEN** the change remains active until the MIDlet closes and cartridge execution continues
