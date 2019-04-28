/*
// Copyright (c) 2014 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/

#include <HwcTrace.h>
#include <Drm.h>
#include <Hwcomposer.h>
#include <common/PrepareListener.h>

namespace android {
namespace intel {

PrepareListener::PrepareListener()
    : IPrepareListener()
{
}

PrepareListener::~PrepareListener()
{
}

void PrepareListener::onProtectedLayerStart(int disp)
{
    WTRACE("disp = %d, ignored for now", disp);
    // need chaabi support for granular IED control
    return;

    Drm *drm = Hwcomposer::getInstance().getDrm();
    int ret = drmCommandNone(drm->getDrmFd(), DRM_PSB_HDCP_DISPLAY_IED_ON);
    if (ret != 0) {
        ETRACE("failed to turn on display IED");
    } else {
        ITRACE("display IED is turned on");
    }
}

} // namespace intel
} // namespace android
