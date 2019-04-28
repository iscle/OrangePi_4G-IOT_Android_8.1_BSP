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

#include <gtest/gtest.h>

#include <android/hardware/configstore/1.0/ISurfaceFlingerConfigs.h>

#include <configstore/Utils.h>
#include <utils/String8.h>

#include <EGL/egl.h>
#include <gui/Surface.h>
#include <gui/IConsumerListener.h>
#include <gui/IProducerListener.h>
#include <gui/IGraphicBufferConsumer.h>
#include <gui/BufferQueue.h>

bool hasEglExtension(EGLDisplay dpy, const char* extensionName) {
    const char* exts = eglQueryString(dpy, EGL_EXTENSIONS);
    size_t cropExtLen = strlen(extensionName);
    size_t extsLen = strlen(exts);
    bool equal = !strcmp(extensionName, exts);
    android::String8 extString(extensionName);
    android::String8 space(" ");
    bool atStart = !strncmp(extString + space, exts, cropExtLen + 1);
    bool atEnd = (cropExtLen + 1) < extsLen &&
            !strcmp(space + extString, exts + extsLen - (cropExtLen + 1));
    bool inMiddle = strstr(exts, space + extString + space);
    return equal || atStart || atEnd || inMiddle;
}

namespace android {

#define EGL_UNSIGNED_TRUE static_cast<EGLBoolean>(EGL_TRUE)

// retrieve wide-color setting from configstore
using namespace android::hardware::configstore;
using namespace android::hardware::configstore::V1_0;

static bool hasWideColorDisplay =
        getBool<ISurfaceFlingerConfigs, &ISurfaceFlingerConfigs::hasWideColorDisplay>(false);

class EGLTest : public ::testing::Test {
protected:
    EGLDisplay mEglDisplay;

protected:
    EGLTest() :
            mEglDisplay(EGL_NO_DISPLAY) {
    }

    virtual void SetUp() {
        mEglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        ASSERT_NE(EGL_NO_DISPLAY, mEglDisplay);
        ASSERT_EQ(EGL_SUCCESS, eglGetError());

        EGLint majorVersion;
        EGLint minorVersion;
        EXPECT_TRUE(eglInitialize(mEglDisplay, &majorVersion, &minorVersion));
        ASSERT_EQ(EGL_SUCCESS, eglGetError());
        RecordProperty("EglVersionMajor", majorVersion);
        RecordProperty("EglVersionMajor", minorVersion);
    }

    virtual void TearDown() {
        EGLBoolean success = eglTerminate(mEglDisplay);
        ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
        ASSERT_EQ(EGL_SUCCESS, eglGetError());
    }
};

TEST_F(EGLTest, DISABLED_EGLConfigEightBitFirst) {

    EGLint numConfigs;
    EGLConfig config;
    EGLBoolean success;
    EGLint attrs[] = {
            EGL_SURFACE_TYPE,       EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,    EGL_OPENGL_ES2_BIT,
            EGL_NONE
    };

    success = eglChooseConfig(mEglDisplay, attrs, &config, 1, &numConfigs);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    ASSERT_GE(numConfigs, 1);

    EGLint components[3];

    success = eglGetConfigAttrib(mEglDisplay, config, EGL_RED_SIZE, &components[0]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_GREEN_SIZE, &components[1]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_BLUE_SIZE, &components[2]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());

    EXPECT_GE(components[0], 8);
    EXPECT_GE(components[1], 8);
    EXPECT_GE(components[2], 8);
}

TEST_F(EGLTest, EGLTerminateSucceedsWithRemainingObjects) {
    EGLint numConfigs;
    EGLConfig config;
    EGLint attrs[] = {
        EGL_SURFACE_TYPE,       EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE,    EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE,           8,
        EGL_GREEN_SIZE,         8,
        EGL_BLUE_SIZE,          8,
        EGL_ALPHA_SIZE,         8,
        EGL_NONE
    };
    EXPECT_TRUE(eglChooseConfig(mEglDisplay, attrs, &config, 1, &numConfigs));

    struct DummyConsumer : public BnConsumerListener {
        void onFrameAvailable(const BufferItem& /* item */) override {}
        void onBuffersReleased() override {}
        void onSidebandStreamChanged() override {}
    };

    // Create a EGLSurface
    sp<IGraphicBufferProducer> producer;
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&producer, &consumer);
    consumer->consumerConnect(new DummyConsumer, false);
    sp<Surface> mSTC = new Surface(producer);
    sp<ANativeWindow> mANW = mSTC;

    EGLSurface eglSurface = eglCreateWindowSurface(mEglDisplay, config,
                                mANW.get(), NULL);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    ASSERT_NE(EGL_NO_SURFACE, eglSurface) ;

    // do not destroy eglSurface
    // eglTerminate is called in the tear down and should destroy it for us
}

TEST_F(EGLTest, EGLConfigRGBA8888First) {

    EGLint numConfigs;
    EGLConfig config;
    EGLBoolean success;
    EGLint attrs[] = {
            EGL_SURFACE_TYPE,       EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,    EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE,           8,
            EGL_GREEN_SIZE,         8,
            EGL_BLUE_SIZE,          8,
            EGL_ALPHA_SIZE,         8,
            EGL_NONE
    };

    success = eglChooseConfig(mEglDisplay, attrs, &config, 1, &numConfigs);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    ASSERT_GE(numConfigs, 1);

    EGLint components[4];

    success = eglGetConfigAttrib(mEglDisplay, config, EGL_RED_SIZE, &components[0]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_GREEN_SIZE, &components[1]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_BLUE_SIZE, &components[2]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_ALPHA_SIZE, &components[3]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());

    EXPECT_GE(components[0], 8);
    EXPECT_GE(components[1], 8);
    EXPECT_GE(components[2], 8);
    EXPECT_GE(components[3], 8);
}

TEST_F(EGLTest, EGLDisplayP3) {
    EGLint numConfigs;
    EGLConfig config;
    EGLBoolean success;

    if (!hasWideColorDisplay) {
        // skip this test if device does not have wide-color display
        std::cerr << "[          ] Device does not support wide-color, test skipped" << std::endl;
        return;
    }

    // Test that display-p3 extensions exist
    ASSERT_TRUE(hasEglExtension(mEglDisplay, "EGL_EXT_gl_colorspace_display_p3"));
    ASSERT_TRUE(hasEglExtension(mEglDisplay, "EGL_EXT_gl_colorspace_display_p3_linear"));

    // Use 8-bit to keep forcus on Display-P3 aspect
    EGLint attrs[] = {
            // clang-format off
            EGL_SURFACE_TYPE,             EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,          EGL_OPENGL_ES2_BIT,
            EGL_SURFACE_TYPE,             EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
            EGL_RED_SIZE,                 8,
            EGL_GREEN_SIZE,               8,
            EGL_BLUE_SIZE,                8,
            EGL_ALPHA_SIZE,               8,
            EGL_COLOR_COMPONENT_TYPE_EXT, EGL_COLOR_COMPONENT_TYPE_FIXED_EXT,
            EGL_NONE,                     EGL_NONE
            // clang-format on
    };
    success = eglChooseConfig(mEglDisplay, attrs, &config, 1, &numConfigs);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(1, numConfigs);

    EGLint components[4];
    EGLint value;
    eglGetConfigAttrib(mEglDisplay, config, EGL_CONFIG_ID, &value);

    success = eglGetConfigAttrib(mEglDisplay, config, EGL_RED_SIZE, &components[0]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_GREEN_SIZE, &components[1]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_BLUE_SIZE, &components[2]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_ALPHA_SIZE, &components[3]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());

    EXPECT_EQ(components[0], 8);
    EXPECT_EQ(components[1], 8);
    EXPECT_EQ(components[2], 8);
    EXPECT_EQ(components[3], 8);

    struct DummyConsumer : public BnConsumerListener {
        void onFrameAvailable(const BufferItem& /* item */) override {}
        void onBuffersReleased() override {}
        void onSidebandStreamChanged() override {}
    };

    // Create a EGLSurface
    sp<IGraphicBufferProducer> producer;
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&producer, &consumer);
    consumer->consumerConnect(new DummyConsumer, false);
    sp<Surface> mSTC = new Surface(producer);
    sp<ANativeWindow> mANW = mSTC;
    EGLint winAttrs[] = {
            // clang-format off
            EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_DISPLAY_P3_EXT,
            EGL_NONE,              EGL_NONE
            // clang-format on
    };

    EGLSurface eglSurface = eglCreateWindowSurface(mEglDisplay, config, mANW.get(), winAttrs);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    ASSERT_NE(EGL_NO_SURFACE, eglSurface);

    success = eglQuerySurface(mEglDisplay, eglSurface, EGL_GL_COLORSPACE_KHR, &value);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_GL_COLORSPACE_DISPLAY_P3_EXT, value);

    EXPECT_TRUE(eglDestroySurface(mEglDisplay, eglSurface));
}

TEST_F(EGLTest, EGLDisplayP31010102) {
    EGLint numConfigs;
    EGLConfig config;
    EGLBoolean success;

    if (!hasWideColorDisplay) {
        // skip this test if device does not have wide-color display
        std::cerr << "[          ] Device does not support wide-color, test skipped" << std::endl;
        return;
    }

    // Test that display-p3 extensions exist
    ASSERT_TRUE(hasEglExtension(mEglDisplay, "EGL_EXT_gl_colorspace_display_p3"));
    ASSERT_TRUE(hasEglExtension(mEglDisplay, "EGL_EXT_gl_colorspace_display_p3_linear"));

    // Use 8-bit to keep forcus on Display-P3 aspect
    EGLint attrs[] = {
            // clang-format off
            EGL_SURFACE_TYPE,             EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,          EGL_OPENGL_ES2_BIT,
            EGL_SURFACE_TYPE,             EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
            EGL_RED_SIZE,                 10,
            EGL_GREEN_SIZE,               10,
            EGL_BLUE_SIZE,                10,
            EGL_ALPHA_SIZE,               2,
            EGL_COLOR_COMPONENT_TYPE_EXT, EGL_COLOR_COMPONENT_TYPE_FIXED_EXT,
            EGL_NONE,                     EGL_NONE
            // clang-format on
    };
    success = eglChooseConfig(mEglDisplay, attrs, &config, 1, &numConfigs);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(1, numConfigs);

    EGLint components[4];
    EGLint value;
    eglGetConfigAttrib(mEglDisplay, config, EGL_CONFIG_ID, &value);

    success = eglGetConfigAttrib(mEglDisplay, config, EGL_RED_SIZE, &components[0]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_GREEN_SIZE, &components[1]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_BLUE_SIZE, &components[2]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_ALPHA_SIZE, &components[3]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());

    EXPECT_EQ(components[0], 10);
    EXPECT_EQ(components[1], 10);
    EXPECT_EQ(components[2], 10);
    EXPECT_EQ(components[3], 2);

    struct DummyConsumer : public BnConsumerListener {
        void onFrameAvailable(const BufferItem& /* item */) override {}
        void onBuffersReleased() override {}
        void onSidebandStreamChanged() override {}
    };

    // Create a EGLSurface
    sp<IGraphicBufferProducer> producer;
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&producer, &consumer);
    consumer->consumerConnect(new DummyConsumer, false);
    sp<Surface> mSTC = new Surface(producer);
    sp<ANativeWindow> mANW = mSTC;
    EGLint winAttrs[] = {
            // clang-format off
            EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_DISPLAY_P3_EXT,
            EGL_NONE,              EGL_NONE
            // clang-format on
    };

    EGLSurface eglSurface = eglCreateWindowSurface(mEglDisplay, config, mANW.get(), winAttrs);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    ASSERT_NE(EGL_NO_SURFACE, eglSurface);

    success = eglQuerySurface(mEglDisplay, eglSurface, EGL_GL_COLORSPACE_KHR, &value);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_GL_COLORSPACE_DISPLAY_P3_EXT, value);

    EXPECT_TRUE(eglDestroySurface(mEglDisplay, eglSurface));
}

TEST_F(EGLTest, EGLConfigFP16) {
    EGLint numConfigs;
    EGLConfig config;
    EGLBoolean success;

    if (!hasWideColorDisplay) {
        // skip this test if device does not have wide-color display
        std::cerr << "[          ] Device does not support wide-color, test skipped" << std::endl;
        return;
    }

    ASSERT_TRUE(hasEglExtension(mEglDisplay, "EGL_EXT_pixel_format_float"));

    EGLint attrs[] = {
            // clang-format off
            EGL_SURFACE_TYPE,             EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,          EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE,                 16,
            EGL_GREEN_SIZE,               16,
            EGL_BLUE_SIZE,                16,
            EGL_ALPHA_SIZE,               16,
            EGL_COLOR_COMPONENT_TYPE_EXT, EGL_COLOR_COMPONENT_TYPE_FLOAT_EXT,
            EGL_NONE,                     EGL_NONE
            // clang-format on
    };
    success = eglChooseConfig(mEglDisplay, attrs, &config, 1, &numConfigs);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(1, numConfigs);

    EGLint components[4];

    success = eglGetConfigAttrib(mEglDisplay, config, EGL_RED_SIZE, &components[0]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_GREEN_SIZE, &components[1]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_BLUE_SIZE, &components[2]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_ALPHA_SIZE, &components[3]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());

    EXPECT_GE(components[0], 16);
    EXPECT_GE(components[1], 16);
    EXPECT_GE(components[2], 16);
    EXPECT_GE(components[3], 16);

    struct DummyConsumer : public BnConsumerListener {
        void onFrameAvailable(const BufferItem& /* item */) override {}
        void onBuffersReleased() override {}
        void onSidebandStreamChanged() override {}
    };

    sp<IGraphicBufferProducer> producer;
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&producer, &consumer);
    consumer->consumerConnect(new DummyConsumer, false);
    sp<Surface> mSTC = new Surface(producer);
    sp<ANativeWindow> mANW = mSTC;

    EGLSurface eglSurface = eglCreateWindowSurface(mEglDisplay, config, mANW.get(), NULL);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    ASSERT_NE(EGL_NO_SURFACE, eglSurface);

    EXPECT_TRUE(eglDestroySurface(mEglDisplay, eglSurface));
}

TEST_F(EGLTest, EGLNoConfigContext) {
    if (!hasWideColorDisplay) {
        // skip this test if device does not have wide-color display
        std::cerr << "[          ] Device does not support wide-color, test skipped" << std::endl;
        return;
    }

    ASSERT_TRUE(hasEglExtension(mEglDisplay, "EGL_KHR_no_config_context"));

    struct DummyConsumer : public BnConsumerListener {
        void onFrameAvailable(const BufferItem& /* item */) override {}
        void onBuffersReleased() override {}
        void onSidebandStreamChanged() override {}
    };

    std::vector<EGLint> contextAttributes;
    contextAttributes.reserve(4);
    contextAttributes.push_back(EGL_CONTEXT_CLIENT_VERSION);
    contextAttributes.push_back(2);
    contextAttributes.push_back(EGL_NONE);
    contextAttributes.push_back(EGL_NONE);

    EGLContext eglContext = eglCreateContext(mEglDisplay, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT,
                                             contextAttributes.data());
    EXPECT_NE(EGL_NO_CONTEXT, eglContext);
    EXPECT_EQ(EGL_SUCCESS, eglGetError());

    if (eglContext != EGL_NO_CONTEXT) {
        eglDestroyContext(mEglDisplay, eglContext);
    }
}

// Emulate what a native application would do to create a
// 10:10:10:2 surface.
TEST_F(EGLTest, EGLConfig1010102) {
    EGLint numConfigs;
    EGLConfig config;
    EGLBoolean success;

    if (!hasWideColorDisplay) {
        // skip this test if device does not have wide-color display
        std::cerr << "[          ] Device does not support wide-color, test skipped" << std::endl;
        return;
    }

    EGLint attrs[] = {
            // clang-format off
            EGL_SURFACE_TYPE,             EGL_WINDOW_BIT,
            EGL_RENDERABLE_TYPE,          EGL_OPENGL_ES2_BIT,
            EGL_SURFACE_TYPE,             EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
            EGL_RED_SIZE,                 10,
            EGL_GREEN_SIZE,               10,
            EGL_BLUE_SIZE,                10,
            EGL_ALPHA_SIZE,               2,
            EGL_COLOR_COMPONENT_TYPE_EXT, EGL_COLOR_COMPONENT_TYPE_FIXED_EXT,
            EGL_NONE,                     EGL_NONE
            // clang-format on
    };
    success = eglChooseConfig(mEglDisplay, attrs, &config, 1, &numConfigs);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(1, numConfigs);

    EGLint components[4];
    EGLint value;
    eglGetConfigAttrib(mEglDisplay, config, EGL_CONFIG_ID, &value);

    success = eglGetConfigAttrib(mEglDisplay, config, EGL_RED_SIZE, &components[0]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_GREEN_SIZE, &components[1]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_BLUE_SIZE, &components[2]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    success = eglGetConfigAttrib(mEglDisplay, config, EGL_ALPHA_SIZE, &components[3]);
    ASSERT_EQ(EGL_UNSIGNED_TRUE, success);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());

    EXPECT_EQ(components[0], 10);
    EXPECT_EQ(components[1], 10);
    EXPECT_EQ(components[2], 10);
    EXPECT_EQ(components[3], 2);

    struct DummyConsumer : public BnConsumerListener {
        void onFrameAvailable(const BufferItem& /* item */) override {}
        void onBuffersReleased() override {}
        void onSidebandStreamChanged() override {}
    };

    // Create a EGLSurface
    sp<IGraphicBufferProducer> producer;
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&producer, &consumer);
    consumer->consumerConnect(new DummyConsumer, false);
    sp<Surface> mSTC = new Surface(producer);
    sp<ANativeWindow> mANW = mSTC;

    EGLSurface eglSurface = eglCreateWindowSurface(mEglDisplay, config, mANW.get(), NULL);
    ASSERT_EQ(EGL_SUCCESS, eglGetError());
    ASSERT_NE(EGL_NO_SURFACE, eglSurface);

    EXPECT_TRUE(eglDestroySurface(mEglDisplay, eglSurface));
}
}
