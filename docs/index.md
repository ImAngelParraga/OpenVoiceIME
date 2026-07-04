---
title: OpenVoiceIME
permalink: /
---

<p align="center">
  <img src="logo.svg" alt="OpenVoiceIME logo" width="128" height="128">
</p>

# OpenVoiceIME

Android voice input keyboard for OpenAI-compatible speech-to-text transcription.

[Download APK](https://github.com/ImAngelParraga/OpenVoiceIME/releases/latest)
[View Source](https://github.com/ImAngelParraga/OpenVoiceIME)

OpenVoiceIME records speech from your Android device, sends the audio to the transcription provider you configure, and inserts the returned text into the current text field.

## Why Use It

- Use OpenAI, Groq, or a self-hosted OpenAI-compatible transcription endpoint.
- Keep provider choice, model, language, and prompt settings configurable.
- Use it from any app that accepts normal keyboard input.
- Avoid a developer-operated transcription backend.

## Setup

1. Download `OpenVoiceIME-release.apk` from the latest GitHub release.
2. Install the APK on your Android device.
3. Open **OpenVoiceIME** from the launcher.
4. Grant microphone permission.
5. Choose a provider preset or configure a custom OpenAI-compatible server.
6. Enter your API token, model, and language settings.
7. Test the server connection.
8. Enable **OpenVoiceIME** in Android keyboard settings.
9. Switch to OpenVoiceIME when you want to dictate.

## Downloads

- [Latest release](https://github.com/ImAngelParraga/OpenVoiceIME/releases/latest)
- [Current APK direct download](https://github.com/ImAngelParraga/OpenVoiceIME/releases/download/v0.2.5/OpenVoiceIME-release.apk)
- [Release notes](https://github.com/ImAngelParraga/OpenVoiceIME/releases/tag/v0.2.5)

Android may ask you to allow installation from your browser or file manager before installing a downloaded APK.

## Provider Compatibility

| Provider | Status | Notes |
| --- | --- | --- |
| OpenAI | Supported | Default preset using `https://api.openai.com` and `gpt-4o-transcribe`. |
| Groq | Supported | Built-in OpenAI-compatible preset. |
| Custom OpenAI-compatible endpoint | Supported | Configure base URL, model, token, language, and prompt. |
| Deepgram, AssemblyAI, Google Speech-to-Text, Azure Speech | Not built in | These need provider-specific adapters. |

## Privacy

OpenVoiceIME records audio on device, but transcription happens on the provider endpoint you configure.

- Audio is sent only to the configured transcription endpoint.
- OpenVoiceIME does not operate a transcription backend.
- API tokens are stored locally using encrypted app storage.
- Release builds block cleartext HTTP; use HTTPS endpoints for normal use.
- Transcript text and API tokens should not be logged by the app.

Read the full [privacy policy](privacy-policy/).

## Project Links

- [Source code](https://github.com/ImAngelParraga/OpenVoiceIME)
- [Latest release](https://github.com/ImAngelParraga/OpenVoiceIME/releases/latest)
- [Privacy policy](privacy-policy/)
- [License](https://github.com/ImAngelParraga/OpenVoiceIME/blob/main/LICENSE)
