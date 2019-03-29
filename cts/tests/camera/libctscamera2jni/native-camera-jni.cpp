/*
 * Copyright (C) 2015 The Android Open Source Project
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
#define LOG_TAG "NativeCamera"
#include <log/log.h>

#include <string>
#include <map>
#include <mutex>
#include <unistd.h>
#include <assert.h>
#include <jni.h>
#include <stdio.h>
#include <string.h>

#include <android/native_window_jni.h>

#include "camera/NdkCameraError.h"
#include "camera/NdkCameraManager.h"
#include "camera/NdkCameraDevice.h"
#include "camera/NdkCameraCaptureSession.h"
#include "media/NdkImage.h"
#include "media/NdkImageReader.h"

#define LOG_ERROR(buf, ...) sprintf(buf, __VA_ARGS__); \
                            ALOGE("%s", buf);

namespace {
    const int MAX_ERROR_STRING_LEN = 512;
    char errorString[MAX_ERROR_STRING_LEN];
}

class CameraServiceListener {
  public:
    static void onAvailable(void* obj, const char* cameraId) {
        ALOGV("Camera %s onAvailable", cameraId);
        if (obj == nullptr) {
            return;
        }
        CameraServiceListener* thiz = reinterpret_cast<CameraServiceListener*>(obj);
        std::lock_guard<std::mutex> lock(thiz->mMutex);
        thiz->mOnAvailableCount++;
        thiz->mAvailableMap[cameraId] = true;
        return;
    }

    static void onUnavailable(void* obj, const char* cameraId) {
        ALOGV("Camera %s onUnavailable", cameraId);
        if (obj == nullptr) {
            return;
        }
        CameraServiceListener* thiz = reinterpret_cast<CameraServiceListener*>(obj);
        std::lock_guard<std::mutex> lock(thiz->mMutex);
        thiz->mOnUnavailableCount++;
        thiz->mAvailableMap[cameraId] = false;
        return;
    }

    void resetCount() {
        std::lock_guard<std::mutex> lock(mMutex);
        mOnAvailableCount = 0;
        mOnUnavailableCount = 0;
        return;
    }

    int getAvailableCount() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mOnAvailableCount;
    }

    int getUnavailableCount() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mOnUnavailableCount;
    }

    bool isAvailable(const char* cameraId) {
        std::lock_guard<std::mutex> lock(mMutex);
        if (mAvailableMap.count(cameraId) == 0) {
            return false;
        }
        return mAvailableMap[cameraId];
    }

  private:
    std::mutex mMutex;
    int mOnAvailableCount = 0;
    int mOnUnavailableCount = 0;
    std::map<std::string, bool> mAvailableMap;
};

class CameraDeviceListener {
  public:
    static void onDisconnected(void* obj, ACameraDevice* device) {
        ALOGV("Camera %s is disconnected!", ACameraDevice_getId(device));
        if (obj == nullptr) {
            return;
        }
        CameraDeviceListener* thiz = reinterpret_cast<CameraDeviceListener*>(obj);
        std::lock_guard<std::mutex> lock(thiz->mMutex);
        thiz->mOnDisconnect++;
        return;
    }

    static void onError(void* obj, ACameraDevice* device, int errorCode) {
        ALOGV("Camera %s receive error %d!", ACameraDevice_getId(device), errorCode);
        if (obj == nullptr) {
            return;
        }
        CameraDeviceListener* thiz = reinterpret_cast<CameraDeviceListener*>(obj);
        std::lock_guard<std::mutex> lock(thiz->mMutex);
        thiz->mOnError++;
        thiz->mLatestError = errorCode;
        return;
    }

  private:
    std::mutex mMutex;
    int mOnDisconnect = 0;
    int mOnError = 0;
    int mLatestError = 0;
};

class CaptureSessionListener {

  public:
    static void onClosed(void* obj, ACameraCaptureSession *session) {
        // TODO: might want an API to query cameraId even session is closed?
        ALOGV("Session %p is closed!", session);
        if (obj == nullptr) {
            return;
        }
        CaptureSessionListener* thiz = reinterpret_cast<CaptureSessionListener*>(obj);
        std::lock_guard<std::mutex> lock(thiz->mMutex);
        thiz->mIsClosed = true;
        thiz->mOnClosed++; // Should never > 1
    }

    static void onReady(void* obj, ACameraCaptureSession *session) {
        ALOGV("%s", __FUNCTION__);
        if (obj == nullptr) {
            return;
        }
        CaptureSessionListener* thiz = reinterpret_cast<CaptureSessionListener*>(obj);
        std::lock_guard<std::mutex> lock(thiz->mMutex);
        ACameraDevice* device = nullptr;
        camera_status_t ret = ACameraCaptureSession_getDevice(session, &device);
        // There will be one onReady fired after session closed
        if (ret != ACAMERA_OK && !thiz->mIsClosed) {
            ALOGE("%s Getting camera device from session callback failed!",
                    __FUNCTION__);
            thiz->mInError = true;
        }
        ALOGV("Session for camera %s is ready!", ACameraDevice_getId(device));
        thiz->mIsIdle = true;
        thiz->mOnReady++;
    }

    static void onActive(void* obj, ACameraCaptureSession *session) {
        ALOGV("%s", __FUNCTION__);
        if (obj == nullptr) {
            return;
        }
        CaptureSessionListener* thiz = reinterpret_cast<CaptureSessionListener*>(obj);
        std::lock_guard<std::mutex> lock(thiz->mMutex);
        ACameraDevice* device = nullptr;
        camera_status_t ret = ACameraCaptureSession_getDevice(session, &device);
        if (ret != ACAMERA_OK) {
            ALOGE("%s Getting camera device from session callback failed!",
                    __FUNCTION__);
            thiz->mInError = true;
        }
        ALOGV("Session for camera %s is busy!", ACameraDevice_getId(device));
        thiz->mIsIdle = false;
        thiz->mOnActive;
    }

    bool isClosed() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mIsClosed;
    }

    bool isIdle() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mIsIdle;
    }

    bool isInError() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mInError;
    }

    int onClosedCount()  {
        std::lock_guard<std::mutex> lock(mMutex);
        return mOnClosed;
    }

    int onReadyCount()  {
        std::lock_guard<std::mutex> lock(mMutex);
        return mOnReady;
    }

    int onActiveCount()  {
        std::lock_guard<std::mutex> lock(mMutex);
        return mOnActive;
    }

    void reset() {
        std::lock_guard<std::mutex> lock(mMutex);
        mIsClosed = false;
        mIsIdle = true;
        mInError = false;
        mOnClosed = 0;
        mOnReady = 0;
        mOnActive = 0;
    }

  private:
    std::mutex mMutex;
    bool mIsClosed = false;
    bool mIsIdle = true;
    bool mInError = false; // should always stay false
    int mOnClosed = 0;
    int mOnReady = 0;
    int mOnActive = 0;
};


class ImageReaderListener {
  public:
    // count, acquire, validate, and delete AImage when a new image is available
    static void validateImageCb(void* obj, AImageReader* reader) {
        ALOGV("%s", __FUNCTION__);
        if (obj == nullptr) {
            return;
        }
        ImageReaderListener* thiz = reinterpret_cast<ImageReaderListener*>(obj);
        std::lock_guard<std::mutex> lock(thiz->mMutex);
        thiz->mOnImageAvailableCount++;

        AImage* img = nullptr;
        media_status_t ret = AImageReader_acquireNextImage(reader, &img);
        if (ret != AMEDIA_OK || img == nullptr) {
            ALOGE("%s: acquire image from reader %p failed! ret: %d, img %p",
                    __FUNCTION__, reader, ret, img);
            return;
        }

        // TODO: validate image content
        int32_t format = -1;
        ret = AImage_getFormat(img, &format);
        if (ret != AMEDIA_OK || format == -1) {
            ALOGE("%s: get format for image %p failed! ret: %d, format %d",
                    __FUNCTION__, img, ret, format);
        }

        // Save jpeg to SD card
        if (thiz->mDumpFilePathBase && format == AIMAGE_FORMAT_JPEG) {
            int32_t numPlanes = 0;
            ret = AImage_getNumberOfPlanes(img, &numPlanes);
            if (ret != AMEDIA_OK || numPlanes != 1) {
                ALOGE("%s: get numPlanes for image %p failed! ret: %d, numPlanes %d",
                        __FUNCTION__, img, ret, numPlanes);
                AImage_delete(img);
                return;
            }

            int32_t width = -1, height = -1;
            ret = AImage_getWidth(img, &width);
            if (ret != AMEDIA_OK || width <= 0) {
                ALOGE("%s: get width for image %p failed! ret: %d, width %d",
                        __FUNCTION__, img, ret, width);
                AImage_delete(img);
                return;
            }

            ret = AImage_getHeight(img, &height);
            if (ret != AMEDIA_OK || height <= 0) {
                ALOGE("%s: get height for image %p failed! ret: %d, height %d",
                        __FUNCTION__, img, ret, height);
                AImage_delete(img);
                return;
            }

            uint8_t* data = nullptr;
            int dataLength = 0;
            ret =  AImage_getPlaneData(img, /*planeIdx*/0, &data, &dataLength);
            if (ret != AMEDIA_OK || data == nullptr || dataLength <= 0) {
                ALOGE("%s: get jpeg data for image %p failed! ret: %d, data %p, len %d",
                        __FUNCTION__, img, ret, data, dataLength);
                AImage_delete(img);
                return;
            }

#if 0
            char dumpFilePath[512];
            sprintf(dumpFilePath, "%s/%dx%d.jpg", thiz->mDumpFilePathBase, width, height);
            ALOGI("Writing jpeg file to %s", dumpFilePath);
            FILE* file = fopen(dumpFilePath,"w+");

            if (file != nullptr) {
                fwrite(data, 1, dataLength, file);
                fflush(file);
                fclose(file);
            }
#endif
        }

        AImage_delete(img);
    }

    // count, acquire image but not delete the image
    static void acquireImageCb(void* obj, AImageReader* reader) {
        ALOGV("%s", __FUNCTION__);
        if (obj == nullptr) {
            return;
        }
        ImageReaderListener* thiz = reinterpret_cast<ImageReaderListener*>(obj);
        std::lock_guard<std::mutex> lock(thiz->mMutex);
        thiz->mOnImageAvailableCount++;

        // Acquire, but not closing.
        AImage* img = nullptr;
        media_status_t ret = AImageReader_acquireNextImage(reader, &img);
        if (ret != AMEDIA_OK || img == nullptr) {
            ALOGE("%s: acquire image from reader %p failed! ret: %d, img %p",
                    __FUNCTION__, reader, ret, img);
            return;
        }
        return;
    }

    int onImageAvailableCount() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mOnImageAvailableCount;
    }

    void setDumpFilePathBase(const char* path) {
        std::lock_guard<std::mutex> lock(mMutex);
        mDumpFilePathBase = path;
    }

  private:
    // TODO: add mReader to make sure each listener is associated to one reader?
    std::mutex mMutex;
    int mOnImageAvailableCount = 0;
    const char* mDumpFilePathBase = nullptr;
};

class StaticInfo {
  public:
    explicit StaticInfo(ACameraMetadata* chars) : mChars(chars) {}

    bool isColorOutputSupported() {
        return isCapabilitySupported(ACAMERA_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE);
    }

    bool isCapabilitySupported(acamera_metadata_enum_android_request_available_capabilities_t cap) {
        ACameraMetadata_const_entry entry;
        ACameraMetadata_getConstEntry(mChars, ACAMERA_REQUEST_AVAILABLE_CAPABILITIES, &entry);
        for (uint32_t i = 0; i < entry.count; i++) {
            if (entry.data.u8[i] == cap) {
                return true;
            }
        }
        return false;
    }
  private:
    const ACameraMetadata* mChars;
};

class PreviewTestCase {
  public:
    ~PreviewTestCase() {
        resetCamera();
        deInit();
        if (mCameraManager) {
            ACameraManager_delete(mCameraManager);
            mCameraManager = nullptr;
        }
    }

    PreviewTestCase() {
        // create is guaranteed to succeed;
        createManager();
    }

    // Free all resources except camera manager
    void resetCamera() {
        mSessionListener.reset();
        if (mSession) {
            ACameraCaptureSession_close(mSession);
            mSession = nullptr;
        }
        if (mDevice) {
            ACameraDevice_close(mDevice);
            mDevice = nullptr;
        }
        if (mImgReader) {
            AImageReader_delete(mImgReader);
            // No need to call ANativeWindow_release on imageReaderAnw
            mImgReaderAnw = nullptr;
            mImgReader = nullptr;
        }
        if (mPreviewAnw) {
            ANativeWindow_release(mPreviewAnw);
            mPreviewAnw = nullptr;
        }
        if (mOutputs) {
            ACaptureSessionOutputContainer_free(mOutputs);
            mOutputs = nullptr;
        }
        if (mPreviewOutput) {
            ACaptureSessionOutput_free(mPreviewOutput);
            mPreviewOutput = nullptr;
        }
        if (mImgReaderOutput) {
            ACaptureSessionOutput_free(mImgReaderOutput);
            mImgReaderOutput = nullptr;
        }
        if (mPreviewRequest) {
            ACaptureRequest_free(mPreviewRequest);
            mPreviewRequest = nullptr;
        }
        if (mStillRequest) {
            ACaptureRequest_free(mStillRequest);
            mStillRequest = nullptr;
        }
        if (mReqPreviewOutput) {
            ACameraOutputTarget_free(mReqPreviewOutput);
            mReqPreviewOutput = nullptr;
        }
        if (mReqImgReaderOutput) {
            ACameraOutputTarget_free(mReqImgReaderOutput);
            mReqImgReaderOutput = nullptr;
        }

        mImgReaderInited = false;
        mPreviewInited = false;
    }

    camera_status_t initWithErrorLog() {
        camera_status_t ret = ACameraManager_getCameraIdList(
                mCameraManager, &mCameraIdList);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Get camera id list failed: ret %d", ret);
            return ret;
        }
        ret = ACameraManager_registerAvailabilityCallback(mCameraManager, &mServiceCb);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Register availability callback failed: ret %d", ret);
            return ret;
        }
        mMgrInited = true;
        return ACAMERA_OK;
    }

    camera_status_t deInit () {
        if (!mMgrInited) {
            return ACAMERA_OK;
        }

        camera_status_t ret = ACameraManager_unregisterAvailabilityCallback(
                mCameraManager, &mServiceCb);
        if (ret != ACAMERA_OK) {
            ALOGE("Unregister availability callback failed: ret %d", ret);
            return ret;
        }

        if (mCameraIdList) {
            ACameraManager_deleteCameraIdList(mCameraIdList);
            mCameraIdList = nullptr;
        }
        mMgrInited = false;
        return ACAMERA_OK;
    }

    int getNumCameras() {
        if (!mMgrInited || !mCameraIdList) {
            return -1;
        }
        return mCameraIdList->numCameras;
    }

    const char* getCameraId(int idx) {
        if (!mMgrInited || !mCameraIdList || idx < 0 || idx >= mCameraIdList->numCameras) {
            return nullptr;
        }
        return mCameraIdList->cameraIds[idx];
    }

    camera_status_t openCamera(const char* cameraId) {
        if (mDevice) {
            ALOGE("Cannot open camera before closing previously open one");
            return ACAMERA_ERROR_INVALID_PARAMETER;
        }
        mCameraId = cameraId;
        return ACameraManager_openCamera(mCameraManager, cameraId, &mDeviceCb, &mDevice);
    }

    camera_status_t closeCamera() {
        camera_status_t ret = ACameraDevice_close(mDevice);
        mDevice = nullptr;
        return ret;
    }

    bool isCameraAvailable(const char* cameraId) {
        if (!mMgrInited) {
            ALOGE("Camera service listener has not been registered!");
        }
        return mServiceListener.isAvailable(cameraId);
    }

    media_status_t initImageReaderWithErrorLog(
            int32_t width, int32_t height, int32_t format, int32_t maxImages,
            AImageReader_ImageListener* listener) {
        if (mImgReader || mImgReaderAnw) {
            LOG_ERROR(errorString, "Cannot init image reader before closing existing one");
            return AMEDIA_ERROR_UNKNOWN;
        }

        media_status_t ret = AImageReader_new(
                width, height, format,
                maxImages, &mImgReader);
        if (ret != AMEDIA_OK) {
            LOG_ERROR(errorString, "Create image reader. ret %d", ret);
            return ret;
        }
        if (mImgReader == nullptr) {
            LOG_ERROR(errorString, "null image reader created");
            return AMEDIA_ERROR_UNKNOWN;
        }

        ret = AImageReader_setImageListener(mImgReader, listener);
        if (ret != AMEDIA_OK) {
            LOG_ERROR(errorString, "Set AImageReader listener failed. ret %d", ret);
            return ret;
        }

        ret = AImageReader_getWindow(mImgReader, &mImgReaderAnw);
        if (ret != AMEDIA_OK) {
            LOG_ERROR(errorString, "AImageReader_getWindow failed. ret %d", ret);
            return ret;
        }
        if (mImgReaderAnw == nullptr) {
            LOG_ERROR(errorString, "Null ANW from AImageReader!");
            return AMEDIA_ERROR_UNKNOWN;
        }
        mImgReaderInited = true;
        return AMEDIA_OK;
    }

    ANativeWindow* initPreviewAnw(JNIEnv* env, jobject jSurface) {
        if (mPreviewAnw) {
            ALOGE("Cannot init preview twice!");
            return nullptr;
        }
        mPreviewAnw =  ANativeWindow_fromSurface(env, jSurface);
        mPreviewInited = true;
        return mPreviewAnw;
    }

    camera_status_t createCaptureSessionWithLog() {
        if (mSession) {
            LOG_ERROR(errorString, "Cannot create session before closing existing one");
            return ACAMERA_ERROR_UNKNOWN;
        }

        if (!mMgrInited || (!mImgReaderInited && !mPreviewInited)) {
            LOG_ERROR(errorString, "Cannot create session. mgrInit %d readerInit %d previewInit %d",
                    mMgrInited, mImgReaderInited, mPreviewInited);
            return ACAMERA_ERROR_UNKNOWN;
        }

        camera_status_t ret = ACaptureSessionOutputContainer_create(&mOutputs);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Create capture session output container failed. ret %d", ret);
            return ret;
        }

        if (mImgReaderInited) {
            ret = ACaptureSessionOutput_create(mImgReaderAnw, &mImgReaderOutput);
            if (ret != ACAMERA_OK || mImgReaderOutput == nullptr) {
                LOG_ERROR(errorString,
                        "Sesssion image reader output create fail! ret %d output %p",
                        ret, mImgReaderOutput);
                if (ret == ACAMERA_OK) {
                    ret = ACAMERA_ERROR_UNKNOWN; // ret OK but output is null
                }
                return ret;
            }

            ret = ACaptureSessionOutputContainer_add(mOutputs, mImgReaderOutput);
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Sesssion image reader output add failed! ret %d", ret);
                return ret;
            }
        }

        if (mPreviewInited) {
            ret = ACaptureSessionOutput_create(mPreviewAnw, &mPreviewOutput);
            if (ret != ACAMERA_OK || mPreviewOutput == nullptr) {
                LOG_ERROR(errorString,
                        "Sesssion preview output create fail! ret %d output %p",
                        ret, mPreviewOutput);
                if (ret == ACAMERA_OK) {
                    ret = ACAMERA_ERROR_UNKNOWN; // ret OK but output is null
                }
                return ret;
            }

            ret = ACaptureSessionOutputContainer_add(mOutputs, mPreviewOutput);
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Sesssion preview output add failed! ret %d", ret);
                return ret;
            }
        }

        ret = ACameraDevice_createCaptureSession(
                mDevice, mOutputs, &mSessionCb, &mSession);
        if (ret != ACAMERA_OK || mSession == nullptr) {
            LOG_ERROR(errorString, "Create session for camera %s failed. ret %d session %p",
                    mCameraId, ret, mSession);
            if (ret == ACAMERA_OK) {
                ret = ACAMERA_ERROR_UNKNOWN; // ret OK but session is null
            }
            return ret;
        }

        return ACAMERA_OK;
    }

    void closeSession() {
        if (mSession != nullptr) {
            ACameraCaptureSession_close(mSession);
        }
        if (mOutputs) {
            ACaptureSessionOutputContainer_free(mOutputs);
            mOutputs = nullptr;
        }
        if (mPreviewOutput) {
            ACaptureSessionOutput_free(mPreviewOutput);
            mPreviewOutput = nullptr;
        }
        if (mImgReaderOutput) {
            ACaptureSessionOutput_free(mImgReaderOutput);
            mImgReaderOutput = nullptr;
        }
        mSession = nullptr;
    }

    camera_status_t createRequestsWithErrorLog() {
        if (mPreviewRequest || mStillRequest) {
            LOG_ERROR(errorString, "Cannot create requests before deleteing existing one");
            return ACAMERA_ERROR_UNKNOWN;
        }

        if (mDevice == nullptr || (!mPreviewInited && !mImgReaderInited)) {
            LOG_ERROR(errorString,
                    "Cannot create request. device %p previewInit %d readeInit %d",
                    mDevice, mPreviewInited, mImgReaderInited);
            return ACAMERA_ERROR_UNKNOWN;
        }

        camera_status_t ret;
        if (mPreviewInited) {
            ret = ACameraDevice_createCaptureRequest(
                    mDevice, TEMPLATE_PREVIEW, &mPreviewRequest);
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Camera %s create preview request failed. ret %d",
                        mCameraId, ret);
                return ret;
            }

            ret = ACameraOutputTarget_create(mPreviewAnw, &mReqPreviewOutput);
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString,
                        "Camera %s create request preview output target failed. ret %d",
                        mCameraId, ret);
                return ret;
            }

            ret = ACaptureRequest_addTarget(mPreviewRequest, mReqPreviewOutput);
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Camera %s add preview request output failed. ret %d",
                        mCameraId, ret);
                return ret;
            }
        } else {
            ALOGI("Preview not inited. Will not create preview request!");
        }

        if (mImgReaderInited) {
            ret = ACameraDevice_createCaptureRequest(
                    mDevice, TEMPLATE_STILL_CAPTURE, &mStillRequest);
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Camera %s create still request failed. ret %d",
                        mCameraId, ret);
                return ret;
            }

            ret = ACameraOutputTarget_create(mImgReaderAnw, &mReqImgReaderOutput);
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString,
                        "Camera %s create request reader output target failed. ret %d",
                        mCameraId, ret);
                return ret;
            }

            ret = ACaptureRequest_addTarget(mStillRequest, mReqImgReaderOutput);
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Camera %s add still request output failed. ret %d",
                        mCameraId, ret);
                return ret;
            }

            if (mPreviewInited) {
                ret = ACaptureRequest_addTarget(mStillRequest, mReqPreviewOutput);
                if (ret != ACAMERA_OK) {
                    LOG_ERROR(errorString,
                            "Camera %s add still request preview output failed. ret %d",
                            mCameraId, ret);
                    return ret;
                }
            }
        } else {
            ALOGI("AImageReader not inited. Will not create still request!");
        }

        return ACAMERA_OK;
    }

    camera_status_t startPreview() {
        if (mSession == nullptr || mPreviewRequest == nullptr) {
            ALOGE("Testcase cannot start preview: session %p, preview request %p",
                    mSession, mPreviewRequest);
            return ACAMERA_ERROR_UNKNOWN;
        }
        int previewSeqId;
        return ACameraCaptureSession_setRepeatingRequest(
                mSession, nullptr, 1, &mPreviewRequest, &previewSeqId);
    }

    camera_status_t takePicture() {
        if (mSession == nullptr || mStillRequest == nullptr) {
            ALOGE("Testcase cannot take picture: session %p, still request %p",
                    mSession, mStillRequest);
            return ACAMERA_ERROR_UNKNOWN;
        }
        int seqId;
        return ACameraCaptureSession_capture(
                mSession, nullptr, 1, &mStillRequest, &seqId);
    }

    camera_status_t resetWithErrorLog() {
        camera_status_t ret;

        closeSession();

        for (int i = 0; i < 50; i++) {
            usleep(100000); // sleep 100ms
            if (mSessionListener.isClosed()) {
                ALOGI("Session take ~%d ms to close", i*100);
                break;
            }
        }

        if (!mSessionListener.isClosed() || mSessionListener.onClosedCount() != 1) {
            LOG_ERROR(errorString,
                    "Session for camera %s close error. isClosde %d close count %d",
                    mCameraId, mSessionListener.isClosed(), mSessionListener.onClosedCount());
            return ACAMERA_ERROR_UNKNOWN;
        }
        mSessionListener.reset();

        ret = closeCamera();
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Close camera device %s failure. ret %d", mCameraId, ret);
            return ret;
        }

        resetCamera();
        return ACAMERA_OK;
    }

    CaptureSessionListener* getSessionListener() {
        return &mSessionListener;
    }

  private:
    ACameraManager* createManager() {
        if (!mCameraManager) {
            mCameraManager = ACameraManager_create();
        }
        return mCameraManager;
    }

    CameraServiceListener mServiceListener;
    ACameraManager_AvailabilityCallbacks mServiceCb {
        &mServiceListener,
        CameraServiceListener::onAvailable,
        CameraServiceListener::onUnavailable
    };
    CameraDeviceListener mDeviceListener;
    ACameraDevice_StateCallbacks mDeviceCb {
        &mDeviceListener,
        CameraDeviceListener::onDisconnected,
        CameraDeviceListener::onError
    };
    CaptureSessionListener mSessionListener;
    ACameraCaptureSession_stateCallbacks mSessionCb {
        &mSessionListener,
        CaptureSessionListener::onClosed,
        CaptureSessionListener::onReady,
        CaptureSessionListener::onActive
    };

    ACameraIdList* mCameraIdList = nullptr;
    ACameraDevice* mDevice = nullptr;
    AImageReader* mImgReader = nullptr;
    ANativeWindow* mImgReaderAnw = nullptr;
    ANativeWindow* mPreviewAnw = nullptr;
    ACameraManager* mCameraManager = nullptr;
    ACaptureSessionOutputContainer* mOutputs = nullptr;
    ACaptureSessionOutput* mPreviewOutput = nullptr;
    ACaptureSessionOutput* mImgReaderOutput = nullptr;
    ACameraCaptureSession* mSession = nullptr;
    ACaptureRequest* mPreviewRequest = nullptr;
    ACaptureRequest* mStillRequest = nullptr;
    ACameraOutputTarget* mReqPreviewOutput = nullptr;
    ACameraOutputTarget* mReqImgReaderOutput = nullptr;
    const char* mCameraId;

    bool mMgrInited = false; // cameraId, serviceListener
    bool mImgReaderInited = false;
    bool mPreviewInited = false;
};

jint throwAssertionError(JNIEnv* env, const char* message)
{
    jclass assertionClass;
    const char* className = "junit/framework/AssertionFailedError";

    assertionClass = env->FindClass(className);
    if (assertionClass == nullptr) {
        ALOGE("Native throw error: cannot find class %s", className);
        return -1;
    }
    return env->ThrowNew(assertionClass, message);
}

extern "C" jboolean
Java_android_hardware_camera2_cts_NativeCameraManagerTest_\
testCameraManagerGetAndCloseNative(
        JNIEnv* env, jclass /*clazz*/) {
    bool pass = false;
    ALOGV("%s", __FUNCTION__);
    ACameraManager* cameraManager2 = nullptr;
    ACameraManager* cameraManager3 = nullptr;
    ACameraManager* cameraManager4 = nullptr;
    camera_status_t ret = ACAMERA_OK;
    ACameraManager* cameraManager = ACameraManager_create();
    if (cameraManager == nullptr) {
        LOG_ERROR(errorString, "ACameraManager_create returns nullptr");
        goto cleanup;
    }
    ACameraManager_delete(cameraManager);
    cameraManager = nullptr;

    // Test get/close multiple instances
    cameraManager = ACameraManager_create();
    cameraManager2 = ACameraManager_create();
    if (cameraManager2 == nullptr) {
        LOG_ERROR(errorString, "ACameraManager_create 2 returns nullptr");
        goto cleanup;
    }
    ACameraManager_delete(cameraManager);
    cameraManager = nullptr;
    cameraManager3 = ACameraManager_create();
    if (cameraManager3 == nullptr) {
        LOG_ERROR(errorString, "ACameraManager_create 3 returns nullptr");
        goto cleanup;
    }
    cameraManager4 = ACameraManager_create();
        if (cameraManager4 == nullptr) {
        LOG_ERROR(errorString, "ACameraManager_create 4 returns nullptr");
        goto cleanup;
    }
    ACameraManager_delete(cameraManager3);
    ACameraManager_delete(cameraManager2);
    ACameraManager_delete(cameraManager4);

    pass = true;
cleanup:
    if (cameraManager) {
        ACameraManager_delete(cameraManager);
    }
    ALOGI("%s %s", __FUNCTION__, pass ? "pass" : "fail");
    if (!pass) {
        throwAssertionError(env, errorString);
    }
    return pass;
}

extern "C" jboolean
Java_android_hardware_camera2_cts_NativeCameraManagerTest_\
testCameraManagerGetCameraIdsNative(
        JNIEnv* env, jclass /*clazz*/) {
    ALOGV("%s", __FUNCTION__);
    bool pass = false;
    ACameraManager* mgr = ACameraManager_create();
    ACameraIdList *cameraIdList = nullptr;
    camera_status_t ret = ACameraManager_getCameraIdList(mgr, &cameraIdList);
    if (ret != ACAMERA_OK || cameraIdList == nullptr) {
        LOG_ERROR(errorString, "Get camera id list failed: ret %d, cameraIdList %p",
                ret, cameraIdList);
        goto cleanup;
    }
    ALOGI("Number of cameras: %d", cameraIdList->numCameras);
    for (int i = 0; i < cameraIdList->numCameras; i++) {
        ALOGI("Camera ID: %s", cameraIdList->cameraIds[i]);
    }
    ACameraManager_deleteCameraIdList(cameraIdList);
    cameraIdList = nullptr;

    pass = true;
cleanup:
    if (mgr) {
        ACameraManager_delete(mgr);
    }
    if (cameraIdList) {
        ACameraManager_deleteCameraIdList(cameraIdList);
    }
    ALOGI("%s %s", __FUNCTION__, pass ? "pass" : "fail");
    if (!pass) {
        throwAssertionError(env, errorString);
    }
    return pass;
}

extern "C" jboolean
Java_android_hardware_camera2_cts_NativeCameraManagerTest_\
testCameraManagerAvailabilityCallbackNative(
        JNIEnv* env, jclass /*clazz*/) {
    ALOGV("%s", __FUNCTION__);
    bool pass = false;
    ACameraManager* mgr = ACameraManager_create();
    ACameraIdList *cameraIdList = nullptr;
    camera_status_t ret = ACameraManager_getCameraIdList(mgr, &cameraIdList);
    int numCameras = cameraIdList->numCameras;
    CameraServiceListener listener;
    ACameraManager_AvailabilityCallbacks cbs {
            &listener,
            CameraServiceListener::onAvailable,
            CameraServiceListener::onUnavailable};
    ret = ACameraManager_registerAvailabilityCallback(mgr, &cbs);
    if (ret != ACAMERA_OK) {
        LOG_ERROR(errorString, "Register availability callback failed: ret %d", ret);
        goto cleanup;
    }
    sleep(1); // sleep a second to give some time for callbacks to happen

    // Should at least get onAvailable for each camera once
    if (listener.getAvailableCount() < numCameras) {
        LOG_ERROR(errorString, "Expect at least %d available callback but only got %d",
                numCameras, listener.getAvailableCount());
        goto cleanup;
    }

    ret = ACameraManager_unregisterAvailabilityCallback(mgr, &cbs);
    if (ret != ACAMERA_OK) {
        LOG_ERROR(errorString, "Unregister availability callback failed: ret %d", ret);
        goto cleanup;
    }
    pass = true;
cleanup:
    if (cameraIdList) {
        ACameraManager_deleteCameraIdList(cameraIdList);
    }
    if (mgr) {
        ACameraManager_delete(mgr);
    }
    ALOGI("%s %s", __FUNCTION__, pass ? "pass" : "failed");
    if (!pass) {
        throwAssertionError(env, errorString);
    }
    return pass;
}

extern "C" jboolean
Java_android_hardware_camera2_cts_NativeCameraManagerTest_\
testCameraManagerCharacteristicsNative(
        JNIEnv* env, jclass /*clazz*/) {
    ALOGV("%s", __FUNCTION__);
    bool pass = false;
    ACameraManager* mgr = ACameraManager_create();
    ACameraIdList *cameraIdList = nullptr;
    ACameraMetadata* chars = nullptr;
    int numCameras = 0;
    camera_status_t ret = ACameraManager_getCameraIdList(mgr, &cameraIdList);
    if (ret != ACAMERA_OK || cameraIdList == nullptr) {
        LOG_ERROR(errorString, "Get camera id list failed: ret %d, cameraIdList %p",
                ret, cameraIdList);
        goto cleanup;
    }
    numCameras = cameraIdList->numCameras;

    for (int i = 0; i < numCameras; i++) {
        ret = ACameraManager_getCameraCharacteristics(
                mgr, cameraIdList->cameraIds[i], &chars);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Get camera characteristics failed: ret %d", ret);
            goto cleanup;
        }

        int32_t numTags = 0;
        const uint32_t* tags = nullptr;
        ret = ACameraMetadata_getAllTags(chars, &numTags, &tags);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Get camera characteristics tags failed: ret %d", ret);
            goto cleanup;
        }

        for (int tid = 0; tid < numTags; tid++) {
            uint32_t tagId = tags[tid];
            ALOGV("%s capture request contains key %u", __FUNCTION__, tagId);
            uint32_t sectionId = tagId >> 16;
            if (sectionId >= ACAMERA_SECTION_COUNT && sectionId < ACAMERA_VENDOR) {
                LOG_ERROR(errorString, "Unknown tagId %u, sectionId %u", tagId, sectionId);
                goto cleanup;
            }
        }

        ACameraMetadata_const_entry entry;
        ret = ACameraMetadata_getConstEntry(chars, ACAMERA_REQUEST_AVAILABLE_CAPABILITIES, &entry);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Get const available capabilities key failed. ret %d", ret);
            goto cleanup;
        }

        // Check the entry is actually legit
        if (entry.tag != ACAMERA_REQUEST_AVAILABLE_CAPABILITIES ||
                entry.count == 0 || entry.type != ACAMERA_TYPE_BYTE || entry.data.i32 == nullptr) {
            LOG_ERROR(errorString,
                    "Bad available capabilities key: tag: %d (expected %d), count %u (expect > 0), "
                    "type %d (expected %d), data %p (expected not null)",
                    entry.tag, ACAMERA_REQUEST_AVAILABLE_CAPABILITIES, entry.count,
                    entry.type, ACAMERA_TYPE_BYTE, entry.data.i32);
            goto cleanup;
        }
        // All camera supports BC except depth only cameras
        bool supportBC = false, supportDepth = false;
        for (uint32_t i = 0; i < entry.count; i++) {
            if (entry.data.u8[i] == ACAMERA_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) {
                supportBC = true;
            }
            if (entry.data.u8[i] == ACAMERA_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
                supportDepth = true;
            }
        }
        if (!(supportBC || supportDepth)) {
            LOG_ERROR(errorString, "Error: camera device %s does not support either BC or DEPTH",
                    cameraIdList->cameraIds[i]);
            goto cleanup;
        }

        // Check get unknown value fails
        uint32_t badTag = (uint32_t) ACAMERA_VENDOR_START - 1;
        ret = ACameraMetadata_getConstEntry(chars, badTag, &entry);
        if (ret == ACAMERA_OK) {
            LOG_ERROR(errorString, "Error: get unknown tag should fail!");
            goto cleanup;
        }

        ACameraMetadata_free(chars);
        chars = nullptr;
    }

    pass = true;
cleanup:
    if (chars) {
        ACameraMetadata_free(chars);
    }
    ACameraManager_deleteCameraIdList(cameraIdList);
    ACameraManager_delete(mgr);
    ALOGI("%s %s", __FUNCTION__, pass ? "pass" : "failed");
    if (!pass) {
        throwAssertionError(env, errorString);
    }
    return pass;
}

extern "C" jboolean
Java_android_hardware_camera2_cts_NativeCameraDeviceTest_\
testCameraDeviceOpenAndCloseNative(
        JNIEnv* env, jclass /*clazz*/) {
    ALOGV("%s", __FUNCTION__);
    int numCameras = 0;
    bool pass = false;
    PreviewTestCase testCase;

    camera_status_t ret = testCase.initWithErrorLog();
    if (ret != ACAMERA_OK) {
        // Don't log error here. testcase did it
        goto cleanup;
    }

    numCameras = testCase.getNumCameras();
    if (numCameras < 0) {
        LOG_ERROR(errorString, "Testcase returned negavtive number of cameras: %d", numCameras);
        goto cleanup;
    }

    for (int i = 0; i < numCameras; i++) {
        const char* cameraId = testCase.getCameraId(i);
        if (cameraId == nullptr) {
            LOG_ERROR(errorString, "Testcase returned null camera id for camera %d", i);
            goto cleanup;
        }

        ret = testCase.openCamera(cameraId);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Open camera device %s failure. ret %d", cameraId, ret);
            goto cleanup;
        }

        usleep(100000); // sleep to give some time for callbacks to happen

        if (testCase.isCameraAvailable(cameraId)) {
            LOG_ERROR(errorString, "Camera %s should be unavailable now", cameraId);
            goto cleanup;
        }

        ret = testCase.closeCamera();
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Close camera device %s failure. ret %d", cameraId, ret);
            goto cleanup;
        }

        usleep(100000); // sleep to give some time for callbacks to happen

        if (!testCase.isCameraAvailable(cameraId)) {
            LOG_ERROR(errorString, "Camera %s should be available now", cameraId);
            goto cleanup;
        }
    }

    ret = testCase.deInit();
    if (ret != ACAMERA_OK) {
        LOG_ERROR(errorString, "Testcase deInit failed: ret %d", ret);
        goto cleanup;
    }

    pass = true;
cleanup:
    ALOGI("%s %s", __FUNCTION__, pass ? "pass" : "failed");
    if (!pass) {
        throwAssertionError(env, errorString);
    }
    return pass;
}

extern "C" jboolean
Java_android_hardware_camera2_cts_NativeCameraDeviceTest_\
testCameraDeviceCreateCaptureRequestNative(
        JNIEnv* env, jclass /*clazz*/) {
    ALOGV("%s", __FUNCTION__);
    bool pass = false;
    ACameraManager* mgr = ACameraManager_create();
    ACameraIdList* cameraIdList = nullptr;
    ACameraDevice* device = nullptr;
    ACaptureRequest* request = nullptr;
    ACameraMetadata* chars = nullptr;
    camera_status_t ret = ACameraManager_getCameraIdList(mgr, &cameraIdList);

    int numCameras = cameraIdList->numCameras;
    for (int i = 0; i < numCameras; i++) {
        CameraDeviceListener deviceListener;
        const char* cameraId = cameraIdList->cameraIds[i];
        ACameraDevice_StateCallbacks deviceCb {
            &deviceListener,
            CameraDeviceListener::onDisconnected,
            CameraDeviceListener::onError
        };
        ret = ACameraManager_openCamera(mgr, cameraId, &deviceCb, &device);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Open camera device %s failure. ret %d", cameraId, ret);
            goto cleanup;
        }

        ret = ACameraManager_getCameraCharacteristics(mgr, cameraId, &chars);
        if (ret != ACAMERA_OK || chars == nullptr) {
            LOG_ERROR(errorString, "Get camera %s characteristics failure. ret %d, chars %p",
                    cameraId, ret, chars);
            goto cleanup;
        }
        StaticInfo staticInfo(chars);

        for (int t = TEMPLATE_PREVIEW; t <= TEMPLATE_MANUAL; t++) {
            ACameraDevice_request_template templateId =
                    static_cast<ACameraDevice_request_template>(t);
            ret = ACameraDevice_createCaptureRequest(device, templateId, &request);
            if (ret == ACAMERA_ERROR_INVALID_PARAMETER) {
                // template not supported. skip
                continue;
            }

            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Create capture request failed!: ret %d", ret);
                goto cleanup;
            }

            int32_t numTags = 0;
            const uint32_t* tags = nullptr;
            ret = ACaptureRequest_getAllTags(request, &numTags, &tags);
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Get capture request tags failed: ret %d", ret);
                goto cleanup;
            }

            for (int tid = 0; tid < numTags; tid++) {
                uint32_t tagId = tags[tid];
                ALOGV("%s capture request contains key %u", __FUNCTION__, tagId);
                uint32_t sectionId = tagId >> 16;
                if (sectionId >= ACAMERA_SECTION_COUNT && sectionId < ACAMERA_VENDOR) {
                    LOG_ERROR(errorString, "Unknown tagId %u, sectionId %u", tagId, sectionId);
                    goto cleanup;
                }
            }

            // try get/set capture request fields
            ACameraMetadata_const_entry entry;
            ret = ACaptureRequest_getConstEntry(request, ACAMERA_CONTROL_AE_MODE, &entry);
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Get AE mode key failed. ret %d", ret);
                goto cleanup;
            }

            if (entry.tag != ACAMERA_CONTROL_AE_MODE || entry.type != ACAMERA_TYPE_BYTE ||\
                    entry.count != 1) {
                LOG_ERROR(errorString,
                        "Bad AE mode key. tag 0x%x (expect 0x%x), type %d (expect %d), "
                        "count %d (expect %d)",
                        entry.tag, ACAMERA_CONTROL_AE_MODE, entry.type, ACAMERA_TYPE_BYTE,
                        entry.count, 1);
                goto cleanup;
            }
            if (t == TEMPLATE_MANUAL) {
                if (entry.data.u8[0] != ACAMERA_CONTROL_AE_MODE_OFF) {
                    LOG_ERROR(errorString, "Error: MANUAL template AE mode %d (expect %d)",
                            entry.data.u8[0], ACAMERA_CONTROL_AE_MODE_OFF);
                    goto cleanup;
                }
                // try set AE_MODE_ON
                uint8_t aeMode = ACAMERA_CONTROL_AE_MODE_ON;
                ret = ACaptureRequest_setEntry_u8(
                        request, ACAMERA_CONTROL_AE_MODE, /*count*/ 1, &aeMode);
                if (ret != ACAMERA_OK) {
                    LOG_ERROR(errorString,
                            "Error: Camera %s template %d: update AE mode key fail. ret %d",
                            cameraId, t, ret);
                    goto cleanup;
                }
                ret = ACaptureRequest_getConstEntry(
                        request, ACAMERA_CONTROL_AE_MODE, &entry);
                if (ret != ACAMERA_OK) {
                    LOG_ERROR(errorString, "Get AE mode key failed. ret %d", ret);
                    goto cleanup;
                }
                if (entry.data.u8[0] != aeMode) {
                    LOG_ERROR(errorString,
                            "Error: AE mode key is not updated. expect %d but get %d",
                            aeMode, entry.data.u8[0]);
                    goto cleanup;
                }
            } else {
                if (staticInfo.isColorOutputSupported()) {
                    if (entry.data.u8[0] != ACAMERA_CONTROL_AE_MODE_ON) {
                        LOG_ERROR(errorString,
                                "Error: Template %d has wrong AE mode %d (expect %d)",
                                t, entry.data.u8[0], ACAMERA_CONTROL_AE_MODE_ON);
                        goto cleanup;
                    }
                    // try set AE_MODE_OFF
                    if (staticInfo.isCapabilitySupported(
                            ACAMERA_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                        uint8_t aeMode = ACAMERA_CONTROL_AE_MODE_OFF;
                        ret = ACaptureRequest_setEntry_u8(
                                request, ACAMERA_CONTROL_AE_MODE, /*count*/ 1, &aeMode);
                        if (ret != ACAMERA_OK) {
                            LOG_ERROR(errorString,
                                    "Error: Camera %s template %d: update AE mode key fail. ret %d",
                                    cameraId, t, ret);
                            goto cleanup;
                        }
                        ret = ACaptureRequest_getConstEntry(
                                request, ACAMERA_CONTROL_AE_MODE, &entry);
                        if (ret != ACAMERA_OK) {
                            LOG_ERROR(errorString, "Get AE mode key failed. ret %d", ret);
                            goto cleanup;
                        }
                        if (entry.data.u8[0] != aeMode) {
                            LOG_ERROR(errorString,
                                    "Error: AE mode key is not updated. expect %d but get %d",
                                    aeMode, entry.data.u8[0]);
                            goto cleanup;
                        }
                    }
                }
            }
            ACaptureRequest_free(request);
            request = nullptr;
        }

        ACameraMetadata_free(chars);
        chars = nullptr;
        ACameraDevice_close(device);
        device = nullptr;
    }

    pass = true;
cleanup:
    if (cameraIdList) {
        ACameraManager_deleteCameraIdList(cameraIdList);
    }
    if (request) {
        ACaptureRequest_free(request);
    }
    if (chars) {
        ACameraMetadata_free(chars);
    }
    if (device) {
        ACameraDevice_close(device);
    }
    if (mgr) {
        ACameraManager_delete(mgr);
    }
    ALOGI("%s %s", __FUNCTION__, pass ? "pass" : "failed");
    if (!pass) {
        throwAssertionError(env, errorString);
    }
    return pass;
}

extern "C" jboolean
Java_android_hardware_camera2_cts_NativeCameraDeviceTest_\
testCameraDeviceSessionOpenAndCloseNative(
        JNIEnv* env, jclass /*clazz*/, jobject jPreviewSurface) {
    ALOGV("%s", __FUNCTION__);
    int numCameras = 0;
    bool pass = false;
    PreviewTestCase testCase;

    camera_status_t ret = testCase.initWithErrorLog();
    if (ret != ACAMERA_OK) {
        // Don't log error here. testcase did it
        goto cleanup;
    }

    numCameras = testCase.getNumCameras();
    if (numCameras < 0) {
        LOG_ERROR(errorString, "Testcase returned negavtive number of cameras: %d", numCameras);
        goto cleanup;
    }

    for (int i = 0; i < numCameras; i++) {
        const char* cameraId = testCase.getCameraId(i);
        if (cameraId == nullptr) {
            LOG_ERROR(errorString, "Testcase returned null camera id for camera %d", i);
            goto cleanup;
        }

        ret = testCase.openCamera(cameraId);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Open camera device %s failure. ret %d", cameraId, ret);
            goto cleanup;
        }

        usleep(100000); // sleep to give some time for callbacks to happen

        if (testCase.isCameraAvailable(cameraId)) {
            LOG_ERROR(errorString, "Camera %s should be unavailable now", cameraId);
            goto cleanup;
        }

        ANativeWindow* previewAnw = testCase.initPreviewAnw(env, jPreviewSurface);
        if (previewAnw == nullptr) {
            LOG_ERROR(errorString, "Null ANW from preview surface!");
            goto cleanup;
        }

        CaptureSessionListener* sessionListener = testCase.getSessionListener();
        if (sessionListener == nullptr) {
            LOG_ERROR(errorString, "Session listener camera %s is null", cameraId);
            goto cleanup;
        }

        // Try open/close session multiple times
        for (int j = 0; j < 5; j++) {
            ret = testCase.createCaptureSessionWithLog();
            if (ret != ACAMERA_OK) {
                // Don't log error here. testcase did it
                goto cleanup;
            }

            usleep(100000); // sleep to give some time for callbacks to happen

            if (!sessionListener->isIdle()) {
                LOG_ERROR(errorString, "Session for camera %s should be idle right after creation",
                        cameraId);
                goto cleanup;
            }

            testCase.closeSession();

            usleep(100000); // sleep to give some time for callbacks to happen
            if (!sessionListener->isClosed() || sessionListener->onClosedCount() != 1) {
                LOG_ERROR(errorString,
                        "Session for camera %s close error. isClosde %d close count %d",
                        cameraId, sessionListener->isClosed(), sessionListener->onClosedCount());
                goto cleanup;
            }
            sessionListener->reset();
        }

        // Try open/close really fast
        ret = testCase.createCaptureSessionWithLog();
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Create session for camera %s failed. ret %d",
                    cameraId, ret);
            goto cleanup;
        }
        testCase.closeSession();
        usleep(100000); // sleep to give some time for callbacks to happen
        if (!sessionListener->isClosed() || sessionListener->onClosedCount() != 1) {
            LOG_ERROR(errorString,
                    "Session for camera %s close error. isClosde %d close count %d",
                    cameraId, sessionListener->isClosed(), sessionListener->onClosedCount());
            goto cleanup;
        }

        ret = testCase.resetWithErrorLog();
        if (ret != ACAMERA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        usleep(100000); // sleep to give some time for callbacks to happen

        if (!testCase.isCameraAvailable(cameraId)) {
            LOG_ERROR(errorString, "Camera %s should be available now", cameraId);
            goto cleanup;
        }
    }

    ret = testCase.deInit();
    if (ret != ACAMERA_OK) {
        LOG_ERROR(errorString, "Testcase deInit failed: ret %d", ret);
        goto cleanup;
    }

    pass = true;
cleanup:
    ALOGI("%s %s", __FUNCTION__, pass ? "pass" : "failed");
    if (!pass) {
        throwAssertionError(env, errorString);
    }
    return pass;
}

extern "C" jboolean
Java_android_hardware_camera2_cts_NativeCameraDeviceTest_\
testCameraDeviceSimplePreviewNative(
        JNIEnv* env, jclass /*clazz*/, jobject jPreviewSurface) {
    ALOGV("%s", __FUNCTION__);
    int numCameras = 0;
    bool pass = false;
    PreviewTestCase testCase;

    camera_status_t ret = testCase.initWithErrorLog();
    if (ret != ACAMERA_OK) {
        // Don't log error here. testcase did it
        goto cleanup;
    }

    numCameras = testCase.getNumCameras();
    if (numCameras < 0) {
        LOG_ERROR(errorString, "Testcase returned negavtive number of cameras: %d", numCameras);
        goto cleanup;
    }

    for (int i = 0; i < numCameras; i++) {
        const char* cameraId = testCase.getCameraId(i);
        if (cameraId == nullptr) {
            LOG_ERROR(errorString, "Testcase returned null camera id for camera %d", i);
            goto cleanup;
        }

        ret = testCase.openCamera(cameraId);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Open camera device %s failure. ret %d", cameraId, ret);
            goto cleanup;
        }

        usleep(100000); // sleep to give some time for callbacks to happen

        if (testCase.isCameraAvailable(cameraId)) {
            LOG_ERROR(errorString, "Camera %s should be unavailable now", cameraId);
            goto cleanup;
        }

        ANativeWindow* previewAnw = testCase.initPreviewAnw(env, jPreviewSurface);
        if (previewAnw == nullptr) {
            LOG_ERROR(errorString, "Null ANW from preview surface!");
            goto cleanup;
        }

        ret = testCase.createCaptureSessionWithLog();
        if (ret != ACAMERA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        ret = testCase.createRequestsWithErrorLog();
        if (ret != ACAMERA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        ret = testCase.startPreview();
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Start preview failed!");
            goto cleanup;
        }

        sleep(3);

        ret = testCase.resetWithErrorLog();
        if (ret != ACAMERA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        usleep(100000); // sleep to give some time for callbacks to happen

        if (!testCase.isCameraAvailable(cameraId)) {
            LOG_ERROR(errorString, "Camera %s should be available now", cameraId);
            goto cleanup;
        }
    }

    ret = testCase.deInit();
    if (ret != ACAMERA_OK) {
        LOG_ERROR(errorString, "Testcase deInit failed: ret %d", ret);
        goto cleanup;
    }

    pass = true;
cleanup:
    ALOGI("%s %s", __FUNCTION__, pass ? "pass" : "failed");
    if (!pass) {
        throwAssertionError(env, errorString);
    }
    return pass;
}

bool nativeImageReaderTestBase(
        JNIEnv* env, jstring jOutPath, AImageReader_ImageCallback cb) {
    const int NUM_TEST_IMAGES = 10;
    const int TEST_WIDTH  = 640;
    const int TEST_HEIGHT = 480;
    media_status_t mediaRet = AMEDIA_ERROR_UNKNOWN;
    int numCameras = 0;
    bool pass = false;
    PreviewTestCase testCase;

    const char* outPath = (jOutPath == nullptr) ? nullptr :
            env->GetStringUTFChars(jOutPath, nullptr);
    if (outPath != nullptr) {
        ALOGI("%s: out path is %s", __FUNCTION__, outPath);
    }

    camera_status_t ret = testCase.initWithErrorLog();
    if (ret != ACAMERA_OK) {
        // Don't log error here. testcase did it
        goto cleanup;
    }

    numCameras = testCase.getNumCameras();
    if (numCameras < 0) {
        LOG_ERROR(errorString, "Testcase returned negavtive number of cameras: %d", numCameras);
        goto cleanup;
    }

    for (int i = 0; i < numCameras; i++) {
        const char* cameraId = testCase.getCameraId(i);
        if (cameraId == nullptr) {
            LOG_ERROR(errorString, "Testcase returned null camera id for camera %d", i);
            goto cleanup;
        }

        ret = testCase.openCamera(cameraId);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Open camera device %s failure. ret %d", cameraId, ret);
            goto cleanup;
        }

        usleep(100000); // sleep to give some time for callbacks to happen

        if (testCase.isCameraAvailable(cameraId)) {
            LOG_ERROR(errorString, "Camera %s should be unavailable now", cameraId);
            goto cleanup;
        }

        ImageReaderListener readerListener;
        AImageReader_ImageListener readerCb { &readerListener, cb };
        readerListener.setDumpFilePathBase(outPath);

        mediaRet = testCase.initImageReaderWithErrorLog(
                TEST_WIDTH, TEST_HEIGHT, AIMAGE_FORMAT_JPEG, NUM_TEST_IMAGES,
                &readerCb);
        if (mediaRet != AMEDIA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        ret = testCase.createCaptureSessionWithLog();
        if (ret != ACAMERA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        ret = testCase.createRequestsWithErrorLog();
        if (ret != ACAMERA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        // Do some still capture
        for (int capture = 0; capture < NUM_TEST_IMAGES; capture++) {
            ret = testCase.takePicture();
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Camera %s capture(%d) failed. ret %d",
                        cameraId, capture, ret);
                goto cleanup;
            }
        }

        // wait until all capture finished
        for (int i = 0; i < 50; i++) {
            usleep(100000); // sleep 100ms
            if (readerListener.onImageAvailableCount() == NUM_TEST_IMAGES) {
                ALOGI("Session take ~%d ms to capture %d images",
                        i*100, NUM_TEST_IMAGES);
                break;
            }
        }

        if (readerListener.onImageAvailableCount() != NUM_TEST_IMAGES) {
            LOG_ERROR(errorString, "Camera %s timeout capturing %d images. Got %d",
                    cameraId, NUM_TEST_IMAGES, readerListener.onImageAvailableCount());
            goto cleanup;
        }

        ret = testCase.resetWithErrorLog();
        if (ret != ACAMERA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        usleep(100000); // sleep to give some time for callbacks to happen

        if (!testCase.isCameraAvailable(cameraId)) {
            LOG_ERROR(errorString, "Camera %s should be available now", cameraId);
            goto cleanup;
        }
    }

    ret = testCase.deInit();
    if (ret != ACAMERA_OK) {
        LOG_ERROR(errorString, "Testcase deInit failed: ret %d", ret);
        goto cleanup;
    }

    pass = true;

cleanup:
    if (outPath != nullptr) {
        env->ReleaseStringUTFChars(jOutPath, outPath);
    }
    ALOGI("%s %s", __FUNCTION__, pass ? "pass" : "failed");
    if (!pass) {
        throwAssertionError(env, errorString);
    }
    return pass;
}

extern "C" jboolean
Java_android_hardware_camera2_cts_NativeImageReaderTest_\
testJpegNative(
        JNIEnv* env, jclass /*clazz*/, jstring jOutPath) {
    ALOGV("%s", __FUNCTION__);
    return nativeImageReaderTestBase(env, jOutPath, ImageReaderListener::validateImageCb);
}


extern "C" jboolean
Java_android_hardware_camera2_cts_NativeImageReaderTest_\
testImageReaderCloseAcquiredImagesNative(
        JNIEnv* env, jclass /*clazz*/) {
    ALOGV("%s", __FUNCTION__);
    return nativeImageReaderTestBase(env, nullptr, ImageReaderListener::acquireImageCb);
}


extern "C" jboolean
Java_android_hardware_camera2_cts_NativeStillCaptureTest_\
testStillCaptureNative(
        JNIEnv* env, jclass /*clazz*/, jstring jOutPath, jobject jPreviewSurface) {
    ALOGV("%s", __FUNCTION__);
    const int NUM_TEST_IMAGES = 10;
    const int TEST_WIDTH  = 640;
    const int TEST_HEIGHT = 480;
    media_status_t mediaRet = AMEDIA_ERROR_UNKNOWN;
    int numCameras = 0;
    bool pass = false;
    PreviewTestCase testCase;

    const char* outPath = env->GetStringUTFChars(jOutPath, nullptr);
    ALOGI("%s: out path is %s", __FUNCTION__, outPath);

    camera_status_t ret = testCase.initWithErrorLog();
    if (ret != ACAMERA_OK) {
        // Don't log error here. testcase did it
        goto cleanup;
    }

    numCameras = testCase.getNumCameras();
    if (numCameras < 0) {
        LOG_ERROR(errorString, "Testcase returned negavtive number of cameras: %d", numCameras);
        goto cleanup;
    }

    for (int i = 0; i < numCameras; i++) {
        const char* cameraId = testCase.getCameraId(i);
        if (cameraId == nullptr) {
            LOG_ERROR(errorString, "Testcase returned null camera id for camera %d", i);
            goto cleanup;
        }

        ret = testCase.openCamera(cameraId);
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Open camera device %s failure. ret %d", cameraId, ret);
            goto cleanup;
        }

        usleep(100000); // sleep to give some time for callbacks to happen

        if (testCase.isCameraAvailable(cameraId)) {
            LOG_ERROR(errorString, "Camera %s should be unavailable now", cameraId);
            goto cleanup;
        }

        ImageReaderListener readerListener;
        AImageReader_ImageListener readerCb {
            &readerListener,
            ImageReaderListener::validateImageCb
        };
        readerListener.setDumpFilePathBase(outPath);
        mediaRet = testCase.initImageReaderWithErrorLog(
                TEST_WIDTH, TEST_HEIGHT, AIMAGE_FORMAT_JPEG, NUM_TEST_IMAGES,
                &readerCb);
        if (mediaRet != AMEDIA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        ANativeWindow* previewAnw = testCase.initPreviewAnw(env, jPreviewSurface);
        if (previewAnw == nullptr) {
            LOG_ERROR(errorString, "Null ANW from preview surface!");
            goto cleanup;
        }

        ret = testCase.createCaptureSessionWithLog();
        if (ret != ACAMERA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        ret = testCase.createRequestsWithErrorLog();
        if (ret != ACAMERA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        ret = testCase.startPreview();
        if (ret != ACAMERA_OK) {
            LOG_ERROR(errorString, "Start preview failed!");
            goto cleanup;
        }

        // Let preview run some time
        sleep(3);

        // Do some still capture
        for (int capture = 0; capture < NUM_TEST_IMAGES; capture++) {
            ret = testCase.takePicture();
            if (ret != ACAMERA_OK) {
                LOG_ERROR(errorString, "Camera %s capture(%d) failed. ret %d",
                        cameraId, capture, ret);
                goto cleanup;
            }
        }

        // wait until all capture finished
        for (int i = 0; i < 50; i++) {
            usleep(100000); // sleep 100ms
            if (readerListener.onImageAvailableCount() == NUM_TEST_IMAGES) {
                ALOGI("Session take ~%d ms to capture %d images",
                        i*100, NUM_TEST_IMAGES);
                break;
            }
        }

        if (readerListener.onImageAvailableCount() != NUM_TEST_IMAGES) {
            LOG_ERROR(errorString, "Camera %s timeout capturing %d images. Got %d",
                    cameraId, NUM_TEST_IMAGES, readerListener.onImageAvailableCount());
            goto cleanup;
        }

        ret = testCase.resetWithErrorLog();
        if (ret != ACAMERA_OK) {
            // Don't log error here. testcase did it
            goto cleanup;
        }

        usleep(100000); // sleep to give some time for callbacks to happen

        if (!testCase.isCameraAvailable(cameraId)) {
            LOG_ERROR(errorString, "Camera %s should be available now", cameraId);
            goto cleanup;
        }
    }

    ret = testCase.deInit();
    if (ret != ACAMERA_OK) {
        LOG_ERROR(errorString, "Testcase deInit failed: ret %d", ret);
        goto cleanup;
    }

    pass = true;
cleanup:
    env->ReleaseStringUTFChars(jOutPath, outPath);
    ALOGI("%s %s", __FUNCTION__, pass ? "pass" : "failed");
    if (!pass) {
        throwAssertionError(env, errorString);
    }
    return pass;
}

