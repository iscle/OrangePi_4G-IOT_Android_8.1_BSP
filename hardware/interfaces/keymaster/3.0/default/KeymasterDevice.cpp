/*
 **
 ** Copyright 2016, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

#define LOG_TAG "android.hardware.keymaster@3.0-impl"

#include "KeymasterDevice.h"

#include <cutils/log.h>

#include <hardware/keymaster_defs.h>
#include <keymaster/keymaster_configuration.h>
#include <keymaster/soft_keymaster_device.h>

namespace android {
namespace hardware {
namespace keymaster {
namespace V3_0 {
namespace implementation {

using ::keymaster::SoftKeymasterDevice;

class SoftwareOnlyHidlKeymasterEnforcement : public ::keymaster::KeymasterEnforcement {
  public:
    SoftwareOnlyHidlKeymasterEnforcement() : KeymasterEnforcement(64, 64) {}

    uint32_t get_current_time() const override {
        struct timespec tp;
        int err = clock_gettime(CLOCK_MONOTONIC, &tp);
        if (err || tp.tv_sec < 0) return 0;
        return static_cast<uint32_t>(tp.tv_sec);
    }

    bool activation_date_valid(uint64_t) const override { return true; }
    bool expiration_date_passed(uint64_t) const override { return false; }
    bool auth_token_timed_out(const hw_auth_token_t&, uint32_t) const override { return false; }
    bool ValidateTokenSignature(const hw_auth_token_t&) const override { return true; }
};

class SoftwareOnlyHidlKeymasterContext : public ::keymaster::SoftKeymasterContext {
  public:
    SoftwareOnlyHidlKeymasterContext() : enforcement_(new SoftwareOnlyHidlKeymasterEnforcement) {}

    ::keymaster::KeymasterEnforcement* enforcement_policy() override { return enforcement_.get(); }

  private:
    std::unique_ptr<::keymaster::KeymasterEnforcement> enforcement_;
};

static int keymaster0_device_initialize(const hw_module_t* mod, keymaster2_device_t** dev) {
    assert(mod->module_api_version < KEYMASTER_MODULE_API_VERSION_1_0);
    ALOGI("Found keymaster0 module %s, version %x", mod->name, mod->module_api_version);

    std::unique_ptr<SoftKeymasterDevice> soft_keymaster(new SoftKeymasterDevice);
    keymaster0_device_t* km0_device = NULL;
    keymaster_error_t error = KM_ERROR_OK;

    int rc = keymaster0_open(mod, &km0_device);
    if (rc) {
        ALOGE("Error opening keystore keymaster0 device.");
        goto err;
    }

    if (km0_device->flags & KEYMASTER_SOFTWARE_ONLY) {
        ALOGI("Keymaster0 module is software-only.  Using SoftKeymasterDevice instead.");
        km0_device->common.close(&km0_device->common);
        km0_device = NULL;
        // SoftKeymasterDevice will be deleted by keymaster_device_release()
        *dev = soft_keymaster.release()->keymaster2_device();
        return 0;
    }

    ALOGD("Wrapping keymaster0 module %s with SoftKeymasterDevice", mod->name);
    error = soft_keymaster->SetHardwareDevice(km0_device);
    km0_device = NULL;  // SoftKeymasterDevice has taken ownership.
    if (error != KM_ERROR_OK) {
        ALOGE("Got error %d from SetHardwareDevice", error);
        rc = error;
        goto err;
    }

    // SoftKeymasterDevice will be deleted by  keymaster_device_release()
    *dev = soft_keymaster.release()->keymaster2_device();
    return 0;

err:
    if (km0_device) km0_device->common.close(&km0_device->common);
    *dev = NULL;
    return rc;
}

static int keymaster1_device_initialize(const hw_module_t* mod, keymaster2_device_t** dev,
                                        bool* supports_all_digests) {
    assert(mod->module_api_version >= KEYMASTER_MODULE_API_VERSION_1_0);
    ALOGI("Found keymaster1 module %s, version %x", mod->name, mod->module_api_version);

    std::unique_ptr<SoftKeymasterDevice> soft_keymaster(new SoftKeymasterDevice);
    keymaster1_device_t* km1_device = nullptr;
    keymaster_error_t error = KM_ERROR_OK;

    int rc = keymaster1_open(mod, &km1_device);
    if (rc) {
        ALOGE("Error %d opening keystore keymaster1 device", rc);
        goto err;
    }

    ALOGD("Wrapping keymaster1 module %s with SofKeymasterDevice", mod->name);
    error = soft_keymaster->SetHardwareDevice(km1_device);
    km1_device = nullptr;  // SoftKeymasterDevice has taken ownership.
    if (error != KM_ERROR_OK) {
        ALOGE("Got error %d from SetHardwareDevice", error);
        rc = error;
        goto err;
    }

    // SoftKeymasterDevice will be deleted by keymaster_device_release()
    *supports_all_digests = soft_keymaster->supports_all_digests();
    *dev = soft_keymaster.release()->keymaster2_device();
    return 0;

err:
    if (km1_device) km1_device->common.close(&km1_device->common);
    *dev = NULL;
    return rc;
}

static int keymaster2_device_initialize(const hw_module_t* mod, keymaster2_device_t** dev) {
    assert(mod->module_api_version >= KEYMASTER_MODULE_API_VERSION_2_0);
    ALOGI("Found keymaster2 module %s, version %x", mod->name, mod->module_api_version);

    keymaster2_device_t* km2_device = nullptr;

    int rc = keymaster2_open(mod, &km2_device);
    if (rc) {
        ALOGE("Error %d opening keystore keymaster2 device", rc);
        goto err;
    }

    *dev = km2_device;
    return 0;

err:
    if (km2_device) km2_device->common.close(&km2_device->common);
    *dev = nullptr;
    return rc;
}

static int keymaster_device_initialize(keymaster2_device_t** dev, uint32_t* version,
                                       bool* supports_ec, bool* supports_all_digests) {
    const hw_module_t* mod;

    *supports_ec = true;

    int rc = hw_get_module_by_class(KEYSTORE_HARDWARE_MODULE_ID, NULL, &mod);
    if (rc) {
        ALOGI("Could not find any keystore module, using software-only implementation.");
        // SoftKeymasterDevice will be deleted by keymaster_device_release()
        *dev = (new SoftKeymasterDevice(new SoftwareOnlyHidlKeymasterContext))->keymaster2_device();
        *version = -1;
        return 0;
    }

    if (mod->module_api_version < KEYMASTER_MODULE_API_VERSION_1_0) {
        *version = 0;
        *supports_all_digests = false;
        int rc = keymaster0_device_initialize(mod, dev);
        if (rc == 0 && ((*dev)->flags & KEYMASTER_SUPPORTS_EC) == 0) {
            *supports_ec = false;
        }
        return rc;
    } else if (mod->module_api_version == KEYMASTER_MODULE_API_VERSION_1_0) {
        *version = 1;
        return keymaster1_device_initialize(mod, dev, supports_all_digests);
    } else {
        *version = 2;
        *supports_all_digests = true;
        return keymaster2_device_initialize(mod, dev);
    }
}

KeymasterDevice::~KeymasterDevice() {
    if (keymaster_device_) keymaster_device_->common.close(&keymaster_device_->common);
}

static inline keymaster_tag_type_t typeFromTag(const keymaster_tag_t tag) {
    return keymaster_tag_get_type(tag);
}

/**
 * legacy_enum_conversion converts enums from hidl to keymaster and back. Currently, this is just a
 * cast to make the compiler happy. One of two thigs should happen though:
 * TODO The keymaster enums should become aliases for the hidl generated enums so that we have a
 *      single point of truth. Then this cast function can go away.
 */
inline static keymaster_tag_t legacy_enum_conversion(const Tag value) {
    return keymaster_tag_t(value);
}
inline static Tag legacy_enum_conversion(const keymaster_tag_t value) {
    return Tag(value);
}
inline static keymaster_purpose_t legacy_enum_conversion(const KeyPurpose value) {
    return keymaster_purpose_t(value);
}
inline static keymaster_key_format_t legacy_enum_conversion(const KeyFormat value) {
    return keymaster_key_format_t(value);
}
inline static ErrorCode legacy_enum_conversion(const keymaster_error_t value) {
    return ErrorCode(value);
}

class KmParamSet : public keymaster_key_param_set_t {
  public:
    KmParamSet(const hidl_vec<KeyParameter>& keyParams) {
        params = new keymaster_key_param_t[keyParams.size()];
        length = keyParams.size();
        for (size_t i = 0; i < keyParams.size(); ++i) {
            auto tag = legacy_enum_conversion(keyParams[i].tag);
            switch (typeFromTag(tag)) {
            case KM_ENUM:
            case KM_ENUM_REP:
                params[i] = keymaster_param_enum(tag, keyParams[i].f.integer);
                break;
            case KM_UINT:
            case KM_UINT_REP:
                params[i] = keymaster_param_int(tag, keyParams[i].f.integer);
                break;
            case KM_ULONG:
            case KM_ULONG_REP:
                params[i] = keymaster_param_long(tag, keyParams[i].f.longInteger);
                break;
            case KM_DATE:
                params[i] = keymaster_param_date(tag, keyParams[i].f.dateTime);
                break;
            case KM_BOOL:
                if (keyParams[i].f.boolValue)
                    params[i] = keymaster_param_bool(tag);
                else
                    params[i].tag = KM_TAG_INVALID;
                break;
            case KM_BIGNUM:
            case KM_BYTES:
                params[i] =
                    keymaster_param_blob(tag, &keyParams[i].blob[0], keyParams[i].blob.size());
                break;
            case KM_INVALID:
            default:
                params[i].tag = KM_TAG_INVALID;
                /* just skip */
                break;
            }
        }
    }
    KmParamSet(KmParamSet&& other) : keymaster_key_param_set_t{other.params, other.length} {
        other.length = 0;
        other.params = nullptr;
    }
    KmParamSet(const KmParamSet&) = delete;
    ~KmParamSet() { delete[] params; }
};

inline static KmParamSet hidlParams2KmParamSet(const hidl_vec<KeyParameter>& params) {
    return KmParamSet(params);
}

inline static keymaster_blob_t hidlVec2KmBlob(const hidl_vec<uint8_t>& blob) {
    /* hidl unmarshals funny pointers if the the blob is empty */
    if (blob.size()) return {&blob[0], blob.size()};
    return {nullptr, 0};
}

inline static keymaster_key_blob_t hidlVec2KmKeyBlob(const hidl_vec<uint8_t>& blob) {
    /* hidl unmarshals funny pointers if the the blob is empty */
    if (blob.size()) return {&blob[0], blob.size()};
    return {nullptr, 0};
}

inline static hidl_vec<uint8_t> kmBlob2hidlVec(const keymaster_key_blob_t& blob) {
    hidl_vec<uint8_t> result;
    result.setToExternal(const_cast<unsigned char*>(blob.key_material), blob.key_material_size);
    return result;
}
inline static hidl_vec<uint8_t> kmBlob2hidlVec(const keymaster_blob_t& blob) {
    hidl_vec<uint8_t> result;
    result.setToExternal(const_cast<unsigned char*>(blob.data), blob.data_length);
    return result;
}

inline static hidl_vec<hidl_vec<uint8_t>>
kmCertChain2Hidl(const keymaster_cert_chain_t* cert_chain) {
    hidl_vec<hidl_vec<uint8_t>> result;
    if (!cert_chain || cert_chain->entry_count == 0 || !cert_chain->entries) return result;

    result.resize(cert_chain->entry_count);
    for (size_t i = 0; i < cert_chain->entry_count; ++i) {
        auto& entry = cert_chain->entries[i];
        result[i] = kmBlob2hidlVec(entry);
    }

    return result;
}

static inline hidl_vec<KeyParameter> kmParamSet2Hidl(const keymaster_key_param_set_t& set) {
    hidl_vec<KeyParameter> result;
    if (set.length == 0 || set.params == nullptr) return result;

    result.resize(set.length);
    keymaster_key_param_t* params = set.params;
    for (size_t i = 0; i < set.length; ++i) {
        auto tag = params[i].tag;
        result[i].tag = legacy_enum_conversion(tag);
        switch (typeFromTag(tag)) {
        case KM_ENUM:
        case KM_ENUM_REP:
            result[i].f.integer = params[i].enumerated;
            break;
        case KM_UINT:
        case KM_UINT_REP:
            result[i].f.integer = params[i].integer;
            break;
        case KM_ULONG:
        case KM_ULONG_REP:
            result[i].f.longInteger = params[i].long_integer;
            break;
        case KM_DATE:
            result[i].f.dateTime = params[i].date_time;
            break;
        case KM_BOOL:
            result[i].f.boolValue = params[i].boolean;
            break;
        case KM_BIGNUM:
        case KM_BYTES:
            result[i].blob.setToExternal(const_cast<unsigned char*>(params[i].blob.data),
                                         params[i].blob.data_length);
            break;
        case KM_INVALID:
        default:
            params[i].tag = KM_TAG_INVALID;
            /* just skip */
            break;
        }
    }
    return result;
}

// Methods from ::android::hardware::keymaster::V3_0::IKeymasterDevice follow.
Return<void> KeymasterDevice::getHardwareFeatures(getHardwareFeatures_cb _hidl_cb) {
    bool is_secure = !(keymaster_device_->flags & KEYMASTER_SOFTWARE_ONLY);
    bool supports_symmetric_cryptography = false;
    bool supports_attestation = false;

    switch (hardware_version_) {
    case 2:
        supports_attestation = true;
    /* Falls through */
    case 1:
        supports_symmetric_cryptography = true;
        break;
    };

    _hidl_cb(is_secure, hardware_supports_ec_, supports_symmetric_cryptography,
             supports_attestation, hardware_supports_all_digests_,
             keymaster_device_->common.module->name, keymaster_device_->common.module->author);
    return Void();
}

Return<ErrorCode> KeymasterDevice::addRngEntropy(const hidl_vec<uint8_t>& data) {
    if (!data.size()) return ErrorCode::OK;
    return legacy_enum_conversion(
        keymaster_device_->add_rng_entropy(keymaster_device_, &data[0], data.size()));
}

Return<void> KeymasterDevice::generateKey(const hidl_vec<KeyParameter>& keyParams,
                                          generateKey_cb _hidl_cb) {
    // result variables for the wire
    KeyCharacteristics resultCharacteristics;
    hidl_vec<uint8_t> resultKeyBlob;

    // result variables the backend understands
    keymaster_key_blob_t key_blob{nullptr, 0};
    keymaster_key_characteristics_t key_characteristics{{nullptr, 0}, {nullptr, 0}};

    // convert the parameter set to something our backend understands
    auto kmParams = hidlParams2KmParamSet(keyParams);

    auto rc = keymaster_device_->generate_key(keymaster_device_, &kmParams, &key_blob,
                                              &key_characteristics);

    if (rc == KM_ERROR_OK) {
        // on success convert the result to wire format
        resultKeyBlob = kmBlob2hidlVec(key_blob);
        resultCharacteristics.softwareEnforced = kmParamSet2Hidl(key_characteristics.sw_enforced);
        resultCharacteristics.teeEnforced = kmParamSet2Hidl(key_characteristics.hw_enforced);
    }

    // send results off to the client
    _hidl_cb(legacy_enum_conversion(rc), resultKeyBlob, resultCharacteristics);

    // free buffers that we are responsible for
    if (key_blob.key_material) free(const_cast<uint8_t*>(key_blob.key_material));
    keymaster_free_characteristics(&key_characteristics);

    return Void();
}

Return<void> KeymasterDevice::getKeyCharacteristics(const hidl_vec<uint8_t>& keyBlob,
                                                    const hidl_vec<uint8_t>& clientId,
                                                    const hidl_vec<uint8_t>& appData,
                                                    getKeyCharacteristics_cb _hidl_cb) {
    // result variables for the wire
    KeyCharacteristics resultCharacteristics;

    // result variables the backend understands
    keymaster_key_characteristics_t key_characteristics{{nullptr, 0}, {nullptr, 0}};

    auto kmKeyBlob = hidlVec2KmKeyBlob(keyBlob);
    auto kmClientId = hidlVec2KmBlob(clientId);
    auto kmAppData = hidlVec2KmBlob(appData);

    auto rc = keymaster_device_->get_key_characteristics(
        keymaster_device_, keyBlob.size() ? &kmKeyBlob : nullptr,
        clientId.size() ? &kmClientId : nullptr, appData.size() ? &kmAppData : nullptr,
        &key_characteristics);

    if (rc == KM_ERROR_OK) {
        resultCharacteristics.softwareEnforced = kmParamSet2Hidl(key_characteristics.sw_enforced);
        resultCharacteristics.teeEnforced = kmParamSet2Hidl(key_characteristics.hw_enforced);
    }

    _hidl_cb(legacy_enum_conversion(rc), resultCharacteristics);

    keymaster_free_characteristics(&key_characteristics);

    return Void();
}

Return<void> KeymasterDevice::importKey(const hidl_vec<KeyParameter>& params, KeyFormat keyFormat,
                                        const hidl_vec<uint8_t>& keyData, importKey_cb _hidl_cb) {
    // result variables for the wire
    KeyCharacteristics resultCharacteristics;
    hidl_vec<uint8_t> resultKeyBlob;

    // result variables the backend understands
    keymaster_key_blob_t key_blob{nullptr, 0};
    keymaster_key_characteristics_t key_characteristics{{nullptr, 0}, {nullptr, 0}};

    auto kmParams = hidlParams2KmParamSet(params);
    auto kmKeyData = hidlVec2KmBlob(keyData);

    auto rc = keymaster_device_->import_key(keymaster_device_, &kmParams,
                                            legacy_enum_conversion(keyFormat), &kmKeyData,
                                            &key_blob, &key_characteristics);

    if (rc == KM_ERROR_OK) {
        // on success convert the result to wire format
        // (Can we assume that key_blob is {nullptr, 0} or a valid buffer description?)
        resultKeyBlob = kmBlob2hidlVec(key_blob);
        resultCharacteristics.softwareEnforced = kmParamSet2Hidl(key_characteristics.sw_enforced);
        resultCharacteristics.teeEnforced = kmParamSet2Hidl(key_characteristics.hw_enforced);
    }

    _hidl_cb(legacy_enum_conversion(rc), resultKeyBlob, resultCharacteristics);

    // free buffers that we are responsible for
    if (key_blob.key_material) free(const_cast<uint8_t*>(key_blob.key_material));
    keymaster_free_characteristics(&key_characteristics);

    return Void();
}

Return<void> KeymasterDevice::exportKey(KeyFormat exportFormat, const hidl_vec<uint8_t>& keyBlob,
                                        const hidl_vec<uint8_t>& clientId,
                                        const hidl_vec<uint8_t>& appData, exportKey_cb _hidl_cb) {

    // result variables for the wire
    hidl_vec<uint8_t> resultKeyBlob;

    // result variables the backend understands
    keymaster_blob_t out_blob{nullptr, 0};

    auto kmKeyBlob = hidlVec2KmKeyBlob(keyBlob);
    auto kmClientId = hidlVec2KmBlob(clientId);
    auto kmAppData = hidlVec2KmBlob(appData);

    auto rc = keymaster_device_->export_key(keymaster_device_, legacy_enum_conversion(exportFormat),
                                            keyBlob.size() ? &kmKeyBlob : nullptr,
                                            clientId.size() ? &kmClientId : nullptr,
                                            appData.size() ? &kmAppData : nullptr, &out_blob);

    if (rc == KM_ERROR_OK) {
        // on success convert the result to wire format
        // (Can we assume that key_blob is {nullptr, 0} or a valid buffer description?)
        resultKeyBlob = kmBlob2hidlVec(out_blob);
    }

    _hidl_cb(legacy_enum_conversion(rc), resultKeyBlob);

    // free buffers that we are responsible for
    if (out_blob.data) free(const_cast<uint8_t*>(out_blob.data));

    return Void();
}

Return<void> KeymasterDevice::attestKey(const hidl_vec<uint8_t>& keyToAttest,
                                        const hidl_vec<KeyParameter>& attestParams,
                                        attestKey_cb _hidl_cb) {

    hidl_vec<hidl_vec<uint8_t>> resultCertChain;

    bool foundAttestationApplicationId = false;
    for (size_t i = 0; i < attestParams.size(); ++i) {
        switch (attestParams[i].tag) {
        case Tag::ATTESTATION_ID_BRAND:
        case Tag::ATTESTATION_ID_DEVICE:
        case Tag::ATTESTATION_ID_PRODUCT:
        case Tag::ATTESTATION_ID_SERIAL:
        case Tag::ATTESTATION_ID_IMEI:
        case Tag::ATTESTATION_ID_MEID:
        case Tag::ATTESTATION_ID_MANUFACTURER:
        case Tag::ATTESTATION_ID_MODEL:
            // Device id attestation may only be supported if the device is able to permanently
            // destroy its knowledge of the ids. This device is unable to do this, so it must
            // never perform any device id attestation.
            _hidl_cb(ErrorCode::CANNOT_ATTEST_IDS, resultCertChain);
            return Void();

        case Tag::ATTESTATION_APPLICATION_ID:
            foundAttestationApplicationId = true;
            break;

        default:
            break;
        }
    }

    // KM3 devices reject missing attest application IDs. KM2 devices do not.
    if (!foundAttestationApplicationId) {
        _hidl_cb(ErrorCode::ATTESTATION_APPLICATION_ID_MISSING,
                 resultCertChain);
        return Void();
    }

    keymaster_cert_chain_t cert_chain{nullptr, 0};

    auto kmKeyToAttest = hidlVec2KmKeyBlob(keyToAttest);
    auto kmAttestParams = hidlParams2KmParamSet(attestParams);

    auto rc = keymaster_device_->attest_key(keymaster_device_, &kmKeyToAttest, &kmAttestParams,
                                            &cert_chain);

    if (rc == KM_ERROR_OK) {
        resultCertChain = kmCertChain2Hidl(&cert_chain);
    }

    _hidl_cb(legacy_enum_conversion(rc), resultCertChain);

    keymaster_free_cert_chain(&cert_chain);

    return Void();
}

Return<void> KeymasterDevice::upgradeKey(const hidl_vec<uint8_t>& keyBlobToUpgrade,
                                         const hidl_vec<KeyParameter>& upgradeParams,
                                         upgradeKey_cb _hidl_cb) {

    // result variables for the wire
    hidl_vec<uint8_t> resultKeyBlob;

    // result variables the backend understands
    keymaster_key_blob_t key_blob{nullptr, 0};

    auto kmKeyBlobToUpgrade = hidlVec2KmKeyBlob(keyBlobToUpgrade);
    auto kmUpgradeParams = hidlParams2KmParamSet(upgradeParams);

    auto rc = keymaster_device_->upgrade_key(keymaster_device_, &kmKeyBlobToUpgrade,
                                             &kmUpgradeParams, &key_blob);

    if (rc == KM_ERROR_OK) {
        // on success convert the result to wire format
        resultKeyBlob = kmBlob2hidlVec(key_blob);
    }

    _hidl_cb(legacy_enum_conversion(rc), resultKeyBlob);

    if (key_blob.key_material) free(const_cast<uint8_t*>(key_blob.key_material));

    return Void();
}

Return<ErrorCode> KeymasterDevice::deleteKey(const hidl_vec<uint8_t>& keyBlob) {
    if (keymaster_device_->delete_key == nullptr) {
        return ErrorCode::UNIMPLEMENTED;
    }
    auto kmKeyBlob = hidlVec2KmKeyBlob(keyBlob);
    auto rc = legacy_enum_conversion(
        keymaster_device_->delete_key(keymaster_device_, &kmKeyBlob));
    // Keymaster 3.0 requires deleteKey to return ErrorCode::OK if the key
    // blob is unusable after the call. This is equally true if the key blob was
    // unusable before.
    if (rc == ErrorCode::INVALID_KEY_BLOB) return ErrorCode::OK;
    return rc;
}

Return<ErrorCode> KeymasterDevice::deleteAllKeys() {
    if (keymaster_device_->delete_all_keys == nullptr) {
        return ErrorCode::UNIMPLEMENTED;
    }
    return legacy_enum_conversion(keymaster_device_->delete_all_keys(keymaster_device_));
}

Return<ErrorCode> KeymasterDevice::destroyAttestationIds() {
    return ErrorCode::UNIMPLEMENTED;
}

Return<void> KeymasterDevice::begin(KeyPurpose purpose, const hidl_vec<uint8_t>& key,
                                    const hidl_vec<KeyParameter>& inParams, begin_cb _hidl_cb) {

    // result variables for the wire
    hidl_vec<KeyParameter> resultParams;
    uint64_t resultOpHandle = 0;

    // result variables the backend understands
    keymaster_key_param_set_t out_params{nullptr, 0};
    keymaster_operation_handle_t& operation_handle = resultOpHandle;

    auto kmKey = hidlVec2KmKeyBlob(key);
    auto kmInParams = hidlParams2KmParamSet(inParams);

    auto rc = keymaster_device_->begin(keymaster_device_, legacy_enum_conversion(purpose), &kmKey,
                                       &kmInParams, &out_params, &operation_handle);

    if (rc == KM_ERROR_OK) resultParams = kmParamSet2Hidl(out_params);

    _hidl_cb(legacy_enum_conversion(rc), resultParams, resultOpHandle);

    keymaster_free_param_set(&out_params);

    return Void();
}

Return<void> KeymasterDevice::update(uint64_t operationHandle,
                                     const hidl_vec<KeyParameter>& inParams,
                                     const hidl_vec<uint8_t>& input, update_cb _hidl_cb) {
    // result variables for the wire
    uint32_t resultConsumed = 0;
    hidl_vec<KeyParameter> resultParams;
    hidl_vec<uint8_t> resultBlob;

    // result variables the backend understands
    size_t consumed = 0;
    keymaster_key_param_set_t out_params{nullptr, 0};
    keymaster_blob_t out_blob{nullptr, 0};

    auto kmInParams = hidlParams2KmParamSet(inParams);
    auto kmInput = hidlVec2KmBlob(input);

    auto rc = keymaster_device_->update(keymaster_device_, operationHandle, &kmInParams, &kmInput,
                                        &consumed, &out_params, &out_blob);

    if (rc == KM_ERROR_OK) {
        resultConsumed = consumed;
        resultParams = kmParamSet2Hidl(out_params);
        resultBlob = kmBlob2hidlVec(out_blob);
    }

    _hidl_cb(legacy_enum_conversion(rc), resultConsumed, resultParams, resultBlob);

    keymaster_free_param_set(&out_params);
    if (out_blob.data) free(const_cast<uint8_t*>(out_blob.data));

    return Void();
}

Return<void> KeymasterDevice::finish(uint64_t operationHandle,
                                     const hidl_vec<KeyParameter>& inParams,
                                     const hidl_vec<uint8_t>& input,
                                     const hidl_vec<uint8_t>& signature, finish_cb _hidl_cb) {
    // result variables for the wire
    hidl_vec<KeyParameter> resultParams;
    hidl_vec<uint8_t> resultBlob;

    // result variables the backend understands
    keymaster_key_param_set_t out_params{nullptr, 0};
    keymaster_blob_t out_blob{nullptr, 0};

    auto kmInParams = hidlParams2KmParamSet(inParams);
    auto kmInput = hidlVec2KmBlob(input);
    auto kmSignature = hidlVec2KmBlob(signature);

    auto rc = keymaster_device_->finish(keymaster_device_, operationHandle, &kmInParams, &kmInput,
                                        &kmSignature, &out_params, &out_blob);

    if (rc == KM_ERROR_OK) {
        resultParams = kmParamSet2Hidl(out_params);
        resultBlob = kmBlob2hidlVec(out_blob);
    }

    _hidl_cb(legacy_enum_conversion(rc), resultParams, resultBlob);

    keymaster_free_param_set(&out_params);
    if (out_blob.data) free(const_cast<uint8_t*>(out_blob.data));

    return Void();
}

Return<ErrorCode> KeymasterDevice::abort(uint64_t operationHandle) {
    return legacy_enum_conversion(keymaster_device_->abort(keymaster_device_, operationHandle));
}

IKeymasterDevice* HIDL_FETCH_IKeymasterDevice(const char* name) {
    keymaster2_device_t* dev = nullptr;

    ALOGI("Fetching keymaster device name %s", name);

    uint32_t version = -1;
    bool supports_ec = false;
    bool supports_all_digests = false;

    if (name && strcmp(name, "softwareonly") == 0) {
        dev = (new SoftKeymasterDevice(new SoftwareOnlyHidlKeymasterContext))->keymaster2_device();
    } else if (name && strcmp(name, "default") == 0) {
        auto rc = keymaster_device_initialize(&dev, &version, &supports_ec, &supports_all_digests);
        if (rc) return nullptr;
    }

    auto kmrc = ::keymaster::ConfigureDevice(dev);
    if (kmrc != KM_ERROR_OK) {
        dev->common.close(&dev->common);
        return nullptr;
    }

    return new KeymasterDevice(dev, version, supports_ec, supports_all_digests);
}

}  // namespace implementation
}  // namespace V3_0
}  // namespace keymaster
}  // namespace hardware
}  // namespace android
