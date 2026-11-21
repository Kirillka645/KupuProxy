# Contributing

Open a bug report or feature request with a reproducible scenario. Keep each pull request focused.
Never include real proxy credentials, tokens or keystores in reports, fixtures or commits.

## Local checks

Use **JDK 17** and Android SDK 35 (see README):

```bash
python3 -m unittest discover -s tools/release/tests -v
./gradlew :shared:desktopTest :desktop:desktopTest
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Desktop packaging must be tested on its target OS; MSI additionally needs WiX 3.x.
The PR checks must pass for Windows MSI/portable, Linux DEB/tar.gz and macOS DMG.
Do not update Gradle, AGP, Kotlin and Compose independently without checking their compatibility.

Versions live in `gradle.properties`. Add release notes in `docs/releases/` when cutting a new release,
not for every intermediate PR. Do not edit `update_manifest.json` manually: it describes the exact
published APK, not the next development build.

## Branding

`assets/branding/icon-source.png` is the approved source image. Regenerate all platform formats with:

```bash
python3 -m pip install Pillow
python3 tools/branding/generate_icons.py
```

Inspect desktop, Android circle/squircle and themed icons. Commit the generated PNG, ICO and ICNS
files together so builds need no image-processing dependency.
