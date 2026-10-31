package com.mutairi.marantzremote;

import java.util.concurrent.*;

/** Dedicated HTTP polling: never blocks the command/Telnet worker. */
public final class ChannelMonitor implements AutoCloseable {
    public interface Source { SpeakerChannels.Panel read(boolean input)throws Exception; default String illumination()throws Exception{return null;} }
    public interface Listener { void channels(SpeakerChannels.Panel input,SpeakerChannels.Panel output); default void illumination(String value){} }
    private final Source source;private final Listener listener;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"speaker-channels");t.setDaemon(true);return t;});
    private String signature="";private long epoch=0;private boolean enabled=false,closed=false;
    public ChannelMonitor(Source source,Listener listener){this.source=source;this.listener=listener;}
    public void start(){worker.scheduleWithFixedDelay(this::refresh,0,3,TimeUnit.SECONDS);}
    public synchronized void route(String signature,boolean enabled){
        if(closed)return;
        if(this.enabled!=enabled||!this.signature.equals(signature)){this.enabled=enabled;this.signature=signature;invalidate();}
    }
    public synchronized void invalidate(){epoch++;listener.channels(null,null);listener.illumination(null);}
    void refresh(){
        long version;synchronized(this){if(closed||!enabled)return;version=epoch;}
        SpeakerChannels.Panel input=null,output=null;
        try{input=source.read(true);}catch(Exception ignored){}
        synchronized(this){if(closed||version!=epoch)return;}
        try{output=source.read(false);}catch(Exception ignored){}
        String illumination=null;try{illumination=source.illumination();}catch(Exception ignored){}
        synchronized(this){if(!closed&&enabled&&version==epoch){listener.channels(input,output);listener.illumination(illumination);}}
    }
    public synchronized void close(){closed=true;enabled=false;epoch++;worker.shutdownNow();listener.channels(null,null);listener.illumination(null);}
}
