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
#ifndef PNW_OVERLAY_PLANE_H
#define PNW_OVERLAY_PLANE_H

#include <utils/KeyedVector.h>
#include <hal_public.h>
#include <DisplayPlane.h>
#include <BufferMapper.h>
#include <common/Wsbm.h>
#include <common/OverlayPlaneBase.h>

namespace android {
namespace intel {

class PnwOverlayPlane : public OverlayPlaneBase {

public:
    PnwOverlayPlane(int index, int disp);
    ~PnwOverlayPlane();

    virtual bool flip();
    virtual void* getContext() const;
};

} // namespace intel
} // namespace android

#endif /* PNW_OVERLAY_PLANE_H */
