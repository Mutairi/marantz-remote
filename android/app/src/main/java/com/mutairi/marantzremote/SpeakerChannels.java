package com.mutairi.marantzremote;

import java.util.*;
import org.w3c.dom.*;

/** Receiver channel routing, independent of codec names and instantaneous sound levels. */
public final class SpeakerChannels {
    private SpeakerChannels(){}
    private static final Set<String> BED=new HashSet<>(Arrays.asList("FL","FR","C","SL","SR","SBL","SBR","SB","FWL","FWR"));
    private static final Set<String> HEIGHT=new HashSet<>(Arrays.asList("FHL","FHR","SHL","SHR","RHL","RHR","TFL","TFR","TML","TMR","TRL","TRR","TS","CH","TOP","FDL","FDR","SDL","SDR","BDL","BDR"));
    private static final Set<String> SUB=new HashSet<>(Arrays.asList("LFE","SW","SW1","SW2","SW3","SW4"));
    public static final class Channel {
        public final String slot,label;public final boolean active;
        Channel(String slot,String label,boolean active){this.slot=slot;this.label=label;this.active=active;}
    }
    public static final class Panel {
        public final List<Channel> channels;public final String layout;public final long updatedAt;
        Panel(List<Channel> channels,String layout){this.channels=Collections.unmodifiableList(new ArrayList<>(channels));this.layout=layout;updatedAt=monotonicMillis();}
        private static long monotonicMillis(){return System.nanoTime()/1000000;}
        public boolean fresh(){return monotonicMillis()-updatedAt<10000;}
    }
    public static Panel parse(Document d,String name,String prefix){
        Element cmd=null;NodeList cmds=d.getDocumentElement().getChildNodes();
        for(int i=0;i<cmds.getLength();i++)if(cmds.item(i) instanceof Element){Element c=(Element)cmds.item(i);if(c.getTagName().equals("cmd")&&ReceiverHttp.text(c,"name").equals(name)){cmd=c;break;}}
        if(cmd==null||cmd.getElementsByTagName("error").getLength()>0)return null;
        NodeList params=cmd.getElementsByTagName("param");if(params.getLength()==0)return null;
        List<Channel> channels=new ArrayList<>();Set<String> seen=new HashSet<>(),active=new HashSet<>();
        for(int i=0;i<params.getLength();i++){
            Element p=(Element)params.item(i);String key=p.getAttribute("name"),control=p.getAttribute("control"),label=p.getTextContent().trim();
            if(!key.matches(prefix+"[a-e][1-4]")||!Arrays.asList("0","1","2").contains(control)||label.length()>32)return null;
            String slot=key.substring(prefix.length());if(!seen.add(slot))return null;if(control.equals("0"))continue;if(label.isEmpty())return null;
            channels.add(new Channel(slot,label,control.equals("2")));if(control.equals("2"))active.add(label.toUpperCase(Locale.ROOT));
        }
        int bed=0,height=0,sub=0;boolean known=true;
        for(String label:active){if(BED.contains(label))bed++;else if(HEIGHT.contains(label))height++;else if(SUB.contains(label))sub++;else known=false;}
        String layout=active.isEmpty()?"No active channels":!known?null:bed+"."+sub+(height>0?"."+height:"");
        return new Panel(channels,layout);
    }
}
