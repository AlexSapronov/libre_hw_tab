package dev.librehw.tab;
import android.media.AudioManager;
import android.media.ToneGenerator;

/** Short, optional synthesized cues. The Android media volume remains authoritative. */
final class InterfaceSound {
    private ToneGenerator tone;
    private int volume=-1;
    private long lastAlert=-10000;
    void play(String cue,int level){
        int requested=Math.max(0,Math.min(100,level));if(requested==0)return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(cue.equals("alert")&&now-lastAlert<3000)return;
        try{
            if(tone==null||volume!=requested){release();tone=new ToneGenerator(AudioManager.STREAM_MUSIC,requested);volume=requested;}
            int type=cue.equals("alert")?ToneGenerator.TONE_PROP_NACK:cue.equals("confirm")?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_PROP_BEEP;
            tone.startTone(type,cue.equals("alert")?180:cue.equals("confirm")?90:25);
            if(cue.equals("alert"))lastAlert=now;
        }catch(RuntimeException ignored){release();}
    }
    void release(){if(tone!=null){tone.release();tone=null;}volume=-1;}
}
