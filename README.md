# Marantz Remote

A minimal, unofficial Marantz remote. Warm charcoal, gold controls, and a direct connection to your receiver.

**[Windows app](windows/README.md) · [Android app](docs/android.md) · [Browser setup](docs/browser.md) · [Development](CONTRIBUTING.md)**

## Choose your app

| Platform | Start here | Needs another computer? |
| --- | --- | --- |
| Windows 10/11 x64 | [Download a Windows build](https://github.com/Mutairi/marantz-remote/actions/workflows/windows.yml), extract, run `Marantz-Remote.exe` | No |
| Android 8+ | [Download an Android build](https://github.com/Mutairi/marantz-remote/actions/workflows/android.yml), extract, install `app-debug.apk` | No |
| Browser | Run `sh start.sh` with Python 3, then open [the remote](http://127.0.0.1:8765/) | Runs on the computer hosting it |

In GitHub Actions, open a successful run and download its platform artifact. Sign in to GitHub to access this private repository's downloads. Artifacts expire after 30 days; download a newer build or rebuild when needed.

## New in v1.4

**Front-panel lights → LED illumination:** Auto (follow display), Bright (100%), Dim (50%), Dark (25%), and Off, using [Marantz's official CINEMA 40 settings](https://manuals.marantz.com/CINEMA40/NA/EN/DRDZSYsqiciyje.php). Changes affect the receiver's decorative illumination and leave front display brightness unchanged. The app confirms changes by reading back the receiver setting. This requires the newer receiver Web Control API; unsupported or stale status shows Unavailable. Supported on Windows, web, and Android.

**[Download v1.4](https://github.com/Mutairi/marantz-remote/releases/tag/v1.4)** — for Windows, extract the ZIP and run Marantz-Remote.exe. Close the previous version first.

## Speaker diagrams (v1.3)

Separate INPUT and OUTPUT speaker diagrams show the receiver's live channel routing. Active channels glow gold; inactive channels are dimmed. Layout counts can differ (for example 7.1 input and 5.1 output). Surround, back, wide, and height labels follow the receiver. This is channel routing, not a meter of instantaneous sound or proof that a physical speaker is connected. SW is a combined subwoofer indicator, so `.1` does not count physical subwoofers. Missing, unsupported, standby, disconnected, or stale readings show **Unavailable** rather than a guessed layout. New channel readings refresh about every three seconds.

## Controls

All platforms offer power, volume, mute, receiver discovery, input selection, listening modes, and an app volume limit. Windows and browser share the same interface and backend; Android has its own native interface.

| Feature | Browser / Windows | Android |
| --- | --- | --- |
| LED illumination | Yes (1.4) | Yes (1.4) |
| Live input/output channel diagrams | Yes (1.3) | Yes (1.3) |
| Volume slider and app limit | Yes | Yes |
| Gold dial and volume presets | Yes | Yes (1.2) |
| Custom receiver input names | Yes | Yes (1.2) |
| Receiver-reported listening modes | Yes | Yes |
| Tone Control and bass | Yes | Yes (1.2) |
| Audyssey LFC and containment | Yes | Yes (1.2) |
| Link to receiver Web Control | Yes | Yes (1.2) |

Controls depend on receiver capabilities and listening mode. Direct and Pure Direct disable tone and LFC controls. The current implementation has been checked against a CINEMA 40; other models may provide fewer controls.

## Repository layout

```text
index.html, logo.svg     Shared browser / Windows interface
server.py, start.sh      Local receiver service and browser launcher
test_server.py          Receiver and HTTP regression tests
windows/                Windows shell, build dependencies, instructions
android/                Native Android app and Java tests
docs/                   Setup, compatibility, and security notes
.github/workflows/      Separate browser checks and platform builds
```

## Local network connection

Keep the device and receiver on the same trusted network, with receiver network control enabled. Use **Volume limit & connection → Change** on browser/Windows to discover a receiver or enter its private IPv4 address. No Marantz cloud account is needed.

The app limit defaults to **−10 dB** and applies only to commands from this app. It does not change the receiver's own limit or constrain other remotes. See [compatibility and security](docs/security.md).

## Build status and testing

GitHub Actions tests the Python backend, builds and smoke-tests the Windows executable, and tests/lints/builds Android. Windows GUI behavior on the ROG and Android behavior on a real phone still require device testing. Development downloads are unsigned Windows executables and debug-signed Android APKs.

Built by [THROY](https://www.instagram.com/throy/). This project is unofficial and is not affiliated with Marantz. The Marantz wordmark belongs to its owner. No software license has been selected for this repository.
