## ADDED Requirements

### Requirement: Optional JSR-82 UX

The system SHALL show Bluetooth Netplay only when a JSR-82 transport is available and SHALL otherwise keep the single-player runtime free of netplay polling and allocation.

#### Scenario: JSR-82 is unavailable

- **WHEN** the system menu opens on a device without the Bluetooth API
- **THEN** the netplay action is hidden or clearly marked unavailable and single-player frame timing remains unchanged

### Requirement: Host and Join flows

The system SHALL allow the user to explicitly create a host service or select a discovered peer, SHALL show progress and cancellation controls, and SHALL request Bluetooth permission only after a user action.

#### Scenario: Discovery is canceled

- **WHEN** the user cancels device discovery
- **THEN** discovery closes, the game or Library remains available, and no session is created

### Requirement: Compatible handshake

The system MUST verify protocol and runtime compatibility and exact cartridge identity before the first multiplayer update.

#### Scenario: Cartridges differ

- **WHEN** peers have different cartridge hash, length, or CRC values
- **THEN** the connection does not start the game and both sides see `Cartridge mismatch`

### Requirement: Synchronized multiplayer input

The system SHALL write local and remote inputs to separate WASM-4 gamepad bytes for the same numbered frame and MUST NOT advance the frame without a complete input set.

#### Scenario: A remote packet is delayed

- **WHEN** the remote player's input for the next frame is missing
- **THEN** the runtime waits in the netplay state instead of simulating zero input on only one side

### Requirement: Coordinated pause and exit

The system SHALL synchronize menu pause and continue at a shared frame boundary and SHALL notify the peer before Restart, Library, or session termination.

#### Scenario: Host opens the menu

- **WHEN** the host requests the System Menu during a session
- **THEN** both runtimes stop on the same frame number and Join shows a paused status

### Requirement: Save/Load is unavailable during a session

The system SHALL reject user `Save State` and `Load State` actions during active netplay with a clear message.

#### Scenario: The user selects Load State

- **WHEN** a netplay session is active
- **THEN** runtime state remains unchanged and `State loading disabled during netplay` is shown

### Requirement: Disconnects and desynchronization are visible

The system MUST stop the shared runtime after a timeout, transport loss, or checksum mismatch and SHALL offer Retry/Exit without silently continuing in single-player mode.

#### Scenario: The peer is lost

- **WHEN** the heartbeat or input timeout expires
- **THEN** the game stops updating and the UI shows the disconnected peer and the available Retry/Library actions

### Requirement: Bounded queues

The system SHALL enforce fixed limits on packet and input queues and SHALL terminate the session with a controlled error instead of allowing unbounded memory growth.

#### Scenario: A peer sends packets outside the window

- **WHEN** a sequence number falls outside the agreed future-frame window
- **THEN** the packet is rejected or the session ends with a protocol error without allocating unbounded buffers
