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
}
