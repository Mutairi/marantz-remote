package com.mutairi.marantzremote;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReceiverHttpTest {
    private String list(String selected){return "<rx><cmd><list><param name='genrelist'>1</param><list>"+
        "<value><listno>42</listno><dispname>Dolby Audio - Dolby Digital Plus</dispname><selected>"+(selected.equals("42")?"1":"0")+"</selected></value>"+
        "<value><listno>7</listno><dispname>Dolby Atmos/DSurr</dispname><selected>"+(selected.equals("7")?"1":"0")+"</selected></value></list></list></cmd></rx>";}
    private String command(String number,String label){return "MODE:1:"+number+":"+ReceiverHttp.token(label);}
    @Test public void selectsExactFreshIndexAndVerifies()throws Exception{
        Queue<String> replies=new ArrayDeque<>(Arrays.asList(list("7"),"<rx><cmd>OK</cmd></rx>",list("42")));List<String> requests=new ArrayList<>();
        ReceiverHttp h=new ReceiverHttp((path,body)->{requests.add(body);return replies.remove();});
        assertEquals("Dolby Audio - Dolby Digital Plus",h.selectMode(command("42","Dolby Audio - Dolby Digital Plus"),"Dolby Audio - Dolby Digital Plus"));
        assertTrue(requests.get(1).contains("name=\"listno\">42<"));assertEquals(3,requests.size());
    }
    @Test public void genericOkIsNotConfirmation()throws Exception{
        Queue<String> replies=new ArrayDeque<>(Arrays.asList(list("7"),"<rx><cmd>OK</cmd></rx>",list("7")));
        ReceiverHttp h=new ReceiverHttp((path,body)->replies.remove());
        try{h.selectMode(command("42","Dolby Audio - Dolby Digital Plus"),"Dolby Audio - Dolby Digital Plus");fail("Wrong active entry was accepted");}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("not confirmed"));}
    }
    @Test public void staleSelectionNeverWrites()throws Exception{
        List<String> requests=new ArrayList<>();ReceiverHttp h=new ReceiverHttp((path,body)->{requests.add(body);return list("7");});
        try{h.selectMode(command("42","Different old label"),"Different old label");fail("Stale choice accepted");}catch(java.io.IOException expected){assertEquals(1,requests.size());}
    }
    @Test public void alreadySelectedIsReadOnly()throws Exception{
        List<String> requests=new ArrayList<>();ReceiverHttp h=new ReceiverHttp((path,body)->{requests.add(body);return list("42");});
        h.selectMode(command("42","Dolby Audio - Dolby Digital Plus"),"Dolby Audio - Dolby Digital Plus");
        assertEquals(2,requests.size());assertTrue(requests.stream().noneMatch(s->s.contains("SetSoundModeList")));
    }
    @Test public void exposesSeparateDolbyChoices()throws Exception{
        ReceiverHttp h=new ReceiverHttp((path,body)->list("7"));List<ReceiverHttp.Choice> choices=h.modes().choices();
        assertEquals("Dolby Digital Plus · original",choices.get(0).label);assertEquals("Dolby Digital Plus + Dolby Surround",choices.get(1).label);
        assertNotEquals(choices.get(0).command,choices.get(1).command);
    }
    @Test public void rejectsExternalEntities()throws Exception{
        try{ReceiverHttp.xml("<!DOCTYPE rx [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rx>&x;</rx>");fail("Entity accepted");}catch(java.io.IOException expected){}
    }
    @Test public void illuminationUsesOfficialSeparateSettingAndConfirms()throws Exception{
        List<String> requests=new ArrayList<>();int[] current={1};
        ReceiverHttp h=new ReceiverHttp((path,body)->{requests.add(path);assertNull(body);if(path.contains("set_config")){assertTrue(path.contains("IlluminationBrightness")||path.contains("DisplaysBrightness"));current[0]=5;return "";}return "<FrontPanel><DisplaysBrightness>"+(current[0]==1?1:current[0]-1)+"</DisplaysBrightness><IlluminationBrightness>"+current[0]+"</IlluminationBrightness></FrontPanel>";});
        assertEquals("1",h.illumination());assertEquals("5",h.setIllumination("5"));
        assertTrue(requests.stream().allMatch(v->v.contains("type=18")));
        try{h.setIllumination("6");fail("Invalid brightness accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void wrongIlluminationReadbackDoesNotConfirm()throws Exception{
        ReceiverHttp h=new ReceiverHttp((path,body)->path.contains("set_config")?"":"<FrontPanel><DisplaysBrightness>1</DisplaysBrightness><IlluminationBrightness>1</IlluminationBrightness></FrontPanel>");
        try{h.setIllumination("5");fail("Wrong readback accepted");}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("not confirm"));}
    }
    @Test public void missingIlluminationIsNotAssumedOff()throws Exception{
        ReceiverHttp h=new ReceiverHttp((path,body)->"<FrontPanel/>");
        try{h.illumination();fail("Unsupported accepted");}catch(java.io.IOException expected){}
    }
}
