## 3.1\. Managed API Compatibility

The managed Dalvik bytecode execution environment is the primary vehicle for
Android applications. The Android application programming interface (API) is the
set of Android platform interfaces exposed to applications running in the
managed runtime environment.

*    [C-0-1] Device implementations MUST provide complete implementations,
including all documented behaviors, of any documented API exposed by the
[Android SDK](http://developer.android.com/reference/packages.html)
or any API decorated with the “@SystemApi” marker in the upstream Android
source code.

*    [C-0-2] Device implementations MUST support/preserve all classes,
methods, and associated elements marked by the TestApi annotation (@TestApi).

*    [C-0-3] Device implementations MUST NOT omit any managed APIs, alter
API interfaces or signatures, deviate from the documented behavior, or include
no-ops, except where specifically allowed by this Compatibility Definition.

*    [C-0-4]  Device implementations MUST still keep the APIs present and behave
     in a reasonable way, even when some hardware features for which Android
     includes APIs are omitted. See [section 7](#7_hardware_compatibility)
     for specific requirements for this scenario.

## 3.1.1\. Android Extensions

Android includes the support of extending the managed APIs while keeping the
same API level version.

*   [C-0-1] Android device implementations MUST preload the AOSP implementation
of both the shared library `ExtShared` and services `ExtServices` with versions
higher than or equal to the minimum versions allowed per each API level.
For example, Android 7.0 device implementations, running API level 24 MUST
include at least version 1.
