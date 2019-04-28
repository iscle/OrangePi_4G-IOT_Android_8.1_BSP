#define LOG_TAG "android.hardware.nfc@1.0-impl"

#include <log/log.h>

#include <hardware/hardware.h>
#include <hardware/nfc.h>
#include "Nfc.h"

namespace android {
namespace hardware {
namespace nfc {
namespace V1_0 {
namespace implementation {

sp<INfcClientCallback> Nfc::mCallback = nullptr;

Nfc::Nfc(nfc_nci_device_t* device) : mDevice(device),
    mDeathRecipient(new NfcDeathRecipient(this)) {
}

// Methods from ::android::hardware::nfc::V1_0::INfc follow.
::android::hardware::Return<NfcStatus> Nfc::open(const sp<INfcClientCallback>& clientCallback)  {
    mCallback = clientCallback;

    if (mDevice == nullptr || mCallback == nullptr) {
        return NfcStatus::FAILED;
    }
    mCallback->linkToDeath(mDeathRecipient, 0 /*cookie*/);
    int ret = mDevice->open(mDevice, eventCallback, dataCallback);
    return ret == 0 ? NfcStatus::OK : NfcStatus::FAILED;
}

::android::hardware::Return<uint32_t> Nfc::write(const hidl_vec<uint8_t>& data)  {
    if (mDevice == nullptr) {
        return -1;
    }
    return mDevice->write(mDevice, data.size(), &data[0]);
}

::android::hardware::Return<NfcStatus> Nfc::coreInitialized(const hidl_vec<uint8_t>& data)  {
    hidl_vec<uint8_t> copy = data;

    if (mDevice == nullptr) {
        return NfcStatus::FAILED;
    }
    int ret = mDevice->core_initialized(mDevice, &copy[0]);
    return ret == 0 ? NfcStatus::OK : NfcStatus::FAILED;
}

::android::hardware::Return<NfcStatus> Nfc::prediscover()  {
    if (mDevice == nullptr) {
        return NfcStatus::FAILED;
    }
    return mDevice->pre_discover(mDevice) ? NfcStatus::FAILED : NfcStatus::OK;
}

::android::hardware::Return<NfcStatus> Nfc::close()  {
    if (mDevice == nullptr || mCallback == nullptr) {
        return NfcStatus::FAILED;
    }
    mCallback->unlinkToDeath(mDeathRecipient);
    return mDevice->close(mDevice) ? NfcStatus::FAILED : NfcStatus::OK;
}

::android::hardware::Return<NfcStatus> Nfc::controlGranted()  {
    if (mDevice == nullptr) {
        return NfcStatus::FAILED;
    }
    return mDevice->control_granted(mDevice) ? NfcStatus::FAILED : NfcStatus::OK;
}

::android::hardware::Return<NfcStatus> Nfc::powerCycle()  {
    if (mDevice == nullptr) {
        return NfcStatus::FAILED;
    }
    return mDevice->power_cycle(mDevice) ? NfcStatus::FAILED : NfcStatus::OK;
}


INfc* HIDL_FETCH_INfc(const char * /*name*/) {
    nfc_nci_device_t* nfc_device;
    int ret = 0;
    const hw_module_t* hw_module = nullptr;

    ret = hw_get_module (NFC_NCI_HARDWARE_MODULE_ID, &hw_module);
    if (ret == 0) {
        ret = nfc_nci_open (hw_module, &nfc_device);
        if (ret != 0) {
            ALOGE ("nfc_nci_open failed: %d", ret);
        }
    }
    else
        ALOGE ("hw_get_module %s failed: %d", NFC_NCI_HARDWARE_MODULE_ID, ret);

    if (ret == 0) {
        return new Nfc(nfc_device);
    } else {
        ALOGE("Passthrough failed to load legacy HAL.");
        return nullptr;
    }
}

} // namespace implementation
}  // namespace V1_0
}  // namespace nfc
}  // namespace hardware
}  // namespace android
