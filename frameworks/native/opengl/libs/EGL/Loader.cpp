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

//#define LOG_NDEBUG 0
#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#include "Loader.h"

#include <string>

#include <dirent.h>
#include <dlfcn.h>

#include <android/dlext.h>
#include <cutils/properties.h>
#include <log/log.h>

#ifndef __ANDROID_VNDK__
#include <graphicsenv/GraphicsEnv.h>
#endif
#include <vndksupport/linker.h>

#include "egl_trace.h"
#include "egldefs.h"

extern "C" {
  android_namespace_t* android_get_exported_namespace(const char*);
}

// ----------------------------------------------------------------------------
namespace android {
// ----------------------------------------------------------------------------


/*
 * EGL userspace drivers must be provided either:
 * - as a single library:
 *      /vendor/lib/egl/libGLES.so
 *
 * - as separate libraries:
 *      /vendor/lib/egl/libEGL.so
 *      /vendor/lib/egl/libGLESv1_CM.so
 *      /vendor/lib/egl/libGLESv2.so
 *
 * The software renderer for the emulator must be provided as a single
 * library at:
 *
 *      /system/lib/egl/libGLES_android.so
 *
 *
 * For backward compatibility and to facilitate the transition to
 * this new naming scheme, the loader will additionally look for:
 *
 *      /{vendor|system}/lib/egl/lib{GLES | [EGL|GLESv1_CM|GLESv2]}_*.so
 *
 */

Loader& Loader::getInstance() {
    static Loader loader;
    return loader;
}

/* This function is called to check whether we run inside the emulator,
 * and if this is the case whether GLES GPU emulation is supported.
 *
 * Returned values are:
 *  -1   -> not running inside the emulator
 *   0   -> running inside the emulator, but GPU emulation not supported
 *   1   -> running inside the emulator, GPU emulation is supported
 *          through the "emulation" host-side OpenGL ES implementation.
 *   2   -> running inside the emulator, GPU emulation is supported
 *          through a guest-side vendor driver's OpenGL ES implementation.
 */
static int
checkGlesEmulationStatus(void)
{
    /* We're going to check for the following kernel parameters:
     *
     *    qemu=1                      -> tells us that we run inside the emulator
     *    android.qemu.gles=<number>  -> tells us the GLES GPU emulation status
     *
     * Note that we will return <number> if we find it. This let us support
     * more additionnal emulation modes in the future.
     */
    char  prop[PROPERTY_VALUE_MAX];
    int   result = -1;

    /* First, check for qemu=1 */
    property_get("ro.kernel.qemu",prop,"0");
    if (atoi(prop) != 1)
        return -1;

    /* We are in the emulator, get GPU status value */
    property_get("qemu.gles",prop,"0");
    return atoi(prop);
}

static void* do_dlopen(const char* path, int mode) {
    ATRACE_CALL();
    return dlopen(path, mode);
}

static void* do_android_dlopen_ext(const char* path, int mode, const android_dlextinfo* info) {
    ATRACE_CALL();
    return android_dlopen_ext(path, mode, info);
}

static void* do_android_load_sphal_library(const char* path, int mode) {
    ATRACE_CALL();
    return android_load_sphal_library(path, mode);
}

// ----------------------------------------------------------------------------

Loader::driver_t::driver_t(void* gles)
{
    dso[0] = gles;
    for (size_t i=1 ; i<NELEM(dso) ; i++)
        dso[i] = 0;
}

Loader::driver_t::~driver_t()
{
    for (size_t i=0 ; i<NELEM(dso) ; i++) {
        if (dso[i]) {
            dlclose(dso[i]);
            dso[i] = 0;
        }
    }
}

int Loader::driver_t::set(void* hnd, int32_t api)
{
    switch (api) {
        case EGL:
            dso[0] = hnd;
            break;
        case GLESv1_CM:
            dso[1] = hnd;
            break;
        case GLESv2:
            dso[2] = hnd;
            break;
        default:
            return -EOVERFLOW;
    }
    return 0;
}

// ----------------------------------------------------------------------------

Loader::Loader()
    : getProcAddress(NULL)
{
}

Loader::~Loader() {
}

static void* load_wrapper(const char* path) {
    void* so = do_dlopen(path, RTLD_NOW | RTLD_LOCAL);
    ALOGE_IF(!so, "dlopen(\"%s\") failed: %s", path, dlerror());
    return so;
}

#ifndef EGL_WRAPPER_DIR
#if defined(__LP64__)
#define EGL_WRAPPER_DIR "/system/lib64"
#else
#define EGL_WRAPPER_DIR "/system/lib"
#endif
#endif

static void setEmulatorGlesValue(void) {
    char prop[PROPERTY_VALUE_MAX];
    property_get("ro.kernel.qemu", prop, "0");
    if (atoi(prop) != 1) return;

    property_get("ro.kernel.qemu.gles",prop,"0");
    if (atoi(prop) == 1) {
        ALOGD("Emulator has host GPU support, qemu.gles is set to 1.");
        property_set("qemu.gles", "1");
        return;
    }

    // for now, checking the following
    // directory is good enough for emulator system images
    const char* vendor_lib_path =
#if defined(__LP64__)
        "/vendor/lib64/egl";
#else
        "/vendor/lib/egl";
#endif

    const bool has_vendor_lib = (access(vendor_lib_path, R_OK) == 0);
    if (has_vendor_lib) {
        ALOGD("Emulator has vendor provided software renderer, qemu.gles is set to 2.");
        property_set("qemu.gles", "2");
    } else {
        ALOGD("Emulator without GPU support detected. "
              "Fallback to legacy software renderer, qemu.gles is set to 0.");
        property_set("qemu.gles", "0");
    }
}

void* Loader::open(egl_connection_t* cnx)
{
    ATRACE_CALL();

    void* dso;
    driver_t* hnd = 0;

    setEmulatorGlesValue();

    dso = load_driver("GLES", cnx, EGL | GLESv1_CM | GLESv2);
    if (dso) {
        hnd = new driver_t(dso);
    } else {
        // Always load EGL first
        dso = load_driver("EGL", cnx, EGL);
        if (dso) {
            hnd = new driver_t(dso);
            hnd->set( load_driver("GLESv1_CM", cnx, GLESv1_CM), GLESv1_CM );
            hnd->set( load_driver("GLESv2",    cnx, GLESv2),    GLESv2 );
        }
    }

    LOG_ALWAYS_FATAL_IF(!hnd, "couldn't find an OpenGL ES implementation");

    cnx->libEgl   = load_wrapper(EGL_WRAPPER_DIR "/libEGL.so");
    cnx->libGles2 = load_wrapper(EGL_WRAPPER_DIR "/libGLESv2.so");
    cnx->libGles1 = load_wrapper(EGL_WRAPPER_DIR "/libGLESv1_CM.so");

    LOG_ALWAYS_FATAL_IF(!cnx->libEgl,
            "couldn't load system EGL wrapper libraries");

    LOG_ALWAYS_FATAL_IF(!cnx->libGles2 || !cnx->libGles1,
            "couldn't load system OpenGL ES wrapper libraries");

    return (void*)hnd;
}

void Loader::close(void* driver)
{
    driver_t* hnd = (driver_t*)driver;
    delete hnd;
}

void Loader::init_api(void* dso,
        char const * const * api,
        __eglMustCastToProperFunctionPointerType* curr,
        getProcAddressType getProcAddress)
{
    ATRACE_CALL();

    const ssize_t SIZE = 256;
    char scrap[SIZE];
    while (*api) {
        char const * name = *api;
        __eglMustCastToProperFunctionPointerType f =
            (__eglMustCastToProperFunctionPointerType)dlsym(dso, name);
        if (f == NULL) {
            // couldn't find the entry-point, use eglGetProcAddress()
            f = getProcAddress(name);
        }
        if (f == NULL) {
            // Try without the OES postfix
            ssize_t index = ssize_t(strlen(name)) - 3;
            if ((index>0 && (index<SIZE-1)) && (!strcmp(name+index, "OES"))) {
                strncpy(scrap, name, index);
                scrap[index] = 0;
                f = (__eglMustCastToProperFunctionPointerType)dlsym(dso, scrap);
                //ALOGD_IF(f, "found <%s> instead", scrap);
            }
        }
        if (f == NULL) {
            // Try with the OES postfix
            ssize_t index = ssize_t(strlen(name)) - 3;
            if (index>0 && strcmp(name+index, "OES")) {
                snprintf(scrap, SIZE, "%sOES", name);
                f = (__eglMustCastToProperFunctionPointerType)dlsym(dso, scrap);
                //ALOGD_IF(f, "found <%s> instead", scrap);
            }
        }
        if (f == NULL) {
            //ALOGD("%s", name);
            f = (__eglMustCastToProperFunctionPointerType)gl_unimplemented;

            /*
             * GL_EXT_debug_label is special, we always report it as
             * supported, it's handled by GLES_trace. If GLES_trace is not
             * enabled, then these are no-ops.
             */
            if (!strcmp(name, "glInsertEventMarkerEXT")) {
                f = (__eglMustCastToProperFunctionPointerType)gl_noop;
            } else if (!strcmp(name, "glPushGroupMarkerEXT")) {
                f = (__eglMustCastToProperFunctionPointerType)gl_noop;
            } else if (!strcmp(name, "glPopGroupMarkerEXT")) {
                f = (__eglMustCastToProperFunctionPointerType)gl_noop;
            }
        }
        *curr++ = f;
        api++;
    }
}

static void* load_system_driver(const char* kind) {
    ATRACE_CALL();
    class MatchFile {
    public:
        static std::string find(const char* kind) {
            std::string result;
            int emulationStatus = checkGlesEmulationStatus();
            switch (emulationStatus) {
                case 0:
#if defined(__LP64__)
                    result = "/vendor/lib64/egl/libGLES_android.so";
#else
                    result = "/vendor/lib/egl/libGLES_android.so";
#endif
                    return result;
                case 1:
                    // Use host-side OpenGL through the "emulation" library
#if defined(__LP64__)
                    result = std::string("/vendor/lib64/egl/lib") + kind + "_emulation.so";
#else
                    result = std::string("/vendor/lib/egl/lib") + kind + "_emulation.so";
#endif
                    return result;
                default:
                    // Not in emulator, or use other guest-side implementation
                    break;
            }

            std::string pattern = std::string("lib") + kind;
            const char* const searchPaths[] = {
#if defined(__LP64__)
                    "/vendor/lib64/egl",
                    "/system/lib64/egl"
#else
                    "/vendor/lib/egl",
                    "/system/lib/egl"
#endif
            };

            // first, we search for the exact name of the GLES userspace
            // driver in both locations.
            // i.e.:
            //      libGLES.so, or:
            //      libEGL.so, libGLESv1_CM.so, libGLESv2.so

            for (size_t i=0 ; i<NELEM(searchPaths) ; i++) {
                if (find(result, pattern, searchPaths[i], true)) {
                    return result;
                }
            }

            // for compatibility with the old "egl.cfg" naming convention
            // we look for files that match:
            //      libGLES_*.so, or:
            //      libEGL_*.so, libGLESv1_CM_*.so, libGLESv2_*.so

            pattern.append("_");
            for (size_t i=0 ; i<NELEM(searchPaths) ; i++) {
                if (find(result, pattern, searchPaths[i], false)) {
                    return result;
                }
            }

            // we didn't find the driver. gah.
            result.clear();
            return result;
        }

    private:
        static bool find(std::string& result,
                const std::string& pattern, const char* const search, bool exact) {
            if (exact) {
                std::string absolutePath = std::string(search) + "/" + pattern;
                if (!access(absolutePath.c_str(), R_OK)) {
                    result = absolutePath;
                    return true;
                }
                return false;
            }

            DIR* d = opendir(search);
            if (d != NULL) {
                struct dirent cur;
                struct dirent* e;
                while (readdir_r(d, &cur, &e) == 0 && e) {
                    if (e->d_type == DT_DIR) {
                        continue;
                    }
                    if (!strcmp(e->d_name, "libGLES_android.so")) {
                        // always skip the software renderer
                        continue;
                    }
                    if (strstr(e->d_name, pattern.c_str()) == e->d_name) {
                        if (!strcmp(e->d_name + strlen(e->d_name) - 3, ".so")) {
                            result = std::string(search) + "/" + e->d_name;
                            closedir(d);
                            return true;
                        }
                    }
                }
                closedir(d);
            }
            return false;
        }
    };


    std::string absolutePath = MatchFile::find(kind);
    if (absolutePath.empty()) {
        // this happens often, we don't want to log an error
        return 0;
    }
    const char* const driver_absolute_path = absolutePath.c_str();

    // Try to load drivers from the 'sphal' namespace, if it exist. Fall back to
    // the original routine when the namespace does not exist.
    // See /system/core/rootdir/etc/ld.config.txt for the configuration of the
    // sphal namespace.
    void* dso = do_android_load_sphal_library(driver_absolute_path,
                                              RTLD_NOW | RTLD_LOCAL);
    if (dso == 0) {
        const char* err = dlerror();
        ALOGE("load_driver(%s): %s", driver_absolute_path, err ? err : "unknown");
        return 0;
    }

    ALOGD("loaded %s", driver_absolute_path);

    return dso;
}

static const char* HAL_SUBNAME_KEY_PROPERTIES[2] = {
    "ro.hardware.egl",
    "ro.board.platform",
};

static void* load_updated_driver(const char* kind, android_namespace_t* ns) {
    ATRACE_CALL();
    const android_dlextinfo dlextinfo = {
        .flags = ANDROID_DLEXT_USE_NAMESPACE,
        .library_namespace = ns,
    };
    void* so = nullptr;
    char prop[PROPERTY_VALUE_MAX + 1];
    for (auto key : HAL_SUBNAME_KEY_PROPERTIES) {
        if (property_get(key, prop, nullptr) > 0) {
            std::string name = std::string("lib") + kind + "_" + prop + ".so";
            so = do_android_dlopen_ext(name.c_str(), RTLD_LOCAL | RTLD_NOW, &dlextinfo);
            if (so) {
                return so;
            }
        }
    }
    return nullptr;
}

void *Loader::load_driver(const char* kind,
        egl_connection_t* cnx, uint32_t mask)
{
    ATRACE_CALL();

    void* dso = nullptr;
#ifndef __ANDROID_VNDK__
    android_namespace_t* ns = android_getDriverNamespace();
    if (ns) {
        dso = load_updated_driver(kind, ns);
    }
#endif
    if (!dso) {
        dso = load_system_driver(kind);
        if (!dso)
            return NULL;
    }

    if (mask & EGL) {
        getProcAddress = (getProcAddressType)dlsym(dso, "eglGetProcAddress");

        ALOGE_IF(!getProcAddress,
                "can't find eglGetProcAddress() in EGL driver library");

        egl_t* egl = &cnx->egl;
        __eglMustCastToProperFunctionPointerType* curr =
            (__eglMustCastToProperFunctionPointerType*)egl;
        char const * const * api = egl_names;
        while (*api) {
            char const * name = *api;
            __eglMustCastToProperFunctionPointerType f =
                (__eglMustCastToProperFunctionPointerType)dlsym(dso, name);
            if (f == NULL) {
                // couldn't find the entry-point, use eglGetProcAddress()
                f = getProcAddress(name);
                if (f == NULL) {
                    f = (__eglMustCastToProperFunctionPointerType)0;
                }
            }
            *curr++ = f;
            api++;
        }
    }

    if (mask & GLESv1_CM) {
        init_api(dso, gl_names,
            (__eglMustCastToProperFunctionPointerType*)
                &cnx->hooks[egl_connection_t::GLESv1_INDEX]->gl,
            getProcAddress);
    }

    if (mask & GLESv2) {
      init_api(dso, gl_names,
            (__eglMustCastToProperFunctionPointerType*)
                &cnx->hooks[egl_connection_t::GLESv2_INDEX]->gl,
            getProcAddress);
    }

    return dso;
}

// ----------------------------------------------------------------------------
}; // namespace android
// ----------------------------------------------------------------------------
