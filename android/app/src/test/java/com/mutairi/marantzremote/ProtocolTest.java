package com.mutairi.marantzremote;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProtocolTest {
    @Test public void soundControlsAreBoundedAndConfirmed(){
        assertTrue(Protocol.allowed("PSTONE CTRL ON"));assertTrue(Protocol.allowed("PSBAS UP"));assertTrue(Protocol.allowed("PSLFC OFF"));assertTrue(Protocol.allowed("PSCNTAMT 07"));
        assertFalse(Protocol.allowed("PSCNTAMT 08"));assertFalse(Protocol.allowed("PSLFC ON\rPWON"));
        assertTrue(Protocol.confirmed("PSBAS 51","PSBAS 51"));assertFalse(Protocol.confirmed("PSBAS 51","PSBAS 50"));assertTrue(Protocol.confirmed("PSLFC ON","PSLFC ON"));
    }
    @Test public void volumeDoesNotConfuseMaximumWithCurrent(){assertEquals(-34.5,Protocol.volume("MV455"),.001);assertEquals(0,Protocol.volume("MV80"),.001);assertNull(Protocol.volume("MVMAX 98"));}
    @Test public void commandInjectionRejected(){assertTrue(Protocol.allowed("MVUP"));assertFalse(Protocol.allowed("MVUP\rPWON"));assertFalse(Protocol.allowed("Hello"));}
    @Test public void localAddressesOnly(){assertTrue(Protocol.localAddress("10.10.10.40"));assertTrue(Protocol.localAddress("192.168.1.5"));assertTrue(Protocol.localAddress("172.16.0.1"));assertFalse(Protocol.localAddress("8.8.8.8"));assertFalse(Protocol.localAddress("10.1.2.999"));assertFalse(Protocol.localAddress("172.32.0.1"));}
    @Test public void volumeLimitCannotBeBypassedByDirectRequest(){
        assertEquals("MV605",Protocol.volumeRequest("MVUP",-20,-10));
        assertEquals("MV745",Protocol.volumeRequest("MVDOWN",-5,-10));
        try{Protocol.volumeRequest("MVUP",-10,-10);fail();}catch(IllegalArgumentException expected){}
        try{Protocol.volumeRequest("MV80",-20,-10);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void confirmationRequiresMatchingReply(){assertTrue(Protocol.confirmed("MSMCH STEREO","SYSMI Multi Ch Stereo"));assertFalse(Protocol.confirmed("MV605","MVMAX 605"));assertFalse(Protocol.confirmed("MUON","MUOFF"));assertTrue(Protocol.confirmed("MV605","MV605"));}
    @org.junit.Test public void onlyOfficialIlluminationValuesAllowed(){
        for(int i=2;i<=5;i++)org.junit.Assert.assertTrue(Protocol.allowed("IL"+i));
        org.junit.Assert.assertFalse(Protocol.allowed("IL0"));org.junit.Assert.assertFalse(Protocol.allowed("IL1"));org.junit.Assert.assertFalse(Protocol.allowed("IL6"));
    }
}
