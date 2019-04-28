/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CAR_EVS_APP_RENDERDIRECTVIEW_H
#define CAR_EVS_APP_RENDERDIRECTVIEW_H


#include "RenderBase.h"

#include <android/hardware/automotive/evs/1.0/IEvsEnumerator.h>
#include "ConfigManager.h"
#include "VideoTex.h"


using namespace ::android::hardware::automotive::evs::V1_0;


/*
 * Renders the view from a single specified camera directly to the full display.
 */
class RenderDirectView: public RenderBase {
public:
    RenderDirectView(sp<IEvsEnumerator> enumerator, const ConfigManager::CameraInfo& cam);

    virtual bool activate() override;
    virtual void deactivate() override;

    virtual bool drawFrame(const BufferDesc& tgtBuffer);

protected:
    sp<IEvsEnumerator>              mEnumerator;
    ConfigManager::CameraInfo       mCameraInfo;

    std::unique_ptr<VideoTex>       mTexture;

    GLuint                          mShaderProgram = 0;
};


#endif //CAR_EVS_APP_RENDERDIRECTVIEW_H
