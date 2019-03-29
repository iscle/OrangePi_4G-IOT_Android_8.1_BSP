## 7.1\. Display and Graphics

Android includes facilities that automatically adjust application assets and UI
layouts appropriately for the device to ensure that third-party applications
run well on a [variety of hardware configurations](http://developer.android.com/guide/practices/screens_support.html).
Devices MUST properly implement these APIs and behaviors, as detailed in this
section.

The units referenced by the requirements in this section are defined as follows:

*   **physical diagonal size**. The distance in inches between two opposing
corners of the illuminated portion of the display.
*   **dots per inch (dpi)**. The number of pixels encompassed by a linear
horizontal or vertical span of 1”. Where dpi values are listed, both horizontal
and vertical dpi must fall within the range.
*   **aspect ratio**. The ratio of the pixels of the longer dimension to the
shorter dimension of the screen. For example, a display of 480x854 pixels would
be 854/480 = 1.779, or roughly “16:9”.
*   **density-independent pixel (dp)**. The virtual pixel unit normalized to a
160 dpi screen, calculated as: pixels = dps * (density/160).

### 7.1.1\. Screen Configuration

#### 7.1.1.1\. Screen Size

*   [H-0-1] Handheld device implementations MUST have a screen at least 2.5
    inches in physical diagonal size.

*   [A-0-1] Android Automotive devices MUST have a screen at least 6 inches
    in physical diagonal size.

*   [A-0-2] Android Automotive devices MUST have a screen size layout of
at least 750 dp x 480 dp.

*   [W-0-1] Android Watch device implementations MUST have a screen with the
    physical diagonal size in the range from 1.1 to 2.5 inches.

The Android UI framework supports a variety of different logical screen layout
sizes, and allows applications to query the current configuration's screen
layout size via `Configuration.screenLayout` with the `SCREENLAYOUT_SIZE_MASK`
and `Configuration.smallestScreenWidthDp`.

*    [C-0-1] Device implementations MUST report the correct layout size for the
 `Configuration.screenLayout` as defined in the Android SDK documentation.
 Specifically, device implementations MUST report the correct logical
 density-independent pixel (dp) screen dimensions as below:

     *   Devices with the `Configuration.uiMode` set as any value other than
     UI_MODE_TYPE_WATCH, and reporting a `small` size for the
     `Configuration.screenLayout`, MUST have at least 426 dp x 320 dp.
     *   Devices reporting a `normal` size for the `Configuration.screenLayout`,
     MUST have at least 480 dp x 320 dp.
     *   Devices reporting a `large` size for the `Configuration.screenLayout`,
     MUST have at least 640 dp x 480 dp.
     *   Devices reporting a `xlarge` size for the `Configuration.screenLayout`,
     MUST have at least 960 dp x 720 dp.

*   [C-0-2] Device implementations MUST correctly honor applications' stated
 support for screen sizes through the [&lt;`supports-screens`&gt;](
 https://developer.android.com/guide/topics/manifest/supports-screens-element.html)
 attribute in the AndroidManifest.xml, as described
 in the Android SDK documentation.

#### 7.1.1.2\. Screen Aspect Ratio

While there is no restriction to the screen aspect ratio value of the physical
screen display, the screen aspect ratio of the logical display that third-party
apps are rendered within, as can be derived from the height and width values
reported through the [`view.Display`](
https://developer.android.com/reference/android/view/Display.html)
APIs and [Configuration](
https://developer.android.com/reference/android/content/res/Configuration.html)
API, MUST meet the following requirements:

*   [C-0-1] Device implementations with the `Configuration.uiMode` set as
    `UI_MODE_TYPE_NORMAL` MUST have an aspect ratio value between 1.3333 (4:3)
    and 1.86 (roughly 16:9), unless the app can be deemed as ready to be
    stretched longer by meeting one of the following conditions:

     *  The app has declared that it supports a larger screen aspect ratio
     through  the [`android.max_aspect`](
     https://developer.android.com/guide/practices/screens&lowbar;support.html#MaxAspectRatio)
     metadata value.
     *  The app declares it is resizeable via the [android:resizeableActivity](
     https://developer.android.com/guide/topics/ui/multi-window.html#configuring)
     attribute.
     *  The app is targeting API level 26 or higher and does not declare a
     [`android:MaxAspectRatio`](
     https://developer.android.com/reference/android/R.attr.html#maxAspectRatio)
     that would restrict the allowed aspect ratio.


*   [C-0-2] Device implementations with the `Configuration.uiMode` set as
    `UI_MODE_TYPE_WATCH` MUST have an aspect ratio value set as 1.0 (1:1).

#### 7.1.1.3\. Screen Density

The Android UI framework defines a set of standard logical densities to help
application developers target application resources.

*    [C-0-1] By default, device implementations MUST report only one of the
following logical Android framework densities through the
[DENSITY_DEVICE_STABLE](
https://developer.android.com/reference/android/util/DisplayMetrics.html#DENSITY_DEVICE_STABLE)
API and this value MUST NOT change at any time; however, the device MAY report
a different arbitrary density according to the display configuration changes
made by the user (for example, display size) set after initial boot.

    *   120 dpi (ldpi)
    *   160 dpi (mdpi)
    *   213 dpi (tvdpi)
    *   240 dpi (hdpi)
    *   260 dpi (260dpi)
    *   280 dpi (280dpi)
    *   300 dpi (300dpi)
    *   320 dpi (xhdpi)
    *   340 dpi (340dpi)
    *   360 dpi (360dpi)
    *   400 dpi (400dpi)
    *   420 dpi (420dpi)
    *   480 dpi (xxhdpi)
    *   560 dpi (560dpi)
    *   640 dpi (xxxhdpi)

*    Device implementations SHOULD define the standard Android framework density
     that is numerically closest to the physical density of the screen, unless that
     logical density pushes the reported screen size below the minimum supported. If
     the standard Android framework density that is numerically closest to the
     physical density results in a screen size that is smaller than the smallest
     supported compatible screen size (320 dp width), device implementations SHOULD
     report the next lowest standard Android framework density.

*    [H-SR] Device implementations are STRONGLY RECOMMENDED to provide users an
     affordance to change the display size.

If there is an affordance to change the display size of the device:

*  [C-1-1] The display size MUST NOT be scaled any larger than 1.5 times the native density or
   produce an effective minimum screen dimension smaller than 320dp (equivalent
   to resource qualifier sw320dp), whichever comes first.
*  [C-1-2] Display size MUST NOT be scaled any smaller than 0.85 times the native density.
*  To ensure good usability and consistent font sizes, it is RECOMMENDED that the
   following scaling of Native Display options be provided (while complying with the limits
   specified above)
   *  Small: 0.85x
   *  Default: 1x (Native display scale)
   *  Large: 1.15x
   *  Larger: 1.3x
   *  Largest 1.45x

### 7.1.2\. Display Metrics

If device implementations include a screen or video output, they:

*    [C-1-1] MUST report correct values for all display metrics defined in the
 [`android.util.DisplayMetrics`](
 https://developer.android.com/reference/android/util/DisplayMetrics.html) API.

If device implementations does not include an embedded screen or video output,
they:

*    [C-2-1] MUST report reasonable values for all display metrics defined in
 the [`android.util.DisplayMetrics`](
 https://developer.android.com/reference/android/util/DisplayMetrics.html) API
 for the emulated default `view.Display`.



### 7.1.3\. Screen Orientation

Device implementations:

*    [C-0-1] MUST report which screen orientations they support
     (`android.hardware.screen.portrait` and/or
     `android.hardware.screen.landscape`) and MUST report at least one supported
     orientation. For example, a device with a fixed orientation landscape
     screen, such as a television or laptop, SHOULD only
     report `android.hardware.screen.landscape`.
*    [C-0-2] MUST report the correct value for the device’s current
     orientation, whenever queried via the
     `android.content.res.Configuration.orientation`,
     `android.view.Display.getOrientation()`, or other APIs.

If device implementations support both screen orientations, they:

*    [C-1-1] MUST support dynamic orientation by applications to either portrait or landscape screen
     orientation. That is, the device must respect the application’s request for a specific screen
     orientation.
*    [C-1-2] MUST NOT change the reported screen size or density when changing orientation.
*    MAY select either portrait or landscape orientation as the default.


### 7.1.4\. 2D and 3D Graphics Acceleration

#### 7.1.4.1 OpenGL ES

Device implementations:

*   [C-0-1] MUST correctly identify the supported OpenGL ES versions (1.1, 2.0,
    3.0, 3.1, 3.2) through the managed APIs (such as via the
    `GLES10.getString()` method) and the native APIs.
*   [C-0-2] MUST include the support for all the corresponding managed APIs and
    native APIs for every OpenGL ES versions they identified to support.

If device implementations include a screen or video output, they:

*   [C-1-1] MUST support both OpenGL ES 1.0 and 2.0, as embodied and detailed
    in the [Android SDK documentation](
    https://developer.android.com/guide/topics/graphics/opengl.html).
*   [SR] are STRONGLY RECOMMENDED to support OpenGL ES 3.0.
*   SHOULD support OpenGL ES 3.1 or 3.2.

If device implementations support any of the OpenGL ES versions, they:

*   [C-2-1] MUST report via the OpenGL ES managed APIs and native APIs any
    other OpenGL ES extensions they have implemented, and conversely MUST
    NOT report extension strings that they do not support.
*   [C-2-2] MUST support the `EGL_KHR_image`, `EGL_KHR_image_base`,
    `EGL_ANDROID_image_native_buffer`, `EGL_ANDROID_get_native_client_buffer`,
    `EGL_KHR_wait_sync`, `EGL_KHR_get_all_proc_addresses`,
    `EGL_ANDROID_presentation_time`, `EGL_KHR_swap_buffers_with_damage` and
    `EGL_ANDROID_recordable` extensions.
*   [SR] are STRONGLY RECOMMENDED to support EGL_KHR_partial_update.
*   SHOULD accurately report via the `getString()` method, any texture
    compression format that they support, which is typically vendor-specific.

If device implementations declare support for OpenGL ES 3.0, 3.1, or 3.2, they:

*   [C-3-1] MUST export the corresponding function symbols for these version in
    addition to the OpenGL ES 2.0 function symbols in the libGLESv2.so library.

If device implementations support OpenGL ES 3.2, they:

*    [C-4-1] MUST support the OpenGL ES Android Extension Pack in its entirety.

If device implementations support the OpenGL ES [Android Extension Pack](
https://developer.android.com/reference/android/opengl/GLES31Ext.html) in its
entirety, they:

*   [C-5-1] MUST identify the support through the `android.hardware.opengles.aep`
    feature flag.

If device implementations expose support for the `EGL_KHR_mutable_render_buffer`
extension, they:

*   [C-6-1] MUST also support the `EGL_ANDROID_front_buffer_auto_refresh`
    extension.

#### 7.1.4.2 Vulkan

Android includes support for [Vulkan](
https://www.khronos.org/registry/vulkan/specs/1.0-wsi&lowbarextensions/xhtml/vkspec.html)
, a low-overhead, cross-platform API for high-performance 3D graphics.

If device implementations support OpenGL ES 3.0 or 3.1, they:

*    [SR] Are STRONGLY RECOMMENDED to include support for Vulkan 1.0 .

If device implementations include a screen or video output, they:

*    SHOULD include support for Vulkan 1.0.

Device implementations, if including support for Vulkan 1.0:

*   [C-1-1] MUST report the correct integer value with the
    `android.hardware.vulkan.level` and `android.hardware.vulkan.version`
    feature flags.
*   [C-1-2] MUST enumarate, at least one `VkPhysicalDevice` for the Vulkan
    native API [`vkEnumeratePhysicalDevices()`](
    https://www.khronos.org/registry/vulkan/specs/1.0/man/html/vkEnumeratePhysicalDevices.html)
    .
*   [C-1-3] MUST fully implement the Vulkan 1.0 APIs for each enumerated
    `VkPhysicalDevice`.
*   [C-1-4] MUST enumerate layers, contained in native libraries named as
    `libVkLayer*.so` in the application package’s native library directory,
    through the Vulkan native APIs [`vkEnumerateInstanceLayerProperties()`](
    https://www.khronos.org/registry/vulkan/specs/1.0/man/html/vkEnumerateInstanceLayerProperties.html)
    and [`vkEnumerateDeviceLayerProperties()`](
    https://www.khronos.org/registry/vulkan/specs/1.0/man/html/vkEnumerateDeviceLayerProperties.html)
    .
*   [C-1-5] MUST NOT enumerate layers provided by libraries outside of the
    application package, or provide other ways of tracing or intercepting the
    Vulkan API, unless the application has the `android:debuggable` attribute
    set as `true`.
*   [C-1-6] MUST report all extension strings that they do support via the
    Vulkan native APIs , and conversely MUST NOT report extension strings
    that they do not correctly support.

Device implementations, if not including support for Vulkan 1.0:

*   [C-2-1] MUST NOT declare any of the Vulkan feature flags (e.g.
    `android.hardware.vulkan.level`, `android.hardware.vulkan.version`).
*   [C-2-2] MUST NOT enumarate any `VkPhysicalDevice` for the Vulkan native API
    `vkEnumeratePhysicalDevices()`.

#### 7.1.4.3 RenderScript

*    [C-0-1] Device implementations MUST support [Android RenderScript](
     http://developer.android.com/guide/topics/renderscript/), as detailed
     in the Android SDK documentation.

#### 7.1.4.4 2D Graphics Acceleration

Android includes a mechanism for applications to declare that they want to
enable hardware acceleration for 2D graphics at the Application, Activity,
Window, or View level through the use of a manifest tag
[android:hardwareAccelerated](
http://developer.android.com/guide/topics/graphics/hardware-accel.html)
or direct API calls.

Device implementations:

*    [C-0-1] MUST enable hardware acceleration by default, and MUST
     disable hardware acceleration if the developer so requests by setting
     android:hardwareAccelerated="false” or disabling hardware acceleration
     directly through the Android View APIs.
*    [C-0-2] MUST exhibit behavior consistent with the
     Android SDK documentation on [hardware acceleration](
     http://developer.android.com/guide/topics/graphics/hardware-accel.html).

Android includes a TextureView object that lets developers directly integrate
hardware-accelerated OpenGL ES textures as rendering targets in a UI hierarchy.

*    [C-0-3] MUST support the TextureView API, and MUST exhibit
     consistent behavior with the upstream Android implementation.

#### 7.1.4.5 Wide-gamut Displays

If device implementations claim support for wide-gamut displays through
[`Display.isWideColorGamut()`
](https://developer.android.com/reference/android/view/Display.html#isWideColorGamut%28%29)
, they:

*   [C-1-1] MUST have a color-calibrated display.
*   [C-1-2] MUST have a display whose gamut covers the sRGB color gamut entirely
    in CIE 1931 xyY space.
*   [C-1-3] MUST have a display whose gamut has an area of at least 90% of NTSC
    1953 in CIE 1931 xyY space.
*   [C-1-4] MUST support OpenGL ES 3.0, 3.1, or 3.2 and report it properly.
*   [C-1-5] MUST advertise support for the `EGL_KHR_no_config_context`,
    `EGL_EXT_pixel_format_float`,`EGL_KHR_gl_colorspace`,
    `EGL_EXT_colorspace_scrgb_linear`, and `EGL_GL_colorspace_display_p3`
    extensions.
*   [SR] Are STRONGLY RECOMMENDED to support `GL_EXT_sRGB`.

Conversely, if device implementations do not support wide-gamut displays, they:

*   [C-2-1] SHOULD cover 100% or more of sRGB in CIE 1931 xyY space, although
    the screen color gamut is undefined.

### 7.1.5\. Legacy Application Compatibility Mode

Android specifies a “compatibility mode” in which the framework operates in a
'normal' screen size equivalent (320dp width) mode for the benefit of legacy
applications not developed for old versions of Android that pre-date
screen-size independence.

*   [H-0-1] Handheld device implementations MUST include support
    for legacy application compatibility mode as implemented by the upstream
    Android open source code. That is, device implementations MUST NOT alter the
    triggers or thresholds at which compatibility mode is activated, and MUST
    NOT alter the behavior of the compatibility mode itself.

### 7.1.6\. Screen Technology

The Android platform includes APIs that allow applications to render rich
graphics to the display. Devices MUST support all of these APIs as defined by
the Android SDK unless specifically allowed in this document.

Device implementations:

*   [C-0-1] MUST support displays capable of rendering 16-bit color graphics.
*   SHOULD support displays capable of 24-bit color graphics.
*   [C-0-2] MUST support displays capable of rendering animations.
*   [C-0-3] MUST use the display technology that have a pixel aspect ratio (PAR)
    between 0.9 and 1.15\. That is, the pixel aspect ratio MUST be near square
    (1.0) with a 10 ~ 15% tolerance.

### 7.1.7\. Secondary Displays

Android includes support for secondary display to enable media sharing
capabilities and developer APIs for accessing external displays.

If device implementations support an external display either via a wired,
wireless, or an embedded additional display connection, they:

*   [C-1-1] MUST implement the [`DisplayManager`](
    https://developer.android.com/reference/android/hardware/display/DisplayManager.html)
    system service and API as described in the Android SDK documentation.