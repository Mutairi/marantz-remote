# Marantz Remote for Windows

A compact standalone window with the same gold remote interface. No browser address bar, terminal, Python installation, or connection to the X1 is needed.

1. Download the `Marantz-Remote-Windows` artifact from the Windows App workflow in GitHub Actions and extract the ZIP on the ROG.
2. Run `Marantz-Remote.exe` on Windows 10/11 (x64). It uses Microsoft Edge WebView2 Runtime, available from https://developer.microsoft.com/microsoft-edge/webview2/ if missing.
3. Keep the ROG and receiver on the same local network. Under **Volume limit & connection**, use **Change** to search for a Marantz or enter its address.
4. Pin the running app to the taskbar if desired. Close the window to stop its local server.

The app remembers the receiver and volume limit in `%LOCALAPPDATA%\Marantz Remote`. Its control service listens only on `127.0.0.1:8765`. Another instance or browser server using that port must be closed first. MORE SETTINGS and Instagram open through the system browser.

The executable is unsigned, so Windows may display a reputation warning. No administrator rights are required. A successful CI build checks the Windows executable and local HTTP service; testing the WebView window and receiver connection on the ROG is still required.

## Build from source on Windows

```powershell
python -m pip install -r windows/requirements.txt
python -m PyInstaller --noconfirm --clean --onefile --windowed --name Marantz-Remote --paths . --add-data "index.html;." --add-data "logo.svg;." windows/app.py
```

Output: `dist/Marantz-Remote.exe`.
