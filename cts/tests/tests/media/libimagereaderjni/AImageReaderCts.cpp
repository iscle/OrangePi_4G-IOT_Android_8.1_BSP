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

#define LOG_TAG "AImageReaderCts"
//#define LOG_NDEBUG 0

#include <jni.h>
#include <stdint.h>
#include <unistd.h>

#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#include <android/log.h>
#include <android/native_window_jni.h>
#include <camera/NdkCameraError.h>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraDevice.h>
#include <camera/NdkCameraCaptureSession.h>
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>

//#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
//#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

static constexpr int kDummyFenceFd = -1;
static constexpr int kCaptureWaitUs = 100 * 1000;
static constexpr int kCaptureWaitRetry = 10;
static constexpr int kTestImageWidth = 640;
static constexpr int kTestImageHeight = 480;
static constexpr int kTestImageFormat = AIMAGE_FORMAT_YUV_420_888;

struct FormatUsageCombination {
    uint64_t format;
    uint64_t usage;
};

static constexpr FormatUsageCombination supportedFormatUsage[] = {
    {AIMAGE_FORMAT_YUV_420_888, AHARDWAREBUFFER_USAGE_CPU_READ_RARELY},
    {AIMAGE_FORMAT_YUV_420_888, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN},
    {AIMAGE_FORMAT_JPEG, AHARDWAREBUFFER_USAGE_CPU_READ_RARELY},
    {AIMAGE_FORMAT_JPEG, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN},
    {AIMAGE_FORMAT_RGBA_8888, AHARDWAREBUFFER_USAGE_CPU_READ_RARELY},
    {AIMAGE_FORMAT_RGBA_8888, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN},
    {AIMAGE_FORMAT_RGBA_8888, AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE},
    {AIMAGE_FORMAT_RGBA_8888, AHARDWAREBUFFER_USAGE_VIDEO_ENCODE},
};

class CameraHelper {
   public:
    CameraHelper(ANativeWindow* imgReaderAnw) : mImgReaderAnw(imgReaderAnw) {}
    ~CameraHelper() { closeCamera(); }

    int initCamera() {
        if (mImgReaderAnw == nullptr) {
            ALOGE("Cannot initialize camera before image reader get initialized.");
            return -1;
        }
        int ret;

        mCameraManager = ACameraManager_create();
        if (mCameraManager == nullptr) {
            ALOGE("Failed to create ACameraManager.");
            return -1;
        }

        ret = ACameraManager_getCameraIdList(mCameraManager, &mCameraIdList);
        if (ret != AMEDIA_OK) {
            ALOGE("Failed to get cameraIdList: ret=%d", ret);
            return ret;
        }
        if (mCameraIdList->numCameras < 1) {
            ALOGW("Device has no camera on board.");
            return 0;
        }

        // We always use the first camera.
        mCameraId = mCameraIdList->cameraIds[0];
        if (mCameraId == nullptr) {
            ALOGE("Failed to get cameraId.");
            return -1;
        }

        ret = ACameraManager_openCamera(mCameraManager, mCameraId, &mDeviceCb, &mDevice);
        if (ret != AMEDIA_OK || mDevice == nullptr) {
            ALOGE("Failed to open camera, ret=%d, mDevice=%p.", ret, mDevice);
            return -1;
        }

        ret = ACameraManager_getCameraCharacteristics(mCameraManager, mCameraId, &mCameraMetadata);
        if (ret != ACAMERA_OK || mCameraMetadata == nullptr) {
            ALOGE("Get camera %s characteristics failure. ret %d, metadata %p", mCameraId, ret,
                  mCameraMetadata);
            return -1;
        }

        if (!isCapabilitySupported(ACAMERA_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
            ALOGW("Camera does not support BACKWARD_COMPATIBLE.");
            return 0;
        }

        // Create capture session
        ret = ACaptureSessionOutputContainer_create(&mOutputs);
        if (ret != AMEDIA_OK) {
            ALOGE("ACaptureSessionOutputContainer_create failed, ret=%d", ret);
            return ret;
        }
        ret = ACaptureSessionOutput_create(mImgReaderAnw, &mImgReaderOutput);
        if (ret != AMEDIA_OK) {
            ALOGE("ACaptureSessionOutput_create failed, ret=%d", ret);
            return ret;
        }
        ret = ACaptureSessionOutputContainer_add(mOutputs, mImgReaderOutput);
        if (ret != AMEDIA_OK) {
            ALOGE("ACaptureSessionOutputContainer_add failed, ret=%d", ret);
            return ret;
        }
        ret = ACameraDevice_createCaptureSession(mDevice, mOutputs, &mSessionCb, &mSession);
        if (ret != AMEDIA_OK) {
            ALOGE("ACameraDevice_createCaptureSession failed, ret=%d", ret);
            return ret;
        }

        // Create capture request
        ret = ACameraDevice_createCaptureRequest(mDevice, TEMPLATE_STILL_CAPTURE, &mStillRequest);
        if (ret != AMEDIA_OK) {
            ALOGE("ACameraDevice_createCaptureRequest failed, ret=%d", ret);
            return ret;
        }
        ret = ACameraOutputTarget_create(mImgReaderAnw, &mReqImgReaderOutput);
        if (ret != AMEDIA_OK) {
            ALOGE("ACameraOutputTarget_create failed, ret=%d", ret);
            return ret;
        }
        ret = ACaptureRequest_addTarget(mStillRequest, mReqImgReaderOutput);
        if (ret != AMEDIA_OK) {
            ALOGE("ACaptureRequest_addTarget failed, ret=%d", ret);
            return ret;
        }

        mIsCameraReady = true;
        return 0;
    }

    bool isCapabilitySupported(acamera_metadata_enum_android_request_available_capabilities_t cap) {
        ACameraMetadata_const_entry entry;
        ACameraMetadata_getConstEntry(
                mCameraMetadata, ACAMERA_REQUEST_AVAILABLE_CAPABILITIES, &entry);
        for (uint32_t i = 0; i < entry.count; i++) {
            if (entry.data.u8[i] == cap) {
                return true;
            }
        }
        return false;
    }

    bool isCameraReady() { return mIsCameraReady; }

    void closeCamera() {
        // Destroy capture request
        if (mReqImgReaderOutput) {
            ACameraOutputTarget_free(mReqImgReaderOutput);
            mReqImgReaderOutput = nullptr;
        }
        if (mStillRequest) {
            ACaptureRequest_free(mStillRequest);
            mStillRequest = nullptr;
        }
        // Destroy capture session
        if (mSession != nullptr) {
            ACameraCaptureSession_close(mSession);
            mSession = nullptr;
        }
        if (mImgReaderOutput) {
            ACaptureSessionOutput_free(mImgReaderOutput);
            mImgReaderOutput = nullptr;
        }
        if (mOutputs) {
            ACaptureSessionOutputContainer_free(mOutputs);
            mOutputs = nullptr;
        }
        // Destroy camera device
        if (mDevice) {
            ACameraDevice_close(mDevice);
            mDevice = nullptr;
        }
        if (mCameraMetadata) {
          ACameraMetadata_free(mCameraMetadata);
          mCameraMetadata = nullptr;
        }
        // Destroy camera manager
        if (mCameraIdList) {
            ACameraManager_deleteCameraIdList(mCameraIdList);
            mCameraIdList = nullptr;
        }
        if (mCameraManager) {
            ACameraManager_delete(mCameraManager);
            mCameraManager = nullptr;
        }
        mIsCameraReady = false;
    }

    int takePicture() {
        int seqId;
        return ACameraCaptureSession_capture(mSession, nullptr, 1, &mStillRequest, &seqId);
    }

    static void onDeviceDisconnected(void* /*obj*/, ACameraDevice* /*device*/) {}

    static void onDeviceError(void* /*obj*/, ACameraDevice* /*device*/, int /*errorCode*/) {}

    static void onSessionClosed(void* /*obj*/, ACameraCaptureSession* /*session*/) {}

    static void onSessionReady(void* /*obj*/, ACameraCaptureSession* /*session*/) {}

    static void onSessionActive(void* /*obj*/, ACameraCaptureSession* /*session*/) {}

   private:
    ACameraDevice_StateCallbacks mDeviceCb{this, onDeviceDisconnected,
                                           onDeviceError};
    ACameraCaptureSession_stateCallbacks mSessionCb{
        this, onSessionClosed, onSessionReady, onSessionActive};

    ANativeWindow* mImgReaderAnw = nullptr;  // not owned by us.

    // Camera manager
    ACameraManager* mCameraManager = nullptr;
    ACameraIdList* mCameraIdList = nullptr;
    // Camera device
    ACameraMetadata* mCameraMetadata = nullptr;
    ACameraDevice* mDevice = nullptr;
    // Capture session
    ACaptureSessionOutputContainer* mOutputs = nullptr;
    ACaptureSessionOutput* mImgReaderOutput = nullptr;
    ACameraCaptureSession* mSession = nullptr;
    // Capture request
    ACaptureRequest* mStillRequest = nullptr;
    ACameraOutputTarget* mReqImgReaderOutput = nullptr;

    bool mIsCameraReady = false;
    const char* mCameraId;
};

class ImageReaderTestCase {
   public:
    ImageReaderTestCase(int32_t width,
                        int32_t height,
                        int32_t format,
                        uint64_t usage,
                        int32_t maxImages,
                        bool async)
        : mWidth(width),
          mHeight(height),
          mFormat(format),
          mUsage(usage),
          mMaxImages(maxImages),
          mAsync(async) {}

    ~ImageReaderTestCase() {
        if (mImgReaderAnw) {
            AImageReader_delete(mImgReader);
            // No need to call ANativeWindow_release on imageReaderAnw
        }
    }

    int initImageReader() {
        if (mImgReader != nullptr || mImgReaderAnw != nullptr) {
            ALOGE("Cannot re-initalize image reader, mImgReader=%p, mImgReaderAnw=%p", mImgReader,
                  mImgReaderAnw);
            return -1;
        }

        media_status_t ret = AImageReader_newWithUsage(
                mWidth, mHeight, mFormat, mUsage, mMaxImages, &mImgReader);
        if (ret != AMEDIA_OK || mImgReader == nullptr) {
            ALOGE("Failed to create new AImageReader, ret=%d, mImgReader=%p", ret, mImgReader);
            return -1;
        }

        ret = AImageReader_setImageListener(mImgReader, &mReaderAvailableCb);
        if (ret != AMEDIA_OK) {
            ALOGE("Failed to set image available listener, ret=%d.", ret);
            return ret;
        }

        ret = AImageReader_setBufferRemovedListener(mImgReader, &mReaderDetachedCb);
        if (ret != AMEDIA_OK) {
            ALOGE("Failed to set buffer detaching listener, ret=%d.", ret);
            return ret;
        }

        ret = AImageReader_getWindow(mImgReader, &mImgReaderAnw);
        if (ret != AMEDIA_OK || mImgReaderAnw == nullptr) {
            ALOGE("Failed to get ANativeWindow from AImageReader, ret=%d, mImgReaderAnw=%p.", ret,
                  mImgReaderAnw);
            return -1;
        }

        return 0;
    }

    ANativeWindow* getNativeWindow() { return mImgReaderAnw; }

    int getAcquiredImageCount() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mAcquiredImageCount;
    }

    void HandleImageAvailable(AImageReader* reader) {
        std::lock_guard<std::mutex> lock(mMutex);

        AImage* outImage = nullptr;
        media_status_t ret;

        // Make sure AImage will be deleted automatically when it goes out of
        // scope.
        auto imageDeleter = [this](AImage* img) {
            if (mAsync) {
                AImage_deleteAsync(img, kDummyFenceFd);
            } else {
                AImage_delete(img);
            }
        };
        std::unique_ptr<AImage, decltype(imageDeleter)> img(nullptr, imageDeleter);

        if (mAsync) {
            int outFenceFd = 0;
            // Verity that outFenceFd's value will be changed by
            // AImageReader_acquireNextImageAsync.
            ret = AImageReader_acquireNextImageAsync(reader, &outImage, &outFenceFd);
            if (ret != AMEDIA_OK || outImage == nullptr || outFenceFd != kDummyFenceFd) {
                ALOGE("Failed to acquire image, ret=%d, outIamge=%p, outFenceFd=%d.", ret, outImage,
                      outFenceFd);
                return;
            }
            img.reset(outImage);
        } else {
            ret = AImageReader_acquireNextImage(reader, &outImage);
            if (ret != AMEDIA_OK || outImage == nullptr) {
                ALOGE("Failed to acquire image, ret=%d, outIamge=%p.", ret, outImage);
                return;
            }
            img.reset(outImage);
        }

        AHardwareBuffer* outBuffer = nullptr;
        ret = AImage_getHardwareBuffer(img.get(), &outBuffer);
        if (ret != AMEDIA_OK || outBuffer == nullptr) {
            ALOGE("Faild to get hardware buffer, ret=%d, outBuffer=%p.", ret, outBuffer);
            return;
        }

        // No need to release AHardwareBuffer, since we don't acquire additional
        // reference to it.
        AHardwareBuffer_Desc outDesc;
        AHardwareBuffer_describe(outBuffer, &outDesc);
        int32_t imageWidth = 0;
        int32_t imageHeight = 0;
        int32_t bufferWidth = static_cast<int32_t>(outDesc.width);
        int32_t bufferHeight = static_cast<int32_t>(outDesc.height);

        AImage_getWidth(outImage, &imageWidth);
        AImage_getHeight(outImage, &imageHeight);
        if (imageWidth != mWidth || imageHeight != mHeight) {
            ALOGE("Mismatched output image dimension: expected=%dx%d, actual=%dx%d", mWidth,
                  mHeight, imageWidth, imageHeight);
            return;
        }

        if (mFormat == AIMAGE_FORMAT_RGBA_8888 ||
            mFormat == AIMAGE_FORMAT_RGBX_8888 ||
            mFormat == AIMAGE_FORMAT_RGB_888 ||
            mFormat == AIMAGE_FORMAT_RGB_565 ||
            mFormat == AIMAGE_FORMAT_RGBA_FP16 ||
            mFormat == AIMAGE_FORMAT_YUV_420_888) {
            // Check output buffer dimension for certain formats. Don't do this for blob based
            // formats.
            if (bufferWidth != mWidth || bufferHeight != mHeight) {
                ALOGE("Mismatched output buffer dimension: expected=%dx%d, actual=%dx%d", mWidth,
                      mHeight, bufferWidth, bufferHeight);
                return;
            }
        }

        if ((outDesc.usage & mUsage) != mUsage) {
            ALOGE("Mismatched output buffer usage: actual (%" PRIu64 "), expected (%" PRIu64 ")",
                  outDesc.usage, mUsage);
            return;
        }

        uint8_t* data = nullptr;
        int dataLength = 0;
        ret = AImage_getPlaneData(img.get(), 0, &data, &dataLength);
        if (mUsage & AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN) {
            // When we have CPU_READ_OFTEN usage bits, we can lock the image.
            if (ret != AMEDIA_OK || data == nullptr || dataLength < 0) {
                ALOGE("Failed to access CPU data, ret=%d, data=%p, dataLength=%d", ret, data,
                      dataLength);
                return;
            }
        } else {
            if (ret != AMEDIA_IMGREADER_CANNOT_LOCK_IMAGE || data != nullptr || dataLength != 0) {
                ALOGE("Shouldn't be able to access CPU data, ret=%d, data=%p, dataLength=%d", ret,
                      data, dataLength);
                return;
            }
        }
        // Only increase mAcquiredImageCount if all checks pass.
        mAcquiredImageCount++;
    }

    static void onImageAvailable(void* obj, AImageReader* reader) {
        ImageReaderTestCase* thiz = reinterpret_cast<ImageReaderTestCase*>(obj);
        thiz->HandleImageAvailable(reader);
    }

    static void
    onBufferRemoved(void* /*obj*/, AImageReader* /*reader*/, AHardwareBuffer* /*buffer*/) {
        // No-op, just to check the listener can be set properly.
    }

   private:
    int32_t mWidth;
    int32_t mHeight;
    int32_t mFormat;
    uint64_t mUsage;
    int32_t mMaxImages;
    bool mAsync;

    std::mutex mMutex;
    int mAcquiredImageCount{0};

    AImageReader* mImgReader = nullptr;
    ANativeWindow* mImgReaderAnw = nullptr;

    AImageReader_ImageListener mReaderAvailableCb{this, onImageAvailable};
    AImageReader_BufferRemovedListener mReaderDetachedCb{this, onBufferRemoved};
};

int takePictures(uint64_t readerUsage, int readerMaxImages, bool readerAsync, int pictureCount) {
    int ret = 0;

    ImageReaderTestCase testCase(
            kTestImageWidth, kTestImageHeight, kTestImageFormat, readerUsage, readerMaxImages,
            readerAsync);
    ret = testCase.initImageReader();
    if (ret < 0) {
        return ret;
    }

    CameraHelper cameraHelper(testCase.getNativeWindow());
    ret = cameraHelper.initCamera();
    if (ret < 0) {
        return ret;
    }

    if (!cameraHelper.isCameraReady()) {
        ALOGW("Camera is not ready after successful initialization. It's either due to camera on "
              "board lacks BACKWARDS_COMPATIBLE capability or the device does not have camera on "
              "board.");
        return 0;
    }

    for (int i = 0; i < pictureCount; i++) {
        ret = cameraHelper.takePicture();
        if (ret < 0) {
            return ret;
        }
    }

    // Sleep until all capture finished
    for (int i = 0; i < kCaptureWaitRetry * pictureCount; i++) {
        usleep(kCaptureWaitUs);
        if (testCase.getAcquiredImageCount() == pictureCount) {
            ALOGI("Session take ~%d ms to capture %d images", i * kCaptureWaitUs / 1000,
                  pictureCount);
            break;
        }
    }

    return testCase.getAcquiredImageCount() == pictureCount ? 0 : -1;
}

}  // namespace

// Test that newWithUsage can create AImageReader correctly.
extern "C" jboolean Java_android_media_cts_NativeImageReaderTest_\
testSucceedsWithSupportedUsageFormatNative(JNIEnv* /*env*/, jclass /*clazz*/) {
    static constexpr int kTestImageCount = 8;

    for (auto& combination : supportedFormatUsage) {
        AImageReader* outReader;
        media_status_t ret = AImageReader_newWithUsage(
                kTestImageWidth, kTestImageHeight, combination.format, combination.usage,
                kTestImageCount, &outReader);
        if (ret != AMEDIA_OK || outReader == nullptr) {
            ALOGE("AImageReader_newWithUsage failed with format: %" PRId64 ", usage: %" PRId64 ".",
                  combination.format, combination.usage);
            return false;
        }
        AImageReader_delete(outReader);
    }

    return true;
}

extern "C" jboolean Java_android_media_cts_NativeImageReaderTest_\
testTakePicturesNative(JNIEnv* /*env*/, jclass /*clazz*/) {
    for (auto& readerUsage :
         {AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN}) {
        for (auto& readerMaxImages : {1, 4, 8}) {
            for (auto& readerAsync : {true, false}) {
                for (auto& pictureCount : {1, 8, 16}) {
                    if (takePictures(readerUsage, readerMaxImages, readerAsync, pictureCount)) {
                        ALOGE("Test takePictures failed for test case usage=%" PRIu64 ", maxImages=%d, "
                              "async=%d, pictureCount=%d",
                              readerUsage, readerMaxImages, readerAsync, pictureCount);
                        return false;
                    }
                }
            }
        }
    }
    return true;
}

extern "C" jobject Java_android_media_cts_NativeImageReaderTest_\
testCreateSurfaceNative(JNIEnv* env, jclass /*clazz*/) {
    static constexpr uint64_t kTestImageUsage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN;
    static constexpr int kTestImageCount = 8;

    ImageReaderTestCase testCase(
            kTestImageWidth, kTestImageHeight, kTestImageFormat, kTestImageUsage, kTestImageCount,
            false);
    int ret = testCase.initImageReader();
    if (ret < 0) {
        ALOGE("Failed to get initialize image reader: ret=%d.", ret);
        return nullptr;
    }

    // No need to release the window as AImageReader_delete takes care of it.
    ANativeWindow* window = testCase.getNativeWindow();
    if (window == nullptr) {
        ALOGE("Failed to get native window for the test case.");
        return nullptr;
    }

    return ANativeWindow_toSurface(env, window);
}
