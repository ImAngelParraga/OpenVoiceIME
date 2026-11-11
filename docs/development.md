# Development and releases

## Build

Install JDK 21 for Gradle (the daemon criteria pin version 21), a JDK 17 Kotlin toolchain, and the Android SDK (platform 35 and build-tools 35.0.0). Set `ANDROID_HOME` to your SDK directory, accept its licenses, and run commands from the repository root.

For local debug builds, use:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

On macOS/Linux, use:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Debug APK is written to `app/build/outputs/apk/debug/OpenVoiceIME-debug.apk`.

### Maintainer signed release build

Release signing credentials are not stored in repository or loaded automatically by Gradle. Choose a private location outside the checkout, for example:

```text
$HOME/.openime/openvoiceime-release.properties
$HOME/.openime/openvoiceime-release.jks
```

Create your own keystore with `keytool -genkeypair -keystore "$HOME/.openime/openvoiceime-release.jks" -alias openvoiceime -keyalg RSA -keysize 3072 -validity 10000` after creating the directory with `mkdir -p "$HOME/.openime"`. Keep this key for future upgrades; a different key cannot update an existing installation.

The properties file must define all four values below and remain private (`chmod 600`):

```properties
OPENIME_RELEASE_STORE_FILE="$HOME/.openime/openvoiceime-release.jks"
OPENIME_RELEASE_STORE_PASSWORD=...
OPENIME_RELEASE_KEY_ALIAS=...
OPENIME_RELEASE_KEY_PASSWORD=...
```

Load private file and expose values as Gradle project properties without copying secrets into repository:

```bash
set -a
. "$HOME/.openime/openvoiceime-release.properties"
set +a

export ORG_GRADLE_PROJECT_OPENIME_RELEASE_STORE_FILE="$OPENIME_RELEASE_STORE_FILE"
export ORG_GRADLE_PROJECT_OPENIME_RELEASE_STORE_PASSWORD="$OPENIME_RELEASE_STORE_PASSWORD"
export ORG_GRADLE_PROJECT_OPENIME_RELEASE_KEY_ALIAS="$OPENIME_RELEASE_KEY_ALIAS"
export ORG_GRADLE_PROJECT_OPENIME_RELEASE_KEY_PASSWORD="$OPENIME_RELEASE_KEY_PASSWORD"

./gradlew testDebugUnitTest assembleRelease
```

Plain `./gradlew assembleRelease` intentionally fails when these Gradle project properties are unavailable. Presence of `~/.openime/openvoiceime-release.properties` alone is insufficient; Gradle does not automatically read that path.

Release APK is written to:

```text
app/build/outputs/apk/release/OpenVoiceIME-release.apk
```

Verify signature before installation or upload:

```bash
ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
"$ANDROID_SDK/build-tools/35.0.0/apksigner" verify --verbose \
  app/build/outputs/apk/release/OpenVoiceIME-release.apk
```

Output must start with `Verifies` and report at least one verified APK signature scheme. Never upload an unsigned APK.

Install verified APK on connected device or emulator:

```bash
adb install -r app/build/outputs/apk/release/OpenVoiceIME-release.apk
```

## Releases

Public APKs are distributed through GitHub Releases. Each release should include:

- `OpenVoiceIME-release.apk`
- A short changelog.
- Any known setup or compatibility notes.

Maintainer policy: every completed user-facing implementation gets a new signed GitHub release unless publication is explicitly disabled for that change.

Release builds block cleartext HTTP and disable app backup to protect provider credentials. Debug builds allow cleartext HTTP for local development endpoints.

The project site also hosts the latest signed APK at:

```text
docs/downloads/OpenVoiceIME-release.apk
```

## Project layout

```text
app/src/main/java/dev/rankis/openime/
  audio/       Local microphone recording
  stt/         Transcription provider client and diagnostics
  settings/    Settings models, persistence, and screen
  metrics/     Local response-time statistics
```

Resources live in `app/src/main/res/`. JVM tests live in `app/src/test/java/`.

## Android integration tests

Connect an Android device with USB debugging enabled, then run:

```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

Debug builds use the separate application ID `dev.rankis.openime.testbed`. Tests do not replace the signed keyboard or alter its preferences. They launch isolated editor/settings activities and exercise real Android InputConnections, lifecycle callbacks, layout measurement, encrypted settings, local HTTP probes, and concurrent metrics. Lifecycle tests use fake audio capture and controlled server completion, so they do not record microphone audio.

`SystemShadeRecordingTest` temporarily selects a debug-only test IME, opens and closes Quick Settings twice, verifies that the same fake recorder/file survives, and checks that dismissal deletes it. Its `finally` block restores the previous default and enabled IMEs. Run device tests sequentially and avoid interacting with the device during this system test.

The hardware-shortcut test temporarily enables a debug-only Accessibility service, invokes its key callback, verifies real IME switching and fake-recorder capture, inserts a synthetic transcript, and restores system settings. It covers Cancel, keyboard return, post-insert return, finish-and-hide while keeping the voice IME selected, consumed Enter events, unchanged physical-keyboard visibility preference, and resumed typing. Lifecycle tests also cover paused finish, failed/truncated insertion, audio/transcript retry, recreation, discard, and editor ownership. Shell-injected keys bypassed Accessibility filtering on the test tablet, so physical Bluetooth delivery and OnePlus-specific compatibility still require device testing.

Validated on Android 13 (API 33) and an Android 16 emulator (API 36). The current Android 16 suite passes all 64 tests. Physical Bluetooth delivery, vendor keyboard behavior, and third-party editors still need device-specific checks.

## Contributing

Useful first areas:

- Add a provider adapter behind the `SttProvider` interface.
- Improve setup diagnostics for Android IME edge cases.
- Add focused tests for parsing, validation, metrics formatting, and provider request mapping.
- Improve release packaging and install documentation.

Before opening a pull request, run local verification:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Maintainers must also run signed release workflow above before publishing.

Use Conventional Commit subjects such as `feat(stt): add provider adapter` or `fix(ime): handle empty recording`.


## Public data check

Review tracked files, screenshots, and release assets for personal paths, private endpoints, credentials, and local usage data. The reusable [Portable ASR scanner](https://github.com/ImAngelParraga/portable-asr/blob/main/scripts/secret-scan.sh) can be run with this checkout as its working directory. It reports file/line locations without printing matches. It is a heuristic over current tracked files; Git history and binary formats require separate review.
