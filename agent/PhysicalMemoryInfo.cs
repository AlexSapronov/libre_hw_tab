using System.Runtime.InteropServices;

internal static class PhysicalMemoryInfo
{
    // Installed DIMM capacity, not commit limit, page file, or OS-usable memory.
    internal static float? InstalledGiB()
    {
        if (!OperatingSystem.IsWindows()) return null;
        return GetPhysicallyInstalledSystemMemory(out var kib) && kib > 0 ? kib / (1024f * 1024f) : null;
    }
    internal static Device? WithInstalledCapacity(Device? physical, float? installed)
    {
        if (physical is null) return null;
        return physical with {
            UsableTotal = physical.Total,
            Total = installed is float value && float.IsFinite(value) && value > 0 ? value : physical.Total
        };
    }
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetPhysicallyInstalledSystemMemory(out ulong totalMemoryInKilobytes);
}
