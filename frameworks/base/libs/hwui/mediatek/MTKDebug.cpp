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

#include "MTKDebug.h"

namespace android {
namespace uirenderer {

bool g_HWUI_DEBUG_INIT = false;
bool g_HWUI_DEBUG_CACHE_FLUSH = IS_ENG_BUILD;
bool g_HWUI_DEBUG_PROGRAMS = false;
bool g_HWUI_DEBUG_LAYERS = IS_ENG_BUILD;
bool g_HWUI_DEBUG_RENDER_BUFFERS = false;
bool g_HWUI_DEBUG_STENCIL = false;
bool g_HWUI_DEBUG_PATCHES = false;
bool g_HWUI_DEBUG_PATCHES_VERTICES = false;
bool g_HWUI_DEBUG_PATCHES_EMPTY_VERTICES = false;
bool g_HWUI_DEBUG_PATHS = false;
bool g_HWUI_DEBUG_TEXTURES = false;
bool g_HWUI_DEBUG_LAYER_RENDERER = false;
bool g_HWUI_DEBUG_FONT_RENDERER = false;
bool g_HWUI_DEBUG_SHADOW = false;

// debug dump functions
bool g_HWUI_debug_dumpDisplayList = false;
bool g_HWUI_debug_dumpDraw = false;
bool g_HWUI_debug_dumpTexture = false;
bool g_HWUI_debug_dumpAlphaTexture = false;
bool g_HWUI_debug_dumpLayer = false;
bool g_HWUI_debug_dumpTextureLayer = false;

// sync with egl trace
bool g_HWUI_debug_egl_trace = false;

// misc
bool g_HWUI_debug_enhancement = true;
bool g_HWUI_debug_continuous_frame = false;
bool g_HWUI_debug_record_state = IS_ENG_BUILD || IS_USERDEBUG_BUILD;
bool g_HWUI_debug_systrace = false;
bool g_HWUI_debug_hwuitask = false;

void setDebugLog() {
    bool* pDebugArray[] = {
        &g_HWUI_DEBUG_INIT,
        &g_HWUI_DEBUG_CACHE_FLUSH,
        &g_HWUI_DEBUG_PROGRAMS,
        &g_HWUI_DEBUG_LAYERS,
        &g_HWUI_DEBUG_RENDER_BUFFERS,
        &g_HWUI_DEBUG_STENCIL,
        &g_HWUI_DEBUG_PATCHES,
        &g_HWUI_DEBUG_PATCHES_VERTICES,
        &g_HWUI_DEBUG_PATCHES_EMPTY_VERTICES,
        &g_HWUI_DEBUG_PATHS,
        &g_HWUI_DEBUG_TEXTURES,
        &g_HWUI_DEBUG_LAYER_RENDERER,
        &g_HWUI_DEBUG_FONT_RENDERER,
        &g_HWUI_DEBUG_SHADOW,

        &g_HWUI_debug_continuous_frame,
        &g_HWUI_debug_record_state,
        &g_HWUI_debug_systrace,
        &g_HWUI_debug_hwuitask,
        &g_HWUI_debug_dumpDisplayList,
        &g_HWUI_debug_dumpDraw,
        &g_HWUI_debug_dumpTexture,
        &g_HWUI_debug_dumpAlphaTexture,
        &g_HWUI_debug_dumpLayer,
        &g_HWUI_debug_dumpTextureLayer,
        &g_HWUI_debug_enhancement,
        &g_HWUI_debug_egl_trace
    };
    const char* properties[] = {
        "debug.hwui.log.init",
        "debug.hwui.log.cache_flush",
        "debug.hwui.log.programs",
        "debug.hwui.log.layers",
        "debug.hwui.log.render_buffers",
        "debug.hwui.log.stencil",
        "debug.hwui.log.patches",
        "debug.hwui.log.patches_vtx",
        "debug.hwui.log.patchesEmptyVtx",
        "debug.hwui.log.paths",
        "debug.hwui.log.tex",
        "debug.hwui.log.layer_renderer",
        "debug.hwui.log.font_renderer",
        "debug.hwui.log.shadow",

        "debug.hwui.log.continuous_frame",   // log continuous frame
        "debug.hwui.log.record_state",       // record state op
        "debug.hwui.log.systrace",           // log more detail in systrace, sync with CanvasContext
        "debug.hwui.log.hwuitask",           // log more detail in hwuiTask
        "debug.hwui.dump.displaylist",       // dump rendering result per frame
        "debug.hwui.dump.draw",              // dump rendering result per draw operation
        "debug.hwui.dump.tex",               // dump texture returned from textureCache
        "debug.hwui.dump.fonttex",           // dump texture for fonts, aka g_HWUI_debug_dumpAlphaTexture
        "debug.hwui.dump.layer",             // dump layer, the result of fbo
        "debug.hwui.dump.texture_layer",     // dump texturelayer, copy layer to bitmap
        "debug.hwui.enhancement",            // mtk enhancements
        "debug.egl.trace"                    // sync with DevelopmentSettings
    };
    char value[PROPERTY_VALUE_MAX];
    char valueId[PROPERTY_VALUE_MAX];
    char valueName[PROPERTY_VALUE_MAX];
    int size = int(sizeof(pDebugArray) / sizeof(pDebugArray[0]));

    char propertyId[] = "debug.hwui.process.id";
    char propertyName[] = "debug.hwui.process.name";

    bool enabled = true;
    int pid = Dumper::getInstance().mPid;
    char* pname = Dumper::getInstance().mProcessName;

    property_get(propertyId, valueId, "0");
    property_get(propertyName, valueName, "0");

    if (strcmp(valueId, "0") != 0 || strcmp(valueName, "0") != 0) {
        if (atoi(valueId) != pid && strcmp(valueName, pname) != 0) {
            // target process's pid is not matched
            enabled = false;
            ALOGD("%s=%s, current=%d, %s=%s, current=%s",
                propertyId, valueId, pid, propertyName, valueName, pname);
        }
    }

    if (enabled) {
        for (int i = 0; i < size; i++) {
            property_get(properties[i], value, "");
            if (value[0] != '\0') {
                ALOGD("<%s> setHwuiLog: %s=%s", pname, properties[i], value);
                //must check "1" because egl_trace property is systrace/error/1
                *pDebugArray[i] = (strcmp(value, "1") == 0) ? true : false;
            }
        }
    }
}

}; // namespace uirenderer
}; // namespace android
