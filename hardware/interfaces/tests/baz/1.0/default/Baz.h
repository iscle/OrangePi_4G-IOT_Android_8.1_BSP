#ifndef ANDROID_HARDWARE_TESTS_BAZ_V1_0_BAZ_H
#define ANDROID_HARDWARE_TESTS_BAZ_V1_0_BAZ_H

#include <android/hardware/tests/baz/1.0/IBaz.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace tests {
namespace baz {
namespace V1_0 {
namespace implementation {

// using ::android::hardware::tests::baz::V1_0::IBase;
using ::android::hardware::tests::baz::V1_0::IBaz;
using ::android::hardware::tests::baz::V1_0::IBazCallback;
using ::android::hidl::base::V1_0::DebugInfo;
using ::android::hidl::base::V1_0::IBase;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct Baz : public IBaz {
    // Methods from ::android::hardware::tests::baz::V1_0::IBase follow.
    Return<void> someBaseMethod() override;
    Return<bool> someBoolMethod(bool x) override;
    Return<void> someBoolArrayMethod(const hidl_array<bool, 3>& x,
                                     someBoolArrayMethod_cb _hidl_cb) override;
    Return<void> someBoolVectorMethod(const hidl_vec<bool>& x,
                                      someBoolVectorMethod_cb _hidl_cb) override;
    Return<void> someOtherBaseMethod(const IBase::Foo& foo,
                                     someOtherBaseMethod_cb _hidl_cb) override;
    Return<void> someMethodWithFooArrays(const hidl_array<IBase::Foo, 2>& fooInput,
                                         someMethodWithFooArrays_cb _hidl_cb) override;
    Return<void> someMethodWithFooVectors(const hidl_vec<IBase::Foo>& fooInput,
                                          someMethodWithFooVectors_cb _hidl_cb) override;
    Return<void> someMethodWithVectorOfArray(const IBase::VectorOfArray& in,
                                             someMethodWithVectorOfArray_cb _hidl_cb) override;
    Return<void> someMethodTakingAVectorOfArray(const hidl_vec<hidl_array<uint8_t, 6>>& in,
                                                someMethodTakingAVectorOfArray_cb _hidl_cb) override;
    Return<void> transpose(const IBase::StringMatrix5x3& in,
                           transpose_cb _hidl_cb) override;
    Return<void> transpose2(const hidl_array<hidl_string, 5, 3>& in,
                            transpose2_cb _hidl_cb) override;
    Return<void> takeAMask(IBase::BitField bf,
                           uint8_t first,
                           const IBase::MyMask& second,
                           uint8_t third,
                           takeAMask_cb _hidl_cb) override;

    // Methods from ::android::hardware::tests::baz::V1_0::IBaz follow.
    Return<void> doThis(float param) override;
    Return<int32_t> doThatAndReturnSomething(int64_t param) override;
    Return<double> doQuiteABit(int32_t a, int64_t b, float c, double d) override;
    Return<void> doSomethingElse(const hidl_array<int32_t, 15>& param,
                                 doSomethingElse_cb _hidl_cb) override;
    Return<void> doStuffAndReturnAString(doStuffAndReturnAString_cb _hidl_cb) override;
    Return<void> mapThisVector(const hidl_vec<int32_t>& param, mapThisVector_cb _hidl_cb) override;
    Return<void> callMe(const sp<IBazCallback>& cb) override;
    Return<void> callMeLater(const sp<IBazCallback>& cb) override;
    Return<void> iAmFreeNow() override;
    Return<void> dieNow() override;
    Return<IBaz::SomeEnum> useAnEnum(IBaz::SomeEnum zzz) override;
    Return<void> haveSomeStrings(const hidl_array<hidl_string, 3>& array,
                                 haveSomeStrings_cb _hidl_cb) override;
    Return<void> haveAStringVec(const hidl_vec<hidl_string>& vector,
                                haveAStringVec_cb _hidl_cb) override;
    Return<void> returnABunchOfStrings(returnABunchOfStrings_cb _hidl_cb) override;
    Return<uint8_t> returnABitField() override;
    Return<uint32_t> size(uint32_t size) override;
    Return<void> getNestedStructs(getNestedStructs_cb _hidl_cb) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
 private:
    sp<IBazCallback> mStoredCallback;
};

extern "C" IBaz* HIDL_FETCH_IBaz(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace baz
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_BAZ_V1_0_BAZ_H
