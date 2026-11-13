package dev.librehw.tab;

import java.net.URI;
import java.util.*;
import org.json.*;

/** Versioned LAN-only pairing envelope; never accepts general URLs or executes QR content. */
final class PairingInfo {
    final List<String> addresses;
    final String token;
    private PairingInfo(List<String> addresses, String token) { this.addresses = addresses; this.token = token; }
    static PairingInfo parse(String text) throws Exception {
        if (text == null || text.length() > 8192) throw new Exception("Это не QR подключения Libre HW Tab");
        JSONObject json = new JSONObject(text);
        if (!"libre-hw-tab".equals(json.optString("app")) || json.optInt("version") != 1) throw new Exception("Нужен QR из окна Libre HW Tab на компьютере");
        String token = json.getString("token");
        if (!token.matches("[a-fA-F0-9]{32}")) throw new Exception("Повреждён ключ подключения в QR");
        JSONArray values = json.getJSONArray("addresses");
        if (values.length() < 1 || values.length() > 16) throw new Exception("В QR нет адреса домашней сети");
        List<String> addresses = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            URI uri = new URI(values.getString(i));
            if (!"http".equals(uri.getScheme()) || !privateIpv4(uri.getHost()) || uri.getPort() < 1024 || uri.getPort() > 65535 || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || !uri.getRawPath().isEmpty()) throw new Exception("В QR недопустимый адрес локальной сети");
            String address = uri.toString(); if (!addresses.contains(address)) addresses.add(address);
        }
        return new PairingInfo(Collections.unmodifiableList(addresses), token);
    }
    private static boolean privateIpv4(String host) {
        if (host == null || !host.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
        String[] parts = host.split("\\."); int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].length() > 3 || (parts[i].length() > 1 && parts[i].startsWith("0"))) return false;
            n[i] = Integer.parseInt(parts[i]); if (n[i] > 255) return false;
        }
        return n[0] == 10 || (n[0] == 172 && n[1] >= 16 && n[1] <= 31) || (n[0] == 192 && n[1] == 168);
    }
    interface Probe { void check(String address, String token) throws Exception; }
    String reachable(Probe probe) throws Exception {
        for (String address : addresses) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            try { probe.check(address, token); return address; } catch (InterruptedException ex) { throw ex; } catch (Exception ignored) { }
        }
        throw new Exception("ПК не отвечает. Проверь общую Wi-Fi-сеть, частный профиль Windows и запущенный агент. Старое подключение сохранено.");
    }
}
