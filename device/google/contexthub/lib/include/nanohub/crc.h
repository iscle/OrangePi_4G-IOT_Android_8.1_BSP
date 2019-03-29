/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef _NANOHUB_CRC_H_
#define _NANOHUB_CRC_H_

#include <stddef.h>
#include <stdint.h>

#define CRC_RESIDUE 0xC704DD7BUL
#define CRC_INIT    0xFFFFFFFFUL

/**
 * Implements CRC with the following parameters:
 *
 * Width:   32
 * Poly:    04C11DB7
 * Init:    FFFFFFFF
 * RefIn:   False
 * RefOut:  False
 * XorOut:  00000000
 *
 * The CRC implementation will pad the buffer with zeroes to the nearest
 * multiple of 4 bytes (if necessary).
 */
uint32_t crc32(const void *buf, size_t size, uint32_t crc);

/* concrete public implementation of crc32() w/o HW acceleration */
uint32_t soft_crc32(const void *buf, size_t size, uint32_t crc);


#endif /* _NANOHUB_CRC_H_ */
