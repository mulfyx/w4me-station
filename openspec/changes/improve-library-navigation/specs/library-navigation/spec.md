## ADDED Requirements

### Requirement: Native LCDUI library

The cartridge library SHALL use an implicit LCDUI `List` for item selection and SHALL NOT implement its own list painting, scrolling, key mapping, or pointer hit testing.

#### Scenario: Selecting a cartridge

- **WHEN** the user navigates with the phone keys or touchscreen and activates a list item
- **THEN** the native select command launches the corresponding bundled or installed cartridge

### Requirement: Library views

The system SHALL provide `All`, `Favorites`, and `Recent` and SHALL clearly show the active view.

#### Scenario: Favorites is empty

- **WHEN** the user opens Favorites with no marked available cartridges
- **THEN** the UI shows an empty state and a way back to All rather than an invalid `1/0` counter

### Requirement: Favorites management

The system SHALL allow the selected cartridge to be added to or removed from Favorites without launching it and SHALL persist the flag by stable identity.

#### Scenario: The same cartridge is reinstalled

- **WHEN** a favorite cartridge is removed and identical bytes are later installed
- **THEN** the entry appears in Favorites again without depending on the old recordId

### Requirement: Bounded launch history

The system SHALL add a successfully launched cartridge to the front of Recent and SHALL store a bounded number of unique entries.

#### Scenario: A cartridge is launched again

- **WHEN** the user launches an existing recent cartridge again
- **THEN** it moves to first place without a duplicate

### Requirement: Local search

The system SHALL filter available cartridge titles case-insensitively and SHALL show the query and an option to clear it.

#### Scenario: No matches

- **WHEN** the query matches no available title
- **THEN** the UI shows `No cartridges found`, retains Back/Clear controls, and does not launch a hidden entry

### Requirement: Position restoration

The system SHALL restore the view, query, selected identity, and scroll position when returning from a game or child screen if the entry remains available.

#### Scenario: Returning from a game

- **WHEN** the user exits to Library
- **THEN** the previously launched cartridge is selected and visible again without scrolling from the beginning

### Requirement: Safe metadata fallback

The system SHALL open the All view when the metadata store is missing or corrupted and SHALL NOT modify cartridge or disk data.

#### Scenario: The Recent record is corrupted

- **WHEN** the metadata checksum fails validation
- **THEN** the library remains available, Favorites/Recent are reset locally, and no games are deleted
