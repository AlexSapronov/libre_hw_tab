package dev.librehw.tab;
import java.net.*;
import java.io.*;
import org.json.*;

final class WeatherClient {
    static JSONObject request(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);
        try {
            if(c.getResponseCode()!=200)throw new IOException("Погода временно недоступна");
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
    static JSONObject fetch(String city,double latitude,double longitude) throws Exception {
        JSONObject data=request("https://api.open-meteo.com/v1/forecast?latitude="+latitude+"&longitude="+longitude+"&current=temperature_2m,weather_code&timezone=auto");
        JSONObject current=data.getJSONObject("current");
        double temperature=current.getDouble("temperature_2m");int code=current.getInt("weather_code");
        if(!Double.isFinite(temperature)||temperature< -100||temperature>70)throw new IOException("Некорректная погода");
        return new JSONObject().put("city",city).put("temperature",temperature).put("code",code).put("fetched",System.currentTimeMillis());
    }
    static String description(int code) {
        if(code==0)return "Ясно";if(code>=1&&code<=3)return "Облачно";if(code==45||code==48)return "Туман";
        if(code>=51&&code<=57)return "Морось";if(code>=61&&code<=67)return "Дождь";if(code>=71&&code<=77)return "Снег";
        if(code>=80&&code<=82)return "Ливень";if(code==85||code==86)return "Снегопад";if(code>=95&&code<=99)return "Гроза";return "Нет данных";
    }
}
