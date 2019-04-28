/*
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

#include "Baz.h"
#include <android-base/logging.h>

namespace android {
namespace hardware {
namespace tests {
namespace baz {
namespace V1_0 {
namespace implementation {

struct BazCallback : public IBazCallback {
    Return<void> heyItsMe(const sp<IBazCallback> &cb) override;
    Return<void> hey() override;
};

Return<void> BazCallback::heyItsMe(
        const sp<IBazCallback> &cb) {
    LOG(INFO) << "SERVER: heyItsMe cb = " << cb.get();

    return Void();
}

Return<void> BazCallback::hey() {
    LOG(INFO) << "SERVER: hey";

    return Void();
}

// Methods from ::android::hardware::tests::baz::V1_0::IBase follow.
Return<void> Baz::someBaseMethod() {
    LOG(INFO) << "Baz::someBaseMethod";

    return Void();
}

Return<bool> Baz::someBoolMethod(bool x) {
    LOG(INFO) << "Baz::someBoolMethod(" << std::to_string(x) << ")";

    return !x;
}

Return<void> Baz::someBoolArrayMethod(const hidl_array<bool, 3>& x,
                                      someBoolArrayMethod_cb _hidl_cb) {
    LOG(INFO) << "Baz::someBoolArrayMethod(" << toString(x) << ")";

    hidl_array<bool, 4> out;
    out[0] = !x[0];
    out[1] = !x[1];
    out[2] = !x[2];
    out[3] = true;

    _hidl_cb(out);

    return Void();
}

Return<void> Baz::someBoolVectorMethod(const hidl_vec<bool>& x, someBoolVectorMethod_cb _hidl_cb) {
    LOG(INFO) << "Baz::someBoolVectorMethod(" << toString(x) << ")";

    hidl_vec<bool> out;
    out.resize(x.size());
    for (size_t i = 0; i < x.size(); ++i) {
        out[i] = !x[i];
    }

    _hidl_cb(out);

    return Void();
}

Return<void> Baz::someOtherBaseMethod(const IBase::Foo& foo, someOtherBaseMethod_cb _hidl_cb) {
    LOG(INFO) << "Baz::someOtherBaseMethod "
              << toString(foo);

    _hidl_cb(foo);

    return Void();
}

Return<void> Baz::someMethodWithFooArrays(const hidl_array<IBase::Foo, 2>& fooInput,
                                          someMethodWithFooArrays_cb _hidl_cb) {
    LOG(INFO) << "Baz::someMethodWithFooArrays "
              << toString(fooInput);

    hidl_array<IBaz::Foo, 2> fooOutput;
    fooOutput[0] = fooInput[1];
    fooOutput[1] = fooInput[0];

    _hidl_cb(fooOutput);

    return Void();
}

Return<void> Baz::someMethodWithFooVectors(const hidl_vec<IBase::Foo>& fooInput,
                                           someMethodWithFooVectors_cb _hidl_cb) {
    LOG(INFO) << "Baz::someMethodWithFooVectors "
              << toString(fooInput);

    hidl_vec<IBaz::Foo> fooOutput;
    fooOutput.resize(2);
    fooOutput[0] = fooInput[1];
    fooOutput[1] = fooInput[0];

    _hidl_cb(fooOutput);

    return Void();
}

Return<void> Baz::someMethodWithVectorOfArray(const IBase::VectorOfArray& in,
                                              someMethodWithVectorOfArray_cb _hidl_cb) {
    LOG(INFO) << "Baz::someMethodWithVectorOfArray "
              << toString(in);

    IBase::VectorOfArray out;

    const size_t n = in.addresses.size();
    out.addresses.resize(n);

    for (size_t i = 0; i < n; ++i) {
        out.addresses[i] = in.addresses[n - 1 - i];
    }

    _hidl_cb(out);

    return Void();
}

Return<void> Baz::someMethodTakingAVectorOfArray(const hidl_vec<hidl_array<uint8_t, 6>>& in,
                                                 someMethodTakingAVectorOfArray_cb _hidl_cb) {
    LOG(INFO) << "Baz::someMethodTakingAVectorOfArray "
              << toString(in);

    const size_t n = in.size();

    hidl_vec<hidl_array<uint8_t, 6> > out;
    out.resize(n);

    for (size_t i = 0; i < n; ++i) {
        out[i] = in[n - 1 - i];
    }

    _hidl_cb(out);

    return Void();
}

Return<void> Baz::transpose(const IBase::StringMatrix5x3& in, transpose_cb _hidl_cb) {
    LOG(INFO) << "Baz::transpose " << toString(in);

    IBase::StringMatrix3x5 out;
    for (size_t i = 0; i < 3; ++i) {
        for (size_t j = 0; j < 5; ++j) {
            out.s[i][j] = in.s[j][i];
        }
    }

    _hidl_cb(out);

    return Void();
}

Return<void> Baz::transpose2(const hidl_array<hidl_string, 5, 3>& in, transpose2_cb _hidl_cb) {
    LOG(INFO) << "Baz::transpose2 " << toString(in);

    hidl_array<hidl_string, 3, 5> out;
    for (size_t i = 0; i < 3; ++i) {
        for (size_t j = 0; j < 5; ++j) {
            out[i][j] = in[j][i];
        }
    }

    _hidl_cb(out);

    return Void();
}

Return<void> Baz::takeAMask(IBase::BitField bf,
                            uint8_t first,
                            const IBase::MyMask& second,
                            uint8_t third,
                            takeAMask_cb _hidl_cb) {
    _hidl_cb(bf, bf | first, second.value & bf, (bf | bf) & third);
    return Void();
}

// Methods from ::android::hardware::tests::baz::V1_0::IBaz follow.

Return<void> Baz::doThis(float param) {
    LOG(INFO) << "Baz::doThis(" << param << ")";

    return Void();
}

Return<int32_t> Baz::doThatAndReturnSomething(int64_t param) {
    LOG(INFO) << "Baz::doThatAndReturnSomething(" << param << ")";

    return 666;
}

Return<double> Baz::doQuiteABit(int32_t a, int64_t b, float c, double d) {
    LOG(INFO) << "Baz::doQuiteABit("
              << a
              << ", "
              << b
              << ", "
              << c
              << ", "
              << d
              << ")";

    return 666.5;
}

Return<void> Baz::doSomethingElse(const hidl_array<int32_t, 15>& param,
                                  doSomethingElse_cb _hidl_cb) {
    LOG(INFO) << "Baz::doSomethingElse(...)";

    hidl_array<int32_t, 32> result;
    for (size_t i = 0; i < 15; ++i) {
        result[i] = 2 * param[i];
        result[15 + i] = param[i];
    }
    result[30] = 1;
    result[31] = 2;

    _hidl_cb(result);

    return Void();
}

Return<void> Baz::doStuffAndReturnAString(doStuffAndReturnAString_cb _hidl_cb) {
    LOG(INFO) << "doStuffAndReturnAString";

    hidl_string s;
    s = "Hello, world!";

    _hidl_cb(s);

    return Void();
}

Return<void> Baz::mapThisVector(const hidl_vec<int32_t>& param, mapThisVector_cb _hidl_cb) {
    LOG(INFO) << "mapThisVector";

    hidl_vec<int32_t> out;
    out.resize(param.size());
    for (size_t i = 0; i < param.size(); ++i) {
        out[i] = param[i] * 2;
    }

    _hidl_cb(out);

    return Void();
}

Return<void> Baz::callMe(const sp<IBazCallback>& cb) {
    LOG(INFO) << "callMe " << cb.get();

    if (cb != NULL) {
        sp<IBazCallback> my_cb = new BazCallback;
        cb->heyItsMe(my_cb);
    }

    return Void();
}

Return<void> Baz::callMeLater(const sp<IBazCallback>& cb) {
    LOG(INFO) << "callMeLater " << cb.get();

    mStoredCallback = cb;

    return Void();
}

Return<void> Baz::iAmFreeNow() {
    if (mStoredCallback != nullptr) {
        mStoredCallback->hey();
    }
    return Void();
}

Return<void> Baz::dieNow() {
    exit(1);
    return Void();
}

Return<IBaz::SomeEnum> Baz::useAnEnum(IBaz::SomeEnum zzz) {
    LOG(INFO) << "useAnEnum " << (int)zzz;

    return SomeEnum::goober;
}

Return<void> Baz::haveSomeStrings(const hidl_array<hidl_string, 3>& array,
                                  haveSomeStrings_cb _hidl_cb) {
    LOG(INFO) << "haveSomeStrings("
              << toString(array)
              << ")";

    hidl_array<hidl_string, 2> result;
    result[0] = "Hello";
    result[1] = "World";

    _hidl_cb(result);

    return Void();
}

Return<void> Baz::haveAStringVec(const hidl_vec<hidl_string>& vector,
                                 haveAStringVec_cb _hidl_cb) {
    LOG(INFO) << "haveAStringVec(" << toString(vector) << ")";

    hidl_vec<hidl_string> result;
    result.resize(2);

    result[0] = "Hello";
    result[1] = "World";

    _hidl_cb(result);

    return Void();
}

Return<void> Baz::returnABunchOfStrings(returnABunchOfStrings_cb _hidl_cb) {
    hidl_string eins; eins = "Eins";
    hidl_string zwei; zwei = "Zwei";
    hidl_string drei; drei = "Drei";
    _hidl_cb(eins, zwei, drei);

    return Void();
}

Return<uint8_t> Baz::returnABitField() {
    return 0;
}

Return<uint32_t> Baz::size(uint32_t size) {
    return size;
}

Return<void> Baz::getNestedStructs(getNestedStructs_cb _hidl_cb) {
    int size = 5;
    hidl_vec<IBaz::NestedStruct> result;
    result.resize(size);
    for (int i = 0; i < size; i++) {
        result[i].a = i;
        if (i == 1) {
            result[i].matrices.resize(6);
        }
    }
    _hidl_cb(result);
    return Void();
}
// Methods from ::android::hidl::base::V1_0::IBase follow.

IBaz* HIDL_FETCH_IBaz(const char* /* name */) {
    return new Baz();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace baz
}  // namespace tests
}  // namespace hardware
}  // namespace android
