/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef HW_EMULATOR_CAMERA_EMULATED_QEMU_CAMERA_DEVICE_H
#define HW_EMULATOR_CAMERA_EMULATED_QEMU_CAMERA_DEVICE_H

/*
 * Contains declaration of a class EmulatedQemuCameraDevice that encapsulates
 * an emulated camera device connected to the host.
 */

#include "EmulatedCameraDevice.h"
#include "QemuClient.h"

namespace android {

class EmulatedQemuCamera;

/* Encapsulates an emulated camera device connected to the host.
 */
class EmulatedQemuCameraDevice : public EmulatedCameraDevice {
public:
    /* Constructs EmulatedQemuCameraDevice instance. */
    explicit EmulatedQemuCameraDevice(EmulatedQemuCamera* camera_hal);

    /* Destructs EmulatedQemuCameraDevice instance. */
    ~EmulatedQemuCameraDevice();

    /***************************************************************************
     * Public API
     **************************************************************************/

public:
    /* Initializes EmulatedQemuCameraDevice instance.
     * Param:
     *  device_name - Name of the camera device connected to the host. The name
     *      that is used here must have been reported by the 'factory' camera
     *      service when it listed camera devices connected to the host.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    status_t Initialize(const char* device_name);

    /***************************************************************************
     * Emulated camera device abstract interface implementation.
     * See declarations of these methods in EmulatedCameraDevice class for
     * information on each of these methods.
     **************************************************************************/

public:
    /* Connects to the camera device. */
    status_t connectDevice();

    /* Disconnects from the camera device. */
    status_t disconnectDevice();

    /* Starts capturing frames from the camera device. */
    status_t startDevice(int width, int height, uint32_t pix_fmt);

    /* Stops capturing frames from the camera device. */
    status_t stopDevice();

    /***************************************************************************
     * EmulatedCameraDevice virtual overrides
     * See declarations of these methods in EmulatedCameraDevice class for
     * information on each of these methods.
     **************************************************************************/

public:

    /* Copy the current frame to |buffer| */
    status_t getCurrentFrame(void* buffer, uint32_t pixelFormat) override;

    /* Copy the current preview frame to |buffer| */
    status_t getCurrentPreviewFrame(void* buffer) override;

    /* Get a pointer to the current frame, lock it first using FrameLock in
     * EmulatedCameraDevice class */
    const void* getCurrentFrame() override;

    /***************************************************************************
     * Worker thread management overrides.
     * See declarations of these methods in EmulatedCameraDevice class for
     * information on each of these methods.
     **************************************************************************/

protected:
    /* Implementation of the frame production routine. */
    bool produceFrame(void* buffer) override;

    void* getPrimaryBuffer() override;
    void* getSecondaryBuffer() override;

    /***************************************************************************
     * Qemu camera device data members
     **************************************************************************/

private:
    /* Qemu client that is used to communicate with the 'emulated camera'
     * service, created for this instance in the emulator. */
    CameraQemuClient    mQemuClient;

    /* Name of the camera device connected to the host. */
    String8             mDeviceName;

    /* Current preview framebuffer. */
    std::vector<uint32_t> mPreviewFrames[2];

    /* Since the Qemu camera needs to keep track of two buffers per frame we
     * use a pair here. One frame is the camera frame and the other is the
     * preview frame. These are in different formats and instead of converting
     * them in the guest it's more efficient to have the host provide the same
     * frame in two different formats. The first buffer in the pair is the raw
     * frame and the second buffer is the RGB encoded frame. The downside of
     * this is that we need to override the getCurrentFrame and
     * getCurrentPreviewFrame methods to extract the correct buffer from this
     * pair. */
    using FrameBufferPair = std::pair<uint8_t*, uint32_t*>;
    FrameBufferPair     mFrameBufferPairs[2];

};

}; /* namespace android */

#endif  /* HW_EMULATOR_CAMERA_EMULATED_QEMU_CAMERA_DEVICE_H */
