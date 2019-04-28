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

#include "key_param_output.h"

#include <iomanip>

namespace android {
namespace hardware {

namespace keymaster {
namespace V3_0 {

::std::ostream& operator<<(::std::ostream& os, const hidl_vec<KeyParameter>& set) {
    if (set.size() == 0) {
        os << "(Empty)" << ::std::endl;
    } else {
        os << "\n";
        for (size_t i = 0; i < set.size(); ++i)
            os << set[i] << ::std::endl;
    }
    return os;
}

::std::ostream& operator<<(::std::ostream& os, ErrorCode value) {
    return os << (int)value;
}

::std::ostream& operator<<(::std::ostream& os, Digest value) {
    return os << stringify(value);
}

::std::ostream& operator<<(::std::ostream& os, Algorithm value) {
    return os << stringify(value);
}

::std::ostream& operator<<(::std::ostream& os, BlockMode value) {
    return os << stringify(value);
}

::std::ostream& operator<<(::std::ostream& os, PaddingMode value) {
    return os << stringify(value);
}

::std::ostream& operator<<(::std::ostream& os, KeyOrigin value) {
    return os << stringify(value);
}

::std::ostream& operator<<(::std::ostream& os, KeyPurpose value) {
    return os << stringify(value);
}

::std::ostream& operator<<(::std::ostream& os, EcCurve value) {
    return os << stringify(value);
}

::std::ostream& operator<<(::std::ostream& os, const KeyParameter& param) {
    os << stringifyTag(param.tag) << ": ";
    switch (typeFromTag(param.tag)) {
    case TagType::INVALID:
        return os << " Invalid";
    case TagType::UINT_REP:
    case TagType::UINT:
        return os << param.f.integer;
    case TagType::ENUM_REP:
    case TagType::ENUM:
        switch (param.tag) {
        case Tag::ALGORITHM:
            return os << param.f.algorithm;
        case Tag::BLOCK_MODE:
            return os << param.f.blockMode;
        case Tag::PADDING:
            return os << param.f.paddingMode;
        case Tag::DIGEST:
            return os << param.f.digest;
        case Tag::EC_CURVE:
            return os << (int)param.f.ecCurve;
        case Tag::ORIGIN:
            return os << param.f.origin;
        case Tag::BLOB_USAGE_REQUIREMENTS:
            return os << (int)param.f.keyBlobUsageRequirements;
        case Tag::PURPOSE:
            return os << param.f.purpose;
        default:
            return os << " UNKNOWN ENUM " << param.f.integer;
        }
    case TagType::ULONG_REP:
    case TagType::ULONG:
        return os << param.f.longInteger;
    case TagType::DATE:
        return os << param.f.dateTime;
    case TagType::BOOL:
        return os << "true";
    case TagType::BIGNUM:
        os << " Bignum: ";
        for (size_t i = 0; i < param.blob.size(); ++i) {
            os << ::std::hex << ::std::setw(2) << static_cast<int>(param.blob[i]) << ::std::dec;
        }
        return os;
    case TagType::BYTES:
        os << " Bytes: ";
        for (size_t i = 0; i < param.blob.size(); ++i) {
            os << ::std::hex << ::std::setw(2) << static_cast<int>(param.blob[i]) << ::std::dec;
        }
        return os;
    }
    return os << "UNKNOWN TAG TYPE!";
}

::std::ostream& operator<<(::std::ostream& os, const KeyCharacteristics& chars) {
    return os << "SW: " << chars.softwareEnforced << ::std::endl
              << "TEE: " << chars.teeEnforced << ::std::endl;
}

}  // namespace V3_0
}  // namespace keymaster
}  // namespace hardware
}  // namespace android
