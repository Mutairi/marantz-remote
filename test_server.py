import json, socket, threading, time, unittest, urllib.request, urllib.error
from unittest.mock import patch, Mock
import server

class RemoteTests(unittest.TestCase):
    def receiver(self):
        r=server.Receiver();r.state['connected']=True;r.receive('MV60');r.sock=Mock();r.select_indexed_mode=Mock();return r
    def test_custom_input_names_keep_control_codes(self):
        import xml.etree.ElementTree as ET
        xml=ET.fromstring('<rx><cmd><functionrename><list><name>Blu-ray</name><rename>DIABLO   </rename></list><list><name>GAME1</name><rename>PS5</rename></list><list><name>CD</name><rename>   </rename></list></functionrename></cmd></rx>')
        with patch.object(server,'read_xml',return_value=xml):
            self.assertEqual(server.renamed_inputs('10.10.10.40',8080),{'BD':'DIABLO','GAME':'PS5'})
    def test_bass_controls_confirm_and_enforce_bounds(self):
        r=self.receiver();r.receive('MSSTEREO');r.receive('PSTONE CTRL OFF');r.receive('PSBAS 50')
        with self.assertRaisesRegex(ValueError,'Enable'):r.execute('PSBAS UP',-10)
        r.execute('PSTONE CTRL ON',-10);r.receive('PSTONE CTRL ON')
        self.assertEqual(r.state['command']['status'],'confirmed')
        r.execute('PSBAS UP',-10)
        self.assertEqual(r.state['command']['wire'],'PSBAS 51')
        r.receive('PSBAS 51');self.assertEqual(r.state['bass'],1)
        self.assertEqual(r.state['command']['status'],'confirmed')
        r.receive('PSBAS 56')
        with self.assertRaisesRegex(ValueError,'limit'):r.execute('PSBAS UP',-10)
        r.receive('MSPURE DIRECT')
        with self.assertRaisesRegex(ValueError,'unavailable'):r.execute('PSTONE CTRL ON',-10)
    def test_lfc_confirmation_and_guards(self):
        r=self.receiver();r.receive('MSSTEREO');r.receive('PSLFC OFF');r.receive('PSCNTAMT 01')
        with self.assertRaisesRegex(ValueError,'Enable'):r.execute('PSCNTAMT 02',-10)
        r.execute('PSLFC ON',-10);r.receive('PSLFC ON')
        self.assertEqual(r.state['command']['status'],'confirmed')
        r.execute('PSCNTAMT 07',-10);r.receive('PSCNTAMT 07')
        self.assertEqual(r.state['containment'],7)
        self.assertEqual(r.state['command']['status'],'confirmed')
        with self.assertRaises(ValueError):r.execute('PSCNTAMT 08',-10)
        r.receive('MSPURE DIRECT')
        with self.assertRaisesRegex(ValueError,'unavailable'):r.execute('PSLFC ON',-10)
    def test_volume_parser(self):
        r=self.receiver()
        for line,value in [('MV00',-80),('MV455',-34.5),('MV80',0),('MV985',18.5)]:
            r.receive(line);self.assertEqual(r.state['volume'],value)
        r.receive('MVMAX 98');self.assertEqual(r.state['volume'],18.5)
    def test_volume_limit_blocks_without_writing(self):
        r=self.receiver();r.receive('MV70')
        with self.assertRaisesRegex(ValueError,'limit'): r.execute('MVUP',-10)
        r.sock.sendall.assert_not_called()
    def test_volume_down_allowed_above_limit(self):
        r=self.receiver();r.receive('MV75');r.execute('MVDOWN',-10)
        self.assertEqual(r.sock.sendall.call_args_list[0].args[0],b'MV745\r')
    def test_duplicate_clicks_wait_for_confirmation(self):
        r=self.receiver();r.execute('MVUP',-10)
        self.assertEqual(r.state['command']['status'],'pending')
        with self.assertRaisesRegex(ValueError,'Wait'):r.execute('MVUP',-10)
        r.receive('MUOFF');self.assertEqual(r.state['command']['status'],'pending')
        r.receive('MV605');self.assertEqual(r.state['command']['status'],'confirmed')
        r.execute('MVUP',-10)
        self.assertEqual(r.state['command']['expected'],-19)
    def test_unconfirmed_command_is_not_replayed(self):
        r=self.receiver();r.execute('PWON',-10);r.state['command']['started']-=6
        writes=r.sock.sendall.call_count
        self.assertEqual(r.snapshot()['command']['status'],'unconfirmed')
        self.assertEqual(r.sock.sendall.call_count,writes)
    def test_only_advertised_controls_and_no_injection(self):
        r=self.receiver()
        for command in ['SIBD','MSSTEREO','MVUP\rPWON']:
            with self.assertRaises(ValueError):r.execute(command,-10)
        r.state['inputs']=[{'value':'BD','label':'Blu-ray'}];r.execute('SIBD',-10);r.receive('SIBD')
        self.assertEqual(r.state['command']['status'],'confirmed')
    def test_advertised_modes_replace_old_list(self):
        r=self.receiver()
        for line in ['OPSMLALL MOV010Stereo','OPSMLALL MUS020Stereo','OPSMLALL PUR030Direct','OPSMLALL END']:r.receive(line)
        self.assertEqual([m['value'] for m in r.state['modes']],['STEREO','DIRECT'])
        for line in ['OPSMLALL MOV010Multi Ch Stereo','OPSMLALL END']:r.receive(line)
        self.assertEqual([m['value'] for m in r.state['modes']],['MCH STEREO'])
        r.execute('MSMCH STEREO',-10);r.receive('MSMULTI CH STEREO')
        self.assertEqual(r.state['command']['status'],'confirmed')
    def test_advertised_dolby_group_confirms_real_receiver_reply(self):
        r=self.receiver()
        r.receive('OPSMLALL MOV020Dolby Audio - Dolby Digital Plus')
        r.receive('OPSMLALL MOV031Dolby Atmos/DSurr');r.receive('OPSMLALL END')
        self.assertEqual([x['value'] for x in r.state['modes']],['DOLBY DIGITAL +','DOLBY DIGITAL'])
        r.execute('MSDOLBY DIGITAL',-10)
        r.receive('MSDOLBY AUDIO-DD+ +DSUR')
        self.assertEqual(r.state['command']['status'],'confirmed')
    def test_native_dolby_is_separate_from_surround(self):
        r=self.receiver();r.receive('SYSDA Dolby Audio - DD+')
        r.receive('OPSMLALL MOV020Dolby Audio - Dolby Digital Plus')
        r.receive('OPSMLALL MOV031Dolby Atmos/DSurr');r.receive('OPSMLALL END')
        self.assertEqual([x['label'] for x in r.state['modes']],['Dolby Digital Plus · original','Dolby Digital Plus + Dolby Surround'])
        r.execute('MSDOLBY DIGITAL +',-10)
        r.select_indexed_mode.assert_called_once()
        self.assertEqual(r.state['command']['expected'],'DOLBY DIGITAL +')
        self.assertEqual(r.sock.sendall.call_args_list[0].args[0],b'MS?\r')
        r.receive('MSDOLBY AUDIO-DD+ +DSUR');self.assertEqual(r.state['command']['status'],'pending')
        r.receive('MSDOLBY AUDIO-DD+');self.assertEqual(r.state['command']['status'],'confirmed')
        r.execute('MSDOLBY DIGITAL',-10)
        r.receive('MSDOLBY AUDIO-DD+');self.assertEqual(r.state['command']['status'],'pending')
        r.receive('MSDOLBY AUDIO-DD+ +DSUR');self.assertEqual(r.state['command']['status'],'confirmed')
    def test_wrong_mode_does_not_falsely_confirm(self):
        r=self.receiver()
        r.receive('OPSMLALL MOV031Dolby Atmos/DSurr');r.receive('OPSMLALL END')
        r.execute('MSDOLBY DIGITAL',-10);r.receive('MSSTEREO')
        self.assertEqual(r.state['command']['status'],'pending')
        r.receive('SYSMI Dolby Audio - DD +   + DSurr')
        self.assertEqual(r.state['command']['status'],'confirmed')
    def test_mode_punctuation_aliases_preserve_codec_and_upmixer(self):
        self.assertEqual(server.mode_key('Dolby Audio - DD +   + DSurr'),server.mode_key('DOLBY AUDIO-DD+ +DSUR'))
        self.assertEqual(server.mode_key('Dolby Audio - Dolby Digital Plus'),server.mode_key('DOLBY AUDIO-DD+'))
        self.assertNotEqual(server.mode_key('DOLBY AUDIO-DD+'),server.mode_key('DOLBY DIGITAL'))
        self.assertNotEqual(server.mode_key('DOLBY AUDIO-DD+'),server.mode_key('DOLBY AUDIO-DD+ +DSUR'))
    def test_stale_status_disables_commands(self):
        r=self.receiver();r.last_received-=15
        with self.assertRaises(ConnectionError):r.execute('MUON',-10)
        r.sock.sendall.assert_not_called()
    def test_public_or_loopback_addresses_rejected(self):
        for host in ['8.8.8.8','127.0.0.1','169.254.169.254','example.com','::1',None]:
            self.assertFalse(server.private_ip(host))
        for host in ['10.10.10.40','192.168.1.100','172.16.0.1']:self.assertTrue(server.private_ip(host))
        with patch.object(server,'capabilities') as probe:
            with self.assertRaises(ValueError):server.app.select('8.8.8.8')
            probe.assert_not_called()
    def test_inputs_from_main_zone_only(self):
        root=server.ET.fromstring('<Device_Info><BrandCode>1</BrandCode><ModelName>CINEMA 40</ModelName><DeviceZoneCapabilities><Zone><No>0</No></Zone><InputSource><List><Source><FuncName>Blu-ray</FuncName><DefaultName>Blu-ray</DefaultName></Source></List></InputSource></DeviceZoneCapabilities><DeviceZoneCapabilities><Zone><No>1</No></Zone><InputSource><List><Source><FuncName>CD</FuncName></Source></List></InputSource></DeviceZoneCapabilities></Device_Info>')
        with patch.object(server,'read_xml',return_value=root):
            self.assertEqual(server.capabilities('10.10.10.40')['inputs'],[{'value':'BD','label':'Blu-ray'}])
    def http_receiver(self):
        r=server.Receiver();r.state['connected']=True;r.receive('MV60');r.sock=Mock()
        r.receive('OPSMLALL MOV020Dolby Audio - Dolby Digital Plus')
        r.receive('OPSMLALL MOV031Dolby Atmos/DSurr');r.receive('OPSMLALL END')
        return r
    def mode_xml(self,selected='7',native=True):
        root=server.ET.Element('rx');cmd=server.ET.SubElement(root,'cmd');ls=server.ET.SubElement(cmd,'list')
        server.ET.SubElement(ls,'param',name='genrelist').text='1'
        for number,label in ([('42','Dolby Audio - Dolby Digital Plus')] if native else [])+[('7','Dolby Atmos/DSurr')]:
            v=server.ET.SubElement(ls,'value');server.ET.SubElement(v,'listno').text=number
            server.ET.SubElement(v,'dispname').text=label;server.ET.SubElement(v,'selected').text='1' if selected==number else '0'
        return root
    def test_http_select_uses_fresh_index_and_verifies_selection(self):
        r=self.http_receiver()
        with patch.object(server,'sound_mode_call',side_effect=[self.mode_xml(),server.ET.fromstring('<rx><cmd>OK</cmd></rx>'),self.mode_xml('42')]) as call:
            r.execute('MSDOLBY DIGITAL +',-10)
            self.assertEqual(call.call_args_list[1].args[2:4],('SetSoundModeList',{'listno':'42'}))
            self.assertEqual(r.state['command']['status'],'confirmed')
            self.assertEqual(r.sock.sendall.call_args_list[0].args[0],b'MS?\r')
    def test_http_does_not_accept_generic_ok_as_confirmation(self):
        r=self.http_receiver()
        with patch.object(server,'sound_mode_call',side_effect=[self.mode_xml(),server.ET.fromstring('<rx><cmd>OK</cmd></rx>'),self.mode_xml()]):
            r.execute('MSDOLBY DIGITAL +',-10)
            self.assertEqual(r.state['command']['status'],'unconfirmed')
    def test_http_changed_modes_abort_without_write(self):
        r=self.http_receiver()
        with patch.object(server,'sound_mode_call',return_value=self.mode_xml(native=False)) as call:
            with self.assertRaisesRegex(ValueError,'Available modes changed'):r.execute('MSDOLBY DIGITAL +',-10)
            self.assertEqual(call.call_count,1);r.sock.sendall.assert_not_called()
    def test_http_already_selected_is_read_only(self):
        r=self.http_receiver()
        with patch.object(server,'sound_mode_call',side_effect=[self.mode_xml('42'),self.mode_xml('42')]) as call:
            r.execute('MSDOLBY DIGITAL +',-10)
            self.assertTrue(all(args.args[2]=='GetSoundModeList' for args in call.call_args_list))
            self.assertEqual(r.state['command']['status'],'confirmed')
    def test_api_validation_and_security(self):
        http=server.ThreadingHTTPServer(('127.0.0.1',0),server.Handler)
        thread=threading.Thread(target=http.serve_forever,daemon=True);thread.start()
        def post(body,host='127.0.0.1:8765',token=server.TOKEN,origin='http://127.0.0.1:8765'):
            req=urllib.request.Request(f'http://127.0.0.1:{http.server_port}/api/command',data=json.dumps(body).encode(),headers={'Host':host,'X-Control-Token':token,'Origin':origin})
            try:
                with urllib.request.urlopen(req) as response:return response.status
            except urllib.error.HTTPError as e:
                status=e.code;e.close();return status
        try:
            with patch.object(server.app.receiver,'execute',return_value='test-id') as execute:
                for body in [[],None,'MVUP',{}, {'command':[]}]:self.assertEqual(post(body),400)
                self.assertEqual(post({'command':'MVUP'},host='evil.example'),403)
                self.assertEqual(post({'command':'MVUP'},token='wrong'),403)
                self.assertEqual(post({'command':'MVUP'},origin='http://evil.example'),403)
                execute.assert_not_called()
                self.assertEqual(post({'command':'MVUP'}),200)
                execute.assert_called_once_with('MVUP',server.app.limit)
        finally:http.shutdown();http.server_close();thread.join()

if __name__=='__main__':unittest.main()

class ChannelTests(unittest.TestCase):
    def xml(self,name,prefix,values):
        import xml.etree.ElementTree as ET
        params=''.join(f'<param name="{prefix}{slot}" control="{control}">{label}</param>' for slot,label,control in values)
        return ET.fromstring(f'<rx><cmd><name>{name}</name><list>{params}</list></cmd></rx>')
    def test_actual_input_and_output_are_separate(self):
        root=self.xml('GetInputSignal','inputsig',[('b2','FL',2),('c2','C',2),('d2','FR',2),('b3','SL',2),('d3','SR',2),('b4','SBL',2),('d4','SBR',2),('b1','LFE',2)])
        p=server.channel_panel(root,'GetInputSignal','inputsig')
        self.assertEqual(p['layout'],'7.1');self.assertEqual(len(p['channels']),8)
        out=server.channel_panel(self.xml('GetActiveSpeaker','activesp',[('b2','FL',2),('d2','FR',2),('b1','SW',2),('c2','C',1)]),'GetActiveSpeaker','activesp')
        self.assertEqual(out['layout'],'2.1');self.assertFalse(out['channels'][-1]['active'])
    def test_height_unknown_absent_and_silent_channels(self):
        p=server.channel_panel(self.xml('GetActiveSpeaker','activesp',[('b2','FL',2),('d2','FR',2),('a1','TFL',2),('e1','TFR',2),('b1','SW',1),('c1','',0)]),'GetActiveSpeaker','activesp')
        self.assertEqual(p['layout'],'2.0.2');self.assertEqual(len(p['channels']),5)
        p=server.channel_panel(self.xml('GetActiveSpeaker','activesp',[('a1','FUTURE',2)]),'GetActiveSpeaker','activesp')
        self.assertIsNone(p['layout']);self.assertEqual(p['channels'][0]['label'],'FUTURE')
        p=server.channel_panel(self.xml('GetInputSignal','inputsig',[('b2','FL',1)]),'GetInputSignal','inputsig')
        self.assertEqual(p['layout'],'No active channels')
    def test_unsupported_and_invalid_are_unavailable(self):
        import xml.etree.ElementTree as ET
        for xml in ['<rx/>','<rx><cmd><name>GetInputSignal</name><list/></cmd></rx>', '<rx><cmd><name>GetInputSignal</name><list><param name="inputsigb2" control="3">FL</param></list></cmd></rx>']:
            self.assertIsNone(server.channel_panel(ET.fromstring(xml),'GetInputSignal','inputsig'))
    def test_channel_query_preserves_required_newline(self):
        root=self.xml('GetInputSignal','inputsig',[('b2','FL',2),('d2','FR',2)])
        with patch.object(server,'read_xml',return_value=root) as read:
            p=server.read_channel_panel('10.10.10.40',8080,'GetInputSignal','inputsig')
        self.assertEqual(p['layout'],'2.0');self.assertIn(b'?>\n<tx>',read.call_args.args[1])

class ChannelRefreshTests(unittest.TestCase):
    def ready(self):
        r=server.Receiver();r.state.update(connected=True,power='On',input='BD',mode='STEREO');r.last_received=time.monotonic();return r
    def panel(self):return {'channels':[{'slot':'b2','label':'FL','active':True}], 'layout':'1.0'}
    def test_failed_output_does_not_hide_valid_input(self):
        r=self.ready()
        with patch.object(server,'read_channel_panel',side_effect=[self.panel(),OSError('unsupported')]):r.refresh_channels()
        s=r.snapshot()['channels'];self.assertEqual(s['input']['layout'],'1.0');self.assertIsNone(s['output'])
    def test_expiry_and_standby_clear_lights(self):
        r=self.ready()
        with patch.object(server,'read_channel_panel',return_value=self.panel()):r.refresh_channels()
        self.assertIsNotNone(r.snapshot()['channels']['input'])
        with patch.object(server.time,'time',return_value=time.time()+15):self.assertIsNone(r.snapshot()['channels']['input'])
        r.receive('PWSTANDBY');self.assertIsNone(r.snapshot()['channels']['input'])
    def test_late_response_after_source_change_is_discarded(self):
        r=self.ready()
        def late(*a):r.receive('SIGAME');return self.panel()
        with patch.object(server,'read_channel_panel',side_effect=late):r.refresh_channels()
        self.assertIsNone(r.snapshot()['channels']['input']);self.assertIsNone(r.snapshot()['channels']['output'])
    def test_mode_change_invalidates_existing_channels(self):
        r=self.ready()
        with patch.object(server,'read_channel_panel',return_value=self.panel()):r.refresh_channels()
        r.receive('MSDIRECT');self.assertIsNone(r.snapshot()['channels']['input'])

class ChannelIndexedModeTests(unittest.TestCase):
    http_receiver=RemoteTests.http_receiver
    mode_xml=RemoteTests.mode_xml
    def test_indexed_mode_change_clears_previous_channel_snapshot(self):
        r=self.http_receiver();r.state['power']='On'
        panel={'layout':'2.0','channels':[]}
        with patch.object(server,'read_channel_panel',return_value=panel):r.refresh_channels()
        self.assertIsNotNone(r.snapshot()['channels']['output'])
        with patch.object(server,'sound_mode_call',side_effect=[self.mode_xml(),server.ET.fromstring('<rx><cmd>OK</cmd></rx>'),self.mode_xml('42')]):r.execute('MSDOLBY DIGITAL +',-10)
        self.assertIsNone(r.snapshot()['channels']['output'])

class IlluminationTests(unittest.TestCase):
    def test_read_uses_separate_front_panel_illumination(self):
        r=server.ET.fromstring('<FrontPanel><DisplaysBrightness>3</DisplaysBrightness><IlluminationBrightness>1</IlluminationBrightness></FrontPanel>')
        with patch.object(server,'read_xml',return_value=r) as read:
            self.assertEqual(server.read_illumination('10.10.10.40',11080),'1')
            self.assertEqual(read.call_args.args[0],'http://10.10.10.40:11080/ajax/general/get_config?type=18')
    def test_unknown_or_missing_read_is_unavailable(self):
        for xml in ['<FrontPanel/>','<FrontPanel><IlluminationBrightness>6</IlluminationBrightness></FrontPanel>','<Other><IlluminationBrightness>1</IlluminationBrightness></Other>']:
            with patch.object(server,'read_xml',return_value=server.ET.fromstring(xml)):
                with self.assertRaises(ValueError):server.read_illumination('10.10.10.40',11080)
    def test_write_is_allowlisted_and_changes_only_illumination(self):
        with patch.object(server,'receiver_http',return_value=b'') as http:
            server.write_illumination('10.10.10.40',11080,'5')
            query=server.urllib.parse.parse_qs(server.urllib.parse.urlsplit(http.call_args.args[0]).query)
            self.assertEqual(query,{'type':['18'],'data':['<IlluminationBrightness>5</IlluminationBrightness>']})
            with self.assertRaises(ValueError):server.write_illumination('10.10.10.40',11080,'6')
    def ready(self):
        r=server.Receiver();r.state.update(connected=True,power='On',illumination='1',illumination_updated_at=time.time());r.last_received=time.monotonic();return r
    def test_only_readback_confirms_setting(self):
        r=self.ready()
        with patch.object(server,'write_illumination') as write,patch.object(server,'read_illumination',return_value='3'):
            r.execute('IL3',-10)
            self.assertEqual(r.snapshot()['illumination'],'3');self.assertEqual(r.state['command']['status'],'confirmed');write.assert_called_once()
        r=self.ready()
        with patch.object(server,'write_illumination'),patch.object(server,'read_illumination',return_value='1'):
            r.execute('IL3',-10);self.assertEqual(r.state['command']['status'],'unconfirmed');self.assertEqual(r.snapshot()['illumination'],'1')
    def test_stale_read_cannot_enable_controls(self):
        r=self.ready();r.state['illumination_updated_at']-=20
        self.assertIsNone(r.snapshot()['illumination'])
        with patch.object(server,'write_illumination') as write:
            with self.assertRaises(ValueError):r.execute('IL2',-10)
            write.assert_not_called()
    def test_late_poll_after_command_is_discarded(self):
        r=self.ready()
        def late(*args):
            r.illumination_epoch+=1;r.state['illumination']='5';return '1'
        with patch.object(server,'read_illumination',side_effect=late):r.refresh_illumination()
        self.assertEqual(r.state['illumination'],'5')

class IlluminationPowerTests(unittest.TestCase):
    def test_power_cycle_does_not_restore_previous_reading(self):
        r=server.Receiver();r.state.update(connected=True,power='On',illumination='2',illumination_updated_at=time.time());r.last_received=time.monotonic()
        r.receive('PWSTANDBY');r.receive('PWON')
        self.assertIsNone(r.snapshot()['illumination'])

class FrontDisplayTests(unittest.TestCase):
    def test_write_updates_circle_and_illumination(self):
        with patch.object(server,'receiver_http',return_value=b'') as http:
            server.write_illumination('10.10.10.40',11080,'5')
            data=''.join(server.urllib.parse.parse_qs(server.urllib.parse.urlsplit(c.args[0]).query)['data'][0] for c in http.call_args_list)
            self.assertIn('<DisplaysBrightness>4</DisplaysBrightness>',data)
            self.assertIn('<IlluminationBrightness>5</IlluminationBrightness>',data)
    def test_partial_readback_is_not_confirmed(self):
        root=server.ET.fromstring('<FrontPanel><DisplaysBrightness>1</DisplaysBrightness><IlluminationBrightness>5</IlluminationBrightness></FrontPanel>')
        with patch.object(server,'read_xml',return_value=root):
            self.assertNotEqual(server.read_illumination('10.10.10.40',11080),'5')
