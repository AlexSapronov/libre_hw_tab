using Xunit;
public class AudioTests {
    private sealed class App(string id,int pid,string name,float volume,bool muted=false) : IAppAudio {public string Id=>id;public int Pid=>pid;public string ProcessName=>name;public float Volume{get;set;}=volume;public bool Muted{get;set;}=muted;}
    private sealed class Mic(string id,bool muted=false) : IMicrophoneAudio {public string Id=>id;public bool Muted{get;set;}=muted;}
    private sealed class Capture(List<IAppAudio> sessions,List<IMicrophoneAudio> microphones,bool micError) : AudioCapture {
        internal override IReadOnlyList<IAppAudio> Sessions=>sessions;internal override IReadOnlyList<IMicrophoneAudio> Microphones=>microphones;internal override bool MicrophoneError=>micError;public override void Dispose(){}
    }
    private sealed class Backend : IAudioBackend {
        internal readonly List<IAppAudio> Sessions=new(){new App("chrome-1",10,"chrome",.6f),new App("chrome-2",11,"chrome.exe",.4f,true),new App("discord",20,"Discord",.8f),new App("game",42,"WowClassic",.7f),new App("other",99,"spotify",.9f)};
        internal readonly List<IMicrophoneAudio> Microphones=new(){new Mic("headset"),new Mic("webcam",true)};
        internal bool MicError;
        public AudioCapture Open()=>new Capture(Sessions,Microphones,MicError);
    }
    [Fact] public void AppSliderControlsOnlyItsSessionsAndReadbackIsReal() {
        var backend=new Backend();var engine=new AudioEngine(backend);var state=engine.Read(42);var chrome=state.Buses.Single(b=>b.Key=="chrome");
        Assert.True(chrome.Mixed);Assert.Equal(2,chrome.Sessions);Assert.Equal(.6f,chrome.Volume);
        var changed=engine.Apply(new("chrome",chrome.Id,.25),42)!;
        Assert.All(backend.Sessions.Where(s=>AudioPolicy.Bus(s.ProcessName,s.Pid,42)=="chrome"),s=>Assert.Equal(.25f,s.Volume));
        Assert.Equal(.8f,backend.Sessions.Single(s=>s.Pid==20).Volume);Assert.Equal(.7f,backend.Sessions.Single(s=>s.Pid==42).Volume);Assert.Equal(.9f,backend.Sessions.Single(s=>s.Pid==99).Volume);
        Assert.Equal(.25f,changed.Buses.Single(b=>b.Key=="chrome").Volume);
    }
    [Fact] public void GlobalMicrophoneMutesEveryCaptureInputWithoutChangingOutputVolume() {
        var backend=new Backend();var engine=new AudioEngine(backend);var mic=engine.Read(42).Microphone;Assert.True(mic.Mixed);Assert.False(mic.Muted);
        Assert.True(engine.Apply(new("microphone",mic.Id,Muted:true),42)!.Microphone.Muted);Assert.All(backend.Microphones,m=>Assert.True(m.Muted));
        Assert.False(engine.Apply(new("microphone",mic.Id,Muted:false),42)!.Microphone.Muted);Assert.All(backend.Microphones,m=>Assert.False(m.Muted));
        Assert.Equal(.6f,backend.Sessions[0].Volume);backend.MicError=true;Assert.False(engine.Read(42).Microphone.Available);Assert.Null(engine.Apply(new("microphone",mic.Id,Muted:true),42));
    }
    [Fact] public void StaleGameSessionInvalidRangesAndAmbiguousCommandsCannotChangeSound() {
        Assert.Equal("game",AudioPolicy.Bus("",42,42));
        var backend=new Backend();var engine=new AudioEngine(backend);var old=engine.Read(42).Buses.Single(b=>b.Key=="game");
        Assert.Null(engine.Apply(new("game",old.Id,.2),99));Assert.Equal(.9f,backend.Sessions.Single(s=>s.Pid==99).Volume);
        foreach(double invalid in new[]{-1,1.1,double.NaN,double.PositiveInfinity})Assert.Null(engine.Apply(new("game",old.Id,invalid),42));
        Assert.Null(engine.Apply(new("game",old.Id,.5,true),42));Assert.Null(engine.Apply(new("game","stale",.5),42));Assert.Null(engine.Apply(new("all",old.Id,.5),42));
        Assert.Equal(.7f,backend.Sessions.Single(s=>s.Pid==42).Volume);
    }
    [Fact] public void MutePreservesVolumeAndNewOrRemovedSessionsInvalidateIdentity() {
        var backend=new Backend();var engine=new AudioEngine(backend);var discord=engine.Read(42).Buses.Single(b=>b.Key=="discord");
        Assert.True(engine.Apply(new("discord",discord.Id,Muted:true),42)!.Buses.Single(b=>b.Key=="discord").Muted);
        Assert.Equal(.8f,backend.Sessions.Single(s=>s.Pid==20).Volume);
        backend.Sessions.Add(new App("discord-new",21,"DiscordCanary",.5f));Assert.Null(engine.Apply(new("discord",discord.Id,Muted:false),42));
        Assert.True(backend.Sessions.Single(s=>s.Pid==20).Muted);
        Assert.False(engine.Read(null).Buses.Single(b=>b.Key=="game").Available);
    }
}
