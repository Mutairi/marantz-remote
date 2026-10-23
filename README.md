# Marantz Remote

A minimal personal remote for compatible Marantz receivers, with two separate apps:

- **Browser:** local Python server plus a browser interface, in the repository root.
- **Android 1.1:** standalone native app in `android/`; no PC or server required.

Both provide power, volume and mute, receiver-reported inputs, listening-mode controls, command confirmation, reconnect handling, and an app volume limit. Discovery depends on the receiver and local-network multicast; manual private IPv4 entry is available. Not every Marantz model exposes the same control APIs.

## Browser

Requires Python 3. Run:

```sh
sh start.sh
```

Open http://127.0.0.1:8765 on the same computer. Use **Change** to search or enter a receiver IP. The saved default for the original installation is `10.10.10.40`. Stop with Ctrl+C. No Python packages or passwords are needed.

The browser server is loopback-only. Host, Origin, request token, XML, command and private-address validation restrict access. It does not expose the receiver to the internet or the rest of the LAN. Local settings live in ignored `.remote-settings.json`.

## Android

Requires Android 8 or newer and the receiver's Wi-Fi network. Download **Marantz-Remote-Android** from the latest successful **Actions → Android APK** run, extract the ZIP, and open `app-debug.apk`. Allow installation from your browser or file manager when Android prompts. Launch **Marantz Remote**, discover a receiver or enter its private IPv4 address.

Version 1.1 includes a volume slider, saved app limit, actual input discovery, receiver-reported mode choices, confirmed/unconfirmed command feedback, reply age and stale-status protection. It disconnects when backgrounded and reconnects when reopened. No cloud account, tracking or background service is used.

This is a test APK, not a Play Store release. Builds use debug signing; a new build may require uninstalling an older test build if the signing key differs. Artifacts expire after 30 days. Android hardware discovery, lifecycle and controls still need a test on your actual phone.

## Listening modes and limits

For indexed mode selection, the app reads the receiver's current HTTP AppCommand0300 genre list, selects the entry by its current index, then reads it back. A generic `OK` alone never confirms the selection. Mode lists are checked again before writing so old choices cannot silently select a different mode after the audio changes. The browser's **Dolby Digital Plus · original** and **Dolby Digital Plus + Dolby Surround** choices were both verified on CINEMA 40 with an active DD+ signal. Other mapped browser controls use TCP port 23; Android uses indexed HTTP mode selection where available and common Direct/Stereo commands as fallback.

The configurable **app volume limit** defaults to −10 dB. It limits increases requested through this app, including direct slider values. It does not change the receiver's own maximum, constrain other controllers, or lower the present volume when edited. A command with no matching confirmation is not replayed automatically.

Receiver network control must be enabled. Capabilities and indexed selection use HTTP port 8080 on tested CINEMA 40; models with different APIs may have reduced support. Guest Wi-Fi isolation, VPN routing, or another controller occupying the receiver connection can prevent access. Receiver control protocols are unencrypted: keep them confined to your trusted LAN.

## Development and tests

Browser regression tests:

```sh
python3 -m unittest -v test_server.py
```

Android requires JDK 17, Android SDK 35 and Gradle 8.11.1:

```sh
cd android
gradle testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions runs the browser tests and Android tests, lint and APK build. Firmware-specific receiver behavior and real Android device operation require hardware verification beyond these checks.

The Marantz wordmark is from the manufacturer's public website. This is a personal, unofficial remote.
