/* Copyright (c) 2012 - 2013, 2015 The Linux Foundation. All rights reserved.
 *
 * redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * this software is provided "as is" and any express or implied
 * warranties, including, but not limited to, the implied warranties of
 * merchantability, fitness for a particular purpose and non-infringement
 * are disclaimed.  in no event shall the copyright owner or contributors
 * be liable for any direct, indirect, incidental, special, exemplary, or
 * consequential damages (including, but not limited to, procurement of
 * substitute goods or services; loss of use, data, or profits; or
 * business interruption) however caused and on any theory of liability,
 * whether in contract, strict liability, or tort (including negligence
 * or otherwise) arising in any way out of the use of this software, even
 * if advised of the possibility of such damage.
 *
 */

#ifndef C2D_ColorConverter_H_
#define C2D_ColorConverter_H_

#include <stdlib.h>
#include <fcntl.h>
#include <linux/msm_kgsl.h>
#include <sys/ioctl.h>
#include <utils/Log.h>
#include <dlfcn.h>
#include <string.h>
#include <errno.h>
#include <media/msm_media_info.h>
#include <gralloc_priv.h>
#include <unordered_map>

#include <c2d2.h>
#include <sys/types.h>

#undef LOG_TAG
#define LOG_TAG "C2DColorConvert"
#define ALIGN( num, to ) (((num) + (to-1)) & (~(to-1)))
#define ALIGN8K 8192
#define ALIGN4K 4096
#define ALIGN2K 2048
#define ALIGN128 128
#define ALIGN32 32
#define ALIGN16 16

typedef C2D_STATUS (*LINK_c2dCreateSurface)( uint32 *surface_id,
        uint32 surface_bits,
        C2D_SURFACE_TYPE surface_type,
        void *surface_definition );

typedef C2D_STATUS (*LINK_c2dUpdateSurface)( uint32 surface_id,
        uint32 surface_bits,
        C2D_SURFACE_TYPE surface_type,
        void *surface_definition );

typedef C2D_STATUS (*LINK_c2dReadSurface)( uint32 surface_id,
        C2D_SURFACE_TYPE surface_type,
        void *surface_definition,
        int32 x, int32 y );

typedef C2D_STATUS (*LINK_c2dDraw)( uint32 target_id,
        uint32 target_config, C2D_RECT *target_scissor,
        uint32 target_mask_id, uint32 target_color_key,
        C2D_OBJECT *objects_list, uint32 num_objects );

typedef C2D_STATUS (*LINK_c2dFlush)( uint32 target_id, c2d_ts_handle *timestamp);

typedef C2D_STATUS (*LINK_c2dFinish)( uint32 target_id);

typedef C2D_STATUS (*LINK_c2dWaitTimestamp)( c2d_ts_handle timestamp );

typedef C2D_STATUS (*LINK_c2dDestroySurface)( uint32 surface_id );

typedef C2D_STATUS (*LINK_c2dMapAddr)( int mem_fd, void * hostptr, uint32 len, uint32 offset, uint32 flags, void ** gpuaddr);

typedef C2D_STATUS (*LINK_c2dUnMapAddr)(void * gpuaddr);

typedef void (*LINK_AdrenoComputeAlignedWidthAndHeight) (int width, int height, int bpp, int tile_mode, int raster_mode,
                                                          int padding_threshold, int *aligned_width, int * aligned_height);

/*TODO: THIS NEEDS TO ENABLED FOR JB PLUS*/
enum ColorConvertFormat {
    RGB565 = 1,
    YCbCr420Tile,
    YCbCr420SP,
    YCbCr420P,
    YCrCb420P,
    RGBA8888,
    RGBA8888_UBWC,
    NV12_2K,
    NV12_128m,
    NV12_UBWC,
    NV12_TP10,
};

typedef struct {
    int32_t numerator;
    int32_t denominator;
} C2DBytesPerPixel;

typedef struct {
  int32_t width;
  int32_t height;
  int32_t stride;
  int32_t sliceHeight;
  int32_t lumaAlign;
  int32_t sizeAlign;
  int32_t size;
  C2DBytesPerPixel bpp;
} C2DBuffReq;

typedef enum {
  C2D_INPUT = 0,
  C2D_OUTPUT,
} C2D_PORT;

typedef std::unordered_map <int, int> ColorMapping;

class C2DColorConverter{

  void *mC2DLibHandle;
  LINK_c2dCreateSurface mC2DCreateSurface;
  LINK_c2dUpdateSurface mC2DUpdateSurface;
  LINK_c2dReadSurface mC2DReadSurface;
  LINK_c2dDraw mC2DDraw;
  LINK_c2dFlush mC2DFlush;
  LINK_c2dFinish mC2DFinish;
  LINK_c2dWaitTimestamp mC2DWaitTimestamp;
  LINK_c2dDestroySurface mC2DDestroySurface;
  LINK_c2dMapAddr mC2DMapAddr;
  LINK_c2dUnMapAddr mC2DUnMapAddr;

  void *mAdrenoUtilsHandle;
  LINK_AdrenoComputeAlignedWidthAndHeight mAdrenoComputeAlignedWidthAndHeight;

  uint32_t mSrcSurface, mDstSurface;
  void * mSrcSurfaceDef;
  void * mDstSurfaceDef;

  C2D_OBJECT mBlit;
  size_t mSrcWidth;
  size_t mSrcHeight;
  size_t mSrcStride;
  size_t mDstWidth;
  size_t mDstHeight;
  size_t mSrcSize;
  size_t mDstSize;
  size_t mSrcYSize;
  size_t mDstYSize;
  ColorConvertFormat mSrcFormat;
  ColorConvertFormat mDstFormat;
  int32_t mFlags;

  bool enabled;

  pthread_mutex_t mLock;

 public:
  C2DColorConverter();
  ~C2DColorConverter();

  ColorMapping mMapCovertor2PixelFormat;
  ColorMapping mMapPixelFormat2Covertor;

  bool setResolution(size_t srcWidth, size_t srcHeight, size_t dstWidth,
                     size_t dstHeight, ColorConvertFormat srcFormat,
                     ColorConvertFormat dstFormat, int32_t flags,
                     size_t srcStride);
  int32_t getBuffSize(int32_t port);
  bool getBuffFilledLen(int32_t port, unsigned int &filled_length);
  bool getBuffReq(int32_t port, C2DBuffReq *req);
  int32_t dumpOutput(char * filename, char mode);
  bool convertC2D(int srcFd, void *srcBase, void * srcData,
                  int dstFd, void *dstBase, void * dstData);
  bool isYUVSurface(ColorConvertFormat format);
  int32_t getDummySurfaceDef(ColorConvertFormat format, size_t width,
                             size_t height, bool isSource);
  C2D_STATUS updateYUVSurfaceDef(int fd, void *base, void * data, bool isSource);
  C2D_STATUS updateRGBSurfaceDef(int fd, void * data, bool isSource);
  uint32_t getC2DFormat(ColorConvertFormat format);
  size_t calcStride(ColorConvertFormat format, size_t width);
  size_t calcYSize(ColorConvertFormat format, size_t width, size_t height);
  size_t calcSize(ColorConvertFormat format, size_t width, size_t height);
  void *getMappedGPUAddr(int bufFD, void *bufPtr, size_t bufLen);
  bool unmapGPUAddr(unsigned long gAddr);
  size_t calcLumaAlign(ColorConvertFormat format);
  size_t calcSizeAlign(ColorConvertFormat format);
  C2DBytesPerPixel calcBytesPerPixel(ColorConvertFormat format);
};

#endif  // C2D_ColorConverter_H_
