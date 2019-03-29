/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2011. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

#ifndef _SEC_SIGN_EXTENSION_H
#define _SEC_SIGN_EXTENSION_H

#define MAX_VERITY_COUNT            32
#define VERIFY_COUNT                "VERIFY_COUNT"
#define CHUNK_SIZE                  "CHUNK_SIZE"
#define FB_CHUNK_SIZE               "FB_CHUNK_SIZE"
#define CFG_VERSION                 "CFG_VERSION"
#define SEC_EXTENSION_MAGIC         (0x7A797A79)
#define SEC_EXTENSION_MAGIC_V4      (0x7B797B79)
#define SEC_EXTENSION_HEADER_MAGIC  (0x45454545)

#define CRYPTO_SIZE_UNKNOWN 0

typedef struct _SEC_EXTENTION_CFG
{
    unsigned int verify_count;
    unsigned long long verify_offset[MAX_VERITY_COUNT];
    unsigned long long verify_length[MAX_VERITY_COUNT];
    unsigned int chunk_size;
} SEC_EXTENTION_CFG;

typedef enum
{
    SEC_EXT_HDR_UNKNOWN = 0,
    SEC_EXT_HDR_CRYPTO = 1,
    SEC_EXT_HDR_FRAG_CFG = 2,
    SEC_EXT_HDR_HASH_ONLY = 3,
    SEC_EXT_HDR_HASH_SIG = 4,
    SEC_EXT_HDR_SPARSE = 5,
    SEC_EXT_HDR_HASH_ONLY_64 = 6,
    
    SEC_EXT_HDR_END_MARK = 0xFFFFFFFF
} SEC_EXT_HEADER_TYPE;

typedef enum
{
    SEC_CRYPTO_HASH_UNKNOWN = 0,
    SEC_CRYPTO_HASH_MD5 = 1,
    SEC_CRYPTO_HASH_SHA1 = 2,
    SEC_CRYPTO_HASH_SHA256 = 3,
    SEC_CRYPTO_HASH_SHA512 = 4,
    
} SEC_CRYPTO_HASH_TYPE;

typedef enum
{
    SEC_CRYPTO_SIG_UNKNOWN = 0,
    SEC_CRYPTO_SIG_RSA512 = 1,
    SEC_CRYPTO_SIG_RSA1024 = 2,
    SEC_CRYPTO_SIG_RSA2048 = 3,
    
} SEC_CRYPTO_SIGNATURE_TYPE;

typedef enum
{
    SEC_CRYPTO_ENC_UNKNOWN = 0,
    SEC_CRYPTO_ENC_RC4 = 1,
    SEC_CRYPTO_ENC_AES128 = 2,
    SEC_CRYPTO_ENC_AES192 = 3,    
    SEC_CRYPTO_ENC_AES256 = 4,

} SEC_CRYPTO_ENCRYPTION_TYPE;

typedef enum
{
    SEC_SIZE_HASH_MD5 = 16,
    SEC_SIZE_HASH_SHA1 = 20,
    SEC_SIZE_HASH_SHA256 = 32,
    SEC_SIZE_HASH_SHA512 = 64,
    
} SEC_CRYPTO_HASH_SIZE_BYTES;

typedef enum
{
    SEC_SIZE_SIG_RSA512 = 64,
    SEC_SIZE_SIG_RSA1024 = 128,
    SEC_SIZE_SIG_RSA2048 = 256,
    
} SEC_CRYPTO_SIGNATURE_SIZE_BYTES;


typedef enum
{
    SEC_CHUNK_SIZE_ZERO = 0,
    SEC_CHUNK_SIZE_UNKNOWN = 0x00100000,
    SEC_CHUNK_SIZE_1M = 0x00100000,
    SEC_CHUNK_SIZE_2M = 0x00200000,
    SEC_CHUNK_SIZE_4M = 0x00400000,
    SEC_CHUNK_SIZE_8M = 0x00800000,
    SEC_CHUNK_SIZE_16M = 0x01000000,
    SEC_CHUNK_SIZE_32M = 0x02000000,
    
} SEC_FRAG_CHUNK_SIZE_BYTES;


typedef struct _SEC_EXTENSTION_CRYPTO
{
    unsigned int magic;
    unsigned int ext_type;
    unsigned char hash_type;
    unsigned char sig_type;
    unsigned char enc_type;
    unsigned char reserved;
} SEC_EXTENSTION_CRYPTO;

typedef struct _SEC_FRAGMENT_CFG
{
    unsigned int magic;
    unsigned int ext_type;
    unsigned int chunk_size;
    unsigned int frag_count;
} SEC_FRAGMENT_CFG;

typedef struct _SEC_EXTENSTION_HASH_ONLY
{
    unsigned int magic;
    unsigned int ext_type;
    unsigned int sub_type;  /* hash type */
    unsigned int hash_offset;
    unsigned int hash_len;
    unsigned char hash_data[];
} SEC_EXTENSTION_HASH_ONLY;

typedef struct _SEC_EXTENSTION_HASH_ONLY_64
{
    unsigned int magic;
    unsigned int ext_type;
    unsigned int sub_type;  /* hash type */
    unsigned int padding;
    unsigned long long hash_offset_64;
    unsigned long long hash_len_64;
    unsigned char hash_data[];
} SEC_EXTENSTION_HASH_ONLY_64;

typedef struct _SEC_EXTENSTION_HASH_SIG
{
    unsigned int magic;
    unsigned int ext_type;
    unsigned int sig_type;  /* sig type */
    unsigned int hash_type; /* hash type */
    unsigned int auth_offset;
    unsigned int auth_len;
    unsigned char auth_data[];  /* sig + hash */
} SEC_EXTENSTION_HASH_SIG;

typedef struct _SEC_EXTENSTION_SPARSE
{
    unsigned int magic;
    unsigned int ext_type;
    unsigned int header_size;
    unsigned int sig_size;
    unsigned int hash_size;
    unsigned int ext_size;
    unsigned char sparse_data[];  /* header + sig + hash + ext */
} SEC_EXTENSTION_SPARSE;

typedef struct _SEC_EXTENSTION_END_MARK
{
    unsigned int magic;
    unsigned int ext_type;
} SEC_EXTENSTION_END_MARK;

#endif

