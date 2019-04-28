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
#include <BufferManager.h>
#include <tangier/TngCursorPlane.h>
#include <tangier/TngGrallocBuffer.h>
#include <hal_public.h>

namespace android {
namespace intel {

TngCursorPlane::TngCursorPlane(int index, int disp)
    : DisplayPlane(index, PLANE_CURSOR, disp)
{
    CTRACE();
    memset(&mContext, 0, sizeof(mContext));
    memset(&mCrop, 0, sizeof(mCrop));
}

TngCursorPlane::~TngCursorPlane()
{
    CTRACE();
}

bool TngCursorPlane::enable()
{
    return enablePlane(true);
}

bool TngCursorPlane::disable()
{
    return enablePlane(false);
}

void* TngCursorPlane::getContext() const
{
    CTRACE();
    return (void *)&mContext;
}

void TngCursorPlane::setZOrderConfig(ZOrderConfig& config, void *nativeConfig)
{
    (void) config;
    (void) nativeConfig;

     VTRACE("\n *** need to implement zorder config *** ");
    CTRACE();
}

bool TngCursorPlane::setDataBuffer(buffer_handle_t handle)
{
    bool ret;

    if (!handle) {
        ETRACE("handle is NULL");
        return false;
    }

    ret = DisplayPlane::setDataBuffer(handle);
    if (ret == false) {
        ETRACE("failed to set data buffer");
        return ret;
    }

    return true;
}

bool TngCursorPlane::setDataBuffer(BufferMapper& mapper)
{
    int w = mapper.getWidth();
    int h = mapper.getHeight();
    int cursorSize = 0;

    CTRACE();

    // setup plane position
    int dstX = mPosition.x;
    int dstY = mPosition.y;

    if (h < w) {
        cursorSize = h;
    } else {
        cursorSize = w;
    }

#if ENABLE_ROTATION_180
    dstX = mModeInfo.hdisplay - dstX - cursorSize;
    dstY = mModeInfo.vdisplay - dstY - cursorSize;
#endif

    uint32_t cntr = 0;
    if (64 <= cursorSize && cursorSize < 128) {
        cursorSize = 64;
        cntr = 0x7;
    } else if (128 <= cursorSize && cursorSize < 256) {
        cursorSize = 128;
        cntr = 0x2;
    } else {
        cursorSize = 256;
        cntr = 0x3;
    }

    if (mapper.getFormat() == HAL_PIXEL_FORMAT_RGBA_8888) {
        cntr |= 1 << 5;
    } else if (mapper.getFormat() == HAL_PIXEL_FORMAT_BGRA_8888) {
        // swap color from BGRA to RGBA - alpha is MSB
        uint8_t *p = (uint8_t *)(mapper.getCpuAddress(0));
        uint8_t *srcPixel;
        uint32_t stride = mapper.getStride().rgb.stride;
        uint8_t temp;
        if (!p) {
            return false;
        }

        for (int i = 0; i < cursorSize; i++) {
            for (int j = 0; j < cursorSize; j++) {
                srcPixel = p + i*stride + j*4;
                temp = srcPixel[0];
                srcPixel[0] = srcPixel[2];
                srcPixel[2] = temp;
            }
        }
        cntr |= 1 << 5;
    } else {
        ETRACE("invalid color format");
        return false;
    }

    // TODO: clean spare mem to be 0 in gralloc instead
    uint8_t *p = (uint8_t *)(mapper.getCpuAddress(0));
    uint8_t *srcPixel;
    uint32_t stride = mapper.getStride().rgb.stride;
    uint8_t temp;
    if (!p) {
        return false;
    }

    if (mCrop.w == 0 && mCrop.h == 0) {
        mCrop = mSrcCrop;
        for (int i = 0; i < cursorSize; i++) {
            for (int j = 0; j < cursorSize; j++) {
                srcPixel = p + i*stride + j*4;
                temp = srcPixel[0];
                if (i >= mCrop.h || j >= mCrop.w) {
                    if (srcPixel[0] == 0 &&
                        srcPixel[3] == 0xff)
                        srcPixel[3] = 0;
                }
            }
        }
    }

    // update context
    mContext.type = DC_CURSOR_PLANE;
    mContext.ctx.cs_ctx.index = mIndex;
    mContext.ctx.cs_ctx.pipe = mDevice;
    mContext.ctx.cs_ctx.cntr = cntr | (mIndex << 28);
    mContext.ctx.cs_ctx.surf = mapper.getGttOffsetInPage(0) << 12;

    mContext.ctx.cs_ctx.pos = 0;
    if (dstX < 0) {
        mContext.ctx.cs_ctx.pos |= 1 << 15;
        dstX = -dstX;
    }
    if (dstY < 0) {
        mContext.ctx.cs_ctx.pos |= 1 << 31;
        dstY = -dstY;
    }
    mContext.ctx.cs_ctx.pos |= (dstY & 0xfff) << 16 | (dstX & 0xfff);
    return true;
}

bool TngCursorPlane::enablePlane(bool enabled)
{
    RETURN_FALSE_IF_NOT_INIT();

    struct drm_psb_register_rw_arg arg;
    memset(&arg, 0, sizeof(struct drm_psb_register_rw_arg));
    if (enabled) {
        arg.plane_enable_mask = 1;
    } else {
        arg.plane_disable_mask = 1;
    }

    arg.plane.type = DC_CURSOR_PLANE;
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

bool TngCursorPlane::isDisabled()
{
    RETURN_FALSE_IF_NOT_INIT();

    struct drm_psb_register_rw_arg arg;
    memset(&arg, 0, sizeof(struct drm_psb_register_rw_arg));

    arg.plane.type = DC_CURSOR_PLANE;
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
    //return arg.plane.ctx == 0; //implement this PSB_DC_PLANE_DISABLED similar in imin_legacy

	return true;
}

void TngCursorPlane::postFlip()
{
    // prevent mUpdateMasks from being reset
    // skipping flip may cause flicking
}

} // namespace intel
} // namespace android
