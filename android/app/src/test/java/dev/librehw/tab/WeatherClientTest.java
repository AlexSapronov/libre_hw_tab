package dev.librehw.tab;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.net.*;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class WeatherClientTest {
    @Test public void PrimarySuccessDoesNotCallFallback() throws Exception {
        int[] calls={0};JSONObject result=WeatherClient.fetch("СПб",60,30,url->{calls[0]++;return new JSONObject("{current:{temperature_2m:4.5,weather_code:73}}");});
        assertEquals(1,calls[0]);assertEquals(4.5,result.getDouble("temperature"),0);assertEquals("Снег",result.getString("summary"));assertEquals("Open-Meteo",result.getString("source"));
    }
    @Test public void DnsFailureUsesIndependentProviderAndItsOwnDescription() throws Exception {
        int[] calls={0};JSONObject result=WeatherClient.fetch("СПб",60,30,url->{if(calls[0]++==0)throw new UnknownHostException();assertTrue(url.startsWith("https://wttr.in/60.0,30.0"));return new JSONObject("{data:{current_condition:[{temp_C:'-3',weatherCode:'113',lang_ru:[{value:'Ясно'}]}]}}");});
        assertEquals(2,calls[0]);assertEquals(-3,result.getDouble("temperature"),0);assertEquals("Ясно",result.getString("summary"));assertEquals("wttr.in",result.getString("source"));assertFalse(result.has("code"));
    }
    @Test public void InvalidTemperatureFallsBackAndBothErrorsRemainVisible() throws Exception {
        try {WeatherClient.fetch("СПб",60,30,url->{if(url.contains("open-meteo"))return new JSONObject("{current:{temperature_2m:999,weather_code:0}}");throw new SocketTimeoutException();});fail();}
        catch(IOException ex){assertTrue(ex.getMessage().contains("Некорректная температура"));assertTrue(ex.getMessage().contains("wttr.in: сервер не ответил вовремя"));}
    }
    @Test public void FallbackAlsoAcceptsOriginalUnwrappedSchema() throws Exception {
        JSONObject result=WeatherClient.fetch("СПб",60,30,url->{if(url.contains("open-meteo"))throw new IOException("HTTP 429");return new JSONObject("{current_condition:[{temp_C:'5',weatherDesc:[{value:'Cloudy'}]}]}");});
        assertEquals("Cloudy",result.getString("summary"));
    }

    static JSONObject primaryForecast() throws Exception {
        JSONObject hourly=new JSONObject();String[] names={"time","temperature_2m","weather_code","precipitation_probability","precipitation"};
        for(String name:names)hourly.put(name,new JSONArray());
        for(int day=0;day<2;day++)for(int hour=0;hour<24;hour++) {
            hourly.getJSONArray("time").put((day==0?"2026-12-31":"2027-01-01")+String.format(java.util.Locale.US,"T%02d:00",hour));
            hourly.getJSONArray("temperature_2m").put(day==0?30:hour==9?-3:hour==15?2:-1);
            hourly.getJSONArray("weather_code").put(hour==15?61:73);
            hourly.getJSONArray("precipitation_probability").put(hour==21?JSONObject.NULL:hour==15?85:40);
            hourly.getJSONArray("precipitation").put(hour==21?JSONObject.NULL:hour==15?1.2:0.3);
        }
        return new JSONObject().put("current",new JSONObject("{time:'2026-12-31T23:30',temperature_2m:1,weather_code:3}"))
            .put("timezone","Pacific/Auckland").put("hourly",hourly);
    }
    @Test public void TomorrowUsesCityDateAcrossYearBoundaryAndPreservesMissingPrecipitation() throws Exception {
        JSONObject result=WeatherClient.fetch("Город",60,30,url->{assertTrue(url.contains("forecast_days=2"));return primaryForecast();});
        assertEquals("2027-01-01",result.getString("forecastDate"));assertEquals("Pacific/Auckland",result.getString("forecastZone"));
        JSONArray slots=result.getJSONArray("forecast");assertEquals(3,slots.length());assertEquals(9,slots.getJSONObject(0).getInt("hour"));
        assertEquals(-3,slots.getJSONObject(0).getDouble("temperature"),0);
        assertEquals(1.2,slots.getJSONObject(1).getDouble("precipitation"),0);assertEquals(85,slots.getJSONObject(1).getDouble("probability"),0);
        assertFalse(slots.getJSONObject(2).has("precipitation"));assertFalse(slots.getJSONObject(2).has("probability"));
        assertEquals(3,new JSONObject(result.toString()).getJSONArray("forecast").length());
    }
    @Test public void FallbackForecastUsesExactTomorrowAndThreeHourRainAndSnow() throws Exception {
        JSONObject day=new JSONObject().put("date","2027-01-01").put("hourly",new JSONArray());
        for(int hour:new int[]{9,15,21})day.getJSONArray("hourly").put(new JSONObject().put("time",hour*100).put("tempC","-5")
            .put("chanceofrain","20").put("chanceofsnow","80").put("precipMM","0.6").put("lang_ru",new JSONArray("[{value:'Снег'}]")));
        JSONObject data=new JSONObject().put("current_condition",new JSONArray("[{temp_C:'-2',localObsDateTime:'2026-12-31 11:30 PM'}]"))
            .put("weather",new JSONArray().put(new JSONObject().put("date","2026-12-31")).put(day));
        JSONObject result=WeatherClient.fetch("Город",60,30,url->{if(url.contains("open-meteo"))throw new UnknownHostException();return new JSONObject().put("data",data);});
        assertEquals("2027-01-01",result.getString("forecastDate"));assertEquals(3,result.getJSONArray("forecast").length());
        JSONObject first=result.getJSONArray("forecast").getJSONObject(0);assertEquals(80,first.getInt("probability"));assertEquals(3,first.getInt("precipitationHours"));
        assertEquals("Снег",first.getString("summary"));
    }
    @Test public void MissingForecastDoesNotBreakCurrentWeather() throws Exception {
        JSONObject result=WeatherClient.fetch("СПб",60,30,url->new JSONObject("{current:{time:'2026-11-14T02:00',temperature_2m:3,weather_code:3}}"));
        assertEquals(3,result.getDouble("temperature"),0);assertFalse(result.has("forecast"));
    }
}
