package com.mutairi.marantzremote;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A spatial channel map using the same five-column slots as the receiver display. */
public final class SpeakerView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String heading;private SpeakerChannels.Panel panel;private boolean receiverFresh=false;
    public SpeakerView(Context context,String heading){super(context);this.heading=heading;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);describe();}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    public void show(SpeakerChannels.Panel panel,boolean fresh){this.panel=panel;this.receiverFresh=fresh;describe();invalidate();}
    private boolean available(){return receiverFresh&&panel!=null&&panel.fresh();}
    private void describe(){
        StringBuilder text=new StringBuilder(heading+" channels: ");
        if(!available())text.append("Unavailable");else{if(panel.layout!=null)text.append(panel.layout).append(". ");for(SpeakerChannels.Channel c:panel.channels)text.append(c.label).append(c.active?" active, ":" inactive, ");}
        setContentDescription(text.toString());
    }
    private void label(Canvas canvas,String value,float x,float y,float size,int color){paint.setColor(color);paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(size));canvas.drawText(value,x,y,paint);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float w=getWidth(),h=getHeight();paint.setColor(0xff1d1c16);paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(dp(1),dp(1),w-dp(1),h-dp(1),dp(12),dp(12),paint);
        paint.setColor(0xff4e432d);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));canvas.drawRoundRect(dp(1),dp(1),w-dp(1),h-dp(1),dp(12),dp(12),paint);
        label(canvas,heading,w/2,dp(23),10,0xffb6a27e);
        boolean valid=available();
        if(valid){float cell=(w-dp(12))/5;
            for(SpeakerChannels.Channel c:panel.channels){
                int col=c.slot.charAt(0)-'a',row=c.slot.charAt(1)-'1';float x=dp(6)+col*cell,y=dp(35)+row*dp(34);
                RectF box=new RectF(x+dp(1),y,x+cell-dp(1),y+dp(26));
                paint.setStyle(Paint.Style.FILL);paint.setColor(c.active?0xff493a21:0xff252219);canvas.drawRoundRect(box,dp(4),dp(4),paint);
                paint.setStyle(Paint.Style.STROKE);paint.setColor(c.active?0xffba9658:0xff494331);canvas.drawRoundRect(box,dp(4),dp(4),paint);
                float size=Math.min(8f,Math.max(5f,(cell/dp(1)-4)/Math.max(1,c.label.length())*1.5f));
                label(canvas,c.label,box.centerX(),y+dp(17),size,c.active?0xffffe4ad:0xff82775f);
            }
        }else label(canvas,"—",w/2,dp(103),22,0xff6e654f);
        label(canvas,valid?(panel.layout==null?"Active channels":panel.layout):"Unavailable",w/2,h-dp(17),valid?12:10,valid?0xffe2c99e:0xff998d73);
    }
}
