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

#ifndef HW_EMULATOR_CAMERA_CALLBACK_NOTIFIER_H
#define HW_EMULATOR_CAMERA_CALLBACK_NOTIFIER_H

/*
 * Contains declaration of a class CallbackNotifier that manages callbacks set
 * via set_callbacks, enable_msg_type, and disable_msg_type camera HAL API.
 */

#include <utils/List.h>
#include <CameraParameters.h>

using ::android::hardware::camera::common::V1_0::helper::CameraParameters;
using ::android::hardware::camera::common::V1_0::helper::Size;

namespace android {

class EmulatedCameraDevice;
class FrameProducer;

/* Manages callbacks set via set_callbacks, enable_msg_type, and disable_msg_type
 * camera HAL API.
 *
 * Objects of this class are contained in EmulatedCamera objects, and handle
 * relevant camera API callbacks.
 * Locking considerations. Apparently, it's not allowed to call callbacks
 * registered in this class, while holding a lock: recursion is quite possible,
 * which will cause a deadlock.
 */
class CallbackNotifier {
public:
    /* Constructs CallbackNotifier instance. */
    CallbackNotifier();

    /* Destructs CallbackNotifier instance. */
    ~CallbackNotifier();

    /****************************************************************************
     * Camera API
     ***************************************************************************/

public:
    /* Actual handler for camera_device_ops_t::set_callbacks callback.
     * This method is called by the containing emulated camera object when it is
     * handing the camera_device_ops_t::set_callbacks callback.
     */
    void setCallbacks(camera_notify_callback notify_cb,
                      camera_data_callback data_cb,
                      camera_data_timestamp_callback data_cb_timestamp,
                      camera_request_memory get_memory,
                      void* user);

    /* Actual handler for camera_device_ops_t::enable_msg_type callback.
     * This method is called by the containing emulated camera object when it is
     * handing the camera_device_ops_t::enable_msg_type callback.
     */
    void enableMessage(uint msg_type);

    /* Actual handler for camera_device_ops_t::disable_msg_type callback.
     * This method is called by the containing emulated camera object when it is
     * handing the camera_device_ops_t::disable_msg_type callback.
     */
    void disableMessage(uint msg_type);

    /* Actual handler for camera_device_ops_t::store_meta_data_in_buffers
     * callback. This method is called by the containing emulated camera object
     * when it is handing the camera_device_ops_t::store_meta_data_in_buffers
     * callback.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    status_t storeMetaDataInBuffers(bool enable);

    /* Enables video recording.
     * This method is called by the containing emulated camera object when it is
     * handing the camera_device_ops_t::start_recording callback.
     * Param:
     *  fps - Video frame frequency. This parameter determins when a frame
     *      received via onNextFrameAvailable call will be pushed through the
     *      callback.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    status_t enableVideoRecording(int fps);

    /* Disables video recording.
     * This method is called by the containing emulated camera object when it is
     * handing the camera_device_ops_t::stop_recording callback.
     */
    void disableVideoRecording();

    /* Releases video frame, sent to the framework.
     * This method is called by the containing emulated camera object when it is
     * handing the camera_device_ops_t::release_recording_frame callback.
     */
    void releaseRecordingFrame(const void* opaque);

    /* Send a message to the notify callback that auto-focus has completed.
     * This method is called from the containing emulated camera object when it
     * has received confirmation from the camera device that auto-focusing is
     * completed.
     */
    void autoFocusComplete();

    /* Actual handler for camera_device_ops_t::msg_type_enabled callback.
     * This method is called by the containing emulated camera object when it is
     * handing the camera_device_ops_t::msg_type_enabled callback.
     * Note: this method doesn't grab a lock while checking message status, since
     * upon exit the status would be undefined anyway. So, grab a lock before
     * calling this method if you care about persisting a defined message status.
     * Return:
     *  0 if message is disabled, or non-zero value, if message is enabled.
     */
    inline int isMessageEnabled(uint msg_type)
    {
        return mMessageEnabler & msg_type;
    }

    /* Checks id video recording is enabled.
     * This method is called by the containing emulated camera object when it is
     * handing the camera_device_ops_t::recording_enabled callback.
     * Note: this method doesn't grab a lock while checking video recordin status,
     * since upon exit the status would be undefined anyway. So, grab a lock
     * before calling this method if you care about persisting of a defined video
     * recording status.
     * Return:
     *  true if video recording is enabled, or false if it is disabled.
     */
    inline bool isVideoRecordingEnabled() const
    {
        return mVideoRecEnabled;
    }

    /****************************************************************************
     * Public API
     ***************************************************************************/

public:
    /* Resets the callback notifier. */
    void cleanupCBNotifier();

    /* Next frame is available in the camera device.
     * This is a notification callback that is invoked by the camera device when
     * a new frame is available. The captured frame is available through the
     * |camera_dev| obejct.
     * Note that most likely this method is called in context of a worker thread
     * that camera device has created for frame capturing.
     * Param:
     * timestamp - Frame's timestamp.
     * camera_dev - Camera device instance that delivered the frame.
     */
    void onNextFrameAvailable(nsecs_t timestamp,
                              EmulatedCameraDevice* camera_dev);

    /* Entry point for notifications that occur in camera device.
     * Param:
     *  err - CAMERA_ERROR_XXX error code.
     */
    void onCameraDeviceError(int err);

    /* Sets, or resets taking picture state.
     * This state control whether or not to notify the framework about compressed
     * image, shutter, and other picture related events.
     */
    void setTakingPicture(bool taking)
    {
        mTakingPicture = taking;
    }

    /* Sets JPEG quality used to compress frame during picture taking. */
    void setJpegQuality(int jpeg_quality)
    {
        mJpegQuality = jpeg_quality;
    }

    /* Sets the camera parameters that will be used to populate exif data in the
     * picture.
     */
    void setCameraParameters(CameraParameters cameraParameters)
    {
        mCameraParameters = cameraParameters;
    }

    /****************************************************************************
     * Private API
     ***************************************************************************/

protected:
    /* Checks if it's time to push new video frame.
     * Note that this method must be called while object is locked.
     * Param:
     *  timestamp - Timestamp for the new frame. */
    bool isNewVideoFrameTime(nsecs_t timestamp);

    /****************************************************************************
     * Data members
     ***************************************************************************/

protected:
    /* Locks this instance for data change. */
    Mutex                           mObjectLock;

    /*
     * Callbacks, registered in set_callbacks.
     */

    camera_notify_callback          mNotifyCB;
    camera_data_callback            mDataCB;
    camera_data_timestamp_callback  mDataCBTimestamp;
    camera_request_memory           mGetMemoryCB;
    void*                           mCBOpaque;

    /* video frame queue for the CameraHeapMemory destruction */
    List<camera_memory_t*>          mCameraMemoryTs;

    /* Timestamp when last frame has been delivered to the framework. */
    nsecs_t                         mLastFrameTimestamp;

    /* Video frequency in nanosec. */
    nsecs_t                         mFrameRefreshFreq;

    /* Message enabler. */
    uint32_t                        mMessageEnabler;

    /* JPEG quality used to compress frame during picture taking. */
    int                             mJpegQuality;

    /* Camera parameters used for EXIF data in picture */
    CameraParameters                mCameraParameters;

    /* Video recording status. */
    bool                            mVideoRecEnabled;

    /* Picture taking status. */
    bool                            mTakingPicture;
};

}; /* namespace android */

#endif  /* HW_EMULATOR_CAMERA_CALLBACK_NOTIFIER_H */
