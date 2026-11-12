# OpenVoiceIME spec

## §G

Received voice messages: explicit selection plus floating Transcribe automates sharing to existing provider; show selectable result without inserting into chat.

Cancel panel visibility configurable. User choose whether Cancel hides IME panel; disabled hide keeps panel open for immediate fresh dictation. Post-insert keyboard return configurable; disabled return and hide keep panel active for hands-free dictation. Transcription language remembered per editor application. Delete edits selected or preceding editor text. Compact keyboard button returns directly to last-used normal keyboard without opening Android picker.
Centered, equally sized Cancel/Pause/Delete/Transcribe controls; direct settings/language/keyboard toolbar. Pause/resume one recording without uploading or counting paused time. Existing post-insert and Cancel visibility preferences preserved. Cursor pad removed from current keyboard; Delete gestures retained.

## §C

- Preserve current behavior by default: Cancel hides panel.
- Setting autosaves with existing settings.
- Cancel always invalidates pending work, stops recording, abandons audio focus, deletes temporary audio, and clears pending text.
- Disabled hide resets visible controls and starts existing fresh-recording flow while panel stays open.
- Existing `hideAfterSuccess` behavior unchanged.
- Preserve current post-insert keyboard return by default.
- Disabled post-insert keyboard return skips keyboard switch; disabled hide keeps panel open for fresh recording.
- `hideAfterSuccess` takes precedence when post-insert keyboard return is disabled; enabled hide closes the panel after insertion.
- App-specific language choice uses editor package identity, with global-language fallback.
- Per-app language lookup uses local preferences only; no network or package enumeration on panel startup.
- Delete text mutation occurs only on ACTION_UP; cursor/selection preview may update on ACTION_MOVE, while ACTION_CANCEL and vertical escape restore the original selection without deletion.
- Cursor pad horizontal slide moves caret by bounded Unicode code-point steps without splitting surrogate pairs.
- Cursor pad locks to first dominant axis; vertical slide sends bounded Up/Down key events while horizontal rich-editor movement remains character-precise.
- Delete tap removes current selection, or one preceding Unicode code point when selection is empty; left slide selects backward from caret and release deletes that range.
- Completed user-facing implementation publishes signed GitHub release unless user explicitly opts out; project-site APK mirrors same build.
- Settings label translated in English and Spanish.
- Direct keyboard-return control cleans active recording/transient work before switching; Android 9+ prefers last-used IME, with safe fallback to sole other enabled IME where available.

## §I

| surface | contract |
|---|---|
| `AppSettings` | Add persisted `hideAfterCancel` behavior setting; default `true`. |
| `SettingsStore` | Load/save setting without affecting connection-test fingerprint. |
| `SettingsActivity` / `activity_settings.xml` | Show autosaving Behavior checkbox. |
| `RemoteSttInputMethodService.cancelCurrentWork` | Branch panel hide vs visible fresh-recording behavior using stored setting. |
| `AppSettings` / `SettingsStore` | Add persisted `returnToKeyboardAfterInsert` setting; default `true`. |
| `SettingsActivity` / `activity_settings.xml` | Show autosaving post-insert keyboard-return checkbox. |
| `RemoteSttInputMethodService.commitPendingText` | Honor keyboard-return setting; keep panel and restart recording when disabled. |
| `InputMethodService.onStartInput` / `EditorInfo.packageName` | Identify current editor application for language selection. |
| `SettingsStore` / `RemoteSttInputMethodService` | Load/save transcription language per app with global fallback. |
| `EditorGestureController` | Pure cursor/Delete gesture math; code-point-safe bounds, tap/slide/cancel outcomes. |
| `CursorTrackpadController` | Pure dominant-axis lock and bounded incremental horizontal/vertical step deltas. |
| `RemoteSttInputMethodService` / `ime_voice_input.xml` | Expose compact cursor pad and Delete edit controls with localized accessible labels. |
| `RemoteSttInputMethodService` / `ime_voice_input.xml` | Expose compact direct keyboard-return button with localized accessible label and safe IME switching fallback. |
| `VoiceRecorder` / `AudioRecorder` | Pause/resume retain one AAC/M4A recording through native recorder methods. |
| `RecordingDuration` | Accumulate active intervals only; fresh start resets accumulated time. |
| `AsrModelCatalog` / `SettingsActivity` | Fetch authenticated `GET {baseUrl}/v1/audio/models`, show advertised transcription IDs in model spinner, refresh on settings resume or connection changes, and keep custom entry when discovery fails. |

| `KeyboardSetupGuide` / `keyboard_setup_guide.xml` | Localized, persisted keyboard-route guide with explicit external/settings actions and a test field; no automatic system keyboard changes. |

| `DictationShortcutService` / `HardwareDictation` | Optional open/finish chords, bounded main-thread IME handoff, session visibility and normal prior-IME return; explicit finish hides while keeping the voice IME selected. No screen retrieval or global visibility changes. |

| `MessageTranscriptionService` / `ShareAutomation` | Optional explicit selection shortcut; bounded menu automation and separate screen-content permission. |
| `SharedTranscriptionActivity` / `SharedTranscriptionModel` / `SharedAudioImporter` | Single content URI import, existing provider settings, retained cancellable request and private temporary audio, selectable result dialog. |

## §V

- V1: Fresh `AppSettings` and legacy stored settings use `hideAfterCancel == true`.
- V2: Settings UI reflects stored value and autosaves changes; reload returns changed value.
- V3: Cancel cleanup remains unconditional for recording, pending upload, temporary audio, pending text, and operation invalidation.
- V4: `hideAfterCancel == true` calls `requestHideSelf(0)`; `hideAfterCancel == false` does not hide panel, resets controls, and schedules fresh recording.
- V5: Cancel setting does not change successful-insert `hideAfterSuccess` behavior or connection-test fingerprint inputs.
- V6: Fresh `AppSettings` and legacy stored settings use `returnToKeyboardAfterInsert == true`.
- V7: Post-insert keyboard-return setting appears in Behavior UI, autosaves, reloads, and does not change connection-test fingerprint inputs.
- V8: `returnToKeyboardAfterInsert == true` preserves current keyboard-switch and `hideAfterSuccess` fallback; `false` skips `switchToNextKeyboard()`, then follows `hideAfterSuccess`: hide when enabled, otherwise reset controls and schedule fresh recording.
- V14: After successful insertion with keyboard return disabled, `hideAfterSuccess == true` calls `requestHideSelf(0)`; only `hideAfterSuccess == false` keeps the panel for fresh recording.
- V9: Current editor package is captured from `EditorInfo.packageName`; language selection loads/saves under that package, while missing package mappings use global language.
- V10: Per-app language resolution performs only local preference access; it does not perform network I/O, package enumeration, or transcription work during panel startup.
- V11: Every uploaded release APK passes `apksigner verify`; release builds fail clearly when signing properties are unavailable; canonical maintainer workflow explicitly loads `$HOME/.openime/openvoiceime-release.properties` and maps all four values to Gradle project properties.
- V12: [Historical T5] Legacy tracked-insertion deletion was guarded by matching current editor selection/cursor.
- V13: [Historical T5] Legacy Delete disabled without tracked insertion and cleared tracking after deletion; current controls use V15–V18.
- V15: Delete content mutation occurs only on ACTION_UP; ACTION_CANCEL and vertical escape produce no deletion.
- V16: Cursor slide clamps caret to editor bounds and advances by Unicode code-point boundaries, never splitting surrogate pairs.
- V17: Delete tap removes current selection or exactly one preceding code point; left slide selects backward from original caret, rightward motion shrinks selection, and release deletes only resulting range.
- V18: Missing InputConnection/extracted text or stale editor state causes safe no-op; controls expose localized English/Spanish/default accessible labels.
- V19: Delete gesture treats vertical displacement at least as large as horizontal displacement as escape, and each horizontal step maps deterministically to one code point.
- V20: Horizontal ACTION_MOVE emits live caret/selection previews; ACTION_UP commits the controller-owned preview, while ACTION_CANCEL/vertical escape restores the original selection only when editor text and ownership remain unchanged.
- V21: Every completed user-facing implementation, unless explicitly opted out, bumps Android version, passes signed release build and `apksigner verify`, pushes `main` plus version tag, publishes GitHub release with APK asset, updates project-site APK, and verifies remote release state.
- V22: Editors with `TYPE_NULL` or unavailable extracted text use bounded key-event gestures: cursor emits only newly crossed left/right arrows (including reversal), Delete tap emits one `KEYCODE_DEL`, drag defers bounded final count until release, and cancel/vertical escape emits none; rich snapshots apply `startOffset` to global selections.
- V23: Rich extracted snapshots retain local selection offsets and validated nonnegative `startOffset`; every editor selection mutation translates local positions to global exactly once, and gesture capability mode is latched on ACTION_DOWN through UP/CANCEL.
- V24: Delete tap honors latched Terminal/Rich mode without reprobe; only an unlatched accessibility/programmatic tap probes capability, and each tap causes at most one mutation.
- V25: IME configuration changes leave at most one recorder active; rebuilt UI and logical state agree; Cancel/Stop remain usable without switching keyboards.
- V26: Cursor pad locks once to dominant axis after touch slop; horizontal behavior stays unchanged, vertical movement emits only newly crossed bounded `KEYCODE_DPAD_UP`/`KEYCODE_DPAD_DOWN` steps including reversal, tap/cancel emits none, and accessible labels describe four directions.
- V27: Keyboard-return button is always reachable in OpenVoiceIME panel, cancels recorder/audio focus/upload/scheduled work before switching, prefers `switchToPreviousInputMethod()` on Android 9+, falls back without opening picker when exactly one other enabled IME exists, and exposes localized English/Spanish/default label and content description.
- V28: [Historical T15; superseded by V51] Header app/version and compact keyboard-return icon.
- V29: Model picker uses authenticated server-advertised transcription IDs when available, refreshes after settings resume or connection changes, ignores stale responses, and preserves the current editable model if discovery fails. Future model IDs require no client update.

- V30: Every transcription attempt has total deadline `clamp(5s + audio duration, 10s, 15min)` covering upload, server wait, and response body; unavailable duration falls back to 64 kbit/s audio size. Cancel/timeout cancels HTTP call; timeout retains audio for retry; each attempt invalidates stale results and shows remaining wait. Diagnostics/model discovery also cancel their HTTP calls and have total deadlines.
- V31: Insertion checks InputConnection acceptance and optional editor readback; missing/rejected/throwing connection or unchanged readable text never deletes pending transcript/audio, reports success, switches keyboard, or starts fresh recording. Retry inserts existing transcript; Copy preserves it via explicit clipboard action. Optional selection failures never repeat accepted insertion.
- V32: Recording captures editor session; changed input field before delivery blocks automatic insertion and retains transcript for copying or an explicit Insert here action. Same-session restart preserves eligibility.
- V33: Only a JSON string `text` can become a transcript; null/numeric/boolean/object/array values fail parsing and cannot be inserted.
- V34: Failed recorder finalization releases recorder, deletes incomplete audio, and returns failure instead of uploading corrupt bytes.

- V35: An inner bounded panel preserves height despite platform root-layout replacement; transcript/error content scrolls inside weighted status region; action buttons remain outside that region and readable transcript cannot be edited in the keyboard.

- V36: Model-discovery controls/status have English and Spanish translations; full debug lint reports zero errors.

- V37: View creation does not start capture before `onStartInputView`; hide stops capture/finalizes usable audio and cancels upload without deleting recovery data; recreation preserves eligible work; reopening renders pending state without fresh capture; explicit discard and destruction clear owned work.
- V38: New editor ownership blocks automatic transcript delivery; explicit Send/Retry or Insert here binds only that user action to current editor. Hidden upload completion retains pending text without insertion; same-editor restart preserves ownership.
- V39: Dictation finishes prior composing text; comparable editor readback must equal expected replacement, otherwise full transcript/audio stay recoverable. Unicode, selection replacement, partial offsets, missing snapshots, truncation, rejected commit, and failed selection verified on Android.
- V40: Settings initial spinner callbacks preserve saved URL/model/token; connection edits and newer probes cancel/invalidate old probes, which cannot update status or tested fingerprint.
- V41: Concurrent metrics writers across store instances atomically update totals/buckets; snapshots and reset share same process lock.
- V42: Replacing named preset preserves its ID, uses unique IDs for new presets, and removes orphaned encrypted preset tokens without removing active API token.
- V43: Gesture escape on release returns owned original selection; hide restores valid owned preview, while new-editor binding drops prior gesture ownership without applying it to the new field.
- V44: Service startup removes only abandoned `openime-*.m4a` regular files from own cache; unrelated cache content survives.
- V45: Discovered model IDs accept only nonblank JSON strings; malformed catalog entries cannot become selectable pseudo-models.

- V46: Explicit Auto language persists independently of legacy language keys and applies to unmapped editors; legacy fallback is used only before an explicit modern selection exists.
- V47: Switching built-in providers clears the previous provider token before discovery/test requests; saved presets load only their own token.

- V48: Guided keyboard setup offers persisted HeliBoard/current-keyboard routes, refreshes permission/IME readiness on resume, and never changes enabled/default keyboards. Gboard mic restriction and auxiliary-picker limits remain explicit in both locales.
- V49: Setup buttons open official HeliBoard releases, installed HeliBoard settings, Android keyboard settings, or a user-invoked picker/test field. Missing apps/handlers give localized recovery; route changes preserve provider/token/model settings. Setup remains scrollable at narrow width and larger text.

- V50: IME panel height 260dp, bottom padding 8dp; root/inset and navigation backgrounds match current keyboard theme; navigation icons contrast with that background; scrollable recovery keeps actions reachable.
- V51: Centered 2×2 Cancel/Pause/Delete/Transcribe buttons share 52dp height and equal widths; settings/language/keyboard toolbar has 48dp touch targets, no More menu or cursor pad. Controls fit 320dp width and enlarged fonts; keyboard follows system light/dark appearance.
- V52: Pause retains same recorder/file/editor ownership, stops duration accumulation and amplitude updates, abandons audio focus, and sends no request. Resume requests focus and continues same file; Transcribe from either state finalizes once. Cancel/hide/destruction release paused recorder and invalidate late work. Paused view recreation preserves resumable state.
- V53: Existing `hideAfterSuccess`, `returnToKeyboardAfterInsert`, `hideAfterCancel`, and confirmation preferences retain defaults and saved values. Both post-insert flags false keeps panel and automatically records again; true return switches, true hide closes as before.
- V54: Settings toolbar opens app settings directly. Delete stays visible in recording/paused/upload/recovery states and edits only editor text; pause/cancel never delete editor text. Pause/resume failure releases recorder/focus and retains finalized audio for retry when possible.
- V55: Notification/Quick Settings overlays retain active capture and elapsed duration. A synchronous finish/start for the same still-visible editor preserves the recording/file or paused state without starting another recorder. Actual dismissal, changed editor, destruction, or finish without immediate restart discards capture. Input sessions remain distinct; explicit Transcribe binds retained capture to the current session. Verify through real system IME binding and callback tests.

- V56: Boolean audio-response verbatim=true suppresses configured trailing space for that transcript; missing, false or nonboolean metadata preserves normal insertion. Exact prepared pending text survives existing confirmation/recovery, and no Enter is sent automatically.

- V57: Optional Android 11+ Accessibility shortcut Ctrl+Shift+Space opens the enabled voice IME from another selected keyboard with an Android 16+ session-scoped Accessibility hardware-keyboard visibility allowance, restoring the original system preference afterward. Exact modifiers only; consume matched Space down/repeats/up, pass unrelated events, never restart active work. No screen-content capability or typed-text storage.
- V58: Shortcut handoff expires within three seconds without an editor; initial hidden callbacks cannot cancel an unshown handoff; normal closing/cancel/successful return restores the prior enabled IME (explicit insert-and-hide follows V60), never overrides an unrelated manual keyboard change, and preserves existing recording/dismissal preferences and configuration-rebuild ownership. Localized setup exposes optional system approval and refreshed service status.

- V59: Shortcut window requests run after input-start callback completion and only for the currently attached owning IME; callbacks from a destroyed prior instance cannot finish a newer session. Verify repeated Cancel/keyboard-return/transcription round trips through real system binding.

- V60: Ctrl+Shift+Enter (including numpad Enter) while the voice panel is active stops recording/paused capture once, transcribes, inserts without confirmation for this action, then hides without switching IMEs only after accepted insertion. Consume matching Enter down/repeats/up while opening or while the voice IME remains selected, including after hidden/failed opening; never forward Enter or editor-submit actions for that shortcut; busy/inactive recording states cannot duplicate work. Normal on-screen actions/preferences remain unchanged.
- V61: Shortcut failures retain visible recovery and owned audio/text. Explicit shortcut retry uses retained work; confirmation/visibility overrides survive recreation/retry for that dictation only. Discard/dismissal/new editor clears intent; late completion cannot close another panel or insert into another field.

- V62: Voice dictation stays in a bounded non-fullscreen IME panel in portrait and landscape, including an ordinary editor with automatic soft-input behavior and a connected hardware keyboard.

- V63: Bounded app-private shortcut/lifecycle diagnostics survive process restart and can be copied explicitly from Keyboard setup without USB. Reports contain fixed event labels/flags and device/app metadata only; no editor contents, audio, API tokens, server addresses or ordinary typing events; key diagnostics cover only open/finish candidates with command modifiers.
- V64: On Android 16+, shortcut show requests acquire Android Accessibility IGNORE_HARD_KEYBOARD only after the target voice IME has an editor; release owned allowance on close, timeout, switch failure or service teardown. For insert-and-hide, release only after Android acknowledges the window hide, so its editor visibility state is cleared before restoring the hardware setting. Preserve pre-existing modes and later user changes. Reopening an already-selected voice IME works with the normal hidden hardware-keyboard preference.
- V65: On Android 16+, a missing normal show callback may attempt the bound IME window directly once after the opening deadline, only while the session, selected IME and started editor remain current. Never reopen a cancelled or switched session. Allow one additional second for final failure and restore owned visibility. Verify visible host insets, recording and exact insertion under dropped normal show delivery; consume finish chords while the selected voice IME is hidden to avoid forwarding Enter after a failed open.
- V66: Opening Settings discards current dictation work; an owned shortcut panel requests hide and closes its window/handoff before launching Settings, so configuration/editor transitions cannot retain the old error session. Returning after a successful connection check permits a fresh recording with one open chord.
- V67: A view-only recreation caused by the shortcut visibility-setting update preserves an owned recording/paused recorder, file, duration and focus; it cannot turn a newly started recording into ReadyToUpload. Explicit configuration changes retain the existing capture policy, and discard/editor replacement/destruction still cancel owned work.
- V68: If the shortcut Accessibility controller is disconnected during prior-IME restoration, the still-selected owning IME may request the same prior keyboard through its own valid IME token. Never override manual selection or explicit insert-and-hide; verify real restoration and fresh recording after service disable/enable.

- V69: Shared-audio receiver accepts one content URI, bounds import to 25 MiB, detects supported audio headers, uploads with matching MIME type through existing settings, and never inserts into a chat. Invalid/empty/oversized input cannot upload.
- V70: Shared transcription survives view recreation without duplicate upload; cancel stops HTTP, retry retains owned audio, closing deletes temporary audio, late results cannot replace cancelled work. Text copies/shares only on explicit action.
- V71: Separate optional message Accessibility service leaves hardware shortcut screen-access contract unchanged. User selects message and explicitly taps floating Transcribe; automation clicks only known Share/More/own-target controls in the originating messenger and system resolver (up to four forward scrolls and one Just once confirmation), expires within eight seconds, and stops on unrelated navigation, failure, disconnect or receiver launch. No automatic uploads from selection alone or screen-content logging.
- V72: Localized setup explains message-selection shortcut, screen access, fallback sharing and compatibility limits; bounded transcript dialog scrolls at narrow width and enlarged fonts. Fixture integration proves menu automation and receiver lifecycle without reading personal chats; third-party compatibility remains explicit until device-tested.

- V73: Shared-transcription Retry is accepted only from failed/cancelled state; repeated or stale Retry actions during work or after success cannot upload again or replace a successful result.

- V74: Message action uses the shared day/night palette and a 48dp pill. Prefer fresh long-click/selected-message screen bounds, clamp within the current window, choose below/above without covering the audio, and use a centered fallback when bounds are unavailable. Scrolling, deselection and unrelated navigation invalidate stale anchors.
- V75: Explicit Transcribe draws the V77 frozen-chat overlay before opening owned Share menus. Underlying windows remain operable by accessibility. Keep the cover until the receiver acknowledges its first frame; timeout, cancellation, configuration changes, interrupt and disconnect remove it. This masks menus without claiming background-only sharing or guaranteed suppression of system transitions.
- V76: Transcript uses a bottom-aligned rounded sheet with shared day/night colors, readable selectable text, 48dp actions above navigation/cutout insets and a scrollable long result. Keep cancellation/retry/rotation/audio cleanup contracts. Narrow large-text layouts retain all actions. No native message insertion or transcript history is introduced; transient screen capture follows V77.

- V77: Explicit Transcribe alone captures the current supported messenger on Android 11+, preferring its own window on Android 14+. Freeze it in a memory-only overlay with a 48dp cancel/progress indicator near the audio; draw before any menu action. No bitmap is saved, logged, uploaded or retained after handoff/cancel/timeout/configuration/disconnect. Failed/blocked capture opens no Share menus and offers direct manual sharing.
- V78: Capture completion must match the still-current request and source package/window. Cancel, timeout, navigation and teardown invalidate pending capture; stale success releases its bitmap without drawing or sharing. The frozen chat cannot receive taps while automation controls underlying menus.

## §T

| id | status | goal | cites |
|---|---|---|---|
| T1 | x | Add persisted setting, Behavior checkbox, localized labels, and Cancel visibility branch; verify defaults, autosave model, cleanup, and both hide branches with unit/source tests; run `./gradlew test` and `./gradlew assembleDebug`. | V1,V2,V3,V4,V5,AppSettings,SettingsStore,SettingsActivity,RemoteSttInputMethodService |
| T2 | x | Add persisted post-insert keyboard-return setting, Behavior checkbox, localized labels, and hands-free post-insert flow; verify defaults, persistence wiring, keyboard-switch branching, panel retention, and fresh recording; run `./gradlew test` and `./gradlew assembleRelease`. | V6,V7,V8,AppSettings,SettingsStore,SettingsActivity,RemoteSttInputMethodService |
| T3 | x | Remember transcription language per editor application using `EditorInfo.packageName`, global fallback, local preference lookup, and regression tests; run `./gradlew testDebugUnitTest assembleRelease`. | V9,V10,SettingsStore,RemoteSttInputMethodService |
| T4 | x | Require release signing configuration, add APK signature regression coverage, and republish signed patch release; run `./gradlew testDebugUnitTest assembleRelease` plus `apksigner verify`. | V11,app/build.gradle.kts,AGENTS.md |
| T5 | x | Add safe Delete button for last inserted transcription, localized labels, cursor/selection guard, and regression tests; run `./gradlew testDebugUnitTest assembleRelease`. | V12,V13,RemoteSttInputMethodService,ime_voice_input.xml |
| T6 | x | Restore `hideAfterSuccess` precedence when keyboard return is disabled; add regression coverage; run `./gradlew testDebugUnitTest assembleRelease` plus `apksigner verify`. | V14,RemoteSttInputMethodService |
| T7 | x | Add compact cursor-pad/Delete gestures with live ACTION_MOVE previews, code-point-safe pure math, ACTION_UP-only text mutation, ownership-safe cancel restore, localized accessible labels, and regression tests; run `./gradlew testDebugUnitTest assembleRelease` plus `apksigner verify`. | V15,V16,V17,V18,V19,V20,EditorGestureController,RemoteSttInputMethodService,ime_voice_input.xml |
| T8 | x | Publish cursor/Delete gestures as signed v0.3.5, update project-site APK, push `main` and tag, create GitHub release with verified asset, and persist automatic release policy. | V11,V21,AGENTS.md,README.md,app/build.gradle.kts |
| T9 | x | Add terminal/non-rich key-event fallback and ExtractedText offset correctness; verify gesture math, service wiring, signed v0.3.6 release, and remote APK. | V22,TerminalGestureController,RemoteSttInputMethodService |
| T10 | x | Fix partial extracted-text local/global offset handling and latch rich/terminal routing for each gesture; verify signed v0.3.7 release and remote APK. | V23,EditorGestureController,RemoteSttInputMethodService |
| T11 | x | Keep Delete tap mode latched through synchronous performClick, probe only when unlatched, and publish signed v0.3.8 with remote verification. | V24,RemoteSttInputMethodService |
| T12 | x | Make IME configuration rebuilds clean up recorder/audio focus and transient work before UI recreation, keep cleanup idempotent across logical-state drift, verify rotation recovery, and publish signed release. | V25,RemoteSttInputMethodService,AudioRecorder |
| T13 | x | Add four-direction cursor trackpad with dominant-axis lock, vertical key-event stepping, reversal/clamp tests, localized labels, and signed release publication. | V26,CursorTrackpadController,RemoteSttInputMethodService |
| T14 | x | Add direct keyboard-return button, safe active-work cleanup, previous/sole-other IME switching fallback, localized accessibility resources, regression tests, and signed release publication. | V27,RemoteSttInputMethodService,ime_voice_input.xml |
| T15 | x | Replace text keyboard-return button with compact upper-right header icon, left-align app/version label, preserve switching behavior, add layout regression test, and publish signed release. | V27,V28,RemoteSttInputMethodService,ime_voice_input.xml |
| T16 | x | Discover transcription model IDs from configured server, refresh the picker on open and connection changes, preserve custom fallback, verify request/auth/parser behavior, and publish signed release. | V21,V29,AsrModelCatalog,SettingsActivity |

| T17 | x | Add adaptive total transcription deadline, cancellable HTTP, countdown, stale-attempt invalidation, recoverable checked insertion, editor-session guard, strict response parsing, recorder-finalization failure handling, regression tests, and signed v0.3.14 release. | V21,V30,V31,V32,V33,V34,V35,V36 |

| T18 | x | Review lifecycle/editor compatibility, shorten common dictation deadlines, preserve recovery across view changes, add isolated Android integration coverage, audit settings/storage/network/metrics/gestures, fix verified regressions, and publish signed release. | V21,V30,V32,V37,V38,V39,V40,V41,V42,V43,V44,V45,V46,V47 |

| T19 | x | Add localized guided keyboard setup, route persistence, safe external/settings actions, isolated Android verification, and signed v0.3.16 publication. | V21,V36,V48,V49 |

| T20 | x | Implement centered keyboard, direct toolbar, native pause/resume, accumulated active duration, theme resources, Android lifecycle/layout/preference tests, and signed release with matching project-site APK. | V21,V30,V31,V35,V50,V51,V52,V53,V54 |

| T21 | x | Preserve active/paused capture across immediate visible input restart after system overlays; verify real Quick Settings binding, duration continuity, dismissal/editor cleanup, and signed v0.4.1 publication. | V21,V32,V52,V55 |

| T22 | x | Preserve service command text using strict boolean verbatim metadata, verify parser/insertion formatting and publish signed release. | V21,V31,V38,V56 |

| T23 | x | Add optional global hardware dictation shortcut, localized accessibility setup, bounded IME handoff and prior-keyboard return; verify key filtering/system binding and publish signed v0.4.3. | V21,V36,V52,V53,V57,V58,V59 |

| T24 | x | Add Ctrl+Shift+Enter transcribe/insert/hide action, suppress previous-IME return for that successful hide, verify failure/retry/ownership/key delivery and publish signed v0.4.4. | V21,V31,V32,V52,V53,V60,V61 |

| T25 | x | Keep the voice panel non-fullscreen, verify landscape/normal-editor/settings-return behavior and add private copyable diagnostics for remaining device-specific dismissal; publish signed v0.4.5 without claiming untraced OEM behavior fixed. | V21,V57,V59,V62,V63 |

| T26 | x | Diagnose remaining OnePlus panel flashing/no-show from the user-copied shortcut trace; apply and verify a fix supported by that device sequence; publish signed v0.4.6. | V57,V58,V59,V62,V63,V64 |
| T27 | x | Use v0.4.6 OnePlus trace to add one guarded direct-window fallback for dropped show delivery, verify fault injection/cancellation/Enter consumption and publish signed v0.4.7. | V57,V60,V63,V64,V65,V66,V67,V68 |
| T28 | x | Confirm v0.4.7 on the OnePlus Pad 3 with actual open/finish chords; inspect copied direct-window fallback and window lifecycle events if opening still fails. | V60,V63,V65 |

| T29 | x | Implement optional floating message transcription, bounded shared-audio import/result dialog, deterministic menu automation, English/Spanish setup, JVM and Android fixture tests; publish signed release. | V21,V30,V36,V69,V70,V71,V72 |

| T30 | x | Anchor and theme the message pill, mask share transitions, restyle the result as a bottom sheet, verify geometry/lifecycle/actual overlay handoff and signed publication. | V21,V70,V71,V73,V74,V75,V76 |

| T31 | x | Replace progress scene with transient chat snapshot/nearby indicator, verify memory cleanup/secure-window rejection/stale callbacks/real chooser; research direct audio retrieval and publish signed release. | V21,V70,V74,V75,V76,V77,V78 |

## §B

| id | date | cause | fix |
|---|---|---|---|
| B1 | 2026-08-04 | release signing properties absent; Gradle still produced unsigned APK published as v0.3.0 | V11 |
| B2 | 2026-08-04 | keyboard-return-disabled branch bypassed `hideAfterSuccess`, so enabled hide setting could not close panel | V14 |
| B3 | 2026-08-13 | gesture regression checks did not pin equal-axis escape and exact step distance; drag could be interpreted as delete | V19 |
| B4 | 2026-08-13 | controller emitted only final ACTION_UP command, so caret/selection did not track finger and cancel could not restore preview | V20 |
| B5 | 2026-08-13 | private signing file existed outside Gradle auto-load paths; plain release command was misread as missing credentials | V11 |
| B6 | 2026-08-13 | implementation stopped after local verification, so feature had no downloadable GitHub release or updated project-site APK | V21 |
| B7 | 2026-08-13 | gestures assumed extracted text; terminal InputConnection returned null, making Termius controls no-op | V22 |
| B8 | 2026-08-13 | nonzero extracted-text offsets were mixed between local snapshots and global selections; capability probing reran during gesture and could switch routes mid-touch | V23 |
| B9 | 2026-08-13 | synchronous Delete performClick reprobed capability despite latched gesture mode, allowing transient route switch | V24 |
| B10 | 2026-08-13 | configuration rebuild reset logical state while MediaRecorder survived, causing duplicate start and state-gated cleanup failure | V25 |
| B11 | 2026-08-20 | keyboard-return control used full weighted text button in edit row, wasting primary editing space | V28 |
| B12 | 2026-11-01 | fixed socket read timeout did not bound whole request; cancelled coroutine left blocking HTTP active | V30 |
| B13 | 2026-11-01 | insertion ignored missing connection and commit return value, discarded audio/text, and restarted recording even when editor had not changed | V31 |
| B14 | 2026-11-01 | async transcript delivery used current editor without binding it to recording input session | V32 |
| B15 | 2026-11-01 | JSON string coercion accepted non-string transcript values as dictation | V33 |
| B16 | 2026-11-01 | recorder stop failure was swallowed and nonempty incomplete file remained eligible for upload | V34 |
| B17 | 2026-11-01 | insertion adapter regression assumed no snapshot query before commit; readback verification requires one | V31 |
| B18 | 2026-11-01 | fixed-height panel used unbounded status content and a second weighted error region, allowing long transcripts/errors to displace actions | V35 |
| B19 | 2026-11-01 | mock response throttling also throttled multipart request reads, so body-cancel test stalled before headers; use delayed response body and await headers explicitly | V30 |
| B20 | 2026-11-01 | model-discovery labels lacked en/es translations; XML framework tint was flagged by AppCompat lint | V36 |
| B21 | 2026-11-01 | 45-second minimum made normal 3–10 second dictation failures wait too long | V30 |
| B22 | 2026-11-01 | view creation scheduled recording before visibility; hide/config cleanup discarded recoverable audio/text and cancelled uploads | V37,V38 |
| B23 | 2026-11-01 | accepted editor request could truncate dictation; commit replaced an existing composing word instead of appending voice text | V39 |
| B24 | 2026-11-01 | posted initial preset selection reapplied built-in defaults over saved connection/model | V40 |
| B25 | 2026-11-01 | stale connection-test response marked changed form reachable and replaced tested fingerprint | V40 |
| B26 | 2026-11-01 | unsynchronized SharedPreferences read-modify-write lost concurrent upload metrics | V41 |
| B27 | 2026-11-01 | replacing named preset generated new ID but retained old encrypted token indefinitely | V42 |
| B28 | 2026-11-01 | release-only vertical escape discarded restore command; view detach discarded owned cursor preview without restoring selection | V43 |
| B29 | 2026-11-01 | process death skipped destructor, leaving abandoned audio in cache after later service startup | V44 |
| B30 | 2026-11-01 | model-ID parser coerced non-string values or failed entire catalog on malformed entries | V45 |
| B31 | 2026-11-01 | saving Auto removed modern language key and reactivated obsolete legacy Spanish/English choice | V46 |
| B32 | 2026-11-01 | built-in provider switch changed URL/model while retaining previous provider credential for subsequent requests | V47 |
| B33 | 2026-11-01 | debug integration host assigned literal view ID, rejected by Android resource-type lint | V36 |
| B34 | 2026-11-04 | 300dp weighted panel wasted recording space; transparent inset and unstyled navigation window exposed mismatched background | V50 |
| B35 | 2026-11-05 | Android verification interrupted by ADB daemon disconnect; transport failure cannot establish app regression or successful verification | reconnect device and rerun complete suite; no product invariant change |
| B36 | 2026-11-09 | Quick Settings collapse synchronously finished/restarted the still-visible editor; unconditional finish cleanup deleted recording and started another | V55 |
| B37 | 2026-11-11 | Shell-injected key combinations bypassed Accessibility key filtering in device test; exercise callback explicitly and verify real system IME binding, retain physical-keyboard verification limit. | test harness correction; V57 unchanged |
| B38 | 2026-11-11 | Root-inset visibility changed before IME hide animation completed; delayed notifyImeHidden dismissed the next panel. Await WindowInsetsAnimation completion before every shortcut handoff. | test harness correction; V58 retains pending-handoff guard |
| B39 | 2026-11-11 | Test attempted hidden UiAutomation syncInputTransactions API; use public waitForIdle and measured IME visibility instead. | test compile correction; no product invariant |
| B40 | 2026-11-11 | isInputViewShown can remain true on destroyed IME after direct keyboard switch; await actual binding/instance teardown rather than stale source visibility. | test harness correction; V58 |
| B41 | 2026-11-11 | Recorder startup callback preceded platform input-view visibility; assertion sampled before show completed. Await recorder and visible input view together. | test harness correction; V57 |
| B42 | 2026-11-11 | Shortcut handoff issued a window request inside input-start and matched old callbacks by component only. Post the request after callback completion and require current service identity. | V59 |
| B43 | 2026-11-11 | ADB daemon disappeared during complete device suite, producing EOF/connection-refused and ddmlib class-loading errors. Reconnect and rerun full suite in a fresh Gradle process. | transport failure; same verification rule as B35 |
| B44 | 2026-11-11 | Lint could not infer Android 11 shortcut gating across the posted window request; add explicit runtime API guard at requestShowSelf. | V57; lintDebug |
| B45 | 2026-11-11 | Layout tests truncated dp-to-pixel expectations at fractional density while Android rounds XML dimensions; use roundToInt for 52dp actions and 260dp panel. | V50,V51; test correction |
| B46 | 2026-11-11 | Resumed-typing test injected keys after default IME changed but before typing keyboard finished binding. Await destination show/hide animation before injecting text. | V58; test correction |
| B47 | 2026-11-11 | Existing system-overlay test requested IME during asynchronous binding before editor focus on Android 16. Select test IME before launch, await active focused editor, and explicitly show it. | V55; test correction |
| B48 | 2026-11-11 | Android 16 emulator reported a visible qwerty hardware keyboard with virtual-keyboard visibility disabled, so the ordinary overlay-test IME correctly refused to show. Snapshot/enable that system option only for the overlay test, then restore it. | V55; test isolation |
| B49 | 2026-11-11 | Bare this inside scope.launch referred to CoroutineScope rather than the IME when closing a shortcut session. Qualify the service receiver. | compiler coverage; no new invariant |
| B50 | 2026-11-11 | Hardware test nested runOnMainSync by calling the key-dispatch helper inside a main-thread block. Dispatch the finish chord outside that block. | test harness correction; no new invariant |
| B51 | 2026-11-11 | Hardware test requested the typing keyboard before the focused editor was served (PHASE_CLIENT_VIEW_SERVED). Await window focus and InputMethodManager.isActive before showing it. | test readiness; V60 unchanged |
| B52 | 2026-11-11 | Accepted insertion could exit on hidden/changed editor before clearing one-dictation finish intent. Consume the intent on acceptance before any visibility/session early return. | V61 |
| B53 | 2026-11-11 | Voice IME inherited Android landscape fullscreen extraction mode, reproducing a full-screen panel in an ordinary editor. Explicitly disable fullscreen evaluation for the bounded recording UI. | V62 |

| B54 | 2026-11-11 | OnePlus trace receives the open chord and has a selected IME/editor, but requestShowSelf produces no show callback with hardware visibility disabled. Add Android Accessibility session allowance before the explicit show request, restore it afterward, and retain device verification limit. | V64 |
| B55 | 2026-11-11 | Reopen test did not await host hide-animation completion and used a shell-selected service before binding settled. Await host insets/animation end and establish already-selected state through a completed shortcut session. | test harness correction; V64 |
| B56 | 2026-11-11 | Restoring the hardware setting before requestHideSelf hid the IME through configuration, leaving Android 16 editor insets requesting visibility and swallowing the next show. Keep the allowance through the hide request and release on the window-hidden callback; suppress prior-IME return separately. | V64 |
| B57 | 2026-11-11 | Android 13 Samsung system-server SettingsObserver dereferenced a null IME client during test teardown; reproduced even with the new allowance disabled. Restrict the new workaround to the affected Android 16+ path; retain older IME behavior and report the firmware test limitation. | V64 |
| B58 | 2026-11-11 | Existing shortcut tests sampled preference restoration when recording stopped, before successful insert/hide completed. Assert restoration after the window-hidden callback or IME teardown, matching the required cleanup order. | test readiness; V64 |
| B59 | 2026-11-11 | OnePlus v0.4.6 accepts hardware allowance mode 2 and restarts the same editor, but two requestShowSelf calls still produce no show callback. The allowance is insufficient; test one guarded direct-window fallback after the normal deadline, retaining real-device verification. | V65 |
| B60 | 2026-11-11 | Pending-editor cancellation test expected IME destruction even when voice was already selected, and Accessibility-restart test sampled reconnect before old IME/hide completion. Assert cancelled session ownership with selected IME preserved; await old service teardown and settled host insets before reconnecting. | test readiness; V65 |
| B61 | 2026-11-11 | Settings navigation left the owned error session visible across Settings editor/configuration changes; the next open chord preserved the old error instead of recording. Discard current work and request/close the shortcut window before launching Settings. Also await settled visible host insets before repeated-shortcut assertions. | V66; test readiness V65 |
| B62 | 2026-11-11 | Settings-return test pressed open during portrait-to-landscape activity restoration; configuration recreation legitimately stopped fresh capture into retained audio. Await Settings destruction and landscape layout, then settle the served typing editor before the chord. | test readiness; V66 |
| B63 | 2026-11-11 | Android can show the newly selected IME before the allowance setting update recreates its view. onCreateInputView detached and stopped the already-running recorder into ReadyToUpload. Preserve owned capture across view-only recreation; keep explicit configuration/discard behavior. | V67 |
| B64 | 2026-11-11 | ImeTracker showed the previous typing IME finishing its hide after the next test shortcut despite host insets and animation completion, cancelling the new voice window. Await Accessibility event quiescence as well as host animation/insets before dispatching the test chord. | test readiness; V65 |
| B65 | 2026-11-11 | Accessibility onUnbind could no longer switch keyboards (accepted=false), leaving the voice IME selected and breaking expected off/on restoration. Fall back to the still-selected bound IME switchInputMethod operation, rechecking selection and explicit no-return intent. | V68 |
| B66 | 2026-11-12 | OnePlus denies shell GRANT_RUNTIME_PERMISSIONS, causing 49 setup failures before microphone-dependent tests. Reuse an already granted test-app microphone permission and request it through normal app UI when shell grants are unavailable. | test setup correction; no product invariant change |
| B67 | 2026-11-12 | OnePlus disconnected the debug Accessibility service before destroying its cached instance; the test reported a stop failure although keyboard and visibility were restored. Clear the test instance on unbind, guard stale destruction by identity, and retain explicit stop-state diagnostics. | test lifecycle correction; no product invariant change |
| B68 | 2026-11-12 | Live Bluetooth check sampled restored keyboard preference when host IME insets first became hidden, before the production IME close callback finished. The first transcript was inserted and the preference later restored; await the callback-dependent setting before asserting and retain per-round timing on failure. | physical test readiness; V64 |
| B69 | 2026-11-12 | Diagnostic-copy instrumentation mutated the personal device clipboard without cleanup. Snapshot the existing ClipData and restore it in finally, alongside test settings, without printing or persisting clipboard contents. | test isolation correction; V63 unchanged |

| B70 | 2026-11-12 | Referenced nonexistent AppCompat DayNight.Dialog.NoActionBar parent; use DayNight.Dialog with windowNoTitle. | resource-link verification; V72 unchanged |
| B71 | 2026-11-12 | Accessibility detail action is not a public SDK 35 constant; use its action string with public Accessibility settings fallback. | compiler verification; V72 unchanged |
| B72 | 2026-11-12 | Fixture chained addFlags after void Intent.setClipData; assign clipData inside apply before continuing intent construction. | test compile verification; V71 unchanged |
| B73 | 2026-11-12 | Locked physical test tablet leaves NotificationShade focused; floating UI test cannot observe the synthetic selection. Require unlocked device before counting UI verification. | device readiness; V72 unchanged |
| B74 | 2026-11-12 | Repeated programmatic Retry after fast success started a third upload and replaced the result with timeout. Gate Retry by failed/cancelled state as well as active job. | V73; shared cancellation/recreation tests |
| B75 | 2026-11-12 | Android 16 chooser handoff did not reach receiver; target handling lacked offscreen-target scrolling and Just once confirmation. Bound both operations and verify handoff on Android 13 and 16. | V71; chooser state-machine and device tests |
| B76 | 2026-11-12 | Fixed-position framework button, visible automated menus and default dialog made voice-message transcription feel disconnected from the chat. Use anchored themed action, bounded progress cover and matching result sheet. | V74,V75,V76 |
| B77 | 2026-11-12 | Covered Android 16 chooser exposed the exact receiver node with isVisibleToUser=false, so the target filter stalled. Permit an occluded exact own-app target only in an expected system resolver while the progress cover is active; other menu controls keep visibility checks. | V75; covered real-chooser test |
| B78 | 2026-11-12 | Screenshot showed a standalone pill measured to intrinsic 28dp despite XML height, and a bottom-sheet action overlapping the gesture handle. Measure the overlay at explicit 48dp and apply navigation/cutout padding to the sheet. | V74,V76; overlay bounds and sheet inset checks |
| B79 | 2026-11-12 | Interrupt cleanup detached the receiver acknowledgement while the service could stay connected; a later share would retain its cover until timeout. Detach only on disconnect/destruction. | V75; interrupt/reuse cleanup |
| B80 | 2026-11-12 | Overlay test still called the removed full-screen progress API after switching to snapshot ownership. Supply a synthetic owned bitmap and assert its release after cancellation. | V77; test contract update |
| B81 | 2026-11-12 | Fixture read message bounds before first layout, producing a false nearby-pill failure. Await focused visible geometry before selection. | V74; test readiness |
| B82 | 2026-11-12 | Navigation test launched a new task and destroyed the original fixture, then queried its ActivityScenario. Keep the source counter reference for the late-callback assertion. | V78; test lifecycle |
| B83 | 2026-11-12 | Repeated overlay measurement allowed the action label to wrap into two lines. Keep action/indicator labels single-line and bound the indicator label width. | V74,V77; screenshot review |
| B84 | 2026-11-12 | Picker fell back to storage root because its nested fixture was absent; root folder approval is intentionally disabled. Verify fixture existence and confirm the exact tree ID before reading. | storage experiment readiness; no production invariant change |
| B85 | 2026-11-12 | Shell-created cross-app Android/media fixture was absent; the initial-URI hint therefore fell back to storage root. Create synthetic bytes through a test-APK provider under its own distinct UID. | storage experiment isolation; no production invariant change |
| B86 | 2026-11-12 | A test-APK provider runs under a separate app class loader and cannot assume the target APK Kotlin runtime. Use a dependency-free Java provider for the cross-UID synthetic fixture. | research fixture isolation |
| B87 | 2026-11-12 | Samsung handoff timed out with previously installed test artifacts; freshly rebuilt/reinstalled APKs passed the same case. Cause unconfirmed; retain capture diagnostics and require a complete freshly installed device rerun before publication. | V75,V77; device verification |
| B88 | 2026-11-12 | Android 16 legacy UI Automator probe failed loading android.test.RepetitiveTest, then assumed a nonexistent bridge getter. Include android.test.base.jar in diagnostic CLASSPATH and obtain the bridge UiAutomation field; the selected-node probe then passed. | diagnostic harness only; no production invariant change |
| B89 | 2026-11-12 | An unconditional Back in the real-chat probe left the authorized conversation and selected a chat-list row. No share followed that selection. Guard the conversation/selected-message context and close selection only when its control exists; rerun paired all three test notes. | diagnostic harness; existing V71 navigation scope |
| B90 | 2026-11-12 | Android returned a null active accessibility root while the focused WhatsApp window still supplied its tree. Diagnostic retrieval via interactive focused windows restored the test; no production fallback was added. | diagnostic harness only; preserve source-window checks |
| B91 | 2026-11-12 | Imported Opus audio had no native waveform, the phone killed the test tone player, and a silence note had no distinguishing bars. Exclude failed setup from positive evidence and abstain when no waveform distinguishes candidates. | research fixture validity; no production invariant change |
| B92 | 2026-11-12 | A different prepared recording amplitude-shaped to the displayed bars scored 0.9985 versus the correct file's 0.7234, passing the experimental score/gap rule. Waveform similarity ranks candidates but is not attachment identity; no automatic direct-file mode was shipped. | research counterexample; no production invariant change |
| B93 | 2026-11-12 | A supplied voice note remained unplayed but its chat had been opened; zero active WhatsApp notifications remained. Audio playback state is not notification availability. Request a fresh arrival while the chat stays in the background; no negative URI result can be inferred yet. | notification fixture validity; no production invariant change |
| B94 | 2026-11-12 | Typed shell notification access required ACCESS_NOTIFICATIONS, and phone installation rejected the diagnostic APK with INSTALL_FAILED_USER_RESTRICTED. Use an authorized listener with public recoverBuilder/getStyle decoding; synthetic controls passed on Android 13/16, and an ordinary retry after owner setup enabled the real listener read. | diagnostic capability checks; no permission bypass |
| B95 | 2026-11-12 | A WhatsApp notification key contained an embedded newline. Line-splitting truncated the conversation key, leaving only a readable group summary without media. Preserve full keys and quote them; the actual conversation exposed its per-message audio MIME/URI. | diagnostic parser; no production invariant change |
| B96 | 2026-11-12 | A cached notification URI changed during refresh, although a scoped re-read returned the same 21,150-byte audio/hash. An experimental URI-only equality guard therefore skipped the test message. Scope the fixture by notification/message context and verify content; do not treat an opaque URI as a permanent identity. | notification research; native-row join remains unproved |
| B97 | 2026-11-12 | Direct audio opens succeeded while a notification was active, then failed with SecurityException 2/10 seconds after targeted dismissal; the bounded memory copy retained identical bytes. Capture while the grant is valid and define cache lifetime before building a later transcription action. | notification research; no production feature shipped |
| B98 | 2026-11-12 | A forwarded PendingIntent and timer did not establish chat entry or notification removal. Trigger lifetime checks from the actual removal callback and assert it; the final test dismissed only the captured notification. | diagnostic lifecycle evidence; no chat-opening timing claim |
