# Play Console — Data Safety Form

This document is the source of truth for filling out the **Data safety** section in the
Google Play Console listing for Screen Translator (`com.translator.screen`). It reflects
the app as of **v2.1.0** and the `docs/PRIVACY_POLICY.md` analysis.

## Summary for the form

**Does your app collect or share any user data?** → **No.**

The app processes on-screen text entirely on-device (ML Kit on-device translation). The
only data that persists is a user-clearable translation history and crash logs, both in
the app's private internal storage, never backed up, never uploaded. No personal data,
no analytics, no advertising SDKs.

## Data types declared

| Data type | Collected? | Shared? | Processed ephemerally? | Notes |
|---|---|---|---|---|
| Personal info (name, email, phone, address) | No | No | Read ephemerally as on-screen text if the user translates a screen containing it; never stored beyond the in-memory overlay + the user-clearable history (which is local-only and never uploaded). |
| Financial info (card numbers, bank data) | No | No | Same as above — only if on-screen at translation time; ephemeral. |
| Health info | No | No | Same as above. |
| Messages (SMS, chat) | No | No | Same — only if a chat is on screen when the user triggers translation; ephemeral + local history. |
| Photos and videos | No | No | The app never captures images (AccessibilityService reads text nodes, not pixels; MediaProjection is deliberately not used). |
| Audio | No | No | — |
| Location | No | No | — |
| Web history | No | No | — |
| App activity (search history, interactions) | No | No | The translation history is on-screen text, not app-interaction telemetry. |
| App info and performance (crash logs) | **Local only** | No | Crash stack traces are written to `filesDir/crashes/` (bounded to 5). **Not uploaded.** The user may share one manually via the Crash logs card. |
| Device or other identifiers | No | No | — |

## Practices

- **Encrypted in transit?** N/A — no user data is transmitted. (ML Kit model download +
  GitHub update check use HTTPS but carry no user data.)
- **You can request that data be deleted?** Yes — Clear history (in-app), Clear crash logs
  (delete app data), or uninstall. (Play "data deletion" feature: the app stores no
  server-side data, so there is nothing to delete remotely; all deletion is local.)
- **Independent of children (COPPA)?** The app is not directed at children and collects no
  data from anyone.

## Computed answers for the Play Console wizard

1. **"Does your app collect or share any of the required user data types?"** → **No**
   (with the clarification that on-screen text is processed ephemerally on-device and the
   local history/crash files are not "collected" in the Play-policy sense because they are
   never transmitted off-device).
2. **"Is all of the user data collected by your app encrypted in transit?"** → N/A (no
   data transmitted).
3. **"Do you provide a way for users to request that their data be deleted?"** → **Yes**
   (local Clear history + uninstall).

> If Play's reviewer disagrees with the "No" answer on step 1 because of the local
> history/crash files, declare **App info and performance → Crash logs** as "collected,
> not shared, stored locally, deletable" and **Other on-screen text** is not a declared
> category (it is ephemeral processing, not storage/transmission).
