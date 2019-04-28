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

#ifndef WIFI_NAN_IFACE_H_
#define WIFI_NAN_IFACE_H_

#include <android-base/macros.h>
#include <android/hardware/wifi/1.0/IWifiNanIface.h>
#include <android/hardware/wifi/1.0/IWifiNanIfaceEventCallback.h>

#include "hidl_callback_util.h"
#include "wifi_legacy_hal.h"

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
using namespace android::hardware::wifi::V1_0;

/**
 * HIDL interface object used to control a NAN Iface instance.
 */
class WifiNanIface : public V1_0::IWifiNanIface {
 public:
  WifiNanIface(const std::string& ifname,
               const std::weak_ptr<legacy_hal::WifiLegacyHal> legacy_hal);
  // Refer to |WifiChip::invalidate()|.
  void invalidate();
  bool isValid();

  // HIDL methods exposed.
  Return<void> getName(getName_cb hidl_status_cb) override;
  Return<void> getType(getType_cb hidl_status_cb) override;
  Return<void> registerEventCallback(
      const sp<IWifiNanIfaceEventCallback>& callback,
      registerEventCallback_cb hidl_status_cb) override;
  Return<void> getCapabilitiesRequest(uint16_t cmd_id,
                                      getCapabilitiesRequest_cb hidl_status_cb) override;
  Return<void> enableRequest(uint16_t cmd_id,
                             const NanEnableRequest& msg,
                             enableRequest_cb hidl_status_cb) override;
  Return<void> configRequest(uint16_t cmd_id,
                             const NanConfigRequest& msg,
                             configRequest_cb hidl_status_cb) override;
  Return<void> disableRequest(uint16_t cmd_id,
                              disableRequest_cb hidl_status_cb) override;
  Return<void> startPublishRequest(uint16_t cmd_id,
                                   const NanPublishRequest& msg,
                                   startPublishRequest_cb hidl_status_cb) override;
  Return<void> stopPublishRequest(uint16_t cmd_id,
                                  uint8_t sessionId,
                                  stopPublishRequest_cb hidl_status_cb) override;
  Return<void> startSubscribeRequest(uint16_t cmd_id,
                                     const NanSubscribeRequest& msg,
                                    startSubscribeRequest_cb hidl_status_cb) override;
  Return<void> stopSubscribeRequest(uint16_t cmd_id,
                                    uint8_t sessionId,
                                    stopSubscribeRequest_cb hidl_status_cb) override;
  Return<void> transmitFollowupRequest(uint16_t cmd_id,
                                       const NanTransmitFollowupRequest& msg,
                                       transmitFollowupRequest_cb hidl_status_cb) override;
  Return<void> createDataInterfaceRequest(uint16_t cmd_id,
                                          const hidl_string& iface_name,
                                          createDataInterfaceRequest_cb hidl_status_cb) override;
  Return<void> deleteDataInterfaceRequest(uint16_t cmd_id,
                                          const hidl_string& iface_name,
                                          deleteDataInterfaceRequest_cb hidl_status_cb) override;
  Return<void> initiateDataPathRequest(uint16_t cmd_id,
                                       const NanInitiateDataPathRequest& msg,
                                       initiateDataPathRequest_cb hidl_status_cb) override;
  Return<void> respondToDataPathIndicationRequest(
      uint16_t cmd_id,
      const NanRespondToDataPathIndicationRequest& msg,
      respondToDataPathIndicationRequest_cb hidl_status_cb) override;
  Return<void> terminateDataPathRequest(uint16_t cmd_id,
                                        uint32_t ndpInstanceId,
                                        terminateDataPathRequest_cb hidl_status_cb) override;

 private:
  // Corresponding worker functions for the HIDL methods.
  std::pair<WifiStatus, std::string> getNameInternal();
  std::pair<WifiStatus, IfaceType> getTypeInternal();
  WifiStatus registerEventCallbackInternal(
      const sp<IWifiNanIfaceEventCallback>& callback);
  WifiStatus getCapabilitiesRequestInternal(uint16_t cmd_id);
  WifiStatus enableRequestInternal(uint16_t cmd_id,
                                   const NanEnableRequest& msg);
  WifiStatus configRequestInternal(uint16_t cmd_id,
                                   const NanConfigRequest& msg);
  WifiStatus disableRequestInternal(uint16_t cmd_id);
  WifiStatus startPublishRequestInternal(uint16_t cmd_id,
                                         const NanPublishRequest& msg);
  WifiStatus stopPublishRequestInternal(uint16_t cmd_id, uint8_t sessionId);
  WifiStatus startSubscribeRequestInternal(uint16_t cmd_id,
                                           const NanSubscribeRequest& msg);
  WifiStatus stopSubscribeRequestInternal(uint16_t cmd_id, uint8_t sessionId);
  WifiStatus transmitFollowupRequestInternal(
      uint16_t cmd_id, const NanTransmitFollowupRequest& msg);
  WifiStatus createDataInterfaceRequestInternal(uint16_t cmd_id,
                                                const std::string& iface_name);
  WifiStatus deleteDataInterfaceRequestInternal(uint16_t cmd_id,
                                                const std::string& iface_name);
  WifiStatus initiateDataPathRequestInternal(
      uint16_t cmd_id, const NanInitiateDataPathRequest& msg);
  WifiStatus respondToDataPathIndicationRequestInternal(
      uint16_t cmd_id, const NanRespondToDataPathIndicationRequest& msg);
  WifiStatus terminateDataPathRequestInternal(
      uint16_t cmd_id, uint32_t ndpInstanceId);

  std::set<sp<IWifiNanIfaceEventCallback>> getEventCallbacks();

  std::string ifname_;
  std::weak_ptr<legacy_hal::WifiLegacyHal> legacy_hal_;
  bool is_valid_;
  hidl_callback_util::HidlCallbackHandler<IWifiNanIfaceEventCallback>
      event_cb_handler_;

  DISALLOW_COPY_AND_ASSIGN(WifiNanIface);
};

}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // WIFI_NAN_IFACE_H_
