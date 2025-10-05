# Pitch Pulse

This is the stub README file for my Android project

Last updated: 2025-10-05

## Overview

Pitch Pulse is a lightweight offline tuner for Android built with Kotlin and Jetpack Compose.  
Variant B implements an in-app pitch detector based on the McLeod Pitch Method (MPM) with optional YIN-inspired checks for robustness. No third-party DSP libraries are required.

## Current Status

- Audio capture pipeline implemented with robust runtime permission handling (RECORD_AUDIO).
- Streaming of PCM audio frames from AudioRecord into a Kotlin callback: onFrame(FloatArray).
- Debug UI shows:
  - Start/Stop controls
  - Frame counter
  - RMS level (sanity/visual feedback)
- Compile- and runtime-verified on modern Android with Jetpack Compose.
- Permissions and SecurityException handling in place (pre-check and try/catch around startRecording).

## Tech Stack

- Language: Kotlin
- UI: Jetpack Compose
- Audio I/O: AudioRecord (Mono, PCM 16-bit)
- Minified DSP: Custom implementation (MPM with optional YIN-style confidence)
- Target: Offline processing on-device
- Build System: Gradle (Android)

## Permissions

- Manifest:
  - `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
- Runtime:
  - Explicit permission check before starting AudioRecord
  - Handles potential SecurityException during startRecording
- UX:
  - If permission is missing, the UI requests it before starting audio

## Audio Pipeline (Implemented)

- Sample rate: 44100 Hz
- Channel: Mono
- Encoding: PCM 16-bit
- Frame size: 2048 samples (configurable)
- Hop size: equals frame size (initially); can be tuned to reduce CPU/latency
- Callback: `onFrame(FloatArray)` delivers normalized floats in [-1, 1]
- Basic metrics: RMS computed in UI for quick sanity check

## Pitch Detection (Planned for Variant B)

- Method: McLeod Pitch Method (MPM)
  - Steps:
    1) Optional windowing (Hann) on the frame
    2) Autocorrelation-like difference function via efficient normalized square difference (NSDF)
    3) Peak picking on NSDF
    4) Parabolic interpolation for sub-sample peak refinement
    5) Period-to-frequency conversion: `f = sampleRate / period`
  - Confidence:
    - Peak-to-neighbor ratio (clarity)
    - Optional YIN-style cumulative mean normalized difference (CMND) threshold as a secondary gate
- Expected frequency range: ~60 Hz to 1000 Hz
  - Low-cut on unrealistic long periods to avoid octave errors
  - High-cut to ignore peaks below minimal period
- Latency/CPU:
  - Start with 2048-sample frames; consider hop size of 1024 for smoother updates
  - Avoid allocations in the hot path (reuse buffers)
- Output:
  - Frequency in Hz
  - Confidence (0..1)

## Music Mapping

- Tuning reference: A4 = 440 Hz (configurable later)
- Temperament: 12-TET
- Note index: `n = round(12 * log2(f / 440))`
- Nearest note frequency: `f_note = 440 * 2^(n/12)`
- Cents deviation: `cents = 1200 * log2(f / f_note)`
- Display:
  - Note name with octave (e.g., A4, C#3)
  - Cents deviation (±50 range commonly highlighted)

## UI/UX (Initial Tuner Screen)

- Controls:
  - Request mic permission
  - Start/Stop audio stream
- Indicators:
  - Live frequency (Hz)
  - Note name + cents deviation
  - Horizontal bar/needle: “flat” vs “sharp”
  - Short rolling history (2–5 s) for pitch stability visualization

## Error Handling & Diagnostics

- Permission denied:
  - UI remains in “permission missing” state and offers to request it again
- SecurityException:
  - Caught and reported; start() returns false
- Audio device unavailable:
  - Start gracefully fails and surfaces message
- Battery optimization (Samsung etc.):
  - Recommend disabling app optimization for stable background capture during tests

## Folder Structure (Proposed)

```
app/src/main/java/de/dbm/pitchpulse/
  core/
    audio/           # AudioInput (AudioRecord lifecycle, frame delivery)
    permissions/     # hasRecordAudioPermission(...)
  feature/
    tuner/
      domain/        # Pitch detection core (MPM, peak picking, note mapping)
      ui/            # TunerScreen (Compose), visual components
      visual/        # Needle/bar, history strip
```

## Public API Sketch (Kotlin, subject to change)

- Audio input:
  - `class AudioInput(context, sampleRate=44100, frameSize=2048, onFrame: (FloatArray) -> Unit)`
  - `suspend fun start(): Boolean`
  - `suspend fun stop()`
- Pitch:
  - `fun estimatePitch(frame: FloatArray, sampleRate: Int): PitchResult?`
  - `data class PitchResult(freqHz: Float, confidence: Float)`
- Music mapping:
  - `fun mapToNote(freqHz: Float, a4: Float = 440f): NoteInfo`
  - `data class NoteInfo(name: String, octave: Int, cents: Float, nearestFreq: Float)`

## Build & Run

1) Sync Gradle.
2) Ensure the manifest declares RECORD_AUDIO.
3) Install/run on device; grant microphone permission on first launch.
4) Press Start; verify “Audio running,” frames increment, and RMS value moves.
5) If using Samsung devices: disable battery optimization for stable testing in the background.

## Test Plan (Short)

- Silence: frequency should be “—” (no stable peak); confidence low.
- Single tone (e.g., tuning fork/A4=440 Hz): frequency within ±1–2 Hz, cents near 0.
- Sweep test: verify stable tracking 80–1000 Hz with minimal octave jumps.
- Noise/voice: verify confidence gating and note mapping remain sensible.

## Next Steps

- Implement MPM core (NSDF + peak picking + interpolation)
- Add confidence gating (peak ratio + optional CMND/YIN gate)
- Build note mapping with A4 reference and cents readout
- Draw needle/bar and rolling history
- Optimize allocations and consider smaller hop size for smoother UI updates
- Add small settings panel (A4, smoothing, noise gate)
