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
#include <tangier/TngPlaneManager.h>
#include <tangier/TngPrimaryPlane.h>
#include <tangier/TngSpritePlane.h>
#include <tangier/TngOverlayPlane.h>
#include <tangier/TngCursorPlane.h>

namespace android {
namespace intel {

TngPlaneManager::TngPlaneManager()
    : DisplayPlaneManager()
{
    memset(&mZorder, 0, sizeof(mZorder));
}

TngPlaneManager::~TngPlaneManager()
{
}

bool TngPlaneManager::initialize()
{
    mSpritePlaneCount = 1;  // Sprite D
    mOverlayPlaneCount = 0;  // Skip overlay A & C by setting count to 0
    mPrimaryPlaneCount = 3;  // Primary A, B, C
    mCursorPlaneCount = 3;

    return DisplayPlaneManager::initialize();
}

void TngPlaneManager::deinitialize()
{
    DisplayPlaneManager::deinitialize();
}

DisplayPlane* TngPlaneManager::allocPlane(int index, int type)
{
    DisplayPlane *plane = 0;

    switch (type) {
    case DisplayPlane::PLANE_PRIMARY:
        plane = new TngPrimaryPlane(index, index);
        break;
    case DisplayPlane::PLANE_SPRITE:
        plane = new TngSpritePlane(index, 0);
        break;
    case DisplayPlane::PLANE_OVERLAY:
        plane = new TngOverlayPlane(index, 0);
        break;
    case DisplayPlane::PLANE_CURSOR:
        plane = new TngCursorPlane(index, index /*disp */);
        break;
    default:
        ETRACE("unsupported type %d", type);
        break;
    }
    if (plane && !plane->initialize(DisplayPlane::MIN_DATA_BUFFER_COUNT)) {
        ETRACE("failed to initialize plane.");
        DEINIT_AND_DELETE_OBJ(plane);
    }

    return plane;
}

bool TngPlaneManager::isValidZOrder(int dsp, ZOrderConfig& config)
{
    // check whether it's a supported z order config
    int firstRGB = -1;
    int lastRGB = -1;
    int firstOverlay = -1;
    int lastOverlay = -1;

    for (int i = 0; i < (int)config.size(); i++) {
        const ZOrderLayer *layer = config[i];
        switch (layer->planeType) {
        case DisplayPlane::PLANE_PRIMARY:
        case DisplayPlane::PLANE_SPRITE:
            if (firstRGB == -1) {
                firstRGB = i;
                lastRGB = i;
            } else {
                lastRGB = i;
            }
            break;
        case DisplayPlane::PLANE_OVERLAY:
        case DisplayPlane::PLANE_CURSOR:
            if (firstOverlay == -1) {
                firstOverlay = i;
                lastOverlay = i;
            } else {
                lastOverlay = i;
            }
            break;
        }
    }

    if ((lastRGB < firstOverlay) || (firstRGB > lastOverlay)) {
        return true;
    } else {
        VTRACE("invalid z order config. rgb (%d, %d) yuv (%d, %d)",
               firstRGB, lastRGB, firstOverlay, lastOverlay);
        return false;
    }
}

bool TngPlaneManager::assignPlanes(int dsp, ZOrderConfig& config)
{
    // probe if plane is available
    int size = (int)config.size();
    for (int i = 0; i < size; i++) {
        const ZOrderLayer *layer = config.itemAt(i);
        if (!getFreePlanes(dsp, layer->planeType)) {
            DTRACE("no plane available for dsp %d, type %d", dsp, layer->planeType);
            return false;
        }
    }

    if (config.size() == 1 && config[0]->planeType == DisplayPlane::PLANE_SPRITE) {
        config[0]->planeType == DisplayPlane::PLANE_PRIMARY;
    }

    // allocate planes
    for (int i = 0; i < size; i++) {
        ZOrderLayer *layer = config.itemAt(i);
        layer->plane = getPlaneHelper(dsp, layer->planeType);
        if (layer->plane == NULL) {
            // should never happen!!
            ETRACE("failed to assign plane for type %d", layer->planeType);
            return false;
        }
        // sequence !!!!! enabling plane before setting zorder
        // see TngSpritePlane::enablePlane implementation!!!!
        layer->plane->enable();
    }

    // setup Z order
    for (int i = 0; i < size; i++) {
        ZOrderLayer *layer = config.itemAt(i);
        layer->plane->setZOrderConfig(config, &mZorder);
    }

    return true;
}

void* TngPlaneManager::getZOrderConfig() const
{
    return (void*)&mZorder;
}

DisplayPlane* TngPlaneManager::getPlaneHelper(int dsp, int type)
{
    RETURN_NULL_IF_NOT_INIT();

    if (dsp < 0 || dsp > IDisplayDevice::DEVICE_EXTERNAL) {
        ETRACE("Invalid display device %d", dsp);
        return 0;
    }

    int index = dsp == IDisplayDevice::DEVICE_PRIMARY ? 0 : 1;

    if (type == DisplayPlane::PLANE_PRIMARY ||
        type == DisplayPlane::PLANE_CURSOR) {
        return getPlane(type, index);
    } else if (type == DisplayPlane::PLANE_SPRITE) {
        return getAnyPlane(type);
    } else if (type == DisplayPlane::PLANE_OVERLAY) {
        // use overlay A for pipe A and overlay C for pipe B if possible
        DisplayPlane *plane = getPlane(type, index);
        if (plane == NULL) {
            plane = getPlane(type, !index);
        }
        return plane;
    } else {
        ETRACE("invalid plane type %d", type);
        return 0;
    }
}

} // namespace intel
} // namespace android

