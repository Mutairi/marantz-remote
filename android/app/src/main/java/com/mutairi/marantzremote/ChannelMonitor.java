package com.mutairi.marantzremote;

import java.util.concurrent.*;

/** Dedicated HTTP polling: never blocks the command/Telnet worker. */
public final class ChannelMonitor implements AutoCloseable {
    public interface Source { SpeakerChannels.Panel read(boolean input)throws Exception; }
    public interface Listener { void channels(SpeakerChannels.Panel input,SpeakerChannels.Panel output); }
    private final Source source;private final Listener listener;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"speaker-channels");t.setDaemon(true);return t;});
    private String signature="";private long epoch=0;private boolean enabled=false,closed=false;
    public ChannelMonitor(Source source,Listener listener){this.source=source;this.listener=listener;}
    public void start(){worker.scheduleWithFixedDelay(this::refresh,0,3,TimeUnit.SECONDS);}
    public synchronized void route(String signature,boolean enabled){
        if(closed)return;
        if(this.enabled!=enabled||!this.signature.equals(signature)){this.enabled=enabled;this.signature=signature;epoch++;listener.channels(null,null);}
    }
    void refresh(){
        long version;synchronized(this){if(closed||!enabled)return;version=epoch;}
        SpeakerChannels.Panel input=null,output=null;
        try{input=source.read(true);}catch(Exception ignored){}
        synchronized(this){if(closed||version!=epoch)return;}
        try{output=source.read(false);}catch(Exception ignored){}
        synchronized(this){if(!closed&&enabled&&version==epoch)listener.channels(input,output);}
    }
    public synchronized void close(){closed=true;enabled=false;epoch++;worker.shutdownNow();listener.channels(null,null);}
}
