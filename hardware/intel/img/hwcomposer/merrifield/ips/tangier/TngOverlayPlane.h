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
#ifndef TNG_OVERLAY_PLANE_H
#define TNG_OVERLAY_PLANE_H

#include <utils/KeyedVector.h>
#include <hal_public.h>
#include <DisplayPlane.h>
#include <BufferMapper.h>
#include <common/Wsbm.h>
#include <common/OverlayPlaneBase.h>
#include <common/RotationBufferProvider.h>

namespace android {
namespace intel {

class TngOverlayPlane : public OverlayPlaneBase {

public:
    TngOverlayPlane(int index, int disp);
    virtual ~TngOverlayPlane();

    virtual bool flip(void *ctx);
    virtual bool reset();
    virtual void* getContext() const;

    virtual bool initialize(uint32_t bufferCount);
    virtual void deinitialize();
    virtual bool rotatedBufferReady(BufferMapper& mapper, BufferMapper* &rotatedMapper);
protected:
    virtual bool setDataBuffer(BufferMapper& mapper);
    virtual bool flush(uint32_t flags);

protected:
    struct intel_dc_plane_ctx mContext;
    RotationBufferProvider *mRotationBufProvider;
};

} // namespace intel
} // namespace android

#endif /* TNG_OVERLAY_PLANE_H */
