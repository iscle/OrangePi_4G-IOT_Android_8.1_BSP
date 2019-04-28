/*
 * Copyright 2016 The Android Open Source Project
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

#include "attestation_record.h"

#include <assert.h>

#include <openssl/asn1t.h>
#include <openssl/bn.h>
#include <openssl/evp.h>
#include <openssl/x509.h>

#include "openssl_utils.h"

namespace android {
namespace hardware {
namespace keymaster {
namespace V3_0 {

struct stack_st_ASN1_TYPE_Delete {
    void operator()(stack_st_ASN1_TYPE* p) { sk_ASN1_TYPE_free(p); }
};

struct ASN1_STRING_Delete {
    void operator()(ASN1_STRING* p) { ASN1_STRING_free(p); }
};

struct ASN1_TYPE_Delete {
    void operator()(ASN1_TYPE* p) { ASN1_TYPE_free(p); }
};

#define ASN1_INTEGER_SET STACK_OF(ASN1_INTEGER)

typedef struct km_root_of_trust {
    ASN1_OCTET_STRING* verified_boot_key;
    ASN1_BOOLEAN* device_locked;
    ASN1_ENUMERATED* verified_boot_state;
} KM_ROOT_OF_TRUST;

ASN1_SEQUENCE(KM_ROOT_OF_TRUST) = {
    ASN1_SIMPLE(KM_ROOT_OF_TRUST, verified_boot_key, ASN1_OCTET_STRING),
    ASN1_SIMPLE(KM_ROOT_OF_TRUST, device_locked, ASN1_BOOLEAN),
    ASN1_SIMPLE(KM_ROOT_OF_TRUST, verified_boot_state, ASN1_ENUMERATED),
} ASN1_SEQUENCE_END(KM_ROOT_OF_TRUST);
IMPLEMENT_ASN1_FUNCTIONS(KM_ROOT_OF_TRUST);

typedef struct km_auth_list {
    ASN1_INTEGER_SET* purpose;
    ASN1_INTEGER* algorithm;
    ASN1_INTEGER* key_size;
    ASN1_INTEGER_SET* digest;
    ASN1_INTEGER_SET* padding;
    ASN1_INTEGER_SET* kdf;
    ASN1_INTEGER* ec_curve;
    ASN1_INTEGER* rsa_public_exponent;
    ASN1_INTEGER* active_date_time;
    ASN1_INTEGER* origination_expire_date_time;
    ASN1_INTEGER* usage_expire_date_time;
    ASN1_NULL* no_auth_required;
    ASN1_INTEGER* user_auth_type;
    ASN1_INTEGER* auth_timeout;
    ASN1_NULL* allow_while_on_body;
    ASN1_NULL* all_applications;
    ASN1_OCTET_STRING* application_id;
    ASN1_INTEGER* creation_date_time;
    ASN1_INTEGER* origin;
    ASN1_NULL* rollback_resistant;
    KM_ROOT_OF_TRUST* root_of_trust;
    ASN1_INTEGER* os_version;
    ASN1_INTEGER* os_patchlevel;
    ASN1_OCTET_STRING* attestation_application_id;
} KM_AUTH_LIST;

ASN1_SEQUENCE(KM_AUTH_LIST) = {
    ASN1_EXP_SET_OF_OPT(KM_AUTH_LIST, purpose, ASN1_INTEGER, TAG_PURPOSE.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, algorithm, ASN1_INTEGER, TAG_ALGORITHM.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, key_size, ASN1_INTEGER, TAG_KEY_SIZE.maskedTag()),
    ASN1_EXP_SET_OF_OPT(KM_AUTH_LIST, digest, ASN1_INTEGER, TAG_DIGEST.maskedTag()),
    ASN1_EXP_SET_OF_OPT(KM_AUTH_LIST, padding, ASN1_INTEGER, TAG_PADDING.maskedTag()),
    ASN1_EXP_SET_OF_OPT(KM_AUTH_LIST, kdf, ASN1_INTEGER, TAG_KDF.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, ec_curve, ASN1_INTEGER, TAG_EC_CURVE.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, rsa_public_exponent, ASN1_INTEGER,
                 TAG_RSA_PUBLIC_EXPONENT.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, active_date_time, ASN1_INTEGER, TAG_ACTIVE_DATETIME.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, origination_expire_date_time, ASN1_INTEGER,
                 TAG_ORIGINATION_EXPIRE_DATETIME.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, usage_expire_date_time, ASN1_INTEGER,
                 TAG_USAGE_EXPIRE_DATETIME.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, no_auth_required, ASN1_NULL, TAG_NO_AUTH_REQUIRED.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, user_auth_type, ASN1_INTEGER, TAG_USER_AUTH_TYPE.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, auth_timeout, ASN1_INTEGER, TAG_AUTH_TIMEOUT.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, allow_while_on_body, ASN1_NULL, TAG_ALLOW_WHILE_ON_BODY.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, all_applications, ASN1_NULL, TAG_ALL_APPLICATIONS.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, application_id, ASN1_OCTET_STRING, TAG_APPLICATION_ID.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, creation_date_time, ASN1_INTEGER, TAG_CREATION_DATETIME.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, origin, ASN1_INTEGER, TAG_ORIGIN.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, rollback_resistant, ASN1_NULL, TAG_ROLLBACK_RESISTANT.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, root_of_trust, KM_ROOT_OF_TRUST, TAG_ROOT_OF_TRUST.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, os_version, ASN1_INTEGER, TAG_OS_VERSION.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, os_patchlevel, ASN1_INTEGER, TAG_OS_PATCHLEVEL.maskedTag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_application_id, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_APPLICATION_ID.maskedTag()),
} ASN1_SEQUENCE_END(KM_AUTH_LIST);
IMPLEMENT_ASN1_FUNCTIONS(KM_AUTH_LIST);

typedef struct km_key_description {
    ASN1_INTEGER* attestation_version;
    ASN1_ENUMERATED* attestation_security_level;
    ASN1_INTEGER* keymaster_version;
    ASN1_ENUMERATED* keymaster_security_level;
    ASN1_OCTET_STRING* attestation_challenge;
    KM_AUTH_LIST* software_enforced;
    KM_AUTH_LIST* tee_enforced;
    ASN1_INTEGER* unique_id;
} KM_KEY_DESCRIPTION;

ASN1_SEQUENCE(KM_KEY_DESCRIPTION) = {
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, attestation_version, ASN1_INTEGER),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, attestation_security_level, ASN1_ENUMERATED),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, keymaster_version, ASN1_INTEGER),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, keymaster_security_level, ASN1_ENUMERATED),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, attestation_challenge, ASN1_OCTET_STRING),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, unique_id, ASN1_OCTET_STRING),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, software_enforced, KM_AUTH_LIST),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, tee_enforced, KM_AUTH_LIST),
} ASN1_SEQUENCE_END(KM_KEY_DESCRIPTION);
IMPLEMENT_ASN1_FUNCTIONS(KM_KEY_DESCRIPTION);

template <Tag tag>
void copyAuthTag(const stack_st_ASN1_INTEGER* stack, TypedTag<TagType::ENUM_REP, tag> ttag,
                 AuthorizationSet* auth_list) {
    typedef typename TypedTag2ValueType<decltype(ttag)>::type ValueT;
    for (size_t i = 0; i < sk_ASN1_INTEGER_num(stack); ++i) {
        auth_list->push_back(
            ttag, static_cast<ValueT>(ASN1_INTEGER_get(sk_ASN1_INTEGER_value(stack, i))));
    }
}

template <Tag tag>
void copyAuthTag(const ASN1_INTEGER* asn1_int, TypedTag<TagType::ENUM, tag> ttag,
                 AuthorizationSet* auth_list) {
    typedef typename TypedTag2ValueType<decltype(ttag)>::type ValueT;
    if (!asn1_int) return;
    auth_list->push_back(ttag, static_cast<ValueT>(ASN1_INTEGER_get(asn1_int)));
}

template <Tag tag>
void copyAuthTag(const ASN1_INTEGER* asn1_int, TypedTag<TagType::UINT, tag> ttag,
                 AuthorizationSet* auth_list) {
    if (!asn1_int) return;
    auth_list->push_back(ttag, ASN1_INTEGER_get(asn1_int));
}

BIGNUM* construct_uint_max() {
    BIGNUM* value = BN_new();
    BIGNUM_Ptr one(BN_new());
    BN_one(one.get());
    BN_lshift(value, one.get(), 32);
    return value;
}

uint64_t BignumToUint64(BIGNUM* num) {
    static_assert((sizeof(BN_ULONG) == sizeof(uint32_t)) || (sizeof(BN_ULONG) == sizeof(uint64_t)),
                  "This implementation only supports 32 and 64-bit BN_ULONG");
    if (sizeof(BN_ULONG) == sizeof(uint32_t)) {
        BIGNUM_Ptr uint_max(construct_uint_max());
        BIGNUM_Ptr hi(BN_new()), lo(BN_new());
        BN_CTX_Ptr ctx(BN_CTX_new());
        BN_div(hi.get(), lo.get(), num, uint_max.get(), ctx.get());
        return static_cast<uint64_t>(BN_get_word(hi.get())) << 32 | BN_get_word(lo.get());
    } else if (sizeof(BN_ULONG) == sizeof(uint64_t)) {
        return BN_get_word(num);
    } else {
        return 0;
    }
}

template <Tag tag>
void copyAuthTag(const ASN1_INTEGER* asn1_int, TypedTag<TagType::ULONG, tag> ttag,
                 AuthorizationSet* auth_list) {
    if (!asn1_int) return;
    BIGNUM_Ptr num(ASN1_INTEGER_to_BN(asn1_int, nullptr));
    auth_list->push_back(ttag, BignumToUint64(num.get()));
}

template <Tag tag>
void copyAuthTag(const ASN1_INTEGER* asn1_int, TypedTag<TagType::DATE, tag> ttag,
                 AuthorizationSet* auth_list) {
    if (!asn1_int) return;
    BIGNUM_Ptr num(ASN1_INTEGER_to_BN(asn1_int, nullptr));
    auth_list->push_back(ttag, BignumToUint64(num.get()));
}

template <Tag tag>
void copyAuthTag(const ASN1_NULL* asn1_null, TypedTag<TagType::BOOL, tag> ttag,
                 AuthorizationSet* auth_list) {
    if (!asn1_null) return;
    auth_list->push_back(ttag);
}

template <Tag tag>
void copyAuthTag(const ASN1_OCTET_STRING* asn1_string, TypedTag<TagType::BYTES, tag> ttag,
                 AuthorizationSet* auth_list) {
    if (!asn1_string) return;
    hidl_vec<uint8_t> buf;
    buf.setToExternal(asn1_string->data, asn1_string->length);
    auth_list->push_back(ttag, buf);
}

// Extract the values from the specified ASN.1 record and place them in auth_list.
static ErrorCode extract_auth_list(const KM_AUTH_LIST* record, AuthorizationSet* auth_list) {
    if (!record) return ErrorCode::OK;

    copyAuthTag(record->active_date_time, TAG_ACTIVE_DATETIME, auth_list);
    copyAuthTag(record->algorithm, TAG_ALGORITHM, auth_list);
    copyAuthTag(record->all_applications, TAG_ALL_APPLICATIONS, auth_list);
    copyAuthTag(record->application_id, TAG_APPLICATION_ID, auth_list);
    copyAuthTag(record->auth_timeout, TAG_AUTH_TIMEOUT, auth_list);
    copyAuthTag(record->creation_date_time, TAG_CREATION_DATETIME, auth_list);
    copyAuthTag(record->digest, TAG_DIGEST, auth_list);
    copyAuthTag(record->ec_curve, TAG_EC_CURVE, auth_list);
    copyAuthTag(record->key_size, TAG_KEY_SIZE, auth_list);
    copyAuthTag(record->no_auth_required, TAG_NO_AUTH_REQUIRED, auth_list);
    copyAuthTag(record->origin, TAG_ORIGIN, auth_list);
    copyAuthTag(record->origination_expire_date_time, TAG_ORIGINATION_EXPIRE_DATETIME, auth_list);
    copyAuthTag(record->os_patchlevel, TAG_OS_PATCHLEVEL, auth_list);
    copyAuthTag(record->os_version, TAG_OS_VERSION, auth_list);
    copyAuthTag(record->padding, TAG_PADDING, auth_list);
    copyAuthTag(record->purpose, TAG_PURPOSE, auth_list);
    copyAuthTag(record->rollback_resistant, TAG_ROLLBACK_RESISTANT, auth_list);
    copyAuthTag(record->rsa_public_exponent, TAG_RSA_PUBLIC_EXPONENT, auth_list);
    copyAuthTag(record->usage_expire_date_time, TAG_USAGE_EXPIRE_DATETIME, auth_list);
    copyAuthTag(record->user_auth_type, TAG_USER_AUTH_TYPE, auth_list);
    copyAuthTag(record->attestation_application_id,
                TAG_ATTESTATION_APPLICATION_ID, auth_list);

    return ErrorCode::OK;
}

MAKE_OPENSSL_PTR_TYPE(KM_KEY_DESCRIPTION)

// Parse the DER-encoded attestation record, placing the results in keymaster_version,
// attestation_challenge, software_enforced, tee_enforced and unique_id.
ErrorCode parse_attestation_record(const uint8_t* asn1_key_desc, size_t asn1_key_desc_len,
                                   uint32_t* attestation_version,  //
                                   SecurityLevel* attestation_security_level,
                                   uint32_t* keymaster_version,
                                   SecurityLevel* keymaster_security_level,
                                   hidl_vec<uint8_t>* attestation_challenge,
                                   AuthorizationSet* software_enforced,
                                   AuthorizationSet* tee_enforced,  //
                                   hidl_vec<uint8_t>* unique_id) {
    const uint8_t* p = asn1_key_desc;
    KM_KEY_DESCRIPTION_Ptr record(d2i_KM_KEY_DESCRIPTION(nullptr, &p, asn1_key_desc_len));
    if (!record.get()) return ErrorCode::UNKNOWN_ERROR;

    *attestation_version = ASN1_INTEGER_get(record->attestation_version);
    *attestation_security_level =
        static_cast<SecurityLevel>(ASN1_ENUMERATED_get(record->attestation_security_level));
    *keymaster_version = ASN1_INTEGER_get(record->keymaster_version);
    *keymaster_security_level =
        static_cast<SecurityLevel>(ASN1_ENUMERATED_get(record->keymaster_security_level));

    auto& chall = record->attestation_challenge;
    attestation_challenge->resize(chall->length);
    memcpy(attestation_challenge->data(), chall->data, chall->length);
    auto& uid = record->unique_id;
    unique_id->resize(uid->length);
    memcpy(unique_id->data(), uid->data, uid->length);

    ErrorCode error = extract_auth_list(record->software_enforced, software_enforced);
    if (error != ErrorCode::OK) return error;

    return extract_auth_list(record->tee_enforced, tee_enforced);
}

}  // namespace V3_0
}  // namespace keymaster
}  // namespace hardware
}  // namespace android
