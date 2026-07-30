## ADDED Requirements

### Requirement: Workshop does not block the local library

The system SHALL start and serve the local library without a network request and SHALL open Workshop only after an explicit user action.

#### Scenario: The phone is offline at startup

- **WHEN** the MIDlet starts without network access
- **THEN** local cartridges are available at normal speed and a network error does not cover Library

### Requirement: Bounded, versioned catalog

The system MUST validate the schema version and limits on size, entry count, and strings before showing remote metadata.

#### Scenario: Unknown major version

- **WHEN** the endpoint returns an unsupported major schema version
- **THEN** Workshop rejects the manifest, retains the last valid cache, and shows incompatibility

### Requirement: Browse, search, and details

The system SHALL allow catalog browsing, search over locally loaded title and author fields, and a detail view with title, author, description, size, and installation state.

#### Scenario: The game is already installed

- **WHEN** a remote entry's content identity matches a local cartridge
- **THEN** the detail view shows `Installed` and does not create a duplicate record

### Requirement: Safe installation

The system MUST download the cartridge over HTTPS, verify the hard size limit, content hash, and WASM validity, and SHALL commit only after all checks pass.

#### Scenario: Hash mismatch

- **WHEN** the download completes but its content hash differs from the manifest
- **THEN** staging is removed, the cartridge is not launched, and the UI shows an integrity error

### Requirement: Progress, cancel, and retry

The system SHALL show actual download progress, SHALL allow cancellation, and SHALL offer Retry after a recoverable network error.

#### Scenario: The user cancels a download

- **WHEN** a download is in progress and the user confirms Cancel
- **THEN** the connection and stream close, staging is hidden or removed, and Library remains available

### Requirement: Offline metadata cache

The system SHALL store only the last valid bounded manifest and SHALL mark it stale when displayed without a successful refresh.

#### Scenario: Refresh fails

- **WHEN** a cache exists and the HTTPS request fails
- **THEN** Workshop may show cached entries with `Offline/Stale` without presenting them as fresh

### Requirement: No insecure downgrade

The system MUST NOT replace HTTPS with HTTP automatically after TLS or connection failure.

#### Scenario: HTTPS is unavailable

- **WHEN** the device cannot establish a secure connection
- **THEN** Workshop shows an unavailable state and Back instead of downloading the catalog or cartridge over HTTP
