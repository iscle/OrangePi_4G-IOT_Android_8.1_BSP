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

/*
 * Contains implementation of a class CallbackNotifier that manages callbacks set
 * via set_callbacks, enable_msg_type, and disable_msg_type camera HAL API.
 */

#define LOG_NDEBUG 0
#define LOG_TAG "EmulatedCamera_CallbackNotifier"
#include <cutils/log.h>
#include <MetadataBufferType.h>
#include "EmulatedCameraDevice.h"
#undef min
#undef max
#include "CallbackNotifier.h"
#include "Exif.h"
#include "JpegCompressor.h"
#include "Thumbnail.h"

namespace android {

/* String representation of camera messages. */
static const char* lCameraMessages[] =
{
    "CAMERA_MSG_ERROR",
    "CAMERA_MSG_SHUTTER",
    "CAMERA_MSG_FOCUS",
    "CAMERA_MSG_ZOOM",
    "CAMERA_MSG_PREVIEW_FRAME",
    "CAMERA_MSG_VIDEO_FRAME",
    "CAMERA_MSG_POSTVIEW_FRAME",
    "CAMERA_MSG_RAW_IMAGE",
    "CAMERA_MSG_COMPRESSED_IMAGE",
    "CAMERA_MSG_RAW_IMAGE_NOTIFY",
    "CAMERA_MSG_PREVIEW_METADATA"
};
static const int lCameraMessagesNum = sizeof(lCameraMessages) / sizeof(char*);

/* Builds an array of strings for the given set of messages.
 * Param:
 *  msg - Messages to get strings for,
 *  strings - Array where to save strings
 *  max - Maximum number of entries in the array.
 * Return:
 *  Number of strings saved into the 'strings' array.
 */
static int GetMessageStrings(uint32_t msg, const char** strings, int max)
{
    int index = 0;
    int out = 0;
    while (msg != 0 && out < max && index < lCameraMessagesNum) {
        while ((msg & 0x1) == 0 && index < lCameraMessagesNum) {
            msg >>= 1;
            index++;
        }
        if ((msg & 0x1) != 0 && index < lCameraMessagesNum) {
            strings[out] = lCameraMessages[index];
            out++;
            msg >>= 1;
            index++;
        }
    }

    return out;
}

/* Logs messages, enabled by the mask. */
static void PrintMessages(uint32_t msg)
{
    const char* strs[lCameraMessagesNum];
    const int translated = GetMessageStrings(msg, strs, lCameraMessagesNum);
    for (int n = 0; n < translated; n++) {
        ALOGV("    %s", strs[n]);
    }
}

CallbackNotifier::CallbackNotifier()
    : mNotifyCB(NULL),
      mDataCB(NULL),
      mDataCBTimestamp(NULL),
      mGetMemoryCB(NULL),
      mCBOpaque(NULL),
      mLastFrameTimestamp(0),
      mFrameRefreshFreq(0),
      mMessageEnabler(0),
      mJpegQuality(90),
      mVideoRecEnabled(false),
      mTakingPicture(false)
{
}

CallbackNotifier::~CallbackNotifier()
{
}

/****************************************************************************
 * Camera API
 ***************************************************************************/

void CallbackNotifier::setCallbacks(camera_notify_callback notify_cb,
                                    camera_data_callback data_cb,
                                    camera_data_timestamp_callback data_cb_timestamp,
                                    camera_request_memory get_memory,
                                    void* user)
{
    ALOGV("%s: %p, %p, %p, %p (%p)",
         __FUNCTION__, notify_cb, data_cb, data_cb_timestamp, get_memory, user);

    Mutex::Autolock locker(&mObjectLock);
    mNotifyCB = notify_cb;
    mDataCB = data_cb;
    mDataCBTimestamp = data_cb_timestamp;
    mGetMemoryCB = get_memory;
    mCBOpaque = user;
}

void CallbackNotifier::enableMessage(uint msg_type)
{
    ALOGV("%s: msg_type = 0x%x", __FUNCTION__, msg_type);
    PrintMessages(msg_type);

    Mutex::Autolock locker(&mObjectLock);
    mMessageEnabler |= msg_type;
    ALOGV("**** Currently enabled messages:");
    PrintMessages(mMessageEnabler);
}

void CallbackNotifier::disableMessage(uint msg_type)
{
    ALOGV("%s: msg_type = 0x%x", __FUNCTION__, msg_type);
    PrintMessages(msg_type);

    Mutex::Autolock locker(&mObjectLock);
    mMessageEnabler &= ~msg_type;
    ALOGV("**** Currently enabled messages:");
    PrintMessages(mMessageEnabler);
}

status_t CallbackNotifier::enableVideoRecording(int fps)
{
    ALOGV("%s: FPS = %d", __FUNCTION__, fps);

    Mutex::Autolock locker(&mObjectLock);
    mVideoRecEnabled = true;
    mLastFrameTimestamp = 0;
    mFrameRefreshFreq = 1000000000LL / fps;

    return NO_ERROR;
}

void CallbackNotifier::disableVideoRecording()
{
    ALOGV("%s:", __FUNCTION__);

    Mutex::Autolock locker(&mObjectLock);
    mVideoRecEnabled = false;
    mLastFrameTimestamp = 0;
    mFrameRefreshFreq = 0;
}

void CallbackNotifier::releaseRecordingFrame(const void* opaque)
{
    List<camera_memory_t*>::iterator it = mCameraMemoryTs.begin();
    for( ; it != mCameraMemoryTs.end(); ++it ) {
        if ( (*it)->data == opaque ) {
            (*it)->release( *it );
            mCameraMemoryTs.erase(it);
            break;
        }
    }
}

void CallbackNotifier::autoFocusComplete() {
    // Even though we don't support auto-focus we are expected to send a fake
    // success message according to the documentation.
    // https://developer.android.com/reference/android/hardware/Camera.AutoFocusCallback.html
    mNotifyCB(CAMERA_MSG_FOCUS, true, 0, mCBOpaque);
}

status_t CallbackNotifier::storeMetaDataInBuffers(bool enable)
{
    // Return error if metadata is request, otherwise silently agree.
    return enable ? INVALID_OPERATION : NO_ERROR;
}

/****************************************************************************
 * Public API
 ***************************************************************************/

void CallbackNotifier::cleanupCBNotifier()
{
    Mutex::Autolock locker(&mObjectLock);
    mMessageEnabler = 0;
    mNotifyCB = NULL;
    mDataCB = NULL;
    mDataCBTimestamp = NULL;
    mGetMemoryCB = NULL;
    mCBOpaque = NULL;
    mLastFrameTimestamp = 0;
    mFrameRefreshFreq = 0;
    mJpegQuality = 90;
    mVideoRecEnabled = false;
    mTakingPicture = false;
}

void CallbackNotifier::onNextFrameAvailable(nsecs_t timestamp,
                                            EmulatedCameraDevice* camera_dev)
{
    if (isMessageEnabled(CAMERA_MSG_VIDEO_FRAME) && isVideoRecordingEnabled() &&
            isNewVideoFrameTime(timestamp)) {
        // This is the path for video frames, the format used here is not
        // exposed to external users so it can be whatever the camera and the
        // encoder can agree upon. The emulator system images use software
        // encoders that expect a YUV420 format but the camera parameter
        // constants cannot represent this. The closest we have is YV12 which is
        // YVU420. So we produce YV12 frames so that we can serve those through
        // the preview callback below and then we convert from YV12 to YUV420
        // here. This is a pretty cheap conversion in most cases since we have
        // to copy the frame here anyway. In the best (and most common) cases
        // the conversion is just copying the U and V parts of the frame in
        // different order. A slightly more expensive case is when the YV12
        // frame has padding to ensure that rows are aligned to 16-byte
        // boundaries. The YUV420 format expected by the encoders do not have
        // this alignment so it has to be removed. This way the encoder gets the
        // format it expects and the preview callback (or data callback) below
        // gets the format that is configured in camera parameters.
        const size_t frameSize = camera_dev->getVideoFrameBufferSize();
        camera_memory_t* cam_buff = mGetMemoryCB(-1, frameSize, 1, mCBOpaque);
        if (NULL != cam_buff && NULL != cam_buff->data) {
            camera_dev->getCurrentFrame(cam_buff->data, V4L2_PIX_FMT_YUV420);
            mDataCBTimestamp(timestamp, CAMERA_MSG_VIDEO_FRAME,
                               cam_buff, 0, mCBOpaque);
            mCameraMemoryTs.push_back( cam_buff );
        } else {
            ALOGE("%s: Memory failure in CAMERA_MSG_VIDEO_FRAME", __FUNCTION__);
        }
    }

    if (isMessageEnabled(CAMERA_MSG_PREVIEW_FRAME)) {
        camera_memory_t* cam_buff =
            mGetMemoryCB(-1, camera_dev->getFrameBufferSize(), 1, mCBOpaque);
        if (NULL != cam_buff && NULL != cam_buff->data) {
            camera_dev->getCurrentFrame(cam_buff->data,
                                        camera_dev->getOriginalPixelFormat());
            mDataCB(CAMERA_MSG_PREVIEW_FRAME, cam_buff, 0, NULL, mCBOpaque);
            cam_buff->release(cam_buff);
        } else {
            ALOGE("%s: Memory failure in CAMERA_MSG_PREVIEW_FRAME", __FUNCTION__);
        }
    }

    if (mTakingPicture) {
        /* This happens just once. */
        mTakingPicture = false;
        /* The sequence of callbacks during picture taking is:
         *  - CAMERA_MSG_SHUTTER
         *  - CAMERA_MSG_RAW_IMAGE_NOTIFY
         *  - CAMERA_MSG_COMPRESSED_IMAGE
         */
        if (isMessageEnabled(CAMERA_MSG_SHUTTER)) {
            mNotifyCB(CAMERA_MSG_SHUTTER, 0, 0, mCBOpaque);
        }
        if (isMessageEnabled(CAMERA_MSG_RAW_IMAGE_NOTIFY)) {
            mNotifyCB(CAMERA_MSG_RAW_IMAGE_NOTIFY, 0, 0, mCBOpaque);
        }
        if (isMessageEnabled(CAMERA_MSG_COMPRESSED_IMAGE)) {
            // Create EXIF data from the camera parameters, this includes things
            // like EXIF default fields, a timestamp and GPS information.
            ExifData* exifData = createExifData(mCameraParameters);

            // Hold the frame lock while accessing the current frame to prevent
            // concurrent modifications. Then create our JPEG from that frame.
            EmulatedCameraDevice::FrameLock lock(*camera_dev);
            const void* frame = camera_dev->getCurrentFrame();

            // Create a thumbnail and place the pointer and size in the EXIF
            // data structure. This transfers ownership to the EXIF data and
            // the memory will be deallocated in the freeExifData call below.
            int width = camera_dev->getFrameWidth();
            int height = camera_dev->getFrameHeight();
            int thumbWidth = mCameraParameters.getInt(
                    CameraParameters::KEY_JPEG_THUMBNAIL_WIDTH);
            int thumbHeight = mCameraParameters.getInt(
                    CameraParameters::KEY_JPEG_THUMBNAIL_HEIGHT);
            if (thumbWidth > 0 && thumbHeight > 0) {
                if (!createThumbnail(static_cast<const unsigned char*>(frame),
                                     width, height, thumbWidth, thumbHeight,
                                     mJpegQuality, exifData)) {
                    // Not really a fatal error, we'll just keep going
                    ALOGE("%s: Failed to create thumbnail for image",
                          __FUNCTION__);
                }
            }

            /* Compress the frame to JPEG. Note that when taking pictures, we
             * have requested camera device to provide us with NV21 frames. */
            NV21JpegCompressor compressor;
            status_t res = compressor.compressRawImage(frame, width, height,
                                                       mJpegQuality, exifData);
            if (res == NO_ERROR) {
                camera_memory_t* jpeg_buff =
                    mGetMemoryCB(-1, compressor.getCompressedSize(), 1, mCBOpaque);
                if (NULL != jpeg_buff && NULL != jpeg_buff->data) {
                    compressor.getCompressedImage(jpeg_buff->data);
                    mDataCB(CAMERA_MSG_COMPRESSED_IMAGE, jpeg_buff, 0, NULL, mCBOpaque);
                    jpeg_buff->release(jpeg_buff);
                } else {
                    ALOGE("%s: Memory failure in CAMERA_MSG_VIDEO_FRAME", __FUNCTION__);
                }
            } else {
                ALOGE("%s: Compression failure in CAMERA_MSG_VIDEO_FRAME", __FUNCTION__);
            }
            // The EXIF data has been consumed, free it
            freeExifData(exifData);
        }
    }
}

void CallbackNotifier::onCameraDeviceError(int err)
{
    if (isMessageEnabled(CAMERA_MSG_ERROR) && mNotifyCB != NULL) {
        mNotifyCB(CAMERA_MSG_ERROR, err, 0, mCBOpaque);
    }
}

/****************************************************************************
 * Private API
 ***************************************************************************/

bool CallbackNotifier::isNewVideoFrameTime(nsecs_t timestamp)
{
    Mutex::Autolock locker(&mObjectLock);
    if ((timestamp - mLastFrameTimestamp) >= mFrameRefreshFreq) {
        mLastFrameTimestamp = timestamp;
        return true;
    }
    return false;
}

}; /* namespace android */
