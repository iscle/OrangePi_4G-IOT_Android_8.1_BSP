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

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <jni.h>
#include <stdlib.h>
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <string>

#define  LOG_TAG    "VrExtensionsJni"
#define  LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE,LOG_TAG,__VA_ARGS__)

using PFNEGLGETNATIVECLIENTBUFFERANDROID =
        EGLClientBuffer(EGLAPIENTRYP)(const AHardwareBuffer* buffer);

using PFNGLEGLIMAGETARGETTEXTURE2DOESPROC = void(GL_APIENTRYP)(GLenum target,
                                                               void* image);

using PFNGLBUFFERSTORAGEEXTERNALEXTPROC =
    void(GL_APIENTRYP)(GLenum target, GLintptr offset, GLsizeiptr size,
                       void* clientBuffer, GLbitfield flags);

using PFNGLMAPBUFFERRANGEPROC = void*(GL_APIENTRYP)(GLenum target,
                                                    GLintptr offset,
                                                    GLsizeiptr length,
                                                    GLbitfield access);

using PFNGLUNMAPBUFFERPROC = void*(GL_APIENTRYP)(GLenum target);

PFNGLEGLIMAGETARGETTEXTURE2DOESPROC glEGLImageTargetTexture2DOES;
PFNEGLGETNATIVECLIENTBUFFERANDROID eglGetNativeClientBufferANDROID;
PFNEGLCREATEIMAGEKHRPROC eglCreateImageKHR;
PFNGLFRAMEBUFFERTEXTUREMULTIVIEWOVRPROC glFramebufferTextureMultiviewOVR;
PFNGLFRAMEBUFFERTEXTUREMULTISAMPLEMULTIVIEWOVRPROC
    glFramebufferTextureMultisampleMultiviewOVR;
PFNGLBUFFERSTORAGEEXTERNALEXTPROC glBufferStorageExternalEXT;
PFNGLMAPBUFFERRANGEPROC glMapBufferRange;
PFNGLUNMAPBUFFERPROC glUnmapBuffer;

#define NO_ERROR 0
#define GL_UNIFORM_BUFFER         0x8A11

// Declare flags that are added to MapBufferRange via EXT_buffer_storage.
// https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_buffer_storage.txt
#define GL_MAP_PERSISTENT_BIT_EXT 0x0040
#define GL_MAP_COHERENT_BIT_EXT   0x0080

#define LOAD_PROC(NAME, TYPE)                                           \
    NAME = reinterpret_cast<TYPE>(eglGetProcAddress(# NAME))

#define ASSERT(condition, format, args...)      \
    if (!(condition)) {                         \
        fail(env, format, ## args);             \
        return;                                 \
    }

#define ASSERT_TRUE(a) \
    ASSERT((a), "assert failed on (" #a ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_FALSE(a) \
    ASSERT(!(a), "assert failed on (!" #a ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_EQ(a, b) \
    ASSERT((a) == (b), "assert failed on (" #a ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_NE(a, b) \
    ASSERT((a) != (b), "assert failed on (" #a ") at " __FILE__ ":%d", __LINE__)
#define ASSERT_GT(a, b) \
    ASSERT((a) > (b), "assert failed on (" #a ") at " __FILE__ ":%d", __LINE__)

void fail(JNIEnv* env, const char* format, ...) {
    va_list args;
    va_start(args, format);
    char* msg;
    vasprintf(&msg, format, args);
    va_end(args);
    jclass exClass;
    const char* className = "java/lang/AssertionError";
    exClass = env->FindClass(className);
    env->ThrowNew(exClass, msg);
    free(msg);
}

static void testEglImageArray(JNIEnv* env, AHardwareBuffer_Desc desc,
                              int nsamples) {
    ASSERT_GT(desc.layers, 1);
    AHardwareBuffer* hwbuffer = nullptr;
    int error = AHardwareBuffer_allocate(&desc, &hwbuffer);
    ASSERT_FALSE(error);
    // Create EGLClientBuffer from the AHardwareBuffer.
    EGLClientBuffer native_buffer = eglGetNativeClientBufferANDROID(hwbuffer);
    ASSERT_TRUE(native_buffer);
    // Create EGLImage from EGLClientBuffer.
    EGLint attrs[] = {EGL_NONE};
    EGLImageKHR image =
        eglCreateImageKHR(eglGetCurrentDisplay(), EGL_NO_CONTEXT,
                          EGL_NATIVE_BUFFER_ANDROID, native_buffer, attrs);
    ASSERT_TRUE(image);
    // Create OpenGL texture from the EGLImage.
    GLuint texid;
    glGenTextures(1, &texid);
    glBindTexture(GL_TEXTURE_2D_ARRAY, texid);
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D_ARRAY, image);
    ASSERT_EQ(glGetError(), GL_NO_ERROR);
    // Create FBO and add multiview attachment.
    GLuint fboid;
    glGenFramebuffers(1, &fboid);
    glBindFramebuffer(GL_FRAMEBUFFER, fboid);
    const GLint miplevel = 0;
    const GLint base_view = 0;
    const GLint num_views = desc.layers;
    if (nsamples == 1) {
        glFramebufferTextureMultiviewOVR(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                                         texid, miplevel, base_view, num_views);
    } else {
        glFramebufferTextureMultisampleMultiviewOVR(
            GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, texid, miplevel, nsamples,
            base_view, num_views);
    }
    ASSERT_EQ(glGetError(), GL_NO_ERROR);
    ASSERT_EQ(glCheckFramebufferStatus(GL_FRAMEBUFFER),
              GL_FRAMEBUFFER_COMPLETE);
    // Release memory.
    glDeleteTextures(1, &texid);
    glDeleteFramebuffers(1, &fboid);
    AHardwareBuffer_release(hwbuffer);
}

extern "C" JNIEXPORT void JNICALL
Java_android_vr_cts_VrExtensionBehaviorTest_nativeTestEglImageArray(
    JNIEnv* env, jclass /* unused */) {
    // First, load entry points provided by extensions.
    LOAD_PROC(glEGLImageTargetTexture2DOES,
              PFNGLEGLIMAGETARGETTEXTURE2DOESPROC);
    ASSERT_NE(glEGLImageTargetTexture2DOES, nullptr);
    LOAD_PROC(eglGetNativeClientBufferANDROID,
              PFNEGLGETNATIVECLIENTBUFFERANDROID);
    ASSERT_NE(eglGetNativeClientBufferANDROID, nullptr);
    LOAD_PROC(eglCreateImageKHR, PFNEGLCREATEIMAGEKHRPROC);
    ASSERT_NE(eglCreateImageKHR, nullptr);
    LOAD_PROC(glFramebufferTextureMultiviewOVR,
              PFNGLFRAMEBUFFERTEXTUREMULTIVIEWOVRPROC);
    ASSERT_NE(glFramebufferTextureMultiviewOVR, nullptr);
    LOAD_PROC(glFramebufferTextureMultisampleMultiviewOVR,
              PFNGLFRAMEBUFFERTEXTUREMULTISAMPLEMULTIVIEWOVRPROC);
    ASSERT_NE(glFramebufferTextureMultisampleMultiviewOVR, nullptr);
    // Try creating a 32x32 AHardwareBuffer and attaching it to a multiview
    // framebuffer, with various formats and depths.
    AHardwareBuffer_Desc desc = {};
    desc.width = 32;
    desc.height = 32;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE |
                 AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;
    const int layers[] = {2, 4};
    const int formats[] = {
      AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM,
      AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
      // Do not test AHARDWAREBUFFER_FORMAT_BLOB, it isn't color-renderable.
    };
    const int samples[] = {1, 2, 4};
    for (int nsamples : samples) {
      for (auto nlayers : layers) {
        for (auto format : formats) {
          desc.layers = nlayers;
          desc.format = format;
          testEglImageArray(env, desc, nsamples);
        }
      }
    }
}

static void testExternalBuffer(JNIEnv* env, uint64_t usage, bool write_hwbuffer,
                               const std::string& test_string) {
    // Create a blob AHardwareBuffer suitable for holding the string.
    AHardwareBuffer_Desc desc = {};
    desc.width = test_string.size();
    desc.height = 1;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_BLOB;
    desc.usage = usage;
    AHardwareBuffer* hwbuffer = nullptr;
    int error = AHardwareBuffer_allocate(&desc, &hwbuffer);
    ASSERT_EQ(error, NO_ERROR);
    // Create EGLClientBuffer from the AHardwareBuffer.
    EGLClientBuffer native_buffer = eglGetNativeClientBufferANDROID(hwbuffer);
    ASSERT_TRUE(native_buffer);
    // Create uniform buffer from EGLClientBuffer.
    const GLbitfield flags = GL_MAP_READ_BIT | GL_MAP_WRITE_BIT |
        GL_MAP_COHERENT_BIT_EXT | GL_MAP_PERSISTENT_BIT_EXT;
    GLuint buf = 0;
    glGenBuffers(1, &buf);
    glBindBuffer(GL_UNIFORM_BUFFER, buf);
    ASSERT_EQ(glGetError(), GL_NO_ERROR);
    const GLsizeiptr bufsize = desc.width * desc.height;
    glBufferStorageExternalEXT(GL_UNIFORM_BUFFER, 0,
             bufsize, native_buffer, flags);
    ASSERT_EQ(glGetError(), GL_NO_ERROR);
    // Obtain a writeable pointer using either OpenGL or the Android API,
    // then copy the test string into it.
    if (write_hwbuffer) {
      void* data = nullptr;
      error = AHardwareBuffer_lock(hwbuffer,
                                   AHARDWAREBUFFER_USAGE_CPU_READ_RARELY, -1,
                                   NULL, &data);
      ASSERT_EQ(error, NO_ERROR);
      ASSERT_TRUE(data);
      memcpy(data, test_string.c_str(), test_string.size());
      error = AHardwareBuffer_unlock(hwbuffer, nullptr);
      ASSERT_EQ(error, NO_ERROR);
    } else {
      void* data =
          glMapBufferRange(GL_UNIFORM_BUFFER, 0, bufsize,
                           GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT_EXT);
      ASSERT_EQ(glGetError(), GL_NO_ERROR);
      ASSERT_TRUE(data);
      memcpy(data, test_string.c_str(), test_string.size());
      glUnmapBuffer(GL_UNIFORM_BUFFER);
      ASSERT_EQ(glGetError(), GL_NO_ERROR);
    }
    // Obtain a readable pointer and verify the data.
    void* data = glMapBufferRange(GL_UNIFORM_BUFFER, 0, bufsize, GL_MAP_READ_BIT);
    ASSERT_TRUE(data);
    ASSERT_EQ(strncmp(static_cast<char*>(data), test_string.c_str(),
                      test_string.size()), 0);
    glUnmapBuffer(GL_UNIFORM_BUFFER);
    ASSERT_EQ(glGetError(), GL_NO_ERROR);
    AHardwareBuffer_release(hwbuffer);
}

extern "C" JNIEXPORT void JNICALL
Java_android_vr_cts_VrExtensionBehaviorTest_nativeTestExternalBuffer(
    JNIEnv* env, jclass /* unused */) {
    // First, check for EXT_external_buffer in the extension string.
    auto exts = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    ASSERT_TRUE(exts && strstr(exts, "GL_EXT_external_buffer"));
    // Next, load entry points provided by extensions.
    LOAD_PROC(eglGetNativeClientBufferANDROID, PFNEGLGETNATIVECLIENTBUFFERANDROID);
    ASSERT_NE(eglGetNativeClientBufferANDROID, nullptr);
    LOAD_PROC(glBufferStorageExternalEXT, PFNGLBUFFERSTORAGEEXTERNALEXTPROC);
    ASSERT_NE(glBufferStorageExternalEXT, nullptr);
    LOAD_PROC(glMapBufferRange, PFNGLMAPBUFFERRANGEPROC);
    ASSERT_NE(glMapBufferRange, nullptr);
    LOAD_PROC(glUnmapBuffer, PFNGLUNMAPBUFFERPROC);
    ASSERT_NE(glUnmapBuffer, nullptr);
    const uint64_t usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN |
        AHARDWAREBUFFER_USAGE_CPU_READ_RARELY |
        AHARDWAREBUFFER_USAGE_GPU_DATA_BUFFER |
        AHARDWAREBUFFER_USAGE_SENSOR_DIRECT_DATA;
    const std::string test_string = "Hello, world.";
    // First try writing to the buffer using OpenGL, then try writing to it via
    // the AHardwareBuffer API.
    testExternalBuffer(env, usage, false, test_string);
    testExternalBuffer(env, usage, true, test_string);
}
