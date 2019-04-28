/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "VirtualCamera.h"
#include "HalCamera.h"
#include "Enumerator.h"

#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>


namespace android {
namespace automotive {
namespace evs {
namespace V1_0 {
namespace implementation {


VirtualCamera::VirtualCamera(sp<HalCamera> halCamera) :
    mHalCamera(halCamera) {
}


VirtualCamera::~VirtualCamera() {
    shutdown();
}


void VirtualCamera::shutdown() {
    // In normal operation, the stream should already be stopped by the time we get here
    if (mStreamState != STOPPED) {
        // Note that if we hit this case, no terminating frame will be sent to the client,
        // but they're probably already dead anyway.
        ALOGW("Virtual camera being shutdown while stream is running");
        mStreamState = STOPPED;

        if (mFramesHeld.size() > 0) {
            ALOGW("VirtualCamera destructing with frames in flight.");

            // Return to the underlying hardware camera any buffers the client was holding
            for (auto&& heldBuffer : mFramesHeld) {
                // Tell our parent that we're done with this buffer
                mHalCamera->doneWithFrame(heldBuffer);
            }
            mFramesHeld.clear();
        }
    }

    // Drop our reference to our associated hardware camera
    mHalCamera = nullptr;
}


bool VirtualCamera::deliverFrame(const BufferDesc& buffer) {
    if (buffer.memHandle == nullptr) {
        // Warn if we got an unexpected stream termination
        if (mStreamState != STOPPING) {
            // TODO:  Should we suicide in this case to trigger a restart of the stack?
            ALOGW("Stream unexpectedly stopped");
        }

        // This is the stream end marker, so send it along, then mark the stream as stopped
        mStream->deliverFrame(buffer);
        mStreamState = STOPPED;
        return true;
    } else {
        if (mStreamState == STOPPED) {
            // A stopped stream gets no frames
            return false;
        } else if (mFramesHeld.size() >= mFramesAllowed) {
            // Indicate that we declined to send the frame to the client because they're at quota
            ALOGI("Skipping new frame as we hold %zu of %u allowed.",
                  mFramesHeld.size(), mFramesAllowed);
            return false;
        } else {
            // Keep a record of this frame so we can clean up if we have to in case of client death
            mFramesHeld.push_back(buffer);

            // Pass this buffer through to our client
            mStream->deliverFrame(buffer);
            return true;
        }
    }
}


// Methods from ::android::hardware::automotive::evs::V1_0::IEvsCamera follow.
Return<void> VirtualCamera::getCameraInfo(getCameraInfo_cb info_cb) {
    // Straight pass through to hardware layer
    return mHalCamera->getHwCamera()->getCameraInfo(info_cb);
}


Return<EvsResult> VirtualCamera::setMaxFramesInFlight(uint32_t bufferCount) {
    // How many buffers are we trying to add (or remove if negative)
    int bufferCountChange = bufferCount - mFramesAllowed;

    // Ask our parent for more buffers
    bool result = mHalCamera->changeFramesInFlight(bufferCountChange);
    if (!result) {
        ALOGE("Failed to change buffer count by %d to %d", bufferCountChange, bufferCount);
        return EvsResult::BUFFER_NOT_AVAILABLE;
    }

    // Update our notion of how many frames we're allowed
    mFramesAllowed = bufferCount;
    return EvsResult::OK;
}


Return<EvsResult> VirtualCamera::startVideoStream(const ::android::sp<IEvsCameraStream>& stream)  {
    // We only support a single stream at a time
    if (mStreamState != STOPPED) {
        ALOGE("ignoring startVideoStream call when a stream is already running.");
        return EvsResult::STREAM_ALREADY_RUNNING;
    }

    // Validate our held frame count is starting out at zero as we expect
    assert(mFramesHeld.size() == 0);

    // Record the user's callback for use when we have a frame ready
    mStream = stream;
    mStreamState = RUNNING;

    // Tell the underlying camera hardware that we want to stream
    Return<EvsResult> result = mHalCamera->clientStreamStarting();
    if ((!result.isOk()) || (result != EvsResult::OK)) {
        // If we failed to start the underlying stream, then we're not actually running
        mStream = nullptr;
        mStreamState = STOPPED;
        return EvsResult::UNDERLYING_SERVICE_ERROR;
    }

    // TODO:  Detect and exit if we encounter a stalled stream or unresponsive driver?
    // Consider using a timer and watching for frame arrival?

    return EvsResult::OK;
}


Return<void> VirtualCamera::doneWithFrame(const BufferDesc& buffer) {
    if (buffer.memHandle == nullptr) {
        ALOGE("ignoring doneWithFrame called with invalid handle");
    } else {
        // Find this buffer in our "held" list
        auto it = mFramesHeld.begin();
        while (it != mFramesHeld.end()) {
            if (it->bufferId == buffer.bufferId) {
                // found it!
                break;
            }
            ++it;
        }
        if (it == mFramesHeld.end()) {
            // We should always find the frame in our "held" list
            ALOGE("Ignoring doneWithFrame called with unrecognized frameID %d", buffer.bufferId);
        } else {
            // Take this frame out of our "held" list
            mFramesHeld.erase(it);

            // Tell our parent that we're done with this buffer
            mHalCamera->doneWithFrame(buffer);
        }
    }

    return Void();
}


Return<void> VirtualCamera::stopVideoStream()  {
    if (mStreamState == RUNNING) {
        // Tell the frame delivery pipeline we don't want any more frames
        mStreamState = STOPPING;

        // Deliver an empty frame to close out the frame stream
        BufferDesc nullBuff = {};
        auto result = mStream->deliverFrame(nullBuff);
        if (!result.isOk()) {
            ALOGE("Error delivering end of stream marker");
        }

        // Since we are single threaded, no frame can be delivered while this function is running,
        // so we can go directly to the STOPPED state here on the server.
        // Note, however, that there still might be frames already queued that client will see
        // after returning from the client side of this call.
        mStreamState = STOPPED;

        // Give the underlying hardware camera the heads up that it might be time to stop
        mHalCamera->clientStreamEnding();
    }

    return Void();
}


Return<int32_t> VirtualCamera::getExtendedInfo(uint32_t opaqueIdentifier)  {
    // Pass straight through to the hardware device
    return mHalCamera->getHwCamera()->getExtendedInfo(opaqueIdentifier);
}


Return<EvsResult> VirtualCamera::setExtendedInfo(uint32_t opaqueIdentifier, int32_t opaqueValue)  {
    // Pass straight through to the hardware device
    // TODO: Should we restrict access to this entry point somehow?
    return mHalCamera->getHwCamera()->setExtendedInfo(opaqueIdentifier, opaqueValue);
}

} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace android
