# Pitch Pulse

![screenshot of the app](doc/screenshot.png)

Pitch Pulse is a lightweight, offline tuner for Android built with Kotlin and Jetpack Compose. It's designed for quick and accurate pitch detection directly on your device.

## Features

- **Real-time Pitch Detection:** Captures audio and analyzes pitch in real-time using a custom McLeod Pitch Method (MPM) implementation.
- **Modern UI:** Built entirely with Jetpack Compose, featuring:
  - A reactive needle bar to show tuning accuracy (in cents).
  - A rolling history chart to visualize pitch stability over time.
- **Note Mapping:** Translates detected frequency (Hz) into musical notes.
- **Robust Permissions:** Includes a user-friendly and robust runtime permission handling flow.
- **Optimized Performance:** The audio pipeline is designed to be efficient and avoid memory allocations in the hot path.

## Roadmap

- [ ] Implement a settings panel (e.g., for A4 reference frequency, smoothing strength).
- [ ] Enhance the pitch detection confidence metric (e.g., using a YIN-style gate).
- [ ] General UI/UX improvements.

## Getting Started

1.  **Clone the repository:**
    ```bash
    git clone <your-repository-url>
    ```
2.  Open the project in the latest version of Android Studio.
3.  Build and run on an Android device or emulator.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Audio I/O:** `AudioRecord` (Mono, PCM 16-bit)
- **Pitch Detection:** Custom implementation of the McLeod Pitch Method (MPM).
- **Build System:** Gradle
