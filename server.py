#!/usr/bin/env python3
import json, secrets, socket, threading, time, re
from collections import deque
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TOKEN = secrets.token_urlsafe(24)
INPUTS = {'TV':'TV audio','BD':'Blu-ray','DVD':'DVD','GAME':'Game','MPLAY':'Media player','CD':'CD','PHONO':'Phono','NET':'Network'}
MODES = {'STEREO':'Stereo','DIRECT':'Direct','PURE DIRECT':'Pure Direct','DOLBY DIGITAL':'Dolby Surround','DTS SURROUND':'DTS Neural:X','MCH STEREO':'Multi Ch Stereo'}
ALLOWED = {'MVUP','MVDOWN','MUON','MUOFF','PWON','PWSTANDBY'} | {'SI'+x for x in INPUTS} | {'MS'+x for x in MODES}

class Receiver:
    def __init__(self, host='10.10.10.40', port=23):
        self.host,self.port=host,port
        self.sock=None
        self.lock=threading.RLock()
        self.state={'connected':False,'power':'Unknown','volume':None,'mute':None,'input':'Unknown','mode':'Unknown','format':'Unknown','error':'Connecting…'}
        self.log=deque(maxlen=80)
        self.last_received=0
    def receive(self,line):
        with self.lock:
            self.last_received=time.monotonic()
            self.log.append({'time':time.strftime('%H:%M:%S'),'text':line})
            if line.startswith('MV') and re.fullmatch(r'\d{2,3}',line[2:]):
                n=line[2:]; self.state['volume']=int(n[:2])+(int(n[2])/10 if len(n)==3 else 0)-80
            elif line in ('MUON','MUOFF'): self.state['mute']=line=='MUON'
            elif line in ('PWON','PWSTANDBY'): self.state['power']='On' if line=='PWON' else 'Standby'
            elif line.startswith('SI'): self.state['input']=line[2:]
            elif line.startswith('MS'): self.state['mode']=line[2:]
            elif line.startswith('SYSMI '): self.state['mode']=line[6:].strip()
            elif line.startswith('SYSDA '): self.state['format']=line[6:].strip()
    def send(self,cmd):
        with self.lock:
            if not self.sock: raise ConnectionError('Receiver is disconnected. Retrying automatically.')
            self.sock.sendall((cmd+'\r').encode('ascii'))
            self.log.append({'time':time.strftime('%H:%M:%S'),'text':'→ '+cmd})
    def run(self):
        while True:
            try:
                s=socket.create_connection((self.host,self.port),3);s.settimeout(1)
                with self.lock:
                    self.sock=s
                    self.last_received=time.monotonic()
                    self.state.update(connected=True,error='',power='Unknown',volume=None,mute=None,input='Unknown',mode='Unknown',format='Unknown')
                buf=b'';last=0
                while True:
                    if time.monotonic()-self.last_received>25:
                        raise ConnectionError('Receiver stopped responding; reconnecting')
                    if time.monotonic()-last>8:
                        for q in ('PW?','MV?','MU?','SI?','MS?'): self.send(q)
                        last=time.monotonic()
                    try: data=s.recv(8192)
                    except socket.timeout: continue
                    if not data: raise ConnectionError('Receiver closed the connection')
                    buf+=data
                    if len(buf)>65536: buf=b''
                    while b'\r' in buf:
                        line,buf=buf.split(b'\r',1)
                        self.receive(line.decode('ascii',errors='replace').strip())
            except (OSError,ConnectionError) as e:
                with self.lock:
                    if self.sock: self.sock.close()
                    self.sock=None;self.state.update(connected=False,error=str(e))
                time.sleep(3)

receiver=Receiver()
class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*a,**kw): super().__init__(*a,directory=str(ROOT),**kw)
    def log_message(self,*a): pass
    def reply(self,status,data):
        raw=json.dumps(data).encode();self.send_response(status)
        self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store')
        self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
    def do_GET(self):
        if self.headers.get('Host') not in ('127.0.0.1:8765','localhost:8765'): return self.reply(403,{'error':'Local access only'})
        if self.path=='/api/status':
            with receiver.lock: return self.reply(200,dict(receiver.state,log=list(receiver.log),token=TOKEN))
        if self.path not in ('/','/index.html','/logo.svg'): return self.reply(404,{'error':'Not found'})
        super().do_GET()
    def do_POST(self):
        if self.headers.get('Host') not in ('127.0.0.1:8765','localhost:8765'): return self.reply(403,{'error':'Local access only'})
        if self.path!='/api/command': return self.reply(404,{'error':'Not found'})
        if self.headers.get('X-Control-Token')!=TOKEN: return self.reply(403,{'error':'Reload this local page'})
        try:
            size=int(self.headers.get('Content-Length','0'))
            if not 0<size<256: raise ValueError('Invalid request')
            body=json.loads(self.rfile.read(size))
            if not isinstance(body,dict): raise ValueError('Expected a JSON object')
            cmd=body.get('command')
            if not isinstance(cmd,str): raise ValueError('Expected a command string')
            if cmd not in ALLOWED: raise ValueError('Unsupported command')
            receiver.send(cmd)
            self.reply(200,{'sent':cmd,'message':'Sent; waiting for receiver status'})
        except (ValueError,TypeError) as e: self.reply(400,{'error':str(e)})
        except (OSError,ConnectionError) as e: self.reply(503,{'error':str(e)})

if __name__=='__main__':
    server=ThreadingHTTPServer(('127.0.0.1',8765),Handler)
    threading.Thread(target=receiver.run,daemon=True).start()
    print('Marantz Remote: http://127.0.0.1:8765',flush=True)
    server.serve_forever()
