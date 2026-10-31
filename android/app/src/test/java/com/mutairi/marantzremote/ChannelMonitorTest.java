package com.mutairi.marantzremote;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChannelMonitorTest {
    SpeakerChannels.Panel panel()throws Exception{return SpeakerChannels.parse(ReceiverHttp.xml("<rx><cmd><name>GetInputSignal</name><list><param name='inputsigb2' control='2'>FL</param></list></cmd></rx>"),"GetInputSignal","inputsig");}
    @Test public void partialFailureKeepsOtherPanel()throws Exception{
        SpeakerChannels.Panel p=panel();SpeakerChannels.Panel[] got=new SpeakerChannels.Panel[2];
        ChannelMonitor monitor=new ChannelMonitor(input->{if(!input)throw new java.io.IOException();return p;},(in,out)->{got[0]=in;got[1]=out;});
        monitor.route("On|BD|STEREO",true);monitor.refresh();assertSame(p,got[0]);assertNull(got[1]);monitor.close();
    }
    @Test public void changingSourceDiscardsInFlightResult()throws Exception{
        SpeakerChannels.Panel p=panel();SpeakerChannels.Panel[] got=new SpeakerChannels.Panel[2];ChannelMonitor[] ref=new ChannelMonitor[1];
        ref[0]=new ChannelMonitor(input->{ref[0].route("On|GAME|STEREO",true);return p;},(in,out)->{got[0]=in;got[1]=out;});
        ref[0].route("On|BD|STEREO",true);ref[0].refresh();assertNull(got[0]);assertNull(got[1]);ref[0].close();
    }
    @Test public void standbyAndCloseDoNotQuery()throws Exception{
        int[] requests={0};ChannelMonitor monitor=new ChannelMonitor(input->{requests[0]++;return null;},(in,out)->{});
        monitor.route("Standby",false);monitor.refresh();assertEquals(0,requests[0]);monitor.close();monitor.refresh();assertEquals(0,requests[0]);
    }
}
