<p align="center">
  <img src="docs/yappy-logo.png" width="170" alt="Yappy-chan, the Yappity mascot">
</p>

<h1 align="center">Yappity for Android</h1>

<p align="center">
  <b>Free voice typing for Android, in any app.</b><br>
  Tap Yappy-chan on the edge of your screen, yap, tap done. The cleaned up text gets copied, so you just paste it.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/price-free-8b7fff" alt="Free">
  <img src="https://img.shields.io/badge/Android-8%2B-5b4bea" alt="Android 8 and newer">
  <img src="https://img.shields.io/badge/download-under%20300%20KB-8b7fff" alt="Under 300 KB">
  <img src="https://img.shields.io/badge/accessibility-not%20needed-5b4bea" alt="No accessibility service">
  <img src="https://img.shields.io/badge/license-MIT-8b7fff" alt="MIT license">
</p>

---

## Why I made this

This is the phone version of [Yappity](https://github.com/paulablaza/yappity), my free voice typing app for Windows. I wanted something like Wispr Flow on my phone without paying every month.

The catch on Android is that most apps like this need an Accessibility Service, so they can see your keyboard and type for you. A lot of banking and e-wallet apps, like GCash here in the Philippines, refuse to open while one of those is turned on. So Yappity doesn't use one at all. It copies the text instead, and your keyboard (Gboard shows it as a paste suggestion) does the rest. You keep your own keyboard too.

---

## How it works

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/yappy-talk.gif" width="130"><br><b>Tap her and talk</b><br>Yappy-chan sits in a little square on the edge of your screen. Tap her and she opens into a box with a timer.</td>
    <td align="center" width="33%"><img src="docs/yappy-tidy.gif" width="130"><br><b>She tidies it up</b><br>Whisper Large v3 on Groq writes it down, then the um's, uh's and false starts get cleaned out.</td>
    <td align="center" width="33%"><img src="docs/yappy-done.gif" width="130"><br><b>Paste it</b><br>The text is copied. Tap any text box and paste it, or tap the suggestion on Gboard.</td>
  </tr>
</table>

You can also start a yap from the Yappity notification or from a Quick Settings tile, if you'd rather hide the bubble.

## Features

- **Free.** Runs on Groq's free plan with your own free key. No account, no subscription.
- **Tiny.** The whole app is under 300 KB. No libraries, no Google Play services.
- **No accessibility service.** GCash, banking apps and everything else keep working.
- **Works over any app.** Messenger, Discord, notes, browsers, anywhere you can paste.
- **Four styles,** same as on Windows. **Clean up** keeps your words, minus the filler. **Clean up and format** turns a yapped list into dash lines. **Organize** turns a long ramble into a clear message. **Raw** is exactly what Whisper heard.
- **Taglish friendly.** English + Tagalog mode keeps every word in the language you said it in.
- **Never loses a yap.** Every recording is saved on your phone first. If Groq fails, send it again from the app.
- **Backup model.** If the cleanup model is busy or out of free requests, Yappity tries another one, then falls back to what Whisper heard.
- **Light and dark mode,** following your phone.

---

## Get started

1. Download the `.apk` from [Releases](../../releases) and open it. Android will ask you to allow installs from your browser or file manager. That's normal for apps outside the Play Store.
2. Open Yappity and add a free Groq key. Get one at [console.groq.com/keys](https://console.groq.com/keys), it takes about a minute.
3. Allow the microphone, notifications and the floating bubble.
4. Tap **Start Yappity**. Yappy-chan shows up on the edge of your screen.
5. Open any app, tap her, say something, tap the check. Then paste.

**On Xiaomi, Redmi and POCO phones,** also turn on "Display pop-up windows while running in the background" and Autostart for Yappity, or HyperOS can hide her. The app has a button that takes you there.

### Moving her around

Drag Yappy-chan up or down, or to the other side. She snaps to the closest edge and stays there. Long-press her to open the app.

---

## Free tier limits

These are Groq's free limits per key at the time of writing.

| Model | Used for | Free limit |
| :--- | :--- | :--- |
| `whisper-large-v3` | Speech to text | 2,000 a day, 20 a minute |
| `qwen/qwen3.8-27b` | Cleanup (default) | 1,000 a day, 30 a minute |
| `openai/gpt-oss-120b` | Cleanup backup | 1,000 a day, 30 a minute |

Groq sets these numbers and can change them. Check [console.groq.com/docs/rate-limits](https://console.groq.com/docs/rate-limits) if something looks off.

---

## Privacy

Your audio goes from your phone straight to Groq and nowhere else. There's no account, no analytics, no ads and no server of mine in between. Your key, settings and the last 30 recordings stay on your phone. The full version is in [PRIVACY.md](PRIVACY.md).

---

## FAQ

**Why doesn't she pop up only when I'm typing?**
Android only lets an app see another app's keyboard through an Accessibility Service. That's exactly the thing GCash and banking apps block, so Yappity stays on the edge instead.

**Why doesn't it type the text in for me?**
Same reason. Typing into other apps also needs an Accessibility Service, or replacing your keyboard. Copy and paste works everywhere and keeps your own keyboard.

**She disappeared.**
Open Yappity and she comes back. On Xiaomi phones, turn on the background pop-up and Autostart permissions so it doesn't happen again.

**I hit the free limit.**
Your recording is saved. Tap **Send the recording again** in the app later.

---

## Build it yourself

No Android Studio and no Gradle. You need a JDK and the Android command line tools (build-tools 35 and platform 35).

```powershell
git clone https://github.com/paulablaza/yappity-mobile
cd yappity-mobile
powershell -ExecutionPolicy Bypass -File build.ps1            # builds bin\Yappity-<version>.apk
powershell -ExecutionPolicy Bypass -File build.ps1 -Install   # and installs it on your phone over adb
```

If you're an AI coding agent, or you're pointing one at this repo, read [AGENTS.md](AGENTS.md) first.

| File | What's in it |
| :--- | :--- |
| `src/.../YapService.java` | The floating tab and box, recording, Groq, copying, the notification |
| `src/.../MainActivity.java` | The app screen: setup, style, language, last yap |
| `src/.../Groq.java` | Whisper and cleanup requests, the cleanup prompts |
| `src/.../Recorder.java` | Mic recording at 16 kHz mono |
| `src/.../YapTile.java` | The Quick Settings tile |
| `src/.../Store.java` | Settings and saved recordings |
| `build.ps1` | The whole build: aapt2, javac, d8, zipalign, apksigner |

---

## License

MIT. Use it, fork it, make your own version. If you make something cool with it, I'd love to see it.
