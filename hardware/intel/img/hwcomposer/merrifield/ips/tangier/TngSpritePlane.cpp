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
#include <tangier/TngSpritePlane.h>
#include <common/PixelFormat.h>

namespace android {
namespace intel {

TngSpritePlane::TngSpritePlane(int index, int disp)
    : SpritePlaneBase(index, disp)
{
    CTRACE();
    memset(&mContext, 0, sizeof(mContext));
}

TngSpritePlane::~TngSpritePlane()
{
    CTRACE();
}

bool TngSpritePlane::setDataBuffer(BufferMapper& mapper)
{
    int bpp;
    int srcX, srcY;
    int dstX, dstY, dstW, dstH;
    uint32_t spriteFormat;
    uint32_t stride;
    uint32_t linoff;
    uint32_t planeAlpha;

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
    stride = mapper.getStride().rgb.stride;
#ifdef ENABLE_ROTATION_180
    linoff = (mapper.getCrop().h + srcY - 1) * stride + (srcX + mapper.getCrop().w - 1) * bpp;
#else
    linoff = srcY * stride + srcX * bpp;
#endif

    // setup plane alpha
    if ((mBlending == HWC_BLENDING_PREMULT) && (mPlaneAlpha == 0)) {
       planeAlpha = mPlaneAlpha | 0x80000000;
    } else {
       // disable plane alpha to offload HW
       planeAlpha = 0;
    }

    // unlikely happen, but still we need make sure linoff is valid
    if (linoff > (stride * mapper.getHeight())) {
        ETRACE("invalid source crop");
        return false;
    }

    // update context
    mContext.type = DC_SPRITE_PLANE;
    mContext.ctx.sp_ctx.index = mIndex;
    mContext.ctx.sp_ctx.pipe = mDevice;
    // none blending and BRGA format layer,set format to BGRX8888
    if (mBlending == HWC_BLENDING_NONE && spriteFormat == PixelFormat::PLANE_PIXEL_FORMAT_BGRA8888)
	mContext.ctx.sp_ctx.cntr = PixelFormat::PLANE_PIXEL_FORMAT_BGRX8888
					| 0x80000000;
    else
	mContext.ctx.sp_ctx.cntr = spriteFormat | 0x80000000;
    mContext.ctx.sp_ctx.linoff = linoff;
    mContext.ctx.sp_ctx.stride = stride;
    mContext.ctx.sp_ctx.surf = mapper.getGttOffsetInPage(0) << 12;
    mContext.ctx.sp_ctx.pos = (dstY & 0xfff) << 16 | (dstX & 0xfff);
    mContext.ctx.sp_ctx.size =
        ((dstH - 1) & 0xfff) << 16 | ((dstW - 1) & 0xfff);
    mContext.ctx.sp_ctx.contalpa = planeAlpha;
    mContext.ctx.sp_ctx.update_mask = SPRITE_UPDATE_ALL;
    mContext.gtt_key = (uint64_t)mapper.getCpuAddress(0);
#ifdef ENABLE_ROTATION_180
    mContext.ctx.sp_ctx.cntr |= 1 << 15;
#endif
    VTRACE("cntr = %#x, linoff = %#x, stride = %#x,"
          "surf = %#x, pos = %#x, size = %#x, contalpa = %#x",
          mContext.ctx.sp_ctx.cntr,
          mContext.ctx.sp_ctx.linoff,
          mContext.ctx.sp_ctx.stride,
          mContext.ctx.sp_ctx.surf,
          mContext.ctx.sp_ctx.pos,
          mContext.ctx.sp_ctx.size,
          mContext.ctx.sp_ctx.contalpa);
    return true;
}

void* TngSpritePlane::getContext() const
{
    CTRACE();
    return (void *)&mContext;
}

bool TngSpritePlane::enablePlane(bool enabled)
{
    RETURN_FALSE_IF_NOT_INIT();

    struct drm_psb_register_rw_arg arg;
    memset(&arg, 0, sizeof(struct drm_psb_register_rw_arg));
    if (enabled) {
        arg.plane_enable_mask = 1;
    } else {
        arg.plane_disable_mask = 1;
    }
    arg.plane.type = DC_SPRITE_PLANE;
    arg.plane.index = mIndex;
    arg.plane.ctx = 0;

    // issue ioctl
    Drm *drm = Hwcomposer::getInstance().getDrm();
    bool ret = drm->writeReadIoctl(DRM_PSB_REGISTER_RW, &arg, sizeof(arg));
    if (ret == false) {
        WTRACE("sprite enabling (%d) failed with error code %d", enabled, ret);
        return false;
    }

    Hwcomposer& hwc = Hwcomposer::getInstance();
    DisplayPlaneManager *pm = hwc.getPlaneManager();
    void *config = pm->getZOrderConfig();
    if (config != NULL) {
        struct intel_dc_plane_zorder *zorder =  (struct intel_dc_plane_zorder *)config;
        zorder->abovePrimary = 0;
    }

    return true;

}

bool TngSpritePlane::isDisabled()
{
    RETURN_FALSE_IF_NOT_INIT();

    struct drm_psb_register_rw_arg arg;
    memset(&arg, 0, sizeof(struct drm_psb_register_rw_arg));

    if (mType == DisplayPlane::PLANE_SPRITE)
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

void TngSpritePlane::setZOrderConfig(ZOrderConfig& zorderConfig,
                                          void *nativeConfig)
{
    if (!nativeConfig) {
        ETRACE("Invalid parameter, no native config");
        return;
    }

    mAbovePrimary = false;

    int primaryIndex = -1;
    int spriteIndex = -1;
    // only consider force bottom when overlay is active
    for (size_t i = 0; i < zorderConfig.size(); i++) {
        DisplayPlane *plane = zorderConfig[i]->plane;
        if (plane->getType() == DisplayPlane::PLANE_PRIMARY)
            primaryIndex = i;
        if (plane->getType() == DisplayPlane::PLANE_SPRITE) {
            spriteIndex = i;
        }
    }

    // if has overlay plane which is below primary plane
    if (spriteIndex > primaryIndex) {
        mAbovePrimary = true;
    }

    struct intel_dc_plane_zorder *zorder =
        (struct intel_dc_plane_zorder *)nativeConfig;
    zorder->abovePrimary = mAbovePrimary ? 1 : 0;
}
} // namespace intel
} // namespace android
