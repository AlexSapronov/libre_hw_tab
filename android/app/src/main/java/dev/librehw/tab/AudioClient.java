package dev.librehw.tab;
import org.json.*;
import java.io.*;
import java.net.*;
final class AudioClient {
    static JSONObject control(String address,String token,String key,String id,String action,double value) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address+"/api/v1/audio/control").openConnection();
        c.setConnectTimeout(2200);c.setReadTimeout(2200);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("X-Libre-Token",token);c.setRequestProperty("Content-Type","application/json");
        try {
            JSONObject body=new JSONObject().put("key",key).put("id",id);
            if(action.equals("volume"))body.put("volume",value);else body.put("muted",value>=.5);
            try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            int status=c.getResponseCode();
            if(status!=200)throw new IOException(status==404?"Обнови Windows-пакет для микшера":status==409?"Аудиосеанс изменился. Попробуй ещё раз":"Не удалось изменить звук: HTTP "+status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[2048];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>16384)throw new IOException("Некорректный ответ микшера");out.write(buffer,0,n);}
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{c.disconnect();}
    }
}
