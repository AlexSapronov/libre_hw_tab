using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using LibreHardwareMonitor.Hardware;

if (args.Contains("--pair") || args.Contains("--pair-test"))
{
    var thread = new Thread(() => PairingWindow.Run(args.Contains("--pair-test")));
    thread.SetApartmentState(ApartmentState.STA);
    thread.Start();
    thread.Join();
    return;
}

using var singleton = new Mutex(true, "Local\\LibreHwTabAgent", out var first);
if (!first) return;
var dataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LibreHwTab");
Directory.CreateDirectory(dataDir);
var configPath = Path.Combine(dataDir, "connection.json");
var config = File.Exists(configPath)
    ? JsonSerializer.Deserialize<Connection>(File.ReadAllText(configPath))!
    : new Connection(8750, Convert.ToHexString(RandomNumberGenerator.GetBytes(16)).ToLowerInvariant());
if (config.Port is < 1024 or > 65535 || config.Token.Length < 16) throw new InvalidOperationException("Invalid connection.json");
File.WriteAllText(configPath, JsonSerializer.Serialize(config, new JsonSerializerOptions { WriteIndented = true }));
var builder = WebApplication.CreateBuilder(args);
builder.WebHost.UseUrls($"http://0.0.0.0:{config.Port}");
// Never log URLs: they can contain a pairing token.
builder.Logging.ClearProviders();
builder.Services.AddSingleton<Telemetry>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<Telemetry>());
builder.Services.AddSingleton<GamingService>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<GamingService>());
builder.Services.AddSingleton<MediaService>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<MediaService>());
builder.Services.AddSingleton<AudioService>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<AudioService>());
var app = builder.Build();
app.Use(async (context, next) =>
{
    context.Response.Headers.CacheControl = "no-store";
    var supplied = Encoding.UTF8.GetBytes(context.Request.Headers["X-Libre-Token"].ToString());
    var expected = Encoding.UTF8.GetBytes(config.Token);
    if (!CryptographicOperations.FixedTimeEquals(supplied, expected))
    {
        context.Response.StatusCode = 401;
        return;
    }
    await next(context);
});
app.MapGet("/api/v1/telemetry", IResult (Telemetry telemetry, GamingService gaming, MediaService media, AudioService audio) => telemetry.Snapshot is { } snapshot
    ? Results.Json(snapshot with { Game = gaming.Current, WakeTargets = NetworkWake.Targets.Value, Media = media.Current, Audio = audio.Current }) : Results.StatusCode(503));
app.MapGet("/api/v1/media/{id}/thumbnail", IResult (string id,MediaService media)=>media.Thumbnail(id) is {} image?Results.Bytes(image,"image/jpeg"):Results.NotFound());
app.MapPost("/api/v1/media/control", async Task<IResult> (MediaCommand command,MediaService media)=>await media.Control(command)?Results.Ok():Results.Conflict());
app.MapPost("/api/v1/audio/control", IResult (AudioCommand command,AudioService audio)=> {
    try{return audio.Control(command) is {} result?Results.Json(result):Results.Conflict();}
    catch{return Results.StatusCode(503);}
});
Console.WriteLine("LIBRE HW TAB — Windows companion");
Console.WriteLine("Enter an address and the pairing key in the tablet app:");
foreach (var nic in NetworkInterface.GetAllNetworkInterfaces().Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback))
    foreach (var ip in nic.GetIPProperties().UnicastAddresses.Where(a => a.Address.AddressFamily == AddressFamily.InterNetwork))
        Console.WriteLine($"  http://{ip.Address}:{config.Port}");
Console.WriteLine($"Pairing key: {config.Token}");
Console.WriteLine($"Connection saved in {configPath}");
await app.RunAsync();

record Connection(int Port, string Token);
record Device(string Name, float? Temperature, float? Load, float? Clock, float? Power, float? Fan, float? Used, float? Total, float? Pump = null, float? SideFan = null, string? FanName = null, string? PumpName = null, bool CoolingProfile = false, float? UsableTotal = null);
record Snapshot(int Version, DateTimeOffset Timestamp, string Host, Device? Cpu, Device? Gpu, Device? Memory, string? SensorError, Cooling? Cooling = null, GameView? Game = null, WakeTarget[]? WakeTargets = null, MediaView? Media = null, AudioView? Audio = null);
sealed class Telemetry : BackgroundService
{
    private Snapshot? snapshot;
    public Snapshot? Snapshot => Volatile.Read(ref snapshot);
    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        var installedMemory = PhysicalMemoryInfo.InstalledGiB();
        var computer = new Computer { IsCpuEnabled = true, IsGpuEnabled = true, IsMemoryEnabled = true, IsMotherboardEnabled = true };
        try { computer.Open(); }
        catch (Exception ex) { Volatile.Write(ref snapshot, new(1, DateTimeOffset.UtcNow, Environment.MachineName, null, null, null, ex.Message)); return; }
        DateTimeOffset nextBoard=DateTimeOffset.MinValue;
        using var sensorTimer=new PeriodicTimer(TimeSpan.FromMilliseconds(500));
        while (!stop.IsCancellationRequested)
        {
            try
            {
                bool boardDue=DateTimeOffset.UtcNow>=nextBoard;
                if(boardDue)nextBoard=DateTimeOffset.UtcNow.AddSeconds(1);
                foreach (var hardware in computer.Hardware) {
                    if(hardware.HardwareType==HardwareType.Motherboard && !boardDue)continue;
                    if(hardware.HardwareType==HardwareType.Memory && hardware.Identifier.ToString()!="/ram")continue;
                    Update(hardware);
                }
                var cpu = computer.Hardware.FirstOrDefault(h => h.HardwareType == HardwareType.Cpu);
                var gpu = computer.Hardware.FirstOrDefault(h => h.HardwareType == HardwareType.GpuNvidia)
                    ?? computer.Hardware.FirstOrDefault(h => h.HardwareType == HardwareType.GpuAmd)
                    ?? computer.Hardware.FirstOrDefault(h => h.HardwareType == HardwareType.GpuIntel);
                var ram = SensorMapping.PhysicalMemory(computer.Hardware);
                var cooling = SensorMapping.ReadCooling(computer.Hardware);
                var processor = Read(cpu);
                if (processor != null) processor = processor with {
                    Fan = cooling.Radiator?.Rpm, Pump = cooling.Pump?.Rpm, SideFan = cooling.Side?.Rpm,
                    FanName = cooling.Radiator?.Name, PumpName = cooling.Pump?.Name, CoolingProfile = cooling.WorkstationProfile
                };
                Volatile.Write(ref snapshot, new(1, DateTimeOffset.UtcNow, Environment.MachineName, processor, Read(gpu), PhysicalMemoryInfo.WithInstalledCapacity(Read(ram), installedMemory), null, cooling));
            }
            catch (Exception ex)
            {
                // Do not retain stale sensor values after a failed sample.
                Volatile.Write(ref snapshot, new(1, DateTimeOffset.UtcNow, Environment.MachineName, null, null, null, ex.Message));
            }
            try { if(!await sensorTimer.WaitForNextTickAsync(stop))break; } catch (OperationCanceledException) { break; }
        }
        // LHM 0.9.6 SuperIOHardware.Close restores control modes even for a read-only
        // monitor. Do not invoke that path alongside Fan Control. This worker lives
        // for the entire agent process; Windows reclaims its handles when it exits.
        // No Sensor.Control / SetSoftware / SetDefault calls are made by this agent.
    }
    private static void Update(IHardware hardware)
    {
        hardware.Update();
        foreach (var child in hardware.SubHardware) Update(child);
    }
    private static Device? Read(IHardware? hardware)
    {
        if (hardware is null) return null;
        float? Sensor(SensorType type, params string[] names)
        {
            foreach (var name in names)
            {
                var s = hardware.Sensors.FirstOrDefault(s => s.SensorType == type && s.Name.Equals(name, StringComparison.OrdinalIgnoreCase));
                if (s?.Value is float value && float.IsFinite(value)) return value;
            }
            return null;
        }
        float? Max(SensorType type)
        {
            var values = hardware.Sensors.Where(s => s.SensorType == type && s.Value.HasValue && float.IsFinite(s.Value.Value)).Select(s => s.Value!.Value).ToArray();
            return values.Length == 0 ? null : values.Max();
        }
        var isCpu = hardware.HardwareType == HardwareType.Cpu;
        var used = Sensor(SensorType.Data, "Memory Used");
        var available = Sensor(SensorType.Data, "Memory Available");
        return new(hardware.Name,
            Sensor(SensorType.Temperature, isCpu ? "Core (Tctl/Tdie)" : "GPU Core", "CPU Package", "Core Average") ?? Max(SensorType.Temperature),
            Sensor(SensorType.Load, "CPU Total", "GPU Core", "Memory"),
            Sensor(SensorType.Clock, "GPU Core") ?? Max(SensorType.Clock),
            Sensor(SensorType.Power, "CPU Package", "Package", "GPU Package", "GPU Power", "GPU Board Power"),
            Max(SensorType.Fan), used, used.HasValue && available.HasValue ? used + available : null);
    }
}
