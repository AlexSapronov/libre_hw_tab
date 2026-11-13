package dev.librehw.tab;
import org.json.*;
final class DemoData {
    static JSONObject sample() throws JSONException {
        double wave = Math.sin(System.currentTimeMillis() / 5500.0);
        JSONObject data = new JSONObject();
        data.put("version", 1).put("timestamp", java.time.Instant.now().toString()).put("host", "DEMO / WORKSTATION");
        data.put("cpu", device("AMD Ryzen 7 7800X3D", 58 + wave * 8, 42 + wave * 15, 4750, 68, 1100, 0, 0));
        data.getJSONObject("cpu").put("pump",2705).put("sideFan",1467).put("fan",765).put("coolingProfile",true);
        data.put("gpu", device("NVIDIA GeForce RTX 5070 Ti", 64 + wave * 5, 86 + wave * 8, 2790, 247, 1450, 0, 0));
        data.put("memory", device("DDR5 MEMORY", 0, 48 + wave * 2, 0, 0, 0, 15.4 + wave, 32));
        JSONArray frames=new JSONArray(); for(int i=0;i<300;i++)frames.put(7.3+Math.sin(i/8.0)*1.2+(i==120?12:0));
        data.put("game",new JSONObject().put("active",false).put("status","idle").put("name","DEMO · World of Warcraft")
            .put("fps",136+wave*8).put("frameTime",7.35).put("low1",111).put("frames",frames).put("frameSeries",frames).put("historySeconds",60).put("bucketMs",200).put("paused",false));
        return data;
    }
    private static JSONObject device(String name, double temperature, double load, double clock, double power, double fan, double used, double total) throws JSONException {
        return new JSONObject().put("name", name).put("temperature", temperature).put("load", load).put("clock", clock).put("power", power).put("fan", fan).put("used", used).put("total", total);
    }
}
