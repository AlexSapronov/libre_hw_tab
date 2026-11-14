package dev.librehw.tab;
import java.net.*;
import java.io.*;
import org.json.*;

final class WeatherClient {
    static JSONObject request(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestProperty("User-Agent","LibreHwTab/1.6.3");c.setRequestProperty("Accept","application/json");
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
        return fetch(city,latitude,longitude,WeatherClient::request,java.time.ZoneId.systemDefault().getId());
    }
    static JSONObject fetch(String city,double latitude,double longitude,String zone) throws Exception {
        return fetch(city,latitude,longitude,WeatherClient::request,zone);
    }
    static JSONObject fetch(String city,double latitude,double longitude,Transport transport) throws Exception {
        return fetch(city,latitude,longitude,transport,java.time.ZoneId.systemDefault().getId());
    }
    static JSONObject fetch(String city,double latitude,double longitude,Transport transport,String zone) throws Exception {
        Exception primary;
        try {
            JSONObject data=transport.get("https://api.open-meteo.com/v1/forecast?latitude="+latitude+"&longitude="+longitude+"&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code,precipitation_probability,precipitation&forecast_days=2&timezone=auto");
            JSONObject current=data.getJSONObject("current");
            int code=current.getInt("weather_code");
            JSONObject result=result(city,current.getDouble("temperature_2m"),description(code),"Open-Meteo").put("code",code);
            try{openMeteoForecast(result,data);}catch(Exception ignored){}
            return result;
        }catch(Exception ex){primary=ex;}
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        try {
            JSONObject data=transport.get("https://wttr.in/"+latitude+","+longitude+"?format=j1&lang=ru");
            if(data.optJSONObject("data")!=null)data=data.getJSONObject("data");
            JSONObject current=data.getJSONArray("current_condition").getJSONObject(0);
            JSONArray descriptions=current.optJSONArray("lang_ru");
            if(descriptions==null||descriptions.length()==0)descriptions=current.optJSONArray("weatherDesc");
            String summary=descriptions==null||descriptions.length()==0?"Текущая погода":descriptions.getJSONObject(0).optString("value","Текущая погода");
            JSONObject result=result(city,current.getDouble("temp_C"),summary,"wttr.in").put("forecastZone",zone);
            try{wttrForecast(result,data);}catch(Exception ignored){}
            return result;
        }catch(Exception fallback){throw new IOException("Open-Meteo: "+reason(primary)+"\nwttr.in: "+reason(fallback));}
    }
    // Keep today and following days in the cache; choose the next three slots at render time.
    static void openMeteoForecast(JSONObject result,JSONObject data) throws Exception {
        JSONObject hourly=data.getJSONObject("hourly");JSONArray times=hourly.getJSONArray("time"),slots=new JSONArray();
        for(int i=0;i<times.length();i++) {
            java.time.LocalDateTime time=java.time.LocalDateTime.parse(times.getString(i));int hour=time.getHour();
            if(time.getMinute()!=0 || (hour!=9 && hour!=15 && hour!=21))continue;
            double temperature=hourly.getJSONArray("temperature_2m").getDouble(i);
            if(!Double.isFinite(temperature)||temperature< -100||temperature>70)continue;
            JSONObject slot=new JSONObject().put("date",time.toLocalDate().toString()).put("hour",hour).put("temperature",temperature)
                .put("summary",description(hourly.getJSONArray("weather_code").getInt(i)));
            optionalNumber(slot,"probability",hourly.optJSONArray("precipitation_probability"),i,100);
            optionalNumber(slot,"precipitation",hourly.optJSONArray("precipitation"),i,1000);
            slot.put("precipitationHours",1);slots.put(slot);
        }
        result.put("forecast",slots).put("forecastZone",data.getString("timezone"));
    }
    static void wttrForecast(JSONObject result,JSONObject data) throws Exception {
        JSONArray days=data.getJSONArray("weather"),slots=new JSONArray();
        for(int d=0;d<days.length();d++) {
            JSONObject day=days.getJSONObject(d);String date=java.time.LocalDate.parse(day.getString("date")).toString();
            JSONArray hourly=day.optJSONArray("hourly");if(hourly==null)continue;
            for(int i=0;i<hourly.length();i++) {
                JSONObject item=hourly.getJSONObject(i);int time=item.optInt("time",-1);
                if(time!=900 && time!=1500 && time!=2100)continue;
                double temperature=item.getDouble("tempC");if(!Double.isFinite(temperature)||temperature< -100||temperature>70)continue;
                JSONArray descriptions=item.optJSONArray("lang_ru");if(descriptions==null||descriptions.length()==0)descriptions=item.optJSONArray("weatherDesc");
                JSONObject slot=new JSONObject().put("date",date).put("hour",time/100).put("temperature",temperature)
                    .put("summary",descriptions==null||descriptions.length()==0?"Нет данных":descriptions.getJSONObject(0).optString("value","Нет данных"));
                double rain=item.optDouble("chanceofrain",Double.NaN),snow=item.optDouble("chanceofsnow",Double.NaN);
                double chance=Double.isFinite(rain)&&Double.isFinite(snow)?Math.max(rain,snow):Double.isFinite(rain)?rain:snow;
                if(Double.isFinite(chance)&&chance>=0&&chance<=100)slot.put("probability",chance);
                double mm=item.optDouble("precipMM",Double.NaN);if(Double.isFinite(mm)&&mm>=0&&mm<=1000)slot.put("precipitation",mm);
                slot.put("precipitationHours",3);slots.put(slot);
            }
        }
        result.put("forecast",slots);
    }
    static java.time.ZoneId forecastZone(JSONObject weather) {
        try{return java.time.ZoneId.of(weather.getString("forecastZone"));}catch(Exception ignored){return java.time.ZoneId.systemDefault();}
    }
    static JSONArray upcoming(JSONObject weather,java.time.Instant instant) {
        java.time.ZonedDateTime now=instant.atZone(forecastZone(weather));JSONArray selected=new JSONArray();
        JSONArray available=weather==null?null:weather.optJSONArray("forecast");
        try {
            for(int day=0;day<2 && selected.length()<3;day++)for(int hour:new int[]{9,15,21}) {
                java.time.LocalDate date=now.toLocalDate().plusDays(day);
                if(date.atTime(hour,0).atZone(now.getZone()).toInstant().isBefore(instant))continue;
                if(selected.length()==3)break;
                JSONObject slot=null;
                if(available!=null)for(int i=0;i<available.length();i++) {
                    JSONObject candidate=available.optJSONObject(i);
                    if(candidate!=null && date.toString().equals(candidate.optString("date")) && candidate.optInt("hour")==hour){slot=candidate;break;}
                }
                selected.put(slot==null?new JSONObject().put("date",date.toString()).put("hour",hour):slot);
            }
        }catch(JSONException ignored){}
        return selected;
    }
    static String dayLabel(String date,java.time.LocalDate today) {
        if(today.toString().equals(date))return "СЕГОДНЯ";
        if(today.plusDays(1).toString().equals(date))return "ЗАВТРА";
        try{return java.time.LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"));}catch(Exception ignored){return "";}
    }
    private static void optionalNumber(JSONObject slot,String key,JSONArray values,int index,double maximum) throws Exception {
        double value=values==null?Double.NaN:values.optDouble(index,Double.NaN);
        if(Double.isFinite(value)&&value>=0&&value<=maximum)slot.put(key,value);
    }
    private static JSONObject result(String city,double temperature,String summary,String source) throws Exception {
        if(!Double.isFinite(temperature)||temperature< -100||temperature>70)throw new IOException("Некорректная температура");
        return new JSONObject().put("city",city).put("temperature",temperature).put("summary",summary.substring(0,Math.min(100,summary.length()))).put("source",source).put("forecastVersion",2).put("fetched",System.currentTimeMillis());
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
