package com.mutairi.marantzremote;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Discovery {
    public static final class Device {
        public final String name,ip;
        Device(String name,String ip) {this.name=name;this.ip=ip;}
        public String toString(){return name+"\n"+ip;}
    }
    public static List<Device> search(Context context) throws IOException {
        WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock=wifi.createMulticastLock("marantz-discovery");lock.setReferenceCounted(false);
        LinkedHashMap<String,Device> devices=new LinkedHashMap<>();Set<String> urls=new LinkedHashSet<>();
        lock.acquire();
        try(DatagramSocket udp=new DatagramSocket()) {
            udp.setSoTimeout(600);
            for(String target:new String[]{"urn:schemas-upnp-org:device:MediaRenderer:1","ssdp:all"}) {
                byte[] msg=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: "+target+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                udp.send(new DatagramPacket(msg,msg.length,InetAddress.getByName("239.255.255.250"),1900));
            }
            long end=System.currentTimeMillis()+4500;
            while(System.currentTimeMillis()<end&&urls.size()<64) {
                byte[] buf=new byte[16384];DatagramPacket reply=new DatagramPacket(buf,buf.length);
                try {udp.receive(reply);}catch(SocketTimeoutException e){continue;}
                String source=reply.getAddress().getHostAddress();
                if(!Protocol.localAddress(source))continue;
                for(String line:new String(reply.getData(),0,reply.getLength(),StandardCharsets.UTF_8).split("\r?\n")) {
                    if(line.toLowerCase(Locale.ROOT).startsWith("location:")) {
                        String location=line.substring(9).trim();
                        try {URL url=new URL(location);if(url.getProtocol().equals("http")&&url.getHost().equals(source))urls.add(location);}catch(MalformedURLException ignored){}
                    }
                }
            }
        } finally {lock.release();}
        for(String location:urls) {
            try {
                URL url=new URL(location);if(devices.containsKey(url.getHost()))continue;
                HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(1200);c.setReadTimeout(1200);c.setInstanceFollowRedirects(false);
                try {
                    if(c.getResponseCode()!=200)continue;
                    byte[] data;
                    try(InputStream in=c.getInputStream()) {ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[4096];int count;while(bytes.size()<=131072&&(count=in.read(block))!=-1)bytes.write(block,0,count);data=bytes.toByteArray();}
                    if(data.length>131072)continue;
                    XmlPullParser parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,false);parser.setInput(new ByteArrayInputStream(data),null);
                    String maker="",name="",model="";
                    for(int e=parser.getEventType();e!=XmlPullParser.END_DOCUMENT;e=parser.next()) {
                        if(e==XmlPullParser.START_TAG) {
                            String tag=parser.getName();
                            if(tag.equals("manufacturer"))maker=parser.nextText();
                            else if(tag.equals("friendlyName")&&name.isEmpty())name=parser.nextText();
                            else if(tag.equals("modelName")&&model.isEmpty())model=parser.nextText();
                        }
                    }
                    if((maker+" "+name+" "+model).toLowerCase(Locale.ROOT).contains("marantz"))
                        devices.put(url.getHost(),new Device(name.isEmpty()?"Marantz "+model:name,url.getHost()));
                } finally {c.disconnect();}
            }catch(Exception ignored){}
        }
        return new ArrayList<>(devices.values());
    }
    private Discovery(){}
}
