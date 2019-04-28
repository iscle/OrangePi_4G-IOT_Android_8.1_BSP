#ifndef ANDROID_HARDWARE_NFC_V1_0_NFC_H
#define ANDROID_HARDWARE_NFC_V1_0_NFC_H

#include <android/hardware/nfc/1.0/INfc.h>
#include <hidl/Status.h>
#include <hardware/hardware.h>
#include <hardware/nfc.h>
namespace android {
namespace hardware {
namespace nfc {
namespace V1_0 {
namespace implementation {

using ::android::hardware::nfc::V1_0::INfc;
using ::android::hardware::nfc::V1_0::INfcClientCallback;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct NfcDeathRecipient : hidl_death_recipient {
    NfcDeathRecipient(const sp<INfc> nfc) : mNfc(nfc) {
    }

    virtual void serviceDied(uint64_t /*cookie*/, const wp<::android::hidl::base::V1_0::IBase>& /*who*/) {
        mNfc->close();
    }
    sp<INfc> mNfc;
};

struct Nfc : public INfc {
  Nfc(nfc_nci_device_t* device);
  ::android::hardware::Return<NfcStatus> open(const sp<INfcClientCallback>& clientCallback)  override;
  ::android::hardware::Return<uint32_t> write(const hidl_vec<uint8_t>& data)  override;
  ::android::hardware::Return<NfcStatus> coreInitialized(const hidl_vec<uint8_t>& data)  override;
  ::android::hardware::Return<NfcStatus> prediscover()  override;
  ::android::hardware::Return<NfcStatus> close()  override;
  ::android::hardware::Return<NfcStatus> controlGranted()  override;
  ::android::hardware::Return<NfcStatus> powerCycle()  override;

  static void eventCallback(uint8_t event, uint8_t status) {
      if (mCallback != nullptr) {
          auto ret = mCallback->sendEvent(
                  (::android::hardware::nfc::V1_0::NfcEvent) event,
                  (::android::hardware::nfc::V1_0::NfcStatus) status);
          if (!ret.isOk()) {
              ALOGW("Failed to call back into NFC process.");
          }
      }
  }
  static void dataCallback(uint16_t data_len, uint8_t* p_data) {
      hidl_vec<uint8_t> data;
      data.setToExternal(p_data, data_len);
      if (mCallback != nullptr) {
          auto ret = mCallback->sendData(data);
          if (!ret.isOk()) {
              ALOGW("Failed to call back into NFC process.");
          }
      }
  }
  private:
    static sp<INfcClientCallback> mCallback;
    const nfc_nci_device_t*       mDevice;
    sp<NfcDeathRecipient>         mDeathRecipient;
};

extern "C" INfc* HIDL_FETCH_INfc(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace nfc
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_NFC_V1_0_NFC_H
