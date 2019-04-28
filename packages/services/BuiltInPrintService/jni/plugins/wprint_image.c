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

#include <stdlib.h>
#include <math.h>
#include "wprint_image.h"
#include "lib_wprint.h"

#define TAG "wprint_image"
#define MIN_DECODE_MEM (1 * 1024 * 1024)
#define MAX_DECODE_MEM (4 * 1024 * 1024)

void wprint_image_setup(wprint_image_info_t *image_info, const char *mime_type,
        const ifc_wprint_t *wprint_ifc, unsigned int output_resolution,
        int pdf_render_resolution) {
    if (image_info != NULL) {
        LOGD("image_setup");
        memset(image_info, 0, sizeof(wprint_image_info_t));
        image_info->wprint_ifc = wprint_ifc;
        image_info->mime_type = mime_type;
        image_info->print_resolution = output_resolution;
        image_info->pdf_render_resolution = pdf_render_resolution;
    }
}

status_t wprint_image_get_info(FILE *imgfile, wprint_image_info_t *image_info) {
    if (image_info == NULL) return ERROR;

    image_info->imgfile = imgfile;
    image_info->rotation = ROT_0;
    image_info->swath_start = -1;
    image_info->rows_cached = 0;
    image_info->output_cache = NULL;
    image_info->output_swath_start = -1;
    image_info->scaled_sample_size = 1;

    image_info->stripe_height = 0;
    image_info->unscaled_rows = NULL;
    image_info->unscaled_rows_needed = 0;
    image_info->mixed_memory = NULL;
    image_info->mixed_memory_needed = 0;
    image_info->scaled_width = -1;
    image_info->scaled_height = -1;
    image_info->unscaled_start_row = -1;
    image_info->unscaled_end_row = -1;
    image_info->scaling_needed = FALSE;

    image_info->output_padding_top = 0;
    image_info->output_padding_left = 0;
    image_info->output_padding_right = 0;
    image_info->output_padding_bottom = 0;

    const image_decode_ifc_t *decode_ifc = image_info->decode_ifc;

    if ((decode_ifc != NULL) && (decode_ifc->get_hdr != NULL)) {
        if (OK == decode_ifc->get_hdr(image_info)) {
            LOGI("wprint_image_get_info(): %s dim = %dx%d", image_info->mime_type,
                    image_info->width, image_info->height);
            return OK;
        } else {
            LOGE("ERROR: get_hdr for %s", image_info->mime_type);
            return ERROR;
        }
    }
    LOGE("Unsupported image type: %s", image_info->mime_type);
    return ERROR;
}

status_t wprint_image_set_output_properties(wprint_image_info_t *image_info,
        wprint_rotation_t rotation, unsigned int printable_width, unsigned int printable_height,
        unsigned int top_margin, unsigned int left_margin, unsigned int right_margin,
        unsigned int bottom_margin, unsigned int render_flags, unsigned int max_decode_stripe,
        unsigned int concurrent_stripes, unsigned int padding_options) {
    // validate rotation
    switch (rotation) {
        default:
            rotation = ROT_0;
        case ROT_0:
        case ROT_90:
        case ROT_180:
        case ROT_270:
            break;
    }

    // rotate margins
    switch (rotation) {
        case ROT_90:
        case ROT_270: {
            unsigned int temp;
            temp = top_margin;
            top_margin = left_margin;
            left_margin = bottom_margin;
            bottom_margin = right_margin;
            right_margin = temp;
            break;
        }
        default:
            break;
    }

    unsigned int input_render_flags = render_flags;

    // store padding options
    image_info->padding_options = (padding_options & PAD_ALL);

    // store margin adjusted printable area
    image_info->printable_width = printable_width - (left_margin + right_margin);
    image_info->printable_height = printable_height - (top_margin + bottom_margin);

    // store rendering parameters
    image_info->render_flags = render_flags;
    image_info->output_rows = max_decode_stripe;
    image_info->stripe_height = max_decode_stripe;
    image_info->concurrent_stripes = concurrent_stripes;

    // free data just in case
    if (image_info->unscaled_rows != NULL) {
        free(image_info->unscaled_rows);
    }

    // free data just in case
    if (image_info->mixed_memory != NULL) {
        free(image_info->mixed_memory);
    }

    image_info->row_offset = 0;
    image_info->col_offset = 0;
    image_info->scaled_sample_size = 1;
    image_info->scaled_width = -1;
    image_info->scaled_height = -1;
    image_info->unscaled_start_row = -1;
    image_info->unscaled_end_row = -1;
    image_info->unscaled_rows = NULL;
    image_info->unscaled_rows_needed = 0;
    image_info->mixed_memory = NULL;
    image_info->mixed_memory_needed = 0;
    image_info->rotation = rotation;

    unsigned int image_output_width;
    unsigned int image_output_height;

    // save margins
    switch (image_info->rotation) {
        case ROT_180:
        case ROT_270:
            image_info->output_padding_top = bottom_margin;
            image_info->output_padding_left = right_margin;
            image_info->output_padding_right = left_margin;
            image_info->output_padding_bottom = top_margin;
            break;
        case ROT_0:
        case ROT_90:
        default:
            image_info->output_padding_top = top_margin;
            image_info->output_padding_left = left_margin;
            image_info->output_padding_right = right_margin;
            image_info->output_padding_bottom = bottom_margin;
            break;
    }

    // swap dimensions
    switch (image_info->rotation) {
        case ROT_90:
        case ROT_270:
            image_output_width = image_info->height;
            image_output_height = image_info->width;
            break;
        case ROT_0:
        case ROT_180:
        default:
            image_output_width = image_info->width;
            image_output_height = image_info->height;
            break;
    }

    int native_units = 0;

    const image_decode_ifc_t *decode_ifc = image_info->decode_ifc;
    if ((image_info->render_flags & RENDER_FLAG_AUTO_FIT) && (decode_ifc != NULL) &&
            (decode_ifc->native_units != NULL)) {
        native_units = decode_ifc->native_units(image_info);
    }

    if (native_units <= 0) {
        native_units = image_info->print_resolution;
    }

    float native_scaling = 1.0f;
    unsigned int native_image_output_width = image_output_width;
    unsigned int native_image_output_height = image_output_height;

    if ((native_units != image_info->print_resolution)
            && !((image_info->render_flags & RENDER_FLAG_AUTO_SCALE)
                    || ((image_info->render_flags & RENDER_FLAG_AUTO_FIT)
                            && !(image_info->render_flags & RENDER_FLAG_DOCUMENT_SCALING)))) {
        native_scaling = (image_info->print_resolution * 1.0f) / (native_units * 1.0f);
        native_image_output_width = (int) floorf(image_output_width * native_scaling);
        native_image_output_height = (int) floorf(image_output_height * native_scaling);
        LOGD("need to do native scaling by %f factor to size %dx%d", native_scaling,
                native_image_output_width, native_image_output_height);
    }

    // if we have to scale determine if we can use subsampling to scale down
    if ((image_info->render_flags & (RENDER_FLAG_AUTO_SCALE | RENDER_FLAG_AUTO_FIT)) &&
            (native_scaling == 1.0f)) {
        LOGD("calculating subsampling");

        /*
         * Find a subsampling scale factor that produces an image that is still bigger
         * than the printable area and then finish scaling later using the fine-scaler.
         * This produces better quality than subsampling to a smaller size and scaling up.
         */
        image_info->scaled_sample_size = 1;
        if ((decode_ifc != NULL) && (decode_ifc->supports_subsampling(image_info) == OK)) {
            // subsampling supported
            int next_width, next_height;
            next_width = image_output_width >> 1;
            next_height = image_output_height >> 1;
            while (((image_info->render_flags & RENDER_FLAG_AUTO_SCALE) &&
                    (next_width > image_info->printable_width) &&
                    (next_height > image_info->printable_height)) ||
                    ((image_info->render_flags & RENDER_FLAG_AUTO_FIT) &&
                            ((next_width > image_info->printable_width) ||
                                    (next_height > image_info->printable_height)))) {
                image_info->scaled_sample_size <<= 1;
                next_width >>= 1;
                next_height >>= 1;
            }
        }

        LOGD("calculated sample size: %d", image_info->scaled_sample_size);

        // are we dong any subsampling?
        if (image_info->scaled_sample_size > 1) {
            // force the decoder to close and reopen with the new sample size setting
            decode_ifc->cleanup(image_info);
            decode_ifc->get_hdr(image_info);

            // update the output size
            image_output_width /= image_info->scaled_sample_size;
            image_output_height /= image_info->scaled_sample_size;
        }

        /*
         * have we reached our target size with subsampling?
         * if so disable further scaling
         */
        // check if width matches and height meets criteria
        if ((image_output_width == image_info->printable_width) &&
                (((image_info->render_flags & RENDER_FLAG_AUTO_SCALE) &&
                        (image_output_height >= image_info->printable_height)) ||
                        ((image_info->render_flags & RENDER_FLAG_AUTO_FIT) &&
                                (image_output_height < image_info->printable_height)))) {
            LOGD("disabling fine scaling since width matches and height meets criteria");
            image_info->render_flags &= ~(RENDER_FLAG_AUTO_SCALE | RENDER_FLAG_AUTO_FIT);
        } else if ((image_output_height == image_info->printable_height) &&
                (((image_info->render_flags & RENDER_FLAG_AUTO_SCALE) &&
                        (image_output_width >= image_info->printable_width)) ||
                        ((image_info->render_flags & RENDER_FLAG_AUTO_FIT) &&
                                (image_output_width < image_info->printable_width)))) {
            // height matches and width meets criteria
            LOGD("disabling fine scaling since height matches and width meets criteria");
            image_info->render_flags &= ~(RENDER_FLAG_AUTO_SCALE | RENDER_FLAG_AUTO_FIT);
        }

        if ((image_info->render_flags & RENDER_FLAG_DOCUMENT_SCALING)
                && (image_output_height <= image_info->printable_height)
                && (image_output_width <= image_info->printable_width)) {
            image_info->render_flags &= ~(RENDER_FLAG_AUTO_SCALE | RENDER_FLAG_AUTO_FIT);
        }
    } else if ((native_scaling != 1.0f) &&
            (image_info->render_flags & RENDER_FLAG_DOCUMENT_SCALING)) {
        LOGD("checking native document scaling factor");
        if ((native_image_output_height <= image_info->printable_height)
                && (native_image_output_width <= image_output_width
                        <= image_info->printable_width)) {
            LOGD("fit in printable area, just scale to native units");
            image_info->render_flags &= ~(RENDER_FLAG_AUTO_SCALE | RENDER_FLAG_AUTO_FIT);
        } else {
            LOGD("we don't fit in printable area, continue with fit-to-page");
            native_scaling = 1.0f;
        }
    }

    // store the subsampled dimensions
    image_info->sampled_width = (image_info->width / image_info->scaled_sample_size);
    image_info->sampled_height = (image_info->height / image_info->scaled_sample_size);

    // do we have any additional scaling to do?
    if ((image_info->render_flags & (RENDER_FLAG_AUTO_SCALE | RENDER_FLAG_AUTO_FIT))
            || (native_scaling != 1.0f)) {
        LOGD("calculating fine-scaling");
        int i;
        float targetHeight, targetWidth;
        float sourceHeight, sourceWidth;
        float rw;
        int useHeight;

        sourceWidth = image_output_width * 1.0f;
        sourceHeight = image_output_height * 1.0f;

        if (image_info->render_flags & (RENDER_FLAG_AUTO_SCALE | RENDER_FLAG_AUTO_FIT)) {
            targetHeight = image_info->printable_height * 1.0f;
            targetWidth = image_info->printable_width * 1.0f;

            // determine what our bounding edge is
            rw = (targetHeight * sourceWidth) / sourceHeight;
            if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
                useHeight = (rw >= targetWidth);
            } else {
                useHeight = (rw < targetWidth);
            }

            // determine the scaling factor
            if (useHeight) {
                image_info->scaled_width = (int) floorf(rw);
                image_info->scaled_height = (int) floorf(targetHeight);
            } else {
                image_info->scaled_height = (int) floorf(targetWidth * sourceHeight / sourceWidth);
                image_info->scaled_width = (int) floorf(targetWidth);
            }
        } else {
            image_info->scaled_height = native_image_output_height;
            image_info->scaled_width = native_image_output_width;
        }
        image_info->scaling_needed = TRUE;

        /*
         * setup the fine-scaler
         * we use rotated image_output_width rather than the pre-rotated sampled_width
         */
        scaler_make_image_scaler_tables(image_output_width, BYTES_PER_PIXEL(image_output_width),
                image_info->scaled_width, BYTES_PER_PIXEL(image_info->scaled_width),
                image_output_height, image_info->scaled_height, &image_info->scaler_config);

        image_info->unscaled_rows_needed = 0;
        image_info->mixed_memory_needed = 0;

        // calculate memory requirements
        for (i = 0; i < image_info->printable_height; i += max_decode_stripe) {
            uint16 row;
            uint16 row_start, row_end, gen_rows, row_offset;
            uint32 mixed;
            row = i;
            if (row >= image_info->scaled_height) {
                break;
            }
            scaler_calculate_scaling_rows(row,
                    MIN((row + (max_decode_stripe - 1)),
                            (image_info->scaled_height - 1)),
                    (void *) &image_info->scaler_config,
                    &row_start, &row_end, &gen_rows,
                    &row_offset, &mixed);

            image_info->output_rows = MAX(image_info->output_rows, gen_rows);
            image_info->unscaled_rows_needed = MAX(image_info->unscaled_rows_needed,
                    ((row_end - row_start) + 3));
            image_info->mixed_memory_needed = MAX(image_info->mixed_memory_needed, mixed);
        }
        int unscaled_size = BYTES_PER_PIXEL(
                (MAX(image_output_width, image_output_height) * image_info->unscaled_rows_needed));

        // allocate memory required for scaling
        image_info->unscaled_rows = malloc(unscaled_size);

        if (image_info->unscaled_rows != NULL) {
            memset(image_info->unscaled_rows, 0xff, unscaled_size);
        }
        image_info->mixed_memory = (image_info->mixed_memory_needed != 0) ? malloc(
                image_info->mixed_memory_needed) : NULL;
    } else {
        image_info->scaled_height = image_output_height;
        image_info->scaled_width = image_output_width;
    }

    // final calculations
    if ((image_info->render_flags & (RENDER_FLAG_AUTO_SCALE | RENDER_FLAG_AUTO_FIT)) ||
            image_info->scaling_needed) {
        /* use the full image size since both of the dimensions could be greater than
         * the printable area */
        image_info->output_width = image_output_width;
        image_info->output_height = image_output_height;
    } else {
        // clip the image within the printable area
        image_info->output_width = MIN(image_info->printable_width, image_output_width);
        image_info->output_height = MIN(image_info->printable_height, image_output_height);
    }

    int delta;
    switch (image_info->rotation) {
        case ROT_90:
            if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
            } else if (image_info->render_flags & RENDER_FLAG_CENTER_HORIZONTAL) {
                if (image_info->scaled_width > image_info->printable_width) {
                    image_info->col_offset = (
                            (image_info->scaled_width - image_info->printable_width) / 2);
                } else {
                    int paddingDelta = (image_info->printable_width - image_info->scaled_width);
                    delta = paddingDelta / 2;
                    image_info->output_padding_left += delta;
                    image_info->output_padding_right += delta + (paddingDelta & 0x1);
                }
            } else if (image_info->scaled_width > image_info->printable_width) {
                image_info->col_offset = (image_info->scaled_width - image_info->printable_width);
            } else if (image_info->scaled_width < image_info->printable_width) {
                image_info->output_padding_right += (image_info->printable_width -
                        image_info->scaled_width);
            }

            if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
            } else if (image_info->render_flags & RENDER_FLAG_CENTER_VERTICAL) {
                if (image_info->scaled_height > image_info->printable_height) {
                    image_info->row_offset = (
                            (image_info->scaled_height - image_info->printable_height) / 2);
                } else {
                    int paddingDelta = (image_info->printable_height - image_info->scaled_height);
                    delta = paddingDelta / 2;
                    image_info->output_padding_top += delta;
                    image_info->output_padding_bottom += delta + (paddingDelta & 0x1);
                }
            } else if (image_info->scaled_height < image_info->printable_height) {
                image_info->output_padding_bottom += (image_info->printable_height -
                        image_info->scaled_height);
            }
            break;
        case ROT_180:
            if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
            } else if (image_info->render_flags & RENDER_FLAG_CENTER_HORIZONTAL) {
                if (image_info->scaled_width > image_info->printable_width) {
                    image_info->col_offset = (
                            (image_info->scaled_width - image_info->printable_width) / 2);
                } else {
                    int paddingDelta = (image_info->printable_width - image_info->scaled_width);
                    delta = paddingDelta / 2;
                    image_info->output_padding_left += delta;
                    image_info->output_padding_right += delta + (paddingDelta & 0x1);
                }
            } else if (image_info->scaled_width > image_info->printable_width) {
                image_info->col_offset = (image_info->scaled_width - image_info->printable_width);
            } else if (image_info->scaled_width < image_info->printable_width) {
                image_info->output_padding_left += (image_info->printable_width -
                        image_info->scaled_width);
            }

            if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
            } else if (image_info->render_flags & RENDER_FLAG_CENTER_VERTICAL) {
                if (image_info->scaled_height > image_info->printable_height) {
                    image_info->row_offset = (
                            (image_info->scaled_height - image_info->printable_height) / 2);
                } else {
                    int paddingDelta = (image_info->printable_height - image_info->scaled_height);
                    delta = paddingDelta / 2;
                    image_info->output_padding_top += delta;
                    image_info->output_padding_bottom += delta + (paddingDelta & 0x1);
                }
            } else if (image_info->scaled_height > image_info->printable_height) {
                image_info->row_offset = (image_info->scaled_height - image_info->printable_height);
            } else if (image_info->scaled_height < image_info->printable_height) {
                image_info->output_padding_top += (image_info->printable_height -
                        image_info->scaled_height);
            }
            break;
        case ROT_270:
            if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
            } else if (image_info->render_flags & RENDER_FLAG_CENTER_HORIZONTAL) {
                if (image_info->scaled_width > image_info->printable_width) {
                    image_info->col_offset = (
                            (image_info->scaled_width - image_info->printable_width) / 2);
                } else {
                    int paddingDelta = (image_info->printable_width - image_info->scaled_width);
                    delta = paddingDelta / 2;
                    image_info->output_padding_left += delta;
                    image_info->output_padding_right += delta + (paddingDelta & 0x1);
                }
            } else if (image_info->scaled_width > image_info->printable_width) {
                image_info->col_offset = (image_info->scaled_width - image_info->printable_width);
            } else if (image_info->scaled_width < image_info->printable_width) {
                image_info->output_padding_left += (image_info->printable_width -
                        image_info->scaled_width);
            }

            if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
            } else if (image_info->render_flags & RENDER_FLAG_CENTER_VERTICAL) {
                if (image_info->scaled_height > image_info->printable_height) {
                    image_info->row_offset = (
                            (image_info->scaled_height - image_info->printable_height) / 2);
                } else {
                    int paddingDelta = (image_info->printable_height - image_info->scaled_height);
                    delta = paddingDelta / 2;
                    image_info->output_padding_top += delta;
                    image_info->output_padding_bottom += delta + (paddingDelta & 0x1);
                }
            } else if (image_info->scaled_height < image_info->printable_height) {
                image_info->output_padding_top += (image_info->printable_height -
                        image_info->scaled_height);
            } else if (image_info->scaled_height > image_info->printable_height) {
                image_info->row_offset = (image_info->scaled_height - image_info->printable_height);
            }
            break;
        case ROT_0:
        default:
            if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
            } else if (image_info->render_flags & RENDER_FLAG_CENTER_HORIZONTAL) {
                if (image_info->scaled_width > image_info->printable_width) {
                    image_info->col_offset = (
                            (image_info->scaled_width - image_info->printable_width) / 2);
                } else {
                    int paddingDelta = (image_info->printable_width - image_info->scaled_width);
                    delta = paddingDelta / 2;
                    image_info->output_padding_left += delta;
                    image_info->output_padding_right += delta + (paddingDelta & 0x1);
                }
            } else if (image_info->scaled_width < image_info->printable_width) {
                image_info->output_padding_right += (image_info->printable_width -
                        image_info->scaled_width);
            }

            if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
            } else if (image_info->render_flags & RENDER_FLAG_CENTER_VERTICAL) {
                if (image_info->scaled_height > image_info->printable_height) {
                    image_info->row_offset = (
                            (image_info->scaled_height - image_info->printable_height) / 2);
                } else {
                    int paddingDelta = (image_info->printable_height - image_info->scaled_height);
                    delta = paddingDelta / 2;
                    image_info->output_padding_top += delta;
                    image_info->output_padding_bottom += delta + (paddingDelta & 0x1);
                }
            } else if (image_info->scaled_height < image_info->printable_height) {
                image_info->output_padding_bottom += (image_info->printable_height -
                        image_info->scaled_height);
            }
            break;
    }

    return OK;
}

static int _get_width(wprint_image_info_t *image_info, unsigned int padding_options) {
    int width;
    if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
        width = image_info->printable_width;
    } else if ((image_info->render_flags & RENDER_FLAG_AUTO_FIT) || image_info->scaling_needed) {
        width = image_info->scaled_width;
    } else {
        width = image_info->output_width;
    }
    if (padding_options & PAD_LEFT) {
        width += image_info->output_padding_left;
    }
    if (padding_options & PAD_RIGHT) {
        width += image_info->output_padding_right;
    }
    return width;
}

int wprint_image_get_width(wprint_image_info_t *image_info) {
    int width = _get_width(image_info, image_info->padding_options);
    LOGD("wprint_image_get_width(): %d", width);
    return width;
}

int wprint_image_get_output_buff_size(wprint_image_info_t *image_info) {
    int width = MAX(MAX(image_info->scaled_width, image_info->scaled_height),
            _get_width(image_info, image_info->padding_options));
    LOGD("wprint_image_get_output_buff_size(): %dx%d", width, image_info->output_rows);
    return (BYTES_PER_PIXEL(width * image_info->output_rows));
}

static int _get_height(wprint_image_info_t *image_info, unsigned int padding_options) {
    int height;
    if (image_info->render_flags & RENDER_FLAG_AUTO_SCALE) {
        height = image_info->printable_height;
    } else {
        height = MIN(image_info->scaled_height, image_info->printable_height);
    }
    if (padding_options & PAD_TOP) {
        height += image_info->output_padding_top;
    }
    if (padding_options & PAD_BOTTOM) {
        height += image_info->output_padding_bottom;
    }
    return height;
}

int wprint_image_get_height(wprint_image_info_t *image_info) {
    int height = _get_height(image_info, image_info->padding_options);
    LOGD("wprint_image_get_height(): %d", height);
    return height;
}

bool wprint_image_is_landscape(wprint_image_info_t *image_info) {
    return (image_info->width > image_info->height);
}

int _decode_stripe(wprint_image_info_t *image_info, int start_row, int num_rows,
        unsigned int padding_options, unsigned char *rgb_pixels) {
    int image_y, image_x;
    unsigned char *image_data;
    int nbytes = -1;
    int rbytes;
    int col_offset;
    int old_num_rows;
    const image_decode_ifc_t *decode_ifc = image_info->decode_ifc;
    if ((decode_ifc == NULL) || (decode_ifc->decode_row == NULL)) {
        return nbytes;
    }

    nbytes = 0;
    start_row += image_info->row_offset;
    rbytes = BYTES_PER_PIXEL(image_info->output_width);

    // get padding values
    int padding_left = ((padding_options & PAD_LEFT) ? BYTES_PER_PIXEL(
            image_info->output_padding_left) : 0);
    int padding_right = ((padding_options & PAD_RIGHT) ? BYTES_PER_PIXEL(
            image_info->output_padding_right) : 0);

    old_num_rows = ~num_rows;
    switch (image_info->rotation) {
        case ROT_90:
            col_offset = BYTES_PER_PIXEL(image_info->col_offset);
            while (num_rows > 0) {
                if (start_row > image_info->sampled_width) {
                    return nbytes;
                }
                if (old_num_rows == num_rows) {
                    LOGE("Bad ROT_90 calculations. Exiting to prevent infinite loop");
                    return ERROR;
                }
                old_num_rows = num_rows;
                if ((image_info->output_swath_start == -1) ||
                        (start_row < image_info->output_swath_start) ||
                        (start_row >= (image_info->output_swath_start + image_info->rows_cached))) {
                    if (image_info->output_swath_start == -1) {
                        if (decode_ifc->decode_row(image_info, 0) == NULL) {
                            return ERROR;
                        }
                    }
                    image_info->output_swath_start = ((start_row / image_info->rows_cached) *
                            image_info->rows_cached);
                    for (image_y = 0; image_y < image_info->sampled_height; image_y++) {
                        image_data = decode_ifc->decode_row(image_info, image_y);
                        if (image_data == NULL) {
                            return ERROR;
                        }
                        for (image_x = 0; (image_x < image_info->rows_cached &&
                                ((image_x + image_info->output_swath_start) <
                                        image_info->sampled_width));
                                image_x++) {
                            memcpy(image_info->output_cache[image_x] + BYTES_PER_PIXEL(
                                            (image_info->sampled_height - image_y - 1)),
                                    image_data + BYTES_PER_PIXEL(
                                            (image_info->output_swath_start + image_x)),
                                    BYTES_PER_PIXEL(1));
                        }
                    }
                }

                for (image_y = start_row; ((num_rows != 0) &&
                        (image_y < image_info->sampled_width) &&
                        (image_y < (image_info->output_swath_start + image_info->rows_cached)));
                        image_y++, num_rows--, start_row++) {
                    memcpy(rgb_pixels + padding_left,
                            image_info->output_cache[image_y - image_info->output_swath_start] +
                                    col_offset, rbytes);
                    nbytes += rbytes + padding_left + padding_right;
                    rgb_pixels += rbytes + padding_left + padding_right;
                }
            }
            break;
        case ROT_180:
            col_offset = image_info->col_offset;
            for (image_y = start_row;
                    ((image_y < image_info->sampled_height) && (num_rows != 0));
                    image_y++, num_rows--) {
                image_data = decode_ifc->decode_row(image_info,
                        (image_info->sampled_height - image_y - 1));
                if (image_data == NULL) {
                    return ERROR;
                }
                for (image_x = 0; image_x < image_info->output_width; image_x++) {
                    memcpy(rgb_pixels + padding_left + BYTES_PER_PIXEL(image_x),
                            image_data + BYTES_PER_PIXEL(image_info->sampled_width -
                                    image_x - col_offset - 1),
                            BYTES_PER_PIXEL(1));
                }
                nbytes += rbytes + padding_left + padding_right;
                rgb_pixels += rbytes + padding_left + padding_right;
            }
            break;
        case ROT_270:
            col_offset = BYTES_PER_PIXEL(image_info->col_offset);
            while (num_rows > 0) {
                if (start_row > image_info->sampled_width) {
                    return nbytes;
                }
                if (old_num_rows == num_rows) {
                    LOGE("Bad ROT_270 calculations. Erroring out to prevent infinite loop.");
                    return ERROR;
                }
                old_num_rows = num_rows;
                if ((image_info->output_swath_start == -1) ||
                        (start_row < image_info->output_swath_start) ||
                        (start_row >= (image_info->output_swath_start + image_info->rows_cached))) {
                    if (image_info->output_swath_start == -1) {
                        if (decode_ifc->decode_row(image_info, 0) == NULL) {
                            return ERROR;
                        }
                    }
                    image_info->output_swath_start = ((start_row / image_info->rows_cached) *
                            image_info->rows_cached);
                    for (image_y = 0; image_y < image_info->sampled_height; image_y++) {
                        image_data = decode_ifc->decode_row(image_info, image_y);
                        if (image_data == NULL) {
                            return ERROR;
                        }
                        for (image_x = 0; ((image_x < image_info->rows_cached) &&
                                ((image_x + image_info->output_swath_start) <
                                        image_info->sampled_width));
                                image_x++) {
                            memcpy(image_info->output_cache[image_x] + BYTES_PER_PIXEL(image_y),
                                    image_data + BYTES_PER_PIXEL(image_info->sampled_width -
                                            (image_info->output_swath_start +
                                                    image_x) - 1),
                                    BYTES_PER_PIXEL(1));
                        }
                    }
                }
                for (image_y = start_row;
                        ((num_rows != 0) &&
                                (image_y < image_info->sampled_width) &&
                                (image_y < (image_info->output_swath_start
                                        + image_info->rows_cached)));
                        image_y++, num_rows--, start_row++) {
                    memcpy(rgb_pixels + padding_left,
                            image_info->output_cache[image_y - image_info->output_swath_start] +
                                    col_offset, rbytes);
                    nbytes += rbytes + padding_left + padding_right;
                    rgb_pixels += rbytes + padding_left + padding_right;
                }
            }
            break;
        case ROT_0:
        default:
            col_offset = BYTES_PER_PIXEL(image_info->col_offset);
            for (image_y = start_row;
                    ((image_y < image_info->sampled_height) && (num_rows != 0));
                    image_y++, num_rows--) {
                image_data = decode_ifc->decode_row(image_info, image_y);
                if (image_data == NULL) {
                    LOGE("ERROR: received no data for row: %d", image_y);
                    return ERROR;
                }
                memcpy(rgb_pixels + padding_left, image_data + col_offset, rbytes);
                nbytes += rbytes + padding_left + padding_right;
                rgb_pixels += rbytes + padding_left + padding_right;
            }
            break;
    }
    return nbytes;
}

int wprint_image_decode_stripe(wprint_image_info_t *image_info, int start_row, int *height,
        unsigned char *rgb_pixels) {
    int nbytes = 0;
    int bytes_per_row = BYTES_PER_PIXEL(_get_width(image_info, image_info->padding_options));

    if (height == NULL) {
        return -1;
    }

    int num_rows = *height;

    *height = 0;

    // get padding values
    int padding_left = ((image_info->padding_options & PAD_LEFT) ? BYTES_PER_PIXEL(
            image_info->output_padding_left) : 0);
    int padding_right = ((image_info->padding_options & PAD_RIGHT) ? BYTES_PER_PIXEL(
            image_info->output_padding_right) : 0);
    int padding_top = ((image_info->padding_options & PAD_TOP) ?
            image_info->output_padding_top : 0);
    // handle invalid requests
    if ((start_row < 0) || (start_row >= _get_height(image_info, image_info->padding_options))) {
        *height = 0;
        return ERROR;
    } else if ((image_info->padding_options & PAD_TOP) &&
            (start_row < padding_top)) {
        int blank_rows = MIN(num_rows, (padding_top - start_row));
        int bytesToBlank = (blank_rows * bytes_per_row);
        nbytes += bytesToBlank;
        num_rows -= blank_rows;
        *height += blank_rows;
        memset(rgb_pixels, 0xff, bytesToBlank);
        rgb_pixels += bytesToBlank;
        start_row += blank_rows;
    } else if ((image_info->padding_options & PAD_BOTTOM) &&
            (start_row >= _get_height(image_info, image_info->padding_options & PAD_TOP))) {
        // handle image padding on bottom
        int blank_rows = MIN(num_rows,
                _get_height(image_info, image_info->padding_options) - start_row);
        int bytesToBlank = (blank_rows * bytes_per_row);
        nbytes += bytesToBlank;
        num_rows -= blank_rows;
        *height += blank_rows;
        memset(rgb_pixels, 0xff, bytesToBlank);
        rgb_pixels += bytesToBlank;
        start_row += blank_rows;
    }

    if (num_rows <= 0) {
        return nbytes;
    }

    unsigned char *pad_rgb_pixels = rgb_pixels;
    int unpadded_start_row = start_row;
    // adjust start row to fit within image bounds
    if (image_info->padding_options & PAD_TOP) {
        unpadded_start_row -= padding_top;
    }

    // check if we need to scaling
    if (image_info->scaling_needed) {
        // scaling required
        uint32 scaled_start_row = unpadded_start_row;
        if (image_info->scaled_height > image_info->printable_height) {
            scaled_start_row += ((image_info->scaled_height - image_info->printable_height) / 2);
        }
        uint32 stripe_height, mixed;
        uint16 unscaled_row_start, unscaled_row_end;
        uint16 generated_rows, row_offset;
        uint32 predecoded_rows;

        int scaled_num_rows = (((scaled_start_row + num_rows) > image_info->scaled_height) ?
                (image_info->scaled_height - scaled_start_row) : num_rows);
        while (scaled_num_rows > 0) {
            stripe_height = MIN(scaled_num_rows, image_info->stripe_height);
            scaler_calculate_scaling_rows(scaled_start_row,
                    MIN((scaled_start_row + stripe_height - 1),
                            (image_info->scaled_height - 1)), (void *) &image_info->scaler_config,
                    &unscaled_row_start, &unscaled_row_end, &generated_rows, &row_offset, &mixed);

            if (mixed > image_info->mixed_memory_needed) {
                LOGE("need more memory");
                return -1;
            }

            predecoded_rows = 0;
            if (unscaled_row_start <= image_info->unscaled_end_row) {
                // shift over any rows we need that were decoded in the previous pass
                predecoded_rows = (image_info->unscaled_end_row - unscaled_row_start) + 1;

                memmove(image_info->unscaled_rows, image_info->unscaled_rows +
                        BYTES_PER_PIXEL(((unscaled_row_start - image_info->unscaled_start_row) *
                                image_info->output_width)),
                        BYTES_PER_PIXEL((predecoded_rows * image_info->output_width)));
            }

            image_info->unscaled_start_row = unscaled_row_start;
            image_info->unscaled_end_row = unscaled_row_end;

            /*
             * decode the remaining rows we need
             * don't pad the output since we need to move the data after scaling anyways
             */
            int rowsLeftToDecode = ((image_info->unscaled_end_row -
                    (image_info->unscaled_start_row + predecoded_rows)) + 1);
            if (rowsLeftToDecode > 0) {
                int dbytes = _decode_stripe(image_info,
                        image_info->unscaled_start_row + predecoded_rows, rowsLeftToDecode,
                        PAD_NONE, (image_info->unscaled_rows + BYTES_PER_PIXEL(predecoded_rows *
                                image_info->output_width)));
                if (dbytes <= 0) {
                    if (dbytes < 0) {
                        LOGE("couldn't decode rows");
                    }
                    return dbytes;
                }
            } else if (predecoded_rows <= 0) {
                return 0;
            }

            // scale the data to it's final size
            scaler_scale_image_data(image_info->unscaled_rows, (void *) &image_info->scaler_config,
                    rgb_pixels, image_info->mixed_memory);
            // do we have to move the data around??
            if ((row_offset != 0) ||
                    (image_info->scaled_width > image_info->printable_width) ||
                    (padding_left > 0) ||
                    (padding_right > 0)) {
                int delta = 0;
                int pixelsToMove = BYTES_PER_PIXEL(MIN(image_info->scaled_width,
                        image_info->printable_width));

                int memMoveRow = ((bytes_per_row < image_info->scaler_config.iOutBufWidth) ? 0 : (
                        stripe_height - 1));
                int memMoveIncrement = ((bytes_per_row < image_info->scaler_config.iOutBufWidth)
                        ? 1 : -1);

                // if scaled width is greater than the printable area drop pixels on either size
                if (image_info->scaled_width > image_info->printable_width) {
                    delta = BYTES_PER_PIXEL(
                            ((image_info->scaled_width - image_info->printable_width) / 2));
                }

                // move the data into the correct location in the output buffer
                for (generated_rows = 0; generated_rows < stripe_height; generated_rows++,
                        memMoveRow += memMoveIncrement) {
                    memmove(rgb_pixels + (memMoveRow * bytes_per_row) + padding_left,
                            rgb_pixels + ((memMoveRow + row_offset) *
                                    image_info->scaler_config.iOutBufWidth) + delta, pixelsToMove);
                }
            }

            num_rows -= stripe_height;
            scaled_num_rows -= stripe_height;
            scaled_start_row += stripe_height;
            nbytes += (bytes_per_row * stripe_height);
            rgb_pixels += (bytes_per_row * stripe_height);
            *height += stripe_height;
            start_row += stripe_height;
        }
    } else {
        // no scaling needed

        // decode the request
        int dbytes = _decode_stripe(image_info, unpadded_start_row,
                (((unpadded_start_row + num_rows) >
                        _get_height(image_info, PAD_NONE)) ?
                        (_get_height(image_info, PAD_NONE) - unpadded_start_row)
                        : num_rows),
                image_info->padding_options, rgb_pixels);
        if (dbytes <= 0) {
            if (dbytes < 0) {
                LOGE("couldn't decode rows");
            }
            return dbytes;
        }

        int rows = (dbytes / bytes_per_row);
        *height += rows;
        num_rows -= rows;
        start_row += rows;
        unpadded_start_row += rows;
        rgb_pixels += dbytes;
        nbytes += dbytes;
    }

    // white pad the left and right edges
    if ((pad_rgb_pixels != rgb_pixels) &&
            (image_info->padding_options & (PAD_LEFT | PAD_RIGHT)) &&
            ((padding_left != 0) || (padding_right != 0))) {
        while (pad_rgb_pixels != rgb_pixels) {
            if (padding_left != 0) {
                memset(pad_rgb_pixels, 0xff, padding_left);
            }
            if (padding_right != 0) {
                memset(pad_rgb_pixels + (bytes_per_row - padding_right), 0xff, padding_right);
            }
            pad_rgb_pixels += bytes_per_row;
        }
    }

    if ((image_info->padding_options & PAD_BOTTOM) && (num_rows > 0) &&
            (start_row >= _get_height(image_info, image_info->padding_options & PAD_TOP))) {
        int blank_rows = MIN(num_rows,
                _get_height(image_info, image_info->padding_options) - start_row);
        int bytesToBlank = (blank_rows * bytes_per_row);
        nbytes += bytesToBlank;
        num_rows -= blank_rows;
        *height += blank_rows;
        memset(rgb_pixels, 0xff, bytesToBlank);
        rgb_pixels += bytesToBlank;
    }

    return nbytes;
}

int wprint_image_compute_rows_to_cache(wprint_image_info_t *image_info) {
    int i;
    int row_width, max_rows;
    unsigned char output_mem;
    int available_mem = MAX_DECODE_MEM;
    int width, height;

    width = image_info->sampled_width;
    height = image_info->sampled_height;

    switch (image_info->rotation) {
        case ROT_90:
        case ROT_270:
            output_mem = 1;
            row_width = height;
            break;
        case ROT_0:
        case ROT_180:
        default:
            output_mem = 0;
            row_width = width;
            break;
    }

    available_mem -= (wprint_image_get_output_buff_size(image_info) *
            image_info->concurrent_stripes);
    if (image_info->unscaled_rows != NULL) {
        // remove any memory allocated for scaling from our pool
        available_mem -= BYTES_PER_PIXEL(
                image_info->unscaled_rows_needed * image_info->output_width);
        available_mem -= image_info->mixed_memory_needed;
    }

    // make sure we have a valid amount of memory to work with
    available_mem = MAX(available_mem, MIN_DECODE_MEM);

    LOGD("wprint_image_compute_rows_to_cache(): %d bytes available for row caching", available_mem);

    row_width = BYTES_PER_PIXEL(row_width);
    max_rows = (available_mem / row_width);

    if (max_rows > 0xf) {
        max_rows &= ~0xf;
    }

    LOGD("wprint_image_compute_rows_to_cache(): based on row width %d (%d), %d rows can be cached",
            row_width, output_mem, max_rows);

    if (output_mem) {
        if (max_rows > (MAX(width, height))) {
            max_rows = MAX(width, height);
        }

        image_info->output_cache = (unsigned char **) malloc(sizeof(unsigned char *) * max_rows);
        for (i = 0; i < max_rows; i++) {
            image_info->output_cache[i] = (unsigned char *) malloc(row_width);
        }
    } else {
        max_rows = MIN(max_rows, height);
    }

    image_info->rows_cached = max_rows;
    LOGD("wprint_image_compute_rows_to_cache(): %d rows being cached", max_rows);

    return wprint_image_input_rows_cached(image_info);
}

int wprint_image_input_rows_cached(wprint_image_info_t *image_info) {
    return ((image_info->output_cache != NULL) ? 1 : image_info->rows_cached);
}

void wprint_image_cleanup(wprint_image_info_t *image_info) {
    int i;
    const image_decode_ifc_t *decode_ifc = image_info->decode_ifc;

    if ((decode_ifc != NULL) && (decode_ifc->cleanup != NULL)) {
        decode_ifc->cleanup(image_info);
    }

    // free memory allocated for saving unscaled rows
    if (image_info->unscaled_rows != NULL) {
        free(image_info->unscaled_rows);
        image_info->unscaled_rows = NULL;
    }

    // free memory allocated needed for mixed scaling
    if (image_info->mixed_memory != NULL) {
        free(image_info->mixed_memory);
        image_info->mixed_memory = NULL;
    }

    if (image_info->output_cache != NULL) {
        for (i = 0; i < image_info->rows_cached; i++) {
            free(image_info->output_cache[i]);
        }
        free(image_info->output_cache);
        image_info->output_cache = NULL;
    }
}