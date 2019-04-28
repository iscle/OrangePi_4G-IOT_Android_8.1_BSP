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

#include "EvsGlDisplay.h"

#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>


namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_0 {
namespace implementation {


EvsGlDisplay::EvsGlDisplay() {
    ALOGD("EvsGlDisplay instantiated");

    // Set up our self description
    // NOTE:  These are arbitrary values chosen for testing
    mInfo.displayId             = "Mock Display";
    mInfo.vendorFlags           = 3870;
}


EvsGlDisplay::~EvsGlDisplay() {
    ALOGD("EvsGlDisplay being destroyed");
	forceShutdown();
}


/**
 * This gets called if another caller "steals" ownership of the display
 */
void EvsGlDisplay::forceShutdown()
{
    ALOGD("EvsGlDisplay forceShutdown");
    std::lock_guard<std::mutex> lock(mAccessLock);

    // If the buffer isn't being held by a remote client, release it now as an
    // optimization to release the resources more quickly than the destructor might
    // get called.
    if (mBuffer.memHandle) {
        // Report if we're going away while a buffer is outstanding
        if (mFrameBusy) {
            ALOGE("EvsGlDisplay going down while client is holding a buffer");
        }

        // Drop the graphics buffer we've been using
        GraphicBufferAllocator& alloc(GraphicBufferAllocator::get());
        alloc.free(mBuffer.memHandle);
        mBuffer.memHandle = nullptr;

        mGlWrapper.shutdown();
    }

    // Put this object into an unrecoverable error state since somebody else
    // is going to own the display now.
    mRequestedState = DisplayState::DEAD;
}


/**
 * Returns basic information about the EVS display provided by the system.
 * See the description of the DisplayDesc structure for details.
 */
Return<void> EvsGlDisplay::getDisplayInfo(getDisplayInfo_cb _hidl_cb)  {
    ALOGD("getDisplayInfo");

    // Send back our self description
    _hidl_cb(mInfo);
    return Void();
}


/**
 * Clients may set the display state to express their desired state.
 * The HAL implementation must gracefully accept a request for any state
 * while in any other state, although the response may be to ignore the request.
 * The display is defined to start in the NOT_VISIBLE state upon initialization.
 * The client is then expected to request the VISIBLE_ON_NEXT_FRAME state, and
 * then begin providing video.  When the display is no longer required, the client
 * is expected to request the NOT_VISIBLE state after passing the last video frame.
 */
Return<EvsResult> EvsGlDisplay::setDisplayState(DisplayState state) {
    ALOGD("setDisplayState");
    std::lock_guard<std::mutex> lock(mAccessLock);

    if (mRequestedState == DisplayState::DEAD) {
        // This object no longer owns the display -- it's been superceeded!
        return EvsResult::OWNERSHIP_LOST;
    }

    // Ensure we recognize the requested state so we don't go off the rails
    if (state >= DisplayState::NUM_STATES) {
        return EvsResult::INVALID_ARG;
    }

    switch (state) {
    case DisplayState::NOT_VISIBLE:
        mGlWrapper.hideWindow();
        break;
    case DisplayState::VISIBLE:
        mGlWrapper.showWindow();
        break;
    default:
        break;
    }

    // Record the requested state
    mRequestedState = state;

    return EvsResult::OK;
}


/**
 * The HAL implementation should report the actual current state, which might
 * transiently differ from the most recently requested state.  Note, however, that
 * the logic responsible for changing display states should generally live above
 * the device layer, making it undesirable for the HAL implementation to
 * spontaneously change display states.
 */
Return<DisplayState> EvsGlDisplay::getDisplayState()  {
    ALOGD("getDisplayState");
    std::lock_guard<std::mutex> lock(mAccessLock);

    return mRequestedState;
}


/**
 * This call returns a handle to a frame buffer associated with the display.
 * This buffer may be locked and written to by software and/or GL.  This buffer
 * must be returned via a call to returnTargetBufferForDisplay() even if the
 * display is no longer visible.
 */
Return<void> EvsGlDisplay::getTargetBuffer(getTargetBuffer_cb _hidl_cb)  {
    ALOGV("getTargetBuffer");
    std::lock_guard<std::mutex> lock(mAccessLock);

    if (mRequestedState == DisplayState::DEAD) {
        ALOGE("Rejecting buffer request from object that lost ownership of the display.");
        BufferDesc nullBuff = {};
        _hidl_cb(nullBuff);
        return Void();
    }

    // If we don't already have a buffer, allocate one now
    if (!mBuffer.memHandle) {
        // Initialize our display window
        // NOTE:  This will cause the display to become "VISIBLE" before a frame is actually
        // returned, which is contrary to the spec and will likely result in a black frame being
        // (briefly) shown.
        if (!mGlWrapper.initialize()) {
            // Report the failure
            ALOGE("Failed to initialize GL display");
            BufferDesc nullBuff = {};
            _hidl_cb(nullBuff);
            return Void();
        }

        // Assemble the buffer description we'll use for our render target
        mBuffer.width       = mGlWrapper.getWidth();
        mBuffer.height      = mGlWrapper.getHeight();
        mBuffer.format      = HAL_PIXEL_FORMAT_RGBA_8888;
        mBuffer.usage       = GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_HW_COMPOSER;
        mBuffer.bufferId    = 0x3870;  // Arbitrary magic number for self recognition
        mBuffer.pixelSize   = 4;

        // Allocate the buffer that will hold our displayable image
        buffer_handle_t handle = nullptr;
        GraphicBufferAllocator& alloc(GraphicBufferAllocator::get());
        status_t result = alloc.allocate(mBuffer.width, mBuffer.height,
                                         mBuffer.format, 1,
                                         mBuffer.usage, &handle,
                                         &mBuffer.stride,
                                         0, "EvsGlDisplay");
        if (result != NO_ERROR) {
            ALOGE("Error %d allocating %d x %d graphics buffer",
                  result, mBuffer.width, mBuffer.height);
            BufferDesc nullBuff = {};
            _hidl_cb(nullBuff);
            mGlWrapper.shutdown();
            return Void();
        }
        if (!handle) {
            ALOGE("We didn't get a buffer handle back from the allocator");
            BufferDesc nullBuff = {};
            _hidl_cb(nullBuff);
            mGlWrapper.shutdown();
            return Void();
        }

        mBuffer.memHandle = handle;
        ALOGD("Allocated new buffer %p with stride %u",
              mBuffer.memHandle.getNativeHandle(), mBuffer.stride);
        mFrameBusy = false;
    }

    // Do we have a frame available?
    if (mFrameBusy) {
        // This means either we have a 2nd client trying to compete for buffers
        // (an unsupported mode of operation) or else the client hasn't returned
        // a previously issued buffer yet (they're behaving badly).
        // NOTE:  We have to make the callback even if we have nothing to provide
        ALOGE("getTargetBuffer called while no buffers available.");
        BufferDesc nullBuff = {};
        _hidl_cb(nullBuff);
        return Void();
    } else {
        // Mark our buffer as busy
        mFrameBusy = true;

        // Send the buffer to the client
        ALOGV("Providing display buffer handle %p as id %d",
              mBuffer.memHandle.getNativeHandle(), mBuffer.bufferId);
        _hidl_cb(mBuffer);
        return Void();
    }
}


/**
 * This call tells the display that the buffer is ready for display.
 * The buffer is no longer valid for use by the client after this call.
 */
Return<EvsResult> EvsGlDisplay::returnTargetBufferForDisplay(const BufferDesc& buffer)  {
    ALOGV("returnTargetBufferForDisplay %p", buffer.memHandle.getNativeHandle());
    std::lock_guard<std::mutex> lock(mAccessLock);

    // Nobody should call us with a null handle
    if (!buffer.memHandle.getNativeHandle()) {
        ALOGE ("returnTargetBufferForDisplay called without a valid buffer handle.\n");
        return EvsResult::INVALID_ARG;
    }
    if (buffer.bufferId != mBuffer.bufferId) {
        ALOGE ("Got an unrecognized frame returned.\n");
        return EvsResult::INVALID_ARG;
    }
    if (!mFrameBusy) {
        ALOGE ("A frame was returned with no outstanding frames.\n");
        return EvsResult::BUFFER_NOT_AVAILABLE;
    }

    mFrameBusy = false;

    // If we've been displaced by another owner of the display, then we can't do anything else
    if (mRequestedState == DisplayState::DEAD) {
        return EvsResult::OWNERSHIP_LOST;
    }

    // If we were waiting for a new frame, this is it!
    if (mRequestedState == DisplayState::VISIBLE_ON_NEXT_FRAME) {
        mRequestedState = DisplayState::VISIBLE;
        mGlWrapper.showWindow();
    }

    // Validate we're in an expected state
    if (mRequestedState != DisplayState::VISIBLE) {
        // Not sure why a client would send frames back when we're not visible.
        ALOGW ("Got a frame returned while not visible - ignoring.\n");
    } else {
        // Update the texture contents with the provided data
// TODO:  Why doesn't it work to pass in the buffer handle we got from HIDL?
//        if (!mGlWrapper.updateImageTexture(buffer)) {
        if (!mGlWrapper.updateImageTexture(mBuffer)) {
            return EvsResult::UNDERLYING_SERVICE_ERROR;
        }

        // Put the image on the screen
        mGlWrapper.renderImageToScreen();
    }

    return EvsResult::OK;
}

} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace hardware
} // namespace android
