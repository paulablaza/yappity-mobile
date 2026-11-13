# Yappity for Android privacy policy

Last updated: November 14, 2026

Yappity for Android is a free voice typing app made by Paul Martin Ablaza. This page explains what happens to your voice and your data when you use it. The short version: Yappity has no account, no analytics, no ads and no server of mine. Your audio goes from your phone to Groq and nowhere else.

## What Yappity sends, and where

When you record, Yappity sends two things to [Groq](https://groq.com), using the Groq API key you put in yourself:

1. **Your recording**, to Groq's speech to text model (Whisper), which sends back the text.
2. **That text**, to a language model on Groq that removes filler words and fixes punctuation. If you pick the Raw style, this step is skipped.

That's it. Yappity doesn't send anything to me or to anyone else, and it doesn't send anything when you're not recording. Groq handles your audio and text under its own terms and privacy policy, at [groq.com/privacy-policy](https://groq.com/privacy-policy).

## What stays on your phone

- **Your Groq API key** and your settings, in Yappity's private app storage.
- **Your last yap**, as text, so you can copy it again.
- **Your last 30 recordings**, saved before they're sent, so a yap is never lost if something fails. Older ones get deleted on their own.

Uninstalling Yappity deletes all of it. Yappity turns off Android's app backup, so none of this goes to a cloud backup either.

## Permissions

- **Microphone:** only used while you're recording. Android shows its green mic dot whenever it's on.
- **Display over other apps:** for the floating Yappy-chan tab.
- **Notifications:** for the Yappity notification with the Yap button, which also keeps Yappity running.
- **Internet:** to reach Groq.

Yappity doesn't use an Accessibility Service, doesn't read your screen, and doesn't read your clipboard. It only writes the text you dictated to it.

## Children

Yappity isn't made for children and doesn't knowingly collect anything from anyone.

## Changes

If this policy changes, the new version will be here with a new date.

## Contact

Open an issue at [github.com/paulablaza/yappity-mobile](https://github.com/paulablaza/yappity-mobile/issues).
