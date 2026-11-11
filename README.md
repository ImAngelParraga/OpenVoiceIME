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
2. Open **OpenVoiceIME → Keyboard setup** and choose **Use HeliBoard** or **Keep my current keyboard**. The guide offers downloads, keyboard settings, microphone permission, and a test field.
3. Select a provider, enter its token, and choose a transcription model and language. Use **Custom** for your own server.
4. Test connectivity, enable OpenVoiceIME in Android keyboard settings, then select it from the keyboard picker.
5. Open voice input to record automatically. Use **Pause/Resume** to think between passages, then **Transcribe** to send the recording.

OpenVoiceIME is a **voice-only keyboard** used alongside your normal typing keyboard. HeliBoard can open it from its microphone button; HeliBoard is optional and can be installed directly from its [official GitHub APK](https://github.com/HeliBorg/HeliBoard/releases/latest), without another app store. Gboard's microphone does not provide a supported way to select OpenVoiceIME. You can keep Gboard for typing and use Android's keyboard picker where your phone exposes voice keyboards. See [keyboard setup and HeliBoard instructions](docs/usage.md#choose-how-to-open-dictation).

For a self-hosted backend, follow the [Portable ASR setup guide](https://github.com/ImAngelParraga/portable-asr#quickstart). Enter its **HTTPS base URL** (without `/v1/audio/transcriptions`), bearer token, and `whisper-1`. Refresh the model list to select other enabled engines. `localhost` on the phone refers to the phone, not your server.

Release builds require HTTPS. Debug builds allow HTTP for local development. Transcription prompt context is an optional vocabulary hint; cleanup is configured on the server.

To stay in the voice keyboard after inserting text, disable both **Return to keyboard after insert** and **Hide after insert** in Behavior. The next recording starts automatically. Existing saved preferences are preserved on upgrade.

The centered keyboard keeps **Cancel**, **Pause/Resume**, **Delete**, and **Transcribe** visible, with direct settings, language, and keyboard-switch shortcuts. Cancel follows **Hide after cancel**: disable it to restart recording without closing the panel. Delete edits text independently of recording.

## Bluetooth keyboard shortcut

On Android 11 or newer, open **Keyboard setup → Set up keyboard shortcut** and enable **OpenVoiceIME keyboard shortcut** in Android Accessibility settings. Keep the system’s on-screen keyboard option off. Focus a text field and press **Ctrl+Shift+Space** to open recording; closing the panel returns to the previous keyboard. Works independently of the selected typing keyboard, including HeliBoard. The optional service filters the shortcut’s key events without reading screen content or saving typed text.

## Recover a dictation

**Retry** resends retained audio after a timeout. **Insert** retries a retained transcript without uploading again; **Copy transcript** provides manual recovery. Changing fields prevents automatic insertion into the new field. Closing the keyboard discards the current dictation. View recreation preserves pending work; **Cancel/Discard** explicitly deletes it.

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

### Dictating terminal commands

With the Portable ASR backend, start a recording with `comando`: say `comando
slash new` for `/new`, or `comando deletreo eme ka de i erre` for `mkdir`.
Use `fin deletreo` before continuing after spelled letters, and `espacio` for an
explicit space. Command mode applies only to that recording; it never sends Enter.
The backend marks exact command responses with `verbatim: true`, so the keyboard
omits its optional trailing space. See the [command dictation guide](https://github.com/ImAngelParraga/portable-asr/blob/main/docs/dictation.md#terminal-command-dictation)
for symbols, spelling, case and more examples. Other backends retain normal dictation.
