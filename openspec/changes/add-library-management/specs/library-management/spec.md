## ADDED Requirements

### Requirement: Manage the selected cartridge

The system SHALL open management UI for one explicitly selected cartridge and SHALL show its title and source kind before any action.

#### Scenario: A bundled cartridge is selected

- **WHEN** the user opens management for a bundled cartridge
- **THEN** disk and cache actions are available according to existing data and cartridge-binary deletion is absent or clearly unavailable

### Requirement: Delete an installed cartridge

The system SHALL delete only the transactional records of the selected installed cartridge and SHALL recover from an interrupted operation without exposing a corrupted library entry.

#### Scenario: Deletion is confirmed

- **WHEN** the user confirms `Delete installed cartridge`
- **THEN** the selected binary disappears from the list and other cartridge, disk, and W4IR stores remain unchanged

### Requirement: Clear disk independently

The system SHALL clear only the selected cartridge's WASM-4 disk data and SHALL NOT delete its binary or W4IR cache.

#### Scenario: Clear game disk is canceled

- **WHEN** the user rejects the confirmation
- **THEN** disk bytes and the library list remain unchanged

### Requirement: Clear W4IR cache independently

The system SHALL clear only the selected cartridge's derived W4IR cache and SHALL allow it to be rebuilt safely on the next launch.

#### Scenario: Cache is cleared

- **WHEN** the user confirms `Clear W4IR cache`
- **THEN** the cache is removed, the binary and disk remain, and the next launch translates the cartridge again

### Requirement: Exact scope confirmation

The system MUST show the cartridge name and the kind of data being removed before every destructive action.

#### Scenario: Disk-clear confirmation

- **WHEN** the confirmation dialog is open
- **THEN** its text cannot be interpreted as deleting the entire library or cartridge binary

### Requirement: Honest RMS usage display

The system SHALL show reliable per-cartridge bytes and available RMS when the implementation provides them; otherwise it SHALL show `Unknown`.

#### Scenario: getSizeAvailable is unsupported

- **WHEN** the MIDP implementation does not return a usable free-space value
- **THEN** the UI does not show a calculated or negative number as free space

### Requirement: Failure is not disguised as success

The system SHALL keep the selected entry available and SHALL show the data kind and reason after a failed inspection or deletion.

#### Scenario: RecordStoreException during cache clearing

- **WHEN** the backend cannot delete the W4IR store
- **THEN** the UI reports `W4IR cache was not cleared` and does not claim that bytes were freed
