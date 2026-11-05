# OpenVoiceIME spec

## §G

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
