#!/usr/bin/env python3
"""Local Marantz remote, review version. Python standard library only."""
import concurrent.futures, ipaddress, json, math, os, re, secrets, socket, threading, time
import urllib.request, urllib.parse
import xml.etree.ElementTree as ET
from collections import deque
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
ROOT=Path(__file__).resolve().parent
TOKEN=secrets.token_urlsafe(24)
SETTINGS=Path(os.environ.get('MARANTZ_DATA_DIR',str(ROOT)))/'.remote-settings.json'
INPUT_CODES={'CBL/SAT':'SAT/CBL','MEDIA PLAYER':'MPLAY','BLU-RAY':'BD','DVD':'DVD','GAME':'GAME','GAME1':'GAME','GAME2':'GAME2','AUX1':'AUX1','AUX2':'AUX2','TV AUDIO':'TV','CD':'CD','PHONO':'PHONO','TUNER':'TUNER','NETWORK':'NET','BLUETOOTH':'BT'}
MODE_CODES={'STEREO':'STEREO','DIRECT':'DIRECT','PURE DIRECT':'PURE DIRECT','MULTI CH STEREO':'MCH STEREO','MULTI CH IN':'MULTI CH IN','DOLBY ATMOS':'DOLBY ATMOS','DOLBY AUDIO - DOLBY DIGITAL PLUS':'DOLBY DIGITAL +','DOLBY ATMOS/DSURR':'DOLBY DIGITAL','DOLBY SURROUND':'DOLBY AUDIO-DSUR','DTS SURROUND':'DTS SURROUND','DTS NEURAL:X':'DTS SURROUND'}
def private_ip(value):
    try:
        ip=ipaddress.IPv4Address(value)
        return any(ip in ipaddress.ip_network(n) for n in ('10.0.0.0/8','172.16.0.0/12','192.168.0.0/16'))
    except (ValueError,TypeError): return False
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args): return None
def receiver_http(url, data=None):
    p=urllib.parse.urlsplit(url)
    if p.scheme!='http' or not private_ip(p.hostname) or p.username or p.password: raise ValueError('Invalid device URL')
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
    request=urllib.request.Request(url,data=data)
    with opener.open(request,timeout=1.5) as r: data=r.read(262145)
    if len(data)>262144: raise ValueError('Oversized receiver response')
    return data
def read_xml(url, data=None):
    data=receiver_http(url,data)
    if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper(): raise ValueError('Invalid device XML')
    root=ET.fromstring(data)
    for node in root.iter(): node.tag=node.tag.split('}')[-1]
    return root
ILLUMINATION={'1':'Auto','2':'Bright','3':'Dim','4':'Dark','5':'Off'}
def read_illumination(host,port):
    if port not in (11080,80): raise ValueError('Unsupported Web Control port')
    root=read_xml(f'http://{host}:{port}/ajax/general/get_config?type=18')
    value=root.findtext('IlluminationBrightness','').strip()
    if root.tag!='FrontPanel' or value not in ILLUMINATION: raise ValueError('LED illumination unavailable')
    return value
def write_illumination(host,port,value):
    if value not in ILLUMINATION or port not in (11080,80): raise ValueError('Invalid LED illumination')
    query=urllib.parse.urlencode({'type':'18','data':f'<IlluminationBrightness>{value}</IlluminationBrightness>'})
    receiver_http(f'http://{host}:{port}/ajax/general/set_config?{query}')

def renamed_inputs(host, port):
    data=b'<?xml version="1.0" encoding="utf-8"?>\n<tx><cmd id="1">GetRenameSource</cmd></tx>'
    root=read_xml(f'http://{host}:{port}/goform/AppCommand.xml',data)
    return {INPUT_CODES[item.findtext('name','').upper()]:item.findtext('rename','').strip()
            for item in root.findall('.//functionrename/list')
            if item.findtext('name','').upper() in INPUT_CODES and item.findtext('rename','').strip()}

def capabilities(host):
    if not private_ip(host): return None
    for port in (8080,80):
        try:
            root=read_xml(f'http://{host}:{port}/goform/Deviceinfo.xml')
            if root.tag!='Device_Info' or root.findtext('BrandCode')!='1': continue
            inputs=[]
            for zone in root.findall('DeviceZoneCapabilities'):
                if zone.findtext('Zone/No')!='0': continue
                for source in zone.findall('InputSource/List/Source'):
                    code=INPUT_CODES.get(source.findtext('FuncName','').upper())
                    if code: inputs.append({'value':code,'label':source.findtext('DefaultName') or code})
            try:
                names=renamed_inputs(host,port)
                for item in inputs: item['label']=names.get(item['value'],item['label'])
            except (OSError,ValueError,ET.ParseError): pass
            return {'host':host,'name':root.findtext('ModelName') or 'Marantz','inputs':inputs,'control_port':port}
        except (OSError,ValueError,ET.ParseError): pass
    return None
def mode_key(value):
    # Preserve '+' before stripping punctuation: codec DD+ and an added
    # upmixer must not be confused with plain Dolby Digital.
    key=re.sub('[^A-Z0-9]','',value.upper().replace('+','PLUS'))
    return (key.replace('MULTICHSTEREO','MCHSTEREO')
               .replace('DOLBYDIGITALPLUS','DDPLUS')
               .replace('DSURR','DSUR'))
def volume_command(db):
    n=round((db+80)*2)
    return f'MV{n//2:02d}'+('5' if n%2 else '')

def sound_mode_call(host, port, name, params):
    if name not in ('GetSoundModeList','SetSoundModeList'):
        raise ValueError('Unsupported HTTP receiver command')
    root=ET.Element('tx');cmd=ET.SubElement(root,'cmd',id='3')
    ET.SubElement(cmd,'name').text=name
    items=ET.SubElement(cmd,'list')
    for key,value in params.items(): ET.SubElement(items,'param',name=key).text=value
    # Match the receiver's request format; text/xml requests can be ignored.
    data=ET.tostring(root,encoding='utf-8',xml_declaration=True)
    response=read_xml(f'http://{host}:{port}/goform/AppCommand0300.xml',data)
    if response.find('.//error') is not None: raise ValueError('Receiver rejected the listening-mode request')
    return response

def sound_mode_list(root):
    genre=root.findtext(".//param[@name='genrelist']")
    values=[]
    for item in root.findall('.//value'):
        number=item.findtext('listno','')
        label=item.findtext('dispname','').strip()
        if number.isdigit() and label:
            values.append({'number':number,'label':label,'selected':item.findtext('selected')=='1'})
    if not genre or not values: raise ValueError('Receiver did not provide its current listening-mode list')
    return genre,values

BED_CHANNELS={'FL','FR','C','SL','SR','SBL','SBR','SB','FWL','FWR'}
HEIGHT_CHANNELS={'FHL','FHR','SHL','SHR','RHL','RHR','TFL','TFR','TML','TMR','TRL','TRR','TS','CH','TOP','FDL','FDR','SDL','SDR','BDL','BDR'}
SUB_CHANNELS={'LFE','SW','SW1','SW2','SW3','SW4'}
def channel_panel(root,name,prefix):
    """Preserve receiver slots; 2 is active, 1 inactive, 0 absent."""
    cmd=next((c for c in root.findall('cmd') if c.findtext('name')==name),None)
    if cmd is None or cmd.find('error') is not None: return None
    params=cmd.findall('list/param')
    if not params: return None
    channels=[];seen=set()
    for p in params:
        key=p.get('name','')
        if not re.fullmatch(re.escape(prefix)+r'[a-e][1-4]',key): return None
        slot=key[len(prefix):];control=p.get('control');label=(p.text or '').strip()
        if slot in seen or control not in ('0','1','2') or len(label)>32: return None
        seen.add(slot)
        if control=='0': continue
        if not label: return None
        channels.append({'slot':slot,'label':label,'active':control=='2'})
    active={c['label'].upper() for c in channels if c['active']}
    if not active: layout='No active channels'
    elif active-(BED_CHANNELS|HEIGHT_CHANNELS|SUB_CHANNELS): layout=None
    else:
        bed=len(active&BED_CHANNELS);height=len(active&HEIGHT_CHANNELS)
        subs=len(active&SUB_CHANNELS)
        layout=f'{bed}.{subs}'+(f'.{height}' if height else '')
    return {'channels':channels,'layout':layout}

def read_channel_panel(host,port,name,prefix):
    if (name,prefix) not in (('GetInputSignal','inputsig'),('GetActiveSpeaker','activesp')):
        raise ValueError('Unsupported channel query')
    data=(f'<?xml version="1.0" encoding="utf-8"?>\n<tx><cmd id="3"><name>{name}</name><list><param name="{prefix}all"></param></list></cmd></tx>').encode()
    return channel_panel(read_xml(f'http://{host}:{port}/goform/AppCommand0300.xml',data),name,prefix)

class Receiver:
    def __init__(self,host='10.10.10.40',port=23):
        self.host,self.port=host,port;self.sock=None;self.lock=threading.RLock();self.stop=threading.Event()
        self.state={'connected':False,'power':'Unknown','volume':None,'mute':None,'tone':None,'bass':None,'lfc':None,'containment':None,'input':'Unknown','mode':'Unknown','format':'Unknown','error':'Connecting…','inputs':[],'modes':[],'name':'Marantz','host':host,'last_update':None,'command':None}
        self.log=deque(maxlen=100);self.last_received=0;self.volume_received=0;self.mode_batch={};self.control_port=8080
        self.state['channels']={'input':None,'output':None}
        self.state.update(illumination=None,illumination_updated_at=None)
        self.illumination_port=11080;self.illumination_epoch=0
        self.channel_epoch=0;self.channel_wake=threading.Event()
    def invalidate_channels(self):
        # Caller holds lock; incrementing also rejects HTTP replies already in flight.
        self.channel_epoch+=1;self.state['channels']={'input':None,'output':None};self.channel_wake.set()
    def refresh_channels(self):
        with self.lock:
            if not self.state['connected'] or self.state['power']!='On': return
            epoch=self.channel_epoch
        for key,name,prefix in (('input','GetInputSignal','inputsig'),('output','GetActiveSpeaker','activesp')):
            try: panel=read_channel_panel(self.host,self.control_port,name,prefix)
            except (OSError,ValueError,ET.ParseError): panel=None
            with self.lock:
                if self.stop.is_set() or epoch!=self.channel_epoch: return
                self.state['channels'][key]=dict(panel,updated_at=time.time()) if panel is not None else None
    def refresh_illumination(self):
        with self.lock:
            if not self.state['connected'] or self.state['power']!='On': return
            epoch,lighting_epoch=self.channel_epoch,self.illumination_epoch
        value=None;port=self.illumination_port
        for candidate in dict.fromkeys((port,11080,80)):
            try: value=read_illumination(self.host,candidate);port=candidate;break
            except (OSError,ValueError,ET.ParseError): pass
        with self.lock:
            if self.stop.is_set() or epoch!=self.channel_epoch or lighting_epoch!=self.illumination_epoch: return
            self.illumination_port=port;self.state.update(illumination=value,illumination_updated_at=time.time() if value else None)
    def poll_channels(self):
        while not self.stop.is_set():
            self.channel_wake.clear();self.refresh_channels();self.refresh_illumination();self.channel_wake.wait(3)
    def snapshot(self):
        with self.lock:
            p=self.state['command']
            if p and p['status']=='pending' and time.monotonic()-p['started']>5: p.update(status='unconfirmed',message=('Requested mode was not confirmed. Receiver reports: '+self.state['mode'] if p['field']=='mode' else 'No matching reply. The receiver may have ignored this command.'))
            fresh=self.state['connected'] and self.state['power']=='On' and time.monotonic()-self.last_received<12
            channels={key:panel if fresh and panel is not None and time.time()-panel['updated_at']<10 else None for key,panel in self.state['channels'].items()}
            return dict(self.state,channels=channels,illumination=self.state['illumination'] if fresh and self.state['illumination_updated_at'] and time.time()-self.state['illumination_updated_at']<12 else None,log=list(self.log))
    def receive(self,line):
        if not line: return
        with self.lock:
            before=(self.state['power'],self.state['input'],mode_key(self.state['mode']),mode_key(self.state['format']))
            self.last_received=time.monotonic();self.state['last_update']=time.time()
            self.log.append({'time':time.strftime('%H:%M:%S'),'text':line});field=None
            if re.fullmatch(r'MV\d{2,3}',line):
                self.volume_received=time.monotonic();n=line[2:];self.state['volume']=int(n[:2])+(int(n[2])/10 if len(n)==3 else 0)-80;field='volume'
            elif line in ('MUON','MUOFF'): self.state['mute']=line=='MUON';field='mute'
            elif line in ('PWON','PWSTANDBY'): self.state['power']='On' if line=='PWON' else 'Standby';field='power'
            elif line in ('PSLFC ON','PSLFC OFF'): self.state['lfc']=line.endswith(' ON');field='lfc'
            elif re.fullmatch(r'PSCNTAMT 0[1-7]',line): self.state['containment']=int(line[9:]);field='containment'
            elif line in ('PSTONE CTRL ON','PSTONE CTRL OFF'): self.state['tone']=line.endswith(' ON');field='tone'
            elif re.fullmatch(r'PSBAS \d{2}',line): self.state['bass']=int(line[6:])-50;field='bass'
            elif line.startswith('SI'): self.state['input']=line[2:];field='input'
            elif line.startswith('MS') and not line.startswith('MSQUICK'): self.state['mode']=line[2:];field='mode'
            elif line.startswith('SYSMI '): self.state['mode']=line[6:].strip();field='mode'
            elif line.startswith('SYSDA '): self.state['format']=line[6:].strip()
            elif line=='OPSMLALL END': self.state['modes']=list(self.mode_batch.values());self.mode_batch={}
            elif line.startswith('OPSMLALL '):
                m=re.fullmatch(r'OPSMLALL (?:MOV|MUS|GAM|PUR)\d{3}(.+)',line)
                if m:
                    label=m[1].strip();code=MODE_CODES.get(label.upper())
                    if code:
                        aliases=([label,'DOLBY ATMOS','DOLBY AUDIO-DSUR','DOLBY AUDIO-DD+ +DSUR','DOLBY AUDIO-DD +DSUR','DOLBY TRUEHD +DSUR'] if label.upper()=='DOLBY ATMOS/DSURR' else [label])
                        display=label
                        if label.upper()=='DOLBY AUDIO - DOLBY DIGITAL PLUS': display='Dolby Digital Plus · original'
                        elif label.upper()=='DOLBY ATMOS/DSURR' and 'DDPLUS' in mode_key(self.state['format']): display='Dolby Digital Plus + Dolby Surround'
                        self.mode_batch[code]={'value':code,'label':display,'aliases':aliases}
            if before[0]!=self.state['power']:
                self.illumination_epoch+=1;self.state.update(illumination=None,illumination_updated_at=None)
            if before!=(self.state['power'],self.state['input'],mode_key(self.state['mode']),mode_key(self.state['format'])): self.invalidate_channels()
            p=self.state['command']
            if field and p and p['status']=='pending' and p['field']==field:
                actual,expected=self.state[field],p['expected']
                if (mode_key(actual) in {mode_key(x) for x in p.get('aliases',[expected])} if field=='mode' else actual==expected): p.update(status='confirmed',message='Confirmed by receiver')
    def send(self,cmd):
        with self.lock:
            if not self.sock: raise ConnectionError('Receiver is disconnected')
            self.sock.sendall((cmd+'\r').encode('ascii'));self.log.append({'time':time.strftime('%H:%M:%S'),'text':'→ '+cmd})
    def select_indexed_mode(self,pending):
        genre,items=sound_mode_list(sound_mode_call(self.host,self.control_port,'GetSoundModeList',{'genrelist':''}))
        aliases={mode_key(x) for x in pending['aliases']}
        target=next((item for item in items if mode_key(item['label']) in aliases),None)
        if target is None: raise ValueError('Available modes changed. Please select again for the current audio.')
        pending['wire']='HTTP SetSoundModeList '+target['number']
        if not target['selected']:
            sound_mode_call(self.host,self.control_port,'SetSoundModeList',{'listno':target['number']})
            self.log.append({'time':time.strftime('%H:%M:%S'),'text':'→ Select mode: '+target['label']})
            time.sleep(.2)
        after_genre,after_items=sound_mode_list(sound_mode_call(self.host,self.control_port,'GetSoundModeList',{'genrelist':''}))
        if after_genre==genre and any(item['number']==target['number'] and item['label']==target['label'] and item['selected'] for item in after_items):
            if mode_key(self.state['mode'])!=mode_key(target['label']): self.invalidate_channels()
            self.state['mode']=target['label']
            pending.update(status='confirmed',message='Confirmed by receiver')
        else:
            pending.update(status='unconfirmed',message='Receiver did not confirm the selected mode. No automatic retry was made.')
    def execute(self,cmd,limit):
        with self.lock:
            self.snapshot();p=self.state['command']
            if p and p['status']=='pending': raise ValueError('Wait for the current command to finish')
            if not self.state['connected'] or time.monotonic()-self.last_received>12: raise ConnectionError('Waiting for fresh receiver status')
            original=cmd
            if cmd in ('MVUP','MVDOWN') or re.fullmatch(r'MV\d{2,3}',cmd):
                current=self.state['volume']
                if current is None or time.monotonic()-self.volume_received>12: raise ValueError('Waiting for a fresh volume reading')
                if cmd in ('MVUP','MVDOWN'): target=current+(.5 if cmd=='MVUP' else -.5)
                else:
                    n=cmd[2:];target=int(n[:2])+(int(n[2])/10 if len(n)==3 else 0)-80
                if not -80<=target<=18 or target*2!=round(target*2): raise ValueError('Invalid volume')
                if target>limit and target>current: raise ValueError(f'Volume limit reached ({limit:g} dB)')
                cmd=volume_command(target);field='volume';expected=target
            elif cmd in ('PSLFC ON','PSLFC OFF') or re.fullmatch(r'PSCNTAMT 0[1-7]',cmd):
                if mode_key(self.state['mode']) in ('PUREDIRECT','DIRECT'): raise ValueError('Audyssey LFC is unavailable in Direct or Pure Direct')
                if self.state['lfc'] is None: raise ValueError('Waiting for receiver LFC status')
                if cmd.startswith('PSLFC'): field='lfc';expected=cmd.endswith(' ON')
                else:
                    if not self.state['lfc']: raise ValueError('Enable Audyssey LFC first')
                    field='containment';expected=int(cmd[9:])
            elif cmd in ('PSTONE CTRL ON','PSTONE CTRL OFF','PSBAS UP','PSBAS DOWN'):
                if mode_key(self.state['mode']) in ('PUREDIRECT','DIRECT'): raise ValueError('Tone controls are unavailable in Direct or Pure Direct')
                if self.state['tone'] is None: raise ValueError('Waiting for receiver tone status')
                if cmd.startswith('PSTONE'):
                    field='tone';expected=cmd.endswith(' ON')
                else:
                    if not self.state['tone'] or self.state['bass'] is None: raise ValueError('Enable Tone Control before adjusting bass')
                    expected=self.state['bass']+(1 if cmd.endswith('UP') else -1)
                    if not -6<=expected<=6: raise ValueError('Bass limit reached')
                    cmd=f'PSBAS {expected+50:02d}';field='bass'
            elif re.fullmatch(r'IL[1-5]',cmd):
                if self.snapshot()['illumination'] is None: raise ValueError('Waiting for fresh LED illumination status')
                field='illumination';expected=cmd[2:]
            elif cmd in ('MUON','MUOFF'): field='mute';expected=cmd=='MUON'
            elif cmd in ('PWON','PWSTANDBY'): field='power';expected='On' if cmd=='PWON' else 'Standby'
            elif cmd.startswith('SI') and cmd[2:] in {x['value'] for x in self.state['inputs']}: field='input';expected=cmd[2:]
            elif cmd.startswith('MS') and cmd[2:] in {x['value'] for x in self.state['modes']}: field='mode';expected=cmd[2:]
            else: raise ValueError('This control has not been advertised by the receiver')
            p={'id':secrets.token_hex(6),'action':original,'wire':cmd,'status':'pending','message':'Waiting for receiver confirmation…','field':field,'expected':expected,'started':time.monotonic()}
            if field=='mode':
                p['aliases']=[expected]+[alias for x in self.state['modes'] if x['value']==expected for alias in x.get('aliases',[x['label']])]
            self.state['command']=p
            try:
                if field=='illumination':
                    self.illumination_epoch+=1
                    write_illumination(self.host,self.illumination_port,expected)
                    actual=read_illumination(self.host,self.illumination_port)
                    self.state.update(illumination=actual,illumination_updated_at=time.time())
                    p.update(status='confirmed' if actual==expected else 'unconfirmed',message='Confirmed by receiver' if actual==expected else 'Receiver did not confirm the LED setting. No automatic retry was made.')
                    return p['id']
                if field=='mode' and expected in ('DOLBY DIGITAL +','DOLBY DIGITAL'):
                    self.select_indexed_mode(p)
                else:
                    self.send(cmd)
                self.send({'volume':'MV?','mute':'MU?','power':'PW?','input':'SI?','mode':'MS?','tone':'PSTONE CTRL ?','bass':'PSBAS ?','lfc':'PSLFC ?','containment':'PSCNTAMT ?'}[field])
            except (OSError,ValueError,ET.ParseError) as error:
                if field=='illumination': self.state.update(illumination=None,illumination_updated_at=None)
                p.update(status='unconfirmed',message='Selection could not be confirmed. No automatic retry was made.')
                raise ValueError(str(error)) from error
            return p['id']
    def close(self):
        self.stop.set();self.channel_wake.set()
        with self.lock:
            self.invalidate_channels()
            if self.sock:
                try: self.sock.shutdown(socket.SHUT_RDWR)
                except OSError: pass
                self.sock.close();self.sock=None
    def load_capabilities(self):
        info=capabilities(self.host)
        if info:
            with self.lock:
                self.control_port=info['control_port']
                self.state.update(name=info['name'],inputs=info['inputs'])
        while not self.stop.wait(30):
            try:
                names=renamed_inputs(self.host,self.control_port)
                with self.lock:
                    self.state['inputs']=[dict(item,label=names.get(item['value'],item['label'])) for item in self.state['inputs']]
            except (OSError,ValueError,ET.ParseError): pass
    def run(self):
        threading.Thread(target=self.load_capabilities,daemon=True).start()
        threading.Thread(target=self.poll_channels,daemon=True).start()
        while not self.stop.is_set():
            try:
                with socket.create_connection((self.host,self.port),3) as s:
                    s.settimeout(.5)
                    with self.lock:
                        if self.stop.is_set(): return
                        self.sock=s;self.last_received=time.monotonic();self.state.update(illumination=None,illumination_updated_at=None,connected=True,error='',power='Unknown',volume=None,mute=None,input='Unknown',mode='Unknown',format='Unknown',last_update=None,modes=[]);self.mode_batch={}
                        self.invalidate_channels()
                    buf=b'';last=0
                    while not self.stop.is_set():
                        if time.monotonic()-self.last_received>25: raise ConnectionError('Receiver stopped responding; reconnecting')
                        if time.monotonic()-last>8:
                            for q in ('PW?','MV?','MU?','SI?','MS?','OPSMLALL ?','PSTONE CTRL ?','PSBAS ?','PSLFC ?','PSCNTAMT ?'): self.send(q)
                            last=time.monotonic()
                        try: data=s.recv(8192)
                        except socket.timeout: continue
                        if not data: raise ConnectionError('Receiver closed the connection')
                        buf+=data
                        if len(buf)>65536: raise ConnectionError('Invalid receiver response')
                        while b'\r' in buf:
                            line,buf=buf.split(b'\r',1);self.receive(line.decode('ascii',errors='replace').strip())
            except (OSError,ConnectionError) as error:
                with self.lock:
                    self.invalidate_channels()
                    self.sock=None;self.state.update(illumination=None,illumination_updated_at=None,connected=False,error=str(error));p=self.state['command']
                    if p and p['status']=='pending': p.update(status='unconfirmed',message='Disconnected before confirmation. Command will not be replayed.')
                self.stop.wait(2)
class App:
    def __init__(self):
        self.lock=threading.RLock()
        try:
            config=json.loads(SETTINGS.read_text())
            if not isinstance(config,dict): config={}
        except (OSError,ValueError): config={}
        self.host=config.get('host','10.10.10.40')
        if not private_ip(self.host): self.host='10.10.10.40'
        limit=config.get('limit',-10)
        self.limit=float(limit) if isinstance(limit,(int,float)) and math.isfinite(limit) and -60<=limit<=0 else -10
        self.receiver=Receiver(self.host);self.devices={self.host:{'host':self.host,'name':'Saved Marantz'}};self.scanning=False;self.scan_error=''
    def save(self):
        temp=SETTINGS.with_suffix('.tmp');temp.write_text(json.dumps({'host':self.host,'limit':self.limit}));temp.replace(SETTINGS)
    def select(self,host):
        if not private_ip(host): raise ValueError('Enter a private IPv4 address')
        info=capabilities(host)
        if not info: raise ValueError('Could not verify a Marantz receiver here. Check Network Control and the IP address.')
        with self.lock:
            self.receiver.close();self.receiver=Receiver(host);self.receiver.state.update(name=info['name'],inputs=info['inputs'])
            self.host=host;self.devices[host]=info;self.save();threading.Thread(target=self.receiver.run,daemon=True).start()
    def discover(self):
        with self.lock:
            if self.scanning: return
            self.scanning=True;self.scan_error=''
        def worker():
            try:
                locations=set()
                with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as udp:
                    udp.settimeout(.4)
                    for target in ('ssdp:all','urn:schemas-upnp-org:device:MediaRenderer:1'):
                        msg=f'M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: "ssdp:discover"\r\nMX: 2\r\nST: {target}\r\n\r\n';udp.sendto(msg.encode(),('239.255.255.250',1900))
                    end=time.monotonic()+4
                    while time.monotonic()<end and len(locations)<32:
                        try: data,peer=udp.recvfrom(16384)
                        except socket.timeout: continue
                        if not private_ip(peer[0]): continue
                        for line in data.decode(errors='replace').splitlines():
                            if line.lower().startswith('location:'):
                                url=line.split(':',1)[1].strip()
                                try:
                                    p=urllib.parse.urlsplit(url)
                                    if p.hostname==peer[0] and p.scheme=='http': locations.add(url)
                                except ValueError: pass
                def identify(url):
                    try:
                        root=read_xml(url)
                        if 'marantz' not in ' '.join((n.text or '') for n in root.iter() if n.tag in ('manufacturer','modelName','friendlyName')).lower(): return
                        info=capabilities(urllib.parse.urlsplit(url).hostname)
                        if info:
                            with self.lock: self.devices[info['host']]=info
                    except (OSError,ValueError,ET.ParseError): pass
                with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool: list(pool.map(identify,locations))
                info=capabilities(self.host)
                if info:
                    with self.lock: self.devices[self.host]=info
            except OSError as error:
                with self.lock: self.scan_error=str(error)
            finally:
                with self.lock: self.scanning=False
        threading.Thread(target=worker,daemon=True).start()
app=App()
class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs): super().__init__(*args,directory=str(ROOT),**kwargs)
    def log_message(self,*args): pass
    def end_headers(self):
        self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff')
        self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        super().end_headers()
    def reply(self,status,data):
        raw=json.dumps(data).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
    def local(self): return self.headers.get('Host') in ('127.0.0.1:8765','localhost:8765')
    def do_GET(self):
        if not self.local(): return self.reply(403,{'error':'Local access only'})
        if self.path=='/api/status':
            with app.lock: return self.reply(200,dict(app.receiver.snapshot(),token=TOKEN,limit=app.limit,devices=list(app.devices.values()),scanning=app.scanning,scan_error=app.scan_error))
        if self.path not in ('/','/index.html','/logo.svg'): return self.reply(404,{'error':'Not found'})
        super().do_GET()
    def do_POST(self):
        if not self.local(): return self.reply(403,{'error':'Local access only'})
        if self.headers.get('Origin') and self.headers['Origin'] not in ('http://127.0.0.1:8765','http://localhost:8765'): return self.reply(403,{'error':'Cross-site requests blocked'})
        if self.headers.get('X-Control-Token')!=TOKEN: return self.reply(403,{'error':'Reload this local page'})
        try:
            size=int(self.headers.get('Content-Length','0'))
            if not 0<size<512: raise ValueError('Invalid request')
            body=json.loads(self.rfile.read(size))
            if not isinstance(body,dict): raise ValueError('Expected a JSON object')
            if self.path=='/api/discover': app.discover();return self.reply(202,{'message':'Searching your network'})
            if self.path=='/api/select': app.select(body.get('host'));return self.reply(200,{'message':'Receiver selected'})
            if self.path=='/api/settings':
                limit=body.get('limit')
                if isinstance(limit,bool) or not isinstance(limit,(int,float)) or not math.isfinite(limit) or not -60<=limit<=0 or limit*2!=round(limit*2): raise ValueError('Choose a limit from -60 to 0 dB in 0.5 dB steps')
                with app.lock: app.limit=limit;app.save()
                return self.reply(200,{'message':'Limit saved. Current volume was not changed.'})
            if self.path!='/api/command': return self.reply(404,{'error':'Not found'})
            cmd=body.get('command')
            if not isinstance(cmd,str): raise ValueError('Expected a command string')
            with app.lock: command_id=app.receiver.execute(cmd,app.limit)
            return self.reply(200,{'id':command_id,'message':'Waiting for receiver confirmation'})
        except (ValueError,TypeError) as error: self.reply(400,{'error':str(error)})
        except (OSError,ConnectionError) as error: self.reply(503,{'error':str(error)})
if __name__=='__main__':
    http=ThreadingHTTPServer(('127.0.0.1',8765),Handler);threading.Thread(target=app.receiver.run,daemon=True).start()
    print('Marantz Remote: http://127.0.0.1:8765',flush=True)
    try: http.serve_forever()
    finally: app.receiver.close();http.server_close()
