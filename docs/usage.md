# Usage and compatibility

## Choose how to open dictation

In OpenVoiceIME, open **Keyboard setup** at the top of Settings. Choose **Use HeliBoard** or **Keep my current keyboard**. The guide remembers your choice, opens official downloads and available keyboard settings, and provides a field for trying dictation. It never changes Android's enabled or default keyboards automatically.

OpenVoiceIME is a separate voice input method, not a full typing keyboard and not a plug-in for Gboard's microphone. Enable it in Android's **On-screen keyboard / Manage keyboards** settings after configuring your transcription endpoint and granting microphone access. Keep a normal typing keyboard enabled.

### Keep Gboard or your existing keyboard

1. Open a text field so the keyboard is visible.
2. Open Android's keyboard picker, usually through the keyboard icon in the navigation area or the **Change keyboard** system notification. The location varies by phone.
3. Select **OpenVoiceIME**, dictate, and tap **Transcribe**.
4. Use OpenVoiceIME's **Return to normal keyboard** button, or the system picker, to resume typing. **Return to keyboard after insert** is enabled by default; with several keyboards enabled, Android's next-keyboard order can select a different keyboard.

This route needs no replacement typing keyboard **if your device lists OpenVoiceIME**. It currently declares an auxiliary voice subtype, which some pickers hide. If it is enabled but missing from the picker, use a keyboard with a voice-input shortcut such as HeliBoard. Manual switching and Gboard behavior still need device-specific testing; the HeliBoard microphone route has been reported working by the maintainer.

Gboard's microphone uses Google's own voice typing; there is no supported setting for redirecting it to OpenVoiceIME. Changing the system speech-recognition engine is a different mechanism: OpenVoiceIME currently provides a voice keyboard, not a `RecognitionService`. See [Gboard's voice-typing instructions](https://support.google.com/gboard/answer/2781851?hl=en) and the [FUTO Voice Input project's keyboard compatibility notes](https://github.com/futo-org/voice-input#keyboard-support), which document the same third-party voice-keyboard limitation.

### HeliBoard microphone shortcut

1. Download HeliBoard's APK from its [official GitHub Releases](https://github.com/HeliBorg/HeliBoard/releases/latest). Allow that browser/file manager to install it when Android asks. **F-Droid is optional**; no additional store is required. Updates from a direct APK are manual; keep using the same distribution source for compatible updates.
2. Enable HeliBoard and use it as your normal typing keyboard. Keep OpenVoiceIME enabled as well.
3. In **HeliBoard Settings → Toolbar → Select pinned toolbar keys**, enable **Voice input**. Menu names vary by version and language.
4. Open a text field and tap HeliBoard's microphone. It uses Android's enabled voice-input shortcut to open the voice keyboard.
5. If it opens Google Voice Typing or another voice keyboard, check Android's **Manage keyboards** settings. Select OpenVoiceIME if your phone offers a voice-keyboard choice; otherwise disable competing **voice input methods** so OpenVoiceIME is the only enabled voice option. Keep HeliBoard enabled. The separate Google Voice Typing switch is different from Gboard's normal typing keyboard; some phones do not expose it separately.
6. Dictate and tap **Transcribe**. Use **Return to normal keyboard** if needed. To make the return path predictable, keep only your intended typing keyboard and OpenVoiceIME enabled during initial setup.

HeliBoard itself describes both [direct APK installation](https://github.com/HeliBorg/HeliBoard/wiki#installation) and [toolbar customization](https://github.com/HeliBorg/HeliBoard/wiki/5.-Toolbar). Its [voice shortcut implementation](https://github.com/HeliBorg/HeliBoard/blob/main/app/src/main/java/helium314/keyboard/latin/RichInputMethodManager.kt) selects from Android's voice-IME shortcuts; it does not configure OpenVoiceIME's server or models. Installing HeliBoard does not replace the endpoint/token setup in OpenVoiceIME.

## Recording controls

Opening OpenVoiceIME starts recording automatically. **Pause** suspends capture without sending audio; **Resume** continues the same file. Paused time is excluded from the recording timer and submitted audio. **Transcribe** sends the combined recording from either state.

**Delete** removes selected editor text or the preceding character; sliding left selects more text before release. It does not discard or pause recording. **Cancel/Discard** clears the current audio, pending request, and transcript without deleting editor text. Disable **Hide after cancel** to immediately start fresh while keeping the panel open.

The toolbar opens settings, changes transcription language, or returns directly to a typing keyboard. The keyboard follows the system light/dark appearance.

To keep OpenVoiceIME open after insertion, disable both **Return to keyboard after insert** and **Hide after insert**. It starts the next recording automatically. Enabling return switches keyboards; enabling hide closes the panel when return is disabled. These saved preferences are unchanged by upgrades.

## How it works

1. The OpenVoiceIME keyboard starts a local microphone recording.
2. The recording is sent as multipart audio to `{baseUrl}/v1/audio/transcriptions`.
3. The configured provider returns a JSON response with a `text` field.
4. OpenVoiceIME commits the text into the original input field and checks whether the editor accepted it.

Each transcription has a total timeout of 5 seconds plus the recording duration, with a minimum of 10 seconds and a maximum of 15 minutes. A 3-second recording gets 10 seconds, a 10-second recording gets 15 seconds, and a one-minute recording gets 65 seconds. The remaining wait appears in the keyboard. Timeout keeps the recording for **Retry**; Cancel stops the HTTP request.

If insertion fails, OpenVoiceIME keeps the transcript and recording, offers **Insert** to retry insertion without another server request, and provides **Copy transcript**. It does not start another recording. When readable editor text stays unchanged after an accepted request, check the field before retrying to avoid duplicates. Editors without readable text can only be checked through their InputConnection acceptance signal. Changing input fields blocks automatic insertion and leaves the transcript available to copy or explicitly **Insert here**. Existing composing text is finished before dictation; truncated or filtered insertion keeps the full transcript for recovery.

By default, OpenVoiceIME uses `https://api.openai.com` with `gpt-4o-transcribe`. Groq and custom OpenAI-compatible servers can be selected in settings.

Closing the keyboard discards the current dictation and cancels requests. View recreation preserves pending uploads/transcripts; a paused recording remains resumable. Rebuilding an actively recording view finalizes usable audio for **Send audio**. Service destruction clears owned temporary work, and a later service start removes abandoned recording files left by process death.

## Transcription compatibility

OpenVoiceIME currently has one OpenAI-compatible transcription client. Providers with different upload flows, authentication schemes, or response formats need separate adapters.

Settings fetches the authenticated `GET {baseUrl}/v1/audio/models` catalog when opened or when the provider URL or token changes. **Refresh models** fetches it again. Servers without this endpoint keep the built-in preset choices or the editable custom model field. New IDs advertised by the server appear without an app update.

Terminal compatibility: editors that expose `TYPE_NULL` or no extracted text (including terminal-style apps) use bounded Delete key events. Rich text editors retain code-point-safe selection previews for Delete gestures.

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
