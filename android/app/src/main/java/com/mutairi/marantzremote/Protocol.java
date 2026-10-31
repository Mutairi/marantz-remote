package com.mutairi.marantzremote;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

public final class Protocol {
    private static final Set<String> ACTIONS=new HashSet<>(Arrays.asList("MVUP","MVDOWN","MUON","MUOFF","PWON","PWSTANDBY",
        "SITV","SIBD","SIDVD","SIGAME","SIMPLAY","SICD","SIPHONO","SINET",
        "MSSTEREO","MSDIRECT","MSPURE DIRECT","MSDOLBY DIGITAL","MSDTS SURROUND","MSMCH STEREO"));
    public static boolean allowed(String command) { return ACTIONS.contains(command)||command.matches("IL[2-5]")||command.matches("MV[0-9]{2,3}")||command.matches("PSTONE CTRL (ON|OFF)")||command.matches("PSLFC (ON|OFF)")||command.matches("PSCNTAMT 0[1-7]")||command.matches("PSBAS (UP|DOWN)"); }
    public static Double volume(String line) {
        if (!line.matches("MV[0-9]{2,3}")) return null;
        String n=line.substring(2);
        return Integer.parseInt(n.substring(0,2)) + (n.length()==3?(n.charAt(2)-'0')/10.0:0) - 80;
    }
    public static String volumeRequest(String command,double current,double limit) {
        double target;
        if(command.equals("MVUP"))target=current+.5;
        else if(command.equals("MVDOWN"))target=current-.5;
        else {Double parsed=volume(command);if(parsed==null)throw new IllegalArgumentException("Invalid volume");target=parsed;}
        if(target < -80||target > 18||target*2!=Math.rint(target*2))throw new IllegalArgumentException("Invalid volume");
        if(target>limit&&target>current)throw new IllegalArgumentException("App volume limit reached ("+limit+" dB)");
        int n=(int)Math.round((target+80)*2);
        return String.format(java.util.Locale.US,"MV%02d%s",n/2,n%2==0?"":"5");
    }
    public static String modeKey(String value){return value.toUpperCase(java.util.Locale.ROOT).replace("+","PLUS").replaceAll("[^A-Z0-9]","").replace("MULTICHSTEREO","MCHSTEREO").replace("DOLBYDIGITALPLUS","DDPLUS").replace("DSURR","DSUR");}
    public static boolean confirmed(String command,String line){
        if(command.startsWith("MV")){Double expected=volume(command),actual=volume(line);return expected!=null&&expected.equals(actual);}
        if(command.startsWith("PS"))return command.equals(line);
        if(command.startsWith("MU")||command.startsWith("PW")||command.startsWith("SI"))return command.equals(line);
        if(command.startsWith("MS")){String actual=line.startsWith("SYSMI ")?line.substring(6):line.startsWith("MS")?line.substring(2):"";return modeKey(command.substring(2)).equals(modeKey(actual));}
        return false;
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
