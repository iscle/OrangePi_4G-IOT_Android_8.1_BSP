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

#ifndef _NANOHUB_AES_H_
#define _NANOHUB_AES_H_

#include <stdint.h>

struct AesContext {
    uint32_t K[64];
};

struct AesSetupTempWorksSpace { //unsed temporarily for aesInitForDecr() only, not used after, need not be live
    struct AesContext tmpCtx;
};

#define AES_KEY_WORDS     8
#define AES_BLOCK_WORDS   4
#define AES_BLOCK_SIZE    16 // in bytes

//basic AES block ops
void aesInitForEncr(struct AesContext *ctx, const uint32_t *k);
void aesInitForDecr(struct AesContext *ctx, struct AesSetupTempWorksSpace *tmpSpace, const uint32_t *k);
void aesEncr(struct AesContext *ctx, const uint32_t *src, uint32_t *dst); //encrypts AES_BLOCK_WORDS words
void aesDecr(struct AesContext *ctx, const uint32_t *src, uint32_t *dst); //deencrypts AES_BLOCK_WORDS words

//AES-CBC
struct AesCbcContext {
    struct AesContext aes;
    uint32_t iv[AES_BLOCK_WORDS];
};

void aesCbcInitForEncr(struct AesCbcContext *ctx, const uint32_t *k, const uint32_t *iv);
void aesCbcInitForDecr(struct AesCbcContext *ctx, const uint32_t *k, const uint32_t *iv);
void aesCbcEncr(struct AesCbcContext *ctx, const uint32_t *src, uint32_t *dst); //encrypts AES_BLOCK_WORDS words
void aesCbcDecr(struct AesCbcContext *ctx, const uint32_t *src, uint32_t *dst); //encrypts AES_BLOCK_WORDS words




#endif // _NANOHUB_AES_H_

