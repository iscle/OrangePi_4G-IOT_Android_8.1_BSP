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

#include <string.h>
#include "wprint_image.h"
#include "wprint_mupdf.h"

const image_decode_ifc_t *wprint_image_get_decode_ifc(wprint_image_info_t *image_info) {
    if ((image_info != NULL) && (image_info->mime_type != NULL)) {
        // Only PDF will be provided by upper layer
        if (strcasecmp(image_info->mime_type, MIME_TYPE_PDF) == 0) {
            return wprint_mupdf_decode_ifc;
        }
    }
    return NULL;
}

int wprint_image_init(wprint_image_info_t *image_info, const char *image_url, const int page_num) {
    if (image_info == NULL) return ERROR;

    image_info->decoder_data.urlPath = image_url;
    image_info->decoder_data.page = page_num;

    const image_decode_ifc_t *decode_ifc = wprint_image_get_decode_ifc(image_info);
    if ((decode_ifc != NULL) && (decode_ifc->init != NULL)) {
        decode_ifc->init(image_info);
        image_info->decode_ifc = decode_ifc;
        return OK;
    }
    return ERROR;
}