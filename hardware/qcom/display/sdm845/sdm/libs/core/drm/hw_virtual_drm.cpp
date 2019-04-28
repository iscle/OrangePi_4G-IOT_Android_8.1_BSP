/*
Copyright (c) 2017, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#include <stdio.h>
#include <ctype.h>
#include <drm_logger.h>
#include <utils/debug.h>
#include "hw_device_drm.h"
#include "hw_virtual_drm.h"
#include "hw_info_drm.h"

#define __CLASS__ "HWVirtualDRM"

using sde_drm::DRMDisplayType;
using sde_drm::DRMConnectorInfo;
using sde_drm::DRMRect;
using sde_drm::DRMOps;

namespace sdm {

HWVirtualDRM::HWVirtualDRM(BufferSyncHandler *buffer_sync_handler,
                           BufferAllocator *buffer_allocator,
                           HWInfoInterface *hw_info_intf)
                           : HWDeviceDRM(buffer_sync_handler, buffer_allocator, hw_info_intf) {
  HWDeviceDRM::deferred_initialize_ = true;
  HWDeviceDRM::device_name_ = "Virtual Display Device";
  HWDeviceDRM::hw_info_intf_ = hw_info_intf;
  HWDeviceDRM::disp_type_ = DRMDisplayType::VIRTUAL;
}

DisplayError HWVirtualDRM::Init() {
  return kErrorNone;
}

DisplayError HWVirtualDRM::DeferredInit() {
  if (HWDeviceDRM::Init() != kErrorNone)
    return kErrorResources;

  drm_mgr_intf_->SetScalerLUT(drm_lut_info_);
  DLOGI_IF(kTagDriverConfig, "Setup CRTC %d, Connector %d for %s",
            token_.crtc_id, token_.conn_id, device_name_);

  return kErrorNone;
}

void HWVirtualDRM::ConfigureWbConnectorFbId(uint32_t fb_id) {
  drm_atomic_intf_->Perform(DRMOps::CONNECTOR_SET_OUTPUT_FB_ID, token_.conn_id, fb_id);
  return;
}

void HWVirtualDRM::ConfigureWbConnectorDestRect() {
  DRMRect dst = {};
  dst.left = 0;
  dst.bottom = height_;
  dst.top = 0;
  dst.right = width_;
  drm_atomic_intf_->Perform(DRMOps::CONNECTOR_SET_OUTPUT_RECT, token_.conn_id, dst);
  return;
}

void HWVirtualDRM::InitializeConfigs() {
  current_mode_.hdisplay = current_mode_.hsync_start = current_mode_.hsync_end \
  = current_mode_.htotal = (uint16_t) width_;
  current_mode_.vdisplay = current_mode_.vsync_start = current_mode_.vsync_end \
  = current_mode_.vtotal = (uint16_t) height_;
  // Not sure SF has a way to configure refresh rate. Hardcoding to 60 fps for now.
  // TODO(user): Make this configurable.
  current_mode_.vrefresh = 60;
  current_mode_.clock = (current_mode_.htotal * current_mode_.vtotal \
  * current_mode_.vrefresh) / 1000;
  struct sde_drm_wb_cfg wb_cfg;
  wb_cfg.connector_id = token_.conn_id;
  wb_cfg.flags |= SDE_DRM_WB_CFG_FLAGS_CONNECTED;
  wb_cfg.count_modes = 1;
  wb_cfg.modes = (uint64_t)&current_mode_;
  #ifdef DRM_IOCTL_SDE_WB_CONFIG
  int ret = drmIoctl(dev_fd_, DRM_IOCTL_SDE_WB_CONFIG, &wb_cfg);
  #endif
  if (ret) {
    DLOGE("WB config failed\n");
  } else {
    drm_mgr_intf_->GetConnectorInfo(token_.conn_id, &connector_info_);
    current_mode_ = connector_info_.modes[0];
    DumpConfigs();
  }
}

void HWVirtualDRM::DumpConfigs() {
  for (uint32_t i = 0; i < (uint32_t)connector_info_.num_modes; i++) {
  DLOGI(
    "Name: %s\tvref: %d\thdisp: %d\t hsync_s: %d\thsync_e:%d\thtotal: %d\t"
    "vdisp: %d\tvsync_s: %d\tvsync_e: %d\tvtotal: %d\n",
    connector_info_.modes[i].name, connector_info_.modes[i].vrefresh,
    connector_info_.modes[i].hdisplay,
    connector_info_.modes[i].hsync_start, connector_info_.modes[i].hsync_end,
    connector_info_.modes[i].htotal, connector_info_.modes[i].vdisplay,
    connector_info_.modes[i].vsync_start, connector_info_.modes[i].vsync_end,
    connector_info_.modes[i].vtotal);
  }
}

DisplayError HWVirtualDRM::Commit(HWLayers *hw_layers) {
  LayerBuffer *output_buffer = hw_layers->info.stack->output_buffer;
  DisplayError err = kErrorNone;

  registry_.RegisterCurrent(hw_layers);
  registry_.MapBufferToFbId(output_buffer);
  uint32_t fb_id = registry_.GetFbId(output_buffer->planes[0].fd);

  ConfigureWbConnectorFbId(fb_id);
  ConfigureWbConnectorDestRect();

  err = HWDeviceDRM::AtomicCommit(hw_layers);
  registry_.UnregisterNext();
  return(err);
}

DisplayError HWVirtualDRM::Validate(HWLayers *hw_layers) {
  // TODO(user) : Add validate support
  return kErrorNone;
}


DisplayError HWVirtualDRM::SetDisplayAttributes(const HWDisplayAttributes &display_attributes) {
  if (display_attributes.x_pixels == 0 || display_attributes.y_pixels == 0) {
    return kErrorParameters;
  }

  display_attributes_ = display_attributes;

  if (display_attributes_.x_pixels > hw_resource_.max_mixer_width) {
    display_attributes_.is_device_split = true;
  }

  width_ = display_attributes_.x_pixels;
  height_ = display_attributes_.y_pixels;
  DeferredInit();

  return kErrorNone;
}

DisplayError HWVirtualDRM::GetPPFeaturesVersion(PPFeatureVersion *vers) {
  return kErrorNone;
}

DisplayError HWVirtualDRM::SetScaleLutConfig(HWScaleLutInfo *lut_info) {
  drm_lut_info_.cir_lut = lut_info->cir_lut;
  drm_lut_info_.dir_lut = lut_info->dir_lut;
  drm_lut_info_.sep_lut = lut_info->sep_lut;
  drm_lut_info_.cir_lut_size = lut_info->cir_lut_size;
  drm_lut_info_.dir_lut_size = lut_info->dir_lut_size;
  drm_lut_info_.sep_lut_size = lut_info->sep_lut_size;

  // Due to differed Init in WB case, we cannot set scaler config immediately as we
  // won't have SDE DRM initialized at this point. Hence have to cache LUT info here
  // and set it in ::DeferredInit

  return kErrorNone;
}

}  // namespace sdm

