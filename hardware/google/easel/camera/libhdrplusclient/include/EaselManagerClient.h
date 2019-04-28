/*
 * Copyright 2017 The Android Open Source Project
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
#ifndef EASEL_MANAGER_CLIENT_H
#define EASEL_MANAGER_CLIENT_H

#include <future>

#include <utils/Errors.h>
#include <utils/Mutex.h>

#define FW_VER_SIZE 24

namespace android {

class EaselManagerClientListener;
class HdrPlusClient;
class HdrPlusClientListener;

class EaselManagerClient {
public:
    static std::unique_ptr<EaselManagerClient> create();

    EaselManagerClient() {};
    virtual ~EaselManagerClient() {};

    /*
     * Return if Easel is present on the device.
     *
     * If Easel is not present, all other calls to HdrPlusClient are invalid.
     */
    virtual bool isEaselPresentOnDevice() const = 0;

    /*
     * Open Easel manager client.
     *
     * This will power on Easel and initialize Easel manager client.
     */
    virtual status_t open() = 0;

    /*
     * Suspend Easel.
     *
     * Put Easel on suspend mode.
     */
    virtual status_t suspend() = 0;

    /*
     * Resume Easel.
     *
     * Resume Easel from suspend mode.
     *
     * listener will be invoked for Easel status.
     */
    virtual status_t resume(EaselManagerClientListener *listener) = 0;

    /*
     * Retrieve Easel firmware version.
     *
     * Firmware version string is added to image exif
     */
    virtual status_t getFwVersion(char *fwVersion) = 0;

    /*
     * Start MIPI with an output pixel lock rate for a camera.
     *
     * Can be called when Easel is powered on or resumed, for Easel to start sending sensor data
     * to AP.
     *
     * cameraId is the camera ID to start MIPI for.
     * outputPixelClkHz is the output pixel rate.
     * enableCapture is whether to enable MIPI capture on Easel.
     */
    virtual status_t startMipi(uint32_t cameraId, uint32_t outputPixelClkHz,
            bool enableCapture) = 0;

    /*
     * Stop MIPI for a camera.
     *
     * cameraId is the camera is ID to stop MIPI for.
     */
    virtual status_t stopMipi(uint32_t cameraId) = 0;

    /*
     * Open an HDR+ client asynchronously.
     *
     * Open an HDR+ client asynchronouly. When an HDR+ client is opened,
     * HdrPlusClientListener::onOpened() will be invoked with the created HDR+ client. If opening
     * an HDR+ client failed, HdrPlusClientListener::onOpenFailed() will be invoked with the error.
     * If this method returns an error, HdrPlusClientListener::onOpenFailed() will not be invoked.
     *
     * listener is an HDR+ client listener.
     *
     * Returns:
     *  OK:             if initiating opening an HDR+ client asynchronously was successful.
     *                  HdrPlusClientListener::onOpened() or HdrPlusClientListener::onOpenFailed()
     *                  will be invoked when opening an HDR+ client completed.
     *  ALREADY_EXISTS: if there is already a pending HDR+ client being opened.
     */
    virtual status_t openHdrPlusClientAsync(HdrPlusClientListener *listener) = 0;

    /*
     * Open an HDR+ client synchronously and block until it completes.
     *
     * listener is an HDR+ client listener.
     * client is an output parameter for created HDR+ client.
     *
     * Returns:
     *  OK:         on success.
     *  -EEXIST:    if an HDR+ client is already opened.
     *  -ENODEV:    if opening an HDR+ failed due to a serious error.
     */
    virtual status_t openHdrPlusClient(HdrPlusClientListener *listener,
            std::unique_ptr<HdrPlusClient> *client) = 0;

    /*
     * Close an HDR+ client.
     *
     * client is the HDR+ client to be closed.
     */
    virtual void closeHdrPlusClient(std::unique_ptr<HdrPlusClient> client) = 0;

private:
    // Disallow copy and assign.
    EaselManagerClient(const EaselManagerClient&) = delete;
    void operator=(const EaselManagerClient&) = delete;
};


/*
 * EaselManagerClientListener defines callbacks that will be invoked by EaselManagerClient.
 */
class EaselManagerClientListener {
public:
    virtual ~EaselManagerClientListener() {};

    // Invoked when Easel encountered a fatal error. Client should shut down Easel.
    virtual void onEaselFatalError(std::string errMsg) = 0;
};

} // namespace android

#endif // EASEL_MANAGER_CLIENT_H
