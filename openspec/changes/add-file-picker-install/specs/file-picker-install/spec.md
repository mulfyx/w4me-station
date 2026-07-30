## ADDED Requirements

### Requirement: Optional availability

The system SHALL show `Choose .wasm file` only when the JSR-75 file API is available and SHALL retain a functional manual URL/file flow in every build.

#### Scenario: Device without JSR-75

- **WHEN** the library opens without FileConnection classes
- **THEN** the MIDlet does not fail during verification or loading and the user can install a cartridge through the existing location entry

### Requirement: Embedded file navigation

The system SHALL allow navigation through available roots and directories, parent navigation, and selection of regular `.wasm` files only.

#### Scenario: A directory contains multiple entry types

- **WHEN** the browser shows a subdirectory, a `.wasm`, and an unrelated file
- **THEN** the directory can be opened, the `.wasm` can be selected, and the unrelated file does not start installation

### Requirement: Explicit user access

The system SHALL access the file system only after an explicit user action and SHALL show permission denial without automatically requesting permission again.

#### Scenario: The user denies permission

- **WHEN** a JSR-75 open or list operation throws SecurityException
- **THEN** the UI explains the denial, provides Back and the manual fallback, and does not loop the prompt

### Requirement: Transactional validation

The system MUST pass the selected file through the current size/header/hash/read-back gates and SHALL publish it in the library only after a complete commit.

#### Scenario: The file exceeds 64 KiB

- **WHEN** the selected stream is longer than the cartridge limit
- **THEN** staging data is removed, no entry appears, and the user sees the exact reason

### Requirement: Selection and progress display

The system SHALL show the file name, known size, and read/validation/commit stages without reporting success prematurely.

#### Scenario: Reading completes but validation fails

- **WHEN** all bytes are loaded but WASM validation fails
- **THEN** the UI shows the validation failure rather than `Installed`

### Requirement: Safe navigation of large directories

The system SHALL bound listing memory and SHALL remain usable when there are more entries than fit on screen or in one batch.

#### Scenario: A directory contains thousands of entries

- **WHEN** the user opens a large directory
- **THEN** the browser shows a bounded page or batch and allows continuation without `OutOfMemoryError`
