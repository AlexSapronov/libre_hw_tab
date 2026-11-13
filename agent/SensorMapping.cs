using LibreHardwareMonitor.Hardware;

internal record FanReading(string Id, string Name, string Hardware, string Board, float? Rpm);
internal record Cooling(FanReading? Radiator, FanReading? Side, FanReading? Pump, FanReading[] Fans, bool WorkstationProfile);

internal static class SensorMapping
{
    // In LHM 0.9.6 VirtualMemory (/vram) is enumerated BEFORE physical TotalMemory (/ram).
    internal static IHardware? PhysicalMemory(IEnumerable<IHardware> hardware) => hardware
        .FirstOrDefault(h => h.HardwareType == HardwareType.Memory && h.Identifier.ToString() == "/ram");

    internal static Cooling ReadCooling(IEnumerable<IHardware> roots)
    {
        var fans = new List<FanReading>();
        foreach (var root in roots.Where(h => h.HardwareType == HardwareType.Motherboard)) Collect(root, root.Name, fans);
        return SelectCooling(fans.ToArray());
    }
    private static void Collect(IHardware hardware, string board, List<FanReading> fans)
    {
        foreach (var sensor in hardware.Sensors.Where(s => s.SensorType == SensorType.Fan))
            fans.Add(new(sensor.Identifier.ToString(), sensor.Name, hardware.Name, board,
                sensor.Value is float rpm && float.IsFinite(rpm) && rpm >= 0 ? rpm : null));
        foreach (var child in hardware.SubHardware) Collect(child, board, fans);
    }
    internal static Cooling SelectCooling(FanReading[] fans)
    {
        // Confirmed against the user's LHM + Fan Control screenshots. Scope the numbered
        // mapping to this board + SuperIO; do not guess what Fan #N means on other boards.
        var workstation = fans.Where(f => f.Board.Contains("TUF GAMING B650M-PLUS", StringComparison.OrdinalIgnoreCase)
            && f.Hardware.Contains("NCT6799D", StringComparison.OrdinalIgnoreCase)).ToArray();
        if (workstation.Length > 0)
            return new(Find(workstation, "Fan #1"), Find(workstation, "Fan #2"), Find(workstation, "Fan #7"), fans, true);
        return new(Find(fans, "CPU Fan", "CPU Fan #1", "CPU_FAN"), null,
            Find(fans, "AIO Pump", "AIO_PUMP", "Water Pump", "Pump Fan", "CPU Pump Fan"), fans, false);
    }
    private static FanReading? Find(IEnumerable<FanReading> fans, params string[] names)
    {
        foreach (var name in names)
        {
            var matches = fans.Where(f => f.Name.Equals(name, StringComparison.OrdinalIgnoreCase)).ToArray();
            if (matches.Length == 1) return matches[0];
        }
        return null; // Unknown/ambiguous channel is not substituted with the fastest fan.
    }
}
