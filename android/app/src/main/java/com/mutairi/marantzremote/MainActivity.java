package com.mutairi.marantzremote;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(19,19,17),FG=Color.rgb(239,237,229),MUTED=Color.rgb(170,166,151),GOLD=Color.rgb(226,201,158);
    private final ExecutorService discovery=Executors.newSingleThreadExecutor();
    private final ArrayList<Button> controls=new ArrayList<>();
    private final ArrayDeque<String> activity=new ArrayDeque<>();
    private LinearLayout body;private TextView status,volume,source,mode,format,notice,log,title;
    private Button mute,scan;private ReceiverConnection connection;
    private boolean connected=false,foreground=false,scanning=false;private Boolean muted=null;
    private int generation=0;private String host="",receiverName="";
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(25),dp(40),dp(25),dp(30));scroll.addView(body);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{body.setPadding(dp(25),dp(28)+insets.getSystemWindowInsetTop(),dp(25),dp(24)+insets.getSystemWindowInsetBottom());return insets;});
        ImageView logo=new ImageView(this);logo.setImageResource(com.mutairi.marantzremote.R.drawable.marantz_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(200),dp(36));lp.gravity=Gravity.CENTER_HORIZONTAL;body.addView(logo,lp);
        title=text("PERSONAL REMOTE",11,MUTED);title.setLetterSpacing(.2f);title.setGravity(Gravity.CENTER);gap(15);
        status=text("Choose a receiver on your Wi-Fi",13,MUTED);status.setGravity(Gravity.CENTER);gap(15);
        scan=button("Find Marantz receivers",()->scan());body.addView(scan);gap(8);
        Button manual=button("Enter receiver IP",()->manual());body.addView(manual);gap(24);
        volume=text("—",64,FG);volume.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));volume.setGravity(Gravity.CENTER);
        TextView caption=text("MASTER VOLUME · dB",10,MUTED);caption.setGravity(Gravity.CENTER);gap(14);
        LinearLayout row=row();addControl(row,"−",()->send("MVDOWN"));mute=addControl(row,"Mute",()->{if(muted!=null)send(muted?"MUOFF":"MUON");});addControl(row,"+",()->send("MVUP"));
        section("POWER");row=row();addControl(row,"On",()->send("PWON"));addControl(row,"Standby",()->send("PWSTANDBY"));
        section("INPUT");source=text("—",14,GOLD);
        row=row();addControl(row,"Choose input",()->choose("Input",new String[]{"TV audio","Blu-ray","DVD","Game","Media player","CD","Phono","Network / HEOS"},new String[]{"SITV","SIBD","SIDVD","SIGAME","SIMPLAY","SICD","SIPHONO","SINET"}));
        section("LISTENING MODE");mode=text("—",14,GOLD);
        row=row();addControl(row,"Choose mode",()->choose("Listening mode",new String[]{"Stereo","Direct","Pure Direct","Dolby Surround","DTS Neural:X","Multi Ch Stereo"},new String[]{"MSSTEREO","MSDIRECT","MSPURE DIRECT","MSDOLBY DIGITAL","MSDTS SURROUND","MSMCH STEREO"}));
        gap(20);text("INCOMING AUDIO",10,MUTED);format=text("—",14,GOLD);gap(16);
        notice=text("Available controls depend on your receiver and incoming audio.",12,MUTED);
        gap(16);Button activityButton=button("Receiver activity",()->log.setVisibility(log.getVisibility()==View.GONE?View.VISIBLE:View.GONE));body.addView(activityButton);
        log=text("",11,MUTED);log.setTypeface(Typeface.MONOSPACE);log.setVisibility(View.GONE);gap(20);
        TextView foot=text("UNOFFICIAL · LOCAL WI-FI ONLY",10,MUTED);foot.setGravity(Gravity.CENTER);
        enabled(false);
        host=getPreferences(0).getString("host","");receiverName=getPreferences(0).getString("name","Marantz");
    }
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));body.addView(t);return t;}
    private void gap(int n){View v=new View(this);body.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private void section(String value){gap(23);View line=new View(this);line.setBackgroundColor(Color.rgb(48,48,41));body.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));gap(16);TextView t=text(value,10,MUTED);t.setLetterSpacing(.17f);gap(6);}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);body.addView(r);return r;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextColor(FG);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(52));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(35,35,31));bg.setCornerRadius(dp(12));bg.setStroke(dp(1),Color.rgb(55,55,47));b.setBackground(bg);b.setOnClickListener(v->action.run());return b;}
    private Button addControl(LinearLayout row,String label,Runnable action){Button b=button(label,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1);p.setMargins(dp(3),0,dp(3),0);row.addView(b,p);controls.add(b);return b;}
    private void enabled(boolean value){for(Button b:controls){b.setEnabled(value);b.setAlpha(value?1f:.35f);}if(mute!=null)mute.setEnabled(value&&muted!=null);}
    private void choose(String heading,String[] labels,String[] commands){new AlertDialog.Builder(this).setTitle(heading).setItems(labels,(d,i)->send(commands[i])).setNegativeButton("Cancel",null).show();}
    private void send(String command){if(connection==null||!connection.send(command))notice.setText("Not connected. Wait for the receiver or choose another one.");else notice.setText("Command sent. Watch the receiver status above; unsupported modes may be ignored.");}
    private void manual(){EditText input=new EditText(this);input.setHint("192.168.1.100");input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);input.setText(host);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Receiver IPv4 address").setView(input).setPositiveButton("Connect",null).setNegativeButton("Cancel",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String ip=input.getText().toString().trim();if(!Protocol.localAddress(ip)){input.setError("Enter a private IPv4 address on your Wi-Fi");return;}dialog.dismiss();connect(ip,"Marantz");}));dialog.show();}
    private void scan(){
        if(scanning)return;scanning=true;scan.setEnabled(false);scan.setText("Searching your Wi-Fi…");
        discovery.execute(()->{try{List<Discovery.Device> found=Discovery.search(this);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(!foreground)return;if(found.isEmpty())new AlertDialog.Builder(this).setTitle("No Marantz found").setMessage("Use the same Wi-Fi as the receiver, enable Network Control, and avoid guest Wi-Fi. You can also enter its IP manually. Some models do not support this discovery or control protocol.").setPositiveButton("OK",null).show();else{String[] labels=new String[found.size()];for(int i=0;i<found.size();i++)labels[i]=found.get(i).toString();new AlertDialog.Builder(this).setTitle("Choose Marantz").setItems(labels,(d,i)->{Discovery.Device device=found.get(i);connect(device.ip,device.name);}).setNegativeButton("Cancel",null).show();}});}catch(Exception e){runOnUiThread(()->notice.setText("Discovery failed: "+e.getMessage()+". Try entering the IP."));}finally{runOnUiThread(()->{scanning=false;scan.setEnabled(true);scan.setText("Find Marantz receivers");});}});
    }
    private void connect(String ip,String name){
        generation++;int current=generation;if(connection!=null)connection.close();host=ip;receiverName=name;connected=false;reset();title.setText(name);status.setText("Connecting · "+ip);enabled(false);
        getPreferences(0).edit().putString("host",ip).putString("name",name).apply();
        connection=new ReceiverConnection(ip,new ReceiverConnection.Listener(){
            public void status(boolean ok,String detail){runOnUiThread(()->{if(current!=generation||!foreground)return;connected=ok;reset();enabled(ok);status.setText((ok?"Connected · ":"")+detail);});}
            public void line(String line){runOnUiThread(()->{if(current!=generation||!foreground)return;parse(line);});}
        });
    }
    private void reset(){muted=null;volume.setText("—");source.setText("—");mode.setText("—");format.setText("—");mute.setText("Mute");}
    private void parse(String line){
        activity.addLast(line);while(activity.size()>65)activity.removeFirst();log.setText(android.text.TextUtils.join("\n",activity));
        Double v=Protocol.volume(line);if(v!=null)volume.setText(String.format(Locale.US,"%.1f",v));
        else if(line.equals("MUON")||line.equals("MUOFF")){muted=line.equals("MUON");mute.setText(muted?"Unmute":"Mute");mute.setEnabled(connected);}
        else if(line.equals("PWON")||line.equals("PWSTANDBY"))status.setText("Connected · "+(line.equals("PWON")?"On":"Standby")+" · "+host);
        else if(line.startsWith("SI"))source.setText(line.substring(2));
        else if(line.startsWith("MS"))mode.setText(line.substring(2));
        else if(line.startsWith("SYSMI "))mode.setText(line.substring(6).trim());
        else if(line.startsWith("SYSDA "))format.setText(line.substring(6).trim());
    }
    @Override protected void onStart(){super.onStart();foreground=true;if(!host.isEmpty())connect(host,receiverName);else scan();}
    @Override protected void onStop(){foreground=false;generation++;if(connection!=null)connection.close();connection=null;connected=false;enabled(false);super.onStop();}
    @Override protected void onDestroy(){discovery.shutdownNow();super.onDestroy();}
}
