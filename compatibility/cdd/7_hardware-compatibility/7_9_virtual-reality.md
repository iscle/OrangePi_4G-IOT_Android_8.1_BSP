## 7.9\. Virtual Reality

Android includes APIs and facilities to build "Virtual Reality" (VR)
applications including high quality mobile VR experiences. Device
implementations MUST properly implement these APIs and behaviors,
as detailed in this section.

### 7.9.1\. Virtual Reality Mode

Android includes support for [VR Mode](
https://developer.android.com/reference/android/app/Activity.html#setVrModeEnabled%28boolean, android.content.ComponentName%29),
a feature which handles stereoscopic rendering of notifications and disables
monocular system UI components while a VR application has user focus.

If Handheld device implementations include support for the VR mode, they:

*   [H-1-1] MUST declare the `android.software.vr.mode` feature.

If device implementations declare `android.software.vr.mode` feature, they:

*   [H-2-1] MUST include an application implementing
`android.service.vr.VrListenerService`
that can be enabled by VR applications via
`android.app.Activity#setVrModeEnabled`.

### 7.9.2\. Virtual Reality High Performance


If Handheld device implementations are capable of meeting all the requirements
to declare the `android.hardware.vr.high_performance` feature flag, they:

*   [H-1-1] MUST declare the `android.hardware.vr.high_performance`
feature flag.

If device implementations identify the support of high performance VR
for longer user periods through the `android.hardware.vr.high_performance`
feature flag, they:

*   [C-1-1] MUST have at least 2 physical cores.
*   [C-1-2] MUST declare `android.software.vr.mode feature`.
*   [C-1-3] MUST support sustained performance mode.
*   [C-1-4] MUST support OpenGL ES 3.2.
*   [C-1-5] MUST support Vulkan Hardware Level 0 and SHOULD support
    Vulkan Hardware Level 1.
*   [C-1-6] MUST implement
    [`EGL_KHR_mutable_render_buffer`](https://www.khronos.org/registry/EGL/extensions/KHR/EGL_KHR_mutable_render_buffer.txt),
    [`EGL_ANDROID_front_buffer_auto_refresh`](https://www.khronos.org/registry/EGL/extensions/ANDROID/EGL_ANDROID_front_buffer_auto_refresh.txt),
    [`EGL_ANDROID_get_native_client_buffer`](https://www.khronos.org/registry/EGL/extensions/ANDROID/EGL_ANDROID_get_native_client_buffer.txt),
    [`EGL_KHR_fence_sync`](https://www.khronos.org/registry/EGL/extensions/KHR/EGL_KHR_fence_sync.txt),
    [`EGL_KHR_wait_sync`](https://www.khronos.org/registry/EGL/extensions/KHR/EGL_KHR_wait_sync.txt),
    [`EGL_IMG_context_priority`](https://www.khronos.org/registry/EGL/extensions/IMG/EGL_IMG_context_priority.txt),
    [`EGL_EXT_protected_content`](https://www.khronos.org/registry/EGL/extensions/EXT/EGL_EXT_protected_content.txt),
    and expose the extensions in the list of available EGL extensions.
*   [C-1-7] The GPU and display MUST be able to synchronize access to the shared
front buffer such that alternating-eye rendering of VR content at 60fps with two
render contexts will be displayed with no visible tearing artifacts.
*   [C-1-8] MUST implement
    [`GL_EXT_multisampled_render_to_texture`](https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_multisampled_render_to_texture.txt),
    [`GL_OVR_multiview`](https://www.khronos.org/registry/OpenGL/extensions/OVR/OVR_multiview.txt),
    [`GL_OVR_multiview2`](https://www.khronos.org/registry/OpenGL/extensions/OVR/OVR_multiview2.txt),
    [`GL_OVR_multiview_multisampled_render_to_texture`](https://www.khronos.org/registry/OpenGL/extensions/OVR/OVR_multiview_multisampled_render_to_texture.txt),
    [`GL_EXT_protected_textures`](https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_protected_textures.txt),
    and expose the extensions in the list of available GL extensions.
*   [C-1-9] MUST implement support for [`AHardwareBuffer`](https://developer.android.com/ndk/reference/hardware__buffer_8h.html)
    flags `AHARDWAREBUFFER_USAGE_GPU_DATA_BUFFER` and
    `AHARDWAREBUFFER_USAGE_SENSOR_DIRECT_DATA` as
    described in the NDK.
*   [C-1-10] MUST implement support for `AHardwareBuffers` with more than one
layer.
*   [C-1-11] MUST support H.264 decoding at least 3840x2160@30fps-40Mbps
(equivalent to 4 instances of 1920x1080@30fps-10Mbps or 2 instances of
1920x1080@60fps-20Mbps).
*   [C-1-12] MUST support HEVC and VP9, MUST be capable to decode at least
    1920x1080@30fps-10Mbps and SHOULD be capable to decode
    3840x2160@30fps-20Mbps (equivalent to
    4 instances of 1920x1080@30fps-5Mbps).
*   [C-1-13] MUST support `HardwarePropertiesManager.getDeviceTemperatures` API
and return accurate values for skin temperature.
*   [C-1-14] MUST have an embedded screen, and its resolution MUST be at least be
    FullHD(1080p) and STRONGLY RECOMMENDED TO BE  be QuadHD (1440p) or higher.
*   [C-1-15] The display MUST measure between 4.7" and 6.3" diagonal.
*   [C-1-16] The display MUST update at least 60 Hz while in VR Mode.
*   [C-1-17] The display latency on Gray-to-Gray, White-to-Black, and
Black-to-White switching time MUST be ≤ 3 ms.
*   [C-1-18] The display MUST support a low-persistence mode with ≤5 ms
persistence, persistence being defined as the amount of time for
which a pixel is emitting light.
*   [C-1-19] MUST support Bluetooth 4.2 and Bluetooth LE Data Length Extension
    [section 7.4.3](#7_4_3_bluetooth).
*   [SR] STRONGLY RECOMMENDED to support
    `android.hardware.sensor.hifi_sensors` feature and MUST meet the gyroscope,
    accelerometer, and magnetometer related requirements for
    `android.hardware.hifi_sensors`.
*   MAY provide an exclusive core to the foreground
    application and MAY support the `Process.getExclusiveCores` API to return
    the numbers of the cpu cores that are exclusive to the top foreground
    application. If exclusive core is supported then the core MUST not allow
    any other userspace processes to run on it (except device drivers used
    by the application), but MAY allow some kernel processes to run as
    necessary.
