package dev.librehw.tab;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.util.*;

/** A native Canvas dashboard: no WebView, remote fonts, or browser dependencies. */
public class DashboardView extends View {
    private final Paint p = new Paint(3);
    final AtmosphereState atmosphere=new AtmosphereState();
    java.util.function.Consumer<String> onSound=cue->{};
    boolean magiOpen;
    private boolean logoArmed,suppressMagiRelease;
    private float logoDownX,logoDownY;
    private String screenKey="";
    private long screenTransition=-10000;
    private final Runnable logoHold=()->{
        if(logoArmed&&!editing&&!boot.active){magiOpen=true;suppressMagiRelease=true;performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);onSound.accept("confirm");invalidate();}
    };
    private final Runnable settings, theme;
    private static final class ThermalPoint {
        final long time; final float[] values;
        ThermalPoint(long time,float cpu,float gpu,float cpuLoad,float gpuLoad,float memoryLoad){this.time=time;values=new float[]{cpu,gpu,cpuLoad,gpuLoad,memoryLoad};}
    }
    private static final class FpsPoint {final long time;final float fps;FpsPoint(long time,float fps){this.time=time;this.fps=fps;}}
    private final ArrayDeque<FpsPoint> fpsHistory=new ArrayDeque<>();
    private String fpsSource="";
    private final ArrayDeque<ThermalPoint> history = new ArrayDeque<>();
    private final MetricHistory trends=new MetricHistory();
    private JSONObject sample;
    private String error = UiText.get("Ожидание подключения");
    private boolean demo,receivedSample;
    public int themeId;
    public int detailLevel=1; // 0 clean, 1 NERV, 2 full MAGI
    static String[] detailLabels(){return new String[]{UiText.get("Чистый"),"NERV",UiText.get("Полный MAGI")};}
    static final int ALERT=0xFFFF5364;
    String focusedMetric;
    private String focusGesture;
    private float focusDownX,focusDownY;
    private final Map<String,RectF> focusTargets=new LinkedHashMap<>();
    private boolean localCoordinates;
    private DashboardTheme palette;
    private String drawingBlock="";
    private boolean fullMagi(){return nerv && detailLevel==2;}
    private boolean hot(String key){float t=value(object(key),"temperature");return Float.isFinite(t)&&t>=(key.equals("cpu")?85:83);}
    String operationalState(){return sample==null?"SIGNAL LOST":!sample.isNull("sensorError")?"SENSOR ERROR":hot("cpu")||hot("gpu")?"THERMAL ALERT":demo?"DEMO":"LIVE";}
    private int stateColor(){String state=operationalState();return state.equals("LIVE")||state.equals("DEMO")?accent:ALERT;}
    private int blockColor(){return (drawingBlock.equals("cpu")||drawingBlock.equals("gpu"))&&hot(drawingBlock)?ALERT:accent;}
    private String channel(){switch(drawingBlock){case "cpu":return "01";case "gpu":return "02";case "memory":return "03";case "history":return "04";case "audio":return "05";case "network":return "06";case "side":return "07";case "main":return "08";default:return "00";}}
    private final RectF focusArea=new RectF(36,125,1164,704);
    public boolean closeFocus(){if(magiOpen){magiOpen=false;invalidate();return true;}if(focusedMetric==null)return false;focusedMetric=null;focusGesture=null;invalidate();return true;}
    float aspectRatio(){return (getWidth()/VIEWPORT_WIDTH)/(getHeight()/VIEWPORT_HEIGHT);}
    private float textRatio(){return localCoordinates?1:aspectRatio();}
    DashboardLayout layout=new DashboardLayout();
    public boolean editing,forceStandby,autoStandby=true,animations=true;
    public JSONObject weather;
    public Bitmap mediaThumbnail;
    public interface MediaAction { void run(String action,double fraction); }
    public MediaAction onMedia=(action,fraction)->{};
    interface AudioAction {void run(String key,String id,String action,double value);}
    public AudioAction onAudio=(key,id,action,value)->{};
    public boolean audioCommandPending;
    public String pendingAudioKey="";
    public double pendingAudioVolume=Double.NaN;
    private JSONObject audioOverride;
    private long audioOverrideUntil;
    private AudioControl audioDrag,pressedAudio;
    private double audioPreview=Double.NaN;
    final Map<String,AudioControl> audioControls=new LinkedHashMap<>();
    static final class AudioControl {
        final String key,id,action;final RectF hit,track;final boolean enabled,muted,vertical;
        AudioControl(String key,String id,String action,RectF hit,RectF track,boolean enabled,boolean muted,boolean vertical){this.key=key;this.id=id;this.action=action;this.hit=hit;this.track=track;this.enabled=enabled;this.muted=muted;this.vertical=vertical;}
        double fraction(float x,float y){return Math.max(0,Math.min(1,vertical?1-(y-track.top)/Math.max(1,track.height()):(x-track.left)/Math.max(1,track.width())));}
    }
    JSONObject audio(){return audioOverride!=null&&android.os.SystemClock.elapsedRealtime()<audioOverrideUntil?audioOverride:object("audio");}
    public void audioAcknowledged(JSONObject value){audioOverride=value;audioOverrideUntil=android.os.SystemClock.elapsedRealtime()+700;invalidate();}
    private AudioControl audioAt(float x,float y){if(editing||focusedMetric!=null||!playing()||standby()||audioCommandPending)return null;for(AudioControl control:audioControls.values())if(control.enabled&&control.hit.contains(x,y))return control;return null;}
    private void registerAudio(String key,String id,String action,RectF localHit,RectF localTrack,boolean enabled,boolean muted,boolean vertical) {
        RectF origin=drawnCards.get("audio");if(origin==null)return;
        RectF hit=designRect(localHit),track=designRect(localTrack);hit.offset(origin.left,origin.top);track.offset(origin.left,origin.top);
        audioControls.put(key+"."+action,new AudioControl(key,id,action,hit,track,enabled&&!audioCommandPending,muted,vertical));
    }
    private void audioCard(Canvas c,float w,float h) {
        float pad=padding(w,h);boolean compact=h<210;cardTitle(c,"05 / AUDIO MIXER",w,h);
        JSONObject state=audio(),mic=state==null?null:state.optJSONObject("microphone");boolean ready=state!=null&&state.optBoolean("ready");
        boolean micAvailable=ready&&mic!=null&&mic.optBoolean("available"),micMuted=mic!=null&&mic.optBoolean("muted"),mixed=mic!=null&&mic.optBoolean("mixed");
        RectF microphone=new RectF(pad,pad+20,w-pad,pad+20+(compact?bounded(h*.24f,20,38):bounded(h*.18f,42,60)));
        int micColor=!micAvailable?muted:mixed?0xFFFFC36A:micMuted?ALERT:accent;
        box(c,microphone.left,microphone.top,microphone.width(),microphone.height(),surface(0xFF263446,35));
        String status=!ready?state==null?UiText.get("ОБНОВИ АГЕНТ ПК"):UiText.get("ЗВУК НЕДОСТУПЕН"):!micAvailable?UiText.get("МИКРОФОН НЕДОСТУПЕН"):mixed?UiText.get("МИКРОФОН / MIXED"):micMuted?UiText.get("MIC CUT / МИКРОФОН"):UiText.get("МИКРОФОН / ON");
        fit(c,status,microphone.left+10,microphone.centerY()+(compact?4:micAvailable?-3:5),compact?11:18,microphone.width()-20,micColor);
        if(!compact&&micAvailable)fit(c,UiText.get("ВСЕ ВХОДЫ WINDOWS · ")+mic.optInt("devices")+(audioCommandPending&&pendingAudioKey.equals("microphone")?UiText.get(" · КОМАНДА…"):""),microphone.left+10,microphone.bottom-8,9,microphone.width()-20,muted);
        registerAudio("microphone",mic==null?"":mic.optString("id"),"mute",microphone,microphone,micAvailable,micMuted,false);
        JSONArray buses=state==null?null:state.optJSONArray("buses");String[] keys={"chrome","game","discord"},names={"CHROME",UiText.get("ИГРА"),"DISCORD"};
        float rowsTop=microphone.bottom+12,rowHeight=(h-pad-rowsTop)/3;
        for(int i=0;i<3;i++) {
            JSONObject bus=null;if(buses!=null)for(int n=0;n<buses.length();n++){JSONObject item=buses.optJSONObject(n);if(item!=null&&keys[i].equals(item.optString("key"))){bus=item;break;}}
            String key=keys[i],id=bus==null?"":bus.optString("id");boolean available=ready&&bus!=null&&bus.optBoolean("available"),isMuted=bus!=null&&bus.optBoolean("muted");
            double volume=bus==null?0:bus.optDouble("volume",0);
            if(audioDrag!=null&&audioDrag.key.equals(key)&&Double.isFinite(audioPreview))volume=audioPreview;
            else if(audioCommandPending&&pendingAudioKey.equals(key)&&Double.isFinite(pendingAudioVolume))volume=pendingAudioVolume;
            volume=Math.max(0,Math.min(1,volume));int color=!available?muted:isMuted?ALERT:accent;
            float top=rowsTop+i*rowHeight;String reading=available?Math.round(volume*100)+"%"+(bus.optBoolean("mixed")?"*":""):"—";
            RectF track,hit;
            if(rowHeight<36) {
                float labelWidth=bounded(w*.25f,36,66),y=top+rowHeight*.53f;
                fit(c,names[i],pad,y+3,9,labelWidth,color);
                track=new RectF(pad+labelWidth+7,y,w-pad-34,y);
                p.setTextAlign(Paint.Align.RIGHT);fit(c,reading,w-pad,y+3,9,31,available?white:muted);p.setTextAlign(Paint.Align.LEFT);
                hit=new RectF(track.left-3,top,track.right+3,top+rowHeight);
            }else {
                float y=top+rowHeight*.53f,muteWidth=bounded(w*.15f,48,64),right=w-pad-muteWidth-18;
                float labelRight=w<260?w-pad:right;
                fit(c,names[i],pad,top+12,12,labelRight-pad-62,color);
                p.setTextAlign(Paint.Align.RIGHT);fit(c,reading,labelRight,top+12,14,60,available?white:muted);p.setTextAlign(Paint.Align.LEFT);
                track=new RectF(pad+4,y,right,y);
                float hitHeight=Math.min(50,rowHeight*.66f);hit=new RectF(pad,Math.max(top+16,y-hitHeight/2),right+8,Math.min(top+rowHeight-2,y+hitHeight/2));
                float bh=bounded(rowHeight*.54f,26,38);RectF mute=new RectF(w-pad-muteWidth,y-bh/2,w-pad,y+bh/2);
                box(c,mute.left,mute.top,mute.width(),mute.height(),available?surface(0xFF263446,35):surface(0xFF152131,18));
                centerFit(c,isMuted?"MUTED":"MUTE",mute.centerX(),mute.centerY()+4,10,mute.width()-8,color);
                registerAudio(key,id,"mute",mute,mute,available,isMuted,false);
                if(i<2)line(c,pad,top+rowHeight-3,w-pad,top+rowHeight-3,surface(0xFF38222A,24),1);
            }
            line(c,track.left,track.top,track.right,track.top,surface(0xFF3D4555,65),rowHeight<36?4:7);
            if(available){float x=track.left+(float)volume*track.width();line(c,track.left,track.top,x,track.top,color,rowHeight<36?4:7);p.setColor(white);circle(c,x,track.top,rowHeight<36?3:7,p);}
            registerAudio(key,id,"volume",hit,track,available,isMuted,false);
        }
        if(audioCommandPending){float x=pad+(animations?(android.os.SystemClock.elapsedRealtime()%1000)/1000f*(w-pad*2-30):0);line(c,x,h-3,x+30,h-3,accent,2);if(animations)postInvalidateOnAnimation();}
    }
    private long mediaReceived,lastMediaSeen;
    private JSONObject displayMedia;
    private boolean mediaTransition;
    private double transitionPosition;
    public boolean mediaCommandPending;
    private RectF pressedRect;
    private boolean fingerDown,touchStarted;
    private long releasedAt;
    private final RectF mediaPrevious=new RectF(877,605,957,685),mediaToggle=new RectF(973,605,1053,685),mediaNext=new RectF(1069,605,1149,685),mediaSeek=new RectF(204,657,840,691);
    public JSONObject media(){return displayMedia;}
    boolean mediaVisible(){return focusedMetric==null && !editing && sample!=null && media()!=null && !media().optString("id").isEmpty();}
    final EvaBootSequence boot=new EvaBootSequence();
    long wakeStarted,wakeDeadline;
    String wakeError="";
    boolean connected(){return sample!=null;}
    boolean wakePending(){return wakeDeadline>0;}
    boolean canWake(){return !connected()&&!wakePending();}
    boolean beginWake(){
        if(!canWake())return false;
        wakeStarted=android.os.SystemClock.elapsedRealtime();wakeDeadline=wakeStarted+60000;wakeError="";closeFocus();boot.start(wakeStarted);forceStandby=true;invalidate();return true;
    }
    void failWake(String message){if(!wakePending()||connected())return;wakeDeadline=0;wakeError=message;invalidate();}
    void updateWake(){if(wakePending()&&android.os.SystemClock.elapsedRealtime()>=wakeDeadline)failWake(UiText.get("ПК не ответил за 1 минуту. Проверь питание и Wake-on-LAN."));}
    public String weatherCity="Санкт-Петербург";
    public String weatherHint=UiText.get("Нажми, чтобы обновить");
    public String weatherStatus=UiText.get("Погода загружается…");
    public String nextAlarm=UiText.get("Будильник не задан");
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
    // Retain saved layout coordinates, but fill the physical display on both axes.
    // Trim the old 36-unit gutters to a small 12-unit drawing inset.
    private static final float VIEWPORT_LEFT=24, VIEWPORT_WIDTH=1152, VIEWPORT_HEIGHT=760;
    float screenX(float x){return (x-VIEWPORT_LEFT)*getWidth()/VIEWPORT_WIDTH;}
    float screenY(float y){return y*getHeight()/VIEWPORT_HEIGHT;}
    private int accent, secondary, panel;
    private final int white = Color.rgb(236, 244, 249), muted = Color.rgb(134, 153, 173);
    private final Typeface terminal = Typeface.create("monospace", Typeface.BOLD);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface digits = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final RectF settingsHit = new RectF(1000, 20, 1164, 86), themeHit = new RectF(830, 20, 990, 86);

    public DashboardView(Context context, Runnable settings, Runnable theme) {
        super(context); this.settings = settings; this.theme = theme;
        setContentDescription(UiText.get("Панель мониторинга ПК. Настройки и тема расположены вверху справа."));
        setFocusable(true);
    }
    public void clear() {closeFocus();receivedSample=false; history.clear();trends.clear();fpsHistory.clear();fpsSource="";displayMedia=null;mediaTransition=false;mediaThumbnail=null;audioOverride=null;audioDrag=null;audioPreview=Double.NaN; sample = null; error = UiText.get("Подключение…"); invalidate(); }
    public void accept(JSONObject data, String error, boolean demo) {
        sample = data;
        if(data==null&&boot.active&&boot.linked>=0){boot.linked=-1;wakeDeadline=wakeStarted+60000;forceStandby=true;updateWake();}
        if(data!=null&&(wakePending()||boot.active)){boot.connect(android.os.SystemClock.elapsedRealtime());wakeDeadline=0;wakeError="";forceStandby=false;}
        if(data==null)audioOverride=null; if(data!=null){lastGood=android.os.SystemClock.elapsedRealtime();receivedSample=true;} this.error = error; this.demo = demo;
        long now=android.os.SystemClock.elapsedRealtime();
        String cue=atmosphere.accept(data,now);if(!cue.isEmpty())onSound.accept(cue);
        trends.accept(data,now);
        JSONObject incoming=data==null?null:data.optJSONObject("media");
        if(incoming!=null) {displayMedia=incoming;mediaReceived=now;lastMediaSeen=now;mediaTransition=false;}
        else if(data!=null && displayMedia!=null && displayMedia.optBoolean("playing") && now-lastMediaSeen<5000) {
            if(!mediaTransition)transitionPosition=displayMedia.optDouble("position",0)+(now-mediaReceived)/1000.0;
            mediaTransition=true;
        }else {displayMedia=null;mediaTransition=false;}
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
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTypeface(nerv ? (font==medium?terminal:Typeface.MONOSPACE) : font); p.setTextSize(size); c.save();c.translate(x,y);c.scale(1/textRatio(),1);c.drawText(text,0,0,p);c.restore();
    }
    private void fit(Canvas c,String value,float x,float y,float size,float width,int color) {
        width*=textRatio();if(width<=0)return;String label=value==null?"—":value;
        p.setTypeface(nerv?terminal:medium);p.setTextSize(size);
        while(p.measureText(label)>width && size>10)p.setTextSize(--size);
        if(p.measureText(label)>width){int end=p.breakText(label,true,Math.max(0,width-p.measureText("…")),null);label=label.substring(0,end)+"…";}
        text(c,label,x,y,size,color,medium);
    }
    private void centerFit(Canvas c,String value,float center,float baseline,float size,float width,int color){p.setTextAlign(Paint.Align.CENTER);fit(c,value,center,baseline,size,width,color);p.setTextAlign(Paint.Align.LEFT);}
    private void box(Canvas c, float x, float y, float w, float h, int color) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); if(!nerv || detailLevel==0)c.drawRoundRect(x,y,x+w,y+h,22,22,p);
        else {
            float cut=Math.min(18,Math.min(w,h)/3);
            Path path=new Path();path.moveTo(x+cut,y);path.lineTo(x+w,y);path.lineTo(x+w,y+h-cut);path.lineTo(x+w-cut,y+h);path.lineTo(x,y+h);path.lineTo(x,y+cut);path.close();c.drawPath(path,p);
            if(h>50){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor((accent&0xffffff)|0x66000000);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
        }
    }
    private void circle(Canvas c,float x,float y,float radius,Paint paint){
        c.save();c.translate(x,y);c.scale(1/textRatio(),1);c.drawCircle(0,0,radius,paint);c.restore();
    }
    private void line(Canvas c, float x, float y, float endX, float endY, int color, float stroke) {
        p.setShader(null); p.setColor(color); p.setStrokeWidth(stroke); p.setStyle(Paint.Style.STROKE); c.drawLine(x,y,endX,endY,p); p.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);updateWake();
        palette=DashboardTheme.ALL[Math.max(0,Math.min(DashboardTheme.ALL.length-1,themeId))];
        nerv=palette.isNerv();
        accent=palette.primary;secondary=palette.secondary;panel=palette.panel;
        long bootNow=android.os.SystemClock.elapsedRealtime();
        if(boot.finished(bootNow,animations)){boot.active=false;}
        if(boot.active&&!boot.revealing(bootNow,animations)){
            boot.draw(c,getWidth(),getHeight(),bootNow,wakeDeadline,wakeError,palette,animations);
            if(animations&&wakeError.isEmpty())postInvalidateOnAnimation();else postInvalidateDelayed(1000);
            return;
        }
        String nextScreen=editing?"editor":magiOpen?"magi":standby()?"standby":focusedMetric!=null?"focus/"+focusedMetric:playing()?"game":"overview";
        nextScreen+="/"+themeId;
        if(!nextScreen.equals(screenKey)){if(!screenKey.isEmpty())screenTransition=bootNow;screenKey=nextScreen;}
        c.drawColor(palette.background);
        if(getWidth()==0 || getHeight()==0)return;
        c.save();c.scale(getWidth()/VIEWPORT_WIDTH,getHeight()/VIEWPORT_HEIGHT);c.translate(-VIEWPORT_LEFT,0);
        p.setShader(new RadialGradient(300, 100, 750, palette.glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(0,0,1200,760,p); p.setShader(null);
        if(editing || standby())focusedMetric=null;
        focusTargets.clear();
        if(nerv && detailLevel>0)drawTerminalFrame(c,palette);
        if(magiOpen){drawMagiTerminal(c);c.restore();postInvalidateDelayed(1000);return;}
        if(standby()){drawStandby(c);finishAnimation(c,"standby");drawStatus(c);drawInteraction(c);c.restore();postInvalidateDelayed(1000);return;}
        text(c, nerv?"NERV":"LIBRE", 36, 53, 29, nerv?accent:white, medium); text(c, nerv?"MAGI / "+(palette.name.equals("NERV")?"SYS":palette.name.substring(4)):"H W   T A B", 144, 52, nerv?13:16, accent, medium);
        boolean playing=playing();
        if(nerv && detailLevel>0 && palette.name.startsWith("EVA-"))
            fit(c,palette.unitDescription(),36,80,9,250,secondary);
        if(editing)drawEditorHeader(c,playing);
        else {
            String time=java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            text(c,time,700,53,27,white,digits);
            button(c,menuHit,UiText.get("МЕНЮ"));
            button(c,modeHit,mode==0?(playing?UiText.get("AUTO / ИГРА"):UiText.get("AUTO / ОБЗОР")):mode==1?UiText.get("РЕЖИМ / ОБЗОР"):UiText.get("РЕЖИМ / ИГРА"));
            button(c,themeHit,"THEME / "+palette.name);button(c,settingsHit,UiText.get("ПОДКЛЮЧЕНИЕ"));
        }
        boolean healthy = sample != null && sample.isNull("sensorError");
        int status = sample == null ? ALERT : healthy ? accent : ALERT;
        p.setColor(status); circle(c,43, 93, 4, p);
        String caption = demo ? UiText.get("DEMO MODE  /  ДЕМОНСТРАЦИОННЫЕ ДАННЫЕ") : sample != null ? sample.optString("host", "PC") + (healthy ? "  /  LIVE TELEMETRY" : UiText.get("  /  ОШИБКА ДАТЧИКОВ")) : "SIGNAL LOST  /  " + error;
        if(fullMagi())drawOperationalBand(c,caption);
        else fit(c, caption, 56, 99, 13, 1090, status);
        drawAtmosphereNotice(c);
        drawnCards.clear();audioControls.clear();
        if(focusedMetric!=null){drawFocus(c);drawStatus(c);c.restore();postInvalidateDelayed(1000);return;}
        if(playing){drawBlock(c,"main",true);drawBlock(c,"audio",true);}
        else {
            drawBlock(c,"cpu",false);
            drawBlock(c,"gpu",false);
        }
        if(playing){drawBlock(c,"gpu",true);drawBlock(c,"cpu",true);}
        else {drawBlock(c,"side",false);drawBlock(c,"network",false);}
        if(layout.metric("memory"))drawBlock(c,"memory",playing);
        if(layout.metric("history"))drawBlock(c,"history",playing);
        if(mediaVisible())drawMedia(c);
        if(editing)drawEditor(c,playing);
        if(!boot.active)finishAnimation(c,(playing?"game":"overview")+themeId);
        JSONObject game = object("game");
        if(!editing && sample!=null && !sample.isNull("sensorError"))fit(c,UiText.get("Ошибка чтения датчиков — проверь агент"),36,727,12,1128,ALERT);
        else if(!editing && game!=null && !game.optBoolean("active") && !game.optString("status").matches("idle|ready|starting"))fit(c,"FPS: "+game.optString("status"),36,727,12,1128,muted);
        if(!editing)drawStatus(c);
        drawInteraction(c);
        c.restore();
        if(boot.active){boot.draw(c,getWidth(),getHeight(),bootNow,wakeDeadline,wakeError,palette,animations);postInvalidateOnAnimation();}
        else postInvalidateDelayed(1000);
    }
    private static int tint(int color,int alpha){return (color&0x00ffffff)|(alpha<<24);}
    private int surface(int fallback,int alpha){return nerv?tint(accent,alpha):fallback;}
    /** Static drafting marks: no extra animation or telemetry state. */
    private void drawTerminalFrame(Canvas c,DashboardTheme palette) {
        line(c,36,112,1164,112,tint(accent,60),1);
        if(!fullMagi())return;
        for(int x=36;x<=1164;x+=24)line(c,x,110,x,116,tint(accent,130),1);
        if(!standby())line(c,36,68,285,68,tint(accent,160),2);
    }
    private void drawOperationalBand(Canvas c,String caption){
        int color=stateColor();
        p.setColor(tint(color,28));c.drawRect(36,83,1164,108,p);
        p.setColor(color);c.drawRect(36,83,290,108,p);
        fit(c,"MAGI / "+operationalState(),44,100,13,238,0xFF100D14);
        String explanation=operationalState().equals("THERMAL ALERT")?(hot("cpu")?"CPU "+number(value(object("cpu"),"temperature"))+"°C  ":"")+(hot("gpu")?"GPU "+number(value(object("gpu"),"temperature"))+"°C  ":"")+" / "+caption:caption;
        fit(c,explanation,304,100,11,845,color);
    }
    private void panelFrame(Canvas c,float w,float h) {
        if(!nerv || detailLevel==0)return;
        if(!fullMagi()){
            line(c,20,1,Math.min(w-20,65),1,tint(accent,130),1);
            return;
        }
        int color=blockColor();
        line(c,20,2,w-20,2,color,3);
        line(c,2,20,2,h-20,color,3);
        for(int y=28;y<h-16;y+=20)line(c,w-5,y,w-1,y,color,2);
        c.save();c.clipRect(18,h-7,w-18,h-2);
        for(int x=18;x<w-18;x+=16)line(c,x,h-7,x+8,h-2,tint(color,130),3);
        c.restore();
        if(palette.name.equals("EVA-00")){
            for(int i=0;i<5;i++)line(c,w-38+i*5,h-20,w-38+i*5,h-20-(i%2==0?9:5),secondary,1);
            line(c,w-42,h-20,w-12,h-20,tint(secondary,150),1);
        }else if(palette.name.equals("EVA-01")){
            Path hex=new Path();for(int i=0;i<6;i++){double a=Math.PI/3*i;float x=w-24+(float)Math.cos(a)*10,y=h-23+(float)Math.sin(a)*10;if(i==0)hex.moveTo(x,y);else hex.lineTo(x,y);}hex.close();p.setColor(secondary);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);c.drawPath(hex,p);p.setStyle(Paint.Style.FILL);
        }else if(palette.name.equals("EVA-02")){
            for(int i=0;i<3;i++)line(c,w-40+i*9,h-14,w-33+i*9,h-29,secondary,3);
        }
    }
    private void button(Canvas c, RectF r, String label) {
        box(c, r.left, r.top+7, r.width(), 40, fingerDown&&r.equals(pressedRect)?surface(0xFF324557,75):surface(0xFF152131,24));
        fit(c, label, r.left+14, r.top+33, 12, r.width()-28, nerv?accent:muted);
        if(nerv && detailLevel>0)line(c,r.left+14,r.top+47,r.right-14,r.top+47,tint(accent,90),1);
    }
    private static float bounded(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private float padding(float w,float h){return bounded(Math.min(w,h)*.075f,12,24);}
    private void cardTitle(Canvas c,String label,float w,float h){
        float pad=padding(w,h);
        if(fullMagi()){
            int color=blockColor();boolean thermal=(drawingBlock.equals("cpu")||drawingBlock.equals("gpu"))&&hot(drawingBlock);
            String title=thermal?drawingBlock.toUpperCase(Locale.ROOT)+" / THERMAL ALERT":label;
            p.setColor(color);c.drawRect(pad-5,pad-4,w-pad+5,pad+18,p);
            float reserved=w>270?58:0;
            if(label.contains("RESOURCE LOAD")||label.equals("THERMAL HISTORY"))reserved=140;
            fit(c,title,pad,pad+12,h<160?10:12,w-pad*2-reserved,0xFF100D14);
            if(w>270 && reserved<100){p.setTextAlign(Paint.Align.RIGHT);text(c,"CH."+channel(),w-pad,pad+11,9,0xFF100D14,medium);p.setTextAlign(Paint.Align.LEFT);}
        }else fit(c,label,pad,pad+12,h<160?10:12,w-pad*2,accent);
    }
    private void metric(Canvas c,String title,String value,String unit,RectF area,int color) {
        float font=bounded(area.height()*.47f,14,46);fit(c,title,area.left,area.top+11,10,area.width(),muted);
        fit(c,value,area.left,Math.min(area.bottom-2,area.top+15+font),font,area.width(),color);
        if(!unit.isEmpty() && area.height()>65)fit(c,unit,area.left,area.bottom-2,10,area.width(),muted);
    }
    private void gamePanel(Canvas c,float w,float h) {
        float pad=padding(w,h),body=h<155?33:h<220?71:84;JSONObject game=object("game");
        cardTitle(c,w<350?"01 / GAME":"01 / GAME TELEMETRY",w,h);
        if(h>=155)fit(c,game==null?UiText.get("Ожидание игры"):game.isNull("name")?UiText.get("Ожидание игры"):game.optString("name"),pad,h<220?56:66,h<240?20:25,w-pad*2,white);
        if(h<155) {
            if(layout.metric("fps"))fit(c,number(value(game,"fps"))+" FPS",pad,Math.min(pad+49,h*.57f),h<110?24:32,w-pad*2,white);
            if(layout.metric("fps"))trend(c,"game.fps",pad,h*.57f+5,w-pad*2,4,accent,false);
            String[] smallKeys={"low1","low01","frameTime"},smallLabels={"1% LOW",UiText.get("0,1% LOW"),UiText.get("мс")};
            int count=0;for(String key:smallKeys)if(layout.metric(key))count++;
            int column=0;float cw=(w-pad*2)/Math.max(1,count);
            for(int i=0;i<3;i++)if(layout.metric(smallKeys[i])) {
                float x=pad+column++*cw;fit(c,smallLabels[i],x,h-28,9,cw-6,muted);
                fit(c,i==2?decimal(value(game,smallKeys[i])):number(value(game,smallKeys[i])),x,h-16,16,cw-6,white);
                trend(c,"game."+smallKeys[i],x,h-9,cw-6,4,accent,false);
            }
            return;
        }
        if(w>=330 && w<540 && h>=230 && layout.metric("fps")) {
            float signalHeight=h>=300?70:45,contentBottom=h-pad-signalHeight-16,top=84;
            float heroWidth=(w-pad*3)*.50f,contentHeight=contentBottom-top;
            float size=bounded(Math.min(heroWidth*.62f,contentHeight*.75f),44,104);
            fit(c,number(value(game,"fps")),pad,top+size,size,heroWidth,white);
            fit(c,game!=null&&game.optBoolean("paused")?UiText.get("ПАУЗА"):"PRESENTED FPS",pad,top+size+20,10,heroWidth,accent);
            java.util.ArrayList<String> stats=new java.util.ArrayList<>();for(String key:new String[]{"low1","low01","frameTime"})if(layout.metric(key))stats.add(key);
            trend(c,"game.fps",pad,top+size+28,heroWidth,8,accent,false);
            float x=pad+heroWidth+pad,row=contentHeight/Math.max(1,stats.size());
            for(int i=0;i<stats.size();i++) {
                String key=stats.get(i),label=key.equals("low1")?UiText.get("1% LOW · 10 С"):key.equals("low01")?UiText.get("0,1% LOW · 60 С"):UiText.get("КАДР / мс");float y=top+i*row;
                String reading=key.equals("frameTime")?decimal(value(game,key)):key.equals("low01")&&Float.isNaN(value(game,key))?low01Status(game):number(value(game,key));
                if(row<34)fit(c,(key.equals("low1")?"1% ":key.equals("low01")?UiText.get("0,1% "):UiText.get("мс "))+reading,x,y+row-5,13,w-pad-x,white);
                else {fit(c,label,x,y+10,9,w-pad-x,muted);fit(c,reading,x,y+row-10,bounded(row*.43f,18,30),w-pad-x,white);}
                trend(c,"game."+key,x,y+row-5,w-pad-x,4,accent,false);
            }
            if(layout.metric("history"))fpsSignal(c,new RectF(pad,contentBottom+16,w-pad,h-pad),game);
            return;
        }
        boolean wide=w>=540,showFps=layout.metric("fps");float heroWidth=showFps?(wide?(w-pad*3)*.34f:w-pad*2):0;
        float heroHeight=wide?h-body-pad:(h>=220?87:42);
        float fpsSize=bounded(Math.min(heroWidth*.64f,heroHeight*.78f),30,148);
        if(layout.metric("fps")) {
            fit(c,number(value(game,"fps")),pad,body+fpsSize,fpsSize,heroWidth,white);
            if(heroHeight>=75)text(c,"PRESENTED FPS",pad,body+fpsSize+21,11,accent,medium);
            trend(c,"game.fps",pad,body+fpsSize+(heroHeight>=75?28:5),heroWidth,5,accent,false);
        }
        java.util.ArrayList<String> keys=new java.util.ArrayList<>();for(String key:new String[]{"low1","low01","frameTime"})if(layout.metric(key))keys.add(key);
        float statsX=wide&&showFps?pad+heroWidth+pad:pad,statsY=wide||!showFps?body:body+heroHeight+10;
        float statsW=w-pad-statsX,statH=wide?70:Math.min(72,h-statsY-pad);
        for(int i=0;i<keys.size();i++) {
            String key=keys.get(i),label=key.equals("low1")?"1% LOW":key.equals("low01")?UiText.get("0,1% LOW"):UiText.get("ВРЕМЯ КАДРА");
            float gap=keys.size()>1?10:0,cw=(statsW-gap*(keys.size()-1))/Math.max(1,keys.size());
            RectF area=new RectF(statsX+i*(cw+gap),statsY,statsX+i*(cw+gap)+cw,statsY+statH);
            metric(c,label,key.equals("frameTime")?decimal(value(game,key)):number(value(game,key)),key.equals("frameTime")?UiText.get("мс"):key.equals("low01")?low01Status(game):"FPS",area,white);
            trend(c,"game."+key,area.left,area.bottom+5,area.width(),6,accent,false);
        }
        float signalTop=wide?body+92:statsY+statH+18;
        if(layout.metric("history") && h-signalTop-pad>=40)fpsSignal(c,new RectF(wide?statsX:pad,signalTop,w-pad,h-pad),game);
        if(game!=null && game.optBoolean("paused") && h>=200)fit(c,UiText.get("ALT+TAB / ПАУЗА"),pad,h-pad,10,heroWidth,muted);
    }
    private void fpsSignal(Canvas c,RectF area,JSONObject game) {
        boolean budget=area.width()>=390 && area.height()>=80;float budgetW=budget?108:0;
        float left=area.left,top=area.top+18,right=area.right-budgetW,bottom=area.bottom-4;
        fit(c,UiText.get("MAGI // FPS SIGNAL · 60 С"),left,area.top+8,9,right-left,accent);
        for(int i=0;i<=8;i++){float x=left+(right-left)*i/8;line(c,x,top,x,bottom,surface(0xFF38222A,24),1);}
        for(int i=0;i<3;i++){float y=top+(bottom-top)*i/2;line(c,left,y,right,y,surface(0xFF56313B,55),1);}
        long now=android.os.SystemClock.elapsedRealtime();float maximum=60;
        for(FpsPoint point:fpsHistory)if(Float.isFinite(point.fps))maximum=Math.max(maximum,point.fps);
        maximum=(float)Math.ceil(maximum/60)*60;
        if(fullMagi() && bottom-top>=60){
            for(int i=0;i<3;i++)text(c,number(maximum*(1-i/2f))+" FPS",left+4,top+11+(bottom-top-16)*i/2,8,muted,regular);
            for(int i=0;i<=4;i++){float x=left+(right-left)*i/4;line(c,x,top,x,bottom,tint(accent,65),1);}
        }
        Path path=new Path();boolean started=false;float lastX=0,lastY=0;
        for(FpsPoint point:fpsHistory) {
            if(now-point.time>60000)continue;
            if(!Float.isFinite(point.fps)){started=false;continue;}
            float x=left+(right-left)*(1-(now-point.time)/60000f),y=bottom-(bottom-top)*Math.min(1,point.fps/maximum);
            if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);lastX=x;lastY=y;
        }
        p.setColor(accent);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        if(started){p.setColor(accent);circle(c,lastX,lastY,3,p);}
        if(fpsHistory.size()<2)fit(c,UiText.get("ОЖИДАНИЕ СИГНАЛА"),left+8,top+20,10,right-left-16,muted);
        if(budget) {
            float frame=value(game,"frameTime"),row=(area.height()-8)/3;
            for(int i=0;i<3;i++) {
                int fps=new int[]{60,120,240}[i];float y=area.top+i*row;
                boolean met=Float.isFinite(frame)&&frame<=1000f/fps;
                text(c,fps+" FPS",area.right-94,y+Math.min(11,row*.48f),row<25?8:10,met?accent:muted,medium);
                for(int n=0;n<7;n++){p.setColor(Float.isFinite(frame)?met?accent:secondary:surface(0xFF38222A,24));c.drawRect(area.right-94+n*12,y+row*.60f,area.right-86+n*12,y+row*.60f+Math.max(2,row*.18f),p);}
            }
        }
    }
    private void frameGraph(Canvas c,float w,float h) {
        float pad=padding(w,h);cardTitle(c,UiText.get("FRAME TIME · 60 С"),w,h);
        JSONObject game=object("game");JSONArray frames=game==null?null:game.optJSONArray("frameSeries");
        float limit=33.4f;if(frames!=null)for(int i=0;i<frames.length();i++){double v=frames.optDouble(i);if(Double.isFinite(v)&&v>0)limit=Math.max(limit,(float)v);}
        limit=(float)Math.ceil(limit/8.333f)*8.333f;
        RectF plot=new RectF(pad,pad+28,w-pad-25,h-pad-15);if(plot.height()<20){fit(c,decimal(value(game,"frameTime"))+UiText.get(" мс"),pad,h-pad,24,w-pad*2,white);return;}
        chartGrid(c,plot,limit,false);
        if(nerv)for(float budget:new float[]{4.167f,8.333f,16.667f})if(budget<limit){float y=plot.bottom-plot.height()*budget/limit;line(c,plot.left,y,plot.right,y,surface(0xFF824354,95),1);if(plot.height()>100)text(c,Math.round(1000/budget)+" FPS",plot.left+4,y-3,8,secondary,medium);}
        if(frames==null || frames.length()<2)fit(c,UiText.get("Ожидание кадров…"),plot.left+5,plot.centerY(),12,plot.width()-10,muted);
        else {
            Path path=new Path();boolean started=false;int count=Math.min(300,frames.length()),start=frames.length()-count;
            for(int i=0;i<count;i++) {
                double value=frames.optDouble(start+i);if(!Double.isFinite(value)||value<=0){started=false;continue;}
                float x=plot.left+plot.width()*(300-count+i)/299,y=plot.bottom-plot.height()*(float)value/limit;
                if(nerv)line(c,x,plot.bottom,x,y,value>16.667?tint(ALERT,119):tint(accent,85),1);
                if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
            }
            p.setColor(accent);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
        chartTimes(c,plot,h-pad,60);
    }
    private void device(Canvas c,float w,float h,String title,JSONObject data,int color,int series) {
        float pad=padding(w,h);boolean compact=h<190;cardTitle(c,compact?title:title.equals("CPU")?"01 / PROCESSOR":"02 / GRAPHICS",w,h);
        if(!compact)fit(c,data==null?UiText.get("Ожидание датчика"):data.optString("name","—"),pad,pad+46,20,w-pad*2,white);
        float body=compact?pad+25:pad+68,footer=h-pad-43;
        float bodyH=Math.max(36,footer-body-10);boolean hasTemp=layout.metric("temperature"),hasLoad=layout.metric("load");boolean columns=hasTemp && hasLoad && (w>=300 || compact);
        float firstW=columns?(w-pad*3)*.61f:w-pad*2,loadX=columns?pad+firstW+pad:pad;
        float tempSize=bounded(Math.min(firstW*.65f,bodyH*.65f),25,84);
        int thermal=Float.isFinite(value(data,"temperature"))&&value(data,"temperature")>=(series==0?85:83)?ALERT:color;
        if(layout.metric("temperature")) {
            valueWithUnit(c,number(value(data,"temperature")),pad,body+tempSize,tempSize,"°C",bounded(tempSize*.30f,12,24),tempSize*.43f,thermal);
            if(bodyH>70)text(c,UiText.get("ТЕМПЕРАТУРА"),pad,body+tempSize+20,10,color,medium);
            float ty=body+tempSize+(bodyH>70?28:5),th=(columns||!hasLoad?footer-18:ty+6)-ty;
            trend(c,(series==0?"cpu":"gpu")+".temperature",pad,ty,firstW,Math.max(4,th),thermal,true);
        }
        if(layout.metric("load")) {
            float load=value(data,"load"),lw=columns?w-pad-loadX:w-pad*2;
            float ly=columns||!hasTemp?body:body+tempSize+35,lh=columns||!hasTemp?bodyH:Math.max(40,footer-ly-8);
            if(nerv || lw<105 || lh<90) {
                float size=bounded(Math.min(lw*.40f,lh*.48f),20,52);
                fit(c,number(load)+"%",loadX,ly+size,size,lw,white);
                if(lh>65){nervSegments(c,loadX,ly+size+12,lw,Math.min(20,lh*.13f),load,color,16);}else nervSegments(c,loadX,ly+size+5,lw,4,load,color,16);
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
            line(c,pad,footer-10,w-pad,footer-10,surface(0xFF56313B,55),1);
            if(layout.metric("clock"))metric(c,UiText.get("ЧАСТОТА"),number(value(data,"clock"))+" MHz","",new RectF(pad,footer,layout.metric("power")?w/2-5:w-pad,h-pad-6),white);
            if(layout.metric("power"))metric(c,UiText.get("МОЩНОСТЬ"),number(value(data,"power"))+" W","",new RectF(layout.metric("clock")?w/2+5:pad,footer,w-pad,h-pad-6),white);
            String prefix=series==0?"cpu.":"gpu.";
            if(layout.metric("clock"))trend(c,prefix+"clock",pad,h-pad-4,(layout.metric("power")?w/2-5:w-pad)-pad,5,color,false);
            if(layout.metric("power")){float x=layout.metric("clock")?w/2+5:pad;trend(c,prefix+"power",x,h-pad-4,w-pad-x,5,color,false);}
        }
    }
    private void standaloneHardware(Canvas c,float w,float h,String title,int color,int series){
        float pad=padding(w,h);cardTitle(c,title+" / TELEMETRY",w,h);
        hardwareSection(c,new RectF(pad,pad+25,w-pad,h-pad),title,object(title.toLowerCase(Locale.ROOT)),color,series);
    }
    static String traffic(double bytes){return !Double.isFinite(bytes)||bytes<0?"—":String.format(Locale.US,bytes*8/1000000>=100?"%.0f":"%.2f",bytes*8/1000000);}
    private void trend(Canvas c,String key,float x,float y,float w,float h,int color,boolean scale){
        trend(c,key,x,y,w,h,color,scale,trends.maximum(key));
    }
    private void trend(Canvas c,String key,float x,float y,float w,float h,int color,boolean scale,float maximum){
        if(w<12 || h<3)return;
        long now=android.os.SystemClock.elapsedRealtime();
        line(c,x,y+h,x+w,y+h,tint(color,55),1);
        if(h>=18)line(c,x,y,x+w,y,tint(color,30),1);
        Path path=new Path();boolean started=false;float lastX=0,lastY=0;boolean any=false;
        for(MetricHistory.Point point:trends.points(key)){
            if(now-point.time>60000)continue;
            if(!Float.isFinite(point.value)){started=false;continue;}
            float px=x+w*(1-Math.max(0,now-point.time)/60000f),py=y+h*(1-Math.max(0,Math.min(1,point.value/maximum)));
            if(!started)path.moveTo(px,py);else path.lineTo(px,py);
            started=true;any=true;lastX=px;lastY=py;
        }
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        if(started){p.setColor(color);circle(c,lastX,lastY,2,p);}
        if(!any && h>=14)fit(c,"NO DATA",x+3,y+h-3,8,w-6,muted);
        if(scale && w>=80 && h>=25)fit(c,"0–"+number(maximum)+" / 60 s",x+3,y+9,7,w-6,muted);
    }
    private void networkCard(Canvas c,float w,float h){
        float pad=padding(w,h);cardTitle(c,"06 / NETWORK",w,h);
        JSONObject n=object("network");boolean ready=n!=null&&n.optString("status").equals("ready");
        String state=n==null?sample==null?UiText.get("НЕТ СВЯЗИ С ПК"):UiText.get("ОБНОВИ АГЕНТ ПК"):ready?n.optString("name",UiText.get("СЕТЬ ПК")):n.optString("status").equals("warming")?UiText.get("ИЗМЕРЕНИЕ…"):UiText.get("СЕТЬ НЕДОСТУПНА");
        if(h>=120)fit(c,state,pad,pad+33,10,w-pad*2,muted);
        float top=h>=120?pad+44:pad+23,row=(h-pad-top-12)/2;
        float maximum=Math.max(trends.maximum("network.downloadBps"),trends.maximum("network.uploadBps"));
        String[] fields={"downloadBps","uploadBps"},labels={"↓ DOWNLOAD","↑ UPLOAD"};
        for(int i=0;i<2;i++){
            float y=top+i*row;int color=i==0?accent:secondary;
            if(row>=48){
                fit(c,labels[i],pad,y+9,9,w-pad*2,color);
                float size="network".equals(focusedMetric)?64:22;
                fit(c,traffic(ready?value(n,fields[i]):Double.NaN)+UiText.get(" Мбит/с"),pad,y+size+9,size,w-pad*2,white);
                trend(c,"network."+fields[i],pad,y+size+16,w-pad*2,Math.max(4,row-size-22),color,false,maximum);
            }else{
                fit(c,(i==0?"↓ ":"↑ ")+traffic(ready?value(n,fields[i]):Double.NaN)+UiText.get(" Мбит/с"),pad,y+Math.min(17,row-8),13,w-pad*2,color);
                trend(c,"network."+fields[i],pad,y+row-7,w-pad*2,4,color,false,maximum);
            }
        }
        if(h>=155)fit(c,"60 s · 0–"+traffic(maximum)+UiText.get(" Мбит/с · LAN + INTERNET"),pad,h-pad+3,8,w-pad*2,muted);
    }

    private void gameHardware(Canvas c,float w,float h) {
        float pad=padding(w,h);cardTitle(c,"02 / HARDWARE",w,h);
        boolean columns=w>=420;float top=pad+(h<150?22:31),gap=h<150?6:16,sectionH=columns?h-top-pad:(h-top-pad-gap)/2,sectionW=columns?(w-pad*2-gap)/2:w-pad*2;
        RectF first=new RectF(pad,top,pad+sectionW,top+sectionH);
        registerFocus("gpu",first);
        hardwareSection(c,first,"GPU",object("gpu"),secondary,1);
        RectF second=columns?new RectF(pad+sectionW+gap,top,w-pad,h-pad):new RectF(pad,top+sectionH+gap,w-pad,h-pad);
        if(!columns)line(c,pad,second.top-gap/2,w-pad,second.top-gap/2,surface(0xFF56313B,55),1);
        registerFocus("cpu",second);
        hardwareSection(c,second,"CPU",object("cpu"),accent,0);
    }
    private void hardwareSection(Canvas c,RectF r,String title,JSONObject data,int color,int series) {
        float temperature=value(data,"temperature"),load=value(data,"load");
        boolean showTemperature=layout.metric("temperature"),showLoad=layout.metric("load");
        String prefix=series==0?"cpu":"gpu";
        int thermal=Float.isFinite(temperature)&&temperature>=(series==0?85:83)?ALERT:color;
        if(r.height()<125){
            // Short user-sized cards still identify both readings explicitly.
            float row=r.height()/Math.max(1,(showTemperature?1:0)+(showLoad?1:0));int index=0;
            if(showTemperature){
                float y=r.top+index++*row;
                fit(c,UiText.get("ТЕМПЕРАТУРА")+" "+number(temperature)+"°C",r.left,y+Math.min(22,row-8),12,r.width(),thermal);
                trend(c,prefix+".temperature",r.left,y+row-7,r.width(),4,thermal,false);
            }
            if(showLoad){
                float y=r.top+index*row;
                fit(c,UiText.get("ЗАГРУЗКА")+" "+number(load)+"%",r.left,y+Math.min(22,row-8),12,r.width(),color);
                nervSegments(c,r.left,y+row-8,r.width(),5,load,color,16);
            }
            return;
        }
        if(showTemperature){
            float size=bounded(Math.min(r.width()*.32f,r.height()*.23f),26,64),baseline=r.top+31+size;
            text(c,UiText.get("ТЕМПЕРАТУРА"),r.left,r.top+16,10,muted,medium);
            valueWithUnit(c,number(temperature),r.left,baseline,size,"°C",bounded(size*.32f,12,22),size*.35f,thermal);
            float plotTop=baseline+8;
            float available=(showLoad?r.top+r.height()*.80f-39:r.bottom-5)-plotTop;
            trend(c,prefix+".temperature",r.left,plotTop,r.width(),Math.max(4,Math.min(30,available)),thermal,false);
        }
        if(showLoad){
            float barY=showTemperature?r.top+r.height()*.80f:r.top+50;
            text(c,UiText.get("ЗАГРУЗКА"),r.left,barY-10,10,muted,medium);
            p.setTextAlign(Paint.Align.RIGHT);fit(c,number(load)+"%",r.right,barY-8,26,r.width()*.48f,white);p.setTextAlign(Paint.Align.LEFT);
            nervSegments(c,r.left,barY,r.width(),14,load,color,18);
            if(r.bottom-barY>38)loadSpark(c,r.left,barY+23,r.width(),r.bottom-barY-24,series+2,color);
        }
    }
    private void cooling(Canvas c,float w,float h) {
        float pad=padding(w,h);cardTitle(c,"04 / COOLING",w,h);JSONObject cpu=object("cpu"),gpu=object("gpu");
        boolean profile=cpu!=null&&cpu.optBoolean("coolingProfile");
        String[] keys={"fan","sideFan","pump","gpuFan"},labels={profile?UiText.get("ВЕРХ / AIO"):"CPU / AIO",UiText.get("БОК / ВДУВ"),UiText.get("ПОМПА"),"GPU / FAN"},sources={profile?"Fan #1":sensorName(cpu,"fanName"),profile?"Fan #2":UiText.get("КОРПУС"),profile?"Fan #7":sensorName(cpu,"pumpName"),UiText.get("ВИДЕОКАРТА")};
        java.util.ArrayList<Integer> rows=new java.util.ArrayList<>();for(int i=0;i<4;i++)if(layout.metric(keys[i]))rows.add(i);
        boolean columns=w>=400;int count=rows.size(),rowCount=columns?(count+1)/2:count;
        float top=pad+29,rh=(h-top-pad)/Math.max(1,rowCount),cw=columns?(w-pad*3)/2:w-pad*2;
        for(int n=0;n<count;n++) {
            int index=rows.get(n);float x=pad+(columns?n%2*(cw+pad):0),y=top+(columns?n/2:n)*rh;
            float rpm=value(index==3?gpu:cpu,keys[index].equals("gpuFan")?"fan":keys[index]);int color=index==3?secondary:accent;
            String trendKey=index==3?"gpu.fan":"cpu."+keys[index];
            boolean calibrated=index==3?gpu!=null&&gpu.optString("name").contains("5070 Ti"):profile;
            float percent=calibrated?FanCalibration.percent(keys[index],rpm):Float.NaN;
            boolean hasCurve=calibrated;
            String percentLabel=index==2&&Float.isFinite(rpm)&&rpm>0&&rpm<2700?"≤75%":"≈"+number(percent)+"%";
            if(rh<65){
                fit(c,labels[index]+"  "+number(rpm)+" RPM"+(hasCurve?" / "+percentLabel:""),x,y+Math.min(22,rh-10),bounded(rh*.45f,9,14),cw,color);
                if(hasCurve)nervSegments(c,x,y+rh-10,cw,6,percent,color,20);
                else trend(c,trendKey,x,y+rh-8,cw,4,color,false);
                continue;
            }
            fit(c,labels[index],x,y+12,11,cw,color);
            float baseline=y+bounded(rh*.56f,35,65);
            // Keep the estimated percentage separate from the measured RPM.
            fit(c,number(rpm)+" RPM",x,baseline,bounded(Math.min(cw*.14f,rh*.30f),17,48),hasCurve?cw*.68f:cw,white);
            if(hasCurve){
                p.setTextAlign(Paint.Align.RIGHT);fit(c,percentLabel,x+cw,baseline,rh>140?30:18,cw*.30f,color);p.setTextAlign(Paint.Align.LEFT);
                nervSegments(c,x,baseline+9,cw,Math.max(5,Math.min(18,y+rh-10-(baseline+9))),percent,color,24);
                if(rh>140){trend(c,trendKey,x,baseline+40,cw,rh-110,color,true);}
            }else{
                float plotTop=baseline+7;trend(c,trendKey,x,plotTop,cw,y+rh-9-plotTop,color,true);
            }
            if(n<count-1)line(c,x,y+rh-4,x+cw,y+rh-4,surface(0xFF56313B,55),1);
        }
    }
    static String low01Status(JSONObject game) {
        if(game==null)return UiText.get("НЕТ ДАННЫХ");
        if(game.optBoolean("paused"))return UiText.get("ПАУЗА");
        if(!game.has("low01"))return UiText.get("ОБНОВИ АГЕНТ ПК");
        if(!Float.isNaN(value(game,"low01")))return "FPS";
        if(game.has("low01Samples")) {
            int samples=game.optInt("low01Samples");double seconds=game.optDouble("low01Seconds",0);
            if(samples<1000)return samples+UiText.get(" / 1000 КАДРОВ");
            return Math.min(10,(int)seconds)+UiText.get(" / 10 СЕКУНД");
        }
        return UiText.get("НАКОПЛЕНИЕ КАДРОВ");
    }
    private String sensorName(JSONObject data,String key) {
        return data==null || data.isNull(key) ? UiText.get("ДАТЧИК НЕДОСТУПЕН") : data.optString(key,UiText.get("ДАТЧИК НЕДОСТУПЕН"));
    }
    private void coolingRow(Canvas c,float x,float y,String title,float rpm,String source,int color) {
        text(c,title,x+22,y,12,color,medium);
        valueWithUnit(c,number(rpm),x+22,y+46,39,"RPM",12,2,muted);
        fit(c,source,x+22,y+67,10,176,muted);
        line(c,x+22,y+86,x+198,y+86,surface(0xFF263446,35),1);
    }
    private void valueWithUnit(Canvas c,String value,float x,float baseline,float size,String unit,float unitSize,float rise,int color) {
        text(c,value,x,baseline,size,white,digits);
        float unitX=x+(p.measureText(value)+10)/textRatio();
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
        boolean wide=w>=400 && h>=160;float mainW=wide?w*.40f:w-pad*2,body=pad+24;
        float size=bounded(Math.min(mainW*.30f,h*.24f),24,64);
        fit(c,number(load)+"%",pad,body+size,size,mainW,color);
        fit(c,decimal(used)+" / "+number(total)+" GB",pad,Math.min(body+size+19,h-pad),Math.min(18,w*.06f),mainW,muted);
        float barY=body+size+30;
        if(barY+14<h-pad){if(nerv)nervSegments(c,pad,barY,mainW,18,load,color,24);else for(int i=0;i<24;i++)box(c,pad+i*mainW/24,barY,mainW/24-2,18,Float.isFinite(load)&&i<Math.ceil(load/100*24)?color:surface(0xFF263446,35));}
        if(barY+14>=h-pad)nervSegments(c,pad,h-pad+4,mainW,4,load,color,24);
        RectF spark=wide?new RectF(w*.47f,body+10,w-pad,h-pad-16):new RectF(pad,barY+25,w-pad,h-pad-12);
        if(spark.height()>=16){loadSpark(c,spark.left,spark.top,spark.width(),spark.height(),4,color);text(c,UiText.get("MEMORY LOAD / 60 С"),spark.left,h-pad+2,9,muted,medium);}
    }
    private void chartGrid(Canvas c,RectF plot,float maximum,boolean percent) {
        int rows=fullMagi() && plot.height()>95?4:2;
        if(nerv)for(int i=0;i<=10;i++){
            float x=plot.left+plot.width()*i/10;line(c,x,plot.top,x,plot.bottom,tint(accent,fullMagi()?(i%5==0?75:30):16),1);
            if(fullMagi())line(c,x,plot.bottom,x,plot.bottom+4,accent,1);
        }
        for(int i=0;i<=rows;i++){
            float y=plot.top+plot.height()*i/rows;line(c,plot.left,y,plot.right,y,fullMagi()?tint(accent,70):surface(0xFF56313B,40),1);
            text(c,number(maximum*(1-i/(float)rows))+(percent?"%":""),plot.right+5,y+4,9,muted,regular);
            if(fullMagi())line(c,plot.left-4,y,plot.left,y,accent,2);
        }
        if(fullMagi()){line(c,plot.left,plot.top,plot.left,plot.bottom,accent,1);line(c,plot.left,plot.bottom,plot.right,plot.bottom,accent,1);}
    }
    private void chartTimes(Canvas c,RectF plot,float baseline,int seconds) {
        if(fullMagi() && plot.width()>450)for(int i:new int[]{1,3}){
            p.setTextAlign(Paint.Align.CENTER);text(c,"−"+seconds*(4-i)/4+UiText.get(" с"),plot.left+plot.width()*i/4,baseline,9,accent,regular);p.setTextAlign(Paint.Align.LEFT);
        }
        text(c,"−"+seconds+UiText.get(" с"),plot.left,baseline,9,muted,regular);
        if(plot.width()>260)text(c,"−"+seconds/2+UiText.get(" с"),plot.centerX()-18,baseline,9,muted,regular);
        p.setTextAlign(Paint.Align.RIGHT);text(c,UiText.get("СЕЙЧАС"),plot.right,baseline,9,muted,regular);p.setTextAlign(Paint.Align.LEFT);
    }
    private void graph(Canvas c,float w,float h) {
        float pad=padding(w,h);cardTitle(c,nerv?"MAGI // RESOURCE LOAD":"THERMAL HISTORY",w,h);
        RectF plot=new RectF(pad,pad+30,w-pad-30,h-pad-15);if(plot.height()<20){fit(c,"CPU "+number(value(object("cpu"),nerv?"load":"temperature"))+(nerv?"%":"°C")+" / GPU "+number(value(object("gpu"),nerv?"load":"temperature"))+(nerv?"%":"°C"),pad,h-pad,14,w-pad*2,white);return;}
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
        if(w>320){text(c,"CPU",w-pad-100,pad+12,10,fullMagi()?0xFF100D14:accent,medium);text(c,"GPU",w-pad-49,pad+12,10,fullMagi()?0xFF100D14:secondary,medium);}
        chartTimes(c,plot,h-pad,180);
    }
    private void nervSegments(Canvas c,float left,float top,float width,float height,float load,int color,int count) {
        float step=width/count;
        for(int i=0;i<count;i++) {
            p.setColor(Float.isFinite(load)&&i<Math.ceil(Math.max(0,Math.min(100,load))/100*count)?(load>=90?ALERT:color):surface(0xFF38222A,24));
            float x=left+i*step,slant=Math.min(3,step/3);Path segment=new Path();
            segment.moveTo(x+slant,top);segment.lineTo(x+step-1,top);segment.lineTo(x+step-1-slant,top+height);segment.lineTo(x,top+height);segment.close();c.drawPath(segment,p);
        }
    }
    private void loadSpark(Canvas c,float left,float top,float width,float height,int series,int color) {
        if(fullMagi() && height>=36){
            for(int i=0;i<3;i++){float y=top+height*i/2;line(c,left,y,left+width,y,tint(color,45),1);text(c,""+(100-i*50),left+3,y+9,7,muted,regular);}
            for(int i=0;i<=4;i++){float x=left+width*i/4;line(c,x,top,x,top+height,tint(color,25),1);}
        }
        Path path=new Path();boolean started=false;long now=android.os.SystemClock.elapsedRealtime();
        for(ThermalPoint point:history) {
            if(now-point.time>60000)continue;float value=point.values[series];if(!Float.isFinite(value)){started=false;continue;}
            float x=left+width*(1-(now-point.time)/60000f),y=top+height*(1-Math.max(0,Math.min(100,value))/100);
            if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
        }
        line(c,left,top+height,left+width,top+height,surface(0xFF56313B,55),1);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private void drawMedia(Canvas c) {
        JSONObject media=media();if(media==null)return;
        box(c,36,589,1128,115,panel);line(c,36,589,1164,589,accent,2);
        RectF thumb=new RectF(52,605,187,689);
        if(mediaThumbnail!=null) {
            float factor=Math.min(thumb.width()*aspectRatio()/mediaThumbnail.getWidth(),thumb.height()/mediaThumbnail.getHeight());
            float w=mediaThumbnail.getWidth()*factor/aspectRatio(),h=mediaThumbnail.getHeight()*factor;
            p.setColor(white);c.drawBitmap(mediaThumbnail,null,new RectF(thumb.centerX()-w/2,thumb.centerY()-h/2,thumb.centerX()+w/2,thumb.centerY()+h/2),p);
        }else {box(c,52,605,135,84,surface(0xFF152131,18));text(c,"CHROME",67,653,17,accent,medium);}
        c.save();c.clipRect(204,598,845,651);
        fit(c,media.optString("title",UiText.get("Видео")),204,621,18,630,white);
        fit(c,"CHROME / "+media.optString("artist","")+(mediaTransition?UiText.get(" · СМЕНА ТРЕКА…"):mediaCommandPending?UiText.get(" · КОМАНДА…"):media.optBoolean("playing")?"":UiText.get(" · ПАУЗА")),204,645,12,630,muted);c.restore();
        double duration=media.optDouble("duration",0),position=media.optDouble("position",0);
        if(mediaTransition)position=transitionPosition;
        else if(media.optBoolean("playing"))position+=(android.os.SystemClock.elapsedRealtime()-mediaReceived)/1000.0;
        if(duration>0)position=Math.min(position,duration);
        line(c,204,673,840,673,surface(0xFF263446,35),5);
        if(duration>0){float x=204+(float)Math.max(0,Math.min(1,position/duration))*636;line(c,204,673,x,673,accent,5);p.setColor(accent);circle(c,x,673,4,p);}
        text(c,mediaTime(position)+" / "+(duration>0?mediaTime(duration):"LIVE"),204,694,9,muted,regular);
        mediaButton(c,mediaPrevious,"|◀",media.optBoolean("canPrevious")&&!mediaTransition&&!mediaCommandPending);
        mediaButton(c,mediaToggle,media.optBoolean("playing")?"Ⅱ":"▶",media.optBoolean("canToggle")&&!mediaTransition&&!mediaCommandPending);
        mediaButton(c,mediaNext,"▶|",media.optBoolean("canNext")&&!mediaTransition&&!mediaCommandPending);
    }
    private void mediaButton(Canvas c,RectF rect,String title,boolean enabled) {
        boolean pressed=fingerDown&&rect.equals(pressedRect);
        box(c,rect.left+(pressed?2:0),rect.top+(pressed?2:0),rect.width()-(pressed?4:0),rect.height()-(pressed?4:0),pressed?surface(0xFF40536A,80):enabled?surface(0xFF263446,35):surface(0xFF152131,18));
        p.setTextAlign(Paint.Align.CENTER);text(c,title,rect.centerX(),rect.centerY()+(pressed?12:10),29,enabled?accent:muted,medium);p.setTextAlign(Paint.Align.LEFT);
    }
    static String mediaTime(double seconds){int time=(int)Math.max(0,seconds);return time>=3600?String.format(Locale.US,"%d:%02d:%02d",time/3600,time/60%60,time%60):String.format(Locale.US,"%d:%02d",time/60,time%60);}
    private void drawBlock(Canvas c,String key,boolean game) {
        if(!layout.visible(key,game))return;
        RectF target=layout.rect(key,game);
        if(mediaVisible()){target.top=125+(target.top-125)*.79f;target.bottom=125+(target.bottom-125)*.79f;}
        drawnCards.put(key,target);
        c.save();c.clipRect(target);c.translate(target.left,target.top);
        float ratio=aspectRatio();c.scale(1/ratio,1);localCoordinates=true;
        drawingBlock=key;
        float w=target.width()*ratio,h=target.height();
        if(animations&&!editing&&!boot.active){
            long elapsed=android.os.SystemClock.elapsedRealtime()-screenTransition;
            int order=key.equals("main")||key.equals("cpu")?0:key.equals("audio")||key.equals("gpu")?1:key.equals("memory")||key.equals("side")?2:3;
            float t=Math.max(0,Math.min(1,(elapsed-order*35f)/palette.transitionMillis()));
            if(t<1){float eased=1-(float)Math.pow(1-t,3);c.clipRect(0,0,w,h*eased);postInvalidateOnAnimation();}
        }
        box(c,0,0,w,h,panel);panelFrame(c,w,h);
        if(!editing && (key.equals("cpu")||key.equals("gpu")||key.equals("main")||key.equals("memory")||key.equals("history")||key.equals("side")||key.equals("network")))focusTargets.put(key,new RectF(target));
        switch(key) {
            case "main":gamePanel(c,w,h);break;
            case "audio":audioCard(c,w,h);break;
            case "cpu":if(game)standaloneHardware(c,w,h,"CPU",accent,0);else device(c,w,h,"CPU",object("cpu"),accent,0);break;
            case "gpu":if(game)standaloneHardware(c,w,h,"GPU",secondary,1);else device(c,w,h,"GPU",object("gpu"),secondary,1);break;
            case "memory":memory(c,w,h);break;
            case "history":if(game)frameGraph(c,w,h);else graph(c,w,h);break;
            case "side":cooling(c,w,h);break;
            case "network":networkCard(c,w,h);break;
        }
        drawingBlock="";localCoordinates=false;c.restore();
    }
    private void drawEditorHeader(Canvas c,boolean game) {
        button(c,modeHit,UiText.get("РЕДАКТОР / ")+(game?UiText.get("ИГРА"):UiText.get("ОБЗОР")));
        button(c,new RectF(655,20,820,86),UiText.get("БЛОКИ / ДАННЫЕ"));
        button(c,themeHit,UiText.get("СОХРАНИТЬ"));button(c,settingsHit,UiText.get("ОТМЕНА"));
    }
    private void drawEditor(Canvas c,boolean game) {
        for(Map.Entry<String,RectF> entry:drawnCards.entrySet()) {
            RectF r=entry.getValue();p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(entry.getKey().equals(selection)?3:1);
            p.setColor(layout.valid(game)?accent:ALERT);c.drawRect(r,p);p.setStyle(Paint.Style.FILL);
            c.drawRect(r.right-22,r.bottom-22,r.right,r.bottom,p);
        }
        text(c,UiText.get("Перетаскивай блоки · тяни нижний правый угол · красная рамка — пересечение"),36,737,13,muted,medium);
    }
    private void finishAnimation(Canvas c,String key) {
        long now=android.os.SystemClock.elapsedRealtime();
        if(!key.equals(transitionKey)){transitionKey=key;transitionStart=now;}
        if(!fullMagi())return;
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<80;i++) {
            float x=36+i*18; if(x>1164)break;Path stripe=new Path();stripe.moveTo(x,716);stripe.lineTo(x+7,716);stripe.lineTo(x+14,721);stripe.lineTo(x+7,721);stripe.close();p.setColor(secondary);c.drawPath(stripe,p);
        }
        long elapsed=now-screenTransition;
        if(animations&&elapsed>=0&&elapsed<palette.transitionMillis()+105&&!editing){
            float t=Math.min(1,elapsed/(float)(palette.transitionMillis()+105));
            line(c,36,117,36+1128*t,117,secondary,2);postInvalidateOnAnimation();
        }
    }
    private void drawStandby(Canvas c) {
        button(c,clockBack,UiText.get("ПАНЕЛЬ"));button(c,settingsHit,UiText.get("НАСТРОЙКИ"));
        java.time.ZonedDateTime clockNow=java.time.ZonedDateTime.now();
        text(c,nerv?"NERV // STANDBY":"LIBRE // STANDBY",60,180,16,accent,medium);
        text(c,connected()?"EXTERNAL LINK / ONLINE":"EXTERNAL LINK / OFFLINE",60,214,12,connected()?accent:muted,medium);
        text(c,clockNow.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),52,390,164,white,digits);
        text(c,clockNow.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM",UiText.locale())),60,455,25,muted,regular);
        String city=weather==null?weatherCity:weather.optString("city","Санкт-Петербург");
        fit(c,UiText.get(city),730,175,25,410,white);
        if(weather==null){fit(c,weatherStatus,730,255,22,410,muted);fit(c,weatherHint,730,305,13,410,muted);}
        else {
            valueWithUnit(c,number(value(weather,"temperature")),730,250,64,"°C",24,22,accent);
            fit(c,WeatherClient.summary(weather),730,290,20,410,muted);
            temperatureGlyph(c,1105,205,value(weather,"temperature"),accent);
            fit(c,"−20 / +40 °C",1060,265,8,82,muted);
            long age=System.currentTimeMillis()-weather.optLong("fetched",0);
            String source=weather.optString("source","Open-Meteo");
            fit(c,(age>900000?UiText.get("Сохранено · "):"")+source+UiText.get(" · нажми для обновления"),730,319,11,410,muted);
        }
        drawForecast(c);
        line(c,730,543,1142,543,surface(0xFF263446,35),1);
        text(c,UiText.get("БУДИЛЬНИК  +"),730,575,13,accent,medium);fit(c,nextAlarm,730,615,23,410,white);
        drawStandbyLink(c);
        if(connected())return;
        boolean waking=wakePending();long now=android.os.SystemClock.elapsedRealtime();
        int powerColor=wakeError.isEmpty()?accent:ALERT;
        box(c,32,556,572,143,powerColor);
        box(c,clockWake.left,clockWake.top,clockWake.width(),clockWake.height(),waking?panel:powerColor);
        int ink=waking?white:0xFF09121C;
        p.setColor(waking?accent:ink);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);
        c.save();c.translate(90,627);c.scale(1/aspectRatio(),1);c.translate(-90,-627);
        float phase=animations?(now%1500)/1500f*360:0;
        c.drawArc(new RectF(65,602,115,652),waking?phase:-55,waking?245:290,false,p);
        if(!waking)c.drawLine(90,590,90,624,p);c.restore();
        p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);
        fit(c,waking?UiText.get("ЗАПУСК ПК"):wakeError.isEmpty()?UiText.get("ВКЛЮЧИТЬ ПК"):UiText.get("ПОВТОРИТЬ ЗАПУСК"),145,610,28,425,ink);
        if(waking){
            long seconds=Math.max(0,(wakeDeadline-now+999)/1000);
            fit(c,UiText.get("ЖДУ ПОДКЛЮЧЕНИЯ…")+" "+seconds+UiText.get(" с"),145,641,14,420,ink);
            float progress=Math.max(0,Math.min(1,(now-wakeStarted)/60000f));
            line(c,145,669,570,669,tint(accent,55),4);line(c,145,669,145+425*progress,669,accent,4);
            if(animations)postInvalidateOnAnimation();else postInvalidateDelayed(250);
        }else{
            fit(c,wakeError.isEmpty()?UiText.get("ВКЛЮЧИТЬ / РАЗБУДИТЬ · WAKE-ON-LAN"):wakeError,145,645,12,420,ink);
        }

    }
    private void drawStandbyLink(Canvas c){
        int color=connected()?accent:muted;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(color);c.drawRect(60,488,157,525,p);c.drawRect(451,488,548,525,p);p.setStyle(Paint.Style.FILL);
        text(c,"TABLET",74,512,13,accent,medium);text(c,"PC",485,512,13,color,medium);
        for(int i=0;i<14;i++)line(c,168+i*19,506,178+i*19,506,tint(color,100),2);
        if(animations){float x=168+(android.os.SystemClock.elapsedRealtime()%5000)/5000f*260;line(c,x,506,x+14,506,accent,3);postInvalidateDelayed(50);}
        text(c,connected()?"SIGNAL VERIFIED":"WAITING FOR UPLINK",190,540,9,color,medium);
    }
    private void drawAtmosphereNotice(Canvas c){
        if(editing||boot.active||android.os.SystemClock.elapsedRealtime()>=atmosphere.noticeUntil)return;
        int color=atmosphere.notice.contains("THERMAL LIMIT")||atmosphere.notice.startsWith("SIGNAL LOST")?ALERT:secondary;
        p.setColor(panel);c.drawRect(36,83,1164,109,p);line(c,36,83,36,109,color,4);
        fit(c,atmosphere.notice,49,101,14,1098,color);
        postInvalidateDelayed(Math.max(1,atmosphere.noticeUntil-android.os.SystemClock.elapsedRealtime()));
    }
    private void drawMagiTerminal(Canvas c){
        p.setColor(palette.background);c.drawRect(24,0,1176,760,p);
        text(c,"MAGI / CENTRAL DIAGNOSTICS",44,65,27,accent,medium);
        fit(c,UiText.get("ТАП ИЛИ BACK — НАЗАД"),44,99,12,1100,muted);
        String[] names={"MELCHIOR : 1","BALTHASAR : 2","CASPER : 3"};
        JSONObject audio=audio(),mic=audio==null?null:audio.optJSONObject("microphone");
        boolean sensors=sample!=null&&sample.isNull("sensorError")&&(object("cpu")!=null||object("gpu")!=null),soundReady=audio!=null&&audio.optBoolean("ready"),micReady=soundReady&&mic!=null&&mic.optBoolean("available");
        for(int i=0;i<3;i++){
            float x=44+i*375;int color=i==0?connected()?accent:ALERT:i==1?sensors?accent:ALERT:soundReady?accent:muted;
            box(c,x,165,350,410,panel);line(c,x+14,166,x+336,166,color,3);
            fit(c,names[i],x+18,205,23,314,color);
            fit(c,i==0?"TELEMETRY LINK":i==1?"HARDWARE SENSORS":"AUDIO CONTROL",x+18,247,14,314,muted);
            fit(c,i==0?connected()?"ONLINE":"SIGNAL LOST":i==1?sample==null||(object("cpu")==null&&object("gpu")==null)?"NO DATA":sensors?"ONLINE":"SENSOR ERROR":soundReady?"READY":"UNAVAILABLE",x+18,302,25,314,color);
            if(i==0){fit(c,sample==null?"PC / OFFLINE":sample.optString("host","PC"),x+18,363,16,314,white);fit(c,receivedSample?"PACKET AGE / "+Math.max(0,(android.os.SystemClock.elapsedRealtime()-lastGood)/1000)+" s":"NO PACKET",x+18,402,13,314,muted);}
            if(i==1){fit(c,"CPU / "+number(value(object("cpu"),"temperature"))+" °C",x+18,363,18,314,hot("cpu")?ALERT:white);fit(c,"GPU / "+number(value(object("gpu"),"temperature"))+" °C",x+18,402,18,314,hot("gpu")?ALERT:white);}
            if(i==2){fit(c,!micReady?"MIC / UNAVAILABLE":mic.optBoolean("muted")?"MIC / MUTED":"MIC / ON",x+18,363,18,314,micReady&&mic.optBoolean("muted")?ALERT:white);fit(c,soundReady?"MIXER / ONLINE":"MIXER / OFFLINE",x+18,402,13,314,muted);}
            fit(c,"LIVE STATUS / NODE 0"+(i+1),x+18,545,11,314,color);
        }
        text(c,"NERV // "+palette.signature(),44,650,15,secondary,medium);
        text(c,"SOURCE / CURRENT TELEMETRY",44,700,11,muted,medium);
    }
    private void drawForecast(Canvas c) {
        java.time.Instant instant=java.time.Instant.now();
        java.time.LocalDate today=instant.atZone(WeatherClient.forecastZone(weather)).toLocalDate();
        text(c,UiText.get("БЛИЖАЙШИЙ ПРОГНОЗ"),730,356,13,accent,medium);
        JSONArray slots=WeatherClient.upcoming(weather,instant);
        for(int n=0;n<3;n++) {
            JSONObject slot=slots.optJSONObject(n);int hour=slot==null?9:slot.optInt("hour",9);
            String label=hour==9?UiText.get("УТРО"):hour==15?UiText.get("ДЕНЬ"):UiText.get("ВЕЧЕР");
            float x=730+n*141;box(c,x,370,130,154,panel);
            text(c,label+" · "+hour+":00",x+9,389,10,accent,medium);
            text(c,slot==null?"":WeatherClient.dayLabel(slot.optString("date"),today),x+9,407,9,muted,regular);
            if(slot==null || !slot.has("temperature")){text(c,"—",x+10,441,26,white,digits);text(c,UiText.get("Нет прогноза"),x+9,460,11,muted,regular);continue;}
            text(c,number(value(slot,"temperature"))+"°",x+10,441,27,white,digits);
            String summary=WeatherClient.summary(slot);
            temperatureGlyph(c,x+108,410,value(slot,"temperature"),accent);
            if(summary.length()>19)summary=summary.substring(0,18)+"…";
            fit(c,summary,x+9,457,12,112,muted);
            float probability=value(slot,"probability");
            text(c,UiText.get("Осадки ")+(Float.isNaN(probability)?"—":number(probability)+"%"),x+9,483,12,accent,regular);
            line(c,x+9,488,x+121,488,tint(accent,45),3);
            if(Float.isFinite(probability))line(c,x+9,488,x+9+112*Math.max(0,Math.min(1,probability/100)),488,accent,3);
            float precipitation=value(slot,"precipitation");
            text(c,(Float.isNaN(precipitation)?"—":decimal(precipitation))+UiText.get(" мм/")+(slot.optInt("precipitationHours",1)==3?UiText.get("3ч"):UiText.get("ч")),x+9,507,11,muted,regular);
        }
    }
    private void temperatureGlyph(Canvas c,float x,float y,float temperature,int color){
        // Weather thermometer: -20..40 C range, labeled on the current weather card.
        line(c,x,y,x,y+32,tint(color,55),5);
        if(Float.isFinite(temperature)){float fraction=Math.max(0,Math.min(1,(temperature+20)/60));line(c,x,y+32,x,y+32-32*fraction,color,5);}
        p.setColor(color);circle(c,x,y+34,5,p);
    }
    private RectF designRect(RectF r){return new RectF(r.left/aspectRatio(),r.top,r.right/aspectRatio(),r.bottom);}
    private void registerFocus(String key,RectF local){
        if(editing)return;RectF origin=drawnCards.get("side");if(origin==null)return;
        RectF r=designRect(local);r.offset(origin.left,origin.top);focusTargets.put(key,r);
    }
    private String focusAt(float x,float y){
        if(editing || standby())return null;
        if(focusedMetric!=null)return focusArea.contains(x,y)?focusedMetric:null;
        for(Map.Entry<String,RectF> entry:focusTargets.entrySet())if(entry.getValue().contains(x,y))return entry.getKey();
        return null;
    }
    private boolean handleFocusTouch(MotionEvent event,float x,float y){
        if(editing || standby()){focusGesture=null;return false;}
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            focusGesture=focusAt(x,y);focusDownX=event.getX();focusDownY=event.getY();
            if(focusGesture!=null){performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);return true;}
        }
        if(focusGesture==null)return false;
        if(action==MotionEvent.ACTION_CANCEL){focusGesture=null;return true;}
        if(action==MotionEvent.ACTION_POINTER_DOWN){focusGesture="cancelled";return true;}
        if(action==MotionEvent.ACTION_MOVE && Math.hypot(event.getX()-focusDownX,event.getY()-focusDownY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()){
            focusGesture="cancelled";return true;
        }
        if(action==MotionEvent.ACTION_UP){
            String target=focusAt(x,y);
            if(focusGesture.equals(target)){focusedMetric=focusedMetric==null?target:null;pressedRect=null;pressedAudio=null;performClick();invalidate();}
            focusGesture=null;return true;
        }
        return true;
    }
    private void drawFocus(Canvas c){
        float ratio=aspectRatio(),w=focusArea.width()*ratio,h=focusArea.height();
        c.save();c.translate(focusArea.left,focusArea.top);c.scale(1/ratio,1);localCoordinates=true;
        drawingBlock=focusedMetric;
        box(c,0,0,w,h,panel);panelFrame(c,w,h);
        String key=focusedMetric;
        cardTitle(c,"FOCUS / "+(key.equals("main")?"FPS":key.equals("side")?"COOLING":key.toUpperCase(Locale.ROOT))+UiText.get(" · ТАП — НАЗАД"),w,h);
        if(key.equals("side")||key.equals("network")){
            c.save();c.translate(0,40);
            if(key.equals("side"))cooling(c,w,h-40);else networkCard(c,w,h-40);
            c.restore();
        }
        else if(key.equals("history")){c.save();c.translate(0,30);if(playing())frameGraph(c,w,h-30);else graph(c,w,h-30);c.restore();}
        else {
            boolean cpu=key.equals("cpu"),gpu=key.equals("gpu"),fps=key.equals("main");
            JSONObject data=object(fps?"game":key.equals("memory")?"memory":key);
            String field=fps?"fps":cpu||gpu?"temperature":"load";
            float value=value(data,field);int color=cpu?accent:gpu?secondary:accent;
            boolean hot=(cpu||gpu)&&Float.isFinite(value)&&value>=(cpu?85:83);
            if(hot)color=ALERT;
            fit(c,data==null?UiText.get("Ожидание данных"):data.optString("name",key.equals("memory")?"RAM":""),24,76,20,w-48,muted);
            centerFit(c,number(value)+(fps?" FPS":cpu||gpu?" °C":" %"),w/2,300,Math.min(180,w*.20f),w-48,color);
            if(hot)centerFit(c,"THERMAL ALERT",w/2,342,18,w-48,ALERT);
            if(fps)fpsSignal(c,new RectF(24,370,w-24,h-24),data);
            else {text(c,cpu||gpu?UiText.get("TEMPERATURE / 60 С"):UiText.get("MEMORY LOAD / 60 С"),24,378,11,color,medium);loadSpark(c,24,398,w-48,h-430,cpu?0:gpu?1:4,color);}
        }
        drawingBlock="";localCoordinates=false;c.restore();
    }
    String statusText(){
        long age=Math.max(0,(android.os.SystemClock.elapsedRealtime()-lastGood)/1000);
        String link=sample==null?"SIGNAL LOST":demo?"DEMO":sample.isNull("sensorError")?"LINK ONLINE":"SENSOR ERROR";
        String ageText=!receivedSample?UiText.get("НЕТ ПАКЕТА"):(demo?UiText.get("ДЕМО-ПАКЕТ "):UiText.get("ПАКЕТ "))+age+UiText.get(" С НАЗАД");
        String state=standby()?"STANDBY":focusedMetric!=null?"FOCUS / "+(focusedMetric.equals("main")?"FPS":focusedMetric.toUpperCase(Locale.ROOT)):playing()?"GAME":"OVERVIEW";
        JSONObject audio=audio(),mic=audio==null?null:audio.optJSONObject("microphone");
        return link+"  /  "+ageText+"  /  "+state+(sample!=null&&mic!=null&&mic.optBoolean("available")&&mic.optBoolean("muted")?"  /  MIC CUT":"");
    }
    private void drawStatus(Canvas c){
        int color=sample==null || !sample.isNull("sensorError")?ALERT:muted;
        if(fullMagi()){
            color=stateColor();p.setColor(color);c.drawRect(36,731,250,756,p);
            fit(c,operationalState(),44,749,12,198,0xFF100D14);
            p.setColor(tint(color,25));c.drawRect(250,731,1164,756,p);
            fit(c,statusText(),262,749,10,890,color);
        }else {
            line(c,36,731,1164,731,tint(color,70),1);
            fit(c,statusText(),36,750,11,1128,color);
        }
    }
    private RectF controlAt(float x,float y) {
        if(editing){for(RectF r:new RectF[]{themeHit,settingsHit,new RectF(655,20,820,86)})if(r.contains(x,y))return r;return null;}
        if(standby()){for(RectF r:new RectF[]{clockBack,settingsHit,clockWake,clockAlarm,clockWeather})if(r.contains(x,y)&& (r!=clockWake||canWake()))return r;return null;}
        AudioControl audio=audioAt(x,y);if(audio!=null)return audio.hit;
        if(mediaVisible() && !mediaTransition && !mediaCommandPending) {
            JSONObject m=media();
            if(mediaPrevious.contains(x,y)&&m.optBoolean("canPrevious"))return mediaPrevious;
            if(mediaToggle.contains(x,y)&&m.optBoolean("canToggle"))return mediaToggle;
            if(mediaNext.contains(x,y)&&m.optBoolean("canNext"))return mediaNext;
            if(mediaSeek.contains(x,y)&&m.optBoolean("canSeek"))return mediaSeek;
        }
        for(RectF r:new RectF[]{menuHit,modeHit,themeHit,settingsHit})if(r.contains(x,y))return r;return null;
    }
    private void drawInteraction(Canvas c) {
        long now=android.os.SystemClock.elapsedRealtime();
        if(pressedRect!=null) {
            float fade=fingerDown?1:Math.max(0,1-(now-releasedAt)/(animations?220f:90f));
            if(fade>0){RectF rect=new RectF(pressedRect);if(rect.top<100){rect.top+=7;rect.bottom=rect.top+40;}
                box(c,rect.left+2,rect.top+2,rect.width()-4,rect.height()-4,((int)(fade*48)<<24)|(accent&0xffffff));
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(((int)(fade*220)<<24)|(accent&0xffffff));c.drawRect(rect.left+2,rect.top+2,rect.right-2,rect.bottom-2,p);p.setStyle(Paint.Style.FILL);
                if(!fingerDown)postInvalidateOnAnimation();
            }else pressedRect=null;
        }
        if(mediaCommandPending && mediaVisible()) {
            float x=877+(animations?(now%1000)/1000f*230:0);line(c,x,699,Math.min(1149,x+40),699,accent,3);
            if(animations)postInvalidateOnAnimation();
        }
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event) {
        if(getWidth()==0 || getHeight()==0)return false;
        if(boot.active){
            if(!wakeError.isEmpty()&&event.getActionMasked()==MotionEvent.ACTION_UP){
                float bx=event.getX()*1200/getWidth(),by=event.getY()*760/getHeight();
                if(by>=635&&by<=711){
                    if(bx>=42&&bx<=582){performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);onWake.run();}
                    else if(bx>=624&&bx<=1158){boot.active=false;forceStandby=true;invalidate();}
                }
            }
            return true;
        }
        float x=event.getX()*VIEWPORT_WIDTH/getWidth()+VIEWPORT_LEFT,y=event.getY()*VIEWPORT_HEIGHT/getHeight();
        if(magiOpen){
            if(event.getActionMasked()==MotionEvent.ACTION_UP){if(suppressMagiRelease)suppressMagiRelease=false;else magiOpen=false;logoArmed=false;removeCallbacks(logoHold);invalidate();}
            return true;
        }
        if(!editing&&event.getActionMasked()==MotionEvent.ACTION_DOWN&&new RectF(36,12,280,88).contains(x,y)){
            logoArmed=true;logoDownX=event.getX();logoDownY=event.getY();postDelayed(logoHold,ViewConfiguration.getLongPressTimeout());return true;
        }
        if(logoArmed){
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.hypot(event.getX()-logoDownX,event.getY()-logoDownY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())removeCallbacks(logoHold);
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){logoArmed=false;removeCallbacks(logoHold);}
            return true;
        }
        if(handleFocusTouch(event,x,y))return true;
        if(!editing && event.getAction()==MotionEvent.ACTION_DOWN){
            pressedAudio=audioAt(x,y);
            if(pressedAudio!=null && pressedAudio.action.equals("volume")){
                audioDrag=pressedAudio;audioPreview=audioDrag.fraction(x,y);pressedRect=audioDrag.hit;fingerDown=true;touchStarted=true;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);invalidate();return true;
            }
        }
        if(audioDrag!=null){
            if(event.getAction()==MotionEvent.ACTION_MOVE){audioPreview=audioDrag.fraction(x,y);invalidate();return true;}
            if(event.getAction()==MotionEvent.ACTION_UP){AudioControl control=audioDrag;double value=control.fraction(x,y);audioDrag=null;audioPreview=Double.NaN;fingerDown=false;touchStarted=false;releasedAt=android.os.SystemClock.elapsedRealtime();onAudio.run(control.key,control.id,"volume",value);invalidate();return true;}
            if(event.getAction()==MotionEvent.ACTION_CANCEL){audioDrag=null;audioPreview=Double.NaN;fingerDown=false;touchStarted=false;pressedRect=null;pressedAudio=null;invalidate();return true;}
        }
        if(event.getAction()==MotionEvent.ACTION_DOWN){touchStarted=true;pressedRect=controlAt(x,y);fingerDown=pressedRect!=null;if(fingerDown)performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);invalidate();}
        if(event.getAction()==MotionEvent.ACTION_CANCEL){fingerDown=false;touchStarted=false;pressedRect=null;pressedAudio=null;invalidate();return true;}
        if(event.getAction()==MotionEvent.ACTION_MOVE && fingerDown && !pressedRect.contains(x,y)){fingerDown=false;pressedRect=null;invalidate();}
        if(event.getAction()==MotionEvent.ACTION_UP){
            boolean cancelled=touchStarted && (pressedRect==null || !pressedRect.contains(x,y));
            fingerDown=false;touchStarted=false;releasedAt=android.os.SystemClock.elapsedRealtime();invalidate();
            if(cancelled && !editing){pressedAudio=null;return true;}
        }
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
            AudioControl audio=pressedAudio!=null?pressedAudio:audioAt(x,y);pressedAudio=null;
            if(!editing && audio!=null && audio.enabled && audio.hit.contains(x,y) && !audioCommandPending){onAudio.run(audio.key,audio.id,audio.action,audio.action.equals("mute")?(audio.muted?0:1):audio.fraction(x,y));return true;}
            if(mediaVisible() && new RectF(36,589,1164,704).contains(x,y)) {
                JSONObject media=media();
                if(mediaTransition || mediaCommandPending)return true;
                if(mediaPrevious.contains(x,y)&&media.optBoolean("canPrevious"))onMedia.run("previous",0);
                else if(mediaToggle.contains(x,y)&&media.optBoolean("canToggle"))onMedia.run("toggle",0);
                else if(mediaNext.contains(x,y)&&media.optBoolean("canNext"))onMedia.run("next",0);
                else if(mediaSeek.contains(x,y)&&media.optBoolean("canSeek"))onMedia.run("seek",Math.max(0,Math.min(1,(x-204)/636)));
                return true;
            }
            if(standby()) {
                if(clockBack.contains(x,y))suppressStandby();else if(settingsHit.contains(x,y))onMenu.run();
                else if(clockAlarm.contains(x,y))onAlarm.run();else if(clockWeather.contains(x,y))onWeather.run();else if(clockWake.contains(x,y)&&canWake())onWake.run();
            } else if(settingsHit.contains(x,y))settings.run();else if(menuHit.contains(x,y))onMenu.run();
            else if(modeHit.contains(x,y))onModeChange.run();else if(themeHit.contains(x,y))theme.run();
            invalidate();
        }
        return true;
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(logoHold);logoArmed=false;super.onDetachedFromWindow();}
    @Override public boolean performClick() { super.performClick();onSound.accept("click");return true; }
}
