package dev.librehw.tab;
import java.net.*;
import java.io.*;
import org.json.*;

final class TelemetryClient {
    static HttpURLConnection open(String address, String token, int timeout) throws IOException {
        HttpURLConnection request = (HttpURLConnection)new URL(address + "/api/v1/telemetry").openConnection();
        request.setConnectTimeout(timeout); request.setReadTimeout(timeout);
        request.setInstanceFollowRedirects(false);
        request.setRequestProperty("X-Libre-Token", token);
        return request;
    }
    static JSONObject read(HttpURLConnection request) throws Exception {
        int status = request.getResponseCode();
        if (status == 401) throw new IOException("Неверный ключ подключения");
        if (status != 200) throw new IOException("Агент: HTTP " + status);
        try (InputStream input=request.getInputStream(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096]; int count;
            while ((count=input.read(buffer)) != -1) {
                if (output.size()+count > 65536) throw new IOException("Слишком большой ответ агента");
                output.write(buffer,0,count);
            }
            JSONObject sample=new JSONObject(output.toString("UTF-8"));
            if (sample.optInt("version") != 1 || !sample.has("timestamp")) throw new IOException("Несовместимая версия агента");
            long age=Math.abs(java.time.Duration.between(java.time.Instant.parse(sample.getString("timestamp")),java.time.Instant.now()).getSeconds());
            if (age > 10) throw new IOException("Данные устарели. Проверь часы ПК и планшета");
            return sample;
        }
    }
    static void probe(String address,String token) throws Exception {
        HttpURLConnection request=open(address,token,1200);
        try { read(request); } finally { request.disconnect(); }
    }
}
