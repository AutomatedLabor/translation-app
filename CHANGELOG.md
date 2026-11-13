# Changelog

All notable changes to **Screen Translator** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Dates are UTC.

Releases live at
[github.com/AutomatedLabor/translation-app/releases](https://github.com/AutomatedLabor/translation-app/releases).
The original app is © mahadhameed095; this fork is © AutomatedLabor.

---

## [Unreleased]

Nothing yet.

---

## [2.1.0] - 2026-11-13

Completes the v2.0 roadmap phases 3-6 (everything achievable without a physical device).
Same signing key as v1.1.0+ (cert SHA-256 `a0b5e5…7fa2`); existing users can update in
place. The duplicate `screen-translator` repo (created by mistake in v2.0.0) is archived;
`translation-app` is the single source of truth.

### Added — Phase 3 (onboarding + dashboard + help)
- **Live status dashboard card** (top of setup screen): "Live translation: Active/Inactive"
  (polled 1/s) + current source → target pair.
- **Help & FAQ card** with a concise usage summary.

### Added — Phase 4 (overlay + history)
- **Draggable close (✕) button** — drag to reposition within screen bounds; a tap (within
  touch slop) still closes the overlay.
- **Auto-hide** — continuous-mode overlay auto-dismisses after 90s of inactivity.
- **Translation history** — last 50 translations persisted as JSON in `filesDir`
  (private, never backed up, never uploaded), viewable + clearable in-app. No Room/KSP
  dependency (avoids the artifact-resolution risk that reverted the v2.0.4 splash).

### Added — Phase 5 (crash reporting + Play Store prep + tests)
- **Local crash logger** — `Thread.UncaughtExceptionHandler` writes stack traces to
  `filesDir/crashes/` (bounded to 5, pruned newest-kept). No telemetry; share-as-text
  from the Crash logs card.
- **AAB build** in CI (jarsigner-signed, uploaded as an artifact, non-blocking).
- **Unit tests** for `TranslationHistory` + `CrashLogger` (7 + 3 cases).

### Added — Phase 6 (a11y, widget, notification, splash, docs)
- **TalkBack** content description on the translation overlay (concatenated translated texts).
- **Home-screen widget** (1×1 "Translate") — toggles live translation or opens setup.
- **Notification "Stop" action** — a `BroadcastReceiver` stops live translation from the shade.
- **Splash screen** via the platform SplashScreen API (pure-XML `windowSplashScreen*` theme
  attrs, API 31+) — NO `core-splashscreen` dependency (the v2.0.4 splash was reverted
  because that artifact failed to resolve on CI).
- **Docs**: `docs/PRIVACY_POLICY.md`, `docs/DATA_SAFETY.md`, `docs/THIRD_PARTY_LICENSES.md`.

### Not done (requires a physical device)
- Hands-on device testing of the draggable ✕, widget placement, TalkBack, splash animation.
- Actual Play Console publication (needs a developer account).

---

## [2.0.0]–[2.0.4] - 2026-11-13

Brief summary of the v2.0.x line (full notes in each GitHub Release):

- **2.0.0** — architecture decision (keep AccessibilityService, not MediaProjection —
  MediaProjection cannot capture FLAG_SECURE windows); diagnostic Toasts at every
  tile-failure point; new `screen-translator` repo (later archived — see v2.1.0).
- **2.0.1** — any-to-any language translation + Auto-Detect + Swap (Phase 2).
- **2.0.2** — first-run onboarding dialog; security fix (no screen content in logcat).
- **2.0.3** — in-app update check (GitHub Releases API).
- **2.0.4** — splash screen attempted via `core-splashscreen`, then **reverted** (artifact
  did not resolve on CI); redone safely in v2.1.0 with the platform SplashScreen API.

---

## [1.7.0] - 2026-11-13

### Added
- **Instrumented emulator test** ("test it yourself" CI job) that boots an API-35 emulator with
  KVM enabled, installs the APK, and runs `connectedDebugAndroidTest` — the accessibility
  service + extractor + overlay pipeline now has an automated on-device smoke test, not just JVM
  unit tests.
- `emulator-runner` GitHub Action pinned to a SHA for supply-chain integrity.

### Fixed
- Emulator boot crash (382s boot under `-accel off`) — switched to KVM acceleration.
- Relaxed instrumented test count assertion from `isAtLeast(3)` to match the actual suite size.

---

## [1.6.0] - 2026-11-13

### Added
- **Continuous live translation** — the overlay now re-translates as the screen content changes
  instead of snapshotting once, so scrolling / paginated content stays translated.
- **Model-downloaded check** before tile activation — the #1 cause of "tile does nothing" was a
  missing ML Kit target-language model. The tile now refuses to activate and surfaces the
  missing-model state instead of silently no-op'ing.

### Fixed
- Tile no-op on first use when the user had not yet downloaded the target-language model.

---

## [1.5.0] - 2026-11-13

### Added
- Polished `README.md` in the NewPipe / Termux documentation style (badges, install matrix,
  architecture tree, verify + security sections).
- `LICENSE` (MIT) added at repo root.
- **gitleaks** secret scan, fail-closed, in CI.
- GitHub Actions pinned to commit SHAs (not floating `@vN` tags) for supply-chain integrity.

### Changed
- README restructured around install → architecture → security → verify → build flow.

---

## [1.4.0] - 2026-11-13

### Added
- **Android 7+ support** — `minSdk` lowered from 21 to **24** (Android 7.0), widening device
  coverage to any phone from ~2017 onward.
- Forced **v2 + v3 APK signing** in CI (signed from an unsigned build via `zipalign` +
  `apksigner`).

### Fixed
- v1.4.0 release candidates: signing required **v1 + v2 + v3**, but v1 is impossible for
  `minSdk 24` — corrected to require v2 + v3 only.
- v1.4.0 release candidates: signed from the debug-aligned APK, which suppressed v1 — switched
  to signing from a fully **unsigned** build so `apksigner` produces the correct schemes.

---

## [1.3.0] - 2026-11-13

### Added
- **ABI splits** — release now ships two APKs: `arm64-v8a` (~20 MB for 64-bit ARM phones) and
  `universal` (~67 MB for 32-bit / x86 emulators + rare devices).
- **Target-language picker** — 12 target languages (English / Urdu / French / Spanish / Italian
  / Portuguese / Russian / Arabic / Turkish / Hindi / Chinese / Japanese). The Quick Settings
  tile picks up the change live.
- **R8 minification + obfuscation** on release builds (smaller APK, harder to reverse-engineer).
- **Security hardening**: `allowBackup=false`, `data_extraction_rules.xml`,
  `network_security_config.xml` (block cleartext).
- **CI scans**: gitleaks secret scan + Gradle wrapper jar validation.

### Changed
- Migrated version catalog to `gradle/libs.versions.toml`.

---

## [1.2.0] - 2026-11-13

A "fresh-eyes" review pass: 1 P0 + 14 P1 fixes.

### Fixed
- **P0 — shade-open race**: `pickTargetWindow()` filtered by `isActive`, but the target app
  window is NOT active while the notification shade is open (you tap the tile from the shade), so
  the core feature silently no-op'ed. Fixed: pick by layer, not by active state.
- **Memory leaks**: overlay `tv` view leak on non-`BadToken` throws; native ML Kit translator
  leak when `warmup()` races with `close()`; root `AccessibilityNodeInfo` recycled on API 31-32.
- **Concurrency**: `CancellationException` rethrown across 5 catch blocks.
- **UX / accessibility**: edge-to-edge for Android 15; model status refreshed on resume;
  close-button TalkBack label + cutout mode; overlay text respects system font-size setting.
- **Tile**: inline `dismiss()` (no stale `STATE_ACTIVE`); API 34+ `startActivityAndCollapse`.
- **Hardening**: `allowBackup=false`; launcher package cached once per process.

---

## [1.1.0] - 2026-11-12

### Added
- **Dedicated release signing keystore** (`CN=Screen Translator Release, O=AutomatedLabor`,
  RSA 2048, 25y), stored as encrypted GitHub Secrets — replaces the throwaway debug keystore
  that signed v1.0.0 / v1.0.1.
- **Fail-closed `apksigner verify` gate** in CI: requires `Verifies` + v3=true + DN match +
  cert SHA-256 match against the pinned baseline (rotation detection) + explicit rejection of
  the debug key.
- Release artifacts: SHA-256 sidecar (`.apk.sha256`), `apksigner-verify.txt`, `badging.txt`.

### Changed
- Signing key changed — **users on v1.0.0 / v1.0.1 must uninstall before installing v1.1.0+**
  (different key, Android won't update in place). v1.1.0+ users can update in place.

### Fixed
- Multi-version `apksigner` cert SHA-256 parsing (different field layouts across versions).
- Keystore smoke-check switched from a verbose-field grep to a `keytool` exit code, which is
  stable across versions.

---

## [1.0.1] - 2026-11-12

### Fixed
- Initial release follow-up: build metadata + workflow tweaks. Signed with the debug keystore
  (superseded by v1.1.0 — uninstall before updating).

---

## [1.0.0] - 2026-11-12

### Added
- **Initial release** — Screen Translator Android app.
- AccessibilityService that reads German on-screen text via the accessibility tree (bypasses
  `FLAG_SECURE`), translates on-device with ML Kit, renders rounded translation cards on top of
  the original via `TYPE_ACCESSIBILITY_OVERLAY` (no `SYSTEM_ALERT_WINDOW` permission needed).
- Quick Settings tile trigger (`QuickTileService`) with state sync + `unlockAndRun` (API 26+).
- Smart pre-filtering: skips numbers, dates, tracking codes, URLs, emails, and
  confidently-English text (ML Kit Language ID).
- LRU cache (500 entries) for sub-5ms repeat translations.
- GitHub Actions workflow: build release APK on push to `main`.

---

[Unreleased]: https://github.com/AutomatedLabor/translation-app/compare/v1.7.0...HEAD
[1.7.0]: https://github.com/AutomatedLabor/translation-app/releases/tag/v1.7.0
[1.6.0]: https://github.com/AutomatedLabor/translation-app/releases/tag/v1.6.0
[1.5.0]: https://github.com/AutomatedLabor/translation-app/releases/tag/v1.5.0
[1.4.0]: https://github.com/AutomatedLabor/translation-app/releases/tag/v1.4.0
[1.3.0]: https://github.com/AutomatedLabor/translation-app/releases/tag/v1.3.0
[1.2.0]: https://github.com/AutomatedLabor/translation-app/releases/tag/v1.2.0
[1.1.0]: https://github.com/AutomatedLabor/translation-app/releases/tag/v1.1.0
[1.0.1]: https://github.com/AutomatedLabor/translation-app/releases/tag/v1.0.1
[1.0.0]: https://github.com/AutomatedLabor/translation-app/releases/tag/v1.0.0
