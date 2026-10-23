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
    }
    private final String host;private final Listener listener;private final ReceiverHttp http;
    private final LinkedBlockingQueue<String> commands=new LinkedBlockingQueue<>(1);
    private volatile boolean running=true,connected=false,waiting=false;private volatile Socket socket;
    private volatile double limit=-10;private volatile Double volume=null;private volatile long volumeAt=0,lastReceivedAt=0;
    private final Map<String,ReceiverHttp.Choice> advertised=new ConcurrentHashMap<>();
    private String expected=null;private long deadline=0;
    public ReceiverConnection(String host,Listener listener){
        if(!Protocol.localAddress(host))throw new IllegalArgumentException("Private IPv4 required");
        this.host=host;this.listener=listener;http=new ReceiverHttp(host);
        Thread worker=new Thread(this::run,"receiver");worker.setDaemon(true);worker.start();
    }
    public void setLimit(double value){if(Double.isNaN(value)||value < -60||value > 0)throw new IllegalArgumentException("Invalid volume limit");limit=value;}
    public synchronized String submit(String cmd){
        if(!running||!connected)return "Receiver is disconnected. Retrying automatically.";
        if(System.currentTimeMillis()-lastReceivedAt>12000)return "Waiting for fresh receiver status.";
        if(waiting)return "Wait for the current command to finish.";
        if((cmd.startsWith("SI")||cmd.startsWith("MODE:"))?!advertised.containsKey(cmd):!Protocol.allowed(cmd))return "This control is not advertised by the receiver.";
        waiting=true;if(!commands.offer(cmd)){waiting=false;return "Command queue is busy.";}return null;
    }
    private void finish(String message){expected=null;waiting=false;listener.feedback(false,message);}
    private void write(OutputStream out,String cmd)throws IOException{out.write((cmd+"\r").getBytes(StandardCharsets.US_ASCII));listener.line("→ "+cmd);}
    private void accept(String line){
        lastReceivedAt=System.currentTimeMillis();
        Double v=Protocol.volume(line);if(v!=null){volume=v;volumeAt=System.currentTimeMillis();}
        listener.line(line);
        if(expected!=null&&Protocol.confirmed(expected,line))finish("Confirmed by receiver");
    }
    private void run(){
        List<ReceiverHttp.Choice> inputs=new ArrayList<>(),modes=new ArrayList<>();
        while(running){
            try(Socket s=new Socket()){
                socket=s;s.connect(new InetSocketAddress(host,23),3000);s.setSoTimeout(250);s.setTcpNoDelay(true);
                if(!running)break;
                volume=null;volumeAt=0;lastReceivedAt=0;connected=true;listener.status(true,host);
                InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
                try{inputs=http.inputs();}catch(Exception e){inputs=new ArrayList<>();listener.feedback(false,"Input discovery unavailable; check receiver Network Control.");}
                long poll=0,last=System.currentTimeMillis();StringBuilder buffer=new StringBuilder();byte[] bytes=new byte[8192];
                while(running){
                    String cmd=commands.poll();
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
                                write(out,wire);expected=wire;deadline=System.currentTimeMillis()+5000;
                                write(out,wire.startsWith("MV")?"MV?":wire.startsWith("MU")?"MU?":wire.startsWith("PW")?"PW?":wire.startsWith("SI")?"SI?":"MS?");
                            }
                        }catch(Exception e){finish(e.getMessage()+" · no automatic retry");}
                    }
                    long now=System.currentTimeMillis();
                    if(expected!=null&&now>deadline)finish("No matching receiver reply. Command was not retried.");
                    if(now-poll>8000){
                        for(String q:new String[]{"PW?","MV?","MU?","SI?","MS?"})write(out,q);
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
            finally{connected=false;socket=null;commands.clear();advertised.clear();if(waiting)finish("Disconnected before confirmation. Command was not replayed.");}
            if(running)try{Thread.sleep(2500);}catch(InterruptedException e){return;}
        }
    }
    public void close(){running=false;connected=false;commands.clear();Socket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}}
}
