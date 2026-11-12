---
title: OpenVoiceIME Privacy Policy
permalink: /privacy-policy/
---

# OpenVoiceIME Privacy Policy

Effective date: 2026-11-12

OpenVoiceIME is an Android input method that records speech, sends it to a user-configured speech-to-text provider, and inserts the returned transcript into the active text field.

## Data OpenVoiceIME Processes

OpenVoiceIME may process:

- Microphone audio recorded when you start dictation.
- Audio files you explicitly share for transcription from another app.
- Transcribed text returned by the selected speech-to-text provider.
- Provider settings such as base URL, model, language, and API token.
- Local response-time metrics used to help diagnose transcription latency.
- Up to 80 local shortcut/panel events with lifecycle flags and app/device metadata for troubleshooting.

## How Audio And Transcripts Are Used

Audio is recorded on your device only when you start a dictation session. The recording is sent to the transcription provider you configure, such as OpenAI, Groq, or a compatible self-hosted server.

The selected provider processes the audio and returns transcript text. OpenVoiceIME inserts that text into the currently focused app according to your settings.

OpenVoiceIME does not operate its own backend service for transcription.

## Shared Voice Messages And Optional Floating Shortcut

When you share an audio message to **Transcribe audio · OpenVoiceIME**, the app temporarily copies and validates that audio in its private cache, sends it to your configured provider, and displays the returned transcript. It does not automatically insert or send text to the conversation. Copying or sharing the transcript requires your explicit action. This feature does not record the microphone.

The separate **OpenVoiceIME message transcription** Accessibility service is optional. If you enable it, it inspects the active interface to recognize message-selection and sharing controls in supported messengers and the Android share chooser. Only pressing its floating **Transcribe** button starts menu automation. A full-screen progress cover masks the automated Share menus. This service does not request screenshot capability or capture chat images. Only the explicitly shared audio is uploaded. The chat is briefly non-interactive during this handoff. The selected audio is sent through the same explicit sharing mechanism. Disable this service in Android Accessibility settings at any time.

Shared audio remains private while the result screen is open, including cancellation/retry. Closing the screen deletes its owned audio; after process death, the next import deletes abandoned files older than 24 hours. Pending shared transcripts remain in memory and are not saved as a transcript history.

## Optional Physical Keyboard Shortcut

If you explicitly enable the keyboard shortcut Accessibility service, Android delivers key events so OpenVoiceIME can recognize Ctrl+Shift+Space to open dictation while another keyboard is selected, and Ctrl+Shift+Enter to finish dictation and hide its panel. Other key combinations pass through. The service does not request screen-content access, read screen text, store typed text, or send key events to a server. You can disable it in Android Accessibility settings at any time. A bounded local diagnostic history records only open/finish key candidates with command modifiers and keyboard-panel lifecycle flags, plus app/Android version, device model and keyboard configuration. It excludes editor contents, audio, tokens and server addresses. **Copy shortcut diagnostics** copies a report only on your explicit action; the app does not upload these reports.

## API Tokens And Provider Settings

API tokens are stored locally on your device using encrypted app storage. Provider settings are stored locally so the app can reuse your selected transcription configuration.

OpenVoiceIME does not intentionally log API tokens or transcript text.

## Third-Party Providers

When you use a third-party transcription provider, that provider may receive and process your audio, transcript text, API token, and request metadata. Their processing is governed by their own terms and privacy policy.

Use HTTPS provider endpoints for production use. Debug builds may allow cleartext HTTP for local development.

## Data Sharing

OpenVoiceIME does not sell user data and does not include advertising or analytics SDKs.

OpenVoiceIME sends audio and authentication data only to the transcription provider endpoint you configure.

## Data Retention And Deletion

OpenVoiceIME stores settings and local metrics on your device. You can delete local data by clearing the app's storage in Android settings or uninstalling the app.

Temporary recordings may remain in app-private storage while a dictation is pending for retry. Cancel/discard, successful insertion, or service destruction removes the owned recording; the next service start removes abandoned recordings after process death. Pending transcripts remain in memory while the service lives.

Remote provider retention is controlled by the provider you choose.

## Contact

For privacy questions, open a [GitHub issue](https://github.com/ImAngelParraga/OpenVoiceIME/issues). Do not include tokens, recordings, or private transcripts.
