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
#include <HwcLayerList.h>
#include <Hwcomposer.h>
#include <GraphicBuffer.h>
#include <IDisplayDevice.h>
#include <PlaneCapabilities.h>
#include <DisplayQuery.h>

namespace android {
namespace intel {

HwcLayerList::HwcLayerList(hwc_display_contents_1_t *list, int disp)
    : mList(list),
      mLayerCount(0),
      mLayers(),
      mFBLayers(),
      mStaticLayersIndex(),
      mSpriteCandidates(),
      mOverlayCandidates(),
      mZOrderConfig(),
      mFrameBufferTarget(NULL),
      mDisplayIndex(disp),
      mLayerSize(0)
{
    initialize();
}

HwcLayerList::~HwcLayerList()
{
    deinitialize();
}

bool HwcLayerList::checkSupported(int planeType, HwcLayer *hwcLayer)
{
    bool valid = false;
    hwc_layer_1_t& layer = *(hwcLayer->getLayer());

    // if layer was forced to use FB
    if (hwcLayer->getType() == HwcLayer::LAYER_FORCE_FB) {
        VTRACE("layer was forced to use HWC_FRAMEBUFFER");
        return false;
    }

    // check layer flags
    if (layer.flags & HWC_SKIP_LAYER) {
        VTRACE("plane type %d: (skip layer flag was set)", planeType);
        return false;
    }

    if (layer.handle == 0) {
        WTRACE("invalid buffer handle");
        return false;
    }

    // check usage
    if (!hwcLayer->getUsage() & GRALLOC_USAGE_HW_COMPOSER) {
        WTRACE("not a composer layer");
        return false;
    }

    // check layer transform
    valid = PlaneCapabilities::isTransformSupported(planeType, hwcLayer);
    if (!valid) {
        VTRACE("plane type %d: (bad transform)", planeType);
        return false;
    }

    // check buffer format
    valid = PlaneCapabilities::isFormatSupported(planeType, hwcLayer);
    if (!valid) {
        VTRACE("plane type %d: (bad buffer format)", planeType);
        return false;
    }

    // check buffer size
    valid = PlaneCapabilities::isSizeSupported(planeType, hwcLayer);
    if (!valid) {
        VTRACE("plane type %d: (bad buffer size)", planeType);
        return false;
    }

    // check layer blending
    valid = PlaneCapabilities::isBlendingSupported(planeType, hwcLayer);
    if (!valid) {
        VTRACE("plane type %d: (bad blending)", planeType);
        return false;
    }

    // check layer scaling
    valid = PlaneCapabilities::isScalingSupported(planeType, hwcLayer);
    if (!valid) {
        VTRACE("plane type %d: (bad scaling)", planeType);
        return false;
    }

    // TODO: check visible region?
    return true;
}

bool HwcLayerList::checkCursorSupported(HwcLayer *hwcLayer)
{
    hwc_layer_1_t& layer = *(hwcLayer->getLayer());

    // if layer was forced to use FB
    if (hwcLayer->getType() == HwcLayer::LAYER_FORCE_FB) {
        VTRACE("layer was forced to use HWC_FRAMEBUFFER");
        return false;
    }

    // check layer flags
    if (layer.flags & HWC_SKIP_LAYER) {
        VTRACE("skip layer flag was set");
        return false;
    }

    if (!(layer.flags & HWC_IS_CURSOR_LAYER)) {
        VTRACE("not a cursor layer");
        return false;
    }

    if (hwcLayer->getIndex() != mLayerCount - 2) {
        WTRACE("cursor layer is not on top of zorder");
        return false;
    }

    if (layer.handle == 0) {
        WTRACE("invalid buffer handle");
        return false;
    }

    // check usage
    if (!(hwcLayer->getUsage() & GRALLOC_USAGE_HW_COMPOSER)) {
        WTRACE("not a composer layer");
        return false;
    }

    uint32_t format = hwcLayer->getFormat();
    if (format != HAL_PIXEL_FORMAT_BGRA_8888 &&
        format != HAL_PIXEL_FORMAT_RGBA_8888) {
        WTRACE("unexpected color format %u for cursor", format);
        return false;
    }

    uint32_t trans = hwcLayer->getLayer()->transform;
    if (trans != 0) {
        WTRACE("unexpected transform %u for cursor", trans);
        return false;
    }

    hwc_frect_t& src = hwcLayer->getLayer()->sourceCropf;
    hwc_rect_t& dest = hwcLayer->getLayer()->displayFrame;
    int srcW = (int)src.right - (int)src.left;
    int srcH = (int)src.bottom - (int)src.top;
    int dstW = dest.right - dest.left;
    int dstH = dest.bottom - dest.top;
    if (srcW != dstW || srcH != dstH) {
        WTRACE("unexpected scaling for cursor: %dx%d => %dx%d",
        srcW, srcH, dstW, dstH);
        //return false;
    }

    if (srcW > 256 || srcH > 256) {
        WTRACE("unexpected size %dx%d for cursor", srcW, srcH);
        return false;
    }

    BufferManager *bm = Hwcomposer::getInstance().getBufferManager();
    if (bm) {
        DataBuffer *buffer = bm->lockDataBuffer(hwcLayer->getHandle());
        if (buffer) {
            uint32_t w = buffer->getWidth();
            uint32_t h = buffer->getHeight();

            if ((w != 64 || h != 64) &&
                (w != 128 || h != 128) &&
                (w != 256 || h != 256)) {
                bm->unlockDataBuffer(buffer);
                return false;
            }
        }
        bm->unlockDataBuffer(buffer);
    }

    return true;
}

bool HwcLayerList::initialize()
{
    if (!mList || mList->numHwLayers == 0) {
        ETRACE("invalid hwc list");
        return false;
    }

    mLayerCount = (int)mList->numHwLayers;
    mLayers.setCapacity(mLayerCount);
    mFBLayers.setCapacity(mLayerCount);
    mSpriteCandidates.setCapacity(mLayerCount);
    mOverlayCandidates.setCapacity(mLayerCount);
    mCursorCandidates.setCapacity(mLayerCount);
    mZOrderConfig.setCapacity(mLayerCount);
    Hwcomposer& hwc = Hwcomposer::getInstance();

    for (int i = 0; i < mLayerCount; i++) {
        hwc_layer_1_t *layer = &mList->hwLayers[i];
        if (!layer) {
            DEINIT_AND_RETURN_FALSE("layer %d is null", i);
        }

        HwcLayer *hwcLayer = new HwcLayer(i, layer);
        if (!hwcLayer) {
            DEINIT_AND_RETURN_FALSE("failed to allocate hwc layer %d", i);
        }

        if (layer->compositionType == HWC_FRAMEBUFFER_TARGET) {
            hwcLayer->setType(HwcLayer::LAYER_FRAMEBUFFER_TARGET);
            mFrameBufferTarget = hwcLayer;
        } else if (layer->compositionType == HWC_OVERLAY){
            // skipped layer, filtered by Display Analyzer
            hwcLayer->setType(HwcLayer::LAYER_SKIPPED);
        } else if (layer->compositionType == HWC_FORCE_FRAMEBUFFER) {
            layer->compositionType = HWC_FRAMEBUFFER;
            hwcLayer->setType(HwcLayer::LAYER_FORCE_FB);
            // add layer to FB layer list for zorder check during plane assignment
            mFBLayers.add(hwcLayer);
        } else  if (layer->compositionType == HWC_FRAMEBUFFER) {
            // by default use GPU composition
            hwcLayer->setType(HwcLayer::LAYER_FB);
            mFBLayers.add(hwcLayer);
            if (checkCursorSupported(hwcLayer)) {
                mCursorCandidates.add(hwcLayer);
            } else if (checkSupported(DisplayPlane::PLANE_SPRITE, hwcLayer)) {
                mSpriteCandidates.add(hwcLayer);
            } else if (hwc.getDisplayAnalyzer()->isOverlayAllowed() &&
                checkSupported(DisplayPlane::PLANE_OVERLAY, hwcLayer)) {
                mOverlayCandidates.add(hwcLayer);
            } else {
                // noncandidate layer
            }
        } else if (layer->compositionType == HWC_SIDEBAND){
            hwcLayer->setType(HwcLayer::LAYER_SIDEBAND);
        } else {
            DEINIT_AND_RETURN_FALSE("invalid composition type %d", layer->compositionType);
        }
        // add layer to layer list
        mLayers.add(hwcLayer);
    }

    if (mFrameBufferTarget == NULL) {
        ETRACE("no frame buffer target?");
        return false;
    }

    // If has layer besides of FB_Target, but no FBLayers, skip plane allocation
    // Note: There is case that SF passes down a layerlist with only FB_Target
    // layer; we need to have this FB_Target to be flipped as well, otherwise it
    // will have the buffer queue blocked. (The buffer hold by driver cannot be
    // released if new buffers' flip is skipped).
    if ((mFBLayers.size() == 0) && (mLayers.size() > 1)) {
        VTRACE("no FB layers, skip plane allocation");
        return true;
    }

    allocatePlanes();

    //dump();
    return true;
}

void HwcLayerList::deinitialize()
{
    if (mLayerCount == 0) {
        return;
    }

    DisplayPlaneManager *planeManager = Hwcomposer::getInstance().getPlaneManager();
    for (int i = 0; i < mLayerCount; i++) {
        HwcLayer *hwcLayer = mLayers.itemAt(i);
        if (hwcLayer) {
            DisplayPlane *plane = hwcLayer->detachPlane();
            if (plane) {
                planeManager->reclaimPlane(mDisplayIndex, *plane);
            }
        }
        delete hwcLayer;
    }

    mLayers.clear();
    mFBLayers.clear();
    mOverlayCandidates.clear();
    mSpriteCandidates.clear();
    mCursorCandidates.clear();
    mZOrderConfig.clear();
    mFrameBufferTarget = NULL;
    mLayerCount = 0;
}


bool HwcLayerList::allocatePlanes()
{
    return assignCursorPlanes();
}

bool HwcLayerList::assignCursorPlanes()
{
    int cursorCandidates = (int)mCursorCandidates.size();
    if (cursorCandidates == 0) {
        return assignOverlayPlanes();
    }

    DisplayPlaneManager *planeManager = Hwcomposer::getInstance().getPlaneManager();
    int planeNumber = planeManager->getFreePlanes(mDisplayIndex, DisplayPlane::PLANE_CURSOR);
    if (planeNumber == 0) {
        DTRACE("no cursor plane available. candidates %d", cursorCandidates);
        return assignOverlayPlanes();
    }

    if (planeNumber > cursorCandidates) {
        // assuming all cursor planes have the same capabilities, just
        // need up to number of candidates for plane assignment
        planeNumber = cursorCandidates;
    }

    for (int i = planeNumber; i >= 0; i--) {
        // assign as many cursor planes as possible
        if (assignCursorPlanes(0, i)) {
            return true;
        }
        if (mZOrderConfig.size() != 0) {
            ETRACE("ZOrder config is not cleaned up!");
        }
    }
    return false;
}

bool HwcLayerList::assignCursorPlanes(int index, int planeNumber)
{
    // index indicates position in mCursorCandidates to start plane assignment
    if (planeNumber == 0) {
        return assignOverlayPlanes();
    }

    int cursorCandidates = (int)mCursorCandidates.size();
    for (int i = index; i <= cursorCandidates - planeNumber; i++) {
        ZOrderLayer *zlayer = addZOrderLayer(DisplayPlane::PLANE_CURSOR, mCursorCandidates[i]);
        if (assignCursorPlanes(i + 1, planeNumber - 1)) {
            return true;
        }
        removeZOrderLayer(zlayer);
    }
    return false;
}

bool HwcLayerList::assignOverlayPlanes()
{
    int overlayCandidates = (int)mOverlayCandidates.size();
    if (overlayCandidates == 0) {
        return assignSpritePlanes();
    }

    DisplayPlaneManager *planeManager = Hwcomposer::getInstance().getPlaneManager();
    int planeNumber = planeManager->getFreePlanes(mDisplayIndex, DisplayPlane::PLANE_OVERLAY);
    if (planeNumber == 0) {
        DTRACE("no overlay plane available. candidates %d", overlayCandidates);
        return assignSpritePlanes();
    }

    if (planeNumber > overlayCandidates) {
        // assuming all overlay planes have the same capabilities, just
        // need up to number of candidates for plane assignment
        planeNumber = overlayCandidates;
    }

    for (int i = planeNumber; i >= 0; i--) {
        // assign as many overlay planes as possible
        if (assignOverlayPlanes(0, i)) {
            return true;
        }
        if (mZOrderConfig.size() != 0) {
            ETRACE("ZOrder config is not cleaned up!");
        }
    }
    return false;
}


bool HwcLayerList::assignOverlayPlanes(int index, int planeNumber)
{
    // index indicates position in mOverlayCandidates to start plane assignment
    if (planeNumber == 0) {
        return assignSpritePlanes();
    }

    int overlayCandidates = (int)mOverlayCandidates.size();
    for (int i = index; i <= overlayCandidates - planeNumber; i++) {
        ZOrderLayer *zlayer = addZOrderLayer(DisplayPlane::PLANE_OVERLAY, mOverlayCandidates[i]);
        if (assignOverlayPlanes(i + 1, planeNumber - 1)) {
            return true;
        }
        removeZOrderLayer(zlayer);
    }
    return false;
}

bool HwcLayerList::assignSpritePlanes()
{
    int spriteCandidates = (int)mSpriteCandidates.size();
    if (spriteCandidates == 0) {
        return assignPrimaryPlane();
    }

    //  number does not include primary plane
    DisplayPlaneManager *planeManager = Hwcomposer::getInstance().getPlaneManager();
    int planeNumber = planeManager->getFreePlanes(mDisplayIndex, DisplayPlane::PLANE_SPRITE);
    if (planeNumber == 0) {
        VTRACE("no sprite plane available, candidates %d", spriteCandidates);
        return assignPrimaryPlane();
    }

    if (planeNumber > spriteCandidates) {
        // assuming all sprite planes have the same capabilities, just
        // need up to number of candidates for plane assignment
        planeNumber = spriteCandidates;
    }

    for (int i = planeNumber; i >= 0; i--) {
        // assign as many sprite planes as possible
        if (assignSpritePlanes(0, i)) {
            return true;
        }

        if (mOverlayCandidates.size() == 0 && mZOrderConfig.size() != 0) {
            ETRACE("ZOrder config is not cleaned up!");
        }
    }
    return false;
}


bool HwcLayerList::assignSpritePlanes(int index, int planeNumber)
{
    if (planeNumber == 0) {
        return assignPrimaryPlane();
    }

    int spriteCandidates = (int)mSpriteCandidates.size();
    for (int i = index; i <= spriteCandidates - planeNumber; i++) {
        ZOrderLayer *zlayer = addZOrderLayer(DisplayPlane::PLANE_SPRITE, mSpriteCandidates[i]);
        if (assignSpritePlanes(i + 1, planeNumber - 1)) {
            return true;
        }
        removeZOrderLayer(zlayer);
    }
    return false;
}

bool HwcLayerList::assignPrimaryPlane()
{
    // find a sprit layer that is not candidate but has lower priority than candidates.
    HwcLayer *spriteLayer = NULL;
    for (int i = (int)mSpriteCandidates.size() - 1; i >= 0; i--) {
        if (mSpriteCandidates[i]->mPlaneCandidate)
            break;

        spriteLayer = mSpriteCandidates[i];
    }

    int candidates = (int)mZOrderConfig.size();
    int layers = (int)mFBLayers.size();
    bool ok = false;

    if (candidates == layers - 1 && spriteLayer != NULL) {
        // primary plane is configured as sprite, all sprite candidates are offloaded to display planes
        ok = assignPrimaryPlaneHelper(spriteLayer);
        if (!ok) {
            VTRACE("failed to use primary as sprite plane");
        }
    } else if (candidates == 0) {
        // none assigned, use primary plane for frame buffer target and set zorder to 0
        ok = assignPrimaryPlaneHelper(mFrameBufferTarget, 0);
        if (!ok) {
            ETRACE("failed to compose all layers to primary plane, should never happen");
        }
    } else if (candidates == layers) {
        // all assigned, primary plane may be used during ZOrder config.
        ok = attachPlanes();
        if (!ok) {
            VTRACE("failed to assign layers without primary");
        }
    } else {
        // check if the remaining planes can be composed to frame buffer target (FBT)
        // look up a legitimate Z order position to place FBT.
        for (int i = 0; i < layers && !ok; i++) {
            if (mFBLayers[i]->mPlaneCandidate) {
                continue;
            }
            if (useAsFrameBufferTarget(mFBLayers[i])) {
                ok = assignPrimaryPlaneHelper(mFrameBufferTarget, mFBLayers[i]->getZOrder());
                if (!ok) {
                    VTRACE("failed to use zorder %d for frame buffer target",
                        mFBLayers[i]->getZOrder());
                }
            }
        }
        if (!ok) {
            VTRACE("no possible zorder for frame buffer target");
        }

    }
    return ok;
}

bool HwcLayerList::assignPrimaryPlaneHelper(HwcLayer *hwcLayer, int zorder)
{
    ZOrderLayer *zlayer = addZOrderLayer(DisplayPlane::PLANE_PRIMARY, hwcLayer, zorder);
    bool ok = attachPlanes();
    if (!ok) {
        removeZOrderLayer(zlayer);
    }
    return ok;
}

bool HwcLayerList::attachPlanes()
{
    DisplayPlaneManager *planeManager = Hwcomposer::getInstance().getPlaneManager();
    if (!planeManager->isValidZOrder(mDisplayIndex, mZOrderConfig)) {
        VTRACE("invalid z order, size of config %d", mZOrderConfig.size());
        return false;
    }

    if (!planeManager->assignPlanes(mDisplayIndex, mZOrderConfig)) {
        WTRACE("failed to assign planes");
        return false;
    }

    VTRACE("============= plane assignment===================");
    for (int i = 0; i < (int)mZOrderConfig.size(); i++) {
        ZOrderLayer *zlayer = mZOrderConfig.itemAt(i);
        if (zlayer->plane == NULL || zlayer->hwcLayer == NULL) {
            ETRACE("invalid ZOrderLayer, should never happen!!");
            return false;
        }

        zlayer->plane->setZOrder(i);

        if (zlayer->plane->getType() == DisplayPlane::PLANE_CURSOR) {
            zlayer->hwcLayer->setType(HwcLayer::LAYER_CURSOR_OVERLAY);
            mFBLayers.remove(zlayer->hwcLayer);
        } else if (zlayer->hwcLayer != mFrameBufferTarget) {
            zlayer->hwcLayer->setType(HwcLayer::LAYER_OVERLAY);
            // update FB layers for smart composition
            mFBLayers.remove(zlayer->hwcLayer);
        }

        zlayer->hwcLayer->attachPlane(zlayer->plane, mDisplayIndex);

        VTRACE("total %d, layer %d, type %d, index %d, zorder %d",
            mLayerCount - 1,
            zlayer->hwcLayer->getIndex(),
            zlayer->plane->getType(),
            zlayer->plane->getIndex(),
            zlayer->zorder);

        delete zlayer;
    }

    mZOrderConfig.clear();
    return true;
}

bool HwcLayerList::useAsFrameBufferTarget(HwcLayer *target)
{
    // check if zorder of target can be used as zorder of frame buffer target
    // eligible only when all noncandidate layers can be merged to the target layer:
    // 1) noncandidate layer and candidate layer below the target layer can't overlap
    // if candidate layer is on top of non candidate layer, as "noncandidate layer" needs
    // to be moved up to target layer in z order;
    // 2) noncandidate layer and candidate layers above the target layer can't overlap
    // if candidate layer is below noncandidate layer, as "noncandidate layer" needs
    // to be moved down to target layer in z order.

    int targetLayerIndex = target->getIndex();

    // check candidate and noncandidate layers below this candidate does not overlap
    for (int below = 0; below < targetLayerIndex; below++) {
        if (mFBLayers[below]->mPlaneCandidate) {
            continue;
        } else {
            // check candidate layer above this noncandidate layer does not overlap
            for (int above = below + 1; above < targetLayerIndex; above++) {
                if (mFBLayers[above]->mPlaneCandidate == false) {
                    continue;
                }
                if (hasIntersection(mFBLayers[above], mFBLayers[below])) {
                    return false;
                }
            }
        }
    }

    // check candidate and noncandidate layers above this candidate does not overlap
    for (int above = targetLayerIndex + 1; above < mFBLayers.size(); above++) {
        if (mFBLayers[above]->mPlaneCandidate) {
            continue;
        } else {
            // check candidate layer below this noncandidate layer does not overlap
            for (int below = targetLayerIndex + 1; below < above; below++) {
                if (mFBLayers[below]->mPlaneCandidate == false) {
                    continue;
                }
                if (hasIntersection(mFBLayers[above], mFBLayers[below])) {
                    return false;
                }
            }
        }
    }

    return true;
}

bool HwcLayerList::hasIntersection(HwcLayer *la, HwcLayer *lb)
{
    hwc_layer_1_t *a = la->getLayer();
    hwc_layer_1_t *b = lb->getLayer();
    hwc_rect_t *aRect = &a->displayFrame;
    hwc_rect_t *bRect = &b->displayFrame;

    if (bRect->right <= aRect->left ||
        bRect->left >= aRect->right ||
        bRect->top >= aRect->bottom ||
        bRect->bottom <= aRect->top)
        return false;

    return true;
}

ZOrderLayer* HwcLayerList::addZOrderLayer(int type, HwcLayer *hwcLayer, int zorder)
{
    ZOrderLayer *layer = new ZOrderLayer;
    layer->planeType = type;
    layer->hwcLayer = hwcLayer;
    layer->zorder = (zorder != -1) ? zorder : hwcLayer->getZOrder();
    layer->plane = NULL;

    if (hwcLayer->mPlaneCandidate) {
        ETRACE("plane is candidate!, order = %d", zorder);
    }

    hwcLayer->mPlaneCandidate = true;

    if ((int)mZOrderConfig.indexOf(layer) >= 0) {
        ETRACE("layer exists!");
    }

    mZOrderConfig.add(layer);
    return layer;
}

void HwcLayerList::removeZOrderLayer(ZOrderLayer *layer)
{
    if ((int)mZOrderConfig.indexOf(layer) < 0) {
        ETRACE("layer does not exist!");
    }

    mZOrderConfig.remove(layer);

    if (layer->hwcLayer->mPlaneCandidate == false) {
        ETRACE("plane is not candidate!, order %d", layer->zorder);
    }
    layer->hwcLayer->mPlaneCandidate = false;
    delete layer;
}

void HwcLayerList::addStaticLayerSize(HwcLayer *hwcLayer)
{
    // Calculate static layer size to avoid only composition navigation bar
    // and status bar etc.
    hwc_layer_1_t *a = hwcLayer->getLayer();
    hwc_rect_t *Rect = &a->displayFrame;

    mLayerSize = mLayerSize + ((Rect->right - Rect->left) * (Rect->bottom - Rect->top));
}

bool HwcLayerList::checkStaticLayerSize()
{
    // Check static layer size if over threshold: half display size
    bool ret = false;
    int width = 0;
    int height = 0;
    drmModeModeInfo mode;
    Drm *drm = Hwcomposer::getInstance().getDrm();
    drm->getModeInfo(mDisplayIndex, mode);
    width = mode.hdisplay;
    height = mode.vdisplay;

    if (mLayerSize > (width * height/2))
        ret = true;

    return ret;
}

void HwcLayerList::setupSmartComposition()
{
    uint32_t compositionType = HWC_OVERLAY;
    HwcLayer *hwcLayer = NULL;

    // setup smart composition only there's no update on all FB layers
    for (size_t i = 0; i < mFBLayers.size(); i++) {
        hwcLayer = mFBLayers.itemAt(i);
        if (hwcLayer->isUpdated() ||
            hwcLayer->getStaticCount() == LAYER_STATIC_THRESHOLD) {
            compositionType = HWC_FRAMEBUFFER;
        }
    }

    VTRACE("smart composition enabled %s",
           (compositionType == HWC_OVERLAY) ? "TRUE" : "FALSE");
    for (size_t i = 0; i < mFBLayers.size(); i++) {
        hwcLayer = mFBLayers.itemAt(i);
        switch (hwcLayer->getType()) {
        case HwcLayer::LAYER_FB:
        case HwcLayer::LAYER_FORCE_FB:
            hwcLayer->setCompositionType(compositionType);
            break;
        default:
            ETRACE("Invalid layer type %d", hwcLayer->getType());
            break;
        }
    }
}

bool HwcLayerList::setupSmartComposition2()
{
    bool ret = false;
    HwcLayer *hwcLayer = NULL;
    int layerIndex = 0;
    int i = 0;

    if (mList->flags & HWC_GEOMETRY_CHANGED) {
        // clear static layers vector once geometry changed
        mStaticLayersIndex.setCapacity(mLayerCount);
        mStaticLayersIndex.clear();
        return ret;
    }

    if (mStaticLayersIndex.size() > 0) {
        // exit criteria: once either static layer has update
        for (i = 0; i < mStaticLayersIndex.size(); i++) {
            layerIndex = mStaticLayersIndex.itemAt(i);
            hwcLayer = mLayers.itemAt(layerIndex);

            if (hwcLayer->isUpdated()) {
                ret = true;
            }
        }

        if (ret == true) {
            for (i = 0; i < mStaticLayersIndex.size(); i++) {
                layerIndex = mStaticLayersIndex.itemAt(i);
                hwcLayer = mLayers.itemAt(layerIndex);

                hwcLayer->setCompositionType(HWC_FRAMEBUFFER);
            }

            DTRACE("Exit Smart Composition2 !");
            mLayerSize = 0;
            mStaticLayersIndex.clear();
        }
    } else {
        // entry criteria: hwc layers has no update
        if (mFBLayers.size() == 0) {
            for (i = 0; i < mLayerCount - 1; i++) {
                hwcLayer = mLayers.itemAt(i);
                if (hwcLayer->getPlane() &&
                    hwcLayer->getCompositionType() == HWC_OVERLAY &&
                    hwcLayer->getStaticCount() >= LAYER_STATIC_THRESHOLD) {
                    mStaticLayersIndex.add(i);
                }
            }

            // check if all static layers in sequence
            // if all in sequence, set FORCE_FB for static layers
            // TODO: optimization here
            //    1. If two connected, can trigger smart composition2
            //    2. Caculate layer size to see if it saves more bandwidth
            //    3. Dynamically check and add new static layers
            int staticLayerCount = mStaticLayersIndex.size();

            if (staticLayerCount > 1 && staticLayerCount < mLayerCount-1) {
                layerIndex = mStaticLayersIndex.itemAt(0);
                hwcLayer = mLayers.itemAt(layerIndex);
                mLayerSize = 0;
                addStaticLayerSize(hwcLayer);
                int preIndex = hwcLayer->getIndex();

                for (i = 1; i < staticLayerCount; i++) {
                    layerIndex = mStaticLayersIndex.itemAt(i);
                    hwcLayer = mLayers.itemAt(layerIndex);
                    int index = hwcLayer->getIndex();

                    if (index == preIndex + 1) {
                        addStaticLayerSize(hwcLayer);
                        preIndex = index;
                    } else
                        break;
                }

                if ((i == staticLayerCount) && checkStaticLayerSize()) {
                    for (i =0; i < staticLayerCount; i++) {
                        layerIndex = mStaticLayersIndex.itemAt(i);
                        hwcLayer = mLayers.itemAt(layerIndex);
                        hwcLayer->setCompositionType(HWC_FORCE_FRAMEBUFFER);
                    }
                    DTRACE("In Smart Composition2 !");
                    ret = true;
                } else {
                    mLayerSize = 0;
                }
            }

            if (!ret)
                mStaticLayersIndex.clear();
        }
    }

    // return ture to trigger remap layers with HW plane
    return ret;
}

#if 1  // support overlay fallback to GLES

bool HwcLayerList::update(hwc_display_contents_1_t *list)
{
    bool ret;

    // basic check to make sure the consistance
    if (!list) {
        ETRACE("null layer list");
        return false;
    }

    if ((int)list->numHwLayers != mLayerCount) {
        ETRACE("layer count doesn't match (%zd, %d)", list->numHwLayers, mLayerCount);
        return false;
    }

    // update list
    mList = list;

    bool ok = true;
    // update all layers, call each layer's update()
    for (int i = 0; i < mLayerCount; i++) {
        HwcLayer *hwcLayer = mLayers.itemAt(i);
        if (!hwcLayer) {
            ETRACE("no HWC layer for layer %d", i);
            continue;
        }

        if (!hwcLayer->update(&list->hwLayers[i])) {
            ok = false;
            hwcLayer->setCompositionType(HWC_FORCE_FRAMEBUFFER);
        }
    }

    if (!ok || setupSmartComposition2()) {
        ITRACE("overlay fallback to GLES. flags: %#x", list->flags);
        for (int i = 0; i < mLayerCount - 1; i++) {
            HwcLayer *hwcLayer = mLayers.itemAt(i);
            if (hwcLayer->getPlane() &&
                (hwcLayer->getCompositionType() == HWC_OVERLAY ||
                hwcLayer->getCompositionType() == HWC_CURSOR_OVERLAY)) {
                hwcLayer->setCompositionType(HWC_FRAMEBUFFER);
            }
        }
        mLayers.itemAt(mLayerCount - 1)->setCompositionType(HWC_FRAMEBUFFER_TARGET);
        deinitialize();
        mList = list;
        initialize();

        // update all layers again after plane re-allocation
        for (int i = 0; i < mLayerCount; i++) {
            HwcLayer *hwcLayer = mLayers.itemAt(i);
            if (!hwcLayer) {
                ETRACE("no HWC layer for layer %d", i);
                continue;
            }

            if (!hwcLayer->update(&list->hwLayers[i])) {
                DTRACE("fallback to GLES update failed on layer[%d]!\n", i);
            }
        }
    }

    setupSmartComposition();
    return true;
}

#else

bool HwcLayerList::update(hwc_display_contents_1_t *list)
{
    bool ret;

    // basic check to make sure the consistance
    if (!list) {
        ETRACE("null layer list");
        return false;
    }

    if ((int)list->numHwLayers != mLayerCount) {
        ETRACE("layer count doesn't match (%d, %d)", list->numHwLayers, mLayerCount);
        return false;
    }

    // update list
    mList = list;

    // update all layers, call each layer's update()
    for (int i = 0; i < mLayerCount; i++) {
        HwcLayer *hwcLayer = mLayers.itemAt(i);
        if (!hwcLayer) {
            ETRACE("no HWC layer for layer %d", i);
            continue;
        }

        hwcLayer->update(&list->hwLayers[i]);
    }

    setupSmartComposition();
    return true;
}

#endif

DisplayPlane* HwcLayerList::getPlane(uint32_t index) const
{
    HwcLayer *hwcLayer;

    if (index >= mLayers.size()) {
        ETRACE("invalid layer index %d", index);
        return 0;
    }

    hwcLayer = mLayers.itemAt(index);
    if ((hwcLayer->getType() == HwcLayer::LAYER_FB) ||
        (hwcLayer->getType() == HwcLayer::LAYER_FORCE_FB) ||
        (hwcLayer->getType() == HwcLayer::LAYER_SKIPPED)) {
        return 0;
    }

    if (hwcLayer->getHandle() == 0) {
        DTRACE("plane is attached with invalid handle");
        return 0;
    }

    return hwcLayer->getPlane();
}

void HwcLayerList::postFlip()
{
    for (size_t i = 0; i < mLayers.size(); i++) {
        HwcLayer *hwcLayer = mLayers.itemAt(i);
        hwcLayer->postFlip();
    }
}

void HwcLayerList::dump(Dump& d)
{
    d.append("Layer list: (number of layers %d):\n", mLayers.size());
    d.append(" LAYER |          TYPE          |   PLANE  | INDEX | Z Order \n");
    d.append("-------+------------------------+----------------------------\n");
    for (size_t i = 0; i < mLayers.size(); i++) {
        HwcLayer *hwcLayer = mLayers.itemAt(i);
        DisplayPlane *plane;
        long int planeIndex = -1;
        long int zorder = -1;
        const char *type = "HWC_FB";
        const char *planeType = "N/A";

        if (hwcLayer) {
            switch (hwcLayer->getType()) {
            case HwcLayer::LAYER_FB:
            case HwcLayer::LAYER_FORCE_FB:
                type = "HWC_FB";
                break;
            case HwcLayer::LAYER_OVERLAY:
            case HwcLayer::LAYER_SKIPPED:
                type = "HWC_OVERLAY";
                break;
            case HwcLayer::LAYER_FRAMEBUFFER_TARGET:
                type = "HWC_FRAMEBUFFER_TARGET";
                break;
            case HwcLayer::LAYER_SIDEBAND:
                type = "HWC_SIDEBAND";
                break;
            case HwcLayer::LAYER_CURSOR_OVERLAY:
                type = "HWC_CURSOR_OVERLAY";
                break;
            default:
                type = "Unknown";
            }

            plane = hwcLayer->getPlane();
            if (plane) {
                planeIndex = plane->getIndex();
                zorder = plane->getZOrder();
                switch (plane->getType()) {
                case DisplayPlane::PLANE_OVERLAY:
                    planeType = "OVERLAY";
                    break;
                case DisplayPlane::PLANE_SPRITE:
                    planeType = "SPRITE";
                    break;
                case DisplayPlane::PLANE_PRIMARY:
                    planeType = "PRIMARY";
                    break;
                case DisplayPlane::PLANE_CURSOR:
                    planeType = "CURSOR";
                    break;
                default:
                    planeType = "Unknown";
                }
            }

            d.append("  %2d   | %22s | %8s | %3ld   | %3ld \n",
                     i, type, planeType, planeIndex, zorder);
        }
    }
}


void HwcLayerList::dump()
{
    static char const* compositionTypeName[] = {
        "GLES",
        "HWC",
        "BG",
        "FBT",
        "SB",
        "CUR",
        "N/A"};

    static char const* planeTypeName[] = {
        "SPRITE",
        "OVERLAY",
        "PRIMARY",
        "CURSOR",
        "UNKNOWN"};

    DTRACE(" numHwLayers = %zu, flags = %08x", mList->numHwLayers, mList->flags);

    DTRACE(" type |  handle  | hints | flags | tr | blend | alpha |  format  |           source crop             |            frame          | index | zorder |  plane  ");
    DTRACE("------+----------+-------+-------+----+-------+-------+----------+-----------------------------------+---------------------------+-------+--------+---------");


    for (int i = 0 ; i < mLayerCount ; i++) {
        const hwc_layer_1_t&l = mList->hwLayers[i];
        DisplayPlane *plane = mLayers[i]->getPlane();
        int planeIndex = -1;
        int zorder = -1;
        const char *planeType = "N/A";
        if (plane) {
            planeIndex = plane->getIndex();
            zorder = plane->getZOrder();
            planeType = planeTypeName[plane->getType()];
        }

        DTRACE(
            " %4s | %p | %5x | %5x | %2x | %5x | %5x | %8x | [%7.1f,%7.1f,%7.1f,%7.1f] | [%5d,%5d,%5d,%5d] | %5d | %6d | %7s ",
            compositionTypeName[l.compositionType],
            mLayers[i]->getHandle(), l.hints, l.flags, l.transform, l.blending, l.planeAlpha, mLayers[i]->getFormat(),
            l.sourceCropf.left, l.sourceCropf.top, l.sourceCropf.right, l.sourceCropf.bottom,
            l.displayFrame.left, l.displayFrame.top, l.displayFrame.right, l.displayFrame.bottom,
            planeIndex, zorder, planeType);
    }

}


} // namespace intel
} // namespace android
