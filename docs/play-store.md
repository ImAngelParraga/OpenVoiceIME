# Google Play Store Release Notes

This document tracks the non-code release work needed before publishing OpenVoiceIME on Google Play.

## Publishing Flow

1. Create or open a Google Play Developer account.
2. Create a new app in Play Console.
3. Complete the main store listing.
4. Add a privacy policy URL. The draft policy lives at `docs/privacy-policy.md`.
5. Complete App content forms:
   - Privacy policy.
   - Data Safety.
   - Content rating.
   - Target audience.
   - App access instructions if reviewers need provider setup details.
6. Upload a signed Android App Bundle to internal testing.
7. Test installation through Google Play on real devices.
8. If required for the account, run closed testing before production.
9. Promote to production after review and testing pass.

New apps currently need `targetSdk = 35` or higher. OpenVoiceIME already targets API 35.

## Data Safety Draft

Use these answers as the starting point for the Play Console form. Re-check against final app behavior before submission.

### Data Collection

OpenVoiceIME does not collect data to a developer-operated backend.

The app sends user audio and authentication data to the speech-to-text provider configured by the user. Depending on Play Console wording, this may need to be disclosed as data shared with a third party because the app transmits it off device.

Relevant data types:

- Audio files: recorded dictation audio sent for transcription.
- App activity or user-generated content: transcript text may be returned by the provider and inserted into another app.
- Personal info: API token only if Play classifies provider credentials this way; token is stored locally and sent to the configured provider for authentication.
- Diagnostics: local response-time metrics stay on device unless the user manually copies or shares them.

### Security Practices

- Data is encrypted in transit when using HTTPS endpoints.
- API tokens are stored locally using encrypted app storage.
- Cleartext HTTP is blocked in release builds and available only in debug builds for local development.
- Local app backup is disabled to avoid backing up credentials.

### Deletion

Users can delete local data by clearing OpenVoiceIME app storage or uninstalling the app. Remote deletion depends on the configured transcription provider.

## Screenshot Plan

Capture screenshots from a real device or emulator after installing an internal-test build.

Recommended screenshots:

1. Settings screen with provider preset/model/language fields visible.
2. Setup screen state showing microphone permission and keyboard enablement.
3. IME opened in a simple text field before recording.
4. IME recording state.
5. IME result/insert confirmation state.
6. Network diagnostic or error state if useful.

Use Android Studio screenshots, `adb exec-out screencap`, or Play Console device screenshots. Use a simple note app or text field host so the IME context is clear.

## Release Signing

Do not commit keystores or passwords.

Generate a release keystore outside the repository or in a local ignored path:

```powershell
keytool -genkeypair -v -keystore $env:USERPROFILE\openime-release.jks -alias openime -keyalg RSA -keysize 2048 -validity 10000
```

Set Gradle properties outside version control, for example in `%USERPROFILE%\.gradle\gradle.properties`:

```properties
OPENIME_RELEASE_STORE_FILE=C:\\path\\outside\\the\\repo\\openime-release.jks
OPENIME_RELEASE_STORE_PASSWORD=replace-with-password
OPENIME_RELEASE_KEY_ALIAS=openime
OPENIME_RELEASE_KEY_PASSWORD=replace-with-password
```

Build the release bundle:

```powershell
.\gradlew.bat bundleRelease
```

Upload:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Internal And Closed Testing

Internal testing is the fastest way to install through Google Play with trusted testers.

Some new personal developer accounts must complete closed testing before production access. Current Google guidance says affected accounts need at least 12 testers opted in for 14 continuous days before applying for production access.
