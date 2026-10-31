package com.mutairi.marantzremote;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpeakerChannelsTest {
    String panel(String params){return "<rx><cmd><name>GetActiveSpeaker</name><list>"+params+"</list></cmd></rx>";}
    String channel(String slot,String label,int control){return "<param name='activesp"+slot+"' control='"+control+"'>"+label+"</param>";}
    @Test public void separatesActivityAndSupportsHeights()throws Exception{
        SpeakerChannels.Panel p=SpeakerChannels.parse(ReceiverHttp.xml(panel(channel("b2","FL",2)+channel("d2","FR",2)+channel("c2","C",1)+channel("b1","SW",2)+channel("a1","TFL",2)+channel("e1","TFR",2)+channel("c1","",0))),"GetActiveSpeaker","activesp");
        assertEquals("2.1.2",p.layout);assertEquals(6,p.channels.size());assertFalse(p.channels.get(2).active);
    }
    @Test public void unknownActiveLabelsPreservedWithoutInventedLayout()throws Exception{
        SpeakerChannels.Panel p=SpeakerChannels.parse(ReceiverHttp.xml(panel(channel("a1","FUTURE",2))),"GetActiveSpeaker","activesp");
        assertNull(p.layout);assertEquals("FUTURE",p.channels.get(0).label);
    }
    @Test public void emptyAndInvalidResponsesAreUnavailable()throws Exception{
        assertNull(SpeakerChannels.parse(ReceiverHttp.xml("<rx/>"),"GetActiveSpeaker","activesp"));
        assertNull(SpeakerChannels.parse(ReceiverHttp.xml(panel("")),"GetActiveSpeaker","activesp"));
        assertNull(SpeakerChannels.parse(ReceiverHttp.xml(panel(channel("b2","FL",3))),"GetActiveSpeaker","activesp"));
    }
    @Test public void queryIncludesRequiredNewline()throws Exception{
        ReceiverHttp h=new ReceiverHttp((path,body)->{assertEquals("/goform/AppCommand0300.xml",path);assertTrue(body.contains("?>\n<tx>"));assertTrue(body.contains("activespall"));return panel(channel("b2","FL",2));});
        assertEquals("1.0",h.channels(false).layout);
    }
}
