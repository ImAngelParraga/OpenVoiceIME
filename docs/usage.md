# Usage and compatibility

## How it works

1. The OpenVoiceIME keyboard starts a local microphone recording.
2. The recording is sent as multipart audio to `{baseUrl}/v1/audio/transcriptions`.
3. The configured provider returns a JSON response with a `text` field.
4. OpenVoiceIME commits the text into the original input field and checks whether the editor accepted it.

Each transcription has a total timeout of 5 seconds plus the recording duration, with a minimum of 10 seconds and a maximum of 15 minutes. A 3-second recording gets 10 seconds, a 10-second recording gets 15 seconds, and a one-minute recording gets 65 seconds. The remaining wait appears in the keyboard. Timeout keeps the recording for **Retry**; Cancel stops the HTTP request.

If insertion fails, OpenVoiceIME keeps the transcript and recording, offers **Insert** to retry insertion without another server request, and provides **Copy transcript**. It does not start another recording. When readable editor text stays unchanged after an accepted request, check the field before retrying to avoid duplicates. Editors without readable text can only be checked through their InputConnection acceptance signal. Changing input fields blocks automatic insertion and leaves the transcript available to copy or explicitly **Insert here**. Existing composing text is finished before dictation; truncated or filtered insertion keeps the full transcript for recovery.

By default, OpenVoiceIME uses `https://api.openai.com` with `gpt-4o-transcribe`. Groq and custom OpenAI-compatible servers can be selected in settings.

Hiding the keyboard stops capture and preserves usable audio for **Send audio** when reopened. It cancels an upload without discarding the recording; pending transcripts survive hiding or view recreation. **Cancel/Discard** still deletes the current dictation. Service destruction clears owned temporary work, and a later service start removes abandoned recording files left by process death.

## Transcription compatibility

OpenVoiceIME currently has one OpenAI-compatible transcription client. Providers with different upload flows, authentication schemes, or response formats need separate adapters.

Settings fetches the authenticated `GET {baseUrl}/v1/audio/models` catalog when opened or when the provider URL or token changes. **Refresh models** fetches it again. Servers without this endpoint keep the built-in preset choices or the editable custom model field. New IDs advertised by the server appear without an app update.

Terminal compatibility: editors that expose `TYPE_NULL` or no extracted text (including terminal-style apps) use arrow and Delete key events for the compact cursor/Delete controls. Cursor pad supports left/right/up/down movement; rich text editors retain character-precise horizontal previews.

| Service | Status | Base URL | Default model |
| --- | --- | --- | --- |
| OpenAI | Supported default | `https://api.openai.com` | `gpt-4o-transcribe` |
| Groq | Supported | `https://api.groq.com/openai` | `whisper-large-v3-turbo` |
| Custom OpenAI-compatible server | Supported as saved custom preset | User-provided | User-provided |
| Deepgram | Needs adapter | Different endpoint/auth/response | N/A |
| AssemblyAI | Needs adapter | Upload + async polling flow | N/A |
| Google Speech-to-Text | Needs adapter | Google recognizer JSON/OAuth flow | N/A |
| Azure Speech | Needs adapter | Azure speech REST path/auth/body | N/A |

Expected compatible request shape:

```http
POST {baseUrl}/v1/audio/transcriptions
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

Multipart fields:

- `file`: recorded audio
- `model`: configured transcription model
- `language`: optional language code
- `temperature`: `0.0`

Expected success response:

```json
{
  "text": "Transcribed text"
}
```
