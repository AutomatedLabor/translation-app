# Third-Party Licenses — Screen Translator

This app (MIT-licensed; see `LICENSE`) depends on the following open-source libraries.
Licenses are reproduced or summarized below with source links. This list is curated from
`gradle/libs.versions.toml` as of **v2.1.0**; before a Play Store submission, regenerate
it with `./gradlew licenseDebugReport` (the `com.jaredsburrows.license` plugin) for a
definitive, transitive-included report.

## Runtime dependencies (shipped in the APK)

### AndroidX Core KTX — `androidx.core:core-ktx:1.13.1`
- License: **Apache License 2.0**
- Source: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:core/core-ktx/
- Copyright: The Android Open Source Project.

### AndroidX Lifecycle (runtime-ktx + viewmodel-compose) — `2.8.6`
- License: **Apache License 2.0**
- Source: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:lifecycle/

### AndroidX Activity Compose — `1.9.2`
- License: **Apache License 2.0**
- Source: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/

### Jetpack Compose (UI, Material3, Material Icons Extended, tooling) — BOM `2024.09.00`
- License: **Apache License 2.0**
- Source: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/
- Copyright: The Android Open Source Project.

### Google ML Kit — Translate — `com.google.mlkit:translate:17.0.3`
- License: **ML Kit Terms of Service** (https://developers.google.com/ml-kit/terms)
- Source: https://developers.google.com/ml-kit/language/translation
- Note: ML Kit bundles Google Play Services components (Apache 2.0 / Google-proprietary
  where applicable). On-device translation runs locally after model download.

### Google ML Kit — Language ID — `com.google.mlkit:language-id:17.0.6`
- License: **ML Kit Terms of Service** (https://developers.google.com/ml-kit/terms)
- Source: https://developers.google.com/ml-kit/language/language-identification

### Kotlin Coroutines (android + play-services) — `org.jetbrains.kotlinx:kotlinx-coroutines-*:1.8.1`
- License: **Apache License 2.0**
- Source: https://github.com/Kotlin/kotlinx.coroutines
- Copyright: JetBrains s.r.o.

## Test-only dependencies (NOT shipped in the release APK)

### JUnit — `junit:junit:4.13.2`
- License: **Eclipse Public License 1.0**
- Source: https://junit.org/junit4/

### Robolectric — `org.robolectric:robolectric:4.13`
- License: **MIT License**
- Source: https://github.com/robolectric/robolectric

### Google Truth — `com.google.truth:truth:1.4.4`
- License: **Apache License 2.0**
- Source: https://github.com/google/truth

### AndroidX Test (core, runner, rules, ext:junit, espresso) — `1.6.1` / `1.2.1` / `3.6.1`
- License: **Apache License 2.0**
- Source: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:test/

## License texts

### Apache License 2.0
Full text: https://www.apache.org/licenses/LICENSE-2.0.txt

### MIT License
```
Permission is hereby granted, free of charge, to any person obtaining a copy of this
software and associated documentation files (the "Software"), to deal in the Software
without restriction, including without limitation the rights to use, copy, modify, merge,
publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND...
```

### Eclipse Public License 1.0
Full text: https://www.eclipse.org/org/documents/epl-v10.php

## Notices

- This app uses Google ML Kit. Use of ML Kit is subject to the
  [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms).
- AndroidX and Jetpack Compose are © The Android Open Source Project, licensed under
  Apache 2.0.
- Kotlin is © JetBrains s.r.o., licensed under Apache 2.0 (the Kotlin compiler itself is
  not shipped in the APK; the coroutines runtime library is).
