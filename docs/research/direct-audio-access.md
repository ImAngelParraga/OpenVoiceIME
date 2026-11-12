# Direct access to received audio

Checked 2026-11-12. The released shortcut still obtains audio through Android sharing; none of the account integrations below is enabled.

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

For a completely independent audio pipeline, an account-based companion is the strongest documented direction. For minimal additional setup, a scoped-folder prototype is smaller, but exact-message matching must be demonstrated before it can replace sharing safely. Neither requires sending transcripts back into a conversation automatically.
