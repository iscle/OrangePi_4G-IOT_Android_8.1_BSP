/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef RAW_H_
#define RAW_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stddef.h>

/* Reads a raw file to a float buffer.
 * Args:
 *    filename - The name of the raw file.
 *    frames - Returns the number of frames read.
 * Returns:
 *    The float buffer allocated by malloc(), or NULL if reading fails. The
 *    first half of the buffer contains left channel data, and the second half
 *    contains the right channel data.
 * The raw file is assumed to have two channel 16 bit signed integer samples in
 * native endian. The raw file can be created by:
 *    sox input.wav output.raw
 * The raw file can be played by:
 *    play -r 44100 -s -b 16 -c 2 test.raw
 */
float *read_raw(const char *filename, size_t *frames);

/* Writes a float buffer to a raw file.
 * Args:
 *    filename - The name of the raw file.
 *    buf - The float buffer containing the samples.
 *    frames - The number of frames in the float buffer.
 * Returns:
 *    0 if success. -1 if writing fails.
 * The format of the float buffer is the same as described in read_raw().
 */
void write_raw(const char *filename, float *buf, size_t frames);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* RAW_H_ */
