# OpenVoiceIME

Android voice keyboard for OpenAI, Groq, and self-hosted OpenAI-compatible transcription servers. Audio is recorded on your phone, sent to the selected server, and the returned text is inserted into the focused app. Requires Android 8.0 or newer.

## OpenVoice family

Three projects for dictation across devices. Use them together for a self-hosted setup, or use each with other compatible clients and services.

| Project | Role |
| --- | --- |
| [Portable ASR](https://github.com/ImAngelParraga/portable-asr) | Self-hosted transcription server with optional transcript cleanup |
| [OpenVoiceIME](https://github.com/ImAngelParraga/OpenVoiceIME) | Android voice keyboard |
| [OpenVoiceDesktop](https://github.com/ImAngelParraga/OpenVoiceDesktop) | Dictation client for Linux and Windows |

[Explore the OpenVoice family](https://imangelparraga.github.io/OpenVoiceIME/#openvoice-family).

## Install and configure

1. Download `OpenVoiceIME-release.apk` from [GitHub Releases](https://github.com/ImAngelParraga/OpenVoiceIME/releases/latest) and install it.
2. Open **OpenVoiceIME** and grant microphone permission.
3. Select a provider, enter its token, and choose a transcription model and language. Use **Custom** for your own server.
4. Test connectivity, enable OpenVoiceIME in Android keyboard settings, then select it from the keyboard picker.
5. Start dictation and tap **Stop** to send the recording.

For a self-hosted backend, follow the [Portable ASR setup guide](https://github.com/ImAngelParraga/portable-asr#quickstart). Enter its **HTTPS base URL** (without `/v1/audio/transcriptions`), bearer token, and `whisper-1`. Refresh the model list to select other enabled engines. `localhost` on the phone refers to the phone, not your server.

Release builds require HTTPS. Debug builds allow HTTP for local development. Transcription prompt context is an optional vocabulary hint; cleanup is configured on the server.

## Recover a dictation

**Retry** resends retained audio after a timeout. **Insert** retries a retained transcript without uploading again; **Copy transcript** provides manual recovery. Changing fields prevents automatic insertion into the new field. Hiding the keyboard preserves pending work while the service remains alive; **Cancel/Discard** deletes it.

See [usage and compatibility](docs/usage.md) for timeout, lifecycle, editor, and API details.

## Privacy

Audio goes to the provider you configure. Tokens use encrypted local storage; settings and response-time metrics stay on the device. Remote retention depends on your provider. See the [privacy policy](docs/privacy-policy.md).

## Build and contribute

With JDK 21 for Gradle, a JDK 17 Kotlin toolchain, and Android SDK 35 installed:

```bash
./gradlew testDebugUnitTest assembleDebug
```

On Windows, use `gradlew.bat`. The debug APK is `app/build/outputs/apk/debug/OpenVoiceIME-debug.apk`; it installs separately from the release keyboard.

[Development guide](docs/development.md): SDK setup, signing your own release, and device tests.


Licensed under [Apache 2.0](LICENSE).
