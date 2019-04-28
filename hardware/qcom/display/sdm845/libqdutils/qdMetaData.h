/*
 * Copyright (c) 2012-2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef _QDMETADATA_H
#define _QDMETADATA_H

#ifdef USE_COLOR_METADATA
#include <color_metadata.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define MAX_UBWC_STATS_LENGTH 32

enum ColorSpace_t{
    ITU_R_601,
    ITU_R_601_FR,
    ITU_R_709,
    ITU_R_2020,
    ITU_R_2020_FR,
};

enum IGC_t {
    IGC_NotSpecified,
    IGC_sRGB,
};

struct HSICData_t {
    int32_t hue;
    float   saturation;
    int32_t intensity;
    float   contrast;
};

struct BufferDim_t {
    int32_t sliceWidth;
    int32_t sliceHeight;
};

enum UBWC_Version {
    UBWC_UNUSED      = 0,
    UBWC_1_0         = 0x1,
    UBWC_2_0         = 0x2,
    UBWC_MAX_VERSION = 0xFF,
};

struct UBWC_2_0_Stats {
    uint32_t nCRStatsTile32;  /**< UBWC Stats info for  32 Byte Tile */
    uint32_t nCRStatsTile64;  /**< UBWC Stats info for  64 Byte Tile */
    uint32_t nCRStatsTile96;  /**< UBWC Stats info for  96 Byte Tile */
    uint32_t nCRStatsTile128; /**< UBWC Stats info for 128 Byte Tile */
    uint32_t nCRStatsTile160; /**< UBWC Stats info for 160 Byte Tile */
    uint32_t nCRStatsTile192; /**< UBWC Stats info for 192 Byte Tile */
    uint32_t nCRStatsTile256; /**< UBWC Stats info for 256 Byte Tile */
};

struct UBWCStats {
    enum UBWC_Version version; /* Union depends on this version. */
    uint8_t bDataValid;      /* If [non-zero], CR Stats data is valid.
                               * Consumers may use stats data.
                               * If [zero], CR Stats data is invalid.
                               * Consumers *Shall* not use stats data */
    union {
        struct UBWC_2_0_Stats ubwc_stats;
        uint32_t reserved[MAX_UBWC_STATS_LENGTH]; /* This is for future */
    };
};

struct S3DGpuComp_t {
    int32_t displayId; /* on which display S3D is composed by client */
    uint32_t s3dMode; /* the S3D format of this layer to be accessed by client */
};

struct MetaData_t {
    int32_t operation;
    int32_t interlaced;
    struct BufferDim_t bufferDim;
    float refreshrate;
    enum ColorSpace_t colorSpace;
    enum IGC_t igc;
     /* Gralloc sets PRIV_SECURE_BUFFER flag to inform that the buffers are from
      * ION_SECURE. which should not be mapped. However, for GPU post proc
      * feature, GFX needs to map this buffer, in the client context and in SF
      * context, it should not. Hence to differentiate, add this metadata field
      * for clients to set, and GPU will to read and know when to map the
      * SECURE_BUFFER(ION) */
    int32_t mapSecureBuffer;
    /* The supported formats are defined in gralloc_priv.h to
     * support legacy code*/
    uint32_t s3dFormat;
    /* VENUS output buffer is linear for UBWC Interlaced video */
    uint32_t linearFormat;
    /* Set by graphics to indicate that this buffer will be written to but not
     * swapped out */
    uint32_t isSingleBufferMode;
    /* Indicate GPU to draw S3D layer on dedicate display device */
    struct S3DGpuComp_t s3dComp;

    /* Set by camera to program the VT Timestamp */
    uint64_t vtTimeStamp;
#ifdef USE_COLOR_METADATA
    /* Color Aspects + HDR info */
    ColorMetaData color;
#endif
    /* Consumer should read this data as follows based on
     * Gralloc flag "interlaced" listed above.
     * [0] : If it is progressive.
     * [0] : Top field, if it is interlaced.
     * [1] : Do not read, if it is progressive.
     * [1] : Bottom field, if it is interlaced.
     */
    struct UBWCStats ubwcCRStats[2];
};

enum DispParamType {
    SET_VT_TIMESTAMP         = 0x0001,
    COLOR_METADATA           = 0x0002,
    PP_PARAM_INTERLACED      = 0x0004,
    UNUSED2                  = 0x0008,
    UNUSED3                  = 0x0010,
    UNUSED4                  = 0x0020,
    SET_UBWC_CR_STATS_INFO   = 0x0040,
    UPDATE_BUFFER_GEOMETRY   = 0x0080,
    UPDATE_REFRESH_RATE      = 0x0100,
    UPDATE_COLOR_SPACE       = 0x0200,
    MAP_SECURE_BUFFER        = 0x0400,
    S3D_FORMAT               = 0x0800,
    LINEAR_FORMAT            = 0x1000,
    SET_IGC                  = 0x2000,
    SET_SINGLE_BUFFER_MODE   = 0x4000,
    SET_S3D_COMP             = 0x8000,
};

enum DispFetchParamType {
    GET_VT_TIMESTAMP         = 0x0001,
    GET_COLOR_METADATA       = 0x0002,
    GET_PP_PARAM_INTERLACED  = 0x0004,
    GET_UBWC_CR_STATS_INFO   = 0x0040,
    GET_BUFFER_GEOMETRY      = 0x0080,
    GET_REFRESH_RATE         = 0x0100,
    GET_COLOR_SPACE          = 0x0200,
    GET_MAP_SECURE_BUFFER    = 0x0400,
    GET_S3D_FORMAT           = 0x0800,
    GET_LINEAR_FORMAT        = 0x1000,
    GET_IGC                  = 0x2000,
    GET_SINGLE_BUFFER_MODE   = 0x4000,
    GET_S3D_COMP             = 0x8000,
};

struct private_handle_t;
int setMetaData(struct private_handle_t *handle, enum DispParamType paramType,
                void *param);
int setMetaDataVa(struct MetaData_t* data, enum DispParamType paramType,
                  void *param);

int getMetaData(struct private_handle_t *handle,
                enum DispFetchParamType paramType,
                void *param);
int getMetaDataVa(struct MetaData_t* data, enum DispFetchParamType paramType,
                  void *param);

int copyMetaData(struct private_handle_t *src, struct private_handle_t *dst);
int copyMetaDataVaToHandle(struct MetaData_t *src, struct private_handle_t *dst);
int copyMetaDataHandleToVa(struct private_handle_t* src, struct MetaData_t *dst);
int copyMetaDataVaToVa(struct MetaData_t *src, struct MetaData_t *dst);

int clearMetaData(struct private_handle_t *handle, enum DispParamType paramType);
int clearMetaDataVa(struct MetaData_t *data, enum DispParamType paramType);

unsigned long getMetaDataSize();

#ifdef __cplusplus
}
#endif

#endif /* _QDMETADATA_H */

