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
#include <Hwcomposer.h>
#include <BufferManager.h>
#include <anniedale/AnnRGBPlane.h>
#include <tangier/TngGrallocBuffer.h>
#include <common/PixelFormat.h>

namespace android {
namespace intel {

AnnRGBPlane::AnnRGBPlane(int index, int type, int disp)
    : DisplayPlane(index, type, disp)
{
    CTRACE();
    memset(&mContext, 0, sizeof(mContext));
}

AnnRGBPlane::~AnnRGBPlane()
{
    CTRACE();
}

bool AnnRGBPlane::enable()
{
    return enablePlane(true);
}

bool AnnRGBPlane::disable()
{
    return enablePlane(false);
}

void* AnnRGBPlane::getContext() const
{
    CTRACE();
    return (void *)&mContext;
}

void AnnRGBPlane::setZOrderConfig(ZOrderConfig& config, void *nativeConfig)
{
    CTRACE();
}

bool AnnRGBPlane::setDataBuffer(buffer_handle_t handle)
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

    return true;
}

bool AnnRGBPlane::setDataBuffer(BufferMapper& mapper)
{
    int bpp;
    int srcX, srcY, srcW, srcH;
    int dstX, dstY, dstW, dstH;
    uint32_t spriteFormat;
    uint32_t stride;
    uint32_t linoff;
    uint32_t planeAlpha;
    drmModeModeInfoPtr mode = &mModeInfo;

    CTRACE();

    // setup plane position
    dstX = mPosition.x;
    dstY = mPosition.y;
    dstW = mPosition.w;
    dstH = mPosition.h;

    checkPosition(dstX, dstY, dstW, dstH);

    // setup plane format
    if (!PixelFormat::convertFormat(mapper.getFormat(), spriteFormat, bpp)) {
        ETRACE("unsupported format %#x", mapper.getFormat());
        return false;
    }

    // setup stride and source buffer crop
    srcX = mapper.getCrop().x;
    srcY = mapper.getCrop().y;
    srcW = mapper.getWidth();
    srcH = mapper.getHeight();
    stride = mapper.getStride().rgb.stride;

    if (mPanelOrientation == PANEL_ORIENTATION_180)
        linoff = srcY * stride + srcX * bpp + (mapper.getCrop().h  - 1) * stride + (mapper.getCrop().w - 1) * bpp;
    else
        linoff = srcY * stride + srcX * bpp;

    // unlikely happen, but still we need make sure linoff is valid
    if (linoff > (stride * mapper.getHeight())) {
        ETRACE("invalid source crop");
        return false;
    }

    // update context
    if (mType == PLANE_SPRITE)
        mContext.type = DC_SPRITE_PLANE;
    else
        mContext.type = DC_PRIMARY_PLANE;

    // setup plane alpha
    if (0 < mPlaneAlpha && mPlaneAlpha < 0xff) {
       planeAlpha = mPlaneAlpha | 0x80000000;
    } else {
       // disable plane alpha to offload HW
       planeAlpha = 0xff;
    }

    mContext.ctx.sp_ctx.index = mIndex;
    mContext.ctx.sp_ctx.pipe = mDevice;
    mContext.ctx.sp_ctx.cntr = spriteFormat | 0x80000000;
    mContext.ctx.sp_ctx.linoff = linoff;
    mContext.ctx.sp_ctx.stride = stride;

    // turn off premultipled alpha blending for HWC_BLENDING_COVERAGE
    if (mBlending == HWC_BLENDING_COVERAGE) {
        mContext.ctx.sp_ctx.cntr |= (0x1 << 23);
    }

    if (mPanelOrientation == PANEL_ORIENTATION_180)
        mContext.ctx.sp_ctx.cntr |= (0x1 << 15);

    if (mapper.isCompression()) {
        mContext.ctx.sp_ctx.stride = align_to(srcW, 32) * 4;
        mContext.ctx.sp_ctx.linoff = (align_to(srcW, 32) * srcH / 64) - 1;
        mContext.ctx.sp_ctx.tileoff = (srcY & 0xfff) << 16 | (srcX & 0xfff);
        mContext.ctx.sp_ctx.cntr |= (0x1 << 11);
    }

    mContext.ctx.sp_ctx.surf = mapper.getGttOffsetInPage(0) << 12;
    mContext.gtt_key = (uint64_t)mapper.getCpuAddress(0);

    if (mPanelOrientation == PANEL_ORIENTATION_180) {
        if (mode->vdisplay && mode->hdisplay)
            mContext.ctx.sp_ctx.pos = ((mode->vdisplay - dstY - dstH) & 0xfff) << 16 | ((mode->hdisplay - dstX - dstW) & 0xfff);
        else
            mContext.ctx.sp_ctx.pos = (dstY & 0xfff) << 16 | (dstX & 0xfff);
    } else {
        mContext.ctx.sp_ctx.pos = (dstY & 0xfff) << 16 | (dstX & 0xfff);
    }

    mContext.ctx.sp_ctx.size =
        ((dstH - 1) & 0xfff) << 16 | ((dstW - 1) & 0xfff);
    mContext.ctx.sp_ctx.contalpa = planeAlpha;
    mContext.ctx.sp_ctx.update_mask = SPRITE_UPDATE_ALL;

    VTRACE("type = %d, index = %d, cntr = %#x, linoff = %#x, stride = %#x,"
          "surf = %#x, pos = %#x, size = %#x, contalpa = %#x", mType, mIndex,
          mContext.ctx.sp_ctx.cntr,
          mContext.ctx.sp_ctx.linoff,
          mContext.ctx.sp_ctx.stride,
          mContext.ctx.sp_ctx.surf,
          mContext.ctx.sp_ctx.pos,
          mContext.ctx.sp_ctx.size,
          mContext.ctx.sp_ctx.contalpa);
    return true;
}

bool AnnRGBPlane::enablePlane(bool enabled)
{
    RETURN_FALSE_IF_NOT_INIT();

    struct drm_psb_register_rw_arg arg;
    memset(&arg, 0, sizeof(struct drm_psb_register_rw_arg));
    if (enabled) {
        arg.plane_enable_mask = 1;
    } else {
        arg.plane_disable_mask = 1;
    }

    if (mType == PLANE_SPRITE)
        arg.plane.type = DC_SPRITE_PLANE;
    else
        arg.plane.type = DC_PRIMARY_PLANE;

    arg.plane.index = mIndex;
    arg.plane.ctx = 0;

    // issue ioctl
    Drm *drm = Hwcomposer::getInstance().getDrm();
    bool ret = drm->writeReadIoctl(DRM_PSB_REGISTER_RW, &arg, sizeof(arg));
    if (ret == false) {
        WTRACE("plane enabling (%d) failed with error code %d", enabled, ret);
        return false;
    }

    return true;
}

bool AnnRGBPlane::isDisabled()
{
    RETURN_FALSE_IF_NOT_INIT();

    struct drm_psb_register_rw_arg arg;
    memset(&arg, 0, sizeof(struct drm_psb_register_rw_arg));

    if (mType == PLANE_SPRITE)
        arg.plane.type = DC_SPRITE_PLANE;
    else
        arg.plane.type = DC_PRIMARY_PLANE;

    arg.get_plane_state_mask = 1;
    arg.plane.index = mIndex;
    arg.plane.ctx = 0;

    // issue ioctl
    Drm *drm = Hwcomposer::getInstance().getDrm();
    bool ret = drm->writeReadIoctl(DRM_PSB_REGISTER_RW, &arg, sizeof(arg));
    if (ret == false) {
        WTRACE("plane state query failed with error code %d", ret);
        return false;
    }

    return arg.plane.ctx == PSB_DC_PLANE_DISABLED;
}

void AnnRGBPlane::postFlip()
{
    // prevent mUpdateMasks from being reset
    // skipping flip may cause flicking
}

void AnnRGBPlane::setFramebufferTarget(buffer_handle_t handle)
{
    uint32_t stride;
    uint32_t planeAlpha;

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
    if (mType == PLANE_SPRITE)
        mContext.type = DC_SPRITE_PLANE;
    else
        mContext.type = DC_PRIMARY_PLANE;

    stride = align_to((4 * align_to(mPosition.w, 32)), 64);

    if (0 < mPlaneAlpha && mPlaneAlpha < 0xff) {
       planeAlpha = mPlaneAlpha | 0x80000000;
    } else {
       // disable plane alpha to offload HW
       planeAlpha = 0xff;
    }

    // FIXME: use sprite context for sprite plane
    mContext.ctx.prim_ctx.update_mask = SPRITE_UPDATE_ALL;
    mContext.ctx.prim_ctx.index = mIndex;
    mContext.ctx.prim_ctx.pipe = mDevice;

    if (mPanelOrientation == PANEL_ORIENTATION_180)
        mContext.ctx.prim_ctx.linoff = (mPosition.h  - 1) * stride + (mPosition.w - 1) * 4;
    else
        mContext.ctx.prim_ctx.linoff = 0;

    mContext.ctx.prim_ctx.stride = stride;
    mContext.ctx.prim_ctx.tileoff = 0;
    mContext.ctx.prim_ctx.pos = 0;
    mContext.ctx.prim_ctx.size =
        ((mPosition.h - 1) & 0xfff) << 16 | ((mPosition.w - 1) & 0xfff);
    mContext.ctx.prim_ctx.surf = 0;
    mContext.ctx.prim_ctx.contalpa = planeAlpha;
    mContext.ctx.prim_ctx.cntr = PixelFormat::PLANE_PIXEL_FORMAT_BGRA8888;
    mContext.ctx.prim_ctx.cntr |= 0x80000000;

    // turn off premultipled alpha blending for HWC_BLENDING_COVERAGE
    if (mBlending == HWC_BLENDING_COVERAGE) {
        mContext.ctx.prim_ctx.cntr |= (0x1 << 23);
    }

    if (mPanelOrientation == PANEL_ORIENTATION_180)
        mContext.ctx.prim_ctx.cntr |= (0x1 << 15);

    VTRACE("type = %d, index = %d, cntr = %#x, linoff = %#x, stride = %#x,"
          "surf = %#x, pos = %#x, size = %#x, contalpa = %#x", mType, mIndex,
          mContext.ctx.prim_ctx.cntr,
          mContext.ctx.prim_ctx.linoff,
          mContext.ctx.prim_ctx.stride,
          mContext.ctx.prim_ctx.surf,
          mContext.ctx.prim_ctx.pos,
          mContext.ctx.prim_ctx.size,
          mContext.ctx.sp_ctx.contalpa);

    mCurrentDataBuffer = handle;
}

} // namespace intel
} // namespace android
