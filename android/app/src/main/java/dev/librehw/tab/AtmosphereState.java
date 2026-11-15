package dev.librehw.tab;
import org.json.JSONObject;

/** Edge-triggered presentation state. Missing samples do not imply game exit or cooling. */
final class AtmosphereState {
    boolean seen,linked,game,cpuHot,gpuHot;
    String notice="";
    long noticeUntil;
    int noticePriority;
    private void show(String text,int priority,long now){if(now<noticeUntil&&priority<noticePriority)return;notice=text;noticePriority=priority;noticeUntil=now+2800;}
    String accept(JSONObject sample,long now){
        boolean online=sample!=null;String sound="";
        if(seen&&linked&&!online){show("SIGNAL LOST / TELEMETRY UPLINK",2,now);sound="alert";}
        if(online&&(!seen||!linked))sound="confirm";
        linked=online;seen=true;
        if(!online)return sound;
        JSONObject current=sample.optJSONObject("game");
        boolean active=current!=null&&(current.optBoolean("active")||current.optBoolean("paused"));
        if(active!=game){show(active?"COMBAT TELEMETRY ENGAGED":"OPERATION COMPLETE",1,now);sound="confirm";game=active;}
        for(String key:new String[]{"cpu","gpu"}){
            JSONObject data=sample.optJSONObject(key);double temp=data==null?Double.NaN:data.optDouble("temperature",Double.NaN);
            if(!Double.isFinite(temp))continue;
            boolean previous=key.equals("cpu")?cpuHot:gpuHot;
            boolean hot=previous?temp>(key.equals("cpu")?82:80):temp>=(key.equals("cpu")?85:83);
            if(hot!=previous){show(key.toUpperCase(java.util.Locale.ROOT)+(hot?" / THERMAL LIMIT":" / CONDITION NORMAL"),3,now);sound=hot?"alert":sound.equals("alert")?sound:"confirm";}
            if(key.equals("cpu"))cpuHot=hot;else gpuHot=hot;
        }
        return sound;
    }
}
