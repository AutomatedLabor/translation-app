# Device Testing Checklist — Screen Translator v2.1.0

This is the handoff for what CI **cannot** test and you **must** verify on a physical
Android device. CI proves (on an API-30 emulator): the app launches, German text is read
from a live accessibility tree, and ML Kit de→en translation produces output. The
**live overlay window** path (`service → pickTargetWindow → OverlayManager.show`) is
device-only — see `RETROSPECTIVE.md` lesson #50 for why (the window-picker excludes the
app's own package + the emulator has no foreign German app).

## 1. Install + signing verification

1. Download `screen-translator-v2.1.0-arm64-v8a-release.apk` from
   [releases/v2.1.0](https://github.com/AutomatedLabor/translation-app/releases/tag/v2.1.0)
   (or `universal` if arm64 fails with `INSTALL_FAILED_NO_MATCHING_ABIS`).
2. Verify the signature independently of CI:
   ```
   apksigner verify --print-certs --verbose screen-translator-v2.1.0-arm64-v8a-release.apk
   # expect: Verifies, v2=true, v3=true,
   #         Signer #1 certificate DN: CN=Screen Translator Release, O=AutomatedLabor, C=US
   #         Signer #1 certificate SHA-256 digest: a0b5e5f35ee134d2816a24cc467297b3024ea466f1d5d98c0104b3e8b7e27fa2
   sha256sum -c screen-translator-v2.1.0-arm64-v8a-release.apk.sha256
   ```
3. Install (accept "Unknown sources"). **v1.0.0/v1.0.1 users must uninstall first**
   (signing key changed in v1.1.0); v1.1.0+ users update in place.
4. **Android 13+:** if you can't toggle the accessibility service on, long-press the app
   icon → App Info → ⋮ → **Allow restricted settings**, then return to Accessibility.

## 2. First-run setup

1. Open the app. (On API 31+ you should see the **green splash** — platform SplashScreen.)
2. The setup screen should show, top-to-bottom: **Status** (Live: Inactive, Translating:
   Auto-Detect → English) → **Accessibility** → **Language** (From/Swap/To) → **Model** →
   **Tile** → **History** → **Crash logs** → **Help & FAQ**.
3. Enable the accessibility service (Settings → Accessibility → Screen Translator → on).
   Back in the app, the Accessibility card should flip to **Enabled** (green chip).
4. Pick source/target languages (default Auto-Detect → English).
5. Tap **Download model**. Wait for "Model downloaded — ready".
6. Pull down the Quick Settings shade twice → edit → drag the **"Translate Screen"** tile
   into the active row.

## 3. Core pipeline — live overlay (the device-only path)

Open a **real German app** (a parcel-tracking email, a German news site, a DHL page, a
banking app — any app with German text). Then:

1. Tap the tile. The overlay should appear over the German text, showing the translation
   in-place (each translated card sits on the original text's bounds).
2. **Drag the ✕** — it should move anywhere on screen; releasing after a drag does NOT
   close the overlay. A tap (no movement) on the ✕ closes it.
3. Scroll the app — the overlay should **update** (continuous mode re-translates on
   content change, debounced ~400ms), not dismiss.
4. Pull the shade → the **"Live translation active"** notification with a **Stop** action
   should be present. Tap **Stop** → the overlay dismisses + the notification cancels.
5. Wait 90s with no screen activity → the overlay should **auto-hide** + a toast
   "Live translation stopped after inactivity" appears.
6. Return to the app → the **History** card should list recent translations (last 5
   shown, last 50 stored). Tap **Clear history** → empties.

## 4. The FLAG_SECURE use case (the architecture's reason for being)

Open a banking / DHL / parcel app that blacks out screenshots. Confirm the overlay
**still translates** it (AccessibilityService reads the text tree, not pixels —
FLAG_SECURE does not block it). This is the core differentiator vs a MediaProjection +
OCR approach; if it works here, the architecture decision (v2.0.0) is validated on-device.

## 5. Widget + TalkBack

1. Long-press the home screen → Widgets → drag **"Translate"** (1×1) onto the home screen.
2. Tap it with the service enabled → toggles live translation. Tap it with the service
   disabled → opens the setup screen.
3. Enable TalkBack (Settings → Accessibility → TalkBack). Trigger a translation → the
   overlay should be **announced** (the concatenated translated texts, via the
   `contentDescription` set in v2.1.0).

## 6. Gotchas by symptom → cause → fix

| Symptom | Cause | Fix |
|---|---|---|
| Tap tile, nothing happens | Model not downloaded | App → Download model |
| Toast: "Download the model first" | Same | Same |
| Toast: "No app window found" | On home screen / only shade open | Open an actual app first |
| Toast: "No text found on screen" | App has no readable text nodes (game / image-only / canvas) | Use an app with real text |
| Toast: "Nothing to translate" | Text already in target language, or source set wrong | Check source/target; try Auto-Detect |
| Tile greyed / "unavailable" | Accessibility service not enabled | Settings → Accessibility → Screen Translator → on |
| Can't toggle service on (Android 13+) | "Restricted settings" blocks sideloaded apps | Long-press icon → App Info → ⋮ → Allow restricted settings |
| Tap tile, overlay doesn't appear (Android 7-10) | Shade doesn't auto-collapse below API 30 | Manually swipe the shade away |
| Overlay covers the text I want | By design (replaces German with translation in-place) | Tap ✕ to dismiss, or drag ✕ aside |
| Overlay vanishes after ~90s | Auto-hide (v2.1.0) | Tap tile again to resume |
| Changed target language, overlay shows old | Updates on next content change (400ms debounce) | Tap ✕ + re-toggle, or scroll |
| Bank/DHL app shows black screen | Only affects screenshot apps (MediaProjection) | Not this app — AccessibilityService reads text, not pixels |
| Translation is literal/wrong | ML Kit on-device is decent but not DeepL | Expected; proper nouns/tracking numbers/URLs are pre-filtered (intentional) |
| Overlay text too big/small | Respects your font-size accessibility setting | Adjust Settings → Display → Font size |
| Notification won't go away | Ongoing continuous-mode notification | Tap Stop on it, or tap tile / ✕ |
| arm64 APK won't install ("parsing error") | Device is 32-bit or x86 | Install the universal APK |
| Update from v1.0.0/v1.0.1 fails (signature conflict) | Signing key changed in v1.1.0 | Uninstall the old version first |
| Widget does nothing | Service not enabled/connected | Enable the service; widget opens setup otherwise |
| History empty after translating | Only source≠target translations are logged; consecutive dupes de-duped | Translate varied text; close+reopen app to refresh |
| Tile says "unavailable" after toggling service | Service not yet bound (async connect) | Wait a few seconds; pull shade again |

## 7. If something crashes

1. Reopen the app → **Crash logs** card. If a crash log exists, tap **Share latest crash
   log** → send the stack trace (share-as-text, no FileProvider needed).
2. File a bug at https://github.com/AutomatedLabor/translation-app/issues with: the toast
   text (if any), the crash log, the Android version/API level, the app you were
   translating, and the source/target languages.

## 8. What CI already proves (you don't need to re-test these)

- App + MainActivity launch without crashing (`AppLaunchSmokeTest`).
- German text is read from a live accessibility tree (`ScreenTextExtractorInstrumentedTest`).
- ML Kit de→en translation produces output on-device (`MlKitTranslationInstrumentedTest`).
- Both APK variants sign-verify (v2+v3, cert `a0b5e5…7fa2`).
- Unit tests: language prefs, target languages, bounding boxes, extractor, LRU cache,
  engine filter, translation history, crash logger.

You only need to test the **overlay rendering + interaction + the real-German-app
pipeline** — sections 3-5 above.
