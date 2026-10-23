# Marantz Remote

Run `sh start.sh`, then open http://127.0.0.1:8765 on this computer.
Requires Python 3 and network access to CINEMA 40 at 10.10.10.40:23. No packages or passwords needed. Stop with Ctrl+C.

Provides power, master volume, mute, common inputs, listening modes, current status and a scrolling receiver log. Uses one persistent receiver connection and retries automatically. Status comes from receiver responses; a successful send alone does not confirm the requested change. Unsupported sound modes may be ignored by the receiver. Source devices determine the incoming audio format.

The service listens only on this computer. It does not expose the receiver controls to the LAN. Close other Telnet clients if the receiver rejects simultaneous connections. Network Control must be enabled on the receiver for standby access.

The Marantz wordmark is fetched from the manufacturer's public website. This is a personal, unofficial remote.

## Standalone Android app

The `android/` project is a native Android 8+ app. No PC or server is required while using it. Connect your phone to the receiver's Wi-Fi, launch **Marantz Remote**, and choose a discovered receiver. Discovery uses SSDP/UPnP and filters Marantz device descriptions. Manual private IPv4 entry is available if multicast discovery is blocked. Receivers must support the Denon/Marantz port-23 network control protocol; not every Marantz product does. Enable Network Control on your receiver. Guest Wi-Fi isolation and some VPNs can block access.

Download the APK from **Actions → Android APK → latest successful run → Marantz-Remote-Android**. Extract the ZIP and open `app-debug.apk` on your phone. Android will ask you to allow installation from your browser or file manager. This is a test APK, not a Play Store release. GitHub artifacts expire after 30 days. Fresh debug signing keys may require uninstalling an older test build before installing a newer one. No passwords, cloud accounts, tracking, or background service are used. The app disconnects when backgrounded and reconnects when opened.

Build locally with JDK 17, Android SDK 35 and Gradle 8.11.1: `cd android && gradle testDebugUnitTest lintDebug assembleDebug`. GitHub Actions performs the same build. Test on your actual Android phone before relying on compatibility with a particular receiver.
