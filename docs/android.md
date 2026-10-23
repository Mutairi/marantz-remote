# Android remote

Requires Android 8 or newer and access to the receiver's local network.

1. Open a successful [Android APK workflow run](https://github.com/Mutairi/marantz-remote/actions/workflows/android.yml).
2. Download `Marantz-Remote-Android` from its artifacts and extract the ZIP.
3. Install `app-debug.apk`, then open **Marantz Remote**.
4. Discover a receiver or enter its private IPv4 address.

Android is a native app and does not need the browser server or another PC. It includes volume, mute, power, a saved app volume limit, receiver inputs, and listening modes. It disconnects in the background and reconnects when reopened.

Version 1.2 brings the gold volume dial, seven volume presets, custom input names, inline Tone Control and bass, Audyssey LFC with containment levels, MORE SETTINGS, and the THROY Instagram footer to the native Android interface. Volume and bass accept queued taps while confirmation runs. Receiver and app-limit settings are grouped below the main controls.

Android uses native buttons and selection dialogs, so it follows the same design and controls without being a pixel-identical browser wrapper. Tone and LFC controls are disabled in Direct/Pure Direct, and levels remain receiver-confirmed.

This is a development APK with debug signing. Different build signing keys may require uninstalling an older build before installing the new one, which clears saved app settings. Real-phone discovery, lifecycle, and receiver controls still need hardware verification.

[Build instructions](../CONTRIBUTING.md)
