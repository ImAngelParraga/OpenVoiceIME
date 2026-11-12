# Usage and compatibility

## Transcribe received voice messages

This uses your existing provider URL, token, model and transcription language. It runs independently of microphone permission and keyboard activation.

1. In app Settings, select **Transcribe voice messages → Set up message transcription**.
2. Enable **OpenVoiceIME message transcription** in Android Accessibility settings. This separate, optional service needs screen-content access to recognize menu controls. The physical-keyboard shortcut service keeps its existing permissions.
3. In WhatsApp or Telegram, hold **one voice message** to select it. When the message-selection toolbar exposes its Forward/Delete or Share/Delete actions, a themed **Transcribe** pill appears beside the selected message when its accessible bounds are available, or in a centered fallback above the composer. Scrolling invalidates stale message coordinates.
4. Tap **Transcribe**. A temporary snapshot keeps the selected chat visually in place, with a small cancellable indicator near the audio, while the service clicks Share, or More → Share, then selects **Transcribe audio · OpenVoiceIME** in Android’s share chooser. The snapshot is released after the result sheet draws, or on cancellation, timeout, device configuration changes or service interruption. The chat is briefly frozen during the handoff. Its snapshot stays only in memory: it is never saved or sent to the transcription provider. This visually masks the automated menus; it does not bypass them, and system transitions may still briefly appear on some devices. Transcription starts automatically with your configured provider.
5. Read/select the result in the bottom sheet. **Copy** and **Share text** act only when pressed; nothing is automatically inserted or sent to the conversation. **Close** or Android Back returns to the previous screen.

The rounded result sheet uses the keyboard’s day/night palette, wraps short messages and scrolls long ones. It floats over the conversation; it does not insert a transcript into the messenger’s message layout. Rotation retains the current audio/request/result. **Cancel** stops the current attempt while keeping imported audio for **Retry**. Closing the dialog deletes its owned temporary audio. After process death, share the message again; abandoned imports are removed after 24 hours on the next import.

The receiver accepts a single shared content URI, validates the audio container and duration, and limits imports to 25 MiB. Supported containers include OGG/Opus, M4A/MP4, WAV, MP3, FLAC, WebM, AAC and AMR. The configured server must also support the format; audio is uploaded with its detected extension/MIME type, without transcoding. Long messages use the existing bounded transcription deadline, up to 15 minutes.

### Capture setup and privacy

The frozen-chat shortcut requires Android 11 or newer. Android 14+ captures the selected messenger window; Android 11–13 captures the display only while the source chat remains current. The service discards stale captures and releases pixels on completion, cancellation, timeout, configuration change or disconnect. It never starts sharing if capture fails or the source changes.

If capture is unavailable after upgrading, turn **OpenVoiceIME message transcription** off and on in Android Accessibility settings to refresh its screenshot capability. Screens that block screenshots cannot use this presentation. On protected screens or older Android versions, use the message's ordinary Share action; direct shared-audio transcription remains available.

[Direct audio access research](research/direct-audio-access.md) records the tested folder-access route and account-based alternatives. This release does not add folder access or account linking to the production app.

### Compatibility and fallback

Automation recognizes English and Spanish menu labels in WhatsApp (including Business) and official Telegram Android packages. It depends on exposed accessibility controls, is bounded to eight seconds, and stops on unexpected navigation or missing controls. Other interface languages, protected messages, app changes, and vendor share choosers can prevent automation. The floating button may also appear for other selected message types; only valid shared audio can be transcribed. Multiple selection and video messages are not supported.

If the button is missing or the automated flow fails, use the message’s normal **Share → Transcribe audio · OpenVoiceIME** action. There is no need to save the audio to a folder. Disable **OpenVoiceIME message transcription** in Android Accessibility settings to remove the floating shortcut; standard sharing continues to work.

The automated flow and result screen are verified using a synthetic chat fixture and the actual Android share chooser, without reading personal conversations. Compatibility with a particular WhatsApp/Telegram version needs a real-message check on that device.

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

### Bluetooth or USB keyboard shortcut (Android 11+)

1. Enable OpenVoiceIME as an Android keyboard and grant microphone access.
2. Open **OpenVoiceIME → Keyboard setup → Set up keyboard shortcut**.
3. In Android Accessibility settings, enable **OpenVoiceIME keyboard shortcut**. This is optional and requires explicit system approval.
4. Leave **Use on-screen keyboard** off under Android’s physical-keyboard settings.
5. Focus a text field and press **Ctrl+Shift+Space**. The recording panel opens while the physical keyboard stays connected.
6. Press **Ctrl+Shift+Enter** to stop recording (including paused recording), transcribe, insert the result, and hide the panel. OpenVoiceIME stays selected; the shortcut never sends Enter or submits the destination app. It skips confirmation for this dictation without changing saved preferences.
7. If transcription or insertion fails, the panel stays open with the available recovery actions. Press **Ctrl+Shift+Enter** again to retry retained audio or insert the retained transcript. Repeated presses while uploading or verifying insertion do not duplicate work.

The on-screen **Transcribe**, **Cancel**, and keyboard-return controls retain their normal preference behavior.

On Android 16 and newer, the shortcut temporarily asks Android Accessibility to allow the panel with a hardware keyboard connected. Android restores your previous on-screen-keyboard setting when the panel closes, opening times out, or the shortcut service stops. You do not need to leave **Use on-screen keyboard** enabled. If Android drops the normal show request, one guarded attempt to show the bound voice-keyboard window runs after about three seconds. Switching keyboards or moving to a new editor cancels that pending attempt. Opening Settings closes the current shortcut session before navigation; returning after a successful connection test starts fresh dictation. While OpenVoiceIME remains selected, the finish shortcut is consumed even with the panel hidden, so it cannot reach the destination app after a failed opening.

HeliBoard can stay selected between recordings. Holding or repeating the open shortcut does not restart an active recording; unrelated key combinations pass through normally. No screen-content access or typed-text storage is requested. On sideloaded installations, Android may require **Allow restricted settings** in the app’s system information menu before it lets you enable the service. Other accessibility services that filter keys or system-reserved shortcuts may prevent delivery. OnePlus Pad 3 / Android 16 and Logitech MX Keys Mini require device-specific verification.

### A panel that flashes or fails to open

Try the shortcut once, then open **OpenVoiceIME → Keyboard setup → Copy shortcut diagnostics** and paste that report when requesting help. This works without USB or a computer. The local report contains up to 80 shortcut/panel events, app/Android versions, device model and keyboard configuration. It excludes typed text, audio, tokens and server addresses; nothing is copied until you tap the button.

The voice panel stays non-fullscreen in both orientations. That fixes Android’s landscape extraction UI; a vendor-specific opening/dismissal problem still needs its own event trace before it can be diagnosed.

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

Opening the notification or Quick Settings panel keeps the current recording running. Closing that panel returns to the same recording; a paused recording stays paused. This covers temporary system panels while the keyboard remains open, rather than recording after leaving the app or dismissing the keyboard.

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
