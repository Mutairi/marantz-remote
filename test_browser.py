"""Real Chromium DOM checks; run with python3 test_browser.py (Chrome required)."""
from pathlib import Path
import json, shutil, subprocess, tempfile

chrome=shutil.which('google-chrome') or shutil.which('chromium') or '/opt/google/chrome/chrome'
html=Path('index.html').read_text().replace('refresh();setInterval(refresh,800);','/* test owns refresh */')
fixture={'connected':True,'last_update':0,'power':'On','name':'Marantz','host':'10.10.10.40','volume':-30,'limit':-10,'format':'PCM','mode':'STEREO','inputs':[],'modes':[],'log':[],'devices':[],'channels':{}}
script='''
(async()=>{const result=document.createElement('pre');result.id='test-result';document.body.append(result);
try{
const check=(yes,message)=>{if(!yes)throw Error(message);};
state=FIXTURE;state.last_update=Date.now()/1000;state.illumination='3';state.illumination_updated_at=Date.now()/1000;
const input={updated_at:Date.now()/1000,layout:'7.1',channels:[{slot:'b2',label:'FL',active:true},{slot:'d2',label:'FR',active:true},{slot:'c2',label:'C',active:false},{slot:'b1',label:'LFE',active:true}]};
state.channels={input,output:{...input,layout:'5.1'}};render();
check($('illumination').value==='3','receiver reported illumination selected');
check(!$('illumination').disabled,'fresh illumination enabled');
check([...$('illumination').options].map(o=>o.text).join(',')==='Off,Dark,Dim,Bright','exact front display choices');
check(!$('frontPanelLights'),'no separate lighting menu');
check(Boolean($('illumination').compareDocumentPosition($('soundAdjustments'))&Node.DOCUMENT_POSITION_FOLLOWING),'front display above sound adjustments');
state.illumination='1';render();check($('illumination').selectedOptions[0].text==='Choose…','mixed settings prompt');check(!$('illumination').disabled,'mixed settings allow selection');state.illumination='3';
state.illumination_updated_at-=20;render();check($('illumination').disabled,'stale illumination disabled');
state.illumination_updated_at=Date.now()/1000;render();
check($('inputLayout').textContent==='7.1','input caption');
check($('outputLayout').textContent==='5.1','independent output caption');
check($('inputSpeakers').querySelectorAll('.speaker.active').length===3,'only active channels glow');
check($('inputSpeakers').querySelectorAll('.speaker:not(.active)').length===1,'inactive channel dim');
check($('inputSpeakers').getAttribute('aria-label').includes('C inactive'),'accessible channel states');
check(document.documentElement.scrollWidth<=innerWidth,'no horizontal overflow');
state.channels.input.updated_at=Date.now()/1000-15;render();
check($('inputLayout').textContent==='Unavailable','stale status unavailable');
check(!$('inputSpeakers').querySelector('.active'),'stale lights cleared');
state.channels.input={...input,updated_at:Date.now()/1000};state.power='Standby';render();
check(!$('inputSpeakers').querySelector('.active'),'standby lights cleared');
state.power='On';render();check($('inputSpeakers').querySelector('.active'),'fresh status restored');
window.fetch=async()=>{throw Error('offline')};await refresh();
check(!$('inputSpeakers').querySelector('.active'),'local service failure lights cleared');
check($('illumination').disabled,'offline illumination disabled');
result.textContent='PASS: channel display, independent captions, inactive/active, accessibility, expiry, standby, offline, viewport';
}catch(e){result.textContent='FAIL: '+e.message;}})();
'''.replace('FIXTURE',json.dumps(fixture))
html=html.replace('</html>','<script>'+script+'</script></html>')
with tempfile.TemporaryDirectory() as tmp:
    path=Path(tmp)/'test.html';path.write_text(html)
    for width in (390,1000):
        r=subprocess.run([chrome,'--headless','--disable-gpu','--no-sandbox','--no-proxy-server',f'--user-data-dir={tmp}/profile{width}',f'--window-size={width},1000','--virtual-time-budget=1000','--dump-dom',path.as_uri()],capture_output=True,text=True,timeout=30)
        import re
        m=re.search(r'<pre id="test-result">([^<]+)</pre>',r.stdout)
        message=m.group(1) if m else 'FAIL: browser did not produce a result: '+r.stderr[-400:]
        print(width,message)
        if not message.startswith('PASS:'):raise SystemExit(1)
