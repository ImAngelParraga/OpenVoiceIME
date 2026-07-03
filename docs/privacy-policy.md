---
title: OpenVoiceIME Privacy Policy
permalink: /privacy-policy/
---

# OpenVoiceIME Privacy Policy

Effective date: 2026-07-02

OpenVoiceIME is an Android input method that records speech, sends it to a user-configured speech-to-text provider, and inserts the returned transcript into the active text field.

## Data OpenVoiceIME Processes

OpenVoiceIME may process:

- Microphone audio recorded when you start dictation.
- Transcribed text returned by the selected speech-to-text provider.
- Provider settings such as base URL, model, language, and API token.
- Local response-time metrics used to help diagnose transcription latency.

## How Audio And Transcripts Are Used

Audio is recorded on your device only when you start a dictation session. The recording is sent to the transcription provider you configure, such as OpenAI, Groq, or a compatible self-hosted server.

The selected provider processes the audio and returns transcript text. OpenVoiceIME inserts that text into the currently focused app according to your settings.

OpenVoiceIME does not operate its own backend service for transcription.

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

Remote provider retention is controlled by the provider you choose.

## Contact

For privacy questions, use the contact address listed in the project repository.
