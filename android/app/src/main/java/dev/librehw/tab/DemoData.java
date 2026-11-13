package dev.librehw.tab;
import org.json.*;
final class DemoData {
    static JSONObject sample() throws JSONException {
        double wave = Math.sin(System.currentTimeMillis() / 5500.0);
        JSONObject data = new JSONObject();
        data.put("version", 1).put("timestamp", java.time.Instant.now().toString()).put("host", "DEMO / WORKSTATION");
        data.put("cpu", device("AMD Ryzen 7 7800X3D", 58 + wave * 8, 42 + wave * 15, 4750, 68, 1100, 0, 0));
        data.put("gpu", device("NVIDIA GeForce RTX 5070 Ti", 64 + wave * 5, 86 + wave * 8, 2790, 247, 1450, 0, 0));
        data.put("memory", device("DDR5 MEMORY", 0, 48 + wave * 2, 0, 0, 0, 15.4 + wave, 32));
        return data;
    }
    private static JSONObject device(String name, double temperature, double load, double clock, double power, double fan, double used, double total) throws JSONException {
        return new JSONObject().put("name", name).put("temperature", temperature).put("load", load).put("clock", clock).put("power", power).put("fan", fan).put("used", used).put("total", total);
    }
}
