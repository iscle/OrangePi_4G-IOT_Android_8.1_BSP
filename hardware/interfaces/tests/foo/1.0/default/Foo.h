#ifndef ANDROID_HARDWARE_TESTS_FOO_V1_0_FOO_H
#define ANDROID_HARDWARE_TESTS_FOO_V1_0_FOO_H

#include <android/hardware/tests/foo/1.0/IFoo.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>
#include <vector>
namespace android {
namespace hardware {
namespace tests {
namespace foo {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::foo::V1_0::Abc;
using ::android::hardware::tests::foo::V1_0::IFoo;
using ::android::hardware::tests::foo::V1_0::IFooCallback;
using ::android::hardware::tests::foo::V1_0::ISimple;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct Foo : public IFoo {
    // Methods from ::android::hardware::tests::foo::V1_0::IFoo follow.
    virtual Return<void> doThis(float param)  override;
    virtual Return<int32_t> doThatAndReturnSomething(int64_t param)  override;
    virtual Return<double> doQuiteABit(int32_t a, int64_t b, float c, double d)  override;
    virtual Return<void> doSomethingElse(const hidl_array<int32_t, 15 /* 15 */>& param, doSomethingElse_cb _hidl_cb)  override;
    virtual Return<void> doStuffAndReturnAString(doStuffAndReturnAString_cb _hidl_cb)  override;
    virtual Return<void> mapThisVector(const hidl_vec<int32_t>& param, mapThisVector_cb _hidl_cb)  override;
    virtual Return<void> callMe(const sp<IFooCallback>& cb)  override;
    virtual Return<IFoo::SomeEnum> useAnEnum(IFoo::SomeEnum zzz)  override;
    virtual Return<void> haveAGooberVec(const hidl_vec<IFoo::Goober>& param)  override;
    virtual Return<void> haveAGoober(const IFoo::Goober& g)  override;
    virtual Return<void> haveAGooberArray(const hidl_array<IFoo::Goober, 20 /* 20 */>& lots)  override;
    virtual Return<void> haveATypeFromAnotherFile(const Abc& def)  override;
    virtual Return<void> haveSomeStrings(const hidl_array<hidl_string, 3 /* 3 */>& array, haveSomeStrings_cb _hidl_cb)  override;
    virtual Return<void> haveAStringVec(const hidl_vec<hidl_string>& vector, haveAStringVec_cb _hidl_cb)  override;
    virtual Return<void> transposeMe(const hidl_array<float, 3 /* 3 */, 5 /* 5 */>& in, transposeMe_cb _hidl_cb)  override;
    virtual Return<void> callingDrWho(const IFoo::MultiDimensional& in, callingDrWho_cb _hidl_cb)  override;
    virtual Return<void> transpose(const IFoo::StringMatrix5x3& in, transpose_cb _hidl_cb)  override;
    virtual Return<void> transpose2(const hidl_array<hidl_string, 5 /* 5 */, 3 /* 3 */>& in, transpose2_cb _hidl_cb)  override;
    virtual Return<void> sendVec(const hidl_vec<uint8_t>& data, sendVec_cb _hidl_cb)  override;
    virtual Return<void> sendVecVec(sendVecVec_cb _hidl_cb)  override;
    virtual Return<void> createMyHandle(createMyHandle_cb _hidl_cb)  override;
    virtual Return<void> createHandles(uint32_t size, createHandles_cb _hidl_cb)  override;
    virtual Return<void> closeHandles()  override;

    Return<void> haveAVectorOfInterfaces(
            const hidl_vec<sp<ISimple> > &in,
            haveAVectorOfInterfaces_cb _hidl_cb) override;

    Return<void> haveAVectorOfGenericInterfaces(
            const hidl_vec<sp<android::hidl::base::V1_0::IBase> > &in,
            haveAVectorOfGenericInterfaces_cb _hidl_cb) override;

    Return<void> echoNullInterface(const sp<IFooCallback> &cb, echoNullInterface_cb _hidl_cb) override;
private:
    std::vector<::native_handle_t *> mHandles;
};

extern "C" IFoo* HIDL_FETCH_IFoo(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace foo
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_FOO_V1_0_FOO_H
