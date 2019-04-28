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
#ifndef TNG_PRIMARY_PLANE_H
#define TNG_PRIMARY_PLANE_H

#include <tangier/TngSpritePlane.h>

namespace android {
namespace intel {

class TngPrimaryPlane : public TngSpritePlane {
public:
    TngPrimaryPlane(int index, int disp);
    virtual ~TngPrimaryPlane();
public:
    bool setDataBuffer(buffer_handle_t handle);
    void setZOrderConfig(ZOrderConfig& config, void *nativeConfig);
    bool assignToDevice(int disp);
private:
    void setFramebufferTarget(buffer_handle_t handle);
    bool enablePlane(bool enabled);
};

} // namespace intel
} // namespace android

#endif /* TNG_PRIMARY_PLANE_H */
