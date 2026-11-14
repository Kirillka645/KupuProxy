# Android release signing

Previously published APKs use the developer's original certificate, formerly configured under
`debug`. A GitHub runner's automatically generated debug keystore is **not** that key.
Replacing the certificate breaks in-place updates. Preserve the original developer key; never
create a replacement merely to make CI green. If it is lost, stop and plan an explicit migration.

## Local build

Supply all four environment variables using a local secret manager or an untracked environment:

- `KUPU_KEYSTORE_PATH`: path to the original developer `.jks`/`.keystore`;
- `KUPU_KEYSTORE_PASSWORD`;
- `KUPU_KEY_ALIAS`;
- `KUPU_KEY_PASSWORD`.

Run `./gradlew :app:assembleRelease`. Missing values fail the release build instead of falling
back to a debug key. Debug builds and Desktop tasks require none of these variables.
Do not put private values in shell history, README, Gradle properties or a tracked file.

## GitHub release setup

Repository Actions secrets:

- `KUPU_RELEASE_KEYSTORE`: Base64 of the original keystore (Base64 is not encryption);
- `KUPU_KEYSTORE_PASSWORD`;
- `KUPU_KEY_ALIAS`;
- `KUPU_KEY_PASSWORD`.

Repository Actions variable (not secret): `KUPU_RELEASE_CERT_SHA256`.
Set it to the certificate SHA-256 fingerprint independently verified from the previously
published, trusted APK:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose --print-certs trusted-previous-release.apk
```

Use the `Signer #1 certificate SHA-256 digest` value (64 hex digits, optionally colon-separated),
**not** the APK file's SHA-256. Verify this value against the original developer keystore too.
Never pin a fingerprint copied from an untrusted replacement APK.

The release workflow requires all secrets and this fingerprint before building. It verifies
cryptographic APK signatures and exact certificate equality before uploading. All Android tests,
lint, shared/Desktop tests, six Desktop formats, manifest consistency and checksums must pass.
Temporary signing files are removed even after failure. No release is published without Android.

Versions are in `gradle.properties`. Create a fresh `v<kupuVersion>` tag after merging and passing
CI; do not move or reuse published tags. Manual dispatch checks out the requested tag, not `main`.
Only tags containing the current workflow/scripts are supported by this workflow.
