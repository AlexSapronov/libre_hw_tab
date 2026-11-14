package dev.librehw.tab;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.util.*;

/** A native Canvas dashboard: no WebView, remote fonts, or browser dependencies. */
public final class DashboardView extends View {
    private final Paint p = new Paint(3);
    private final Runnable settings, theme;
    private static final class ThermalPoint {
        final long time; final float[] values;
        ThermalPoint(long time,float cpu,float gpu,float cpuLoad,float gpuLoad,float memoryLoad){this.time=time;values=new float[]{cpu,gpu,cpuLoad,gpuLoad,memoryLoad};}
    }
    private static final class FpsPoint {final long time;final float fps;FpsPoint(long time,float fps){this.time=time;this.fps=fps;}}
    private final ArrayDeque<FpsPoint> fpsHistory=new ArrayDeque<>();
    private String fpsSource="";
    private final ArrayDeque<ThermalPoint> history = new ArrayDeque<>();
    private JSONObject sample;
    private String error = "Ожидание подключения";
    private boolean demo;
    public int themeId;
    DashboardLayout layout=new DashboardLayout();
    public boolean editing,forceStandby,autoStandby=true,animations=true;
    public JSONObject weather;
    public Bitmap mediaThumbnail;
    public interface MediaAction { void run(String action,double fraction); }
    public MediaAction onMedia=(action,fraction)->{};
    private long mediaReceived;
    private final RectF mediaPrevious=new RectF(877,605,957,685),mediaToggle=new RectF(973,605,1053,685),mediaNext=new RectF(1069,605,1149,685),mediaSeek=new RectF(204,657,840,691);
    public JSONObject media(){return object("media");}
    boolean mediaVisible(){return !editing && sample!=null && media()!=null && !media().optString("id").isEmpty();}
    public long wakeSentUntil;
    public String weatherCity="Санкт-Петербург";
    public String weatherHint="Нажми, чтобы обновить";
    public String weatherStatus="Погода загружается…";
    public String nextAlarm="Будильник не задан";
    public Runnable onMenu=()->{},onAlarm=()->{},onWeather=()->{},onWake=()->{},onEditorSave=()->{},onEditorCancel=()->{},onEditorOptions=()->{};
    private long lastGood=android.os.SystemClock.elapsedRealtime(),suppressedUntil,transitionStart;
    private String transitionKey="",selection;
    private boolean nerv,resizing;
    private RectF dragRect;
    private float downX,downY;
    private final Map<String,RectF> drawnCards=new LinkedHashMap<>();
    private final RectF menuHit=new RectF(312,20,396,86);
    private final RectF clockBack=new RectF(36,40,230,95),clockAlarm=new RectF(700,545,1164,635),clockWeather=new RectF(700,140,1164,540),clockWake=new RectF(36,560,600,695);
    public void suppressStandby(){forceStandby=false;suppressedUntil=android.os.SystemClock.elapsedRealtime()+30000;invalidate();}
    public boolean standby(){return !editing && (forceStandby || (autoStandby && sample==null && android.os.SystemClock.elapsedRealtime()-lastGood>=15000 && android.os.SystemClock.elapsedRealtime()>suppressedUntil));}
    public boolean playing(){return mode==2 || (mode==0 && object("game")!=null && object("game").optBoolean("active"));}
    public int mode; // 0 automatic, 1 overview, 2 game
    public Runnable onModeChange = () -> { mode=(mode+1)%3; invalidate(); };
    private final RectF modeHit = new RectF(410,20,654,86);
    private float scale = 1, offsetX, offsetY;
    private int accent, secondary, panel;
    private final int white = Color.rgb(236, 244, 249), muted = Color.rgb(134, 153, 173);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface digits = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final RectF settingsHit = new RectF(1000, 20, 1164, 86), themeHit = new RectF(830, 20, 990, 86);

    public DashboardView(Context context, Runnable settings, Runnable theme) {
        super(context); this.settings = settings; this.theme = theme;
        setContentDescription("Панель мониторинга ПК. Настройки и тема расположены вверху справа.");
        setFocusable(true);
    }
    public void clear() { history.clear();fpsHistory.clear();fpsSource=""; sample = null; error = "Подключение…"; invalidate(); }
    public void accept(JSONObject data, String error, boolean demo) {
        sample = data;mediaReceived=android.os.SystemClock.elapsedRealtime(); if(data!=null)lastGood=android.os.SystemClock.elapsedRealtime(); this.error = error; this.demo = demo;
        long now=android.os.SystemClock.elapsedRealtime();
        history.add(new ThermalPoint(now,value(object("cpu"),"temperature"),value(object("gpu"),"temperature"),value(object("cpu"),"load"),value(object("gpu"),"load"),value(object("memory"),"load")));
        JSONObject game=object("game");String source=game==null?"":game.optString("sourceId",game.optString("name",""));
        if(!source.equals(fpsSource)){fpsHistory.clear();fpsSource=source;}
        fpsHistory.add(new FpsPoint(now,value(game,"fps")));
        while(!fpsHistory.isEmpty()&&(now-fpsHistory.peekFirst().time>60000||fpsHistory.size()>180))fpsHistory.removeFirst();
        while (!history.isEmpty() && (now-history.peekFirst().time>180000 || history.size()>1000))history.removeFirst();
        invalidate();
    }
    private JSONObject object(String key) { return sample == null ? null : sample.optJSONObject(key); }
    private static float value(JSONObject o, String key) {
        double n = o == null || o.isNull(key) ? Double.NaN : o.optDouble(key, Double.NaN);
        return Double.isFinite(n) ? (float)n : Float.NaN;
    }
    private static String number(float n) { return Float.isNaN(n) ? "—" : String.format(Locale.US, "%.0f", n); }
    private static String decimal(float n) { return Float.isNaN(n) ? "—" : String.format(Locale.US, "%.1f", n); }
    private void text(Canvas c, String text, float x, float y, float size, int color, Typeface font) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTypeface(nerv ? (font==medium?Typeface.create("monospace",Typeface.BOLD):Typeface.MONOSPACE) : font); p.setTextSize(size); c.drawText(text, x, y, p);
    }
    private void fit(Canvas c,String value,float x,float y,float size,float width,int color) {
        if(width<=0)return;String label=value==null?"—":value;
        p.setTypeface(nerv?Typeface.create("monospace",Typeface.BOLD):medium);p.setTextSize(size);
        while(p.measureText(label)>width && size>10)p.setTextSize(--size);
        if(p.measureText(label)>width){int end=p.breakText(label,true,Math.max(0,width-p.measureText("…")),null);label=label.substring(0,end)+"…";}
        text(c,label,x,y,size,color,medium);
    }
    private void box(Canvas c, float x, float y, float w, float h, int color) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); if(!nerv)c.drawRoundRect(x,y,x+w,y+h,22,22,p);
        else {
            float cut=Math.min(18,Math.min(w,h)/3);
            Path path=new Path();path.moveTo(x+cut,y);path.lineTo(x+w,y);path.lineTo(x+w,y+h-cut);path.lineTo(x+w-cut,y+h);path.lineTo(x,y+h);path.lineTo(x,y+cut);path.close();c.drawPath(path,p);
            if(h>50){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor((accent&0xffffff)|0x66000000);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
        }
    }
    private void line(Canvas c, float x, float y, float endX, float endY, int color, float stroke) {
        p.setShader(null); p.setColor(color); p.setStrokeWidth(stroke); p.setStyle(Paint.Style.STROKE); c.drawLine(x,y,endX,endY,p); p.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        DashboardTheme palette=DashboardTheme.ALL[Math.max(0,Math.min(DashboardTheme.ALL.length-1,themeId))];
        nerv="NERV".equals(palette.name);
        accent=palette.primary;secondary=palette.secondary;panel=palette.panel;
        c.drawColor(palette.background);
        scale = Math.min(getWidth()/1200f, getHeight()/760f);
        offsetX = (getWidth()-1200*scale)/2; offsetY = (getHeight()-760*scale)/2;
        c.save(); c.translate(offsetX, offsetY); c.scale(scale, scale);
        p.setShader(new RadialGradient(300, 100, 750, palette.glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(0,0,1200,760,p); p.setShader(null);
        if(standby()){drawStandby(c);finishAnimation(c,"standby");c.restore();postInvalidateDelayed(1000);return;}
        text(c, nerv?"NERV":"LIBRE", 36, 53, 29, white, medium); text(c, "H W   T A B", 144, 52, 16, accent, medium);
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        text(c, time, 700, 53, 27, white, digits);
        boolean playing=playing();
        button(c,menuHit,"МЕНЮ");
        button(c, modeHit, mode==0 ? (playing ? "AUTO / ИГРА" : "AUTO / ОБЗОР") : mode==1 ? "РЕЖИМ / ОБЗОР" : "РЕЖИМ / ИГРА");
        button(c, themeHit, "THEME / "+palette.name); button(c, settingsHit, "ПОДКЛЮЧЕНИЕ");
        boolean healthy = sample != null && sample.isNull("sensorError");
        int status = sample == null ? secondary : healthy ? accent : 0xFFFF7777;
        p.setColor(status); c.drawCircle(43, 93, 4, p);
        String caption = demo ? "DEMO MODE  /  ДЕМОНСТРАЦИОННЫЕ ДАННЫЕ" : sample != null ? sample.optString("host", "PC") + (healthy ? "  /  LIVE TELEMETRY" : "  /  ОШИБКА ДАТЧИКОВ") : "OFFLINE  /  " + error;
        fit(c, caption, 56, 99, 13, 1090, status);
        drawnCards.clear();
        if(playing)drawBlock(c,"main",true);
        else {
            drawBlock(c,"cpu",false);
            drawBlock(c,"gpu",false);
        }
        drawBlock(c,"side",playing);
        if(layout.metric("memory"))drawBlock(c,"memory",playing);
        if(layout.metric("history"))drawBlock(c,"history",playing);
        if(mediaVisible())drawMedia(c);
        if(editing)drawEditor(c,playing);
        finishAnimation(c,(playing?"game":"overview")+themeId);
        JSONObject game = object("game");
        if(sample!=null && !sample.isNull("sensorError"))fit(c,"Ошибка чтения датчиков — проверь агент",36,727,12,1128,0xFFFF7777);
        else if(game!=null && !game.optBoolean("active") && !game.optString("status").matches("idle|ready|starting"))fit(c,"FPS: "+game.optString("status"),36,727,12,1128,muted);
        c.restore();
    }
    private void button(Canvas c, RectF r, String label) {
        box(c, r.left, r.top+7, r.width(), 40, 0xFF152131);
        text(c, label, r.left+14, r.top+33, 12, muted, medium);
    }
    private static float bounded(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private float padding(float w,float h){return bounded(Math.min(w,h)*.075f,12,24);}
    private void cardTitle(Canvas c,String label,float w,float h){float pad=padding(w,h);fit(c,label,pad,pad+12,h<160?10:12,w-pad*2,accent);}
    private void metric(Canvas c,String title,String value,String unit,RectF area,int color) {
        float font=bounded(area.height()*.47f,14,46);fit(c,title,area.left,area.top+11,10,area.width(),muted);
        fit(c,value,area.left,Math.min(area.bottom-2,area.top+15+font),font,area.width(),color);
        if(!unit.isEmpty() && area.height()>65)fit(c,unit,area.left,area.bottom-2,10,area.width(),muted);
    }
    private void gamePanel(Canvas c,float w,float h) {
        float pad=padding(w,h),body=h<155?33:h<220?65:78;JSONObject game=object("game");
        cardTitle(c,w<350?"01 / GAME":"01 / GAME TELEMETRY",w,h);
        if(h>=155)fit(c,game==null?"Ожидание игры":game.isNull("name")?"Ожидание игры":game.optString("name"),pad,h<220?50:60,h<240?20:25,w-pad*2,white);
        if(h<155) {
            if(layout.metric("fps"))fit(c,number(value(game,"fps"))+" FPS",pad,pad+49,32,w-pad*2,white);
            String[] smallKeys={"low1","low01","frameTime"},smallLabels={"1% LOW","0,1% LOW","мс"};
            int count=0;for(String key:smallKeys)if(layout.metric(key))count++;
            int column=0;float cw=(w-pad*2)/Math.max(1,count);
            for(int i=0;i<3;i++)if(layout.metric(smallKeys[i])) {
                float x=pad+column++*cw;fit(c,smallLabels[i],x,h-28,9,cw-6,muted);
                fit(c,i==2?decimal(value(game,smallKeys[i])):number(value(game,smallKeys[i])),x,h-12,16,cw-6,white);
            }
            return;
        }
        boolean wide=w>=540,showFps=layout.metric("fps");float heroWidth=showFps?(wide?(w-pad*3)*.34f:w-pad*2):0;
        float heroHeight=wide?h-body-pad:(h>=220?87:42);
        float fpsSize=bounded(Math.min(heroWidth*.64f,heroHeight*.78f),30,148);
        if(layout.metric("fps")) {
            fit(c,number(value(game,"fps")),pad,body+fpsSize,fpsSize,heroWidth,white);
            if(heroHeight>=75)text(c,"PRESENTED FPS",pad,body+fpsSize+21,11,accent,medium);
        }
        java.util.ArrayList<String> keys=new java.util.ArrayList<>();for(String key:new String[]{"low1","low01","frameTime"})if(layout.metric(key))keys.add(key);
        float statsX=wide&&showFps?pad+heroWidth+pad:pad,statsY=wide||!showFps?body:body+heroHeight+10;
        float statsW=w-pad-statsX,statH=wide?70:Math.min(72,h-statsY-pad);
        for(int i=0;i<keys.size();i++) {
            String key=keys.get(i),label=key.equals("low1")?"1% LOW":key.equals("low01")?"0,1% LOW":"ВРЕМЯ КАДРА";
            float gap=keys.size()>1?10:0,cw=(statsW-gap*(keys.size()-1))/Math.max(1,keys.size());
            RectF area=new RectF(statsX+i*(cw+gap),statsY,statsX+i*(cw+gap)+cw,statsY+statH);
            metric(c,label,key.equals("frameTime")?decimal(value(game,key)):number(value(game,key)),key.equals("frameTime")?"мс":key.equals("low01")?low01Status(game):"FPS",area,white);
        }
        float signalTop=wide?body+92:statsY+statH+18;
        if(nerv && layout.metric("history") && h-signalTop-pad>=40)fpsSignal(c,new RectF(wide?statsX:pad,signalTop,w-pad,h-pad),game);
        if(game!=null && game.optBoolean("paused") && h>=200)fit(c,"ALT+TAB / ПАУЗА",pad,h-pad,10,heroWidth,muted);
    }
    private void fpsSignal(Canvas c,RectF area,JSONObject game) {
        boolean budget=area.width()>=390;float budgetW=budget?108:0;
        float left=area.left,top=area.top+18,right=area.right-budgetW,bottom=area.bottom-4;
        fit(c,"MAGI // FPS SIGNAL · 60 С",left,area.top+8,9,right-left,accent);
        for(int i=0;i<=8;i++){float x=left+(right-left)*i/8;line(c,x,top,x,bottom,0xFF38222A,1);}
        for(int i=0;i<3;i++){float y=top+(bottom-top)*i/2;line(c,left,y,right,y,0xFF56313B,1);}
        long now=android.os.SystemClock.elapsedRealtime();float maximum=60;
        for(FpsPoint point:fpsHistory)if(Float.isFinite(point.fps))maximum=Math.max(maximum,point.fps);
        maximum=(float)Math.ceil(maximum/60)*60;
        Path path=new Path();boolean started=false;float lastX=0,lastY=0;
        for(FpsPoint point:fpsHistory) {
            if(now-point.time>60000)continue;
            if(!Float.isFinite(point.fps)){started=false;continue;}
            float x=left+(right-left)*(1-(now-point.time)/60000f),y=bottom-(bottom-top)*Math.min(1,point.fps/maximum);
            if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);lastX=x;lastY=y;
        }
        p.setColor(accent);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        if(started){p.setColor(accent);c.drawCircle(lastX,lastY,3,p);}
        if(fpsHistory.size()<2)fit(c,"ОЖИДАНИЕ СИГНАЛА",left+8,top+20,10,right-left-16,muted);
        if(budget) {
            float frame=value(game,"frameTime"),row=(area.height()-8)/3;
            for(int i=0;i<3;i++) {
                int fps=new int[]{60,120,240}[i];float y=area.top+i*row;
                boolean met=Float.isFinite(frame)&&frame<=1000f/fps;
                text(c,fps+" FPS",area.right-94,y+12,10,met?accent:muted,medium);
                for(int n=0;n<7;n++){p.setColor(Float.isFinite(frame)?met?accent:secondary:0xFF38222A);c.drawRect(area.right-94+n*12,y+18,area.right-86+n*12,y+24,p);}
            }
        }
    }
    private void frameGraph(Canvas c,float w,float h) {
        float pad=padding(w,h);cardTitle(c,"FRAME TIME · 60 С",w,h);
        JSONObject game=object("game");JSONArray frames=game==null?null:game.optJSONArray("frameSeries");
        float limit=33.4f;if(frames!=null)for(int i=0;i<frames.length();i++){double v=frames.optDouble(i);if(Double.isFinite(v)&&v>0)limit=Math.max(limit,(float)v);}
        limit=(float)Math.ceil(limit/8.333f)*8.333f;
        RectF plot=new RectF(pad,pad+28,w-pad-25,h-pad-15);if(plot.height()<20)return;
        chartGrid(c,plot,limit,false);
        if(nerv)for(float budget:new float[]{4.167f,8.333f,16.667f})if(budget<limit){float y=plot.bottom-plot.height()*budget/limit;line(c,plot.left,y,plot.right,y,0xFF824354,1);if(plot.height()>100)text(c,Math.round(1000/budget)+" FPS",plot.left+4,y-3,8,secondary,medium);}
        if(frames==null || frames.length()<2)fit(c,"Ожидание кадров…",plot.left+5,plot.centerY(),12,plot.width()-10,muted);
        else {
            Path path=new Path();boolean started=false;int count=Math.min(300,frames.length()),start=frames.length()-count;
            for(int i=0;i<count;i++) {
                double value=frames.optDouble(start+i);if(!Double.isFinite(value)||value<=0){started=false;continue;}
                float x=plot.left+plot.width()*(300-count+i)/299,y=plot.bottom-plot.height()*(float)value/limit;
                if(nerv)line(c,x,plot.bottom,x,y,value>16.667?0x77FF4657:0x55FF953D,1);
                if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
            }
            p.setColor(accent);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
        chartTimes(c,plot,h-pad,60);
    }
    private void device(Canvas c,float w,float h,String title,JSONObject data,int color,int series) {
        float pad=padding(w,h);boolean compact=h<190;cardTitle(c,compact?title:title.equals("CPU")?"01 / PROCESSOR":"02 / GRAPHICS",w,h);
        if(!compact)fit(c,data==null?"Ожидание датчика":data.optString("name","—"),pad,pad+46,20,w-pad*2,white);
        float body=compact?pad+25:pad+68,footer=h-pad-37;
        float bodyH=Math.max(36,footer-body-10);boolean hasTemp=layout.metric("temperature"),hasLoad=layout.metric("load");boolean columns=hasTemp && hasLoad && (w>=300 || compact);
        float firstW=columns?(w-pad*3)*.61f:w-pad*2,loadX=columns?pad+firstW+pad:pad;
        float tempSize=bounded(Math.min(firstW*.65f,bodyH*.65f),25,84);
        if(layout.metric("temperature")) {
            fit(c,number(value(data,"temperature"))+"°C",pad,body+tempSize,tempSize,firstW,white);
            if(bodyH>70)text(c,"ТЕМПЕРАТУРА",pad,body+tempSize+20,10,color,medium);
        }
        if(layout.metric("load")) {
            float load=value(data,"load"),lw=columns?w-pad-loadX:w-pad*2;
            float ly=columns||!hasTemp?body:body+tempSize+35,lh=columns||!hasTemp?bodyH:Math.max(40,footer-ly-8);
            if(nerv || lw<105 || lh<90) {
                float size=bounded(Math.min(lw*.40f,lh*.48f),20,52);
                fit(c,number(load)+"%",loadX,ly+size,size,lw,white);
                if(lh>65){nervSegments(c,loadX,ly+size+12,lw,Math.min(20,lh*.13f),load,color,16);}
                if(lh>120)loadSpark(c,loadX,ly+size+46,lw,Math.max(16,lh-size-46),series+2,color);
            }else {
                float diameter=Math.min(lw-4,lh-4),cx=loadX+lw/2,cy=ly+lh/2;
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xFF253245);
                RectF arc=new RectF(cx-diameter/2,cy-diameter/2,cx+diameter/2,cy+diameter/2);c.drawArc(arc,140,260,false,p);
                if(Float.isFinite(load)){p.setColor(color);c.drawArc(arc,140,260*Math.max(0,Math.min(1,load/100)),false,p);}p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);
                loadLabel(c,cx,cy,load);
            }
        }
        if(h>=135) {
            line(c,pad,footer-10,w-pad,footer-10,0xFF56313B,1);
            if(layout.metric("clock"))metric(c,"ЧАСТОТА",number(value(data,"clock"))+" MHz","",new RectF(pad,footer,layout.metric("power")?w/2-5:w-pad,h-pad+2),white);
            if(layout.metric("power"))metric(c,"МОЩНОСТЬ",number(value(data,"power"))+" W","",new RectF(layout.metric("clock")?w/2+5:pad,footer,w-pad,h-pad+2),white);
        }
    }
    private void gameHardware(Canvas c,float w,float h) {
        float pad=padding(w,h);cardTitle(c,"02 / HARDWARE",w,h);
        boolean columns=w>=420;float top=pad+31,gap=16,sectionH=columns?h-top-pad:(h-top-pad-gap)/2,sectionW=columns?(w-pad*2-gap)/2:w-pad*2;
        hardwareSection(c,new RectF(pad,top,pad+sectionW,top+sectionH),"GPU",object("gpu"),secondary,1);
        RectF second=columns?new RectF(pad+sectionW+gap,top,w-pad,h-pad):new RectF(pad,top+sectionH+gap,w-pad,h-pad);
        if(!columns)line(c,pad,second.top-gap/2,w-pad,second.top-gap/2,0xFF56313B,1);
        hardwareSection(c,second,"CPU",object("cpu"),accent,0);
    }
    private void hardwareSection(Canvas c,RectF r,String title,JSONObject data,int color,int series) {
        boolean compact=r.height()<150;float temperature=value(data,"temperature"),load=value(data,"load");
        float tempSize=bounded(Math.min(r.width()*.35f,r.height()*.28f),24,68);
        text(c,title,r.left,r.top+12,12,color,medium);
        if(compact) {
            String values=(layout.metric("temperature")?number(temperature)+"°C":"")+(layout.metric("temperature")&&layout.metric("load")?" / ":"")+(layout.metric("load")?number(load)+"%":"");
            if(r.height()<65){fit(c,title+" "+values,r.left,r.top+Math.min(20,r.height()-2),14,r.width(),color);return;}
            fit(c,values,r.left,r.top+40,24,r.width(),white);
            if(layout.metric("load") && r.height()>65)nervSegments(c,r.left,r.bottom-18,r.width(),14,load,color,16);return;
        }
        float baseline=r.top+tempSize+27;
        if(layout.metric("temperature")){fit(c,number(temperature)+"°C",r.left,baseline,tempSize,r.width(),white);text(c,"ТЕМПЕРАТУРА",r.left,baseline+18,10,muted,medium);}
        if(layout.metric("load")) {
            float barY=layout.metric("temperature")?r.top+r.height()*.73f:r.top+65;
            text(c,"LOAD",r.left,barY-14,10,muted,medium);p.setTextAlign(Paint.Align.RIGHT);text(c,number(load)+"%",r.right,barY-12,28,white,digits);p.setTextAlign(Paint.Align.LEFT);
            if(nerv)nervSegments(c,r.left,barY,r.width(),18,load,color,18);
            else {box(c,r.left,barY,r.width(),7,0xFF263446);if(Float.isFinite(load))box(c,r.left,barY,r.width()*Math.max(0,Math.min(1,load/100)),7,color);}
            if(r.bottom-barY>44)loadSpark(c,r.left,barY+28,r.width(),r.bottom-barY-29,series+2,color);
        }
    }
    private void cooling(Canvas c,float w,float h) {
        float pad=padding(w,h);cardTitle(c,"04 / COOLING",w,h);JSONObject cpu=object("cpu"),gpu=object("gpu");
        boolean profile=cpu!=null&&cpu.optBoolean("coolingProfile");
        String[] keys={"fan","sideFan","pump","gpuFan"},labels={profile?"ВЕРХ / AIO":"CPU / AIO","БОК / ВДУВ","ПОМПА","GPU / FAN"},sources={profile?"Fan #1":sensorName(cpu,"fanName"),profile?"Fan #2":"КОРПУС",profile?"Fan #7":sensorName(cpu,"pumpName"),"ВИДЕОКАРТА"};
        java.util.ArrayList<Integer> rows=new java.util.ArrayList<>();for(int i=0;i<4;i++)if(layout.metric(keys[i]))rows.add(i);
        boolean columns=w>=400;int count=rows.size(),rowCount=columns?(count+1)/2:count;
        float top=pad+29,rh=(h-top-pad)/Math.max(1,rowCount),cw=columns?(w-pad*3)/2:w-pad*2;
        for(int n=0;n<count;n++) {
            int index=rows.get(n);float x=pad+(columns?n%2*(cw+pad):0),y=top+(columns?n/2:n)*rh;
            float rpm=value(index==3?gpu:cpu,keys[index].equals("gpuFan")?"fan":keys[index]);int color=index==3?secondary:accent;
            if(rh<65){fit(c,labels[index]+"  "+number(rpm)+" RPM",x,y+Math.min(25,rh-3),bounded(rh*.55f,9,14),cw,color);continue;}
            fit(c,labels[index],x,y+12,11,cw,color);
            fit(c,number(rpm)+" RPM",x,y+bounded(rh*.56f,35,65),bounded(Math.min(cw*.21f,rh*.35f),20,44),cw,white);
            if(rh>90)fit(c,sources[index],x,y+rh-14,10,cw,muted);
            if(n<count-1)line(c,x,y+rh-4,x+cw,y+rh-4,0xFF56313B,1);
        }
    }
    static String low01Status(JSONObject game) {
        if(game==null)return "НЕТ ДАННЫХ";
        if(game.optBoolean("paused"))return "ПАУЗА";
        if(!game.has("low01"))return "ОБНОВИ АГЕНТ ПК";
        if(!Float.isNaN(value(game,"low01")))return "FPS";
        if(game.has("low01Samples")) {
            int samples=game.optInt("low01Samples");double seconds=game.optDouble("low01Seconds",0);
            if(samples<1000)return samples+" / 1000 КАДРОВ";
            return Math.min(10,(int)seconds)+" / 10 СЕКУНД";
        }
        return "НАКОПЛЕНИЕ КАДРОВ";
    }
    private String sensorName(JSONObject data,String key) {
        return data==null || data.isNull(key) ? "ДАТЧИК НЕДОСТУПЕН" : data.optString(key,"ДАТЧИК НЕДОСТУПЕН");
    }
    private void coolingRow(Canvas c,float x,float y,String title,float rpm,String source,int color) {
        text(c,title,x+22,y,12,color,medium);
        valueWithUnit(c,number(rpm),x+22,y+46,39,"RPM",12,2,muted);
        fit(c,source,x+22,y+67,10,176,muted);
        line(c,x+22,y+86,x+198,y+86,0xFF263446,1);
    }
    private void valueWithUnit(Canvas c,String value,float x,float baseline,float size,String unit,float unitSize,float rise,int color) {
        text(c,value,x,baseline,size,white,digits);
        float unitX=x+p.measureText(value)+10;
        text(c,unit,unitX,baseline-rise,unitSize,color,medium);
    }
    private void loadLabel(Canvas c, float centerX, float centerY, float load) {
        p.setTypeface(digits); p.setTextSize(46); Paint.FontMetrics big = p.getFontMetrics();
        p.setTypeface(medium); p.setTextSize(11); Paint.FontMetrics small = p.getFontMetrics();
        float height = big.descent-big.ascent + 6 + small.descent-small.ascent;
        float top = centerY-height/2;
        p.setTextAlign(Paint.Align.CENTER);
        text(c,number(load),centerX,top-big.ascent,46,white,digits);
        text(c,"% LOAD",centerX,top+big.descent-big.ascent+6-small.ascent,11,muted,medium);
        p.setTextAlign(Paint.Align.LEFT);
    }
    private void stat(Canvas c,float x,float y,String title,String val) { stat(c,x,y,title,val,21); }
    private void stat(Canvas c,float x,float y,String title,String val,float size) {
        text(c,title,x,y,10,muted,medium); text(c,val,x,y+26,size,white,regular);
    }
    private void memory(Canvas c,float w,float h) {
        float pad=padding(w,h);cardTitle(c,"03 / MEMORY",w,h);JSONObject ram=object("memory");
        float load=value(ram,"load"),used=value(ram,"used"),total=value(ram,"total");int color=Float.isFinite(load)&&load>=90?secondary:accent;
        boolean wide=w>=400 && h>=160;float mainW=wide?w*.40f:w-pad*2,body=pad+27;
        float size=bounded(Math.min(mainW*.30f,h*.24f),24,64);
        fit(c,number(load)+"%",pad,body+size,size,mainW,color);
        fit(c,decimal(used)+" / "+number(total)+" GB",pad,body+size+23,Math.min(18,w*.06f),mainW,muted);
        float barY=body+size+36;
        if(barY+14<h-pad){if(nerv)nervSegments(c,pad,barY,mainW,18,load,color,24);else for(int i=0;i<24;i++)box(c,pad+i*mainW/24,barY,mainW/24-2,18,Float.isFinite(load)&&i<Math.ceil(load/100*24)?color:0xFF263446);}
        RectF spark=wide?new RectF(w*.47f,body+10,w-pad,h-pad-16):new RectF(pad,barY+35,w-pad,h-pad-16);
        if(spark.height()>=16){loadSpark(c,spark.left,spark.top,spark.width(),spark.height(),4,color);text(c,"MEMORY LOAD / 60 С",spark.left,h-pad+2,9,muted,medium);}
    }
    private void chartGrid(Canvas c,RectF plot,float maximum,boolean percent) {
        if(nerv)for(int i=0;i<=10;i++){float x=plot.left+plot.width()*i/10;line(c,x,plot.top,x,plot.bottom,0xFF38222A,1);}
        for(int i=0;i<3;i++){float y=plot.top+plot.height()*i/2;line(c,plot.left,y,plot.right,y,0xFF56313B,1);text(c,number(maximum*(1-i/2f))+(percent?"%":""),plot.right+5,y+4,9,muted,regular);}
    }
    private void chartTimes(Canvas c,RectF plot,float baseline,int seconds) {
        text(c,"−"+seconds+" с",plot.left,baseline,9,muted,regular);
        if(plot.width()>260)text(c,"−"+seconds/2+" с",plot.centerX()-18,baseline,9,muted,regular);
        p.setTextAlign(Paint.Align.RIGHT);text(c,"СЕЙЧАС",plot.right,baseline,9,muted,regular);p.setTextAlign(Paint.Align.LEFT);
    }
    private void graph(Canvas c,float w,float h) {
        float pad=padding(w,h);cardTitle(c,nerv?"MAGI // RESOURCE LOAD":"THERMAL HISTORY",w,h);
        RectF plot=new RectF(pad,pad+30,w-pad-30,h-pad-15);if(plot.height()<20)return;
        chartGrid(c,plot,100,nerv);
        for(int series=0;series<2;series++) {
            Path path=new Path();boolean started=false;float lastY=0;long now=android.os.SystemClock.elapsedRealtime();
            for(ThermalPoint point:history) {
                float value=point.values[series+(nerv?2:0)];if(!Float.isFinite(value)){started=false;continue;}
                float x=plot.left+plot.width()*(1-(now-point.time)/180000f),y=plot.bottom-plot.height()*Math.max(0,Math.min(100,value))/100;
                if(!started){path.moveTo(x,y);started=true;}else {if(nerv)path.lineTo(x,lastY);path.lineTo(x,y);}lastY=y;
            }
            p.setColor(series==0?accent:secondary);p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
        if(w>320){text(c,"CPU",w-pad-100,pad+12,10,accent,medium);text(c,"GPU",w-pad-49,pad+12,10,secondary,medium);}
        chartTimes(c,plot,h-pad,180);
    }
    private void nervSegments(Canvas c,float left,float top,float width,float height,float load,int color,int count) {
        float step=width/count;
        for(int i=0;i<count;i++) {
            p.setColor(Float.isFinite(load)&&i<Math.ceil(Math.max(0,Math.min(100,load))/100*count)?(load>=90?secondary:color):0xFF38222A);
            float x=left+i*step,slant=Math.min(3,step/3);Path segment=new Path();
            segment.moveTo(x+slant,top);segment.lineTo(x+step-1,top);segment.lineTo(x+step-1-slant,top+height);segment.lineTo(x,top+height);segment.close();c.drawPath(segment,p);
        }
    }
    private void loadSpark(Canvas c,float left,float top,float width,float height,int series,int color) {
        Path path=new Path();boolean started=false;long now=android.os.SystemClock.elapsedRealtime();
        for(ThermalPoint point:history) {
            if(now-point.time>60000)continue;float value=point.values[series];if(!Float.isFinite(value)){started=false;continue;}
            float x=left+width*(1-(now-point.time)/60000f),y=top+height*(1-Math.max(0,Math.min(100,value))/100);
            if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
        }
        line(c,left,top+height,left+width,top+height,0xFF56313B,1);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private void drawMedia(Canvas c) {
        JSONObject media=media();if(media==null)return;
        box(c,36,589,1128,115,panel);line(c,36,589,1164,589,accent,2);
        RectF thumb=new RectF(52,605,187,689);
        if(mediaThumbnail!=null) {
            float factor=Math.min(thumb.width()/mediaThumbnail.getWidth(),thumb.height()/mediaThumbnail.getHeight());
            float w=mediaThumbnail.getWidth()*factor,h=mediaThumbnail.getHeight()*factor;
            p.setColor(white);c.drawBitmap(mediaThumbnail,null,new RectF(thumb.centerX()-w/2,thumb.centerY()-h/2,thumb.centerX()+w/2,thumb.centerY()+h/2),p);
        }else {box(c,52,605,135,84,0xFF152131);text(c,"CHROME",67,653,17,accent,medium);}
        c.save();c.clipRect(204,598,845,651);
        fit(c,media.optString("title","Видео"),204,621,18,630,white);
        fit(c,"CHROME / "+media.optString("artist","")+(media.optBoolean("playing")?"":" · ПАУЗА"),204,645,12,630,muted);c.restore();
        double duration=media.optDouble("duration",0),position=media.optDouble("position",0);
        if(media.optBoolean("playing"))position+=(android.os.SystemClock.elapsedRealtime()-mediaReceived)/1000.0;
        if(duration>0)position=Math.min(position,duration);
        line(c,204,673,840,673,0xFF263446,5);
        if(duration>0){float x=204+(float)Math.max(0,Math.min(1,position/duration))*636;line(c,204,673,x,673,accent,5);p.setColor(accent);c.drawCircle(x,673,4,p);}
        text(c,mediaTime(position)+" / "+(duration>0?mediaTime(duration):"LIVE"),204,694,9,muted,regular);
        mediaButton(c,mediaPrevious,"|◀",media.optBoolean("canPrevious"));
        mediaButton(c,mediaToggle,media.optBoolean("playing")?"Ⅱ":"▶",media.optBoolean("canToggle"));
        mediaButton(c,mediaNext,"▶|",media.optBoolean("canNext"));
    }
    private void mediaButton(Canvas c,RectF rect,String title,boolean enabled) {
        box(c,rect.left,rect.top,rect.width(),rect.height(),enabled?0xFF263446:0xFF152131);
        p.setTextAlign(Paint.Align.CENTER);text(c,title,rect.centerX(),rect.centerY()+10,29,enabled?accent:muted,medium);p.setTextAlign(Paint.Align.LEFT);
    }
    static String mediaTime(double seconds){int time=(int)Math.max(0,seconds);return time>=3600?String.format(Locale.US,"%d:%02d:%02d",time/3600,time/60%60,time%60):String.format(Locale.US,"%d:%02d",time/60,time%60);}
    private void drawBlock(Canvas c,String key,boolean game) {
        if(!layout.visible(key,game))return;
        RectF target=layout.rect(key,game);
        if(mediaVisible()){target.top=125+(target.top-125)*.79f;target.bottom=125+(target.bottom-125)*.79f;}
        drawnCards.put(key,target);
        c.save();c.clipRect(target);c.translate(target.left,target.top);
        float w=target.width(),h=target.height();box(c,0,0,w,h,panel);
        switch(key) {
            case "main":gamePanel(c,w,h);break;
            case "cpu":device(c,w,h,"CPU",object("cpu"),accent,0);break;
            case "gpu":device(c,w,h,"GPU",object("gpu"),secondary,1);break;
            case "memory":memory(c,w,h);break;
            case "history":if(game)frameGraph(c,w,h);else graph(c,w,h);break;
            case "side":if(game)gameHardware(c,w,h);else cooling(c,w,h);break;
        }
        c.restore();
    }
    private void drawEditor(Canvas c,boolean game) {
        button(c,modeHit,"РЕДАКТОР / "+(game?"ИГРА":"ОБЗОР"));
        button(c,new RectF(655,20,820,86),"БЛОКИ / ДАННЫЕ");
        button(c,themeHit,"СОХРАНИТЬ");button(c,settingsHit,"ОТМЕНА");
        for(Map.Entry<String,RectF> entry:drawnCards.entrySet()) {
            RectF r=entry.getValue();p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(entry.getKey().equals(selection)?3:1);
            p.setColor(layout.valid(game)?accent:0xFFFF7777);c.drawRect(r,p);p.setStyle(Paint.Style.FILL);
            c.drawRect(r.right-22,r.bottom-22,r.right,r.bottom,p);
        }
        text(c,"Перетаскивай блоки · тяни нижний правый угол · красная рамка — пересечение",36,737,13,muted,medium);
    }
    private void finishAnimation(Canvas c,String key) {
        long now=android.os.SystemClock.elapsedRealtime();
        if(!key.equals(transitionKey)){transitionKey=key;transitionStart=now;}
        if(!nerv)return;
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<80;i++) {
            float x=i*18-18;Path stripe=new Path();stripe.moveTo(x,716);stripe.lineTo(x+8,716);stripe.lineTo(x+18,724);stripe.lineTo(x+10,724);stripe.close();p.setColor(secondary);c.drawPath(stripe,p);
        }
        if(animations && now-transitionStart<450 && !editing) {
            float t=(now-transitionStart)/450f;p.setColor(((int)((1-t)*80)<<24)|(accent&0xffffff));c.drawRect(0,100,1200,710,p);
            line(c,36,125+t*565,1164,125+t*565,accent,2);postInvalidateOnAnimation();
        }
    }
    private void drawStandby(Canvas c) {
        button(c,clockBack,"ПАНЕЛЬ");button(c,settingsHit,"НАСТРОЙКИ");
        java.time.ZonedDateTime now=java.time.ZonedDateTime.now();
        text(c,nerv?"NERV // STANDBY":"LIBRE // STANDBY",60,180,16,accent,medium);
        text(c,now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),52,390,164,white,digits);
        text(c,now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM",new Locale("ru"))),60,455,25,muted,regular);
        String city=weather==null?weatherCity:weather.optString("city","Санкт-Петербург");
        fit(c,city,730,175,25,410,white);
        if(weather==null){fit(c,weatherStatus,730,255,22,410,muted);fit(c,weatherHint,730,305,13,410,muted);}
        else {
            valueWithUnit(c,number(value(weather,"temperature")),730,250,64,"°C",24,22,accent);
            fit(c,weather.optString("summary",WeatherClient.description(weather.optInt("code"))),730,290,20,410,muted);
            long age=System.currentTimeMillis()-weather.optLong("fetched",0);
            String source=weather.optString("source","Open-Meteo");
            fit(c,(age>900000?"Сохранено · ":"")+source+" · нажми для обновления",730,319,11,410,muted);
        }
        drawForecast(c);
        line(c,730,543,1142,543,0xFF263446,1);
        text(c,"БУДИЛЬНИК  +",730,575,13,accent,medium);fit(c,nextAlarm,730,615,23,410,white);
        boolean waking=android.os.SystemClock.elapsedRealtime()<wakeSentUntil;
        int powerColor=nerv?0xFFFFB52E:accent;
        box(c,32,556,572,143,powerColor);
        box(c,clockWake.left,clockWake.top,clockWake.width(),clockWake.height(),waking?0xFF263C43:powerColor);
        int ink=waking?white:0xFF09121C;
        p.setColor(ink);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(new RectF(65,602,115,652),-55,290,false,p);c.drawLine(90,590,90,624,p);
        p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);
        text(c,waking?"КОМАНДА ОТПРАВЛЕНА":"ВКЛЮЧИТЬ ПК",145,618,28,ink,medium);
        text(c,waking?"ЖДУ ПОДКЛЮЧЕНИЯ…":"ВКЛЮЧИТЬ / РАЗБУДИТЬ · WAKE-ON-LAN",145,654,13,ink,regular);
        fit(c,sample==null?"ПК недоступен · "+error:"Соединение с ПК активно",60,738,12,1080,muted);
    }
    private void drawForecast(Canvas c) {
        java.time.Instant instant=java.time.Instant.now();
        java.time.LocalDate today=instant.atZone(WeatherClient.forecastZone(weather)).toLocalDate();
        text(c,"БЛИЖАЙШИЙ ПРОГНОЗ",730,356,13,accent,medium);
        JSONArray slots=WeatherClient.upcoming(weather,instant);
        for(int n=0;n<3;n++) {
            JSONObject slot=slots.optJSONObject(n);int hour=slot==null?9:slot.optInt("hour",9);
            String label=hour==9?"УТРО":hour==15?"ДЕНЬ":"ВЕЧЕР";
            float x=730+n*141;box(c,x,370,130,154,panel);
            text(c,label+" · "+hour+":00",x+9,389,10,accent,medium);
            text(c,slot==null?"":WeatherClient.dayLabel(slot.optString("date"),today),x+9,407,9,muted,regular);
            if(slot==null || !slot.has("temperature")){text(c,"—",x+10,441,26,white,digits);text(c,"Нет прогноза",x+9,460,11,muted,regular);continue;}
            text(c,number(value(slot,"temperature"))+"°",x+10,441,27,white,digits);
            String summary=slot.optString("summary","Нет данных");
            if(summary.length()>19)summary=summary.substring(0,18)+"…";
            fit(c,summary,x+9,457,12,112,muted);
            float probability=value(slot,"probability");
            text(c,"Осадки "+(Float.isNaN(probability)?"—":number(probability)+"%"),x+9,483,12,accent,regular);
            float precipitation=value(slot,"precipitation");
            text(c,(Float.isNaN(precipitation)?"—":decimal(precipitation))+" мм/"+(slot.optInt("precipitationHours",1)==3?"3ч":"ч"),x+9,507,11,muted,regular);
        }
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event) {
        float x=(event.getX()-offsetX)/scale,y=(event.getY()-offsetY)/scale;
        if(editing) {
            if(event.getAction()==MotionEvent.ACTION_DOWN) {
                selection=null;
                for(Map.Entry<String,RectF> e:drawnCards.entrySet())if(e.getValue().contains(x,y)){selection=e.getKey();dragRect=new RectF(e.getValue());downX=x;downY=y;resizing=x>dragRect.right-28 && y>dragRect.bottom-28;}
            } else if(event.getAction()==MotionEvent.ACTION_MOVE && selection!=null) {
                float dx=Math.round((x-downX)/10)*10,dy=Math.round((y-downY)/10)*10;RectF r=new RectF(dragRect);
                if(resizing){r.right=Math.max(r.left+160,Math.min(1164,r.right+dx));r.bottom=Math.max(r.top+110,Math.min(710,r.bottom+dy));}
                else {r.offset(Math.max(36-r.left,Math.min(1164-r.right,dx)),Math.max(125-r.top,Math.min(710-r.bottom,dy)));}
                layout.positions.put(DashboardLayout.id(selection,playing()),r);invalidate();
            } else if(event.getAction()==MotionEvent.ACTION_UP){
                if(themeHit.contains(x,y))onEditorSave.run();else if(settingsHit.contains(x,y))onEditorCancel.run();
                else if(new RectF(655,20,820,86).contains(x,y))onEditorOptions.run();
            }
            return true;
        }
        if(event.getAction()==MotionEvent.ACTION_UP) {
            performClick();
            if(mediaVisible() && new RectF(36,589,1164,704).contains(x,y)) {
                JSONObject media=media();
                if(mediaPrevious.contains(x,y)&&media.optBoolean("canPrevious"))onMedia.run("previous",0);
                else if(mediaToggle.contains(x,y)&&media.optBoolean("canToggle"))onMedia.run("toggle",0);
                else if(mediaNext.contains(x,y)&&media.optBoolean("canNext"))onMedia.run("next",0);
                else if(mediaSeek.contains(x,y)&&media.optBoolean("canSeek"))onMedia.run("seek",Math.max(0,Math.min(1,(x-204)/636)));
                return true;
            }
            if(standby()) {
                if(clockBack.contains(x,y))suppressStandby();else if(settingsHit.contains(x,y))onMenu.run();
                else if(clockAlarm.contains(x,y))onAlarm.run();else if(clockWeather.contains(x,y))onWeather.run();else if(clockWake.contains(x,y))onWake.run();
            } else if(settingsHit.contains(x,y))settings.run();else if(menuHit.contains(x,y))onMenu.run();
            else if(modeHit.contains(x,y))onModeChange.run();else if(themeHit.contains(x,y))theme.run();
            invalidate();
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
