# Firebase Test Lab — setup + usage

Firebase Test Lab runs the app's instrumented tests on **real device configs** (Pixel /
Galaxy, API 28/30/33, locale=de) instead of a single API-30 emulator — catching
device-specific + old-Android regressions the GitHub Actions emulator misses, free
on the Spark plan (30 virtual-device runs / 10 physical-device runs per day).

## The ONE manual step (only you can do it — needs a Google account + browser)

Firebase project creation requires a Google account + a browser; an agent can't do it.
This is the same boundary as "can't create a GitHub repo without a GitHub account" —
once the project exists + the key is in GitHub Secrets, the CI workflow runs itself.

1. **Create a Firebase project:** https://console.firebase.google.com → Add project →
   pick any name (e.g. `screen-translator-test`). The **Spark** plan is free (no credit
   card); it includes 30 virtual-device + 10 physical-device test runs per day.
2. **Generate a service-account key:** Project settings (⚙) → Service accounts →
   "Generate new private key" → download the JSON file.
3. **Add the key as a GitHub Secret:** https://github.com/AutomatedLabor/translation-app/
   settings/secrets/actions → New repository secret:
   - Name: `FIREBASE_SERVICE_ACCOUNT`
   - Value: the entire JSON contents you downloaded.
4. **Add the project id as a secret:** same page → New repository secret:
   - Name: `FIREBASE_PROJECT_ID`
   - Value: your Firebase project id (visible in the console URL / project settings).
5. **Opt in via a repo variable:** same page → Variables (not Secrets) → New variable:
   - Name: `ENABLE_FIREBASE_TEST_LAB`
   - Value: `true`
   (The workflow is gated on this variable so it stays dormant until you're ready.)

## Running it

- **Manual (first time):** Actions tab → "Firebase Test Lab" → Run workflow. Watch the
  run; it builds the debug + androidTest APKs, auths gcloud with your key, + runs the
  instrumented suite on the 3-device matrix (Pixel2/API30, Pixel5/API33, NexusLowRes/
  API28, all locale=de).
- **On every push (after it's green once):** edit
  `.github/workflows/firebase-test-lab.yml` + uncomment the `push:` trigger.

## What it tests

The 5 instrumented tests (same as the GitHub Actions emulator job, on real devices):
- `AppLaunchSmokeTest` — app + MainActivity reach RESUMED.
- `ScreenTextExtractorInstrumentedTest` — German text read from a live accessibility tree.
- `MlKitTranslationInstrumentedTest` — real de→en ML Kit translation on-device.
- + 2 context/package tests.

## What it does NOT yet test (the live-overlay full pipeline)

The full pipeline (`service → pickTargetWindow → OverlayManager.show`) still can't run
on Firebase Test Lab either, for the same reason as the emulator: `pickTargetWindow`
excludes the app's own package + there's no foreign German app to translate. Closing
that gap requires a **companion-app fixture** — a second Gradle module in a different
package (e.g. `com.example.germanfixture`) that shows German text + is installed
alongside the translator app during the test. Then a full-pipeline test could: install
both apps, enable the translator service via `WRITE_SECURE_SETTINGS`, launch the fixture,
toggle translation, assert the overlay + history populate. That's a future enhancement
(needs the module refactor) — tracked in RETROSPECTIVE.md "Open follow-ups" item 4.

## Cost / limits

- Spark (free): 30 virtual-device runs/day + 10 physical-device runs/day. Each workflow
  run uses 3 devices (the matrix) = 3 of your 30 daily virtual runs. So ~10 full runs/day
  before hitting the free cap. Plenty for a per-push or manual-triggered cadence.
- No credit card on Spark. Blaze (pay-as-you-go) only if you exceed the free quota.

## Why this matters (the gap it closes)

The GitHub Actions emulator is a single API-30 x86_64 google_apis image. Firebase Test
Lab gives:
- **Real OEM hardware** (Pixel / Samsung) — catches OEM-specific bugs (overlay rendering
  on punched-hole displays, Samsung's accessibility quirks).
- **API 28 + 33** in the matrix — catches old-Android (API 28) + new-Android (API 33)
  regressions the single API-30 emulator can't.
- **German locale devices** — the app's primary use case is translating German on-screen
  text; a German-locale device is the realistic test environment.
