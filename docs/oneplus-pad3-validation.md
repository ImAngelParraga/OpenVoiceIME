# OnePlus Pad 3 validation

Validated on 2026-11-12 with OnePlus OPD2415, Android 16 / API 36, a Bluetooth Logitech MX Keys Mini, HeliBoard, and the published OpenVoiceIME 0.4.7 APK (version code 39).

## Results

- 74 automated Android checks passed on the tablet. The additional interactive test was skipped in the unattended run.
- 116 JVM tests, lint, and the canonical signed release build passed; APK signature verification succeeded.
- One physical Logitech open/record/finish sequence used the installed release app, its actual microphone, and its existing transcription backend. Text was inserted and the panel closed. The first normal IME show request reached `onShown` in 294 ms; the three-second fallback was not used in that sequence.
- The interactive harness initially checked the hardware-keyboard setting before the closing callback restored it, then exited. The setting did restore to its original value. The harness now waits for that callback-dependent state and retains timing on failure. Two additional manual rounds were not completed before the human-input deadline, so the complete three-round interactive test is not reported as passing.

Automated coverage includes portrait/landscape panel bounds, repeated open/finish/hide, retained recovery after failures, insertion and Enter consumption, Settings return, Accessibility restart, dropped normal show delivery, failed fallback, editor/keyboard changes, and recorder ownership through view recreation. These tests use synthetic audio/network behavior and invoke the Accessibility callback directly. The physical check supplies separate evidence for actual Bluetooth delivery, microphone capture, backend transcription, and insertion into a native test field; it does not establish compatibility with every Notes or WebView editor.

No production change or timing optimization was justified by these observations. The installed release APK matched the published 0.4.7 asset. Only test infrastructure and documentation changed.

## Device setup and cleanup

OxygenOS denied automatic shell microphone grants. The isolated test app was granted Microphone through Android's normal permission dialog; test setup now reuses that grant. A direct instrumentation run preserves it between installs with `-r`.

The debug Accessibility fixture now clears its connected instance on unbind, because OxygenOS can retain the disconnected service object before destruction. Its old lifetime check produced a false stop failure even though the keyboard and hidden-IME setting were already restored.

The initial diagnostics-copy test replaced the clipboard with test data. Its cleanup now saves and restores the previous ClipData in memory; clipboard contents are never written to the report.

The test APKs were removed. Original default/enabled keyboards, Accessibility services, hardware-keyboard visibility, Accessibility keyboard mode, and rotation settings were restored and compared against the saved snapshot. Release credentials and provider configuration were not copied or edited.

## Repeat the interactive check

Install the debug app and its instrumentation APK alongside the configured release app. Enable the release voice keyboard and shortcut service, keep the tablet unlocked, connect the physical keyboard, and leave the system on-screen-keyboard option off.

```bash
adb -s "$TEST_DEVICE_SERIAL" shell am instrument -w -r \
  -e class dev.rankis.openime.PhysicalKeyboardTest \
  -e physical_keyboard true \
  dev.rankis.openime.testbed.test/androidx.test.runner.AndroidJUnitRunner
```

Read the timing report before uninstalling the test app:

```bash
adb -s "$TEST_DEVICE_SERIAL" shell run-as dev.rankis.openime.testbed \
  cat files/physical-keyboard-check.json
```

Follow the three labelled rounds in the blank test editor. The test uses the release app's real microphone and configured server. It records timing and inserted-character counts, and checks keyboard selection, restored visibility, and absence of Enter/editor-action delivery; it does not persist transcript contents or credentials. An optional `-e start_round 2` resumes with the two remaining rounds.
