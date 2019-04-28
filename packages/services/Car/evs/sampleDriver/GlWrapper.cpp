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

#include "GlWrapper.h"

#include <stdio.h>
#include <fcntl.h>
#include <sys/ioctl.h>

#include <ui/DisplayInfo.h>
#include <ui/GraphicBuffer.h>
#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>


using namespace android;


// TODO:  Consider dropping direct use of GraphicsBufferAllocator and Mapper?
using android::GraphicBuffer;
using android::GraphicBufferAllocator;
using android::GraphicBufferMapper;
using android::sp;


const char vertexShaderSource[] = ""
        "#version 300 es                    \n"
        "layout(location = 0) in vec4 pos;  \n"
        "layout(location = 1) in vec2 tex;  \n"
        "out vec2 uv;                       \n"
        "void main()                        \n"
        "{                                  \n"
        "   gl_Position = pos;              \n"
        "   uv = tex;                       \n"
        "}                                  \n";

const char pixelShaderSource[] =
        "#version 300 es                            \n"
        "precision mediump float;                   \n"
        "uniform sampler2D tex;                     \n"
        "in vec2 uv;                                \n"
        "out vec4 color;                            \n"
        "void main()                                \n"
        "{                                          \n"
        "    vec4 texel = texture(tex, uv);         \n"
        "    color = texel;                         \n"
        "}                                          \n";


static const char *getEGLError(void) {
    switch (eglGetError()) {
        case EGL_SUCCESS:
            return "EGL_SUCCESS";
        case EGL_NOT_INITIALIZED:
            return "EGL_NOT_INITIALIZED";
        case EGL_BAD_ACCESS:
            return "EGL_BAD_ACCESS";
        case EGL_BAD_ALLOC:
            return "EGL_BAD_ALLOC";
        case EGL_BAD_ATTRIBUTE:
            return "EGL_BAD_ATTRIBUTE";
        case EGL_BAD_CONTEXT:
            return "EGL_BAD_CONTEXT";
        case EGL_BAD_CONFIG:
            return "EGL_BAD_CONFIG";
        case EGL_BAD_CURRENT_SURFACE:
            return "EGL_BAD_CURRENT_SURFACE";
        case EGL_BAD_DISPLAY:
            return "EGL_BAD_DISPLAY";
        case EGL_BAD_SURFACE:
            return "EGL_BAD_SURFACE";
        case EGL_BAD_MATCH:
            return "EGL_BAD_MATCH";
        case EGL_BAD_PARAMETER:
            return "EGL_BAD_PARAMETER";
        case EGL_BAD_NATIVE_PIXMAP:
            return "EGL_BAD_NATIVE_PIXMAP";
        case EGL_BAD_NATIVE_WINDOW:
            return "EGL_BAD_NATIVE_WINDOW";
        case EGL_CONTEXT_LOST:
            return "EGL_CONTEXT_LOST";
        default:
            return "Unknown error";
    }
}


// Given shader source, load and compile it
static GLuint loadShader(GLenum type, const char *shaderSrc) {
    // Create the shader object
    GLuint shader = glCreateShader (type);
    if (shader == 0) {
        return 0;
    }

    // Load and compile the shader
    glShaderSource(shader, 1, &shaderSrc, nullptr);
    glCompileShader(shader);

    // Verify the compilation worked as expected
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        ALOGE("Error compiling shader\n");

        GLint size = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &size);
        if (size > 0)
        {
            // Get and report the error message
            char *infoLog = (char*)malloc(size);
            glGetShaderInfoLog(shader, size, nullptr, infoLog);
            ALOGE("  msg:\n%s\n", infoLog);
            free(infoLog);
        }

        glDeleteShader(shader);
        return 0;
    }

    return shader;
}


// Create a program object given vertex and pixels shader source
static GLuint buildShaderProgram(const char* vtxSrc, const char* pxlSrc) {
    GLuint program = glCreateProgram();
    if (program == 0) {
        ALOGE("Failed to allocate program object\n");
        return 0;
    }

    // Compile the shaders and bind them to this program
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, vtxSrc);
    if (vertexShader == 0) {
        ALOGE("Failed to load vertex shader\n");
        glDeleteProgram(program);
        return 0;
    }
    GLuint pixelShader = loadShader(GL_FRAGMENT_SHADER, pxlSrc);
    if (pixelShader == 0) {
        ALOGE("Failed to load pixel shader\n");
        glDeleteProgram(program);
        glDeleteShader(vertexShader);
        return 0;
    }
    glAttachShader(program, vertexShader);
    glAttachShader(program, pixelShader);

    // Link the program
    glLinkProgram(program);
    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked)
    {
        ALOGE("Error linking program.\n");
        GLint size = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &size);
        if (size > 0)
        {
            // Get and report the error message
            char *infoLog = (char*)malloc(size);
            glGetProgramInfoLog(program, size, nullptr, infoLog);
            ALOGE("  msg:  %s\n", infoLog);
            free(infoLog);
        }

        glDeleteProgram(program);
        glDeleteShader(vertexShader);
        glDeleteShader(pixelShader);
        return 0;
    }

    return program;
}


// Main entry point
bool GlWrapper::initialize() {
    //
    //  Create the native full screen window and get a suitable configuration to match it
    //
    status_t err;

    mFlinger = new SurfaceComposerClient();
    if (mFlinger == nullptr) {
        ALOGE("SurfaceComposerClient couldn't be allocated");
        return false;
    }
    err = mFlinger->initCheck();
    if (err != NO_ERROR) {
        ALOGE("SurfaceComposerClient::initCheck error: %#x", err);
        return false;
    }

    // Get main display parameters.
    sp <IBinder> mainDpy = SurfaceComposerClient::getBuiltInDisplay(
            ISurfaceComposer::eDisplayIdMain);
    DisplayInfo mainDpyInfo;
    err = SurfaceComposerClient::getDisplayInfo(mainDpy, &mainDpyInfo);
    if (err != NO_ERROR) {
        ALOGE("ERROR: unable to get display characteristics");
        return false;
    }

    if (mainDpyInfo.orientation != DISPLAY_ORIENTATION_0 &&
        mainDpyInfo.orientation != DISPLAY_ORIENTATION_180) {
        // rotated
        mWidth = mainDpyInfo.h;
        mHeight = mainDpyInfo.w;
    } else {
        mWidth = mainDpyInfo.w;
        mHeight = mainDpyInfo.h;
    }

    mFlingerSurfaceControl = mFlinger->createSurface(
            String8("Evs Display"), mWidth, mHeight,
            PIXEL_FORMAT_RGBX_8888, ISurfaceComposerClient::eOpaque);
    if (mFlingerSurfaceControl == nullptr || !mFlingerSurfaceControl->isValid()) {
        ALOGE("Failed to create SurfaceControl");
        return false;
    }
    mFlingerSurface = mFlingerSurfaceControl->getSurface();


    // Set up our OpenGL ES context associated with the default display
    mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (mDisplay == EGL_NO_DISPLAY) {
        ALOGE("Failed to get egl display");
        return false;
    }

    EGLint major = 3;
    EGLint minor = 0;
    if (!eglInitialize(mDisplay, &major, &minor)) {
        ALOGE("Failed to initialize EGL: %s", getEGLError());
        return false;
    }


    const EGLint config_attribs[] = {
            // Tag                  Value
            EGL_RED_SIZE,           8,
            EGL_GREEN_SIZE,         8,
            EGL_BLUE_SIZE,          8,
            EGL_DEPTH_SIZE,         0,
            EGL_NONE
    };

    // Pick the default configuration without constraints (is this good enough?)
    EGLConfig egl_config = {0};
    EGLint numConfigs = -1;
    eglChooseConfig(mDisplay, config_attribs, &egl_config, 1, &numConfigs);
    if (numConfigs != 1) {
        ALOGE("Didn't find a suitable format for our display window");
        return false;
    }

    // Create the EGL render target surface
    mSurface = eglCreateWindowSurface(mDisplay, egl_config, mFlingerSurface.get(), nullptr);
    if (mSurface == EGL_NO_SURFACE) {
        ALOGE("gelCreateWindowSurface failed.");
        return false;
    }

    // Create the EGL context
    // NOTE:  Our shader is (currently at least) written to require version 3, so this
    //        is required.
    const EGLint context_attribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    mContext = eglCreateContext(mDisplay, egl_config, EGL_NO_CONTEXT, context_attribs);
    if (mContext == EGL_NO_CONTEXT) {
        ALOGE("Failed to create OpenGL ES Context: %s", getEGLError());
        return false;
    }


    // Activate our render target for drawing
    if (!eglMakeCurrent(mDisplay, mSurface, mSurface, mContext)) {
        ALOGE("Failed to make the OpenGL ES Context current: %s", getEGLError());
        return false;
    }


    // Create the shader program for our simple pipeline
    mShaderProgram = buildShaderProgram(vertexShaderSource, pixelShaderSource);
    if (!mShaderProgram) {
        ALOGE("Failed to build shader program: %s", getEGLError());
        return false;
    }

    // Create a GL texture that will eventually wrap our externally created texture surface(s)
    glGenTextures(1, &mTextureMap);
    if (mTextureMap <= 0) {
        ALOGE("Didn't get a texture handle allocated: %s", getEGLError());
        return false;
    }


    return true;
}


void GlWrapper::shutdown() {

    // Drop our device textures
    if (mKHRimage != EGL_NO_IMAGE_KHR) {
        eglDestroyImageKHR(mDisplay, mKHRimage);
        mKHRimage = EGL_NO_IMAGE_KHR;
    }

    // Release all GL resources
    eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(mDisplay, mSurface);
    eglDestroyContext(mDisplay, mContext);
    eglTerminate(mDisplay);
    mSurface = EGL_NO_SURFACE;
    mContext = EGL_NO_CONTEXT;
    mDisplay = EGL_NO_DISPLAY;

    // Let go of our SurfaceComposer resources
    mFlingerSurface.clear();
    mFlingerSurfaceControl.clear();
    mFlinger.clear();
}


void GlWrapper::showWindow() {
    if (mFlingerSurfaceControl != nullptr) {
        SurfaceComposerClient::openGlobalTransaction();
        mFlingerSurfaceControl->setLayer(0x7FFFFFFF);     // always on top
        mFlingerSurfaceControl->show();
        SurfaceComposerClient::closeGlobalTransaction();
    }
}


void GlWrapper::hideWindow() {
    if (mFlingerSurfaceControl != nullptr) {
        SurfaceComposerClient::openGlobalTransaction();
        mFlingerSurfaceControl->hide();
        SurfaceComposerClient::closeGlobalTransaction();
    }
}


bool GlWrapper::updateImageTexture(const BufferDesc& buffer) {

    // If we haven't done it yet, create an "image" object to wrap the gralloc buffer
    if (mKHRimage == EGL_NO_IMAGE_KHR) {
        // create a temporary GraphicBuffer to wrap the provided handle
        sp<GraphicBuffer> pGfxBuffer = new GraphicBuffer(
                buffer.width,
                buffer.height,
                buffer.format,
                1,      /* layer count */
                buffer.usage,
                buffer.stride,
                const_cast<native_handle_t*>(buffer.memHandle.getNativeHandle()),
                false   /* keep ownership */
        );
        if (pGfxBuffer.get() == nullptr) {
            ALOGE("Failed to allocate GraphicsBuffer to wrap our native handle");
            return false;
        }


        // Get a GL compatible reference to the graphics buffer we've been given
        EGLint eglImageAttributes[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
        EGLClientBuffer cbuf = static_cast<EGLClientBuffer>(pGfxBuffer->getNativeBuffer());
// TODO:  If we pass in a context, we get "bad context" back
#if 0
        mKHRimage = eglCreateImageKHR(mDisplay, mContext,
                                      EGL_NATIVE_BUFFER_ANDROID, cbuf,
                                      eglImageAttributes);
#else
        mKHRimage = eglCreateImageKHR(mDisplay, EGL_NO_CONTEXT,
                                      EGL_NATIVE_BUFFER_ANDROID, cbuf,
                                      eglImageAttributes);
#endif
        if (mKHRimage == EGL_NO_IMAGE_KHR) {
            ALOGE("error creating EGLImage: %s", getEGLError());
            return false;
        }


        // Update the texture handle we already created to refer to this gralloc buffer
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mTextureMap);
        glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, static_cast<GLeglImageOES>(mKHRimage));

    }

    return true;
}


void GlWrapper::renderImageToScreen() {
    // Set the viewport
    glViewport(0, 0, mWidth, mHeight);

    // Clear the color buffer
    glClearColor(0.1f, 0.5f, 0.1f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    // Select our screen space simple texture shader
    glUseProgram(mShaderProgram);

    // Bind the texture and assign it to the shader's sampler
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mTextureMap);
    GLint sampler = glGetUniformLocation(mShaderProgram, "tex");
    glUniform1i(sampler, 0);

    // We want our image to show up opaque regardless of alpha values
    glDisable(GL_BLEND);


    // Draw a rectangle on the screen
    // TODO:  We pulled in from the edges for now for diagnostic purposes...
#if 0
    GLfloat vertsCarPos[] = { -1.0,  1.0, 0.0f,   // left top in window space
                               1.0,  1.0, 0.0f,   // right top
                              -1.0, -1.0, 0.0f,   // left bottom
                               1.0, -1.0, 0.0f    // right bottom
    };
#else
    GLfloat vertsCarPos[] = { -0.8,  0.8, 0.0f,   // left top in window space
                               0.8,  0.8, 0.0f,   // right top
                              -0.8, -0.8, 0.0f,   // left bottom
                               0.8, -0.8, 0.0f    // right bottom
    };
#endif
    // NOTE:  We didn't flip the image in the texture, so V=0 is actually the top of the image
    GLfloat vertsCarTex[] = { 0.0f, 0.0f,   // left top
                              1.0f, 0.0f,   // right top
                              0.0f, 1.0f,   // left bottom
                              1.0f, 1.0f    // right bottom
    };
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, vertsCarPos);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 0, vertsCarTex);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);


    // Clean up and flip the rendered result to the front so it is visible
    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);

    glFinish();

    eglSwapBuffers(mDisplay, mSurface);
}

