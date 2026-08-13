# OpenVoiceIME spec

## §G

Cancel panel visibility configurable. User choose whether Cancel hides IME panel; disabled hide keeps panel open for immediate fresh dictation. Post-insert keyboard return configurable; disabled return keeps panel active for hands-free dictation. Transcription language remembered per editor application. Editor text editable from compact cursor/Delete controls.
Compact edit controls let user slide a cursor pad horizontally to move caret and slide Delete left to select text before caret, then release to delete.

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
- Cursor and Delete gestures mutate editor only on ACTION_UP; ACTION_CANCEL and vertical escape do not delete text.
- Cursor pad horizontal slide moves caret by bounded Unicode code-point steps without splitting surrogate pairs.
- Delete tap removes current selection, or one preceding Unicode code point when selection is empty; left slide selects backward from caret and release deletes that range.
- Settings label translated in English and Spanish.

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
| `RemoteSttInputMethodService` / `ime_voice_input.xml` | Expose compact cursor pad and Delete edit controls with localized accessible labels. |

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
- V11: Every uploaded release APK passes `apksigner verify`; release builds fail clearly when signing properties are unavailable.
- V12: [Historical T5] Legacy tracked-insertion deletion was guarded by matching current editor selection/cursor.
- V13: [Historical T5] Legacy Delete disabled without tracked insertion and cleared tracking after deletion; current controls use V15–V18.
- V15: Cursor/Delete gesture state performs no editor mutation before ACTION_UP; ACTION_CANCEL and vertical escape produce no deletion.
- V16: Cursor slide clamps caret to editor bounds and advances by Unicode code-point boundaries, never splitting surrogate pairs.
- V17: Delete tap removes current selection or exactly one preceding code point; left slide selects backward from original caret, rightward motion shrinks selection, and release deletes only resulting range.
- V18: Missing InputConnection/extracted text or stale editor state causes safe no-op; controls expose localized English/Spanish/default accessible labels.
- V19: Delete gesture treats vertical displacement at least as large as horizontal displacement as escape, and each horizontal step maps deterministically to one code point.

## §T

| id | status | goal | cites |
|---|---|---|---|
| T1 | x | Add persisted setting, Behavior checkbox, localized labels, and Cancel visibility branch; verify defaults, autosave model, cleanup, and both hide branches with unit/source tests; run `./gradlew test` and `./gradlew assembleDebug`. | V1,V2,V3,V4,V5,AppSettings,SettingsStore,SettingsActivity,RemoteSttInputMethodService |
| T2 | x | Add persisted post-insert keyboard-return setting, Behavior checkbox, localized labels, and hands-free post-insert flow; verify defaults, persistence wiring, keyboard-switch branching, panel retention, and fresh recording; run `./gradlew test` and `./gradlew assembleRelease`. | V6,V7,V8,AppSettings,SettingsStore,SettingsActivity,RemoteSttInputMethodService |
| T3 | x | Remember transcription language per editor application using `EditorInfo.packageName`, global fallback, local preference lookup, and regression tests; run `./gradlew testDebugUnitTest assembleRelease`. | V9,V10,SettingsStore,RemoteSttInputMethodService |
| T4 | x | Require release signing configuration, add APK signature regression coverage, and republish signed patch release; run `./gradlew testDebugUnitTest assembleRelease` plus `apksigner verify`. | V11,app/build.gradle.kts,AGENTS.md |
| T5 | x | Add safe Delete button for last inserted transcription, localized labels, cursor/selection guard, and regression tests; run `./gradlew testDebugUnitTest assembleRelease`. | V12,V13,RemoteSttInputMethodService,ime_voice_input.xml |
| T6 | x | Restore `hideAfterSuccess` precedence when keyboard return is disabled; add regression coverage; run `./gradlew testDebugUnitTest assembleRelease` plus `apksigner verify`. | V14,RemoteSttInputMethodService |
| T7 | ~ | Add compact cursor-pad/Delete gestures with code-point-safe pure math, ACTION_UP-only editor mutations, cancel/vertical escape no-op, localized accessible labels, and regression tests; run `./gradlew testDebugUnitTest assembleRelease` plus `apksigner verify`. | V15,V16,V17,V18,V19,EditorGestureController,RemoteSttInputMethodService,ime_voice_input.xml |

## §B

| id | date | cause | fix |
|---|---|---|---|
| B1 | 2026-08-04 | release signing properties absent; Gradle still produced unsigned APK published as v0.3.0 | V11 |
| B2 | 2026-08-04 | keyboard-return-disabled branch bypassed `hideAfterSuccess`, so enabled hide setting could not close panel | V14 |
| B3 | 2026-08-13 | gesture regression checks did not pin equal-axis escape and exact step distance; drag could be interpreted as delete | V19 |
