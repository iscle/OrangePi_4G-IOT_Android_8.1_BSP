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

#include <stdio.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include <nanohub/nanoapp.h>

void *reallocOrDie(void *buf, size_t bufSz)
{
    void *newBuf = realloc(buf, bufSz);
    if (!newBuf) {
        fprintf(stderr, "Failed to allocate %zu bytes\n", bufSz);
        exit(2);
    }
    return newBuf;
}

void assertMem(size_t used, size_t total)
{
    if (used <= total)
        return;
    fprintf(stderr, "Buffer size %zu is not big enough to complete operation; we need %zu bytes\n", total, used);
    exit(2);
}

// read file of known size, make sure the size is correct
bool readFile(void *dst, uint32_t len, const char *fileName)
{
    FILE *f = fopen(fileName, "rb");
    bool ret = false;

    if (!f)
        return false;

    if (len != fread(dst, 1, len, f))
        goto out;

    if (fread(&len, 1, 1, f)) //make sure file is actually over
        goto out;

    ret = true;

out:
    fclose(f);
    return ret;
}

// read complete file of unknown size, return malloced buffer and size
void *loadFile(const char *fileName, uint32_t *size)
{
    FILE *f = fopen(fileName, "rb");
    uint8_t *dst = NULL;
    uint32_t len = 0, grow = 16384, total = 0;
    uint32_t block;

    if (!f) {
        fprintf(stderr, "couldn't open %s: %s\n", fileName, strerror(errno));
        exit(2);
    }

    do {
        len += grow; dst = reallocOrDie(dst, len);

        block = fread(dst + total, 1, grow, f);
        total += block;
    } while (block == grow);

    *size = total;
    if (!feof(f)) {
        fprintf(stderr, "Failed to read entire file %s: %s\n",
                fileName, strerror(errno));
        free(dst);
        fclose(f);
        dst = NULL;
        exit(2);
    }

    fclose(f);
    return dst;
}

static void doPrintHash(FILE *out, const char *pfx, const uint32_t *hash, size_t size, int increment)
{
    size_t i;
    int pos;
    fprintf(out, "%s: ", pfx);
    for (i = 0, pos = 0; i < size; ++i, pos += increment)
        fprintf(out, "%08" PRIx32, hash[pos]);
    fprintf(out, "\n");
}

void printHash(FILE *out, const char *pfx, const uint32_t *hash, size_t size)
{
    doPrintHash(out, pfx, hash, size, 1);
}

void printHashRev(FILE *out, const char *pfx, const uint32_t *hash, size_t size)
{
    doPrintHash(out, pfx, hash + size - 1, size, -1);
}
