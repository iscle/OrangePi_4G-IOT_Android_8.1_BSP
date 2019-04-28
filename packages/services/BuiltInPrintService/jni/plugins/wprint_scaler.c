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

#include "wprint_scaler.h"
#include <assert.h>
#include <stdio.h>

#define ROUND_4_DOWN(x) ((x) & ~3)
#define ROUND_4_UP(x)   (ROUND_4_DOWN((x) + 3))
#define PSCALER_FRACT_BITS_COUNT 24

typedef enum {
    FRACTION_ROUND_UP,
    FRACTION_TRUNCATE
} pscaler_fraction_t;

static uint32
        _scaler_fraction_part(uint32 iNum, uint32 iDen, pscaler_fraction_t mode, bool_t *overflow);

static void _hw_scale_image_plane(scaler_config_t *pscaler_config, scaler_mode_t scaleMode);

static void _calculate_factors(scaler_config_t *pscaler_config, scaler_mode_t scaleMode);

void scaler_make_image_scaler_tables(uint16 image_input_width, uint16 image_input_buf_width,
        uint16 image_output_width, uint16 image_output_buf_width, uint16 image_input_height,
        uint16 image_output_height, scaler_config_t *pscaler_config) {
    pscaler_config->iSrcWidth = image_input_width;
    pscaler_config->iSrcHeight = image_input_height;
    pscaler_config->iOutWidth = image_output_width;
    pscaler_config->iOutHeight = image_output_height;

    if ((image_input_width >= image_output_width) &&
            (image_input_height >= image_output_height)) { // scale DOWN
        pscaler_config->scaleMode = PSCALER_SCALE_DOWN;
    } else if ((image_input_width <= image_output_width) &&
            (image_input_height <= image_output_height)) { // scale UP
        pscaler_config->scaleMode = PSCALER_SCALE_UP;
    } else if (image_input_width > image_output_width) { // mixed scale Y-axis first
        pscaler_config->scaleMode = PSCALER_SCALE_MIXED_YUP;
    } else { // mixed scale X-axis first
        pscaler_config->scaleMode = PSCALER_SCALE_MIXED_XUP;
    }

    // Setup scale factors
    _calculate_factors(pscaler_config, pscaler_config->scaleMode);

    // calculates initial buffer sizes for scaling whole image
    //  start rows    == 0
    //  end_rows      == image height
    //  buffer widths == image widths
    pscaler_config->fSrcStartRow.decimal = 0;
    pscaler_config->fSrcStartRow.fraction = 0;
    pscaler_config->iSrcStartRow = 0;
    pscaler_config->iSrcEndRow = pscaler_config->iSrcHeight;
    pscaler_config->iSrcBufWidth = image_input_buf_width;
    pscaler_config->iOutStartRow = 0;
    pscaler_config->iOutEndRow = pscaler_config->iOutHeight;
    pscaler_config->iOutBufWidth = image_output_buf_width;
    pscaler_config->pSrcBuf = NULL;
    pscaler_config->pOutBuf = NULL;
    pscaler_config->pTmpBuf = NULL;
}

void scaler_calculate_scaling_rows(uint16 start_output_row_number, uint16 end_output_row_number,
        void *tables_ptr, uint16 *start_input_row_number, uint16 *end_input_row_number,
        uint16 *num_output_rows_generated, uint16 *num_rows_offset_to_start_output_row,
        uint32 *mixed_axis_temp_buffer_size_needed) {
    float64_t fSrcEndRow;
    bool_t overflow;
    scaler_config_t *pscaler_config;

    pscaler_config = (scaler_config_t *) tables_ptr;
    assert (start_output_row_number < pscaler_config->iOutHeight);

    // copy the output start and end rows
    // Don't ever attempt to output a single row from the scaler.
    if (end_output_row_number == start_output_row_number) {
        if (start_output_row_number == 0) {
            pscaler_config->iOutStartRow = start_output_row_number;
            pscaler_config->iOutEndRow = end_output_row_number + 1;
            *num_rows_offset_to_start_output_row = 0;
        } else {
            pscaler_config->iOutStartRow = start_output_row_number - 1;
            pscaler_config->iOutEndRow = end_output_row_number;
            *num_rows_offset_to_start_output_row = 1;
        }
    } else {
        pscaler_config->iOutStartRow = start_output_row_number;
        pscaler_config->iOutEndRow = end_output_row_number;
        *num_rows_offset_to_start_output_row = 0;
    }

    if (pscaler_config->iOutEndRow >= pscaler_config->iOutHeight) { // last stripe
        pscaler_config->iOutEndRow = pscaler_config->iOutHeight - 1;
    }

    if (pscaler_config->scaleMode == PSCALER_SCALE_UP ||
            pscaler_config->scaleMode == PSCALER_SCALE_MIXED_YUP) {
        // scale factors are calculated as dim-1/dim-1
        pscaler_config->iSrcHeight--;
        pscaler_config->iOutHeight--;
    }

    pscaler_config->fSrcStartRow.decimal = (uint32) pscaler_config->iOutStartRow *
            (uint32) pscaler_config->iSrcHeight / (uint32) pscaler_config->iOutHeight;

    pscaler_config->fSrcStartRow.fraction = _scaler_fraction_part(
            (uint32) pscaler_config->iOutStartRow * (uint32) pscaler_config->iSrcHeight,
            (uint32) pscaler_config->iOutHeight, FRACTION_ROUND_UP, &overflow);

    if (overflow) {
        pscaler_config->fSrcStartRow.decimal++;
    }

    pscaler_config->iSrcStartRow = pscaler_config->fSrcStartRow.decimal;

    if (pscaler_config->scaleMode == PSCALER_SCALE_UP ||
            pscaler_config->scaleMode == PSCALER_SCALE_MIXED_YUP) {
        fSrcEndRow.decimal = (uint32) pscaler_config->iOutEndRow *
                (uint32) pscaler_config->iSrcHeight / (uint32) pscaler_config->iOutHeight;
        fSrcEndRow.fraction = _scaler_fraction_part(
                (uint32) pscaler_config->iOutEndRow * (uint32) pscaler_config->iSrcHeight,
                (uint32) pscaler_config->iOutHeight, FRACTION_TRUNCATE, &overflow);

        pscaler_config->iSrcEndRow = (uint16) fSrcEndRow.decimal;

        if (0 != fSrcEndRow.fraction) {
            // will cause an extra output row to be created...
            pscaler_config->iSrcEndRow++;
            pscaler_config->iOutEndRow++;
        }

        // restore dimensions
        pscaler_config->iSrcHeight++;
        pscaler_config->iOutHeight++;
    } else {
        fSrcEndRow.decimal = (uint32) (pscaler_config->iOutEndRow + 1) *
                (uint32) pscaler_config->iSrcHeight /
                (uint32) pscaler_config->iOutHeight;

        fSrcEndRow.fraction = _scaler_fraction_part(
                (uint32) (pscaler_config->iOutEndRow + 1) * (uint32) pscaler_config->iSrcHeight,
                (uint32) pscaler_config->iOutHeight, FRACTION_TRUNCATE, &overflow);

        pscaler_config->iSrcEndRow = (uint16) fSrcEndRow.decimal;

        if (0 == fSrcEndRow.fraction) {
            pscaler_config->iSrcEndRow--;
        }
    }

    // check to be sure we're not going beyond the source image
    if (pscaler_config->iSrcEndRow >= pscaler_config->iSrcHeight) { // last stripe
        pscaler_config->iSrcEndRow = pscaler_config->iSrcHeight - 1;
    }

    *start_input_row_number = pscaler_config->iSrcStartRow;
    *end_input_row_number = pscaler_config->iSrcEndRow;
    *num_output_rows_generated = (pscaler_config->iOutEndRow - pscaler_config->iOutStartRow + 1);

    // Calculate the 2nd pass buffer size if mixed scaling is done
    if (pscaler_config->scaleMode == PSCALER_SCALE_MIXED_XUP) {
        *mixed_axis_temp_buffer_size_needed =
                ROUND_4_UP(pscaler_config->iOutWidth + 1) *
                        (*end_input_row_number - *start_input_row_number + 1);
    } else if (pscaler_config->scaleMode == PSCALER_SCALE_MIXED_YUP) {
        *mixed_axis_temp_buffer_size_needed =
                ROUND_4_UP(pscaler_config->iSrcWidth) * (*num_output_rows_generated + 1);
    } else {
        *mixed_axis_temp_buffer_size_needed = 0;
    }

    (*num_output_rows_generated)++;
}

void scaler_scale_image_data(uint8 *input_plane, void *tables_ptr, uint8 *scaled_output_plane,
        uint8 *temp_buffer_for_mixed_axis_scaling) {
    uint16 iOrigWidth, iOrigHeight, iOrigOutBufWidth, iOrigSrcBufWidth;
    uint16 iOrigOutStartRow, iOrigOutEndRow, iOrigSrcStartRow, iOrigSrcEndRow;
    float64_t fOrigSrcStartRow;
    uint8 *pOrigBuf;
    scaler_config_t *pscaler_config;

    pscaler_config = (scaler_config_t *) tables_ptr;
    pscaler_config->pSrcBuf = input_plane;
    pscaler_config->pOutBuf = scaled_output_plane;

    if ((PSCALER_SCALE_MIXED_XUP == pscaler_config->scaleMode) ||
            (PSCALER_SCALE_MIXED_YUP == pscaler_config->scaleMode)) {
        pscaler_config->pTmpBuf = temp_buffer_for_mixed_axis_scaling;

        // save the output buffer
        pOrigBuf = pscaler_config->pOutBuf;

        // use the temp buff as the output buff for pass 1
        pscaler_config->pOutBuf = pscaler_config->pTmpBuf;

        if (PSCALER_SCALE_MIXED_YUP == pscaler_config->scaleMode) {
            // save the original output widths
            iOrigWidth = pscaler_config->iOutWidth;
            iOrigOutBufWidth = pscaler_config->iOutBufWidth;

            // set output widths to input widths (1::1)
            pscaler_config->iOutWidth = pscaler_config->iSrcWidth;
            pscaler_config->iOutBufWidth = pscaler_config->iSrcBufWidth;

            // calculate the new scaler factors
            _calculate_factors(pscaler_config, PSCALER_SCALE_UP);

            // Run the photo scaler hardware
            _hw_scale_image_plane(pscaler_config, PSCALER_SCALE_UP);

            // reset the output widths
            pscaler_config->iOutWidth = iOrigWidth;
            pscaler_config->iOutBufWidth = iOrigOutBufWidth;
        } else {
            // save the original output height and row info
            iOrigHeight = pscaler_config->iOutHeight;
            iOrigOutStartRow = pscaler_config->iOutStartRow;
            iOrigOutEndRow = pscaler_config->iOutEndRow;
            fOrigSrcStartRow.fraction = pscaler_config->fSrcStartRow.fraction;

            // set output height and rows to input height and rows(1::1)
            pscaler_config->iOutHeight = pscaler_config->iSrcHeight;
            pscaler_config->iOutStartRow = pscaler_config->iSrcStartRow;
            pscaler_config->iOutEndRow = pscaler_config->iSrcEndRow;
            pscaler_config->fSrcStartRow.fraction = 0;

            // calculate the new scaler factors
            _calculate_factors(pscaler_config, PSCALER_SCALE_UP);

            // Run the photo scaler hardware
            _hw_scale_image_plane(pscaler_config, PSCALER_SCALE_UP);

            // reset the output height and rows
            pscaler_config->iOutHeight = iOrigHeight;
            pscaler_config->iOutStartRow = iOrigOutStartRow;
            pscaler_config->iOutEndRow = iOrigOutEndRow;
            pscaler_config->fSrcStartRow.fraction = fOrigSrcStartRow.fraction;
        }
        // restore the original output buffer
        pscaler_config->pOutBuf = pOrigBuf;

        // save the original input buffer
        pOrigBuf = pscaler_config->pSrcBuf;

        // use the previous output (temp) buffer as the new input buffer
        pscaler_config->pSrcBuf = pscaler_config->pTmpBuf;

        if (PSCALER_SCALE_MIXED_YUP == pscaler_config->scaleMode) {
            // save the original input height and rows
            iOrigHeight = pscaler_config->iSrcHeight;
            iOrigSrcStartRow = pscaler_config->iSrcStartRow;
            iOrigSrcEndRow = pscaler_config->iSrcEndRow;
            fOrigSrcStartRow.decimal = pscaler_config->fSrcStartRow.decimal;
            fOrigSrcStartRow.fraction = pscaler_config->fSrcStartRow.fraction;

            // set the height and rows to 1::1 for the second pass
            pscaler_config->iSrcHeight = pscaler_config->iOutHeight;
            pscaler_config->iSrcStartRow = pscaler_config->iOutStartRow;
            pscaler_config->iSrcEndRow = pscaler_config->iOutEndRow;
            pscaler_config->fSrcStartRow.decimal = pscaler_config->iOutStartRow;
            pscaler_config->fSrcStartRow.fraction = 0;

            // calculate new scale factors
            _calculate_factors(pscaler_config, PSCALER_SCALE_DOWN);

            // Run the photo scaler hardware
            _hw_scale_image_plane(pscaler_config, PSCALER_SCALE_DOWN);

            // restore original input height and rows
            pscaler_config->iSrcHeight = iOrigHeight;
            pscaler_config->iSrcStartRow = iOrigSrcStartRow;
            pscaler_config->iSrcEndRow = iOrigSrcEndRow;
            pscaler_config->fSrcStartRow.decimal = fOrigSrcStartRow.decimal;
            pscaler_config->fSrcStartRow.fraction = fOrigSrcStartRow.fraction;
        } else {
            // save the original input widths
            iOrigWidth = pscaler_config->iSrcWidth;
            iOrigSrcBufWidth = pscaler_config->iSrcBufWidth;

            // set the widths to 1::1 for the second pass
            pscaler_config->iSrcWidth = pscaler_config->iOutWidth;
            pscaler_config->iSrcBufWidth = pscaler_config->iOutBufWidth;

            // calculate new scale factors
            _calculate_factors(pscaler_config, PSCALER_SCALE_DOWN);

            // Run the photo scaler hardware
            _hw_scale_image_plane(pscaler_config, PSCALER_SCALE_DOWN);

            // restore original input widths
            pscaler_config->iSrcWidth = iOrigWidth;
            pscaler_config->iSrcBufWidth = iOrigSrcBufWidth;
        }

        // restore the input buffer
        pscaler_config->pTmpBuf = pscaler_config->pSrcBuf;
        pscaler_config->pSrcBuf = pOrigBuf;

        // release the temp buffer
        pscaler_config->pTmpBuf = NULL;
    } else {
        // Run the photo scaler hardware
        _hw_scale_image_plane(pscaler_config, pscaler_config->scaleMode);
    }
}

static void _calculate_factors(scaler_config_t *pscaler_config, scaler_mode_t scaleMode) {
    bool_t overflow;
    if ((pscaler_config->scaleMode == PSCALER_SCALE_UP) ||
            (pscaler_config->scaleMode == PSCALER_SCALE_MIXED_YUP)) {
        // scale up factors are computed as (dim-1)/(dim-1)
        pscaler_config->iSrcHeight--;
        pscaler_config->iOutHeight--;
    }
    if ((pscaler_config->scaleMode == PSCALER_SCALE_UP) ||
            (pscaler_config->scaleMode == PSCALER_SCALE_MIXED_XUP)) {
        pscaler_config->iSrcWidth--;
        pscaler_config->iOutWidth--;
    }

    pscaler_config->fXfactor.decimal = (uint32) pscaler_config->iOutWidth /
            (uint32) pscaler_config->iSrcWidth;
    pscaler_config->fXfactor.fraction = _scaler_fraction_part(
            (uint32) pscaler_config->iOutWidth,
            (uint32) pscaler_config->iSrcWidth,
            FRACTION_TRUNCATE,
            &overflow);

    pscaler_config->fXfactorInv.decimal = (uint32) pscaler_config->iSrcWidth /
            (uint32) pscaler_config->iOutWidth;
    pscaler_config->fXfactorInv.fraction = _scaler_fraction_part(
            (uint32) pscaler_config->iSrcWidth, (uint32) pscaler_config->iOutWidth,
            FRACTION_ROUND_UP, &overflow);

    if (overflow) {
        pscaler_config->fXfactorInv.decimal++;
    }

    pscaler_config->fYfactor.decimal = (uint32) pscaler_config->iOutHeight /
            (uint32) pscaler_config->iSrcHeight;
    pscaler_config->fYfactor.fraction = _scaler_fraction_part(
            (uint32) pscaler_config->iOutHeight, (uint32) pscaler_config->iSrcHeight,
            FRACTION_TRUNCATE, &overflow);

    pscaler_config->fYfactorInv.decimal = (uint32) pscaler_config->iSrcHeight /
            (uint32) pscaler_config->iOutHeight;
    pscaler_config->fYfactorInv.fraction = _scaler_fraction_part(
            (uint32) pscaler_config->iSrcHeight, (uint32) pscaler_config->iOutHeight,
            FRACTION_ROUND_UP, &overflow);

    if (overflow) {
        pscaler_config->fYfactorInv.decimal++;
    }

    if ((pscaler_config->scaleMode == PSCALER_SCALE_UP) ||
            (pscaler_config->scaleMode == PSCALER_SCALE_MIXED_YUP)) {
        // restore original dimensions
        pscaler_config->iSrcHeight++;
        pscaler_config->iOutHeight++;
    }
    if ((pscaler_config->scaleMode == PSCALER_SCALE_UP) ||
            (pscaler_config->scaleMode == PSCALER_SCALE_MIXED_XUP)) {
        pscaler_config->iSrcWidth++;
        pscaler_config->iOutWidth++;
    }
}

static uint32 _scaler_fraction_part(uint32 iNum, uint32 iDen, pscaler_fraction_t mode,
        bool_t *overflow) {
    uint32 iFract;     // fractional part
    uint32 iRem;       // remainder part
    int i;          // loop counter

    *overflow = 0;
    iFract = 0;
    iRem = iNum % iDen;

    if (iRem == 0) {
        return (0);
    }

    for (i = PSCALER_FRACT_BITS_COUNT - 1; i >= 0; i--) {
        iRem <<= 1;

        if (iRem == iDen) {
            iFract |= (1 << i);
            break;
        } else if (iRem > iDen) {
            iFract |= (1 << i);
            iRem -= iDen;
        }
    }

    if (mode == FRACTION_TRUNCATE) {
        return (iFract << 8);
    } else {
        if (iRem == 0) {
            return (iFract << 8);
        } else {
            if (iFract < 0x00ffffff) {
                iFract++;
                return (iFract << 8);
            } else {
                *overflow = 1;
                return (0);
            }
        }
    }
}

#define _RESTRICT_ __restrict__

static inline void _scale_row_down_9in(uint8 *_RESTRICT_ in0, uint8 *_RESTRICT_ in1,
        uint8 *_RESTRICT_ in2, uint8 *_RESTRICT_ in3, uint8 *_RESTRICT_ in4, uint8 *_RESTRICT_ in5,
        uint8 *_RESTRICT_ in6, uint8 *_RESTRICT_ in7, uint8 *_RESTRICT_ in8, uint8 *_RESTRICT_ out,
        uint64 position_x, uint64 x_factor_inv, uint32 top_weight, uint32 bot_weight,
        uint32 weight_reciprocal, int out_width) {
    int x;
    uint32 in_col;
    sint32 total_weight;

    for (x = 0; x < out_width; x++) {
        uint32 acc_r = 0;
        uint32 acc_g = 0;
        uint32 acc_b = 0;
        uint32 curr_weight = 256 - ((position_x >> 24) & 0xff);
        total_weight = x_factor_inv >> 24;

        in_col = position_x >> 32;

        while (total_weight > 0) {
            acc_r += (uint32) in0[(in_col * 3) + 0] * curr_weight * top_weight;
            acc_r += (uint32) in1[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in2[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in3[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in4[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in5[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in6[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in7[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in8[(in_col * 3) + 0] * curr_weight * bot_weight;

            acc_g += (uint32) in0[(in_col * 3) + 1] * curr_weight * top_weight;
            acc_g += (uint32) in1[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in2[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in3[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in4[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in5[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in6[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in7[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in8[(in_col * 3) + 1] * curr_weight * bot_weight;

            acc_b += (uint32) in0[(in_col * 3) + 2] * curr_weight * top_weight;
            acc_b += (uint32) in1[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in2[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in3[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in4[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in5[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in6[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in7[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in8[(in_col * 3) + 2] * curr_weight * bot_weight;

            in_col++;

            total_weight -= curr_weight;
            curr_weight = total_weight > 256 ? 256 : total_weight;
        }

        position_x += x_factor_inv;

        out[(x * 3) + 0] = ((uint64) acc_r * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 0] = ((uint64) acc_g * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 0] = ((uint64) acc_b * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
    }
}

static inline void _scale_row_down_8in(uint8 *_RESTRICT_ in0, uint8 *_RESTRICT_ in1,
        uint8 *_RESTRICT_ in2, uint8 *_RESTRICT_ in3, uint8 *_RESTRICT_ in4, uint8 *_RESTRICT_ in5,
        uint8 *_RESTRICT_ in6, uint8 *_RESTRICT_ in7, uint8 *_RESTRICT_ out, uint64 position_x,
        uint64 x_factor_inv, uint32 top_weight,
        uint32 bot_weight, uint32 weight_reciprocal,
        int out_width) {
    int x;
    uint32 in_col;
    sint32 total_weight;

    for (x = 0; x < out_width; x++) {
        uint32 acc_r = 0;
        uint32 acc_g = 0;
        uint32 acc_b = 0;
        uint32 curr_weight = 256 - ((position_x >> 24) & 0xff);
        total_weight = x_factor_inv >> 24;

        in_col = position_x >> 32;

        while (total_weight > 0) {
            acc_r += (uint32) in0[(in_col * 3) + 0] * curr_weight * top_weight;
            acc_r += (uint32) in1[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in2[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in3[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in4[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in5[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in6[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in7[(in_col * 3) + 0] * curr_weight * bot_weight;

            acc_g += (uint32) in0[(in_col * 3) + 1] * curr_weight * top_weight;
            acc_g += (uint32) in1[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in2[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in3[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in4[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in5[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in6[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in7[(in_col * 3) + 1] * curr_weight * bot_weight;

            acc_b += (uint32) in0[(in_col * 3) + 2] * curr_weight * top_weight;
            acc_b += (uint32) in1[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in2[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in3[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in4[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in5[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in6[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in7[(in_col * 3) + 2] * curr_weight * bot_weight;

            in_col++;

            total_weight -= curr_weight;
            curr_weight = total_weight > 256 ? 256 : total_weight;
        }

        position_x += x_factor_inv;

        out[(x * 3) + 0] = ((uint64) acc_r * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 1] = ((uint64) acc_g * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 2] = ((uint64) acc_b * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
    }
}

static inline void _scale_row_down_7in(uint8 *_RESTRICT_ in0, uint8 *_RESTRICT_ in1,
        uint8 *_RESTRICT_ in2, uint8 *_RESTRICT_ in3, uint8 *_RESTRICT_ in4, uint8 *_RESTRICT_ in5,
        uint8 *_RESTRICT_ in6, uint8 *_RESTRICT_ out, uint64 position_x, uint64 x_factor_inv,
        uint32 top_weight, uint32 bot_weight, uint32 weight_reciprocal, int out_width) {
    int x;
    uint32 in_col;
    sint32 total_weight;

    for (x = 0; x < out_width; x++) {
        uint32 acc_r = 0;
        uint32 acc_g = 0;
        uint32 acc_b = 0;
        uint32 curr_weight = 256 - ((position_x >> 24) & 0xff);
        total_weight = x_factor_inv >> 24;

        in_col = position_x >> 32;

        while (total_weight > 0) {
            acc_r += (uint32) in0[(in_col * 3) + 0] * curr_weight * top_weight;
            acc_r += (uint32) in1[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in2[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in3[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in4[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in5[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in6[(in_col * 3) + 0] * curr_weight * bot_weight;

            acc_g += (uint32) in0[(in_col * 3) + 1] * curr_weight * top_weight;
            acc_g += (uint32) in1[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in2[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in3[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in4[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in5[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in6[(in_col * 3) + 1] * curr_weight * bot_weight;

            acc_b += (uint32) in0[(in_col * 3) + 2] * curr_weight * top_weight;
            acc_b += (uint32) in1[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in2[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in3[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in4[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in5[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in6[(in_col * 3) + 2] * curr_weight * bot_weight;

            in_col++;

            total_weight -= curr_weight;
            curr_weight = total_weight > 256 ? 256 : total_weight;
        }

        position_x += x_factor_inv;

        out[(x * 3) + 0] = ((uint64) acc_r * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 1] = ((uint64) acc_g * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 2] = ((uint64) acc_b * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
    }
}

static inline void _scale_row_down_6in(uint8 *_RESTRICT_ in0, uint8 *_RESTRICT_ in1,
        uint8 *_RESTRICT_ in2, uint8 *_RESTRICT_ in3, uint8 *_RESTRICT_ in4, uint8 *_RESTRICT_ in5,
        uint8 *_RESTRICT_ out, uint64 position_x, uint64 x_factor_inv, uint32 top_weight,
        uint32 bot_weight, uint32 weight_reciprocal, int out_width) {
    int x;
    uint32 in_col;
    sint32 total_weight;

    for (x = 0; x < out_width; x++) {
        uint32 acc_r = 0;
        uint32 acc_g = 0;
        uint32 acc_b = 0;
        uint32 curr_weight = 256 - ((position_x >> 24) & 0xff);
        total_weight = x_factor_inv >> 24;

        in_col = position_x >> 32;

        while (total_weight > 0) {
            acc_r += (uint32) in0[(in_col * 3) + 0] * curr_weight * top_weight;
            acc_r += (uint32) in1[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in2[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in3[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in4[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in5[(in_col * 3) + 0] * curr_weight * bot_weight;

            acc_g += (uint32) in0[(in_col * 3) + 1] * curr_weight * top_weight;
            acc_g += (uint32) in1[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in2[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in3[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in4[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in5[(in_col * 3) + 1] * curr_weight * bot_weight;

            acc_b += (uint32) in0[(in_col * 3) + 2] * curr_weight * top_weight;
            acc_b += (uint32) in1[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in2[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in3[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in4[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in5[(in_col * 3) + 2] * curr_weight * bot_weight;

            in_col++;

            total_weight -= curr_weight;
            curr_weight = total_weight > 256 ? 256 : total_weight;
        }

        position_x += x_factor_inv;

        out[(x * 3) + 0] = ((uint64) acc_r * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 1] = ((uint64) acc_g * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 2] = ((uint64) acc_b * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
    }
}

static inline void _scale_row_down_5in(uint8 *_RESTRICT_ in0, uint8 *_RESTRICT_ in1,
        uint8 *_RESTRICT_ in2, uint8 *_RESTRICT_ in3, uint8 *_RESTRICT_ in4, uint8 *_RESTRICT_ out,
        uint64 position_x, uint64 x_factor_inv, uint32 top_weight, uint32 bot_weight,
        uint32 weight_reciprocal, int out_width) {
    int x;
    uint32 in_col;
    sint32 total_weight;

    for (x = 0; x < out_width; x++) {
        uint32 acc_r = 0;
        uint32 acc_g = 0;
        uint32 acc_b = 0;
        uint32 curr_weight = 256 - ((position_x >> 24) & 0xff);
        total_weight = x_factor_inv >> 24;

        in_col = position_x >> 32;

        while (total_weight > 0) {
            acc_r += (uint32) in0[(in_col * 3) + 0] * curr_weight * top_weight;
            acc_r += (uint32) in1[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in2[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in3[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in4[(in_col * 3) + 0] * curr_weight * bot_weight;

            acc_g += (uint32) in0[(in_col * 3) + 1] * curr_weight * top_weight;
            acc_g += (uint32) in1[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in2[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in3[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in4[(in_col * 3) + 1] * curr_weight * bot_weight;

            acc_b += (uint32) in0[(in_col * 3) + 2] * curr_weight * top_weight;
            acc_b += (uint32) in1[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in2[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in3[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in4[(in_col * 3) + 2] * curr_weight * bot_weight;

            in_col++;

            total_weight -= curr_weight;
            curr_weight = total_weight > 256 ? 256 : total_weight;
        }

        position_x += x_factor_inv;

        out[(x * 3) + 0] = ((uint64) acc_r * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 1] = ((uint64) acc_g * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 2] = ((uint64) acc_b * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
    }
}

static inline void _scale_row_down_4in(uint8 *_RESTRICT_ in0, uint8 *_RESTRICT_ in1,
        uint8 *_RESTRICT_ in2, uint8 *_RESTRICT_ in3, uint8 *_RESTRICT_ out, uint64 position_x,
        uint64 x_factor_inv, uint32 top_weight, uint32 bot_weight, uint32 weight_reciprocal,
        int out_width) {
    int x;
    uint32 in_col;
    sint32 total_weight;

    for (x = 0; x < out_width; x++) {
        uint32 acc_r = 0;
        uint32 acc_g = 0;
        uint32 acc_b = 0;
        uint32 curr_weight = 256 - ((position_x >> 24) & 0xff);
        total_weight = x_factor_inv >> 24;

        in_col = position_x >> 32;

        while (total_weight > 0) {
            acc_r += (uint32) in0[(in_col * 3) + 0] * curr_weight * top_weight;
            acc_r += (uint32) in1[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in2[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in3[(in_col * 3) + 0] * curr_weight * bot_weight;

            acc_g += (uint32) in0[(in_col * 3) + 1] * curr_weight * top_weight;
            acc_g += (uint32) in1[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in2[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in3[(in_col * 3) + 1] * curr_weight * bot_weight;

            acc_b += (uint32) in0[(in_col * 3) + 2] * curr_weight * top_weight;
            acc_b += (uint32) in1[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in2[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in3[(in_col * 3) + 2] * curr_weight * bot_weight;

            in_col++;

            total_weight -= curr_weight;
            curr_weight = total_weight > 256 ? 256 : total_weight;
        }

        position_x += x_factor_inv;

        out[(x * 3) + 0] = ((uint64) acc_r * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 1] = ((uint64) acc_g * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 2] = ((uint64) acc_b * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
    }
}

static inline void _scale_row_down_3in(uint8 *_RESTRICT_ in0, uint8 *_RESTRICT_ in1,
        uint8 *_RESTRICT_ in2, uint8 *_RESTRICT_ out, uint64 position_x, uint64 x_factor_inv,
        uint32 top_weight, uint32 bot_weight, uint32 weight_reciprocal, int out_width) {
    int x;
    uint32 in_col;
    sint32 total_weight;

    for (x = 0; x < out_width; x++) {
        uint32 acc_r = 0;
        uint32 acc_g = 0;
        uint32 acc_b = 0;
        uint32 curr_weight = 256 - ((position_x >> 24) & 0xff);
        total_weight = x_factor_inv >> 24;

        in_col = position_x >> 32;

        while (total_weight > 0) {
            acc_r += (uint32) in0[(in_col * 3) + 0] * curr_weight * top_weight;
            acc_r += (uint32) in1[(in_col * 3) + 0] * curr_weight << 8;
            acc_r += (uint32) in2[(in_col * 3) + 0] * curr_weight * bot_weight;

            acc_g += (uint32) in0[(in_col * 3) + 1] * curr_weight * top_weight;
            acc_g += (uint32) in1[(in_col * 3) + 1] * curr_weight << 8;
            acc_g += (uint32) in2[(in_col * 3) + 1] * curr_weight * bot_weight;

            acc_b += (uint32) in0[(in_col * 3) + 2] * curr_weight * top_weight;
            acc_b += (uint32) in1[(in_col * 3) + 2] * curr_weight << 8;
            acc_b += (uint32) in2[(in_col * 3) + 2] * curr_weight * bot_weight;

            in_col++;

            total_weight -= curr_weight;
            curr_weight = total_weight > 256 ? 256 : total_weight;
        }

        position_x += x_factor_inv;

        out[(x * 3) + 0] = ((uint64) acc_r * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 1] = ((uint64) acc_g * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 2] = ((uint64) acc_b * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
    }
}

static inline void _scale_row_down_2in(uint8 *_RESTRICT_ in0, uint8 *_RESTRICT_ in1,
        uint8 *_RESTRICT_ out, uint64 position_x, uint64 x_factor_inv, uint32 top_weight,
        uint32 bot_weight, uint32 weight_reciprocal, int out_width) {
    int x;
    uint32 in_col;
    sint32 total_weight;

    for (x = 0; x < out_width; x++) {
        uint32 acc_r = 0;
        uint32 acc_g = 0;
        uint32 acc_b = 0;
        uint32 curr_weight = 256 - ((position_x >> 24) & 0xff);
        total_weight = x_factor_inv >> 24;

        in_col = position_x >> 32;

        while (total_weight > 0) {
            acc_r += (uint32) in0[(in_col * 3) + 0] * curr_weight * top_weight;
            acc_r += (uint32) in1[(in_col * 3) + 0] * curr_weight * bot_weight;

            acc_g += (uint32) in0[(in_col * 3) + 1] * curr_weight * top_weight;
            acc_g += (uint32) in1[(in_col * 3) + 1] * curr_weight * bot_weight;

            acc_b += (uint32) in0[(in_col * 3) + 2] * curr_weight * top_weight;
            acc_b += (uint32) in1[(in_col * 3) + 2] * curr_weight * bot_weight;

            in_col++;

            total_weight -= curr_weight;
            curr_weight = total_weight > 256 ? 256 : total_weight;
        }

        position_x += x_factor_inv;

        out[(x * 3) + 0] = ((uint64) acc_r * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 1] = ((uint64) acc_g * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        out[(x * 3) + 2] = ((uint64) acc_b * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
    }
}

static inline void _scale_row_down(uint8 *in, uint8 *_RESTRICT_ out, uint32 in_row_ofs,
        uint64 position_x, uint64 position_y, uint64 x_factor_inv, uint64 y_factor_inv,
        uint32 weight_reciprocal, int out_width) {
    int x;
    uint32 y, in_col, in_rows, top_weight, bot_weight;
    sint32 total_weight;

    total_weight = y_factor_inv >> 24;

    top_weight = (uint32) 256 - ((position_y >> 24) & 0xff);

    if ((sint32) top_weight > total_weight) {
        top_weight = total_weight;
    }
    total_weight -= top_weight;

    if (total_weight & 0xff) {
        bot_weight = total_weight & 0xff;
    } else if (total_weight > 255) {
        bot_weight = 256;
    } else {
        bot_weight = 0;
    }

    total_weight -= bot_weight;

    assert(total_weight >= 0);
    assert((total_weight & 0xff) == 0);

    in_rows = 2 + (total_weight >> 8);

    if (in_rows == 2) {
        _scale_row_down_2in(in, in + in_row_ofs,
                out, position_x, x_factor_inv, top_weight, bot_weight, weight_reciprocal,
                out_width);
    } else if (in_rows == 3) {
        _scale_row_down_3in(in, in + in_row_ofs, in + 2 * in_row_ofs,
                out, position_x, x_factor_inv, top_weight, bot_weight, weight_reciprocal,
                out_width);
    } else if (in_rows == 4) {
        _scale_row_down_4in(in, in + in_row_ofs, in + 2 * in_row_ofs, in + 3 * in_row_ofs,
                out, position_x, x_factor_inv, top_weight, bot_weight, weight_reciprocal,
                out_width);
    } else if (in_rows == 5) {
        _scale_row_down_5in(in, in + in_row_ofs, in + 2 * in_row_ofs, in + 3 * in_row_ofs,
                in + 4 * in_row_ofs,
                out, position_x, x_factor_inv,
                top_weight, bot_weight, weight_reciprocal,
                out_width);
    } else if (in_rows == 6) {
        _scale_row_down_6in(in, in + in_row_ofs, in + 2 * in_row_ofs, in + 3 * in_row_ofs,
                in + 4 * in_row_ofs, in + 5 * in_row_ofs,
                out, position_x, x_factor_inv, top_weight, bot_weight, weight_reciprocal,
                out_width);
    } else if (in_rows == 7) {
        _scale_row_down_7in(in, in + in_row_ofs, in + 2 * in_row_ofs, in + 3 * in_row_ofs,
                in + 4 * in_row_ofs, in + 5 * in_row_ofs, in + 6 * in_row_ofs,
                out, position_x, x_factor_inv, top_weight, bot_weight, weight_reciprocal,
                out_width);
    } else if (in_rows == 8) {
        _scale_row_down_8in(in, in + in_row_ofs, in + 2 * in_row_ofs, in + 3 * in_row_ofs,
                in + 4 * in_row_ofs, in + 5 * in_row_ofs, in + 6 * in_row_ofs,
                in + 7 * in_row_ofs,
                out, position_x, x_factor_inv, top_weight, bot_weight, weight_reciprocal,
                out_width);
    } else if (in_rows == 9) {
        _scale_row_down_9in(in, in + in_row_ofs, in + 2 * in_row_ofs, in + 3 * in_row_ofs,
                in + 4 * in_row_ofs, in + 5 * in_row_ofs, in + 6 * in_row_ofs,
                in + 7 * in_row_ofs, in + 8 * in_row_ofs,
                out, position_x, x_factor_inv, top_weight, bot_weight, weight_reciprocal,
                out_width);
    } else {
        for (x = 0; x < out_width; x++) {
            uint32 acc_r = 0;
            uint32 acc_g = 0;
            uint32 acc_b = 0;
            uint32 curr_weight = 256 - ((position_x >> 24) & 0xff);
            total_weight = x_factor_inv >> 24;

            in_col = position_x >> 32;

            while (total_weight > 0) {
                acc_r += (uint32) in[(in_col * 3) + 0] * curr_weight * top_weight;
                acc_g += (uint32) in[(in_col * 3) + 1] * curr_weight * top_weight;
                acc_b += (uint32) in[(in_col * 3) + 2] * curr_weight * top_weight;

                for (y = 1; y < in_rows - 1; y++) {
                    acc_r += (uint32) in[y * in_row_ofs + ((in_col * 3) + 0)] * curr_weight * 256;
                    acc_g += (uint32) in[y * in_row_ofs + ((in_col * 3) + 1)] * curr_weight * 256;
                    acc_b += (uint32) in[y * in_row_ofs + ((in_col * 3) + 2)] * curr_weight * 256;
                }

                acc_r +=
                        (uint32) in[y * in_row_ofs + ((in_col * 3) + 0)] * curr_weight * bot_weight;
                acc_g +=
                        (uint32) in[y * in_row_ofs + ((in_col * 3) + 1)] * curr_weight * bot_weight;
                acc_b +=
                        (uint32) in[y * in_row_ofs + ((in_col * 3) + 2)] * curr_weight * bot_weight;

                in_col++;
                total_weight -= curr_weight;
                curr_weight = total_weight > 256 ? 256 : total_weight;
            }

            position_x += x_factor_inv;

            out[(x * 3) + 0] = ((uint64) acc_r * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
            out[(x * 3) + 1] = ((uint64) acc_g * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
            out[(x * 3) + 2] = ((uint64) acc_b * weight_reciprocal + ((uint64) 1 << 31)) >> 32;
        }
    }
}

static void _scale_row_up(uint8 *_RESTRICT_ in0, uint8 *_RESTRICT_ in1, uint8 *_RESTRICT_ out,
        sint32 weight_y, uint64 position_x, uint64 increment_x, int out_width) {
    int x;
    for (x = 0; x < out_width; x++) {
        sint32 top_val_r, bot_val_r;
        sint32 top_val_g, bot_val_g;
        sint32 top_val_b, bot_val_b;

        // Position is tracked with 32 bits of precision, but interpolation is
        // only guided by 10. REVISIT - Check ASM and make sure the compiler
        // handled the second part here optimally.
        uint32 pix_x = position_x >> 32;

        sint32 weight_x = (position_x & 0xffffffff) >> 22;

        // top_val and bot_val become 18-bit values here
        top_val_r = (in0[(pix_x * 3) + 0] << 10) +
                weight_x * ((sint32) in0[((pix_x + 1) * 3) + 0] - in0[(pix_x * 3) + 0]);
        bot_val_r = (in1[(pix_x * 3) + 0] << 10) +
                weight_x * ((sint32) in1[((pix_x + 1) * 3) + 0] - in1[(pix_x * 3) + 0]);

        top_val_g = (in0[(pix_x * 3) + 1] << 10) +
                weight_x * ((sint32) in0[((pix_x + 1) * 3) + 1] - in0[(pix_x * 3) + 1]);
        bot_val_g = (in1[(pix_x * 3) + 1] << 10) +
                weight_x * ((sint32) in1[((pix_x + 1) * 3) + 1] - in1[(pix_x * 3) + 1]);

        top_val_b = (in0[(pix_x * 3) + 2] << 10) +
                weight_x * ((sint32) in0[((pix_x + 1) * 3) + 2] - in0[(pix_x * 3) + 2]);
        bot_val_b = (in1[(pix_x * 3) + 2] << 10) +
                weight_x * ((sint32) in1[((pix_x + 1) * 3) + 2] - in1[(pix_x * 3) + 2]);

        // out is an 8-bit value. We do not need to range-check, as overflow
        // is mathematically impossible.
        out[(x * 3) + 0] = ((top_val_r << 10) + weight_y * (bot_val_r - top_val_r)) >> 20;
        out[(x * 3) + 1] = ((top_val_g << 10) + weight_y * (bot_val_g - top_val_g)) >> 20;
        out[(x * 3) + 2] = ((top_val_b << 10) + weight_y * (bot_val_b - top_val_b)) >> 20;

        position_x += increment_x;
    }
}

static void _hw_scale_image_plane(scaler_config_t *pscaler_config, scaler_mode_t scaleMode) {
    // These pointers duplicate h/w regs
    uint64 x_factor, y_factor, x_factor_inv, y_factor_inv;
    uint32 x_output_width, y_output_width;
    uint32 input_pixel_ptr_offset, output_pixel_ptr_offset;
    uint32 first_xi;
    uint64 first_y_src, first_x_src, weight_reciprocal;

    // These are internal state
    uint32 r;
    uint8 *outp;

    x_output_width = pscaler_config->iOutWidth;
    y_output_width = pscaler_config->iOutEndRow -
            pscaler_config->iOutStartRow + 1;

    input_pixel_ptr_offset = pscaler_config->iSrcBufWidth;
    output_pixel_ptr_offset = pscaler_config->iOutBufWidth;

    x_factor = (uint64) pscaler_config->fXfactor.decimal << 32;
    x_factor |= pscaler_config->fXfactor.fraction;

    y_factor = (uint64) pscaler_config->fYfactor.decimal << 32;
    y_factor |= pscaler_config->fYfactor.fraction;

    x_factor_inv = (uint64) pscaler_config->fXfactorInv.decimal << 32;
    x_factor_inv |= pscaler_config->fXfactorInv.fraction;

    y_factor_inv = (uint64) pscaler_config->fYfactorInv.decimal << 32;
    y_factor_inv |= pscaler_config->fYfactorInv.fraction;

    first_y_src = (uint64) pscaler_config->fSrcStartRow.decimal << 32;
    first_y_src |= pscaler_config->fSrcStartRow.fraction;

    // PC REVISIT - The HW has config registers for these, but they aren't being
    // used by lib_photo_scaler do I don't want to use them, either. For now
    // just print them so I can figure out what's going on and then clear the
    // associated variables. Maybe we're always running the scaler from the
    // left edge of the source so they're implicitly zero?
    first_xi = pscaler_config->iOutStartColumn;

    first_x_src = (uint64) pscaler_config->fSrcStartColumn.decimal << 32;
    first_x_src |= pscaler_config->fSrcStartColumn.fraction;

    first_xi = first_x_src = 0;

    weight_reciprocal = ((uint64) 1 << 32);
    weight_reciprocal /= (x_factor_inv >> 24) * (y_factor_inv >> 24);

    outp = (pscaler_config->pOutBuf) + (first_xi * 3);

    // PC - Assume pSrcBuf is already aligned to "true" base of input,
    // so ignore whole-number part of first_y_src.
    first_y_src = first_y_src & 0xffffffff;

    for (r = 0; r < y_output_width; r++) {
        uint8 *inp = (pscaler_config->pSrcBuf) +
                (first_y_src >> 32) * input_pixel_ptr_offset;
        {
            if (scaleMode == PSCALER_SCALE_UP) {
                _scale_row_up(inp, inp + input_pixel_ptr_offset, outp,
                        (first_y_src & 0xffffffff) >> 22, first_x_src,
                        x_factor_inv, x_output_width);
            } else {
                _scale_row_down(inp, outp, input_pixel_ptr_offset,
                        first_x_src, first_y_src, x_factor_inv, y_factor_inv,
                        weight_reciprocal, x_output_width);
            }
        }
        first_y_src += y_factor_inv;
        outp += output_pixel_ptr_offset;
    }
}