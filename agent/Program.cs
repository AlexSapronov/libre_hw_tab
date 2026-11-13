using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using LibreHardwareMonitor.Hardware;

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
app.MapGet("/api/v1/telemetry", IResult (Telemetry telemetry) => telemetry.Snapshot is { } snapshot
    ? Results.Json(snapshot) : Results.StatusCode(503));
Console.WriteLine("LIBRE HW TAB — Windows companion");
Console.WriteLine("Enter an address and the pairing key in the tablet app:");
foreach (var nic in NetworkInterface.GetAllNetworkInterfaces().Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback))
    foreach (var ip in nic.GetIPProperties().UnicastAddresses.Where(a => a.Address.AddressFamily == AddressFamily.InterNetwork))
        Console.WriteLine($"  http://{ip.Address}:{config.Port}");
Console.WriteLine($"Pairing key: {config.Token}");
Console.WriteLine($"Connection saved in {configPath}");
await app.RunAsync();

record Connection(int Port, string Token);
record Device(string Name, float? Temperature, float? Load, float? Clock, float? Power, float? Fan, float? Used, float? Total);
record Snapshot(int Version, DateTimeOffset Timestamp, string Host, Device? Cpu, Device? Gpu, Device? Memory, string? SensorError);
sealed class Telemetry : BackgroundService
{
    private Snapshot? snapshot;
    public Snapshot? Snapshot => Volatile.Read(ref snapshot);
    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        var computer = new Computer { IsCpuEnabled = true, IsGpuEnabled = true, IsMemoryEnabled = true };
        try { computer.Open(); }
        catch (Exception ex) { Volatile.Write(ref snapshot, new(1, DateTimeOffset.UtcNow, Environment.MachineName, null, null, null, ex.Message)); return; }
        while (!stop.IsCancellationRequested)
        {
            try
            {
                foreach (var hardware in computer.Hardware) Update(hardware);
                var cpu = computer.Hardware.FirstOrDefault(h => h.HardwareType == HardwareType.Cpu);
                var gpu = computer.Hardware.FirstOrDefault(h => h.HardwareType == HardwareType.GpuNvidia)
                    ?? computer.Hardware.FirstOrDefault(h => h.HardwareType == HardwareType.GpuAmd)
                    ?? computer.Hardware.FirstOrDefault(h => h.HardwareType == HardwareType.GpuIntel);
                var ram = computer.Hardware.FirstOrDefault(h => h.HardwareType == HardwareType.Memory);
                Volatile.Write(ref snapshot, new(1, DateTimeOffset.UtcNow, Environment.MachineName, Read(cpu), Read(gpu), Read(ram), null));
            }
            catch (Exception ex)
            {
                // Do not retain stale sensor values after a failed sample.
                Volatile.Write(ref snapshot, new(1, DateTimeOffset.UtcNow, Environment.MachineName, null, null, null, ex.Message));
            }
            try { await Task.Delay(1000, stop); } catch (OperationCanceledException) { break; }
        }
        computer.Close();
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
