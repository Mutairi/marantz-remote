package com.mutairi.marantzremote;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

public final class ReceiverConnection implements AutoCloseable {
    public interface Listener { void status(boolean connected,String detail); void line(String line); }
    private final String host;
    private final Listener listener;
    private final LinkedBlockingQueue<String> commands=new LinkedBlockingQueue<>(30);
    private volatile boolean running=true,connected=false;
    private volatile Socket socket;
    public ReceiverConnection(String host,Listener listener) {
        this.host=host;this.listener=listener;
        Thread worker=new Thread(this::run,"receiver");worker.setDaemon(true);worker.start();
    }
    public boolean send(String cmd) { return running&&connected&&Protocol.allowed(cmd)&&commands.offer(cmd); }
    private void run() {
        while(running) {
            try(Socket s=new Socket()) {
                socket=s;s.connect(new InetSocketAddress(host,23),3000);s.setSoTimeout(200);s.setTcpNoDelay(true);
                if(!running) break;
                connected=true;listener.status(true,host);
                InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
                long poll=0,last=System.currentTimeMillis();StringBuilder buffer=new StringBuilder();byte[] bytes=new byte[8192];
                while(running) {
                    String command;
                    while((command=commands.poll())!=null) { out.write((command+"\r").getBytes(StandardCharsets.US_ASCII));listener.line("→ "+command); }
                    long now=System.currentTimeMillis();
                    if(now-poll>8000) { out.write("PW?\rMV?\rMU?\rSI?\rMS?\r".getBytes(StandardCharsets.US_ASCII));poll=now; }
                    if(now-last>25000) throw new IOException("Receiver not responding");
                    int n;
                    try { n=in.read(bytes); } catch(SocketTimeoutException e) { continue; }
                    if(n<0) throw new EOFException("Receiver disconnected");
                    last=System.currentTimeMillis();
                    for(int i=0;i<n;i++) {
                        char c=(char)(bytes[i]&255);
                        if(c=='\r') {String line=buffer.toString().trim();buffer.setLength(0);if(!line.isEmpty())listener.line(line);}
                        else if(c!='\n'&&c>=32&&c<127) {buffer.append(c);if(buffer.length()>8192)buffer.setLength(0);}
                    }
                }
            } catch(IOException e) { if(running) listener.status(false,"Retrying · "+e.getMessage()); }
            finally {connected=false;socket=null;commands.clear();}
            if(running) try {Thread.sleep(2500);} catch(InterruptedException e) {return;}
        }
    }
    public void close() {
        running=false;connected=false;commands.clear();
        Socket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}
    }
}
