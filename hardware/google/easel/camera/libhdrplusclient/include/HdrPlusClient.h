/*
 * Copyright 2016 The Android Open Source Project
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

#ifndef HDR_PLUS_CLIENT_H
#define HDR_PLUS_CLIENT_H

#include "CameraMetadata.h"
#include "hardware/camera3.h"
#include "HdrPlusClientListener.h"
#include "HdrPlusTypes.h"

using ::android::hardware::camera::common::V1_0::helper::CameraMetadata;
namespace android {

/**
 * HdrPlusClient
 *
 * HdrPlusClient class can be used to connect to HDR+ service to perform HDR+ processing on
 * Easel.
 */
class HdrPlusClient {
public:
    // HdrPlusClientListener is the listener to receive callbacks from HDR+ client. The listener
    // must be valid during the life cycle of HdrPlusClient
    HdrPlusClient(HdrPlusClientListener *) {};
    /*
     * The recommended way to create an HdrPlusClient instance is via
     * EaselManagerClient::openHdrPlusClientAsync() or EaselManagerClient::openHdrPlusClientAsync().
     * EaselManagerClient will make sure Easel is in a valid state to open an HDR+ client. To close
     * an HdrPlusClient, use EaselManagerClient::closeHdrPlusClient.
     */
    virtual ~HdrPlusClient() {};

    /*
     * Connect to HDR+ service.
     *
     * If EaselManagerClient is used to create the HdrPlusClient, it is already connected.
     *
     * Returns:
     *  0:          on success.
     *  -EEXIST:    if it's already connected.
     *  -ENODEV:    if connecting failed due to a serious error.
     */
    virtual status_t connect() = 0;

    /*
     * Set the static metadata of current camera device.
     *
     * Must be called after connect() and before configuring streams.
     *
     * staticMetadata is the static metadata of current camera device.
     *
     * Returns:
     *  0:         on success.
     *  -ENODEV:   if HDR+ service is not connected.
     */
    virtual status_t setStaticMetadata(const camera_metadata_t &staticMetadata) = 0;

    /*
     * Configure streams.
     *
     * Must be called when configuration changes including input (sensor) resolution and format, and
     * output resolutions and formats.
     *
     * inputConfig contains the information about the input frames or sensor configurations.
     * outputConfigs is a vector of output stream configurations.
     *
     * Returns:
     *  0:          on success.
     *  -EINVAL:    if outputConfigs is empty or the configurations are not supported.
     *  -ENODEV:    if HDR+ service is not connected.
     */
    virtual status_t configureStreams(const pbcamera::InputConfiguration &inputConfig,
            const std::vector<pbcamera::StreamConfiguration> &outputConfigs) = 0;

    /*
     * Enable or disable ZSL HDR+ mode.
     *
     * When ZSL HDR+ mode is enabled, Easel will capture ZSL RAW buffers. ZSL HDR+ mode should be
     * disabled to reduce power consumption when HDR+ processing is not necessary, e.g in video
     * mode.
     *
     * enabled is a flag indicating whether to enable ZSL HDR+ mode.
     *
     * Returns:
     *  0:          on success.
     *  -ENODEV:    if HDR+ service is not connected, or streams are not configured.
     */
    virtual status_t setZslHdrPlusMode(bool enabled) = 0;

    /*
     * Submit a capture request for HDR+ outputs.
     *
     * For each output buffer in CaptureRequest, it will be returned in a CaptureResult via
     * HdrPlusClientListener::onCaptureResult(). HdrPlusClientListener::onCaptureResult() may be
     * invoked multiple times to return all output buffers in one CaptureRequest. Each output
     * buffer will be returned in CaptureResult only once.
     *
     * request is a CaptureRequest containing output buffers to be filled by HDR+ service.
     * requestMetadata is the metadata for this request.
     *
     * Returns:
     *  0:              on success.
     *  -EINVAL:        if the request is invalid such as containing invalid stream IDs.
     */
    virtual status_t submitCaptureRequest(pbcamera::CaptureRequest *request,
            const CameraMetadata &requestMetadata) = 0;

    /*
     * Send an input buffer to HDR+ service. This is used when HDR+ service's input buffers come
     * from the client rather than MIPI.
     *
     * inputBuffer is the input buffer to send to HDR+ service. After this method returns, the
     *             buffer has been copied (with DMA) to HDR+ service and the caller has the
     *             ownership of the buffer.
     */
    virtual void notifyInputBuffer(const pbcamera::StreamBuffer &inputBuffer,
            int64_t timestampNs) = 0;

    /*
     * Notify about result metadata of a frame that AP captured. This may be called multiple times
     * for a frame to send multiple partial metadata and lastMetadata must be false except for the
     * last partial metadata. When there is only one metadata for a frame, lastMetadata must be
     * true.
     *
     * frameNumber is a unique frame number that the metadata belong to.
     * resultMetadata is the result metadata of a frame that AP captured.
     * lastMetadata is a flag indicating whether this is the last metadata for the frame number.
     */
    virtual void notifyFrameMetadata(uint32_t frameNumber, const camera_metadata_t &resultMetadata,
            bool lastMetadata=true) = 0;

private:
    // Disallow copy and assign.
    HdrPlusClient(const HdrPlusClient&) = delete;
    void operator=(const HdrPlusClient&) = delete;
};

} // namespace android

#endif // HDR_PLUS_CLIENT_H
