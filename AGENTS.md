# AGENTS.md

Notes for coding agents working on Yappity for Android. Read this before changing anything.

## What it is

The Android version of [Yappity](https://github.com/paulablaza/yappity). A floating Yappy-chan tab sits on the screen edge. Tap it, talk, tap done: the audio goes to Groq (Whisper, then a small cleanup model) and the text is copied to the clipboard. Users bring their own free Groq key.

The promises that matter most:

1. **No Accessibility Service, ever.** GCash and many banking apps refuse to open while one is on. That's the whole reason this app works the way it does. No auto paste, no keyboard detection, no screen reading.
2. **Free.** Everything works on Groq's free tier. No paid services.
3. **Tiny.** No libraries, no AndroidX, no Google Play services. Plain Android Views built in code, Java only.
4. **Never lose a yap.** The recording is saved to disk before it goes to Groq.

## How it's built

- Java 8 source level against `android.jar` (platform 35), min SDK 26.
- `build.ps1` runs aapt2, javac, d8, zipalign and apksigner directly. There is no Gradle and there should never be.
- No release key set: the build is debuggable and signed with a throwaway key in `keys/` (gitignored).
- Release builds: set `YAPPITY_KEYSTORE` and `YAPPITY_KEY_PASS`. Never commit a keystore or a password.
- Bump `android:versionCode` and `android:versionName` in `AndroidManifest.xml` and `VERSION` in `BuildInfo.java` together.

## Where things are

| File | What it does |
| :--- | :--- |
| `src/.../YapService.java` | Microphone foreground service: the floating tab and box, recording flow, Groq calls, clipboard, notification |
| `src/.../MainActivity.java` | The one screen: start/stop, setup checklist, style, language, last yap |
| `src/.../Groq.java` | HTTP calls to Groq, the cleanup prompts (kept identical to the Windows app) |
| `src/.../Recorder.java` | AudioRecord at 16 kHz mono 16-bit, to WAV in memory |
| `src/.../Store.java` | SharedPreferences and saved recordings |
| `src/.../YapTile.java` | Quick Settings tile |
| `src/.../Ui.java` | Colors and rounded backgrounds |
| `res/drawable-nodpi/` | Yappy-chan frames, copied from the Windows repo's `assets/mascot` |

## Rules

- Cleanup must not change meaning. If you touch the prompts in `Groq.java`, keep them in sync with `src/Groq.cs` in the Windows repo.
- No em dashes in anything Yappity copies, in the app text or in the README. `YapService.tidy` strips them.
- Writing style for anything a user reads: casual, plain, full sentences, no hype words.
- Don't mention AI coding assistants in commits, code or docs, and don't add co-author lines.

## Testing

Connect a phone with adb (USB or wireless debugging), then `build.ps1 -Install`. On Xiaomi phones, turn on Developer options > Install via USB first. Reinstalling can reset the "Display over other apps" permission; `adb shell appops set com.paulablaza.yappity SYSTEM_ALERT_WINDOW allow` restores it. A test key can be passed with `adb shell am start -n com.paulablaza.yappity/.MainActivity --es groq_key gsk_...`. Test builds are debuggable, so `adb logcat -s Yappity` shows the app's log.

A real yap needs a human voice, so ask a person to try the full flow after changing the recorder, the service or the clipboard code.
