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

The hardware-shortcut test temporarily enables a debug-only Accessibility service, invokes its key callback, verifies real IME switching and fake-recorder capture, inserts a synthetic transcript, and restores system settings. It covers Cancel, keyboard return, post-insert return, finish-and-hide while keeping the voice IME selected, consumed Enter events, session-scoped hardware-keyboard visibility allowance and restoration, and resumed typing. Lifecycle tests also cover paused finish, failed/truncated insertion, audio/transcript retry, recreation, discard, and editor ownership. Shell-injected keys bypassed Accessibility filtering on the test tablet, so physical Bluetooth delivery and OnePlus-specific compatibility still require device testing.

Current verification covers 116 JVM tests and all 74 tests on an Android 16 emulator (API 36). Portrait and landscape system-binding tests cover both the controlled host and normal automatic editor behavior, including connection-error/settings return, Accessibility-service restart, repeated open/finish/hide with the voice IME already selected, show timeout and restoration of both enabled and disabled hardware-keyboard visibility preferences. Fault injection drops normal show delivery in the debug IME interface and verifies actual host IME insets, a bounded landscape panel, one recording per opening, exact insertion and hiding through the direct-window fallback. Additional checks cover rejected fallback, timeout cleanup, keyboard switching and editor replacement before the deadline, and consumed finish chords with the selected voice IME hidden. Settings navigation closes the old shortcut session before its editors bind. Prior-keyboard restoration can use the bound IME when Accessibility disconnects before its switch request completes. Recorder checks distinguish owned view-only recreation from explicit configuration changes, preserving one live capture only for the former. Shortcut diagnostics are bounded, copied explicitly, and tested to exclude editor text and credentials. The Android 13 Samsung run aborted in a firmware SettingsObserver null-client crash during teardown, including with the new allowance disabled; it is not counted as a passing run for this release. Physical Bluetooth delivery, vendor keyboard behavior, and third-party editors still need device-specific checks.

The OnePlus Pad 3 (OPD2415, Android 16) also passed all 74 automated checks with a Bluetooth MX Keys Mini connected. One live release-app dictation inserted text and closed the panel; two further manual rounds remain unverified. See [device results and repeatable physical-keyboard check](oneplus-pad3-validation.md). The opt-in `PhysicalKeyboardTest` is skipped in unattended runs, so the suite reports 75 cases with 74 passing and one assumption skip. OxygenOS may require granting the isolated test app Microphone through its normal UI before running instrumentation; setup reuses an existing grant.

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
