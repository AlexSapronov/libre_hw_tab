package dev.librehw.tab;
import java.net.*;
import java.io.*;
import org.json.*;

final class WeatherClient {
    static JSONObject request(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestProperty("User-Agent","LibreHwTab/1.6.1");c.setRequestProperty("Accept","application/json");
        c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);
        try {
            int status=c.getResponseCode();if(status!=200)throw new IOException("HTTP "+status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>131072)throw new IOException("Слишком большой ответ");out.write(b,0,n);}
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{c.disconnect();}
    }
    static JSONArray cities(String query) throws Exception {
        JSONObject data=request("https://geocoding-api.open-meteo.com/v1/search?count=5&language=ru&name="+URLEncoder.encode(query,"UTF-8"));
        JSONArray result=data.optJSONArray("results");return result==null?new JSONArray():result;
    }
    interface Transport { JSONObject get(String url) throws Exception; }
    static JSONObject fetch(String city,double latitude,double longitude) throws Exception {
        return fetch(city,latitude,longitude,WeatherClient::request);
    }
    static JSONObject fetch(String city,double latitude,double longitude,Transport transport) throws Exception {
        Exception primary;
        try {
            JSONObject current=transport.get("https://api.open-meteo.com/v1/forecast?latitude="+latitude+"&longitude="+longitude+"&current=temperature_2m,weather_code&timezone=auto").getJSONObject("current");
            int code=current.getInt("weather_code");
            return result(city,current.getDouble("temperature_2m"),description(code),"Open-Meteo").put("code",code);
        }catch(Exception ex){primary=ex;}
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        try {
            JSONObject data=transport.get("https://wttr.in/"+latitude+","+longitude+"?format=j1&lang=ru");
            if(data.optJSONObject("data")!=null)data=data.getJSONObject("data");
            JSONObject current=data.getJSONArray("current_condition").getJSONObject(0);
            JSONArray descriptions=current.optJSONArray("lang_ru");
            if(descriptions==null||descriptions.length()==0)descriptions=current.optJSONArray("weatherDesc");
            String summary=descriptions==null||descriptions.length()==0?"Текущая погода":descriptions.getJSONObject(0).optString("value","Текущая погода");
            return result(city,current.getDouble("temp_C"),summary,"wttr.in");
        }catch(Exception fallback){throw new IOException("Open-Meteo: "+reason(primary)+"\nwttr.in: "+reason(fallback));}
    }
    private static JSONObject result(String city,double temperature,String summary,String source) throws Exception {
        if(!Double.isFinite(temperature)||temperature< -100||temperature>70)throw new IOException("Некорректная температура");
        return new JSONObject().put("city",city).put("temperature",temperature).put("summary",summary.substring(0,Math.min(100,summary.length()))).put("source",source).put("fetched",System.currentTimeMillis());
    }
    static String reason(Exception ex) {
        if(ex instanceof UnknownHostException)return "не удалось найти сервер (DNS)";
        if(ex instanceof SocketTimeoutException)return "сервер не ответил вовремя";
        if(ex instanceof javax.net.ssl.SSLException)return "ошибка защищённого соединения (TLS)";
        if(ex instanceof JSONException)return "неожиданный ответ сервиса";
        String message=ex.getMessage();return message==null?"ошибка сети":message.substring(0,Math.min(240,message.length()));
    }
    static String description(int code) {
        if(code==0)return "Ясно";if(code>=1&&code<=3)return "Облачно";if(code==45||code==48)return "Туман";
        if(code>=51&&code<=57)return "Морось";if(code>=61&&code<=67)return "Дождь";if(code>=71&&code<=77)return "Снег";
        if(code>=80&&code<=82)return "Ливень";if(code==85||code==86)return "Снегопад";if(code>=95&&code<=99)return "Гроза";return "Нет данных";
    }
}
