# Audio architecture and diagnostics

W4ME exposes three explicit output profiles:

- **WAV synthesis** generates WASM-4 pulse, triangle, and noise waveforms as
  unsigned 8-bit, 8 kHz PCM and plays up to four MMAPI WAV Players.
- **MIDI synthesis** approximates the four channels in one generated Standard
  MIDI File Player.
- **Simple tones** converts each event to monophonic `Manager.playTone`
  output.

The selected profile is a preference, not a promise that the device can open
that technology. The Audio settings form reports `Preferred`, `Active`, and,
when a fallback occurred, `Reason`. The WAV fallback order is MIDI, simple
tones, then silence. The MIDI fallback order is simple tones, then silence.

## Sound off fast path

The global Sound switch is a hard performance mute rather than volume zero.
At the host-import boundary a muted `tone()` returns without entering the APU,
synthesizing PCM, allocating a WAV, constructing MIDI, or opening an MMAPI
Player. The end-of-frame path also skips channel and backend ticks. Turning
sound off closes existing output and clears active channel progress; turning it
back on waits for the cartridge's next `tone()` event.

The cartridge still executes its own instructions and the WASM host-import
dispatch, so sound cannot be literally free. The remaining host cost is one
cached boolean check per `tone()` import and per frame. Muted tone events do
not enter APU diagnostics or save-state data.

## Click root cause

The host trace harness records the original packed `tone(frequency, duration,
volume, flags)` calls and inspects the generated WAV data. Reproduce it through
the full test suite:

```sh
just test
```

The CSV receipts are:

```text
build/reports/audio/nyancat-tone-trace.csv
build/reports/audio/watris-tone-trace.csv
```

Before the edge-ramp fix, all 48 Nyan Cat events and all three events in the
Watris route started and ended away from unsigned 8-bit silence (`0x80`).
Every Nyan Cat event had zero release. The largest waveform step was 254.
Therefore a click-capable discontinuity existed in the generated PCM before
MMAPI or J2ME Loader processed it.

`Wasm4Pcm` now applies a one-millisecond ramp inside the requested tone
duration. A zero-attack WAV starts at `0x80`; every finite WAV returns to
`0x80`. It does not append a tail or change the logical WASM-4 duration.
Pulse, triangle, noise, pan, pitch-slide, ADSR, note-mode, and repeated
same-channel boundaries are covered by the PCM smoke and differential tests.
The internal 254-level transition of a pulse/noise waveform remains: it is part
of the waveform, not a media boundary.

This proves that the generated-WAV defect is removed. It does **not** prove
that every MMAPI implementation closes a Player without a device-side click.
With audio diagnostics enabled, the runtime prints separate
`synthesize/create/realize/prefetch/start` and `stop/close` timings so that the
Player lifecycle can be evaluated on J2ME Loader and physical handsets.
Diagnostics are disabled by default. Add the standard MIDlet property
`W4ME-Audio-Diagnostics: true` to the JAD when collecting a device log.

## Difference from the WASM-4 web runtime

The official web runtime owns one persistent 44.1 kHz stereo audio graph. Its
AudioWorklet (or ScriptProcessor fallback) preserves four channel states and
phases, mixes sample-by-sample, uses polyBLEP pulse generation, and adds a
one-millisecond hard-stop release to the triangle channel. It does not create a
media player for each note:

- <https://github.com/aduros/wasm4/blob/main/runtimes/web/src/apu.ts>
- <https://github.com/aduros/wasm4/blob/main/runtimes/web/src/apu-worklet.ts>
- <https://wasm4.org/docs/guides/audio/>

W4ME cannot use Web Audio and must work through optional, device-specific
MMAPI codecs. The current WAV profile creates finite 8 kHz WAV Players and
therefore cannot promise web-runtime phase continuity or gapless Player
replacement.

## Why the default is not an emulator-specific stream

MMAPI standardizes `Manager.createPlayer(InputStream, type)`, but it does not
require every implementation to consume a generated stream lazily. Some
devices can buffer during `realize`, `prefetch`, or `start`. A never-ending
producer can therefore block startup on real hardware unless it has bounded
buffering, shutdown unblocking, timeout/fallback behavior, and device evidence.

Long-lived generated WAV input is nevertheless a real Java ME technique:

- ASAP MIDlet generates PCM in a custom `InputStream` and feeds one MMAPI WAV
  Player. Its alternate finite double-buffer mode is explicitly marked work in
  progress:
  <https://github.com/cisco-open-source/kodi/blob/master/lib/asap/java/ASAPMIDlet.java>
- Micromod's `WavInputStream` generates bounded PCM for
  `Manager.createPlayer(stream, "audio/x-wav")`:
  <https://github.com/martincameron/micromod/blob/master/ibxm/WavInputStream.java>
- MMAPI documents the portable InputStream entry point and also warns that an
  InputStream-backed Player need not support seeking:
  <https://docs.oracle.com/javame/config/cldc/opt-pkgs/api/mm/jsr135/javax/microedition/media/Manager.html>

A future persistent mixer must therefore remain a separate hardware prototype,
use only CLDC 1.1/MIDP 2.0/MMAPI, keep a bounded ring, unblock on pause/close,
and fall back when a device buffers instead of streaming. J2ME Loader behavior
alone is not sufficient evidence to make it the default.

## Device validation

Use Watris and Nyan Cat in both orders:

1. Start from a fresh MIDlet launch with **WAV synthesis**.
2. In Watris, start a game and wait through the first game-over tone.
3. In Nyan Cat, listen for at least three full musical phrases.
4. Repeat after swapping the order of the baseline and candidate JARs.
5. Record clicks at note start, natural end, cartridge close, pause/resume, and
   same-channel replacement separately.
6. Preserve stdout lines beginning with `W4ME_PCM_LIFECYCLE`,
   `W4ME_PCM_CLOSE`, and `W4ME_MIDI_LIFECYCLE`.

Until this is run on J2ME Loader and a physical MMAPI device, audible click and
latency status is **DEVICE VALIDATION REQUIRED**.
