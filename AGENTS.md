# AGENTS.md

## Release workflow

- Every completed user-facing implementation must be published as a new GitHub release unless user explicitly says not to publish.
- Every implementation change: run tests and signed release build using canonical command below. Plain `./gradlew testDebugUnitTest assembleRelease` lacks private property injection and must not be treated as evidence that signing credentials are absent.
- Bump Android `versionCode` and `versionName` for each new GitHub release.
- Commit changes, push `main`, create and push version tag.
- Create GitHub release with `app/build/outputs/apk/release/OpenVoiceIME-release.apk` and short changelog.
- Verify APK with SDK `apksigner verify --verbose` before upload; never publish unsigned APK.
- Verify release is published, APK asset uploaded, and working tree clean.
- Release is incomplete until `origin/main`, version tag, GitHub release, APK asset, and `docs/downloads/OpenVoiceIME-release.apk` all reference new signed build.

## Canonical signed release command

Signing properties live outside repository at `/home/rankis/.openime/openvoiceime-release.properties`; Gradle does not load this file automatically. Source it, map values to Gradle project-property environment variables, then build:

```bash
set -a
. /home/rankis/.openime/openvoiceime-release.properties
set +a

export ORG_GRADLE_PROJECT_OPENIME_RELEASE_STORE_FILE="$OPENIME_RELEASE_STORE_FILE"
export ORG_GRADLE_PROJECT_OPENIME_RELEASE_STORE_PASSWORD="$OPENIME_RELEASE_STORE_PASSWORD"
export ORG_GRADLE_PROJECT_OPENIME_RELEASE_KEY_ALIAS="$OPENIME_RELEASE_KEY_ALIAS"
export ORG_GRADLE_PROJECT_OPENIME_RELEASE_KEY_PASSWORD="$OPENIME_RELEASE_KEY_PASSWORD"

./gradlew testDebugUnitTest assembleRelease
ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/home/rankis/Android/Sdk}}"
"$ANDROID_SDK/build-tools/35.0.0/apksigner" verify --verbose \
  app/build/outputs/apk/release/OpenVoiceIME-release.apk
```

Never print property values, commit private property file, or commit keystore. If build reports missing properties, first check file existence and property names without displaying values, then confirm canonical injection steps ran.

## Release signing history

- `v0.3.0` was initially published unsigned because `OPENIME_RELEASE_*` properties were missing; Android rejected it as an invalid package.
- `v0.3.0` asset was replaced with a signed APK; `v0.3.1` contains the permanent fix.
- Release signing uses existing `/home/rankis/.openime/openvoiceime-release.jks` through local `OPENIME_RELEASE_*` properties; never commit keystore files or passwords.
- Missing signing properties must fail release builds before APK upload.
