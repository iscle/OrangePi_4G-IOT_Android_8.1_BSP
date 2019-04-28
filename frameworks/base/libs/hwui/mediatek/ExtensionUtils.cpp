 /* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 */

/*****************************************************************************
 * Include
 *****************************************************************************/
#include <cutils/compiler.h>
#include <dlfcn.h>
#include <utils/Log.h>
#include <utils/Trace.h>
#include "ExtensionUtils.h"

/*****************************************************************************
 * Class ExtensionUtils
 *****************************************************************************/

namespace android {
namespace uirenderer {

#define LIB_FULL_NAME "libhwuiext.so"
#define ATRACE_TAG ATRACE_TAG_VIEW

void* ExtensionUtils::m_dlHandler = NULL;
IHwuiExtension* ExtensionUtils::m_hwuiExtension = NULL;
IBasicInfo* ExtensionUtils::m_basicInfo = NULL;
ProgramCache* ExtensionUtils::m_programCache = NULL;
IProgramAtlas* ExtensionUtils::m_programAtlas = NULL;

void ExtensionUtils::init() {
    ATRACE_CALL();
    if (m_dlHandler != NULL) {
        ALOGE("[%s] return, m_dlHandler = %p", __FUNCTION__, m_dlHandler);
        return;
    }

    m_dlHandler = dlopen(LIB_FULL_NAME, RTLD_NOW);
    if (m_dlHandler == NULL) {
        ALOGI("[%s] dlopen failed in %s: %s",
                __FUNCTION__, LIB_FULL_NAME, dlerror());
        return;
    }

    // reset errors
    dlerror();

    create_t* createExtHandler = (create_t*) dlsym(m_dlHandler, "create");
    const char* dlsym_error = dlerror();
    if (createExtHandler == NULL) {
        ALOGE("[%s] create not defined or exported in %s: %s",
                __FUNCTION__, LIB_FULL_NAME, dlsym_error);
        return;
    }

    // create an instance of ExtHandler
    m_hwuiExtension = createExtHandler();
    ALOGD("[%s] completed", __FUNCTION__);
}

IBasicInfo* ExtensionUtils::getBasicInfo() {
    if (m_dlHandler == NULL) {
        init();
    }
    if (m_hwuiExtension == NULL) {
        return NULL;
    }
    if (m_basicInfo == NULL) {
        m_basicInfo = m_hwuiExtension->getBasicInfo();
    }
    return m_basicInfo;
}

void ExtensionUtils::deleteBasicInfo() {
    if (m_basicInfo != NULL) {
        delete m_basicInfo;
        m_basicInfo = NULL;
    }
}

ProgramCache* ExtensionUtils::getProgramCache(const Extensions& extensions) {
    if (m_dlHandler == NULL) {
        init();
    }
    if (m_hwuiExtension == NULL && m_programCache == NULL) {
        // default implementation for Android
        return new ProgramCache(extensions);
    }
    if (m_programCache == NULL) {
        m_programCache = m_hwuiExtension->getProgramCache(extensions);
    }
    return m_programCache;
}

void ExtensionUtils::deleteProgramCache() {
    if (m_programCache != NULL) {
        delete m_programCache;
        m_programCache = NULL;
    }
}

IProgramAtlas* ExtensionUtils::getProgramAtlas() {
    if (m_dlHandler == NULL) {
        init();
    }
    if (m_hwuiExtension == NULL) {
        return NULL;
    }
    if (m_programAtlas == NULL) {
        m_programAtlas = m_hwuiExtension->getProgramAtlas();
    }
    return m_programAtlas;
}

void ExtensionUtils::deleteProgramAtlas() {
    if (m_programAtlas != NULL) {
        delete m_programAtlas;
        m_programAtlas = NULL;
    }
}

void ExtensionUtils::deInit() {
    ATRACE_CALL();
    if (m_dlHandler == NULL) {
        ALOGI("[%s] return, m_dlHandler == NULL", __FUNCTION__);
        return;
    }

    destroy_t* destroyExtHandler = (destroy_t*) dlsym(m_dlHandler, "destroy");
    const char* dlsym_error = dlerror();
    if (destroyExtHandler == NULL) {
        ALOGE("[%s] destroy not defined or exported in %s: %s",
                __FUNCTION__, LIB_FULL_NAME, dlsym_error);
        return;
    }

    destroyExtHandler(m_hwuiExtension);
    m_hwuiExtension = NULL;
    dlclose(m_dlHandler);
    m_dlHandler = NULL;
    ALOGD("[%s] completed", __FUNCTION__);
}

}; // namespace uirenderer
}; // namespace android

