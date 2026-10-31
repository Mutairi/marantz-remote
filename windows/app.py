"""Small Windows shell for the shared local Marantz remote."""
import ctypes
import os
from pathlib import Path
import sys
import threading
import urllib.request

if not getattr(sys, 'frozen', False):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def main():
    data = Path(os.environ.get('LOCALAPPDATA', str(Path.home()))) / 'Marantz Remote'
    data.mkdir(parents=True, exist_ok=True)
    os.environ['MARANTZ_DATA_DIR'] = str(data)
    import server
    try:
        http = server.ThreadingHTTPServer(('127.0.0.1', 8765), server.Handler)
    except OSError as error:
        raise RuntimeError('Port 8765 is already in use. Close the other Marantz Remote window or local server, then try again.') from error
    worker = threading.Thread(target=http.serve_forever, daemon=True)
    worker.start()
    try:
        if '--smoke-test' in sys.argv:
            import json
            with urllib.request.urlopen('http://127.0.0.1:8765/api/status', timeout=5) as response:
                assert 'token' in json.load(response)
            with urllib.request.urlopen('http://127.0.0.1:8765/', timeout=5) as response:
                page = response.read()
                assert b'MORE SETTINGS' in page
                assert b'illumination' in page
                assert b'inputSpeakers' in page and b'outputSpeakers' in page and b'v1.4' in page
            return
        import webview
        threading.Thread(target=server.app.receiver.run, daemon=True).start()
        webview.create_window('Marantz Remote', 'http://127.0.0.1:8765/',
                              width=490, height=900, min_size=(360, 600),
                              background_color='#131311', text_select=False)
        webview.start(gui='edgechromium', debug=False)
    finally:
        server.app.receiver.close()
        http.shutdown()
        http.server_close()
        worker.join(timeout=3)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        if sys.platform == 'win32' and '--smoke-test' not in sys.argv:
            ctypes.windll.user32.MessageBoxW(0, str(error) + '\n\nThis app requires Microsoft Edge WebView2 Runtime.', 'Marantz Remote', 0x10)
        raise
