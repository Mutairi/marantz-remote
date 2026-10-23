# Development

Keep the three platforms in this repository. Browser and Windows intentionally share `index.html`, `logo.svg`, and `server.py`; Android is a separate Java implementation. Document platform differences rather than implying every feature is shared.

## Browser / backend

```sh
python3 -m unittest -v test_server.py
sh start.sh
```

Use a trusted local receiver for hardware checks. Unit tests use mocked receiver behavior and do not prove firmware compatibility. Avoid changing playback, volume, or calibration merely to test visual changes.

## Windows

On Windows with Python 3.12:

```powershell
python -m pip install -r windows/requirements.txt
python -m PyInstaller --noconfirm --clean --onefile --windowed --name Marantz-Remote --paths . --add-data "index.html;." --add-data "logo.svg;." windows/app.py
```

Output: `dist/Marantz-Remote.exe`. The Windows workflow also starts the bundled HTTP service with `--smoke-test`. This checks packaged assets and startup, not the WebView GUI. See [Windows instructions](windows/README.md).

## Android

Requires JDK 17, Android SDK 35, and Gradle 8.11.1:

```sh
cd android
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Automation

- `browser.yml`: Python regression checks on backend/test changes.
- `windows.yml`: Windows executable, regression tests, packaged startup check.
- `android.yml`: Android unit tests, lint, and debug APK.

Each workflow also supports manual dispatch. Build products, local settings, credentials, signing keys, and review screenshots must stay out of Git. There is no automatic release publication or code signing.
