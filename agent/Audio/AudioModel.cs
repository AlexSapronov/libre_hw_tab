using System.Security.Cryptography;
using System.Text;
internal record AudioBusView(string Key,string Id,bool Available,float Volume,bool Muted,bool Mixed,int Sessions);
internal record MicrophoneView(string Id,bool Available,bool Muted,bool Mixed,int Devices);
internal record AudioView(bool Ready,string? Error,MicrophoneView Microphone,AudioBusView[] Buses);
internal record AudioCommand(string Key,string Id,double? Volume=null,bool? Muted=null);
internal interface IAppAudio {string Id{get;} int Pid{get;} string ProcessName{get;} float Volume{get;set;} bool Muted{get;set;}}
internal interface IMicrophoneAudio {string Id{get;} bool Muted{get;set;}}
internal abstract class AudioCapture : IDisposable {
    internal abstract IReadOnlyList<IAppAudio> Sessions{get;}
    internal abstract IReadOnlyList<IMicrophoneAudio> Microphones{get;}
    internal virtual bool MicrophoneError=>false;
    public abstract void Dispose();
}
internal interface IAudioBackend {AudioCapture Open();}
internal static class AudioPolicy {
    internal static string? Bus(string process,int pid,int? gamePid) {
        if(pid>0 && pid==gamePid)return "game";
        string name=Path.GetFileNameWithoutExtension(process).ToLowerInvariant();
        return name=="chrome"?"chrome":name is "discord" or "discordptb" or "discordcanary"?"discord":null;
    }
    internal static string Identity(IEnumerable<string> ids)=>Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(string.Join("\n",ids.Order(StringComparer.Ordinal))))).ToLowerInvariant()[..32];
    internal static bool Valid(AudioCommand command)=>(command.Key is "microphone" or "chrome" or "game" or "discord")
        && ((command.Muted.HasValue && command.Volume==null) || (command.Key!="microphone" && command.Muted==null && command.Volume is double v && double.IsFinite(v) && v>=0 && v<=1));
}
internal sealed class AudioEngine(IAudioBackend backend) {
    internal static readonly string[] Keys={"chrome","game","discord"};
    internal AudioView Read(int? gamePid){using var capture=backend.Open();return Snapshot(capture,gamePid);}
    internal AudioView? Apply(AudioCommand command,int? gamePid) {
        if(!AudioPolicy.Valid(command))return null;
        using var capture=backend.Open();var before=Snapshot(capture,gamePid);
        if(command.Key=="microphone") {
            if(!before.Microphone.Available || before.Microphone.Id!=command.Id)return null;
            foreach(var mic in capture.Microphones)mic.Muted=command.Muted!.Value;
        }else {
            var bus=before.Buses.Single(b=>b.Key==command.Key);if(!bus.Available || bus.Id!=command.Id)return null;
            foreach(var session in capture.Sessions.Where(s=>AudioPolicy.Bus(s.ProcessName,s.Pid,gamePid)==command.Key)) {
                if(command.Volume is double v)session.Volume=(float)v;else session.Muted=command.Muted!.Value;
            }
        }
        return Snapshot(capture,gamePid);
    }
    private static AudioView Snapshot(AudioCapture capture,int? gamePid) {
        var mic=capture.Microphones;bool available=mic.Count>0 && !capture.MicrophoneError;
        var microphone=new MicrophoneView(AudioPolicy.Identity(mic.Select(m=>m.Id)),available,available && mic.All(m=>m.Muted),available && mic.Any(m=>m.Muted) && mic.Any(m=>!m.Muted),mic.Count);
        var buses=Keys.Select(key=>{
            var sessions=capture.Sessions.Where(s=>AudioPolicy.Bus(s.ProcessName,s.Pid,gamePid)==key).ToArray();
            float volume=sessions.Length==0?0:sessions.Max(s=>s.Volume);
            return new AudioBusView(key,AudioPolicy.Identity(sessions.Select(s=>s.Id)),sessions.Length>0,volume,sessions.Length>0 && sessions.All(s=>s.Muted),
                sessions.Any(s=>Math.Abs(s.Volume-volume)>.005f) || (sessions.Any(s=>s.Muted) && sessions.Any(s=>!s.Muted)),sessions.Length);
        }).ToArray();
        return new(true,capture.MicrophoneError?"Не удалось прочитать все микрофоны":null,microphone,buses);
    }
}
