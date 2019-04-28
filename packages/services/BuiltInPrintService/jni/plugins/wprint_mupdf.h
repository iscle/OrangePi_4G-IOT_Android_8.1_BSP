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

#ifndef __WPRINT_MUPDF__
#define __WPRINT_MUPDF__

#include <jni.h>
#include "wprint_image.h"

extern const image_decode_ifc_t *wprint_mupdf_decode_ifc;

typedef struct pdf_render_ifc pdf_render_ifc_t;

/*
 * Defines an interface for inspecting and rendering PDFs. Note: all methods must be called
 * from the same thread that created the interface.
 */
struct pdf_render_ifc {
    /*
     * Return the page count for the specified file, or 0 on failure.
     */
    int (*openDocument)(pdf_render_ifc_t *self, const char *fileName);

    /*
     * Render a page (1-based) at the specified zoom level into the supplied output buffer. The
     * buffer must be large enough to contain width * height * 3 (RGB). Returns success.
     */
    status_t (*renderPageStripe)(pdf_render_ifc_t *self, int page, int width,
            int height, float zoom, char *buffer);

    /*
     * Determine the width and height of a particular page (1-based), returning success.
     */
    status_t (*getPageAttributes)(pdf_render_ifc_t *self, int page,
            double *width, double *height);

    /*
     * Finally release all resources related to this interface
     */
    void (*destroy)(pdf_render_ifc_t *self);
};

/*
 * One-time initialization of pdf_render module. pdf_render_deinit must be called later to
 * free resources.
 */
void pdf_render_init(JNIEnv *env);

/*
 * Deinitialize the pdf_render module (at shutdown)
 */
void pdf_render_deinit(JNIEnv *env);

/*
 * Allocate and return a thread-specific pdf_render interface. Caller must eventually
 * call destroy on this instance.
 */
pdf_render_ifc_t *create_pdf_render_ifc();

#endif // __WPRINT_MUPDF__