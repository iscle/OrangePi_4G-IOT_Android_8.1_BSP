/*
**
** Copyright 2016, The Android Open Source Project
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

#include <fcntl.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <cutils/properties.h>

#include <string>

#include <android-base/logging.h>

// from LOCAL_C_INCLUDES
#include "MediaCodecService.h"
#include "minijail.h"

#include <hidl/HidlTransportSupport.h>
#include <media/stagefright/omx/1.0/Omx.h>
#include <media/stagefright/omx/1.0/OmxStore.h>

using namespace android;

// Must match location in Android.mk.
static const char kSystemSeccompPolicyPath[] =
        "/system/etc/seccomp_policy/mediacodec.policy";
static const char kVendorSeccompPolicyPath[] =
        "/vendor/etc/seccomp_policy/mediacodec.policy";

int main(int argc __unused, char** argv)
{
    LOG(INFO) << "mediacodecservice starting";
    bool treble = property_get_bool("persist.media.treble_omx", true);
    if (treble) {
      android::ProcessState::initWithDriver("/dev/vndbinder");
    }

    signal(SIGPIPE, SIG_IGN);
    SetUpMinijail(kSystemSeccompPolicyPath, kVendorSeccompPolicyPath);

    strcpy(argv[0], "media.codec");

    ::android::hardware::configureRpcThreadpool(64, false);
    sp<ProcessState> proc(ProcessState::self());

    if (treble) {
        using namespace ::android::hardware::media::omx::V1_0;
        sp<IOmxStore> omxStore = new implementation::OmxStore();
        if (omxStore == nullptr) {
            LOG(ERROR) << "Cannot create IOmxStore HAL service.";
        } else if (omxStore->registerAsService() != OK) {
            LOG(ERROR) << "Cannot register IOmxStore HAL service.";
        }
        sp<IOmx> omx = new implementation::Omx();
        if (omx == nullptr) {
            LOG(ERROR) << "Cannot create IOmx HAL service.";
        } else if (omx->registerAsService() != OK) {
            LOG(ERROR) << "Cannot register IOmx HAL service.";
        } else {
            LOG(INFO) << "Treble OMX service created.";
        }
    } else {
        MediaCodecService::instantiate();
        LOG(INFO) << "Non-Treble OMX service created.";
    }

    ProcessState::self()->startThreadPool();
    IPCThreadState::self()->joinThreadPool();
}
