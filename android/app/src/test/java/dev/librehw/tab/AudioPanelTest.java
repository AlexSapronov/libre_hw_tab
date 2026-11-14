package dev.librehw.tab;
import android.graphics.*;
import android.view.MotionEvent;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AudioPanelTest {
    private MediaPanelTest.FeedbackView view() throws Exception {
        MediaPanelTest.FeedbackView view=new MediaPanelTest.FeedbackView();view.layout(0,0,1340,800);view.animations=false;view.themeId=6;view.mode=2;for(int i=0;i<61;i++){JSONObject sample=DemoData.sample();sample.getJSONObject("game").put("fps",130+Math.sin(i/8.)*25);view.accept(sample,"",true);org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));}return view;
    }
    @Test public void MixerSharesStockCardSizeAndExistsOnlyOnGameScreen() throws Exception {
        DashboardView view=view();RectF audio=view.layout.rect("audio",true),cpu=view.layout.rect("cpu",false),main=view.layout.rect("main",true);
        assertEquals(cpu.width(),audio.width(),0);assertEquals(cpu.height(),audio.height(),0);assertEquals(audio.width(),main.width(),0);assertTrue(view.layout.valid(true));
        render(view,"audio-game");assertEquals(7,view.audioControls.size());
        for(DashboardView.AudioControl control:view.audioControls.values())assertTrue(audio.contains(control.hit));
        view.accept(DemoData.sample().put("media",MediaPanelTest.media()),"",true);render(view,"audio-game-media");
        for(DashboardView.AudioControl control:view.audioControls.values())assertTrue(control.hit.bottom<589);
        view.mode=1;render(view,"audio-hidden-overview");assertTrue(view.audioControls.isEmpty());
    }
    @Test public void FaderPreviewsLocallyAndSendsOnceOnReleaseWithOriginalSessionIdentity() throws Exception {
        MediaPanelTest.FeedbackView view=view();render(view,"audio-before-drag");DashboardView.AudioControl chrome=view.audioControls.get("chrome.volume");
        int[] count={0};String[] key={""},id={""},action={""};double[] value={-1};view.onAudio=(k,i,a,v)->{count[0]++;key[0]=k;id[0]=i;action[0]=a;value[0]=v;};
        float x=chrome.track.centerX(),start=chrome.track.centerY(),quarter=chrome.track.bottom-chrome.track.height()*.25f;
        touch(view,0,x,start);touch(view,2,x,quarter);assertEquals(0,count[0]);assertEquals(1,view.haptics);render(view,"audio-drag-preview");
        JSONObject changed=DemoData.sample();changed.getJSONObject("audio").getJSONArray("buses").getJSONObject(0).put("id","new-session");view.accept(changed,"",true);render(view,"audio-session-changed");
        touch(view,1,x,quarter);assertEquals(1,count[0]);assertEquals("chrome",key[0]);assertEquals("volume",action[0]);assertEquals("demo-chrome",id[0]);assertEquals(.25,value[0],.001);
        chrome=view.audioControls.get("chrome.volume");touch(view,0,chrome.track.centerX(),chrome.track.centerY());touch(view,3,chrome.track.centerX(),chrome.track.centerY());assertEquals(1,count[0]);
    }
    @Test public void MicrophoneUsesConfirmedStateAndUnavailableAppsAreNotClickable() throws Exception {
        MediaPanelTest.FeedbackView view=view();render(view,"audio-mic-on");DashboardView.AudioControl mic=view.audioControls.get("microphone.mute");double[] desired={-1};int[] calls={0};view.onAudio=(k,id,a,value)->{desired[0]=value;calls[0]++;};
        touch(view,0,mic.hit.centerX(),mic.hit.centerY());touch(view,1,mic.hit.centerX(),mic.hit.centerY());assertEquals(1,desired[0],0);assertFalse(view.audio().getJSONObject("microphone").getBoolean("muted"));
        JSONObject confirmed=new JSONObject(view.audio().toString());confirmed.getJSONObject("microphone").put("muted",true);view.audioAcknowledged(confirmed);render(view,"audio-mic-muted");assertTrue(view.audioControls.get("microphone.mute").muted);
        confirmed.getJSONArray("buses").getJSONObject(2).put("available",false);view.audioAcknowledged(confirmed);render(view,"audio-discord-unavailable");assertFalse(view.audioControls.get("discord.volume").enabled);
        view.audioCommandPending=true;render(view,"audio-pending");assertFalse(view.audioControls.get("microphone.mute").enabled);
    }
    @Test public void OldGameLayoutMigratesWithoutResettingOverviewOrHiddenMetrics() throws Exception {
        DashboardLayout migrated=DashboardLayout.parse("{version:1,positions:{'overview.cpu':[36,125,300,400],'game.main':[36,125,926,425],'game.memory':[36,443,306,690]},disabled:['power']}");
        assertEquals(264,migrated.rect("cpu",false).width(),0);assertFalse(migrated.metric("power"));assertTrue(migrated.valid(true));assertEquals(436,migrated.rect("main",true).width(),0);assertEquals(493,migrated.rect("memory",true).top,0);
        assertEquals(2,new JSONObject(migrated.json()).getInt("version"));assertEquals(migrated.json(),DashboardLayout.parse(migrated.json()).json());
    }
    private void touch(DashboardView view,int action,float x,float y){float scale=800f/760f,dx=(1340-1200*scale)/2;view.onTouchEvent(MotionEvent.obtain(0,0,action,dx+x*scale,y*scale,0));}
    private void render(DashboardView view,String name) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(1340,800,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));File folder=new File("build/screenshots");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
}
