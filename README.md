# SFX Timeline Editor — Android source project

Android app for the workflow:

1. **Get Code** — enter a script, select sound-design style/language, generate and copy a reusable external-AI master prompt. The user must attach the actual video to the external video-capable AI.
2. **I Have Code** — select/paste the AI JSON and import the source video. The parser validates schema version, required project duration, unique asset/event IDs, asset references, timestamps, volume, pan and fade ranges.
3. **Asset Manager** — creates one upload slot per JSON asset. Supports individual assignment and bulk filename/asset-ID-prefix matching.
4. **Timeline** — video preview, event list, event volume adjustment and missing-asset status.
5. **Export** — uses AndroidX Media3 Transformer Composition to combine the source video with audio sequences offset by generated PCM silence WAVs. Gain and per-event fade envelopes are applied through Media3 GainProcessor.

## Build APK

Open the project in Android Studio with JDK 17 and Android SDK Platform 36. Sync Gradle, then choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**. Or push to GitHub and run the included **Actions → Build SFX Timeline Editor APK** workflow. The workflow uploads `app-debug.apk` as an artifact.

## Honest implementation status / limitations

This is a source project, not a precompiled or device-tested APK. The current environment does not have the Android SDK/Gradle toolchain to compile and install it here. The GitHub Actions workflow is provided to build it.

Implemented in source: Get Code prompt generation; JSON import/paste and validation; video picker; asset slots; individual and bulk audio assignment; filename matching; timeline event list; volume control; video preview; Media3 multi-sequence MP4 render; per-event gain and fades; save rendered MP4 through Android document picker.

Not yet implemented: waveform drawing; draggable clip editing; editable event start/end fields; automated speech-aware music ducking; stereo pan rendering; separate WAV master export; project database/autosave; render pause/resume. Media3 Composition currently does not support crossfading between composition tracks. Confirm all imported files and export on the target Android device before relying on it for production work.

## JSON compatibility

Expected top-level keys: `schema_version`, `project`, `analysis`, `audio_settings`, `assets`, `timeline`, `mixing`, `validation`. The parser requires `project.video_duration_ms`, `assets[]`, and `timeline[]`. Timeline events use integer milliseconds. `source_frame` and informational metadata may be present but are not needed by the renderer.

## Project Library (added)
- Use **Save Project** from Home or Timeline to store the current project on this device.
- Open **Projects** from the bottom navigation to browse saved projects.
- **Open / Edit** restores the saved timeline, asset assignments and source video URI. After editing, tap **Update Saved Project** to overwrite that saved entry.
- **Delete** removes the selected saved project from the local project library.
- Project records are stored in app-private SharedPreferences as JSON. They are local to this Android installation and are not cloud-synced. Keep the original video/audio files in place and do not clear app data if you want to retain the saved library.
