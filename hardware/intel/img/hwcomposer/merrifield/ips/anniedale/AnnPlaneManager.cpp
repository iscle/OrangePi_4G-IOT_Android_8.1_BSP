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
#include <utils/String8.h>
#include <anniedale/AnnPlaneManager.h>
#include <anniedale/AnnRGBPlane.h>
#include <anniedale/AnnOverlayPlane.h>
#include <anniedale/AnnCursorPlane.h>
#include <PlaneCapabilities.h>

namespace android {
namespace intel {


struct PlaneDescription {
    char nickname;
    int type;
    int index;
};


static PlaneDescription PLANE_DESC[] =
{
    // nickname must be continous and start with 'A',
    // it is used to fast locate plane index and type
    {'A', DisplayPlane::PLANE_PRIMARY, 0},
    {'B', DisplayPlane::PLANE_PRIMARY, 1},
    {'C', DisplayPlane::PLANE_PRIMARY, 2},
    {'D', DisplayPlane::PLANE_SPRITE,  0},
    {'E', DisplayPlane::PLANE_SPRITE,  1},
    {'F', DisplayPlane::PLANE_SPRITE,  2},
    {'G', DisplayPlane::PLANE_OVERLAY, 0},  // nickname for Overlay A
    {'H', DisplayPlane::PLANE_OVERLAY, 1},   // nickname for Overlay C
    {'I', DisplayPlane::PLANE_CURSOR,  0},  // nickname for cursor A
    {'J', DisplayPlane::PLANE_CURSOR,  1},  // nickname for cursor B
    {'K', DisplayPlane::PLANE_CURSOR,  2}   // nickname for cursor C
};


struct ZOrderDescription {
    int index;  // based on overlay position
    const char *zorder;
};

// If overlay is in the bottom of Z order, two legitimate combinations are Oa, D, E, F
// and Oc, D, E, F. However, plane A has to be part of the blending chain as it can't
//  be disabled [HW bug]. The only legitimate combinations including overlay and plane A is:
// A, Oa, E, F
// A, Oc, E, F
// Cursor plane can be placed on top of any plane below and is intentionally ignored
// in the zorder table.

// video mode panel doesn't need the primay plane A always on hack
static ZOrderDescription PIPE_A_ZORDER_DESC_VID[] =
{
    {0, "ADEF"},  // no overlay
    {1, "GDEF"},  // overlay A at bottom (1 << 0)
    {1, "HDEF"},  // overlay C at bottom (1 << 0)
    {2, "AGEF"},  // overlay A at next to bottom (1 << 1)
    {2, "AHEF"},  // overlay C at next to bottom (1 << 1)
    {3, "GHEF"},  // overlay A, C at bottom
    {4, "ADGF"},  // overlay A at next to top (1 << 2)
    {4, "ADHF"},  // overlay C at next to top (1 << 2)
    {6, "AGHF"},  // overlay A, C in between
    {8, "ADEG"},  // overlay A at top (1 << 3)
    {8, "ADEH"},  // overlay C at top (1 <<3)
    {12, "ADGH"}  // overlay A, C at top
};

static ZOrderDescription PIPE_A_ZORDER_DESC_CMD[] =
{
    {0, "ADEF"},  // no overlay
    {1, "GEF"},  // overlay A at bottom (1 << 0)
    {1, "HEF"},  // overlay C at bottom (1 << 0)
    {2, "AGEF"},  // overlay A at next to bottom (1 << 1)
    {2, "AHEF"},  // overlay C at next to bottom (1 << 1)
    {3, "GHF"},   // overlay A, C at bottom
    {4, "ADGF"},  // overlay A at next to top (1 << 2)
    {4, "ADHF"},  // overlay C at next to top (1 << 2)
    {6, "AGHF"},  // overlay A, C in between
    {8, "ADEG"},  // overlay A at top (1 << 3)
    {8, "ADEH"},  // overlay C at top (1 <<3)
    {12, "ADGH"}  // overlay A, C at top
};

// use overlay C over overlay A if possible on pipe B
static ZOrderDescription PIPE_B_ZORDER_DESC[] =
{
    {0, "BD"},    // no overlay
    {1, "HBD"},   // overlay C at bottom (1 << 0)
//    {1, "GBD"},   // overlay A at bottom (1 << 0), overlay A don`t switch to pipeB and only overlay C on pipeB
    {2, "BHD"},   // overlay C at middle (1 << 1)
//   {2, "BGD"},   // overlay A at middle (1 << 1), overlay A don`t switch to pipeB and only overaly C on pipeB
    {3, "GHBD"},  // overlay A and C at bottom ( 1 << 0 + 1 << 1)
    {4, "BDH"},   // overlay C at top (1 << 2)
    {4, "BDG"},   // overlay A at top (1 << 2)
    {6, "BGHD"},  // overlay A/C at middle  1 << 1 + 1 << 2)
    {12, "BDGH"}  // overlay A/C at top (1 << 2 + 1 << 3)
};

static ZOrderDescription *PIPE_A_ZORDER_TBL;
static int PIPE_A_ZORDER_COMBINATIONS;
static ZOrderDescription *PIPE_B_ZORDER_TBL;
static int PIPE_B_ZORDER_COMBINATIONS;
static bool OVERLAY_HW_WORKAROUND;

AnnPlaneManager::AnnPlaneManager()
    : DisplayPlaneManager()
{
}

AnnPlaneManager::~AnnPlaneManager()
{
}

bool AnnPlaneManager::initialize()
{
    mSpritePlaneCount = 3;  // Sprite D, E, F
    mOverlayPlaneCount = 2; // Overlay A, C
    mPrimaryPlaneCount = 3; // Primary A, B, C
    mCursorPlaneCount = 3;

    uint32_t videoMode = 0;
    Drm *drm = Hwcomposer::getInstance().getDrm();
    drm->readIoctl(DRM_PSB_PANEL_QUERY, &videoMode, sizeof(uint32_t));
    if (videoMode == 1) {
        DTRACE("video mode panel, no primay A always on hack");
        PIPE_A_ZORDER_TBL = PIPE_A_ZORDER_DESC_VID;
        PIPE_A_ZORDER_COMBINATIONS =
            sizeof(PIPE_A_ZORDER_DESC_VID)/sizeof(ZOrderDescription);
    } else {
        DTRACE("command mode panel, need primay A always on hack");
        PIPE_A_ZORDER_TBL = PIPE_A_ZORDER_DESC_CMD;
        PIPE_A_ZORDER_COMBINATIONS =
            sizeof(PIPE_A_ZORDER_DESC_CMD)/sizeof(ZOrderDescription);
	OVERLAY_HW_WORKAROUND = true;
    }

    PIPE_B_ZORDER_TBL = PIPE_B_ZORDER_DESC;
    PIPE_B_ZORDER_COMBINATIONS =
        sizeof(PIPE_B_ZORDER_DESC)/sizeof(ZOrderDescription);

    return DisplayPlaneManager::initialize();
}

void AnnPlaneManager::deinitialize()
{
    DisplayPlaneManager::deinitialize();
}

DisplayPlane* AnnPlaneManager::allocPlane(int index, int type)
{
    DisplayPlane *plane = NULL;

    switch (type) {
    case DisplayPlane::PLANE_PRIMARY:
        plane = new AnnRGBPlane(index, DisplayPlane::PLANE_PRIMARY, index/*disp*/);
        break;
    case DisplayPlane::PLANE_SPRITE:
        plane = new AnnRGBPlane(index, DisplayPlane::PLANE_SPRITE, 0/*disp*/);
        break;
    case DisplayPlane::PLANE_OVERLAY:
        plane = new AnnOverlayPlane(index, 0/*disp*/);
        break;
    case DisplayPlane::PLANE_CURSOR:
        plane = new AnnCursorPlane(index, index /*disp */);
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

bool AnnPlaneManager::isValidZOrder(int dsp, ZOrderConfig& config)
{
    int size = (int)config.size();
    bool hasCursor = false;

    for (int i = 0; i < size; i++) {
        if (config[i]->planeType == DisplayPlane::PLANE_CURSOR) {
            hasCursor = true;
            break;
        }
    }

    if (size <= 0 ||
        (hasCursor && size > 5) ||
        (!hasCursor && size > 4)) {
        VTRACE("invalid z order config size %d", size);
        return false;
    }

    if (dsp == IDisplayDevice::DEVICE_PRIMARY) {
        int firstOverlay = -1;
        for (int i = 0; i < size; i++) {
            if (config[i]->planeType == DisplayPlane::PLANE_OVERLAY) {
                firstOverlay = i;
                break;
            }
        }

        int sprites = 0;
        for (int i = 0; i < size; i++) {
            if (config[i]->planeType != DisplayPlane::PLANE_OVERLAY &&
                config[i]->planeType != DisplayPlane::PLANE_CURSOR) {
                sprites++;
            }
        }

        if (firstOverlay < 0 && sprites > 4) {
            VTRACE("not capable to support more than 4 sprite layers");
            return false;
        }

        if (OVERLAY_HW_WORKAROUND) {
            if (firstOverlay == 0 && size > 2) {
                VTRACE("can not support 3 sprite layers on top of overlay");
                return false;
            }
        }
    } else if (dsp == IDisplayDevice::DEVICE_EXTERNAL) {
        int sprites = 0;
        for (int i = 0; i < size; i++) {
            if (config[i]->planeType != DisplayPlane::PLANE_OVERLAY &&
                config[i]->planeType != DisplayPlane::PLANE_CURSOR) {
                sprites++;
            }
        }
        if (sprites > 2) {
            ETRACE("number of sprite: %d, maximum 1 sprite and 1 primary supported on pipe 1", sprites);
            return false;
        }
    } else {
        ETRACE("invalid display device %d", dsp);
        return false;
    }
    return true;
}

bool AnnPlaneManager::assignPlanes(int dsp, ZOrderConfig& config)
{
    if (dsp < 0 || dsp > IDisplayDevice::DEVICE_EXTERNAL) {
        ETRACE("invalid display device %d", dsp);
        return false;
    }

    int size = (int)config.size();

    // calculate index based on overlay Z order position
    int index = 0;
    for (int i = 0; i < size; i++) {
        if (config[i]->planeType == DisplayPlane::PLANE_OVERLAY) {
            index += (1 << i);
        }
    }

    int combinations;
    ZOrderDescription *table;
    if (dsp == IDisplayDevice::DEVICE_PRIMARY) {
        combinations = PIPE_A_ZORDER_COMBINATIONS;
        table = PIPE_A_ZORDER_TBL;
    } else {
        combinations = PIPE_B_ZORDER_COMBINATIONS;
        table = PIPE_B_ZORDER_TBL;
    }

    for (int i = 0; i < combinations; i++) {
        ZOrderDescription *zorderDesc = table + i;

        if (zorderDesc->index != index)
            continue;

        if (assignPlanes(dsp, config, zorderDesc->zorder)) {
            VTRACE("zorder assigned %s", zorderDesc->zorder);
            return true;
        }
    }
    return false;
}

bool AnnPlaneManager::assignPlanes(int dsp, ZOrderConfig& config, const char *zorder)
{
    // zorder string does not include cursor plane, therefore cursor layer needs to be handled
    // in a special way. Cursor layer must be on top of zorder and no more than one cursor layer.

    int size = (int)config.size();
    if (zorder == NULL || size == 0) {
        //DTRACE("invalid zorder or ZOrder config.");
        return false;
    }

    int zorderLen = (int)strlen(zorder);

    // test if plane is avalable
    for (int i = 0; i < size; i++) {
        if (config[i]->planeType == DisplayPlane::PLANE_CURSOR) {
            if (i != size - 1) {
                ETRACE("invalid zorder of cursor layer");
                return false;
            }
            PlaneDescription& desc = PLANE_DESC['I' - 'A' + dsp];
            if (!isFreePlane(desc.type, desc.index)) {
                ETRACE("cursor plane is not available");
                return false;
            }
            continue;
        }
        if (i >= zorderLen) {
            DTRACE("index of ZOrderConfig is out of bound");
            return false;
        }

        char id = *(zorder + i);
        PlaneDescription& desc = PLANE_DESC[id - 'A'];
        if (!isFreePlane(desc.type, desc.index)) {
            DTRACE("plane type %d index %d is not available", desc.type, desc.index);
            return false;
        }

#if 0
        // plane type check
        if (config[i]->planeType == DisplayPlane::PLANE_OVERLAY &&
            desc.type != DisplayPlane::PLANE_OVERLAY) {
            ETRACE("invalid plane type %d, expected %d", desc.type, config[i]->planeType);
            return false;
        }

        if (config[i]->planeType != DisplayPlane::PLANE_OVERLAY) {
            if (config[i]->planeType != DisplayPlane::PLANE_PRIMARY &&
                config[i]->planeType != DisplayPlane::PLANE_SPRITE) {
                ETRACE("invalid plane type %d,", config[i]->planeType);
                return false;
            }
            if (desc.type != DisplayPlane::PLANE_PRIMARY &&
                desc.type != DisplayPlane::PLANE_SPRITE) {
                ETRACE("invalid plane type %d, expected %d", desc.type, config[i]->planeType);
                return false;
            }
        }
#endif

        if  (desc.type == DisplayPlane::PLANE_OVERLAY && desc.index == 1 &&
             config[i]->hwcLayer->getTransform() != 0) {
            DTRACE("overlay C does not support transform");
            return false;
        }
    }

    bool primaryPlaneActive = false;
    // allocate planes
    for (int i = 0; i < size; i++) {
        if (config[i]->planeType == DisplayPlane::PLANE_CURSOR) {
            PlaneDescription& desc = PLANE_DESC['I' - 'A' + dsp];
            ZOrderLayer *zLayer = config.itemAt(i);
            zLayer->plane = getPlane(desc.type, desc.index);
            if (zLayer->plane == NULL) {
                ETRACE("failed to get cursor plane, should never happen!");
            }
            continue;
        }

        char id = *(zorder + i);
        PlaneDescription& desc = PLANE_DESC[id - 'A'];
        ZOrderLayer *zLayer = config.itemAt(i);
        zLayer->plane = getPlane(desc.type, desc.index);
        if (zLayer->plane == NULL) {
            ETRACE("failed to get plane, should never happen!");
        }
        // override type
        zLayer->planeType = desc.type;
        if (desc.type == DisplayPlane::PLANE_PRIMARY) {
            primaryPlaneActive = true;
        }
    }

    // setup Z order
    int slot = 0;
    for (int i = 0; i < size; i++) {
        slot = i;

        if (OVERLAY_HW_WORKAROUND) {
            if (!primaryPlaneActive &&
                config[i]->planeType == DisplayPlane::PLANE_OVERLAY) {
                slot += 1;
            }
        }

        config[i]->plane->setZOrderConfig(config, (void *)(unsigned long)slot);
        config[i]->plane->enable();
    }

#if 0
    DTRACE("config size %d, zorder %s", size, zorder);
    for (int i = 0; i < size; i++) {
        const ZOrderLayer *l = config.itemAt(i);
        ITRACE("%d: plane type %d, index %d, zorder %d",
            i, l->planeType, l->plane->getIndex(), l->zorder);
    }
#endif

    return true;
}

void* AnnPlaneManager::getZOrderConfig() const
{
    return NULL;
}

int AnnPlaneManager::getFreePlanes(int dsp, int type)
{
    RETURN_NULL_IF_NOT_INIT();

    if (type != DisplayPlane::PLANE_SPRITE) {
        return DisplayPlaneManager::getFreePlanes(dsp, type);
    }

    if (dsp < 0 || dsp > IDisplayDevice::DEVICE_EXTERNAL) {
        ETRACE("invalid display device %d", dsp);
        return 0;
    }

    uint32_t freePlanes = mFreePlanes[type] | mReclaimedPlanes[type];
    int start = 0;
    int stop = mSpritePlaneCount;
    if (dsp == IDisplayDevice::DEVICE_EXTERNAL) {
        // only Sprite D (index 0) can be assigned to pipe 1
        // Sprites E/F (index 1, 2) are fixed on pipe 0
        stop = 1;
    }
    int count = 0;
    for (int i = start; i < stop; i++) {
        if ((1 << i) & freePlanes) {
            count++;
        }
    }
    return count;
}

} // namespace intel
} // namespace android

