/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <sys/resource.h>

#include <sched.h>

#include <android/frameworks/displayservice/1.0/IDisplayService.h>
#include <android/hardware/configstore/1.0/ISurfaceFlingerConfigs.h>
#include <android/hardware/graphics/allocator/2.0/IAllocator.h>
#include <cutils/sched_policy.h>
#include <binder/IServiceManager.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <displayservice/DisplayService.h>
#include <hidl/LegacySupport.h>
#include <configstore/Utils.h>
#include "GpuService.h"
#include "SurfaceFlinger.h"
#ifdef MTK_SF_DEBUG_SUPPORT
#include <dlfcn.h>
#include <cutils/properties.h>
#include <sys/prctl.h>
#include "gui_ext/GuiExtService.h"
#define SIGNUM 7
#endif

using namespace android;

static status_t startGraphicsAllocatorService() {
    using android::hardware::graphics::allocator::V2_0::IAllocator;

    status_t result =
        hardware::registerPassthroughServiceImplementation<IAllocator>();
    if (result != OK) {
        ALOGE("could not start graphics allocator service");
        return result;
    }

    return OK;
}

static status_t startHidlServices() {
    using android::frameworks::displayservice::V1_0::implementation::DisplayService;
    using android::frameworks::displayservice::V1_0::IDisplayService;
    using android::hardware::configstore::getBool;
    using android::hardware::configstore::getBool;
    using android::hardware::configstore::V1_0::ISurfaceFlingerConfigs;
    hardware::configureRpcThreadpool(1 /* maxThreads */,
            false /* callerWillJoin */);

    status_t err;

    if (getBool<ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::startGraphicsAllocatorService>(false)) {
        err = startGraphicsAllocatorService();
        if (err != OK) {
           return err;
        }
    }

    sp<IDisplayService> displayservice = new DisplayService();
    err = displayservice->registerAsService();

    if (err != OK) {
        ALOGE("Could not register IDisplayService service.");
    }

    return err;
}

#ifdef MTK_SF_DEBUG_SUPPORT
void directcoredump_init() {
    int sigtype[SIGNUM] = {SIGABRT, SIGBUS, SIGFPE, SIGILL, SIGSEGV, SIGTRAP, SIGSYS};
    char value[PROPERTY_VALUE_MAX] = {'\0'};

    // eng&userdebug load direct-coredump default enable
    // user load direct-coredump default disable due to libdirect-coredump.so will not be preloaded
    property_get("persist.aee.core.direct", value, "default");
    if (strncmp(value, "disable", sizeof("disable"))) {
        int loop;
        for (loop = 0; loop < SIGNUM; loop++) {
            signal(sigtype[loop], SIG_DFL);
        }
    }
}
#endif

int main(int, char**) {
    startHidlServices();

    signal(SIGPIPE, SIG_IGN);

#ifdef MTK_SF_DEBUG_SUPPORT
#ifdef MTK_DEBUGGABLE_BUILD
    directcoredump_init();
    prctl(PR_SET_DUMPABLE, 1);
#endif
#endif

    // When SF is launched in its own process, limit the number of
    // binder threads to 4.
    ProcessState::self()->setThreadPoolMaxThreadCount(4);

    // start the thread pool
    sp<ProcessState> ps(ProcessState::self());
    ps->startThreadPool();

    // instantiate surfaceflinger
    sp<SurfaceFlinger> flinger = new SurfaceFlinger();

    setpriority(PRIO_PROCESS, 0, PRIORITY_URGENT_DISPLAY);

    set_sched_policy(0, SP_FOREGROUND);

    // Put most SurfaceFlinger threads in the system-background cpuset
    // Keeps us from unnecessarily using big cores
    // Do this after the binder thread pool init
    if (cpusets_enabled()) set_cpuset_policy(0, SP_SYSTEM);

    // initialize before clients can connect
    flinger->init();

    // publish surface flinger
    sp<IServiceManager> sm(defaultServiceManager());
    sm->addService(String16(SurfaceFlinger::getServiceName()), flinger, false);

    // publish GpuService
    sp<GpuService> gpuservice = new GpuService();
    sm->addService(String16(GpuService::SERVICE_NAME), gpuservice, false);

#ifdef MTK_SF_DEBUG_SUPPORT
    // publish GuiExt service
    char value[PROPERTY_VALUE_MAX];

#ifndef MTK_DEBUGGABLE_BUILD
    property_get("debug.bq.ext_service", value, "0");
#else
    property_get("debug.bq.ext_service", value, "1");
#endif

    if (atoi(value)){
        void* soHandle = dlopen("libgui_ext.so", RTLD_LAZY);
        if (soHandle) {
            void (*createGuiExtPtr)();
            createGuiExtPtr = (decltype(createGuiExtPtr))(dlsym(soHandle, "createGuiExtService"));
            if (NULL == createGuiExtPtr) {
                dlclose(soHandle);
                ALOGE("finding createGuiExtService() failed");
            } else {
                createGuiExtPtr();
            }
        } else {
            ALOGE("open libgui_ext failed");
        }
    } else
        ALOGD("debug.bq.ext_service: %d\n", atoi(value));
#endif

    struct sched_param param = {0};
    param.sched_priority = 2;
    if (sched_setscheduler(0, SCHED_FIFO, &param) != 0) {
        ALOGE("Couldn't set SCHED_FIFO");
    }

    // run surface flinger in this thread
    flinger->run();

    return 0;
}
