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
#ifndef PNW_PRIMARY_PLANE_H
#define PNW_PRIMARY_PLANE_H

#include <penwell/PnwSpritePlane.h>

namespace android {
namespace intel {

class PnwPrimaryPlane : public PnwSpritePlane {
public:
    PnwPrimaryPlane(int index, int disp);
    ~PnwPrimaryPlane();
public:
    bool setDataBuffer(uint32_t handle);
    bool assignToDevice(int disp);
private:
    void setFramebufferTarget(DataBuffer& buf);
};

} // namespace intel
} // namespace android

#endif /* TNG_PRIMARY_PLANE_H */
