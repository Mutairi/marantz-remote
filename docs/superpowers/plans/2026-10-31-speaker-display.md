# Live speaker display implementation plan

**Goal:** Add separate INPUT and OUTPUT speaker diagrams to Windows, web, and Android.

**Approved design:** Side-by-side diagrams in the existing charcoal/gold style. Active channels glow gold; inactive channels are dimmed. Show stereo, surround, back, wide, subwoofer, and height channels when reported. Show layout counts where all active labels are recognized. Show Unavailable for missing or stale data. Never infer channels from a listening mode. This indicates channel routing, not instantaneous sound levels.

**Approaches:** Selected two spatial diagrams rather than compact channel rows for readability. Use receiver-reported HTTP channel data rather than sound-mode estimates for correctness.

**Architecture:** Windows/web share server.py and index.html. Android uses ReceiverHttp, ReceiverConnection, and MainActivity. Both backends poll GetInputSignal/inputsigall and GetActiveSpeaker/activespall through AppCommand0300.xml, independently of volume commands. Preserve the newline after the XML declaration: the actual CINEMA 40 returned an empty response without it. Control 2 means active; control 1 means inactive; control 0 means absent. Preserve receiver-provided labels and spatial slots. SW is a combined subwoofer indicator, not a count of physical subwoofers.

## Task 1: Read channel status reliably

Files: server.py, test_server.py, android/app/src/main/java/com/mutairi/marantzremote/ReceiverHttp.java, android/app/src/test/java/com/mutairi/marantzremote/ReceiverHttpTest.java.

- [ ] Add failing fixture tests for stereo, 5.1, 7.1, height channels, separate input/output, empty/malformed responses, and unknown labels.
- [ ] Run Python and Java tests and confirm new assertions fail before implementation.
- [ ] Implement equivalent parsers returning input/output panels with slot, label, active status, and availability. Only control 2 lights a channel. Do not count unknown active labels as a known layout.
- [ ] Verify request formatting against the receiver, using read-only queries only.
- [ ] Run the parser suites and commit.

## Task 2: Refresh status and draw the diagrams

Files: server.py, index.html, android/app/src/main/java/com/mutairi/marantzremote/ReceiverConnection.java, android/app/src/main/java/com/mutairi/marantzremote/MainActivity.java; add a focused Android speaker view if needed.

- [ ] Add tests for partial failures, expiry, disconnect/reconnect, and standby clearing.
- [ ] Poll in a background worker so channel requests do not delay remote controls. Timestamp channel status independently from Telnet status. Reject results from an expired connection or an earlier source/mode change.
- [ ] Draw INPUT and OUTPUT panels using the receiver spatial slots, gold active labels, dim inactive labels, layout captions, and accessible textual descriptions. Keep absent slots empty. Preserve unfamiliar receiver labels safely.
- [ ] Verify mobile and desktop layout, unavailable state, and live CINEMA 40 data without changing playback or settings.
- [ ] Run regression tests and commit.

## Task 3: Validate and package the change

Files: README.md, documentation, build workflows only if required for new bundled files.

- [ ] Document channel routing semantics and combined subwoofer indication.
- [ ] Run Python regression tests, Android unit tests/lint/build where available, and frontend syntax checks.
- [ ] Review the whole change for stale lights, protocol correctness, UI readability, and packaged asset coverage.
- [ ] Produce a reviewable branch and report hardware checks and any unavailable platform build checks accurately. Publishing a release is a separate action.

## Review focus

- Separate input/output results: a 7.1 input may have a 5.1 output.
- Unsupported, missing, or malformed status: Unavailable, never all-off or guessed.
- Standby, disconnection, source/mode changes, or slow replies: no stale gold lights.
- Unknown height/channel labels: preserve labels without inventing a channel count.
- SW/LFE: distinguish input LFE from the combined output subwoofer indicator.

## Hardware evidence

Read-only queries on 2026-10-31 confirmed CINEMA 40 input FL/C/FR/SL/SR/SBL/SBR/LFE active, and output FL/C/FR/SL/SR/SW active. No volume, playback, sound mode, or calibration was changed.
