## 8.5\. Consistent Performance

Performance can fluctuate dramatically for high-performance long-running apps,
either because of the other apps running in the background or the CPU throttling
due to temperature limits. Android includes programmatic interfaces so that when
the device is capable, the top foreground application can request that the
system optimize the allocation of the resources to address such fluctuations.

Device implementations:

*    [C-0-1] MUST report the support of Sustained Performance Mode accurately
through the [`PowerManager.isSustainedPerformanceModeSupported()`](
https://developer.android.com/reference/android/os/PowerManager.html#isSustainedPerformanceModeSupported%28%29)
API method.

*   SHOULD support Sustained Performance Mode.

If device implementations report support of Sustained Performance Mode, they:

*   [C-1-1] MUST provide the top foreground application a consistent level of
performance for at least 30 minutes, when the app requests it.
*   [C-1-2] MUST honor the [`Window.setSustainedPerformanceMode()`](
https://developer.android.com/reference/android/view/Window.html#setSustainedPerformanceMode%28boolean%29)
API and other related APIs.

If device implementations include two or more CPU cores, they:

*   SHOULD provide at least one exclusive core that can be reserved by the top
foreground application.

If device implementations support reserving one exclusive core for the top
foreground application, they:

*    [C-2-1] MUST report through the [`Process.getExclusiveCores()`](https://developer.android.com/reference/android/os/Process.html#getExclusiveCores%28%29)
     API method the ID numbers of the exclusive cores that can be reserved
     by the top foreground application.
*    [C-2-2] MUST not allow any user space processes except the device drivers
     used by the application to run on the exclusive cores, but MAY allow some
     kernel processes to run as necessary.

If device implementations do not support an exclusive core, they:

*    [C-3-1] MUST return an empty list through the
[`Process.getExclusiveCores()`](
https://developer.android.com/reference/android/os/Process.html#getExclusiveCores%28%29)
     API method.