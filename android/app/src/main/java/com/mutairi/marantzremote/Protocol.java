package com.mutairi.marantzremote;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

public final class Protocol {
    private static final Set<String> ACTIONS=new HashSet<>(Arrays.asList("MVUP","MVDOWN","MUON","MUOFF","PWON","PWSTANDBY",
        "SITV","SIBD","SIDVD","SIGAME","SIMPLAY","SICD","SIPHONO","SINET",
        "MSSTEREO","MSDIRECT","MSPURE DIRECT","MSDOLBY DIGITAL","MSDTS SURROUND","MSMCH STEREO"));
    public static boolean allowed(String command) { return ACTIONS.contains(command); }
    public static Double volume(String line) {
        if (!line.matches("MV[0-9]{2,3}")) return null;
        String n=line.substring(2);
        return Integer.parseInt(n.substring(0,2)) + (n.length()==3?(n.charAt(2)-'0')/10.0:0) - 80;
    }
    public static boolean localAddress(String ip) {
        String[] parts=ip.split("\\.");
        if(parts.length!=4) return false;
        int[] n=new int[4];
        try { for(int i=0;i<4;i++) { n[i]=Integer.parseInt(parts[i]); if(n[i]<0||n[i]>255) return false; } }
        catch(NumberFormatException e) {return false;}
        return n[0]==10 || (n[0]==192&&n[1]==168) || (n[0]==172&&n[1]>=16&&n[1]<=31);
    }
    private Protocol() {}
}
