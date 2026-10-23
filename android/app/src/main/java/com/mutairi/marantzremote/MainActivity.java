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
    private Button mute,scan,inputButton,modeButton;private ReceiverConnection connection;
    private SeekBar slider;private boolean tracking=false,commandPending=false;private Double currentVolume=null;private double volumeLimit=-10;
    private long lastReply=0;private TextView lastUpdate,limitText;
    private List<ReceiverHttp.Choice> inputs=new ArrayList<>(),modes=new ArrayList<>();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable freshness=new Runnable(){public void run(){if(!foreground)return;long age=lastReply==0?Long.MAX_VALUE:(System.currentTimeMillis()-lastReply)/1000;lastUpdate.setText(age==Long.MAX_VALUE?"Waiting for receiver response":"Last response · "+age+"s ago");enabled(connected);ui.postDelayed(this,1000);}};
    private boolean connected=false,foreground=false,scanning=false;private Boolean muted=null;
    private int generation=0;private String host="",receiverName="";
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);volumeLimit=getPreferences(0).getFloat("volumeLimit",-10);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
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
        slider=new SeekBar(this);slider.setContentDescription("Set master volume");body.addView(slider);limitText=text("",11,MUTED);renderSlider();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){tracking=true;}public void onProgressChanged(SeekBar s,int p,boolean user){if(user)limitText.setText(String.format(Locale.US,"Set to %.1f dB",p/2.0-80));}public void onStopTrackingTouch(SeekBar s){tracking=false;int n=s.getProgress();send(String.format(Locale.US,"MV%02d%s",n/2,n%2==0?"":"5"));}});
        section("POWER");row=row();addControl(row,"On",()->send("PWON"));addControl(row,"Standby",()->send("PWSTANDBY"));
        section("INPUT");source=text("—",14,GOLD);
        row=row();inputButton=addControl(row,"Choose input",()->chooseAvailable("Input",inputs));
        section("LISTENING MODE");mode=text("—",14,GOLD);
        row=row();modeButton=addControl(row,"Choose mode",()->chooseAvailable("Listening mode",modes));
        gap(20);text("INCOMING AUDIO",10,MUTED);format=text("—",14,GOLD);gap(16);
        lastUpdate=text("Waiting for receiver response",11,MUTED);
        notice=text("Controls follow the receiver’s reported inputs and listening modes.",12,MUTED);
        gap(12);body.addView(button("App volume limit",()->chooseLimit()));
        text("Limits this app only. Other remotes and receiver power-on settings can exceed it.",10,MUTED);
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
    private void enabled(boolean value){value=value&&!commandPending&&lastReply>0&&System.currentTimeMillis()-lastReply<12000;for(Button b:controls){b.setEnabled(value);b.setAlpha(value?1f:.35f);}if(mute!=null)mute.setEnabled(value&&muted!=null);if(inputButton!=null)inputButton.setEnabled(value&&!inputs.isEmpty());if(modeButton!=null)modeButton.setEnabled(value&&!modes.isEmpty());if(slider!=null)slider.setEnabled(value&&currentVolume!=null);}
    private void choose(String heading,String[] labels,String[] commands){new AlertDialog.Builder(this).setTitle(heading).setItems(labels,(d,i)->send(commands[i])).setNegativeButton("Cancel",null).show();}
    private void send(String command){String error=connection==null?"Receiver is disconnected.":connection.submit(command);if(error!=null){notice.setText(error);return;}commandPending=true;notice.setText("Waiting for receiver confirmation…");enabled(connected);}
    private void chooseAvailable(String heading,List<ReceiverHttp.Choice> list){List<ReceiverHttp.Choice> snapshot=new ArrayList<>(list);if(snapshot.isEmpty()){notice.setText("Waiting for the receiver’s capability list.");return;}String[] labels=new String[snapshot.size()];for(int i=0;i<labels.length;i++)labels[i]=snapshot.get(i).label;new AlertDialog.Builder(this).setTitle(heading).setItems(labels,(d,i)->send(snapshot.get(i).command)).setNegativeButton("Cancel",null).show();}
    private void chooseLimit(){double[] values={-40,-30,-20,-10,-5,0};String[] labels={"−40 dB","−30 dB","−20 dB","−10 dB","−5 dB","0 dB"};new AlertDialog.Builder(this).setTitle("App volume limit").setItems(labels,(d,i)->{volumeLimit=values[i];getPreferences(0).edit().putFloat("volumeLimit",(float)volumeLimit).apply();if(connection!=null)connection.setLimit(volumeLimit);renderSlider();notice.setText("Limit saved. Current receiver volume was not changed.");}).setNegativeButton("Cancel",null).show();}
    private void renderSlider(){slider.setMax((int)Math.round((volumeLimit+80)*2));if(!tracking)slider.setProgress(currentVolume==null?0:(int)Math.round((Math.min(currentVolume,volumeLimit)+80)*2));limitText.setText(String.format(Locale.US,"−80 dB     App limit %.1f dB",volumeLimit));}
    private void manual(){EditText input=new EditText(this);input.setHint("192.168.1.100");input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);input.setText(host);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Receiver IPv4 address").setView(input).setPositiveButton("Connect",null).setNegativeButton("Cancel",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String ip=input.getText().toString().trim();if(!Protocol.localAddress(ip)){input.setError("Enter a private IPv4 address on your Wi-Fi");return;}dialog.dismiss();connect(ip,"Marantz");}));dialog.show();}
    private void scan(){
        if(scanning)return;scanning=true;scan.setEnabled(false);scan.setText("Searching your Wi-Fi…");
        discovery.execute(()->{try{List<Discovery.Device> found=Discovery.search(this);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(!foreground)return;if(found.isEmpty())new AlertDialog.Builder(this).setTitle("No Marantz found").setMessage("Use the same Wi-Fi as the receiver, enable Network Control, and avoid guest Wi-Fi. You can also enter its IP manually. Some models do not support this discovery or control protocol.").setPositiveButton("OK",null).show();else{String[] labels=new String[found.size()];for(int i=0;i<found.size();i++)labels[i]=found.get(i).toString();new AlertDialog.Builder(this).setTitle("Choose Marantz").setItems(labels,(d,i)->{Discovery.Device device=found.get(i);connect(device.ip,device.name);}).setNegativeButton("Cancel",null).show();}});}catch(Exception e){runOnUiThread(()->notice.setText("Discovery failed: "+e.getMessage()+". Try entering the IP."));}finally{runOnUiThread(()->{scanning=false;scan.setEnabled(true);scan.setText("Find Marantz receivers");});}});
    }
    private void connect(String ip,String name){
        generation++;int current=generation;if(connection!=null)connection.close();host=ip;receiverName=name;connected=false;commandPending=false;lastReply=0;inputs=new ArrayList<>();modes=new ArrayList<>();reset();title.setText(name);status.setText("Connecting · "+ip);enabled(false);
        getPreferences(0).edit().putString("host",ip).putString("name",name).apply();
        connection=new ReceiverConnection(ip,new ReceiverConnection.Listener(){
            public void status(boolean ok,String detail){runOnUiThread(()->{if(current!=generation||!foreground)return;connected=ok;reset();enabled(ok);status.setText((ok?"Connected · ":"")+detail);});}
            public void line(String line){runOnUiThread(()->{if(current!=generation||!foreground)return;parse(line);});}
            public void capabilities(List<ReceiverHttp.Choice> in,List<ReceiverHttp.Choice> sound){runOnUiThread(()->{if(current!=generation||!foreground)return;inputs=in;modes=sound;enabled(connected);});}
            public void feedback(boolean pending,String message){runOnUiThread(()->{if(current!=generation||!foreground)return;commandPending=pending;notice.setText(message);notice.setTextColor(message.startsWith("Confirmed")?Color.rgb(183,209,174):MUTED);enabled(connected);});}
        });
        connection.setLimit(volumeLimit);
    }
    private void reset(){muted=null;currentVolume=null;if(slider!=null)renderSlider();volume.setText("—");source.setText("—");mode.setText("—");format.setText("—");mute.setText("Mute");}
    private void parse(String line){
        if(!line.startsWith("→ "))lastReply=System.currentTimeMillis();
        activity.addLast(line);while(activity.size()>65)activity.removeFirst();log.setText(android.text.TextUtils.join("\n",activity));
        Double v=Protocol.volume(line);if(v!=null){currentVolume=v;volume.setText(String.format(Locale.US,"%.1f",v));renderSlider();}
        else if(line.equals("MUON")||line.equals("MUOFF")){muted=line.equals("MUON");mute.setText(muted?"Unmute":"Mute");mute.setEnabled(connected&&!commandPending);}
        else if(line.equals("PWON")||line.equals("PWSTANDBY"))status.setText("Connected · "+(line.equals("PWON")?"On":"Standby")+" · "+host);
        else if(line.startsWith("SI"))source.setText(line.substring(2));
        else if(line.startsWith("MS"))mode.setText(line.substring(2));
        else if(line.startsWith("SYSMI "))mode.setText(line.substring(6).trim());
        else if(line.startsWith("SYSDA "))format.setText(line.substring(6).trim());
        enabled(connected);
    }
    @Override protected void onStart(){super.onStart();foreground=true;ui.post(freshness);if(!host.isEmpty())connect(host,receiverName);else scan();}
    @Override protected void onStop(){foreground=false;ui.removeCallbacks(freshness);generation++;if(connection!=null)connection.close();connection=null;connected=false;enabled(false);super.onStop();}
    @Override protected void onDestroy(){discovery.shutdownNow();super.onDestroy();}
}
