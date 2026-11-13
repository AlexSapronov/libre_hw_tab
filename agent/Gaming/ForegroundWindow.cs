using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

internal static class ForegroundWindow
{
    internal static ForegroundApp? Read()
    {
        try {
            var hwnd=GetForegroundWindow(); if(hwnd==IntPtr.Zero) return null;
            GetWindowThreadProcessId(hwnd,out var pid);
            using var process=Process.GetProcessById((int)pid);
            string path=""; try {path=process.MainModule?.FileName ?? "";} catch {}
            var title=new StringBuilder(512); GetWindowText(hwnd,title,title.Capacity);
            var info=new MonitorInfo {Size=Marshal.SizeOf<MonitorInfo>()};
            bool full=GetWindowRect(hwnd,out var rect) && GetMonitorInfo(MonitorFromWindow(hwnd,2),ref info)
                && rect.Right-rect.Left >= (info.Monitor.Right-info.Monitor.Left)*.8
                && rect.Bottom-rect.Top >= (info.Monitor.Bottom-info.Monitor.Top)*.8;
            return new((int)pid,process.ProcessName+".exe",path,title.ToString(),full);
        } catch {return null;}
    }
    internal static bool Alive(int? pid) {try {if(pid==null)return false;using var p=Process.GetProcessById(pid.Value);return !p.HasExited;}catch{return false;}}
    [StructLayout(LayoutKind.Sequential)] private struct Rect {public int Left,Top,Right,Bottom;}
    [StructLayout(LayoutKind.Sequential)] private struct MonitorInfo {public int Size;public Rect Monitor,Work;public uint Flags;}
    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd,out uint pid);
    [DllImport("user32.dll",CharSet=CharSet.Unicode)] private static extern int GetWindowText(IntPtr hwnd,StringBuilder text,int count);
    [DllImport("user32.dll")] [return:MarshalAs(UnmanagedType.Bool)] private static extern bool GetWindowRect(IntPtr hwnd,out Rect rect);
    [DllImport("user32.dll")] private static extern IntPtr MonitorFromWindow(IntPtr hwnd,uint flags);
    [DllImport("user32.dll",CharSet=CharSet.Unicode)] [return:MarshalAs(UnmanagedType.Bool)] private static extern bool GetMonitorInfo(IntPtr monitor,ref MonitorInfo info);
}
