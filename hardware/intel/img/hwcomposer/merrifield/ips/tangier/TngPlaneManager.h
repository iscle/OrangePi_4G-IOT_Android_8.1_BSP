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
#ifndef TNG_PLANE_MANAGER_H
#define TNG_PLANE_MANAGER_H

#include <DisplayPlaneManager.h>
#include <linux/psb_drm.h>

namespace android {
namespace intel {

class TngPlaneManager : public DisplayPlaneManager {
public:
    TngPlaneManager();
    virtual ~TngPlaneManager();

public:
    virtual bool initialize();
    virtual void deinitialize();
    virtual bool isValidZOrder(int dsp, ZOrderConfig& config);
    virtual bool assignPlanes(int dsp, ZOrderConfig& config);
    // TODO: remove this API
    virtual void* getZOrderConfig() const;

protected:
    DisplayPlane* allocPlane(int index, int type);
    DisplayPlane* getPlaneHelper(int dsp, int type);

private:
    struct intel_dc_plane_zorder mZorder;
};

} // namespace intel
} // namespace android


#endif /* TNG_PLANE_MANAGER_H */
