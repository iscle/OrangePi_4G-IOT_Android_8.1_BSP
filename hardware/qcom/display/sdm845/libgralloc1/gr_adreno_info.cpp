/*
 * Copyright (c) 2011-2017, The Linux Foundation. All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
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

#include <cutils/log.h>
#include <cutils/properties.h>
#include <dlfcn.h>
#include <mutex>

#include "gralloc_priv.h"
#include "gr_adreno_info.h"
#include "gr_utils.h"

using std::lock_guard;
using std::mutex;

namespace gralloc1 {

AdrenoMemInfo *AdrenoMemInfo::s_instance = nullptr;

AdrenoMemInfo *AdrenoMemInfo::GetInstance() {
  static mutex s_lock;
  lock_guard<mutex> obj(s_lock);
  if (!s_instance) {
    s_instance = new AdrenoMemInfo();
    if (!s_instance->Init()) {
      delete s_instance;
      s_instance = nullptr;
    }
  }

  return s_instance;
}

bool AdrenoMemInfo::Init() {
  libadreno_utils_ = ::dlopen("libadreno_utils.so", RTLD_NOW);
  if (libadreno_utils_) {
    *reinterpret_cast<void **>(&LINK_adreno_compute_aligned_width_and_height) =
        ::dlsym(libadreno_utils_, "compute_aligned_width_and_height");
    *reinterpret_cast<void **>(&LINK_adreno_compute_fmt_aligned_width_and_height) =
        ::dlsym(libadreno_utils_, "compute_fmt_aligned_width_and_height");
    *reinterpret_cast<void **>(&LINK_adreno_compute_padding) =
        ::dlsym(libadreno_utils_, "compute_surface_padding");
    *reinterpret_cast<void **>(&LINK_adreno_compute_compressedfmt_aligned_width_and_height) =
        ::dlsym(libadreno_utils_, "compute_compressedfmt_aligned_width_and_height");
    *reinterpret_cast<void **>(&LINK_adreno_isUBWCSupportedByGpu) =
        ::dlsym(libadreno_utils_, "isUBWCSupportedByGpu");
    *reinterpret_cast<void **>(&LINK_adreno_get_gpu_pixel_alignment) =
        ::dlsym(libadreno_utils_, "get_gpu_pixel_alignment");
  } else {
    ALOGE(" Failed to load libadreno_utils.so");
    return false;
  }

  // Check if the overriding property debug.gralloc.gfx_ubwc_disable
  // that disables UBWC allocations for the graphics stack is set
  char property[PROPERTY_VALUE_MAX];
  property_get("debug.gralloc.gfx_ubwc_disable", property, "0");
  if (!(strncmp(property, "1", PROPERTY_VALUE_MAX)) ||
      !(strncmp(property, "true", PROPERTY_VALUE_MAX))) {
    gfx_ubwc_disable_ = true;
  }

  if ((property_get("debug.gralloc.map_fb_memory", property, NULL) > 0) &&
      (!strncmp(property, "1", PROPERTY_VALUE_MAX) ||
       (!strncasecmp(property, "true", PROPERTY_VALUE_MAX)))) {
    map_fb_ = true;
  }

  return true;
}

AdrenoMemInfo::~AdrenoMemInfo() {
  if (libadreno_utils_) {
    ::dlclose(libadreno_utils_);
  }
}

void AdrenoMemInfo::AlignUnCompressedRGB(int width, int height, int format, int tile_enabled,
                                         unsigned int *aligned_w, unsigned int *aligned_h) {
  *aligned_w = (unsigned int)ALIGN(width, 32);
  *aligned_h = (unsigned int)ALIGN(height, 32);

  // Don't add any additional padding if debug.gralloc.map_fb_memory
  // is enabled
  if (map_fb_) {
    return;
  }

  int bpp = 4;
  switch (format) {
    case HAL_PIXEL_FORMAT_RGB_888:
    case HAL_PIXEL_FORMAT_BGR_888:
      bpp = 3;
      break;
    case HAL_PIXEL_FORMAT_RGB_565:
    case HAL_PIXEL_FORMAT_BGR_565:
    case HAL_PIXEL_FORMAT_RGBA_5551:
    case HAL_PIXEL_FORMAT_RGBA_4444:
      bpp = 2;
      break;
    default:
      break;
  }

  int raster_mode = 0;          // Adreno unknown raster mode.
  int padding_threshold = 512;  // Threshold for padding surfaces.
  // the function below computes aligned width and aligned height
  // based on linear or macro tile mode selected.
  if (LINK_adreno_compute_fmt_aligned_width_and_height) {
    // We call into adreno_utils only for RGB formats. So plane_id is 0 and
    // num_samples is 1 always. We may  have to add uitility function to
    // find out these if there is a need to call this API for YUV formats.
    LINK_adreno_compute_fmt_aligned_width_and_height(
        width, height, 0/*plane_id*/, GetGpuPixelFormat(format), 1/*num_samples*/,
        tile_enabled, raster_mode, padding_threshold,
        reinterpret_cast<int *>(aligned_w), reinterpret_cast<int *>(aligned_h));
  } else if (LINK_adreno_compute_aligned_width_and_height) {
    LINK_adreno_compute_aligned_width_and_height(
        width, height, bpp, tile_enabled, raster_mode, padding_threshold,
        reinterpret_cast<int *>(aligned_w), reinterpret_cast<int *>(aligned_h));
  } else if (LINK_adreno_compute_padding) {
    int surface_tile_height = 1;  // Linear surface
    *aligned_w = UINT(LINK_adreno_compute_padding(width, bpp, surface_tile_height, raster_mode,
                                                  padding_threshold));
    ALOGW("%s: Warning!! Old GFX API is used to calculate stride", __FUNCTION__);
  } else {
    ALOGW(
        "%s: Warning!! Symbols compute_surface_padding and "
        "compute_fmt_aligned_width_and_height and "
        "compute_aligned_width_and_height not found",
        __FUNCTION__);
  }
}

void AdrenoMemInfo::AlignCompressedRGB(int width, int height, int format, unsigned int *aligned_w,
                                       unsigned int *aligned_h) {
  if (LINK_adreno_compute_compressedfmt_aligned_width_and_height) {
    int bytesPerPixel = 0;
    int raster_mode = 0;          // Adreno unknown raster mode.
    int padding_threshold = 512;  // Threshold for padding
    // surfaces.

    LINK_adreno_compute_compressedfmt_aligned_width_and_height(
        width, height, format, 0, raster_mode, padding_threshold,
        reinterpret_cast<int *>(aligned_w), reinterpret_cast<int *>(aligned_h), &bytesPerPixel);
  } else {
    ALOGW("%s: Warning!! compute_compressedfmt_aligned_width_and_height not found", __FUNCTION__);
  }
}

bool AdrenoMemInfo::IsUBWCSupportedByGPU(int format) {
  if (!gfx_ubwc_disable_ && LINK_adreno_isUBWCSupportedByGpu) {
    ADRENOPIXELFORMAT gpu_format = GetGpuPixelFormat(format);
    return LINK_adreno_isUBWCSupportedByGpu(gpu_format);
  }

  return false;
}

uint32_t AdrenoMemInfo::GetGpuPixelAlignment() {
  if (LINK_adreno_get_gpu_pixel_alignment) {
    return LINK_adreno_get_gpu_pixel_alignment();
  }

  return 1;
}

ADRENOPIXELFORMAT AdrenoMemInfo::GetGpuPixelFormat(int hal_format) {
  switch (hal_format) {
    case HAL_PIXEL_FORMAT_RGBA_8888:
      return ADRENO_PIXELFORMAT_R8G8B8A8;
    case HAL_PIXEL_FORMAT_RGBX_8888:
      return ADRENO_PIXELFORMAT_R8G8B8X8;
    case HAL_PIXEL_FORMAT_RGB_565:
      return ADRENO_PIXELFORMAT_B5G6R5;
    case HAL_PIXEL_FORMAT_BGR_565:
      return ADRENO_PIXELFORMAT_R5G6B5;
    case HAL_PIXEL_FORMAT_NV12_ENCODEABLE:
      return ADRENO_PIXELFORMAT_NV12;
    case HAL_PIXEL_FORMAT_YCbCr_420_SP_VENUS:
    case HAL_PIXEL_FORMAT_YCbCr_420_SP_VENUS_UBWC:
      return ADRENO_PIXELFORMAT_NV12_EXT;
    case HAL_PIXEL_FORMAT_YCbCr_420_TP10_UBWC:
      return ADRENO_PIXELFORMAT_TP10;
    case HAL_PIXEL_FORMAT_YCbCr_420_P010:
    case HAL_PIXEL_FORMAT_YCbCr_420_P010_UBWC:
      return ADRENO_PIXELFORMAT_P010;
    case HAL_PIXEL_FORMAT_RGBA_1010102:
       return ADRENO_PIXELFORMAT_R10G10B10A2_UNORM;
    case HAL_PIXEL_FORMAT_RGBX_1010102:
       return ADRENO_PIXELFORMAT_R10G10B10X2_UNORM;
    case HAL_PIXEL_FORMAT_ABGR_2101010:
       return ADRENO_PIXELFORMAT_A2B10G10R10_UNORM;
    default:
      ALOGE("%s: No map for format: 0x%x", __FUNCTION__, hal_format);
      break;
  }

  return ADRENO_PIXELFORMAT_UNKNOWN;
}

}  // namespace gralloc1
