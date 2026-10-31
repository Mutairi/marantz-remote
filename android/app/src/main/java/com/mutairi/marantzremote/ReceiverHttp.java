package com.mutairi.marantzremote;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Bounded local-only receiver HTTP API, using the verified AppCommand0300 format. */
public final class ReceiverHttp {
    public interface Transport { String request(String path,String body) throws Exception; }
    public static final class Choice {
        public final String label,command,rawLabel;
        public Choice(String label,String command){this(label,command,label);}
        public Choice(String label,String command,String rawLabel){this.label=label;this.command=command;this.rawLabel=rawLabel;}
    }
    public static final class Mode {
        public final String number,label;public final boolean selected;
        Mode(String n,String l,boolean s){number=n;label=l;selected=s;}
    }
    public static final class ModeList {
        public final String genre;public final List<Mode> items;
        ModeList(String genre,List<Mode> items){this.genre=genre;this.items=items;}
        public List<Choice> choices(){
            List<Choice> out=new ArrayList<>();
            boolean plus=items.stream().anyMatch(m->m.label.equalsIgnoreCase("Dolby Audio - Dolby Digital Plus"));
            for(Mode m:items){String label=plus&&m.label.equalsIgnoreCase("Dolby Atmos/DSurr")?"Dolby Digital Plus + Dolby Surround":display(m.label);out.add(new Choice(label,"MODE:"+genre+":"+m.number+":"+token(m.label),m.label));}
            if(items.stream().noneMatch(m->m.label.equalsIgnoreCase("Direct")))out.add(new Choice("Direct","MSDIRECT"));
            if(items.stream().noneMatch(m->m.label.equalsIgnoreCase("Pure Direct")))out.add(new Choice("Pure Direct","MSPURE DIRECT"));
            return out;
        }
    }
    private final Transport transport;private final Transport[] lightingTransports;private volatile Transport lightingTransport;
    public ReceiverHttp(String host){
        if(!Protocol.localAddress(host))throw new IllegalArgumentException("Private IPv4 required");
        transport=localTransport(host,8080);lightingTransports=new Transport[]{localTransport(host,11080),localTransport(host,80)};
    }
    private static Transport localTransport(String host,int port){
        return (path,body)->{
            HttpURLConnection c=(HttpURLConnection)new URL("http://"+host+":"+port+path).openConnection();
            c.setConnectTimeout(1500);c.setReadTimeout(1500);c.setInstanceFollowRedirects(false);
            try{
                if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
                if(c.getResponseCode()!=200)throw new IOException("Receiver HTTP "+c.getResponseCode());
                try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                    byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){bytes.write(buf,0,n);if(bytes.size()>262144)throw new IOException("Oversized receiver response");}
                    return bytes.toString("UTF-8");
                }
            }finally{c.disconnect();}
        };
    }
    ReceiverHttp(Transport transport){this.transport=transport;lightingTransports=new Transport[]{transport};}
    static Document xml(String text)throws Exception{
        if(text.length()>262144||text.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")||text.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new IOException("Unsafe receiver XML");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setExpandEntityReferences(false);
        DocumentBuilder b=f.newDocumentBuilder();b.setEntityResolver((publicId,systemId)->{throw new IOException("External XML entity blocked");});
        return b.parse(new InputSource(new StringReader(text)));
    }
    static String text(Element e,String tag){NodeList nodes=e.getElementsByTagName(tag);return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();}
    private Document call(String name,String param,String value)throws Exception{
        if(!Arrays.asList("GetSoundModeList","SetSoundModeList").contains(name)||!Arrays.asList("genrelist","listno").contains(param)||!value.matches("[0-9]*"))throw new IllegalArgumentException("Invalid mode command");
        String body="<?xml version='1.0' encoding='utf-8'?>\n<tx><cmd id=\"3\"><name>"+name+"</name><list><param name=\""+param+"\">"+value+"</param></list></cmd></tx>";
        Document d=xml(transport.request("/goform/AppCommand0300.xml",body));if(d.getElementsByTagName("error").getLength()>0)throw new IOException("Receiver rejected request");return d;
    }
    private String readIllumination(Transport t)throws Exception{
        Document d=xml(t.request("/ajax/general/get_config?type=18",null));Element root=d.getDocumentElement();
        String value=text(root,"IlluminationBrightness");
        if(!root.getTagName().equals("FrontPanel")||!value.matches("[1-5]"))throw new IOException("LED illumination unavailable");
        String display=text(root,"DisplaysBrightness");
        if(!display.matches("[1-4]"))throw new IOException("Front display unavailable");
        return !value.equals("1")&&Integer.parseInt(display)==Integer.parseInt(value)-1?value:"1";
    }
    public String illumination()throws Exception{
        if(lightingTransport!=null)try{return readIllumination(lightingTransport);}catch(Exception ignored){}
        for(Transport t:lightingTransports)try{String value=readIllumination(t);lightingTransport=t;return value;}catch(Exception ignored){}
        throw new IOException("LED illumination unavailable");
    }
    public String setIllumination(String value)throws Exception{
        if(value==null||!value.matches("[2-5]"))throw new IllegalArgumentException("Invalid LED illumination");
        String before=illumination();Transport target=lightingTransport;
        if(!before.equals(value)){
            target.request("/ajax/general/set_config?type=18&data="+URLEncoder.encode("<DisplaysBrightness>"+(Integer.parseInt(value)-1)+"</DisplaysBrightness>","UTF-8"),null);
            target.request("/ajax/general/set_config?type=18&data="+URLEncoder.encode("<IlluminationBrightness>"+value+"</IlluminationBrightness>","UTF-8"),null);
        }
        String after=readIllumination(target);
        for(int attempt=0;attempt<5&&!after.equals(value);attempt++){Thread.sleep(150);after=readIllumination(target);}
        if(!after.equals(value))throw new IOException("Receiver did not confirm LED illumination. No automatic retry made.");
        return after;
    }
    public SpeakerChannels.Panel channels(boolean input)throws Exception{
        String name=input?"GetInputSignal":"GetActiveSpeaker",prefix=input?"inputsig":"activesp";
        String body="<?xml version='1.0' encoding='utf-8'?>\n<tx><cmd id=\"3\"><name>"+name+"</name><list><param name=\""+prefix+"all\"></param></list></cmd></tx>";
        return SpeakerChannels.parse(xml(transport.request("/goform/AppCommand0300.xml",body)),name,prefix);
    }
    public ModeList modes()throws Exception{
        Document d=call("GetSoundModeList","genrelist","");String genre="";
        NodeList params=d.getElementsByTagName("param");for(int i=0;i<params.getLength();i++){Element e=(Element)params.item(i);if(e.getAttribute("name").equals("genrelist"))genre=e.getTextContent().trim();}
        List<Mode> items=new ArrayList<>();NodeList values=d.getElementsByTagName("value");
        for(int i=0;i<values.getLength();i++){Element e=(Element)values.item(i);String number=text(e,"listno"),label=text(e,"dispname");if(number.matches("[0-9]+")&&!label.isEmpty())items.add(new Mode(number,label,text(e,"selected").equals("1")));}
        if(!genre.matches("[1-4]")||items.isEmpty())throw new IOException("Mode list unavailable");return new ModeList(genre,items);
    }
    public String selectMode(String command,String expectedLabel)throws Exception{
        String[] p=command.split(":");if(p.length!=4||!p[0].equals("MODE"))throw new IllegalArgumentException("Invalid selection");
        ModeList before=modes();Mode target=null;
        for(Mode m:before.items)if(before.genre.equals(p[1])&&m.number.equals(p[2])&&p[3].equals(token(m.label))&&m.label.equals(expectedLabel))target=m;
        if(target==null)throw new IOException("Modes changed. Open the mode list again.");
        if(!target.selected)call("SetSoundModeList","listno",target.number);
        ModeList after=modes();for(Mode m:after.items)if(after.genre.equals(before.genre)&&m.number.equals(target.number)&&m.label.equals(target.label)&&m.selected)return m.label;
        throw new IOException("Selection not confirmed. No automatic retry made.");
    }
    public List<Choice> inputs()throws Exception{
        Document d=xml(transport.request("/goform/Deviceinfo.xml",null));Element root=d.getDocumentElement();
        if(!text(root,"BrandCode").equals("1"))throw new IOException("Not a verified Marantz receiver");
        Map<String,String> codes=new HashMap<>();String[][] mappings={{"CBL/SAT","SAT/CBL"},{"MEDIA PLAYER","MPLAY"},{"BLU-RAY","BD"},{"DVD","DVD"},{"GAME1","GAME"},{"GAME2","GAME2"},{"GAME","GAME"},{"AUX1","AUX1"},{"AUX2","AUX2"},{"TV AUDIO","TV"},{"CD","CD"},{"PHONO","PHONO"},{"TUNER","TUNER"},{"NETWORK","NET"},{"BLUETOOTH","BT"}};for(String[] m:mappings)codes.put(m[0],m[1]);
        List<Choice> out=new ArrayList<>();NodeList zones=d.getElementsByTagName("DeviceZoneCapabilities");
        for(int i=0;i<zones.getLength();i++){Element zone=(Element)zones.item(i);NodeList zn=zone.getElementsByTagName("Zone");if(zn.getLength()==0||!text((Element)zn.item(0),"No").equals("0"))continue;
            NodeList inputs=zone.getElementsByTagName("InputSource");if(inputs.getLength()==0)continue;NodeList sources=((Element)inputs.item(0)).getElementsByTagName("Source");
            for(int j=0;j<sources.getLength();j++){Element e=(Element)sources.item(j);String code=codes.get(text(e,"FuncName").toUpperCase(Locale.ROOT));if(code!=null)out.add(new Choice(text(e,"DefaultName"),"SI"+code));}
        }
        try{
            Document names=xml(transport.request("/goform/AppCommand.xml","<?xml version='1.0' encoding='utf-8'?>\n<tx><cmd id=\"1\">GetRenameSource</cmd></tx>"));
            NodeList lists=names.getElementsByTagName("list");Map<String,String> renamed=new HashMap<>();
            for(int i=0;i<lists.getLength();i++){Element e=(Element)lists.item(i);String code=codes.get(text(e,"name").toUpperCase(Locale.ROOT)),label=text(e,"rename");if(code!=null&&!label.isEmpty())renamed.put("SI"+code,label);}
            for(int i=0;i<out.size();i++){Choice c=out.get(i);if(renamed.containsKey(c.command))out.set(i,new Choice(renamed.get(c.command),c.command));}
        }catch(Exception ignored){ /* Factory labels remain usable when rename API is absent. */ }
        return out;
    }
    static String token(String label){return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(label.getBytes(StandardCharsets.UTF_8));}
    static String display(String label){if(label.equalsIgnoreCase("Dolby Audio - Dolby Digital Plus"))return "Dolby Digital Plus · original";if(label.equalsIgnoreCase("Dolby Atmos/DSurr"))return "Dolby Atmos / Dolby Surround";return label;}
}
