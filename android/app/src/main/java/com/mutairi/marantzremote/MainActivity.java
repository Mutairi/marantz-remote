package com.mutairi.marantzremote;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
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
    private Button illuminationButton;private String illuminationValue=null;private long illuminationAt=0;
    private SpeakerView inputSpeakers,outputSpeakers;private SpeakerChannels.Panel inputChannels,outputChannels;
    private LinearLayout body;private TextView status,volume,source,mode,format,notice,log,title;
    private Button mute,scan,inputButton,modeButton;private ReceiverConnection connection;
    private SeekBar slider;private boolean tracking=false,commandPending=false;private Double currentVolume=null;private double volumeLimit=-10;
    private long lastReply=0;private TextView limitText;
    private List<ReceiverHttp.Choice> inputs=new ArrayList<>(),modes=new ArrayList<>();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable freshness=new Runnable(){public void run(){if(!foreground)return;enabled(connected);ui.postDelayed(this,1000);}};
    private boolean connected=false,foreground=false,scanning=false;private Boolean muted=null;
    private LinearLayout soundPanel,connectionPanel;private Button toneButton,lfcButton,bassDown,bassUp,containmentButton,down,up;
    private Boolean tone=null,lfc=null;private Integer bass=null,containment=null;private String inputCode="",modeRaw="";private TextView bassValue,soundHint;
    private final ArrayList<Button> repeatControls=new ArrayList<>();private final Map<Button,Integer> presets=new HashMap<>();
    private int generation=0;private String host="",receiverName="";
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        setTheme(android.R.style.Theme_Material_NoActionBar);super.onCreate(state);volumeLimit=getPreferences(0).getFloat("volumeLimit",-10);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(25),dp(40),dp(25),dp(30));scroll.addView(body);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{body.setPadding(dp(25),dp(28)+insets.getSystemWindowInsetTop(),dp(25),dp(24)+insets.getSystemWindowInsetBottom());return insets;});
        ImageView logo=new ImageView(this);logo.setImageResource(com.mutairi.marantzremote.R.drawable.marantz_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(200),dp(36));lp.gravity=Gravity.CENTER_HORIZONTAL;body.addView(logo,lp);
        title=text("PERSONAL REMOTE · v1.4",11,MUTED);title.setLetterSpacing(.2f);title.setGravity(Gravity.CENTER);gap(15);
        status=text("Choose a receiver on your Wi-Fi",13,MUTED);status.setVisibility(View.GONE);
        volume=text("—",62,GOLD);volume.setTypeface(Typeface.create("sans-serif-thin",Typeface.NORMAL));volume.setGravity(Gravity.CENTER);
        body.removeView(volume);LinearLayout dial=new LinearLayout(this);dial.setOrientation(LinearLayout.VERTICAL);dial.setGravity(Gravity.CENTER);
        GradientDrawable ring=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff302e24,0xff12120f});ring.setShape(GradientDrawable.OVAL);ring.setStroke(dp(2),0xffb89a62);dial.setBackground(ring);dial.setElevation(dp(6));
        LinearLayout.LayoutParams dialParams=new LinearLayout.LayoutParams(dp(218),dp(218));dialParams.gravity=Gravity.CENTER_HORIZONTAL;dialParams.setMargins(0,dp(18),0,dp(26));body.addView(dial,dialParams);dial.addView(volume);
        TextView caption=new TextView(this);caption.setText("dB\nMASTER VOLUME");caption.setTextSize(9);caption.setTextColor(MUTED);caption.setGravity(Gravity.CENTER);caption.setLetterSpacing(.18f);dial.addView(caption);
        LinearLayout row=row();down=addControl(row,"−",()->send("MVDOWN"));repeatControls.add(down);mute=addControl(row,"Mute",()->{if(muted!=null)send(muted?"MUOFF":"MUON");});up=addControl(row,"+",()->send("MVUP"));repeatControls.add(up);
        slider=new SeekBar(this);slider.setContentDescription("Set master volume");body.addView(slider);limitText=text("",11,MUTED);renderSlider();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){tracking=true;}public void onProgressChanged(SeekBar s,int p,boolean user){if(user)limitText.setText(String.format(Locale.US,"Set to %.1f dB",p/2.0-80));}public void onStopTrackingTouch(SeekBar s){tracking=false;int n=s.getProgress();send(String.format(Locale.US,"MV%02d%s",n/2,n%2==0?"":"5"));}});
        LinearLayout presetRow=row();for(int db:new int[]{-10,-15,-20,-25,-30,-35,-40}){Button b=addControl(presetRow,"−"+(-db),()->send(String.format(Locale.US,"MV%02d",db+80)));b.setTextSize(10);b.setPadding(0,0,0,0);b.setMinWidth(0);repeatControls.add(b);presets.put(b,db);}
        section("POWER");row=row();addControl(row,"On",()->send("PWON"));addControl(row,"Standby",()->send("PWSTANDBY"));
        section("INPUT");source=text("—",14,GOLD);source.setVisibility(View.GONE);
        row=row();inputButton=addControl(row,"Choose input",()->chooseAvailable("Input",inputs));
        section("LISTENING MODE");mode=text("—",14,GOLD);mode.setVisibility(View.GONE);
        row=row();modeButton=addControl(row,"Choose mode",()->chooseAvailable("Listening mode",modes));
        gap(16);Button soundToggle=button("Sound adjustments ▾",()->soundPanel.setVisibility(soundPanel.getVisibility()==View.GONE?View.VISIBLE:View.GONE));body.addView(soundToggle);
        soundPanel=new LinearLayout(this);soundPanel.setOrientation(LinearLayout.VERTICAL);body.addView(soundPanel);soundPanel.setVisibility(View.GONE);
        LinearLayout soundRow=new LinearLayout(this);soundRow.setGravity(Gravity.CENTER_VERTICAL);soundPanel.addView(soundRow);rowLabel(soundRow,"Tone Control");
        toneButton=inline(soundRow,"—",()->send(Boolean.TRUE.equals(tone)?"PSTONE CTRL OFF":"PSTONE CTRL ON"));toneButton.setContentDescription("Toggle Tone Control");
        bassDown=inline(soundRow,"−",()->send("PSBAS DOWN"));bassDown.setContentDescription("Decrease bass");repeatControls.add(bassDown);
        bassValue=new TextView(this);bassValue.setText("—");bassValue.setTextColor(GOLD);bassValue.setTextSize(11);bassValue.setGravity(Gravity.CENTER);soundRow.addView(bassValue,new LinearLayout.LayoutParams(dp(44),dp(44)));
        bassUp=inline(soundRow,"+",()->send("PSBAS UP"));bassUp.setContentDescription("Increase bass");repeatControls.add(bassUp);
        soundRow=new LinearLayout(this);soundRow.setGravity(Gravity.CENTER_VERTICAL);soundPanel.addView(soundRow);rowLabel(soundRow,"Audyssey LFC");
        lfcButton=inline(soundRow,"—",()->send(Boolean.TRUE.equals(lfc)?"PSLFC OFF":"PSLFC ON"));lfcButton.setContentDescription("Toggle Audyssey LFC");
        containmentButton=inline(soundRow,"Level —",()->choose("Containment amount",new String[]{"1","2","3","4","5","6","7"},new String[]{"PSCNTAMT 01","PSCNTAMT 02","PSCNTAMT 03","PSCNTAMT 04","PSCNTAMT 05","PSCNTAMT 06","PSCNTAMT 07"}));containmentButton.setMinWidth(dp(90));
        soundHint=new TextView(this);soundHint.setTextSize(10);soundHint.setTextColor(MUTED);soundPanel.addView(soundHint);
        gap(16);Button lightsToggle=button("Front-panel lights ▾",()->{illuminationButton.setVisibility(illuminationButton.getVisibility()==View.GONE?View.VISIBLE:View.GONE);});body.addView(lightsToggle);
        illuminationButton=button("LED illumination · Unavailable",()->{
            String[] labels={"Auto · follow display","Bright · 100%","Dim · 50%","Dark · 25%","Off"};
            choose("LED illumination",labels,new String[]{"IL1","IL2","IL3","IL4","IL5"});
        });body.addView(illuminationButton);controls.add(illuminationButton);illuminationButton.setVisibility(View.GONE);
        gap(20);text("INCOMING AUDIO",10,MUTED);format=text("—",14,GOLD);gap(16);
        LinearLayout speakers=row();
        inputSpeakers=new SpeakerView(this,"INPUT");outputSpeakers=new SpeakerView(this,"OUTPUT");
        LinearLayout.LayoutParams leftMap=new LinearLayout.LayoutParams(0,dp(210),1);leftMap.setMargins(0,dp(16),dp(5),0);speakers.addView(inputSpeakers,leftMap);
        LinearLayout.LayoutParams rightMap=new LinearLayout.LayoutParams(0,dp(210),1);rightMap.setMargins(dp(5),dp(16),0,0);speakers.addView(outputSpeakers,rightMap);
        TextView channelNote=text("Gold = active · dim = inactive\nChannel routing, not sound levels. SW combines subwoofer outputs.",9,MUTED);channelNote.setGravity(Gravity.CENTER);
        notice=text("Controls follow the receiver’s reported inputs and listening modes.",12,MUTED);
        gap(16);body.addView(button("MORE SETTINGS ↗",()->openLink("http://"+host+"/")));
        gap(12);body.addView(button("Volume limit & connection ▾",()->connectionPanel.setVisibility(connectionPanel.getVisibility()==View.GONE?View.VISIBLE:View.GONE)));
        connectionPanel=new LinearLayout(this);connectionPanel.setOrientation(LinearLayout.VERTICAL);body.addView(connectionPanel);connectionPanel.setVisibility(View.GONE);
        body.removeView(status);connectionPanel.addView(status);status.setVisibility(View.VISIBLE);
        scan=button("Find Marantz receivers",()->scan());connectionPanel.addView(scan);connectionPanel.addView(button("Enter receiver IP",()->manual()));connectionPanel.addView(button("App volume limit",()->chooseLimit()));
        gap(16);Button activityButton=button("Receiver activity",()->log.setVisibility(log.getVisibility()==View.GONE?View.VISIBLE:View.GONE));body.addView(activityButton);
        log=text("",11,MUTED);log.setTypeface(Typeface.MONOSPACE);log.setVisibility(View.GONE);gap(20);
        TextView foot=text("DIRECT TO YOUR MARANTZ",10,MUTED);foot.setGravity(Gravity.CENTER);TextView credit=text("BUILT BY THROY",10,GOLD);credit.setGravity(Gravity.CENTER);credit.setOnClickListener(v->openLink("https://www.instagram.com/throy/"));
        enabled(false);
        host=getPreferences(0).getString("host","");receiverName=getPreferences(0).getString("name","Marantz");
    }
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));body.addView(t);return t;}
    private void gap(int n){View v=new View(this);body.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private void section(String value){gap(23);View line=new View(this);line.setBackgroundColor(Color.rgb(48,48,41));body.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));gap(16);TextView t=text(value,10,MUTED);t.setLetterSpacing(.17f);gap(6);}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);body.addView(r);return r;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextColor(FG);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(52));GradientDrawable bg=new GradientDrawable();bg.setColors(new int[]{0xff2c2c24,0xff20201b});bg.setOrientation(GradientDrawable.Orientation.TL_BR);bg.setCornerRadius(dp(12));bg.setStroke(dp(1),Color.rgb(55,55,47));b.setBackground(bg);b.setOnClickListener(v->action.run());return b;}
    private Button addControl(LinearLayout row,String label,Runnable action){Button b=button(label,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1);p.setMargins(dp(3),0,dp(3),0);row.addView(b,p);controls.add(b);return b;}
    private void rowLabel(LinearLayout row,String label){TextView t=new TextView(this);t.setText(label);t.setTextSize(11);t.setTextColor(FG);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));}
    private Button inline(LinearLayout row,String label,Runnable action){Button b=button(label,action);b.setTextSize(10);b.setMinWidth(0);b.setPadding(dp(3),0,dp(3),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(44),dp(44));lp.setMargins(dp(2),dp(6),dp(2),0);row.addView(b,lp);controls.add(b);return b;}
    private void openLink(String url){if(url.startsWith("http://")&&!Protocol.localAddress(host)){notice.setText("Choose a receiver first.");return;}try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){notice.setText("No browser available.");}}
    private void enabled(boolean value){
        boolean fresh=value&&lastReply>0&&System.currentTimeMillis()-lastReply<12000,ready=fresh&&!commandPending;
        if(inputSpeakers!=null){inputSpeakers.show(inputChannels,fresh);outputSpeakers.show(outputChannels,fresh);}
        for(Button b:controls){b.setEnabled(repeatControls.contains(b)?fresh:ready);b.setAlpha(fresh?1f:.4f);}
        if(illuminationButton!=null){
            boolean lightFresh=fresh&&illuminationValue!=null&&System.nanoTime()/1000000-illuminationAt<12000;
            String[] lightLabels={"Auto · follow display","Bright · 100%","Dim · 50%","Dark · 25%","Off"};
            illuminationButton.setText("LED illumination · "+(lightFresh?lightLabels[Integer.parseInt(illuminationValue)-1]:"Unavailable"));illuminationButton.setEnabled(ready&&lightFresh);
        }
        if(mute!=null)mute.setEnabled(ready&&muted!=null);if(inputButton!=null)inputButton.setEnabled(ready&&!inputs.isEmpty());if(modeButton!=null)modeButton.setEnabled(ready&&!modes.isEmpty());if(slider!=null)slider.setEnabled(fresh&&currentVolume!=null);
        if(up!=null)up.setEnabled(fresh&&currentVolume!=null&&currentVolume<volumeLimit);if(down!=null)down.setEnabled(fresh&&currentVolume!=null&&currentVolume > -80);
        for(Map.Entry<Button,Integer> e:presets.entrySet()){e.getKey().setEnabled(fresh&&e.getValue()<=volumeLimit);e.getKey().setTextColor(currentVolume!=null&&currentVolume.equals(e.getValue().doubleValue())?GOLD:FG);}
        boolean bypass=Arrays.asList("DIRECT","PUREDIRECT").contains(Protocol.modeKey(modeRaw));
        if(toneButton!=null){toneButton.setText(tone==null?"—":tone?"ON":"OFF");toneButton.setTextColor(Boolean.TRUE.equals(tone)?GOLD:MUTED);toneButton.setEnabled(ready&&!bypass&&tone!=null);lfcButton.setText(lfc==null?"—":lfc?"ON":"OFF");lfcButton.setTextColor(Boolean.TRUE.equals(lfc)?GOLD:MUTED);lfcButton.setEnabled(ready&&!bypass&&lfc!=null);bassValue.setText(bass==null?"—":(bass>0?"+":"")+bass+" dB");bassDown.setEnabled(fresh&&!bypass&&Boolean.TRUE.equals(tone)&&bass!=null&&bass > -6);bassUp.setEnabled(fresh&&!bypass&&Boolean.TRUE.equals(tone)&&bass!=null&&bass < 6);containmentButton.setText("Level "+(containment==null?"—":containment));containmentButton.setEnabled(ready&&!bypass&&Boolean.TRUE.equals(lfc)&&containment!=null);soundHint.setText(bypass?"Unavailable in Direct / Pure Direct":"Bass −6 to +6 dB · LFC containment 1–7");}
    }
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
        generation++;int current=generation;if(connection!=null)connection.close();host=ip;receiverName=name;connected=false;commandPending=false;lastReply=0;inputs=new ArrayList<>();modes=new ArrayList<>();reset();title.setText("PERSONAL REMOTE · v1.4");status.setText("Connecting · "+ip);enabled(false);
        getPreferences(0).edit().putString("host",ip).putString("name",name).apply();
        connection=new ReceiverConnection(ip,new ReceiverConnection.Listener(){
            public void status(boolean ok,String detail){runOnUiThread(()->{if(current!=generation||!foreground)return;connected=ok;reset();enabled(ok);status.setText((ok?"Connected · ":"")+detail);});}
            public void line(String line){runOnUiThread(()->{if(current!=generation||!foreground)return;parse(line);});}
            public void capabilities(List<ReceiverHttp.Choice> in,List<ReceiverHttp.Choice> sound){runOnUiThread(()->{if(current!=generation||!foreground)return;inputs=in;modes=sound;renderNames();enabled(connected);});}
            public void illumination(String value){runOnUiThread(()->{if(current!=generation||!foreground)return;illuminationValue=value;illuminationAt=System.nanoTime()/1000000;enabled(connected);});}
            public void channels(SpeakerChannels.Panel in,SpeakerChannels.Panel out){runOnUiThread(()->{if(current!=generation||!foreground)return;inputChannels=in;outputChannels=out;enabled(connected);});}
            public void feedback(boolean pending,String message){runOnUiThread(()->{if(current!=generation||!foreground)return;commandPending=pending;notice.setText(message);notice.setTextColor(message.startsWith("Confirmed")?Color.rgb(183,209,174):MUTED);enabled(connected);});}
        });
        connection.setLimit(volumeLimit);
    }
    private void reset(){illuminationValue=null;illuminationAt=0;inputChannels=null;outputChannels=null;muted=null;currentVolume=null;tone=null;lfc=null;bass=null;containment=null;modeRaw="";inputCode="";if(slider!=null)renderSlider();volume.setText("—");source.setText("—");mode.setText("—");format.setText("—");mute.setText("Mute");}
    private void parse(String line){
        if(!line.startsWith("→ "))lastReply=System.currentTimeMillis();
        activity.addLast(line);while(activity.size()>65)activity.removeFirst();log.setText(android.text.TextUtils.join("\n",activity));
        Double v=Protocol.volume(line);if(v!=null){currentVolume=v;volume.setText(String.format(Locale.US,"%.1f",v));renderSlider();}
        else if(line.equals("MUON")||line.equals("MUOFF")){muted=line.equals("MUON");mute.setText(muted?"Unmute":"Mute");mute.setEnabled(connected&&!commandPending);}
        else if(line.equals("PWON")||line.equals("PWSTANDBY"))status.setText("Connected · "+(line.equals("PWON")?"On":"Standby")+" · "+host);
        else if(line.startsWith("SI")){inputCode=line.substring(2);renderNames();}
        else if(line.startsWith("MS")){modeRaw=line.substring(2);renderNames();}
        else if(line.startsWith("SYSMI ")){modeRaw=line.substring(6).trim();renderNames();}
        else if(line.startsWith("SYSDA "))format.setText(line.substring(6).trim());
        if(line.equals("PSTONE CTRL ON")||line.equals("PSTONE CTRL OFF"))tone=line.endsWith(" ON");
        if(line.equals("PSLFC ON")||line.equals("PSLFC OFF"))lfc=line.endsWith(" ON");
        if(line.matches("PSBAS [0-9]{2}"))bass=Integer.parseInt(line.substring(6))-50;
        if(line.matches("PSCNTAMT 0[1-7]"))containment=Integer.parseInt(line.substring(9));
        enabled(connected);
    }
    private void renderNames(){String label=inputCode;for(ReceiverHttp.Choice c:inputs)if(c.command.equals("SI"+inputCode))label=c.label;source.setText(label);inputButton.setText(label.isEmpty()?"Choose input":label);label=modeRaw;for(ReceiverHttp.Choice c:modes)if(Protocol.modeKey(c.rawLabel).equals(Protocol.modeKey(modeRaw)))label=c.label;mode.setText(label);modeButton.setText(label.isEmpty()?"Choose mode":label);}
    @Override protected void onStart(){super.onStart();foreground=true;ui.post(freshness);if(!host.isEmpty())connect(host,receiverName);else scan();}
    @Override protected void onStop(){foreground=false;ui.removeCallbacks(freshness);generation++;if(connection!=null)connection.close();connection=null;connected=false;enabled(false);super.onStop();}
    @Override protected void onDestroy(){discovery.shutdownNow();super.onDestroy();}
}
