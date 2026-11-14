using NAudio.CoreAudioApi;
using NAudio.CoreAudioApi.Interfaces;
using System.Diagnostics;
internal sealed class WindowsAudio : IAudioBackend {
    public AudioCapture Open()=>new Capture();
    private sealed class Capture : AudioCapture {
        private readonly MMDeviceEnumerator enumerator=new();
        private readonly List<MMDevice> devices=new();
        private readonly List<AudioSessionControl> controls=new();
        private readonly List<IAppAudio> sessions=new();
        private readonly List<IMicrophoneAudio> microphones=new();
        private bool micError;
        internal override IReadOnlyList<IAppAudio> Sessions=>sessions;
        internal override IReadOnlyList<IMicrophoneAudio> Microphones=>microphones;
        internal override bool MicrophoneError=>micError;
        internal Capture() {
            try {
                foreach(var device in enumerator.EnumerateAudioEndPoints(DataFlow.Capture,DeviceState.Active)) {
                    devices.Add(device);try{microphones.Add(new Mic(device));}catch{micError=true;}
                }
                foreach(var device in enumerator.EnumerateAudioEndPoints(DataFlow.Render,DeviceState.Active)) {
                    devices.Add(device);
                    try {
                        var list=device.AudioSessionManager.Sessions;
                        for(int i=0;i<list.Count;i++) {
                            var session=list[i];controls.Add(session);
                            try {
                                if(session.State==AudioSessionState.AudioSessionStateExpired || session.IsSystemSoundsSession)continue;
                                int pid=(int)session.GetProcessID;if(pid<=0)continue;
                                using var process=Process.GetProcessById(pid);sessions.Add(new App(device.ID,session,pid,process.ProcessName));
                            }catch{}
                        }
                    }catch{}
                }
            }catch{Dispose();throw;}
        }
        public override void Dispose(){foreach(var control in controls)control.Dispose();foreach(var device in devices)device.Dispose();enumerator.Dispose();}
    }
    private sealed class App : IAppAudio {
        private readonly AudioSessionControl session;
        public string Id{get;}public int Pid{get;}public string ProcessName{get;}
        internal App(string endpoint,AudioSessionControl session,int pid,string process){this.session=session;Pid=pid;ProcessName=process;Id=endpoint+"|"+session.GetSessionInstanceIdentifier;_=Volume;_=Muted;}
        public float Volume{get=>session.SimpleAudioVolume.Volume;set=>session.SimpleAudioVolume.Volume=value;}
        public bool Muted{get=>session.SimpleAudioVolume.Mute;set=>session.SimpleAudioVolume.Mute=value;}
    }
    private sealed class Mic : IMicrophoneAudio {
        private readonly MMDevice device;
        public string Id=>device.ID;
        internal Mic(MMDevice device){this.device=device;_=Muted;}
        public bool Muted{get=>device.AudioEndpointVolume.Mute;set=>device.AudioEndpointVolume.Mute=value;}
    }
}
internal sealed class AudioService(GamingService gaming) : BackgroundService {
    private readonly object gate=new();private readonly AudioEngine engine=new(new WindowsAudio());
    private AudioView? current;private int? gamePid;
    internal AudioView? Current=>Volatile.Read(ref current);
    private int? GamePid() {
        if(gaming.Current.Active && gaming.Current.Pid is int pid)gamePid=pid;
        if(gamePid is int known)try{using var process=Process.GetProcessById(known);if(process.HasExited)gamePid=null;}catch{gamePid=null;}
        return gamePid;
    }
    protected override async Task ExecuteAsync(CancellationToken stop) {
        using var timer=new PeriodicTimer(TimeSpan.FromMilliseconds(500));
        while(!stop.IsCancellationRequested) {
            lock(gate)try{Volatile.Write(ref current,engine.Read(GamePid()));}catch{Volatile.Write(ref current,new(false,"Аудиослужба Windows недоступна",new("",false,false,false,0),Array.Empty<AudioBusView>()));}
            try{if(!await timer.WaitForNextTickAsync(stop))break;}catch(OperationCanceledException){break;}
        }
    }
    internal AudioView? Control(AudioCommand command) {
        lock(gate){var result=engine.Apply(command,GamePid());if(result!=null)Volatile.Write(ref current,result);return result;}
    }
}
