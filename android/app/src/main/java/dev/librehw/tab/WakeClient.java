package dev.librehw.tab;
import java.net.*;
import org.json.*;
final class WakeClient {
    static byte[] packet(String mac) {
        String hex=mac.replace(":","").replace("-","");
        if(!hex.matches("[0-9a-fA-F]{12}"))throw new IllegalArgumentException("MAC должен содержать 12 шестнадцатеричных цифр");
        if(hex.matches("0{12}|[fF]{12}") || (Integer.parseInt(hex.substring(0,2),16)&1)!=0)throw new IllegalArgumentException("Нужен unicast MAC сетевой карты ПК");
        byte[] packet=new byte[102];for(int i=0;i<6;i++)packet[i]=(byte)255;
        for(int i=0;i<16;i++)for(int j=0;j<6;j++)packet[6+i*6+j]=(byte)Integer.parseInt(hex.substring(j*2,j*2+2),16);
        return packet;
    }
    static void send(JSONArray targets) throws Exception {
        if(targets.length()==0)throw new Exception("Сначала подключись к ПК для сохранения MAC или укажи его в настройках");
        int sent=0;
        try(DatagramSocket socket=new DatagramSocket()) {
            socket.setBroadcast(true);
            for(int i=0;i<Math.min(8,targets.length());i++){
                JSONObject target=targets.getJSONObject(i);byte[] bytes=packet(target.getString("mac"));
                String broadcast=target.optString("broadcast","255.255.255.255");
                if(!localBroadcast(broadcast))continue;
                for(String address:new String[]{broadcast,"255.255.255.255"})for(int repeat=0;repeat<3;repeat++) {
                    socket.send(new DatagramPacket(bytes,bytes.length,InetAddress.getByName(address),9));sent++;
                }
            }
        }
        if(sent==0)throw new Exception("Нет корректного адреса Wake-on-LAN");
    }
    private static boolean localBroadcast(String address) {
        if("255.255.255.255".equals(address))return true;
        if(!address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
        String[] parts=address.split("\\.");int[] n=new int[4];for(int i=0;i<4;i++){n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;}
        return n[0]==10 || (n[0]==172&&n[1]>=16&&n[1]<=31) || (n[0]==192&&n[1]==168) || (n[0]==169&&n[1]==254);
    }

}
