/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#ifndef MTK_HWUI_DEBUG_H
#define MTK_HWUI_DEBUG_H

#include "MTKDumper.h"
#include "MTKMonitorThread.h"

#include <utils/Trace.h>
#include <cutils/properties.h>

namespace android {
namespace uirenderer {

///////////////////////////////////////////////////////////////////////////////
// Globals
///////////////////////////////////////////////////////////////////////////////

// Turn on to enable initialization information
#undef DEBUG_INIT
#define DEBUG_INIT defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_INIT;

// Turn on to enable debugging of cache flushes
#undef DEBUG_CACHE_FLUSH
#define DEBUG_CACHE_FLUSH defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_CACHE_FLUSH;

// Turn on to display debug info about vertex/fragment shaders
#undef DEBUG_PROGRAMS
#define DEBUG_PROGRAMS defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_PROGRAMS;

// Turn on to display info about layers
#undef DEBUG_LAYERS
#define DEBUG_LAYERS defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_LAYERS;

// Turn on to display info about render buffers
#undef DEBUG_RENDER_BUFFERS
#define DEBUG_RENDER_BUFFERS defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_RENDER_BUFFERS;

// Turn on to make stencil operations easier to debug
// (writes 255 instead of 1 in the buffer, forces 8 bit stencil)
#undef DEBUG_STENCIL
#define DEBUG_STENCIL defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_STENCIL;

// Turn on to display debug info about 9patch objects
#undef DEBUG_PATCHES
#define DEBUG_PATCHES defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_PATCHES;

// Turn on to display vertex and tex coords data about 9patch objects
// This flag requires DEBUG_PATCHES to be turned on
#undef DEBUG_PATCHES_VERTICES
#define DEBUG_PATCHES_VERTICES defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_PATCHES_VERTICES;

// Turn on to display vertex and tex coords data used by empty quads
// in 9patch objects
// This flag requires DEBUG_PATCHES to be turned on
#undef DEBUG_PATCHES_EMPTY_VERTICES
#define DEBUG_PATCHES_EMPTY_VERTICES defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_PATCHES_EMPTY_VERTICES;

// Turn on to display debug info about shapes
#undef DEBUG_PATHS
#define DEBUG_PATHS defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_PATHS;

// Turn on to display debug info about textures
#undef DEBUG_TEXTURES
#define DEBUG_TEXTURES defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_TEXTURES;

// Turn on to display debug info about the layer renderer
#undef DEBUG_LAYER_RENDERER
#define DEBUG_LAYER_RENDERER defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_LAYER_RENDERER;

// Turn on to enable additional debugging in the font renderers
#undef DEBUG_FONT_RENDERER
#define DEBUG_FONT_RENDERER defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_FONT_RENDERER;

// Turn on to enable debugging shadow
#undef DEBUG_SHADOW
#define DEBUG_SHADOW defined(MTK_DEBUG_RENDERER)
extern bool g_HWUI_DEBUG_SHADOW;

// debug dump functions
extern bool g_HWUI_debug_dumpDisplayList;
extern bool g_HWUI_debug_dumpDraw;
extern bool g_HWUI_debug_dumpTexture;
extern bool g_HWUI_debug_dumpAlphaTexture;
extern bool g_HWUI_debug_dumpLayer;
extern bool g_HWUI_debug_dumpTextureLayer;

// sync with egl trace
extern bool g_HWUI_debug_egl_trace;

// misc
extern bool g_HWUI_debug_enhancement;
extern bool g_HWUI_debug_continuous_frame;
extern bool g_HWUI_debug_record_state;
extern bool g_HWUI_debug_systrace;
extern bool g_HWUI_debug_hwuitask;

extern void setDebugLog();

///////////////////////////////////////////////////////////////////////////////
// Defines
///////////////////////////////////////////////////////////////////////////////

#ifndef ATRACE_TAG_HWUI
#define ATRACE_TAG_HWUI ATRACE_TAG_VIEW
#endif

/**
 * Log helper
 */
#define MLOGD(OPTION, ...) if (CC_UNLIKELY(g_HWUI_##OPTION)) ALOGD(__VA_ARGS__)

/**
 * Systrace helper
 */
// overview
#define ATRACE_BEGIN_L1(...) atrace_begin(ATRACE_TAG_VIEW, __VA_ARGS__)
#define ATRACE_END_L1() atrace_end(ATRACE_TAG_VIEW)
#define ATRACE_NAME_L1(...) android::ScopedTrace ___tracer(ATRACE_TAG_VIEW, __VA_ARGS__)
#define ATRACE_CALL_L1() ATRACE_NAME_L1(__FUNCTION__)
#define ATRACE_FORMAT_L1(fmt, ...) \
        HwuiTraceUtils::HwuiTraceEnder __traceEnder = \
        (HwuiTraceUtils::atraceFormatBegin(ATRACE_TAG_VIEW, true, fmt, ##__VA_ARGS__), \
        HwuiTraceUtils::HwuiTraceEnder(ATRACE_TAG_VIEW, true))
#define ATRACE_FORMAT_BEGIN_L1(fmt, ...) \
        HwuiTraceUtils::atraceFormatBegin(ATRACE_TAG_VIEW, true, fmt, ##__VA_ARGS__)

// detail
#define ATRACE_BEGIN_L2(...) atrace_begin(ATRACE_TAG_HWUI, __VA_ARGS__)
#define ATRACE_END_L2() atrace_end(ATRACE_TAG_HWUI)
#define ATRACE_NAME_L2(...) android::ScopedTrace ___tracer(ATRACE_TAG_HWUI, __VA_ARGS__)
#define ATRACE_CALL_L2() ATRACE_NAME_L2(__FUNCTION__)
#define ATRACE_FORMAT_L2(fmt, ...) \
        HwuiTraceUtils::HwuiTraceEnder __traceEnder = \
        (HwuiTraceUtils::atraceFormatBegin(ATRACE_TAG_HWUI, true, fmt, ##__VA_ARGS__), \
        HwuiTraceUtils::HwuiTraceEnder(ATRACE_TAG_HWUI, true))
#define ATRACE_FORMAT_BEGIN_L2(fmt, ...) \
        HwuiTraceUtils::atraceFormatBegin(ATRACE_TAG_HWUI, true, fmt, ##__VA_ARGS__)

// more detail
#define ATRACE_BEGIN_L3(...) if (CC_UNLIKELY(g_HWUI_debug_systrace)) atrace_begin(ATRACE_TAG_HWUI, __VA_ARGS__)
#define ATRACE_END_L3() if (CC_UNLIKELY(g_HWUI_debug_systrace)) atrace_end(ATRACE_TAG_HWUI)
#define ATRACE_NAME_L3(...) HwuiScopedTrace ___tracer(__VA_ARGS__)
#define ATRACE_CALL_L3() ATRACE_NAME_L3(__FUNCTION__)
#define ATRACE_FORMAT_L3(fmt, ...) \
        HwuiTraceUtils::HwuiTraceEnder __traceEnder = \
        (HwuiTraceUtils::atraceFormatBegin(ATRACE_TAG_HWUI, false, fmt, ##__VA_ARGS__), \
        HwuiTraceUtils::HwuiTraceEnder(ATRACE_TAG_HWUI, false))
#define ATRACE_FORMAT_BEGIN_L3(fmt, ...) \
        HwuiTraceUtils::atraceFormatBegin(ATRACE_TAG_HWUI, false, fmt, ##__VA_ARGS__)

/**
 * Performance log helper
 */
#define TIME_LOG_NAME(NAME) ScopedMonitor __monitor = ScopedMonitor(NAME, nullptr)

#define TIME_LOG_BASIC(NAME, COMMAND) \
        {   \
            TIME_LOG_NAME(NAME); \
            COMMAND; \
        }

#define TIME_LOG_SYSTRACE(NAME, COMMAND, SYSTRACE_NAME) \
            ATRACE_BEGIN_L2(SYSTRACE_NAME); TIME_LOG_BASIC(NAME, COMMAND); ATRACE_END_L2()

#define TIME_LOG(NAME, COMMAND) TIME_LOG_SYSTRACE(NAME, COMMAND, NAME)

/**
 * Dump helper
 */
#define IF_METHOD(CONDITION, INSTANCE, FUNCTION, ...) \
        if (CC_UNLIKELY(CONDITION)) { \
            INSTANCE::getInstance().FUNCTION(__VA_ARGS__); \
        }

#define DUMP_DISPLAY_LIST(...) IF_METHOD(g_HWUI_debug_dumpDisplayList, Dumper, dumpDisplayList, __VA_ARGS__)
#define DUMP_DRAW(...) IF_METHOD(g_HWUI_debug_dumpDraw, Dumper, dumpDraw, __VA_ARGS__)
#define DUMP_TEXTURE(...) IF_METHOD(g_HWUI_debug_dumpTexture,Dumper, dumpTexture, __VA_ARGS__, false)
#define DUMP_ALPHA_TEXTURE(...) IF_METHOD(g_HWUI_debug_dumpAlphaTexture, Dumper, dumpAlphaTexture, __VA_ARGS__)
#define DUMP_LAYER(...) IF_METHOD(g_HWUI_debug_dumpLayer, Dumper, dumpLayer, __VA_ARGS__)
#define DUMP_TEXTURE_LAYER(...) IF_METHOD(g_HWUI_debug_dumpTextureLayer, Dumper, dumpTexture, __VA_ARGS__, true)

///////////////////////////////////////////////////////////////////////////////
// Classes
///////////////////////////////////////////////////////////////////////////////

class HwuiTraceUtils {
public:
    class HwuiTraceEnder {
    public:
        HwuiTraceEnder(int tag, bool force) : mTag(tag), mForce(force) { }
        ~HwuiTraceEnder() {
            if (CC_UNLIKELY(ATRACE_ENABLED()) &&
                    (mForce || CC_UNLIKELY(g_HWUI_debug_systrace))) {
                atrace_end(mTag);
            }
        }
    private:
        int mTag;
        bool mForce;
    };

    static void atraceFormatBegin(int tag, bool force, const char* fmt, ...) {
        if (CC_UNLIKELY(ATRACE_ENABLED()) &&
                (force || CC_UNLIKELY(g_HWUI_debug_systrace))) {
            const int BUFFER_SIZE = 256;
            va_list ap;
            char buf[BUFFER_SIZE];

            va_start(ap, fmt);
            vsnprintf(buf, BUFFER_SIZE, fmt, ap);
            va_end(ap);

            atrace_begin(tag, buf);
        }
    }
};

class HwuiScopedTrace {
public:
    inline HwuiScopedTrace(const char* name) {
        if (g_HWUI_debug_systrace) atrace_begin(ATRACE_TAG_HWUI, name);
    }

    inline ~HwuiScopedTrace() {
        if (g_HWUI_debug_systrace) atrace_end(ATRACE_TAG_HWUI);
    }
};

class ScopedMonitor {
public:
    inline ScopedMonitor(const char* name, nsecs_t* duration) {
#ifdef MTK_DEBUG_RENDERER
        mTask = new MonitorTask(name);
        mDurationNs = duration;
#else
        (void) name;
        (void) duration;
#endif
    }

    inline ~ScopedMonitor() {
#ifdef MTK_DEBUG_RENDERER
        // task will commit suicide
        nsecs_t duration = mTask->requestRemove();
        if (mDurationNs) *mDurationNs = duration;
#endif
    }
private:
#ifdef MTK_DEBUG_RENDERER
    MonitorTask* mTask;
    nsecs_t* mDurationNs;
#endif
};

}; // namespace uirenderer
}; // namespace android

#endif /* MTK_HWUI_DEBUG_H */
