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

#include "StreamHandler.h"

#include <stdio.h>
#include <string.h>

#include <log/log.h>
#include <cutils/native_handle.h>


StreamHandler::StreamHandler(android::sp <IEvsCamera> pCamera) :
    mCamera(pCamera)
{
    // We rely on the camera having at least two buffers available since we'll hold one and
    // expect the camera to be able to capture a new image in the background.
    pCamera->setMaxFramesInFlight(2);
}


void StreamHandler::shutdown()
{
    // Make sure we're not still streaming
    blockingStopStream();

    // At this point, the receiver thread is no longer running, so we can safely drop
    // our remote object references so they can be freed
    mCamera = nullptr;
}


bool StreamHandler::startStream() {
    std::unique_lock<std::mutex> lock(mLock);

    if (!mRunning) {
        // Tell the camera to start streaming
        Return <EvsResult> result = mCamera->startVideoStream(this);
        if (result != EvsResult::OK) {
            return false;
        }

        // Mark ourselves as running
        mRunning = true;
    }

    return true;
}


void StreamHandler::asyncStopStream() {
    // Tell the camera to stop streaming.
    // This will result in a null frame being delivered when the stream actually stops.
    mCamera->stopVideoStream();
}


void StreamHandler::blockingStopStream() {
    // Tell the stream to stop
    asyncStopStream();

    // Wait until the stream has actually stopped
    std::unique_lock<std::mutex> lock(mLock);
    if (mRunning) {
        mSignal.wait(lock, [this]() { return !mRunning; });
    }
}


bool StreamHandler::isRunning() {
    std::unique_lock<std::mutex> lock(mLock);
    return mRunning;
}


bool StreamHandler::newFrameAvailable() {
    std::unique_lock<std::mutex> lock(mLock);
    return (mReadyBuffer >= 0);
}


const BufferDesc& StreamHandler::getNewFrame() {
    std::unique_lock<std::mutex> lock(mLock);

    if (mHeldBuffer >= 0) {
        ALOGE("Ignored call for new frame while still holding the old one.");
    } else {
        if (mReadyBuffer < 0) {
            ALOGE("Returning invalid buffer because we don't have any.  Call newFrameAvailable first?");
            mReadyBuffer = 0;   // This is a lie!
        }

        // Move the ready buffer into the held position, and clear the ready position
        mHeldBuffer = mReadyBuffer;
        mReadyBuffer = -1;
    }

    return mBuffers[mHeldBuffer];
}


void StreamHandler::doneWithFrame(const BufferDesc& buffer) {
    std::unique_lock<std::mutex> lock(mLock);

    // We better be getting back the buffer we original delivered!
    if ((mHeldBuffer < 0) || (buffer.bufferId != mBuffers[mHeldBuffer].bufferId)) {
        ALOGE("StreamHandler::doneWithFrame got an unexpected buffer!");
    }

    // Send the buffer back to the underlying camera
    mCamera->doneWithFrame(mBuffers[mHeldBuffer]);

    // Clear the held position
    mHeldBuffer = -1;
}


Return<void> StreamHandler::deliverFrame(const BufferDesc& buffer) {
    ALOGD("Received a frame from the camera (%p)", buffer.memHandle.getNativeHandle());

    // Take the lock to protect our frame slots and running state variable
    {
        std::unique_lock <std::mutex> lock(mLock);

        if (buffer.memHandle.getNativeHandle() == nullptr) {
            // Signal that the last frame has been received and the stream is stopped
            mRunning = false;
        } else {
            // Do we already have a "ready" frame?
            if (mReadyBuffer >= 0) {
                // Send the previously saved buffer back to the camera unused
                mCamera->doneWithFrame(mBuffers[mReadyBuffer]);

                // We'll reuse the same ready buffer index
            } else if (mHeldBuffer >= 0) {
                // The client is holding a buffer, so use the other slot for "on deck"
                mReadyBuffer = 1 - mHeldBuffer;
            } else {
                // This is our first buffer, so just pick a slot
                mReadyBuffer = 0;
            }

            // Save this frame until our client is interested in it
            mBuffers[mReadyBuffer] = buffer;
        }
    }

    // Notify anybody who cares that things have changed
    mSignal.notify_all();

    return Void();
}
