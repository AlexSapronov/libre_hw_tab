package dev.librehw.tab;
import android.graphics.RectF;
import org.json.*;
import java.util.*;

final class DashboardLayout {
    static final String[] OVERVIEW={"cpu","gpu","memory","history","side","network"};
    static final String[] GAME={"main","audio","memory","history","gpu","cpu"};
    final Map<String,RectF> positions=new HashMap<>();
    final Set<String> hidden=new HashSet<>(),disabledMetrics=new HashSet<>();
    static String[] keys(boolean game){return game?GAME:OVERVIEW;}
    static RectF original(String key,boolean game) {
        switch(key){
            case "cpu":return game?new RectF(944,417,1164,690):new RectF(36,125,472,475);
            case "gpu":return game?new RectF(944,125,1164,399):new RectF(490,125,926,475);
            case "main":return new RectF(36,125,472,475);
            case "audio":return new RectF(490,125,926,475);
            case "side":return new RectF(944,125,1164,475);
            case "network":return new RectF(944,493,1164,690);
            case "memory":return new RectF(36,493,306,690);
            default:return new RectF(324,493,926,690);
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
            return root.put("version",3).put("positions",items).put("hidden",new JSONArray(hidden)).put("disabled",new JSONArray(disabledMetrics)).toString();
        }catch(JSONException ex){throw new IllegalStateException(ex);}
    }
    static DashboardLayout parse(String value){
        DashboardLayout result=new DashboardLayout();
        try {
            JSONObject root=new JSONObject(value);int version=root.optInt("version");if(version!=1 && version!=2 && version!=3)return result;
            JSONObject items=root.optJSONObject("positions");
            for(boolean game:new boolean[]{false,true})for(String key:keys(game)){
                String id=id(key,game);JSONArray a=items==null?null:items.optJSONArray(id);
                if(a!=null && a.length()==4){float[] v=new float[4];boolean finite=true;for(int i=0;i<4;i++){v[i]=(float)a.getDouble(i);finite&=Float.isFinite(v[i]);}if(finite)result.positions.put(id,new RectF(v[0],v[1],v[2],v[3]));}
            }
            JSONArray hidden=root.optJSONArray("hidden"),disabled=root.optJSONArray("disabled");
            if(hidden!=null)for(int i=0;i<hidden.length();i++)result.hidden.add(hidden.getString(i));
            if(disabled!=null)for(int i=0;i<disabled.length();i++)result.disabledMetrics.add(disabled.getString(i));
            if(version<3){
                for(boolean game:new boolean[]{false,true}){
                    RectF old=new RectF(944,125,1164,690);
                    JSONArray a=items==null?null:items.optJSONArray(id("side",game));
                    if(a!=null && a.length()==4)old=new RectF((float)a.getDouble(0),(float)a.getDouble(1),(float)a.getDouble(2),(float)a.getDouble(3));
                    if(!Float.isFinite(old.left)||!Float.isFinite(old.top)||!Float.isFinite(old.right)||!Float.isFinite(old.bottom))return new DashboardLayout();
                    String first=game?"gpu":"side",second=game?"cpu":"network";
                    boolean wasHidden=result.hidden.contains(id("side",game));
                    RectF one=new RectF(old),two=new RectF(old);
                    if(old.height()>=238){float split=game?old.centerY()-9:Math.max(old.top+110,Math.min(old.bottom-128,old.top+(old.height()-18)*.64f));one.bottom=split;two.top=split+18;}
                    else if(old.width()>=338){one.right=old.centerX()-9;two.left=old.centerX()+9;}
                    else {two=original(second,game);result.hidden.add(id(second,game));}
                    result.positions.put(id(first,game),one);result.positions.put(id(second,game),two);
                    if(wasHidden){result.hidden.add(id(first,game));result.hidden.add(id(second,game));}
                }
                // Version 1 may also contain the obsolete full-width FPS card.
                if(version==1 && !result.valid(true))for(String key:GAME)result.positions.remove(id(key,true));
            }
            if(!result.valid(false)||!result.valid(true))return new DashboardLayout();
        }catch(Exception ignored){return new DashboardLayout();}
        return result;
    }
}
