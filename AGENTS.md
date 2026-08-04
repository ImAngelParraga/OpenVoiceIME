# AGENTS.md

## Release workflow

- Every implementation change: run tests and build release APK with `./gradlew testDebugUnitTest assembleRelease`.
- Bump Android `versionCode` and `versionName` for each new GitHub release.
- Commit changes, push `main`, create and push version tag.
- Create GitHub release with `app/build/outputs/apk/release/OpenVoiceIME-release.apk` and short changelog.
- Verify APK with SDK `apksigner verify --verbose` before upload; never publish unsigned APK.
- Verify release is published, APK asset uploaded, and working tree clean.

## Release signing history

- `v0.3.0` was initially published unsigned because `OPENIME_RELEASE_*` properties were missing; Android rejected it as an invalid package.
- `v0.3.0` asset was replaced with a signed APK; `v0.3.1` contains the permanent fix.
- Release signing uses the existing release keystore through local `OPENIME_RELEASE_*` properties; never commit keystore files or passwords.
- Missing signing properties must fail release builds before APK upload.
