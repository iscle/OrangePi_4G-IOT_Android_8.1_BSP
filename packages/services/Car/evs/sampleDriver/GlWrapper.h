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

#ifndef ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_DISPLAY_GLWRAPPER_H
#define ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_DISPLAY_GLWRAPPER_H

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>

#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

#include <android/hardware/automotive/evs/1.0/types.h>


using ::android::sp;
using ::android::SurfaceComposerClient;
using ::android::SurfaceControl;
using ::android::Surface;
using ::android::hardware::automotive::evs::V1_0::BufferDesc;


class GlWrapper {
public:
    bool initialize();
    void shutdown();

    bool updateImageTexture(const BufferDesc& buffer);
    void renderImageToScreen();

    void showWindow();
    void hideWindow();

    unsigned getWidth()     { return mWidth; };
    unsigned getHeight()    { return mHeight; };

private:
    sp<SurfaceComposerClient>   mFlinger;
    sp<SurfaceControl>          mFlingerSurfaceControl;
    sp<Surface>                 mFlingerSurface;
    EGLDisplay                  mDisplay;
    EGLSurface                  mSurface;
    EGLContext                  mContext;

    unsigned mWidth  = 0;
    unsigned mHeight = 0;

    EGLImageKHR mKHRimage = EGL_NO_IMAGE_KHR;

    GLuint mTextureMap    = 0;
    GLuint mShaderProgram = 0;
};

#endif // ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_DISPLAY_GLWRAPPER_H
