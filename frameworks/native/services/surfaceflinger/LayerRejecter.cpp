/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include "LayerRejecter.h"

#include <gui/BufferItem.h>
#include <system/window.h>

#include "clz.h"

#define DEBUG_RESIZE 0

namespace android {

LayerRejecter::LayerRejecter(Layer::State& front,
                             Layer::State& current,
                             bool& recomputeVisibleRegions,
                             bool stickySet,
                             const char* name,
                             int32_t overrideScalingMode,
                             bool& freezePositionUpdates)
  : mFront(front),
    mCurrent(current),
    mRecomputeVisibleRegions(recomputeVisibleRegions),
    mStickyTransformSet(stickySet),
    mName(name),
    mOverrideScalingMode(overrideScalingMode),
    mFreezeGeometryUpdates(freezePositionUpdates) {}

bool LayerRejecter::reject(const sp<GraphicBuffer>& buf, const BufferItem& item) {
    if (buf == NULL) {
        return false;
    }

    uint32_t bufWidth = buf->getWidth();
    uint32_t bufHeight = buf->getHeight();

    // check that we received a buffer of the right size
    // (Take the buffer's orientation into account)
    if (item.mTransform & Transform::ROT_90) {
        swap(bufWidth, bufHeight);
    }

    int actualScalingMode = mOverrideScalingMode >= 0 ? mOverrideScalingMode : item.mScalingMode;
    bool isFixedSize = actualScalingMode != NATIVE_WINDOW_SCALING_MODE_FREEZE;
    if (mFront.active != mFront.requested) {
        if (isFixedSize || (bufWidth == mFront.requested.w && bufHeight == mFront.requested.h)) {
            // Here we pretend the transaction happened by updating the
            // current and drawing states. Drawing state is only accessed
            // in this thread, no need to have it locked
            mFront.active = mFront.requested;

            // We also need to update the current state so that
            // we don't end-up overwriting the drawing state with
            // this stale current state during the next transaction
            //
            // NOTE: We don't need to hold the transaction lock here
            // because State::active is only accessed from this thread.
            mCurrent.active = mFront.active;
            mCurrent.modified = true;

            // recompute visible region
            mRecomputeVisibleRegions = true;

            mFreezeGeometryUpdates = false;

            if (mFront.crop != mFront.requestedCrop) {
                mFront.crop = mFront.requestedCrop;
                mCurrent.crop = mFront.requestedCrop;
                mRecomputeVisibleRegions = true;
            }
            if (mFront.finalCrop != mFront.requestedFinalCrop) {
                mFront.finalCrop = mFront.requestedFinalCrop;
                mCurrent.finalCrop = mFront.requestedFinalCrop;
                mRecomputeVisibleRegions = true;
            }
        }

        ALOGD_IF(DEBUG_RESIZE,
                 "[%s] latchBuffer/reject: buffer (%ux%u, tr=%02x), scalingMode=%d\n"
                 "  drawing={ active   ={ wh={%4u,%4u} crop={%4d,%4d,%4d,%4d} (%4d,%4d) "
                 "}\n"
                 "            requested={ wh={%4u,%4u} }}\n",
                 mName, bufWidth, bufHeight, item.mTransform, item.mScalingMode, mFront.active.w,
                 mFront.active.h, mFront.crop.left, mFront.crop.top, mFront.crop.right,
                 mFront.crop.bottom, mFront.crop.getWidth(), mFront.crop.getHeight(),
                 mFront.requested.w, mFront.requested.h);
    }

    if (!isFixedSize && !mStickyTransformSet) {
        if (mFront.active.w != bufWidth || mFront.active.h != bufHeight) {
            // reject this buffer
            ALOGE("[%s] rejecting buffer: "
                  "bufWidth=%d, bufHeight=%d, front.active.{w=%d, h=%d}",
                  mName, bufWidth, bufHeight, mFront.active.w, mFront.active.h);
            return true;
        }
    }

    // if the transparent region has changed (this test is
    // conservative, but that's fine, worst case we're doing
    // a bit of extra work), we latch the new one and we
    // trigger a visible-region recompute.
    //
    // We latch the transparent region here, instead of above where we latch
    // the rest of the geometry because it is only content but not necessarily
    // resize dependent.
    if (!mFront.activeTransparentRegion.isTriviallyEqual(mFront.requestedTransparentRegion)) {
        mFront.activeTransparentRegion = mFront.requestedTransparentRegion;

        // We also need to update the current state so that
        // we don't end-up overwriting the drawing state with
        // this stale current state during the next transaction
        //
        // NOTE: We don't need to hold the transaction lock here
        // because State::active is only accessed from this thread.
        mCurrent.activeTransparentRegion = mFront.activeTransparentRegion;

        // recompute visible region
        mRecomputeVisibleRegions = true;
    }

    return false;
}

}  // namespace android
