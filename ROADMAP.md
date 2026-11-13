# Roadmap — Screen Translator Android app

Living document. Updated every improvement cycle. Lives at
`github.com/AutomatedLabor/translation-app` (the single source of truth; the duplicate
`screen-translator` repo created in v2.0.0 is archived).

**Current release: v2.1.0** (signed with `CN=Screen Translator Release`, cert SHA-256
`a0b5e5…7fa2`). v2.1.0 completes the v2.0 roadmap phases 2-6.

---

## The two APK variants (v1.3.0+) — what each is for

From v1.3.0 the release ships **two APKs** via ABI splits, so you pick the smallest one
that works for your device:

| APK | ABIs bundled | Size | Install on |
|---|---|---|---|
| **`screen-translator-v2.1.0-arm64-v8a-release.apk`** | arm64-v8a only | ~22 MB | **Any phone from ~2017 onward** — Pixel, Samsung, OnePlus, Xiaomi, Oppo, Vivo, Realme, Motorola, etc. (64-bit ARM). This is what ~99% of users want. |
| **`screen-translator-v2.1.0-universal-release.apk`** | arm64-v8a + armeabi-v7a + x86 + x86_64 | ~72 MB | **Everything else** — 32-bit ARM phones (pre-2017), Android Studio x86_64 emulators, rare x86 tablets. The fallback. |

**Which to install?** Try `arm64-v8a` first (much smaller download). If install fails with
`INSTALL_FAILED_NO_MATCHING_ABIS`, your device is 32-bit or x86 — use `universal`.

**Why two APKs?** ML Kit's on-device translation bundles native libraries
(`libtranslate_jni.so`, `liblanguage_id_l2c_jni.so`) for 4 CPU architectures. A single
"universal" APK carries all 4 copies (~107 MB). An arm64-v8a-only APK carries one copy
(~40 MB) — a 63% size cut with zero functionality loss on any 64-bit ARM phone.

---

## What's DONE (v1.1.0 + v1.2.0)

### v1.1.0 — release-signing fix
- Dedicated release keystore (`CN=Screen Translator Release, RSA 2048, 25y`), stored as
  GitHub Secrets, replacing the throwaway debug keystore that signed v1.0.0/v1.0.1.
- CI fail-closed on signing: `apksigner verify` gate requires `Verifies` + v3=true +
  DN=`CN=Screen Translator Release` + cert SHA-256 matches pinned baseline (rotation
  detection) + explicitly rejects the debug key.
- SHA-256 sidecar + `apksigner-verify.txt` + `badging.txt` as release artifacts.

### v1.2.0 — fresh-eyes review: 1 P0 + 14 P1 fixes
- **P0: shade-open race** — `pickTargetWindow()` filtered by `isActive`, but the target
  app window is NOT active while the notification shade is open (you tap the tile from the
  shade), so the core feature silently no-opped. Fixed: pick by layer.
- **Leaks:** overlay `tv` view leak on non-BadToken throws; native ML Kit translator leak
  when `warmup()` races with `close()`; root `AccessibilityNodeInfo` recycled on API 31-32.
- **Concurrency:** `CancellationException` rethrown across 5 catch blocks.
- **UX/a11y:** edge-to-edge for Android 15; model status on resume; close-button TalkBack
  label + cutout mode; overlay text respects font-size setting.
- **Tile:** inline `dismiss()` (no stale STATE_ACTIVE); API 34+ `startActivityAndCollapse`.
- **Hardening:** `allowBackup=false`; launcher package cached once per process.

---

## What's DONE (v1.3.0 → v2.0.4)

### v1.3.0 — ABI splits + target picker + R8 + security hardening
- ABI splits (arm64-v8a + universal); R8 minification + resource shrinking; target
  language picker; `usesCleartextTraffic=false` + network/data-extraction security; CI
  gitleaks + SHA pinning + lint. versionCode 4, versionName 1.3.0.

### v1.4.0 — Android 7+ (minSdk 24) + forced v2+v3 signing
- minSdk 24 (Android 7+); every API 26/28/30 call site version-guarded; CI re-signs each
  release variant from unsigned with `apksigner --v2 --v3` (v2 covers Android 7-8, v3
  Android 9+; v1 impossible for minSdk 24).

### v1.5.0–v1.8.0 — README polish, supply-chain, continuous mode, UI i18n
- NewPipe/Termux-style README; gitleaks fail-closed; Actions SHA-pinned; continuous live
  translation (`OverlayManager.update`); model-downloaded check; UI localization (de+ur);
  repo health files; Dependabot; Mermaid diagram.

### v1.9.0 — continuous-mode notification + POST_NOTIFICATIONS
- Persistent silent notification while live translation is active; runtime POST_NOTIFICATIONS
  request on Android 13+.

### v2.0.0 — architecture decision + diagnostic Toasts
- **Kept AccessibilityService (NOT MediaProjection)** — MediaProjection cannot capture
  FLAG_SECURE windows (banking / DHL / parcel apps), the exact problem the accessibility
  approach bypasses. Diagnostic Toasts at every tile-failure point so the user knows WHY
  nothing happened. (New `screen-translator` repo — later archived in v2.1.0.)

### v2.0.1 — any-to-any translation + auto-detect + swap (Phase 2)
- Source language picker with Auto-Detect (ML Kit LanguageIdentification); swap button;
  engine refactor to (source, target).

### v2.0.2 — onboarding dialog + security fix (Phase 3 partial)
- First-run onboarding dialog; no screen content written to logcat.

### v2.0.3 — in-app update check (Phase 5 partial)
- GitHub Releases API check on launch; "update available" dialog.

### v2.0.4 — splash attempted + reverted
- Splash via `core-splashscreen` — **reverted** (artifact did not resolve on CI). Redone
  safely in v2.1.0 with the platform SplashScreen API.

---

## What's DONE (v2.1.0) — phases 3-6 completion

### Phase 3 — onboarding + status dashboard + help
- Live status dashboard card (Active/Inactive, polled 1/s, current source→target pair).
- Help & FAQ card.

### Phase 4 — overlay improvements + history
- Draggable close (✕) button (tap closes, drag repositions).
- Auto-hide after 90s idle in continuous mode.
- Translation history (JSON in `filesDir`, last 50, clearable; no Room dependency).

### Phase 5 — crash reporting + Play Store prep + tests
- Local crash logger (`filesDir/crashes/`, bounded to 5, share-as-text).
- AAB build + jarsigner-sign in CI (non-blocking artifact).
- Unit tests for TranslationHistory + CrashLogger.
- Docs: privacy policy, data-safety form, OSS licenses.

### Phase 6 — a11y, widget, notification action, splash, OSS docs
- TalkBack content description on the overlay.
- 1×1 home-screen "Translate" widget (toggle or open setup).
- Notification "Stop" action (BroadcastReceiver).
- Splash screen via platform SplashScreen API (pure XML, no core-splashscreen dep).
- OSS licenses / privacy policy / data-safety form under `docs/`.

---

## Roadmap (deferred — not blocking, with rationale)

| Item | Why deferred |
|---|---|
| **Hands-on device testing** (draggable ✕ feel, widget placement, TalkBack reading, splash animation) | No physical device in the sandbox. CI's instrumented emulator covers the extraction pipeline; these UI behaviours need a real device. |
| **Actual Play Console publication** | Needs a Google Play developer account + manual AAB upload. The signed AAB is produced by CI as an artifact. |
| **Copy-translation-to-clipboard** (tap a card) | The overlay is `FLAG_NOT_TOUCHABLE`; making individual cards tappable requires per-card hit-testing + a touchable region, conflicting with the pass-through design. |
| **OCR fallback for image-rendered text** | Fundamentally incompatible with the spec: OCR needs a screenshot (MediaProjection), which re-introduces the `FLAG_SECURE` black-screen problem the accessibility approach was chosen to bypass. |
| **Firebase Crashlytics / opt-in analytics** | Deliberately omitted — the app is offline-first + telemetry-free by design. A local crash logger covers bug-report needs without uploading anything. |
| **Room-based history with search/export** | The JSON-file history (v2.1.0) covers the "see recent translations" use case. Room would add KSP + schema migrations for marginal benefit; revisit if history grows beyond a ring buffer. |
| **`android:largeHeap` / `configChanges` tuning** | Not needed; ML Kit manages its own memory; Compose handles config changes. |
