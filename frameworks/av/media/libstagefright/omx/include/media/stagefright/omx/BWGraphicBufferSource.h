/*
 * Copyright 2017, The Android Open Source Project
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

#ifndef BWGRAPHIC_BUFFER_SOURCE_H_
#define BWGRAPHIC_BUFFER_SOURCE_H_

#include <binder/Binder.h>
#include <binder/Status.h>
#include <android/BnGraphicBufferSource.h>
#include <android/BnOMXBufferSource.h>
#include <media/IOMX.h>

#include "GraphicBufferSource.h"
#include "IOmxNodeWrapper.h"

namespace android {

using ::android::binder::Status;
using ::android::BnGraphicBufferSource;
using ::android::GraphicBufferSource;
using ::android::IOMXNode;
using ::android::sp;

struct BWGraphicBufferSource : public BnGraphicBufferSource {
    struct BWOMXBufferSource;
    struct BWOmxNodeWrapper;

    sp<GraphicBufferSource> mBase;
    sp<IOMXBufferSource> mOMXBufferSource;

    BWGraphicBufferSource(sp<GraphicBufferSource> const &base);

    Status configure(
            const sp<IOMXNode>& omxNode, int32_t dataSpace) override;
    Status setSuspend(bool suspend, int64_t timeUs) override;
    Status setRepeatPreviousFrameDelayUs(
            int64_t repeatAfterUs) override;
    Status setMaxFps(float maxFps) override;
    Status setTimeLapseConfig(
            double fps, double captureFps) override;
    Status setStartTimeUs(int64_t startTimeUs) override;
    Status setStopTimeUs(int64_t stopTimeUs) override;
    Status getStopTimeOffsetUs(int64_t* stopTimeOffsetUs) override;
    Status setColorAspects(int32_t aspects) override;
    Status setTimeOffsetUs(int64_t timeOffsetsUs) override;
    Status signalEndOfInputStream() override;
};

}  // namespace android

#endif  // ANDROID_HARDWARE_MEDIA_OMX_V1_0_WGRAPHICBUFFERSOURCE_H
