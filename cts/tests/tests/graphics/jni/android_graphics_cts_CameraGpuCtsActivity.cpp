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
 *
 */

#define LOG_TAG "CameraGpuCtsActivity"

#include <jni.h>
#include <unistd.h>

#include <deque>
#include <memory>
#include <mutex>
#include <vector>

#include <android/log.h>
#include <android/native_window_jni.h>
#include <camera/NdkCameraError.h>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraDevice.h>
#include <camera/NdkCameraCaptureSession.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES/gl.h>
#include <GLES/glext.h>
#include <GLES2/gl2.h>
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>

//#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
//#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

static constexpr uint32_t kTestImageWidth = 640;
static constexpr uint32_t kTestImageHeight = 480;
static constexpr uint32_t kTestImageFormat = AIMAGE_FORMAT_PRIVATE;
static constexpr uint64_t kTestImageUsage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
static constexpr uint32_t kTestImageCount = 3;

static const char kVertShader[] = R"(
  attribute vec2 aPosition;
  attribute vec2 aTextureCoords;
  varying vec2 texCoords;

  void main() {
    texCoords =  aTextureCoords;
    gl_Position = vec4(aPosition, 0.0, 1.0);
  }
)";

static const char kFragShader[] = R"(
  #extension GL_OES_EGL_image_external : require

  precision mediump float;
  varying vec2 texCoords;
  uniform samplerExternalOES sTexture;

  void main() {
    gl_FragColor = texture2D(sTexture, texCoords);
  }
)";

// A 80%-full screen mesh.
GLfloat kScreenTriangleStrip[] = {
    // 1st vertex
    -0.8f, -0.8f, 0.0f, 1.0f,
    // 2nd vertex
    -0.8f, 0.8f, 0.0f, 0.0f,
    // 3rd vertex
    0.8f, -0.8f, 1.0f, 1.0f,
    // 4th vertex
    0.8f, 0.8f, 1.0f, 0.0f,
};

static void checkGlError(const char* op) {
    for (GLint error = glGetError(); error; error
            = glGetError()) {
        ALOGW("after %s() glError (0x%x)\n", op, error);
    }
}

class CameraHelper {
  public:
    ~CameraHelper() { closeCamera(); }

    int initCamera(ANativeWindow* imgReaderAnw) {
        if (imgReaderAnw == nullptr) {
            ALOGE("Cannot initialize camera before image reader get initialized.");
            return -1;
        }

        mImgReaderAnw = imgReaderAnw;
        mCameraManager = ACameraManager_create();
        if (mCameraManager == nullptr) {
            ALOGE("Failed to create ACameraManager.");
            return -1;
        }

        int ret = ACameraManager_getCameraIdList(mCameraManager, &mCameraIdList);
        if (ret != AMEDIA_OK) {
            ALOGE("Failed to get cameraIdList: ret=%d", ret);
            return ret;
        }
        ALOGI("Found %d camera(s).", mCameraIdList->numCameras);

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
        ret = ACameraDevice_createCaptureRequest(mDevice, TEMPLATE_RECORD, &mCaptureRequest);
        if (ret != AMEDIA_OK) {
            ALOGE("ACameraDevice_createCaptureRequest failed, ret=%d", ret);
            return ret;
        }
        ret = ACameraOutputTarget_create(mImgReaderAnw, &mReqImgReaderOutput);
        if (ret != AMEDIA_OK) {
            ALOGE("ACameraOutputTarget_create failed, ret=%d", ret);
            return ret;
        }
        ret = ACaptureRequest_addTarget(mCaptureRequest, mReqImgReaderOutput);
        if (ret != AMEDIA_OK) {
            ALOGE("ACaptureRequest_addTarget failed, ret=%d", ret);
            return ret;
        }

        mIsCameraReady = true;
        return 0;
    }

    bool isCapabilitySupported(acamera_metadata_enum_android_request_available_capabilities_t cap) {
        ACameraMetadata_const_entry entry;
        ACameraMetadata_getConstEntry(mCameraMetadata, ACAMERA_REQUEST_AVAILABLE_CAPABILITIES,
                                      &entry);
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
        if (mCaptureRequest) {
            ACaptureRequest_free(mCaptureRequest);
            mCaptureRequest = nullptr;
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
        return ACameraCaptureSession_capture(mSession, nullptr, 1, &mCaptureRequest, nullptr);
    }

    static void onDeviceDisconnected(void* /*obj*/, ACameraDevice* /*device*/) {}

    static void onDeviceError(void* /*obj*/, ACameraDevice* /*device*/, int /*errorCode*/) {}

    static void onSessionClosed(void* /*obj*/, ACameraCaptureSession* /*session*/) {}

    static void onSessionReady(void* /*obj*/, ACameraCaptureSession* /*session*/) {}

    static void onSessionActive(void* /*obj*/, ACameraCaptureSession* /*session*/) {}

  private:
    ACameraDevice_StateCallbacks mDeviceCb{this, onDeviceDisconnected, onDeviceError};
    ACameraCaptureSession_stateCallbacks mSessionCb{this, onSessionClosed, onSessionReady,
                                                    onSessionActive};

    ANativeWindow* mImgReaderAnw{nullptr};  // not owned by us.

    // Camera manager
    ACameraManager* mCameraManager{nullptr};
    ACameraIdList* mCameraIdList{nullptr};
    // Camera device
    ACameraMetadata* mCameraMetadata{nullptr};
    ACameraDevice* mDevice{nullptr};
    // Capture session
    ACaptureSessionOutputContainer* mOutputs{nullptr};
    ACaptureSessionOutput* mImgReaderOutput{nullptr};
    ACameraCaptureSession* mSession{nullptr};
    // Capture request
    ACaptureRequest* mCaptureRequest{nullptr};
    ACameraOutputTarget* mReqImgReaderOutput{nullptr};

    bool mIsCameraReady{false};
    const char* mCameraId{nullptr};
};

class ImageReaderHelper {
  public:
    using ImagePtr = std::unique_ptr<AImage, decltype(&AImage_delete)>;

    ImageReaderHelper(int32_t width, int32_t height, int32_t format, uint64_t usage,
                      int32_t maxImages)
        : mWidth(width), mHeight(height), mFormat(format), mUsage(usage), mMaxImages(maxImages) {}

    ~ImageReaderHelper() {
        mAcquiredImage.reset();
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

        int ret =
            AImageReader_newWithUsage(mWidth, mHeight, mFormat, mUsage, mMaxImages, &mImgReader);
        if (ret != AMEDIA_OK || mImgReader == nullptr) {
            ALOGE("Failed to create new AImageReader, ret=%d, mImgReader=%p", ret, mImgReader);
            return -1;
        }

        ret = AImageReader_setImageListener(mImgReader, &mReaderAvailableCb);
        if (ret != AMEDIA_OK) {
            ALOGE("Failed to set image available listener, ret=%d.", ret);
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

    int getBufferFromCurrentImage(AHardwareBuffer** outBuffer) {
        std::lock_guard<std::mutex> lock(mMutex);

        int ret = 0;
        uint8_t* data;
        int data_size;
        if (mAvailableImages > 0) {
            AImage* outImage = nullptr;

            mAvailableImages -= 1;

            ret = AImageReader_acquireNextImage(mImgReader, &outImage);
            if (ret != AMEDIA_OK || outImage == nullptr) {
                // When the BufferQueue is in async mode, it is still possible that
                // AImageReader_acquireNextImage returns nothing after onFrameAvailable.
                ALOGW("Failed to acquire image, ret=%d, outIamge=%p.", ret, outImage);
            } else {
                // Any exisitng in mAcquiredImage will be deleted and released automatically.
                mAcquiredImage.reset(outImage);
            }
            // Expected getPlaneData to fail for AIMAGE_FORMAT_PRIV, if not then
            // return error
            ret = AImage_getPlaneData(outImage, 0, &data, &data_size);
            if (ret != AMEDIA_IMGREADER_CANNOT_LOCK_IMAGE)
              return -EINVAL;
        }

        if (mAcquiredImage == nullptr) {
            return -EAGAIN;
        }

        // Note that AImage_getHardwareBuffer is not acquiring additional reference to the buffer,
        // so we can return it here any times we want without worrying about releasing.
        AHardwareBuffer* buffer = nullptr;
        ret = AImage_getHardwareBuffer(mAcquiredImage.get(), &buffer);
        if (ret != AMEDIA_OK || buffer == nullptr) {
            ALOGE("Faild to get hardware buffer, ret=%d, outBuffer=%p.", ret, buffer);
            return -ENOMEM;
        }

        *outBuffer = buffer;
        return 0;
    }

    void handleImageAvailable() {
        std::lock_guard<std::mutex> lock(mMutex);

        mAvailableImages += 1;
    }

    static void onImageAvailable(void* obj, AImageReader*) {
        ImageReaderHelper* thiz = reinterpret_cast<ImageReaderHelper*>(obj);
        thiz->handleImageAvailable();
    }

  private:
    int32_t mWidth;
    int32_t mHeight;
    int32_t mFormat;
    uint64_t mUsage;
    uint32_t mMaxImages;

    std::mutex mMutex;
    // Number of images that's avaiable for acquire.
    size_t mAvailableImages{0};
    // Although AImageReader supports acquiring multiple images at a time, we don't really need it
    // in this test. We only acquire one image that a time.
    ImagePtr mAcquiredImage{nullptr, AImage_delete};

    AImageReader* mImgReader{nullptr};
    ANativeWindow* mImgReaderAnw{nullptr};

    AImageReader_ImageListener mReaderAvailableCb{this, onImageAvailable};
};

class CameraFrameRenderer {
  public:
    CameraFrameRenderer()
        : mImageReader(kTestImageWidth, kTestImageHeight, kTestImageFormat, kTestImageUsage,
                       kTestImageCount) {}

    ~CameraFrameRenderer() {
        if (mProgram) {
            glDeleteProgram(mProgram);
            mProgram = 0;
        }

        if (mEglImage != EGL_NO_IMAGE_KHR) {
            eglDestroyImageKHR(mEglDisplay, mEglImage);
            mEglImage = EGL_NO_IMAGE_KHR;
        }
    }

    // Retrun Zero on success, or negative error code.
    int initRenderer() {
        int ret = mImageReader.initImageReader();
        if (ret < 0) {
            ALOGE("Failed to initialize image reader: %d", ret);
            return ret;
        }

        ret = mCamera.initCamera(mImageReader.getNativeWindow());
        if (ret < 0) {
            ALOGE("Failed to initialize camera: %d", ret);
            return ret;
        }

        // This test should only test devices with at least one camera.
        if (!mCamera.isCameraReady()) {
            ALOGE(
                "Camera is not ready after successful initialization. It's either due to camera on "
                "board lacks BACKWARDS_COMPATIBLE capability or the device does not have camera on "
                "board.");
            return -EIO;
        }

        // Load shader and program.
        mProgram = glCreateProgram();
        GLuint vertShader = loadShader(GL_VERTEX_SHADER, kVertShader);
        GLuint fragShader = loadShader(GL_FRAGMENT_SHADER, kFragShader);

        if (vertShader == 0 || fragShader == 0) {
            ALOGE("Failed to load shader");
            return -EINVAL;
        }

        mProgram = glCreateProgram();
        glAttachShader(mProgram, vertShader);
        checkGlError("glAttachShader");
        glAttachShader(mProgram, fragShader);
        checkGlError("glAttachShader");

        glLinkProgram(mProgram);
        GLint success;
        glGetProgramiv(mProgram, GL_LINK_STATUS, &success);
        if (!success) {
            GLchar infoLog[512];
            glGetProgramInfoLog(mProgram, 512, nullptr, infoLog);
            ALOGE("Shader failed to link: %s", infoLog);
            return -EINVAL;
        }

        // Get attributes.
        mPositionHandle = glGetAttribLocation(mProgram, "aPosition");
        mTextureCoordsHandle = glGetAttribLocation(mProgram, "aTextureCoords");

        // Get uniforms.
        mTextureUniform = glGetUniformLocation(mProgram, "sTexture");
        checkGlError("glGetUniformLocation");

        // Generate texture.
        glGenTextures(1, &mTextureId);
        checkGlError("glGenTextures");
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureId);

        // Cache the display
        mEglDisplay = eglGetCurrentDisplay();

        return 0;
    }

    // Return Zero on success, or negative error code.
    int drawFrame() {
        // Indicate the camera to take recording.
        int ret = mCamera.takePicture();
        if (ret < 0) {
            ALOGE("Camera failed to take picture, error=%d", ret);
        }

        // Render the current buffer and then release it.
        AHardwareBuffer* buffer;
        ret = mImageReader.getBufferFromCurrentImage(&buffer);
        if (ret != 0) {
          // There might be no buffer acquired yet.
          return ret;
        }

        AHardwareBuffer_Desc outDesc;
        AHardwareBuffer_describe(buffer, &outDesc);

        // Render with EGLImage.
        EGLClientBuffer eglBuffer = eglGetNativeClientBufferANDROID(buffer);
        if (!eglBuffer) {
          ALOGE("Failed to create EGLClientBuffer");
          return -EINVAL;
        }

        if (mEglImage != EGL_NO_IMAGE_KHR) {
            eglDestroyImageKHR(mEglDisplay, mEglImage);
            mEglImage = EGL_NO_IMAGE_KHR;
        }

        EGLint attrs[] = {
            EGL_IMAGE_PRESERVED_KHR,
            EGL_TRUE,
            EGL_NONE,
        };

        mEglImage = eglCreateImageKHR(mEglDisplay, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
                                      eglBuffer, attrs);

        if (mEglImage == EGL_NO_IMAGE_KHR) {
            ALOGE("Failed to create EGLImage.");
            return -EINVAL;
        }

        glClearColor(0.4f, 0.6f, 1.0f, 0.2f);
        glClear(GL_COLOR_BUFFER_BIT);
        checkGlError("glClearColor");

        // Use shader
        glUseProgram(mProgram);
        checkGlError("glUseProgram");

        // Map texture
        glEGLImageTargetTexture2DOES(GL_TEXTURE_EXTERNAL_OES, mEglImage);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureId);
        glUniform1i(mTextureUniform, 0);
        checkGlError("glUniform1i");

        // Draw mesh
        glVertexAttribPointer(mPositionHandle, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat),
                              kScreenTriangleStrip);
        glEnableVertexAttribArray(mPositionHandle);
        glVertexAttribPointer(mTextureCoordsHandle, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat),
                              kScreenTriangleStrip + 2);
        glEnableVertexAttribArray(mTextureCoordsHandle);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");

        return 0;
    }

  private:
    static GLuint loadShader(GLenum shaderType, const char* source) {
        GLuint shader = glCreateShader(shaderType);

        glShaderSource(shader, 1, &source, nullptr);
        glCompileShader(shader);

        GLint success;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
        if (!success) {
            ALOGE("Shader Failed to compile: %s", source);
            shader = 0;
        }
        return shader;
    }

    ImageReaderHelper mImageReader;
    CameraHelper mCamera;

    // Shader
    GLuint mProgram{0};

    // Texture
    EGLDisplay mEglDisplay{EGL_NO_DISPLAY};
    EGLImageKHR mEglImage{EGL_NO_IMAGE_KHR};
    GLuint mTextureId{0};
    GLuint mTextureUniform{0};
    GLuint mPositionHandle{0};
    GLuint mTextureCoordsHandle{0};
};

inline jlong jptr(CameraFrameRenderer* native_video_player) {
    return reinterpret_cast<intptr_t>(native_video_player);
}

inline CameraFrameRenderer* native(jlong ptr) {
    return reinterpret_cast<CameraFrameRenderer*>(ptr);
}

jlong createRenderer(JNIEnv*, jclass) {
    auto renderer = std::unique_ptr<CameraFrameRenderer>(new CameraFrameRenderer);
    int ret = renderer->initRenderer();
    if (ret < 0) {
        ALOGE("Failed to init renderer: %d", ret);
        return jptr(nullptr);
    }

    return jptr(renderer.release());
}

void destroyRenderer(JNIEnv*, jclass, jlong renderer) { delete native(renderer); }

jint drawFrame(JNIEnv*, jclass, jlong renderer) {
    if (renderer == 0) {
        ALOGE("Invalid renderer.");
        return -EINVAL;
    }

    return native(renderer)->drawFrame();
}

const std::vector<JNINativeMethod> gMethods = {{
    {"nCreateRenderer", "()J", (void*)createRenderer},
    {"nDestroyRenderer", "(J)V", (void*)destroyRenderer},
    {"nDrawFrame", "(J)I", (void*)drawFrame},
}};

}  // namespace

int register_android_graphics_cts_CameraGpuCtsActivity(JNIEnv* env) {
    jclass clazz = env->FindClass("android/graphics/cts/CameraGpuCtsActivity");
    return env->RegisterNatives(clazz, gMethods.data(), gMethods.size());
}
