/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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
#include <jni.h>
#include <malloc.h>
#include "wprint_mupdf.h"
#include "wprint_debug.h"

#define TAG "pdf_render"

/* Global reference to JVM */
extern JavaVM *_JVM;

/* Local data associated with pdf_render_st instances */
typedef struct pdf_render_st {
    /* Public interface. Must be first. */
    pdf_render_ifc_t ifc;

    /* JNI environment */
    JNIEnv *env;

    /* true if the env was created for this thread */
    bool needDetach;

    /* Reference to associated PdfRender object */
    jobject obj;
} pdf_render_st_t;

static jclass gPdfRenderClass;
static jmethodID gPdfRenderOpenDocument, gPdfRenderGetPageSize, gPdfRenderRenderPageStripe;
static jclass gSizeDClass;
static jmethodID gSizeDGetHeight, gSizeDGetWidth;

static int openDocument(pdf_render_ifc_t *obj, const char *fileName) {
    LOGD("getPageCount %p %s", obj, fileName);
    if (!gPdfRenderClass) return ERROR;

    pdf_render_st_t *self = (pdf_render_st_t *) obj;
    jstring fileNameString = (*self->env)->NewStringUTF(self->env, fileName);
    int count = (*self->env)->CallIntMethod(self->env, self->obj, gPdfRenderOpenDocument,
            fileNameString);
    LOGD("getPageCount %p %s returning %d", obj, fileName, count);
    return count;
}

static int getPageAttributes(pdf_render_ifc_t *obj, int page, double *width, double *height) {
    LOGD("getPageAttributes %p %d", obj, page);
    if (!gPdfRenderClass) return ERROR;

    pdf_render_st_t *self = (pdf_render_st_t *) obj;

    jobject size = (*self->env)->CallObjectMethod(self->env, self->obj, gPdfRenderGetPageSize,
            page);
    if (size == NULL) return ERROR;

    // Extract width/height and return them
    *width = (double) (*self->env)->CallDoubleMethod(self->env, size, gSizeDGetWidth);
    *height = (double) (*self->env)->CallDoubleMethod(self->env, size, gSizeDGetHeight);
    return OK;
}

static int renderPageStripe(pdf_render_ifc_t *obj, int page, int width, int height, float zoom,
        char *buffer) {
    LOGD("renderPageStripe %p %d", obj, page);
    if (!gPdfRenderClass) return ERROR;

    pdf_render_st_t *self = (pdf_render_st_t *) obj;

    int bufferSize = width * height * 3;
    jobject byteBuffer = (*self->env)->NewDirectByteBuffer(self->env, buffer, bufferSize);

    if (!(*self->env)->CallBooleanMethod(self->env, self->obj, gPdfRenderRenderPageStripe, page,
            0, width, height, (double) zoom, byteBuffer)) {
        return ERROR;
    }

    (*self->env)->DeleteLocalRef(self->env, byteBuffer);
    return OK;
}

static void destroy(pdf_render_ifc_t *obj) {
    LOGD("destroy %p", obj);
    pdf_render_st_t *self = (pdf_render_st_t *) obj;

    (*self->env)->DeleteGlobalRef(self->env, self->obj);

    if (self->needDetach) {
        (*_JVM)->DetachCurrentThread(_JVM);
    }

    free(self);
}

void pdf_render_init(JNIEnv *env) {
    LOGD("pdf_render_init");

    /* Lock down global class references and look up method IDs */
    gPdfRenderClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env,
            "com/android/bips/jni/PdfRender"));
    gPdfRenderOpenDocument = (*env)->GetMethodID(env, gPdfRenderClass, "openDocument",
            "(Ljava/lang/String;)I");
    gPdfRenderGetPageSize = (*env)->GetMethodID(env, gPdfRenderClass, "getPageSize",
            "(I)Lcom/android/bips/jni/SizeD;");
    gPdfRenderRenderPageStripe = (*env)->GetMethodID(env, gPdfRenderClass, "renderPageStripe",
            "(IIIIDLjava/nio/ByteBuffer;)Z");

    gSizeDClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/android/bips/jni/SizeD"));
    gSizeDGetWidth = (*env)->GetMethodID(env, gSizeDClass, "getWidth", "()D");
    gSizeDGetHeight = (*env)->GetMethodID(env, gSizeDClass, "getHeight", "()D");
}

void pdf_render_deinit(JNIEnv *env) {
    LOGD("pdf_render_deinit");
    (*env)->DeleteGlobalRef(env, gPdfRenderClass);
    (*env)->DeleteGlobalRef(env, gSizeDClass);
    gPdfRenderClass = 0;
}

pdf_render_ifc_t *create_pdf_render_ifc() {
    LOGD("create_pdf_render_ifc");

    pdf_render_st_t *self;

    // Set up the interface
    self = (pdf_render_st_t *) malloc(sizeof(pdf_render_st_t));
    if (!self) return NULL;

    self->ifc.openDocument = openDocument;
    self->ifc.getPageAttributes = getPageAttributes;
    self->ifc.renderPageStripe = renderPageStripe;
    self->ifc.destroy = destroy;

    // Get the environment
    jint result = (*_JVM)->GetEnv(_JVM, (void **) &self->env, JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        self->needDetach = true;
        if ((*_JVM)->AttachCurrentThread(_JVM, &self->env, NULL) < 0) {
            LOGE("AttachCurrentThread failed");
            free(self);
            return NULL;
        }
    } else {
        self->needDetach = false;
    }

    // Get the object
    jmethodID methodId = (*self->env)->GetStaticMethodID(self->env, gPdfRenderClass, "getInstance",
            "(Landroid/content/Context;)Lcom/android/bips/jni/PdfRender;");
    jobject instance = (*self->env)->CallStaticObjectMethod(self->env, gPdfRenderClass, methodId,
            NULL);
    self->obj = (*self->env)->NewGlobalRef(self->env, instance);

    return &self->ifc;
}