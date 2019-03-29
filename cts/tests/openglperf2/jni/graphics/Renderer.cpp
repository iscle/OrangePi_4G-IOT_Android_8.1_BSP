/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
#include "Renderer.h"
#include <graphics/GLUtils.h>

#define LOG_TAG "CTS_OPENGL"
#define LOG_NDEBUG 0
#include <android/log.h>

#include <Trace.h>

// Used to center the grid on the screen.
#define CENTER_GRID(x) ((((x) * 2.0 + 1) - OFFSCREEN_GRID_SIZE) / OFFSCREEN_GRID_SIZE)
// Leave a good error message if something fails.
#define EGL_RESULT_CHECK(X) do { \
                                   EGLint error = eglGetError(); \
                                   if (!(X) || error != EGL_SUCCESS) { \
                                       __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                                          "EGL error '%d' at %s:%d", error, __FILE__, __LINE__);\
                                       return false; \
                                    } \
                            } while (0)

static const int FBO_NUM_VERTICES = 6;

static const float FBO_VERTICES[FBO_NUM_VERTICES * 3] = {
        0.1f, 0.1f, -0.1f,
        -0.1f, 0.1f, -0.1f,
        -0.1f, -0.1f, -0.1f,
        -0.1f, -0.1f, -0.1f,
        0.1f, -0.1f, -0.1f,
        0.1f, 0.1f, -0.1f };
static const float FBO_TEX_COORDS[FBO_NUM_VERTICES * 2] = {
        1.0f, 1.0f,
        0.0f, 1.0f,
        0.0f, 0.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f };

static const char* FBO_VERTEX =
        "attribute vec4 a_Position;"
        "attribute vec2 a_TexCoord;"
        "uniform float u_XOffset;"
        "uniform float u_YOffset;"
        "varying vec2 v_TexCoord;"
        "void main() {"
        "  v_TexCoord = a_TexCoord;"
        "  gl_Position.x = a_Position.x + u_XOffset;"
        "  gl_Position.y = a_Position.y + u_YOffset;"
        "  gl_Position.zw = a_Position.zw;"
        "}";

static const char* FBO_FRAGMENT =
        "precision mediump float;"
        "uniform sampler2D u_Texture;"
        "varying vec2 v_TexCoord;"
        "void main() {"
        "  gl_FragColor = texture2D(u_Texture, v_TexCoord);"
        "}";

static const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE };

static const EGLint configAttribs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 16,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE };

static const int FBO_SIZE = 128;

Renderer::Renderer(EGLNativeWindowType window, bool offscreen) :
        mOffscreen(offscreen), mEglDisplay(EGL_NO_DISPLAY), mEglSurface(EGL_NO_SURFACE),
        mEglContext(EGL_NO_CONTEXT), mWindow(window)  {
}

bool Renderer::eglSetUp() {
    SCOPED_TRACE();
    mEglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGL_RESULT_CHECK(mEglDisplay != EGL_NO_DISPLAY);

    EGLint major;
    EGLint minor;
    EGL_RESULT_CHECK(eglInitialize(mEglDisplay, &major, &minor));

    EGLint numConfigs = 0;
    EGL_RESULT_CHECK(eglChooseConfig(mEglDisplay, configAttribs, &mGlConfig, 1, &numConfigs)
                     && (numConfigs > 0));

    mEglSurface = eglCreateWindowSurface(mEglDisplay, mGlConfig, mWindow, NULL);
    EGL_RESULT_CHECK(mEglSurface != EGL_NO_SURFACE);

    mEglContext = eglCreateContext(mEglDisplay, mGlConfig, EGL_NO_CONTEXT, contextAttribs);
    EGL_RESULT_CHECK(mEglContext != EGL_NO_CONTEXT);

    EGL_RESULT_CHECK(eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext));
    EGL_RESULT_CHECK(eglQuerySurface(mEglDisplay, mEglSurface, EGL_WIDTH, &mWidth));
    EGL_RESULT_CHECK(eglQuerySurface(mEglDisplay, mEglSurface, EGL_HEIGHT, &mHeight));

    return true;
}

void Renderer::eglTearDown() {
    SCOPED_TRACE();
    eglMakeCurrent(mEglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

    if (mEglContext != EGL_NO_CONTEXT) {
        eglDestroyContext(mEglDisplay, mEglContext);
        mEglContext = EGL_NO_CONTEXT;
    }

    if (mEglSurface != EGL_NO_SURFACE) {
        mEglSurface = EGL_NO_SURFACE;
    }

    if (mEglDisplay != EGL_NO_DISPLAY) {
        eglTerminate(mEglDisplay);
        mEglDisplay = EGL_NO_DISPLAY;
    }
}

bool Renderer::setUp(int /*workload*/) {
    SCOPED_TRACE();

    EGL_RESULT_CHECK(eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext));

    if (mOffscreen) {
        mFboWidth = FBO_SIZE;
        mFboHeight = FBO_SIZE;

        glGenFramebuffers(1, &mFboId);
        glBindFramebuffer(GL_FRAMEBUFFER, mFboId);

        glGenRenderbuffers(1, &mFboDepthId);
        glBindRenderbuffer(GL_RENDERBUFFER, mFboDepthId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, mFboWidth, mFboHeight);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                                  GL_RENDERBUFFER, mFboDepthId);

        mFboTexId = GLUtils::genTexture(mFboWidth, mFboHeight, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mFboTexId, 0);

        GLuint err = glGetError();
        if (err != GL_NO_ERROR) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "GLError %d", err);
            return false;
        }

        GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
           __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Framebuffer not complete: %d", status);
            return false;
        }
        // Create fbo program.
        mFboProgId = GLUtils::createProgram(&FBO_VERTEX, &FBO_FRAGMENT);
        if (mFboProgId == 0) {
            return false;
        }
        // Bind attributes.
        mFboTexUniformHandle = glGetUniformLocation(mFboProgId, "u_Texture");
        mFboXOffsetUniformHandle = glGetUniformLocation(mFboProgId, "u_XOffset");
        mFboYOffsetUniformHandle = glGetUniformLocation(mFboProgId, "u_YOffset");
        mFboPositionHandle = glGetAttribLocation(mFboProgId, "a_Position");
        mFboTexCoordHandle = glGetAttribLocation(mFboProgId, "a_TexCoord");
    } else {
        mFboWidth = 0;
        mFboHeight = 0;
        mFboId = 0;
        mFboDepthId = 0;
        mFboTexId = 0;
    }

    GLuint err = glGetError();
    if (err != GL_NO_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "GLError %d in setUp", err);
        return false;
    }

    return true;
}

bool Renderer::tearDown() {
    SCOPED_TRACE();
    if (mOffscreen) {
        if (mFboId != 0) {
            glDeleteFramebuffers(1, &mFboId);
            mFboId = 0;
        }
        if (mFboDepthId != 0) {
            glDeleteRenderbuffers(1, &mFboDepthId);
            mFboDepthId = 0;
        }
        if (mFboTexId != 0) {
            glDeleteTextures(1, &mFboTexId);
            mFboTexId = 0;
        }
    }
    GLuint err = glGetError();
    if (err != GL_NO_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "GLError %d in tearDown", err);
        return false;
    }

    return true;
}

bool Renderer::draw() {
    SCOPED_TRACE();

    EGL_RESULT_CHECK(eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext));

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, mWidth, mHeight);

    if (mOffscreen) {
        // Set the background clear color to black.
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
        for (int i = 0; i < OFFSCREEN_INNER_FRAMES; i++) {
            // Switch to FBO and re-attach.
            glBindFramebuffer(GL_FRAMEBUFFER, mFboId);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                                    GL_RENDERBUFFER, mFboDepthId);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                                 GL_TEXTURE_2D, mFboTexId, 0);
            glViewport(0, 0, mFboWidth, mFboHeight);

            // Render workload.
            drawWorkload();
            glFlush();

            // Switch back to display.
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, mWidth, mHeight);

            // No culling of back faces
            glDisable (GL_CULL_FACE);
            // No depth testing
            glDisable (GL_DEPTH_TEST);
            // No blending
            glDisable (GL_BLEND);

            glUseProgram(mFboProgId);

            // Set the texture.
            glActiveTexture (GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, mFboTexId);
            glUniform1i(mFboTexUniformHandle, 0);

            // Set the offsets
            glUniform1f(mFboXOffsetUniformHandle, CENTER_GRID(i / OFFSCREEN_GRID_SIZE));
            glUniform1f(mFboYOffsetUniformHandle, CENTER_GRID(i % OFFSCREEN_GRID_SIZE));

            glEnableVertexAttribArray(mFboPositionHandle);
            glEnableVertexAttribArray(mFboTexCoordHandle);
            glVertexAttribPointer(mFboPositionHandle, 3, GL_FLOAT, false, 0, FBO_VERTICES);
            glVertexAttribPointer(mFboTexCoordHandle, 2, GL_FLOAT, false, 0, FBO_TEX_COORDS);

            // Render FBO to display.
            glDrawArrays(GL_TRIANGLES, 0, FBO_NUM_VERTICES);
        }
    } else {
        // Render workload.
        drawWorkload();
    }

    GLuint err = glGetError();
    if (err != GL_NO_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "GLError %d in draw", err);
        return false;
    }

    EGL_RESULT_CHECK(eglSwapBuffers(mEglDisplay, mEglSurface)); 
    return true;
}
