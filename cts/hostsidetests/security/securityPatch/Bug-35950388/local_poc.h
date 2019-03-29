/**
 * Copyright (C) 2017 The Android Open Source Project
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


#ifndef __CMD_H__
#define __CMD_H__

#define _IOC_NRBITS     8
#define _IOC_TYPEBITS   8

/*
 * Let any architecture override either of the following before
 * including this file.
 */

#ifndef _IOC_SIZEBITS
# define _IOC_SIZEBITS  14
#endif

#ifndef _IOC_DIRBITS
# define _IOC_DIRBITS   2
#endif

#define _IOC_NRMASK     ((1 << _IOC_NRBITS)-1)
#define _IOC_TYPEMASK   ((1 << _IOC_TYPEBITS)-1)
#define _IOC_SIZEMASK   ((1 << _IOC_SIZEBITS)-1)
#define _IOC_DIRMASK    ((1 << _IOC_DIRBITS)-1)

#define _IOC_NRSHIFT    0
#define _IOC_TYPESHIFT  (_IOC_NRSHIFT+_IOC_NRBITS)
#define _IOC_SIZESHIFT  (_IOC_TYPESHIFT+_IOC_TYPEBITS)
#define _IOC_DIRSHIFT   (_IOC_SIZESHIFT+_IOC_SIZEBITS)

/*
 * Direction bits, which any architecture can choose to override
 * before including this file.
 */

#ifndef _IOC_NONE
# define _IOC_NONE      0U
#endif

#ifndef _IOC_WRITE
# define _IOC_WRITE     1U
#endif

#ifndef _IOC_READ
# define _IOC_READ      2U
#endif



#define _IOC_TYPECHECK(t) (sizeof(t))
#define _IOC(dir,type,nr,size) \
        (((dir)  << _IOC_DIRSHIFT) | \
         ((type) << _IOC_TYPESHIFT) | \
         ((nr)   << _IOC_NRSHIFT) | \
         ((size) << _IOC_SIZESHIFT))



/* used to create numbers */
#define _IO(type,nr)            _IOC(_IOC_NONE,(type),(nr),0)
#define _IOR(type,nr,size)      _IOC(_IOC_READ,(type),(nr),(_IOC_TYPECHECK(size)))
#define _IOW(type,nr,size)      _IOC(_IOC_WRITE,(type),(nr),(_IOC_TYPECHECK(size)))
#define _IOWR(type,nr,size)     _IOC(_IOC_READ|_IOC_WRITE,(type),(nr),(_IOC_TYPECHECK(size)))



struct mult_factor {
        uint32_t numer;
        uint32_t denom;
};

struct mdp_rotation_buf_info {
        uint32_t width;
        uint32_t height;
        uint32_t format;
        struct mult_factor comp_ratio;
};

struct mdp_rotation_config {
        uint32_t        version;
        uint32_t        session_id;
        struct mdp_rotation_buf_info        input;
        struct mdp_rotation_buf_info        output;
        uint32_t        frame_rate;
        uint32_t        flags;
        uint32_t        reserved[6];
};


struct mdp_rect {
        uint32_t x;
        uint32_t y;
        uint32_t w;
        uint32_t h;
};




struct mdp_layer_plane {
        /* DMA buffer file descriptor information. */
        int fd;

        /* Pixel offset in the dma buffer. */
        uint32_t offset;

        /* Number of bytes in one scan line including padding bytes. */
        uint32_t stride;
};

#define MAX_PLANES        4


struct mdp_layer_buffer {
        /* layer width in pixels. */
        uint32_t width;

        /* layer height in pixels. */
        uint32_t height;

        /*
         * layer format in DRM-style fourcc, refer drm_fourcc.h for
         * standard formats
         */
        uint32_t format;

        /* plane to hold the fd, offset, etc for all color components */
        struct mdp_layer_plane planes[MAX_PLANES];

        /* valid planes count in layer planes list */
        uint32_t plane_count;

        /* compression ratio factor, value depends on the pixel format */
        struct mult_factor comp_ratio;

        /*
         * SyncFence associated with this buffer. It is used in two ways.
         *
         * 1. Driver waits to consume the buffer till producer signals in case
         * of primary and external display.
         *
         * 2. Writeback device uses buffer structure for output buffer where
         * driver is producer. However, client sends the fence with buffer to
         * indicate that consumer is still using the buffer and it is not ready
         * for new content.
         */
        int         fence;

        /* 32bits reserved value for future usage. */
        uint32_t reserved;
};


struct mdp_rotation_item {
        /* rotation request flag */
        uint32_t        flags;

        /* Source crop rectangle */
        struct mdp_rect        src_rect;

        /* Destination rectangle */
        struct mdp_rect        dst_rect;

        /* Input buffer for the request */
        struct mdp_layer_buffer        input;

        /* The output buffer for the request */
        struct mdp_layer_buffer        output;

        /*
          * DMA pipe selection for this request by client:
          * 0: DMA pipe 0
          * 1: DMA pipe 1
          * or MDSS_ROTATION_HW_ANY if client wants
          * driver to allocate any that is available
          */
        uint32_t        pipe_idx;

        /*
          * Write-back block selection for this request by client:
          * 0: Write-back block 0
          * 1: Write-back block 1
          * or MDSS_ROTATION_HW_ANY if client wants
          * driver to allocate any that is available
          */
        uint32_t        wb_idx;

        /* Which session ID is this request scheduled on */
        uint32_t        session_id;

        /* 32bits reserved value for future usage */
        uint32_t        reserved[6];
};

struct mdp_rotation_request {
        /* 32bit version indicates the request structure */
        uint32_t        version;

        uint32_t        flags;

        /* Number of rotation request items in the list */
        uint32_t        count;

        /* Pointer to a list of rotation request items */
        struct mdp_rotation_item __user        *list;

        /* 32bits reserved value for future usage*/
        uint32_t        reserved[6];
};

#define MDSS_ROTATOR_IOCTL_MAGIC 'w'

/* open a rotation session */
#define MDSS_ROTATION_OPEN \
        _IOWR(MDSS_ROTATOR_IOCTL_MAGIC, 1, struct mdp_rotation_config *)

/* change the rotation session configuration */
#define MDSS_ROTATION_CONFIG \
        _IOWR(MDSS_ROTATOR_IOCTL_MAGIC, 2, struct mdp_rotation_config *)

/* queue the rotation request */
#define MDSS_ROTATION_REQUEST \
        _IOWR(MDSS_ROTATOR_IOCTL_MAGIC, 3, struct mdp_rotation_request *)

/* close a rotation session with the specified rotation session ID */
#define MDSS_ROTATION_CLOSE        _IOW(MDSS_ROTATOR_IOCTL_MAGIC, 4, unsigned int)




#define MDP_IMGTYPE_END 0x100
#define MDP_IMGTYPE2_START 0x10000

enum {
        MDP_RGB_565,      /* RGB 565 planer */
        MDP_XRGB_8888,    /* RGB 888 padded */
        MDP_Y_CBCR_H2V2,  /* Y and CbCr, pseudo planer w/ Cb is in MSB */
        MDP_Y_CBCR_H2V2_ADRENO,
        MDP_ARGB_8888,    /* ARGB 888 */
        MDP_RGB_888,      /* RGB 888 planer */
        MDP_Y_CRCB_H2V2,  /* Y and CrCb, pseudo planer w/ Cr is in MSB */
        MDP_YCRYCB_H2V1,  /* YCrYCb interleave */
        MDP_CBYCRY_H2V1,  /* CbYCrY interleave */
        MDP_Y_CRCB_H2V1,  /* Y and CrCb, pseduo planer w/ Cr is in MSB */
        MDP_Y_CBCR_H2V1,   /* Y and CrCb, pseduo planer w/ Cr is in MSB */
        MDP_Y_CRCB_H1V2,
        MDP_Y_CBCR_H1V2,
        MDP_RGBA_8888,    /* ARGB 888 */
        MDP_BGRA_8888,          /* ABGR 888 */
        MDP_RGBX_8888,          /* RGBX 888 */
        MDP_Y_CRCB_H2V2_TILE,  /* Y and CrCb, pseudo planer tile */
        MDP_Y_CBCR_H2V2_TILE,  /* Y and CbCr, pseudo planer tile */
        MDP_Y_CR_CB_H2V2,  /* Y, Cr and Cb, planar */
        MDP_Y_CR_CB_GH2V2,  /* Y, Cr and Cb, planar aligned to Android YV12 */
        MDP_Y_CB_CR_H2V2,  /* Y, Cb and Cr, planar */
        MDP_Y_CRCB_H1V1,  /* Y and CrCb, pseduo planer w/ Cr is in MSB */
        MDP_Y_CBCR_H1V1,  /* Y and CbCr, pseduo planer w/ Cb is in MSB */
        MDP_YCRCB_H1V1,   /* YCrCb interleave */
        MDP_YCBCR_H1V1,   /* YCbCr interleave */
        MDP_BGR_565,      /* BGR 565 planer */
        MDP_BGR_888,      /* BGR 888 */
        MDP_Y_CBCR_H2V2_VENUS,
        MDP_BGRX_8888,   /* BGRX 8888 */
        MDP_RGBA_8888_TILE,          /* RGBA 8888 in tile format */
        MDP_ARGB_8888_TILE,          /* ARGB 8888 in tile format */
        MDP_ABGR_8888_TILE,          /* ABGR 8888 in tile format */
        MDP_BGRA_8888_TILE,          /* BGRA 8888 in tile format */
        MDP_RGBX_8888_TILE,          /* RGBX 8888 in tile format */
        MDP_XRGB_8888_TILE,          /* XRGB 8888 in tile format */
        MDP_XBGR_8888_TILE,          /* XBGR 8888 in tile format */
        MDP_BGRX_8888_TILE,          /* BGRX 8888 in tile format */
        MDP_YCBYCR_H2V1,  /* YCbYCr interleave */
        MDP_RGB_565_TILE,          /* RGB 565 in tile format */
        MDP_BGR_565_TILE,          /* BGR 565 in tile format */
        MDP_ARGB_1555,        /*ARGB 1555*/
        MDP_RGBA_5551,        /*RGBA 5551*/
        MDP_ARGB_4444,        /*ARGB 4444*/
        MDP_RGBA_4444,        /*RGBA 4444*/
        MDP_RGB_565_UBWC,
        MDP_RGBA_8888_UBWC,
        MDP_Y_CBCR_H2V2_UBWC,
        MDP_RGBX_8888_UBWC,
        MDP_Y_CRCB_H2V2_VENUS,
        MDP_IMGTYPE_LIMIT,
        MDP_RGB_BORDERFILL,        /* border fill pipe */
        MDP_XRGB_1555,
        MDP_RGBX_5551,
        MDP_XRGB_4444,
        MDP_RGBX_4444,
        MDP_ABGR_1555,
        MDP_BGRA_5551,
        MDP_XBGR_1555,
        MDP_BGRX_5551,
        MDP_ABGR_4444,
        MDP_BGRA_4444,
        MDP_XBGR_4444,
        MDP_BGRX_4444,
        MDP_ABGR_8888,
        MDP_XBGR_8888,
        MDP_RGBA_1010102,
        MDP_ARGB_2101010,
        MDP_RGBX_1010102,
        MDP_XRGB_2101010,
        MDP_BGRA_1010102,
        MDP_ABGR_2101010,
        MDP_BGRX_1010102,
        MDP_XBGR_2101010,
        MDP_RGBA_1010102_UBWC,
        MDP_RGBX_1010102_UBWC,
        MDP_Y_CBCR_H2V2_P010,
        MDP_Y_CBCR_H2V2_TP10_UBWC,
        MDP_CRYCBY_H2V1,  /* CrYCbY interleave */
        MDP_IMGTYPE_LIMIT1 = MDP_IMGTYPE_END,
        MDP_FB_FORMAT = MDP_IMGTYPE2_START,    /* framebuffer format */
        MDP_IMGTYPE_LIMIT2 /* Non valid image type after this enum */
};

#endif

