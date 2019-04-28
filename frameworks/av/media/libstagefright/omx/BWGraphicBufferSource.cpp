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

//#define LOG_NDEBUG 0
#define LOG_TAG "BWGraphicBufferSource"

#include <media/stagefright/omx/BWGraphicBufferSource.h>
#include <media/stagefright/omx/OMXUtils.h>
#include <media/openmax/OMX_Component.h>
#include <media/openmax/OMX_IndexExt.h>
#include <media/OMXBuffer.h>
#include <media/IOMX.h>

namespace android {

static const OMX_U32 kPortIndexInput = 0;

struct BWGraphicBufferSource::BWOmxNodeWrapper : public IOmxNodeWrapper {
    sp<IOMXNode> mOMXNode;

    BWOmxNodeWrapper(const sp<IOMXNode> &omxNode): mOMXNode(omxNode) {
    }

    virtual status_t emptyBuffer(
            int32_t bufferId, uint32_t flags,
            const sp<GraphicBuffer> &buffer,
            int64_t timestamp, int fenceFd) override {
        return mOMXNode->emptyBuffer(bufferId, buffer, flags, timestamp, fenceFd);
    }

    virtual void dispatchDataSpaceChanged(
            int32_t dataSpace, int32_t aspects, int32_t pixelFormat) override {
        omx_message msg;
        msg.type = omx_message::EVENT;
        msg.fenceFd = -1;
        msg.u.event_data.event = OMX_EventDataSpaceChanged;
        msg.u.event_data.data1 = dataSpace;
        msg.u.event_data.data2 = aspects;
        msg.u.event_data.data3 = pixelFormat;
        mOMXNode->dispatchMessage(msg);
    }
};

struct BWGraphicBufferSource::BWOMXBufferSource : public BnOMXBufferSource {
    sp<GraphicBufferSource> mSource;

    BWOMXBufferSource(const sp<GraphicBufferSource> &source): mSource(source) {
    }

    Status onOmxExecuting() override {
        return mSource->onOmxExecuting();
    }

    Status onOmxIdle() override {
        return mSource->onOmxIdle();
    }

    Status onOmxLoaded() override {
        return mSource->onOmxLoaded();
    }

    Status onInputBufferAdded(int bufferId) override {
        return mSource->onInputBufferAdded(bufferId);
    }

    Status onInputBufferEmptied(
            int bufferId, const OMXFenceParcelable& fenceParcel) override {
        return mSource->onInputBufferEmptied(bufferId, fenceParcel.get());
    }
};

BWGraphicBufferSource::BWGraphicBufferSource(
        sp<GraphicBufferSource> const& base) :
    mBase(base),
    mOMXBufferSource(new BWOMXBufferSource(base)) {
}

::android::binder::Status BWGraphicBufferSource::configure(
        const sp<IOMXNode>& omxNode, int32_t dataSpace) {
    // Do setInputSurface() first, the node will try to enable metadata
    // mode on input, and does necessary error checking. If this fails,
    // we can't use this input surface on the node.
    status_t err = omxNode->setInputSurface(mOMXBufferSource);
    if (err != NO_ERROR) {
        ALOGE("Unable to set input surface: %d", err);
        return Status::fromStatusT(err);
    }

    // use consumer usage bits queried from encoder, but always add
    // HW_VIDEO_ENCODER for backward compatibility.
    uint32_t consumerUsage;
    if (omxNode->getParameter(
            (OMX_INDEXTYPE)OMX_IndexParamConsumerUsageBits,
            &consumerUsage, sizeof(consumerUsage)) != OK) {
        consumerUsage = 0;
    }

    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexInput;

    err = omxNode->getParameter(
            OMX_IndexParamPortDefinition, &def, sizeof(def));
    if (err != NO_ERROR) {
        ALOGE("Failed to get port definition: %d", err);
        return Status::fromStatusT(UNKNOWN_ERROR);
    }

    return Status::fromStatusT(mBase->configure(
              new BWOmxNodeWrapper(omxNode),
              dataSpace,
              def.nBufferCountActual,
              def.format.video.nFrameWidth,
              def.format.video.nFrameHeight,
              consumerUsage));
}

::android::binder::Status BWGraphicBufferSource::setSuspend(
        bool suspend, int64_t timeUs) {
    return Status::fromStatusT(mBase->setSuspend(suspend, timeUs));
}

::android::binder::Status BWGraphicBufferSource::setRepeatPreviousFrameDelayUs(
        int64_t repeatAfterUs) {
    return Status::fromStatusT(mBase->setRepeatPreviousFrameDelayUs(repeatAfterUs));
}

::android::binder::Status BWGraphicBufferSource::setMaxFps(float maxFps) {
    return Status::fromStatusT(mBase->setMaxFps(maxFps));
}

::android::binder::Status BWGraphicBufferSource::setTimeLapseConfig(
        double fps, double captureFps) {
    return Status::fromStatusT(mBase->setTimeLapseConfig(
            fps, captureFps));
}

::android::binder::Status BWGraphicBufferSource::setStartTimeUs(
        int64_t startTimeUs) {
    return Status::fromStatusT(mBase->setStartTimeUs(startTimeUs));
}

::android::binder::Status BWGraphicBufferSource::setStopTimeUs(
        int64_t stopTimeUs) {
    return Status::fromStatusT(mBase->setStopTimeUs(stopTimeUs));
}

::android::binder::Status BWGraphicBufferSource::getStopTimeOffsetUs(
        int64_t *stopTimeOffsetUs) {
    return Status::fromStatusT(mBase->getStopTimeOffsetUs(stopTimeOffsetUs));
}

::android::binder::Status BWGraphicBufferSource::setColorAspects(
        int32_t aspects) {
    return Status::fromStatusT(mBase->setColorAspects(aspects));
}

::android::binder::Status BWGraphicBufferSource::setTimeOffsetUs(
        int64_t timeOffsetsUs) {
    return Status::fromStatusT(mBase->setTimeOffsetUs(timeOffsetsUs));
}

::android::binder::Status BWGraphicBufferSource::signalEndOfInputStream() {
    return Status::fromStatusT(mBase->signalEndOfInputStream());
}

}  // namespace android
