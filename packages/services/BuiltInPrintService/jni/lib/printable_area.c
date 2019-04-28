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

#include <math.h>
#include "lib_printable_area.h"
#include "wprint_debug.h"
#include "../plugins/media.h"

#define TAG "printable_area"

void printable_area_get(wprint_job_params_t *job_params, float top_margin, float left_margin,
        float right_margin, float bottom_margin) {
    if (job_params == NULL) return;

    job_params->printable_area_width = job_params->printable_area_height = 0.0f;
    job_params->width = job_params->height = 0.0f;
    job_params->page_top_margin = job_params->page_bottom_margin = 0.0f;
    job_params->page_right_margin = job_params->page_left_margin = 0.0f;

    job_params->page_width = 0.0f;
    job_params->page_height = 0.0f;
    int i;
    for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
        if (job_params->media_size == SupportedMediaSizes[i].media_size) {
            job_params->page_width = SupportedMediaSizes[i].WidthInInches / 1000;
            job_params->page_height = SupportedMediaSizes[i].HeightInInches / 1000;
        }
    }
    // don't adjust for margins if job is borderless and PCLm.  dimensions of image will not
    // match (will be bigger than) the dimensions of the page size and a corrupt image will render
    // in genPCLm
    job_params->printable_area_width = floorf(
            ((job_params->page_width - (left_margin + right_margin)) *
                    (float) job_params->pixel_units));
    job_params->printable_area_height = floorf(
            ((job_params->page_height - (top_margin + bottom_margin)) *
                    (float) job_params->pixel_units));

    job_params->page_top_margin = top_margin;
    job_params->page_left_margin = left_margin;
    job_params->page_right_margin = right_margin;
    job_params->page_bottom_margin = bottom_margin;

    if (!job_params->borderless) {
        if (job_params->job_top_margin > top_margin) {
            job_params->print_top_margin = floorf(
                    ((job_params->job_top_margin - top_margin) * (float) job_params->pixel_units));
        } else {
            job_params->print_top_margin = floorf(((top_margin) * (float) job_params->pixel_units));
        }
        if (job_params->job_left_margin > left_margin) {
            job_params->print_left_margin = floorf(((job_params->job_left_margin - left_margin) *
                    (float) job_params->pixel_units));
        } else {
            job_params->print_left_margin = floorf(
                    ((left_margin) * (float) job_params->pixel_units));
        }
        if (job_params->job_right_margin > right_margin) {
            job_params->print_right_margin = floorf(((job_params->job_right_margin - right_margin) *
                    (float) job_params->pixel_units));
        } else {
            job_params->print_right_margin = floorf(
                    ((right_margin) * (float) job_params->pixel_units));
        }
        if (job_params->job_bottom_margin > bottom_margin) {
            job_params->print_bottom_margin = floorf(
                    ((job_params->job_bottom_margin - bottom_margin) *
                            (float) job_params->pixel_units));
        } else {
            job_params->print_bottom_margin = floorf(
                    ((bottom_margin) * (float) job_params->pixel_units));
        }
    }

    job_params->width = (job_params->printable_area_width -
            (job_params->print_left_margin + job_params->print_right_margin));
    job_params->height = (job_params->printable_area_height -
            (job_params->print_top_margin + job_params->print_bottom_margin));
}

void printable_area_get_default_margins(const wprint_job_params_t *job_params,
        const printer_capabilities_t *printer_cap,
        float *top_margin,
        float *left_margin, float *right_margin,
        float *bottom_margin) {
    if ((job_params == NULL) || (printer_cap == NULL)) {
        return;
    }

    bool useDefaultMargins = true;

    if (job_params->borderless) {
        useDefaultMargins = false;
        switch (job_params->pcl_type) {
            case PCLm:
            case PCLPWG:
                *top_margin = 0.0f;
                *left_margin = 0.0f;
                *right_margin = 0.0f;
                *bottom_margin = 0.00f;
                break;
            default:
                *top_margin = -0.065f;
                *left_margin = -0.10f;
                *right_margin = -0.118f;
                *bottom_margin = -0.10f;
                break;
        }
    } else {
        switch (job_params->pcl_type) {
            case PCLm:
                *top_margin = (float) printer_cap->printerTopMargin / 2540;
                *bottom_margin = (float) printer_cap->printerBottomMargin / 2540;
                *left_margin = (float) printer_cap->printerLeftMargin / 2540;
                *right_margin = (float) printer_cap->printerRightMargin / 2540;
                useDefaultMargins = false;
                break;
            case PCLPWG:
                *top_margin = 0.0f;
                *left_margin = 0.0f;
                *right_margin = 0.0f;
                *bottom_margin = 0.00f;
                useDefaultMargins = false;
                break;
            default:
                break;
        }
    }

    if (useDefaultMargins) {
        if (!printer_cap->inkjet) {
            // default laser margins
            *top_margin = 0.2f;
            *left_margin = 0.25f;
            *right_margin = 0.25f;
            *bottom_margin = 0.2f;
        } else {
            // default inkjet margins
            *top_margin = 0.125f;
            *left_margin = 0.125f;
            *right_margin = 0.125f;
            if ((job_params->duplex != DUPLEX_MODE_NONE) || !printer_cap->borderless) {
                *bottom_margin = 0.5f;
            } else {
                *bottom_margin = 0.125f;
            }
        }
    }

    LOGD("printable_area_get_default_margins(): top_margin=%f, left_margin=%f, "
            "right_margin=%f, bottom_margin=%f", *top_margin, *left_margin, *right_margin,
            *bottom_margin);
}