# SoundBreaker Studio

Android DAW (Digital Audio Workstation) built with Kotlin & Jetpack Compose. Designed for tablet landscape.

![Edit Tab](screenshot_edit.png)

## Features

### Multi-Tab Workspace

**Edit** — Timeline with multi-track waveform editing

![Edit Tab](screenshot_edit.png)

- Zoom in/out timeline with pinch
- Select, move, cut, delete audio regions
- Nudge buttons (◀ ▶) for precise positioning
- Tap timeline to set playhead
- Auto-scroll to follow playhead during playback

**Mix** — Full mixer console with per-channel strips

![Mix Tab](screenshot_mix.png)

- Per-channel volume faders (vertical)
- Pan knobs with equal-power law
- Per-channel Mute / Solo
- Real-time level meters during playback
- Master strip with volume, pan, and output device selector

**FX** — Per-track effects chain with parameter editing

![FX Tab](screenshot_fx.png)

- Track selector (left panel)
- Effect chain management (middle): add, toggle, remove effects
- Parameter sliders per effect (right panel)
- Effects: Compressor, Reverb, Delay, Chorus, Distortion, Filter
- Real-time processing in playback loop

**Master EQ** — 10-band parametric equalizer on master bus

![Master EQ Tab](screenshot_master_eq.png)

- 10-band graphic EQ (31 Hz – 16 kHz)
- 12 built-in presets (Flat, Rock, Pop, Jazz, Classical, Electronic, Hip-Hop, Acoustic, Bright, Warm, Vocal, Bass Boost)
- Waveform + EQ curve visualization
- Power toggle, preset chips, reset button
- Applied post-mix, pre-master-volume

---

### Audio Engine

- Record stereo audio (44.1 kHz, 16-bit PCM)
- Import WAV, AIFF, MP3, AAC files
- Export to WAV
- Multi-track playback with real-time mixing
- Sample rate auto-detect + resample to 44.1 kHz on import
- WAV chunk scanning: handles non-standard WAV files (JUNK, INFO chunks)
- Streaming file read (8 KB chunks) — prevents OOM with 7+ large tracks

### Editing

- Region selection, deletion, cutting
- Move regions via drag or nudge buttons
- Per-region BPM-aware width scaling
- Timeline with 200 bars

### Track Controls

- Mute / Solo per track
- Volume, Pan, EQ per track
- Track rename (double-tap)
- Add / Remove tracks
- Audio input routing per track
- Audio output device selection

### Transport

- Play / Pause / Stop / Record
- Fast-forward / Rewind
- BPM editing (tap BPM in transport bar)
- Time signature display
- Loop toggle
- Click track (metronome)
- Bar:Beat:Ticks counter + elapsed time

### Visualization

- Waveform display (symmetric filled envelope)
- 60 fps smooth playhead with auto-scroll
- Real-time level meters in mixer

### Project Management

- Save / Open projects (.sbrk format)
- Custom project names
- BPM and all track settings persist

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM
- **Audio**: AudioTrack + AudioRecord, MediaCodec decode, custom DSP
- **Tablet Landscape** (primary)
- **Min SDK**: 26 (Android 8.0) / **Target SDK**: 36

## Project Structure

```
app/src/main/java/id/soundbreaker/studio/
├── MainActivity.kt
├── audio/
│   ├── AudioEngine.kt           (record, playback, WAV read/write, resampling, mix)
│   ├── EffectsProcessor.kt      (per-track effects: compressor, reverb, delay, chorus, dist, filter)
│   └── MasterEqProcessor.kt     (10-band biquad EQ for master bus)
├── data/
│   ├── Track.kt                  (data models)
│   ├── ProjectData.kt            (JSON serialization)
│   └── MasterEqPresets.kt        (12 EQ presets)
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   └── Theme.kt
│   ├── components/
│   │   ├── TopBar.kt             (New, Open, Save, Export, tabs)
│   │   ├── TrackListItem.kt      (track list with M/S/R)
│   │   ├── Timeline.kt           (ruler, track lanes, waveform)
│   │   ├── InspectorPanel.kt     (volume, pan, EQ, effects, delete)
│   │   ├── TransportBar.kt       (play/pause/stop/record, BPM, loop, click)
│   │   └── MiniChannelFader.kt   (mini mixer faders)
│   └── screens/
│       ├── StudioScreen.kt       (main layout, tab routing)
│       ├── MixScreen.kt          (full mixer console)
│       ├── FxScreen.kt           (effects chain editor)
│       └── MasterEqScreen.kt     (10-band parametric EQ)
└── viewmodel/
    └── StudioViewModel.kt        (state management, audio engine)
```

## Build

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

- `RECORD_AUDIO` — audio recording
- `MODIFY_AUDIO_SETTINGS` — audio configuration
- `MANAGE_EXTERNAL_STORAGE` — save/load projects

## File Format

### Save Project (.sbrk folder)

```
project_name.sbrk/
├── project.json      (settings, track metadata, regions, master EQ)
├── track_1.wav       (audio files)
├── track_2.wav
└── ...
```

### project.json structure

```json
{
  "name": "My Project",
  "bpm": 120,
  "timeSigNumerator": 4,
  "timeSigDenominator": 4,
  "isLooping": true,
  "masterEq": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
  "masterEqPreset": "Flat",
  "tracks": [
    {
      "id": 1,
      "name": "Audio 1",
      "type": "AUDIO_STEREO",
      "color": "#FF4757",
      "volume": 0.75,
      "pan": 0.5,
      "isMuted": false,
      "isSolo": false,
      "channels": 2,
      "bitDepth": 16,
      "sampleRate": 44100,
      "audioFile": "Audio 1.wav",
      "eqLow": 0.0,
      "eqMid": 0.0,
      "eqHigh": 0.0,
      "regions": [
        {
          "id": 1,
          "startBar": 1.0,
          "widthBars": 16.0
        }
      ]
    }
  ]
}
```
