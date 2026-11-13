using System.Diagnostics;
using System.Text.Json;

internal sealed class GamingService : BackgroundService
{
    private readonly object gate=new();
    private readonly FrameHistory history=new(); private readonly GameDetector detector=new();
    private GameView view=new(false,"starting");
    internal GameView Current=>Volatile.Read(ref view);
    private GameRules rules=new();
    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        string path=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"LibreHwTab","games.json");
        try {if(!File.Exists(path))File.WriteAllText(path,JsonSerializer.Serialize(new GameRules(Array.Empty<string>(),Array.Empty<string>()),new JsonSerializerOptions{WriteIndented=true}));}catch{}
        while(!stop.IsCancellationRequested) {
            Process? child=null;
            try {
                string exe=Path.Combine(AppContext.BaseDirectory,"PresentMon.exe");
                if(!File.Exists(exe))throw new IOException("PresentMon.exe отсутствует — переустановите Windows-пакет");
                var start=new ProcessStartInfo(exe) {UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true,WorkingDirectory=AppContext.BaseDirectory};
                foreach(var arg in new[]{"--output_stdout","--no_console_stats","--v1_metrics","--no_track_gpu","--no_track_input","--session_name",$"LibreHwTab-{Process.GetCurrentProcess().SessionId}","--stop_existing_session"})start.ArgumentList.Add(arg);
                child=Process.Start(start) ?? throw new IOException("Не удалось запустить PresentMon");
                using var job=new CaptureJob(child);
                using var captureStop=CancellationTokenSource.CreateLinkedTokenSource(stop);
                var stderr=DrainErrors(child.StandardError,captureStop.Token);
                var poll=Poll(path,captureStop.Token);
                var parser=new PresentCsv();
                bool headerSeen=false;
                try {
                    while(await child.StandardOutput.ReadLineAsync(stop) is {} line) {
                        var frame=parser.Parse(line);
                        if(line.StartsWith("Application,",StringComparison.OrdinalIgnoreCase))headerSeen=true;
                        if(frame!=null)lock(gate)history.Add(frame,DateTimeOffset.UtcNow);
                    }
                    throw new IOException(headerSeen?"PresentMon завершился — повторный запуск через 15 с":"Нет потока PresentMon — проверьте права администратора");
                } finally {captureStop.Cancel();try{await Task.WhenAll(stderr,poll);}catch(OperationCanceledException){}}
            } catch(OperationCanceledException)when(stop.IsCancellationRequested){break;}
            catch(Exception ex){lock(gate){history.Clear();detector.Reset();}Volatile.Write(ref view,new(false,ex.Message));}
            finally {if(child!=null){try{if(!child.HasExited)child.Kill(true);}catch{}child.Dispose(); StopOwnSession();}}
            try{await Task.Delay(15000,stop);}catch(OperationCanceledException){break;}
        }
    }
    private static void StopOwnSession()
    {
        try {
            var start=new ProcessStartInfo(Path.Combine(AppContext.BaseDirectory,"PresentMon.exe")) {UseShellExecute=false,CreateNoWindow=true};
            foreach(var arg in new[]{"--session_name",$"LibreHwTab-{Process.GetCurrentProcess().SessionId}","--terminate_existing_session"})start.ArgumentList.Add(arg);
            using var cleanup=Process.Start(start);
            if(cleanup!=null && !cleanup.WaitForExit(3000))cleanup.Kill();
        } catch { }
    }
    private static async Task DrainErrors(StreamReader reader,CancellationToken stop) {while(await reader.ReadLineAsync(stop)!=null){} }
    private async Task Poll(string rulesPath,CancellationToken stop)
    {
        while(!stop.IsCancellationRequested) {
            try {rules=JsonSerializer.Deserialize<GameRules>(await File.ReadAllTextAsync(rulesPath,stop),new JsonSerializerOptions{PropertyNameCaseInsensitive=true}) ?? new();}catch(OperationCanceledException)when(stop.IsCancellationRequested){throw;}catch{}
            var app=ForegroundWindow.Read();var now=DateTimeOffset.UtcNow;
            GameView next;
            lock(gate)next=detector.Update(app,app==null?null:history.ForProcess(app.Pid,now),now,ForegroundWindow.Alive(detector.ActivePid),rules);
            Volatile.Write(ref view,next);
            await Task.Delay(1000,stop);
        }
    }
}
