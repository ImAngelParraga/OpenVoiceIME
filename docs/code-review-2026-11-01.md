# Code review: 2026-11-01

Reviewed recording/audio focus, IME lifecycle and editor operations, settings and encrypted storage, network clients/parsers, metrics, gesture controllers, resources, and release configuration. The changes ship in v0.3.15.

## Stage 1: lifecycle and editor compatibility

| Problem | Result |
|---|---|
| Creating an input view started capture before the view was shown. | Capture starts only after the input view is shown and the session is idle. |
| Hide/rebuild cleanup deleted pending audio and transcripts. | View detachment preserves recovery data; hide finalizes capture and cancels upload. Rebuild keeps an eligible upload. Explicit discard and final destruction clear owned work. |
| Reopening reset controls to Recording despite retained work. | Controls render the current session: saved audio offers Send audio, pending text offers insertion or copying, and errors remain retryable. |
| Async delivery could target a changed editor or hidden keyboard. | Automatic insertion requires the original visible editor session. Explicit Insert here can target the current field. Late cancelled replies cannot insert. |
| Accepted commits could truncate/filter text and lose the full transcript. | Comparable snapshots must match the expected replacement; otherwise the full transcript/audio remain recoverable. |
| Existing composing text was overwritten by dictation. | Composition is finished before insertion. Optional selection failure never repeats an insertion. |
| Invalid extracted offsets were clamped into plausible positions. | Insertion and gestures share validated snapshots; partial offsets translate once. Missing snapshots retain terminal-compatible insertion. |
| Vertical escape on release lost its selection-restore command. | Escape returns the restore command; hiding restores an owned preview without changing a newly bound editor. |
| Process death could leave recordings in cache. | A later service start removes only abandoned recording files matching its own filename pattern. |

## Stage 2: settings, storage, and concurrency

| Problem | Result |
|---|---|
| Posted initial spinner callbacks overwrote saved connection/model values. | Initial callbacks recognize the already-bound preset and preserve saved values. |
| Old connection-test responses marked a changed form reachable. | Changed settings and newer probes cancel/invalidate older requests; completion checks both request generation and connection fingerprint. |
| Switching built-in providers retained another provider's credential. | Built-in switches clear the previous credential before discovery/testing; saved presets load their own token. |
| Auto language reactivated obsolete legacy language settings. | An explicit modern Auto choice persists and overrides migration fallback. |
| Replacing a preset left old encrypted tokens behind; timestamp IDs could collide. | Replacement preserves identity, new IDs use UUIDs, and orphaned encrypted token keys are removed. |
| Concurrent metrics lost read-modify-write increments. | All store instances share a process lock for updates, snapshots, and reset. The regression reproduced 25 counted writes out of 100 before the fix. |

## Stage 3: deadlines, network, and verification

The total transcription deadline is now `clamp(5 seconds + audio duration, 10 seconds, 15 minutes)`: 3-second audio gets 10 seconds, 10-second audio gets 15 seconds, and one-minute audio gets 65 seconds. It covers upload, server wait, and complete response body. Audio remains available after timeout. Model catalogs reject malformed/non-string IDs.

Android instrumentation uses a separate debug application ID, `dev.rankis.openime.testbed`. It does not replace or select the installed production keyboard. Lifecycle tests use controlled capture/server completion; editor tests use real Android EditText input connections. Settings/network tests use local HTTP servers; storage and concurrent metrics tests use Android implementations. Long recovery content is measured at 320dp width with font scales 1.0 and 1.3, checking action bounds.

Validation: 104 JVM tests and 24 Android integration tests on Samsung SM-P610, Android 13/API 33; debug lint with zero errors; signed release build and APK signature verification. Several lifecycle assertions previously implemented as source inspection are now covered by Android behavior tests.

## Limits and follow-up

The instrumentation harness invokes real service callbacks with controlled dependencies; it does not exercise every system IME binding path, third-party editor, Android version, physical microphone, or remote transcription model. Editors without readable text still rely on their acceptance signal. Ambiguous insertion requires checking the field before manual retry to avoid duplicates.

The remaining lint warnings include dependency/target-version notices, style/icon warnings, and settings accessibility/nested-scroll warnings. They are not a claim of verified runtime correctness. The service still combines multiple responsibilities; further structural extraction should preserve the integration tests and be scoped separately from these behavior fixes.
