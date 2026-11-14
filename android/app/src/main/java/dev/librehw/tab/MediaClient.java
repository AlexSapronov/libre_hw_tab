package dev.librehw.tab;
import android.graphics.*;
import org.json.*;
import java.net.*;
import java.io.*;

final class MediaClient {
    private static HttpURLConnection open(String address,String token,String path) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address+path).openConnection();c.setConnectTimeout(1800);c.setReadTimeout(1800);c.setInstanceFollowRedirects(false);c.setRequestProperty("X-Libre-Token",token);return c;
    }
    static void control(String address,String token,String id,String action,double position) throws Exception {
        if(!id.matches("[a-f0-9]{32}"))throw new IOException("Медиасессия изменилась");
        HttpURLConnection c=open(address,token,"/api/v1/media/control");
        try {
            c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
            JSONObject body=new JSONObject().put("id",id).put("action",action);if(action.equals("seek"))body.put("position",position);
            try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            if(c.getResponseCode()!=200)throw new IOException(c.getResponseCode()==409?"Видео изменилось или команда недоступна":"Нет связи с медиаплеером");
        }finally{c.disconnect();}
    }
    static Bitmap thumbnail(String address,String token,String id) throws Exception {
        if(!id.matches("[a-f0-9]{32}"))return null;
        HttpURLConnection c=open(address,token,"/api/v1/media/"+id+"/thumbnail");
        try {
            if(c.getResponseCode()!=200)return null;
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>131072)return null;out.write(b,0,n);}
                byte[] bytes=out.toByteArray();BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,dimensions);
                if(dimensions.outWidth<=0||dimensions.outHeight<=0||dimensions.outWidth>512||dimensions.outHeight>512)return null;
                return BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            }
        }finally{c.disconnect();}
    }
}
