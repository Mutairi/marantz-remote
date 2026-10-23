import json
import socket
import threading
import unittest
import urllib.request
import urllib.error
from unittest.mock import patch
import server


class RemoteTests(unittest.TestCase):
    def test_volume_and_status(self):
        r=server.Receiver()
        for line,volume in [('MV00',-80),('MV455',-34.5),('MV80',0),('MV985',18.5)]:
            r.receive(line)
            self.assertEqual(r.state['volume'],volume)
        r.receive('MVMAX 98')
        self.assertEqual(r.state['volume'],18.5)
        for line in ('MUON','PWON','SIBD','MSSTEREO','SYSDA PCM'):
            r.receive(line)
        self.assertTrue(r.state['mute'])
        self.assertEqual(r.state['format'],'PCM')
        self.assertEqual(r.state['input'],'BD')

    def test_wire_commands(self):
        a,b=socket.socketpair()
        try:
            r=server.Receiver();r.sock=a
            for command in server.ALLOWED:
                r.send(command)
                self.assertEqual(b.recv(1024),(command+'\r').encode())
        finally:
            a.close();b.close()

    def test_api_validation(self):
        http=server.ThreadingHTTPServer(('127.0.0.1',0),server.Handler)
        thread=threading.Thread(target=http.serve_forever,daemon=True);thread.start()
        def post(body,host='127.0.0.1:8765',token=server.TOKEN):
            req=urllib.request.Request(f'http://127.0.0.1:{http.server_port}/api/command',
                data=json.dumps(body).encode(),headers={'Host':host,'X-Control-Token':token})
            try:
                with urllib.request.urlopen(req) as response: return response.status
            except urllib.error.HTTPError as e:
                status=e.code;e.close();return status
        try:
            with patch.object(server.receiver,'send') as send:
                for body in ([],None,'MVUP',{}, {'command':[]},{'command':'MVUP\rPWON'}):
                    self.assertEqual(post(body),400)
                self.assertEqual(post({'command':'MVUP'},host='evil.example'),403)
                self.assertEqual(post({'command':'MVUP'},token='wrong'),403)
                send.assert_not_called()
                self.assertEqual(post({'command':'MVUP'}),200)
                send.assert_called_once_with('MVUP')
        finally:
            http.shutdown();http.server_close();thread.join()


if __name__=='__main__': unittest.main()
