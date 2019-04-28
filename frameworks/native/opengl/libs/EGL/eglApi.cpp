/*
 ** Copyright 2007, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#include <ctype.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>

#include <hardware/gralloc1.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>

#include <android/hardware_buffer.h>
#include <private/android/AHardwareBufferHelpers.h>

#include <cutils/compiler.h>
#include <cutils/properties.h>
#include <log/log.h>

#include <condition_variable>
#include <deque>
#include <mutex>
#include <unordered_map>
#include <string>
#include <thread>

#include "../egl_impl.h"

#include "egl_display.h"
#include "egl_object.h"
#include "egl_tls.h"
#include "egl_trace.h"

using namespace android;

// ----------------------------------------------------------------------------

namespace android {

using nsecs_t = int64_t;

struct extention_map_t {
    const char* name;
    __eglMustCastToProperFunctionPointerType address;
};

/*
 * This is the list of EGL extensions exposed to applications.
 *
 * Some of them (gBuiltinExtensionString) are implemented entirely in this EGL
 * wrapper and are always available.
 *
 * The rest (gExtensionString) depend on support in the EGL driver, and are
 * only available if the driver supports them. However, some of these must be
 * supported because they are used by the Android system itself; these are
 * listed as mandatory below and are required by the CDD. The system *assumes*
 * the mandatory extensions are present and may not function properly if some
 * are missing.
 *
 * NOTE: Both strings MUST have a single space as the last character.
 */

extern char const * const gBuiltinExtensionString;
extern char const * const gExtensionString;

// clang-format off
char const * const gBuiltinExtensionString =
        "EGL_KHR_get_all_proc_addresses "
        "EGL_ANDROID_presentation_time "
        "EGL_KHR_swap_buffers_with_damage "
        "EGL_ANDROID_get_native_client_buffer "
        "EGL_ANDROID_front_buffer_auto_refresh "
        "EGL_ANDROID_get_frame_timestamps "
        ;

char const * const gExtensionString  =
        "EGL_KHR_image "                        // mandatory
        "EGL_KHR_image_base "                   // mandatory
        "EGL_KHR_image_pixmap "
        "EGL_KHR_lock_surface "
        "EGL_KHR_gl_colorspace "
        "EGL_KHR_gl_texture_2D_image "
        "EGL_KHR_gl_texture_3D_image "
        "EGL_KHR_gl_texture_cubemap_image "
        "EGL_KHR_gl_renderbuffer_image "
        "EGL_KHR_reusable_sync "
        "EGL_KHR_fence_sync "
        "EGL_KHR_create_context "
        "EGL_KHR_config_attribs "
        "EGL_KHR_surfaceless_context "
        "EGL_KHR_stream "
        "EGL_KHR_stream_fifo "
        "EGL_KHR_stream_producer_eglsurface "
        "EGL_KHR_stream_consumer_gltexture "
        "EGL_KHR_stream_cross_process_fd "
        "EGL_EXT_create_context_robustness "
        "EGL_NV_system_time "
        "EGL_ANDROID_image_native_buffer "      // mandatory
        "EGL_KHR_wait_sync "                    // strongly recommended
        "EGL_ANDROID_recordable "               // mandatory
        "EGL_KHR_partial_update "               // strongly recommended
        "EGL_EXT_pixel_format_float "
        "EGL_EXT_buffer_age "                   // strongly recommended with partial_update
        "EGL_KHR_create_context_no_error "
        "EGL_KHR_mutable_render_buffer "
        "EGL_EXT_yuv_surface "
        "EGL_EXT_protected_content "
        "EGL_IMG_context_priority "
        "EGL_KHR_no_config_context "
        ;
// clang-format on

// extensions not exposed to applications but used by the ANDROID system
//      "EGL_ANDROID_blob_cache "               // strongly recommended
//      "EGL_IMG_hibernate_process "            // optional
//      "EGL_ANDROID_native_fence_sync "        // strongly recommended
//      "EGL_ANDROID_framebuffer_target "       // mandatory for HWC 1.1
//      "EGL_ANDROID_image_crop "               // optional

/*
 * EGL Extensions entry-points exposed to 3rd party applications
 * (keep in sync with gExtensionString above)
 *
 */
static const extention_map_t sExtensionMap[] = {
    // EGL_KHR_lock_surface
    { "eglLockSurfaceKHR",
            (__eglMustCastToProperFunctionPointerType)&eglLockSurfaceKHR },
    { "eglUnlockSurfaceKHR",
            (__eglMustCastToProperFunctionPointerType)&eglUnlockSurfaceKHR },

    // EGL_KHR_image, EGL_KHR_image_base
    { "eglCreateImageKHR",
            (__eglMustCastToProperFunctionPointerType)&eglCreateImageKHR },
    { "eglDestroyImageKHR",
            (__eglMustCastToProperFunctionPointerType)&eglDestroyImageKHR },

    // EGL_KHR_reusable_sync, EGL_KHR_fence_sync
    { "eglCreateSyncKHR",
            (__eglMustCastToProperFunctionPointerType)&eglCreateSyncKHR },
    { "eglDestroySyncKHR",
            (__eglMustCastToProperFunctionPointerType)&eglDestroySyncKHR },
    { "eglClientWaitSyncKHR",
            (__eglMustCastToProperFunctionPointerType)&eglClientWaitSyncKHR },
    { "eglSignalSyncKHR",
            (__eglMustCastToProperFunctionPointerType)&eglSignalSyncKHR },
    { "eglGetSyncAttribKHR",
            (__eglMustCastToProperFunctionPointerType)&eglGetSyncAttribKHR },

    // EGL_NV_system_time
    { "eglGetSystemTimeFrequencyNV",
            (__eglMustCastToProperFunctionPointerType)&eglGetSystemTimeFrequencyNV },
    { "eglGetSystemTimeNV",
            (__eglMustCastToProperFunctionPointerType)&eglGetSystemTimeNV },

    // EGL_KHR_wait_sync
    { "eglWaitSyncKHR",
            (__eglMustCastToProperFunctionPointerType)&eglWaitSyncKHR },

    // EGL_ANDROID_presentation_time
    { "eglPresentationTimeANDROID",
            (__eglMustCastToProperFunctionPointerType)&eglPresentationTimeANDROID },

    // EGL_KHR_swap_buffers_with_damage
    { "eglSwapBuffersWithDamageKHR",
            (__eglMustCastToProperFunctionPointerType)&eglSwapBuffersWithDamageKHR },

    // EGL_ANDROID_get_native_client_buffer
    { "eglGetNativeClientBufferANDROID",
            (__eglMustCastToProperFunctionPointerType)&eglGetNativeClientBufferANDROID },

    // EGL_KHR_partial_update
    { "eglSetDamageRegionKHR",
            (__eglMustCastToProperFunctionPointerType)&eglSetDamageRegionKHR },

    { "eglCreateStreamKHR",
            (__eglMustCastToProperFunctionPointerType)&eglCreateStreamKHR },
    { "eglDestroyStreamKHR",
            (__eglMustCastToProperFunctionPointerType)&eglDestroyStreamKHR },
    { "eglStreamAttribKHR",
            (__eglMustCastToProperFunctionPointerType)&eglStreamAttribKHR },
    { "eglQueryStreamKHR",
            (__eglMustCastToProperFunctionPointerType)&eglQueryStreamKHR },
    { "eglQueryStreamu64KHR",
            (__eglMustCastToProperFunctionPointerType)&eglQueryStreamu64KHR },
    { "eglQueryStreamTimeKHR",
            (__eglMustCastToProperFunctionPointerType)&eglQueryStreamTimeKHR },
    { "eglCreateStreamProducerSurfaceKHR",
            (__eglMustCastToProperFunctionPointerType)&eglCreateStreamProducerSurfaceKHR },
    { "eglStreamConsumerGLTextureExternalKHR",
            (__eglMustCastToProperFunctionPointerType)&eglStreamConsumerGLTextureExternalKHR },
    { "eglStreamConsumerAcquireKHR",
            (__eglMustCastToProperFunctionPointerType)&eglStreamConsumerAcquireKHR },
    { "eglStreamConsumerReleaseKHR",
            (__eglMustCastToProperFunctionPointerType)&eglStreamConsumerReleaseKHR },
    { "eglGetStreamFileDescriptorKHR",
            (__eglMustCastToProperFunctionPointerType)&eglGetStreamFileDescriptorKHR },
    { "eglCreateStreamFromFileDescriptorKHR",
            (__eglMustCastToProperFunctionPointerType)&eglCreateStreamFromFileDescriptorKHR },

    // EGL_ANDROID_get_frame_timestamps
    { "eglGetNextFrameIdANDROID",
            (__eglMustCastToProperFunctionPointerType)&eglGetNextFrameIdANDROID },
    { "eglGetCompositorTimingANDROID",
            (__eglMustCastToProperFunctionPointerType)&eglGetCompositorTimingANDROID },
    { "eglGetCompositorTimingSupportedANDROID",
            (__eglMustCastToProperFunctionPointerType)&eglGetCompositorTimingSupportedANDROID },
    { "eglGetFrameTimestampsANDROID",
            (__eglMustCastToProperFunctionPointerType)&eglGetFrameTimestampsANDROID },
    { "eglGetFrameTimestampSupportedANDROID",
            (__eglMustCastToProperFunctionPointerType)&eglGetFrameTimestampSupportedANDROID },

    // EGL_ANDROID_native_fence_sync
    { "eglDupNativeFenceFDANDROID",
            (__eglMustCastToProperFunctionPointerType)&eglDupNativeFenceFDANDROID },
};

/*
 * These extensions entry-points should not be exposed to applications.
 * They're used internally by the Android EGL layer.
 */
#define FILTER_EXTENSIONS(procname) \
        (!strcmp((procname), "eglSetBlobCacheFuncsANDROID") ||    \
         !strcmp((procname), "eglHibernateProcessIMG")      ||    \
         !strcmp((procname), "eglAwakenProcessIMG"))



// accesses protected by sExtensionMapMutex
static std::unordered_map<std::string, __eglMustCastToProperFunctionPointerType> sGLExtentionMap;

static int sGLExtentionSlot = 0;
static pthread_mutex_t sExtensionMapMutex = PTHREAD_MUTEX_INITIALIZER;

static void(*findProcAddress(const char* name,
        const extention_map_t* map, size_t n))() {
    for (uint32_t i=0 ; i<n ; i++) {
        if (!strcmp(name, map[i].name)) {
            return map[i].address;
        }
    }
    return NULL;
}

// ----------------------------------------------------------------------------

extern void setGLHooksThreadSpecific(gl_hooks_t const *value);
extern EGLBoolean egl_init_drivers();
extern const __eglMustCastToProperFunctionPointerType gExtensionForwarders[MAX_NUMBER_OF_GL_EXTENSIONS];
extern gl_hooks_t gHooksTrace;

} // namespace android;


// ----------------------------------------------------------------------------

static inline void clearError() { egl_tls_t::clearError(); }
static inline EGLContext getContext() { return egl_tls_t::getContext(); }

// ----------------------------------------------------------------------------

EGLDisplay eglGetDisplay(EGLNativeDisplayType display)
{
    ATRACE_CALL();
    clearError();

    uintptr_t index = reinterpret_cast<uintptr_t>(display);
    if (index >= NUM_DISPLAYS) {
        return setError(EGL_BAD_PARAMETER, EGL_NO_DISPLAY);
    }

    if (egl_init_drivers() == EGL_FALSE) {
        return setError(EGL_BAD_PARAMETER, EGL_NO_DISPLAY);
    }

    EGLDisplay dpy = egl_display_t::getFromNativeDisplay(display);
    return dpy;
}

// ----------------------------------------------------------------------------
// Initialization
// ----------------------------------------------------------------------------

EGLBoolean eglInitialize(EGLDisplay dpy, EGLint *major, EGLint *minor)
{
    clearError();

    egl_display_ptr dp = get_display(dpy);
    if (!dp) return setError(EGL_BAD_DISPLAY, (EGLBoolean)EGL_FALSE);

    EGLBoolean res = dp->initialize(major, minor);

    return res;
}

EGLBoolean eglTerminate(EGLDisplay dpy)
{
    // NOTE: don't unload the drivers b/c some APIs can be called
    // after eglTerminate() has been called. eglTerminate() only
    // terminates an EGLDisplay, not a EGL itself.

    clearError();

    egl_display_ptr dp = get_display(dpy);
    if (!dp) return setError(EGL_BAD_DISPLAY, (EGLBoolean)EGL_FALSE);

    EGLBoolean res = dp->terminate();

    return res;
}

// ----------------------------------------------------------------------------
// configuration
// ----------------------------------------------------------------------------

EGLBoolean eglGetConfigs(   EGLDisplay dpy,
                            EGLConfig *configs,
                            EGLint config_size, EGLint *num_config)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    if (num_config==0) {
        return setError(EGL_BAD_PARAMETER, (EGLBoolean)EGL_FALSE);
    }

    EGLBoolean res = EGL_FALSE;
    *num_config = 0;

    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso) {
        res = cnx->egl.eglGetConfigs(
                dp->disp.dpy, configs, config_size, num_config);
    }

    return res;
}

EGLBoolean eglChooseConfig( EGLDisplay dpy, const EGLint *attrib_list,
                            EGLConfig *configs, EGLint config_size,
                            EGLint *num_config)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    if (num_config==0) {
        return setError(EGL_BAD_PARAMETER, (EGLBoolean)EGL_FALSE);
    }

    EGLBoolean res = EGL_FALSE;
    *num_config = 0;

    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso) {
        if (attrib_list) {
            char value[PROPERTY_VALUE_MAX];
            property_get("debug.egl.force_msaa", value, "false");

            if (!strcmp(value, "true")) {
                size_t attribCount = 0;
                EGLint attrib = attrib_list[0];

                // Only enable MSAA if the context is OpenGL ES 2.0 and
                // if no caveat is requested
                const EGLint *attribRendererable = NULL;
                const EGLint *attribCaveat = NULL;

                // Count the number of attributes and look for
                // EGL_RENDERABLE_TYPE and EGL_CONFIG_CAVEAT
                while (attrib != EGL_NONE) {
                    attrib = attrib_list[attribCount];
                    switch (attrib) {
                        case EGL_RENDERABLE_TYPE:
                            attribRendererable = &attrib_list[attribCount];
                            break;
                        case EGL_CONFIG_CAVEAT:
                            attribCaveat = &attrib_list[attribCount];
                            break;
                        default:
                            break;
                    }
                    attribCount++;
                }

                if (attribRendererable && attribRendererable[1] == EGL_OPENGL_ES2_BIT &&
                        (!attribCaveat || attribCaveat[1] != EGL_NONE)) {

                    // Insert 2 extra attributes to force-enable MSAA 4x
                    EGLint aaAttribs[attribCount + 4];
                    aaAttribs[0] = EGL_SAMPLE_BUFFERS;
                    aaAttribs[1] = 1;
                    aaAttribs[2] = EGL_SAMPLES;
                    aaAttribs[3] = 4;

                    memcpy(&aaAttribs[4], attrib_list, attribCount * sizeof(EGLint));

                    EGLint numConfigAA;
                    EGLBoolean resAA = cnx->egl.eglChooseConfig(
                            dp->disp.dpy, aaAttribs, configs, config_size, &numConfigAA);

                    if (resAA == EGL_TRUE && numConfigAA > 0) {
                        ALOGD("Enabling MSAA 4x");
                        *num_config = numConfigAA;
                        return resAA;
                    }
                }
            }
        }

        res = cnx->egl.eglChooseConfig(
                dp->disp.dpy, attrib_list, configs, config_size, num_config);
    }
    return res;
}

EGLBoolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config,
        EGLint attribute, EGLint *value)
{
    clearError();

    egl_connection_t* cnx = NULL;
    const egl_display_ptr dp = validate_display_connection(dpy, cnx);
    if (!dp) return EGL_FALSE;

    return cnx->egl.eglGetConfigAttrib(
            dp->disp.dpy, config, attribute, value);
}

// ----------------------------------------------------------------------------
// surfaces
// ----------------------------------------------------------------------------

// Turn linear formats into corresponding sRGB formats when colorspace is
// EGL_GL_COLORSPACE_SRGB_KHR, or turn sRGB formats into corresponding linear
// formats when colorspace is EGL_GL_COLORSPACE_LINEAR_KHR. In any cases where
// the modification isn't possible, the original dataSpace is returned.
static android_dataspace modifyBufferDataspace(android_dataspace dataSpace,
                                               EGLint colorspace) {
    if (colorspace == EGL_GL_COLORSPACE_LINEAR_KHR) {
        return HAL_DATASPACE_SRGB_LINEAR;
    } else if (colorspace == EGL_GL_COLORSPACE_SRGB_KHR) {
        return HAL_DATASPACE_SRGB;
    } else if (colorspace == EGL_GL_COLORSPACE_DISPLAY_P3_EXT) {
        return HAL_DATASPACE_DISPLAY_P3;
    } else if (colorspace == EGL_GL_COLORSPACE_DISPLAY_P3_LINEAR_EXT) {
        return HAL_DATASPACE_DISPLAY_P3_LINEAR;
    } else if (colorspace == EGL_GL_COLORSPACE_SCRGB_EXT) {
        return HAL_DATASPACE_V0_SCRGB;
    } else if (colorspace == EGL_GL_COLORSPACE_SCRGB_LINEAR_EXT) {
        return HAL_DATASPACE_V0_SCRGB_LINEAR;
    }
    return dataSpace;
}

// Return true if we stripped any EGL_GL_COLORSPACE_KHR attributes.
static EGLBoolean stripColorSpaceAttribute(egl_display_ptr dp, const EGLint* attrib_list,
                                           EGLint format,
                                           std::vector<EGLint>& stripped_attrib_list) {
    std::vector<EGLint> allowedColorSpaces;
    switch (format) {
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_RGB_565:
            // driver okay with linear & sRGB for 8888, but can't handle
            // Display-P3 or other spaces.
            allowedColorSpaces.push_back(EGL_GL_COLORSPACE_SRGB_KHR);
            allowedColorSpaces.push_back(EGL_GL_COLORSPACE_LINEAR_KHR);
            break;

        case HAL_PIXEL_FORMAT_RGBA_FP16:
        case HAL_PIXEL_FORMAT_RGBA_1010102:
        default:
            // driver does not want to see colorspace attributes for 1010102 or fp16.
            // Future: if driver supports XXXX extension, we can pass down that colorspace
            break;
    }

    bool stripped = false;
    if (attrib_list && dp->haveExtension("EGL_KHR_gl_colorspace")) {
        for (const EGLint* attr = attrib_list; attr[0] != EGL_NONE; attr += 2) {
            if (attr[0] == EGL_GL_COLORSPACE_KHR) {
                EGLint colorSpace = attr[1];
                bool found = false;
                // Verify that color space is allowed
                for (auto it : allowedColorSpaces) {
                    if (colorSpace == it) {
                        found = true;
                    }
                }
                if (!found) {
                    stripped = true;
                } else {
                    stripped_attrib_list.push_back(attr[0]);
                    stripped_attrib_list.push_back(attr[1]);
                }
            } else {
                stripped_attrib_list.push_back(attr[0]);
                stripped_attrib_list.push_back(attr[1]);
            }
        }
    }
    if (stripped) {
        stripped_attrib_list.push_back(EGL_NONE);
        stripped_attrib_list.push_back(EGL_NONE);
    }
    return stripped;
}

static EGLBoolean getColorSpaceAttribute(egl_display_ptr dp, NativeWindowType window,
                                         const EGLint* attrib_list, EGLint& colorSpace,
                                         android_dataspace& dataSpace) {
    colorSpace = EGL_GL_COLORSPACE_LINEAR_KHR;
    dataSpace = HAL_DATASPACE_UNKNOWN;

    if (attrib_list && dp->haveExtension("EGL_KHR_gl_colorspace")) {
        for (const EGLint* attr = attrib_list; *attr != EGL_NONE; attr += 2) {
            if (*attr == EGL_GL_COLORSPACE_KHR) {
                colorSpace = attr[1];
                bool found = false;
                bool verify = true;
                // Verify that color space is allowed
                if (colorSpace == EGL_GL_COLORSPACE_SRGB_KHR ||
                    colorSpace == EGL_GL_COLORSPACE_LINEAR_KHR) {
                    // SRGB and LINEAR are always supported when EGL_KHR_gl_colorspace
                    // is available, so no need to verify.
                    found = true;
                    verify = false;
                } else if (colorSpace == EGL_EXT_gl_colorspace_bt2020_linear &&
                           dp->haveExtension("EGL_EXT_gl_colorspace_bt2020_linear")) {
                    found = true;
                } else if (colorSpace == EGL_EXT_gl_colorspace_bt2020_pq &&
                           dp->haveExtension("EGL_EXT_gl_colorspace_bt2020_pq")) {
                    found = true;
                } else if (colorSpace == EGL_GL_COLORSPACE_SCRGB_EXT &&
                           dp->haveExtension("EGL_EXT_gl_colorspace_scrgb")) {
                    found = true;
                } else if (colorSpace == EGL_GL_COLORSPACE_SCRGB_LINEAR_EXT &&
                           dp->haveExtension("EGL_EXT_gl_colorspace_scrgb_linear")) {
                    found = true;
                } else if (colorSpace == EGL_GL_COLORSPACE_DISPLAY_P3_LINEAR_EXT &&
                           dp->haveExtension("EGL_EXT_gl_colorspace_display_p3_linear")) {
                    found = true;
                } else if (colorSpace == EGL_GL_COLORSPACE_DISPLAY_P3_EXT &&
                           dp->haveExtension("EGL_EXT_gl_colorspace_display_p3")) {
                    found = true;
                }
                if (!found) {
                    return false;
                }
                if (verify && window) {
                    bool wide_color_support = true;
                    // Ordinarily we'd put a call to native_window_get_wide_color_support
                    // at the beginning of the function so that we'll have the
                    // result when needed elsewhere in the function.
                    // However, because eglCreateWindowSurface is called by SurfaceFlinger and
                    // SurfaceFlinger is required to answer the call below we would
                    // end up in a deadlock situation. By moving the call to only happen
                    // if the application has specifically asked for wide-color we avoid
                    // the deadlock with SurfaceFlinger since it will not ask for a
                    // wide-color surface.
                    int err = native_window_get_wide_color_support(window, &wide_color_support);

                    if (err) {
                        ALOGE("getColorSpaceAttribute: invalid window (win=%p) "
                              "failed (%#x) (already connected to another API?)",
                              window, err);
                        return false;
                    }
                    if (!wide_color_support) {
                        // Application has asked for a wide-color colorspace but
                        // wide-color support isn't available on the display the window is on.
                        return false;
                    }
                }
                // Only change the dataSpace from default if the application
                // has explicitly set the color space with a EGL_GL_COLORSPACE_KHR attribute.
                dataSpace = modifyBufferDataspace(dataSpace, colorSpace);
            }
        }
    }
    return true;
}

static EGLBoolean getColorSpaceAttribute(egl_display_ptr dp, const EGLint* attrib_list,
                                         EGLint& colorSpace, android_dataspace& dataSpace) {
    return getColorSpaceAttribute(dp, NULL, attrib_list, colorSpace, dataSpace);
}

void getNativePixelFormat(EGLDisplay dpy, egl_connection_t* cnx, EGLConfig config, EGLint& format) {
    // Set the native window's buffers format to match what this config requests.
    // Whether to use sRGB gamma is not part of the EGLconfig, but is part
    // of our native format. So if sRGB gamma is requested, we have to
    // modify the EGLconfig's format before setting the native window's
    // format.

    EGLint componentType = EGL_COLOR_COMPONENT_TYPE_FIXED_EXT;
    cnx->egl.eglGetConfigAttrib(dpy, config, EGL_COLOR_COMPONENT_TYPE_EXT, &componentType);

    EGLint a = 0;
    EGLint r, g, b;
    r = g = b = 0;
    cnx->egl.eglGetConfigAttrib(dpy, config, EGL_RED_SIZE, &r);
    cnx->egl.eglGetConfigAttrib(dpy, config, EGL_GREEN_SIZE, &g);
    cnx->egl.eglGetConfigAttrib(dpy, config, EGL_BLUE_SIZE, &b);
    cnx->egl.eglGetConfigAttrib(dpy, config, EGL_ALPHA_SIZE, &a);
    EGLint colorDepth = r + g + b;

    // Today, the driver only understands sRGB and linear on 888X
    // formats. Strip other colorspaces from the attribute list and
    // only use them to set the dataspace via
    // native_window_set_buffers_dataspace
    // if pixel format is RGBX 8888
    //    TBD: Can test for future extensions that indicate that driver
    //    handles requested color space and we can let it through.
    //    allow SRGB and LINEAR. All others need to be stripped.
    // else if 565, 4444
    //    TBD: Can we assume these are supported if 8888 is?
    // else if FP16 or 1010102
    //    strip colorspace from attribs.
    // endif
    if (a == 0) {
        if (colorDepth <= 16) {
            format = HAL_PIXEL_FORMAT_RGB_565;
        } else {
            if (componentType == EGL_COLOR_COMPONENT_TYPE_FIXED_EXT) {
                if (colorDepth > 24) {
                    format = HAL_PIXEL_FORMAT_RGBA_1010102;
                } else {
                    format = HAL_PIXEL_FORMAT_RGBX_8888;
                }
            } else {
                format = HAL_PIXEL_FORMAT_RGBA_FP16;
            }
        }
    } else {
        if (componentType == EGL_COLOR_COMPONENT_TYPE_FIXED_EXT) {
            if (colorDepth > 24) {
                format = HAL_PIXEL_FORMAT_RGBA_1010102;
            } else {
                format = HAL_PIXEL_FORMAT_RGBA_8888;
            }
        } else {
            format = HAL_PIXEL_FORMAT_RGBA_FP16;
        }
    }
}

EGLSurface eglCreateWindowSurface(  EGLDisplay dpy, EGLConfig config,
                                    NativeWindowType window,
                                    const EGLint *attrib_list)
{
    clearError();

    egl_connection_t* cnx = NULL;
    egl_display_ptr dp = validate_display_connection(dpy, cnx);
    if (dp) {
        EGLDisplay iDpy = dp->disp.dpy;

        if (!window) {
            return setError(EGL_BAD_NATIVE_WINDOW, EGL_NO_SURFACE);
        }

        int value = 0;
        window->query(window, NATIVE_WINDOW_IS_VALID, &value);
        if (!value) {
            return setError(EGL_BAD_NATIVE_WINDOW, EGL_NO_SURFACE);
        }

        int result = native_window_api_connect(window, NATIVE_WINDOW_API_EGL);
        if (result < 0) {
            ALOGE("eglCreateWindowSurface: native_window_api_connect (win=%p) "
                    "failed (%#x) (already connected to another API?)",
                    window, result);
            return setError(EGL_BAD_ALLOC, EGL_NO_SURFACE);
        }

        EGLint format;
        getNativePixelFormat(iDpy, cnx, config, format);

        // now select correct colorspace and dataspace based on user's attribute list
        EGLint colorSpace;
        android_dataspace dataSpace;
        if (!getColorSpaceAttribute(dp, window, attrib_list, colorSpace, dataSpace)) {
            ALOGE("error invalid colorspace: %d", colorSpace);
            return setError(EGL_BAD_ATTRIBUTE, EGL_NO_SURFACE);
        }

        std::vector<EGLint> strippedAttribList;
        if (stripColorSpaceAttribute(dp, attrib_list, format, strippedAttribList)) {
            // Had to modify the attribute list due to use of color space.
            // Use modified list from here on.
            attrib_list = strippedAttribList.data();
        }

        if (format != 0) {
            int err = native_window_set_buffers_format(window, format);
            if (err != 0) {
                ALOGE("error setting native window pixel format: %s (%d)",
                        strerror(-err), err);
                native_window_api_disconnect(window, NATIVE_WINDOW_API_EGL);
                return setError(EGL_BAD_NATIVE_WINDOW, EGL_NO_SURFACE);
            }
        }

        if (dataSpace != 0) {
            int err = native_window_set_buffers_data_space(window, dataSpace);
            if (err != 0) {
                ALOGE("error setting native window pixel dataSpace: %s (%d)",
                        strerror(-err), err);
                native_window_api_disconnect(window, NATIVE_WINDOW_API_EGL);
                return setError(EGL_BAD_NATIVE_WINDOW, EGL_NO_SURFACE);
            }
        }

        // the EGL spec requires that a new EGLSurface default to swap interval
        // 1, so explicitly set that on the window here.
        ANativeWindow* anw = reinterpret_cast<ANativeWindow*>(window);
        anw->setSwapInterval(anw, 1);

        EGLSurface surface = cnx->egl.eglCreateWindowSurface(
                iDpy, config, window, attrib_list);
        if (surface != EGL_NO_SURFACE) {
            egl_surface_t* s =
                    new egl_surface_t(dp.get(), config, window, surface, colorSpace, cnx);
            return s;
        }

        // EGLSurface creation failed
        native_window_set_buffers_format(window, 0);
        native_window_api_disconnect(window, NATIVE_WINDOW_API_EGL);
    }
    return EGL_NO_SURFACE;
}

EGLSurface eglCreatePixmapSurface(  EGLDisplay dpy, EGLConfig config,
                                    NativePixmapType pixmap,
                                    const EGLint *attrib_list)
{
    clearError();

    egl_connection_t* cnx = NULL;
    egl_display_ptr dp = validate_display_connection(dpy, cnx);
    EGLint colorSpace;
    android_dataspace dataSpace;
    if (dp) {
        // now select a corresponding sRGB format if needed
        if (!getColorSpaceAttribute(dp, attrib_list, colorSpace, dataSpace)) {
            ALOGE("error invalid colorspace: %d", colorSpace);
            return setError(EGL_BAD_ATTRIBUTE, EGL_NO_SURFACE);
        }

        EGLSurface surface = cnx->egl.eglCreatePixmapSurface(
                dp->disp.dpy, config, pixmap, attrib_list);
        if (surface != EGL_NO_SURFACE) {
            egl_surface_t* s = new egl_surface_t(dp.get(), config, NULL, surface, colorSpace, cnx);
            return s;
        }
    }
    return EGL_NO_SURFACE;
}

EGLSurface eglCreatePbufferSurface( EGLDisplay dpy, EGLConfig config,
                                    const EGLint *attrib_list)
{
    clearError();

    egl_connection_t* cnx = NULL;
    egl_display_ptr dp = validate_display_connection(dpy, cnx);
    if (dp) {
        EGLDisplay iDpy = dp->disp.dpy;
        EGLint format;
        getNativePixelFormat(iDpy, cnx, config, format);

        // now select correct colorspace and dataspace based on user's attribute list
        EGLint colorSpace;
        android_dataspace dataSpace;
        if (!getColorSpaceAttribute(dp, attrib_list, colorSpace, dataSpace)) {
            ALOGE("error invalid colorspace: %d", colorSpace);
            return setError(EGL_BAD_ATTRIBUTE, EGL_NO_SURFACE);
        }

        // Pbuffers are not displayed so we don't need to store the
        // colorspace. We do need to filter out color spaces the
        // driver doesn't know how to process.
        std::vector<EGLint> strippedAttribList;
        if (stripColorSpaceAttribute(dp, attrib_list, format, strippedAttribList)) {
            // Had to modify the attribute list due to use of color space.
            // Use modified list from here on.
            attrib_list = strippedAttribList.data();
        }

        EGLSurface surface = cnx->egl.eglCreatePbufferSurface(
                dp->disp.dpy, config, attrib_list);
        if (surface != EGL_NO_SURFACE) {
            egl_surface_t* s = new egl_surface_t(dp.get(), config, NULL, surface, colorSpace, cnx);
            return s;
        }
    }
    return EGL_NO_SURFACE;
}

EGLBoolean eglDestroySurface(EGLDisplay dpy, EGLSurface surface)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get())
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);

    egl_surface_t * const s = get_surface(surface);
    EGLBoolean result = s->cnx->egl.eglDestroySurface(dp->disp.dpy, s->surface);
    if (result == EGL_TRUE) {
        _s.terminate();
    }
    return result;
}

EGLBoolean eglQuerySurface( EGLDisplay dpy, EGLSurface surface,
                            EGLint attribute, EGLint *value)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get())
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);

    egl_surface_t const * const s = get_surface(surface);
    if (attribute == EGL_GL_COLORSPACE_KHR) {
        *value = s->getColorSpace();
        return EGL_TRUE;
    }
    if (attribute == EGL_IS_INVALID_MTK) {
        int tmp_w;
        EGLNativeWindowType win = (EGLNativeWindowType)s->getNativeWindow();
        if (!win) return EGL_FALSE;
        *value = win->query(win, NATIVE_WINDOW_HEIGHT, &tmp_w) != 0 ? 1 : 0;
        return EGL_TRUE;
    }
    return s->cnx->egl.eglQuerySurface(
            dp->disp.dpy, s->surface, attribute, value);
}

void EGLAPI eglBeginFrame(EGLDisplay dpy, EGLSurface surface) {
    ATRACE_CALL();
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) {
        return;
    }

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get()) {
        setError(EGL_BAD_SURFACE, EGL_FALSE);
    }
}

// ----------------------------------------------------------------------------
// Contexts
// ----------------------------------------------------------------------------

EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config,
                            EGLContext share_list, const EGLint *attrib_list)
{
    clearError();

    egl_connection_t* cnx = NULL;
    const egl_display_ptr dp = validate_display_connection(dpy, cnx);
    if (dp) {
        if (share_list != EGL_NO_CONTEXT) {
            if (!ContextRef(dp.get(), share_list).get()) {
                return setError(EGL_BAD_CONTEXT, EGL_NO_CONTEXT);
            }
            egl_context_t* const c = get_context(share_list);
            share_list = c->context;
        }
        EGLContext context = cnx->egl.eglCreateContext(
                dp->disp.dpy, config, share_list, attrib_list);
        if (context != EGL_NO_CONTEXT) {
            // figure out if it's a GLESv1 or GLESv2
            int version = 0;
            if (attrib_list) {
                while (*attrib_list != EGL_NONE) {
                    GLint attr = *attrib_list++;
                    GLint value = *attrib_list++;
                    if (attr == EGL_CONTEXT_CLIENT_VERSION) {
                        if (value == 1) {
                            version = egl_connection_t::GLESv1_INDEX;
                        } else if (value == 2 || value == 3) {
                            version = egl_connection_t::GLESv2_INDEX;
                        }
                    }
                };
            }
            egl_context_t* c = new egl_context_t(dpy, context, config, cnx,
                    version);
            return c;
        }
    }
    return EGL_NO_CONTEXT;
}

EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp)
        return EGL_FALSE;

    ContextRef _c(dp.get(), ctx);
    if (!_c.get())
        return setError(EGL_BAD_CONTEXT, (EGLBoolean)EGL_FALSE);

    egl_context_t * const c = get_context(ctx);
    EGLBoolean result = c->cnx->egl.eglDestroyContext(dp->disp.dpy, c->context);
    if (result == EGL_TRUE) {
        _c.terminate();
    }
    return result;
}

EGLBoolean eglMakeCurrent(  EGLDisplay dpy, EGLSurface draw,
                            EGLSurface read, EGLContext ctx)
{
    clearError();

    egl_display_ptr dp = validate_display(dpy);
    if (!dp) return setError(EGL_BAD_DISPLAY, (EGLBoolean)EGL_FALSE);

    // If ctx is not EGL_NO_CONTEXT, read is not EGL_NO_SURFACE, or draw is not
    // EGL_NO_SURFACE, then an EGL_NOT_INITIALIZED error is generated if dpy is
    // a valid but uninitialized display.
    if ( (ctx != EGL_NO_CONTEXT) || (read != EGL_NO_SURFACE) ||
         (draw != EGL_NO_SURFACE) ) {
        if (!dp->isReady()) return setError(EGL_NOT_INITIALIZED, (EGLBoolean)EGL_FALSE);
    }

    // get a reference to the object passed in
    ContextRef _c(dp.get(), ctx);
    SurfaceRef _d(dp.get(), draw);
    SurfaceRef _r(dp.get(), read);

    // validate the context (if not EGL_NO_CONTEXT)
    if ((ctx != EGL_NO_CONTEXT) && !_c.get()) {
        // EGL_NO_CONTEXT is valid
        return setError(EGL_BAD_CONTEXT, (EGLBoolean)EGL_FALSE);
    }

    // these are the underlying implementation's object
    EGLContext impl_ctx  = EGL_NO_CONTEXT;
    EGLSurface impl_draw = EGL_NO_SURFACE;
    EGLSurface impl_read = EGL_NO_SURFACE;

    // these are our objects structs passed in
    egl_context_t       * c = NULL;
    egl_surface_t const * d = NULL;
    egl_surface_t const * r = NULL;

    // these are the current objects structs
    egl_context_t * cur_c = get_context(getContext());

    if (ctx != EGL_NO_CONTEXT) {
        c = get_context(ctx);
        impl_ctx = c->context;
    } else {
        // no context given, use the implementation of the current context
        if (draw != EGL_NO_SURFACE || read != EGL_NO_SURFACE) {
            // calling eglMakeCurrent( ..., !=0, !=0, EGL_NO_CONTEXT);
            return setError(EGL_BAD_MATCH, (EGLBoolean)EGL_FALSE);
        }
        if (cur_c == NULL) {
            // no current context
            // not an error, there is just no current context.
            return EGL_TRUE;
        }
    }

    // retrieve the underlying implementation's draw EGLSurface
    if (draw != EGL_NO_SURFACE) {
        if (!_d.get()) return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
        d = get_surface(draw);
        impl_draw = d->surface;
    }

    // retrieve the underlying implementation's read EGLSurface
    if (read != EGL_NO_SURFACE) {
        if (!_r.get()) return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
        r = get_surface(read);
        impl_read = r->surface;
    }


    EGLBoolean result = dp->makeCurrent(c, cur_c,
            draw, read, ctx,
            impl_draw, impl_read, impl_ctx);

    if (result == EGL_TRUE) {
        if (c) {
            setGLHooksThreadSpecific(c->cnx->hooks[c->version]);
            egl_tls_t::setContext(ctx);
            _c.acquire();
            _r.acquire();
            _d.acquire();
        } else {
            setGLHooksThreadSpecific(&gHooksNoContext);
            egl_tls_t::setContext(EGL_NO_CONTEXT);
        }
    } else {

        if (cur_c != NULL) {
            // Force return to current context for drivers that cannot handle errors
            EGLBoolean restore_result = EGL_FALSE;
            // get a reference to the old current objects
            ContextRef _c2(dp.get(), cur_c);
            SurfaceRef _d2(dp.get(), cur_c->draw);
            SurfaceRef _r2(dp.get(), cur_c->read);

            c = cur_c;
            impl_ctx = c->context;
            impl_draw = EGL_NO_SURFACE;
            if (cur_c->draw != EGL_NO_SURFACE) {
                d = get_surface(cur_c->draw);
                impl_draw = d->surface;
            }
            impl_read = EGL_NO_SURFACE;
            if (cur_c->read != EGL_NO_SURFACE) {
                r = get_surface(cur_c->read);
                impl_read = r->surface;
            }
            restore_result = dp->makeCurrent(c, cur_c,
                    cur_c->draw, cur_c->read, cur_c->context,
                    impl_draw, impl_read, impl_ctx);
            if (restore_result == EGL_TRUE) {
                _c2.acquire();
                _r2.acquire();
                _d2.acquire();
            } else {
                ALOGE("Could not restore original EGL context");
            }
        }
        // this will ALOGE the error
        egl_connection_t* const cnx = &gEGLImpl;
        result = setError(cnx->egl.eglGetError(), (EGLBoolean)EGL_FALSE);
    }
    return result;
}


EGLBoolean eglQueryContext( EGLDisplay dpy, EGLContext ctx,
                            EGLint attribute, EGLint *value)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    ContextRef _c(dp.get(), ctx);
    if (!_c.get()) return setError(EGL_BAD_CONTEXT, (EGLBoolean)EGL_FALSE);

    egl_context_t * const c = get_context(ctx);
    return c->cnx->egl.eglQueryContext(
            dp->disp.dpy, c->context, attribute, value);

}

EGLContext eglGetCurrentContext(void)
{
    // could be called before eglInitialize(), but we wouldn't have a context
    // then, and this function would correctly return EGL_NO_CONTEXT.

    clearError();

    EGLContext ctx = getContext();
    return ctx;
}

EGLSurface eglGetCurrentSurface(EGLint readdraw)
{
    // could be called before eglInitialize(), but we wouldn't have a context
    // then, and this function would correctly return EGL_NO_SURFACE.

    clearError();

    EGLContext ctx = getContext();
    if (ctx) {
        egl_context_t const * const c = get_context(ctx);
        if (!c) return setError(EGL_BAD_CONTEXT, EGL_NO_SURFACE);
        switch (readdraw) {
            case EGL_READ: return c->read;
            case EGL_DRAW: return c->draw;
            default: return setError(EGL_BAD_PARAMETER, EGL_NO_SURFACE);
        }
    }
    return EGL_NO_SURFACE;
}

EGLDisplay eglGetCurrentDisplay(void)
{
    // could be called before eglInitialize(), but we wouldn't have a context
    // then, and this function would correctly return EGL_NO_DISPLAY.

    clearError();

    EGLContext ctx = getContext();
    if (ctx) {
        egl_context_t const * const c = get_context(ctx);
        if (!c) return setError(EGL_BAD_CONTEXT, EGL_NO_SURFACE);
        return c->dpy;
    }
    return EGL_NO_DISPLAY;
}

EGLBoolean eglWaitGL(void)
{
    clearError();

    egl_connection_t* const cnx = &gEGLImpl;
    if (!cnx->dso)
        return setError(EGL_BAD_CONTEXT, (EGLBoolean)EGL_FALSE);

    return cnx->egl.eglWaitGL();
}

EGLBoolean eglWaitNative(EGLint engine)
{
    clearError();

    egl_connection_t* const cnx = &gEGLImpl;
    if (!cnx->dso)
        return setError(EGL_BAD_CONTEXT, (EGLBoolean)EGL_FALSE);

    return cnx->egl.eglWaitNative(engine);
}

EGLint eglGetError(void)
{
    EGLint err = EGL_SUCCESS;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso) {
        err = cnx->egl.eglGetError();
    }
    if (err == EGL_SUCCESS) {
        err = egl_tls_t::getError();
    }
    return err;
}

static __eglMustCastToProperFunctionPointerType findBuiltinWrapper(
        const char* procname) {
    const egl_connection_t* cnx = &gEGLImpl;
    void* proc = NULL;

    proc = dlsym(cnx->libEgl, procname);
    if (proc) return (__eglMustCastToProperFunctionPointerType)proc;

    proc = dlsym(cnx->libGles2, procname);
    if (proc) return (__eglMustCastToProperFunctionPointerType)proc;

    proc = dlsym(cnx->libGles1, procname);
    if (proc) return (__eglMustCastToProperFunctionPointerType)proc;

    return NULL;
}

__eglMustCastToProperFunctionPointerType eglGetProcAddress(const char *procname)
{
    // eglGetProcAddress() could be the very first function called
    // in which case we must make sure we've initialized ourselves, this
    // happens the first time egl_get_display() is called.

    clearError();

    if (egl_init_drivers() == EGL_FALSE) {
        setError(EGL_BAD_PARAMETER, NULL);
        return  NULL;
    }

    if (FILTER_EXTENSIONS(procname)) {
        return NULL;
    }

    __eglMustCastToProperFunctionPointerType addr;
    addr = findProcAddress(procname, sExtensionMap, NELEM(sExtensionMap));
    if (addr) return addr;

    addr = findBuiltinWrapper(procname);
    if (addr) return addr;

    // this protects accesses to sGLExtentionMap and sGLExtentionSlot
    pthread_mutex_lock(&sExtensionMapMutex);

        /*
         * Since eglGetProcAddress() is not associated to anything, it needs
         * to return a function pointer that "works" regardless of what
         * the current context is.
         *
         * For this reason, we return a "forwarder", a small stub that takes
         * care of calling the function associated with the context
         * currently bound.
         *
         * We first look for extensions we've already resolved, if we're seeing
         * this extension for the first time, we go through all our
         * implementations and call eglGetProcAddress() and record the
         * result in the appropriate implementation hooks and return the
         * address of the forwarder corresponding to that hook set.
         *
         */

        const std::string name(procname);

    auto& extentionMap = sGLExtentionMap;
    auto pos = extentionMap.find(name);
        addr = (pos != extentionMap.end()) ? pos->second : nullptr;
        const int slot = sGLExtentionSlot;

        ALOGE_IF(slot >= MAX_NUMBER_OF_GL_EXTENSIONS,
                "no more slots for eglGetProcAddress(\"%s\")",
                procname);

        if (!addr && (slot < MAX_NUMBER_OF_GL_EXTENSIONS)) {
            bool found = false;

            egl_connection_t* const cnx = &gEGLImpl;
            if (cnx->dso && cnx->egl.eglGetProcAddress) {
                // Extensions are independent of the bound context
                addr =
                cnx->hooks[egl_connection_t::GLESv1_INDEX]->ext.extensions[slot] =
                cnx->hooks[egl_connection_t::GLESv2_INDEX]->ext.extensions[slot] =
                        cnx->egl.eglGetProcAddress(procname);
                if (addr) found = true;
            }

            if (found) {
                addr = gExtensionForwarders[slot];
                extentionMap[name] = addr;
                sGLExtentionSlot++;
            }
        }

    pthread_mutex_unlock(&sExtensionMapMutex);
    return addr;
}

class FrameCompletionThread {
public:

    static void queueSync(EGLSyncKHR sync) {
        static FrameCompletionThread thread;

        char name[64];

        std::lock_guard<std::mutex> lock(thread.mMutex);
        snprintf(name, sizeof(name), "kicked off frame %u", (unsigned int)thread.mFramesQueued);
        ATRACE_NAME(name);

        thread.mQueue.push_back(sync);
        thread.mCondition.notify_one();
        thread.mFramesQueued++;
        ATRACE_INT("GPU Frames Outstanding", int32_t(thread.mQueue.size()));
    }

private:

    FrameCompletionThread() : mFramesQueued(0), mFramesCompleted(0) {
        std::thread thread(&FrameCompletionThread::loop, this);
        thread.detach();
    }

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wmissing-noreturn"
    void loop() {
        while (true) {
            threadLoop();
        }
    }
#pragma clang diagnostic pop

    void threadLoop() {
        EGLSyncKHR sync;
        uint32_t frameNum;
        {
            std::unique_lock<std::mutex> lock(mMutex);
            while (mQueue.empty()) {
                mCondition.wait(lock);
            }
            sync = mQueue[0];
            frameNum = mFramesCompleted;
        }
        EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        {
            char name[64];
            snprintf(name, sizeof(name), "waiting for frame %u", (unsigned int)frameNum);
            ATRACE_NAME(name);

            EGLint result = eglClientWaitSyncKHR(dpy, sync, 0, EGL_FOREVER_KHR);
            if (result == EGL_FALSE) {
                ALOGE("FrameCompletion: error waiting for fence: %#x", eglGetError());
            } else if (result == EGL_TIMEOUT_EXPIRED_KHR) {
                ALOGE("FrameCompletion: timeout waiting for fence");
            }
            eglDestroySyncKHR(dpy, sync);
        }
        {
            std::lock_guard<std::mutex> lock(mMutex);
            mQueue.pop_front();
            mFramesCompleted++;
            ATRACE_INT("GPU Frames Outstanding", int32_t(mQueue.size()));
        }
    }

    uint32_t mFramesQueued;
    uint32_t mFramesCompleted;
    std::deque<EGLSyncKHR> mQueue;
    std::condition_variable mCondition;
    std::mutex mMutex;
};

EGLBoolean eglSwapBuffersWithDamageKHR(EGLDisplay dpy, EGLSurface draw,
        EGLint *rects, EGLint n_rects)
{
    ATRACE_CALL();
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    SurfaceRef _s(dp.get(), draw);
    if (!_s.get())
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);

    egl_surface_t const * const s = get_surface(draw);

    if (CC_UNLIKELY(dp->traceGpuCompletion)) {
        EGLSyncKHR sync = eglCreateSyncKHR(dpy, EGL_SYNC_FENCE_KHR, NULL);
        if (sync != EGL_NO_SYNC_KHR) {
            FrameCompletionThread::queueSync(sync);
        }
    }

    if (CC_UNLIKELY(dp->finishOnSwap)) {
        uint32_t pixel;
        egl_context_t * const c = get_context( egl_tls_t::getContext() );
        if (c) {
            // glReadPixels() ensures that the frame is complete
            s->cnx->hooks[c->version]->gl.glReadPixels(0,0,1,1,
                    GL_RGBA,GL_UNSIGNED_BYTE,&pixel);
        }
    }

    if (n_rects == 0) {
        return s->cnx->egl.eglSwapBuffers(dp->disp.dpy, s->surface);
    }

    std::vector<android_native_rect_t> androidRects((size_t)n_rects);
    for (int r = 0; r < n_rects; ++r) {
        int offset = r * 4;
        int x = rects[offset];
        int y = rects[offset + 1];
        int width = rects[offset + 2];
        int height = rects[offset + 3];
        android_native_rect_t androidRect;
        androidRect.left = x;
        androidRect.top = y + height;
        androidRect.right = x + width;
        androidRect.bottom = y;
        androidRects.push_back(androidRect);
    }
    native_window_set_surface_damage(s->getNativeWindow(), androidRects.data(), androidRects.size());

    if (s->cnx->egl.eglSwapBuffersWithDamageKHR) {
        return s->cnx->egl.eglSwapBuffersWithDamageKHR(dp->disp.dpy, s->surface,
                rects, n_rects);
    } else {
        return s->cnx->egl.eglSwapBuffers(dp->disp.dpy, s->surface);
    }
}

EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface)
{
    return eglSwapBuffersWithDamageKHR(dpy, surface, NULL, 0);
}

EGLBoolean eglCopyBuffers(  EGLDisplay dpy, EGLSurface surface,
                            NativePixmapType target)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get())
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);

    egl_surface_t const * const s = get_surface(surface);
    return s->cnx->egl.eglCopyBuffers(dp->disp.dpy, s->surface, target);
}

const char* eglQueryString(EGLDisplay dpy, EGLint name)
{
    clearError();

    // Generate an error quietly when client extensions (as defined by
    // EGL_EXT_client_extensions) are queried.  We do not want to rely on
    // validate_display to generate the error as validate_display would log
    // the error, which can be misleading.
    //
    // If we want to support EGL_EXT_client_extensions later, we can return
    // the client extension string here instead.
    if (dpy == EGL_NO_DISPLAY && name == EGL_EXTENSIONS)
        return setErrorQuiet(EGL_BAD_DISPLAY, (const char*)0);

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return (const char *) NULL;

    switch (name) {
        case EGL_VENDOR:
            return dp->getVendorString();
        case EGL_VERSION:
            return dp->getVersionString();
        case EGL_EXTENSIONS:
            return dp->getExtensionString();
        case EGL_CLIENT_APIS:
            return dp->getClientApiString();
        default:
            break;
    }
    return setError(EGL_BAD_PARAMETER, (const char *)0);
}

extern "C" EGLAPI const char* eglQueryStringImplementationANDROID(EGLDisplay dpy, EGLint name)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return (const char *) NULL;

    switch (name) {
        case EGL_VENDOR:
            return dp->disp.queryString.vendor;
        case EGL_VERSION:
            return dp->disp.queryString.version;
        case EGL_EXTENSIONS:
            return dp->disp.queryString.extensions;
        case EGL_CLIENT_APIS:
            return dp->disp.queryString.clientApi;
        default:
            break;
    }
    return setError(EGL_BAD_PARAMETER, (const char *)0);
}

// ----------------------------------------------------------------------------
// EGL 1.1
// ----------------------------------------------------------------------------

EGLBoolean eglSurfaceAttrib(
        EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint value)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get())
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);

    egl_surface_t * const s = get_surface(surface);

    if (attribute == EGL_FRONT_BUFFER_AUTO_REFRESH_ANDROID) {
        if (!s->getNativeWindow()) {
            setError(EGL_BAD_SURFACE, EGL_FALSE);
        }
        int err = native_window_set_auto_refresh(s->getNativeWindow(), value != 0);
        return (err == 0) ? EGL_TRUE : setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    if (attribute == EGL_TIMESTAMPS_ANDROID) {
        if (!s->getNativeWindow()) {
            return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
        }
        int err = native_window_enable_frame_timestamps(s->getNativeWindow(), value != 0);
        return (err == 0) ? EGL_TRUE : setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    if (s->cnx->egl.eglSurfaceAttrib) {
        return s->cnx->egl.eglSurfaceAttrib(
                dp->disp.dpy, s->surface, attribute, value);
    }
    return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
}

EGLBoolean eglBindTexImage(
        EGLDisplay dpy, EGLSurface surface, EGLint buffer)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get())
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);

    egl_surface_t const * const s = get_surface(surface);
    if (s->cnx->egl.eglBindTexImage) {
        return s->cnx->egl.eglBindTexImage(
                dp->disp.dpy, s->surface, buffer);
    }
    return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
}

EGLBoolean eglReleaseTexImage(
        EGLDisplay dpy, EGLSurface surface, EGLint buffer)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get())
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);

    egl_surface_t const * const s = get_surface(surface);
    if (s->cnx->egl.eglReleaseTexImage) {
        return s->cnx->egl.eglReleaseTexImage(
                dp->disp.dpy, s->surface, buffer);
    }
    return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
}

EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean res = EGL_TRUE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglSwapInterval) {
        res = cnx->egl.eglSwapInterval(dp->disp.dpy, interval);
    }

    return res;
}


// ----------------------------------------------------------------------------
// EGL 1.2
// ----------------------------------------------------------------------------

EGLBoolean eglWaitClient(void)
{
    clearError();

    egl_connection_t* const cnx = &gEGLImpl;
    if (!cnx->dso)
        return setError(EGL_BAD_CONTEXT, (EGLBoolean)EGL_FALSE);

    EGLBoolean res;
    if (cnx->egl.eglWaitClient) {
        res = cnx->egl.eglWaitClient();
    } else {
        res = cnx->egl.eglWaitGL();
    }
    return res;
}

EGLBoolean eglBindAPI(EGLenum api)
{
    clearError();

    if (egl_init_drivers() == EGL_FALSE) {
        return setError(EGL_BAD_PARAMETER, (EGLBoolean)EGL_FALSE);
    }

    // bind this API on all EGLs
    EGLBoolean res = EGL_TRUE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglBindAPI) {
        res = cnx->egl.eglBindAPI(api);
    }
    return res;
}

EGLenum eglQueryAPI(void)
{
    clearError();

    if (egl_init_drivers() == EGL_FALSE) {
        return setError(EGL_BAD_PARAMETER, (EGLBoolean)EGL_FALSE);
    }

    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglQueryAPI) {
        return cnx->egl.eglQueryAPI();
    }

    // or, it can only be OpenGL ES
    return EGL_OPENGL_ES_API;
}

EGLBoolean eglReleaseThread(void)
{
    clearError();

    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglReleaseThread) {
        cnx->egl.eglReleaseThread();
    }

    // If there is context bound to the thread, release it
    egl_display_t::loseCurrent(get_context(getContext()));

    egl_tls_t::clearTLS();
    return EGL_TRUE;
}

EGLSurface eglCreatePbufferFromClientBuffer(
          EGLDisplay dpy, EGLenum buftype, EGLClientBuffer buffer,
          EGLConfig config, const EGLint *attrib_list)
{
    clearError();

    egl_connection_t* cnx = NULL;
    const egl_display_ptr dp = validate_display_connection(dpy, cnx);
    if (!dp) return EGL_FALSE;
    if (cnx->egl.eglCreatePbufferFromClientBuffer) {
        return cnx->egl.eglCreatePbufferFromClientBuffer(
                dp->disp.dpy, buftype, buffer, config, attrib_list);
    }
    return setError(EGL_BAD_CONFIG, EGL_NO_SURFACE);
}

// ----------------------------------------------------------------------------
// EGL_EGLEXT_VERSION 3
// ----------------------------------------------------------------------------

EGLBoolean eglLockSurfaceKHR(EGLDisplay dpy, EGLSurface surface,
        const EGLint *attrib_list)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get())
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);

    egl_surface_t const * const s = get_surface(surface);
    if (s->cnx->egl.eglLockSurfaceKHR) {
        return s->cnx->egl.eglLockSurfaceKHR(
                dp->disp.dpy, s->surface, attrib_list);
    }
    return setError(EGL_BAD_DISPLAY, (EGLBoolean)EGL_FALSE);
}

EGLBoolean eglUnlockSurfaceKHR(EGLDisplay dpy, EGLSurface surface)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get())
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);

    egl_surface_t const * const s = get_surface(surface);
    if (s->cnx->egl.eglUnlockSurfaceKHR) {
        return s->cnx->egl.eglUnlockSurfaceKHR(dp->disp.dpy, s->surface);
    }
    return setError(EGL_BAD_DISPLAY, (EGLBoolean)EGL_FALSE);
}

EGLImageKHR eglCreateImageKHR(EGLDisplay dpy, EGLContext ctx, EGLenum target,
        EGLClientBuffer buffer, const EGLint *attrib_list)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_NO_IMAGE_KHR;

    ContextRef _c(dp.get(), ctx);
    egl_context_t * const c = _c.get();

    EGLImageKHR result = EGL_NO_IMAGE_KHR;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglCreateImageKHR) {
        result = cnx->egl.eglCreateImageKHR(
                dp->disp.dpy,
                c ? c->context : EGL_NO_CONTEXT,
                target, buffer, attrib_list);
    }
    return result;
}

EGLBoolean eglDestroyImageKHR(EGLDisplay dpy, EGLImageKHR img)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglDestroyImageKHR) {
        result = cnx->egl.eglDestroyImageKHR(dp->disp.dpy, img);
    }
    return result;
}

// ----------------------------------------------------------------------------
// EGL_EGLEXT_VERSION 5
// ----------------------------------------------------------------------------


EGLSyncKHR eglCreateSyncKHR(EGLDisplay dpy, EGLenum type, const EGLint *attrib_list)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_NO_SYNC_KHR;

    EGLSyncKHR result = EGL_NO_SYNC_KHR;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglCreateSyncKHR) {
        result = cnx->egl.eglCreateSyncKHR(dp->disp.dpy, type, attrib_list);
    }
    return result;
}

EGLBoolean eglDestroySyncKHR(EGLDisplay dpy, EGLSyncKHR sync)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglDestroySyncKHR) {
        result = cnx->egl.eglDestroySyncKHR(dp->disp.dpy, sync);
    }
    return result;
}

EGLBoolean eglSignalSyncKHR(EGLDisplay dpy, EGLSyncKHR sync, EGLenum mode) {
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglSignalSyncKHR) {
        result = cnx->egl.eglSignalSyncKHR(
                dp->disp.dpy, sync, mode);
    }
    return result;
}

EGLint eglClientWaitSyncKHR(EGLDisplay dpy, EGLSyncKHR sync,
        EGLint flags, EGLTimeKHR timeout)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLint result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglClientWaitSyncKHR) {
        result = cnx->egl.eglClientWaitSyncKHR(
                dp->disp.dpy, sync, flags, timeout);
    }
    return result;
}

EGLBoolean eglGetSyncAttribKHR(EGLDisplay dpy, EGLSyncKHR sync,
        EGLint attribute, EGLint *value)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglGetSyncAttribKHR) {
        result = cnx->egl.eglGetSyncAttribKHR(
                dp->disp.dpy, sync, attribute, value);
    }
    return result;
}

EGLStreamKHR eglCreateStreamKHR(EGLDisplay dpy, const EGLint *attrib_list)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_NO_STREAM_KHR;

    EGLStreamKHR result = EGL_NO_STREAM_KHR;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglCreateStreamKHR) {
        result = cnx->egl.eglCreateStreamKHR(
                dp->disp.dpy, attrib_list);
    }
    return result;
}

EGLBoolean eglDestroyStreamKHR(EGLDisplay dpy, EGLStreamKHR stream)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglDestroyStreamKHR) {
        result = cnx->egl.eglDestroyStreamKHR(
                dp->disp.dpy, stream);
    }
    return result;
}

EGLBoolean eglStreamAttribKHR(EGLDisplay dpy, EGLStreamKHR stream,
        EGLenum attribute, EGLint value)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglStreamAttribKHR) {
        result = cnx->egl.eglStreamAttribKHR(
                dp->disp.dpy, stream, attribute, value);
    }
    return result;
}

EGLBoolean eglQueryStreamKHR(EGLDisplay dpy, EGLStreamKHR stream,
        EGLenum attribute, EGLint *value)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglQueryStreamKHR) {
        result = cnx->egl.eglQueryStreamKHR(
                dp->disp.dpy, stream, attribute, value);
    }
    return result;
}

EGLBoolean eglQueryStreamu64KHR(EGLDisplay dpy, EGLStreamKHR stream,
        EGLenum attribute, EGLuint64KHR *value)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglQueryStreamu64KHR) {
        result = cnx->egl.eglQueryStreamu64KHR(
                dp->disp.dpy, stream, attribute, value);
    }
    return result;
}

EGLBoolean eglQueryStreamTimeKHR(EGLDisplay dpy, EGLStreamKHR stream,
        EGLenum attribute, EGLTimeKHR *value)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglQueryStreamTimeKHR) {
        result = cnx->egl.eglQueryStreamTimeKHR(
                dp->disp.dpy, stream, attribute, value);
    }
    return result;
}

EGLSurface eglCreateStreamProducerSurfaceKHR(EGLDisplay dpy, EGLConfig config,
        EGLStreamKHR stream, const EGLint *attrib_list)
{
    clearError();

    egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_NO_SURFACE;

    EGLint colorSpace = EGL_GL_COLORSPACE_LINEAR_KHR;
    android_dataspace dataSpace = HAL_DATASPACE_UNKNOWN;
    // TODO: Probably need to update EGL_KHR_stream_producer_eglsurface to
    // indicate support for EGL_GL_COLORSPACE_KHR.
    // now select a corresponding sRGB format if needed
    if (!getColorSpaceAttribute(dp, attrib_list, colorSpace, dataSpace)) {
        ALOGE("error invalid colorspace: %d", colorSpace);
        return setError(EGL_BAD_ATTRIBUTE, EGL_NO_SURFACE);
    }

    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglCreateStreamProducerSurfaceKHR) {
        EGLSurface surface = cnx->egl.eglCreateStreamProducerSurfaceKHR(
                dp->disp.dpy, config, stream, attrib_list);
        if (surface != EGL_NO_SURFACE) {
            egl_surface_t* s = new egl_surface_t(dp.get(), config, NULL, surface, colorSpace, cnx);
            return s;
        }
    }
    return EGL_NO_SURFACE;
}

EGLBoolean eglStreamConsumerGLTextureExternalKHR(EGLDisplay dpy,
        EGLStreamKHR stream)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglStreamConsumerGLTextureExternalKHR) {
        result = cnx->egl.eglStreamConsumerGLTextureExternalKHR(
                dp->disp.dpy, stream);
    }
    return result;
}

EGLBoolean eglStreamConsumerAcquireKHR(EGLDisplay dpy,
        EGLStreamKHR stream)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglStreamConsumerAcquireKHR) {
        result = cnx->egl.eglStreamConsumerAcquireKHR(
                dp->disp.dpy, stream);
    }
    return result;
}

EGLBoolean eglStreamConsumerReleaseKHR(EGLDisplay dpy,
        EGLStreamKHR stream)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;

    EGLBoolean result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglStreamConsumerReleaseKHR) {
        result = cnx->egl.eglStreamConsumerReleaseKHR(
                dp->disp.dpy, stream);
    }
    return result;
}

EGLNativeFileDescriptorKHR eglGetStreamFileDescriptorKHR(
        EGLDisplay dpy, EGLStreamKHR stream)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_NO_FILE_DESCRIPTOR_KHR;

    EGLNativeFileDescriptorKHR result = EGL_NO_FILE_DESCRIPTOR_KHR;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglGetStreamFileDescriptorKHR) {
        result = cnx->egl.eglGetStreamFileDescriptorKHR(
                dp->disp.dpy, stream);
    }
    return result;
}

EGLStreamKHR eglCreateStreamFromFileDescriptorKHR(
        EGLDisplay dpy, EGLNativeFileDescriptorKHR file_descriptor)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_NO_STREAM_KHR;

    EGLStreamKHR result = EGL_NO_STREAM_KHR;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglCreateStreamFromFileDescriptorKHR) {
        result = cnx->egl.eglCreateStreamFromFileDescriptorKHR(
                dp->disp.dpy, file_descriptor);
    }
    return result;
}

// ----------------------------------------------------------------------------
// EGL_EGLEXT_VERSION 15
// ----------------------------------------------------------------------------

EGLint eglWaitSyncKHR(EGLDisplay dpy, EGLSyncKHR sync, EGLint flags) {
    clearError();
    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_FALSE;
    EGLint result = EGL_FALSE;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglWaitSyncKHR) {
        result = cnx->egl.eglWaitSyncKHR(dp->disp.dpy, sync, flags);
    }
    return result;
}

// ----------------------------------------------------------------------------
// ANDROID extensions
// ----------------------------------------------------------------------------

EGLint eglDupNativeFenceFDANDROID(EGLDisplay dpy, EGLSyncKHR sync)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) return EGL_NO_NATIVE_FENCE_FD_ANDROID;

    EGLint result = EGL_NO_NATIVE_FENCE_FD_ANDROID;
    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->egl.eglDupNativeFenceFDANDROID) {
        result = cnx->egl.eglDupNativeFenceFDANDROID(dp->disp.dpy, sync);
    }
    return result;
}

EGLBoolean eglPresentationTimeANDROID(EGLDisplay dpy, EGLSurface surface,
        EGLnsecsANDROID time)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) {
        return EGL_FALSE;
    }

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get()) {
        setError(EGL_BAD_SURFACE, EGL_FALSE);
        return EGL_FALSE;
    }

    egl_surface_t const * const s = get_surface(surface);
    native_window_set_buffers_timestamp(s->getNativeWindow(), time);

    return EGL_TRUE;
}

EGLClientBuffer eglGetNativeClientBufferANDROID(const AHardwareBuffer *buffer) {
    clearError();
    // AHardwareBuffer_to_ANativeWindowBuffer is a platform-only symbol and thus
    // this function cannot be implemented when this libEGL is built for
    // vendors.
#ifndef __ANDROID_VNDK__
    if (!buffer) return setError(EGL_BAD_PARAMETER, (EGLClientBuffer)0);
    return const_cast<ANativeWindowBuffer *>(AHardwareBuffer_to_ANativeWindowBuffer(buffer));
#else
    return setError(EGL_BAD_PARAMETER, (EGLClientBuffer)0);
#endif
}

// ----------------------------------------------------------------------------
// NVIDIA extensions
// ----------------------------------------------------------------------------
EGLuint64NV eglGetSystemTimeFrequencyNV()
{
    clearError();

    if (egl_init_drivers() == EGL_FALSE) {
        return setError(EGL_BAD_PARAMETER, (EGLuint64NV)EGL_FALSE);
    }

    EGLuint64NV ret = 0;
    egl_connection_t* const cnx = &gEGLImpl;

    if (cnx->dso && cnx->egl.eglGetSystemTimeFrequencyNV) {
        return cnx->egl.eglGetSystemTimeFrequencyNV();
    }

    return setErrorQuiet(EGL_BAD_DISPLAY, (EGLuint64NV)0);
}

EGLuint64NV eglGetSystemTimeNV()
{
    clearError();

    if (egl_init_drivers() == EGL_FALSE) {
        return setError(EGL_BAD_PARAMETER, (EGLuint64NV)EGL_FALSE);
    }

    EGLuint64NV ret = 0;
    egl_connection_t* const cnx = &gEGLImpl;

    if (cnx->dso && cnx->egl.eglGetSystemTimeNV) {
        return cnx->egl.eglGetSystemTimeNV();
    }

    return setErrorQuiet(EGL_BAD_DISPLAY, (EGLuint64NV)0);
}

// ----------------------------------------------------------------------------
// Partial update extension
// ----------------------------------------------------------------------------
EGLBoolean eglSetDamageRegionKHR(EGLDisplay dpy, EGLSurface surface,
        EGLint *rects, EGLint n_rects)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) {
        setError(EGL_BAD_DISPLAY, EGL_FALSE);
        return EGL_FALSE;
    }

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get()) {
        setError(EGL_BAD_SURFACE, EGL_FALSE);
        return EGL_FALSE;
    }

    egl_surface_t const * const s = get_surface(surface);
    if (s->cnx->egl.eglSetDamageRegionKHR) {
        return s->cnx->egl.eglSetDamageRegionKHR(dp->disp.dpy, s->surface,
                rects, n_rects);
    }

    return EGL_FALSE;
}

EGLBoolean eglGetNextFrameIdANDROID(EGLDisplay dpy, EGLSurface surface,
            EGLuint64KHR *frameId) {
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) {
        return setError(EGL_BAD_DISPLAY, (EGLBoolean)EGL_FALSE);
    }

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get()) {
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    egl_surface_t const * const s = get_surface(surface);

    if (!s->getNativeWindow()) {
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    uint64_t nextFrameId = 0;
    int ret = native_window_get_next_frame_id(s->getNativeWindow(), &nextFrameId);

    if (ret != 0) {
        // This should not happen. Return an error that is not in the spec
        // so it's obvious something is very wrong.
        ALOGE("eglGetNextFrameId: Unexpected error.");
        return setError(EGL_NOT_INITIALIZED, (EGLBoolean)EGL_FALSE);
    }

    *frameId = nextFrameId;
    return EGL_TRUE;
}

EGLBoolean eglGetCompositorTimingANDROID(EGLDisplay dpy, EGLSurface surface,
        EGLint numTimestamps, const EGLint *names, EGLnsecsANDROID *values)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) {
        return setError(EGL_BAD_DISPLAY, (EGLBoolean)EGL_FALSE);
    }

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get()) {
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    egl_surface_t const * const s = get_surface(surface);

    if (!s->getNativeWindow()) {
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    nsecs_t* compositeDeadline = nullptr;
    nsecs_t* compositeInterval = nullptr;
    nsecs_t* compositeToPresentLatency = nullptr;

    for (int i = 0; i < numTimestamps; i++) {
        switch (names[i]) {
            case EGL_COMPOSITE_DEADLINE_ANDROID:
                compositeDeadline = &values[i];
                break;
            case EGL_COMPOSITE_INTERVAL_ANDROID:
                compositeInterval = &values[i];
                break;
            case EGL_COMPOSITE_TO_PRESENT_LATENCY_ANDROID:
                compositeToPresentLatency = &values[i];
                break;
            default:
                return setError(EGL_BAD_PARAMETER, (EGLBoolean)EGL_FALSE);
        }
    }

    int ret = native_window_get_compositor_timing(s->getNativeWindow(),
            compositeDeadline, compositeInterval, compositeToPresentLatency);

    switch (ret) {
      case 0:
        return EGL_TRUE;
      case -ENOSYS:
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
      default:
        // This should not happen. Return an error that is not in the spec
        // so it's obvious something is very wrong.
        ALOGE("eglGetCompositorTiming: Unexpected error.");
        return setError(EGL_NOT_INITIALIZED, (EGLBoolean)EGL_FALSE);
    }
}

EGLBoolean eglGetCompositorTimingSupportedANDROID(
        EGLDisplay dpy, EGLSurface surface, EGLint name)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) {
        return setError(EGL_BAD_DISPLAY, (EGLBoolean)EGL_FALSE);
    }

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get()) {
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    egl_surface_t const * const s = get_surface(surface);

    ANativeWindow* window = s->getNativeWindow();
    if (!window) {
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    switch (name) {
        case EGL_COMPOSITE_DEADLINE_ANDROID:
        case EGL_COMPOSITE_INTERVAL_ANDROID:
        case EGL_COMPOSITE_TO_PRESENT_LATENCY_ANDROID:
            return EGL_TRUE;
        default:
            return EGL_FALSE;
    }
}

EGLBoolean eglGetFrameTimestampsANDROID(EGLDisplay dpy, EGLSurface surface,
        EGLuint64KHR frameId, EGLint numTimestamps, const EGLint *timestamps,
        EGLnsecsANDROID *values)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) {
        return setError(EGL_BAD_DISPLAY, (EGLBoolean)EGL_FALSE);
    }

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get()) {
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    egl_surface_t const * const s = get_surface(surface);

    if (!s->getNativeWindow()) {
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    nsecs_t* requestedPresentTime = nullptr;
    nsecs_t* acquireTime = nullptr;
    nsecs_t* latchTime = nullptr;
    nsecs_t* firstRefreshStartTime = nullptr;
    nsecs_t* gpuCompositionDoneTime = nullptr;
    nsecs_t* lastRefreshStartTime = nullptr;
    nsecs_t* displayPresentTime = nullptr;
    nsecs_t* dequeueReadyTime = nullptr;
    nsecs_t* releaseTime = nullptr;

    for (int i = 0; i < numTimestamps; i++) {
        switch (timestamps[i]) {
            case EGL_REQUESTED_PRESENT_TIME_ANDROID:
                requestedPresentTime = &values[i];
                break;
            case EGL_RENDERING_COMPLETE_TIME_ANDROID:
                acquireTime = &values[i];
                break;
            case EGL_COMPOSITION_LATCH_TIME_ANDROID:
                latchTime = &values[i];
                break;
            case EGL_FIRST_COMPOSITION_START_TIME_ANDROID:
                firstRefreshStartTime = &values[i];
                break;
            case EGL_LAST_COMPOSITION_START_TIME_ANDROID:
                lastRefreshStartTime = &values[i];
                break;
            case EGL_FIRST_COMPOSITION_GPU_FINISHED_TIME_ANDROID:
                gpuCompositionDoneTime = &values[i];
                break;
            case EGL_DISPLAY_PRESENT_TIME_ANDROID:
                displayPresentTime = &values[i];
                break;
            case EGL_DEQUEUE_READY_TIME_ANDROID:
                dequeueReadyTime = &values[i];
                break;
            case EGL_READS_DONE_TIME_ANDROID:
                releaseTime = &values[i];
                break;
            default:
                return setError(EGL_BAD_PARAMETER, (EGLBoolean)EGL_FALSE);
        }
    }

    int ret = native_window_get_frame_timestamps(s->getNativeWindow(), frameId,
            requestedPresentTime, acquireTime, latchTime, firstRefreshStartTime,
            lastRefreshStartTime, gpuCompositionDoneTime, displayPresentTime,
            dequeueReadyTime, releaseTime);

    switch (ret) {
        case 0:
            return EGL_TRUE;
        case -ENOENT:
            return setError(EGL_BAD_ACCESS, (EGLBoolean)EGL_FALSE);
        case -ENOSYS:
            return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
        case -EINVAL:
            return setError(EGL_BAD_PARAMETER, (EGLBoolean)EGL_FALSE);
        default:
            // This should not happen. Return an error that is not in the spec
            // so it's obvious something is very wrong.
            ALOGE("eglGetFrameTimestamps: Unexpected error.");
            return setError(EGL_NOT_INITIALIZED, (EGLBoolean)EGL_FALSE);
    }
}

EGLBoolean eglGetFrameTimestampSupportedANDROID(
        EGLDisplay dpy, EGLSurface surface, EGLint timestamp)
{
    clearError();

    const egl_display_ptr dp = validate_display(dpy);
    if (!dp) {
        return setError(EGL_BAD_DISPLAY, (EGLBoolean)EGL_FALSE);
    }

    SurfaceRef _s(dp.get(), surface);
    if (!_s.get()) {
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    egl_surface_t const * const s = get_surface(surface);

    ANativeWindow* window = s->getNativeWindow();
    if (!window) {
        return setError(EGL_BAD_SURFACE, (EGLBoolean)EGL_FALSE);
    }

    switch (timestamp) {
        case EGL_COMPOSITE_DEADLINE_ANDROID:
        case EGL_COMPOSITE_INTERVAL_ANDROID:
        case EGL_COMPOSITE_TO_PRESENT_LATENCY_ANDROID:
        case EGL_REQUESTED_PRESENT_TIME_ANDROID:
        case EGL_RENDERING_COMPLETE_TIME_ANDROID:
        case EGL_COMPOSITION_LATCH_TIME_ANDROID:
        case EGL_FIRST_COMPOSITION_START_TIME_ANDROID:
        case EGL_LAST_COMPOSITION_START_TIME_ANDROID:
        case EGL_FIRST_COMPOSITION_GPU_FINISHED_TIME_ANDROID:
        case EGL_DEQUEUE_READY_TIME_ANDROID:
        case EGL_READS_DONE_TIME_ANDROID:
            return EGL_TRUE;
        case EGL_DISPLAY_PRESENT_TIME_ANDROID: {
            int value = 0;
            window->query(window,
                    NATIVE_WINDOW_FRAME_TIMESTAMPS_SUPPORTS_PRESENT, &value);
            return value == 0 ? EGL_FALSE : EGL_TRUE;
        }
        default:
            return EGL_FALSE;
    }
}
