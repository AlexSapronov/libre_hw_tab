using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

// Windows closes this job handle even if the agent is killed by the installer or crashes.
// Only the bundled PresentMon child is assigned; other monitors/ETW sessions are untouched.
internal sealed class CaptureJob : IDisposable
{
    private readonly SafeFileHandle handle;
    internal CaptureJob(Process child)
    {
        handle=CreateJobObject(IntPtr.Zero,null);
        if(handle.IsInvalid) throw new Win32Exception();
        var info=new ExtendedLimit {Basic=new BasicLimit {Flags=0x2000}};
        if(!SetInformationJobObject(handle,9,ref info,(uint)Marshal.SizeOf<ExtendedLimit>()) || !AssignProcessToJobObject(handle,child.Handle)) {
            handle.Dispose();throw new Win32Exception();
        }
    }
    public void Dispose()=>handle.Dispose();
    [StructLayout(LayoutKind.Sequential)] private struct BasicLimit {public long ProcessTime,JobTime;public uint Flags;public UIntPtr Min,Max;public uint Active;public UIntPtr Affinity;public uint Priority,Scheduling;}
    [StructLayout(LayoutKind.Sequential)] private struct IoCounters {public ulong ReadOps,WriteOps,OtherOps,ReadBytes,WriteBytes,OtherBytes;}
    [StructLayout(LayoutKind.Sequential)] private struct ExtendedLimit {public BasicLimit Basic;public IoCounters Io;public UIntPtr ProcessMemory,JobMemory,PeakProcess,PeakJob;}
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] private static extern SafeFileHandle CreateJobObject(IntPtr attributes,string? name);
    [DllImport("kernel32.dll",SetLastError=true)] [return:MarshalAs(UnmanagedType.Bool)] private static extern bool SetInformationJobObject(SafeFileHandle job,int infoClass,ref ExtendedLimit info,uint size);
    [DllImport("kernel32.dll",SetLastError=true)] [return:MarshalAs(UnmanagedType.Bool)] private static extern bool AssignProcessToJobObject(SafeFileHandle job,IntPtr process);
}
