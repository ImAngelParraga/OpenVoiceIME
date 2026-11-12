# Direct access to received audio

Checked 2026-11-12. **A real incoming WhatsApp notification supplied an exact audio URI that an ordinary notification-listener app read without Share.** Access expired after notification dismissal; a temporary memory buffer preserved the exact bytes. The released shortcut still uses Android sharing, and matching an arbitrary selected native chat row to a cached note remains unverified. No account integration below is enabled.

## What the storage experiment establishes

An opt-in Android 16 emulator test created a real OGG/Opus file in another installed app's `Android/media` directory, alongside `.nomedia`. A provider in the instrumentation APK created the fixture under a different UID from the client. The client queried only that synthetic directory and compared the returned bytes with the known fixture; no personal messages or media were inspected.

| Access route | Observed result |
|---|---|
| Client reads the file path without storage permission | Denied |
| Same read after `READ_MEDIA_AUDIO` approval | Denied |
| Exact-folder query through `MediaStore.Audio` | No indexed rows |
| User-approved Storage Access Framework tree, then `openInputStream` | Exact fixture bytes read successfully |

This proves that one-time scoped folder access can enable later direct reads on this Android configuration. It does **not** establish which folders a particular WhatsApp build populates, vendor picker behavior, or the identity of a selected voice message. Android documents [persistent folder grants](https://developer.android.com/training/data-storage/shared/documents-files) and the [.nomedia scanner exclusion](https://developer.android.com/reference/android/provider/MediaStore#MEDIA_IGNORE_FILENAME).

### Reproduce on an owned emulator

Build/install both debug APKs, then run:

```bash
adb shell pm revoke dev.rankis.openime.testbed android.permission.READ_MEDIA_AUDIO
adb shell am instrument -w -r -e runDirectAudioProbe true \
  -e class dev.rankis.openime.DirectAudioAccessProbeTest \
  dev.rankis.openime.testbed.test/androidx.test.runner.AndroidJUnitRunner
adb shell pm revoke dev.rankis.openime.testbed android.permission.READ_MEDIA_AUDIO
```

The probe grants audio permission only on an emulator. Revoke it from the host after the runner exits because runtime revocation kills the target process. The test releases its folder URI and deletes its own fixture. Production declares no storage permission. The probe is skipped in the ordinary regression suite.

## Native WhatsApp selection: real-device check

On 2026-11-12, a user-prepared test voice note was inspected on a Xiaomi 2412DPC0AG running Android 16 and WhatsApp **2.26.43.76**. Testing finished within the supplied ten-minute device window. No audio was played or sent to transcription, no account was paired, and the installed production app was unchanged.

The phone rejected the isolated debug APK installation. Inspection therefore used a temporary Java UI Automator probe through ADB, reading the selected WhatsApp subtree directly. This is diagnostic shell automation, not a deployable app integration or proof of ordinary-app file permission. Only structural metadata was retained: resource IDs, selection flags, unique-ID presence, extras keys and types, and whether text/description contained a file reference. Message text, sender names and descriptions were not retained in the report. Temporary UI dumps and the local test-audio copy were deleted; the device-side probe and reports were removed before releasing the phone.

| Check | Observed result | Consequence |
|---|---|---|
| Selected voice-note row | `com.whatsapp:id/conversation_row_voice_note`, selected, with 19 descendants | The exact on-screen row can be located. |
| `AccessibilityNodeInfo.getUniqueId()` | Null on all 20 inspected nodes | No message key through this field in this build. Android permits [null IDs](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#getUniqueId()). |
| Node extras | Empty on all 20 nodes | No extra URI, filename or message key was exposed. |
| Text/content descriptions | No `PTT-`, `.opus`, `content://` or `/storage/` reference in the selected subtree | Visible metadata does not supply a file reference. |
| Waveform control | `audio_visualizer`, exposed as a SeekBar with no text, description or extras | The waveform bytes are not supplied by that accessibility node. |
| Controlled Share, stopped at system chooser | A `PTT-YYYYMMDD-WAnnnn.opus` filename and `content://com.whatsapp.provider.media/item/<uuid>` grant appeared | Sharing provides the missing reference, but this does not avoid Share. No receiving/transcription app was selected. |
| Exact known-filename search in the voice-note folder | One matching local file | The shared filename can locate this file. The name was learned only after Share. |
| Query of that media URI from the shell | Permission denied, even while the chooser held its grant | A chooser's grant does not make the URI generally readable. |
| Test Opus metadata | Duration 3.9465 seconds; no format or stream tags reported by `ffprobe` | This file had no tag carrying the message/file association. |

These observations rule out those exposed fields **for this message and WhatsApp build**. They do not establish that every version, message type or hidden provider behaves identically.

As a separate static cross-check, the APK obtained from the [official Android download](https://www.whatsapp.com/download/android) reported version **2.26.45.70** (SHA-256 `d06c94313ebbf34b6d925fa51be371442e4dae536b27f7e457511f3c32eea0b7`). Its manifest declares `com.whatsapp.provider.media` non-exported with URI grants enabled. This APK was inspected locally and was not installed; its version differs from the tested phone. That provider is not a public attachment catalogue.

### Matching approaches after this check

| Approach | Assessment | Next useful experiment |
|---|---|---|
| Native row ID / extras → exact file | No bridge found on the tested build | Recheck structural fields only when a materially different WhatsApp build exposes additional data. A view resource ID identifies a control type, not an attachment. |
| Date, displayed time, duration, file ordering | Candidate filters; no verified identity guarantee | Use them to narrow a folder-access candidate set, never as evidence that the only remaining file must be correct. Missing files and delayed/concurrent downloads invalidate that inference. |
| Visual waveform + duration → candidate file | Worth testing as a similarity-based route, not yet validated | Compare screenshots of synthetic native voice notes against decoded candidate envelopes, including same-duration notes, silence, forwarded notes and deliberately missing targets. Require abstention/fallback for ambiguous cases. |
| A selected note triggers one new download | Potential special case for not-yet-downloaded media | Measure an explicitly triggered download alongside concurrent downloads, temporary-file renames and folder access. Already-downloaded messages still need another route. No downloads were triggered in this session. |
| Notification supplies an audio URI | Confirmed for a fresh incoming voice note on the tested Android 16/WhatsApp build. | Capture locally while the notification grant is valid; the URI stopped being readable after dismissal. Arbitrary older selections and arrivals that create no notification still need another route. |
| Linked-device message object → file hash/media | Stronger content identity, still needs selection mapping | A separately authorized client can obtain media metadata, but it needs its own message picker or a demonstrated bridge from the native selection. |

The waveform limitation follows from primary implementation evidence: Baileys [computes a 64-value normalized amplitude envelope](https://github.com/WhiskeySockets/Baileys/blob/81345beb67ca3fb0eb79a2c20cd4ac24da517929/src/Utils/messages-media.ts), while its [message preparation accepts an explicitly supplied waveform](https://github.com/WhiskeySockets/Baileys/blob/81345beb67ca3fb0eb79a2c20cd4ac24da517929/src/Utils/messages.ts). Its [protocol schema keeps waveform and file SHA-256 in separate fields](https://github.com/WhiskeySockets/Baileys/blob/81345beb67ca3fb0eb79a2c20cd4ac24da517929/WAProto/WAProto.proto). **Inference:** matching displayed bars may narrow candidates, but cannot be treated as a verified file hash, and the native Android renderer/algorithm must be measured rather than assumed to match Baileys.

Android provides both [`MessagingStyle.Message.getDataUri()`](https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message#getDataUri()) and [`EXTRA_AUDIO_CONTENTS_URI`](https://developer.android.com/reference/android/app/Notification#EXTRA_AUDIO_CONTENTS_URI). The live experiment below confirms that WhatsApp populated per-message MIME/URI fields and an authorized listener could read the attachment on this configuration. The separate top-level audio-contents key was absent. This does not establish identical behavior on every version.

The waveform follow-up below now supplies native-note comparisons, missing-target checks and a constructed false acceptance. It has not established a reliable automatic replacement for sharing. A separately authorized message picker provides a clearer identity boundary, while the bridge from native long-press selection remains unproven. No direct-file matching feature was added by this investigation.

## Follow-up: matching visible waveforms to actual files

A second session used the same Android 16 phone and WhatsApp 2.26.43.76. The user authorized test messages only in their self-chat and supplied two new non-private spoken notes plus one silence note. No transcription requests were sent. A generated four-second Opus attachment had no native waveform, so it was excluded from native-waveform validation. The phone killed the temporary tone player; that attempted automated recording was also excluded. Test messages remain in the self-chat; temporary probe files, exported crops and locally copied recordings were removed after analysis. The original music volume was restored. No production app change or notification-access grant was made.

### Procedure and limits

Only each waveform rectangle was exported, not a screenshot of the chat. Each non-flat crop contained 39 visible bars. Ground truth came from a controlled Share stopped at Android's chooser: its filename located the exact file for local decoding. Thus **the experiment tests matching; it does not itself demonstrate a Share-free app integration**. File reads used ADB, not an ordinary app's folder grant on this phone.

The first prepared note was used for calibration. A simple 64-bin decoded-audio envelope plus interpolation scored the two new spoken notes at 0.7200 and 0.5009; the second could rank below a wrong synthetic candidate. Inspection of the installed APK then identified a rendering difference: the bar view averages groups of waveform values when reducing their count, and the separate playback thumb obscures the first visible interval. The corrected model used 64 bins of decoded 48-kHz mono PCM, square-root RMS features, grouped reduction to 40 bars, and omission of the first covered bar before Pearson correlation. These changes improve sampling alignment; they do not replicate the waveform-generation pipeline completely.

The installed APK inspected locally has SHA-256 `a1b3b262a86c8ed928bce8b777da69a2324607356bd03d814396a66bbc27da55`. Relevant code paths include `VoiceVisualizer`, `VoiceNoteSeekBar`, and obfuscated `X.9kG` (bar resizing), `X.SWO` (separate visualization-file output) and `X.AEd` (byte-valued visualization-file reads). Decompiled code was used for local inspection only and is not included here. Some unrelated methods could not be decompiled; the findings above concern readable methods, not a complete reconstruction of the app.

After this correction, each spoken note was compared against 1,004 same-duration candidates: its correct file, three other prepared notes repeated/cut to that exact sample count, and 1,000 synthetic step-envelope tone signals. These are controlled distractors, **not 1,004 independently collected WhatsApp messages**. A hypothetical conservative rule required a score of at least 0.90 and a lead of at least 0.15 over the runner-up; it never uploaded anything.

| Note | Decoded duration | Correct-file score | Strongest wrong candidate | Rank of correct file | Conservative decision |
|---|---:|---:|---:|---:|---|
| Original calibration note | 3.933 s | 0.9341 | 0.6069 | 1 | Accept |
| New spoken note A | 9.934 s | 0.8586 | 0.4797 | 1 | Abstain |
| New spoken note B | 11.954 s | 0.7234 | 0.5004 | 1 | Abstain |
| New silence note | 6.913 s | No discriminating bars | — | — | Abstain |

Removing the correct file also produced abstention for all three spoken examples under that rule. Lowering the cutoff would accept more of these examples, but the small corpus cannot establish a safe cutoff or real-world false-match rate. The new notes were collected after initial calibration, but the resampling correction was developed after their first scores were observed; these results are model development, not final unseen validation. The small self-chat corpus does not establish accuracy for incoming notes from other senders, iOS, forwarded messages, different sizes/themes or other WhatsApp versions.

### A constructed failure even with conservative thresholds

An additional local control used the *other* prepared speech note, repeated/cut to the target length and amplitude-shaped per frame to resemble the displayed bars. It was Opus-encoded and decoded again before scoring. Its score against spoken note B's on-screen waveform was **0.9985**, versus **0.7234** for the real file. Its lead over the real file was **0.2751**, so the conservative rule would accept the wrong recording even with the real file present.

This was a deliberately constructed counterexample, not an accidental collision observed in normal chats and not an estimate of real-world risk. It demonstrates that this scorer measures amplitude shape rather than attachment identity. A larger score alone cannot establish that a selected note belongs to a particular file; silence and missing/modified files also need explicit fallback behavior.

### Inspection fixes learned during the session

- The hierarchy can expose both chat and selection-toolbar controls with the same resource ID. Select the message-toolbar ancestor explicitly and verify the expected conversation before each action.
- An unconditional Back left the test conversation and selected a chat-list row. The error was caught before sharing that selection. Subsequent pairing required the authorized conversation header, a voice-note row and exactly one selected message; selection was dismissed through its close control only when present.
- The active accessibility root later became null although the app remained visible. With interactive-window retrieval enabled, the focused window still supplied the correct WhatsApp tree. A successful final continuous probe paired all three new notes with their files. This is diagnostic evidence; no focused-window fallback was added to production.
- Run device UI operations sequentially and validate actual test status. A process exit code of zero did not reliably indicate a passing legacy UI Automator test.

The static notification-code check found a standard `MessagingStyle.Message.setData` forwarding wrapper (`X.K1c`, called from `X.K8S`). The later real incoming-notification experiment below established readable attachment access; static wrapper presence alone had not proved that behavior.

**Outcome:** waveform matching is useful for ranking candidates in this small experiment, but no reliable automatic replacement for Share was established. The next useful work would be a substantially larger independent corpus and a better waveform-generation model, or an exact identifier supplied by an explicitly authorized client integration. Neither a lower threshold nor a folder grant alone resolves message identity.

## Notification-URI experiment: confirmed direct retrieval

A fresh non-private voice note arrived while WhatsApp was in the background on the Android 16 phone running WhatsApp **2.26.43.76**. A temporary ordinary app with [`NotificationListenerService`](https://developer.android.com/reference/android/service/notification/NotificationListenerService) access received the notification and read its attachment through `ContentResolver.openInputStream`. **No Share menu, audio playback, folder grant, storage permission, account pairing or transcription request was involved.** The diagnostic APK declared no Internet permission.

| Check | Observed result |
|---|---|
| Real conversation notification | `MessagingStyle`, with per-message MIME `audio/ogg; codecs=opus` and a `content://com.whatsapp.provider.media/item/<uuid>` URI. |
| Group-summary notification | No per-message audio reference; inspecting only this record would miss the attachment. |
| Top-level `EXTRA_AUDIO_CONTENTS_URI` | Absent. Its absence did not mean there was no audio URI. |
| Ordinary listener opens the URI | **21,150 bytes read**, with an Ogg header; SHA-256 matched across repeated reads. |
| Notification refreshed during testing | The opaque URI changed; recapturing the scoped test message still returned byte-identical audio. |
| After dismissing the exact test notification | `NotificationRemoved` was observed. New opens failed with `SecurityException` at both **2 seconds and 10 seconds**. |
| Temporary in-memory copy after dismissal | Still 21,150 bytes with the same SHA-256 at both checks; cleared after the final check. |

The lifetime test used the listener's targeted `cancelNotification` API. It did **not** establish the exact timing for chat opening: the attempted forwarding of the notification's PendingIntent did not open the chat. No other notification was dismissed, and the test audio was not played. The result demonstrates access revocation after this removal path, not permanent availability or identical behavior for every removal reason.

The notification also contained a top-level `last_row_id` value of type `Long`. Its per-audio semantics and any bridge to a selected native accessibility row were not established. It must not be assumed to solve native-message identity, especially when a notification contains multiple messages. For the diagnostic read, a known notification-key hash plus the test message timestamp limited capture to the supplied fixture; byte hashes verified that refreshed URI reads still represented the same audio. This is not a claim that that tuple is a universal message identifier.

### Test validity and cleanup

The extractor first passed unposted synthetic MIME/URI round-trip controls on **Android 13 and Android 16**, using the public `Notification.Builder.recoverBuilder(...).getStyle()` API. These controls verified metadata decoding; the real listener read above separately verified provider access. A direct shell API call requiring `ACCESS_NOTIFICATIONS` was rejected, and a shell `content read` was denied even though its process exit code was zero. No signature permission was granted or bypassed.

Initial phone installations returned `INSTALL_FAILED_USER_RESTRICTED`; an ordinary retry succeeded after the owner enabled USB installation. A temporary listener then collected structural metadata, not message text or sender names. Other WhatsApp notifications arrived during the session, so the attachment-reading step was restricted to the supplied test message before the removal experiment.

A diagnostic parsing trap initially hid the real conversation record: its notification key contained an embedded newline in an opaque tag. Splitting `cmd notification list` into individual lines truncated that key, so `get` failed for the conversation while succeeding for the summary. Preserving the complete key and quoting it correctly exposed the actual per-message MIME and URI. Production listener callbacks already supply structured notification objects and should not parse shell output.

The temporary app was uninstalled, its notification access revoked, its memory buffer cleared, and transient URI/identity files removed. The installed OpenVoiceIME app was unchanged. The test note remains in WhatsApp, unplayed; its test notification was dismissed deliberately. No production direct-reading feature was published by this research.

### What this enables

For notes that generate a notification, an opt-in implementation can capture the exact attachment into a bounded private cache while its grant is valid, then submit it to transcription only after an explicit user action. URI strings alone are insufficient: they can change and their read grants can expire. Cache retention, size limits, cleanup and duplicate notification updates need explicit handling.

A per-note **Transcribe action in our own notification** can address the captured clip directly without matching a native chat row. The original long-press shortcut still needs a demonstrated join from the selected WhatsApp row to that cached clip. Older notes, notifications missed before capture, and arrivals that produce no notification still need the existing Share fallback or a different integration. One successful phone/build/voice-note test does not establish universal compatibility.

## Why folder access is not yet an exact-message shortcut

Accessibility supplies interface nodes, labels and coordinates; it has no documented general contract for obtaining a messenger's selected-message media URI. A folder grant supplies files, not the messenger's message-to-file index. Matching duration, visible time, filename date or modification order can produce collisions or select a different download. A filename or URI learned through a previous explicit Share can identify that prior file, but does not solve a new unshared message.

A safe folder prototype would need either a demonstrated stable message/file identifier or explicit user confirmation when matching is ambiguous. A clearly named “latest downloaded audio” feature would be a different action from “this selected message.” No heuristic auto-upload was added.

## Account-based routes that really avoid Share

| Route | Technical basis | What remains |
|---|---|---|
| WhatsApp linked-device companion | [Baileys](https://github.com/WhiskeySockets/Baileys) documents QR/pairing-code login, incoming message events and media download/decryption. This provides media from message objects without driving phone menus. | Unofficial integration, session storage and maintenance. Requires explicit account pairing and a reliable link between the user's requested message and its message ID, or a separate message picker. No personal account was linked. |
| Telegram client integration | Official TDLib exposes [messageVoiceNote](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1message_voice_note.html), [chat history](https://core.telegram.org/tdlib/getting-started) and [downloadFile](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1download_file.html). | An authorized Telegram client session and a message picker or proven native-UI-to-message-ID bridge. No account was authorized. |
| Telegram's existing file provider | The [official Android manifest](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/AndroidManifest.xml) declares its main FileProvider non-exported with temporary URI grants. | It is not an unrestricted voice-note catalogue; an authorized URI grant is still needed. |

The [WhatsApp Business platform](https://whatsappbusiness.com/developers/developer-hub/) exposes business messaging integration. It is not a documented way for this keyboard app to retrieve a selected message from an existing personal chat.

For incoming notes with accessible notifications, listener capture is now the demonstrated route with no account pairing or folder grant. An account-based companion remains a separate direction for independent message access and history. Neither route has proved an exact join from an arbitrary native long-press selection to its message/file identity, and neither requires sending transcripts back into a conversation automatically.
