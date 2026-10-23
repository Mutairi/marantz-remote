package com.mutairi.marantzremote;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProtocolTest {
    @Test public void volumeDoesNotConfuseMaximumWithCurrent(){assertEquals(-34.5,Protocol.volume("MV455"),.001);assertEquals(0,Protocol.volume("MV80"),.001);assertNull(Protocol.volume("MVMAX 98"));}
    @Test public void commandInjectionRejected(){assertTrue(Protocol.allowed("MVUP"));assertFalse(Protocol.allowed("MVUP\rPWON"));assertFalse(Protocol.allowed("Hello"));}
    @Test public void localAddressesOnly(){assertTrue(Protocol.localAddress("10.10.10.40"));assertTrue(Protocol.localAddress("192.168.1.5"));assertTrue(Protocol.localAddress("172.16.0.1"));assertFalse(Protocol.localAddress("8.8.8.8"));assertFalse(Protocol.localAddress("10.1.2.999"));assertFalse(Protocol.localAddress("172.32.0.1"));}
}
