# OpenVoiceIME spec

## §G

Cancel panel visibility configurable. User choose whether Cancel hides IME panel; disabled hide keeps panel open for immediate fresh dictation. Post-insert keyboard return configurable; disabled return keeps panel active for hands-free dictation. Transcription language remembered per editor application. Editor text editable from compact cursor/Delete controls. Compact keyboard button returns directly to last-used normal keyboard without opening Android picker.
Compact edit controls let user slide a cursor pad in four directions to move caret and slide Delete left to select text before caret, then release to delete.

## §C

- Preserve current behavior by default: Cancel hides panel.
- Setting autosaves with existing settings.
- Cancel always invalidates pending work, stops recording, abandons audio focus, deletes temporary audio, and clears pending text.
- Disabled hide resets visible controls and starts existing fresh-recording flow while panel stays open.
- Existing `hideAfterSuccess` behavior unchanged.
- Preserve current post-insert keyboard return by default.
- Disabled post-insert keyboard return skips keyboard switch and panel hide, then starts fresh recording.
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
- V11: Every uploaded release APK passes `apksigner verify`; release builds fail clearly when signing properties are unavailable; canonical maintainer workflow explicitly loads `/home/rankis/.openime/openvoiceime-release.properties` and maps all four values to Gradle project properties.
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
| T14 | ~ | Add direct keyboard-return button, safe active-work cleanup, previous/sole-other IME switching fallback, localized accessibility resources, regression tests, and signed release publication. | V27,RemoteSttInputMethodService,ime_voice_input.xml |

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
