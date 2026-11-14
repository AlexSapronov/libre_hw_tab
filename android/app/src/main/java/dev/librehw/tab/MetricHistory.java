package dev.librehw.tab;

import org.json.JSONObject;
import java.util.*;

/** Real 60-second samples. Unknown values and disconnected sources create gaps. */
final class MetricHistory {
    static final class Point { final long time; final float value; Point(long time,float value){this.time=time;this.value=value;} }
    private final Map<String,ArrayDeque<Point>> data=new LinkedHashMap<>();
    private String networkSource="",gameSource="";
    static final String[] KEYS={"cpu.temperature","gpu.temperature","cpu.clock","gpu.clock","cpu.power","gpu.power","cpu.fan","cpu.sideFan","cpu.pump","gpu.fan","network.downloadBps","network.uploadBps","game.fps","game.low1","game.low01","game.frameTime"};
    void clear(){data.clear();networkSource="";gameSource="";}
    void accept(JSONObject sample,long now){
        JSONObject network=sample==null?null:sample.optJSONObject("network"),game=sample==null?null:sample.optJSONObject("game");
        String net=network==null?networkSource:network.optString("id",network.optString("name")),source=game==null?gameSource:game.optString("sourceId",game.optString("name"));
        if(!net.equals(networkSource)){data.keySet().removeIf(k->k.startsWith("network."));networkSource=net;}
        if(!source.equals(gameSource)){data.keySet().removeIf(k->k.startsWith("game."));gameSource=source;}
        for(String key:KEYS){
            String[] parts=key.split("\\.");JSONObject obj=sample==null?null:sample.optJSONObject(parts[0]);
            double value=obj==null?Double.NaN:obj.optDouble(parts[1],Double.NaN);
            if(parts[0].equals("network") && (network==null || !"ready".equals(network.optString("status"))))value=Double.NaN;
            if(parts[0].equals("game") && game!=null && game.optBoolean("paused"))value=Double.NaN;
            ArrayDeque<Point> points=data.computeIfAbsent(key,k->new ArrayDeque<>());
            points.add(new Point(now,Double.isFinite(value)&&value>=0?(float)value:Float.NaN));
            while(!points.isEmpty() && (now-points.peekFirst().time>60000 || points.size()>180))points.removeFirst();
        }
    }
    Iterable<Point> points(String key){return data.getOrDefault(key,new ArrayDeque<>());}
    float maximum(String key){
        float peak=0;for(Point p:points(key))if(Float.isFinite(p.value))peak=Math.max(peak,p.value);
        // Always use a nonzero axis, rounded above the highest observed sample.
        if(peak<=0)return 1;
        double step=Math.pow(10,Math.floor(Math.log10(peak)))/2;
        return (float)(Math.ceil(peak/step)*step);
    }
}
