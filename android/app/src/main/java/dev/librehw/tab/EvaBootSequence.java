package dev.librehw.tab;

import android.graphics.*;

/** Procedural MAGI startup; no bitmap assets, network calls or fabricated PC diagnostics. */
final class EvaBootSequence {
    static final long INTRO_MS=4500, CONFIRM_MS=1200, REVEAL_MS=1100;
    long started,linked=-1;
    boolean active;
    void start(long now){started=now;linked=-1;active=true;}
    void connect(long now){if(active&&linked<0)linked=now;}
    long revealAt(){return Math.max(started+INTRO_MS,linked)+CONFIRM_MS;}
    boolean revealing(long now,boolean motion){return active&&linked>=0&&motion&&now>=revealAt();}
    boolean finished(long now,boolean motion){return active&&linked>=0&&(!motion||now>=revealAt()+REVEAL_MS);}
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int primary,secondary;
    private float ratio;
    private static final int INK=0xFF08090D,WHITE=0xFFF1F3EF,MUTED=0xFF8C929C,RED=0xFFFF4C50;
    private void rect(Canvas c,float x,float y,float w,float h,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRect(x,y,x+w,y+h,p);}
    private void line(Canvas c,float x,float y,float x2,float y2,int color,float stroke){p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);c.drawLine(x,y,x2,y2,p);p.setStyle(Paint.Style.FILL);}
    private void text(Canvas c,String value,float x,float y,float size,int color){
        p.setColor(color);p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("monospace",Typeface.BOLD));p.setTextSize(size);
        float max=(1160-x)*ratio;while(p.measureText(value)>max&&size>9)p.setTextSize(--size);
        c.save();c.translate(x,y);c.scale(1/ratio,1);c.drawText(value,0,0,p);c.restore();
    }
    private void polygon(Canvas c,float[] points,int fill,int border){
        Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);path.close();
        p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(border);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private int alpha(int color,int a){return (color&0xffffff)|(a<<24);}
    void draw(Canvas canvas,int width,int height,long now,long deadline,String error,DashboardTheme theme,boolean motion){
        primary=theme.primary;secondary=theme.secondary;ratio=(width/1200f)/(height/760f);
        Canvas c=canvas;c.save();c.scale(width/1200f,height/760f);
        if(revealing(now,motion)&&error.isEmpty()){
            float t=Math.min(1,(now-revealAt())/(float)REVEAL_MS),ease=1-(float)Math.pow(1-t,3),edge=380*(1-ease);
            rect(c,0,0,1200,edge,INK);rect(c,0,760-edge,1200,edge,INK);
            line(c,0,edge,1200,edge,primary,3);line(c,0,760-edge,1200,760-edge,primary,3);
            if(edge>50){text(c,"MAGI // TELEMETRY LINK ESTABLISHED",48,Math.max(30,edge-22),17,primary);text(c,"RELEASING DISPLAY INTERLOCK",48,Math.min(739,782-edge),12,secondary);}
            c.restore();return;
        }
        rect(c,0,0,1200,760,INK);
        int color=error.isEmpty()?primary:RED;
        long elapsed=Math.max(0,now-started);float local=Math.min(1,elapsed/(float)INTRO_MS);
        boolean ready=linked>=0&&now>=Math.max(started+INTRO_MS,linked);
        // Slow scan and fine grid; no flashing or random state changes.
        for(int x=24;x<1200;x+=48)line(c,x,0,x,760,alpha(color,18),1);
        for(int y=24;y<760;y+=38)line(c,0,y,1200,y,alpha(color,14),1);
        if(motion&&error.isEmpty()){float scan=(elapsed%6000)/6000f*760;rect(c,0,scan,1200,24,alpha(color,18));line(c,24,scan,1176,scan,alpha(color,80),1);}
        for(int i=0;i<48;i++){float x=i*27;polygon(c,new float[]{x,0,x+12,0,x+30,16,x+18,16},color,color);polygon(c,new float[]{x,744,x+12,744,x+30,760,x+18,760},color,color);}
        text(c,"NERV",42,78,50,color);text(c,"AUTONOMOUS STARTUP CONTROL",255,48,13,MUTED);
        text(c,theme.name+" / MAGI SYSTEM",255,75,19,color);
        text(c,"SEQ 01 / WAKE-ON-LAN",865,50,13,MUTED);
        text(c,error.isEmpty()?ready?"LINK / ESTABLISHED":"LINK / ACQUIRING":"LINK / UNAVAILABLE",865,75,15,color);
        line(c,42,98,1158,98,color,2);
        String stage=!error.isEmpty()?UiText.get("ЗАПУСК ПРЕРВАН"):ready?UiText.get("СВЯЗЬ УСТАНОВЛЕНА"):elapsed<1300?UiText.get("ПОДАЧА ПИТАНИЯ"):elapsed<2800?UiText.get("ИНИЦИАЛИЗАЦИЯ MAGI"):elapsed<4500?UiText.get("СИНХРОНИЗАЦИЯ ИНТЕРФЕЙСА"):UiText.get("ОЖИДАНИЕ ТЕЛЕМЕТРИИ");
        text(c,stage,42,156,31,color);
        text(c,UiText.get("ЛОКАЛЬНАЯ ПОСЛЕДОВАТЕЛЬНОСТЬ / СОСТОЯНИЕ ПК ПОДТВЕРЖДАЕТСЯ ТЕЛЕМЕТРИЕЙ"),44,184,10,MUTED);
        // Three interlocking MAGI terminals, illuminated in sequence.
        String[] names={"MELCHIOR : 1","BALTHASAR : 2","CASPER : 3"};
        for(int i=0;i<3;i++){
            float x=42+i*380;boolean lit=elapsed>i*900+300||!motion;
            polygon(c,new float[]{x+22,214,x+350,214,x+364,228,x+364,363,x+342,386,x,386,x,236},alpha(lit?color:MUTED,lit?35:12),lit?color:alpha(MUTED,80));
            text(c,names[i],x+17,247,20,lit?color:MUTED);
            String state=!error.isEmpty()?"HOLD":ready?"LINK CONFIRMED":lit?"LOCAL READY":"INITIALIZING";
            text(c,state,x+17,283,16,lit?WHITE:MUTED);
            for(int n=0;n<18;n++)rect(c,x+17+n*18,306,13,24,alpha(lit?color:MUTED,lit&&n<(ready?18:Math.floor(local*18))?255:35));
            text(c,"INTERFACE NODE / 0"+(i+1),x+17,365,10,MUTED);
        }
        text(c,ready?"ALL INTERFACE NODES SYNCHRONIZED":"MAGI CONSENSUS / LOCAL DISPLAY BUS",42,421,14,secondary);
        String[] logs={"01  WAKE REQUEST / DISPATCHED","02  DISPLAY CONTROLLER / ONLINE","03  MAGI INTERFACE / SYNCHRONIZING","04  TELEMETRY UPLINK / WAITING"};
        for(int i=0;i<4;i++){
            int tone=elapsed>i*900?color:MUTED;
            text(c,i==3&&ready?"04  TELEMETRY UPLINK / VERIFIED":logs[i],44,460+i*29,14,tone);
        }
        // Acquisition is deliberately not shown as a fictitious boot percentage.
        if(!error.isEmpty()){
            text(c,error,44,605,17,WHITE);
            rect(c,42,635,540,76,RED);text(c,UiText.get("ПОВТОРИТЬ ЗАПУСК"),62,683,25,INK);
            polygon(c,new float[]{624,635,1158,635,1158,693,1140,711,624,711},alpha(RED,20),RED);
            text(c,UiText.get("НАЗАД К ЧАСАМ"),646,683,23,WHITE);
        }else{
            long seconds=Math.max(0,(deadline-now+999)/1000);
            text(c,ready?UiText.get("ОТКРЫТИЕ КАНАЛА УПРАВЛЕНИЯ"):UiText.get("ОЖИДАНИЕ ОТВЕТА ПК")+" / "+seconds+" s",44,615,25,color);
            float progress=ready?1:Math.min(.88f,local*.65f+Math.max(0,elapsed-INTRO_MS)/60000f*.23f);
            for(int i=0;i<56;i++)rect(c,44+i*20,641,14,28,i<progress*56?color:alpha(color,30));
            text(c,ready?"DISPLAY INTERLOCK / RELEASE":"ACQUISITION WINDOW / 60 s",44,704,13,MUTED);
            text(c,"EVA // "+(ready?"ACTIVE":"STANDBY → STARTUP"),865,704,15,secondary);
        }
        c.restore();
    }
}
