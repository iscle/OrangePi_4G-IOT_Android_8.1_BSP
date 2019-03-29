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

#ifndef _NANOHUB_NANOAPP_H_
#define _NANOHUB_NANOAPP_H_

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

void *reallocOrDie(void *buf, size_t bufSz);
void assertMem(size_t used, size_t total);
bool readFile(void *dst, uint32_t len, const char *fileName);
void *loadFile(const char *fileName, uint32_t *size);
void printHash(FILE *out, const char *pfx, const uint32_t *hash, size_t size);
void printHashRev(FILE *out, const char *pfx, const uint32_t *hash, size_t size);

#ifdef __cplusplus
}; /* extern "C" */
#endif

#endif /* _NANOHUB_NANOAPP_H_ */
