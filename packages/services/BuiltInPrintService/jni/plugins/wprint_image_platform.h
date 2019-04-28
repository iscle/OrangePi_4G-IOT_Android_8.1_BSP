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

#ifdef __cplusplus
extern "C"
{
#endif

#ifdef __DEFINE_WPRINT_PLATFORM_TYPES__

#include <setjmp.h>

#ifndef TRUE
#define TRUE 1
#endif

#ifndef FALSE
#define FALSE 0
#endif

/*
 * Define general data used by image decoders
 */
typedef struct {
    const char *urlPath;
    unsigned int page;

    // PDF data
    struct {
        void *bitmap_ptr;
        void *fz_context_ptr;
        void *fz_doc_ptr;
        void *fz_page_ptr;
        void *fz_pixmap_ptr;
    } pdf_info;
} decoder_data_t;

#endif // __DEFINE_WPRINT_PLATFORM_TYPES__

#ifdef __DEFINE_WPRINT_PLATFORM_METHODS__
int wprint_image_init(wprint_image_info_t *image_info, const char *urlPath, int pageNum);
#endif // __DEFINE_WPRINT_PLATFORM_METHODS__

#ifdef __cplusplus
}
#endif