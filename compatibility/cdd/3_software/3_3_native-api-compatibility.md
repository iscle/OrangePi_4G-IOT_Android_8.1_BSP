## 3.3\. Native API Compatibility

Device implementers are:

Native code compatibility is challenging. For this reason,
device implementers are:

*   [SR] STRONGLY RECOMMENDED to use the implementations of the libraries
listed below from the upstream Android Open Source Project.

### 3.3.1\. Application Binary Interfaces

Managed Dalvik bytecode can call into native code provided in the application
`.apk` file as an ELF `.so` file compiled for the appropriate device hardware
architecture. As native code is highly dependent on the underlying processor
technology, Android defines a number of Application Binary Interfaces (ABIs) in
the Android NDK.

Device implementations:

*   [C-0-1] MUST be compatible with one or more defined ABIs and implement
    compatibility with the Android NDK.
*   [C-0-2] MUST include support for code running in the managed environment to
    call into native code, using the standard Java Native Interface (JNI)
    semantics.
*   [C-0-3] MUST be source-compatible (i.e. header-compatible) and
    binary-compatible (for the ABI) with each required library in the list
    below.
*   [C-0-4] MUST support the equivalent 32-bit ABI if any 64-bit ABI is
    supported.
*   [C-0-5]  MUST accurately report the native Application Binary Interface
    (ABI) supported by the device, via the `android.os.Build.SUPPORTED_ABIS`,
    `android.os.Build.SUPPORTED_32_BIT_ABIS`, and
    `android.os.Build.SUPPORTED_64_BIT_ABIS` parameters, each a comma separated
    list of ABIs ordered from the most to the least preferred one.
*   [C-0-6] MUST report, via the above parameters, only those ABIs documented
    and described in the latest version of the
    [Android NDK ABI Management documentation](
    https://developer.android.com/ndk/guides/abis.html), and MUST include
    support for the [Advanced SIMD](
    http://infocenter.arm.com/help/index.jsp?topic=/com.arm.doc.ddi0388f/Beijfcja.html)
    (a.k.a. NEON) extension.
*   [C-0-7] MUST make all the following libraries, providing native APIs,
    available to apps that include native code:

    *   libaaudio.so (AAudio native audio support)
    *   libandroid.so (native Android activity support)
    *   libc (C library)
    *   libcamera2ndk.so
    *   libdl (dynamic linker)
    *   libEGL.so (native OpenGL surface management)
    *   libGLESv1\_CM.so (OpenGL ES 1.x)
    *   libGLESv2.so (OpenGL ES 2.0)
    *   libGLESv3.so (OpenGL ES 3.x)
    *   libicui18n.so
    *   libicuuc.so
    *   libjnigraphics.so
    *   liblog (Android logging)
    *   libmediandk.so (native media APIs support)
    *   libm (math library)
    *   libOpenMAXAL.so (OpenMAX AL 1.0.1 support)
    *   libOpenSLES.so (OpenSL ES 1.0.1 audio support)
    *   libRS.so
    *   libstdc++ (Minimal support for C++)
    *   libvulkan.so (Vulkan)
    *   libz (Zlib compression)
    *   JNI interface

*   [C-0-8] MUST NOT add or remove the public functions for the native libraries
    listed above.
*   [C-0-9] MUST list additional non-AOSP libraries exposed directly to
    third-party apps in `/vendor/etc/public.libraries.txt`.
*   [C-0-10] MUST NOT expose any other native libraries, implemented and
    provided in AOSP as system libraries, to third-party apps targeting API
    level 24 or higher as they are reserved.
*   [C-0-11] MUST export all the OpenGL ES 3.1 and [Android Extension Pack](
    http://developer.android.com/guide/topics/graphics/opengl.html#aep)
    function symbols, as defined in the NDK, through the `libGLESv3.so` library.
    Note that while all the symbols MUST be present, section 7.1.4.1 describes
    in more detail the requirements for when the full implementation of each
    corresponding functions are expected.
*   [C-0-12] MUST export function symbols for the core Vulkan 1.0 function
    symobls, as well as the `VK_KHR_surface`, `VK_KHR_android_surface`,
    `VK_KHR_swapchain`, `VK_KHR_maintenance1`, and
    `VK_KHR_get_physical_device_properties2` extensions through the
    `libvulkan.so` library.  Note that while all the symbols MUST be present,
    section 7.1.4.2 describes in more detail the requirements for when the full
    implementation of each corresponding functions are expected.
*   SHOULD be built using the source code and header files available in the
    upstream Android Open Source Project

Note that future releases of the Android NDK may introduce support for
additional ABIs.

### 3.3.2. 32-bit ARM Native Code Compatibility

If device implementations are 64-bit ARM devices, then:

*    [C-1-1] Although the ARMv8 architecture deprecates several CPU operations,
     including some operations used in existing native code, the following
     deprecated operations MUST remain available to 32-bit native ARM code,
     either through native CPU support or through software emulation:

     *   SWP and SWPB instructions
     *   SETEND instruction
     *   CP15ISB, CP15DSB, and CP15DMB barrier operations

If device implementations include a 32-bit ARM ABI, they:

*    [C-2-1] MUST include the following lines in `/proc/cpuinfo` when it is read
     by 32-bit ARM applications to ensure compatibility with applications built
     using legacy versions of Android NDK.

     *   `Features: `, followed by a list of any optional ARMv7 CPU features
     supported by the device.
     *   `CPU architecture: `, followed by an integer describing the device's
     highest supported ARM architecture (e.g., "8" for ARMv8 devices).

*    SHOULD not alter `/proc/cpuinfo` when read by 64-bit ARM or non-ARM
     applications.