# Whisperer (Android)

Offline, on-device dictation for Android — the mobile fork of the desktop
Whisperer. Tap **Record**, speak, and the transcript fills an editable notes
field you can copy or share. No network, no account.

## Status — v0.1

Working, installable APK: a dictation **notes app** with offline streaming
speech-to-text.

- **Engine:** [Vosk](https://alphacephei.com/vosk/) (Kaldi) running fully
  on-device with prebuilt native libs — no NDK needed to build.
- **Pluggable backend:** the app talks to a `SpeechEngine` interface
  (`VoskEngine` is the first implementation), so a whisper.cpp backend can drop
  in later without touching the UI — same approach as the desktop's swappable
  model layer.

### Roadmap
- **Dictation keyboard (IME)** — type speech into *any* app (the Android analog
  of the desktop's "type into the focused window").
- **Floating overlay bar** + quick-tile toggle.
- **whisper.cpp backend** for higher accuracy (adds an NDK build step).
- System-audio capture (`AudioPlaybackCapture`, API 29+) for note-taking.

## Install the APK

`Whisperer-0.1-debug.apk` is a debug build. On your phone:

1. Copy the APK over (USB, Drive, etc.) and tap it, **or** with the phone in USB
   debugging mode: `adb install Whisperer-0.1-debug.apk`.
2. Allow "install from unknown sources" if prompted (debug builds aren't from
   the Play Store).
3. Open **Whisperer**, grant the microphone permission, tap **Record**, talk.

## Build from source

Needs JDK 17 and the Android SDK (platform 34, build-tools 34). The model isn't
in git (see below), so fetch it first.

```
fetch-model.bat           # downloads the Vosk model into app/src/main/assets/model
gradlew assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` must point at your SDK (`sdk.dir=...`).

### The model

The offline model (`vosk-model-small-en-us`, ~40 MB zipped / ~67 MB unpacked)
lives in `app/src/main/assets/model/` and is **gitignored**. `fetch-model.bat`
downloads and unpacks it. Without it the app builds but can't transcribe.

## Project layout

```
app/src/main/
  java/com/minidraco/whisperer/
    SpeechEngine.kt   # pluggable STT interface
    VoskEngine.kt     # Vosk backend
    MainActivity.kt   # notes UI + mic flow
  res/                # layout, theme, launcher icon
  assets/model/       # offline model (gitignored)
```
