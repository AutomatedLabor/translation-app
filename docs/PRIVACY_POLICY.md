# Privacy Policy — Screen Translator

**Effective date:** 2026-11-13 (v2.1.0)
**App package:** `com.translator.screen`
**Developer:** AutomatedLabor

Screen Translator is an on-device, offline-first accessibility tool that translates text
shown on your screen into a language you choose. This policy explains, in plain language,
what data the app handles and where it goes.

## 1. The one-sentence summary

**No personal data leaves your device.** The app reads on-screen text, translates it
on-device with Google ML Kit, shows the translation as an overlay, and forgets it. The
only network access is the one-time download of the offline translation model and a
best-effort check for app updates.

## 2. What the app reads

The app is an **AccessibilityService**. When you tap the Quick Settings tile (or the home-
screen widget), it reads the **text content of the accessibility tree** of the foreground
application window — the same text a screen reader (TalkBack) would read aloud. This may
include text from any app on your screen at the moment you trigger translation (e.g. a
parcel-tracking number, a chat message, a bank statement).

This text is processed **in memory** to produce a translation and is shown as an overlay.
The app **does not** send this text to any server.

## 3. Where text is stored

- **In memory:** the current translation, only while the overlay is visible.
- **On-device history (v2.1.0):** the most recent 50 translations are saved as a JSON file
  in the app's private internal storage (`filesDir/translation_history.json`). This file is
  **never backed up** (`android:allowBackup="false"` + `data_extraction_rules` exclude
  every domain from cloud backup and device transfer) and **never uploaded**. You can clear
  it at any time from the app's **History** card.
- **Crash logs (v2.1.0):** if the app crashes, a stack trace is written to
  `filesDir/crashes/` (bounded to 5 files). This is **not** uploaded; you can share it
  manually from the **Crash logs** card if you choose to file a bug report.

## 4. Network access

The app connects to the network only for:

1. **ML Kit model download** — the one-time download of the offline translation model for
   your chosen source → target language pair. This uses Google ML Kit's own HTTPS stack
   (Google's servers). After download, translation is fully offline.
2. **Update check (v2.0.3+)** — on each launch, the app asks `api.github.com` for the
   latest release tag of this repository, to show an "update available" dialog. The request
   sends no user data (only the standard GitHub API request). It fails silently if offline.

Cleartext (HTTP) traffic is blocked app-wide (`network_security_config.xml`).

## 5. What the app does NOT do

- Does **not** collect personal data, analytics, or usage statistics.
- Does **not** use Firebase, AdMob, or any advertising/analytics SDK.
- Does **not** upload screen content, translations, or crash logs.
- Does **not** access your contacts, location, camera, microphone, or storage (beyond its
  own private `filesDir`).
- Does **not** require a Google account.

## 6. Permissions

| Permission | Why | When |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Read on-screen text + draw the translation overlay | Core feature; you grant it in Accessibility Settings |
| `POST_NOTIFICATIONS` (Android 13+) | Show the "Live translation active" notification with a Stop action | Requested once on first launch |
| Internet (implicit via ML Kit) | Download the translation model + check for updates | One-time download + on launch |

## 7. Children

The app is not directed at children and does not knowingly collect any data from anyone.

## 8. Your choices

- **Disable the service:** Settings → Accessibility → Screen Translator → off.
- **Clear history:** in the app, History card → Clear history.
- **Clear crash logs:** delete the app's data, or the files in `filesDir/crashes/` via a
  file manager with root/debug access.
- **Uninstall:** removes all on-device data (history, crash logs, the downloaded model).

## 9. Open source

The app is MIT-licensed. Source: https://github.com/AutomatedLabor/translation-app

## 10. Changes

Material changes to this policy will accompany a version bump in `CHANGELOG.md`.

## Contact

Open an issue at https://github.com/AutomatedLabor/translation-app/issues
