package dev.librehw.tab;
import android.graphics.RectF;
import org.json.*;
import java.util.*;

final class DashboardLayout {
    static final String[] OVERVIEW={"cpu","gpu","memory","history","side"};
    static final String[] GAME={"main","memory","history","side"};
    final Map<String,RectF> positions=new HashMap<>();
    final Set<String> hidden=new HashSet<>(),disabledMetrics=new HashSet<>();
    static String[] keys(boolean game){return game?GAME:OVERVIEW;}
    static RectF original(String key,boolean game) {
        switch(key){
            case "cpu":return new RectF(36,125,472,475);
            case "gpu":return new RectF(490,125,926,475);
            case "main":return new RectF(36,125,926,425);
            case "side":return new RectF(944,125,1164,690);
            case "memory":return new RectF(36,game?443:493,306,690);
            default:return new RectF(324,game?443:493,926,690);
        }
    }
    static String id(String key,boolean game){return (game?"game.":"overview.")+key;}
    RectF rect(String key,boolean game){RectF r=positions.get(id(key,game));return new RectF(r==null?original(key,game):r);}
    boolean visible(String key,boolean game){return !hidden.contains(id(key,game)) && (!key.equals("memory")||metric("memory")) && (!key.equals("history")||metric("history"));}
    boolean metric(String key){return !disabledMetrics.contains(key);}
    boolean valid(boolean game){
        List<RectF> visible=new ArrayList<>();
        for(String key:keys(game))if(visible(key,game)){
            RectF r=rect(key,game);
            if(r.width()<160 || r.height()<110 || r.left<36 || r.top<125 || r.right>1164 || r.bottom>710)return false;
            for(RectF other:visible)if(RectF.intersects(r,other))return false;
            visible.add(r);
        }
        return !visible.isEmpty();
    }
    String json(){
        try {
            JSONObject root=new JSONObject(),items=new JSONObject();
            for(Map.Entry<String,RectF> e:positions.entrySet()){RectF r=e.getValue();items.put(e.getKey(),new JSONArray(new double[]{r.left,r.top,r.right,r.bottom}));}
            return root.put("version",1).put("positions",items).put("hidden",new JSONArray(hidden)).put("disabled",new JSONArray(disabledMetrics)).toString();
        }catch(JSONException ex){throw new IllegalStateException(ex);}
    }
    static DashboardLayout parse(String value){
        DashboardLayout result=new DashboardLayout();
        try {
            JSONObject root=new JSONObject(value);if(root.optInt("version")!=1)return result;
            JSONObject items=root.optJSONObject("positions");
            for(boolean game:new boolean[]{false,true})for(String key:keys(game)){
                String id=id(key,game);JSONArray a=items==null?null:items.optJSONArray(id);
                if(a!=null && a.length()==4){float[] v=new float[4];boolean finite=true;for(int i=0;i<4;i++){v[i]=(float)a.getDouble(i);finite&=Float.isFinite(v[i]);}if(finite)result.positions.put(id,new RectF(v[0],v[1],v[2],v[3]));}
            }
            JSONArray hidden=root.optJSONArray("hidden"),disabled=root.optJSONArray("disabled");
            if(hidden!=null)for(int i=0;i<hidden.length();i++)result.hidden.add(hidden.getString(i));
            if(disabled!=null)for(int i=0;i<disabled.length();i++)result.disabledMetrics.add(disabled.getString(i));
            if(!result.valid(false)||!result.valid(true))return new DashboardLayout();
        }catch(Exception ignored){return new DashboardLayout();}
        return result;
    }
}
