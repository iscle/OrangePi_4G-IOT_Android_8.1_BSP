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

#ifndef HW_EMULATOR_CAMERA_EMULATED_CAMERA_DEVICE_H
#define HW_EMULATOR_CAMERA_EMULATED_CAMERA_DEVICE_H

/*
 * Contains declaration of an abstract class EmulatedCameraDevice that defines
 * functionality expected from an emulated physical camera device:
 *  - Obtaining and setting camera device parameters
 *  - Capturing frames
 *  - Streaming video
 *  - etc.
 */

#include <utils/threads.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>
#include "EmulatedCameraCommon.h"
#include "Converters.h"
#include "WorkerThread.h"

#undef min
#undef max
#include <vector>

namespace android {

class EmulatedCamera;

/* Encapsulates an abstract class EmulatedCameraDevice that defines
 * functionality expected from an emulated physical camera device:
 *  - Obtaining and setting camera device parameters
 *  - Capturing frames
 *  - Streaming video
 *  - etc.
 */
class EmulatedCameraDevice {
public:
    /* Constructs EmulatedCameraDevice instance.
     * Param:
     *  camera_hal - Emulated camera that implements the camera HAL API, and
     *      manages (contains) this object.
     */
    explicit EmulatedCameraDevice(EmulatedCamera* camera_hal);

    /* Destructs EmulatedCameraDevice instance. */
    virtual ~EmulatedCameraDevice();

    /***************************************************************************
     * Emulated camera device abstract interface
     **************************************************************************/

public:
    /* Connects to the camera device.
     * This method must be called on an initialized instance of this class.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    virtual status_t connectDevice() = 0;

    /* Disconnects from the camera device.
     * Return:
     *  NO_ERROR on success, or an appropriate error status. If this method is
     *  called for already disconnected, or uninitialized instance of this class,
     *  a successful status must be returned from this method. If this method is
     *  called for an instance that is in the "started" state, this method must
     *  return a failure.
     */
    virtual status_t disconnectDevice() = 0;

    /* Starts the camera device.
     * This method tells the camera device to start capturing frames of the given
     * dimensions for the given pixel format. Note that this method doesn't start
     * the delivery of the captured frames to the emulated camera. Call
     * startDeliveringFrames method to start delivering frames. This method must
     * be called on a connected instance of this class. If it is called on a
     * disconnected instance, this method must return a failure.
     * Param:
     *  width, height - Frame dimensions to use when capturing video frames.
     *  pix_fmt - Pixel format to use when capturing video frames.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    virtual status_t startDevice(int width, int height, uint32_t pix_fmt) = 0;

    /* Stops the camera device.
     * This method tells the camera device to stop capturing frames. Note that
     * this method doesn't stop delivering frames to the emulated camera. Always
     * call stopDeliveringFrames prior to calling this method.
     * Return:
     *  NO_ERROR on success, or an appropriate error status. If this method is
     *  called for an object that is not capturing frames, or is disconnected,
     *  or is uninitialized, a successful status must be returned from this
     *  method.
     */
    virtual status_t stopDevice() = 0;

    /***************************************************************************
     * Emulated camera device public API
     **************************************************************************/

public:
    /* Initializes EmulatedCameraDevice instance.
     * Derived classes should override this method in order to cache static
     * properties of the physical device (list of supported pixel formats, frame
     * sizes, etc.) If this method is called on an already initialized instance,
     * it must return a successful status.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    virtual status_t Initialize();

    /* Initializes the white balance modes parameters.
     * The parameters are passed by each individual derived camera API to
     * represent that different camera manufacturers may have different
     * preferences on the white balance parameters. Green channel in the RGB
     * color space is fixed to keep the luminance to be reasonably constant.
     *
     * Param:
     * mode the text describing the current white balance mode
     * r_scale the scale factor for the R channel in RGB space
     * b_scale the scale factor for the B channel in RGB space.
     */
    void initializeWhiteBalanceModes(const char* mode,
                                     const float r_scale,
                                     const float b_scale);

    /* Starts delivering frames captured from the camera device.
     * This method will start the worker thread that would be pulling frames from
     * the camera device, and will deliver the pulled frames back to the emulated
     * camera via onNextFrameAvailable callback. This method must be called on a
     * connected instance of this class with a started camera device. If it is
     * called on a disconnected instance, or camera device has not been started,
     * this method must return a failure.
     * Param:
     *  one_burst - Controls how many frames should be delivered. If this
     *      parameter is 'true', only one captured frame will be delivered to the
     *      emulated camera. If this parameter is 'false', frames will keep
     *      coming until stopDeliveringFrames method is called. Typically, this
     *      parameter is set to 'true' only in order to obtain a single frame
     *      that will be used as a "picture" in takePicture method of the
     *      emulated camera.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    virtual status_t startDeliveringFrames(bool one_burst);

    /* Stops delivering frames captured from the camera device.
     * This method will stop the worker thread started by startDeliveringFrames.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    virtual status_t stopDeliveringFrames();

    /* Set the preview frame rate.
     * Indicates the rate at which the camera should provide preview frames in
     * frames per second. */
    status_t setPreviewFrameRate(int framesPerSecond);

    /* Sets the exposure compensation for the camera device.
     */
    void setExposureCompensation(const float ev);

    /* Sets the white balance mode for the device.
     */
    void setWhiteBalanceMode(const char* mode);

    /* Gets current framebuffer in a selected format
     * This method must be called on a connected instance of this class with a
     * started camera device. If it is called on a disconnected instance, or
     * camera device has not been started, this method must return a failure.
     * Note that this method should be called only after at least one frame has
     * been captured and delivered. Otherwise it will return garbage in the
     * preview frame buffer. Typically, this method should be called from
     * onNextFrameAvailable callback. The method can perform some basic pixel
     * format conversion for the most efficient conversions. If a conversion
     * is not supported the method will fail. Note that this does NOT require
     * that the current frame be locked using a FrameLock object.
     *
     * Param:
     *  buffer - Buffer, large enough to contain the entire frame.
     *  pixelFormat - The pixel format to convert to, use
     *                getOriginalPixelFormat() to get the configured pixel
     *                format (if using this no conversion will be needed)
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    virtual status_t getCurrentFrame(void* buffer, uint32_t pixelFormat);

    /* Gets current framebuffer, converted into preview frame format.
     * This method must be called on a connected instance of this class with a
     * started camera device. If it is called on a disconnected instance, or
     * camera device has not been started, this method must return a failure.
     * Note that this method should be called only after at least one frame has
     * been captured and delivered. Otherwise it will return garbage in the
     * preview frame buffer. Typically, this method should be called from
     * onNextFrameAvailable callback. Note that this does NOT require that the
     * current frame be locked using a FrameLock object.
     * Param:
     *  buffer - Buffer, large enough to contain the entire preview frame.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    virtual status_t getCurrentPreviewFrame(void* buffer);

    /* Gets a pointer to the current frame buffer in its raw format.
     * This method must be called on a connected instance of this class with a
     * started camera device. If it is called on a disconnected instance, or
     * camera device has not been started, this method must return NULL.
     * This method should only be called when the frame lock is held through
     * a FrameLock object. Otherwise the contents of the frame might change
     * unexpectedly or its memory could be deallocated leading to a crash.
     * Return:
     *  A pointer to the current frame buffer on success, NULL otherwise.
     */
    virtual const void* getCurrentFrame();

    class FrameLock {
    public:
        FrameLock(EmulatedCameraDevice& cameraDevice);
        ~FrameLock();
    private:
        EmulatedCameraDevice& mCameraDevice;
    };

    /* Gets width of the frame obtained from the physical device.
     * Return:
     *  Width of the frame obtained from the physical device. Note that value
     *  returned from this method is valid only in case if camera device has been
     *  started.
     */
    inline int getFrameWidth() const
    {
        ALOGE_IF(!isStarted(), "%s: Device is not started", __FUNCTION__);
        return mFrameWidth;
    }

    /* Gets height of the frame obtained from the physical device.
     * Return:
     *  Height of the frame obtained from the physical device. Note that value
     *  returned from this method is valid only in case if camera device has been
     *  started.
     */
    inline int getFrameHeight() const
    {
        ALOGE_IF(!isStarted(), "%s: Device is not started", __FUNCTION__);
        return mFrameHeight;
    }

    /* Gets byte size of the current frame buffer.
     * Return:
     *  Byte size of the frame buffer. Note that value returned from this method
     *  is valid only in case if camera device has been started.
     */
    inline size_t getFrameBufferSize() const
    {
        ALOGE_IF(!isStarted(), "%s: Device is not started", __FUNCTION__);
        return mFrameBufferSize;
    }

    /* Get number of bytes required to store current video frame buffer. Note
     * that this can be different from getFrameBufferSize depending on the pixel
     * format and resolution. The video frames use a pixel format that is
     * suitable for the encoding pipeline and this may have different alignment
     * requirements than the pixel format used for regular frames.
     */
    inline size_t getVideoFrameBufferSize() const
    {
        ALOGE_IF(!isStarted(), "%s: Device is not started", __FUNCTION__);
        // Currently the video format is always YUV 420 without any kind of
        // alignment. So each pixel uses 12 bits, and then we divide by 8 to get
        // the size in bytes. If additional pixel formats are supported this
        // should be updated to take the selected video format into
        // consideration.
        return (mFrameWidth * mFrameHeight * 12) / 8;
    }

    /* Gets number of pixels in the current frame buffer.
     * Return:
     *  Number of pixels in the frame buffer. Note that value returned from this
     *  method is valid only in case if camera device has been started.
     */
    inline int getPixelNum() const
    {
        ALOGE_IF(!isStarted(), "%s: Device is not started", __FUNCTION__);
        return mTotalPixels;
    }

    /* Gets pixel format of the frame that camera device streams to this class.
     * Throughout camera framework, there are three different forms of pixel
     * format representation:
     *  - Original format, as reported by the actual camera device. Values for
     *    this format are declared in bionic/libc/kernel/common/linux/videodev2.h
     *  - String representation as defined in CameraParameters::PIXEL_FORMAT_XXX
     *    strings in frameworks/base/include/camera/CameraParameters.h
     *  - HAL_PIXEL_FORMAT_XXX format, as defined in system/core/include/system/graphics.h
     * Since emulated camera device gets its data from the actual device, it gets
     * pixel format in the original form. And that's the pixel format
     * representation that will be returned from this method. HAL components will
     * need to translate value returned from this method to the appropriate form.
     * This method must be called only on started instance of this class, since
     * it's applicable only when camera device is ready to stream frames.
     * Param:
     *  pix_fmt - Upon success contains the original pixel format.
     * Return:
     *  Current framebuffer's pixel format. Note that value returned from this
     *  method is valid only in case if camera device has been started.
     */
    inline uint32_t getOriginalPixelFormat() const
    {
        ALOGE_IF(!isStarted(), "%s: Device is not started", __FUNCTION__);
        return mPixelFormat;
    }

    /*
     * State checkers.
     */

    inline bool isInitialized() const {
        return mState != ECDS_CONSTRUCTED;
    }
    inline bool isConnected() const {
        /* Instance is connected when its status is either"connected", or
         * "started". */
        return mState == ECDS_CONNECTED || mState == ECDS_STARTED;
    }
    inline bool isStarted() const {
        return mState == ECDS_STARTED;
    }

    /* Enable auto-focus for the camera, this is only possible between calls to
     * startPreview and stopPreview, i.e. when preview frames are being
     * delivered. This will eventually trigger a callback to the camera HAL
     * saying auto-focus completed.
     */
    virtual status_t setAutoFocus();

    /* Cancel auto-focus if it's enabled.
     */
    virtual status_t cancelAutoFocus();

    /* Request an asynchronous camera restart with new image parameters. The
     * restart will be performed on the same thread that delivers frames,
     * ensuring that all callbacks are done from the same thread.
     * Return
     *  false if the thread request cannot be honored because no thread is
     *        running or some other error occured.
     */
    bool requestRestart(int width, int height, uint32_t pixelFormat,
                        bool takingPicture, bool oneBurst);

    /****************************************************************************
     * Emulated camera device private API
     ***************************************************************************/
protected:
    /* Performs common validation and calculation of startDevice parameters.
     * Param:
     *  width, height, pix_fmt - Parameters passed to the startDevice method.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    virtual status_t commonStartDevice(int width, int height, uint32_t pix_fmt);

    /* Performs common cleanup on stopDevice.
     * This method will undo what commonStartDevice had done.
     */
    virtual void commonStopDevice();

    /** Computes a luminance value after taking the exposure compensation.
     * value into account.
     *
     * Param:
     * inputY - The input luminance value.
     * Return:
     * The luminance value after adjusting the exposure compensation.
     */
    inline uint8_t changeExposure(const uint8_t& inputY) const {
        return static_cast<uint8_t>(clamp(static_cast<float>(inputY) *
                                    mExposureCompensation));
    }

    /** Computes the pixel value in YUV space after adjusting to the current
     * white balance mode.
     */
    void changeWhiteBalance(uint8_t& y, uint8_t& u, uint8_t& v) const;

    /* Check if there is a pending auto-focus trigger and send a notification
     * if there is. This should be called from the worker thread loop if the
     * camera device wishes to use the default behavior of immediately sending
     * an auto-focus completion event on request. Otherwise the device should
     * implement its own auto-focus behavior. */
    void checkAutoFocusTrigger();

    /* Implementation for getCurrentFrame that includes pixel format conversion
     * if needed. This allows subclasses to easily use this method instead of
     * having to reimplement the conversion all over.
     */
    status_t getCurrentFrameImpl(const uint8_t* source, uint8_t* dest,
                                 uint32_t pixelFormat) const;

    /****************************************************************************
     * Worker thread management.
     * Typicaly when emulated camera device starts capturing frames from the
     * actual device, it does that in a worker thread created in StartCapturing,
     * and terminated in StopCapturing. Since this is such a typical scenario,
     * it makes sence to encapsulate worker thread management in the base class
     * for all emulated camera devices.
     ***************************************************************************/

protected:
    /* Starts the worker thread.
     * Typically, the worker thread is started from the startDeliveringFrames
     * method of this class.
     * Param:
     *  one_burst - Controls how many times thread loop should run. If this
     *      parameter is 'true', thread routine will run only once If this
     *      parameter is 'false', thread routine will run until
     *      stopWorkerThreads method is called. See startDeliveringFrames for
     *      more info.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    virtual status_t startWorkerThread(bool one_burst);

    /* Stop the worker thread.
     * Note that this method will always wait for the worker thread to
     * terminate. Typically, the worker thread is stopped from the
     * stopDeliveringFrames method of this class.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    virtual status_t stopWorkerThread();

    /* Produce a camera frame and place it in buffer. The buffer is one of
     * the two buffers provided to mFrameProducer during construction along with
     * a pointer to this method. The method is expected to know what size frames
     * it provided to the producer thread. Returning false indicates an
     * unrecoverable error that will stop the frame production thread. */
    virtual bool produceFrame(void* buffer) = 0;

    /* Get the primary buffer to use when constructing the FrameProducer. */
    virtual void* getPrimaryBuffer() {
        return mFrameBuffers[0].data();
    }

    /* Get the seconary buffer to use when constructing the FrameProducer. */
    virtual void* getSecondaryBuffer() {
        return mFrameBuffers[1].data();
    }

    /* A class that encaspulates the asynchronous behavior of a camera. This
     * includes asynchronous production (through another thread), frame delivery
     * as well as asynchronous state changes that have to be synchronized with
     * frame production and delivery but can't be blocking the camera HAL. */
    class CameraThread : public WorkerThread {
    public:
        typedef bool (*ProduceFrameFunc)(void* opaque, void* destinationBuffer);
        CameraThread(EmulatedCameraDevice* cameraDevice,
                     ProduceFrameFunc producer,
                     void* producerOpaque);

        /* Access the primary buffer of the frame producer, this is the frame
         * that is currently not being written to. The buffer will only have
         * valid contents if hasFrame() returns true. Note that accessing this
         * without first having created a Lock can lead to contents changing
         * without notice. */
        const void* getPrimaryBuffer() const;

        /* Lock and unlock the primary buffer */
        void lockPrimaryBuffer();
        void unlockPrimaryBuffer();

        void requestRestart(int width, int height, uint32_t pixelFormat,
                            bool takingPicture, bool oneBurst);

    private:
        bool checkRestartRequest();
        bool waitForFrameOrTimeout(nsecs_t timeout);
        bool inWorkerThread() override;

        status_t onThreadStart() override;
        void onThreadExit() override;

        /* A class with a thread that will call a function at a specified
         * interval to produce frames. This is done in a double-buffered fashion
         * to make sure that one of the frames can be delivered without risk of
         * overwriting its contents. Access to the primary buffer, the one NOT
         * being drawn to, should be protected with the lock methods provided or
         * the guarantee of not overwriting the contents does not hold.
         */
        class FrameProducer : public WorkerThread {
        public:
            FrameProducer(EmulatedCameraDevice* cameraDevice,
                          ProduceFrameFunc producer, void* opaque,
                          void* primaryBuffer, void* secondaryBuffer);

            /* Indicates if the producer has produced at least one frame. */
            bool hasFrame() const;

            const void* getPrimaryBuffer() const;

            void lockPrimaryBuffer();
            void unlockPrimaryBuffer();

        protected:
            bool inWorkerThread() override;

            ProduceFrameFunc mProducer;
            void* mOpaque;
            void* mPrimaryBuffer;
            void* mSecondaryBuffer;
            nsecs_t mLastFrame;
            mutable Mutex mBufferMutex;
            std::atomic<bool> mHasFrame;
        };

        nsecs_t mCurFrameTimestamp;
        /* Worker thread that will produce frames for the camera thread */
        sp<FrameProducer> mFrameProducer;
        ProduceFrameFunc mProducerFunc;
        void* mProducerOpaque;
        Mutex mRequestMutex;
        int mRestartWidth;
        int mRestartHeight;
        uint32_t mRestartPixelFormat;
        bool mRestartOneBurst;
        bool mRestartTakingPicture;
        bool mRestartRequested;
    };

    /****************************************************************************
     * Data members
     ***************************************************************************/

protected:
    /* Locks this instance for parameters, state, etc. change. */
    Mutex                       mObjectLock;

    /* A camera thread that is used in frame production, delivery and handling
     * of asynchronous restarts. Internally the process of generating and
     * delivering frames is split up into two threads. This way frames can
     * always be delivered on time even if they cannot be produced fast enough
     * to keep up with the expected frame rate. It also increases performance on
     * multi-core systems. If the producer cannot keep up the last frame will
     * simply be delivered again. */
    sp<CameraThread>          mCameraThread;

    /* Emulated camera object containing this instance. */
    EmulatedCamera*             mCameraHAL;

    /* Framebuffers containing the frame being drawn to and the frame being
     * delivered. This is used by the double buffering producer thread and
     * the consumer thread will copy frames from one of these buffers to
     * mCurrentFrame to avoid being stalled by frame production. */
    std::vector<uint8_t>        mFrameBuffers[2];

    /*
     * Framebuffer properties.
     */

    /* Byte size of the framebuffer. */
    size_t                      mFrameBufferSize;

    /* Original pixel format (one of the V4L2_PIX_FMT_XXX values, as defined in
     * bionic/libc/kernel/common/linux/videodev2.h */
    uint32_t                    mPixelFormat;

    /* Frame width */
    int                         mFrameWidth;

    /* Frame height */
    int                         mFrameHeight;

    /* The number of frames per second that the camera should deliver */
    int                         mFramesPerSecond;

    /* Defines byte distance between the start of each Y row */
    int                         mYStride;

    /* Defines byte distance between the start of each U/V row. For formats with
     * separate U and V planes this is the distance between rows in each plane.
     * For formats with interleaved U and V components this is the distance
     * between rows in the interleaved plane, meaning that it's the stride over
     * the combined U and V components. */
    int                         mUVStride;

    /* Total number of pixels */
    int                         mTotalPixels;

    /* Exposure compensation value */
    float                       mExposureCompensation;

    float*                      mWhiteBalanceScale;

    DefaultKeyedVector<String8, float*>      mSupportedWhiteBalanceScale;

    /* Defines possible states of the emulated camera device object.
     */
    enum EmulatedCameraDeviceState {
        /* Object has been constructed. */
        ECDS_CONSTRUCTED,
        /* Object has been initialized. */
        ECDS_INITIALIZED,
        /* Object has been connected to the physical device. */
        ECDS_CONNECTED,
        /* Camera device has been started. */
        ECDS_STARTED,
    };

    /* Object state. */
    EmulatedCameraDeviceState   mState;

private:
    /* Lock the current frame so that it can safely be accessed using
     * getCurrentFrame. Prefer using a FrameLock object on the stack instead
     * to ensure that the lock is always unlocked properly.
     */
    void lockCurrentFrame();
    /* Unlock the current frame after locking it. Prefer using a FrameLock
     * object instead.
     */
    void unlockCurrentFrame();

    static bool staticProduceFrame(void* opaque, void* buffer) {
        auto cameraDevice = reinterpret_cast<EmulatedCameraDevice*>(opaque);
        return cameraDevice->produceFrame(buffer);
    }

    /* A flag indicating if an auto-focus completion event should be sent the
     * next time the worker thread runs. This implies that auto-focus completion
     * event can only be delivered while preview frames are being delivered.
     * This is also a requirement specified in the documentation where a request
     * to perform auto-focusing is only valid between calls to startPreview and
     * stopPreview.
     * https://developer.android.com/reference/android/hardware/Camera.html#autoFocus(android.hardware.Camera.AutoFocusCallback)
     */
    std::atomic<bool> mTriggerAutoFocus;
};

}; /* namespace android */

#endif  /* HW_EMULATOR_CAMERA_EMULATED_CAMERA_DEVICE_H */
