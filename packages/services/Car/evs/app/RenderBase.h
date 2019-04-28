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

#ifndef CAR_EVS_APP_RENDERBASE_H
#define CAR_EVS_APP_RENDERBASE_H

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>

#include <android/hardware/automotive/evs/1.0/IEvsEnumerator.h>

using namespace ::android::hardware::automotive::evs::V1_0;
using ::android::sp;


/*
 * Abstract base class for the workhorse classes that handle the user interaction and display for
 * each mode of the EVS application.
 */
class RenderBase {
public:
    virtual ~RenderBase() {};

    virtual bool activate() = 0;
    virtual void deactivate() = 0;

    virtual bool drawFrame(const BufferDesc& tgtBuffer) = 0;

protected:
    static bool prepareGL();

    static bool attachRenderTarget(const BufferDesc& tgtBuffer);
    static void detachRenderTarget();

    // OpenGL state shared among all renderers
    static EGLDisplay   sDisplay;
    static EGLContext   sContext;
    static EGLSurface   sDummySurface;
    static GLuint       sFrameBuffer;
    static GLuint       sColorBuffer;
    static GLuint       sDepthBuffer;

    static EGLImageKHR  sKHRimage;

    static unsigned     sWidth;
    static unsigned     sHeight;
    static float        sAspectRatio;
};


#endif //CAR_EVS_APP_RENDERBASE_H
