package com.mutairi.marantzremote;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class ReceiverConnection implements AutoCloseable {
    public interface Listener {
        void status(boolean connected,String detail);
        void line(String line);
        default void capabilities(List<ReceiverHttp.Choice> inputs,List<ReceiverHttp.Choice> modes){}
        default void feedback(boolean pending,String message){}
        default void channels(SpeakerChannels.Panel input,SpeakerChannels.Panel output){}
    }
    private final ChannelMonitor channelMonitor;private String routePower="",routeInput="",routeFormat="";
    private final String host;private final Listener listener;private final ReceiverHttp http;
    private final LinkedBlockingQueue<String> commands=new LinkedBlockingQueue<>(40);
    private volatile boolean running=true,connected=false,waiting=false;private volatile Socket socket;
    private volatile double limit=-10;private volatile Double volume=null;private volatile long volumeAt=0,lastReceivedAt=0;
    private final Map<String,ReceiverHttp.Choice> advertised=new ConcurrentHashMap<>();
    private volatile Boolean tone=null,lfc=null;private volatile Integer bass=null;private volatile String mode="";
    private String expected=null;private long deadline=0;
    public ReceiverConnection(String host,Listener listener){
        if(!Protocol.localAddress(host))throw new IllegalArgumentException("Private IPv4 required");
        this.host=host;this.listener=listener;http=new ReceiverHttp(host);
        channelMonitor=new ChannelMonitor(http::channels,listener::channels);channelMonitor.start();
        Thread worker=new Thread(this::run,"receiver");worker.setDaemon(true);worker.start();
    }
    public void setLimit(double value){if(Double.isNaN(value)||value < -60||value > 0)throw new IllegalArgumentException("Invalid volume limit");limit=value;}
    public synchronized String submit(String cmd){
        if(!running||!connected)return "Receiver is disconnected. Retrying automatically.";
        if(System.currentTimeMillis()-lastReceivedAt>12000)return "Waiting for fresh receiver status.";
        if(waiting&&!cmd.matches("MV(UP|DOWN|[0-9]{2,3})|PSBAS (UP|DOWN)"))return "Wait for the current command to finish.";
        if((cmd.startsWith("SI")||cmd.startsWith("MODE:"))?!advertised.containsKey(cmd):!Protocol.allowed(cmd))return "This control is not advertised by the receiver.";
        if(!commands.offer(cmd)){return "Command queue is busy.";}waiting=true;return null;
    }
    private void finish(String message){expected=null;if(!message.startsWith("Confirmed"))commands.clear();waiting=!commands.isEmpty();listener.feedback(waiting,message);}
    private void write(OutputStream out,String cmd)throws IOException{out.write((cmd+"\r").getBytes(StandardCharsets.US_ASCII));listener.line("→ "+cmd);}
    private void accept(String line){
        lastReceivedAt=System.currentTimeMillis();
        Double v=Protocol.volume(line);if(v!=null){volume=v;volumeAt=System.currentTimeMillis();}
        if(line.equals("PSTONE CTRL ON")||line.equals("PSTONE CTRL OFF"))tone=line.endsWith(" ON");
        if(line.equals("PSLFC ON")||line.equals("PSLFC OFF"))lfc=line.endsWith(" ON");
        if(line.matches("PSBAS [0-9]{2}"))bass=Integer.parseInt(line.substring(6))-50;
        if(line.startsWith("SYSMI "))mode=line.substring(6).trim();else if(line.startsWith("MS"))mode=line.substring(2);
        if(line.equals("PWON")||line.equals("PWSTANDBY"))routePower=line;
        if(line.startsWith("SI"))routeInput=line.substring(2);
        if(line.startsWith("SYSDA "))routeFormat=line.substring(6).trim();
        channelMonitor.route(routePower+"|"+routeInput+"|"+Protocol.modeKey(mode)+"|"+routeFormat,connected&&routePower.equals("PWON"));
        listener.line(line);
        if(expected!=null&&Protocol.confirmed(expected,line))finish("Confirmed by receiver");
    }
    private void run(){
        List<ReceiverHttp.Choice> inputs=new ArrayList<>(),modes=new ArrayList<>();
        while(running){
            try(Socket s=new Socket()){
                socket=s;s.connect(new InetSocketAddress(host,23),3000);s.setSoTimeout(250);s.setTcpNoDelay(true);
                if(!running)break;
                channelMonitor.route("Connecting",false);routePower="";routeInput="";routeFormat="";
                volume=null;tone=null;lfc=null;bass=null;mode="";volumeAt=0;lastReceivedAt=0;connected=true;listener.status(true,host);
                InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
                try{inputs=http.inputs();}catch(Exception e){inputs=new ArrayList<>();listener.feedback(false,"Input discovery unavailable; check receiver Network Control.");}
                long poll=0,last=System.currentTimeMillis();StringBuilder buffer=new StringBuilder();byte[] bytes=new byte[8192];
                while(running){
                    String cmd=expected==null?commands.poll():null;
                    if(cmd!=null){
                        listener.feedback(true,"Waiting for receiver confirmation…");
                        try{
                            if(cmd.startsWith("MODE:")){
                                String selected=http.selectMode(cmd,advertised.get(cmd).rawLabel);
                                if(!running)break;
                                accept("SYSMI "+selected);write(out,"MS?");finish("Confirmed by receiver");
                            }else{
                                String wire=cmd;
                                if(cmd.equals("MVUP")||cmd.equals("MVDOWN")||cmd.matches("MV[0-9]{2,3}")){
                                    if(volume==null||System.currentTimeMillis()-volumeAt>12000)throw new IOException("Waiting for a fresh volume reading");
                                    wire=Protocol.volumeRequest(cmd,volume,limit);
                                }
                                if(cmd.startsWith("PS")){
                                    if(Arrays.asList("DIRECT","PUREDIRECT").contains(Protocol.modeKey(mode)))throw new IOException("Unavailable in Direct / Pure Direct");
                                    if(cmd.startsWith("PSTONE")&&tone==null)throw new IOException("Waiting for tone status");
                                    if(cmd.startsWith("PSLFC")&&lfc==null)throw new IOException("Waiting for LFC status");
                                    if(cmd.startsWith("PSCNTAMT")&&!Boolean.TRUE.equals(lfc))throw new IOException("Enable Audyssey LFC first");
                                    if(cmd.startsWith("PSBAS")){
                                        if(!Boolean.TRUE.equals(tone)||bass==null)throw new IOException("Enable Tone Control first");
                                        int target=bass+(cmd.endsWith("UP")?1:-1);if(target < -6||target > 6)throw new IOException("Bass limit reached");
                                        wire=String.format(Locale.US,"PSBAS %02d",target+50);
                                    }
                                }
                                write(out,wire);expected=wire;deadline=System.currentTimeMillis()+5000;
                                write(out,wire.startsWith("PSTONE")?"PSTONE CTRL ?":wire.startsWith("PSBAS")?"PSBAS ?":wire.startsWith("PSLFC")?"PSLFC ?":wire.startsWith("PSCNTAMT")?"PSCNTAMT ?":wire.startsWith("MV")?"MV?":wire.startsWith("MU")?"MU?":wire.startsWith("PW")?"PW?":wire.startsWith("SI")?"SI?":"MS?");
                            }
                        }catch(Exception e){finish(e.getMessage()+" · no automatic retry");}
                    }
                    long now=System.currentTimeMillis();
                    if(expected!=null&&now>deadline)finish("No matching receiver reply. Command was not retried.");
                    if(now-poll>8000){
                        for(String q:new String[]{"PW?","MV?","MU?","SI?","MS?","PSTONE CTRL ?","PSBAS ?","PSLFC ?","PSCNTAMT ?"})write(out,q);
                        try{inputs=http.inputs();}catch(Exception ignored){}
                        // The receiver's current genre list includes exact Dolby decoder/upmixer entries.
                        try{modes=http.modes().choices();}catch(Exception e){modes=new ArrayList<>(Arrays.asList(new ReceiverHttp.Choice("Stereo","MSSTEREO"),new ReceiverHttp.Choice("Direct","MSDIRECT"),new ReceiverHttp.Choice("Pure Direct","MSPURE DIRECT")));}
                        advertised.clear();for(ReceiverHttp.Choice c:inputs)advertised.put(c.command,c);for(ReceiverHttp.Choice c:modes)advertised.put(c.command,c);
                        listener.capabilities(new ArrayList<>(inputs),new ArrayList<>(modes));poll=now;
                    }
                    if(now-last>25000)throw new IOException("Receiver not responding");
                    int n;try{n=in.read(bytes);}catch(SocketTimeoutException e){continue;}
                    if(n<0)throw new EOFException("Receiver disconnected");last=System.currentTimeMillis();
                    for(int i=0;i<n;i++){
                        char c=(char)(bytes[i]&255);
                        if(c=='\r'){String line=buffer.toString().trim();buffer.setLength(0);if(!line.isEmpty())accept(line);}
                        else if(c!='\n'&&c>=32&&c<127){buffer.append(c);if(buffer.length()>8192)throw new IOException("Invalid receiver response");}
                    }
                }
            }catch(IOException e){if(running)listener.status(false,"Retrying · "+e.getMessage());}
            finally{connected=false;channelMonitor.route("Disconnected",false);socket=null;commands.clear();advertised.clear();if(waiting)finish("Disconnected before confirmation. Command was not replayed.");}
            if(running)try{Thread.sleep(2500);}catch(InterruptedException e){return;}
        }
    }
    public void close(){running=false;channelMonitor.close();connected=false;commands.clear();Socket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}}
}
