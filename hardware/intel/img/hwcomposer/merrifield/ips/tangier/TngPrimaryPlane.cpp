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
#include <tangier/TngPrimaryPlane.h>
#include <tangier/TngGrallocBuffer.h>
#include <common/PixelFormat.h>

namespace android {
namespace intel {

TngPrimaryPlane::TngPrimaryPlane(int index, int disp)
    : TngSpritePlane(index, disp)
{
    CTRACE();
    mType = PLANE_PRIMARY;
    mForceBottom = true;
    mAbovePrimary = false;
}

TngPrimaryPlane::~TngPrimaryPlane()
{
    CTRACE();
}

void TngPrimaryPlane::setFramebufferTarget(buffer_handle_t handle)
{
    CTRACE();

    // do not need to update the buffer handle
    if (mCurrentDataBuffer != handle)
        mUpdateMasks |= PLANE_BUFFER_CHANGED;
    else
        mUpdateMasks &= ~PLANE_BUFFER_CHANGED;

    // if no update then do Not need set data buffer
    if (!mUpdateMasks)
        return;

    // don't need to map data buffer for primary plane
    mContext.type = DC_PRIMARY_PLANE;
    mContext.ctx.prim_ctx.update_mask = SPRITE_UPDATE_ALL;
    mContext.ctx.prim_ctx.index = mIndex;
    mContext.ctx.prim_ctx.pipe = mDevice;
    mContext.ctx.prim_ctx.stride = align_to((4 * align_to(mPosition.w, 32)), 64);
#ifdef ENABLE_ROTATION_180
    mContext.ctx.prim_ctx.linoff = (mPosition.h - 1) * mContext.ctx.prim_ctx.stride + (mPosition.w  - 1)* 4;
#else
    mContext.ctx.prim_ctx.linoff = 0;
#endif
    mContext.ctx.prim_ctx.pos = 0;
    mContext.ctx.prim_ctx.size =
        ((mPosition.h - 1) & 0xfff) << 16 | ((mPosition.w - 1) & 0xfff);
    mContext.ctx.prim_ctx.surf = 0;
    mContext.ctx.prim_ctx.contalpa = 0;

    mContext.ctx.prim_ctx.cntr = PixelFormat::PLANE_PIXEL_FORMAT_BGRA8888;
#ifdef ENABLE_ROTATION_180
    mContext.ctx.prim_ctx.cntr |= 0x80008000;
#else
    mContext.ctx.prim_ctx.cntr |= 0x80000000;
#endif
    mCurrentDataBuffer = handle;
}

bool TngPrimaryPlane::enablePlane(bool enabled)
{
    RETURN_FALSE_IF_NOT_INIT();

    struct drm_psb_register_rw_arg arg;
    memset(&arg, 0, sizeof(struct drm_psb_register_rw_arg));
    if (enabled) {
        arg.plane_enable_mask = 1;
    } else {
        arg.plane_disable_mask = 1;
    }
    arg.plane.type = DC_PRIMARY_PLANE;
    arg.plane.index = mIndex;
    arg.plane.ctx = 0;

    // issue ioctl
    Drm *drm = Hwcomposer::getInstance().getDrm();
    bool ret = drm->writeReadIoctl(DRM_PSB_REGISTER_RW, &arg, sizeof(arg));
    if (ret == false) {
        WTRACE("primary enabling (%d) failed with error code %d", enabled, ret);
        return false;
    }

    return true;

}

bool TngPrimaryPlane::setDataBuffer(buffer_handle_t handle)
{
    if (!handle) {
        setFramebufferTarget(handle);
        return true;
    }

    TngGrallocBuffer tmpBuf(handle);
    uint32_t usage;
    bool ret;

    ATRACE("handle = %#x", handle);

    usage = tmpBuf.getUsage();
    if (GRALLOC_USAGE_HW_FB & usage) {
        setFramebufferTarget(handle);
        return true;
    }

    // use primary as a sprite
    ret = DisplayPlane::setDataBuffer(handle);
    if (ret == false) {
        ETRACE("failed to set data buffer");
        return ret;
    }

    mContext.type = DC_PRIMARY_PLANE;
    return true;
}

void TngPrimaryPlane::setZOrderConfig(ZOrderConfig& zorderConfig,
                                           void *nativeConfig)
{
    if (!nativeConfig) {
        ETRACE("Invalid parameter, no native config");
        return;
    }

    mForceBottom = false;

    int primaryIndex = -1;
    int overlayIndex = -1;
    // only consider force bottom when overlay is active
    for (size_t i = 0; i < zorderConfig.size(); i++) {
        DisplayPlane *plane = zorderConfig[i]->plane;
        if (plane->getType() == DisplayPlane::PLANE_PRIMARY)
            primaryIndex = i;
        if (plane->getType() == DisplayPlane::PLANE_OVERLAY) {
            overlayIndex = i;
        }
    }

    // if has overlay plane which is below primary plane
    if (overlayIndex > primaryIndex) {
        mForceBottom = true;
    }

    struct intel_dc_plane_zorder *zorder =
        (struct intel_dc_plane_zorder *)nativeConfig;
    zorder->forceBottom[mIndex] = mForceBottom ? 1 : 0;
}

bool TngPrimaryPlane::assignToDevice(int disp)
{
    DisplayPlane::assignToDevice(disp);
    return true;
}

} // namespace intel
} // namespace android
