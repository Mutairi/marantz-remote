# Android remote

Requires Android 8 or newer and access to the receiver's local network.

1. Open a successful [Android APK workflow run](https://github.com/Mutairi/marantz-remote/actions/workflows/android.yml).
2. Download `Marantz-Remote-Android` from its artifacts and extract the ZIP.
3. Install `app-debug.apk`, then open **Marantz Remote**.
4. Discover a receiver or enter its private IPv4 address.

Android is a native app and does not need the browser server or another PC. It includes volume, mute, power, a saved app volume limit, receiver inputs, and listening modes. It disconnects in the background and reconnects when reopened.

Browser/Windows refinements do not automatically appear in Android. See the [feature table](../README.md#controls) for differences.

This is a development APK with debug signing. Different build signing keys may require uninstalling an older build before installing the new one, which clears saved app settings. Real-phone discovery, lifecycle, and receiver controls still need hardware verification.

[Build instructions](../CONTRIBUTING.md)
