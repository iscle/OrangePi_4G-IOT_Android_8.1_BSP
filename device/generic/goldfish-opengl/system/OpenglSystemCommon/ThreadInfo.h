/*
* Copyright (C) 2011 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
#ifndef _THREAD_INFO_H
#define _THREAD_INFO_H

#include "HostConnection.h"
#include <pthread.h>

#include <bionic_tls.h>
struct EGLContext_t;
struct HostConnection;

struct EGLThreadInfo
{
    EGLThreadInfo() : currentContext(NULL), hostConn(NULL), eglError(EGL_SUCCESS) { }

    EGLContext_t *currentContext;
    HostConnection *hostConn;
    int           eglError;
};


typedef bool (*tlsDtorCallback)(void*);
void setTlsDestructor(tlsDtorCallback);

extern "C" __attribute__((visibility("default"))) EGLThreadInfo *goldfish_get_egl_tls();

inline EGLThreadInfo* getEGLThreadInfo() {
#ifdef __ANDROID__
    EGLThreadInfo *tInfo =
        (EGLThreadInfo *)(((uintptr_t *)__get_tls())[TLS_SLOT_OPENGL]);
    if (!tInfo) {
        tInfo = goldfish_get_egl_tls();
        ((uintptr_t *)__get_tls())[TLS_SLOT_OPENGL] = (uintptr_t)tInfo;
    }
    return tInfo;
#else
    return goldfish_get_egl_tls();
#endif
}

#endif // of _THREAD_INFO_H
