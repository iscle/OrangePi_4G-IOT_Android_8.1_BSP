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
#ifndef TNG_SPRITE_PLANE_H
#define TNG_SPRITE_PLANE_H

#include <utils/KeyedVector.h>
#include <hal_public.h>
#include <Hwcomposer.h>
#include <BufferCache.h>
#include <DisplayPlane.h>

#include <common/SpritePlaneBase.h>

namespace android {
namespace intel {

class TngSpritePlane : public SpritePlaneBase {
public:
    TngSpritePlane(int index, int disp);
    virtual ~TngSpritePlane();
public:
    virtual void* getContext() const;
    virtual void setZOrderConfig(ZOrderConfig& config, void *nativeConfig);
    virtual bool isDisabled();
protected:
    virtual bool setDataBuffer(BufferMapper& mapper);
    virtual bool enablePlane(bool enabled);
protected:
    struct intel_dc_plane_ctx mContext;
};

} // namespace intel
} // namespace android

#endif /* TNG_SPRITE_PLANE_H */
