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
#include <math.h>
#include <HwcTrace.h>
#include <Drm.h>
#include <Hwcomposer.h>
#include <tangier/TngOverlayPlane.h>
#include <tangier/TngGrallocBuffer.h>

namespace android {
namespace intel {

TngOverlayPlane::TngOverlayPlane(int index, int disp)
    : OverlayPlaneBase(index, disp),
      mRotationBufProvider(NULL)
{
    CTRACE();

    memset(&mContext, 0, sizeof(mContext));
}

TngOverlayPlane::~TngOverlayPlane()
{
    CTRACE();
}

bool TngOverlayPlane::flip(void *ctx)
{
    RETURN_FALSE_IF_NOT_INIT();

    if (!DisplayPlane::flip(ctx))
        return false;

    mContext.type = DC_OVERLAY_PLANE;
    mContext.ctx.ov_ctx.ovadd = 0x0;
    mContext.ctx.ov_ctx.ovadd = (mBackBuffer[mCurrent]->gttOffsetInPage << 12);
    mContext.ctx.ov_ctx.index = mIndex;
    mContext.ctx.ov_ctx.pipe = mDevice;
    mContext.ctx.ov_ctx.ovadd |= mPipeConfig;
    mContext.ctx.ov_ctx.ovadd |= 0x1;

    // move to next back buffer
    //mCurrent = (mCurrent + 1) % OVERLAY_BACK_BUFFER_COUNT;

    VTRACE("ovadd = %#x, index = %d, device = %d",
          mContext.ctx.ov_ctx.ovadd,
          mIndex,
          mDevice);

    return true;
}

bool TngOverlayPlane::reset()
{
    OverlayPlaneBase::reset();
    if (mRotationBufProvider)
        mRotationBufProvider->reset();
    return true;
}

void* TngOverlayPlane::getContext() const
{
    CTRACE();
    return (void *)&mContext;
}

bool TngOverlayPlane::setDataBuffer(BufferMapper& mapper)
{
    if (OverlayPlaneBase::setDataBuffer(mapper) == false) {
        return false;
    }

    if (mIsProtectedBuffer) {
        // Bit 0: Decryption request, only allowed to change on a synchronous flip
        // This request will be qualified with the separate decryption enable bit for OV
        mBackBuffer[mCurrent]->buf->OSTART_0Y |= 0x1;
        mBackBuffer[mCurrent]->buf->OSTART_1Y |= 0x1;
    }

    mContext.gtt_key = (uint64_t)mapper.getCpuAddress(0);
    return true;
}

bool TngOverlayPlane::initialize(uint32_t bufferCount)
{
    if (!OverlayPlaneBase::initialize(bufferCount)) {
        ETRACE("failed to initialize OverlayPlaneBase");
        return false;
    }

    // setup rotation buffer
    mRotationBufProvider = new RotationBufferProvider(mWsbm);
    if (!mRotationBufProvider || !mRotationBufProvider->initialize()) {
        DEINIT_AND_RETURN_FALSE("failed to initialize RotationBufferProvider");
    }
    return true;
}

void TngOverlayPlane::deinitialize()
{
    DEINIT_AND_DELETE_OBJ(mRotationBufProvider);
    OverlayPlaneBase::deinitialize();
}

bool TngOverlayPlane::rotatedBufferReady(BufferMapper& mapper, BufferMapper* &rotatedMapper)
{
    struct VideoPayloadBuffer *payload;
    VideoPayloadBuffer buffer_info;
    uint32_t format;
    // only NV12_VED has rotated buffer
    format = mapper.getFormat();

    if (format != OMX_INTEL_COLOR_FormatYUV420PackedSemiPlanar &&
        format != OMX_INTEL_COLOR_FormatYUV420PackedSemiPlanar_Tiled &&
        format != HAL_PIXEL_FORMAT_NV12) {
        ETRACE("Invalid video format %#x", format);
        return false;
    }

    payload = (struct VideoPayloadBuffer *)mapper.getCpuAddress(SUB_BUFFER1);

    if (payload == NULL && format == HAL_PIXEL_FORMAT_NV12) {
         // need to populate buffer_info
        void *p = mapper.getCpuAddress(SUB_BUFFER0);
        if (!p) {
            ETRACE("failed to get buffer user pointer");
            return false;
        }

        bool ret = mRotationBufProvider->prepareBufferInfo(mapper.getWidth(),
                                                mapper.getHeight(),
                                                mapper.getStride().yuv.yStride,
                                                &buffer_info, p);
        if (ret == false) {
            ETRACE("failed to prepare buffer info");
            return false;
        }
        payload = &buffer_info;
    }

    // check payload
    if (!payload) {
        ETRACE("no payload found");
        return false;
    }

    if (payload->force_output_method == FORCE_OUTPUT_GPU) {
        ETRACE("Output method is not supported!");
        return false;
    }

    if (payload->client_transform != mTransform ||
        mBobDeinterlace) {
        payload->hwc_timestamp = systemTime();
        payload->layer_transform = mTransform;
        if (!mRotationBufProvider->setupRotationBuffer(payload, mTransform)) {
            ETRACE("failed to setup rotation buffer");
            return false;
        }
    }

    rotatedMapper = getTTMMapper(mapper, payload);

    return true;
}

bool TngOverlayPlane::flush(uint32_t flags)
{
    RETURN_FALSE_IF_NOT_INIT();
    ATRACE("flags = %#x, type = %d, index = %d", flags, mType, mIndex);

    if (!(flags & PLANE_ENABLE) && !(flags & PLANE_DISABLE))
        return false;

    struct drm_psb_register_rw_arg arg;
    memset(&arg, 0, sizeof(struct drm_psb_register_rw_arg));

    if (flags & PLANE_DISABLE)
        arg.plane_disable_mask = 1;
    else if (flags & PLANE_ENABLE)
        arg.plane_enable_mask = 1;

    arg.plane.type = DC_OVERLAY_PLANE;
    arg.plane.index = mIndex;
    arg.plane.ctx = (mBackBuffer[mCurrent]->gttOffsetInPage << 12);
    // pipe select
    arg.plane.ctx |= mPipeConfig;

    if (flags & PLANE_DISABLE) {
        DTRACE("disabling overlay %d on device %d", mIndex, mDevice);
    }

    // issue ioctl
    Drm *drm = Hwcomposer::getInstance().getDrm();
    bool ret = drm->writeReadIoctl(DRM_PSB_REGISTER_RW, &arg, sizeof(arg));
    if (ret == false) {
        WTRACE("overlay update failed with error code %d", ret);
        return false;
    }

    return true;
}

} // namespace intel
} // namespace android
