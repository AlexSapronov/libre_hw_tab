package dev.librehw.tab;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import java.util.*;
import java.io.IOException;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PairingTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static String envelope(String... addresses) throws Exception {
        return new JSONObject().put("app","libre-hw-tab").put("version",1).put("token",TOKEN)
            .put("addresses",new JSONArray(Arrays.asList(addresses))).toString();
    }
    @Test public void qrRoundTripAndAutomaticAddressSelection() throws Exception {
        String source=envelope("http://172.23.16.1:8750","http://10.8.1.2:8750","http://192.168.0.100:8750");
        BitMatrix image=new QRCodeWriter().encode(source,BarcodeFormat.QR_CODE,900,900);
        int[] pixels=new int[900*900];
        for(int y=0;y<900;y++) for(int x=0;x<900;x++) pixels[y*900+x]=image.get(x,y)?0xff000000:0xffffffff;
        String decoded=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(900,900,pixels)))).getText();
        PairingInfo pairing=PairingInfo.parse(decoded);
        List<String> attempted=new ArrayList<>();
        String selected=pairing.reachable((address,token)-> { assertEquals(TOKEN,token); attempted.add(address); if (!address.contains("192.168.0.100")) throw new IOException("unreachable"); });
        assertEquals("http://192.168.0.100:8750",selected); assertEquals(3,attempted.size());
        try { pairing.reachable((a,t)-> { throw new IOException("unauthorized"); }); fail(); } catch(Exception expected) { assertTrue(expected.getMessage().contains("ПК не отвечает")); }
    }
    @Test public void rejectsOtherQrAndUnsafeAddresses() throws Exception {
        String[] rejected={"https://example.com", "http://8.8.8.8:8750", "http://127.0.0.1:8750", "http://192.168.0.100.evil.test:8750", "http://192.168.0.100:8750/path", "http://u:p@192.168.0.100:8750", "http://192.168.0.100:8750?x=1", "http://192.168.0.100:8750#x", "http://192.168.0.100:80", "http://192.168.0.999:8750", "http://192.168.00.1:8750"};
        for(String address:rejected) { try { PairingInfo.parse(envelope(address)); fail(address); } catch(Exception expected) { } }
        for(String text:new String[]{"", "https://example.com",envelope(),envelope("http://192.168.0.1:8750").replace(TOKEN,"short"),envelope("http://192.168.0.1:8750").replace("libre-hw-tab","another-app")}) {
            try { PairingInfo.parse(text); fail(text); } catch(Exception expected) { }
        }
    }
    @Test public void scanButtonStartsBundledCameraScanner() {
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            Button scan=findScan(dialog.getWindow().getDecorView()); assertNotNull(scan); scan.performClick();
            Intent intent=Shadows.shadowOf(activity).getNextStartedActivity(); assertNotNull(intent);
            assertEquals("com.journeyapps.barcodescanner.CaptureActivity",intent.getComponent().getClassName());
            assertEquals("QR_CODE",intent.getStringExtra("SCAN_FORMATS"));
        }
    }
    private Button findScan(View view) {
        if(view instanceof Button && "Сканировать QR".contentEquals(((Button)view).getText())) return (Button)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { Button found=findScan(((ViewGroup)view).getChildAt(i)); if(found!=null) return found; }
        return null;
    }
}
