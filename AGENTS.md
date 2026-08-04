# AGENTS.md

## Release workflow

- Every implementation change: run tests and build release APK with `./gradlew testDebugUnitTest assembleRelease`.
- Bump Android `versionCode` and `versionName` for each new GitHub release.
- Commit changes, push `main`, create and push version tag.
- Create GitHub release with `app/build/outputs/apk/release/OpenVoiceIME-release.apk` and short changelog.
- Verify release is published, APK asset uploaded, and working tree clean.
